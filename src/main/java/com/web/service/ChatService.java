package com.web.service;

import com.google.gson.*;
import com.web.entity.Plant;
import com.web.repository.PlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
import java.util.stream.Collectors;

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
    private PlantRepository plantRepository;

    @Autowired
    private RagPipelineService ragPipelineService;

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

    private String identifyPlantNameFromImage(String imageUrl) {
        try {
            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");

            JsonArray parts = new JsonArray();

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text",
                    "Bạn là chuyên gia thực vật. Hãy nhìn vào ảnh sau và cho biết cây này là cây gì. " +
                            "Trả về DUY NHẤT một chuỗi JSON với cấu trúc: {\"common_name\":\"...\",\"scientific_name\":\"...\"}. " +
                            "Nếu không chắc chắn, hãy vẫn đoán tên gần đúng nhất.");
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
                if (obj.has("common_name")) {
                    return obj.get("common_name").getAsString();
                }
                if (obj.has("scientific_name")) {
                    return obj.get("scientific_name").getAsString();
                }
            } catch (Exception e) {
                // Không parse được JSON → trả text thô
            }
            return text;
        } catch (Exception e) {
            log.error("Gemini image identify error", e);
            return null;
        }
    }

    /**
     * Xử lý câu hỏi kèm ảnh với luồng tối ưu:
     * 1. Dùng Gemini nhận diện tên cây từ ảnh
     * 2. Tìm trong database trước
     * 3. Nếu có trong DB → dùng dữ liệu DB (chính xác hơn)
     * 4. Nếu không có → dùng tri thức nền của Gemini
     */
    private String answerWithImageAndDatabase(String userMessage, String imageUrl, String history) {
        try {
            String identifiedPlantName = identifyPlantNameFromImage(imageUrl);
            List<Plant> plantsFromDB = new ArrayList<>();
            
            if (identifiedPlantName != null && !identifiedPlantName.trim().isEmpty()) {
                plantsFromDB.addAll(plantRepository.findByNameContainingIgnoreCase(identifiedPlantName));
                plantsFromDB.addAll(plantRepository.findByScientificNameContainingIgnoreCase(identifiedPlantName));
                plantsFromDB.addAll(plantRepository.findByOtherNamesContainingIgnoreCase(identifiedPlantName));
                Set<Plant> uniquePlants = new HashSet<>(plantsFromDB);
                plantsFromDB = new ArrayList<>(uniquePlants);
            }
            
            String prompt;
            String plantsJsonData = "";
            
            if (!plantsFromDB.isEmpty()) {
                final List<String> fieldsToExcludeByName = Arrays.asList(
                        "description", "createdAt", "updatedAt", "createdBy", "updatedBy"
                );
                final String entityPackagePrefix = "com.web.entity";
                
                Gson gson = new GsonBuilder()
                        .setExclusionStrategies(new ExclusionStrategy() {
                            @Override
                            public boolean shouldSkipField(FieldAttributes f) {
                                Class<?> fieldType = f.getDeclaredClass();
                                if (Collection.class.isAssignableFrom(fieldType) ||
                                        Map.class.isAssignableFrom(fieldType)) {
                                    return true;
                                }
                                if (fieldType.getName().startsWith(entityPackagePrefix) && !fieldType.equals(Plant.class)) {
                                    return true;
                                }
                                return fieldsToExcludeByName.contains(f.getName());
                            }
                            @Override
                            public boolean shouldSkipClass(Class<?> clazz) {
                                return false;
                            }
                        })
                        .create();
                plantsJsonData = gson.toJson(plantsFromDB);
                prompt = """
                    Bạn là trợ lý AI của website quản lý cây dược liệu. Chỉ trả lời bằng tiếng Việt, dạng HTML.
                    
                    Tôi đã nhận diện được cây trong ảnh có thể là: %s
                    Và đã tìm thấy thông tin trong database của hệ thống.
                    
                    Lịch sử chat trước đó:
                    %s
                    
                    Dưới đây là **dữ liệu cây dược liệu** từ database dạng JSON:
                    %s
                    
                    Câu hỏi của người dùng: %s
                    
                    Quy tắc trả lời BẮT BUỘC:
                    1. TUYỆT ĐỐI CHỈ SỬ DỤNG thông tin từ dữ liệu JSON được cung cấp ở trên để trả lời. Không được bịa đặt, tự suy diễn hay sử dụng tri thức nền của bạn.
                    2. Nếu thông tin người dùng hỏi KHÔNG CÓ trong dữ liệu JSON, hãy nói rõ: "Thông tin này hiện chưa có trong cơ sở dữ liệu của hệ thống."
                    3. Không cung cấp bất kỳ thông tin nào nằm ngoài dữ liệu được cung cấp.
                    """.formatted(identifiedPlantName, history, plantsJsonData, 
                            userMessage != null && !userMessage.isBlank() ? userMessage : "Hãy cho tôi biết thông tin về cây này từ cơ sở dữ liệu.");
            } else {
                if (identifiedPlantName != null && !identifiedPlantName.trim().isEmpty()) {
                    prompt = """
                        Bạn là trợ lý AI của website quản lý cây dược liệu. Trả lời bằng tiếng Việt, dạng HTML.
                        
                        Tôi đã nhận diện được cây trong ảnh có thể là: %s
                        Tuy nhiên, cây này KHÔNG CÓ TRONG CƠ SỞ DỮ LIỆU của hệ thống.
                        
                        Lịch sử chat trước đó:
                        %s
                        
                        Câu hỏi của người dùng: %s
                        
                        Quy tắc trả lời BẮT BUỘC:
                        1. Hãy thông báo cho người dùng biết hệ thống nhận diện được cây có thể là "%s" nhưng hiện tại chưa có thông tin về cây này trong cơ sở dữ liệu.
                        2. TUYỆT ĐỐI KHÔNG cung cấp thêm bất kỳ thông tin nào về đặc điểm, công dụng, hay tên khoa học từ tri thức nền của bạn. Chỉ trả lời dựa trên dữ liệu của hệ thống, mà ở đây là không có.
                        """.formatted(identifiedPlantName, history, 
                                userMessage != null && !userMessage.isBlank() ? userMessage : "Đây là cây gì?", identifiedPlantName);
                } else {
                    prompt = """
                        Bạn là trợ lý AI của website quản lý cây dược liệu. Trả lời bằng tiếng Việt, dạng HTML.
                        
                        Lịch sử chat trước đó:
                        %s
                        
                        Câu hỏi của người dùng: %s
                        
                        Quy tắc trả lời BẮT BUỘC:
                        1. Hãy nói với người dùng: "Tôi không thể nhận diện được cây trong ảnh và không tìm thấy thông tin trong cơ sở dữ liệu."
                        2. TUYỆT ĐỐI KHÔNG cố gắng đoán bừa hay tự nghĩ ra thông tin để trả lời.
                        """.formatted(history, 
                                userMessage != null && !userMessage.isBlank() ? userMessage : "Đây là cây gì?");
                }
            }
            
            // Build Gemini request with image — BẮT BUỘC Gemini vì cần vision
            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");

            JsonArray parts = new JsonArray();
            JsonObject partText = new JsonObject();
            partText.addProperty("text", prompt);
            parts.add(partText);

            JsonObject imagePart = buildImagePart(imageUrl);
            if (imagePart != null) {
                parts.add(imagePart);
            }

            userMsg.add("parts", parts);
            contents.add(userMsg);

            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.5);
            root.add("generationConfig", generationConfig);
            root.add("contents", contents);

            // Gọi Gemini với retry
            String responseBody = callGeminiRawWithRetry(root);
            if (responseBody == null) {
                return "❌ Lỗi kết nối với AI. Vui lòng thử lại sau.";
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
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
                log.warn("Gemini response structure unexpected");
                return "⚠️ Không nhận được phản hồi hợp lệ từ AI.";
            } else if (json.has("error")) {
                log.error("Gemini API error");
                return "❌ Lỗi xử lý yêu cầu. Vui lòng thử lại sau.";
            } else {
                log.warn("Unexpected Gemini response");
                return "⚠️ Không nhận được phản hồi từ AI. Vui lòng thử lại.";
            }
        } catch (Exception e) {
            log.error("answerWithImageAndDatabase error", e);
            return "❌ Lỗi hệ thống khi xử lý ảnh. Vui lòng thử lại sau.";
        }
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