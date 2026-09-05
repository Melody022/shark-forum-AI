package com.itswy.paicodingai.file.parser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 默认 OCR 降级实现，不依赖外部服务，保证图片上传不会阻塞主链路。
 */
@Component
@ConditionalOnMissingBean(OcrService.class)
public class NoopOcrService implements OcrService {

    @Override
    public OcrResult recognize(File file) {
        return new OcrResult("", 0F);
    }
}
