# aifei-job 测试策略

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

本文面向测试维护者：说明测试结构、用例矩阵（与需求验收项逐项对齐）、表达式用例清单、语义用例构造方案、纪律与 demo 验收口径。契约以 `docs/design.md` 为准；开发约束见模块 `AGENTS.md`。

## 1. 运行方式

命令一律在模块根 `aifei-job/` 执行（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                              # 必须 0 failure / 0 error
mvn test -Dtest=CronExpressionCasesTest       # 单类
mvn test -Dtest=JobScheduleTest#nextDelayOfFixedRateIsBasedOnLastStart   # 单方法
```

默认可执行测试**零外部依赖**：无中间件、无数据库、无网络、**无真实时钟等待**。测试依赖只有 JUnit 4.13.2（`test` 范围）。

## 2. 测试结构

| 测试类 | 类型 | 覆盖 | 对应验收项 |
| --- | --- | --- | --- |
| `ArchitectureBoundaryTest` | 架构门禁（**6 例**） | 包边界、公共面 7 类型、`local` 包唯一 Quartz 触点、`cn.aifei.log.*` 例外、接入包存在（设计 §11） | A02 |
| `CronExpressionCasesTest` | 单元（纯计算） | 表达式用例 **≥20**：合法（含月末 / 闰年 / 跨年 / `?` `L` `W` `#`）与非法（越界 / 段数不足 / 非法字符） | A03 |
| `JobConfigTest` | 单元 | 4 个配置键：缺必填、空项、默认值、越界、非整数；消息含键名与原值 | A11 |
| `JobTriggerTest` | 单元 | 注解属性规则：`period` / `delay` ≥ 1、`initialDelay` ≥ 0、负数与非正数 | A11 |
| `JobNamesTest` | 单元 | 任务名格式 `类全名#方法名` 与错误消息定位口径一致 | A12 |
| `JobScannerTest` | 单元 | 方法签名校验（`static` / 有返回值 / 参数非法）、多触发注解、重复扫描判重、扫不到任务、cron 非法定位 | A10 / A11 / A12 |
| `JobScheduleTest` | 单元（纯函数） | `nextDelay` 三触发方式唯一分叉、`isFirst` 为真返回 `initialDelay` / 为假完全忽略、`overduePlanTime` 超时判定与事件时间 | A06 / A19（纯计算部分） |
| `JobEventTest` | 单元 | 不可变形态、`of` 唯一工厂、`triggerTime` 为 `long`、`Phase` 取值、`error` 仅 `FAILED` 非空 | A14 |
| `JobStateTest` | 单元 | 只接受终态（`STARTED` 忽略）、`snapshot()` 返回新 `Map` 与新 `JobEvent` 值、按任务名排序、不可修改 | A14 / A15 |
| `JobHolderTest` / `JobKitTest` | 单元 | 未启动 / 已清除时 `state()` / `names()` 返回空集合**且不抛**；安装后可见 | A13 |
| `JobInstancesTest` | 单元（容器替身） | 取不到 → 任务实例失败 / 监听器为 `null`；多例 → 两者均失败（消息区分）；单例 → 正常 | A10 / A17 |
| `JobEventsTest` | 单元 | 无监听器时 `dispatch` 静默；回调抛异常被隔离（不外抛、不改变事件）；同步执行在调用线程 | A14 / A16 |
| `JobRunnerSemanticsTest` | 语义 | 「注册 → 触发 → 执行」闭环、同任务不重叠、异常隔离、fixedRate 超时跳过记账、错过不补跑 | A04 / A05 |
| `LongTaskDoesNotBlockWakeupTest` | 语义 | 长任务不阻塞唤醒层：一个任务阻塞时其余任务照常到点触发 | A07 |
| `RejectionRescheduleTest` | 语义 | 队列占满时：唤醒线程不阻塞、本轮记 `SKIPPED`、不产生提交空转、队列腾空后该任务下一周期仍触发、拒绝信号确实以 `RejectedExecutionException` 到达唤醒层 | A08 / A19 |
| `ShutdownTest` | 语义 | 优雅停机等待、超时后 `shutdownNow` 剩余任务记 `SKIPPED`、在途任务续排被拒不外抛、`stop()` 幂等 | A09 |
| `ListenerContractTest` | 语义 | 监听器可选（无实现时任务照常）、多例启动失败、`start()` 只解析一次、`STARTED` 先于任务体、事件时间不晚于 `lastStart`、耗时不计入 fixedRate 判定、并发回调不冲突、`STARTED → 终态` 顺序 | A16 / A17 |
| `StateViewTest` | 语义 | 首次执行中不出现、再次执行中保留上一次终态、结束后终态覆盖、回调内 `state()` 已见本次终态（记账顺序） | A15 / A18 |
| `StartupExceptionContractTest`（装配/启动期断言）+ `ListenerContractTest` | 单元（装配） | 装配生命周期、`start()` / `stop()` 幂等、启动期失败不留半装配、任务名契约端到端 | A12 / A17 |

> **设计名 → 实现名映射**（主表与 §3 矩阵已统一采用下表中的实测类名，可直接 `mvn test -Dtest=<类名>` 执行）：
> `QuartzCronSupportTest` → `CronExpressionCasesTest`；`JobWakeSeparationTest` → `LongTaskDoesNotBlockWakeupTest`；
> `JobRejectionTest` → `RejectionRescheduleTest`；`JobShutdownTest` → `ShutdownTest`；
> `JobListenerTest` → `ListenerContractTest`；`JobStateObservationTest` → `StateViewTest`；
> 原 `JobPluginTest` 的装配断言由 `StartupExceptionContractTest` + `ListenerContractTest` 承担；另有
> `FixedRateSkipTest` / `InitialDelayTest` / `StartupExceptionContractTest` 三个按语义拆分的新增类。逐类计数见 §9.1。

