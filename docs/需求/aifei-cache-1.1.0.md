# aifei-cache 1.1.0 需求（任务输入）

> 本文件是**任务输入**，不是 PRD / 设计。第三节「已拍板决策」进入实现后不得改动；**2026-11-04 已完成全部定档，无待拍板项**。
>
> 来源：`docs/需求/aifei-lock.md` 第二节的前置依赖。**本版本是 `aifei-lock` 的前置包，必须先发布。**

## 一、背景

`aifei-cache` 的 `Cache` 接口只有**无条件** 的 `remove` / `expire`：

```java
void remove(String cacheName, String key);                                  // 不校验当前值
boolean expire(String cacheName, String key, Duration ttl);                 // 不校验当前值
```

这意味着业务无法安全地「只删除自己写入的那一项」。分布式锁场景下表现为经典缺陷：

1. A 持有锁，租约到期自动释放；
2. B 抢到同一把锁；
3. A 执行完调用 `remove`，把 **B 的锁**删掉，C 随即也能进入临界区。

「先 `get` 比对、再 `remove`」无法修复——两步之间不是原子的。因此本版本补齐 **CAS（比较并操作）** 能力，并暴露后端标识，供上层判断存储是否为共享存储。

## 二、目标与边界

- 坐标：`io.github.gollyhu:aifei-cache:1.1.0`（minor 版本，**纯新增**）
- 技术基线：**Java 8**（class major 52，`mvn clean verify` 含 `ClassFileVersionTest`）
- 目标：新增 3 个 CAS 方法与 1 个后端标识。`removeIfEquals` / `expireIfEquals` / `backendType()` 供 `aifei-lock` 使用，`putIfEquals` 作为通用乐观更新能力

**明确不做**：

1. **不改任何既有方法的签名与行为**，不动冻结数据格式（哨兵类型、key 前缀、物理 key 格式、Fury 编码）。
2. **不改 `CacheProvider` / `CacheProviderRegistry` 的扩展契约**（仍属非承诺 API）。
3. **不做 `Cache` 接口的 `default` 方法兜底**（理由见第六节）。
4. **不在 `caffeine` 后端实现 CAS 语义**，其实现体显式抛 `UnsupportedOperationException`（见决策 5）。

## 三、已拍板决策

1. **新增 4 个公共方法**：`removeIfEquals` / `expireIfEquals` / `putIfEquals` / `backendType()`，均加到 `Cache` 接口。
2. **比对基准 = 序列化后的字节**（`jedis` 后端）；`caffeine` 后端**不支持**三个 CAS 方法。该差异须写入 `docs/design.md` 的「共同底线 / 允许差异」两份清单（**该节目前不存在，需新建**，见第七节）。
3. **存储故障不得吞成 `false`**：`false` 只表达「值不匹配」这一个确定结果；存储不可用、连接超时、序列化失败必须抛运行时异常。
4. **1.1.0 先发布**，`aifei-lock` 依赖该 release 版（不依赖 SNAPSHOT、不同批发布）。
5. **新增方法为抽象方法**（不是 `default`），两个内置后端都必须写出方法体：`RedisCache` 实现 CAS 语义，`CaffeineCache` 的实现体为 `throw new UnsupportedOperationException(...)`。这样「不支持」是有意声明且在编译期强制可见，而不是 `default` 兜底导致漏写。`backendType()` 两侧都正常返回。
6. **`backendType()` 的返回值与 `CacheProvider#type()` 必须同源**：取值为 `cache.type` 的字面量 **`"jedis"` / `"caffeine"`（不是 `"redis"`）**，常量归口 `internal.BackendTypes`；**类型名取值的 4 处**（`RedisCacheProvider#type()`、`CaffeineCacheProvider#type()`、`CacheProviderRegistry` 静态注册块的两个 key）与 `Cache#backendType()` 一律引用该常量，不得各写一份。**`RedisConfig.JEDIS_KEY_SEGMENT` 不纳入归口**——它是配置分段名（`cache.jedis.*`），与类型名当前同值但属另一类规则，绑死会让「改类型名」牵动「改配置键」。
7. **`putIfAbsent` 的单命令语义固化为契约**：Redis 实现必须保持单条 `SET key value PX ttl NX`，**不得拆成「无 TTL 写入 + 随后 `expire`」**——两步之间进程崩溃会留下无租约的永久锁。须在 `docs/design.md` 标注「`aifei-lock` 依赖此语义，不得退化」。注意 `putIfAbsent` 成功后另有一次 `SADD`（`cacheNames()` 登记），它是**附加登记**、不参与互斥判断，标注时须区分「锁语义只依赖 `SET NX PX`」与「登记是附加命令」。
8. **`expectedValue == null` 与 `putIfEquals` 的 `newValue == null` 一律抛 `IllegalArgumentException`**：`null` 与「空值哨兵」在字节层是两种不同形态，语义容易误用；锁与业务 CAS 都不需要删除 `null` 条目。`newValue` 单独定档的理由：`cacheNull=false` 时 `put` 对 `null` 是「跳过写入」，若沿用该规则，`putIfEquals` 既不能返回 `true`（无操作发生，违背第五节第 4 条）也不能返回 `false`（`false` 专属「值不匹配」），语义无解，故与 `expectedValue` 一致地拒绝。
9. **Lua 沿用 `client.eval`**（每次发送脚本体），不做 `SCRIPT LOAD` + `EVALSHA` 缓存，与 `RedisCounter#updateCounter` 保持一致。
10. **`removeIfEquals` / `putIfEquals` 不清理 `cacheNames()` 登记**，与既有 `remove` 保持一致，避免扩大范围。

