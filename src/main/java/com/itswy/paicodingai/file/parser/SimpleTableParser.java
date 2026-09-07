package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化的表格解析器
 * 用于演示，从Markdown表格或简单格式解析表格
 */
@Slf4j
@Component
public class SimpleTableParser {

    /**
     * 从Markdown表格文本解析表格
     */
    public TableData parseMarkdownTable(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String[] lines = text.split("\\n");
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("|--") || trimmed.startsWith("| ---")) {
                continue;  // 跳过分隔符
            }

            if (trimmed.startsWith("|")) {
                // 解析表格行
                String[] cells = trimmed.split("\\|");
                List<String> row = new ArrayList<>();
                for (String cell : cells) {
                    String value = cell.trim();
                    if (!value.isEmpty()) {
                        row.add(value);
                    }
                }

                if (!row.isEmpty()) {
                    if (headers.isEmpty()) {
                        headers = row;  // 第一行是表头
                    } else {
                        rows.add(row);
                    }
                }
            }
        }

        if (headers.isEmpty()) {
            return null;
        }

        return new TableData(headers, rows, "Markdown表格");
    }

    /**
     * 从简单格式解析表格（列用空格或Tab分隔）
     */
    public TableData parseSimpleTable(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String[] lines = text.split("\\n");
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 尝试用制表符或多个空格分割
            String[] cells = trimmed.split("\\t|\\s{2,}");
            List<String> row = new ArrayList<>();
            for (String cell : cells) {
                String value = cell.trim();
                if (!value.isEmpty()) {
                    row.add(value);
                }
            }

            if (!row.isEmpty()) {
                if (headers.isEmpty()) {
                    headers = row;
                } else {
                    rows.add(row);
                }
            }
        }

        if (headers.isEmpty()) {
            return null;
        }

        return new TableData(headers, rows, "简单表格");
    }

    /**
     * 检测文本中是否包含表格
     */
    public boolean hasTable(String text) {
        if (text == null) {
            return false;
        }

        // 检测Markdown表格特征
        if (text.contains("|") && text.contains("--")) {
            return true;
        }

        // 检测多行表格特征（多行有相同数量的列）
        String[] lines = text.split("\\n");
        if (lines.length < 3) {
            return false;
        }

        int columnCount = 0;
        for (String line : lines) {
            String[] cells = line.trim().split("\\t|\\s{2,}");
            if (cells.length > 1) {
                if (columnCount == 0) {
                    columnCount = cells.length;
                } else if (cells.length != columnCount) {
                    return false;
                }
            }
        }

        return columnCount > 1;
    }

    /**
     * 表格数据
     */
    public record TableData(List<String> headers, List<List<String>> rows, String source) {
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
