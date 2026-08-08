package com.itswy.paicodingai.knowledge.service;

import java.util.Map;

/**
 * 向量数据
 */
public class VectorData {
    public String id;
    public float[] vector;
    public Map<String, Object> metadata;

    public VectorData(String id, float[] vector, Map<String, Object> metadata) {
        this.id = id;
        this.vector = vector;
        this.metadata = metadata;
    }
}
