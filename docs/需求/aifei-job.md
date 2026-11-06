# aifei-job 模块需求（任务输入）

> 本文件是任务输入，不是 PRD / 设计。
> 关联：`docs/需求/aifei-lock.md`（v1.1 集群互斥的运行时依赖）。
> 发布序列：`aifei-cache:1.1.0` → `aifei-lock:1.0.0` → `aifei-job:1.1.0`（集群互斥）。

## 一、背景

`aifei-all:1.1.2` 与现有 7 个生态模块（cache / sentinel / weixin / sa-token / websocket / mq / oss）均无任何调度构件。业务侧的替代都不成立：`new Thread` / `ScheduledExecutorService` 散落各 Service，无法统一配置、观测与停机；裸用 Quartz / XXL-Job 脱离 Plugin 装配范式，XXL-Job 还要额外运维中间件；MQ 延迟消息语义不同（延迟消费 ≠ 定时触发）且强依赖延迟级别。

本模块补齐**应用内定时任务**：业务只写方法 + 一个注解，调度、并发控制、异常隔离、停机收敛由模块统一负责。

调度内核为 JDK `ScheduledThreadPoolExecutor` 上的**单飞自续排模型**：每个任务始终至多一个待触发实例，执行结束后于 `finally` 计算下次延迟并重排——不重叠、不堆叠、不补跑是结构属性，而非附加守卫逻辑。cron 解析与下次触发计算委托 Quartz `CronExpression`（**唯一用途**）。注解扫包与生命周期接入参考官方 `aifei-cron4j` 项目。

## 二、范围

- **本次交付 `io.github.gollyhu:aifei-job:1.0.0`**；集群互斥为 v1.1（发布 `1.1.0`），不在本次范围
- 包名 `cn.hg.aifei.job`；技术基线 Java 8（class major 52）
- 公共 API 零框架类型、零第三方类型；运行时零中间件依赖（不建表、不连 DB，Quartz 仅作 cron 表达式计算库）

**不做**（边界，防范围蔓延）：

1. 工作流 / DAG 编排：任务之间不建立依赖与编排关系。
2. 分布式调度：不做选主、分片、任务分发；v1 也不做执行互斥（v1.1 经 `aifei-lock` 增补，见决策 12）。
3. 数据流 / 批处理框架：不接管大批量读写、分片游标与检查点。
4. 任务管理后台：demo 演示页属示例工程，不是模块能力。
5. 动态脚本任务：任务必须是编译期存在的方法。
6. Quartz 调度运行时：`StdSchedulerFactory` / `Scheduler` / `RAMJobStore` / `JobDetail` / `Trigger`（含 `CronTrigger` / `SimpleTrigger`）/ `JobDataMap` / misfire 策略全不用，不建任何 Quartz 表。
7. v1 不做任务传参：注解无「任务入参」属性（`period` / `delay` / `initialDelay` 属节拍属性，不在此列）、`JobEvent` 无参数字段。

## 三、设计决策

