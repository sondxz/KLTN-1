package com.web.service;

import com.google.gson.*;
import com.web.entity.ChunkEmbedding;
import com.web.repository.ChunkEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG Pipeline Service — 3 lớp:
 * 1. RETRIEVAL: Hybrid search (FTS + Semantic cosine)
 * 2. RERANK: Sắp xếp lại theo điểm tổng hợp
 * 3. GENERATE: Ghép context vào prompt, gọi AI
 * 
 * THAY ĐỔI CHÍNH:
 * - Embedding query dùng Cloudflare Worker (qua EmbeddingService)
 * - Chat text thuần: Cloudflare Worker trước, fallback Gemini nếu lỗi
 * - Chat có hình ảnh: bắt buộc dùng Gemini (Cloudflare không hỗ trợ vision)
 */
@Service
public class RagPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RagPipelineService.class);

    private static final String GEMINI_GENERATE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    /** Số kết quả lấy từ mỗi nguồn ở lớp Retrieval */
    private static final int RETRIEVAL_LIMIT = 20;

    /** Số chunk giữ lại sau Rerank */
    private static final int TOP_K = 5;

    /** Số lần retry Gemini khi lỗi transient */
    private static final int GEMINI_MAX_RETRIES = 2;

    /** Trọng số ưu tiên nguồn (plant > folk_remedy > article > research) */
    private static final Map<ChunkEmbedding.ContentType, Double> SOURCE_PRIORITY;
    static {
        SOURCE_PRIORITY = new HashMap<>();
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.plant, 1.5);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.folk_remedy, 1.3);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.article, 1.1);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.research, 1.0);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.disease, 1.0);
    }

    private static final String PROMPT_TEMPLATE = """
            Bạn là chuyên gia tư vấn cây dược liệu Việt Nam. Chỉ trả lời dựa trên tài liệu được cung cấp bên dưới.
            
            Tài liệu tham khảo:
            %s
            
            Câu hỏi: %s
            
            Quy tắc trả lời:
            1. Tuyệt đối chỉ dùng thông tin từ tài liệu trên. Nếu tài liệu nói về cây A nhưng người dùng hỏi cây B, BẮT BUỘC trả lời: "Hệ thống hiện tại chưa có thông tin về cây này."
            2. BẮT BUỘC liệt kê danh sách Nguồn tham khảo ở cuối câu trả lời dưới dạng mã HTML. Hãy lấy chính xác thẻ <a href="...">...</a> được cung cấp trong Tài liệu tham khảo ở mục "Nguồn Link". (Ví dụ: Nguồn: <a href="/plant-detail/slug">Tên cây</a>)
            3. Nếu tài liệu không đủ để trả lời hoặc không liên quan, hãy nói: "Tôi không có đủ thông tin trong cơ sở dữ liệu để trả lời câu hỏi này."
            4. Nếu câu hỏi mơ hồ, hỏi lại: "Bạn có thể nói rõ hơn về [điểm mơ hồ] không?"
            5. Không đưa ra lời khuyên y tế thay thế bác sĩ.
            6. Trả lời bằng tiếng Việt, định dạng HTML đẹp, dễ đọc.
            """;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.timeout-seconds:60}")
    private int geminiTimeoutSeconds;

    @Autowired
    private ChunkEmbeddingRepository chunkEmbeddingRepository;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private CloudflareAIService cloudflareAIService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ================================================
    // MAIN METHOD: Xử lý câu hỏi qua RAG Pipeline
    // ================================================

    /**
     * Xử lý câu hỏi qua RAG pipeline 3 lớp
     * @param question Câu hỏi của người dùng
     * @return Câu trả lời đã có trích dẫn nguồn
     */
    public String processQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "Vui lòng nhập câu hỏi.";
        }

        try {
            // ===== LỚP 1: RETRIEVAL =====
            List<ScoredChunk> retrievedChunks = retrieve(question);

            if (retrievedChunks.isEmpty()) {
                log.info("RAG: Không tìm thấy chunk nào liên quan cho: {}", question);
                return generateWithoutContext(question);
            }

            // ===== LỚP 2: RERANK =====
            List<ScoredChunk> rerankedChunks = rerank(retrievedChunks, TOP_K);

            // ===== LỚP 3: GENERATE =====
            String context = buildContext(rerankedChunks);
            return generate(context, question);

        } catch (Exception e) {
            log.error("RAG pipeline error: {}", e.getMessage(), e);
            return "❌ Lỗi hệ thống khi xử lý câu hỏi. Vui lòng thử lại sau.";
        }
    }

    // ================================================
    // LỚP 1: RETRIEVAL — Hybrid Search
    // ================================================

    private List<ScoredChunk> retrieve(String question) {
        Map<Long, ScoredChunk> chunksMap = new LinkedHashMap<>();

        // --- A) MySQL Full-Text Search ---
        try {
            List<ChunkEmbedding> ftsResults = chunkEmbeddingRepository.findByFullTextSearch(question, RETRIEVAL_LIMIT);
            for (int i = 0; i < ftsResults.size(); i++) {
                ChunkEmbedding ce = ftsResults.get(i);
                // Điểm FTS giảm dần theo thứ tự (normalize 0-1)
                double ftsScore = 1.0 - ((double) i / Math.max(ftsResults.size(), 1));
                chunksMap.computeIfAbsent(ce.getId(), k -> new ScoredChunk(ce))
                        .setFtsScore(ftsScore);
            }
            log.debug("FTS found {} chunks", ftsResults.size());
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
        }

        // --- B) Semantic Search (Cosine Similarity - Re-ranking Top FTS Results) ---
        // Do MySQL không hỗ trợ Vector Search, việc parse 30k+ JSON embeddings trong Java sẽ gây Timeout/OOM.
        // Giải pháp: Dùng FTS để retrieve top 300 chunks có liên quan nhất, sau đó dùng Embedding để Rerank.
        try {
            List<Double> questionEmbedding = embeddingService.createEmbedding(question);
            if (!questionEmbedding.isEmpty()) {
                List<ScoredChunk> semanticResults = new ArrayList<>();
                
                // Lấy top 300 chunks từ FTS để rerank
                List<ChunkEmbedding> candidateChunks = chunkEmbeddingRepository.findByFullTextSearch(question, 300);
                
                for (ChunkEmbedding ce : candidateChunks) {
                    if (ce.getEmbedding() != null && !ce.getEmbedding().isEmpty()) {
                        List<Double> chunkEmb = embeddingService.parseEmbedding(ce.getEmbedding());
                        if (!chunkEmb.isEmpty()) {
                            double cosine = EmbeddingService.cosineSimilarity(questionEmbedding, chunkEmb);
                            if (cosine > 0.55) { // Threshold tối thiểu 0.55 để tránh ảo giác
                                ScoredChunk sc = new ScoredChunk(ce);
                                sc.setCosineScore(cosine);
                                semanticResults.add(sc);
                            }
                        }
                    }
                }

                // Sắp xếp theo cosine giảm dần, lấy top
                semanticResults.sort((a, b) -> Double.compare(b.getCosineScore(), a.getCosineScore()));
                int limit = Math.min(RETRIEVAL_LIMIT, semanticResults.size());
                for (int i = 0; i < limit; i++) {
                    ScoredChunk sc = semanticResults.get(i);
                    chunksMap.merge(sc.getChunk().getId(), sc, (existing, newer) -> {
                        existing.setCosineScore(newer.getCosineScore());
                        return existing;
                    });
                }
                log.debug("Semantic search found {} chunks above threshold", semanticResults.size());
            }
        } catch (Exception e) {
            log.warn("Semantic search failed: {}", e.getMessage());
        }

        return new ArrayList<>(chunksMap.values());
    }

    // ================================================
    // LỚP 2: RERANK
    // ================================================

    private List<ScoredChunk> rerank(List<ScoredChunk> chunks, int topK) {
        for (ScoredChunk sc : chunks) {
            // Trọng số ưu tiên nguồn
            double sourcePriority = SOURCE_PRIORITY.getOrDefault(sc.getChunk().getContentType(), 1.0);

            // Tính điểm tổng hợp: FTS + Cosine + Source Priority
            double combinedScore = (sc.getFtsScore() * 0.4)
                    + (sc.getCosineScore() * 0.4)
                    + (sourcePriority / 1.5 * 0.2); // Normalize source priority

            sc.setCombinedScore(combinedScore);
        }

        // Sắp xếp theo combined score giảm dần
        chunks.sort((a, b) -> Double.compare(b.getCombinedScore(), a.getCombinedScore()));

        return chunks.stream().limit(topK).collect(Collectors.toList());
    }

    // ================================================
    // LỚP 3: GENERATE — Dual AI với Fallback
    // ================================================

    private String buildContext(List<ScoredChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ScoredChunk sc = chunks.get(i);
            ChunkEmbedding ce = sc.getChunk();
            String linkUrl = "";
            if (ce.getEntitySlug() != null) {
                switch(ce.getContentType()) {
                    case plant: linkUrl = "/plant-detail/" + ce.getEntitySlug(); break;
                    case article: linkUrl = "/article-detail/" + ce.getEntitySlug(); break;
                    case research: linkUrl = "/research-detail/" + ce.getEntitySlug(); break;
                    case folk_remedy: linkUrl = "/folk-remedy-detail/" + ce.getEntitySlug(); break;
                    default: linkUrl = "#";
                }
            }
            context.append(String.format("--- Tài liệu %d [%s: %s | Nguồn Link: <a href=\"%s\" target=\"_blank\">%s</a>] ---\n",
                    i + 1,
                    getContentTypeLabel(ce.getContentType()),
                    ce.getEntityName() != null ? ce.getEntityName() : "N/A",
                    linkUrl,
                    ce.getEntityName() != null ? ce.getEntityName() : "N/A"));
            context.append(ce.getChunkText()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * Generate response: Cloudflare Worker trước, fallback Gemini nếu Cloudflare lỗi.
     */
    private String generate(String context, String question) {
        String prompt = String.format(PROMPT_TEMPLATE, context, question);

        // Thử Cloudflare Worker trước (nhanh hơn, không quota)
        if (cloudflareAIService.isAvailable()) {
            try {
                String cfResponse = cloudflareAIService.chat(prompt);
                if (cfResponse != null && !cfResponse.trim().isEmpty()) {
                    log.debug("Sử dụng Cloudflare Worker cho generate");
                    return cfResponse;
                }
                log.warn("Cloudflare chat trả về rỗng, fallback sang Gemini...");
            } catch (Exception e) {
                log.warn("Cloudflare chat lỗi, fallback sang Gemini: {}", e.getMessage());
            }
        }

        // Fallback: Gemini
        log.debug("Sử dụng Gemini cho generate (fallback)");
        return callGeminiWithRetry(prompt);
    }

    /**
     * Fallback: trả lời khi không tìm thấy context nào
     */
    private String generateWithoutContext(String question) {
        String prompt = """
                Bạn là chuyên gia tư vấn cây dược liệu Việt Nam.
                Người dùng hỏi: %s
                
                Hiện tại không tìm thấy tài liệu liên quan trong hệ thống.
                Hãy trả lời: "Tôi không có đủ thông tin trong cơ sở dữ liệu để trả lời câu hỏi này. 
                Bạn có thể thử hỏi cụ thể hơn về tên cây, công dụng, hoặc bệnh cần điều trị."
                Trả lời bằng tiếng Việt, dạng HTML.
                """.formatted(question);

        // Thử Cloudflare trước
        if (cloudflareAIService.isAvailable()) {
            try {
                String cfResponse = cloudflareAIService.chat(prompt);
                if (cfResponse != null && !cfResponse.trim().isEmpty()) {
                    return cfResponse;
                }
            } catch (Exception e) {
                log.warn("Cloudflare chat lỗi khi generateWithoutContext, fallback Gemini: {}", e.getMessage());
            }
        }

        return callGeminiWithRetry(prompt);
    }

    /**
     * Gọi Gemini API với retry khi gặp lỗi transient (429, 500+).
     * 
     * THAY ĐỔI: Thêm retry với exponential backoff.
     */
    private String callGeminiWithRetry(String prompt) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= GEMINI_MAX_RETRIES; attempt++) {
            try {
                String result = callGemini(prompt);
                if (result != null) {
                    return result;
                }
            } catch (GeminiRetryableException e) {
                log.warn("Gemini API lỗi retryable (attempt {}/{}): {}", attempt, GEMINI_MAX_RETRIES, e.getMessage());
                lastException = e;

                // Exponential backoff
                if (attempt < GEMINI_MAX_RETRIES) {
                    try {
                        Thread.sleep(1000L * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                // Non-retryable error
                log.error("Gemini API lỗi không retry được: {}", e.getMessage());
                return "❌ Lỗi kết nối với AI. Vui lòng thử lại sau.";
            }
        }

        log.error("Gemini API thất bại sau {} lần retry", GEMINI_MAX_RETRIES);
        return "❌ Lỗi kết nối với AI. Vui lòng thử lại sau.";
    }

    /**
     * Gọi Gemini 2.5 Flash API — 1 lần duy nhất.
     * Throw GeminiRetryableException nếu gặp lỗi có thể retry.
     */
    private String callGemini(String prompt) {
        try {
            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");

            JsonArray parts = new JsonArray();
            JsonObject partText = new JsonObject();
            partText.addProperty("text", prompt);
            parts.add(partText);

            userMsg.add("parts", parts);
            contents.add(userMsg);

            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.3);
            generationConfig.addProperty("maxOutputTokens", 2048);

            root.add("contents", contents);
            root.add("generationConfig", generationConfig);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_GENERATE_URL + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new GeminiRetryableException("Gemini HTTP " + response.statusCode());
            }

            if (response.statusCode() != 200) {
                log.error("Gemini API returned status {}", response.statusCode());
                return "❌ Lỗi kết nối với AI. Vui lòng thử lại sau.";
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (json.has("candidates")) {
                JsonArray candidates = json.getAsJsonArray("candidates");
                if (candidates.size() > 0) {
                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    if (candidate.has("content")) {
                        JsonObject content = candidate.getAsJsonObject("content");
                        if (content.has("parts")) {
                            JsonArray responseParts = content.getAsJsonArray("parts");
                            if (responseParts.size() > 0) {
                                JsonObject part = responseParts.get(0).getAsJsonObject();
                                if (part.has("text")) {
                                    return part.get("text").getAsString();
                                }
                            }
                        }
                    }
                }
            }

            return "⚠️ Không nhận được phản hồi hợp lệ từ AI.";

        } catch (GeminiRetryableException e) {
            throw e; // Re-throw để caller xử lý retry
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Gemini API timeout", e);
            throw new GeminiRetryableException("Gemini timeout");
        } catch (Exception e) {
            log.error("Error calling Gemini: {}", e.getMessage(), e);
            return "❌ Lỗi hệ thống. Vui lòng thử lại sau.";
        }
    }

    private String getContentTypeLabel(ChunkEmbedding.ContentType type) {
        switch (type) {
            case plant: return "Cây dược liệu";
            case article: return "Bài viết";
            case research: return "Nghiên cứu";
            case disease: return "Bệnh";
            case folk_remedy: return "Bài thuốc dân gian";
            default: return "Khác";
        }
    }

    // ================================================
    // INNER CLASSES
    // ================================================

    /**
     * Exception cho lỗi Gemini có thể retry (429, 500+, timeout)
     */
    private static class GeminiRetryableException extends RuntimeException {
        public GeminiRetryableException(String message) {
            super(message);
        }
    }

    /**
     * Wrapper cho ChunkEmbedding kèm các điểm số
     */
    public static class ScoredChunk {
        private final ChunkEmbedding chunk;
        private double ftsScore;
        private double cosineScore;
        private double combinedScore;

        public ScoredChunk(ChunkEmbedding chunk) {
            this.chunk = chunk;
            this.ftsScore = 0.0;
            this.cosineScore = 0.0;
            this.combinedScore = 0.0;
        }

        public ChunkEmbedding getChunk() { return chunk; }
        public double getFtsScore() { return ftsScore; }
        public void setFtsScore(double ftsScore) { this.ftsScore = ftsScore; }
        public double getCosineScore() { return cosineScore; }
        public void setCosineScore(double cosineScore) { this.cosineScore = cosineScore; }
        public double getCombinedScore() { return combinedScore; }
        public void setCombinedScore(double combinedScore) { this.combinedScore = combinedScore; }
    }
}
