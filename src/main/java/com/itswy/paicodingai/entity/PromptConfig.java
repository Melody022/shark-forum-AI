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
 * Prompt 存储(BASE/AGENT/SKILL/CLASSIFIER),DB 驱动 + Redis 版本缓存热更新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_prompt")
public class PromptConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String promptKey;
    private String promptType;
    private String name;
    private Integer version;
    private String content;
    private Integer enabled;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
