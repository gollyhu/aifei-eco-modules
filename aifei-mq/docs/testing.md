# aifei-mq 测试策略

本文面向测试维护者：说明测试结构、覆盖口径、运行方式与新增约定。

## 运行方式

命令一律在**父工程 `aifei-mq/`（父 `pom`）根目录**执行：`mvn clean verify` 经 reactor **逐模块**构建与测试（架构门禁依赖各子模块相对路径 `src/main/java`）；调试单个模块时用 `mvn -pl aifei-mq-<module> test`——**聚合后必须加 `-pl`**，否则其它模块找不到该测试类而失败。

```bash
cd aifei-mq && mvn clean verify     # 父工程：reactor 逐模块，必须 0 failure / 0 error
mvn -pl aifei-mq-core test -Dtest=MqConfigTest        # 单模块单类
mvn -pl aifei-mq-core test -Dtest=MqConfigTest#shouldDefaultToMemoryWhenTypeAbsent   # 单模块单方法
```

默认可执行的测试**零外部中间件**：内存后端全程无 IO，**6 个真实后端**（`rocketmq` / `mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`）的集成用例**默认关闭**（见下文「真实后端集成测试」）。测试依赖仅 JUnit；各后端客户端在**各自子模块**内以 `compile` 声明（引该子模块即传递获得），只有该模块的实现包与集成测试使用。

## 测试结构

测试按子模块分布（每个子模块自成一个 JAR、自跑门禁）：`aifei-mq-core`（公共 API + `internal` + 契约基类 + 核心单元测试）、`aifei-mq-memory`（内存后端契约实现 + 装配测试）、7 个后端模块（集成契约子类 + 各自门禁；其中 5 个实验性后端另有配置校验，`rocketmq` 无独立配置校验类）。

| 模块 | 测试类 | 类型 | 覆盖 |
| --- | --- | --- | --- |
| core | `ArchitectureBoundaryTest` | 架构门禁（6 例） | 公共面包边界与公共面大小（源码文本扫描，设计 §12；含 `coreNoThirdParty`） |
| core | `MessageBusContractTest` | 契约基类（abstract，经 core 的 `test-jar` 供各后端复用） | `MessageBus` 的**共同底线**（§5.6 A，含订阅取消：取消后不再派发、幂等 no-op、`close()` 后 `IllegalStateException`）；提供**异步轮询等待钩子**供真实后端复用；本身不含独立用例 |
| core | `MqConfigTest` | 单元 | 配置默认值、类型白名单、实例前缀与后端键读取、命名实例解析、`mq.names` 解析与校验 |
| core | `MessageTest` / `SendResultTest` | 单元 | 值对象校验、双向防御性拷贝、值语义 |
| core | `MqValidatorTest` | 单元 | 参数校验规则与异常类型 |
| core | `MqHolderTest` | 单元 | 多实例登记 / 按名取用 / 清除，以及取用期的状态与参数异常（先状态、后参数） |
| core | `ProducerKitTest` / `ConsumerKitTest` | 单元 | `ProducerKit` / `ConsumerKit` 未启动状态异常、`use(name)` 实例名校验与判定顺序、静态短写转发 |
| core | `MqProviderRegistryTest` | 单元 | 按 `type()` 建索引、同类型重复实现报错（列出冲突类名）、类型缺失报错且**不泄露配置取值** |
| memory | `MemoryMessageBusContractTest` | 契约实现 | 内存后端继承运行契约；另加**内存专有**用例（单主题 FIFO、多订阅者登记顺序、`messageId` 十进制递增、投递同一消息对象、多总线独立计数、取消末个监听器后保留空列表等） |
| memory | `MqPluginTest` | 单元（装配） | 装配生命周期、多实例装配与实例隔离、幂等、未注册后端 / 非法实例名 / 命名实例失败 / 框架桥失败均不留半装配 |
| memory | `MemoryBoundaryGateTest` | 架构门禁（3 例） | 内存后端实现包边界、禁 `java.sql`、`META-INF/services` 声明与 `type()` 一致 |
| rocketmq / mqtt / mqtt5 / rabbitmq / kafka / redisstream | `XxxMessageBusContractTest` | 契约实现（集成） | 各后端继承运行**共同底线**契约；**默认关闭**，对应开关开启时运行（须连上 broker）；后端差异断言**不入契约基类**——6 个集成契约子类**目前只继承共同底线、差异未另写断言**（差异口径见 `docs/design.md` §5.6 (B) / (C)） |
| mqtt / mqtt5 / rabbitmq / kafka / redisstream | `XxxConfigValidationTest` | 单元（**默认执行**，各 1 例） | 该后端缺必填键时**构造期在建连之前**以 `MqException` 快速失败，且该类型已在 provider 注册表；**不建连、无外部中间件依赖** |
| 7 个后端子模块 | `XxxBoundaryGateTest` | 架构门禁（各 3 例） | 第三方客户端 import 只在实现包、禁 `java.sql`、`META-INF/services` 单行声明且与 `type()` 一致 |

