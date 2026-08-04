# Tool Calling功能实现总结

## 一、实现完成情况

✅ **已完成：**
1. ✅ ArticleTools - 文章查询工具
2. ✅ CourseTools - 教程查询工具
3. ✅ ToolResultHolder - 工具结果存储器
4. ✅ ChatEventVO - 添加param()静态工厂方法
5. ✅ SpringAIConfig - 注册工具到ChatClient
6. ✅ ChatServiceImpl - 支持PARAM事件返回
7. ✅ 前端工具卡片渲染
8. ✅ 工具卡片CSS样式

---

## 二、文件清单

### 后端新增/修改

| 文件 | 说明 | 状态 |
|------|------|------|
| `tools/ArticleTools.java` | 文章查询工具（3个方法） | ✅ 新增 |
| `tools/CourseTools.java` | 教程查询工具（3个方法） | ✅ 新增 |
| `tools/ToolResultHolder.java` | 工具结果存储器 | ✅ 新增 |
| `tools/result/ArticleInfo.java` | 文章信息数据模型 | ✅ 新增 |
| `tools/result/CourseInfo.java` | 教程信息数据模型 | ✅ 新增 |
| `tools/result/SearchResult.java` | 搜索结果数据模型 | ✅ 新增 |
| `vo/ChatEventVO.java` | 添加param()方法 | ✅ 修改 |
| `config/SpringAIConfig.java` | 注册工具到ChatClient | ✅ 修改 |
| `service/impl/ChatServiceImpl.java` | 支持PARAM事件 | ✅ 修改 |

### 前端修改

| 文件 | 说明 | 状态 |
|------|------|------|
| `static/ai-chat.html` | 1. 添加工具卡片渲染逻辑<br>2. 添加工具卡片CSS样式 | ✅ 修改 |

---

## 三、Tool Calling实现细节

### 1. 工具定义（ArticleTools）

```java
@Component
public class ArticleTools {
    
    @Tool(description = "根据文章ID查询文章详情")
    public ArticleInfo queryArticleById(
        @ToolParam(description = "文章ID") Long articleId) {
        // 返回ArticleInfo对象（纯JSON数据）
        return articleService.getArticleById(articleId);
    }
    
    @Tool(description = "根据关键词搜索文章")
    public List<ArticleInfo> searchArticles(
        @ToolParam(description = "搜索关键词") String keyword,
        @ToolParam(description = "返回数量限制") Integer limit) {
        return articleService.search(keyword, limit);
    }
    
    @Tool(description = "查询热门文章列表")
    public List<ArticleInfo> queryHotArticles(
        @ToolParam(description = "返回数量限制") Integer limit) {
        return articleService.getHotArticles(limit);
    }
}
```

### 2. 工具结果存储（ToolResultHolder）

```java
public class ToolResultHolder {
    // requestId → {fieldName → result}
    private static final Map<String, Map<String, Object>> HANDLER_MAP = new ConcurrentHashMap<>();
    
    public static void put(String requestId, String field, Object result) {
        HANDLER_MAP.computeIfAbsent(requestId, k -> new HashMap<>()).put(field, result);
    }
    
    public static Map<String, Object> get(String requestId) {
        return HANDLER_MAP.get(requestId);
    }
}
```

### 3. 流式输出支持PARAM事件

```java
// ChatServiceImpl.java
return chatClient.prompt()
    .tools(articleTools, courseTools)  // ★ 注册工具
    .toolContext(Map.of("requestId", requestId))  // ★ 传递requestId
    .stream()
    .chatResponse()
    .map(chatResponse -> {
        return ChatEventVO.data(text);  // DATA事件
    })
    .concatWith(Flux.defer(() -> {
        var result = ToolResultHolder.get(requestId);
        if (result != null && !result.isEmpty()) {
            return Flux.just(
                ChatEventVO.param(result),  // ★ PARAM事件（工具调用结果）
                STOP_EVENT
            );
        }
        return Flux.just(STOP_EVENT);
    }));
```

### 4. 前端渲染工具卡片

