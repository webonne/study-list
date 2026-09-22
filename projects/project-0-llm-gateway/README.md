# Project 0 · LLM 网关服务（Java）

> 对应 [ROADMAP](../../ROADMAP.md) 任务 **#01–#07**，任务卡见 [阶段一](../../stages/stage-1-llm-api.md)。
> **当前骨架覆盖 #01 和 #02**，#03–#07 的接入点已在代码里用 `TODO #0X` 标出。

一句话：把大模型 API 包装成一个你熟悉的 Spring Boot 服务，顺便把"它和普通 RPC 有什么不一样"搞清楚。

---

## 快速开始

```bash
export ANTHROPIC_API_KEY=sk-ant-...        # 唯一必需的环境变量，别写进配置文件
mvn spring-boot:run
```

默认用**内存**存对话历史，不需要装 Redis，第一天就能跑起来。

### 跑 #01：第一个请求

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.demo.first-request=true
```

启动时会真实调用一次 API，日志里打印回复、`stopReason` 和 token 用量。
**默认是关的**——每次启动都发请求 = 每次启动都花钱。

### 跑 #02：多轮对话

```bash
# 第一轮
curl -X POST localhost:8080/chat -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","message":"Spring 的 @Transactional 默认传播行为是什么？"}'

# 第二轮：故意用"它"来指代，验证历史真的带上了
curl -X POST localhost:8080/chat -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","message":"那它有哪些坑？"}'

# 清空会话
curl -X DELETE localhost:8080/chat/s1
```

**第二轮答得上来 = 历史管理是对的；答非所问 = 你漏存了 assistant 的回复。**

### 切到 Redis

```yaml
app:
  conversation:
    store: redis     # 默认 memory
management:
  health:
    redis:
      enabled: true  # 用上 Redis 后把健康检查打开
```

---

## 代码结构

```
config/
  LlmProperties          模型、maxTokens、系统提示、历史长度、TTL
  AnthropicClientConfig  SDK 客户端 Bean（超时 + 重试）
chat/
  ChatController         POST /chat、DELETE /chat/{sessionId}
  ChatService            #01 #02 的主逻辑，后续任务在这里接着长
  dto/                   ChatRequest / ChatResponse / TokenUsage
  store/
    ConversationStore    历史存储接口
    InMemoryConversationStore   默认，免装 Redis
    RedisConversationStore      app.conversation.store=redis 时启用
demo/
  FirstRequestRunner     #01 演示，默认关闭
```

---

## 这个骨架想让你注意的四件事

### 1. API 是无状态的，"多轮"是你自己拼出来的

服务端不记得上一轮。每次请求都要把**完整历史**重新发过去——`ConversationStore` 存在的全部意义就是这个。

直接推论：**token 消耗随轮数线性增长**。所以才需要 #05 的 prompt caching 和阶段四的上下文管理。

### 2. 漏存 assistant 回复是 #02 的头号 bug

```java
store.append(sessionId, Turn.user(userMessage));
store.append(sessionId, Turn.assistant(reply));   // ← 少了这行，模型每轮都"失忆"
```

症状很迷惑：不报错，回答也通顺，只是完全不记得上一轮。

### 3. 响应的 content 是**块列表**，不是字符串

```java
response.content().stream().flatMap(block -> block.text().stream())
```

除了文本块，还可能有思考块、工具调用块。阶段四做 tool use 时，就是在这里分出 `toolUse()` 分支——
**现在多花两分钟看懂这个结构，第 27 号任务能省你两小时。**

### 4. 超时和重试必须一起配

SDK 默认重试 2 次，最坏情况总耗时 ≈ `timeout × (重试次数 + 1)`。
只调小 timeout 而不管重试次数，并不会让请求更快失败——这个坑在压测时才会暴露。

---

## 下一步（按序号往下做）

| # | 任务 | 从哪下手 |
|---|---|---|
| **03** | 流式输出 | `ChatController` 加 `/chat/stream`；`client.messages().createStreaming(params)`。**务必注册 `onCompletion`/`onTimeout` 关掉上游流**，否则客户端断开后还在烧 token |
| **04** | 结构化输出 | `MessageCreateParams` 加 `outputConfig`，回复反序列化成 DTO。跑 50 次统计失败率 |
| **05** | Prompt Caching | `.system(String)` 换成 `.systemOfTextBlockParams(...)` 并设 `CacheControlEphemeral`。验证 `usage().cacheReadInputTokens() > 0`。**再故意往系统提示里塞个时间戳，看命中率归零** |
| **06** | 错误处理 | 按最具体优先写 catch 链：`NotFoundException` → `RateLimitException` → `AnthropicServiceException` → 连接异常 |
| **07** | 计量与成本 | `TokenUsage` 打到 Micrometer；用 `client.messages().countTokens(...)` 预估长输入 |

完成后对照 [阶段一过关卡片](../../stages/stage-1-llm-api.md#-过关卡片) 自查，然后去做 Go 版（**#08–#12**，见 [Go 轨道](../../tracks/go.md)）。

---

## 已验证

- `mvn compile` / `mvn package` 通过（Java 21 + Spring Boot 3.5.0 + anthropic-java 2.34.0）
- 用**假 API Key** 启动，Spring 上下文加载成功，`/actuator/health` 返回 `{"status":"UP"}`
- `POST /chat` 打通了整条链路（Controller → Service → Store → SDK → HTTP），
  用假 key 时拿到 `com.anthropic.errors.UnauthorizedException`（401）——
  **这正是期望结果**：说明请求真的发出去了，只是 key 不对
- ⚠️ **真实回复未验证**（本环境没有可用的 Key）。换成你自己的 Key 就能拿到正常回复

### 一个刻意留下的粗糙点

现在用假 Key 调 `/chat`，客户端拿到的是一个光秃秃的 **HTTP 500**，看不出到底哪错了。
这不是 bug，是 **#06 错误处理**留给你的活：加上分类 catch 链之后，
鉴权失败应该返回 401 而不是 500，限流应该返回 429 并带上重试提示。
**先亲眼看看"没有错误处理"有多难排查，再去写它。**
