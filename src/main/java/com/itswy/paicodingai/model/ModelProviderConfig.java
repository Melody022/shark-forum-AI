package com.itswy.paicodingai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 运行时模型 Provider 配置。API Key 只保存密文，接口只返回掩码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_provider_config")
public class ModelProviderConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configScope;
    private String providerCode;
    private String displayName;
    private String apiStyle;
    private String apiBaseUrl;
    private String modelName;
    private String apiKeyCiphertext;
    private Integer enabled;
    private Integer active;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
