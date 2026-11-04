# aifei-job 模块需求（任务输入）

> 本文件是**任务输入**，不是 PRD / 设计。第三节「已拍板决策」为当前生效版本（2026-11-04 四次修订已定档），进入设计后不得回退；第九节给出全部拍板结论与变更留档，可直接进入 `docs/design.md`。
>
> 关联文件：`docs/需求/aifei-lock.md`（分布式锁；**v1.1** 集群互斥的运行时依赖，见第三节第 5 条与第八节）。
>
> 变更留档：
> - **2026-11-04 二次拍板**——本地调度引擎改为 Quartz，cron 交由 Quartz 表达式，集群互斥改依赖 `aifei-lock`，交付 demo 静态页面原型。
> - **2026-11-04 三次拍板**——Quartz **降级为 cron 计算器**（不再使用其 Scheduler / Trigger / JobStore），调度改 JDK 自续排单飞模型；**保留**触发与执行分离（唤醒层 + 执行层）；**删除** `JobScheduler` 公共接口；`JobListener` 收敛为单方法 `onEvent(JobEvent)`；**v1 整块删除集群互斥**，随 `aifei-lock` 作为 v1.1 增补；根包公共类型定档 **7**。
> - **2026-11-04 四次修订**——线程池三处细节定档：执行层**有界队列 + 拒绝即记账**（禁止阻塞唤醒线程）、`lastStart` = 任务体实际开始执行时刻、停机续排必须吞掉 `RejectedExecutionException`；补齐 `JobKit` 只读面；显式声明 v1 不支持任务传参；配置键 3 → 4（增 `job.pool.queue`）。
> - **2026-11-04 五次修订（三文档交叉评审补充）**——① 提交被拒路径的续排责任明确到**唤醒层就地重排**（原「续排唯一位于执行结束点」会让被拒任务永久停摆）；② v1.1 的 fail-fast 拆成「依赖存在性（`Class.forName` 探测）」与「后端可用性（`LockPlugin` 自身启动校验）」两层；③ 补充 Quartz `compile` 会传递给消费方的声明；④ `JobKit` 的写入通道定档为 `internal.JobHolder`；⑤ 新增「v1.1 待定档」（租约取值与续期责任）。

## 一、背景

`aifei-all:1.1.2` 实际包含的模块为：`aifei`（内核 / 路由 / AOP）、`aifei-log`、`aifei-json`、`aifei-proxy`、`aifei-db`（ActiveRecord）、`aifei-enjoy`（模板）、`aifei-undertow`（HTTP 服务）。**没有任何调度构件。**

生态现状：`aifei-cache`、`aifei-sentinel`、`aifei-weixin`、`aifei-sa-token`、`aifei-websocket`、`aifei-mq`、`aifei-oss`，**也没有调度能力**。

业务现状的替代都不理想：

| 替代做法 | 为什么不成立 |
| --- | --- |
| 业务里直接 `new Thread` / `ScheduledExecutorService` | 散落在各 Service 中，无法统一配置、统一观测、统一停机 |
| 自行裸用 Quartz / XXL-Job | 与 aifei 的 Plugin 装配范式脱节；XXL-Job 还需额外运维中间件 |
| 用 MQ 延迟消息代替 | 语义不同（延迟消费 ≠ 定时触发），且强依赖 MQ 与延迟级别 |

本模块补齐「应用内定时任务」能力：业务只写方法 + 一个注解，调度、并发控制、异常隔离、停机收敛由模块统一负责。

调度内核为 JDK `ScheduledThreadPoolExecutor` 上的**单飞自续排模型**：每个任务始终最多只有一个待触发实例，执行结束后自行计算下次延迟并重排——不重叠、不堆叠、不补跑是模型的结构属性，而非附加守卫逻辑。cron 表达式的解析与下次触发时间计算委托 Quartz `CronExpression`（**唯一用途**，不使用 Quartz 的 Scheduler / Trigger / JobStore）。注解扫包与生命周期接入形态参考官方 `aifei-cron4j` 项目。

## 二、目标与边界

- 坐标：`io.github.gollyhu:aifei-job:1.0.0`（v1.1 集群互斥发布为 `1.1.0`）
- 包名：`cn.hg.aifei.job`
- 技术基线：**Java 8**（class major 52）
- 目标：为 aifei 应用补齐定时任务调度能力，公共 API 零框架类型、零第三方类型；运行时零中间件依赖（不建表、不连 DB，Quartz 仅作 cron 表达式计算库）

