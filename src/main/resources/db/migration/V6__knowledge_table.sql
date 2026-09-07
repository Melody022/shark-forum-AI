-- 表格元数据表
CREATE TABLE IF NOT EXISTS knowledge_table (
    id VARCHAR(64) PRIMARY KEY COMMENT '表格ID',
    document_id VARCHAR(64) NOT NULL COMMENT '所属文档ID',
    knowledge_base_id VARCHAR(64) NOT NULL COMMENT '所属知识库ID',
    title VARCHAR(512) COMMENT '表格标题',
    page_start INT COMMENT '起始页码',
    page_end INT COMMENT '结束页码',
    section_path VARCHAR(512) COMMENT '章节路径',
    summary TEXT COMMENT '表格摘要',
    headers_json TEXT COMMENT '表头JSON',
    row_count INT COMMENT '行数',
    column_count INT COMMENT '列数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_doc_id (document_id),
    INDEX idx_kb_id (knowledge_base_id),
    INDEX idx_page (page_start, page_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表格元数据';

-- 表格行数据表（EAV 格式）
CREATE TABLE IF NOT EXISTS knowledge_table_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    table_id VARCHAR(64) NOT NULL COMMENT '表格ID',
    row_index INT NOT NULL COMMENT '行索引',
    field_name VARCHAR(128) NOT NULL COMMENT '字段名（列名）',
    field_value TEXT COMMENT '字段值',
    normalized_value VARCHAR(512) COMMENT '归一化值（用于查询）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_table_id (table_id),
    INDEX idx_table_row (table_id, row_index),
    INDEX idx_field_name (table_id, field_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表格行数据';
