package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * OCR 识别服务接口
 */
public interface OcrService {

    /**
     * OCR 识别结果
     */
    record OcrBlock(String text, double x1, double y1, double x2, double y2, float confidence) {}

    /**
     * 识别图片中的文字
     *
     * @param imageBytes 图片字节数组
     * @return OCR识别结果
     */
    List<OcrBlock> recognize(byte[] imageBytes);

    /**
     * 识别PDF页面中的文字
     *
     * @param pdfBytes PDF字节数组
     * @param pageNumber 页码（从0开始）
     * @return OCR识别结果
     */
    List<OcrBlock> recognizePdfPage(byte[] pdfBytes, int pageNumber);
}
