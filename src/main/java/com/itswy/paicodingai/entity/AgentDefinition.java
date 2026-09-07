package com.itswy.paicodingai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 定义(DB 驱动):prompt_key + 声明工具 + 绑定 MCP server + 模型用途。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_agent")
public class AgentDefinition {

    @TableId(type = IdType.INPUT)
    private String agentCode;

    private String agentName;
    private String agentType;
    private String promptKey;
    private String modelUsage;
    /** JSON 数组:agent 声明可用工具(逻辑名),与角色白名单取交集。 */
    private String tools;
    /** JSON 数组:绑定 MCP server 名。 */
    private String mcpServers;
    private Integer enabled;
    private LocalDateTime updatedAt;
}
