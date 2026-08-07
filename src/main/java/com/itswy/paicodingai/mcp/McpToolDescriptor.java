package com.itswy.paicodingai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP工具描述符
 *
 * 描述一个MCP工具的元数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDescriptor {

    /** 服务器名称 */
    private String serverName;

    /** 工具名称 */
    private String name;

    /** 命名空间化工具名（server-tool） */
    private String namespacedName;

    /** 工具描述 */
    private String description;

    /** 输入Schema */
    private JsonNode inputSchema;

    /**
     * 生成命名空间化工具名
     */
    public static String namespaced(String serverName, String toolName) {
        return serverName + "-" + toolName;
    }
}
