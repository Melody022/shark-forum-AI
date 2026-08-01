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
 * 聊天记录实体 —— 存储对话历史消息
 *
 * 对应数据库表：chat_record
 * 每条记录代表一个消息（用户提问或AI回复）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_record")
public class ChatRecord {

    /** 数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对话ID（格式：sessionId，唯一标识一个对话窗口） */
    private String conversationId;

    /** 消息数据（JSON格式，存储 Message 对象） */
    private String data;

    /** 消息类型：1-用户，2-AI */
    private Integer type;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
