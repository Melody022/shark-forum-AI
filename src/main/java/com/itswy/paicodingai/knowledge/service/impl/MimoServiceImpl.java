package com.itswy.paicodingai.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.knowledge.service.MimoService;
import com.itswy.paicodingai.service.ModelProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * MIMO 多模态识别服务实现
 */
@Slf4j
@Service
public class MimoServiceImpl implements MimoService {

    @Autowired
    private ModelProviderService modelProviderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TableStructure recognizeTableStructure(byte[] imageBytes, String ocrResult) {
        try {
            // 1. 获取MIMO配置
            ModelProviderService.ActiveProvider provider = modelProviderService.getActiveLlmProvider();

            // 2. 构造Prompt
            String prompt = buildPrompt(ocrResult);

            // 3. 调用MIMO API
            String response = callMimoApi(provider, imageBytes, prompt);

            // 4. 解析返回结果
            return parseResponse(response);

        } catch (Exception e) {
            log.error("MIMO识别失败: {}", e.getMessage(), e);
            return new TableStructure(
                    "识别失败",
                    List.of(),
                    List.of(),
                    List.of(),
                    0.0f
            );
        }
    }

    private String buildPrompt(String ocrResult) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请识别这张图片中的表格结构，返回 JSON 格式。\n\n");

        if (ocrResult != null && !ocrResult.isBlank()) {
            prompt.append("OCR 识别结果：\n");
            prompt.append(ocrResult).append("\n\n");
        }

        prompt.append("""
                要求：
                1. 识别表格标题（如果有的话）
                2. 提取表头（第一行通常是表头）
                3. 提取所有数据行
                4. 检测合并单元格
                5. 每行列数一致

                返回 JSON 格式：
                {
                  "title": "表格标题",
                  "headers": ["列1", "列2", "列3"],
                  "rows": [
                    ["值1", "值2", "值3"],
                    ["值4", "值5", "值6"]
                  ],
                  "mergedCells": [
                    {"row": 0, "col": 0, "rowspan": 2, "colspan": 1, "text": "合并内容"}
                  ],
                  "confidence": 0.95
                }

                只返回 JSON，不要其他文字。
                """);

        return prompt.toString();
    }

    private String callMimoApi(ModelProviderService.ActiveProvider provider,
                               byte[] imageBytes, String prompt) {
        try {
            // 将图片转为Base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建请求体
            Map<String, Object> requestBody = Map.of(
                    "model", provider.model(),
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", prompt),
                                    Map.of("type", "image_url", "image_url",
                                            Map.of("url", "data:image/png;base64," + base64Image))
                            ))
                    ),
                    "stream", false,
                    "max_tokens", 2000
            );

            // 调用API
            WebClient client = WebClient.builder()
                    .baseUrl(provider.apiBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + provider.apiKey())
                    .build();

            Map<String, Object> response = client.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            // 提取返回内容
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }

            return "{}";

        } catch (Exception e) {
            log.error("调用MIMO API失败: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private TableStructure parseResponse(String response) {
        try {
            // 清理响应，提取JSON部分
            String json = response.trim();
            int start = json.indexOf("{");
            int end = json.lastIndexOf("}") + 1;

            if (start >= 0 && end > start) {
                json = json.substring(start, end);
            }

            // 解析JSON
            Map<String, Object> result = objectMapper.readValue(json, Map.class);

            String title = (String) result.get("title");
            List<String> headers = (List<String>) result.get("headers");
            List<List<String>> rows = (List<List<String>>) result.get("rows");
            List<Map<String, Object>> mergedCellsData = (List<Map<String, Object>>) result.get("mergedCells");
            float confidence = ((Number) result.getOrDefault("confidence", 0.0)).floatValue();

            // 解析合并单元格
            List<MergedCell> mergedCells = new ArrayList<>();
            if (mergedCellsData != null) {
                for (Map<String, Object> cell : mergedCellsData) {
                    mergedCells.add(new MergedCell(
                            (int) cell.get("row"),
                            (int) cell.get("col"),
                            (int) cell.get("rowspan"),
                            (int) cell.get("colspan"),
                            (String) cell.get("text")
                    ));
                }
            }

            return new TableStructure(
                    title != null ? title : "未命名表格",
                    headers != null ? headers : List.of(),
                    rows != null ? rows : List.of(),
                    mergedCells,
                    confidence
            );

        } catch (Exception e) {
            log.error("解析MIMO返回结果失败: {}", e.getMessage(), e);
            return new TableStructure(
                    "解析失败",
                    List.of(),
                    List.of(),
                    List.of(),
                    0.0f
            );
        }
    }
}
