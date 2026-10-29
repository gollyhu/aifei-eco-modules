# aifei-mq-demo

`aifei-mq` 的内存后端演示工程（**示例工程，不发布**）：在**同一个进程内**装配**多个**消息后端实例，用订阅者收消息、
用 HTTP 端点发消息与查看结果，并提供两个浏览器演示页面（实例入口页 + 单实例演示页），
用来现场演示 **装配 → 发送 → 订阅消费** 的完整闭环，并按 **E2 定档**（固定 topic + payload 内 `type` 路由键）
演示「事件类型由消费侧分发」的模式（见下文「E2 事件演示」）。

- 结构对齐 `aifei-sentinel-demo` / `aifei-websocket-demo`：`DemoApp` → `Aifei.start(DemoAppConfig)`，
  HIO 逐字同步自官方 `jfinal/aifei-eco`（保留官方包名 `cn.aifei.demo.common.arch` 与版权块）+
  两个 Just Service（`service` 包）。
- 生产者 / 消费者由装配入口 `MqPlugin` 注册为 `@Inject` 单例，Service 里 `@Inject Producer` / `@Inject Consumer`
  取用；订阅则用静态工具类 `ConsumerUtil.use().subscribe(...)`，同一 demo 里两种取用方式都被演示。
- 页面为 Aifei 静态层（`src/main/webapp/`，原生 HTML/CSS/JS + 本地全局 Vue，无构建工具）：
  实例入口页按 `mq.type` / `mq.names` 列出全部实例，点击进入该实例的演示页（订阅 / 发布 / 消息流）。

## 端口

| 用途 | 端口 | 来源 |
| --- | --- | --- |
| HTTP（演示端点） | **18100** | `start-demo.bat` 的 `-Dundertow.port=18100` |

内存后端在进程内、不监听任何端口，故本 demo 只占一个 HTTP 端口，与 `aifei-sentinel-demo` 的 18080、
`aifei-websocket-demo` 的 18090 / 18091 均错开。

## 运行

```bat
start-demo.bat    :: 启动 demo（HTTP 18100），按 Ctrl+C 或运行 stop-demo.bat 结束
stop-demo.bat     :: 按端口 18100 找到监听进程并结束
```

启动后访问 <http://localhost:18100/> 进入实例入口页（3 张实例卡：`__MAIN__`、`demoA`、`demoB`）；访问
<http://localhost:18100/mq/demo/state> 看到 `mqType=memory`、`subscribedTopics` 含默认主题即为正常。

## 演示页面

| 页面 | 地址 | 内容 |
| --- | --- | --- |
| 实例入口页 | `GET /` | 按 `mq.names`（+ 主实例）列出实例卡（类型 / 配置前缀 / 就绪状态 / 订阅数 / 记录数），点击进入 |
| 实例演示页 | `GET /mq/demo/page?instance=<实例名>` | 订阅区（可运行时订阅 / 取消，标注「影响所有浏览器会话」）+ 发布区（文本捷径 / Message 对象两种方式）+ **E2 事件演示区**（业务域 / 事件类型 / 命中表）+ 消息流日志（2 秒轮询） |

页面固定标注「内存后端 · 同步投递 · 无真实 broker」；订阅是进程级全局副作用（影响访问同一实例的所有浏览器会话）；
装配期订阅的 chip 不可从页面取消；消息记录、发送 / 接收计数均**按实例隔离**。

## 演示端点

所有端点都是 GET，返回 `{"code":0,"msg":...,"data":...}`（HTTP 恒 200，语义由 `code` 承载；`0` 为成功）。
除 `instances` / `page` 外均支持可选参数 `instance`（省略即主实例 `__MAIN__`，向后兼容；未知实例返回
`code=10001` 并附可用实例清单）。

