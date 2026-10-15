# aifei-mq

为 aifei 应用补齐框架未覆盖的异步消息能力（解耦、削峰）。业务代码只依赖公共接口，换 MQ 中间件只改配置、不改代码。

- 坐标：`io.github.gollyhu:aifei-mq:1.0.0`
- 包名：`cn.hg.aifei.mq`
- 技术基线：Java 8
- 首批后端：**内存/本地实现**（零第三方运行依赖）；真实后端（Kafka / RocketMQ 等）以 provider 扩展点追加

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
```

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
- **无订阅者视为成功**：消息被丢弃，但仍返回 `SendResult`（`messageId` 已分配）。
- **可重入**：允许在 `onMessage` 内再次 `send`，调用方自行避免无限递归。
- **监听器异常隔离**：监听器抛出的运行时异常就地丢弃，不中断同主题其他订阅者、不上抛给生产者。
- **无取消订阅**：本版不提供 `unsubscribe`。

### 异常分类

| 场景 | 异常 |
| --- | --- |
| 参数非法（topic 空、payload / text / listener / message / prop / messageId 为 null、`mq.type` 未注册） | `IllegalArgumentException` |
| 状态非法（停止后 `send` / `subscribe`；未启动时取 `MqKit.producer()` / `consumer()`） | `IllegalStateException` |
| 运行时故障（后端运行时错误，如真实后端连接失败） | `MqException` |

## 配置

| 键 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mq.type` | 否 | `memory` | 后端类型；指向未注册后端时启动立即失败 |
| `mq.<type>.*` | 后端自定 | — | 后端专有配置；**内存后端无额外键** |

`app-config.txt`：

```properties
# 使用默认内存后端时无需配置 mq.type
mq.type = memory
```

## 换后端 / 扩展后端

新增一种后端 = 在 `cn.hg.aifei.mq.<backend>` 包新增 `XxxMqProvider extends AbstractMqProvider`，并在
`MqProviderRegistry` 的静态初始化块加一行注册；装配逻辑无需改动。

## 生命周期

- `start()`：解析配置 → 创建总线 → 注册 `Producer` / `Consumer` 单例。重复启动不重复装配；配置有误即失败，不留半装配状态。
- `stop()`：注销单例（此后 `MqKit` 取实例即失败）→ 关闭总线。重复停止不重复关闭。

## 文档

- 设计契约（事实来源）：`docs/design.md`
- 开发约束：`AGENTS.md`
- 测试策略：`docs/testing.md`
