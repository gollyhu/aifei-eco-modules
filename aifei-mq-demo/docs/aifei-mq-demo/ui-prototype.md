# aifei-mq-demo 页面原型

> 契约对齐：字段名与 `docs/aifei-mq-demo/prd.md` §5「Service 方法清单」严格一致。
> 前端形态：**Aifei 静态层**（原生 HTML/CSS/JS + 局部 AJAX + 本地全局 Vue，无构建工具）。

## 0. 全局基线

### 0.1 文件组织

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

### 0.3 全局警示条（两页都出现）

按当前实例真实后端生成，不写死实例名：

```
⚠ 多后端演示 · 当前实例 <instanceName> 为内存后端（进程内、重启即清空） / 走 <type> · 各实例独立总线
```

内存实例标注「内存后端（进程内、重启即清空）」，其它实例标注实际后端类型（当前为 `mqtt` / `redisstream` / `mqtt5`）。

### 0.4 术语禁用表

| 禁止出现 | 允许的替代表述 |
| --- | --- |
| 已连接 / 断开连接 / 重连 / 连接中 | 就绪 / 未装配 / 装配完成 |
| QoS、Retain、Keep Alive、证书、Client ID、主机、端口、路径 | 页面不提供该控件 |
| 发布成功已送达 broker | 已发送（`delivered=n`） |
| 订阅成功（无补充） | 已订阅（**影响该实例的所有浏览器会话**） |

---

## 1. 页面一：实例入口页

- **页面名**：实例入口页
- **路由**：`GET /`（view：`view/mq/index.html`）
- **调用端点**：`GET /mq/demo/instances`（进入时请求一次；P1「刷新实例清单」按钮可重发）
- **数据规模**：4 张卡（`__MAIN__`、`demoA`、`demoB`、`demoC`）

### 1.1 布局线框（桌面）

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  aifei-mq-demo · MQ 演示入口                              [↻ 刷新实例清单]      │  ← topbar
│  配置来源：app-config.txt ｜ mq.type = memory ｜ mq.names = demoA,demoB,demoC [说明▸]│
├────────────────────────────────────────────────────────────────────────────────┤
│  ⚠ 多后端演示 · 当前实例为内存后端 / 走 mqtt · 各实例独立总线                    │  ← 警示条
├────────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐  │
│  │ __MAIN__     [主实例]│  │ demoA                │  │ demoB                │  │demoC│
│  │ ──────────────────── │  │ ──────────────────── │  │ ──────────────────── │  │
│  │ 后端类型   memory    │  │ 后端类型   mqtt      │  │ 后端类型   redisstream│  │mqtt5│
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
| ③ | 实例卡栅格 | `repeat(auto-fit, minmax(280px, 1fr))`，4 张卡；卡片整体可点 |
| ④ | 页脚 | 一句「实例互相隔离」提示 + 数据来源说明 |

### 1.3 控件清单

| 控件 | 类型 | 绑定字段（来自 `instances`） | 行为 |
| --- | --- | --- | --- |
| 实例名 | 文本 + 徽标 | `instanceName`（`__MAIN__` 时追加「主实例」pill） | — |
| 后端类型 | 键值对 | `type`（`memory` / `mqtt` / `redisstream` / `mqtt5`） | — |
| 配置前缀 | 等宽文本 | `configPrefix`（`mq` / `mq.demoA`），展示时补 `.*` | 悬停 title 提示「该实例配置走此前缀」 |
| 状态徽标 | chip | `ready === true` → 「● 就绪」绿；`false` → 「● 未装配」红灰 | 悬停 title：`producer` / `consumer` 是否可取用 |
| 订阅主题数 | 数值 | `subscribedTopicCount` | — |
| 消息记录 | 数值 | `messageLogSize` / `messageLogCapacity` | 显示 `3 / 200` |
| 进入演示页 | 主按钮 + 整卡点击 | 跳 `/mq/demo/page?instance=<instanceName>`（**绝对路由**，不可写相对 `demo.html`） | 见 §1.5 |
| 刷新实例清单 | 次级按钮 | 重发 `/mq/demo/instances` | 请求中禁用并显示「刷新中…」 |

### 1.4 三态文案

| 态 | 触发 | 呈现 |
| --- | --- | --- |
| **loading** | 首次请求未返回 | 4 个骨架卡（灰块占位）+ 顶栏右侧「加载实例清单…」；不显示空态文案 |
| **空态** | `code=0` 且 `instances` 为空（理论不可达，主实例恒存在） | 居中提示：「未发现任何 MQ 实例」/ 副行「请确认 `app-config.txt` 中已注册 `MqPlugin`，且 `mq.names` 配置正确」/ 按钮「重新加载」 |
| **错误态** | 请求失败 或 `code != 0` | 红色错误条：「读取实例清单失败：{msg}」+「重试」按钮；**保留**上一次成功数据（若曾成功），否则卡片区不渲染 |
| 降级态（P1 刷新失败） | 刷新时失败 | 顶部红条 + 旧数据灰化（`opacity .5`）+ 时间戳「数据时间 12:00:01」 |

