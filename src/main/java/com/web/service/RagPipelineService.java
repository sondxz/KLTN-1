package com.web.service;
 
import com.google.gson.*;
import com.web.entity.ChunkEmbedding;
import com.web.entity.FolkRemedy;
import com.web.entity.Plant;
import com.web.entity.PlantDiseases;
import com.web.entity.Research;
import com.web.enums.PlantStatus;
import com.web.repository.ChunkEmbeddingRepository;
import com.web.repository.FolkRemedyRepository;
import com.web.repository.PlantDiseasesRepository;
import com.web.repository.PlantRepository;
import com.web.repository.ResearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
 
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String NO_DATA_RESPONSE = """
            Xin lỗi, tôi chưa tìm thấy thông tin phù hợp trong cơ sở dữ liệu.

            Bạn có thể:
            • Đặt lại câu hỏi với từ khóa cụ thể hơn.
            • Sử dụng tên gọi khác của đối tượng cần tìm.
            • Hỏi về một chủ đề liên quan khác.

            Tôi sẽ cố gắng hỗ trợ bạn tra cứu thông tin phù hợp nhất.
            """.trim();
 
    private static final Map<ChunkEmbedding.ContentType, Double> SOURCE_PRIORITY;
    static {
        SOURCE_PRIORITY = new HashMap<>();
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.plant, 1.5);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.folk_remedy, 1.3);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.article, 1.1);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.research, 1.0);
        SOURCE_PRIORITY.put(ChunkEmbedding.ContentType.disease, 1.0);
    }

    private static final Map<String, List<String>> HEALTH_KEYWORD_ALIASES = Map.ofEntries(
            Map.entry("dau da day", List.of("viem loet da day", "da day", "dau bao tu", "bao tu", "tieu hoa")),
            Map.entry("da day", List.of("viem loet da day", "dau da day", "dau bao tu", "bao tu", "tieu hoa")),
            Map.entry("dau bao tu", List.of("dau da day", "viem loet da day", "da day", "bao tu", "tieu hoa")),
            Map.entry("bao tu", List.of("dau da day", "viem loet da day", "da day", "dau bao tu", "tieu hoa")),
            Map.entry("tieu hoa", List.of("day bung", "tieu hoa kem", "viem ruot", "da day")),
            Map.entry("mat ngu", List.of("kho ngu")),
            Map.entry("kho ngu", List.of("mat ngu")),
            Map.entry("ho", List.of("ho co dom", "viem hong")),
            Map.entry("dom", List.of("ho co dom", "tieu dom")),
            Map.entry("nhieu dom", List.of("ho co dom", "tieu dom", "dom"))
    );
 
    private static final String PROMPT_TEMPLATE = """
            Bạn là chuyên gia tư vấn về cây dược liệu Việt Nam. Hãy trả lời câu hỏi của người dùng một cách tự nhiên, thân thiện và chính xác, CHỈ dựa vào thông tin tham khảo bên dưới.

            === THÔNG TIN THAM KHẢO ===
            %s

            === CÂU HỎI ===
            Người dùng hỏi về: %s
            Nội dung: %s

            === PHẠM VI TRẢ LỜI ===
            - Bạn CHỈ trả lời các câu hỏi liên quan đến cây dược liệu, thảo dược, bài thuốc dân gian, cách chữa bệnh bằng thảo dược, hoặc các chủ đề y học cổ truyền.
            - Nếu câu hỏi KHÔNG liên quan đến cây dược liệu hoặc y học cổ truyền (ví dụ: công nghệ, giải trí, thời tiết, tài chính, chính trị, v.v.), hãy trả lời: "🌿 Xin lỗi, tôi là trợ lý chuyên về cây dược liệu Việt Nam. Câu hỏi này nằm ngoài phạm vi của tôi. Mời bạn hỏi về cây dược liệu, bài thuốc dân gian hoặc cách chữa bệnh bằng thảo dược nhé!"
            - Với câu chào hỏi, cảm ơn, hỏi thăm: trả lời tự nhiên, ngắn gọn, và gợi ý hỏi về cây dược liệu.

            === CÁCH TRẢ LỜI ===
            - Nếu trong thông tin tham khảo CÓ dữ liệu về "%s": trả lời trực tiếp, rõ ràng, đúng trọng tâm câu hỏi. Không lan man, không liệt kê hết mọi thứ.
            - Nếu KHÔNG có dữ liệu về "%s": chỉ trả lời đúng mẫu: "❌ Hệ thống chưa có thông tin về %s. Bạn có thể thử từ khóa khác hoặc liên hệ chuyên gia nhé."

            === LƯU Ý ===
            - Dùng văn phong tự nhiên như đang trò chuyện, không máy móc, không khuôn mẫu
            - Chỉ trả lời bằng văn bản thuần, không dùng markdown, HTML hay emoji (trừ ❌ khi báo lỗi và 🌿 khi từ chối)
            - Dùng dấu gạch ngang (-) cho danh sách, xuống dòng giữa các ý
            - Nếu có dùng thông tin từ tài liệu tham khảo: ghi nguồn ở cuối theo đúng mẫu sau: "Nguồn: [Tên cây](đường dẫn)" trong đó "Tên cây" lấy từ mục "Tên" trong tài liệu, còn "đường dẫn" lấy từ mục "Link" trong tài liệu. Ví dụ: "Nguồn: [Atiso](/plant-detail/atiso)". Nếu có nhiều nguồn thì mỗi nguồn trên một dòng riêng.
            - Nếu không dùng thông tin tham khảo (vd: chào hỏi, cảm ơn) thì KHÔNG thêm nguồn, KHÔNG tự bịa
            - Nếu thiếu thông tin để trả lời trọn vẹn, hãy thành thật nói rõ thay vì bịa đặt
            - Nếu câu hỏi có nhiều ý (ví dụ công dụng và chống chỉ định), phải trả lời đủ từng ý có dữ liệu trong tài liệu
            - Trả lời ngắn gọn, súc tích, không dài dòng
            """;
 
    @Value("${gemini.api.key}")
    private String geminiApiKey;
 
    @Value("${gemini.api.timeout-seconds:60}")
    private int geminiTimeoutSeconds;
 
    @Autowired
    private ChunkEmbeddingRepository chunkEmbeddingRepository;

    @Autowired
    private PlantDiseasesRepository plantDiseasesRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private FolkRemedyRepository folkRemedyRepository;

    @Autowired
    private ResearchRepository researchRepository;
 
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
            extractedEntities = addExplicitPlantSubject(extractedEntities, question);
            extractedEntities = resolveSpecificPlantEntities(extractedEntities, question);
            String entityDesc = buildEntityDescription(extractedEntities);
            log.info("RAG: Extracted entities: {}", extractedEntities);

            if (isOutOfScopeQuestion(question)) {
                return "Câu hỏi này nằm ngoài phạm vi dữ liệu cây dược liệu, bài thuốc dân gian và nghiên cứu hiện có trong hệ thống.";
            }

            String diseaseToPlantResponse = answerDiseaseToPlantQuestion(question, extractedEntities);
            if (diseaseToPlantResponse != null) {
                return diseaseToPlantResponse;
            }

            String plantToDiseaseResponse = answerPlantToDiseaseQuestion(question, extractedEntities);
            if (plantToDiseaseResponse != null) {
                return plantToDiseaseResponse;
            }

            String plantAttributeResponse = answerPlantAttributeQuestion(question, extractedEntities);
            if (plantAttributeResponse != null) {
                return plantAttributeResponse;
            }

            String folkRemedyResponse = answerFolkRemedyQuestion(question, extractedEntities);
            if (folkRemedyResponse != null) {
                return folkRemedyResponse;
            }

            String researchResponse = answerResearchQuestion(question, extractedEntities);
            if (researchResponse != null) {
                return researchResponse;
            }

            if (!hasAnyEntity(extractedEntities) && requiresSpecificEntity(question)) {
                return "Bạn muốn hỏi liều lượng hoặc cách sử dụng của cây dược liệu/bài thuốc nào? "
                        + "Vui lòng cho biết tên cụ thể để tôi tra cứu đúng dữ liệu và tránh tư vấn nhầm.";
            }

            // Nếu không có entity và câu hỏi ngắn (< 30 ký tự), trả lời trực tiếp không qua RAG
            if (!hasAnyEntity(extractedEntities) && question.trim().length() < 30) {
                return """
                    Xin chào! Tôi là trợ lý AI về cây dược liệu Việt Nam.
                    Bạn cần tôi giúp gì về cây dược liệu, bài thuốc hay cách chữa bệnh bằng thảo dược?
                    """;
            }

            List<ScoredChunk> exactMatches = exactMatchSearch(extractedEntities, question);
            if (!exactMatches.isEmpty()) {
                log.info("RAG: Exact match found {} chunks for: {}", exactMatches.size(), entityDesc);
                List<ScoredChunk> selectedChunks = exactMatches.stream().limit(TOP_K).collect(Collectors.toList());
                String context = buildContext(selectedChunks);
                return appendVerifiedSources(generate(context, question, entityDesc), selectedChunks, question);
            }

            if (requestedContentType(question) != ChunkEmbedding.ContentType.plant
                    && hasAnyEntity(extractedEntities)) {
                return buildNoMatchResponse(extractedEntities);
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
            return appendVerifiedSources(generate(context, question, entityDesc), rerankedChunks, question);
 
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
 
    List<ScoredChunk> exactMatchSearch(Map<String, List<String>> extractedEntities, String question) {
        Map<Long, ScoredChunk> exactMatches = new LinkedHashMap<>();
        ChunkEmbedding.ContentType requestedType = requestedContentType(question);
        for (String name : extractedEntities.getOrDefault("plants", Collections.emptyList())) {
            String resolvedName = resolveMostSpecificPlantName(name, question);
            if (requestedType == ChunkEmbedding.ContentType.plant) {
                addExactChunks(exactMatches, chunkEmbeddingRepository.findByEntityNameIgnoreCase(resolvedName));
            } else {
                List<ChunkEmbedding> relatedContent = chunkEmbeddingRepository.findByFullTextSearchAndType(
                        resolvedName, requestedType.name(), TOP_K).stream()
                        .filter(chunk -> containsNormalizedPhrase(chunk.getEntityName(), resolvedName))
                        .collect(Collectors.toList());
                addExactChunks(exactMatches, relatedContent);
            }
        }
        for (String name : extractedEntities.getOrDefault("remedies", Collections.emptyList())) {
            addExactChunks(exactMatches, chunkEmbeddingRepository.findByEntityNameIgnoreCase(name));
        }
        return new ArrayList<>(exactMatches.values());
    }

    private String resolveMostSpecificPlantName(String extractedName, String question) {
        String normalizedQuestion = normalizeSearchText(question);
        return chunkEmbeddingRepository.findByEntityNameContainingIgnoreCase(extractedName).stream()
                .filter(chunk -> chunk.getContentType() == ChunkEmbedding.ContentType.plant)
                .map(ChunkEmbedding::getEntityName)
                .filter(Objects::nonNull)
                .filter(name -> normalizedQuestion.contains(normalizeSearchText(name)))
                .max(Comparator.comparingInt(name -> normalizeSearchText(name).length()))
                .orElse(extractedName);
    }

    private Map<String, List<String>> resolveSpecificPlantEntities(
            Map<String, List<String>> entities, String question) {
        Map<String, List<String>> resolved = new LinkedHashMap<>();
        resolved.put("plants", entities.getOrDefault("plants", Collections.emptyList()).stream()
                .map(name -> resolveMostSpecificPlantName(name, question))
                .distinct()
                .collect(Collectors.toList()));
        resolved.put("diseases", new ArrayList<>(
                entities.getOrDefault("diseases", Collections.emptyList())));
        resolved.put("remedies", new ArrayList<>(
                entities.getOrDefault("remedies", Collections.emptyList())));
        return resolved;
    }

    private Map<String, List<String>> addExplicitPlantSubject(
            Map<String, List<String>> entities, String question) {
        if (!entities.getOrDefault("plants", Collections.emptyList()).isEmpty()) {
            return entities;
        }
        String subject = extractExplicitPlantSubject(question);
        if (subject == null) return entities;

        Map<String, List<String>> enriched = new LinkedHashMap<>();
        enriched.put("plants", List.of(subject));
        enriched.put("diseases", new ArrayList<>(
                entities.getOrDefault("diseases", Collections.emptyList())));
        enriched.put("remedies", new ArrayList<>(
                entities.getOrDefault("remedies", Collections.emptyList())));
        return enriched;
    }

    static String extractExplicitPlantSubject(String question) {
        if (question == null) return null;
        Matcher matcher = Pattern.compile(
                "(?iu)\\bcây\\s+([\\p{L}][\\p{L}\\s-]*?)(?=\\s+(?:có|là|nói|dùng|chữa|trị|giúp|trong|với|như|thế|gì|không)\\b|[?.!,]|$)")
                .matcher(question.trim());
        if (!matcher.find()) return null;
        String subject = matcher.group(1).trim().replaceAll("\\s+", " ");
        return subject.length() >= 2 ? subject : null;
    }

    private String answerDiseaseToPlantQuestion(String question, Map<String, List<String>> extractedEntities) {
        if (!isDiseaseToPlantQuestion(question)) {
            return null;
        }

        List<String> healthTerms = extractHealthTerms(question, extractedEntities);
        if (healthTerms.isEmpty()) {
            return null;
        }

        LinkedHashMap<Long, PlantMatchCandidate> candidates = new LinkedHashMap<>();
        LinkedHashSet<String> matchedTerms = new LinkedHashSet<>();

        for (String term : healthTerms) {
            int matchedCountBefore = matchedTerms.size();
            int candidateCountBefore = candidates.size();

            List<String> keywords = expandHealthSearchKeywords(term);
            if (!keywords.isEmpty()) {
                searchPlantsByHealthKeyword(candidates, matchedTerms, term, keywords.get(0));
                if (candidates.size() == candidateCountBefore && matchedTerms.size() == matchedCountBefore) {
                    keywords.stream()
                            .skip(1)
                            .forEach(keyword -> searchPlantsByHealthKeyword(candidates, matchedTerms, term, keyword));
                }
            }
            if (matchedTerms.size() == matchedCountBefore) {
                matchedTerms.add(term);
            }
        }

        if (candidates.isEmpty()) {
            return NO_DATA_RESPONSE;
        }

        List<PlantMatchCandidate> rankedPlants = candidates.values().stream()
                .sorted((a, b) -> {
                    int textMatchCompare = Integer.compare(
                            countPlantTextMatches(b.plant, healthTerms),
                            countPlantTextMatches(a.plant, healthTerms));
                    if (textMatchCompare != 0) return textMatchCompare;
                    int termCompare = Integer.compare(b.matchedTerms.size(), a.matchedTerms.size());
                    if (termCompare != 0) return termCompare;
                    int scoreCompare = Integer.compare(b.score, a.score);
                    if (scoreCompare != 0) return scoreCompare;
                    return a.plant.getName().compareToIgnoreCase(b.plant.getName());
                })
                .limit(3)
                .collect(Collectors.toList());

        String subject = matchedTerms.stream()
                .filter(term -> term != null && !term.isBlank())
                .map(this::toHealthDisplayText)
                .limit(3)
                .collect(Collectors.joining(", "));
        if (subject.isBlank()) {
            subject = String.join(", ", healthTerms);
        }

        StringBuilder result = new StringBuilder();
        result.append("Các cây dược liệu trong cơ sở dữ liệu có liên quan đến ")
                .append(subject)
                .append(":\n");

        rankedPlants.forEach(candidate -> {
            Plant plant = candidate.plant;
            result.append("- ").append(plant.getName());
            String note = firstNonBlank(plant.getIndications(), plant.getMedicinalUses());
            if (note != null && !note.isBlank()) {
                result.append(": ").append(summarizeText(note, 180));
            }
            result.append("\n");
        });

        result.append("\nLưu ý: thông tin chỉ mang tính tham khảo từ dữ liệu của hệ thống, không thay thế tư vấn của bác sĩ.\n\n");
        rankedPlants.stream()
                .map(candidate -> candidate.plant)
                .filter(plant -> plant.getSlug() != null && !plant.getSlug().isBlank())
                .limit(3)
                .forEach(plant -> result.append("Nguồn: [")
                        .append(plant.getName())
                        .append("](/plant-detail/")
                        .append(plant.getSlug())
                        .append(")\n"));

        String fallback = result.toString().trim();
        String structuredContext = buildDiseaseToPlantGroundedContext(subject, rankedPlants);
        List<String> requiredTerms = rankedPlants.stream()
                .map(candidate -> candidate.plant.getName())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
        return generateGroundedStructuredAnswer(question, structuredContext, requiredTerms, fallback);
    }

    private String answerPlantToDiseaseQuestion(String question, Map<String, List<String>> extractedEntities) {
        if (!isPlantToDiseaseQuestion(question, extractedEntities)) {
            return null;
        }

        List<Plant> plants = findPlantsForPlantToDiseaseQuestion(question, extractedEntities);
        if (plants.isEmpty()) {
            return NO_DATA_RESPONSE;
        }

        LinkedHashMap<String, String> diseases = new LinkedHashMap<>();
        for (Plant plant : plants) {
            String keyword = plant.getName();
            if (keyword == null || keyword.isBlank()) continue;
            for (PlantDiseases relation : plantDiseasesRepository.findPublishedDiseasesByPlantKeyword(keyword)) {
                if (relation.getDiseases() == null || relation.getDiseases().getName() == null) continue;
                diseases.putIfAbsent(relation.getDiseases().getName(), relation.getDiseases().getSlug());
            }
            if (!diseases.isEmpty()) break;
        }

        if (diseases.isEmpty()) {
            return NO_DATA_RESPONSE;
        }

        Plant mainPlant = plants.get(0);
        StringBuilder result = new StringBuilder();
        result.append(mainPlant.getName())
                .append(" có liên quan/hỗ trợ các bệnh hoặc vấn đề sức khỏe sau trong cơ sở dữ liệu:\n");
        diseases.keySet().stream()
                .limit(8)
                .forEach(name -> result.append("- ").append(name).append("\n"));

        if (mainPlant.getSlug() != null && !mainPlant.getSlug().isBlank()) {
            result.append("\nNguồn: [")
                    .append(mainPlant.getName())
                    .append("](/plant-detail/")
                    .append(mainPlant.getSlug())
                    .append(")");
        }

        String fallback = result.toString().trim();
        String structuredContext = buildPlantToDiseaseGroundedContext(mainPlant, diseases.keySet());
        List<String> requiredTerms = new ArrayList<>();
        requiredTerms.add(mainPlant.getName());
        diseases.keySet().stream().limit(3).forEach(requiredTerms::add);
        return generateGroundedStructuredAnswer(question, structuredContext, requiredTerms, fallback);
    }

    private String buildDiseaseToPlantGroundedContext(String subject, List<PlantMatchCandidate> rankedPlants) {
        StringBuilder context = new StringBuilder();
        context.append("Luồng: bệnh/triệu chứng -> cây dược liệu\n");
        context.append("Vấn đề sức khỏe người dùng hỏi: ").append(subject).append("\n");
        context.append("Dữ liệu đã truy xuất từ cơ sở dữ liệu, chỉ được dùng các cây sau:\n");
        for (PlantMatchCandidate candidate : rankedPlants) {
            Plant plant = candidate.plant;
            context.append("- Tên cây: ").append(plant.getName()).append("\n");
            context.append("  Chỉ định/công dụng trong DB: ")
                    .append(summarizeText(firstNonBlank(plant.getIndications(), plant.getMedicinalUses()), 260))
                    .append("\n");
            if (plant.getSlug() != null && !plant.getSlug().isBlank()) {
                context.append("  Link nguồn: /plant-detail/").append(plant.getSlug()).append("\n");
            }
        }
        return context.toString();
    }

    private String buildPlantToDiseaseGroundedContext(Plant plant, Collection<String> diseases) {
        StringBuilder context = new StringBuilder();
        context.append("Luồng: cây dược liệu -> bệnh/vấn đề sức khỏe\n");
        context.append("Cây người dùng hỏi: ").append(plant.getName()).append("\n");
        context.append("Các bệnh/vấn đề sức khỏe liên quan đã truy xuất từ cơ sở dữ liệu:\n");
        diseases.stream().limit(8).forEach(disease -> context.append("- ").append(disease).append("\n"));
        if (plant.getSlug() != null && !plant.getSlug().isBlank()) {
            context.append("Link nguồn: /plant-detail/").append(plant.getSlug()).append("\n");
        }
        return context.toString();
    }

    private String generateGroundedStructuredAnswer(String question, String groundedContext,
            List<String> requiredTerms, String fallback) {
        String answerContext = removeGroundedSourceLines(groundedContext);
        String prompt = """
                Bạn là trợ lý tra cứu cây dược liệu. Hãy trả lời tự nhiên bằng tiếng Việt, nhưng CHỈ được dùng dữ liệu trong CONTEXT.

                QUY TẮC BẮT BUỘC:
                - Không thêm cây, bệnh, công dụng, liều dùng, chống chỉ định hoặc tác dụng phụ ngoài CONTEXT.
                - Không suy diễn kiến thức ngoài cơ sở dữ liệu.
                - Nếu CONTEXT có danh sách cây, phải giữ đúng các cây trong danh sách và không thêm cây khác.
                - Nếu CONTEXT có danh sách bệnh, phải giữ đúng các bệnh trong danh sách và không thêm bệnh khác.
                - Không tự tạo nguồn; nếu có link nguồn trong CONTEXT thì có thể dùng đúng link đó.
                - Luôn nhắc thông tin chỉ mang tính tham khảo, không thay thế tư vấn của bác sĩ.

                - Tuyệt đối không viết link, đường dẫn hoặc dòng "Tham khảo thêm tại" trong câu trả lời.

                CONTEXT:
                %s

                CÂU HỎI:
                %s
                """.formatted(
                answerContext == null ? "" : answerContext.replace("%", "%%"),
                question == null ? "" : question.replace("%", "%%"));

        String generated = "";
        if (cloudflareAIService.isAvailable()) {
            try {
                generated = cleanResponse(cloudflareAIService.chat(prompt));
                if (isGroundedStructuredAnswerValid(generated, requiredTerms)) {
                    return appendGroundedSources(generated, groundedContext);
                }
            } catch (Exception e) {
                log.warn("Grounded structured Cloudflare generate lỗi: {}", e.getMessage());
            }
        }

        try {
            generated = cleanResponse(callGeminiWithRetry(prompt));
            if (isGroundedStructuredAnswerValid(generated, requiredTerms)) {
                return appendGroundedSources(generated, groundedContext);
            }
        } catch (Exception e) {
            log.warn("Grounded structured Gemini generate lỗi: {}", e.getMessage());
        }

        return fallback;
    }

    private String removeGroundedSourceLines(String groundedContext) {
        if (groundedContext == null || groundedContext.isBlank()) return "";
        return Arrays.stream(groundedContext.split("\\R"))
                .filter(line -> !normalizeSearchText(line).contains("link nguon"))
                .collect(Collectors.joining("\n"));
    }

    private String appendGroundedSources(String answer, String groundedContext) {
        String cleanedAnswer = stripInlineSourceLines(answer);
        LinkedHashMap<String, String> sources = extractGroundedSources(groundedContext);
        if (sources.isEmpty()) return cleanedAnswer.trim();

        StringBuilder result = new StringBuilder(cleanedAnswer.trim());
        result.append("\n\n");
        sources.forEach((name, link) -> result.append("Nguồn: [")
                .append(name)
                .append("](")
                .append(link)
                .append(")\n"));
        return result.toString().trim();
    }

    private String stripInlineSourceLines(String answer) {
        if (answer == null || answer.isBlank()) return "";
        return Arrays.stream(answer.split("\\R"))
                .filter(line -> {
                    String normalized = normalizeSearchText(line);
                    return !normalized.contains("tham khao them tai")
                            && !normalized.contains("link nguon")
                            && !normalized.matches(".*\\/plant-detail\\/[^\\s)]+.*");
                })
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private LinkedHashMap<String, String> extractGroundedSources(String groundedContext) {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        if (groundedContext == null || groundedContext.isBlank()) return sources;

        String currentName = "";
        Pattern linkPattern = Pattern.compile("(/plant-detail/[^\\s)]+)");
        for (String line : groundedContext.split("\\R")) {
            String normalizedLine = normalizeSearchText(line);
            int colonIndex = line.indexOf(':');
            if (colonIndex >= 0 && (normalizedLine.contains("ten cay") || normalizedLine.contains("cay nguoi dung hoi"))) {
                currentName = line.substring(colonIndex + 1).trim();
                continue;
            }

            Matcher matcher = linkPattern.matcher(line);
            if (matcher.find()) {
                String link = matcher.group(1);
                String sourceName = currentName.isBlank() ? link.substring(link.lastIndexOf('/') + 1) : currentName;
                sources.putIfAbsent(sourceName, link);
            }
        }
        return sources;
    }

    private boolean isGroundedStructuredAnswerValid(String answer, List<String> requiredTerms) {
        if (answer == null || answer.isBlank()) return false;
        String normalizedAnswer = normalizeSearchText(answer);
        if (normalizedAnswer.contains("khong tim thay")
                || normalizedAnswer.contains("chua co thong tin")
                || normalizedAnswer.contains("khong co du lieu")) {
            return false;
        }
        if (requiredTerms == null) return true;
        for (String term : requiredTerms) {
            String normalizedTerm = normalizeSearchText(term);
            if (normalizedTerm.isBlank()) continue;
            if (!normalizedAnswer.contains(normalizedTerm)) {
                return false;
            }
        }
        return true;
    }

    private String answerPlantAttributeQuestion(String question, Map<String, List<String>> extractedEntities) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.isBlank() || !isPlantAttributeQuestion(normalized)) {
            return null;
        }

        List<Plant> plants = findPlantsForAttributeQuestion(question, extractedEntities);
        if (plants.isEmpty()) {
            return null;
        }

        Plant plant = plants.get(0);
        if (plant.getPlantStatus() != PlantStatus.DA_XUAT_BAN) {
            return NO_DATA_RESPONSE;
        }

        String response;
        if (isContraindicationQuestion(normalized)) {
            response = buildSinglePlantFieldAnswer(plant,
                    "Theo dữ liệu hiện có, " + plant.getName() + " không nên dùng hoặc cần thận trọng trong các trường hợp: ",
                    plant.getContraindications());
        } else if (isSideEffectQuestion(normalized)) {
            response = buildSinglePlantFieldAnswer(plant,
                    "Theo dữ liệu hiện có, tác dụng phụ của " + plant.getName() + ": ",
                    plant.getSideEffects());
        } else if (isUsageQuestion(normalized)) {
            response = buildSinglePlantFieldAnswer(plant,
                    "Theo dữ liệu hiện có, cách dùng/liều lượng của " + plant.getName() + ": ",
                    plant.getDosage());
        } else {
            response = buildPlantInfoAnswer(plant, normalized);
        }

        if (response == null || response.isBlank()) {
            return NO_DATA_RESPONSE;
        }
        return response;
    }

    private boolean isPlantAttributeQuestion(String normalized) {
        return isUsageQuestion(normalized)
                || isSideEffectQuestion(normalized)
                || isContraindicationQuestion(normalized);
    }

    private boolean isUsageQuestion(String normalized) {
        return normalized.matches(".*\\b(cach dung|cach su dung|su dung|lieu luong|lieu dung|uong nhu the nao|dung nhu the nao|uong bao nhieu|dung bao nhieu)\\b.*")
                || normalized.matches(".*\\b(uong|dung)\\b.*\\b(ra sao|nhu the nao|bao nhieu|lieu)\\b.*");
    }

    private boolean isSideEffectQuestion(String normalized) {
        return normalized.matches(".*\\b(tac dung phu|tac dung khong mong muon|rui ro|dung lau|su dung lau)\\b.*");
    }

    private boolean isContraindicationQuestion(String normalized) {
        return normalized.matches(".*\\b(chong chi dinh|ai khong nen|khong nen dung|doi tuong khong nen|can than trong|than trong)\\b.*");
    }

    private List<Plant> findPlantsForAttributeQuestion(String question, Map<String, List<String>> extractedEntities) {
        LinkedHashMap<Long, Plant> plants = new LinkedHashMap<>();
        extractedEntities.getOrDefault("plants", Collections.emptyList()).stream()
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> addPlantMatches(plants, name));
        extractPlantNamePhrase(question).ifPresent(name -> addPlantMatches(plants, name));
        extractLoosePlantKeyword(question).ifPresent(name -> addPlantMatches(plants, name));
        return new ArrayList<>(plants.values());
    }

    private Optional<String> extractLoosePlantKeyword(String question) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.isBlank()) return Optional.empty();
        String cleaned = normalized
                .replaceAll("\\b(cho toi biet|thong tin ve|ve cay|cay|cong dung|tac dung|thanh phan|dac diem|ten khoa hoc|bo phan|mo ta|la cay gi)\\b", " ")
                .replaceAll("\\b(cach dung|cach su dung|su dung|lieu luong|lieu dung|uong nhu the nao|dung nhu the nao|uong bao nhieu|dung bao nhieu|uong|dung|ra sao|nhu the nao)\\b", " ")
                .replaceAll("\\b(tac dung phu|tac dung khong mong muon|rui ro|dung lau|su dung lau|chong chi dinh|ai khong nen|khong nen dung|doi tuong khong nen|can than trong|than trong|co|khong|ai)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() < 2 || cleaned.split("\\s+").length > 4) {
            return Optional.empty();
        }
        return Optional.of(cleaned);
    }

    private String buildSinglePlantFieldAnswer(Plant plant, String prefix, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        result.append(prefix).append(summarizeText(value, 260));
        appendPlantSource(result, plant);
        return result.toString().trim();
    }

    private String buildPlantInfoAnswer(Plant plant, String normalizedQuestion) {
        if (normalizedQuestion.contains("ten khoa hoc")) {
            return buildSinglePlantFieldAnswer(plant, "Tên khoa học của " + plant.getName() + " là ", plant.getScientificName());
        }
        if (normalizedQuestion.contains("bo phan")) {
            return buildSinglePlantFieldAnswer(plant, "Bộ phận dùng của " + plant.getName() + ": ", plant.getPartsUsed());
        }
        if (normalizedQuestion.contains("thanh phan")) {
            return buildSinglePlantFieldAnswer(plant, "Thành phần hóa học của " + plant.getName() + ": ", plant.getChemicalComposition());
        }
        if (normalizedQuestion.contains("dac diem") || normalizedQuestion.contains("mo ta")) {
            return buildSinglePlantFieldAnswer(plant, "Đặc điểm của " + plant.getName() + ": ",
                    firstNonBlank(plant.getBotanicalCharacteristics(), plant.getDescription()));
        }
        if (normalizedQuestion.contains("cong dung") || normalizedQuestion.contains("tac dung")) {
            return buildSinglePlantFieldAnswer(plant, "Công dụng của " + plant.getName() + ": ",
                    firstNonBlank(plant.getMedicinalUses(), plant.getIndications()));
        }

        StringBuilder result = new StringBuilder();
        result.append(plant.getName());
        if (plant.getScientificName() != null && !plant.getScientificName().isBlank()) {
            result.append(" (").append(plant.getScientificName()).append(")");
        }
        String description = summarizeText(plant.getDescription(), 220);
        if (!description.isBlank()) {
            result.append(": ").append(description);
        }
        if (plant.getPartsUsed() != null && !plant.getPartsUsed().isBlank()) {
            result.append("\nBộ phận dùng: ").append(summarizeText(plant.getPartsUsed(), 120));
        }
        if (plant.getMedicinalUses() != null && !plant.getMedicinalUses().isBlank()) {
            result.append("\nCông dụng: ").append(summarizeText(plant.getMedicinalUses(), 160));
        }
        appendPlantSource(result, plant);
        return result.toString().trim();
    }

    private void appendPlantSource(StringBuilder result, Plant plant) {
        if (plant.getSlug() == null || plant.getSlug().isBlank()) return;
        result.append("\n\nNguồn: [")
                .append(plant.getName())
                .append("](/plant-detail/")
                .append(plant.getSlug())
                .append(")");
    }

    private String answerFolkRemedyQuestion(String question, Map<String, List<String>> extractedEntities) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (!normalized.contains("bai thuoc") && !normalized.contains("thuoc dan gian")) {
            return null;
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        extractDisplayDiseasePhrase(question).ifPresent(keywords::add);
        extractDiseasePhrase(normalized).ifPresent(keywords::add);
        extractedEntities.getOrDefault("diseases", Collections.emptyList()).forEach(keywords::add);
        if (keywords.isEmpty()) {
            String cleaned = cleanHealthKeyword(normalized
                    .replace("bai thuoc dan gian", " ")
                    .replace("bai thuoc", " ")
                    .replace("thuoc dan gian", " "));
            if (!cleaned.isBlank()) keywords.add(cleaned);
        }

        LinkedHashMap<Long, FolkRemedy> remedies = new LinkedHashMap<>();
        for (String keyword : keywords) {
            String clean = cleanHealthKeyword(keyword);
            if (clean.isBlank()) continue;
            for (FolkRemedy remedy : folkRemedyRepository.findApprovedByKeyword(clean)) {
                if (remedy.getId() != null) remedies.putIfAbsent(remedy.getId(), remedy);
                if (remedies.size() >= 3) break;
            }
            if (remedies.isEmpty()) {
                addFolkRemedyFallbackMatches(remedies, clean);
            }
            if (remedies.size() >= 3) break;
        }

        if (remedies.isEmpty()) {
            return NO_DATA_RESPONSE;
        }

        StringBuilder result = new StringBuilder();
        result.append("Các bài thuốc dân gian phù hợp trong cơ sở dữ liệu:\n");
        remedies.values().stream().limit(3).forEach(remedy -> {
            result.append("- ").append(remedy.getName());
            String note = firstNonBlank(remedy.getDescription(), remedy.getUsageInstruction());
            if (note != null && !note.isBlank()) {
                result.append(": ").append(summarizeText(note, 180));
            }
            result.append("\n");
        });
        result.append("\n");
        remedies.values().stream().limit(3).forEach(remedy ->
                result.append("Nguồn: [").append(remedy.getName()).append("](/folk-remedies/")
                        .append(remedy.getId()).append(")\n"));
        return result.toString().trim();
    }

    private void addFolkRemedyFallbackMatches(Map<Long, FolkRemedy> target, String keyword) {
        String normalizedKeyword = normalizeSearchText(keyword);
        if (normalizedKeyword.isBlank()) return;
        for (FolkRemedy remedy : folkRemedyRepository.findAllApproved()) {
            String searchable = normalizeSearchText(remedy.getName() + " "
                    + nullToEmpty(remedy.getDescription()) + " "
                    + nullToEmpty(remedy.getUsageInstruction()) + " "
                    + remedy.getDiseases().stream()
                            .map(disease -> disease == null ? "" : disease.getName())
                            .collect(Collectors.joining(" ")));
            if (!searchable.contains(normalizedKeyword)) continue;
            if (remedy.getId() != null) target.putIfAbsent(remedy.getId(), remedy);
            if (target.size() >= 3) break;
        }
    }

    private String answerResearchQuestion(String question, Map<String, List<String>> extractedEntities) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (!normalized.contains("nghien cuu")
                && !normalized.contains("khoa hoc")
                && !normalized.contains("bang chung")) {
            return null;
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        extractedEntities.getOrDefault("plants", Collections.emptyList()).forEach(keywords::add);
        extractedEntities.getOrDefault("diseases", Collections.emptyList()).forEach(keywords::add);
        extractPlantNamePhrase(question).ifPresent(keywords::add);
        keywords.removeIf(this::isGenericResearchKeyword);
        if (keywords.isEmpty()) {
            String cleaned = cleanHealthKeyword(normalized
                    .replace("nghien cuu", " ")
                    .replace("khoa hoc", " ")
                    .replace("bang chung", " "));
            if (!cleaned.isBlank() && !isGenericResearchKeyword(cleaned)) keywords.add(cleaned);
        }

        LinkedHashMap<Long, Research> researches = new LinkedHashMap<>();
        if (keywords.isEmpty()) {
            researchRepository.findAllPublicByParam(null, null, null, PageRequest.of(0, 3))
                    .forEach(research -> researches.putIfAbsent(research.getId(), research));
        } else {
            for (String keyword : keywords) {
                String clean = cleanHealthKeyword(keyword);
                if (clean.isBlank()) continue;
                researchRepository.findAllPublicByParam(clean, null, null, PageRequest.of(0, 3))
                        .forEach(research -> researches.putIfAbsent(research.getId(), research));
                if (researches.size() >= 3) break;
            }
        }

        if (researches.isEmpty()) {
            return NO_DATA_RESPONSE;
        }

        StringBuilder result = new StringBuilder();
        result.append("Các nghiên cứu phù hợp trong cơ sở dữ liệu:\n");
        researches.values().stream().limit(3).forEach(research -> {
            result.append("- ").append(research.getTitle());
            if (research.getPublishedYear() != null) {
                result.append(" (").append(research.getPublishedYear()).append(")");
            }
            String note = summarizeText(firstNonBlank(research.getAbstractText(), research.getContent()), 180);
            if (!note.isBlank()) {
                result.append(": ").append(note);
            }
            result.append("\n");
        });
        result.append("\n");
        researches.values().stream()
                .filter(research -> research.getSlug() != null && !research.getSlug().isBlank())
                .limit(3)
                .forEach(research -> result.append("Nguồn: [")
                        .append(research.getTitle())
                        .append("](/research-detail/")
                        .append(research.getSlug())
                        .append(")\n"));
        return result.toString().trim();
    }

    private boolean isGenericResearchKeyword(String keyword) {
        String normalized = cleanHealthKeyword(keyword);
        return normalized.isBlank()
                || normalized.equals("cay")
                || normalized.equals("duoc lieu")
                || normalized.equals("cay duoc lieu")
                || normalized.equals("thao duoc")
                || normalized.equals("y hoc co truyen");
    }

    private boolean isOutOfScopeQuestion(String question) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.isBlank()) return false;
        return normalized.matches(".*\\b(thoi tiet|bong da|chung khoan|gia vang|bitcoin|lap trinh|python|java|phim|game|du lich|ve may bay|khach san|chinh tri)\\b.*");
    }

    private boolean isPlantToDiseaseQuestion(String question, Map<String, List<String>> extractedEntities) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.isBlank()) return false;
        boolean asksForDisease = normalized.matches(".*\\b(benh gi|benh nao|chua benh gi|tri benh gi|ho tro benh nao|ho tro benh gi|tot cho benh nao|co tac dung voi benh nao|dung cho benh gi)\\b.*");
        if (!asksForDisease) return false;
        return !extractedEntities.getOrDefault("plants", Collections.emptyList()).isEmpty()
                || extractPlantNamePhrase(question).isPresent();
    }

    private List<Plant> findPlantsForPlantToDiseaseQuestion(String question,
            Map<String, List<String>> extractedEntities) {
        LinkedHashMap<Long, Plant> plants = new LinkedHashMap<>();
        extractedEntities.getOrDefault("plants", Collections.emptyList()).stream()
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> addPlantMatches(plants, name));
        extractPlantNamePhrase(question).ifPresent(name -> addPlantMatches(plants, name));
        return new ArrayList<>(plants.values());
    }

    private Optional<String> extractPlantNamePhrase(String question) {
        if (question == null || question.isBlank()) return Optional.empty();
        List<Pattern> patterns = List.of(
                Pattern.compile("\\bcây\\s+(.+?)(?:\\s+(?:chữa|trị|hỗ trợ|tốt cho|dùng cho|có tác dụng|liên quan)|[?.!,]|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile("^(.+?)\\s+(?:chữa|trị|hỗ trợ|tốt cho|dùng cho|có tác dụng)\\s+bệnh", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(question);
            if (matcher.find()) {
                String name = cleanPlantDisplayName(matcher.group(1));
                if (!name.isBlank()) return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private void addPlantMatches(Map<Long, Plant> target, String name) {
        String keyword = cleanPlantKeyword(name);
        if (keyword.isBlank()) return;
        addPublishedPlants(target, plantRepository.findByNameContainingIgnoreCase(keyword));
        addPublishedPlants(target, plantRepository.findByScientificNameContainingIgnoreCase(keyword));
        addPublishedPlants(target, plantRepository.findByOtherNamesContainingIgnoreCase(keyword));
    }

    private String cleanPlantDisplayName(String value) {
        if (value == null) return "";
        return value.replaceAll("(?iu)\\b(cây|dược liệu|này|đó|nào|gì|có|thể|là)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanPlantKeyword(String value) {
        if (value == null) return "";
        return normalizeSearchText(value)
                .replaceAll("\\b(cay|duoc lieu|nay|do|nao|gi|co|the|la)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isDiseaseToPlantQuestion(String question) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.isBlank()) return false;
        boolean asksForPlant = normalized.matches(".*\\b(cay nao|cay gi|cay thuoc nao|loai cay nao|cay duoc lieu nao|dung cay gi|dung gi|uong gi|nen dung|nen uong|chua bang cay|tri bang cay|ho tro bang cay)\\b.*");
        boolean hasHealthSignal = normalized.matches(".*\\b(benh|bi|dau|viem|ho|mat ngu|kho ngu|tieu duong|huyet ap|gan|than|da day|bao tu|tieu hoa|mun|ngua|cam|sot)\\b.*")
                || extractDiseasePhrase(normalized).isPresent();
        return asksForPlant && hasHealthSignal;
    }

    private List<String> extractHealthTerms(String question, Map<String, List<String>> extractedEntities) {
        LinkedHashMap<String, String> terms = new LinkedHashMap<>();
        extractDelimitedSymptoms(question).forEach(term -> addHealthTerm(terms, term));

        extractDisplayDiseasePhrase(question)
                .map(this::splitHealthTerms)
                .ifPresent(values -> values.forEach(term -> addHealthTerm(terms, term)));

        if (terms.isEmpty()) {
            String normalized = normalizeSearchText(question);
            extractDiseasePhrase(normalized)
                    .map(this::splitHealthTerms)
                    .ifPresent(values -> values.forEach(term -> addHealthTerm(terms, term)));
        }

        if (terms.isEmpty()) {
            extractLooseHealthPhrase(question)
                    .map(this::splitHealthTerms)
                    .ifPresent(values -> values.forEach(term -> addHealthTerm(terms, term)));
        }

        if (terms.isEmpty()) {
            extractedEntities.getOrDefault("diseases", Collections.emptyList()).stream()
                    .map(this::cleanHealthKeyword)
                    .filter(term -> !term.isBlank())
                    .flatMap(term -> splitHealthTerms(term).stream())
                    .forEach(term -> addHealthTerm(terms, term));
        }
        return new ArrayList<>(terms.values()).stream()
                .filter(term -> term != null && !term.isBlank())
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<String> extractDelimitedSymptoms(String question) {
        if (question == null || question.isBlank()) return Collections.emptyList();
        if (!question.contains(",") && !question.contains(";")) return Collections.emptyList();
        String normalized = normalizeSearchText(question);
        if (!normalized.matches(".*\\b(bi|toi bi|nguoi bi)\\b.*")) return Collections.emptyList();
        String normalizedSymptoms = normalized.replaceFirst("^.*\\bbi\\s+", "")
                .replaceFirst("\\s+(?:co the\\s+)?(?:dung|uong|nen dung|nen uong|chua|tri|ho tro|bang).*$", "")
                .trim();
        if (!normalizedSymptoms.isBlank()) {
            return splitHealthTerms(normalizedSymptoms);
        }

        String symptoms = question.replaceFirst("(?iu)^.*\\bbị\\s+", "")
                .replaceFirst("(?iu)\\s+(?:có thể\\s+)?(?:dùng|uống|nên dùng|nên uống|chữa|trị|hỗ trợ|bằng).*$", "")
                .trim();
        return splitHealthTerms(symptoms);
    }

    private void addHealthTerm(Map<String, String> terms, String term) {
        String cleaned = cleanDisplayHealthKeyword(term);
        if (cleaned.isBlank()) cleaned = cleanHealthKeyword(term);
        String key = normalizeSearchText(cleaned);
        if (key.isBlank()) return;
        terms.putIfAbsent(key, cleaned);
    }

    private List<String> splitHealthTerms(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        if (System.nanoTime() >= 0) {
            String normalized = cleanHealthKeyword(value);
            if (normalized.isBlank()) return Collections.emptyList();
            return Arrays.stream(normalized.split("\\s*(?:,|;|\\+|/|\\bva\\b|\\bkem\\b|\\bvoi\\b|\\bdong thoi\\b)\\s*"))
                    .map(this::cleanHealthKeyword)
                    .filter(term -> !term.isBlank())
                    .filter(term -> normalizeSearchText(term).length() >= 3 || normalizeSearchText(term).equals("ho"))
                    .distinct()
                    .limit(3)
                    .collect(Collectors.toList());
        }
        String cleaned = cleanDisplayHealthKeyword(value);
        if (cleaned.isBlank()) cleaned = cleanHealthKeyword(value);
        return Arrays.stream(cleaned.split("(?iu)\\s*(?:,|;|\\+|/|\\bvà\\b|\\bkèm\\b|\\bkem\\b|\\bvoi\\b|\\bvới\\b|\\bdong thoi\\b|\\bđồng thời\\b)\\s*"))
                .map(this::cleanDisplayHealthKeyword)
                .map(term -> term.isBlank() ? "" : term)
                .filter(term -> !term.isBlank())
                .filter(term -> normalizeSearchText(term).length() >= 3)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
    }

    private Optional<String> extractDisplayDiseasePhrase(String question) {
        if (question == null || question.isBlank()) return Optional.empty();
        List<Pattern> patterns = List.of(
                Pattern.compile("\\bbệnh\\s+(.+?)(?:\\s+(?:dùng|uống|nên|chữa|trị|hỗ trợ|bằng|thiếu|cần)|[?.!,]|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile("\\bbị\\s+(.+?)(?:\\s+(?:dùng|uống|nên|chữa|trị|hỗ trợ|bằng)|[?.!,]|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile("\\b(?:chữa|trị|hỗ trợ)\\s+(.+?)(?:\\s+(?:bằng|với|được|nên|không)|[?.!,]|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile("\\b(?:tốt cho|phù hợp với|liên quan đến)\\s+(.+?)(?:\\s+(?:không|là|nên)|[?.!,]|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(question);
            if (matcher.find()) {
                String term = cleanDisplayHealthKeyword(matcher.group(1));
                if (!term.isBlank()) return Optional.of(term);
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractDiseasePhrase(String normalizedQuestion) {
        List<Pattern> patterns = List.of(
                Pattern.compile("\\bbenh\\s+(.+?)(?:\\s+(?:thi|dung|uong|nen|chua|tri|ho tro|bang|thieu|can)|[?.!,]|$)"),
                Pattern.compile("\\bbi\\s+(.+?)(?:\\s+(?:thi|dung|uong|nen|chua|tri|ho tro|bang)|[?.!,]|$)"),
                Pattern.compile("\\b(?:chua|tri|ho tro)\\s+(.+?)(?:\\s+(?:bang|voi|duoc|nen|khong)|[?.!,]|$)"),
                Pattern.compile("\\b(?:tot cho|phu hop voi|lien quan den)\\s+(.+?)(?:\\s+(?:khong|la|nen)|[?.!,]|$)")
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(normalizedQuestion);
            if (matcher.find()) {
                String term = cleanHealthKeyword(matcher.group(1));
                if (!term.isBlank()) return Optional.of(term);
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractLooseHealthPhrase(String question) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.isBlank()) return Optional.empty();
        if (!normalized.matches(".*\\b(cay nao|cay gi|cay thuoc nao|loai cay nao|cay duoc lieu nao|dung cay gi|dung gi|uong gi|nen dung|nen uong)\\b.*")) {
            return Optional.empty();
        }

        String beforeAction = normalized
                .replaceFirst("\\b(?:thi\\s+)?(?:co the\\s+)?(?:nen dung|nen uong|dung cay gi|dung gi|uong gi|cay thuoc nao|loai cay nao|cay duoc lieu nao|cay nao|cay gi).*$", " ");
        String cleaned = cleanHealthKeyword(beforeAction
                .replaceAll("\\b(toi|minh|em|hay|thuong|dang|nguoi|co|trieu chung|van de|suc khoe)\\b", " "));
        return cleaned.isBlank() ? Optional.empty() : Optional.of(cleaned);
    }

    private String cleanHealthKeyword(String value) {
        if (value == null) return "";
        return normalizeSearchText(value)
                .replaceAll("\\b(cay|thuoc|duoc lieu|nao|gi|co|the|la|thi|khong|dung|uong|nen|chua|tri|ho tro|bang|cho|toi|nguoi|benh|bi)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanDisplayHealthKeyword(String value) {
        if (value == null) return "";
        return value.replaceAll("(?iu)\\b(cây|dược liệu|nào|gì|có|thể|là|dùng|uống|nên|chữa|trị|hỗ trợ|bằng|cho|tôi|người|bệnh|bị)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String toHealthDisplayText(String value) {
        String normalized = cleanHealthKeyword(value);
        if (normalized.isBlank()) return value == null ? "" : value;
        return switch (normalized) {
            case "tieu duong" -> "tiểu đường";
            case "mat ngu", "kho ngu" -> "mất ngủ";
            case "dau dau" -> "đau đầu";
            case "dau da day" -> "đau dạ dày";
            case "dau bao tu", "bao tu" -> "đau bao tử";
            case "viem hong" -> "viêm họng";
            case "nhieu dom" -> "nhiều đờm";
            case "dom" -> "đờm";
            case "ho" -> "ho";
            case "viem loet da day" -> "viêm loét dạ dày";
            default -> value;
        };
    }

    private List<String> expandHealthSearchKeywords(String term) {
        String keyword = cleanHealthKeyword(term);
        if (keyword.isBlank()) return Collections.emptyList();

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (keyword.equals("kho ngu") || keyword.equals("ho")) {
            HEALTH_KEYWORD_ALIASES.getOrDefault(keyword, Collections.emptyList()).forEach(keywords::add);
            keywords.add(keyword);
        } else {
            keywords.add(keyword);
            HEALTH_KEYWORD_ALIASES.getOrDefault(keyword, Collections.emptyList()).forEach(keywords::add);
        }

        for (Map.Entry<String, List<String>> entry : HEALTH_KEYWORD_ALIASES.entrySet()) {
            if (keyword.contains(entry.getKey())) {
                entry.getValue().forEach(keywords::add);
            }
        }

        return keywords.stream()
                .map(this::cleanHealthKeyword)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private void searchPlantsByHealthKeyword(Map<Long, PlantMatchCandidate> candidates,
            Set<String> matchedTerms, String term, String keyword) {
        if (keyword == null || keyword.isBlank()) return;

        for (PlantDiseases relation : plantDiseasesRepository.findPublishedPlantsByDiseaseKeyword(keyword)) {
            Plant plant = relation.getPlant();
            if (plant == null || plant.getId() == null) continue;
            addPlantCandidate(candidates, plant, term, 3);
            if (relation.getDiseases() != null && relation.getDiseases().getName() != null) {
                matchedTerms.add(relation.getDiseases().getName());
            }
        }

        addPlantCandidates(candidates, plantRepository.findByIndicationsContainingIgnoreCase(keyword), term, 2);
        addPlantCandidates(candidates, plantRepository.findByMedicinalUsesContainingIgnoreCase(keyword), term, 1);
    }

    private int countPlantTextMatches(Plant plant, List<String> healthTerms) {
        if (plant == null || healthTerms == null || healthTerms.isEmpty()) return 0;
        String searchable = normalizeSearchText(nullToEmpty(plant.getName()) + " "
                + nullToEmpty(plant.getIndications()) + " "
                + nullToEmpty(plant.getMedicinalUses()) + " "
                + nullToEmpty(plant.getFolkRemedies()));
        int count = 0;
        for (String term : healthTerms) {
            String keyword = cleanHealthKeyword(term);
            if (keyword.isBlank()) continue;
            if (searchable.contains(keyword)
                    || HEALTH_KEYWORD_ALIASES.getOrDefault(keyword, Collections.emptyList()).stream()
                            .anyMatch(alias -> !alias.isBlank() && searchable.contains(alias))) {
                count++;
            }
        }
        return count;
    }

    private void addPublishedPlants(Map<Long, Plant> target, List<Plant> candidates) {
        if (candidates == null) return;
        for (Plant plant : candidates) {
            if (plant == null || plant.getId() == null) continue;
            if (plant.getPlantStatus() != PlantStatus.DA_XUAT_BAN) continue;
            target.putIfAbsent(plant.getId(), plant);
        }
    }

    private void addPlantCandidates(Map<Long, PlantMatchCandidate> target,
            List<Plant> plants, String matchedTerm, int score) {
        if (plants == null) return;
        for (Plant plant : plants) {
            addPlantCandidate(target, plant, matchedTerm, score);
        }
    }

    private void addPlantCandidate(Map<Long, PlantMatchCandidate> target,
            Plant plant, String matchedTerm, int score) {
        if (plant == null || plant.getId() == null) return;
        if (plant.getPlantStatus() != PlantStatus.DA_XUAT_BAN) return;
        PlantMatchCandidate candidate = target.computeIfAbsent(plant.getId(), id -> new PlantMatchCandidate(plant));
        candidate.score += Math.max(score, 1);
        String cleanTerm = cleanDisplayHealthKeyword(matchedTerm);
        if (cleanTerm.isBlank()) cleanTerm = cleanHealthKeyword(matchedTerm);
        if (!cleanTerm.isBlank()) {
            candidate.matchedTerms.add(cleanTerm);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String summarizeText(String text, int maxLength) {
        if (text == null) return "";
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) return cleaned;
        int cut = cleaned.lastIndexOf('.', maxLength);
        if (cut < 80) cut = cleaned.lastIndexOf(';', maxLength);
        if (cut < 80) cut = cleaned.lastIndexOf(',', maxLength);
        if (cut < 80) cut = maxLength;
        return cleaned.substring(0, cut).trim() + "...";
    }

    private static class PlantMatchCandidate {
        private final Plant plant;
        private final Set<String> matchedTerms = new LinkedHashSet<>();
        private int score;

        private PlantMatchCandidate(Plant plant) {
            this.plant = plant;
        }
    }

    private void addExactChunks(Map<Long, ScoredChunk> target, List<ChunkEmbedding> chunks) {
        for (ChunkEmbedding ce : chunks) {
            ScoredChunk sc = new ScoredChunk(ce);
            sc.setFtsScore(1.0);
            sc.setCosineScore(1.0);
            sc.setCombinedScore(1.0);
            target.putIfAbsent(ce.getId(), sc);
        }
    }

    static boolean containsNormalizedPhrase(String text, String phrase) {
        if (text == null || phrase == null) return false;
        return normalizeSearchText(text).contains(normalizeSearchText(phrase));
    }

    private static String normalizeSearchText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
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
            String linkUrl = sourcePath(ce);
            // Gửi context: tên cây + link để AI trả về nguồn có đường dẫn
            String entityName = ce.getEntityName() != null ? ce.getEntityName() : "N/A";
            context.append(String.format(
                    "--- Tài liệu %d [Loại: %s | Tên: %s | Link: %s] ---\n",
                    i + 1,
                    getContentTypeLabel(ce.getContentType()),
                    entityName,
                    linkUrl.isEmpty() ? "#" : linkUrl));
            context.append(ce.getChunkText()).append("\n\n");
        }
        return context.toString();
    }

    private String appendVerifiedSources(String response, List<ScoredChunk> chunks, String question) {
        if (response == null || response.isBlank()
                || response.startsWith("❌") || response.startsWith("⚠") || response.startsWith("🌿")) {
            return response;
        }

        String withoutGeneratedSources = stripGeneratedSources(response);
        Map<String, String> sources = new LinkedHashMap<>();
        ChunkEmbedding.ContentType requestedType = requestedContentType(question);
        for (ScoredChunk scoredChunk : chunks) {
            ChunkEmbedding chunk = scoredChunk.getChunk();
            if (chunk.getContentType() != requestedType) continue;
            if (chunk.getEntityName() == null) continue;
            String path = sourcePath(chunk);
            if (path.isEmpty() || "#".equals(path)) continue;
            sources.putIfAbsent(path, chunk.getEntityName());
        }
        if (sources.isEmpty()) return withoutGeneratedSources;

        StringBuilder result = new StringBuilder(withoutGeneratedSources).append("\n\n");
        sources.forEach((path, name) -> result.append("Nguồn: [")
                .append(name).append("](").append(path).append(")\n"));
        return result.toString().trim();
    }

    static String sourcePath(ChunkEmbedding chunk) {
        if (chunk == null || chunk.getContentType() == null) return "";
        switch (chunk.getContentType()) {
            case folk_remedy:
                return chunk.getEntityId() == null ? "" : "/folk-remedies/" + chunk.getEntityId();
            case plant:
                return chunk.getEntitySlug() == null ? "" : "/plant-detail/" + chunk.getEntitySlug();
            case article:
                return chunk.getEntitySlug() == null ? "" : "/article-detail/" + chunk.getEntitySlug();
            case research:
                return chunk.getEntitySlug() == null ? "" : "/research-detail/" + chunk.getEntitySlug();
            default:
                return "#";
        }
    }

    static ChunkEmbedding.ContentType requestedContentType(String question) {
        String normalized = normalizeSearchText(question == null ? "" : question);
        if (normalized.contains("bai thuoc")) return ChunkEmbedding.ContentType.folk_remedy;
        if (normalized.contains("bai viet")) return ChunkEmbedding.ContentType.article;
        if (normalized.contains("nghien cuu")) return ChunkEmbedding.ContentType.research;
        return ChunkEmbedding.ContentType.plant;
    }

    static String stripGeneratedSources(String response) {
        if (response == null) return null;
        return response.replaceAll(
                "(?im)^\\s*(?:[-*]\\s*)?nguồn\\s*:\\s*.*(?:\\R|$)", "")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
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
                    String cleanedCfResponse = cleanResponse(cfResponse);
                    if (coversRequestedIntents(cleanedCfResponse, question)) {
                        log.debug("S\u1EED d\u1EE5ng Cloudflare Worker cho generate");
                        return cleanedCfResponse;
                    }
                    log.warn("Cloudflare generate bỏ sót một phần câu hỏi, fallback sang Gemini");
                    String geminiResponse = cleanResponse(callGeminiWithRetry(prompt));
                    return coversRequestedIntents(geminiResponse, question)
                            ? geminiResponse : cleanedCfResponse;
                }
                log.warn("Cloudflare chat tr\u1EA3 v\u1EC1 r\u1ED7ng, fallback sang Gemini...");
            } catch (Exception e) {
                log.warn("Cloudflare chat l\u1ED7i, fallback sang Gemini: {}", e.getMessage());
            }
        }

        log.debug("S\u1EED d\u1EE5ng Gemini cho generate (fallback)");
        return cleanResponse(callGeminiWithRetry(prompt));
    }

    static boolean coversRequestedIntents(String response, String question) {
        if (response == null || response.isBlank() || question == null) return false;
        String normalizedQuestion = normalizeSearchText(question);
        String normalizedResponse = normalizeSearchText(response);

        if (normalizedQuestion.contains("chong chi dinh")
                && !(normalizedResponse.contains("chong chi dinh")
                || normalizedResponse.contains("khong dung")
                || normalizedResponse.contains("than trong"))) {
            return false;
        }
        if ((normalizedQuestion.contains("lieu dung") || normalizedQuestion.contains("lieu luong"))
                && !(normalizedResponse.contains("lieu dung")
                || normalizedResponse.matches(".*\\b\\d+[\\s-]*(g|mg|ml|lan|am|ngay)\\b.*"))) {
            return false;
        }
        return true;
    }

    /**
     * Clean response: strip markdown artifacts, normalize whitespace.
     * Kh\u00F4ng c\u1EA7n strip HTML n\u1EEFa v\u00EC context \u0111\u00E3 s\u1EA1ch.
     */
    private String cleanResponse(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        // Giữ nguyên các thông báo lỗi/từ chối có icon
        if (text.startsWith("\u274C") || text.startsWith("\u26A0") || text.startsWith("\uD83C\uDF3F")) return text.trim();

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
        if (System.nanoTime() >= 0) {
            return NO_DATA_RESPONSE;
        }
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

    static boolean requiresSpecificEntity(String question) {
        if (question == null) return false;
        String normalized = java.text.Normalizer.normalize(question, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
        return normalized.matches(".*\\b(lieu luong|lieu dung|cach dung|cach su dung|su dung|uong bao nhieu|dung bao nhieu|chong chi dinh|tac dung phu)\\b.*");
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
