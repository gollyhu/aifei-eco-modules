# aifei-mq

为 aifei 应用补齐框架未覆盖的异步消息能力（解耦、削峰）。业务代码只依赖公共接口，换 MQ 中间件只改配置、不改代码。

- 坐标：`io.github.gollyhu:aifei-mq:1.0.0`
- 包名：`cn.hg.aifei.mq`
- 技术基线：Java 8
- 后端：默认**内存/本地实现**（零第三方运行依赖）；内置**6 个真实后端**——`rocketmq` / `mqtt`（MQTT v3.1.1）/ `mqtt5`（MQTT v5.0）/ `rabbitmq` / `kafka` / `redisstream`（客户端均由消费方自带）；以 provider 扩展点追加更多后端

## 安装

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-mq</artifactId>
    <version>1.0.0</version>
</dependency>
```

框架内核 `cn.aifei:aifei-all` 为 `provided`，由消费方应用自带。

## 支持的后端

换后端**只改配置**：把 `mq.type` 指向目标后端（主实例）或 `mq.<name>.type`（命名实例）即可，业务代码仍只依赖 `Producer` / `Consumer`（`@Inject` 或 `ProducerUtil` / `ConsumerUtil`）。

| `mq.type` 取值 | 后端 | 必填键 | 启动期连通性 | 客户端依赖（需自行引入，`optional` 不传递） |
| --- | --- | --- | --- | --- |
| `memory`（默认） | 内存 / 本地 | 无 | 不涉及（无外部连接） | 无 |
| `rocketmq` | RocketMQ | `namesrv-addr` | 懒连接（**启动成功 ≠ 可达**） | `org.apache.rocketmq:rocketmq-client:4.9.7` |
| `mqtt` | MQTT v3.1.1 | `broker-url` | **建连即校验（fail-fast）** | `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` |
| `mqtt5` | MQTT v5.0 | `broker-url` | **建连即校验（fail-fast）** | `org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5` |
| `rabbitmq` | RabbitMQ | `host` | **建连即校验（fail-fast）** | `com.rabbitmq:amqp-client:5.21.0` |
| `kafka` | Apache Kafka | `bootstrap-servers` | 懒连接（**启动成功 ≠ 可达**） | `org.apache.kafka:kafka-clients:3.9.1`（**必须停 3.x**，4.x 要求 Java 11+） |
| `redisstream` | Redis Streams | `host` | 懒连接（**启动成功 ≠ 可达**） | `redis.clients:jedis:8.0.1` |

> **启动期连通性**：`mqtt` / `mqtt5` / `rabbitmq` 在**总线构造（= `MqPlugin.start()` 链路）内即建连**，broker 不可达则**应用启动失败**；`rocketmq` / `kafka` / `redisstream` 为**懒连接**，broker 不可达时应用**仍能启动**，故障延迟到**首次收发**才以 `MqException` 暴露。

## 快速开始

在应用配置类里装配插件：

```java
public class AppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Plugins plugins) {
        plugins.add(new MqPlugin(PropKit.use("app-config.txt")));
    }
}
```

之后有两种取用方式：

```java
// 方式一：注入
@Service
public class OrderService {

    @Inject Producer producer;
    @Inject Consumer consumer;

    public void placeOrder(Order order) {
        producer.send("order-created", order.getId());
    }
}

// 方式二：静态工具类（不注入也能用）
ProducerUtil.send("order-created", "A1001");
ConsumerUtil.subscribe("order-created", message -> handle(message));

