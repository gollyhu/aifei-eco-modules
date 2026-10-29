# aifei-mq-demo 低保真 UI 原型（基线候选）

> 定位：**评审对象，非交付代码**。经门禁评审通过后锁定为基线，供架构师 / 实现层 / QA 对照施工与验收。
> 契约对齐：字段名与 `docs/aifei-mq-demo/prd.md` §5「Service 方法清单」严格一致。
> 前端形态：**Aifei 静态层**（原生 HTML/CSS/JS + 局部 AJAX + 本地全局 Vue，无构建工具），对齐 `aifei-websocket-demo`。

## 0. 全局基线

### 0.1 文件组织（按 Aifei 静态层设想，实际实现由前端工程师负责）

| 路径 | 内容 |
| --- | --- |
| `src/main/webapp/view/mq/index.html` | 实例入口页（路由 `GET /`） |
| `src/main/webapp/view/mq/demo.html` | 实例演示页（路由 `GET /mq/demo/page?instance=`） |
| `src/main/webapp/assets/css/mq-test.css` | 两页共用样式（沿用 `ws-test.css` 的 token 与组件类名） |
| `src/main/webapp/assets/js/mq-index.js` | 入口页脚本（只调 `/mq/demo/instances`） |
| `src/main/webapp/assets/js/mq-demo.js` | 演示页脚本（state / messages / subscribe / unsubscribe / send / clear） |
| `src/main/webapp/assets/lib/vue.global.prod.js` | 本地全局构建版 Vue（随工程放置，与 `ws-demo` 同源同版本） |

### 0.2 视觉语言（提取自 `ws-test.css`，不新造体系）

| Token | 取值 | 用途 |
| --- | --- | --- |
| `--c-bg` / `--c-surface` | `#f4f5f7` / `#ffffff` | 页面底色 / 卡片 |
| `--c-border` | `#dfe3e8` | 卡片描边、分隔线 |
| `--c-text` / `--c-text-muted` / `--c-text-faint` | `#1f2329` / `#6b7280` / `#9aa1a9` | 正文 / 次要 / 占位 |
| `--c-primary` | `#2563eb` | 主按钮（订阅、发送、进入） |
| `--c-recv` / `--c-send` | `#16a34a` / `#2563eb` | 日志方向：收 / 发 |
| `--c-err` / `--c-danger` | `#dc2626` | 错误态 / 危险按钮（清空、取消订阅） |
| `--c-open` / `--c-pending` / `--c-idle` | `#16a34a` / `#d97706` / `#6b7280` | 就绪 / 装配中 / 未装配 徽标 |
| `--r-md` / `--r-lg` / `--r-pill` | `8px` / `12px` / `999px` | 卡片 / 输入 / chip |
| `--fs-xs~xl` | 12 / 13 / 14 / 16 / 18px | 字号阶 |
| `--font-mono` | `Consolas, "Courier New"` | 时间戳、topic、payload、code |
| 页面骨架 | `topbar`（sticky） + `.layout`（左右 flex 5:?） + 卡片 `.card`（白底、1px 边框、12px 圆角、16px 内距） | 与 `ws-demo` 完全一致 |
| 断点 | `@media (max-width: 900px)` → 单列堆叠、整页滚动 | 与 `ws-demo` 一致 |

### 0.3 全局固定警示条（两页都出现）

```
⚠ 内存后端 · 同步投递 · 无真实 broker —— 消息不过进程边界，重启即清空；无 QoS / retain / 通配符
```

### 0.4 术语禁用表（门禁核对）

| 禁止出现 | 允许的替代表述 |
| --- | --- |
| 已连接 / 断开连接 / 重连 / 连接中 | 就绪 / 未装配 / 装配完成 |
| QoS、Retain、Keep Alive、证书、Client ID、主机、端口、路径 | （删除，不做灰置占位） |
| 发布成功已送达 broker | 已发送（同步投递，`delivered=n`） |
| 订阅成功（无补充） | 已订阅（**影响该实例的所有浏览器会话**） |

---

## 1. 页面一：实例入口页

- **页面名**：实例入口页
- **路由**：`GET /`（view：`view/mq/index.html`）
- **调用端点**：`GET /mq/demo/instances`（进入时请求一次；P1「刷新实例清单」按钮可重发）
- **数据规模**：3 张卡（`__MAIN__`、`demoA`、`demoB`）

### 1.1 布局线框（桌面）

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  aifei-mq-demo · MQ 演示入口                              [↻ 刷新实例清单]      │  ← topbar
│  配置来源：app-config.txt ｜ mq.type = memory ｜ mq.names = demoA,demoB   [说明▸]│
├────────────────────────────────────────────────────────────────────────────────┤
│  ⚠ 内存后端 · 同步投递 · 无真实 broker —— 重启即清空；无 QoS / retain / 通配符  │  ← 警示条
├────────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐  │
│  │ __MAIN__     [主实例]│  │ demoA                │  │ demoB                │  │
│  │ ──────────────────── │  │ ──────────────────── │  │ ──────────────────── │  │
│  │ 后端类型   memory    │  │ 后端类型   memory    │  │ 后端类型   memory    │  │
│  │ 配置前缀   mq.*      │  │ 配置前缀   mq.demoA.*│  │ 配置前缀   mq.demoB.*│  │
│  │ 状态       ● 就绪    │  │ 状态       ● 就绪    │  │ 状态       ● 就绪    │  │
│  │ 订阅主题   1         │  │ 订阅主题   0         │  │ 订阅主题   0         │  │
│  │ 消息记录   3 / 200   │  │ 消息记录   0 / 200   │  │ 消息记录   0 / 200   │  │
│  │ ──────────────────── │  │ ──────────────────── │  │ ──────────────────── │  │
│  │ [ 进入演示页 → ]     │  │ [ 进入演示页 → ]     │  │ [ 进入演示页 → ]     │  │
│  └──────────────────────┘  └──────────────────────┘  └──────────────────────┘  │
├────────────────────────────────────────────────────────────────────────────────┤
│  提示：每个实例是独立总线，同名 topic 在 demoA / demoB 之间互不可见。            │  ← 页脚
└────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 布局分块

