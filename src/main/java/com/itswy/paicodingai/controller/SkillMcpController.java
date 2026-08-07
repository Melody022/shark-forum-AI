package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.mcp.McpClient;
import com.itswy.paicodingai.mcp.McpToolDescriptor;
import com.itswy.paicodingai.skill.Skill;
import com.itswy.paicodingai.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Skill和MCP管理接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SkillMcpController {

    private final SkillRegistry skillRegistry;
    private final McpClient mcpClient;

    /**
     * 获取所有Skills
     */
    @GetMapping("/skills")
    public List<Skill> getSkills() {
        return skillRegistry.findAll();
    }

    /**
     * 获取所有MCP工具
     */
    @GetMapping("/mcp/tools")
    public List<McpToolDescriptor> getMcpTools() {
        return mcpClient.getDiscoveredTools();
    }

    /**
     * 获取系统信息（Skills + MCP工具）
     */
    @GetMapping("/system/info")
    public Map<String, Object> getSystemInfo() {
        return Map.of(
                "skills", skillRegistry.findAll(),
                "mcpTools", mcpClient.getDiscoveredTools(),
                "warnings", skillRegistry.getWarnings()
        );
    }
}
