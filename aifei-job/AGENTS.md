# aifei-job 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

四份文档的分工：`README.md` 面向使用者（安装、用法、配置、语义声明）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **v1 不留任何 v1.1 桩代码与占位配置**：不建 `cluster` 包、不声明 `job.cluster.*` 键、不写 `TODO` / 被注释掉的代码 / 预留未使用参数。

- **改代码前必读 `docs/design.md`**。需求与现有设计冲突时，先明确并更新设计决策，再改代码。
- 未经设计决策，不得自行确定契约类行为（触发与执行语义、跳过 / 拒绝 / 停机口径、异常类型与消息、配置键名、任务名格式等），也不得新增或扩大公共 API 面。

## 硬性约束

### 技术基线

- 代码、依赖与 API 必须兼容 **Java 8**：**语法**与 **API 双约束**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching 等 Java 8 之后语法；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 之后 API。
- 主 JAR 内全部 class 的 major version 必须为 **52**。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；文件头版权块属归属信息，不得改写。

### 依赖纪律

- **`cn.aifei:aifei` 为 `provided`**：消费方自带，不向下游传递。只引核心 `cn.aifei:aifei` 时 `cn.aifei.log` 不在 classpath，运行期 `NoClassDefFoundError: cn/aifei/log/Log`——README 必须声明「消费方须自带 `aifei`」。
- **`org.quartz-scheduler:quartz:2.3.2` 为 `compile`**：仅用于 cron 表达式计算（`CronExpression`），**会向消费方传递**（版本冲突由消费方 `exclusion` 或对齐，README 声明）。除 `CronExpression` 外**不得**使用 Quartz 任何其它类型（`Scheduler` / `JobDetail` / `Trigger` / `JobDataMap` / `JobStore` / misfire 策略全禁）。
- **`org.slf4j:slf4j-api:1.7.30` 为 `compile`**：**仅 Quartz 内部日志门面**，显式直接声明；**模块代码零 `org.slf4j.*` 引用**（门禁③）。
- **不引入任何日志绑定**（`slf4j-simple` / `logback` / `log4j` 系一律禁止）。
- 除上述两项第三方依赖外**不新增任何直接依赖**；传递依赖排除清单与允许清单见 `docs/design.md` §10.2 / §10.3（**已实测回填**，2026-11-10：Maven 3.9.16 / JDK 21.0.11，主 JAR 仍为 class major 52）。
- **`<exclusions>` 只允许写实测真实存在的传递项**：当前 3 条（`com.mchange:c3p0` / `com.mchange:mchange-commons-java` / `com.zaxxer:HikariCP-java7`）经去掉 exclusion 前后对照实测均为**有效排除**；`c3p0:c3p0` 旧 groupId 变体在 Quartz 2.3.2 中**不存在**，故**不得**写入（空操作 exclusion 会造成「已排空」的错觉）。新增 exclusion 前必须先以 `mvn dependency:tree` 证明该项存在。
- **实测允许清单（5 个构件）**：`cn.aifei:aifei:1.1.2`(provided，版本由根 BOM 管理) / `org.quartz-scheduler:quartz:2.3.2`(compile) / `org.slf4j:slf4j-api:1.7.30`(compile) / `junit:junit:4.13.2`(test) / `org.hamcrest:hamcrest-core:1.3`(test)；除此外出现任何条目即依赖失控。**判定口径**：无任何日志绑定（`slf4j-simple` / `logback` / `log4j` / `commons-logging` 等 0 项），无任何 `java.sql` 相关传递项（JDBC 驱动 / 连接池 0 项，且门禁④保证零 `import java.sql.`）。
- 该两项 `compile` 依赖须在**仓库根 `AGENTS.md`** 的「已登记的 `compile` 例外」处登记（`docs/design.md` §10.5）。

### 日志纪律