1. **调度引擎 = JDK 自续排，Quartz 降级为 cron 计算器**。唤醒层 `ScheduledThreadPoolExecutor` **固定 1 线程**（任务体只做「提交到执行层」，微秒级返回，不暴露配置）+ 执行层 `ThreadPoolExecutor`。Quartz 定版 `2.3.2`（Java 8 兼容），只使用 `CronExpression#getNextValidTimeAfter`；`local` 包内 `QuartzCronSupport` 是**全模块唯一出现 `org.quartz.*` 的类**，`CronExpression` 实例按任务缓存（构造有解析开销，计算只读且线程安全）。传递依赖排除 `c3p0` 两个变体、`HikariCP-java7`、全部日志绑定，**保留 `slf4j-api`**（Quartz 内部日志门面，排掉会类加载失败），并在 `pom.xml` 中**显式声明为 compile 直接依赖**（不靠 Quartz 传递，避免 Quartz 变动后门面消失）——该门面**只供 Quartz 内部使用，本模块代码不得 `import org.slf4j.*`**。模块自身的「记录」（异常与堆栈、停机续排终止、跳过原因）统一经 aifei 框架日志门面 **`cn.aifei.log.Log`**（`Log.get(本类.class)`），随 `cn.aifei:aifei-all`（provided）提供，不新增依赖、不引入任何日志绑定，禁止 `System.err` 或自建记录通道。**provided 不向消费方传递**：消费方必须自带 `aifei-all`（只引核心 `cn.aifei:aifei` 时 `cn.aifei.log` 不在 classpath，运行期 `NoClassDefFoundError: cn/aifei/log/Log`，早于任何可读错误），与 `aifei-weixin` 的依赖声明口径一致——写入 README 与 `design.md`。日志获取**一律惰性**（不在类的静态初始化期强取，须晚于框架装配 `LogFactory`；`Log.get(...)` 依赖 `LogFactory`，早取会拿到不可用实例）；未装配 `LogFactory` 时按框架既有行为（README 声明，测试须先装配，见第六节）；`design.md` 附「`dependency:tree` 允许出现的传递项清单」。排除清单须在实现期以 `mvn dependency:tree` **实测校准**（不存在的依赖写进 `<exclusions>` 是空操作、不报错，会造成「已排空」的错觉），实测结果回填 `design.md`，本文列举的 `c3p0` 两个变体 / `HikariCP-java7` 以实测为准。Quartz 为 **compile** 依赖**会向消费方传递**：版本冲突由消费方 exclusion 或显式对齐（README 与 design.md 声明）。
2. **注解声明式，3 个注解、公共类型定 7 个**。`@Cron` / `@FixedRate` / `@FixedDelay` 各自语义单义；`initialDelay` 是「首次触发延迟」而非节拍参数，`@FixedRate` / `@FixedDelay` 对称持有、语义一致（只影响首次），`@Cron` 不设（首次触发点由表达式自身定义，两套来源会冲突）；根包公共类型 `JobKit` / `JobEvent` / `JobListener` / `JobException` + 3 注解。`JobEvent` 是任务状态快照（任务名、触发时间、阶段、异常），`final` 类 + 全 `final` 字段 + 无 setter + 无懒加载字段，构造期一次赋值；触发时间为 `long`（epoch 毫秒），不用 `Date` / `Calendar`；除异常 `Throwable` 引用外不可变（`Throwable` 可被 `setStackTrace` / `initCause` 修改，仅作故障记录、不作调度判据，不为其做防御性拷贝）；任务方法与监听器共用同一实例，不另设 `JobContext`；**不设 `JobScheduler` 公共接口**（唯一引擎、无 provider 扩展点、生命周期归 `JobPlugin`，接口无承载方法）。**任务名 = `类全名#方法名`**（如 `com.acme.OrderJob#clean`）：它是 `JobKit.names()` / `state()` 的 key、`JobEvent#taskName()` 的取值与全部错误消息的定位口径，由 `internal` 的唯一定义点生成；注解不提供「任务名」属性（v1 无该能力），同一方法被扫到两次即按任务名判重并启动失败。`JobException` 是**启动期失败的唯一异常类型**（配置非法、扫描与注解 / 方法签名非法、cron 表达式非法、节拍参数越界、任务实例缺失），`final class extends RuntimeException`、构造器包私有（仅模块内抛出，业务只 `catch` 不 `new`）；运行期不抛它（任务异常被捕获记录、续排被拒静默、监听器异常被隔离），完整契约见第五节第 4 小节。
3. **cron 语义 = Quartz 表达式**：6 段含秒，可选第 7 段年；字段与特殊字符 `* , - / ? L W #` 全按 Quartz 语义；校验与下次触发计算一律委托 `CronExpression`，不自实现解析器，**不做 5 段兼容写法**（Quartz 不接受，写错即启动失败）。
4. **同一任务恒串行**：自续排结构保证同任务不重叠、不排队、不堆叠；不提供「允许重叠」开关。
5. **错过触发不补跑**：停机期间未触发的计划点一律跳过，不做补偿执行；唤醒线程因 GC / 系统调度延迟取到已到期任务属**延迟执行**（任务不丢、不记 `SKIPPED`、`lastStart` 取实际开始时刻使节拍随之后移），不是补跑；fixedRate 执行结束时已过去的计划点按跳过规则记 `SKIPPED`（见决策 7）。
6. **任务异常就地隔离**：单个任务抛异常不影响其他任务与后续触发；异常捕获并记堆栈；本轮不重试；续排在 `finally`，异常不断链。
7. **触发与执行分离，调度内核不阻塞唤醒线程**。执行层队列有界，拒绝策略**固定为「不执行 + 抛 `RejectedExecutionException`」**（显式指定 `AbortPolicy` 或等价 handler；「丢弃」指**不执行**，不是**不抛**——不抛则唤醒层收不到信号，该任务永久停摆）。提交方（唤醒线程）捕获该异常后**在同一处完成「记 `SKIPPED` + 就地重排」**，因此「拒绝记账」只有这一处定义点，不另设自定义 handler 记一次账再抛。禁 `CallerRunsPolicy`（长任务占住唯一的唤醒线程，全部任务的到点唤醒停摆）、禁任何**不抛异常**的静默丢弃 handler、禁 `BlockingQueue.put` 等阻塞提交方的调用；「固定 1 线程」的正确性与该拒绝策略互为前提；`catch` 块内的监听器回调异常同样被捕获，不得阻断重排。**续排只有两处**：① 执行结束点（执行层 `finally`，正常路径）；② 提交被拒点（唤醒层捕获 `RejectedExecutionException` 后就地重排）——被拒任务进不了执行层，那个 `finally` 永远不执行，缺 ② 该任务永久停摆。提交被拒时**重置该任务节拍基准为当前时刻**（首次 `initialDelay` 未到即被拒同样适用，不另开分支）。三种触发方式的差异全部收敛到「下次延迟」一个纯函数（入参含 `isFirst`：为真返回 `initialDelay`，为假完全不读该值）。**被拒重排的入参写死**：`isFirst` 置 `false`（首次触发尝试即已消费，不因执行与否而退还；否则首次被拒后每次重排都再读 `initialDelay`，首次触发被无限推迟）、`lastStart` 与 `lastEnd` 同置当次采样的**同一个** `now`（一次取时刻，不得两次读时钟，否则 fixedRate 的周期会累积漂移），随后走同一个 `nextDelay` 纯函数；记 `SKIPPED` 事件的触发时间取「本次原计划触发时刻」，与重置基准的 `now` 是两个不同时刻，不得混用。**三类 `SKIPPED` 的事件时间口径统一**：提交被拒取原计划触发时刻；fixedRate 超时跳过取 `lastStart + period`；停机 `shutdownNow()` 剩余任务取其原计划触发时刻。事件时间一律不晚于新基准 `now`，避免观测中出现「跳过时间晚于下次触发时间」的倒挂。该保证只覆盖调度内核自身，不含监听器回调耗时（见决策 14）。
8. **三处细节**：`lastStart` = 任务体**实际开始执行**的时刻（队列等待不计入，`STARTED` 回调在其之前，见决策 14；**唯一例外**是决策 7 的被拒重置，此时它只是节拍起点，该任务本轮并未执行）；停机时在途任务 `finally` 续排必须捕获 `RejectedExecutionException` 并静默终止（记「停机续排终止」，不得外抛覆盖任务自身异常）；执行层 `shutdown()` 后已提交任务继续执行属预期。
9. **优雅停机**：`stopping` 标志置位 → 唤醒层 `shutdown()`（停止新触发）→ 执行层 `shutdown()` → `awaitTermination(job.shutdown.timeoutMillis)` → 超时 `shutdownNow()`，返回的未执行任务记 `SKIPPED` 并留日志。任务须自行响应中断，模块不保证强制终止（README 声明）。
10. **任务实例与方法校验**：扫描到的任务所在类**一律从 aifei 容器按类型取**，取不到即启动失败（不做反射 `new` + 手工注入兜底；取出的是代理对象，事务 / 日志生效）。容器确有**多例（prototype）语义**，故**多例 Bean 视为配置错误**并启动失败——多例实例每次取出都不相同，任务状态与节拍会散落在不同对象上；判定通道取容器提供的单例 / 多例标记，判定点唯一在 `internal`，不在任务扫描与监听器解析两处各写一套（与 `JobListener` 可选取用形成对照，见决策 13）。注解方法必须为 `public` 实例方法、非 `static`、返回 `void`、参数为 `()` 或 `(JobEvent)`，否则启动失败并指出方法全名；`job.scan.packages` 内多个包存在包含关系（如 `cn.foo` 与 `cn.foo.bar`）时同一方法可能被扫到多次——按任务名判重，**重复即启动失败**（不静默去重，避免掩盖配置冗余）。
11. **v1 不支持任务传参**：注解无「任务入参」属性（`period` / `delay` / `initialDelay` 属节拍属性，不在此列）、`JobEvent` 无参数字段；README 声明参数由业务经自身注入的 Service / 配置获取。后续若支持须先过设计决策（涉及参数类型学）。
12. **集群互斥（v1.1，本次不做）**：多实例部署时同一任务由各实例竞争一把执行锁（依赖 `aifei-lock`，scope `optional`），抢到者执行。可用性两层、都 fail-fast 不降级：**依赖存在性**由 `job.cluster.enabled=true` 时 `Class.forName` 探测（optional 不传递，普通引用类加载期即 `NoClassDefFoundError`）；**后端可用性**由 `aifei-lock` 的 `LockPlugin` 启动校验负责，与 `job.cluster.enabled` 无关，本模块不重复校验。锁语义完全由 `aifei-lock` 负责，本模块不复制、也不自拼 cache 的 `putIfAbsent`。**v1 不留桩代码与占位配置**（v1.1 会修改 `JobPlugin.start()`，非纯新增）。v1.1 待定档两项：超时任务的租约与续期责任（`aifei-lock` 无看门狗，业务拿不到句柄，须由集群实现包内部持有句柄续期，或放弃互斥记事件；含 `job.cluster.*` 的 `lease` 配置键）；失败语义（抢锁失败记 `SKIPPED`；`LockException` 结果未知，记 `FAILED` 留堆栈，不得吞成「抢不到」）。
13. **`JobListener` 为可选的单监听器，按类型从容器取**。`JobPlugin.start()` 按 `JobListener.class` 从 aifei 容器取一次并持有引用（与决策 10 取任务实例同一通道），取不到即视为「无监听器」——**不启动失败、不升级为配置错误**；`stop()` 释放引用。这与任务实例「取不到即启动失败」是**两条不同语义**：任务实例缺失属配置错误，监听器缺失属正常（观测为可选设施）。**监听器为多例 Bean 时同样视为配置错误并启动失败**：`start()` 只取一次并长期持有该引用，多例会让事件散落到不同实例、监听器自身状态也不可依赖（判定通道与决策 10 同一实现）。**v1 不支持多个监听器**：框架按类型注册，同类型重复注册抛 `Singleton object already exists`（`aifei-cache` 用例已确证该路径可达），且框架无 `@Order` / 按名通道，多实现既注册不进也无排序依据；需要多路分发的业务自行实现一个 `JobListener` 做组合转发。`JobKit` 因此不新增注册方法，仍是 `state()` / `names()` 两个只读方法。
14. **`JobEvent` 不可变 + 监听器同步回调**。`JobEvent` 为 `final` 类，字段全 `final`、无 setter、无懒加载 / 缓存字段，构造期一次赋值；触发时间用 `long`（epoch 毫秒），不得使用 `java.util.Date` / `Calendar`（可变类型，`final` 字段挡不住）。不承诺深度不可变：唯一可变引用是异常 `Throwable`，仅作故障记录、不参与调度判据。`JobKit.state()` 每次调用构建新的不可变 `Map` 与新 `JobEvent` 值，不返回内部记录的实例，也不返回 `Collections.unmodifiableMap` 包装的活视图。`JobListener#onEvent` **同步调用、运行在产生该事件的线程上**，不提供异步派发，无监听队列与监听线程池：`STARTED` / `SUCCESS` / `FAILED` 与 fixedRate 超时跳过产生的 `SKIPPED` 在执行层线程；提交被拒的 `SKIPPED` 在唤醒层线程；停机 `shutdownNow()` 剩余任务的 `SKIPPED` 在 `stop()` 调用线程。因此监听器必须快速返回、无阻塞 IO / 长计算、线程安全（不同任务的事件可并发到达同一实例）、不依赖线程局部变量；同一任务的阶段事件按 `STARTED → 终态` 顺序串行到达。**`STARTED` 在 `lastStart` 采样之前发出**：进入执行后先采样时刻构造并同步回调 `STARTED`（该时刻即事件触发时间），回调返回后再采样 `lastStart` 并调用任务体——监听器耗时因此不计入 `lastEnd - lastStart`，不参与 fixedRate 的「耗时超过间隔」判定，也不改变 fixedDelay 的结束基准；**两个时刻不要求复用同一个 `now`**：事件时间只服务观测，`lastStart` 只服务节拍计算，二者差值即监听器耗时，属预期而非实现偏差；顺序取此而非「采样后发事件」，因为 `lastStart` 的语义是「任务体开始」，反过来会让慢监听器间接改变跳过行为，违背监听器不得影响调度。**监听器回调异常完全隔离于状态机**：异常被捕获并记录，不影响任务执行、终态记账与续排，不改变 `state()` 观测结果；终态事件一律**先记账再回调**（回调内调用 `state()` 已能看到本次终态），`STARTED` 本就不记账。监听器耗时由使用方承担，模块不做回调超时中断，README 显式警告「慢监听器会拖慢全部任务的到点触发」。

