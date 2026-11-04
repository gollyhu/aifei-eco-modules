# aifei-lock 模块需求

> 版本 **1.0**（2026-11-04，已完成定档，无待拍板项）。本文是任务输入，不是 PRD / 设计。
>
> 定位：只做分布式锁，不做分布式事务。公共面 3 个类型、0 个配置键、句柄不可变。

## 一、背景与目标

现有 7 个模块均无跨实例互斥能力：`aifei-cache` 的 `putIfAbsent` 只有无条件 `remove`，租约到期会误删他人的锁；数据库行锁只对单库单行生效；JDK `ReentrantLock` 只在本 JVM 内有效。

本模块补齐该能力，**锁语义只依赖三条原子命令**：`SET key token PX lease NX`（加锁）、Lua CAS 删除（释放）、Lua CAS 续期（续期）。加锁成功后 `aifei-cache` 还会追加一次 `cacheNames()` 登记（`SADD`），属**附加命令**、不参与互斥判断，但每次加锁会多一次往返。坐标 `io.github.gollyhu:aifei-lock:1.0.0`，包名 `cn.hg.aifei.lock`，Java 8（class major 52）。

**不做**：分布式事务（跨库一致性用 outbox + 幂等消费或 Saga）；单机 / caffeine 后端（单机直接用 `ReentrantLock`，静默无互斥比报错更危险，无共享存储的用户不能使用本模块）；可重入、公平排队、读写锁、锁丢失回调、jdbc 后端、多实例；自动续期看门狗。

## 二、前置依赖：`aifei-cache` 必须提供的能力

只依赖 `aifei-cache:1.1.0` 的 **release** 版本，不依赖 SNAPSHOT；该版本**先发**，本模块不同批发布。**完整发布序列：`aifei-cache:1.1.0` → `aifei-lock:1.0.0` → `aifei-job:1.1.0`**（job 的 v1.1 集群互斥）。

| 序号 | 能力 | 现状 | 性质 |
| --- | --- | --- | --- |
| 1 | 带租约的原子加锁 `putIfAbsent(cacheName, key, value, Duration ttl)` | 已具备（Redis 为单条 `SET key value PX ttl NX`） | 无需改动，须在 `aifei-cache/docs/design.md` 标注「锁依赖此语义，不得退化为两条命令」 |
| 2 | `removeIfEquals(cacheName, key, expectedValue)` | 缺 | **必须新增**，安全释放的唯一实现基础 |
| 3 | `expireIfEquals(cacheName, key, expectedValue, Duration ttl)` | 缺 | **必须新增**，`renew()` 属 P0 |
| 4 | `backendType()` 只读标识 | 缺（标识仅在 `internal.CacheProvider#type()`） | **必须新增**，启动校验共享存储 |
| 5 | `getTtl(cacheName, key)` | 已具备 | 无需改动，仅测试与排障，不进入实现路径 |

- **加锁必须是单条命令**：拆成「无 TTL 的 `putIfAbsent` + 随后 `expire`」时，进程在两步之间崩溃会留下无租约的永久锁，即分布式死锁。
- **cache 侧异常契约**：参数非法抛 `IllegalArgumentException`；存储不可用、超时、序列化失败**必须抛运行时异常，不得吞成 `false`**——`false` 只能表达「值不匹配」。

## 三、公共 API

公共面固定 3 个类型：`Lock` / `LockKit` / `LockException`。新增公共类型须先过设计决策并同步门禁断言。

```java
public interface Lock extends AutoCloseable {

    /** 绑定的锁名。 */
    String name();

    /** CAS 释放；true=本次释放成功，false=非持有者（已过期或已释放）。 */
    boolean release();

    /** CAS 续期；true=成功，false=值不匹配（锁已丢失）。 */
    boolean renew();

    /** 等价于 release() 并吞掉 LockException：永不抛异常，故障记 ERROR；供 try-with-resources 使用。 */
    @Override
    void close();
}

public final class LockKit {

    /** 立即尝试加锁；成功返回句柄，抢不到返回 null。 */
    public static Lock tryLock(String name, Duration lease);

    /** 有限等待加锁；wait 用尽仍抢不到、或等待期间被中断，返回 null。 */
    public static Lock tryLock(String name, Duration lease, Duration wait);
}
```

| 能力 | 说明 |
| --- | --- |
| 加锁 | 成功返回句柄，失败返回 `null`；`wait > 0` 时 50ms 轮询重试 |
| 释放 | `release()` 幂等，非持有者返回 `false`；`close()` 永不抛，供 `try-with-resources` |
| 续期 | `renew()` 返回 `false` 表示锁已丢失，业务须立即停手 |
| 锁名规则 | 非空、不含空白、长度 ≤ 128、允许 `:` 分隔；唯一定义点在 `internal.LockNames` |
| 参数校验 | 锁名非法、`lease < 1ms`、`wait < 0` → `IllegalArgumentException`，不被包装；租约下限与 `aifei-cache` 的 `requireTtl` 对齐 |

