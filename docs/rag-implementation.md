# RAG 知识库系统实现方案

> 目标：面试 Demo，展示设计思路，能跑即可

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户界面                              │
│  聊天窗口（支持图片）  +  知识库管理后台  +  模型切换后台      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      应用编排层                              │
│  Agent 路由 → Skill 判断 → 检索 → 工具调用 → LLM 回答        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                        工具层                                │
│  search_knowledge    query_structured_table    get_source    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                        存储层                                │
│  Elasticsearch（文本 + 向量）    MySQL（表格结构化数据）       │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 核心数据结构

###2.1 ContentBlock（内容块）

解析后的统一内容单元，不同类型文件都转成这个格式。

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

###2.2 Chunk（切片单元）

向量化和检索的最小单位。

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

###2.3 结构化表格数据（MySQL）

```sql
-- 表格元数据
CREATE TABLE knowledge_table (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64),
    knowledge_base_id VARCHAR(64),
    title VARCHAR(512),           -- 表格标题
    page_start INT,
    page_end INT,
    section_path VARCHAR(512),
    summary TEXT,                 -- 表格摘要（用于向量化）
    created_at DATETIME
);

-- 表格行数据（EAV 模式）
CREATE TABLE knowledge_table_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id VARCHAR(64),         -- 关联 knowledge_table
    row_index INT,                -- 行号
    field_name VARCHAR(128),      -- 字段名（列名）
    field_value TEXT,             -- 字段值
    INDEX idx_table_id (table_id),
    INDEX idx_table_row (table_id, row_index)
);
```

---

## 3. 文件解析流程

###3.1 文件类型路由

```
文件上传
  ↓
判断文件类型
  ├─ .md   → Markdown 解析器
  ├─ .txt  → 纯文本解析器
  ├─ .pdf  → PDF 解析器（自动判断文本/扫描）
  └─ 其他  → 拒绝或按纯文本处理
```

###3.2 Markdown 解析

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

###3.3 纯文本解析

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

###3.4 PDF 解析

```
PDF 文件
  ↓
PDFBox 提取文本 + 页码
  ↓
每页渲染成图片（用于表格识别）
  ↓
MIMO 分析图片，识别表格
  ↓
生成两种 ContentBlock：
  - TEXT 类型：从 PDFBox 提取的文本
  - TABLE 类型：从 MIMO 识别的表格
```

MIMO Prompt：
```
请识别这张图片中的所有表格，返回 JSON 格式：
{
  "tables": [
    {
      "title": "表格标题",
      "headers": ["列1", "列2"],
      "rows": [["值1", "值2"], ["值3", "值4"]]
    }
  ]
}
如果图片中没有表格，返回 {"tables": []}。
```

---

## 4. 表格处理流程

###4.1 表格入库

```
MIMO 返回表格 JSON
  ↓
解析 JSON
  ↓
生成三种产物：
  ├─1. 表格摘要 Chunk（向量化 → ES）
  │     "该表记录 XXX，包含 N 行数据，字段包括 A、B、C..."
  │
  ├─2. 行组 Chunks（向量化 → ES）
  │     每5行一组，每组重复表头：
  │     "表名：XXX\n字段：A, B, C\nA:值1\nB:值2\nC:值3\nA:值4\nB:值5\nC:值6"
  │
  └─3. 结构化数据（写 MySQL）
        knowledge_table: 表格元数据
        knowledge_table_row: 每行每字段一条记录
```

###4.2 表格向量化策略

```
表格摘要向量 → 用于匹配"这个表格讲什么"
  示例问题："Spring Boot 版本演进表在哪里？"

行组向量 → 用于匹配具体数据
  示例问题："2.0版本有什么特性？"

表头向量（可选）→ 用于匹配"有哪些字段"
  示例问题："这个表有哪些列？"
```

---

## 5. 检索流程

###5.1 知识库检索（search_knowledge）

```
用户问题
  ↓
文本向量化（text-embedding-v3）
  ↓
ES 向量相似度搜索（top-K）
  ↓
返回 Chunk 列表，包含：
  - chunk 内容
  - blockType（TEXT / CODE / TABLE_SUMMARY / TABLE_ROW_GROUP）
  - tableId（表格专用）
  - sectionPath
  - pageStart / pageEnd
  - score（相似度分数）
```

###5.2 表格查询（query_structured_table）

