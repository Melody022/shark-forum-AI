package com.itswy.paicodingai.service.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统提示词拼装(全部动态来自 DB/Redis,不再依赖 Nacos/本地写死)。
 *
 * <p>主链路 = base(全局约束)+ 主 Agent 提示词;技能索引与工具说明由调用方追加。</p>
 */
@Slf4j
@Component
public class PromptAssembler {

    /** 主 Agent 默认 prompt key(ai_agent.paicoding.prompt_key 兜底)。 */
    public static final String DEFAULT_MAIN_AGENT_KEY = "agent.general";

    private final PromptStoreService store;

    public PromptAssembler(PromptStoreService store) {
        this.store = store;
    }

    /** 拼 base + 指定 agent 提示词;缺任何一段都自动跳过,不会插入空段落。 */
    public String assemble(String agentPromptKey) {
        String base = store.getContent("base");
        String agent = store.getContent(agentPromptKey == null || agentPromptKey.isBlank()
                ? DEFAULT_MAIN_AGENT_KEY : agentPromptKey);

        StringBuilder sb = new StringBuilder();
        if (base != null && !base.isBlank()) {
            sb.append(base.trim());
        }
        if (agent != null && !agent.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(agent.trim());
        }
        return sb.toString();
    }

    /** 主 Agent 默认(未配置 prompt_key 时兜底 agent.general)。 */
    public String assembleMain() {
        return assemble(DEFAULT_MAIN_AGENT_KEY);
    }
}
