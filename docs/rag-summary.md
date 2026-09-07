# RAG 知识库系统总结

> 切片 → 存储 → 召回完整流程

---

## 第一部分：详细流程版

###1. 文件解析与切片

####1.1 文件类型路由

```
文件上传
  ↓
判断文件类型
  ├─ .md   → Markdown 解析器（flexmark-java）
  ├─ .txt  → 纯文本解析器（按段落切分）
  ├─ .pdf  → PDF 解析器（自动判断文本/扫描）
  └─ 其他  → 拒绝或按纯文本处理
```

####1.2 Markdown 解析

```
Markdown 文件
  ↓
flexmark-java 解析 AST
  ↓
识别标题层级（H1-H6）
  ↓
识别段落、列表、代码块、表格
  ↓
生成 ContentBlock 列表
```

示例：
```markdown
# Spring Boot
## 自动配置
Spring Boot 会根据条件自动装配 Bean。

```java
@EnableAutoConfiguration
```

解析结果：
```
ContentBlock(HEADING, "Spring Boot", sectionPath="Spring Boot")
ContentBlock(HEADING, "自动配置", sectionPath="Spring Boot > 自动配置")
ContentBlock(TEXT, "Spring Boot 会根据条件自动装配 Bean。", sectionPath="Spring Boot > 自动配置")
ContentBlock(CODE, "@EnableAutoConfiguration", sectionPath="Spring Boot > 自动配置")
```

####1.3 纯文本解析

```
TXT 文件
  ↓
UTF-8 读取
  ↓
按空行分割段落
  ↓
识别编号标题（如 "1."、"第一章"）
  ↓
生成 ContentBlock 列表
```

切片参数：
- child chunk：300~600 token
- overlap：50~100 token
- 尽量保持段落完整，过长再按句子切分

####1.4 PDF 解析（LiteParse + OCR + MIMO）

```
PDF 文件
  ↓
LiteParse 解析（主解析器）
  ├─ 有文本层 → 直接提取文字（TEXT 类型）
  └─ 表格/扫描页 → 调用阿里云 OCR（LiteParse 内置）
                     ↓
                   OCR 返回：文字 + bbox 坐标 + 置信度
                     ↓
                   TableStructureReconstructor 根据 bbox 恢复行列结构
                     ↓
                   TableQualityChecker 检查质量
                     ├─ 合格 → 使用恢复的结构
                     └─ 不合格 → 调用 MIMO 增强识别（兜底方案）
                     ↓
                   生成两种 ContentBlock：
                     - TEXT 类型：从 LiteParse 提取的文本
                     - TABLE 类型：从 OCR/MIMO 识别的表格
```

**关键点**：
- LiteParse 是主解析器，负责 PDF 解析
- 阿里云 OCR 是 LiteParse 调用的子模块，识别图片中的文字
- MIMO 是兜底方案，当 OCR 识别质量不合格时使用
- OCR 返回文字 + bbox 坐标，通过坐标恢复行列结构

---

###2. 表格处理（三种产物）

####2.1 表格识别流程

```
OCR 识别表格
  ↓
解析 bbox，重建行列结构
  ↓
TableQualityChecker 检查质量
  ├─ 置信度 < 0.85 → 调用 MIMO
  ├─ 列数不一致 → 调用 MIMO
  ├─ 检测到合并单元格 → 调用 MIMO
  └─ 质量合格 → 使用 OCR 结果
  ↓
生成三种产物
```

####2.2 三种产物

**产物1：表格摘要（向量化 → ES）**
```
该表记录 Spring Boot 版本演进，包含3个版本，
字段包括：版本、日期、特性。
```
用途：匹配"这个表格讲什么"

**产物2：行组 Chunks（向量化 → ES）**
```
表名：Spring Boot 版本演进
字段：版本、日期、特性
版本：1.0
日期：2014
特性：初始发布

表名：Spring Boot 版本演进
字段：版本、日期、特性
版本：2.0
日期：2018
特性：支持 Java 8
```
用途：匹配"2.0版本有什么特性"

**产物3：结构化数据（写 MySQL）**
```sql
knowledge_table:
  id | title        | document_id | page_start
  t1 | 版本演进表    | doc_001     | 10

knowledge_table_row (EAV):
  table_id | row_index | field_name | field_value
  t1       | 0         | 版本       | 1.0
  t1       | 0         | 日期       | 2014
  t1       | 0         | 特性       | 初始发布
  t1       | 1         | 版本       | 2.0
  t1       | 1         | 日期       | 2018
  t1       | 1         | 特性       | 支持 Java 8
