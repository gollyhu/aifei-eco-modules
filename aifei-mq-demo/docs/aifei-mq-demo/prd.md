# aifei-mq-demo 演示页面 PRD

> 范围：`aifei-mq-demo` 示例工程的演示页面与多实例入口，不含库模块变更。

## 1. 项目信息

| 项 | 内容 |
| --- | --- |
| Language | 简体中文 |
| Programming Language | Java 8 + Maven + Aifei（Just Service + HIO + Undertow） |
| 项目名 | `aifei_mq_demo` |
| 模块性质 | **示例工程**（不发布），为演示页面，不是库能力扩展 |
| 前端形态 | **Aifei 静态层**：原生 HTML/CSS/JS + AJAX + 本地全局构建版 Vue，**无构建工具**（对齐 `aifei-websocket-demo`） |
| 端口 | HTTP **18100**（`start-demo.bat` 的 `-Dundertow.port=18100`），与 socket/sentinel demo 错开 |

`aifei-mq-demo` 提供测试演示页面（页面功能与布局参考 `https://www.bchrt.com/tools/mqtt-websocket-client-test-tool/`），并提供按配置 `mq.type` / `mq.names` 声明的多个实例列表示例入口页，点击后进入各自的演示页。

当前配置为 4 个实例：主实例 `__MAIN__`（`mq.type = memory`）与 `mq.names = demoA,demoB,demoC` 三个命名实例，后端分别为 `mqtt` / `redisstream` / `mqtt5`。`pom.xml` 已引入全部 7 个后端子模块，`mq.type` 可切到任一已注册类型。

演示页提供「订阅 / 取消订阅」输入框与按钮，调用 `Consumer.subscribe(topic, listener)` / `Consumer.unsubscribe(topic, listener)`；UI **必须显式标注「影响所有浏览器会话（全局副作用）」**。实例由 `mq.names` 声明，入口页按声明列出实例卡：`__MAIN__`（主实例，走根级 `mq.*`）与每个命名实例（各走 `mq.<name>.*`）。

## 2. 产品定义

### 2.1 产品目标（3 个，正交）

1. **闭环可点**：把「装配 → 发送 → 订阅消费」从 curl 变成浏览器里点得动、看得见、可重复的现场，一个页面完成发送与观察。
2. **多实例可见**：让 `mq.names` 声明的多实例能力可视化——入口页一屏看清 4 个实例（含 4 种后端）各自的类型与就绪状态，进入后每个实例是**互相隔离**的独立现场。
3. **语义不撒谎**：警示条按当前实例真实后端生成（`memory` 标「内存后端 · 进程内 · 重启即清空」，其它后端标实际类型），**不假装**有连接态、QoS、retain、异步确认。

### 2.2 用户故事

| # | 作为 | 我希望 | 以便 |
| --- | --- | --- | --- |
| U1 | 库维护者（演示给他人看） | 打开 `http://localhost:18100/` 就看到 4 张实例卡，点进去直接能发消息、看回执 | 30 秒内讲清「装配 → 发送 → 订阅消费」闭环，不用开终端 |
| U2 | 库维护者（验证多实例） | 在 `demoA` 页面发一条消息，`demoB` 页面完全看不到 | 直观证明每个实例是独立总线、消息不串实例 |
| U3 | 使用者（想试订阅行为） | 在页面上输入一个主题并点「订阅」，看消息是否被记录 | 不下代码就能验证 `subscribe` 的效果，并清楚知道它影响所有人 |
| U4 | 使用者（排查） | 看到「已发送 / 接收 / 记录条数 / 上限 200」与最近错误 | 判断「是没订阅、还是发送失败、还是记录被清空」 |
| U5 | 使用者（演示前重置） | 一键清空**当前实例**的记录与计数，其他实例不受影响 | 干净地重新演示一遍 |

## 3. 需求池

