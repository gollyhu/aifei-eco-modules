# aifei-id 测试策略与用例清单

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

本文面向**测试维护者**：说明测试分类、用例与设计契约场景的对照关系、验证命令、离线纪律与未覆盖项。行为契约、异常语义与门禁判定的完整定义见 `docs/design.md`（§5 公共 API 契约 / §7 行为契约与 §7.6 消息措辞 / §10 门禁 14 例 / §13 demo 契约 / §15 验收标准），本文不复制契约细节，只用指针引用。

> **交付性质**：`aifei-id` 是库模块（SDK），零 DB、无入站 HTTP 面、无页面。因此**不设**表结构 / SQL / 事务 / HIO / 路由 / 前端基线门禁相关用例（design.md §1.2 不适用声明）；HTTP 与页面只在 `aifei-id-demo` 出现，按 §8 的命令行冒烟核验，不纳入 surefire 计数。
>
> **本文数字为 QA 独立复验口径**（`mvn -B clean verify`，2026-11 复验）：**89 例，0 failure / 0 error**。

---

## 1. 测试分类

| 分类 | 文件 | 用例数 | 职责 |
| --- | --- | --- | --- |
| **架构门禁** | `ArchitectureBoundaryTest` | 14 | 源码文本扫描断言包边界 / 公共面精确计数 / 零 DB / 零 JVM 全局写 / Java 8 / 无 SPI / 唯一定义点 / 测试离线（design.md §10 十四例，逐条一一对应） |
| **单测（配置域）** | `IdConfigTest` | 29 | design.md §8 全部 8 个键的默认值、边界、越界、非整数、必填、空白剥离，以及场景 21/22「无效键不读不校验」 |
| **单测（雪花）** | `internal.SnowflakeIdGeneratorTest` | 13 | §7.1 位布局 / 序列 / 序列耗尽、§7.2 回拨三分支、场景 3/4 时钟边界、场景 5 租约丢失、构造期 workerId 越界、并发唯一性 |
| **单测（号段）** | `SegmentIdGeneratorTest` | 8 | §7.4 段内连续 / 耗尽取段 / 取段参数下传、场景 9 取段失败包装、场景 11 回退拦截、`local` 按 key 独立与「重启重号」、并发唯一性 |
| **单测（号段存储）** | `CacheSegmentStoreTest` | 4 | 保留分区 `__ID__` / 段键 `segment.<key>` / 段长 / TTL 如实下传、返回值即段末端、按 key 隔离、异常不重复包装 |
| **单测（门面）** | `IdKitTest` | 6 | 场景 17 未装配（消息 O）、转发语义与十进制字符串精度、场景 1/18/19 的装配入口生命周期 |
| **单测（装配）** | `IdPluginTest` | 15 | 场景 1/5/12/13/14/15/16/18/19/22/23：生命周期幂等与失败回滚、workerId 三来源、可选依赖缺失（J/K）、协同模块未装配（P）、号段后端非共享 → 消息 H |
| **测试桩** | `support/*`（9 个） | — | `FakeClock`（脚本化时钟）、`FakeCounter`、`FakeSegmentStore`（段末端脚本 + 故障注入）、`FakeWorkerIdSource`（`breakLease()`）、`FakeIdGenerator`、`FakeCache`（动态代理，`supportsCas=true`）、`FakeLockStore`（`LockStore` 的离线实现）、`HidingClassLoader`（伪装类缺失）、`NoopLog` / `NoopLogFactory`（不引入日志绑定） |

合计 **89 例**（14 + 29 + 13 + 8 + 4 + 6 + 15），全部 JUnit 4 + Java 8。

### 1.1 为什么某些用例必须落在特定包