**明确不做**（边界，防止范围蔓延）：

1. **不做工作流 / DAG 编排**：任务之间不建立依赖与编排关系。
2. **不做分布式调度**：不做选主、分片、任务分发。v1 也不做执行互斥，多实例各跑一次（v1.1 才经 `aifei-lock` 提供执行互斥，见第三节第 5 条）。
3. **不做数据流 / 批处理框架**：不接管大批量读写、分片游标与检查点。
4. **不做任务管理后台**：不提供生产级 Web 控制台、任务 CRUD 与手动触发页面（`aifei-job-demo` 的演示页面属示例工程，不是模块能力）。
5. **不做动态脚本任务**：任务必须是编译期存在的方法，不支持运行时提交脚本。
6. **不用 Quartz 的调度运行时**：不使用 `StdSchedulerFactory` / `Scheduler` / `RAMJobStore` / `JobDetail` / `Trigger`（含 `CronTrigger`、`SimpleTrigger`）/ `JobDataMap` / misfire 策略，Quartz 只提供 `CronExpression`。因此不建任何 Quartz 表，全模块仍禁止 `java.sql.*`。
7. **v1 不做集群互斥**：不含 `cluster` 包、`job.cluster.*` 配置与相关门禁，随 `aifei-lock` 落地后作为 **v1.1** 增补。
8. **v1 不做任务传参**：注解无参数属性、`JobEvent` 无参数字段（见第五节第 4 条）。

## 三、已拍板决策（进入设计前不得改动，无需再问）

1. **本地调度引擎 = JDK 自续排 + Quartz 仅作 cron 计算器**：调度用 `ScheduledThreadPoolExecutor`（唤醒层）+ `ThreadPoolExecutor`（执行层），**单飞自续排**——每个任务始终最多一个待触发实例，执行结束后在 `finally` 中计算下次延迟并重排。Quartz 只被 `CronExpression#getNextValidTimeAfter` 使用（`local` 包内薄封装），不使用其 Scheduler / Trigger / JobStore / 集群模式，不建表、不连 DB；Quartz 作为**编译期运行时依赖**引入。注解扫包与生命周期接入形态参考官方 `aifei-cron4j` 项目（注解声明 → Plugin 扫包注册 → 容器随应用生命周期启停）。
2. **任务定义 = 注解声明式**：业务在 Just Service 的方法上标注触发规则注解，`JobPlugin.start()` 扫描配置的包并注册。v1 不提供运行时动态注册 / 取消。
3. **cron 语义 = Quartz 表达式**：不自实现 cron 解析器，`@Cron` 直接采用 Quartz cron 语义（6 段含秒，可选第 7 段年）；字段与特殊字符 `* , - / ? L W #` 全部按 Quartz 语义。校验与下次触发时间计算一律委托 `CronExpression`，模块只做薄封装（`QuartzCronSupport`，全模块唯一出现 `org.quartz.*` 的类）；**不提供 5 段兼容写法**（Quartz 不接受，写错即启动失败）。
4. **注解用 3 个，公共类型定档 7 个**：`@Cron` / `@FixedRate` / `@FixedDelay` 各自语义单义；根包公共类型为 `JobKit` / `JobEvent` / `JobListener` / `JobException` + 3 个注解。`JobEvent` 是任务状态快照（任务名、触发时间、阶段、异常），任务方法与监听器共用，不另设 `JobContext`；**不设 `JobScheduler` 公共接口**——唯一引擎、无 provider 扩展点、生命周期归 `JobPlugin`，接口无承载方法。
5. **集群互斥（v1.1，v1 不实现）**：多实例部署时，同一任务到点后由各实例竞争一把执行锁，抢到者执行；锁实现依赖 `aifei-lock`（scope `optional`）。**可用性分两层，都 fail-fast，不降级、不静默重复执行**：
   - **依赖存在性**：`optional` 依赖**不向消费方传递**，普通引用会在类加载期直接 `NoClassDefFoundError`，无法转成可读的启动错误。故 `job.cluster.enabled=true` 时用 `Class.forName` 探测 `aifei-lock`（参照 `aifei-sentinel` 对 `sentinel-transport-simple-http` 的探测范式），探测失败抛 `IllegalStateException`。
   - **后端可用性**：由 `aifei-lock` 的 `LockPlugin` 启动校验负责（主缓存非共享存储则 `LockPlugin` 直接启动失败），**与 `job.cluster.enabled` 无关**，本模块不重复校验。

   该能力随 `aifei-lock` 落地增补，届时重新过设计决策并同步门禁。
