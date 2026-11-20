# aifei-trace

## 1. 定位与场景

一次请求跨 Handler → Service → 线程池 → 日志输出时，散落的日志行里看不到同一条链路。`aifei-trace` 补齐三件事，一件一个落点：

| 能力 | 落点 |
| --- | --- |
| **链路上下文**：当前线程持有 traceId / spanId，随取随用 | `TraceKit`（静态门面）+ `TraceContext` |
| **日志贯通**：任意日志工厂产出的日志自动携带 `[traceId] ` 前缀 | `TraceLog` + `TraceLogFactory` |
| **Web 入口**：请求进入绑定、请求结束清理，杜绝线程池串号 | `web.TraceHandler` |

**适合**：aifei 应用（Java 8+）内做请求级日志贯通、异步任务链路延续、跨服务 TraceId 透传提取。
**不适合**：采样率 / 上报 / 存储（本模块不做任何遥测后端对接）、MDC 注入、自动写入 MQ 消息头 / WebSocket 帧头 / Job 事件字段（跨模块传播由各消费模块自行承载，本模块零生态模块依赖）。

## 2. 5 分钟快速开始

加依赖（消费方自带 aifei 框架内核，模块不传递）：

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-trace</artifactId>
    <version>1.0.0</version>
</dependency>
```

两条装配主线，各一行，全部写在应用自己的 `AppConfig` 里：

```java
public class OrderAppConfig implements AifeiConfig<InputImpl, OutputImpl> {
    @Override
    public void config(Settings settings) {
        // 主线一：日志贯通——全应用所有日志自动带 "[traceId] " 前缀
        settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()));

        // 主线二：Web 入口——必须第一个 addHandler，才能覆盖全部下游 Handler
        settings.addHandler(new TraceHandler<InputImpl, OutputImpl>());
    }
}
```

> `Slf4jLogFactory` 等 aifei-log 工厂由消费方经 aifei-all 自带；本模块不绑定任何日志实现。
> 纯生成模式下每条请求生成新 TraceId；要从上游透传请看 §4 场景二。

跑通后即可观测：业务里正常打日志，控制台输出形如 `[8c02a1f4e6d3b7a9c1e5f203a4b6c8d0] 订单已创建`，同一请求内所有环节前缀一致；刷新页面（重发请求）TraceId 变化。

## 3. 常用用法

业务代码（Service / Handler 同线程内）直接取用：

```java
public class OrderService {
    public void placeOrder(String skuId) {
        // 取当前请求的 TraceId（未绑定时返回 null，不抛异常）
        String traceId = TraceKit.getTraceId();

        // 标记当前链路内的一个片段（再次调用覆盖旧值；不做 Span 树）
        String spanId = TraceKit.startSpan();

        // 日志自动携带前缀——这一行经 TraceLog 装饰后输出 "[traceId] 下单成功"
        log.info("下单成功, skuId=" + skuId);
    }
}
```

Web 入口由 `TraceHandler` 自动完成绑定与清理，请求结束（无论成功还是抛异常）链路上下文都会被清理，业务无需手动 `clear()`。

## 4. 深入使用

### 场景一：下单后异步履约（线程池手动传递）

业务问题：下单接口同步返回，履约任务提交线程池异步执行，履约日志要与下单请求归到同一条链路。

```java
// 发送端：提交前取出 TraceId，随任务闭包传递
public class OrderService {
    private final ExecutorService fulfillmentPool = ...;

    public void placeOrder(final String orderId) {
        final String traceId = TraceKit.getTraceId();
        fulfillmentPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    TraceKit.init(traceId);   // 子线程恢复链路
                    log.info("开始履约, orderId=" + orderId);   // 带 [traceId] 前缀
                    // ...履约逻辑...
                } finally {
                    TraceKit.clear();          // 任务结束必须清理，防线程复用串号
                }
            }
        });
    }
}
```

链路延续还是新链路，语义由这段代码显式表达——这是刻意的：模块不自动跨线程传播（见 §6 注意事项第一条）。

### 场景二：跨服务透传（提取上游 X-Trace-Id）

业务问题：上游服务（如网关）已生成 TraceId 放在 `X-Trace-Id` 请求头里，本服务提取后沿用同一 TraceId，日志可在多服务间串联检索。

框架的 `Input` 公开接口只有参数读取能力，没有 header 访问——需要业务侧自定义 Input 实现暴露 header：

```java
// 业务自定义 Input（摘录）：在框架 Input 之上增加 header 读取
public class OrderRequestInput implements Input {
    // ...实现 Input 全部方法...

