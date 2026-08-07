package com.itswy.paicodingai.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SKILL.md frontmatter解析器
 *
 * 解析格式：
 * ---
 * name: skill-name
 * description: 描述
 * version: 1.0.0
 * tags: [tag1, tag2]
 * ---
 * body内容
 */
public class SkillFrontmatterParser {

    public static ParseResult parse(String content) {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        String body = "";
        List<String> warnings = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return new ParseResult(frontmatter, "", warnings);
        }

        // 检查是否有frontmatter
        if (!content.startsWith("---")) {
            warnings.add("缺少frontmatter分隔符");
            return new ParseResult(frontmatter, content, warnings);
        }

        // 查找第二个---
        int secondDelimiter = content.indexOf("---", 3);
        if (secondDelimiter == -1) {
            warnings.add("未找到frontmatter结束符");
            return new ParseResult(frontmatter, content, warnings);
        }

        // 解析frontmatter
        String frontmatterStr = content.substring(3, secondDelimiter).trim();
        body = content.substring(secondDelimiter + 3).trim();

        // 简单解析YAML格式的frontmatter
        String[] lines = frontmatterStr.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                continue;
            }

            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            // 处理列表格式 [tag1, tag2]
            if (value.startsWith("[") && value.endsWith("]")) {
                String listStr = value.substring(1, value.length() - 1);
                List<String> list = new ArrayList<>();
                for (String item : listStr.split(",")) {
                    list.add(item.trim());
                }
                frontmatter.put(key, list);
            } else {
                frontmatter.put(key, value);
            }
        }

        return new ParseResult(frontmatter, body, warnings);
    }

    public record ParseResult(Map<String, Object> frontmatter, String body, List<String> warnings) {}
}
