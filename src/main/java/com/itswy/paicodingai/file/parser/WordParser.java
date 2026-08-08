package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Word文档解析器（.docx）
 */
@Slf4j
@Component
public class WordParser implements DocumentParser {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 50;

    @Override
    public String getSupportedType() {
        return "docx";
    }

    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType) || "doc".equalsIgnoreCase(fileType);
    }

    @Override
    public ParseResult parse(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            XWPFDocument document = new XWPFDocument(fis);

            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String content = extractor.getText();
            document.close();
            fis.close();

            // 提取标题
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
            log.error("Word文档解析失败: {}", file.getName(), e);
            return new ParseResult("Word文档解析失败: " + e.getMessage());
        }
    }

    private String extractTitle(String fileName, String content) {
        String title = fileName;
        if (title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }

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
