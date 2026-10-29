# aifei-mq 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档的分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **改代码前必读 `docs/design.md`**。需求与现有设计冲突时，先明确并更新设计决策，再改代码。
- 未经设计决策，不得自行确定契约类行为（投递语义、异常类型与消息、关闭语义、配置键名等），也不得新增或扩大公共 API 面。

## 交付性质

`aifei-mq` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**：没有 `AppConfig` / `Launcher` / `service/` 包 / `webapp/` / HTTP 路由 / HIO / 表结构 / Db+Row / Generator，只有「公共 API + 内部实现 + 一个装配用 `MqPlugin`」。

## 硬性约束

### 技术基线

- 代码、依赖与 API 必须兼容 **Java 8**：**语法**与 **API 双约束**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching 等 Java 8 之后语法；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 之后 API。
- 主 JAR 内全部 class 的 major version 必须为 **52**。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；文件头版权块属归属信息，不得改写。
- **`cn.aifei:aifei-all` 为 `provided`**：消费方自带，不向下游传递。
- **第三方依赖分层**：内存后端**零第三方运行依赖**；真实后端**按其 provider 实现包自带 `optional` 依赖**（如 `org.apache.rocketmq:rocketmq-client:4.9.7`），**不向下游传递**；公共面（根包 / `internal` / `aifei`）**不得**出现第三方类型，也**不引入日志绑定**。**本轮新增 5 个 `optional` 客户端**（均 `<optional>true</optional>`、**仅各自实现包使用、不向下游传递**）：`org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`（`mqtt`）、`org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5`（`mqtt5`）、`com.rabbitmq:amqp-client:5.21.0`（`rabbitmq`）、`org.apache.kafka:kafka-clients:3.9.1`（`kafka`，**必须停 3.x**，4.x 要求 Java 11+）、`redis.clients:jedis:8.0.1`（`redisstream`，与 `aifei-cache` 版本对齐、Jedis 直连）；除这 5 个客户端外**不新增任何直接依赖**，也**不得引入任何日志绑定**（`slf4j-simple` / `logback` / `log4j` 等一律禁止；`slf4j-api` 只以**门面**身份随传递依赖出现）。

### 公共面收敛（不得放宽）

- **公共 API 零第三方类型、零框架类型**。
- 根包公共类型固定为 **8 个**：`Message` / `Producer` / `Consumer` / `MessageListener` / `SendResult` / `MqException` / `ProducerUtil` / `ConsumerUtil`。新增公共类型必须先过设计决策。（`MqKit` 已删除、拆为 `ProducerUtil` + `ConsumerUtil`，见 `docs/design.md` §3.7 / §13 第 25 条。）
- `internal` / `memory` / `aifei` 均属**非承诺 API**，消费方不得引用。
- 公共面（根包）不得出现 `internal` 包行话（唯一定义点、注册表、总线、视图等）。

## 包边界与门禁

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| 第三方 / 其它模块 import | 仅实现包（`memory` / `rocketmq` / `mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`） | `ArchitectureBoundaryTest#thirdPartyOnlyInImplementationPackage` |
| `cn.aifei.*`（除 `cn.hg.aifei.mq.*`，框架类型） | 仅 `cn.hg.aifei.mq.aifei` | `ArchitectureBoundaryTest#frameworkOnlyInBridgePackage` |
| `java.sql.*` | **全模块禁止** | `ArchitectureBoundaryTest#noSqlReference` |

另有门禁 `ArchitectureBoundaryTest#aifeiBridgePackageExists`：断言接入包非空且 `MqPlugin.java` 存在；`publicSurfaceExact`：根包公共类型**精确 8 个**且文件名集合精确等于那 8 个。

`ArchitectureBoundaryTest` 扫描 `src/main/java` 下全部 `.java` 断言上述边界，因此**必须在 `aifei-mq` 模块根目录执行**。**不得为兼容错误用法而放宽或绕过门禁断言。**