## 四、功能需求（P0，本次交付范围）

### 1. `removeIfEquals`

```java
/**
 * 当缓存项存在、未过期且当前值与 expectedValue 一致时删除；否则不做任何操作。
 */
boolean removeIfEquals(String cacheName, String key, Object expectedValue);
```

| 情形 | 结果 |
| --- | --- |
| 存在、未过期、值匹配 | 删除并返回 `true` |
| 不存在 / 已过期 | `false`，不抛 |
| 存在但值不匹配 | `false`，不抛，**不得删除** |
| `expectedValue` 为 `null` | `IllegalArgumentException` |
| 参数非法（名称/键为空、含非法字符） | `IllegalArgumentException` |
| 存储不可用 / 连接超时 / 序列化失败 | **抛运行时异常**，不得返回 `false` |
| `caffeine` 后端 | `UnsupportedOperationException` |

原子性要求：`jedis` 后端必须用**单条 Lua 脚本**完成「GET → 字节比较 → DEL」；不得拆成两次往返。

### 2. `expireIfEquals`

```java
/**
 * 当缓存项存在、未过期且当前值与 expectedValue 一致时重设剩余有效期；否则不做任何操作。
 */
boolean expireIfEquals(String cacheName, String key, Object expectedValue, Duration ttl);
```

| 情形 | 结果 |
| --- | --- |
| 存在、未过期、值匹配 | 重设 TTL 并返回 `true` |
| 不存在 / 已过期 | `false`，不抛，**不重建条目** |
| 存在但值不匹配 | `false`，不抛，**不得续期** |
| `expectedValue` 为 `null` / `ttl` 非法 | `IllegalArgumentException`，TTL 校验沿用 `CacheValidator.requireTtl` |
| 存储不可用 / 连接超时 / 序列化失败 | **抛运行时异常**，不得返回 `false` |
| `caffeine` 后端 | `UnsupportedOperationException` |

原子性要求同 `removeIfEquals`：单条 Lua 完成「GET → 字节比较 → PEXPIRE」。

### 3. `putIfEquals`

```java
/**
 * 当缓存项存在、未过期且当前值与 expectedValue 一致时写入 newValue 并重设有效期；否则不做任何操作。
 */
boolean putIfEquals(String cacheName, String key, Object expectedValue, Object newValue, Duration ttl);
```

| 情形 | 结果 |
| --- | --- |
| 存在、未过期、值匹配 | 写入 `newValue`、重设 TTL，返回 `true` |
| 不存在 / 已过期 | `false`，不抛，**不重建条目** |
| 存在但值不匹配 | `false`，不抛，**不得写入** |
| `newValue` 为 `null` | `IllegalArgumentException`，**不按 `cacheNull` 规则处理**（理由见决策 8） |
| `expectedValue` 为 `null` / `ttl` 非法 | `IllegalArgumentException` |
| 存储不可用 / 连接超时 / 序列化失败 | **抛运行时异常**，不得返回 `false` |
| `caffeine` 后端 | `UnsupportedOperationException` |

原子性要求：单条 Lua 完成「GET → 字节比较 → `SET key newValue PX ttl`」；写入必须一次带 TTL，不得先写后补 `PEXPIRE`。

用途：值匹配才更新的乐观更新（状态机迁移、快照替换等），不用于锁。

