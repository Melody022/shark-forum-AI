package com.itswy.paicodingai.entity.forum;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专栏实体类
 *
 * 对应 forum 数据库的 column_info 表
 * 用作"教程"功能的替代实现
 */
@Data
@TableName("column_info")
public class ColumnInfo {

    /**
     * 专栏ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 专栏名
     */
    private String columnName;

    /**
     * 作者id
     */
    private Long userId;

    /**
     * 专栏简述
     */
    private String introduction;

    /**
     * 专栏封面
     */
    private String cover;

    /**
     * 状态: 0-审核中，1-连载，2-完结
     */
    private Integer state;

    /**
     * 上线时间
     */
    private LocalDateTime publishTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