### 实测用例数（`cd aifei-mq && mvn clean verify`）

reactor 合计 `Tests run: 167 / Failures: 0 / Errors: 0 / Skipped: 60`，即**执行 107 项** + **默认跳过 60 项**（6 个真实后端集成**默认关闭**，每个 10 条）。按模块拆解：core **49**（单元 43 + 门禁 6）/ memory **35**（契约 20 + 装配 12 + 门禁 3）/ rocketmq **13**（集成 10 跳过 + 门禁 3）/ 5 个实验性后端各 **14**（集成 10 跳过 + 门禁 3 + 配置校验 1）。`MessageBusContractTest` 为 abstract 基类、不含独立用例（其共同底线用例经 `MemoryMessageBusContractTest` 运行计入该行 20 项）；**逐类用例数与父工程 surefire 输出一致，明细以 `docs/design.md` §12.3 为准**，本文不重复维护。

## 架构门禁（`ArchitectureBoundaryTest` + 各后端子模块 `XxxBoundaryGateTest`）

门禁**按子模块分布执行**：每个门禁类只扫描**本模块** `src/main/java` 下全部 `.java`，按源码文本断言（设计 §12.1 / §12.6），**不得放宽或绕过**；因扫描依赖相对路径 `src/main/java`，必须在父工程 `aifei-mq/` 内由 reactor 执行（`mvn clean verify`），在子模块外或缺失该路径处执行会失败。

核心模块 `ArchitectureBoundaryTest`（6 例）：① `thirdPartyOnlyInImplementationPackage` 第三方 / 其它模块 import 只允许出现在实现包（根包 / `internal` / `aifei` 均禁止）；② `frameworkOnlyInBridgePackage` `import cn.aifei.*`（除 `cn.hg.aifei.mq.*`）只允许出现在 `cn.hg.aifei.mq.aifei`；③ `noSqlReference` 禁 `import java.sql.`；④ `aifeiBridgePackageExists` 接入包存在且含 `MqPlugin.java`；⑤ `publicSurfaceExact` 根包公共类型精确 8 个且文件名集合精确相等；⑥ `coreNoThirdParty` 核心模块**零第三方 import**（公共面 + `internal` + `aifei` 全部）。

7 个后端子模块（memory / rocketmq / mqtt / mqtt5 / rabbitmq / kafka / redisstream）的 `XxxBoundaryGateTest` **各 3 例**：第三方客户端 import 只在该模块实现包（`thirdPartyOnlyInImplementationPackage` / `thirdPartyImportsStayInImplementationPackage`）、禁 SQL（`noSqlReference` / `noJdbcReference`）、`META-INF/services` 单行声明且与 `type()` 一致（`servicesDeclarationMatchesType` / `serviceDeclarationIsSingleAndMatchesType`）。

## 契约测试约定

`MessageBusContractTest` 以抽象工厂 `protected abstract MessageBus newBus();` 承载契约，新增后端只需新增一个子类实现 `newBus()` 即可复用全部用例。**契约基类只断言跨后端的「共同底线」**（`docs/design.md` §5.6 A）；**后端专有行为**（投递模型、线程模型、有序性、投递保证与重复、持久化、无订阅者时的「消息丢弃 / 留存」（B6）、`messageId` 格式、可观测性、消费位点 / 消费组、取消订阅的尽力语义，即 §5.6 B / C）**不得写入契约基类**，须放各后端子类的测试。