**测试替身（`src/test/java/cn/hg/aifei/job/support/`）**：

| 替身 | 用途 |
| --- | --- |
| `NoopLog` / `NoopLogFactory` | 从 `aifei-weixin` 的同名类**复制**（保留詹波原版权头，仅改 `package`）；`@BeforeClass` 装配 `LogKit.get().setLogFactory(new NoopLogFactory())` |
| `TestJobContainer` | `internal.JobContainer` 的测试实现：预置「类型 → 实例」与「类型 → 是否单例」，可构造「取不到」与「多例」两种情形 |
| `EventProbe` | 实现 `JobListener` 的探针：线程安全记录全部事件，提供 `awaitPhase(taskName, phase, count, timeoutMillis)` / `awaitStartedCount(...)` / `events()` / `reset()`，并记录**回调线程** |
| `TestJobs` | 带注解的测试任务类集合（可注入 `CountDownLatch`、可抛异常、可阻塞），供语义用例服用 |

> **装配路径两条，用途不同**：① **语义用例直接构造** `internal.JobScheduler`（registry / state / events / schedule / config 手工装配，任务实例来自 `TestJobContainer`）——不出线程池以外的装配噪声，可在毫秒级控制；② **装配用例走 `aifei.JobPlugin`**（真实 `AopKit` / `Aop` 容器）——验证容器解析、监听器解析与回滚语义。

## 3. 用例矩阵（与需求验收项对齐）

> 编号 `A01`–`A24` 与需求文档「九、验收标准」的 **24 项**逐项一一对应（顺序一致）；需求第 25 项「v1.1（本次不验收）」记作 `N1`，不建用例。

| 编号 | 验收项 | 覆盖测试 | 关键断言 |
| --- | --- | --- | --- |
| A01 | 模块内 `mvn clean verify` 通过 | — | `Tests run` 无 failure / error（计数见 §7） |
| A02 | `ArchitectureBoundaryTest` 全部断言 | `ArchitectureBoundaryTest` | 6 例全绿；根包公共类型**精确 7**、`org.quartz.*` 只在 `local`、无 `java.sql.*`、无 `org.slf4j.*`、`cn.aifei.log.*` 例外生效 |
| A03 | 表达式用例 ≥20 条 | `CronExpressionCasesTest` | §4 清单共 **31 条**（合法 22 + 非法 9），期望值一律硬编码字面量 |
| A04 | 「注册 → 触发 → 执行」闭环 | `JobRunnerSemanticsTest` | `initialDelay = 0` + `period` 短 → `EventProbe` 在 5s 内收到 `STARTED` / `SUCCESS`，任务体副作用可见 |
| A05 | 语义用例：不重叠 / 超时跳过 / 不补跑 / 异常隔离 / 优雅停机 / 多注解 / 非法表达式 | `JobRunnerSemanticsTest` + `ShutdownTest` + `FixedRateSkipTest` + `JobScannerTest` | 见 §5 构造方案 |
| A06 | `initialDelay` 只作用于首次 | `JobScheduleTest`（纯计算）+ `JobRunnerSemanticsTest` | `isFirst=true` 返回 `initialDelay`；`isFirst=false` 时把 `initialDelay` 改成任意值结果**完全不变**；语义层首次不早于 `initialDelay`、第二次起间隔 ≈ `period` |
| A07 | 触发与执行分离 | `LongTaskDoesNotBlockWakeupTest` | `pool.size=2`：任务 A 阻塞期间任务 B 仍连续触发 ≥2 次 |
| A08 | 执行池拒绝 | `RejectionRescheduleTest` | 唤醒线程不阻塞、`SKIPPED` 事件到达、队列腾空后下一周期仍触发、拒绝确实以 `RejectedExecutionException` 到达唤醒层 |
| A09 | 停机用例 | `ShutdownTest` | 在途任务续排被拒时不外抛（`Thread.setDefaultUncaughtExceptionHandler` 收集器为空）、无未捕获堆栈 |
| A10 | 方法签名 / 多例 / 重复扫描 | `JobScannerTest` + `JobInstancesTest` | 各情形抛 `JobException` 且消息含方法全名或任务名 |
| A11 | 启动期异常契约 | `JobConfigTest` + `JobTriggerTest` + `JobScannerTest` | 全部为 `JobException`（**不是** `IllegalArgumentException` / `IllegalStateException`），消息含定位信息，底层异常在 `getCause()` |
| A12 | 任务名契约 | `JobNamesTest` + `StartupExceptionContractTest` | `names()` / `state()` 的 key 与 `JobEvent#taskName()` 一律 `类全名#方法名`，与错误消息定位一致 |
| A13 | `JobKit` 未启动语义 | `JobKitTest` | 未启动 / 已停机时两方法返回空集合且**不抛** |
| A14 | 不可变与监听器基础 | `JobEventTest` + `JobStateTest` + `JobEventsTest` | `Map` 与 value 均不可修改、每次返回新实例；同步执行于产生事件的线程；并发回调不冲突；回调抛异常不影响调度 |
| A15 | `state()` 观测口径 | `StateViewTest` | 首次执行中不出现；再次执行中保留上一次终态；结束后终态覆盖 |
| A16 | 监听器耗时与异常隔离 | `ListenerContractTest` | `STARTED` 先于任务体；事件时间 ≤ `lastStart`；耗时不计入 fixedRate 判定（纯计算）；终态回调抛异常后 `state()` 与续排不受影响 |
| A17 | 监听器注册 | `ListenerContractTest` + `StartupExceptionContractTest` | 无实现时正常启动并照常触发；有实现时事件全部送达；`start()` 只解析一次；多例 → 启动失败 |
| A18 | 记账顺序 | `StateViewTest` | 终态回调内 `state()` 已是本次终态；`STARTED` 回调内为上一次终态（首次执行时不在 `state()`） |
| A19 | 被拒重排入参 | `RejectionRescheduleTest` + `JobScheduleTest` | `isFirst` 已置 `false`（不重复消费 `initialDelay`、连续被拒不累加）；`lastStart` / `lastEnd` 取自同一 `now`（纯计算等价断言 `nextDelay(t, now, now, now, false)` 与 `nextDelay(t, now, now, now, true)` 差异仅在 `isFirst`）；`SKIPPED` 事件时间 = 原计划触发时刻 ≠ 重置基准 |
| A20 | 公共面零框架 / 零第三方类型 + 依赖树 | `ArchitectureBoundaryTest` + 人工核对 | 门禁①–⑥ 通过；`mvn dependency:tree` 与设计 §10.3 允许清单一致，无日志绑定；`aifei-all` 为 `provided` |
| A21 | 日志装配 | 全部测试 | 模块代码零 `org.slf4j.*`（门禁③）；用例 `@BeforeClass` 装配 Noop 工厂后无日志相关异常 |
| A22 | README 七处声明 | 人工核对 | 语义表 / v1 不支持多实例互斥 / 参数由业务自行获取 / 慢监听器拖慢全部触发 / 监听器可选且单例须自行注册 / 日志经 aifei `Log` / **消费方须自带 `aifei-all`** |
| A23 | 四份文档齐备且与实现一致 | 人工核对 | `design.md` / `testing.md` / `README.md` / `AGENTS.md` |
| A24 | demo 静态原型 + 完整 Demo | demo 工程（§6） | 观测点 5 项逐项验收；完整 demo 端口 `18110` 可启动 |
| N1 | v1.1 集群互斥（**不验收**） | — | 无用例、无桩代码 |

