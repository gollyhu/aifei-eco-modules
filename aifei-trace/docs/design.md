# aifei-trace 设计文档

本文是 `aifei-trace` 的**设计事实来源（契约）**：公共 API 逐字签名、包结构与门禁、行为契约、异常语义、任务分解与验收标准以本文为准。面向使用者的用法见 `README.md`（编码角色实现后写）；面向开发代理的约束见模块 `AGENTS.md`；测试策略与用例清单见 `docs/testing.md`（由编码角色在实现后编写，本文只定契约）。

> **交付性质**：`aifei-trace` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**。工程内**没有** `AppConfig` / `Launcher` / `service/` 包 / `webapp/` / HTTP 路由 / HIO / 表结构 / Db+Row / Generator / UI 页面；只有「根包公共 API 精确 6 类型 + `web` 接入包精确 2 类型」，**无 Plugin、无配置键、无 internal 包**。
>
> **坐标**：`io.github.gollyhu:aifei-trace:1.0.0`（`packaging=jar`）；包名 `cn.hg.aifei.trace`；**Java 8**（语法与 API 双约束，主 JAR class major = 52）；**零第三方**（pom 仅 `cn.aifei:aifei-all` provided + junit test）。
>
> **文档状态**：本文是 1.0.0 的**设计定稿，已按本文实现交付**（模块 + `aifei-trace-demo` 已落地并过 `mvn clean verify`，§12 事项 1 已完成根 `AGENTS.md` 登记）。§5 的 8 个公共类型签名仍是公共面的**唯一依据**，变更前必须先改本文。
>
> **底稿**：官方 `cn.aifei:aifei-trace:1.1.1` 已解包核实（8 个公共类型源码逐字精读）。本模块**参考其语义重写**（包名改为 `cn.hg.aifei.trace`、版权头换为仓库统一版权、异常口径有显式偏离），**不逐字复制官方源文件**，故全部文件按仓库「原创文件」口径处理。

---

## 1. 模块定位与边界

### 1.1 定位

aifei 生态缺少**请求链路贯穿能力**：一次请求跨 Handler → Service → 线程池 → 日志输出，日志里看不到同一条链路。本模块补齐三件事，一件一个落点：

| # | 目标 | 落点 |
| --- | --- | --- |
| 1 | **链路上下文**：当前线程持有 traceId / spanId，随取随用 | `TraceContext`（ThreadLocal 唯一持有点）+ `TraceKit`（全静态门面） |
| 2 | **日志贯通**：任意 LogFactory 产出的日志自动携带 `[traceId] ` 前缀 | `TraceLog`（Log 装饰器）+ `TraceLogFactory`（LogFactory 装饰器） |
| 3 | **Web 入口**：请求进入绑定、请求结束清理，杜绝线程池串号 | `web.TraceHandler`（extends `cn.aifei.core.Handler`，finally 保证 clear） |

与生态的关系：`aifei-mq`（消息头）/ `aifei-websocket`（帧头）/ `aifei-job`（事件字段）的**跨模块传播在 v1.0.0 范围之外**（§2 决策 8）；未来由各消费模块自行读取 `TraceKit.getTraceId()` 写入其协议字段，本模块**零生态模块依赖**。

### 1.2 为什么是「库/SDK」而非应用工程（不适用章节声明）

| 常规章节 | 结论 | 理由 |
| --- | --- | --- |
| 表结构草案 | **不适用** | 零 DB、零表、零 `Db+Row`、零 `Generator`；门禁反向断言全模块无 `import java.sql.` |
| Service 方法清单 / `@Path` | **不适用** | 模块本体无 Service；公共面由本文 §5 定稿为 **8 类型** |
| HIO / 路由 / 拦截器（入站） | **不适用** | 模块无入站 HTTP 面；`TraceHandler` 是挂在使用方 Handler 链上的**接入构件**，不是路由 |
| 低保真 UI 原型 / QA 基线门禁 | **不适用** | 无页面；`aifei-trace-demo` 单页由前端直接实装，**不经原型与 QA 基线门禁**（§13 已注明） |
| `AppConfig implements AifeiConfig` / `Launcher` | **不适用** | 装配动作全部发生在**使用方应用**的 `AppConfig` 内（`settings.setLogFactory(...)` / `addHandler(...)`）；本模块**不提供 Plugin**（§2 决策 6） |

`aifei-trace/` 内 `mvn clean verify` 必须 **0 failure / 0 error**（含架构门禁测试）。

### 1.3 公共面收敛纪律（硬约束）

| # | 纪律 |
| --- | --- |
| 1 | **根包公共 API 精确 6 个类型**：`TraceKit` / `TraceContext` / `TraceIdGenerator` / `DefaultTraceIdGenerator` / `TraceLog` / `TraceLogFactory`；**`web` 包精确 2 个**：`TraceHandler` / `TraceIdExtractor`。**不得增删、不得改名**；变更须先改本文 §5 并过设计决策 |
| 2 | 公共面出现 `cn.aifei.log.Log` / `LogFactory`（TraceLog / TraceLogFactory 签名）与 `cn.aifei.core.Handler` / `Input` / `Output`（web 包签名）属**已定档的框架类型例外**（§2 决策 2 / 决策 3）；除这两组外，公共面**零第三方类型** |
| 3 | **零第三方依赖**：pom 只有 `cn.aifei:aifei-all`（`provided`）+ `junit`（`test`）；**不声明 `cn.aifei:aifei-log`**（其 `Log` / `LogFactory` 接口定义在框架内核 `cn.aifei:aifei`，随 `aifei-all` 提供，已核实本地仓库 1.1.2 内含 `cn/aifei/log/{Log,LogFactory,LogKit}.class`） |
| 4 | **无 internal 包**：全模块 8 公共类型即全部实现（§2 决策 5），是对 aifei-http 范式（根包公共 + internal 实现）的**有意偏离** |
| 5 | 每类规则只有一个定义点（§6.4），实现类不得复制 |
| 6 | Java 8 语法与 API 双约束；主 JAR class major = 52 |

> 第 1、2、3、6 条由架构门禁 `ArchitectureBoundaryTest`（§10）强制，**不得放宽或绕过**。

### 1.4 v1 不做清单（边界，防范围蔓延）

**不做跨线程自动传播**（刻意不用 `InheritableThreadLocal`，防线程池串号——官方既定语义）；**不做 Span 树**（`startSpan()` 覆盖旧值，单层 span）；**不做 TraceException**（官方 1.1.1 无此类型，本模块也无业务失败场景，§2 决策 1）；**不做 MDC 注入**（aifei-log 无 MDC 上下文能力，已核实；`[traceId] ` 前缀是唯一日志贯通形态）；**不做跨模块传播**（MQ 消息头 / WS 帧头 / Job 事件字段，§2 决策 8）；**不做 Plugin / 配置键 / SPI**；**不做采样率、上报、存储**（本模块只负责「标识与贯通」，不做任何遥测后端对接）；**不做异步日志通道**（TraceLog 同步在调用线程拼前缀）。

---

## 2. 设计决策表

> 格式：**决策** —— 理由 —— 被否决的替代方案。