## 四、功能需求

本节与第五节的「启动即失败」一律抛 `JobException`（契约见第五节第 4 小节），不再逐一注明。

| 能力 | 说明 |
| --- | --- |
| cron 触发 | `@Cron("0 0/5 * * * ?")`，Quartz 表达式；非法表达式启动即失败，指出方法全名与表达式原文 |
| 固定频率 | `@FixedRate(period = 60000, initialDelay = 0)`，以上次**开始**（任务体实际执行）为基准；超时后按规则重算，不补跑 |
| 固定延迟 | `@FixedDelay(delay = 60000, initialDelay = 0)`，以上次**结束**为基准；与 fixedRate 共用执行链，仅「下次延迟」计算不同 |
| 首次触发延迟 | `initialDelay` 只影响首次触发（基准时刻 = `JobPlugin.start()` 完成注册的时刻），之后不参与任何节拍计算；`0` 表示立即触发；`@Cron` 不设该参数（表达式自身定义首次触发点）；负数启动即失败 |
| 执行事件 | `JobEvent`：任务名、触发时间（`long` epoch 毫秒）、阶段（`STARTED` / `SUCCESS` / `FAILED` / `SKIPPED`）、异常；`final` 类 + 全 `final` 字段 + 无 setter，除异常 `Throwable` 引用外不可变；任务方法可选接收 `STARTED` 快照，监听器收全阶段 |
| 执行监听 | `JobListener#onEvent(JobEvent)` 单方法回调，**同步执行在产生事件的线程上**；监听器为可选单例，`start()` 从容器按类型取一次，取不到即无监听器（不启动失败，见决策 13）；须快速返回、无阻塞操作、线程安全；回调内异常必须被捕获，不得影响调度 |
| 只读观测 | `JobKit.state()` / `JobKit.names()`（见第五节） |
| 并发控制 | 自续排，同任务恒串行，无「允许重叠」开关 |
| 触发与执行分离 | 唤醒层固定 1 线程 + 执行层 `job.pool.size`；长任务不影响其他任务触发精度 |
| 执行池拒绝 | 队列满时记 `SKIPPED`、重置节拍基准、唤醒层就地重排；绝不阻塞唤醒线程 |
| 优雅停机 | `JobPlugin.stop()`：停止新触发、等待在途至超时、超时中断并记录 |

