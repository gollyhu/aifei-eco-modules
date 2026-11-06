# aifei-lock 设计文档

本文件是 `aifei-lock` 的**契约与设计事实来源**。`README.md` 面向使用者，`AGENTS.md` 面向开发代理与贡献者；三者冲突时以本文件为准。

## 1. 定位与范围

本模块只做**分布式锁**，不做分布式事务（跨库一致性用 outbox + 幂等消费或 Saga）。锁语义只依赖三条原子命令：

| 动作 | 存储侧命令 |
| --- | --- |
| 加锁 | `SET key token PX lease NX`（cache 侧 `putIfAbsent`） |
| 释放 | Lua CAS 删除（cache 侧 `removeIfEquals`） |
| 续期 | Lua CAS 续期（cache 侧 `expireIfEquals`） |

加锁成功后 cache 侧会追加一次 `cacheNames()` 登记（`SADD`），属**附加命令**：不参与互斥判断，其失败也不得改变加锁结论（见决策 7）。

**不做**：分布式事务、单机 / caffeine 后端（静默无互斥比报错更危险）、可重入、公平排队、读写锁、锁丢失回调、JDBC 后端、多实例、自动续期看门狗。

## 2. 依赖与发布序列

发布序列固定为 `aifei-cache:1.1.0` → `aifei-lock:1.0.0`（先发、不同批发布）。

| 依赖 | 版本 / 范围 | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei-all` | 1.1.2，`provided` | 消费方自带；日志门面 `cn.aifei.log` 由它提供 |
| `io.github.gollyhu:aifei-cache` | ≥ 1.1.0，`optional` | 共享存储能力来源；**不向下游传递** |
| 不直接引入 Jedis | — | 连接与编解码全部由 `aifei-cache` 负责 |

**optional 的代价（必须写进消费方须知）**：消费方漏引入 `aifei-cache` ≥ 1.1.0 时，`cn.hg.aifei.lock.cache.CacheLockStore` 在**类加载期**即 `NoClassDefFoundError`，早于 `LockPlugin` 的启动校验，无法转成可读错误。

依赖的 cache 能力（1.1.0 新增或既有）：

| 能力 | 性质 |
| --- | --- |
| `putIfAbsent(cacheName, key, value, Duration)` | 既有；Redis 侧为单条 `SET ... PX ... NX`，不得退化为两条命令 |
| `removeIfEquals(cacheName, key, expectedValue)` | 1.1.0 新增；安全释放的唯一基础 |
| `expireIfEquals(cacheName, key, expectedValue, Duration)` | 1.1.0 新增；`renew()` 基础 |
| `supportsCas()` | 1.1.0 新增；**无 I/O 的纯能力声明**，启动校验依据 |
| `getTtl(cacheName, key)` | 既有；仅测试与排障，不进实现路径 |

cache 侧异常契约：参数非法抛 `IllegalArgumentException`（**先于**能力检查）；`false` 表达确定的「未发生」；存储不可用、超时、序列化失败**必须抛运行时异常，不得吞成 `false`**。

## 3. 公共面

根包固定 **3 个类型**，新增公共类型须先过设计决策并同步门禁断言：

| 类型 | 职责 |
| --- | --- |
| `Lock` | 不可变锁句柄（`final class`、全字段 final、构造器包私有） |
| `LockKit` | 加锁入口（唯一创建句柄的地方） |
| `LockException` | 存储侧失败的运行时异常 |

公共 API 零 `cn.aifei.*`、零第三方类型、零 `cn.hg.aifei.cache.*`。

**句柄语义**：

- 四个 `final` 字段：`name` / `token` / `lease` / `store`；`token` 为 UUID 字符串且不对外暴露（不提供 `token()` 与 `lease()`）。
- `lease` 是创建时的租约时长，`renew()` 以它重设 TTL——无参、租约随句柄携带。
- `release()` / `renew()` / `close()` 无 name / token 入参，配对安全由句柄绑定保证。
- 不提供 `getLock(name)`；句柄只对应一次持有，不能存全局字段。
- 不重写 `equals` / `hashCode`（见决策 3）。

## 4. 契约

### 4.1 锁名与参数

锁名规则唯一定义点 `internal.LockNames`：非空、不含空白、长度 ≤ 128、允许 `:` 分隔。

参数校验先于装配检查与小写实现路径：锁名非法、`lease < 1ms`、`wait < 0`（含 `null`）→ `IllegalArgumentException`，**不被包装**。租约上限（`Integer.MAX_VALUE` 秒）不在本模块校验，由 cache 的 `requireTtl` 承担并原样抛 `IllegalArgumentException`；本模块只校验下限，以保证「参数错误」早于「装配 / 能力错误」暴露。

### 4.2 加锁

- 立即尝试：`tryLock(name, lease)`。成功构造句柄，抢不到返回 `null`。
- 有限等待：`tryLock(name, lease, wait)`。`wait > 0` 时**先立即尝试一次**（不睡眠），失败后按固定 50ms 间隔轮询，无退避、无抖动、无配置。
- 单次睡眠取 `min(50ms, 剩余等待)`；截止时间用单调时钟（`System.nanoTime()`），否则 `wait < 50ms` 会睡过窗口、系统时钟回拨会多等。
- `token` 由 `LockNames.newToken()` 生成后作为入参传给存储层；存储层不得自行生成（CAS 期望值的一部分，生成点唯一）。

### 4.3 等待与中断

| 场景 | 行为 |
| --- | --- |
| `wait > 0`，等待期间被 `interrupt()` | 捕获 `InterruptedException`，**恢复中断位**，返回 `null`，不抛异常 |
| `wait == 0` | 不检查中断位，正常尝试加锁（对齐 `ReentrantLock#tryLock()`） |
| 轮询期间任一次加锁抛 `LockException` | **立即上抛，不继续轮询**（存储故障不得被「等满后返回 null」掩盖成「抢不到锁」） |
| 存储调用期间被中断 | 不主动检查，由连接超时处理，可能包装为 `LockException` |

