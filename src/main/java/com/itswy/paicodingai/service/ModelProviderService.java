package com.itswy.paicodingai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.itswy.paicodingai.model.ModelProviderConfig;
import com.itswy.paicodingai.mapper.ModelProviderConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LLM Provider 管理服务。多模态请求按当前激活配置动态读取，不需要重启。
 */
@Slf4j
@Service
public class ModelProviderService {

    public static final String SCOPE_LLM = "llm";
    public static final String API_STYLE_OPENAI = "openai-compatible";

    private final ModelProviderConfigMapper mapper;
    private final ModelProviderSecretService secretService;

    @Value("${mimo.api.base-url:https://api.xiaomimimo.com/v1}")
    private String mimoBaseUrl;

    @Value("${mimo.api.model:mimo-v2.5}")
    private String mimoModel;

    @Value("${mimo.api.key:}")
    private String mimoApiKey;

    @Value("${deepseek.api.base-url:https://api.deepseek.com/v1}")
    private String deepSeekBaseUrl;

    @Value("${deepseek.api.model:deepseek-chat}")
    private String deepSeekModel;

    @Value("${deepseek.api.key:}")
    private String deepSeekApiKey;

    @Value("${OLLAMA_BASE_URL:http://localhost:11434/v1}")
    private String ollamaBaseUrl;

    @Value("${OLLAMA_MODEL:deepseek-r1:7b}")
    private String ollamaModel;

    @Value("${OLLAMA_API_KEY:}")
    private String ollamaApiKey;

    public ModelProviderService(ModelProviderConfigMapper mapper, ModelProviderSecretService secretService) {
        this.mapper = mapper;
        this.secretService = secretService;
    }

    /** 启动时打印实际生效的 provider,便于排查 .env 被 OS 环境变量覆盖这类问题。 */
    @PostConstruct
    public void logEffectiveProvider() {
        try {
            ActiveProvider p = getActiveLlmProvider();
            log.info("生效 LLM provider: code={}, baseUrl={}, model={}, hasKey={}",
                    p.providerCode(), p.apiBaseUrl(), p.model(), hasText(p.apiKey()));
            log.info("Provider 取值优先级: model_provider_config(DB) > OS/用户环境变量 > .env;"
                    + " 若生效值与 .env 不一致,请检查是否有同名系统环境变量。");
        } catch (Exception e) {
            log.warn("读取生效 provider 失败(可能表未就绪),将在首次调用时重试: {}", e.getMessage());
        }
    }

    public ActiveProvider getActiveLlmProvider() {
        List<ProviderView> providers = getProviders();
        return providers.stream()
                .filter(provider -> provider.active() && provider.enabled())
                .findFirst()
                .map(this::toActiveProvider)
                .orElseGet(() -> toActiveProvider(providers.stream()
                        .filter(provider -> "mimo".equals(provider.providerCode()))
                        .findFirst()
                        .orElse(providers.get(0))));
    }

    public List<ProviderView> getProviders() {
        Map<String, ProviderView> defaults = new LinkedHashMap<>();
        defaults.put("mimo", new ProviderView("mimo", "小米 MiMo", API_STYLE_OPENAI,
                normalizeBaseUrl(mimoBaseUrl), mimoModel, true, true,
                hasText(mimoApiKey), secretService.mask(mimoApiKey)));
        defaults.put("deepseek", new ProviderView("deepseek", "DeepSeek", API_STYLE_OPENAI,
                normalizeBaseUrl(deepSeekBaseUrl), deepSeekModel, true, false,
                hasText(deepSeekApiKey), secretService.mask(deepSeekApiKey)));
        // Ollama：默认启用但非激活，供后台把第 2 层小模型当 provider 管理(OpenAI 兼容)
        defaults.put("ollama", new ProviderView("ollama", "Ollama(本地)", API_STYLE_OPENAI,
                normalizeBaseUrl(ollamaBaseUrl), ollamaModel, true, false,
                hasText(ollamaApiKey), secretService.mask(ollamaApiKey)));

        try {
            for (ModelProviderConfig config : mapper.selectList(Wrappers.<ModelProviderConfig>lambdaQuery()
                    .eq(ModelProviderConfig::getConfigScope, SCOPE_LLM))) {
                ProviderView fallback = defaults.get(config.getProviderCode());
                if (fallback == null) {
                    continue;
                }
                String apiKey = secretService.decrypt(config.getApiKeyCiphertext());
                defaults.put(config.getProviderCode(), new ProviderView(
                        config.getProviderCode(),
                        config.getDisplayName(),
                        config.getApiStyle(),
                        normalizeBaseUrl(config.getApiBaseUrl()),
                        config.getModelName(),
                        config.getEnabled() == null || config.getEnabled() == 1,
                        config.getActive() != null && config.getActive() == 1,
                        hasText(apiKey),
                        secretService.mask(apiKey)
                ));
            }
        } catch (Exception e) {
            // 管理表尚未执行迁移时仍允许使用环境变量中的默认模型启动。
            log.warn("读取运行时模型配置失败，使用环境变量默认值: {}", e.getMessage());
        }
        return defaults.values().stream().sorted(Comparator.comparing(ProviderView::providerCode)).toList();
    }

