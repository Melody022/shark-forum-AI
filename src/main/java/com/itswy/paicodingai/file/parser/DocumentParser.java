package com.itswy.paicodingai.file.parser;

import java.io.File;
import java.util.List;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 支持的文件类型
     */
    String getSupportedType();

    /**
     * 返回该解析器支持的全部扩展名。默认保持旧解析器的单类型行为。
     */
    default List<String> getSupportedTypes() {
        return List.of(getSupportedType());
    }

    /**
     * 解析文档
     *
     * @param file 文档文件
     * @return 解析结果
     */
    ParseResult parse(File file);

    /**
     * 检查是否支持该文件类型
     */
    boolean supports(String fileType);
}