// 静态短写一律作用于主实例；需要取实例时：
Producer producer = ProducerUtil.use();          // 主实例（与 @Inject 同一个对象）
Consumer consumer = ConsumerUtil.use("orders");  // 命名实例（mq.names 声明）
```

> 两种方式取到的是**主实例的同一个对象**。`ProducerUtil` / `ConsumerUtil` 与注入的 `Producer` / `Consumer` 都只在插件启动后可用（未启动 / 已停止时调用会抛 `IllegalStateException`）。`@Inject` 只对应**主实例**；**命名实例**只能经 `use(name)` 取。

## 接口

公共面固定为 8 个类型：`Message` / `Producer` / `Consumer` / `MessageListener` / `SendResult` / `MqException` / `ProducerUtil` / `ConsumerUtil`。

- `Producer` / `Consumer`：业务接口，经 `@Inject` 取主实例。
- `ProducerUtil` / `ConsumerUtil`：静态工具类。`use()` 取主实例（与 `@Inject` 同一个对象）；`use(name)` 取 `mq.names` 声明的**命名实例**；静态短写（`ProducerUtil.send(...)` / `ConsumerUtil.subscribe(...)` / `ConsumerUtil.unsubscribe(...)`）**一律作用于主实例**。未启动 / 已停止时调用抛 `IllegalStateException`；`use(name)` 的实例名为空 / 非法 / 未注册抛 `IllegalArgumentException`。

### Producer

```java
SendResult send(Message message);
SendResult send(String topic, byte[] payload);
SendResult send(String topic, String text);   // 文本以 UTF-8 编码为字节
```

### Consumer

```java
void subscribe(String topic, MessageListener listener);
void unsubscribe(String topic, MessageListener listener);   // 取消：从该主题按实例身份移除该监听器
```

`unsubscribe` 从该主题的登记列表中移除**该监听器实例**（**按实例身份**判定，不用 `equals`——`MessageListener` 是函数式接口，lambda / 方法引用的 `equals` 无可靠语义）。**重复取消**与**取消一个从未登记过的订阅**表现相同：**幂等静默 no-op，不抛**。`close()` / 停止之后调用抛 `IllegalStateException`；`topic` 空 / 空白或 `listener` 为 `null` 抛 `IllegalArgumentException`。

> 易误用点：**不能**用 `unsubscribe` 来「取消一个不存在的订阅」并期待报错——本模块把它当**幂等 no-op** 处理；如需判断某订阅是否仍存在，请在业务侧自行记账。

`MessageListener` 是函数式接口：

```java
@FunctionalInterface
public interface MessageListener {
    void onMessage(Message message);
}
```

### 接口通用规则

- **同步投递**：`send` 在调用线程内依次同步回调订阅者，返回前回调已执行完毕。本模块不引入线程池、队列或背压。
- **单主题保序**：同一主题内 `send` 的完成顺序即投递顺序；不保证跨主题顺序。单线程连续 `send` 严格保序，多线程并发 `send` 的相对顺序不作保证。
- **多订阅者顺序**：同一主题的多个订阅者按 `subscribe` 登记顺序依次回调。
- **无订阅者**：`send` 仍成功返回 `SendResult`（`messageId` 已分配）——属**跨后端共同底线**（`docs/design.md` §5.6 A5）；消息**被丢弃还是留存**随后端而变（内存后端丢弃，broker 独立存储待消费者上线，§5.6 B6）。
- **可重入**：允许在 `onMessage` 内再次 `send`，调用方自行避免无限递归。
- **监听器异常隔离**：监听器抛出的运行时异常就地丢弃，不中断同主题其他订阅者、不上抛给生产者。
- **取消订阅**：`unsubscribe` 按**实例身份**移除该监听器；**重复取消**、**取消从未登记过的订阅**均为**幂等静默 no-op**；停止后调用抛 `IllegalStateException`（详见上文 `Consumer` 一节）。
- **不承诺延迟投递**：本模块**不提供**延迟消息——`send` 一律**立即**投递。**需要延迟请在业务侧自行调度**（如用 `ScheduledExecutorService` 定时后再 `send`），模块零改动。**理由**：`delayMillis` / `delayLevel` 这类延迟参数只能被**部分后端**有意义地实现（内存后端不允许引入调度线程），按设计 **§5.7 准入判据**不得进入公共 API（决策与理由链见 `docs/design.md` **§13 第 26 条**）。

> **跨后端差异说明**：上列各项描述的是当前**内存后端**的行为。本模块后续可用 provider 扩展真实后端（如 RocketMQ）。**跨后端一致**的是核心契约：`Message` 不可变与双向防御性拷贝、`Producer` 的 3 个 `send` 重载（文本按 UTF-8）、`Consumer.subscribe` 的登记语义与 `Consumer.unsubscribe` 的取消语义（**取消后本进程不再向该 listener 派发该主题消息**）、`MessageListener` 异常隔离、`SendResult` 无 success 布尔（失败抛异常）、无订阅者时 `send` 仍成功返回 `SendResult`（§5.6 A5）、异常分类（参数非法 `IllegalArgumentException` / 状态非法 `IllegalStateException` / 运行时故障 `MqException`）、`close()` / `stop()` 幂等与停止后调用抛 `IllegalStateException`。**随后端而变**的是：投递模型（内存同步投递 vs 真实后端异步 + 拉取，`send` 返回不等于已消费）、线程模型、有序性、投递保证（内存精确一次 vs 真实后端 at-least-once、可能重复消费）、持久化、无订阅者时消息是否丢弃 / 留存（§5.6 B6）、`messageId` 生成、可观测性、真实后端特有的消费位点 / 消费组，以及**取消订阅的尽力语义**（真实后端在拉取 / 消费组模型下「按 listener 取消」不保证 broker 侧立即撤销）。完整清单见 `docs/design.md` §5.6。

### 异常分类

| 场景 | 异常 |
| --- | --- |
| 参数非法（topic 空、payload / text / listener / message / prop / messageId 为 null、`mq.type` 未注册；`use(name)` 的实例名为空 / 非法 / 未注册） | `IllegalArgumentException` |
| 状态非法（停止后 `send` / `subscribe` / `unsubscribe`；未启动 / 已停止时 `ProducerUtil` / `ConsumerUtil` 的 `use()` / `use(name)` / 静态短写） | `IllegalStateException` |
| 运行时故障（后端运行时错误，如真实后端连接失败） | `MqException` |

## 配置

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.type` | 否 | `memory` | 后端类型；指向未注册后端时启动立即失败。**用 RocketMQ 必须显式 `mq.type = rocketmq`** |
| `mq.<type>.*` | 后端自定 | — | 后端专有配置；**内存后端无额外键**（不引入 `mq.memory.*`） |
| `mq.rocketmq.namesrv-addr` | **是**（用 RocketMQ 时） | — | RocketMQ nameserver 地址，多个用 `;` 分隔 |
| `mq.rocketmq.producer-group` | 否 | `DEFAULT_PRODUCER` | RocketMQ 生产者组 |
| `mq.rocketmq.consumer-group` | 否 | `DEFAULT_CONSUMER` | RocketMQ 消费者组 |
| `mq.names` | 否 | — | 声明**命名实例**（逗号分隔）；主实例恒存在、无需声明。空项 / 重名 / 保留名（`type` / `names`）/ `__MAIN__` 即启动失败 |
| `mq.<name>.*` | 否 | — | **命名实例** `name` 的配置：类型键 `mq.<name>.type`、后端专有键 `mq.<name>.<type>.*`（如 `mq.orders.rocketmq.namesrv-addr`） |