| 现象 | 原因 |
| --- | --- |
| `SnowflakeIdGeneratorTest` 在 `cn.hg.aifei.id.internal` | 注入假时钟的双参构造 `SnowflakeIdGenerator(WorkerIdSource, LongSupplier)` 是**包级可见**（只供测试），包外无法构造；这也是回拨与序列耗尽两条等待路径能零等待覆盖的前提 |
| `LockWorkerIdSource` 的真实抢占路径用 `FakeLockStore` 而非 `FakeLock` | `cn.hg.aifei.lock.Lock` 为 `final` 且构造器包私有，无法桩化；`LockKit` 是从 `LockHolder` 取 **LockStore**，因此桩落在存储层即可让「0..1023 升序抢占 / 全占用 / 停机释放」三条路径离线可测（design.md §12 事项 13 只提到「FakeLock 不可行」，实现期以 `FakeLockStore` 达成了同等覆盖） |
| 「租约丢失」分两处使用 | 雪花侧用 `FakeWorkerIdSource#breakLease()` 验证「不健康即拒发号」（消息 E）；装配侧用 `FakeLockStore` 验证抢占与释放。`LockWorkerIdSource` 内部「续期失败 → `healthy=false`」的置位逻辑本身无用例（见 §7） |
| `IdKitTest` / `IdPluginTest` 每例换 AOP 容器 | 框架无单例注销 API，同一 JVM 内第二个装配入口注册 `IdGenerator` 单例会因「已存在」失败；`@Before` 用 `AopKit.get().setAopFactory(new AopFactory())` 隔离，**不改被测代码** |

### 1.2 测试隔离与纪律

- 每个涉及装配态的用例在 `@Before` / `@After` 清 `IdHolder`、`CacheManager`、`LockHolder` 并换 AOP 容器，用例间无状态残留。
- 全部用例**零联网、零外部服务、零秒级 `Thread.sleep`**：等待路径由 `FakeClock` 脚本化驱动；并发用例用 `CountDownLatch` 起跑 + `join(30000)` 收口，不靠 sleep 对齐。
- 消息断言一律**逐字字面量**（照抄 design.md §7.6），**不得**用 `IdErrors` 的构造方法生成期望值，否则措辞漂移无法被发现。
- 门禁测试读自身源码属固定输入，不构成外部 I/O。

---

## 2. 场景 ↔ 用例对照表（design.md §7.5）

