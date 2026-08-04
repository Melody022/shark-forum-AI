package com.itswy.paicodingai.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * VectorStore配置
 *
 * 使用Spring AI的SimpleVectorStore（内存存储）
 * 用于快速验证RAG流程
 * 后续可以替换为ElasticsearchVectorStore
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * 配置VectorStore
     *
     * 使用SimpleVectorStore（内存存储）
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("初始化SimpleVectorStore（内存存储）");

        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
