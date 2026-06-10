package com.web.service;

import com.web.dto.ResearchRequest;
import com.web.entity.Article;
import com.web.entity.Expert;
import com.web.entity.Plant;
import com.web.entity.Research;
import com.web.entity.ResearchExpert;
import com.web.entity.ResearchPlant;
import com.web.enums.ArticleStatus;
import com.web.enums.ResearchStatus;
import com.web.exception.MessageException;
import com.web.repository.ArticleRepository;
import com.web.repository.ExpertRepository;
import com.web.repository.ResearchExpertRepository;
import com.web.repository.ResearchPlantRepository;
import com.web.repository.ResearchRepository;
import com.web.utils.SlugGenerator;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;

@Service
public class ResearchService {

    private static final Logger logger = LoggerFactory.getLogger(ResearchService.class);

    @Autowired
    private ResearchRepository researchRepository;

    @Autowired
    private ResearchPlantRepository researchPlantRepository;

    @Autowired
    private ResearchExpertRepository researchExpertRepository;

    @Autowired
    private ExpertRepository expertRepository;

    public Page<Research> getAll(String search, ResearchStatus status, Pageable pageable) {
        try {
            String searchVal = (search == null || search.trim().isEmpty()) ? null : search.trim();
            return researchRepository.findAllByParam(searchVal, status, pageable);
        } catch (Exception e) {
            logger.warn("FULLTEXT search failed: {}", e.getMessage());
            throw new MessageException("Có lỗi xảy ra khi tìm kiếm. Vui lòng thử lại sau.");
        }
    }

    public Page<Research> getAllPublic(String search, String field, Integer publishedYear, Pageable pageable) {
        return researchRepository.findAllPublicByParam(
                (search == null || search.trim().isEmpty()) ? null : search.trim(),
                field, publishedYear,
                pageable
        );
    }

    public Research findById(Long id) {
        return researchRepository.findById(id)
                .orElseThrow(() -> new MessageException("Không tìm thấy nghiên cứu"));
    }

    /**
     * Tạo mới hoặc cập nhật bài viết (nếu có id)
     */
    @Transactional
    public Research save(ResearchRequest request) {
        Research research = request.getResearch();
        
        // ===== VALIDATE =====
        if (research.getTitle() == null || research.getTitle().trim().isEmpty()) {
            throw new MessageException("Tiêu đề không được để trống");
        }
        if (research.getContent() == null || research.getContent().trim().isEmpty() 
                || research.getContent().equals("<p><br></p>") || research.getContent().equals("<p></p>")) {
            throw new MessageException("Nội dung không được để trống");
        }
        if (research.getAbstractText() == null || research.getAbstractText().trim().isEmpty()) {
            throw new MessageException("Tóm tắt không được để trống");
        }
        // Phải có ít nhất 1 tác giả (expert hoặc text)
        boolean hasAuthors = (request.getExpertIds() != null && !request.getExpertIds().isEmpty())
                || (request.getAuthorsText() != null && !request.getAuthorsText().trim().isEmpty());
        if (!hasAuthors) {
            throw new MessageException("Vui lòng chọn ít nhất 1 tác giả hoặc nhập tên tác giả");
        }
        // Phải có ít nhất 1 cây dược liệu liên quan
        if (request.getPlantId() == null || request.getPlantId().isEmpty()) {
            throw new MessageException("Vui lòng chọn ít nhất 1 cây dược liệu liên quan");
        }
        
        if (research.getId() == null) {
            // Tạo mới
            if (researchRepository.existsByTitle(research.getTitle())) {
                throw new MessageException("Tiêu đề đã tồn tại");
            }
            if (researchRepository.existsBySlug(research.getSlug())) {
                throw new MessageException("Slug đã tồn tại");
            }
            if(research.getSlug() == null){
                research.setSlug(SlugGenerator.generateSlug(research.getTitle()));
            }
            
            // Set authors text nếu có
            if (request.getAuthorsText() != null) {
                research.setAuthors(request.getAuthorsText().trim());
            }
            
            researchRepository.saveAndFlush(research);
        } else {
            // Update
            Research existing = findById(research.getId());

            // Nếu đổi title -> kiểm tra trùng
            if (!existing.getTitle().equals(research.getTitle()) &&
                    researchRepository.existsByTitle(research.getTitle())) {
                throw new MessageException("Tiêu đề đã tồn tại");
            }

            // Nếu đổi slug -> kiểm tra trùng
            if (!existing.getSlug().equals(research.getSlug()) &&
                    researchRepository.existsBySlug(research.getSlug())) {
                throw new MessageException("Slug đã tồn tại");
            }

            existing.setTitle(research.getTitle());
            if(research.getSlug() == null){
                existing.setSlug(SlugGenerator.generateSlug(research.getTitle()));
            }
            else{
                existing.setSlug(research.getSlug());
            }
            existing.setAbstractText(research.getAbstractText());
            existing.setContent(research.getContent());
            existing.setImageBanner(research.getImageBanner());
            existing.setLinkDocument(research.getLinkDocument());
            if (research.getResearchStatus() != null) {
                existing.setResearchStatus(research.getResearchStatus());
            }
            existing.setPublishedYear(research.getPublishedYear());
            existing.setInstitution(research.getInstitution());
            existing.setJournal(research.getJournal());
            existing.setField(research.getField());
            
            // Cập nhật authors text (cho các tác giả không phải Expert)
            if (request.getAuthorsText() != null) {
                existing.setAuthors(request.getAuthorsText().trim());
            } else {
                existing.setAuthors(null);
            }

            // Xóa các quan hệ cũ
            researchPlantRepository.deleteByResearch(existing.getId());
            researchExpertRepository.deleteByResearch(existing.getId());
            
            research = existing;
            researchRepository.saveAndFlush(research);
        }

        // Lưu các cây dược liệu liên quan
        if (request.getPlantId() != null && !request.getPlantId().isEmpty()) {
            java.util.Set<Long> processedPlantIds = new java.util.HashSet<>();
            for(Long id : request.getPlantId()){
                if (id == null || processedPlantIds.contains(id)) {
                    continue;
                }
                processedPlantIds.add(id);
                Plant plant = new Plant();
                plant.setId(id);
                ResearchPlant researchPlant = new ResearchPlant(null, research, plant);
                researchPlantRepository.save(researchPlant);
            }
        }
        
        // Lưu các Expert (chuyên gia) là tác giả
        if (request.getExpertIds() != null && !request.getExpertIds().isEmpty()) {
            java.util.Set<Long> processedExpertIds = new java.util.HashSet<>();
            for(Long expertId : request.getExpertIds()){
                if (expertId == null || processedExpertIds.contains(expertId)) {
                    continue;
                }
                processedExpertIds.add(expertId);
                Expert expert = expertRepository.findById(expertId).orElse(null);
                if (expert != null) {
                    boolean exists = researchExpertRepository.existsByResearchAndExpert(research.getId(), expertId);
                    if (!exists) {
                        ResearchExpert researchExpert = new ResearchExpert(null, research, expert);
                        researchExpertRepository.save(researchExpert);
                    }
                }
            }
        }
        
        return research;
    }

