# aifei-sa-token 测试文档

> 关联：`docs/design.md`（设计契约）、`README.md`（用法）

## 1. 测试策略

| 层次 | 手段 | 覆盖目标 |
|---|---|---|
| 架构门禁 | 源码文本断言 | 分层边界不被突破（`cn.aifei.aop.*`、`io.undertow.*`、`cn.hg.aifei.cache.*` 的包归属） |
| 单元测试 | 纯 JVM，无网络、无数据库 | 配置装载、路径匹配、鉴权决策、异常转换、上下文生命周期、持久层映射 |
| 端到端 | 由消费方应用自行验证 | 真实 HTTP 请求链路（本模块不含 demo） |

**不引入真实 Undertow 容器**：桥接层的构造依赖 `HttpServerExchange`，其可构造性在不同 Undertow 版本间不稳定。因此桥接层的验证退化为：

- 用可空的 exchange 驱动 `SaTokenUndertowDispatcher`，断言**上下文装配与清理的生命周期**（这部分与 exchange 内容无关）；
- 桥接对象的方法体由「编译期类型检查 + 消费方端到端」共同保证。

**不引入真实 Redis**：`SaTokenDaoForCache` 只依赖 `Cache` 接口，测试用内存桩实现该接口（复刻 `keys` 的「筛选 + 字典序排序 + 分页」语义）。因此超时换算、key 映射与搜索分页可被完整覆盖，而测试不需要任何外部服务。

两处取舍都是显式的：本模块不做假绿的桥接单测，把真正易错的部分（生命周期、语义换算）测实。

## 2. 运行方式

```bat
cd aifei-sa-token
mvn clean test
```

**必须 0 failure / 0 error**。

## 3. 用例清单

### 3.1 `KernelBindingTest`（架构门禁，4 例）

| 用例 | 断言 |
|---|---|
| `aopImportOnlyInAifeiPackage` | `import cn.aifei.aop.` 仅出现在 `cn/aifei/satoken/aifei/` |
| `undertowImportOnlyInUndertowPackage` | `import io.undertow.` 仅出现在 `cn/aifei/satoken/undertow/` |
| `cacheImportOnlyInDaoPackage` | `import cn.hg.aifei.cache.` 仅出现在 `cn/aifei/satoken/dao/` |
| `noArchOrSqlReference` | 全源码无 `cn.aifei.vip.`、`java.sql.` |

扫描根为 `src/main/java`（相对路径），故**须在模块目录内执行**，否则用例直接失败并提示。

### 3.2 `SaTokenConfigLoaderTest`（5 例）

| 用例 | 断言 |
|---|---|
| `mapsConfiguredKeysToConfig` | `sa-token-test.txt` 中 18 个键全部映射到对应 getter |
| `keepsSaTokenDefaultsWhenKeysAbsent` | 空配置下保持 Sa-Token 官方默认值 |
| `failsFastOnInvalidNumber` | 数值非法抛 `IllegalArgumentException`，消息含出错键名 |
| `failsFastOnInvalidBoolean` | 布尔非法抛 `IllegalArgumentException`，消息含出错键名 |
| `rejectsNullProp` | 传入 null 立即拒绝 |

### 3.3 `PathPatternMatcherTest`（9 例）

| 用例 | 断言 |
|---|---|
| `singleStarMatchesExactlyOneSegment` | `*` 不跨层级、不匹配缺省段 |
| `doubleStarMatchesAnyDepthAndMayBeAbsent` | `/a/**` 匹配 `/a`、`/a/b`、`/a/b/c`，不匹配 `/ab` |
| `wholeMatchIsAnchored` | 匹配为全串锚定，非前缀匹配 |
| `regexMetaCharactersAreEscaped` | `.` 按字面量处理 |
| `pathVariableMatchesSingleSegment` | `{id}` 不跨层级 |
| `crossLayerPathVariableMatchesAnyDepth` | `{path_}` 跨层级 |
| `questionMarkMatchesSingleCharacter` | `?` 匹配单字符且不跨层级 |
| `sameExpressionReusesCompiledInstance` | 同表达式返回同一实例（缓存生效） |
| `rejectsBlankExpression` | 空表达式抛出异常 |

### 3.4 `SaTokenInterceptorTest`（8 例）

| 用例 | 断言 |
|---|---|
| `invokesActionWhenPathIsExcluded` | 命中 exclude 时不执行鉴权并放行 |
| `invokesActionWhenIncludeNotMatched` | 有 include 规则但未命中时放行 |
| `invokesActionWhenAuthPasses` | 命中 include 且鉴权通过后进入 action |
| `convertsAuthFailureIntoHandlerResult` | 鉴权失败不进入 action，返回值来自 `SaTokenErrorHandler` |
| `returnsBackResultCarriedByException` | `BackResultException` 的 `result` 作为 action 返回值 |
| `propagatesNonSaTokenException` | 非 Sa-Token 异常继续向上传播，不被吞掉 |
| `ruleListsAreUnmodifiable` | 规则列表对外只读 |
| `rejectsNullErrorHandler` | 构造时拒绝 null 策略 |

