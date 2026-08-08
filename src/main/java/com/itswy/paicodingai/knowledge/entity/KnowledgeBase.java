package com.itswy.paicodingai.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * 类型：0-用户知识库，1-系统知识库
     */
    private Integer type;

    private String userId;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 类型常量
    public static final int TYPE_USER = 0;
    public static final int TYPE_SYSTEM = 1;

    // 状态常量
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;
}
