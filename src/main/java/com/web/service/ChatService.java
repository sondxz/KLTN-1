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
import java.text.Normalizer;
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
    private RagPipelineService ragPipelineService;

    @Autowired
    private PlantRepository plantRepository;

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final List<String> VISION_MODELS = List.of(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash"
    );
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

    /**
     * Tạo phần dữ liệu ảnh gửi sang Gemini Vision.
     */
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

    /**
     * Chuyển data URL ảnh thành inline_data cho Gemini.
     */
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

    /**
     * Nhận diện tối đa 3 tên cây có thể có trong ảnh.
     */
    private List<Map<String, String>> identifyPlantNameFromImage(String imageUrl) {
        try {
            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");

            JsonArray parts = new JsonArray();

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text",
                    "Bạn là chuyên gia thực vật. Hãy nhìn ảnh và xác định Top 3 cây có thể.\n" +
                    "QUAN TRỌNG:\n" +
                    "- common_name PHẢI là tên tiếng Việt phổ biến (vd: 'Atiso', 'Nha đam', 'Xoài'). KHÔNG dùng tiếng Anh.\n" +
                    "- scientific_name là tên khoa học Latin (vd: 'Cynara scolymus').\n" +
                    "- confidence: 'high' (>=90%), 'medium' (60-89%), 'low' (<60%)\n" +
                    "- diagnostic_features_visible: true CHỈ khi ảnh nhìn rõ ít nhất một đặc điểm có thể phân biệt ở cấp loài như lá, hoa, quả hoặc vỏ đặc trưng.\n" +
                    "- Ảnh chụp toàn cây từ xa, chỉ thấy tán/cành/thân chung chung thì diagnostic_features_visible=false và confidence=low.\n" +
                    "- Không suy đoán loài từ phong cảnh, địa điểm hoặc dáng cây chung chung. Nếu thiếu dấu hiệu phân biệt thì phải hạ thấp độ tin cậy.\n" +
                    "- Sắp xếp theo confidence giảm dần.\n" +
                    "- Nếu ảnh không phải cây cối → trả về: []\n" +
                    "- Chỉ trả về JSON array, không thêm text nào khác.\n" +
                    "Ví dụ: [{\"common_name\":\"Xoài\",\"scientific_name\":\"Mangifera indica\",\"confidence\":\"high\",\"diagnostic_features_visible\":true},{\"common_name\":\"Bơ\",\"scientific_name\":\"Persea americana\",\"confidence\":\"low\",\"diagnostic_features_visible\":false}]");
            parts.add(textPart);

            JsonObject imagePart = buildImagePart(imageUrl);
            if (imagePart == null) {
                log.warn("Image data could not be loaded; aborting vision request");
                return Collections.emptyList();
            }
            parts.add(imagePart);

            userMsg.add("parts", parts);
            contents.add(userMsg);

            root.add("contents", contents);
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.1);
            generationConfig.addProperty("responseMimeType", "application/json");
            root.add("generationConfig", generationConfig);

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
                // Clean markdown code block nếu có
                String cleanText = text.trim();
                if (cleanText.startsWith("```")) {
                    cleanText = cleanText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                }

                // Parse JSON array
                JsonArray arr = JsonParser.parseString(cleanText).getAsJsonArray();
                List<Map<String, String>> candidates = new ArrayList<>();

                for (int i = 0; i < arr.size() && i < 3; i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String commonName = obj.has("common_name") ? obj.get("common_name").getAsString() : null;
                    String scientificName = obj.has("scientific_name") ? obj.get("scientific_name").getAsString() : null;
                    String confidence = obj.has("confidence") ? obj.get("confidence").getAsString() : "low";
                    boolean diagnosticFeaturesVisible = obj.has("diagnostic_features_visible")
                            && obj.get("diagnostic_features_visible").getAsBoolean();

                    if ((commonName != null && !commonName.isBlank()) ||
                        (scientificName != null && !scientificName.isBlank())) {
                        Map<String, String> candidate = new HashMap<>();
                        if (commonName != null && !commonName.isBlank()) candidate.put("common_name", commonName);
                        if (scientificName != null && !scientificName.isBlank()) candidate.put("scientific_name", scientificName);
                        candidate.put("confidence", confidence);
                        candidate.put("diagnostic_features_visible", Boolean.toString(diagnosticFeaturesVisible));
                        candidates.add(candidate);
                    }
                }

                if (!candidates.isEmpty()) {
                    log.info("Vision trả về {} candidates: {}", candidates.size(),
                            candidates.stream().map(c -> c.getOrDefault("common_name", "?") +
                            "(" + c.getOrDefault("confidence", "?") + ")").collect(Collectors.joining(", ")));
                    return candidates;
                }
            } catch (Exception e) {
                // Không parse được array → thử parse single object (fallback)
                log.debug("Không parse được JSON array, thử single object: {}", e.getMessage());
            }

            // Fallback: thử parse single object (tương thích format cũ)
            try {
                String cleanText = text.trim();
                if (cleanText.startsWith("```")) {
                    cleanText = cleanText.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                }
                JsonObject obj = JsonParser.parseString(cleanText).getAsJsonObject();
                String commonName = obj.has("common_name") ? obj.get("common_name").getAsString() : null;
                String scientificName = obj.has("scientific_name") ? obj.get("scientific_name").getAsString() : null;
                
                if ((commonName != null && !commonName.isBlank()) ||
                    (scientificName != null && !scientificName.isBlank())) {
                    Map<String, String> candidate = new HashMap<>();
                    if (commonName != null && !commonName.isBlank()) candidate.put("common_name", commonName);
                    if (scientificName != null && !scientificName.isBlank()) candidate.put("scientific_name", scientificName);
                    candidate.put("confidence", "medium");
                    return List.of(candidate);
                }
            } catch (Exception e2) {
                // Không parse được gì → fallback text thô
            }

            // Fallback cuối: trả text thô dưới dạng single candidate
            log.warn("Gemini Vision returned invalid JSON; ignoring unsafe fallback text");
            return Collections.emptyList();
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
            // 1. Gemini Vision nhận diện — trả về Top 3 candidates
            List<Map<String, String>> candidates = identifyPlantNameFromImage(imageUrl);
            
            // ===== GUARD: Vision không nhận diện được =====
            if (candidates == null) {
                return "AI nhận diện hình ảnh đang tạm thời không khả dụng hoặc đã hết hạn mức. "
                        + "Dữ liệu RAG không bị ảnh hưởng. Vui lòng thử lại sau hoặc nhập tên cây bằng chữ.";
            }

            if (candidates.isEmpty()) {
                return "⚠️ Không thể nhận diện cây trong ảnh.\n\n" +
                       "Vui lòng thử lại với:\n" +
                       "- Ảnh rõ nét hơn, chụp cận cảnh cây\n" +
                       "- Hoặc mô tả cây bằng văn bản để tôi tra cứu";
            }
            
            // 2. Duyệt Top 3 candidates, tìm candidate đầu tiên khớp DB
            // Candidate #1 alone determines identity. Database coverage must never
            // promote a lower-ranked vision candidate to the final answer.
            Map<String, String> first = primaryCandidate(candidates);
            String commonName = first.get("common_name");
            String scientificName = first.get("scientific_name");
            String confidence = normalizeConfidence(first.get("confidence"));
            String chosenDisplayName = firstNonBlank(commonName, scientificName);

            if (chosenDisplayName == null) {
                return "Kh\u00f4ng th\u1ec3 nh\u1eadn di\u1ec7n c\u00e2y trong \u1ea3nh. Vui l\u00f2ng g\u1eedi \u1ea3nh r\u00f5 n\u00e9t h\u01a1n.";
            }

            if (!hasSufficientVisualEvidence(first)) {
                return "AI ch\u1ec9 c\u00f3 th\u1ec3 \u0111\u01b0a ra gi\u1ea3 thuy\u1ebft c\u00e2y trong \u1ea3nh l\u00e0 "
                        + chosenDisplayName + ", nh\u01b0ng \u1ea3nh ch\u01b0a c\u00f3 \u0111\u1ee7 \u0111\u1eb7c \u0111i\u1ec3m h\u00ecnh th\u00e1i \u0111\u1ec3 x\u00e1c nh\u1eadn. "
                        + "H\u1ec7 th\u1ed1ng s\u1ebd kh\u00f4ng truy xu\u1ea5t d\u1eef li\u1ec7u c\u1ee7a c\u00e2y n\u00e0y \u0111\u1ec3 tr\u00e1nh tr\u1ea3 l\u1eddi sai. "
                        + "Vui l\u00f2ng g\u1eedi th\u00eam \u1ea3nh c\u1eadn c\u1ea3nh l\u00e1, hoa, qu\u1ea3 ho\u1eb7c v\u1ecf c\u00e2y.";
            }

            String dbPlantName = findMatchingPlantName(commonName, scientificName);
            String chosenPlantName = dbPlantName != null ? dbPlantName : chosenDisplayName;

            if (dbPlantName == null) {
                return "AI nhận diện cây trong ảnh có thể là " + chosenDisplayName
                        + ", nhưng tên này không khớp với cây nào trong cơ sở dữ liệu. "
                        + "Hệ thống sẽ không truy xuất RAG để tránh lấy nhầm dữ liệu. "
                        + "Vui lòng gửi thêm ảnh cận cảnh lá, hoa, quả hoặc nhập tên cây bằng chữ.";
            }

            // Log tóm tắt
            boolean nameWasMapped = dbPlantName != null && chosenDisplayName != null
                    && !dbPlantName.equalsIgnoreCase(chosenDisplayName);
            if (nameWasMapped) {
                log.info("Đã map tên cây từ Vision '{}' -> tên trong DB '{}'", chosenDisplayName, dbPlantName);
            }
            
            // 3. Tạo câu hỏi text cho RAG
            String plantNameForQuery = (dbPlantName != null) ? dbPlantName : chosenPlantName;
            String textQuestion;
            if (userMessage != null && !userMessage.isBlank()) {
                textQuestion = userMessage + " (cây " + plantNameForQuery + ")";
            } else {
                textQuestion = "Cây " + plantNameForQuery + " có đặc điểm, công dụng gì?";
            }
            
            // 4. DÙNG RAG PIPELINE
            String ragResponse = ragPipelineService.processQuestion(textQuestion);
            
            // 5. Bao kết quả với prefix nhận diện (TEXT THUẦN)
            boolean notFound = ragResponse.contains("chưa có thông tin") 
                            || ragResponse.contains("rag-no-result")
                            || ragResponse.contains("không tìm thấy");
            
            // Build tên hiển thị
            String displayName = nameWasMapped
                    ? chosenDisplayName + " (" + dbPlantName + ")"
                    : (chosenDisplayName != null ? chosenDisplayName : "không xác định");
            
            if (notFound) {
                return "🔍 Hệ thống nhận diện cây trong ảnh có thể là: " + displayName + "\n\n" +
                       "⚠️ Tuy nhiên, cây này hiện chưa có trong cơ sở dữ liệu.\n\n" +
                       ragResponse;
            }
            
            String recognitionText = "high".equals(confidence) ? "l\u00e0" : "c\u00f3 th\u1ec3 l\u00e0";
            return "H\u1ec7 th\u1ed1ng nh\u1eadn di\u1ec7n c\u00e2y trong \u1ea3nh " + recognitionText + ": " + displayName + "\n\n" + ragResponse;

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
    String findMatchingPlantName(String commonName, String scientificName) {
        // ===== BƯỚC 1: Tìm bằng scientific_name trước (Latin → chính xác nhất) =====
        if (scientificName != null && !scientificName.isBlank()) {
            String result = findPlantByScientificName(scientificName);
            if (result != null) return result;
        }
        
        // ===== BƯỚC 2: Tìm bằng common_name (tiếng Việt) =====
        if (commonName != null && !commonName.isBlank()) {
            String result = findPlantByWordMatch(commonName);
            if (result != null) return result;
        }
        
        return null;
    }
    
    /**
     * Tìm plant bằng cách tách tên thành từng từ và tìm trong name, otherNames.
     * Ưu tiên: exact name match > name contains > otherNames contains
     * Yêu cầu: từ phải >= 4 ký tự để tránh false positive (vd: "măng" ≠ "mạng")
     */
    private String findPlantByWordMatch(String visionName) {
        try {
            String normalizedVisionName = normalizePlantName(visionName);
            if (normalizedVisionName.isBlank()) return null;

            List<Plant> candidates = new ArrayList<>();
            candidates.addAll(plantRepository.findByNameContainingIgnoreCase(visionName.trim()));
            candidates.addAll(plantRepository.findByOtherNamesContainingIgnoreCase(visionName.trim()));

            for (Plant plant : candidates) {
                if (plant.getName() != null
                        && normalizePlantName(plant.getName()).equals(normalizedVisionName)) {
                    return plant.getName();
                }
                if (containsExactAlias(plant.getOtherNames(), normalizedVisionName)) {
                    return plant.getName();
                }
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
            String canonicalName = canonicalScientificName(scientificName);
            if (canonicalName.isBlank()) return null;

            for (Plant plant : plantRepository.findByScientificNameContainingIgnoreCase(canonicalName)) {
                if (plant.getScientificName() != null
                        && canonicalScientificName(plant.getScientificName()).equals(canonicalName)) {
                    return plant.getName();
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi khi tra cứu scientific name '{}': {}", scientificName, e.getMessage());
        }
        return null;
    }

    /**
     * Chuẩn hóa độ tin cậy trả về từ Vision.
     */
    static String normalizeConfidence(String confidence) {
        if (confidence == null) return "low";
        String value = confidence.trim().toLowerCase(Locale.ROOT);
        return value.equals("high") || value.equals("medium") ? value : "low";
    }

    /**
     * Kiểm tra ảnh có đủ bằng chứng hình thái hay không.
     */
    static boolean hasSufficientVisualEvidence(Map<String, String> candidate) {
        if (candidate == null) return false;
        return "high".equals(normalizeConfidence(candidate.get("confidence")))
                && Boolean.parseBoolean(candidate.get("diagnostic_features_visible"));
    }

    /**
     * Lấy candidate nhận diện ưu tiên nhất.
     */
    static Map<String, String> primaryCandidate(List<Map<String, String>> candidates) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyMap();
        return candidates.get(0);
    }

    /**
     * Lấy chuỗi đầu tiên không rỗng.
     */
    static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    /**
     * Chuẩn hóa tên cây để so khớp.
     */
    static String normalizePlantName(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.replaceFirst("^cay\\s+", "");
    }

    /**
     * Chuẩn hóa tên khoa học về dạng genus species.
     */
    static String canonicalScientificName(String value) {
        if (value == null) return "";
        Matcher matcher = Pattern.compile("([A-Za-z]+)\\s+([a-z][A-Za-z-]+)").matcher(value.trim());
        if (!matcher.find()) return "";
        return (matcher.group(1) + " " + matcher.group(2)).toLowerCase(Locale.ROOT);
    }

    /**
     * Kiểm tra tên khác có khớp chính xác hay không.
     */
    private static boolean containsExactAlias(String aliases, String normalizedName) {
        if (aliases == null || aliases.isBlank()) return false;
        for (String alias : aliases.split("[,;/|]")) {
            if (normalizePlantName(alias).equals(normalizedName)) return true;
        }
        return false;
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
                        .uri(URI.create(GEMINI_BASE_URL + VISION_MODELS.get(0) + ":generateContent?key=" + geminiApiKey))
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
                    log.error("Gemini Vision API returned status {}: {}",
                            response.statusCode(), summarizeErrorBody(response.body()));
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

    /**
     * Rút gọn body lỗi để ghi log.
     */
    private String summarizeErrorBody(String body) {
        if (body == null || body.isBlank()) return "";
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 800 ? compact : compact.substring(0, 800) + "...";
    }
}
