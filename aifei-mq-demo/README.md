# aifei-mq-demo

`aifei-mq` 的内存后端演示工程（**示例工程，不发布**）：在**同一个进程内**装配消息后端，用一个订阅者收消息、
用几个 HTTP 端点发消息与查看结果，用来现场演示 **装配 → 发送 → 订阅消费** 的完整闭环。

- 结构对齐 `aifei-sentinel-demo` / `aifei-websocket-demo`：`DemoApp` → `Aifei.start(DemoAppConfig)`，
  HIO 逐字同步自官方 `jfinal/aifei-eco`（保留官方包名 `cn.aifei.demo.common.arch` 与版权块）+
  一个 Just Service（`service` 包）。
- 生产者 / 消费者由装配入口 `MqPlugin` 注册为 `@Inject` 单例，Service 里 `@Inject Producer` / `@Inject Consumer`
  取用；订阅则用静态门面 `MqKit.consumer().subscribe(...)`，同一 demo 里两种取用方式都被演示。
- **无页面**：本 demo 只有 HTTP 端点（JSON），不提供前端页面。

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

启动后访问 <http://localhost:18100/mq/demo/state>，看到 `mqType=memory`、`subscribedTopics` 含默认主题
即为正常。

## 演示端点

所有端点都是 GET，返回 `{"code":0,"msg":...,"data":...}`（HTTP 恒 200，语义由 `code` 承载；`0` 为成功）。

| 端点 | 作用 |
| --- | --- |
| `GET /mq/demo/state` | 现场快照：`mqType` / `subscribedTopics` / `subscribers` / `sentCount` / `receivedCount` / `messageLogSize` / `messageLogCapacity` / `producerInjected` / `consumerInjected` |
| `GET /mq/demo/send?topic=...&text=hello` | 发送文本消息（`producer.send(topic, text)`），返回 `topic` / `messageId` / `text` / `delivered` / `deliveredRefTopic` / `receivedCount` |
| `GET /mq/demo/send/message?topic=...&text=hello` | 以 `Message` 对象发送（`producer.send(Message.of(...))`），较上面多一项 `payloadSize` |
| `GET /mq/demo/messages` | 订阅侧收到的消息记录（最新在前，容量上限 200 条） |
| `GET /mq/demo/messages/clear` | 清空消息记录与累计计数（订阅与 `mq.type` 快照保留） |

`topic` / `text` 的缺省值分别是默认主题 `aifei.mq.demo.topic` 与 `hello`；参数省略或为空串时取缺省值（`code=0`），仅含空白字符时返回 `code=10001`。

## 现场演示脚本（约 30 秒）

1. `GET /mq/demo/state` → `mqType=memory`、`subscribedTopics=[aifei.mq.demo.topic]`、`sentCount=0`、
   `receivedCount=0`。
2. `GET /mq/demo/send?text=hello` → `code=0`、`delivered=1`、`messageId=1`（本模块同步投递，返回前订阅者
   回调已执行完毕）。
3. `GET /mq/demo/messages` → 1 条记录，`text=hello`。
4. `GET /mq/demo/send?text=hi&topic=other.topic` → `code=0`、`delivered=0`、`messageId=2`：**无订阅者的主题
   仍视为发送成功**，消息被丢弃。
5. `GET /mq/demo/messages/clear` 后再 `GET /mq/demo/messages` → `count=0`。

## 配置要点（`src/main/resources/app-config.txt`）

| 键 | 值 | 说明 |
| --- | --- | --- |
| `mq.type` | `memory` | 消息后端类型；缺省即 `memory`，显式写出便于演示与排查 |
| `printAction` | 未配置 | 打开后每个 action 调用会打印到控制台（默认 `false`） |

HTTP 端口不在配置文件里，由 `start-demo.bat` 的 `-Dundertow.port=18100` 传入；需要换配置源时用
`-Ddemo.config=<file>`（与参考 demo 同名键）。

## 语义说明

- **内存后端是进程内的**：消息不过进程边界，**应用重启即清空**；`messageId` 是进程内全局单调递增的十进制
  字符串（从 `1` 开始），仅在同一进程生命周期内唯一。
- **同步投递**：`send` 在调用线程内直接回调订阅者后才返回，因此「发送后立刻查询 `messages`」必定能看到刚
  发的那条；主题上没有订阅者时视为成功，消息被丢弃但仍返回 `messageId`。
- **本版无取消订阅**：`Consumer` 只有 `subscribe`，订阅一旦登记不会（也无需）清理；因此 demo 把订阅只放在
  装配期注册一次，避免重复登记导致同一消息被记录多次。
- **`send(topic, byte[])` 与 `send(topic, String)` 等价**：`/mq/demo/send` 走文本捷径，`/mq/demo/send/message`
  走 `Message` 对象路径，两者除返回体多一项 `payloadSize` 外行为一致。
- **收到的消息无 `messageId`**：`messageId` 只存在于发送结果 `SendResult` 上，订阅侧拿到的 `Message` 只有
  主题与字节载荷，文本由 demo 自行按 UTF-8 解码后记录。

## 依赖说明

| 依赖 | 用途 |
| --- | --- |
| `cn.aifei:aifei-all` | 框架内核（`aifei-mq` 内为 `provided`，消费方自带） |
| `io.github.gollyhu:aifei-mq` | 被演示模块（`MqPlugin` + 7 个公共类型） |
| `io.undertow:undertow-core` | HTTP 命令式服务端（不用 Servlet） |
| `org.apache.logging.log4j:log4j-core` | `Log4jLogFactory` 的运行时实现 |

内存后端零第三方运行依赖；本 demo 不引入任何 MQ 客户端、连接池或数据库驱动。未提供 `log4j2.xml`，
log4j2 以默认配置运行（控制台 ERROR 级），启动时的 "No Log4j2 configuration file found" 属预期提示。