| 端点 | 作用 |
| --- | --- |
| `GET /` | 实例入口页视图 |
| `GET /mq/demo/page?instance=...` | 实例演示页视图（`instance` 仅由前端读取） |
| `GET /mq/demo/instances` | 实例清单：`__MAIN__` 在首，其后按 `mq.names` 声明顺序；每项含 `instanceName` / `type` / `main` / `configPrefix` / `ready` / `subscribedTopicCount` / `messageLogSize` / `messageLogCapacity` |
| `GET /mq/demo/state` | 现场快照（按实例）：`mqType` / `configPrefix` / `subscribedTopics` / `subscribers`（含 `source`：`startup` / `page`）/ `sentCount` / `receivedCount` / `messageLogSize` / `messageLogCapacity` |
| `GET /mq/demo/send?topic=...&text=hello` | 发送文本消息（`producer.send(topic, text)`），返回 `topic` / `messageId` / `text` / `delivered` / `deliveredRefTopic` / `receivedCount` |
| `GET /mq/demo/send/message?topic=...&text=hello` | 以 `Message` 对象发送（`producer.send(Message.of(...))`），较上面多一项 `payloadSize` |
| `GET /mq/demo/messages` | 订阅侧收到的消息记录（按实例，最新在前，每实例容量上限 200 条） |
| `GET /mq/demo/messages/clear` | 清空**该实例**的消息记录与累计计数（订阅保留） |
| `GET /mq/demo/subscribe?topic=...` | 运行时订阅（页面订阅用独立监听器，登记为 `page` 来源） |
| `GET /mq/demo/unsubscribe?topic=...` | 取消**页面订阅**；装配期订阅（`startup` 来源）未登记，对其调用返回 `code=10001` |
| `GET /mq/demo/event/send?domain=...&type=...&bizId=...` | **E2 发送事件**：`domain` 缺省 `order-events`、`type` 缺省该域首类型、`bizId` 缺省 `A1001`；返回 `payload` / `payloadSize` / `messageId` / `delivered` / `hitCount` |
| `GET /mq/demo/event/dispatch` | **E2 分发统计**（按实例）：`rows[{domain,type,count,lastBizId,lastAt}]`（每域含一行 `unknown` 兜底桶）/ `totalReceived` / `unknownCount` / `lastPayload` |
| `GET /mq/demo/event/dispatch/clear` | 清空**该实例**的事件分发命中统计（订阅保留，不影响消息流） |

`topic` / `text` 的缺省值分别是默认主题 `aifei.mq.demo.topic` 与 `hello`；参数省略或为空串时取缺省值（`code=0`），仅含空白字符时返回 `code=10001`。

## 现场演示脚本（约 30 秒）

1. `GET /mq/demo/state` → `mqType=memory`、`subscribedTopics=[aifei.mq.demo.topic, order-events, pay-events]`、
   `sentCount=0`、`receivedCount=0`。
2. `GET /mq/demo/send?text=hello` → `code=0`、`delivered=1`、`messageId=1`（本模块同步投递，返回前订阅者
   回调已执行完毕）。
3. `GET /mq/demo/messages` → 1 条记录，`text=hello`。
4. `GET /mq/demo/send?text=hi&topic=other.topic` → `code=0`、`delivered=0`、`messageId=2`：**无订阅者的主题
   仍视为发送成功**，消息被丢弃。
5. `GET /mq/demo/messages/clear` 后再 `GET /mq/demo/messages` → `count=0`。

## E2 事件演示（固定 topic + payload 内 `type`）

按 `aifei-mq` 的 **E2 定档**（`aifei-mq/docs/design.md` §5.8）：topic 表达**稳定业务域**，事件类型放 payload 的 `type` 字段，由**订阅侧本地分发**——broker 不需要知道事件类型，运行期不增减 topic。

- 业务域（固定常量，不进配置）：`order-events`（`created` / `paid` / `cancelled`）、`pay-events`（`paid` / `refunded` / `failed`）。
- 装配期为**每个实例**订阅这两个业务域（`source=startup`，页面不可取消）；payload 形如 `{"type":"paid","version":1,"bizId":"A1001"}`。
- 事件走**独立监听器**，**不进右侧消息流**——与自由 topic 演示形成干净对照：自由 topic 演示「任意 topic + 无订阅者即丢弃」，E2 演示「topic 固定 + 订阅即契约 + 消费侧分发」。
- 命中表恒含每域一行 `unknown` 兜底桶（畸形 payload / 未知 type 落这里，不抛异常）。

**演示脚本（约 20 秒）**：

1. `GET /mq/demo/event/dispatch` → `domainTopics=[order-events, pay-events]`、`totalReceived=0`、8 行全 0。
2. `GET /mq/demo/event/send?domain=order-events&type=created&bizId=A1001` → `code=0`、`delivered=1`、`hitCount=1`、`payload={"type":"created","version":1,"bizId":"A1001"}`。
3. `GET /mq/demo/event/dispatch` → `order-events/created` 计数 1、`lastBizId=A1001`、`lastAt` 非空；其余行仍 0。
4. `GET /mq/demo/event/dispatch?instance=demoA` → 全 0（**计数按实例隔离**）；对 `__MAIN__` 再发一次才变化。
5. 打开实例演示页的「E2 事件演示」卡 → 换业务域（类型随域联动）、点「发送事件」→ 命中表随 2 秒轮询自动刷新。
6. `GET /mq/demo/event/send?domain=nope` → `code=10001` + 可用业务域提示；`GET /mq/demo/event/dispatch/clear` → 该实例统计归零（订阅与消息流不受影响）。

