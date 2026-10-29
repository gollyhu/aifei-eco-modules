# aifei-mq

为 aifei 应用补齐框架未覆盖的异步消息能力（解耦、削峰）。公共 API 只有 8 个类型，业务代码只依赖它们——换 MQ 中间件只改配置、不改代码。

**适合**：进程内解耦 / 削峰，或需要在 RocketMQ、Kafka、RabbitMQ、MQTT、Redis Streams 之间切换而业务代码不动。

**不适合**：延迟消息 / 定时消息（`send` 一律立即投递，请在业务侧自行调度）；事务消息、消息回溯、死信管理等 broker 高级特性（公共面未覆盖，需直接用对应客户端）。

- 坐标：`io.github.gollyhu:aifei-mq:1.0.0`
- 包名：`cn.hg.aifei.mq`
- 技术基线：Java 8
- 后端：默认**内存/本地实现**（零第三方运行依赖）；内置**6 个真实后端**——`rocketmq` / `mqtt`（MQTT v3.1.1）/ `mqtt5`（MQTT v5.0）/ `rabbitmq` / `kafka` / `redisstream`（客户端均由消费方自带）；以 provider 扩展点追加更多后端

## 目录

- [安装](#安装)
- [支持的后端](#支持的后端)
- [5 分钟上手（内存后端，零依赖）](#5-分钟上手内存后端零依赖)
- [取用方式](#取用方式)
- [Topic 与订阅](#topic-与订阅)
- [接口](#接口)
- [配置](#配置)
- [换后端 / 使用真实后端](#换后端--使用真实后端)
- [生命周期](#生命周期)
- [常见问题](#常见问题)

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

## 5 分钟上手（内存后端，零依赖）

默认后端是内存实现：不装中间件、不加第三方依赖，同一进程内即可跑通「配置 → 装配 → 订阅 → 发送 → 回调」。**先跑通这一节，再换真实后端。**

### 第 1 步：写配置

`app-config.txt`（`memory` 是默认值，整份不写也能跑；显式写出更清楚）：

```properties
mq.type = memory
```

### 第 2 步：装配插件

在应用配置类里注册插件：

```java
public class AppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Plugins plugins) {
        plugins.add(new MqPlugin(PropKit.use("app-config.txt")));
    }
}
```

### 第 3 步：启动期订阅

```java
// 在应用启动后的初始化入口登记一次（不要在收到第一条请求时才订阅）
ConsumerUtil.subscribe("order-events", message ->
        System.out.println("收到 " + message.topic() + ": "
                + new String(message.payload(), StandardCharsets.UTF_8)));
```

> **订阅必须在插件启动之后、第一条消息发出之前登记**。内存后端没有暂存队列，未订阅时发出的消息直接丢弃（`send` 仍返回成功）。用 `@Inject Consumer` 也可以，但不要在字段注入完成之前（如构造函数内）使用。

### 第 4 步：发送

```java
@Service
public class OrderService {

    @Inject Producer producer;

    public void placeOrder(String orderId) {
        producer.send("order-events",
                "{\"type\":\"created\",\"version\":1,\"orderId\":\"" + orderId + "\"}");
    }
}
```

触发一次 `placeOrder`，控制台应打印回调。内存后端是**同步投递**：`send` 返回时回调已经执行完毕。

### 第 5 步：换真实后端

只改配置（见[支持的后端](#支持的后端)），业务代码不动；同时把该后端的客户端依赖加进你的应用。topic 与订阅的完整规则见 [Topic 与订阅](#topic-与订阅)。

## 取用方式

插件启动后有两种取用方式：

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

## Topic 与订阅

### topic 是什么

`topic` 就是一条字符串标识，发送侧与订阅侧用**同一个字符串**对齐。模块不解析它、不限定字符集，只要求**非空白**（`null` 或全空白抛 `IllegalArgumentException`）。

- 建议用**小写 kebab-case 的业务域**（`order-events`），不要拼动态值（订单号、用户 ID、日期）——否则 topic 数量不可控，RabbitMQ 还会为每个 topic 声明一个队列。
- 事件类型不要做成 topic，放进 payload（见[Topic 使用模式](#topic-使用模式e2-定档)）。

### 一次订阅的完整写法

```java
// 订阅：把监听器登记到本实例的该 topic 上
ConsumerUtil.subscribe("order-events", message -> {
    String json = new String(message.payload(), StandardCharsets.UTF_8);   // payload() 每次返回副本
    System.out.println(message.topic() + " -> " + json);
});

// 发送：同一个 topic 字符串
ProducerUtil.send("order-events", "{\"type\":\"created\",\"version\":1,\"orderId\":\"A1001\"}");
```

`subscribe` 是**登记**而不是拉取：登记之后，该 topic 上到达的每条消息都会回调该监听器。历史消息是否补发由后端决定（内存后端不补发，见下表）。

### 订阅时机（最容易踩的一步）

1. **在插件启动之后调用**：`MqPlugin.start()` 完成后 `Consumer` 才可用；启动前 / 停止后调用抛 `IllegalStateException`。
2. **在应用初始化阶段一次性登记完毕**，不要等"第一次用到"才订阅——那属于运行期动态增 topic，已定档为不支持（见[Topic 使用模式](#topic-使用模式e2-定档)）。
3. **内存后端必须先订阅再发送**：进程内没有暂存，未订阅时 `send` 直接丢弃消息（`send` 仍成功返回 `SendResult`）。
4. **真实后端的订阅有副作用与成本**：首次订阅某个 topic 才会在 broker 侧建队列 / 起消费者 / 建消费组（见下表）；订阅失败抛 `MqException`，由调用方处理。

### 一个 topic 多个监听器

同一实例内对同一 topic 登记多个监听器，**每个监听器都会收到每条消息**。单条消息的派发按本地 `subscribe` 登记顺序执行；消息**之间**的顺序随后端（内存后端单主题 FIFO，真实后端不保证）。

### 一个监听器订阅多个 topic

`listener` 与 topic 是多对多关系：对每个 topic 各调用一次 `subscribe` 即可；`unsubscribe(topic, listener)` 只解除**这一个组合**。

### 重复订阅会重复回调

模块**不做去重**：同一个 listener 对同一个 topic 调用两次 `subscribe`，每条消息就会被回调**两次**（MQTT 后端只向客户端登记一次过滤器，但本地派发仍按登记次数）。请只在初始化路径登记一次，或用集合标记防重。

### 订阅的作用域：实例 / 进程 / 消费组

- **实例级隔离**：主实例与每个命名实例各自一条总线、各自一份订阅表。同一 topic 名在不同实例之间**互不影响**——只在 `mq.orders` 上订阅 `order-events`，主实例不会收到该消息。
- **进程级隔离**：内存后端只在**本进程**内投递；跨进程 / 跨机器请换真实后端。
- **跨进程分发由 broker 决定**（`rocketmq` / `kafka` / `redisstream`）：同一消费组的多个实例**竞争消费**（每个实例只收到一部分），不同消费组**各收一份**（广播效果）；MQTT 与 RabbitMQ 的分发由各自的会话 / 队列模型决定，见对应后端小节。

```java
// 命名实例的订阅必须写到该实例的 Consumer 上
Consumer orders = ConsumerUtil.use("orders");
orders.subscribe("order-events", message -> handle(message));
```

> 命名实例用哪个后端、消费组 / 消费者名是什么，由 `mq.<name>.*` 配置决定（见[配置](#配置)）。

### 各后端在首次 `subscribe` 时做了什么

| 后端 | 首次 `subscribe(topic)` 的动作 | 在**未订阅**的 topic 上 `send` |
| --- | --- | --- |
| `memory` | 仅登记到本进程订阅表 | **丢弃**（`send` 仍成功） |
| `rocketmq` | 向消费者登记该 topic；首次调用时启动消费者 | broker 存储，消费者上线后消费 |
| `mqtt` / `mqtt5` | 向客户端登记该 topic 过滤器（同过滤器只登记一次） | 由 broker 与会话 / QoS 决定 |
| `rabbitmq` | `queueDeclare(topic)`；配置了 `exchange` 时再声明交换机并绑定，随后开始投递 | **默认交换机下队列不存在 → broker 静默丢弃**（`send` 仍成功） |
| `kafka` | 首个主题时创建消费者、订阅并启动拉取循环 | broker 存储（topic 需已存在或 broker 允许自动创建），按消费组位点消费 |
| `redisstream` | 建消费组并启动拉取循环 | 留存于流中，按消费组读取 |

> 这张表解释了为什么本模块把 **topic 集合当作订阅契约**：RabbitMQ 在默认交换机下"发了没人订"不会报错，只会悄悄丢消息。

### 通配订阅只有 MQTT 支持

`mqtt` / `mqtt5` 的 `subscribe` 参数是 **MQTT 过滤器**，可用 `+`（单层）与 `#`（多层）；但 `send` 的 topic 仍是**精确名**。其余后端（`memory` / `rocketmq` / `rabbitmq` / `kafka` / `redisstream`）精确匹配；RabbitMQ 的通配匹配只在配置了 `exchange` 后由 broker 侧绑定承担。

### 取消订阅

- **按实例身份**移除：`unsubscribe(topic, listener)` 必须传**同一个 listener 对象**。`message -> handle(message)` 每写一次就是新对象，用它取消不掉任何订阅——需要取消时把 listener 存成字段或变量。
- 取消后**本进程不再向该 listener 派发该 topic 的消息**；真实后端**不撤销** broker 侧订阅（尽力语义）。
- 重复取消、取消从未登记的订阅都是**幂等静默 no-op**；停止后调用抛 `IllegalStateException`；参数非法抛 `IllegalArgumentException`。
- 取消最后一个监听器后该 topic 的空登记**保留**，不会自动从订阅表移除。

### 订阅易错点速查

| 现象 | 原因 |
| --- | --- |
| 发送成功但收不到 | 内存后端先发后订阅（消息已丢弃）；或订阅写在了另一个实例上 |
| 同一条消息回调两次 | 同一 listener 对同一 topic 重复 `subscribe` |
| `unsubscribe` 没反应 | 传的是新写的 lambda，不是当初登记的那个对象 |
| 启动期报 `MqException` | 真实后端订阅失败（队列声明 / 消费者启动 / broker 不可达） |
| 跨机器收不到 | 用的是内存后端，它只在当前进程内投递 |
| RabbitMQ 收不到 | 该 topic 从未被任何实例订阅过，队列不存在，broker 丢弃消息 |

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

> 订阅时机、一个 topic 多个监听器、命名实例隔离、各后端首次 `subscribe` 的副作用，见 [Topic 与订阅](#topic-与订阅)。

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

> **跨后端差异说明**：上列各项描述的是**内存后端**的行为；本模块已内置 6 个真实后端（见[支持的后端](#支持的后端)），provider 扩展点可继续追加。**跨后端一致**的是核心契约：`Message` 不可变与双向防御性拷贝、`Producer` 的 3 个 `send` 重载（文本按 UTF-8）、`Consumer.subscribe` 的登记语义与 `Consumer.unsubscribe` 的取消语义（**取消后本进程不再向该 listener 派发该主题消息**）、`MessageListener` 异常隔离、`SendResult` 无 success 布尔（失败抛异常）、无订阅者时 `send` 仍成功返回 `SendResult`（§5.6 A5）、异常分类（参数非法 `IllegalArgumentException` / 状态非法 `IllegalStateException` / 运行时故障 `MqException`）、`close()` / `stop()` 幂等与停止后调用抛 `IllegalStateException`。**随后端而变**的是：投递模型（内存同步投递 vs 真实后端异步 + 拉取，`send` 返回不等于已消费）、线程模型、有序性、投递保证（内存精确一次 vs 真实后端 at-least-once、可能重复消费）、持久化、无订阅者时消息是否丢弃 / 留存（§5.6 B6）、`messageId` 生成、可观测性、真实后端特有的消费位点 / 消费组，以及**取消订阅的尽力语义**（真实后端在拉取 / 消费组模型下「按 listener 取消」不保证 broker 侧立即撤销）。完整清单见 `docs/design.md` §5.6。

### Topic 使用模式（E2 定档）

本模块定档的推荐用法（设计事实来源：`docs/design.md` §5.8）：**topic 表达稳定的业务域，事件类型放 payload 业务字段，订阅侧本地分发**。

- **为什么不是动态 topic**：7 个后端中仅 MQTT（`+` / `#` 过滤器）与 RabbitMQ（改造后）有 topic 级通配，其余后端没有；且「只 send 从未订阅的 topic」在 RabbitMQ 下会被 broker **静默丢弃**（`send` 仍返回成功）。固定 topic 集合是唯一跨后端一致的模式，且运行期不涉及任何 broker 开关。
- **命名**：小写 kebab-case 领域名（`order-events`），单进程 topic 数量个位数到低两位数；**消费方集合相同 + 保序 / 权限需求相同**的事件才合并，故障域需隔离的保持拆分。
- **载荷**：JSON 含 `type`（事件类型）+ `version` + 业务字段；模块**不解析 payload 内容**，序列化由业务侧负责。
- **订阅即契约**：启动期一次性 `subscribe` 完毕，运行期不增减 topic——RabbitMQ 的 `subscribe` 会自动声明队列，这套模式下不存在「发了没人订」的路径。

```java
// 发送：topic = 业务域，事件类型进 payload
producer.send("order-events", "{\"type\":\"created\",\"version\":1,\"orderId\":\"A1001\"}");

// 订阅：启动期一次登记，本地按 type 分发（新事件类型 = 分发表加一行，模块零感知）
ConsumerUtil.subscribe("order-events", message -> dispatch(decode(message.payload())));
```

**注意**：合并 topic 会共享单主题 FIFO 保序流（状态变迁类事件是收益；需独立并行保序时拆 topic），过滤后置到消费侧，且失去 broker 级 ACL / 配额 / 隔离粒度。不适用场景与回退判据见 `docs/design.md` §5.8.7。

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

## 常见问题

**Q：应用启动成功就代表 MQ 可用吗？**
不代表。`mqtt` / `mqtt5` / `rabbitmq` 在启动期建连（broker 不可达即启动失败）；`rocketmq` / `kafka` / `redisstream` 为懒连接，故障延迟到首次收发（见[运维提示](#运维提示连通性与凭据)）。

**Q：怎么确认真的能收发？**
启动后发一条并观察订阅回调（[5 分钟上手](#5-分钟上手内存后端零依赖) 的第 3、4 步即最小闭环）；真实后端请按对应后端小节里的集成测试命令连上中间件跑契约用例。

**Q：消息会不会重复？**
`rocketmq` / `rabbitmq` / `kafka` / `redisstream` 为 at-least-once，故障 / 重投下**可能重复**，监听器请自行幂等；内存后端为进程内一次性回调，不重复。

**Q：一个进程能用多个后端 / 多个消费组吗？**
能，用 `mq.names` 声明命名实例，经 `use(name)` 取用（见[命名实例](#命名实例多后端混合)）。

**Q：需要延迟消息 / 定时消息怎么办？**
本模块不提供，请在业务侧用调度器定时后再 `send`。

**Q：消息体怎么序列化？**
模块只传 `byte[]`（文本以 UTF-8 编码），序列化与反序列化由业务侧负责。

## 文档

- 设计契约（事实来源）：`docs/design.md`
- 开发约束：`AGENTS.md`
- 测试策略：`docs/testing.md`