| 区 | 名称 | 内容 |
| --- | --- | --- |
| ① | 顶栏 topbar | 标题、配置摘要（`mq.type` / `mq.names`）、刷新按钮、说明入口（P2 抽屉） |
| ② | 警示条 | 固定一句语义声明（§0.3） |
| ③ | 实例卡栅格 | `repeat(auto-fit, minmax(280px, 1fr))`，3 张卡；卡片整体可点 |
| ④ | 页脚 | 一句「实例互相隔离」提示 + 数据来源说明 |

### 1.3 控件清单

| 控件 | 类型 | 绑定字段（来自 `instances`） | 行为 |
| --- | --- | --- | --- |
| 实例名 | 文本 + 徽标 | `instanceName`（`__MAIN__` 时追加「主实例」pill） | — |
| 后端类型 | 键值对 | `type` | — |
| 配置前缀 | 等宽文本 | `configPrefix`（`mq` / `mq.demoA`），展示时补 `.*` | 悬停 title 提示「该实例配置走此前缀」 |
| 状态徽标 | chip | `ready === true` → 「● 就绪」绿；`false` → 「● 未装配」红灰 | 悬停 title：`producer` / `consumer` 是否可取用 |
| 订阅主题数 | 数值 | `subscribedTopicCount` | — |
| 消息记录 | 数值 | `messageLogSize` / `messageLogCapacity` | 显示 `3 / 200` |
| 进入演示页 | 主按钮 + 整卡点击 | 跳 `/mq/demo/page?instance=<instanceName>`（**绝对路由**，不可写相对 `demo.html`） | 见 §1.5 |
| 刷新实例清单 | 次级按钮 | 重发 `/mq/demo/instances` | 请求中禁用并显示「刷新中…」 |

### 1.4 三态文案

| 态 | 触发 | 呈现 |
| --- | --- | --- |
| **loading** | 首次请求未返回 | 3 个骨架卡（灰块占位）+ 顶栏右侧「加载实例清单…」；不显示空态文案 |
| **空态** | `code=0` 且 `instances` 为空（理论不可达，主实例恒存在） | 居中提示：「未发现任何 MQ 实例」/ 副行「请确认 `app-config.txt` 中已注册 `MqPlugin`，且 `mq.names` 配置正确」/ 按钮「重新加载」 |
| **错误态** | 请求失败 或 `code != 0` | 红色错误条：「读取实例清单失败：{msg}」+「重试」按钮；**保留**上一次成功数据（若曾成功），否则卡片区不渲染 |
| 降级态（P1 刷新失败） | 刷新时失败 | 顶部红条 + 旧数据灰化（`opacity .5`）+ 时间戳「数据时间 12:00:01」 |

### 1.5 交互流程

| 步骤 | 触发 | 调用 | 页面变化 |
| --- | --- | --- | --- |
| 1 | 打开 `/` | `GET /mq/demo/instances` | loading 骨架 → 渲染 3 卡 |
| 2 | 点击卡片任意处 / 「进入演示页」 | 无后端调用 | `location.href = '/mq/demo/page?instance=' + encodeURIComponent(instanceName)`（绝对路由；`__MAIN__` 同样显式带参，便于回传/分享） |
| 3 | 点击「刷新实例清单」 | `GET /mq/demo/instances` | 按钮禁用 → 卡片数据刷新 → 按钮恢复 |
| 4 | 键盘：`Tab` → 卡片内按钮 → `Enter` | 同上 | 焦点环 2px `--c-primary`；卡片整块可点但**只暴露一个可聚焦元素**（按钮），避免重复焦点 |

### 1.6 关键文案

| 位置 | 文案 |
| --- | --- |
| 标题 | `aifei-mq-demo · MQ 演示入口` |
| 副标题 | `配置来源：app-config.txt ｜ mq.type = memory ｜ mq.names = demoA,demoB` |
| 主实例徽标 | `主实例` |
| 状态 | `就绪` / `未装配` |
| 按钮 | `进入演示页 →` / `刷新实例清单` |
| 页脚 | `提示：每个实例是独立总线，同名 topic 在 demoA / demoB 之间互不可见。` |
| 空态 | `未发现任何 MQ 实例` + `请确认 app-config.txt 中已注册 MqPlugin，且 mq.names 配置正确` |
| 错误 | `读取实例清单失败：{msg}` + `重试` |

### 1.7 响应式

| 断点 | 行为 |
| --- | --- |
| > 1200px | 3 卡一行（`minmax(280px,1fr)`） |
| 900–1200px | 2 卡一行，第 3 张换行 |
| ≤ 900px | 单列堆叠；顶栏配置摘要折行，隐藏「说明▸」文字只留图标；整页滚动（`body` 可滚动，不锁视口高度） |

---

## 2. 页面二：实例演示页

- **页面名**：实例演示页
- **路由**：`GET /mq/demo/page?instance=demoA`（view 文件 `src/main/webapp/view/mq/demo.html`；`page()` 返回的模板名必须是 **`/mq/demo.html`**——模板根即 `webapp/view`，故不带 `view/` 前缀，但必须以 `/` 开头；`instance` 缺省 → `__MAIN__`）
- **调用端点**：`state` / `messages` / `subscribe` / `unsubscribe` / `send`（或 `send/message`）/ `messages/clear`，**每个都带 `instance`**
- **页面骨架**：`topbar`（实例信息条） + `.layout` 双列：左列「订阅卡 + 发布卡」，右列「消息流卡整列」（与 `ws-demo` 同构）

