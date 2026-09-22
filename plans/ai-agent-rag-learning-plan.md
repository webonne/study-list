# AI Agent / RAG 学习计划（面向 5 年经验后端工程师 · Java + Go 双轨）

> 目标读者：有 5 年 Java 后端经验（Spring Boot / 微服务 / MySQL / Redis / MQ 熟练），手上也有 Go 项目但还不太熟，零或少量 AI 经验。
> 总周期：**14 周**，每周投入 **8~12 小时**（工作日 1h + 周末 4h 左右）。
> 产出导向：每个阶段都有可运行的代码产物，最终沉淀 3 个可以写进简历、可以对外演示的项目。
> 附带收益：在做 AI 应用的过程中把 Go 吃透，而不是先花两周刷语法。

---

## 0. 先建立正确的认知

### 0.1 你的优势和短板

| | 说明 |
|---|---|
| **优势** | 工程化能力、并发/IO、服务治理、可观测、数据库与检索（ES 经验尤其值钱）、成本意识。**RAG 和 Agent 本质上 80% 是工程问题，20% 是模型问题**，这正是后端工程师的主场。 |
| **短板** | 对"不确定性系统"的直觉（同样输入输出不一样，没法写 `assertEquals`）、评估方法论（eval）、Python 生态（大量前沿方案先出 Python 版）、向量检索与 NLP 基础概念。 |

**结论**：不要从"学 Python / 学深度学习 / 学 Transformer 论文"开始。从**调 API 做出能跑的东西**开始，缺什么补什么。数学和模型原理在第 5 阶段按需补，不要卡在第一周。

### 0.2 三个必须记住的原则

1. **先 Prompt，再 Workflow，最后才 Agent。**
   能一次 API 调用解决的，不要写 workflow；能用代码固定编排的 workflow 解决的，不要上 Agent。Agent（模型自主决定调用哪个工具、循环几轮）成本高、延迟高、难调试，只在"任务多步且无法提前穷举流程"时才用。
2. **没有 eval 的优化都是玄学。**
   你改了 prompt、换了 chunk 策略、加了 rerank——怎么知道变好了？必须有一套**黄金问答集 + 自动打分脚本**。这是本计划里唯一一个"不做就白学"的环节（第 3 阶段）。
3. **检索质量决定 RAG 上限，模型只决定下限。**
   90% 的 RAG 效果问题出在"没召回到正确的片段"，而不是"模型不会答"。先查检索，再怪模型。

---

## 1. 语言与技术选型

**结论：Java 主力，Go 补位，Python 会读。**

| 语言 | 定位 | 投入 |
|---|---|---|
| **Java** | 主力。业务落地、离线数据处理（解析/切块/embedding/入库）、能进 CI 的 eval | 全程 |
| **Go** | 补位。**Agent 运行时、MCP Server、高并发在线服务**——这几块 Go 明显更合适（单二进制交付、goroutine 并行、context 级联取消） | 阶段一 + 阶段四为主 |
| **Python** | 只需"能读能改"（约 2~3 天） | 按需 |

**为什么 Java 做主力**：Spring AI / LangChain4j 已经足够成熟，能直接融进现有 Spring Boot 体系——事务、连接池、监控、鉴权、灰度全都复用，同事也接得住。

**为什么值得搭一条 Go 线**：不是"Go 也能做"，而是**有些活 Go 确实更合适**。MCP Server 编译成一个二进制扔给团队就能跑，不用装 JRE；Agent 的并行工具调用用 `errgroup` 几行搞定；用户关掉页面，`context` 一取消整条链路（LLM 流、检索、rerank）自动停，直接省 token——这几件事在 Java 里都要绕一圈。

**为什么 Python 只需要读懂**：新方法先在 Python 生态出现（论文复现、embedding 微调、数据清洗）。你需要的是"看懂示例然后翻译过来"，不是成为 Python 工程师。

> ⚠️ **不要把 14 周的内容用两种语言各做一遍。** 时间翻倍，收获远不到两倍——RAG 和 Agent 的难点 80% 与语言无关。
> 按语言优势分配阶段的具体方案见 **[tracks/README.md](../tracks/README.md)**。

### 1.1 技术栈详情