中断不是存储故障，**不得**包装成 `LockException`；恢复中断位是硬要求。

### 4.4 释放与续期

- 必须 CAS（值匹配才执行），不得使用无条件 `remove` / `expire`；比较基准为序列化后的字节（cache 侧以二进制安全 `eval` 按字节传递期望值，不经字符串中转；`token` 为 `String` 时字节确定性成立）。
- `release()`：`true` = 本次释放成功；`false` = 非持有者（已过期或已释放）。
- `renew()`：`true` = 成功；`false` = 值不匹配（锁已丢失），业务须立即停手。
- `close()`：等价于 `release()` 并吞掉异常，永不抛；失败由 `release()` 记 ERROR，`close()` 不再重复记录。

### 4.5 异常与日志

包装规则唯一定义点 `internal.LockErrors`：

| 输入 | 处理 |
| --- | --- |
| `IllegalArgumentException` | 原样抛，不包装 |
| `LockException` | 不重复包装 |
| 其他 `RuntimeException` | 包装为 `LockException` 并保留 cause |
| `Error` | 不捕获 |

消息格式（全部由 `LockErrors` 构造）：

- `lock acquire failed, name: <name>`
- `lock release failed, name: <name>`
- `lock renew failed, name: <name>`
- `lock backend is not a shared store`
- `lock backend capability check failed`
- `cache __MAIN__ instance is not initialized`
- `LockHolder has not been initialized. Add LockPlugin first.`

日志统一经 `cn.aifei.log.Log`（`Log.get(本类.class)`），不引入任何日志绑定，禁止 `System.err` / `org.slf4j.*` / 自建通道。记录口径：`release()` / `renew()` 的 `true` 与 `false` 记 DEBUG，存储故障记 ERROR 并带 cause 堆栈，`close()` 吞掉的异常记 ERROR（由 `release()` 完成的那条）。日志文案中的锁名一律取自句柄 `name()`。

### 4.6 装配与启动校验

