# aifei-mq 测试策略

本文面向测试维护者：说明测试结构、覆盖口径、运行方式与新增约定。

## 运行方式

命令一律在模块根目录（`aifei-mq`）内执行（架构门禁依赖相对路径 `src/main/java`）：

```bash
cd aifei-mq && mvn clean verify     # 必须 0 failure / 0 error
mvn test -Dtest=MqConfigTest        # 单类
mvn test -Dtest=MqConfigTest#shouldDefaultToMemoryWhenTypeAbsent   # 单方法
```

默认可执行的测试**零外部中间件**：内存后端全程无 IO，**6 个真实后端**（`rocketmq` / `mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`）的集成用例**默认关闭**（见下文「真实后端集成测试」）。测试依赖仅 JUnit；各后端客户端均为模块 `optional` 依赖，仅其实现包与集成测试在开启时使用。

## 测试结构

| 测试类 | 类型 | 覆盖 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 架构门禁 | 包边界与公共面大小（源码文本扫描，设计 §12） |
| `MessageBusContractTest` | 契约基类（abstract） | `MessageBus` 的**共同底线**（§5.6 A，含订阅取消：取消后不再派发、幂等 no-op、`close()` 后 `IllegalStateException`）；提供**异步轮询等待钩子**供真实后端复用 |
| `MemoryMessageBusContractTest` | 契约实现 | 内存后端继承运行契约；另加**内存专有**用例（单主题 FIFO、多订阅者登记顺序、`messageId` 十进制递增、投递同一消息对象、多总线独立计数、取消末个监听器后保留空列表等） |
| `RocketMqMessageBusContractTest` | 契约实现（集成） | RocketMQ 后端继承运行**共同底线**契约；**默认关闭**，`-Drocketmq.integration=true` 开启（须连上 broker）；后端差异用例见 `docs/design.md` §5.6 (C) |
| `MqttMessageBusContractTest` | 契约实现（集成） | MQTT v3.1.1 后端继承运行**共同底线**契约；**默认关闭**，`-Dmqtt.integration=true` 开启（须连上 broker）；**无**后端差异断言 |
| `Mqtt5MessageBusContractTest` | 契约实现（集成） | MQTT v5.0 后端继承运行**共同底线**契约；**默认关闭**，`-Dmqtt5.integration=true` 开启；**无**后端差异断言 |
| `RabbitMqMessageBusContractTest` | 契约实现（集成） | RabbitMQ 后端继承运行**共同底线**契约；**默认关闭**，`-Drabbitmq.integration=true` 开启；**无**后端差异断言 |
| `KafkaMessageBusContractTest` | 契约实现（集成） | Kafka 后端继承运行**共同底线**契约；**默认关闭**，`-Dkafka.integration=true` 开启（客户端懒连接，故障延迟到首次收发）；**无**后端差异断言 |
| `RedisStreamMessageBusContractTest` | 契约实现（集成） | Redis Streams 后端继承运行**共同底线**契约；**默认关闭**，`-Dredisstream.integration=true` 开启（连接池懒建连）；**无**后端差异断言 |
| `BackendConfigValidationTest` | 单元（**默认执行**） | 5 个新后端缺必填键时**构造期在建连之前**以 `MqException` 快速失败 + 5 个新类型均已在 provider 注册表；**不建连、无外部中间件依赖** |
| `MqConfigTest` | 单元 | 配置默认值、类型白名单、实例前缀与后端键读取、命名实例解析、`mq.names` 解析与校验 |
| `MessageTest` | 单元 | 值对象校验、双向防御性拷贝、值语义 |
| `SendResultTest` | 单元 | 值对象校验与值语义 |
| `MqValidatorTest` | 单元 | 参数校验规则与异常类型 |
| `MqHolderTest` | 单元 | 多实例登记 / 按名取用 / 清除，以及取用期的状态与参数异常（先状态、后参数） |
| `ProducerUtilTest` | 单元 | `ProducerUtil` 未启动状态异常、`use(name)` 实例名校验与判定顺序、静态短写转发 |
| `ConsumerUtilTest` | 单元 | `ConsumerUtil` 未启动状态异常、`use(name)` 实例名校验与判定顺序、`subscribe` / `unsubscribe` 静态短写转发 |
| `MqPluginTest` | 单元 | 装配生命周期、多实例装配与实例隔离、幂等、未注册后端 / 非法实例名 / 命名实例失败 / 框架桥失败均不留半装配 |

### 逐类实测用例数（`mvn clean verify`）

总 `Tests run: 143 / Failures: 0 / Errors: 0 / Skipped: 60`，即**执行 83 项** + **默认跳过 60 项**（6 个真实后端集成**默认关闭**，每个 10 条）：