```
用途：Text-to-SQL 精确查询

####2.3 OCR 文字如何转换成表格结构（EAV 格式）

**问题**：OCR 只返回文字，怎么知道哪行哪列？

**答案**：靠 bbox 坐标推断。

**OCR 返回的数据**：
```json
{
  "blocks": [
    {"text": "版本", "bbox": [100, 50, 150, 70], "confidence": 0.95},
    {"text": "日期", "bbox": [200, 50, 250, 70], "confidence": 0.95},
    {"text": "特性", "bbox": [300, 50, 350, 70], "confidence": 0.95},
    {"text": "1.0", "bbox": [100, 70, 150, 90], "confidence": 0.95},
    {"text": "2014", "bbox": [200, 70, 250, 90], "confidence": 0.95},
    {"text": "初始发布", "bbox": [300, 70, 350, 90], "confidence": 0.95}
  ]
}
```

**根据 bbox 恢复行列**：
```
bbox 格式：[x1, y1, x2, y2]
  - (x1, y1) = 左上角坐标
  - (x2, y2) = 右下角坐标

判断规则：
  - y 坐标接近 → 同一行（y1 ≈ y1）
  - x 坐标接近 → 同一列（x1 ≈ x1）
```

**可视化示例**：
```
原始表格：
┌────────┬────────┬────────┐
│ 版本   │ 日期   │ 特性   │  ← y ≈ 50
├────────┼────────┼────────┤
│ 1.0    │ 2014   │ 初始发布│  ← y ≈ 70
└────────┴────────┴────────┘

OCR 返回的 bbox：
  版本: [100, 50, 150, 70]
  日期: [200, 50, 250, 70]
  特性: [300, 50, 350, 70]
  1.0:  [100, 70, 150, 90]
  2014: [200, 70, 250, 90]
  初始发布: [300, 70, 350, 90]

按 y 坐标分组（y ≈ 50, 70）：
  第1行（y≈50）：版本, 日期, 特性
  第2行（y≈70）：1.0, 2014, 初始发布

每行内按 x 坐标排序（x ≈ 100, 200, 300）：
  第1行：版本(100), 日期(200), 特性(300)
  第2行：1.0(100), 2014(200), 初始发布(300)
```

**转换成 EAV 格式**：
```java
// 遍历每一行每一列
for (int rowIndex = 0; rowIndex < dataRows.size(); rowIndex++) {
    for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
        String fieldName = headers.get(colIndex);  // 列名
        String fieldValue = dataRows.get(rowIndex).get(colIndex);  // 单元格值
        
        // 写入 EAV 表
        insert(tableId, rowIndex, fieldName, fieldValue);
    }
}
```

结果：
```sql
table_id | row_index | field_name | field_value
---------|-----------|------------|------------
t001     | 0         | 版本       | 1.0
t001     | 0         | 日期       | 2014
t001     | 0         | 特性       | 初始发布
t001     | 1         | 版本       | 2.0
t001     | 1         | 日期       | 2018
t001     | 1         | 特性       | 支持Java8
```

---

###3. 向量化与存储

####3.1 向量化策略

```
ContentBlock
  ↓
切片成 Chunks
  ├─ 普通文本 Chunk → 向量化 → ES
  ├─ 代码 Chunk → 向量化 → ES
  ├─ 表格摘要 Chunk → 向量化 → ES
  └─ 行组 Chunk → 向量化 → ES
```

向量模型：阿里云 text-embedding-v3

####3.2 ES 索引结构

```json
{
  "mappings": {
    "properties": {
      "content": {"type": "text"},
      "embedding": {"type": "dense_vector", "dims": 1024},
      "documentId": {"type": "keyword"},
      "knowledgeBaseId": {"type": "keyword"},
      "sectionPath": {"type": "text"},
      "pageStart": {"type": "integer"},
      "blockType": {"type": "keyword"},
      "tableId": {"type": "keyword"},
      "parentId": {"type": "keyword"}
    }
  }
}
```

####3.3 元数据用途

| 字段 | 检索时用途 | 展示时用途 |
|---|---|---|
| `knowledgeBaseId` | 前置过滤 | - |
| `sectionPath` | 前置过滤 | 溯源显示章节 |
| `documentId` | - | 溯源关联文档 |
| `pageStart` | 过滤（指定页） | 溯源跳转页码 |
| `blockType` | 区分类型 | - |
| `tableId` | 表格命中查MySQL | - |
| `parentId` | 返回相连子块 | - |

---

###4. 检索流程

####4.1 双路召回

```
用户问题
  ↓
文本向量化
  ↓
