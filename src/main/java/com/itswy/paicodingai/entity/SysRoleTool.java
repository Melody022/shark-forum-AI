package com.itswy.paicodingai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色-工具白名单(权限模型 = 某角色能否调用某工具)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_tool")
public class SysRoleTool {

    private String roleCode;
    private String toolName;
}
