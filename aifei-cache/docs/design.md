# aifei-cache 设计文档

本文是**设计事实来源**：契约、决策与约束以本文为准。面向使用者的说明（含逐键配置说明）见 `README.md`，面向开发者的约束见 `AGENTS.md`。

## 定位与设计原则

应用代码只依赖缓存抽象接口，不依赖任何具体缓存实现；单实例与多实例、本地与分布式之间的差别，全部通过配置表达。

- 公共 API 面固定 5 个类型：`Cache`、`Counter`、`CacheUtil`、`CounterUtil`、`CachePlugin`。
- 实例划分：`internal`（非承诺 API）、`caffeine`（本地实现）、`redis`（分布式实现）。
- 存储与编解码：Caffeine（本地）、Jedis（Redis）；Redis 的 value 序列化固定用 Fury。
- 依赖注入：aifei；主缓存与主计数器可通过 `@Inject` 注入。
- **物理 key**：缓存为三段式 `namespace:cacheName:key`，计数为 `namespace:COUNTER:counterName:key`；**不做转义、不做编码**。命名空间与名字（`cacheName` / `counterName`）不得含 `:`，否则该段会被解析成分级；`key` 不受此限。
- **契约基线与变更纪律**：`internal.CacheValidator` 的 9 个校验方法（含命名空间校验）已内联为本项目源码，其规则与异常消息属**冻结契约**（见「异常模型」）；其余契约语义在本项目内定义，可自主演进。**不依赖 `cn.aifei:aifei-cache` 构件**，唯一保留的 aifei 依赖是框架核心 `cn.aifei:aifei`。

设计原则六条：

1. **接口极简**：`Cache` / `Counter` 只暴露通路能力，不依赖运行期环境；业务只依赖接口，公共 API 不得暴露 Caffeine、Jedis、Fury 等实现类型。
2. **实现可替换**：切换 Caffeine / Redis 只改配置与装配，不改业务代码；两个后端遵循同一套契约与公共行为。`Cache` / `Counter` 的实现类各共用无行为元数据基类 `internal.AbstractCache` / `internal.AbstractCounter`，都不得再引入其它跨实现基类（装配侧的 provider 抽象父类不在此限，见「实现约束」）。
3. **实例隔离**：单应用可持多实例，靠实例名标识、`namespace` 隔离，互不覆盖。同一应用内所有实例（含主缓存）的 `namespace` 必须互不相同；参与排他判定的实例（实现 `internal.UniqueNameAware` 的后端，目前是 jedis）由启动期排他校验拦截。
4. **平滑演进**：单实例本地缓存扩到集群只改配置与装配，本地模式与集群模式的配置差异只在 `cache.type`。
5. **保持极简**：不做未被需求驱动的抽象；新增能力先过设计决策。
6. **兼容 Java 8**：代码、依赖与 API 均以 Java 8 为基线（主 JAR 字节码 major version 52）；Caffeine 锁定 2.9.3。

## 术语与命名

| 术语 | 含义 |
| --- | --- |
| 实例 | 一个 `Cache` 对象及其派生的 `Counter`，由实例名标识 |
| 实例名 | 主缓存为 `__MAIN__`（保留名，不可重命名）；扩展实例名由 `cache.names` 声明 |
| 缓存名 / 计数名 | 实例内的逻辑分组，区分大小写，不能为空白，**不得含 `:`** |
| key | 缓存名内的业务键，可用冒号分级，区分大小写，不能为空白 |
| namespace | 物理 key 的第一段，用于同一存储上的多应用隔离；不得含 `:` |
| 物理 key | `namespace:cacheName:key`（缓存）、`namespace:COUNTER:counterName:key`（计数） |

实例名**不参与**物理 key 拼接：同一应用内的不同实例靠 `namespace` 区分。主缓存实例名是**保留名**而非「默认值」，取主实例走 `CacheUtil.use()` 或 `@Inject`，调用方无需引用该常量。

## 配置模型

底层是 `java.util.Properties`，由 `PropKit.use("app-config.txt")` 载入；全部解析与校验发生在 `CachePlugin.start()` 内（见「装配与生命周期」）。

**逐键的类型、必填性与默认值见 `README.md` 的「配置参考」**；本节只记录设计级规则：

- **jedis 键的 `jedis` 分段**：jedis 专有键统一为 `cache.jedis.*`（扩展实例 `cache.{name}.jedis.*`），与 caffeine 的 `cache.caffeine.maxSize` 对称。分段名的唯一定义点是 `RedisConfig.JEDIS_KEY_SEGMENT`，读取侧（`RedisCacheProvider`）与报错侧（`RedisConfig` 的 setter，经重写的 `configKey`）都走 `RedisConfig.jedisKey`，故键名不会两侧漂移。**不带 `jedis` 分段的平铺键不再被读取**。
- **URI 与显式项的优先级**：`cache.jedis.uri` 提供连接信息；配置后 `host` / `port` 一律取自 URI，而显式配置的 `database` / `user` / `password` 仍优先于 URI 中的对应项。唯一名的计算沿用同一套语义，不另立规则。
- **读取与范围校验的分工**：配置项**读取**（`internal.Config` 的 `optionalText` / `optionalBoolean` / `optionalInt` / `optionalLong`）只做语法解析与「空值视为未配置」归一，不判范围；数值型选项的**范围校验**由 `RedisConfig` 的 setter 承担 —— 可选项遇 `null` 不校验、不赋值（保持字段默认），非 `null` 先校验后赋值；跨字段的**连接池上下限关系**由 `RedisConfig.requirePoolBounds()` 在链式调用末尾校验。两类规则各自只有一处定义。
- **`cache.jedis.allowedPackages`**：声明可被反序列化的业务值类型所在包，逗号分隔（`com.foo` 与 `com.foo.*` 等价）。未声明的业务类型在**写入**序列化时抛异常；JDK 内置类型与框架自身类型无需声明。该键**只认根级**，同一进程内所有实例共用一份策略。`redis.FuryRedisValueCodec` 另内置一份**硬编码的拒绝清单**（`javax.naming.*`、`javax.script.*`、`java.lang.Runtime`、`java.lang.ProcessBuilder` 等），先于放行判定生效，即使被 `allowedPackages` 粗粒度覆盖也不放行。
- **连接池上下限关系**在启动期校验：`maxIdle ≤ maxTotal`、`minIdle ≤ maxTotal`、`minIdle ≤ maxIdle`（`maxTotal = -1` 时不参与比较），违反即抛 `IllegalArgumentException`。
- 未显式配置的连接池容量、连接校验、空闲扫描与 JMX 参数不主动覆盖底层默认值；默认容量是保守值，不是生产容量建议。
- **不可配置的四个对象**：`sslSocketFactory`、`sslParameters`、`hostnameVerifier`、`valueCodec` 无配置键，也没有其他注入出口；需要自定义 value 序列化或 SSL 组件时本项目不支持。不提供 `Ticker` 配置入口，可控时钟只用于测试。

