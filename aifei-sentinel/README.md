# aifei-sentinel

aifei-sentinel 是为 [aifei](https://aifei.cn) 提供的极简流量治理组件，把 Sentinel 的流控、熔断与系统保护能力接入 aifei。业务代码只注册拦截器、实现降级策略，不出现任何 Sentinel 类型。

- `provided` 依赖仅 `aifei-all`，对 arch / undertow 零依赖。
- 通过 `routes.scan(...)` 注册拦截器，在 `onStart()` 中加载规则。
- 五种治理能力共用同一处埋点，差异只在规则类型。
- 由是否配置 Dashboard 地址切换两种互斥运行模式，切换模式不需要修改业务代码。
- 不引入 Spring：官方的 `@SentinelResource` 注解依赖 Spring AOP，本 SDK 改用 aifei 拦截器表达等价的横切。

两个基础概念：**资源（Resource）** 是被保护代码块的名字，本 SDK 默认取「全限定类名#方法名」；**规则（Rule）** 描述「某个资源怎么限」。埋点及 `entry` / `entry.exit` 的成对调用已封装在拦截器内，业务不需要手写。

## 快速接入

### 第 1 步：加依赖

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-sentinel</artifactId>
    <version>1.0.0</version>
</dependency>
```

`aifei-all` 是 `provided` 依赖，由消费方应用提供；`sentinel-core`、`sentinel-parameter-flow-control` 与 `sentinel-datasource-extension` 随 SDK 传递。

### 第 2 步：注册拦截器

```java
public void config(Routes routes) {
    routes.scan("cn.demo",
            // 限流拦截器放最前：被拦下的请求不再进入后续鉴权，省开销
            new SentinelInterceptor(e -> Out.fail(OutCode.RATE_LIMIT)),
            new AuthInterceptor());
}
```

`e -> Out.fail(OutCode.RATE_LIMIT)` 是消费方的降级接线：被限流时返回项目自己的失败对象。返回值经 `inv.setReturnValue` 走消费方 IoHandler 正常渲染，因此 **HTTP 状态码始终是 200**，限流语义由业务码承载。

### 第 3 步：启动时加载规则

`SentinelKit.loadRules` 必须放在 `onStart()` 中最靠前的位置（不得晚于任何业务埋点）：

```java
public void onStart() {
    SentinelKit.loadRules(PropKit.use("app-config.txt"));
}
```

再按所选模式二选一配置：

**① 本地模式（默认）**

前提：无额外依赖；不配置 `sentinel.dashboard.server` 即进入本地模式，规则文件是唯一来源。

```properties
sentinel.enable = true
sentinel.rules.file = sentinel-rules.json
```

之后往 `sentinel-rules.json` 写规则即可，改文件秒级热生效；规则文件位于 classpath 资源中时按一次性加载，不做变更监控。

**② Dashboard 模式**

前提一，消费方引入 transport 依赖（SDK 不打包它）：

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-transport-simple-http</artifactId>
    <version>1.8.8</version>
</dependency>
```

前提二，以下两项必须同时配置，缺一即 `loadRules` 抛 `IllegalStateException`：

```properties
sentinel.dashboard.server = 127.0.0.1:8858
sentinel.app.name = my-app
```

该模式不启文件热读（避免回环），规则由控制台推送并自动回写本地 `sentinel-rules.json`，重启不丢。两种模式的差别见「运行模式」。

### 一次请求的完整链路

1. 请求打到 `VipService#index`。
2. `SentinelInterceptor` 拦截，调 `SphU.entry("cn.demo.VipService#index")`。
3. Sentinel 查规则：该资源 QPS 100，当前是第 101 个 → 抛 `FlowException`。
4. 拦截器捕获，走降级接线返回 `Out.fail(OutCode.RATE_LIMIT)`，业务方法不再执行。

## 治理能力

### 流量控制

限制资源每秒请求数或并发线程数，超出阈值的请求被直接拒绝。适用于保护核心接口不被突发流量打垮。

```json
{
  "resource": "cn.demo.VipService#index",
  "grade": 1,           // 1 = 按 QPS（每秒请求数）；0 = 按并发线程数
  "count": 100.0,       // 阈值：每秒最多 100 个请求
  "strategy": 0,        // 0 = 直接限流；1 = 关联；2 = 链路
  "controlBehavior": 0  // 0 = 直接拒绝；1 = 排队等待；2 = 预热
}
```

按并发线程数（`grade: 0`）适合「每个请求独占一个线程且耗时较长」的接口。

### 熔断降级

资源平均响应时间过长、异常比例或异常数超过阈值时，暂停该资源的调用一段时间，之后放行试探请求观察是否恢复。适用于防止下游故障拖垮整个应用。

```json
{
  "resource": "cn.demo.VipService#index",
  "grade": 0,          // 0 = 平均响应时间（RT）；1 = 异常比例；2 = 异常数
  "count": 500.0,      // 阈值：500ms
  "timeWindow": 10     // 熔断时长（秒）
}
```

与限流的区别：限流防「流量太大」，熔断防「方法本身坏了还一直被打」。

### 热点参数限流

对资源的某个参数值单独限流，例如给频繁访问的某个 id 设置独立阈值。适用于爆款商品、热点账户等场景。

```json
{
  "resource": "cn.demo.VipService#detail",
  "grade": 1,
  "count": 50.0,        // 热点参数阈值
  "paramIdx": 0,        // 以第 0 个参数作为统计维度
  "controlBehavior": 0
}
```

### 系统保护

不针对单个资源，而按整台机器的负载、平均响应时间、并发线程数和整体 QPS 兜底限流。适用于作为其他规则之外的最后一道防线。

```json
{
  "highestSystemLoad": 4.0,   // 系统最高负载
  "avgRt": 100,               // 平均响应时间上限（ms）
  "maxThread": 200,           // 最大并发线程数
  "qps": 500                  // 整体 QPS 上限
}
```

### 黑白名单

按调用来源（origin）决定资源是否放行。适用于只允许特定来源访问的接口。该规则依赖调用来源，需配合 `ContextUtil.enter(name, origin)` 使用。

```json
{
  "resource": "cn.demo.VipService#admin",
  "strategy": 0,         // 0 = 白名单（只有 limitApp 能进）；1 = 黑名单
  "limitApp": "default"  // 来源标识
}
```

## 运行模式

```text
本地模式                                Dashboard 模式
规则文件 --热读--> 内存规则 --> 生效    规则文件 --一次性加载--> 内存规则 --> 生效
                                        Dashboard --推送--> 内存规则
                                                          └--回写--> 规则文件
```

两种模式互斥，由是否配置 `sentinel.dashboard.server` 决定。本地模式中规则文件是唯一来源，改文件即热生效；Dashboard 模式中规则由控制台推送，规则文件是自动回写的持久化镜像。同一份规则 JSON 在两种模式下结构一致，切换模式不需要修改业务代码。

Dashboard 模式不启动文件热读：否则推送、回写、热读会互相触发形成回环。多实例各自连接同一个 Dashboard，各自回写本地规则文件，不需要共享文件系统。

transport 依赖只在 Dashboard 模式需要，由消费方引入（见「快速接入」第 3 步）。SDK 不 import 任何 transport 类型，只在运行时反射探测其是否存在，因此不添加该依赖不会触发 `NoClassDefFoundError`。

## 拦截器 API

```java
public class SentinelInterceptor implements Interceptor {

    public SentinelInterceptor(SentinelBlockHandler blockHandler);

    public void intercept(Invocation inv) throws Exception;

    protected String resourceOf(Invocation inv);
}
```

```java
@FunctionalInterface
public interface SentinelBlockHandler {

    Object handle(BlockException e);
}
```

`SentinelInterceptor` 是 SDK 中唯一引用 `cn.aifei.aop.*` 的类；`SentinelBlockHandler` 位于 `spi` 包，不依赖 aifei 内核类型。

进入 `handle` 的 `BlockException` 按命中原因分为若干子类，需要区分处理时可按类型分支：

| 子类 | 触发场景 |
|---|---|
| `FlowException` | 流量控制命中 |
| `DegradeException` | 熔断降级命中 |
| `SystemBlockException` | 系统保护命中 |
| `ParamFlowException` | 热点参数限流命中 |
| `AuthorityException` | 黑白名单命中 |

### 资源名

默认资源名为「全限定类名#方法名」，例如 `cn.demo.VipService#index`。规则 JSON 中的 `resource` 必须与此严格一致，否则限流不会生效。需要按 HTTP path 维度限流时，继承 `SentinelInterceptor` 覆盖 `resourceOf`。

## 规则文件

规则文件是一份「单文件 + 分 key」的 JSON，五类规则各占一个 key：

```json
{
  "flowRules": [...],
  "degradeRules": [...],
  "systemRules": [...],
  "paramFlowRules": [...],
  "authorityRules": [...]
}
```

缺失的 key 按空规则处理，不影响已加载的其他类型规则；单条规则的字段含义见「治理能力」。

本地模式下只对物理文件启用热读（`FileRefreshableDataSource`），刷新间隔由 `sentinel.rules.refreshMs` 决定；规则文件位于 classpath 资源中时按一次性加载，不做变更监控。Dashboard 模式的回写采用合并写入：先读现有 JSON 对象，逐 key 覆盖后原子落盘，其余 key 保留，避免单类规则覆盖整份文件。

## 配置与注入

在 `app-config-{env}.txt` 中配置，键统一使用 `sentinel.` 前缀：

| 键 | 语义 | 默认 |
|---|---|---|
| `sentinel.enable` | 总开关，`false` 时跳过规则加载 | `true` |
| `sentinel.rules.file` | 规则 JSON 文件路径（相对 webroot 或 classpath） | `sentinel-rules.json` |
| `sentinel.rules.refreshMs` | 规则文件热生效刷新间隔（毫秒），仅本地模式且规则文件为物理文件时生效 | `3000` |
| `sentinel.dashboard.server` | Dashboard 地址 `host:port`，有值即进入 Dashboard 模式 | 空（本地模式） |
| `sentinel.app.name` | 应用名，Dashboard 模式必填 | — |
| `sentinel.rules.writable` | 回写开关，`false` 时关闭回写 | 非 `false` 即开启 |

Dashboard 地址与应用名通过系统属性注入 Sentinel（`csp.sentinel.dashboard.server` / `project.name`），因此 `loadRules` 必须早于 transport 初始化（transport 在首次埋点时初始化），即不得晚于任何业务埋点。

配置了 Dashboard 地址却缺少 `sentinel.app.name`，或未引入 `sentinel-transport-simple-http`，`loadRules` 都会直接抛出 `IllegalStateException`，避免「看似连上、实则未连」的隐性故障。

`SentinelKit.loadRules` 只加载一次，重复调用会被忽略；`SentinelKit.shutdown()` 关闭规则数据源与回写，释放资源，应用关闭时调用。除 Dashboard 模式的两类装配错误外，规则文件缺失或读取失败抛出 `IllegalStateException`，规则 JSON 解析失败与回写失败也会按原始类型向调用方传播。

## 常见问题

| 问题 | 解答 |
|---|---|
| 限流没生效？ | 先核对规则里的 `resource` 和「全限定类名#方法名」是否一字不差 |
| 改了 JSON 规则没反应？ | 本地模式改文件秒级热生效（`sentinel.rules.refreshMs`，默认 3s）；Dashboard 模式下规则以控制台为准，手改文件会被回写覆盖 |
| 配了 Dashboard 启动报错？ | 两种可能：未引入 `sentinel-transport-simple-http`，或漏配 `sentinel.app.name`；二者 SDK 均直接报错 |
| Dashboard 模式下重启会丢规则吗？ | 不会，SDK 自动回写本地 `sentinel-rules.json`，重启即恢复 |
| `sentinel.enable = false` 会发生什么？ | 拦截器空转、不加载规则，等于没装 |
| 拦截器放什么位置？ | 放 `routes.scan` 拦截器数组最前，被限流的请求不浪费后续鉴权开销 |
| 能不能按 URL 而不是方法名限流？ | 能，继承 `SentinelInterceptor` 覆盖 `resourceOf` 返回 path |
| 会不会有性能开销？ | 命中即拒绝的场景开销极小（纳秒级），远低于业务执行本身 |

## 测试与发布

普通单元测试不启动 HTTP、不连接 Dashboard：

```shell
mvn test
```

测试包含四条架构门禁（不引用 arch / undertow / sql、`cn.aifei.aop.*` 与 `cn.aifei.core.*` 只出现在接入层包、不硬引用 transport 类），以及拦截器放行与降级、规则文件热生效、两种模式分流与回写等行为测试。发布前使用 `mvn clean verify`，在打包后检查实际 JAR、源码与 Java 8 字节码。

## 本地跑 demo

`aifei-sentinel-demo` 是自写 HIO 的最小示例，脚本均在该目录下：

- 本地模式：`start-demo.bat` → 访问 http://localhost:18080/demo；改该目录下的 `sentinel-rules.json` 秒级热生效。
- Dashboard 模式：先 `start-dashboard.bat` 起本地控制台（http://localhost:8858，账号 sentinel/sentinel），再 `start-demo-dashboard.bat`（控制台未起时该脚本会自动拉起并等待就绪）。
- 停止：`stop-demo.bat`（或在运行窗口按 Ctrl+C）。

## 相关文档

- 设计目标、行为契约与关键决策：[设计文档](docs/design.md)
- 测试策略与用例清单：[测试文档](docs/testing.md)
