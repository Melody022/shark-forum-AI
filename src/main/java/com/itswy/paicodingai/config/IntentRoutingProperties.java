package com.itswy.paicodingai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 意图路由(三层漏斗)配置。
 *
 * ai.routing.enabled=false 时仅保留规则层,跳过模型意图,用于延迟敏感或 Ollama 不可用场景。
 * ai.routing.intent-small.* 指向第 2 层小模型(默认本地 Ollama,OpenAI 兼容)。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.routing")
public class IntentRoutingProperties {

    /** 是否启用小/大模型意图层;false 时规则未命中直接走大模型兜底或默认。 */
    private boolean enabled = true;

    /** 第 2 层小模型配置。 */
    private SmallModel intentSmall = new SmallModel();

    @Data
    public static class SmallModel {
        private String baseUrl = "http://localhost:11434/v1";
        private String model = "deepseek-r1:7b";
        private String apiKey = "";
    }
}