**配置键 4 个**（构造期只持 `Prop` 引用，解析与校验全部在 `start()`；违规即 `JobException`，不留半装配状态）：

| 键 | 必填 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `job.scan.packages` | 是 | — | 逗号分隔、每项 `trim()`；空值或含空项即失败（对齐 `cache.names` 规则） |
| `job.pool.size` | 否 | `Runtime.getRuntime().availableProcessors()` | 执行层线程数，≥ 1 |
| `job.pool.queue` | 否 | `256` | 执行层有界队列容量，≥ 1；默认值只适合开发 / 低负载环境，**正式环境须按「任务数 × 单批堆积量」评估后取一个更大值**（过小会让到点任务频繁被拒并记 `SKIPPED`） |
| `job.shutdown.timeoutMillis` | 否 | `10000` | 停机等待上限，≥ 0（`0` = 不等待，直接 `shutdownNow()`） |

**注解属性**：`@Cron.value` 必填且非空；`@FixedRate.period` / `@FixedDelay.delay` 必填、≥ 1 毫秒（注解默认 `0` 视为未设置，启动失败）；`initialDelay` 默认 `0`、≥ 0。时间单位一律毫秒。

**P1（本次不做）**：集群互斥（v1.1）、Quartz 持久化 JobStore / 集群模式、XXL-Job 等后端、「允许重叠」开关、任务传参、运行时动态注册 / 取消、手动触发、执行历史持久化、失败重试与告警、任务分片、分组与优先级、日历式排除。

