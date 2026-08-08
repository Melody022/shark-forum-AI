package com.itswy.paicodingai.knowledge.service;

import java.util.List;
import java.util.Map;

/**
 * 向量存储服务接口
 */
public interface VectorStoreService {

    /**
     * 存储向量
     *
     * @param index 索引名称
     * @param id 文档ID
     * @param vector 向量
     * @param metadata 元数据
     */
    void store(String index, String id, float[] vector, Map<String, Object> metadata);

    /**
     * 批量存储向量
     *
     * @param index 索引名称
     * @param vectors 向量列表（id, vector, metadata）
     */
    void storeBatch(String index, List<VectorData> vectors);

    /**
     * 搜索相似向量
     *
     * @param index 索引名称
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @return 搜索结果（id, score, metadata）
     */
    List<VectorSearchResult> search(String index, float[] queryVector, int topK);

    /**
     * 删除向量
     *
     * @param index 索引名称
     * @param id 文档ID
     */
    void delete(String index, String id);

    /**
     * 根据条件删除向量
     *
     * @param index 索引名称
     * @param fieldName 字段名
     * @param fieldValue 字段值
     */
    void deleteByField(String index, String fieldName, Object fieldValue);
}

