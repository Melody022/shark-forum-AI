-- ============================================================
-- paicoding-ai 数据库初始化脚本
-- 第一阶段：基本对话与课程咨询
-- ============================================================
-- 使用前先创建数据库：
   CREATE DATABASE IF NOT EXISTS paicoding_ai
   DEFAULT CHARSET utf8mb4
   DEFAULT COLLATE utf8mb4_unicode_ci;
-- ============================================================

-- 1. 会话表 —— 一次完整的对话（一个聊天窗口）
-- 每次打开聊天窗口创建一个 session
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `session_id`  VARCHAR(64)  NOT NULL                 COMMENT '会话ID（UUID，给前端用）',
    `user_id`     BIGINT       NOT NULL DEFAULT 0       COMMENT '用户ID（从paicoding传过来）',
    `title`       VARCHAR(200) DEFAULT NULL             COMMENT '会话标题（AI自动生成）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 2. 聊天记录表 —— 存储对话历史
-- 每一条用户消息或AI回复就是一条记录
CREATE TABLE IF NOT EXISTS `chat_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `conversation_id` VARCHAR(128) NOT NULL                 COMMENT '对话ID（格式：userId_sessionId）',
    `data`            TEXT         NOT NULL                 COMMENT '消息数据（JSON格式的Message对象）',
    `type`            TINYINT     DEFAULT NULL              COMMENT '消息类型：1-用户，2-AI',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';