| # | 决策 | 理由 | 被否决的替代方案 |
| --- | --- | --- | --- |
| 1 | **公共面对齐官方 8 类型，不收敛、不改名**：根包 `TraceKit` / `TraceContext` / `TraceIdGenerator` / `DefaultTraceIdGenerator` / `TraceLog` / `TraceLogFactory` + `web.TraceHandler` / `web.TraceIdExtractor`；**不引入 `TraceException`** | ① 官方 1.1.1 公共面即此 8 个，需求排期表中预估的 `TraceException` 在官方实际产物中**不存在**——本模块全部失败路径均为「静默回退/吞掉」（extractor 异常吞掉回退生成、init 空白回退生成），**没有任何场景需要向业务抛模块异常**，为不存在的失败造异常类型纯属公共面扩张；② 生态协同（未来 mq / ws / job 读 traceId）与官方用法一致可零成本迁移；③ 命名 `TraceKit` 保留：它操作的是**有状态的运行期对象**（ThreadLocal 绑定的链路上下文 + 可替换的 TraceIdGenerator），与 aifei 官方 `LogKit` 门面形态同类，且 `TraceKit` 名称已被需求文档冻结 | ① 收敛为 4~5 类型（如合并 TraceContext 进 TraceKit）：破坏与官方 API 一致性，且 TraceContext 作为「ThreadLocal 唯一持有点」独立存在才能支撑门禁单点断言；② 引入 TraceException：无抛出场景，纯面扩张；③ TraceKit 改名 TraceUtil（按 Util 判定口径）：破坏需求冻结名称与生态术语一致性——其「无装配生命周期、任何时刻可调用」的特性偏离 Kit 判据这一点已在 §2 决策 4 显式声明为命名例外 |
| 2 | **TraceLog / TraceLogFactory 保留为公共类型并登记「公共面框架类型」例外**：二者 `implements cn.aifei.log.Log` / `LogFactory`，签名直接出现框架类型 | `settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()))` 是框架配置入口（`AifeiConfig` 的 `config(Settings)` 内），**使用方必须在自身 AppConfig 中构造并传入该类型**——收进 internal 等于核心用法不可用；类比根 `AGENTS.md` 已登记的 `aifei-sa-token` 例外（公共 API 签名直接暴露内核类型）。pom 维持 `aifei-all` provided，消费方自带框架，无传递依赖问题。**须在根 `AGENTS.md` 登记后落地**（§12 待明确事项 1） | ① 收进 internal：`setLogFactory` 入口拿不到类型，核心用法崩塌；② 自定义 `TraceLog` 接口 + 适配器：`setLogFactory` 只接受 `cn.aifei.log.LogFactory`，自造接口无法注入 |
| 3 | **`web.TraceHandler` / `web.TraceIdExtractor` 放 `cn.hg.aifei.trace.web`，属公共承诺 API**；门禁定档：`cn.aifei.core.*` 允许出现在 `web` 包（本模块唯一接入包），`cn.aifei.log.*` 允许出现在根包（TraceLog / TraceLogFactory），其余 `cn.aifei.*` 全模块禁止 | ① TraceHandler 是本模块**核心使用入口**：使用方必须在 AppConfig 中 `new TraceHandler<>(...)` 并 `addHandler` 进 Handler 链；② 若按 aifei-http 纪律收进非承诺桥接包（`cn.hg.aifei.trace.aifei`），核心功能变成「消费方不得引用」——模块定位自相矛盾；③ trace 与 aifei-http 的差异是本质的：http 的公共面刻意与框架无关（纯出站客户端），trace 是**天然的框架接合模块**，接合类型即产品。`TraceIdExtractor` 签名含 `cn.aifei.core.Input`（lambda 用法 `input -> input.getHeader(...)`），与 TraceHandler 同包同承诺级 | ① 放 `aifei` 桥接包作非承诺 API：核心入口不可承诺，违背模块定位；② 放根包：`cn.aifei.core.*` 污染根包公共面，包边界不可断言 |
| 4 | **`TraceKit` 命名为对 Kit 判据的显式命名例外**：它是 `final` + 私有构造 + 全静态门面，但**无装配生命周期**——未做任何配置时调用即成立（`getTraceId()` 返回 null，`init()` 直接生成） | 仓库 Kit 判据（「需先从注册表取实例才能干活」）本应判 Util；但 ① `TraceKit` 是 aifei 官方与需求文档**冻结的术语**（类比 LogKit——LogKit 同样无装配期、随取随用）；② 它操作的对象（ThreadLocal 上下文、可替换生成器）确实**有状态**，「有状态门面」语义成立，只是该状态**无生命周期边界**；③ 改名收益为零、破坏生态一致性。后果：TraceKit **永不抛「未装配」异常**，与 aifei-http `HttpKit` 的「未装配即失败」是**两种门面形态**，README 须分写 | ① 改名 `TraceUtil`：语义上更贴判据，但破坏官方术语冻结与生态术语一致（根 `AGENTS.md`：跨模块术语必须一致）；② 保留 TraceKit 并附加 Plugin 强造装配期：为不存在的生命周期付全部契约成本（见决策 6） |
| 5 | **无 internal 包**：全模块 8 公共类型即全部实现，无任何隐藏实现类 | ① 模块体量极小（8 类型、无编排逻辑），拆 internal 只添间接层；② 官方 1.1.1 同样无 internal；③ 唯一定义点由「类型即定义点」天然满足：ThreadLocal 持有点 = TraceContext、生成点 = DefaultTraceIdGenerator、前缀拼装点 = TraceLog，各自公开且唯一，门禁按「字面量只在唯一文件出现」断言（§10 ⑥⑦）即无实现藏匿空间 | ① 仿 aifei-http 设 internal 包（把 TraceContext / DefaultTraceIdGenerator 收进去）：破坏官方公共面一致性，且 TraceContext 必须被 TraceLog（根包）与 TraceHandler（web 包）跨包访问，收 internal 反而需要提升可见性，自相矛盾 |
| 6 | **无 Plugin、无配置键**：纯静态门面 + 使用方显式构造接入构件 | ① trace **无连接资源**：ThreadLocal 无需 start/stop，生成器无需初始化；② **无配置可解析**：全部行为由代码装配（`setTraceIdGenerator` / `setExtractor` / `setLogFactory`），没有一条配置能从 `app-config.txt` 读进来，强造 Plugin 只剩空壳 `start()`；③ 接入点本就发生在使用方 AppConfig 内（Handler 链、LogFactory 均是框架配置对象），再经 Plugin 中转是绕路 | ① 仿 aifei-http 加 `TracePlugin implements Plugin`：无配置无资源无实例注册，纯空转；② Plugin 承担 `setTraceIdGenerator` 装配：见决策 5（替换入口维持静态 setter，Plugin 无处安放） |
| 7 | **生成器契约与替换入口定档**：`TraceIdGenerator.generate()` 契约 = 非空、全局趋势唯一、仅 `[0-9a-f]`、固定 32 位小写十六进制；默认实现 `DefaultTraceIdGenerator`（UUID 去横线）；替换入口 = `TraceKit.setTraceIdGenerator(TraceIdGenerator)` 静态 setter，**null → `IllegalArgumentException`**（对官方 `Objects.requireNonNull` 抛 NPE 的**有意偏离**，对齐仓库「调用方参数写错 → IllegalArgumentException」口径）；**模块不校验替换实现产出的格式**（契约由 javadoc 约定，替换方自担）；建议仅在启动期（AppConfig）替换，运行期替换无锁、不保证原子可见性（javadoc 与 README 声明） | ① 契约固定 32 位十六进制使日志检索、grep、跨服务对齐有确定格式；② 静态 setter 是官方既定语义，且本模块无 Plugin（决策 6），无 start 装配时机可承载替换；③ 不校验产出：校验每条生成的格式属热路径开销，且「生成器自己最清楚格式」，契约违例属实现方 bug 而非运行期输入错误 | ① 改 Plugin.start 装配：无 Plugin（决策 6）；② 模块校验产出格式：热路径正则开销 + 违例处置策略（回退？抛出？）又要新增一整类语义；③ `TraceKit.idGenerator` 设为 public static 可直接赋值：绕过 null 防线与 setter 语义，官方即 package-private 字段 + 公共 setter，维持官方形态 |
| 8 | **跨模块传播（MQ 消息头 / WS 帧头 / Job 事件字段）v1.0.0 明确不做**：本模块零生态模块依赖，不提供任何「把 traceId 写进消息/帧/事件」的 API | ① 传播点在各协议的**头/帧/字段结构**里，属 aifei-mq / aifei-websocket / aifei-job 的协议冻结面，由 trace 侧提供写入口意味着 trace 必须反向依赖（或被依赖进）这三大模块——依赖方向倒置，且三大模块各自发版节奏不同，耦合会拖死 trace 的独立演进；② 正确方向：各消费模块**自行**在其协议结构中增设 traceId 透传字段，**消费端 `TraceKit.init(收到的traceId)`、发送端 `TraceKit.getTraceId()` 读取**——只需本模块既有 API，零新增；③ 业务侧手动传递指引写入 README（§8.3） | ① 本模块提供 `TracePropagator` 之类统一传播 API：需定义消息头/帧头/事件字段的通用抽象，反向制造跨模块协议耦合；② 依赖 mq/ws/job 提供回调接口：依赖方向倒置 + 三模块版本对齐成本 |
| 9 | **异常与失败语义全面「不失败」**：模块方法**不声明、不抛出**任何模块异常类型；受检异常仅在 `TraceHandler.handle` 签名上透传（override 框架签名）；调用方参数写错用 `IllegalArgumentException` | ① trace 是**横切可观测设施**：它的任何失败都不应影响业务请求——extractor 抛异常吞掉回退生成、init 空白回退生成、前缀拼装不可能失败（字符串拼接）；② 无失败场景则无异常分类表，TraceException 无存在基础（决策 1）；③ `IllegalArgumentException` 对齐仓库口径（aifei-http 决策 15 同款） | ① 引入 TraceException 体系：无场景；② extractor 异常向上传播：一次提取失败打断业务请求，违背可观测设施定位 |
| 10 | **`aifei-trace-demo` 定档**：`aifei-http-demo` 同款形态（平级工程、零构建静态层单页、`start-demo.bat` / `stop-demo.bat`、离线可跑、端口 **18130**）；演示内容 = ① 经 `TraceHandler` 绑定的当前请求 traceId / spanId 实时展示；② `TraceLogFactory` 装配演示（`DemoAppConfig` 中 `settings.setLogFactory(new TraceLogFactory(new ConsoleLogFactory()))`，demo 自带极简 ConsoleLog/ConsoleLogFactory，**不引任何 slf4j/log4j 绑定**，维持零第三方）；③ 线程池手动传递演示（页面触发异步任务，子线程恢复链路）；④ 一条带 `[traceId] ` 前缀的日志样本展示。**demo 单页不经原型基线门禁** | 与 aifei-http-demo 同范式已验证（离线、零外网、零构建）；ConsoleLogFactory 方案让 demo 在不引日志绑定的前提下完整演示「LogFactory 装饰链」，且验证了模块 `provided` 口径（demo 自带 aifei-all）；端口顺延 18110（job）/ 18120（http）→ 18130 | ① demo 引 slf4j + Slf4jLogFactory：引入第三方绑定，违反 demo 离线极简口径（且 aifei-log 的 Slf4jLogFactory 属官方模块源码，demo 引入需同步源码，超出演示必要）；② 不做 demo：`TraceLogFactory` 的「配置入口装配」用法无法离线直观验证 |

