# aifei-cache 1.1.0 需求（任务输入）

> 本文件是任务输入，不是 PRD / 设计。本版本是 `aifei-lock` 的前置包，必须先发布；lock 依赖本 release 版本，不同批发布。

## 一、背景

`Cache` 只有**无条件**的 `remove` / `expire`，业务无法安全地「只删除 / 只续期自己写入的那一项」。锁场景的经典缺陷：A 持锁，租约到期自动释放；B 抢到同一把锁；A 执行完调用 `remove`，删掉 **B 的锁**。「先 `get` 比对、再 `remove`」两步不原子，无法修复。本版本补齐**条件操作（CAS）**能力，并暴露后端能力声明，供上层判断存储是否可用。

## 二、范围

- 坐标：`io.github.gollyhu:aifei-cache:1.1.0`（minor：公共**调用面**纯新增；`Cache` **SPI 实现面**是 breaking，见四.3）
- 技术基线：Java 8（class major 52）
- 新增 **3 个公共方法**：`removeIfEquals` / `expireIfEquals` / `supportsCas`
- 内部新增 2 处（非承诺 API）：`CacheValidator.requireExpectedValue`（`null` 期望值的唯一定义点）、`RedisCache` 的包私有 ARGV 装配点（供离线单测断言字节）

**不做**：不改任何既有方法的签名与行为；不动冻结数据格式（哨兵类型、key 前缀、物理 key 格式、Fury 编码）；不改 `CacheProvider` / `CacheProviderRegistry` 扩展契约；不做 `default` 兜底；caffeine 不实现 CAS。

## 三、新增方法

### 1. `removeIfEquals`

```java
/** 当缓存项存在、未过期且当前值与 expectedValue 一致时删除；否则不做任何操作。 */
boolean removeIfEquals(String cacheName, String key, Object expectedValue);
```

| 情形 | 结果 |
| --- | --- |
| 存在、未过期、值匹配 | 删除并返回 `true` |
| 不存在 / 已过期 | `false`，不抛 |
| 存在但值不匹配 | `false`，不抛，**不得删除** |
| `expectedValue` 为 `null` | `IllegalArgumentException` |
| 参数非法（名称/键为空、含非法字符） | `IllegalArgumentException` |
| 存储不可用 / 连接超时 / 序列化失败 | **抛运行时异常，不得返回 `false`** |
| `caffeine` 后端（参数合法） | `UnsupportedOperationException` |

**校验顺序（钉死）**：`CacheValidator` 参数校验在前，能力检查在后 —— `cacheName` → `key` → `expectedValue != null`（`expireIfEquals` 再 → `ttl`）。因此：

- 合法参数 + `caffeine` → `UnsupportedOperationException`；
- 任一参数非法 + 任意后端 → `IllegalArgumentException`（`caffeine` 也不例外，且优先于 `UnsupportedOperationException`）；
- 合法参数 + `jedis` → 执行 CAS。

不得把 `supportsCas()` 判断前置到参数校验之前，否则「非法参数 + `caffeine`」会错抛 `UnsupportedOperationException`，两后端的契约测试无法用同一组断言。

原子性：`jedis` 后端必须用**单条 Lua 脚本**完成「GET → 字节比较 → DEL」，不得拆成两次往返。脚本调用**只能用二进制重载** `eval(byte[] script, List<byte[]> keys, List<byte[]> args)`，理由与具体约束见第四节第 1 条。

### 2. `expireIfEquals`

```java
/** 当缓存项存在、未过期且当前值与 expectedValue 一致时重设剩余有效期；否则不做任何操作。 */
boolean expireIfEquals(String cacheName, String key, Object expectedValue, Duration ttl);
```

行为表同 `removeIfEquals`，匹配成功改为「重设 TTL」；不匹配 / 不存在时不续期、不重建条目。原子性：单条 Lua 完成「GET → 字节比较 → PEXPIRE」，同样只走二进制重载。

