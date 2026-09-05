package com.itswy.paicodingai.file.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析管理器
 */
@Slf4j
@Component
public class DocumentParserManager {

    private final Map<String, DocumentParser> parsers = new HashMap<>();

    @Autowired
    public DocumentParserManager(List<DocumentParser> parserList) {
        for (DocumentParser parser : parserList) {
            for (String type : parser.getSupportedTypes()) {
                parsers.put(type.toLowerCase(), parser);
                log.info("注册文档解析器: {} -> {}", type, parser.getClass().getSimpleName());
            }
        }
    }

    /**
     * 解析文档
     *
     * @param file 文件
     * @param fileType 文件类型
     * @return 解析结果
     */
    public ParseResult parse(File file, String fileType) {
        String normalizedType = normalizeType(fileType);
        DocumentParser parser = parsers.get(normalizedType);

        if (parser == null) {
            log.warn("不支持的文件类型: {}", fileType);
            return new ParseResult("不支持的文件类型: " + fileType);
        }

        log.info("开始解析文档: {}, 类型: {}", file.getName(), normalizedType);
        ParseResult result = parser.parse(file);

        if (result.isSuccess()) {
            int blockCount = result.getContentBlocks() != null && !result.getContentBlocks().isEmpty()
                    ? result.getContentBlocks().size()
                    : result.getChunks() != null ? result.getChunks().size() : 0;
            log.info("文档解析成功: {}, 共{}个内容块", file.getName(), blockCount);
        } else {
            log.error("文档解析失败: {}, 错误: {}", file.getName(), result.getErrorMessage());
        }

        return result;
    }

    /**
     * 检查是否支持该文件类型
     */
    public boolean supports(String fileType) {
        return parsers.containsKey(normalizeType(fileType));
    }

    /**
     * 获取支持的文件类型列表
     */
    public List<String> getSupportedTypes() {
        return parsers.keySet().stream().toList();
    }

    private String normalizeType(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return "";
        }
        String normalized = fileType.trim().toLowerCase(java.util.Locale.ROOT);
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.equals("application/pdf")) {
            normalized = "pdf";
        } else if (normalized.contains("wordprocessingml")) {
            normalized = "docx";
        }
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }
}