### 扩展实例（`cache.names` 中每个 `name`）

- **声明方式**：主缓存由根级 `cache.*` 配置且恒存在；只有主缓存时无需配置 `cache.names`。扩展实例由 `cache.names` 声明，**未声明的 `cache.{name}.*` 配置无效**。
- **`cache.names` 解析**：逗号分隔，每项 `trim()`，出现空项即抛异常（`mem,,other`、结尾逗号都算空项）；未配置视为无扩展实例；区分大小写。
- **实例名合法性**：不得包含 `.`、`=`、`:` 或空白；`type`、`ttlMillis`、`cacheNull`、`namespace`、`names`、`main`、`allowedPackages` 不可作为实例名；也不得使用 `__MAIN__`；`cache.names` 内重名同样在启动期抛异常。该表与 `Config.RESERVED_INSTANCE_NAMES` 同源，**根级配置键改名时必须同步改这张表**，否则会漏掉同名前缀造成的标量 / 前缀歧义。
- **`type` 取值**：`jedis` / `caffeine`，严格小写，不提供 `redis` 别名。
- **缺省继承**：`cache.{name}.ttlMillis` 与 `cache.{name}.cacheNull` 未配置时继承主缓存同名项；`cache.{name}.type` 与 `cache.{name}.namespace` 不继承。继承规则的唯一实现点是 `Config.resolveTtl` / `resolveCacheNull`。
- **读取 API**：一律经 `cn.aifei.util.Prop#get` 取**原始字符串**，再由 `internal.Config` 的 `optional*` / `requireConfig*` / `resolve*` 自行解析；不直接用 `Prop#getInt` / `Prop#getLong` —— 前者会截断，后者会把「语法错」与「范围错」混成一类，且都拿不到统一报错所需的键名与原值。
- **校验时机**：全部配置在 `CachePlugin.start()` 内解析与校验；失败抛 `IllegalArgumentException`，消息格式 `cache config invalid: <key>=<value>, reason=<原因>`，阻止应用启动。构造函数不做任何解析与校验。
- **profile 覆盖**：覆盖文件名为 `app-config-{profile}.txt`，命令行用 `--aifei.profiles.active=<profile>` 指定。覆盖粒度是**按键整值替换**，`cache.names` 在 profile 文件中必须写全量列表。
- **注释与数值写法**：底层是 `java.util.Properties`，只支持整行注释（`#` / `!`），不支持行内注释；value 是字面字符串，`60000` 不能写成 `60_000` 或 `60 * 1000`。
- **空值语义**：`cache.<key> =`（空值）与未配置等价，一律按未配置处理。

## 公共接口契约

### `Cache`

```java
public interface Cache {

    // 抽象方法
    <T> T get(String cacheName, String key);
    boolean exists(String cacheName, String key);
    void put(String cacheName, String key, Object value, Duration ttl);
    boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl);
    boolean expire(String cacheName, String key, Duration ttl);
    void remove(String cacheName, String key);
    void clear(String cacheName);
    Duration getTtl(String cacheName, String key);
    List<String> keys(String cacheName, int begin, int end);
    Set<String> cacheNames();
    Duration ttl();
    boolean cacheNull();
    String namespace();

    // default 方法：秒重载、Loader 委托与省略 TTL 重载
    // TTL：Duration 参数是毫秒精度（毫秒级的缓存时间用它）；int ttlSeconds 只能表达整秒
    <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader);
    <T> T get(String cacheName, String key, int ttlSeconds, Supplier<T> loader);
    <T> T get(String cacheName, String key, Supplier<T> loader);          // TTL 取实例默认
    void put(String cacheName, String key, Object value, int ttlSeconds);
    void put(String cacheName, String key, Object value);                 // TTL 取实例默认
    boolean putIfAbsent(String cacheName, String key, Object value, int ttlSeconds);
    boolean putIfAbsent(String cacheName, String key, Object value);      // TTL 取实例默认
    boolean expire(String cacheName, String key, int ttlSeconds);
    boolean expire(String cacheName, String key);                         // TTL 取实例默认
}
```

**参数与返回值**

- 普通 `get`：参数非法或未命中返回 `null`，**不抛异常**。
- `exists`：参数非法或不存在返回 `false`；这是**存储级**判定，命中哨兵必须返回 `true`，不得用 `get(...) != null` 实现。
- `remove`：参数非法时不执行任何操作、不抛异常。`clear` 与其余写路径**参数非法时抛 `IllegalArgumentException`**。
- `getTtl`：返回剩余有效期；条目不存在、已过期或参数非法返回 `null`；没有过期时间的条目（Redis `PTTL == -1`）返回 `Integer.MAX_VALUE` 秒。
- `keys`：返回该缓存名下键的指定区间，见「行为契约」的键区间查询。
- `cacheNames`：见「`cacheNames()` / `counterNames()` 集合的存储」。
- `ttl` / `cacheNull` / `namespace`：只读配置自述，无运行期 setter。
- 带 `loader` 的 `get`：`loader` 为 `null` 或 `cacheName` / `key` / TTL 非法时抛 `IllegalArgumentException` 且**不调用** `loader`；校验顺序为 `loader` → `cacheName` → `key` → TTL。
- 省略 TTL 的三个写重载（`put` / `putIfAbsent` / `expire`）缺省取实例 `ttl()`。