### 4. `backendType`

```java
/**
 * 返回该缓存实例所连接后端的类型标识，取值与配置项 cache.type 一致。
 */
String backendType();
```

- 取值：`"jedis"` / `"caffeine"`（与 `cache.type` 完全一致，**不存在 `"redis"`**）
- 用途：`aifei-lock` 启动时校验「后端是否为共享存储」，`caffeine` 直接启动失败
- **定义点**：`internal.BackendTypes` 为唯一定义点；`RedisCacheProvider#type()`、`CaffeineCacheProvider#type()` 与 `Cache#backendType()` 都引用它

## 五、关键契约

### 1. 字节比对的具体规则

`jedis` 后端：

1. `expectedValue` 先经**本实例的 codec**（与写入时同一个）序列化；
2. 与存储中的原始字节直接比较（Lua 中 `redis.call('GET', KEYS[1]) == ARGV[1]`）；
3. **不在比较前解码**——解码会触发反序列化，既慢又可能因类型信息引入歧义。

**字节确定性前提（必须写入 `design.md`）**：`FuryRedisValueCodec` 无状态、Fury 实例进程内单例，同一标量值两次 `encode` 字节相同；但构造参数含 `withRefTracking(true)` 与 `CompatibleMode.COMPATIBLE`，字节依赖**对象图的引用共享形态**，且跨进程 / 跨 Fury 版本不保证等价。因此 `putIfEquals` 的 `expectedValue` 必须是**同一对象引用**或**字节确定性等价**的可比值；带共享子对象或环的对象图不得用于 CAS。锁场景的 `token` 为 `String`，满足该前提。

`caffeine` 后端：**不实现 CAS**，三个方法一律抛 `UnsupportedOperationException`。`caffeine` 是进程内后端，没有跨实例互斥需求，提供半个 CAS 语义（按 `equals`）只会让调用方误以为拿到了共享存储的保证。

**空值哨兵**：`NullValue` 是冻结数据格式，比较目标是**存储字节**，因此哨兵条目按哨兵的字节参与比较，不受本次改动影响。

**已接受限制（须记入 `design.md` 的「已接受限制与登记」）**：`expectedValue == null` 被拒且 `NullValue` 位于 `internal` 包不可达，故**哨兵条目没有 CAS 删除 / CAS 更新路径**；此类条目只能沿用不安全的无条件 `remove`，或干脆不使用 `cacheNull`。锁与本次交付的业务 CAS 均不需要该路径。

### 2. `null` 入参（已定档）

抛 `IllegalArgumentException`。`expectedValue` 表达的是「我写入的那份值」，必须是可序列化的确定值；允许 `null` 会让「匹配空值哨兵」与「没有期望值」纠缠不清。`putIfEquals` 的 `newValue` 同样受此限制（见决策 8）。

### 3. 参数校验复用唯一定义点

名称、键、TTL 的校验一律调用 `CacheValidator`，**不得在实现类里复制规则**（沿用本模块既有约束）。

### 4. 返回值、异常与「不支持」的边界

| 返回 / 抛出 | 含义 | 调用方处理 |
| --- | --- | --- |
| `true` | 操作确实发生 | 正常继续 |
| `false` | 确定的「没有发生」：不存在、已过期、值不匹配 | 正常业务分支 |
| 运行时异常 | **结果未知**（存储故障），可能发生也可能没发生 | 按可重试 / 需补偿处理 |
| `UnsupportedOperationException` | **能力不支持**（`caffeine` 后端），未执行任何操作 | 不得按「可能已发生」处理；应改用共享存储 |

前两行的边界是 `aifei-lock` 区分「锁被别人持有」与「Redis 挂了」的唯一依据，**不得放宽**。

## 六、兼容性与版本策略

| 项 | 结论 |
| --- | --- |
| 版本号 | `1.1.0`（minor，新增能力，无行为变更） |
| 源码兼容 | 既有调用方无需改动 |
| 二进制兼容 | **对自定义 `Cache` 实现是破坏性的**——新增抽象方法后，未实现的第三方实现会在运行时抛 `AbstractMethodError`；但自定义实现只能经 `internal.CacheProvider` 扩展（非承诺 API），实际影响受控，且必须在 `design.md` 声明 |
| 为什么不给 `default` 兜底 | `default` 抛 `UnsupportedOperationException` 会让不兼容的实现「编译通过、运行时才炸」，把发现时机推迟到生产；抽象方法让问题在编译期暴露 |
| `caffeine` 抛异常是否等于 `default` 兜底 | **不等价**。`default` 是接口替实现兜底、实现者可以不知情；本方案是每个实现**必须显式写出**方法体，`caffeine` 的抛异常是「有意声明不支持」，编译期即强制面对 |
| 发布方式 | `mvn -Pcentral-release clean deploy`（本模块已配置 Central 发布） |