TTL 边界（钉死）：校验沿用 `CacheValidator.requireTtl`，与既有写路径共用同一条规则、同一批异常消息 —— `null` → `IllegalArgumentException("ttl can not be null")`；`< 1ms` → `IllegalArgumentException("ttl must be at least one millisecond")`；`> Integer.MAX_VALUE` 秒 → `IllegalArgumentException`（不截断）。该校验直接返回 `long` 毫秒并作为 `PEXPIRE` 参数使用：上限约 `2.1e12` 毫秒，远小于 `long`，故不存在 `Duration.toMillis()` 溢出，也不存在超出 Redis `PEXPIRE` 参数范围的问题；**不得**在实现类里另写 TTL 判定或做单位换算。

校验顺序：`cacheName` → `key` → `expectedValue` → `ttl` → 能力检查 → 存储命令（`ttl` 非法时 `caffeine` 也抛 `IllegalArgumentException`）。

### 3. `supportsCas`

```java
/** 该实例后端是否支持条件操作（removeIfEquals / expireIfEquals）。 */
boolean supportsCas();
```

- `jedis` 返回 `true`，`caffeine` 返回 `false`；返回 `false` 的后端调用两个 CAS 方法抛 `UnsupportedOperationException`，两者必须一致。
- **无 I/O 的纯能力声明**：不得为「探测连通性」发起任何 Redis 命令（`RedisCache` 恒返回 `true`、`CaffeineCache` 恒返回 `false`）。`aifei-lock` 的启动校验因此不会因存储不可用而失败，存储故障在首次 `tryAcquire` 暴露；lock 侧「能力查询失败」的包装路径保留为防御，实际不触发。
- **能力声明，不是身份标识**：上层（`aifei-lock`）只问「能否」，不问「是谁」。未来 cache 新增共享后端（如 redisson）时，其 `supportsCas()` 返回值即能力声明，上层**零同步**——不存在白名单、不存在跨模块契约字面量与双向登记义务。
- **与「共享存储」等价**：本生态中，支持条件操作即意味着后端可被多实例共享——`jedis` 是共享存储、`caffeine` 是进程内存储故不支持。因此 `aifei-lock` 以 `supportsCas()` 兼作「后端是否为共享存储」的判定；未来不得新增「进程内支持 CAS」的后端，否则 `lock` 会误判其具备跨实例互斥能力。

## 四、关键契约

### 1. 字节比对

`jedis` 后端：`expectedValue` 经**本实例的 codec**（与写入时同一个）序列化后，与存储原始字节在 Lua 中直接比较（`redis.call('GET', KEYS[1]) == ARGV[1]`），**不在比较前解码**——解码既慢又可能因类型信息引入歧义。

**必须使用二进制重载**：`eval(byte[] script, List<byte[]> keys, List<byte[]> args)`（Jedis 8.0.1 由 `ScriptingKeyBinaryCommands` 提供，`RedisClient extends UnifiedJedis` 已继承）。脚本、key、ARGV 三者全部按字节传递：

- 脚本：`bytes(SCRIPT)`（脚本本身是 ASCII，用 `byte[]` 是为了走同一重载）；
- key：`Collections.singletonList(bytes(physicalKey(namespace, cacheName, key)))` —— `keyCount` 由该列表长度推导，恒为 1；
- ARGV：`Collections.singletonList(<ARGV 装配点>(...))`，值必须是 `codec.serialize(expectedValue)` 的原始字节。

**禁止** `eval(String script, List<String> keys, List<String> args)`：该重载按字符串编码传参，Fury 字节可能含非 UTF-8 序列与 `\u0000`，经 `String` 往返会被替换或截断。后果不是误删（比较恒不相等），而是**锁释放永久失效**，只能等租约过期。`RedisCounter` 的 `eval(String, …)` 调用**不在本次改动范围**（其 ARGV 是十进制数字文本，无二进制风险），不得顺手替换。