**句柄是不可变值对象**：只有 `name` / `token` / `lease` / `store` 四个 `final` 字段，不记录持有状态；`token` 为 `String`（UUID）且不对外暴露；因此句柄天然线程安全，可跨线程传递。`lease` 是**创建时的租约时长**，`renew()` 以它重设 TTL——`Lock#renew()` 无参，租约必须随句柄携带，不能由 `store` 记忆（`store` 是共享单例，无法按句柄区分）。加锁入口只在 `LockKit`，**不提供 `getLock(name)`**。

**释放与续期必须 CAS**（值匹配才执行），不得使用无条件 `remove` / `expire`；比较基准为序列化后的字节。

`close()` **等价于调用 `release()` 并吞掉 `LockException`**：它在 `try-with-resources` 退出时调用，抛出会抑制业务真实异常，故一定不抛、只记 ERROR；需要感知释放失败（如告警）时显式调用 `release()` 并自行 `try/catch`。

### 等待与中断

`wait > 0` 时按固定 50ms 间隔轮询，无退避、无抖动、无配置。单次睡眠时长取 `min(50ms, 剩余等待)`，截止时间用单调时钟计算——否则 `wait` 小于 50ms 时会睡过窗口，系统时钟回拨时会多等。

- 等待期间线程被 `interrupt()`：捕获 `InterruptedException`，**恢复中断位**后返回 `null`；此时尚未持有锁，无需清理资源，调用方可用 `isInterrupted()` 区分「等待超时」与「被中断」。
- **不抛 `LockException`**：中断不是存储故障。`LockException` 只表达存储不可用，把中断包装成它会让调用方误判为可重试的存储故障。
- **`wait == 0`（立即尝试）不检查中断**，与 `ReentrantLock#tryLock()` 一致：非阻塞调用不响应中断。

## 四、装配与配置

- **配置键为 0**：租约必填、等待显式传，没有可调项，`LockPlugin` 无参构造；不设 `lock.enabled`（不想用就不要装配插件）。
- **挂载主缓存实例，不新建连接**：锁键落在主缓存实例的命名空间下，使用专用 `cacheName` 常量 `__LOCK__`，不新增 `lock.redis.*` 任何配置。`__LOCK__` 为保留分区，业务不得直接读写，**尤其不得调用 `Cache#clear("__LOCK__")`**（`clear` 的参数是 cacheName、不是全局清空，清掉该分区会清空全部锁）。
- **`__LOCK__` 已确认符合 cacheName 校验**：`aifei-cache` 的 `requireCacheName` 只要求非空白且不含 `:`，无长度上限与字符白名单，双下划线合法；因此 `putIfAbsent` 不会因名称非法抛 `IllegalArgumentException`。
- **启动校验**：`Cache#backendType()` 的取值与 `cache.type` 一致——Redis 后端为 **`"jedis"`**（刻意沿用该字面量，非 `"redis"`），caffeine 为 `"caffeine"`。白名单只接受 `"jedis"`，其余一律启动失败并抛 `IllegalStateException`，消息含 `lock backend is not a shared store: <type>`。校验在 `cache.CacheLockStore.open()` 内完成，`LockPlugin` 不引用 `cn.hg.aifei.cache.*`（否则撞门禁①）。
  - **跨模块契约字面量**：`"jedis"` 既是 `aifei-cache` 的后端标识、也是本模块白名单的唯一取值，本模块必然再写一份（`aifei-cache` 的「唯一定义点」只覆盖它自己的源码，**这里的重复是预期而非违规**）。白名单常量归口 `cache` 包内单一常量，两侧 `docs/design.md` 须双向登记：cache 侧标注「`"jedis"` 是对外契约字面量，`aifei-lock` 依赖它」，lock 侧标注「**`aifei-cache` 新增共享后端时须同步本白名单**」——否则未来 cache 加共享后端（如 `redisson`）会被 lock 静默拒绝。
