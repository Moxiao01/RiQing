# D4 Agent 工具契约

| 项目 | 内容 |
|------|------|
| 文档 ID | D4 |
| 状态 | 已锁定（P0） |
| 上游 | D1、D2、D3、PRD §3.4 |
| 下游 | D5、D6（后续）、Prompt 编写 |
| 更新 | 2026-08-11 |

---

## 1. 定位与硬约束

1. Agent 仅通过下列**工具**读写本地数据；禁止模型直接拼 SQL/任意文件访问。
2. 所有写操作必须先过 `ToolValidator`（与 D3 校验一致），再进 Repository。
3. 时间参数一律要求模型输出**本地 ISO-8601 不带 offset** 的形式（如 `2026-08-12T15:00:00`）；端侧按设备时区解释。
4. 模糊时间（「明天下午」）必须在对话层解析为绝对时间后再调工具；解析不确定 → **反问**，禁止猜。
5. 示例负载仅文档示意，实现以本表字段名为准。

---

## 2. 工具总表（14）

| # | 工具名 | 读/写 | 风险 | 确认策略 | 一句话 |
|---|--------|-------|------|----------|--------|
| 1 | `list_events` | 读 | 低 | 无 | 按时间范围列出日程（含展开） |
| 2 | `create_event` | 写 | 低 | 直接执行 + 结果可撤销 | 新建日程 |
| 3 | `update_event` | 写 | 中 | 字段 diff 确认 | 修改日程（父事件或本次例外） |
| 4 | `delete_event` | 写 | 高 | 单条确认后软删可撤销 | 删除日程/本次实例 |
| 5 | `list_todos` | 读 | 低 | 无 | 列待办 |
| 6 | `create_todo` | 写 | 低 | 直接执行 + 可撤销 | 新建待办 |
| 7 | `update_todo` | 写 | 中 | diff 确认 | 改待办字段 |
| 8 | `complete_todo` | 写 | 低 | 直接执行 | 标记完成 |
| 9 | `delete_todo` | 写 | 高 | 确认后软删可撤销 | 删待办 |
| 10 | `list_courses` | 读 | 低 | 无 | 按周/日期列课程 |
| 11 | `create_course` | 写 | 中 | 确认关键字段 | 新建课程 |
| 12 | `update_course` | 写 | 中 | diff 确认 | 改课程定义 |
| 13 | `delete_course` | 写 | 高 | 确认后软删可撤销 | 删课程定义 |
| 14 | `get_semester` | 读 | 低 | 无 | 读当前/指定学期 |

> MVP **不提供**批量专用工具；批量由模型多次调用或端上「批量删除待办」专用 UI 流程（高风险二次确认）。若模型连环 delete_todo，端上应合并为一张确认列表。

---

## 3. 通用返回与错误

### 3.1 成功返回结构（逻辑）

```json
{
  "ok": true,
  "tool": "create_event",
  "data": { }
}
```

### 3.2 失败返回结构

```json
{
  "ok": false,
  "tool": "create_event",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "endAt must be after startAt",
    "field": "endAt"
  }
}
```

### 3.3 错误码

| code | 含义 | UI |
|------|------|-----|
| `VALIDATION_ERROR` | 字段非法 | 透传给模型修复一轮；仍失败则终止并提示改说法 |
| `NOT_FOUND` | id 不存在或已删 | 提示「找不到该条目」 |
| `CONFLICT` | 唯一约束（如同例外重复） | 提示冲突 |
| `UNAUTHORIZED` | 未配置 API（仅聊天层） | 引导设置 |
| `TIMEOUT` | 上游超时 | 重试 |
| `CONFIRM_REQUIRED` | 端上拦截，等待用户确认 | 显示确认卡 |
| `CANCELLED` | 用户拒绝确认 | 结果卡「已取消」 |
| `INTERNAL` | 未知错误 | 通用失败文案 + 日志（无 Key） |

---

## 4. 确认与撤销矩阵

| 场景 | 策略 |
|------|------|
| 读工具 | 直接执行 |
| create_event / create_todo / complete_todo | 直接执行；结果卡 10s 可撤销 |
| update_* / create_course | 展示 diff 或关键字段 → 用户确认 → 执行 |
| delete 单条 | 展示标题+时间 → 确认 → 软删 → 可撤销 |
| 连续多个 delete / 用户说「删掉所有…」 | 合并列表 + **二次确认** |
| 撤销 | 读 `undo_snapshots`，不调用模型 |

---

## 5. 逐工具契约

### 5.1 `list_events`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| start | ISO local datetime | 是 | 含 |
| end | ISO local datetime | 是 | 不含或含均可，端上统一为闭开区间文档化：`[start, end)` |
| keyword | String? | 否 | 匹配 title/notes |

**返回 data**：`EventOccurrence[]`（含展开后的日期、标题、时间、来源 course/event、id）

**风险**：低

---

### 5.2 `create_event`

| 参数 | 类型 | 必填 | 校验（同 D3） |
|------|------|------|----------------|
| title | String | 是 | 1–80 |
| startAt | ISO | 是 | |
| endAt | ISO | 否 | 缺省 start+60min；须 > start |
| isAllDay | Boolean | 否 | 默认 false |
| notes | String? | 否 | ≤2000 |
| reminderOffsets | Int[]? | 否 | ≤3 项，0–10080 |
| repeatFreq | Enum? | 否 | DAILY/WEEKLY/WORKDAYS |
| repeatUntil | ISO date/datetime? | 条件 | 有 repeat 时与 count 至少一 |
| repeatCount | Int? | 条件 | 1–365 |

