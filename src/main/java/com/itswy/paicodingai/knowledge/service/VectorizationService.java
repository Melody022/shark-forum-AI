package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * 向量化服务接口
 */
public interface VectorizationService {

    /**
     * 文本向量化
     *
     * @param text 输入文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量文本向量化
     *
     * @param texts 输入文本列表
     * @return 向量列表
     */
    List<float[]> embedBatch(List<String> texts);
}
