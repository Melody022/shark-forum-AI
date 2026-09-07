# OCR + MIMO 降级策略设计文档

> 表格识别质量保障方案

---

## 1. 问题背景

OCR 识别表格时可能出现以下问题：
- 列错位（bbox 坐标不准）
- 行粘连（两行合并成一行）
- 合并单元格丢失
- 置信度低（文字模糊）

需要一个**自动判断机制**，决定何时调用 MIMO 多模态模型增强识别。

---

## 2. 整体流程

```
PDF 表格图片
  ↓
OCR 识别（阿里云 OCR）
  ↓
解析 bbox，重建行列结构
  ↓
TableQualityChecker 检查质量
  ├─ 质量合格 → 使用 OCR 结果
  └─ 质量不合格 → 调用 MIMO 增强识别
                    ↓
                  MIMO 返回结构化 JSON
                    ↓
                  使用 MIMO 结果
```

---

## 3. OCR 失败的典型表现

###3.1 列错位

```
正常：
  版本 | 日期 | 特性
  1.0  | 2014 | 初始发布
  2.0  | 2018 | 支持Java8

OCR 错误：
  版本 | 日期
  1.0  | 2014 | 初始发布   ← 多了一列
  2.0  | 2018                ← 少了一列
```

###3.2 行粘连

```
正常：
  版本 | 日期 | 特性
  1.0  | 2014 | 初始发布
  2.0  | 2018 | 支持Java8

OCR 错误：
  版本 日期 特性 1.0 2014 初始发布 2.0 2018 支持Java8
  ↑ 所有内容粘连成一行
```

###3.3 合并单元格丢失

```
正常（有合并单元格）：
  ┌─────────────┐
  │  版本演进    │  ← 合并单元格
  ├──────┬──────┤
  │ 1.0  │ 2014 │
  │ 2.0  │ 2018 │

OCR 错误：
  版本演进 1.0 2014 2.0 2018
  ↑ 合并单元格结构完全丢失
```

###3.4 置信度低

```
OCR 返回：
  {
    "text": "版木",  ← "本" 被识别成 "木"
    "confidence": 0.65
  }
```

---

## 4. 质量检测规则

###4.1 规则清单

| 规则 | 检测方法 | 阈值 | 触发动作 |
|---|---|---|---|
| 置信度过低 | OCR 返回的 confidence | < 0.85 | 调用 MIMO |
| 列数不一致 | 各行列数是否相同 | 不一致 | 调用 MIMO |
| 合并单元格 | cell 宽度异常大 | 宽度 > 平均×2 | 调用 MIMO |
| 多行表头 | 前两行都是中文密集 | 可选 | 调用 MIMO |
| 行数异常 | 太少或太多 | < 2 或 > 1000 | 调用 MIMO |

###4.2 优先级

```
高优先级（必须检测）：
  - 置信度过低
  - 列数不一致

中优先级（建议检测）：
  - 合并单元格

低优先级（可选）：
  - 多行表头
  - 行数异常
```

---

## 5. 实现代码

###5.1 核心检查器

