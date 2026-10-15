# aifei-sentinel 设计文档

> 版本：1.0.0 ｜ 关联：`README.md`（定位与用法）、`docs/testing.md`（测试）

## 1. 背景与目标

aifei 生态已有缓存（`aifei-cache`）、鉴权（`aifei-sa-token`）等社区组件，缺流量治理能力。`aifei-sentinel` 以「L1 接线」方式把阿里开源的 Sentinel 接入 aifei，提供五种能力：

- 流量控制（QPS / 并发线程数）
- 熔断降级（平均响应时间 / 异常比例 / 异常数）
- 热点参数限流
- 系统自适应保护
- 授权（黑白名单）

设计目标：

- `provided` 依赖仅 `aifei-all`，对 arch / undertow 零依赖。
- 业务代码零 Sentinel 类型，只注册拦截器、实现降级策略。
- 五种能力共用同一处埋点，差异只在规则类型。

## 2. 选型与技术决策

| 决策 | 结论 | 理由 |
|---|---|---|
| 引入方式 | 引入 `sentinel-core`（compile），L1 接线 | 流控与熔断算法成熟但滑动窗口/令牌桶边界易错，不宜自研；`sentinel-core` 是纯 Java，可手工装配 |
| 埋点方式 | aifei 拦截器 + 编程式 `SphU.entry` | `@SentinelResource` 依赖 Spring AOP，aifei 不支持 Spring |
| 禁用项 | `sentinel-annotation-aspectj`、`spring-cloud-starter-alibaba-sentinel` | 同上，Spring 生态 |
| 规则管理 | 对接官方 Sentinel Dashboard，不自研控制台 | 规则管理、推送、监控由官方 Dashboard 承担，自研 WebUI + CRUD 的投入产出比不划算 |
| 生命周期 | 静态门面 `SentinelKit`，在 `onStart()` 装配 | 不需要插件机制，静态 init 即可嵌入 aifei 启动流程 |

## 3. 总体架构

两个平级的独立 Maven 项目，各自自包含 `pom.xml`（无聚合父 pom）：

| 项目 | 定位 |
|---|---|
| `aifei-sentinel` | SDK 本体，坐标 `io.github.gollyhu:aifei-sentinel:1.0.0` |
| `aifei-sentinel-demo` | 自写 HIO 的最小接入示例，演示两种运行模式 |

各项目在自身 `<properties>` 内统一版本（`aifei.version` / `sentinel.version` / `junit.version`），demo 通过坐标 `io.github.gollyhu:aifei-sentinel` 依赖 SDK。

### 3.1 SDK 内部分层

```
aifei-sentinel/src/main/java/cn/hg/aifei/sentinel/
├── core/    SentinelKit、SentinelRuleLoader、SentinelRuleWriter  ← 零内核依赖
├── spi/     SentinelBlockHandler                                 ← 零内核依赖
└── aifei/   SentinelInterceptor                                  ← 唯一 import cn.aifei.aop.* 的接入层
```

依赖方向单向：`aifei → spi → core`；`core` 不反向依赖上层。

### 3.2 架构约束

由 `KernelBindingTest` 门禁强制（见 `docs/testing.md`）：

- `cn.aifei.aop.*` / `cn.aifei.core.*` 只允许出现在 `cn.hg.aifei.sentinel.aifei` 包。
- `cn.aifei.vip.*`、`io.undertow.*`、`java.sql.*` 零引用。
- `sentinel-transport-*` 零硬引用，只在运行时反射探测。
- 业务代码零 Sentinel 类型（`Entry` / `FlowRule` / `BlockException` 不出 SDK 门面）。
- 项目代码、依赖与 API 兼容 Java 8。

### 3.3 依赖设计

| 依赖 | scope | 说明 |
|---|---|---|
| `cn.aifei:aifei-all` | `provided` | 内核，消费方自带（含传递的 `fastjson2`） |
| `com.alibaba.csp:sentinel-core` | `compile` | Sentinel 核心：埋点、规则管理器、降级 |
| `com.alibaba.csp:sentinel-parameter-flow-control` | `compile` | 热点参数限流（独立于 core 的扩展模块） |
| `com.alibaba.csp:sentinel-datasource-extension` | `compile` | `FileRefreshableDataSource`，规则文件热读 |
| `com.alibaba.csp:sentinel-transport-simple-http` | `provided` + `optional` | 仅 Dashboard 模式需要，由消费方提供；SDK 零 import |
| `junit:junit` | `test` | 单元测试 |