| 测试类 | 用例数 | 备注 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 5 | 架构门禁 |
| `BackendConfigValidationTest` | 6 | 单元（5 个新后端缺必填键快速失败 + 新类型已注册；默认执行、不建连） |
| `ConsumerUtilTest` | 4 | 单元 |
| `MemoryMessageBusContractTest` | 20 | 契约：共同底线 10 + 内存专有 10 |
| `MessageTest` | 5 | 单元 |
| `MqConfigTest` | 11 | 单元 |
| `MqHolderTest` | 5 | 单元 |
| `MqPluginTest` | 12 | 单元 |
| `MqValidatorTest` | 8 | 单元 |
| `ProducerUtilTest` | 4 | 单元 |
| `SendResultTest` | 3 | 单元 |
| `RocketMqMessageBusContractTest` | 10（全部跳过） | 集成，默认关闭 |
| `MqttMessageBusContractTest` | 10（全部跳过） | 集成，默认关闭 |
| `Mqtt5MessageBusContractTest` | 10（全部跳过） | 集成，默认关闭 |
| `RabbitMqMessageBusContractTest` | 10（全部跳过） | 集成，默认关闭 |
| `KafkaMessageBusContractTest` | 10（全部跳过） | 集成，默认关闭 |
| `RedisStreamMessageBusContractTest` | 10（全部跳过） | 集成，默认关闭 |

> 上述数字以 `aifei-mq` 模块根目录内 `mvn clean verify` 的 surefire 输出为准（`MessageBusContractTest` 为 abstract 基类，不含独立用例，其共同底线用例经 `MemoryMessageBusContractTest` 运行计入该行的 20 项）。

## 架构门禁（`ArchitectureBoundaryTest`）

扫描 `src/main/java` 下全部 `.java`，按源码文本断言 5 条（设计 §12.1），**不得放宽或绕过**：

1. `thirdPartyOnlyInImplementationPackage`：第三方 / 其它模块 import 只允许出现在实现包（根包 / `internal` / `aifei` 包均禁止）。
2. `frameworkOnlyInBridgePackage`：`import cn.aifei.*`（除 `cn.hg.aifei.mq.*`）只允许出现在 `cn.hg.aifei.mq.aifei`。
3. `noSqlReference`：全模块禁止 `import java.sql.`。
4. `aifeiBridgePackageExists`：接入包存在且含 `MqPlugin.java`。
5. `publicSurfaceExact`：根包公共类型精确 8 个，且文件名集合精确等于那 8 个。

> 门禁扫描源码文本，因此**必须在模块根目录内执行**；在仓库根执行会因找不到 `src/main/java` 而失败。

## 契约测试约定

`MessageBusContractTest` 以抽象工厂 `protected abstract MessageBus newBus();` 承载契约，新增后端只需新增一个
子类实现 `newBus()` 即可复用全部用例。**契约基类只断言跨后端的「共同底线」**（`docs/design.md` §5.6 A）；**后端专有行为**（投递模型、线程模型、有序性、投递保证与重复、持久化、无订阅者时的「消息丢弃 / 留存」（B6）、`messageId` 格式、可观测性、消费位点 / 消费组、取消订阅的尽力语义，即 §5.6 B / C）**不得写入契约基类**，须放各后端子类的测试。

**本轮（R 轮）5 个新后端同样适用**：`mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream` 的子类**只继承运行共同底线**、**不写任何后端差异断言**（差异逐项见 `docs/design.md` §5.6 (B) / (C) 及其「实现期决策」补记）；其运行期语义**均未经真机 broker 验证**，须在具备环境时开启对应开关执行（见下文「真实后端集成测试」）。

**异步后端适配（阶段 2 新增）**：真实后端投递**异步**（§5.6 B1 / B2），故基类提供**轮询等待钩子**（受保护的 `awaitTrue(condition, timeoutMillis)` 一类方法），投递类断言一律「发送后**等待条件成立（带超时）**」而非立即断言；断言使用的收集容器须**线程安全**（如 `CopyOnWriteArrayList`），以免与后端消费线程竞态。同步后端（memory）在同一钩子下**首轮即通过**。

**投递强度（(A) / (B) 边界）**：真实后端为 **at-least-once**（§5.6 B4）、故障 / 重试 / 位点回退下**可能重复消费**，故契约基类**只断言「至少收到且内容正确」，不得断言「恰好一次」**；「恰好一次」是内存后端的特性（B4 差异），其断言落**内存专有**子类 `MemoryMessageBusContractTest`。

**基类用例（§5.6 (A) 共同底线，各后端继承运行）**：

