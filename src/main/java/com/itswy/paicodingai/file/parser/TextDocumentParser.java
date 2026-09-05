package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT、Markdown 和 HTML 的结构化文本解析器。
 */
@Slf4j
@Component
public class TextDocumentParser implements DocumentParser {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(第\\s*\\d+\\s*[章节篇]|\\d+(?:\\.\\d+){0,5})[\\s:：]+(.+)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+).+");

    @Override
    public String getSupportedType() {
        return "txt";
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of("txt", "md", "markdown", "html", "htm");
    }

    @Override
    public boolean supports(String fileType) {
        if (fileType == null) {
            return false;
        }
        String normalized = normalize(fileType);
        return getSupportedTypes().contains(normalized);
    }

    @Override
    public ParseResult parse(File file) {
        try {
            String fileType = extension(file.getName());
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String content = "html".equals(fileType) || "htm".equals(fileType)
                    ? htmlToText(raw)
                    : raw;

            ParsedText parsed = parseBlocks(content);
            String title = parsed.title();
            if (title == null || title.isBlank()) {
                title = fileNameWithoutExtension(file.getName());
            }

            return new ParseResult(title, content, parsed.blocks(), fileType, file.length(), true);
        } catch (Exception e) {
            log.error("文本文件解析失败: {}", file.getName(), e);
            return new ParseResult("文本文件解析失败: " + e.getMessage());
        }
    }

    private ParsedText parseBlocks(String source) {
        String normalized = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        List<ContentBlock> blocks = new ArrayList<>();
        Deque<String> sectionNames = new ArrayDeque<>();
        Deque<Integer> sectionLevels = new ArrayDeque<>();
        String currentSectionId = null;
        String title = null;
        StringBuilder paragraph = new StringBuilder();
        StringBuilder list = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean inCode = false;
        int codeStartLine = 0;
        int blockNo = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\f", "").stripTrailing();
            String trimmed = line.trim();

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (inCode) {
                    if (!code.isEmpty()) {
                        blocks.add(ContentBlock.builder()
                                .blockId("block-" + (++blockNo))
                                .type(BlockType.CODE)
                                .content(code.toString().stripTrailing())
                                .pageStart(0)
                                .pageEnd(0)
                                .sectionPath(sectionPath(sectionNames))
                                .parentId(currentSectionId)
                                .metadata(java.util.Map.of("startLine", codeStartLine))
                                .build());
                    }
                    code.setLength(0);
                    inCode = false;
                } else {
                    flushParagraph(blocks, paragraph, currentSectionId, sectionPath(sectionNames), ++blockNo);
                    blockNo = blocks.size();
                    flushList(blocks, list, currentSectionId, sectionPath(sectionNames), ++blockNo);
                    blockNo = blocks.size();
                    inCode = true;
                    codeStartLine = i + 1;
                }
                continue;
            }

            if (inCode) {
                code.append(line).append('\n');
                continue;
            }

            Heading heading = parseHeading(trimmed);
            if (heading != null) {
                flushParagraph(blocks, paragraph, currentSectionId, sectionPath(sectionNames), ++blockNo);
                blockNo = blocks.size();
                flushList(blocks, list, currentSectionId, sectionPath(sectionNames), ++blockNo);
                blockNo = blocks.size();

                while (!sectionLevels.isEmpty() && sectionLevels.peek() >= heading.level()) {
                    sectionLevels.pop();
                    sectionNames.pop();
                }
                sectionLevels.push(heading.level());
                sectionNames.push(heading.text());
                currentSectionId = "section-" + (blocks.size() + 1);
                String sectionPath = sectionPath(sectionNames);
                blocks.add(ContentBlock.builder()
                        .blockId("block-" + (++blockNo))
                        .type(BlockType.HEADING)
                        .content(heading.text())
                        .pageStart(0)
                        .pageEnd(0)
                        .sectionPath(sectionPath)
                        .parentId(currentSectionId)
                        .searchable(false)
                        .metadata(java.util.Map.of("level", heading.level()))
                        .build());
                if (title == null) {
                    title = heading.text();
                }
                continue;
            }

            if (LIST_ITEM.matcher(line).matches()) {
                flushParagraph(blocks, paragraph, currentSectionId, sectionPath(sectionNames), ++blockNo);
                blockNo = blocks.size();
                if (!list.isEmpty()) {
                    list.append('\n');
                }
                list.append(line.trim());
                continue;
            }

            if (!list.isEmpty()) {
                flushList(blocks, list, currentSectionId, sectionPath(sectionNames), ++blockNo);
                blockNo = blocks.size();
            }

            if (trimmed.isEmpty()) {
                flushParagraph(blocks, paragraph, currentSectionId, sectionPath(sectionNames), ++blockNo);
                blockNo = blocks.size();
                flushList(blocks, list, currentSectionId, sectionPath(sectionNames), ++blockNo);
                blockNo = blocks.size();
                continue;
            }

            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }

        if (inCode && !code.isEmpty()) {
            blocks.add(ContentBlock.builder()
                    .blockId("block-" + (++blockNo))
                    .type(BlockType.CODE)
                    .content(code.toString().stripTrailing())
                    .pageStart(0)
                    .pageEnd(0)
                    .sectionPath(sectionPath(sectionNames))
                    .parentId(currentSectionId)
                    .build());
        }
        flushParagraph(blocks, paragraph, currentSectionId, sectionPath(sectionNames), ++blockNo);
        blockNo = blocks.size();
        flushList(blocks, list, currentSectionId, sectionPath(sectionNames), ++blockNo);

        return new ParsedText(title, blocks);
    }

    private void flushParagraph(List<ContentBlock> blocks, StringBuilder paragraph,
                                 String parentId, String sectionPath, int ignoredBlockNo) {
        String content = paragraph.toString().trim();
        if (!content.isBlank()) {
            blocks.add(ContentBlock.builder()
                    .blockId("block-" + (blocks.size() + 1))
                    .type(BlockType.TEXT)
                    .content(content)
                    .pageStart(0)
                    .pageEnd(0)
                    .sectionPath(sectionPath)
                    .parentId(parentId)
                    .build());
        }
        paragraph.setLength(0);
    }

    private void flushList(List<ContentBlock> blocks, StringBuilder list,
                           String parentId, String sectionPath, int ignoredBlockNo) {
        String content = list.toString().trim();
        if (!content.isBlank()) {
            blocks.add(ContentBlock.builder()
                    .blockId("block-" + (blocks.size() + 1))
                    .type(BlockType.LIST)
                    .content(content)
                    .pageStart(0)
                    .pageEnd(0)
                    .sectionPath(sectionPath)
                    .parentId(parentId)
                    .build());
        }
        list.setLength(0);
    }

    private Heading parseHeading(String line) {
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return new Heading(markdown.group(2).trim(), markdown.group(1).length());
        }
        Matcher numbered = NUMBERED_HEADING.matcher(line);
        if (numbered.matches()) {
            String prefix = numbered.group(1);
            int level = prefix.startsWith("第") ? 1 : Math.max(1, prefix.split("\\.").length);
            return new Heading(line, level);
        }
        return null;
    }

    private String htmlToText(String html) {
        String converted = html == null ? "" : html;
        converted = converted.replaceAll("(?is)<h1[^>]*>", "\n# ")
                .replaceAll("(?is)<h2[^>]*>", "\n## ")
                .replaceAll("(?is)<h3[^>]*>", "\n### ")
                .replaceAll("(?is)<h4[^>]*>", "\n#### ")
                .replaceAll("(?is)<h5[^>]*>", "\n##### ")
                .replaceAll("(?is)<h6[^>]*>", "\n###### ")
                .replaceAll("(?is)</h[1-6]>", "\n")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p\\s*>", "\n\n")
                .replaceAll("(?is)<li[^>]*>", "\n- ")
                .replaceAll("(?is)</li\\s*>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        return converted;
    }

    private String sectionPath(Deque<String> sections) {
        if (sections.isEmpty()) {
            return "";
        }
        List<String> ordered = new ArrayList<>(sections);
        java.util.Collections.reverse(ordered);
        return String.join(" > ", ordered);
    }

    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return normalize(dot >= 0 ? name.substring(dot + 1) : "txt");
    }

    private String normalize(String type) {
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private record Heading(String text, int level) {}
    private record ParsedText(String title, List<ContentBlock> blocks) {}
}