## 七、非功能需求 / 硬约束

- **冻结数据格式不得变更**：哨兵类型、key 前缀、物理 key 格式、Fury 编码。本次只新增读-比较-写命令，不改任何编码路径。
- **每类规则唯一定义点**：参数校验（`CacheValidator`）、后端标识（`BackendTypes`）、Lua 脚本常量（各自实现类内），实现类不得复制。
- **CAS 走写路径校验**：3 个 CAS 方法对非法参数抛 `IllegalArgumentException`，**不得沿用 `remove` / `get` / `getTtl` 的静默返回**（该不对称是既有实现现状，本次新增方法统一取写路径语义），须写入 `design.md` 并由用例钉死。
- **门面镜像不得漏**：`FacadeMirrorTest` 要求 `Cache` 的每个实例方法在 `CacheKit` 有同签名静态方法，白名单仅 `namespace()`。因此 3 个 CAS 方法必须补 `CacheKit` 静态镜像；`backendType()` 与 `namespace()` 同属配置自述，**加入 `CONTRACT_ONLY` 白名单**（否则门禁转红）。`Cache` 亦不得声明常量（`shouldNotDeclareConstantsOnContracts`），类型名字面量只能落 `internal.BackendTypes`。
- **两个后端契约一致**：除「`caffeine` 不支持 CAS」这一能力性差异外，其余返回值、异常、TTL 语义完全一致，差异逐项写入 `design.md` 的「允许差异」清单。该清单**当前尚不存在**（`design.md` 第 19 行指向一个空章节），本次须**新建**「跨实现契约：共同底线与允许差异」一节（参照 `aifei-mq/docs/design.md` §5.6 的分层表述），并顺带修掉该悬空引用。
- **测试基线**：
  - 契约测试扩展现有 `CacheContractTest`（以 `CaffeineCache` 为参照实现，**不是抽象基类**，沿用现状即可，不强行改造）；
  - **CAS 语义用例不得放进以 `CaffeineCache` 为参照的共享契约测试**——该实现恒抛 `UnsupportedOperationException`。共享契约测试对 3 个 CAS 方法只断言「`caffeine` 抛 `UnsupportedOperationException`」，实际 CAS 语义全部落在 Redis 侧；
  - Redis 侧扩展 `RedisCacheIntegrationTest`，默认跳过，`-Dredis.integration=true` 开启；开启后连不上必须失败。
  - 3 个 CAS 是本次交付核心且**唯一真实实现在 Redis 侧**，因此必须补**不依赖 Redis 的单测**：断言 3 条 Lua 常量文本包含 `redis.call('GET', ...)`、字节比较以及 `DEL` / `PEXPIRE` / `SET`，并断言 `eval` 收到 1 个 key 与预期 ARGV 装配。
  - 「契约测试不抽基类、Redis 契约只在集成测试覆盖」属既有状态，本次不改造，但须记入 `design.md` 的「已接受限制」。
- **不新增第三方依赖**：Lua 通过既有 `client.eval` 执行（参照 `RedisCounter#updateCounter`），不引入脚本框架。
- **文档同步**：`docs/design.md`（新方法契约、允许差异清单、兼容性声明，以及 `putIfAbsent` 单命令语义的固化标注）、`README.md`（用法与 `backendType` 说明）、`AGENTS.md`（如需新增约束）。
- **模块内验证**：`aifei-cache` 目录内 `mvn clean verify` 必须 **0 failure / 0 error**；涉及 Redis 的改动，`-Dredis.integration=true` 为**发布前置门禁**（不是可选步骤），须在发布前实际跑通。

## 八、必须交付的固定配套