┌─────────────────────────────────────────────────────┐
│ BM25 检索（关键词匹配）                               │
│  - 搜索 content 字段                                 │
│  - 适合精确匹配：函数名、版本号、专有名词              │
│  - 元数据前置过滤（knowledgeBaseId, sectionPath）      │
└─────────────────────────────────────────────────────┘
  +
┌─────────────────────────────────────────────────────┐
│ 向量检索（语义相似度）                                 │
│  - 搜索 embedding 字段                               │
│  - 适合模糊匹配：语义相近但表述不同                    │
│  - 元数据前置过滤（knowledgeBaseId, sectionPath）      │
└─────────────────────────────────────────────────────┘
  ↓
RRF 分数融合
  ↓
返回 top-K Chunks
```

####4.2 BM25 vs 向量对比

| 维度 | BM25 | 向量检索 |
|---|---|---|
| **搜索什么** | 关键词匹配 | 语义相似度 |
| **擅长场景** | "@EnableAutoConfiguration" 精确匹配 | "Java怎么学" 匹配"学习Java的路线" |
| **索引内容** | 文本分词后的倒排索引 | embedding 向量 |
| **元数据作用** | 前置过滤（filter） | 前置过滤（filter） |

**注意**：元数据不参与 BM25 打分，只用于前置过滤。

---

###5. 表格问答流程

####5.1 完整流程

```
用户提问
  ↓
调用 search_knowledge 检索
  ↓
返回 top-K Chunks
  ↓
检查是否有 TABLE 类型 Chunk
  ├─ 没有 → 直接用检索结果组织 RAG 上下文
  └─ 有 ↓
       判断问题类型
       ├─ 解释/总结类 → 用表格摘要 + 行组回答
       └─ 精确查询类 → 调用 query_structured_table
  ↓
合并所有上下文
  ↓
调用 LLM 生成最终回答
  ↓
返回带来源引用的回答
```

####5.2 Text-to-SQL 安全设计

**核心原则**：LLM 永远不写 SQL，只返回 JSON 查询计划。

```
用户："2018年以后发布的版本有几个？"
  ↓
LLM 返回 JSON 查询计划：
  {
    "operation": "COUNT",
    "filters": [
      {"field": "日期", "operator": ">", "value": "2018"}
    ],
    "limit": 100
  }
  ↓
Java 校验 + 生成 SQL：
  ├─ 表名在白名单？ ✓
  ├─ 字段名在白名单？ ✓
  ├─ 操作类型允许？ ✓（COUNT 允许）
  └─ 参数需要转义？ ✓
  ↓
生成安全的参数化 SQL：
  SELECT COUNT(DISTINCT row_index)
  FROM knowledge_table_row
  WHERE table_id = ?
    AND field_name = ?
    AND CAST(field_value AS DECIMAL) > ?
  参数: ["t001", "日期", "2018"]
  ↓
执行，返回结果
```

**白名单设计**：
```java
// 允许查询的表
ALLOWED_TABLES = Set.of("knowledge_table", "knowledge_table_row");

// 允许的操作
ALLOWED_OPERATIONS = Set.of("SELECT", "COUNT", "SUM", "AVG", "MIN", "MAX");

