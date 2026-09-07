package com.itswy.paicodingai.tools;

import com.itswy.paicodingai.agent.SkillLoadTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表:把命名工具(逻辑名,对应 sys_tool/ai_agent 里声明的名字)映射到实际的 @Tool Bean。
 *
 * <p>主链路按「角色白名单 ∩ Agent声明」取出的名字,再通过这里还原为可注册给 ChatClient 的实例,
 * 从而把原来写死在 PaicodingAgent 里的工具列表改成 DB 驱动。</p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Object> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(ArticleTools articleTools, CourseTools courseTools, SkillLoadTool skillLoadTool,
                         KnowledgeAdminTools knowledgeAdminTools) {
        toolsByName.put("article", articleTools);
        toolsByName.put("course", courseTools);
        toolsByName.put("load_skill", skillLoadTool);
        toolsByName.put("knowledge_docs", knowledgeAdminTools);
    }

    /** 注册(供新增工具 Bean 在装配时补充,如管理员工具组)。 */
    public void register(String name, Object toolBean) {
        toolsByName.put(name, toolBean);
    }

    public Object get(String name) {
        return toolsByName.get(name);
    }

    /** 按名字列表解析为可注册实例(跳过未注册/空值)。 */
    public List<Object> resolve(Collection<String> names) {
        List<Object> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            Object bean = toolsByName.get(name);
            if (bean != null) {
                result.add(bean);
            } else {
                log.warn("工具未注册,跳过: {}", name);
            }
        }
        return result;
    }
}