- **未装配**：`LockHolder` 未注册时 `LockKit.tryLock(...)` 抛 `IllegalStateException("LockHolder has not been initialized. Add LockPlugin first.")`。**禁止降级为无锁执行，禁止返回 `null`**——那会让「模块没启动」被误当成「抢锁失败」而重试。
- **内部 SPI**：`internal.LockStore` 三个方法 `boolean tryAcquire(String name, String token, Duration lease)` / `boolean release(String name, String token)` / `boolean renew(String name, String token, Duration lease)`，实现为 `cache.CacheLockStore`。**token 由 lock 层（`LockNames.newToken()`）生成后作为入参传入，store 不生成 token**——token 是锁语义（CAS 期望值）的一部分，生成点必须唯一。
- **句柄由 `LockHolder` 创建**：`LockHolder` 兼作 `Lock` 工厂，提供 `Lock newLock(String name, String token, Duration lease)`（`lease` 由 `LockKit.tryLock(...)` 原样透传，写入句柄供 `renew()` 使用）；`LockImpl` 保持包私有，`LockKit` 只调 `LockHolder.newLock(...)`，不直接 new 实现类。`LockHolder` 另提供 `register(LockStore)` / `clear()` 作测试注入通道。

## 五、使用示例与反例

**标准用法**（锁在事务外获取，提交后自动释放）：

```java
public Out pay(Long orderId) {
    String name = "order:pay:" + orderId;
    // 锁在事务外获取；null 句柄不会触发 close（JLS 保证）
    try (Lock lock = LockKit.tryLock(name, Duration.ofSeconds(30))) {
        if (lock == null) {
            return Out.fail("操作过于频繁，请稍后重试");     // 抢锁失败是正常业务结果
        }
        // return 先求值事务（提交完成），再执行 close —— 释放一定在提交之后
        return Db.transaction(tx -> doPay(tx, orderId));
    }
}
```

**需感知释放失败时**（不能用 try-with-resources）：

```java
Lock lock = LockKit.tryLock(name, Duration.ofSeconds(30));
if (lock == null) return Out.fail("操作过于频繁，请稍后重试");
try {
    return Db.transaction(tx -> doPay(tx, orderId));
} finally {
    try {
        lock.release();          // 存储故障抛 LockException，便于监控
    } catch (LockException e) {
        log.error("release lock failed, name: " + lock.name(), e);   // 不能往上抛
    }
}
```

**定时任务：抢不到就跳过；长任务分段续期**：

```java
try (Lock lock = LockKit.tryLock("job:report:monthly", Duration.ofSeconds(60))) {
    if (lock == null) return;                        // 已有实例在跑
    for (Batch batch : batches()) {
        process(batch);
        if (!lock.renew()) {                         // false = 锁已丢失
            log.warn("lock lost, abort job");
            return;                                  // 互斥已失效，必须停手
        }
    }
}
```

**反例**（写进 README，明确禁止）：

```java
Db.transaction(tx -> { LockKit.tryLock(name, lease); ... });   // ✗ 事务内加锁：等待计入事务时长
Lock lock = LockKit.tryLock(name, lease);
Db.transaction(...);
lock.release();                                                // ✗ 不在 finally，异常即漏释放
if (!lock.renew()) throw new RuntimeException("lock lost");    // ✗ 锁丢失是业务分支，应 return
static Lock current;  current = LockKit.tryLock(...);          // ✗ 句柄只对应一次持有，不能存成全局字段
```

## 六、关键契约

### 1. 使用顺序（写进 README，违反即为误用）

- **锁在事务外获取**：事务内加锁把锁等待计入事务时长，放大连接占用与锁竞争。
- **锁在提交之后释放**：提前释放会开出「他人读到未提交或部分可见数据」的窗口。
- **租约时长 > 事务最长耗时 + 网络余量**，按 P99 标定。
- **锁粒度必须覆盖事务内改动的全部数据**：漏一条写路径，互斥即失效。

### 2. 能力声明（README 必须写明）

本模块提供的是**互斥**，不是**一致性**。不在保证范围内：租约到期后仍有并发执行（双持有）、Redis 主从切换导致锁丢失、GC / 长时间停顿期间锁过期、跨库跨服务以及 DB 与 MQ 的原子性、无锁降级（未装配即抛 `IllegalStateException`）。

### 3. 返回值与异常

| 情形 | `release()` | `renew()` | `close()` | 日志 |
| --- | --- | --- | --- | --- |
| 值不匹配（已过期或被他人持有） | `false` | `false` | 吞掉 | DEBUG |
| CAS 成功 | `true` | `true` | 吞掉 | DEBUG |
| 存储不可用 / 超时 / 序列化失败 | **抛 `LockException`** | **抛 `LockException`** | **不抛**（记 ERROR） | ERROR |

