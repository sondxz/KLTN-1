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

    /** Cosine similarity threshold — chỉ giữ chunk >= 0.70 */
    private static final double COSINE_THRESHOLD = 0.70;

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
            Bạn là chuyên gia tư vấn cây dược liệu Việt Nam.
            
            NGƯỜI DÙNG ĐANG HỎI VỀ: %s
            
            Tài liệu tham khảo (CHỈ dùng nếu thực sự liên quan đến thực thể trên):
            %s
            
            Câu hỏi: %s
            
            QUY TẮC BẮT BUỘC (vi phạm = câu trả lời SAI):
            1. KIỂM TRA ĐẦU TIÊN: Tài liệu trên có thực sự nói về "%s" không?
               - Nếu KHÔNG → trả lời CHÍNH XÁC câu: "Hệ thống hiện tại chưa có thông tin về %s. Bạn có thể thử tìm kiếm cây khác."
               - Nếu CÓ → tiếp tục bước 2.
            2. Chỉ dùng thông tin từ tài liệu trên. Không thêm bất kỳ kiến thức ngoài nào.
            3. BẮT BUỘC liệt kê Nguồn tham khảo ở cuối dạng HTML, lấy chính xác thẻ <a href="...">...</a> từ tài liệu.
            4. Trả lời bằng tiếng Việt, định dạng HTML đẹp, dễ đọc.
            5. Không đưa ra lời khuyên y tế thay thế bác sĩ.
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

    @Autowired
    private EntityExtractorService entityExtractorService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ================================================
    // MAIN METHOD: Xử lý câu hỏi qua RAG Pipeline
    // ================================================

    /**
     * Xử lý câu hỏi qua RAG pipeline nâng cấp:
     * 0. Entity Extraction (AI primary, Regex fallback)
     * 0.5. Exact Match Search
     * 1. RETRIEVAL: Hybrid search (FTS Boolean + Semantic cosine)
     * 1.5. ENTITY VERIFICATION: Lọc chunk không khớp thực thể
     * 2. RERANK: Sắp xếp + penalty entity mismatch
     * 3. GENERATE: Ghép context vào prompt, gọi AI
     * 
     * @param question Câu hỏi của người dùng
     * @return Câu trả lời HTML đã có trích dẫn nguồn
     */
    public String processQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "Vui lòng nhập câu hỏi.";
        }

        try {
            // ===== BƯỚC 0: ENTITY EXTRACTION =====
            Map<String, List<String>> extractedEntities = entityExtractorService.extract(question);
            String entityDesc = buildEntityDescription(extractedEntities);
            log.info("RAG: Extracted entities: {}", extractedEntities);

            // ===== BƯỚC 0.5: EXACT MATCH SEARCH =====
            List<ScoredChunk> exactMatches = exactMatchSearch(extractedEntities);
            if (!exactMatches.isEmpty()) {
                log.info("RAG: Exact match found {} chunks for: {}", exactMatches.size(), entityDesc);
                String context = buildContext(exactMatches.stream().limit(TOP_K).collect(Collectors.toList()));
                return generate(context, question, entityDesc);
            }

            // ===== BƯỚC 1: RETRIEVAL =====
            List<ScoredChunk> retrievedChunks = retrieve(question);

            if (retrievedChunks.isEmpty()) {
                log.info("RAG: Không tìm thấy chunk nào cho: {}", entityDesc);
                return buildNoMatchResponse(extractedEntities);
            }

            // ===== BƯỚC 1.5: ENTITY VERIFICATION =====
            List<ScoredChunk> verifiedChunks = verifyEntityMatch(extractedEntities, retrievedChunks);

            if (verifiedChunks.isEmpty()) {
                log.info("RAG: Entity verification LOẠI BỎ toàn bộ {} chunks cho: {}", 
                        retrievedChunks.size(), entityDesc);
                return buildNoMatchResponse(extractedEntities);
            }

            log.info("RAG: Entity verification giữ lại {}/{} chunks", verifiedChunks.size(), retrievedChunks.size());

            // ===== BƯỚC 2: RERANK (có entity penalty) =====
            List<ScoredChunk> rerankedChunks = rerank(verifiedChunks, extractedEntities, TOP_K);

            // ===== BƯỚC 3: GENERATE =====
            String context = buildContext(rerankedChunks);
            return generate(context, question, entityDesc);

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

        // --- A) MySQL Full-Text Search: BOOLEAN MODE (primary — kiểm soát chặt) ---
        try {
            List<ChunkEmbedding> ftsResults = chunkEmbeddingRepository.findByFullTextSearchBoolean(question, RETRIEVAL_LIMIT);
            
            // Nếu BOOLEAN MODE không trả kết quả → fallback sang NATURAL LANGUAGE MODE
            if (ftsResults.isEmpty()) {
                log.debug("BOOLEAN MODE không có kết quả, fallback NATURAL LANGUAGE MODE");
                ftsResults = chunkEmbeddingRepository.findByFullTextSearch(question, RETRIEVAL_LIMIT);
            }
            
            for (int i = 0; i < ftsResults.size(); i++) {
                ChunkEmbedding ce = ftsResults.get(i);
                double ftsScore = 1.0 - ((double) i / Math.max(ftsResults.size(), 1));
                chunksMap.computeIfAbsent(ce.getId(), k -> new ScoredChunk(ce))
                        .setFtsScore(ftsScore);
            }
            log.debug("FTS found {} chunks", ftsResults.size());
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
            // Fallback to NATURAL LANGUAGE MODE if BOOLEAN fails
            try {
                List<ChunkEmbedding> ftsResults = chunkEmbeddingRepository.findByFullTextSearch(question, RETRIEVAL_LIMIT);
                for (int i = 0; i < ftsResults.size(); i++) {
                    ChunkEmbedding ce = ftsResults.get(i);
                    double ftsScore = 1.0 - ((double) i / Math.max(ftsResults.size(), 1));
                    chunksMap.computeIfAbsent(ce.getId(), k -> new ScoredChunk(ce))
                            .setFtsScore(ftsScore);
                }
            } catch (Exception e2) {
                log.warn("FTS NATURAL LANGUAGE fallback also failed: {}", e2.getMessage());
            }
        }

        // --- B) Semantic Search ---
        try {
            List<Double> questionEmbedding = embeddingService.createEmbedding(question);
            if (!questionEmbedding.isEmpty()) {
                List<ScoredChunk> semanticResults = new ArrayList<>();
                
                // Lấy top 300 chunks từ FTS để rerank
                List<ChunkEmbedding> candidateChunks = chunkEmbeddingRepository.findByFullTextSearchBoolean(question, 300);
                
                
                for (ChunkEmbedding ce : candidateChunks) {
                    if (ce.getEmbedding() != null && !ce.getEmbedding().isEmpty()) {
                        List<Double> chunkEmb = embeddingService.parseEmbedding(ce.getEmbedding());
                        if (!chunkEmb.isEmpty()) {
                            double cosine = EmbeddingService.cosineSimilarity(questionEmbedding, chunkEmb);
                            if (cosine >= COSINE_THRESHOLD) {
                                ScoredChunk sc = new ScoredChunk(ce);
                                sc.setCosineScore(cosine);
                                semanticResults.add(sc);
                            }
                        }
                    }
                }

                semanticResults.sort((a, b) -> Double.compare(b.getCosineScore(), a.getCosineScore()));
                int limit = Math.min(RETRIEVAL_LIMIT, semanticResults.size());
                for (int i = 0; i < limit; i++) {
                    ScoredChunk sc = semanticResults.get(i);
                    chunksMap.merge(sc.getChunk().getId(), sc, (existing, newer) -> {
                        existing.setCosineScore(newer.getCosineScore());
                        return existing;
                    });
                }
                log.debug("Semantic search found {} chunks above threshold {}", semanticResults.size(), COSINE_THRESHOLD);
            }
        } catch (Exception e) {
            log.warn("Semantic search failed: {}", e.getMessage());
        }

        return new ArrayList<>(chunksMap.values());
    }

    // ================================================
    // BƯỚC 0.5: EXACT MATCH SEARCH
    // ================================================

    /**
     * Tìm exact match trong DB dựa trên danh sách entity đã extract.
     * Nếu tìm thấy entityName khớp chính xác → trả về chunk luôn, không cần hybrid search.
     */
    private List<ScoredChunk> exactMatchSearch(Map<String, List<String>> extractedEntities) {
        List<ScoredChunk> exactMatches = new ArrayList<>();
        
        // Tìm plants
        for (String name : extractedEntities.getOrDefault("plants", Collections.emptyList())) {
            List<ChunkEmbedding> matches = chunkEmbeddingRepository.findByEntityNameIgnoreCase(name);
            for (ChunkEmbedding ce : matches) {
                ScoredChunk sc = new ScoredChunk(ce);
                sc.setFtsScore(1.0);  // max score for exact match
                sc.setCosineScore(1.0);
                sc.setCombinedScore(1.0);
                exactMatches.add(sc);
            }
        }
        
        return exactMatches;
    }

    // ================================================
    // BƯỚC 1.5: ENTITY VERIFICATION
    // ================================================

    /**
     * Kiểm tra chunk có thực sự liên quan đến thực thể người dùng hỏi không.
     * So sánh entityName của chunk với danh sách tên đã extract.
     * 
     * Chiến lược matching:
     * - Exact match (equalsIgnoreCase)
     * - Contains match (tên người dùng ⊂ entityName hoặc ngược lại)
     * - Nếu không có entity nào được extract → giữ lại tất cả (fallback an toàn)
     */
    private List<ScoredChunk> verifyEntityMatch(
            Map<String, List<String>> extractedEntities, 
            List<ScoredChunk> chunks) {
        
        // Nếu không extract được entity nào → giữ lại tất cả (không lọc)
        List<String> allEntityNames = new ArrayList<>();
        allEntityNames.addAll(extractedEntities.getOrDefault("plants", Collections.emptyList()));
        allEntityNames.addAll(extractedEntities.getOrDefault("diseases", Collections.emptyList()));
        allEntityNames.addAll(extractedEntities.getOrDefault("remedies", Collections.emptyList()));
        
        if (allEntityNames.isEmpty()) {
            log.debug("Entity Verification: không có entity để verify, giữ lại toàn bộ {} chunks", chunks.size());
            return new ArrayList<>(chunks);
        }
        
        List<ScoredChunk> verified = new ArrayList<>();
        for (ScoredChunk sc : chunks) {
            String chunkEntityName = sc.getChunk().getEntityName();
            if (chunkEntityName == null) {
                // Chunk không có entityName → vẫn giữ (có thể là chunk tổng quát)
                verified.add(sc);
                continue;
            }
            
            String chunkNameLower = chunkEntityName.toLowerCase().trim();
            
            for (String extractedName : allEntityNames) {
                String extractedLower = extractedName.toLowerCase().trim();
                
                // Exact match
                if (chunkNameLower.equals(extractedLower)) {
                    sc.setEntityMatched(true);
                    verified.add(sc);
                    break;
                }
                
                // Contains match (2 chiều)
                if (chunkNameLower.contains(extractedLower) || extractedLower.contains(chunkNameLower)) {
                    sc.setEntityMatched(true);
                    verified.add(sc);
                    break;
                }
            }
        }
        
        return verified;
    }

    // ================================================
    // BƯỚC 2: RERANK (có entity penalty)
    // ================================================

    private List<ScoredChunk> rerank(List<ScoredChunk> chunks, Map<String, List<String>> extractedEntities, int topK) {
        boolean hasEntities = extractedEntities.values().stream().anyMatch(l -> !l.isEmpty());
        
        for (ScoredChunk sc : chunks) {
            double sourcePriority = SOURCE_PRIORITY.getOrDefault(sc.getChunk().getContentType(), 1.0);

            double combinedScore = (sc.getFtsScore() * 0.35)
                    + (sc.getCosineScore() * 0.35)
                    + (sourcePriority / 1.5 * 0.3);

            // Penalty nếu entity name không match (chỉ áp dụng khi có entity extract được)
            if (hasEntities && !sc.isEntityMatched() && sc.getChunk().getEntityName() != null) {
                combinedScore *= 0.5; // Giảm 50% điểm
            }

            sc.setCombinedScore(combinedScore);
        }

        chunks.sort((a, b) -> Double.compare(b.getCombinedScore(), a.getCombinedScore()));
        return chunks.stream().limit(topK).collect(Collectors.toList());
    }

    // ================================================
    // BƯỚC 3: GENERATE
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
     * Generate response: Cloudflare Worker trước, fallback Gemini.
     * Prompt mới có entityDesc để AI biết chính xác người dùng hỏi về cây gì.
     */
    private String generate(String context, String question, String entityDesc) {
        String prompt = String.format(PROMPT_TEMPLATE, entityDesc, context, question, entityDesc, entityDesc);

        // Thử Cloudflare Worker trước
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

        log.debug("Sử dụng Gemini cho generate (fallback)");
        return callGeminiWithRetry(prompt);
    }

    /**
     * Trả về HTML thông báo "không tìm thấy" — KHÔNG gọi AI.
     * Tránh AI tự bịa ra câu trả lời từ tri thức nền.
     * Có kèm gợi ý cây tương tự nếu tìm thấy trong DB.
     */
    private String buildNoMatchResponse(Map<String, List<String>> extractedEntities) {
        List<String> allNames = new ArrayList<>();
        allNames.addAll(extractedEntities.getOrDefault("plants", Collections.emptyList()));
        allNames.addAll(extractedEntities.getOrDefault("diseases", Collections.emptyList()));
        allNames.addAll(extractedEntities.getOrDefault("remedies", Collections.emptyList()));
        
        String entityDesc = allNames.isEmpty() ? "cây dược liệu này" 
                : "\"" + String.join(", ", allNames) + "\"";

        StringBuilder html = new StringBuilder();
        html.append("<div class='rag-no-result'>");
        html.append(String.format(
                "<p><b>Hệ thống hiện tại chưa có thông tin về %s.</b></p>", entityDesc));
        html.append("<p>Bạn có thể:</p><ul>");
        html.append("<li>Kiểm tra lại tên cây/bệnh/bài thuốc</li>");
        html.append("<li>Thử tìm kiếm với từ khóa khác</li>");
        html.append("<li>Liên hệ chuyên gia để được tư vấn</li>");
        html.append("</ul>");

        // Tìm gợi ý cây tương tự
        List<String> similarPlants = findSimilarEntities(allNames);
        if (!similarPlants.isEmpty()) {
            html.append("<p><b>🔎 Cây dược liệu tương tự trong hệ thống:</b></p><ul>");
            for (String s : similarPlants) {
                html.append(String.format("<li>%s</li>", s));
            }
            html.append("</ul>");
        }

        html.append("</div>");
        return html.toString();
    }

    /**
     * Tìm thực thể tương tự trong DB khi không có exact match.
     */
    private List<String> findSimilarEntities(List<String> names) {
        Set<String> similar = new LinkedHashSet<>();
        for (String name : names) {
            if (name.length() >= 3) {
                List<ChunkEmbedding> matches = chunkEmbeddingRepository
                        .findByEntityNameContainingIgnoreCase(name);
                for (ChunkEmbedding ce : matches) {
                    if (ce.getEntityName() != null && ce.getContentType() == ChunkEmbedding.ContentType.plant) {
                        similar.add(ce.getEntityName());
                    }
                }
            }
        }
        return new ArrayList<>(similar).stream().limit(5).collect(Collectors.toList());
    }

    /**
     * Build mô tả thực thể để inject vào prompt.
     */
    private String buildEntityDescription(Map<String, List<String>> entities) {
        List<String> parts = new ArrayList<>();
        List<String> plants = entities.getOrDefault("plants", Collections.emptyList());
        List<String> diseases = entities.getOrDefault("diseases", Collections.emptyList());
        List<String> remedies = entities.getOrDefault("remedies", Collections.emptyList());
        
        if (!plants.isEmpty()) parts.add("Cây: " + String.join(", ", plants));
        if (!diseases.isEmpty()) parts.add("Bệnh: " + String.join(", ", diseases));
        if (!remedies.isEmpty()) parts.add("Bài thuốc: " + String.join(", ", remedies));
        
        return parts.isEmpty() ? "cây dược liệu (không xác định cụ thể)" : String.join(" | ", parts);
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
            generationConfig.addProperty("temperature", 0.0);
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
        private boolean entityMatched;

        public ScoredChunk(ChunkEmbedding chunk) {
            this.chunk = chunk;
            this.ftsScore = 0.0;
            this.cosineScore = 0.0;
            this.combinedScore = 0.0;
            this.entityMatched = false;
        }

        public ChunkEmbedding getChunk() { return chunk; }
        public double getFtsScore() { return ftsScore; }
        public void setFtsScore(double ftsScore) { this.ftsScore = ftsScore; }
        public double getCosineScore() { return cosineScore; }
        public void setCosineScore(double cosineScore) { this.cosineScore = cosineScore; }
        public double getCombinedScore() { return combinedScore; }
        public void setCombinedScore(double combinedScore) { this.combinedScore = combinedScore; }
        public boolean isEntityMatched() { return entityMatched; }
        public void setEntityMatched(boolean entityMatched) { this.entityMatched = entityMatched; }
    }
}