    public String getHeader(String name) {
        // 从底层 HTTP 请求读取 header（业务自行实现）
        return headerOf(name);
    }
}
```

```java
// 装配：构造提取模式的 TraceHandler
public class OrderAppConfig implements AifeiConfig<OrderRequestInput, OutputImpl> {
    @Override
    public void config(Settings settings) {
        settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()));
        settings.addHandler(new TraceHandler<OrderRequestInput, OutputImpl>(
                new TraceIdExtractor<OrderRequestInput>() {
                    @Override
                    public String extract(OrderRequestInput input) {
                        return input.getHeader("X-Trace-Id");
                    }
                }));
    }
}
```

行为约定（结论）：提取值为 null 或空白时回退生成新 TraceId；提取过程抛任何异常也被吞掉并回退生成——trace 失败不会影响业务请求。异常不会从这个处理器抛出，只有下游业务异常原样向上传播（finally 清理照常执行）。

### 场景三：MQ / WS / Job 的链路延续（由消费模块承载）

跨模块传播 v1 不做。当 aifei-mq / aifei-websocket / aifei-job 在各自协议结构中增设 traceId 透传字段后：发送端写 `TraceKit.getTraceId()` 的值，消费端取出后 `TraceKit.init(值)`、处理完 finally `TraceKit.clear()`——用本模块既有 API 即可，无需新增依赖或 API。

## 5. 高级使用

### 替换 TraceId 生成器

默认生成器产出 UUID 派生的 32 位小写十六进制串。若业务已有一套全局 ID 服务（如统一发号器），可实现 `TraceIdGenerator` 替换：

```java
public class OrderCenterIdGenerator implements TraceIdGenerator {
    @Override
    public String generate() {
        return OrderCenter.nextTraceId();   // 业务自有发号逻辑（需满足接口格式契约）
    }
}

// 启动期装配（AppConfig 内）：
TraceKit.setTraceIdGenerator(new OrderCenterIdGenerator());

// 需要恢复默认时传入单例：
TraceKit.setTraceIdGenerator(DefaultTraceIdGenerator.INSTANCE);
```

替换实现需满足格式契约（非空、仅 `[0-9a-f]`、固定 32 位小写十六进制），模块不校验产出——格式违例的后果由实现方承担。**建议仅在启动期替换**（运行期替换无锁、不保证原子可见性）。

## 6. 注意事项与技术难点

- **不自动跨线程传播（刻意设计）**：链路上下文用普通 ThreadLocal 而非可继承 ThreadLocal——线程池复用线程会让上一个任务残留的 TraceId 串到下一个任务。线程池 / 异步场景必须手动传递（§4 场景一），任务结束必须 `clear()`。
- **清理责任**：Web 请求由 `TraceHandler` 在 finally 中无条件清理；手动 `init` 的场景（异步任务、消费端）由业务自行 try/finally 清理。
- **TraceId 缺失时日志原样透出**：不拼空前缀、不报错、不打警告，高频路径零开销。前缀在打印时刻读取当前线程的链路上下文——同一个 Log 实例跨线程使用时，各线程各自取值（装饰器无状态）。
- **`Input` 无 header 能力**：框架 `Input` 公开接口只有参数读取；提取请求头必须业务自定义 Input 实现（§4 场景二）。本模块不提供「万能提取器」。
- **静态资源天然无 TraceId**：静态资源请求不进 Handler 链，无需在 trace 侧排除。
- **`TraceKit` 无装配生命周期**：不做任何配置直接调用即成立（`getTraceId()` 返回 null、`init()` 直接生成），永不抛「未装配」类异常——与需要先装配再使用的门面形态不同。
- **spanId 单层**：`startSpan()` 再次调用覆盖旧值，不做 Span 树；不影响 traceId。
- **TraceHandler 链上位置**：必须第一个 `addHandler`，否则只覆盖到链上其后的 Handler。
- **零第三方依赖**：模块只依赖 aifei 内核（provided）与 junit（test）；日志适配器（Slf4j / Log4j 工厂）由消费方自带，本模块不引入任何日志绑定。
- **替换生成器产出不校验**：格式契约由接口 javadoc 约定，模块按原值绑定不拦截。

## 7. 参考

- **行为契约与异常语义**（init 回退、提取异常吞并回退、finally 清理、`IllegalArgumentException` 消息逐字表）：`docs/design.md` §7——契约细节以设计文档为准，本文只给结论。
- **TraceHandler 执行序与链上语义**：`docs/design.md` §5.7 / §8.1。
- **架构门禁（10 例）与公共面收敛纪律**：`docs/design.md` §10；开发代理约束见本模块 `AGENTS.md`。
- **坐标与依赖口径**：`docs/design.md` §11（`io.github.gollyhu:aifei-trace:1.0.0`，aifei-all provided）。
- **示例工程**：`aifei-trace-demo/`（平级目录、端口 18130、离线可跑），演示当前请求 TraceId 实时展示、日志前缀贯通、线程池手动传递、上游透传提取 4 个场景。
- **常见问题**：日志没有前缀？——当前线程没有绑定值（检查是否经过 `TraceHandler` 或手动 `init`）；异步任务日志串了别的请求？——任务结束忘了 `clear()` 或未走手动传递；提取不到上游 TraceId？——`Input` 无 header 能力，确认已自定义 Input 并正确实现 `getHeader`。
