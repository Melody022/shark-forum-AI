# 第一期：从零搭建 → 流式对话跑通

> 对应视频：P5 需求分析 ~ P15 流式对话代码实现(3)
> 
> 目标：实现一个能跑的流式 AI 对话

---

## 一、本期效果

```
用户打开聊天窗口 → 看到"HELLO, 我是AI助手"
                    ↓
        显示示例问题（"能帮我推荐一个教程吗？"等）
                    ↓
        用户输入 → AI逐字回复（SSE打字机效果）
```

---

## 二、数据流

```
浏览器 → POST /ai/session → 返回 sessionId + 示例问题
浏览器 → POST /ai/chat     → SSE 流式返回 AI 逐字回复
         {question, sessionId}
                              ↓
                     Spring AI ChatClient → DeepSeek API
                              ↓
                    Flux<ChatEventVO> → SSE → 浏览器打字机效果
```

SSE 事件类型：

| 事件 | 值 | 含义 |
|------|-----|------|
| DATA | 1001 | AI正在回复，追加到气泡 |
| STOP | 1002 | AI说完了，关闭loading |
| PARAM | 1003 | 工具调用结果（后续阶段用）|

---

## 三、项目结构（第一期）

```
enums/                    ← 类型定义
├── AgentTypeEnum.java       智能体类型（本期只用ROUTE）
├── ChatEventTypeEnum.java   SSE事件类型
└── MessageTypeEnum.java     消息角色 USER/ASSISTANT

constants/
└── Constant.java         常量

entity/                   ← 数据库表映射
├── ChatSession.java        会话表
└── ChatRecord.java         聊天记录表

mapper/                   ← 数据库操作
├── ChatSessionMapper.java
└── ChatRecordMapper.java

dto/
└── ChatDTO.java          请求体 {question, sessionId}

vo/                       ← 返回给前端的数据
├── SessionVO.java          创建会话响应
├── ChatEventVO.java        ★ SSE流式返回的核心
├── MessageVO.java          聊天记录中的一条消息
└── ChatSessionVO.java      历史会话列表项

config/                   ← 配置
├── SessionProperties.java   读取yml配置
├── SystemPromptConfig.java  系统提示词
└── SpringAIConfig.java     ★ 核心：ChatClient + ChatMemory + Advisor

memory/
└── JdbcChatMemoryRepository.java ★ 对话历史存MySQL

service/                  ← 业务逻辑
├── ChatService.java         聊天接口
├── ChatSessionService.java  会话管理接口
└── impl/
    ├── ChatServiceImpl.java       ★★★ 核心：流式对话
    └── ChatSessionServiceImpl.java  会话管理

controller/               ← API入口
├── ChatController.java      ★ SSE流式接口
└── SessionController.java   会话管理接口

sql/init.sql              建表脚本
```

---

## 四、核心概念

**1. SSE（Server-Sent Events）**
- 传统 HTTP：等全部结果 → 一次性返回
- SSE：一边生成一边推 → 打字机效果
- Spring 实现：`Flux<ChatEventVO>` + `produces = TEXT_EVENT_STREAM_VALUE`

**2. Spring AI ChatClient**
- 跟大模型对话的核心客户端
- 用法：`chatClient.prompt().system().user().stream().chatResponse()`

**3. Advisor（功能增强）**
- 类似 Filter 拦截器
- SimpleLoggerAdvisor：打印日志
- MessageChatMemoryAdvisor：自动拼历史消息

**4. ChatMemory + ChatMemoryRepository**
- AI 不记上下文 → 需要每次带历史消息
- ChatMemory 高层接口 → ChatMemoryRepository 持久化到 MySQL

**5. 停止生成**
- Redis 存标记 `GENERATE_STATUS:{sessionId} = true`
- `takeWhile` 检查标记 → 用户点停止 → 删除标记 → 停止流式输出

---

## 五、API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/session?n=3` | 创建会话 |
| POST | `/ai/chat` | 流式对话（SSE）|
| POST | `/ai/stop?sessionId=xxx` | 停止生成 |
| POST | `/ai/text` | 非流式对话 |
| GET | `/ai/session/{sessionId}` | 查看聊天记录 |
| GET | `/ai/history` | 历史会话列表 |
| DELETE | `/ai/history?sessionId=xxx` | 删除会话 |
| PUT | `/ai/history?sessionId=xxx&title=xxx` | 修改标题 |

---

## 六、遇到的坑

**坑1：Spring AI 2.0 不兼容 1.x API**

你的 pom 配的是 Spring AI 2.0，但天机学堂代码基于 1.x。

| 方法 | 1.x | 2.0 |
|------|-----|-----|
| 添加消息 | `add()` | `saveAll()` |
| 查询消息 | `get()` | `findByConversationId()` |
| 清除消息 | `clear()` | `deleteByConversationId()` |
| 序列化 | `ModelOptionsUtils` | ❌ 已移除，改 Jackson |

解决：`JdbcChatMemoryRepository` 按 2.0 API 重写。

**坑2：Java 版本不匹配**

pom.xml 配了 JDK 21，本机是 JDK 17 → 改为 17。

**坑3：缺少 jackson-datatype-jsr310**

ObjectMapper 序列化 LocalDateTime 需要此依赖。
