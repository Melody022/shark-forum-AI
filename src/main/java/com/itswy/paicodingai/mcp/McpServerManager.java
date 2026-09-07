package com.itswy.paicodingai.mcp;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.entity.AgentDefinition;
import com.itswy.paicodingai.entity.McpServerConfig;
import com.itswy.paicodingai.mapper.AgentDefinitionMapper;
import com.itswy.paicodingai.mapper.McpServerConfigMapper;
import com.itswy.paicodingai.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP server 管理器(DB 驱动)。
 *
 * <p>从 ai_mcp_server 读启用配置,并解析某 agent 声明的 mcp_servers 列表。
 * 本地工具类型(transport=local 或 tools 里声明的名字能在 ToolRegistry 找到)直接还原为 @Tool Bean
 * 挂给模型;远程 stdio/http/sse 连接需要 Spring AI 2.0 MCP client,见风险说明(占位,后续接入)。</p>
 */
@Slf4j
@Component
public class McpServerManager {

    private final AgentDefinitionMapper agentMapper;
    private final McpServerConfigMapper mcpServerMapper;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpServerManager(AgentDefinitionMapper agentMapper,
                            McpServerConfigMapper mcpServerMapper,
                            ToolRegistry toolRegistry) {
        this.agentMapper = agentMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.toolRegistry = toolRegistry;
    }

    /** 解析某 agent 声明 MCP server 中可用的「本地工具」实例(远程未接入时返回空并告警)。 */
    public List<Object> resolveDeclaredLocalTools(String agentCode) {
        List<Object> beans = new ArrayList<>();
        for (String serverName : agentMcpServerNames(agentCode)) {
            McpServerConfig server = getServer(serverName);
            if (server == null || server.getEnabled() == null || server.getEnabled() != 1) {
                continue;
            }
            boolean remote = server.getTransport() != null
                    && (server.getTransport().equalsIgnoreCase("http")
                    || server.getTransport().equalsIgnoreCase("sse")
                    || server.getTransport().equalsIgnoreCase("stdio"));
            if (remote) {
                log.warn("远程 MCP server 暂未接入(占位): {} transport={}", serverName, server.getTransport());
                continue;
            }
            beans.addAll(toolRegistry.resolve(parseNames(server.getTools())));
        }
        return beans;
    }

    private Set<String> agentMcpServerNames(String agentCode) {
        Set<String> set = new LinkedHashSet<>();
        if (agentCode == null || agentCode.isBlank()) {
            return set;
        }
        try {
            AgentDefinition def = agentMapper.selectOne(Wrappers.<AgentDefinition>lambdaQuery()
                    .eq(AgentDefinition::getAgentCode, agentCode)
                    .eq(AgentDefinition::getEnabled, 1));
            if (def != null) {
                set.addAll(parseNames(def.getMcpServers()));
            }
        } catch (Exception e) {
            log.warn("读取 ai_agent.mcp_servers 失败: {}", e.getMessage());
        }
        return set;
    }

    private McpServerConfig getServer(String serverName) {
        try {
            return mcpServerMapper.selectOne(Wrappers.<McpServerConfig>lambdaQuery()
                    .eq(McpServerConfig::getServerName, serverName));
        } catch (Exception e) {
            log.warn("读取 ai_mcp_server 失败: {} - {}", serverName, e.getMessage());
            return null;
        }
    }

    private Set<String> parseNames(String json) {
        Set<String> set = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return set;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                node.forEach(item -> set.add(item.asText()));
            }
        } catch (Exception e) {
            log.warn("解析 MCP tools JSON 失败: {}", json, e);
        }
        return set;
    }
}
