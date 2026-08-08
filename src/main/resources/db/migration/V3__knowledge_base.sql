-- 知识库模块数据库表

-- 知识库主表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识库ID',
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description TEXT COMMENT '知识库描述',
    type TINYINT NOT NULL DEFAULT 0 COMMENT '类型：0-用户知识库，1-系统知识库',
    user_id VARCHAR(64) COMMENT '用户ID（系统知识库为NULL）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
    kb_id BIGINT NOT NULL COMMENT '知识库ID',
    file_md5 VARCHAR(32) NOT NULL COMMENT '文件MD5',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_type VARCHAR(20) NOT NULL COMMENT '文件类型',
    total_size BIGINT NOT NULL COMMENT '文件大小',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-解析中，1-已完成，2-失败',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    parsed_at TIMESTAMP NULL COMMENT '解析完成时间',
    INDEX idx_kb (kb_id),
    INDEX idx_user (user_id),
    INDEX idx_file_md5 (file_md5),
    FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- 知识库文档分块表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分块ID',
    doc_id BIGINT NOT NULL COMMENT '文档ID',
    chunk_index INT NOT NULL COMMENT '分块序号',
    content TEXT NOT NULL COMMENT '文本内容',
    token_count INT COMMENT 'Token数量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_doc (doc_id),
    FOREIGN KEY (doc_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分块表';
