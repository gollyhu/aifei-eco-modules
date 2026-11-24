# aifei-id 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与架构门禁、唯一定义点清单、行为契约速查与验证命令。

三份文档分工：`README.md` 面向使用者（安装、用法、部署警告）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者。

## 开发前提

- **改代码前必读 `docs/design.md`**（尤其 §5 的 4 个公共类型逐字签名、§7 的行为契约与 §7.6 异常消息表、§10 的门禁 14 例、§15 验收标准）。
- 需求与设计冲突时，**先改设计决策再改代码**；公共面签名不可行时先回写 `docs/design.md` §5，不得先斩后奏。
- 未经设计决策，不得自行确定契约类行为：空值语义、TTL 语义、位布局与纪元、workerId 分配规则、异常类型与消息、缓存键命名；也不得新增或扩大公共 API 面。

## 交付性质

`aifei-id` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**。坐标 `io.github.gollyhu:aifei-id:1.0.0-SNAPSHOT`（`packaging=jar`），Java 8，包名 `cn.hg.aifei.id`。

**不适用范围（不得据此模块约束要求）**：

| 常规章节 | 结论 | 原因 |
| --- | --- | --- |
| 表结构 / `Db+Row` / `Generator` / `java.sql` | 不适用 | 零 DB，门禁反向断言全模块无 `import java.sql.` |
| `@Path` Service / HIO / 路由 / 拦截器 | 不适用 | 无入站 HTTP 面；端点只存在于 `aifei-id-demo` |
| 页面 / 原型 / QA 基线门禁 | 不适用 | 无页面；demo 单页由前端直接实装 |
| `AppConfig implements AifeiConfig` / `Launcher` | 不适用 | 装配动作发生在**使用方**的 `AppConfig.config(Plugins)` 内；本模块只提供 `aifei.IdPlugin` |

## 硬性约束

### 技术基线

- **Java 8 双约束（语法 + API）**：`source`/`target` = `1.8`；主 JAR 内全部 class major = **52**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；原创文件写统一版权头，既有版权块不得改写。
- **零第三方**：pom 只允许 `cn.aifei:aifei-all`（`provided`）+ `io.github.gollyhu:aifei-cache` / `aifei-lock`（`optional`）+ `junit`（`test`）。不得新增依赖，不得引入任何日志绑定（`slf4j` / `logback` / `log4j` 一律禁止）。
- **零 DB**：全模块禁 `import java.sql.`。
- **禁改写 JVM 全局状态**：不得出现 `System.setProperty` / `System.getProperties().put` / `System.setErr` / `System.setOut`。
- **无 provider / 无 SPI**：不得出现 `ServiceLoader` / `META-INF/services`；`src/main/resources` 下不得存在 `META-INF/services/` 目录。自定义生成器注册不在 v1 范围。
- **异常分层固定**：`IdException`（模块自身启动期与运行期失败）、`IllegalArgumentException`（仅 `new IdPlugin(null)`）、`IllegalStateException`（仅 `IdKit` 未装配）。运行期**永不降级**：不返回 0、不回退本地、不静默重试。
- **日志纪律**：只允许 `cn.aifei.log.Log.get(本类.class)` 且**惰性获取**（实例类在构造内、静态工具类在方法内；**禁止** `static` 字段持有 `Log`——类初始化期命中未装配的日志门面会把异常包装成 `ExceptionInInitializerError`，类随后永久不可用）。

### 公共面收敛（不得放宽）

