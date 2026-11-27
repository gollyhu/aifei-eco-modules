# aifei-job-demo

`aifei-job` 的可运行示例工程：一个应用 + 6 个演示任务 + 1 个事件监听器，页面与端点把
「到点触发 / 跳过 / 异常隔离 / 停机收敛」四类语义直接摊开给人看。

- **示例工程，不发布**：不在 `aifei-job` 的发布件里，坐标仅供本地运行（`io.github.gollyhu:aifei-job-demo:1.0.0-SNAPSHOT`）。
- **不改模块**：本工程只消费 `aifei-job` 的公共面（`JobPlugin` / 3 个触发注解 / `JobKit` / `JobListener` / `JobEvent`），
  不引用其 `internal` 包，也不为 demo 反向要求模块新增 API。
- 技术基线：Java 8、Maven、Aifei + Undertow、自写 HIO（`cn.aifei.demo.common.arch`，与 `aifei-mq-demo` 同源；根路径跳转由 `RootPageService` + `redirect.html` 提供）。

## 1. 环境与准备

| 项 | 要求 |
| --- | --- |
| JDK | 8+（源码按 Java 8 语法编写） |
| Maven | 3.6+，本地仓库需已有 `cn.aifei:aifei:1.1.2` |
| 模块 | `io.github.gollyhu:aifei-job:1.0.0-SNAPSHOT` 已 `mvn install` 到本地仓库 |

## 2. 启动 / 停止

Windows：

```bat
start-demo.bat
```

脚本做三件事：`mvn compile dependency:build-classpath` → 读 classpath → 以
`-Ddemo.config=app-config.txt -Dundertow.port=18110` 启动 `cn.hg.aifei.job.demo.DemoApp`。

停止：

```bat
stop-demo.bat
```

手工启动（参数同 `start-demo.bat`，PowerShell 可直接执行）：

```powershell
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
$CP = Get-Content target\cp.txt -Raw
java -Ddemo.config=app-config.txt -Dundertow.port=18110 -Dundertow.host=0.0.0.0 -cp ".\target\classes;$CP" cn.hg.aifei.job.demo.DemoApp
```

| 项 | 值 |
| --- | --- |
| HTTP 端口 | **18110** |
| 根路径 | `http://localhost:18110/` → 200 跳转页（meta refresh）进入 `/job/demo/page` |
| 演示页 | `http://localhost:18110/job/demo/page`（Enjoy 渲染 `view/job/demo.html`） |
| 同一页的静态地址 | `http://localhost:18110/view/job/demo.html`（含 `.` 的路径由 Undertow 直出静态资源） |
| 静态资源 | `/assets/**`（`job-demo.css` / `job-demo.js` / `vue.global.prod.js`） |

> 页面本体由前端负责（`src/main/webapp/**`）。现行页面每秒轮询下列端点取真实数据，
> 页面内无模拟调度引擎；后端按契约提供数据。

## 3. 端点清单

全部为 **GET**，**HTTP 状态恒 200**，业务语义由信封 `code` 承载（`0` = 成功，非 0 见 `msg`）。
信封字段：`{ code, msg, data }`（与 `aifei-mq-demo` 的 `Out` 一致；`null` 字段不输出）。

| 端点 | 入参 | `data` 内容 | 数据来源 |
| --- | --- | --- | --- |
| `/job/demo/page` | — | HTML（Enjoy 渲染 `view/job/demo.html`） | 静态层 |
| `/job/demo/names` | — | 已注册任务名数组（按任务名排序） | `JobKit.names()` |
| `/job/demo/state` | — | 任务名 → `{ phase, triggerTime, triggerAt, error }` | `JobKit.state()` |
| `/job/demo/events` | — | 事件数组（环形缓冲 240 条，最新在前）：`{ seq, taskName, phase, triggerTime, triggerAt, threadName, error }` | demo 监听器 |
| `/job/demo/events/clear` | — | `{ cleared, remaining, capacity }` | demo 监听器缓冲 |
| `/job/demo/tasks` | — | 任务数组：`{ name, biz, mode, trigger, rhythm, expression, period, delay, initialDelay, registered, lastPhase, lastTriggerTime, lastTriggerAt, running, nextFireTime, nextFireAt }` | demo 自算 + 事件流 |
| `/job/demo/shutdown` | — | `{ applying, timeoutMillis, poolSize, queueCapacity, steps[], inFlight[], runningTotal, skippedTotal, note }` | demo 自算 + 事件流 |