## 五、关键契约

### 1. 触发与执行语义（写入 README 与 `docs/design.md`）

| 场景 | 约定 |
| --- | --- |
| fixedRate 结束时发现 period 已过 | 记一条 `SKIPPED`（事件时间取原计划触发时刻 `lastStart + period`，非当前时刻）、以当前时刻为新基准立即重排，已过触发点不补跑 |
| 触发点因停机错过 | 停机期间的计划点全部跳过，重启后不补跑 |
| 唤醒线程因 GC / 系统调度延迟取到已到期任务 | 照常提交执行（延迟执行，非补跑、不记 `SKIPPED`），`lastStart` 取实际开始时刻使节拍随之后移 |
| `initialDelay` 未到即停机 / 错过该点 | 不触发、不补跑、不记账（从未执行过的任务不进入 `state()`） |
| 首次提交即被拒 | 同后续周期口径：记 `SKIPPED` + 节拍基准重置为当前时刻 + `isFirst` 置 `false`（不重复消费 `initialDelay`），不另开分支 |
| 任务耗时超过间隔 | 结束后按「上次开始 + 间隔」重算；已过时间点不补跑，按跳过规则记账 |
| 任务抛异常 | 捕获 + 记录（含堆栈）；不影响其他任务与后续触发；本轮不重试；续排不受影响 |
| 执行池队列满（提交被拒） | 唤醒层捕获 `RejectedExecutionException` → 记 `SKIPPED` → 重置节拍基准 → **唤醒层就地重排**；唤醒线程不阻塞，异常仅在内部流转、不向调用方外抛 |
| 任务在队列等待 | 等待**不计入** `lastStart` |
| 应用停机 | 停止新触发，等待在途任务；超时后中断并记录未完成 |
| 同一方法多个触发注解 | 配置错误，启动即失败（避免语义歧义） |
| 非法 cron 表达式 | 启动即失败，指出方法全名与表达式原文 |

### 2. 集群语义（v1.1 生效，v1 不适用）

- 「不重复执行」依赖执行互斥，**不是分布式调度**：没有选主、任务分片、执行归属迁移。
- 执行锁基于 `aifei-lock` 租约：**锁过期仍可能重复执行，业务任务必须自行幂等**。
- v1 无该能力，多实例各跑一次；README 必须显式声明「v1 不支持多实例互斥」。
- 开启 `job.cluster.enabled` 须**显式引入** `aifei-lock` 及其依赖的 `aifei-cache` ≥1.1.0（optional 不向消费方传递，否则类加载期 `NoClassDefFoundError`——这也是必须 `Class.forName` 探测的原因）。

### 3. 只读观测面

- `JobKit.state()`：`Map<String, JobEvent>` **不可变**、按任务名排序；value 为该任务**最近一次终态**（`SUCCESS` / `FAILED` / `SKIPPED`）快照，`STARTED` **既不作结果也不清除记录**——任务执行中（`STARTED` 已发、终态未发）保留上一次终态，观测口径是「最后已知结果」，执行中的任务不会暂时消失；首次执行且尚无终态的任务在执行中仍不出现（只有终态才让任务进入观测面），因此 `state()` 无法区分「正在执行」与「空闲等待」，执行中视图只能由监听器事件流提供；每次调用构建新的不可变 `Map` 与新 `JobEvent` 值，不返回内部记录的实例、不返回活视图（见决策 14）；从未执行过的任务不出现。
- `JobKit.names()`：已注册任务名的不可变 `Set<String>`，区分「已注册未执行」与「未注册」。
- 静态引用由 `JobPlugin.start()` 写入、`stop()` 清除，通道为 `internal.JobHolder`（对齐 `aifei-mq` 的 `MqHolder` 范式）；`JobKit` 只读、不对外暴露 setter。**未启动时两个方法返回空集合不抛异常**（停机后观测口径不破裂）。

### 4. 启动期异常契约

`JobException`（公共面 7 类型之一，`final class extends RuntimeException`，构造器包私有）是**启动期失败的唯一异常类型**，覆盖：`job.*` 配置非法（缺失必填键、取值越界）、`job.scan.packages` 为空或扫不到任何任务、注解与注解方法签名非法（`static` / 有返回值 / 参数不是 `()` 或 `(JobEvent)`）、同一方法多个触发注解、cron 表达式非法、`initialDelay` / `period` / `delay` 越界、任务实例从容器取不到或为多例、同一方法被扫到多次。

- **消息必含定位信息**：方法全名（`类全名#方法名`）、非法键名与原值、表达式原文；底层异常（如 Quartz 解析异常）保留为 `cause`。
- **启动期只用它**：不复用 `IllegalArgumentException` / `IllegalStateException`（与 `aifei-cache` 用前者、`aifei-lock` 用后者的差异在此显式声明）——启动失败横跨配置 / 扫描 / 注解 / 表达式四类，用单一模块异常让消费方可一次捕获，且公共面已含该类型，不另设。
- **运行期不抛它**：任务体异常、续排被拒、监听器回调异常一律就地捕获记录，不外抛（见决策 6 / 7 / 13）。

## 六、非功能与硬约束