### P0（必须有）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P0-1 | 配置 `mq.names = demoA,demoB,demoC` | `app-config.txt` 含该键；应用启动成功；`/mq/demo/instances` 返回 4 项（`__MAIN__`、`demoA`、`demoB`、`demoC`，顺序为「主实例在最前，其余按声明顺序」） |
| P0-2 | 实例枚举端点 `/mq/demo/instances` | 每个实例返回：`instanceName` / `type` / `main`(bool) / `configPrefix`(`mq`、`mq.demoA`) / `ready`(producer、consumer 是否可取用) / `subscribedTopicCount` / `messageLogSize` / `messageLogCapacity`；HTTP 200，`code=0`；`type` 分别为 `memory` / `mqtt` / `redisstream` / `mqtt5` |
| P0-3 | 基础端点全部支持 `instance` 参数 | 省略时取默认值 `__MAIN__`；`instance` 非法/未注册返回 `code=10001` 且 `msg` 含「未知实例」与可用实例名清单 |
| P0-4 | 入口页（4 张实例卡） | 每卡显示 **实例名 / 后端类型 / 状态**（+ 配置前缀、订阅主题数、记录条数）；点击卡片或「进入演示页」**以绝对路由**跳 `/mq/demo/page?instance=<instanceName>`（不得写相对 `demo.html`）；空清单时显示空态文案 |
| P0-5 | 演示页顶部「实例信息条」 | 显示当前实例名、`mq.type`、配置前缀、就绪状态、订阅主题数、发送/接收计数、记录 `n/200`；含「← 返回实例列表」（**绝对路由**回 `/`，不得写相对 `index.html`） |
| P0-6 | 演示页「订阅区」 | Topic 输入 + 「订阅」+「取消订阅」按钮；已订阅主题以 chip 列出（**本页订阅**的 chip 可取消；**装配期订阅**的 chip 只读、不提供取消入口，见 §7 第 14 条）；**必须**显式标注「订阅 / 取消订阅影响该实例的所有浏览器会话（全局副作用）」 |
| P0-7 | 演示页「发布区」 | Topic 输入（默认值 = `DEMO_TOPIC`）+ 发送方式二选一（**文本捷径** `/send` / **Message 对象** `/send/message`）+ 消息体 textarea + 「发送」；发送后展示回执：`code` / `msg` / `messageId` / `delivered` / `receivedCount` |
| P0-8 | 演示页「消息流」日志区 | 轮询 `/mq/demo/messages?instance=`；字段：时间戳（`HH:mm:ss.SSS`）/ 方向 / topic / 文本 / payloadSize / subscriber；**最新在上**；显示「共 n 条 / 上限 200」；「自动滚动」开关 / 「清空记录」按钮 |
| P0-9 | **消息记录按实例隔离** | `demoA` 的记录不出现在 `demoB` 的介绍页/日志区；`/messages/clear?instance=demoA` 只清 `demoA`；发送/接收计数、订阅登记同样按实例隔离（`DemoMessageLog` 按实例分片，每片 `MAX_ENTRIES=200`） |
| P0-10 | 三态齐全 | 每页每区都有**空态 / loading 态 / 错误态**（具体文案见 `ui-prototype.md`） |
| P0-11 | 语义标注 | 警示条按当前实例后端生成：内存实例显示「内存后端（进程内、重启即清空）」，其它实例显示实际后端类型；统一标注「多后端演示 · 各实例独立总线」；**不得**出现「已连接 / 断开连接 / 重连 / QoS / retain / 证书」等词 |
| P0-12 | 静态层组织 | 页面在 `src/main/webapp/view/mq/`，样式脚本在 `src/main/webapp/assets/{css,js}/`；无构建工具、无 npm、无 Vite |
| P0-13 | E2 事件分发卡 | 演示页提供独立 E2 区块：业务域（`order-events` / `pay-events`）、事件类型（`payload.type`）、`bizId`（默认 `A1001`）；调 `/mq/demo/event/send` 发送；事件不进右侧消息流，由独立监听器计数 |
| P0-14 | E2 分发统计 | 调 `/mq/demo/event/dispatch` 展示按业务域与事件类型的命中次数（域 / 类型候选 + 逐行命中明细）；`/mq/demo/event/dispatch/clear` 清空统计；统计按实例隔离 |

