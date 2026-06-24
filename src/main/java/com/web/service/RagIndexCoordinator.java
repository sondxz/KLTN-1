package com.web.service;

import com.web.entity.ChunkEmbedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RagIndexCoordinator {

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * Đồng bộ lại RAG sau khi transaction lưu dữ liệu thành công.
     */
    public void syncAfterCommit(ChunkEmbedding.ContentType contentType, Long entityId) {
        if (entityId == null) {
            return;
        }

        Runnable sync = () -> embeddingService.syncEntityAsync(contentType, entityId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * Chạy đồng bộ RAG sau khi commit.
                 */
                @Override
                public void afterCommit() {
                    sync.run();
                }
            });
        } else {
            sync.run();
        }
    }
}
