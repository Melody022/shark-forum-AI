package com.itswy.paicodingai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * ==========================================================================
 * 会话表实体类 —— 一次对话就是一个 session
 * ==========================================================================
 *
 * 对应数据库表：chat_session
 *
 * 大白话理解：
 *   你打开聊天窗口，就创建了一个 session（会话），
 *   你在这个窗口里发的所有消息都属于这个 session。
 *   就像微信里的一个聊天对话框。
 *
 *   sessionId 是给前端用的（UUID字符串），
 *   id 是给数据库用的（自增主键）。
 *
 * @date 2026-07-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {

    /** 数据库自增主键（MySQL自动生成） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话唯一标识（UUID字符串，给前端用） */
    private String sessionId;

    /** 用户ID（paicoding的用户ID，后面做多用户时用） */
    private Long userId;

    /** 会话标题（AI自动根据第一句话生成，比如"Java怎么学"） */
    private String title;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最近更新时间 */
    private LocalDateTime updateTime;
}
