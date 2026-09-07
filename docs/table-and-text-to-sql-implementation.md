# RAG 知识库系统实现总结

> 表格处理 + Text-to-SQL 完整实现

---

## 实现内容

### 1. 数据库层

#### 表格元数据表（knowledge_table）
```sql
CREATE TABLE knowledge_table (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64),
    knowledge_base_id VARCHAR(64),
    title VARCHAR(512),
    page_start INT,
    page_end INT,
    section_path VARCHAR(512),
    summary TEXT,  -- 表格摘要（用于向量化）
    headers_json TEXT,  -- 表头JSON
    row_count INT,
    column_count INT
);
```

#### 表格行数据表（EAV 格式）
```sql
CREATE TABLE knowledge_table_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id VARCHAR(64),
    row_index INT,
    field_name VARCHAR(128),  -- 列名
    field_value TEXT,  -- 字段值
    normalized_value VARCHAR(512)  -- 归一化值（用于查询）
);
```

### 2. 表格处理服务

#### 核心功能
- **processTable()**：处理表格，生成三种产物
- **generateTableSummary()**：生成表格摘要（用于向量化）
- **generateRowGroupChunks()**：生成行组Chunks（用于向量化）
- **toEavFormat()**：转换为EAV格式（用于存储）
- **getTableSchema()**：获取表格Schema（用于Text-to-SQL）

#### 三种产物

**产物1：表格摘要**
```
该表标题为「Spring Boot 版本演进」，包含3行数据，字段包括：版本、日期、特性。
```
用途：匹配"这个表格讲什么"

**产物2：行组Chunks**
```
表名：Spring Boot 版本演进
字段：版本, 日期, 特性
版本：1.0
日期：2014
特性：初始发布
```
用途：匹配"2.0版本有什么特性"

**产物3：结构化数据（EAV）**
```
table_id | row_index | field_name | field_value
table_001 | 0 | 版本 | 1.0
table_001 | 0 | 日期 | 2014
table_001 | 0 | 特性 | 初始发布
```
用途：Text-to-SQL 精确查询

### 3. OCR 质量检查服务

#### 核心功能
- **needMimo()**：判断是否需要调用MIMO增强识别
- **reconstructTable()**：根据OCR返回的bbox坐标重建表格结构

#### 检测规则
1. 置信度 < 0.85 → 调用MIMO
2. 列数不一致 → 调用MIMO
3. 检测到合并单元格 → 调用MIMO
4. 行数太少（< 2）→ 调用MIMO

### 4. Text-to-SQL 服务

#### 核心功能
- **executeQuery()**：执行Text-to-SQL查询
- **buildPrompt()**：构造LLM提示词
- **generateQueryPlan()**：生成JSON查询计划
- **buildSql()**：根据查询计划生成SQL
- **validateSql()**：校验SQL安全性
- **executeQuery()**：执行参数化查询

#### 安全设计

**核心原则**：LLM 永远不写 SQL，只返回 JSON 查询计划

```
用户问题
  ↓
LLM 返回 JSON 查询计划
  {
    "operation": "COUNT",
    "filters": [
      {"field": "日期", "operator": ">", "value": "2018"}
    ]
  }
  ↓
Java 校验 + 生成参数化 SQL
  - 表名在白名单？ ✓
  - 字段名在白名单？ ✓
  - 操作类型允许？ ✓
  ↓
执行参数化 SQL
  ↓
返回结果
```

**白名单**：
- 允许查询的表：knowledge_table_row
- 允许的操作：SELECT, COUNT, SUM, AVG, MIN, MAX
- 禁止的关键词：INSERT, UPDATE, DELETE, DROP, ALTER, UNION, --, ;

### 5. 简单表格解析器

#### 核心功能
- **parseMarkdownTable()**：解析Markdown表格
- **parseSimpleTable()**：解析简单格式表格
- **hasTable()**：检测文本中是否包含表格

---

## 关键文件

### 实体类
- `KnowledgeTable.java` - 表格元数据实体
- `KnowledgeTableRow.java` - 表格行数据实体（EAV格式）

### Mapper
- `KnowledgeTableMapper.java` - 表格Mapper
- `KnowledgeTableRowMapper.java` - 表格行Mapper

### 服务接口
- `TableProcessingService.java` - 表格处理接口
- `TextToSqlService.java` - Text-to-SQL接口
- `OcrQualityService.java` - OCR质量检查接口

### 服务实现
- `TableProcessingServiceImpl.java` - 表格处理实现
- `TextToSqlServiceImpl.java` - Text-to-SQL实现
- `OcrQualityServiceImpl.java` - OCR质量检查实现

### 解析器
- `SimpleTableParser.java` - 简单表格解析器

### 数据库迁移
- `V6__knowledge_table.sql` - 创建表格相关表

### 测试
- `RagEndToEndTest.java` - 端到端测试

---

## 运行测试

### 编译项目
```bash
cd D:/IdeaProjects/paicoding-ai
mvn clean compile -DskipTests
```

### 运行端到端测试
```bash
mvn test -Dtest=RagEndToEndTest
```

