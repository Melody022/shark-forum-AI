-- 运行时 LLM Provider 配置。API Key 只保存密文。
CREATE TABLE IF NOT EXISTS model_provider_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    config_scope VARCHAR(32) NOT NULL COMMENT '作用域：llm',
    provider_code VARCHAR(64) NOT NULL COMMENT 'Provider标识',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
    api_style VARCHAR(64) NOT NULL DEFAULT 'openai-compatible' COMMENT '协议类型',
    api_base_url VARCHAR(512) NOT NULL COMMENT 'OpenAI兼容根地址',
    model_name VARCHAR(255) NOT NULL COMMENT '模型名称',
    api_key_ciphertext VARCHAR(2048) NULL COMMENT 'AES-GCM加密后的API Key',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    active TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前激活',
    updated_by VARCHAR(128) NULL COMMENT '最后更新人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_provider_scope_code (config_scope, provider_code),
    KEY idx_model_provider_scope (config_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时模型 Provider 配置';

INSERT INTO model_provider_config
    (config_scope, provider_code, display_name, api_style, api_base_url, model_name, enabled, active, updated_by)
VALUES
    ('llm', 'mimo', '小米 MiMo', 'openai-compatible', 'https://api.xiaomimimo.com/v1', 'mimo-v2.5', 1, 1, 'migration'),
    ('llm', 'deepseek', 'DeepSeek', 'openai-compatible', 'https://api.deepseek.com/v1', 'deepseek-chat', 1, 0, 'migration')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    api_style = VALUES(api_style),
    updated_at = CURRENT_TIMESTAMP;