各语言的完整选型表、SDK 用法、各阶段实现要点和坑，见语言轨道文档：

- **[Java 轨道](../tracks/java.md)** — Spring AI / pgvector / Tika / Micrometer，以及 Java 特有的坑（包命名空间、SSE 断连、MDC 跨线程丢失、Batch 乱序）
- **[Go 轨道](../tracks/go.md)** — 官方 Go SDK / errgroup / pprof，含**面向 Java 工程师的 Go 速成**（错误即值、context、接口隐式实现、typed nil 陷阱等）

两条轨道共用的判断：
- **向量库从 pgvector 起步**，不要一上来就引专用向量库。一个库解决元数据过滤 + 向量检索 + 事务，运维成本最低。
- **Embedding 要单独选型**（Anthropic 不提供 embedding 接口）。中文场景优先 bge-m3 / BGE-zh 系列。
- **Embedding 和 rerank 的本地推理、文档解析，交给 Python/Java 服务**，Go 调 HTTP。Go 在这一层生态确实薄，硬写是浪费时间。
- **Rerank 必做**，是性价比最高的单点提升。


### 1.2 关于模型选择（以 Claude 为例）

| 模型 | Model ID | 适用场景 |
|---|---|---|
| Claude Opus 5 | `claude-opus-5` | 复杂推理、Agent 主控、代码生成。默认首选 |
| Claude Sonnet 5 | `claude-sonnet-5` | 日常对话、成本敏感的高并发链路 |
| Claude Haiku 4.5 | `claude-haiku-4-5` | 批量抽取、分类、子 Agent 里的"苦力"任务 |

选型方法论（比记住某个型号更重要）：
- **先用最强的模型把效果做出来**，跑通 eval 拿到质量基线，再往下降级找"质量不掉的最便宜档位"。反过来（先便宜再调优）会让你分不清是模型不行还是 prompt 不行。
- 同一个链路上不同节点可以用不同模型：主控用强模型，批量抽取/摘要用小模型。
- 成本优化顺序：**Prompt Caching（免费收益）→ 精简输入 token → 降低 effort/思考深度 → 换小模型**。不要一上来就换小模型。

---

## 2. 分阶段计划

### 阶段一（第 1~2 周）：LLM API 基本功

**目标**：能像调用一个"不确定的 RPC 接口"一样调用大模型，并理解它的计费、限流、失败模式。

**学什么**

1. Messages API 的核心概念：`system` / `messages` 数组、多轮对话要自己把历史带回去（服务端无状态）、`max_tokens`、`stop_reason`。
2. **Streaming**：SSE 流式返回，长输出/大 `max_tokens` 必须用流式，否则容易 HTTP 超时。Java SDK 有 `StreamResponse`。
3. **思考（extended thinking / adaptive thinking）**：新模型上开启自适应思考，复杂任务质量明显提升；注意"思考深度"是可调的成本旋钮（effort）。
4. **结构化输出**：让模型稳定返回 JSON —— 用 `output_config.format`（structured outputs）或工具的 `strict: true`，而不是在 prompt 里跪求"请只返回 JSON"。这是 Java 这种强类型语言接入 LLM 的关键点。
5. **Prompt Caching**：把稳定的长前缀（系统提示、知识片段、工具定义）缓存起来，重复请求只付很低的读取成本。**规则是前缀匹配**：渲染顺序是 `tools` → `system` → `messages`，前缀里任何一个字节变了，后面全部失效。所以"当前时间戳""随机 requestId""每次顺序不同的 JSON"放进系统提示里，会让缓存命中率直接归零。验证方法：看响应 `usage.cache_read_input_tokens` 是不是 > 0。
6. **错误处理与重试**：区分可重试（429 / 5xx / 网络）和不可重试（400 / 404）。SDK 有分类异常（`RateLimitException` / `NotFoundException` / `AnthropicServiceException`），按"最具体优先"写 catch 链，别一把 `catch (Exception)`。
7. **Token 计费直觉**：input / output / cache read / cache write 分开计价，output 通常比 input 贵 5 倍左右。用 `count_tokens` 接口而不是自己估算。

**Java 最小示例**