### P1（应该有）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P1-1 | 日志方向区分 | 接收行来自 `/messages`（▼收）；发送行是**前端本地记录**（▲发），且页面上注明「发送行由本页记录，不来自后端」 |
| P1-2 | 已订阅主题「来源」标注 | chip 上区分「装配期」（`onStart` 订阅）与「本页订阅」，便于解释「为什么我没订阅也有记录」 |
| P1-3 | 轮询降频 | 连续 3 次请求失败后间隔由 2s 降为 5s，恢复成功后回到 2s；错误在信息条以红字显示「最近错误」 |
| P1-4 | 主题快捷填充 | 提供「填入演示默认主题」小按钮（值 = `aifei.mq.demo.topic`） |
| P1-5 | 复制日志 / 复制回执 | 一键复制当前日志文本；复制失败时提示「复制失败，请手动选择」 |
| P1-6 | 入口页「刷新实例清单」 | 点击重新请求 `/mq/demo/instances`，卡片数据刷新，失败显示错误条并保留旧数据 |

### P2（可以有）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P2-1 | 实例卡显示最近活动时间 | 由 `instances` 返回或前端记录；无数据显示 `—` |
| P2-2 | 消息体历史（↑↓ 调出） | 单页内保留最近 50 条，刷新页面即清空 |
| P2-3 | 日志按方向过滤 | 「全部 / 仅接收 / 仅发送」分段按钮 |
| P2-4 | 页脚说明抽屉 | 端点速查表 + 多后端语义说明（对齐 `ws-demo` 的抽屉做法） |

## 4. 表结构草案 —— **不适用**

| 项 | 结论 |
| --- | --- |
| 表结构 / DDL | **不适用** |
| Generator / Model | **不适用** |
| 理由 | 本 demo **无表、无持久化**：`pom.xml` 无 JDBC 驱动/连接池，`app-config.txt` 无 `jdbcUrl`，消息记录走进程内内存 `DemoMessageLog`。`DemoMessageLog` 按实例分片（`SHARDS`），每片独立保存记录与收发计数、各有 `MAX_ENTRIES=200` 上限；订阅登记见 `DemoSubscriptions`，后端类型与就绪状态见 `MqInstanceCatalog`。应用重启即清空。**不编造任何表。** |
| 排序白名单 | 不适用（无 `#orderBy`）；日志排序固定为「最新在上」，由 `/messages` 返回顺序决定 |

## 5. Service 方法清单

### 5.1 基础端点（5 个，全部 GET，返回 `Out`；HTTP 恒 200，`code=0` 为成功）

| Service 类 | 路由前缀 | 方法 | 参数 | 返回 | 变更 |
| --- | --- | --- | --- | --- | --- |
| `MqDemoService` | `/mq/demo` | `state` | — | `Out`（`mqType`/`subscribedTopics`/`subscribers`/`sentCount`/`receivedCount`/`messageLogSize`/`messageLogCapacity`/`producerInjected`/`consumerInjected`） | **扩展**：+ `instance` |
| | | `send` | `topic`(默认 `DEMO_TOPIC`)、`text`(默认 `hello`) | `Out`（`topic`/`messageId`/`text`/`delivered`/`deliveredRefTopic`/`receivedCount`） | **扩展**：+ `instance` |
| | | `sendMessage` (`/send/message`) | 同上 | `Out`（较上多 `payloadSize`） | **扩展**：+ `instance` |
| | | `messages` | — | `Out`（`count`/`maxEntries`/`messages[]`；每条 `sequence`/`topic`/`subscriber`/`text`/`payloadSize`/`receivedAt`） | **扩展**：+ `instance` |
| | | `clearMessages` (`/messages/clear`) | — | `Out` | **扩展**：+ `instance`；语义改为「只清该实例」 |

### 5.2 其余端点

