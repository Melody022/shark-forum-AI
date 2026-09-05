package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * 搜索结果
 */
public class SearchResult {
    public String content;
    public float score;
    public Long docId;
    public Long kbId;
    public String fileMd5;
    public Integer chunkIndex;

    public Long chunkId;
    public Long parentId;
    public String fileName;
    public String sectionPath;
    public String blockType;
    public Integer pageStart;
    public Integer pageEnd;
    public String context;
    public List<String> adjacentContents;

    public SearchResult(String content, float score, Long docId, Long kbId, String fileMd5, Integer chunkIndex) {
        this.content = content;
        this.score = score;
        this.docId = docId;
        this.kbId = kbId;
        this.fileMd5 = fileMd5;
        this.chunkIndex = chunkIndex;
    }

    public SearchResult(String content, float score, Long docId, Long kbId, String fileMd5,
                        Integer chunkIndex, Long chunkId, Long parentId, String fileName,
                        String sectionPath, String blockType, Integer pageStart, Integer pageEnd,
                        String context, List<String> adjacentContents) {
        this(content, score, docId, kbId, fileMd5, chunkIndex);
        this.chunkId = chunkId;
        this.parentId = parentId;
        this.fileName = fileName;
        this.sectionPath = sectionPath;
        this.blockType = blockType;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.context = context;
        this.adjacentContents = adjacentContents;
    }
}
