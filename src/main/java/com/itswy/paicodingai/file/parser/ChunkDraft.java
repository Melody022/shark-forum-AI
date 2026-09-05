package com.itswy.paicodingai.file.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 层级切片器生成的待持久化 Chunk。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDraft {

    public static final String ROLE_PARENT = "PARENT";
    public static final String ROLE_CHILD = "CHILD";

    private String role;
    private String parentKey;
    private String sourceBlockId;
    private BlockType blockType;
    private String content;
    private int pageStart;
    private int pageEnd;
    private String sectionPath;
    private boolean searchable;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
