---
feature: riqing-p0-docs
status: delivered
updated: 2026-08-11
branch: none
commits: n/a
---

# 日清 P0 规格文档包

## Report

**What was built** — 在 `docs/` 交付 P0 规格五件套：术语表（D1）、数据模型与 ER（D2）、字段字典（D3）、Agent 14 工具契约（D4）、核心页面文本线框与 Agent 消息状态机（D5）。全部为 Markdown；示例负载仅出现在 md 代码围栏内。共享决策（分表投影、软删撤销、本地时间、风险分档确认）在五份文档间交叉引用一致。

**Verification** — `docs/` 下仅 `.md` 文件（6 个：5 份交付物 + 本 feature 文档）。对 `weeksSpec`/`reminderOffsets`/`biweeklyOdd`/`isDeletedInstance`/`week1Monday` 与 14 个工具名做跨文档字符串检索，D3 参数名与 D4 工具参数一致；术语与 D1 对齐。

**Journey log** — 1) 工作区非 git，按用户指定跳过 worktree。2) Q1–Q6 采用规划默认：调课用额外 Event、多学期、Todo 含 notes、文本线框、产物进 `docs/`。3) 重复「仅本次」在 D2 用 `event_exceptions` 支撑，与「调课不建表」不冲突。4) 未做 D6–D8（规划 P1）。

## [S1] Problem

修订版 PRD 与产出物规划已锁定方向，但缺少可直接指导开发对齐的 P0 规格：术语不统一、数据模型未固化、字段无校验规格、Agent 工具无契约、页面无文本线框。

## [S2] Design

### 交付物

| ID | 路径 | 状态 |
|----|------|------|
| D1 | `docs/01_术语表与概念边界.md` | 已交付 |
| D2 | `docs/02_数据模型与ER图.md` | 已交付 |
| D3 | `docs/03_字段字典.md` | 已交付 |
| D4 | `docs/04_Agent工具契约.md` | 已交付 |
| D5 | `docs/05_核心页面线框.md` | 已交付 |

### 硬约束

- 仅 `.md`；示例只在 Markdown 代码围栏内
- 不建 git worktree
- Q1–Q6 默认决策已落实

### 共享决策

1. 课程与日程分表，日历为投影
2. 软删除 + 撤销快照
3. 设备本地时区；Agent ISO-8601 本地
4. 重复 DAILY/WEEKLY/WORKDAYS；单双周在 Course
5. 风险级：读/创建/完成低；修改中；删除高
6. 批量删除二次确认

## [S3] Out of Scope

- D6–D8、视觉系统、实现代码、真实 API、云同步、语音

## Tasks

- [x] T1: 写入 D1 术语表 — acceptance: 存在且含术语与边界（covers: S2）
- [x] T2: 写入 D2 ER/数据模型 — acceptance: mermaid ER、投影、索引（covers: S2; depends: T1）
- [x] T3: 写入 D3 字段字典 — acceptance: 四实体规格齐，与 D4 参数名一致（covers: S2; depends: T2）
- [x] T4: 写入 D4 工具契约 — acceptance: 14 工具 + 确认矩阵 + 错误码（covers: S2; depends: T3）
- [x] T5: 写入 D5 页面线框 — acceptance: P0 页面线框 + 消息状态机（covers: S2）
- [x] T6: 交叉一致性自检 — acceptance: 字段/工具/术语一致；docs 无非 md（covers: S2）
