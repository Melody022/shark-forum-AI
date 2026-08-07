package com.itswy.paicodingai.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill注册中心 - 加载和管理所有skills
 *
 * 扫描skills目录，解析SKILL.md文件
 */
@Slf4j
@Component
public class SkillRegistry {

    @Value("classpath:skills/*")
    private Resource[] skillDirs;

    private final Map<String, Skill> skillsByName = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadSkills();
        log.info("Skill加载完成，共 {} 个skill", skillsByName.size());
    }

    /**
     * 加载所有skills
     */
    private void loadSkills() {
        skillsByName.clear();
        warnings.clear();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*/SKILL.md");

            for (Resource resource : resources) {
                try {
                    Path skillMdPath = resource.getFile().toPath();
                    Path skillDir = skillMdPath.getParent();
                    String skillName = skillDir.getFileName().toString();

                    String content = Files.readString(skillMdPath);
                    SkillFrontmatterParser.ParseResult parsed = SkillFrontmatterParser.parse(content);

                    // 收集警告
                    warnings.addAll(parsed.warnings());

                    // 解析frontmatter
                    Map<String, Object> fm = parsed.frontmatter();
                    String name = getStringValue(fm, "name", skillName);
                    String description = getStringValue(fm, "description", "");
                    String version = getStringValue(fm, "version", "1.0.0");
                    List<String> tags = getListValue(fm, "tags");

                    // 创建Skill对象
                    Skill skill = new Skill(
                            name,
                            description,
                            version,
                            tags,
                            parsed.body(),
                            skillMdPath
                    );

                    skillsByName.put(name, skill);
                    log.debug("加载Skill: {} - {}", name, description);

                } catch (IOException e) {
                    String errorMsg = "读取SKILL.md失败: " + resource.getFilename();
                    warnings.add(errorMsg);
                    log.warn(errorMsg, e);
                }
            }

        } catch (IOException e) {
            String errorMsg = "扫描skills目录失败";
            warnings.add(errorMsg);
            log.error(errorMsg, e);
        }
    }

    /**
     * 根据名称查找skill
     */
    public Skill findSkill(String name) {
        return skillsByName.get(name);
    }

    /**
     * 获取所有skills
     */
    public List<Skill> findAll() {
        return new ArrayList<>(skillsByName.values());
    }

    /**
     * 获取所有warnings
     */
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    /**
     * 重新加载skills
     */
    public void reload() {
        loadSkills();
    }

    private String getStringValue(Map<String, Object> fm, String key, String defaultValue) {
        Object value = fm.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getListValue(Map<String, Object> fm, String key) {
        Object value = fm.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        }
        return List.of();
    }
}
