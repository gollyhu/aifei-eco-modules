# aifei-cache 开发约束

三份文档的分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**事实来源**）；本文件适用于整个仓库，面向开发代理与贡献者（改代码前必须遵守什么、怎么验证）。

## 开发前提

- 改代码前先读 `docs/design.md`；需求与现有设计冲突时，先更新设计决策，再改代码。
- 仓库根的协作记忆目录下维护当日日志（`YYYY-MM-DD.md`，追加式）与长期项目约定；该目录属项目数据，不得删除。

## 硬性约束

### 技术栈

- 代码、依赖与 API 必须兼容 Java 8；主 JAR 内全部 class 的 major version 必须为 52。
- 不得升级 Caffeine 2.9.3（最后一个支持 JDK 8 的版本）；Caffeine / Jedis / Fury 必须保持 `<optional>true</optional>`。
- 不依赖 `cn.aifei:aifei-cache` 构件：`internal.CacheValidator` 的 12 个校验方法（2 个布尔判定 `isValid*` / `isInvalid*` + 10 个 `require*`）已内联为本项目源码，其判定规则与异常消息属**冻结契约**，改动时须先在测试中更新消息断言。

### 结构

- 公共 API 面固定为 6 个类型：`Cache` / `Counter` / `CacheUtil` / `CounterUtil` / `CachePlugin` / `CacheKeyMatchType`；新增公共类型必须先过设计决策。
- 其余代码落在 `internal`（非承诺 API）、`caffeine`、`redis` 三个子包。
- `Cache` 实现类共用无行为元数据基类 `internal.AbstractCache`，`Counter` 实现类共用 `internal.AbstractCounter`。两类都不得再引入其它跨实现基类或抽象父类，不得暴露 Caffeine、Jedis、Fury 等实现类型。
- 装配侧允许有一个抽象父类 `internal.AbstractCacheProvider`：它只承载通用配置装配，必须无状态，且不得被 `Cache` / `Counter` 的实现类继承。
- `CacheUtil` / `CounterUtil` 镜像 `Cache` / `Counter` 的**全部业务方法**（`namespace()` 除外）：`Duration` 与 `int ttlSeconds` 双重重载、省略 TTL 的写重载，以及 `exists` / `remove` / `getTtl` / `cacheNames()` / `counterNames()` / `ttl()` / `cacheNull()` / `clear()`；`namespace()` 刻意不镜像，门面完整性（含「不得补齐」的反向用例）由 `FacadeMirrorTest` 钉住。

### 契约

- `CaffeineCache` / `RedisCache`、`CaffeineCounter` / `RedisCounter` 必须遵循相同的接口契约与公共行为。
- 未经设计决策，不得自行确定空值语义、过期语义、序列化格式、异常类型或 key 规则。
- **名字与命名空间不得含 `:`**（名字 = `cacheName` / `counterName`）：它是物理 key 的分段符，进入名字会造成层级歧义。名字违规时写路径抛 `IllegalArgumentException`、读路径按未命中处理；`namespace` 违规时在配置解析期（`Config.resolveNamespace`）直接失败，不进入读 / 写路径。`key` 不受此限。它与实例名的既有合法性规则同源，判定只在 `internal.CacheValidator` 一处。
- TTL 上限为 `Integer.MAX_VALUE` 秒，超限不截断、直接抛 `IllegalArgumentException`。
- 以下都属**数据格式**，发布后不得更改：哨兵类型 `internal.NullValue` 的包名与枚举名、`internal.Config` 的 `COUNTER_KEY_PREFIX` / `CACHE_NAMES_NAME` / `COUNTER_NAMES_NAME`、物理 key 格式、`FuryRedisValueCodec` 的 5 个 builder 参数（含 `requireClassRegistration(false)`）。
- 不得把 `FuryRedisValueCodec` 的 `requireClassRegistration` 改成 `true` —— 实测会改变写入字节格式，破坏存量数据与滚动升级期的混合读取。
- 配置解析与校验一律在 `CachePlugin.start()` 内完成：构造函数只保留 `Prop` 引用，不解析、不校验、不创建实例。

### 唯一定义点（每类规则只有一处）