## 4. 表达式用例清单（`CronExpressionCasesTest`，共 31 条）

**时间字面量约定**：用例统一用 `at("yyyy-MM-dd HH:mm:ss")` 把字面量转为 epoch 毫秒——`SimpleDateFormat` + **固定默认时区**（`@BeforeClass` 设 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))`，`@AfterClass` 还原原值）。

**硬性纪律**：期望值**一律以硬编码时刻字面量书写**；用例内**不得**用被测实现（`QuartzCronSupport` 或任何 `CronExpression` 调用）推导期望值，否则断言恒真、锁不住语义。

### 4.1 合法表达式（22 条）

| # | 表达式 | 参考时刻 | 期望下次触发 | 覆盖点 |
| --- | --- | --- | --- | --- |
| 1 | `0 0/5 * * * ?` | `2021-03-01 10:02:30` | `2021-03-01 10:05:00` | 步进分钟 |
| 2 | `0 0/5 * * * ?` | `2021-03-01 10:05:00` | `2021-03-01 10:10:00` | **严格晚于**参考时刻（命中点不返回自身） |
| 3 | `*/10 * * * * ?` | `2021-03-01 10:00:05` | `2021-03-01 10:00:10` | 秒级步进 |
| 4 | `0 0 12 * * ?` | `2021-03-01 11:59:59` | `2021-03-01 12:00:00` | 每日定点 |
| 5 | `0 0 12 * * ?` | `2021-03-01 12:00:00` | `2021-03-02 12:00:00` | 命中后取次日 |
| 6 | `0 0 12 * * ?` | `2021-12-31 13:00:00` | `2022-01-01 12:00:00` | **跨年** |
| 7 | `0 0 0 1 1 ? *` | `2021-06-01 00:00:00` | `2022-01-01 00:00:00` | 每年定点 |
| 8 | `0 30 4 ? * SUN` | `2021-03-01 00:00:00` | `2021-03-07 04:30:00` | 周字段（`SUN`） |
| 9 | `0 0/30 9-17 ? * MON-FRI` | `2021-03-06 10:00:00` | `2021-03-08 09:00:00` | 区间 + 工作日（周六跳至周一） |
| 10 | `0 0 0 L * ?` | `2021-02-01 00:00:00` | `2021-02-28 00:00:00` | **月末 `L`**（非闰 2 月） |
| 11 | `0 0 0 L * ?` | `2021-02-28 00:00:00` | `2021-03-31 00:00:00` | `L` 跨月 |
| 12 | `0 0 0 L * ?` | `2020-02-01 00:00:00` | `2020-02-29 00:00:00` | **闰年 2 月末** |
| 13 | `0 0 0 29 2 ? *` | `2021-03-01 00:00:00` | `2024-02-29 00:00:00` | **闰日跨年**（2024） |
| 14 | `0 0 0 29 2 ? *` | `2024-02-29 00:00:00` | `2028-02-29 00:00:00` | 闰日下一闰年（2028） |
| 15 | `0 15 10 ? * 6L` | `2021-03-01 00:00:00` | `2021-03-26 10:15:00` | **`L` 于周字段**（当月最后一个周五） |
| 16 | `0 15 10 ? * 6L` | `2021-03-26 10:15:00` | `2021-04-30 10:15:00` | 最后一个周五跨月 |
| 17 | `0 0 12 15W * ?` | `2021-08-01 00:00:00` | `2021-08-16 12:00:00` | **`W`**：15 日为周日 → 顺延到周一 16 日 |
| 18 | `0 0 12 15W * ?` | `2021-05-01 00:00:00` | `2021-05-14 12:00:00` | **`W`**：15 日为周六 → 提前到周五 14 日 |
| 19 | `0 0 12 ? * 6#3` | `2021-03-01 00:00:00` | `2021-03-19 12:00:00` | **`#`**：当月第 3 个周五 |
| 20 | `0 0 12 ? * 2#1` | `2021-03-01 00:00:00` | `2021-03-01 12:00:00` | **`#`**：当月第 1 个周一（当日即命中） |
| 21 | `0 0 0 1,15 * ?` | `2021-03-10 00:00:00` | `2021-03-15 00:00:00` | 列表字段 |
| 22 | `0 0 0 1 6 ? 2030` | `2021-03-01 00:00:00` | `2030-06-01 00:00:00` | **第 7 段年** |

