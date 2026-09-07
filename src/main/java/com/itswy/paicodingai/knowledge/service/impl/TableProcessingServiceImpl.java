package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.entity.KnowledgeTable;
import com.itswy.paicodingai.knowledge.entity.KnowledgeTableRow;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeTableMapper;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeTableRowMapper;
import com.itswy.paicodingai.knowledge.service.TableProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 表格处理服务实现
 */
@Slf4j
@Service
public class TableProcessingServiceImpl implements TableProcessingService {

    @Autowired
    private KnowledgeTableMapper tableMapper;

    @Autowired
    private KnowledgeTableRowMapper tableRowMapper;

    @Override
    @Transactional
    public KnowledgeTable processTable(String documentId, String knowledgeBaseId,
                                        String title, List<String> headers,
                                        List<List<String>> rows,
                                        int pageStart, String sectionPath) {
        // 1. 生成表格ID
        String tableId = "table_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 2. 生成表格摘要
        String summary = generateTableSummary(title, headers, rows.size());

        // 3. 构建表头JSON
        String headersJson = headers.toString();

        // 4. 创建表格元数据
        KnowledgeTable table = KnowledgeTable.builder()
                .id(tableId)
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .title(title != null ? title : "未命名表格")
                .pageStart(pageStart)
                .pageEnd(pageStart)
                .sectionPath(sectionPath)
                .summary(summary)
                .headersJson(headersJson)
                .rowCount(rows.size())
                .columnCount(headers.size())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 5. 插入表格元数据
        tableMapper.insert(table);

        // 6. 转换为EAV格式并插入
        List<KnowledgeTableRow> tableRows = toEavFormat(tableId, headers, rows);
        for (KnowledgeTableRow row : tableRows) {
            tableRowMapper.insert(row);
        }

        log.info("表格处理完成: tableId={}, rowCount={}, columnCount={}",
                tableId, rows.size(), headers.size());

        return table;
    }

    @Override
    public String generateTableSummary(String title, List<String> headers, int rowCount) {
        StringBuilder summary = new StringBuilder();

        if (title != null && !title.isBlank()) {
            summary.append("该表标题为「").append(title).append("」，");
        } else {
            summary.append("该表");
        }

        summary.append("包含").append(rowCount).append("行数据，");
        summary.append("字段包括：").append(String.join("、", headers)).append("。");

        return summary.toString();
    }

    @Override
    public List<String> generateRowGroupChunks(String title, List<String> headers,
                                                List<List<String>> rows, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // 构建前缀（表名 + 表头）
        String prefix = "表名：" + (title != null ? title : "未命名表格") + "\n"
                + "字段：" + String.join(", ", headers) + "\n";

        // 按chunkSize分行组
        for (int i = 0; i < rows.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, rows.size());
            List<List<String>> rowGroup = rows.subList(i, end);

            StringBuilder chunk = new StringBuilder(prefix);
            for (List<String> row : rowGroup) {
                for (int col = 0; col < headers.size() && col < row.size(); col++) {
                    chunk.append(headers.get(col)).append("：").append(row.get(col)).append("\n");
                }
            }

            chunks.add(chunk.toString().trim());
        }

        return chunks;
    }

    @Override
    public List<KnowledgeTableRow> toEavFormat(String tableId, List<String> headers,
                                                 List<List<String>> rows) {
        List<KnowledgeTableRow> tableRows = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < headers.size() && colIndex < row.size(); colIndex++) {
                String fieldName = headers.get(colIndex);
                String fieldValue = row.get(colIndex);

                // 归一化值（用于精确查询）
                String normalizedValue = normalizeValue(fieldValue);

                KnowledgeTableRow tableRow = KnowledgeTableRow.builder()
                        .tableId(tableId)
                        .rowIndex(rowIndex)
                        .fieldName(fieldName)
                        .fieldValue(fieldValue)
                        .normalizedValue(normalizedValue)
                        .createdAt(LocalDateTime.now())
                        .build();

                tableRows.add(tableRow);
            }
        }

        return tableRows;
    }

    @Override
    public KnowledgeTable getTable(String tableId) {
        return tableMapper.selectById(tableId);
    }

    @Override
    public List<KnowledgeTableRow> getTableRows(String tableId) {
        return tableRowMapper.selectByTableId(tableId);
    }

    @Override
    public String getTableSchema(String tableId) {
        KnowledgeTable table = getTable(tableId);
        if (table == null) {
            return "";
        }

        List<String> fieldNames = tableRowMapper.selectFieldNames(tableId);

        StringBuilder schema = new StringBuilder();
        schema.append("表格ID: ").append(tableId).append("\n");
        schema.append("表格标题: ").append(table.getTitle()).append("\n");
        schema.append("字段列表: ").append(String.join(", ", fieldNames)).append("\n");
        schema.append("行数: ").append(table.getRowCount()).append("\n");

        return schema.toString();
    }

    /**
     * 归一化值（用于精确查询）
     */
    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }

        // 去除空格
        value = value.trim();

        // 尝试转为数字（如果是数字的话）
        try {
            double numValue = Double.parseDouble(value);
            return String.valueOf(numValue);
        } catch (NumberFormatException e) {
            // 不是数字，保持原样
        }

        return value.toLowerCase();
    }
}
