-- 文件上传模块数据库表

-- 文件主表
CREATE TABLE IF NOT EXISTS file_upload (
    file_md5 VARCHAR(32) PRIMARY KEY COMMENT '文件的MD5值，作为主键唯一标识文件',
    file_name VARCHAR(255) NOT NULL COMMENT '文件的原始名称',
    total_size BIGINT NOT NULL COMMENT '文件总大小(字节)',
    status INT NOT NULL DEFAULT 0 COMMENT '文件上传状态：0-上传中，1-合并中，2-已完成，3-解析中，4-解析完成，5-失败',
    user_id VARCHAR(64) NOT NULL COMMENT '上传用户的标识符',
    chunk_size INT COMMENT '分片大小(字节)',
    total_chunks INT COMMENT '总分片数量',
    storage_path VARCHAR(500) COMMENT '合并后的文件存储路径',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '文件上传创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    merged_at TIMESTAMP NULL COMMENT '文件合并完成时间',
    parsed_at TIMESTAMP NULL COMMENT '文档解析完成时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传记录表';

-- 分片表
CREATE TABLE IF NOT EXISTS chunk_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分块记录唯一标识',
    file_md5 VARCHAR(32) NOT NULL COMMENT '关联的文件MD5值',
    chunk_index INT NOT NULL COMMENT '分块序号',
    chunk_size INT NOT NULL COMMENT '分块大小(字节)',
    chunk_md5 VARCHAR(32) NOT NULL COMMENT '分块的MD5值',
    storage_path VARCHAR(500) NOT NULL COMMENT '分块在存储系统中的路径',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_file_chunk (file_md5, chunk_index),
    FOREIGN KEY (file_md5) REFERENCES file_upload(file_md5) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分块信息表';

-- 索引
CREATE INDEX idx_file_upload_user ON file_upload(user_id);
CREATE INDEX idx_file_upload_status ON file_upload(status);
CREATE INDEX idx_chunk_info_file ON chunk_info(file_md5);