```java
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

AnthropicClient client = AnthropicOkHttpClient.fromEnv(); // 读 ANTHROPIC_API_KEY

MessageCreateParams params = MessageCreateParams.builder()
        .model("claude-opus-5")
        .maxTokens(16000L)
        .addUserMessage("用一句话解释什么是 RAG")
        .build();

Message response = client.messages().create(params);
response.content().stream()
        .flatMap(block -> block.text().stream())
        .forEach(t -> System.out.println(t.text()));
```

**本阶段产出（Project 0）**
一个 Spring Boot 服务，提供 `/chat` 接口：支持多轮对话（历史存 Redis）、SSE 流式输出、结构化输出（返回强类型 DTO）、统一异常与重试、每次调用记录 token 与耗时到日志/Micrometer。

**自检**
- [ ] 能说清楚为什么第二轮对话要把第一轮的 assistant 回复也发回去
- [ ] 能让模型 100% 返回可反序列化的 JSON（不是靠 prompt 祈祷）
- [ ] 打开缓存后能在日志里看到 `cache_read_input_tokens` 上升
- [ ] 能估算一个请求的成本（按 1M token 单价换算）

---

### 阶段二（第 3~5 周）：RAG —— 从能跑到跑对

**目标**：搭出一条完整的 RAG 链路，并理解每个环节的失败模式。

#### 2.1 必须建立的心智模型

RAG = **离线索引** + **在线检索** + **生成**。

```
离线：原始文档 → 解析 → 清洗 → 切块(chunk) → 向量化(embedding) → 写入向量库(+元数据)
在线：用户问题 → 查询改写 → 混合检索(向量+关键词) → 重排(rerank) → 组装上下文 → LLM 生成 → 带引用返回
```

#### 2.2 逐环节要点（重点，这里决定成败）

**① 文档解析**
最脏最重要的一步。PDF 的表格、分栏、页眉页脚、扫描件是四大杀手。建议：先只做 Markdown / HTML / 纯文本跑通全链路，再回头攻 PDF。

**② 切块（Chunking）**
- 不要用"固定 512 字符硬切"作为最终方案，它会把一句话、一个表格切断。
- 按**结构**切：Markdown 按标题层级、代码按函数、法律/规章按条款。
- 保留 **overlap**（10~20%）避免边界信息丢失。
- **Parent-Child（小块检索、大块喂给模型）**：用小块（精确匹配）做检索，命中后把它所属的大块/整节送给模型。这是提升效果的高性价比技巧。
- **Contextual Retrieval（上下文增强切块）**：入库前，用小模型给每个 chunk 生成一句"这段在整篇文档中的位置和主题"的说明，拼到 chunk 前面再做 embedding。对"这个指标是指哪一年"这类跨段指代问题提升显著。配合 prompt caching 做，成本可控。

**③ Embedding**
- 中文场景优先中文/多语模型（bge-m3、BGE-zh 系列）。
- **入库和查询必须用同一个模型同一个版本**，换模型 = 全量重建索引。
- 注意 embedding 的最大输入长度，超了会被截断（静默丢信息）。

**④ 检索**
- **必须做混合检索（Hybrid）**：向量召回（语义）+ BM25 召回（关键词/专有名词/编号/代码），用 RRF（Reciprocal Rank Fusion）融合。纯向量检索在"CVE-2024-1234""SKU-88"这类字符串上必挂。
- **元数据过滤**是 Java 工程师的舒适区也是杀手锏：租户、部门、时间、文档类型、权限。**权限过滤必须在检索层做**（`WHERE tenant_id = ?`），绝不能靠 prompt 说"不要泄露别人的数据"。
- 召回数量：先召回 50~100 条，交给 rerank 收敛到 5~10 条。

**⑤ 查询改写（Query Transformation）**
- 多轮对话中，"它多少钱？"必须先改写成"iPhone 17 Pro 多少钱？"（指代消解），否则检索必然失败。这是多轮 RAG 的头号 bug。
- 进阶：Multi-Query（一个问题拆成多个检索式并集）、HyDE（先让模型编一个假答案，用假答案去检索）、Step-back（先检索更上位的概念）。

