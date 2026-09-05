package com.itswy.paicodingai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent上下文
 *
 * 封装会话信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {
    private String sessionId;
    private String requestId;
    /** 用于知识库检索的用户隔离标识。 */
    private String userId;
}