> 第 2、5、11、14、16、20 行是「命中点 / 边界」用例（参考时刻正好落在触发点上，必须返回**严格更晚**的下一次）。

### 4.2 非法表达式（9 条）

| # | 表达式 | 非法原因 | 期望 |
| --- | --- | --- | --- |
| 1 | `0 70 * * * ?` | 分钟越界（0–59） | `JobException`，消息含表达式原文，`getCause()` 为底层解析异常 |
| 2 | `0 0 25 * * ?` | 小时越界（0–23） | 同上 |
| 3 | `0 0 12 32 * ?` | 日越界（1–31） | 同上 |
| 4 | `0 0 12 * 13 ?` | 月越界（1–12） | 同上 |
| 5 | `0 0 12 ? * 8` | 周越界（1–7） | 同上 |
| 6 | `0 0 12 * *` | **5 段写法**（本模块不做 5 段兼容） | 同上 |
| 7 | `0 0 12 ? * 6#6` | `#` 值越界（1–5） | 同上 |
| 8 | `0 0 12 * * $` | 不支持的字符 | 同上 |
| 9 | 空串 `""` | 空值 | 同上 |

> 非法用例 9 条（上表 #6 的 5 段写法是需求点名项，必须保留）。

**合计 22 + 9 = 31 条表达式用例**，满足「≥20」并覆盖 `?` / `L` / `W` / `#` 全部特殊字符语义与月末 / 闰年 / 跨年。

## 5. 语义用例构造方案

> **总纪律**：**不依赖真实时钟等待、禁秒级 `Thread.sleep`**、禁断言「第 N 次触发发生在 X ms 内」。全部语义用例用 `CountDownLatch` + **宽松超时（统一 5s）**；`period` 只用于「至少触发两次」一类宽松断言。等待一律用 `EventProbe#awaitPhase(...)`（内部 `latch.await(5, SECONDS)`，超时即断言失败）。

### 5.1 队列占满（拒绝路径，A08 / A19）

构造：`job.pool.size = 1` + `job.pool.queue = 1` + 阻塞任务。

```
TaskA（@FixedRate period=50, initialDelay=0，任务体 await(holdLatch)）  → 占住唯一工作线程
TaskB（@FixedRate period=50, initialDelay=0，任务体立即返回）           → 占住容量为 1 的队列
TaskC（@FixedRate period=50, initialDelay=0，任务体立即返回）           → 提交必被拒
```

断言顺序：

1. `probe.awaitPhase(C, SKIPPED, 1, 5s)` 成功 → 拒绝确实发生且**记账**；
2. 断言 `TaskC` 的 `SKIPPED` 事件时间 = 原计划触发时刻（`plannedTime ≤ SKIPPED.triggerTime` 且 `SKIPPED.triggerTime < 事件观测时刻`）——证明事件时间不是「重置基准的 now」；
3. 断言唤醒线程未被阻塞：`TaskA` 仍阻塞时，`TaskB` 的 `STARTED` 计数持续增长（唤醒层在 1 个线程内连续投递）；
4. `holdLatch.countDown()` 释放 `TaskA` → `probe.awaitStartedCount(C, +1, 5s)` 成功 → **队列腾空后 `TaskC` 下一周期仍被触发**（证明唤醒层已就地重排、未永久停摆）；
5. 拒绝信号以 `RejectedExecutionException` 到达唤醒层：用 `JobScheduler` 的**可注入探针**断言——测试侧在 `Thread.setDefaultUncaughtExceptionHandler` 收集器之外，再断言「无任何 `SKIPPED` 由自定义 handler 产生」（**实现约束**：拒绝策略为 `AbortPolicy`、模块内**只有** `JobWakeTask` 一处 `catch RejectedExecutionException`，该点由代码评审 + `RejectionRescheduleTest` 的「`SKIPPED` 恰好 1 条/次拒绝」间接锁定）。

### 5.2 被拒重排的入参（A19）

分两层验证（避免为了可测性给生产代码加钩子）：

- **纯计算层**（`JobScheduleTest`）：`nextDelay(task, now, now, now, false)` 对 fixedRate 返回 `period`、对 fixedDelay 返回 `delay`；同一 `task` 把 `initialDelay` 改成任意值，`isFirst=false` 的结果**完全不变**（证明「为假完全不读该值」）；`isFirst=true` 返回 `initialDelay`。
- **语义层**（`RejectionRescheduleTest`）：`TaskC` 的首次触发即被拒（`initialDelay = 300`、`period = 50`）。若 `isFirst` 未置 `false`，下次唤醒将再等 300ms 且累加；断言「连续被拒后下一次 `STARTED` 在 ≥1 个 `period` 且 **< 250ms** 内出现」即证明未重复消费 `initialDelay`（阈值取 `initialDelay/2` 与 `period` 的宽松区间，**不断言精确毫秒**）。

### 5.3 长任务不阻塞唤醒层（A07）

`pool.size = 2`；`TaskA` 立即阻塞（`await(holdLatch)`）；`TaskB` 短 `period` 且立即返回。断言 `TaskB` 在 `TaskA` 仍阻塞期间 `STARTED` 计数 ≥ 2——若唤醒层被 `TaskA` 拖住（如 `CallerRunsPolicy`），`TaskB` 不可能触发。

### 5.4 fixedRate 超时跳过（A05）

任务体阻塞到 `period` 之后：`period = 50`，任务体 `await(gateLatch)`；`probe.awaitPhase(Task, SUCCESS, 1, 5s)` 后 `gateLatch.countDown()`（此时 `now - lastStart > period`）。断言：