`app-config.txt`：

```properties
# 使用默认内存后端时无需配置 mq.type
mq.type = memory
```

**命名实例（可选）**：需在同一进程内用多个后端实例（如各自一个消费组）时，用 `mq.names` 声明，经 `use(name)` 取；主实例走根级 `mq.*`、命名实例走 `mq.<name>.*`（对齐 `aifei-cache` 的 `cache.names`）：

```properties
mq.type = rocketmq
mq.rocketmq.namesrv-addr = 127.0.0.1:9876

mq.names = orders,payments
mq.orders.type = rocketmq
mq.orders.rocketmq.namesrv-addr = 127.0.0.1:9876
mq.orders.rocketmq.consumer-group = ORDER_CONSUMER
mq.payments.type = rocketmq
mq.payments.rocketmq.namesrv-addr = 127.0.0.1:9876
mq.payments.rocketmq.consumer-group = PAY_CONSUMER
```

## 换后端 / 使用真实后端

换后端**只改配置、不改代码**：把 `mq.type` 指向目标后端即可（业务仍只依赖 `Producer` / `Consumer` 与 `ProducerUtil` / `ConsumerUtil`）。

### 使用 RocketMQ（内置真实后端）

`app-config.txt`：

```properties
mq.type = rocketmq                        # 必须显式配置（缺省仍是 memory）
mq.rocketmq.namesrv-addr = 127.0.0.1:9876 # 必填：nameserver 地址，多个用 ';' 分隔
mq.rocketmq.producer-group = ORDER_PRODUCER   # 可选，默认 DEFAULT_PRODUCER
mq.rocketmq.consumer-group = ORDER_CONSUMER   # 可选，默认 DEFAULT_CONSUMER
```