**ARGV 装配必须是包私有唯一定义点**（建议名 `casExpectedBytes(RedisValueCodec, Object)`，返回 `byte[]`），两个 CAS 方法只能取它的返回值。要求：① 单测可直接调用它断言字节；② 返回类型为 `byte[]` 使 String 重载在编译期不可用（`List<byte[]>` 与 `List<String>` 不协变），「误用 String 重载」不再依赖代码评审；③ 不得经过 `String.valueOf(...)` / `new String(...)` / `toString().getBytes(...)` 等任何字符串中转。

**Lua 返回值语义**：`redis.call('GET', …)` 在 key 不存在或已过期时返回 `false`，与 `ARGV[1]`（string）比较为不等 → 脚本返回 `0`；比较命中后 `DEL` / `PEXPIRE` 在同一次脚本执行内必然成功（无其它命令插入），故脚本返回 `1` 表示「比较命中且动作已执行」、`0` 表示「未发生」，**不以 `DEL` / `PEXPIRE` 的返回值参与判定**。脚本用 `redis.call` 而非 `redis.pcall`：脚本错误与存储异常以 `JedisDataException` / `JedisConnectionException` 等运行时异常原样传播，天然满足「故障不吞」。

字节确定性前提（写入 `design.md`）：`FuryRedisValueCodec` 无状态、Fury 进程内单例，同一标量值两次 `serialize` 字节相同；但跨进程 / 跨 Fury 版本不保证等价。因此 `expectedValue` 必须是同一对象引用或字节确定性等价的可比值；带共享子对象或环的对象图不得用于 CAS（`withRefTracking(true)` 会把对象图内的重复引用编码为引用标记，字节与「等价但无共享」的图不同）。锁场景 `token` 为 `String`（各实例只比较自己写入的 token），满足该前提，且须由固定用例钉死（见第五节）。

空值哨兵：比较目标是**存储字节**，`NullValue` 条目按哨兵字节参与比较，不受本次改动影响。`expectedValue == null` 被拒且 `NullValue` 不可达，故哨兵条目没有 CAS 路径，只能沿用无条件 `remove`——已接受限制，记入 `design.md`（锁与业务 CAS 均不需要该路径）。

### 2. 异常与「不支持」的边界

| 返回 / 抛出 | 含义 | 调用方处理 |
| --- | --- | --- |
| `true` | 操作确实发生 | 正常继续 |
| `false` | 确定的「没有发生」：不存在、已过期、值不匹配 | 正常业务分支 |
| 运行时异常 | **结果未知**（存储故障） | 按可重试 / 需补偿处理 |
| `UnsupportedOperationException` | **能力不支持**（`caffeine`），未执行任何操作 | 不得按「可能已发生」处理；应改用共享存储 |

`false` 表达确定的「未发生」：不存在、已过期或值不匹配；**绝不表达存储故障**。存储故障必须抛运行时异常。这是 `aifei-lock` 区分「锁已不归自己 / 未释放成功」与「存储未知故障」的唯一依据，不得放宽。

### 3. 抽象方法而非 `default`

3 个方法均为抽象方法，两个内置后端都必须写出方法体：`RedisCache` 实现 CAS 语义，`CaffeineCache` 的 `supportsCas()` 返回 `false`、两个 CAS 方法显式抛 `UnsupportedOperationException`。「不支持」是有意声明、编译期强制可见；`default` 兜底会把发现时机推迟到生产。

二进制兼容性：对既有 `Cache` 实现是**编译期**破坏（新增抽象方法后编译失败，而非运行期 `AbstractMethodError`），须同步补齐的现有实现共 4 处：`aifei-cache` 内 3 个测试桩（`CacheManagerTest.StubCache`、`CacheManagerTest.UnsupportedCache`、`SingleFlightTest.CountingCache`，升级即编译失败）、`aifei-sa-token` 的 `InMemoryCacheStub`（该模块把依赖升到 1.1.0 时须同步补）。`internal.AbstractCache` **不得**代为实现这 3 个方法 —— 否则「两个内置后端都必须写出方法体」失效，`caffeine` 的「显式不支持」也会被默认掉。