| 场景 | 行为 | 覆盖用例 |
| --- | --- | --- |
| 1 | `new IdPlugin(null)` → 消息 A | `IdPluginTest.nullPropRejected`、`IdKitTest.nullPropRejected` |
| 2 | 雪花回拨 > 5ms → 消息 B | `SnowflakeIdGeneratorTest.clockBackwardsBeyondToleranceRejected`（逐字断言含 lastTimestamp / now / backwards / tolerance） |
| 3 | 时钟早于 EPOCH → 消息 C | `SnowflakeIdGeneratorTest.clockBeforeEpochRejected` |
| 4 | 41 位时间戳溢出 → 消息 D | `SnowflakeIdGeneratorTest.timestampOverflowRejected` |
| 5 | 租约丢失 → 消息 E | `SnowflakeIdGeneratorTest.workerLeaseLostRejected`、`IdPluginTest.leaseLossStopsGenerationWithMessageE` |
| 6 | 回拨等待被中断 → 消息 F + 恢复中断位 | `SnowflakeIdGeneratorTest.interruptedWhileWaitingRejected`（`finally` 断言 `Thread.interrupted()`） |
| 7 | 同一毫秒 4096 格耗尽 → 等下一毫秒继续 | `SnowflakeIdGeneratorTest.sequenceExhaustionWaitsForNextMillisecond`（4097 个号，第 4097 个落到 `T0+1` 且序列归零） |
| 8 | 回拨 ≤ 5ms → 等待追上后继续 | `SnowflakeIdGeneratorTest.shortBackwardsWaitsThenContinues`（序列 +1 不重置） |
| 9 | 号段取段失败 → 消息 G + cause | `SegmentIdGeneratorTest.storeFailureWrappedWithCause`（`assertSame` 根因） |
| 10 | 号段后端非共享（`supportsCas=false`）→ 消息 H | **已覆盖**：`IdPluginTest#notSharedBackendFailsWithMessageH`（`FakeCache.notShared(new FakeCounter())` 令 `supportsCas()==false`，逐字断言消息 H 且不留半装配） |
| 11 | 号段回退 → 消息 I | `SegmentIdGeneratorTest.rollbackRejected` |
| 12 | `snowflake` + `source=cache` 缺 `aifei-cache` → 消息 J | `IdPluginTest.missingCacheDependencyFailsWithMessageJ`（`HidingClassLoader` 隐藏 `CounterKit`，并反向断言不出现 `NoClassDefFoundError`） |
| 13 | `segment` + `store=cache` 缺 `aifei-cache` → 消息 J | 同上（同一用例的第二组断言） |
| 14 | `source=lock` 缺 `aifei-lock` → 消息 K | `IdPluginTest.missingLockDependencyFailsWithMessageK` |
| 15 | `0..1023` 全占用 → 消息 L | `IdPluginTest.lockWorkerIdExhaustedFailsWithMessageL`（同时断言 1024 次升序尝试、零释放） |
| 16 | 配置缺失 / 越界 / 非整数 → 消息 M / N | `IdConfigTest` 全部 29 例；装配侧 `IdPluginTest.configWorkerIdOutOfRangeRejected`（越界即失败且不留半装配） |
| 17 | 未装配 / 已停机调用 → 消息 O | `IdKitTest.notInstalledFailsExplicitly`、`IdKitTest.pluginStopIsIdempotent`、`IdPluginTest` 各失败用例末尾的 `assertNotInstalled()` |
| 18 | `start()` 幂等 | `IdKitTest.pluginStartIsIdempotent`、`IdPluginTest.startIsIdempotentWithoutReacquiringWorkerId`（断言生成器实例不变 + 计数器只取一次） |
| 19 | `stop()` 幂等 / 未启动即 stop | `IdKitTest.pluginStopIsIdempotent`、`IdPluginTest.stopIsIdempotentAndClearsHolder` |
| 20 | `close()` 时 `release()` 抛异常被吞 | **未覆盖**（见 §7 第 3 条；`FakeLockStore.release` 恒成功） |
| 21 | `id.worker.*` 在 `segment` 下被忽略 | `IdConfigTest.workerKeysIgnoredUnderSegment` |
| 22 | `id.segment.*` 在 `snowflake` 下被忽略 | `IdConfigTest.segmentKeysIgnoredUnderSnowflake`、`IdPluginTest.segmentKeysIgnoredUnderSnowflake`（装配侧证据） |
| 23 | 协同模块在 classpath 上但未装配 → 消息 P | `IdPluginTest.unassembledCacheDependencyFailsWithMessageP`、`unassembledLockDependencyFailsWithMessageP`（`detail` 动态取自框架，模块措辞部分为字面量） |

### 2.1 §7.1 / §7.3 / §7.4 的补充契约点

