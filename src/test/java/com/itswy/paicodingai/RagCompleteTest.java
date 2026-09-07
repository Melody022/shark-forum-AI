package com.itswy.paicodingai;

import com.itswy.paicodingai.file.parser.ContentBlock;
import com.itswy.paicodingai.file.parser.ParseResult;
import com.itswy.paicodingai.file.parser.SimpleTableParser;
import com.itswy.paicodingai.file.parser.TextDocumentParser;
import com.itswy.paicodingai.knowledge.entity.KnowledgeTable;
import com.itswy.paicodingai.knowledge.service.TableProcessingService;
import com.itswy.paicodingai.knowledge.service.TableVectorizationService;
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
 * RAG 知识库完整功能测试
 */
@SpringBootTest
public class RagCompleteTest {

    @Autowired
    private TextDocumentParser textParser;

    @Autowired
    private HierarchicalChunker chunker;

    @Autowired
    private SimpleTableParser tableParser;

    @Autowired
    private TableProcessingService tableProcessingService;

    @Autowired
    private TableVectorizationService tableVectorizationService;

    @Autowired
    private TextToSqlService textToSqlService;

    // ============== 解析层测试 ==============

    @Test
    public void test1_MarkdownParsing() throws IOException {
        System.out.println("=== 测试1：Markdown解析 ===");

        String markdown = """
                # Spring Boot

                ## 自动配置

                Spring Boot 会根据 classpath 中的依赖自动装配 Bean。

                ```java
                @EnableAutoConfiguration
                ```

                ## 版本演进

                | 版本 | 日期 | 特性 |
                |------|------|------|
                | 1.0 | 2014 | 初始发布 |
                | 2.0 | 2018 | 支持 Java 8 |
                """;

        Path file = Files.createTempFile("test-md-", ".md");
        Files.writeString(file, markdown);

        try {
            ParseResult result = textParser.parse(file.toFile());

            System.out.println("✓ 解析成功");
            System.out.println("  标题: " + result.getTitle());
            System.out.println("  ContentBlock数量: " + result.getContentBlocks().size());

            assertTrue(result.isSuccess());
            assertEquals("Spring Boot", result.getTitle());

            // 打印每个ContentBlock
            result.getContentBlocks().forEach(b ->
                    System.out.println("  - " + b.getType() + ": " +
                            b.getContent().substring(0, Math.min(50, b.getContent().length())) + "..."));

        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ============== 切片层测试 ==============

    @Test
    public void test2_ParentChildChunking() throws IOException {
        System.out.println("\n=== 测试2：父子切片 ===");

        String markdown = """
                # Spring Boot

                ## 自动配置

                Spring Boot 会根据 classpath 中的依赖自动装配 Bean。

                ```java
                @EnableAutoConfiguration
                ```

                ## 版本信息

                Spring Boot 1.0 于2014年发布，是初始版本。
                Spring Boot 2.0 于2018年发布，支持 Java 8。
                """;

        Path file = Files.createTempFile("test-chunk-", ".md");
        Files.writeString(file, markdown);

        try {
            ParseResult result = textParser.parse(file.toFile());
            List<ChunkDraft> chunks = chunker.chunk(result.getContentBlocks());

            long parentCount = chunks.stream().filter(c -> "PARENT".equals(c.getRole())).count();
            long childCount = chunks.stream().filter(c -> "CHILD".equals(c.getRole())).count();

            System.out.println("✓ 切片成功");
            System.out.println("  父块数量: " + parentCount);
            System.out.println("  子块数量: " + childCount);
            System.out.println("  总Chunk数: " + chunks.size());

            assertTrue(parentCount > 0);
            assertTrue(childCount > 0);

        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ============== 表格解析测试 ==============

    @Test
    public void test3_TableParsing() {
        System.out.println("\n=== 测试3：表格解析 ===");

        String markdownTable = """
                | 版本 | 日期 | 特性 |
                |------|------|------|
                | 1.0 | 2014 | 初始发布 |
                | 2.0 | 2018 | 支持 Java 8 |
                """;

        var tableData = tableParser.parseMarkdownTable(markdownTable);

        System.out.println("✓ 表格解析成功");
        System.out.println("  表头: " + tableData.headers());
        System.out.println("  行数: " + tableData.rowCount());
        System.out.println("  列数: " + tableData.columnCount());

        assertEquals(3, tableData.columnCount());
        assertEquals(2, tableData.rowCount());
    }

    // ============== 表格处理测试 ==============

    @Test
    public void test4_TableProcessing() {
        System.out.println("\n=== 测试4：表格处理 ===");

        List<String> headers = List.of("版本", "日期", "特性");
        List<List<String>> rows = List.of(
                List.of("1.0", "2014", "初始发布"),
                List.of("2.0", "2018", "支持 Java 8")
        );

        // 处理表格
        KnowledgeTable table = tableProcessingService.processTable(
                "doc_test_001",
                "kb_test_001",
                "Spring Boot 版本演进",
                headers,
                rows,
                1,
                "Spring Boot > 版本信息"
        );

        System.out.println("✓ 表格处理成功");
        System.out.println("  表格ID: " + table.getId());
        System.out.println("  标题: " + table.getTitle());
        System.out.println("  行数: " + table.getRowCount());
        System.out.println("  列数: " + table.getColumnCount());
        System.out.println("  摘要: " + table.getSummary());

        assertNotNull(table.getId());
        assertEquals(2, table.getRowCount());
        assertEquals(3, table.getColumnCount());

        // 生成行组Chunks
        List<String> rowGroupChunks = tableProcessingService.generateRowGroupChunks(
                "Spring Boot 版本演进", headers, rows, 10);

        System.out.println("\n  行组Chunks数量: " + rowGroupChunks.size());
        assertFalse(rowGroupChunks.isEmpty());

        // 显示第一个Chunk内容
        String firstChunk = rowGroupChunks.get(0);
        System.out.println("  第一个Chunk:\n" + firstChunk);
    }

    // ============== 向量化测试 ==============

    @Test
    public void test5_Vectorization() {
        System.out.println("\n=== 测试5：向量化 ===");

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

        // 向量化表格
        boolean success = tableVectorizationService.vectorizeTable(table.getId());

        System.out.println("✓ 向量化完成: " + (success ? "成功" : "失败"));

        // 清理向量
        if (success) {
            tableVectorizationService.deleteTableVectors(table.getId());
            System.out.println("  向量已清理");
        }
    }

    // ============== Text-to-SQL 测试 ==============

    @Test
    public void test6_TextToSql() {
        System.out.println("\n=== 测试6：Text-to-SQL ===");

        List<String> headers = List.of("版本", "日期", "特性");
        List<List<String>> rows = List.of(
                List.of("1.0", "2014", "初始发布"),
                List.of("2.0", "2018", "支持 Java 8"),
                List.of("3.0", "2022", "支持 Java 17")
        );

        KnowledgeTable table = tableProcessingService.processTable(
                "doc_test_003",
                "kb_test_003",
                "Spring Boot 版本演进",
                headers,
                rows,
                1,
                "Spring Boot > 版本信息"
        );

        // 执行查询
        var result = textToSqlService.executeQuery(table.getId(), "有几行数据？", 100);

        System.out.println("✓ Text-to-SQL 执行成功");
        System.out.println("  SQL: " + result.sql());
        System.out.println("  说明: " + result.explanation());
        System.out.println("  列: " + (result.columns().length > 0 ? String.join(", ", result.columns()) : "无"));
        System.out.println("  结果数: " + result.total());

        assertNotNull(result.sql());
    }

    // ============== 溯源功能测试 ==============

    @Test
    public void test7_SourceTracing() {
        System.out.println("\n=== 测试7：溯源功能 ===");

        List<String> headers = List.of("版本", "日期", "特性");
        List<List<String>> rows = List.of(
                List.of("1.0", "2014", "初始发布"),
                List.of("2.0", "2018", "支持 Java 8")
        );

        KnowledgeTable table = tableProcessingService.processTable(
                "doc_test_004",
                "kb_test_004",
                "Spring Boot 版本演进",
                headers,
                rows,
                1,
                "Spring Boot > 版本信息"
        );

        System.out.println("✓ 溯源信息");
        System.out.println("  表格ID: " + table.getId());
        System.out.println("  文档ID: " + table.getDocumentId());
        System.out.println("  知识库ID: " + table.getKnowledgeBaseId());
        System.out.println("  页码: " + table.getPageStart() + "-" + table.getPageEnd());
        System.out.println("  章节路径: " + table.getSectionPath());
        System.out.println("  标题: " + table.getTitle());

        assertNotNull(table.getDocumentId());
        assertNotNull(table.getKnowledgeBaseId());
        assertNotNull(table.getSectionPath());
    }

    // ============== 端到端流程测试 ==============

    @Test
    public void test8_EndToEndFlow() throws IOException {
        System.out.println("\n=== 测试8：端到端流程 ===");

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
            // Step1: 解析
            ParseResult parseResult = textParser.parse(file.toFile());
            System.out.println("Step1 解析完成: " + parseResult.getContentBlocks().size() + " 个ContentBlock");

            // Step2: 切片
            List<ChunkDraft> chunks = chunker.chunk(parseResult.getContentBlocks());
            System.out.println("Step2 切片完成: " + chunks.size() + " 个Chunk");

            // Step3: 查找表格
            var tableBlock = parseResult.getContentBlocks().stream()
                    .filter(b -> b.getType() == com.itswy.paicodingai.file.parser.BlockType.TABLE)
                    .findFirst()
                    .orElse(null);

            if (tableBlock != null) {
                System.out.println("Step3 找到表格ContentBlock");

                // Step4: 解析表格
                var tableData = tableParser.parseMarkdownTable(tableBlock.getContent());
                if (tableData != null) {
                    System.out.println("Step4 表格解析完成: " + tableData.rowCount() + " 行");

                    // Step5: 处理表格
                    KnowledgeTable table = tableProcessingService.processTable(
                            "doc_e2e_001",
                            "kb_e2e_001",
                            "Spring Boot 版本演进",
                            tableData.headers(),
                            tableData.rows(),
                            1,
                            "Spring Boot > 版本演进"
                    );
                    System.out.println("Step5 表格处理完成，ID: " + table.getId());

                    // Step6: 向量化
                    boolean vectorSuccess = tableVectorizationService.vectorizeTable(table.getId());
                    System.out.println("Step6 向量化完成: " + (vectorSuccess ? "成功" : "失败"));

                    // Step7: Text-to-SQL
                    var sqlResult = textToSqlService.executeQuery(table.getId(), "有几行数据？", 100);
                    System.out.println("Step7 Text-to-SQL完成");
                    System.out.println("  SQL: " + sqlResult.sql());
                    System.out.println("  说明: " + sqlResult.explanation());

                    // 清理
                    if (vectorSuccess) {
                        tableVectorizationService.deleteTableVectors(table.getId());
                    }
                }
            }

            System.out.println("\n✅ 端到端流程测试通过！");

        } finally {
            Files.deleteIfExists(file);
        }
    }
}
