# aifei-lock 测试文档

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据与契约，含 §7 测试策略 / §8 验收对照）、`README.md`（使用说明）

本文面向测试维护者：说明测试分层、用例清单、离线纪律、集成与计时开关、运行方式与未覆盖项。契约与异常语义以 `docs/design.md` 为准，本文不复制契约细节，只落地测试策略与用例映射。

---

## 1. 测试原则

- **零外部依赖（默认）**：非集成用例全部离线，不连真实 Redis、不连库、不启 HTTP；架构门禁为 surefire 源码文本断言。
- **测试替身**：非集成用例注入 `support/FakeLockStore`（`LockStore` 的内存实现），缓存能力声明用动态代理实现 `Cache` + `CounterFactory`，只提供能力探测，不发起网络请求。
- **状态隔离**：运行期实例经 `LockHolder.register(...)` / `clear()` 注入与清理，测试结束必须 `clear()`；各测试类 `@Before` 重新装配日志工厂，保证用例间无残留。
- **日志工厂先装配**：`Log.get(...)` 路径依赖已装配的 `LogFactory`，非集成用例在 `@BeforeClass` 调 `LogKit.get().setLogFactory(new NoopLogFactory())` 装配 Noop 实现；测试替身不引入任何日志绑定依赖。
- **不设契约测试基类**：根 `AGENTS.md` 的该要求限定为「公共接口 + 多实现」，`Lock` 为 `final class` 唯一实现形态（设计 §6 决策 1），共同底线通过 `LockKitTest` + 集成用例交叉覆盖。

---

## 2. 测试分层与用例清单（`@Test` 合计 34）

| 测试类 | 层次 | 用例数 | 覆盖 |
| --- | --- | --- | --- |
| `ArchitectureBoundaryTest` | 架构门禁（源码扫描） | 3 | 包边界、公共面、框架类型归属 |
| `LockKitTest` | 契约（门面 + 语义） | 20 | 加锁 / 冲突 / 释放 / 续期 / 等待与中断 / 异常语义 / 参数校验 |
| `LockPluginTest` | 装配（启动校验） | 5 | 启动期依赖与能力校验、失败清理 |
| `LockTimingOfflineTest` | 计时（**默认跳过**） | 1 | 等待窗口 `waitWindowIsRespected` |
| `cache.CacheLockStoreIntegrationTest` | Redis 集成（**默认跳过**） | 5 | 真实 `CacheLockStore` 端到端语义 |

> 计数口径：`grep -r '@Test' aifei-lock/src/test` 实测 **34**（3 + 20 + 5 + 1 + 5）。默认 `mvn clean verify` 执行 **28** 例（跳过 6 例方法：1 计时 + 5 集成）。
> **surefire 报告口径提示**：集成类用 `@BeforeClass` Assume、计时类用 `@Before` Assume 跳过整类，故 `target/surefire-reports` 中这两类各记为 1 条 `Skipped`（`Tests run: 30, Skipped: 2`，其中 28 条实际执行），与按 `@Test` 方法计的「跳过 6 例」是同一事实的两种统计口径，勿混读。

### 2.1 架构门禁 `ArchitectureBoundaryTest`（3 例）

对 `src/main/java` 下源码做文本扫描：

| 用例 | 断言 |
| --- | --- |
| `cacheTypesOnlyInCachePackage` | `cn.hg.aifei.cache.*` 类型仅出现在 `cache` 包 |
| `frameworkTypesOnlyInBridgePackage` | 框架类型仅出现在 `aifei` 接入包 |
| `rootPackageExactThreeFiles` | 根包公共类型精确 3 个（`Lock` / `LockException` / `LockKit`） |

门禁自带前置校验：源码目录存在、扫描结果非空。**须在模块根目录执行**（相对路径 `src/main/java`）。

### 2.2 契约测试 `LockKitTest`（20 例）

| 用例 | 断言 |
| --- | --- |
| `acquireReturnsHandleBoundToName` | 加锁返回句柄绑定锁名 |
| `acquireConflictReturnsNull` | 锁被占用返回 `null` |
| `acquireFailureThrowsInsteadOfNull` | 存储失败按「结果未知」抛异常，不返回 `null` |
| `parameterValidationPrecedesAssembly` | 参数校验先于装配 |
| `notInitializedThrows` | 未装配时调用抛异常 |
| `releaseIsIdempotent` | 重复释放幂等 |
| `releaseAfterExpiryKeepsNewHolder` | 租约过期后新持有者的锁不被旧句柄误删（误删防护） |
| `releaseFailureIsRetryable` | 释放失败可重试 |
| `releaseFailureFollowedByMissIsNotAnError` | 释放失败后未命中不报错 |
| `renewUsesHandleLease` | 续期采用句柄租约 |
| `renewFailsWhenLockLost` | 锁丢失后续期失败 |
| `renewFailureIsRetryable` | 续期失败可重试 |
| `handlesArePairedByName` | 句柄按锁名配对（配对安全） |
| `handlesUseIdentityEquality` | 句柄用恒等相等（不重写 `equals`/`hashCode`） |
| `waitTimeoutReturnsNull` | 等待超时返回 `null` |
| `interruptDuringWaitReturnsNull` | 等待中被中断返回 `null` |
| `zeroWaitIgnoresInterruptStatus` | 零等待忽略中断状态 |
| `pollingStopsOnStoreFailure` | 存储失败时停止轮询 |
| `closeSwallowsFailure` | `close()` 吞掉失败（异常语义） |
| `lockPartitionNameIsAcceptedByCache` | `__LOCK__` 分区名不含 `:`，被缓存接受 |

