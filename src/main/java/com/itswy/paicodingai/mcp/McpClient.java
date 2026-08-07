package com.itswy.paicodingai.mcp;

import com.itswy.paicodingai.mcp.config.McpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP客户端 - 发现和调用MCP工具
 *
 * 基于Spring AI 2.0的MCP支持
 */
@Slf4j
@Component
public class McpClient {

    /** 已发现的工具 */
    private final Map<String, McpToolDescriptor> discoveredTools = new ConcurrentHashMap<>();

    @Autowired
    private McpProperties mcpProperties;

    @PostConstruct
    public void init() {
        if (mcpProperties.isEnabled()) {
            discoverToolsFromConfig();
            log.info("MCP客户端初始化完成，已加载 {} 个工具", discoveredTools.size());
        } else {
            log.info("MCP客户端未启用");
        }
    }

    /**
     * 从配置文件加载工具
     */
    private void discoverToolsFromConfig() {
        Map<String, McpProperties.ServerConfig> servers = mcpProperties.getServers();
        if (servers == null || servers.isEmpty()) {
            return;
        }

        for (Map.Entry<String, McpProperties.ServerConfig> entry : servers.entrySet()) {
            String serverName = entry.getKey();
            McpProperties.ServerConfig config = entry.getValue();

            if (config.isDisabled()) {
                log.debug("MCP服务器已禁用: {}", serverName);
                continue;
            }

            // 根据配置创建工具描述符
            if (config.getTools() != null) {
                for (String toolName : config.getTools()) {
                    McpToolDescriptor descriptor = new McpToolDescriptor();
                    descriptor.setServerName(serverName);
                    descriptor.setName(toolName);
                    descriptor.setNamespacedName(McpToolDescriptor.namespaced(serverName, toolName));
                    descriptor.setDescription("MCP工具: " + toolName);

                    discoveredTools.put(toolName, descriptor);
                    log.debug("注册MCP工具: {} from server {}", toolName, serverName);
                }
            }
        }
    }

    /**
     * 注册工具
     */
    public void registerTool(McpToolDescriptor descriptor) {
        discoveredTools.put(descriptor.getName(), descriptor);
        log.debug("注册MCP工具: {} - {}", descriptor.getName(), descriptor.getDescription());
    }

    /**
     * 获取所有发现的工具
     */
    public List<McpToolDescriptor> getDiscoveredTools() {
        return new ArrayList<>(discoveredTools.values());
    }

    /**
     * 根据名称获取工具
     */
    public McpToolDescriptor getTool(String toolName) {
        return discoveredTools.get(toolName);
    }

    /**
     * 调用MCP工具
     */
    public String invokeTool(String toolName, Map<String, Object> params) {
        McpToolDescriptor tool = discoveredTools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("未找到MCP工具: " + toolName);
        }

        // TODO: 实现真正的工具调用逻辑
        log.info("调用MCP工具: {}, 参数: {}", toolName, params);
        return "工具调用结果: " + toolName;
    }
}
