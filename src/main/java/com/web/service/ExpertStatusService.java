package com.web.service;

import com.web.entity.User;
import com.web.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Theo dõi trạng thái online/offline của user qua WebSocket events.
 * 
 * CHÚ Ý: WebSocketSecurityConfig set Authentication principal = user.getId().toString(),
 * nên accessor.getUser().getName() trả về user ID (vd: "42"), không phải username.
 * Service này lookup username từ DB (có cache) để broadcast.
 * 
 * Dùng reverse map userId → Set<sessionId> để:
 * - Kiểm tra stillOnline trong O(1) (không cần duyệt toàn bộ map)
 * - Hỗ trợ multi-tab: user chỉ offline khi Set<sessionId> rỗng
 */
@Component
public class ExpertStatusService {

    private static final Logger log = LoggerFactory.getLogger(ExpertStatusService.class);

    // sessionId → userId
    private final ConcurrentHashMap<String, String> sessionToUser = new ConcurrentHashMap<>();

    // userId → Set<sessionId>  (reverse map, O(1) lookup)
    private final ConcurrentHashMap<String, Set<String>> userToSessions = new ConcurrentHashMap<>();

    // Cache userId → username để tránh query DB mỗi lần connect
    // WebSocket CONNECT xảy ra thường xuyên (mỗi tab, mỗi refresh) — không nên query DB mỗi lần
    private final ConcurrentHashMap<String, String> userIdToUsername = new ConcurrentHashMap<>();

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String userId = accessor.getUser() != null
                ? accessor.getUser().getName()  // Trả về user ID (vd: "42")
                : null;

        if (userId == null) {
            return;
        }

        // Cập nhật forward map
        sessionToUser.put(sessionId, userId);

        // Cập nhật reverse map (thread-safe atomic)
        userToSessions.compute(userId, (k, sessions) -> {
            if (sessions == null) sessions = ConcurrentHashMap.newKeySet();
            sessions.add(sessionId);
            return sessions;
        });

        // Lookup username từ cache (không query DB nếu đã có)
        String username = getUsernameCached(userId);
        if (username == null) return;

        log.info("🔵 User ONLINE: {} (userId={}, session={})", username, userId, sessionId);
        broadcastStatus(userId, username, true);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String userId = sessionToUser.remove(sessionId);

        if (userId == null) return;

        // Cập nhật reverse map, xóa session khỏi set
        // Nếu set rỗng → user thực sự offline
        boolean stillOnline = false;
        Set<String> remainingSessions = userToSessions.get(userId);
        if (remainingSessions != null) {
            remainingSessions.remove(sessionId);
            if (remainingSessions.isEmpty()) {
                userToSessions.remove(userId);  // Xóa key khi set rỗng
            } else {
                stillOnline = true;
            }
        }

        if (!stillOnline) {
            String username = getUsernameCached(userId);
            if (username != null) {
                log.info("🔴 User OFFLINE: {} (userId={})", username, userId);
                broadcastStatus(userId, username, false);
            }
            // Cleanup cache khi user offline hoàn toàn
            userIdToUsername.remove(userId);
        } else {
            log.debug("User {} disconnect 1 session (còn {} session)", userId, remainingSessions.size());
        }
    }

    /**
     * Lookup username từ cache. Nếu chưa có → query DB 1 lần rồi cache vĩnh viễn.
     * Tránh query DB mỗi lần WebSocket CONNECT (mỗi tab, mỗi refresh).
     */
    private String getUsernameCached(String userId) {
        // Check cache trước (O(1), không query DB)
        String cached = userIdToUsername.get(userId);
        if (cached != null) return cached;

        // Cache miss → query DB 1 lần duy nhất
        try {
            Long id = Long.parseLong(userId);
            User user = userRepository.findById(id).orElse(null);
            if (user != null && user.getUsername() != null) {
                userIdToUsername.put(userId, user.getUsername());
                return user.getUsername();
            }
        } catch (Exception e) {
            log.warn("Lỗi lookup username cho userId={}: {}", userId, e.getMessage());
        }
        return null;
    }

    /**
     * Gửi trạng thái online/offline qua WebSocket broadcast.
     */
    private void broadcastStatus(String userId, String username, boolean online) {
        Map<String, Object> payload = Map.of(
                "userId", userId,
                "username", username,
                "online", online
        );
        messagingTemplate.convertAndSend("/topic/expert-status", payload);
    }

    // ===== PUBLIC API =====

    public boolean isOnline(String userId) {
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * Lấy danh sách userId đang online.
     */
    public Set<String> getOnlineUserIds() {
        return new HashSet<>(userToSessions.keySet());
    }
}
