package com.itswy.paicodingai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 提示词Nacos配置属性
 *
 * 用于从Nacos读取提示词文件（base.md、route.md等）
 * 注意：类名避免与Spring Cloud Alibaba的NacosProperties冲突
 */
@Data
@Component
@ConfigurationProperties(prefix = "paicoding.ai.nacos")
public class PromptNacosProperties {

    private String serverAddr = "127.0.0.1:8848";
    private String namespace = "";
    private String group = "PAICODING_AI_PROMPT";
}