**5 个实验性后端同样适用**：`mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream` 的子类**只继承运行共同底线**、**不写任何后端差异断言**（差异逐项见 `docs/design.md` §5.6 (B) / (C) 及其「实现期决策」补记）；其运行期语义**均未经真机 broker 验证**，须在具备环境时开启对应开关执行（见下文「真实后端集成测试」）。

**异步后端适配（阶段 2 新增）**：真实后端投递**异步**（§5.6 B1 / B2），故基类提供**轮询等待钩子**（受保护的 `awaitTrue(condition, timeoutMillis)` 一类方法），投递类断言一律「发送后**等待条件成立（带超时）**」而非立即断言；断言使用的收集容器须**线程安全**（如 `CopyOnWriteArrayList`），以免与后端消费线程竞态。同步后端（memory）在同一钩子下**首轮即通过**。

**投递强度（(A) / (B) 边界）**：真实后端为 **at-least-once**（§5.6 B4）、故障 / 重试 / 位点回退下**可能重复消费**，故契约基类**只断言「至少收到且内容正确」，不得断言「恰好一次」**；「恰好一次」是内存后端的特性（B4 差异），其断言落**内存专有**子类 `MemoryMessageBusContractTest`。

**基类用例（§5.6 (A) 共同底线，各后端继承运行）**：

- `shouldDeliverToSubscriber`：生产 → 订阅 → 消费闭环——订阅者**至少收到**该消息且**内容 / 载荷正确**（基类**不**断言「恰好一次」，见下「投递强度」），返回带消息标识的结果。
- `shouldSucceedWithoutSubscribers`：无订阅者时 `send` **仍成功返回 `SendResult`**（含 `messageId`）——即 **§5.6 A5 共同底线**；**不**断言「消息是否留存 / 丢弃」（后者属 **§5.6 B6** 差异，不在本用例断言范围）。
- 关闭语义：`shouldCloseIdempotently`（关闭幂等）、`shouldRejectSendAfterClose`（关闭后发送抛 `IllegalStateException`）、`shouldRejectSubscribeAfterClose`（关闭后订阅抛 `IllegalStateException`）、`shouldRejectUnsubscribeAfterClose`（`close()` 后取消抛 `IllegalStateException`，**共同底线 A9**）。
- `shouldIsolateListenerException`：监听器异常就地隔离，不中断其余订阅者。
- 取消语义：`shouldStopDeliveringAfterUnsubscribe`（取消后该监听器**不再收到**、同主题其他监听器仍收到，**共同底线 A3**）、`shouldUnsubscribeIdempotently`（重复取消 / 取消**从未登记过的**订阅均**幂等静默 no-op**）、`shouldRemoveByInstanceIdentityNotEquals`（按**实例身份**而非 `equals` 移除，锁定「按 `equals` 移除」的实现退化）。

**已移出基类、移入 `MemoryMessageBusContractTest` 的用例（§5.6 B 差异，**非**共同底线）**：

- `shouldPreserveSingleTopicOrder`（单主题 FIFO，对齐 B3）、`shouldInvokeSubscribersInRegistrationOrder`（多订阅者按登记顺序回调，内存专有）、`shouldAssignIncreasingMessageId`（`messageId` 十进制递增格式，对齐 B7）、`shouldDeliverSameMessageInstance`（投递发送时构造的同一消息对象；真实后端按值重建）

> **为何移出**：这 4 项在 §5.6 中明确**不属共同底线**（§5.1 分层提示亦然），真实后端（RocketMQ）下**不成立**（可能乱序 / 异步 / broker 侧 `messageId` / 反序列化重建）。留在基类会使真实后端**必然失败**。此为**对齐 §5.6 的同步修订**（不改动 §5.6 (A) / (B) 定档）：基类只留共同底线，内存专有行为落 `MemoryMessageBusContractTest`。

> **取消语义的落点**：「取消后本进程不再向该 listener 派发该主题消息」「重复取消 / 取消未登记的订阅为幂等 no-op」「`close()` 后取消抛 `IllegalStateException`」属**共同底线**（§5.6 A3 / A9），入契约基类 `MessageBusContractTest`（所有后端继承运行）；而**真实后端在拉取 / 消费组模型下「按 listener 取消」的尽力语义**（取消不保证 broker 侧立即撤销、重试 / 位点回退下仍可能到达，§5.6 B10）属**后端差异**，须放各后端子类测试；内存后端的「取消最后一个监听器后保留空列表」等实现细节亦只放 `MemoryMessageBusContractTest`。`unsubscribe` 的参数非法（`topic` 空 / 空白、`listener` 为 `null`）与 `subscribe` 一致，复用 `MqValidator`，由单元测试 `MqValidatorTest` 覆盖。

