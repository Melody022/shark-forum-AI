package com.itswy.paicodingai.tools.result;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索结果 - Tool Calling返回的数据模型
 *
 * 用于返回搜索结果列表，前端根据此数据渲染成卡片列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    @JsonPropertyDescription("搜索关键词")
    private String keyword;

    @JsonPropertyDescription("结果总数")
    private Integer totalCount;

    @JsonPropertyDescription("搜索结果列表")
    private List<SearchItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchItem {
        @JsonPropertyDescription("结果ID")
        private Long id;

        @JsonPropertyDescription("结果标题")
        private String title;

        @JsonPropertyDescription("结果摘要")
        private String summary;

        @JsonPropertyDescription("结果类型：article/course")
        private String type;

        @JsonPropertyDescription("相关度分数 0-1")
        private Double score;

        @JsonPropertyDescription("链接")
        private String url;
    }
}