- 模块自身的记录（异常与堆栈、停机续排终止、跳过原因、拒绝、超时）统一经 aifei 框架门面 **`cn.aifei.log.Log`**（`Log.get(本类.class)`）。
- **`cn.aifei.log.*` 是唯一允许出现在任意包的框架类型**（门禁②例外），不得据此例外引入其它 `cn.aifei.*` 类型到非 `aifei` 包。
- **实测例外清单（只有这 4 个文件 import `cn.aifei.log.*`，全在 `internal` 包）**：`internal/JobRunner.java`、`internal/JobWakeTask.java`、`internal/JobScheduler.java`、`internal/JobEvents.java`（对应 §5.3 第 12 条「日志获取方式」的持有者）。新增第 5 处属行为变更：须先确认该处确实需要记录，并同步 `docs/design.md` §5.1 / §5.3。
- **`aifei` 包内引用其它 `cn.aifei.*` 的只有两个文件**：`aifei/JobPlugin.java`（`plugin.Plugin` / `scanner.Scanner` / `util.Prop`）与 `aifei/AifeiJobContainer.java`（`aop.Aop` / `aop.AopKit` / `aop.Singleton`）。其它包（根包 / `internal` / `local`）一律零 `cn.aifei.*`（`cn.aifei.log.*` 除外）。
- **日志一律惰性获取**：静态工具类在**方法内**取用；实例类在**构造内 / 字段初始化期**取用；**禁止** `static` 字段持有 `Log`（框架默认 `NoLogFactory` 每次调用都抛 `IllegalStateException`，静态初始化期强取会退化为 `ExceptionInInitializerError`）。
- 禁止 `System.err` / `System.out` 打印、禁止自建记录通道、禁止引入日志绑定。

### 公共面收敛（不得放宽）

- **公共 API 零第三方类型、零框架类型**（含零 `org.quartz.*`）。
- 根包公共类型固定为 **7 个**：`JobKit` / `JobEvent` / `JobListener` / `JobException` / `Cron` / `FixedRate` / `FixedDelay`。新增公共类型必须先过设计决策。
- `JobKit` **只有** `state()` / `names()` 两个只读方法，不对外暴露 setter 与注册方法。
- `internal` / `local` / `aifei` 均属**非承诺 API**，消费方不得引用。
- 公共面（根包）不得出现 `internal` 包行话（唯一定义点、注册表、唤醒层、执行层、续排等）。
- **`JobException` 构造器虽为 `public`，业务方只 `catch` 不 `new`**；唯一构造点是 `internal.JobErrors`（全模块唯一 `new JobException` 出现处），不得在其它类里直接 `new JobException(...)`。

## 包边界与门禁

`ArchitectureBoundaryTest`（**6 例**，`src/test/java/cn/hg/aifei/job/ArchitectureBoundaryTest.java`）扫描 `src/main/java` 全部 `.java`（源码文本断言）。**必须在模块根目录 `aifei-job/` 执行**（依赖相对路径 `src/main/java`），**不得为兼容错误用法而放宽或绕过**：

| # | 断言 |
| --- | --- |
| ① | 第三方 / 其它模块 import 只允许出现在 `local` 包，且必须是 `org.quartz.*`（`org.quartz.*` **只在** `local`） |
| ② | `cn.aifei.*`（除本模块 `cn.hg.aifei.job.*`）只允许出现在 `cn.hg.aifei.job.aifei`；**唯一例外** `cn.aifei.log.*` 允许出现在**任意**包 |
| ③ | 全模块禁止 `import org.slf4j.` |
| ④ | 全模块禁止 `import java.sql.` |
| ⑤ | 根包**顶层**公共 `.java` 文件**精确 7 个**且文件名集合精确相等（`JobEvent.Phase` 为嵌套类型，不单独计数） |
| ⑥ | 接入包 `cn/hg/aifei/job/aifei/` 存在且含 `JobPlugin.java` |

