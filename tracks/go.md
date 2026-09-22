# Go 轨道

> 面向：**有 5 年 Java 经验、Go 还不太熟、手上有 Go 项目**的工程师。
> 目标：在做 AI 应用的过程中顺带把 Go 吃透，而不是先花两周刷完 Go 语法再开始。

配合 [阶段任务卡](../stages/) 使用。任务卡讲"做什么"，本文讲"用 Go 怎么做、会踩什么坑"。
**执行顺序按 [ROADMAP.md](../ROADMAP.md) 的编号走**；分配策略见 [tracks/README.md](README.md)——**别把所有阶段都用 Go 再做一遍**。

> **Go 相关任务一览**：`#08–#12`（阶段一双语，12h）· `#27–#33`（阶段四主力，36h）· `#41`（生产化，4h）· 可选 `⊕#21+` `⊕#47`
>
> **调用的是 DeepSeek**，走 OpenAI 兼容协议，和 Java 版同一套字段——两版对照着看差异最清楚。

---

## 1. 为什么值得用 Go 做 AI 应用

不是"Go 也能做"，而是在某些环节 **Go 明显更合适**：

| Go 的优势 | 在 AI 应用里具体意味着什么 |
|---|---|
| **goroutine 极轻量** | 并行工具调用、向量+BM25 多路并发召回、fan-out/fan-in——`errgroup` 几行搞定，Java 要编排 `CompletableFuture` |
| **`context.Context` 贯穿调用链** | 用户关闭页面 → LLM 流、检索、rerank **整条链路自动取消**。这直接省 token。Java 里做这件事很别扭 |
| **单二进制交付** | **MCP Server 的最佳形态**：编译出一个文件，无运行时依赖，谁都能跑。这是 Go 相对 Java 最硬的优势 |
| **长连接成本低** | SSE 流式一个连接一个 goroutine，几 KB 栈；Java 要么占线程要么上响应式（心智负担大） |
| **启动快、内存小** | 适合 sidecar、serverless、边缘部署 |

**劣势也要认清**（这决定了哪些活别用 Go 干）：

| 劣势 | 应对 |
|---|---|
| AI 生态薄：文档解析、本地 embedding/rerank 推理、数据处理都没有好轮子 | **别硬写**。这些放 Python/Java 服务，Go 调 HTTP |
| 错误处理啰嗦（`if err != nil` 遍地） | 接受它。用 `errors.Is/As` + `fmt.Errorf("...: %w", err)` 包装 |
| 没有 Spring 那样的开箱即用 | 手写构造函数注入。**没有魔法反而更好调试** |

**结论性的架构判断**：
> Go 做**在线编排层**（网关、检索编排、Agent 运行时、MCP Server），
> Python / Java 做**离线数据层**（文档解析、切块、embedding、eval）。
> 这不是妥协，这本来就是更清晰的职责划分。

---

## 2. Go 语言速成：Java 工程师版

