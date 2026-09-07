# OCR 表格重建完整流程

> 从阿里云OCR扁平文本到结构化数据库

---

## 问题背景

阿里云OCR返回的是**扁平文本**，不是结构化表格数据：

```
{
  "code": 200,
  "data": {
    "content": "2025年 2024年 2023年\n总资产 247，353，213，805.61 233，421，377，309.40..."
  }
}
```

**问题**：
- 扁平文本，没有行列结构
- 多行文本（"归属于上市公司股\n东的净资产"）
- 数字有逗号分隔符（"247，353，213，805.61"）
- 无法直接存数据库

---

## 解决方案

### 完整流程

```
阿里云OCR
  ↓
返回扁平文本
  ↓
OcrTableReconstructor 重建表格
  ├─ 清理文本
  ├─ 分割行
  ├─ 识别表头
  ├─ 解析数据行
  └─ 返回 ReconstructedTable
  ↓
TableProcessingService 处理表格
  ├─ 生成表格摘要 → 向量化 → ES
  ├─ 生成行组Chunks → 向量化 → ES
  └─ 生成EAV格式 → 存储MySQL
  ↓
支持检索和Text-to-SQL
```

---

## 核心组件

### 1. OcrTableReconstructor

**职责**：从OCR扁平文本重建表格结构

**功能**：
- 清理文本（处理多行、特殊字符）
- 识别表头
- 解析数据行
- 清理数字中的逗号分隔符

**返回**：
```java
ReconstructedTable(
    headers: List<String>,  // ["2025年", "2024年", "2023年"]
    rows: List<List<String>>,  // [["总资产", "247353213805.61", ...], ...]
    title: String
)
```

**核心方法**：
```java
// 重建表格
ReconstructedTable reconstruct(String ocrContent)

// 解析单行为单元格
List<String> parseLine(String line)

// 清理数字中的逗号
String cleanNumber(String number)
```

### 2. AliyunOcrServiceImpl

**职责**：调用阿里云OCR API，返回重建后的表格

**功能**：
- 调用阿里云OCR API
- 合并所有文字
- 调用OcrTableReconstructor重建表格

**核心方法**：
```java
// 识别图片中的文字
List<OcrBlock> recognize(byte[] imageBytes)

// 识别表格并返回重建后的结构化数据
ReconstructedTable recognizeTable(byte[] imageBytes)
```

### 3. TableProcessingService

**职责**：处理表格，生成三种产物

**功能**：
- 生成表格摘要（用于向量化）
- 生成行组Chunks（用于向量化）
- 转换为EAV格式（用于存储）

**三种产物**：

**产物1：表格摘要**
```
该表标题为「Spring Boot 版本演进」，包含3行数据，字段包括：2025年、2024年、2023年。
```

**产物2：行组Chunks**
```
表名：Spring Boot 版本演进
字段：2025年, 2024年, 2023年
2025年：247353213805.61
2024年：233421377309.40
2023年：219115319747.00
```

**产物3：EAV格式**
```sql
table_id | row_index | field_name | field_value
table_001 | 0 | 2025年 | 247353213805.61
table_001 | 0 | 2024年 | 233421377309.40
table_001 | 0 | 2023年 | 219115319747.00
```

---

## 完整流程代码示例

### 示例1：从OCR到数据库

```java
@Autowired
private AliyunOcrServiceImpl ocrService;

@Autowired
private TableProcessingService tableProcessingService;

// Step1: OCR识别
byte[] imageBytes = ...;  // 图片字节数组
OcrTableReconstructor.ReconstructedTable reconstructed = ocrService.recognizeTable(imageBytes);

// Step2: 处理表格
KnowledgeTable table = tableProcessingService.processTable(
    "doc_001",
    "kb_001",
    "财务报表",
    reconstructed.headers(),
    reconstructed.rows(),
    1,
    "财务报表 > 主要会计数据"
);

// Step3: 生成表格摘要
String summary = tableProcessingService.generateTableSummary(
    table.getTitle(), reconstructed.headers(), reconstructed.rowCount()
);

// Step4: 生成行组Chunks
List<String> rowGroupChunks = tableProcessingService.generateRowGroupChunks(
    table.getTitle(), reconstructed.headers(), reconstructed.rows(), 10
);

// Step5: 向量化
tableVectorizationService.vectorizeTable(table.getId());
```

### 示例2：财务表格处理

