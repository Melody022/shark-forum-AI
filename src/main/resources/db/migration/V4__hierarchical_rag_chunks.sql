-- 统一 ContentBlock 后的父子 Chunk 元数据
ALTER TABLE knowledge_chunk
    ADD COLUMN chunk_role VARCHAR(16) NOT NULL DEFAULT 'CHILD' COMMENT 'Chunk角色：PARENT/CHILD',
    ADD COLUMN parent_id BIGINT NULL COMMENT '父Chunk ID',
    ADD COLUMN parent_key VARCHAR(160) NULL COMMENT '解析阶段的父块标识',
    ADD COLUMN source_block_id VARCHAR(160) NULL COMMENT '来源ContentBlock ID',
    ADD COLUMN block_type VARCHAR(32) NULL COMMENT '内容块类型',
    ADD COLUMN page_start INT NULL COMMENT '起始页码',
    ADD COLUMN page_end INT NULL COMMENT '结束页码',
    ADD COLUMN section_path VARCHAR(1000) NULL COMMENT '章节路径',
    ADD COLUMN metadata_json TEXT NULL COMMENT '结构化来源元数据JSON',
    ADD COLUMN searchable TINYINT NOT NULL DEFAULT 1 COMMENT '是否进入检索索引';

ALTER TABLE knowledge_chunk
    ADD INDEX idx_chunk_parent (parent_id),
    ADD INDEX idx_chunk_role (chunk_role),
    ADD INDEX idx_chunk_searchable (searchable);

-- 兼容已经存在的旧 Chunk：它们仍可作为普通可检索子块使用。
UPDATE knowledge_chunk
SET chunk_role = 'CHILD', searchable = 1
WHERE chunk_role IS NULL OR chunk_role = '';
