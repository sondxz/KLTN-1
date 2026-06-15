package com.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Cấu hình Async cho ứng dụng.
 * Thread pool riêng cho indexing để không block HTTP thread và connection pool.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Thread pool dành riêng cho RAG indexing.
     * - corePoolSize=2: chỉ cần 2 thread vì indexing chạy tuần tự bên trong
     * - maxPoolSize=4: tối đa 4 thread nếu có nhiều task đồng thời
     * - queueCapacity=10: hàng đợi nhỏ, không cần buffer lớn
     */
    @Bean(name = "ragIndexingExecutor")
    public Executor ragIndexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("rag-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool dành riêng cho email notification.
     * - corePoolSize=2: đủ cho hầu hết traffic
     * - maxPoolSize=5: mở rộng khi cao tải
     * - queueCapacity=100: buffer email chờ gửi
     */
    @Bean(name = "emailNotificationExecutor")
    public Executor emailNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-notif-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("Email notification task rejected - executor is full"));
        executor.initialize();
        return executor;
    }
}
