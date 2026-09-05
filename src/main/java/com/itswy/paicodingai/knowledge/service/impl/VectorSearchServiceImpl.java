package com.itswy.paicodingai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeBaseMapper;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeChunkMapper;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeDocumentMapper;
import com.itswy.paicodingai.knowledge.service.KnowledgeBaseService;
import com.itswy.paicodingai.knowledge.service.SearchResult;
import com.itswy.paicodingai.knowledge.service.VectorSearchResult;
import com.itswy.paicodingai.knowledge.service.VectorSearchService;
import com.itswy.paicodingai.knowledge.service.VectorStoreService;
import com.itswy.paicodingai.knowledge.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合召回和层级上下文恢复服务。
 */
@Slf4j
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final String VECTOR_INDEX = "knowledge_vectors";

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public List<SearchResult> search(String query, String userId, int topK) {
        List<KnowledgeBase> bases = knowledgeBaseService.getAccessibleKnowledgeBases(userId);
        if (bases.isEmpty()) {
            return List.of();
        }
        List<Long> kbIds = bases.stream().map(KnowledgeBase::getId).toList();
        return searchInternal(query, topK, Map.of("kbId", kbIds));
    }

    @Override
    public List<SearchResult> searchInKnowledgeBase(String query, Long kbId, int topK) {
        if (knowledgeBaseMapper.selectById(kbId) == null) {
            return List.of();
        }
        return searchInternal(query, topK, Map.of("kbId", kbId));
    }

    @Override
    public List<SearchResult> searchInKnowledgeBase(String query, Long kbId, String userId, int topK) {
        if (kbId == null || userId == null || !knowledgeBaseService.hasAccess(kbId, userId)) {
            return List.of();
        }
        return searchInternal(query, topK, Map.of("kbId", kbId));
    }

    private List<SearchResult> searchInternal(String query, int topK, Map<String, Object> filters) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            float[] queryVector = vectorizationService.embed(query);
            List<VectorSearchResult> vectorResults = vectorStoreService.hybridSearch(
                    VECTOR_INDEX, query, queryVector, Math.max(topK * 3, 3), filters);
            if (vectorResults.isEmpty()) {
                return databaseFallback(query, topK, filters);
            }
            return enrich(vectorResults, topK);
        } catch (Exception e) {
            log.warn("混合检索失败，尝试数据库关键词检索: {}", e.getMessage());
            return databaseFallback(query, topK, filters);
        }
    }

    private List<SearchResult> enrich(List<VectorSearchResult> vectorResults, int topK) {
        List<SearchResult> results = new ArrayList<>();
        Set<Long> seenChunks = new HashSet<>();
        Set<Long> seenParents = new HashSet<>();
        for (VectorSearchResult item : vectorResults) {
            Map<String, Object> metadata = item.metadata == null ? Map.of() : item.metadata;
            Long chunkId = asLong(metadata.get("chunkId"));
            if (chunkId != null && !seenChunks.add(chunkId)) {
                continue;
            }
            Long docId = asLong(metadata.get("docId"));
            Long kbId = asLong(metadata.get("kbId"));
            Long parentId = asLong(metadata.get("parentId"));
            List<KnowledgeChunk> siblings = loadSiblingChunks(parentId, docId, asInt(metadata.get("chunkIndex")));
            String parentContent = loadParentContent(parentId);
            String context = buildContext(metadata, parentContent, siblings);
            List<String> adjacent = siblings.stream().map(KnowledgeChunk::getContent).toList();

            if (parentId != null && !seenParents.add(parentId)) {
                // 同一父块只保留一次命中，但每个 child 的来源仍然保留在 context 中。
                continue;
            }
            results.add(new SearchResult(
                    stringValue(metadata.get("content")),
                    item.score,
                    docId,
                    kbId,
                    stringValue(metadata.get("fileMd5")),
                    asInt(metadata.get("chunkIndex")),
                    chunkId,
                    parentId,
                    stringValue(metadata.get("fileName")),
                    stringValue(metadata.get("sectionPath")),
                    stringValue(metadata.get("blockType")),
                    asInt(metadata.get("pageStart")),
                    asInt(metadata.get("pageEnd")),
                    context,
                    adjacent));
            if (results.size() >= topK) {
                break;
            }
        }
        return results;
    }

    private List<KnowledgeChunk> loadSiblingChunks(Long parentId, Long docId, Integer chunkIndex) {
        if (docId == null) {
            return List.of();
        }
        var wrapper = Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocId, docId)
                .eq(KnowledgeChunk::getChunkRole, "CHILD");
        if (parentId == null) {
            wrapper.isNull(KnowledgeChunk::getParentId);
        } else {
            wrapper.eq(KnowledgeChunk::getParentId, parentId);
        }
        List<KnowledgeChunk> chunks = chunkMapper.selectList(wrapper.orderByAsc(KnowledgeChunk::getChunkIndex));
        if (chunks.isEmpty() || chunkIndex == null) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> Math.abs(chunk.getChunkIndex() - chunkIndex) <= 1)
                .toList();
    }

    private String loadParentContent(Long parentId) {
        if (parentId == null) {
            return "";
        }
        KnowledgeChunk parent = chunkMapper.selectById(parentId);
        return parent == null ? "" : parent.getContent();
    }

    private String buildContext(Map<String, Object> metadata, String parentContent,
                                List<KnowledgeChunk> siblings) {
        StringBuilder context = new StringBuilder();
        String sectionPath = stringValue(metadata.get("sectionPath"));
        if (!sectionPath.isBlank()) {
            context.append("章节：").append(sectionPath).append('\n');
        }
        if (metadata.get("pageStart") != null) {
            context.append("页码：").append(metadata.get("pageStart"));
            if (metadata.get("pageEnd") != null && !metadata.get("pageEnd").equals(metadata.get("pageStart"))) {
                context.append('-').append(metadata.get("pageEnd"));
            }
            context.append('\n');
        }
        context.append("命中内容：").append(stringValue(metadata.get("content")));
        if (!siblings.isEmpty()) {
            context.append("\n相邻内容：\n");
            siblings.stream()
                    .map(KnowledgeChunk::getContent)
                    .filter(value -> value != null && !value.equals(metadata.get("content")))
                    .forEach(value -> context.append(value).append('\n'));
        }
        if (parentContent != null && !parentContent.isBlank()) {
            context.append("父章节摘要：\n").append(limit(parentContent, 2200));
        }
        return context.toString();
    }

    private List<SearchResult> databaseFallback(String query, int topK, Map<String, Object> filters) {
        Object kbFilter = filters.get("kbId");
        List<Long> kbIds = kbFilter instanceof List<?> values
                ? values.stream().map(this::asLong).filter(java.util.Objects::nonNull).toList()
                : asLong(kbFilter) == null ? List.of() : List.of(asLong(kbFilter));
        if (kbIds.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDocument> docs = documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .in(!kbIds.isEmpty(), KnowledgeDocument::getKbId, kbIds)
                .eq(KnowledgeDocument::getStatus, KnowledgeDocument.STATUS_COMPLETED));
        if (docs.isEmpty()) {
            return List.of();
        }
        List<Long> docIds = docs.stream().map(KnowledgeDocument::getId).toList();
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .in(KnowledgeChunk::getDocId, docIds)
                .eq(KnowledgeChunk::getChunkRole, "CHILD")
                .eq(KnowledgeChunk::getSearchable, 1)
                .like(KnowledgeChunk::getContent, query)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + Math.max(1, topK)));
        Map<Long, KnowledgeDocument> documentMap = new HashMap<>();
        docs.forEach(doc -> documentMap.put(doc.getId(), doc));
        return chunks.stream().map(chunk -> {
            KnowledgeDocument doc = documentMap.get(chunk.getDocId());
            return new SearchResult(chunk.getContent(), 1F, chunk.getDocId(), doc.getKbId(),
                    doc.getFileMd5(), chunk.getChunkIndex(), chunk.getId(), chunk.getParentId(),
                    doc.getFileName(), chunk.getSectionPath(), chunk.getBlockType(),
                    chunk.getPageStart(), chunk.getPageEnd(), chunk.getContent(), List.of());
        }).toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