- 根包 `cn.hg.aifei.id` **精确 3 个公共类型**：`IdGenerator` / `IdKit` / `IdException`；装配包 `cn.hg.aifei.id.aifei` **精确 1 个**：`IdPlugin`。**不得增删、不得改名**。
- 根包**零框架类型、零第三方类型**（`IdKit` / `IdGenerator` / `IdException` 签名里不出现任何 `cn.aifei.*`）。`IdPlugin` 位于装配包（非承诺 API），暴露 `cn.aifei.plugin.Plugin` / `cn.aifei.util.Prop` / `cn.aifei.aop.AopKit`。
- **不提供**：`SnowflakeConfig` / `SegmentConfig` 值对象、ID 状态查询 API（`workerId()` / `strategy()`）、命名实例（`id.names`）、provider 扩展点、UUID / 短链 / 业务前缀编码、指标上报。
- 公共面需要新增类型或方法时，先改 `docs/design.md` §2/§5 过设计决策，再改门禁 ④ 的期望集合。

### 包结构与依赖方向

```text
cn.hg.aifei.id              根包：3 公共类型（契约 + 门面 + 异常），零框架类型
cn.hg.aifei.id.aifei        装配包：IdPlugin（唯一框架触点，非承诺 API）
cn.hg.aifei.id.internal     实现层（非承诺 API）：配置 / 错误 / 命名 / 持有器 / 依赖探测 / 两个生成器 / 号段存储 / 三种 workerId 来源
```

- `aifei` 包 → `internal` + `cn.aifei.plugin` / `cn.aifei.util.Prop` / `cn.aifei.aop.AopKit` / `cn.aifei.log.*`。
- `internal` 包 → `cn.aifei.log.*`（唯一允许的任意包框架类型）+ `cn.hg.aifei.cache.*` / `cn.hg.aifei.lock.*`（可选依赖，只在此处出现）。
- 根包 → 纯 JDK。
- 任何文件不得 `import java.sql.*` 或第三方包。

## 包边界与门禁

