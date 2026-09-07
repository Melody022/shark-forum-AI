package com.itswy.paicodingai.service.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 启动时把 classpath 内置种子导入 ai_prompt 表(仅当该 key 不存在,不覆盖管理端修改)。
 */
@Slf4j
@Component
@DependsOn("dbSchemaInitializer")
public class PromptSeeder {

    private final PromptStoreService promptStoreService;

    public PromptSeeder(PromptStoreService promptStoreService) {
        this.promptStoreService = promptStoreService;
    }

    @PostConstruct
    public void init() {
        int inserted = 0;
        for (PromptStoreService.SeedDef def : PromptStoreService.SEEDS) {
            if (promptStoreService.getConfig(def.key()) != null) {
                continue; // 已存在(可能是管理端改过的),跳过
            }
            String content = PromptStoreService.seedContent(def.key());
            if (content == null) {
                continue;
            }
            try {
                promptStoreService.publish(def.key(), content, "seeder");
                inserted++;
            } catch (Exception e) {
                log.warn("写入 Prompt 种子失败: key={}", def.key(), e);
            }
        }
        log.info("Prompt 种子初始化完成,新增 {} 条", inserted);
    }
}
