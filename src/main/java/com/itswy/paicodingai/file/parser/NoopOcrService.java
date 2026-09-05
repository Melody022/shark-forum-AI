package com.itswy.paicodingai.file.parser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 默认 OCR 降级实现，不依赖外部服务，保证图片上传不会阻塞主链路。
 */
@Component
@ConditionalOnProperty(name = "aliyun.ocr.enabled", havingValue = "false", matchIfMissing = true)
public class NoopOcrService implements OcrService {

    @Override
    public OcrResult recognize(File file) {
        return new OcrResult("", 0F);
    }

    @Override
    public Map<String, Object> recognizeForLiteParse(byte[] imageBytes, String language) {
        return Map.of("results", List.of());
    }
}
