# aifei-mq

给 aifei 应用补齐**异步消息**能力：业务只在「发送」和「订阅」两处写代码，消息走内存、RocketMQ 还是 Kafka 由配置决定。

- **适合**：进程内解耦 / 削峰；需要在多种消息后端之间切换而业务代码不动。
- **不适合**：延迟 / 定时消息（`send` 一律立即投递，请在业务侧自行调度）；事务消息、消息回溯、死信管理等 broker 高级特性（公共 API 未覆盖）。
- 坐标：`io.github.gollyhu:aifei-mq:1.0.0`（单 `jar`，内置 7 个后端）
- 包名：`cn.hg.aifei.mq`｜技术基线：Java 8

## 目录

- [1. 5 分钟快速开始](#1-5-分钟快速开始)
- [2. 常用用法](#2-常用用法)
- [3. 深入使用](#3-深入使用)
- [4. 高级使用](#4-高级使用)
- [5. 注意事项与技术难点](#5-注意事项与技术难点)
- [6. 参考](#6-参考)

---

## 1. 5 分钟快速开始

内存后端零第三方依赖、不装中间件，同一进程内就能跑通「配置 → 装配 → 订阅 → 发送 → 回调」。**先跑通这一节，再考虑换真实后端。**

### 1.1 加依赖

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-mq</artifactId>
    <version>1.0.0</version>
</dependency>
```

框架内核 `cn.aifei:aifei-all:1.1.2` 为 `provided`，由你的应用自带。**`memory` 后端不需要任何额外依赖**；用其他后端时客户端要自己引入（见 [3.1](#31-换后端只改配置--加客户端)）。

### 1.2 写配置

`app-config.txt` 里加一行（`memory` 是默认值，整份不写也能跑）：

```properties
mq.type = memory
```

### 1.3 装配插件

在应用配置类里注册插件：

```java
public class AppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Plugins plugins) {
        plugins.add(new MqPlugin(PropKit.use("app-config.txt")));
    }
}
```

### 1.4 订阅与发送

在应用启动后的初始化入口**登记一次**订阅：

```java
ConsumerKit.subscribe("order-events", message ->
        System.out.println("收到 " + message.topic() + ": "
                + new String(message.payload(), StandardCharsets.UTF_8)));
```

然后在业务里发送：

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

### 1.5 确认跑通

触发一次 `placeOrder`，控制台应打印回调。内存后端是**同步投递**：`send` 返回时回调已经执行完毕。

到这里就完成了最小闭环。接下来是日常使用中最常见的写法。

---

## 2. 常用用法

### 2.1 取用 Producer / Consumer

```java
// 方式一：注入
@Service
public class OrderService {

    @Inject Producer producer;
    @Inject Consumer consumer;
}

// 方式二：静态门面（不注入也能用）
ProducerKit.send("order-created", "A1001");
ConsumerKit.subscribe("order-created", message -> handle(message));

// 需要显式取实例时
Producer producer = ProducerKit.use();          // 主实例（与 @Inject 同一个对象）
Consumer consumer = ConsumerKit.use("orders");  // 命名实例（见 3.7）
```

两种方式取到的是**主实例的同一个对象**。`ProducerKit` / `ConsumerKit` 与注入的 `Producer` / `Consumer` 都**只在插件启动后可用**（未启动 / 已停止时调用抛 `IllegalStateException`）。`@Inject` 只对应主实例；命名实例只能经 `use(name)` 取。

### 2.2 topic 怎么取

`topic` 就是一条字符串标识，发送侧与订阅侧用**同一个字符串**对齐。模块不解析它、不限定字符集，只要求**非空白**（`null` 或全空白抛 `IllegalArgumentException`）。

- 建议用**小写 kebab-case 的业务域**（`order-events`），不要拼动态值（订单号、用户 ID、日期）——否则 topic 数量不可控。
- 事件类型不要做成 topic，放进 payload。
- 订阅的完整规则与推荐模式见 [4.2](#42-topic-使用模式)。

### 2.3 一次订阅的完整写法

```java
ConsumerKit.subscribe("order-events", message -> {
    String json = new String(message.payload(), StandardCharsets.UTF_8);   // payload() 每次返回副本
    System.out.println(message.topic() + " -> " + json);
});
ProducerKit.send("order-events", "{\"type\":\"created\",\"version\":1,\"orderId\":\"A1001\"}");
```

`subscribe` 是**登记**而不是拉取：登记之后，该 topic 上到达的每条消息都会回调该监听器；历史消息是否补发由后端决定（内存后端不补发）。

> 订阅时机很关键：**内存后端必须先订阅再发送**，否则消息直接丢弃；真实后端的首次订阅还会在 broker 侧建队列 / 起消费者。详见 [5.3](#53-内存后端必须先订阅再发送) 与 [3.9](#39-各后端首次-subscribe-做了什么)。

### 2.4 发送与返回值

```java
SendResult send(Message message);
SendResult send(String topic, byte[] payload);
SendResult send(String topic, String text);   // 文本以 UTF-8 编码为字节
```

- `SendResult` 只有 `topic` 与 `messageId`，**没有 success 布尔**：发送失败一律抛异常，返回即成功。
- `messageId` 用于**诊断**，唯一性只在本进程 / 单次投递范围内成立，**不要当幂等键**。
- 消息体是 `byte[]`，序列化 / 反序列化由业务侧负责（推荐 JSON）。

### 2.5 取消订阅

```java
void subscribe(String topic, MessageListener listener);
void unsubscribe(String topic, MessageListener listener);
```

`unsubscribe` 从该 topic 的登记列表中移除**该监听器对象**（按实例身份，不用 `equals`）。**重复取消**与**取消一个从未登记过的订阅**表现相同：**幂等静默 no-op，不报错**。

取消后本进程不再向该监听器派发该 topic 的消息；真实后端**不撤销** broker 侧订阅（尽力语义）。

### 2.6 一个 topic 多个监听器 / 一个监听器多个 topic

- 同一实例内对同一 topic 登记多个监听器：**每个监听器都会收到每条消息**，按 `subscribe` 登记顺序回调。
- 一个监听器订阅多个 topic：对每个 topic 各调用一次 `subscribe`；`unsubscribe(topic, listener)` 只解除**这一个组合**。

### 2.7 异常分类

| 场景 | 异常 |
| --- | --- |
| 参数非法（topic 空、payload / listener / message 为 null、`mq.type` 未注册、`use(name)` 实例名非法） | `IllegalArgumentException` |
| 状态非法（停止后 `send` / `subscribe` / `unsubscribe`；未启动时取实例） | `IllegalStateException` |
| 运行时故障（真实后端连接失败等） | `MqException` |

监听器自身抛出的运行时异常由消息层**就地隔离**：不中断同主题其他订阅者、不上抛给生产者。

### 2.8 投递语义

以内存后端为基准（跨后端一致的只有契约底线，模型差异见 [3.9](#39-各后端首次-subscribe-做了什么) 与 [3.10](#310-各后端-topic-语义差异)）：

- **同步投递**：`send` 在调用线程内依次回调订阅者，返回前回调已执行完毕；不引入线程池、队列或背压。
- **单主题保序**：同一主题内 `send` 的完成顺序即投递顺序；不保证跨主题顺序；多线程并发 `send` 的相对顺序不作保证。
- **多订阅者顺序**：同一主题的多个订阅者按 `subscribe` 登记顺序依次回调。
- **可重入**：允许在 `onMessage` 内再次 `send`，请自行避免无限递归。
- **不承诺延迟投递**：`send` 一律立即投递，延迟请在业务侧自行调度。

---

## 3. 深入使用

### 3.1 换后端：只改配置 + 加客户端

**发送与订阅代码保持一致；投递、持久化、顺序和重复语义由后端决定。** 切换后端 = 引入对应客户端 + 把 `mq.type` 指向它。

| `mq.type` | 支持级别 | 必填键 | 启动期连通性 | 需自行引入的客户端 |
| --- | --- | --- | --- | --- |
| `memory`（默认） | **稳定** | 无 | 不涉及 | 无需额外引入 |
| `rocketmq` | **稳定** | `namesrv-addr` | 懒连接 | `org.apache.rocketmq:rocketmq-client:4.9.7` |
| `rabbitmq` | **稳定** | `host` | 建连即校验 | `com.rabbitmq:amqp-client:5.21.0` |
| `mqtt` | 随包提供 · 不承诺语义 | `broker-url` | 建连即校验 | `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` |
| `mqtt5` | 随包提供 · 不承诺语义 | `broker-url` | 建连即校验 | `org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5` |
| `kafka` | 随包提供 · 不承诺语义 | `bootstrap-servers` | 懒连接 | `org.apache.kafka:kafka-clients:3.9.1`（**必须停 3.x**） |
| `redisstream` | 随包提供 · 不承诺语义 | `host` | 懒连接 | `redis.clients:jedis:8.0.1` |

> 「建连即校验」= broker 不可达则**应用启动失败**；「懒连接」= 应用能启动，故障延迟到首次收发才以 `MqException` 暴露。详见 [5.1](#51-启动成功不等于后端可达)。

客户端在本模块以 `optional` 声明（不随包传递），**必须由你的应用显式引入**；完整说明见 [5.2](#52-客户端必须自行引入)。各后端的完整配置键见 [6.1](#61-配置键总表)。

**下面按业务场景展开**：每个场景给出「配置 + 业务代码 + 该后端的语义差异」。业务代码在场景之间是**同一套写法**，切换后端只换配置与客户端依赖。

### 3.2 场景：下单后异步履约（RocketMQ）

订单落库后发事件，库存、积分、通知各自订阅同一主题；消费失败可重试、消息不丢。

```properties
mq.type = rocketmq
mq.rocketmq.namesrv-addr = 10.0.0.11:9876;10.0.0.12:9876   # 必填，多个用 ';' 分隔
mq.rocketmq.producer-group = ORDER_PRODUCER                 # 可选，默认 DEFAULT_PRODUCER
mq.rocketmq.consumer-group = ORDER_CONSUMER                 # 可选，默认 DEFAULT_CONSUMER
```

发送端——**先落库再发消息**，避免消费者查不到订单：

```java
@Service
public class OrderService {

    @Inject Producer producer;

    public void placeOrder(Order order) {
        orderMapper.insert(order);
        producer.send("order-events", orderCreatedPayload(order));   // byte[]，业务侧自行序列化
    }
}
```

消费端——事件类型放 payload、本地分发；**投递是 at-least-once，必须幂等**：

```java
// 应用初始化入口：启动期一次性登记
ConsumerKit.subscribe("order-events", message -> {
    OrderEvent event = decode(message.payload());
    if (!"created".equals(event.getType())) {
        return;
    }
    if (dedup.markOnce(event.getOrderId() + ":created")) {   // 重复投递在这里被挡掉
        inventoryService.reserve(event.getOrderId(), event.getItems());
    }
});
```

该后端的语义差异：

- `send` 返回成功只表示**已提交 broker**，不代表已被消费；回调在 RocketMQ 自有线程，不要在回调里做长事务或同步等待。
- **at-least-once**：故障、重试都可能重复投递，请用业务唯一键（如上例的 `orderId`）幂等。
- 消息持久化在 broker，消费者离线期间的消息上线后仍会消费；`unsubscribe` 是尽力语义，不撤销 broker 侧订阅。
- 同一 `consumer-group` 的多台实例**分摊**消息，且必须订阅相同的 topic 集合（见 [3.8](#38-场景消费者水平扩容与分工消费)）。

### 3.3 场景：耗时任务交给多个 worker（RabbitMQ）

报表生成、图片处理这类任务不适合占用 Web 请求；提交后入队，多台 worker 竞争消费。

```properties
mq.type = rabbitmq
mq.rabbitmq.host = 10.0.0.21        # 必填
mq.rabbitmq.prefetch = 5            # 每个 worker 的在途任务上限
mq.rabbitmq.exchange = aifei.jobs   # 可选；非空才走交换机
```

内网 broker 要求 TLS 时（`port` 不会随 `ssl.enabled` 自动切换，TLS 端口通常是 `5671`，须显式配置）：

```properties
mq.rabbitmq.ssl.enabled = true
mq.rabbitmq.port = 5671
mq.rabbitmq.ssl.trust-store = certs/truststore.p12   # 校验服务端证书；不配则用 JVM 默认信任库
mq.rabbitmq.ssl.trust-store-password = changeit
mq.rabbitmq.ssl.key-store = certs/client.p12         # 双向认证的客户端证书；不配则不提交
mq.rabbitmq.ssl.key-store-password = changeit
```

Web 层——提交任务后立即返回：

```java
@Service
public class ReportService {

    @Inject Producer producer;

    public String submit(ReportQuery query) {
        String jobId = jobRepo.create(query);
        producer.send("report-jobs", encode(new ReportJob(jobId, query)));
        return jobId;
    }
}
```

Worker 进程——多开即竞争消费，队列名与发送端 `topic` 一致：

```java
ConsumerKit.subscribe("report-jobs", message -> {
    ReportJob job = decode(message.payload());
    reportService.render(job);        // 耗时操作；处理失败 / 超时由 broker 重投
});
```

该后端的语义差异：

- `topic` 精确匹配 = **队列名**。队列由第一次 `subscribe` 声明，**worker 必须先起来**；发往不存在队列的消息会被 broker 静默丢弃。
- **at-least-once**：手动确认，worker 处理到一半断开任务会被重投，处理逻辑要幂等。
- 投递异步：未开 `publisher-confirms` 时 `send` 不等待 broker 回应；开启后 `SendResult.messageId` 取确认序号，可落库对账。
- `ssl.enabled=true` 时 SSL 上下文在建连**之前**构建：算法非法、证书库不可读、口令不符都表现为**应用启动失败**。

### 3.4 场景：连锁门店设备状态上报（MQTT）

设备按「区域 / 门店 / 设备 / 状态」分层上报，平台侧用一层订阅覆盖整个区域；v3.1.1 与 v5.0 是**两个独立后端**（`mq.mqtt.*` / `mq.mqtt5.*`，没有 `mq.mqtt.version` 键）。

```properties
# 平台侧：MQTT v3.1.1
mq.type = mqtt
mq.mqtt.broker-url = ssl://mqtt.example.com:8883   # 必填
mq.mqtt.client-id = store-platform-01             # 同 broker 下需唯一
mq.mqtt.qos = 1
mq.mqtt.clean-session = false                     # 断线后由 broker 保留会话与离线消息
mq.mqtt.username = platform
mq.mqtt.password = ******

# 改用 MQTT v5 时只换命名空间，两后端互不相通
# mq.type = mqtt5
# mq.mqtt5.broker-url = ssl://mqtt.example.com:8884
# mq.mqtt5.clean-start = false
# mq.mqtt5.session-expiry-interval-seconds = 3600
```

设备侧——主题用层级表达，发送侧必须是精确名：

```java
@Service
public class DeviceReporter {

    @Inject Producer producer;

    public void report(String region, String storeId, String deviceId, DeviceStatus status) {
        producer.send("store/" + region + "/" + storeId + "/device/" + deviceId + "/status",
                encode(status));
    }
}
```

平台侧——一个订阅覆盖整个区域，另一个只盯告警：

```java
// 上海浦东所有门店的全部设备状态
ConsumerKit.subscribe("store/shanghai-pudong/#", message ->
        dashboard.update(message.topic(), decode(message.payload())));

// 所有区域的设备告警：+ 只匹配一层
ConsumerKit.subscribe("store/+/+/device/alarm", message ->
        alarmService.raise(message.topic(), decode(message.payload())));
```

该后端的语义差异：

- 订阅侧 `topic` 是**过滤器**（`+` 单层、`#` 多层且只能作最后一段），发送侧必须是**精确主题名**；`message.topic()` 返回实际发布的主题，不是过滤器。
- `messageId` 由适配器本地生成（`mqtt:<client-id>:<序号>`），仅供诊断，**不能当幂等键**。
- 启动期 **fail-fast**：broker 不可达即应用启动失败。
- v5 共享订阅写在 topic 里：`subscribe("$share/alarm-workers/store/+/+/device/alarm", …)`（同组分摊、不同组各收一份），`send` 仍用真实主题。
- 同一实例登记**重叠**过滤器时是否重复回调由 broker 决定，本模块不去重。

### 3.5 场景：行为埋点同时供实时与归档使用（Kafka）

同一份埋点数据，实时指标和离线归档**各收一份**——用**不同消费组**表达，不改业务代码。

```properties
# 实时指标服务
mq.type = kafka
mq.kafka.bootstrap-servers = 10.0.0.31:9092,10.0.0.32:9092   # 必填，多个用 ',' 分隔
mq.kafka.group-id = behavior-realtime                        # 消费组
mq.kafka.acks = all                                          # 埋点宁可重发也不要丢
mq.kafka.auto-offset-reset = latest                          # 新组从最新开始，避免回放历史
```

上报端（Web / App 网关）：

```java
producer.send("user-behavior", encode(new BehaviorEvent(userId, action, at)));   // 返回即 broker 已接收
```

实时指标服务：

```java
ConsumerKit.subscribe("user-behavior", message -> {
    BehaviorEvent event = decode(message.payload());
    metrics.count(event.getUserId(), event.getAction());
});
```

归档服务——**同一 topic、另一个消费组**，因此也能收到完整数据：

```properties
mq.kafka.group-id = behavior-archive
mq.kafka.auto-offset-reset = earliest
```

该后端的语义差异：

- **消费组决定分发**：同组多实例分摊；不同组各收一份（上例实时与归档互不影响）。
- 异步生产 + 拉取消费：`send` 成功只表示 broker 已接收，`messageId` 由 broker 生成（`<topic>-<partition>@<offset>`）。
- 公共面**不暴露 message key**，无法控制分区：需要按业务键保序时只能拆 topic。
- `auto-offset-reset` 只在消费组无位点 / 位点越界时生效，已有位点时不回退。
- **懒连接**：启动成功 ≠ broker 可达（见 [5.1](#51-启动成功不等于后端可达)）。
- 客户端**必须停 3.x**：4.x 要求 Java 11+，会破坏本模块的 Java 8 基线。

### 3.6 场景：已有 Redis，用它做订单事件流（Redis Streams）

不想为中小项目再部署一套 MQ 时，可以直接用现成 Redis；配合 `pending` 与 `claim` 实现重启补漏、宕机接管。

```properties
mq.type = redisstream
mq.redisstream.host = 10.0.0.41                    # 必填
mq.redisstream.group = order-group                 # 消费组，流不存在时自动创建
mq.redisstream.consumer-name = order-worker-01     # 同组内必须唯一
mq.redisstream.start-mode = pending                # 重启后先补读本消费者未确认的消息
mq.redisstream.claim-enabled = true                # 接管同组内宕机实例的超时未确认消息
mq.redisstream.claim-idle-millis = 120000
```

发送端：

```java
producer.send("order-events", encode(event));   // 追加到流，返回即写入成功
```

消费端——可能被重读或被其它实例接管，**务必幂等**：

```java
ConsumerKit.subscribe("order-events", message -> {
    if (!dedup.markOnce(message.topic() + ":" + message.messageId())) {
        return;
    }
    orderEventService.handle(decode(message.payload()));
});
```

该后端的语义差异：

- `topic` = **流键**，`messageId` 为条目 ID（`毫秒-序号`）。
- `start-mode=new`（默认）只读订阅之后的新消息；`pending` 先重读本消费者未确认消息，**重复面随之扩大**。
- `claim-enabled=true` 会把同组内空闲超时的 pending 收归本消费者（宕机接管），同样扩大重复面。
- `maxlen` 是**近似裁剪**，超限的旧条目会被丢弃，不适合当消息存档。
- **懒连接**：启动成功 ≠ Redis 可达。

### 3.7 场景：同一应用同时处理业务与审计（命名实例）

业务事件要正常消费，审计流水要**独立消费组**再收一份，归档又走另一个后端——用 `mq.names` 声明多个实例，经 `use(name)` 取用。

```properties
# 主实例：业务事件
mq.type = rocketmq
mq.rocketmq.namesrv-addr = 10.0.0.11:9876
mq.rocketmq.consumer-group = ORDER_CONSUMER

# 命名实例：审计（独立消费组）与归档（另一个后端）
mq.names = audit,archive

mq.audit.type = rocketmq
mq.audit.rocketmq.namesrv-addr = 10.0.0.11:9876
mq.audit.rocketmq.consumer-group = AUDIT_CONSUMER

mq.archive.type = kafka
mq.archive.kafka.bootstrap-servers = 10.0.0.31:9092
mq.archive.kafka.group-id = archive-writer
```

```java
// 主实例：正常业务
producer.send("order-events", payload);
ConsumerKit.subscribe("order-events", message -> orderEventHandler.handle(message));

// 命名实例 audit：独立消费组，能收到该主题的另一份
ConsumerKit.use("audit").subscribe("order-events", message -> auditService.record(message));

// 命名实例 archive：写到另一个后端
ConsumerKit.use("archive").subscribe("order-events", message -> archiveService.store(message));
```

该场景的约束：

- 主实例走根级 `mq.*`，命名实例走 `mq.<name>.*`，后端专有键为 `mq.<name>.<type>.*`。
- `@Inject` 只对应**主实例**；命名实例只能经 `use(name)` 取。
- **实例级隔离**：每个实例各自一条连接、各自一份订阅表，同一 topic 名在实例之间互不影响。
- `mq.names` 为空项 / 重名 / 保留名（`type` / `names`）/ `__MAIN__` 即启动失败。

### 3.8 场景：消费者水平扩容与分工消费

订单量上来后，把消费者部署成多个实例即可，**不需要改代码**——同一消费组内由 broker 自动分摊：

```properties
# 两台订单服务实例，配置完全一致
mq.type = rocketmq
mq.rocketmq.consumer-group = ORDER_CONSUMER
```

结果：订单事件被两台实例**分摊**（每台只处理一部分）。扩容时的约束与反向需求：

- RocketMQ：同组实例必须订阅**相同 topic 集合**，否则 rebalance 异常；订阅集合不同就配不同组。
- Kafka：同 `group-id` 分摊；要「每台各收一份」就配不同组。
- 反向需求（广播 / 每台各收一份）一律用**不同消费组**表达；单进程内多个组用[命名实例](#37-场景同一应用同时处理业务与审计命名实例)。
- 新实例加入会触发一次 rebalance / 分区重分配，出现短暂停顿属正常。

### 3.9 各后端首次 subscribe 做了什么

订阅不只是本地登记：真实后端会在 broker 侧建队列 / 起消费者。排查「为什么多了一个队列 / 消费者」「为什么发了没人收」时看这张表：

| 后端 | 首次 `subscribe(topic)` 的动作 | 在**未订阅**的 topic 上 `send` |
| --- | --- | --- |
| `memory` | 仅登记到本进程订阅表 | **丢弃**（`send` 仍成功） |
| `rocketmq` | 向消费者登记该 topic；首次调用时启动消费者 | broker 存储，消费者上线后消费 |
| `mqtt` / `mqtt5` | 向客户端登记该 topic 过滤器（同过滤器只登记一次） | 由 broker 与会话 / QoS 决定 |
| `rabbitmq` | `queueDeclare(topic)`；配置 `exchange` 时再声明交换机并绑定 | **默认交换机下队列不存在 → broker 静默丢弃** |
| `kafka` | 首个主题时创建消费者、订阅并启动拉取循环 | broker 存储，按消费组位点消费 |
| `redisstream` | 建消费组并启动拉取循环 | 留存于流中，按消费组读取 |

> 这张表解释了为什么本模块把 **topic 集合当作订阅契约**：RabbitMQ 在默认交换机下「发了没人订」不会报错，只会悄悄丢消息。

### 3.10 各后端 topic 语义差异

| 后端 | 订阅侧 `topic` 读作 | 发送侧 `topic` |
| --- | --- | --- |
| `memory` / `rocketmq` / `kafka` / `redisstream` | 精确名（`redisstream` 为流键） | 精确名 |
| `rabbitmq` | 队列名（routing key）；配置 `exchange` 后由 broker 侧绑定承担通配 | 精确 routing key |
| `mqtt` / `mqtt5` | **过滤器**：`+` 单层、`#` 多层（只能作最后一段） | 精确主题名 |

---

## 4. 高级使用

### 4.1 扩展新后端

新增一种后端 = **在本 JAR 内新增实现包**，实现契约层扩展点即可，装配逻辑无需改动：

1. `XxxProvider implements MqProvider`——`public`、无参构造、**无状态**；提供 `type()`（后端标识）与 `createBus(MqConfig)`。
2. `XxxMessageBus implements MessageBus`——实现 `publish` / `subscribe` / `unsubscribe` / `close` 四个方法，订阅登记与派发由本后端负责。
3. 在 `src/main/resources/META-INF/services/cn.hg.aifei.mq.internal.MqProvider` 追加一行 provider 全限定类名，并重新发布本 JAR。

约束：`Producer` / `Consumer` 视图已统一提供，后端**不要**再写视图类；配置一律经 `MqConfig.get(relativeKey)` 读取，**不要手写** `"mq.<type>."` 前缀；后端行为差异须在 `docs/design.md` 的差异清单中逐项列明。

### 4.2 Topic 使用模式

推荐用法（设计事实来源：`docs/design.md` §5.8）：**topic 表达稳定业务域，事件类型放 payload 业务字段，订阅侧本地分发**。

- **为什么不在运行期增减 topic**：7 个后端中仅 MQTT（`+` / `#`）与 RabbitMQ（配置交换机后）有 topic 级通配；「只 send 从未订阅的 topic」在 RabbitMQ 下会被 broker 静默丢弃。固定 topic 集合是唯一跨后端一致的模式。
- **命名与载荷**：小写 kebab-case 领域名；单进程 topic 个位数到低两位数（消费方集合与保序 / 权限需求相同的才合并）；payload 用 JSON（`type` + `version` + 业务字段），模块不解析。
- **订阅即契约**：启动期一次性 `subscribe` 完毕，运行期不增减 topic。

```java
producer.send("order-events", "{\"type\":\"created\",\"version\":1,\"orderId\":\"A1001\"}");
ConsumerKit.subscribe("order-events", message -> dispatch(decode(message.payload())));
```

> 合并 topic 会共享单主题 FIFO 保序流（状态变迁类事件是收益，需独立并行保序时拆 topic），过滤后置到消费侧，并失去 broker 级 ACL / 配额隔离粒度。

### 4.3 生命周期

- `start()`：解析配置（含 `mq.names`）→ 按实例逐个建连接 → 登记各实例（主实例同时注册为 `@Inject` 的 `Producer` / `Consumer`）。重复启动不重复装配；配置有误（必填键缺失或非法 / `mq.names` 非法）即失败，不留半装配。
- `stop()`：注销各实例（此后 `ProducerKit` / `ConsumerKit` 取实例即失败）→ 关闭全部实例。重复停止不重复关闭。
- 只支持**单次启动**，不支持 `stop()` 后原地重启。

---

## 5. 注意事项与技术难点

### 5.1 「启动成功」不等于「后端可达」

| 后端 | 启动期行为 | 故障暴露时机 |
| --- | --- | --- |
| `mqtt` / `mqtt5` / `rabbitmq` | 构造期即建连（fail-fast） | **应用启动失败** |
| `rocketmq` / `kafka` / `redisstream` | 懒连接 | 首次 `send` / 消费拉取时 `MqException` |

不要把「应用启动成功」当作「后端可用」。`rocketmq` 的 `producer.start()` 明确**不校验** nameserver 连通性；启动后请以**实际收发探活**确认。

注意区分：`mq.type` 指向未注册类型、必填键缺失等**配置错误**仍在启动期立即失败。

### 5.2 客户端必须自行引入

6 个后端客户端在本模块以 `optional` 声明：它们在**模块内部**编译与测试期可用，但**不向下游传递**。因此：

- 用 `memory`：只引 `aifei-mq` 即可。
- 用其他后端：**必须**在应用中显式声明对应客户端（版本见 [3.1](#31-换后端只改配置--加客户端)）。
- 漏引客户端又配了该 `mq.type`：`MqPlugin.start()` 会回滚、不留半装配，但抛出的是 `NoClassDefFoundError`（**不是** `MqException`）——看到这个错误先查依赖。

### 5.3 内存后端必须先订阅再发送

`memory` 后端只在**本进程**内投递，且**没有暂存队列**：未订阅时 `send` 仍返回成功，但消息被直接丢弃。因此必须在应用初始化阶段完成订阅，不要等「第一次用到」才订阅。

真实后端一般由 broker 暂存，但仍建议启动期一次性订阅（见 [4.2](#42-topic-使用模式)）。

### 5.4 重复订阅会重复回调

模块**不做去重**：同一 listener 对同一 topic 调用两次 `subscribe`，每条消息会被回调**两次**（MQTT 只向客户端登记一次过滤器，但本地派发仍按登记次数）。请只在初始化路径登记一次，或用集合标记防重。

### 5.5 unsubscribe 按对象身份

`unsubscribe(topic, listener)` 必须传**当初登记的同一个对象**。`message -> handle(message)` 每写一次就是新对象，用它取消不掉任何订阅——需要取消时把 listener 存成字段或变量。

另外：取消最后一个监听器后，该 topic 的空登记会**保留**，不会自动从订阅表移除。

### 5.6 无订阅者时消息去哪了

`send` 仍然成功返回 `SendResult`（`messageId` 已分配）——这是跨后端共同底线。但消息**是丢弃还是留存随后端而变**：

- `memory`：丢弃。
- `rocketmq` / `kafka` / `redisstream`：broker 存储，消费者上线后按位点消费。
- `rabbitmq`：默认交换机下队列不存在 → broker 静默丢弃。
- `mqtt` / `mqtt5`：由 broker 与会话 / QoS 决定。

### 5.7 at-least-once 与幂等

`rocketmq` / `rabbitmq` / `kafka` / `redisstream` 均为 **at-least-once**：故障、重投、位点回退都可能导致**重复消费**，监听器必须自行幂等（如按 `messageId` 或业务唯一键去重）。内存后端是进程内一次性回调，不重复。

### 5.8 实验性后端不承诺语义

`mqtt` / `mqtt5` / `kafka` / `redisstream` 随包提供，但**运行期语义未经真机验证、不在 1.0.0 稳定支持矩阵**。生产环境请优先使用 `memory` / `rocketmq` / `rabbitmq`。

### 5.9 易错点速查

| 现象 | 原因 |
| --- | --- |
| 发送成功但收不到 | 内存后端先发后订阅（消息已丢弃）；或订阅写在了另一个实例上 |
| 同一条消息回调两次 | 同一 listener 对同一 topic 重复 `subscribe` |
| `unsubscribe` 没反应 | 传的是新写的 lambda，不是当初登记的那个对象 |
| 启动报 `MqException` | 真实后端订阅失败 / 配置非法（队列声明、消费者启动、必填键缺失） |
| 启动报 `NoClassDefFoundError` | 配了真实后端但没引入对应客户端（见 [5.2](#52-客户端必须自行引入)） |
| 启动成功但一发消息就报错 | 懒连接后端不可达（`rocketmq` / `kafka` / `redisstream`，见 [5.1](#51-启动成功不等于后端可达)） |
| 跨机器收不到 | 用的是内存后端，它只在当前进程内投递 |
| RabbitMQ 收不到 | 该 topic 从未被任何实例订阅过，队列不存在，broker 丢弃消息 |

---

## 6. 参考

### 6.1 配置键总表

通用键：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.type` | 否 | `memory` | 主实例后端类型；指向未注册类型时启动立即失败 |
| `mq.<type>.*` | 后端自定 | — | 后端专有配置；**内存后端无额外键** |
| `mq.names` | 否 | — | 声明命名实例（逗号分隔）；主实例恒存在、无需声明 |
| `mq.<name>.type` / `mq.<name>.<type>.*` | 否 | — | 命名实例的类型与专有配置 |

`rocketmq`（前缀 `mq.rocketmq.`，命名实例 `mq.<name>.rocketmq.`）：

| 键 | 必填 | 默认 |
| --- | --- | --- |
| `namesrv-addr` | **是** | — |
| `producer-group` | 否 | `DEFAULT_PRODUCER` |
| `consumer-group` | 否 | `DEFAULT_CONSUMER` |

`rabbitmq`（前缀 `mq.rabbitmq.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `host` | **是** | — | broker 主机名 / IP |
| `port` | 否 | 客户端默认 | TLS 端口通常 5671，须显式配置 |
| `virtual-host` | 否 | `/` | vhost |
| `username` / `password` | 否 | — | 凭据（只进配置域） |
| `exchange` / `exchange-type` | 否 | 空 / `topic` | 为空走默认交换机（`topic` 即队列名） |
| `durable` / `prefetch` | 否 | `true` / `0` | 队列持久化 / 消费预取上限 |
| `publisher-confirms` | 否 | `false` | 开启后 `messageId` 取确认序号 |
| `auto-reconnect` / `topology-recovery` | 否 | `true` / `true` | 客户端原生恢复 |
| `connection-timeout-millis` / `network-recovery-interval-millis` | 否 | 客户端默认 | 建连超时 / 恢复间隔 |
| `ssl.enabled` | 否 | `false` | TLS 开关；关闭时其余 `ssl.*` 一律不读 |
| `ssl.algorithm` | 否 | `TLSv1.2` | SSL 上下文算法 |
| `ssl.trust-store` / `ssl.trust-store-type` / `ssl.trust-store-password` | 否 | — / `PKCS12` / — | 信任库（按文件系统路径解析） |
| `ssl.key-store` / `ssl.key-store-type` / `ssl.key-store-password` | 否 | — / `PKCS12` / — | 客户端证书（双向认证） |

`mqtt`（前缀 `mq.mqtt.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `broker-url` | **是** | — | `tcp://host:1883` / `ssl://host:8883` |
| `client-id` | 否 | 客户端生成 | 同一 broker 下需唯一 |
| `clean-session` | 否 | `true` | `false` 时依赖 broker 保留会话与离线消息 |
| `qos` / `retain` | 否 | `0` / `false` | 订阅与发布的默认值（配置域，不进 `send` 签名） |
| `username` / `password` | 否 | — | 认证凭据 |
| `auto-reconnect` | 否 | `true` | 客户端原生自动重连 |
| `keep-alive-seconds` / `connection-timeout-seconds` / `reconnect-delay-max-millis` | 否 | 客户端默认 | 保活 / 建连超时 / 重连退避上限 |

`mqtt5`（前缀 `mq.mqtt5.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `broker-url` | **是** | — | v5 客户端口径的 server URI |
| `client-id` | 否 | 适配器生成 | v5 无「生成 client-id」API，未配置时由适配器生成 |
| `clean-start` / `session-expiry-interval-seconds` | 否 | `true` / `0` | v5 会话语义 |
| `qos` / `retain` | 否 | `0` / `false` | 订阅与发布默认值 |
| `username` / `password` | 否 | — | 认证凭据 |
| `auto-reconnect` | 否 | `true` | 客户端原生自动重连 |
| `keep-alive-seconds` / `connection-timeout-seconds` / `reconnect-delay-millis` / `reconnect-delay-max-millis` | 否 | 客户端默认 | 保活 / 超时 / 重连退避 |

`kafka`（前缀 `mq.kafka.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `bootstrap-servers` | **是** | — | broker 地址列表，多个用 `,` |
| `group-id` | 否 | `aifei-mq` | 消费组（同组多实例分摊） |
| `auto-offset-reset` | 否 | `earliest` | 无位点 / 越界时起点（`earliest` / `latest` / `none`） |
| `acks` | 否 | `1` | 生产者应答级别（`0` / `1` / `all`） |
| `client-id` | 否 | 客户端默认 | 客户端标识 |
| `security-protocol` / `sasl-mechanism` | 否 | 明文 / — | 安全协议与 SASL 机制 |
| `username` / `password` / `sasl-jaas-config` | 否 | — | 凭据；仅 `PLAIN` / `SCRAM-SHA-256` / `SCRAM-SHA-512` 由适配器拼 JAAS |
| `request-timeout-ms` / `reconnect-backoff-ms` / `retry-backoff-ms` / `poll-timeout-ms` | 否 | 客户端默认 | 超时与退避 |

`redisstream`（前缀 `mq.redisstream.`）：

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `host` | **是** | — | Redis 主机 |
| `port` / `database` | 否 | 客户端默认 | 端口 / 库号 |
| `username` / `password` | 否 | — | 凭据（Redis 6+ ACL） |
| `group` / `consumer-name` | 否 | `aifei-mq` / 实现生成 | 消费组（流不存在时创建）/ 同组内唯一消费者名 |
| `start-mode` | 否 | `new` | `new` 只读新消息；`pending` 先重读未确认消息 |
| `claim-enabled` / `claim-idle-millis` | 否 | `true` / `60000` | 接管同组内空闲超时的 pending |
| `maxlen` | 否 | 空（不裁剪） | 流长度上限（近似裁剪） |
| `count` / `block-ms` / `timeout-millis` / `auto-reconnect` | 否 | 客户端默认 | 读取批量 / 阻塞 / 超时 / 重连 |

> 凭据类键（`username` / `password` / `sasl-jaas-config` 等）只从配置读取、不进公共 API；异常消息只出现键名，不出现取值。

### 6.2 集成测试

6 个真实后端的端到端用例**默认关闭**，需具备对应中间件后显式开启；**开启后连不上必须失败**。命令在 `aifei-mq/` 模块根执行，PowerShell 下 `-D` 参数须整体加引号：

```bash
mvn test "-Dtest=RocketMqMessageBusContractTest" "-Drocketmq.integration=true" "-Drocketmq.namesrv-addr=127.0.0.1:9876"
mvn test "-Dtest=MqttMessageBusContractTest" "-Dmqtt.integration=true" "-Dmqtt.broker-url=tcp://127.0.0.1:1883"
mvn test "-Dtest=Mqtt5MessageBusContractTest" "-Dmqtt5.integration=true" "-Dmqtt5.broker-url=tcp://127.0.0.1:1883"
mvn test "-Dtest=RabbitMqMessageBusContractTest" "-Drabbitmq.integration=true" "-Drabbitmq.host=127.0.0.1" "-Drabbitmq.port=5672"
mvn test "-Dtest=KafkaMessageBusContractTest" "-Dkafka.integration=true" "-Dkafka.bootstrap-servers=127.0.0.1:9092"
mvn test "-Dtest=RedisStreamMessageBusContractTest" "-Dredisstream.integration=true" "-Dredisstream.host=127.0.0.1" "-Dredisstream.port=6379"
```

### 6.3 常见问题

**Q：应用启动成功就代表 MQ 可用吗？**
不代表。见 [5.1](#51-启动成功不等于后端可达)：`mqtt` / `mqtt5` / `rabbitmq` 启动期建连；`rocketmq` / `kafka` / `redisstream` 懒连接。

**Q：怎么确认真的能收发？**
启动后发一条并观察订阅回调（[1.4](#14-订阅与发送) 就是最小闭环）；真实后端按 [6.2](#62-集成测试) 连上中间件跑契约用例。

**Q：消息会不会重复？**
`rocketmq` / `rabbitmq` / `kafka` / `redisstream` 为 at-least-once，可能重复，监听器请自行幂等；内存后端不重复。

**Q：一个进程能用多个后端 / 多个消费组吗？**
能，用 `mq.names` 声明命名实例（[3.7](#37-场景同一应用同时处理业务与审计命名实例)）。

**Q：需要延迟消息 / 定时消息怎么办？**
本模块不提供；请在业务侧用调度器定时后再 `send`。

**Q：消息体怎么序列化？**
模块只传 `byte[]`（文本以 UTF-8 编码），序列化与反序列化由业务侧负责。

### 6.4 文档

- 设计契约（事实来源）：`docs/design.md`
- 开发约束：`AGENTS.md`
- 测试策略：`docs/testing.md`