> 注意：在订阅区手动订阅 `order-events` / `pay-events` 会让同一事件被记录两次（`delivered` 叠加）——装配期与页面订阅是两个独立监听器，这是既有订阅语义的正常表现。

## 配置要点（`src/main/resources/app-config.txt`）

| 键 | 值 | 说明 |
| --- | --- | --- |
| `mq.type` | `memory` | 主实例（`__MAIN__`）的消息后端类型；缺省即 `memory`，显式写出便于演示与排查 |
| `mq.names` | `demoA,demoB` | 命名实例清单（逗号分隔）；主实例 `__MAIN__` 是保留名、走根级 `mq.*`，不得在此声明；实例卡顺序 = 主实例在前 + 本行声明顺序 |
| `mq.demoA.type` / `mq.demoB.type` | `memory` | 各命名实例的后端类型（前缀 `mq.<实例名>`），未配置时默认 `memory` |
| `printAction` | 未配置 | 打开后每个 action 调用会打印到控制台（默认 `false`） |

HTTP 端口不在配置文件里，由 `start-demo.bat` 的 `-Dundertow.port=18100` 传入；需要换配置源时用
`-Ddemo.config=<file>`（与参考 demo 同名键）。

## 语义说明

- **内存后端是进程内的**：消息不过进程边界，**应用重启即清空**；`messageId` 是**每实例**进程内单调递增的
  十进制字符串（从 `1` 开始），仅在同一实例、同一进程生命周期内唯一。
- **同步投递**：`send` 在调用线程内直接回调订阅者后才返回，因此「发送后立刻查询 `messages`」必定能看到刚
  发的那条；主题上没有订阅者时视为成功，消息被丢弃但仍返回 `messageId`。
- **订阅与取消订阅**：`Consumer` 现有 `subscribe` 与 `unsubscribe` 两个方法。`unsubscribe` 按**实例身份**（引用
  相等）从该主题移除监听器实例，`MessageListener` 是函数式接口，lambda / 方法引用的 `equals` 无可靠语义，故
  不按 `equals` 判定；**重复取消**与**取消一个从未登记过的订阅**均为**幂等静默 no-op**（不抛异常），总线停止
  后调用抛 `IllegalStateException`。装配期订阅只注册**主实例**的默认主题；页面订阅用独立监听器（登记为
  `page` 来源，可取消），对已被装配期订阅的主题再订阅会使同一消息被记录多次（`delivered` 叠加）——页面已提示这一现象。
- **`send(topic, byte[])` 与 `send(topic, String)` 等价**：`/mq/demo/send` 走文本捷径，`/mq/demo/send/message`
  走 `Message` 对象路径，两者除返回体多一项 `payloadSize` 外行为一致。
- **收到的消息无 `messageId`**：`messageId` 只存在于发送结果 `SendResult` 上，订阅侧拿到的 `Message` 只有
  主题与字节载荷，文本由 demo 自行按 UTF-8 解码后记录。
- **E2 事件与自由 topic 消息互不污染**：E2 事件经独立监听器（`E2EventListener`）记入**分发表**，不写
  `DemoMessageLog`；`/mq/demo/messages` 与 `/mq/demo/event/dispatch` 两套状态各自独立清空。装配期订阅口径：
  默认主题 `aifei.mq.demo.topic` **仅主实例**（保持自由 topic 演示对照），两个 E2 业务域**覆盖全部就绪实例**。

## 依赖说明

| 依赖 | 用途 |
| --- | --- |
| `cn.aifei:aifei-all` | 框架内核（`aifei-mq` 内为 `provided`，消费方自带） |
| `io.github.gollyhu:aifei-mq` | 被演示模块（`MqPlugin` + 8 个公共类型） |
| `io.undertow:undertow-core` | HTTP 命令式服务端（不用 Servlet） |
| `org.apache.logging.log4j:log4j-core` | `Log4jLogFactory` 的运行时实现 |

内存后端零第三方运行依赖；本 demo 不引入任何 MQ 客户端、连接池或数据库驱动。未提供 `log4j2.xml`，
log4j2 以默认配置运行（控制台 ERROR 级），启动时的 "No Log4j2 configuration file found" 属预期提示。