### 2.1 布局线框（桌面，双栏）

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ [← 返回实例列表]  实例：demoA │ 类型：memory │ 前缀：mq.demoA.* │ ● 就绪                │ ← ①
│ 订阅 2 主题 │ 发送 5 │ 接收 5 │ 记录 5/200 │ 最近错误 —                    [说明▸]     │
│ ⚠ 内存后端 · 同步投递 · 无真实 broker —— 消息不过进程边界，重启即清空                  │
├──────────────────────────────────────┬─────────────────────────────────────────────────┤
│ ② 订阅（影响该实例所有会话）          │ ④ 消息流                                        │
│ ┌ ⚠ 全局副作用 徽标 ────────────────┐ │ [☑ 自动滚动] [⏸ 暂停] [复制] [清空记录]         │
│ │ 只影响当前实例总线（不影响 demoB） │ │ 共 5 条 / 上限 200                               │
│ │ 影响该实例的所有浏览器会话         │ │ ────────────────────────────────────────────── │
│ └───────────────────────────────────┘ │ 12:03:11.482  ▼收  aifei.mq.demo.topic         │
│ Topic [ aifei.mq.demo.topic     ]      │              hello · 5B · PageDemoListener     │
│       [ 订阅 ]  [ 取消订阅 ]           │ 12:03:10.901  ▲发  aifei.mq.demo.topic         │
│ 已订阅主题：                           │              hello · messageId=5   delivered=1  │
│  [aifei.mq.demo.topic 装配期·不可取消]  │ 12:03:02.117  ▼收  demo.extra                  │
│  [demo.extra ×本页]                    │              hi                                  │
│  （无 chip 时：暂无已订阅主题）        │ …                                               │
│ 提示：主题按字符串精确匹配，无通配符   │ ────────────────────────────────────────────── │
├──────────────────────────────────────┤ 最新在上 · 发送行由本页记录，接收行来自后端      │
│ ③ 发布                                │                                                 │
│ Topic [ aifei.mq.demo.topic     ][默认]│                                                 │
│ 发送方式  (•) 文本捷径 send   ( ) Message 对象 send/message                              │
│ 消息体 [ hello                          ]                                                │
│        [ hello                          ]   ← textarea rows=4                            │
│ [ 发送 ]  [ 清空输入 ]                 │                                                 │
│ 回执：code=0 已发送到主题 aifei.mq.demo.topic                                            │
│       messageId=5  delivered=1  receivedCount=5                                          │
│ 提示：无订阅者时发送仍成功（delivered=0），消息被丢弃                                     │
└──────────────────────────────────────┴─────────────────────────────────────────────────┘
```

### 2.2 布局分块

| 区 | 名称 | 位置 | 内容 |
| --- | --- | --- | --- |
| ① | 实例信息条 topbar | sticky 顶部 | 返回入口页、实例名、`type`、`configPrefix`、状态徽标、订阅/发送/接收/记录计数、最近错误、说明入口；其下固定语义警示条 |
| ② | 订阅卡 | 左列上部 | 全局副作用徽标、Topic 输入、订阅/取消订阅按钮、已订阅 chip 列表、精确匹配提示 |
| ③ | 发布卡 | 左列下部 | Topic 输入 + 默认值按钮、发送方式单选、消息体 textarea、发送/清空、回执区、无订阅者提示 |
| ④ | 消息流卡 | 右列整列 | 工具条（自动滚动/暂停/复制/清空）、计数、日志行、页脚说明 |

### 2.3 控件清单（字段名与 PRD §5 对齐）

| 区 | 控件 | 类型 | 数据来源 / 参数 | 行为 |
| --- | --- | --- | --- | --- |
| ① | 返回实例列表 | 链接 | — | 回 `/`（**绝对路由**，`location.href = '/'`；不可写相对 `index.html`） |
| ① | 实例名 / 类型 / 前缀 | 文本 | `state.instance`（若未返回则用 URL 参数）、`state.mqType` | 前缀等宽显示 |
| ① | 状态徽标 | chip | `state.producerInjected && state.consumerInjected` → 就绪 / 未装配 | — |
| ① | 订阅主题数 | 数值 | `state.subscribedTopics.length` | 点击滚动到订阅卡 |
| ① | 发送 / 接收 | 数值 | `state.sentCount` / `state.receivedCount` | — |
| ① | 记录 n/200 | 数值 | `state.messageLogSize` / `state.messageLogCapacity` | 上限读接口，不硬编码 |
| ① | 最近错误 | 文本 + 时间 | 前端记录最近一次失败响应的 `msg` | 空为 `—`；title 显示完整文本 |
| ② | 全局副作用徽标 | 静态徽标（必出） | — | 文案见 §2.6；不可关闭 |
| ② | Topic 输入 | `input[type=text]` | 参数 `topic` | 后缀 `×` 清空；`Enter` = 订阅 |
| ② | 订阅 | 主按钮 | `GET /mq/demo/subscribe?instance=&topic=` | 见 §2.5 |
| ② | 取消订阅 | 次级/危险按钮 | `GET /mq/demo/unsubscribe?instance=&topic=` | 同上；Topic 为空时禁用 |
| ② | 已订阅 chip | chip + `×`（仅「本页」来源） | `state.subscribedTopics[]` + `state.subscribers[]` | 来源为「本页」的 chip 带 `×` = 用该 chip 的 topic 调 `unsubscribe`；来源为「装配期」的 chip **不提供 `×`**（置灰不可点，title：`装配期订阅由 DemoAppConfig.onStart() 注册，页面无法取消；如需取消请改代码后重启`） |
| ③ | Topic 输入 | `input[type=text]` | 参数 `topic`（默认 `aifei.mq.demo.topic`） | 「默认」小按钮填入默认主题（值来自页面常量，与 `DemoAppConfig.DEMO_TOPIC` 一致） |
| ③ | 发送方式 | 单选组（2 项） | 文本捷径 → `/mq/demo/send`；Message 对象 → `/mq/demo/send/message` | 切换不改变已输入内容 |
| ③ | 消息体 | `textarea rows=4` | 参数 `text` | `Ctrl/⌘ + Enter` 发送 |
| ③ | 发送 | 主按钮 | 依发送方式调对应端点 | 发送中禁用 + 文案「发送中…」 |
| ③ | 回执 | 只读块 | `code` / `msg` / `messageId` / `delivered` / `receivedCount` | 成功绿框、失败红框；`payloadSize` 在 Message 方式下额外显示 |
| ④ | 自动滚动 | 复选框 | — | 默认勾选；关闭后新记录不改变滚动位置 |
| ④ | 暂停 | 切换按钮 | — | 暂停轮询（只冻结渲染，不清数据）；文案在「暂停 / 继续」间切换 |
| ④ | 复制 | 次级按钮 | — | 复制日志纯文本；成功后按钮短暂变「已复制」 |
| ④ | 清空记录 | 危险按钮 + 二次确认 | `GET /mq/demo/messages/clear?instance=` | 确认文案：「将清空 demoA 的消息记录与发送/接收计数（所有浏览器会话都受影响），订阅不受影响。确定？」 |
| ④ | 日志行 | 列表项 | `messages[]`：`receivedAt` / `topic` / `text` / `payloadSize` / `subscriber` / `sequence` | 接收行 ▼收 绿色；发送行 ▲发 蓝色（前端本地） |

### 2.4 三态文案

| 区 | loading | 空态 | 错误态 |
| --- | --- | --- | --- |
| ① 信息条 | 各数值显示 `—`，徽标「○ 读取中」灰；不做整屏骨架（首屏先渲染框架） | 不适用 | 徽标「● 未装配」+ 红字 `读取状态失败：{msg}`；数值保持 `—`；每 2s 自动重试 |
| ② 订阅区 | 按钮禁用 + 「订阅中…」/「取消中…」 | `暂无已订阅主题。输入主题后点「订阅」——注意这会影响该实例的所有浏览器会话。` | 顶部红字 `订阅失败：{msg}`（如 `topic 不能为空`、`未知实例`）；失败后 Topic 输入内容保留 |
| ③ 发布区 | 按钮禁用 + 「发送中…」 | 不适用（始终有默认 topic/text） | 红框回执，直接展示后端 `code` + `msg`，如 `code=10001 topic 不能为空`、`code=90000 MQ 尚未装配完成…` |
| ④ 消息流 | 首次拉取前显示 3 行灰条骨架 | `暂无消息。在上方发布区发一条，或订阅一个已有消息的主题。` | 日志区保留旧数据 + 顶部红条 `读取消息记录失败：{msg}（已降频至 5 秒重试）` |

### 2.5 交互流程（点击 → 端点 → 页面变化）

| # | 触发 | 调用 | 页面变化 |
| --- | --- | --- | --- |
| 1 | 打开页面 | `GET /mq/demo/state?instance=` + `GET /mq/demo/messages?instance=` | 信息条填充；日志渲染；启动 2s 轮询 |
| 2 | 点「订阅」 | `GET /mq/demo/subscribe?instance=&topic=` | 成功：回执区绿字「已订阅 {topic}（影响该实例的所有浏览器会话）」+ 订阅 chip 追加（标记「本页」，带 `×`）+ 信息条订阅数 +1；失败：红字 `订阅失败：{msg}`，输入保留 |
| 3 | 点**「本页」来源** chip 的 `×` / 点「取消订阅」（Topic 同 chip） | `GET /mq/demo/unsubscribe?instance=&topic=` | 成功：chip 移除 + 提示「已取消订阅 {topic}（影响该实例的所有浏览器会话）」；**来源为「装配期」的 chip 无 `×`**，不可从页面取消（点「取消订阅」按钮对装配期主题会因未登记而返回 `code=10001`「未登记该主题的页面订阅」，页面如实展示该错误） |
| 4 | 点「发送」 | `GET /mq/demo/send`（或 `/send/message`）`?instance=&topic=&text=` | 发送中禁用 → 回执区显示 `code`/`msg`/`messageId`/`delivered`/`receivedCount` → 日志区**本页追加一行 ▲发**（不参与后端轮询）→ 立即触发一次 `messages` 拉取，把 ▼收 行补上；若 `delivered=0`，回执下方灰字提示「该主题当前无订阅者，消息已被丢弃（发送仍成功）」 |
| 5 | 轮询（每 2s，可暂停） | `GET /mq/demo/messages?instance=` | 渲染最新记录；若自动滚动开且用户未上滚 → 滚动到顶（最新在上）；连续 3 次失败 → 间隔 5s + 红条 |
| 6 | 点「清空记录」并确认 | `GET /mq/demo/messages/clear?instance=` | 日志清空 → 空态文案 → 信息条「记录 0/200、发送 0、接收 0」；提示「已清空 demoA 的记录与计数（所有浏览器会话都受影响），订阅保留」 |
| 7 | 点「返回实例列表」 | — | `location.href = '/'`（绝对路由；不保留本页状态） |
| 8 | URL 传入非法 instance | 首个请求返回 `code=10001` | 整页降级：信息条实例名显示 URL 值 + 红字「未知实例：{name}」+ 主区禁用（按钮全 disabled）+ 按钮「返回实例列表」；**不自动跳转**，避免用户看不到错误原因 |

### 2.6 关键文案（中文，全部直接可用）

| 位置 | 文案 |
| --- | --- |
| 标题（`document.title`） | `MQ 演示 · {instance}` |
| 返回链接 | `← 返回实例列表` |
| 状态徽标 | `● 就绪` / `○ 读取中` / `● 未装配` |
| 警示条 | `⚠ 内存后端 · 同步投递 · 无真实 broker —— 消息不过进程边界，重启即清空` |
| 订阅卡标题 | `订阅` |
| **全局副作用徽标** | `⚠ 影响所有浏览器会话（全局副作用）` |
| 副作用说明 | `订阅与取消订阅只作用于当前实例的总线（demoA 的操作不影响 demoB），但会影响访问同一实例的所有浏览器会话。` |
| 订阅按钮 | `订阅` / `订阅中…` |
| 取消订阅按钮 | `取消订阅` / `取消中…` |
| chip 来源标记 | `装配期` / `本页`；来源「装配期」的 chip 无 `×`，title：`装配期订阅由 DemoAppConfig.onStart() 注册，页面无法取消；如需取消请改代码后重启` |
| 订阅成功 | `已订阅 {topic}（影响该实例的所有浏览器会话）` |
| 取消成功 | `已取消订阅 {topic}（影响该实例的所有浏览器会话）` |
| 订阅空态 | `暂无已订阅主题。输入主题后点「订阅」——注意这会影响该实例的所有浏览器会话。` |
| 主题匹配提示 | `提示：主题按字符串精确匹配，无通配符（+ / #）` |
| 发布卡标题 | `发布` |
| 发送方式标签 | `文本捷径 send` / `Message 对象 send/message` |
| 发送按钮 | `发送` / `发送中…` |
| 无订阅者提示 | `该主题当前无订阅者，消息已被丢弃（发送仍成功）` |
| 重复记录提示（Q1） | `该主题已由装配期监听器订阅，本次订阅会使同一消息被记录多次` |
| 回执标题 | `回执` |
| 消息流标题 | `消息流` |
| 日志工具 | `自动滚动` / `暂停` / `继续` / `复制` / `已复制` / `清空记录` |
| 日志计数 | `共 {n} 条 / 上限 {capacity}` |
| 日志空态 | `暂无消息。在上方发布区发一条，或订阅一个已有消息的主题。` |
| 日志页脚 | `最新在上 · 发送行由本页记录，接收行来自后端` |
| 清空二次确认 | `将清空 {instance} 的消息记录与发送/接收计数（所有浏览器会话都受影响），订阅不受影响。确定？` |
| 读取状态失败 | `读取状态失败：{msg}` |
| 未知实例 | `未知实例：{name}` |
| 未装配 | `MQ 尚未装配完成，请确认已注册 MqPlugin` |

