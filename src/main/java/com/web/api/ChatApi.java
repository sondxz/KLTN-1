package com.web.api;

import com.web.service.ChatService;
import com.web.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatApi {

    private static final Logger logger = LoggerFactory.getLogger(ChatApi.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private RateLimitService rateLimitService;

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
