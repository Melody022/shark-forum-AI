package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel文档解析器
 */
@Slf4j
@Component
public class ExcelParser implements DocumentParser {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 50;

    @Override
    public String getSupportedType() {
        return "xlsx";
    }

    @Override
    public boolean supports(String fileType) {
        return "xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType);
    }

    @Override
    public ParseResult parse(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            Workbook workbook = new XSSFWorkbook(fis);

            StringBuilder contentBuilder = new StringBuilder();

            // 遍历所有工作表
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                contentBuilder.append("工作表: ").append(sheet.getSheetName()).append("\n\n");

                // 遍历所有行
                for (Row row : sheet) {
                    List<String> cellValues = new ArrayList<>();
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        Cell cell = row.getCell(j);
                        String cellValue = getCellValue(cell);
                        cellValues.add(cellValue);
                    }
                    contentBuilder.append(String.join(" | ", cellValues)).append("\n");
                }
                contentBuilder.append("\n");
            }

            workbook.close();
            fis.close();

            String content = contentBuilder.toString();
            String title = extractTitle(file.getName());

            // 文本分块
            List<String> chunks = splitText(content);

            return new ParseResult(
                    title,
                    content,
                    chunks,
                    getSupportedType(),
                    file.length()
            );

        } catch (Exception e) {
            log.error("Excel解析失败: {}", file.getName(), e);
            return new ParseResult("Excel解析失败: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return "";
        }
    }

    private String extractTitle(String fileName) {
        String title = fileName;
        if (title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }
        return title;
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            if (end < text.length()) {
                int lastSentence = findLastSentenceEnd(text, start, end);
                if (lastSentence > start) {
                    end = lastSentence;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            start = end - CHUNK_OVERLAP;
            if (start >= text.length()) {
                break;
            }
        }

        return chunks;
    }

    private int findLastSentenceEnd(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                return i + 1;
            }
        }
        return -1;
    }
}
