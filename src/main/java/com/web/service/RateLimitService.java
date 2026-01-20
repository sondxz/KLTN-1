package com.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service để rate limiting cho WebSocket messages
 */
@Service
public class RateLimitService {

    @Value("${websocket.rate.limit.messages-per-second:10}")
    private int messagesPerSecond;

    @Value("${websocket.rate.limit.messages-per-minute:60}")
    private int messagesPerMinute;

    // Map lưu số tin nhắn của mỗi user trong giây hiện tại
    private final Map<Long, AtomicInteger> messagesPerSecondMap = new ConcurrentHashMap<>();
    
    // Map lưu số tin nhắn của mỗi user trong phút hiện tại
    private final Map<Long, AtomicInteger> messagesPerMinuteMap = new ConcurrentHashMap<>();
    
    // Map lưu timestamp của request cuối cùng
    private final Map<Long, Long> lastRequestTime = new ConcurrentHashMap<>();

    /**
     * Kiểm tra xem user có vượt quá rate limit không
     */
    public boolean isRateLimited(Long userId) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastRequestTime.get(userId);
        
        // Reset counter nếu đã qua 1 giây
        if (lastTime == null || (currentTime - lastTime) >= 1000) {
            messagesPerSecondMap.put(userId, new AtomicInteger(0));
            lastRequestTime.put(userId, currentTime);
        }
        
        // Reset counter nếu đã qua 1 phút
        if (lastTime == null || (currentTime - lastTime) >= 60000) {
            messagesPerMinuteMap.put(userId, new AtomicInteger(0));
        }

        // Kiểm tra rate limit per second
        AtomicInteger secondCount = messagesPerSecondMap.computeIfAbsent(userId, k -> new AtomicInteger(0));
        if (secondCount.incrementAndGet() > messagesPerSecond) {
            return true;
        }

        // Kiểm tra rate limit per minute
        AtomicInteger minuteCount = messagesPerMinuteMap.computeIfAbsent(userId, k -> new AtomicInteger(0));
        if (minuteCount.incrementAndGet() > messagesPerMinute) {
            return true;
        }

        return false;
    }

    /**
     * Reset rate limit cho user (khi cần)
     */
    public void resetRateLimit(Long userId) {
        messagesPerSecondMap.remove(userId);
        messagesPerMinuteMap.remove(userId);
        lastRequestTime.remove(userId);
    }
}



