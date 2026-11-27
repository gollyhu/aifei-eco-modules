# PRD：aifei-job（应用内定时任务 SDK）

> 关联：`aifei-lock/docs/prd.md`（v1.1 集群互斥的运行时依赖）。

## 二、范围

- **本次交付 `io.github.gollyhu:aifei-job:1.0.0-SNAPSHOT`**；集群互斥为 v1.1（发布 `1.1.0`），不在本次范围
- 包名 `cn.hg.aifei.job`；技术基线 Java 8（class major 52）
- 公共 API 零框架类型、零第三方类型；运行时零中间件依赖（不建表、不连 DB，Quartz 仅作 cron 表达式计算库）

**不做**（能力边界）：

1. 工作流 / DAG 编排：任务之间不建立依赖与编排关系。
2. 分布式调度：不做选主、分片、任务分发；v1 也不做执行互斥（v1.1 经 `aifei-lock` 增补）。
3. 数据流 / 批处理框架：不接管大批量读写、分片游标与检查点。
4. 任务管理后台：demo 演示页属示例工程，不是模块能力。
5. 动态脚本任务：任务必须是编译期存在的方法。
6. Quartz 调度运行时：`StdSchedulerFactory` / `Scheduler` / `RAMJobStore` / `JobDetail` / `Trigger`（含 `CronTrigger` / `SimpleTrigger`）/ `JobDataMap` / misfire 策略全不用，不建任何 Quartz 表。
7. v1 不做任务传参：注解无「任务入参」属性（`period` / `delay` / `initialDelay` 属节拍属性，不在此列）、`JobEvent` 无参数字段。

## 四、功能需求

本节与第五节的「启动即失败」一律抛 `JobException`（契约见第五节第 4 小节），不再逐一注明。

| 能力 | 说明 |
| --- | --- |
| cron 触发 | `@Cron("0 0/5 * * * ?")`，Quartz 表达式；非法表达式启动即失败，指出方法全名与表达式原文 |
| 固定频率 | `@FixedRate(period = 60000, initialDelay = 0)`，以上次**开始**（任务体实际执行）为基准；超时后按规则重算，不补跑 |
| 固定延迟 | `@FixedDelay(delay = 60000, initialDelay = 0)`，以上次**结束**为基准；与 fixedRate 共用执行链，仅「下次延迟」计算不同 |
| 首次触发延迟 | `initialDelay` 只影响首次触发（基准时刻 = `JobPlugin.start()` 完成注册的时刻），之后不参与任何节拍计算；`0` 表示立即触发；`@Cron` 不设该参数（表达式自身定义首次触发点）；负数启动即失败 |
| 执行事件 | `JobEvent`：任务名、触发时间（`long` epoch 毫秒）、阶段（`STARTED` / `SUCCESS` / `FAILED` / `SKIPPED`）、异常；`final` 类 + 全 `final` 字段 + 无 setter，除异常 `Throwable` 引用外不可变；任务方法可选接收 `STARTED` 快照，监听器收全阶段 |
| 执行监听 | `JobListener#onEvent(JobEvent)` 单方法回调，**同步执行在产生事件的线程上**；监听器为可选单例，`start()` 从容器按类型取一次，取不到即无监听器（不启动失败）；须快速返回、无阻塞操作、线程安全；回调内异常必须被捕获，不得影响调度 |
| 只读观测 | `JobKit.state()` / `JobKit.names()`（见第五节第 3 小节） |
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

- `JobKit.state()`：`Map<String, JobEvent>` **不可变**、按任务名排序；value 为该任务**最近一次终态**（`SUCCESS` / `FAILED` / `SKIPPED`）快照，`STARTED` **既不作结果也不清除记录**——任务执行中（`STARTED` 已发、终态未发）保留上一次终态，观测口径是「最后已知结果」，执行中的任务不会暂时消失；首次执行且尚无终态的任务在执行中仍不出现（只有终态才让任务进入观测面），因此 `state()` 无法区分「正在执行」与「空闲等待」，执行中视图只能由监听器事件流提供；每次调用构建新的不可变 `Map` 与新 `JobEvent` 值，不返回内部记录的实例、不返回活视图（见第五节第 3 小节）；从未执行过的任务不出现。
- `JobKit.names()`：已注册任务名的不可变 `Set<String>`，区分「已注册未执行」与「未注册」。
- 静态引用由 `JobPlugin.start()` 写入、`stop()` 清除，通道为 `internal.JobHolder`（对齐 `aifei-mq` 的 `MqHolder` 范式）；`JobKit` 只读、不对外暴露 setter。**未启动时两个方法返回空集合不抛异常**（停机后观测口径不破裂）。

