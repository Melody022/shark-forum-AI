package com.itswy.paicodingai.tools.result;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教程信息 - Tool Calling返回的数据模型
 *
 * 用于返回教程详情，前端根据此数据渲染成卡片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfo {

    @JsonPropertyDescription("教程ID")
    private Long id;

    @JsonPropertyDescription("教程名称")
    private String name;

    @JsonPropertyDescription("教程简介")
    private String description;

    @JsonPropertyDescription("教程价格，单位：元")
    private Double price;

    @JsonPropertyDescription("教程时长，单位：小时")
    private Integer duration;

    @JsonPropertyDescription("难度级别：初级/中级/高级")
    private String level;

    @JsonPropertyDescription("适用人群")
    private String targetAudience;

    @JsonPropertyDescription("教程标签，多个用逗号分隔")
    private String tags;

    @JsonPropertyDescription("教程链接")
    private String url;

    @JsonPropertyDescription("教程封面图片")
    private String coverUrl;

    @JsonPropertyDescription("作者ID")
    private Long authorId;

    @JsonPropertyDescription("作者名称")
    private String authorName;

    @JsonPropertyDescription("发布时间")
    private String publishTime;
}