| 契约点 | 用例 |
| --- | --- |
| 位布局 `1+41+10+12` 可按位反推（`id >> 22` / `(id >> 12) & 0x3FF` / `id & 0xFFF`） | `SnowflakeIdGeneratorTest.bitLayoutEncodesTimestampWorkerAndSequence` |
| 同毫秒序列从 0 起递增、单实例严格递增 | `sameMillisecondIncrementsSequence` |
| 构造期 workerId 越界即拒绝（三种来源统一在此拦截） | `workerIdOutOfRangeRejected`（消息 N 复用） |
| `acquire()` 幂等（诊断路径重复调用不重复消耗编号） | `acquireIsIdempotent` |
| 默认构造使用真实系统时钟且可用 | `defaultConstructorUsesSystemClock` |
| 并发唯一 + 每线程内严格递增（AC-06） | `SnowflakeIdGeneratorTest.concurrentGenerationIsUniqueAndIncreasing`（8 线程 × 1000） |
| 号段段内连续、耗尽才取段 | `SegmentIdGeneratorTest.firstSegmentStartsFromOneAndStaysContiguous`、`refillsAfterSegmentExhausted` |
| 取段参数（key / step / ttl）如实下传 | `storeReceivesKeyStepAndTtl` |
| `local` 存储跨 key 独立、重启重号 | `localStoreKeepsKeysIndependent`、`localStoreRestartsFromOne` |
| 号段并发唯一 | `SegmentIdGeneratorTest.concurrentGenerationIsUniqueAndIncreasing`（8 × 500，`step=64` 强制频繁换段） |
| `cache` 模式 workerId 取模 `(v-1) % 1024` | `IdPluginTest.cacheWorkerIdIsCounterModulo1024`（预置计数 1025，取号后 1026 → workerId 必须为 1，截断或越界判断会立刻失败） |
| `lock` 模式升序抢占 + 停机释放 | `IdPluginTest.lockWorkerIdAcquiredInAscendingOrder` |
| 装配失败回滚（清持有器 + 关闭来源释放租约） | `IdPluginTest.startFailureLeavesNoPartialAssembly` |
| `IdKit.nextIdStr() == Long.toString(nextId())`，19 位不丢精度 | `IdKitTest.facadeForwardsToInstalledGenerator`（`9007199254740993`） |

---

## 3. 验收标准 ↔ 覆盖通道（design.md §15）

| AC | 覆盖通道 |
| --- | --- |
| AC-01 公共面精确 3 + 1 | 门禁 ④ `publicSurfaceExact` |
| AC-02 零第三方 / 无日志绑定 | 门禁 ① `noThirdPartyReference` + `mvn dependency:tree` |
| AC-03 Java 8 双约束 / major 52 | pom（1.8）+ 门禁 ⑦ `java8Compatibility` + `javap -verbose` 抽查 |
| AC-04 位布局 / EPOCH / 4096 / workerId 范围 | `SnowflakeIdGeneratorTest` 位拆解与序列耗尽用例 |
| AC-05 回拨三分支 + 时钟边界（B/C/D） | `SnowflakeIdGeneratorTest` 四个假时钟用例 |
| AC-06 并发唯一 + 严格递增 | `SnowflakeIdGeneratorTest.concurrentGenerationIsUniqueAndIncreasing` |
| AC-07 号段连续性 / 消息 G / 消息 I / local 按 key | `SegmentIdGeneratorTest` + `CacheSegmentStoreTest` |
| AC-08 三来源（config 校验 / cache 取模 / lock 抢占 + 消息 L + 消息 E） | `IdPluginTest` 五个来源用例；`lock` 抢占经 `FakeLockStore` **离线**覆盖（真 Redis 路径见 §7 第 5 条） |
| AC-09 `IdKit` 未装配 + 装配后可用 + 字符串形态 | `IdKitTest` 全 6 例 |
| AC-10 消息 A / `start` 幂等 / 失败回滚 / `stop` 幂等 | `IdPluginTest` + `IdKitTest` 生命周期用例 |
| AC-11 §8 全部键的缺失 / 越界 / 非整数 + 场景 21/22 | `IdConfigTest` 29 例 |
| AC-12 依赖缺失 → 消息 J/K（不得 `NoClassDefFoundError`） | `IdPluginTest` 两个 `HidingClassLoader` 用例 |
| AC-13 唯一定义点（⑨⑩⑪⑫⑬） | 门禁 ⑨ / ⑩ / ⑪ / ⑫ / ⑬ |
| AC-14 无 SQL / 无 SPI / 无 JVM 全局写 / 测试零联网 | 门禁 ⑤ / ⑥ / ⑧ / ⑭（断网 verify 见 §7 第 7 条） |
| AC-15 demo 端口 / 5 端点 / 4 展示内容 / 字符串 ID / 双策略 | **手工冒烟**（本文件 §6；不进 surefire） |