**依赖由消费方自带**：RocketMQ 客户端（`org.apache.rocketmq:rocketmq-client:4.9.7`）在 `aifei-mq` 内以
`<optional>true</optional>` 声明，**不会**传递到你的应用。使用 RocketMQ 后端时请在应用中自行声明：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.9.7</version>
</dependency>
```

**语义差异（内存后端 vs RocketMQ）**：`send` 变为**异步投递**——返回成功仅表示**已提交到 broker**，
**不代表已被消费**；消费回调发生在 **RocketMQ 自有线程**（非生产者线程）；投递保证为
**at-least-once**（故障 / 重试下**可能重复消费**，监听器请自行幂等）；消息**持久化**在 broker；
`messageId` 为 broker 的全局唯一标识；`unsubscribe` 为**尽力语义**（仅解除本进程绑定，不撤销 broker 侧订阅）。
完整清单见 `docs/design.md` §5.6 (C)。

> **⚠️ 运维提示（重要）**：`mq.type = rocketmq` 时，**「应用启动成功」不等于「nameserver 可达」**。RocketMQ 客户端采用**懒连接**（`producer.start()` **不校验** nameserver 连通性），nameserver / broker 不可达时应用**仍能正常启动**，故障会**延迟到首次发消息（或首次订阅拉取）**才以 `MqException` 抛出。请在启动后以**实际收发探活**确认后端可用，**不要**把启动结果当作后端可达条件。（注意区分：`mq.type` 指向未注册后端、`mq.rocketmq.namesrv-addr` 缺失等**配置错误**仍在启动时立即失败。）

**集成测试**：RocketMQ 端到端用例**默认关闭**，需具备 nameserver + broker 环境后显式开启（见 `docs/testing.md`）：

```bash
mvn test -Dtest=RocketMqMessageBusContractTest -Drocketmq.integration=true -Drocketmq.namesrv-addr=127.0.0.1:9876
```

### 多实例部署与分工消费

**现状即可用**：同一 `mq.rocketmq.consumer-group` 部署**多个应用实例**，**broker 侧自动竞争消费**——同组内的实例**分摊**消息（每个实例消费**部分**消息）。无需调用方做任何协调，也**不必**改公共 API：消费组由**配置**表达（`docs/design.md` §13 第 23 条）。

> **⚠️ 必要约束**：**同一消费组的多个实例必须有相同的订阅集合**。若组内实例订阅的 topic 不同，broker 的 **rebalance 会异常**（依据 `docs/design.md` §5.6 (C) B9 与 §13 第 14 条）。需要**不同订阅集合**时，请为它们配置**不同的 `mq.rocketmq.consumer-group`**。

> **反向需求**：若要让**每个实例都收到全部消息**（同主题全量各收 / 广播效果），**不要**让它们共用同一消费组——配置**不同的 `consumer-group`** 即可各收一份。「**单进程内多个消费组**」已**定档**为**命名实例**方案（学 `aifei-cache`，见 `docs/design.md` §13 第 24 条 / §5.5 / §3.7）：用 `mq.names` 声明、经 `use(name)` 取，**已随 P2 落地**（配置域见 `docs/design.md` §5.5）。

### 使用 MQTT（v3.1.1）

**依赖由消费方自带**：MQTT v3 客户端（`org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`）在本模块内以 `<optional>true</optional>` 声明，**不会**传递到你的应用。使用时请在应用中自行声明：

```xml
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>1.2.5</version>
</dependency>
```

`app-config.txt`：

```properties
mq.type = mqtt
mq.mqtt.broker-url = tcp://127.0.0.1:1883   # 必填
# mq.mqtt.client-id = app-orders            # 可选；未配置由客户端生成
# mq.mqtt.qos = 1                           # 可选，默认 0
```

**配置键**（主实例前缀 `mq.mqtt.`；命名实例为 `mq.<name>.mqtt.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.mqtt.broker-url` | **是** | — | broker 地址（客户端口径的 server URI，形如 `tcp://host:1883` / `ssl://host:8883`）；缺失即 `MqException`（配置错误：启动期失败、不留半装配） |
| `mq.mqtt.client-id` | 否 | 由客户端生成 | 客户端标识；同一 broker 下需唯一（同 `client-id` 的连接会互相顶掉） |
| `mq.mqtt.clean-session` | 否 | `true` | v3 的 clean session 标志（`false` 时依赖 broker 保留会话与离线消息） |
| `mq.mqtt.qos` | 否 | `0` | 订阅与发布的默认 QoS（`0` / `1` / `2`）；属配置域，不进入 `send` 签名 |
| `mq.mqtt.retain` | 否 | `false` | 发布是否设置 retain 标志（属配置域） |
| `mq.mqtt.username` | 否 | — | 认证用户名（只进配置域） |
| `mq.mqtt.password` | 否 | — | 认证口令（只进配置域） |
| `mq.mqtt.keep-alive-seconds` | 否 | `60` | 心跳间隔（秒） |
| `mq.mqtt.connection-timeout-seconds` | 否 | `30` | 连接超时（秒） |
| `mq.mqtt.auto-reconnect` | 否 | `true` | 是否启用客户端原生自动重连 |
| `mq.mqtt.reconnect-delay-millis` | 否 | `1000` | 重连退避起始间隔（毫秒）；**Paho v3 无对应 API，此键无直接落点**（仅校验取值） |
| `mq.mqtt.reconnect-delay-max-millis` | 否 | `30000` | 重连退避上限（毫秒） |

**语义差异提示**：

- **投递模型**：**异步**——`send` 只表示「已交给客户端发布」，返回 `SendResult` **不等于已被消费**。
- **`topic` 语义**：`subscribe` 的 `topic` 是 MQTT **过滤器**，支持 `+`（单层）/ `#`（多层）通配；`send` 的 `topic` 为精确主题名。
- **`messageId`**：broker 不提供消息标识，由**适配器本地生成**，形如 `mqtt:<client-id>:<序号>`。
- **启动期连通性**：**fail-fast**——构造内建连，broker 不可达则应用启动失败。
- **自动重连**：客户端**原生自动重连**（`close()` 后不再重连）。
- **凭据**：`username` / `password` **只从配置读取**，不进公共 API；异常消息只出现键名。

