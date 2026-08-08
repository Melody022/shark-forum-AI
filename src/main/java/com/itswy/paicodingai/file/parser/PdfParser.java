package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF文档解析器
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 50;

    @Override
    public String getSupportedType() {
        return "pdf";
    }

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public ParseResult parse(File file) {
        try {
            PDDocument document = Loader.loadPDF(file);
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document);
            document.close();

            // 提取标题（从文件名或第一页）
            String title = extractTitle(file.getName(), content);

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
            log.error("PDF解析失败: {}", file.getName(), e);
            return new ParseResult("PDF解析失败: " + e.getMessage());
        }
    }

    private String extractTitle(String fileName, String content) {
        // 从文件名提取标题
        String title = fileName;
        if (title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }

        // 如果内容很短，使用内容作为标题
        if (content != null && !content.isBlank() && content.length() < 100) {
            String firstLine = content.split("\n")[0].trim();
            if (!firstLine.isBlank()) {
                title = firstLine;
            }
        }

        return title;
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        // 清理文本
        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            // 尝试在句号、问号、感叹号处断开
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