---

## 3. 术语表

| 术语 | 含义 | 归属 |
| --- | --- | --- |
| **TraceId** | 一次完整请求链路的唯一标识；32 位小写十六进制；同链路所有环节恒等 | `TraceIdGenerator`（生成）/ `TraceContext`（持有） |
| **SpanId** | 链路内**单层**片段标识；`startSpan()` 生成并覆盖旧值，**不做 Span 树** | 同上 |
| **链路上下文** | 当前线程绑定的 traceId + spanId（两个独立 ThreadLocal） | `TraceContext` |
| **生成器 TraceIdGenerator** | TraceId 生成策略接口；默认实现 `DefaultTraceIdGenerator` | 根包（公共接口 + 默认实现） |
| **提取器 TraceIdExtractor** | 请求进入时从上游提取 traceId 的策略（函数式接口）；失败/空白回退生成 | `web` 包（公共接口） |
| **接入处理器 TraceHandler** | 挂 Handler 链的入口构件：绑定 → 透传下游 → finally 清理 | `web` 包（公共类） |
| **日志装饰器 TraceLog** | 在打印时刻拼 `[traceId] ` 前缀的 `cn.aifei.log.Log` 包装实现 | 根包（公共类） |
| **日志工厂装饰器 TraceLogFactory** | 把任意 `cn.aifei.log.LogFactory` 产出的 Log 包装为 TraceLog | 根包（公共类） |
| **回退生成 fallback** | 提取结果 null/空白或提取抛异常时，改用生成器产出新 TraceId 的语义 | `TraceKit#init` / `TraceHandler#handle` |

---

## 4. 需求协同口径定档

需求排期表协同目标「HTTP 入口 → Service → aifei-mq 消息头 → aifei-websocket 帧头 → aifei-job 事件」逐段定档：

| 环节 | v1.0.0 状态 | 说明 |
| --- | --- | --- |
| HTTP 入口（Handler） | **做**（§5.7 / §5.8） | `web.TraceHandler` + `web.TraceIdExtractor` 全覆盖 |
| Service（同线程业务代码） | **做**（免费获得） | 同线程直读 `TraceKit.getTraceId()`，`TraceLog` 前缀自动贯通 |
| 线程池 / 异步 | **手动传递**（§8.3 指引） | 刻意不做自动传播（防串号），README 写明用法 |
| aifei-mq 消息头 | **不做**（决策 8） | 由 aifei-mq 后续版本增设透传字段，消费端 `TraceKit.init` |
| aifei-websocket 帧头 | **不做**（决策 8） | 同上 |
| aifei-job 事件 | **不做**（决策 8） | 同上 |

---

## 5. 公共 API 契约（根包精确 6 + web 包精确 2）

根包 `cn.hg.aifei.trace` 与子包 `cn.hg.aifei.trace.web` 共 **8 个公共类型**，为本节与门禁 `EXPECTED_ROOT_TYPES` / `EXPECTED_WEB_TYPES` 的**唯一事实来源**。逐字签名如下（**编码角色照抄**；`static` 与否、返回类型、异常声明均不得改动）。

### 5.1 `TraceKit`（公共静态门面，`final`）

```java
public final class TraceKit {

    public static TraceIdGenerator getTraceIdGenerator();

    public static void setTraceIdGenerator(TraceIdGenerator generator);

    public static String init();

    public static String init(String traceId);

    public static String startSpan();

    public static String getTraceId();

    public static String getSpanId();

    public static void clear();
}
```

| 方法 | 契约 | 非法入参 |
| --- | --- | --- |
| `getTraceIdGenerator()` | 返回当前生成器（初始为 `DefaultTraceIdGenerator.INSTANCE`） | — |
| `setTraceIdGenerator(generator)` | 替换生成器；**建议仅启动期调用**（运行期替换无锁，不保证原子可见性，javadoc 声明） | `null` → `IllegalArgumentException` |
| `init()` | 等价 `init(null)`：生成并绑定新 TraceId，返回该 TraceId | — |
| `init(traceId)` | 绑定 TraceId：`traceId` **先 trim**，trim 后非空白则原样绑定并返回该值；为 null / 空白 → 回退调用生成器产出新 TraceId 并绑定；返回**最终生效**的 TraceId | 无（null/空白是合法输入，走回退分支） |
| `startSpan()` | 生成新 SpanId 并绑定，返回该 SpanId；**同一请求内多次调用覆盖旧值**，不做 Span 树 | — |
| `getTraceId()` | 当前线程绑定的 TraceId，**未绑定返回 null**（不抛异常） | — |
| `getSpanId()` | 同上，SpanId | — |
| `clear()` | 清理当前线程全部链路上下文（TraceId 与 SpanId）；重复调用安全 | — |

- **命名例外**（决策 4）：本类无装配生命周期、任何时刻调用成立，Kit 判据上属显式例外；**永不抛「未装配」类异常**。
- 内部持有 `static TraceIdGenerator idGenerator`（**包级可见、非 final**，官方同形态）：字段本身是 internal 细节，唯一写入点是 `setTraceIdGenerator`，**不得升为 public**。

### 5.2 `TraceContext`（公共上下文持有器，`final`）

```java
public final class TraceContext {

    public static String getTraceId();

    public static String getSpanId();

    public static void setTraceId(String traceId);

    public static void setSpanId(String spanId);

    public static void clear();
}
```

| 方法 | 契约 |
| --- | --- |
| `getTraceId()` / `getSpanId()` | 当前线程绑定值，未绑定返回 null |
| `setTraceId(traceId)` | 绑定；**传 null 等价于移除**（官方语义：`ThreadLocal.remove`） |
| `setSpanId(spanId)` | 同上 |
| `clear()` | 移除两个 ThreadLocal，防线程池复用串号 |

- **全模块 ThreadLocal 唯一持有点**（门禁 ⑥ 断言）：两个字段 `private static final ThreadLocal<String> traceIdTL` / `spanIdTL`。
- **普通 `ThreadLocal`，刻意不用 `InheritableThreadLocal`**：防线程池复用线程导致 TraceId 串号；跨线程传播须业务手动（§8.3）。
- 与 `TraceKit` 的分工：TraceContext 是**原始持有器**（只做 set/get/remove，零业务语义）；TraceKit 是**语义门面**（trim、回退、生成）。业务日常只用 TraceKit。

### 5.3 `TraceIdGenerator`（公共接口）

```java
public interface TraceIdGenerator {

    String generate();
}
```

- **契约**（javadoc 强约束，模块运行期不校验，决策 7）：返回值**非空**、全局趋势唯一、仅含 `[0-9a-f]`、**固定 32 位**小写十六进制——保证日志检索与展示格式对齐。
- 唯一方法无参数；实现应无状态或自行保证线程安全（默认实现无状态）。

### 5.4 `DefaultTraceIdGenerator`（公共默认实现，单例）

```java
public class DefaultTraceIdGenerator implements TraceIdGenerator {

    public static final DefaultTraceIdGenerator INSTANCE;

    private DefaultTraceIdGenerator();

    @Override
    public String generate();
}
```

- `generate()` = `UUID.randomUUID().toString().replace("-", "")`：32 位小写十六进制。
- **私有构造 + 唯一单例 `INSTANCE`**（官方同形态）：外部无法另建实例；替换生成器时传入 `INSTANCE` 即恢复默认。
- **全模块 `UUID` 唯一出现点**（门禁 ⑦ 断言）。