- **配置键 0**：租约必填、等待显式传；`LockPlugin` 无参构造，不设 `lock.enabled`。
- **挂载主缓存实例，不新建连接**：锁键落在主缓存实例命名空间下的专用 `cacheName` `__LOCK__`（保留分区，业务不得读写，尤其不得 `clear("__LOCK__")`）。不新增任何 `lock.*` 配置。
- **启动校验 = 能力查询，非白名单**：`CacheLockStore.open()` 用无参 `CacheKit.use()` 取 `__MAIN__` 主缓存实例，调其 `supportsCas()`；为 `false` 时抛 `IllegalStateException("lock backend is not a shared store")`。本模块不维护后端类型白名单：cache 新增共享后端时能力声明自动生效，本模块零改动。
- **能力查询自身无 I/O**：启动校验不探活、不建连、不发存储命令；存储故障推迟到首次 `tryAcquire`。
- **能力查询失败（防御分支）**：`supportsCas()` 自身抛 `RuntimeException` 时包装为 `IllegalStateException("lock backend capability check failed")` 并保留 cause，`Error` 不捕获。内置后端不会触发该分支；保留它是为了不把「实例方法抛异常」误当成「后端不支持」。
- **装配顺序**：要求 `CachePlugin` 先装配并已 `start()`。cache 注册表未初始化或主实例缺失时 `open()` 抛 `IllegalStateException("cache __MAIN__ instance is not initialized")`，cause 保留 cache 侧原始异常。
- **未装配**：`LockHolder` 未注册时 `LockKit.tryLock(...)` 抛 `IllegalStateException("LockHolder has not been initialized. Add LockPlugin first.")`。**禁止降级为无锁执行、禁止返回 null**。
- **内部 SPI**：`internal.LockStore` 三方法（`tryAcquire` / `release` / `renew`，均显式传 token），实现为 `cache.CacheLockStore`。`LockHolder` 只负责 `register(LockStore)` / `clear()` 与运行期读取，不做工厂。
- **句柄创建**：`Lock` 构造器包私有，`LockKit`（同包）直接构造。

### 4.7 能力边界（README 必写）

本模块提供的是**互斥**，不是**一致性**。不在保证范围：租约到期后仍有并发执行（双持有）、Redis 主从切换导致锁丢失、GC / 长时间停顿期间锁过期、跨库跨服务以及 DB 与 MQ 的原子性、无锁降级（未装配即抛 `IllegalStateException`）。

## 5. 数据格式（发布后不得变更）

| 项 | 取值 | 说明 |
| --- | --- | --- |
| 锁所在缓存名 | `__LOCK__` | 主缓存实例命名空间下的保留分区，唯一定义点 `internal.LockNames#CACHE_NAME` |
| 锁键（cache key） | 锁名本身 | 允许 `:`，故物理键表现为 `namespace:__LOCK__:order:pay:1` |
| 锁值（cache value） | UUID 字符串 | 一次持有一个 token，释放与续期的 CAS 期望值 |
| CAS 比较 | 序列化后字节 | cache 侧按字节比较，token 为 `String` 时字节确定性成立 |

`__LOCK__` 不含 `:`，通过 cache 侧 `requireCacheName` 校验。

## 6. 设计决策

### 决策 1：`Lock` 用 final class，下游不能 mock

**取舍**：句柄不可伪造、不可继承，代价是下游无法用 JDK 代理替身；Java 8 下 Mockito 需 inline mock maker 才能 mock final class。

**替代路径**：替身放在 `LockStore` 层——测试经 `LockHolder.register(FakeLockStore)` 注入，不对句柄做 mock。

### 决策 2：`supportsCas()` 抛异常时 `open()` 包装为 `IllegalStateException`

包装为 `IllegalStateException("lock backend capability check failed")` 并保留 cause，`Error` 不捕获；启动期失败一律 `IllegalStateException`，不复用运行期的 `LockException` 包装规则。

**理由**：cache 1.1.0 的 `supportsCas()` 无 I/O，该分支对内置后端不可达，属防御与测试替身路径；保留它是为了「实例方法抛异常」不被误判成「后端不支持」。

### 决策 3：`Lock` 不重写 `equals` / `hashCode`

采用对象身份比较——句柄代表「一次持有」，同名两次持有互不相等。若按 `name` 相等，两个句柄放进 `Set` / `Map` 会互相覆盖，掩盖重复持有。

### 决策 4：构造句柄失败时锁由租约兜底

`tryAcquire` 返回 `true` 之后才构造句柄，构造过程不执行 I/O、不抛受检异常；唯一可能的失败是 `Error`（如 OOM）。此时**不做补偿释放**——避免在 `Error` 路径上再做一次 I/O，锁由租约自然过期。

### 决策 5：等待窗口用例标注离线，不进默认 CI

`wait = 10ms` 且总耗时 ≤ 35ms 的断言对 CI 抖动敏感，故用 `-Dlock.timing=true` 开关控制、默认跳过；容差**不放大到 100ms**，否则「睡满剩余等待」的退化实现也能通过。

