package com.itswy.paicodingai.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.mcp")
public class McpProperties {

    /** 是否启用MCP */
    private boolean enabled = false;

    /** MCP服务器配置 */
    private Map<String, ServerConfig> servers = new HashMap<>();

    @Data
    public static class ServerConfig {
        /** 服务器类型：stdio, http, local */
        private String type = "stdio";

        /** 服务器命令（stdio类型） */
        private String command;

        /** 服务器URL（http类型） */
        private String url;

        /** API密钥（http类型） */
        private String apiKey;

        /** 工具列表（local类型） */
        private List<String> tools;

        /** 是否禁用 */
        private boolean disabled = false;
    }
}