## 真实后端集成测试

真实后端端到端用例**默认关闭**（未开启时整类跳过，**不得**把「未连上」静默当作通过），采用 `aifei-cache` 的 `-Dredis.integration=true` 同款范式。

### RocketMQ

- **开关与连接参数**：`-Drocketmq.integration=true`（未开启时整类 `Assume` 跳过）、`-Drocketmq.namesrv-addr=host:port`（默认 `127.0.0.1:9876`）。
- **开启后连不上必须失败**（不得用 `Assume` 掩盖）：`DefaultMQProducer#start()` **不校验 nameserver 连通性**（懒连接），故 `@Before` 创建总线（`newBus()` → `producer.start()`）**通常不会**在此处报错，故障一般在**首个 `send` / `subscribe`** 处以 `MqException` 使用例失败；**仅创建 / 关闭而不做真实收发**的用例（`close()` 幂等、关闭后调用抛 `IllegalStateException`）在 nameserver 不可达时**可能仍通过**——判定后端可用性须看真正收发的用例，**不得**把「建总线未报错」当作「后端可达」。
- **自清理**：每个用例用**随机 topic 前缀**（如 `aifeimq_<uuid>`）与**随机消费组**后缀隔离实例；`@After` 关闭总线。broker 侧被自动创建的 topic **无法经客户端删除**，故命名带随机前缀以便识别与运维清理。
- **命令**：

  ```bash
  mvn -pl aifei-mq-rocketmq test -Dtest=RocketMqMessageBusContractTest -Drocketmq.integration=true -Drocketmq.namesrv-addr=127.0.0.1:9876
  ```

  > 聚合工程下**必须加 `-pl aifei-mq-<module>`**：其它子模块不含该测试类，不加 `-pl` 会因找不到类而失败。

### 5 个实验性后端（`mqtt` / `mqtt5` / `rabbitmq` / `kafka` / `redisstream`）

5 个子类（`MqttMessageBusContractTest` / `Mqtt5MessageBusContractTest` / `RabbitMqMessageBusContractTest` / `KafkaMessageBusContractTest` / `RedisStreamMessageBusContractTest`）与 RocketMQ **同范式**：未开启时整类经 `Assume` 跳过；**一旦开启，连不上必须使测试失败**（不得静默跳过）；每个用例新建总线、以随机主题 / 消费组 / 客户端标识隔离，`@After` 兜底关闭。下列命令中的开关名与参数**须与各子模块 `pom.xml` 的 `systemPropertyVariables` 逐字一致**（各 `<backend>.integration` 开关在 `pom.xml` 中默认 `false`）：

- **命令**：

  ```bash
  mvn -pl aifei-mq-mqtt test -Dtest=MqttMessageBusContractTest -Dmqtt.integration=true -Dmqtt.broker-url=tcp://127.0.0.1:1883
  mvn -pl aifei-mq-mqtt5 test -Dtest=Mqtt5MessageBusContractTest -Dmqtt5.integration=true -Dmqtt5.broker-url=tcp://127.0.0.1:1883
  mvn -pl aifei-mq-rabbitmq test -Dtest=RabbitMqMessageBusContractTest -Drabbitmq.integration=true -Drabbitmq.host=127.0.0.1 -Drabbitmq.port=5672
  mvn -pl aifei-mq-kafka test -Dtest=KafkaMessageBusContractTest -Dkafka.integration=true -Dkafka.bootstrap-servers=127.0.0.1:9092
  mvn -pl aifei-mq-redisstream test -Dtest=RedisStreamMessageBusContractTest -Dredisstream.integration=true -Dredisstream.host=127.0.0.1 -Dredisstream.port=6379
  ```

