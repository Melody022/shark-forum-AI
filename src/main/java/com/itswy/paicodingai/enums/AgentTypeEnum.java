package com.itswy.paicodingai.enums;

/**
 * ==========================================================================
 * 智能体类型枚举
 * ==========================================================================
 *
 * 什么是智能体（Agent）？
 *   智能体就是一个"AI角色"，每个角色有自己的专长。
 *   好比一个客服团队里有不同分工的人：
 *     - 前台接待（ROUTE）：先问用户"你要办什么业务？"
 *     - 售前咨询（ARTICLE）：推荐合适的文章/教程
 *     - 技术支持（KNOWLEDGE）：解答技术问题
 *     - 普通客服（GENERAL）：闲聊、帮助
 *
 * 第一阶段虽然只用到 ROUTE（单智能体模式），
 * 但先把框架搭好，后面加多智能体时不用改代码。
 *
 * agentName 字段特别重要！！！
 *   后面路由智能体（RouteAgent）的工作方式：
 *   大模型分析用户意图后，输出一个字符串，比如 "ARTICLE"，
 *   我们通过 agentNameOf("ARTICLE") 找到对应的智能体来处理。
 *   所以 agentName 必须和 大模型输出的值 一致！
 *
 * @date 2026-07-18
 */
public enum AgentTypeEnum {

    /**
     * 路由智能体：分析用户意图，分发给其他智能体
     * 第一阶段：作为普通对话智能体
     * 后面阶段：作为路由器，判断用户想干什么
     */
    ROUTE("ROUTE", "路由智能体"),

    /**
     * 文章推荐智能体：根据用户需求推荐合适的文章/教程
     * 第三阶段才会用到
     */
    ARTICLE("ARTICLE", "文章推荐智能体"),

    /**
     * 知识讲解智能体：回答具体的技术问题
     * 第三阶段才会用到
     */
    KNOWLEDGE("KNOWLEDGE", "知识讲解智能体"),

    /**
     * 通用对话智能体：闲聊、帮助、写作辅助等
     * 第三阶段才会用到
     */
    GENERAL("GENERAL", "通用对话智能体");

    /**
     * 智能体的英文名称 —— 大模型输出的就是这个值
     * 比如 RouteAgent 问大模型"用户想干什么"，
     * 大模型回答 "ARTICLE"，我们就找 agentName="ARTICLE" 的智能体
     */
    private final String agentName;

    /**
     * 智能体的中文描述 —— 方便我们开发者看
     */
    private final String desc;

    AgentTypeEnum(String agentName, String desc) {
        this.agentName = agentName;
        this.desc = desc;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return this.name();
    }

    /**
     * 根据智能体名称查找对应的枚举
     * 比如 RouteAgent 返回 "ARTICLE"，调用这个方法找到 ARTICLE 枚举
     *
     * @param agentName 智能体名称（大模型输出的值）
     * @return 对应的枚举，没找到返回 null
     */
    public static AgentTypeEnum agentNameOf(String agentName) {
        for (AgentTypeEnum type : values()) {
            if (type.getAgentName().equals(agentName)) {
                return type;
            }
        }
        return null;
    }
}
