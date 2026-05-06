package com.web.service;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service giao tiếp với Cloudflare Worker AI.
 * <p>
 * Cloudflare Worker cung cấp 2 endpoint:
 * - POST /embed : nhận batch text array, trả về mảng embeddings (1024-dim, model bge-m3)
 * - POST /chat  : nhận prompt, trả về text response
 * <p>
 * Ưu điểm so với Gemini: không giới hạn request, miễn phí.
 */
@Service
public class CloudflareAIService {

    private static final Logger log = LoggerFactory.getLogger(CloudflareAIService.class);

    @Value("${cloudflare.worker.url:}")
    private String workerBaseUrl;

    @Value("${cloudflare.worker.api.key:}")
    private String workerApiKey;

    /** Số lần retry tối đa khi gặp lỗi transient */
    private static final int MAX_RETRIES = 3;

    /** Base delay cho exponential backoff (ms) */
    private static final long BASE_DELAY_MS = 1000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Gson gson = new Gson();

    // ================================================
    // EMBEDDING — Batch
    // ================================================

    /**
     * Gọi Cloudflare Worker /embed endpoint với batch texts.
     * Worker nhận: {"texts": ["text1", "text2", ...]}
     * Worker trả: {"embeddings": [[0.1, 0.2, ...], [0.3, 0.4, ...], ...]}
     *
     * @param texts Danh sách text cần embed (max 50 phần tử/batch)
     * @return List các embedding vectors (mỗi vector là List<Double>), hoặc empty list nếu lỗi
     */
    public List<List<Double>> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        if (!isAvailable()) {
            log.warn("Cloudflare Worker URL chưa được cấu hình");
            return Collections.emptyList();
        }

        String url = workerBaseUrl.replaceAll("/$", "") + "/embed";

        JsonObject requestBody = new JsonObject();
        JsonArray textsArray = new JsonArray();
        for (String text : texts) {
            // Truncate text quá dài (bge-m3 giới hạn ~8192 tokens)
            String truncated = text.length() > 3000 ? text.substring(0, 3000) : text;
            textsArray.add(truncated);
        }
        requestBody.add("text", textsArray);

        return callWithRetry(url, requestBody.toString(), response -> {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            // Parse response: {"embeddings": [[...], [...]]}
            if (json.has("embeddings")) {
                JsonArray embeddingsArr = json.getAsJsonArray("embeddings");
                List<List<Double>> result = new ArrayList<>(embeddingsArr.size());
                for (JsonElement embEl : embeddingsArr) {
                    JsonArray vecArr = embEl.getAsJsonArray();
                    List<Double> vec = new ArrayList<>(vecArr.size());
                    for (JsonElement v : vecArr) {
                        vec.add(v.getAsDouble());
                    }
                    result.add(vec);
                }
                return result;
            }

            // Fallback: {"data": [{"embedding": [...]}]}
            if (json.has("data")) {
                JsonArray dataArr = json.getAsJsonArray("data");
                List<List<Double>> result = new ArrayList<>(dataArr.size());
                for (JsonElement dataEl : dataArr) {
                    JsonArray vecArr = dataEl.getAsJsonObject().getAsJsonArray("embedding");
                    List<Double> vec = new ArrayList<>(vecArr.size());
                    for (JsonElement v : vecArr) {
                        vec.add(v.getAsDouble());
                    }
                    result.add(vec);
                }
                return result;
            }

            log.warn("Cloudflare embed response structure không đúng: {}", 
                    response.substring(0, Math.min(200, response.length())));
            return Collections.emptyList();
        });
    }

    /**
     * Embed đơn lẻ 1 text (wrapper tiện dụng cho search query).
     */
    public List<Double> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<List<Double>> results = batchEmbed(List.of(text));
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        return Collections.emptyList();
    }

    // ================================================
    // CHAT
    // ================================================

    /**
     * Gọi Cloudflare Worker /chat endpoint.
     * Worker nhận: {"prompt": "..."}
     * Worker trả: {"response": "..."} hoặc {"result": "..."}
     *
     * @param prompt Full prompt đã bao gồm context
     * @return Text response, hoặc null nếu lỗi
     */
    public String chat(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return null;
        }

        if (!isAvailable()) {
            log.warn("Cloudflare Worker URL chưa được cấu hình");
            return null;
        }

        String url = workerBaseUrl.replaceAll("/$", "") + "/chat";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("prompt", prompt);

        return callWithRetry(url, requestBody.toString(), response -> {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            // Thử các key phổ biến
            if (json.has("response")) {
                return json.get("response").getAsString();
            }
            if (json.has("result")) {
                JsonElement resultEl = json.get("result");
                if (resultEl.isJsonPrimitive()) {
                    return resultEl.getAsString();
                }
                // result có thể là object: {"response": "..."}
                if (resultEl.isJsonObject()) {
                    JsonObject resultObj = resultEl.getAsJsonObject();
                    if (resultObj.has("response")) {
                        return resultObj.get("response").getAsString();
                    }
                }
            }
            if (json.has("text")) {
                return json.get("text").getAsString();
            }

            log.warn("Cloudflare chat response structure không đúng: {}",
                    response.substring(0, Math.min(200, response.length())));
            return null;
        });
    }

    // ================================================
    // HELPER METHODS
    // ================================================

    /**
     * Kiểm tra Cloudflare Worker có sẵn sàng không.
     */
    public boolean isAvailable() {
        return workerBaseUrl != null && !workerBaseUrl.trim().isEmpty();
    }

    /**
     * Gọi HTTP với retry + exponential backoff.
     * Retry khi: 429 (rate limit), 500+ (server error), IOException.
     */
    private <T> T callWithRetry(String url, String body, ResponseParser<T> parser) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60));

                // Gửi API key nếu có cấu hình
                if (workerApiKey != null && !workerApiKey.trim().isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + workerApiKey);
                }

                HttpRequest request = reqBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parser.parse(response.body());
                }

                // Retryable status codes
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    log.warn("Cloudflare API trả về status {} (attempt {}/{}), sẽ retry...",
                            response.statusCode(), attempt, MAX_RETRIES);
                    lastException = new RuntimeException("HTTP " + response.statusCode());
                } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                    // Lỗi xác thực — kiểm tra lại API key
                    log.error("Cloudflare API lỗi xác thực ({}). Kiểm tra lại cloudflare.worker.api.key trong application.properties",
                            response.statusCode());
                    return null;
                } else {
                    // Non-retryable error (4xx khác)
                    log.error("Cloudflare API trả về status {}: {}", response.statusCode(),
                            response.body().substring(0, Math.min(200, response.body().length())));
                    return null;
                }

            } catch (java.net.http.HttpTimeoutException e) {
                log.warn("Cloudflare API timeout (attempt {}/{})", attempt, MAX_RETRIES);
                lastException = e;
            } catch (Exception e) {
                log.warn("Cloudflare API lỗi (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                lastException = e;
            }

            // Exponential backoff: 1s, 2s, 4s
            if (attempt < MAX_RETRIES) {
                try {
                    long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("Cloudflare API thất bại sau {} lần retry: {}", MAX_RETRIES,
                lastException != null ? lastException.getMessage() : "unknown");
        return null;
    }

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(String responseBody);
    }
}
