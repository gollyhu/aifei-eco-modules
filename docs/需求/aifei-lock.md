# aifei-lock 模块需求（任务输入）

> 本文件是任务输入，不是 PRD / 设计。定位：只做分布式锁，不做分布式事务。
> 公共面 3 个类型、0 个配置键、句柄不可变。
> 发布序列：`aifei-cache:1.1.0` → `aifei-lock:1.0.0` →（`aifei-job:1.1.0` 集群互斥）。

## 一、背景与目标

现有 7 个模块均无跨实例互斥能力：`aifei-cache` 1.0 的 `putIfAbsent` 只配无条件 `remove`，租约到期会误删他人的锁；数据库行锁只对单库单行生效；JDK `ReentrantLock` 只在本 JVM 有效。本模块补齐该能力，**锁语义只依赖三条原子命令**：`SET key token PX lease NX`（加锁）、Lua CAS 删除（释放）、Lua CAS 续期（续期）。加锁成功后 cache 侧会追加一次 `cacheNames()` 登记（`SADD`），属**附加命令**：既不参与互斥判断，其失败也不得改变加锁结论（见第二节硬约束）。

坐标 `io.github.gollyhu:aifei-lock:1.0.0`，包名 `cn.hg.aifei.lock`，Java 8（class major 52）。

**不做**：分布式事务（跨库一致性用 outbox + 幂等消费或 Saga）；单机 / caffeine 后端（静默无互斥比报错更危险，无共享存储的用户不能使用本模块）；可重入、公平排队、读写锁、锁丢失回调、jdbc 后端、多实例；自动续期看门狗。

## 二、前置依赖：`aifei-cache:1.1.0`

只依赖 release 版，先发、不同批发布。

| 能力 | 性质 |
| --- | --- |
| `putIfAbsent(cacheName, key, value, Duration ttl)`——Redis 单条 `SET key value PX ttl NX` | 已具备；cache 侧 `design.md` 须标注「锁依赖此语义，不得退化为两条命令」；其后的 `SADD` 登记失败按 cache 既有行为传播异常，本模块以「结果未知」承接（见下方说明） |
| `removeIfEquals(cacheName, key, expectedValue)` | 1.1.0 新增，安全释放的唯一基础 |
| `expireIfEquals(cacheName, key, expectedValue, Duration ttl)` | 1.1.0 新增，`renew()` 基础 |
| `supportsCas()` | 1.1.0 新增，启动校验（见第四节）；**无 I/O 的纯能力声明** |
| `getTtl(cacheName, key)` | 已具备，仅测试与排障，不进实现路径 |

cache 侧异常契约：参数非法抛 `IllegalArgumentException`（**先于**能力检查）；`false` 表达确定的「未发生」——不存在、已过期或值不匹配；存储不可用、超时、序列化失败**必须抛运行时异常，不得吞成 `false`**（`putIfAbsent` 的登记命令失败同样抛异常，见下条）。

**登记失败不做降级**（已定档 2026-11-05）：`SET NX PX` 成功后的 `cacheNames()` 登记（`SADD`）若失败，异常按 `aifei-cache` 既有行为传播，`aifei-cache:1.1.0` **不新增**「只记日志、仍返回 `true`」的分支——那会修改既有方法行为，越过其「只新增条件操作」的版本边界。本模块承接该语义：此时锁**可能已落库**，`LockKit.tryLock` 抛 `LockException`（**不得返回 `null`**），调用方按「结果未知」处理（与「抢不到锁」严格区分，见第六节）；残留锁由租约自然过期兜底，最坏窗口 = 本次 `lease`。本模块因此**不做**「异常后二次 `get` 比对 token」的补偿确认：那会引入第三条往返与新的竞态，违反「锁语义只依赖三条原子命令」。登记缺失只让 `cacheNames()` 少一个名字，不影响互斥。

## 三、公共 API

根包固定 3 个类型：`Lock` / `LockKit` / `LockException`。新增公共类型须先过设计决策并同步门禁断言。