```
用户问题涉及表格数据
  ↓
获取表格 Schema：
  - 表格标题
  - 字段列表
  - 示例数据（前3行）
  ↓
构造 Prompt，让 LLM 生成 SQL：
  """
  表结构：knowledge_table_row (table_id, row_index, field_name, field_value)
  示例：table_id='t001', row_index=0, field_name='版本', field_value='1.0'
  问题：2.0版本有什么特性？
  请生成 SELECT SQL：
  """
  ↓
LLM 返回 SQL + 参数
  ↓
后端校验（白名单表名、只允许 SELECT）
  ↓
执行参数化 SQL
  ↓
返回查询结果
```

---

## 6. 完整问答流程

```
用户提问
  ↓
Agent 路由 → Skill 判断
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

---

## 7. Tool 定义

###7.1 search_knowledge

```yaml
name: search_knowledge
description: 搜索知识库，返回相关文档片段
input:
  query: string              # 用户查询
  knowledgeBaseId: string    # 知识库ID（可选）
  topK: int                  # 返回数量，默认5
output:
  chunks:
    - content: string        # 文本内容
      blockType: string      # TEXT / CODE / TABLE_SUMMARY / TABLE_ROW_GROUP
      tableId: string        # 表格ID（表格类型专用）
      sectionPath: string    # 章节路径
      page: int              # 页码
      fileName: string       # 文件名
      score: float           # 相似度分数
```

###7.2 query_structured_table

```yaml
name: query_structured_table
description: 查询结构化表格数据，用于统计、过滤、排序
input:
  tableId: string            # 表格ID
  question: string           # 用户原始问题（用于生成SQL）
  limit: int                 # 最大返回行数，默认100
output:
  columns: string[]          # 列名
  rows: object[][]           # 数据行
  total: int                 # 总行数
  sql: string                # 生成的SQL（调试用）
```

###7.3 get_source_page

```yaml
name: get_source_page
description: 获取原始文档页面，用于来源核验
input:
  documentId: string         # 文档ID
  pageNumber: int            # 页码
output:
  pageImage: bytes           # 页面图片（PDF专用）
  pageText: string           # 页面文本
```

---

## 8. Skill 定义

###8.1 document-ingest（文件入库）

```yaml
name: document-ingest
trigger: 用户上传文件到知识库
states:
  - route_by_type:
      md → parse_markdown
      txt → parse_txt
      pdf → parse_pdf

  - parse_markdown:
      tool: markdown_parser
      output: ContentBlock[]

  - parse_txt:
      tool: txt_parser
      params: { child_tokens: [300,600], overlap: 50 }
      output: ContentBlock[]

  - parse_pdf:
      tool: pdf_parser + mimo_vision
      output: ContentBlock[] (TEXT + TABLE)

  - chunk_and_vectorize:
      input: ContentBlock[]
      steps:
        - 切片成 Chunks
        - 调用 embedding API 生成向量
        - 写入 ES
        - 表格数据写入 MySQL
```

###8.2 table-qa（表格问答）

```yaml
name: table-qa
trigger: 检索结果中存在 TABLE 类型 Chunk
states:
  - classify_question:
      explain/summary → use_summary
      precise_query → generate_sql

  - use_summary:
      input: 表格摘要 + 行组Chunk + 用户问题
      tool: llm_chat

  - generate_sql:
      steps:
        - 获取表格 Schema
        - LLM 生成 SQL
        - 校验 SQL 安全
        - 执行查询
        - 返回结果
