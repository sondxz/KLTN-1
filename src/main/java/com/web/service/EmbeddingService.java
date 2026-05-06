package com.web.service;

import com.google.gson.*;
import com.web.entity.*;
import com.web.repository.ChunkEmbeddingRepository;
import com.web.repository.PlantRepository;
import com.web.repository.ArticleRepository;
import com.web.repository.ResearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service quản lý embedding: tạo chunk, gọi Cloudflare Worker Embedding API, lưu vào DB.
 * <p>
 * THAY ĐỔI CHÍNH so với phiên bản cũ:
 * 1. Chuyển embedding sang Cloudflare Worker (không giới hạn request, miễn phí)
 * 2. Batch embedding: gộp nhiều chunk vào 1 request (max 50/batch)
 * 3. Tách @Transactional ra khỏi HTTP call → không giữ DB connection khi gọi API
 * 4. @Async indexing → không block HTTP thread
 * 5. Retry + exponential backoff qua CloudflareAIService
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Kích thước tối đa mỗi chunk (ký tự) */
    private static final int MAX_CHUNK_SIZE = 1500;

    /** Overlap giữa các chunk liên tiếp */
    private static final int CHUNK_OVERLAP = 200;

    /** Số chunk tối đa gộp vào 1 batch embedding request */
    private static final int EMBEDDING_BATCH_SIZE = 50;

    @Autowired
    private ChunkEmbeddingRepository chunkEmbeddingRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ResearchRepository researchRepository;

    @Autowired
    private CloudflareAIService cloudflareAIService;

    @Autowired
    private ChunkPersistenceService chunkPersistenceService;

    private final Gson gson = new Gson();

    /** Flag ngăn chạy nhiều indexing đồng thời */
    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);

    /** Tracking tiến trình indexing */
    private final AtomicInteger indexingProgress = new AtomicInteger(0);
    private volatile String indexingStatus = "idle";

    // ================================================
    // PUBLIC METHODS — ASYNC INDEXING
    // ================================================

    /**
     * Index toàn bộ nội dung (async).
     * Trả về CompletableFuture để caller có thể track trạng thái.
     * 
     * QUAN TRỌNG: @Async chạy trên thread pool riêng (ragIndexingExecutor),
     * không block HTTP thread.
     */
    @Async("ragIndexingExecutor")
    public CompletableFuture<IndexingResult> indexAllAsync(List<FolkRemedy> folkRemedies) {
        if (!indexingInProgress.compareAndSet(false, true)) {
            log.warn("Indexing đang chạy, bỏ qua request mới");
            return CompletableFuture.completedFuture(
                    new IndexingResult(0, 0, 0, 0, "Indexing đang chạy, vui lòng đợi"));
        }

        try {
            indexingStatus = "running";
            indexingProgress.set(0);
            log.info("===== BẮT ĐẦU ASYNC RAG INDEXING =====");

            int plants = indexAllPlants();
            indexingProgress.set(25);

            int articles = indexAllArticles();
            indexingProgress.set(50);

            int research = indexAllResearch();
            indexingProgress.set(75);

            int folkRemediesCount = indexAllFolkRemedies(folkRemedies);
            indexingProgress.set(100);

            indexingStatus = "completed";
            log.info("===== HOÀN TẤT RAG INDEXING: plants={}, articles={}, research={}, folkRemedies={} =====",
                    plants, articles, research, folkRemediesCount);

            return CompletableFuture.completedFuture(
                    new IndexingResult(plants, articles, research, folkRemediesCount, "success"));

        } catch (Exception e) {
            indexingStatus = "error: " + e.getMessage();
            log.error("RAG Indexing thất bại", e);
            return CompletableFuture.completedFuture(
                    new IndexingResult(0, 0, 0, 0, "error: " + e.getMessage()));
        } finally {
            indexingInProgress.set(false);
        }
    }

    /**
     * Index toàn bộ plants đã xuất bản vào chunk_embeddings.
     * 
     * THAY ĐỔI: Tách thành 2 phase:
     * Phase 1: Delete old + tạo chunks (DB operations, dùng @Transactional ngắn)
     * Phase 2: Gọi Cloudflare API batch embed (KHÔNG giữ DB connection)
     * Phase 3: Lưu embeddings vào DB (@Transactional ngắn)
     */
    public int indexAllPlants() {
        log.info("Bắt đầu index tất cả plants...");

        // Phase 1: Delete old data (short transaction via separate service)
        chunkPersistenceService.deleteByType(ChunkEmbedding.ContentType.plant);

        // Lấy danh sách plants — TẤT CẢ (không giới hạn 50)
        List<Plant> plants = plantRepository.findAll();

        int count = 0;
        for (Plant plant : plants) {
            if (plant.getPlantStatus() != null &&
                    plant.getPlantStatus() == com.web.enums.PlantStatus.DA_XUAT_BAN) {
                try {
                    indexPlant(plant);
                    count++;
                } catch (Exception e) {
                    log.warn("Lỗi index plant {}: {}", plant.getName(), e.getMessage());
                }
            }
        }
        log.info("Đã index {} plants", count);
        return count;
    }

    /**
     * Index toàn bộ articles đã xuất bản
     */
    public int indexAllArticles() {
        log.info("Bắt đầu index tất cả articles...");
        chunkPersistenceService.deleteByType(ChunkEmbedding.ContentType.article);

        // Lấy TẤT CẢ articles
        List<Article> articles = articleRepository.findAll();
        int count = 0;
        for (Article article : articles) {
            if (article.getArticleStatus() != null &&
                    article.getArticleStatus() == com.web.enums.ArticleStatus.DA_XUAT_BAN) {
                try {
                    indexArticle(article);
                    count++;
                } catch (Exception e) {
                    log.warn("Lỗi index article {}: {}", article.getTitle(), e.getMessage());
                }
            }
        }
        log.info("Đã index {} articles", count);
        return count;
    }

    /**
     * Index toàn bộ research đã xuất bản
     */
    public int indexAllResearch() {
        log.info("Bắt đầu index tất cả research...");
        chunkPersistenceService.deleteByType(ChunkEmbedding.ContentType.research);

        // Lấy TẤT CẢ research
        List<Research> researchList = researchRepository.findAll();
        int count = 0;
        for (Research research : researchList) {
            if (research.getResearchStatus() != null &&
                    research.getResearchStatus() == com.web.enums.ResearchStatus.DA_XUAT_BAN) {
                try {
                    indexResearch(research);
                    count++;
                } catch (Exception e) {
                    log.warn("Lỗi index research {}: {}", research.getTitle(), e.getMessage());
                }
            }
        }
        log.info("Đã index {} research", count);
        return count;
    }

    /**
     * Index toàn bộ folk_remedies đã approved
     */
    public int indexAllFolkRemedies(List<FolkRemedy> folkRemedies) {
        log.info("Bắt đầu index tất cả folk remedies...");
        chunkPersistenceService.deleteByType(ChunkEmbedding.ContentType.folk_remedy);

        if (folkRemedies == null) {
            return 0;
        }

        int count = 0;
        for (FolkRemedy fr : folkRemedies) {
            if ("approved".equals(fr.getStatus())) {
                try {
                    indexFolkRemedy(fr);
                    count++;
                } catch (Exception e) {
                    log.warn("Lỗi index folk remedy {}: {}", fr.getName(), e.getMessage());
                }
            }
        }
        log.info("Đã index {} folk remedies", count);
        return count;
    }

    /**
     * Re-index một entity cụ thể
     */
    public void reindexEntity(ChunkEmbedding.ContentType contentType, Long entityId) {
        chunkPersistenceService.deleteByTypeAndEntity(contentType, entityId);

        switch (contentType) {
            case plant:
                plantRepository.findById(entityId).ifPresent(this::indexPlant);
                break;
            case article:
                articleRepository.findById(entityId).ifPresent(this::indexArticle);
                break;
            case research:
                researchRepository.findById(entityId).ifPresent(this::indexResearch);
                break;
            default:
                log.warn("Unsupported content type for reindex: {}", contentType);
        }
    }

    /**
     * Tạo embedding cho một đoạn text (dùng Cloudflare Worker).
     * Dùng cho embedding câu hỏi khi search (single text).
     * 
     * @return List<Double> vector embedding, hoặc empty list nếu lỗi
     */
    public List<Double> createEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Double> result = cloudflareAIService.embed(text);
        if (result != null && !result.isEmpty()) {
            return result;
        }

        log.warn("Cloudflare embedding trả về rỗng cho text: {}...",
                text.substring(0, Math.min(50, text.length())));
        return new ArrayList<>();
    }

    /**
     * Tính cosine similarity giữa 2 vector
     */
    public static double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA == null || vecB == null || vecA.isEmpty() || vecB.isEmpty() || vecA.size() != vecB.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.size(); i++) {
            dotProduct += vecA.get(i) * vecB.get(i);
            normA += vecA.get(i) * vecA.get(i);
            normB += vecB.get(i) * vecB.get(i);
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0) return 0.0;

        return dotProduct / denominator;
    }

    /**
     * Parse embedding JSON string thành List<Double>
     */
    public List<Double> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JsonArray arr = JsonParser.parseString(embeddingJson).getAsJsonArray();
            List<Double> result = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                result.add(el.getAsDouble());
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Lấy trạng thái indexing hiện tại
     */
    public String getIndexingStatus() {
        return indexingStatus;
    }

    public int getIndexingProgress() {
        return indexingProgress.get();
    }

    public boolean isIndexing() {
        return indexingInProgress.get();
    }

    // ================================================
    // PRIVATE METHODS: Chunking & Indexing
    // (DB operations delegated to ChunkPersistenceService)
    // ================================================

    private void indexPlant(Plant plant) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Cây dược liệu: ").append(nullSafe(plant.getName())).append("\n");
        fullText.append("Tên khoa học: ").append(nullSafe(plant.getScientificName())).append("\n");
        fullText.append("Tên khác: ").append(nullSafe(plant.getOtherNames())).append("\n");
        fullText.append("Họ: ").append(plant.getFamilies() != null ? plant.getFamilies().getName() : "").append("\n");
        fullText.append("Chi: ").append(nullSafe(plant.getGenus())).append("\n");
        fullText.append("Bộ phận dùng: ").append(nullSafe(plant.getPartsUsed())).append("\n");
        fullText.append("Mô tả: ").append(stripHtml(nullSafe(plant.getDescription()))).append("\n");
        fullText.append("Đặc điểm hình thái: ").append(stripHtml(nullSafe(plant.getBotanicalCharacteristics()))).append("\n");
        fullText.append("Thành phần hóa học: ").append(stripHtml(nullSafe(plant.getChemicalComposition()))).append("\n");
        fullText.append("Phân bố: ").append(stripHtml(nullSafe(plant.getDistribution()))).append("\n");
        fullText.append("Sinh thái: ").append(stripHtml(nullSafe(plant.getEcology()))).append("\n");
        fullText.append("Công dụng y học: ").append(stripHtml(nullSafe(plant.getMedicinalUses()))).append("\n");
        fullText.append("Chỉ định: ").append(stripHtml(nullSafe(plant.getIndications()))).append("\n");
        fullText.append("Chống chỉ định: ").append(stripHtml(nullSafe(plant.getContraindications()))).append("\n");
        fullText.append("Liều dùng: ").append(stripHtml(nullSafe(plant.getDosage()))).append("\n");
        fullText.append("Bài thuốc dân gian: ").append(stripHtml(nullSafe(plant.getFolkRemedies()))).append("\n");
        fullText.append("Tác dụng phụ: ").append(stripHtml(nullSafe(plant.getSideEffects()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        createAndSaveChunksWithBatchEmbedding(ChunkEmbedding.ContentType.plant, plant.getId(),
                plant.getSlug(), plant.getName(), chunks);
    }

    private void indexArticle(Article article) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Bài viết: ").append(nullSafe(article.getTitle())).append("\n");
        fullText.append("Tóm tắt: ").append(stripHtml(nullSafe(article.getExcerpt()))).append("\n");
        fullText.append("Nội dung: ").append(stripHtml(nullSafe(article.getContent()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        createAndSaveChunksWithBatchEmbedding(ChunkEmbedding.ContentType.article, article.getId(),
                article.getSlug(), article.getTitle(), chunks);
    }

    private void indexResearch(Research research) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Nghiên cứu: ").append(nullSafe(research.getTitle())).append("\n");
        fullText.append("Tác giả: ").append(nullSafe(research.getAuthors())).append("\n");
        fullText.append("Tóm tắt: ").append(stripHtml(nullSafe(research.getAbstractText()))).append("\n");
        fullText.append("Nội dung: ").append(stripHtml(nullSafe(research.getContent()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        createAndSaveChunksWithBatchEmbedding(ChunkEmbedding.ContentType.research, research.getId(),
                research.getSlug(), research.getTitle(), chunks);
    }

    private void indexFolkRemedy(FolkRemedy fr) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Bài thuốc dân gian: ").append(nullSafe(fr.getName())).append("\n");
        fullText.append("Mô tả: ").append(stripHtml(nullSafe(fr.getDescription()))).append("\n");
        fullText.append("Cách dùng: ").append(stripHtml(nullSafe(fr.getUsageInstruction()))).append("\n");
        fullText.append("Cách bào chế: ").append(stripHtml(nullSafe(fr.getPreparation()))).append("\n");
        fullText.append("Chống chỉ định: ").append(stripHtml(nullSafe(fr.getContraindication()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        createAndSaveChunksWithBatchEmbedding(ChunkEmbedding.ContentType.folk_remedy, fr.getId(),
                fr.getSlug(), fr.getName(), chunks);
    }

    /**
     * Tạo chunks, gọi batch embedding (NGOÀI transaction), rồi save vào DB (transaction ngắn).
     * 
     * ĐÂY LÀ THAY ĐỔI QUAN TRỌNG NHẤT:
     * - saveChunks cũ: mỗi chunk → 1 HTTP call + save → tất cả trong 1 @Transactional
     * - Phiên bản mới: tất cả chunks → 1 batch HTTP call (ngoài TX) → 1 batch save (TX ngắn)
     */
    private void createAndSaveChunksWithBatchEmbedding(
            ChunkEmbedding.ContentType contentType, Long entityId,
            String entitySlug, String entityName, List<String> chunks) {

        if (chunks.isEmpty()) return;

        // Filter empty chunks
        List<String> validChunks = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk != null && !chunk.trim().isEmpty()) {
                validChunks.add(chunk);
            }
        }
        if (validChunks.isEmpty()) return;

        // Phase 1: Batch embed — NGOÀI @Transactional, không giữ DB connection
        List<List<Double>> embeddings = Collections.emptyList();
        try {
            // Chia thành sub-batches nếu vượt quá EMBEDDING_BATCH_SIZE
            if (validChunks.size() <= EMBEDDING_BATCH_SIZE) {
                embeddings = cloudflareAIService.batchEmbed(validChunks);
            } else {
                embeddings = new ArrayList<>();
                for (int i = 0; i < validChunks.size(); i += EMBEDDING_BATCH_SIZE) {
                    int end = Math.min(i + EMBEDDING_BATCH_SIZE, validChunks.size());
                    List<String> subBatch = validChunks.subList(i, end);
                    List<List<Double>> subResult = cloudflareAIService.batchEmbed(subBatch);
                    if (subResult != null) {
                        embeddings.addAll(subResult);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Batch embedding lỗi cho {} {}: {}", contentType, entityName, e.getMessage());
        }

        // Phase 2: Tạo ChunkEmbedding entities
        List<ChunkEmbedding> chunkEntities = new ArrayList<>();
        for (int i = 0; i < validChunks.size(); i++) {
            ChunkEmbedding ce = new ChunkEmbedding();
            ce.setContentType(contentType);
            ce.setEntityId(entityId);
            ce.setEntitySlug(entitySlug);
            ce.setEntityName(entityName);
            ce.setChunkText(validChunks.get(i));

            // Set embedding nếu có
            if (embeddings != null && i < embeddings.size() && embeddings.get(i) != null && !embeddings.get(i).isEmpty()) {
                ce.setEmbedding(gson.toJson(embeddings.get(i)));
            }

            chunkEntities.add(ce);
        }

        // Phase 3: Save batch — transaction ngắn via ChunkPersistenceService (tránh self-invocation)
        chunkPersistenceService.saveBatch(chunkEntities);

        log.debug("Đã index {} chunks cho {} '{}'", chunkEntities.size(), contentType, entityName);
    }

    /**
     * Tách text dài thành các chunk nhỏ hơn, có overlap
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String cleanText = text.trim();
        if (cleanText.length() <= MAX_CHUNK_SIZE) {
            chunks.add(cleanText);
            return chunks;
        }

        int start = 0;
        while (start < cleanText.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, cleanText.length());

            // Tìm điểm cắt tự nhiên (cuối câu hoặc cuối dòng)
            if (end < cleanText.length()) {
                int lastNewline = cleanText.lastIndexOf('\n', end);
                int lastPeriod = cleanText.lastIndexOf('.', end);
                int breakPoint = Math.max(lastNewline, lastPeriod);

                if (breakPoint > start + MAX_CHUNK_SIZE / 2) {
                    end = breakPoint + 1;
                }
            }

            chunks.add(cleanText.substring(start, end).trim());
            
            // Ensure start advances strictly to avoid infinite loop
            int nextStart = end - CHUNK_OVERLAP;
            if (nextStart <= start) {
                nextStart = start + 1; // force advance if overlap is too large
            }
            start = nextStart;
            
            // Tránh vòng lặp vô hạn
            if (start >= cleanText.length()) break;
        }

        return chunks;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    /**
     * Loại bỏ HTML tags khỏi text
     */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ================================================
    // DTO: Indexing Result
    // ================================================

    /**
     * DTO chứa kết quả indexing
     */
    public static class IndexingResult {
        private final int plantsIndexed;
        private final int articlesIndexed;
        private final int researchIndexed;
        private final int folkRemediesIndexed;
        private final String status;

        public IndexingResult(int plantsIndexed, int articlesIndexed,
                              int researchIndexed, int folkRemediesIndexed, String status) {
            this.plantsIndexed = plantsIndexed;
            this.articlesIndexed = articlesIndexed;
            this.researchIndexed = researchIndexed;
            this.folkRemediesIndexed = folkRemediesIndexed;
            this.status = status;
        }

        public int getPlantsIndexed() { return plantsIndexed; }
        public int getArticlesIndexed() { return articlesIndexed; }
        public int getResearchIndexed() { return researchIndexed; }
        public int getFolkRemediesIndexed() { return folkRemediesIndexed; }
        public String getStatus() { return status; }
    }
}