### 2.7 响应式

| 断点 | 行为 |
| --- | --- |
| > 900px | 双栏：左列（订阅 + 发布，`flex: 5 1 0`）／右列（消息流整列，`overflow-y:auto` 独立滚动）；页面 `height:100vh; overflow:hidden` |
| 768–900px | 单列堆叠：信息条 → 订阅 → 发布 → 消息流；解除视口锁定（`height:auto`），整页滚动；消息流最小高度 320px |
| < 768px | 单列；信息条指标折成 2 行（实例名独占一行）；订阅/发送按钮宽度 100%；日志行改为两行式（首行 时间 + 方向 + topic，次行 payload）；chip 换行排列 |

### 2.8 与契约的对齐核对（门禁用）

| 页面元素 | 对应端点 / 参数 | 对齐状态 |
| --- | --- | --- |
| 实例名 / type / 前缀 / 就绪 / 计数 | `GET /mq/demo/state?instance=` → `mqType` / `producerInjected` / `consumerInjected` / `sentCount` / `receivedCount` / `messageLogSize` / `messageLogCapacity` / `subscribedTopics` | ✔ |
| 已订阅 chip | `state.subscribedTopics[]` + `state.subscribers[]`（含来源） | ✔（来源字段需后端补充，见 PRD Q1）；来源「装配期」的 chip 无 `×`（页面无法取消，见 PRD §7 第 14 条） |
| 订阅 / 取消订阅 | `GET /mq/demo/subscribe`、`GET /mq/demo/unsubscribe`（`instance` + `topic`） | ✔（`unsubscribe` 的 listener 由后端登记表取回，见 PRD Q2）；装配期订阅**不登记进页面登记表**，故对其调 `unsubscribe` 返回 `code=10001`，不在 UI 承诺成功 |
| 发送（两种方式） | `GET /mq/demo/send`、`GET /mq/demo/send/message`（`instance` + `topic` + `text`） | ✔ |
| 日志区接收行 | `GET /mq/demo/messages?instance=` → `receivedAt` / `topic` / `text` / `payloadSize` / `subscriber` / `sequence` | ✔（接收侧无 `messageId`，已在 UI 中不显示） |
| 清空记录 | `GET /mq/demo/messages/clear?instance=` | ✔ |
| 入口页卡片 | `GET /mq/demo/instances` → `instanceName` / `type` / `main` / `configPrefix` / `ready` / `subscribedTopicCount` / `messageLogSize` / `messageLogCapacity` | ✔ |
| 页面路由 | 入口 `GET /`、演示 `GET /mq/demo/page?instance=`；页内跳转一律**绝对路由**（`/`、`/mq/demo/page?instance=`） | ⚠ 待实测（`@Path("/")` 映射根路径的写法待架构师确认，见 PRD Q4）；`page()` 返回模板名 **`/mq/demo.html`**（须以 `/` 开头，否则被 targetPath 二次拼接成 `/mq/demo/mq/demo.html`，见 PRD §5.2） |