> 关键推论：`internal` 与 `memory` **一律不得 import `cn.aifei.*`**；`MqPlugin` 是全模块唯一 import 框架类型的类。
>
> **本轮（R 轮）新增 5 个实现包**（`mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`，各 4 个类）的门禁合规性：这 5 个包的文件路径**既非**根包直接子文件、**也不含** `/internal/`、**也不含** `cn/hg/aifei/mq/aifei/`，故门禁① `thirdPartyOnlyInImplementationPackage` **自动放行**（其 `org.eclipse.paho.*` / `com.rabbitmq.*` / `org.apache.kafka.*` / `redis.clients.*` import 合规，与既有 `memory` / `rocketmq` 同构）；**根包仍精确 8 类型**（新类全部落子包，门禁⑤ `publicSurfaceExact` 不受影响）；`internal` 与 5 个实现包**一律不得 import `cn.aifei.*`**（配置经 `internal.MqConfig` 读取，不经框架类型），`MqPlugin` 仍是全模块唯一 import 框架类型的类。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 参数校验（topic / payload / text / message / messageId / listener / producer / consumer） | `internal.MqValidator` |
| 配置域与键常量、`mq.type` 解析与校验、`mq.names` 解析与实例名校验、`<实例前缀>.<type>.<relative>` 键拼装 | `internal.MqConfig` |
| `String` → `byte[]` 编码（UTF-8） | `internal.TextCodec` |
| 运行期实例注册表（多实例，按实例名登记 / 取用） | `internal.MqHolder` |
| 总线契约 | `internal.MessageBus` |
| 后端扩展点契约 | `internal.MqProvider` |
| provider 装配骨架 | `internal.AbstractMqProvider` |
| 后端注册表（静态初始化块一行） | `internal.MqProviderRegistry` |
| 内存订阅登记与取消、投递、`close()`、`messageId` 生成 | `memory.MemoryMessageBus` |
| 后端标识字面量 `"memory"` | `internal.MqConfig.DEFAULT_TYPE`（`MemoryMqProvider.TYPE` 引用之） |
| 后端标识字面量 `"rocketmq"` | `rocketmq.RocketMqProvider.TYPE` |
| RocketMQ 订阅登记、投递（同步 `send`）、`close()`、建连 | `rocketmq.RocketMqMessageBus` |
| RocketMQ 后端专有配置键名（`namesrv-addr` / `producer-group` / `consumer-group`） | `rocketmq.RocketMqMessageBus`（键名常量集中于此；前缀经 `MqConfig.get(relativeKey)` 拼装，**不得手写** `"mq.rocketmq."`） |
| 后端标识字面量 `"mqtt"` | `mqtt.MqttProvider.TYPE` |
| 后端标识字面量 `"mqtt5"` | `mqtt5.Mqtt5Provider.TYPE` |
| 后端标识字面量 `"rabbitmq"` | `rabbitmq.RabbitMqProvider.TYPE` |
| 后端标识字面量 `"kafka"` | `kafka.KafkaProvider.TYPE` |
| 后端标识字面量 `"redisstream"` | `redisstream.RedisStreamProvider.TYPE` |
| 各新后端专有配置键名（`docs/design.md` §4.6–§4.10 各键表；**一后端一处**） | 各后端**各自**的 `XxxMessageBus`：`mqtt.MqttMessageBus` / `mqtt5.Mqtt5MessageBus` / `rabbitmq.RabbitMqMessageBus` / `kafka.KafkaMessageBus` / `redisstream.RedisStreamMessageBus`（键名常量集中于此；前缀经 `MqConfig.get(relativeKey)` 拼装，**不得手写** `"mq.mqtt."` / `"mq.kafka."` 等字面量） |
| 各新后端的订阅登记 / 投递 / 建连 / `close()`（**一后端一处**） | 各后端**各自**的 `XxxMessageBus`：`mqtt.MqttMessageBus`（构造内建连 + 投递 + `close()`）/ `mqtt5.Mqtt5MessageBus`（同构）/ `rabbitmq.RabbitMqMessageBus`（构造内建连；首次 `subscribe` 声明队列并投递）/ `kafka.KafkaMessageBus`（构造内建 producer；首次 `subscribe` 起拉取循环）/ `redisstream.RedisStreamMessageBus`（连接池；首次 `subscribe` 建组并起拉取循环）。视图类（`XxxProducer` / `XxxConsumer`）**只委托**，不得复制登记 / 建连 / 投递逻辑 |

## 行为契约要点速查

> 本清单描述的是**内存后端**的行为；其中「同步投递 / 无线程池与背压 / 单主题 FIFO / `messageId` 格式」等**不是跨后端共同底线**。**「无订阅者」须区分**：**`send` 仍成功返回 `SendResult`（`messageId` 已分配）属共同底线（§5.6 A5）**；**消息被丢弃还是留存属后端差异（§5.6 B6）**。跨后端的**共同底线**与**允许差异**见 `docs/design.md` §5.6；**新增后端的差异须在 §5.6（B）逐项列明**，共同底线不得更改。

