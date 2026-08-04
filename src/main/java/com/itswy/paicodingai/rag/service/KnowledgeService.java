package com.itswy.paicodingai.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.rag.splitter.SemanticTextSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库服务
 *
 * 负责文档的存储、切分和检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final SemanticTextSplitter textSplitter;
    private final ObjectMapper objectMapper;

    /** 存储原始文档（用于管理页面展示） */
    private final Map<String, DocumentInfo> documentStore = new ConcurrentHashMap<>();

    /**
     * 添加文档到知识库
     */
    public List<String> addDocument(String title, String content, String category) {
        log.info("添加文档到知识库: title={}, category={}", title, category);

        // 1. 语义切分
        List<String> chunks = textSplitter.split(content);
        log.info("文档切分完成，Chunk数量: {}", chunks.size());

        // 2. 生成文档ID
        String documentId = UUID.randomUUID().toString();

        // 3. 构建Document对象并存储
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = documentId + "_chunk_" + i;
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("title", title);
            metadata.put("category", category);
            metadata.put("document_id", documentId);
            metadata.put("chunk_index", i);
            metadata.put("total_chunks", chunks.size());

            Document document = Document.builder()
                .id(chunkId)
                .text(chunks.get(i))
                .metadata(metadata)
                .build();

            documents.add(document);
        }

        // 4. 存储到VectorStore
        vectorStore.add(documents);

        // 5. 存储原始文档信息（用于管理）
        documentStore.put(documentId, new DocumentInfo(
            documentId, title, content, category, chunks.size()
        ));

        log.info("文档添加成功: id={}, title={}, chunks={}", documentId, title, chunks.size());

        return documents.stream()
            .map(Document::getId)
            .toList();
    }

    /**
     * 获取所有文档
     */
    public List<Document> getAllDocuments() {
        // 返回文档摘要信息（不包含完整内容，避免数据量太大）
        return documentStore.values().stream()
            .map(info -> Document.builder()
                .id(info.id())
                .text(info.title() + " - " + info.category())
                .metadata(Map.of(
                    "title", info.title(),
                    "category", info.category(),
                    "chunkCount", info.chunkCount()
                ))
                .build())
            .toList();
    }

    /**
     * 获取文档详情
     */
    public DocumentInfo getDocumentDetail(String documentId) {
        return documentStore.get(documentId);
    }

    /**
     * 搜索知识库
     */
    public List<Document> search(String query, int topK) {
        log.info("搜索知识库: query={}, topK={}", query, topK);

        SearchRequest searchRequest = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        log.info("搜索完成，返回结果数量: {}", results.size());
        return results;
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String documentId) {
        log.info("删除文档: documentId={}", documentId);

        DocumentInfo info = documentStore.remove(documentId);
        if (info != null) {
            // 删除VectorStore中的chunks
            List<String> chunkIds = new ArrayList<>();
            for (int i = 0; i < info.chunkCount(); i++) {
                chunkIds.add(documentId + "_chunk_" + i);
            }
            vectorStore.delete(chunkIds);
            log.info("文档删除成功: documentId={}", documentId);
        } else {
            log.warn("文档不存在: documentId={}", documentId);
        }
    }

    /**
     * 更新文档
     */
    public void updateDocument(String documentId, String title, String content, String category) {
        log.info("更新文档: documentId={}", documentId);

        // 先删除旧文档
        deleteDocument(documentId);

        // 添加新文档
        addDocument(title, content, category);

        log.info("文档更新成功: documentId={}", documentId);
    }

    /**
     * 启动时加载种子数据
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("开始加载知识库种子数据...");

        try {
            loadSeedData();
            log.info("知识库初始化完成");
        } catch (Exception e) {
            log.warn("知识库初始化失败（非致命错误）: {}", e.getMessage());
        }
    }

    /**
     * 加载种子数据
     */
    private void loadSeedData() {
        try {
            ClassPathResource resource = new ClassPathResource("data/knowledge-seed.json");
            InputStream inputStream = resource.getInputStream();

            List<Map<String, String>> seedData = objectMapper.readValue(
                inputStream,
                new TypeReference<List<Map<String, String>>>() {}
            );

            for (Map<String, String> item : seedData) {
                String title = item.get("title");
                String content = item.get("content");
                String category = item.getOrDefault("category", "通用");

                addDocument(title, content, category);
            }

            log.info("种子数据加载完成，文档数量: {}", seedData.size());

        } catch (Exception e) {
            log.error("加载种子数据失败", e);
        }
    }

    /**
     * 文档信息记录
     */
    public record DocumentInfo(
        String id,
        String title,
        String content,
        String category,
        int chunkCount
    ) {}
}