---

## 基线评审

- 评审人：QA 工程师 严过关（独立评审，非产出方）
- 评审对象：`prd.md`（v1）+ `ui-prototype.md`（v1，基线候选）
- 评审标尺：用户原话 + 已拍板 2 项（订阅控件保留且须标注全局副作用 / 入口页 3 张实例卡）+ 官方仓库源码交叉核对
- 轮次：第 1 轮（硬上限 2 轮）

### 一、三维结论表

| 维度 | 结论 | 依据摘要 |
| --- | --- | --- |
| 完整性 | ✅ PASS | 两页均齐备七要素：布局线框、布局分块、控件清单、三态文案、关键文案、交互流程（点击→端点→页面变化）、响应式（入口页 §1.1–§1.7；演示页 §2.1–§2.7）。参考站点四大核心功能区全部处置完毕：连接参数卡→**重定义**为「实例信息条」（§2.2 ①）、订阅区→**保留**且带全局副作用徽标（§2.2 ②、§2.6）、发布区→**保留**并拆「文本捷径 / Message 对象」两条代码路径（§2.2 ③）、消息流→**保留**且区分收发来源（§2.2 ④）；明确**删除**项（QoS / retain / 通配符 / Keep Alive / Client ID / 用户名密码 / 连接态 / 主机端口路径）在 §0.4 术语禁用表与 §2.6 文案中无残留。逐项处置表见 `prd.md` §6.2，无遗漏 |
| 可实施性 | ❌ NO | 端点 ↔ 控件映射完整、`instance` 默认 `__MAIN__` 两端一致（见下方核对表，均为正例）；但存 1 条 P0 + 4 条 P1 硬伤：导航相对路径、view 返回值前缀、装配期 chip 取消承诺、实例隔离与全局单例矛盾未点明、总线唯一性措辞 |
| 一致性 / 真实性 | ❌ NO | **硬否决项未触发**：全文无「假装有 broker / 网络连接态 / QoS / retain / 通配符 / 异步确认 / 服务端持久化」的描述；「内存后端 · 同步投递 · 无真实 broker」固定警示条已在两页就位（§0.3、§1.1 第 68 行、§2.1 第 162 行、§2.6）。但 §2.6「订阅作用于进程内唯一总线」与 §1.6「每个实例是独立总线」自相矛盾（P1-3），属失实陈述 |

**端点 ↔ 控件逐条核对（正例，均一致）**