```java
/**
 * 不可变锁句柄。final class、全字段 final、构造器包私有，只能经 LockKit 创建；
 * 不记录持有状态，天然线程安全，可跨线程传递。
 */
public final class Lock implements AutoCloseable {

    /** 绑定的锁名。 */
    public String name();

    /** CAS 释放；true=本次释放成功，false=非持有者（已过期或已释放）。 */
    public boolean release();

    /** CAS 续期；true=成功，false=值不匹配（锁已丢失），业务须立即停手。 */
    public boolean renew();

    /** 等价于 release() 并吞掉 LockException：永不抛异常，故障记 ERROR；供 try-with-resources。 */
    @Override
    public void close();
}

public final class LockKit {

    /** 立即尝试加锁；成功返回句柄，抢不到返回 null。 */
    public static Lock tryLock(String name, Duration lease);

    /** 有限等待加锁；wait 用尽仍抢不到、或等待期间被中断，返回 null。 */
    public static Lock tryLock(String name, Duration lease, Duration wait);
}
```

**句柄语义**：

- `name` / `token` / `lease` / `store` 四个 `final` 字段；`token` 为 `String`（UUID）且不对外暴露（`Lock` 不暴露 `token()` 与 `lease()`）。
- `lease` 是**创建时的租约时长**，`renew()` 以它重设 TTL——无参、租约随句柄携带（`store` 是共享单例，无法按句柄区分）。
- `release()` / `renew()` / `close()` 无 name / token 入参，配对安全由句柄绑定保证。
- 加锁入口只在 `LockKit`，不提供 `getLock(name)`；句柄只对应一次持有，不能存全局字段。

**释放与续期必须 CAS**（值匹配才执行），不得使用无条件 `remove` / `expire`；比较基准为序列化后的字节（cache 侧以二进制安全 `eval` 按字节传递期望值，不经字符串中转；`token` 为 `String` 时字节确定性成立）。

`close()` 等价于 `release()` 并吞掉 `LockException`：try-with-resources 退出时抛异常会抑制业务真实异常，故永不抛、只记 ERROR；需感知释放失败（告警）时显式调用 `release()` 并自行 try/catch。

**锁名规则**：非空、不含空白、长度 ≤ 128、允许 `:` 分隔；唯一定义点 `internal.LockNames`。**参数校验**：锁名非法、`lease < 1ms`、`wait < 0` → `IllegalArgumentException`，不被包装；租约下限与 cache 的 `requireTtl` 对齐。**`lease` 上限（`Integer.MAX_VALUE` 秒）不在本模块校验**，由 cache 的 `requireTtl` 承担并原样抛 `IllegalArgumentException`（`LockErrors` 原样透传）；本模块只校验下限，以保证「参数错误」早于「装配 / 能力错误」暴露。

### 等待与中断

`wait > 0` 时**先立即尝试一次**（不睡眠），失败后按固定 50ms 间隔轮询，无退避、无抖动、无配置。单次睡眠取 `min(50ms, 剩余等待)`，截止时间用单调时钟（否则 `wait < 50ms` 会睡过窗口，系统时钟回拨会多等）。

| 场景 | 行为 |
| --- | --- |
| `wait > 0`，等待期间被 `interrupt()` | 捕获 `InterruptedException`，**恢复中断位**，返回 `null`，不抛异常（尚未持锁，无需清理；调用方用 `isInterrupted()` 区分「等待超时」与「被中断」） |
| `wait == 0` | 不检查中断位，正常尝试加锁（对齐 `ReentrantLock#tryLock()`：非阻塞调用不响应中断） |
| 轮询期间任一次加锁调用抛 `LockException` | **立即上抛，不继续轮询**（存储故障不得被「等满 `wait` 后返回 `null`」掩盖成「抢不到锁」）；已消耗的等待时间不重试、不外抛后重排 |
| 存储调用期间被中断 | 不主动检查，由连接超时处理，可能包装为 `LockException` |

中断不是存储故障，**不得**包装成 `LockException`；恢复中断位是硬要求，否则上层线程池无法感知信号。

## 四、装配与配置

