package com.itswy.paicodingai.file.parser;

import java.util.List;

/**
 * 文档解析结果
 */
public class ParseResult {

    /** 文档标题 */
    private String title;

    /** 文档内容（纯文本） */
    private String content;

    /** 文本分块列表 */
    private List<String> chunks;

    /** 文档类型 */
    private String fileType;

    /** 文档大小 */
    private Long fileSize;

    /** 解析是否成功 */
    private boolean success;

    /** 错误信息（如果有） */
    private String errorMessage;

    public ParseResult() {
        this.success = true;
    }

    public ParseResult(String title, String content, List<String> chunks, String fileType, Long fileSize) {
        this.title = title;
        this.content = content;
        this.chunks = chunks;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.success = true;
    }

    public ParseResult(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getChunks() { return chunks; }
    public void setChunks(List<String> chunks) { this.chunks = chunks; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
