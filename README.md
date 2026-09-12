# 日清（RiQing）

Android 本地优先日历 / 课表 / 待办 + App 内 Agent。

## 工程结构

```text
app-src/
  app/                 # 主界面与导航
  core/model/          # 领域模型
  core/common/         # 时间/周次/教学周工具
  core/database/       # Room 实体与 DAO
  core/datastore/      # 设置（AI 配置等）
  core/data/           # Repository + Agenda 投影 + 校验
  core/ai/             # OpenAI 兼容客户端 + 14 工具
  core/notify/         # 通知渠道与提醒调度
  core/designsystem/   # Material3 主题
```

## 环境

- JDK 17
- Android SDK 36
- AGP 8.9 / Kotlin 2.1

## 构建

> 注意：Android 工具链对**非 ASCII 路径**不友好。若工程位于 `E:\日清\`，请先拷到纯英文路径（如 `E:\RiQingBuild`）再编译，或在 `gradle.properties` 保留 `android.overridePathCheck=true`（部分环境仍会失败）。

```powershell
# 使用本机 Gradle（示例：9.x）
cd E:\RiQingBuild   # 或英文路径下的工程根
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

已产出的 Debug 包：`dist/riqing-debug.apk`（约 19MB）。

单元测试：

```powershell
gradle :core:common:test :core:data:test
```

## 对齐的规格文档

实现对应 `docs/01`–`08`：

| 能力 | 文档 |
|------|------|
| 术语/边界 | D1 |
| 分表 + 投影 + 软删撤销 | D2 |
| 字段校验 | D3 |
| Agent 14 工具 | D4 |
| 页面 IA | D5 |
| 时间约束（ISO 本地） | D6 |
| 通知渠道 | D7 |
| 验收路径 | D8 |

## 使用说明

1. 首次进入「我的 → 学期与节次」配置 week1Monday（须周一）与节次。
2. 「我的 → AI 配置」填写 OpenAI 兼容 Endpoint / Key / Model，先「连接测试」。
3. 底栏五个 Tab：今日 / 日历 / 课表 / Agent / 我的。
4. Agent 中高风险操作会弹确认卡；创建类结果可在 10 分钟内撤销（快照）。
