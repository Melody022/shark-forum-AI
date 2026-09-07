package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.entity.KnowledgeTable;
import com.itswy.paicodingai.knowledge.entity.KnowledgeTableRow;
import com.itswy.paicodingai.knowledge.service.TableProcessingService;
import com.itswy.paicodingai.knowledge.service.TableVectorizationService;
import com.itswy.paicodingai.knowledge.service.VectorizationService;
import com.itswy.paicodingai.knowledge.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格向量化服务实现
 */
@Slf4j
@Service
public class TableVectorizationServiceImpl implements TableVectorizationService {

    private static final String VECTOR_INDEX = "knowledge_vectors";

    @Autowired
    private TableProcessingService tableProcessingService;

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Override
    public boolean vectorizeTableSummary(String tableId) {
        try {
            // 1. 获取表格信息
            KnowledgeTable table = tableProcessingService.getTable(tableId);
            if (table == null) {
                log.error("表格不存在: {}", tableId);
                return false;
            }

            // 2. 获取表格行数据
            List<KnowledgeTableRow> rows = tableProcessingService.getTableRows(tableId);
            List<String> headers = parseHeaders(table.getHeadersJson());

            // 3. 生成表格摘要
            String summary = table.getSummary();
            if (summary == null || summary.isBlank()) {
                summary = tableProcessingService.generateTableSummary(
                        table.getTitle(), headers, table.getRowCount());
            }

            // 4. 向量化
            float[] vector = vectorizationService.embed(summary);

            // 5. 存储到ES
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("chunkId", tableId + "_summary");
            metadata.put("docId", table.getDocumentId());
            metadata.put("kbId", table.getKnowledgeBaseId());
            metadata.put("tableId", tableId);
            metadata.put("chunkType", "TABLE_SUMMARY");
            metadata.put("title", table.getTitle());
            metadata.put("pageStart", table.getPageStart());
            metadata.put("sectionPath", table.getSectionPath());
            metadata.put("content", summary);

            vectorStoreService.store(VECTOR_INDEX, tableId + "_summary", vector, metadata);

            log.info("表格摘要向量化完成: tableId={}", tableId);
            return true;

        } catch (Exception e) {
            log.error("表格摘要向量化失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean vectorizeRowGroupChunks(String tableId, int chunkSize) {
        try {
            // 1. 获取表格信息
            KnowledgeTable table = tableProcessingService.getTable(tableId);
            if (table == null) {
                log.error("表格不存在: {}", tableId);
                return false;
            }

            // 2. 获取表格行数据
            List<KnowledgeTableRow> rows = tableProcessingService.getTableRows(tableId);
            List<String> headers = parseHeaders(table.getHeadersJson());

            // 3. 将EAV格式转换为二维列表
            List<List<String>> tableData = convertEavToTableData(rows, headers, table.getRowCount());

            // 4. 生成行组Chunks
            List<String> chunks = tableProcessingService.generateRowGroupChunks(
                    table.getTitle(), headers, tableData, chunkSize);

            // 5. 批量向量化
            List<float[]> vectors = vectorizationService.embedBatch(chunks);

            // 6. 存储到ES
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = tableId + "_rowgroup_" + i;
                String content = chunks.get(i);
                float[] vector = vectors.get(i);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("chunkId", chunkId);
                metadata.put("docId", table.getDocumentId());
                metadata.put("kbId", table.getKnowledgeBaseId());
                metadata.put("tableId", tableId);
                metadata.put("chunkType", "TABLE_ROW_GROUP");
                metadata.put("title", table.getTitle());
                metadata.put("pageStart", table.getPageStart());
                metadata.put("sectionPath", table.getSectionPath());
                metadata.put("rowGroupIndex", i);
                metadata.put("content", content);

                vectorStoreService.store(VECTOR_INDEX, chunkId, vector, metadata);
            }

            log.info("表格行组Chunks向量化完成: tableId={}, chunks={}", tableId, chunks.size());
            return true;

        } catch (Exception e) {
            log.error("表格行组Chunks向量化失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean vectorizeTable(String tableId) {
        try {
            // 1. 向量化表格摘要
            boolean summarySuccess = vectorizeTableSummary(tableId);
            if (!summarySuccess) {
                log.warn("表格摘要向量化失败: {}", tableId);
            }

            // 2. 向量化行组Chunks
            boolean chunksSuccess = vectorizeRowGroupChunks(tableId, 10);
            if (!chunksSuccess) {
                log.warn("表格行组Chunks向量化失败: {}", tableId);
            }

            return summarySuccess || chunksSuccess;

        } catch (Exception e) {
            log.error("表格向量化失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteTableVectors(String tableId) {
        try {
            vectorStoreService.deleteByField(VECTOR_INDEX, "tableId", tableId);
            log.info("表格向量删除成功: tableId={}", tableId);
            return true;
        } catch (Exception e) {
            log.error("表格向量删除失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 解析表头JSON
     */
    private List<String> parseHeaders(String headersJson) {
        try {
            // headersJson格式: "[版本, 日期, 特性]"
            if (headersJson != null && headersJson.startsWith("[") && headersJson.endsWith("]")) {
                String content = headersJson.substring(1, headersJson.length() - 1);
                return List.of(content.split(", "));
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 将EAV格式转换为二维列表
     */
    private List<List<String>> convertEavToTableData(List<KnowledgeTableRow> rows,
                                                      List<String> headers,
                                                      int rowCount) {
        List<List<String>> tableData = new java.util.ArrayList<>();

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            final int currentRowIndex = rowIndex;
            List<String> row = new java.util.ArrayList<>();
            for (String header : headers) {
                String value = rows.stream()
                        .filter(r -> r.getRowIndex() == currentRowIndex && r.getFieldName().equals(header))
                        .map(KnowledgeTableRow::getFieldValue)
                        .findFirst()
                        .orElse("");
                row.add(value);
            }
            tableData.add(row);
        }

        return tableData;
    }
}
