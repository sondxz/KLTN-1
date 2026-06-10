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
 
@Service
public class RagPipelineService {
 
    private static final Logger log = LoggerFactory.getLogger(RagPipelineService.class);
 
    // FIX 2: Thay GEMINI_GENERATE_URL hardcode bằng danh sách fallback
    private static final List<String> GEMINI_MODELS = List.of(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-pro-latest",
        "gemini-1.5-flash"
    );
    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
 
    private static final int RETRIEVAL_LIMIT = 20;
    private static final int TOP_K = 5;
    private static final double COSINE_THRESHOLD = 0.70;
    private static final int GEMINI_MAX_RETRIES = 2;
 
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
            Bạn là chuyên gia tư vấn về cây dược liệu Việt Nam. Hãy trả lời câu hỏi của người dùng một cách tự nhiên, thân thiện và chính xác, CHỈ dựa vào thông tin tham khảo bên dưới.

            === THÔNG TIN THAM KHẢO ===
            %s

            === CÂU HỎI ===
            Người dùng hỏi về: %s
            Nội dung: %s

            === CÁCH TRẢ LỜI ===
            - Nếu trong thông tin tham khảo CÓ dữ liệu về "%s": trả lời trực tiếp, rõ ràng, đúng trọng tâm câu hỏi. Không lan man, không liệt kê hết mọi thứ.
            - Nếu KHÔNG có dữ liệu về "%s": chỉ trả lời đúng mẫu: "❌ Hệ thống chưa có thông tin về %s. Bạn có thể thử từ khóa khác hoặc liên hệ chuyên gia nhé."

            === LƯU Ý ===
            - Dùng văn phong tự nhiên như đang trò chuyện, không máy móc, không khuôn mẫu
            - Chỉ trả lời bằng văn bản thuần, không dùng markdown, HTML hay emoji (trừ ❌ khi báo lỗi)
            - Dùng dấu gạch ngang (-) cho danh sách, xuống dòng giữa các ý
            - Nếu có dùng thông tin từ tài liệu tham khảo: ghi "Nguồn: Tên cây" ở cuối (dùng mục "Tên" trong tài liệu, KHÔNG dùng link URL). Nếu không dùng (vd: chào hỏi, cảm ơn) thì KHÔNG thêm nguồn, KHÔNG tự bịa
            - Nếu thiếu thông tin để trả lời trọn vẹn, hãy thành thật nói rõ thay vì bịa đặt
            - Trả lời ngắn gọn, súc tích, không dài dòng
            - Với câu chào hỏi, cảm ơn, hỏi thăm: trả lời tự nhiên, ngắn gọn, có thể gợi ý hỏi về cây dược liệu nếu phù hợp
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
    // MAIN METHOD
    // ================================================
 
