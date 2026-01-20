package com.web.service;

import com.web.dto.PlantImp;
import com.web.dto.PlantSearch;
import com.web.dto.request.PlantRequestDto;
import com.web.dto.specification.PlantSpecification;
import com.web.entity.Diseases;
import com.web.entity.Families;
import com.web.entity.Plant;
import com.web.entity.PlantDiseases;
import com.web.entity.PlantMedia;
import com.web.enums.PlantStatus;
import com.web.exception.MessageException;
import com.web.repository.DiseasesRepository;
import com.web.repository.FamiliesRepository;
import com.web.repository.PlantDiseasesRepository;
import com.web.repository.PlantMediaRepository;
import com.web.repository.PlantRepository;
import com.web.repository.ResearchPlantRepository;
import com.web.utils.Contains;
import com.web.utils.SlugGenerator;
import com.web.utils.UserUtils;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

@Service
public class PlantService {

    private static final Logger logger = LoggerFactory.getLogger(PlantService.class);

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "cây", "cay", "cỏ", "co", "ok", "lá", "la", "rễ", "re",
        "hoa", "quả", "qua", "nuoc", "nước", "nuoc hoa", "hoa qua",
        "dược liệu", "duoc lieu", "thuốc", "thuoc",
        "cây thuốc", "cay thuoc", "cây dược liệu", "cay duoc lieu",
        "dung", "tim", "tri", "chua", "benh", "va", "và", "hoac", "hoặc"
    ));

    private static final double SIMILARITY_THRESHOLD = 0.90;
    private static final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantMediaRepository plantMediaRepository;

    @Autowired
    private PlantDiseasesRepository plantDiseasesRepository;

    @Autowired
    private FamiliesRepository familiesRepository;

    @Autowired
    private DiseasesRepository diseasesRepository;

    @Autowired
    private ResearchPlantRepository researchPlantRepository;

    @Autowired
    private UserUtils userUtils;

    private String normalizeString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        return str.trim()
            .toLowerCase()
            .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
            .replaceAll("[èéẹẻẽêềếệểễ]", "e")
            .replaceAll("[ìíịỉĩ]", "i")
            .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
            .replaceAll("[ùúụủũưừứựửữ]", "u")
            .replaceAll("[ỳýỵỷỹ]", "y")
            .replaceAll("[đ]", "d")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeNoAccent(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        return str
            .toLowerCase()
            .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
            .replaceAll("[èéẹẻẽêềếệểễ]", "e")
            .replaceAll("[ìíịỉĩ]", "i")
            .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
            .replaceAll("[ùúụủũưừứựửữ]", "u")
            .replaceAll("[ỳýỵỷỹ]", "y")
            .replaceAll("[đ]", "d");
    }

    private String extractPlantName(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        
        String normalized = normalizeString(str);
        String[] tokens = normalized.split("\\s+");
        
        List<String> meaningfulTokens = new ArrayList<>();
        for (String token : tokens) {
            if (!STOP_WORDS.contains(token) && token.length() >= 2) {
                meaningfulTokens.add(token);
            }
        }
        
        return String.join(" ", meaningfulTokens);
    }

    private double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null || str1.isEmpty() || str2.isEmpty()) {
            return 0.0;
        }
        return jaroWinkler.apply(str1, str2);
    }

    private boolean hasCommonWords(String str1, String str2) {
        if (str1 == null || str2 == null || str1.isEmpty() || str2.isEmpty()) {
            return false;
        }
        String[] words1 = str1.split("\\s+");
        String[] words2 = str2.split("\\s+");
        
        if (words1.length == 0 || words2.length == 0) {
            return false;
        }
        
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        set1.retainAll(set2);
        
        if (set1.isEmpty()) {
            return false;
        }
        
        if (words1.length == 1 && words2.length == 1) {
            return words1[0].equals(words2[0]);
        }
        
        if (words1.length == 1) {
            String singleWord = words1[0];
            if (singleWord.length() < 4) {
                return false;
            }
            for (String word : words2) {
                if (word.equals(singleWord) && word.length() >= 4) {
                    return true;
                }
            }
            return false;
        }
        
        if (words2.length == 1) {
            String singleWord = words2[0];
            if (singleWord.length() < 4) {
                return false;
            }
            for (String word : words1) {
                if (word.equals(singleWord) && word.length() >= 4) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }

    private boolean isSimilarName(String input, String dbName) {
        if (input == null || dbName == null || input.isEmpty() || dbName.isEmpty()) {
            return false;
        }
        
        String extractedInput = extractPlantName(input);
        String extractedDb = extractPlantName(dbName);
        
        if (extractedInput.isEmpty() || extractedDb.isEmpty()) {
            return false;
        }
        
        String[] inputWords = extractedInput.split("\\s+");
        String[] dbWords = extractedDb.split("\\s+");
        
        if (extractedInput.equals(extractedDb)) {
            return true;
        }
        
        if (inputWords.length == 1 && dbWords.length == 1) {
            return extractedInput.equals(extractedDb);
        }
        
        if (inputWords.length == 1) {
            String singleWord = inputWords[0];
            if (singleWord.length() < 5) {
                return false;
            }
            for (String dbWord : dbWords) {
                if (singleWord.equals(dbWord)) {
                    return true;
                }
            }
            if (extractedDb.contains(singleWord) && singleWord.length() >= 5) {
                int pos = extractedDb.indexOf(singleWord);
                if (pos == 0 || extractedDb.charAt(pos - 1) == ' ') {
                    return true;
                }
            }
            return false;
        }
        
        if (dbWords.length == 1) {
            String singleWord = dbWords[0];
            if (singleWord.length() < 5) {
                return false;
            }
            for (String inputWord : inputWords) {
                if (singleWord.equals(inputWord)) {
                    return true;
                }
            }
            if (extractedInput.contains(singleWord) && singleWord.length() >= 5) {
                int pos = extractedInput.indexOf(singleWord);
                if (pos == 0 || extractedInput.charAt(pos - 1) == ' ') {
                    return true;
                }
            }
            return false;
        }
        
        if (extractedInput.length() >= 4 && extractedDb.length() >= 4) {
            if (extractedInput.contains(extractedDb) || extractedDb.contains(extractedInput)) {
                int minLen = Math.min(extractedInput.length(), extractedDb.length());
                int maxLen = Math.max(extractedInput.length(), extractedDb.length());
                if (maxLen <= minLen * 1.5) {
                    return true;
                }
            }
        }
        
        if (!hasCommonWords(extractedInput, extractedDb)) {
            return false;
        }
        
        if (extractedInput.length() < 4 || extractedDb.length() < 4) {
            return false;
        }
        
        double similarity = calculateSimilarity(extractedInput, extractedDb);
        return similarity >= SIMILARITY_THRESHOLD;
    }

    public Plant checkDuplicate(String name, String scientificName, Long excludeId) {
        if ((name == null || name.trim().isEmpty()) && 
            (scientificName == null || scientificName.trim().isEmpty())) {
            return null;
        }
        
        String trimmedName = (name != null) ? name.trim() : "";
        String trimmedScientificName = (scientificName != null) ? scientificName.trim() : "";
        
        List<Plant> duplicates = plantRepository.findDuplicatePlants(
            trimmedName.isEmpty() ? null : trimmedName,
            trimmedScientificName.isEmpty() ? null : trimmedScientificName,
            excludeId
        );
        
        if (!duplicates.isEmpty()) {
            return duplicates.get(0);
        }
        
        return null;
    }

    @Transactional
    public Plant saveOrUpdate(PlantRequestDto dto){
        if(dto == null){
            throw new MessageException("Dữ liệu không hợp lệ");
        }
        if(dto.getPlant() == null){
            throw new MessageException("Thông tin cây dược liệu không được để trống");
        }
        Plant plant = dto.getPlant();
        Long plantId = plant != null ? plant.getId() : null;
        
        if(plantId != null && plantId > 0){
            Optional<Plant> existOpt = plantRepository.findById(plantId);
            if(!existOpt.isPresent()){
                throw new MessageException("Không tìm thấy cây dược liệu với ID: " + plantId);
            }
            Plant exist = existOpt.get();
            
            String plantName = plant.getName() != null ? plant.getName().trim() : "";
            String plantScientificName = plant.getScientificName() != null ? plant.getScientificName().trim() : "";
            String existName = exist.getName() != null ? exist.getName().trim() : "";
            String existScientificName = exist.getScientificName() != null ? exist.getScientificName().trim() : "";
            
            boolean nameChanged = !plantName.equals(existName);
            boolean scientificNameChanged = !plantScientificName.equals(existScientificName);
            
            if (nameChanged || scientificNameChanged) {
                Plant duplicatePlant = checkDuplicate(plantName, plantScientificName, plantId);
                if (duplicatePlant != null) {
                    throw new MessageException(
                        String.format(
                            "Cây dược liệu này đã tồn tại trong hệ thống! " +
                            "Cây trùng: '%s' (ID: %d). " +
                            "Vui lòng kiểm tra lại.",
                            duplicatePlant.getName(),
                            duplicatePlant.getId()
                        )
                    );
                }
            }
            
            if(plant.getName() != null) exist.setName(plant.getName());
            if(plant.getScientificName() != null) exist.setScientificName(plant.getScientificName());
            if(plant.getSlug() != null && !plant.getSlug().trim().isEmpty()) exist.setSlug(plant.getSlug());
            if(plant.getGenus() != null) exist.setGenus(plant.getGenus());
            if(plant.getOtherNames() != null) exist.setOtherNames(plant.getOtherNames());
            if(plant.getPartsUsed() != null) exist.setPartsUsed(plant.getPartsUsed());
            if(plant.getDescription() != null) exist.setDescription(plant.getDescription());
            if(plant.getBotanicalCharacteristics() != null) exist.setBotanicalCharacteristics(plant.getBotanicalCharacteristics());
            if(plant.getChemicalComposition() != null) exist.setChemicalComposition(plant.getChemicalComposition());
            if(plant.getEcology() != null) exist.setEcology(plant.getEcology());
            if(plant.getMedicinalUses() != null) exist.setMedicinalUses(plant.getMedicinalUses());
            if(plant.getIndications() != null) exist.setIndications(plant.getIndications());
            if(plant.getContraindications() != null) exist.setContraindications(plant.getContraindications());
            if(plant.getDosage() != null) exist.setDosage(plant.getDosage());
            if(plant.getFolkRemedies() != null) exist.setFolkRemedies(plant.getFolkRemedies());
            if(plant.getSideEffects() != null) exist.setSideEffects(plant.getSideEffects());
            if(plant.getStem() != null) exist.setStem(plant.getStem());
            if(plant.getLeaf() != null) exist.setLeaf(plant.getLeaf());
            if(plant.getFlower() != null) exist.setFlower(plant.getFlower());
            if(plant.getFruitOrSeed() != null) exist.setFruitOrSeed(plant.getFruitOrSeed());
            if(plant.getRoot() != null) exist.setRoot(plant.getRoot());
            if(plant.getDistribution() != null) exist.setDistribution(plant.getDistribution());
            if(plant.getAltitude() != null) exist.setAltitude(plant.getAltitude());
            if(plant.getHarvestSeason() != null) exist.setHarvestSeason(plant.getHarvestSeason());
            if(plant.getSource() != null) exist.setSource(plant.getSource());
            if(plant.getLinkDocument() != null) exist.setLinkDocument(plant.getLinkDocument());
            if(plant.getImage() != null) exist.setImage(plant.getImage());
            if(plant.getFeatured() != null) exist.setFeatured(plant.getFeatured());
            if(plant.getPlantStatus() != null){
                exist.setPlantStatus(plant.getPlantStatus());
            }
            if(plant.getFamilies() != null && plant.getFamilies().getId() != null){
                exist.setFamilies(plant.getFamilies());
            }
            
            plant = exist;
        }
        else{
            String plantName = plant.getName() != null ? plant.getName().trim() : "";
            String plantScientificName = plant.getScientificName() != null ? plant.getScientificName().trim() : "";
            
            Plant duplicatePlant = checkDuplicate(plantName, plantScientificName, null);
            if (duplicatePlant != null) {
                throw new MessageException(
                    "Cây dược liệu này đã tồn tại trong hệ thống. Vui lòng kiểm tra lại."
                );
            }
            
            String userRole = userUtils.getCurrentUserRole();
            if(userRole != null){
                if(Contains.ROLE_USER.equals(userRole)){
                    plant.setPlantStatus(PlantStatus.CHO_DUYET);
                } else if(Contains.ROLE_EXPERT.equals(userRole) || Contains.ROLE_ADMIN.equals(userRole)){
                    plant.setPlantStatus(PlantStatus.DA_XUAT_BAN);
                }
            } else {
                plant.setPlantStatus(PlantStatus.CHO_DUYET);
            }
        }
        
        if(plant.getName() == null || plant.getName().trim().isEmpty()){
            throw new MessageException("Tên cây không được để trống");
        }
        if(plant.getPartsUsed() == null || plant.getPartsUsed().trim().isEmpty()){
            throw new MessageException("Bộ phận dùng không được để trống");
        }
        
        if(plant.getSlug() == null || plant.getSlug().trim().isEmpty()){
            plant.setSlug(SlugGenerator.generateSlug(plant.getName()));
        }
        
        Optional<Plant> existingBySlug = plantRepository.findBySlug(plant.getSlug());
        if(existingBySlug.isPresent()){
            if(plant.getId() != null && existingBySlug.get().getId().equals(plant.getId())){
            } else {
                if(dto.getForceSubmit() != null && dto.getForceSubmit()){
                    String baseSlug = plant.getSlug();
                    int counter = 1;
                    String newSlug = baseSlug + "-" + counter;
                    while(plantRepository.findBySlug(newSlug).isPresent()){
                        counter++;
                        newSlug = baseSlug + "-" + counter;
                    }
                    plant.setSlug(newSlug);
                } else {
                    throw new MessageException(
                        String.format("Slug '%s' đã tồn tại. Vui lòng sử dụng slug khác.", plant.getSlug())
                    );
                }
            }
        }
        
        if(plant.getFamilies() != null && plant.getFamilies().getId() != null) {
            Families families = familiesRepository.findById(plant.getFamilies().getId())
                    .orElse(null);
            if(families != null) {
                plant.setFamilies(families);
            } else {
                plant.setFamilies(null);
            }
        } else {
            plant.setFamilies(null);
        }
        
        Plant savedPlant = plantRepository.save(plant);
        
        if(savedPlant.getId() != null){
            plantMediaRepository.deleteByPlantId(savedPlant.getId());
        }
        
        if(dto.getImages() != null && !dto.getImages().isEmpty()){
            List<PlantMedia> list = new ArrayList<>();
            for(String s : dto.getImages()){
                if(s != null && !s.trim().isEmpty()){
                    PlantMedia md = new PlantMedia();
                    md.setPlant(savedPlant);
                    md.setImageLink(s);
                    list.add(md);
                }
            }
            if(!list.isEmpty()){
                plantMediaRepository.saveAll(list);
            }
        }
        
        if(savedPlant.getId() != null){
            plantDiseasesRepository.deleteByPlant(savedPlant.getId());
        }
        if(dto.getDiseasesIds() != null && !dto.getDiseasesIds().isEmpty()){
            for(Object idObj : dto.getDiseasesIds()){
                if(idObj != null){
                    Long id;
                    if(idObj instanceof String){
                        try {
                            id = Long.parseLong((String) idObj);
                        } catch (NumberFormatException e) {
                            continue; // Bỏ qua nếu không parse được
                        }
                    } else if(idObj instanceof Number){
                        id = ((Number) idObj).longValue();
                    } else {
                        continue;
                    }
                    
                    Optional<Diseases> diseasesOpt = diseasesRepository.findById(id);
                    if(diseasesOpt.isPresent()){
                        PlantDiseases plantDiseases = new PlantDiseases();
                        plantDiseases.setDiseases(diseasesOpt.get());
                        plantDiseases.setPlant(savedPlant);
                        plantDiseasesRepository.save(plantDiseases);
                    }
                }
            }
        }
        return savedPlant;
    }

    public Page<Plant> getAllByAdmin(Pageable pageable, String q, Long familiesId,PlantStatus plantStatus) {
        try {
            String search = sanitizeSearchQuery(q);
            
            if (search != null && search.length() > 200) {
                search = search.substring(0, 200);
                logger.warn("Search query truncated to 200 characters");
            }
            
            String cleanedSearch = null;
            if (search != null && !search.trim().isEmpty()) {
                cleanedSearch = cleanSearchKeywordForAdmin(search);
            }

            String booleanSearch = buildBooleanFulltext(cleanedSearch);
            
            Page<Plant> page;
            try {
                if (booleanSearch != null && !booleanSearch.trim().isEmpty()) {
                    page = plantRepository.searchByAdminFullText(
                        booleanSearch, 
                        familiesId, 
                        plantStatus != null ? plantStatus.name() : null, 
                        pageable
                    );
                } else {
                    page = plantRepository.searchByAdmin(cleanedSearch, familiesId, plantStatus, pageable);
                }
            } catch (Exception e) {
                logger.warn("FULLTEXT search failed, falling back to LIKE search: {}", e.getMessage());
                page = plantRepository.searchByAdmin(cleanedSearch, familiesId, plantStatus, pageable);
            }
            
            if (cleanedSearch != null && !cleanedSearch.isEmpty()) {
                List<Plant> content = new ArrayList<>(page.getContent());
                String searchLower = cleanedSearch.toLowerCase();
                
                content.sort((p1, p2) -> {
                    if (p1 == null || p2 == null) {
                        return p1 == null ? 1 : -1;
                    }
                    int priority1 = getRelevancePriority(p1, searchLower);
                    int priority2 = getRelevancePriority(p2, searchLower);
                    int compare = Integer.compare(priority1, priority2);
                    if (compare == 0 && p1.getCreatedAt() != null && p2.getCreatedAt() != null) {
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    }
                    return compare;
                });
                
                return new org.springframework.data.domain.PageImpl<>(
                    content, 
                    pageable, 
                    page.getTotalElements()
                );
            }
            
            if (page.getContent().size() > 0) {
                List<Plant> content = new ArrayList<>(page.getContent());
                content.sort((p1, p2) -> {
                    if (p1 == null || p2 == null || p1.getCreatedAt() == null || p2.getCreatedAt() == null) {
                        return 0;
                    }
                    return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                });
                return new org.springframework.data.domain.PageImpl<>(
                    content, 
                    pageable, 
                    page.getTotalElements()
                );
            }
            
            return page;
        } catch (Exception e) {
            logger.error("Lỗi khi tìm kiếm cây dược liệu: {}", e.getMessage(), e);
            throw new MessageException("Có lỗi xảy ra khi tìm kiếm. Vui lòng thử lại sau.");
        }
    }
    
    private String cleanSearchKeywordForAdmin(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        
        String normalized = str.trim()
            .toLowerCase()
            .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
            .replaceAll("[èéẹẻẽêềếệểễ]", "e")
            .replaceAll("[ìíịỉĩ]", "i")
            .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
            .replaceAll("[ùúụủũưừứựửữ]", "u")
            .replaceAll("[ỳýỵỷỹ]", "y")
            .replaceAll("[đ]", "d")
            .replaceAll("\\s+", " ")
            .trim();
        
        String[] tokens = normalized.split("\\s+");
        List<String> meaningfulTokens = new ArrayList<>();
        for (String token : tokens) {
            if (!STOP_WORDS.contains(token) && token.length() >= 2) {
                meaningfulTokens.add(token);
            }
        }
        
        String cleaned = String.join(" ", meaningfulTokens);
        return cleaned.isEmpty() ? str.trim() : cleaned;
    }

    /**
     * Chuyển query đã làm sạch thành chuỗi BOOLEAN MODE bắt buộc +token*
     */
    private String buildBooleanFulltext(String cleaned) {
        if (cleaned == null) {
            return null;
        }
        String[] tokens = cleaned.trim().split("\\s+");
        List<String> parts = new ArrayList<>();
        for (String t : tokens) {
            if (t.length() >= 2) {
                parts.add(t);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        // Bắt buộc token đầu tiên; nếu có >=2 token thì bắt buộc 2 token, phần còn lại tùy chọn
        if (parts.size() == 1) {
            return "+" + parts.get(0) + "*";
        }
        String first = "+" + parts.get(0) + "*";
        String second = "+" + parts.get(1) + "*";
        if (parts.size() == 2) {
            return first + " " + second;
        }
        List<String> optional = new ArrayList<>();
        for (int i = 2; i < parts.size(); i++) {
            optional.add(parts.get(i) + "*");
        }
        return first + " " + second + " " + String.join(" ", optional);
    }
    
    /**
     * Tính độ ưu tiên của plant dựa trên từ khóa tìm kiếm
     * @param plant Plant cần tính
     * @param searchLower Từ khóa tìm kiếm (đã lowercase)
     * @return Độ ưu tiên (1 = cao nhất, 10 = thấp nhất)
     * 
     * Logic ưu tiên:
     * - Priority 1: Tên cây bắt đầu bằng từ khóa (ví dụ: "rau dền" khi tìm "rau")
     * - Priority 2: Tên cây chứa từ khóa (ví dụ: "cây rau dền" khi tìm "rau")
     * - Priority 3: Tên khoa học bắt đầu bằng từ khóa
     * - Priority 4: Tên khoa học chứa từ khóa
     * - Priority 5: Tên khác bắt đầu bằng từ khóa
     * - Priority 6: Tên khác chứa từ khóa
     * - Priority 7: Genus chứa từ khóa
     * - Priority 8: PartsUsed chứa từ khóa
     * - Priority 9: Các field khác (description, medicinalUses, etc.)
     * - Priority 10: Không tìm thấy
     */
    private int getRelevancePriority(Plant plant, String searchLower) {
        String searchNorm = normalizeNoAccent(searchLower);

        String nameLower = plant.getName() != null ? plant.getName().toLowerCase() : "";
        String scientificNameLower = plant.getScientificName() != null ? plant.getScientificName().toLowerCase() : "";
        String otherNamesLower = plant.getOtherNames() != null ? plant.getOtherNames().toLowerCase() : "";
        String genusLower = plant.getGenus() != null ? plant.getGenus().toLowerCase() : "";
        String partsUsedLower = plant.getPartsUsed() != null ? plant.getPartsUsed().toLowerCase() : "";

        String nameNorm = normalizeNoAccent(nameLower);
        String scientificNorm = normalizeNoAccent(scientificNameLower);
        String otherNorm = normalizeNoAccent(otherNamesLower);
        String genusNorm = normalizeNoAccent(genusLower);
        String partsNorm = normalizeNoAccent(partsUsedLower);
        
        // Ưu tiên cao nhất: Tên cây bắt đầu bằng từ khóa (ví dụ: "rau dền" khi tìm "rau")
        if (nameNorm.startsWith(searchNorm)) {
            return 1;
        }
        
        // Ưu tiên 2: Tên cây chứa từ khóa
        if (nameNorm.contains(searchNorm)) {
            return 2;
        }
        
        // Ưu tiên 3: Tên khoa học bắt đầu bằng từ khóa
        if (scientificNorm.startsWith(searchNorm)) {
            return 3;
        }
        
        // Ưu tiên 4: Tên khoa học chứa từ khóa
        if (scientificNorm.contains(searchNorm)) {
            return 4;
        }
        
        // Ưu tiên 5: Tên khác bắt đầu bằng từ khóa
        if (otherNorm.startsWith(searchNorm)) {
            return 5;
        }
        
        // Ưu tiên 6: Tên khác chứa từ khóa
        if (otherNorm.contains(searchNorm)) {
            return 6;
        }
        
        // Ưu tiên 7: Genus chứa từ khóa
        if (genusNorm.contains(searchNorm)) {
            return 7;
        }
        
        // Ưu tiên 8: PartsUsed chứa từ khóa
        if (partsNorm.contains(searchNorm)) {
            return 8;
        }
        
        // Ưu tiên 9: Các field khác (description, medicinalUses, etc.) - đã được tìm thấy bởi query
        // Nhưng không ưu tiên cao
        return 9;
    }
    
    /**
     * Sanitize search query để tránh SQL injection và XSS
     * Loại bỏ các ký tự đặc biệt nguy hiểm, giữ lại ký tự tiếng Việt
     */
    private String sanitizeSearchQuery(String q) {
        if (q == null || q.trim().isEmpty()) {
            return null;
        }
        
        // Trim và normalize whitespace
        String sanitized = q.trim().replaceAll("\\s+", " ");
        
        // Loại bỏ các ký tự đặc biệt nguy hiểm (giữ lại ký tự tiếng Việt, số, khoảng trắng, dấu câu cơ bản)
        // Cho phép: a-z, A-Z, 0-9, khoảng trắng, và các ký tự tiếng Việt
        sanitized = sanitized.replaceAll("[^\\p{L}\\p{N}\\s\\-.,;:()]", "");
        
        // Giới hạn độ dài
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        
        return sanitized.isEmpty() ? null : sanitized;
    }

    

    public Page<Plant> getAllByPublic(Pageable pageable, PlantSearch search) {
        try {
            String nameSearch = search.getNameSearch();
            String searchQuery = search.getSearch();
            
            Long familyId = (search.getFamiliesId() != null && !search.getFamiliesId().isEmpty())
                ? search.getFamiliesId().get(0)
                : null;

            // Nếu có nameSearch (tìm theo tên cây), ưu tiên dùng query tìm theo tên
            if (nameSearch != null && !nameSearch.trim().isEmpty()) {
                // Chỉ dùng cleaned name search (không build boolean query vì không có FULLTEXT index)
                String cleanedNameSearch = cleanSearchKeywordForAdmin(nameSearch.trim());
                // Nếu sau khi clean mà rỗng, dùng nameSearch gốc
                if (cleanedNameSearch == null || cleanedNameSearch.trim().isEmpty()) {
                    cleanedNameSearch = nameSearch.trim();
                }
                
                Page<Plant> nameSearchPage = plantRepository.findAllPublicByName(cleanedNameSearch, familyId, pageable);
                
                // Nếu cũng có full text search, thì filter thêm kết quả
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    String cleanedSearch = cleanSearchKeywordForAdmin(searchQuery);
                    String booleanSearch = buildBooleanFulltext(cleanedSearch);
                    
                    // Lấy kết quả từ nameSearch, sau đó filter thêm bằng full text search
                    List<Plant> filteredContent = new ArrayList<>();
                    for (Plant plant : nameSearchPage.getContent()) {
                        // Kiểm tra xem plant có match với full text search không
                        boolean matchesFullText = false;
                        if (booleanSearch != null && !booleanSearch.trim().isEmpty()) {
                            // Kiểm tra match trong các trường khác ngoài name
                            String lowerSearch = cleanedSearch.toLowerCase();
                            if ((plant.getScientificName() != null && plant.getScientificName().toLowerCase().contains(lowerSearch)) ||
                                (plant.getDescription() != null && plant.getDescription().toLowerCase().contains(lowerSearch)) ||
                                (plant.getMedicinalUses() != null && plant.getMedicinalUses().toLowerCase().contains(lowerSearch)) ||
                                (plant.getChemicalComposition() != null && plant.getChemicalComposition().toLowerCase().contains(lowerSearch)) ||
                                (plant.getOtherNames() != null && plant.getOtherNames().toLowerCase().contains(lowerSearch))) {
                                matchesFullText = true;
                            }
                        } else {
                            matchesFullText = true; // Nếu không có full text search thì giữ tất cả
                        }
                        
                        if (matchesFullText) {
                            filteredContent.add(plant);
                        }
                    }
                    
                    // Filter theo diseases nếu có
                    if (search.getDiseases() != null && !search.getDiseases().isEmpty()) {
                        filteredContent = filteredContent.stream()
                            .filter(plant -> {
                                if (plant.getPlantDiseases() == null || plant.getPlantDiseases().isEmpty()) {
                                    return false;
                                }
                                return plant.getPlantDiseases().stream()
                                    .anyMatch(pd -> pd.getDiseases() != null && 
                                        search.getDiseases().contains(pd.getDiseases().getId()));
                            })
                            .collect(java.util.stream.Collectors.toList());
                    }
                    
                    return new org.springframework.data.domain.PageImpl<>(
                        filteredContent,
                        pageable,
                        filteredContent.size()
                    );
                } else {
                    // Chỉ có nameSearch, filter theo diseases nếu có
                    if (search.getDiseases() != null && !search.getDiseases().isEmpty()) {
                        List<Plant> filteredContent = nameSearchPage.getContent().stream()
                            .filter(plant -> {
                                if (plant.getPlantDiseases() == null || plant.getPlantDiseases().isEmpty()) {
                                    return false;
                                }
                                return plant.getPlantDiseases().stream()
                                    .anyMatch(pd -> pd.getDiseases() != null && 
                                        search.getDiseases().contains(pd.getDiseases().getId()));
                            })
                            .collect(java.util.stream.Collectors.toList());
                        
                        return new org.springframework.data.domain.PageImpl<>(
                            filteredContent,
                            pageable,
                            filteredContent.size()
                        );
                    }
                    return nameSearchPage;
                }
            }
            
            // Nếu không có nameSearch, dùng logic full text search như cũ
            String cleanedSearch = null;
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                cleanedSearch = cleanSearchKeywordForAdmin(searchQuery);
            }
            
            String booleanSearch = buildBooleanFulltext(cleanedSearch);

            Page<Plant> page;
            try {
                if (booleanSearch != null && !booleanSearch.trim().isEmpty()) {
                    page = plantRepository.findAllPublic(booleanSearch, familyId, pageable);
                } else {
                    PlantSpecification plantSpecification = new PlantSpecification(searchQuery, search.getFamiliesId(), search.getDiseases());
                    page = plantRepository.findAll(plantSpecification, pageable);
                }
            } catch (Exception e) {
                logger.warn("FULLTEXT search failed, falling back to LIKE search: {}", e.getMessage());
                PlantSpecification plantSpecification = new PlantSpecification(searchQuery, search.getFamiliesId(), search.getDiseases());
                page = plantRepository.findAll(plantSpecification, pageable);
            }
            
            if (cleanedSearch != null && !cleanedSearch.isEmpty()) {
                List<Plant> content = new ArrayList<>(page.getContent());
                String searchLower = cleanedSearch.toLowerCase();
                
                content.sort((p1, p2) -> {
                    if (p1 == null || p2 == null) {
                        return p1 == null ? 1 : -1;
                    }
                    int priority1 = getRelevancePriority(p1, searchLower);
                    int priority2 = getRelevancePriority(p2, searchLower);
                    int compare = Integer.compare(priority1, priority2);
                    if (compare == 0 && p1.getCreatedAt() != null && p2.getCreatedAt() != null) {
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    }
                    return compare;
                });
                
                return new org.springframework.data.domain.PageImpl<>(
                    content, 
                    pageable, 
                    page.getTotalElements()
                );
            }
            
            return page;
        } catch (Exception e) {
            logger.error("Lỗi khi tìm kiếm cây dược liệu (public): {}", e.getMessage(), e);
            throw new MessageException("Có lỗi xảy ra khi tìm kiếm. Vui lòng thử lại sau.");
        }
    }

    @Transactional
    public void delete(Long id) {
        // Tìm plant trước khi xóa để kiểm tra status
        Plant plant = findById(id);
        if (plant == null) {
            throw new MessageException("Không tìm thấy cây dược liệu với ID: " + id);
        }
        
        // Không cho phép xóa cây đang ở trạng thái "Chờ duyệt"
        if (plant.getPlantStatus() == PlantStatus.CHO_DUYET) {
            throw new MessageException("Không thể xóa cây dược liệu đang ở trạng thái 'Chờ duyệt'. " +
                    "Vui lòng duyệt hoặc từ chối cây dược liệu này trước khi xóa.");
        }
        
        try {
            // Xóa các bản ghi liên quan trước để tránh constraint violation
            // 1. Xóa ResearchPlant (nếu có cascade sẽ tự xóa, nhưng để chắc chắn)
            researchPlantRepository.deleteByPlant(id);
            
            // 2. PlantMedia và PlantDiseases sẽ tự động xóa nhờ cascade = CascadeType.REMOVE
            // Nhưng để chắc chắn, có thể xóa thủ công nếu cần
            
            // 3. Cuối cùng mới xóa Plant
            plantRepository.deleteById(id);
        }
        catch (Exception e){
            logger.error("Error deleting plant with ID {}: {}", id, e.getMessage(), e);
            throw new MessageException("Có lỗi khi xóa cây dược liệu này: " + 
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()) + 
                    ". Có thể cây dược liệu này đang được sử dụng trong nghiên cứu hoặc có dữ liệu liên quan khác.");
        }
    }

    public void deleteImage(Long id) {
        try {
            plantMediaRepository.deleteById(id);
        }
        catch (Exception e){
            throw new MessageException("Có lỗi khi xóa cây thực vật này: "+e.getMessage());
        }
    }

    public Plant findById(Long id){
        return plantRepository.findById(id).orElse(null);
    }

    public List<Plant> cayNoiBatIndex() {
        List<Plant> list = plantRepository.cayNoiBat();
        return list;
    }

    public Plant findBySlug(String slug) {
        Optional<Plant> optionalBlog = plantRepository.findBySlug(slug);
        return optionalBlog.orElse(null);
    }

    /**
     * Tìm cây dược liệu theo slug - chỉ trả về nếu đã xuất bản (cho public access)
     * @param slug Slug của cây dược liệu
     * @return Plant nếu tìm thấy và đã xuất bản, null nếu không
     */
    public Plant findBySlugPublic(String slug) {
        return plantRepository.findBySlugAndPublished(slug).orElse(null);
    }

    /**
     * Tăng view count cho plant với chống spam (session-based)
     * Chỉ đếm 1 lần mỗi session trong 1 giờ
     */
    @Transactional
    public void incrementViewCount(Long plantId, javax.servlet.http.HttpSession session) {
        if (plantId == null || session == null) {
            return;
        }

        try {
            // Key để lưu danh sách plants đã xem trong session
            String sessionKey = "viewed_plants";
            
            // Lấy danh sách plants đã xem từ session
            @SuppressWarnings("unchecked")
            Set<Long> viewedPlants = (Set<Long>) session.getAttribute(sessionKey);
            
            if (viewedPlants == null) {
                viewedPlants = new HashSet<>();
            }

            // Nếu chưa xem plant này trong session này, thì tăng counter
            if (!viewedPlants.contains(plantId)) {
                plantRepository.incrementViewCount(plantId);
                viewedPlants.add(plantId);
                session.setAttribute(sessionKey, viewedPlants);
            }
        } catch (Exception e) {
            // Log lỗi nhưng không throw để không làm gián đoạn response
            logger.error("Error incrementing plant view count for plantId {}: {}", plantId, e.getMessage(), e);
        }
    }

    /**
     * Lấy top viewed plants (đã xuất bản)
     */
    public List<Plant> getTopViewedPlants(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return plantRepository.findTopViewed(PlantStatus.DA_XUAT_BAN, pageable);
    }

    public List<PlantImp> findAllName(){
        return plantRepository.findAllName();
    }

    /**
     * Ghi danh sách cây dược liệu ra CSV (Excel có thể mở được).
     */
    public void writePlantsToCsv(Writer writer, String q, Long familiesId, PlantStatus plantStatus) {
        try {
            String search = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
            List<Plant> plants;
            // Sử dụng FULLTEXT search nếu có index
            try {
                if (search != null && !search.isEmpty()) {
                    plants = plantRepository.searchForExportFullText(search, familiesId, 
                        plantStatus != null ? plantStatus.name() : null);
                } else {
                    plants = plantRepository.searchForExport(search, familiesId, plantStatus);
                }
            } catch (Exception e) {
                // Fallback về LIKE search nếu FULLTEXT chưa được setup
                logger.warn("FULLTEXT search not available for export, falling back to LIKE search: " + e.getMessage());
                plants = plantRepository.searchForExport(search, familiesId, plantStatus);
            }

            // Header
            writer.write("ID,TEN_CAY,TEN_KHOA_HOC,HO_THUC_VAT,BO_PHAN_DUNG,TRANG_THAI,NGAY_TAO,NGAY_CAP_NHAT\n");

            for (Plant p : plants) {
                String line = String.format(
                        "%d,%s,%s,%s,%s,%s,%s,%s\n",
                        p.getId(),
                        escapeCsv(p.getName()),
                        escapeCsv(p.getScientificName()),
                        p.getFamilies() != null ? escapeCsv(p.getFamilies().getName()) : "",
                        escapeCsv(p.getPartsUsed()),
                        p.getPlantStatus() != null ? p.getPlantStatus().name() : "",
                        p.getCreatedAt() != null ? p.getCreatedAt().toString() : "",
                        p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : ""
                );
                writer.write(line);
            }
            writer.flush();
        } catch (IOException e) {
            throw new MessageException("Lỗi khi xuất dữ liệu cây dược liệu: " + e.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    /**
     * Duyệt hoặc từ chối cây dược liệu (chỉ EXPERT và ADMIN)
     */
    public Plant approveOrReject(Long id, PlantStatus status) {
        Plant plant = findById(id);
        if(plant == null){
            throw new MessageException("Không tìm thấy cây dược liệu");
        }
        if(status != PlantStatus.DA_XUAT_BAN && status != PlantStatus.TU_CHOI){
            throw new MessageException("Trạng thái không hợp lệ. Chỉ có thể duyệt (DA_XUAT_BAN) hoặc từ chối (TU_CHOI)");
        }
        plant.setPlantStatus(status);
        return plantRepository.save(plant);
    }

    /**
     * Lấy danh sách cây dược liệu chờ duyệt (cho EXPERT và ADMIN)
     */
    public Page<Plant> getPendingPlants(Pageable pageable, String q, Long familiesId) {
        String search = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
        return plantRepository.searchByAdmin(search, familiesId, PlantStatus.CHO_DUYET, pageable);
    }
}
