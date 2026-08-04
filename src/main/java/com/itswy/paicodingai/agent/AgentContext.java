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
}
