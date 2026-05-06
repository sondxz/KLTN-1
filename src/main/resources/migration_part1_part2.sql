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
