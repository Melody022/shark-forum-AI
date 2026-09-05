package com.itswy.paicodingai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.file.parser.BlockType;
import com.itswy.paicodingai.file.parser.ChunkDraft;
import com.itswy.paicodingai.file.parser.ContentBlock;
import com.itswy.paicodingai.file.parser.DocumentParserManager;
import com.itswy.paicodingai.file.parser.ParseResult;
import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeBaseMapper;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeChunkMapper;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeDocumentMapper;
import com.itswy.paicodingai.knowledge.service.DocumentProcessingService;
import com.itswy.paicodingai.knowledge.service.KnowledgeBaseService;
import com.itswy.paicodingai.rag.splitter.HierarchicalChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired
    private DocumentParserManager documentParserManager;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private HierarchicalChunker hierarchicalChunker;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${file.upload.merged-path:./uploads/merged}")
    private String mergedPath;

    @Override
    @Transactional
    public KnowledgeBase createUserKnowledgeBase(String userId, String name, String description) {
        // 检查用户是否已有同名知识库
        Long count = knowledgeBaseMapper.selectCount(
                Wrappers.<KnowledgeBase>lambdaQuery()
                        .eq(KnowledgeBase::getUserId, userId)
                        .eq(KnowledgeBase::getName, name)
        );

        if (count > 0) {
            throw new RuntimeException("知识库名称已存在");
        }

        KnowledgeBase kb = KnowledgeBase.builder()
                .name(name)
                .description(description)
                .type(KnowledgeBase.TYPE_USER)
                .userId(userId)
                .status(KnowledgeBase.STATUS_ENABLED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        knowledgeBaseMapper.insert(kb);
        log.info("创建用户知识库: userId={}, kbId={}, name={}", userId, kb.getId(), name);

        return kb;
    }

    @Override
    public List<KnowledgeBase> getUserKnowledgeBases(String userId) {
        return knowledgeBaseMapper.selectList(
                Wrappers.<KnowledgeBase>lambdaQuery()
                        .eq(KnowledgeBase::getUserId, userId)
                        .eq(KnowledgeBase::getType, KnowledgeBase.TYPE_USER)
                        .eq(KnowledgeBase::getStatus, KnowledgeBase.STATUS_ENABLED)
                        .orderByDesc(KnowledgeBase::getCreatedAt)
        );
    }

    @Override
    public KnowledgeBase getSystemKnowledgeBase() {
        return knowledgeBaseMapper.selectOne(
                Wrappers.<KnowledgeBase>lambdaQuery()
                        .eq(KnowledgeBase::getType, KnowledgeBase.TYPE_SYSTEM)
                        .eq(KnowledgeBase::getStatus, KnowledgeBase.STATUS_ENABLED)
                        .last("LIMIT 1")
        );
    }

    @Override
    public List<KnowledgeBase> getAccessibleKnowledgeBases(String userId) {
        List<KnowledgeBase> result = new ArrayList<>();

        // 用户自己的知识库
        result.addAll(getUserKnowledgeBases(userId));

        // 系统知识库
        KnowledgeBase systemKb = getSystemKnowledgeBase();
        if (systemKb != null) {
            result.add(systemKb);
        }

        return result;
    }

    @Override
    @Transactional
    public KnowledgeDocument uploadDocument(Long kbId, String fileMd5, String fileName,
                                             String fileType, Long totalSize, String userId) {
        // 验证知识库访问权限
        if (!hasAccess(kbId, userId)) {
            throw new RuntimeException("无权限访问该知识库");
        }

        // 检查文档是否已存在
        KnowledgeDocument existing = knowledgeDocumentMapper.selectOne(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .eq(KnowledgeDocument::getFileMd5, fileMd5)
        );

        if (existing != null) {
            throw new RuntimeException("文档已存在");
        }

        // 创建文档记录
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .kbId(kbId)
                .fileMd5(fileMd5)
                .fileName(fileName)
                .fileType(fileType)
                .totalSize(totalSize)
                .status(KnowledgeDocument.STATUS_PARSING)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        knowledgeDocumentMapper.insert(doc);

        // 异步解析文档
        parseDocument(doc);

        return doc;
    }

    private void parseDocument(KnowledgeDocument doc) {
        try {
            // 查找合并后的文件
            File file = findMergedFile(doc.getFileMd5(), doc.getUserId(), doc.getFileName());

            if (file == null || !file.exists()) {
                log.error("找不到合并后的文件: fileMd5={}", doc.getFileMd5());
                updateDocumentStatus(doc.getId(), KnowledgeDocument.STATUS_FAILED);
                return;
            }

            // 解析文档
            ParseResult result = documentParserManager.parse(file, doc.getFileType());

            if (!result.isSuccess()) {
                log.error("文档解析失败: docId={}, error={}", doc.getId(), result.getErrorMessage());
                updateDocumentStatus(doc.getId(), KnowledgeDocument.STATUS_FAILED);
                return;
            }

            // 统一结构化块后再生成章节父块和可检索子块。
            List<ContentBlock> contentBlocks = result.getContentBlocks();
            if (contentBlocks == null || contentBlocks.isEmpty()) {
                contentBlocks = legacyBlocks(result);
            }
            List<ChunkDraft> drafts = hierarchicalChunker.chunk(contentBlocks);
            if (drafts.isEmpty()) {
                log.warn("文档解析后没有可入库内容: docId={}", doc.getId());
            }

            knowledgeChunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getDocId, doc.getId()));

            List<KnowledgeChunk> chunks = persistChunkDrafts(doc, drafts);

            // 更新文档状态
            doc.setStatus(KnowledgeDocument.STATUS_COMPLETED);
            doc.setChunkCount(chunks.size());
            doc.setParsedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);

            log.info("文档解析完成: docId={}, chunks={}", doc.getId(), chunks.size());

            // 触发向量化处理（异步）
            try {
                documentProcessingService.processDocument(doc.getId());
                log.info("向量化处理已触发: docId={}", doc.getId());
            } catch (Exception e) {
                log.error("触发向量化处理失败: docId={}", doc.getId(), e);
                // 向量化失败不影响文档解析结果
            }

        } catch (Exception e) {
            log.error("文档解析异常: docId={}", doc.getId(), e);
            updateDocumentStatus(doc.getId(), KnowledgeDocument.STATUS_FAILED);
        }
    }

    private List<ContentBlock> legacyBlocks(ParseResult result) {
        if (result.getChunks() == null || result.getChunks().isEmpty()) {
            return List.of();
        }
        List<ContentBlock> blocks = new ArrayList<>();
        for (int i = 0; i < result.getChunks().size(); i++) {
            blocks.add(ContentBlock.builder()
                    .blockId("legacy-" + i)
                    .type(BlockType.TEXT)
                    .content(result.getChunks().get(i))
                    .build());
        }
        return blocks;
    }

    private List<KnowledgeChunk> persistChunkDrafts(KnowledgeDocument doc, List<ChunkDraft> drafts) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        java.util.Map<String, Long> parentIds = new java.util.HashMap<>();
        int chunkIndex = 0;

        for (ChunkDraft draft : drafts) {
            Long parentId = ChunkDraft.ROLE_CHILD.equals(draft.getRole())
                    ? parentIds.get(draft.getParentKey()) : null;
            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .docId(doc.getId())
                    .chunkIndex(chunkIndex++)
                    .content(draft.getContent())
                    .chunkRole(draft.getRole())
                    .parentId(parentId)
                    .parentKey(draft.getParentKey())
                    .sourceBlockId(draft.getSourceBlockId())
                    .blockType(draft.getBlockType() == null ? BlockType.TEXT.name() : draft.getBlockType().name())
                    .pageStart(draft.getPageStart())
                    .pageEnd(draft.getPageEnd())
                    .sectionPath(draft.getSectionPath())
                    .metadataJson(writeMetadata(draft.getMetadata()))
                    .searchable(draft.isSearchable() ? 1 : 0)
                    .tokenCount(estimateTokenCount(draft.getContent()))
                    .createdAt(LocalDateTime.now())
                    .build();
            knowledgeChunkMapper.insert(chunk);
            chunks.add(chunk);
            if (ChunkDraft.ROLE_PARENT.equals(draft.getRole())) {
                parentIds.put(draft.getParentKey(), chunk.getId());
            }
        }
        return chunks;
    }

    private String writeMetadata(java.util.Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? java.util.Map.of() : metadata);
        } catch (Exception e) {
            log.warn("Chunk 元数据序列化失败", e);
            return "{}";
        }
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.codePointCount(0, content.length()) / 2);
    }

    private File findMergedFile(String fileMd5, String userId, String fileName) {
        // 尝试多个可能的路径
        String[] possiblePaths = {
                mergedPath + "/" + userId + "/" + fileName,
                mergedPath + "/" + userId + "/" + fileMd5,
                mergedPath + "/" + fileName,
                mergedPath + "/" + fileMd5
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }

        return null;
    }

    private void updateDocumentStatus(Long docId, int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(docId);
        doc.setStatus(status);
        doc.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);
    }

    @Override
    public List<KnowledgeDocument> getKnowledgeBaseDocuments(Long kbId, String userId) {
        // 验证权限
        if (!hasAccess(kbId, userId)) {
            throw new RuntimeException("无权限访问该知识库");
        }

        return knowledgeDocumentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt)
        );
    }

    @Override
    public List<KnowledgeDocument> getUserDocuments(String userId) {
        return knowledgeDocumentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getUserId, userId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt)
        );
    }

    @Override
    public List<KnowledgeChunk> searchKnowledgeBase(String query, String userId, int topK) {
        // 获取用户可访问的知识库
        List<KnowledgeBase> accessibleKbs = getAccessibleKnowledgeBases(userId);

        if (accessibleKbs.isEmpty()) {
            return List.of();
        }

        List<Long> kbIds = accessibleKbs.stream()
                .map(KnowledgeBase::getId)
                .toList();

        // 获取这些知识库下的所有文档
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .in(KnowledgeDocument::getKbId, kbIds)
                        .eq(KnowledgeDocument::getStatus, KnowledgeDocument.STATUS_COMPLETED)
        );

        if (docs.isEmpty()) {
            return List.of();
        }

        List<Long> docIds = docs.stream()
                .map(KnowledgeDocument::getId)
                .toList();

        // 搜索分块（简单实现：包含关键词的分块）
        return knowledgeChunkMapper.selectList(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                        .in(KnowledgeChunk::getDocId, docIds)
                        .eq(KnowledgeChunk::getChunkRole, ChunkDraft.ROLE_CHILD)
                        .eq(KnowledgeChunk::getSearchable, 1)
                        .like(KnowledgeChunk::getContent, query)
                        .last("LIMIT " + topK)
        );
    }

    @Override
    public List<KnowledgeChunk> searchInKnowledgeBase(Long kbId, String query, int topK) {
        // 获取知识库下的所有文档
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .eq(KnowledgeDocument::getStatus, KnowledgeDocument.STATUS_COMPLETED)
        );

        if (docs.isEmpty()) {
            return List.of();
        }

        List<Long> docIds = docs.stream()
                .map(KnowledgeDocument::getId)
                .toList();

        // 搜索分块
        return knowledgeChunkMapper.selectList(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                        .in(KnowledgeChunk::getDocId, docIds)
                        .eq(KnowledgeChunk::getChunkRole, ChunkDraft.ROLE_CHILD)
                        .eq(KnowledgeChunk::getSearchable, 1)
                        .like(KnowledgeChunk::getContent, query)
                        .last("LIMIT " + topK)
        );
    }

    @Override
    @Transactional
    public boolean deleteDocument(Long docId, String userId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);

        if (doc == null) {
            return false;
        }

        // 检查权限
        if (doc.getUserId() == null || !doc.getUserId().equals(userId)) {
            return false;
        }

        // 删除分块
        knowledgeChunkMapper.delete(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getDocId, docId)
        );

        // 同步清理 ES 中的子块向量。
        documentProcessingService.deleteDocumentVectors(docId);

        // 删除文档
        knowledgeDocumentMapper.deleteById(docId);

        return true;
    }

    @Override
    public boolean hasAccess(Long kbId, String userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);

        if (kb == null) {
            return false;
        }

        // 系统知识库所有人都可以访问
        if (kb.getType() == KnowledgeBase.TYPE_SYSTEM) {
            return kb.getStatus() != null && kb.getStatus() == KnowledgeBase.STATUS_ENABLED;
        }

        // 用户知识库只有所有者可以访问
        return kb.getStatus() != null && kb.getStatus() == KnowledgeBase.STATUS_ENABLED
                && kb.getUserId() != null && kb.getUserId().equals(userId);
    }
}