**⑥ 重排（Rerank）**
用 cross-encoder 重排模型对召回结果精排。**单点投入产出比最高的一步**，通常能带来 10~20 个点的命中率提升。

**⑦ 生成**
- Prompt 里必须明确："只根据提供的资料回答；资料中没有就说不知道；每句话标注来源编号"。
- **引用（Citation）**要做成结构化的（片段 id → 原文位置），而不是让模型自由发挥写来源，否则引用本身也会幻觉。
- 上下文顺序有影响：最相关的放最前或最后（中间容易被忽略，"lost in the middle"）。

#### 2.3 本阶段产出（Project 1：企业知识库问答）

以你熟悉的内容做数据源（公司 wiki 导出 / Spring 官方文档 / 某个开源项目的 docs），实现：
- Spring Boot + Spring AI（或 LangChain4j）+ pgvector
- 支持增量索引（文档更新后只重建变化的部分，用 content hash 判断）
- 混合检索 + rerank + 带引用的流式回答
- 一个简单的前端或 Swagger 页面能演示

**自检**
- [ ] 给一个只出现在某一个 chunk 里的冷门事实，能被正确召回并回答
- [ ] 问一个知识库里没有的问题，系统说"不知道"而不是编造
- [ ] 多轮追问（"那它的默认值呢？"）能正确改写并命中
- [ ] 能解释"这次回答用了哪几个片段、为什么这几个排在前面"

---

### 阶段三（第 6~7 周）：评估（Eval）—— 分水岭

> **这是整个计划中最容易被跳过、也最不该跳过的阶段。** 跳过它，你就是一个"会调 API 的人"；做了它，你才是"能把 AI 系统做上线的人"。

**目标**：建立可重复、可量化的评估体系，让每一次优化都有数据支撑。

**做什么**

1. **构建黄金集（Golden Set）**：50~200 条 `问题 → 标准答案 → 应该命中的文档片段 id`。来源：真实用户问题日志 > 业务同学出题 > 用 LLM 从文档反向生成（要人工审一遍）。
2. **分层指标**（必须把检索和生成分开评，否则定位不了问题）：
   - 检索层：**Recall@k**（正确片段是否被召回）、**MRR / NDCG**（排序质量）。这是 RAG 的体检核心指标。
   - 生成层：**Faithfulness / 忠实度**（答案是否只来自给定上下文，即幻觉检测）、**Answer Relevance**（是否答到点上）、引用准确率。
3. **LLM-as-Judge**：用强模型当裁判给答案打分。注意三个坑：裁判要用**比被测更强或同级**的模型；评分标准要写成明确的 rubric（打分表）而不是"你觉得好不好"；同一批题目要固定随机性以便对比。
4. **回归流程**：把 eval 做成一条命令（或一个 JUnit 测试），每次改 prompt / 换模型 / 调 chunk 策略都跑一遍，结果存成 CSV 对比。**这和你写单元测试的直觉是一样的**，只是断言从"相等"变成"分数不下降"。

**本阶段产出**
`eval` 模块 + 一份对比报告：至少跑出 3 组对照实验，例如
- chunk 512 vs 1024 vs parent-child
- 纯向量 vs 混合检索 vs 混合+rerank
- Opus 5 vs Sonnet 5（质量差多少、成本差多少）

**自检**
- [ ] 能用一条命令跑完整个 eval 并输出分数
- [ ] 能拿着数据说："加了 rerank 后 Recall@5 从 0.62 提到 0.81，成本增加 X%"
- [ ] 遇到 badcase 能判断是"没召回到"还是"召回了但没答对"

---

### 阶段四（第 8~10 周）：Agent

**目标**：理解 Agent 的本质就是**带工具的循环**，并能做出可控、可观测、可回滚的 Agent。

#### 4.1 核心概念

**Tool Use（函数调用）是 Agent 的地基**，机制其实很简单：

```
1. 你在请求里声明工具（name + description + JSON Schema）
2. 模型返回 stop_reason = "tool_use" 和一个 tool_use 块（含参数）
3. 你的代码执行真实逻辑（查库、调微服务、发邮件）
4. 把结果作为 tool_result 塞回 messages，再发一次请求
5. 重复 2~4，直到 stop_reason = "end_turn"
```