**不要先刷两周语法再开始。** 下面这张对照表 + 官方 [A Tour of Go](https://go.dev/tour/)（2~3 小时）足够你开始写阶段一的代码，剩下的边写边补。

### 2.1 会让你不适应的地方（按杀伤力排序）

| # | Go | Java 对照 | 说明 |
|---|---|---|---|
| 1 | **错误是返回值** `if err != nil { return err }` | 异常 + try/catch | **最大的心智转变**。没有异常传播，每一层都要显式处理或往上传。`panic` 只用于真正不可恢复的情况（相当于 `Error`，不是 `Exception`） |
| 2 | **`context.Context` 是第一个参数** | 超时是配置项、ThreadLocal 传上下文 | Go 把「超时 + 取消 + 请求级数据」合成一个显式参数，贯穿整个调用链。**对 LLM 应用极其重要**，后面反复用 |
| 3 | **接口隐式实现** | `implements` 显式声明 | 类型只要有对应方法就自动满足接口。接口定义在**使用方**而不是实现方（"接口要小，且属于调用者"） |
| 4 | **没有继承** | extends / 抽象类 | 用组合（struct 嵌入）+ 接口。**这会逼你重新设计习以为常的类层次**，是好事 |
| 5 | **零值可用** | null | `var m map[string]int` 是 nil map：**读安全、写 panic**。struct 零值通常直接可用，不需要构造函数 |
| 6 | **goroutine + channel** | 线程池 + BlockingQueue | 启动一个 goroutine 的成本约等于一次函数调用。但**谁启动谁负责结束**，否则泄漏 |
| 7 | **`defer`** | finally | 绑定在**函数**上（不是块），后进先出。`defer resp.Body.Close()` 是标准姿势 |
| 8 | **struct tag** ``json:"name"`` | Jackson 注解 | 字符串写错不报错，只是静默不生效——**这是 Go 新手的高频坑** |
| 9 | **大小写决定可见性** | public/private | 首字母大写 = 导出（public），小写 = 包内私有。没有 protected |
| 10 | **泛型较弱**（1.18+） | 完整泛型 | 够用但不如 Java 灵活。很多库还在用 `interface{}` / `any` |

### 2.2 三个一定会踩的坑

**① 接口的 typed nil 陷阱**——最经典的 Go 坑：

```go
type MyErr struct{}
func (e *MyErr) Error() string { return "boom" }

func bad() error {
    var e *MyErr = nil
    return e          // ← 返回的 error 接口「非 nil」！(类型有值，值为 nil)
}

if err := bad(); err != nil {
    // 会进来！尽管你觉得返回的是 nil
}
```
**规则**：要返回"无错误"，就直接 `return nil`，不要返回一个 nil 的具体错误类型指针。

**② 切片共享底层数组**：

```go
a := []int{1, 2, 3, 4, 5}
b := a[:2]
b = append(b, 99)   // 可能改掉 a[2]！
```
需要独立副本时用 `slices.Clone` 或 `append([]T(nil), s...)`。

**③ 循环变量捕获**（Go 1.22 已修复，但老代码里全是）：

```go
for _, v := range items {
    go func() { process(v) }()   // Go < 1.22：所有 goroutine 拿到同一个 v
}
```
确认你的 `go.mod` 里 `go` 版本 ≥ 1.22，否则显式传参 `go func(v T){...}(v)`。

### 2.3 工具链（这部分比 Java 舒服）

| 工具 | 用途 | Java 对照 |
|---|---|---|
| `go test ./...` | 内置测试，**表驱动测试**是社区惯例 | JUnit（但不用引依赖） |
| `go test -race` | **竞态检测器**，能直接抓出数据竞争 | Java 没有等价物。**并发代码必跑** |
| `go test -bench` | 内置基准测试 | JMH |
| `net/http/pprof` | **内置性能剖析**：CPU、内存、goroutine 数 | 要挂 arthas / async-profiler |
| `go vet` / `golangci-lint` | 静态检查 | SpotBugs / SonarQube |
| `go build` | **交叉编译单二进制**：`GOOS=linux GOARCH=amd64 go build` | 需要 JRE |

`-race` 和 `pprof` 这两个在阶段四、五会救你的命，提前知道它们存在。

### 2.4 Web 服务怎么写（没有 Spring 的日子）

- **路由**：`net/http`（1.22 后已支持路径参数，简单项目够用）或 `chi` / `gin` / `echo`
- **依赖注入**：**手写构造函数**。`NewAgentService(llmClient, retriever, logger)` 一路传下去。项目大了再考虑 `wire`（编译期注入）
- **配置**：`viper` 或标准库 + 环境变量
- **日志**：标准库 `log/slog`（结构化日志，Go 1.21+ 内置），不用引第三方
- **心态**：**Go 没有魔法。** 没有 `@Autowired`、没有 AOP、没有启动时扫描。一开始觉得啰嗦，出问题时你会感谢它——调用链是你自己写的，不需要猜框架干了什么

---

## 3. Go 侧技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| **LLM 调用** | **标准库 `net/http` + `encoding/json`**，直调 OpenAI 兼容接口（DeepSeek） | 零依赖，和 Java 版用同一套协议、同一组字段，两版对照着看差异最清楚。SSE 流式用 `bufio.Scanner` 解析，本身就是很好的 Go 练习 |
| **应用框架** | **建议不用框架，直接用官方 SDK 自己封装** | Go 的 AI 框架生态（eino、langchaingo 等）不如 Python/Java 成熟。**这反而是好事**：你会被迫理解底层循环，而不是被抽象包着。等你手写过一遍 Agent 循环，再评估要不要引框架 |
| **向量库** | **pgvector**（配 `pgx` 驱动）起步；规模大了用 Qdrant / Milvus 官方 Go client | 和 Java 轨道保持一致，便于对比 |
| **关键词检索** | PostgreSQL 全文检索 或 Elasticsearch（`go-elasticsearch`） | 混合检索必备 |
| **Embedding / Rerank** | ⚠️ **不要用 Go 跑本地推理**。起一个 Python 服务暴露 HTTP，Go 调用 | `onnxruntime-go` 存在但坑多、部署复杂，不值得在学习期投入 |
| **文档解析** | ⚠️ 同上，交给 Python（unstructured / MinerU）或 Java（Tika） | Go 没有 Tika 的对等物 |
| **并发控制** | `golang.org/x/sync/errgroup` | **并行工具调用、多路召回的核心工具**，必学 |
| **可观测** | `log/slog` + OpenTelemetry Go SDK + 内置 pprof | |
| **HTTP 客户端** | 标准库 `net/http`，**注意复用 `http.Client`、设超时** | 别每次 new 一个 |

### 模型 id 与 base URL

```go
const (
    baseURL = "https://api.deepseek.com"   // 换通义 / Kimi / 本地 vLLM 只改这里
    model   = "deepseek-v4-pro"            // 便宜档位：deepseek-v4-flash
)
```

⚠️ **模型名会变**。旧别名 `deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 下线，
网上大量教程还在用它们。**做成配置项**，报 "model not found" 时先查官方文档。

---

## 4. 各阶段的 Go 实现要点

### 阶段一 · API 基本功 ｜ **#08–#12**（12h）｜ ✅ 完整做一遍

**这是学 Go 的最佳起点**：代码量小、零依赖，而且和你刚写完的 Java 版逐行对照，语言差异一目了然。

```go
type Message struct {
    Role    string `json:"role"`
    Content string `json:"content"`
}

type ChatRequest struct {
    Model     string    `json:"model"`
    Messages  []Message `json:"messages"`
    MaxTokens int       `json:"max_tokens"`
    Stream    bool      `json:"stream,omitempty"`   // omitempty：false 时不发出去
}

type Usage struct {
    PromptTokens     int `json:"prompt_tokens"`
    CompletionTokens int `json:"completion_tokens"`
    // DeepSeek 特有的自动缓存统计
    PromptCacheHitTokens  int `json:"prompt_cache_hit_tokens"`
    PromptCacheMissTokens int `json:"prompt_cache_miss_tokens"`
}

func (c *Client) Complete(ctx context.Context, msgs []Message) (*ChatResponse, error) {
    body, _ := json.Marshal(ChatRequest{Model: c.model, Messages: msgs, MaxTokens: 4096})

    req, err := http.NewRequestWithContext(ctx, http.MethodPost,
        c.baseURL+"/chat/completions", bytes.NewReader(body))
    if err != nil {
        return nil, err
    }
    req.Header.Set("Authorization", "Bearer "+c.apiKey)
    req.Header.Set("Content-Type", "application/json")

    resp, err := c.httpClient.Do(req)
    if err != nil {
        return nil, fmt.Errorf("调用 LLM 失败: %w", err)   // %w 保留错误链，errors.Is/As 才能用
    }
    defer resp.Body.Close()                                // ⚠️ 忘了它就是 goroutine + 连接泄漏

    if resp.StatusCode != http.StatusOK {
        raw, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
        return nil, fmt.Errorf("LLM 返回 %d: %s", resp.StatusCode, raw)
    }

    var out ChatResponse
    if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
        return nil, fmt.Errorf("解析响应失败: %w", err)
    }
    return &out, nil
}
```

**Go 这里比 Java 舒服的地方**：`encoding/json` 默认就忽略未知字段——
Java 要专门加 `@JsonIgnoreProperties(ignoreUnknown = true)` 才不会因为厂商加字段而崩。
而 `omitempty` 对应 Java 的 `@JsonInclude(NON_NULL)`，两边都要注意"别把空值发出去"。

**⚠️ 流式：SSE 要自己解析**

```go
req.Header.Set("Accept", "text/event-stream")
// 请求体里 Stream: true