- **投递语义**：同步投递；回调在生产者调用线程内直接执行；无线程池 / 队列 / 背压。
- **有序范围**：单主题 FIFO；同一主题多个订阅者按 `subscribe` 登记顺序回调；不保证跨主题顺序。
- **无订阅者**：`send` 仍成功返回 `SendResult`（`messageId` 已分配）——属**共同底线**（§5.6 A5）；消息**被丢弃还是留存**随后端而变（内存后端丢弃，broker 独立存储，§5.6 B6）。
- **取消订阅**：`unsubscribe` 从该主题登记列表中按**实例身份**移除该 listener（**不用 `equals`**——`MessageListener` 是函数式接口，lambda / 方法引用的 `equals` 无可靠语义）；**重复取消 / 取消未登记的订阅**均**幂等静默 no-op**（不抛）；参数非法抛 `IllegalArgumentException`、`close()` 后抛 `IllegalStateException`；「**取消后本进程不再向该 listener 派发该主题消息**」属**共同底线**（§5.6 A3），真实后端「按 listener 取消」属**尽力语义**（§5.6 B10）。取消最后一个监听器后**保留该主题的空列表**（不移除主题键）。
- **`messageId`（内存后端）**：进程内全局单调递增的长整型十进制字符串（`Long.toString(messageSeq.incrementAndGet())`）。
- **关闭语义**：`close()` 幂等；关闭后 `send` / `subscribe` / `unsubscribe` 抛 `IllegalStateException`；同步投递下无在途消息，无需 drain。
- **监听器异常**：由总线就地隔离捕获，不中断同主题其他订阅者、不上抛给生产者（本版静默丢弃，不引入日志依赖）。
- **异常分类**：参数非法 `IllegalArgumentException`；状态非法 `IllegalStateException`；运行时故障 `MqException`。内存后端无外部运行时故障源，本版不会从 `memory` 抛出 `MqException`。
- **provider 必须无状态**：不得声明实例字段保存解析结果，配置在 `createBus(config)` 内经参数现读现用。
- **配置解析时机**：一律在 `MqPlugin.start()` 内完成；构造函数只保留 `Prop` 引用，不解析、不校验、不建总线。
- **配置读取唯一口径**：后端专有键一律经 `MqConfig.get(relativeKey)`（内部拼 `prefix + "." + relativeKey`），**不得手写** `"mq.memory."` 等字面量。
- **启动期连通性（后端差异）**：`mqtt` / `mqtt5` / `rabbitmq` **构造期即建连、fail-fast**——broker 不可达则 `MqPlugin.start()` 失败并回滚；`kafka` / `redisstream`（及既有 `rocketmq`）为**懒连接**——**「启动成功」≠「后端可达」**，故障延迟到首次收发才以 `MqException` 暴露（`docs/design.md` §5.6 B12 与 (C) 各表 B12 行）。
- **`topic` 语义（后端差异）**：MQTT（`mqtt` / `mqtt5`）的 `topic` 是**过滤器**（支持 `+` / `#` 通配；`Consumer.subscribe` 签名不变）；其余后端（`memory` / `rocketmq` / `rabbitmq` / `kafka` / `redisstream`）为**精确名**（RabbitMQ 为队列名 / routing key，Kafka 为 topic 名，Redis Streams 为流键）。**精确匹配是通配匹配的特例**（`docs/design.md` §5.6 B14）。
- **Topic 使用模式（E2 定档，`docs/design.md` §5.8）**：**固定少量 topic + payload 内路由键**是既定设计决策——topic 表达稳定业务域、事件类型进 payload、订阅侧本地分发、运行期不增减 topic。触及 topic 匹配 / `send` / `subscribe` 的代码时检查点：① 公共 API 面 **8 类型不得增加**（§5.8.3 零改动论证）；② **不得**引入「运行期动态 topic」机制——broker 自动创建开关、通配公共参数、预声明 API（如 `Consumer.declare`）、运行期后端重建均已被否决（否决理由见 §5.8.2，其中 `AopKit` 无移除单例 API 已经官方源码核实）；③ 确需偏离时按**回弹机制**停下并回报调度人 / 项目所有者，**先更新 `docs/design.md` §5.8 再动代码**。

## provider 扩展点