### 5.5 `TraceLog`（公共 Log 装饰器，`implements cn.aifei.log.Log`）——框架类型例外（决策 2）

```java
public class TraceLog implements Log {

    public TraceLog(Log delegate);

    @Override
    public String getName();

    // ---- 每个级别（trace / debug / info / warn / error）各 6 个方法，共 30 个 + 5 个 isXxxEnabled ----
    @Override
    public boolean isTraceEnabled();
    @Override
    public void trace(String msg);
    @Override
    public void trace(String msg, Throwable t);
    @Override
    public void trace(String format, Object arg);
    @Override
    public void trace(String format, Object arg1, Object arg2);
    @Override
    public void trace(String format, Object... arguments);
    @Override
    public void trace(Supplier<String> messageSupplier);
    // debug / info / warn / error 与 trace 同构（略——签名逐字对齐 cn.aifei.log.Log 接口）
}
```

| 项 | 契约 |
| --- | --- |
| 构造 | `delegate == null` → `IllegalArgumentException`（对官方 NPE 的有意偏离，对齐仓库口径） |
| **前缀拼装**（唯一定义点，§6.4） | 打印时刻计算 `prefix()`：`TraceContext.getTraceId()` 非 null → `"[" + traceId + "] "`，**缺失 → 空串**（消息原样透出） |
| String 消息变体 | `delegate.方法(prefix() + msg, ...)` —— 前缀在**调用时刻**拼接 |
| `Supplier<String>` 变体 | **懒求值**：`delegate.方法(() -> prefix() + messageSupplier.get())` —— 前缀在**打印时刻**读取链路上下文，保证异步日志取到打印线程的 TraceId |
| `isXxxEnabled()` / `getName()` | 纯透传 delegate，不拼前缀 |
| 格式变体 | 前缀只拼在 **format 串**上（`prefix() + format`），占位参数原样传递 |
| 可变性 | 无状态（只有 `final delegate` 字段），线程安全 |

### 5.6 `TraceLogFactory`（公共 LogFactory 装饰器，`implements cn.aifei.log.LogFactory`）——框架类型例外（决策 2）

```java
public class TraceLogFactory implements LogFactory {

    public TraceLogFactory(LogFactory delegate);

    @Override
    public Log getLog();

    @Override
    public Log getLog(Class<?> clazz);

    @Override
    public Log getLog(String name);
}
```

| 项 | 契约 |
| --- | --- |
| 构造 | `delegate == null` → `IllegalArgumentException`；**delegate 必须显式给定**（javadoc 声明：避免自包装造成无限递归——`new TraceLogFactory(new TraceLogFactory(...))` 或包自身工厂属使用方错误，模块不检测不防护） |
| 三个 `getLog` | 返回 `new TraceLog(delegate.getLog(...))`——每次取用新建装饰器（TraceLog 无状态，成本为零） |
| 用法 | `settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()))` / `new TraceLogFactory(new Log4jLogFactory())`——与 aifei-log 各工厂天然兼容；适配器由消费方经 aifei-all 自带，本模块不造日志 |

### 5.7 `web.TraceHandler`（公共接入处理器，`extends cn.aifei.core.Handler`）——框架类型例外（决策 3）

```java
public class TraceHandler<I extends Input, O extends Output> extends Handler<I, O> {

    public TraceHandler();

    public TraceHandler(TraceIdExtractor<I> extractor);

    public TraceHandler<I, O> setExtractor(TraceIdExtractor<I> extractor);

    @Override
    public void handle(String path, I input, O output) throws Exception;
}
```

| 项 | 契约 |
| --- | --- |
| 泛型（决策定档） | `<I extends Input, O extends Output>` 保留（必须与 `Handler<I,O>` 对齐才能 override `handle`）；`TraceIdExtractor` 只保留 `I`（提取只读 Input，与 O 无关，官方同形态） |
| 构造 | 无参 = **纯生成模式**（不提取，直接生成新 TraceId）；带 extractor = 提取模式；`setExtractor` 返回 `this` 支持链式，传 `null` 等价纯生成模式 |
| `handle` 执行序 | ① extractor 非 null 时调 `extractor.extract(input)`；② `TraceKit.init(traceId)` 绑定（含回退语义）；③ `next != null` 时透传下游；④ **finally 中 `TraceKit.clear()`** |
| extractor 异常 | **吞掉并回退为生成新 TraceId**——任何路径下 trace 失败不影响业务请求（决策 9） |
| 下游异常 | **原样向上传播**（不捕获、不包装）；finally 照常清理 |
| `next == null` | 安全结束（框架 `Handler` 的 `next` 为 protected 字段（aifei-all 1.1.2 实测），子类跨包可直接访问，官方同形态） |
| 链上位置 | **必须第一个 addHandler**，才能覆盖全部下游 Handler（javadoc 与 README 写明） |
| 静态资源 | 静态资源在 undertow IO 线程即被拦截，不进 Dispatcher，天然不产生 TraceId，无需排除（javadoc 注明，官方同口径） |

### 5.8 `web.TraceIdExtractor`（公共函数式接口）

```java
@FunctionalInterface
public interface TraceIdExtractor<I extends Input> {

    String extract(I input);
}
```

- 语义：从请求中提取上游透传的 TraceId（如 `input -> input.getHeader("X-Trace-Id")`）；**无法提取返回 null**。
- `cn.aifei.core.Input` 公开接口**只有参数读取能力，无 header 访问**（官方已核实）：跨服务透传需业务侧自定义 Input 实现暴露 header 后提取——此边界写入 javadoc 与 README 注意事项。
- 抛出的任何异常被 TraceHandler 吞掉回退生成（§5.7）。

### 5.9 公共面计数口径（供门禁复用）

「根包公共类型」= `src/main/java/cn/hg/aifei/trace/` 目录**直接子级的 `.java` 文件**（不含子包）：

`TraceKit.java` / `TraceContext.java` / `TraceIdGenerator.java` / `DefaultTraceIdGenerator.java` / `TraceLog.java` / `TraceLogFactory.java` —— **精确 6 个**。

「web 包公共类型」= `src/main/java/cn/hg/aifei/trace/web/` 目录**直接子级 `.java` 文件**：`TraceHandler.java` / `TraceIdExtractor.java` —— **精确 2 个**。

### 5.10 命名依据（`Kit` / 接口 / 实现前缀 / 异常缺席）

| 类型 | 形态 | 命名依据（仓库根 `AGENTS.md`「命名约定」+ 本设计决策） |
| --- | --- | --- |
| `TraceKit` | `public final` + 私有构造 + 全 `static` | **有状态门面**（操作 ThreadLocal 上下文与可替换生成器）；「无装配生命周期」属显式命名例外（决策 4，对齐官方 LogKit / TraceKit 门面形态与需求冻结术语） |
| `TraceContext` | `public final` + 私有构造 + 全 `static` | 上下文**持有器**名词；官方同名，非 Kit/Util 语义（无装配、无业务语义，纯存取） |
| `TraceIdGenerator` | `public interface` | 策略接口名词性后缀 `-Generator` |
| `DefaultTraceIdGenerator` | `public class implements TraceIdGenerator` | 实现类冠实现前缀 `Default`（仓库惯例） |
| `TraceLog` / `TraceLogFactory` | `public class implements cn.aifei.log.Log / LogFactory` | 装饰器：被装饰类型名 + 前缀（对齐官方与 aifei-log 的 `Slf4jLog` / `Slf4jLogFactory` 命名法） |
| `TraceHandler` / `TraceIdExtractor` | `web` 包公共类型 | 对齐框架 `cn.aifei.core.Handler` 与官方同名 |
| （缺席）`TraceException` | **不使用** | 全部失败路径为静默回退/吞掉，无抛出场景（决策 1 / 9） |

---

## 6. 内部结构

### 6.1 包结构与依赖方向

```text
cn.hg.aifei.trace          根包：6 公共类型（上下文 + 门面 + 生成策略 + 日志桥接）
cn.hg.aifei.trace.web      接入包：2 公共类型（Handler 链接入）

依赖方向（门禁 ② 强制）：
  web 包 ──→ 根包（TraceKit / TraceContext）
  根包 ──→ cn.aifei.log（仅 TraceLog / TraceLogFactory 两个文件）
  web 包 ──→ cn.aifei.core（仅 TraceHandler / TraceIdExtractor 两个文件）
  根包其余 4 类型 ──→ 纯 JDK（java.lang / java.util）
  禁止：web 包 import cn.aifei.log.*；根包 import cn.aifei.core.*；
        任何文件 import cn.aifei.plugin.* / cn.aifei.aop.* / cn.aifei.util.* 等其他框架包
```