| Service 类 | 路由前缀 | 方法 | 参数 | 返回类型 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `MqDemoService` | `/mq/demo` | `instances` | — | `Out` | 实例清单：`count` + `instances[]`（`instanceName`/`type`/`main`/`configPrefix`/`ready`/`subscribedTopicCount`/`messageLogSize`/`messageLogCapacity`）。顺序：`__MAIN__` 在首，其后按 `mq.names` 声明顺序 |
| | | `subscribe` | `instance`(默认 `__MAIN__`)、`topic`（必填） | `Out` | 调 `Consumer.subscribe(topic, listener)`；返回最新订阅清单 `subscribedTopics[]`。`topic` 空白 → `10001`；实例未知 → `10001`；总线已停止 → `90000` |
| | | `unsubscribe` | `instance`(默认 `__MAIN__`)、`topic`（必填） | `Out` | 调 `Consumer.unsubscribe(topic, listener)`；返回最新订阅清单。**约束**：`unsubscribe` 按**监听器实例身份**移除，必须用登记时同一个 `listener` 实例，否则是幂等静默 no-op（页面上看起来「点了取消没反应」）→ 需保留登记表。**装配期订阅不在该登记表内**（其 listener 由 `DemoAppConfig.onStart()` 创建），对装配期主题调用一律返回 `code=10001`「未登记该主题的页面订阅」，**不得**静默成功或提示「已取消」 |
| | | `page` | — | `String`（返回 **`/mq/demo.html`**——`@Path("/mq/demo")` 下返回值必须以 `/` 开头，否则会被 targetPath 前缀二次拼接成 `/mq/demo/mq/demo.html`；模板根即 `webapp/view`，故不带 `view/` 前缀） | 演示页视图。`?instance=` **仅由前端 JS 读取**，服务端不参与渲染 |
| | | `event/send` | `instance`、`domain`（默认 `order-events`）、`type`、`bizId`（默认 `A1001`） | `Out` | 发送 E2 事件（固定域 topic + payload 内 `type` 路由键） |
| | | `event/dispatch` | `instance` | `Out` | E2 分发命中统计（`domainTopics` / `typeOptions` / `defaultDomain` / `defaultType` / `totalReceived` / `unknownCount` / `rows`） |
| | | `event/dispatch/clear` | `instance` | `Out` | 清空该实例的 E2 分发统计 |
| `RootPageService` | `/` | `index` | — | `String`（返回 **`/demo/redirect.html`**，跳转至演示主页面 `GET /mq/demo/page`） | 入口路由，访问 `http://localhost:18100/` |

**`instance` 参数语义（统一约定）**

| 项 | 约定 |
| --- | --- |
| 默认值 | `__MAIN__`（主实例，配置走根级 `mq.*`）—— **不带参数即旧行为，向后兼容** |
| 合法值 | `__MAIN__` + `mq.names` 声明的命名实例名（`demoA`、`demoB`、`demoC`） |
| 非法值 | 未注册名 / 空串 / 含 `.`、`=`、`:` 或空白 → `code=10001`，`msg` 含「未知实例」+ 可用实例清单 |
| 未装配 | MQ 未装配完成（`MqHolder` 未初始化）→ `code=90000` |
| 可见性 | 每实例独立总线：同 topic 名在 `demoA` 与 `demoB` 上互不可见、互不投递；若不同实例指向同一外部 broker，同一 topic 仍可能同时被两端收到 |

### 5.3 页面路由（静态层）

| 页面 | 路由 | view 文件 | 说明 |
| --- | --- | --- | --- |
| 实例入口页 | `GET /` | `src/main/webapp/view/mq/index.html` | 4 张实例卡（经 `/demo/redirect.html` 跳转进入） |
| 实例演示页 | `GET /mq/demo/page?instance=demoA` | `src/main/webapp/view/mq/demo.html` | `instance` 缺省时前端按 `__MAIN__` 处理 |

> **路由与跳转约定**
>
> 1. 页面路由只有两个：入口页 `GET /`、演示页 `GET /mq/demo/page?instance=`；实例由查询参数区分。
> 2. **页内跳转一律写绝对路由**：入口页 → `location.href = '/mq/demo/page?instance=' + encodeURIComponent(instanceName)`；演示页 → `location.href = '/'`。**不得**写 `demo.html` / `index.html` 这类相对路径（会分别解析成 `/demo.html`、`/mq/demo/index.html`，两个 URL 都不存在 → 404）。
> 3. **Service 返回的 view 模板名统一以 `/` 开头**（`/mq/index.html`、`/mq/demo.html`）：`IoHandler.handleEnjoyView` 在 view 不以 `/` 开头时会前置 action 的 targetPath（`@Path("/mq/demo")` 下会变成 `/mq/demo/mq/demo.html`）；模板根为 `webapp/view`，故不带 `view/` 前缀。
> 4. 入口页 `GET /` 由 `@Path("/")` 类级路径 + 无 `@Path` 的 `index()` 方法直接映射（`aifei-mq-demo/src/main/java/cn/hg/aifei/mq/demo/service/RootPageService.java`），`index()` 返回跳转页 `/demo/redirect.html`，再跳转至演示主页面 `GET /mq/demo/page`。