// 禁止的关键词
FORBIDDEN_KEYWORDS = Set.of("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "UNION");

// 允许的运算符
ALLOWED_OPERATORS = Set.of("=", "!=", ">", "<", ">=", "<=", "LIKE", "IN");
```

---

###6. 前端溯源

####6.1 溯源数据

每个 Chunk 必须携带：
```json
{
  "content": "Spring Boot 会根据...",
  "documentId": "doc_001",
  "fileName": "Spring Boot 官方文档.pdf",
  "sectionPath": "Spring Boot > 自动配置",
  "pageStart": 12,
  "pageEnd": 12
}
```

####6.2 溯源交互

```
用户看到回答：
"Spring Boot 会根据 classpath 中的依赖自动装配 Bean..."

点击 [查看来源]：
┌────────────────────────────────────────┐
│ 来源：Spring Boot 官方文档.pdf          │
│ 章节：Spring Boot > 自动配置            │
│ 页码：第12页                            │
│ [打开原文] [查看上下文]                  │
└────────────────────────────────────────┘
```

---

###7. 数据结构定义

####7.1 ContentBlock

```java
public record ContentBlock(
    String id,              // UUID
    String documentId,      // 所属文档ID
    String knowledgeBaseId, // 所属知识库ID
    String blockType,       // TEXT / CODE / TABLE / HEADING
    String content,         // 原始文本内容
    String sectionPath,     // 章节路径，如 "Spring Boot > 自动配置"
    int pageStart,          // 起始页码（PDF专用）
    int pageEnd,            // 结束页码
    String tableId,         // 表格ID（TABLE类型专用）
    String parentId,        // 父块ID（用于 parent-child 关系）
    Map<String, String> metadata  // 额外元数据
) {}
```

####7.2 Chunk

```java
public record Chunk(
    String id,                    // UUID
    String contentBlockId,        // 来源 ContentBlock
    String documentId,            // 所属文档
    String knowledgeBaseId,       // 所属知识库
    String chunkType,             // TEXT / CODE / TABLE_SUMMARY / TABLE_ROW_GROUP
    String content,               // 实际文本（向量化的内容）
    String sectionPath,           // 章节路径
    float[] embedding,            // 向量
    int pageStart,
    int pageEnd,
    String tableId,               // 表格ID（表格类型专用）
    Map<String, String> metadata  // 额外元数据
) {}
```

---

###8. 实现阶段

```
Phase 1: 基础框架（1-2天）
  - 定义 ContentBlock、Chunk 数据结构
  - 实现 Markdown 解析器（flexmark-java）
  - 实现 TXT 解析器（按段落切分）
  - 知识库上传接口对接切片逻辑

Phase 2: 向量化存储（1天）
  - ES 索引定义（text + dense_vector）
  - Chunk 写入 ES（含向量化）
  - search_knowledge Tool 实现

Phase 3: PDF + 表格（2-3天）
  - PDF 文本提取（PDFBox）
  - PDF 页面渲染成图片
  - OCR 识别表格
  - TableQualityChecker 检查质量
  - MIMO 增强识别（可选）
  - 表格三种产物生成
  - 结构化数据写入 MySQL

Phase 4: 表格问答（1-2天）
  - query_structured_table Tool 实现
  - LLM 生成 JSON 查询计划
  - Java 生成参数化 SQL
  - SQL 安全校验（白名单）
  - 表格问答流程串联

Phase 5: 联调优化（1天）
  - 端到端流程测试
  - Prompt 调优
  - 错误处理和降级
```

---

###9. 技术选型

| 组件 | 选型 | 说明 |
|---|---|---|
| Markdown 解析 | flexmark-java | Java 生态最成熟的 Markdown 库 |
| PDF 解析 | Apache PDFBox | 已有依赖，功能够用 |
| OCR | 阿里云 OCR | 文字识别 + bbox 坐标 + 置信度 |
| 多模态模型 | MIMO v2.5 | 复杂表格增强识别 |
| 向量模型 | 阿里云 text-embedding-v3 | 已接入，性价比高 |
| 向量存储 | Elasticsearch dense_vector | 已有 ES，支持向量搜索 |
| 表格存储 | MySQL EAV 模式 | 灵活，适合 demo |
| LLM | MIMO / DeepSeek | 已接入，可切换 |

---

###10. 简化点说明（Demo 专用）

| 完整方案 | Demo 简化 |
|---|---|
| HanLP 分词切片 | 按标点/句号切分 |
| LiteParse + OCR + MIMO 三级处理 | 统一用 OCR + MIMO 降级 |
| BM25 + 向量混合检索 | 先只做向量检索 |
| SQL 安全校验 + 参数化 | 简单白名单校验 |
| 3个 Skill | 2个：document-ingest + table-qa |
| 表头单独向量化 | 跳过，够用 |
| 跨页表格合并 | 跳过，每页独立处理 |

---

---

## 第二部分：面试概括版

### 一句话概括

> 基于 RAG 的知识库系统，支持多格式文档解析、Parent-Child 切片、表格多模态识别、双路召回和 Text-to-SQL 精确查询。

### 核心技术点

#### 1. Parent-Child 切片策略

```
子块（Child）：300~600 token，用于向量检索
父块（Parent）：1000~2000 token，用于补充上下文

命中子块后，返回相连子块保留上下文完整性。
```

#### 2. 表格多模态识别

```
OCR 识别 → 质量检查 → 不合格调用 MIMO

三种产物：
  - 表格摘要（向量化）→ 匹配"讲什么"
  - 行组 Chunks（向量化）→ 匹配"具体数据"
  - 结构化数据（EAV）→ Text-to-SQL 精确查询
```

#### 3. 双路召回

```
BM25：关键词匹配（函数名、版本号）
向量：语义相似度（模糊查询）

元数据前置过滤：knowledgeBaseId、sectionPath
```

#### 4. Text-to-SQL 安全设计

```
LLM 只返回 JSON 查询计划
Java 根据白名单生成参数化 SQL
杜绝 SQL 注入风险
```

#### 5. 前端溯源

```
每个 Chunk 保留：documentId、sectionPath、pageStart
点击来源可跳转到原文位置
```

### 关键问答

**Q: 为什么用 Parent-Child Chunk？**
> A: 平衡检索精度和上下文完整性。子块小，检索精准；父块大，保留上下文。命中子块后返回相连子块，避免信息碎片化。

**Q: 表格为什么不用纯 OCR？**
> A: OCR 对复杂表格（合并单元格、多行表头、列错位）识别率低。通过质量检查器检测置信度和列数一致性，不合格时调用 MIMO 增强识别。

**Q: Text-to-SQL 怎么防注入？**
> A: LLM 永远不写 SQL，只返回 JSON 查询计划。Java 根据白名单（表名、字段、操作、运算符）生成参数化 SQL，LLM 无法突破白名单。

**Q: BM25 和向量检索怎么配合？**
> A: BM25 搜关键词（精确），向量搜语义（模糊）。两路召回后 RRF 分数融合。元数据（knowledgeBaseId、sectionPath）用于前置过滤，不参与打分。

**Q: 元数据怎么用？**
> A: 三个用途：①前置过滤（缩小检索范围）②后置重排（可选，业务逻辑）③前端溯源（显示来源、页码）。

**Q: MIMO 什么时候调用？**
> A: OCR 质量检查不合格时：①置信度 < 0.85 ②列数不一致 ③检测到合并单元格 ④多行表头。

---

---

## 第三部分：简历版（两条）

### 版本A：突出广度

```
• 设计 Parent-Child 双层切片策略，子块向量化用于检索，命中后返回相连子块保留上下文；
  PDF 表格通过多模态模型识别，生成摘要向量、行组向量和结构化数据，支持 Text-to-SQL 精确查询

• 实现 BM25 + 向量双路召回，文本命中走 RAG 上下文，表格命中走关系型数据库查询；
  所有切片保留 documentId、sectionPath、pageStart 等元数据，前端支持按来源溯源
```

### 版本B：突出深度

```
• 设计 RAG 知识库的 ContentBlock + Chunk 双层数据结构，通过 sectionPath 保留章节上下文，
  实现 Markdown/TXT/PDF 统一解析和 Parent-Child 切片策略

• 实现 PDF 表格处理流程：OCR 识别 + 质量检查 + MIMO 增强，生成表格摘要/行组/结构化数据，
  设计安全的 Text-to-SQL（LLM 返回 JSON 查询计划，Java 白名单生成 SQL）
```

### 版本C：最简洁

```
• 实现 RAG 知识库系统，支持多格式文档解析和 Parent-Child Chunk 切片策略

• 实现 PDF 表格识别与 Text-to-SQL，通过 OCR + MIMO 提取表格并结构化存储
```

### 推荐版本A

理由：
- **第一句**：展示系统设计能力（Parent-Child、表格三种产物、Text-to-SQL）
- **第二句**：展示技术深度（双路召回、元数据过滤、前端溯源）
- 每句都有3个技术点，信息密度高
- 面试官一眼能看到：**切片策略**、**表格处理**、**检索架构**

---

### 面试追问点

| 技术点 | 追问问题 | 回答要点 |
|---|---|---|
| Parent-Child | 怎么实现？ | 子块带 parentId，命中后按 parentId 查兄弟块 |
| 多模态识别 | 什么时候用 MIMO？ | 置信度低、列数不一致、合并单元格 |
| 三种产物 | 为什么生成三种？ | 摘要匹配"讲什么"，行组匹配"具体数据"，结构化支持 SQL |
| 双路召回 | 怎么融合？ | RRF 分数归一化后加权 |
| Text-to-SQL | 怎么防注入？ | LLM 返回 JSON，Java 白名单生成 SQL |
| 元数据 | 怎么用？ | 前置过滤 + 前端溯源 |
| 前端溯源 | 怎么实现？ | Chunk 带 sectionPath + pageStart，点击跳转 |

---

---

## 附录：关键 Prompt

### 表格识别 Prompt（MIMO）

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

### Text-to-SQL Prompt

```
你是一个查询计划生成器。用户会问关于表格数据的问题，
你需要返回 JSON 格式的查询计划，而不是 SQL。

可用的表：
- knowledge_table_row: 存储表格数据
  字段：table_id, row_index, field_name, field_value

可用的操作：COUNT, SUM, AVG, MIN, MAX, SELECT
可用的运算符：=, !=, >, <, >=, <=, LIKE, IN

示例：
用户："2018年以后的版本有几个？"
返回：
{
  "operation": "COUNT",
  "filters": [
    {"field": "日期", "operator": ">", "value": "2018"}
  ],
  "limit": 100
}

只返回 JSON，不要写 SQL。
```