无 internal 包（决策 5）；无 Plugin（决策 6）；无 SPI（§10 门禁 ⑨）。

### 6.2 各类型职责（★ 后为唯一职责点）

| 类型 | 职责 |
| --- | --- |
| `TraceContext` | **★ ThreadLocal 持有唯一定义点**：两个字段、set（null=移除）/ get / clear，零业务语义 |
| `TraceKit` | **★ init trim/回退语义唯一定义点** + **★ 生成器替换入口唯一定义点**：idGenerator 字段、setter 校验、init 生成与绑定、startSpan 覆盖语义 |
| `TraceIdGenerator` | 生成契约声明（javadoc 唯一定义点） |
| `DefaultTraceIdGenerator` | **★ TraceId 生成算法唯一定义点**（UUID 去横线；全模块唯一 `UUID` 出现点） |
| `TraceLog` | **★ `[traceId] ` 前缀拼装唯一定义点**（`prefix()` 私有方法）：30 个打印方法 + 5 个透传开关 + getName |
| `TraceLogFactory` | **★ 日志工厂装饰唯一定义点**：3 个 getLog 包装 |
| `web.TraceHandler` | **★ 提取异常吞并回退 + finally 清理唯一定义点**：handle 执行序 |
| `web.TraceIdExtractor` | 提取契约声明（javadoc 唯一定义点：null 语义、Input 无 header 能力边界、异常被吞） |

### 6.3 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| ThreadLocal 字段与 set/get/remove（null = 移除） | `TraceContext` |
| `init` 的 trim 与空白回退生成语义、`startSpan` 覆盖语义 | `TraceKit#init` / `TraceKit#startSpan` |
| 生成器字段、替换入口与 null 校验 | `TraceKit#setTraceIdGenerator` |
| TraceId 生成算法（UUID 去横线） | `DefaultTraceIdGenerator#generate` |
| 生成格式契约（非空 / `[0-9a-f]` / 32 位） | `TraceIdGenerator` javadoc |
| `[traceId] ` 前缀拼装与「缺失 → 空串」 | `TraceLog#prefix` |
| Supplier 懒求值包装形态 | `TraceLog` 各 Supplier 变体 |
| LogFactory 装饰与「显式 delegate 防自包装」约定 | `TraceLogFactory` |
| extractor 异常吞并回退、finally 清理、`next == null` 安全结束 | `web.TraceHandler#handle` |
| 提取契约（null 语义、Input 无 header 边界） | `web.TraceIdExtractor` javadoc |
| `IllegalArgumentException` 消息措辞 | §7 异常语义表（各抛出点按表逐字实现，不各写措辞） |

---

## 7. 行为契约要点与异常语义表

### 7.1 行为契约要点速查

- **ThreadLocal 刻意不继承**：`InheritableThreadLocal` 被否决（线程池复用串号）；跨线程必须手动传递（§8.3）。
- **请求结束必须 clear**：`TraceHandler` 在 finally 保证；手动 `init` 的场景（异步任务）业务自行 try/finally `TraceKit.clear()`。
- **traceId 绑定前 trim**：`init("  abc  ")` 绑定 `"abc"`；`init("   ")` 走回退生成。
- **spanId 单层覆盖**：`startSpan()` 再次调用覆盖旧 spanId，**不影响 traceId**；不做栈式 Span。
- **前缀拼装在打印时刻**：Supplier 变体懒求值；String 变体在调用时刻拼接——同一 Log 实例跨线程使用时各自读到各自线程的 TraceId（TraceLog 无状态）。
- **TraceId 缺失时日志原样透出**：不加空前缀，不报错，不打警告（高频路径零开销）。
- **TraceHandler 链上位置**：必须第一个 addHandler；下游异常原样传播；finally 清理无条件执行。

### 7.2 异常语义表（全部失败路径，无遗漏）

| # | 场景 | 行为 | 异常 |
| --- | --- | --- | --- |
| 1 | `TraceKit.setTraceIdGenerator(null)` | 拒绝 | `IllegalArgumentException`（消息含参数语义，见 §7.3） |
| 2 | `TraceKit.init(null)` / `init("")` / `init("   ")` | **回退**：调用生成器产出新 TraceId 并绑定 | 无 |
| 3 | `TraceKit.init(" abc ")` | trim 后绑定 `"abc"` | 无 |
| 4 | `extractor.extract(input)` 返回 null / 空白 | **回退生成**（经 `TraceKit.init` 语义） | 无 |
| 5 | `extractor.extract(input)` 抛任何异常 | **吞掉并回退生成**；trace 失败不影响业务 | 无（被吞） |
| 6 | 下游 Handler 抛异常 | **原样向上传播**；finally 中 `TraceKit.clear()` 照常执行 | 原异常 |
| 7 | `TraceKit.getTraceId()` / `getSpanId()` 未绑定 | 返回 null | 无 |
| 8 | `TraceLog` 打印时 TraceId 未绑定 | 消息原样透出（空前缀） | 无 |
| 9 | `new TraceLog(null)` / `new TraceLogFactory(null)` | 拒绝 | `IllegalArgumentException` |
| 10 | 替换生成器产出格式违例（非 32 位十六进制等） | **模块不校验、不拦截**，按原值绑定（契约由 javadoc 约定，替换方自担，决策 7） | 无 |
| 11 | `TraceKit` / `TraceContext` 未做任何配置直接调用 | 正常工作（生成器有默认值） | 无 |
| 12 | `TraceHandler.next == null` | 安全结束（无下游可传） | 无 |
| 13 | 重复 `TraceKit.clear()` / 对未绑定线程 clear | 幂等安全（`ThreadLocal.remove`） | 无 |

### 7.3 `IllegalArgumentException` 消息格式（唯一措辞来源）

| 场景 | 消息（逐字） |
| --- | --- |
| `setTraceIdGenerator(null)` | `generator can not be null.` |
| `new TraceLog(null)` | `delegate can not be null.` |
| `new TraceLogFactory(null)` | `delegate can not be null.` |

> **对官方的有意偏离声明**：官方对上述三处以 `Objects.requireNonNull` 抛 `NullPointerException`；本模块按仓库「调用方参数写错 → IllegalArgumentException」口径改为 `IllegalArgumentException`（含 cause 语义等价：消息即 NPE 的 message）。已在门禁测试中按本表断言。

---

## 8. 使用链路与手动传递指引（README 素材，唯一来源）

### 8.1 请求链路（自动）

```text
请求进入 → TraceHandler.handle:
  ① extractor.extract(input)          （可 null / 可抛异常 → 回退）
  ② TraceKit.init(traceId)            （绑定，返回生效值）
  ③ next.handle(path, input, output)  （业务全程可 TraceKit.getTraceId() / TraceLog 自动前缀）
  ④ finally TraceKit.clear()
```

### 8.2 日志贯通（装配期一次性）

```text
AppConfig.config(Settings): settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()))
→ 全应用所有 Log 产出自动带 "[traceId] " 前缀（当前线程有绑定值时）
```

### 8.3 手动传递（线程池 / 异步 / 跨模块，v1 唯一形态）

- **线程池 / 异步**：提交前 `String tid = TraceKit.getTraceId();`，任务体内 `try { TraceKit.init(tid); ... } finally { TraceKit.clear(); }`。
- **MQ / WS / Job（未来）**：发送端把 `TraceKit.getTraceId()` 写入消息属性/帧头/事件字段（由各模块自身的协议结构承载）；消费端取出后 `TraceKit.init(值)`，处理完 finally clear。**本模块不提供写入口**（决策 8）。
- **刻意不自动传播的原因**：`InheritableThreadLocal` 在线程池复用下串号（上一任务残留），以及自动传播无法区分「延续链路」与「新链路」——语义决定权必须在提交/消费代码里显式表达。

---

## 9. 类图与关键时序图

### 9.1 类图

