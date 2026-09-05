package com.itswy.paicodingai.knowledge.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 向量存储和混合检索实现。
 */
@Slf4j
@Service
public class ElasticsearchVectorStoreService implements VectorStoreService {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${rag.embedding-dimensions:1024}")
    private int embeddingDimensions;

    @Override
    public void store(String index, String id, float[] vector, Map<String, Object> metadata) {
        try {
            ensureIndex(index, vector.length);
            Map<String, Object> doc = new HashMap<>(metadata == null ? Map.of() : metadata);
            doc.put("vector", vector);
            IndexResponse response = elasticsearchClient.index(IndexRequest.of(builder -> builder
                    .index(index).id(id).document(doc)));
            log.debug("存储向量成功: index={}, id={}, result={}", index, id, response.result());
        } catch (IOException e) {
            throw new RuntimeException("存储向量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeBatch(String index, List<VectorData> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        try {
            ensureIndex(index, vectors.get(0).vector.length);
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (VectorData data : vectors) {
                Map<String, Object> doc = new HashMap<>(data.metadata == null ? Map.of() : data.metadata);
                doc.put("vector", data.vector);
                bulk.operations(operation -> operation.index(item -> item
                        .index(index).id(data.id).document(doc)));
            }
            BulkResponse response = elasticsearchClient.bulk(bulk.build());
            if (response.errors()) {
                log.error("批量存储向量存在错误: {}", response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.error().reason()).toList());
            }
        } catch (IOException e) {
            throw new RuntimeException("批量存储向量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorSearchResult> search(String index, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        return knnSearch(index, queryVector, Math.max(1, topK), Map.of("searchable", 1));
    }

    @Override
    public List<VectorSearchResult> keywordSearch(String index, String query, int topK,
                                                  Map<String, Object> filters) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            SearchResponse<Map> response = elasticsearchClient.search(request -> request
                    .index(index)
                    .size(Math.max(1, topK))
                    .query(buildKeywordQuery(query, filters)), Map.class);
            return toResults(response);
        } catch (IOException e) {
            log.warn("BM25 搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<VectorSearchResult> hybridSearch(String index, String query, float[] queryVector,
                                                 int topK, Map<String, Object> filters) {
        Map<String, Object> effectiveFilters = new HashMap<>(filters == null ? Map.of() : filters);
        effectiveFilters.putIfAbsent("searchable", 1);
        int candidateK = Math.max(10, topK * 4);
        List<VectorSearchResult> vectorResults = knnSearch(index, queryVector, candidateK, effectiveFilters);
        List<VectorSearchResult> keywordResults = keywordSearch(index, query, candidateK, effectiveFilters);
        return reciprocalRankFusion(vectorResults, keywordResults, topK);
    }

    private List<VectorSearchResult> knnSearch(String index, float[] queryVector, int topK,
                                               Map<String, Object> filters) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        try {
            if (!indexExists(index)) {
                return List.of();
            }
            Query filter = buildFilterQuery(filters);
            SearchResponse<Map> response = elasticsearchClient.search(request -> request
                    .index(index)
                    .size(Math.max(1, topK))
                    .knn(knn -> {
                        KnnQuery.Builder builder = knn.field("vector")
                                .queryVector(toFloatList(queryVector))
                                .k(Math.max(1, topK))
                                .numCandidates(Math.max(50, topK * 10));
                        if (filter != null) {
                            builder.filter(filter);
                        }
                        return builder;
                    }), Map.class);
            return toResults(response);
        } catch (IOException e) {
            log.warn("KNN 搜索失败，保留 BM25 结果: {}", e.getMessage());
            return List.of();
        }
    }

    private Query buildKeywordQuery(String query, Map<String, Object> filters) {
        Query textQuery = Query.of(q -> q.multiMatch(m -> m
                .query(query)
                .fields("content^3", "sectionPath^2", "fileName", "ocrText", "tableText")));
        Query filter = buildFilterQuery(filters);
        if (filter == null) {
            return textQuery;
        }
        return Query.of(q -> q.bool(bool -> bool.must(textQuery).filter(filter)));
    }

    private Query buildFilterQuery(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<Query> clauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() instanceof List<?> values) {
                List<FieldValue> fieldValues = values.stream()
                        .map(this::toFieldValue).toList();
                clauses.add(Query.of(q -> q.terms(terms -> terms.field(entry.getKey())
                        .terms(valuesBuilder -> valuesBuilder.value(fieldValues)))));
            } else {
                Object value = entry.getValue();
                clauses.add(Query.of(q -> q.term(term -> {
                    term.field(entry.getKey());
                    if (value instanceof Number number) {
                        return term.value(number.longValue());
                    }
                    return term.value(String.valueOf(value));
                })));
            }
        }
        if (clauses.isEmpty()) {
            return null;
        }
        return Query.of(q -> q.bool(bool -> bool.filter(clauses)));
    }

    private List<VectorSearchResult> reciprocalRankFusion(List<VectorSearchResult> vectorResults,
                                                          List<VectorSearchResult> keywordResults,
                                                          int topK) {
        Map<String, ScoredResult> merged = new LinkedHashMap<>();
        addRanks(merged, vectorResults, 0.55D);
        addRanks(merged, keywordResults, 0.45D);
        return merged.values().stream()
                .sorted((left, right) -> Double.compare(right.fusedScore, left.fusedScore))
                .limit(Math.max(1, topK))
                .map(item -> new VectorSearchResult(item.result.id, (float) item.fusedScore, item.result.metadata))
                .toList();
    }

    private void addRanks(Map<String, ScoredResult> merged, List<VectorSearchResult> results, double weight) {
        for (int i = 0; i < results.size(); i++) {
            VectorSearchResult result = results.get(i);
            String key = result.id != null ? result.id : String.valueOf(result.metadata.get("chunkId"));
            ScoredResult scored = merged.computeIfAbsent(key, ignored -> new ScoredResult(result));
            scored.fusedScore += weight / (60D + i + 1D);
        }
    }

    private List<VectorSearchResult> toResults(SearchResponse<Map> response) {
        List<VectorSearchResult> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                results.add(new VectorSearchResult(hit.id(),
                        hit.score() == null ? 0F : hit.score().floatValue(), hit.source()));
            }
        }
        return results;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private FieldValue toFieldValue(Object value) {
        if (value instanceof Number number) {
            return FieldValue.of(number.longValue());
        }
        if (value instanceof Boolean bool) {
            return FieldValue.of(bool);
        }
        return FieldValue.of(String.valueOf(value));
    }

    @Override
    public void delete(String index, String id) {
        try {
            if (!indexExists(index)) {
                return;
            }
            DeleteResponse response = elasticsearchClient.delete(request -> request.index(index).id(id));
            log.debug("删除向量成功: index={}, id={}, result={}", index, id, response.result());
        } catch (IOException e) {
            throw new RuntimeException("删除向量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByField(String index, String fieldName, Object fieldValue) {
        try {
            if (!indexExists(index)) {
                return;
            }
            DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(request -> request
                    .index(index)
                    .query(query -> query.term(term -> term.field(fieldName)
                            .value(String.valueOf(fieldValue)))));
            log.debug("按条件删除向量成功: index={}, field={}, deleted={}",
                    index, fieldName, response.deleted());
        } catch (IOException e) {
            throw new RuntimeException("按条件删除向量失败: " + e.getMessage(), e);
        }
    }

    private void ensureIndex(String index, int vectorLength) throws IOException {
        boolean exists = indexExists(index);
        if (exists) {
            return;
        }
        int dimensions = vectorLength > 0 ? vectorLength : embeddingDimensions;
        elasticsearchClient.indices().create(request -> request.index(index).mappings(mapping -> mapping
                .properties("vector", property -> property.denseVector(vector -> vector
                        .dims(dimensions).index(true).similarity("cosine")))
                .properties("content", property -> property.text(text -> text))
                .properties("sectionPath", property -> property.text(text -> text))
                .properties("fileName", property -> property.keyword(keyword -> keyword))
                .properties("fileMd5", property -> property.keyword(keyword -> keyword))
                .properties("chunkIndex", property -> property.integer(integer -> integer))
                .properties("docId", property -> property.long_(number -> number))
                .properties("kbId", property -> property.long_(number -> number))
                .properties("parentId", property -> property.long_(number -> number))
                .properties("chunkRole", property -> property.keyword(keyword -> keyword))
                .properties("blockType", property -> property.keyword(keyword -> keyword))
                .properties("pageStart", property -> property.integer(integer -> integer))
                .properties("pageEnd", property -> property.integer(integer -> integer))
                .properties("searchable", property -> property.integer(integer -> integer))
                .properties("ocrText", property -> property.text(text -> text))
                .properties("tableText", property -> property.text(text -> text))));
        log.info("创建 ES 向量索引成功: {}, dims={}", index, dimensions);
    }

    private boolean indexExists(String index) throws IOException {
        return elasticsearchClient.indices().exists(request -> request.index(index)).value();
    }

    private static class ScoredResult {
        private final VectorSearchResult result;
        private double fusedScore;

        private ScoredResult(VectorSearchResult result) {
            this.result = result;
        }
    }
}