### 1.5 交互流程

| 步骤 | 触发 | 调用 | 页面变化 |
| --- | --- | --- | --- |
| 1 | 打开 `/` | `GET /mq/demo/instances` | loading 骨架 → 渲染 4 卡 |
| 2 | 点击卡片任意处 / 「进入演示页」 | 无后端调用 | `location.href = '/mq/demo/page?instance=' + encodeURIComponent(instanceName)`（绝对路由；`__MAIN__` 同样显式带参，便于回传/分享） |
| 3 | 点击「刷新实例清单」 | `GET /mq/demo/instances` | 按钮禁用 → 卡片数据刷新 → 按钮恢复 |
| 4 | 键盘：`Tab` → 卡片内按钮 → `Enter` | 同上 | 焦点环 2px `--c-primary`；卡片整块可点但**只暴露一个可聚焦元素**（按钮），避免重复焦点 |

### 1.6 关键文案

| 位置 | 文案 |
| --- | --- |
| 标题 | `aifei-mq-demo · MQ 演示入口` |
| 副标题 | `配置来源：app-config.txt ｜ mq.type = memory ｜ mq.names = demoA,demoB,demoC` |
| 主实例徽标 | `主实例` |
| 状态 | `就绪` / `未装配` |
| 按钮 | `进入演示页 →` / `刷新实例清单` |
| 页脚 | `提示：每个实例是独立总线，同名 topic 在 demoA / demoB 之间互不可见。` |
| 空态 | `未发现任何 MQ 实例` + `请确认 app-config.txt 中已注册 MqPlugin，且 mq.names 配置正确` |
| 错误 | `读取实例清单失败：{msg}` + `重试` |

### 1.7 响应式