```mermaid
classDiagram
    direction LR

    class TraceKit {
        <<final, 全 static>>
        -TraceIdGenerator idGenerator
        +getTraceIdGenerator()$ TraceIdGenerator
        +setTraceIdGenerator(TraceIdGenerator)$ void
        +init()$ String
        +init(String)$ String
        +startSpan()$ String
        +getTraceId()$ String
        +getSpanId()$ String
        +clear()$ void
    }

    class TraceContext {
        <<final, 全 static>>
        -ThreadLocal~String~ traceIdTL
        -ThreadLocal~String~ spanIdTL
        +getTraceId()$ String
        +getSpanId()$ String
        +setTraceId(String)$ void
        +setSpanId(String)$ void
        +clear()$ void
    }

    class TraceIdGenerator {
        <<interface>>
        +generate() String
    }

    class DefaultTraceIdGenerator {
        <<单例>>
        +INSTANCE$ DefaultTraceIdGenerator
        +generate() String
    }

    class TraceLog {
        <<implements cn.aifei.log.Log>>
        -Log delegate
        +TraceLog(Log)
        +getName() String
        -prefix() String
        +trace(String) void
        +trace(Supplier~String~) void
        +info(String) void
        +error(String, Throwable) void
        +isInfoEnabled() boolean
    }

    class TraceLogFactory {
        <<implements cn.aifei.log.LogFactory>>
        -LogFactory delegate
        +TraceLogFactory(LogFactory)
        +getLog() Log
        +getLog(Class) Log
        +getLog(String) Log
    }

    class TraceHandler {
        <<web, extends cn.aifei.core.Handler>>
        -TraceIdExtractor~I~ extractor
        +TraceHandler()
        +TraceHandler(TraceIdExtractor~I~)
        +setExtractor(TraceIdExtractor~I~) TraceHandler
        +handle(String, I, O) void
    }

    class TraceIdExtractor {
        <<web, @FunctionalInterface>>
        +extract(I input) String
    }

    class Log {
        <<cn.aifei.log, 框架接口>>
    }

    class LogFactory {
        <<cn.aifei.log, 框架接口>>
    }

    class Handler {
        <<cn.aifei.core, 框架类>>
        +Handler~I,O~ next
    }

    class Input {
        <<cn.aifei.core, 框架接口>>
    }

    class Output {
        <<cn.aifei.core, 框架接口>>
    }

    TraceIdGenerator <|.. DefaultTraceIdGenerator : implements
    Log <|.. TraceLog : implements（框架类型例外）
    LogFactory <|.. TraceLogFactory : implements（框架类型例外）
    Handler <|-- TraceHandler : extends（框架类型例外）
    TraceIdGenerator "idGenerator" o-- TraceKit : 可替换
    TraceContext ..> TraceKit : set/get 语义层调用
    TraceLog ..> TraceContext : 打印时刻读 traceId
    TraceLogFactory ..> TraceLog : 包装产出
    TraceHandler ..> TraceKit : init / clear
    TraceHandler o-- TraceIdExtractor : 可选提取策略
    TraceIdExtractor ..> Input : 泛型约束 I extends Input
```

> 类图事实要点：8 个公共类型 + 4 个框架类型（`Log` / `LogFactory` / `Handler` / `Input`，均属已定档例外）。**没有** Plugin / internal / SPI / 配置类；`TraceLog` 与 `TraceLogFactory` 是纯无状态装饰器，`TraceContext` 是唯一的可变全局状态（ThreadLocal）。

### 9.2 时序图 ①：一次请求经 TraceHandler 绑定 → 业务 → 清理（提取成功路径）

```mermaid
sequenceDiagram
    autonumber
    participant F as 框架 Handler 链
    participant TH as TraceHandler
    participant EX as TraceIdExtractor
    participant K as TraceKit
    participant C as TraceContext
    participant B as 业务 Handler（下游）
    participant L as TraceLog
    participant DL as 底层 Log（如 Slf4jLog）

    F->>TH: handle(path, input, output)
    TH->>EX: extract(input)
    EX-->>TH: "e4f1...9a"（上游透传的 traceId）
    TH->>K: init("e4f1...9a")
    Note over K: trim 非空白 → 原样绑定
    K->>C: setTraceId("e4f1...9a")
    K-->>TH: "e4f1...9a"
    TH->>B: next.handle(path, input, output)
    B->>L: info("订单已创建")
    Note over L: 打印时刻读取当前线程 traceId
    L->>C: getTraceId()
    C-->>L: "e4f1...9a"
    L->>DL: info("[e4f1...9a] 订单已创建")
    B-->>TH: 返回
    Note over TH: finally：无条件清理
    TH->>K: clear()
    K->>C: clear()（移除 traceIdTL + spanIdTL）
    TH-->>F: 返回
```

### 9.3 时序图 ②：提取失败回退 + 下游异常（finally 仍清理）

```mermaid
sequenceDiagram
    autonumber
    participant F as 框架 Handler 链
    participant TH as TraceHandler
    participant EX as TraceIdExtractor
    participant K as TraceKit
    participant G as TraceIdGenerator（当前）
    participant B as 业务 Handler（下游）
    participant C as TraceContext

    F->>TH: handle(path, input, output)
    TH->>EX: extract(input)
    EX--xTH: 抛 RuntimeException
    Note over TH: 吞掉（决策 9：trace 失败不影响业务）
    TH->>K: init(null)
    Note over K: traceId 空白 → 回退生成
    K->>G: generate()
    G-->>K: "8c02...3f"（32 位十六进制）
    K->>C: setTraceId("8c02...3f")
    TH->>B: next.handle(path, input, output)
    B--xTH: 抛业务异常
    Note over TH: 异常原样传播（不捕获不包装）
    TH->>K: clear()（finally，无条件）
    K->>C: clear()
    TH-->>F: throw 原业务异常
```

### 9.4 时序图 ③：线程池手动传递（异步任务恢复链路）

```mermaid
sequenceDiagram
    autonumber
    participant Biz as 业务代码（请求线程）
    participant K as TraceKit
    participant Pool as 线程池
    participant Task as 异步任务（池线程）

    Biz->>K: getTraceId()
    K-->>Biz: "e4f1...9a"
    Biz->>Pool: submit(() -> { ... })
    Note over Biz,Task: TraceId 以变量捕获方式随 lambda 进入任务<br/>（刻意不用 InheritableThreadLocal，防池线程串号）
    Pool->>Task: run()
    Task->>K: init("e4f1...9a")
    Task->>Task: 业务处理（日志自动带前缀）
    Task->>K: clear()（finally，防池线程复用污染）
    Task-->>Pool: 完成
```

---

## 10. 架构门禁设计（`ArchitectureBoundaryTest`）

- **位置 / 机制**：`src/test/java/cn/hg/aifei/trace/ArchitectureBoundaryTest.java`；`Files.walk(Paths.get("src/main/java"))` 收集全部 `.java`，按**源码文本**断言（路径分隔符归一化为 `/`）。
- **执行位置**：**必须在 `aifei-trace/` 根目录**（依赖相对路径 `src/main/java`），不得放宽断言。
- **关键常量**：

```java
private static final Path    SOURCE_ROOT      = Paths.get("src/main/java");
private static final Path    TEST_ROOT        = Paths.get("src/test/java");
private static final String  ROOT_PACKAGE     = "cn/hg/aifei/trace/";
private static final String  WEB_PACKAGE      = "cn/hg/aifei/trace/web/";
private static final int     EXPECTED_PUBLIC_TYPES = 6;
private static final int     EXPECTED_WEB_TYPES    = 2;
private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
        "TraceKit.java", "TraceContext.java", "TraceIdGenerator.java",
        "DefaultTraceIdGenerator.java", "TraceLog.java", "TraceLogFactory.java"));
private static final Set<String> EXPECTED_WEB_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
        "TraceHandler.java", "TraceIdExtractor.java"));
```

### 10.1 断言清单（**10 例**）

| # | 测试方法 | 判定口径 | 风险针对 |
| --- | --- | --- | --- |
| ① | `noThirdPartyReference` | 每个 `.java` 的每条 `import` 必须以 `java.` / `javax.` / `cn.aifei.` / `cn.hg.aifei.trace.` 之一开头（本模块**零第三方**，无放行包） | 第三方依赖蔓延 |
| ② | `frameworkScopeRules` | `import cn.aifei.core.*` → 文件路径必须含 `WEB_PACKAGE`；`import cn.aifei.log.*` → 文件路径必须含 `ROOT_PACKAGE` **且**不含 `WEB_PACKAGE`；`import cn.aifei.*` 其余任何框架包（`plugin` / `aop` / `util` / `core` 之外的）→ 一律失败 | 框架类型例外被滥用 / Plugin 被偷引 |
| ③ | `publicSurfaceExact` | 根包直接子文件数**精确等于 6** 且文件名集合**精确等于** `EXPECTED_ROOT_TYPES`；`web` 直接子文件数**精确等于 2** 且等于 `EXPECTED_WEB_TYPES_SET` | 同名替换 / 悄悄增删公共类型 |
| ④ | `noSqlReference` | 全模块任何 `.java` 不得出现 `import java.sql.` | 零 DB 底线 |
| ⑤ | `noJvmGlobalPropertyWrite` | 不得出现 `System.setProperty` / `System.getProperties().put` / `System.setErr` / `System.setOut` | JVM 全局状态写改 |
| ⑥ | `threadLocalOnlyInTraceContext` | `ThreadLocal` 字面量只允许出现在 `TraceContext.java`（上下文持有单点）；其余任何文件出现即失败 | ThreadLocal 被复制到别处造成第二状态源 |
| ⑦ | `uuidOnlyInDefaultGenerator` | `UUID` 字面量只允许出现在 `DefaultTraceIdGenerator.java`（生成算法单点） | 第二生成算法 / 前缀里夹带生成逻辑 |
| ⑧ | `java8Compatibility` | 不得出现 `List.of(` / `Map.of(` / `Set.of(` / `record ` 声明 / `var ` 局部声明 / text block `"""` / `sealed ` / `instanceof` 模式匹配等 Java 8 后语法与 API | Java 8 兼容底线 |
| ⑨ | `noServiceProvider` | 全模块不得出现 `ServiceLoader`、`META-INF/services` 字面量；`src/main/resources` 下不得存在 `META-INF/services/` 目录 | 无 Plugin / 无 SPI 决策被绕过（暗装自动装配） |
| ⑩ | `testSourcesOfflineOnly` | 扫描 `src/test/java` 全部 `.java`：出现的 `http://` / `https://` 字面量，其 host 只能是 `127.0.0.1` 或 `localhost`（本模块测试本就零联网，断言防蔓延） | 测试联网 |