    public void delete(Long id) {
        if (!researchRepository.existsById(id)) {
            throw new MessageException("Bài viết không tồn tại");
        }
        researchRepository.deleteById(id);
    }

    public Research findBySlug(String slug) {
        return researchRepository.findBySlug(slug).orElse(null);
    }

    /**
     * Tìm nghiên cứu theo slug - chỉ trả về nếu đã xuất bản (cho public access)
     * @param slug Slug của nghiên cứu
     * @return Research nếu tìm thấy và đã xuất bản, null nếu không
     */
    public Research findBySlugPublic(String slug) {
        return researchRepository.findBySlugAndPublished(slug).orElse(null);
    }

    /**
     * Ghi danh sách nghiên cứu ra CSV.
     */
    public void writeResearchToCsv(Writer writer, String q, ResearchStatus status) {
        try {
            // Dùng chuỗi rỗng (không phải null) vì native query có guard :search = ''
            String search = (q != null && !q.trim().isEmpty()) ? q.trim() : "";
            java.util.List<Research> list = researchRepository.findAllForExport(search, status != null ? status.name() : null);
            // BOM để Excel nhận diện UTF-8
            writer.write('\uFEFF');
            writer.write("ID,TIEU_DE,TAC_GIA,NAM_XUAT_BAN,TRANG_THAI,NGAY_TAO,NGAY_CAP_NHAT\n");
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            for (Research r : list) {
                try {
                    String statusStr = "";
                    if (r.getResearchStatus() != null) {
                        switch (r.getResearchStatus()) {
                            case DA_XUAT_BAN: statusStr = "Đã xuất bản"; break;
                            case CHO_DUYET: statusStr = "Chờ duyệt"; break;
                            case TU_CHOI: statusStr = "Từ chối"; break;
                            default: statusStr = r.getResearchStatus().name();
                        }
                    }
                    String line = String.format(
                            "%d,%s,%s,%s,%s,%s,%s\n",
                            r.getId(),
                            escapeCsv(r.getTitle()),
                            escapeCsv(r.getAuthors()),
                            r.getPublishedYear() != null ? r.getPublishedYear().toString() : "",
                            escapeCsv(statusStr),
                            r.getCreatedAt() != null ? r.getCreatedAt().format(fmt) : "",
                            r.getUpdatedAt() != null ? r.getUpdatedAt().format(fmt) : ""
                    );
                    writer.write(line);
                } catch (Exception e) {
                    writer.write(r.getId() + ",LỖI_KHI_XUAT,,,,,\n");
                }
            }
            writer.flush();
        } catch (Exception e) {
            try { writer.write("\nCó lỗi khi xuất: " + e.getMessage() + "\n"); writer.flush(); } catch (IOException ignored) {}
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

    public java.util.List<Research> findByExpertId(Long expertId) {
        return researchRepository.findByExpertId(expertId);
    }
}