新增包（例如未来 v1.1 的 `cluster`）若引入新的第三方类型，**先**更新 `docs/design.md` §11 的门禁表并说明理由，再改断言。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 任务名生成（`类全名#方法名`） | `internal.JobNames#of` |
| cron 表达式校验与下次触发计算（唯一 `org.quartz.*` 触点） | `local.QuartzCronSupport`（`internal.CronCalculator` 契约） |
| 下次触发延迟纯函数（三触发方式唯一分叉点；`initialDelay` 只读一次） | `internal.JobSchedule#nextDelay` |
| fixedRate 超时跳过判定与跳过事件时间 | `internal.JobSchedule#overduePlanTime` |
| 三类 `SKIPPED` 的事件时间口径 | 被拒 / 停机取 `plannedTime`（`JobWakeTask` / `JobRunner` 的 `plannedTime`）；超时取 `overduePlanTime` 返回值 |
| 拒绝记账（`RejectedExecutionException` → `SKIPPED`） | `internal.JobWakeTask#run` 的 `catch` 块（**唯一入口**，不另设自定义 `RejectedExecutionHandler` 记账） |
| 节拍基准重置（`isFirst=false` + 同一 `now` 采样） | `internal.JobWakeTask` 的同一 `catch` 块 |
| 状态记录顺序（终态先记账再回调，`STARTED` 不记账） | 调用顺序在 `internal.JobRunner`，只收终态在 `internal.JobState#record` |
| `JobEvent` 构造（不可变装配点） | `internal.JobEvents#assemble`（内部只经 `JobEvent#of`） |
| 监听器解析（按类型取一次、缺失即无监听器、多例即失败） | `internal.JobInstances#optionalSingle` |
| 监听器分发与回调异常捕获 | `internal.JobEvents#dispatch` |
| 容器实例获取与多例判定实现 | `internal.JobInstances`；容器语义映射在 `aifei.AifeiJobContainer#isSingleton`（先 `AopKit.get().getAopFactory().getMappingClass(type)` 解析实际类型，再读该类型上的 `@Singleton`，未声明回落 `AopKit.get().isSingleton()`——与 `AopFactory#doGet` 逐条一致，见 `docs/design.md` §5.2） |
| 配置域常量、默认值与解析校验 | `internal.JobConfig` |
| 任务名判重 | `internal.JobRegistry` 构造期 |
| 续排投递（含「无下次触发」放弃） | `internal.JobScheduler#reschedule` |
| 日志获取方式 | 各持有日志的类（方法内 / 构造内 `Log.get(本类.class)`） |

## 行为契约要点速查

> 完整契约见 `docs/design.md` §6；本清单只列易被写错的口径。

- **调度模型**：唤醒层 `ScheduledThreadPoolExecutor` **固定 1 线程**（只做提交，微秒级返回）+ 执行层 `ThreadPoolExecutor`（`job.pool.size` 线程 + `job.pool.queue` 有界队列）。**禁止**把唤醒线程池改成 >1、禁止 `CallerRunsPolicy`、禁止静默丢弃的拒绝 handler、禁止 `put` 等阻塞提交。
- **同任务恒串行**：同一任务恒至多一个待触发实例；不提供「允许重叠」开关。
- **续排只有两处**：执行结束点（`JobRunner` 的 `finally`）与提交被拒点（`JobWakeTask` 的 `catch`）。缺一处该任务会永久停摆。
- **被拒重排入参写死**：`isFirst = false`；`lastStart` 与 `lastEnd` 同置**同一个** `now`（一次取时刻，不得两次读时钟）。
- **三类 `SKIPPED` 事件时间**：提交被拒 = 原计划触发时刻；fixedRate 超时 = `lastStart + period`；停机 `shutdownNow` 剩余任务 = 其原计划触发时刻。事件时间一律**不晚于**新基准 `now`。
- **`lastStart`** = 任务体**实际开始执行**的时刻（队列等待不计入；`STARTED` 回调在其之前）；唯一例外是被拒重置（此时它只是节拍起点）。
- **停机顺序固定**：`stopping` → 唤醒层置「不执行已排队延迟任务」策略后 `shutdown()` → 执行层 `shutdown()` → `awaitTermination(job.shutdown.timeoutMillis)` → 超时 `shutdownNow()` 并把返回的未执行任务记 `SKIPPED` 留日志 → 唤醒层 `shutdownNow()` 兜底。**注意**：`ScheduledThreadPoolExecutor#shutdown()` 默认仍会执行已排队的延迟任务，必须先 `setExecuteExistingDelayedTasksAfterShutdownPolicy(false)`。
- **停机丢弃口径**：执行层队列中已到点未执行的 `JobRunner` 记 `SKIPPED`；唤醒层队列中**未到点**的 `JobWakeTask` 直接丢弃、**不记账**（属「停机期间错过」）。
- **异常隔离**：任务体异常（含 `Error`）就地捕获记录、本轮不重试、`finally` 续排不断链；`InvocationTargetException` 须解包为 `error`。
- **监听器**：可选单例、`start()` 只取一次、缺失即无监听器（不启动失败）、多例即启动失败；**同步回调在产生事件的线程上**；回调异常被隔离；终态**先记账再回调**；`STARTED` 在 `lastStart` 采样**之前**发出。
- **`JobKit` 未启动 / 已停机**：`state()` / `names()` 返回**空集合且不抛**（有意偏离根 `AGENTS.md`「未装配即失败」，理由见 `docs/design.md` §4.4；该让步只覆盖这两个只读方法）。
- **启动期只用 `JobException`**：不复用 `IllegalArgumentException` / `IllegalStateException`；消息必含定位信息（方法全名 / 键名与原值 / 表达式原文 / 任务名），底层异常保留为 `cause`。**运行期不抛它**。**唯一例外**是 `JobPlugin` 构造期 `prop == null` 抛 `IllegalArgumentException`（调用方编程错误，早于 `start()`，与 `MqPlugin` / `WebSocketServerPlugin` 一致，见 `docs/design.md` §4.3）。
- **配置解析时机**：全部在 `JobPlugin.start()`；构造函数只持 `Prop` 引用，不解析、不校验、不建线程池；失败即回滚（`scheduler.stop()` + `JobHolder.clear()`），不留半装配。
- **配置读取唯一口径**：键名常量只在 `internal.JobConfig`，其它类不得硬编码 `"job.pool.size"` 等字面量。
- **方法发现**：只扫被扫到类**自身声明**的方法（`getDeclaredMethods()`），跳过 `isSynthetic()` / `isBridge()`；签名须 `public` 实例方法、非 `static`、返回 `void`、参数为 `()` 或 `(JobEvent)`。
- **候选类发现**：只能用框架 `cn.aifei.scanner.Scanner`（仅 `aifei` 包），规则识别仍在 `internal.JobScanner`。