- **实现要点**：私有工具 `javaFiles(Path)` / `imports(String)` / `normalize(Path)` / `isDirectPackageFile(String, String)`；异常消息须列出**违规文件 + 违规内容**。
- 门禁**不得为兼容错误用法而放宽或绕过**；例外面（决策 2 / 3 的框架类型）已按文件级白名单编码进 ②，新增例外必须先改本文 §2 再改 ②。

---

## 11. 依赖与构建

| 依赖 | 范围 | 用途 |
| --- | --- | --- |
| `cn.aifei:aifei-all:1.1.2` | **`provided`** | 框架内核：`cn.aifei.log.Log` / `LogFactory`（TraceLog / TraceLogFactory 实现）+ `cn.aifei.core.Handler` / `Input` / `Output`（web 包）。**不向下游传递**，消费方必须自带（README 声明）。**不声明 `cn.aifei:aifei-log`**——其 Log/LogFactory 接口在内核 `cn.aifei:aifei` 中，随 aifei-all 提供（已核实本地仓库 1.1.2 内含 `cn/aifei/log/{Log,LogFactory,LogKit}.class`） |
| `junit:junit:4.13.2` | `test` | 单元测试与架构门禁 |

- **零第三方 compile / optional**：不引入任何日志绑定（`slf4j` / `logback` / `log4j` 一律禁止——模块只面向 `cn.aifei.log` 接口）。
- **零 DB**：全模块禁 `import java.sql.`。
- pom 对齐 `aifei-http/pom.xml` 模板：`maven-compiler-plugin`（source/target 1.8、UTF-8）+ `maven-surefire-plugin`（`workingDirectory = ${project.basedir}`，门禁依赖相对路径）。
- **验证命令**（模块根目录）：

```bash
cd aifei-trace
mvn clean verify                                     # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest             # 仅跑架构门禁（10 例）
mvn dependency:tree                                  # 断言：仅 aifei-all(provided) + junit(test)
```

---

## 12. 待明确事项与假设

| # | 事项 | 处理 / 假设 |
| --- | --- | --- |
| 1 | **公共面框架类型例外的根 AGENTS.md 登记** | **已完成**：根 `AGENTS.md`「已登记的公共面框架类型例外」清单已登记本模块两处例外（`TraceLog` / `TraceLogFactory` 暴露 `cn.aifei.log.Log` / `LogFactory`；`web.TraceHandler` / `web.TraceIdExtractor` 暴露 `cn.aifei.core.Handler` / `Input` / `Output`），理由引用本文 §2 决策 2 / 3。pom 依赖口径不受影响（维持 aifei-all provided，非 compile 例外） |
| 2 | **IllegalArgumentException 替代官方 NPE** | 官方三处（setTraceIdGenerator / TraceLog / TraceLogFactory 构造）用 `Objects.requireNonNull` 抛 NPE；本模块统一 `IllegalArgumentException`（§7.3 消息逐字对齐官方 message）。属显式偏离，测试按本表断言 |
| 3 | **运行期替换生成器的可见性** | `idGenerator` 为普通 static 字段（官方同形态），运行期替换无锁；javadoc 与 README 声明「建议仅启动期替换」。不引入 volatile / AtomicReference：替换属一次性启动期动作，为它加同步语义收益为零 |
| 4 | **生成产出不校验** | 替换实现产出格式违例（如非 32 位）时模块按原值绑定、不拦截（决策 7）；格式契约靠 javadoc。若未来需要强校验，属公共行为变更，先改本文再实施 |
| 5 | **`Supplier` 变体的 Java 8 可用性** | `cn.aifei.log.Log` 接口含 `Supplier<String>` 变体（官方底稿已核实）；`java.util.function.Supplier` 是 Java 8 API，兼容无虞。TraceLog 的 Supplier 变体为**必须实现**项（接口方法，非 default 可跳过项） |
| 6 | **Input 无 header 能力** | `cn.aifei.core.Input` 公开接口只有参数读取，无 header 访问（官方 javadoc 已核实）：提取 `X-Trace-Id` 请求头需业务自定义 Input 实现。此边界写入 TraceIdExtractor javadoc 与 README 注意事项，本模块不做任何「万能提取器」 |
| 7 | **静态资源不产生 TraceId** | 静态资源在 undertow IO 线程被资源处理拦截，不进 Dispatcher / Handler 链，天然无绑定无清理需求（官方口径，javadoc 注明） |
| 8 | **demo 不经原型基线门禁** | `aifei-trace-demo` 单页由前端直接实装（同 aifei-http-demo 定档）；不得因页面需求回头要求模块新增公共 API |
| 9 | **「允许差异/共同底线」不适用** | 本模块为单实现模块（无多后端切换），根 `AGENTS.md` 中「多实现契约底线 / 允许差异双清单」条款不适用 |

---

## 13. `aifei-trace-demo`（示例工程，平级目录，不随模块发布）

- **位置**：`aifei-trace-demo/`（仓库根、与 `aifei-trace/` 平级）；端口 **18130**（18110 job-demo / 18120 http-demo 顺延）；依赖 `io.github.gollyhu:aifei-trace:1.0.0` + `cn.aifei:aifei-all`（demo 自带，验证模块 `provided` 口径）+ junit 无关（demo 无测试要求）。
- **形态**：`aifei-http-demo` 同款——零构建静态层单页（`src/main/webapp/view/demo/index.html` + `assets/{js,css}`）+ HIO + `DemoService` + `DemoAppConfig`；`start-demo.bat` / `stop-demo.bat`。
- **演示场景（4 个）**：
  1. **当前请求 traceId / spanId 实时展示**：`DemoService` 经 HIO 返回 `TraceKit.getTraceId()` / `getSpanId()`，页面显示——验证 TraceHandler 绑定与 finally 清理（刷新页面 traceId 每次变化）。
  2. **TraceLogFactory 装配演示**：`DemoAppConfig` 中 `settings.setLogFactory(new TraceLogFactory(new ConsoleLogFactory()))`；`ConsoleLog` / `ConsoleLogFactory` 为 demo 内部极简实现（`System.out` 输出，**不引任何 slf4j/log4j 绑定**）；页面触发一条业务日志并展示控制台输出样本——验证 `[traceId] ` 前缀贯通。
  3. **线程池手动传递演示**：页面触发异步任务，任务内 `TraceKit.init(parentTraceId)` 后返回子线程视角 traceId——验证 §8.3 指引可行性与「同一 traceId」结果。
  4. **提取演示**：demo 的 Input 实现暴露 `X-Trace-Id` 请求头，页面提供「携带自定义 X-Trace-Id 发请求」入口——验证 extractor 提取上游透传 + 空白/缺失回退生成两条路径。
- **离线可跑**：页面零外网请求；启动即用，无外部服务依赖。
- **不经过原型与 QA 基线门禁**（§12 事项 8）；**不得**因页面需求回头要求模块新增公共 API。
- **实现归属**：落盘批次 B6（§14），`aifei-frontend-engineer`。

---

## 14. 文件清单与有序任务分解（落盘批次）

### 14.1 模块源码（14 个文件）

