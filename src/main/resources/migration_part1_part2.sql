-- ========================================
-- MIGRATION PHẦN 1: Bảng chunk_embeddings cho RAG
-- ========================================

CREATE TABLE IF NOT EXISTS chunk_embeddings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  content_type ENUM('plant','article','research','disease','folk_remedy') NOT NULL,
  entity_id BIGINT NOT NULL,
  entity_slug VARCHAR(255),
  entity_name VARCHAR(255),
  chunk_text TEXT NOT NULL,
  embedding JSON,
  metadata JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_content_type (content_type),
  INDEX idx_entity_id (entity_id),
  INDEX idx_entity_slug (entity_slug),
  FULLTEXT INDEX ft_chunk_text (chunk_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ========================================
-- MIGRATION PHẦN 2: Bảng folk_remedies
-- ========================================

CREATE TABLE IF NOT EXISTS folk_remedies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  description TEXT,
  usage_instruction TEXT,
  preparation TEXT,
  contraindication TEXT,
  source VARCHAR(500),
  status ENUM('pending','approved','rejected') DEFAULT 'pending',
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FULLTEXT INDEX ft_folk (name, description),
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS folk_remedy_plants (
  folk_remedy_id BIGINT NOT NULL,
  plant_id BIGINT NOT NULL,
  PRIMARY KEY (folk_remedy_id, plant_id),
  FOREIGN KEY (folk_remedy_id) REFERENCES folk_remedies(id) ON DELETE CASCADE,
  FOREIGN KEY (plant_id) REFERENCES plants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS folk_remedy_diseases (
  folk_remedy_id BIGINT NOT NULL,
  disease_id BIGINT NOT NULL,
  PRIMARY KEY (folk_remedy_id, disease_id),
  FOREIGN KEY (folk_remedy_id) REFERENCES folk_remedies(id) ON DELETE CASCADE,
  FOREIGN KEY (disease_id) REFERENCES diseases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ========================================
-- MIGRATION DỮ LIỆU: Chuyển folk_remedies từ plants sang bảng mới
-- ========================================

INSERT INTO folk_remedies (name, slug, description, source, status, created_at)
SELECT
    CONCAT('Bài thuốc từ ', p.name),
    CONCAT(p.slug, '-bai-thuoc'),
    p.folk_remedies,
    p.source,
    'approved',
    NOW()
FROM plants p
WHERE p.folk_remedies IS NOT NULL AND p.folk_remedies != '';

-- Tạo quan hệ folk_remedy ↔ plant
INSERT INTO folk_remedy_plants (folk_remedy_id, plant_id)
SELECT fr.id, p.id
FROM folk_remedies fr
INNER JOIN plants p ON fr.slug = CONCAT(p.slug, '-bai-thuoc')
WHERE fr.slug LIKE '%-bai-thuoc';


-- ========================================
-- MIGRATION PHẦN 3: Bảng messages cho Chat Realtime
-- ========================================

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  content TEXT,
  file_url VARCHAR(500),
  file_name VARCHAR(255),
  file_type VARCHAR(50),
  message_type VARCHAR(20) NOT NULL DEFAULT 'text',
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sender (sender_id),
  INDEX idx_receiver (receiver_id),
  INDEX idx_created_at (created_at),
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ========================================
-- MIGRATION PHẦN 4: Bổ sung cột cho bảng articles (nếu thiếu)
-- Chạy an toàn: bỏ qua lỗi nếu cột đã tồn tại
-- ========================================

DELIMITER //
CREATE PROCEDURE IF NOT EXISTS add_column_if_missing(
    IN tbl_name VARCHAR(64),
    IN col_name VARCHAR(64),
    IN col_def VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
          AND TABLE_NAME = tbl_name 
          AND COLUMN_NAME = col_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl_name, ' ADD COLUMN ', col_name, ' ', col_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_missing('articles', 'article_status', 'VARCHAR(20) DEFAULT ''CHO_DUYET''');
CALL add_column_if_missing('articles', 'published_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('articles', 'view_count', 'BIGINT DEFAULT 0');
CALL add_column_if_missing('articles', 'is_featured', 'TINYINT(1) DEFAULT 0');
CALL add_column_if_missing('articles', 'allow_comments', 'TINYINT(1) DEFAULT 1');
CALL add_column_if_missing('articles', 'user_id', 'BIGINT');
CALL add_column_if_missing('articles', 'diseases_id', 'BIGINT');

DROP PROCEDURE IF EXISTS add_column_if_missing;