```java
/**
 * OCR 表格质量检查器
 * 判断是否需要调用 MIMO 增强识别
 */
public class TableQualityChecker {
    
    // 置信度阈值
    private static final float CONFIDENCE_THRESHOLD = 0.85f;
    
    // 列数一致性阈值（允许少量偏差）
    private static final float COLUMN_CONSISTENCY_THRESHOLD = 0.8f;
    
    /**
     * 检查 OCR 结果质量，决定是否需要调用 MIMO
     * 
     * @param ocrResult OCR 识别结果
     * @return true 表示需要调用 MIMO
     */
    public boolean needMimo(OcrTableResult ocrResult) {
        // 规则1：平均置信度过低
        float avgConfidence = calculateAvgConfidence(ocrResult);
        if (avgConfidence < CONFIDENCE_THRESHOLD) {
            log.info("触发 MIMO：置信度过低 {}", avgConfidence);
            return true;
        }
        
        // 规则2：列数不一致（超过20%的行列数不同）
        if (!isColumnCountConsistent(ocrResult)) {
            log.info("触发 MIMO：列数不一致");
            return true;
        }
        
        // 规则3：检测到合并单元格（文本跨多列）
        if (hasMergedCells(ocrResult)) {
            log.info("触发 MIMO：检测到合并单元格");
            return true;
        }
        
        // 规则4：检测到多行表头（第一行不是表头）
        if (hasMultiLineHeader(ocrResult)) {
            log.info("触发 MIMO：检测到多行表头");
            return true;
        }
        
        // 规则5：行数太少或太多（可能是解析错误）
        if (ocrResult.rows().size() < 2 || ocrResult.rows().size() > 1000) {
            log.info("触发 MIMO：行数异常 {}", ocrResult.rows().size());
            return true;
        }
        
        return false;
    }
    
    /**
     * 计算平均置信度
     */
    private float calculateAvgConfidence(OcrTableResult ocrResult) {
        return (float) ocrResult.allCells().stream()
            .mapToDouble(OcrCell::confidence)
            .average()
            .orElse(0.0);
    }
    
    /**
     * 检查列数是否一致
     */
    private boolean isColumnCountConsistent(OcrTableResult ocrResult) {
        List<Integer> columnCounts = ocrResult.rows().stream()
            .map(row -> row.cells().size())
            .toList();
        
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
        return consistencyRate >= COLUMN_CONSISTENCY_THRESHOLD;
    }
    
    /**
     * 检测合并单元格
     * 合并单元格特征：某个 cell 的宽度明显大于其他 cell
     */
    private boolean hasMergedCells(OcrTableResult ocrResult) {
        for (var row : ocrResult.rows()) {
            List<Double> widths = row.cells().stream()
                .map(cell -> cell.bbox().width())
                .toList();
            
            double avgWidth = widths.stream().mapToDouble(d -> d).average().orElse(0);
            double maxWidth = widths.stream().mapToDouble(d -> d).max().orElse(0);
            
            // 如果最大宽度超过平均宽度的2倍，可能是合并单元格
            if (maxWidth > avgWidth * 2) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检测多行表头
     * 表头特征：第一行和第二行的文字风格相似（都是粗体、字号相同等）
     */
    private boolean hasMultiLineHeader(OcrTableResult ocrResult) {
        if (ocrResult.rows().size() < 2) {
            return false;
        }
        
        // 简单实现：检查第一行和第二行是否有相似的文字（都是描述性词语）
        var firstRowTexts = ocrResult.rows().get(0).cells().stream()
            .map(OcrCell::text)
            .toList();
        var secondRowTexts = ocrResult.rows().get(1).cells().stream()
            .map(OcrCell::text)
            .toList();
        
        // 如果第一行和第二行都有很多中文（可能是双行表头）
        long firstRowChinese = firstRowTexts.stream()
            .filter(text -> text.matches(".*[\\u4e00-\\u9fa5].*"))
            .count();
        long secondRowChinese = secondRowTexts.stream()
            .filter(text -> text.matches(".*[\\u4e00-\\u9fa5].*"))
            .count();
        
        // 两行都是中文密集，可能是多行表头
        return firstRowChinese > firstRowTexts.size() * 0.5 
            && secondRowChinese > secondRowTexts.size() * 0.5;
    }
}
```

###5.2 简化版（Demo 够用）

```java
/**
 * 简化版质量检查器
 * 只检测置信度和列数一致性
 */
public class SimpleTableQualityChecker {
    
    private static final float CONFIDENCE_THRESHOLD = 0.85f;
    
    public boolean needMimo(OcrTableResult ocrResult) {
        // 规则1：置信度低于阈值
        float avgConfidence = calculateAvgConfidence(ocrResult);
        if (avgConfidence < CONFIDENCE_THRESHOLD) {
            return true;
        }
        
        // 规则2：列数不一致
        long distinctColumnCounts = ocrResult.rows().stream()
            .map(row -> row.cells().size())
            .distinct()
            .count();
        if (distinctColumnCounts > 1) {
            return true;
        }
        
        return false;
    }
    
    private float calculateAvgConfidence(OcrTableResult ocrResult) {
        return (float) ocrResult.allCells().stream()
            .mapToDouble(OcrCell::confidence)
            .average()
            .orElse(0.0);
    }
}
```

---

## 6. MIMO Prompt 设计

当 OCR 失败时，调用 MIMO 的 Prompt：

```
OCR 识别这个表格失败了（{失败原因}）。
请直接分析图片，识别表格结构，返回 JSON：

{
  "title": "表格标题",
  "headers": ["列1", "列2", "列3"],
  "rows": [
    ["值1", "值2", "值3"],
    ["值4", "值5", "值6"]
  ],
  "merged_cells": [
    {"row": 0, "col": 0, "rowspan": 2, "colspan": 1, "text": "合并内容"}
  ]
}

请确保：
1. 每行列数一致
2. 合并单元格明确标注
3. 只返回 JSON
```

失败原因可以是：
- "列数不一致"
- "置信度过低"
- "检测到合并单元格"
- "多行表头"

---

## 7. 数据结构定义

###7.1 OCR 返回结构

```java
public record OcrTableResult(
    List<OcrRow> rows,
    float avgConfidence
) {
    public List<OcrCell> allCells() {
        return rows.stream()
            .flatMap(row -> row.cells().stream())
            .toList();
    }
}

public record OcrRow(
    int rowIndex,
    List<OcrCell> cells
) {}

public record OcrCell(
    String text,
    Bbox bbox,
    float confidence
) {}

public record Bbox(
    double x, double y,
    double width, double height
) {}
```

###7.2 MIMO 返回结构

```java
public record MimoTableResult(
    String title,
    List<String> headers,
    List<List<String>> rows,
    List<MergedCell> mergedCells
) {}

public record MergedCell(
    int row, int col,
    int rowspan, int colspan,
    String text
) {}
```