6. **同一任务禁止并发重叠**：自续排结构上保证同一任务只有一个在途实例，不排队、不堆叠。v1 不提供「允许重叠」开关。
7. **错过触发不补跑**：停机、GC 或线程繁忙导致的错过点一律跳过，不做补偿执行。
8. **任务异常就地隔离**：单个任务抛异常不影响其他任务与后续触发，异常必须被捕获并记录（含堆栈），且续排位于 `finally`，异常不得断链。
9. **触发与执行分离 + 唤醒线程永不被阻塞**：唤醒层 `ScheduledThreadPoolExecutor` 线程数**固定 1**（任务体只做「提交到执行层」，微秒级返回，不暴露配置）；执行层 `ThreadPoolExecutor` 线程数由 `job.pool.size` 配置、队列为**有界**队列（`job.pool.queue`）、拒绝策略**只允许「记账 + 丢弃」**——**禁止 `CallerRunsPolicy`，禁止 `BlockingQueue.put` 等任何会阻塞提交方的策略**（否则一个长任务占住唤醒线程，全部任务的到点唤醒停摆）。「固定 1 线程」的正确性与该拒绝策略互为前提，不可分开定档。**续排只有两处**：① 执行结束点（执行层 `finally`，正常路径）；② **提交被拒点**（唤醒层捕获 `RejectedExecutionException` 后就地重排，降级路径）——被拒的任务进不了执行层，那个 `finally` 永远不会执行，缺第 ② 处会让该任务永久停摆。三种触发方式的差异全部收敛到「下次延迟」一个纯函数。
10. **优雅停机**：`stopping` 标志置位 → 唤醒层 `shutdown()`（停止新触发）→ 执行层 `shutdown()`（**已提交任务继续执行**，属预期）→ `awaitTermination(job.shutdown.timeoutMillis)` → 超时 `shutdownNow()`，其返回的未执行任务记 `SKIPPED` 并留日志。**在途任务 `finally` 续排时必须捕获 `RejectedExecutionException` 并静默终止（记「停机续排终止」）**——该异常不得从 `finally` 传播，否则会覆盖任务自身异常、干扰停机流程。任务必须自行响应中断，模块不保证强制终止（须写入 README）。

## 四、功能需求（P0，本次交付范围）

| 能力 | 接口面（示意） | 说明 |
| --- | --- | --- |
| cron 触发 | `@Cron("0 0/5 * * * ?")` | Quartz 表达式（6 段必填、第 7 段年可选）；非法表达式启动即失败 |
| 固定频率触发 | `@FixedRate(period = 60000)` | 以上次**开始**时间为基准（`repeatInterval` 语义；Quartz 仅作 cron 计算器，**不使用 `SimpleTrigger`**）；「开始时间」指任务体实际开始执行 |
| 固定延迟触发 | `@FixedDelay(delay = 60000, initialDelay = 0)` | 以上次**结束**时间为基准；与 fixedRate 共用同一条执行链，仅「下次延迟」计算不同 |
| 执行事件 | `JobEvent` | 任务名、触发时间、阶段（`STARTED` / `SUCCESS` / `FAILED` / `SKIPPED`）、异常；任务方法可选接收（`STARTED` 快照），监听器收全阶段。**不含任务参数**（v1 裁剪，见第五节第 4 条） |
| 执行监听 | `JobListener#onEvent(JobEvent)` | 单方法回调；回调内异常必须被捕获，不得影响调度 |
| 静态门面 | `JobKit.state()` / `JobKit.names()` | 只读内存快照（重启即丢；不含手动触发）；返回类型与未启动行为见第五节第 4 条 |
| 并发控制 | 自续排 | 同一任务始终串行，结构上无重叠、无堆叠；不提供「允许重叠」开关 |
| 触发与执行分离 | 唤醒层固定 1 线程 + 执行层 `job.pool.size` | 长任务不占唤醒线程，不影响其他任务触发精度 |
| 执行池拒绝 | 配置项 `job.pool.queue` | 有界队列满时记 `SKIPPED`、重置该任务节拍基准，并**由唤醒层就地重排下一次**；**绝不阻塞唤醒线程** |
| 优雅停机 | `JobPlugin.stop()` | 停止新触发 + 等待在途任务至超时，超时中断并记录未完成 |