---

## 4. 架构门禁 14 例与「非空转」验证

门禁按**源码文本**断言（路径分隔符归一化、注释由状态机剥离后再匹配字面量），扫描 `src/main/java`（⑭ 扫 `src/test/java`），**必须在 `aifei-id/` 根目录执行**（相对路径依赖，surefire 已固定 `workingDirectory = ${project.basedir}`）。

| # | 方法 | 判定的「唯一落点」 |
| --- | --- | --- |
| ① | `noThirdPartyReference` | 白名单外零 import |
| ② | `optionalModuleScopeRules` | `cn.hg.aifei.cache.*` / `cn.hg.aifei.lock.*` 只在 `internal` |
| ③ | `frameworkOnlyInBridgeExceptLog` | `cn.aifei.log.*` 只在 `internal` + 装配包；其余 `cn.aifei.*` 只在装配包 |
| ④ | `publicSurfaceExact` | 根包精确 3 / 装配包精确 1，文件名集合精确相等 |
| ⑤ | `noSqlReference` | 零 `import java.sql.` |
| ⑥ | `noJvmGlobalPropertyWrite` | 零 `System.setProperty` / `getProperties().put` / `setErr` / `setOut` |
| ⑦ | `java8Compatibility` | 零 Java 9+ 集合工厂 / `record` / `var` / text block / `sealed` / `instanceof` 模式匹配 |
| ⑧ | `noServiceProvider` | 零 `ServiceLoader` / `META-INF/services`，且目录不存在 |
| ⑨ | `clockOnlyInSnowflake` | `System.currentTimeMillis` 与 `1704067200000` 只在 `SnowflakeIdGenerator` |
| ⑩ | `cacheNamesOnlyInIdNames` | `"__ID__"` / `"segment."` / `"worker"` 只在 `IdNames` |
| ⑪ | `classForNameOnlyInIdDeps` | `Class.forName` 只在 `IdDeps` |
| ⑫ | `threadCreationOnlyInWorkerLease` | `new Thread(` / `Executors.` / `ThreadPoolExecutor` 只在 `LockWorkerIdSource` |
| ⑬ | `configKeysOnlyInIdConfig` | `"id.` 只在 `IdConfig` |
| ⑭ | `testSourcesOfflineOnly` | 测试源码 URL host 只能是 `127.0.0.1` / `localhost` |

> **⑩ 的片段清单与 design.md 的差异（已裁定，非缺陷）**：design.md §10.1 ⑩ 列出 4 个片段（含 `"id.worker."`），实现与 `AGENTS.md` 为 3 个。裁定见 design.md §10 备注：`IdNames` 用 `IdConfig.KEY_PREFIX + "worker."` 拼装锁名，源码中不存在 `"id.worker."` 整体字面量，该片段恒真故视为不可达项清理，**两条门禁均未放宽**（⑬ 仍锁 `"id.` 前缀唯一定义点，⑩ 仍锁三个片段）。

### 4.1 QA 独立「非空转」探针记录

复核门禁是否真的会红（**探针用完即删，删除后重跑 verify 回到 89 例全绿**）：

