# aifei-job

给 aifei 应用补齐**应用内定时任务**：业务只写一个方法 + 一个注解，调度、并发控制、异常隔离与停机收敛由模块统一负责。

- **适合**：应用内的周期任务（固定频率 / 固定延迟）与 cron 定点任务；需要统一配置、统一观测与优雅停机的场景。
- **不适合**：分布式调度（选主 / 分片 / 任务分发）、工作流与 DAG 编排、批处理框架、任务管理后台、动态脚本任务、任务传参（v1 均不做）。
- 坐标：`io.github.gollyhu:aifei-job:1.0.0`（单 `jar`，**库模块**，不是应用工程）
- 包名：`cn.hg.aifei.job`｜技术基线：**Java 8**（语法与 API 双约束，主 JAR class major = 52）
- 调度内核：JDK `ScheduledThreadPoolExecutor` 上的**单飞自续排模型**——每个任务始终至多一个待触发实例，执行结束后重算下次延迟并重排，**不重叠、不堆叠、不补跑是结构属性**；cron 解析委托 Quartz `CronExpression`（唯一用途）

## 目录

- [1. 快速开始](#1-快速开始)
- [2. 触发方式与执行语义](#2-触发方式与执行语义)
- [3. 配置参考](#3-配置参考)
- [4. 观测：JobKit](#4-观测jobkit)
- [5. 监听器：JobListener](#5-监听器joblistener)
- [6. 依赖与日志](#6-依赖与日志)
- [7. 注意事项](#7-注意事项)

---

## 1. 快速开始

### 1.1 加依赖

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-job</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 框架内核：本模块对它是 provided，不传递，必须由你的应用自带 -->
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-all</artifactId>
    <version>1.1.2</version>
</dependency>
```

### 1.2 写配置

`app-config.txt` 里加（`job.scan.packages` 是**唯一必填键**）：

```properties
job.scan.packages = com.acme.job
```

### 1.3 装配插件

```java
public class AppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Plugins plugins) {
        plugins.add(new JobPlugin(PropKit.use("app-config.txt")));
    }
}
```

构造函数只保存 `Prop` 引用：**不解析、不校验、不建线程池**；`job.*` 的解析与校验、扫描与装配全部在 `start()` 完成。

### 1.4 写任务

任务 = 一个容器内的**单例 Bean** + 一个被注解的**方法**：

```java
@Service
public class DemoJob {

    // cron：6 段含秒（可选第 7 段年），Quartz 语义
    @Cron("0 0/5 * * * ?")
    public void tick() {
        // 每 5 秒执行一次
    }

    // 固定频率：以上次"开始"为基准；initialDelay 只影响首次
    @FixedRate(period = 3000, initialDelay = 2000)
    public void poll(JobEvent started) {
        // 需要触发时刻时接住 JobEvent（STARTED 快照），否则写无参方法即可
    }

    // 固定延迟：以上次"结束"为基准
    @FixedDelay(delay = 5000)
    public void flush() {
        // 上次执行结束 5 秒后再执行
    }
}
```

任务名自动生成为 `类全名#方法名`（如 `com.acme.job.DemoJob#tick`），它是 `JobKit.names()` / `JobKit.state()` 的 key，也是全部错误消息的定位口径。

### 1.5 确认跑通

应用启动后，`JobKit.names()` 应列出已注册任务；任务到点执行后 `JobKit.state()` 出现该任务最近一次终态。要看到「执行中」这种过程视图，请实现一个 `JobListener`（见 [5](#5-监听器joblistener)）。

---

## 2. 触发方式与执行语义

### 2.1 三个注解

| 注解 | 属性 | 语义 | 首次触发基准 |
| --- | --- | --- | --- |
| `@Cron` | `value`（必填） | Quartz 表达式（6 段含秒，可选第 7 段年） | 表达式自身定义（**不设** `initialDelay`） |
| `@FixedRate` | `period`（≥ 1 ms，必填）、`initialDelay`（≥ 0，默认 0） | 以上次**开始**为基准 | `initialDelay` |
| `@FixedDelay` | `delay`（≥ 1 ms，必填）、`initialDelay`（≥ 0，默认 0） | 以上次**结束**为基准 | `initialDelay` |

- 注解默认值 `0` 对 `period` / `delay` 视为「未设置」，**启动失败**；时间单位一律**毫秒**。
- `initialDelay` 是**首次触发延迟**，不是节拍参数：`@FixedRate` / `@FixedDelay` 对称持有、语义一致，**只影响首次**；`@Cron` 不设（首次触发点由表达式自身定义，两套来源会冲突）。
- 三个注解都没有「任务名 / 分组 / 优先级 / 允许重叠 / 入参」属性。

### 2.2 触发与执行语义表

| 场景 | 约定 |
| --- | --- |
| **同一任务重叠** | **恒串行**：同一任务任何时刻至多一个待触发实例，不重叠、不堆叠、不排队；**没有**「允许重叠」开关 |
| **错过不补跑** | 停机期间未触发的计划点**一律跳过，不做补偿执行**；重启后也不补 |
| fixedRate 执行结束时 `period` 已过 | 记一条 `SKIPPED`（事件时间取原计划触发时刻 `lastStart + period`，不是当前时刻），以当前时刻为新基准立即重排，已过触发点**不补跑** |
| 执行池队列满（提交被拒） | 唤醒层捕获 `RejectedExecutionException` → 记 `SKIPPED` → 重置该任务节拍基准为当前时刻 → **唤醒层就地重排**；唤醒线程全程不阻塞、异常不向调用方外抛 |
| 首次提交即被拒 | 与后续周期同口径，不另开分支（`isFirst` 置 `false`，不重复消费 `initialDelay`） |
| `initialDelay` 未到即停机 / 错过该点 | 不触发、不补跑、不记账（从未执行过的任务不进入 `state()`） |
| 唤醒线程因 GC / 系统调度延迟取到已到期任务 | 照常提交（**延迟执行，不是补跑、不记 `SKIPPED`**），`lastStart` 取实际开始时刻，节拍随之后移 |
| 任务耗时超过间隔 | 结束后按「上次开始 + 间隔」重算；已过时间点不补跑，按跳过规则记账 |
| **任务抛异常** | **就地隔离**：捕获并记录堆栈（含 `Error`），不影响其他任务与后续触发；本轮不重试；续排不受影响 |
| 任务在队列等待 | 等待**不计入** `lastStart`（`lastStart` = 任务体实际开始执行的时刻） |
| 应用停机 | 停止新触发 → 等待在途任务至 `job.shutdown.timeoutMillis` → 超时 `shutdownNow()`，未执行任务记 `SKIPPED` 并留日志 |
| 同一方法多个触发注解 / 非法 cron | 配置错误，**启动即失败**（指出方法全名与表达式原文） |

`SKIPPED` 三类事件的时间口径统一为「原计划触发时刻 / `lastStart + period`」，一律不晚于新基准，所以观测里不会出现「跳过时间晚于下次触发时间」的倒挂。

### 2.3 任务方法签名约束

| 规则 | 违规结果 |
| --- | --- |
| 必须 `public` 实例方法、非 `static`、返回 `void` | 启动失败并指出方法全名 |
| 参数为 `()` 或 `(JobEvent)` | 同上 |
| 同一方法只能有一个触发注解 | 启动失败（避免语义歧义） |
| 同一任务名只能出现一次 | 启动失败（多个扫描包存在包含关系时同一方法会被扫两次，**不静默去重**） |
| 任务所在类必须是容器内的**单例** Bean（`@Service` 等） | 取不到 → 启动失败；多例（prototype）→ 启动失败 |

- 任务实例一律**从 aifei 容器按类型取**（取出的是代理对象，事务与日志生效），不做反射 `new` 兜底。
- 只注册**被扫到类自身声明**的方法（继承 / 接口默认实现上的注解不会被注册）。

### 2.4 任务参数：由业务自行获取

**v1 不支持任务传参**：注解没有「任务入参」属性，`JobEvent` 也没有参数字段。任务的输入由业务**经自身注入的 Service / 配置获取**：

```java
@Service
public class ReportJob {

    @Inject ReportService reportService;   // 参数从自己的 Service / 配置里拿

    @Cron("0 0 2 * * ?")
    public void build() {
        reportService.buildDaily();
    }
}
```

`period` / `delay` / `initialDelay` 是**节拍属性**，不属于任务参数。

### 2.5 v1 不支持多实例互斥

**v1 不支持多实例互斥**：多实例部署时同一个任务在**每个实例上都会各跑一次**。因此**业务任务必须自行幂等**（按业务唯一键去重 / 用条件更新兜底），不要假设「同一时刻只有一台机器在跑」。

执行互斥计划在 v1.1 经 `aifei-lock` 增补（租约到期仍可能重复执行，幂等要求不变）；v1 不提供任何相关配置键、桩代码与占位开关。

---

## 3. 配置参考

| 键 | 必填 | 默认值 | 规则 / 说明 |
| --- | --- | --- | --- |
| `job.scan.packages` | **是** | — | 逗号分隔、每项 `trim()`；空值或含空项即启动失败 |
| `job.pool.size` | 否 | `Runtime.getRuntime().availableProcessors()` | 执行层线程数，`≥ 1` |
| `job.pool.queue` | 否 | `256` | 执行层有界队列容量，`≥ 1`。**默认 `256` 只适合开发 / 低负载环境，正式环境须按「任务数 × 单批堆积量」评估后取一个更大的值**；过小会让到点任务频繁被拒并记 `SKIPPED` |
| `job.shutdown.timeoutMillis` | 否 | `10000` | 停机等待上限，`≥ 0`（`0` = 不等待，直接 `shutdownNow()`） |

线程模型（**不可配置**，是语义的一部分）：

- **唤醒层**：`ScheduledThreadPoolExecutor`，**固定 1 线程**（`aifei-job-wake`），只做「提交到执行层」，微秒级返回 —— 长任务不会拖慢其他任务的到点唤醒。
- **执行层**：`ThreadPoolExecutor`，`job.pool.size` 线程（`aifei-job-worker-N`）+ `job.pool.queue` 有界队列，拒绝策略固定为「不执行 + 抛 `RejectedExecutionException`」（`AbortPolicy`）。
- 两层线程均为 **daemon**：插件未 `stop()` 时不拖住 JVM 退出。

---

## 4. 观测：JobKit

```java
Set<String> names = JobKit.names();           // 已注册任务名：不可变、按任务名排序
Map<String, JobEvent> state = JobKit.state(); // 各任务最近一次终态：不可变、按任务名排序
```

- `JobKit` **只有这两个只读方法**，不提供注册 / 触发 / 取消入口，也不对外暴露 setter。
- **未启动或已停机时返回空集合，不抛异常**（停机窗口轮询观测面不会开始抛错）。
- `state()` **只承载最近一次终态**（`SUCCESS` / `FAILED` / `SKIPPED`），**不承载运行态**：`STARTED` 既不作结果也不清除记录，所以执行中的任务保留上一次终态、不会从视图里消失；而**首次执行且尚无终态的任务在执行中仍不出现**。因此 `state()` 无法区分「正在执行」与「空闲等待」，**执行中视图只能由监听器事件流提供**。
- 每次调用都返回**新的**不可变 `Map` 与新的 `JobEvent` 值，不返回内部记录实例、不返回活视图。
- `names()` 用于区分「已注册但从未执行」与「根本没有注册」。

`JobEvent` 的字段：`taskName()`（`类全名#方法名`）、`triggerTime()`（`long`，epoch 毫秒）、`phase()`（`STARTED` / `SUCCESS` / `FAILED` / `SKIPPED`）、`error()`（仅 `FAILED` 非 `null`）。公共面**不提供**触发方式与「下次触发时间」，需要时请业务侧自行计算。

---

## 5. 监听器：JobListener

```java
@Service
public class DemoJobListener implements JobListener {

    @Override
    public void onEvent(JobEvent event) {
        // 必须快速返回：不要在这里做阻塞 IO、长计算或同步等待
    }
}
```

- **监听器可选且是单例**：容器里没有 `JobListener` 实现时应用正常启动、任务照常触发，只是没有任何事件送达方（**不启动失败**）。多例（prototype）Bean → **启动失败**。
- **须自行注册进容器**（如 `@Service`）：本模块不会替你 `new`；**未注册则无任何事件**。`start()` 按类型从容器取**一次**并长期持有 —— 启动后再往容器注册新实现**不会生效**。
- v1 **不支持多个监听器**：框架按类型注册，同类型重复注册会抛 `Singleton object already exists`，且没有 `@Order` / 按名通道。需要多路分发时，自行实现**一个** `JobListener` 做组合转发。
- **同步回调**：`onEvent` 运行在**产生该事件的线程**上（执行层线程 / 唤醒层线程 / `stop()` 调用线程），没有异步派发、没有监听队列与监听线程池。因此必须：快速返回、无阻塞 IO 与长计算、线程安全（不同任务的事件可并发到达同一实例）、不依赖线程局部变量。
- **慢监听器会拖慢全部任务的到点触发**：`STARTED` 回调在任务体之前同步执行，而唤醒层只有 1 个线程 —— 一次缓慢回调会推迟所有任务的到点唤醒。模块**不做回调超时中断**，耗时完全由使用方承担。
- 回调异常被捕获并记录，**不影响**任务执行、终态记账与续排，也不改变 `state()` 的观测结果。
- 顺序：同一任务的事件按 `STARTED → 终态` 串行到达；终态**先记账再回调**（所以在终态回调里读 `JobKit.state()` 已能看到本次终态），`STARTED` 不记账（在 `STARTED` 回调里读到的是上一次终态，首次执行时该任务还不在 `state()` 中）。

---

## 6. 依赖与日志

### 6.1 依赖

| 依赖 | 范围 | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei-all:1.1.2` | `provided` | 框架内核（`Plugin` / `Aop` / `Prop` / `Scanner` / `Log`）；**不向消费方传递** |
| `org.quartz-scheduler:quartz:2.3.2` | `compile` | 仅用于 `CronExpression`（表达式校验 + 下次触发计算）；**会向消费方传递** |
| `org.slf4j:slf4j-api:1.7.30` | `compile` | **仅 Quartz 内部日志门面**；模块代码零 `org.slf4j.*` 引用 |

- **消费方须自带 `aifei-all`**：本模块对它是 `provided`，**不传递**。只引核心 `cn.aifei:aifei` 时 `cn.aifei.log` 不在 classpath，运行期会以 `NoClassDefFoundError: cn/aifei/log/Log` 失败（早于任何可读错误）——看到这个错误先查依赖。
- **Quartz 2.3.2 会向消费方传递**：若你的应用已有其它 Quartz 版本，请自行 `exclusion` 或**显式对齐版本**。Quartz 只被当作「表达式解析器 + 时刻计算器」使用，**不启动任何调度器、不建表、不连库**（`Scheduler` / `JobDetail` / `Trigger` / `JobDataMap` / JobStore / misfire 策略一概不用）。
- Quartz 的三个连接池 / JDBC 传递项（`com.mchange:c3p0` / `com.mchange:mchange-commons-java` / `com.zaxxer:HikariCP-java7`）已被本模块排空，不会传给你的应用。
- 消费方**不得**把本模块带来的 `slf4j-api` 当作自身日志门面（本模块不承诺其日志语义）。
- 本模块**不引入任何日志绑定**（`slf4j-simple` / `logback` / `log4j` / `commons-logging` 一律没有）。

### 6.2 日志：经 aifei 框架 `Log`

模块自身的记录（任务异常与堆栈、跳过原因、停机续排终止）统一经 aifei 框架 `Log`（`cn.aifei.log.Log`，`Log.get(本类.class)`），不使用 `System.err` / `System.out`，也没有自建记录通道。

**未装配 `LogFactory` 时按框架既有语义**：框架默认的 `NoLogFactory` 每次调用都抛 `IllegalStateException`；本模块带 `slf4j-api` 时框架通常探测到 `Slf4jLogFactory`（无绑定 → SLF4J 打印一次 NOP 警告、日志静默）。要看到模块日志，请按你自己的日志方案装配 `LogFactory`。

---

## 7. 注意事项

### 7.1 启动期失败一律 `JobException`

`JobException` 是**启动期失败的唯一异常类型**（`final class extends RuntimeException`），**由模块内部抛出，业务方只 `catch`、不要 `new`**。

覆盖：`job.*` 缺失或越界、扫描包为空或扫不到任务、注解与方法签名非法、同一方法多个触发注解、cron 非法、`initialDelay` / `period` / `delay` 越界、任务实例取不到或为多例、同一任务名重复、监听器为多例。

- 消息**必含定位信息**（方法全名 / 键名与原值 / 表达式原文 / 任务名），底层异常（如 Quartz `ParseException`）保留在 `getCause()`。
- 任一环节失败都会**回滚**：已启动的调度停止、观测面清空，**不留半装配状态**，`JobKit` 回到「空集合」。
- 唯一例外：`JobPlugin` 构造期 `prop == null` 抛 `IllegalArgumentException`（调用方编程错误，早于 `start()`）。
- **运行期不抛它**：任务体异常、续排被拒、监听器回调异常一律就地捕获记录。

### 7.2 停机：任务须自行响应中断

停机顺序固定：置停机标志 → 唤醒层停止新触发 → 执行层 `shutdown()`（已提交任务继续执行）→ 等待至 `job.shutdown.timeoutMillis` → 超时 `shutdownNow()` 并把未执行任务记 `SKIPPED`。

- 停机期间**未到点**的待触发实例直接丢弃、不记账（属「停机期间错过」，不补跑）。
- **模块不保证强制终止**：超时后只能靠**中断**提示在途任务。任务体必须自行检查中断标志 / 响应 `InterruptedException` 并及时返回，否则该任务会一直跑下去（线程是 daemon，「进程退出即停」）。
- 执行层 `shutdown()` 之后**已提交任务继续执行**属预期行为。

### 7.3 cron 只认 Quartz 6 段写法

6 段含秒、可选第 7 段年；字段与特殊字符 `* , - / ? L W #` 全按 Quartz 语义。**不做 5 段兼容写法**（Quartz 不接受，写错即启动失败）。

### 7.4 观测面不含「触发方式」与「下次触发时间」

公共面只有 `JobKit.state()`（最近终态）与 `JobKit.names()`（已注册任务名），`JobEvent` 也没有 `period` / `cron` / `nextFireTime` 字段。需要这两项请在业务侧按自己的配置自算（示例工程 `aifei-job-demo` 即如此）。

### 7.5 参考

- 设计契约（事实来源）：`docs/design.md`
- 开发约束（面向开发代理）：`AGENTS.md`
- 测试策略与用例清单：`docs/testing.md`
