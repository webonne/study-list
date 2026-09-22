# Java 轨道

> 面向：有 5 年 Java 经验、Spring Boot / 微服务熟练的工程师。
> 定位：**主力语言**。生态最全，业务落地首选。

配合 [阶段任务卡](../stages/) 使用。任务卡讲"做什么"，本文讲"用 Java 怎么做、会踩什么坑"。
和 Go 轨道的分工见 [tracks/README.md](README.md)。

---

## 1. 为什么主力用 Java

- **无缝融进现有体系**：事务、连接池、监控、鉴权、灰度、CI/CD 全部复用，不用为了 AI 单独搭一套。
- **生态成熟**：Spring AI / LangChain4j 已经足够用；文档解析（Tika、PDFBox）、Elasticsearch 客户端、调度、批处理都是现成的。
- **团队可维护**：你写完之后同事接得住。这在企业里比"技术先进"重要得多。

**Java 的短板**：高并发长连接（SSE）和"级联取消"的表达不如 Go 自然，Agent 运行时和 MCP Server 这类场景可以考虑 Go——见 [go.md](go.md)。

---

## 2. Java 侧技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| **LLM 调用** | **Spring `RestClient` 直调 OpenAI 兼容接口**（DeepSeek）。零第三方 LLM 依赖 | 阶段一的目标是看清协议本身，SDK 恰好会把它藏起来。副产品：换任何兼容服务只改配置 |
| **应用框架** | **Spring AI**（推荐）或 **LangChain4j** | Spring AI 与 Spring Boot 集成度最好（自动配置、`ChatClient`、`VectorStore` 抽象、Advisor 链）；LangChain4j 抽象更贴近 LangChain，社区示例多 |
| **向量存储** | 入门 **pgvector**；已有 ES 就用 **Elasticsearch / OpenSearch**（dense_vector + BM25）；规模大再上 **Milvus / Qdrant** | 不要一上来就引专用向量库。pgvector 一个库解决元数据过滤 + 向量检索 + 事务，运维成本最低 |
| **关键词检索** | Elasticsearch BM25 / PostgreSQL 全文检索 | 混合检索必备 |
| **Embedding** | 云厂商服务（DeepSeek 之外单独选）；本地可用 **bge-m3**、**BGE-large-zh**（ONNX Runtime 或独立 Python 服务暴露 HTTP） | ⚠️ 对话模型接口不含 embedding，需单独选型 |
| **Rerank** | bge-reranker-v2-m3（本地）或云端 rerank 服务 | 性价比最高的单点提升 |
| **文档解析** | Apache Tika、PDFBox、docx4j；复杂 PDF 用版面解析服务（MinerU / 商用 OCR） | **Java 在这一层比 Go 强很多**，离线数据处理交给 Java |
| **序列化** | Jackson | 配合结构化输出反序列化成强类型 DTO |
| **可观测** | Micrometer + Langfuse / OpenTelemetry | |
| **异步** | `CompletableFuture` 或 WebFlux | 并行工具调用、多路召回用得上 |

### 模型 id 的注意点

```yaml
app:
  llm:
    base-url: https://api.deepseek.com
    model: deepseek-v4-pro      # 便宜档位：deepseek-v4-flash
```

⚠️ **模型名会变**。旧别名 `deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 下线，
网上大量教程还在用。**做成配置项，别写死在代码里**，报 "model not found" 时先查官方文档。

---

## 3. 各阶段的 Java 实现要点

### 阶段一 · API 基本功

完整可运行代码见 [`projects/project-0-llm-gateway/`](../projects/project-0-llm-gateway/)，这里只列要点。

```java
RestClient client = RestClient.builder()
        .requestFactory(jdkFactory)                      // 超时配在这里
        .baseUrl("https://api.deepseek.com")
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .build();

var resp = client.post().uri("/chat/completions")
        .body(request)
        .retrieve()
        .body(ChatCompletionResponse.class);
