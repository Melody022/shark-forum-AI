package com.itswy.paicodingai.file.parser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDocumentParserTest {

    private final TextDocumentParser parser = new TextDocumentParser();

    @Test
    void parsesMarkdownIntoHeadingTextListAndCodeBlocks() throws Exception {
        Path file = Files.createTempFile("rag-parser-", ".md");
        try {
            Files.writeString(file, "# Spring Boot\n\n介绍自动配置。\n\n- 条件装配\n- 起步依赖\n\n```java\n@EnableAutoConfiguration\n```\n", StandardCharsets.UTF_8);

            ParseResult result = parser.parse(file.toFile());

            assertTrue(result.isSuccess());
            assertEquals("Spring Boot", result.getTitle());
            assertEquals(List.of(BlockType.HEADING, BlockType.TEXT, BlockType.LIST, BlockType.CODE),
                    result.getContentBlocks().stream().map(ContentBlock::getType).toList());
            assertFalse(result.getContentBlocks().get(0).isSearchable());
            assertTrue(result.getContentBlocks().get(1).getSectionPath().contains("Spring Boot"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void recognizesHtmlHeadingLevel() throws Exception {
        Path file = Files.createTempFile("rag-parser-", ".html");
        try {
            Files.writeString(file, "<h1>第一章</h1><h2>概述</h2><p>内容。</p>", StandardCharsets.UTF_8);

            ParseResult result = parser.parse(file.toFile());

            assertEquals("第一章", result.getTitle());
            assertEquals("第一章 > 概述", result.getContentBlocks().get(2).getSectionPath());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