**行为契约**

- **`cacheNull` 与哨兵**：`cacheNull = true` 时写入 `null` 落成包私有哨兵 `NullValue.INSTANCE`，`get` 命中哨兵返回 `null`（对调用方透明），`exists` 返回 `true`，`putIfAbsent` 视为已存在并返回 `false`，`getTtl` 返回其剩余有效期。
- **`cacheNull = false` 时写入 `null`：不写入**。`put` 直接返回、`putIfAbsent` 返回 `false`，**都不抛异常**；「不写入」是保持现状（既有值不被清掉），也不发起任何存储命令。`loader` 返回 `null` 同理。
- **命中否定结果不触发回源**：`cacheNull = true` 且缓存项为哨兵时，带 `loader` 的 `get` 返回 `null`，既不调用 `loader`，也不刷新该哨兵的有效期。
- **`null` 的判定与转换只有一个定义点**：`internal.AbstractCache.toStoredValue(value, cacheNull)`。非 `null` 值原样返回；`null` 且 `cacheNull = true` 返回哨兵；`null` 且 `cacheNull = false` 返回 `null`，表示不应写入。`fromStoredValue` 负责读取侧的还原。
- **回源单飞（`loader` 语义）**：带 `loader` 的 `get` 在未命中时按**物理 key** 做同 JVM 回源单飞 —— 同一缓存项的并发未命中收敛为一次 `loader` 调用，其余线程复用其返回结果（含 `null`，不受 `cacheNull` 影响）。边界：只覆盖单个 JVM，多实例部署时每个实例各自装载一次；等待被中断的线程退化为自行回源；`loader` 内不得对**同一缓存实例**的其它 key 再走带 `loader` 的 `get`（交叉等待可能成环）；`loader` 抛出的 `RuntimeException` / `Error` 原样传递给该批跟随者。定义点在 `internal.SingleFlight`，实现类不参与。
- **清理语义**：`clear(cacheName)` 清空该名称下的全部缓存项并把该名称移出 `cacheNames()`，非原子、尽力清理。名字不得含 `:`，故不存在「下级名称」，数据清理范围与名称移除范围一致。
- **键区间查询（`keys`）**：返回该缓存名下全部键的指定区间，先按业务 key 字典序升序排序再切片 —— 排序是分页可复现的前提。区间为闭区间（两端均包含）、负数索引从末尾计数（`-1` 为最后一项）、越界钳制、换算后 `begin > end` 或无条目返回空列表，语义对齐 Redis `LRANGE`；换算与切片的唯一定义点在 `internal.KeyRanges`。`cacheName` 非法抛 `IllegalArgumentException`；`begin` / `end` 任意 int 值均合法。结果是弱一致快照：扫描期间并发写入可能使相邻两次分页重叠或遗漏；键枚举只看键存在，哨兵条目的 key 照常返回。
- **TTL 精度与上下限**：配置与接口取值均为毫秒；合法范围 `[1, 2147483647000]`（1 毫秒 ~ `Integer.MAX_VALUE` 秒）；超限不截断、直接抛 `IllegalArgumentException`；`int ttlSeconds` 重载必须大于 0。
- **命名责任（调用方约束）**：必须保证各实例 `namespace` 互不相同。名字也不得含 `:`（由参数校验拒绝）；`key` 可以含 `:`、不参与分级，也不因此被拒绝 —— 三段都不携带分段符，拼接因此是单射的。

### `Counter`

```java
public interface Counter {

    Long get(String counterName, String key);
    long increase(String counterName, String key, long step, Duration ttl);
    long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);
    long decrease(String counterName, String key, long step, Duration ttl);
    long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);
    void remove(String counterName, String key);
    void clear(String counterName);
    Set<String> counterNames();
    Duration ttl();
    String namespace();

    // default 方法：秒重载
    long increase(String counterName, String key, long step, int ttlSeconds);
    long increaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds);
    long decrease(String counterName, String key, long step, int ttlSeconds);
    long decreaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds);

    // default 方法：省略 TTL 重载，缺省取实例默认 TTL（cache.ttlMillis）
    long increase(String counterName, String key, long step);              // TTL 取实例默认
    long increaseAndRefreshTtl(String counterName, String key, long step); // TTL 取实例默认
    long decrease(String counterName, String key, long step);              // TTL 取实例默认
    long decreaseAndRefreshTtl(String counterName, String key, long step); // TTL 取实例默认
}
```

- `get`：计数项不存在或已过期返回 `null`，不返回 `0`。
- 四种增减：计数项不存在时按当前值 `0` 创建并使用传入 TTL；`step` 必须大于零。
- **固定窗口**：窗口长度由首次写入的 TTL 决定，窗口内不刷新 —— `increase` / `decrease` 命中时保留原 TTL，`increaseAndRefreshTtl` / `decreaseAndRefreshTtl` 把剩余 TTL 重置为传入值。
- `remove`：参数非法时不执行任何操作、不抛异常。`clear` 与四种增减**参数非法时抛 `IllegalArgumentException`**。
- `clear`：清空该计数名称下的全部计数项，非原子、尽力清理；并把该名称从 `counterNames()` 中精确移除。
- 数值溢出抛 `ArithmeticException` 且不改变数值与 TTL。
- `ttl` / `namespace`：只读配置自述，无运行期 setter。
- 省略 TTL 的四个重载一律取实例 `ttl()`；其固定窗口 / 闲置过期语义与显式 TTL 重载逐一同源。

## 装配与生命周期

### 装配入口与顺序

唯一入口是 `CachePlugin(Prop)`：