- **`-pl`、开关名与失败位置**：`-pl aifei-mq-<module>` 不可省（聚合后其它模块无该类），开关与连接参数名**逐字**不变、须与各子模块 `pom.xml` 的 `systemPropertyVariables` 一致。失败位置按连通性模型不同：`mqtt` / `mqtt5` / `rabbitmq` **启动期 fail-fast**（`newBus()` 建总线即建连，不可达即抛 `MqException`）；`kafka` / `redisstream` **懒连接**（连接池懒建连），建总线通常不报错、故障延迟到首个收发处——**不得**把「建总线未报错」当作「后端可达」。

> **验证盲区（重要，P6 首轮环境口径）**：本仓库开发机当时**无 Docker、无任何真实 broker（含 Redis）**（`docker` 命令不存在）；RocketMQ 需 **nameserver + broker 两进程**，其余各需对应中间件。故上述集成测试**首轮无法在本机执行**——**6 个后端的运行期语义（连通性、消费组 / 位点、重投与重复、通配匹配等）当时均未经真机 broker 验证**。**后续进展见文末「P6 补验记录」（2026-11-05）**：`rocketmq` 与 `redisstream` 已真机 PASS，`mqtt` / `mqtt5` / `rabbitmq` / `kafka` 仍 BLOCKED。默认的 `mvn clean verify`（集成关闭）**不含**任何真实后端端到端验证（其 `Skipped: 60` 即这 6 类各 10 条）。

## 新增约定

- 新增或改变**公共行为**时，先在 `docs/design.md` 明确契约，再补测试；已修复的缺陷固化为回归测试。
- 公共行为的契约用例优先加入 `MessageBusContractTest`（所有后端共用），后端专有行为放各自子类。
- 测试须独立、幂等：每个用例自建被测对象并自行关闭；`MqHolder` / `AopKit` 等静态状态在 `@Before` / `@After` 内清理。
- 新增**真实后端**（如 RocketMQ）时，其**集成测试默认关闭**，用系统属性显式开启（对齐 `aifei-cache` 的 `-Dredis.integration=true` 范式，如 `-Drocketmq.integration=true`）；**一旦开启，连不上必须使测试失败**，不得静默跳过。契约基类仍只跑共同底线，真实后端的行为差异放其子类（含已移出基类的内存专有用例）。

## 发布门禁执行记录（P6，2026-11-04）

本节记录 aifei-mq 发布门禁（P6）在**本机**（Windows / `JDK 21.0.11` / `Apache Maven 3.9.16`）的实际执行结果与原始证据。结论：**5 个必做项中 3 项 PASS、2 项 BLOCKED**；2 项 BLOCKED 全部是**环境缺失**，**不得**记为通过、**不得**以其它证据冒充。

执行前置：父工程根目录 `mvn -DskipTests package` → 9 个 reactor 模块全 `SUCCESS`，8 个后端子模块均产出 `target/*-1.0.0.jar`；随后 `mvn -DskipTests install` 把**当前源码**产物装入本地仓库（原因见本节末尾「发现 A」）。

| # | 门禁项 | 判定 | 证据 |
| --- | --- | --- | --- |
| 1 | 集成开关负向验证 | **PASS** | `Skipped: 0` + `RemotingConnectException: connect to [127.0.0.1:9876] failed`（本节 1） |
| 2 | 字节码基线（Java 8 目标） | **PASS** | 8/8 代表类 `major version: 52`（本节 2） |
| 3 | 凭据泄露检查（静态 + 现有测试） | **PASS**（附 1 条已知边界） | 8 模块 96 处抛异常点逐条核对（本节 3） |
| 4 | 「缺后端依赖」报错证据 | **PASS** | `MqProviderRegistryTest` `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`（本节 4） |
| 5 | Java 8 消费方冒烟 | **BLOCKED** | 本机无 JDK/JRE 8（本节 5） |
| — | RocketMQ **真机 broker** 端到端 | **BLOCKED** | `9876` / `10911` 均无监听、`docker` 命令不存在（本节 1 + 6） |

### 1）集成开关负向验证（PASS —— 预期失败，失败即正确）

- **环境事实（已探测）**：`127.0.0.1:9876`（nameserver）与 `127.0.0.1:10911`（broker）**均无监听**；`docker` 命令**不存在**。
- **命令**：`mvn -pl aifei-mq-rocketmq test -Drocketmq.integration=true`
- **结果**：`BUILD FAILURE`（`exit code 1`），契约类**未被跳过**——原始统计行：

  ```
  [ERROR] Tests run: 10, Failures: 0, Errors: 6, Skipped: 0, Time elapsed: 0.876 s <<< FAILURE! -- in cn.hg.aifei.mq.rocketmq.RocketMqMessageBusContractTest
  [ERROR] Tests run: 13, Failures: 0, Errors: 6, Skipped: 0
  [INFO] BUILD FAILURE
  ```

