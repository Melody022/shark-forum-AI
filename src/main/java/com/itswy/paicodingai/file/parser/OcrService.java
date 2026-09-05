package com.itswy.paicodingai.file.parser;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 图片 OCR 扩展点。接入云 OCR 或本地 OCR 时只需替换实现。
 */
public interface OcrService {

    OcrResult recognize(File file);

    /** 可选的 LiteParse 适配输出，默认实现返回空结果。 */
    default Map<String, Object> recognizeForLiteParse(byte[] imageBytes, String language) {
        return Map.of("results", List.of());
    }

    record OcrResult(String text, Float confidence) {
        public boolean hasText() {
            return text != null && !text.isBlank();
        }
    }
}