| 端点 | 消费控件 / 位置 | 核对 |
| --- | --- | --- |
| `GET /mq/demo/instances` | §1.3 全部卡片字段 +「刷新实例清单」 | ✅ 字段逐字对齐 `prd.md` §5.2 |
| `GET /mq/demo/state?instance=` | §2.3 ① 信息条全部指标 | ✅（`instance` 字段名见 P2-2） |
| `GET /mq/demo/messages?instance=` | §2.3 ④ 日志行 + 计数 | ✅ 六个字段与 `DemoMessageLog.record()` 一致，接收行不显示 messageId 亦正确 |
| `GET /mq/demo/subscribe?instance=&topic=` | §2.3 ② 订阅按钮 | ✅ 签名与 `Consumer.subscribe(String, MessageListener)` 一致 |
| `GET /mq/demo/unsubscribe?instance=&topic=` | §2.3 ② 取消订阅按钮 + chip `×` | ✅ 端点形态正确（chip `×` 的语义见 P1-2） |
| `GET /mq/demo/send`、`/send/message` | §2.3 ③ 发送方式单选 + 发送按钮 | ✅ 参数 `topic`/`text` 与 `MqDemoService` 现值一致 |
| `GET /mq/demo/messages/clear?instance=` | §2.3 ④ 清空记录 + 二次确认 | ✅ |
| `GET /`、`GET /mq/demo/page` | §1 / §2 页面本身 | ⚠️ 路由形态可行（`@Path("/")` 先例），但 view 返回值见 P1-1、跳转见 P0-1 |

### 二、缺陷清单

| 编号 | 严重度 | 一句话缺陷 | 证据 |
| --- | --- | --- | --- |
| P0-1 | P0 | 入口页→演示页、演示页→入口页两处跳转写成相对路径，与本文档自身的路由契约冲突，两条主链路必然 404 | `ui-prototype.md:122`、`:202` vs 路由契约 `:57`、`:152` |
| P1-1 | P1 | 演示页 view 返回值未以 `/` 开头，`IoHandler` 会再拼一次类前缀，模板被解析成 `/mq/demo/mq/demo.html` 而找不到 | `prd.md:108`；`IoHandler.java:88-92`、`:153-161`；`MqDemoService.java:36`；原型过早标 ✔ 于 `ui-prototype.md:300` |
| P1-2 | P1 | 「装配期」chip 的取消订阅被承诺为成功，实际为静默 no-op，页面会给出假成功回执 | `ui-prototype.md:173`、`:240`、`:260`；`Consumer.java:12-13`、`:29-38`；`DemoAppConfig.java:64`；`prd.md:185`（Q1 默认值） |
| P1-3 | P1 | 「订阅作用于进程内唯一总线」与「每实例独立总线」自相矛盾，3 实例场景下属失实陈述 | `ui-prototype.md:166`、`:257` vs `:82`、`:135`；`MqPlugin.java:85-91`；`prd.md:141` |
| P1-4 | P1 | 「消息记录按实例隔离」与现有全局静态 `DemoMessageLog` 的冲突未被显式指出，且 PRD 把现状写成「上限 200/实例」 | `prd.md:86`、`:141`；`DemoMessageLog.java:35`、`:45-75`、`:88-96` |
| P2-1 | P2 | chip 的「装配期 / 本页」来源字段不在任何端点契约内（可由 `subscribers[].name` 推导，不阻塞） | `ui-prototype.md:294`；`prd.md:95` |
| P2-2 | P2 | `state.instance` 与 `instances[].instanceName` 命名不统一，且 `state` 返回项未列该字段 | `ui-prototype.md:203`、`:294`；`prd.md:95`、`:105` |
| P2-3 | P2 | 枚举实例依赖库内部包 `cn.hg.aifei.mq.internal.MqConfig`，而 demo 已持有 `Prop` 可自行切分 | `prd.md:139`；`MqConfig.java:7`、`:24`；`DemoAppConfig.java:59`、`:74` |
| P2-4 | P2 | P0-10 要求「每页每区三态齐全」，原型对「实例信息条」「发布区」标注空态「不适用」，口径需同步 | `prd.md:56`；`ui-prototype.md:229`、`:231` |

#### P0-1 导航相对路径与路由契约冲突（必修）

- 事实：入口页 URL 为 `GET /`，相对 `demo.html` 解析为 `/demo.html`——无此路由、无此静态文件，命中 `IoHandler` 的 404 分支；演示页 URL 为 `GET /mq/demo/page`，相对 `index.html` 解析为 `/mq/demo/index.html`，同样不存在。参照工程一律用绝对路径（`aifei-websocket-demo/src/main/webapp/view/ws/index.html:7` 的 `/assets/css/ws-test.css`）。
- 可直接采纳的修正文字：
  - §1.5 步骤 2 的页面变化列改为：`location.href = '/mq/demo/page?instance=' + encodeURIComponent(instanceName)`（`__MAIN__` 同样显式带参）。
  - §2.3 ① 行「返回实例列表」的数据来源列改为：`/`（绝对路径，`location.href = '/'`）。
  - §2.5 步骤 7 的页面变化列改为：`location.href = '/'`（不保留本页状态）。

#### P1-1 演示页 view 返回值必须以 `/` 开头（必修）

- 事实：`IoHandler` 把「以 `.html` 结尾的 String 返回值」当 view（`IoHandler.java:88-92`），并在 view 不以 `/` 开头时拼上 action 的 targetPath（`:153-161`，仅 targetPath 为 `/` 时不拼）。`MqDemoService` 类前缀是 `/mq/demo`（`MqDemoService.java:36`），故 `prd.md:108` 写的「返回 view 路径 `mq/demo.html`」按字面实现会变成 `/mq/demo/mq/demo.html`，enjoy 找不到模板；`ui-prototype.md:300` 提前标 ✔ 属过早确认。
- 可直接采纳的修正文字（PRD §5.2 `page` 行「返回类型」列改为）：
  `String`（返回 **`/mq/demo.html`**——`@Path("/mq/demo")` 下返回值必须以 `/` 开头，否则会被 targetPath 前缀二次拼接成 `/mq/demo/mq/demo.html`）
- 备选：把 `page()` 移入 `@Path("/")` 的 `MqPageService`，与 `aifei-websocket-demo` 的 `DemoPageService` 同构（`DemoPageService.java:14-22`）。

#### P1-2 「装配期」chip 的取消订阅是假承诺（必修）