```

---

## 9. 实现阶段

### Phase 1: 基础框架（1-2天）

- [ ] 定义 ContentBlock、Chunk 数据结构
- [ ] 实现 Markdown 解析器（flexmark-java）
- [ ] 实现 TXT 解析器（按段落切分）
- [ ] 知识库上传接口对接切片逻辑

### Phase 2: 向量化存储（1天）

- [ ] ES 索引定义（text + dense_vector）
- [ ] Chunk 写入 ES（含向量化）
- [ ] search_knowledge Tool 实现

### Phase 3: PDF + 表格（2-3天）

- [ ] PDF 文本提取（PDFBox）
- [ ] PDF 页面渲染成图片
- [ ] MIMO 表格识别集成
- [ ] 表格三种产物生成
- [ ] 结构化数据写入 MySQL

### Phase 4: 表格问答（1-2天）

- [ ] query_structured_table Tool 实现
- [ ] LLM 生成 SQL 逻辑
- [ ] SQL 安全校验（白名单）
- [ ] 表格问答流程串联

### Phase 5: 联调优化（1天）

- [ ] 端到端流程测试
- [ ] Prompt 调优
- [ ] 错误处理和降级

---

## 10. 技术选型

| 组件 | 选型 | 说明 |
|---|---|---|
| Markdown 解析 | flexmark-java | Java 生态最成熟的 Markdown 库 |
| PDF 解析 | Apache PDFBox | 已有依赖，功能够用 |
| 多模态模型 | MIMO v2.5 | 表格识别 + 图片理解 |
| 向量模型 | 阿里云 text-embedding-v3 | 已接入，性价比高 |
| 向量存储 | Elasticsearch dense_vector | 已有 ES，支持向量搜索 |
| 表格存储 | MySQL EAV 模式 | 灵活，适合 demo |
| LLM | MIMO / DeepSeek | 已接入，可切换 |

---

## 11. 简化点说明（Demo 专用）

| 完整方案 | Demo 简化 |
|---|---|
| HanLP 分词切片 | 按标点/句号切分 |
| LiteParse + OCR + MIMO 三级处理 | 统一用 MIMO 看图 |
| BM25 + 向量混合检索 | 只做向量检索 |
| SQL 安全校验 + 参数化 | 简单白名单校验 |
| 3个 Skill | 2个：document-ingest + table-qa |
| 表头单独向量化 | 跳过，够用 |
| 跨页表格合并 | 跳过，每页独立处理 |

---

## 12. 关键 Prompt 示例

### 表格识别 Prompt

```
你是一个表格识别专家。请识别图片中的所有表格，返回 JSON 格式。

要求：
1. 识别所有表格，包括嵌套表格
2. 提取表格标题（如果有的话）
3. 提取表头行（第一行通常是表头）
4. 提取所有数据行
5. 如果有合并单元格，请展开为多个单元格

返回格式：
{
  "tables": [
    {
      "title": "表格标题（如果没有则为空字符串）",
      "headers": ["列1", "列2", "列3"],
      "rows": [
        ["值1", "值2", "值3"],
        ["值4", "值5", "值6"]
      ]
    }
  ]
}

如果图片中没有表格，返回：{"tables": []}

只返回 JSON，不要其他文字。
```

### Text-to-SQL Prompt

```
你是一个 SQL 专家。根据以下表结构和用户问题，生成 SQL 查询。

表结构：
表名：knowledge_table_row
字段：
- table_id (VARCHAR): 表格ID
- row_index (INT): 行号
- field_name (VARCHAR): 字段名
- field_value (TEXT): 字段值

示例数据：
table_id | row_index | field_name | field_value
---------|-----------|------------|------------
t001     | 0         | 版本       | 1.0
t001     | 0         | 日期       | 2014
t001     | 0         | 特性       | 初始发布
t001     | 1         | 版本       | 2.0
t001     | 1         | 日期       | 2018
t001     | 1         | 特性       | 支持 Java 8

用户问题：{question}

要求：
1. 只生成 SELECT 语句
2. 使用参数化查询（用 ? 占位符）
3. 限制返回行数（LIMIT 100）
4. 如果需要过滤，先找到对应的 row_index，再查询其他字段

请返回 JSON 格式：
{
  "sql": "SELECT ... FROM knowledge_table_row WHERE ...",
  "params": ["param1", "param2"],
  "explanation": "查询逻辑说明"
}
```

---

## 13. 验收标准

### 功能验收

- [ ] 能上传 Markdown/TXT/PDF 文件到知识库
- [ ] 文件被正确解析和切片
- [ ] 切片后的 Chunk 能向量化并写入 ES
- [ ] 能通过 search_knowledge 检索到相关内容
- [ ] PDF 中的表格能被识别并结构化存储
- [ ] 能通过 query_structured_table 查询表格数据
- [ ] 表格问答流程能端到端跑通

### 面试展示点

- [ ] 能画出完整架构图
- [ ] 能解释 ContentBlock / Chunk 设计
- [ ] 能说明表格三种产物（摘要、行组、结构化）
- [ ] 能说明 Text-to-SQL 的实现思路
- [ ] 能说明 Skill 和 Tool 的职责划分
- [ ] 能说明简化点和生产环境需要加强的地方