字段口径：

- `phase` 取值 `STARTED` / `SUCCESS` / `FAILED` / `SKIPPED`；`error` 仅 `FAILED` 出现。
- `triggerTime` 为 epoch 毫秒，`triggerAt` 为 `HH:mm:ss.SSS`；`SKIPPED` 的触发时间是**原计划触发时刻**。
- `mode` 取值 `CRON` / `FIXED_RATE` / `FIXED_DELAY`；`period` / `delay` / `expression` 只在对应模式下出现。
- `nextFireTime` **由 demo 自算**（见 §6），不是模块能力。
- `/job/demo/shutdown` 为**只读展示**：demo 不会调用 `JobPlugin.stop()`，`applying` 恒为 `false`。

## 4. 演示任务

| 任务名（`类全名#方法名`） | 触发方式 | 演示目的 |
| --- | --- | --- |
| `…job.DemoCronJob#tick` | `@Cron("0/5 * * * * ?")` | 到点触发（每 5 秒）；方法签名取 `(JobEvent)` 形式 |
| `…job.DemoFixedRateJob#poll` | `@FixedRate(period = 3000, initialDelay = 2000)` | `initialDelay` 只影响首次 |
| `…job.DemoFixedDelayJob#flush` | `@FixedDelay(delay = 5000)` | 以上次**结束**为基准（实际间隔 = 5 秒 + 任务体耗时） |
| `…job.DemoFailingJob#boom` | `@FixedRate(period = 4000)` | 每轮必抛异常：观测就地隔离（`FAILED`） |
| `…job.DemoSlowJob#slow` | `@FixedRate(period = 2000)`，任务体睡 4 秒 | fixedRate 超时跳过（`SKIPPED`，事件时间取原计划点） |
| `…job.DemoShutdownJob#waiting` | `@FixedDelay(delay = 3000)`，任务体最多阻塞 6 秒 | 停机收敛的「等待在途 / 超时中断」样本 |

每个任务都是容器内的**单例**（`@Singleton(true)`），并在 `DemoAppConfig#config(Settings)` 里用
`AopKit.get().addSingletonObject(...)` 显式注册——模块启动时按类型从容器取实例，取不到或多例都会启动失败。
监听器同理，按 `JobListener` 接口类型注册；**不注册则一条事件都不会送达**（任务照常触发）。

## 5. 观测点怎么看

| # | 观测点 | 看哪里 |
| --- | --- | --- |
| 1 | 任务列表（名称 / 触发方式 / 最近触发时间 / 阶段，`STARTED` = 执行中） | `/job/demo/tasks`（`trigger` + `lastPhase` + `running`） |
| 2 | 到点触发（新事件插入、下次触发倒计时） | `/job/demo/events`（`STARTED`）+ `/job/demo/tasks` 的 `nextFireTime` |
| 3 | 跳过 `SKIPPED`（与 `SUCCESS` 可区分，展示与原计划时刻的差） | `/job/demo/events` 中 `DemoSlowJob#slow` 的 `SKIPPED` 事件 |
| 4 | 异常隔离（一个任务 `FAILED`，其他任务照常推进） | `/job/demo/events`：`DemoFailingJob#boom` 为 `FAILED` 期间其余任务持续 `SUCCESS` |
| 5 | 停机收敛（等待中在途数 / 已中断跳过数） | `/job/demo/shutdown` 的 `inFlight[]` / `runningTotal` / `skippedTotal` |

`state()` 只承载**最近一次终态**：执行中的任务保留上一次终态、首次执行且尚无终态的任务不出现在 `state()` 里，
因此「执行中」只能从事件流推导（`running` / `inFlight` 就是这么算的）。

## 6. 硬边界：触发方式与「下次触发时间」由 demo 自算

模块公共面只有 `JobKit.names()` 与 `JobKit.state()`，`JobEvent` 没有 `period` / `cron` / `nextFireTime`。
这两个字段由 `DemoJobCatalog` 按**自身注解常量**计算：

- `CRON`：`0/5 * * * * ?` 的触发点是秒数被 5 整除的时刻 → 取下一个 5 秒对齐点（不依赖 Quartz）；
- `FIXED_RATE`：以上次**开始**为基准；已越过计划点则按「当前时刻 + 节拍」重排（对应一次 `SKIPPED`）；
- `FIXED_DELAY`：以上次**结束**为基准；从未执行过时取 `initialDelay`。