**配置键（4 个，最终以设计为准）**：`job.scan.packages`（必填）、`job.pool.size`（执行层线程数）、`job.pool.queue`（执行层有界队列容量）、`job.shutdown.timeoutMillis`（停机等待上限）。

构造期只持 `Prop` 引用，解析与校验全部在 `start()`；配置有误即启动失败，不留半装配状态。

**P1（本次不做）**：集群互斥（v1.1）、Quartz 持久化 JobStore / 集群模式、XXL-Job 等 provider 后端、「允许重叠」开关、任务参数传递、运行时动态注册与取消、手动触发、执行历史持久化、失败重试与告警、任务分片、任务分组与优先级、日历式排除（节假日）。

## 五、关键契约

### 1. 触发与执行语义（必须写进 README 与 `docs/design.md`）

| 场景 | 约定 |
| --- | --- |
| 本轮结束时发现 period 已过 | 单飞结构下不存在并发的新触发实例；`@FixedRate` 在结束点算出「上次开始 + period」已过期时，记一条 `SKIPPED` 事件、以当前时刻为新基准并立即重排，已过去的触发点不补跑 |
| 触发点因停机 / GC 错过 | 不补跑 |
| `@FixedRate` 任务耗时超过间隔 | 本轮结束后按「上次开始时间 + 间隔」重算；已过去的时间点不补跑，按跳过规则记账 |
| 任务执行抛异常 | 捕获 + 记录；不影响其他任务与后续触发；本轮不重试；续排不受影响 |
| 执行池队列满（提交被拒） | 唤醒层捕获 `RejectedExecutionException` → 记 `SKIPPED` → **重置该任务节拍基准为当前时刻** → **由唤醒层就地重排下一次**（缺这一步该任务永久停摆）；唤醒线程不阻塞、不抛异常 |
| 任务在队列等待 | 等待**不计入**「上次开始时间」：`lastStart` 以任务体实际开始执行的时刻为准 |
| 应用停机 | 停止新触发，等待在途任务；超时后中断并记录未完成 |
| 同一方法被标注多个触发注解 | 视为配置错误，启动即失败（避免语义歧义） |
| 非法 cron 表达式 | 启动即失败，指出方法全名与表达式原文 |

### 2. 集群语义的能力声明（**v1.1 生效**，v1 不适用）

- 「不重复执行」依赖执行互斥，**不是分布式调度**：没有选主、没有任务分片、没有执行归属迁移。
- 执行锁基于 `aifei-lock` 的租约：**锁过期仍可能出现重复执行**，业务任务必须自行幂等。
- v1 无该能力，多实例会各跑一次；README 必须显式声明「v1 不支持多实例互斥」。
- v1.1 的互斥可用性分两层，都 fail-fast，不存在「静默降级为各跑一次」的路径：① **依赖存在性**由本模块在 `job.cluster.enabled=true` 时探测（`Class.forName`），缺失即启动失败；② **后端可用性**由 `aifei-lock` 的 `LockPlugin` 启动校验决定（主缓存非共享存储则 `LockPlugin` 直接启动失败，**与 `job.cluster.enabled` 无关**，本模块不重复校验）。

### 3. 与 `aifei-lock` 的关系（**v1.1 生效**）

v1.1 的集群互斥依赖 `aifei-lock`（scope `optional`，类型只允许出现在集群互斥实现包）：加锁、租约、释放与续期语义完全由 `aifei-lock` 负责，本模块不复制锁实现，也不用 `aifei-cache` 的 `putIfAbsent` 自行拼装。

**`optional` 不向消费方传递**：v1.1 的 README 必须声明「开启 `job.cluster.enabled` 须**显式引入** `aifei-lock` 及其依赖的 `aifei-cache` ≥1.1.0」，否则会在类加载期直接 `NoClassDefFoundError`——这也是本模块必须用 `Class.forName` 探测、而非普通引用的原因。