- **Java 8**：语法与 API 双约束；主 JAR 内 class major 52。
- **公共 API 极简、实现无关**：零 `cn.aifei.*`、零第三方类型（含零 `org.quartz.*`）。
- **每类规则唯一定义点**：任务名生成（`类全名#方法名`）、表达式校验、下次触发延迟计算（三种注解的唯一分叉点，`initialDelay` 只在该函数读取一次，其余位置不得出现）、跳过判定（含三类 `SKIPPED` 的事件时间口径）、拒绝记账（唤醒层 `catch` 处唯一入口）、节拍基准重置（含 `isFirst` 置 `false` 与同一 `now` 采样，决策 7 是唯一入口）、状态记录（终态先记账再回调，顺序唯一）、`JobEvent` 构造（不可变装配点）、监听器解析（决策 13 唯一入口，取一次后不再访问容器）、监听器回调的阶段分发与异常捕获、配置域常量、日志获取方式（一律 `Log.get(本类.class)`，不经 `org.slf4j.*`），实现类不得复制。
- **架构门禁 `ArchitectureBoundaryTest`**（源码文本扫描，须在模块根目录执行，不得放宽）：
  - `org.quartz.*` 只允许出现在 cron 薄封装类（`local` 包）
  - `cn.aifei.*`（框架类型）只允许出现在 `aifei` 接入包；**唯一例外**：`cn.aifei.log.*` 允许出现在任意包（模块自身的记录通道，见决策 1）
  - `org.slf4j.*` 全模块禁止（只由 Quartz 内部使用，见决策 1）
  - 全模块禁止 `java.sql.*`
  - 根包**顶层**公共类型数量精确 7（嵌套类型如 `JobEvent.Phase` 不单独计数）
  - 接入包存在 `JobPlugin`
- **依赖**：`cn.aifei:aifei-all` provided（含 `cn.aifei.log`，模块自身的记录走它；**provided 不传递 → 消费方必须自带 `aifei-all`**，只引核心 `cn.aifei:aifei` 时运行期 `NoClassDefFoundError: cn/aifei/log/Log`）；Quartz 2.3.2 compile（仅用 `CronExpression`）+ `slf4j-api` compile（仅 Quartz 内部日志门面，显式直接依赖，见决策 1），除这两项外无其它第三方运行时依赖，排空与传递声明见决策 1；不引入任何日志**绑定**。
- **表达式用例** ≥ 20 条：合法 / 非法（6 段不合法、字段越界、字符不支持）、月末、闰年、跨年、`?` `L` `W` `#` 语义。**期望值一律以硬编码时刻字面量书写**：用例内不得用被测实现（或其内部同一 `CronExpression` 调用）推导期望值，否则断言恒真、锁不住语义；用例只应在「固定预期」与「实现输出」之间比对。
- **测试须先装配日志工厂**：`Log.get(...)` 依赖 `LogFactory`，非集成用例在 `@BeforeClass` 调 `LogKit.get().setLogFactory(...)` 装配 Noop 实现（参考 `aifei-weixin` 的 `support/NoopLogFactory`），否则日志路径 NPE；测试替身不引入任何日志绑定依赖。
- **测试不依赖真实时钟等待与精确时序**：下次触发延迟用纯计算断言（`nextDelay` 为纯函数）；触发 / 执行语义用 `CountDownLatch` + 宽松超时（如 5s），period 只用于「至少触发两次」类宽松断言；禁秒级 `Thread.sleep`、禁断言「第 N 次触发发生在 X ms 内」。
- **`initialDelay` 用例**：纯计算断言 `isFirst` 为真返回 `initialDelay`、为假完全忽略该值；`initialDelay` 为负 → 启动失败并指出方法全名与参数值；语义用例用 `CountDownLatch` + 宽松超时断言首次不早于 `initialDelay`，且第二次起间隔仍约等于 `period`（证明只影响首次）。
- **不可变与监听器用例**：`state()` 返回的 `Map` 与 value 均不可修改，多次调用返回不同实例（证明不暴露内部实例与活视图）；监听器在产生事件的线程同步执行（用例内比对当前线程，不依赖线程名）；同一监听器实例被多个任务并发回调不冲突；监听器抛异常不影响任务结果与后续触发；同一任务事件按 `STARTED → 终态` 顺序到达。
- **`STARTED` 顺序与耗时隔离用例**：监听器内阻塞（`CountDownLatch`）期间任务体未开始执行（证明 `STARTED` 先于任务体同步回调）；`STARTED` 事件时间不晚于 `lastStart`；监听器耗时不计入 fixedRate 耗时判定（纯计算断言 `nextDelay` 只依赖 `lastStart` / `lastEnd`）。
- **监听器异常与记账隔离用例**：`SUCCESS` / `FAILED` / `SKIPPED` 的回调抛异常后，`state()` 仍为本次终态、后续触发照常。
- **记账顺序用例**：终态回调内调用 `JobKit.state()`，断言已是本次终态；`STARTED` 回调内调用，断言为上一次终态，首次执行时该任务不在 `state()` 中（证明 `STARTED` 不记账、终态先记账再回调）。
- **监听器可选与解析用例**：容器无 `JobListener` 时应用正常启动、任务照常触发；容器有实现时全部事件送达；`start()` 只解析一次（启动后再注册的监听器不生效）；监听器为多例 Bean → 抛 `JobException` 启动失败。
- **`state()` 观测口径用例**：任务首次执行中（尚无终态）不出现在 `state()`；已有终态的任务再次执行中仍返回上一次终态（`STARTED` 既不覆盖也不清除）；执行结束后的终态覆盖生效。
- **被拒重排入参用例**：`initialDelay` 非 0 的任务首次即被拒（预先占满队列），断言下一次延迟按 `period` / `delay` 计算而非再次 `initialDelay`，且连续被拒不产生 `initialDelay` 累加（证明 `isFirst` 已置 `false`）；断言重排后 `lastStart` 与 `lastEnd` 取自同一时刻（两者相等）；断言 `SKIPPED` 事件的触发时间取原计划触发时刻、不等于基准重置的 `now`。
- 模块内 `mvn clean verify` 0 failure / 0 error。
- **文档分工**：`README.md`（用法与语义声明）、`docs/design.md`（契约事实来源）、`docs/testing.md`（测试策略）、`AGENTS.md`（开发约束）。注释简体中文，公共类型与公共成员写全 javadoc，原创文件统一版权头。

