package com.itswy.paicodingai.tools.result;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章信息 - Tool Calling返回的数据模型
 *
 * 用于返回文章详情，前端根据此数据渲染成卡片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleInfo {

    @JsonPropertyDescription("文章ID")
    private Long id;

    @JsonPropertyDescription("文章标题")
    private String title;

    @JsonPropertyDescription("文章摘要")
    private String summary;

    @JsonPropertyDescription("文章内容")
    private String content;

    @JsonPropertyDescription("文章分类")
    private String category;

    @JsonPropertyDescription("作者")
    private String author;

    @JsonPropertyDescription("发布时间")
    private String publishTime;

    @JsonPropertyDescription("浏览量")
    private Long viewCount;

    @JsonPropertyDescription("点赞数")
    private Long likeCount;

    @JsonPropertyDescription("文章标签，多个用逗号分隔")
    private String tags;
}