| 步骤 | 动作 | 结果 |
| --- | --- | --- |
| 1 | 新增主源码探针 `src/main/java/cn/hg/aifei/id/IdGateProbe.java`（含 `import java.sql.Connection` / `com.alibaba.fastjson2.JSON` / `cn.hg.aifei.cache.CacheKit` / `cn.aifei.plugin.Plugin` / `System.currentTimeMillis()` / `"__ID__"` / `"segment."` / `"worker"` / `"id.strategy"` / `Class.forName` / `new Thread()` / `System.setProperty` / `List.of(` / `ServiceLoader`） | — |
| 2 | 新增测试源码探针 `OfflineProbeTest`（含 `http://example.com` 字面量） | — |
| 3 | `mvn -B test -Dtest=ArchitectureBoundaryTest` | **Tests run: 14, Failures: 14** —— ①–⑭ 逐条命中预期违规（④ 报 `expected:<3> but was:<4>`，⑭ 报 `http://example.com`），**证明 14 条断言均非空转** |
| 4 | 删除两个探针文件 → `mvn -B clean verify` | **Tests run: 89, Failures: 0, Errors: 0**，`BUILD SUCCESS`（无残留） |

维护要求：任何临时探针**必须**以 `*ProbeTest` 命名或放独立临时文件，**不得**计入正式用例数，且用完必须删除并重跑 `verify` 确认用例数回到 89。

---

## 5. 验证命令与运行方式

**全部命令在 `aifei-id/` 模块根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn -B clean verify                        # 全量 89 例，必须 0 failure / 0 error
mvn -B test -Dtest=ArchitectureBoundaryTest # 仅架构门禁（14 例）
mvn -B test -Dtest=SnowflakeIdGeneratorTest # 单类；亦可 类名#方法名
mvn -B dependency:tree                      # 依赖面核对（见 design.md §11.2）
javap -verbose target/classes/cn/hg/aifei/id/IdKit.class | findstr "major version"   # 应为 52（Windows；Linux/Git Bash 用 grep）
```

预期依赖树（design.md §11.2 允许清单，实测一致）：

```text
io.github.gollyhu:aifei-id:jar:1.0.0
+- cn.aifei:aifei-all:jar:1.1.2:provided
|  \- com.alibaba.fastjson2:fastjson2:jar:2.0.65:provided
+- io.github.gollyhu:aifei-cache:jar:1.1.0:compile (optional)
|  \- cn.aifei:aifei:jar:1.1.2:compile (optional)
+- io.github.gollyhu:aifei-lock:jar:1.0.0:compile (optional)
\- junit:junit:jar:4.13.2:test
   \- org.hamcrest:hamcrest-core:jar:1.3:test
