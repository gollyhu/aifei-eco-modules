# aifei-mq-demo 演示页面 PRD

> 版本：v1（待评审）｜作者：产品经理 许清楚｜范围：**在 `aifei-mq-demo` 示例工程内新增演示页面 + 多实例入口**，不含库模块变更

## 1. 项目信息

| 项 | 内容 |
| --- | --- |
| Language | 简体中文 |
| Programming Language | Java 8 + Maven + Aifei（Just Service + HIO + Undertow） |
| 项目名 | `aifei_mq_demo` |
| 模块性质 | **示例工程**（不发布），本次为「加页面」，不是库能力扩展 |
| 前端形态 | **Aifei 静态层**：原生 HTML/CSS/JS + AJAX + 本地全局构建版 Vue，**无构建工具**（对齐 `aifei-websocket-demo`） |
| 端口 | HTTP **18100**（`start-demo.bat` 的 `-Dundertow.port=18100`），与 socket/sentinel demo 错开 |

**原始需求复述**：在 `aifei-mq-demo` 中增加测试演示页面，页面功能与布局参考 `https://www.bchrt.com/tools/mqtt-websocket-client-test-tool/`，视觉风格参考 `aifei-websocket-demo`；另外需要一个入口页面，按配置 `mq.type` / `mq.names` 声明的多个实例列出，点击后进入各自的演示页面。

**用户已拍板（不再推翻）**

1. **订阅控件：放**。演示页提供「订阅 / 取消订阅」输入框与按钮，调用 `Consumer.subscribe(topic, listener)` / `Consumer.unsubscribe(topic, listener)`；UI **必须显式标注「影响所有浏览器会话（全局副作用）」**。
2. **实例清单**：补 `mq.names = demoA,demoB`，入口页展示 **3 张实例卡**：`__MAIN__`（主实例，走根级 `mq.*`）+ `demoA` + `demoB`（各走 `mq.demoA.*` / `mq.demoB.*`）。

## 2. 产品定义

### 2.1 产品目标（3 个，正交）

1. **闭环可点**：把「装配 → 发送 → 订阅消费」从 curl 变成浏览器里点得动、看得见、可重复的现场，一个页面完成发送与观察。
2. **多实例可见**：让 `mq.names` 声明的多实例能力可视化——入口页一屏看清 3 个实例各自的类型与就绪状态，进入后每个实例是**互相隔离**的独立现场。
3. **语义不撒谎**：页面上如实标出「内存后端 · 同步投递 · 无真实 broker」，**不假装**有 broker、连接态、QoS、retain、异步确认。

### 2.2 用户故事

| # | 作为 | 我希望 | 以便 |
| --- | --- | --- | --- |
| U1 | 库维护者（演示给他人看） | 打开 `http://localhost:18100/` 就看到 3 张实例卡，点进去直接能发消息、看回执 | 30 秒内讲清「装配 → 发送 → 订阅消费」闭环，不用开终端 |
| U2 | 库维护者（验证多实例） | 在 `demoA` 页面发一条消息，`demoB` 页面完全看不到 | 直观证明每个实例是独立总线、消息不串实例 |
| U3 | 使用者（想试订阅行为） | 在页面上输入一个主题并点「订阅」，看消息是否被记录 | 不下代码就能验证 `subscribe` 的效果，并清楚知道它影响所有人 |
| U4 | 使用者（排查） | 看到「已发送 / 接收 / 记录条数 / 上限 200」与最近错误 | 判断「是没订阅、还是发送失败、还是记录被清空」 |
| U5 | 使用者（演示前重置） | 一键清空**当前实例**的记录与计数，其他实例不受影响 | 干净地重新演示一遍 |

## 3. 需求池

