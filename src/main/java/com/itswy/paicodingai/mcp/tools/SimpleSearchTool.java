package com.itswy.paicodingai.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP工具示例 - 简单的搜索工具
 *
 * 通过@Tool注解定义，可以被MCP协议发现和调用
 */
@Component
public class SimpleSearchTool {

    /**
     * 搜索工具
     *
     * @param query 搜索关键词
     * @return 搜索结果
     */
    @Tool(description = "搜索互联网上的信息，返回相关结果")
    public String search(@ToolParam(description = "搜索关键词") String query) {
        // 模拟搜索结果
        String result = String.format(
                "搜索结果 for '%s'：\n" +
                "1. 技术派论坛 - 最新文章\n" +
                "2. Spring Boot教程 - 入门指南\n" +
                "3. Java编程技巧 - 实战经验",
                query
        );
        return result;
    }

    /**
     * 获取当前时间
     *
     * @return 当前时间字符串
     */
    @Tool(description = "获取当前服务器时间")
    public String getCurrentTime() {
        return "当前时间: " + java.time.LocalDateTime.now().toString();
    }

    /**
     * 简单的数学计算
     *
     * @param expression 数学表达式
     * @return 计算结果
     */
    @Tool(description = "计算数学表达式，如 '2+3*4'")
    public String calculate(@ToolParam(description = "数学表达式") String expression) {
        try {
            // 简单的表达式计算（仅支持基础运算）
            // 注意：生产环境应该使用安全的表达式解析器
            String result = evaluateExpression(expression);
            return "计算结果: " + expression + " = " + result;
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    private String evaluateExpression(String expression) {
        // 简单的表达式计算（示例）
        // 实际应该使用安全的表达式解析库
        try {
            // 移除空格
            expression = expression.replaceAll("\\s+", "");

            // 简单的加减乘除计算
            double result = evaluate(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            throw new RuntimeException("无法解析表达式: " + expression);
        }
    }

    private double evaluate(String expression) {
        // 简单的递归下降解析器（仅用于示例）
        return new Object() {
            int pos = 0;

            double parse() {
                double result = parseTerm();
                while (pos < expression.length()) {
                    char op = expression.charAt(pos);
                    if (op == '+' || op == '-') {
                        pos++;
                        double term = parseTerm();
                        if (op == '+') result += term;
                        else result -= term;
                    } else {
                        break;
                    }
                }
                return result;
            }

            double parseTerm() {
                double result = parseFactor();
                while (pos < expression.length()) {
                    char op = expression.charAt(pos);
                    if (op == '*' || op == '/') {
                        pos++;
                        double factor = parseFactor();
                        if (op == '*') result *= factor;
                        else result /= factor;
                    } else {
                        break;
                    }
                }
                return result;
            }

            double parseFactor() {
                if (pos < expression.length() && expression.charAt(pos) == '(') {
                    pos++; // 跳过 '('
                    double result = parse();
                    if (pos < expression.length() && expression.charAt(pos) == ')') {
                        pos++; // 跳过 ')'
                    }
                    return result;
                }

                // 解析数字
                int start = pos;
                while (pos < expression.length() &&
                       (Character.isDigit(expression.charAt(pos)) || expression.charAt(pos) == '.')) {
                    pos++;
                }
                return Double.parseDouble(expression.substring(start, pos));
            }
        }.parse();
    }
}