排期影响已确认接受：`aifei-lock` 自身尚未落地，且它依赖 `aifei-cache` 先补 `removeIfEquals` / 后端标识等前置能力（见 `aifei-lock.md` 第四节）。因此集群互斥随 `aifei-lock` 顺延，v1 交付范围（自续排调度、注解扫描、语义收敛、demo）不受影响。

### 4. 只读观测面与参数裁剪（v1）

- `JobKit.state()` 返回 `Map<String, JobEvent>`：**不可变**、按任务名排序；value 为该任务**最近一次终态**（`SUCCESS` / `FAILED` / `SKIPPED`）快照，`STARTED` 不作为结果；从未执行过的任务不出现。
- `JobKit.names()` 返回已注册任务的不可变名称集合（`Set<String>`），用于区分「已注册未执行」与「未注册」。
- 静态引用由 `JobPlugin.start()` 写入、`stop()` 清除；**写入通道为 `internal.JobHolder`**（对齐 `aifei-mq` 的 `internal.MqHolder` 范式），`JobKit` 只从 holder 读取、不对外暴露 setter（否则扩大公共面）。**未启动时两个方法返回空集合而不抛异常**（停机后观测口径不破裂）。
- **v1 不支持给任务传参**：`JobEvent` 无参数字段，注解无参数属性。README 必须写明「任务所需参数由业务通过自身注入的 Service / 配置获取，模块不提供任务参数传递」。后续若支持，须先过设计决策（涉及参数类型学：字符串 / 表达式 / 强类型对象）。

## 六、非功能需求 / 硬约束

- **Java 8**：语法与 API 双约束；主 JAR 内 class major 必须为 52。
- **公共 API 极简、实现无关**：零 `cn.aifei.*`、零第三方类型（含零 `org.quartz.*`）。
- **每类规则唯一定义点**：表达式校验、下次触发延迟计算（三种注解的唯一分叉点）、跳过判定、拒绝记账、状态记录、配置域常量，实现类不得复制。
- **架构门禁 `ArchitectureBoundaryTest`**（源码文本扫描，须在模块根目录执行，不得放宽）：
  - `org.quartz.*`（第三方）只允许出现在 cron 薄封装类（`local` 包）
  - `cn.aifei.*`（框架类型）只允许出现在 `aifei` 接入包
  - 全模块禁止 `java.sql.*`
  - 根包**顶层**公共类型数量精确等于 7（嵌套类型如 `JobEvent.Phase` 不单独计数）
  - 接入包存在 `JobPlugin`
- **依赖**：
  - `cn.aifei:aifei-all` 为 `provided`
  - Quartz 为 **compile 运行时依赖**（唯一的第三方运行时依赖，仅用 `CronExpression`），版本定档 `2.3.2`（Java 8 兼容）；**排空无用传递依赖**（不得带入 c3p0 / JDBC 组件 / 日志绑定），允许保留项见第九节第 3 小节
  - **`compile` scope 会向消费方传递**：消费方 classpath 会出现 Quartz 2.3.2。若业务自带其他 Quartz 版本，实际版本由 Maven 仲裁决定，`CronExpression` 行为可能随之变化——须在 `README.md` 与 `docs/design.md` 声明「冲突时由消费方 exclusion 或显式对齐版本」
  - 不引入任何日志**绑定**；`slf4j-api` 为 Quartz 的日志门面，须保留，不得被排空
- **表达式用例**：不少于 20 条，覆盖合法 / 非法（6 段不合法、字段越界、字符不支持）、月末、闰年、跨年、`?` `L` `W` `#` 语义；断言直接取 `CronExpression#getNextValidTimeAfter` 的计算结果。
- **测试不得依赖真实时钟等待，也不得依赖精确时序**：下次触发延迟用纯计算断言（`nextDelay` 为纯函数）；触发 / 执行语义一律用 `CountDownLatch` + 宽松超时（如 5s）等待事件，period 只用于「至少触发两次」这类宽松断言；禁止 `Thread.sleep` 秒级等待，禁止断言「第 N 次触发发生在 X ms 内」。
- **模块内验证**：`aifei-job` 目录内 `mvn clean verify` 必须 **0 failure / 0 error**。
- **文档分工**：`README.md`（用法与语义声明）、`docs/design.md`（契约事实来源）、`docs/testing.md`（测试策略）、`AGENTS.md`（开发约束）。
- **注释规范**：简体中文；公共类型与公共成员写全 javadoc；原创文件使用统一版权头。