Java 侧要点：
- **工具定义就是接口文档**。`description` 写不好，模型就选错工具/传错参。把它当成给新同事写的 API 说明来写。
- **并行工具调用**：一条 assistant 消息里可能有多个 `tool_use`，要并发执行，然后把**所有** `tool_result` 放在**同一条** user 消息里返回（拆开返回会让模型以后不再并行调用）。
- 工具执行失败要返回 `tool_result` + `is_error: true`，而不是直接抛异常中断循环——让模型有机会重试或换路径。
- `strict: true` + `additionalProperties: false` 保证参数一定符合 schema，省掉大量参数校验代码。

#### 4.2 四种 Agent 实现方式（按"谁提供循环/谁提供部署"划分）

| 方式 | 你写什么 | 适用 |
|---|---|---|
| 手写循环 | 自己写 `while (stopReason == TOOL_USE)` | 想完全掌控控制流；学习阶段**必须手写一遍** |
| SDK Tool Runner | 只写工具函数，SDK 驱动循环 | 大多数自定义工具 Agent |
| 托管 Agent（Managed Agents） | 只写 Agent 配置 + 工具结果 | 需要服务端托管会话、沙箱执行、定时触发 |
| Claude Agent SDK | prompt + options | 偏编码/文件系统类 Agent（注意：这是独立产品，非 API SDK 的一部分） |

**学习路径建议**：手写循环（理解本质）→ Tool Runner（提效）→ 按需了解托管方案。

#### 4.3 MCP（Model Context Protocol）

- **是什么**：一个开放协议，把"工具/数据源"标准化成 MCP Server，任何支持 MCP 的客户端（Claude Code、各种 IDE、你自己的 Agent）都能即插即用。
- **为什么对 Java 工程师重要**：它相当于 AI 世界的"微服务契约"。你可以把公司的订单查询、工单系统、监控系统封装成 MCP Server，所有 AI 应用共享，而不是每个应用重写一遍工具。
- **学法**：先用现成的 MCP Server（文件系统、GitHub、数据库），再用 Java MCP SDK（Spring AI 已集成 MCP）自己写一个内部系统的 MCP Server。
- 在 Messages API 里用 MCP connector 时注意：`mcp_servers` 和 `tools` 里的 `mcp_toolset` **两半都要写**，只写一半会报参数校验错误。

#### 4.4 上下文工程（Context Engineering）—— Agent 的真正难点

Agent 跑十几轮之后，上下文会爆炸。三种应对手段：
- **Compaction（压缩）**：服务端自动把早期历史总结掉。注意要把响应的完整 `content`（含压缩块）原样回传，只取文本会丢状态。
- **Context Editing（清理）**：直接清掉旧的 tool_result 或思考块。它是"删除"，不是"总结"。
- **Memory（记忆）**：让 Agent 把要长期记住的东西写到外部存储（文件/DB），需要时再读回来。

#### 4.5 本阶段产出（Project 2：运维/研发助手 Agent）

做一个你自己每天用得上的东西，例如：
- 工具集：查 Jenkins 构建状态、查 ES 日志、查数据库慢 SQL、查 Git 提交、发钉钉/飞书通知
- 能力："帮我看下昨晚 order-service 的报错集中在哪，关联最近的发布记录，给个结论"
- 必做的工程约束：
  - **危险操作二次确认**（写操作、发通知、改配置必须人工确认）
  - **工具调用全链路日志**（每一轮的输入、工具、参数、结果、token）
  - **最大轮数 + 超时 + 预算上限**（防止无限循环烧钱）
  - 只读工具和写工具分权限

**自检**
- [ ] 能手写一个完整的 tool use 循环（不依赖框架）
- [ ] 能讲清楚 Agent 和 Workflow 的区别，以及什么时候不该用 Agent
- [ ] 你的 Agent 有轮数上限、成本上限和危险操作确认
- [ ] 写过一个 MCP Server 并被至少两个客户端复用

---

### 阶段五（第 11~12 周）：生产化

**目标**：把 demo 变成敢上线的系统。这是你作为 5 年工程师最能建立差异化优势的阶段。

**清单**

