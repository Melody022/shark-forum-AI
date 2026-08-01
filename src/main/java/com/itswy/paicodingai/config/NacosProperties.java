package com.itswy.paicodingai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.nacos")
public class NacosProperties {

    private String serverAddr = "127.0.0.1:8848";
    private String namespace = "";
    private String group = "PAICODING_AI_PROMPT";
}
