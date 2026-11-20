# aifei-id

## 1. 定位与场景

业务需要「下单号」「流水号」「主键」这类**全局不重复、还能按大小排序**的整数 ID，但把雪花算法抄来抄去、号段方案每次重写，是每个项目都要交一次学费的事。`aifei-id` 把两代生成策略做成开箱可用的 SDK：

| 能力 | 适合什么 |
| --- | --- |
| **雪花**（`1` 符号位 + `41` 毫秒 + `10` workerId + `12` 序列） | 单机每毫秒 4096 个、跨节点不重号、ID 大小即时间先后；默认路径，零外部依赖 |
| **号段**（从共享存储一次领走区间） | 想降低对系统时钟的依赖；每 1000 个 ID 一次存储往返，重新加载时直接接着花用 |

**适合**：aifei 应用（Java 8+）要在进程内生成数字 ID，并希望「换实现只改配置、业务代码不动」。
**不适合**：UUID / 短链 / 业务前缀编码（不在本模块定位）；指标上报、审计、埋点；ID 状态查询接口（模块不提供，运维看启动日志即可）。

## 2. 5 分钟快速开始

加依赖（消费方自带 aifei 框架内核 `cn.aifei:aifei-all`，本模块不传递）：

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-id</artifactId>
    <version>1.0.0</version>
</dependency>
```

配置一个节点编号（`app-config.txt`）：

```properties
id.workerId=1
```

装配一行（写在应用自己的 `AppConfig` 里）：

```java
public class OrderAppConfig implements AifeiConfig<In, Out> {
    @Override
    public void config(Plugins plugins) {
        // 只引 aifei-id 一个 jar 就能跑：默认雪花 + 静态 workerId
        plugins.add(new IdPlugin(prop));
    }
}
```

取号一行：

```java
public class OrderService {
    public void placeOrder(String skuId) {
        long orderId = IdKit.nextId();          // 恒正、单实例严格递增
        String orderNo = IdKit.nextIdStr();     // 给前端 / JSON 用字符串
        // ...落库、返回...
    }
}
```

跑通后即可观测：每次调用得到一个 19 位以内的十进制数字，相邻两次调用递增；启动日志里会出现一行 `aifei-id started, strategy: snowflake, workerSource: config, workerId: 1`。

> 忘了装配就在业务里调用 `IdKit`？会立刻抛 `IllegalStateException`（消息见 §7），不会静默返回 0 —— 让问题停在第一次调用，而不是留到数据对账。

## 3. 常用用法

### 3.1 取用方式（二选一）

不注入直接用（推荐，业务零样板代码）：

```java
long id = IdKit.nextId();
String idStr = IdKit.nextIdStr();   // 等价 Long.toString(nextId())
```

需要注入时用接口（Service 里注入，单测里换成假实现）：

```java
public class OrderService {
    @Inject
    private IdGenerator idGenerator;    // 装配后由框架注入