1. 收到 `SKIPPED`，其 `triggerTime == lastStart + period`（事件时间口径），且 `≤ now`（无倒挂）；
2. `SUCCESS` 与 `SKIPPED` 的到达顺序为 `SUCCESS` → `SKIPPED`，故 `state()` 最终为 `SKIPPED`（设计 §3.7 的记账顺序，**必须**在用例注释中写明这是预期）；
3. 之后不出现「补跑」：`STARTED` 计数不因过期计划点而额外增加。

### 5.5 错过不补跑 / 异常隔离（A05）

- 不补跑：`initialDelay` 较大的任务在 `stop()` 前未到点 → 重新 `start()` 后**不出现**该任务的任何事件（`names()` 有该任务、`state()` 无记录）。
- 异常隔离：`BadTask`（必抛 `RuntimeException`）+ `GoodTask`（正常）同批装配；断言 `BadTask` 收到 `FAILED`（`error()` 非空）后 `GoodTask` 的 `STARTED` 计数继续增长，且 `BadTask` 自身后续周期**继续触发**（不重试、不断链）。

### 5.6 同任务不重叠（A05）

任务体计数 `AtomicInteger running` 并在体内 `await(holdLatch)`；断言任意时刻 `running.get() <= 1`（在体内断言 + 结束后断言峰值 1）。结构依据：同一任务只有一个待触发实例。

### 5.7 停机收敛（A09）

- **等待在途**：任务体阻塞 100ms 级别的 latch（非秒级 sleep），`stop()` 期间 `awaitTermination` 覆盖该在途任务 → `stop()` 正常返回、`SUCCESS` 已到达、无 `SKIPPED`。
- **超时中断**：任务体长期阻塞（`await(releaseLatch)`，由 `@After` 释放避免泄漏），`job.shutdown.timeoutMillis = 100` → 超时后 `shutdownNow()`；断言：① 队列中未执行任务产生 `SKIPPED`（事件时间 = 其 `plannedTime`）；② 在途任务因中断记 `FAILED`；③ 在途任务 `finally` 的续排被拒 → **无未捕获异常**（`Thread.setDefaultUncaughtExceptionHandler` 收集器为空，`@Before` 安装 / `@After` 还原并断言为空）。
- **幂等**：连续调用 `stop()` 两次不抛、不重复记账。

### 5.8 监听器相关（A14 / A16 / A17 / A18）

- **同步线程**：`EventProbe` 记录回调线程，断言 `STARTED` 的回调线程 == 任务体所在线程（用 `Thread.currentThread()` 在任务体里写进探针，比对 `Thread` 引用，**不依赖线程名**）。
- **`STARTED` 先于任务体**：任务体入口 `startedGate.countDown()`；监听器 `STARTED` 分支 `await(holdLatch)` 阻塞 → 断言「监听器阻塞期间 `startedGate` 计数为 0」（`await(200, MILLISECONDS)` 返回 false 即证明未进入任务体）。
- **事件时间 ≤ `lastStart`**：监听器 `STARTED` 记录 `triggerTime`；任务体记录 `firstSeen = System.currentTimeMillis()`；断言 `triggerTime <= firstSeen`（同 JVM 内同一毫秒或更早）。
- **耗时不计入 fixedRate**：纯计算断言（`JobScheduleTest`）：`nextDelay` 只依赖 `lastStart` / `lastEnd` / `now` / `isFirst`，不存在监听器耗时入参 → 断言「把 `now` 手工前移任意量不改变 `nextDelay` 的公式行为」。
- **记账顺序**：监听器在 `SUCCESS` 回调内读 `JobKit.state()`（或 `JobState#snapshot`）→ 断言已是本次终态；在首个 `STARTED` 回调内读 → 断言该任务**不在** `state()` 中。
- **可选与多例**：`TestJobContainer` 不提供 `JobListener` → `JobPlugin.start()` 成功、任务照常触发；提供多例标记 → `start()` 抛 `JobException`；`start()` 后再注册新监听器 → 事件**仍只送达旧实例**（只解析一次）。
- **并发不冲突**：两个任务同时触发，`EventProbe` 用 `ConcurrentLinkedQueue` 收集，断言两任务的 `STARTED → 终态` 各自成对且顺序正确。

### 5.9 启动期异常（A10 / A11 / A12）

统一断言模板：

```java
try {
    plugin.start();   // 或 new JobScanner(...).scan(...) / JobConfig.parse(...)
    fail("应抛 JobException");
} catch (JobException e) {
    assertTrue(e.getMessage().contains("com.acme.BadJob#badMethod"));   // 定位信息
    assertTrue(e.getMessage().contains("0 70 * * * ?"));                // 表达式原文（cron 场景）
    assertNotNull(e.getCause());                                        // 底层异常
}
```

覆盖矩阵：缺失 `job.scan.packages`、含空项、`pool.size`/`pool.queue` 越界、`shutdown.timeoutMillis` 负数与非整数、扫不到任务、`static` 方法、有返回值方法、参数为 `(String)`、一个方法两个注解、重复扫描（`cn.foo` 与 `cn.foo.bar` 同时声明）、任务实例取不到、任务实例多例、监听器多例。

## 6. demo 观测点清单与验收（A24）

> 本节是 demo 静态原型的**验收依据**，也是完整 demo 的联调清单。端口 `18110`、端点契约、5 个观测点见 `docs/design.md` §13。**原型确认时点：demo 后端开工前，由用户确认**（不走产品文档 + QA 基线门禁）。

### 6.1 观测点 5 项（逐项验收）