```

### 5.1 Redis 集成测试口径（默认关闭，当前未落地）

- **默认关闭**：`mvn clean verify` 不含任何外部服务依赖，89 例全部离线通过。
- **开启方式（口径，尚未实现）**：需真实 Redis + `aifei-cache` + `aifei-lock` 后端，用 `-Dredis.integration=true` 门控；**连不上必须失败，不得静默跳过**（design.md §12 事项 13）。
- **当前状态**：本批次**未落地**集成用例（无 `*IntegrationTest` 类、pom 无 `redis.integration` 开关），故 `-Dredis.integration=true` 目前无效。受影响的能力见 §7 第 5 条。
- 若后续落地，建议命令：

```bash
mvn -B test -Dredis.integration=true      # 需要本机或容器内 Redis；失败即视为回归
```

### 5.2 手工冒烟（AC-15，`aifei-id-demo/`，不进 surefire）

```bash
cd aifei-id-demo
mvn -B clean package
start-demo.bat            # 雪花：id.strategy=snowflake / source=config / workerId=1，HTTP 18140
stop-demo.bat             # 停止后 18140 必须无监听
start-demo-segment.bat    # 号段：id.strategy=segment / step=200 / store=local，同一 18140
stop-demo.bat
```

两个脚本**同端口不可并存**（脚本注释与 `app-config-segment.txt` 注释均已写明，设计定档见 design.md §12 事项 8）。

---

## 6. AC-15 手工冒烟实测记录（QA 独立复验）

| 步骤 | 实测结果 |
| --- | --- |
| `mvn -B clean package`（demo） | `BUILD SUCCESS`，产出 `target/aifei-id-demo-1.0.0.jar` |
| 雪花模式 `GET /id/demo/page` | HTTP 200，24188 字节，页面含 ①单号生成与位拆解 / ②并发批量去重 / ③号段耗尽与新段 / ④workerId 来源与状态 四个功能块 |
| 雪花模式 `GET /id/demo/state` | HTTP 200，`{"code":0,...,"installed":true,"strategy":"snowflake","workerSource":"config","workerId":1,"segmentStore":"cache","step":1000}` |
| 雪花模式 `GET /id/demo/next` | HTTP 200，`{"code":0,"data":{"id":"378817169368027136","firstSeen":true}}` —— `id` 为**字符串** |
| 雪花模式 `GET /id/demo/batch?size=2000` | HTTP 200，`size=2000, unique=2000, duplicated=0, costMillis=2`，`first` / `last` 为字符串 |
| 雪花模式 `GET /id/demo/segment?size=450` | HTTP 200，`step=1000`，`index` / `start` / `end` / `currentSegment` 均以字符串下发 |
| `stop-demo.bat` | 正确终止 PID，18140 释放，无残留 java 进程 |
| 号段模式 `GET /id/demo/state` | HTTP 200，`{"strategy":"segment","segmentStore":"local","step":200}`，**`workerSource` / `workerId` 按 design.md §13 省略** |
| 号段模式 `GET /id/demo/next` | HTTP 200，`id="2"`（字符串；本次 `1` 已被 `state` 的可用性探测消耗，属 design.md §13 既定语义） |
| 号段模式 `GET /id/demo/batch?size=2000` | HTTP 200，`size=2000, unique=2000, duplicated=0`，`first="3" last="2002"` |
| 号段模式 `GET /id/demo/segment?size=450` | HTTP 200，`step=200`，返回 **3 段**（`10:[2001,2200]` count 198、`11:[2201,2400]` count 200、`12:[2401,2600]` count 52，合计 450）—— 跨段可见，`generated=450` |
| `stop-demo.bat` + 端口检查 | 18140 无监听、无残留 java 进程 |
| 页面离线 | `src/main/webapp` 下无外链 `<script src="http...">` / `@import url(...)` / CDN 引用；Vue 走本地 `assets/lib` |

> 冒烟细节：`/id/demo/state` 的 `installed` 由「调用 `IdKit.nextId()` 是否抛 `IllegalStateException`」判定，因此每次调用会消耗一个 ID（design.md §13 既定），号段模式下表现为首个可见号从 `2` 开始。

---

## 7. 未覆盖项与理由（维护者知悉项）

| # | 未覆盖项 | 理由 | 影响 / 建议 |
| --- | --- | --- | --- |
| 1 | ~~§7.5 场景 10 / 消息 H（号段 `store=cache` 但后端 `supportsCas=false`）~~ —— **已于本轮固化，不再是缺口** | 原缺口已补齐：`support/FakeCache.notShared()`（`supportsCas()==false`）与 `IdPluginTest#notSharedBackendFailsWithMessageH` 均已存在并纳入测试 | 现由 §2 场景 10 的 `IdPluginTest#notSharedBackendFailsWithMessageH` 覆盖，逐字断言消息 H 且不留半装配；保留本行仅为变更留痕 |
| 2 | `LockWorkerIdSource` 内部「续期失败 → `healthy=false`」与线程名 / 周期 / `close()` join 逻辑 | 真实续期周期下限 1000ms，离线纪律不允许秒级等待；`FakeLockStore.renew` 恒成功 | 「拒发号」语义已由 `FakeWorkerIdSource#breakLease()`（消息 E）覆盖，但**置位逻辑本身**未被断言。建议给 `FakeLockStore` 增加 `renewFails()` 并把租约配置到 1000ms 上界内可控验证（或用可注入时钟的线程替代），否则该分支靠评审保证 |
| 3 | **§7.5 场景 20**（`close()` 时 `Lock#release()` 抛异常被 `log.error` 后吞掉） | `FakeLockStore.release` 恒返回 `true`，无法注入释放失败 | 停机路径容错，风险低；建议随第 2 条一并补（`FakeLockStore.releaseThrows()`） |
| 4 | `internal.CacheAccess`（`CounterKit.use()` + `supportsCas()` 校验）无独立用例 | 仅经 `IdPluginTest` 的 `cache` 来源 / 号段 `cache` 分支间接执行；`CacheKit` / `CounterKit` 的真实装配错误路径（消息 P）已由装配用例覆盖 | 与第 1 条同源；第 1 条已由 `IdPluginTest#notSharedBackendFailsWithMessageH` 固化（含 `supportsCas()` 校验路径），本条仅保留说明，不单列用例 |
| 5 | **真 Redis 的端到端路径**（`CacheSegmentStore` 真实 `INCRBY`、`LockWorkerIdSource` 真实抢占 + 续期、TTL 过期触发的号段回退） | 无集成用例、pom 无 `redis.integration` 开关（见 §5.1）；离线单测用 `FakeCounter` / `FakeLockStore` / `FakeSegmentStore` | 单元层已覆盖「参数下传 + 返回值语义 + 失败包装 + 回退检测」；真后端语义（原子性、TTL 续期、跨实例空洞）**未验证**，属 v1 已知缺口 |
| 6 | `IdDeps` 的 `LinkageError` 分支 | `HidingClassLoader` 只伪装 `ClassNotFoundException` | 防「类存在但依赖缺失」的场景，风险极低 |
| 7 | 断网 `mvn clean verify`（AC-14 的断网口径） | 复验环境未切网；旁证为门禁 ⑭（测试源码无外网 URL）+ 测试桩零外部服务 + 依赖树无网络客户端 | 建议在发布前于断网 / `-o` 离线模式再跑一次全量确认 |

