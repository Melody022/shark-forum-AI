package com.itswy.paicodingai.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.entity.AgentDefinition;
import com.itswy.paicodingai.entity.SysRoleTool;
import com.itswy.paicodingai.mapper.AgentDefinitionMapper;
import com.itswy.paicodingai.mapper.SysRoleToolMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 工厂:根据 DB 里的 ai_agent 定义(MAIN)计算一次组装所需的提示词 key 与可用工具集。
 *
 * <p>可用工具 = Agent 声明工具 ∩ 该角色白名单(sys_role_tool);白名单未配置(表未就绪)时
 * 降级为「全部使用 Agent 声明」,避免把老链路锁死。</p>
 */
@Slf4j
@Component
public class AgentFactory {

    public static final String DEFAULT_AGENT_CODE = "paicoding";
    public static final String DEFAULT_ROLE = "USER";

    private final AgentDefinitionMapper agentDefinitionMapper;
    private final SysRoleToolMapper sysRoleToolMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentFactory(AgentDefinitionMapper agentDefinitionMapper, SysRoleToolMapper sysRoleToolMapper) {
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.sysRoleToolMapper = sysRoleToolMapper;
    }

    /** 取启用的 Agent 定义;无则返回 null(调用方用默认主 Agent 兜底)。 */
    public AgentDefinition getAgent(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return null;
        }
        try {
            return agentDefinitionMapper.selectOne(Wrappers.<AgentDefinition>lambdaQuery()
                    .eq(AgentDefinition::getAgentCode, agentCode)
                    .eq(AgentDefinition::getEnabled, 1));
        } catch (Exception e) {
            log.warn("读取 ai_agent 失败(表未就绪?): code={} - {}", agentCode, e.getMessage());
            return null;
        }
    }

    /** 计算某角色可用工具集(∩ 白名单)。 */
    public List<String> allowedToolNames(String agentCode, String roleCode) {
        AgentDefinition def = getAgent(agentCode);
        if (def == null) {
            return List.of();
        }
        Set<String> declared = parseNames(def.getTools());
        if (declared.isEmpty()) {
            return List.of();
        }
        Set<String> whitelist = roleWhitelist(roleCode);
        if (whitelist.isEmpty()) {
            // 白名单未就绪:降级放行 Agent 声明工具,保持可用
            return new ArrayList<>(declared);
        }
        List<String> result = new ArrayList<>();
        for (String name : declared) {
            if (whitelist.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private Set<String> roleWhitelist(String roleCode) {
        String role = (roleCode == null || roleCode.isBlank()) ? DEFAULT_ROLE : roleCode;
        Set<String> set = new LinkedHashSet<>();
        try {
            for (SysRoleTool row : sysRoleToolMapper.selectList(Wrappers.<SysRoleTool>lambdaQuery()
                    .eq(SysRoleTool::getRoleCode, role))) {
                set.add(row.getToolName());
            }
        } catch (Exception e) {
            log.warn("读取角色工具白名单失败(表未就绪?): role={} - {}", role, e.getMessage());
        }
        return set;
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
            log.warn("解析 Agent tools JSON 失败: {}", json, e);
        }
        return set;
    }
}