### P0（必须有，本次范围）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P0-1 | 配置补 `mq.names = demoA,demoB` | `app-config.txt` 含该键；应用启动成功；`/mq/demo/instances` 返回 3 项（`__MAIN__`、`demoA`、`demoB`，顺序为「主实例在最前，其余按声明顺序」） |
| P0-2 | 实例枚举端点 `/mq/demo/instances` | 每个实例返回：`instanceName` / `type` / `main`(bool) / `configPrefix`(`mq`、`mq.demoA`) / `ready`(producer、consumer 是否可取用) / `subscribedTopicCount` / `messageLogSize` / `messageLogCapacity`；HTTP 200，`code=0` |
| P0-3 | 现有 5 个端点全部支持 `instance` 参数 | 省略时取默认值 `__MAIN__`，**向后兼容**：`/mq/demo/state` 不带参数时行为与现状一致；`instance` 非法/未注册返回 `code=10001` 且 `msg` 含「未知实例」与可用实例名清单 |
| P0-4 | 入口页（3 张实例卡） | 每卡显示 **实例名 / 后端类型 / 状态**（+ 配置前缀、订阅主题数、记录条数）；点击卡片或「进入演示页」**以绝对路由**跳 `/mq/demo/page?instance=<instanceName>`（不得写相对 `demo.html`）；空清单时显示空态文案 |
| P0-5 | 演示页顶部「实例信息条」 | 显示当前实例名、`mq.type`、配置前缀、就绪状态、订阅主题数、发送/接收计数、记录 `n/200`；含「← 返回实例列表」（**绝对路由**回 `/`，不得写相对 `index.html`） |
| P0-6 | 演示页「订阅区」 | Topic 输入 + 「订阅」+「取消订阅」按钮；已订阅主题以 chip 列出（**本页订阅**的 chip 可取消；**装配期订阅**的 chip 只读、不提供取消入口，见 §7 第 14 条）；**必须**显式标注「订阅 / 取消订阅影响该实例的所有浏览器会话（全局副作用）」 |
| P0-7 | 演示页「发布区」 | Topic 输入（默认值 = `DEMO_TOPIC`）+ 发送方式二选一（**文本捷径** `/send` / **Message 对象** `/send/message`）+ 消息体 textarea + 「发送」；发送后展示回执：`code` / `msg` / `messageId` / `delivered` / `receivedCount` |
| P0-8 | 演示页「消息流」日志区 | 轮询 `/mq/demo/messages?instance=`；字段：时间戳（`HH:mm:ss.SSS`）/ 方向 / topic / 文本 / payloadSize / subscriber；**最新在上**；显示「共 n 条 / 上限 200」；「自动滚动」开关 / 「清空记录」按钮 |
| P0-9 | **消息记录按实例隔离** | `demoA` 的记录不出现在 `demoB` 的介绍页/日志区；`/messages/clear?instance=demoA` 只清 `demoA`；发送/接收计数、订阅登记同样按实例隔离（**只陈述需求，实现方案由架构师决定**） |
| P0-10 | 三态齐全 | 每页每区都有**空态 / loading 态 / 错误态**（具体文案见 `ui-prototype.md`） |
| P0-11 | 语义标注 | 页面固定位置出现「内存后端 · 同步投递 · 无真实 broker」；**不得**出现「已连接 / 断开连接 / 重连 / QoS / retain / 证书」等词 |
| P0-12 | 静态层组织 | 页面在 `src/main/webapp/view/mq/`，样式脚本在 `src/main/webapp/assets/{css,js}/`；无构建工具、无 npm、无 Vite |

### P1（应该有，本次争取）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P1-1 | 日志方向区分 | 接收行来自 `/messages`（▼收）；发送行是**前端本地记录**（▲发），且页面上注明「发送行由本页记录，不来自后端」 |
| P1-2 | 已订阅主题「来源」标注 | chip 上区分「装配期」（`onStart` 订阅）与「本页订阅」，便于解释「为什么我没订阅也有记录」 |
| P1-3 | 轮询降频 | 连续 3 次请求失败后间隔由 2s 降为 5s，恢复成功后回到 2s；错误在信息条以红字显示「最近错误」 |
| P1-4 | 主题快捷填充 | 提供「填入演示默认主题」小按钮（值 = `aifei.mq.demo.topic`） |
| P1-5 | 复制日志 / 复制回执 | 一键复制当前日志文本；复制失败时提示「复制失败，请手动选择」 |
| P1-6 | 入口页「刷新实例清单」 | 点击重新请求 `/mq/demo/instances`，卡片数据刷新，失败显示错误条并保留旧数据 |

### P2（可以有，不阻塞）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P2-1 | 实例卡显示最近活动时间 | 由 `instances` 返回或前端记录；无数据显示 `—` |
| P2-2 | 消息体历史（↑↓ 调出） | 单页内保留最近 50 条，刷新页面即清空 |
| P2-3 | 日志按方向过滤 | 「全部 / 仅接收 / 仅发送」分段按钮 |
| P2-4 | 页脚说明抽屉 | 端点速查表 + 内存后端语义说明（对齐 `ws-demo` 的抽屉做法） |

