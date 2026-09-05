package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Word 文档解析器，保留标题层级、段落、代码和表格边界。
 */
@Slf4j
@Component
public class WordParser implements DocumentParser {

    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+).+");

    @Override
    public String getSupportedType() {
        return "docx";
    }

    @Override
    public List<String> getSupportedTypes() {
        // .doc 是旧二进制格式，需要 poi-scratchpad；当前项目只声明支持 OOXML。
        return List.of("docx");
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && getSupportedTypes().contains(fileType.toLowerCase(Locale.ROOT));
    }

    @Override
    public ParseResult parse(File file) {
        try (FileInputStream input = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(input)) {
            List<ContentBlock> blocks = new ArrayList<>();
            Deque<String> sectionNames = new ArrayDeque<>();
            Deque<Integer> sectionLevels = new ArrayDeque<>();
            String currentSectionId = null;
            String title = null;

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    Integer level = headingLevel(paragraph);
                    if (level != null) {
                        while (!sectionLevels.isEmpty() && sectionLevels.peek() >= level) {
                            sectionLevels.pop();
                            sectionNames.pop();
                        }
                        sectionLevels.push(level);
                        sectionNames.push(text.trim());
                        currentSectionId = "section-" + (blocks.size() + 1);
                        blocks.add(ContentBlock.builder()
                                .blockId("block-" + (blocks.size() + 1))
                                .type(BlockType.HEADING)
                                .content(text.trim())
                                .sectionPath(sectionPath(sectionNames))
                                .parentId(currentSectionId)
                                .searchable(false)
                                .metadata(Map.of("level", level))
                                .build());
                        if (title == null) {
                            title = text.trim();
                        }
                        continue;
                    }

                    String trimmed = text.trim();
                    BlockType type = isCodeParagraph(paragraph) ? BlockType.CODE
                            : LIST_PATTERN.matcher(trimmed).matches() ? BlockType.LIST : BlockType.TEXT;
                    blocks.add(ContentBlock.builder()
                            .blockId("block-" + (blocks.size() + 1))
                            .type(type)
                            .content(trimmed)
                            .sectionPath(sectionPath(sectionNames))
                            .parentId(currentSectionId)
                            .build());
                } else if (element instanceof XWPFTable table) {
                    addTableBlock(blocks, table, currentSectionId, sectionPath(sectionNames));
                }
            }

            String content = blocks.stream().map(ContentBlock::getContent)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((left, right) -> left + "\n\n" + right).orElse("");
            if (title == null || title.isBlank()) {
                title = fileNameWithoutExtension(file.getName());
            }
            return new ParseResult(title, content, blocks, getSupportedType(), file.length(), true);
        } catch (Exception e) {
            log.error("Word 文档解析失败: {}", file.getName(), e);
            return new ParseResult("Word 文档解析失败: " + e.getMessage());
        }
    }

    private void addTableBlock(List<ContentBlock> blocks, XWPFTable table,
                               String parentId, String sectionPath) {
        List<String> headers = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        List<XWPFTableRow> tableRows = table.getRows();
        if (!tableRows.isEmpty()) {
            headers = cells(tableRows.get(0));
            for (int i = 1; i < tableRows.size(); i++) {
                rows.add(String.join(" | ", cells(tableRows.get(i))));
            }
        }
        StringBuilder content = new StringBuilder();
        if (!headers.isEmpty()) {
            content.append(String.join(" | ", headers)).append('\n');
            content.append(String.join(" | ", headers.stream().map(value -> "---").toList())).append('\n');
        }
        rows.forEach(row -> content.append(row).append('\n'));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("headers", headers);
        metadata.put("rowCount", rows.size());
        blocks.add(ContentBlock.builder()
                .blockId("block-" + (blocks.size() + 1))
                .type(BlockType.TABLE)
                .content(content.toString().trim())
                .sectionPath(sectionPath)
                .parentId(parentId)
                .metadata(metadata)
                .build());
    }

    private List<String> cells(XWPFTableRow row) {
        List<String> values = new ArrayList<>();
        for (XWPFTableCell cell : row.getTableCells()) {
            values.add(cell.getText().replaceAll("\\s+", " ").trim());
        }
        return values;
    }

    private Integer headingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return null;
        }
        String lower = style.toLowerCase(Locale.ROOT);
        if (lower.startsWith("heading") || lower.startsWith("标题")) {
            String digits = lower.replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                return Math.max(1, Math.min(6, Integer.parseInt(digits)));
            }
            return 1;
        }
        return null;
    }

    private boolean isCodeParagraph(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return false;
        }
        String lower = style.toLowerCase(Locale.ROOT);
        return lower.contains("code") || lower.contains("代码") || lower.contains("pre")
                || lower.contains("source");
    }

    private String sectionPath(Deque<String> sections) {
        if (sections.isEmpty()) {
            return "";
        }
        List<String> ordered = new ArrayList<>(sections);
        java.util.Collections.reverse(ordered);
        return String.join(" > ", ordered);
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
