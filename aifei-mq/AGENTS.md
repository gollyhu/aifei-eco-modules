# aifei-mq 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档的分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **改代码前必读 `docs/design.md`**。需求与现有设计冲突时，先明确并更新设计决策，再改代码。
- 未经设计决策，不得自行确定契约类行为（投递语义、异常类型与消息、关闭语义、配置键名等），也不得新增或扩大公共 API 面。

## 交付性质

`aifei-mq` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**：没有 `AppConfig` / `Launcher` / `service/` 包 / `webapp/` / HTTP 路由 / HIO / 表结构 / Db+Row / Generator。1.0.0 为**单模块** `io.github.gollyhu:aifei-mq:1.0.0`（`packaging=jar`）：公共 API + `internal` + `aifei.MqPlugin` + 7 个后端实现包同处一个 JAR（`docs/design.md` §15）。

## 硬性约束

### 技术基线

- 代码、依赖与 API 必须兼容 **Java 8**：**语法**与 **API 双约束**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching 等 Java 8 之后语法；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 之后 API。
- 主 JAR 内全部 class 的 major version 必须为 **52**。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；文件头版权块属归属信息，不得改写。
- **`cn.aifei:aifei-all` 为 `provided`**：消费方自带，不向下游传递。
- **第三方依赖分层**：6 个后端客户端一律 `optional`（本模块编译与测试期可见、**不向下游传递**），只能被实现包使用；契约层（根包 / `internal` / `aifei`）**零第三方类型**；**不引入任何日志绑定**（`slf4j-api` 只以门面身份随传递依赖出现）。除这 6 个客户端外**不新增任何直接依赖**。
  - 客户端与版本约束：`rocketmq-client:4.9.7`、`org.eclipse.paho.client.mqttv3:1.2.5`、`org.eclipse.paho.mqttv5.client:1.2.5`、`amqp-client:5.21.0`、`kafka-clients:3.9.1`（**必须停 3.x**，4.x 要求 Java 11+）、`jedis:8.0.1`（与 `aifei-cache` 对齐）。

### 公共面收敛（不得放宽）

- **公共 API 零第三方类型、零框架类型**。
- 根包公共类型固定为 **8 个**：`Message` / `Producer` / `Consumer` / `MessageListener` / `SendResult` / `MqException` / `ProducerKit` / `ConsumerKit`。新增公共类型必须先过设计决策。
- `internal` / 各实现包 / `aifei` 均属**非承诺 API**，消费方不得引用。
- 公共面（根包）不得出现 `internal` 包行话（唯一定义点、注册表、总线、视图等）。

## 包边界与门禁

`ArchitectureBoundaryTest`（**6 例**）扫描 `src/main/java` 全部 `.java`：

| # | 断言 |
| --- | --- |
| ① | 第三方 / 其它模块 import 只允许出现在 7 个实现包（根包 / `internal` / `aifei` 均禁止） |
| ② | `cn.aifei.*`（除 `cn.hg.aifei.mq.*`）只允许出现在 `cn.hg.aifei.mq.aifei` |
| ③ | 全模块禁止 `import java.sql.` |
| ④ | 接入包存在且含 `MqPlugin.java` |
| ⑤ | 根包公共类型**精确 8 个**且文件名集合精确相等 |
| ⑥ | `META-INF/services/...MqProvider` 恰 7 行、无重复，每行 provider 的 `type()` 与其实现包同名，且类型集合与 `MqProviderRegistry.types()` 一致 |

门禁**不得为兼容错误用法而放宽或绕过**；扫描依赖相对路径 `src/main/java`，必须在 `aifei-mq/` 根目录执行。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 参数校验（topic / payload / text / message / messageId / listener / producer / consumer） | `internal.MqValidator` |
| 配置域与键常量、`mq.type` 解析与校验、`mq.names` 解析与实例名校验、`<实例前缀>.<type>.<relative>` 键拼装 | `internal.MqConfig` |
| 消息文本载荷编码（`send(topic, String)` 的 UTF-8） | `internal.BusProducer` |
| 运行期实例注册表（多实例，按实例名登记 / 取用） | `internal.MqHolder` |
| 总线契约（`publish` / `subscribe` / `unsubscribe` / `close`） | `internal.MessageBus` |
| 生产视图的 3 个 `send` 重载 | `internal.BusProducer` |
| 消费视图的 `subscribe` / `unsubscribe` | `internal.BusConsumer` |
| 后端扩展点契约 | `internal.MqProvider` |
| provider 发现（`ServiceLoader` + 按 `type` 索引）与未注册提示 | `internal.MqProviderRegistry` |
| 各后端标识字面量、专有配置键名、订阅登记 / 投递 / 建连 / `close()`（**一后端一处**） | 各实现包的 `XxxProvider.TYPE` 与 `XxxMessageBus` |

## 行为契约要点速查

> 跨后端**共同底线**与**允许差异**见 `docs/design.md` §5.6；本清单以**内存后端**为基准，新增后端的差异须在 §5.6 (B) 逐项列明。

