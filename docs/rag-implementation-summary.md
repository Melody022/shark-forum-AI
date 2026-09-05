# paicoding-ai RAG 实现与面试复盘摘要

> 文档版本：v1.0  
> 更新时间：2026-09-05  
> 适用项目：`paicoding-ai`

## 1. 先说结论

本项目采用“统一内容块 + 父子 Chunk + 混合检索 + 结构化表格查询”的 RAG 方案。

当前已增加 MIMO OpenAI-compatible 多模态通道：聊天窗口选择图片后，图片会以标准 `image_url` content part 发送给当前多模态 Provider；无图片请求继续使用原有 Spring AI Agent，以保留 Skill、Tool Calling 和 ChatMemory 行为。后台模型配置可以动态切换 MIMO/DeepSeek，动态配置首先用于多模态请求，普通文本链路后续可再统一迁移到动态 HTTP 路由。

最终确定的 PDF 路线是：

```text
PDF
  -> LiteParse 负责页面渲染、空间文本解析和 OCR 编排
  -> 阿里云 OCR 负责扫描件文字识别
  -> OCR 适配层统一输出文本、bbox、页码和置信度
  -> 复杂表格、图表和流程图按需交给多模态模型增强
  -> 统一转换为 ContentBlock
  -> 生成 parent / child Chunk
  -> child 使用 BM25 + 向量混合检索
  -> 命中后补充 parent 和相邻 child
  -> 需要统计、过滤、排序时再执行受限结构化查询
```

核心原则：

> 文件后缀决定解析器，内容类型决定切片方式，用户问题决定查询方式。

## 2. 当前项目实际模型配置

启动时默认 profile 是 `dev`，因此 `application-dev.yml` 会覆盖 `application.yml` 中的同名配置。

当前有效配置如下：

| 能力 | 当前配置 |
|---|---|
| 聊天模型 | DeepSeek，`deepseek-v4-flash` |
| 聊天 Base URL | `https://api.deepseek.com` |
| Embedding 服务 | 阿里云兼容 OpenAI 接口 |
| Embedding Base URL | 当前 dev 配置中的阿里云百炼兼容地址 |
| Embedding 模型 | `text-embedding-v3` |
| ES | `http://localhost:9200`，可通过 `ES_URIS` 覆盖 |
| Redis | `127.0.0.1:6379` |
| MySQL | `pai_coding` 数据库 |

因此，回答“现在的 Embedding 是不是阿里云的”：

**是。当前开发 profile 实际使用的是阿里云兼容接口上的 `text-embedding-v3`。**

主配置里写的是 `text-embedding-v4`，但被 `application-dev.yml` 的 `text-embedding-v3` 覆盖。后续如果切换模型，要同时确认：

1. Embedding 接口返回维度；
2. ES `dense_vector` 的 `dims`；
3. 已有索引是否需要重建；
4. 新旧向量不能混在同一个不同维度的索引中。

## 3. 已实现的 RAG 主链路

### 3.1 统一内容块

不同格式先统一为 `ContentBlock`，当前支持：

- `HEADING`：标题和章节元数据；
- `TEXT`：普通段落；
- `LIST`：列表内容；
- `CODE`：代码块；
- `TABLE`：表格和表头；
- `IMAGE`：图片、OCR 文本或扫描页。

每个内容块可以保留：

```text
blockId
type
content
pageStart / pageEnd
sectionPath
parentId
x / y / width / height
sourcePath
metadata
searchable
```

这样后续切片器不需要知道输入是 PDF、Word 还是 Excel。

### 3.2 父子 Chunk

切片分两层：

```text
章节或内容组
  -> parent chunk：默认最大 6000 字符
      -> child chunk：默认最大 1800 字符
          -> overlap：默认 240 字符
```

父块的作用是恢复上下文，子块的作用是检索。

只对 `searchable=1` 的 child 生成 Embedding，父块不会进入向量检索。

不同内容类型使用不同边界：

