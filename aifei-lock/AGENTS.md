# aifei-lock 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档的分工：`README.md` 面向使用者（安装、用法、使用顺序、反例、能力声明、异常语义）；`docs/design.md` 面向设计者与实现者（契约、7 条设计决策、数据格式、验收对照，是**设计事实来源**）；本文件面向开发代理与贡献者。**不单设 `docs/testing.md`**：测试策略写在 `docs/design.md` 第 7 节与本文件的测试约定里。

## 开发前提

- **改代码前必读 `docs/design.md`**。需求与现有设计冲突时，先明确并更新设计决策，再改代码。
- 未经设计决策，不得自行确定契约类行为：锁名规则、异常类型与消息、等待与中断策略、`cacheName` 常量、日志口径、CAS 比较语义。
- 本模块遵循仓库根 `AGENTS.md` 的共同底线，冲突时以更严格者为准。

## 模块定位

`aifei-lock` 只做**分布式锁**，不做分布式事务。锁语义只依赖三条原子命令（`SET NX PX` 加锁、Lua CAS 删除释放、Lua CAS 续期），加锁后的 `SADD` 登记属附加命令，不参与互斥判断。

**不做**：分布式事务、单机 / caffeine 后端、可重入、公平排队、读写锁、锁丢失回调、JDBC 后端、多实例、自动续期看门狗。

## 硬性约束

### 技术基线

- 代码、依赖与 API 兼容 **Java 8**：语法与 API 双约束；主 JAR 全部 class 的 major version 必须为 **52**。
- 源码 UTF-8、缩进 4 空格；注释一律简体中文（保留英文原文的只有 javadoc 标签名、代码标识符、异常消息）；文件头版权块不得改写。
- **`cn.aifei:aifei-all` 为 `provided`**，消费方自带。
- **`io.github.gollyhu:aifei-cache` ≥ 1.1.0 为 `optional`**：不向下游传递。消费方漏引入时 `cache.CacheLockStore` 在类加载期即 `NoClassDefFoundError`（早于启动校验），该须知必须保留在 README 与 `docs/design.md`。
- **不直接引入 Jedis**；连接、编解码与 TTL 校验全部由 `aifei-cache` 负责。

### 公共面收敛

- 公共 API 零 `cn.aifei.*`、零第三方类型、零 `cn.hg.aifei.cache.*`。
- 根包固定 **3 个类型**：`Lock` / `LockKit` / `LockException`。`Lock` 为 final class，只暴露 `name()`，不暴露 `token()` 与 `lease()`。新增公共类型须先过设计决策并同步门禁断言。
- `internal` / `cache` / `aifei` 均属**非承诺 API**：实现类为装配需要可声明 `public`，但不是公共 API，消费方不得引用。

## 包边界与门禁

`ArchitectureBoundaryTest` 扫描 `src/main/java` 下全部 `.java`，因此**必须在模块根目录执行**：

| 门禁 | 断言 |
| --- | --- |
| ① `cacheTypesOnlyInCachePackage` | `cn.hg.aifei.cache.*` 只允许出现在 `lock/cache` 包 |
| ② `frameworkTypesOnlyInBridgePackage` | `cn.aifei.*`（框架类型）只允许出现在 `lock/aifei` 包，**唯一例外是日志门面 `cn.aifei.log`** |
| ③ `rootPackageExactThreeFiles` | 根包直属 `.java` 精确 3 个（`Lock` / `LockKit` / `LockException`），实现类一律不放根包 |

**不得为兼容错误用法而放宽或绕过门禁断言。**

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 锁名校验（非空、无空白、≤ 128）与 token 生成 | `internal.LockNames` |
| 锁所在 `cacheName` 常量（`__LOCK__`） | `internal.LockNames#CACHE_NAME` |
| 异常消息构造与运行期包装规则 | `internal.LockErrors` |
| 等待间隔、剩余等待收窄、中断恢复 | `LockKit` |
| 租约下限校验（1ms） | `LockKit`；上限由 cache 的 `requireTtl` 承担 |
| CAS 比较语义 | `cache.CacheLockStore` 经 cache 的 `removeIfEquals` / `expireIfEquals` |
| 运行期实例注册与读取 | `internal.LockHolder` |

