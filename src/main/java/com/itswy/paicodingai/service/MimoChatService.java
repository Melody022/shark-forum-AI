package com.itswy.paicodingai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.agent.AgentContext;
import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.knowledge.service.SearchResult;
import com.itswy.paicodingai.knowledge.service.VectorSearchService;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MIMO OpenAI-compatible 多模态聊天客户端。
 *
 * <p>图片使用标准的 {@code image_url} content part，可传 HTTPS URL 或 data URL。
 * 配置按请求读取，因此管理后台切换 Provider 后无需重启。</p>
 */
@Slf4j
@Service
public class MimoChatService {

    private static final int MAX_IMAGES = 4;
    private static final int MAX_DATA_URL_LENGTH = 12 * 1024 * 1024;

    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;
    private final SystemPromptConfig promptConfig;
    private final VectorSearchService vectorSearchService;

    public MimoChatService(ModelProviderService modelProviderService,
                           ObjectMapper objectMapper,
                           SystemPromptConfig promptConfig,
                           VectorSearchService vectorSearchService) {
        this.modelProviderService = modelProviderService;
        this.objectMapper = objectMapper;
        this.promptConfig = promptConfig;
        this.vectorSearchService = vectorSearchService;
    }

    public Flux<ChatEventVO> stream(String question, List<String> imageUrls, AgentContext context) {
        ModelProviderService.ActiveProvider provider = modelProviderService.getActiveLlmProvider();
        List<String> images = validateImages(imageUrls);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", provider.model());
        payload.put("messages", buildMessages(question, images, context));
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));
        payload.put("temperature", 0.3);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(ModelProviderService.normalizeBaseUrl(provider.apiBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey());
        }

        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();
            Disposable subscription = builder.build().post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMinutes(5))
                    .subscribe(
                            chunk -> consumeSseChunk(buffer, chunk, sink),
                            error -> {
                                log.warn("MIMO 流式调用失败: provider={}, model={}", provider.providerCode(), provider.model(), error);
                                sink.error(error);
                            },
                            () -> {
                                consumeSseLines(buffer, sink, true);
                                sink.complete();
                            });
            sink.onDispose(subscription::dispose);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private List<Map<String, Object>> buildMessages(String question, List<String> images,
                                                     AgentContext context) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", promptConfig.getSystemMessage("paicoding")
                        + "\n请理解用户附带的图片。若图片是表格，先说明表头、行列关系和单位；不要臆造无法确认的数字。"
        ));

        String ragContext = loadRagContext(question, context);
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", buildUserText(question, ragContext)));
        for (String image : images) {
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", image)));
        }
        messages.add(Map.of("role", "user", "content", parts));
        return messages;
    }

    private String buildUserText(String question, String ragContext) {
        if (ragContext.isBlank()) {
            return question == null || question.isBlank() ? "请分析这张图片。" : question;
        }
        return (question == null ? "" : question)
                + "\n\n以下是知识库参考资料，请结合图片回答：\n" + ragContext;
    }

    private String loadRagContext(String question, AgentContext context) {
        if (question == null || question.isBlank() || context == null || vectorSearchService == null) {
            return "";
        }
        try {
            String userId = context.getUserId() == null || context.getUserId().isBlank()
                    ? "0" : context.getUserId();
            return vectorSearchService.search(question, userId, 3).stream()
                    .map(this::formatResult)
                    .reduce((left, right) -> left + "\n---\n" + right)
                    .orElse("");
        } catch (Exception e) {
            log.debug("MIMO 请求补充 RAG 上下文失败: {}", e.getMessage());
            return "";
        }
    }

    private String formatResult(SearchResult result) {
        String source = result.fileName == null ? "" : result.fileName;
        if (result.sectionPath != null && !result.sectionPath.isBlank()) {
            source += " / " + result.sectionPath;
        }
        if (result.pageStart != null && result.pageStart > 0) {
            source += " / 第" + result.pageStart + "页";
        }
        return "来源：" + source + "\n" + (result.context == null ? result.content : result.context);
    }

    private List<String> validateImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        if (imageUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("一次最多处理 " + MAX_IMAGES + " 张图片");
        }
        return imageUrls.stream().map(value -> {
            if (value == null || value.isBlank()
                    || !(value.startsWith("data:image/") || value.startsWith("https://") || value.startsWith("http://"))) {
                throw new IllegalArgumentException("图片必须是 data URL 或 HTTP(S) URL");
            }
            if (value.startsWith("data:") && value.length() > MAX_DATA_URL_LENGTH) {
                throw new IllegalArgumentException("图片 data URL 过大");
            }
            return value;
        }).toList();
    }

    private void consumeSseChunk(StringBuilder buffer, String chunk, FluxSink<ChatEventVO> sink) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        buffer.append(chunk.replace("\r\n", "\n").replace('\r', '\n'));
        consumeSseLines(buffer, sink, false);
    }

    private void consumeSseLines(StringBuilder buffer, FluxSink<ChatEventVO> sink, boolean flush) {
        int newline;
        while ((newline = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, newline).trim();
            buffer.delete(0, newline + 1);
            emitLine(line, sink);
        }
        if (flush && !buffer.isEmpty()) {
            emitLine(buffer.toString().trim(), sink);
            buffer.setLength(0);
        }
    }

    private void emitLine(String line, FluxSink<ChatEventVO> sink) {
        if (!line.startsWith("data:")) {
            return;
        }
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode delta = root.path("choices").path(0).path("delta");
            String text = delta.path("content").asText("");
            if (!text.isBlank()) {
                sink.next(ChatEventVO.data(text));
            }
        } catch (Exception e) {
            log.debug("忽略无法解析的 MIMO SSE 数据: {}", data);
        }
    }
}
