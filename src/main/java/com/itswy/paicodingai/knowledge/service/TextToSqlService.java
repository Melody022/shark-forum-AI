package com.itswy.paicodingai.knowledge.service;

/**
 * Text-to-SQL 服务接口
 */
public interface TextToSqlService {

    /**
     * 执行 Text-to-SQL 查询
     *
     * @param tableId 表格ID
     * @param question 用户问题
     * @param limit 最大返回行数
     * @return 查询结果
     */
    SqlQueryResult executeQuery(String tableId, String question, int limit);

    /**
     * SQL 查询结果
     */
    record SqlQueryResult(
            String[] columns,
            Object[][] rows,
            int total,
            String sql,
            String explanation
    ) {}
}