**性能与成本**
- Prompt Caching 命中率监控（目标 > 70%）
- 语义缓存（相似问题直接返回历史答案，注意时效性失效策略）
- 流式输出降低首字延迟（TTFT）；并发调用用异步/响应式
- 批量任务走 Batch API（异步、约 5 折）
- 预算护栏：单用户/单会话/单租户的 token 配额

**稳定性**
- 限流与退避（429 要读 `retry-after`）
- 超时分层：连接超时、请求超时、Agent 总时长
- 降级方案：主模型不可用时切备用模型（注意切模型会让 prompt 缓存失效）
- 幂等：Agent 的写操作工具必须幂等，模型可能重复调用

**安全（重点，且最容易被忽略）**
- **Prompt Injection**：知识库里的文档、网页抓取的内容、用户上传的文件都可能藏"忽略之前的指令"。防御：把外部内容明确标注为数据而非指令、工具做权限校验（不信任模型传来的 userId，用会话里的）、输出做二次校验。
- **权限**：检索层强制租户/ACL 过滤；工具层做和普通 API 一样的鉴权。
- **数据泄露**：敏感字段脱敏后再进 prompt；日志里 prompt 要脱敏。
- **输出安全**：生成的 SQL/代码不要直接执行；生成的链接不要直接跳转。

**可观测**
- 每次调用记录：traceId、prompt 版本、模型、token 明细、耗时、命中片段 id、用户反馈（👍/👎）
- Prompt 版本化（当代码管，走 Git + 灰度）
- 线上 badcase 自动回流到黄金集，形成闭环

**本阶段产出**：把 Project 1 / 2 补全上述能力，并写一份《我们的 LLM 应用上线 checklist》。

---

### 阶段六（第 13~14 周）：补基础 + 选方向

前面全是"自顶向下"，现在按需补"自底向上"的原理，只补和调优相关的部分：

- **必补**：Transformer / Attention 的直觉理解（为什么有上下文长度限制、为什么"中间被忽略"）、Tokenizer（为什么中文更费 token）、温度/采样的含义、Embedding 与向量相似度（cosine）、ANN 索引原理（HNSW 的 ef / M 参数怎么影响召回和延迟）。
- **选修**（挑一个深入）：
  - **检索方向**：Embedding 微调、稀疏向量（SPLADE）、GraphRAG、多模态 RAG
  - **Agent 方向**：多 Agent 协作与任务分解、长时程任务、沙箱执行
  - **推理与部署**：vLLM / Ollama 本地部署、量化、私有化方案（很多国内企业有强需求）
  - **微调方向**：LoRA / SFT —— 但请注意：**90% 你以为需要微调的场景，其实是 RAG 没做好或 prompt 没写好**，把它放在最后是有意的。

---

## 3. 实战项目清单（简历可写）

| # | 项目 | 阶段 | 核心卖点 |
|---|---|---|---|
| 0 | LLM 网关服务 | 一 | 流式、结构化输出、缓存、重试、计量 |
| 1 | 企业知识库问答 | 二~三 | 混合检索 + rerank + 引用 + **完整 eval 体系与对比数据** |
| 2 | 研发/运维助手 Agent | 四~五 | 手写 tool use 循环、MCP Server、成本与安全护栏 |

**面试加分点排序**：有 eval 数据 > 有成本优化数据 > 有安全设计 > 用了多少新框架。
能说出"我们把 Recall@5 从 0.62 优化到 0.85，单次问答成本从 0.08 降到 0.02"的人，比能背出十个框架名字的人值钱得多。

---

## 4. 每周时间分配建议

| | 内容 | 时长 |
|---|---|---|
| 工作日 × 5 | 看文档 / 读源码 / 小实验 | 1h/天 |
| 周末 | 写代码，推进当周产出 | 4~5h |
| 每周日 | 写一篇学习笔记（哪怕只有 300 字），记录"我原以为…实际上…" | 0.5h |

**强烈建议**：把笔记提交到这个仓库，形成可回溯的记录。踩过的坑三个月后你一定会忘。

---

## 5. 学习资源

