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
            parsers.put(parser.getSupportedType().toLowerCase(), parser);
            log.info("注册文档解析器: {}", parser.getSupportedType());
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
        DocumentParser parser = parsers.get(fileType.toLowerCase());

        if (parser == null) {
            log.warn("不支持的文件类型: {}", fileType);
            return new ParseResult("不支持的文件类型: " + fileType);
        }

        log.info("开始解析文档: {}, 类型: {}", file.getName(), fileType);
        ParseResult result = parser.parse(file);

        if (result.isSuccess()) {
            log.info("文档解析成功: {}, 共{}个分块", file.getName(),
                    result.getChunks() != null ? result.getChunks().size() : 0);
        } else {
            log.error("文档解析失败: {}, 错误: {}", file.getName(), result.getErrorMessage());
        }

        return result;
    }

    /**
     * 检查是否支持该文件类型
     */
    public boolean supports(String fileType) {
        return parsers.containsKey(fileType.toLowerCase());
    }

    /**
     * 获取支持的文件类型列表
     */
    public List<String> getSupportedTypes() {
        return parsers.keySet().stream().toList();
    }
}