## 行为契约要点速查

- **抢不到 ≠ 故障**：抢不到锁返回 `null` / `false`（DEBUG）；存储故障抛 `LockException`（ERROR），**不得返回 `null`**。
- **异常 ≠ 丢失**：`LockException` 只表示「结果未知」；只有 `renew()` 返回 `false` 才是「确认丢失」。`release()` 重试安全，不推断锁状态。
- **等待语义**：`wait > 0` 先立即尝试一次，之后固定 50ms 轮询、单调时钟截止、单次睡眠取 `min(50ms, 剩余)`；被中断返回 `null` 并**恢复中断位**；`wait == 0` 不检查中断位；轮询期间存储故障立即上抛。
- **`close()` 永不抛**：等价于 `release()` 并吞掉异常，只记 ERROR；需感知释放失败时显式调用 `release()`。
- **装配**：`LockPlugin` 必须晚于 `CachePlugin`；启动期只做一次无 I/O 的能力查询，失败一律 `IllegalStateException`，不走运行期 `LockException` 包装规则；未装配时取用抛 `IllegalStateException`，**禁止无锁降级**。
- **句柄**：不可变、身份比较、只对应一次持有；测试替身放在 `LockStore` 层（`LockHolder.register(FakeLockStore)`），不 mock `Lock`。
- **日志**：统一经 `cn.aifei.log.Log`，不引入日志绑定，禁止 `System.err` / `org.slf4j.*`。

## 测试约定

- 非集成用例注入 `FakeLockStore`，缓存替身用动态代理实现 `Cache` + `CounterFactory`；不连真实 Redis。
- **测试结束必须 `LockHolder.clear()`**；涉及 cache 注册表的用例同时 `CacheManager.clear()`。
- **测试须先装配日志工厂**：在 `@BeforeClass` 调 `LogKit.get().setLogFactory(...)` 装配 Noop 实现（`src/test/java/.../support/NoopLogFactory`），否则 `Log.get(...)` 路径失败；测试替身不引入日志绑定依赖。
- **Redis 集成测试默认关闭**，用 `-Dredis.integration=true` 开启（地址取 `-Dredis.uri`，缺省 `redis://127.0.0.1:6379/0`）；一旦开启连不上必须失败。
- **计时用例默认关闭**，用 `-Dlock.timing=true` 开启；`wait = 10ms` 且总耗时 ≤ 35ms 的容差**不得放大**。

## 验证

命令一律在模块目录内执行（门禁依赖相对路径 `src/main/java`）：

```bash
cd aifei-lock && mvn clean verify   # 必须 0 failure / 0 error
mvn test -Dtest=LockKitTest#releaseIsIdempotent          # 单用例：类名#方法名
mvn test -Dredis.integration=true                         # 含 Redis 集成用例，连不上必须失败
mvn test "-Dlock.timing=true"                             # 含离线计时用例
```

当前基线：3 个默认运行的测试类（`ArchitectureBoundaryTest` 3 条 / `LockKitTest` 20 条 / `LockPluginTest` 5 条）+ Redis 集成类 5 条 + 离线计时类 1 条，共 34 个用例方法（后两类默认跳过），`mvn clean verify` 实测 0 failure / 0 error。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约与决策）与 `README.md`（用法 / 使用顺序 / 反例 / 异常语义）。
- 术语统一：锁名、锁键、租约（`lease`）、等待（`wait`）、持有标识（token）、锁分区（`__LOCK__`）、能力查询（`supportsCas`）；不得另起一套叫法。
- 公共类型与公共成员必须书写 javadoc 并写全 `@param` / `@return` / `@throws`；只在代码无法表达「为什么」时写注释，不复述代码、不写行尾注释、不留死代码。