**官方文档（第一优先级，中文资料普遍滞后且有错）**
- Anthropic 官方文档：https://docs.anthropic.com （Tool use、Prompt caching、Agent 相关的工程文章质量很高）
- Anthropic 工程博客《Building effective agents》《Contextual Retrieval》—— 必读，且读两遍
- Model Context Protocol：https://modelcontextprotocol.io
- Spring AI：https://docs.spring.io/spring-ai/reference/
- LangChain4j：https://docs.langchain4j.dev/

**理解原理**
- 《The Illustrated Transformer》（图解，不需要数学基础）
- Andrej Karpathy 的 LLM 系列视频（想深入原理时看）

**跟进前沿（每周 30 分钟足够）**
- Anthropic / OpenAI 官方博客
- Hugging Face 博客与 MTEB 榜单（挑 embedding / rerank 模型时看）

**避坑提示**：AI 领域 API 变化极快，训练数据里的写法（包括各种教程和你问到的 AI 给的答案）经常已经过时。**任何 API 用法以官方文档当前版本为准**，尤其是模型 ID、参数名、beta header 这类细节。

---

## 6. 任务卡与进度

本文档只回答"**为什么这么学**"。具体"**这周干什么、怎么算学会了**"在阶段任务卡里——每张卡包含拆解到 1~4 小时粒度的任务清单，以及一张**过关卡片**（硬指标 + 演示题 + 口试题）。

| 阶段 | 任务卡 | 周次 | 过关卡片的核心硬指标 |
|---|---|---|---|
| 一 · LLM API 基本功 | [stage-1](../stages/stage-1-llm-api.md) | 1~2 | 结构化输出 50 次 0 失败；缓存命中 ≥ 90% |
| 二 · RAG 全链路 | [stage-2](../stages/stage-2-rag.md) | 3~5 | 冷门事实召回 8/10；正确拒答 9/10；权限零泄露 |
| 三 · 评估体系 | [stage-3](../stages/stage-3-eval.md) | 6~7 | 黄金集 ≥ 50 条；重复跑波动 < 3%；Recall@5 提升 ≥ 5 点 |
| 四 · Agent | [stage-4](../stages/stage-4-agent.md) | 8~10 | 手写循环跑通；三道护栏可触发；写操作 100% 拦截 |
| 五 · 生产化 | [stage-5](../stages/stage-5-production.md) | 11~12 | 降本 ≥ 40% 且 eval 掉分 < 2%；红队 0 攻破 |
| 六 · 补基础 + 选方向 | [stage-6](../stages/stage-6-fundamentals.md) | 13~14 | 5 个概念能结合亲历现象自述；HNSW 参数曲线 |

**进度记录**统一写在 [PROGRESS.md](../PROGRESS.md)，仓库目录约定见 [README.md](../README.md)。

**关于过关标准**：硬指标是可量化、跑一下就知道过没过的；口试题要合上电脑作答，防止复制粘贴式通关。没过就记录卡在哪一条、下周优先补——**带着漏洞往前冲的代价，在第三、第五阶段会加倍还回来**（阶段三的 eval 没建，阶段五的成本优化就无从验证）。

---

## 7. 常见误区（提前避坑）

1. **一上来就学微调**——绝大多数业务问题用 RAG + prompt 就能解决，微调是最后手段。
2. **不做 eval 就开始调优**——你会陷入"改一版感觉好了，过两天又觉得不行"的循环。
3. **盲目上 Agent**——能用固定流程编排的，不要让模型自己决定流程。Agent 的调试成本是 workflow 的 5 倍以上。
4. **只用向量检索**——必须混合关键词检索。
5. **在 prompt 里做权限控制**——权限必须在代码/检索层做，模型不是安全边界。
6. **忽视 chunk 和解析质量**——垃圾进垃圾出，再强的模型也救不了切烂的文档。
7. **追框架不追原理**——框架半年换一茬，"检索—重排—上下文组装—评估"的方法论是稳定的。
8. **完全相信 AI 生成的 API 用法**——模型对自己最新的 API 也可能记错，一律以官方文档为准。
9. **为了"用上 Go"而用 Go**——文档解析、embedding 推理这类活 Go 生态确实薄，硬写会耗在无关的地方。按职责切分服务（Go 做在线编排，Python/Java 做离线数据），这本来就是更好的架构。