兼容性口径（须写入 `README.md` / `docs/design.md` / 发布说明）：**公共调用面 minor 兼容**（纯新增方法，既有调用方无需改动）；**`Cache` SPI 实现面 breaking**（自定义实现必须补 3 个方法，且自定义实现只能经非承诺 API 扩展）。本仓库代码无法确证仓库外是否已有第三方 `Cache` 实现，故发布前须由维护者确认：**有则按 major 发布；确认无则 minor 可接受**，确认结论记入 `design.md`。

### 4. `putIfAbsent` 单命令语义固化

Redis 实现必须保持单条 `SET key value PX ttl NX`，不得拆成「无 TTL 写入 + 随后 `expire`」——两步之间进程崩溃会留下无租约的永久锁。`putIfAbsent` 成功后的 `SADD`（`cacheNames()` 登记）是**附加登记**，不参与互斥判断。须在 `docs/design.md` 标注「`aifei-lock` 依赖此语义，不得退化」。

登记失败（`SADD` 抛异常）**不做降级**（已定档 2026-11-05）：异常按既有行为传播，本版本不新增「只记日志、仍返回 `true`」的分支——那会修改既有方法行为，越过本版本「只新增条件操作、不改既有行为」的边界。`aifei-lock` 侧以 `LockException`「结果未知」承接，残留锁由租约兜底（见 `aifei-lock.md` 第二节），本模块不承担该项。

### 5. 其他

- `expectedValue == null` 一律抛 `IllegalArgumentException`：`null` 与空值哨兵在字节层是两种形态，语义易误用；锁与业务 CAS 都不需要删除 `null` 条目。判定落在 `internal.CacheValidator`：新增 `requireExpectedValue(Object)`（返回 `void`，与 `requireValue` 对齐；第 13 个校验方法 / 第 11 个 `require*`，消息 `expectedValue can not be null`）；`design.md`「异常模型」与 `AGENTS.md` 里的「12 个校验方法」计数须同步改为 13，既有 12 个方法的判定规则与消息文本仍属冻结契约、不得改动。
- 两个 CAS 方法**不清理 `cacheNames()` 登记**，与既有 `remove` 一致。
- Lua 沿用 `client.eval`（每次发送脚本体），不做 `SCRIPT LOAD` + `EVALSHA` 缓存；CAS 走二进制重载（见第 1 条），与 `RedisCounter#updateCounter` 的 String 重载并存、互不替换。
- `supportsCas()` **不提供 `CacheKit` 静态镜像**：它与 `namespace()` 同属配置自述，加入 `FacadeMirrorTest.CACHE_CONTRACT_ONLY` 白名单，并由反向用例钉死「门面不得出现该签名」。`aifei-lock` 取实例的唯一方式是无参 `CacheKit.use()`（返回 `__MAIN__` 实例）后调实例方法，或 `@Inject Cache`；`AGENTS.md`「`CacheKit` 镜像 `Cache` 的全部业务方法（`namespace()` 除外）」须改为「（`namespace()` / `supportsCas()` 除外）」。
- 2 条 Lua 脚本常量各自留在实现类内（`RedisCache` 的 `private static final String`），不抽公共常量类；`Cache` 仍不得声明常量。
- CAS 对非法参数抛 `IllegalArgumentException`（**写路径语义**），不沿用 `remove` / `get` / `getTtl` 的静默返回；该不对称属既有现状，须记入 `design.md` 并由用例钉死。

## 五、硬约束

