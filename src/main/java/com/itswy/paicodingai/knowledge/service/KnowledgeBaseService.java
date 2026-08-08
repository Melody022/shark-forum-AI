package com.itswy.paicodingai.knowledge.service;

import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;
import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeBaseService {

    /**
     * 创建用户知识库
     */
    KnowledgeBase createUserKnowledgeBase(String userId, String name, String description);

    /**
     * 获取用户的所有知识库
     */
    List<KnowledgeBase> getUserKnowledgeBases(String userId);

    /**
     * 获取系统知识库
     */
    KnowledgeBase getSystemKnowledgeBase();

    /**
     * 获取用户可访问的所有知识库（用户知识库 + 系统知识库）
     */
    List<KnowledgeBase> getAccessibleKnowledgeBases(String userId);

    /**
     * 上传文档到知识库
     */
    KnowledgeDocument uploadDocument(Long kbId, String fileMd5, String fileName,
                                      String fileType, Long totalSize, String userId);

    /**
     * 获取知识库的所有文档
     */
    List<KnowledgeDocument> getKnowledgeBaseDocuments(Long kbId, String userId);

    /**
     * 获取用户的所有文档
     */
    List<KnowledgeDocument> getUserDocuments(String userId);

    /**
     * 搜索知识库（支持多用户隔离）
     */
    List<KnowledgeChunk> searchKnowledgeBase(String query, String userId, int topK);

    /**
     * 搜索指定知识库
     */
    List<KnowledgeChunk> searchInKnowledgeBase(Long kbId, String query, int topK);

    /**
     * 删除文档
     */
    boolean deleteDocument(Long docId, String userId);

    /**
     * 检查用户是否有权限访问知识库
     */
    boolean hasAccess(Long kbId, String userId);
}
