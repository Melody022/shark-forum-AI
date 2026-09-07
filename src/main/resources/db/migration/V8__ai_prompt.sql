-- ============================================================
-- V8: Prompt 定义表(DB 驱动,Redis 缓存版本热更)
-- 目标库:pai_coding。Prompt 正文由 PromptSeeder 从 classpath 种子导入。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_prompt (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_key  VARCHAR(128) NOT NULL,
  prompt_type VARCHAR(16)  NOT NULL DEFAULT 'AGENT',
  name        VARCHAR(128),
  version     INT          NOT NULL DEFAULT 1,
  content     TEXT,
  enabled     TINYINT      NOT NULL DEFAULT 1,
  updated_by  VARCHAR(64),
  updated_at  DATETIME,
  UNIQUE KEY uk_ai_prompt_key (prompt_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 存储(BASE/AGENT/SKILL/CLASSIFIER)';