| 内容类型 | 切片策略 |
|---|---|
| 普通文本 | 段落 -> 句子 -> 标点边界 -> 字符兜底 |
| 代码 | 尽量保持代码块和换行，超长时按行切分 |
| 列表 | 保留列表项，不和普通段落混合 |
| 表格 | 表头 + 行组，每个行组重复表头 |
| 图片 | OCR 文本或图片描述作为可检索内容 |
| 标题 | 主要作为章节树和 `sectionPath`，通常不单独向量化 |

### 3.3 混合召回

当前 ES 检索路径是：

```text
KNN 向量召回
  + BM25 关键词召回
  -> RRF 融合排序
  -> parentId 去重
  -> 补充前后相邻 child
  -> 补充 parent 内容
  -> 返回来源信息
```

返回结果会携带：

- 文件名；
- 文档 ID 和知识库 ID；
- Chunk ID；
- 父 Chunk ID；
- 章节路径；
- 内容类型；
- 起止页码；
- 命中内容；
- 父章节和相邻内容。

### 3.4 Chat 链路

聊天请求中的 `userId` 会沿着下面的链路传递：

```text
ChatDTO.userId
  -> AgentContext.userId
  -> VectorSearchService
  -> 用户可访问知识库过滤
  -> 混合召回
  -> 来源和上下文注入 Agent Prompt
```

知识库故障时会降级为普通聊天，不让 ES、Embedding 或数据库故障直接阻塞对话。

## 4. LiteParse + 阿里云 OCR 方案

MIMO 与 LiteParse/OCR 的职责不同：MIMO 负责图片理解、复杂表格和图表增强；LiteParse 负责 PDF 页面解析和 OCR 编排；阿里云 OCR 负责扫描文字识别。三者可以组合，但不能把 MIMO 当作 OCR 的唯一替代品。

### 4.1 为什么选择这条路线

LiteParse 适合作为本地 PDF 解析编排层：

- 支持 PDF 页面渲染；
- 能识别文本层和扫描页；
- 可以输出 JSON；
- 能保留页码和 bbox；
- 可以通过 `--ocr-server-url` 对接外部 OCR；
- 不需要把整个 PDF 上传到 LlamaParse 云服务。

阿里云 OCR 适合负责：

- 中文扫描件文字识别；
- 返回文本块和坐标；
- 返回识别置信度；
- 对接已有阿里云百炼账号和网络环境。

推荐调用关系：

```text
LiteParse 子进程
  -> POST 页面图片到内部 OCR 接口
  -> InternalOcrController
  -> AliyunOcrService
  -> AliyunOcrAdapter
  -> 返回 LiteParse 约定的 results JSON
```

LiteParse 期望的内部 OCR 返回格式类似：

```json
{
  "results": [
    {
      "text": "识别出的一行字",
      "bbox": [10, 20, 300, 60],
      "confidence": 0.98
    }
  ]
}
```

阿里云原始响应不直接等于这个格式，需要做适配。适配层必须负责：

- 解析嵌套的文本块；
- 将四角坐标转换为 `[x0, y0, x1, y1]`；
- 保留置信度；
- 处理空文本和异常响应；
- 避免把阿里云 SDK 数据结构泄漏到 LiteParse 或 RAG 层。

### 4.2 LiteParse 运行时配置建议

建议最终采用独立的配置组：

```yaml
rag:
  pdf:
    engine: liteparse
  liteparse:
    command: ${LITEPARSE_COMMAND:lit}
    ocr-enabled: ${LITEPARSE_OCR_ENABLED:true}
    ocr-language: ${LITEPARSE_OCR_LANGUAGE:chi_sim+eng}
    dpi: ${LITEPARSE_DPI:150}
    max-pages: ${LITEPARSE_MAX_PAGES:1000}
    timeout-seconds: ${LITEPARSE_TIMEOUT_SECONDS:300}
    ocr-server-url: ${LITEPARSE_OCR_SERVER_URL:http://127.0.0.1:8081/api/v1/internal/ocr/liteparse}
```

ProcessBuilder 必须具备：

