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
| **LLM SDK** | **官方 `com.anthropic:anthropic-java`** | 直连 API，功能最全、更新最快。tool use / streaming / 缓存 / 结构化输出都有一等支持 |
| **应用框架** | **Spring AI**（推荐）或 **LangChain4j** | Spring AI 与 Spring Boot 集成度最好（自动配置、`ChatClient`、`VectorStore` 抽象、Advisor 链）；LangChain4j 抽象更贴近 LangChain，社区示例多 |
| **向量存储** | 入门 **pgvector**；已有 ES 就用 **Elasticsearch / OpenSearch**（dense_vector + BM25）；规模大再上 **Milvus / Qdrant** | 不要一上来就引专用向量库。pgvector 一个库解决元数据过滤 + 向量检索 + 事务，运维成本最低 |
| **关键词检索** | Elasticsearch BM25 / PostgreSQL 全文检索 | 混合检索必备 |
| **Embedding** | Voyage / Cohere / 云厂商服务；本地可用 **bge-m3**、**BGE-large-zh**（ONNX Runtime 或独立 Python 服务暴露 HTTP） | ⚠️ Anthropic 不提供 embedding 接口，需单独选型 |
| **Rerank** | bge-reranker-v2-m3（本地）或云端 rerank 服务 | 性价比最高的单点提升 |
| **文档解析** | Apache Tika、PDFBox、docx4j；复杂 PDF 用版面解析服务（MinerU / 商用 OCR） | **Java 在这一层比 Go 强很多**，离线数据处理交给 Java |
| **序列化** | Jackson | 配合结构化输出反序列化成强类型 DTO |
| **可观测** | Micrometer + Langfuse / OpenTelemetry | |
| **异步** | `CompletableFuture` 或 WebFlux | 并行工具调用、多路召回用得上 |

### 模型 ID 的注意点

```java
MessageCreateParams.builder()
    .model("claude-opus-5")   // 用 String 重载，对任何模型 id 都有效
    .maxTokens(16000L)
```

SDK 里有 `Model.*` 的类型化常量，但**常量更新会滞后于模型发布**。用 `.model(String)` 重载最稳妥，别因为"有常量"而选模型。

---

## 3. 各阶段的 Java 实现要点

### 阶段一 · API 基本功

```java
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

AnthropicClient client = AnthropicOkHttpClient.fromEnv();  // 读 ANTHROPIC_API_KEY

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

**包结构要记住一点**：`client.messages()` 用 `com.anthropic.models.messages.*`，`client.beta().messages()` 用 `com.anthropic.models.beta.messages.*`。
**两个包里都有 `MessageCreateParams`**——import 错了会得到很困惑的编译错误。

**流式**：SDK 返回 `StreamResponse`，Spring 侧用 `SseEmitter`（MVC）或 `Flux<ServerSentEvent>`（WebFlux）暴露。
⚠️ **客户端断开时要关掉上游流**，否则白烧 token。MVC 下注册 `SseEmitter.onCompletion` / `onTimeout` 回调；WebFlux 下用 `doOnCancel`。
（这件事在 Go 里是 `ctx` 自动完成的，Java 需要手动接线——可以对照体会一下。）

**结构化输出**：用 structured outputs 或工具的 `.strict(true)`，配合 `.putAdditionalProperty("additionalProperties", JsonValue.from(false))`，然后 Jackson 反序列化成强类型 DTO。
**这是 Java 接入 LLM 的关键点**：强类型语言最怕"有时候返回的不是 JSON"，别在 prompt 里跪求。

**错误处理**：按最具体优先写 catch 链：
`NotFoundException` → `RateLimitException` → `AnthropicServiceException` → 连接异常。**不要一把 `catch (Exception)`**。
⚠️ SDK 自带重试（默认 2 次），**总耗时 = 超时 × (重试次数 + 1)**，超时配置要按这个算。Java SDK 对流式请求会自动放大默认超时，非流式是 30s~10min 区间。

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
    Message resp = client.messages().create(params);
    messages.add(resp.toParam());

    if (resp.stopReason() != StopReason.TOOL_USE) break;

    List<ContentBlockParam> results = new ArrayList<>();
    for (ContentBlock block : resp.content()) {
        block.toolUse().ifPresent(tu -> {
            // 执行工具，失败时 isError = true 回传，不要抛异常中断循环
            results.add(toToolResult(tu, execute(tu)));
        });
    }
    // ⚠️ 所有 tool_result 放在同一条 user 消息里
    messages.add(MessageParam.builder().role(USER).content(results).build());
}
```

**并行工具调用**：`CompletableFuture` + 自定义线程池，`allOf().join()` 后按原顺序组装结果。
比起 Go 的 `errgroup` 会啰嗦一些，但配合 Spring 的线程池管理和 `@Async` 也够用。

**工具定义**：`Tool` + `Tool.InputSchema`，用 `.strict(true)` + `additionalProperties: false`。
Java 需要**手写 JSON Schema**（Go 可以从 struct tag 自动生成），可以考虑用 `victools/jsonschema-generator` 从 DTO 类生成，省掉手写。

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
| 编译报找不到 `MessageCreateParams` 的某个方法 | import 错了包：`messages` 和 `beta.messages` 两个包都有同名类 |
| 客户端断开后 token 还在涨 | `SseEmitter` 的 `onCompletion` / `onTimeout` 没注册，上游流没关 |
| 偶发超时，但单次请求明明不慢 | SDK 自带重试，总耗时 = 超时 × (重试数+1)。超时和重试要一起配 |
| 并行任务把服务拖垮 | 用了 `ForkJoinPool.commonPool()`。配独立线程池 |
| 异步链路里 traceId 丢了 | MDC 没跨线程传递，要配 `TaskDecorator` |
| Batch API 结果对不上 | 按位置取结果了。必须用 `custom_id` 关联 |
| 结构化输出偶尔反序列化失败 | 没开 `strict`，或 schema 里缺 `required` / `additionalProperties: false` |
| 大文档 embedding 报错或效果差 | 超过模型最大输入长度被静默截断 |