- 构造函数**只保留 `Prop` 引用**（仅守卫 `null`），不解析、不校验、不创建实例、不建立连接；配置错误的表现是**启动失败**，而不是 `plugins.add(new CachePlugin(prop))` 抛异常。
- 不提供单实例构造 `CachePlugin(Cache)`：多实例由配置统一描述，实例的创建、注册与关闭由插件独占持有；代价是 `sslSocketFactory` / `sslParameters` / `hostnameVerifier` / `valueCodec` 没有注入出口。

`start()` 内的装配顺序：

1. **解析实例名单**：主缓存在首，其后是 `cache.names` 声明的扩展实例，保持声明顺序；空项、重名、保留名在此被拒。
2. **逐个解析并构造**：由实例名推导配置前缀（主缓存为 `cache`，扩展实例为 `cache.<name>`），按该前缀下 `cache.type` 的取值从注册表取可复用的 provider，调用 `provider.buildCache(instanceName, prefix, prop)`；通用字段与后端专有字段都在这一次调用内解析。
3. **逐个注册**：实例建成后立即 `CacheManager.register(实例名, cache)`；同源 `Counter` 派生与「唯一名 + `namespace`」排他判定都在 `register` 内完成，调用方不单独创建 `Counter`。
4. **框架集成**：主 `Cache` 与主 `Counter` 的单例在注册之后写入 `AopKit`，使 `@Inject` 生效。
5. **任一步失败**：先 `CacheManager.clear()` 再向上抛，不留下「注册表非空、实例不完整」的半装配状态；已创建实例不逐个关闭（框架捕获 `Plugin.start()` 异常后终止 JVM）。

### 实例注册表 `CacheManager`

- 位置 `cn.hg.aifei.cache.internal.CacheManager`，非承诺 API；持有「实例名 → (Cache, Counter)」，是 `CacheUtil` / `CounterUtil` 取用实例的唯一数据源。
- `register` 做两件事：派生同源 `Counter`（全项目唯一的派生点）、校验「唯一名 + `namespace`」唯一。
- `register` 的校验顺序固定：实例名（不能为空白，且须符合已注册名规则）→ `cache` 不能为 `null` → 实现类必须实现 `internal.CounterFactory`（否则被拒）→ 同名重复注册抛 `IllegalStateException` → 排他校验 → 派生出的 `Counter` 不能为 `null`。
- **排他判据**：两个实例冲突 ⟺ 唯一名相同（且均非空）并且 `namespace` 相同。唯一名由能力接口 `internal.UniqueNameAware` 声明（**不经 `Cache` 接口暴露**）：jedis 返回 `host:port/database`，进程内后端不实现该接口（不参与排他）。冲突抛 `IllegalArgumentException`，消息含 `duplicate key space` 与两个实例名。
- 排他校验的唯一落脚点是 `register`：它是所有实例进入系统的唯一写入口，无论实例来自自动装配还是手写代码。

### 工具门面 `CacheUtil` / `CounterUtil`

- 两个类都是 `public final` + 私有构造 + 全静态方法；静态方法一律作用于主缓存，扩展实例必须先 `use(name)`。
- `use(name)` 返回注册表中的同一个实例对象，调用方可以安全缓存引用。
- 门面镜像 `Cache` / `Counter` 的**全部业务方法**（`namespace()` 除外）：`Duration` 与 `int ttlSeconds` 双重重载、省略 TTL 的写重载，以及 `exists` / `remove` / `getTtl` / `cacheNames()` / `counterNames()` / `ttl()` / `cacheNull()` / `clear()`。
- **不镜像** `namespace()`（配置自述，业务不需要经门面访问）；`FacadeMirrorTest` 以白名单放行，并有反向用例钉住「不得补齐」。
- 门面只做转发：不重复校验参数、不转换异常、不吞异常。

### `cacheNames()` / `counterNames()` 集合的存储

两个集合的语义都是「历史上出现过、且未被**同名** `clear` 清空」：`cacheNames()` 记写入过的缓存名，`counterNames()` 记创建过的计数名称。

- `clear(name)` 清空 `name` 下的全部数据并把 `name` 从集合中**精确移除**：名字不得含 `:`，因此不存在「下级名称」，数据范围与名称移除范围一致。`remove` **完全不触碰集合**（职责切分：`clear` 管名称与清理，`remove` 只删单条数据）。
- TTL 到期与容量淘汰**不移除**名称。
- 因此集合**不是**「当前有数据的名称清单」：它既可能包含已过期的名称，在 Redis 跨实例并发下也可能漏掉仍在使用的名称。
- `cacheNames()`：Caffeine 侧存于实例内的名称集合字段（与业务条目分离，不受缓存容量约束），可见范围是本进程；Redis 侧存于同命名空间下的固定 key（`SADD` 追加、`SREM` 移除），在同一 Redis 上的所有应用实例之间共享。
- `counterNames()`：Caffeine 侧是计数器实例内的集合（专用锁保护）；Redis 侧是同命名空间下的固定 key，由计数更新脚本在**计数项首次创建**时 `SADD` 登记，与数值创建同处一次 `eval`，不额外增加往返。
- 计数名称集合的登记时机、存储载体与 `clear` 的遍历方式**没有跨实现定义点**：两个后端各自实现，不得下沉到 `internal.AbstractCounter`。`clear` 的匹配判定同样不设共用方法，在各调用点就地书写：Caffeine 侧是同源组合键 `CaffeineCacheKey#belongsTo` 按「命名空间 + 名称」匹配，Redis 侧是 `SCAN MATCH` 的 `namespace:name:*` 模式；名称集合的移除用精确匹配（Caffeine 侧 `Set#remove`、Redis 侧 `SREM`），与数据清理范围一致。
- 该能力属实现相关，调用方不能依赖它在两个后端间给出相同结果。

### 生命周期

