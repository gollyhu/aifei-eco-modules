# aifei-cache

aifei-cache 是面向 [aifei](https://aifei.cn) 应用的缓存与计数组件。业务代码只依赖两个接口，换后端只改配置、不改代码。

它支持一个应用同时持有多个缓存实例——例如「主 Redis 实例 + 本地 Caffeine 实例」：

```text
                        ┌─ 主缓存 __MAIN__  → cache.*        → @Inject / CacheUtil.use()
应用 → Cache/Counter ───┤
                        └─ 扩展实例 mem    → cache.mem.*    → CacheUtil.use("mem")
```

- **主缓存**：恒存在，实例名 `__MAIN__`，是唯一能 `@Inject` 注入的实例；也可以用 `CacheUtil.xxx()` 直接操作。
- **扩展实例**：由 `cache.names` 声明，没有 `@Inject` 入口，只能用 `CacheUtil.use("{name}").xxx()` 操作。
- 名字分三层，各管一件事：**实例名**（连哪个存储）/ **缓存名**（业务分区）/ **命名空间**（同一存储上区分不同应用）。

只有一个实例时，只需认「主缓存」这一条线，可以完全忽略扩展实例。

## 快速上手

### 第一步：加依赖

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

Caffeine、Jedis、Fury 是**可选依赖**，用哪个后端就加哪个，只用一种不会被另一种牵连。组件编译目标为 Java 8。

| 后端 | 再添加这些依赖 |
| --- | --- |
| `caffeine`（进程内） | `com.github.ben-manes.caffeine:caffeine:2.9.3` |
| `jedis`（Redis） | `redis.clients:jedis` + `org.apache.fury:fury-core`（值默认用 Fury 编解码） |

### 第二步：写配置

配置文件是 `app-config.txt`，由 aifei 的 `PropKit` 载入。只要一个实例时不用写 `cache.names`：

```properties
cache.type = jedis
cache.ttlMillis = 3600000
cache.namespace = aifei
cache.jedis.host = 127.0.0.1
cache.jedis.port = 6379
```

换成进程内缓存，只需把 `cache.type` 改成 `caffeine`；`cache.jedis.*` 留着也无妨，当前后端用不到的键不会被读取，也不会报错。

配置写错的表现是**应用启动失败**（异常从 `CachePlugin.start()` 抛出），不会带着半套配置跑起来。逐键说明见「配置参考」，扩展实例见「多实例与生命周期」。

### 第三步：装配并注入

```java
@Override
public void config(Plugins plugins) {
    plugins.add(new CachePlugin(PropKit.use("app-config.txt")));
}

@Inject
Cache cache;

@Inject
Counter counter;

cache.put("user", "42", user, 1800);                // 写入，TTL 1800 秒
User cached = cache.get("user", "42");              // 读取
long hits = counter.increase("hit", "42", 1, 300);  // 计数 +1，TTL 300 秒
```

## Cache 怎么用

```java
public interface Cache {

    // 抽象方法：TTL 一律是 Duration 类型（毫秒精度）
    <T> T get(String cacheName, String key);
    boolean exists(String cacheName, String key);
    void put(String cacheName, String key, Object value, Duration ttl);
    boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl);
    boolean expire(String cacheName, String key, Duration ttl);
    void remove(String cacheName, String key);
    void clear(String cacheName);
    Duration getTtl(String cacheName, String key);
    Set<String> cacheNames();
    Duration ttl();
    boolean cacheNull();
    String namespace();

    // default 方法：秒重载、省略 TTL 重载与 Loader 委托
    <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader);
    <T> T get(String cacheName, String key, int ttlSeconds, Supplier<T> loader);
    <T> T get(String cacheName, String key, Supplier<T> loader);                     // TTL 取实例默认
    void put(String cacheName, String key, Object value, int ttlSeconds);
    void put(String cacheName, String key, Object value);                            // TTL 取实例默认
    boolean putIfAbsent(String cacheName, String key, Object value, int ttlSeconds);
    boolean putIfAbsent(String cacheName, String key, Object value);                 // TTL 取实例默认
    boolean expire(String cacheName, String key, int ttlSeconds);
    boolean expire(String cacheName, String key);                                    // TTL 取实例默认
}
```

TTL 精度为毫秒：`int ttlSeconds` 重载只能表达整秒，需要亚秒级 TTL 时必须改用 `Duration`。

```java
cache.put("submit-lock", userId, "1", Duration.ofMillis(500));   // 500 毫秒，只能用 Duration
cache.put("sms-code", phone, code, 300);                         // 300 秒，int 重载即可
```

下面四种场景覆盖了绝大多数用法。

### 固定 TTL / 绝对过期

缓存项在写入或首次创建时确定过期时间，之后读取、`exists` 或 `putIfAbsent` 命中都**不会**延长有效期。适合验证码、一次性 token、提交幂等标记、发送冷却等必须在固定时间后自然失效的场景。

```java
boolean allowed = cache.putIfAbsent("sms-cooldown", phone, "1", 60);
if (!allowed) {
    throw new TooManyRequestsException();
}
```

带 `loader` 的 `get` 同样落在固定 TTL 上：命中时直接返回、不调用 `loader`，并自动具备**回源单飞（抗缓存击穿）**能力（规则见「接口通用规则」）。

```java
UserProfile profile = cache.get("user-profile", userId, 1800, () -> userService.loadProfile(userId));
```

### 访问续期 / 闲置过期

每次业务确认缓存项仍然有效后，显式调用 `expire` 把剩余有效期重设为指定 TTL。适合登录 session、在线用户上下文、编辑草稿等「最后一次访问后 N 秒过期」的场景。

```java
Session session = cache.get("login-session", sessionId);
if (session == null || !session.isValid()) {
    throw new UnauthorizedException();
}
cache.expire("login-session", sessionId, 1800);
```

`expire` 只续期**已存在且未过期**的缓存项，不读取、不修改缓存值；不存在或已过期返回 `false`，**不复活过期项**。

### 空值缓存（`cacheNull`）

`cacheNull = true` 时，写入 `null` 会把「查不到」这一否定结果也缓存下来，避免同一无效 key 反复穿透到下游。

```java
// cacheNull = true 时
cache.put("user", "missing-id", null, 60);
cache.get("user", "missing-id");       // null
cache.exists("user", "missing-id");    // true
```

### 自定义类型的 Redis 白名单（`cache.jedis.allowedPackages`）

jedis 后端的值经 Fury 序列化，反序列化按白名单放行：**JDK 内置类型**（String、数值、Map、List、Date 等）自动放行，**业务自定义值类型必须显式声明所在包**，否则写入时抛 Fury 的 `InsecureException`。

```properties
# 逗号分隔；com.foo 与 com.foo.* 等价
cache.jedis.allowedPackages = com.example.order.model,com.example.user.dto
```

- **只认根级配置**，同一进程内所有实例共用一份策略，放行项只增不减。
- **只作用于 jedis 后端**：caffeine 后端把值作为内存对象直接存放，不经序列化，无需声明。
- **数组值与标量同口径**：放行与否只看**元素类**，与数组层数无关。`String[]`、`int[][]` 无需声明；`Order[]`、`Order[][]` 须声明 `Order` 所在包。
- 未声明的类型**不会静默失败**：`put` / `putIfAbsent` / `get(..., loader)` 在写入时抛异常，错误信息含被拒的类名。

## Counter 怎么用

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

计数项不存在或已过期时，增减方法一律按当前值 `0` 创建新计数项。命名、TTL、返回值与异常契约见「接口通用规则」。

需要精确计数时请用 `Counter`，**不要**用 `Cache` 的 `get + put` 自行实现原子计数。

两种 TTL 语义按业务选：

**固定窗口** —— 计数项第一次创建时确定过期时间，后续命中只更新数值，不延长窗口。适合固定窗口限流、固定周期配额、验证码发送次数。

```java
long requests = counter.increase("api-request", userId, 1, 60);
if (requests > 100) {
    throw new TooManyRequestsException();
}
```

按自然周期统计时，推荐把周期放进 `counterName`，用 TTL 控制历史计数自动清理：

```java
String day = LocalDate.now().toString();
long views = counter.increase("article-views-" + day, articleId, 1, 172800);
```

**闲置过期** —— 每次更新都把 TTL 刷新为传入值。适合登录失败次数、会话内操作次数、临时冷却计数。

```java
long failures = counter.increaseAndRefreshTtl("login-fail", userId, 1, 900);
if (failures >= 5) {
    accountService.lockTemporarily(userId);
}
```

这种语义常被称为「滑动过期」，但**不等价于精确的最近 N 秒滑动窗口统计**：用户持续活跃时计数会持续续期并继续累计。需要严格统计「最近 N 秒内发生了多少次」时，应使用专门的时间桶或时间序列算法。

### 计数名称与清理

`clear(counterName)` 清空该名称下的全部计数项，并把 `counterName` 本身从名称集合中移除：

```java
Set<String> names = CounterUtil.use("mem").counterNames();

CounterUtil.clear("api-request");        // 清掉 api-request 下的全部计数项，并把它移出名称集合
```

`remove` 只删单个计数项，**不改变** `counterNames()`；可见范围规则见「接口通用规则」。

## 配置参考

### 主缓存键（`cache.*`）

主缓存的键按**作用类别**分成三组，键名分段互不重叠：

| 类别 | 键前缀 | 作用 |
| --- | --- | --- |
| **通用键** | `cache.*` | 与后端无关：后端类型、默认 TTL、`cacheNull`、命名空间、扩展实例名单 |
| **caffeine 专有键** | `cache.caffeine.*` | 只被 caffeine 后端读取 |
| **jedis 专有键** | `cache.jedis.*` | 只被 jedis 后端读取 |

三组里**只有 `cache.type` 必填**，其余全部可省略，省略即取「默认」列给出的值（标注为「空」的表示沿用底层默认，不主动覆盖）。

#### 通用键

对可选键，**省略、写成空值、只写空白三者等价**，一律按未配置处理并采用缺省值；`cache.type` 则缺失与空白都算错。TTL 键名自带单位：默认 TTL 是 `cache.ttlMillis`（扩展实例 `cache.{name}.ttlMillis`），**没有** `cache.ttl` 这个键。

| 键 | 必填性 / 默认 | 说明 |
| --- | --- | --- |
| `cache.type` | **必填** | `jedis` \| `caffeine`，严格小写，无默认值 |
| `cache.ttlMillis` | 可选，`60000` | 默认 TTL，**单位毫秒**，范围 1 ~ 2147483647000 |
| `cache.cacheNull` | 可选，`false` | 是否缓存 `null` 值 |
| `cache.namespace` | 可选，`aifei` | 存储中所有键的公共前缀；**不得含 `:`** |
| `cache.names` | 可选，空 | 本应用要额外创建哪些实例：逗号分隔的实例名列表（例如 `mem,log`），每写一个名字就要多配一段 `cache.{name}.*`，不配置即只有主缓存。每个名字不得包含 `.` / `=` / `:` 或空白，不得用保留词（`type` / `ttlMillis` / `cacheNull` / `namespace` / `names` / `main` / `allowedPackages`），不得为 `__MAIN__`，不得重名 |

校验不通过一律抛 `IllegalArgumentException`，消息格式固定为 `cache config invalid: <键>=<值>, reason=<原因>`。

#### caffeine 专有键

| 键 | 必填性 / 默认 | 说明 |
| --- | --- | --- |
| `cache.caffeine.maxSize` | 可选，`10000` | 本实例的最大条目数；**必须大于 0**，否则启动失败 |

#### jedis 专有键

| 键 | 必填性 / 默认 | 说明 |
| --- | --- | --- |
| `cache.jedis.allowedPackages` | 空 | 允许反序列化的业务包名，逗号分隔；**只认根级**，不加实例前缀；出现空项（如 `a,,b`）即启动失败 |
| `cache.jedis.uri` | — | 用一个 URI 提供连接信息。**配置后 `cache.jedis.host` / `cache.jedis.port` 被忽略**；显式配置的 `database` / `user` / `password` 仍优先于 URI 中的对应项 |
| `cache.jedis.host` / `cache.jedis.port` | `127.0.0.1` / `6379` | 连接地址；`port` 范围 1 ~ 65535 |
| `cache.jedis.user` / `cache.jedis.password` | 空 | ACL 凭据；**空值按未配置处理** |
| `cache.jedis.database` | `0`（沿用底层默认） | 库号，≥ 0 |
| `cache.jedis.ssl` / `cache.jedis.resp3` / `cache.jedis.clientName` | `false` / `false` / 空 | TLS、RESP3 协议与客户端名 |
| `cache.jedis.timeoutMillis` | 空 | 同时设置连接超时与 socket 读写超时（毫秒），> 0 |
| `cache.jedis.connectionTimeoutMillis` / `socketTimeoutMillis` / `blockingSocketTimeoutMillis` | 空 | 分别覆盖连接、读写、阻塞命令超时（毫秒）；前两者 > 0，`blockingSocketTimeoutMillis` ≥ 0（`0` 表示不超时） |
| `cache.jedis.maxTotal` / `maxIdle` / `minIdle` | 空（沿用默认） | 连接池容量；`maxTotal` 可 -1 表示不限 |
| `cache.jedis.maxWaitMillis` | `1500` | 连接池等待上限，-1 表示一直等待 |
| `cache.jedis.blockWhenExhausted` / `lifo` / `fairness` | 空 | 连接池行为开关 |
| `cache.jedis.testOnCreate` / `testOnBorrow` / `testOnReturn` / `testWhileIdle` | 空 | 连接校验开关 |
| `cache.jedis.timeBetweenEvictionRunsMillis` / `minEvictableIdleTimeMillis` / `softMinEvictableIdleTimeMillis` | 空 | 空闲连接扫描，≥ -1 |
| `cache.jedis.numTestsPerEvictionRun` | 空 | -1 或大于 0 |
| `cache.jedis.jmxEnabled` / `jmxNamePrefix` / `jmxNameBase` | 空 | 连接池 JMX 参数 |

未显式配置的键一律沿用底层默认值；`sslSocketFactory` / `sslParameters` / `hostnameVerifier` / `valueCodec` **没有配置出口**，不支持自定义。

### 扩展实例键（`cache.names` + `cache.{name}.*`）

在 `cache.names` 里声明有哪些扩展实例，其余键一律带 `cache.{name}.` 前缀、按实例分别配置，分组与主缓存一致。

| 键 | 必填性 / 默认 | 说明 |
| --- | --- | --- |
| `cache.{name}.type` | **必填** | `jedis` \| `caffeine`；**缺失即启动失败，不继承主缓存** |
| `cache.{name}.ttlMillis` | 可选，继承主缓存 | 缺省继承 `cache.ttlMillis`；主缓存也未配时为 `60000` |
| `cache.{name}.cacheNull` | 可选，继承主缓存 | 缺省继承 `cache.cacheNull`；主缓存也未配时为 `false` |
| `cache.{name}.namespace` | 可选，`aifei` | **不继承主缓存**；缺省与主缓存相同会导致互相覆盖，应显式配置且互不相同；**不得含 `:`** |
| `cache.{name}.caffeine.maxSize` | 可选，`10000` | **必须大于 0**，否则启动失败 |
| `cache.{name}.jedis.<键后缀>` | 可选 | 主缓存表里每个 jedis 键都可加该前缀单独配置；未配置时取该键自身的默认值，**不继承主缓存里实际配置的值**。`cache.jedis.allowedPackages` 例外，它只认根级、不加实例前缀 |

`ttlMillis` 与 `cacheNull` 继承主缓存，`type` 与 `namespace` 不继承；其余键的语义与取值边界与主缓存同名键一致。

### 配置示例（主缓存 + 一个本地实例）

```properties
# 扩展缓存实例名称列表（逗号分隔，不得出现空项）
cache.names = mem

# 【主缓存】Redis
cache.type = jedis
cache.cacheNull = false
cache.ttlMillis = 3600000
cache.namespace = aifei
cache.jedis.allowedPackages = cn.hg,com.hg
cache.jedis.host = 127.0.0.1
cache.jedis.port = 6379
cache.jedis.user =
cache.jedis.password =
cache.jedis.database = 0
cache.jedis.timeoutMillis = 2000

# 【mem 实例】进程内本地缓存
cache.mem.type = caffeine
cache.mem.ttlMillis = 600000
cache.mem.namespace = mem
cache.mem.caffeine.maxSize = 50000
```

### 配置规则要点

- **数值写法**：配置是 `java.util.Properties` 的字面字符串，**不支持 `60_000` 或 `60 * 1000`**，只能写 `60000`。
- **注释**：只支持整行注释（行首 `#` 或 `!`），不支持行内注释。
- **profile 覆盖**：覆盖文件为 `app-config-{profile}.txt`，命令行用 `--aifei.profiles.active=<profile>` 指定；覆盖粒度为**按键整值替换**，`cache.names` 必须写全量列表。

## 多实例与生命周期

主缓存与主计数器直接注入；扩展实例**没有 `@Inject` 入口**，按实例名从静态门面取：

```java
Cache local = CacheUtil.use("mem");
local.put("user", "42", user, 300);

Counter localCounter = CounterUtil.use("mem");
long hits = localCounter.increase("hit", "42", 1, 300);
```

`use(name)` 返回注册表中的同一个实例对象，调用方可以安全缓存引用。

`CacheUtil` / `CounterUtil` 同时提供 `Cache` / `Counter` 业务方法的**静态镜像**（参数逐一对应，静态方法一律作用于主缓存；`namespace()` 不提供镜像，需要时用 `CacheUtil.use().namespace()` 取实例上的值）：

```java
CacheUtil.put("user", "42", user, 1800);     // 等价于 cache.put(...)
CacheUtil.use("mem").put("user", "42", user, 300);
```

`cacheNames()` 返回当前实例曾被写入过的缓存名集合，用于运维自查，规则见「接口通用规则」：

```java
Set<String> names = CacheUtil.use("mem").cacheNames();
```

## 接口通用规则

下面是 `Cache` 与 `Counter` **所有方法共用**的规则；两个接口各自的专有差异已在对应小节说明，这里不再重复。

### 命名

- `cacheName` / `counterName` 与 `key` 都不能为空白，两者一起定位一条缓存项或计数项。
- `cacheName` / `counterName` **不能含 `:`**：写路径抛 `IllegalArgumentException`，读路径按未命中处理；`key` 不受此限，可以自由用冒号分级（如 `profile:42`）。

### 有效期（TTL）

- **每次写入与续期都必须给出 TTL**，合法范围为 **1 毫秒 ~ `Integer.MAX_VALUE` 秒**（约 68 年），含两端。
- **超限不截断、不缩短**，直接抛 `IllegalArgumentException`。
- 精度为毫秒：`int ttlSeconds` 重载只能表达整秒，需要亚秒级时用 `Duration`。
- 省略 TTL 的重载取该实例的默认 TTL（配置项 `cache.ttlMillis`）。
- `Counter` 的步长 `step` 必须大于 0。

### 返回值

| 情况 | 结果 |
| --- | --- |
| 普通 `get`、`getTtl` 未命中 / 已过期 / 参数非法 | `null` |
| `getTtl` 命中**无过期时间**的键（仅 jedis 后端） | 返回 `Integer.MAX_VALUE` 秒对应的 `Duration` |
| `Counter.get` 计数项未命中或已过期 | `null`（**不返回 `0`**） |
| `exists`（`Cache`）参数非法 | `false` |
| `remove` 参数非法 | 不执行任何操作 |
| `putIfAbsent` 命中 | `false`，不覆盖原值、不重置原 TTL |
| `expire` 目标不存在或已过期 | `false`，**不复活过期项** |
| `keys` 空缓存、筛选后无条目或 `offset` 越界 | 空列表（**不返回 `null`**） |
| `Counter` 的增减方法遇到未命中 | 按当前值 `0` 创建新计数项，并使用传入的 TTL |

### 异常契约

| 场景 | 抛出 |
| --- | --- |
| 写路径参数校验失败：`put` / `putIfAbsent` / `expire` / `clear` / 带 `loader` 的 `get` / `Counter` 四种增减 | `IllegalArgumentException` |
| `keys` 的 `cacheName` 空白或含 `:`，或 `offset < 0` / `limit <= 0` / `matchType` 为 `null` | `IllegalArgumentException` |
| 读路径参数非法：`get` / `exists` / `remove` / `getTtl` | **不抛**，走「不可能命中」路径 |
| 计数结果超出 `long` 范围 | `ArithmeticException`，且不改变数值与 TTL |
| `Counter.get` 读到外部直接写入、无 TTL 或非整数的计数项 | `IllegalStateException`（**仅 jedis 后端**） |
| `CacheUtil.use()` / `CounterUtil.use()` 时插件未启动或已停止 | `IllegalStateException` |
| `CacheUtil.use(name)` / `CounterUtil.use(name)` 实例名为空、含非法字符或未注册 | `IllegalArgumentException`（**先生命周期、后参数**） |

### `null` 与 `cacheNull`

- `cacheNull = true` 时，写入 `null` 会被缓存下来，对调用方不可见：`get` 仍返回 `null`，但 `exists` 返回 `true`、`putIfAbsent` 视为已存在、`getTtl` 返回其剩余有效期。于是「查不到」这一否定结果也能被缓存，避免同一无效 key 反复穿透到下游。
- `cacheNull = false`（默认）时，`put(..., null, ...)` **不写入、不抛异常，也不清掉该键上原有的值**（`putIfAbsent(..., null, ...)` 返回 `false`）。要把某个 key 置空请用 `remove`。
- 因此**判断某项是否已缓存只能用 `exists`**，不能用 `get(cacheName, key) != null` 代替。

### 带 `loader` 的读取

- 未命中时调用 `loader` 并按传入 TTL 写入；`loader` 返回 `null` 时是否写入由 `cacheNull` 决定（同上）。
- **命中否定结果时直接返回、不回源**：`cacheNull = true` 时缓存的 `null` 会让 `get` 返回 `null`、不调用 `loader`，也不刷新该否定结果的有效期。
- **并发未命中只装载一次（回源单飞 / 抗缓存击穿）**：同一 JVM 内、同一缓存项的并发未命中最多调用一次 `loader`，其余线程直接复用这次装载的结果（结果为 `null` 时同样复用），因此热点 key 到期的一瞬间不会把并发流量整体放大到下游。带 `loader` 的 `get` 自动具备，不需要额外配置。
- **只覆盖单个 JVM**：多实例部署时每个实例各自装载一次，跨实例不收敛。
- **`loader` 内不要嵌套**：不要在 `loader` 里对**同一个缓存实例**的其它 key 再调用带 `loader` 的 `get`，两层交叉等待可能互相阻塞。
- **与 `cacheNull` 无关**：复用的是本次装载的结果本身，不是缓存里的内容，所以 `cacheNull = false` 时也不会重复回源。
- **与不带 `loader` 的 `get` 的差别**：参数非法或 `loader` 为 `null` 时它**抛 `IllegalArgumentException`** 而不是返回 `null`，且不会调用 `loader`；校验顺序为 `loader` → 名字 → `key` → TTL。

### 名称集合与 `clear`

职责划分：**`clear` 管名称与清理，`remove` 只管单条数据，两者互不影响。**

- `cacheNames()` / `counterNames()` 返回**曾被写入（创建）过的名称集合**。`clear("user")` 从中**精确移除** `user` 这一个名称；`remove` 只删单个条目，**完全不改动名称集合**。
- **数据清理与名称移除范围一致**：`clear("user")` 清空 `user` 下的全部数据并把 `user` 移出集合。名字不得含 `:`，因此不存在「下级名称」，也不会出现「只在集合里、已经查不到数据」的名称。但它仍是「写过、且未被同名 `clear` 清过」的登记，**不是「当前有数据的名称清单」**。
- **自然过期与容量淘汰不移除名称**：条目 TTL 到期或被淘汰后，名称仍留在集合里。
- 它的**可见范围随后端而变**：caffeine 侧是本进程，jedis 侧在同一 Redis 上的所有应用实例之间共享，**调用方不能依赖它在两个后端给出相同结果**。jedis 侧还需注意：跨实例并发执行 `clear` 与写入时，名称集合**可能漏掉仍在使用的名称**。
- `clear("user")` 的数据清理**非原子、尽力清理**，执行期间可能读到部分已删除的状态。
- 被移除的名称在**下次写入时重新登记**，集合随时可增可减。

### 键分页与筛选查询（仅 `Cache`）

- `keys(cacheName, offset, limit, pattern, matchType)` 返回该缓存名下**按业务 key 字典序升序**排列、经 `pattern` 筛选后的分页结果，分页结果因此可复现；两后端语义一致。
- **分页为「跳过 + 取上限」**：`offset` 是跳过的键条数（从 0 开始）、`limit` 是返回条数上限；越界自动截断（`limit` 超过剩余条数按剩余条数返回、`offset` 不小于总条数返回**空列表**，不返回 `null`）。
- **分页参数非法抛异常**：`offset < 0` 或 `limit <= 0` 抛 `IllegalArgumentException`。
- **筛选先于排序与分页**，匹配基于业务 key 的字符串形态：`pattern` 为 `null` 或空串时**不筛选**；否则按 `CacheKeyMatchType` 的 `EQUALS`（完全相等）/ `CONTAINS`（包含，省略重载用的默认值）/ `STARTS_WITH`（前缀）/ `ENDS_WITH`（后缀）判定，`matchType` 为 `null` 抛 `IllegalArgumentException`。省略 `pattern` 与 `matchType` 的 `keys(cacheName, offset, limit)` 等价于不筛选。
- **弱一致快照**：扫描期间的并发写入、过期或容量淘汰可能使相邻两次分页重叠或遗漏，属尽力而为。
- 键枚举只看键是否存在：`cacheNull = true` 写入的否定结果（哨兵）条目，其 key 照常返回。
- **开销随后端与键量而变**：jedis 侧对物理 key 做 `SCAN` 全量收集（按 `matchType` 下推 glob 缩小收集范围）后再筛选与分页，键量大时开销显著，不适合高频调用；caffeine 侧遍历进程内条目。

### 命名责任

后端不同，定位一条缓存项的方式也不同。**jedis 后端**把 `namespace`、`cacheName`、`key` 拼成存储中的键；**caffeine 后端**按这三个值分别匹配。**命名空间与名字都不得含 `:`**保证了两者结果一致，唯一需要调用方自行回避的是：

- **命名空间重复：** 同一应用内所有实例（含主缓存）的 `namespace` 必须互不相同，否则缓存与计数互相覆盖。**库只在启动期校验 jedis 后端**（它知道自己是哪个存储），并且**双方都参与校验**才会拦截：冲突抛 `IllegalArgumentException`（消息含 `duplicate key space`）。两种情况不会报错，需自行回避：指向不同 Redis 服务器的两个实例，`namespace` 相同不冲突；进程内（caffeine）实例各自独占存储、不参与校验，与它重名也不报错。

该限制不在命名空间隔离与实现可替换的保证范围内，属已接受的设计限制。

## 附：三种「失效」不是一回事

**「不存在的 key」与「到期的热点 key」成因不同，手段也不同**：

| 现象 | 成因 | 手段 |
| --- | --- | --- |
| **缓存穿透** | 同一个**不存在的** key 被反复查询 | 否定结果缓存（`cacheNull = true` + `put(..., null, ...)`），或改用会随数据出现而失效的 key |
| **缓存击穿** | **单个热点** key 到期瞬间，并发未命中把流量整体放大到下游 | 带 `loader` 的 `get`，同一 JVM 内自动合并为一次回源 |
| **缓存雪崩** | **大批** key 在同一时刻到期 | 给 TTL 加随机抖动，错开各自的到期时刻 |

「变换 key」（把 key 写成带版本号或随机后缀的形式）能绕开陈旧数据，但会**放弃否定结果缓存**：key 一变，此前缓存的值就都不再可见。
