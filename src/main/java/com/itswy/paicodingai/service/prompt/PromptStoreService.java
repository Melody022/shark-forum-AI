package com.itswy.paicodingai.service.prompt;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.itswy.paicodingai.entity.PromptConfig;
import com.itswy.paicodingai.mapper.PromptConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prompt 存储服务:ai_prompt 表 + Redis 版本缓存。
 *
 * <p>读取优先级:Redis → DB → classpath 种子(兜底,保证离线可启)。
 * publish 使 DB 版本 +1 并清 Redis,下次读取即热生效。</p>
 */
@Slf4j
@Service
public class PromptStoreService {

    public static final String TYPE_BASE = "BASE";
    public static final String TYPE_AGENT = "AGENT";
    public static final String TYPE_SKILL = "SKILL";
    public static final String TYPE_CLASSIFIER = "CLASSIFIER";

    public record SeedDef(String key, String type, String name, String resource) {
    }

    /** 内置种子清单:key -> classpath 资源(prompt 正文不写进 SQL,避免手抄大段文案)。 */
    public static final List<SeedDef> SEEDS = List.of(
            new SeedDef("base", TYPE_BASE, "全局基础提示词", "prompts/base.md"),
            new SeedDef("agent.general", TYPE_AGENT, "通用对话主Agent", "prompts/agents/general.md"),
            new SeedDef("agent.article", TYPE_AGENT, "文章问答Agent", "prompts/agents/article.md"),
            new SeedDef("agent.course", TYPE_AGENT, "教程问答Agent", "prompts/agents/course.md"),
            new SeedDef("agent.knowledge", TYPE_AGENT, "知识库问答Agent", "prompts/agents/knowledge.md"),
            new SeedDef("skill.article-recommend", TYPE_SKILL, "文章推荐技能", "skills/article-recommend/SKILL.md"),
            new SeedDef("skill.course-recommend", TYPE_SKILL, "教程推荐技能", "skills/course-recommend/SKILL.md"),
            new SeedDef("skill.knowledge-qa", TYPE_SKILL, "知识问答技能", "skills/knowledge-qa/SKILL.md"),
            new SeedDef("skill.general-chat", TYPE_SKILL, "通用对话技能", "skills/general-chat/SKILL.md")
    );

    private static final String CACHE_PREFIX = "ai:prompt:";
    private static final long CACHE_TTL_DAYS = 7;

    private final PromptConfigMapper mapper;
    private final StringRedisTemplate redisTemplate;

    public PromptStoreService(PromptConfigMapper mapper, StringRedisTemplate redisTemplate) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
    }

    /** 读取指定 key 的当前内容(Redis → DB → 种子兜底;Redis/DB 不可用均自动降级)。 */
    public String getContent(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String cacheKey = CACHE_PREFIX + key;
        String cached = redisGet(cacheKey);
        if (cached != null) {
            return cached;
        }
        String content = null;
        PromptConfig row = getConfig(key);
        if (row != null && row.getContent() != null) {
            content = row.getContent();
        } else {
            content = seedContent(key);
        }
        if (content != null) {
            redisSet(cacheKey, content);
        }
        return content;
    }

    /** Redis 尽力而为:连不上(本地未启动/故障)只记一条日志,不阻塞启动或读取。 */
    private String redisGet(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 读取失败(降级为 DB/种子): key={} - {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void redisSet(String cacheKey, String content) {
        try {
            redisTemplate.opsForValue().set(cacheKey, content, CACHE_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 写入失败(降级,跳过缓存): key={} - {}", cacheKey, e.getMessage());
        }
    }

    private void redisDelete(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 删除失败(降级,DB 已更新): key={} - {}", cacheKey, e.getMessage());
        }
    }

    public PromptConfig getConfig(String key) {
        try {
            return mapper.selectOne(Wrappers.<PromptConfig>lambdaQuery()
                    .eq(PromptConfig::getPromptKey, key)
                    .eq(PromptConfig::getEnabled, 1)
                    .orderByDesc(PromptConfig::getVersion)
                    .last("limit 1"));
        } catch (Exception e) {
            log.warn("读取 Prompt 失败(表未就绪?): key={} - {}", key, e.getMessage());
            return null;
        }
    }

    public List<PromptConfig> listAll() {
        try {
            return mapper.selectList(Wrappers.<PromptConfig>lambdaQuery().orderByAsc(PromptConfig::getPromptKey));
        } catch (Exception e) {
            log.warn("列出 Prompt 失败(表未就绪?): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 发布新内容:DB version+1(或首次插入 version=1),清 Redis 缓存。
     * @return 新版本号
     */
    public int publish(String key, String content, String updatedBy) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("prompt key 不能为空");
        }
        PromptConfig row = getConfig(key);
        int version;
        if (row == null) {
            row = PromptConfig.builder()
                    .promptKey(key)
                    .promptType(guessType(key))
                    .name(key)
                    .version(1)
                    .content(content)
                    .enabled(1)
                    .updatedBy(updatedBy != null ? updatedBy : "system")
                    .updatedAt(LocalDateTime.now())
                    .build();
            mapper.insert(row);
            version = 1;
        } else {
            row.setContent(content);
            row.setVersion(row.getVersion() == null ? 1 : row.getVersion() + 1);
            row.setUpdatedBy(updatedBy != null ? updatedBy : "system");
            row.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(row);
            version = row.getVersion();
        }
        redisDelete(CACHE_PREFIX + key);
        log.info("Prompt 已发布: key={}, version={}", key, version);
        return version;
    }

    /** 取某 key 的内置种子内容;无则 null。 */
    public static String seedContent(String key) {
        for (SeedDef def : SEEDS) {
            if (def.key().equals(key)) {
                if (def.resource() == null) {
                    return null;
                }
                try {
                    return new String(new ClassPathResource(def.resource()).getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.warn("读取内置种子失败: {}", def.resource(), e);
                    return null;
                }
            }
        }
        return null;
    }

    public static String guessType(String key) {
        if (key.equals("base")) {
            return TYPE_BASE;
        }
        if (key.startsWith("skill.")) {
            return TYPE_SKILL;
        }
        if (key.startsWith("classifier.")) {
            return TYPE_CLASSIFIER;
        }
        return TYPE_AGENT;
    }
}
