# 进度看板

> 唯一的"我学到哪了"的事实来源。每周日更新一次。

**计划**：[AI Agent / RAG 学习计划](plans/ai-agent-rag-learning-plan.md) ｜ **开始日期**：`____-__-__`

## 阶段进度

| 阶段 | 任务卡 | 计划周次 | 状态 | 开始 | 过关 | 备注 |
|---|---|---|---|---|---|---|
| 一 · LLM API 基本功 | [stage-1](stages/stage-1-llm-api.md) | 第 1~2 周 | ⬜ 未开始 | | | |
| 二 · RAG 全链路 | [stage-2](stages/stage-2-rag.md) | 第 3~5 周 | ⬜ 未开始 | | | |
| 三 · 评估体系 | [stage-3](stages/stage-3-eval.md) | 第 6~7 周 | ⬜ 未开始 | | | |
| 四 · Agent | [stage-4](stages/stage-4-agent.md) | 第 8~10 周 | ⬜ 未开始 | | | |
| 五 · 生产化 | [stage-5](stages/stage-5-production.md) | 第 11~12 周 | ⬜ 未开始 | | | |
| 六 · 补基础 + 选方向 | [stage-6](stages/stage-6-fundamentals.md) | 第 13~14 周 | ⬜ 未开始 | | | |

状态图例：⬜ 未开始 ｜ 🟡 进行中 ｜ 🔴 卡住（备注写清卡在哪）｜ ✅ 已过关

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
| Project 0 · LLM 网关服务 | 一 | ⬜ | `projects/project-0-llm-gateway/` |
| Project 1 · 企业知识库问答 | 二~三 | ⬜ | `projects/project-1-knowledge-qa/` |
| Project 2 · 研发运维助手 Agent | 四~五 | ⬜ | `projects/project-2-devops-agent/` |

## 周记

| 周次 | 日期 | 本周推进 | 卡点 | 笔记 |
|---|---|---|---|---|
| W1 | | | | |
