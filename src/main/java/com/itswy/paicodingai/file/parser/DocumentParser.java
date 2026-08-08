package com.itswy.paicodingai.file.parser;

import java.io.File;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 支持的文件类型
     */
    String getSupportedType();

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
