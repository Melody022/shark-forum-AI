package com.itswy.paicodingai.file.parser;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextRequest;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.Common;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云通用文字识别高精版实现。
 */
@Slf4j
@Primary
@Service
@ConditionalOnProperty(name = "aliyun.ocr.enabled", havingValue = "true")
public class AliyunOcrService implements OcrService {

    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String type;
    private final String outputCoordinate;
    private final boolean outputOricoord;
    private final boolean outputRow;
    private final boolean outputParagraph;
    private final boolean outputTable;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private volatile Client client;

    public AliyunOcrService(
            ObjectMapper objectMapper,
            @Value("${aliyun.ocr.endpoint:ocr-api.cn-hangzhou.aliyuncs.com}") String endpoint,
            @Value("${aliyun.ocr.access-key-id:${ALIBABA_CLOUD_ACCESS_KEY_ID:}}") String accessKeyId,
            @Value("${aliyun.ocr.access-key-secret:${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}}") String accessKeySecret,
            @Value("${aliyun.ocr.type:Advanced}") String type,
            @Value("${aliyun.ocr.output-coordinate:points}") String outputCoordinate,
            @Value("${aliyun.ocr.output-oricoord:true}") boolean outputOricoord,
            @Value("${aliyun.ocr.output-row:false}") boolean outputRow,
            @Value("${aliyun.ocr.output-paragraph:false}") boolean outputParagraph,
            @Value("${aliyun.ocr.output-table:false}") boolean outputTable,
            @Value("${aliyun.ocr.connect-timeout-millis:10000}") int connectTimeoutMillis,
            @Value("${aliyun.ocr.read-timeout-millis:60000}") int readTimeoutMillis) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.type = type;
        this.outputCoordinate = outputCoordinate;
        this.outputOricoord = outputOricoord;
        this.outputRow = outputRow;
        this.outputParagraph = outputParagraph;
        this.outputTable = outputTable;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public OcrResult recognize(File file) {
        try {
            JsonNode data = recognizeRaw(Files.readAllBytes(file.toPath()));
            String content = firstText(data, "Content", "content", "Text", "text");
            double confidence = firstNumber(data, 0D, "Confidence", "confidence", "BlockConfidence", "blockConfidence");
            if (confidence > 1D) {
                confidence /= 100D;
            }
            return new OcrResult(content, (float) Math.max(0D, Math.min(1D, confidence)));
        } catch (IOException e) {
            throw new RuntimeException("读取 OCR 文件失败", e);
        }
    }

    /** 返回阿里云原始结果，供 LiteParse 适配器提取 bbox 和置信度。 */
    public JsonNode recognizeRaw(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR 图片为空");
        }
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "阿里云 OCR AccessKey 未配置");
        }
        try {
            RecognizeAllTextRequest request = new RecognizeAllTextRequest()
                    .setBody(new ByteArrayInputStream(imageBytes))
                    .setType(defaultIfBlank(type, "Advanced"))
                    .setOutputOricoord(outputOricoord);
            if (StringUtils.hasText(outputCoordinate)) {
                request.setOutputCoordinate(outputCoordinate.trim());
            }
            if ("Advanced".equalsIgnoreCase(defaultIfBlank(type, "Advanced"))) {
                request.setAdvancedConfig(new RecognizeAllTextRequest.RecognizeAllTextRequestAdvancedConfig()
                        .setOutputRow(outputRow)
                        .setOutputParagraph(outputParagraph)
                        .setOutputTable(outputTable));
            }

            RecognizeAllTextResponse response = getClient().recognizeAllTextWithOptions(request,
                    new RuntimeOptions().setConnectTimeout(connectTimeoutMillis).setReadTimeout(readTimeoutMillis));
            if (response == null || response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 OCR 返回为空");
            }
            String code = response.getBody().getCode();
            if (StringUtils.hasText(code) && !"200".equals(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "阿里云 OCR 失败: " + code + " " + response.getBody().getMessage());
            }
            return objectMapper.readTree(Common.toJSONString(response.getBody().getData()));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (TeaException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 OCR 请求失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 OCR 请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> recognizeForLiteParse(byte[] imageBytes, String language) {
        JsonNode data = recognizeRaw(imageBytes);
        List<Map<String, Object>> results = new ArrayList<>();
        JsonNode subImages = pathAny(data, "SubImages", "subImages");
        if (subImages.isArray()) {
            for (JsonNode subImage : subImages) {
                JsonNode blockDetails = pathAny(pathAny(subImage, "BlockInfo", "blockInfo"),
                        "BlockDetails", "blockDetails");
                if (!blockDetails.isArray()) {
                    continue;
                }
                for (JsonNode block : blockDetails) {
                    String text = textAny(block, "BlockContent", "blockContent").trim();
                    if (text.isBlank()) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("text", text);
                    item.put("bbox", pointsToBbox(pathAny(block, "BlockPoints", "blockPoints")));
                    double confidence = numberAny(block, 100D, "BlockConfidence", "blockConfidence");
                    item.put("confidence", confidence > 1D ? confidence / 100D : confidence);
                    results.add(item);
                }
            }
        }
        if (results.isEmpty()) {
            String content = textAny(data, "Content", "content");
            for (String line : content.split("\\R+")) {
                if (!line.isBlank()) {
                    results.add(Map.of("text", line.trim(), "bbox", List.of(0D, 0D, 0D, 0D), "confidence", 1D));
                }
            }
        }
        results.sort(Comparator.comparingDouble(item -> ((List<?>) item.get("bbox")).isEmpty()
                ? 0D : ((Number) ((List<?>) item.get("bbox")).get(1)).doubleValue()));
        return Map.of("results", results);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret);
    }

    private Client getClient() throws Exception {
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (client == null) {
                Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
                config.endpoint = defaultIfBlank(endpoint, "ocr-api.cn-hangzhou.aliyuncs.com");
                client = new Client(config);
            }
            return client;
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.path(name);
            if (value != null && !value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private double firstNumber(JsonNode node, double fallback, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.path(name);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return fallback;
    }

    private JsonNode pathAny(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private String textAny(JsonNode node, String... names) {
        JsonNode value = pathAny(node, names);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private double numberAny(JsonNode node, double fallback, String... names) {
        JsonNode value = pathAny(node, names);
        return value.isMissingNode() || value.isNull() ? fallback : value.asDouble(fallback);
    }

    private List<Double> pointsToBbox(JsonNode points) {
        if (!points.isArray() || points.isEmpty()) {
            return List.of(0D, 0D, 0D, 0D);
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0D, maxY = 0D;
        for (JsonNode point : points) {
            double x = numberAny(point, 0D, "X", "x");
            double y = numberAny(point, 0D, "Y", "y");
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return minX == Double.MAX_VALUE ? List.of(0D, 0D, 0D, 0D) : List.of(minX, minY, maxX, maxY);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