## 七、必须交付的固定配套（对齐既有模块）

| 类别 | 交付物 |
| --- | --- |
| 构建 | `pom.xml`（Java 8、`aifei-all` provided、Quartz compile） |
| 公共面 | 根包 7 类型：`JobKit` / `JobEvent` / `JobListener` / `JobException` + 注解 `@Cron` / `@FixedRate` / `@FixedDelay`；`JobKit` 只有 `state()` / `names()` 两个只读方法 |
| 内部 | `internal`：配置装载、注解扫描与方法签名校验、下次触发延迟计算、任务注册表、跳过与拒绝判定、状态快照、`JobHolder`（`JobKit` 的写入通道）、唤醒层与执行层（有界队列 + 拒绝记账 + **被拒后由唤醒层就地重排**） |
| 实现 | `local` 实现包：`QuartzCronSupport`（唯一允许出现 `org.quartz.*` 的类，`CronExpression` 按任务缓存） |
| 接入 | `aifei` 接入包：`JobPlugin`（唯一 import 框架类型的类） |
| 测试 | `ArchitectureBoundaryTest` 门禁 + 契约测试基类 + Quartz 表达式用例 + 跳过 / 拒绝 / 异常隔离 / 停机 / 触发执行分离用例 |
| 示例 | `aifei-job-demo`（自写 HIO，端口 18110，`start-demo.bat` / `stop-demo.bat`）：**先交静态演示页面原型**（任务列表、到点触发、跳过、异常隔离、停机收敛的直观视图，可用单页 HTML + 假数据直接打开），确认后再生成完整可运行 Demo |
| 文档 | `docs/design.md`、`docs/testing.md`、`README.md`、`AGENTS.md` |

v1.1 增补（本次不交付）：`cluster` 实现包（基于 `aifei-lock` 的执行互斥）与对应门禁断言、fail-fast 用例。

## 八、参考资产

- **官方 `aifei-cron4j` 项目**：注解声明 + Plugin 扫包注册 + 任务容器随应用生命周期启停的接入范式（具体 API 以实现阶段核对源码为准；本项目只借接入形态）
- **Quartz 表达式计算**：`CronExpression` 的构造校验与 `getNextValidTimeAfter(Date)`（只此一个 API，不使用 Scheduler）
- **门禁骨架**：`aifei-websocket/src/test/java/cn/hg/aifei/websocket/ArchitectureBoundaryTest.java`
- **Plugin 装配与生命周期**：`aifei-websocket` 的 `WebSocketServerPlugin`（构造期只持引用、`start()` 解析配置、`stop()` 幂等收敛）
- **门面范式**：`aifei-cache/CacheKit`（有状态门面）、`aifei-websocket/WebSocketUtil`
- **回调保护范式**：`aifei-mq` 的监听器异常隔离（单监听器故障不影响其他订阅者）
- **包隔离先例**：`aifei-sa-token`（`cn.hg.aifei.cache.*` 只允许出现在指定包）
- **锁语义与能力声明**（v1.1）：`docs/需求/aifei-lock.md`（租约、CAS 释放、不保证什么）
- **demo 组织方式**：`aifei-mq-demo`（HIO 同步 + 批处理启动脚本）
- **全体底线**：仓库根 `AGENTS.md`

## 九、拍板结论

### 1. 首轮（原「待拍板」8 项）

| 序号 | 原问题 | 结论 |
| --- | --- | --- |
| 1 | 公共面注解数量 | **3 个注解**（`@Cron` / `@FixedRate` / `@FixedDelay`） |
| 2 | cron 语义范围 | **引入 Quartz 表达式**：6 段（可选第 7 段年）+ `?` `L` `W` `#`，不做 5 段兼容，不自实现解析器 |
| 3 | `@FixedRate` 触发基准 | **以上次开始时间为基准**（对齐 Quartz `SimpleTrigger`；超时后重新计算，已过时间点不补跑） |
| 4 | 集群互斥的降级行为 | **fail-fast**（v1.1）：开启 `job.cluster.enabled` 但锁不可用即启动失败，不降级 |
| 5 | 线程模型 | **触发与执行分离** |
| 6 | 集群互斥锁实现 | **依赖 `aifei-lock`**（optional）；顺延至 v1.1 |
| 7 | 是否交付 demo | **交付 `aifei-job-demo`**，**先交静态演示页面原型**，确认后再生成完整 Demo |
| 8 | `JobKit.state()` 观测面 | v1 只返回内存最近状态（重启即丢）；执行历史持久化后期考虑 |