## 七、必须交付的固定配套

| 类别 | 交付物 |
| --- | --- |
| 构建 | `pom.xml`（Java 8、aifei-all provided（含 `cn.aifei.log`）、Quartz 2.3.2 compile + slf4j-api compile（仅 Quartz 内部门面、无绑定）+ 传递依赖排空） |
| 公共面 | 根包 7 类型：`JobKit` / `JobEvent` / `JobListener` / `JobException` + `@Cron` / `@FixedRate` / `@FixedDelay`；`JobKit` 只有 `state()` / `names()` 两个只读方法 |
| 内部 | `internal`：配置装载、注解扫描与方法签名校验、下次触发延迟纯函数、任务注册表、跳过 / 拒绝 / 状态记账、`JobEvent` 不可变构造、监听器解析（单例、容器按类型取一次、缺失即无监听器）与同步分发（含回调异常捕获）、`JobHolder`（`JobKit` 写入通道）、唤醒层与执行层（有界队列 + 拒绝记账 + 被拒后就地重排） |
| 实现 | `local.QuartzCronSupport`（唯一 `org.quartz.*` 出现点，`CronExpression` 按任务缓存） |
| 接入 | `aifei.JobPlugin`（唯一 import aifei 插件装配类型的类；`cn.aifei.log.*` 按门禁例外可出现在任意包） |
| 测试 | `ArchitectureBoundaryTest` 门禁 + 契约用例 + 表达式用例 + 跳过 / 拒绝 / 异常隔离 / 停机 / 触发执行分离 / 不可变与监听器用例 |
| 示例 | `aifei-job-demo`（自写 HIO，端口 18110，`start-demo.bat` / `stop-demo.bat`）：**先交静态演示页面原型**（任务列表、到点触发、跳过、异常隔离、停机收敛的直观视图，单页 HTML + 假数据），确认后再生成完整 Demo |
| 文档 | `docs/design.md`、`docs/testing.md`、`README.md`、`AGENTS.md`；根 `README.md` 模块总览补条目 |

demo 静态原型的验收不是「页面能打开」，而是视图覆盖语义观测点：任务列表（名称 / 最近触发时间 / 阶段）、到点触发（高亮，执行中视图取监听器事件流，`state()` 不承载运行态）、跳过（`SKIPPED` 可见）、异常隔离（一个任务失败时其他仍触发）、停机收敛（等待中 / 已中断）；清单写入 `docs/testing.md`。

**「触发方式」与「下次触发时间」由 demo 端按自身配置自算**：这是示例工程的行为，不是模块能力——公共面只提供 `state()`（最近终态）与 `names()`，不提供、也不承诺这两项，demo 不得为此要求模块新增公共字段或 API。

v1.1 增补（本次不交付）：`cluster` 实现包（基于 `aifei-lock` 的执行互斥）与对应门禁断言、fail-fast 用例、决策 12 的两项定档结论。

## 八、参考资产

- **官方 `aifei-cron4j`**：注解声明 + Plugin 扫包注册 + 容器随应用生命周期启停的接入范式（只借接入形态）
- **Quartz 表达式计算**：`CronExpression` 构造校验与 `getNextValidTimeAfter(Date)`（只此一个 API）
- **门禁骨架**：`aifei-websocket` 的 `ArchitectureBoundaryTest`
- **Plugin 装配与生命周期**：`aifei-websocket` 的 `WebSocketServerPlugin`
- **门面与 holder 范式**：`aifei-mq` 的 `MqHolder` / `ProducerKit`
- **回调保护范式**：`aifei-mq` 监听器异常隔离
- **锁语义与能力声明（v1.1）**：`docs/需求/aifei-lock.md`
- **demo 组织方式**：`aifei-mq-demo`；全体底线：仓库根 `AGENTS.md`

## 九、验收标准