- **配置键 0**：租约必填、等待显式传，无可调项；`LockPlugin` 无参构造，不设 `lock.enabled`（不想用就不装配）。
- **挂载主缓存实例，不新建连接**：锁键落在主缓存实例的命名空间下，专用 `cacheName` 常量 `__LOCK__`（保留分区，业务不得读写，尤其不得 `Cache#clear("__LOCK__")`——那会清空全部锁；已确认通过 cache 侧 `requireCacheName` 校验）。不新增 `lock.redis.*` 任何配置。
- **启动校验 = 能力查询，非白名单**：`CacheLockStore.open()` 通过 aifei-cache 的实例获取 API 取 `__MAIN__` 主缓存实例（固定为无参 `CacheKit.use()`——只返回实例、不设置任何「当前实例」状态，无副作用；`supportsCas()` **不提供门面镜像**，故只能取实例后调实例方法），调其 `supportsCas()`，为 `false` 时抛 `IllegalStateException("lock backend is not a shared store")`。**本模块不维护后端类型白名单**：cache 未来新增共享后端时，其能力声明自动生效，本模块零改动、无登记义务。`LockPlugin` 不引用 `cn.hg.aifei.cache.*`（门禁①）。
- **能力查询自身无 I/O**：`aifei-cache:1.1.0` 的 `supportsCas()` 是纯能力声明（`jedis` 恒 `true`、`caffeine` 恒 `false`），不得探测连通性、不发起任何存储命令。因此启动校验不会因存储不可用而失败——存储故障推迟到首次 `tryAcquire`，由第六节的 `LockException` 语义承载（启动期不承担「探活」职责，也不额外建连）。
- **能力查询失败（防御分支）**：仍保留 —— `supportsCas()` 自身抛 `RuntimeException` 时，`open()` 包装为 `IllegalStateException("lock backend capability check failed")` 并保留 cause，`Error` 不捕获。内置后端不会触发该分支；保留它是为了不把「实例方法抛异常」误当成「后端不支持」，也便于测试替身覆盖。启动期一律用 `IllegalStateException` 表达「不能装配」，消息同样由 `LockErrors` 构造，但不走第六节的 `LockException` 包装规则。
- **装配顺序**：`LockPlugin` 要求 `aifei-cache` 主实例已注册（`CachePlugin` 先装配并已完成 `start()`）。cache 注册表未初始化或主实例缺失时 `open()` 抛 `IllegalStateException("cache __MAIN__ instance is not initialized")`，cause 保留 cache 侧原始异常——消息由 `LockErrors` 生成、不依赖 cache 的异常文案，故可稳定断言。
- **未装配**：`LockHolder` 未注册时 `LockKit.tryLock(...)` 抛 `IllegalStateException("LockHolder has not been initialized. Add LockPlugin first.")`。**禁止降级为无锁执行、禁止返回 null**——「模块没启动」不能被误当成「抢锁失败」而重试。
- **内部 SPI**：`internal.LockStore` 三方法——`boolean tryAcquire(String name, String token, Duration lease)` / `boolean release(String name, String token)` / `boolean renew(String name, String token, Duration lease)`；实现为 `cache.CacheLockStore`。**token 由 lock 层（`LockNames.newToken()`）生成后作为入参传入，store 不生成 token**——token 是 CAS 期望值的一部分，生成点必须唯一。
- **句柄创建**：`Lock` 构造器包私有，`LockKit`（同包）直接构造；`LockHolder` 只负责 `register(LockStore)` / `clear()`（测试注入通道）与运行期实例读取，不做工厂。

## 五、使用示例与反例

**标准用法**（锁在事务外获取、提交后自动释放）：

```java
public Out pay(Long orderId) {
    String name = "order:pay:" + orderId;
    // null 句柄不触发 close（JLS 保证）；return 先求值事务（提交完成），再执行 close
    try (Lock lock = LockKit.tryLock(name, Duration.ofSeconds(30))) {
        if (lock == null) {
            return Out.fail("操作过于频繁，请稍后重试");   // 抢锁失败是正常业务结果
        }
        return Db.transaction(tx -> doPay(tx, orderId));
    }
}
```

