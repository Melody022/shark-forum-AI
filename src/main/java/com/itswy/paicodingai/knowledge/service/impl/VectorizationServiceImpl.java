package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务实现
 * 使用Spring AI的EmbeddingModel进行向量化
 */
@Slf4j
@Service
public class VectorizationServiceImpl implements VectorizationService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        try {
            // 简化实现：使用EmbeddingModel的call方法
            List<String> texts = List.of(text);
            List<float[]> results = embedBatch(texts);

            if (results.isEmpty()) {
                throw new RuntimeException("向量化失败：返回结果为空");
            }

            return results.get(0);

        } catch (Exception e) {
            log.error("文本向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();

        try {
            // 使用EmbeddingModel进行批量向量化
            // Spring AI 2.0 API变化：直接调用call方法
            org.springframework.ai.embedding.EmbeddingRequest request =
                    new org.springframework.ai.embedding.EmbeddingRequest(
                            texts,
                            org.springframework.ai.embedding.EmbeddingOptions.builder().build()
                    );

            org.springframework.ai.embedding.EmbeddingResponse response = embeddingModel.call(request);

            if (response != null && response.getResults() != null) {
                for (org.springframework.ai.embedding.Embedding embedding : response.getResults()) {
                    // 获取向量数据
                    // Spring AI 2.0: getOutput()返回的是float[]
                    float[] vector = embedding.getOutput();
                    results.add(vector);
                }
            }

            log.info("批量向量化完成: {} 条文本", results.size());
            return results;

        } catch (Exception e) {
            log.error("批量向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量向量化失败: " + e.getMessage(), e);
        }
    }
}