- **冻结数据格式不得变更**：本次只新增读-比较-写命令，不改任何编码路径。
- **每类规则唯一定义点**：参数校验（`CacheValidator`）、Lua 脚本常量（各自实现类内）。
- **门面镜像**：`removeIfEquals` / `expireIfEquals` 补 `CacheKit` 同签名静态镜像；`supportsCas()` 与 `namespace()` 同属配置自述，加入 `FacadeMirrorTest` 的 **Cache 专属白名单** `CACHE_CONTRACT_ONLY`；`Cache` 不得声明常量。**该白名单须与 `Counter` 的白名单拆开**：现有实现是单一 `CONTRACT_ONLY` 集合，同时被 `CacheKit` 与 `CounterKit` 的反向用例（`shouldNotMirrorContractOnlyMethods`）遍历，直接往里加 `supportsCas()` 会给 `CounterKit` 附加一条恒真断言 —— 本版本拆为 `CACHE_CONTRACT_ONLY` / `COUNTER_CONTRACT_ONLY`，`Counter` 侧内容仍为 `namespace()`。
- **测试基线**：
  - 共享契约测试（以 `CaffeineCache` 为参照实现，不抽基类）对 CAS 断言两条：合法参数抛 `UnsupportedOperationException`；参数非法（`expectedValue == null`、名称/键非法、`ttl` 非法）抛 `IllegalArgumentException` —— 后一条即「校验先于能力检查」的钉死；实际 CAS 语义全部在 Redis 侧；
  - 新增**不依赖 Redis 的单测**：① 2 条 Lua 常量文本包含 `GET`、字节比较与 `DEL` / `PEXPIRE`；② ARGV 装配点（包私有、返回 `byte[]`）的输出等于 `codec.serialize(expectedValue)`，且**不等于** `String.valueOf(expectedValue).getBytes(UTF_8)`（用例取含非 ASCII 与 `\u0000` 的值）；③ 字节确定性固定用例（见下条）；④ 故障不吞：固定 `127.0.0.1` + 未监听端口（不依赖主机名解析，避免 DNS 超时让用例耗时不稳）+ 短超时（如 `connectionTimeoutMillis(200)`）构造的实例上调用两个 CAS 方法，必须抛运行时异常且不是 `false`（若客户端在构造期即建连，则「构造或调用任一阶段抛异常」合格）。「1 个 key」由 `Collections.singletonList(...)` 在结构上保证（`keyCount` 由列表长度推导），无需也不应对 `eval` 做 mock；
  - **字节确定性固定用例**（不依赖 Redis，落在 `FuryRedisValueCodecTest`）：同一 `String` 实例两次 `serialize` 字节相等；两个不同实例同值 `serialize` 字节相等；`UUID.randomUUID().toString()` 形态的字符串两次 `serialize` 字节相等。带共享子对象 / 环的对象图**不做正向断言**，只在 `design.md` 记录限制；
  - Redis 侧扩展 `RedisCacheIntegrationTest`（默认跳过，`-Dredis.integration=true` 开启，开启后连不上必须失败），覆盖误删防护、误续期防护、**非 ASCII `token` 的匹配成功**（字节路径的端到端证明）、`INFO commandstats` 中 `cmdstat_eval:calls` 差值佐证单次调用只发一条 `EVAL`（该计数是**实例级全局**的，断言前须确认同批没有并发 `EVAL`：集成用例默认串行执行，若接入 CI 则该 job 内不得并行跑其它 Redis 用例）；故障不吞不放这里（见上一条 ④，离线可跑）；
  - 冻结格式固定向量用例全部回归通过。