    public long nextOrderId() {
        return idGenerator.nextId();
    }
}
```

两种方式取到的是**同一个**生成器：先装配（`IdPlugin.start`）再取用，停机后 `IdKit` 立刻不可用。

### 3.2 雪花：只需配 workerId

| 配置 | 说明 | 默认 |
| --- | --- | --- |
| `id.strategy` | `snowflake` / `segment` | `snowflake` |
| `id.worker.source` | `config` / `cache` / `lock` | `config` |
| `id.workerId` | `0..1023`，`source=config` 时**必填** | — |

单机或实例数固定的小集群：**每个实例配不同的 `id.workerId`**，其余不用管。

### 3.3 号段：只需配策略

```properties
id.strategy=segment
```

默认号段长度 1000、号段键 `default`、号段落在**主缓存**里（需要已装配 `CachePlugin`）。业务代码完全不变，仍是 `IdKit.nextId()`。

### 3.4 常见配置总览

| 配置键 | 必填 | 默认值 | 规则 | 生效范围 |
| --- | --- | --- | --- | --- |
| `id.strategy` | 否 | `snowflake` | `snowflake` / `segment` | 全局 |
| `id.worker.source` | 否 | `config` | `config` / `cache` / `lock` | 仅雪花 |
| `id.workerId` | `source=config` 时必填 | — | `0..1023` | 仅雪花 + `config` |
| `id.worker.leaseMillis` | 否 | `30000` | `≥ 1000` | 仅雪花 + `lock` |
| `id.segment.step` | 否 | `1000` | `1..1000000` | 仅号段 |
| `id.segment.key` | 否 | `default` | 非空白、无空白字符、`≤ 64` 字符 | 仅号段 |
| `id.segment.store` | 否 | `cache` | `cache` / `local` | 仅号段 |
| `id.state.ttlMillis` | 否 | `31536000000`（365 天） | `60000 .. 2147483647000` | 号段取段 + 雪花 `cache` 取号 |

与生效策略无关的键**不读、不校验**：切换策略时残留的键不会导致启动失败。

## 4. 深入使用

### 场景一：多实例部署，订单号要全局不重号

业务问题：服务横向扩到 8 个实例，每个实例都要发号，且订单号在全局唯一。

做法：给每个实例分配**互不相同**的 `id.workerId`，从启动参数或环境变量注入：

```properties
# app-config.txt（每台机器不同）
id.strategy=snowflake
id.worker.source=config
id.workerId=${INSTANCE_INDEX}
```

```java
public class DemoAppConfig implements AifeiConfig<In, Out> {
    @Override
    public void config(Plugins plugins) {
        Prop prop = new Prop();
        prop.getProperties().setProperty("id.workerId", System.getenv("INSTANCE_INDEX"));
        plugins.add(new IdPlugin(prop));
    }
}
```

业务代码不变（仍是 `IdKit.nextId()`）。

会影响这段业务的语义：**模块无法检测重复 workerId**（雪花在 `config` 模式没有任何共享状态），两个实例配同一个号必然重号且启动期不报错。实例数会动态变化时，改用场景二。

### 场景二：实例数会扩缩，不想手工编号

业务问题：容器编排自动扩缩容，没人愿意维护一张「实例 → 编号」的表。

配置：

```properties
id.strategy=snowflake
id.worker.source=lock
id.worker.leaseMillis=30000
```

装配顺序：`CachePlugin` → `LockPlugin` → `IdPlugin`（`plugins.add` 的顺序就是启动顺序）。

```java
@Override
public void config(Plugins plugins) {
    plugins.add(new CachePlugin(cacheProp));   // 共享缓存后端（如 Redis）
    plugins.add(new LockPlugin(lockProp));     // 分布式锁
    plugins.add(new IdPlugin(prop));           // 按 0..1023 升序抢占一个号并续期
}
```

业务代码不变。

会影响这段业务的语义：编号是**抢来的租约**，进程存活期长期持有、后台每 `max(1000ms, 租约/3)` 续期一次；`0..1023` 全被占用时**启动即失败**（不排队等待）；续期一旦失败（锁丢失），此后每次取号都抛 `IdException`，**不会自愈**，请重启该实例并排查锁后端。

### 场景三：只有 Redis 没有分布式锁模块

业务问题：架构里统一用 Redis，但没引 `aifei-lock`，希望尽量少引一个模块。

配置：

```properties
id.strategy=snowflake
id.worker.source=cache
id.state.ttlMillis=31536000000
```

装配顺序：`CachePlugin` → `IdPlugin`。业务代码不变。

会影响这段业务的语义：编号由共享计数器 `+1` 后对 1024 取模得到，**没有租约、没有续期**。同一个 TTL 窗口内累计冷启动超过 1024 次时会绕回已占用的编号（见 §6 部署警告）。

### 场景四：ID 不要求按时间排序，想少依赖系统时钟

业务问题：数据要分库分表，只要 ID 唯一且单调，不要求能反推出时间，反而担心时钟漂移影响发号。

配置：

```properties
id.strategy=segment
id.segment.step=1000
id.segment.key=orders
```

装配顺序：`CachePlugin`（共享后端）→ `IdPlugin`。业务代码不变。

会影响这段业务的语义：号段从共享存储一次领 1000 个，进程内花完再领下一段；**跨实例会有空洞**（别的实例领走的区间），进程崩溃会丢掉当前段剩余号。不同 `id.segment.key` 各自一条序列，**跨 key 不保证唯一**。

### 场景五：前端要展示订单号，不能丢精度

业务问题：19 位十进制超出 JS 的 `Number.MAX_SAFE_INTEGER`（2^53），前端直接用数字会静默丢精度，展示成末位被抹平的错号。

做法：JSON 里一律给字符串。

```java
public class OrderService {
    public Map<String, Object> detail(long orderId) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", IdKit.nextIdStr());     // 字符串形态，前端原样展示
        return data;
    }
}
```

会影响这段业务的语义：字符串形态就是同一个 `long` 的十进制表示，不改变位布局，也不改变唯一性——库里的列仍建议用 `BIGINT`。

### 场景六：本地开发与演示（离线、单实例）

业务问题：开发机上没有 Redis，只想把流程跑通。

配置：

```properties
id.strategy=segment
id.segment.store=local
id.segment.step=200
```

业务代码不变。

会影响这段业务的语义：**号段计数器退化到进程内**，重启从 1 重新开始，必然与重启前发出的号重叠。只用于单实例与开发测试；多实例或需要跨重启唯一时必须用 `store=cache`。

## 5. 高级使用

### 5.1 在自己的单测里替换生成器

`IdGenerator` 是极简接口（只有一个 `nextId()`），业务可以在自己的测试里直接提供假实现，不必启动插件：

```java
public class OrderServiceTest {
    @Test
    public void usesInjectedGenerator() {
        OrderService service = new OrderService();
        service.setIdGenerator(new IdGenerator() {
            @Override
            public long nextId() {
                return 1001L;
            }
        });
        assertEquals(1001L, service.nextOrderId());
    }
}
```

### 5.2 多个 ID 域（订单号 / 流水号各一条序列）

号段模式下用 `id.segment.key` 区分域。一个进程只装配一个生成器，因此需要多域时按部署拆分为多个应用，或每个应用只保留一个域：

```properties
id.strategy=segment
id.segment.key=payment-flow
```

雪花模式下不需要多域：同一 workerId 的空间内全局唯一，直接一个序列够用。

### 5.3 启停顺序与生命周期

- `plugins.add` 顺序即启动顺序：`cache` 来源需晚于 `CachePlugin`，`lock` 来源需晚于 `CachePlugin` 与 `LockPlugin`；顺序不对会在启动期抛 `IdException` 并说明哪个模块没就绪。
- `start()` 幂等：重复调用直接返回，不会装配第二套。
- `stop()` 清空可取用的生成器并释放 workerId 租约（中断续期线程、释放锁）；重复或未启动调用都是空操作。
- 装配失败（配置非法 / 依赖缺失 / 号段后端不是共享存储 / workerId 全被占用）时会回滚到「未装配」，不会留下半个生成器。

### 5.4 组合生态模块做可观测

模块自身只记录一条启动 INFO（策略 + workerId），不做指标上报。需要 QPS、失败率、号段切换次数等观测时，在业务侧包装 `IdGenerator` 或对 `IdKit` 调用埋点，接入消费方已有的指标 / 监控系统（如 Micrometer、日志埋点）自行实现——ID 生成在整条链路上本就不是瓶颈。

## 6. 注意事项与技术难点

**部署警告（上线前逐条确认）：**

- **`config` 模式无法检测重复 workerId**：模块在该模式没有任何共享状态，两个实例配同一个 `id.workerId` 必然重号，且**启动期不会报错**。多实例场景必须保证编号互不相同，或改用 `cache` / `lock` 来源。
- **`cache` 模式取模会回绕**：`workerId = (计数 - 1) % 1024`，同一 TTL 窗口（默认 365 天）内累计冷启动超过 1024 次就会与仍在运行的实例撞号。用启动日志里记录的 workerId 做对账，扩容频繁时优先用 `lock`。
- **`local` 号段重启必重号**：进程重启后计数从 0 开始，首个号段仍是 `[1, step]`，与重启前的号重叠。只允许单实例 / 开发测试使用。
- **停机后 `@Inject IdGenerator` 仍留在容器里**：框架没有单例注销 API，`stop()` 只让 `IdKit` 不可用（抛 `IllegalStateException`）。停机后请改用 `IdKit` 判断可用性，不要继续持有注入的引用发号。
- **号段 TTL 默认 365 天，闲置过期后从「静默重号」变成「显式失败」**：计数器过期归零后，第一次取段会因「新段起点不大于上次段末端」抛 `IdException`（消息 I）。处置：换一个 `id.segment.key`，或把该计数器手工置回高位；活跃进程每次取段都会续期，不会过期。

**其它技术要点：**

- **运行期永不降级**：时钟回拨超阈、号段存储失败、租约丢失都直接抛 `IdException`，不返回 0、不回退本地、不静默重试。继续发号等于重号，失败是唯一正确的行为。
- **时钟回拨策略**：回拨 ≤ 5ms（NTP 微调、GC 抖动量级）在内部等时钟追上后按同一毫秒继续（序列号 +1，不重号）；> 5ms 视为系统性时钟故障，直接失败且**不换 workerId**。
- **单节点每毫秒上限 4096**：超出即等下一毫秒；41 位毫秒可用到约 2093 年，越界直接失败。位布局与纪元（2024-01-01Z）是**冻结数据格式**，不提供配置项，升级不会改变已落库 ID 的解释。
- **可选依赖是「要么能用要么启动失败」，不是功能降级**：不引 `aifei-cache` 就用不了 `cache` 来源与号段 `cache` 存储；不引 `aifei-lock` 就用不了 `lock` 来源。缺失时启动期抛出带具体坐标的 `IdException`，不会退回本地实现。
- **号段后端必须是共享存储**：本地缓存后端（Caffeine）不支持条件操作，多实例会各自从 1 开始，因此号段 `store=cache` 在启动期即被拒绝。
- **不支持命名实例**：一个进程只应有一个 workerId，一个 workerId 只能对应一个生成器；两个生成器共享一个号必然重号。
- **没有状态查询 API**：生效策略与 workerId 只出现在启动日志里；这是刻意的，避免为运维定位新增公共类型。
- **零第三方依赖**：`aifei-all`（`provided`）+ `aifei-cache` / `aifei-lock`（`optional`）+ `junit`（`test`），不引入任何日志绑定与 DB 驱动。

## 7. 参考

### 7.1 异常对照（消息逐字，便于按日志定位）

| 场景 | 异常类型 | 消息 |
| --- | --- | --- |
| `new IdPlugin(null)` | `IllegalArgumentException` | `prop can not be null` |
| 未装配 / 已停机调用 `IdKit` | `IllegalStateException` | `IdGenerator not installed, add IdPlugin to AppConfig.config(Plugins) before using IdKit` |
| 时钟回拨超 5ms | `IdException` | `clock moved backwards beyond tolerance, lastTimestamp: {last}, now: {now}, backwards: {back}ms, tolerance: 5ms` |
| 时钟早于纪元 | `IdException` | `clock is before aifei-id epoch 2024-01-01T00:00:00Z, now: {now}` |
| 41 位时间戳耗尽 | `IdException` | `timestamp overflow, the 41-bit timestamp field of snowflake is exhausted, now: {now}` |
| 等待时钟被中断 | `IdException` | `interrupted while waiting for clock to catch up` |
| workerId 租约丢失 | `IdException` | `worker id lease lost, workerId: {workerId}, stop generating ids to avoid duplicates` |
| 号段取段失败 | `IdException` | `segment store failed, cache: __ID__, key: {key}, cause: {底层原因}` |
| 号段回退（计数器过期 / 被重置） | `IdException` | `segment rolled back, lastMax: {lastMax}, newSegment: [{start}, {end}], the counter may have expired or been reset` |
| 号段后端不是共享存储 | `IdException` | `aifei-cache backend is not a shared store (supportsCas=false), aifei-id requires a shared backend such as redis` |
| 缺少 `aifei-cache` | `IdException` | `aifei-id requires module aifei-cache for this configuration, add io.github.gollyhu:aifei-cache to your dependencies` |
| 缺少 `aifei-lock` | `IdException` | `aifei-id requires module aifei-lock when id.worker.source=lock, add io.github.gollyhu:aifei-lock to your dependencies` |
| `0..1023` 全被占用 | `IdException` | `no worker id available in [0, 1023], all leases are held by other instances` |
| 协同模块未就绪（启动顺序不对） | `IdException` | `aifei-id assembly failed, {模块名} is not ready, start it before IdPlugin: {原因}` |
| 枚举配置非法 | `IdException` | `id.strategy must be one of [snowflake, segment], but was: {值}`（另两处：`id.worker.source` 为 `[config, cache, lock]`，`id.segment.store` 为 `[cache, local]`） |
| 必填 / 越界 / 非整数 | `IdException` | `id.workerId is required when id.worker.source=config`；`id.workerId must be in [0, 1023], but was: {值}`；`id.segment.step must be in [1, 1000000], but was: {值}`；`id.state.ttlMillis must be in [60000, 2147483647000], but was: {值}`；`id.segment.key must be non-blank, without whitespace and no longer than 64 chars, but was: {值}`；`id.worker.leaseMillis must be at least 1000, but was: {值}`；`id.{键} must be an integer, but was: {值}` |

启动期失败一律不留半装配；运行期失败一律拒绝发号，不会「看起来正常」。

### 7.2 常见问题

- **启动报 `id.workerId is required when id.worker.source=config`**：默认来源是 `config`，必须配 `id.workerId`（`0..1023`）；不想手工编号就改 `id.worker.source`。
- **启动报 `... is not ready, start it before IdPlugin`**：`plugins.add` 顺序不对，`CachePlugin` / `LockPlugin` 要放在 `IdPlugin` 之前。
- **取号报 `IdGenerator not installed ...`**：`IdPlugin` 没装配、没启动，或已停机。
- **取号报 `clock moved backwards beyond tolerance`**：宿主时钟被改回或虚拟机暂停恢复，先修时钟再重启（模块不换 workerId）。
- **取号报 `segment rolled back`**：号段计数器过期或被人清空，换 `id.segment.key` 或把计数器手工置回高位。
- **ID 看起来「不连续」**：正常。雪花在毫秒边界跳跃，号段跨实例有空洞、崩溃会丢弃当前段剩余号；契约只保证唯一与单调。
- **前端显示末位是 0**：用了数字形态的 19 位 ID，改用 `IdKit.nextIdStr()`。

### 7.3 文档索引

- **契约与决策（事实来源）**：`docs/design.md`——公共 API 逐字签名、位布局与冻结数据格式、回拨与号段语义、异常消息表、验收标准。
- **开发代理约束**：本模块 `AGENTS.md`——包边界与架构门禁、唯一定义点清单、验证命令。
- **坐标与依赖口径**：`io.github.gollyhu:aifei-id:1.0.0`（`packaging=jar`，Java 8）；`cn.aifei:aifei-all` 为 `provided`，`aifei-cache` / `aifei-lock` 为 `optional`。
- **示例工程**：`aifei-id-demo/`（平级目录、端口 18140、离线可跑），演示单号生成、并发批量去重、号段换段与 workerId 来源。
