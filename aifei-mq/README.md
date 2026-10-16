# aifei-mq

为 aifei 应用补齐框架未覆盖的异步消息能力（解耦、削峰）。业务代码只依赖公共接口，换 MQ 中间件只改配置、不改代码。

- 坐标：`io.github.gollyhu:aifei-mq:1.0.0`
- 包名：`cn.hg.aifei.mq`
- 技术基线：Java 8
- 后端：默认**内存/本地实现**（零第三方运行依赖）；内置**RocketMQ 真实后端**（`mq.type=rocketmq`，客户端由消费方自带）；其它后端以 provider 扩展点追加

## 安装

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-mq</artifactId>
    <version>1.0.0</version>
</dependency>
```

框架内核 `cn.aifei:aifei-all` 为 `provided`，由消费方应用自带。

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

// 方式二：静态门面（不注入也能用）
MqKit.send("order-created", "A1001");
MqKit.subscribe("order-created", message -> handle(message));
```

> 两种方式取到的是同一个实例。`MqKit` 与注入的 `Producer` / `Consumer` 都只在插件启动后可用。

## 接口

公共面固定为 7 个类型：`Message` / `Producer` / `Consumer` / `MessageListener` / `SendResult` / `MqKit` / `MqException`。

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

> **跨后端差异说明**：上列各项描述的是当前**内存后端**的行为。本模块后续可用 provider 扩展真实后端（如 RocketMQ）。**跨后端一致**的是核心契约：`Message` 不可变与双向防御性拷贝、`Producer` 的 3 个 `send` 重载（文本按 UTF-8）、`Consumer.subscribe` 的登记语义与 `Consumer.unsubscribe` 的取消语义（**取消后本进程不再向该 listener 派发该主题消息**）、`MessageListener` 异常隔离、`SendResult` 无 success 布尔（失败抛异常）、无订阅者时 `send` 仍成功返回 `SendResult`（§5.6 A5）、异常分类（参数非法 `IllegalArgumentException` / 状态非法 `IllegalStateException` / 运行时故障 `MqException`）、`close()` / `stop()` 幂等与停止后调用抛 `IllegalStateException`。**随后端而变**的是：投递模型（内存同步投递 vs 真实后端异步 + 拉取，`send` 返回不等于已消费）、线程模型、有序性、投递保证（内存精确一次 vs 真实后端 at-least-once、可能重复消费）、持久化、无订阅者时消息是否丢弃 / 留存（§5.6 B6）、`messageId` 生成、可观测性、真实后端特有的消费位点 / 消费组，以及**取消订阅的尽力语义**（真实后端在拉取 / 消费组模型下「按 listener 取消」不保证 broker 侧立即撤销）。完整清单见 `docs/design.md` §5.6。

### 异常分类

| 场景 | 异常 |
| --- | --- |
| 参数非法（topic 空、payload / text / listener / message / prop / messageId 为 null、`mq.type` 未注册） | `IllegalArgumentException` |
| 状态非法（停止后 `send` / `subscribe` / `unsubscribe`；未启动时取 `MqKit.producer()` / `consumer()`） | `IllegalStateException` |
| 运行时故障（后端运行时错误，如真实后端连接失败） | `MqException` |

## 配置

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.type` | 否 | `memory` | 后端类型；指向未注册后端时启动立即失败。**用 RocketMQ 必须显式 `mq.type = rocketmq`** |
| `mq.<type>.*` | 后端自定 | — | 后端专有配置；**内存后端无额外键**（不引入 `mq.memory.*`） |
| `mq.rocketmq.namesrv-addr` | **是**（用 RocketMQ 时） | — | RocketMQ nameserver 地址，多个用 `;` 分隔 |
| `mq.rocketmq.producer-group` | 否 | `DEFAULT_PRODUCER` | RocketMQ 生产者组 |
| `mq.rocketmq.consumer-group` | 否 | `DEFAULT_CONSUMER` | RocketMQ 消费者组 |

`app-config.txt`：

```properties
# 使用默认内存后端时无需配置 mq.type
mq.type = memory
```

## 换后端 / 使用 RocketMQ

换后端**只改配置、不改代码**：把 `mq.type` 指向目标后端即可（业务仍只依赖 `Producer` / `Consumer` / `MqKit`）。

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

> **反向需求**：若要让**每个实例都收到全部消息**（同主题全量各收 / 广播效果），**不要**让它们共用同一消费组——配置**不同的 `consumer-group`** 即可各收一份。「**单进程内多个消费组**」属**未来方向**（学 `aifei-cache` 的命名实例，见 `docs/design.md` §13 第 24 条），**本版未实现**。

### 扩展全新后端

新增一种后端 = 在 `cn.hg.aifei.mq.<backend>` 包新增 `XxxMqProvider extends AbstractMqProvider`，并在
`MqProviderRegistry` 的静态初始化块加一行注册；装配逻辑无需改动（与 `aifei-cache` 同构）。

## 生命周期

- `start()`：解析配置 → 创建总线 → 注册 `Producer` / `Consumer` 单例。重复启动不重复装配；**配置**有误（未注册后端 / 必填键缺失或非法）即失败，不留半装配状态。**注意**：这里的「失败」只覆盖**配置**；外部**连通性**不保证在启动期校验——`mq.type = rocketmq` 时即使 nameserver 不可达，`start()` 仍可能成功（懒连接），故障延迟到首次使用（见上文「使用 RocketMQ」的运维提示）。
- `stop()`：注销单例（此后 `MqKit` 取实例即失败）→ 关闭总线。重复停止不重复关闭。

## 文档

- 设计契约（事实来源）：`docs/design.md`
- 开发约束：`AGENTS.md`
- 测试策略：`docs/testing.md`