## 6. 实例枚举来源与语义映射

### 6.1 实例枚举来源

| 事实 | 出处 | 对本需求的影响 |
| --- | --- | --- |
| 主实例登记名恒为 `__MAIN__`，属保留名，不需在 `mq.names` 声明 | `MqConfig.MAIN_INSTANCE_NAME` | 入口页固定 1 张主实例卡 |
| `mq.names` 逗号分隔，逐项去空白；空项/非法字符/保留名（`type`、`names`）/重名即**启动失败** | `MqConfig.parseInstanceNames` | `demoA,demoB,demoC` 合法；配置错则应用起不来，页面无「部分实例」状态 |
| 命名实例前缀为 `mq.<name>`，类型键 `mq.<name>.type`，未配置时默认为 `memory` | `MqConfig.parse` / `prefixOf` | 当前 `__MAIN__` = `memory`、`demoA` = `mqtt`、`demoB` = `redisstream`、`demoC` = `mqtt5`；类型显式写出便于演示 |
| `MqPlugin` 的实例名清单方法 `instanceNames()` 是 **private**，`MqHolder` **不提供枚举 API** | `MqPlugin` / `MqHolder` | demo 侧要列实例，只能**自己重解析 `mq.names`**（`MqConfig.parseInstanceNames(p.getProperties(), MqConfig.NAMES_KEY)`）再拼 `__MAIN__` |
| `ConsumerKit.use(name)` 按名取实例，未注册抛 `IllegalArgumentException`，未初始化抛 `IllegalStateException` | `ConsumerKit` / `MqHolder.lookup` | `ready` 判定可直接以「能否取到 producer/consumer」为依据 |
| 每实例独立 `MemoryMessageBus`（各自的 `subscriptions` 与 `messageId` 自增） | `MemoryMessageBus` / `MqPlugin.start` | 「按实例隔离」**在库层**成立（`start()` 内逐实例 `createBus`）；demo 侧 `DemoMessageLog` 已按实例分片（见 §4），记录/计数按实例隔离；订阅登记见 `DemoSubscriptions` |
| `MessageBus` 只有 `producer()` / `consumer()` / `close()`，**无法枚举主题或订阅者** | `MessageBus` | 页面的「已订阅主题 chip 列表」**只能由 demo 侧自维护**（含装配期订阅），不能从库查 |
| `Consumer.unsubscribe(topic, listener)` 需传 listener **实例身份**；重复取消/取消未登记订阅为静默 no-op | `Consumer` | 取消订阅端点必须能取回登记时的 listener 实例 |
| 接收侧 `Message` 只有 `topic()` + `payload()`，**没有 messageId**；`messageId` 只在 `SendResult` 上 | `Message` / `SendResult` | 日志区的接收行不能显示 messageId；发送行可显示 |

### 6.2 页面要素与本项目映射

| 参考站点要素 | 本项目对应 | 处理 |
| --- | --- | --- |
| 协议（ws/wss/mqtt）、主机、端口、路径、TLS/证书 | **无网络层**：内存后端进程内，不监听端口 | 整块「连接参数卡」替换为「实例信息条」（实例名 / `mq.type` / 配置前缀 / 就绪状态 / 计数） |
| Client ID | 无客户端身份概念 | 身份用「实例名」表达 |
| 用户名 / 密码 | 无鉴权 | 在「已知限制」中写明「无鉴权」 |
| Keep Alive | 无心跳/会话 | 不提供 |
| 「连接 / 断开」按钮 + 连接态（已连接/断开/重连中） | 实例在应用启动时就已装配好，无运行期连接动作 | 状态位只表达「装配就绪 / 未装配」，禁用「已连接 / 断开 / 重连」措辞 |
| QoS 下拉（0/1/2） | 页面不暴露 QoS | 不提供该控件 |
| Retain 复选 | 页面不暴露 retain | 在发布区提示「无订阅者时发送仍成功（`delivered=0`），消息被丢弃」 |
| 通配符主题（`+` / `#`） | 主题为字面量精确匹配 | 提示「主题按字符串精确匹配」 |
| 订阅区：Topic + 订阅按钮 + 已订阅 chip + 取消 | `/mq/demo/subscribe`、`/mq/demo/unsubscribe`，chip 来自 demo 侧订阅登记 | **保留**，加「影响所有浏览器会话」警示徽标 |
| 发布区：Topic + 消息体 + 发布按钮 | `/mq/demo/send`（文本捷径）、`/mq/demo/send/message`（Message 对象） | **保留**，QoS/retain 删除；新增「发送方式」二选一，把两条代码路径显式暴露出来 |
| 消息流：方向 / 时间 / Topic / Payload | `/mq/demo/messages` 轮询（最新在上）+ 前端本地发送行 | **保留**；明确标注「发送行由本页记录，接收行来自后端记录」 |
| 清空 / 暂停滚动 | `/mq/demo/messages/clear` + 前端「自动滚动」开关 | **保留**（清空按实例生效） |
| 消息条数上限 | `DemoMessageLog.MAX_ENTRIES = 200` | 显示 `n/200`（每实例 200 条，见 §4） |
| 主题/订阅者枚举面板 | 库无此 API | 使用 demo 侧登记的订阅清单 |
| 多实例（参考站点没有） | `__MAIN__` + `demoA` + `demoB` + `demoC` | 入口页 4 卡 + 每实例独立演示页 |

