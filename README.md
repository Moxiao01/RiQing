# 日清（RiQing）

Android 本地优先的日历 / 课表 / 待办 + App 内 Agent。**当前版本：V1.0.0**（见 [Releases](https://github.com/Moxiao01/RiQing/releases)）。

## 功能总览（V1.0）

- **今日 / 日历 / 课表 / Agent / 我的** 五个 Tab：议程聚合视图、月历、分单双周课表、AI 助手。
- **课程管理**：手动添加编辑，或用**截图一键导入**（端侧离线中文 OCR，模型内置 APK，不依赖网络）。
- **待办与日程**：待办勾选、日程提醒（精确闹钟 + 通知渠道），创建类操作 10 分钟内可撤销（快照）。
- **AI Agent**：OpenAI 兼容接口（可配 Endpoint / Key / Model），14 个结构化工具，高风险操作需确认。
- **桌面小组件（V1.0 新增）**：今日日程、下一节课、待办列表（支持在小组件上直接勾选）、周课表四件套；按下一节课时间自动定时刷新，开机 / 时间变更 / 应用更新后自动同步，适配深色模式。
- **主题设置（V1.0 新增）**：跟随系统 / 强制浅色 / 强制深色。
- **本地优先**：数据全部存于本机（Room + DataStore），无账号、无云端同步。

## 工程结构

```text
app/                  # 主界面、导航、桌面小组件（widget/）
core/model/           # 领域模型
core/common/          # 时间/周次/教学周工具
core/database/        # Room 实体与 DAO
core/datastore/       # 设置（AI 配置、主题等）
core/data/            # Repository + Agenda 投影 + 校验
core/ai/              # OpenAI 兼容客户端 + 14 工具
core/notify/          # 通知渠道与提醒调度
core/designsystem/    # Material3 主题与通用组件
docs/                 # 规格文档 01–08
```

## 环境与构建

- JDK 17
- Android SDK 36
- AGP 8.9 / Kotlin 2.1 / Gradle 8.11（wrapper）

> 注意：Android 工具链对**非 ASCII 路径**不友好。若工程位于 `E:\日清\`，请先拷到纯英文路径（如 `E:\RiQingBuild`）再编译。

```powershell
# Debug 包
gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release 包（签名）
gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

**Release 签名**：构建脚本会读取仓库根目录的本地 `keystore.properties`（已 gitignore）：

```properties
storeFile=dist/riqing-release.keystore
storePassword=你的密码
keyAlias=riqing
keyPassword=你的密码
```

该文件不存在时会正常产出未签名包，因此克隆仓库后可直接构建 Debug 包。

单元测试：

```powershell
gradlew :core:common:test :core:data:test
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
3. 底栏五个 Tab：今日 / 日历 / 课表 / Agent / 我的；「我的 → 主题设置」切换浅色 / 深色。
4. 长按桌面空白处添加小组件：今日日程 / 下一节课 / 待办 / 周课表。
5. Agent 中高风险操作会弹确认卡；创建类结果可在 10 分钟内撤销（快照）。
