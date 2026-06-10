package com.web.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * EntityExtractorService — Trích xuất tên thực thể (cây, bệnh, bài thuốc)
 * từ câu hỏi người dùng để phục vụ Entity Verification trong RAG Pipeline.
 * 
 * Chiến lược 3 tầng:
 * - TẦNG 1 (PRIMARY): AI extract qua Cloudflare Worker — prompt siêu ngắn, nhanh, chính xác
 * - TẦNG 2 (FALLBACK): Gemini — dùng khi Cloudflare lỗi (model deprecated, timeout...)
 * - TẦNG 3 (LAST RESORT): Regex — chỉ bắt pattern rõ ràng, dùng khi cả 2 AI đều lỗi
 * 
 * Lý do AI làm primary: Tiếng Việt biến thể quá nhiều, Regex không thể phủ hết
 *   "Sâm ngọc linh chữa được gì?" → không có chữ "cây"
 *   "Tôi muốn hỏi về tam thất"    → không pattern cố định
 *   "húng chanh trị ho ra sao?"   → viết thường, không prefix
 */
@Service
public class EntityExtractorService {

    private static final Logger log = LoggerFactory.getLogger(EntityExtractorService.class);

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    /** Prompt siêu ngắn (~30 tokens input, ~20 tokens output) — rẻ, nhanh */
    private static final String EXTRACT_PROMPT = """
        Trích xuất tên cây dược liệu, bệnh, bài thuốc từ câu hỏi sau.
        Chỉ trả về JSON: {"plants":[], "diseases":[], "remedies":[]}
        Nếu không có thì trả về mảng rỗng.
        Lưu ý: không thêm tiền tố "cây" vào tên cây.
        Câu hỏi: %s
        """;