## 7. 已知限制（页面必须如实呈现，不允许美化）

1. **后端差异**：`__MAIN__` 为内存后端（进程内、消息不过进程边界、重启即清空、同步投递）；`demoA`/`demoB`/`demoC` 分别走 MQTT / Redis Stream / MQTT5，经真实后端中转（broker 不可达时启动失败）。E2 事件不进消息流，由独立监听器计数。
2. **进程重启即清空**：消息、计数、订阅登记全部丢失；`messageId` 是**每实例**进程内自增十进制串，不跨实例唯一。
3. **同步投递**：`send` 在调用线程内回调完订阅者才返回，「发送后立刻查 messages 必能看到」；真实后端是异步生产+拉取消费，页面文案不得暗示异步确认。
4. **页面不暴露 QoS / retain / 通配符 / 持久化 / 确认 / 重试 / 死信控件**。
5. **无「连接态」**：只有「装配就绪 / 未装配」。
6. **订阅的作用域是该实例的总线**：`MqPlugin.start()` 逐实例 `provider.createBus(config)`，每实例各建一条总线，故 demoA 的订阅/取消不影响 demoB；但同一实例内任一浏览器会话的订阅/取消，立即影响**访问该实例的所有浏览器会话**（UI 必须标注）。
7. **同一实例的消息记录对所有会话共享**：同一实例的 `/messages` 对所有会话返回同一份记录，`/messages/clear` 同样对该实例全局生效（会导致「别人清空了我的日志」）。UI 需提示。
8. **已订阅列表不可从库枚举**：只能依赖 demo 侧登记表；若登记表漏记（例如装配期订阅未登记），页面就会少显示 chip。
9. **取消订阅按监听器实例身份**：登记表若丢失 listener 实例，取消会变成静默 no-op——页面表现为「点了没反应」。
10. **重复订阅叠加**：装配期已用 `DemoMessageListener` 订阅了 `aifei.mq.demo.topic`；页面若用**另一个** listener 再订阅同一主题，同一消息会被记录两次，`delivered` 变 2。UI 需提示这一现象（见 §7 第 10 条）。
11. **无鉴权**：任何能访问 18100 的人都能发送、清空、取消订阅。
12. **单实例单后端**：一个实例名只能指向一种 `mq.type`；当前 4 个实例分别为 memory / mqtt / redisstream / mqtt5，页面不提供切换后端的能力。
13. **消息记录按实例分片**：`DemoMessageLog` 经 `SHARDS` 分片，每片独立记录与计数、`MAX_ENTRIES = 200` 为每片上限；不同实例记录互不可见。
14. **装配期订阅无法从页面取消**：装配期 listener 由 `DemoAppConfig.onStart()` 创建（不在页面侧订阅登记表内），而 `Consumer.unsubscribe` 按 listener **实例身份**移除、未登记即幂等静默 no-op；因此页面上面向装配期订阅**不提供取消入口**（chip 无 `×`），对其调 `/mq/demo/unsubscribe` 返回 `code=10001`，UI **不得**提示「已取消」。