- `shouldDeliverToSubscriber`：生产 → 订阅 → 消费闭环——订阅者**至少收到**该消息且**内容 / 载荷正确**（基类**不**断言「恰好一次」，见下「投递强度」），返回带消息标识的结果。
- `shouldSucceedWithoutSubscribers`：无订阅者时 `send` **仍成功返回 `SendResult`**（含 `messageId`）——即 **§5.6 A5 共同底线**；**不**断言「消息是否留存 / 丢弃」（后者属 **§5.6 B6** 差异，不在本用例断言范围）。
- `shouldCloseIdempotently`：关闭幂等。
- `shouldRejectSendAfterClose`：关闭后发送抛 `IllegalStateException`。
- `shouldRejectSubscribeAfterClose`：关闭后订阅抛 `IllegalStateException`。
- `shouldIsolateListenerException`：监听器异常就地隔离，不中断其余订阅者。
- `shouldStopDeliveringAfterUnsubscribe`：订阅后取消，再发送，该监听器**不再收到**；同主题其他监听器仍收到（**共同底线 A3**）。
- `shouldUnsubscribeIdempotently`：重复取消、取消**从未登记过的**订阅均**幂等静默 no-op**（不抛）。
- `shouldRejectUnsubscribeAfterClose`：`close()` 后取消抛 `IllegalStateException`（**共同底线 A9**）。
- `shouldRemoveByInstanceIdentityNotEquals`：取消按**实例身份**（引用相等）移除，而非按 `equals`——两个 `equals` 判等但并非同一实例的监听器，取消其一后仅该实例被移除、另一实例仍收到消息（锁定「按 `equals` 移除」的实现退化）。

**已移出基类、移入 `MemoryMessageBusContractTest` 的用例（§5.6 B 差异，**非**共同底线）**：

- `shouldPreserveSingleTopicOrder`（单主题 FIFO；对齐 B3）
- `shouldInvokeSubscribersInRegistrationOrder`（多订阅者按登记顺序回调；内存专有）
- `shouldAssignIncreasingMessageId`（`messageId` 十进制递增格式；对齐 B7）
- `shouldDeliverSameMessageInstance`（投递发送时构造的同一消息对象；真实后端按值重建）

> **为何移出**：这 4 项在 §5.6 中明确**不属共同底线**（§5.1 分层提示亦然），真实后端（RocketMQ）下**不成立**（可能乱序 / 异步 / broker 侧 `messageId` / 反序列化重建）。留在基类会使真实后端**必然失败**。此为**对齐 §5.6 的同步修订**（不改动 §5.6 (A) / (B) 定档）：基类只留共同底线，内存专有行为落 `MemoryMessageBusContractTest`。

> **取消语义的落点**：「取消后本进程不再向该 listener 派发该主题消息」「重复取消 / 取消未登记的订阅为幂等 no-op」「`close()` 后取消抛 `IllegalStateException`」属**共同底线**（§5.6 A3 / A9），入契约基类 `MessageBusContractTest`（所有后端继承运行）；而**真实后端在拉取 / 消费组模型下「按 listener 取消」的尽力语义**（取消不保证 broker 侧立即撤销、重试 / 位点回退下仍可能到达，§5.6 B10）属**后端差异**，须放各后端子类测试；内存后端的「取消最后一个监听器后保留空列表」等实现细节亦只放 `MemoryMessageBusContractTest`。`unsubscribe` 的参数非法（`topic` 空 / 空白、`listener` 为 `null`）与 `subscribe` 一致，复用 `MqValidator`，由单元测试 `MqValidatorTest` 覆盖。

## 真实后端集成测试

真实后端端到端用例**默认关闭**（未开启时整类跳过，**不得**把「未连上」静默当作通过），采用 `aifei-cache`
`-Dredis.integration=true` 同款范式。

### RocketMQ

- **开关**：`-Drocketmq.integration=true`（未开启时整类 `Assume` 跳过）。
- **连接参数**：`-Drocketmq.namesrv-addr=host:port`（默认 `127.0.0.1:9876`）。
- **开启后连不上必须失败**（不得用 `Assume` 掩盖）：nameserver / broker 不可达时用例**失败**。**须注意失败出现的位置**：`DefaultMQProducer#start()` **不校验 nameserver 连通性**（懒连接），故 `@Before` 创建总线（`newBus()` → `producer.start()`）**通常不会**在此处报错；故障一般在**首个 `send` / `subscribe`** 处以 `MqException` 使用例失败。因此**不得**把「建总线未报错」当作「后端可达」；且**仅创建 / 关闭而不做真实收发**的用例（如 `close()` 幂等、关闭后调用抛 `IllegalStateException`）在 nameserver 不可达时**可能仍通过**——判定后端可用性须看真正收发的用例。
- **自清理**：每个用例用**随机 topic 前缀**（如 `aifeimq_<uuid>`）与**随机消费组**后缀隔离实例；`@After` 关闭总线。broker 侧被自动创建的 topic **无法经客户端删除**，故命名带随机前缀以便识别与运维清理。
- **命令**：

  ```bash
  mvn test -Dtest=RocketMqMessageBusContractTest -Drocketmq.integration=true -Drocketmq.namesrv-addr=127.0.0.1:9876
  ```

