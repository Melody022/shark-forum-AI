package com.itswy.paicodingai.knowledge.service;

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

    public SearchResult(String content, float score, Long docId, Long kbId, String fileMd5, Integer chunkIndex) {
        this.content = content;
        this.score = score;
        this.docId = docId;
        this.kbId = kbId;
        this.fileMd5 = fileMd5;
        this.chunkIndex = chunkIndex;
    }
}
