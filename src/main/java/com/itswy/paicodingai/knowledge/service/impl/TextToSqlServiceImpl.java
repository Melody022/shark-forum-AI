package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.service.TableProcessingService;
import com.itswy.paicodingai.knowledge.service.TextToSqlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Text-to-SQL 服务实现
 *
 * 安全设计：
 * 1. LLM 只返回 JSON 查询计划，不写 SQL
 * 2. Java 根据白名单生成参数化 SQL
 * 3. 所有参数使用 PreparedStatement 设置，防止注入
 */
@Slf4j
@Service
public class TextToSqlServiceImpl implements TextToSqlService {

    @Autowired
    private TableProcessingService tableProcessingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 白名单：允许查询的表
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "knowledge_table_row"
    );

    // 白名单：允许的操作
    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            "SELECT", "COUNT", "SUM", "AVG", "MIN", "MAX"
    );

    // 禁止的关键词（双重保险）
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER",
            "CREATE", "TRUNCATE", "EXEC", "EXECUTE", "UNION",
            "--", ";"
    );

    @Override
    public SqlQueryResult executeQuery(String tableId, String question, int limit) {
        try {
            // 1. 获取表格Schema
            String schema = tableProcessingService.getTableSchema(tableId);

            // 2. 构造Prompt，让LLM生成JSON查询计划
            String prompt = buildPrompt(schema, question);

            // 3. 调用LLM（这里简化，直接用模板生成）
            // 实际实现应该调用MIMO或DeepSeek
            Map<String, Object> queryPlan = generateQueryPlan(tableId, question);

            // 4. 根据JSON查询计划生成SQL
            SqlWithParams sqlWithParams = buildSql(queryPlan, tableId, limit);

            // 5. 校验SQL安全性
            validateSql(sqlWithParams.sql());

            // 6. 执行参数化查询
            return executeQuery(sqlWithParams, question);

        } catch (Exception e) {
            log.error("Text-to-SQL 查询失败: {}", e.getMessage(), e);
            return new SqlQueryResult(
                    new String[0],
                    new Object[0][],
                    0,
                    "",
                    "查询失败：" + e.getMessage()
            );
        }
    }

    /**
     * 构造Prompt
     */
    private String buildPrompt(String schema, String question) {
        return """
                你是一个 SQL 专家。根据以下表结构和用户问题，返回 JSON 格式的查询计划，而不是 SQL。

                表结构：
                %s

                用户问题：%s

                可用的操作：COUNT, SUM, AVG, MIN, MAX, SELECT
                可用的运算符：=, !=, >, <, >=, <=, LIKE, IN

                返回格式：
                {
                  "operation": "COUNT",
                  "filters": [
                    {"field": "字段名", "operator": "=", "value": "值"}
                  ],
                  "limit": 100
                }

                只返回 JSON，不要写 SQL。
                """.formatted(schema, question);
    }

    /**
     * 根据用户问题生成查询计划（简化版）
     * 实际实现应该调用LLM
     */
    private Map<String, Object> generateQueryPlan(String tableId, String question) {
        // 简化实现：直接返回一个基础查询
        // 实际实现应该调用MIMO/DeepSeek，让LLM生成JSON

        return Map.of(
                "operation", "SELECT",
                "filters", List.of(
                        Map.of("field", "table_id", "operator", "=", "value", tableId)
                ),
                "limit", 100
        );
    }

    /**
     * 根据查询计划生成SQL
     */
    private SqlWithParams buildSql(Map<String, Object> plan, String tableId, int limit) {
        String operation = (String) plan.getOrDefault("operation", "SELECT");
        List<Map<String, Object>> filters = (List<Map<String, Object>>) plan.getOrDefault("filters", List.of());
        int queryLimit = Math.min((int) plan.getOrDefault("limit", limit), 1000);

        // 校验操作类型
        if (!ALLOWED_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("不允许的操作: " + operation);
        }

        // 构建WHERE子句
        StringBuilder whereClause = new StringBuilder("WHERE table_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tableId);

        for (Map<String, Object> filter : filters) {
            String field = (String) filter.get("field");
            String operator = (String) filter.get("operator");
            Object value = filter.get("value");

            // 校验字段名（只能是预定义的字段）
            if (!isValidFieldName(field)) {
                throw new IllegalArgumentException("不允许的字段: " + field);
            }

            // 校验运算符
            if (!isValidOperator(operator)) {
                throw new IllegalArgumentException("不允许的运算符: " + operator);
            }

            whereClause.append(" AND ").append(field).append(" ").append(operator).append(" ?");
            params.add(value);
        }

        // 构建SQL
        String sql;
        if ("COUNT".equals(operation)) {
            sql = "SELECT COUNT(DISTINCT row_index) as total FROM knowledge_table_row " + whereClause;
        } else if ("SUM".equals(operation) || "AVG".equals(operation) || "MIN".equals(operation) || "MAX".equals(operation)) {
            String field = (String) plan.getOrDefault("field", "field_value");
            if (!isValidFieldName(field)) {
                field = "field_value";
            }
            sql = "SELECT " + operation + "(CAST(field_value AS DECIMAL)) as result FROM knowledge_table_row " + whereClause;
        } else {
            // SELECT
            sql = "SELECT DISTINCT row_index, field_name, field_value FROM knowledge_table_row " + whereClause;
            sql += " ORDER BY row_index, field_name";
            sql += " LIMIT ?";
            params.add(queryLimit);
        }

        return new SqlWithParams(sql, params);
    }

    /**
     * 校验SQL安全性
     */
    private void validateSql(String sql) {
        String upperSql = sql.toUpperCase();

        // 检查禁止的关键词
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                throw new IllegalArgumentException("SQL包含禁止的关键词: " + keyword);
            }
        }

        // 检查是否只包含SELECT
        if (!upperSql.trim().startsWith("SELECT")) {
            throw new IllegalArgumentException("只允许SELECT查询");
        }
    }

    /**
     * 执行参数化查询
     */
    private SqlQueryResult executeQuery(SqlWithParams sqlWithParams, String question) {
        String sql = sqlWithParams.sql();
        List<Object> params = sqlWithParams.params();

        log.info("执行SQL: {}, 参数: {}", sql, params);

        // 使用JdbcTemplate执行参数化查询
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.toArray());

        // 转换结果
        if (results.isEmpty()) {
            return new SqlQueryResult(
                    new String[0],
                    new Object[0][],
                    0,
                    sql,
                    "未找到相关数据"
            );
        }

        // 提取列名
        String[] columns = results.get(0).keySet().toArray(new String[0]);

        // 提取行数据
        Object[][] rows = new Object[results.size()][];
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            rows[i] = new Object[columns.length];
            for (int j = 0; j < columns.length; j++) {
                rows[i][j] = row.get(columns[j]);
            }
        }

        return new SqlQueryResult(
                columns,
                rows,
                results.size(),
                sql,
                "查询成功"
        );
    }

    /**
     * 校验字段名
     */
    private boolean isValidFieldName(String field) {
        Set<String> validFields = Set.of(
                "table_id", "row_index", "field_name", "field_value",
                "normalized_value", "created_at"
        );
        return validFields.contains(field);
    }

    /**
     * 校验运算符
     */
    private boolean isValidOperator(String operator) {
        Set<String> validOperators = Set.of(
                "=", "!=", ">", "<", ">=", "<=", "LIKE", "IN"
        );
        return validOperators.contains(operator);
    }

    /**
     * SQL和参数的封装
     */
    private record SqlWithParams(String sql, List<Object> params) {}
}
