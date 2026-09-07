package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.service.OcrQualityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OCR 质量检查服务实现
 */
@Slf4j
@Service
public class OcrQualityServiceImpl implements OcrQualityService {

    @Value("${table.quality.confidence-threshold:0.85}")
    private float confidenceThreshold = 0.85f;

    @Value("${table.quality.column-consistency-threshold:0.8}")
    private float columnConsistencyThreshold = 0.8f;

    @Value("${table.quality.merged-cell-width-ratio:2.0}")
    private double mergedCellWidthRatio = 2.0;

    @Override
    public boolean needMimo(OcrTableResult result) {
        if (result == null || result.rows() == null || result.rows().isEmpty()) {
            return true;
        }

        // 规则1：平均置信度过低
        float avgConfidence = result.avgConfidence();
        if (avgConfidence < confidenceThreshold) {
            log.info("触发 MIMO：置信度过低 {}", avgConfidence);
            return true;
        }

        // 规则2：列数不一致
        if (!isColumnCountConsistent(result)) {
            log.info("触发 MIMO：列数不一致");
            return true;
        }

        // 规则3：检测到合并单元格
        if (hasMergedCells(result)) {
            log.info("触发 MIMO：检测到合并单元格");
            return true;
        }

        // 规则4：行数太少
        if (result.rows().size() < 2) {
            log.info("触发 MIMO：行数太少 {}", result.rows().size());
            return true;
        }

        return false;
    }

    @Override
    public OcrTableResult reconstructTable(List<OcrBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new OcrTableResult(List.of(), 0.0f);
        }

        // 1. 按 y 坐标分组（找出行）
        Map<Double, List<OcrBlock>> rowsByY = new HashMap<>();
        for (OcrBlock block : blocks) {
            double yKey = roundToNearest(block.y1(), 10.0);  // 10像素阈值
            rowsByY.computeIfAbsent(yKey, k -> new ArrayList<>()).add(block);
        }

        // 2. 按 y 坐标排序（从上到下）
        List<Double> sortedYKeys = rowsByY.keySet().stream()
                .sorted()
                .toList();

        // 3. 每行内按 x 坐标排序（从左到右）
        List<List<OcrBlock>> rows = new ArrayList<>();
        for (Double yKey : sortedYKeys) {
            List<OcrBlock> row = rowsByY.get(yKey).stream()
                    .sorted(Comparator.comparingDouble(OcrBlock::x1))
                    .toList();
            rows.add(row);
        }

        // 4. 计算平均置信度
        float avgConfidence = (float) blocks.stream()
                .mapToDouble(OcrBlock::confidence)
                .average()
                .orElse(0.0);

        return new OcrTableResult(rows, avgConfidence);
    }

    /**
     * 检查列数是否一致
     */
    private boolean isColumnCountConsistent(OcrTableResult result) {
        List<Integer> columnCounts = result.rows().stream()
                .map(List::size)
                .toList();

        if (columnCounts.isEmpty()) {
            return true;
        }

        // 找到最常见的列数
        int mostCommonCount = columnCounts.stream()
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);

        // 计算一致率
        long consistentRows = columnCounts.stream()
                .filter(count -> count == mostCommonCount)
                .count();

        float consistencyRate = (float) consistentRows / columnCounts.size();
        return consistencyRate >= columnConsistencyThreshold;
    }

    /**
     * 检测合并单元格
     * 合并单元格特征：某个 cell 的宽度明显大于其他 cell
     */
    private boolean hasMergedCells(OcrTableResult result) {
        for (List<OcrBlock> row : result.rows()) {
            List<Double> widths = row.stream()
                    .map(block -> block.x2() - block.x1())
                    .toList();

            if (widths.isEmpty()) {
                continue;
            }

            double avgWidth = widths.stream().mapToDouble(d -> d).average().orElse(0);
            double maxWidth = widths.stream().mapToDouble(d -> d).max().orElse(0);

            // 如果最大宽度超过平均宽度的指定倍数，可能是合并单元格
            if (maxWidth > avgWidth * mergedCellWidthRatio) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将坐标四舍五入到最近的阈值倍数
     */
    private double roundToNearest(double value, double threshold) {
        return Math.round(value / threshold) * threshold;
    }
}