scanner := bufio.NewScanner(resp.Body)
scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)   // ⚠️ 默认 64KB 上限，长行会截断
for scanner.Scan() {
    line := scanner.Text()
    if !strings.HasPrefix(line, "data: ") {
        continue                                       // 空行、注释行直接跳过
    }
    payload := strings.TrimPrefix(line, "data: ")
    if payload == "[DONE]" {
        break                                          // ⚠️ 这不是 JSON，先判断再解析
    }
    var chunk StreamChunk
    if err := json.Unmarshal([]byte(payload), &chunk); err != nil {
        return err
    }
    // chunk.Choices[0].Delta.Content 就是增量文本
}
if err := scanner.Err(); err != nil {                  // ⚠️ 循环正常结束不代表没出错
    return err
}
```

三个必须做对的点：
1. **`[DONE]` 不是 JSON**，先判断再 `Unmarshal`，否则每次流结束都报解析错误
2. **循环结束后检查 `scanner.Err()`**——流中途断开时循环只是安静地结束
3. **把 `ctx` 传进 `http.NewRequestWithContext`**，客户端断开时请求自动取消，
   **不会继续烧 token**（这是 Go 相对 Java 的真实优势，务必亲手验证一次）

**错误处理**：Go 没有异常，用 `%w` 包装保留错误链，上层用 `errors.Is` / `errors.As` 判断：

```go
var netErr net.Error
switch {
case errors.Is(err, context.Canceled):        // 用户断开，不算故障，别告警
case errors.As(err, &netErr) && netErr.Timeout():  // 超时，可重试
default:                                       // 看业务错误码决定
}
```

对照 Java 的 catch 链，体会「错误是值」和「异常是控制流」的区别——同一个逻辑，两种语言的表达完全不同。

**Go 侧额外产出**：`projects/project-0-llm-gateway-go/`，功能和 Java 版对齐。
写完做一件事：**对照两版代码写一篇笔记**，记录「同一个功能，Java 怎么写 / Go 怎么写 / 哪个更顺手」。
这篇笔记会是你 Go 认知的地基。

---

### 阶段二 · RAG ｜ **⊕#21+**（8h，P2）｜ 🔶 只做在线检索服务

**离线部分（解析、切块、embedding、入库）不要用 Go 写**，生态不支持，硬写会让你耗在无关的地方。用 Java/Python 做，Go 只做在线检索编排。

**Go 的主场：并发多路召回。** 向量检索和 BM25 检索本来就该并行：

```go
import "golang.org/x/sync/errgroup"

