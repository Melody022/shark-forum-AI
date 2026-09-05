package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Excel 表格解析器。表头会重复写入每个可检索块，保证单独召回一行时仍然有字段语义。
 */
@Slf4j
@Component
public class ExcelParser implements DocumentParser {

    private static final int ROWS_PER_BLOCK = 20;

    @Override
    public String getSupportedType() {
        return "xlsx";
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of("xlsx", "xls");
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && getSupportedTypes().contains(fileType.toLowerCase(Locale.ROOT));
    }

    @Override
    public ParseResult parse(File file) {
        try (FileInputStream input = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter();
            List<ContentBlock> blocks = new ArrayList<>();
            StringBuilder allContent = new StringBuilder();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                List<List<String>> rows = new ArrayList<>();
                int maxColumns = 0;
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    int lastCell = Math.max(row.getLastCellNum(), 0);
                    for (int i = 0; i < lastCell; i++) {
                        Cell cell = row.getCell(i);
                        values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    while (!values.isEmpty() && values.get(values.size() - 1).isBlank()) {
                        values.remove(values.size() - 1);
                    }
                    if (!values.isEmpty()) {
                        maxColumns = Math.max(maxColumns, values.size());
                        rows.add(values);
                    }
                }
                if (rows.isEmpty()) {
                    continue;
                }

                List<String> headers = rows.get(0);
                for (int start = 1; start < rows.size(); start += ROWS_PER_BLOCK) {
                    int end = Math.min(start + ROWS_PER_BLOCK, rows.size());
                    StringBuilder content = new StringBuilder();
                    content.append("工作表：").append(sheet.getSheetName()).append('\n');
                    content.append("表头：").append(String.join(" | ", headers)).append('\n');
                    content.append("字段：").append(String.join(" | ", headers)).append('\n');
                    for (int rowIndex = start; rowIndex < end; rowIndex++) {
                        List<String> values = rows.get(rowIndex);
                        content.append("行 ").append(rowIndex).append("：")
                                .append(String.join(" | ", values)).append('\n');
                    }

                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("sheetName", sheet.getSheetName());
                    metadata.put("headers", headers);
                    metadata.put("rowStart", start);
                    metadata.put("rowEnd", end - 1);
                    metadata.put("rowCount", end - start);
                    metadata.put("columnCount", maxColumns);
                    metadata.put("structuredTable", true);

                    String blockText = content.toString().trim();
                    blocks.add(ContentBlock.builder()
                            .blockId("sheet-" + sheetIndex + "-block-" + blocks.size())
                            .type(BlockType.TABLE)
                            .content(blockText)
                            .sectionPath(sheet.getSheetName())
                            .metadata(metadata)
                            .build());
                    if (!allContent.isEmpty()) {
                        allContent.append("\n\n");
                    }
                    allContent.append(blockText);
                }
            }

            return new ParseResult(fileNameWithoutExtension(file.getName()), allContent.toString(),
                    blocks, getSupportedType(), file.length(), true);
        } catch (Exception e) {
            log.error("Excel 解析失败: {}", file.getName(), e);
            return new ParseResult("Excel 解析失败: " + e.getMessage());
        }
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
