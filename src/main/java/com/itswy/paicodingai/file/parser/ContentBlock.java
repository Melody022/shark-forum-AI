package com.itswy.paicodingai.file.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不同格式文档统一后的内容块。
 *
 * <p>解析器只负责还原结构和来源，后续切片不再直接依赖文件格式。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentBlock {

    private String blockId;
    private BlockType type;
    private String content;
    private int pageStart;
    private int pageEnd;
    private String sectionPath;
    private String parentId;

    private Float x;
    private Float y;
    private Float width;
    private Float height;

    /** 原始来源路径，图片块会使用它保留原图位置。 */
    private String sourcePath;

    /** 表头、OCR 文本、置信度等格式相关信息。 */
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /** 是否适合进入文本检索索引。 */
    @Builder.Default
    private boolean searchable = true;

    public static ContentBlock text(String blockId, String content, int page, String sectionPath, String parentId) {
        return ContentBlock.builder()
                .blockId(blockId)
                .type(BlockType.TEXT)
                .content(content)
                .pageStart(page)
                .pageEnd(page)
                .sectionPath(sectionPath)
                .parentId(parentId)
                .build();
    }
}
