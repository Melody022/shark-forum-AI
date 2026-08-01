# Token预算管理 vs 固定消息数管理

## 一、问题分析

### 原来的实现（有bug）

```java
@Bean
public Advisor memoryAdvisor(ChatMemoryRepository chatMemoryRepository) {
    ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(MEMORY_WINDOW_SIZE)  // 保留最近 20 条消息
            .build();
    return new MessageChatMemoryAdvisor(chatMemory, ...);
}
```

**问题：**
1. ❌ 固定20条消息，不考虑Token数量
2. ❌ 20条消息可能占用5000 tokens，也可能占用50000 tokens
3. ❌ 无法适配不同模型的上下文窗口
4. ❌ 可能导致Token超出限制，LLM调用失败

---

## 二、正确的方案：基于Token预算

### 为什么用Token而不是消息数？

| 场景 | 固定消息数（20条） | Token预算（50000） |
|------|-------------------|-------------------|
| 短消息（10 tokens/条） | ✅ 200 tokens | ✅ 5000条消息 |
| 中等消息（100 tokens/条） | ✅ 2000 tokens | ✅ 500条消息 |
| 长消息（500 tokens/条） | ⚠️ 10000 tokens | ✅ 100条消息 |
| 混合消息 | ❌ 不可控 | ✅ **可控** |

### Token预算的优势

1. **精确控制**
   - 准确知道占用多少Token
   - 不会超出模型限制
   - 避免LLM调用失败

2. **自适应**
   - 短消息可以保留更多
   - 长消息可以保留较少
   - 动态调整，最优利用

3. **支持不同模型**
   ```yaml
   # DeepSeek 1M
   paicoding.ai.memory.token-budget.context-window: 1048576
   
   # GPT-4 128k
   paicoding.ai.memory.token-budget.context-window: 128000
   
   # Claude 200k
   paicoding.ai.memory.token-budget.context-window: 200000
   ```

---

## 三、TokenAwareChatMemory 设计

### 核心设计

```java
public class TokenAwareChatMemory implements ChatMemory {
    
    private final RedisConversationMemory shortTermMemory;
    private final TokenBudget tokenBudget;
    
    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            MemoryEntry entry = convertToMemoryEntry(message);
            
            // 存入短期记忆（会自动检查Token预算并淘汰）
            shortTermMemory.store(entry);
        }
        
        // 检查是否需要压缩
        checkAndCompress();
    }
    
    @Override
    public List<Message> get(String conversationId) {
        // 获取所有MemoryEntry
        List<MemoryEntry> allEntries = shortTermMemory.getAll();
        
        // 转换为Message列表
        return allEntries.stream()
                .map(this::convertToMessage)
                .filter(Objects::nonNull)
                .toList();
    }
    
    private void checkAndCompress() {
        // 检查Token是否超过预算的90%
        if (!tokenBudget.needsCompression(shortTermMemory)) {
            return;
        }
        
        // 执行Map-Reduce压缩
        log.info("Token占用超过阈值，触发压缩");
        // ...
    }
}
```

### 工作流程

```
用户输入
    ↓
TokenAwareChatMemory.add()
    ├── 1. Message → MemoryEntry
    │   └── 计算Token数
    ├── 2. RedisConversationMemory.store()
    │   ├── 存入Redis List
    │   ├── 更新Token计数
    │   └── 如果超出预算，淘汰最旧消息
    └── 3. checkAndCompress()
        ├── 如果Token占用 >= 90%
        └── 调用ContextCompressor压缩

获取上下文
    ↓
TokenAwareChatMemory.get()
    ├── 1. RedisConversationMemory.getAll()
    │   └── 获取所有MemoryEntry
    ├── 2. MemoryEntry → Message
    └── 3. 返回Message列表
```

---

## 四、配置参数

### Token预算配置