## 4. 表结构草案（★ Aifei 专章）—— **不适用**

| 项 | 结论 |
| --- | --- |
| 表结构 / DDL | **不适用** |
| Generator / Model | **不适用** |
| 理由 | 本 demo **无表、无持久化**：`pom.xml` 无 JDBC 驱动/连接池，`app-config.txt` 无 `jdbcUrl`，消息记录走进程内内存 `DemoMessageLog`。**现值是全静态单份**：`MAX_ENTRIES = 200` 为全部实例共享的上限，`ENTRIES`、`sentCount`、`receivedCount` 与装配快照（`SUBSCRIBED_TOPICS` / `SUBSCRIBER_NAMES`）均为 `static` 字段，且 `markStartup` 只保存 1 个主题——这与 P0-9「按实例隔离」直接冲突，**必须重构为按实例分片**（每实例独立记录、计数与订阅登记；改造后每实例 200 条，现状共 200 条），应用重启即清空。**不编造任何表。** |
| 排序白名单 | 不适用（无 `#orderBy`）；日志排序固定为「最新在上」，由 `/messages` 返回顺序决定 |

## 5. Service 方法清单（★ Aifei 专章）

### 5.1 现有端点（5 个，全部 GET，返回 `Out`；HTTP 恒 200，`code=0` 为成功）

| Service 类 | 路由前缀 | 方法 | 参数 | 返回 | 变更 |
| --- | --- | --- | --- | --- | --- |
| `MqDemoService` | `/mq/demo` | `state` | — | `Out`（`mqType`/`subscribedTopics`/`subscribers`/`sentCount`/`receivedCount`/`messageLogSize`/`messageLogCapacity`/`producerInjected`/`consumerInjected`） | **扩展**：+ `instance` |
| | | `send` | `topic`(默认 `DEMO_TOPIC`)、`text`(默认 `hello`) | `Out`（`topic`/`messageId`/`text`/`delivered`/`deliveredRefTopic`/`receivedCount`） | **扩展**：+ `instance` |
| | | `sendMessage` (`/send/message`) | 同上 | `Out`（较上多 `payloadSize`） | **扩展**：+ `instance` |
| | | `messages` | — | `Out`（`count`/`maxEntries`/`messages[]`；每条 `sequence`/`topic`/`subscriber`/`text`/`payloadSize`/`receivedAt`） | **扩展**：+ `instance` |
| | | `clearMessages` (`/messages/clear`) | — | `Out` | **扩展**：+ `instance`；语义改为「只清该实例」 |

### 5.2 新增 / 扩展端点

| Service 类 | 路由前缀 | 方法 | 参数 | 返回类型 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `MqDemoService` | `/mq/demo` | `instances` | — | `Out` | **新增**。实例清单：`count` + `instances[]`（`instanceName`/`type`/`main`/`configPrefix`/`ready`/`subscribedTopicCount`/`messageLogSize`/`messageLogCapacity`）。顺序：`__MAIN__` 在首，其后按 `mq.names` 声明顺序 |
| | | `subscribe` | `instance`(默认 `__MAIN__`)、`topic`（必填） | `Out` | **新增**。调 `Consumer.subscribe(topic, listener)`；返回最新订阅清单 `subscribedTopics[]`。`topic` 空白 → `10001`；实例未知 → `10001`；总线已停止 → `90000` |
| | | `unsubscribe` | `instance`(默认 `__MAIN__`)、`topic`（必填） | `Out` | **新增**。调 `Consumer.unsubscribe(topic, listener)`；返回最新订阅清单。**约束**：`unsubscribe` 按**监听器实例身份**移除，必须用登记时同一个 `listener` 实例，否则是幂等静默 no-op（页面上看起来「点了取消没反应」）→ 需保留登记表。**装配期订阅不在该登记表内**（其 listener 由 `DemoAppConfig.onStart()` 创建），对装配期主题调用一律返回 `code=10001`「未登记该主题的页面订阅」，**不得**静默成功或提示「已取消」 |
| | | `page` | — | `String`（返回 **`/mq/demo.html`**——`@Path("/mq/demo")` 下返回值必须以 `/` 开头，否则会被 targetPath 前缀二次拼接成 `/mq/demo/mq/demo.html`；模板根即 `webapp/view`，故不带 `view/` 前缀） | **新增**。演示页视图。`?instance=` **仅由前端 JS 读取**，服务端不参与渲染 |
| `MqPageService`（新类，对齐 `ws-demo` 的 `DemoPageService`） | `/` | `index` | — | `String`（返回 **`/mq/index.html`**；`@Path("/")` 下不加 `/` 也会被补成同值，但统一按加 `/` 书写） | **新增**。入口页，访问 `http://localhost:18100/` |

