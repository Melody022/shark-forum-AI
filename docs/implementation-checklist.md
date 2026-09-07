# RAG 知识库系统实现检查清单

> 验证当前项目实现状态

---

## 已实现的功能（可以跑通）

###1. 文件解析层
- ✅ DocumentParser 接口定义
- ✅ TextDocumentParser（TXT/Markdown/HTML）
- ✅ PdfParser（PDF，使用 PDFBox + LiteParse）
- ✅ ContentBlock 统一数据结构（BlockType: TEXT/HEADING/TABLE/CODE/LIST）
- ✅ ParseResult 解析结果

###2. 切片层
- ✅ HierarchicalChunker（父子切片）
  - 父块用于上下文恢复，不参与向量检索
  - 子块用于向量检索
  - 不同内容类型（TABLE/CODE/LIST）使用不同的切分策略
  - 大表格切分时重复表头
- ✅ ChunkDraft 数据结构（ROLE_PARENT/ROLE_CHILD）
- ✅ SemanticTextSplitter（备选切片器）

###3. 存储层
- ✅ KnowledgeChunk 实体（包含 chunk_role/parent_id/parent_key/block_type/section_path 等字段）
- ✅ V4 数据库迁移（添加父子切片字段）
- ✅ Elasticsearch 向量存储
- ✅ MySQL 知识库存储

###4. 向量化层
- ✅ VectorizationService（Spring AI EmbeddingModel）
- ✅ BatchEmbeddingService（批量向量化）
- ✅ Aliyun text-embedding-v3 集成

###5. 检索层
- ✅ VectorSearchService（向量搜索接口）
- ✅ VectorSearchServiceImpl（混合检索：BM25 + 向量 + RRF 融合）
- ✅ SearchResult 结果结构

###6. 文档处理流程
- ✅ DocumentProcessingService（处理文档：解析 + 向量化）
- ✅ DocumentProcessingServiceImpl（异步处理，批量向量化，写入 ES）

###7. 知识库管理
- ✅ KnowledgeBaseService（知识库 CRUD）
- ✅ KnowledgeBaseController（REST API）
- ✅ KnowledgeDocument 实体
- ✅ KnowledgeBase 实体

###8. 已有测试
- ✅ TextDocumentParserTest（验证 Markdown/HTML 解析）
- ✅ HierarchicalChunkerTest（验证父子切片）
- ✅ 集成测试框架

---

## 待实现的功能

###1. 表格处理（高优先级）
- ❌ 表格识别（LiteParse + OCR + MIMO）
  - 需要集成阿里云 OCR
  - 需要实现 TableQualityChecker
  - 需要实现 MIMO 增强识别
- ❌ 表格三种产物生成
  - 表格摘要（向量化 → ES）
  - 行组 Chunks（向量化 → ES）
  - 结构化数据（EAV 格式 → MySQL）
- ❌ knowledge_table 表创建
- ❌ knowledge_table_row 表创建（EAV 格式）
- ❌ 表格解析器（TableParser）

###2. Text-to-SQL（高优先级）
- ❌ query_structured_table Tool
- ❌ LLM 生成 JSON 查询计划的 Prompt
- ❌ Java 生成 SQL 的白名单校验
- ❌ 参数化 SQL 执行
- ❌ TableQueryService

###3. 表格问答流程（中优先级）
- ❌ table-qa Skill 定义
- ❌ 表格候选检测逻辑
- ❌ 问题类型判断（解释类/精确查询类）
- ❌ 表格结果与 RAG 上下文合并

###4. 前端溯源（低优先级）
- ❌ 溯源数据展示（来源、章节、页码）
- ❌ 溯源跳转逻辑

---

## 验证步骤

### Step1：编译项目
```bash
cd D:/IdeaProjects/paicoding-ai
mvn clean compile -DskipTests
```

### Step2：运行测试
```bash
mvn test
```

### Step3：验证核心组件
1. **TextDocumentParser**
   - 创建一个 Markdown 测试文件
   - 调用解析器，检查 ContentBlock 列表
   - 验证 HEADING/TEXT/CODE/LIST 类型是否正确

2. **HierarchicalChunker**
   - 传入 ContentBlock 列表
   - 检查是否生成父块和子块
   - 验证父块 searchable=false，子块 searchable=true
   - 验证大表格切分时表头重复

3. **VectorizationService**
   - 传入文本，返回向量
   - 验证向量维度（1024）

4. **VectorSearchService**
   - 传入查询，返回检索结果
   - 验证返回包含 score、chunkId、content 等字段

### Step4：端到端测试
1. 创建知识库
2. 上传 Markdown/TXT 文件
3. 等待异步处理完成（解析 + 向量化）
4. 查询知识库
5. 验证返回相关内容

---

## 快速验证代码

