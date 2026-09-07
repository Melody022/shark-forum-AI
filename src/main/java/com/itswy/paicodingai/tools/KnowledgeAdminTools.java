package com.itswy.paicodingai.tools;

import com.itswy.paicodingai.agent.AgentFactory;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;
import com.itswy.paicodingai.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 管理员工具组(仅 ADMIN 角色通过白名单获得,用于演示"某角色能否调用某工具")。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAdminTools {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 列出当前用户知识库文档(供管理员在聊天里概览知识库)。
     */
    @Tool(description = "列出当前用户知识库中的文档列表(仅管理员可用),返回文档ID、标题、状态与分块数")
    public String listKnowledgeDocuments(ToolContext toolContext) {
        String role = toolContext == null ? null : (String) toolContext.getContext().get("role");
        if (!"ADMIN".equals(role)) {
            return "无权限:该操作仅管理员可用。";
        }
        String userId = toolContext == null ? null : (String) toolContext.getContext().get("userId");
        if (userId == null || userId.isBlank()) {
            userId = "0";
        }
        try {
            List<KnowledgeDocument> docs = knowledgeBaseService.getUserDocuments(userId);
            if (docs == null || docs.isEmpty()) {
                return "当前用户暂无知识库文档。";
            }
            StringBuilder sb = new StringBuilder("当前知识库文档列表:\n");
            for (KnowledgeDocument d : docs) {
                String status = switch (d.getStatus() == null ? -1 : d.getStatus()) {
                    case KnowledgeDocument.STATUS_PARSING -> "解析中";
                    case KnowledgeDocument.STATUS_COMPLETED -> "已完成";
                    case KnowledgeDocument.STATUS_FAILED -> "失败";
                    default -> "未知";
                };
                sb.append(String.format("- docId=%d, 标题=%s, 类型=%s, 分块=%d, 状态=%s%n",
                        d.getId(), d.getFileName(), d.getFileType(),
                        d.getChunkCount() == null ? 0 : d.getChunkCount(), status));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("列出知识库文档失败", e);
            return "查询失败:" + e.getMessage();
        }
    }
}
