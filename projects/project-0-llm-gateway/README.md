# Project 0 · LLM 网关服务（Java）

> 对应 [ROADMAP](../../ROADMAP.md) 任务 **#01–#07**，任务卡见 [阶段一](../../stages/stage-1-llm-api.md)。
> **当前骨架覆盖 #01 和 #02**，#03–#07 的接入点已在代码里用 `#0X` 标出。

一句话：把大模型 API 包装成一个你熟悉的 Spring Boot 服务，顺便把"它和普通 RPC 有什么不一样"搞清楚。

**接的是 DeepSeek**，走 OpenAI 兼容协议（`POST /chat/completions`）。

---

## 快速开始

```bash
export LLM_GATEWAY_API_KEY=sk-...     # 项目专用变量名，别写进配置文件
mvn spring-boot:run                    # 服务起在 8090
```

**为什么不用 `DEEPSEEK_API_KEY` 这种厂商变量名**：避免和本机其他工具撞名，
换供应商时也不用改代码。密钥由 `app.llm.api-key: ${LLM_GATEWAY_API_KEY:}` 注入。

`app.llm.auth` 决定密钥放哪个头：`bearer` → `Authorization: Bearer`（OpenAI 兼容接口都用这个）；
`api-key` → `x-api-key`（少数网关/中转站用）。

默认用**内存**存对话历史，不需要装 Redis，第一天就能跑起来。

### 跑 #01：第一个请求

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.demo.first-request=true
```

启动时真实调用一次 API，日志打印回复、`finish_reason` 和 token 用量。
**默认关闭**——每次启动都发请求 = 每次启动都花钱。

### 跑 #02：多轮对话

```bash
curl -X POST localhost:8090/chat -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","message":"Spring 的 @Transactional 默认传播行为是什么？"}'

# 第二轮故意用"它"指代，验证历史真的带上了
curl -X POST localhost:8090/chat -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","message":"那它有哪些坑？"}'

curl -X DELETE localhost:8090/chat/s1    # 清空会话
```

**第二轮答得上来 = 历史管理是对的；答非所问 = 你漏存了 assistant 的回复。**

### 换别的模型服务

因为走的是 OpenAI 兼容协议，**换供应商只改两行配置**，代码一行不动：

```yaml
app:
  llm:
    base-url: https://api.deepseek.com     # 通义 / Kimi / 本地 vLLM、Ollama、自建中转站都行
    model: deepseek-v4-pro
    auth: bearer                           # 个别网关要 x-api-key，改成 api-key
```

⚠️ **base-url 不要带 `/v1`**——路径由代码拼（本项目拼 `/chat/completions`）。
如果你的中转站路径前缀是 `/v1`，把 base-url 写成 `https://xxx/v1` 即可。

⚠️ **模型名会变**。旧别名 `deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 下线，
当前是 `deepseek-v4-pro` / `deepseek-v4-flash`（便宜档位）。
**以 DeepSeek 官方文档当前版本为准**，不要照抄网上的老教程——也不要照抄这一行。

### 切到 Redis

```yaml
app:
  conversation:
    store: redis
management:
  health:
    redis:
      enabled: true    # 用上 Redis 后把健康检查打开
```

---

## 代码结构

```
config/LlmProperties      base-url / api-key / auth / 模型 / maxTokens / 系统提示 / 超时
llm/
  LlmClientConfig         RestClient Bean（超时、鉴权头、HTTP 版本）
  LlmClient               POST /chat/completions 的最小封装
  dto/                    ChatCompletionRequest / Response / ChatMessage / Usage
chat/
  ChatController          POST /chat、DELETE /chat/{sessionId}
  ChatService             #01 #02 主逻辑
  dto/                    ChatRequest / ChatResponse / TokenUsage
  store/                  ConversationStore + 内存 / Redis 两种实现