- **异常 ≠ 丢失**：`release()` / `renew()` 抛 `LockException` 只表示「结果未知」，不代表锁已不在手上——句柄不做任何状态推断，业务可重试，或放弃并由租约自然过期兜底。只有返回 `false` 才是「确认不匹配（已丢失或已释放）」。
- **包装规则**（唯一定义点 `internal.LockErrors`）：`IllegalArgumentException` 原样抛出；`LockException` 不重复包装；其他 `RuntimeException` 包装并保留 `cause`；`Error` 不捕获。
- **消息格式**（英文小写短语 + 操作 + 锁名）：`lock acquire failed, name: <name>`、`lock release failed, name: <name>`、`lock renew failed, name: <name>`、`lock backend is not a shared store: <type>`。
- **竞争失败与故障必须可区分**：抢不到锁返回 `null` / `false`（DEBUG），存储故障抛 `LockException`（`extends RuntimeException`，ERROR）——把故障返回 `false` 会让它被当成「有人持锁」而静默掩盖。公共方法签名不声明 `throws`。

### 4. 中断语义

| 场景 | 行为 |
| --- | --- |
| `wait > 0`，等待期间收到中断 | 捕获 `InterruptedException`，恢复中断位，返回 `null`，不抛异常 |
| `wait == 0`，中断位已置位 | 不检查，正常尝试加锁 |
| 存储调用期间被中断（`wait` 是否为 0 都一样） | 本模块不主动检查中断位；由底层连接超时处理，可能包装为 `LockException` |

等待期间捕获的 `InterruptedException` 不是存储故障，**不得**包装成 `LockException`；恢复中断位是硬要求，否则上层线程池无法感知该信号。存储调用期间由中断引发的连接层异常按普通存储故障处理（表格第 3 行），与等待阶段的中断是两件事。

## 七、非功能需求与硬约束

- **Java 8**：语法与 API 双约束；主 JAR 内 class major 必须为 52。
- **公共 API 极简、实现无关**：零 `cn.aifei.*`、零第三方类型、零 `cn.hg.aifei.cache.*`。
- **每类规则唯一定义点**：锁名校验、`cacheName` 常量、token 生成、异常消息构造、CAS 比较语义、等待与中断处理，实现类不得复制。
- **依赖**：`cn.aifei:aifei-all` 为 `provided`；`io.github.gollyhu:aifei-cache` ≥ 1.1.0 且为 `optional`；不直接引入 Jedis。
- **`optional` 不向消费方传递**：消费方必须**显式引入** `aifei-cache` ≥1.1.0，否则 `cache.CacheLockStore` 在**类加载期**即 `NoClassDefFoundError`——它早于 `LockPlugin` 的启动校验，无法转成可读的启动错误。该要求必须写入 `README.md` 的依赖说明，并在 `docs/design.md` 声明。
- **架构门禁 `ArchitectureBoundaryTest`**（源码文本扫描，范围 `src/main/java`，须在模块根目录执行，不得放宽）：① `cn.hg.aifei.cache.*` 只允许出现在 `lock/cache/`；② `cn.aifei.*` 只允许出现在 `lock/aifei/`；③ 根包直属 `.java` **文件数精确 3 个**。
  - ③ 的统计口径与 `aifei-websocket` 门禁一致：按文件位置计数，**不区分 `public` 与包私有、不解析嵌套类**。因此 `LockImpl` 及其他实现类**一律不得放根包**，即使声明为包私有也会让断言失败。
- **测试**：运行期实例经 `LockHolder.register(...)` / `clear()` 注入与清理，测试结束必须 `clear()`；非集成用例注入 `FakeLockStore`，不连真实 Redis。不设契约测试基类——根 `AGENTS.md` 该要求限定为「公共接口 + 多实现」，本模块公共接口 `Lock` 只有 `LockImpl` 一个实现，`LockStore` 属 internal 非承诺 API，不计入公共接口。集成测试默认关闭，一旦开启连不上 Redis 必须失败。
- **模块内验证**：`aifei-lock` 目录内 `mvn clean verify` 必须 0 failure / 0 error。
- **文档与注释**：`README.md`（用法、使用顺序、反例、能力声明）、`docs/design.md`（契约事实来源）、`AGENTS.md`（开发约束）三件；根 `AGENTS.md` 的 `docs/testing.md` 标注「模块存在时」属可选，测试策略并入本文。注释简体中文，公共类型与成员写全 javadoc，原创文件用统一版权头。

## 八、必须交付的固定配套