- **接口参数校验**（缓存名 / 计数名 / 键 / TTL / 步长 / 值）与命名空间校验：`internal.CacheValidator`。校验规则不得在实现类里复制。
- **配置域与框架级常量**（配置键名、配置项校验、实例名与 `cache.names`、通用字段解析、键空间保留名与保留前缀）：`internal.Config`。
- **jedis 配置键的分段与拼装**：`redis.RedisConfig` 的 `JEDIS_KEY_SEGMENT` + `jedisKey(prefix, relativeKey)`。读取侧（`RedisCacheProvider`）与报错侧（`RedisConfig` 的 setter，经重写的 `configKey`）都必须走它，不得手写 `"jedis"` 字面量；caffeine 的键分段随其 provider 一处书写，同样不得复制。
- **缓存物理 key 拼装与哨兵编解码**：`internal.AbstractCache`；**计数物理 key 拼装**：`internal.AbstractCounter`。两者都**不经公共接口暴露**：`Cache` / `Counter` 不声明任何 `static` 方法，也不声明任何常量。
- **计数物理 key 的格式**属数据格式（`namespace:COUNTER:counterName:key`，保留前缀常量是 `internal.Config.COUNTER_KEY_PREFIX`）；但**计数名称集合（`counterNames()`）的登记与 `clear(counterName)` 的遍历没有跨实现定义点** —— 两后端的存储载体不同，一律留在实现类，不得下沉到 `internal.AbstractCounter`。
- **启动期排他判定的唯一名**：能力接口 `internal.UniqueNameAware`（实现即声明「我所在存储的身份」，不实现即不参与排他）。`CacheManager.register` 据此按「唯一名 + `namespace`」判重，冲突抛 `IllegalArgumentException`；不得把判据简化成只看 `namespace` 或 `type`。jedis 的取值定义点是 `redis.RedisConfig#uniqueName()`，实现类只做转发。
- **Fury 反序列化白名单**：唯一定义点在 `redis.FuryRedisValueCodec`，拒绝项先于放行项判定，框架自身类型（哨兵 `NullValue`）与**数组维度**在类内登记，业务类型由根级 `cache.jedis.allowedPackages` 声明。数组只放行**维度**、不放开**元素**：元素类仍逐个走白名单，不得为「修数组」而放宽拒绝项，也不得让数组形态绕过元素判定。
- **后端扩展点**：`internal.CacheProvider` + `internal.CacheProviderRegistry` + `internal.AbstractCacheProvider`。新增后端 = 新增 provider 实现 + 在注册表静态初始化里注册一行，不得回头改 `CachePlugin` 的装配逻辑；类型白名单由 `CacheProviderRegistry#types()` 派生。
- **回源单飞的登记与「回源 + 回写」**：`internal.SingleFlight`，登记键取自 `AbstractCache.physicalKey`；实现类不得自行维护在途状态或复制该流程。
- **`keys` 的筛选、排序与分页**：`internal.KeyRanges`（按业务 key 匹配、字典序排序、offset/limit 分页），两后端实现共用，不得各自复制。
- **`CacheProvider` 实现必须无状态**：不得声明实例字段保存解析结果，配置在 `buildCache(instanceName, prefix, prop)` 内经 `buildConfig(cfg, prefix, prop)` 现读现用；配置对象是本实例的瞬时对象，不得存进 provider 字段、不得跨阶段传递。装配共用逻辑走 `AbstractCacheProvider#initConfig`；**缓存实例由各子类在自己的 `buildCache` 内创建**，基类不提供实例创建钩子。

## 注释规范