- `start()` 创建全部实例并注册；`stop()` 先对注册表快照 → `CacheManager.clear()` 切断取用入口 → 再按快照关闭实例。
- `start()` / `stop()` 均为幂等；`stop()` 内 `started = false` 在关闭动作完成后才置位。
- 关闭集合是「本次创建的实例中实现了 `AutoCloseable` 的」，即只有各 `RedisCache`（持有连接池）；`CaffeineCache` 与两种 `Counter` 都不在其中。
- 关闭过程抛错时包成 `RuntimeException("Failed to close cache plugin", e)` 抛出，不吞异常。
- `stop()` 后再次 `use(name)` 抛 `IllegalStateException`，不会拿到已关闭的实例。

## 实现约束

### 跨实现公共逻辑的六条承载方式

新增逻辑前先判断它落在哪一条（与各条对应的「唯一定义点」清单见 `AGENTS.md`）：

| # | 承载方式 | 承载内容 |
| --- | --- | --- |
| 1 | `internal` 静态工具类 | `CacheValidator`（接口参数校验）、`Config`（配置域与框架级常量）、`SingleFlight`（回源单飞）、`KeyRanges`（`keys` 区间换算与切片） |
| 2 | 接口 `default` 方法 | 秒重载、省略 TTL 重载与 `loader` 委托 |
| 3 | 实现类自身 | 各后端专有逻辑 |
| 4 | `internal.AbstractCacheProvider` | **仅**两个后端 provider 的通用配置装配（`initConfig`）；不得承载实现逻辑，不得被实现类继承 |
| 5 | `internal.AbstractCache` | `Cache` 两实现类共用的**无行为元数据基类**：只承载 `namespace` / `ttl` / `cacheNull` 自述，以及哨兵编解码与物理 key 拼装。不得承载存储读写逻辑，`Counter` 实现类不继承它 |
| 6 | `internal.AbstractCounter` | `Counter` 两实现类共用的**无行为元数据基类**：只承载 `namespace` / `ttl` 自述与计数物理 key 拼装。不得承载计数读写、名称登记或清理逻辑，`Cache` 实现类不继承它 |

- **不下沉到接口**：「下沉到接口 `static` 或接口常量」等于成为公共 API，本库的答案是**不下沉** —— 实现专用能力落在上述两个 `internal` 抽象父类，框架级常量落在 `internal.Config`；确有内容属于公共契约时才例外，并在注释中标注【实现专用】或【数据格式】。因此哨兵类型 `NullValue` 落在 `internal` 包（仍是包级私有），`Cache` / `Counter` 不声明任何 `static` 方法与常量。
- Java 8 的接口成员只能是 `public`，无法在接口上表达 `protected` 钩子；元数据与编解码的承载因此落在上述两个抽象类上，而它们位于 `internal`、不扩张公共 API 面。

### CaffeineCache

- 存储：Caffeine `Cache` + `Policy.VarExpiration`；key 为自带的组合键类型，`equals` / `hashCode` 参与三段比较，`belongsTo` 按「命名空间 + 名称」匹配。缓存名集合另存于实例字段（`namesLock` 保护），**不占用缓存容量，也不与业务条目共用 key 空间**。
- 并发：同 key 的 `get` / `exists` / `put` / `putIfAbsent` / `expire` / `remove` 共用同一套 128 把分段锁；`expire` 的存活检查与 TTL 更新在同一把锁内完成。
- `exists` 用底层条目存在性判定（命中哨兵也返回 `true`）。
- `clear(cacheName)` 先按「命名空间 + 缓存名」收集匹配条目，再逐条 `invalidate`，最后移除名称登记。
- `keys` 遍历底层 `keySet` 按 `belongsTo` 过滤、取组合键的业务 key，排序后经 `internal.KeyRanges.slice` 切片；不持分段锁，弱一致。
- 不实现 `AutoCloseable`（条目全在进程内、由 GC 回收，不持有需要显式释放的资源），也不实现 `internal.UniqueNameAware`（进程内缓存各自独占存储，不参与启动期排他判定）。
- 容量与命名空间校验在构造期完成（容量先于命名空间）；`namespace` 不能为空白，也不得含 `:`。
- 实现包内 `internal.CounterFactory`，`createCounter()` 返回同 `namespace` / 同配置的 `CaffeineCounter`。

### CaffeineCounter

- 存储：独立的 Caffeine 存储与分段锁，不与同实例的 `CaffeineCache` 共享。
- `increase` / `decrease` 在锁内读改写并做溢出检查；`*AndRefreshTtl` 在锁内更新数值并把剩余 TTL 重置为传入值。
- 固定窗口命中时只改数值、保留原绝对截止点（`replace` 不触碰有效期）；替换前该条目已过期或被淘汰时，从 `0` 重新创建并登记。**不得把此前采样到的剩余 TTL 当作新 TTL 写回**。
- 数值溢出抛 `ArithmeticException` 且不改变数值与 TTL。
- 计数名称集合是计数器实例内的 `LinkedHashSet`，由专用锁保护，与业务条目的分段锁不嵌套；`counterNames()` 返回副本。
- 名称只在计数项首次创建时登记；`clear(counterName)` 遍历内部 keySet，按 `namespace` + `counterName` 精确匹配后逐条 `invalidate`，随后在同一把锁内**精确移除**集合中的该名称。
- 不实现 `AutoCloseable`。

### RedisCache

- 命令：`SET ... PX` / `SET ... NX PX` / `EXISTS` / `PEXPIRE` / `PTTL` / `DEL` / `SADD` / `SREM` / `SMEMBERS` / `SCAN` + `UNLINK`。
- 读取结果先过 `AbstractCache.fromStoredValue`，写入值先过 `AbstractCache.toStoredValue`；后者返回 `null` 时直接返回、不发任何命令。
- `exists` 用 `EXISTS`，不得用 `get(...) != null`。
- `clear` 用 `SCAN MATCH ... COUNT 1000` 逐页 `UNLINK`，不使用 `KEYS`；glob 转义字符集为 `* ? [ ] \`。
- `keys` 用 `SCAN MATCH`（前缀经与 `clear` 同源的 glob 转义）收集物理 key、剥前缀还原业务 key，排序后经 `KeyRanges.slice` 切片；不使用 `KEYS`。
- 实现 `AutoCloseable`：`close()` 关闭连接池，重复调用不重复关闭。
- 实现 `internal.UniqueNameAware`：唯一名取自 `RedisConfig#uniqueName()`，构造期算好并固定，本类不保留 `RedisConfig`。
- 实现包内 `internal.CounterFactory`，`createCounter()` 返回共享同一客户端的 `RedisCounter`。
- 与 `RedisCounter` 各自的每轮 `SCAN` 工作量提示（`SCAN_COUNT = 1000`）同值但各自定义：它是本后端的调参而非存储内容，故不强制收敛到单一定义点。