**`instance` 参数语义（统一约定）**

| 项 | 约定 |
| --- | --- |
| 默认值 | `__MAIN__`（主实例，配置走根级 `mq.*`）—— **不带参数即旧行为，向后兼容** |
| 合法值 | `__MAIN__` + `mq.names` 声明的命名实例名（`demoA`、`demoB`） |
| 非法值 | 未注册名 / 空串 / 含 `.`、`=`、`:` 或空白 → `code=10001`，`msg` 含「未知实例」+ 可用实例清单 |
| 未装配 | MQ 未装配完成（`MqHolder` 未初始化）→ `code=90000` |
| 可见性 | 每实例独立总线：同 topic 名在 `demoA` 与 `demoB` 上互不可见、互不投递 |

### 5.3 页面路由（静态层）

| 页面 | 路由 | view 文件 | 说明 |
| --- | --- | --- | --- |
| 实例入口页 | `GET /` | `src/main/webapp/view/mq/index.html` | 3 张实例卡 |
| 实例演示页 | `GET /mq/demo/page?instance=demoA` | `src/main/webapp/view/mq/demo.html` | `instance` 缺省时前端按 `__MAIN__` 处理 |

> **路由与跳转约定（门禁核对项）**
>
> 1. 页面路由只有两个：入口页 `GET /`、演示页 `GET /mq/demo/page?instance=`；实例由查询参数区分。
> 2. **页内跳转一律写绝对路由**：入口页 → `location.href = '/mq/demo/page?instance=' + encodeURIComponent(instanceName)`；演示页 → `location.href = '/'`。**不得**写 `demo.html` / `index.html` 这类相对路径（会分别解析成 `/demo.html`、`/mq/demo/index.html`，两个 URL 都不存在 → 404）。
> 3. **Service 返回的 view 模板名统一以 `/` 开头**（`/mq/index.html`、`/mq/demo.html`）：`IoHandler.handleEnjoyView` 在 view 不以 `/` 开头时会前置 action 的 targetPath（`@Path("/mq/demo")` 下会变成 `/mq/demo/mq/demo.html`）；模板根为 `webapp/view`，故不带 `view/` 前缀。
> 4. 「入口页 `index` 是否直接映射到类前缀根 `/`」请架构师实测确认后落定（`aifei-websocket-demo` 的 `DemoPageService` 为同构先例）。

## 6. 实例枚举来源与语义映射（★ 本章为门禁核对依据）

### 6.1 实例枚举来源（已核实，不臆测）

| 事实 | 出处 | 对本需求的影响 |
| --- | --- | --- |
| 主实例登记名恒为 `__MAIN__`，属保留名，不需在 `mq.names` 声明 | `MqConfig.MAIN_INSTANCE_NAME` | 入口页固定 1 张主实例卡 |
| `mq.names` 逗号分隔，逐项去空白；空项/非法字符/保留名（`type`、`names`）/重名即**启动失败** | `MqConfig.parseInstanceNames` | `demoA,demoB` 合法；配置错则应用起不来，页面无「部分实例」状态 |
| 命名实例前缀为 `mq.<name>`，类型键 `mq.<name>.type`，未配置时默认为 `memory` | `MqConfig.parse` / `prefixOf` | 3 个实例都是 `memory`；`mq.demoA.type` 可省略，建议显式写出便于演示 |
| `MqPlugin` 的实例名清单方法 `instanceNames()` 是 **private**，`MqHolder` **不提供枚举 API** | `MqPlugin` / `MqHolder` | demo 侧要列实例，只能**自己重解析 `mq.names`**（`MqConfig.parseInstanceNames(p.getProperties(), MqConfig.NAMES_KEY)`）再拼 `__MAIN__` |
| `ConsumerUtil.use(name)` 按名取实例，未注册抛 `IllegalArgumentException`，未初始化抛 `IllegalStateException` | `ConsumerUtil` / `MqHolder.lookup` | `ready` 判定可直接以「能否取到 producer/consumer」为依据 |
| 每实例独立 `MemoryMessageBus`（各自的 `subscriptions` 与 `messageId` 自增） | `MemoryMessageBus` / `MqPlugin.start` | 「按实例隔离」**在库层**成立（`start()` 内逐实例 `createBus`）；但 demo 侧 `DemoMessageLog` 现状是**全静态单份**（见 §4 与 §7 第 13 条），记录/计数/订阅登记**必须重构为按实例分片**——这是本需求要新增的改造点，方案由架构师决定 |
| `MessageBus` 只有 `producer()` / `consumer()` / `close()`，**无法枚举主题或订阅者** | `MessageBus` | 页面的「已订阅主题 chip 列表」**只能由 demo 侧自维护**（含装配期订阅），不能从库查 |
| `Consumer.unsubscribe(topic, listener)` 需传 listener **实例身份**；重复取消/取消未登记订阅为静默 no-op | `Consumer` | 取消订阅端点必须能取回登记时的 listener 实例 |
| 接收侧 `Message` 只有 `topic()` + `payload()`，**没有 messageId**；`messageId` 只在 `SendResult` 上 | `Message` / `SendResult` | 日志区的接收行不能显示 messageId；发送行可显示 |