**成功 data**：完整 Event

**错误**：`VALIDATION_ERROR` / `INTERNAL`

---

### 5.3 `update_event`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| eventId | String | 是 | |
| scope | Enum | 否 | `THIS` \| `SERIES`；默认 `SERIES`（无重复时忽略） |
| occurrenceStartAt | ISO | 条件 | scope=THIS 时必填 |
| 其余可选字段 | | 否 | 同 create，只传要改的 |

**行为**：

- `SERIES`：更新父事件字段
- `THIS`：写/更 `event_exceptions`（改期）或仅改标题 override

**确认**：中风险 → diff

---

### 5.4 `delete_event`

| 参数 | 类型 | 必填 |
|------|------|------|
| eventId | String | 是 |
| scope | Enum | 否，默认 SERIES |
| occurrenceStartAt | ISO | 条件 THIS |

**行为**：SERIES → 软删父；THIS → `isDeletedInstance=true`

**确认**：高

---

### 5.5 `list_todos`

| 参数 | 类型 | 必填 |
|------|------|------|
| date | ISO date? | 否；本地日 |
| includeDone | Boolean | 否，默认 false |
| keyword | String? | 否 |

**返回**：Todo[]（无 due 且未请求 keyword 时可包含收件箱）

---

### 5.6 `create_todo`

| 参数 | 类型 | 必填 |
|------|------|------|
| title | String | 是 |
| dueAt | ISO? | 否 |
| notes | String? | 否 |
| reminderOffsets | Int[]? | 否；无 due 则忽略 |

**风险**：低

---

### 5.7 `update_todo`

| 参数 | 类型 | 必填 |
|------|------|------|
| todoId | String | 是 |
| title / dueAt / notes / reminderOffsets / isDone | 可选 | 只传变更项 |

**风险**：中（若仅 isDone 可按 complete 路径降低摩擦）

---

### 5.8 `complete_todo`

| 参数 | 类型 | 必填 |
|------|------|------|
| todoId | String | 是 |

**行为**：幂等完成；**低风险**

---

### 5.9 `delete_todo`

| 参数 | 类型 | 必填 |
|------|------|------|
| todoId | String | 是 |

**风险**：高；批量时合并确认

---

### 5.10 `list_courses`

| 参数 | 类型 | 必填 |
|------|------|------|
| teachingWeek | Int? | 与 date 二选一可空 |
| date | ISO date? | |
| semesterId | String? | 默认当前学期 |

**返回**：课程定义 + 该周/日投影摘要（occurrences）

---

### 5.11 `create_course`

| 参数 | 类型 | 必填 |
|------|------|------|
| name | String | 是 |
| semesterId | String? | 默认当前 |
| teacher / location | String? | 否 |
| weekday | 1–7 | 是 |
| periodStart / periodEnd | Int | 是 |
| weeksSpec | String | 是 |
| isBiweekly | Boolean | 否 |
| biweeklyOdd | Boolean? | 条件 |
| reminderOffsets / colorToken | | 否 |

**风险**：中（确认：课名、周次、节次、地点）

---

### 5.12 `update_course`

| 参数 | 类型 | 必填 |
|------|------|------|
| courseId | String | 是 |
| 其余字段 | 可选 | 同 create |

**确认**：diff；影响所有匹配周次——确认卡须提示「将影响第 X–Y 周」

---

### 5.13 `delete_course`

| 参数 | 类型 | 必填 |
|------|------|------|
| courseId | String | 是 |

**确认**：高；说明「之后不再出现在课表」

---

### 5.14 `get_semester`

| 参数 | 类型 | 必填 |
|------|------|------|
| semesterId | String? | 否；空=当前 |

**返回**：学期 + 节次列表 + 最大教学周估算

---

## 6. 端上执行时序（文字）

```text
用户输入
  → LLM（含工具定义）
  → tool_calls
  → ToolValidator（参数/D3）
  → 风险路由
       低：Repository 写/读 → 结果卡（可撤销）
       中：确认卡(diff) → 用户确认 → Repository → 结果卡
       高：确认卡(条目) → [批量则二次确认] → Repository → 结果卡
  → 工具结果回传模型（短摘要）→ 最终助手文案
```

失败：Validator 失败 → 错误回传模型一轮修复；用户取消 → `CANCELLED`，不写库。

---

## 7. 模型侧系统指令要点（非代码，供 Prompt 作者）

- 只使用列出的工具；不要编造 id。
- 时间必须解析为绝对本地时间再调用写工具。
- 不确定日期/对象 → 向用户提问，不要调用写工具。
- 创建后不要求用户再说一遍「好了吗」；以结果卡为准。
- 涉及删除时先 list 或使用用户原话中的明确对象。

---

## 8. 会话内指代（MVP）

- 同一会话保留最近 N 轮（建议 N≤10）用户消息 + 工具结果摘要。
- 「再加一个一样的，改到周五」→ 需能引用上一次 create_event 的结果 id。
- 跨会话不恢复指代。

---

## 9. 不在契约内

- 批量专用 delete_all 工具
- 导入导出
- 系统日历
- 流式 token API 细节（V1.1）

---

## 10. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-08-11 | 首版 14 工具契约 |