**长任务分段续期**（renew 的公共必要性来源）：

```java
try (Lock lock = LockKit.tryLock("job:report:monthly", Duration.ofSeconds(60))) {
    if (lock == null) return;                  // 已有实例在跑
    for (Batch batch : batches()) {
        process(batch);
        if (!lock.renew()) {                   // false = 锁已丢失
            log.warn("lock lost, abort job");
            return;                            // 互斥已失效，必须停手
        }
    }
}
```

**需感知释放失败时**（不能用 try-with-resources）：显式 `lock.release()`，捕获 `LockException` 记 ERROR 后继续，不上抛。

**反例**（写进 README，明确禁止）：

```java
Db.transaction(tx -> { LockKit.tryLock(name, lease); ... });   // ✗ 事务内加锁：等待计入事务时长
lock.release();  // 不在 finally 内                            // ✗ 异常即漏释放
if (!lock.renew()) throw new RuntimeException("lock lost");    // ✗ 锁丢失是业务分支，应 return
static Lock current;  current = LockKit.tryLock(...);          // ✗ 句柄只对应一次持有，不能存全局字段
```

## 六、关键契约

### 使用顺序（README 必写，违反即误用）

- 锁在**事务外**获取：事务内加锁把等待计入事务时长，放大连接占用与锁竞争。
- 锁在**提交之后**释放：提前释放开出「他人读到未提交或部分可见数据」的窗口。
- 租约时长 > 事务最长耗时 + 网络余量，按 P99 定。
- 锁粒度必须覆盖事务内改动的全部数据：漏一条写路径，互斥即失效。

### 能力声明（README 必写）

本模块提供的是**互斥**，不是**一致性**。不在保证范围：租约到期后仍有并发执行（双持有）、Redis 主从切换导致锁丢失、GC / 长时间停顿期间锁过期、跨库跨服务以及 DB 与 MQ 的原子性、无锁降级（未装配即抛 `IllegalStateException`）。

### 返回值与异常

| 情形 | `release()` | `renew()` | `close()` | 日志 |
| --- | --- | --- | --- | --- |
| 值不匹配（已过期或被他人持有） | `false` | `false` | 吞掉 | DEBUG |
| CAS 成功 | `true` | `true` | 吞掉 | DEBUG |
| 存储不可用 / 超时 / 序列化失败 | **抛 `LockException`** | **抛 `LockException`** | **不抛**（记 ERROR） | ERROR |

- **异常 ≠ 丢失**：`LockException` 只表示「结果未知」，业务可重试或放弃由租约自然过期兜底；只有 `renew()` 返回 `false` 才是「确认丢失」。句柄不做任何状态推断。
- **`release()` 也遵守「异常 ≠ 丢失」**：抛 `LockException` 表示「是否删除未知」，不代表锁仍在；重试 `release()` 是安全的（CAS 不会误删他人锁），重试返回 `false` 只表示「本次未删除」——可能第一次其实已删除成功，也可能已过期。
- **竞争失败与故障必须可区分**：抢不到锁返回 `null` / `false`（DEBUG），存储故障抛 `LockException`（`extends RuntimeException`，公共方法不声明 `throws`）——把故障返回 `false` 会让它被当成「有人持锁」而静默掩盖。
- **包装规则**（唯一定义点 `internal.LockErrors`）：`IllegalArgumentException` 原样抛；`LockException` 不重复包装；其他 `RuntimeException` 包装并保留 cause；`Error` 不捕获。
- **消息格式**（英文小写短语 + 操作 + 锁名；全部由 `internal.LockErrors` 构造）：`lock acquire failed, name: <name>`、`lock release failed, name: <name>`、`lock renew failed, name: <name>`、`lock backend is not a shared store`、`lock backend capability check failed`、`cache __MAIN__ instance is not initialized`、`LockHolder has not been initialized. Add LockPlugin first.`。

### 日志

