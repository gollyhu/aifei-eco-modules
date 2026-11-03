# aifei-cache 测试文档

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据与契约）、`README.md`（使用说明）

本文件面向测试维护者：测试分层与文件清单、契约测试机制、运行方式、当前基线与新增约定。契约本身以 `docs/design.md` 为准。

## 1. 测试原则

- **不 mock 被测实现**：Caffeine 后端用真实 `CaffeineCache` / `CaffeineCounter`；Redis 集成用例连真实 Redis（默认关闭）。
- **契约只断言一次**：与存储无关的哨兵语义、`loader` 校验顺序、TTL 上下限、物理 key 拼装集中在 `CacheContractTest`；两后端共享的行为不在各自测试类里重复断言。
- **门禁自动化**：公共面大小、门面完整性、冻结数据格式由测试断言，不依赖人工 review。
- **用例独立且幂等**：不依赖执行顺序，可重复运行；Redis 用例用随机前缀并在结束时按前缀 `SCAN` + `UNLINK` 自清理。

## 2. 测试分层与文件清单

16 个可运行测试类，另有 3 个测试辅助文件（`TestSupport`、`elementvalue/ElementValue`、`testvalue/CodecValue`，不含用例）。

| 层次 | 测试类 | 用例数 | 职责 |
| --- | --- | --- | --- |
| 契约（存储无关） | `CacheContractTest` | 21 | 空值哨兵语义、带 `loader` 的 `get` 校验顺序、TTL 上下限、哨兵编解码与物理 key 拼装 |
| 公共面 | `FacadeMirrorTest` | 8 | 反射比对 `CacheKit` / `CounterKit` 与 `Cache` / `Counter` 的方法集合（`namespace()` 白名单 + 反向用例），钉死根包公共类型集合 |
| 装配与配置 | `CachePluginTest` | 30 | `cache.*` 解析与校验、启动期失败语义、主实例与扩展实例装配 |
| 多实例 | `MultiInstanceTest` | 5 | `cache.names` 声明、`CacheKit.use(name)` 取用、唯一名排他判定 |
| 字节码 | `ClassFileVersionTest` | 1 | 主 JAR 全部 class 的 major version = 52 |
| Caffeine 后端 | `CaffeineCacheTest` | 22 | 读写、TTL、`exists` / `remove` / `getTtl` / `keys` / `cacheNames()` 与空值语义 |
| Caffeine 后端 | `CaffeineCounterTest` | 18 | 计数递增、步长、TTL 与 `counterNames()` |
| Caffeine 后端 | `CaffeineConcurrencyRegressionTest` | 3 | 并发写入 / 计数回归（防止历史并发缺陷复现） |
| internal | `CacheManagerTest` | 14 | 实例注册、唯一名冲突、生命周期 |
| internal | `CacheProviderRegistryTest` | 7 | provider 注册、`types()` 派生、未知后端失败 |
| internal | `SingleFlightTest` | 6 | 回源单飞的登记、胜者复用与中断降级 |
| internal | `SentinelDefinitionPointTest` | 2 | 哨兵单实例存 / 取往返；哨兵全限定名 `cn.hg.aifei.cache.internal.NullValue` 冻结 |
| Redis | `RedisConfigUniqueNameTest` | 5 | `jedis` 键分段与唯一名派生（`RedisConfig#uniqueName()`） |
| Redis | `FuryRedisValueCodecTest` | 4 | Fury 编解码与反序列化白名单（拒绝项先于放行项） |
| Redis | `ArrayValueShapeTest` | 9 | 数组值形态往返：业务类型数组、多维数组与判别性样本（`java.time` 数组、`BigDecimal[]`、`Object[]{LocalDate[]}`），不依赖 Redis |
| Redis（集成） | `RedisCacheIntegrationTest` | 20 | 真实 Redis 端到端（默认跳过，见第 5 节） |

## 3. 契约测试机制

- `CacheContractTest` **不是抽象基类**：它用 `new CaffeineCache(...)` 直接构造被测实例，因为断言的是与存储无关的契约（哨兵、`loader` 校验顺序、TTL 上下限），换后端不改变结论。
- 两后端的**真实差异**（如 `getTtl` 对无过期键的处理、`clear` 与名称集合的交互）写在各自后端测试类，并在 `docs/design.md` 的「允许差异」清单中登记；`CacheContractTest` 只覆盖共同底线。
- `FacadeMirrorTest` 用反射比对签名，新增 `Cache` / `Counter` 方法时若忘记同步门面会直接失败；`namespace()` 属**刻意不镜像**项，反向用例固定该决定。
- `SentinelDefinitionPointTest` 钉住哨兵的全限定名 —— 哨兵类型与包名属**冻结数据格式**，改动会破坏存量 Redis 数据。

