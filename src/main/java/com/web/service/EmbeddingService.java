package com.web.service;

import com.google.gson.*;
import com.web.entity.*;
import com.web.repository.ChunkEmbeddingRepository;
import com.web.repository.PlantRepository;
import com.web.repository.ArticleRepository;
import com.web.repository.ResearchRepository;
import com.web.repository.FolkRemedyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service quản lý embedding: tạo chunk, gọi Cloudflare Worker Embedding API,
 * lưu vào DB.
 * <p>
 * THAY ĐỔI CHÍNH so với phiên bản cũ:
 * 1. Chuyển embedding sang Cloudflare Worker (không giới hạn request, miễn phí)
 * 2. Batch embedding: gộp nhiều chunk vào 1 request (max 50/batch)
 * 3. Tách @Transactional ra khỏi HTTP call → không giữ DB connection khi gọi
 * API
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
    private FolkRemedyRepository folkRemedyRepository;

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
        try {
            indexingStatus = "running";
            indexingProgress.set(0);
            log.info("===== BẮT ĐẦU ASYNC RAG INDEXING =====");

            BuildResult plantResult = buildAllPlants();
            indexingProgress.set(25);

            BuildResult articleResult = buildAllArticles();
            indexingProgress.set(50);

            BuildResult researchResult = buildAllResearch();
            indexingProgress.set(75);

            BuildResult folkRemedyResult = buildAllFolkRemedies(folkRemedies);
            Map<ChunkEmbedding.ContentType, List<ChunkEmbedding>> newIndex =
                    new EnumMap<>(ChunkEmbedding.ContentType.class);
            newIndex.put(ChunkEmbedding.ContentType.plant, plantResult.chunks);
            newIndex.put(ChunkEmbedding.ContentType.article, articleResult.chunks);
            newIndex.put(ChunkEmbedding.ContentType.research, researchResult.chunks);
            newIndex.put(ChunkEmbedding.ContentType.folk_remedy, folkRemedyResult.chunks);

            int totalChunks = newIndex.values().stream().mapToInt(List::size).sum();
            if (totalChunks == 0 && chunkEmbeddingRepository.count() > 0) {
                throw new IllegalStateException(
                        "Không tìm thấy dữ liệu đã xuất bản; giữ nguyên RAG index hiện tại");
            }

            chunkPersistenceService.replaceAll(newIndex);
            indexingProgress.set(100);

            indexingStatus = "completed";
            log.info("===== HOÀN TẤT RAG INDEXING: plants={}, articles={}, research={}, folkRemedies={} =====",
                    plantResult.entities, articleResult.entities,
                    researchResult.entities, folkRemedyResult.entities);

            return CompletableFuture.completedFuture(
                    new IndexingResult(plantResult.entities, articleResult.entities,
                            researchResult.entities, folkRemedyResult.entities, "success"));

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
        List<ChunkEmbedding> chunks;
        switch (contentType) {
            case plant:
                chunks = plantRepository.findById(entityId)
                        .filter(p -> p.getPlantStatus() == com.web.enums.PlantStatus.DA_XUAT_BAN)
                        .map(this::buildPlantChunks).orElse(Collections.emptyList());
                break;
            case article:
                chunks = articleRepository.findById(entityId)
                        .filter(a -> a.getArticleStatus() == com.web.enums.ArticleStatus.DA_XUAT_BAN)
                        .map(this::buildArticleChunks).orElse(Collections.emptyList());
                break;
            case research:
                chunks = researchRepository.findById(entityId)
                        .filter(r -> r.getResearchStatus() == com.web.enums.ResearchStatus.DA_XUAT_BAN)
                        .map(this::buildResearchChunks).orElse(Collections.emptyList());
                break;
            case folk_remedy:
                chunks = folkRemedyRepository.findById(entityId)
                        .filter(fr -> "approved".equals(fr.getStatus()))
                        .map(this::buildFolkRemedyChunks).orElse(Collections.emptyList());
                break;
            default:
                log.warn("Unsupported content type for reindex: {}", contentType);
                return;
        }
        chunkPersistenceService.replaceEntity(contentType, entityId, chunks);
    }

    /**
     * Đồng bộ lại một entity RAG ở background.
     */
    @Async("ragIndexingExecutor")
    public void syncEntityAsync(ChunkEmbedding.ContentType contentType, Long entityId) {
        try {
            reindexEntity(contentType, entityId);
            log.info("Đã đồng bộ RAG entity type={}, id={}", contentType, entityId);
        } catch (Exception e) {
            log.error("Đồng bộ RAG entity thất bại type={}, id={}; index cũ được giữ nguyên",
                    contentType, entityId, e);
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
        if (denominator == 0)
            return 0.0;

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

    /**
     * Lấy phần trăm tiến trình indexing.
     */
    public int getIndexingProgress() {
        return indexingProgress.get();
    }

    /**
     * Kiểm tra indexing có đang chạy không.
     */
    public boolean isIndexing() {
        return indexingInProgress.get();
    }

    /**
     * Đánh dấu bắt đầu chạy full indexing.
     */
    public boolean beginFullIndexing() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            return false;
        }
        indexingProgress.set(0);
        indexingStatus = "queued";
        return true;
    }

    /**
     * Ghi nhận lỗi khi không dispatch được indexing.
     */
    public void failIndexingDispatch(String message) {
        indexingStatus = "error: " + message;
        indexingInProgress.set(false);
    }

    /**
     * Tạo toàn bộ chunk cho cây đã xuất bản.
     */
    private BuildResult buildAllPlants() {
        List<ChunkEmbedding> chunks = new ArrayList<>();
        int entities = 0;
        for (Plant plant : plantRepository.findAll()) {
            if (plant.getPlantStatus() == com.web.enums.PlantStatus.DA_XUAT_BAN) {
                chunks.addAll(buildPlantChunks(plant));
                entities++;
            }
        }
        return new BuildResult(entities, chunks);
    }

    /**
     * Tạo toàn bộ chunk cho bài viết đã xuất bản.
     */
    private BuildResult buildAllArticles() {
        List<ChunkEmbedding> chunks = new ArrayList<>();
        int entities = 0;
        for (Article article : articleRepository.findAll()) {
            if (article.getArticleStatus() == com.web.enums.ArticleStatus.DA_XUAT_BAN) {
                chunks.addAll(buildArticleChunks(article));
                entities++;
            }
        }
        return new BuildResult(entities, chunks);
    }

    /**
     * Tạo toàn bộ chunk cho nghiên cứu đã xuất bản.
     */
    private BuildResult buildAllResearch() {
        List<ChunkEmbedding> chunks = new ArrayList<>();
        int entities = 0;
        for (Research research : researchRepository.findAll()) {
            if (research.getResearchStatus() == com.web.enums.ResearchStatus.DA_XUAT_BAN) {
                chunks.addAll(buildResearchChunks(research));
                entities++;
            }
        }
        return new BuildResult(entities, chunks);
    }

    /**
     * Tạo toàn bộ chunk cho bài thuốc đã duyệt.
     */
    private BuildResult buildAllFolkRemedies(List<FolkRemedy> folkRemedies) {
        List<ChunkEmbedding> chunks = new ArrayList<>();
        int entities = 0;
        if (folkRemedies != null) {
            for (FolkRemedy folkRemedy : folkRemedies) {
                if ("approved".equals(folkRemedy.getStatus())) {
                    chunks.addAll(buildFolkRemedyChunks(folkRemedy));
                    entities++;
                }
            }
        }
        return new BuildResult(entities, chunks);
    }

    // ================================================
    // PRIVATE METHODS: Chunking & Indexing
    // (DB operations delegated to ChunkPersistenceService)
    // ================================================

    /**
     * Lưu index RAG cho một cây.
     */
    private void indexPlant(Plant plant) {
        chunkPersistenceService.saveBatch(buildPlantChunks(plant));
    }

    /**
     * Xây danh sách chunk từ dữ liệu cây.
     */
    private List<ChunkEmbedding> buildPlantChunks(Plant plant) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Cây dược liệu: ").append(nullSafe(plant.getName())).append("\n");
        fullText.append("Tên khoa học: ").append(nullSafe(plant.getScientificName())).append("\n");
        fullText.append("Tên khác: ").append(nullSafe(plant.getOtherNames())).append("\n");
        fullText.append("Họ: ").append(plant.getFamilies() != null ? plant.getFamilies().getName() : "").append("\n");
        fullText.append("Chi: ").append(nullSafe(plant.getGenus())).append("\n");
        fullText.append("Bộ phận dùng: ").append(nullSafe(plant.getPartsUsed())).append("\n");
        fullText.append("Mô tả: ").append(stripHtml(nullSafe(plant.getDescription()))).append("\n");
        fullText.append("Đặc điểm hình thái: ").append(stripHtml(nullSafe(plant.getBotanicalCharacteristics())))
                .append("\n");
        fullText.append("Thành phần hóa học: ").append(stripHtml(nullSafe(plant.getChemicalComposition())))
                .append("\n");
        fullText.append("Phân bố: ").append(stripHtml(nullSafe(plant.getDistribution()))).append("\n");
        fullText.append("Sinh thái: ").append(stripHtml(nullSafe(plant.getEcology()))).append("\n");
        fullText.append("Công dụng y học: ").append(stripHtml(nullSafe(plant.getMedicinalUses()))).append("\n");
        fullText.append("Chỉ định: ").append(stripHtml(nullSafe(plant.getIndications()))).append("\n");
        fullText.append("Chống chỉ định: ").append(stripHtml(nullSafe(plant.getContraindications()))).append("\n");
        fullText.append("Liều dùng: ").append(stripHtml(nullSafe(plant.getDosage()))).append("\n");
        fullText.append("Bài thuốc dân gian: ").append(stripHtml(nullSafe(plant.getFolkRemedies()))).append("\n");
        fullText.append("Tác dụng phụ: ").append(stripHtml(nullSafe(plant.getSideEffects()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        return createChunkEntitiesWithBatchEmbedding(ChunkEmbedding.ContentType.plant, plant.getId(),
                plant.getSlug(), plant.getName(), chunks);
    }

    /**
     * Lưu index RAG cho một bài viết.
     */
    private void indexArticle(Article article) {
        chunkPersistenceService.saveBatch(buildArticleChunks(article));
    }

    /**
     * Xây danh sách chunk từ bài viết.
     */
    private List<ChunkEmbedding> buildArticleChunks(Article article) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Bài viết: ").append(nullSafe(article.getTitle())).append("\n");
        fullText.append("Tóm tắt: ").append(stripHtml(nullSafe(article.getExcerpt()))).append("\n");
        fullText.append("Nội dung: ").append(stripHtml(nullSafe(article.getContent()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        return createChunkEntitiesWithBatchEmbedding(ChunkEmbedding.ContentType.article, article.getId(),
                article.getSlug(), article.getTitle(), chunks);
    }

    /**
     * Lưu index RAG cho một nghiên cứu.
     */
    private void indexResearch(Research research) {
        chunkPersistenceService.saveBatch(buildResearchChunks(research));
    }

    /**
     * Xây danh sách chunk từ nghiên cứu.
     */
    private List<ChunkEmbedding> buildResearchChunks(Research research) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Nghiên cứu: ").append(nullSafe(research.getTitle())).append("\n");
        fullText.append("Tác giả: ").append(nullSafe(research.getAuthors())).append("\n");
        fullText.append("Tóm tắt: ").append(stripHtml(nullSafe(research.getAbstractText()))).append("\n");
        fullText.append("Nội dung: ").append(stripHtml(nullSafe(research.getContent()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        return createChunkEntitiesWithBatchEmbedding(ChunkEmbedding.ContentType.research, research.getId(),
                research.getSlug(), research.getTitle(), chunks);
    }

    /**
     * Lưu index RAG cho một bài thuốc.
     */
    private void indexFolkRemedy(FolkRemedy fr) {
        chunkPersistenceService.saveBatch(buildFolkRemedyChunks(fr));
    }

    /**
     * Xây danh sách chunk từ bài thuốc dân gian.
     */
    private List<ChunkEmbedding> buildFolkRemedyChunks(FolkRemedy fr) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("Bài thuốc dân gian: ").append(nullSafe(fr.getName())).append("\n");
        fullText.append("Mô tả: ").append(stripHtml(nullSafe(fr.getDescription()))).append("\n");
        fullText.append("Cách dùng: ").append(stripHtml(nullSafe(fr.getUsageInstruction()))).append("\n");
        fullText.append("Cách bào chế: ").append(stripHtml(nullSafe(fr.getPreparation()))).append("\n");
        fullText.append("Chống chỉ định: ").append(stripHtml(nullSafe(fr.getContraindication()))).append("\n");

        List<String> chunks = splitIntoChunks(fullText.toString());
        return createChunkEntitiesWithBatchEmbedding(ChunkEmbedding.ContentType.folk_remedy, fr.getId(),
                fr.getSlug(), fr.getName(), chunks);
    }

    /**
     * Tạo chunks, gọi batch embedding (NGOÀI transaction), rồi save vào DB
     * (transaction ngắn).
     * 
     * ĐÂY LÀ THAY ĐỔI QUAN TRỌNG NHẤT:
     * - saveChunks cũ: mỗi chunk → 1 HTTP call + save → tất cả trong
     * 1 @Transactional
     * - Phiên bản mới: tất cả chunks → 1 batch HTTP call (ngoài TX) → 1 batch save
     * (TX ngắn)
     */
    private List<ChunkEmbedding> createChunkEntitiesWithBatchEmbedding(
            ChunkEmbedding.ContentType contentType, Long entityId,
            String entitySlug, String entityName, List<String> chunks) {

        if (chunks.isEmpty())
            return Collections.emptyList();

        // Filter empty chunks
        List<String> validChunks = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk != null && !chunk.trim().isEmpty()) {
                validChunks.add(chunk);
            }
        }
        if (validChunks.isEmpty())
            return Collections.emptyList();

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
            throw new IllegalStateException("Không thể tạo embedding cho " + entityName, e);
        }

        if (embeddings == null || embeddings.size() != validChunks.size()
                || embeddings.stream().anyMatch(vector -> vector == null || vector.isEmpty())) {
            throw new IllegalStateException("Embedding không đầy đủ cho " + entityName
                    + " (cần " + validChunks.size() + ", nhận "
                    + (embeddings == null ? 0 : embeddings.size()) + ")");
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

            ce.setEmbedding(gson.toJson(embeddings.get(i)));

            chunkEntities.add(ce);
        }

        log.debug("Đã chuẩn bị {} chunks cho {} '{}'", chunkEntities.size(), contentType, entityName);
        return chunkEntities;
    }

    /**
     * Tách text dài thành các chunk nhỏ hơn, có overlap
     */
    List<String> splitIntoChunks(String text) {
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

            // Đã thêm đoạn cuối thì dừng ngay. Nếu tiếp tục áp dụng overlap,
            // cùng 200 ký tự cuối sẽ bị tạo lại nhiều lần.
            if (end >= cleanText.length()) {
                break;
            }

            // Ensure start advances strictly to avoid infinite loop
            int nextStart = end - CHUNK_OVERLAP;
            if (nextStart <= start) {
                nextStart = start + 1; // force advance if overlap is too large
            }
            start = nextStart;

            // Tránh vòng lặp vô hạn
            if (start >= cleanText.length())
                break;
        }

        return chunks;
    }

    /**
     * Chuyển null thành chuỗi rỗng.
     */
    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    /**
     * Loại bỏ HTML tags khỏi text
     */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty())
            return "";
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

    private static class BuildResult {
        private final int entities;
        private final List<ChunkEmbedding> chunks;

        /**
         * Tạo kết quả build chunk nội bộ.
         */
        private BuildResult(int entities, List<ChunkEmbedding> chunks) {
            this.entities = entities;
            this.chunks = chunks;
        }
    }

    /**
     * DTO chứa kết quả indexing
     */
    public static class IndexingResult {
        private final int plantsIndexed;
        private final int articlesIndexed;
        private final int researchIndexed;
        private final int folkRemediesIndexed;
        private final String status;

        /**
         * Tạo kết quả tổng hợp sau khi indexing.
         */
        public IndexingResult(int plantsIndexed, int articlesIndexed,
                int researchIndexed, int folkRemediesIndexed, String status) {
            this.plantsIndexed = plantsIndexed;
            this.articlesIndexed = articlesIndexed;
            this.researchIndexed = researchIndexed;
            this.folkRemediesIndexed = folkRemediesIndexed;
            this.status = status;
        }

        /**
         * Lấy số cây đã index.
         */
        public int getPlantsIndexed() {
            return plantsIndexed;
        }

        /**
         * Lấy số bài viết đã index.
         */
        public int getArticlesIndexed() {
            return articlesIndexed;
        }

        /**
         * Lấy số nghiên cứu đã index.
         */
        public int getResearchIndexed() {
            return researchIndexed;
        }

        /**
         * Lấy số bài thuốc đã index.
         */
        public int getFolkRemediesIndexed() {
            return folkRemediesIndexed;
        }

        /**
         * Lấy trạng thái indexing.
         */
        public String getStatus() {
            return status;
        }
    }
}