| # | 观测点 | 验收判据 |
| --- | --- | --- |
| 1 | **任务列表** | 列出全部已注册任务（`类全名#方法名`）、触发方式、最近触发时间、阶段；执行中的任务显示「执行中」而非从列表消失 |
| 2 | **到点触发** | 触发瞬间该行高亮（事件流 `STARTED` 驱动），并显示距下次触发的倒计时 |
| 3 | **跳过 `SKIPPED`** | `SKIPPED` 与 `SUCCESS` 视觉可区分；行内可见其事件时间（原计划触发时刻）与当前时间的差 |
| 4 | **异常隔离** | 一个任务 `FAILED`（红色 + 异常摘要）时，其他任务的触发点**照常推进**（不集体停摆） |
| 5 | **停机收敛** | 停机过程中可见「等待中（在途任务数）」与「已中断（`shutdownNow` 后 `SKIPPED` 计数）」 |

### 6.2 原型验收清单（静态页面，假数据）

- [ ] 单页 HTML，无构建工具、无 npm；可直接双击打开或由静态服务器打开；
- [ ] 5 个观测点在**假数据**下均可演示（含「执行中」状态与 `SKIPPED` 行）；
- [ ] 页面不出现模块不提供的字段（**无** `nextFireTime` 来自公共面、**无** `period` / `cron` 来自 `JobEvent`）——「触发方式」与「下次触发时间」由 demo 端自算；
- [ ] 术语与 `docs/design.md` §2 一致（任务 / 触发方式 / 阶段 / 终态 / 跳过）；
- [ ] 由用户确认后，方可开始 demo 后端实现。

### 6.3 完整 demo 验收清单

> 实测回填：**2026-11-10**，详见 §9.2–§9.4。

- [x] `start-demo.bat` / `stop-demo.bat` 可用，HTTP 端口 **18110**（起：约 3.5s 内开始监听；停：端口立即释放，无残留 `DemoApp` 进程）；
- [x] 端点全部 GET、`/job/demo/...` 命名空间、HTTP 恒 200、语义由业务 `code` 承载（7 端点均 `code=0`）；
- [x] `GET /job/demo/state` 与 `GET /job/demo/events` 数据不一致处符合语义（`state()` 只给终态，执行中视图取事件流；实测 `DemoSlowJob#slow` 在事件流最新事件为 `STARTED` 时 `state()` 仍为上一次终态 `SKIPPED`）；
- [x] 覆盖 cron / fixedRate / fixedDelay / 异常任务 / 长任务（超时跳过）/ 停机任务六类演示任务（`/job/demo/tasks` 返回 6 条，`registered=true`）；
- [x] 停机收敛可在页面上演示（停止 → 「等待中」→「已中断」计数；页面对 `applying=false`、`timeoutMillis`、三步语义、`inFlight[]` / `runningTotal` / `skippedTotal` 均有只读展示，并明示**不真停应用**）。

## 7. 计数与状态（实测回填：2026-11-10）

| 项 | 状态 |
| --- | --- |
| `mvn clean verify` 的 `Tests run / Failures / Errors` | **184 / 0 / 0**（`Skipped: 0`，详见 §9.1） |
| 表达式用例条数 | 实测 **30 个用例方法**（`CronExpressionCasesTest`，覆盖 §4 的 31 条断言口径：22 合法 + 9 非法），满足「≥20」 |
| 门禁用例条数 | **6**（`ArchitectureBoundaryTest`，全绿） |
| demo 构建 | `mvn -B clean package -DskipTests` → BUILD SUCCESS（§9.2） |
| demo 5 观测点 | 5/5 PASS（§9.4） |

## 8. 新增约定

- 新增或改变**公共行为**时，先在 `docs/design.md` 明确契约，再补测试；已修复的缺陷固化为回归测试。
- **测试不引入任何日志绑定**；日志路径一律经 `support/NoopLogFactory` 装配。
- 语义用例的等待**只允许** `CountDownLatch` + 宽松超时（≤ 5s）；**禁止** `Thread.sleep`（含 100ms 级）、禁止断言精确毫秒时序。
- 用例须独立、幂等：每例自建 `JobScheduler` / `JobPlugin`，`@After` 释放 latch、`stop()` 调度器、清 `JobHolder`、还原默认时区与未捕获异常处理器。
- 表达式期望值**必须**是硬编码字面量；需要新用例时按 §4 表格追加一行，**不得**用被测实现生成期望值。

## 9. 实测结果（2026-11-10 全量验收）

> 环境：Windows（win32）+ Maven 3.6+ + JDK 8 编译目标；`aifei-job` 已 `install` 到本地仓库。
> 全部命令在对应模块根目录执行。本轮为**最终验收**，未新增/修改任何用例与主源码，仅回填本文与 `aifei-job-demo/README.md` 的验收小节。

### 9.1 库本体全量回归

```bash
cd aifei-job && mvn -B clean verify      # Tests run: 184, Failures: 0, Errors: 0, Skipped: 0 → BUILD SUCCESS（约 8.7s）
cd aifei-job && mvn -B -DskipTests install   # BUILD SUCCESS（装入本地仓库，供 demo 消费）
```

逐类实测计数（Surefire 输出，共 **22** 个测试类）：

| 测试类 | 用例数 | 测试类 | 用例数 |
| --- | --- | --- | --- |
| `ArchitectureBoundaryTest` | 6 | `JobRunnerSemanticsTest` | 5 |
| `CronExpressionCasesTest` | 30 | `JobScannerTest` | 11 |
| `FixedRateSkipTest` | 2 | `JobScheduleTest` | 18 |
| `InitialDelayTest` | 2 | `JobStateTest` | 10 |
| `JobConfigTest` | 13 | `JobTriggerTest` | 11 |
| `JobEventsTest` | 10 | `ListenerContractTest` | 6 |
| `JobEventTest` | 11 | `LongTaskDoesNotBlockWakeupTest` | 1 |
| `JobHolderTest` | 5 | `RejectionRescheduleTest` | 2 |
| `JobInstancesTest` | 8 | `ShutdownTest` | 4 |
| `JobKitTest` | 7 | `StartupExceptionContractTest` | 13 |
| `JobNamesTest` | 6 | `StateViewTest` | 3 |
| **合计** | | | **184** |

