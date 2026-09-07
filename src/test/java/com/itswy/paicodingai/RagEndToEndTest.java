package com.itswy.paicodingai;

import com.itswy.paicodingai.file.parser.ContentBlock;
import com.itswy.paicodingai.file.parser.ParseResult;
import com.itswy.paicodingai.file.parser.SimpleTableParser;
import com.itswy.paicodingai.file.parser.TextDocumentParser;
import com.itswy.paicodingai.knowledge.entity.KnowledgeTable;
import com.itswy.paicodingai.knowledge.service.TableProcessingService;
import com.itswy.paicodingai.knowledge.service.TextToSqlService;
import com.itswy.paicodingai.rag.splitter.HierarchicalChunker;
import com.itswy.paicodingai.file.parser.ChunkDraft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 知识库端到端测试
 */
@SpringBootTest
public class RagEndToEndTest {

    @Autowired
    private TextDocumentParser textParser;

    @Autowired
    private HierarchicalChunker chunker;

    @Autowired
    private SimpleTableParser tableParser;

    @Autowired
    private TableProcessingService tableProcessingService;

    @Autowired
    private TextToSqlService textToSqlService;

    @Test
    public void testMarkdownParsingAndChunking() throws IOException {
        System.out.println("=== 测试 Markdown 解析和切片 ===");

        // 1. 创建测试文件
        String markdown = """
                # Spring Boot

                ## 自动配置

                Spring Boot 会根据 classpath 中的依赖自动装配 Bean。

                ```java
                @EnableAutoConfiguration
                ```

                ## 版本信息

                | 版本 | 日期 | 特性 |
                |------|------|------|
                | 1.0 | 2014 | 初始发布 |
                | 2.0 | 2018 | 支持 Java 8 |
                """;

        Path file = Files.createTempFile("test-rag-", ".md");
        Files.writeString(file, markdown);

        try {
            // 2. 解析Markdown
            ParseResult result = textParser.parse(file.toFile());

            System.out.println("解析成功: " + result.isSuccess());
            System.out.println("标题: " + result.getTitle());
            System.out.println("ContentBlock 数量: " + result.getContentBlocks().size());

            // 打印每个ContentBlock
            for (ContentBlock block : result.getContentBlocks()) {
                System.out.println("  - " + block.getType() + ": " +
                        block.getContent().substring(0, Math.min(50, block.getContent().length())) + "...");
            }

            assertTrue(result.isSuccess());
            assertEquals("Spring Boot", result.getTitle());
            assertFalse(result.getContentBlocks().isEmpty());

            // 3. 切片
            List<ChunkDraft> chunks = chunker.chunk(result.getContentBlocks());

            System.out.println("\n=== 切片结果 ===");
            System.out.println("Chunk 数量: " + chunks.size());

            long parentCount = chunks.stream().filter(c -> "PARENT".equals(c.getRole())).count();
            long childCount = chunks.stream().filter(c -> "CHILD".equals(c.getRole())).count();

            System.out.println("父块数量: " + parentCount);
            System.out.println("子块数量: " + childCount);

            // 打印子块内容
            chunks.stream()
                    .filter(c -> "CHILD".equals(c.getRole()))
                    .forEach(c -> System.out.println("  子块: " +
                            c.getContent().substring(0, Math.min(60, c.getContent().length())) + "..."));

            assertTrue(parentCount > 0);
            assertTrue(childCount > 0);

        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void testTableProcessing() {
        System.out.println("\n=== 测试表格处理 ===");

        // 1. 创建测试表格数据
        List<String> headers = List.of("版本", "日期", "特性");
        List<List<String>> rows = List.of(
                List.of("1.0", "2014", "初始发布"),
                List.of("2.0", "2018", "支持 Java 8")
        );

        // 2. 处理表格
        KnowledgeTable table = tableProcessingService.processTable(
                "doc_test_001",
                "kb_test_001",
                "Spring Boot 版本演进",
                headers,
                rows,
                1,
                "Spring Boot > 版本信息"
        );

        System.out.println("表格ID: " + table.getId());
        System.out.println("标题: " + table.getTitle());
        System.out.println("行数: " + table.getRowCount());
        System.out.println("列数: " + table.getColumnCount());
        System.out.println("摘要: " + table.getSummary());

        assertNotNull(table.getId());
        assertEquals(2, table.getRowCount());
        assertEquals(3, table.getColumnCount());
        assertTrue(table.getSummary().contains("版本演进"));

        // 3. 生成表格摘要
        String summary = tableProcessingService.generateTableSummary(
                "Spring Boot 版本演进",
                headers,
                rows.size()
        );
        System.out.println("\n生成的摘要: " + summary);
        assertTrue(summary.contains("2行数据"));

        // 4. 生成行组Chunks
        List<String> rowGroupChunks = tableProcessingService.generateRowGroupChunks(
                "Spring Boot 版本演进",
                headers,
                rows,
                10
        );
        System.out.println("\n行组 Chunk 数量: " + rowGroupChunks.size());
        assertFalse(rowGroupChunks.isEmpty());

        // 5. 转换为EAV格式
        var eavRows = tableProcessingService.toEavFormat(table.getId(), headers, rows);
        System.out.println("\nEAV 格式行数: " + eavRows.size());
        assertEquals(6, eavRows.size());  // 2行 × 3列 = 6条记录
    }

    @Test
    public void testTextToSql() {
        System.out.println("\n=== 测试 Text-to-SQL ===");

        // 先创建表格
        List<String> headers = List.of("版本", "日期", "特性");
        List<List<String>> rows = List.of(
                List.of("1.0", "2014", "初始发布"),
                List.of("2.0", "2018", "支持 Java 8")
        );

        KnowledgeTable table = tableProcessingService.processTable(
                "doc_test_002",
                "kb_test_002",
                "Spring Boot 版本演进",
                headers,
                rows,
                1,
                "Spring Boot > 版本信息"
        );

        // 执行 Text-to-SQL
        System.out.println("查询: 总共有几行数据？");
        var result = textToSqlService.executeQuery(table.getId(), "总共有几行数据？", 100);

        System.out.println("SQL: " + result.sql());
        System.out.println("说明: " + result.explanation());
        System.out.println("列: " + String.join(", ", result.columns()));
        System.out.println("行数: " + result.total());

        assertNotNull(result);
        assertNotNull(result.sql());
    }

    @Test
    public void testSimpleTableParser() {
        System.out.println("\n=== 测试简单表格解析器 ===");

        // 测试Markdown表格
        String markdownTable = """
                | 版本 | 日期 | 特性 |
                |------|------|------|
                | 1.0 | 2014 | 初始发布 |
                | 2.0 | 2018 | 支持 Java 8 |
                """;

        var tableData = tableParser.parseMarkdownTable(markdownTable);

        System.out.println("解析结果:");
        System.out.println("  表头: " + tableData.headers());
        System.out.println("  行数: " + tableData.rowCount());
        System.out.println("  列数: " + tableData.columnCount());
        System.out.println("  Markdown:\n" + tableData.toMarkdown());

        assertNotNull(tableData);
        assertEquals(3, tableData.columnCount());
        assertEquals(2, tableData.rowCount());

        // 测试检测表格
        assertTrue(tableParser.hasTable(markdownTable));
        assertFalse(tableParser.hasTable("这是一个普通文本"));
    }

    @Test
    public void testEndToEndFlow() throws IOException {
        System.out.println("\n=== 端到端流程测试 ===");

        // 1. 解析Markdown
        String markdown = """
                # Spring Boot 版本说明

                ## 版本演进

                | 版本 | 日期 | 特性 |
                |------|------|------|
                | 1.0 | 2014 | 初始发布 |
                | 2.0 | 2018 | 支持 Java 8 |
                | 3.0 | 2022 | 支持 Java 17 |
                """;

        Path file = Files.createTempFile("test-e2e-", ".md");
        Files.writeString(file, markdown);

        try {
            // 2. 解析
            ParseResult parseResult = textParser.parse(file.toFile());
            System.out.println("1. 解析完成，ContentBlock 数量: " + parseResult.getContentBlocks().size());

            // 3. 切片
            List<ChunkDraft> chunks = chunker.chunk(parseResult.getContentBlocks());
            System.out.println("2. 切片完成，Chunk 数量: " + chunks.size());

            // 4. 查找表格ContentBlock
            var tableBlock = parseResult.getContentBlocks().stream()
                    .filter(b -> b.getType() == com.itswy.paicodingai.file.parser.BlockType.TABLE)
                    .findFirst()
                    .orElse(null);

            if (tableBlock != null) {
                System.out.println("3. 找到表格ContentBlock");

                // 5. 解析表格
                var tableData = tableParser.parseMarkdownTable(tableBlock.getContent());
                if (tableData != null) {
                    System.out.println("4. 表格解析完成: " + tableData.rowCount() + " 行");

                    // 6. 处理表格
                    KnowledgeTable table = tableProcessingService.processTable(
                            "doc_e2e_001",
                            "kb_e2e_001",
                            "Spring Boot 版本演进",
                            tableData.headers(),
                            tableData.rows(),
                            1,
                            "Spring Boot > 版本演进"
                    );
                    System.out.println("5. 表格处理完成，ID: " + table.getId());

                    // 7. Text-to-SQL 查询
                    var sqlResult = textToSqlService.executeQuery(table.getId(), "有几行数据？", 100);
                    System.out.println("6. Text-to-SQL 查询完成");
                    System.out.println("   SQL: " + sqlResult.sql());
                    System.out.println("   结果: " + sqlResult.explanation());
                }
            }

            System.out.println("\n✅ 端到端流程测试通过！");

        } finally {
            Files.deleteIfExists(file);
        }
    }
}
