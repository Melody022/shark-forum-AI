package com.itswy.paicodingai.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.itswy.paicodingai.service.ModelProviderService;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 OpenAI-compatible ChatModel。
 *
 * <p>Spring AI 的默认 OpenAiChatModel 在启动时固定 API 地址和模型，后台切换后无法立即生效。
 * 此代理在每次请求开始时按当前 provider 创建或复用 delegate，同时保留 Spring AI 的
 * Tool Calling、Media 和 Advisor 处理能力。</p>
 */
@Primary
@Component
public class DynamicChatModel implements ChatModel {

    private final ModelProviderService providerService;
    private final ToolCallingManager toolCallingManager;
    private final Map<String, ChatModel> delegates = new ConcurrentHashMap<>();

    public DynamicChatModel(ModelProviderService providerService, ToolCallingManager toolCallingManager) {
        this.providerService = providerService;
        this.toolCallingManager = toolCallingManager;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate().call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate().stream(prompt);
    }

    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
        return OpenAiChatOptions.builder().model(providerService.getActiveLlmProvider().model()).build();
    }

    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
        return getDefaultOptions();
    }

    private ChatModel delegate() {
        ModelProviderService.ActiveProvider provider = providerService.getActiveLlmProvider();
        String cacheKey = provider.providerCode() + "|" + provider.apiBaseUrl() + "|"
                + provider.model() + "|" + provider.apiKey();
        return delegates.computeIfAbsent(cacheKey, ignored -> createDelegate(provider));
    }

    private ChatModel createDelegate(ModelProviderService.ActiveProvider provider) {
        ClientOptions.Builder options = ClientOptions.builder()
                .baseUrl(ModelProviderService.normalizeBaseUrl(provider.apiBaseUrl()))
                .timeout(Duration.ofMinutes(5))
                .maxRetries(2)
                .httpClient(SpringAiOpenAiHttpClient.builder().timeout(Duration.ofMinutes(5)).build());
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) {
            options.apiKey(provider.apiKey());
        }
        OpenAIClient client = new OpenAIClientImpl(options.build());
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(provider.model())
                .temperature(0.3)
                .streamUsage(true)
                .build();
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(chatOptions)
                .toolCallingManager(toolCallingManager)
                .build();
    }
}