`sentinel-transport-simple-http` 用 `provided + optional` 的双重含义：`provided` 对齐 aifei 惯例（不打包进 SDK、由消费方提供），`optional` 阻断传递，避免消费方被动引入。

### 3.4 demo HIO

`aifei-sentinel-demo` 不依赖 `aifei-vip-arch`：其 HIO 逐字同步自官方仓库 `jfinal/aifei-eco` 的 `aifei-demo/src/main/java/cn/aifei/demo/common`（保留官方包名 `cn.aifei.demo.common.arch` 与版权块），配一个 `@Path("/demo")` 演示 Service，用于跑通并验证拦截器与两种运行模式。依赖 `aifei-all`（内核）+ `undertow-core` + SDK 本体 + `fastjson2`（JSON 序列化）。

## 4. 核心组件

### 4.1 SPI：`SentinelBlockHandler`

解耦「SDK 不知道消费方 `Out` 类型」这一矛盾：

```java
package cn.hg.aifei.sentinel.spi;

import com.alibaba.csp.sentinel.slots.block.BlockException;

/** 被限流/熔断/系统保护命中时的降级策略：返回值即 action 的返回值（消费方返回自己的 Out.fail 或抛异常） */
@FunctionalInterface
public interface SentinelBlockHandler {
    Object handle(BlockException e);
}
```

### 4.2 接入层：`SentinelInterceptor`

```java
public class SentinelInterceptor implements Interceptor {

    private final SentinelBlockHandler blockHandler;

    public SentinelInterceptor(SentinelBlockHandler blockHandler) {
        this.blockHandler = blockHandler;
    }

    @Override
    public void intercept(Invocation inv) throws Exception {
        Entry entry = null;
        try {
            entry = SphU.entry(resourceOf(inv));
            inv.invoke();
        } catch (BlockException e) {
            inv.setReturnValue(blockHandler.handle(e));   // 复用消费方 IoHandler 的输出渲染
        } finally {
            if (entry != null) {
                entry.exit();                              // 必须成对退出，否则统计计数器泄漏
            }
        }
    }

    /** 资源名：默认「全限定类名#方法名」，规则按此维度配置；需按 path 维度时覆盖此方法 */
    protected String resourceOf(Invocation inv) {
        return inv.getMethod().getDeclaringClass().getName() + "#" + inv.getMethodName();
    }
}
```

要点：埋点只写一次，流控/熔断/热点参数/系统保护/黑白名单共用同一 `SphU.entry`，差异只在规则类型。

### 4.3 规则加载：`SentinelRuleLoader`

```java
public final class SentinelRuleLoader {

    public static void load(Path file);                        // 物理文件一次性加载
    public static void load(ClassLoader cl, String resource);  // classpath 资源一次性加载
    public static void load(String json);                      // 解析 JSON 并加载五类规则
    public static List<FileRefreshableDataSource<?>> watch(Path file, int refreshMs);  // 文件热生效
}
```

- 规则 JSON 按 key 分发到 `FlowRuleManager` / `DegradeRuleManager` / `SystemRuleManager` / `ParamFlowRuleManager` / `AuthorityRuleManager`；缺失的 key 按空规则处理。
- `watch` 对五类规则各建一个 `FileRefreshableDataSource` 并 `register2Property`，返回的数据源须在应用关闭时 `close`。
- 数据源用**五参构造**显式指定刷新间隔（`converter, refreshMs, bufSize, charset`），避免三参构造的 `int` 被误当作 `bufSize`。

### 4.4 回写：`SentinelRuleWriter`（Dashboard 模式专用）

```java
final class SentinelRuleWriter implements Closeable {

    /** 用可回写的 property 替换五类规则的 property，返回写入器供 shutdown 关闭 */
    static Closeable register(Path file) {
        SentinelRuleWriter writer = new SentinelRuleWriter(file);
        FlowRuleManager.register2Property(writer.property("flowRules"));
        DegradeRuleManager.register2Property(writer.property("degradeRules"));
        SystemRuleManager.register2Property(writer.property("systemRules"));
        ParamFlowRuleManager.register2Property(writer.property("paramFlowRules"));
        AuthorityRuleManager.register2Property(writer.property("authorityRules"));
        return writer;
    }
}
```