    // Regex fallback patterns — chỉ bắt được pattern RÕ RÀNG
    private static final Pattern PLANT_PATTERN = Pattern.compile(
            "(?i)cây\\s+(?:dược\\s+liệu\\s+)?([A-ZÀ-Ỹa-zà-ỹ][A-ZÀ-Ỹa-zà-ỹ\\s]+?)(?:\\s+có|\\s+là|\\s+chữa|\\s+trị|\\s+dùng|\\s+để|\\s+ở|$)",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern DISEASE_PATTERN = Pattern.compile(
            "(?i)(?:bệnh|chữa|trị)\\s+([a-zà-ỹ][a-zà-ỹ\\s]+?)(?:\\s+bằng|\\s+từ|\\s+với|\\s+dùng|\\s+ở|$)",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern REMEDY_PATTERN = Pattern.compile(
            "(?i)bài\\s+thuốc\\s+(?:chữa\\s+)?([a-zà-ỹ][a-zà-ỹ\\s]+?)(?:\\s+từ|\\s+bằng|\\s+với|\\s+ở|$)",
            Pattern.UNICODE_CHARACTER_CLASS);

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.timeout-seconds:60}")
    private int geminiTimeoutSeconds;

    @Autowired
    private CloudflareAIService cloudflareAIService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Gson gson = new Gson();

    /**
     * Trích xuất thực thể từ câu hỏi.
     * Chiến lược 3 tầng: Cloudflare → Gemini → Regex
     * @return Map với keys: "plants", "diseases", "remedies"
     */
    public Map<String, List<String>> extract(String question) {
        if (question == null || question.trim().isEmpty()) {
            return emptyResult();
        }

        // ===== TẦNG 1: CLOUDFLARE AI (PRIMARY) =====
        try {
            Map<String, List<String>> cfResult = extractWithCloudflare(question);
            if (cfResult != null && hasAnyEntity(cfResult)) {
                log.debug("Cloudflare extract thành công: {}", cfResult);
                return cfResult;
            }
            log.info("Cloudflare extract không có kết quả, fallback sang Gemini...");
        } catch (Exception e) {
            log.warn("Cloudflare extract lỗi, fallback sang Gemini: {}", e.getMessage());
        }

        // ===== TẦNG 2: GEMINI FALLBACK =====
        try {
            Map<String, List<String>> geminiResult = extractWithGemini(question);
            if (geminiResult != null && hasAnyEntity(geminiResult)) {
                log.debug("Gemini extract thành công: {}", geminiResult);
                return geminiResult;
            }
            log.info("Gemini extract không có kết quả, fallback sang regex...");
        } catch (Exception e) {
            log.warn("Gemini extract lỗi, fallback sang regex: {}", e.getMessage());
        }

        // ===== TẦNG 3: REGEX (LAST RESORT) =====
        Map<String, List<String>> regexResult = extractWithRegex(question);
        log.debug("Regex extract (last resort): {}", regexResult);
        return regexResult;
    }

    /**
     * TẦNG 1: Gọi Cloudflare Worker /chat với prompt extract siêu ngắn.
     */
    private Map<String, List<String>> extractWithCloudflare(String question) {
        if (!cloudflareAIService.isAvailable()) {
            return null;
        }

        String prompt = String.format(EXTRACT_PROMPT, question.replace("%", "%%"));
        String response = cloudflareAIService.chat(prompt);

        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        return parseAIResponse(response, question);
    }

    /**
     * TẦNG 2: Gọi Gemini 2.5 Flash để extract entity khi Cloudflare lỗi.
     * Dùng chung prompt template và parse logic với Cloudflare.
     */
    private Map<String, List<String>> extractWithGemini(String question) {
        try {
            String prompt = String.format(EXTRACT_PROMPT, question.replace("%", "%%"));

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
            generationConfig.addProperty("maxOutputTokens", 256);

            root.add("contents", contents);
            root.add("generationConfig", generationConfig);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(root)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini extract HTTP {}: {}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String text = extractTextFromGeminiResponse(json);

            if (text == null || text.trim().isEmpty()) {
                return null;
            }

            return parseAIResponse(text, question);

        } catch (Exception e) {
            log.warn("Gemini extract exception: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Trích xuất text từ Gemini API response.
     */
    private String extractTextFromGeminiResponse(JsonObject json) {
        try {
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
        } catch (Exception e) {
            log.debug("Parse Gemini response error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Parse JSON response từ AI extract.
     * Hỗ trợ nhiều format: thuần JSON, JSON trong markdown code block...
     * Sau khi parse, filter entity: chỉ giữ entity có ít nhất 1 từ (>=3 ký tự)
     * xuất hiện trong câu hỏi gốc — chống AI tự bịa tên cây khi câu hỏi chung chung.
     */
    Map<String, List<String>> parseAIResponse(String response, String question) {
        try {
            String cleanJson = response.trim();

            // Bỏ markdown code block nếu có
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }

            // Chỉ lấy phần JSON (phòng AI thêm text thừa)
            int jsonStart = cleanJson.indexOf('{');
            int jsonEnd = cleanJson.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
            }

            JsonObject root = JsonParser.parseString(cleanJson).getAsJsonObject();

            List<String> rawPlants = parseStringArray(root, "plants");
            List<String> rawDiseases = parseStringArray(root, "diseases");
            List<String> rawRemedies = parseStringArray(root, "remedies");

            // Chuẩn hóa: bỏ tiền tố "cây " nếu AI thêm vào
            rawPlants = rawPlants.stream()
                    .map(p -> p.replaceFirst("(?i)^cây\\s+", "").trim())
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());

            // Filter: chỉ giữ entity có từ xuất hiện trong câu hỏi gốc
            Map<String, List<String>> result = new LinkedHashMap<>();
            result.put("plants", filterByQuestion(rawPlants, question));
            result.put("diseases", filterByQuestion(rawDiseases, question));
            result.put("remedies", filterByQuestion(rawRemedies, question));

            return result;
        } catch (Exception e) {
            log.debug("Parse AI extract response thất bại: {}", e.getMessage());
            return null;
        }
    }

    // Stopwords tiếng Việt — từ hỏi, từ chức năng không bao giờ là tên thực thể
    private static final Set<String> VI_STOPWORDS = Set.of(
            "nào", "gì", "đâu", "sao", "ai", "bao", "nhiêu", "mấy",
            "à", "nhỉ", "nhé", "chứ", "vậy", "không", "được", "thế",
            "này", "kia", "đó", "đây", "ấy", "nên", "thì", "mà", "là",
            "có", "đã", "sẽ", "đang", "và", "hoặc", "của", "với",
            "cho", "về", "từ", "đến", "bằng", "như", "những", "các"
    );

    /**
     * Filter danh sách entity: chỉ giữ entity có ít nhất 1 từ (>= 3 ký tự)
     * xuất hiện trong câu hỏi gốc, VÀ từ đó KHÔNG phải là stopword tiếng Việt.
     * 
     * VD: question="Cây nào chữa tiểu đường?", entity=["Đinh Lăng"]
     *     → "đinh" không có, "lăng" không có → LOẠI (AI tự bịa)
     * VD: question="Sâm Ngọc Linh chữa gì?", entity=["Sâm Ngọc Linh"]
     *     → "sâm" có, "ngọc" có, "linh" có → GIỮ
     * VD: entity=["nào"] → "nào" là stopword → LOẠI
     */
    private List<String> filterByQuestion(List<String> entities, String question) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        String questionLower = question.toLowerCase();
        List<String> filtered = new ArrayList<>();

        for (String entity : entities) {
            if (entity == null || entity.trim().isEmpty()) {
                continue;
            }

            // Tách entity thành từng từ, kiểm tra từng từ >= 3 ký tự
            String[] words = entity.toLowerCase().split("\\s+");
            boolean found = false;
            for (String word : words) {
                // Bỏ qua stopword — không bao giờ dùng làm tên thực thể
                if (VI_STOPWORDS.contains(word)) {
                    continue;
                }
                if (word.length() >= 3 && questionLower.contains(word)) {
                    found = true;
                    break;
                }
            }

            if (found) {
                filtered.add(entity);
            } else {
                log.debug("Entity \"{}\" bị filter loại bỏ vì không có từ nào xuất hiện trong câu hỏi", entity);
            }
        }

        return filtered;
    }

    /**
     * TẦNG 2: Regex fallback — chỉ bắt pattern rõ ràng.
     * Không kỳ vọng phủ hết mọi biến thể tiếng Việt.
     */
    private Map<String, List<String>> extractWithRegex(String question) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("plants", extractPattern(question, PLANT_PATTERN));
        result.put("diseases", extractPattern(question, DISEASE_PATTERN));
        result.put("remedies", extractPattern(question, REMEDY_PATTERN));
        return result;
    }

    /**
     * Áp dụng regex pattern để trích xuất danh sách kết quả.
     */
    private List<String> extractPattern(String text, Pattern pattern) {
        List<String> results = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String group = matcher.group(1);
            if (group != null && !group.trim().isEmpty()) {
                String cleaned = group.trim().replaceAll("\\s+", " ");
                if (cleaned.length() >= 2 && !results.contains(cleaned)) {
                    results.add(cleaned);
                }
            }
        }
        return results;
    }

    private List<String> parseStringArray(JsonObject root, String key) {
        List<String> result = new ArrayList<>();
        if (root.has(key)) {
            JsonElement elem = root.get(key);
            if (elem.isJsonArray()) {
                JsonArray arr = elem.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (e.isJsonPrimitive()) {
                        String val = e.getAsString().trim();
                        if (!val.isEmpty()) {
                            result.add(val);
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean hasAnyEntity(Map<String, List<String>> result) {
        return result.values().stream().anyMatch(list -> !list.isEmpty());
    }

    private Map<String, List<String>> emptyResult() {
        Map<String, List<String>> empty = new LinkedHashMap<>();
        empty.put("plants", Collections.emptyList());
        empty.put("diseases", Collections.emptyList());
        empty.put("remedies", Collections.emptyList());
        return empty;
    }
}
