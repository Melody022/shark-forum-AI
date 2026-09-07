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
 * 知识库表格行数据（EAV 格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_table_row")
public class KnowledgeTableRow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tableId;

    private Integer rowIndex;

    private String fieldName;

    private String fieldValue;

    /** 归一化值，用于精确查询 */
    private String normalizedValue;

    private LocalDateTime createdAt;
}
