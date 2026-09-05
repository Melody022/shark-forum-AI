package com.itswy.paicodingai.file.parser;

import java.io.File;

/**
 * 图片 OCR 扩展点。接入云 OCR 或本地 OCR 时只需替换实现。
 */
public interface OcrService {

    OcrResult recognize(File file);

    record OcrResult(String text, Float confidence) {
        public boolean hasText() {
            return text != null && !text.isBlank();
        }
    }
}