## 4. 门禁与冻结格式断言

| 断言 | 位置 | 内容 |
| --- | --- | --- |
| 公共面大小 | `FacadeMirrorTest` | 根包公共类型集合精确匹配（当前 6 个），防止公共面无意扩张 |
| 门面完整性 | `FacadeMirrorTest` | 门面镜像全部业务方法；`Cache` / `Counter` 不声明 `static` 方法与常量 |
| 哨兵数据格式 | `SentinelDefinitionPointTest` | `cn.hg.aifei.cache.internal.NullValue` 全限定名不得变更 |
| 字节码 | `ClassFileVersionTest` | `target/classes` 下全部 `.class` 的 major version = 52 |

## 5. 运行方式

**命令一律在模块根目录执行**：

```bat
cd aifei-cache
mvn clean verify                        :: 全量（含门禁与字节码），必须 0 failure / 0 error
mvn test -Dtest=CaffeineCacheTest#putThenGet   :: 单个用例，格式 类名#方法名
mvn test -Dtest=FacadeMirrorTest        :: 单个测试类
```

Redis 集成用例**默认关闭**（`pom.xml` 的 `redis.integration=false`，经 surefire `systemPropertyVariables` 注入）：

```bat
mvn verify -Dredis.integration=true -Dredis.uri=redis://127.0.0.1:6379/0
```

- 开启后**连不上必须失败**，不得静默跳过；`redis.uri` 缺省为 `redis://127.0.0.1:6379/0`，可用同名系统属性覆盖。
- 用例使用随机前缀隔离数据，结束时按前缀 `SCAN` + `UNLINK` 清理；Redis 服务不支持 RESP3（`HELLO`）时相关用例按 `Assume` 跳过。

## 6. 当前基线

| 项 | 值 |
| --- | --- |
| 可运行测试类 | 16（另 3 个测试辅助文件） |
| 用例总数 | 175 |
| 默认执行 | 155（Redis 集成 20 项跳过） |
| 开启 `-Dredis.integration=true` | 175 项全跑，0 skipped |
| 编译目标 | Java 8（class major version = 52） |

> 基线随功能演进更新；提交前 `mvn clean verify` 必须 0 failure / 0 error，用例数不得减少。

## 7. 未覆盖项与作用边界

- **回源单飞只覆盖单个 JVM**：跨实例不收敛，多实例部署时每个 JVM 各自装载一次（`docs/design.md` 附 B 已登记，非缺陷）。
- **`keys` 的 `offset` / `limit` 语义对齐 Redis `LRANGE`**：跨实例并发写入下的顺序不保证强一致。
- **TTL 精度**：Caffeine 侧依赖其自身的过期精度，Redis 侧为毫秒级，不对跨后端精度做统一断言。
- **未接入的后端**：`docs/design.md`「暂不支持」一节的场景（分布式锁、Lua、pub/sub、ZSet / HLL / Bitmap、pipeline、精确滑动窗口、fail-open 等）无测试，因其不在能力范围内。

## 8. 新增测试约定

1. **修缺陷先固化回归**：已修复的缺陷必须有对应用例（如 `CaffeineConcurrencyRegressionTest`）。
2. **契约改动双后端**：触及共同底线的改动，同时在 `CacheContractTest` 与两后端测试类确认；两后端确有差异时，先更新 `docs/design.md` 的「允许差异」清单再写断言。
3. **冻结格式加固定向量**：哨兵类型、物理 key 格式、Fury 字节格式变更必须补固定向量用例，并在 `docs/design.md` 记录决策。
4. **规则不复制**：测试不得复制被测的校验规则（TTL 上下限、名称合法性），应引用被测常量或直接断言契约。
5. **门禁不放宽**：新增公共类型须同步更新 `FacadeMirrorTest` 的期望集合并说明设计依据，不得为绕过断言而改写断言。
6. **集成开关纪律**：新增外部依赖用例沿用 `redis.integration` 模式 —— 默认关闭、开启后连不上必须失败。