### 2. 三次拍板（简化项）与四次修订（细节定档）

| 项 | 结论 |
| --- | --- |
| 调度引擎 | **Quartz 降级为 cron 计算器**；调度改 JDK `ScheduledThreadPoolExecutor` 上的单飞自续排（`RAMJobStore` / `Scheduler` / `Trigger` 全部不用） |
| 触发与执行分离 | **保留**：唤醒层固定 1 线程只做提交，执行层 `job.pool.size`；续排**只有两处**（执行结束点 / 提交被拒点） |
| `JobScheduler` 公共接口 | **删除**（唯一引擎、无扩展点、生命周期归 `JobPlugin`；保留即转发型空壳） |
| `JobListener` | 收敛为单方法 `onEvent(JobEvent)` |
| 公共类型数量 | **8 → 7**（`JobContext` 并入 `JobEvent`，`JobScheduler` 删除） |
| 集群互斥 | **v1 整块删除**（包 / 配置键 / 门禁 / fail-fast 用例 / `instanceId`），随 `aifei-lock` 作为 v1.1 |
| 配置键 | **7 → 4**（删 `job.enabled`、`job.scheduler.threads`、`job.cluster.*`；增 `job.pool.queue`） |
| 「允许重叠」开关 | 删除，v1 恒串行 |
| 执行层队列与拒绝策略 | **有界队列 + 拒绝即记账**；禁 `CallerRunsPolicy` / `BlockingQueue.put`；拒绝后重置节拍基准并**由唤醒层就地重排**，防止提交空转与任务停摆 |
| `@FixedRate` 的 `lastStart` | **任务体实际开始执行的时刻**，队列等待不计入 |
| 停机续排 | `finally` 续排必须捕获 `RejectedExecutionException` 并记「停机续排终止」，不得外抛 |
| 任务参数 | **v1 不支持**，README 显式声明由业务自行获取 |

### 3. 设计阶段定档（本轮已给结论，直接进 `docs/design.md`）

1. **Quartz 版本与依赖排空**：定 `2.3.2`；排除 `c3p0:c3p0` 与 `com.mchange:c3p0` 两个变体、`HikariCP-java7`、所有日志绑定（`slf4j-log4j12` / `slf4j-simple` / `logback-classic` 等）；**保留 `slf4j-api`**（Quartz 内部日志门面，排掉会类加载失败）。`design.md` 附「`dependency:tree` 允许出现的传递项清单」，验收逐项核对。`CronExpression` 实例**按任务缓存、不重复构造**（`getNextValidTimeAfter` 只读且线程安全，构造有解析开销）。
2. **停机时序**：除第三节第 10 条外，另定档三条——① 在途任务 `finally` 续排捕获 `RejectedExecutionException` 并记「停机续排终止」；② 执行层 `shutdown()` 后已提交任务继续执行，属预期；③ `shutdownNow()` 返回的未执行任务记 `SKIPPED` 并留日志。
3. **任务实例来源与方法校验**：扫描到的任务所在类**一律从 aifei 容器按类型取**，取不到即启动失败（不做反射 `new` + 手工注入兜底；取到的是代理对象，事务 / 日志生效）；**多实例（多例 Bean）视为配置错误**。注解方法必须为 `public` 实例方法、非 `static`、返回 `void`、参数为 `()` 或 `(JobEvent)`，否则启动失败并指出方法全名。
4. **demo 静态原型验收清单**：不是「页面能打开」，而是视图覆盖语义观测点——任务列表（名称 / 触发方式 / 下次触发时间）、到点触发（高亮）、跳过（`SKIPPED` 可见）、异常隔离（一个任务失败时其他仍触发）、停机收敛（等待中 / 已中断）。清单写入 `docs/testing.md`。

### 4. v1.1 待定档（集群互斥，本轮不做）