### 本轮 5 个新后端（`mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`）

5 个子类与 RocketMQ **同范式**：未开启时整类经 `Assume` 跳过；**一旦开启，连不上必须使测试失败**（不得静默跳过）；每个用例新建总线、以随机主题 / 消费组 / 客户端标识隔离，`@After` 兜底关闭。开关名与连接参数**须与 `pom.xml` 的 `systemPropertyVariables` 逐字一致**：

| 后端 | 测试类 | 开关（默认关闭） | 连接参数 |
| --- | --- | --- | --- |
| `mqtt` | `MqttMessageBusContractTest` | `-Dmqtt.integration=true` | `-Dmqtt.broker-url=tcp://127.0.0.1:1883` |
| `mqtt5` | `Mqtt5MessageBusContractTest` | `-Dmqtt5.integration=true` | `-Dmqtt5.broker-url=tcp://127.0.0.1:1883` |
| `rabbitmq` | `RabbitMqMessageBusContractTest` | `-Drabbitmq.integration=true` | `-Drabbitmq.host=127.0.0.1` / `-Drabbitmq.port=5672` |
| `kafka` | `KafkaMessageBusContractTest` | `-Dkafka.integration=true` | `-Dkafka.bootstrap-servers=127.0.0.1:9092` |
| `redisstream` | `RedisStreamMessageBusContractTest` | `-Dredisstream.integration=true` | `-Dredisstream.host=127.0.0.1` / `-Dredisstream.port=6379` |

- **命令**：

  ```bash
  mvn test -Dtest=MqttMessageBusContractTest -Dmqtt.integration=true -Dmqtt.broker-url=tcp://127.0.0.1:1883
  mvn test -Dtest=Mqtt5MessageBusContractTest -Dmqtt5.integration=true -Dmqtt5.broker-url=tcp://127.0.0.1:1883
  mvn test -Dtest=RabbitMqMessageBusContractTest -Drabbitmq.integration=true -Drabbitmq.host=127.0.0.1 -Drabbitmq.port=5672
  mvn test -Dtest=KafkaMessageBusContractTest -Dkafka.integration=true -Dkafka.bootstrap-servers=127.0.0.1:9092
  mvn test -Dtest=RedisStreamMessageBusContractTest -Dredisstream.integration=true -Dredisstream.host=127.0.0.1 -Dredisstream.port=6379
  ```

- **失败出现的位置（按连通性模型不同）**：`mqtt` / `mqtt5` / `rabbitmq` 为**启动期 fail-fast**——`newBus()` 建总线即建连，broker 不可达时通常在**建总线处**即以 `MqException` 失败；`kafka` / `redisstream` 为**懒连接**（`redisstream` 连接池懒建连）——建总线**通常不报错**，故障延迟到**首个收发**处。故**不得**把「建总线未报错」当作「后端可达」。

> **验证盲区（重要）**：本仓库开发机**无 Docker、无任何真实 broker（含 Redis）**（`docker` 命令不存在）；RocketMQ 需 **nameserver + broker 两进程**，其余各需对应中间件。故上述**全部集成测试当前无法在本机执行**——**6 个后端的运行期语义（连通性、消费组 / 位点、重投与重复、通配匹配等）均未经真机 broker 验证**；具备环境后须执行上述命令补验。默认的 `mvn clean verify`（集成关闭）**不含**任何真实后端端到端验证（其 `Skipped: 60` 即这 6 类各 10 条）。

## 新增约定

- 新增或改变**公共行为**时，先在 `docs/design.md` 明确契约，再补测试；已修复的缺陷固化为回归测试。
- 公共行为的契约用例优先加入 `MessageBusContractTest`（所有后端共用），后端专有行为放各自子类。
- 测试须独立、幂等：每个用例自建被测对象并自行关闭；`MqHolder` / `AopKit` 等静态状态在 `@Before` / `@After` 内清理。
- 新增**真实后端**（如 RocketMQ）时，其**集成测试默认关闭**，用系统属性显式开启（对齐 `aifei-cache` 的 `-Dredis.integration=true` 范式，如 `-Drocketmq.integration=true`）；**一旦开启，连不上必须使测试失败**，不得静默跳过。契约基类仍只跑共同底线，真实后端的行为差异放其子类（含已移出基类的内存专有用例）。
