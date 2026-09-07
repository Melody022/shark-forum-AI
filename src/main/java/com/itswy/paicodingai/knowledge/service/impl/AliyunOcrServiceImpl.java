package com.itswy.paicodingai.knowledge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.knowledge.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 阿里云 OCR 服务实现
 * 使用DashScope API Key（与Embedding共用）
 */
@Slf4j
@Service
public class AliyunOcrServiceImpl implements OcrService {

    @Value("${aliyun.ocr.access-key-id:}")
    private String dashscopeApiKey;

    @Value("${aliyun.ocr.endpoint:ocr-api.cn-hangzhou.aliyuncs.com}")
    private String endpoint;

    @Autowired
    private OcrTableReconstructor tableReconstructor;

    @Autowired
    private ObjectMapper objectMapper;

    private WebClient webClient;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            webClient = WebClient.builder()
                    .baseUrl("https://" + endpoint)
                    .defaultHeader("Authorization", "Bearer " + dashscopeApiKey)
                    .build();

            log.info("阿里云 OCR 客户端初始化成功（使用DashScope API Key）");
        } catch (Exception e) {
            log.warn("阿里云 OCR 客户端初始化失败: {}", e.getMessage());
            webClient = null;
        }
    }

    @Override
    public List<OcrBlock> recognize(byte[] imageBytes) {
        if (webClient == null) {
            log.warn("OCR 客户端未初始化，返回空结果");
            return List.of();
        }

        try {
            // 将图片转为Base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建请求体
            Map<String, Object> requestBody = Map.of(
                    "model", "ocr-v1",
                    "input", Map.of(
                            "image", "data:image/png;base64," + base64Image
                    )
            );

            // 调用OCR API
            Map<String, Object> response = webClient.post()
                    .uri("/api/v1/services/aigc/text2image/image-synthesis")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            // 解析结果
            List<OcrBlock> blocks = new ArrayList<>();

            if (response != null && response.containsKey("output")) {
                Map<String, Object> output = (Map<String, Object>) response.get("output");
                if (output.containsKey("text")) {
                    String text = (String) output.get("text");
                    blocks.add(new OcrBlock(text, 0, 0, 100, 20, 0.95f));
                }
            }

            log.info("OCR识别完成，返回 {} 个文本块", blocks.size());
            return blocks;

        } catch (Exception e) {
            log.error("OCR识别失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 识别表格并返回重建后的结构化数据
     */
    public OcrTableReconstructor.ReconstructedTable recognizeTable(byte[] imageBytes) {
        // 1. OCR识别
        List<OcrBlock> blocks = recognize(imageBytes);

        // 2. 合并所有文字
        StringBuilder content = new StringBuilder();
        for (OcrBlock block : blocks) {
            content.append(block.text()).append("\n");
        }

        // 3. 重建表格结构
        return tableReconstructor.reconstruct(content.toString());
    }

    @Override
    public List<OcrBlock> recognizePdfPage(byte[] pdfBytes, int pageNumber) {
        log.warn("PDF页面OCR识别功能待完善，返回空结果");
        return List.of();
    }
}
