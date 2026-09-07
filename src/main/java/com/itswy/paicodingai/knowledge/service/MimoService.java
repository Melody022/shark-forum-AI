package com.itswy.paicodingai.knowledge.service;

import java.util.List;

/**
 * MIMO 多模态识别服务接口
 * 用于复杂表格增强识别
 */
public interface MimoService {

    /**
     * 识别表格结构
     *
     * @param imageBytes 图片字节数组
     * @param ocrResult OCR识别结果（用于上下文）
     * @return 表格结构数据
     */
    TableStructure recognizeTableStructure(byte[] imageBytes, String ocrResult);

    /**
     * 表格结构数据
     */
    record TableStructure(
            String title,
            List<String> headers,
            List<List<String>> rows,
            List<MergedCell> mergedCells,
            float confidence
    ) {}

    /**
     * 合并单元格信息
     */
    record MergedCell(
            int row,
            int col,
            int rowspan,
            int colspan,
            String text
    ) {}
}
