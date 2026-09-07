package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.service.OcrQualityService.OcrBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 表格重建服务
 * 从扁平文本重建表格结构
 */
@Slf4j
@Service
public class OcrTableReconstructor {

    /**
     * 从OCR扁平文本重建表格
     */
    public ReconstructedTable reconstruct(String content) {
        if (content == null || content.isBlank()) {
            return new ReconstructedTable(List.of(), List.of(), "");
        }

        // 1. 清理文本
        String cleaned = cleanText(content);

        // 2. 分割成行
        String[] lines = cleaned.split("\\n");

        // 3. 识别表头和数据
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        String title = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 尝试解析为表格行
            List<String> cells = parseLine(trimmed);

            if (headers.isEmpty()) {
                // 第一行作为表头
                headers = cells;
            } else {
                // 数据行
                rows.add(cells);
            }
        }

        // 4. 如果没有识别到表头，返回原始文本
        if (headers.isEmpty()) {
            headers = List.of("内容");
            rows = List.of(List.of(cleaned));
        }

        return new ReconstructedTable(headers, rows, title);
    }

    /**
     * 解析一行文本为单元格列表
     */
    private List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();

        // 方法1：使用空格分割（适用于英文/数字）
        // 方法2：使用正则匹配模式（适用于特定格式）
        // 方法3：使用固定列宽（适用于表格）

        // 对于财务表格，使用正则匹配数字模式
        Pattern pattern = Pattern.compile(
                "([\\u4e00-\\u9fa5a-zA-Z]+[\\u4e00-\\u9fa5a-zA-Z\\s]*|[0-9][0-9，,\\.]+[0-9]|\\d+\\.\\d+|\\d+%)"
        );

        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String cell = matcher.group().trim();
            // 清理数字中的逗号
            cell = cleanNumber(cell);
            cells.add(cell);
        }

        // 如果正则匹配失败，按空格分割
        if (cells.isEmpty()) {
            cells = List.of(line.split("\\s{2,}"));
        }

        return cells;
    }

    /**
     * 清理文本
     */
    private String cleanText(String text) {
        // 合并多行文本（处理OCR换行）
        text = text.replaceAll("([^\\d])\\n([^\\d])", "$1$2");

        // 清理特殊字符
        text = text.replace("\r", "");
        text = text.replaceAll("[\\x00-\\x1F]", "");  // 控制字符

        return text;
    }

    /**
     * 清理数字中的逗号分隔符
     */
    private String cleanNumber(String number) {
        // 移除数字中的逗号/顿号
        return number.replaceAll("[，,]", "");
    }

    /**
     * 重建后的表格
     */
    public record ReconstructedTable(
            List<String> headers,
            List<List<String>> rows,
            String title
    ) {
        public int rowCount() {
            return rows.size();
        }

        public int columnCount() {
            return headers.size();
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();

            // 表头
            sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
            sb.append("|").append("--".repeat(headers.size())).append("|\n");

            // 数据行
            for (List<String> row : rows) {
                sb.append("| ").append(String.join(" | ", row)).append(" |\n");
            }

            return sb.toString();
        }
    }
}