g, ctx := errgroup.WithContext(ctx)
var vecHits, bm25Hits []Chunk

g.Go(func() error {
    var err error
    vecHits, err = vectorSearch(ctx, queryVec, 50)
    return err
})
g.Go(func() error {
    var err error
    bm25Hits, err = keywordSearch(ctx, query, 50)
    return err
})
if err := g.Wait(); err != nil {   // 任一失败 → ctx 取消 → 另一路自动中止
    return nil, err
}
merged := rrfFuse(vecHits, bm25Hits)
```

`errgroup` 的行为值得琢磨：**任何一路出错，`ctx` 立即取消，其他路自动中止**。这个「级联取消」正是 Go 并发模型的精髓，Java 里要实现同样的语义相当啰嗦。

其他要点：
- **pgvector**：用 `pgx`（不是 `database/sql`），性能更好、类型支持更完整
- **`context` 一路传到底**：HTTP handler → 检索 → rerank → LLM 调用。用户一断开，全链路停止
- **HTTP 客户端复用**：调 embedding / rerank 服务时复用 `http.Client` 并设超时，别每次 new
- 用 `-race` 跑一次并发召回的测试，确认没有数据竞争

---

### 阶段三 · Eval ｜ ❌ 跳过 Go 实现

Eval 是离线批处理，用哪种语言写没有本质差别，做两遍是纯粹的重复劳动。用你主力语言写一套就行。

如果你**特别想**用 Go 练手，可以只把「批量并发跑题目」这部分用 Go 写——`errgroup` + 带缓冲 channel 控制并发度，是个不错的练习：

```go
sem := make(chan struct{}, 8)   // 并发度 8，别把自己的 API 限流打爆
g, ctx := errgroup.WithContext(ctx)
for _, q := range questions {
    q := q
    g.Go(func() error {
        sem <- struct{}{}
        defer func() { <-sem }()
        return runOne(ctx, q)
    })
}
err := g.Wait()
```

但**优先级不高**，别因此拖慢阶段三的主线。阶段三的价值在黄金集和指标设计，不在实现语言。

---

### 阶段四 · Agent ｜ **#27–#33**（36h）｜ ✅ Go 做主力，本轨道的重头戏

**这是 Go 相对 Java 优势最大的阶段**，也是你把 Go 真正学扎实的地方——因为 Agent 恰好用满了 Go 的核心特性：并发、channel、context、接口、错误处理。

#### 手写循环（T4.1，仍然不许用框架）

先手写，理解数据流。Go 的结构体 + 显式错误让这个循环意外地清晰：

```go
for turn := 0; turn < maxTurns; turn++ {
    resp, err := client.Complete(ctx, messages, tools)
    if err != nil {
        return err
    }
    choice := resp.Choices[0]
    messages = append(messages, choice.Message)       // assistant 消息，含 tool_calls

    if choice.FinishReason != "tool_calls" {
        break                                          // stop / length / content_filter ...
    }

    for _, call := range choice.Message.ToolCalls {
        out, err := execute(ctx, call)
        // ⚠️ 失败也要作为结果回传，让模型自己决定重试或换路径，不要中断循环
        if err != nil {
            out = fmt.Sprintf("工具执行失败: %v", err)
        }
        messages = append(messages, Message{
            Role:       "tool",
            ToolCallID: call.ID,                       // ⚠️ 必须回带，否则对不上
            Content:    out,
        })
    }
}
```

⚠️ **OpenAI 兼容协议里，每个 tool_call 对应一条独立的 `role=tool` 消息**，且必须带 `tool_call_id`。
看别家文档时注意：不同厂商在这里的协议不一样，别把两套写法混着抄。

#### 并行工具调用（T4.3）—— Go 在这里完胜

一条 assistant 消息里的多个 `tool_call` 应该并发执行。Go 版本干净得让人舒服：

```go
results := make([]Message, len(calls))
g, gctx := errgroup.WithContext(ctx)
for i, call := range calls {
    i, call := i, call
    g.Go(func() error {
        out, err := execute(gctx, call)
        if err != nil {
            out = fmt.Sprintf("工具执行失败: %v", err)
        }
        results[i] = Message{Role: "tool", ToolCallID: call.ID, Content: out}
        return nil   // ← 注意：返回 nil，单个工具失败不该中断整批
    })
}
_ = g.Wait()
messages = append(messages, results...)
```

**注意那个 `return nil`**：单个工具失败要作为结果回传给模型（让它自己决定重试或换路径），
**而不是让整批中断**。这是 Agent 健壮性的关键细节，也是很容易写错的地方。
按索引写入 `results` 切片是并发安全的（各 goroutine 写不同下标），但**用 `-race` 跑一遍确认**，养成习惯。

#### 工具定义（T4.2）

OpenAI 兼容格式的 `tools` 数组，每个工具的 `parameters` 就是一份 JSON Schema：

```go
var queryLogsTool = Tool{
    Type: "function",
    Function: FunctionDef{
        Name:        "query_logs",
        Description: "查询指定服务在某时间段内的错误日志，返回按错误类型聚合的统计",
        Parameters: map[string]any{
            "type": "object",
            "properties": map[string]any{
                "service": map[string]any{"type": "string", "description": "服务名，如 order-service"},
                "since":   map[string]any{"type": "string", "description": "起始时间，RFC3339 格式"},
            },
            "required":             []string{"service"},
            "additionalProperties": false,
        },
    },
}
```

手写 map 很啰嗦，可以用 `invopop/jsonschema` 之类的库从 struct tag 生成。
但**无论怎么生成，都要把最终 schema 打印出来看一眼**——
schema 写错不会报错，只会让模型开始传错参数，然后你陷入玄学调试。

⚠️ `description` 就是给模型看的接口文档。**写不好，模型就选错工具、传错参**——
这是 Agent 效果的头号影响因素，比换模型有用得多。

#### 上下文管理（T4.5）

⚠️ **这里和 Anthropic 那套不一样，别照抄教程**：服务端自动压缩（compaction）、
上下文清理（context editing）这些是 **Anthropic API 的特有能力**，
OpenAI 兼容协议（含 DeepSeek）**没有**对应字段。

在这条技术栈上，上下文管理要**你自己实现**：
- **滚动摘要**：历史超过阈值时，用一次便宜模型的调用把早期对话压成一段摘要，替换掉原文
- **按需丢弃**：直接扔掉最早的若干轮（最简单，但会"忘事"）
- **外部记忆**：要长期记住的事实写进 KV / 数据库，需要时检索回来塞进上下文

**这反而是好事**：自己实现一遍，你会真正理解"上下文窗口是稀缺资源"这件事，
而不是调一个参数就以为解决了。

#### MCP Server —— 用 Go 写的最佳理由（T4.6）

**单二进制交付**：`GOOS=linux GOARCH=amd64 go build` 出一个文件，扔到任何机器上就能跑，不需要 JRE、不需要 pip install、不需要容器。
对于"给团队做一个内部系统的 MCP Server"这种场景，**Go 是最省事的选择**，没有之一。

这也是你在团队里最容易体现价值的切入点：把公司的工单系统、发布系统、监控系统各封装一个 MCP Server，交付几个二进制文件，所有人的 AI 工具立刻都能用。

#### 护栏的 Go 写法（T4.4）

```go
ctx, cancel := context.WithTimeout(ctx, 5*time.Minute)   // 会话总时长
defer cancel()
```
- **轮数上限**：循环计数，或 `MaxIterations`
- **会话超时**：`context.WithTimeout`，一处设置全链路生效——**这正是 context 设计的价值**
- **token 预算**：累加 `resp.Usage`，超了主动 `cancel()`
- **危险操作确认**：工具函数内部拦截，返回"需要确认"的结果给模型，由外层驱动人工介入

#### Go 侧必须注意的 goroutine 泄漏

Agent 会长时间运行，泄漏会累积成事故：
- 每个 `NewStreaming` 都要么读完、要么靠 ctx 取消
- `defer resp.Body.Close()` 别忘（调 embedding / rerank 服务时）
- 用 `net/http/pprof` 看 goroutine 数量，**跑一个长会话前后对比**，数量应该回落

---

### 阶段五 · 生产化 ｜ **#41**（4h）｜ ✅ 和 Java 轨道关注点不同，不算重复

Go 侧独有的重点：

| 项 | 做法 |
|---|---|
| **竞态检测** | CI 里跑 `go test -race ./...`。**并发代码不跑 race 等于没测** |
| **goroutine 泄漏** | pprof 的 `/debug/pprof/goroutine`，压测前后对比数量 |
| **内存剖析** | `/debug/pprof/heap`。注意大 slice 和字符串拼接（用 `strings.Builder`） |
| **context 取消省成本** | 验证：客户端断开连接后，上游 LLM 流真的停了。**这是实打实的省钱**，做个对照实验测一下 |
| **优雅关闭** | `signal.NotifyContext` + `http.Server.Shutdown`，让进行中的会话跑完 |
| **限流** | `golang.org/x/time/rate`（令牌桶，标准做法） |
| **结构化日志** | `log/slog`，traceId 放进 `slog.With()` 一路传（Go 没有 MDC，靠 ctx 或显式传 logger） |

⚠️ **Go 没有 ThreadLocal/MDC**。traceId 要么放 `context.Value`，要么显式传 logger。
一开始会觉得麻烦，但**它避免了 Java 里"异步/线程池场景 MDC 丢失"那类经典问题**——显式的代价换确定性。

---

### 阶段六 · 基础 ｜ **⊕#47**（3h，P2）｜ ✅ 读优秀 Go 代码

既然不引 LLM SDK，这一项改成读**标准库和你已经在用的库**——学 Go 工程实践的材料同样上好：

- **`net/http`** 的 `Client` / `Transport`：连接池、超时分层是怎么设计的（对照你自己写的客户端）
- **`context`** 包本身：只有一百多行，读完你会彻底理解取消是怎么级联的
- **`errgroup`**：加起来不到 100 行，却解决了你在阶段二、四反复用到的问题
- **`option.RequestOption` 这类函数式选项模式**：Go 社区最常用的可选参数写法，学会了到处能用

读完写一篇笔记：**「哪些设计我会搬到自己的项目里」**。
这比读十篇 Go 最佳实践文章有用。

---

## 5. 🎫 Go 轨道过关补充

在 [阶段任务卡](../stages/) 的过关卡片之外，**额外**满足这些才算 Go 轨道过关。

### 🎯 硬指标

| # | 指标 | 达标线 | 对应任务 |
|---|---|---|---|
| G1 | SSE 解析正确 | 流式拼出的完整文本与非流式结果一致；`[DONE]` 不当 JSON 解析；**循环后检查了 `scanner.Err()`** | **#10** |
| G2 | **context 取消真的生效** | 客户端断开后，上游 LLM 调用**在 1s 内停止**（用日志或 token 计数证明没有继续消耗） | **#10** / #41 |
| G3 | 并发召回 | 向量 + BM25 并行执行，总耗时 ≈ max(两者) 而非 sum；任一失败时另一路能被取消 | ⊕#21+ |
| G4 | **race 干净** | `go test -race ./...` **零告警** | **#29** / #41 |
| G5 | 并行工具调用 | 一轮内并发执行 ≥ 2 个工具，且**单个工具失败不中断整批** | **#29** |
| G6 | **单二进制交付** | MCP Server 交叉编译出 Linux / macOS 二进制，**在没装 Go 的机器上直接运行成功** | **#32** |
| G7 | **无 goroutine 泄漏** | 跑完 20 轮长会话后，pprof 里 goroutine 数量**回落到基线附近** | **#33** / #41 |
| G8 | schema 正确性 | 每个工具的 `parameters` schema 都**打印检查过**，与预期一致（schema 写错不报错，只会让模型传错参） | **#28** |

G2 和 G7 是 Go 轨道的招牌指标——**它们在 Java 里要么很难做，要么做起来很别扭**。能拿下这两条，说明你抓住了 Go 的核心价值，而不只是换了套语法。

### 🗣 Go 专属口试题

1. `if err != nil` 到处都是，比异常啰嗦得多。**Go 为什么坚持这么设计？** 你认同吗？
2. 什么是 typed nil 陷阱？写一段会触发它的代码。
3. `context.Context` 解决了哪三个问题？在你的 Agent 里，一次 `cancel()` 会级联影响到哪些地方？
4. goroutine 泄漏通常怎么发生？你怎么发现它？
5. Go 的接口和 Java 的接口，设计哲学上最大的区别是什么？（提示：想想接口应该定义在哪一侧）
6. 同一个 Agent，Java 和 Go 各实现一遍之后——**哪些地方 Go 明显更顺手，哪些地方你更想念 Java？**

最后一题是本轨道的核心问题。**能具体回答它，说明你不是"会写 Go 语法"，而是"知道什么时候该用 Go"。** 这才是多学一门语言的真正价值。

---

## 6. Go + LLM 常见卡点

| 症状 | 大概率原因 |
|---|---|
| 流式解析每次都在结尾报错 | 把 `data: [DONE]` 当 JSON 解析了。要先判断再 `Unmarshal` |
| 流式中途静默结束、没报错 | 忘了在循环后检查 `scanner.Err()`；或长行超过 `bufio.Scanner` 默认 64KB 上限被截断 |
| 客户端断开后 token 还在涨 | 用了 `http.NewRequest` 而不是 `NewRequestWithContext`，或中途用 `context.Background()` 把链路截断了 |
| 工具参数总是不对 | schema 写错（`required` 漏了、类型不对），或 `description` 太含糊。**先打印 schema 检查，再改描述** |
| 请求被拒绝说参数非法 | 把 `nil`/零值字段发出去了。可选字段加 `omitempty` |
| `err != nil` 成立但错误内容是空的 | typed nil 陷阱 |
| 并发工具调用偶发数据错乱 | 多个 goroutine 写同一块内存。跑 `-race` |
| 服务跑久了内存持续上涨 | goroutine 泄漏：`defer resp.Body.Close()` 忘了，或流没读完就返回。查 pprof |
| 大量 `context deadline exceeded` | 超时设太短，或超时没有按「单次请求 / 整个会话」分层 |
| JSON 反序列化字段全是零值 | struct 字段首字母小写（未导出），或 tag 名字对不上 |
| HTTP 调用偶发失败率高 | 每次 new 了 `http.Client`，连接没复用；或没设超时用了默认无限等待 |
