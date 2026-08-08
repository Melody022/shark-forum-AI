package com.itswy.paicodingai.knowledge.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.knowledge.service.VectorData;
import com.itswy.paicodingai.knowledge.service.VectorSearchResult;
import com.itswy.paicodingai.knowledge.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch向量存储服务实现
 */
@Slf4j
@Service
public class ElasticsearchVectorStoreService implements VectorStoreService {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String esUri;

    @Override
    public void store(String index, String id, float[] vector, Map<String, Object> metadata) {
        try {
            // 确保索引存在
            ensureIndex(index);

            // 构建文档
            Map<String, Object> doc = new HashMap<>(metadata);
            doc.put("vector", vector);

            IndexRequest<Map<String, Object>> request = IndexRequest.of(builder -> builder
                    .index(index)
                    .id(id)
                    .document(doc)
            );

            IndexResponse response = elasticsearchClient.index(request);
            log.debug("存储向量成功: index={}, id={}, result={}", index, id, response.result());

        } catch (IOException e) {
            log.error("存储向量失败: index={}, id={}", index, id, e);
            throw new RuntimeException("存储向量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeBatch(String index, List<VectorData> vectors) {
        try {
            ensureIndex(index);

            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (VectorData data : vectors) {
                Map<String, Object> doc = new HashMap<>(data.metadata);
                doc.put("vector", data.vector);

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(index)
                                .id(data.id)
                                .document(doc)
                        )
                );
            }

            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

            if (response.errors()) {
                log.error("批量存储向量存在错误: {}", response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.error().reason())
                        .toList());
            } else {
                log.info("批量存储向量成功: {} 条", vectors.size());
            }

        } catch (IOException e) {
            log.error("批量存储向量失败", e);
            throw new RuntimeException("批量存储向量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorSearchResult> search(String index, float[] queryVector, int topK) {
        try {
            // 使用余弦相似度搜索
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(index)
                    .size(topK)
                    .knn(k -> k
                            .field("vector")
                            .k(topK)
                            .numCandidates(topK * 10)
                            .queryVector(queryVector)
                    ),
                    Map.class
            );

            List<VectorSearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    float score = hit.score() != null ? hit.score().floatValue() : 0f;
                    results.add(new VectorSearchResult(hit.id(), score, source));
                }
            }

            log.debug("向量搜索完成: index={}, 结果数={}", index, results.size());
            return results;

        } catch (IOException e) {
            log.error("向量搜索失败: index={}", index, e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String index, String id) {
        try {
            DeleteResponse response = elasticsearchClient.delete(d -> d
                    .index(index)
                    .id(id)
            );

            log.debug("删除向量成功: index={}, id={}, result={}", index, id, response.result());

        } catch (IOException e) {
            log.error("删除向量失败: index={}, id={}", index, id, e);
            throw new RuntimeException("删除向量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByField(String index, String fieldName, Object fieldValue) {
        try {
            DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(d -> d
                    .index(index)
                    .query(q -> q
                            .term(t -> t
                                    .field(fieldName)
                                    .value(fieldValue.toString())
                            )
                    )
            );

            log.debug("按条件删除向量成功: index={}, field={}, deleted={}",
                    index, fieldName, response.deleted());

        } catch (IOException e) {
            log.error("按条件删除向量失败: index={}, field={}", index, fieldName, e);
            throw new RuntimeException("按条件删除向量失败: " + e.getMessage(), e);
        }
    }

    private void ensureIndex(String index) throws IOException {
        boolean exists = elasticsearchClient.indices().exists(e -> e.index(index)).value();
        if (!exists) {
            // 创建索引，配置向量字段
            elasticsearchClient.indices().create(c -> c
                    .index(index)
                    .mappings(m -> m
                            .properties("vector", p -> p
                                    .denseVector(d -> d
                                            .dims(1024)  // text-embedding-v3维度
                                            .index(true)
                                            .similarity(co.elastic.clients.elasticsearch._types.analysis.DenseVectorSimilarity.Cosine)
                                    )
                            )
                            .properties("content", p -> p.text(t -> t))
                            .properties("fileMd5", p -> p.keyword(k -> k))
                            .properties("chunkIndex", p -> p.integer(i -> i))
                            .properties("docId", p -> p.long_(l -> l))
                            .properties("kbId", p -> p.long_(l -> l))
                    )
            );

            log.info("创建ES索引成功: {}", index);
        }
    }
}
