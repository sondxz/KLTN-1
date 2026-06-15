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

    public void syncAfterCommit(ChunkEmbedding.ContentType contentType, Long entityId) {
        if (entityId == null) {
            return;
        }

        Runnable sync = () -> embeddingService.syncEntityAsync(contentType, entityId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
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
