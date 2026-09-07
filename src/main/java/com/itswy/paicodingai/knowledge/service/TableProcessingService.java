package com.itswy.paicodingai.knowledge.service;

import com.itswy.paicodingai.knowledge.entity.KnowledgeTable;
import com.itswy.paicodingai.knowledge.entity.KnowledgeTableRow;

import java.util.List;

/**
 * 表格处理服务接口
 */
public interface TableProcessingService {

    /**
     * 识别并处理表格
     *
     * @param documentId 文档ID
     * @param knowledgeBaseId 知识库ID
     * @param title 表格标题
     * @param headers 表头列表
     * @param rows 数据行列表
     * @param pageStart 起始页码
     * @param sectionPath 章节路径
     * @return 表格元数据
     */
    KnowledgeTable processTable(String documentId, String knowledgeBaseId,
                                String title, List<String> headers,
                                List<List<String>> rows,
                                int pageStart, String sectionPath);

    /**
     * 生成表格摘要（用于向量化）
     */
    String generateTableSummary(String title, List<String> headers, int rowCount);

    /**
     * 生成行组Chunks（用于向量化）
     */
    List<String> generateRowGroupChunks(String title, List<String> headers,
                                        List<List<String>> rows, int chunkSize);

    /**
     * 将表格行转换为EAV格式
     */
    List<KnowledgeTableRow> toEavFormat(String tableId, List<String> headers,
                                         List<List<String>> rows);

    /**
     * 获取表格元数据
     */
    KnowledgeTable getTable(String tableId);

    /**
     * 获取表格行数据
     */
    List<KnowledgeTableRow> getTableRows(String tableId);

    /**
     * 获取表格Schema（用于Text-to-SQL）
     */
    String getTableSchema(String tableId);
}
