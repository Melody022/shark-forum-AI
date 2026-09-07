package com.itswy.paicodingai.skill;

import com.itswy.paicodingai.service.prompt.PromptStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 注册中心(DB 驱动)。
 *
 * <p>Skill 全文(含 frontmatter)改存 ai_prompt(prompt_type=SKILL,key=skill.{name}),
 * 启动/重载时从 {@link PromptStoreService} 读取(Redis→DB→classpath 种子兜底)。
 * 管理端「发布 SKILL」后调用 {@link #reload()} 即热生效。</p>
 */
@Slf4j
@Component
public class SkillRegistry {

    private final PromptStoreService promptStoreService;

    private final Map<String, Skill> skillsByName = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public SkillRegistry(PromptStoreService promptStoreService) {
        this.promptStoreService = promptStoreService;
    }

    @PostConstruct
    public void init() {
        loadSkills();
        log.info("Skill加载完成(DB),共 {} 个skill", skillsByName.size());
    }

    private void loadSkills() {
        skillsByName.clear();
        warnings.clear();

        for (PromptStoreService.SeedDef def : PromptStoreService.SEEDS) {
            if (!PromptStoreService.TYPE_SKILL.equals(def.type())) {
                continue;
            }
            String content = promptStoreService.getContent(def.key());
            if (content == null || content.isBlank()) {
                warnings.add("Skill 内容为空: " + def.key());
                continue;
            }
            try {
                SkillFrontmatterParser.ParseResult parsed = SkillFrontmatterParser.parse(content);
                warnings.addAll(parsed.warnings());

                Map<String, Object> fm = parsed.frontmatter();
                String fallbackName = def.key().startsWith("skill.") ? def.key().substring("skill.".length()) : def.key();
                String name = valueAsString(fm, "name", fallbackName);
                String description = valueAsString(fm, "description", def.name());
                String version = valueAsString(fm, "version", "1.0.0");
                List<String> tags = fm.get("tags") instanceof List<?> list
                        ? list.stream().filter(it -> it instanceof String).map(String.class::cast).toList()
                        : List.of();

                Skill skill = new Skill(name, description, version, tags, parsed.body(), null);
                skillsByName.put(name, skill);
                log.debug("加载Skill(DB): {} - {}", name, description);
            } catch (Exception e) {
                warnings.add("解析 Skill 失败: " + def.key() + " - " + e.getMessage());
                log.warn("解析 Skill 失败: {}", def.key(), e);
            }
        }
    }

    public Skill findSkill(String name) {
        return skillsByName.get(name);
    }

    public List<Skill> findAll() {
        return new ArrayList<>(skillsByName.values());
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    /** 管理端发布/更新 SKILL 后调用,重新从 DB 加载。 */
    public void reload() {
        loadSkills();
        log.info("Skill 已重载,共 {} 个", skillsByName.size());
    }

    private String valueAsString(Map<String, Object> fm, String key, String def) {
        Object v = fm.get(key);
        return v instanceof String s ? s : def;
    }
}