    public synchronized List<ProviderView> updateLlmProviders(UpdateRequest request, String updatedBy) {
        if (request == null || request.providers() == null || request.providers().isEmpty()) {
            throw new IllegalArgumentException("LLM Provider 配置不能为空");
        }
        String activeProvider = normalizeProvider(request.activeProvider());
        ProviderRequest active = request.providers().stream()
                .filter(item -> normalizeProvider(item.provider()).equals(activeProvider))
                .findFirst().orElse(null);
        if (active == null) {
            throw new IllegalArgumentException("激活 Provider 不在配置列表中");
        }
        if (active.enabled() != null && !active.enabled()) {
            throw new IllegalArgumentException("激活 Provider 必须处于启用状态");
        }

        Map<String, ProviderView> existing = getProviders().stream()
                .collect(java.util.stream.Collectors.toMap(ProviderView::providerCode, item -> item));
        for (ProviderRequest item : request.providers()) {
            String code = normalizeProvider(item.provider());
            ProviderView fallback = existing.get(code);
            if (fallback == null) {
                throw new IllegalArgumentException("不支持的 Provider: " + code);
            }
            if (!hasText(item.apiBaseUrl()) || !hasText(item.model())) {
                throw new IllegalArgumentException(code + " 的 API 地址和模型不能为空");
            }
            ModelProviderConfig entity = mapper.selectOne(Wrappers.<ModelProviderConfig>lambdaQuery()
                    .eq(ModelProviderConfig::getConfigScope, SCOPE_LLM)
                    .eq(ModelProviderConfig::getProviderCode, code));
            if (entity == null) {
                entity = new ModelProviderConfig();
                entity.setConfigScope(SCOPE_LLM);
                entity.setProviderCode(code);
                entity.setCreatedAt(LocalDateTime.now());
            }
            entity.setDisplayName(fallback.displayName());
            entity.setApiStyle(API_STYLE_OPENAI);
            entity.setApiBaseUrl(normalizeBaseUrl(item.apiBaseUrl()));
            entity.setModelName(item.model().trim());
            entity.setEnabled(item.enabled() == null || item.enabled() ? 1 : 0);
            entity.setActive(code.equals(activeProvider) ? 1 : 0);
            entity.setUpdatedBy(hasText(updatedBy) ? updatedBy : "local-admin");
            if (hasText(item.apiKey())) {
                entity.setApiKeyCiphertext(secretService.encrypt(item.apiKey().trim()));
            }
            entity.setUpdatedAt(LocalDateTime.now());
            if (entity.getId() == null) {
                mapper.insert(entity);
            } else {
                mapper.updateById(entity);
            }
        }
        return getProviders();
    }

    public ConnectivityTest testConnection(ProviderRequest request) {
        if (request == null || !hasText(request.apiBaseUrl()) || !hasText(request.model())) {
            return new ConnectivityTest(false, "API 地址和模型不能为空", 0);
        }
        long start = System.currentTimeMillis();
        try {
            String apiKey = hasText(request.apiKey()) ? request.apiKey() : resolveExistingKey(request.provider());
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(normalizeBaseUrl(request.apiBaseUrl()))
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            if (hasText(apiKey)) {
                builder.defaultHeader("Authorization", "Bearer " + apiKey.trim());
            }
            Map<String, Object> payload = Map.of(
                    "model", request.model().trim(),
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "stream", false,
                    "max_tokens", 1
            );
            builder.build().post().uri("/chat/completions").bodyValue(payload)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
            return new ConnectivityTest(true, "连接成功", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Provider 连接测试失败: provider={}", request.provider(), e);
            return new ConnectivityTest(false, "连接失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String resolveExistingKey(String provider) {
        String code = normalizeProvider(provider);
        try {
            ModelProviderConfig config = mapper.selectOne(Wrappers.<ModelProviderConfig>lambdaQuery()
                    .eq(ModelProviderConfig::getConfigScope, SCOPE_LLM)
                    .eq(ModelProviderConfig::getProviderCode, code));
            if (config != null && hasText(config.getApiKeyCiphertext())) {
                return secretService.decrypt(config.getApiKeyCiphertext());
            }
        } catch (Exception e) {
            log.debug("运行时模型配置表不可用，回退环境变量: {}", e.getMessage());
        }
        return switch (code) {
            case "mimo" -> mimoApiKey;
            case "deepseek" -> deepSeekApiKey;
            case "ollama" -> ollamaApiKey;
            default -> null;
        };
    }

    private ActiveProvider toActiveProvider(ProviderView provider) {
        return new ActiveProvider(provider.providerCode(), provider.displayName(), provider.apiBaseUrl(),
                provider.model(), resolveExistingKey(provider.providerCode()));
    }

    public static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        for (String suffix : List.of("/chat/completions", "/embeddings")) {
            if (result.toLowerCase(Locale.ROOT).endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String normalizeProvider(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Provider 不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ProviderView(String providerCode, String displayName, String apiStyle,
                               String apiBaseUrl, String model, boolean enabled, boolean active,
                               boolean hasApiKey, String maskedApiKey) {}

    public record ActiveProvider(String providerCode, String displayName, String apiBaseUrl,
                                 String model, String apiKey) {}

    public record ProviderRequest(String provider, String apiBaseUrl, String model,
                                  String apiKey, Boolean enabled) {}

    public record UpdateRequest(String activeProvider, List<ProviderRequest> providers) {}

    public record ConnectivityTest(boolean success, String message, long latencyMs) {}
}
