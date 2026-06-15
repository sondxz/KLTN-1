package com.web.service;

import com.web.entity.Article;
import com.web.entity.ChunkEmbedding;
import com.web.enums.ArticleStatus;
import com.web.exception.MessageException;
import com.web.repository.ArticleRepository;
import com.web.utils.SlugGenerator;
import com.web.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ArticleService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleService.class);

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserUtils userUtils;

    @Autowired
    private RagIndexCoordinator ragIndexCoordinator;

    public Page<Article> getAll(String search, ArticleStatus status, Pageable pageable) {
        try {
            return articleRepository.findAllByParam(
                    (search == null || search.trim().isEmpty()) ? null : search.trim(),
                    status != null ? status.name() : null,
                    pageable
            );
        } catch (Exception e) {
            logger.warn("FULLTEXT search failed: {}", e.getMessage());
            throw new MessageException("Có lỗi xảy ra khi tìm kiếm. Vui lòng thử lại sau.");
        }
    }

    public Page<Article> getAllPublic(String search, Long diseasesId, Pageable pageable) {
        // Lấy sort từ pageable, tạm thời gọi repo với Pageable không sort (dùng ORDER BY mặc định)
        // Sau đó sort trong Java theo yêu cầu
        org.springframework.data.domain.Sort sort = pageable.getSort();
        Pageable unsortedPageable = org.springframework.data.domain.PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Article> page = articleRepository.findAllByParam(
                (search == null || search.trim().isEmpty()) ? null : search.trim(),
                diseasesId,
                unsortedPageable
        );
        
        // Nếu có sort, sort lại trong Java
        if (sort.isSorted()) {
            java.util.List<Article> sorted = new java.util.ArrayList<>(page.getContent());
            sort.forEach(order -> {
                java.util.Comparator<Article> comparator = null;
                String prop = order.getProperty();
                if ("id".equals(prop)) {
                    comparator = java.util.Comparator.comparing(Article::getId, 
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                } else if ("title".equals(prop)) {
                    comparator = java.util.Comparator.comparing(
                        a -> a.getTitle() != null ? a.getTitle().toLowerCase() : "", 
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                } else if ("viewCount".equals(prop)) {
                    comparator = java.util.Comparator.comparing(Article::getViewCount,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                } else if ("createdAt".equals(prop)) {
                    comparator = java.util.Comparator.comparing(Article::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                }
                if (comparator != null) {
                    if (order.isDescending()) comparator = comparator.reversed();
                    sorted.sort(comparator);
                }
            });
            return new org.springframework.data.domain.PageImpl<>(sorted, pageable, page.getTotalElements());
        }
        
        return page;
    }

    public Article findById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new MessageException("Không tìm thấy bài viết"));
    }

    /**
     * Tạo mới hoặc cập nhật bài viết (nếu có id)
     */
    @Transactional
    public Article save(Article article) {
        if (article.getId() == null) {
            if (articleRepository.existsByTitle(article.getTitle())) {
                throw new MessageException("Tiêu đề đã tồn tại");
            }
            if (articleRepository.existsBySlug(article.getSlug())) {
                throw new MessageException("Slug đã tồn tại");
            }
            if(article.getSlug() == null){
                article.setSlug(SlugGenerator.generateSlug(article.getTitle()));
            }
            article.setUser(userUtils.getUserWithAuthority());
            
            String userRole = userUtils.getCurrentUserRole();
            if(userRole != null){
                if(com.web.utils.Contains.ROLE_USER.equals(userRole)){
                    article.setArticleStatus(ArticleStatus.CHO_DUYET);
                } else if(com.web.utils.Contains.ROLE_EXPERT.equals(userRole) || com.web.utils.Contains.ROLE_ADMIN.equals(userRole)){
                    article.setArticleStatus(ArticleStatus.DA_XUAT_BAN);
                    article.setPublishedAt(LocalDateTime.now());
                }
            } else {
                article.setArticleStatus(ArticleStatus.CHO_DUYET);
            }
            
            Article saved = articleRepository.save(article);
            ragIndexCoordinator.syncAfterCommit(ChunkEmbedding.ContentType.article, saved.getId());
            return saved;
        }
        else {
            Article existing = findById(article.getId());

            if (!existing.getTitle().equals(article.getTitle()) &&
                    articleRepository.existsByTitle(article.getTitle())) {
                throw new MessageException("Tiêu đề đã tồn tại");
            }

            if (!existing.getSlug().equals(article.getSlug()) &&
                    articleRepository.existsBySlug(article.getSlug())) {
                throw new MessageException("Slug đã tồn tại");
            }

            existing.setTitle(article.getTitle());
            if(article.getSlug() == null){
                existing.setSlug(SlugGenerator.generateSlug(article.getTitle()));
            }
            else{
                existing.setSlug(article.getSlug());
            }
            existing.setExcerpt(article.getExcerpt());
            existing.setContent(article.getContent());
            existing.setImageBanner(article.getImageBanner());
            existing.setIsFeatured(article.getIsFeatured());
            existing.setAllowComments(article.getAllowComments());
            if(article.getArticleStatus() != null && article.getArticleStatus().equals(ArticleStatus.DA_XUAT_BAN) 
                    && (existing.getArticleStatus() == null || !existing.getArticleStatus().equals(ArticleStatus.DA_XUAT_BAN))){
                existing.setPublishedAt(LocalDateTime.now());
            }
            if(article.getArticleStatus() != null){
                existing.setArticleStatus(article.getArticleStatus());
            }
            existing.setDiseases(article.getDiseases());
            Article saved = articleRepository.save(existing);
            ragIndexCoordinator.syncAfterCommit(ChunkEmbedding.ContentType.article, saved.getId());
            return saved;
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!articleRepository.existsById(id)) {
            throw new MessageException("Bài viết không tồn tại");
        }
        articleRepository.deleteById(id);
        ragIndexCoordinator.syncAfterCommit(ChunkEmbedding.ContentType.article, id);
    }

    public Article findBySlug(String slug) {
        return articleRepository.findBySlug(slug).orElse(null);
    }

    /**
     * Tìm bài viết theo slug - chỉ trả về nếu đã xuất bản (cho public access)
     * @param slug Slug của bài viết
     * @return Article nếu tìm thấy và đã xuất bản, null nếu không
     */
    public Article findBySlugPublic(String slug) {
        return articleRepository.findBySlugAndPublished(slug).orElse(null);
    }

    /**
     * Tăng view count cho article với chống spam (session-based)
     * Chỉ đếm 1 lần mỗi session trong 1 giờ
     */
    @Transactional
    public void incrementViewCount(Long articleId, javax.servlet.http.HttpSession session) {
        if (articleId == null || session == null) {
            return;
        }

        try {
            // Key để lưu danh sách articles đã xem trong session
            String sessionKey = "viewed_articles";
            
            @SuppressWarnings("unchecked")
            Set<Long> viewedArticles = (Set<Long>) session.getAttribute(sessionKey);
            
            if (viewedArticles == null) {
                viewedArticles = new HashSet<>();
            }

            if (!viewedArticles.contains(articleId)) {
                articleRepository.incrementViewCount(articleId);
                viewedArticles.add(articleId);
                session.setAttribute(sessionKey, viewedArticles);
            }
        } catch (Exception e) {
            logger.error("Error incrementing article view count for articleId {}: {}", articleId, e.getMessage(), e);
        }
    }

    /**
     * Lấy top viewed articles (đã xuất bản)
     */
    public List<Article> getTopViewedArticles(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return articleRepository.findTopViewed(ArticleStatus.DA_XUAT_BAN, pageable);
    }

    /**
     * Ghi danh sách bài viết ra CSV.
     */
    public void writeArticlesToCsv(Writer writer, String q, ArticleStatus status) {
        try {
            // Dùng chuỗi rỗng (không phải null) vì native query có guard :search = ''
            String search = (q != null && !q.trim().isEmpty()) ? q.trim() : "";
            List<Article> list = articleRepository.findAllForExport(search, status != null ? status.name() : null);
            // BOM để Excel nhận diện UTF-8
            writer.write('\uFEFF');
            writer.write("ID,TIEU_DE,CONG_DUNG,TOM_TAT,TAC_GIA,TRANG_THAI,NGAY_TAO,NGAY_CAP_NHAT\n");
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            for (Article a : list) {
                try {
                    String statusStr = "";
                    if (a.getArticleStatus() != null) {
                        switch (a.getArticleStatus()) {
                            case DA_XUAT_BAN: statusStr = "Đã xuất bản"; break;
                            case CHO_DUYET: statusStr = "Chờ duyệt"; break;
                            case TU_CHOI: statusStr = "Từ chối"; break;
                            default: statusStr = a.getArticleStatus().name();
                        }
                    }
                    String line = String.format(
                            "%d,%s,%s,%s,%s,%s,%s,%s\n",
                            a.getId(),
                            escapeCsv(a.getTitle()),
                            a.getDiseases() != null ? escapeCsv(a.getDiseases().getName()) : "",
                            escapeCsv(a.getExcerpt()),
                            a.getUser() != null ? escapeCsv(a.getUser().getFullname()) : "",
                            escapeCsv(statusStr),
                            a.getCreatedAt() != null ? a.getCreatedAt().format(fmt) : "",
                            a.getUpdatedAt() != null ? a.getUpdatedAt().format(fmt) : ""
                    );
                    writer.write(line);
                } catch (Exception e) {
                    writer.write(a.getId() + ",LỖI_KHI_XUAT,,,,,,\n");
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

    /**
     * Duyệt hoặc từ chối bài viết (chỉ EXPERT và ADMIN)
     */
    @Transactional
    public Article approveOrReject(Long id, ArticleStatus status) {
        Article article = findById(id);
        if(status != ArticleStatus.DA_XUAT_BAN && status != ArticleStatus.TU_CHOI){
            throw new MessageException("Trạng thái không hợp lệ. Chỉ có thể duyệt (DA_XUAT_BAN) hoặc từ chối (TU_CHOI)");
        }
        article.setArticleStatus(status);
        if(status == ArticleStatus.DA_XUAT_BAN && article.getPublishedAt() == null){
            article.setPublishedAt(LocalDateTime.now());
        }
        Article saved = articleRepository.save(article);
        ragIndexCoordinator.syncAfterCommit(ChunkEmbedding.ContentType.article, saved.getId());
        return saved;
    }

    /**
     * Lấy danh sách bài viết chờ duyệt (cho EXPERT và ADMIN)
     */
    public Page<Article> getPendingArticles(Pageable pageable, String q) {
        try {
            String search = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
            return articleRepository.findAllByParam(search, ArticleStatus.CHO_DUYET.name(), pageable);
        } catch (Exception e) {
            logger.warn("FULLTEXT search failed: {}", e.getMessage());
            throw new MessageException("Có lỗi xảy ra khi tìm kiếm. Vui lòng thử lại sau.");
        }
    }
}