```java
// 模拟OCR返回的扁平文本
String ocrContent = """
    2025年 2024年 2023年 本年比上年增减(%)
    总资产 247，353，213，805.61 233，421，377，309.40 5.97 219，115，319，747.00
    归属于上市公司股
    东的净资产 67，190，861，995.78 62，275，575，565.47 7.89 57，495，312，836.17
    营业收入 189，500，829，774.75 185，843，681，886.95 1.97 178，357，510，396.41
    """;

// 重建表格
OcrTableReconstructor.ReconstructedTable table = reconstructor.reconstruct(ocrContent);

// 输出结果
System.out.println("表头: " + table.headers());
// 结果: [2025年, 2024年, 2023年, 本年比上年增减]

System.out.println("行数: " + table.rowCount());
// 结果: 3

System.out.println("数据:");
for (List<String> row : table.rows()) {
    System.out.println("  " + row);
}
// 结果:
//   [总资产, 247353213805.61, 233421377309.40, 5.97, 219115319747.00]
//   [归属于上市公司股东的净资产, 67190861995.78, 62275575565.47, 7.89, 57495312836.17]
//   [营业收入, 189500829774.75, 185843681886.95, 1.97, 178357510396.41]
```

---

## 关键技术点

### 1. 多行文本处理

**问题**：
```
归属于上市公司股
东的净资产
```

**解决**：
```java
// 合并多行文本
text = text.replaceAll("([^\\d])\\n([^\\d])", "$1$2");
```

### 2. 数字清理

**问题**：
```
247，353，213，805.61
```

**解决**：
```java
// 清理数字中的逗号
String cleanNumber(String number) {
    return number.replaceAll("[，,]", "");
}
```

### 3. 表头识别

**问题**：如何区分表头和数据行？

**解决**：
- 第一行通常是表头
- 使用正则匹配数字和文本模式
- 表头通常包含年份、指标名称等

### 4. 正则匹配模式

```java
Pattern pattern = Pattern.compile(
    "([\\u4e00-\\u9fa5a-zA-Z]+[\\u4e00-\\u9fa5a-zA-Z\\s]*" +  // 中文/英文文本
    "|[0-9][0-9，,\\.]+[0-9]" +  // 数字（带逗号）
    "|\\d+\\.\\d+" +  // 小数
    "|\\d+%)"  // 百分比
);
```

---

## 测试用例

### 测试1：简单表格重建

```java
@Test
public void test1_SimpleTableReconstruction() {
    String ocrContent = """
        版本 日期 特性
        1.0 2014 初始发布
        2.0 2018 支持 Java 8
        """;

    var table = reconstructor.reconstruct(ocrContent);

    assertEquals(3, table.columnCount());
    assertEquals(2, table.rowCount());
}
```

### 测试2：财务表格重建

```java
@Test
public void test2_FinancialTableReconstruction() {
    String ocrContent = """
        2025年 2024年 2023年
        总资产 247，353，213，805.61 233，421，377，309.40 219，115，319，747.00
        """;

    var table = reconstructor.reconstruct(ocrContent);

    assertTrue(table.rowCount() > 0);
    assertTrue(table.columnCount() > 0);
}
```

### 测试3：完整流程

```java
@Test
public void test3_FullTableProcessing() {
    String ocrContent = """
        版本 日期 特性
        1.0 2014 初始发布
        2.0 2018 支持 Java 8
        """;

    // Step1: OCR表格重建
    var reconstructed = reconstructor.reconstruct(ocrContent);

    // Step2: 处理表格
    KnowledgeTable table = tableProcessingService.processTable(
        "doc_001", "kb_001", "Spring Boot 版本演进",
        reconstructed.headers(), reconstructed.rows(), 1, "Spring Boot > 版本信息"
    );

    // Step3: 向量化
    boolean success = tableVectorizationService.vectorizeTable(table.getId());

    assertTrue(success);
}
```

---

## 运行测试

```bash
cd D:/IdeaProjects/paicoding-ai

# 编译项目
mvn clean compile -DskipTests

# 运行OCR表格重建测试
mvn test -Dtest=OcrTableReconstructionTest

# 运行完整功能测试
mvn test -Dtest=RagCompleteTest
```

---

## 总结

### 问题
阿里云OCR返回扁平文本，无法直接存数据库

### 解决方案
1. **OcrTableReconstructor**：从扁平文本重建表格结构
2. **AliyunOcrServiceImpl**：调用OCR并返回重建后的表格
3. **TableProcessingService**：处理表格，生成三种产物

### 核心技术
- 多行文本处理
- 数字清理（逗号分隔符）
- 表头识别
- 正则匹配

### 输出产物
1. 表格摘要 → 向量化 → ES
2. 行组Chunks → 向量化 → ES
3. EAV格式 → 存储MySQL

### 安全性
- Text-to-SQL：LLM返回JSON，Java生成SQL
- 白名单校验
- 参数化查询

---

## 关键文件

| 文件 | 功能 |
|---|---|
| `OcrTableReconstructor.java` | OCR表格重建 |
| `AliyunOcrServiceImpl.java` | 阿里云OCR实现 |
| `TableProcessingService.java` | 表格处理接口 |
| `TableProcessingServiceImpl.java` | 表格处理实现 |
| `OcrTableReconstructionTest.java` | 测试 |
