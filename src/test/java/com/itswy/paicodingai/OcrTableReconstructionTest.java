package com.itswy.paicodingai;

import com.itswy.paicodingai.knowledge.entity.KnowledgeTable;
import com.itswy.paicodingai.knowledge.service.TableProcessingService;
import com.itswy.paicodingai.knowledge.service.impl.OcrTableReconstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OCR 表格重建测试
 */
@SpringBootTest
public class OcrTableReconstructionTest {

    @Autowired
    private OcrTableReconstructor reconstructor;

    @Autowired
    private TableProcessingService tableProcessingService;

    @Test
    public void test1_SimpleTableReconstruction() {
        System.out.println("=== 测试1：简单表格重建 ===");

        String ocrContent = """
                版本 日期 特性
                1.0 2014 初始发布
                2.0 2018 支持 Java 8
                3.0 2022 支持 Java 17
                """;

        var table = reconstructor.reconstruct(ocrContent);

        System.out.println("✓ 表格重建成功");
        System.out.println("  表头: " + table.headers());
        System.out.println("  行数: " + table.rowCount());
        System.out.println("  列数: " + table.columnCount());
        System.out.println("\n  表格内容:\n" + table.toMarkdown());

        assertEquals(3, table.columnCount());
        assertEquals(3, table.rowCount());
    }

    @Test
    public void test2_FinancialTableReconstruction() {
        System.out.println("\n=== 测试2：财务表格重建 ===");

        String ocrContent = """
                2025年 2024年 2023年 本年比上年增减(%)
                总资产 247，353，213，805.61 233，421，377，309.40 5.97 219，115，319，747.00
                归属于上市公司股
                东的净资产 67，190，861，995.78 62，275，575，565.47 7.89 57，495，312，836.17
                营业收入 189，500，829，774.75 185，843，681，886.95 1.97 178，357，510，396.41
                利润总额 8，434，871，808.74 7，359，315，343.06 14.61 6，905，485，918.49
                归属于上市公司股
                东的净利润 6，436，145，919.35 5，687，933，905.73 13.15 5，426，141，014.95
                归属于上市公司股
                东的扣除非经常性
                损益的净利润 6，090，476，046.42 5，514，772，863.23 10.44 5，201，581，000.12
                经营活动产生的现
                金流量净额 1，443，378，231.39 8，722，135，369.15 -83.45 9，134，015，889.74
                加权平均净资产收
                益率(%) 9.93 9.50 9.83 增加0.43个百分点
                """;

        var table = reconstructor.reconstruct(ocrContent);

        System.out.println("✓ 财务表格重建成功");
        System.out.println("  表头: " + table.headers());
        System.out.println("  行数: " + table.rowCount());
        System.out.println("  列数: " + table.columnCount());

        // 打印前3行
        System.out.println("\n  前3行数据:");
        for (int i = 0; i < Math.min(3, table.rows().size()); i++) {
            System.out.println("  行" + i + ": " + table.rows().get(i));
        }

        assertTrue(table.rowCount() > 0);
        assertTrue(table.columnCount() > 0);
    }

    @Test
    public void test3_FullTableProcessing() {
        System.out.println("\n=== 测试3：完整表格处理流程 ===");

        // 模拟OCR返回的内容
        String ocrContent = """
                版本 日期 特性
                1.0 2014 初始发布
                2.0 2018 支持 Java 8
                3.0 2022 支持 Java 17
                """;

        // Step1: OCR表格重建
        var reconstructed = reconstructor.reconstruct(ocrContent);
        System.out.println("Step1 表格重建完成");
        System.out.println("  表头: " + reconstructed.headers());
        System.out.println("  行数: " + reconstructed.rowCount());

        // Step2: 处理表格
        KnowledgeTable table = tableProcessingService.processTable(
                "doc_test_001",
                "kb_test_001",
                "Spring Boot 版本演进",
                reconstructed.headers(),
                reconstructed.rows(),
                1,
                "Spring Boot > 版本信息"
        );

        System.out.println("Step2 表格处理完成");
        System.out.println("  表格ID: " + table.getId());
        System.out.println("  标题: " + table.getTitle());
        System.out.println("  行数: " + table.getRowCount());
        System.out.println("  列数: " + table.getColumnCount());

        // Step3: 生成表格摘要
        String summary = tableProcessingService.generateTableSummary(
                table.getTitle(), reconstructed.headers(), reconstructed.rowCount()
        );
        System.out.println("Step3 表格摘要生成完成");
        System.out.println("  摘要: " + summary);

        // Step4: 生成行组Chunks
        List<String> rowGroupChunks = tableProcessingService.generateRowGroupChunks(
                table.getTitle(), reconstructed.headers(), reconstructed.rows(), 10
        );
        System.out.println("Step4 行组Chunks生成完成");
        System.out.println("  行组数量: " + rowGroupChunks.size());
        System.out.println("  第一个行组:\n" + rowGroupChunks.get(0));

        assertNotNull(table.getId());
        assertNotNull(summary);
        assertFalse(rowGroupChunks.isEmpty());
    }

    @Test
    public void test4_MultiLineHeaderReconstruction() {
        System.out.println("\n=== 测试4：多行表头重建 ===");

        String ocrContent = """
                归属于上市公司股
                东的净资产
                2025年 2024年 2023年
                67，190，861，995.78 62，275，575，565.47 57，495，312，836.17
                """;

        var table = reconstructor.reconstruct(ocrContent);

        System.out.println("✓ 多行表头重建成功");
        System.out.println("  表头: " + table.headers());
        System.out.println("  行数: " + table.rowCount());
        System.out.println("  列数: " + table.columnCount());

        System.out.println("\n  表格内容:\n" + table.toMarkdown());

        assertTrue(table.columnCount() > 0);
    }

    @Test
    public void test5_NumberCleaning() {
        System.out.println("\n=== 测试5：数字清理 ===");

        String ocrContent = """
                项目 金额
                总资产 247，353，213，805.61
                净资产 67，190，861，995.78
                """;

        var table = reconstructor.reconstruct(ocrContent);

        System.out.println("✓ 数字清理测试");
        System.out.println("  表头: " + table.headers());
        System.out.println("  数据:");

        for (List<String> row : table.rows()) {
            System.out.println("  " + row);
        }

        // 检查数字是否已清理逗号
        assertTrue(table.rows().stream()
                .flatMap(List::stream)
                .anyMatch(cell -> cell.contains("247353213805.61")));
    }
}
