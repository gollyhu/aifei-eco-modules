# aifei-mq 测试策略

本文面向测试维护者：说明测试结构、覆盖口径、运行方式与新增约定。

## 运行方式

命令一律在模块根 `aifei-mq/` 执行（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify     # 必须 0 failure / 0 error
mvn test -Dtest=MqConfigTest        # 单类
mvn test -Dtest=MqConfigTest#shouldDefaultToMemoryWhenTypeAbsent   # 单方法
```

默认可执行的测试**零外部中间件**：内存后端全程无 IO，**6 个真实后端**（`rocketmq` / `mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`）的集成用例**默认关闭**。测试依赖仅 JUnit；6 个客户端在本模块以 `optional` 声明（编译与测试期可见，不向下游传递）。

## 测试结构

| 测试类 | 类型 | 覆盖 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 架构门禁（6 例） | 包边界、公共面 8 类型、services 声明与 provider 类型一致（设计 §12） |
| `MessageBusContractTest` | 契约基类（abstract） | `MessageBus` 的**共同底线**（§5.6 A）；提供异步轮询等待钩子；本身不含独立用例 |
| `MqConfigTest` | 单元 | 配置默认值、类型白名单、实例前缀与后端键读取、命名实例解析与校验 |
| `MessageTest` / `SendResultTest` | 单元 | 值对象校验、防御性拷贝、值语义 |
| `MqValidatorTest` | 单元 | 参数校验规则与异常类型 |
| `MqHolderTest` | 单元 | 多实例登记 / 按名取用 / 清除，取用期异常顺序 |
| `ProducerKitTest` / `ConsumerKitTest` | 单元 | 未启动状态异常、`use(name)` 校验顺序、静态短写转发 |
| `internal.MqProviderRegistryTest` | 单元 | 按 `type()` 建索引、同类型重复实现报错、类型缺失报错且不泄露配置取值 |
| `MemoryMessageBusContractTest` | 契约实现 | 内存后端继承共同底线 + 内存专有用例（单主题 FIFO、多订阅者顺序、`messageId` 递增、同一消息对象、取消末个监听器后保留空列表等） |
| `MqPluginTest` | 单元（装配） | 装配生命周期、多实例隔离、幂等、未注册后端 / 非法实例名 / 命名实例失败 / 框架桥失败均不留半装配 |
| `XxxMessageBusContractTest`（6 个） | 契约实现（集成） | 各后端继承共同底线；**默认关闭**；差异断言不入基类 |
| `XxxConfigValidationTest`（5 个） | 单元（默认可执行） | 缺必填键时构造期在建连之前以 `MqException` 失败；rabbitmq 另覆盖 `ssl.*`（5 例） |

### 实测计数

`mvn clean verify` → **`Tests run: 150 / Failures: 0 / Errors: 0 / Skipped: 60`**：执行 **90**（门禁 6 + 单元 75 + 配置校验 9）+ 跳过 **60**（6 个集成契约类各 10）。

## 架构门禁

`ArchitectureBoundaryTest`（6 例）在模块根执行，按源码文本断言，**不得放宽或绕过**：
① `thirdPartyOnlyInImplementationPackage`；② `frameworkOnlyInBridgePackage`；③ `noSqlReference`；④ `aifeiBridgePackageExists`；⑤ `publicSurfaceExact`（精确 8 类型）；⑥ `providerDeclarationsMatchTypes`（services 恰 7 行、与 `type()` 集合一致）。

## 契约测试约定

`MessageBusContractTest` 以抽象工厂 `protected abstract MessageBus newBus();` 承载契约，新增后端只需新增子类实现 `newBus()`。基类**只断言跨后端共同底线**（§5.6 A）；后端专有行为（投递模型、线程模型、有序性、投递保证与重复、持久化、无订阅者时的消息去留、`messageId` 格式、消费位点 / 消费组、取消的尽力语义）**不得写入基类**。

- **异步后端适配**：真实后端投递异步，基类提供轮询等待钩子，投递类断言一律「等待条件成立（带超时）」；断言使用的容器须线程安全。
- **投递强度**：真实后端为 at-least-once，基类只断言「至少收到且内容正确」，**不得断言「恰好一次」**；「恰好一次」是内存后端特性，落 `MemoryMessageBusContractTest`。
- **取消语义**：「取消后本进程不再派发」「重复 / 未登记取消幂等」「`close()` 后取消抛 `IllegalStateException`」属共同底线；真实后端「按 listener 取消」的尽力语义放各后端子类。

## 真实后端集成测试

6 个后端的端到端用例**默认关闭**（未开启时整类 `Assume` 跳过），采用 `-D<backend>.integration=true` 范式。**一旦开启，连不上必须失败，不得用 `Assume` 掩盖**。

```bash
mvn test "-Dtest=RocketMqMessageBusContractTest" "-Drocketmq.integration=true" "-Drocketmq.namesrv-addr=127.0.0.1:9876"
mvn test "-Dtest=MqttMessageBusContractTest" "-Dmqtt.integration=true" "-Dmqtt.broker-url=tcp://127.0.0.1:1883"
mvn test "-Dtest=Mqtt5MessageBusContractTest" "-Dmqtt5.integration=true" "-Dmqtt5.broker-url=tcp://127.0.0.1:1883"
mvn test "-Dtest=RabbitMqMessageBusContractTest" "-Drabbitmq.integration=true" "-Drabbitmq.host=127.0.0.1" "-Drabbitmq.port=5672"
mvn test "-Dtest=KafkaMessageBusContractTest" "-Dkafka.integration=true" "-Dkafka.bootstrap-servers=127.0.0.1:9092"
mvn test "-Dtest=RedisStreamMessageBusContractTest" "-Dredisstream.integration=true" "-Dredisstream.host=127.0.0.1" "-Dredisstream.port=6379"
```

- **开关名与连接参数须与 `pom.xml` 的 `systemPropertyVariables` 逐字一致**；PowerShell 下 `-D` 必须整体加引号。
- **自清理**：每个用例用随机 topic / 消费组 / 客户端标识隔离，`@After` 关闭总线；broker 侧自动创建的 topic 无法经客户端删除，命名带随机前缀便于运维清理。
- **失败位置随后端而异**：`mqtt` / `mqtt5` / `rabbitmq` 启动期 fail-fast（`newBus()` 即建连）；`rocketmq` / `kafka` / `redisstream` 懒连接（故障延迟到首个收发）——**不得**把「建总线未报错」当作「后端可达」。
- **验证状态**：`rocketmq` / `rabbitmq` / `redisstream` 已真机验证；`mqtt` / `mqtt5` / `kafka` 尚无真机 broker 验证，默认 `mvn clean verify` 的 `Skipped: 60` 即这 6 类各 10 条。

## 新增约定

- 新增或改变**公共行为**时，先在 `docs/design.md` 明确契约，再补测试；已修复的缺陷固化为回归测试。
- 公共行为的契约用例优先加入 `MessageBusContractTest`，后端专有行为放各自子类。
- 测试须独立、幂等：每个用例自建被测对象并自行关闭；`MqHolder` / `AopKit` 等静态状态在 `@Before` / `@After` 内清理。
- 新增**真实后端**时，集成测试默认关闭、用系统属性显式开启；**一旦开启，连不上必须使测试失败**。