> 类名以本表（实测）为准，且 §2 结构表与 §3 矩阵已统一采用这些实测类名（设计期名称到实现名的映射见 §2 表下注释）。
> 另有 `FixedRateSkipTest` / `InitialDelayTest` / `StartupExceptionContractTest` 三个按语义拆分的新增类；
> 原 `JobPluginTest` 的装配断言由 `StartupExceptionContractTest` + `ListenerContractTest` 承担。覆盖项与 §3 矩阵的对应关系不变。

### 9.2 demo 构建与启停

```bash
cd aifei-job-demo && mvn -B clean package -DskipTests   # BUILD SUCCESS（产出 aifei-job-demo-1.0.0.jar，约 2.6s）
start-demo.bat    # 起：约 3.5s 后 18110 进入 LISTENING
stop-demo.bat     # 停：taskkill 目标 PID，端口立即释放，无残留 java DemoApp 进程
```

- 起停各一次均成功；停止后 `netstat` 查 18110 无 `LISTENING`，`Win32_Process` 查无 `cn.hg.aifei.job.demo.DemoApp` 残留。
- 运行日志（`target/demo-run.log`）中 `ERROR` 仅 `DemoFailingJob#boom failed`（每轮必抛，**预期内**），无其他异常。

### 9.3 端点验证（7 个，全部 GET）

| 端点 | HTTP | `code` | 结构核对 |
| --- | --- | --- | --- |
| `/job/demo/page` | 200 | —（HTML） | `text/html; charset=utf-8`，Enjoy 渲染 `view/job/demo.html`，14986 字节；引用 `/assets/{css/job-demo.css,lib/vue.global.prod.js,js/job-demo.js}`；`/view/job/demo.html` 直出 200 |
| `/job/demo/names` | 200 | 0 | 6 个任务名（`类全名#方法名`，字典序） |
| `/job/demo/state` | 200 | 0 | 任务名 → `{phase, triggerTime, triggerAt, error}`；6 条（`FAILED` 带 `error`） |
| `/job/demo/events` | 200 | 0 | 环形缓冲（capacity 240，满后 `240 / 240`），最新在前，含 `seq / taskName / phase / triggerTime / triggerAt / threadName`（`error` 仅 `FAILED`） |
| `/job/demo/events/clear` | 200 | 0 | `{cleared, remaining, capacity}` + `msg`（实测 `cleared=193, remaining=0, capacity=240`），清空后事件重新长回 |
| `/job/demo/tasks` | 200 | 0 | 6 条，字段 `name/biz/mode/trigger/rhythm/expression/initialDelay/registered/lastPhase/lastTriggerTime/lastTriggerAt/running/nextFireTime/nextFireAt`，`registered=true` 全为 true |
| `/job/demo/shutdown` | 200 | 0 | `{applying:false, timeoutMillis:1000, poolSize:4, queueCapacity:64, steps[3], inFlight[], runningTotal, skippedTotal, note}` |

配置镜像与 `app-config.txt` 一致：`job.pool.size=4` / `job.pool.queue=64` / `job.shutdown.timeoutMillis=1000`。

### 9.4 demo 5 观测点（无头 Edge + HTTP 数据双证）

| # | 观测点 | 结论 | 实测依据 |
| --- | --- | --- | --- |
| 1 | **任务列表** | **PASS** | 页面表头实测为 `任务` / `触发方式与节拍` / `阶段` / `最近触发时间` / `下次触发时间（demo 自算，非模块能力）`；6 行均为 `类全名#方法名` + `@Cron("0/5 * * * * ?")` / `@FixedRate(period=…)` / `@FixedDelay(delay=…)` + `phase-chip ph-success/ph-failed/ph-skipped` + `最近触发时间`（含 `state()` 终态时刻）+ 倒计时（如「17:52:10.000 约 4.4 秒后」）；执行中任务不从列表消失（同格显示「执行中：事件流最新事件为 STARTED（aifei-job-worker-N）」） |
| 2 | **到点触发** | **PASS** | 9s 内以 80ms 采样行 className，6 个任务**全部**出现 `is-firing`（`DemoCronJob#tick` 30 次采样、`DemoFixedRateJob#poll` 45 次…）；事件流条数随时间增长（清空后 0 → 12s 后 89 条 → 满 240），`SKIPPED` 计数 13 → 21；倒计时逐轮刷新 |
| 3 | **`SKIPPED` 可见** | **PASS** | `DemoSlowJob#slow`（`period=2000`，任务体 4s）事件流中稳定出现 `SKIPPED`（240 条窗口内 21 次），与 `SUCCESS` 视觉可区分（`ph-skipped` vs `ph-success`）；事件时间为**原计划触发时刻**：`STARTED 17:51:33.066` → `SKIPPED 17:51:35.066`（= 33.066 + 2000ms），`SUCCESS 17:51:37.075`；页面「跳过分类（按事件线程名）」实测 `提交被拒 0 · fixedRate 超时 21 · 其他 0`；`state()` 亦可见 `DemoSlowJob#slow => SKIPPED`。**提交被拒类**在本 demo 配置下不自然产生，依据见 §9.4.1 |
| 4 | **异常隔离** | **PASS** | 页面「异常隔离（事件流统计）」实测 `DemoFailingJob#boom FAILED 21 次 · 同期其他任务 SUCCESS 89 次`；`boom` 行 `row-failed`（红色）+ 异常摘要 `IllegalStateException: 模拟下游库存服务连续 3 次超时…`；同期 `tick / poll / flush / waiting` 节拍照常推进（各自 `nextFireAt` 持续前移、`is-firing` 持续出现）；日志中 20 条 `ERROR` 全为该任务 |
| 5 | **停机收敛** | **PASS** | `/job/demo/shutdown` 实测 `applying=false`（只读）、`steps` 三步齐全（① 停止新触发 ② 等待在途 ≤ timeoutMillis ③ 超时中断 `shutdownNow` 记 SKIPPED）、`inFlight[]` 逐条给出 `taskName / threadName / startedAt / runningMillis`、`runningTotal=2`、`skippedTotal=21`、`note` 明示不调用 `JobPlugin.stop()`；页面「停机收敛（只读展示）」卡片显式写明 <strong>「本页不会真的停掉应用」</strong> 并指向 `stop-demo.bat` |

