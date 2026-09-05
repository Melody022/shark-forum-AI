package com.itswy.paicodingai.file.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 图片解析器。第一版保存原图来源，并通过可选 OCR 生成可检索文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageParser implements DocumentParser {

    private final OcrService ocrService;

    @Override
    public String getSupportedType() {
        return "image";
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of("png", "jpg", "jpeg", "bmp", "gif", "webp");
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && getSupportedTypes().contains(normalize(fileType));
    }

    @Override
    public ParseResult parse(File file) {
        try {
            OcrService.OcrResult ocr = ocrService.recognize(file);
            String ocrText = ocr == null || ocr.text() == null ? "" : ocr.text().trim();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ocrText", ocrText);
            metadata.put("ocrConfidence", ocr == null ? 0F : ocr.confidence());
            metadata.put("imagePath", file.getAbsolutePath());

            String content = ocrText.isBlank()
                    ? "图片：" + file.getName() + "（未配置 OCR，保留原图供后续视觉解析）"
                    : "图片 OCR 文本：\n" + ocrText;
            ContentBlock block = ContentBlock.builder()
                    .blockId("image-1")
                    .type(BlockType.IMAGE)
                    .content(content)
                    .pageStart(0)
                    .pageEnd(0)
                    .sourcePath(file.getAbsolutePath())
                    .metadata(metadata)
                    .searchable(!ocrText.isBlank())
                    .build();

            return new ParseResult(fileNameWithoutExtension(file.getName()), content,
                    List.of(block), getSupportedType(), file.length(), true);
        } catch (Exception e) {
            log.error("图片解析失败: {}", file.getName(), e);
            return new ParseResult("图片解析失败: " + e.getMessage());
        }
    }

    private String normalize(String type) {
        String normalized = type.toLowerCase(Locale.ROOT).trim();
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
