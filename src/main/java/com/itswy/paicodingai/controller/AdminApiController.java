package com.itswy.paicodingai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.config.AuthContext;
import com.itswy.paicodingai.entity.AgentDefinition;
import com.itswy.paicodingai.entity.McpServerConfig;
import com.itswy.paicodingai.entity.PromptConfig;
import com.itswy.paicodingai.entity.SysRoleTool;
import com.itswy.paicodingai.entity.SysTool;
import com.itswy.paicodingai.entity.SysUser;
import com.itswy.paicodingai.mapper.AgentDefinitionMapper;
import com.itswy.paicodingai.mapper.McpServerConfigMapper;
import com.itswy.paicodingai.mapper.SysRoleToolMapper;
import com.itswy.paicodingai.mapper.SysToolMapper;
import com.itswy.paicodingai.mapper.SysUserMapper;
import com.itswy.paicodingai.service.AuthService;
import com.itswy.paicodingai.service.HumanQueueService;
import com.itswy.paicodingai.service.prompt.PromptStoreService;
import com.itswy.paicodingai.skill.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 精简配置页后端(Agent / Prompt / 工具白名单 / MCP / 转人工队列 / 用户)。
 * /admin 下全部需 ADMIN(由 AuthInterceptor 保证)。
 */
@Slf4j
@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentDefinitionMapper agentMapper;
    private final PromptStoreService promptStore;
    private final SkillRegistry skillRegistry;
    private final SysToolMapper sysToolMapper;
    private final SysRoleToolMapper sysRoleToolMapper;
    private final McpServerConfigMapper mcpServerMapper;
    private final HumanQueueService humanQueueService;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminApiController(AgentDefinitionMapper agentMapper,
                              PromptStoreService promptStore,
                              SkillRegistry skillRegistry,
                              SysToolMapper sysToolMapper,
                              SysRoleToolMapper sysRoleToolMapper,
                              McpServerConfigMapper mcpServerMapper,
                              HumanQueueService humanQueueService,
                              SysUserMapper userMapper,
                              PasswordEncoder passwordEncoder) {
        this.agentMapper = agentMapper;
        this.promptStore = promptStore;
        this.skillRegistry = skillRegistry;
        this.sysToolMapper = sysToolMapper;
        this.sysRoleToolMapper = sysRoleToolMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.humanQueueService = humanQueueService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    private Map<String, Object> ok(Object data) {
        return Map.of("code", 200, "data", data);
    }

    private Map<String, Object> err(int code, String message) {
        return Map.of("code", code, "message", message);
    }

    // ---------- Agent ----------
    @GetMapping("/agents")
    public Map<String, Object> agents() {
        try {
            return ok(agentMapper.selectList(null));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    @PutMapping("/agents/{agentCode}")
    public Map<String, Object> updateAgent(@PathVariable String agentCode,
                                           @RequestBody AgentUpdate body) {
        try {
            AgentDefinition def = agentMapper.selectById(agentCode);
            if (def == null) {
                return err(404, "agent 不存在: " + agentCode);
            }
            if (body.agentName() != null) def.setAgentName(body.agentName());
            if (body.promptKey() != null) def.setPromptKey(body.promptKey());
            if (body.tools() != null) def.setTools(objectMapper.writeValueAsString(body.tools()));
            if (body.mcpServers() != null) def.setMcpServers(objectMapper.writeValueAsString(body.mcpServers()));
            if (body.enabled() != null) def.setEnabled(body.enabled() ? 1 : 0);
            def.setUpdatedAt(LocalDateTime.now());
            agentMapper.updateById(def);
            return ok(def);
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // ---------- Prompt ----------
    @GetMapping("/prompts")
    public Map<String, Object> prompts() {
        return ok(promptStore.listAll());
    }

    @GetMapping("/prompts/{key}")
    public Map<String, Object> prompt(@PathVariable String key) {
        PromptConfig config = promptStore.getConfig(key);
        if (config == null) {
            return err(404, "prompt 不存在: " + key);
        }
        return ok(config);
    }

    /** 发布(PromptStoreService 内部 DB version+1 并清 Redis,即热生效)。 */
    @PutMapping("/prompts/{key}")
    public Map<String, Object> publishPrompt(@PathVariable String key, @RequestBody Map<String, String> body) {
        try {
            String content = body.get("content");
            if (content == null || content.isBlank()) {
                return err(400, "content 不能为空");
            }
            int version = promptStore.publish(key, content, AuthContext.currentUserId());
            if (key.startsWith("skill.")) {
                skillRegistry.reload(); // 技能内容变化 → 重载
            }
            return ok(Map.of("key", key, "version", version));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // ---------- 工具白名单矩阵 ----------
    @GetMapping("/tools")
    public Map<String, Object> tools() {
        try {
            List<SysTool> tools = sysToolMapper.selectList(null);
            List<SysRoleTool> grants = sysRoleToolMapper.selectList(null);
            Map<String, Set<String>> roleTools = grants.stream()
                    .collect(Collectors.groupingBy(SysRoleTool::getRoleCode,
                            Collectors.mapping(SysRoleTool::getToolName, Collectors.toCollection(LinkedHashSet::new))));
            return ok(Map.of("tools", tools, "grants", roleTools));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    @PutMapping("/tools")
    public Map<String, Object> updateToolGrant(@RequestBody ToolGrant body) {
        try {
            String role = body.roleCode();
            String tool = body.toolName();
            SysRoleTool existing = sysRoleToolMapper.selectOne(Wrappers.<SysRoleTool>lambdaQuery()
                    .eq(SysRoleTool::getRoleCode, role)
                    .eq(SysRoleTool::getToolName, tool));
            if (Boolean.TRUE.equals(body.allowed()) && existing == null) {
                sysRoleToolMapper.insert(SysRoleTool.builder().roleCode(role).toolName(tool).build());
            } else if (!Boolean.TRUE.equals(body.allowed()) && existing != null) {
                sysRoleToolMapper.delete(Wrappers.<SysRoleTool>lambdaQuery()
                        .eq(SysRoleTool::getRoleCode, role)
                        .eq(SysRoleTool::getToolName, tool));
            }
            return ok("ok");
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // ---------- MCP ----------
    @GetMapping("/mcp-servers")
    public Map<String, Object> mcpServers() {
        try {
            return ok(mcpServerMapper.selectList(null));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    @PutMapping("/mcp-servers")
    public Map<String, Object> upsertMcpServer(@RequestBody McpServerConfig body) {
        try {
            McpServerConfig existing = mcpServerMapper.selectById(body.getServerName());
            if (existing == null) {
                body.setEnabled(body.getEnabled() == null ? 1 : body.getEnabled());
                mcpServerMapper.insert(body);
            } else {
                existing.setTransport(body.getTransport());
                existing.setUrl(body.getUrl());
                existing.setCommand(body.getCommand());
                existing.setArgs(body.getArgs());
                existing.setHeaders(body.getHeaders());
                existing.setTools(body.getTools());
                existing.setEnabled(body.getEnabled());
                mcpServerMapper.updateById(existing);
            }
            return ok("ok");
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // ---------- 转人工队列 ----------
    @GetMapping("/human-queue")
    public Map<String, Object> humanQueue() {
        return ok(humanQueueService.listPending());
    }

    @PostMapping("/human-queue/{sessionId}/accept")
    public Map<String, Object> acceptHuman(@PathVariable String sessionId) {
        humanQueueService.markActive(sessionId);
        return ok("已接入: " + sessionId);
    }

    // ---------- 用户 ----------
    @GetMapping("/users")
    public Map<String, Object> users() {
        try {
            List<Map<String, Object>> list = userMapper.selectList(null).stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("nickname", u.getNickname());
                m.put("roleCode", u.getRoleCode());
                m.put("enabled", u.getEnabled());
                return m;
            }).toList();
            return ok(list);
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            if (username == null || password == null || username.isBlank() || password.isBlank()) {
                return err(400, "username/password 必填");
            }
            Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (count != null && count > 0) {
                return err(400, "用户名已存在");
            }
            SysUser user = SysUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(password))
                    .nickname(body.getOrDefault("nickname", username))
                    .roleCode(body.getOrDefault("roleCode", SysUser.ROLE_USER))
                    .enabled(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            return ok(user);
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return err(404, "用户不存在");
        }
        if (body.containsKey("roleCode")) {
            user.setRoleCode(String.valueOf(body.get("roleCode")));
        }
        if (body.containsKey("enabled")) {
            user.setEnabled(Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0);
        }
        if (body.containsKey("nickname")) {
            user.setNickname(String.valueOf(body.get("nickname")));
        }
        if (body.containsKey("password") && body.get("password") != null) {
            user.setPasswordHash(passwordEncoder.encode(String.valueOf(body.get("password"))));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return ok("ok");
    }

    // ---------- DTO ----------
    public record AgentUpdate(String agentName, String promptKey, List<String> tools,
                              List<String> mcpServers, Boolean enabled) {
    }

    public record ToolGrant(String roleCode, String toolName, Boolean allowed) {
    }
}
