package com.web.service;

import com.google.gson.*;
import com.web.entity.Plant;
import com.web.repository.PlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service xử lý chat với người dùng.
 * 
 * THAY ĐỔI CHÍNH:
 * - Tự động detect request có hình ảnh → bắt buộc dùng Gemini (vision)
 * - Request text thuần → dùng RAG pipeline (đã có dual AI bên trong)
 * - Thêm retry cho Gemini vision calls
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.timeout-seconds:30}")
    private int geminiTimeoutSeconds;

    @Autowired
    private RagPipelineService ragPipelineService;

    @Autowired
    private PlantRepository plantRepository;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("Ảnh kèm theo:\\s*(https?://\\S+)");
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("Ảnh kèm theo:\\s*(data:image/[^\\s]+)");

    /** Số lần retry Gemini vision khi lỗi transient */
    private static final int VISION_MAX_RETRIES = 2;

    private final HttpClient sharedHttpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();


    /**
     * Gửi tin nhắn người dùng — giờ dùng RAG Pipeline thay vì trực tiếp.
     * Nếu có ảnh kèm → vẫn dùng flow nhận diện ảnh cũ (Gemini vision).
     * Nếu text thuần → chuyển qua RAG pipeline 3 lớp.
     *
     * @param userMessage Tin nhắn của người dùng.
     * @return Phản hồi từ RAG/Gemini hoặc thông báo lỗi.
     */
    public String chatWithGemini(String userMessage, HttpSession session) {
        // Lưu lịch sử chat vào session
        if(session.getAttribute("history-chat") == null){
            session.setAttribute("Number", 1);
            session.setAttribute("history-chat", "Lịch sử chat của người dùng:\nCâu 1: "+userMessage+"\n");
        }
        else{
            String his = (String) session.getAttribute("history-chat");
            Integer num = (Integer) session.getAttribute("Number");
            his = his + "\n"+"Câu "+num.toString()+": "+userMessage;
            session.setAttribute("Number", ++num);
            session.setAttribute("history-chat",his);
        }

        try {
            // Auto-detect hình ảnh: kiểm tra cả URL và data URL
            String imageUrl = detectImageUrl(userMessage);
            
            if (imageUrl != null) {
                // Tách phần text ra khỏi URL ảnh
                String cleanMessage = userMessage.split("Ảnh kèm theo:")[0].trim();
                String his = (String) session.getAttribute("history-chat");
                
                // Có hình ảnh → BẮT BUỘC dùng Gemini (Cloudflare không hỗ trợ vision)
                log.info("Phát hiện ảnh kèm theo, sử dụng Gemini Vision");
                return answerWithImageAndDatabase(cleanMessage, imageUrl, his);
            }

            // ===== Text thuần → RAG PIPELINE (đã tích hợp dual AI) =====
            return ragPipelineService.processQuestion(userMessage);

        } catch (Exception e) {
            log.error("Error in chatWithGemini", e);
            return "❌ Lỗi hệ thống. Vui lòng thử lại sau.";
        }
    }

    /**
     * Auto-detect URL hoặc data URL của ảnh trong message.
     * Hỗ trợ cả http(s) URL và data:image base64.
     * 
     * @return URL ảnh hoặc null nếu không có
     */
    private String detectImageUrl(String message) {
        if (message == null) return null;
        
        // Thử HTTP/HTTPS URL trước
        Matcher httpMatcher = IMAGE_URL_PATTERN.matcher(message);
        if (httpMatcher.find()) {
            return httpMatcher.group(1);
        }
        
        // Thử data URL
        Matcher dataMatcher = DATA_URL_PATTERN.matcher(message);
        if (dataMatcher.find()) {
            return dataMatcher.group(1);
        }
        
        return null;
    }

    // ================================================
    // GIỮ NGUYÊN: Logic xử lý ảnh (Gemini Vision)
    // Thêm retry cho Gemini API calls
    // ================================================

    private JsonObject buildImagePart(String imageUrl) {
        if (imageUrl.startsWith("data:image")) {
            return buildImagePartFromDataUrl(imageUrl);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();
            HttpResponse<byte[]> res = sharedHttpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() >= 300) return null;
            HttpHeaders headers = res.headers();
            String mime = headers.firstValue("content-type").orElse("image/jpeg");
            String base64 = Base64.getEncoder().encodeToString(res.body());
            JsonObject part = new JsonObject();
            JsonObject inline = new JsonObject();
            inline.addProperty("mimeType", mime);
            inline.addProperty("data", base64);
            part.add("inline_data", inline);
            return part;
        } catch (Exception ex) {
            log.warn("Lỗi download ảnh: {}", ex.getMessage());
            return null;
        }
    }

    private JsonObject buildImagePartFromDataUrl(String dataUrl) {
        try {
            String[] parts = dataUrl.split(",");
            if (parts.length != 2) return null;
            String meta = parts[0];
            String base64Data = parts[1];
            String mime = "image/jpeg";
            int start = meta.indexOf(":");
            int semi = meta.indexOf(";");
            if (start != -1 && semi != -1 && semi > start) {
                mime = meta.substring(start + 1, semi);
            }
            JsonObject part = new JsonObject();
            JsonObject inline = new JsonObject();
            inline.addProperty("mimeType", mime);
            inline.addProperty("data", base64Data);
            part.add("inline_data", inline);
            return part;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> identifyPlantNameFromImage(String imageUrl) {
        try {
            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");

            JsonArray parts = new JsonArray();

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text",
                    "Bạn là chuyên gia thực vật. Hãy nhìn ảnh và xác định chính xác tên cây.\n" +
                    "QUAN TRỌNG:\n" +
                    "- common_name PHẢI là tên tiếng Việt phổ biến (vd: 'Atiso', 'Nha đam', 'Sâm Ngọc Linh'). KHÔNG dùng tiếng Anh.\n" +
                    "- scientific_name là tên khoa học Latin (vd: 'Cynara scolymus').\n" +
                    "- Nếu bạn CHẮC CHẮN (>=90%) → trả về JSON: {\"common_name\":\"...\",\"scientific_name\":\"...\",\"confidence\":\"high\"}\n" +
                    "- Nếu bạn KHÔNG CHẮC (<90%) → trả về JSON: {\"common_name\":\"...\",\"scientific_name\":\"...\",\"confidence\":\"low\"}\n" +
                    "- Nếu ảnh không phải cây cối → trả về JSON: {\"common_name\":\"\",\"scientific_name\":\"\",\"confidence\":\"none\"}\n" +
                    "- Chỉ trả về JSON, không thêm text nào khác.");
            parts.add(textPart);

            JsonObject imagePart = buildImagePart(imageUrl);
            if (imagePart != null) {
                parts.add(imagePart);
            }

            userMsg.add("parts", parts);
            contents.add(userMsg);

            root.add("contents", contents);

            // Gọi Gemini với retry
            String responseBody = callGeminiRawWithRetry(root);
            if (responseBody == null) return null;

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!json.has("candidates")) {
                return null;
            }
            String text = json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            try {
                // Thử parse JSON từ response
                String cleanText = text.trim();
                if (cleanText.startsWith("```")) {
                    cleanText = cleanText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                }
                JsonObject obj = JsonParser.parseString(cleanText).getAsJsonObject();
                String commonName = obj.has("common_name") ? obj.get("common_name").getAsString() : null;
                String scientificName = obj.has("scientific_name") ? obj.get("scientific_name").getAsString() : null;
                
                // Trả về Map chứa cả 2 tên để caller dùng cho DB lookup
                Map<String, String> result = new HashMap<>();
                if (commonName != null && !commonName.isBlank()) result.put("common_name", commonName);
                if (scientificName != null && !scientificName.isBlank()) result.put("scientific_name", scientificName);
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                // Không parse được JSON → trả text thô
            }
            // Fallback: trả text thô dưới dạng common_name
            Map<String, String> fallback = new HashMap<>();
            fallback.put("common_name", text);
            return fallback;
        } catch (Exception e) {
            log.error("Gemini image identify error", e);
            return null;
        }
    }

    /**
     * Xử lý câu hỏi kèm ảnh với luồng tối ưu:
     * 1. Gemini Vision nhận diện tên cây từ ảnh
     * 2. Nếu không nhận diện được → thông báo ngay, không gọi AI thêm
     * 3. Tạo câu hỏi text từ kết quả nhận diện
     * 4. Đưa qua RAG Pipeline (có entity verification + hybrid search) → chính xác hơn
     */
    private String answerWithImageAndDatabase(String userMessage, String imageUrl, String history) {
        try {
            // 1. Gemini Vision nhận diện — trả về Map với common_name & scientific_name
            Map<String, String> visionResult = identifyPlantNameFromImage(imageUrl);
            
            String identifiedPlantName = null;
            String identifiedScientificName = null;
            if (visionResult != null) {
                identifiedPlantName = visionResult.get("common_name");
                identifiedScientificName = visionResult.get("scientific_name");
            }
            
            // ===== GUARD: Vision không nhận diện được =====
            if ((identifiedPlantName == null || identifiedPlantName.isBlank()) 
                    && (identifiedScientificName == null || identifiedScientificName.isBlank())) {
                return """
                    <div class="rag-no-result">
                        <p>⚠️ <b>Không thể nhận diện cây trong ảnh.</b></p>
                        <p>Vui lòng thử lại với:</p>
                        <ul>
                            <li>Ảnh rõ nét hơn, chụp cận cảnh cây</li>
                            <li>Hoặc mô tả cây bằng văn bản để tôi tra cứu</li>
                        </ul>
                    </div>
                    """;
            }
            
            log.info("Gemini Vision nhận diện cây: common={}, scientific={}", identifiedPlantName, identifiedScientificName);
            
            // 2. Tra cứu trực tiếp trong bảng plants để lấy tên chính xác trong DB
            //    Thử common_name trước, nếu không có thì thử scientific_name
            String dbPlantName = findMatchingPlantName(identifiedPlantName, identifiedScientificName);
            String plantNameForQuery = (dbPlantName != null) ? dbPlantName 
                    : (identifiedPlantName != null && !identifiedPlantName.isBlank()) ? identifiedPlantName 
                    : identifiedScientificName;
            
            if (dbPlantName != null && identifiedPlantName != null 
                    && !dbPlantName.equalsIgnoreCase(identifiedPlantName)) {
                log.info("Đã map tên cây từ Vision '{}' -> tên trong DB '{}'", identifiedPlantName, dbPlantName);
            }
            
            // 3. Tạo câu hỏi text — LUÔN ghép tên cây đã nhận diện để RAG tìm đúng
            //    Tránh bug: userMessage kiểu "đây là cây gì" không chứa tên cây → RAG trả lời sai
            String textQuestion;
            if (userMessage != null && !userMessage.isBlank()) {
                textQuestion = "Người dùng hỏi: \"" + userMessage + "\". "
                        + "Cây trong ảnh được nhận diện là: " + plantNameForQuery + ". "
                        + "Hãy cung cấp thông tin về cây " + plantNameForQuery + ".";
            } else {
                textQuestion = "Cây " + plantNameForQuery + " có đặc điểm, công dụng gì?";
            }
            
            // 4. DÙNG RAG PIPELINE — đã có entity verification + hybrid search
            String ragResponse = ragPipelineService.processQuestion(textQuestion);
            
            // 5. Bao kết quả với prefix nhận diện
            boolean notFound = ragResponse.contains("chưa có thông tin") 
                            || ragResponse.contains("rag-no-result")
                            || ragResponse.contains("không tìm thấy");
            
            // Build tên hiển thị (ưu tiên common_name, fallback scientific_name)
            String visionDisplayName = (identifiedPlantName != null && !identifiedPlantName.isBlank())
                    ? identifiedPlantName
                    : (identifiedScientificName != null) ? identifiedScientificName : "không xác định";
            boolean nameWasMapped = dbPlantName != null && identifiedPlantName != null 
                    && !dbPlantName.equalsIgnoreCase(identifiedPlantName);
            
            if (notFound) {
                String displayName = nameWasMapped
                    ? visionDisplayName + " (tên trong CSDL: " + dbPlantName + ")"
                    : visionDisplayName;
                return String.format("""
                    <p>🔍 Hệ thống nhận diện cây trong ảnh có thể là: <b>%s</b></p>
                    <p>⚠️ Tuy nhiên, cây này hiện chưa có trong cơ sở dữ liệu.</p>
                    """, displayName) + ragResponse;
            }
            
            String displayName = nameWasMapped
                ? visionDisplayName + " (" + dbPlantName + ")"
                : visionDisplayName;
            return String.format("""
                <p>🔍 Hệ thống nhận diện cây trong ảnh là: <b>%s</b></p>
                """, displayName) + ragResponse;

        } catch (Exception e) {
            log.error("Error in answerWithImageAndDatabase", e);
            return "❌ Lỗi hệ thống khi xử lý ảnh. Vui lòng thử lại sau.";
        }
    }

    /**
     * Tìm tên cây khớp trong bảng plants dựa trên tên Gemini Vision trả về.
     * 
     * Chiến lược:
     * 1. Tìm bằng common_name (tiếng Việt) trước — tách từ, tìm trong name/otherNames
     * 2. Nếu không có → tìm bằng scientific_name (Latin) trong scientificName
     * 3. Chọn plant có nhiều từ khớp nhất
     * 
     * @param commonName Tên tiếng Việt từ Vision (vd: "Atiso", "Atiso đỏ"), có thể null
     * @param scientificName Tên khoa học từ Vision (vd: "Cynara scolymus"), có thể null
     * @return Tên cây trong DB nếu tìm thấy, null nếu không tìm thấy
     */
    private String findMatchingPlantName(String commonName, String scientificName) {
        // ===== BƯỚC 1: Tìm bằng common_name (tiếng Việt) =====
        if (commonName != null && !commonName.isBlank()) {
            String result = findPlantByWordMatch(commonName);
            if (result != null) return result;
        }
        
        // ===== BƯỚC 2: Tìm bằng scientific_name (Latin) =====
        if (scientificName != null && !scientificName.isBlank()) {
            String result = findPlantByScientificName(scientificName);
            if (result != null) return result;
        }
        
        return null;
    }
    
    /**
     * Tìm plant bằng cách tách tên thành từng từ và tìm trong name, otherNames.
     */
    private String findPlantByWordMatch(String visionName) {
        try {
            String[] words = visionName.toLowerCase().trim().split("\\s+");
            List<String> searchWords = new ArrayList<>();
            for (String w : words) {
                if (w.length() >= 3) {
                    searchWords.add(w);
                }
            }
            if (searchWords.isEmpty() && words.length > 0) {
                searchWords.add(words[0]);
            }
            if (searchWords.isEmpty()) return null;
            
            Map<Plant, Integer> plantScore = new LinkedHashMap<>();
            
            for (String word : searchWords) {
                for (Plant p : plantRepository.findByNameContainingIgnoreCase(word)) {
                    plantScore.merge(p, 2, Integer::sum);
                }
                for (Plant p : plantRepository.findByOtherNamesContainingIgnoreCase(word)) {
                    plantScore.merge(p, 3, Integer::sum);
                }
            }
            
            if (plantScore.isEmpty()) return null;
            
            Plant bestMatch = plantScore.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            
            if (bestMatch != null && bestMatch.getName() != null) {
                log.debug("Tìm thấy plant trong DB: '{}' (score={}) cho vision '{}'", 
                        bestMatch.getName(), plantScore.get(bestMatch), visionName);
                return bestMatch.getName();
            }
        } catch (Exception e) {
            log.warn("Lỗi khi tra cứu plant name '{}' trong DB: {}", visionName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Tìm plant theo scientific name (Latin).
     * Scientific name thường là duy nhất và ngôn ngữ-độc-lập → độ chính xác cao.
     */
    private String findPlantByScientificName(String scientificName) {
        try {
            // Tách scientific name thành từng từ (vd: "Cynara scolymus" → ["cynara", "scolymus"])
            String[] words = scientificName.toLowerCase().trim().split("\\s+");
            
            // Tìm ít nhất 2 từ khớp trong scientificName của DB
            Map<Plant, Integer> plantScore = new LinkedHashMap<>();
            
            for (String word : words) {
                if (word.length() < 3) continue;
                for (Plant p : plantRepository.findByScientificNameContainingIgnoreCase(word)) {
                    plantScore.merge(p, 1, Integer::sum);
                }
            }
            
            if (plantScore.isEmpty()) {
                // Fallback: tìm toàn bộ scientific name
                for (Plant p : plantRepository.findByScientificNameContainingIgnoreCase(scientificName)) {
                    plantScore.put(p, 1);
                }
            }
            
            if (plantScore.isEmpty()) return null;
            
            Plant bestMatch = plantScore.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            
            if (bestMatch != null && bestMatch.getName() != null) {
                log.debug("Tìm thấy plant qua scientific name: '{}' (sci={})", 
                        bestMatch.getName(), bestMatch.getScientificName());
                return bestMatch.getName();
            }
        } catch (Exception e) {
            log.warn("Lỗi khi tra cứu scientific name '{}': {}", scientificName, e.getMessage());
        }
        return null;
    }

    /**
     * Gọi Gemini API với request body đã build sẵn, có retry.
     * Dùng cho cả identify image và answer with image.
     * 
     * @param requestBody JsonObject đã build sẵn (contents, generationConfig, etc.)
     * @return Response body string, hoặc null nếu lỗi sau retry
     */
    private String callGeminiRawWithRetry(JsonObject requestBody) {
        for (int attempt = 1; attempt <= VISION_MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GEMINI_URL + "?key=" + geminiApiKey))
                        .header("Content-Type", "application/json")
                        .timeout(java.time.Duration.ofSeconds(geminiTimeoutSeconds))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                        .build();

                HttpResponse<String> response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                }

                // Retryable status
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    log.warn("Gemini Vision API status {} (attempt {}/{}), retry...",
                            response.statusCode(), attempt, VISION_MAX_RETRIES);
                } else {
                    // Non-retryable
                    log.error("Gemini Vision API returned status {}", response.statusCode());
                    return null;
                }

            } catch (java.net.http.HttpTimeoutException e) {
                log.warn("Gemini Vision API timeout (attempt {}/{})", attempt, VISION_MAX_RETRIES);
            } catch (Exception e) {
                log.error("Gemini Vision API error (attempt {}/{}): {}", attempt, VISION_MAX_RETRIES, e.getMessage());
                return null; // Non-retryable
            }

            // Exponential backoff: 2s, 4s
            if (attempt < VISION_MAX_RETRIES) {
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        log.error("Gemini Vision API thất bại sau {} lần retry", VISION_MAX_RETRIES);
        return null;
    }
}