- [ ] 模块目录内 `mvn clean verify` 通过（0 failure / 0 error）。
- [ ] `ArchitectureBoundaryTest` 全部断言通过：根包顶层公共类型精确 7，`org.quartz.*` 只在 `local` 包，无 `java.sql.*`。
- [ ] 表达式用例 ≥ 20 条，含非法表达式、月末 / 闰年 / 跨年与 `?` `L` `W` `#` 语义。
- [ ] 「注册 → 触发 → 执行」闭环可完整单测跑通，不依赖真实时钟等待与精确时序。
- [ ] 语义用例覆盖：任务重叠不产生新实例、fixedRate 超时跳过记账、错过不补跑、异常隔离、优雅停机等待、注解重复启动失败、非法表达式启动失败。
- [ ] `initialDelay` 用例覆盖：只作用于首次（第二次起间隔不受影响）、负数启动失败、首次提交被拒与后续周期同口径。
- [ ] 触发与执行分离用例：一个长任务不阻塞其他任务的到点唤醒。
- [ ] 执行池拒绝用例：队列占满时唤醒线程不被阻塞、本轮记 `SKIPPED`、不产生提交空转，且**该任务在队列腾空后的下一周期仍被触发**（证明唤醒层已就地重排，未永久停摆）；并断言拒绝信号确实以 `RejectedExecutionException` 到达唤醒层（拒绝策略未静默丢弃）。
- [ ] 停机用例：在途任务续排被拒时不外抛异常、不留未捕获堆栈。
- [ ] 方法签名校验用例：`static` / 有返回值 / 非法参数 → 抛 `JobException` 并指出方法全名；**任务所在类为多例 Bean → 抛 `JobException`**；多个扫描包存在包含关系导致同一方法被扫两次 → 抛 `JobException`（见决策 10）。
- [ ] 启动期异常契约用例：配置非法、扫描包为空、非法 cron、`initialDelay` / `period` / `delay` 越界、同一方法多注解均抛 `JobException`（非 `IllegalArgumentException` / `IllegalStateException`），消息含定位信息（方法全名 / 键与原值 / 表达式原文），底层异常在 `getCause()`。
- [ ] 任务名契约：`JobKit.names()` / `state()` 的 key 与 `JobEvent#taskName()` 一律为 `类全名#方法名`，与错误消息中的定位口径一致。
- [ ] `JobKit` 未启动时 `state()` / `names()` 返回空集合且不抛异常。
- [ ] 不可变与监听器用例覆盖：`state()` 的 `Map` 与 value 均不可修改、每次调用返回新实例；监听器同步执行于产生事件的线程、并发回调不冲突、抛异常不影响调度与后续触发；同一任务事件顺序为 `STARTED → 终态`。
- [ ] `state()` 观测口径用例覆盖：首次执行中不出现、再次执行中保留上一次终态、执行结束后终态覆盖生效。
- [ ] 监听器耗时与异常隔离用例覆盖：`STARTED` 先于任务体、事件时间不晚于 `lastStart`、监听器耗时不参与 fixedRate 判定；终态回调抛异常后 `state()` 与续排不受影响，回调内 `state()` 可见本次终态。
- [ ] 监听器注册用例覆盖：容器无 `JobListener` 时应用正常启动、任务照常触发；有实现时全部事件送达；`start()` 只解析一次；监听器为多例 Bean → 启动失败。
- [ ] 记账顺序用例覆盖：终态回调内 `state()` 已为本次终态；`STARTED` 回调内为上一次终态，首次执行时该任务不在 `state()` 中。
- [ ] 被拒重排用例覆盖：`isFirst` 置 `false`（不重复消费 `initialDelay`、连续被拒不累加）、`lastStart` / `lastEnd` 为同一 `now` 采样、`SKIPPED` 事件时间取原计划触发时刻。
- [ ] 公共面零框架类型、零第三方类型；`mvn dependency:tree` 仅出现 Quartz 与设计文档列明允许的传递项，无日志绑定；`aifei-all` 为 provided（不传递），README 已声明消费方须自带。
- [ ] 日志装配：模块代码零 `org.slf4j.*` 引用（门禁断言），日志经 `cn.aifei.log.Log` 且获取晚于 `LogFactory` 装配（不在类静态初始化期强取）；非集成用例先装配 Noop `LogFactory`。
- [ ] `README.md` 含「触发与执行语义表」「v1 不支持多实例互斥」「任务参数由业务自行获取」「监听器同步回调、慢监听器会拖慢全部任务触发」「监听器可选且单例，须自行注册进容器（未注册则无任何事件）」「日志经 aifei 框架 `Log`（未装配 `LogFactory` 时按框架既有语义）」「**消费方须自带 `aifei-all`**（本模块对它是 provided，不传递）」七处声明。
- [ ] `docs/design.md` / `docs/testing.md` / `README.md` / `AGENTS.md` 齐备且与实现一致。
- [ ] demo 静态原型按观测点清单确认；完整 Demo 可启动（端口 18110）并覆盖 cron / fixedRate / 异常隔离 / 停机收敛。
- [ ] v1.1（本次不验收）：集群互斥用例、`aifei-lock` 缺失时 `Class.forName` 探测失败用例、租约与续期责任定档结论。

## 十、执行方式

- 本文件为任务输入，按仓库根 `AGENTS.md` 的文档分工与模块约束执行。
- 建议实施顺序：`QuartzCronSupport` + 下次延迟纯函数（可独立测试）→ 注解扫描与方法签名校验 → 唤醒层 / 执行层与语义收敛（跳过 / 拒绝 / 异常 / 停机，决策 7、8 的细节一并落地，不留 TODO）→ demo 静态页面原型（确认）→ 完整 Demo。
- 集群互斥（v1.1）依赖 `aifei-lock` 落地（其前置 `aifei-cache:1.1.0` 提供条件操作与能力声明，见 `aifei-lock.md` 第二节），不阻塞 v1。