- **语言**：一律简体中文。保留英文原文的只有 `@param` / `@return` / `@throws` 等 javadoc 标签名、代码标识符、配置键名、异常消息。
- **javadoc**（`/** ... */`）：公共类型与公共成员必须书写，并写全 `@param` / `@return` / `@throws`；`internal` 与包内可见成员在「非自明」时书写。公共面（根包 6 类型）不得出现 `internal` 包的行话（哨兵、物理 key、唯一名、回源单飞、唯一定义点等）。
- **区块分隔注释**（`// ─────────── 分组名 ───────────`）：只用于类内成员分组与「唯一定义点」标记，一句话说明这一组做什么，不描述实现过程。
- **单行注释**（`//`）：只在代码本身无法表达「为什么」时使用，紧贴相关语句，不复述下一行代码；不写行尾注释。
- **标注词** `【实现专用】` / `【数据格式】` 只用于 `internal` 包，公共面不得出现。
- **一致性**：注释必须与当前实现一致。细节说明的唯一定义点在各接口，转发层只写摘要并指向接口方法，不复述细节段落，避免两处各自漂移。
- **公共说明外提、成员只留摘要**：命名与空白校验、TTL 范围、返回值、异常契约、`null` 语义、带 `loader` 的读取约束、名称集合语义等**跨方法共用**的说明，文档侧唯一定义点在 `README.md` 的「接口通用规则」一节；接口与实现类的成员 javadoc 只写方法摘要与自身差异点，`@throws` 只写异常类型、不展开触发条件，委托重载的异常说明由被委托的方法承载。新增公共成员时若引入新的通用规则，先补进该节，再在成员上引用。
- 文件头版权块：原创文件按「包名与版权」约束使用统一版权头，导入/复制文件保留原版权块；既有历史版权块不回溯改写。

## 工作流程

1. **读设计**：确认需求在 `docs/design.md` 中有依据；没有依据则先补决策。
2. **改实现**：满足接口契约与上述定义点，不越出「结构」一节划定的基类边界。
3. **补测试**：新增或改变公共行为时同步更新测试。
4. **跑验证**：`mvn clean verify` 必须 0 failure / 0 error；Redis 相关改动额外跑 `-Dredis.integration=true -Dredis.uri=redis://127.0.0.1:6379/0`，开启后连不上必须失败。
5. **同步文档**：行为变化同步 `README.md`（用法 / 配置）与 `docs/design.md`（契约）。

## 文档维护

- 术语必须一致：实例名 / 缓存名 / 命名空间 / 物理 key / 主缓存 `__MAIN__` / 扩展实例 / 哨兵值。
- 交叉引用使用相对路径或章节标题，不用易失效的裸章节号。
- 条款须区分**冻结契约**（已发布、不得变更）与**可自主演进**两类，并在注释或文档中标注清楚。

## 批量改写与脚本约定

- 批量改写一律用**一次性脚本**：整文件读入内存 → 逐处断言命中次数 → 全部通过才单次写回。**不得对同一文件并发多次编辑**（后写覆盖先写，工具仍逐个报成功）。
- 优先用 Python；脚本与中间产物放临时目录，不要把中间稿留在仓库里，构建日志不得重定向进 `target/`。
- **保持文件原有行尾**：工作区文本文件为 CRLF（`core.autocrlf=true`），脚本按字节读写，不得把它归一成 LF。
- 在 PowerShell 下：脚本正文只用 ASCII（无 BOM 的 `.ps1` 按 GBK 解码，中文会乱码并吞掉换行），且 `-D` 形式的 Maven 参数要整体加引号，否则会在冒号处被切断。

## 提交前检查清单

- [ ] `mvn clean verify` 通过（0 failure / 0 error）。
- [ ] 涉及公共行为的改动已同步 `docs/design.md` 与 `README.md`；新增/变更行为有测试覆盖。
- [ ] 校验规则仍只有两个定义点，实现类未复制规则。
- [ ] 回源单飞的登记与「回源 + 回写」仍只在 `internal.SingleFlight`；实现类未自行维护在途状态、未复制该流程。
- [ ] 新增后端只动了 provider + 注册一行；provider 仍无实例字段。
- [ ] `Cache` / `Counter` 实现类仍只共用 `internal.AbstractCache` / `internal.AbstractCounter`，未暴露实现类型，未破坏 Java 8 兼容与 `optional` 隔离。
- [ ] 新增/改动的注释符合「注释规范」：公共面无 `internal` 行话，转发层未复述接口细节，跨方法共用的说明只在 `README.md`「接口通用规则」一处、成员上未重复。
- [ ] `git status` 无「已暂存但工作区已删」（`AD`）的残留。
- [ ] 当日工作日志已追加到仓库根的协作记忆目录。