新增后端 = 新增 `XxxMqProvider extends AbstractMqProvider` + 在 `MqProviderRegistry` 的静态初始化块注册一行；**不回头改 `MqPlugin` 装配逻辑**。`MqProviderRegistry` **不提供** `register` 公开方法。

新增后端的**行为差异须在 `docs/design.md` §5.6（B）逐项列明**，**共同底线（§5.6 A）不得更改**；真实后端还须按 §5.6（C）的样式补出本后端的**逐项实际落点**；后端集成测试默认关闭，开启须连上（见 `docs/testing.md`）。

真实后端（如 `rocketmq`）的**建连时机 / 线程模型 / `close()` 语义**全部落在其**实现包内**：producer 在总线构造（= `MqPlugin.start()` 链路）内启动；consumer 因订阅主题启动时未知而**延迟到首次 `subscribe`** 启动。**「启动期失败、不留半装配」只适用于配置错误**（未注册后端 / 必填键缺失或非法）——**不得假设「启动期必连上真实后端」**：如 RocketMQ 的 `DefaultMQProducer#start()` **不校验 nameserver 连通性**（懒连接），nameserver 不可达时启动**仍成功**，故障延迟到首次使用（`docs/design.md` §5.6 B12）。**provider 必须无状态**，配置只在 `buildBus(config)` 内经 `MqConfig.get(relativeKey)` 现读现用。

## 注释规范

- **语言**：一律简体中文。保留英文原文的只有 `@param` / `@return` / `@throws` 等 javadoc 标签名、代码标识符、配置键名、异常消息。
- **javadoc**：公共类型与公共成员必须书写，并写全 `@param` / `@return` / `@throws`；`internal` / `memory` 成员在非自明处书写；`aifei` 包公共类写全。
- **精简**：只在代码无法表达「为什么」时书写注释，不复述下一行代码，不写行尾注释，不留被注释掉的死代码 / TODO / 占位符。

## 验证

**命令一律在模块目录内执行**（门禁测试依赖相对路径 `src/main/java`）：

```bash
cd aifei-mq && mvn clean verify   # 必须 0 failure / 0 error
mvn test -Dtest=MqConfigTest      # 单用例：类名或 类名#方法名
mvn test -Dtest=RocketMqMessageBusContractTest -Drocketmq.integration=true -Drocketmq.namesrv-addr=127.0.0.1:9876   # RocketMQ 集成测试（默认关闭，开启须连上）
mvn test -Dtest=MqttMessageBusContractTest -Dmqtt.integration=true -Dmqtt.broker-url=tcp://127.0.0.1:1883          # MQTT v3（默认关闭，开启须连上）
mvn test -Dtest=Mqtt5MessageBusContractTest -Dmqtt5.integration=true -Dmqtt5.broker-url=tcp://127.0.0.1:1883        # MQTT v5（同上）
mvn test -Dtest=RabbitMqMessageBusContractTest -Drabbitmq.integration=true -Drabbitmq.host=127.0.0.1 -Drabbitmq.port=5672   # RabbitMQ（同上）
mvn test -Dtest=KafkaMessageBusContractTest -Dkafka.integration=true -Dkafka.bootstrap-servers=127.0.0.1:9092        # Kafka（同上）
mvn test -Dtest=RedisStreamMessageBusContractTest -Dredisstream.integration=true -Dredisstream.host=127.0.0.1 -Dredisstream.port=6379   # Redis Streams（同上）
```

> **集成测试纪律**：上述 5 个集成开关（连同既有 `-Drocketmq.integration`）**默认关闭**（未开启时整类经 `Assume` 跳过）；**一旦开启，连不上必须使测试失败**，**不得**用 `Assume` 掩盖。开关名与连接参数须与 `pom.xml` 的 `systemPropertyVariables` **逐字一致**（见 `docs/testing.md`）。
>
> **文件 / 用例计数（实测）**：主源码 **45** 个（`src/main/java`）/ 测试 **18** 个（`src/test/java`）；模块内 `mvn clean verify` 实测 **`Tests run: 143 / Failures: 0 / Errors: 0 / Skipped: 60`**（执行 **83** 项 + 跳过 **60** 项；跳过的 60 项为 6 个集成子类各 10 条，默认关闭）。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置），并更新 `docs/testing.md`。
- 术语必须与 `docs/design.md` 统一：主题 topic / 消息 Message / 生产者 Producer / 消费者 Consumer / 监听器 MessageListener / 总线 MessageBus / 后端 provider / 后端标识 type。