- **首条失败原因**（原始行）：

  ```
  cn.hg.aifei.mq.MqException: rocketmq send failed, topic: aifeimq_92b34d92821c420b975472245f7c5b1c_none
      at cn.hg.aifei.mq.rocketmq.RocketMqMessageBus.publish(RocketMqMessageBus.java:210)
  Caused by: org.apache.rocketmq.remoting.exception.RemotingConnectException: connect to [127.0.0.1:9876] failed
  ```

- 6 条 Error **全部**为 `RemotingConnectException: connect to [127.0.0.1:9876] failed`（发送侧为 `rocketmq send failed`，订阅/消费侧为 `failed to start rocketmq consumer: connect to [127.0.0.1:9876] failed`）。
- **判定依据**：`Skipped: 0` —— 开关开启后 `Assume` **没有**把连通性故障静默成跳过，**连不上必须失败**这一约束成立；该失败是**预期结果**，不影响其余门禁结论。
- **偏差记录（如实）**：契约类 10 条结果为 **6 Error + 4 通过**，**不是**「10 条全 Error」。通过的 4 条是**只建总线 / 关闭、不做真实收发**的用例：`shouldCloseIdempotently`、`shouldRejectSendAfterClose`、`shouldRejectSubscribeAfterClose`、`shouldRejectUnsubscribeAfterClose`。这与上文「真实后端集成测试」中「失败位置按连通性模型不同」的记述**完全一致**（`DefaultMQProducer#start()` 懒连接，故障在首个 `send` / `subscribe` 处暴露；仅创建关闭的用例在 nameserver 不可达时可能仍通过）。故**判定后端可用性**仍须看真正收发的用例。
- **真机端到端**：nameserver / broker 双进程均缺失、`docker` 不可用 → **BLOCKED**，本轮**未**做任何 RocketMQ 真机收发验证。

### 2）字节码基线（PASS —— 8/8 为 Java 8 目标）

命令：`javap -verbose -cp <module>\target\<module>-1.0.0.jar <FQCN> | findstr "major version"`（`javap` 取 `C:\Program Files\Java\jdk-21.0.11\bin\javap.exe`）。

8 个模块代表类逐个执行（core `cn.hg.aifei.mq.Message`、memory `...memory.MemoryMessageBus`、rocketmq `...rocketmq.RocketMqMessageBus`、mqtt `...mqtt.MqttMessageBus`、mqtt5 `...mqtt5.Mqtt5MessageBus`、rabbitmq `...rabbitmq.RabbitMqMessageBus`、kafka `...kafka.KafkaMessageBus`、redisstream `...redisstream.RedisStreamMessageBus`），输出关键行**全部**为 `major version: 52`（= Java 8 字节码目标），**无例外**。

### 3）凭据泄露检查（PASS，附 1 条已知边界）