### 使用 MQTT（v5.0）

**依赖由消费方自带**：MQTT v5 客户端（`org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5`）在本模块内以 `<optional>true</optional>` 声明，**不会**传递到你的应用。使用时请在应用中自行声明：

```xml
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
    <version>1.2.5</version>
</dependency>
```

`app-config.txt`：

```properties
mq.type = mqtt5
mq.mqtt5.broker-url = tcp://127.0.0.1:1883   # 必填
# mq.mqtt5.client-id = app-orders            # 可选；未配置由适配器生成
# mq.mqtt5.clean-start = false               # 可选；配合会话过期保留会话
```

**配置键**（主实例前缀 `mq.mqtt5.`；命名实例为 `mq.<name>.mqtt5.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.mqtt5.broker-url` | **是** | — | broker 地址（v5 客户端口径的 server URI）；缺失即 `MqException`（配置错误：启动期失败） |
| `mq.mqtt5.client-id` | 否 | 由适配器生成 | v5 客户端无「生成 client-id」的 API，未配置时由适配器生成非空标识 `aifei-mq-<纳秒时间戳>`（不写回配置） |
| `mq.mqtt5.clean-start` | 否 | `true` | **v5 的 clean start 标志**（替代 v3 的 `clean-session`；v5 无 `clean-session` 键） |
| `mq.mqtt5.session-expiry-interval-seconds` | 否 | `0` | **v5 会话过期时间**（秒）：配合 `clean-start = false` 表达「断线后由 broker 保留会话」 |
| `mq.mqtt5.qos` | 否 | `0` | 订阅与发布的默认 QoS（属配置域） |
| `mq.mqtt5.retain` | 否 | `false` | 发布是否设置 retain 标志（属配置域） |
| `mq.mqtt5.username` | 否 | — | 认证用户名（只进配置域） |
| `mq.mqtt5.password` | 否 | — | 认证口令（只进配置域） |
| `mq.mqtt5.keep-alive-seconds` | 否 | `60` | 心跳间隔（秒） |
| `mq.mqtt5.connection-timeout-seconds` | 否 | `30` | 连接超时（秒） |
| `mq.mqtt5.auto-reconnect` | 否 | `true` | 是否启用客户端原生自动重连 |
| `mq.mqtt5.reconnect-delay-millis` | 否 | `1000` | 重连退避起始间隔（毫秒）；v5 客户端有对应 API，**有直接落点**（与 `mqtt` 不同） |
| `mq.mqtt5.reconnect-delay-max-millis` | 否 | `30000` | 重连退避上限（毫秒）；v5 客户端**有直接落点** |

**语义差异提示**：与 `mqtt`（v3.1.1）**同构**——异步投递、`topic` 为**过滤器**（另可表达 `$share/<group>/<topic>` 共享订阅）、`messageId` 由适配器本地生成（形如 `mqtt5:<client-id>:<序号>`）、**启动期 fail-fast**、客户端原生自动重连、凭据只进配置域。**两后端配置命名空间互不相通**（`mq.mqtt.*` 与 `mq.mqtt5.*`），且**无** `mq.mqtt.version` 键。

### 使用 RabbitMQ

**依赖由消费方自带**：RabbitMQ 客户端（`com.rabbitmq:amqp-client:5.21.0`）在本模块内以 `<optional>true</optional>` 声明，**不会**传递到你的应用。使用时请在应用中自行声明：

```xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>5.21.0</version>
</dependency>
```

`app-config.txt`：

```properties
mq.type = rabbitmq
mq.rabbitmq.host = 127.0.0.1        # 必填
# mq.rabbitmq.port = 5672
# mq.rabbitmq.virtual-host = /
```