| 类别 | 交付物 |
| --- | --- |
| 构建 | `pom.xml`（Java 8、`aifei-all` provided、`aifei-cache` optional） |
| 公共面 | 根包 3 类型：`Lock`（不可变句柄）/ `LockKit`（门面）/ `LockException` |
| 内部 | `LockImpl`（包私有，仅由 `LockHolder` 创建，4 个 `final` 字段含 `lease`）、`LockStore`（SPI，`tryAcquire(name, token, lease)` 显式传 token）、`LockHolder`（`register` / `clear` + 工厂 `newLock(name, token, lease)`）、`LockNames`（常量 + 锁名校验 + token 生成）、`LockErrors`（消息构造） |
| 实现 | `cache.CacheLockStore`（原子加锁 / CAS 释放 / CAS 续期）+ 静态 `open()`（取主缓存实例并校验后端） |
| 接入 | `aifei.LockPlugin`（唯一 import 框架类型的类；调 `open()` 后 `register`） |
| 测试 | `ArchitectureBoundaryTest` + `FakeLockStore` + 契约用例 + Redis 集成测试（开关控制） |
| 文档 | `README.md`、`docs/design.md`、`AGENTS.md` |

共 10 个 Java 文件：3 公共 + 5 内部 + 1 实现 + 1 接入。

## 九、验收标准

- [ ] 模块目录内 `mvn clean verify` 通过（0 failure / 0 error），全部非集成用例基于 `FakeLockStore`、无 Redis 环境可跑；门禁三条断言通过，根包直属 `.java` 文件精确 3 个（`LockImpl` 等实现类不在根包）。
- [ ] **误删防护**：租约到期、他人持锁后，原持有者 `release()` 返回 `false` 且不删除新持有者的锁。
- [ ] **幂等释放**：重复 `release()` 不抛异常，第二次返回 `false`；`close()` 重复调用不抛。
- [ ] **配对安全**：句柄内部绑定 name 与 token（`release()` / `renew()` / `close()` 无 name / token 入参）；行为上维护两把不同锁 A、B，`a.release()` 后 `b.renew()` 仍返回 `true`，B 的锁未被误删。
- [ ] **加锁原子性**：加锁成功后 `getTtl` 非空（证明租约与加锁是一条命令）；`__LOCK__` 通过 `requireCacheName`（非空白、不含 `:`），不因名称非法抛 `IllegalArgumentException`。
- [ ] **异常语义**：存储故障时 `release()` / `renew()` 抛 `LockException`（`getCause()` 为原始异常）；`close()` 不抛且记 ERROR；参数非法抛 `IllegalArgumentException` 且未被包装。
- [ ] **续期语义**：`renew()` 在锁被他人持有后返回 `false` 且不延长他人租期。
- [ ] **续期租约**：`renew()` 成功后 TTL ≈ **创建时的 `lease`**（而非其他值）；`FakeLockStore` 断言收到的正是句柄携带的 `lease`。
- [ ] **中断语义**：`wait > 0` 期间 `interrupt()` → 返回 `null`、不抛异常、调用后 `isInterrupted()` 仍为 `true`；`wait == 0` 时**不主动检查中断位**——中断位已置位仍照常发起一次加锁调用，结果与中断无关，存储层故障按异常表处理。
- [ ] **等待窗口**：`wait = 10ms` 时总耗时 ≤ `wait + 25ms`（35ms）；固定睡 50ms 的实现耗时会 ≥ 50ms，该用例必然失败。容差不放大到 100ms——它大于固定睡眠本身，会让退化实现也通过。
- [ ] **异常 ≠ 丢失**：`FakeLockStore` 首次抛 `LockException`、随后返回成功时，同一句柄重试 `renew()` 可成功，不被误判为锁已丢失。
- [ ] **后端校验**：主缓存为 `caffeine` 后端时启动失败，消息含 `lock backend is not a shared store`。
- [ ] **未装配**：`LockHolder.clear()` 后 `LockKit.tryLock(...)` 抛 `IllegalStateException`，消息含 `has not been initialized`。
- [ ] 公共面零框架类型、零第三方类型、零 `cn.hg.aifei.cache.*`；`LockException` 继承 `RuntimeException`，方法签名不声明 `throws`；`Lock` 不暴露 `token()` 与 `lease()`。
- [ ] `pom.xml` 中 `aifei-cache` 为 `optional`，且 `README.md` 已声明「消费方须显式引入 `aifei-cache` ≥1.1.0」。
- [ ] `README.md` 含「使用顺序」「反例」「能力声明」「返回值与异常语义」四节；`docs/design.md`、`AGENTS.md` 齐备；根 `README.md` 模块总览补充 `aifei-lock` 条目。

## 十、执行方式

- 本文件为任务输入，按仓库根 `AGENTS.md` 的文档分工与模块约束执行。
- 前置依赖 `aifei-cache:1.1.0`（第二节第 2~4 项）release 发布后再启动编码；在此之前不实现 `CacheLockStore`。