- **统一经 aifei 框架日志门面 `cn.aifei.log.Log`**：`Log.get(本类.class)` 取本类 logger，用 `debug` / `info` / `warn` / `error`。不新增依赖（随 `cn.aifei:aifei-all` provided 提供），**不引入任何日志绑定**；禁止 `System.err`、禁止 `org.slf4j.*`、禁止自建记录通道。
- 门禁例外：`cn.aifei.log.*` 允许出现在 `lock/` 任意包（见第七节门禁②），这是唯一可在接入包之外出现的框架类型；其余 `cn.aifei.*` 仍只允许在 `lock/aifei/`。
- 记录口径与第六节返回值表一致：`release()` / `renew()` 的 `false` 与 `true` 记 DEBUG，存储故障记 ERROR 并带 `cause` 堆栈，`close()` 吞掉的异常记 ERROR。日志文案中的锁名一律取自句柄 `name()`，不另行拼装。
- 未装配 `LogFactory` 时按框架既有行为（README 声明）；测试须先装配（见第七节测试约束）。

## 七、非功能与硬约束

- **Java 8**：语法与 API 双约束；主 JAR class major 52。
- **公共 API 极简、实现无关**：零 `cn.aifei.*`、零第三方类型、零 `cn.hg.aifei.cache.*`。
- **每类规则唯一定义点**：锁名校验、`cacheName` 常量、token 生成、异常消息构造、CAS 比较语义、等待与中断处理，实现类不得复制。
- **依赖**：`cn.aifei:aifei-all` provided；`io.github.gollyhu:aifei-cache` ≥1.1.0 且 **optional**；不直接引入 Jedis。**optional 不向消费方传递**：消费方必须显式引入 `aifei-cache` ≥1.1.0，否则 `cache.CacheLockStore` 在**类加载期**即 `NoClassDefFoundError`（早于 `LockPlugin` 启动校验，无法转成可读错误）——写入 README 与 `docs/design.md`。
- **架构门禁 `ArchitectureBoundaryTest`**（源码文本扫描，范围 `src/main/java`，模块根目录执行，不得放宽）：① `cn.hg.aifei.cache.*` 只允许出现在 `lock/cache/`；② `cn.aifei.*` 只允许出现在 `lock/aifei/`，**唯一例外是 `cn.aifei.log.*`**（日志门面，允许出现在任意包，见第六节「日志」）；③ 根包直属 `.java` **文件数精确 3 个**（`Lock` / `LockKit` / `LockException`；统计不区分 public 与包私有、不解析嵌套类，实现类一律不放根包）。
- **测试**：运行期实例经 `LockHolder.register(...)` / `clear()` 注入与清理，测试结束必须 `clear()`；非集成用例注入 `FakeLockStore`，不连真实 Redis。不设契约测试基类（根 `AGENTS.md` 该要求限定为「公共接口 + 多实现」，`Lock` 为 final class 唯一实现形态）。集成测试默认关闭，一旦开启连不上 Redis 必须失败。**计时用例标注离线**：`wait = 10ms` 且总耗时 ≤ 35ms 的断言对 CI 抖动敏感，标注为离线用例、不进默认 CI（与 Redis 集成测试同策略），本地/离线环境执行。**测试须先装配日志工厂**：`Log.get(...)` 依赖 `LogFactory`，非集成用例在 `@BeforeClass` 调 `LogKit.get().setLogFactory(...)` 装配 Noop 实现（参考 `aifei-weixin` 的 `support/NoopLogFactory`），否则日志路径 NPE；测试替身不引入任何日志绑定依赖。
- 模块内 `mvn clean verify` 0 failure / 0 error。
- **文档与注释**：`README.md`（用法、使用顺序、反例、能力声明、日志口径）、`docs/design.md`（契约事实来源，含第八节 **7 条**决策）、`AGENTS.md`（开发约束）。注释简体中文，公共类型与成员全 javadoc，统一版权头。

## 八、必须交付的固定配套