demo/FirstRequestRunner   #01 演示，默认关闭
```

### 为什么不用 SDK / 框架

阶段一的目标就是**看清协议本身**——messages 数组长什么样、usage 里有哪些字段、`finish_reason` 有哪些取值。SDK 恰好会把这些全藏起来。

副产品是不绑定任何厂商：`RestClient` + 几个 record，换任何 OpenAI 兼容服务只改配置。
等阶段二需要 VectorStore 抽象时，再引 Spring AI 不迟。

---

## 这个骨架想让你注意的五件事

### 1. API 是无状态的，"多轮"是你自己拼出来的

服务端不记得上一轮。每次请求都要把**完整历史**重新发过去——`ConversationStore` 存在的全部意义就是这个。

直接推论：**token 消耗随轮数线性增长**。所以才需要 #05 的缓存和阶段四的上下文管理。

### 2. 漏存 assistant 回复是 #02 的头号 bug

```java
store.append(sessionId, Turn.user(userMessage));
store.append(sessionId, Turn.assistant(reply));   // ← 少了这行，模型每轮都"失忆"
```

症状很迷惑：不报错、回答也通顺，就是完全不记得上一轮。

### 3. DeepSeek 的上下文缓存是**自动**的

和 Anthropic 要手动设置"缓存断点"不同，**DeepSeek 的缓存自动生效**，没有开关可开。
命中与否只取决于一件事：**你的请求前缀是否和上一次一致**（且长度达到阈值，约 1K token 以上）。

所以 #05 的功课不是"怎么开缓存"，而是**"怎么让前缀保持稳定"**——
往系统提示里塞时间戳、随机 id、顺序不固定的 JSON，命中率立刻归零。**这个原则各家都一样。**

观测：响应里的 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`，
本项目已算好 `cacheHitRatio` 直接返回。

### 4. 没有内置重试，这是故意的

官方 SDK 通常自带重试，`RestClient` 没有。**这不是缺陷，是 #06 留给你的活**——
自己写一遍，你才会真正想清楚"哪些错误该重试、退避多久、总耗时上限是多少"。

### 5. JDK 的 HttpClient 默认走 HTTP/2

走 https 时靠 ALPN 协商没问题，但**明文 http 下它会尝试 h2c 升级**，
很多简单服务端（本地 mock、Ollama、部分自建网关）读不懂，
表现为莫名其妙的 `header parser received no bytes` / `EOFException`。

代码里已固定 HTTP/1.1。**这个坑我在搭骨架时真踩到了**，留个注释省你半小时。

---

## 下一步（按序号往下做）

| # | 任务 | 从哪下手 |
|---|---|---|
| **03** | 流式输出 | 请求体加 `stream=true`，按 SSE 逐行解析 `data: {...}`，遇 `data: [DONE]` 结束；Controller 侧用 `SseEmitter`。**务必在客户端断开时关掉上游流**，否则还在烧 token |
| **04** | 结构化输出 | 请求体加 `response_format`。⚠️ DeepSeek 的 JSON 模式和 OpenAI 的严格 schema 能力不完全一样，**先查当前文档确认支持到哪一步**，再决定要不要加一层校验兜底 |
| **05** | 缓存 | 把稳定内容固定在最前，观测 `cacheHitRatio`。**再故意往系统提示里塞个时间戳，看命中率归零**——这个实验必须亲手做 |
| **06** | 错误处理 + 重试 | `RestClient` 的 `.onStatus(...)` 按状态码分类；429 退避重试；注意总耗时 = 超时 × (重试数+1) |
| **07** | 计量与成本 | `TokenUsage` 打到 Micrometer；缓存命中和未命中要**分开计价**才算得准 |

完成后对照 [阶段一过关卡片](../../stages/stage-1-llm-api.md#-过关卡片) 自查，再做 Go 版（**#08–#12**，见 [Go 轨道](../../tracks/go.md)）。

---

## 已验证

用一个模拟上游（本地 mock，返回真实形状的 OpenAI 兼容响应）跑通了全链路：

- `mvn package` 通过（Java 21 + Spring Boot 3.5.0，**零第三方 LLM 依赖**）
- `/actuator/health` → `{"status":"UP"}`
- 两轮对话实测：第一轮上游收到 **2 条**消息（system + user），
  第二轮收到 **4 条**（system + user + assistant + user）—— **历史确实带上了**
- 请求体字段名正确：`max_tokens` 是 snake_case，未设置的 `temperature` 不会发出去
- `Authorization: Bearer <key>` 正确携带
- usage 解析正确，**含 DeepSeek 特有的 `prompt_cache_hit_tokens`**，
  `cacheHitRatio` 计算无误（96 / (96+32) = 0.75）

⚠️ **未对真实 DeepSeek 接口验证**：本环境网络策略屏蔽了 `api.deepseek.com`。
配上你自己的 Key 应该可直接跑；如果报错，先确认模型名是否仍然有效。

### 一个刻意留下的粗糙点

上游报错时，客户端拿到的是光秃秃的 **HTTP 500**，看不出哪错了。
这不是 bug，是 **#06** 留给你的活：加上分类处理后，鉴权失败该返回 401，限流该返回 429。
**先亲眼看看"没有错误处理"有多难排查，再去写它。**
