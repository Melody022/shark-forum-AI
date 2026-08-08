package com.itswy.paicodingai.knowledge.service;

import java.util.Map;

/**
 * 向量搜索结果
 */
public class VectorSearchResult {
    public String id;
    public float score;
    public Map<String, Object> metadata;

    public VectorSearchResult(String id, float score, Map<String, Object> metadata) {
        this.id = id;
        this.score = score;
        this.metadata = metadata;
    }
}
