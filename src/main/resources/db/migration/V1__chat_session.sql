-- ============================================================
-- V1: 基础会话表(chat_session / chat_record)
-- 原散落在 sql/init.sql / merge_to_pai_coding.sql,归并进自动迁移
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id  VARCHAR(64)  NOT NULL                 COMMENT '会话ID（UUID，给前端用）',
    user_id     BIGINT       NOT NULL DEFAULT 0       COMMENT '用户ID',
    title       VARCHAR(200) DEFAULT NULL             COMMENT '会话标题（AI自动生成）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id) USING BTREE,
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

CREATE TABLE IF NOT EXISTS chat_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id VARCHAR(128) NOT NULL                 COMMENT '对话ID（格式：userId_sessionId）',
    data            TEXT         NOT NULL                 COMMENT '消息数据（JSON格式的Message对象）',
    type            TINYINT      DEFAULT NULL             COMMENT '消息类型：1-用户，2-AI',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id) USING BTREE,
    KEY idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';