- **扫描范围**：8 个模块 `src/main/java` 全部 `.java`；口径 `throw new (MqException|IllegalArgumentException)`，命中 **96 处**（core 24 / memory 1 / rocketmq 10 / mqtt 10 / mqtt5 10 / rabbitmq 16 / kafka 10 / redisstream 15）。
- **逐条核对消息构成**：消息文本只含**键名**（如 `rocketmq nameserver address is required: mq.rocketmq.namesrv-addr`、`kafka bootstrap servers are required: mq.kafka.bootstrap-servers`）、**类名**（provider 冲突 / 无类型）、**topic / stream 名**、**数值**；数值型回显**仅**来自非凭据键（`qos` / `keep-alive-seconds` / `connection-timeout-*` / `reconnect-*` / `request-timeout-ms` / `prefetch` / `port` / `database` / `count` / `block-ms` / `start-mode` / `claim-idle-millis` 等）。
- `username` / `password` / `sasl-jaas-config` 的**取值从不进入任何异常消息**：`KEY_PASSWORD` 仅 `config.get()` 后直送客户端（`setUserName` / `setPassword` / `factory.setPassword` / `clientConfigBuilder.password`）；kafka 的 `sasl-jaas-config` 仅在 `resolveJaasConfig(...)` 内原样使用或按机制拼装 JAAS 串并写入客户端属性，**不**参与消息拼接。
- **无日志侧泄露路径**：8 模块主源码中 `Logger` / `printStackTrace` / `System.out|err` 命中 **0**。
- **现有测试断言**：5 个 `XxxConfigValidationTest` **均断言「消息含必填键名」**——kafka `bootstrap-servers`、mqtt / mqtt5 `broker-url`、rabbitmq / redisstream `host`（例：`assertTrue("异常消息应指明缺失的必填键 bootstrap-servers…", failure.getMessage().contains("bootstrap-servers"))`）。对「**不含凭据**」的直接断言见第 4 项的唯一用例（core 侧 `SECRET = "s3cr3t-p@ssw0rd"`）。
- **已知边界（如实上报，未验证）**：`mqtt`（`MqttMessageBus:248`）、`mqtt5`（`:281`）、`rabbitmq`（`:485`）、`rocketmq`（`:140` / `:245`）在「建连 / 启动失败」处把**第三方客户端异常消息**（`e.getMessage()`）拼进自家 `MqException`。我方**不主动**输出凭据，但第三方消息内容**不受本工程控制**；若某客户端在异常中原样回显配置值即可能间接泄露。**kafka 未使用该写法**（`failed to initialize kafka producer` / `failed to start kafka consumer` 只带原因链），`redisstream` 也仅以 `stream` 名 + 原因链报错。因本机无对应 broker，**该间接路径本轮无法触发验证**，记为遗留风险。

### 4）「缺后端依赖」报错证据（PASS）

命令与原始统计行：