### RedisCounter

- 计数逻辑用 Lua 脚本执行，保证读改写原子；脚本必须返回 Redis 原始字符串，由 Java 侧解析。
- 非整数或缺失 TTL 抛 `IllegalStateException`；`WRONGTYPE` 原样传播。
- TTL 必须在 Java 侧先校验再执行脚本：超限值一旦进入 Redis 会留下「数值被改动、TTL 未刷新」的脏状态。合法 TTL 下，固定窗口命中不执行 TTL 命令。
- 计数名称集合是本命名空间下的固定 key（`namespace + ":" + Config.COUNTER_NAMES_NAME`），由更新脚本在计数项首次创建时 `SADD` 登记；`counterNames()` 用 `SMEMBERS`，`clear(counterName)` 用 `SCAN MATCH` + `UNLINK` 清计数项（glob 模式只匹配带保留前缀的计数 key，不命中名称集合 key），再直接 `SREM` 移除该名称本身（按成员精确删除）。
- 更新脚本因此是**双 key**脚本（计数项 key + 名称集合 key）；本库只用单机 `RedisClient`，不涉及 Redis Cluster 的同槽约束。
- 不持有独立 Redis 客户端：连接随对应 `RedisCache` 关闭，因此不实现 `AutoCloseable`。

### 包结构与复用边界

- 代码落在 `cn.hg.aifei.cache` 及其 `internal` / `caffeine` / `redis` 子包。
- 两个实现包各自独立：`caffeine` 用 Caffeine 本地存储 + 分段锁，`redis` 用 Jedis + Fury value 编解码；包内辅助类型（组合键、过期策略、分段锁、value codec）均为包级私有，不跨包复用。
- `internal` 包承载非承诺 API：`AbstractCache`、`AbstractCounter`、`NullValue`、`CacheValidator`、`Config`、`SingleFlight`、`KeyRanges`、`CacheManager`、`CounterFactory`、`UniqueNameAware`、`CacheProvider`、`AbstractCacheProvider`、`CacheProviderRegistry`。其中 `CacheProviderRegistry` 引用 `caffeine` / `redis` 两个子包（组合根落在这里），但只引用 provider 类本身，方法签名不含第三方后端类型，因此不破坏 `optional` 隔离。
- 配置域常量与键空间保留名集中在 `internal.Config`（`ROOT_PREFIX` / `MAIN_INSTANCE_NAME` / `ALLOWED_PACKAGES_KEY` / `NAMES_KEY` / `DEFAULT_NAMESPACE` / `DEFAULT_TTL` / `MAX_TTL_MILLIS` / `COUNTER_KEY_PREFIX` / `CACHE_NAMES_NAME` / `COUNTER_NAMES_NAME`）；**`Cache` / `Counter` 接口不声明任何常量与 `static` 方法** —— 属数据格式的保留前缀与保留名也放在 `internal.Config`，公共面只留行为契约。

### 后端扩展点

- `internal.CacheProvider` 只有两个方法：`type()` 与 `buildCache(instanceName, prefix, prop)`；`internal.CacheProviderRegistry` 内置 `jedis` / `caffeine` 两个后端，持有 `type → 可复用的无状态 provider`。
- 新增后端 = 新增 provider 实现 + 在注册表静态初始化里注册一行；不得回头修改 `CachePlugin` 的装配逻辑或 `CacheManager`。
- **provider 必须无状态**：不得声明实例字段保存解析结果；配置在 `buildCache` 内经 `buildConfig(cfg, prefix, prop)` 现读现用，否则同一后端的多实例会互相串配置。
- provider 在 `buildConfig(cfg, prefix, prop)` 内读取本后端专有字段，键名由本后端的键分段拼装（jedis 走 `RedisConfig.jedisKey(prefix, ".port")`，caffeine 走 `prefix + ".caffeine.maxSize"`），取值校验交配置类 setter；实例名、类型、前缀与通用字段（`namespace` / `ttlMillis` / `cacheNull`）由 `AbstractCacheProvider#initConfig` 统一写入。配置键必须由 `prefix` 拼装，不得硬编码 `cache` 字面前缀。
- 配置对象（`Config` 子类实例）是**瞬时对象**：在 `buildCache` 内创建、用完即弃，不得存进 provider 字段，也不得跨「构造期 → `start()`」传递。
- 缓存实例由各子类在自己的 `buildCache` 内创建；`AbstractCacheProvider` 只提供 `initConfig`，不提供实例创建钩子。
- `cache.type` 的允许取值由 `CacheProviderRegistry#types()` 派生，不得硬编码。

## 异常模型

- 普通 `get`、`getTtl`、`exists`、`remove` 的非法参数（名字为空白或含 `:`、key 为空白）走「不可能命中」路径（返回 `null` / `false` / 不执行），不抛异常。
- 其余参数校验失败统一抛 `IllegalArgumentException`。
- 配置校验失败抛 `IllegalArgumentException`，消息格式 `cache config invalid: <key>=<value>, reason=<原因>`。
- 注册期冲突抛 `IllegalArgumentException`（`duplicate key space`）；同名重复注册抛 `IllegalStateException`。
- 计数溢出抛 `ArithmeticException`；Redis 端类型不符按 `IllegalStateException` 或原样传播。
- 底层缓存、连接、序列化或标准库运行时异常按原始类型传播。
- 参数校验顺序：带 `loader` 的 `get` 为 `loader` → `cacheName` → `key` → TTL，不得改变。
- `internal.CacheValidator` 的 9 个校验方法（2 个布尔判定 `isValid*` / `isInvalid*` + 7 个 `require*` 校验并抛异常）是参数合法性的唯一定义点，其**判定规则与异常消息文本属冻结契约**，不得变更：命名空间与名字非空白**且不含 `:`**，key 非空白；9 个方法中 `requireValue` 当前无调用方，仅为保留的冻结入口 —— 写入 `null` 由 `cacheNull` 规则处理，不经它拒绝；确有变更需求时，必须先更新测试中的消息断言。其余异常消息由本项目自定义。