- 事实：`Consumer.unsubscribe` 按 listener **实例身份**移除，未登记即幂等静默 no-op（`Consumer.java:12-13`、`:29-38`）；装配期订阅用的 listener 是 `DemoAppConfig.java:64` 的 `final MessageListener listener = new DemoMessageListener()`（包级可见，页面 Service 在另一个包），而 `prd.md:185`（Q1 默认）又选定「页面用独立 listener」——用页面 listener 去取消装配期主题必然是静默 no-op，但原型 `:240` 会提示「已取消装配期订阅，重启应用可恢复」，`:173` 还给了可点的 `×`。这违反「语义不撒谎」的产品目标。
- 可直接采纳的修正文字：
  - §2.3 ②「已订阅 chip」行为列补：来源为「装配期」的 chip **不提供 `×`**（置灰，title：`装配期订阅由 DemoAppConfig.onStart() 注册，页面无法取消；如需取消请改代码后重启`）。
  - §2.5 步骤 3 删去「若该主题为装配期订阅，提示『已取消装配期订阅，重启应用可恢复』」整句。
- 备选（二选一，不要都做）：由 `prd.md` 明确「装配期 listener 实例必须登记进订阅登记表，使页面可取消装配期订阅」，并同步 §7 已知限制第 9 条。

#### P1-3 「进程内唯一总线」失实（必修，纯文案）

- 事实：`mq.names` 每实例各建一条总线（`MqPlugin.java:85-91`：`for (String instanceName : instanceNames())` 内逐实例 `provider.createBus(config)`），3 实例即 3 条总线；与 `prd.md:141`「每实例独立 `MemoryMessageBus`」一致，却与原型 §2.6 的「进程内唯一总线」冲突，并与 §1.6「每个实例是独立总线」自相矛盾。
- 可直接采纳的修正文字（§2.6「副作用说明」与 §2.2 ② 徽标说明统一改为）：
  `订阅与取消订阅只作用于当前实例的总线（demoA 的操作不影响 demoB），但会影响访问同一实例的所有浏览器会话。`
- 徽标文案 `⚠ 影响所有浏览器会话（全局副作用）` 保持不变（用户已拍板）。

#### P1-4 实例隔离与全局静态 `DemoMessageLog` 的冲突未显式指出（必修，改 PRD 文字）

- 事实：`DemoMessageLog` 全静态单份——`MAX_ENTRIES = 200` 为全局共享上限（`:35`），`ENTRIES` / `SUBSCRIBED_TOPICS` / `SUBSCRIBER_NAMES` / `sentCount` / `receivedCount` 全为 `static`、私有构造（`:45-75`），`markStartup` 只保存 1 个主题且 `clear()` 清空全部（`:88-96`、`:208-214`）。因此 `prd.md:86` 的「环形、上限 200/实例」与源码不符（现为全局一份 200），而 `prd.md:141` 只以「必须同样分片」一句带过，未点明这是**重构现有全局单例**、也未说明 `markStartup` 的单主题结构撑不起页面订阅登记。这正是「需求与既有实现相矛盾」被淡化之处。
- 可直接采纳的修正文字（PRD §4「理由」列改为）：
  `本 demo 无表、无持久化：pom.xml 无 JDBC 驱动与连接池，app-config.txt 无 jdbcUrl，消息记录走进程内内存 DemoMessageLog。**现值是全静态单份**：MAX_ENTRIES=200 为全部实例共享的上限，ENTRIES、sentCount、receivedCount 与装配快照（SUBSCRIBED_TOPICS / SUBSCRIBER_NAMES）均为 static 字段，且 markStartup 只保存 1 个主题——这与 P0-9「按实例隔离」直接冲突，**必须重构为按实例分片**（每实例独立记录、计数与订阅登记，容量仍取 200/实例），应用重启即清空。不编造任何表。`
- 同时建议在 §7 追加第 13 条：`13. **demo 侧记录当前是单份全局状态**：DemoMessageLog 全静态、容量 200 为共享上限，实现 P0-9 需将其重构为按实例分片；重构前 demoA 与 demoB 会看到同一份记录。`

### 三、主观 / 未知区（不伪装为客观结论）

- [主观/未知区] 双栏信息架构（左「订阅 + 发布」、右「消息流整列」）与 `ws-demo` 同构，主观判断合理；观感与布局取舍仍需用户最终确认，门禁不替用户拍板。
- [主观/未知区] 参考站点要素的「保留 / 重定义 / 删除」处置表写在 `prd.md` §6.2 而非原型内，原型只呈现结果；判定为「无遗漏」，但归属位置属编辑性偏好，不计缺陷。
- [未知区] 入口页 `GET /` 是否可由 `@Path("/")` 的无 `@Path` 方法直接映射，需实现期实测（`DemoPageService.java:14-22` 为同构先例）；`prd.md` Q4 已声明待实测，本轮不计缺陷。
- [未知区] 「复制日志」依赖 `navigator.clipboard`：`http://localhost` 属安全上下文可用，若改用局域网 IP 访问需降级路径（P1-5 已要求「复制失败时提示手动选择」），不阻塞。

### 四、结论

- 计数：P0 = 1 条，P1 = 4 条（超过 2 条上限），P2 = 4 条。
- 硬否决项：**未触发**（无假装 broker / 连接态 / QoS / retain / 通配符 / 异步确认 / 服务端持久化的描述；三词警示条已在两页固定就位）。
- 处置：回退 **产品经理 许清楚** 迭代（第 2 轮）。修掉 P0-1 与 P1-1 ~ P1-4 即可通过复审；P2 四条不阻塞，可同轮顺手改。
- 复审时只校验上述 5 条改动点，不重开全文评审。

`BASELINE: NO`

---

## 基线复审（第 2 轮）

- 复审人：QA 工程师 严过关（独立复审，非产出方）｜轮次：第 2 轮（硬上限 2 轮）
- 范围：**仅校验第 1 轮点名的 P0-1、P1-1、P1-2、P1-3、P1-4 五处是否真闭环**，不重开全文评审、不扩大范围
- 对象：`prd.md`（改后）+ `ui-prototype.md`（改后；上方 `## 基线评审` 一节保持原样，本轮未改动）
- 方法：逐条读文档取证 + 回源码复核事实，**不采信「已修改」声明**

### 一、五条逐条闭环核对

