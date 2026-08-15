package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.skill.Skill;
import com.itswy.paicodingai.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Skill加载工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillLoadTool {

    private final SkillRegistry skillRegistry;

    /**
     * 加载指定Skill的完整内容
     *
     * @param skillName Skill名称（如article-recommend、course-recommend）
     * @return Skill的完整内容
     */
    @Tool(description = "加载指定Skill的完整内容，用于获取技能指南和操作规范。" +
                       "可用的Skill: article-recommend, course-recommend, knowledge-qa, general-chat")
    public String loadSkill(
            @ToolParam(description = "Skill名称（如article-recommend、course-recommend、knowledge-qa、general-chat）")
            String skillName) {

        log.info("加载Skill: {}", skillName);

        Skill skill = skillRegistry.findSkill(skillName);

        if (skill == null) {
            String availableSkills = String.join(", ",
                skillRegistry.findAll().stream()
                    .map(Skill::getName)
                    .toList());
            return "未找到Skill: " + skillName + "。可用的Skill: " + availableSkills;
        }

        log.info("Skill加载成功: {}", skillName);
        return skill.getBody();
    }
}
