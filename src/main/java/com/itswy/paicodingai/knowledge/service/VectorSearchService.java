package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * 向量搜索服务接口
 */
public interface VectorSearchService {

    /**
     * 在知识库中搜索
     *
     * @param query 查询文本
     * @param userId 用户ID
     * @param topK 返回数量
     * @return 搜索结果
     */
    List<SearchResult> search(String query, String userId, int topK);

    /**
     * 在指定知识库中搜索
     *
     * @param query 查询文本
     * @param kbId 知识库ID
     * @param topK 返回数量
     * @return 搜索结果
     */
    List<SearchResult> searchInKnowledgeBase(String query, Long kbId, int topK);
}

