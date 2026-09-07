package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * OCR 质量检查服务
 * 判断是否需要调用 MIMO 增强识别
 */
public interface OcrQualityService {

    /**
     * OCR 识别结果
     */
    record OcrBlock(String text, double x1, double y1, double x2, double y2, float confidence) {}

    /**
     * OCR 表格识别结果
     */
    record OcrTableResult(List<List<OcrBlock>> rows, float avgConfidence) {}

    /**
     * 检查 OCR 结果质量，判断是否需要调用 MIMO
     *
     * @param result OCR 识别结果
     * @return true 表示需要调用 MIMO
     */
    boolean needMimo(OcrTableResult result);

    /**
     * 根据 OCR 返回的 blocks 重建表格结构
     *
     * @param blocks OCR 识别的所有文本块
     * @return 重建后的表格（按行按列排列）
     */
    OcrTableResult reconstructTable(List<OcrBlock> blocks);
}