## 演进约束

- **接口稳定**：两个接口都只保留通路能力；配置自述与诊断类成员（`Cache` 的 `cacheNames()` / `ttl()` / `cacheNull()` / `namespace()`，`Counter` 的 `counterNames()` / `ttl()` / `namespace()`）不参与数据通路，新增同类能力前必须先过设计决策。
- **装配后不可变**：实例级配置只读，接口不提供运行期 setter。
- **边界不越**：接口成员不得引用实现类型（实现类型只出现在实现类与 `internal`）；配置域常量不进接口，统一定义在 `internal.Config`；`Cache` / `Counter` 的实现类只共用各自的 `internal` 基类；公共逻辑限六条承载方式（见「实现约束」）。
- 任何改变公共行为的设计决策，先更新本文档，再落实到代码与测试。

## 暂不支持

| 项 | 说明 |
| --- | --- |
| 永久缓存 | 所有写入必须带 TTL |
| 批量操作 | 不提供多 key 批量读写 |
| 缓存统计 | 不提供命中率、条目数等指标 |
| 自定义 value 序列化 | 无注入出口 |
| 自定义 SSL 组件 | 无注入出口 |
| 固定窗口的滑动语义 | 只提供固定窗口 |

## 测试与验收

- **判据**：`mvn clean verify` 必须 0 failure / 0 error；跳过项必须在报告中逐条列举并说明理由。
- Redis 集成用例默认跳过，用 `-Dredis.integration=true -Dredis.uri=...` 显式开启；开启后连不上必须失败，不得静默跳过；用例使用随机前缀并在结束时按前缀 `SCAN` + `UNLINK` 自清理。
- 回归覆盖必须包含下列各项：
  1. `expire` 的存活检查与 TTL 更新在同一把锁内，已过期条目不被复活。
  2. 读改写共用分段锁，`remove` 不被旧值写回覆盖。
  3. 刷新 TTL 保留原绝对截止点，固定窗口不被逐次延长。
  4. 计数 TTL 在 Java 侧先校验，超限值不进入脚本。
  5. 哨兵契约：`get` 返回 `null`、`exists` 返回 `true`、`putIfAbsent` 返回 `false`、`getTtl` 非 `null`。
  6. 多实例隔离与命名空间排他（含不同 Redis 主机同名 `namespace` 不冲突、进程内后端不参与排他两条放行边界）。
  7. 回源单飞：并发未命中只调用一次 `loader`；`cacheNull = false` 且装载结果为 `null` 时同样复用；胜者失败向跟随者传播；等待被中断者退化为自行回源。
  8. 名称与清理：名字含 `:` 时写路径抛 `IllegalArgumentException`、读路径静默走「不可能命中」；`clear` 清空该名称下的全部数据并精确移除该名称，`remove` 只删条目、完全不改动名称集合；`clear("user")` 不误伤前缀相近的 `users`；清除后再次写入能重新登记；派生计数器继承同源缓存的默认 TTL，省略 TTL 的重载随之生效。
  9. 保留名语义：`__CACHE_NAMES__` / `__COUNTER_NAMES__` 作为业务名与普通名称同等对待（可写入、可读、登记进名称集合、`clear` 真正生效）；Caffeine 侧的名称集合存于实例字段，容量远小于写入量时名称仍完整。
  10. `keys` 的区间语义：闭区间含两端、负索引从末尾计数、越界钳制、空列表不返回 `null`；字典序排序；`cacheName` 非法抛 `IllegalArgumentException`；glob 字符缓存名经转义后精确匹配、不误伤前缀相近名称；`clear` 后为空；哨兵条目的 key 照常返回。
- **门面完整性**：反射比对 `CacheUtil` / `CounterUtil` 与 `Cache` / `Counter` 的方法集合（`namespace()` 白名单放行，含反向用例）；并钉住 `Cache` / `Counter` 既不声明 `static` 方法也不声明常量；根包公共类型恒为 5 个。
- **字节码约束**：主 JAR 内全部 class 的 major version 必须为 52。

## 附 A：依赖与构建约束

- 不依赖 `cn.aifei:aifei-cache` 构件；唯一 aifei 依赖是框架核心 `cn.aifei:aifei`（提供 `Prop`、`@Inject`、`StrUtil`、`Plugin`）。
- 第三方依赖版本锁定：Caffeine 2.9.3（最后一个支持 JDK 8 的版本）、Jedis 8.0.1、Fury 0.10.3、aifei 1.1.1；Caffeine / Jedis / Fury 必须保持 `<optional>true</optional>`。
- 构建：JDK 8 或更高（编译目标 1.8）与 Maven；主 JAR 内 class 的 major version 必须为 52。

## 附 B：已接受限制与登记

按性质分组；每条都是**主动接受**的结果，不是待修缺陷。