| 类别 | 交付物 |
| --- | --- |
| 构建 | `pom.xml`（Java 8、aifei-all provided、aifei-cache optional） |
| 公共面 | `Lock`（final class，不可变句柄）/ `LockKit`（门面）/ `LockException` |
| 内部 | `LockStore`（SPI，显式传 token）、`LockHolder`（`register` / `clear` + 运行期读取）、`LockNames`（常量 + 锁名校验 + token 生成）、`LockErrors`（消息构造） |
| 实现 | `cache.CacheLockStore`（原子加锁 / CAS 释放 / CAS 续期）+ 静态 `open()`（取主缓存实例并调 `supportsCas()` 校验） |
| 接入 | `aifei.LockPlugin`（唯一 import 框架类型的类；调 `open()` 后 `register`） |
| 测试 | `ArchitectureBoundaryTest` + `FakeLockStore` + 契约用例 + Redis 集成测试（开关控制） |
| 文档 | `README.md`、`docs/design.md`、`AGENTS.md`；**不单设 `docs/testing.md`**——测试策略与「计时用例离线、不进默认 CI」写在 `docs/design.md` 第 5 条决策与 `AGENTS.md` 的测试约定里 |

`docs/design.md` 第一版必须记录以下 7 条设计决策（缺一条视为未交付）：

1. **`Lock` 是 final class 的 mock 影响**：下游无法用 JDK 代理替身，Java 8 下 Mockito 需 inline mock maker 才能 mock final class。这是刻意取舍（句柄不可伪造、不可继承），替代路径是把替身放在 `LockStore` 层——测试经 `LockHolder.register(FakeLockStore)` 注入，不对句柄做 mock。
2. **`supportsCas()` 抛异常时 `open()` 的包装**：包装为 `IllegalStateException("lock backend capability check failed")` 并保留 cause，`Error` 不捕获；启动期失败一律 `IllegalStateException`，不复用运行期的 `LockException` 包装规则。cache 1.1.0 的 `supportsCas()` 无 I/O，该分支对内置后端不可达，属防御与测试替身路径——保留它是为了「实例方法抛异常」不被误判成「后端不支持」。
3. **`Lock` 不重写 `equals` / `hashCode`**：采用对象身份比较——句柄代表「一次持有」，同名两次持有互不相等；若按 `name` 相等，两个句柄放进 `Set` / `Map` 会互相覆盖，掩盖重复持有。
4. **构造句柄失败时锁由租约兜底**：`tryAcquire` 返回 `true` 之后才构造句柄，构造过程不执行 I/O、不抛受检异常；唯一可能的失败是 `Error`（如 OOM），此时不做补偿释放——避免在 `Error` 路径上再做一次 I/O，锁由租约自然过期。
5. **等待窗口用例标注离线**：`wait = 10ms` 且总耗时 ≤ 35ms 的断言对 CI 抖动敏感，标注为离线用例、不进默认 CI；容差不放大到 100ms，否则退化实现也能通过。
6. **`release()` 也遵守「异常 ≠ 丢失」**：抛 `LockException` 只表示「是否删除未知」，重试安全且不推断锁状态；只有 `renew()` 返回 `false` 才是「确认丢失」。
7. **拒在 `aifei-cache` 侧为 `putIfAbsent` 的 `SADD` 登记失败做降级**：那会修改 cache 既有方法行为，与其 1.1.0「只新增条件操作」的边界冲突。代价是登记失败时 `tryAcquire` 抛 `LockException` 而锁可能已落库，残留窗口由租约兜底；不实现「异常后二次 `get` 比对 token」的补偿确认（见第二节）。

共 **9 个 Java 文件**：3 公共 + 4 内部 + 1 实现 + 1 接入。

## 九、验收标准