这是**示例工程的行为**，不是模块能力；聚合口径是近似值（`SKIPPED` 事件的 `triggerTime` 为原计划时刻）。

## 7. 配置（`src/main/resources/app-config.txt`）

| 键 | 本 demo 取值 | 说明 |
| --- | --- | --- |
| `job.scan.packages` | `cn.hg.aifei.job.demo.job` | 唯一必填键；只扫任务包 |
| `job.pool.size` | `4` | 执行层线程数（缺省 = CPU 核数） |
| `job.pool.queue` | `64` | 执行层有界队列（缺省 256），队满即拒绝并记 `SKIPPED` |
| `job.shutdown.timeoutMillis` | `1000` | 停机等待上限（缺省 10000），取小值便于观察超时中断 |

非 `job.*` 的演示开关：`enjoyDevMode = true`（模板热加载）、`printAction = false`（打印 action 便于排查路由）。
HTTP 端口不在配置文件里，由 `start-demo.bat` 的 `-Dundertow.port=18110` 传入。

## 8. 工程结构

```text
aifei-job-demo/
├── pom.xml                                  # aifei 1.1.2 + aifei-job 1.0.0 + undertow + log4j-core
├── start-demo.bat / stop-demo.bat           # 端口 18110
├── README.md
└── src/main/
    ├── resources/app-config.txt             # job.* 四个键 + 演示开关
    ├── webapp/                              # 前端负责，勿在后端任务里改动
    │   ├── view/job/demo.html
    │   └── assets/{css,js,lib}
    └── java/
        ├── cn/aifei/demo/common/arch/       # 自写 HIO（11 个类，自 aifei-mq-demo 逐字同步）
        └── cn/hg/aifei/job/demo/
            ├── DemoApp.java                 # 入口：Aifei.start(new DemoAppConfig(), args)
            ├── common/DemoAppConfig.java    # 装配中心（HIO + JobPlugin + 单例注册）
            ├── common/DemoRuntimeConfig.java# 三个 job.* 参数的展示镜像
            ├── service/JobDemoService.java  # @Path("/job/demo")，全部 GET
            └── job/
                ├── DemoCronJob / DemoFixedRateJob / DemoFixedDelayJob
                ├── DemoFailingJob / DemoSlowJob / DemoShutdownJob
                ├── DemoJobListener.java     # 监听器：写事件环形缓冲
                ├── DemoEventLog.java        # 环形缓冲（240 条，最新在前，线程安全）
                ├── DemoJobCatalog.java      # 任务元数据 + 下次触发时间自算
                ├── DemoFormats.java / DemoWork.java
```

`cn.aifei.demo.common.arch` 的 11 个类（`In` / `Out` / `IoHandler` / `IoDispatcher` / `InParser` /
`TargetFactory` / `ActionPrinter` / `Code` / `OutCode` / `ContentType` / `CorsHandler`）**全部保留**，
包名、版权块与缩进均未改动；根路径跳转已改为 `RootPageService` + `redirect.html`，
其余与 `aifei-mq-demo` 逐字同步。它们是 HIO 的应用侧实现，删减任一都会牵动其它类的编译；
本 demo 只经 `IoHandler(printAction, "view")` 使用其中的 action 派发、JSON 输出与 Enjoy 渲染三块能力。

## 9. 注意事项

1. **页面由前端负责**：`src/main/webapp/**` 是前端资产，后端任务不修改它；`/job/demo/page` 只负责把
   `view/job/demo.html` 交给 Enjoy 渲染。
2. **不真的停机**：`/job/demo/shutdown` 是只读信息页；要观察真实停机请停进程，或自行在代码里调用
   `Plugin#stop()`（模块语义：停止新触发 → 等待在途至 `job.shutdown.timeoutMillis` → 超时 `shutdownNow()`）。
3. **任务必须可中断**：`DemoSlowJob` / `DemoShutdownJob` 用切片睡眠 + 检查中断实现，符合「模块只发中断信号、
   不保证强制终止」的约定。
4. **单实例语义**：v1 不做多实例互斥，多实例部署时同一任务在每个实例上都会各跑一次，业务任务须自行幂等。
5. **日志**：与 `aifei-mq-demo` 一致，装配 `Log4jLogFactory` 但不带 `log4j2.xml`，故控制台只看到 ERROR 级
   （任务异常堆栈可见，`log.info` 静默）；需要更详细日志请自行补 `log4j2.xml`。