### 6.2 参考站点要素 → 本项目映射（逐项处置）

| 参考站点要素 | 本项目对应 | 处置 |
| --- | --- | --- |
| 协议（ws/wss/mqtt）、主机、端口、路径、TLS/证书 | **无网络层**：内存后端进程内，不监听端口 | **删除**。整块「连接参数卡」替换为「实例信息条」（实例名 / `mq.type` / 配置前缀 / 就绪状态 / 计数） |
| Client ID | 无客户端身份概念 | **删除**。身份用「实例名」表达 |
| 用户名 / 密码 | 无鉴权 | **删除**（并在「已知限制」中写明「无鉴权」） |
| Keep Alive | 无心跳/会话 | **删除** |
| 「连接 / 断开」按钮 + 连接态（已连接/断开/重连中） | 实例在应用启动时就已装配好，**无运行期连接动作** | **删除**。状态位只表达「装配就绪 / 未装配」，**禁用**「已连接 / 断开 / 重连」措辞 |
| QoS 下拉（0/1/2） | 无 QoS、无投递等级 | **删除**（不做灰置占位，避免让人以为「选不了但存在」） |
| Retain 复选 | 无 retain、无留存消息（内存后端无订阅者即丢弃） | **删除**；改为在发布区提示「无订阅者时发送仍成功（`delivered=0`），消息被丢弃」 |
| 通配符主题（`+` / `#`） | 主题为字面量**精确匹配**，无通配符 | **删除**；提示「主题按字符串精确匹配」 |
| 订阅区：Topic + 订阅按钮 + 已订阅 chip + 取消 | `/mq/demo/subscribe`、`/mq/demo/unsubscribe`，chip 来自 demo 侧订阅登记 | **保留**（用户已拍板）+ 加「影响所有浏览器会话」警示徽标 |
| 发布区：Topic + 消息体 + 发布按钮 | `/mq/demo/send`（文本捷径）、`/mq/demo/send/message`（Message 对象） | **保留**，QoS/retain 删除；新增「发送方式」二选一，把两条代码路径显式暴露出来 |
| 消息流：方向 / 时间 / Topic / Payload | `/mq/demo/messages` 轮询（最新在上）+ 前端本地发送行 | **保留**；明确标注「发送行由本页记录，接收行来自后端记录」 |
| 清空 / 暂停滚动 | `/mq/demo/messages/clear` + 前端「自动滚动」开关 | **保留**（清空按实例生效） |
| 消息条数上限 | `DemoMessageLog.MAX_ENTRIES = 200` | **保留**并显示 `n/200`（改造后每实例 200 条，现状共 200 条，见 §4） |
| 主题/订阅者枚举面板 | 库无此 API | **改为** demo 侧登记的订阅清单 |
| 多实例（本需求新增，参考站点没有） | `__MAIN__` + `demoA` + `demoB` | **新增**入口页 3 卡 + 每实例独立演示页 |

## 7. 已知限制（页面必须如实呈现，不允许美化）

