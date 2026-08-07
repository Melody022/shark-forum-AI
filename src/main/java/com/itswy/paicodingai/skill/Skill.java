package com.itswy.paicodingai.skill;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill实体类 - 表示一个技能单元
 *
 * 由SKILL.md文件解析得到：
 * - frontmatter决定元数据（name, description, tags等）
 * - body在LLM调用时作为上下文注入
 */
public class Skill {
    private final String name;
    private final String description;
    private final String version;
    private final List<String> tags;
    private final String body;
    private final Path skillMdPath;

    public Skill(String name, String description, String version,
                 List<String> tags, String body, Path skillMdPath) {
        this.name = name;
        this.description = description != null ? description : "";
        this.version = version;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.body = body != null ? body : "";
        this.skillMdPath = skillMdPath;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getBody() {
        return body;
    }

    public Path getSkillMdPath() {
        return skillMdPath;
    }

    @Override
    public String toString() {
        return "Skill{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
