package com.web.service;

import com.web.entity.ChunkEmbedding;
import com.web.repository.ChunkEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service tách riêng cho các DB operation có @Transactional.
 * <p>
 * QUAN TRỌNG: Spring @Transactional chỉ hoạt động khi gọi qua proxy (từ bean khác).
 * Nếu gọi từ cùng class (self-invocation) → @Transactional bị bỏ qua.
 * Vì vậy tách ra service riêng để EmbeddingService gọi qua Spring proxy.
 */
@Service
public class ChunkPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChunkPersistenceService.class);

    @Autowired
    private ChunkEmbeddingRepository chunkEmbeddingRepository;

    /**
     * Xóa chunks theo type — transaction ngắn, chỉ làm DB operation.
     */
    @Transactional
    public void deleteByType(ChunkEmbedding.ContentType contentType) {
        chunkEmbeddingRepository.deleteByContentType(contentType);
        log.debug("Đã xóa chunks type={}", contentType);
    }

    /**
     * Xóa chunks theo type + entity — transaction ngắn.
     */
    @Transactional
    public void deleteByTypeAndEntity(ChunkEmbedding.ContentType contentType, Long entityId) {
        chunkEmbeddingRepository.deleteByContentTypeAndEntityId(contentType, entityId);
        log.debug("Đã xóa chunks type={}, entityId={}", contentType, entityId);
    }

    /**
     * Lưu batch chunks vào DB — transaction ngắn, chỉ làm DB operation.
     * <p>
     * QUAN TRỌNG: Method này KHÔNG gọi HTTP API.
     * Embedding đã được lấy trước đó (ngoài transaction).
     */
    @Transactional
    public void saveBatch(List<ChunkEmbedding> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        chunkEmbeddingRepository.saveAll(chunks);
        log.debug("Đã lưu {} chunks vào DB", chunks.size());
    }

    @Transactional
    public void replaceAll(Map<ChunkEmbedding.ContentType, List<ChunkEmbedding>> chunksByType) {
        for (ChunkEmbedding.ContentType contentType : chunksByType.keySet()) {
            chunkEmbeddingRepository.deleteByContentType(contentType);
        }
        for (List<ChunkEmbedding> chunks : chunksByType.values()) {
            if (chunks != null && !chunks.isEmpty()) {
                chunkEmbeddingRepository.saveAll(chunks);
            }
        }
        chunkEmbeddingRepository.flush();
    }

    @Transactional
    public void replaceEntity(ChunkEmbedding.ContentType contentType, Long entityId,
                              List<ChunkEmbedding> chunks) {
        chunkEmbeddingRepository.deleteByContentTypeAndEntityId(contentType, entityId);
        if (chunks != null && !chunks.isEmpty()) {
            chunkEmbeddingRepository.saveAll(chunks);
        }
        chunkEmbeddingRepository.flush();
    }
}
