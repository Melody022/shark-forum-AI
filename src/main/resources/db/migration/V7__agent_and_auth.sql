-- ============================================================
-- V7: 认证/权限 + Agent/MCP 定义表(幂等,可重复执行)
-- 目标库:pai_coding(实际连库)。由 DbSchemaInitializer 启动时自动执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_role (
  role_code   VARCHAR(32) PRIMARY KEY,
  role_name   VARCHAR(64)  NOT NULL,
  description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  nickname      VARCHAR(64),
  role_code     VARCHAR(32)  NOT NULL DEFAULT 'USER',
  enabled       TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME,
  updated_at    DATETIME,
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE IF NOT EXISTS sys_tool (
  tool_name  VARCHAR(64) PRIMARY KEY,
  tool_desc  VARCHAR(255),
  tool_owner VARCHAR(16)  NOT NULL DEFAULT 'USER',
  risk_level VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
  built_in   TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具注册表';

CREATE TABLE IF NOT EXISTS sys_role_tool (
  role_code VARCHAR(32) NOT NULL,
  tool_name VARCHAR(64) NOT NULL,
  PRIMARY KEY (role_code, tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-工具白名单';

CREATE TABLE IF NOT EXISTS ai_agent (
  agent_code   VARCHAR(64) PRIMARY KEY,
  agent_name   VARCHAR(128) NOT NULL,
  agent_type   VARCHAR(16)  NOT NULL DEFAULT 'MAIN',
  prompt_key   VARCHAR(128),
  model_usage  VARCHAR(32)  NOT NULL DEFAULT 'chat',
  tools        TEXT COMMENT 'JSON数组:agent声明可用工具(逻辑名),与角色白名单取交集',
  mcp_servers  TEXT COMMENT 'JSON数组:绑定MCP server名',
  enabled      TINYINT      NOT NULL DEFAULT 1,
  updated_at   DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent定义(DB驱动)';

CREATE TABLE IF NOT EXISTS ai_mcp_server (
  server_name VARCHAR(64) PRIMARY KEY,
  transport   VARCHAR(16)  NOT NULL DEFAULT 'stdio',
  url         VARCHAR(512),
  command     VARCHAR(512),
  args        VARCHAR(512),
  headers     TEXT,
  tools       TEXT COMMENT 'JSON数组:该server声明的本地工具(逻辑名)',
  enabled     TINYINT      NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP server 定义(DB驱动)';

-- 种子(幂等)
INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES
  ('ADMIN', '管理员', '可管理知识库、配置与后台'),
  ('USER', '普通用户', '只读问答/查文章/查教程/转人工');

INSERT IGNORE INTO sys_tool (tool_name, tool_desc, tool_owner, risk_level, built_in) VALUES
  ('article',        '文章查询工具组(详情/列表/搜索/热门)', 'USER',  'NORMAL', 1),
  ('course',         '教程课程工具组(详情/列表/搜索/推荐)', 'USER',  'NORMAL', 1),
  ('load_skill',     '按需加载 Skill 指南',               'USER',  'NORMAL', 1),
  ('knowledge_docs', '(管理员)列出知识库文档',             'ADMIN', 'NORMAL', 1);

INSERT IGNORE INTO sys_role_tool (role_code, tool_name) VALUES
  ('USER',  'article'),
  ('USER',  'course'),
  ('USER',  'load_skill'),
  ('ADMIN', 'article'),
  ('ADMIN', 'course'),
  ('ADMIN', 'load_skill'),
  ('ADMIN', 'knowledge_docs');

INSERT IGNORE INTO ai_agent (agent_code, agent_name, agent_type, prompt_key, model_usage, tools, mcp_servers, enabled, updated_at) VALUES
  ('paicoding', '技术派AI客服主Agent', 'MAIN', 'agent.general', 'chat',
   '["article","course","load_skill","knowledge_docs"]', '[]', 1, NOW());