- **不新增第三方依赖**（不引入 Mockito：`RedisClient` 是具体类、`eval` 是实例方法，没有可替身的接口；`RedisCache` 的构造器亦不对外暴露可注入的客户端，测试缝只允许「包私有纯函数 + 真实 Redis 集成」两种）。
- **文档同步**：`docs/design.md`（「跨实现契约：共同底线与允许差异」一节**已存在**（第 304 行起，`design.md` 第 19 行与 `docs/testing.md` 的两处引用已指向它），本版本只复核并补 CAS 两行，不再新建；另记参数校验顺序、二进制 `eval`、Lua 返回值语义、TTL 边界、字节确定性限制、哨兵无 CAS 路径、兼容性口径、校验方法计数 13、门面白名单两项）、`README.md`（CAS 用法 + 「升级到 1.1.0」兼容性小节）、`AGENTS.md`（计数 13、门面镜像例外、CAS ARGV 约束）、`docs/testing.md`（新增离线用例与集成用例基线、发布门禁命令）。

  **注意**：这四份文档已按 1.1.0 目标态预写（`design.md` 第 14 行的「13 个校验方法（含 1.1.0 新增 `requireExpectedValue`）」、`AGENTS.md` 第 16 行的「已内联…含 1.1.0 新增的 `requireExpectedValue`」、`README.md` 的「1.1.0 变更」小节、`docs/testing.md` 的版本行），**代码落地前这些现在时陈述并不成立**（当前 `Cache` 无 3 个新方法、`CacheValidator` 仍是 10 个 `require*`）。实现完成后须逐处复核其真实性；在此之前不得据这些描述判断能力已存在。
- 模块内 `mvn clean verify` 0 failure / 0 error；涉及 Redis 的改动发布前实际跑通 `-Dredis.integration=true`。

## 六、交付配套

| 类别 | 交付物 |
| --- | --- |
| 公共面 | `Cache` 新增 3 个方法 + 完整 javadoc（`@param` / `@return` / `@throws`，含 `UnsupportedOperationException`；CAS 方法写明「参数校验先于能力检查」） |
| 门面 | `CacheKit` 2 个 CAS 静态镜像；`supportsCas()` 入 `CACHE_CONTRACT_ONLY` 白名单（**不镜像**，反向用例钉死） |
| 实现 | `redis.RedisCache`：3 个方法 + 2 条 Lua 脚本 + 包私有 ARGV 装配点（二进制 `eval`）；`caffeine.CaffeineCache`：`supportsCas()` 返回 `false`，2 个 CAS 方法先校验参数再显式抛 `UnsupportedOperationException` |
| 内部 | `internal.CacheValidator` 新增 `requireExpectedValue`（`expectedValue can not be null`） |
| 接入 | `aifei` 包无新增；`CachePlugin` 行为不变 |
| 测试 | 契约测试扩展（含「非法参数抛 IAE 而非 UOE」）+ Lua 常量与 ARGV 装配单测 + Fury 字节确定性固定用例 + **离线**故障不吞用例 + 集成测试扩展（非 ASCII token、`EVAL` 计数）+ 固定向量回归 |
| 文档 | `docs/design.md`、`README.md`、`AGENTS.md`、`docs/testing.md`；发布说明写明兼容性口径 |

## 七、验收标准