- 自定义 `DynamicSentinelProperty` 的 `updateValue` 先走默认通知（更新内存），再合并写回文件：**任何来源**的变更（Dashboard 推送 / `loadRules`）都经由该 property，故无需 hook transport 的 command handler。
- **合并写入**：规则文件是「单文件 + 分 key」结构，须先读现有 JSON 对象 → 逐 key 覆盖 → 其余 key 保留 → 原子落盘（临时文件 + `ATOMIC_MOVE`，不支持时退化为 `REPLACE_EXISTING`）。直接用 `FileWritableDataSource` 会把单类规则整值覆盖成裸数组，破坏结构、与读侧不兼容。
- 仅依赖 `sentinel-core`，与 transport 无关——transport 只负责「Dashboard 能否推送进来」。

### 4.5 门面：`SentinelKit`

```java
public final class SentinelKit {

    /** 消费方唯一装配入口：按是否配置 Dashboard 地址分流两种模式；只加载一次，重复调用忽略 */
    public static void loadRules(Prop prop);

    /** 关闭热读数据源与回写，释放资源；应用关闭时调用 */
    public static void shutdown();
}
```

## 5. 接口契约

### 5.1 资源名（埋点维度）

默认「全限定类名#方法名」，如 `cn.demo.VipService#index`。规则 JSON 中的 `resource` 必须与此严格对齐，否则限流不生效；需按 HTTP path 维度时子类覆盖 `resourceOf`。

### 5.2 规则 JSON

```json
{
  "flowRules": [
    {"resource": "cn.demo.VipService#index", "grade": 1, "count": 100.0, "strategy": 0, "controlBehavior": 0}
  ],
  "degradeRules": [
    {"resource": "cn.demo.VipService#index", "grade": 0, "count": 500.0, "timeWindow": 10}
  ],
  "systemRules": [
    {"highestSystemLoad": 4.0, "avgRt": 100, "maxThread": 200, "qps": 500}
  ],
  "paramFlowRules": [
    {"resource": "cn.demo.VipService#detail", "grade": 1, "count": 50.0, "paramIdx": 0, "controlBehavior": 0}
  ],
  "authorityRules": [
    {"resource": "cn.demo.VipService#admin", "strategy": 0, "limitApp": "default"}
  ]
}
```

两种模式下该 JSON 结构完全一致：本地模式它是**规则来源**，Dashboard 模式它是**持久化镜像**（由回写产生）。切换模式不需要修改业务代码。

### 5.3 降级策略

被限流/熔断命中时调用 `SentinelBlockHandler.handle(BlockException)`，返回值经 `inv.setReturnValue` 走消费方 IoHandler 正常渲染（**HTTP 恒 200**，业务码承载语义）。消费方典型接线：

```java
new SentinelInterceptor(e -> Out.fail(OutCode.RATE_LIMIT))
```

## 6. 运行模式（两种，互斥）

由「是否配置 `sentinel.dashboard.server`」自动切换：

| | 本地模式 | Dashboard 模式 |
|---|---|---|
| 触发条件 | 未配 `sentinel.dashboard.server` | 配了 `sentinel.dashboard.server` |
| 文件 → 内存 | ✓ `watch()` 定时热读 | ✗ 启动只 `load()` 一次 |
| 内存 → 文件 | ✗ 不回写 | ✓ 自定义 property 回写 |
| 规则来源（运行时） | 规则文件 | Dashboard 推送 |
| 规则文件角色 | source of truth | 持久化镜像 |
| 适用 | 单机 / 独立实例 | 单机或集群（多实例各自连同一 Dashboard） |
| 需要 transport | ✗ | ✓（消费方引入） |

**互斥的必要性**：Dashboard 模式若同时开热读与回写会形成回路——推送 → 内存变 → 回写文件 → 文件变 → 热读触发 `loadRules` → 内存变 → 再回写……故两种方向必须择一。

### 6.1 本地模式（默认）

```
sentinel-rules.json ──watch(FileRefreshableDataSource, refreshMs)──> 内存规则 ──> 流控生效
```