1. 超时控制；
2. 超时后 `destroyForcibly()`；
3. stdout 和 stderr 重定向，避免缓冲区写满导致子进程卡住；
4. 临时输入、JSON 输出和日志文件 finally 清理；
5. 文件大小、页数和并发限制；
6. 对命令路径和参数做白名单或固定配置，不能把用户输入直接拼进 shell 命令。

### 4.3 阿里云 OCR 配置

建议使用独立的环境变量：

```text
ALIYUN_OCR_ENABLED=true
ALIYUN_OCR_ENDPOINT=ocr-api.cn-hangzhou.aliyuncs.com
ALIYUN_OCR_ACCESS_KEY_ID=...
ALIYUN_OCR_ACCESS_KEY_SECRET=...
ALIYUN_OCR_TYPE=Advanced
ALIYUN_OCR_OUTPUT_COORDINATE=points
ALIYUN_OCR_TIMEOUT_SECONDS=30
```

真实密钥只放在本机环境变量、密钥管理服务或不提交的本地配置中，不放入 Git、文档、日志和前端代码。

## 5. PDF 表格方案

你的表格方案方向正确，最终建议变成下面这样：

```text
PDF 页面
  -> LiteParse / OCR 得到文本块和 bbox
  -> 表格区域检测
  -> 行列结构恢复
  -> 必要时多模态模型校正
  -> 保存原图、OCR 原文、结构化数据和自然语言描述
```

一个表格建议同时保留四份信息：

1. 原始页面或表格裁剪图；
2. OCR 原始结果，包含 bbox 和置信度；
3. 结构化表格数据；
4. 表格描述、表头和行组 Chunk。

推荐的关系型数据结构：

```text
knowledge_table
  table_id
  document_id
  title
  headers_json
  units_json
  page_start
  page_end
  section_path
  raw_content
  description
  source_image_path

knowledge_table_row
  id
  table_id
  row_index
  values_json
  normalized_values_json
```

ES 中保存：

- 表格描述 Chunk；
- 表头 + 行组 Chunk；
- `tableId`；
- `pageStart/pageEnd`；
- `sectionPath`；
- `blockType=TABLE`。

### 表格查询原则

向量召回只能帮助找到相关表格，不能单独决定是否执行 SQL。

解释性问题走 RAG：

```text
Spring Boot 版本是如何演进的？
这张表说明了什么趋势？
```

计算、排序、过滤和聚合问题走结构化查询：

```text
2020 年之后有几个版本？
哪个版本发布日期最晚？
平均销售额是多少？
按年份统计数量。
```

推荐让模型输出受限查询计划，而不是直接执行任意 SQL：

```json
{
  "tableId": "table_001",
  "operation": "COUNT",
  "filters": [
    {"field": "year", "operator": ">", "value": 2020}
  ],
  "groupBy": [],
  "limit": 100
}
```

后端根据白名单生成参数化 SQL。必须限制：

- 只允许 `SELECT`；
- 只允许白名单表和字段；
- 强制 `LIMIT`；
- 禁止 DDL、INSERT、UPDATE、DELETE；
- 禁止多语句执行；
- 绑定参数，不能字符串拼接用户输入；
- 设置超时和最大返回行数；
- 查询结果必须带回表格和页码来源。

## 6. 多模态 LLM 的使用边界

可以结合多模态 LLM，但不建议对所有 PDF 页面默认调用。

适合调用的场景：

- OCR 置信度低；
- 表格列错位或合并单元格复杂；
- 跨页表格；
- 柱状图、折线图、饼图；
- 流程图、架构图和时序图；
- 需要生成趋势、业务含义或图片描述。

不适合默认调用的场景：

- 普通文本层 PDF；
- OCR 结果清晰的扫描正文；
- 大量重复格式的简单页面。

推荐策略：

```text
确定性解析优先
  -> 低置信度或复杂版面才调用视觉模型
  -> 视觉模型输出结构化 JSON
  -> 校验行数、列数、单位和合计关系
  -> 保存原图和原始 OCR，不能只保留模型描述
```

多模态模型生成的数字必须能够追溯到：

