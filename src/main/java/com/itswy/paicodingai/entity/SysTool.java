package com.itswy.paicodingai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具注册信息(tool_owner 标记该工具归属 USER/ADMIN)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_tool")
public class SysTool {

    @TableId(type = IdType.INPUT)
    private String toolName;

    private String toolDesc;
    private String toolOwner;
    private String riskLevel;
    private Integer builtIn;
}
