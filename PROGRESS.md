# 进度看板

> 唯一的"我学到哪了"的事实来源。每周日更新一次。

**计划**：[AI Agent / RAG 学习计划](plans/ai-agent-rag-learning-plan.md) ｜ **开始日期**：`____-__-__`
**双轨方案**：⬜ 紧凑版（14 周，Go 只做阶段一 + 四）｜ ⬜ 完整版（17 周）——见 [tracks/README.md](tracks/README.md)，选定后勾一个

## 阶段进度

| 阶段 | 任务卡 | 计划周次 | Java | Go | 状态 | 开始 | 过关 | 备注 |
|---|---|---|---|---|---|---|---|---|
| 一 · LLM API 基本功 | [stage-1](stages/stage-1-llm-api.md) | 第 1~2 周 | 主力 | **也做一遍** | ⬜ 未开始 | | | |
| 二 · RAG 全链路 | [stage-2](stages/stage-2-rag.md) | 第 3~5 周 | 主力 | 只做在线检索 | ⬜ 未开始 | | | |
| 三 · 评估体系 | [stage-3](stages/stage-3-eval.md) | 第 6~7 周 | 主力 | 跳过 | ⬜ 未开始 | | | |
| 四 · Agent | [stage-4](stages/stage-4-agent.md) | 第 8~10 周 | 了解 | **主力** | ⬜ 未开始 | | | |
| 五 · 生产化 | [stage-5](stages/stage-5-production.md) | 第 11~12 周 | 限流/熔断 | pprof/race | ⬜ 未开始 | | | |
| 六 · 补基础 + 选方向 | [stage-6](stages/stage-6-fundamentals.md) | 第 13~14 周 | — | 读 SDK 源码 | ⬜ 未开始 | | | |

状态图例：⬜ 未开始 ｜ 🟡 进行中 ｜ 🔴 卡住（备注写清卡在哪）｜ ✅ 已过关

### Go 轨道专属过关项

见 [tracks/go.md § 过关补充](tracks/go.md#5--go-轨道过关补充)。这 8 条和阶段过关卡片是**并列**关系，都要过。

| 编号 | 指标 | 对应阶段 | 状态 |
|---|---|---|---|
| G1 | 流式 `Accumulate` 正确 + 检查 `stream.Err()` | 一 | ⬜ |
| G2 | **context 取消真的生效**（断开后 1s 内停止烧 token） | 一 / 五 | ⬜ |
| G3 | 并发多路召回，耗时 ≈ max 而非 sum | 二 | ⬜ |
| G4 | `go test -race ./...` 零告警 | 二 / 四 | ⬜ |
| G5 | 并行工具调用，单个失败不中断整批 | 四 | ⬜ |
| G6 | **MCP Server 单二进制**，无 Go 环境的机器直接跑通 | 四 | ⬜ |
| G7 | **无 goroutine 泄漏**（长会话后 pprof 回落基线） | 四 / 五 | ⬜ |
| G8 | 每个工具的 `jsonschema` tag 生成的 schema 都检查过 | 四 | ⬜ |

## 关键指标（跨阶段追踪）

这几个数字是你学习成果的硬证据，面试时直接能用。每次有新测量就补一行。

| 日期 | 指标 | 数值 | 怎么测的 |
|---|---|---|---|
| | Recall@5（检索命中率） | | 阶段三 eval 脚本 |
| | 忠实度 / 幻觉率 | | LLM-as-Judge |
| | 单次问答成本 | | token 明细 × 单价 |
| | Prompt 缓存命中率 | | `usage.cache_read_input_tokens` 占比 |
| | 端到端 P95 延迟 | | 压测 |

## 项目状态

| 项目 | 阶段 | 状态 | 代码 |
|---|---|---|---|
| Project 0 · LLM 网关服务（Java） | 一 | ⬜ | `projects/project-0-llm-gateway/` |
| Project 0 · LLM 网关服务（Go） | 一 | ⬜ | `projects/project-0-llm-gateway-go/` |
| Project 1 · 企业知识库问答 | 二~三 | ⬜ | `projects/project-1-knowledge-qa/` |
| Project 2 · 研发运维助手 Agent（**Go 主力**） | 四~五 | ⬜ | `projects/project-2-devops-agent/` |

## 周记

| 周次 | 日期 | 本周推进 | 卡点 | 笔记 |
|---|---|---|---|---|
| W1 | | | | |