| 类别 | 交付物 |
| --- | --- |
| 公共面 | `Cache` 接口新增 4 个方法 + 完整 javadoc（`@param` / `@return` / `@throws`，含 `UnsupportedOperationException` 说明） |
| 门面 | `CacheKit` 新增 3 个 CAS 方法的静态镜像（`removeIfEquals` / `expireIfEquals` / `putIfEquals`）；`backendType()` 加入 `FacadeMirrorTest.CONTRACT_ONLY` 白名单（与 `namespace()` 同属配置自述），经 `CacheKit.use().backendType()` 取用 |
| 内部 | `internal.BackendTypes`（后端标识唯一定义点，覆盖类型名取值的 4 处 + `Cache#backendType()`；`RedisConfig.JEDIS_KEY_SEGMENT` 不纳入）；Lua 脚本常量落在各自实现类 |
| 实现 | `redis.RedisCache`：4 个方法（3 条 Lua 脚本）；`caffeine.CaffeineCache`：`backendType()` 返回 `"caffeine"`，3 个 CAS 方法显式抛 `UnsupportedOperationException` |
| 接入 | `aifei` 包无新增；`CachePlugin` 行为不变 |
| 测试 | `CacheContractTest` 扩展（CAS 只断言 `caffeine` 抛 `UnsupportedOperationException`）；新增不依赖 Redis 的 Lua 常量与 `eval` 参数装配单测；`RedisCacheIntegrationTest` 扩展；新增固定向量用例（见验收） |
| 文档 | `docs/design.md`、`README.md`、`AGENTS.md`（按需） |

## 九、参考资产

- **Lua 原子操作范式**：`aifei-cache/src/main/java/cn/hg/aifei/cache/redis/RedisCounter.java#updateCounter`（`client.eval(SCRIPT, keys, args)`，TTL 先在 Java 侧校验再进脚本）
- **校验唯一定义点**：`aifei-cache/src/main/java/cn/hg/aifei/cache/internal/CacheValidator.java`
- **值编解码入口**：`RedisConfig#createValueCodec`、`FuryRedisValueCodec`（字节比较必须用同一个 codec 实例）
- **契约测试现状**：`aifei-cache/src/test/java/cn/hg/aifei/cache/CacheContractTest.java`（以 `CaffeineCache` 为参照实现）
- **门面门禁**：`aifei-cache/src/test/java/cn/hg/aifei/cache/FacadeMirrorTest.java`（`Cache` 的每个实例方法须在 `CacheKit` 有同签名静态镜像，白名单仅 `namespace()`；`Cache` 不得声明常量）
- **唯一定义点扫描范式**：`aifei-cache/src/test/java/cn/hg/aifei/cache/internal/SentinelDefinitionPointTest.java`
- **集成测试开关**：`aifei-cache/src/test/java/cn/hg/aifei/cache/redis/RedisCacheIntegrationTest.java`（`Boolean.getBoolean("redis.integration")` + `Assume`）
- **版本门禁**：`aifei-cache/src/test/java/cn/hg/aifei/cache/ClassFileVersionTest.java`（class major 52）
- **下游需求**：`docs/需求/aifei-lock.md` 第二节与第四节

## 十、验收标准