`ArchitectureBoundaryTest`（**14 例**）扫描 `src/main/java`（⑭ 扫 `src/test/java`）全部 `.java`，按源码文本断言（注释先剥离、路径分隔符归一化）：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| ① | `noThirdPartyReference` | 每条 `import` 必须以 `java.` / `javax.` / `cn.aifei.` / `cn.hg.aifei.id.` / `cn.hg.aifei.cache.` / `cn.hg.aifei.lock.` 开头，无其它放行包 |
| ② | `optionalModuleScopeRules` | `cn.hg.aifei.cache.*` 与 `cn.hg.aifei.lock.*` 的 import 只允许出现在 `internal` 包 |
| ③ | `frameworkOnlyInBridgeExceptLog` | `cn.aifei.log.*` 只允许在 `internal` 与装配包；其余 `cn.aifei.*` 只允许在装配包 |
| ④ | `publicSurfaceExact` | 根包直接子 `.java` 精确 **3** 个、装配包精确 **1** 个，文件名集合精确相等 |
| ⑤ | `noSqlReference` | 全模块不得出现 `import java.sql.` |
| ⑥ | `noJvmGlobalPropertyWrite` | 不得出现 `System.setProperty` / `System.getProperties().put` / `System.setErr` / `System.setOut` |
| ⑦ | `java8Compatibility` | 不得出现 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` / `"""` / `sealed ` / `instanceof` 模式匹配 |
| ⑧ | `noServiceProvider` | 不得出现 `ServiceLoader` / `META-INF/services`；`src/main/resources` 下不得存在 `META-INF/services/` |
| ⑨ | `clockOnlyInSnowflake` | `System.currentTimeMillis`（调用形式）与 `System::currentTimeMillis`（方法引用形式）及纪元字面量 `1704067200000` 只允许出现在 `internal/SnowflakeIdGenerator.java` |
| ⑩ | `cacheNamesOnlyInIdNames` | `"__ID__"` / `"segment."` / `"worker"` 只允许出现在 `internal/IdNames.java` |
| ⑪ | `classForNameOnlyInIdDeps` | `Class.forName` 只允许出现在 `internal/IdDeps.java` |
| ⑫ | `threadCreationOnlyInWorkerLease` | `new Thread(` / `Executors.` / `ThreadPoolExecutor` 只允许出现在 `internal/LockWorkerIdSource.java` |
| ⑬ | `configKeysOnlyInIdConfig` | 字面量 `"id.` 只允许出现在 `internal/IdConfig.java` |
| ⑭ | `testSourcesOfflineOnly` | 测试源码中的 `http://` / `https://` 字面量，host 只能是 `127.0.0.1` 或 `localhost` |

门禁**不得为兼容错误用法而放宽或绕过**；新增例外必须先改 `docs/design.md` §2 再改断言。扫描依赖相对路径 `src/main/java`，**必须在 `aifei-id/` 根目录执行**。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 雪花位宽 / 移位 / EPOCH / 序列上限 / 回拨容差常量 / 时刻采样 / 两条等待路径 | `internal.SnowflakeIdGenerator` |
| 号段分配、耗尽取段、**回退检测**、取段失败包装 | `internal.SegmentIdGenerator` |
| 分区名 `__ID__`、段键 `segment.<key>`、workerId 计数键、锁名、workerId 范围 | `internal.IdNames` |
| 配置键名、默认值常量、`id.*` 解析与校验（分策略） | `internal.IdConfig` |
| `IdException` 构造与全部消息措辞 | `internal.IdErrors` |
| 未装配异常（`IllegalStateException`）与消息 | `internal.IdHolder#get` |
| 缓存模式 workerId 取模规则 | `internal.CacheWorkerIdSource` |
| 共享后端能力校验（`supportsCas`） | `internal.CacheAccess` |
| 可选依赖探测（唯一 `Class.forName` 点） | `internal.IdDeps` |
| 后台线程创建（唯一） | `internal.LockWorkerIdSource` |
| `long → 十进制字符串` | `IdKit#nextIdStr` |
| 装配、失败回滚、停机收敛、启动 INFO | `aifei.IdPlugin#start` / `#stop` |

## 行为契约速查

- **雪花位布局（冻结数据格式）**：`[1 符号=0][41 毫秒][10 workerId][12 序列]`，`EPOCH = 1704067200000L`，`TIMESTAMP_SHIFT = 22`、`WORKER_ID_SHIFT = 12`，单节点 4096/ms。位布局与纪元**不是配置项**，发布后不得更改。
- **时钟回拨**：≤ 5ms 在锁内等时钟追上，追上后按「同一毫秒」继续（序列号 +1，不重置，故不重号）；> 5ms 抛 `IdException`；**永不更换 workerId**。时钟早于纪元、时间戳超 41 位同样失败。
- **线程安全**：两个生成器的 `nextId()` 均为 `synchronized`（锁对象 = 实例），不用 `AtomicLong` + CAS 自旋。`IdHolder` 用 `volatile` 引用，只保证可见性。
- **号段**：`value/max/lastMax` 三段状态，`value > max` 时在锁内同步取段；`start <= lastMax` 即回退失败（防计数器过期 / 被清导致重号）；单 buffer、无预取、无常驻线程。
- **workerId 三来源**：`config`（默认，零依赖，**跨实例重复无法检测**）、`cache`（`(计数 - 1) % 1024`，无租约，**会回绕**）、`lock`（`0..1023` 升序 `tryLock` + 后台续期，全占用即启动失败，续期丢失不可自愈）。
- **失败语义**：运行期不可用一律抛 `IdException` 并停止发号，消息逐字对齐 `docs/design.md` §7.6；装配失败回滚到「未装配」，不留半装配。
- **幂等**：`IdPlugin.start()` 重复调用直接返回；`stop()` 重复或未启动调用为空操作；`WorkerIdSource.acquire()` 幂等（诊断路径重复调用不重复消耗编号）。
- **停机**：`IdKit` 立刻抛 `IllegalStateException`；AOP 容器中的 `IdGenerator` 单例**不注销**（框架无 API），停机后不得继续用注入引用发号。
- **可选依赖缺失**：启动期抛带坐标的 `IdException`，不降级、不回退本地实现。

## 测试纪律

- 测试全部**零联网、零外部服务**，可重复执行；不得用秒级 `Thread.sleep` 堆叠。
- **雪花用例必须注入假时钟**（包级双参构造 `SnowflakeIdGenerator(WorkerIdSource, LongSupplier)`），因此 `SnowflakeIdGeneratorTest` 位于 `cn.hg.aifei.id.internal` 包；等待路径与序列耗尽靠脚本化时钟覆盖，不真实等待。
- 桩一律放 `src/test/java/cn/hg/aifei/id/support/`：`FakeClock`（脚本化时钟）、`FakeCounter`（只用 `increaseAndRefreshTtl`，其余抛 `UnsupportedOperationException`）、`FakeSegmentStore`（段末端脚本 + 故障注入）、`FakeWorkerIdSource`、`FakeIdGenerator`、`NoopLogFactory` / `NoopLog`（不引入日志绑定）。
- `Lock` 是 `final` 且构造器包私有，**无法伪造**：`id.worker.source=lock` 改由 `FakeLockStore`（桩在 `LockStore` 层）在 `IdPluginTest` 内**离线**覆盖「`0..1023` 升序抢占 / 全占用消息 L / 停机释放」三条路径；租约丢失（消息 E）用 `FakeWorkerIdSource#breakLease()` 覆盖。真 Redis 的 `-Dredis.integration=true` 集成用例**默认关闭**（本批未落地），上述路径的离线桩已达成同等覆盖。
- 涉及 `IdPlugin` 的用例需注意：框架无单例注销 API，同一 JVM 内第二个装配入口注册 `IdGenerator` 单例会因「已存在」失败；`IdKitTest` 在每个用例前用 `AopKit.get().setAopFactory(new AopFactory())` 换容器隔离，不修改被测代码。
- 已修复的缺陷必须固化为**回归测试**；消息断言照抄 `docs/design.md` §7.6 的措辞，**不得用常量拼期望值**（否则措辞漂移无法被发现）。

## 注释规范

- 语言一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息。
- 公共类型与公共成员**必须**写 javadoc 并写全 `@param` / `@return` / `@throws`；行为契约（空值、TTL、线程安全、异常、冻结格式）写在 javadoc 里，不写在行注释里。
- 只在代码无法表达「为什么」时写注释；不复述下一行代码、不写行尾注释、不留死代码与 TODO。

## 验证

**命令在 `aifei-id/` 根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                  # 必须 0 failure / 0 error（含架构门禁 14 例）
mvn test -Dtest=ArchitectureBoundaryTest          # 仅跑架构门禁
mvn test -Dtest=SnowflakeIdGeneratorTest          # 单用例：类名或 类名#方法名
mvn dependency:tree                               # 断言：仅 aifei-all(provided) + cache/lock(optional) + junit(test)
```

构建后可用 `javap -verbose` 抽查主 class major = 52。

> **零联网纪律**：全部单测必须断网全绿；测试桩按 `support/` 范式自建，不引入任何服务器或外部服务。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法与部署警告）；公共 API 签名变更须先改 `docs/design.md` §5 并过设计决策。
- 术语与 `docs/design.md` §3 统一：雪花 snowflake / 纪元 epoch / workerId / 序列号 sequence / 号段 segment / 取段 refill / 号段回退 / 本地退化 / 共享后端 / workerId 租约。
- 新增失败路径必须同时落三处：`internal.IdErrors` 的消息构造、`docs/design.md` §7.6 消息表、以及测试的逐字断言。
- 新增配置键必须同时落三处：`internal.IdConfig` 的常量与解析校验、`docs/design.md` §8 配置表、`README.md` §3.4 配置总览。
- 不得因 `aifei-id-demo` 的页面需求回头要求模块新增公共 API（尤其状态查询 API）；demo 的展示值由 demo 自算或取自身配置。