```yaml
paicoding:
  ai:
    memory:
      token-budget:
        # 模型上下文窗口
        context-window: 1048576           # DeepSeek 1M
        
        # 预留空间
        reserved-for-system: 1000         # 系统提示预留
        reserved-for-tools: 2000          # 工具定义预留
        reserved-for-response: 4000       # 回复预留
        
        # 压缩阈值
        compression-threshold: 0.9        # 90%触发压缩
```

### 计算结果

```
总窗口：1,048,576 tokens
可用额度：1,048,576 - 1,000 - 2,000 - 4,000 = 1,041,576 tokens
短期记忆预算：500,000 tokens（约48%）
```

---

## 五、与MessageWindowChatMemory对比

### MessageWindowChatMemory

```java
// Spring AI 2.0默认实现
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(chatMemoryRepository)
        .maxMessages(20)  // 固定保留20条消息
        .build();
```

**问题：**
- ❌ 固定消息数，不考虑Token
- ❌ 20条消息可能是5000 tokens，也可能是50000 tokens
- ❌ 无法适配不同模型

### TokenAwareChatMemory

```java
// 我们的自定义实现
ChatMemory chatMemory = new TokenAwareChatMemory(
        shortTermMemory,  // RedisConversationMemory
        tokenBudget       // TokenBudget
);
```

**优势：**
- ✅ 基于Token预算，精确控制
- ✅ 自适应，动态调整
- ✅ 支持不同模型

---

## 六、完整的配置示例

### SpringAIConfig.java

```java
@Configuration
public class SpringAIConfig {

    @Bean
    public TokenBudget tokenBudget() {
        return new TokenBudget();  // 从配置文件读取参数
    }

    @Bean
    public RedisConversationMemory shortTermMemory(
            RedisUtils redisUtils,
            ObjectMapper objectMapper,
            TokenBudget tokenBudget) {
        return new RedisConversationMemory(
                redisUtils,
                objectMapper,
                "default",  // conversationId占位符
                tokenBudget.getAvailableForConversation()
        );
    }

    @Bean
    public ChatMemory chatMemory(
            RedisConversationMemory shortTermMemory,
            TokenBudget tokenBudget) {
        return new TokenAwareChatMemory(shortTermMemory, tokenBudget);
    }

    @Bean
    public Advisor memoryAdvisor(ChatMemory chatMemory) {
        return new MessageChatMemoryAdvisor(chatMemory);
    }
}
```

### application.yml

```yaml
paicoding:
  ai:
    memory:
      token-budget:
        context-window: 1048576           # DeepSeek 1M
        reserved-for-system: 1000
        reserved-for-tools: 2000
        reserved-for-response: 4000
        compression-threshold: 0.9
```

---

## 七、性能对比

| 指标 | MessageWindowChatMemory | TokenAwareChatMemory |
|------|------------------------|---------------------|
| **精确度** | 低（固定消息数） | **高（Token精确控制）** |
| **自适应** | ❌ | ✅ |
| **支持不同模型** | ❌ | ✅ |
| **避免Token超限** | ⚠️ 不保证 | ✅ 保证 |
| **实现复杂度** | 低 | 中 |

---

## 八、总结

### 为什么不用MessageWindowChatMemory？

1. ❌ 固定消息数，不考虑Token
2. ❌ 无法适配不同模型
3. ❌ 可能导致Token超限

### 为什么用TokenAwareChatMemory？

1. ✅ 基于Token预算，精确控制
2. ✅ 自适应，动态调整
3. ✅ 支持不同模型
4. ✅ 避免Token超限

### 最佳实践

```yaml
# 根据模型配置不同的Token预算
# DeepSeek 1M
paicoding.ai.memory.token-budget.context-window: 1048576

# GPT-4 128k
# paicoding.ai.memory.token-budget.context-window: 128000

# Claude 200k
# paicoding.ai.memory.token-budget.context-window: 200000
```

**结论：基于Token预算的管理比固定消息数管理更科学、更精确、更可靠！** 🎯