### 2.3 装配测试 `LockPluginTest`（5 例）

| 用例 | 断言 |
| --- | --- |
| `startFailsWithoutMainCacheInstance` | 无主缓存实例启动失败 |
| `startFailsWhenMainInstanceMissing` | 主实例缺失启动失败 |
| `startFailsForNonSharedBackend` | 后端 `supportsCas=false`（非共享）启动失败 |
| `startWrapsCapabilityFailure` | 能力探测失败被包装 |
| `capabilityCheckedOnceAndStoppedClearsHolder` | 能力只探测一次，`stop()` 清空持有器 |

### 2.4 计时用例 `LockTimingOfflineTest`（1 例，默认跳过）

| 用例 | 断言 |
| --- | --- |
| `waitWindowIsRespected` | `wait=10ms` 时等待窗口被遵守，总耗时 ≤ 35ms |

对 CI 抖动敏感，默认跳过（设计 §6 决策 5）；用 `-Dlock.timing=true` 在本地 / 离线环境开启。

### 2.5 Redis 集成用例 `cache.CacheLockStoreIntegrationTest`（5 例，默认跳过）

| 用例 | 断言 |
| --- | --- |
| `acquireCarriesLease` | 加锁携带租约（原子性） |
| `mutualExclusion` | 互斥 |
| `expiryAllowsTakeOverWithoutDeletingNewHolder` | 过期后可接管且不误删新持有者 |
| `renewResetsToHandleLease` | 续期重置为句柄租约 |
| `renewFailsAfterExpiry` | 过期后续期失败 |

通过 `@BeforeClass` 装配真实 `CachePlugin` / `CacheLockStore`，只在类级装配一次（框架 `@Inject` 单例不允许重复注册），每个用例前清空锁分区。**连不上 Redis 必须直接失败，不得静默跳过**。

---

## 3. 运行方式与开关

**全部命令在模块根目录 `aifei-lock/` 执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                   # 主验证：默认 28 例（跳过 1 计时 + 5 集成），必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest             # 仅架构门禁（3 例）
mvn test -Dtest=LockKitTest                          # 仅契约用例（20 例）
```

### 3.1 集成开关（默认关闭）

- **默认关闭**：pom `redis.integration=false`，`CacheLockStoreIntegrationTest` 用 `Assume.assumeTrue(ENABLED)` 跳过 5 例，`mvn clean verify` 全离线通过。
- **开启方式**：需本机或容器内 Redis，用 `-Dredis.integration=true` 开启，连接地址取 `-Dredis.uri`（缺省 `redis://127.0.0.1:6379/0`）：

```bash
mvn verify -Dredis.integration=true -Dredis.uri=redis://127.0.0.1:6379/0
```

- **连不上即失败**：集成用例启动期探活写入会直接抛异常，不静默跳过（设计 §7）。

### 3.2 计时开关（默认关闭）

- **默认关闭**：pom `lock.timing=false`，`LockTimingOfflineTest` 用 `Assume.assumeTrue(ENABLED)` 跳过 1 例。
- **开启方式**（本地 / 离线环境，CI 抖动敏感）：

```bash
mvn test -Dlock.timing=true
```

### 3.3 两个开关一起开

```bash
mvn verify -Dredis.integration=true -Dlock.timing=true -Dredis.uri=redis://127.0.0.1:6379/0
```

---

## 4. 离线纪律与依赖

| 检查项 | 结论 |
| --- | --- |
| 零联网（默认） | 非集成用例注入 `FakeLockStore` + 缓存能力替身，无任何网络请求 |
| 日志无绑定 | 测试只装配 Noop `LogFactory`，替身不引入日志绑定依赖 |
| 状态隔离 | 每例 `LockHolder.clear()` + 重新装配日志工厂，无状态残留 |
| 依赖范围 | `aifei-all`（provided）+ `aifei-cache`（provided/optional）+ junit（test） |

---

## 5. 未覆盖项与人工验证

| 项 | 说明 |
| --- | --- |
| Redis 真后端端到端（默认） | 由 `CacheLockStoreIntegrationTest` 承担，默认跳过；需 Redis 时按 §3.1 开启 |
| 计时窗口对 CI 抖动的敏感性 | 由 `LockTimingOfflineTest` 承担，默认跳过；本地按 §3.2 开启 |
| 多实例互斥 / 集群场景 | v1 不覆盖（见设计 §1 范围与 README 能力边界） |
| 真实 `aifei-cache` 装配异常路径 | 由 `LockPluginTest` 的能力探测用例 + 集成用例交叉覆盖，非集成态用替身 |

---

## 6. 变更时的测试要求

- 新增或改变公共行为（锁名规则、等待/中断语义、异常措辞、装配校验）时，同步更新本文用例清单与 `docs/design.md`。
- 触及包边界或公共面（根包 3 类型、`cn.hg.aifei.cache.*` / 框架类型归属）时，必须先让 `ArchitectureBoundaryTest` 通过。
- 新增用例须离线可跑、独立幂等；日志工厂与 `LockHolder` 的装配/清理成对，不留残留。
- 用例数与 surefire 口径以本文 §2 为准；开关用例（计时、集成）默认不计入 `mvn clean verify` 的 28 例基线。
