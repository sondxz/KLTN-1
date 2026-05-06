package com.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình Async cho ứng dụng.
 * Thread pool riêng cho indexing để không block HTTP thread và connection pool.
 */
@Configuration
@EnableAsync
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
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("rag-index-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("RAG indexing task rejected - executor is full"));
        executor.initialize();
        return executor;
    }
}