- **投递语义**：同步投递；回调在生产者调用线程内直接执行；无线程池 / 队列 / 背压。
- **有序范围**：单主题 FIFO；同一主题多个订阅者按 `subscribe` 登记顺序回调；不保证跨主题顺序。
- **无订阅者**：`send` 仍成功返回 `SendResult`（`messageId` 已分配）——共同底线；消息**被丢弃还是留存**随后端而变。
- **取消订阅**：按**实例身份**移除（不用 `equals`）；重复取消 / 取消未登记的订阅幂等静默 no-op。
- **关闭语义**：`close()` 幂等；关闭后 `send` / `subscribe` / `unsubscribe` 抛 `IllegalStateException`。
- **监听器异常**：就地隔离捕获，不中断同主题其他订阅者、不上抛给生产者（本版静默丢弃，不引入日志依赖）。
- **异常分类**：参数非法 `IllegalArgumentException`；状态非法 `IllegalStateException`；运行时故障 `MqException`。
- **provider 必须无状态**：不得声明实例字段保存解析结果，配置在 `createBus(config)` 内现读现用。
- **配置解析时机**：一律在 `MqPlugin.start()` 内完成；构造函数只保留 `Prop` 引用。
- **配置读取唯一口径**：后端专有键一律经 `MqConfig.get(relativeKey)`，**不得手写** `"mq.memory."` 等字面量。
- **启动期连通性（后端差异）**：`mqtt` / `mqtt5` / `rabbitmq` 构造期即建连、fail-fast；`kafka` / `redisstream` / `rocketmq` 懒连接——**「启动成功」≠「后端可达」**。
- **Topic 使用模式**：固定少量 topic + payload 内路由键；运行期不增减 topic（`docs/design.md` §5.8）。

## provider 扩展点

新增后端 = **在本 JAR 内新增实现包**：`XxxProvider implements MqProvider`（`public` 无参构造 + `type()` + `createBus(MqConfig)`）+ `XxxMessageBus implements MessageBus` + 在 `src/main/resources/META-INF/services/cn.hg.aifei.mq.internal.MqProvider` 追加一行 provider 全限定类名，并重新发布。`MqProviderRegistry` **不提供** `register` 公开方法。

新增后端的**行为差异须在 `docs/design.md` §5.6 (B) 逐项列明**；后端集成测试默认关闭，开启须连上（见 `docs/testing.md`）。

## 注释规范

- **语言**：一律简体中文。保留英文原文的只有 javadoc 标签名、代码标识符、配置键名、异常消息。
- **javadoc**：公共类型与公共成员必须书写，并写全 `@param` / `@return` / `@throws`；`internal` / 实现包成员在非自明处书写；`aifei` 包公共类写全。
- **精简**：只在代码无法表达「为什么」时书写注释，不复述下一行代码，不写行尾注释，不留被注释掉的死代码 / TODO / 占位符。

## 验证

**命令在 `aifei-mq/` 根目录执行**（门禁测试依赖相对路径 `src/main/java`）：

```bash
mvn clean verify   # 必须 0 failure / 0 error
mvn test -Dtest=MqConfigTest   # 单用例：类名或 类名#方法名
mvn test "-Dtest=RocketMqMessageBusContractTest" "-Drocketmq.integration=true" "-Drocketmq.namesrv-addr=127.0.0.1:9876"
mvn test "-Dtest=MqttMessageBusContractTest" "-Dmqtt.integration=true" "-Dmqtt.broker-url=tcp://127.0.0.1:1883"
mvn test "-Dtest=Mqtt5MessageBusContractTest" "-Dmqtt5.integration=true" "-Dmqtt5.broker-url=tcp://127.0.0.1:1883"
mvn test "-Dtest=RabbitMqMessageBusContractTest" "-Drabbitmq.integration=true" "-Drabbitmq.host=127.0.0.1" "-Drabbitmq.port=5672"
mvn test "-Dtest=KafkaMessageBusContractTest" "-Dkafka.integration=true" "-Dkafka.bootstrap-servers=127.0.0.1:9092"
mvn test "-Dtest=RedisStreamMessageBusContractTest" "-Dredisstream.integration=true" "-Dredisstream.host=127.0.0.1" "-Dredisstream.port=6379"
```

> **集成测试纪律**：6 个集成开关**默认关闭**（未开启时整类经 `Assume` 跳过）；**一旦开启，连不上必须使测试失败**，不得用 `Assume` 掩盖。PowerShell 下 `-D` 参数必须整体加引号。
>
> **实测计数**：`mvn clean verify` → **`Tests run: 150 / Failures: 0 / Errors: 0 / Skipped: 60`**（执行 90 + 跳过 60）；主源码 31 个 / 测试文件 23 个；门禁 6 例。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置），并更新 `docs/testing.md`。
- 术语必须与 `docs/design.md` 统一：主题 topic / 消息 Message / 生产者 Producer / 消费者 Consumer / 监听器 MessageListener / 总线 MessageBus / 后端 provider / 后端标识 type。