改文件即生效，规则文件是唯一来源，零新增依赖。规则文件仅在为**物理文件**时启用热读；位于 classpath 资源中时按一次性加载，不做变更监控。

### 6.2 Dashboard 模式

```
启动时：sentinel-rules.json ──load() 一次──> 内存规则（初始）
                                   ↑
Dashboard ──推送──> 内存规则 ──> 流控生效
                        │
                        └──自定义 SentinelProperty──> 合并写回 ──> sentinel-rules.json（持久化镜像）
```

多实例形态：

```
            [Sentinel Dashboard]
             │ 推送规则（统一）
   ┌─────────┼─────────┐
   ▼         ▼         ▼
实例 A     实例 B     实例 C      各自回写自己的本地 sentinel-rules.json
（无需共享盘，靠 Dashboard 推送保证一致）
```

### 6.3 transport 依赖策略

| 项 | 约定 |
|---|---|
| scope | `provided` + `optional`（对齐 aifei 惯例 + 阻断传递） |
| 引入方 | 消费方应用（需要 Dashboard 时） |
| SDK 代码 | **零 import** transport 类，仅反射探测标记类 `TransportConfig` |
| 装配错误 | 配了 Dashboard 但探测不到 transport → 抛 `IllegalStateException`（fail-fast） |

模式切换的判断依据是「**是否配置了 Dashboard 地址**」，不是「transport 在不在 classpath」。

## 7. 配置项与注入时序

`app-config-{env}.txt`（消费方项目），键统一 `sentinel.` 前缀：

| 键 | 语义 | 默认 |
|---|---|---|
| `sentinel.enable` | 总开关，`false` 时跳过规则加载、拦截器空转 | `true` |
| `sentinel.rules.file` | 规则 JSON 文件路径（相对 webroot 或 classpath） | `sentinel-rules.json` |
| `sentinel.rules.refreshMs` | 规则文件热生效刷新间隔（毫秒），仅**本地模式**且规则文件为物理文件时生效 | `3000` |
| `sentinel.dashboard.server` | Dashboard 地址 `host:port`；**有值即进入 Dashboard 模式** | 空（本地模式） |
| `sentinel.app.name` | 应用名（Dashboard 机器列表显示用）；**Dashboard 模式必填，缺失即报错** | — |
| `sentinel.rules.writable` | 回写开关，`false` 时关闭回写 | 非 `false` 即开启 |

**注入时序（实现约束）**：Sentinel 原生通过 JVM 参数读取 Dashboard 地址（`csp.sentinel.dashboard.server` / `project.name`）。本 SDK 改为从 `app-config.txt` 读取后用 `System.setProperty` 注入，**该注入必须早于 transport 初始化**（transport 在首次 `SphU.entry` 时初始化）。因此消费方须在 `onStart()` 中调用 `SentinelKit.loadRules(...)`，且不得晚于任何业务埋点。

Dashboard 模式下的启动顺序为：注入属性 → 注册回写（替换 property 会清空内存）→ 一次性加载初始规则（恢复内存并持久化）。

## 8. 风险与规避

| 风险 | 规避 |
|---|---|
| `entry` / `exit` 不配对 → 计数器泄漏 | 拦截器 `finally` 强制 `exit` + 测试断言 |
| 规则 `resource` 与埋点资源名错位 → 限流失效 | 资源名统一走「全限定类名#方法名」，规则示例同源 |
| Sentinel 类型渗透业务代码 | 门面收敛 + `KernelBindingTest` 把关 |
| 回环震荡：同时开热读与回写互相触发 | 两种模式互斥，Dashboard 模式不注册热读数据源 |
| 注入时序晚于 transport 初始化 → 连不上 Dashboard | 文档强制 `onStart()` 中最早调用 `loadRules` |
| 配了 Dashboard 却未引 transport，或缺 `sentinel.app.name` | SDK 零硬引用 + 运行时反射探测；两种装配错误均 fail-fast，杜绝「看似连上、实则未连」 |
| 官方 Dashboard 默认只推内存、不落盘 | 由 SDK 侧回写机制补上持久化，不依赖改 Dashboard 源码 |
| 回写并发与半截文件 | 写操作串行化（锁）+ 临时文件原子落盘 |
| 规则文件是 classpath 资源 | 不启热读、不回写，按一次性加载 |
