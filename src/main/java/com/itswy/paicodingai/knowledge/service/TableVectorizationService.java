package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * 表格向量化服务接口
 */
public interface TableVectorizationService {

    /**
     * 向量化表格摘要
     *
     * @param tableId 表格ID
     * @return 向量化是否成功
     */
    boolean vectorizeTableSummary(String tableId);

    /**
     * 向量化行组Chunks
     *
     * @param tableId 表格ID
     * @param chunkSize 每个Chunk包含的行数
     * @return 向量化是否成功
     */
    boolean vectorizeRowGroupChunks(String tableId, int chunkSize);

    /**
     * 向量化整个表格（摘要 + 行组Chunks）
     *
     * @param tableId 表格ID
     * @return 向量化是否成功
     */
    boolean vectorizeTable(String tableId);

    /**
     * 删除表格向量
     *
     * @param tableId 表格ID
     * @return 删除是否成功
     */
    boolean deleteTableVectors(String tableId);
}