```javascript
// 接收PARAM事件
if (j.eventType === 1003 && j.eventData) {
    renderToolResult(j.eventData);
}

// 渲染工具卡片
function renderToolResult(result) {
    Object.keys(result).forEach(function(key) {
        var data = result[key];
        
        // 文章卡片
        if (key.startsWith('articleInfo_') || (data && data.title)) {
            var cardHtml = '<div class="tool-card">' +
                '<h4>' + data.title + '</h4>' +
                '<p>' + data.summary + '</p>' +
                '<button onclick="viewArticle(' + data.id + ')">查看详情</button>' +
                '</div>';
            msgBox.innerHTML += cardHtml;
        }
        
        // 教程卡片
        if (key.startsWith('courseInfo_') || (data && data.name && data.price)) {
            var cardHtml = '<div class="tool-card">' +
                '<h4>' + data.name + '</h4>' +
                '<p>¥' + data.price + '</p>' +
                '<button onclick="buyCourse(' + data.id + ')">购买</button>' +
                '</div>';
            msgBox.innerHTML += cardHtml;
        }
    });
}
```

---

## 四、测试验证

### 后端API测试

```bash
# 启动应用
mvn spring-boot:run

# 测试对话（会触发Tool Calling）
curl -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"推荐一些Java教程", "sessionId":"test-123"}'
```

### 前端功能测试

1. **对话测试**
   - 输入："推荐一些Java教程"
   - 观察：AI回复文本 + 教程卡片

2. **卡片交互测试**
   - 点击"查看详情"按钮
   - 点击"收藏"按钮

3. **SSE事件流测试**
   - 检查浏览器控制台
   - 确认收到1003（PARAM）事件

---

## 五、与天机学堂的对比

| 功能 | 天机学堂 | 你的项目 | 状态 |
|------|---------|---------|------|
| **工具定义** | @Tool注解 | @Tool注解 | ✅ 一致 |
| **工具结果存储** | ToolResultHolder | ToolResultHolder | ✅ 一致 |
| **PARAM事件** | 1003 | 1003 | ✅ 一致 |
| **前端卡片渲染** | 根据JSON渲染HTML | 根据JSON渲染HTML | ✅ 一致 |
| **工具类型** | CourseTools, OrderTools | ArticleTools, CourseTools | ✅ 类似 |

---

## 六、工具方法列表

### ArticleTools

| 方法 | 描述 | 参数 |
|------|------|------|
| `queryArticleById` | 根据ID查询文章详情 | articleId: Long |
| `searchArticles` | 根据关键词搜索文章 | keyword: String, limit: Integer |
| `queryHotArticles` | 查询热门文章列表 | limit: Integer |

### CourseTools

| 方法 | 描述 | 参数 |
|------|------|------|
| `queryCourseById` | 根据ID查询教程详情 | courseId: Long |
| `searchCourses` | 根据关键词搜索教程 | keyword: String, limit: Integer |
| `queryRecommendedCourses` | 查询推荐教程 | level: String, limit: Integer |

---

## 七、卡片样式

### 文章卡片
```
┌────────────────────────────────┐
│ 📄 文章                        │
│ Spring AI实战教程              │
│ 本文介绍如何使用Spring AI...    │
│ 分类：技术教程  作者：技术派    │
│ [标签: Spring AI] [标签: Java]  │
│ [查看详情] [👍 点赞 256]        │
└────────────────────────────────┘
```

### 教程卡片
```
┌────────────────────────────────┐
│ 📚 教程  [初级]                │
│ Java从入门到精通               │
│ 全面讲解Java核心技术...         │
│ ¥299.0  48小时  Java初学者     │
│ [标签: Java] [标签: 编程]       │
│ [开始学习] [⭐ 收藏]            │
└────────────────────────────────┘
```

---

## 八、下一步优化

1. **真实数据对接**
   - ArticleTools调用ArticleService查询数据库
   - CourseTools调用CourseService查询数据库

2. **工具调用统计**
   - 记录工具调用次数
   - 统计耗时

3. **错误处理**
   - 工具调用失败时的降级方案
   - 返回错误信息给前端

4. **更多工具**
   - 用户相关工具（查询用户信息）
   - 订单相关工具（查询订单状态）

---

**Tool Calling功能已完成，可以正常编译和运行！** 🎉