- [ ] `aifei-cache` 目录内 `mvn clean verify` 通过（0 failure / 0 error），`ClassFileVersionTest` 通过（class major 52）。
- [ ] `Cache` 接口新增 4 个方法，javadoc 含 `@param` / `@return` / `@throws`。
- [ ] **误删防护用例**：值不匹配时 `removeIfEquals` 返回 `false` 且条目仍存在（Redis 侧覆盖；`caffeine` 侧断言抛 `UnsupportedOperationException`）。
- [ ] **误续期防护用例**：值不匹配时 `expireIfEquals` 返回 `false` 且 TTL 未变。
- [ ] **条件写入用例**：值匹配时 `putIfEquals` 返回 `true`、值已更新且 TTL 已重设；不匹配时返回 `false` 且**值与 TTL 均未变**；条目不存在时不重建。
- [ ] **`null` 参数用例**：三个 CAS 方法的 `expectedValue` 为 `null` 时抛 `IllegalArgumentException`；`putIfEquals` 的 `newValue` 为 `null` 时同样抛 `IllegalArgumentException`，且条目值与 TTL 均未被修改。
- [ ] **参数校验走写路径**：`removeIfEquals` / `expireIfEquals` / `putIfEquals` 在 `cacheName` 或 `key` 非法时抛 `IllegalArgumentException`（**不得沿用 `remove` 的静默返回**）。
- [ ] **故障不吞用例**：模拟存储故障时抛运行时异常，**不得返回 `false`**。
- [ ] **原子性用例**：断言 3 条 Lua 常量文本分别包含 `GET` + 字节比较 + `DEL` / `PEXPIRE` / `SET ... PX`，且 `eval` 只收到 1 个 key 与预期 ARGV（不依赖 Redis 即可执行）；Redis 侧另以命令计数佐证只发一条 `EVAL`。
- [ ] **`putIfAbsent` 单命令回归**：Redis 实现仍为单条 `SET ... PX ... NX`，未被拆成「无 TTL 写入 + `expire`」两步；`design.md` 已标注「锁语义只依赖 `SET NX PX`，`cacheNames()` 的 `SADD` 是附加登记」。
- [ ] **冻结格式回归**：现有哨兵、物理 key、Fury 编码相关固定向量用例全部通过，未被本次改动影响。
- [ ] **字节确定性用例**：同一标量值两次 `encode` 字节相等；`putIfEquals` 用同一对象引用可命中。带共享子对象的对象图不保证字节等价，作为「已接受限制」记入 `design.md`，不做正向断言。
- [ ] **后端标识一致性用例**：`backendType()` 与 `CacheProvider#type()` 取值一致（`"jedis"` / `"caffeine"`）；源码扫描断言「类型名取值的 4 处 + `Cache#backendType()` 均引用 `internal.BackendTypes`，字面量只在该类出现」，`RedisConfig.JEDIS_KEY_SEGMENT` 显式排除；扫描测试沿用 `internal/SentinelDefinitionPointTest` 的形态。
- [ ] **门面镜像用例**：`CacheKit` 提供 3 个 CAS 方法的同签名静态镜像；`backendType()` 在 `CacheKit` 无镜像且已列入 `FacadeMirrorTest.CONTRACT_ONLY`；`FacadeMirrorTest` 全部通过。
- [ ] `RedisCacheIntegrationTest` 在 `-Dredis.integration=true` 下通过；未开启时正常跳过。该开关为发布前置门禁。
- [ ] `docs/design.md` 与 `README.md` 已同步（含「允许差异」清单与兼容性声明）。

## 十一、执行方式

- 本文件为**任务输入**。收到该开发类任务后，按仓库根 `AGENTS.md` 与 `aifei-cache/AGENTS.md` 的约束执行。
- 第三节已全部定档，无待拍板项，直接进入设计与实现。
- **顺序约束**：本版本必须**先于** `aifei-lock` 发布；`aifei-lock` 不在此基础上实现自己的 Redis 原子操作。
- 不改动任何既有方法行为；若发现既有缺陷（非本次范围），只记录、不在本次顺手修。

## 附录：与 `aifei-lock` 的对应关系

| `aifei-lock` 内部操作 | 依赖的 `aifei-cache` 能力 | 现状 |
| --- | --- | --- |
| `LockStore.tryAcquire` | `putIfAbsent(cacheName, key, token, ttl)`（单命令 `SET NX PX`） | 1.0.0 已具备 |
| `LockStore.release` | `removeIfEquals(cacheName, key, token)` | **本次新增** |
| `LockStore.renew` | `expireIfEquals(cacheName, key, token, ttl)` | **本次新增** |
| `LockPlugin` 启动校验 | `backendType()`（只接受 `"jedis"`） | **本次新增** |
| 测试断言租约存在 | `getTtl(cacheName, key)` | 1.0.0 已具备 |

`token` 在 lock 侧固定为 `String` 且不对外暴露（见 `docs/需求/aifei-lock.md` 三、公共 API），因此本版本只需支持通用 `Object` 的字节比较，不需要为特定类型做特殊处理。`LockStore.renew` 的 `ttl` 取句柄携带的**创建时 `lease`**（`Lock#renew()` 无参，租约随句柄传递）。

`backendType()` 的取值与 lock 侧启动白名单已对齐：lock 只接受 `"jedis"`（`"redis"` 不是合法取值），`caffeine` 及其他取值一律导致 `LockPlugin` 启动失败。

**跨模块契约字面量声明（须写入两侧 `design.md`）**：本模块的「唯一定义点」只覆盖本模块源码；`"jedis"` 同时是对外契约字面量，lock 侧必然在自己模块再写一份白名单，这属**预期重复而非违规**。须双向登记：① `aifei-cache/docs/design.md` 标注「`"jedis"` 是对外契约字面量，`aifei-lock` 白名单依赖它」；② `aifei-lock/docs/design.md` 标注「`aifei-cache` 新增共享后端时须同步本白名单」。若不登记，未来 cache 增加共享后端（如 `redisson`）会被 lock 静默拒绝，且改 cache 的人无从得知。

`putIfEquals` 不在 lock 的依赖面内，lock 不得调用它。