### 决策 6：`release()` 也遵守「异常 ≠ 丢失」

抛 `LockException` 只表示「是否删除未知」，重试安全且不推断锁状态；只有 `renew()` 返回 `false` 才是「确认丢失」。理由：CAS 删除重试不会误删他人锁，而把「未知」当成「已丢失」会诱导业务做多余的补偿动作。

### 决策 7：拒在 `aifei-cache` 侧为 `putIfAbsent` 的 `SADD` 登记失败做降级

那会修改 cache 既有方法行为，与其 1.1.0「只新增条件操作」的版本边界冲突。

**代价与承接**：登记失败时 `LockKit.tryLock` 抛 `LockException`（**不返回 `null`**），而锁可能已落库；调用方按「结果未知」处理，残留锁由租约自然过期兜底，最坏窗口 = 本次 `lease`。本模块**不实现**「异常后二次 `get` 比对 token」的补偿确认：那会引入第三条往返与新的竞态，违反「锁语义只依赖三条原子命令」。登记缺失只让 `cacheNames()` 少一个名字，不影响互斥。

## 7. 测试策略

- 运行期实例经 `LockHolder.register(...)` / `clear()` 注入与清理，测试结束必须 `clear()`。
- 非集成用例注入 `FakeLockStore`，不连真实 Redis；缓存替身用动态代理实现 `Cache` + `CounterFactory`，只提供能力声明。
- 不设契约测试基类：根 `AGENTS.md` 的该要求限定为「公共接口 + 多实现」，而 `Lock` 为 final class 唯一实现形态。
- 集成测试默认关闭（`-Dredis.integration=true` 开启），一旦开启连不上 Redis 必须失败，不得静默跳过。
- **计时用例默认关闭**（`-Dlock.timing=true` 开启）：`wait = 10ms`、总耗时 ≤ 35ms 的断言对 CI 抖动敏感，与 Redis 集成测试同策略，本地 / 离线环境执行。
- 测试须先装配日志工厂：非集成用例在 `@BeforeClass` 调 `LogKit.get().setLogFactory(...)` 装配 Noop 实现，否则 `Log.get(...)` 路径失败；测试替身不引入任何日志绑定依赖。
- 用例清单：`ArchitectureBoundaryTest`（3 条门禁）、`LockKitTest`（20 条契约）、`LockPluginTest`（5 条启动校验）、`CacheLockStoreIntegrationTest`（5 条，默认跳过）、`LockTimingOfflineTest`（1 条，默认跳过）。

## 8. 验收对照

| 验收项 | 落点 |
| --- | --- |
| 误删防护 | `LockKitTest#releaseAfterExpiryKeepsNewHolder`、集成 `expiryAllowsTakeOverWithoutDeletingNewHolder` |
| 幂等释放 | `LockKitTest#releaseIsIdempotent` |
| 配对安全 | `LockKitTest#handlesArePairedByName` |
| 加锁原子性 | 集成 `acquireCarriesLease`；`LockKitTest#lockPartitionNameIsAcceptedByCache` 钉住 `__LOCK__` 不含 `:` |
| 登记失败按「结果未知」承接 | `LockKitTest#acquireFailureThrowsInsteadOfNull` |
| 异常语义 | `LockKitTest#releaseFailureIsRetryable` / `#renewFailureIsRetryable` / `#closeSwallowsFailure` |
| 续期语义 | `LockKitTest#renewUsesHandleLease` / `#renewFailsWhenLockLost` |
| 中断语义 | `LockKitTest#interruptDuringWaitReturnsNull` / `#zeroWaitIgnoresInterruptStatus` |
| 等待窗口（离线） | `LockTimingOfflineTest#waitWindowIsRespected` |
| 能力校验 | `LockPluginTest#startFailsForNonSharedBackend` / `#capabilityCheckedOnceAndStoppedClearsHolder` |
| 启动顺序 | `LockPluginTest#startFailsWithoutMainCacheInstance` / `#startFailsWhenMainInstanceMissing` / `#startWrapsCapabilityFailure` |
| 未装配 | `LockKitTest#notInitializedThrows` |
| 包边界与公共面 | `ArchitectureBoundaryTest` 三条门禁 |