**配置键**（主实例前缀 `mq.rabbitmq.`；命名实例为 `mq.<name>.rabbitmq.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.rabbitmq.host` | **是** | — | broker 主机名 / IP；缺失即 `MqException`（配置错误，启动期失败） |
| `mq.rabbitmq.port` | 否 | `5672` | broker 端口 |
| `mq.rabbitmq.virtual-host` | 否 | `/` | vhost |
| `mq.rabbitmq.username` / `mq.rabbitmq.password` | 否 | — | 认证凭据（只进配置域） |
| `mq.rabbitmq.exchange` | 否 | 空（**默认交换机**） | **高级键**：为空走默认交换机——此时 `topic` **即队列名**、直接路由 |
| `mq.rabbitmq.exchange-type` | 否 | `topic` | **高级键**：仅在配置了 `exchange` 时生效（`direct` / `topic` / `fanout` 等）；`topic` 类型下**通配匹配由 broker 侧的绑定承担**，本模块的 `topic` 仍是**精确 routing key** |
| `mq.rabbitmq.durable` | 否 | `true` | 订阅侧声明队列时是否持久化 |
| `mq.rabbitmq.prefetch` | 否 | `0` | 消费预取上限（`0` = 不限，按客户端语义） |
| `mq.rabbitmq.publisher-confirms` | 否 | `false` | 发布确认：**关闭**时 broker 不回标识 → `messageId` 由适配器本地生成；**开启**时可用确认序号 |
| `mq.rabbitmq.connection-timeout-millis` | 否 | `60000` | 建连超时（毫秒） |
| `mq.rabbitmq.auto-reconnect` | 否 | `true` | 是否启用客户端原生自动恢复（连接恢复 + 拓扑恢复） |
| `mq.rabbitmq.network-recovery-interval-millis` | 否 | `5000` | 网络恢复重试间隔（毫秒） |
| `mq.rabbitmq.topology-recovery` | 否 | `true` | 恢复后是否自动重建队列 / 绑定等拓扑 |

**语义差异提示**：

- **投递模型**：**异步**——`basicPublish` 提交到 broker（未开 `publisher-confirms` 时不等待 broker 回应）；返回 `SendResult` **不等于已被消费**。
- **`topic` 语义**：**精确**——`topic` = **队列名**（routing key 精确匹配，走默认交换机）；配置 `exchange` + `exchange-type=topic` 时，**通配匹配由 broker 侧「绑定」承担**。
- **`messageId`**：未开 `publisher-confirms` 时由**适配器本地生成**（形如 `rabbitmq:<host:port>:<序号>`）；开启确认时取确认序号。
- **启动期连通性**：**fail-fast**——构造内建连（`Connection` + 发布 `Channel`），broker 不可达则应用启动失败。
- **投递保证**：**at-least-once**（消费侧手动确认、派发后确认；未确认即断开时 broker 重投，**可能重复**，监听器请自行幂等）。
- **自动重连**：客户端**原生自动恢复**（连接恢复 + 拓扑恢复；`close()` 后不再重连）。
- **凭据**：`username` / `password` / `virtual-host` **只从配置读取**，不进公共 API；异常消息只出现键名。

### 使用 Kafka

**依赖由消费方自带**：Kafka 客户端（`org.apache.kafka:kafka-clients:3.9.1`）在本模块内以 `<optional>true</optional>` 声明，**不会**传递到你的应用。使用时请在应用中自行声明（**必须停 3.x**，4.x 要求 Java 11+）：

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.9.1</version>
</dependency>
```

`app-config.txt`：

```properties
mq.type = kafka
mq.kafka.bootstrap-servers = 127.0.0.1:9092   # 必填
# mq.kafka.group-id = aifei-mq                # 可选，消费组
```

**配置键**（主实例前缀 `mq.kafka.`；命名实例为 `mq.<name>.kafka.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.kafka.bootstrap-servers` | **是** | — | broker 地址列表（`host:port`，多个以 `,` 分隔）；缺失即 `MqException` |
| `mq.kafka.group-id` | 否 | `aifei-mq` | **消费组**（同组多实例**竞争消费**） |
| `mq.kafka.auto-offset-reset` | 否 | `earliest` | 无位点 / 位点越界时的起始策略（`earliest` / `latest` / `none`） |
| `mq.kafka.acks` | 否 | `1` | 生产者应答级别（`0` / `1` / `all`） |
| `mq.kafka.client-id` | 否 | 实现生成 | 客户端标识（未配置时生成 `kafka-<UUID>`） |
| `mq.kafka.security-protocol` | 否 | 客户端默认（明文） | 安全协议（如 `PLAINTEXT` / `SASL_PLAINTEXT` / `SASL_SSL`） |
| `mq.kafka.sasl-mechanism` | 否 | — | SASL 机制 |
| `mq.kafka.username` / `mq.kafka.password` | 否 | — | 凭据：SASL 用户名 / 口令（未直接给 `sasl-jaas-config` 时，仅当 `sasl-mechanism` 为 `PLAIN` / `SCRAM-SHA-256` / `SCRAM-SHA-512` 才由适配器按机制拼装 JAAS；其它机制须显式提供 `sasl-jaas-config`，否则本键不生效；只进配置域） |
| `mq.kafka.sasl-jaas-config` | 否 | — | 完整 JAAS 配置串（凭据整体落此；只进配置域） |
| `mq.kafka.request-timeout-ms` | 否 | `30000` | 请求超时（毫秒） |
| `mq.kafka.reconnect-backoff-ms` | 否 | `50` | 连接层重连退避（毫秒） |
| `mq.kafka.retry-backoff-ms` | 否 | `100` | 可重试请求的重试退避（毫秒） |
| `mq.kafka.poll-timeout-ms` | 否 | `1000` | 消费拉取循环单次阻塞上限（毫秒，实现参数） |

**语义差异提示**：

- **投递模型**：**异步生产 + 拉取消费**——`send` 返回「broker 已接收」（受 `acks` 影响），**不等于已被消费**。
- **`topic` 语义**：**精确**——`topic` = Kafka topic 名；组名走 `mq.kafka.group-id`。
- **`messageId`**：**broker 生成**——取发送返回的「分区 + 位点」拼装（形如 `<topic>-<partition>@<offset>`）。
- **启动期连通性**：**懒连接**——生产者构造不探测 broker，**「启动成功」≠「broker 可达」**，故障延迟到首次 `send` / 拉取才以 `MqException` 暴露。
- **连接恢复**：客户端**无独立自动重连开关**，连接恢复由**客户端内部退避**承担（经 `reconnect-backoff-ms` / `retry-backoff-ms` 调节）；`close()` 后不再重连。
- **凭据**：`security-protocol` / `sasl-mechanism` / `username` / `password` / `sasl-jaas-config` **只从配置读取**，不进公共 API；异常消息只出现键名。

### 使用 Redis Streams

**依赖由消费方自带**：Redis 客户端（`redis.clients:jedis:8.0.1`，与 `aifei-cache` 版本对齐、**Jedis 直连、不跨模块依赖 `aifei-cache`**）在本模块内以 `<optional>true</optional>` 声明，**不会**传递到你的应用。使用时请在应用中自行声明：

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>8.0.1</version>
</dependency>
```