### 4. 启动期异常契约

`JobException`（公共面 7 类型之一，`final class extends RuntimeException`，构造器包私有）是**启动期失败的唯一异常类型**，覆盖：`job.*` 配置非法（缺失必填键、取值越界）、`job.scan.packages` 为空或扫不到任何任务、注解与注解方法签名非法（`static` / 有返回值 / 参数不是 `()` 或 `(JobEvent)`）、同一方法多个触发注解、cron 表达式非法、`initialDelay` / `period` / `delay` 越界、任务实例从容器取不到或为多例、同一方法被扫到多次。

- **消息必含定位信息**：方法全名（`类全名#方法名`）、非法键名与原值、表达式原文；底层异常（如 Quartz 解析异常）保留为 `cause`。
- **启动期只用它**：不复用 `IllegalArgumentException` / `IllegalStateException`（与 `aifei-cache` 用前者、`aifei-lock` 用后者的差异在此显式声明）——启动失败横跨配置 / 扫描 / 注解 / 表达式四类，用单一模块异常让消费方可一次捕获，且公共面已含该类型，不另设。
- **运行期不抛它**：任务体异常、续排被拒、监听器回调异常一律就地捕获记录，不外抛（见第五节）。

## 六、非功能与硬约束

- **Java 8**：语法与 API 双约束；主 JAR 内 class major 52。
- **公共 API 极简、实现无关**：零 `cn.aifei.*`、零第三方类型（含零 `org.quartz.*`）。
- **每类规则唯一定义点**：任务名生成（`类全名#方法名`）、表达式校验、下次触发延迟计算（三种注解的唯一分叉点，`initialDelay` 只在该函数读取一次，其余位置不得出现）、跳过判定（含三类 `SKIPPED` 的事件时间口径）、拒绝记账（唤醒层 `catch` 处唯一入口）、节拍基准重置（含 `isFirst` 置 `false` 与同一 `now` 采样）、状态记录（终态先记账再回调，顺序唯一）、`JobEvent` 构造（不可变装配点）、监听器解析（取一次后不再访问容器）、监听器回调的阶段分发与异常捕获、配置域常量、日志获取方式（一律 `Log.get(本类.class)`，不经 `org.slf4j.*`），实现类不得复制。
- **架构门禁 `ArchitectureBoundaryTest`**（源码文本扫描，须在模块根目录执行，不得放宽）：
  - `org.quartz.*` 只允许出现在 cron 薄封装类（`local` 包）
  - `cn.aifei.*`（框架类型）只允许出现在 `aifei` 接入包；**唯一例外**：`cn.aifei.log.*` 允许出现在任意包（模块自身的记录通道）
  - `org.slf4j.*` 全模块禁止（只由 Quartz 内部使用）
  - 全模块禁止 `java.sql.*`
  - 根包**顶层**公共类型数量精确 7（嵌套类型如 `JobEvent.Phase` 不单独计数）
  - 接入包存在 `JobPlugin`
- **依赖**：`cn.aifei:aifei-all` provided（含 `cn.aifei.log`，模块自身的记录走它；**provided 不传递 → 消费方必须自带 `aifei-all`**，只引核心 `cn.aifei:aifei` 时运行期 `NoClassDefFoundError: cn/aifei/log/Log`）；Quartz 2.3.2 compile（仅用 `CronExpression`）+ `slf4j-api` compile（仅 Quartz 内部日志门面，显式直接依赖），除这两项外无其它第三方运行时依赖；不引入任何日志**绑定**。
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
- **交付完整性**：执行层 / 唤醒层语义（跳过 / 拒绝 / 异常 / 停机）须完整实现，交付物不得残留 TODO / FIXME 标记。


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
- [ ] 方法签名校验用例：`static` / 有返回值 / 非法参数 → 抛 `JobException` 并指出方法全名；**任务所在类为多例 Bean → 抛 `JobException`**；多个扫描包存在包含关系导致同一方法被扫两次 → 抛 `JobException`（见第五节第 4 小节）。
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