---

## 8. 调用流程图

```
┌─────────────────────────────────────────────────────────────┐
│                    PDF 表格处理流程                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │   OCR 识别       │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ 解析 bbox        │
                    │ 重建行列结构     │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ TableQuality    │
                    │ Checker 检查    │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
    ┌─────────────────┐           ┌─────────────────┐
    │ 质量合格         │           │ 质量不合格       │
    │ 使用 OCR 结果    │           │ 调用 MIMO       │
    └────────┬────────┘           └────────┬────────┘
              │                             │
              │                             ▼
              │                    ┌─────────────────┐
              │                    │ MIMO 返回 JSON  │
              │                    │ 使用 MIMO 结果   │
              │                    └────────┬────────┘
              │                             │
              └──────────────┬──────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ 生成三种产物：   │
                    │ - 表格摘要      │
                    │ - 行组 Chunks   │
                    │ - 结构化数据    │
                    └─────────────────┘
```

---

## 9. 测试用例

###9.1 应该触发 MIMO 的情况

```java
@Test
void shouldTriggerMimoWhenLowConfidence() {
    OcrTableResult result = new OcrTableResult(
        List.of(
            new OcrRow(0, List.of(
                new OcrCell("版木", new Bbox(0, 0, 50, 20), 0.65f),  // 低置信度
                new OcrCell("日期", new Bbox(50, 0, 100, 20), 0.95f)
            ))
        ),
        0.80f  // 平均置信度 < 0.85
    );
    
    assertTrue(checker.needMimo(result));
}

@Test
void shouldTriggerMimoWhenInconsistentColumns() {
    OcrTableResult result = new OcrTableResult(
        List.of(
            new OcrRow(0, List.of(
                new OcrCell("版本", new Bbox(0, 0, 50, 20), 0.95f),
                new OcrCell("日期", new Bbox(50, 0, 100, 20), 0.95f)
            )),
            new OcrRow(1, List.of(
                new OcrCell("1.0", new Bbox(0, 20, 50, 40), 0.95f),
                new OcrCell("2014", new Bbox(50, 20, 100, 40), 0.95f),
                new OcrCell("初始发布", new Bbox(100, 20, 150, 40), 0.95f)  // 多了一列
            ))
        ),
        0.95f
    );
    
    assertTrue(checker.needMimo(result));
}
```

###9.2 不应该触发 MIMO 的情况

```java
@Test
void shouldNotTriggerMimoWhenQualityGood() {
    OcrTableResult result = new OcrTableResult(
        List.of(
            new OcrRow(0, List.of(
                new OcrCell("版本", new Bbox(0, 0, 50, 20), 0.95f),
                new OcrCell("日期", new Bbox(50, 0, 100, 20), 0.95f),
                new OcrCell("特性", new Bbox(100, 0, 150, 20), 0.95f)
            )),
            new OcrRow(1, List.of(
                new OcrCell("1.0", new Bbox(0, 20, 50, 40), 0.95f),
                new OcrCell("2014", new Bbox(50, 20, 100, 40), 0.95f),
                new OcrCell("初始发布", new Bbox(100, 20, 150, 40), 0.95f)
            ))
        ),
        0.95f
    );
    
    assertFalse(checker.needMimo(result));
}
```

---

## 10. 配置参数

```yaml
# application.yml
table:
  quality:
    # 置信度阈值
    confidence-threshold: 0.85
    # 列数一致性阈值
    column-consistency-threshold: 0.8
    # 合并单元格宽度倍数阈值
    merged-cell-width-ratio: 2.0
    # 最小行数
    min-rows: 2
    # 最大行数
    max-rows: 1000
```

---

## 11. 监控指标

建议记录以下指标用于调优：

```java
// 触发 MIMO 的原因统计
Map<String, Long> mimoTriggerReasons = Map.of(
    "low_confidence", 150L,
    "inconsistent_columns", 80L,
    "merged_cells", 45L,
    "multi_line_header", 20L,
    "row_count_anomaly", 10L
);

// OCR 平均置信度分布
// 列数一致性分布
// MIMO 调用频率
// MIMO 调用成本
```

---

## 12. 简历写法

```
• 实现 OCR + MIMO 降级策略：OCR 识别后通过置信度、列数一致性、合并单元格检测判断质量，
  不合格时自动调用多模态模型增强识别，保证表格解析准确率
```

---

## 13. 实现阶段

### Phase 1：基础检测（Demo）
- [ ] 实现简化版 TableQualityChecker
- [ ] 只检测置信度和列数一致性
- [ ] 触发时调用 MIMO

### Phase 2：完整检测
- [ ] 实现合并单元格检测
- [ ] 实现多行表头检测
- [ ] 添加配置参数

### Phase 3：监控调优
- [ ] 记录触发原因统计
- [ ] 根据统计数据调整阈值
- [ ] 优化 MIMO 调用成本