`app-config.txt`：

```properties
mq.type = redisstream
mq.redisstream.host = 127.0.0.1     # 必填
# mq.redisstream.port = 6379
# mq.redisstream.group = aifei-mq
```

**配置键**（主实例前缀 `mq.redisstream.`；命名实例为 `mq.<name>.redisstream.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.redisstream.host` | **是** | — | Redis 主机；缺失即 `MqException`（配置错误，启动期失败） |
| `mq.redisstream.port` | 否 | `6379` | 端口 |
| `mq.redisstream.database` | 否 | `0` | 库号 |
| `mq.redisstream.username` / `mq.redisstream.password` | 否 | — | 认证凭据（Redis 6+ ACL；只进配置域） |
| `mq.redisstream.group` | 否 | `aifei-mq` | **消费组名**（流不存在时由实现创建该组） |
| `mq.redisstream.consumer-name` | 否 | 实现生成 | **消费者名**（同一组内标识本进程消费者；未配置时实现生成非空标识） |
| `mq.redisstream.start-mode` | 否 | `new` | **读取起始策略**：`new` = 只读**新消息**；`pending` = 先重读**本消费者**的 pending（未确认）消息，再转新消息 |
| `mq.redisstream.claim-enabled` | 否 | `true` | 是否回收本组**空闲超时**的 pending 消息（交由其它消费者处理） |
| `mq.redisstream.claim-idle-millis` | 否 | `60000` | pending 空闲超过该值即视为可回收（仅在 `claim-enabled=true` 时生效） |
| `mq.redisstream.count` | 否 | `100` | 单次读取条数上限 |
| `mq.redisstream.block-ms` | 否 | `2000` | 单次阻塞读取上限（毫秒） |
| `mq.redisstream.maxlen` | 否 | 空（**不裁剪**） | 流长度上限（近似裁剪）；为空表示不设置 |
| `mq.redisstream.timeout-millis` | 否 | `2000` | 连接 / 读写超时（毫秒） |
| `mq.redisstream.auto-reconnect` | 否 | `true` | 是否启用连接池原生自愈（借出时校验失效连接并重建） |

**语义差异提示**：

- **投递模型**：**异步**——追加返回条目 ID，表示「已写入流」，**不等于已被消费**。
- **`topic` 语义**：**精确**——`topic` = **流键（stream key）**；组名走 `mq.redisstream.group`、消费者名走 `mq.redisstream.consumer-name`。
- **`messageId`**：**broker 生成**——取追加返回的流条目 ID（形如 `毫秒-序号`）。
- **消息字段形态**：流条目固定以**单一字段 `payload`** 承载载荷原始字节（不引入序列化层）。
- **启动期连通性**：**懒连接**（连接池懒建连）——**「启动成功」≠「Redis 可达」**，故障延迟到首次收发才以 `MqException` 暴露。
- **投递保证**：**at-least-once**——未确认（pending）消息可重读 / 被回收，**可能重复**；`start-mode=pending` 与 `claim-enabled` 会**扩大**重复面，监听器请自行幂等。
- **自动重连**：连接池**原生自愈**（借出时校验失效连接并重建；`close()` 后不再重连）。
- **凭据**：`username` / `password` / `database` **只从配置读取**，不进公共 API；异常消息只出现键名。