```text
aifei-trace/                                   io.github.gollyhu:aifei-trace:1.0.0（packaging=jar）
├── pom.xml                                    # Java 8；aifei-all provided；junit test；零第三方
├── README.md                                  # 用法 / 能力边界 / 手动传递指引（编码角色写，素材见 §8）
├── AGENTS.md                                  # 模块硬约束（另任务产出，不在本批次）
├── docs/
│   └── design.md                              # ★ 本文（契约事实来源）
└── src/
    ├── main/java/cn/hg/aifei/trace/
    │   ├── TraceIdGenerator.java              # 生成契约接口
    │   ├── DefaultTraceIdGenerator.java       # UUID 去横线默认实现（唯一 UUID 出现点）
    │   ├── TraceContext.java                  # ThreadLocal 唯一持有点
    │   ├── TraceKit.java                      # 全静态门面（init/回退/替换入口）
    │   ├── TraceLog.java                      # Log 装饰器（前缀唯一定义点）
    │   ├── TraceLogFactory.java               # LogFactory 装饰器
    │   └── web/
    │       ├── TraceIdExtractor.java          # 提取契约（@FunctionalInterface）
    │       └── TraceHandler.java              # Handler 链接入（finally 清理）
    └── test/java/cn/hg/aifei/trace/
        ├── ArchitectureBoundaryTest.java      # 门禁 10 例（§10）
        ├── TraceKitTest.java
        ├── TraceContextTest.java
        ├── DefaultTraceIdGeneratorTest.java
        ├── TraceLogTest.java                  # 伪 Log 记录器断言前缀 / 懒求值
        ├── TraceLogFactoryTest.java           # 伪 LogFactory 断言包装
        └── web/
            └── TraceHandlerTest.java          # 伪 Handler 链 + 伪 Input/Output
```

- **不计入本清单**：`aifei-trace-demo/` 位于仓库根、与本模块平级（§13）。
- 测试注意：`TraceKitTest` 替换生成器后必须在 `@After` 恢复 `DefaultTraceIdGenerator.INSTANCE`（测试间隔离）；全部测试零联网、无 `Thread.sleep` 堆叠。

### 14.2 有序落盘批次（每批 ≤ 4 文件）

| 批次 | 文件（按写入顺序） | 依赖 | 优先级 | 建议执行者 | 批次验收 |
| --- | --- | --- | --- | --- | --- |
| **B1** 契约类型（4） | `pom.xml` · `TraceIdGenerator.java` · `DefaultTraceIdGenerator.java` · `TraceContext.java` | — | P0 | `aifei-core-engineer` | `mvn -q -DskipTests compile` 通过；`DefaultTraceIdGenerator` 产出 32 位 `[0-9a-f]`；`TraceContext` null=移除语义自测通过 |
| **B2** 门面与日志桥接（3） | `TraceKit.java` · `TraceLog.java` · `TraceLogFactory.java` | B1 | P0 | `aifei-core-engineer` | init trim/回退、startSpan 覆盖、setTraceIdGenerator 校验行为符合 §5.1；前缀拼装与 Supplier 懒求值符合 §5.5 |
| **B3** web 接入 + 门禁（3） | `web/TraceIdExtractor.java` · `web/TraceHandler.java` · `ArchitectureBoundaryTest.java` | B1、B2 | P0 | `aifei-core-engineer` | 门禁 10 例全绿；handle 执行序（提取→init→透传→finally clear）符合 §5.7 |
| **B4** 核心单测（3） | `TraceKitTest.java` · `TraceContextTest.java` · `DefaultTraceIdGeneratorTest.java` | B2 | P0 | `aifei-core-engineer` | §7.2 场景 1–3 / 7 / 11–13 有用例覆盖；生成器替换用例 `@After` 恢复默认 |
| **B5** 桥接与 web 单测（3） | `TraceLogTest.java` · `TraceLogFactoryTest.java` · `web/TraceHandlerTest.java` | B3、B4 | P0 | `aifei-core-engineer` | §7.2 场景 4–6 / 8–9 覆盖：前缀拼接、TraceId 缺失透出、Supplier 打印时刻求值、提取异常吞掉回退、下游异常原样传播且 finally 清理、null delegate 拒绝 |
| **B6** demo（平级工程，不计入模块） | `aifei-trace-demo/`（`pom.xml` · `DemoAppConfig` · `ConsoleLog`/`ConsoleLogFactory` · HIO 实作 · `DemoService` · `webapp/view/demo/index.html` · `assets/{js,css}` · `start-demo.bat` · `stop-demo.bat`） | B2、B3 | P1 | `aifei-frontend-engineer` | 4 个演示场景可见（§13）；页面零外网请求；端口 18130 |

**硬纪律**：B1–B5 每批完成后**不得**改动公共面签名；若实现期发现签名不可行，**先回写本文 §5** 再改代码（根 `AGENTS.md`：需求与设计冲突时先更新设计决策）。

---

## 15. 验收标准（AC）

| # | 判定条件 | 验证通道 |
| --- | --- | --- |
| AC-01 | 根包顶层 public 类型集合恰等于 6 个、web 包恰等于 2 个 | 门禁 **③ `publicSurfaceExact`**（数量 + 文件名集合精确相等） |
| AC-02 | 除 `aifei-all`（provided）外零第三方依赖；`mvn dependency:tree` 仅两节点 | `dependency:tree` + 门禁 **①** + **②** |
| AC-03 | 全部 class major = 52；无 Java 8 后语法 / API | pom（source/target 1.8）+ 门禁 **⑧** + 构建后 `javap -verbose` 批量断言 |
| AC-04 | `init(null/空白)` 回退生成；`init(" abc ")` 绑定 `"abc"`；返回值恒为最终生效 traceId | `TraceKitTest` |
| AC-05 | `startSpan()` 覆盖旧 spanId 且不影响 traceId；`getSpanId()` 未绑定返回 null | `TraceKitTest` |
| AC-06 | `setTraceIdGenerator(null)` → `IllegalArgumentException`；替换后 `init()` 走新生成器；`getTraceIdGenerator()` 返回当前值 | `TraceKitTest` |
| AC-07 | `DefaultTraceIdGenerator.generate()`：非空、32 位、仅 `[0-9a-f]`、多次生成互不相等 | `DefaultTraceIdGeneratorTest`（固定格式断言） |
| AC-08 | `TraceContext.set(null)` 等价移除；`clear()` 幂等；两 ThreadLocal 独立存取 | `TraceContextTest` |
| AC-09 | `TraceLog`：String 变体拼前缀；Supplier 变体**打印时刻**读上下文（先绑定后打印 vs 先打印后绑定结果不同）；TraceId 缺失时消息原样；`isXxxEnabled` / `getName` 透传；30+5 方法全覆盖 | `TraceLogTest`（伪 Log 记录器） |
| AC-10 | `TraceLogFactory`：三个 getLog 均返回包装 TraceLog；null delegate 拒绝 | `TraceLogFactoryTest`（伪 LogFactory） |
| AC-11 | `TraceHandler`：extractor null（纯生成）；提取值绑定；null/空白回退生成；**提取异常吞掉回退**；下游异常**原样传播**且 **finally 清理**（清后 `getTraceId()==null`）；`next==null` 安全结束 | `TraceHandlerTest`（伪 Handler 链 + 伪 Input/Output） |
| AC-12 | `ThreadLocal` 字面量仅在 `TraceContext`；`UUID` 仅在 `DefaultTraceIdGenerator` | 门禁 **⑥** + **⑦** |
| AC-13 | `cn.aifei.core.*` 仅 web 包；`cn.aifei.log.*` 仅根包 TraceLog/TraceLogFactory；无其他框架包引用 | 门禁 **② `frameworkScopeRules`** |
| AC-14 | 无 SPI / ServiceLoader / `META-INF/services`；无 Plugin | 门禁 **⑨ `noServiceProvider`** |
| AC-15 | 断网可跑全绿；测试源 URL host 仅 `127.0.0.1` / `localhost`；无秒级 `Thread.sleep` | 门禁 **⑩** + CI 断网 `mvn clean verify` |
| AC-16 | demo 4 场景可见、零外网、端口 18130、`start/stop-demo.bat` 可用 | `aifei-trace-demo` 实测（§13） |

---

> **一句话总览**：`io.github.gollyhu:aifei-trace:1.0.0` 单模块 —— 对齐官方 8 公共类型（`TraceKit` / `TraceContext` / `TraceIdGenerator` / `DefaultTraceIdGenerator` / `TraceLog` / `TraceLogFactory` + `web.TraceHandler` / `web.TraceIdExtractor`），无 Plugin、无配置、无 internal、无异常体系；ThreadLocal 上下文唯一持有、UUID 生成唯一算法、前缀拼装唯一定义；日志经 `TraceLogFactory` 装饰任意 LogFactory 贯通，Web 经 `TraceHandler` 绑定/清理；跨模块传播 v1 不做，手动传递指引进 README。
