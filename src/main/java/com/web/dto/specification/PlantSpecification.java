package com.web.dto.specification;

import com.web.entity.Diseases;
import com.web.entity.Plant;
import com.web.entity.PlantDiseases;
import com.web.enums.PlantStatus;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
public class PlantSpecification implements Specification<Plant> {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "cây", "cay", "cỏ", "co", "ok", "lá", "la", "rễ", "re", 
        "hoa", "quả", "qua", "dược liệu", "duoc lieu", "thuốc", "thuoc",
        "cây thuốc", "cay thuoc", "cây dược liệu", "cay duoc lieu"
    ));

    private String search;

    private List<Long> familiesId = new ArrayList<>();

    private List<Long> diseases = new ArrayList<>();

    @Override
    public Predicate toPredicate(Root<Plant> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Predicate predicate = cb.conjunction();
        query.distinct(true);
        if (diseases != null && !diseases.isEmpty()) {
            Join<Plant, PlantDiseases> productCategoryJoin = root.join("plantDiseases", JoinType.INNER);
            Join<PlantDiseases, Diseases> join = productCategoryJoin.join("diseases", JoinType.INNER);
            predicate = cb.and(predicate, join.get("id").in(diseases));
        }

        if (familiesId != null && !familiesId.isEmpty()) {
            predicate = cb.and(predicate, root.get("families").get("id").in(familiesId));
        }

        if (search != null && !search.isEmpty() && !search.isBlank()) {
            // Tiền xử lý: loại bỏ stop-word như "cây", "lá", "rễ" để tìm kiếm chính xác hơn
            String cleanedSearch = cleanSearchKeyword(search.trim());
            String searchPattern = "%" + cleanedSearch.toLowerCase() + "%";
            List<Predicate> searchPredicates = new ArrayList<>();
            
            // Tìm trong các trường cơ bản
            searchPredicates.add(cb.like(cb.lower(root.get("name")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("scientificName")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("otherNames")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("genus")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("partsUsed")), searchPattern));
            
            // Tìm trong các trường mô tả chi tiết (TEXT)
            searchPredicates.add(cb.like(cb.lower(root.get("description")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("botanicalCharacteristics")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("chemicalComposition")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("distribution")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("ecology")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("medicinalUses")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("indications")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("contraindications")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("dosage")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("folkRemedies")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("sideEffects")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("source")), searchPattern));
            
            // Tìm trong các trường mô tả bộ phận cây
            searchPredicates.add(cb.like(cb.lower(root.get("stem")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("leaf")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("flower")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("fruitOrSeed")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("root")), searchPattern));
            
            Predicate searchPredicate = cb.or(searchPredicates.toArray(new Predicate[0]));
            predicate = cb.and(predicate, searchPredicate);
        }

        predicate = cb.and(predicate, cb.equal(root.get("plantStatus"), PlantStatus.DA_XUAT_BAN));
        return predicate;
    }

    /**
     * Loại bỏ stop-word thông dụng (cây, lá, rễ…) để phục vụ tìm kiếm.
     * Tương tự logic trong PlantService.extractPlantName()
     */
    private String cleanSearchKeyword(String str) {
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
        // Nếu sau khi loại bỏ stop-word mà rỗng, vẫn giữ nguyên từ gốc
        return cleaned.isEmpty() ? str.trim() : cleaned;
    }
}