1. **租约取值与续期责任**：任务方法签名只有 `()` / `(JobEvent)`，业务**拿不到 `Lock` 句柄、无法自行 `renew()`**；而 `@Cron` 任务的执行时长可能超过任意固定租约。须定档：`job.cluster.*` 中 `lease` 的配置键，以及「超时任务由集群实现包**内部续期**（自己持有句柄）」还是「**放弃互斥 + 记事件**」。`aifei-lock` 不提供看门狗（见 `aifei-lock.md` 第一节「不做」），选择权在本模块。
2. **失败语义**：抢锁失败记 `SKIPPED`（与其他跳过同口径）；`LockException`（结果未知）**不得吞成「抢不到」**，须记 `FAILED` 并留堆栈。

## 十、验收标准

- [ ] 模块目录内 `mvn clean verify` 通过（0 failure / 0 error）。
- [ ] `ArchitectureBoundaryTest` 全部断言通过：根包顶层公共类型精确 7 个，`org.quartz.*` 只在 `local` 包，无 `java.sql.*`。
- [ ] 表达式用例不少于 20 条，含非法表达式、月末 / 闰年 / 跨年与 `?` `L` `W` `#` 语义。
- [ ] 「注册 → 触发 → 执行」闭环可完整单测跑通，测试不依赖真实时钟等待与精确时序。
- [ ] 语义用例覆盖：任务重叠不产生新实例、fixedRate 超时跳过记账、错过不补跑、异常隔离、优雅停机等待、注解重复启动失败、非法表达式启动失败。
- [ ] 触发与执行分离用例：一个长任务不阻塞其他任务的到点唤醒。
- [ ] 执行池拒绝用例：队列占满时唤醒线程不被阻塞、本轮记 `SKIPPED`、不产生提交空转，且**该任务在队列腾空后的下一周期仍被触发**（证明唤醒层已就地重排，未永久停摆）。
- [ ] 停机用例补充：在途任务续排被拒时不外抛异常、不留未捕获堆栈。
- [ ] 注解方法签名校验用例：`static` / 有返回值 / 非法参数 → 启动失败并指出方法全名；多例 Bean → 启动失败。
- [ ] `JobKit` 未启动时 `state()` / `names()` 返回空集合且不抛异常。
- [ ] 公共面零框架类型、零第三方类型；`mvn dependency:tree` 仅出现 Quartz 与设计文档列明允许的传递项，无日志绑定。
- [ ] `README.md` 含「触发与执行语义表」「v1 不支持多实例互斥」「任务参数由业务自行获取」三处声明。
- [ ] `docs/design.md` / `docs/testing.md` / `README.md` / `AGENTS.md` 齐备且与实现一致。
- [ ] `aifei-job-demo` 静态演示页面原型已按第九节第 3 小节第 4 点的清单确认，完整 Demo 可启动（端口 18110）并覆盖 cron / fixedRate / 异常隔离 / 停机收敛。
- [ ] 根 `README.md` 模块总览补充 `aifei-job` 与 `aifei-job-demo` 条目。
- [ ] v1.1（本次不验收）：集群互斥用例、`aifei-lock` 缺失时的 `Class.forName` 探测失败用例，以及第九节第 4 小节（租约与续期责任）的定档结论。

## 十一、执行方式

- 本文件为**任务输入**。收到该开发类任务后，按仓库根 `AGENTS.md` 的文档分工与模块约束执行。
- 第三节已拍板决策不再重复询问；第九节结论直接写入 `docs/design.md`，其中第 3 小节 4 点已在本文定档。
- 建议实施顺序：`QuartzCronSupport` + 下次延迟纯函数（可独立测试）→ 注解扫描与方法签名校验 → 唤醒层 / 执行层与语义收敛（跳过 / 拒绝 / 异常 / 停机）→ `aifei-job-demo` 静态页面原型（确认）→ 完整 Demo。
- 线程池三处细节（有界队列 + 拒绝记账、`lastStart` 定义、停机续排吞异常）在写调度骨架时一并落地，**不得留 TODO 交给联调阶段**。
- 集群互斥（v1.1）**依赖 `aifei-lock` 落地**，且 `aifei-cache` 需先补 CAS 能力；不阻塞 v1，也不在 v1 留桩代码与占位配置。这意味着 **v1.1 会修改 `JobPlugin.start()`**（新增集群包装配与依赖探测分支），**不是纯新增**；v1 的兼容承诺只覆盖 v1 的公共面。