- [ ] `mvn clean verify` 通过（0 failure / 0 error），`ClassFileVersionTest` 通过（class major 52）。
- [ ] **误删防护**：值不匹配时 `removeIfEquals` 返回 `false` 且条目仍存在（Redis 侧覆盖；caffeine 侧断言抛 `UnsupportedOperationException`）。
- [ ] **误续期防护**：值不匹配时 `expireIfEquals` 返回 `false` 且 TTL 未变。
- [ ] **`null` / 非法参数**：`expectedValue` 为 `null`、名称或键非法、`ttl` 非法时抛 `IllegalArgumentException`，条目值与 TTL 均未被修改；**参数非法 + `caffeine` 同样抛 `IllegalArgumentException` 而非 `UnsupportedOperationException`**（校验先于能力检查）。
- [ ] **故障不吞**：在不可达 `host:port`（配短超时）构造的实例上调用两个 CAS 方法，抛运行时异常且**不是** `false`（构造期即建连时，「构造或调用任一阶段抛异常」均合格）；该用例不依赖 Redis，默认构建即执行。
- [ ] **原子性与二进制安全**：2 条 Lua 常量文本分别包含 `GET` + 字节比较 + `DEL` / `PEXPIRE`；CAS 只用 `eval(byte[], List<byte[]>, List<byte[]>)`（key 列表长度恒为 1，`keyCount` 由列表推导）；ARGV 装配点输出等于 codec 原始字节、不等于 `String.getBytes(UTF_8)`；Redis 侧以 `INFO commandstats` 的 `cmdstat_eval:calls` 差值佐证每次调用只发一条 `EVAL`。
- [ ] **`putIfAbsent` 单命令回归**：Redis 实现仍为单条 `SET ... PX ... NX`；`design.md` 已标注「锁语义只依赖 `SET NX PX`，`cacheNames()` 的 `SADD` 是附加登记」。
- [ ] **能力一致性**：`supportsCas()` 与两个 CAS 方法行为一致（返回 `false` 的后端抛 `UnsupportedOperationException`），且 `supportsCas()` 不发起任何 Redis 命令；「共同底线与允许差异」清单已写入 `design.md`。
- [ ] **门面镜像**：2 个 CAS 方法有同签名静态镜像；`supportsCas()` 在 `CACHE_CONTRACT_ONLY` 白名单内且**门面不得出现该签名**（反向用例）；`FacadeMirrorTest` 通过。
- [ ] **字节确定性**：同实例两次、不同实例同值、UUID 字符串三条固定用例通过；带共享子对象的对象图不做正向断言，限制记入 `design.md`。
- [ ] **既有实现同步**：4 处既有 `Cache` 实现（3 个测试桩 + `aifei-sa-token` 的 `InMemoryCacheStub`）已补齐方法；`AbstractCache` 未代为实现。
- [ ] **冻结格式回归**：哨兵、物理 key、Fury 编码固定向量用例全部通过。
- [ ] **Redis 集成门禁**：`RedisCacheIntegrationTest` 在 `-Dredis.integration=true` 下通过，未开启时正常跳过；**发布前置已固化「起 Redis + 打开开关 + 连不上即失败」**，不靠人工记忆 —— 当前仓库内无 CI 配置，落地为发布脚本 + `AGENTS.md` 发布检查清单；接入 CI 时该 job 必须带 Redis service。
- [ ] `docs/design.md` / `README.md` / `AGENTS.md` / `docs/testing.md` 已同步（含允许差异清单、校验方法计数 13、门面例外、兼容性声明）。
- [ ] 发布说明写明**公共调用面 minor 兼容、`Cache` SPI 实现面 breaking**；若确认生态内存在外部 `Cache` 实现，版本号改按 major 处理。

## 八、与 `aifei-lock` 的对应关系

| `aifei-lock` 内部操作 | 依赖的 `aifei-cache` 能力 | 现状 |
| --- | --- | --- |
| `LockStore.tryAcquire` | `putIfAbsent(cacheName, key, token, ttl)`（单命令 `SET NX PX`） | 1.0.0 已具备 |
| `LockStore.release` | `removeIfEquals(cacheName, key, token)` | 本次新增 |
| `LockStore.renew` | `expireIfEquals(cacheName, key, token, ttl)` | 本次新增 |
| 启动校验 | `supportsCas()`（`false` 即 `LockPlugin` 启动失败；无 I/O、不探测连通性） | 本次新增 |
| 测试断言租约存在 | `getTtl(cacheName, key)` | 1.0.0 已具备 |

`LockStore.renew` 的 `ttl` 取句柄携带的**创建时 `lease`**（`Lock#renew()` 无参，租约随句柄传递）。lock 启动校验只调 `supportsCas()`，**不维护任何后端类型白名单**：本模块未来新增共享后端时，其能力声明自动生效，lock 侧零改动、无登记义务。

取实例的途径固定为无参 `CacheKit.use()`（返回 `__MAIN__` 主实例，只读、无副作用）或 `@Inject Cache`；`supportsCas()` 不提供门面镜像，lock 侧因此不存在「门面缺方法」的接入风险。`supportsCas()` 是无 I/O 的纯声明，不会因存储不可用而抛异常；lock 侧把该调用包成 `IllegalStateException("lock backend capability check failed")` 的分支仅作防御保留。

