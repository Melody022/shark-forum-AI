package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.skill.Skill;
import com.itswy.paicodingai.vo.ChatEventVO;
import reactor.core.publisher.Flux;

/**
 * Agent接口
 *
 * 定义统一的Agent规范
 */
public interface Agent {

    /**
     * 获取Agent名称
     */
    String getName();

    /**
     * 获取Agent描述
     */
    String getDescription();

    /**
     * 处理用户问题
     *
     * @param question 用户问题
     * @param ctx Agent上下文
     * @return 流式响应
     */
    Flux<ChatEventVO> chat(String question, AgentContext ctx);

    /**
     * 处理用户问题（带Skill）
     *
     * @param question 用户问题
     * @param ctx Agent上下文
     * @param skill 技能上下文（可选）
     * @return 流式响应
     */
    default Flux<ChatEventVO> chat(String question, AgentContext ctx, Skill skill) {
        return chat(question, ctx);
    }
}