### 命名实例（多后端混合）

同一进程内可用多个后端实例（各自一份配置，如各自一个消费组 / 消费者名）——用 `mq.names` 声明，经 `use(name)` 取；主实例走根级 `mq.*`、命名实例走 `mq.<name>.*`：

```properties
mq.type = memory

mq.names = orders,events
# 命名实例 orders 用 RocketMQ
mq.orders.type = rocketmq
mq.orders.rocketmq.namesrv-addr = 127.0.0.1:9876
mq.orders.rocketmq.consumer-group = ORDER_CONSUMER
# 命名实例 events 用 Kafka
mq.events.type = kafka
mq.events.kafka.bootstrap-servers = 127.0.0.1:9092
mq.events.kafka.group-id = EVENTS_GROUP
```

```java
Producer rocket = ProducerUtil.use("orders");
Consumer kafka = ConsumerUtil.use("events");
kafka.subscribe("order-events", message -> handle(message));
```

> 命名实例的后端专有键前缀**恒为** `mq.<name>.<type>.`（如 `mq.orders.rocketmq.*`、`mq.events.kafka.*`、`mq.order.mqtt.client-id`）；`@Inject` 只对应**主实例**。空项 / 重名 / 保留名（`type` / `names`）/ `__MAIN__` 即启动失败。

### 集成测试

5 个新后端的端到端用例**默认关闭**，需具备对应中间件后显式开启（开关名与 `pom.xml` 一致）：

```bash
mvn test -Dtest=MqttMessageBusContractTest -Dmqtt.integration=true -Dmqtt.broker-url=tcp://127.0.0.1:1883
mvn test -Dtest=Mqtt5MessageBusContractTest -Dmqtt5.integration=true -Dmqtt5.broker-url=tcp://127.0.0.1:1883
mvn test -Dtest=RabbitMqMessageBusContractTest -Drabbitmq.integration=true -Drabbitmq.host=127.0.0.1 -Drabbitmq.port=5672
mvn test -Dtest=KafkaMessageBusContractTest -Dkafka.integration=true -Dkafka.bootstrap-servers=127.0.0.1:9092
mvn test -Dtest=RedisStreamMessageBusContractTest -Dredisstream.integration=true -Dredisstream.host=127.0.0.1 -Dredisstream.port=6379
```

> **纪律**：**默认关闭**（未开启时整类跳过）；**一旦开启，连不上必须使测试失败**，不得静默跳过。

### 运维提示：连通性与凭据

- **启动期即校验连通性**：`mqtt` / `mqtt5` / `rabbitmq` 在应用启动（`MqPlugin.start()`）时即建连，**broker 不可达则应用启动失败**（fail-fast）；请在启动前确认 broker 已就绪。
- **懒连接**：`kafka` / `redisstream`（及 `rocketmq`）**启动成功 ≠ 后端可达**——故障延迟到**首次收发**才以 `MqException` 抛出；请在启动后以**实际收发探活**确认后端可用，**不要**把启动结果当作后端可达条件。
- **凭据只从配置读**：`username` / `password`（及 Kafka 的安全 / SASL 键、RabbitMQ 的 `virtual-host`、Redis 的 `database`）**只从 `mq.<type>.*` 读取**，**不进公共 API**；**异常消息只出现键名、绝不出现取值**。

### 扩展全新后端

新增一种后端 = 在 `cn.hg.aifei.mq.<backend>` 包新增 `XxxMqProvider extends AbstractMqProvider`，并在
`MqProviderRegistry` 的静态初始化块加一行注册；装配逻辑无需改动（与 `aifei-cache` 同构）。

## 生命周期

- `start()`：解析配置（含 `mq.names`）→ **按实例逐个**创建总线 → 注册各实例（**主实例**同时注册为 `@Inject` 的 `Producer` / `Consumer` 单例）。重复启动不重复装配；**配置**有误（未注册后端 / 必填键缺失或非法 / `mq.names` 非法）即失败，不留半装配状态。**注意**：这里的「失败」只覆盖**配置**；外部**连通性**不保证在启动期校验——`mq.type = rocketmq` 时即使 nameserver 不可达，`start()` 仍可能成功（懒连接），故障延迟到首次使用（见上文「使用 RocketMQ」的运维提示）。
- `stop()`：注销各实例（此后 `ProducerUtil` / `ConsumerUtil` 取实例即失败）→ 关闭**全部实例**总线。重复停止不重复关闭。

## 文档

- 设计契约（事实来源）：`docs/design.md`
- 开发约束：`AGENTS.md`
- 测试策略：`docs/testing.md`