| 类别 | 项 | 说明 |
| --- | --- | --- |
| 公共 API 面 | 公共面收窄为 5 类型 + 行为契约 | 实现专用能力（唯一名、哨兵编解码、物理 key 拼装）与数据格式常量一律落在 `internal`；`Cache` / `Counter` 因此不声明任何 `static` 方法与常量（发布前变更，不触及 5 类型约束） |
| 公共 API 面 | `cacheNames()` / `counterNames()` / `Counter.clear()` 在公共接口上 | 只读诊断与清理能力，属实现相关，两后端结果可能不同 |
| 行为契约 | 名字不得含 `:` | 与实例名的既有合法性规则对齐（实例名早已禁 `:`，因为它要拼进配置键）。名字与命名空间都不含 `:` 后，物理 key 拼接是单射的，`clear(name)` 的清理范围恰好等于该名字下的条目集合 —— 原先「数据清理覆盖下级名称、名称集合只精确移除」这条双粒度差异，以及 jedis 侧「拼接重名」与「清理前缀重叠」两条调用方约束一并消失。`key` 的 `:` 保持允许（不参与分级、不产生歧义），校验在 `internal.CacheValidator` 单点实施 |
| 行为契约 | 命名空间也不得含 `:` | `namespace` 是物理 key 的第一段，与名字同源。它含 `:` 时「名字不含 `:` 即单射」的推论失效 —— 实例甲 `("a","b","c:k")` 与实例乙 `("a:b","c","k")` 落到同一物理 key，而启动期排他判定按「唯一名 + 命名空间」判重、不会报冲突。违者抛 `namespace must not contain ':'`；空白判定沿用原消息 `namespace can not be blank` |
| 行为契约 | 名称集合由「只增不减」改为「`clear` 精确移除同名、`remove` 不动」 | Caffeine 侧写回保留条目、Redis 侧 `SREM`；`remove` 路径完全不触碰集合。集合不再只增不减，但仍**不反映** TTL 过期与容量淘汰；Redis 侧跨实例并发 `clear` 与写入时可能漏报 |
| 结构 | `Cache` / `Counter` 实现类各共用无行为元数据基类 | 两者都落在 `internal`、不扩张公共 API 面；`Counter` 那条相应放宽了原先「`Counter` 实现类不得引入跨实现基类」的限定 |
| 结构 | Caffeine 侧 `cacheNames()` 的载体由「缓存内保留条目」改为「实例字段」 | 名册不再与业务条目共用 `maximumSize`，也不再需要保留名守卫；`clear("__CACHE_NAMES__")` 与普通名称同等对待，两端口径一致。`CACHE_NAMES_NAME` 取值不变，用途收窄为 Redis 侧 |
| 结构 | `internal.CacheProviderRegistry` 引用后端子包 | 组合根落在此处；不影响 `optional` 隔离 |
| 数据格式 | 键空间保留名与保留前缀集中在 `internal.Config` | `CACHE_NAMES_NAME` 是缓存名集合 key 的一段、`COUNTER_NAMES_NAME` 是计数名称集合 key 的一段，故收敛到单一定义点；Caffeine 侧的两个名称集合都是实例字段，不使用保留名 |
| 数据格式 | 计数物理 key 为 `namespace:COUNTER:counterName:key` | `Config.COUNTER_KEY_PREFIX` 取值为 `COUNTER:`（自带结尾的分隔符）；1.0.0 发布后升级需走迁移 —— 旧前缀的键在 TTL 到期前既读不到、也不被 `clear(counterName)` 的扫描模式命中 |
| 数据格式 | 哨兵类型为 `internal.NullValue` | 哨兵的全限定名会写入 Fury 字节流，包名与枚举名同属数据格式，发布后不得更改 |
| 序列化 | Fury 反序列化白名单为进程级 | 底层序列化器是静态共享实例，`cache.jedis.allowedPackages` 只认根级、多实例共用；放行项只增不减 |
| 序列化 | 关闭 Fury 类注册但保留字节格式 | 反序列化管控改用白名单校验器，字节格式与含 `NullValue` 哨兵的存量数据保持兼容 |
| 序列化 | 数组值按元素类判定 | 白名单校验器按**类名字符串**判定，数组类名（`[Lpkg.Foo;` / `[[I`）不携带元素类的可判定信息。在类内放行**数组维度**（前缀 `[` 与维度无关，一条即覆盖一维与多维），元素类仍逐个走 `cache.jedis.allowedPackages`，与标量同口径；据此修正了此前「数组值写成功、读抛 `DeserializationException`」的静默损坏 |
| 装配 | 无 valueCodec / SSL 注入出口 | 唯一装配入口只接受 `Prop`，四类 JDK 或扩展点对象无法用配置表达 |
| 装配 | 配置解析在 `start()` 而非构造期 | 构造函数只保留 `Prop` 引用；配置错误的表现是启动失败 |
| 生命周期 | `start()` 失败路径不逐个关闭实例 | 依赖框架捕获异常后终止 JVM |
| 生命周期 | 进程级注册表 | `CacheManager` 与 `CacheProviderRegistry` 的映射表为 `static`，同 JVM 内插件不可重启 |
| 行为限制 | `put(key, null, ttl)` 不抛异常 | `cacheNull = false` 时不写入、保持现状，不拒绝 `null` |
| 行为限制 | 门面提供省略 TTL 的写重载 | `CacheUtil` 镜像 `put` / `putIfAbsent` / `expire` 的省略 TTL 重载，缺省取实例 `ttl()`，与接口保持一致 |
| 行为限制 | 回源单飞只覆盖单个 JVM | 跨实例不收敛：多实例部署时每个 JVM 各自装载一次。要跨实例收敛须引入分布式互斥，会牵出锁 TTL 与回源耗时的耦合，本轮未采纳 |
| 行为限制 | 单飞等待被中断即降级为自行回源 | 保证是**有界**的而非无条件：中断不是错误路径，不阻止该线程回源，只是不再复用胜者结果 |
| 行为限制 | `loader` 内不得嵌套同实例的带 `loader` 的 `get` | 登记与装载之间没有全局顺序，交叉等待会成环；单层 `loader` 是既定使用形态，不为此引入防环检测 |
| 兼容性 | Redis 计数名称登记写在更新脚本内 | 与数值创建同处一次 `eval`，不额外增加往返；代价是更新脚本成为双 key 脚本，与 Redis Cluster 不兼容（本库只用单机 `RedisClient`，无影响） |
