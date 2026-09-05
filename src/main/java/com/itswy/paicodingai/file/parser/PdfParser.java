package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PDF 页面解析器。保留页面边界，并对常见编号标题做启发式识别。
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    private final OcrService ocrService;

    private final LiteParseDocumentParser liteParseDocumentParser;

    @Autowired
    public PdfParser(OcrService ocrService, LiteParseDocumentParser liteParseDocumentParser) {
        this.ocrService = ocrService;
        this.liteParseDocumentParser = liteParseDocumentParser;
    }

    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(第\\s*\\d+\\s*[章节篇]|[一二三四五六七八九十]+[、.]|\\(?[一二三四五六七八九十]+\\)|\\d+(?:\\.\\d+){0,5})[\\s:：].+$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+).+");

    @Override
    public String getSupportedType() {
        return "pdf";
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public ParseResult parse(File file) {
        ParseResult liteParseResult = liteParseDocumentParser.parse(file);
        if (liteParseResult.isSuccess() && liteParseResult.getContentBlocks() != null
                && !liteParseResult.getContentBlocks().isEmpty()) {
            return liteParseResult;
        }
        try (PDDocument document = Loader.loadPDF(file)) {
            List<ContentBlock> blocks = new ArrayList<>();
            Deque<String> sectionNames = new ArrayDeque<>();
            Deque<Integer> sectionLevels = new ArrayDeque<>();
            String currentSectionId = null;
            StringBuilder allText = new StringBuilder();
            String title = null;
            PDFRenderer renderer = new PDFRenderer(document);

            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = normalizePageText(stripper.getText(document));
                if (pageText.isBlank()) {
                    ContentBlock ocrBlock = parseScannedPage(file, renderer, page - 1);
                    if (ocrBlock != null) {
                        blocks.add(ocrBlock);
                    }
                    continue;
                }
                if (!allText.isEmpty()) {
                    allText.append('\n');
                }
                allText.append(pageText);

                String[] lines = pageText.split("\\n");
                StringBuilder paragraph = new StringBuilder();
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.isBlank()) {
                        flushText(blocks, paragraph, page, currentSectionId, sectionPath(sectionNames));
                        continue;
                    }

                    if (isHeading(line)) {
                        flushText(blocks, paragraph, page, currentSectionId, sectionPath(sectionNames));
                        int level = headingLevel(line);
                        while (!sectionLevels.isEmpty() && sectionLevels.peek() >= level) {
                            sectionLevels.pop();
                            sectionNames.pop();
                        }
                        sectionLevels.push(level);
                        sectionNames.push(line);
                        currentSectionId = "section-" + (blocks.size() + 1);
                        blocks.add(ContentBlock.builder()
                                .blockId("block-" + (blocks.size() + 1))
                                .type(BlockType.HEADING)
                                .content(line)
                                .pageStart(page)
                                .pageEnd(page)
                                .sectionPath(sectionPath(sectionNames))
                                .parentId(currentSectionId)
                                .searchable(false)
                                .metadata(Map.of("level", level))
                                .build());
                        if (title == null) {
                            title = line;
                        }
                        continue;
                    }

                    if (LIST_ITEM.matcher(line).matches()) {
                        flushText(blocks, paragraph, page, currentSectionId, sectionPath(sectionNames));
                        ContentBlock listBlock = ContentBlock.builder()
                                .blockId("block-" + (blocks.size() + 1))
                                .type(BlockType.LIST)
                                .content(line)
                                .pageStart(page)
                                .pageEnd(page)
                                .sectionPath(sectionPath(sectionNames))
                                .parentId(currentSectionId)
                                .build();
                        blocks.add(listBlock);
                        continue;
                    }

                    if (!paragraph.isEmpty()) {
                        paragraph.append(' ');
                    }
                    paragraph.append(line);
                }
                flushText(blocks, paragraph, page, currentSectionId, sectionPath(sectionNames));
            }

            if (blocks.isEmpty()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("ocrRequired", true);
                blocks.add(ContentBlock.builder()
                        .blockId("page-1-image")
                        .type(BlockType.IMAGE)
                        .content("该 PDF 没有可提取的文本层，需要 OCR 后再建立文本检索索引。")
                        .pageStart(1)
                        .pageEnd(document.getNumberOfPages())
                        .sourcePath(file.getAbsolutePath())
                        .metadata(metadata)
                        .searchable(false)
                        .build());
            }

            if (title == null || title.isBlank()) {
                title = fileNameWithoutExtension(file.getName());
            }
            return new ParseResult(title, allText.toString(), blocks, getSupportedType(), file.length(), true);
        } catch (Exception e) {
            log.error("PDF 解析失败: {}", file.getName(), e);
            return new ParseResult("PDF 解析失败: " + e.getMessage());
        }
    }

    private void flushText(List<ContentBlock> blocks, StringBuilder paragraph, int page,
                           String parentId, String sectionPath) {
        String text = paragraph.toString().trim();
        if (!text.isBlank()) {
            blocks.add(ContentBlock.builder()
                    .blockId("block-" + (blocks.size() + 1))
                    .type(BlockType.TEXT)
                    .content(text)
                    .pageStart(page)
                    .pageEnd(page)
                    .sectionPath(sectionPath)
                    .parentId(parentId)
                    .build());
        }
        paragraph.setLength(0);
    }

    private ContentBlock parseScannedPage(File source, PDFRenderer renderer, int pageIndex) {
        File image = null;
        try {
            image = File.createTempFile("rag-pdf-page-", ".png");
            BufferedImage rendered = renderer.renderImageWithDPI(pageIndex, 150);
            javax.imageio.ImageIO.write(rendered, "png", image);
            OcrService.OcrResult ocr = ocrService.recognize(image);
            String text = ocr == null || ocr.text() == null ? "" : ocr.text().trim();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ocrText", text);
            metadata.put("ocrConfidence", ocr == null ? 0F : ocr.confidence());
            metadata.put("sourcePage", pageIndex + 1);
            if (text.isBlank()) {
                metadata.put("ocrRequired", true);
            }
            return ContentBlock.builder()
                    .blockId("page-" + (pageIndex + 1) + "-image")
                    .type(BlockType.IMAGE)
                    .content(text.isBlank()
                            ? "第" + (pageIndex + 1) + "页为扫描图片，尚未识别出文本。"
                            : "扫描页 OCR 文本：\n" + text)
                    .pageStart(pageIndex + 1)
                    .pageEnd(pageIndex + 1)
                    .sourcePath(source.getAbsolutePath())
                    .metadata(metadata)
                    .searchable(!text.isBlank())
                    .build();
        } catch (Exception e) {
            log.warn("扫描 PDF 页面 OCR 失败: file={}, page={}", source.getName(), pageIndex + 1, e);
            return ContentBlock.builder()
                    .blockId("page-" + (pageIndex + 1) + "-image")
                    .type(BlockType.IMAGE)
                    .content("第" + (pageIndex + 1) + "页为扫描图片，OCR 处理失败。")
                    .pageStart(pageIndex + 1)
                    .pageEnd(pageIndex + 1)
                    .sourcePath(source.getAbsolutePath())
                    .metadata(Map.of("ocrRequired", true))
                    .searchable(false)
                    .build();
        } finally {
            if (image != null && image.exists() && !image.delete()) {
                image.deleteOnExit();
            }
        }
    }

    private String normalizePageText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean isHeading(String line) {
        return NUMBERED_HEADING.matcher(line).matches();
    }

    private int headingLevel(String line) {
        if (line.startsWith("第")) {
            return 1;
        }
        if (line.matches("^[一二三四五六七八九十]+[、.]?.*$") || line.matches("^\\([一二三四五六七八九十]+\\).*$")) {
            return 1;
        }
        String prefix = line.split("[\\s:：]", 2)[0];
        return Math.max(1, Math.min(6, prefix.split("\\.").length));
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