| # | 修正点 | 结论 | 证据（行号） |
| --- | --- | --- | --- |
| P0-1 | 双向跳转全部改绝对路由 | ✅ **闭环** | `ui-prototype.md:105`（`/mq/demo/page?instance=<instanceName>`，并注明不可写相对 `demo.html`）、`:122`（`location.href = '/mq/demo/page?instance=' + encodeURIComponent(instanceName)`）、`:202`（回 `/`，`location.href = '/'`）、`:244`（`location.href = '/'`）、`:300`（契约核对行同步）；`prd.md:50`、`:51`、`:131`（含「不得写 `demo.html` / `index.html`」的禁止性说明） |
| P1-1 | view 返回值 `/mq/demo.html`、`@Path("/")` 下 `/mq/index.html` | ✅ **闭环**（事实独立复核成立，见 §三） | `ui-prototype.md:152`（`page()` 返回模板名必须是 `/mq/demo.html`，模板根即 `webapp/view`）、`:300`（✔ 已改为 ⚠ 待实测）；`prd.md:108`、`:109`、`:132` |
| P1-2 | 装配期 chip 不提供取消入口 | ✅ **闭环** | `ui-prototype.md:172`（线框「装配期·不可取消」）、`:213`（「装配期」chip **不提供 `×`**，置灰 + title 说明由 `DemoAppConfig.onStart()` 注册、页面无法取消）、`:240`（取消只对「本页」；对装配期主题调 `unsubscribe` 返回 `code=10001`「未登记该主题的页面订阅」并如实展示错误）、`:260`、`:294`、`:295`；`prd.md:52`、`:107`、`:186`（§7 新增第 14 条）。全文无「已取消装配期订阅」类残留承诺 |
| P1-3 | 「订阅作用于进程内唯一总线」改「影响该实例的所有浏览器会话」 | ✅ **闭环** | `ui-prototype.md:50`、`:164`、`:166`、`:167`、`:230`、`:239`、`:257`、`:261`、`:262`、`:263` 均为「该实例的所有浏览器会话」，`:257` 副作用说明已含「只作用于当前实例的总线（demoA 的操作不影响 demoB）」；`prd.md:178`、`:179`。除上方第 1 轮评审节的引用外，正文全文**已无**「唯一总线 / 进程内唯一」表述 |
| P1-4 | 显式写「现状全局单例」+ 容量口径分开 | ✅ **闭环** | `prd.md:86`（「**现值是全静态单份**……改造后每实例 200 条，现状共 200 条」）、`:146`（库层隔离成立，但 demo 侧须重构，标注「这是本需求要新增的改造点，方案由架构师决定」）、`:167`（结论同口径）、`:185`（§7 第 13 条「当前是单份全局状态」）；`prd.md:178`、`:179` 作用域口径一致 |

### 二、P0-1 漏网扫描（专项）

- `href="` 全文 **0 处**（页内跳转一律走 `location.href`，无 `<a href>` 裸相对跳转）。
- `location.href` 共 3 处：`:122`、`:202`、`:244`，**全部以 `/` 开头**（绝对路由）。
- `demo.html` / `index.html` 剩余出现位置逐处判定：`:13`、`:14`、`:57`、`:152`、`prd.md:125`、`:126` = **静态文件路径**（`src/main/webapp/view/...`），非跳转；`:105`、`:202`、`prd.md:50`、`:51`、`:131` = **禁止性说明**（明令「不可写相对 `demo.html` / `index.html`」）；`:300` = 契约核对行。
- 结论：**无漏网的裸相对路径跳转**。

### 三、P1-1 事实独立复核（源码级，不采信文档自述）

| 待验事实 | 复核结论 | 源码依据 |
| --- | --- | --- |
| 模板根 = `webapp/view` | ✅ 成立 | `DemoAppConfig.java:77` → `new IoHandler(printAction, "view")`；`IoHandler.java:42-52` → `engine.setBaseTemplatePath(webRoot.resolve("view"))`；故模板名 `mq/demo.html` 对应文件 `webapp/view/mq/demo.html`，**不带 `view/` 前缀正确** |
| view 不以 `/` 开头会被前置 targetPath | ✅ 成立 | `IoHandler.java:153-161`：`!view.startsWith("/")` 时，`targetPath.length() != 1` → `view = targetPath + "/" + view` |
| `@Path("/mq/demo")` 下必须写 `/mq/demo.html` | ✅ 成立 | `MqDemoService.java:36` 为 `@Path("/mq/demo")` ⇒ 裸写 `mq/demo.html` 会变成 `/mq/demo/mq/demo.html`（enjoy 找不到模板） |
| `@Path("/")` 下 `/mq/index.html` 正确且与不加 `/` 等价 | ✅ 成立 | `IoHandler.java:155-156`（`length()==1` 时仅补一个 `/`）；同构先例 `DemoPageService.java:14-22` 返回 `"ws/index.html"` → 实际渲染 `/ws/index.html` |

- 判定：P1-1 的结论与写法**均成立**，`prd.md:108` / `:109` / `:132` 与 `ui-prototype.md:152` 描述准确；`:300` 从 ✔ 降为 ⚠ 待实测处理得当（`@Path("/")` 能否直接映射 `GET /` 仍需实现期实测，属 [未知区]，`prd.md:133`/Q4 已声明）。

### 四、格式复核

| 项 | 结果 |
| --- | --- |
| 行尾 | 两文件 CR 数 = LF 数（`prd.md` 199/199、`ui-prototype.md` 397/397），**无裸 LF、无孤立 CR** ⇒ 全 CRLF |
| 编码 | UTF-8 严格解码通过，**无 BOM** |
| `## 基线评审` 节 | `:304` 起至原 `BASELINE: NO` 段落内容与第 1 轮完全一致，**未被篡改** |

### 五、结论

- 5 条修正点**全部闭环**：P0-1 ✅、P1-1 ✅（含源码级事实复核）、P1-2 ✅、P1-3 ✅、P1-4 ✅；未发现新增硬伤，**硬否决项未触发**（无假装 broker / 连接态 / QoS / retain / 通配符 / 异步确认 / 服务端持久化；三词警示条仍在两页固定就位）。
- 遗留（**不阻塞锁定**，交架构师实现期落定）：[未知区] `@Path("/")` 能否直接映射 `GET /`（`prd.md:133` / Q4 已声明待实测）。第 1 轮 P2-1 ~ P2-4 未改，均不阻塞。
- [主观/未知区] 双栏信息架构与观感仍属主观判断，本轮不重复评述；建议用户对关键交付形态做最终确认。
- 处置：**PASS**，可锁定基线（UI 原型 + PRD）并进入架构设计。

`BASELINE: PASS`