    public String processQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "Vui lòng nhập câu hỏi.";
        }
 
        try {
            Map<String, List<String>> extractedEntities = entityExtractorService.extract(question);
            String entityDesc = buildEntityDescription(extractedEntities);
            log.info("RAG: Extracted entities: {}", extractedEntities);

            // Nếu không có entity và câu hỏi ngắn (< 30 ký tự), trả lời trực tiếp không qua RAG
            if (!hasAnyEntity(extractedEntities) && question.trim().length() < 30) {
                return """
                    Xin chào! Tôi là trợ lý AI về cây dược liệu Việt Nam.
                    Bạn cần tôi giúp gì về cây dược liệu, bài thuốc hay cách chữa bệnh bằng thảo dược?
                    """;
            }

            List<ScoredChunk> exactMatches = exactMatchSearch(extractedEntities);
            if (!exactMatches.isEmpty()) {
                log.info("RAG: Exact match found {} chunks for: {}", exactMatches.size(), entityDesc);
                String context = buildContext(exactMatches.stream().limit(TOP_K).collect(Collectors.toList()));
                return generate(context, question, entityDesc);
            }
 
            List<ScoredChunk> retrievedChunks = retrieve(question);
            if (retrievedChunks.isEmpty()) {
                log.info("RAG: Không tìm thấy chunk nào cho: {}", entityDesc);
                return buildNoMatchResponse(extractedEntities);
            }
 
            List<ScoredChunk> verifiedChunks = verifyEntityMatch(extractedEntities, retrievedChunks);
            if (verifiedChunks.isEmpty()) {
                log.info("RAG: Entity verification LOẠI BỎ toàn bộ {} chunks cho: {}",
                        retrievedChunks.size(), entityDesc);
                return buildNoMatchResponse(extractedEntities);
            }
 
            log.info("RAG: Entity verification giữ lại {}/{} chunks", verifiedChunks.size(), retrievedChunks.size());
 
            List<ScoredChunk> rerankedChunks = rerank(verifiedChunks, extractedEntities, TOP_K);
            String context = buildContext(rerankedChunks);
            return generate(context, question, entityDesc);
 
        } catch (Exception e) {
            log.error("RAG pipeline error: {}", e.getMessage(), e);
            return "❌ Lỗi hệ thống khi xử lý câu hỏi. Vui lòng thử lại sau.";
        }
    }
 
    // ================================================
    // LỚP 1: RETRIEVAL
    // ================================================
 
    private List<ScoredChunk> retrieve(String question) {
        Map<Long, ScoredChunk> chunksMap = new LinkedHashMap<>();
 
        try {
            List<ChunkEmbedding> ftsResults = chunkEmbeddingRepository.findByFullTextSearchBoolean(question, RETRIEVAL_LIMIT);
            if (ftsResults.isEmpty()) {
                log.debug("BOOLEAN MODE không có kết quả, fallback NATURAL LANGUAGE MODE");
                ftsResults = chunkEmbeddingRepository.findByFullTextSearch(question, RETRIEVAL_LIMIT);
            }
            for (int i = 0; i < ftsResults.size(); i++) {
                ChunkEmbedding ce = ftsResults.get(i);
                double ftsScore = 1.0 - ((double) i / Math.max(ftsResults.size(), 1));
                chunksMap.computeIfAbsent(ce.getId(), k -> new ScoredChunk(ce)).setFtsScore(ftsScore);
            }
            log.debug("FTS found {} chunks", ftsResults.size());
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
            try {
                List<ChunkEmbedding> ftsResults = chunkEmbeddingRepository.findByFullTextSearch(question, RETRIEVAL_LIMIT);
                for (int i = 0; i < ftsResults.size(); i++) {
                    ChunkEmbedding ce = ftsResults.get(i);
                    double ftsScore = 1.0 - ((double) i / Math.max(ftsResults.size(), 1));
                    chunksMap.computeIfAbsent(ce.getId(), k -> new ScoredChunk(ce)).setFtsScore(ftsScore);
                }
            } catch (Exception e2) {
                log.warn("FTS NATURAL LANGUAGE fallback also failed: {}", e2.getMessage());
            }
        }
 
        try {
            List<Double> questionEmbedding = embeddingService.createEmbedding(question);
            if (!questionEmbedding.isEmpty()) {
                List<ScoredChunk> semanticResults = new ArrayList<>();
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
 
    private List<ScoredChunk> exactMatchSearch(Map<String, List<String>> extractedEntities) {
        List<ScoredChunk> exactMatches = new ArrayList<>();
        for (String name : extractedEntities.getOrDefault("plants", Collections.emptyList())) {
            List<ChunkEmbedding> matches = chunkEmbeddingRepository.findByEntityNameIgnoreCase(name);
            for (ChunkEmbedding ce : matches) {
                ScoredChunk sc = new ScoredChunk(ce);
                sc.setFtsScore(1.0);
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
 
    private List<ScoredChunk> verifyEntityMatch(
            Map<String, List<String>> extractedEntities,
            List<ScoredChunk> chunks) {
 
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
                verified.add(sc);
                continue;
            }
            String chunkNameLower = chunkEntityName.toLowerCase().trim();
            for (String extractedName : allEntityNames) {
                String extractedLower = extractedName.toLowerCase().trim();
                if (chunkNameLower.equals(extractedLower)
                        || chunkNameLower.contains(extractedLower)
                        || extractedLower.contains(chunkNameLower)) {
                    sc.setEntityMatched(true);
                    verified.add(sc);
                    break;
                }
            }
        }
        return verified;
    }
 
    // ================================================
    // BƯỚC 2: RERANK
    // ================================================
 
    private List<ScoredChunk> rerank(List<ScoredChunk> chunks,
            Map<String, List<String>> extractedEntities, int topK) {
        boolean hasEntities = extractedEntities.values().stream().anyMatch(l -> !l.isEmpty());
        for (ScoredChunk sc : chunks) {
            double sourcePriority = SOURCE_PRIORITY.getOrDefault(sc.getChunk().getContentType(), 1.0);
            double combinedScore = (sc.getFtsScore() * 0.35)
                    + (sc.getCosineScore() * 0.35)
                    + (sourcePriority / 1.5 * 0.3);
            if (hasEntities && !sc.isEntityMatched() && sc.getChunk().getEntityName() != null) {
                combinedScore *= 0.5;
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
                switch (ce.getContentType()) {
                    case plant: linkUrl = "/plant-detail/" + ce.getEntitySlug(); break;
                    case article: linkUrl = "/article-detail/" + ce.getEntitySlug(); break;
                    case research: linkUrl = "/research-detail/" + ce.getEntitySlug(); break;
                    case folk_remedy: linkUrl = "/folk-remedy-detail/" + ce.getEntitySlug(); break;
                    default: linkUrl = "#";
                }
            }
            // Gửi context: tên cây nổi bật, link chỉ để tham khảo nội bộ
            String entityName = ce.getEntityName() != null ? ce.getEntityName() : "N/A";
            context.append(String.format(
                    "--- Tài liệu %d [Loại: %s | Tên cây: %s] ---\n",
                    i + 1,
                    getContentTypeLabel(ce.getContentType()),
                    entityName));
            context.append(ce.getChunkText()).append("\n\n");
        }
        return context.toString();
    }
 
    /**
     * Generate: g\u1ECDi Cloudflare Worker tr\u01B0\u1EDBc (nhanh, mi\u1EC5n ph\u00ED), fallback Gemini n\u1EBFu l\u1ED7i.
     */
    private String generate(String context, String question, String entityDesc) {
        // Escape % để tránh lỗi String.format khi context chứa ký tự %
        String safeContext = context.replace("%", "%%");
        String safeEntityDesc = entityDesc.replace("%", "%%");
        String safeQuestion = question.replace("%", "%%");
        String prompt = String.format(PROMPT_TEMPLATE, safeContext, safeEntityDesc, safeQuestion, safeEntityDesc, safeEntityDesc, safeEntityDesc);

        if (cloudflareAIService.isAvailable()) {
            try {
                String cfResponse = cloudflareAIService.chat(prompt);
                if (cfResponse != null && !cfResponse.trim().isEmpty()) {
                    log.debug("S\u1EED d\u1EE5ng Cloudflare Worker cho generate");
                    return cleanResponse(cfResponse);
                }
                log.warn("Cloudflare chat tr\u1EA3 v\u1EC1 r\u1ED7ng, fallback sang Gemini...");
            } catch (Exception e) {
                log.warn("Cloudflare chat l\u1ED7i, fallback sang Gemini: {}", e.getMessage());
            }
        }

        log.debug("S\u1EED d\u1EE5ng Gemini cho generate (fallback)");
        return cleanResponse(callGeminiWithRetry(prompt));
    }

    /**
     * Clean response: strip markdown artifacts, normalize whitespace.
     * Kh\u00F4ng c\u1EA7n strip HTML n\u1EEFa v\u00EC context \u0111\u00E3 s\u1EA1ch.
     */
    private String cleanResponse(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        // Gi\u1EEF nguy\u00EAn c\u00E1c th\u00F4ng b\u00E1o l\u1ED7i c\u00F3 icon
        if (text.startsWith("\u274C") || text.startsWith("\u26A0")) return text.trim();

        // Strip markdown code blocks
        text = text.replaceAll("(?s)```(?:html|json|text)?\\s*\\n?", "");
        text = text.replaceAll("(?s)\\n?```", "");

        // Strip markdown formatting: **bold**, # heading, *italic*
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("\\*(.+?)\\*", "$1");
        text = text.replaceAll("(?m)^#{1,4}\\s+", "");

        // Strip any lingering HTML
        text = text.replaceAll("<[^>]+>", " ");

        // Decode HTML entities
        text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");

        // Normalize whitespace
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.replaceAll(" {2,}", " ");
        return text.trim();
    }
 
    private String buildNoMatchResponse(Map<String, List<String>> extractedEntities) {
        List<String> allNames = new ArrayList<>();
        allNames.addAll(extractedEntities.getOrDefault("plants", Collections.emptyList()));
        allNames.addAll(extractedEntities.getOrDefault("diseases", Collections.emptyList()));
        allNames.addAll(extractedEntities.getOrDefault("remedies", Collections.emptyList()));

        // Chuẩn hóa: bỏ tiền tố "cây " nếu entity đã có sẵn
        List<String> cleanNames = allNames.stream()
                .map(n -> n.replaceFirst("(?i)^cây\\s+", "").trim())
                .collect(Collectors.toList());

        String entityDesc = cleanNames.isEmpty() ? "cây dược liệu này"
                : "\"" + String.join(", ", cleanNames) + "\"";

        StringBuilder sb = new StringBuilder();
        sb.append("❌ Hệ thống hiện chưa có thông tin về ").append(entityDesc).append(".\n\n");
        sb.append("Bạn có thể:\n");
        sb.append("- Kiểm tra lại chính tả tên cây/bệnh/bài thuốc\n");
        sb.append("- Thử tìm kiếm với từ khóa khác\n");
        sb.append("- Liên hệ chuyên gia để được tư vấn thêm\n");

        List<String> similarPlants = findSimilarEntities(cleanNames);
        if (!similarPlants.isEmpty()) {
            sb.append("\nGợi ý cây tương tự bạn có thể quan tâm:\n");
            for (String s : similarPlants) {
                sb.append("- ").append(s).append("\n");
            }
        }

        return sb.toString();
    }

    /** Chuyển tên thành slug đơn giản cho URL */
    private String slugify(String name) {
        if (name == null) return "#";
        return name.toLowerCase()
                .replaceAll("[đ]", "d")
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

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
 
    private String buildEntityDescription(Map<String, List<String>> entities) {
        List<String> parts = new ArrayList<>();
        List<String> plants = entities.getOrDefault("plants", Collections.emptyList());
        List<String> diseases = entities.getOrDefault("diseases", Collections.emptyList());
        List<String> remedies = entities.getOrDefault("remedies", Collections.emptyList());
 
        // Chuẩn hóa: bỏ tiền tố "cây " nếu entity đã có sẵn
        List<String> cleanPlants = plants.stream()
                .map(p -> p.replaceFirst("(?i)^cây\\s+", "").trim())
                .collect(Collectors.toList());

        if (!cleanPlants.isEmpty()) parts.add("Cây: " + String.join(", ", cleanPlants));
        if (!diseases.isEmpty()) parts.add("Bệnh: " + String.join(", ", diseases));
        if (!remedies.isEmpty()) parts.add("Bài thuốc: " + String.join(", ", remedies));

        return parts.isEmpty() ? "cây dược liệu (chưa xác định)" : String.join(" | ", parts);
    }

    private boolean hasAnyEntity(Map<String, List<String>> entities) {
        return entities.values().stream().anyMatch(l -> !l.isEmpty());
    }

    private String callGeminiWithRetry(String prompt) {
        for (String model : GEMINI_MODELS) {
            for (int attempt = 1; attempt <= GEMINI_MAX_RETRIES; attempt++) {
                try {
                    String result = callGemini(prompt, model);
                    if (result != null && !result.startsWith("❌")) {
                        log.debug("Gemini thành công với model: {}", model);
                        return result;
                    }
                } catch (GeminiRetryableException e) {
                    log.warn("Gemini [{}] lỗi retryable (attempt {}/{}): {}",
                            model, attempt, GEMINI_MAX_RETRIES, e.getMessage());
                    if (attempt < GEMINI_MAX_RETRIES) {
                        try {
                            Thread.sleep(1000L * (1L << (attempt - 1)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return "❌ Lỗi hệ thống. Vui lòng thử lại sau.";
                        }
                    }
                } catch (Exception e) {
                    log.warn("Gemini [{}] lỗi không retry: {}", model, e.getMessage());
                    break;
                }
            }
            log.warn("Gemini model [{}] thất bại, thử model tiếp theo...", model);
        }
        log.error("Tất cả Gemini model đều thất bại");
        return "❌ Không thể kết nối với AI. Vui lòng thử lại sau.";
    }
 
    private String callGemini(String prompt, String model) {
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
            generationConfig.addProperty("topP", 0.9);
 
            root.add("contents", contents);
            root.add("generationConfig", generationConfig);
 
            // Build URL động theo model
            String geminiUrl = GEMINI_BASE_URL + model + ":generateContent";
 
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(geminiUrl + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();
 
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
 
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new GeminiRetryableException("Gemini HTTP " + response.statusCode());
            }
 
            if (response.statusCode() != 200) {
                log.error("Gemini [{}] returned status {}: {}",
                        model, response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
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
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Gemini [{}] timeout", model);
            throw new GeminiRetryableException("Gemini timeout");
        } catch (Exception e) {
            log.error("Error calling Gemini [{}]: {}", model, e.getMessage(), e);
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
 
    private static class GeminiRetryableException extends RuntimeException {
        public GeminiRetryableException(String message) { super(message); }
    }
 
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