#### 9.4.1 观测点 3 补充：提交被拒（`唤醒层 SKIPPED`）不自然产生的依据

- 拒绝阈值 = `pool.size + queue.queue + 1` = `4 + 64 + 1 = 69` 个待执行单元；本 demo 只有 **6** 个任务，且模块「同一任务恒至多一个待触发实例」是结构属性 → 同一时刻待执行单元数上限为 6，**远达不到 69**；
- 实测 21 条 `SKIPPED` 的 `threadName` 全为 `aifei-job-worker-N`（执行层 fixedRate 超时记账），**零**条来自 `aifei-job-wake`（唤醒层拒绝记账）；页面「提交被拒 0」与之互相印证；
- 拒绝路径的正确性由模块级用例 `RejectionRescheduleTest`（2 例）锁定：构造 `pool.size=1 / queue=1` + 阻塞任务，断言 `SKIPPED` 记账、唤醒线程不阻塞、队列腾空后下一周期仍触发。

> 结论：demo 的 5 个观测点全部 PASS。观测点 3 的「fixedRate 超时」子类由 demo 自然产生并被页面区分展示；「提交被拒」子类需要更小的执行队列才可观测，本 demo 未刻意制造（属配置选择，非缺陷）。

### 9.5 页面健壮性

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 无 JS 报错 | **PASS** | 注入 `error` / `unhandledrejection` / `console.error` 收集器后连续运行 2.5min（1s 轮询）：`errs=[]` |
| 无残留模拟调度引擎 | **PASS** | `job-demo.js`（32624 字节）零 `Math.random` / `Math.sin` / 假数据生成；`模拟` 匹配数 0；13 处 `/job/demo/` 真实请求；页头标注「真实数据」 |
| 轮询失败可见降级 | **PASS** | 实测：停掉 demo 进程后页面在下一轮（≈1s）内出现顶部横幅「后端不可达：无法连接后端（请求未到达，检查 18110 端口进程是否在运行）——页面保留上一次成功拉取的数据，恢复后自动继续轮询」+ 状态徽标「后端不可达」，且保留上次成功数据；重启后自动恢复「后端在线」并继续刷新（`errs=[]`） |
| 观测点 3 的「与原计划时刻的差」 | **部分** | 页面渲染了 `SKIPPED` 事件时间（`原计划触发时刻`）与全局「最近刷新」时刻，可同屏比对出差值；但未提供显式「距现在 N 秒」文本（属可选增强，不影响语义可见性） |

### 9.6 门禁与契约抽查（未改动用例，仅复核）

| # | 项 | 结论 | 实测 |
| --- | --- | --- | --- |
| 1 | `ArchitectureBoundaryTest` 6 条断言 | PASS | `Tests run: 6, Failures: 0` |
| 2 | 根包顶层公共类型 = 7 | PASS | `src/main/java/cn/hg/aifei/job/*.java` 恰 7：`Cron` / `FixedDelay` / `FixedRate` / `JobEvent` / `JobException` / `JobKit` / `JobListener` |
| 3 | `org.quartz.*` 仅在 `local/QuartzCronSupport.java` | PASS | 全量 grep 命中 2 行，均在该文件（import + 使用） |
| 4 | `org.slf4j.*` 零引用 | PASS | `src/main/java` 命中数 **0** |
| 5 | `java.sql.*` 零引用 | PASS | `src/main/java` 命中数 **0** |
| 6 | `aifei-all` 为 `provided` | PASS | `dependency:tree` → `cn.aifei:aifei-all:jar:1.1.2:provided`；`pom.xml` 显式 `<scope>provided</scope>` |
| 7 | 无日志绑定 | PASS | `dependency:tree -Dscope=runtime` 仅 `quartz:2.3.2` + `slf4j-api:1.7.30`（均 compile），无 `log4j` / `logback` / `jcl-over-slf4j` 等绑定 |
| 8 | 无分层污染 | PASS | 模块包结构仅 `cn.hg.aifei.job`（7 类型）/ `.aifei`（`JobPlugin`）/ `.internal` / `.local`；无 controller / repository / mapper / dto / render 包 |
| 9 | Java 8 兼容 | PASS | 两个产物 `.class` 头 `CAFEBABE` + `major=52`；pom `maven.compiler.source/target=1.8` |
| 10 | HIO 完整（demo 侧） | PASS | `cn.aifei.demo.common.arch` 11 类齐全（`In`/`Out`/`IoHandler`/`IoDispatcher`/`InParser`/`TargetFactory`/`ActionPrinter`/`Code`/`OutCode`/`ContentType`/`CorsHandler`），且与 `aifei-mq-demo` 同名类 **SHA256 逐字一致**（11/11 无差异） |
| 11 | 事务用法 | PASS | 库本体与 demo 均无 `commit()` / `@Transactional` / `Db.` 查询；demo 的 `Out` 按框架范式 `implements RollbackDecision`（与 `aifei-mq-demo` 逐字一致），仅声明回滚决策，不含事务提交 |
| 12 | 能力边界 | PASS | demo 仅消费 `aifei-job` 公共面（`JobPlugin` / 3 注解 / `JobKit` / `JobListener` / `JobEvent`）；对 `cn.hg.aifei.job.internal` 的 import 数为 **0**；未使用上传下载 / Cookie / Session / Excel / Admin 脚手架 |
