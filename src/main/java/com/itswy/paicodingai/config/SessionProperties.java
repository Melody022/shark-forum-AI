package com.itswy.paicodingai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "ai.session")
public class SessionProperties {
    private String title;
    private String describe;
    private List<String> examples;
}