> 以上均**不改变**契约语义；新增公共类型或放宽门禁须先改 `docs/design.md` §5 / §10，再改断言。

---

## 8. 不适用项（本模块为库/SDK，不得据此判失败）

| 常规测试面 | 结论 | 依据 |
| --- | --- | --- |
| 表结构 / `Db+Row` / `Generator` 产物 / SQL 安全 / 事务回滚 | 不适用 | 零 DB；门禁 ⑤ 反向断言全模块无 `import java.sql.` |
| `@Path` Service / HIO / 路由 / 拦截器 | 不适用 | 模块本体无入站 HTTP 面（design.md §1.2）；HTTP 只在 demo，见 §6 |
| 前端页面与原型基线门禁 | 不适用 | demo 单页由前端直接实装，不经原型与 QA 基线门禁（design.md §12 事项 7） |
| 多实现契约测试基类（`XxxContractTest` 继承式） | 部分适用 | 两个生成器共享 `IdGenerator` 契约，但二者语义不同（雪花按毫秒、号段按存储），共同底线已在 design.md §7.1/§7.4 分列并由各自测试类核验；两个 `SegmentStore` 实现的共同底线由 `SegmentIdGeneratorTest`（真实 `LocalSegmentStore`）+ `CacheSegmentStoreTest`（`FakeCounter`）交叉覆盖，未单列契约基类 |

---

## 9. 维护要求

- 新增失败路径必须同时落三处：`internal.IdErrors` 的构造方法、`docs/design.md` §7.6 消息表、以及本文 §2 的逐字断言用例。
- 新增配置键必须同时落三处：`internal.IdConfig`、`docs/design.md` §8、`README.md` §3.4，并在 `IdConfigTest` 补齐默认值 / 边界 / 越界 / 非整数四类断言。
- 门禁只允许**加严**：新增例外必须先改 `docs/design.md` §2 再改断言；不得为兼容错误用法放宽。
- 用例数与 surefire 口径以本文件 §1 为准；任何临时探针不得计入正式用例数（见 §4.1）。
