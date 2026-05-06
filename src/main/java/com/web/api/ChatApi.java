package com.web.api;

import com.web.service.ChatService;
import com.web.service.EmbeddingService;
import com.web.service.FolkRemedyService;
import com.web.service.RateLimitService;
import com.web.repository.ChunkEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Chat API Controller.
 * 
 * THAY ĐỔI CHÍNH:
 * - index-rag chạy async, trả response ngay lập tức
 * - Thêm endpoint kiểm tra tiến trình indexing
 */
@RestController
@RequestMapping("/api/chat")
public class ChatApi {

    private static final Logger logger = LoggerFactory.getLogger(ChatApi.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ChunkEmbeddingRepository chunkEmbeddingRepository;

    @Autowired(required = false)
    private FolkRemedyService folkRemedyService;

    @PostMapping
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> request, 
            HttpSession session,
            HttpServletRequest httpRequest) {
        
        String userMessage = request.get("message");
        
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("reply", "Tin nhắn không được để trống."));
        }

        String clientIp = getClientIp(httpRequest);
        Long rateLimitKey = (long) clientIp.hashCode();
        
        if (rateLimitService.isRateLimited(rateLimitKey)) {
            logger.warn("Rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("reply", "Bạn đã gửi quá nhiều tin nhắn. Vui lòng đợi một chút rồi thử lại."));
        }

        try {
            String reply = chatService.chatWithGemini(userMessage, session);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            logger.error("Error processing chat request from IP: {}", clientIp, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("reply", "Xin lỗi, hệ thống đang bận. Vui lòng thử lại sau."));
        }
    }

    /**
     * Admin: Trigger RAG indexing cho tất cả nội dung — ASYNC.
     * 
     * THAY ĐỔI: Không block HTTP thread nữa.
     * - Trả response ngay lập tức với status "indexing_started"
     * - Indexing chạy trên thread pool riêng (ragIndexingExecutor)
     * - Dùng GET /api/chat/admin/indexing-progress để theo dõi tiến trình
     */
    @PostMapping("/admin/index-rag")
    public ResponseEntity<Map<String, Object>> indexRag() {
        try {
            // Kiểm tra nếu đang indexing
            if (embeddingService.isIndexing()) {
                Map<String, Object> busyResp = new HashMap<>();
                busyResp.put("status", "already_running");
                busyResp.put("progress", embeddingService.getIndexingProgress());
                busyResp.put("message", "Indexing đang chạy, vui lòng đợi hoàn tất");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(busyResp);
            }

            logger.info("Admin triggered ASYNC RAG indexing...");

            // Lấy folk remedies list trước khi dispatch async
            java.util.List<com.web.entity.FolkRemedy> folkRemedies = java.util.Collections.emptyList();
            if (folkRemedyService != null) {
                folkRemedies = folkRemedyService.findAllApproved();
            }

            // Dispatch async — KHÔNG BLOCK, trả response ngay
            embeddingService.indexAllAsync(folkRemedies);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "indexing_started");
            result.put("message", "Indexing đã được khởi động. Dùng GET /api/chat/admin/indexing-progress để theo dõi.");
            
            logger.info("RAG indexing dispatched to async executor");
            return ResponseEntity.accepted().body(result);

        } catch (Exception e) {
            logger.error("RAG indexing dispatch failed", e);
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("status", "error");
            errorResp.put("message", e.getMessage() != null ? e.getMessage() : e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResp);
        }
    }

    /**
     * Admin: Kiểm tra tiến trình indexing.
     * GET /api/chat/admin/indexing-progress
     */
    @GetMapping("/admin/indexing-progress")
    public ResponseEntity<Map<String, Object>> indexingProgress() {
        Map<String, Object> progress = new HashMap<>();
        progress.put("isRunning", embeddingService.isIndexing());
        progress.put("progress", embeddingService.getIndexingProgress());
        progress.put("status", embeddingService.getIndexingStatus());
        progress.put("totalChunks", chunkEmbeddingRepository.count());
        progress.put("chunksWithEmbedding", chunkEmbeddingRepository.countWithEmbedding());
        return ResponseEntity.ok(progress);
    }

    /**
     * Admin: Kiểm tra trạng thái RAG index
     * GET /api/chat/admin/rag-status
     */
    @GetMapping("/admin/rag-status")
    public ResponseEntity<Map<String, Object>> ragStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalChunks", chunkEmbeddingRepository.count());
        status.put("chunksWithEmbedding", chunkEmbeddingRepository.countWithEmbedding());
        status.put("plantChunks", chunkEmbeddingRepository.countByContentType(
                com.web.entity.ChunkEmbedding.ContentType.plant));
        status.put("articleChunks", chunkEmbeddingRepository.countByContentType(
                com.web.entity.ChunkEmbedding.ContentType.article));
        status.put("researchChunks", chunkEmbeddingRepository.countByContentType(
                com.web.entity.ChunkEmbedding.ContentType.research));
        status.put("folkRemedyChunks", chunkEmbeddingRepository.countByContentType(
                com.web.entity.ChunkEmbedding.ContentType.folk_remedy));
        status.put("indexingInProgress", embeddingService.isIndexing());
        return ResponseEntity.ok(status);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