- 原始图片；
- OCR 文本块；
- bbox；
- 表格单元格；
- 页码。

否则不能把模型描述当作精确事实来源。

## 7. 当前踩坑记录

### 7.1 `OcrService` Bean 启动失败

现象：

```text
Parameter 0 of constructor in ImageParser required a bean of type OcrService
```

原因：默认 `NoopOcrService` 使用 `@ConditionalOnMissingBean` 后，在当前组件扫描顺序下没有注册成功。

处理：

- 默认实现改为普通 `@Component`；
- 增加 `@Primary`；
- 未配置真实 OCR 时使用空结果降级；
- ImageParser 和 PdfParser 仍然可以正常注入。

### 7.2 Embedding 配置被 profile 覆盖

主配置使用 `text-embedding-v4`，但 dev profile 使用 `text-embedding-v3`。排查模型问题时不能只看 `application.yml`，必须确认最终 profile 合并后的配置。

### 7.3 `.env` 不一定自动生效

项目当前注释已经说明：`spring-dotenv 4.0.0` 与 Spring Boot 4.x 的接口兼容性存在问题，不能假设 `.env` 一定会覆盖 Spring 配置。

更可靠的方式是：

- 使用系统环境变量；
- 使用 IDE 的 Run Configuration 环境变量；
- 使用不提交的 `application-dev.yml`；
- 启动时打印非敏感的配置摘要，例如 provider、model、endpoint，不打印 API Key。

### 7.4 PDF 不能直接当纯文本

PDF 可能存在：

- 多栏顺序错乱；
- 页眉页脚重复；
- 每行断开；
- 中文字体乱码；
- 没有文本层；
- 表格列被拼成普通文本。

因此不能只做：

```java
text.replaceAll("\\s+", " ");
```

必须保留页边界，并区分文本层、扫描页、表格和图片。

### 7.5 表格数字不等于 SQL 查询

数字占比高不代表一定走 SQL。版本表、配置表、参数说明表仍然可能是语义问答；只有过滤、排序、统计、聚合才应该进入结构化查询。

### 7.6 ES 向量维度不能随意变化

如果 Embedding 从 `v3` 切到 `v4`，或者切换到其他模型，必须确认输出维度。ES `dense_vector` 的维度固定，不能把不同维度的向量写入同一个索引。

### 7.7 父块不能直接全部塞给 LLM

命中一个 child 后直接把整个章节塞进上下文，会导致上下文膨胀。当前设计是：

```text
命中 child
  + 前后相邻 child
  + 章节路径
  + 页码
  + 必要时截断的 parent 内容
```

### 7.8 ES 不可用时需要降级

Embedding、ES、OCR 都属于外部依赖。检索服务需要：

- 超时；
- 日志记录原因；
- 数据库关键词检索或普通聊天降级；
- 不能因为外部检索失败导致整个聊天接口不可用。

## 8. 你现在需要做什么

你不需要把任何 Key 发给我。代码实现、配置模板、内部 OCR 接口、LiteParse ProcessBuilder、阿里云响应适配和测试代码都可以在本地完成。

真正需要你准备的是运行环境和账号权限：

### 立即处理

1. **轮换已经出现在项目文件中的 API Key 和数据库密码。** 当前 `application-dev.yml` 和 `.env` 曾包含真实凭证，不应继续使用。
2. 将新凭证放入本机环境变量或本地配置，不提交到 Git。
3. 确认 MySQL 的数据库名、Redis 和 Elasticsearch 地址与 `application.yml` 一致。

### 联调前准备

1. 安装 LiteParse，并确认命令可执行：

   ```bash
   lit --help
   lit parse sample.pdf --format json
   ```

2. 准备阿里云 OCR 账号和最小权限 AccessKey。
3. 开通教程中对应的通用文字识别高精版能力。
4. 准备一份普通文本 PDF、一份扫描 PDF、一份含表格 PDF，作为回归样本。
5. 执行 `V4__hierarchical_rag_chunks.sql`，让已有数据库具备父子 Chunk 元数据字段。
6. 如果已有 `knowledge_vectors` 是旧映射，切换 Embedding 维度或字段后需要重建索引并重新向量化。