## 九、执行方式

- 本文件为任务输入，按仓库根 `AGENTS.md` 的文档分工与模块约束执行。
- 本版本是 `aifei-lock` 的前置包，**先于 `aifei-lock:1.0.0` 发布**；lock 再基于本 release 实现 `CacheLockStore`，在此之前不实现该类（见 `aifei-lock.md` 第二节第 2~4 项）。

## 附：评审意见落实（2026-11-05）

| # | 意见（等级） | 落实结论与落点 |
| --- | --- | --- |
| 1 | Jedis `eval` 必须二进制安全（高） | 采纳。三.1 原子性、三.2、四.1 钉死 `eval(byte[], List<byte[]>, List<byte[]>)`：脚本 / key / ARGV 全字节传递，禁 `String` 重载、禁字符串中转；ARGV 收敛为包私有唯一定义点（返回 `byte[]`，类型系统使 String 重载编译不通过）；离线单测断言「等于 codec 字节、不等于 `String.getBytes(UTF_8)`」，集成用例补非 ASCII `token` 端到端证明 |
| 2 | `false` 语义前后矛盾（高） | 采纳。四.2 统一为「确定的未发生：不存在 / 已过期 / 值不匹配，绝不表达存储故障」，删除「只表达值不匹配」的旧措辞，与行为表一致 |
| 3 | 参数校验与 UOE 优先级未钉死（高） | 采纳。三.1 明确顺序 `cacheName` → `key` → `expectedValue` → `ttl` → 能力检查 → 存储命令；契约测试双向断言（合法参数 + caffeine → UOE；非法参数 + 任意后端 → IAE），七 同步 |
| 4 | Fury 字节确定性需针对 String token 固定用例（中高） | 采纳。五 测试基线新增三条固定用例（同实例 / 不同实例同值 / UUID 字符串）；共享子对象与环仍只记录限制、不做正向断言 |
| 5 | `CacheKit.supportsCas()` 是否镜像未明确（中） | 定为**不镜像**：与 `namespace()` 同列 `CACHE_CONTRACT_ONLY`，反向用例钉死；lock 经无参 `CacheKit.use()` 或 `@Inject Cache` 取实例（三.3、四.5、八） |
| 6 | 新增抽象方法对 SPI 实现者破坏（中） | 采纳并补事实：本仓库内 4 处既有实现须同步补齐（编译期失败）；`AbstractCache` 不得代为实现；兼容性口径「公共调用面 minor 兼容 / SPI 实现面 breaking」写入 README、design 与发布说明；有外部实现则升 major |
| 7 | Redis 集成测试门禁落地（中） | 采纳。发布前置固化「起 Redis + `-Dredis.integration=true` + 连不上即失败」（当前无 CI 配置 → 发布脚本 + `AGENTS.md` 检查清单，接入 CI 时 job 必带 Redis service）；故障不吞用例改为「不可达 `host:port` + 短超时」的离线可执行形态；原子性补 `cmdstat_eval:calls` 差值佐证 |
| 8 | TTL 转换与边界需记录（低中） | 采纳。三.2 钉死 `requireTtl` 三类异常与消息、`long` 毫秒直传 `PEXPIRE`（无 `toMillis()` 溢出、无 Redis 超限）、校验顺序；四.1 补 Lua 返回值语义（`1` = 命中并执行、`0` = 未发生，不看 `DEL` / `PEXPIRE` 返回值） |
| 9 | `putIfAbsent` 的 `SADD` 登记失败要求降级（跨文档，2026-11-05 定档） | **不采纳** lock 侧「登记失败只记日志、仍返回 `true`」的要求，理由见四.4。落点：`aifei-lock.md` 第二节改为由 lock 以 `LockException`「结果未知」承接、残留锁由租约兜底，本版本不新增该分支 |