| 断点 | 行为 |
| --- | --- |
| > 1200px | 4 卡一行（`minmax(280px,1fr)`） |
| 900–1200px | 2~3 卡一行，其余换行 |
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
│ [← 返回实例列表]  实例：demoA │ 类型：mqtt │ 前缀：mq.demoA.* │ ● 就绪                  │ ← ①
│ 订阅 2 主题 │ 发送 5 │ 接收 5 │ 记录 5/200 │ 最近错误 —                    [说明▸]     │
│ ⚠ 多后端演示 · 当前实例为内存后端 / 走 mqtt · 各实例独立总线                            │
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
│ ⑤ 事件分发 E2（固定 topic + payload.type 路由）                                        │
│ 业务域 [order-events ▾]  类型 [CREATE ▾]  bizId [A1001] [发送事件]                       │
│ 命中统计：域 / 类型 / 次数（独立监听器计数，不进右侧消息流） [清空统计]                   │
└──────────────────────────────────────┴─────────────────────────────────────────────────┘
```

### 2.2 布局分块

| 区 | 名称 | 位置 | 内容 |
| --- | --- | --- | --- |
| ① | 实例信息条 topbar | sticky 顶部 | 返回入口页、实例名、`type`、`configPrefix`、状态徽标、订阅/发送/接收/记录计数、最近错误、说明入口；其下固定语义警示条 |
| ② | 订阅卡 | 左列上部 | 全局副作用徽标、Topic 输入、订阅/取消订阅按钮、已订阅 chip 列表、精确匹配提示 |
| ③ | 发布卡 | 左列下部 | Topic 输入 + 默认值按钮、发送方式单选、消息体 textarea、发送/清空、回执区、无订阅者提示 |
| ④ | 消息流卡 | 右列整列 | 工具条（自动滚动/暂停/复制/清空）、计数、日志行、页脚说明 |
| ⑤ | E2 事件分发卡 | 左列最下 | 业务域下拉、类型下拉、`bizId` 输入、发送按钮、命中统计表与清空统计按钮 |

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
| ⑤ | 业务域 / 类型 / `bizId` | 下拉 + 文本输入 | `GET /mq/demo/event/send`（`domain` / `type` / `bizId`） | 候选来自 `event/dispatch`；`bizId` 默认 `A1001` |
| ⑤ | 发送事件 | 主按钮 | `GET /mq/demo/event/send?instance=&domain=&type=&bizId=` | 发送中禁用 + 文案「发送中…」；事件不进消息流 |
| ⑤ | 命中统计表 | 只读表格 | `GET /mq/demo/event/dispatch?instance=` | 列：域 / 类型 / 命中次数；`unknownCount` / `totalReceived` 可见 |
| ⑤ | 清空统计 | 危险按钮 + 二次确认 | `GET /mq/demo/event/dispatch/clear?instance=` | 仅清空当前实例统计 |
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
| ⑤ E2 区 | 按钮禁用 + 「发送中…」 | 命中统计表显示空态 | 红框提示后端 `code` + `msg`，保留上次统计 |
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
| 9 | 点「发送事件」 | `GET /mq/demo/event/send?instance=&domain=&type=&bizId=` | 发送中禁用 → 成功提示 → 立即刷新命中统计；事件不进消息流 |
| 10 | 点「清空统计」并确认 | `GET /mq/demo/event/dispatch/clear?instance=` | 命中统计归零 → 空态文案 |

### 2.6 关键文案（中文，全部直接可用）

| 位置 | 文案 |
| --- | --- |
| 标题（`document.title`） | `MQ 演示 · {instance}` |
| 返回链接 | `← 返回实例列表` |
| 状态徽标 | `● 就绪` / `○ 读取中` / `● 未装配` |
| 警示条 | `⚠ 多后端演示 · <按实例后端生成> · 各实例独立总线` |
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
| 重复记录提示 | `该主题已由装配期监听器订阅，本次订阅会使同一消息被记录多次` |
| 回执标题 | `回执` |
| 消息流标题 | `消息流` |
| 日志工具 | `自动滚动` / `暂停` / `继续` / `复制` / `已复制` / `清空记录` |
| 日志计数 | `共 {n} 条 / 上限 {capacity}` |
| 日志空态 | `暂无消息。在上方发布区发一条，或订阅一个已有消息的主题。` |
| 日志页脚 | `最新在上 · 发送行由本页记录，接收行来自后端` |
| E2 标题 | `事件分发（E2：固定 topic + payload 内 type）` |
| E2 说明 | `E2：固定 topic + payload 内 type 路由键 —— 分发在消费侧，不在 broker` |
| E2 提示 | `本区事件不进消息流（E2 有独立监听器，不写消息记录）；在订阅区手动订阅 order-events 会造成双记录` |
| E2 按钮 | `发送事件` / `发送中…` / `清空统计` |
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

### 2.8 与契约的对齐核对

| 页面元素 | 对应端点 / 参数 | 对齐状态 |
| --- | --- | --- |
| 实例名 / type / 前缀 / 就绪 / 计数 | `GET /mq/demo/state?instance=` → `mqType` / `producerInjected` / `consumerInjected` / `sentCount` / `receivedCount` / `messageLogSize` / `messageLogCapacity` / `subscribedTopics` | ✔ |
| 已订阅 chip | `state.subscribedTopics[]` + `state.subscribers[]`（含来源） | ✔（来源字段由后端补充）；来源「装配期」的 chip 无 `×`（页面无法取消，见 PRD §7 第 14 条） |
| 订阅 / 取消订阅 | `GET /mq/demo/subscribe`、`GET /mq/demo/unsubscribe`（`instance` + `topic`） | ✔（`unsubscribe` 的 listener 由后端登记表取回）；装配期订阅**不登记进页面登记表**，故对其调 `unsubscribe` 返回 `code=10001`，不在 UI 承诺成功 |
| 发送（两种方式） | `GET /mq/demo/send`、`GET /mq/demo/send/message`（`instance` + `topic` + `text`） | ✔ |
| 日志区接收行 | `GET /mq/demo/messages?instance=` → `receivedAt` / `topic` / `text` / `payloadSize` / `subscriber` / `sequence` | ✔（接收侧无 `messageId`，已在 UI 中不显示） |
| 清空记录 | `GET /mq/demo/messages/clear?instance=` | ✔ |
| E2 事件发送 / 命中统计 / 清空 | `GET /mq/demo/event/send`、`/event/dispatch`、`/event/dispatch/clear`（`instance` + `domain` / `type` / `bizId`） | ✔（命中统计由独立监听器计数，不写消息记录） |
| 入口页卡片 | `GET /mq/demo/instances` → `instanceName` / `type` / `main` / `configPrefix` / `ready` / `subscribedTopicCount` / `messageLogSize` / `messageLogCapacity` | ✔ |
| 页面路由 | 入口 `GET /`、演示 `GET /mq/demo/page?instance=`；页内跳转一律**绝对路由**（`/`、`/mq/demo/page?instance=`） | ✔（`GET /` 由入口 Service 映射）；`page()` 返回模板名 **`/mq/demo.html`**（须以 `/` 开头，否则被 targetPath 二次拼接成 `/mq/demo/mq/demo.html`，见 PRD §5.2） |