### 暂时不需要准备

- 不需要现在注册 LlamaParse；
- 不需要现在提供多模态模型 Key；
- 不需要把阿里云 AccessKey 粘贴到聊天窗口；
- 不需要一开始就做复杂 Text-to-SQL；
- 不需要一开始接入图片向量检索。

## 9. 当前完成度和后续阶段

| 模块 | 当前状态 | 说明 |
|---|---:|---|
| ContentBlock 统一模型 | 已完成 | 支持文本、标题、代码、列表、表格、图片 |
| parent / child Chunk | 已完成 | child 检索，parent 恢复上下文 |
| BM25 + KNN + RRF | 已完成 | ES 混合召回 |
| 相邻 Chunk 和 parent 补充 | 已完成 | 返回来源和上下文 |
| TXT / Markdown / HTML | 已完成 | 结构化解析 |
| DOCX | 已完成 | 标题、段落、代码、表格 |
| Excel | 已完成 | 工作表、表头、行组 |
| PDF 页面和页码 | 已完成 | 当前保留 PDF 页面边界 |
| 真正 LiteParse 集成 | 待实现 | 需要接入 CLI 和 JSON 映射 |
| 阿里云 OCR 桥接 | 待实现 | 需要 SDK、内部接口和响应适配 |
| PDF 表格行列恢复 | 待实现 | 需要 bbox 聚类和结构校验 |
| 表格关系型存储 | 待实现 | 建议增加 table 和 row 表 |
| 受限表格查询工具 | 待实现 | 查询计划 + 白名单 + 参数化 SQL |
| 多模态复杂页面增强 | 可选 | 低置信度或复杂图表再调用 |

## 10. 面试时可以这样讲

### 30 秒版本

> 我先把 PDF、Word、Excel 和图片解析成统一的结构化内容块，保留章节、页码、坐标和来源信息。切片时采用父子 Chunk，父块用于恢复上下文，子块用于检索，并按照文本、代码、列表和表格使用不同边界策略。检索阶段使用 BM25 和向量 KNN 混合召回，再用 RRF 融合、按父块去重并补充相邻内容。扫描 PDF 通过 LiteParse 做页面和 OCR 编排，阿里云 OCR 负责文字识别。表格同时保存描述和结构化行列数据，解释问题走 RAG，统计、排序和过滤问题走受限的结构化查询，最终生成带文件、章节和页码来源的回答。

### 追问“为什么不用固定 512 字符切分”

> 固定字符切分会破坏标题、代码、列表和表格结构，也会造成 PDF 页码和来源丢失。我先按结构恢复内容块，再按语义单元切 child。对于超长文本才逐步降级到句子、分词边界和字符兜底。

### 追问“为什么表格不全部转成文本”

> 表格既有语义检索问题，也有精确计算问题。表头和行组转成文本便于 BM25 和向量召回，原始行列存关系型数据库便于过滤、排序和聚合。两份数据互相补充，不能只保留其中一份。

### 追问“为什么不让 LLM 直接生成 SQL”

> LLM 只输出受限查询计划，后端根据表和字段白名单生成参数化 SQL，并限制只读、LIMIT、超时和返回行数。这样可以避免任意 SQL、越权访问和误操作。

## 11. 推荐后续实施顺序

```text
第一步：轮换密钥并整理环境变量
第二步：LiteParse CLI 集成和 JSON 映射
第三步：阿里云 OCR 内部桥接和扫描 PDF 回归
第四步：PDF 表格 bbox 行列恢复
第五步：表格 MySQL 存储和 tableId 元数据
第六步：受限表格查询工具
第七步：复杂表格、图表的多模态增强
第八步：评测集、召回率、引用准确率和延迟监控
```

第一版不建议同时引入 LlamaParse、图片向量库、复杂 Text-to-SQL 和 ES Join Field。先把“解析 -> 结构化 -> 切片 -> 混合召回 -> 来源回溯”主链路跑通，再按评测结果增加能力。