- [ ] 模块目录内 `mvn clean verify` 通过（0 failure / 0 error）；非集成用例基于 `FakeLockStore`、无 Redis 环境可跑；门禁三条通过，根包直属 `.java` 精确 3 个。
- [ ] **误删防护**：租约到期、他人持锁后，原持有者 `release()` 返回 `false` 且不删除新持有者的锁。
- [ ] **幂等释放**：重复 `release()` 不抛异常，第二次返回 `false`；`close()` 重复调用不抛。
- [ ] **配对安全**：句柄内部绑定 name 与 token；两把锁 A、B，`a.release()` 后 `b.renew()` 仍返回 `true`。
- [ ] **加锁原子性**：加锁成功后 `getTtl` 非空（租约与加锁是一条命令）；`__LOCK__` 不因名称非法抛 `IllegalArgumentException`。
- [ ] **登记失败按「结果未知」承接**：`FakeLockStore` 的 `tryAcquire` 抛异常时 `LockKit.tryLock` 抛 `LockException` 且**不返回 `null`**（不与「抢不到锁」混淆）；不实现「异常后二次 `get` 比对 token」的补偿确认；`aifei-cache` 侧亦不为此修改既有 `putIfAbsent` 行为（见第二节）。
- [ ] **异常语义**：存储故障时 `release()` / `renew()` 抛 `LockException`（`getCause()` 为原始异常）；`close()` 不抛且记 ERROR；参数非法抛 `IllegalArgumentException` 且未被包装。
- [ ] **release() 异常 ≠ 丢失**：`FakeLockStore` 首次 `release` 抛 `LockException`、随后返回成功或 `false` 时，同一句柄重试 `release()` 不抛异常，且不据此推断锁状态。
- [ ] **续期语义**：锁被他人持有后 `renew()` 返回 `false` 且不延长他人租期；成功后 TTL ≈ **创建时的 `lease`**（`FakeLockStore` 断言收到的正是句柄携带的 lease）。
- [ ] **中断语义**：`wait > 0` 期间 `interrupt()` → 返回 `null`、不抛异常、调用后 `isInterrupted()` 仍为 `true`；`wait == 0` 时不主动检查中断位，照常发起一次加锁调用。
- [ ] **等待窗口**（离线用例，不进默认 CI）：`wait = 10ms` 时总耗时 ≤ 35ms（容差不放大到 100ms，否则退化实现也能通过）。
- [ ] **renew() 异常 ≠ 丢失**：`FakeLockStore` 首次抛 `LockException`、随后返回成功时，同一句柄重试 `renew()` 可成功。
- [ ] **能力校验**：`supportsCas()` 为 `false` 的主缓存实例（caffeine）启动失败，消息含 `lock backend is not a shared store`；无后端类型白名单（cache 新增共享后端时本模块零改动）；启动校验只调一次 `supportsCas()`，不额外建连、不发存储命令（`supportsCas()` 的「无 I/O」由 cache 模块用例钉死，本模块不重复测）。
- [ ] **启动顺序**：cache 注册表未初始化或主实例缺失时 `LockPlugin.start()` 失败，消息含 `cache __MAIN__ instance is not initialized`；`supportsCas()` 抛 `RuntimeException`（由测试替身触发，内置后端不抛）时消息含 `lock backend capability check failed` 且 `getCause()` 非空。
- [ ] **未装配**：`LockHolder.clear()` 后 `LockKit.tryLock(...)` 抛 `IllegalStateException`，消息含 `has not been initialized`。
- [ ] 公共面零框架类型、零第三方类型、零 `cn.hg.aifei.cache.*`；`LockException` 继承 `RuntimeException`，方法签名不声明 `throws`；`Lock` 不暴露 `token()` 与 `lease()`。
- [ ] `pom.xml` 中 aifei-cache 为 optional，README 已声明「消费方须显式引入 `aifei-cache` ≥1.1.0」。
- [ ] README 含「使用顺序」「反例」「能力声明」「返回值与异常语义」四节，并声明日志经 aifei 框架 `Log`（未装配 `LogFactory` 时按框架既有行为）；`docs/design.md`、`AGENTS.md` 齐备，且 `docs/design.md` 已记录第八节 **7 条**设计决策；根 `README.md` 模块总览补充 `aifei-lock` 条目。

## 十、执行方式

- 本文件为任务输入，按仓库根 `AGENTS.md` 的文档分工与模块约束执行。
- 前置依赖 `aifei-cache:1.1.0`（第二节第 2~4 项）release 发布后再实现 `CacheLockStore`；在此之前不实现该类。