### 测试内容
1. **Markdown解析和切片测试** - 验证TextDocumentParser和HierarchicalChunker
2. **表格处理测试** - 验证三种产物生成（摘要、行组、EAV）
3. **Text-to-SQL测试** - 验证查询计划生成和SQL执行
4. **表格解析器测试** - 验证Markdown表格解析
5. **端到端流程测试** - 验证完整流程

---

## 使用示例

### 示例1：处理表格

```java
@Autowired
private TableProcessingService tableProcessingService;

// 表格数据
List<String> headers = List.of("版本", "日期", "特性");
List<List<String>> rows = List.of(
    List.of("1.0", "2014", "初始发布"),
    List.of("2.0", "2018", "支持 Java 8")
);

// 处理表格
KnowledgeTable table = tableProcessingService.processTable(
    "doc_001",
    "kb_001",
    "Spring Boot 版本演进",
    headers,
    rows,
    1,
    "Spring Boot > 版本信息"
);

// 获取表格摘要（用于向量化）
String summary = tableProcessingService.generateTableSummary("Spring Boot 版本演进", headers, rows.size());
// 结果："该表标题为「Spring Boot 版本演进」，包含2行数据，字段包括：版本、日期、特性。"

// 获取行组Chunks（用于向量化）
List<String> chunks = tableProcessingService.generateRowGroupChunks("Spring Boot 版本演进", headers, rows, 10);
// 结果：["表名：Spring Boot 版本演进\n字段：版本, 日期, 特性\n版本：1.0\n日期：2014\n特性：初始发布\n版本：2.0\n日期：2018\n特性：支持 Java 8"]
```

### 示例2：Text-to-SQL 查询

```java
@Autowired
private TextToSqlService textToSqlService;

// 执行查询
var result = textToSqlService.executeQuery(
    "table_001",
    "2018年以后发布的版本有几个？",
    100
);

System.out.println("SQL: " + result.sql());
System.out.println("结果: " + result.explanation());
```

---

## 安全保障

### 1. SQL注入防护
- LLM 只返回 JSON 查询计划，不写 SQL
- Java 根据白名单生成 SQL
- 所有参数使用 PreparedStatement

### 2. 白名单校验
- 只允许查询 knowledge_table_row 表
- 只允许 SELECT, COUNT, SUM, AVG, MIN, MAX 操作
- 禁止 INSERT, UPDATE, DELETE, DROP, ALTER, UNION, --, ;

### 3. 参数化查询
```java
// 使用 ? 占位符
String sql = "SELECT * FROM knowledge_table_row WHERE table_id = ? AND field_name = ?";

// 使用 PreparedStatement 设置参数
jdbcTemplate.queryForList(sql, tableId, fieldName);
```

---

## 下一步

### 短期
- [ ] 集成阿里云 OCR
- [ ] 集成 MIMO 增强识别
- [ ] 实现完整的表格识别流程

### 中期
- [ ] 实现向量化表格摘要和行组Chunks
- [ ] 实现表格检索
- [ ] 实现前端溯源

### 长期
- [ ] 实现跨页表格合并
- [ ] 实现复杂表格处理（合并单元格、多行表头）
- [ ] 优化Text-to-SQL的LLM提示词

---

## 项目结构

```
paicoding-ai/
├── src/main/java/com/itswy/paicodingai/
│   ├── file/parser/
│   │   ├── ContentBlock.java
│   │   ├── BlockType.java
│   │   ├── TextDocumentParser.java
│   │   ├── PdfParser.java
│   │   └── SimpleTableParser.java
│   ├── knowledge/
│   │   ├── entity/
│   │   │   ├── KnowledgeTable.java
│   │   │   └── KnowledgeTableRow.java
│   │   ├── mapper/
│   │   │   ├── KnowledgeTableMapper.java
│   │   │   └── KnowledgeTableRowMapper.java
│   │   └── service/
│   │       ├── TableProcessingService.java
│   │       ├── TextToSqlService.java
│   │       ├── OcrQualityService.java
│   │       └── impl/
│   │           ├── TableProcessingServiceImpl.java
│   │           ├── TextToSqlServiceImpl.java
│   │           └── OcrQualityServiceImpl.java
│   └── rag/splitter/
│       └── HierarchicalChunker.java
├── src/main/resources/db/migration/
│   ├── V3__knowledge_base.sql
│   ├── V4__hierarchical_rag_chunks.sql
│   └── V6__knowledge_table.sql
├── src/test/java/
│   └── RagEndToEndTest.java
└── docs/
    ├── rag-summary.md
    ├── ocr-mimo-fallback-strategy.md
    └── implementation-checklist.md
```

---

## 总结

✅ **已实现**：
1. 表格元数据表和EAV格式行数据表
2. 表格处理服务（三种产物生成）
3. OCR质量检查服务（检测是否需要MIMO）
4. Text-to-SQL服务（安全的查询执行）
5. 简单表格解析器（Markdown表格）
6. 完整的端到端测试

✅ **安全保障**：
1. LLM 只返回JSON查询计划
2. Java 白名单生成SQL
3. 参数化查询防止注入

✅ **可以运行**：
1. 编译通过
2. 端到端测试可执行
3. 基础功能验证完成

下一步：继续完善表格识别和向量化流程。