## 注释规范

- **语言**：一律简体中文。保留英文原文的只有 javadoc 标签名、代码标识符、配置键名、异常消息。
- **javadoc**：公共类型与公共成员必须书写，并写全 `@param` / `@return` / `@throws`；`internal` / `local` 成员在非自明处书写；`aifei` 包公共类写全。`JobException` 的 javadoc **必须**显著声明「由模块内部抛出，业务方只 `catch` 不 `new`」。
- **精简**：只在代码无法表达「为什么」时书写注释，不复述下一行代码，不写行尾注释，不留被注释掉的死代码 / TODO / 占位符。
- **时序类口径**（`STARTED` 事件时间与 `lastStart` 的先后、`SKIPPED` 事件时间取哪个时刻）必须写注释说明**为什么**，因为它是评审最容易改错的地方。

## 验证

**命令在 `aifei-job/` 根目录执行**（门禁测试依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                              # 必须 0 failure / 0 error
mvn test -Dtest=QuartzCronSupportTest         # 单用例：类名或 类名#方法名
mvn dependency:tree -Dverbose                 # 依赖核对：与 docs/design.md §10.3 允许清单逐行比对（实测 8 节点 / 6 构件、零日志绑定）
```

- 测试**不依赖真实时钟等待**、**禁秒级 `Thread.sleep`**；语义用例一律 `CountDownLatch` + 宽松超时（≤ 5s）。
- 测试须先装配日志工厂：`@BeforeClass` 调 `LogKit.get().setLogFactory(new NoopLogFactory())`（`support/` 下的替身，**不得**改其版权头）。
- **实测计数待回填**：实现完成后把 `Tests run / Failures / Errors` 写回 `docs/testing.md` §7 与本文件。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 语义声明），并更新 `docs/testing.md`。
- README 必须始终包含这七处声明：触发与执行语义表、**v1 不支持多实例互斥**、任务参数由业务自行获取、监听器同步回调且慢监听器会拖慢全部任务触发、监听器可选且单例（须自行注册进容器，未注册则无任何事件）、日志经 aifei 框架 `Log`（未装配 `LogFactory` 时按框架既有语义）、**消费方须自带 `aifei`**。
- 术语必须与 `docs/design.md` §2 统一：任务 / 任务名 / 触发方式 / 节拍 / 唤醒层 / 执行层 / 待触发实例 / 续排 / 终态 / 观测面。不得另起叫法（如「作业」/「定时器」/「调度器实例」）。