### 验证 TextDocumentParser
```java
@SpringBootTest
class TextDocumentParserTest {
    
    @Autowired
    private TextDocumentParser parser;
    
    @Test
    void testMarkdownParsing() throws IOException {
        // 创建测试文件
        Path file = Files.createTempFile("test-", ".md");
        Files.writeString(file, """
            # Spring Boot
            ## 自动配置
            Spring Boot 会根据 classpath 中的依赖自动装配 Bean。
            
            ```java
            @EnableAutoConfiguration
            ```
            """);
        
        // 解析
        ParseResult result = parser.parse(file.toFile());
        
        // 验证
        assertTrue(result.isSuccess());
        assertEquals(4, result.getContentBlocks().size());  // HEADING, HEADING, TEXT, CODE
        assertEquals(BlockType.CODE, result.getContentBlocks().get(3).getType());
        
        Files.deleteIfExists(file);
    }
}
```

### 验证 HierarchicalChunker
```java
@SpringBootTest
class HierarchicalChunkerTest {
    
    @Autowired
    private HierarchicalChunker chunker;
    
    @Test
    void testParentChildChunking() {
        // 创建 ContentBlock
        List<ContentBlock> blocks = List.of(
            ContentBlock.builder()
                .blockId("h1")
                .type(BlockType.HEADING)
                .content("自动配置")
                .sectionPath("第3章 > 自动配置")
                .parentId("section-1")
                .build(),
            ContentBlock.builder()
                .blockId("text-1")
                .type(BlockType.TEXT)
                .content("自动配置根据 classpath 条件加载 Bean。")
                .sectionPath("第3章 > 自动配置")
                .parentId("section-1")
                .build()
        );
        
        // 切片
        List<ChunkDraft> drafts = chunker.chunk(blocks);
        
        // 验证
        assertEquals(1, drafts.stream().filter(d -> d.getRole().equals("PARENT")).count());
        assertEquals(1, drafts.stream().filter(d -> d.getRole().equals("CHILD")).count());
    }
}
```

---

## 下一步计划

### 阶段1：基础跑通（今天）
- [x] 编译项目
- [x] 运行测试
- [ ] 创建测试文件，验证解析流程
- [ ] 验证向量化流程
- [ ] 验证检索流程

### 阶段2：表格处理（明天）
- [ ] 集成阿里云 OCR
- [ ] 实现 TableQualityChecker
- [ ] 实现表格三种产物生成
- [ ] 创建 knowledge_table/knowledge_table_row 表

### 阶段3：Text-to-SQL（后天）
- [ ] 实现 query_structured_table Tool
- [ ] 实现白名单校验
- [ ] 实现参数化 SQL
- [ ] 端到端测试

---

## 项目结构

```
paicoding-ai/
├── src/
│   ├── main/
│   │   ├── java/com/itswy/paicodingai/
│   │   │   ├── file/parser/          ← 文件解析器
│   │   │   │   ├── DocumentParser.java
│   │   │   │   ├── TextDocumentParser.java
│   │   │   │   ├── PdfParser.java
│   │   │   │   ├── ContentBlock.java
│   │   │   │   └── BlockType.java
│   │   │   ├── rag/splitter/         ← 切片器
│   │   │   │   ├── HierarchicalChunker.java
│   │   │   │   └── SemanticTextSplitter.java
│   │   │   ├── knowledge/            ← 知识库服务
│   │   │   │   ├── service/
│   │   │   │   │   ├── VectorSearchService.java
│   │   │   │   │   ├── VectorizationService.java
│   │   │   │   │   └── impl/
│   │   │   │   ├── entity/
│   │   │   │   └── mapper/
│   │   │   └── controller/
│   │   │       ├── KnowledgeBaseController.java
│   │   │       └── VectorSearchController.java
│   │   └── resources/
│   │       ├── db/migration/         ← 数据库迁移
│   │       │   ├── V3__knowledge_base.sql
│   │       │   └── V4__hierarchical_rag_chunks.sql
│   │       └── application.yml
│   └── test/
│       └── java/com/itswy/paicodingai/
│           ├── file/parser/
│           │   └── TextDocumentParserTest.java
│           └── rag/splitter/
│               └── HierarchicalChunkerTest.java
```

---

## 总结

**当前状态**：项目已经具备完整的解析→切片→向量化→检索链路，可以跑通基础功能。

**下一步**：
1. 先验证基础功能（Markdown/TXT 解析 + 切片 + 向量化 + 检索）
2. 再添加表格处理（OCR + MIMO）
3. 最后实现 Text-to-SQL

**关键文件**：
- 解析器：`TextDocumentParser.java`、`PdfParser.java`
- 切片器：`HierarchicalChunker.java`
- 向量化：`VectorizationServiceImpl.java`
- 检索：`VectorSearchServiceImpl.java`
- 数据库：`V4__hierarchical_rag_chunks.sql`