用例通过 `SaTokenContextForThreadLocalStaff` 装配上下文，并以 `StubRequest` 提供请求路径，**不依赖真实 HTTP 栈**。

### 3.5 `SaTokenUndertowDispatcherTest`（4 例）

| 用例 | 断言 |
|---|---|
| `bindsContextDuringDispatchAndClearsAfterwards` | 分发期间上下文有效，分发后已被清理 |
| `clearsContextEvenWhenDelegateThrows` | delegate 抛异常时仍清理上下文，且异常继续传播 |
| `defaultErrorHandlerToleratesMissingExchange` | 无请求上下文时默认策略返回 null 且不抛异常 |
| `rejectsNullDelegate` | 构造时拒绝 null delegate |

### 3.6 `SaTokenDaoForCacheTest`（纯缓存形态，18 例）

| 用例 | 断言 |
|---|---|
| `storesAndReadsStringWithTimeout` | 字符串写入后可按同一 key 读回，TTL 按秒换算 |
| `neverExpireTimeoutMapsToCacheMaximumTtl` | `timeout = -1` 换算为 aifei-cache 最长有效期，`getTimeout` 回报 `NEVER_EXPIRE` |
| `nonPositiveTimeoutSkipsWrite` | `timeout = 0` 与 `<= -2` 均不写入 |
| `deleteRemovesEntry` | 删除后读取为 null |
| `getTimeoutReportsAbsentEntry` | 无该项时返回 `NOT_VALUE_EXPIRE` |
| `updateTimeoutResetsTtlWithoutTouchingValue` | 改 TTL 不影响值 |
| `updateTimeoutWithNeverExpireMapsToMaximumTtl` | `updateTimeout(-1)` 后 `getTimeout` 回报 `NEVER_EXPIRE` |
| `storesAndReadsObject` | 对象写入与按类型读回 |
| `getObjectWithWrongTypeFails` | 按不匹配类型读取抛 `ClassCastException` |
| `sessionRoundTrip` | 会话写入、读回（含内部数据）、删除 |
| `sessionTimeoutDelegatesToObjectTimeout` | 会话超时读写委托对象超时 |
| `updateKeepsRemainingTtl` | 更新值后剩余有效期不变 |
| `updateSkipsAbsentEntry` | 更新不存在的项时不重建 |
| `searchDataFiltersByPrefixAndKeyword` | 前缀与关键字双条件过滤 |
| `searchDataReversesWhenSortTypeIsFalse` | `sortType = false` 时结果反转 |
| `searchDataPaginatesAndToleratesOutOfRangeStart` | 分页、`start` 越界、负 `start` |
| `searchDataWithNoMatchReturnsEmpty` | 无命中返回空集合 |
| `rejectsNullCache` | 构造时拒绝 null 缓存 |

用例以内存桩承担 `Cache` 角色，**不依赖 Redis 或 aifei-cache 插件**。

### 3.7 `SaTokenDaoForCacheDelegateTest`（缓存加速 + 权威委托，15 例）

| 用例 | 断言 |
|---|---|
| `readsThroughToDelegateAndBackfillsCache` | 缓存未命中回源委托层，并把结果回填缓存 |
| `backfilledTtlComesFromDelegate` | 回填所用 TTL 取自委托层的剩余超时 |
| `servesFromCacheWithoutHittingDelegate` | 缓存命中时不再访问委托层 |
| `missesReturnNullWhenBothLayersEmpty` | 两层皆无数据时返回 null |
| `writesThroughToBothLayers` | 写入同时落到委托层与缓存 |
| `updatePropagatesToBothLayers` | 更新同时作用于两层 |
| `deletesFromBothLayers` | 删除同时作用于两层 |
| `timeoutComesFromDelegate` | 超时读取以委托层为准 |
| `updateTimeoutAppliesToBothLayers` | 超时修改同时作用于两层 |
| `searchDataComesFromDelegate` | 搜索结果完整来自委托层 |
| `objectReadsThroughAndBackfills` | 对象回源回填，并支持按类型读取 |
| `objectWritesThroughToBothLayers` | 对象写穿 |
| `sessionReadsThroughAndBackfills` | 会话回源回填 |
| `sessionWritesAndDeletesThroughBothLayers` | 会话写穿与删穿 |
| `sessionUpdateKeepsAuthorityValue` | 会话更新后两层值一致 |

委托层使用 Sa-Token 内置的内存实现 `SaTokenDaoDefaultImpl`，使两侧语义都是真实的 Sa-Token 契约，而非测试自造的近似；超时断言带 1 秒容差（委托层按秒计算剩余时间）。

## 4. 回归要求

- 已修复的缺陷必须固化为回归测试；
- 新增或改变公共行为时，同步更新本文件与 `docs/design.md`；
- 门禁测试不得放宽断言来「兼容」错误用法。
