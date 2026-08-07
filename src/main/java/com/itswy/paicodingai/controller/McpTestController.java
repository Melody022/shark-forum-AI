package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.mcp.tools.SimpleSearchTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP工具测试接口
 */
@RestController
@RequestMapping("/api/mcp/test")
@RequiredArgsConstructor
public class McpTestController {

    private final SimpleSearchTool simpleSearchTool;

    /**
     * 测试搜索工具
     * GET /api/mcp/test/search?query=Spring Boot
     */
    @GetMapping("/search")
    public String testSearch(@RequestParam String query) {
        return simpleSearchTool.search(query);
    }

    /**
     * 测试获取时间
     * GET /api/mcp/test/time
     */
    @GetMapping("/time")
    public String testTime() {
        return simpleSearchTool.getCurrentTime();
    }

    /**
     * 测试计算工具
     * GET /api/mcp/test/calculate?expression=2+3*4
     */
    @GetMapping("/calculate")
    public String testCalculate(@RequestParam String expression) {
        return simpleSearchTool.calculate(expression);
    }
}
