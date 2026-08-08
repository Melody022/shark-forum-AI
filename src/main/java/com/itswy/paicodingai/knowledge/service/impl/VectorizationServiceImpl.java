package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
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
            EmbeddingRequest request = new EmbeddingRequest(List.of(text));
            EmbeddingResponse response = embeddingModel.call(request);

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                Embedding embedding = response.getData().get(0);
                return embedding.getOutput();
            }

            throw new RuntimeException("向量化失败：返回结果为空");

        } catch (Exception e) {
            log.error("文本向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();

        try {
            // 批量向量化
            EmbeddingRequest request = new EmbeddingRequest(texts);
            EmbeddingResponse response = embeddingModel.call(request);

            if (response != null && response.getData() != null) {
                for (Embedding embedding : response.getData()) {
                    results.add(embedding.getOutput());
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
