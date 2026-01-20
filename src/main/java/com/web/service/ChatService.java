package com.web.service;

import com.google.gson.*;
import com.web.entity.Plant;
import com.web.repository.PlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.timeout-seconds:30}")
    private int geminiTimeoutSeconds;

    @Autowired
    private PlantRepository plantRepository;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("Ảnh kèm theo:\\s*(https?://\\S+)");


    /**
     * Gửi tin nhắn người dùng cùng với dữ liệu cây dược liệu đến API Gemini.
     * @param userMessage Tin nhắn của người dùng.
     * @return Phản hồi từ Gemini hoặc thông báo lỗi.
     */
    public String chatWithGemini(String userMessage, HttpSession session) {
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
        String his = (String) session.getAttribute("history-chat");
        try {
            String imageUrl = null;
            Matcher matcher = IMAGE_URL_PATTERN.matcher(userMessage);
            if (matcher.find()) {
                imageUrl = matcher.group(1);
                userMessage = userMessage.split("Ảnh kèm theo:")[0].trim();
            }

            if (imageUrl != null) {
                return answerWithImageAndDatabase(userMessage, imageUrl, his);
            }

            List<Plant> plants = findRelevantPlants(userMessage);
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

            String plantsJsonData = gson.toJson(plants);

            if (userMessage.isEmpty() && imageUrl != null) {
                userMessage = "Hãy xác định và phân tích cây dược liệu trong ảnh";
            }
            String prompt = """
                Bạn là trợ lý AI của website quản lý cây dược liệu, trả lời bằng tiếng Việt, ngắn gọn, thân thiện, 
                trả lời dạng HTML nhé, nếu có link ảnh hãy trả lời dạng thẻ img, set độ rộng 150px cho tôi nhé.
                Các khả năng chính:
                - Tìm kiếm cây dược liệu phù hợp
                - Xác định cây dược liệu dựa vào link hình ảnh được cung cấp (nếu hình ảnh được gửi dạng link cloudinary)
                - Xác định công dụng, cách dùng, nơi trồng của cây dược liệu
                - Các câu hỏi khác thì tìm câu trả lời từ các nguồn khác database
                Đây là lịch sử câu hỏi trước đó của người dùng:
                %s
                
                Dưới đây là **dữ liệu cây dược liệu** từ database dạng json. Hãy sử dụng thông tin này để trả lời các câu hỏi liên quan đến cây dược liệu một cách chính xác nhất có thể:

                %s

                Câu hỏi của người dùng: %s
                """.formatted(his, plantsJsonData, userMessage);

            if (imageUrl != null) {
                prompt += "\n\n*** LƯU Ý: Hãy phân tích hình ảnh tại link sau để trả lời: " + imageUrl + " ***";
            }
            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");

            JsonArray parts = new JsonArray();
            JsonObject partText = new JsonObject();
            partText.addProperty("text", prompt);
            parts.add(partText);

            if (imageUrl != null) {
                JsonObject imagePart = buildImagePart(imageUrl);
                if (imagePart != null) {
                    parts.add(imagePart);
                }
            }

            userMsg.add("parts", parts);
            contents.add(userMsg);

            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 1);
            root.add("generationConfig", generationConfig);
            root.add("contents", contents);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API returned status code: {}", response.statusCode());
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
                log.warn("Gemini response structure unexpected");
                return "⚠️ Không nhận được phản hồi hợp lệ từ AI.";
            } else if (json.has("error")) {
                log.error("Gemini API error");
                return "❌ Lỗi xử lý yêu cầu. Vui lòng thử lại sau.";
            } else {
                log.warn("Unexpected Gemini response");
                return "⚠️ Không nhận được phản hồi từ AI. Vui lòng thử lại.";
            }

        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Gemini API timeout", e);
            return "⏱️ Yêu cầu quá thời gian chờ. Vui lòng thử lại sau.";
        } catch (Exception e) {
            log.error("Error in chatWithGemini", e);
            return "❌ Lỗi hệ thống. Vui lòng thử lại sau.";
        }
    }

    private JsonObject buildImagePart(String imageUrl) {
        if (imageUrl.startsWith("data:image")) {
            return buildImagePartFromDataUrl(imageUrl);
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
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
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                if (obj.has("common_name")) {
                    return obj.get("common_name").getAsString();
                }
                if (obj.has("scientific_name")) {
                    return obj.get("scientific_name").getAsString();
                }
            } catch (Exception e) {
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
                    Bạn là trợ lý AI của website quản lý cây dược liệu, trả lời bằng tiếng Việt, ngắn gọn, thân thiện, dạng HTML.
                    
                    Tôi đã nhận diện được cây trong ảnh có thể là: %s
                    Và đã tìm thấy thông tin trong database của hệ thống.
                    
                    Lịch sử chat trước đó:
                    %s
                    
                    Dưới đây là **dữ liệu cây dược liệu** từ database dạng JSON. Hãy sử dụng thông tin này để trả lời chính xác:
                    %s
                    
                    Câu hỏi của người dùng: %s
                    
                    *** LƯU Ý: Hãy phân tích hình ảnh kèm theo để xác nhận và bổ sung thông tin. Nếu thông tin trong database khác với hình ảnh, hãy đề cập đến điều đó.
                    """.formatted(identifiedPlantName, history, plantsJsonData, 
                            userMessage != null && !userMessage.isBlank() ? userMessage : "Hãy phân tích và xác định cây dược liệu trong ảnh");
            } else {
                if (identifiedPlantName != null && !identifiedPlantName.trim().isEmpty()) {
                    prompt = """
                        Bạn là chuyên gia thực vật. Trả lời bằng tiếng Việt, ngắn gọn, thân thiện, dạng HTML.
                        
                        Tôi đã nhận diện được cây trong ảnh có thể là: %s
                        Tuy nhiên, cây này không có trong database của hệ thống.
                        
                        Lịch sử chat trước đó:
                        %s
                        
                        Câu hỏi của người dùng: %s
                        
                        Hãy sử dụng tri thức nền của bạn để phân tích hình ảnh và cung cấp thông tin về cây này:
                        - Tên thường gọi và tên khoa học (nếu biết)
                        - Đặc điểm nhận dạng chính
                        - Công dụng và cách sử dụng (nếu biết)
                        - Lưu ý: Nếu bạn không chắc chắn, hãy nói rõ điều đó.
                        """.formatted(identifiedPlantName, history, 
                                userMessage != null && !userMessage.isBlank() ? userMessage : "Hãy phân tích và xác định cây dược liệu trong ảnh");
                } else {
                    prompt = """
                        Bạn là chuyên gia thực vật. Trả lời bằng tiếng Việt, ngắn gọn, thân thiện, dạng HTML.
                        
                        Lịch sử chat trước đó:
                        %s
                        
                        Câu hỏi của người dùng: %s
                        
                        Hãy nhìn vào hình ảnh kèm theo và cho biết đây là cây gì, tên thường gọi và tên khoa học (nếu biết),
                        kèm mô tả ngắn về đặc điểm nhận dạng chính.
                        """.formatted(history, 
                                userMessage != null && !userMessage.isBlank() ? userMessage : "Hãy phân tích và xác định cây dược liệu trong ảnh");
                }
            }
            
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API returned status code: {}", response.statusCode());
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
                log.warn("Gemini response structure unexpected");
                return "⚠️ Không nhận được phản hồi hợp lệ từ AI.";
            } else if (json.has("error")) {
                log.error("Gemini API error");
                return "❌ Lỗi xử lý yêu cầu. Vui lòng thử lại sau.";
            } else {
                log.warn("Unexpected Gemini response");
                return "⚠️ Không nhận được phản hồi từ AI. Vui lòng thử lại.";
            }
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Gemini API timeout", e);
            return "⏱️ Yêu cầu quá thời gian chờ. Vui lòng thử lại sau.";
        } catch (Exception e) {
            log.error("answerWithImageAndDatabase error", e);
            return "❌ Lỗi hệ thống khi xử lý ảnh. Vui lòng thử lại sau.";
        }
    }

    private String answerImageOnly(String userMessage, String imageUrl, String history) {
        try {
            if (userMessage == null || userMessage.isBlank()) {
                userMessage = "Hãy cho tôi biết đây là cây gì trong ảnh, tên thường gọi và tên khoa học (nếu có).";
            }

            String prompt = """
                Bạn là chuyên gia thực vật. Trả lời bằng tiếng Việt, ngắn gọn, thân thiện, dạng HTML.
                Hãy nhìn vào hình ảnh kèm theo và cho biết đây là cây gì, tên thường gọi và tên khoa học (nếu biết),
                kèm mô tả ngắn về đặc điểm nhận dạng chính.

                Lịch sử chat trước đó (nếu có):
                %s

                Câu hỏi của người dùng: %s
                """.formatted(history, userMessage);

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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(geminiTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API returned status code: {}", response.statusCode());
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
                log.warn("Gemini response structure unexpected");
                return "⚠️ Không nhận được phản hồi hợp lệ từ AI.";
            } else if (json.has("error")) {
                log.error("Gemini API error");
                return "❌ Lỗi xử lý yêu cầu. Vui lòng thử lại sau.";
            } else {
                log.warn("Unexpected Gemini response");
                return "⚠️ Không nhận được phản hồi từ AI. Vui lòng thử lại.";
            }
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Gemini API timeout", e);
            return "⏱️ Yêu cầu quá thời gian chờ. Vui lòng thử lại sau.";
        } catch (Exception e) {
            log.error("answerImageOnly error", e);
            return "❌ Lỗi hệ thống khi xử lý ảnh. Vui lòng thử lại sau.";
        }
    }

    /**
     * Tìm các cây dược liệu liên quan đến câu hỏi của người dùng (Semantic Search)
     * Thay vì lấy tất cả plants, chỉ lấy 10-15 cây liên quan nhất
     * Giúp giảm token usage và tăng tốc độ response
     * 
     * @param userMessage Câu hỏi của người dùng
     * @return Danh sách các cây dược liệu liên quan (tối đa 15 cây)
     */
    private List<Plant> findRelevantPlants(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return plantRepository.findAll(PageRequest.of(0, 5)).getContent();
        }

        try {
            String normalizedQuery = userMessage.trim().toLowerCase()
                    .replaceAll("[^a-z0-9\\sàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            if (!normalizedQuery.isEmpty() && normalizedQuery.length() >= 2) {
                List<Plant> fullTextResults = plantRepository.findRelevantPlantsFullText(normalizedQuery);
                if (!fullTextResults.isEmpty()) {
                    return fullTextResults;
                }
            }
        } catch (Exception e) {
            log.warn("FULLTEXT search failed, falling back to keyword search: {}", e.getMessage());
        }

        Set<Plant> relevantPlants = new HashSet<>();
        String[] keywords = userMessage.toLowerCase()
                .replaceAll("[^a-z0-9\\sàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", " ")
                .split("\\s+");

        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "cây", "cay", "cỏ", "co", "lá", "la", "rễ", "re", "hoa", "quả", "qua",
                "dược", "duoc", "liệu", "lieu", "thuốc", "thuoc", "là", "la", "của", "cua",
                "và", "va", "có", "co", "để", "de", "cho", "với", "voi", "theo", "từ", "tu"
        ));

        for (String keyword : keywords) {
            keyword = keyword.trim();
            if (keyword.length() < 2 || stopWords.contains(keyword)) {
                continue;
            }

            try {
                relevantPlants.addAll(plantRepository.findByNameContainingIgnoreCase(keyword));
                relevantPlants.addAll(plantRepository.findByScientificNameContainingIgnoreCase(keyword));
                relevantPlants.addAll(plantRepository.findByOtherNamesContainingIgnoreCase(keyword));
                if (isMedicinalKeyword(keyword)) {
                    relevantPlants.addAll(plantRepository.findByMedicinalUsesContainingIgnoreCase(keyword));
                    relevantPlants.addAll(plantRepository.findByIndicationsContainingIgnoreCase(keyword));
                }
            } catch (Exception e) {
                log.warn("Error searching for keyword '{}': {}", keyword, e.getMessage());
            }
        }

        if (relevantPlants.isEmpty()) {
            return plantRepository.findAll(PageRequest.of(0, 5)).getContent();
        }

        return relevantPlants.stream()
                .limit(15)
                .collect(Collectors.toList());
    }

    private boolean isMedicinalKeyword(String keyword) {
        String[] medicinalWords = {
            "chữa", "chua", "điều", "dieu", "trị", "tri", "công", "cong", 
            "dụng", "dung", "tác", "tac", "bệnh", "benh", "thuốc", "thuoc",
            "dược", "duoc", "liệu", "lieu", "cây", "cay", "thuốc", "thuoc",
            "hoạt", "hoat", "chất", "chat", "thành", "thanh", "phần", "phan"
        };
        
        String lowerKeyword = keyword.toLowerCase();
        for (String word : medicinalWords) {
            if (lowerKeyword.contains(word) || word.contains(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }
}