```
mvn -pl aifei-mq-core test -Dtest=MqProviderRegistryTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.097 s -- in cn.hg.aifei.mq.internal.MqProviderRegistryTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

3 例分别覆盖：按 `type()` 建索引；同类型多实现报错（列出冲突类名、不静默择一）；**缺 `type` 报错**。关键用例 `shouldFailWithoutLeakingCredentialsWhenTypeMissing`：

- 向配置注入 `mq.nosuch.password=s3cr3t-p@ssw0rd`、`mq.nosuch.username=s3cr3t-p@ssw0rd`；
- 断言消息含 `mq.type=nosuch`、含补齐依赖提示 `aifei-mq-memory`；
- 断言消息 **不含** `s3cr3t-p@ssw0rd`（`assertFalse("消息不得泄露配置取值…")`）。

即「缺后端依赖」时给出**可操作**的报错（指明缺哪个 type、该引哪个 artifact），且**不含凭据取值**。

**未做项（如实）**：**没有**做独立消费方工程冒烟（新建临时 Maven 工程 → `install` 本工程 8 个 jar → 以外部工程验证「漏引后端子模块」的错误信息是否如期出现）。原因是需新建临时工程并安装全部产物，**本轮不扩大范围**；上列 core 单元用例作为等价证据。

### 5）Java 8 消费方冒烟（BLOCKED）

- **阻塞原因（环境缺失）**：本机仅有 `C:\Program Files\Java\jdk-21.0.11`，**无 JDK / JRE 8**（`Program Files\Java` 下仅一个 JDK 目录，`JAVA_HOME` 未设置）。故「以 JRE 8 运行消费方工程」**无法在本机执行**，记为 **BLOCKED**；**不得**用 JDK 21 运行冒充 Java 8 冒烟。
- **可提供的替代证据**：第 2 项 8/8 类 `major version: 52` —— 即产物字节码为 Java 8 目标，属**兼容性静态证据**，不等价于运行时冒烟。具备 JRE 8 环境后须补验。

### 6）本轮未覆盖 / 后续补验清单

- **RocketMQ 真机端到端**（BLOCKED）：需 nameserver + broker 两进程；具备环境后执行 §「真实后端集成测试 / RocketMQ」命令补验收发、消费组、重投与重复。
- **其余 5 个后端真机验证**（BLOCKED）：`1883` / `5672` / `9092` 均无监听，`mqtt` / `mqtt5` / `rabbitmq` / `kafka` 无法验证。
- **Java 8 运行时冒烟**（BLOCKED）：见第 5 项。

### 发现（意外项，如实上报）

**发现 A（影响单模块测试的跑法，重要）**：首次执行第 1 项命令时，契约类报 `java.lang.AbstractMethodError: Receiver class cn.hg.aifei.mq.rocketmq.RocketMqMessageBus does not define or inherit an implementation of the resolved method 'abstract cn.hg.aifei.mq.Producer producer()' of interface cn.hg.aifei.mq.internal.MessageBus`，使 10 条用例变成 **4 Failures + 6 Errors**。**根因不在源码**：本地仓库 `C:\IDEs\apache-maven-repository\io\github\gollyhu\aifei-mq-core\1.0.0\aifei-mq-core-1.0.0.jar`（当日 17:28 安装）是**模块拆分前**的旧 core（`MessageBus` 仍是 `producer() / consumer() / close()`），而 `-pl` **不带 `-am`** 时该模块从**本地仓库**取 core，造成新旧接口错配。执行 `mvn -DskipTests install` 刷新本地仓库后，同一条命令才得到第 1 项的真实结果。**建议**：跑单模块测试前先 `mvn -DskipTests install`，或加 `-am`；否则可能拿到**与源码无关**的假失败（或假通过）。

**发现 B（可用资源，供调度人决定是否纳入后续轮次）**：本机 `127.0.0.1:6379` **有监听**（存在 `C:\IDEs\Redis` 目录），即 **Redis 可用**；`1883` / `5672` / `9092` 无监听。本轮**未**执行 `redisstream` 集成用例——P6 只要求 RocketMQ 负向验证，且该用例会向用户 Redis 写入随机前缀的 stream 与消费组（有副作用）。若需补验，可另开一轮以 `-Dredisstream.integration=true` 执行 §「真实后端集成测试 / 5 个实验性后端」中的对应命令。

**发现 C（命令写法，影响判定）**：在 PowerShell 下 `-Drocketmq.integration=true` **不加引号**会被拆成 `-Drocketmq` 与 `.integration=true`，Maven 报 `Unknown lifecycle phase ".integration=true"`——同样是 BUILD FAILURE，但**测试根本没跑**，极易被误读为「负向验证通过」。**执行任何 `-D` 形式的 Maven 参数时必须整体加引号**：`"-Drocketmq.integration=true"`。

## P6 补验记录（2026-11-05：本机 nameserver / Redis 变化）

- **本地仓库旧产物清理**：已删除 `C:\IDEs\apache-maven-repository\io\github\gollyhu\aifei-mq\1.0.0`（含拆分前 `aifei-mq-1.0.0.jar`），随后 `mvn -DskipTests clean install` 重建。现该坐标下**仅有 `aifei-mq-1.0.0.pom`**（聚合壳，无 jar），8 个子模块均为最新产物——「发现 A」的假失败根因已消除。
- **`redisstream` 真机集成（PASS）**：`mvn -pl aifei-mq-redisstream test "-Dredisstream.integration=true"` → `Tests run: 14 / Failures: 0 / Errors: 0 / Skipped: 0`，`BUILD SUCCESS`。契约类 10 项**真实收发通过**（此前默认跳过），即 Redis Streams 后端的建组、投递、取消订阅等共同底线语义**首次获得真机验证**。用例会向本机 Redis 写入随机前缀的 stream / 消费组，已按用户授权执行。
- **`rocketmq` 真机集成（PASS，2026-11-05）**：nameserver 与 broker 均由本轮启动（RocketMQ `5.5.1`，`9876` / `10911` 监听；启动日志落在 `C:\IDEs\rocketmq-all-5.5.1-bin-release\namesrv-start.log` 与 `broker-start.log`）；`mvn -pl aifei-mq-rocketmq test "-Drocketmq.integration=true"` → **`Tests run: 13 / Failures: 0 / Errors: 0 / Skipped: 0`**，`BUILD SUCCESS`，契约类 10 项**真实收发通过**（耗时 88.56s）。
  - **过程记录（环境阶梯）**：仅 broker 在跑、nameserver 已退出 → `RemotingConnectException: 9876`；仅 nameserver、无 broker → `No route info of this topic`；两者齐全 → **全部通过**。
  - **版本组合**：客户端 `rocketmq-client:4.9.7` 对 broker `5.5.1` 兼容，本次 10 项契约用例无协议异常。
