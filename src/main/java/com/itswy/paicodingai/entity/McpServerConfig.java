package com.itswy.paicodingai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP server 定义(DB 驱动,含远程 http/sse)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_mcp_server")
public class McpServerConfig {

    @TableId(type = IdType.INPUT)
    private String serverName;

    private String transport;
    private String url;
    private String command;
    private String args;
    private String headers;
    /** JSON 数组:该 server 声明的本地工具(逻辑名,经 ToolRegistry 解析);远程工具解析后续接入。 */
    private String tools;
    private Integer enabled;
}