```

**DTO 上两个注解不能少**：
- `@JsonInclude(NON_NULL)` 在请求上——没设的字段别发出去，发个 `"temperature": null` 有些网关直接 400
- `@JsonIgnoreProperties(ignoreUnknown = true)` 在响应上——**厂商会不定期加字段**
  （reasoner 系列会多返回 `reasoning_content`），不加这个，对方一升级你就 500

**流式**：请求体加 `stream=true`，响应是 SSE——逐行读 `data: {...}`，遇到 `data: [DONE]` 结束。
Spring 侧用 `SseEmitter`（MVC）或 `Flux<ServerSentEvent>`（WebFlux）暴露。
⚠️ **客户端断开时要关掉上游流**，否则白烧 token。MVC 下注册 `SseEmitter.onCompletion` / `onTimeout` 回调；WebFlux 下用 `doOnCancel`。
（这件事在 Go 里是 `ctx` 自动完成的，Java 需要手动接线——可以对照体会一下。）

**结构化输出**：请求体加 `response_format`。⚠️ **各家对"严格 JSON Schema"的支持程度不一样**，
DeepSeek 的 JSON 模式和 OpenAI 的 structured outputs 不能划等号——
先查当前文档确认支持到哪一步，不够严格就自己加一层校验兜底（校验失败则重试一次）。
**这是 Java 接入 LLM 的关键点**：强类型语言最怕"有时候返回的不是 JSON"，别在 prompt 里跪求。

**错误处理**：用 `RestClient` 的 `.onStatus(...)` 按状态码分类：401 鉴权、429 限流、400 参数、5xx 上游。
**不要一把 `catch (Exception)`**，那样线上排查等于瞎猜。

⚠️ **`RestClient` 没有内置重试**——这正是 #06 的功课。自己写退避时记住：
**总耗时 = 超时 × (重试次数 + 1)**，只调小超时而不管重试次数，并不会让请求更快失败。

⚠️ **JDK 的 `HttpClient` 默认走 HTTP/2**。走 https 没问题，但明文 http 下会尝试 h2c 升级，
本地 mock、Ollama、部分自建网关读不懂，报莫名其妙的 `header parser received no bytes`。
固定 `HttpClient.Version.HTTP_1_1` 最省心。

---

### 阶段二 · RAG

**Java 在这一阶段是最强的**，离线数据处理（解析、切块、批量 embedding、入库）全交给 Java。

- **文档解析**：Tika 统一入口，PDF 走 PDFBox。保留标题层级和章节信息，后面切块和引用都要用
- **切块**：Parent-Child 用一张表两个字段（`chunk_id` / `parent_id`）就能实现，别过度设计
- **增量索引**：content hash 判断变更，Spring 的 `@Transactional` 保证"删旧 chunk + 插新 chunk"的原子性——**这是 Java 相对脚本语言的优势**
- **并行多路召回**：`CompletableFuture.allOf(vectorSearch, bm25Search).join()`
  ⚠️ 注意配一个独立线程池，别用默认的 `ForkJoinPool.commonPool()`（会和其他任务抢资源）
- **权限过滤**：在 Repository 层强制拼 `WHERE tenant_id = ?`，用 MyBatis 拦截器或 JPA 的 `@Filter` 兜底，**保证上层忘了传也不会漏**

---

### 阶段三 · Eval

- 做成 **JUnit 测试** 或 Maven 插件，一条命令跑完，天然融进 CI
- 黄金集用 JSON/YAML 存，Jackson 读取
- 并发跑题目用固定大小线程池控制并发度（别把自己的 API 限流打爆）
- 结果输出 CSV，用 Excel 或简单脚本做对比

**Java 在这里的优势是"能进 CI"**：把 eval 做成构建流程的一部分，改 prompt 的 PR 自动跑分，分数掉了就红灯。这是团队协作时最实用的形态。

---

### 阶段四 · Agent

**手写循环**（T4.1，仍然不许用框架）：

```java
for (int turn = 0; turn < maxTurns; turn++) {
    var resp = llmClient.complete(messages, tools);
    var choice = resp.choices().get(0);
    messages.add(choice.message());                       // assistant 消息（含 tool_calls）

    if (!"tool_calls".equals(choice.finishReason())) break;

    // ⚠️ OpenAI 兼容协议里，每个 tool_call 对应一条独立的 role=tool 消息，
    //    且必须带上对应的 tool_call_id。看别家文档时注意：不同厂商的协议在这里不一样
    for (var call : choice.message().toolCalls()) {
        String result = execute(call);                    // 失败也要回传，别抛异常中断循环
        messages.add(ChatMessage.tool(call.id(), result));
    }
}
```

**并行工具调用**：`CompletableFuture` + 自定义线程池，`allOf().join()` 后按原顺序组装结果。
比起 Go 的 `errgroup` 会啰嗦一些，但配合 Spring 的线程池管理和 `@Async` 也够用。

**工具定义**：OpenAI 兼容格式的 `tools` 数组，每个工具是 `{type:"function", function:{name, description, parameters}}`，
`parameters` 就是一份 JSON Schema。
Java 手写 schema 很啰嗦，可以用 `victools/jsonschema-generator` 从 DTO 类生成。

**护栏**：
- 轮数上限、会话超时用 Spring 的 `@Timed` / 自己计时
- token 预算：累加 `usage`，超了抛出终止
- 危险操作确认：工具执行前查一张"需确认操作"白名单，命中则返回待确认状态

⚠️ **Java 的痛点在"级联取消"**：用户断开连接时，要让进行中的 LLM 流、检索、rerank 全部停下来，Java 里需要手动传递取消信号（`CompletableFuture.cancel()` 或自定义 flag）。
**这正是 Go 的 `context` 天生解决的问题**——如果你的 Agent 是长会话、高并发场景，可以认真考虑 Go 版本（见 [go.md](go.md) 阶段四）。

**MCP Server**：Spring AI 已集成 MCP，写起来不难。
但如果目标是"交付给团队用"，**Go 的单二进制更省事**（不需要对方装 JRE）——这是选型时值得权衡的点。

---

### 阶段五 · 生产化

Java 侧的优势区，你的经验直接迁移：

| 项 | 做法 |
|---|---|
| **限流 / 熔断** | Resilience4j（限流、熔断、重试、降级一站式） |
| **降级** | 主模型不可用切备用模型。⚠️ 切模型会让 prompt 缓存失效，降级期成本上升，要单独告警 |
| **链路追踪** | Micrometer Tracing + OpenTelemetry。⚠️ **MDC 在线程池和异步链路里会丢**，要手动包装 `TaskDecorator` |
| **成本看板** | Micrometer 打点 + Prometheus + Grafana |
| **配置热更新** | Prompt 当配置管，走配置中心，支持灰度和秒级回滚 |
| **Batch API** | 批量任务异步跑，约 5 折。⚠️ **结果乱序返回**，必须用 `custom_id` 关联，不能按位置取 |

---

### 阶段六 · 基础

- Java SDK 的 builder 模式和联合类型建模值得读一读，对照 Go SDK 的 `option.RequestOption` 模式看，能看出两种语言的设计哲学差异
- HNSW 参数实验用 JMH 做基准测试

---

## 4. Java + LLM 常见卡点

| 症状 | 大概率原因 |
|---|---|
| 响应反序列化报 unknown field | 没加 `@JsonIgnoreProperties(ignoreUnknown = true)`，厂商加字段就炸 |
| 请求报 400 但参数看着没问题 | 发了 `null` 字段过去。请求 DTO 要加 `@JsonInclude(NON_NULL)` |
| 本地 mock / Ollama 调不通，报 header parser 错误 | JDK HttpClient 默认 HTTP/2，明文下 h2c 升级失败。固定 HTTP/1.1 |
| 客户端断开后 token 还在涨 | `SseEmitter` 的 `onCompletion` / `onTimeout` 没注册，上游流没关 |
| 偶发超时，但单次请求明明不慢 | 自写的重试叠加了超时：总耗时 = 超时 × (重试数+1)。两者要一起配 |
| 并行任务把服务拖垮 | 用了 `ForkJoinPool.commonPool()`。配独立线程池 |
| 异步链路里 traceId 丢了 | MDC 没跨线程传递，要配 `TaskDecorator` |
| Batch API 结果对不上 | 按位置取结果了。必须用 `custom_id` 关联 |
| 结构化输出偶尔反序列化失败 | `response_format` 没生效，或该服务的 JSON 模式本就不保证严格符合 schema——加一层校验兜底 |
| 大文档 embedding 报错或效果差 | 超过模型最大输入长度被静默截断 |
