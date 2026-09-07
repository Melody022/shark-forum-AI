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

-- 注意:不再插入硬编码 provider 种子(曾写死失效域名且 active=1;DB 优先级高于 env,
-- 重建库时会用旧地址覆盖 .env 正确值导致 401)。Provider 由 ModelProviderService 先取环境变量默认,
-- 管理员在后台保存后才落库(model_provider_config 表)。
