package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.file.parser.AliyunOcrService;
import com.itswy.paicodingai.file.parser.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** LiteParse -> 阿里云 OCR 的内部适配接口。 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/ocr")
@RequiredArgsConstructor
public class InternalOcrController {

    private final OcrService ocrService;

    @PostMapping(path = {"/liteparse", "/liteparse/", "/liteparse/ocr"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> recognizeForLiteParse(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "token", required = false) String token) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "OCR 图片为空"));
        }
        String expected = System.getProperty("ALIYUN_OCR_CALLBACK_TOKEN", "");
        if (StringUtils.hasText(expected) && !expected.equals(token)) {
            return ResponseEntity.status(401).body(Map.of("message", "OCR token 无效"));
        }
        if (ocrService instanceof AliyunOcrService aliyunOcrService) {
            return ResponseEntity.ok(aliyunOcrService.recognizeForLiteParse(file.getBytes(), language));
        }
        return ResponseEntity.ok(ocrService.recognizeForLiteParse(file.getBytes(), language));
    }
}