1. **无真实 broker**：内存后端进程内，消息不过进程边界；无 MQTT/AMQP 协议，无序列化格式约定（payload 就是 UTF-8 字节）。
2. **进程重启即清空**：消息、计数、订阅登记全部丢失；`messageId` 是**每实例**进程内自增十进制串，不跨实例唯一。
3. **同步投递**：`send` 在调用线程内回调完订阅者才返回，「发送后立刻查 messages 必能看到」；真实后端是异步生产+拉取消费，页面文案不得暗示异步确认。
4. **无 QoS / retain / 通配符 / 持久化 / 确认 / 重试 / 死信**。
5. **无「连接态」**：只有「装配就绪 / 未装配」。
6. **订阅的作用域是该实例的总线**：`MqPlugin.start()` 逐实例 `provider.createBus(config)`，每实例各建一条总线，故 demoA 的订阅/取消不影响 demoB；但同一实例内任一浏览器会话的订阅/取消，立即影响**访问该实例的所有浏览器会话**（用户已拍板保留订阅控件，UI 必须标注）。
7. **同一实例的消息记录对所有会话共享**：同一实例的 `/messages` 对所有会话返回同一份记录，`/messages/clear` 同样对该实例全局生效（会导致「别人清空了我的日志」）。UI 需提示。
8. **已订阅列表不可从库枚举**：只能依赖 demo 侧登记表；若登记表漏记（例如装配期订阅未登记），页面就会少显示 chip。
9. **取消订阅按监听器实例身份**：登记表若丢失 listener 实例，取消会变成静默 no-op——页面表现为「点了没反应」。
10. **重复订阅叠加**：装配期已用 `DemoMessageListener` 订阅了 `aifei.mq.demo.topic`；页面若用**另一个** listener 再订阅同一主题，同一消息会被记录两次，`delivered` 变 2。UI 需提示这一现象（见待确认 Q1）。
11. **无鉴权**：任何能访问 18100 的人都能发送、清空、取消订阅。
12. **单实例单后端**：一个实例名只能指向一种 `mq.type`；本次 3 个实例都是 `memory`，页面不提供切换后端的能力。
13. **demo 侧记录当前是单份全局状态**：`DemoMessageLog` 全静态、`MAX_ENTRIES = 200` 为共享上限，实现 P0-9 需将其重构为按实例分片（改造后每实例 200 条，现状共 200 条）；重构前 demoA 与 demoB 会看到同一份记录。
14. **装配期订阅无法从页面取消**：装配期 listener 由 `DemoAppConfig.onStart()` 创建（不在页面侧订阅登记表内），而 `Consumer.unsubscribe` 按 listener **实例身份**移除、未登记即幂等静默 no-op；因此页面上面向装配期订阅**不提供取消入口**（chip 无 `×`），对其调 `/mq/demo/unsubscribe` 返回 `code=10001`，UI **不得**提示「已取消」。

## 8. 待确认问题（每条含默认建议）

| # | 问题 | 默认建议（若无人反对即按此实现） |
| --- | --- | --- |
| Q1 | 页面订阅用哪个 listener？若与装配期 `DemoMessageListener` 复用同一实例，会「取消掉装配期订阅」；若新建，则同主题重复记录、`delivered` 叠加 | **页面用独立 listener**（如 `PageDemoListener`，名称随登记表暴露给前端）；对已被装配期订阅的主题，页面在 chip 上标注「装配期已订阅」，并在订阅 succeess 提示里说明「同一消息可能被记录多次」 |
| Q2 | `unsubscribe` 端点只传 `topic`（由后端查登记表取 listener），还是也允许传 listener 名？ | **只传 `instance` + `topic`**，后端按登记表取回同一实例；登记表无该项 → `code=10001`「未登记该主题的页面订阅」，不静默成功 |
| Q3 | `delivered` 在多订阅者 + 多实例下的口径 | **该实例本次发送期间新增的接收记录条数**（发送前后 `receivedCount` 之差），与现有实现一致，只是分片到实例 |
| Q4 | 页面路由写法（`index` 是否直接映射到类前缀根 `/`） | 入口页 `GET /`、演示页 `GET /mq/demo/page`；由架构师实测确认后落定 |
| Q5 | 入口页是否需要「刷新实例清单」 | **需要**，P1，普通重新 GET `/mq/demo/instances` |
| Q6 | 轮询间隔 | **2000ms**；连续 3 次失败降为 5000ms，成功即恢复 |
| Q7 | QoS / retain 等不支持项：删除还是灰置「不支持」 | **删除**，仅在页脚用一句说明「无 QoS / retain / 通配符」 |
| Q8 | 日志上限是否在页面显示 | **显示** `n/200`，与 `MAX_ENTRIES` 一致（若库常量变更，页面读 `maxEntries` 而非硬编码） |
