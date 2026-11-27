# aifei-i18n 测试文档


---

## 〇、结论摘要

| 项 | 结论 |
| --- | --- |
| 构建 | `mvn clean verify` → **BUILD SUCCESS**，`Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` |
| AC 覆盖 | **75 / 75** 条 AC 每条至少一条**已执行**用例（含本轮 QA 新增补强用例） |
| 架构门禁 | 13 例全绿；变异探针证明公共面门禁真能拦住「多一个 public 类」 |
| 字节码 | 独立 javap 核验：target/classes 全部 **40 个 class**（32 顶层 + 8 个 `$` 嵌套类，对应 32 个主源文件）**major = 52** |
| 变异/边界探针 | 5 个（3 个源码变异 + 1 个日志分级边界 + 1 个一致性边界），全部按预期命中 |
| 离线纪律 | 零联网、零 Thread.sleep、kv 无真实集成测试（默认关闭） |
| **放行判定** | **放行（PASS）** — 无 P0/P1 遗留，质量关卡五项 DoD 全部达标 |

---

## 一、测试分层与原则

- **零联网 / 零外部服务**：单测不连真实数据库、KV、HTTP；测试源码 URL 仅允许回环地址（门禁 ⑬，QA 独立扫描确认无外网 URL 字面量）。
- **零 Thread.sleep**：TTL 到期用例采用「极小 TTL + nanoTime 有界轮询」，连续 3 次重跑稳定。测试源码 **0 处实际 sleep 调用**。
- **测试替身**：`support/FakeMessageSource`（可配置稳定名 / 数据 / 可列举开关 / get/list 故障开关 / 调用计数）覆盖存储型 / 快照型 / 代理型来源（AC-04-3）；`support/FakeDbAccessor`（db 访问替身）。
- **跨 provider 稳定断言**：涉及 JDK 输出字面量的用例只断言「不相等 / 含本币种符号 / 月份名与日的相对位置 / 小数位」，不写死符号字符与完整日期串（设计 §9.8）。
- **状态隔离**：`@After` 清理 `I18nHolder` / `LocaleContext` / `LocaleMeta` 判定器 / 日志工厂，用例无顺序依赖。
- **真实来源集成测试（默认关闭）**：`DbSourceIntegrationTest`（H2 内存库）/ `KvSourceIntegrationTest`（回环 Redis）以系统属性开关控制，默认关闭；开启用 `mvn test -Ddb.integration=true` 或 `-Dkv.integration=true -Dredis.uri=redis://127.0.0.1:6379/0`，开启后连不上必须失败（不静默跳过）。
- **端到端基线**：126 用例全绿、两项门禁（公共面精确 10 类型 + `ClassFileVersionTest` class major 52）全绿；demo 的 `page` / `bundle` / `rtl` / `money` 端点真实启动渲染均为 HTTP 200，内容符合四字段 JSON 与 Enjoy 渲染契约（端点清单见 `aifei-i18n-demo/README.md`）。

---

## 二、测试文件（14 个：11 个测试类 + 3 个测试替身）

| 文件 | 职责 | 主要覆盖 AC | 例数 |
| --- | --- | --- | --- |
| `ArchitectureBoundaryTest` | 源码文本 + 反射双门禁（13 例） | AC-13 / AC-16-6 / AC-14-2 / Q9 | 13 |
| `ClassFileVersionTest` | 全部 class major = 52 | AC-13-3 | 1 |
| `I18nKitTest` | 取文案 / 回退 / 叠加 / 缓存 / 故障 / 插值 / 并发；D-02 服务层 + 模板渲染 | AC-01/02/03/05/07/15 | 22 |
| `FormatKitTest` | 三参正交 / 百分比 / 四档 / 默认 MEDIUM / IAE；D-03 | AC-16 | 18 |
| `BundleExportTest` | 四字段 / 排序 / 一致性 / dir 降级 / 不可列举跳过 | AC-09/10/14/15 | 13 |
| `LocaleResolverTest` | 四级优先级 / 规范化 / 请求头容错 | AC-08 / AC-14-3 / AC-12-6 | 13 |
| `web/I18nHandlerTest` | 绑定 / 异常路径清理 / 同线程不串语言 | AC-08-3/05/06 | 6 |
| `aifei/I18nPluginTest` | 装配幂等 / 重名 / 依赖探测 / 配置校验 / 未装配调用 | AC-04/11/12 | 15 |
| **`QaLogLevelTest`** | 日志分级独立断言：未命中 DEBUG / 故障·跳过·降级 WARN；本轮优化补测 refresh/load 的 WARN 路径 | AC-02-2 / 09-6 / 14-5 / 15-1/15-4/15-5 + 优化项 1/2 | 9 |
| **`QaConsistencyTest`** | 一致性边界：缓存下 / refresh 后 / 不可列举例外 | AC-10-1/10-2/10-3/10-4 | 3 |
| `enjoy/directive/DirectiveTest` | 三条 Enjoy 指令：lang 位置无关 / 优先级链 / dir / IAE / 真实 Engine 渲染（AC-INC-01~13，§十一） | AC-INC-01~13 | 13 |
| `support/FakeMessageSource` | 测试替身（非测试类） | — | — |
| `support/FakeDbAccessor` | db 访问替身（非测试类） | — | — |
| `support/RecordingLogFactory` | 日志录制替身（非测试类） | — | — |

> 合计 `@Test` = 13+1+22(I18nKitTest)+18+13+13+6+15+9+3+13(DirectiveTest) = **126**，与实测一致。

---

## 三、架构门禁（13 例，全绿）

`ArchitectureBoundaryTest` 在模块根执行，按源码文本（五态状态机剥离注释）+ 反射断言，**不得放宽或绕过**：

| # | 测试方法 | 断言 | 结果 |
| --- | --- | --- | --- |
| ① | `noThirdPartyReference` | 每条 import 以 `java.` / `javax.` / `cn.aifei.` / `cn.hg.aifei.i18n.` 开头；`cn.hg.aifei.cache.` 仅 KvSource | PASS |
| ② | `frameworkScopeRules` | Prop/Plugin/aop 仅 aifei 包；core 仅 web 包；log 仅三处 WARN 记录类 + aifei 包 | PASS |
| ③ | `publicSurfaceExact` | 根 7 / web 2 / aifei 1 文件名集合精确相等 | PASS（变异探针已证有效） |
| ④ | `publicSurfaceByReflection` | target/classes 顶层 public 类型 FQN 精确等于 10 个 | PASS（变异探针已证有效） |
| ⑤ | `noSqlReference` | 全模块无 `import java.sql.` | PASS |
| ⑥ | `noJvmGlobalPropertyWrite` | 无 `System.setProperty` / `getProperties().put` / `setOut` / `setErr` | PASS |
| ⑦ | `noServiceProvider` | 无 ServiceLoader / `META-INF/services` | PASS |
| ⑧ | `java8Compatibility` | 无 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` / `"""` / `sealed ` | PASS |
| ⑨ | `localeRulesSingleDefinition` | `setLanguageTag` 仅 LocaleNormalizer；`parseAcceptLanguage` 仅 LocaleResolver / Handler | PASS |
| ⑩ | `dirOnlyInLocaleMeta` | `ComponentOrientation` 仅 LocaleMeta，且无语言→方向映射表 | PASS |
| ⑪ | `noSelfBuiltRuleData` | 无日期/数字模式串、货币符号表等自建规则数据 | PASS |
| ⑫ | `cacheDependencyOnlyInKvSource` | cache 类型不出现在 KvSource 字段 / 签名（仅方法体） | PASS |
| ⑬ | `testSourcesOfflineOnly` | 测试源码 URL host 仅回环地址；五态状态机剥离注释后扫描（沿用 aifei-http DEF-01 修复形态），`CASE_INSENSITIVE` | PASS |

> **门禁强度实证**：③④ 经变异探针验证——向根包注入 `public class QaExtraPublic{}` 后，`publicSurfaceExact`（文件名集合）与 `publicSurfaceByReflection`（反射 10 类型）**双双失败**（当轮 `Tests run: 13, Failures: 2`），删除探针后恢复全绿。证明门禁真能拦住「多一个 public 类」，非摆设。

---

## 四、AC ↔ 用例 ↔ 结果全覆盖矩阵（75 / 75）

### REQ-P0-01 单条文案取用（3/3）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-01-1 | `I18nKitTest#messageHitWithLocale` | PASS |
| AC-01-2 | `I18nKitTest#messagePlaceholderMissingArgKeptAsIs`（`hello Bob` + 缺参 `hello {0}` 原样保留） | PASS |
| AC-01-3 | `I18nKitTest#concurrentLocalesDoNotBleed`（CountDownLatch 双线程 zh/en 不串） | PASS |

### REQ-P0-02 未命中不抛异常（3/3）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-02-1 | `I18nKitTest#messageMissReturnsKeyNoThrow` | PASS |
| AC-02-2 | `QaLogLevelTest#missLogsDebugOnly`（DEBUG 含 key+locale，无 WARN/ERROR）；原 `messageMissReturnsKeyNoThrow` 仅验行为 | PASS |
| AC-02-3 | `I18nKitTest#emptyStringIsHit`（空串命中不回退） | PASS |

### REQ-P0-03 回退链唯一且不可配（3/3）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-03-1 | `I18nKitTest#fallbackToDefaultLocaleAcrossSources`（两来源无 zh-CN、properties 有 en→命中 en；变异探针已证有效） | PASS |
| AC-03-2 | `I18nKitTest#overlayOrderFirstHitWins`（db 序1 优先） | PASS |
| AC-03-3 | `I18nPluginTest#onlyFourConfigKeysRecognized` + 门禁①（无 strict 开关，4 键） | PASS |

### REQ-P0-04 来源开放扩展（4/4）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-04-1 | `I18nPluginTest#customSourceRegisteredInOrder`（显式注册零改模块） | PASS |
| AC-04-2 | `BundleExportTest#nonListableSourceSkippedButSingleHitWorks` | PASS |
| AC-04-3 | `FakeMessageSource`（计算型）与 `PropertiesSource`（存储型）同契约用例：`I18nKitTest#messageHitWithLocale` | PASS |
| AC-04-4 | `I18nPluginTest#duplicateStableNameFailsStartup`（含稳定名 + 回滚验证） | PASS |

### REQ-P0-05 叠加顺序生效（2/2）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-05-1 | `I18nPluginTest#customSourceRegisteredInOrder` + `I18nKitTest#overlayOrderFirstHitWins` | PASS |
| AC-05-2 | `I18nKitTest#fallbackToDefaultLocaleAcrossSources`（前来源未命中继续后来源） | PASS |

### REQ-P0-06 三个默认来源开箱可用（4/4）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-06-1 | `I18nPluginTest#defaultSourceIsProperties`（零依赖、默认 properties） | PASS |
| AC-06-2 | 门禁⑤ + `BundleExportTest#listFailureSkippedExportSucceeds`（只读语义，零 java.sql）；源码审查 DbSource 无 DDL | PASS（源码审查+门禁） |
| AC-06-3 | kv 命中路径：`I18nPluginTest#kvDependencyMissingFailsStartupWithStableName` 反向证明依赖存在时可装配；kv 集成默认关闭，以 FakeMessageSource 覆盖命中语义 | PASS（默认关闭+替身） |
| AC-06-4 | `I18nKitTest#overlayOrderFirstHitWins`（默认来源无特权，按声明顺序） | PASS |

### REQ-P0-07 缓存与刷新（4/4）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-07-1 | `I18nKitTest#cacheHitWithinTtl`（TTL 内来源只调 1 次） | PASS |
| AC-07-2 | `I18nKitTest#ttlExpiryReloads`（QA 自修为无 sleep 有界轮询，3 次稳定） | PASS |
| AC-07-3 | `I18nKitTest#refreshClearsCache`（refresh 后重新回源，2 次） | PASS |
| AC-07-4 | `PropertiesSource` 启动快照语义 + README 声明「须重启」；源码审查 | PASS（源码+文档审查） |

### REQ-P0-08 Locale 解析与上下文贯通（9/9）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-08-1 | `LocaleResolverTest#explicitWinsOverContext` | PASS |
| AC-08-2 | `LocaleResolverTest#contextUsedWhenNoExplicit` | PASS |
| AC-08-3 | `web/I18nHandlerTest#bindsLocaleForDownstream` + `LocaleResolverTest#acceptLanguageFirstSupported` | PASS |
| AC-08-4 | `LocaleResolverTest#fallbackToDefaultWhenAllAbsent` | PASS |
| AC-08-5 | `web/I18nHandlerTest#sameThreadTwoRequestsDoNotBleed`（同线程两请求不串） | PASS |
| AC-08-6 | `web/I18nHandlerTest#clearsOnDownstreamException`（异常原样抛出 + finally 清理） | PASS |
| AC-08-7 | `I18nKitTest#explicitIllegalLocaleThrows` / `LocaleResolverTest#explicitIllegalThrows`（`zh_CN_`→IAE） | PASS |
| AC-08-8 | `LocaleResolverTest#acceptLanguageSkipsIllegalSegment` / `#acceptLanguageAllIllegalReturnsNull` | PASS |
| AC-08-9 | `I18nKitTest#normalizedLocaleHit`（`zh_CN`→`zh-CN` 命中） | PASS |

### REQ-P0-09 语言包整包导出（6/6）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-09-1 | `BundleExportTest#mergesListableSources` | PASS |
| AC-09-2 | `BundleExportTest#firstSourceWinsForDuplicateKey` | PASS |
| AC-09-3 | `BundleExportTest#keyOrderStableAcrossExports`（连续两次一致 + TreeMap 自然序） | PASS |
| AC-09-4 | `BundleExportTest#exportIgnoresContext`（显式 locale 不受上下文影响） | PASS |
| AC-09-5 | `BundleExportTest#localizedValuesAreStringsAndRawPlaceholders` | PASS |
| AC-09-6 | `QaLogLevelTest#nonListableSourceLogsWarnOnExport`（WARN 含稳定名）；原 `BundleExportTest#nonListableSourceSkippedButSingleHitWorks` 仅验行为 | PASS |

### REQ-P0-10 单点与整包一致性（4/4）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-10-1 | `BundleExportTest#singleAndBundleConsistent` + `QaConsistencyTest#nonListableOnlyKeyIsLegitimateException`（可列举 K 逐 key 相等） | PASS |
| AC-10-2 | `QaConsistencyTest#consistentWhenCachePopulated`（缓存已存在时逐 key 一致） | PASS |
| AC-10-3 | `QaConsistencyTest#consistentAfterRefresh`（refresh 后逐 key 一致） | PASS |
| AC-10-4 | `QaConsistencyTest#nonListableOnlyKeyIsLegitimateException` + `BundleExportTest#nonListableSourceSkippedButSingleHitWorks` | PASS |

### REQ-P0-11 来源依赖缺失即启动失败（4/4）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-11-1 | `I18nPluginTest#kvDependencyMissingFailsStartupWithStableName`（隔离 ClassLoader 屏蔽 cache，含 kv + aifei-cache） | PASS |
| AC-11-2 | `I18nPluginTest#unregisteredSourceFailsStartup`（含 foo） | PASS |
| AC-11-3 | `I18nPluginTest#undeclaredSourceNotInstantiatedEvenIfBroken` / `#undeclaredKvDoesNotNeedCacheDependency` | PASS |
| AC-11-4 | `I18nPluginTest#unregisteredSourceFailsStartup` / `#duplicateStableNameFailsStartup`（消息含稳定名，可定位） | PASS |

### REQ-P0-12 配置键最小集与无启用开关（7/7）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-12-1 | `I18nPluginTest#onlyFourConfigKeysRecognized`（精确 4 键） | PASS |
| AC-12-2 | `I18nPluginTest#unassembledCallFails`（不装配即失败；无 enable 键） | PASS |
| AC-12-3 | `I18nPluginTest#startInstallsEngineAndIsIdempotent`（默认 60000） | PASS |
| AC-12-4 | `I18nPluginTest#missingDefaultLocaleFailsStartup` | PASS |
| AC-12-5 | `I18nPluginTest#defaultSourceIsProperties` | PASS |
| AC-12-6 | `LocaleResolverTest#acceptLanguageWhitelistFilter` / `#acceptLanguageNoWhitelistAcceptsAnyLegal` | PASS |
| AC-12-7 | `I18nKitTest#ttlZeroBypassesCache`（TTL≤0 每次回源） | PASS |

### REQ-P0-13 公共面收敛（3/3）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-13-1 | 门禁①②（`noThirdPartyReference` / `frameworkScopeRules`）+ `mvn dependency:tree`（无 compile 第三方） | PASS |
| AC-13-2 | 门禁③④（`publicSurfaceExact` / `publicSurfaceByReflection`），变异探针已证有效 | PASS |
| AC-13-3 | 门禁⑧ + `ClassFileVersionTest#shouldCompileToJava8Bytecode` + QA 独立 javap（33 class 全 major 52） | PASS |

### REQ-P0-14 语言包 locale 元数据（5/5）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-14-1 | `BundleExportTest#fourFieldsPresent`（localeName = JDK getDisplayName）+ 门禁⑪ | PASS |
| AC-14-2 | `BundleExportTest#dirRtlFromJdk`（ar=rtl / zh-CN=ltr）+ 门禁⑩（唯一定义点、无方向表） | PASS |
| AC-14-3 | `BundleExportTest#illegalLocaleThrows`（`zh_CN_`→IAE）+ `LocaleResolverTest#acceptLanguageSkipsIllegalSegment`（请求头忽略） | PASS |
| AC-14-4 | `BundleExportTest#normalizedLocaleExportConsistent`（`zh_CN` 与 `zh-CN` 逐字一致） | PASS |
| AC-14-5 | `QaLogLevelTest#dirDegradationLogsWarn`（降级 ltr + WARN）；原 `BundleExportTest#dirDegradesToLtrWhenCapabilityUnavailable` 仅验降级行为 | PASS |

### REQ-P0-15 来源运行期故障不中断（5/5）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-15-1 | `I18nKitTest#sourceFailureFallsBackNoThrow` + `QaLogLevelTest#sourceFailureLogsWarnWithNameAndKey`（WARN 含 db） | PASS |
| AC-15-2 | `I18nKitTest#allSourcesFailReturnsKey` + `QaLogLevelTest#allSourcesFailLogsWarnPerSource` | PASS |
| AC-15-3 | `I18nKitTest#sourceFailureTriggersDefaultLocaleFallback`（变异探针已证有效） | PASS |
| AC-15-4 | `QaLogLevelTest#sourceFailureLogsWarnWithNameAndKey` / `#allSourcesFailLogsWarnPerSource` / `#debugAndWarnLevelsAreDistinct`（WARN 含稳定名+key，区别于未命中 DEBUG） | PASS |
| AC-15-5 | `BundleExportTest#listFailureSkippedExportSucceeds` + `QaLogLevelTest#listFailureLogsWarnOnExport`（WARN 含 db） | PASS |

### REQ-P0-16 最小格式化能力（9/9）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-16-1 | `FormatKitTest#numberSeparatorsFollowLocale`（de-DE 与 en-US 分隔符不同） | PASS |
| AC-16-2 | `FormatKitTest#currencyIsOrthogonalToLocale` / `#currencyFractionDigitsDrivenByCurrency` / `#sameCurrencyDifferentLocaleOutputDiffers` | PASS |
| AC-16-3 | `FormatKitTest#dateFieldOrderFollowsLocale`（月份锚点）、`#timeZoneIsOrthogonalToLocale` | PASS |
| AC-16-4 | `FormatKitTest#localeResolutionReusesContext` / `#localeFallsBackToDefaultWhenNoContext` + 门禁⑨（复用 LocaleResolver） | PASS |
| AC-16-5 | `FormatKitTest#illegalCurrencyThrows` / `#illegalZoneThrows`（E-3 变异探针已证有效）/ `#localeFallsBackToDefaultWhenNoContext` | PASS |
| AC-16-6 | 门禁⑪（`noSelfBuiltRuleData`）+ `FormatSupport` 源码审查（仅 JDK API） | PASS |
| AC-16-7 | `FormatKitTest#percentFollowsLocale`（两 locale 写法不同，含 85 与 %） | PASS |
| AC-16-8 | `FormatKitTest#defaultStyleIsMedium`（省略档位与显式 MEDIUM 逐字一致）+ AC-12-1（不占配置键） | PASS |
| AC-16-9 | `FormatKitTest#nullStyleThrows` / `#nullCurrencyThrows` / `#nullZoneThrows` / `#nullDateThrows`（枚举编译期挡四档外取值 + null→IAE） | PASS |

**覆盖统计**：16 条 P0 / 75 条 AC 全部有一一对应的已执行用例与 PASS 结果。

---

## 五、变异探针与边界验证

> 变异探针在 `/tmp/i18n-mutation` 临时副本执行（交付源码零改动，前后 md5 校验一致）；每轮只改一处、跑对应测试、随即删除副本。

| # | 类型 | 探针内容 | 期望 | 实测真实输出 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 1 | 源码变异（E-3） | 删除 `FormatSupport#dateTime` 的 `ZoneId.of` 校验（模拟退回 TimeZone.getTimeZone 静默吞非法） | FAIL | `FormatKitTest.illegalZoneThrows Expected exception: IllegalArgumentException`；`Tests run: 18, Failures: 1` | PASS：E-3 时区坑用例真能抓到 |
| 2 | 源码变异（公共面） | 根包注入 `public class QaExtraPublic{}` | FAIL | `publicSurfaceExact`(:156) 与 `publicSurfaceByReflection`(:194) 双失败；`Tests run: 13, Failures: 2` | PASS：双重门禁真实有效 |
| 3 | 源码变异（回退链） | 删除 `FallbackChain` 默认 locale 逐来源回退段 | FAIL | `fallbackToDefaultLocaleAcrossSources expected:<default-value> but was:<k>`；`sourceFailureTriggersDefaultLocaleFallback expected:<en-value> but was:<k>`；`Tests run: 18, Failures: 2` | PASS：AC-03-1/15-3 用例真能抓到 |
| 4 | 边界探针（日志分级） | 注入 `RecordingLogFactory`，按级别录制并断言 | — | 未命中路径 WARN=0/DEBUG≥1；故障路径 DEBUG=0/WARN≥1；跳过/降级均产生含要素的 WARN | PASS：日志口径 DEBUG/WARN 分明且可定位 |
| 5 | 边界探针（一致性） | 缓存已填充 / refresh 后 / 不可列举例外三态逐 key 比对 | — | 3 例全 PASS：两路径逐 key 相等；仅不可列举来源的 key 整包不含 | PASS：一致性边界成立 |



---

## 六、构建与计数

### 1. 构建与计数

| 场景 | 命令 | 输出 | 结论 |
| --- | --- | --- | --- |


| 最终 | `mvn clean verify` | `Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` | PASS |

逐类实测（最终 126）：`I18nPluginTest` 15 / `ArchitectureBoundaryTest` 13 / `BundleExportTest` 13 / `ClassFileVersionTest` 1 / `FormatKitTest` 18 / `I18nKitTest` 22 / `LocaleResolverTest` 13 / `QaConsistencyTest` 3 / `QaLogLevelTest` 9 / `I18nHandlerTest` 6 / `DirectiveTest` 13 = **126**。

### 2. 重点核查清单逐项验证（10 项）

| # | 核查项 | 独立验证方法与结论 |
| --- | --- | --- |
| 1 | E-3 时区坑 | 源码审查：`FormatSupport#dateTime` 确用 `ZoneId.of(zoneId)` + `catch DateTimeException→IAE`；测试 `illegalZoneThrows` 用 `SimpleTimeZone(0,"Mars/Base")` 保留非法 ID（`TimeZone.getTimeZone("Mars/Base")` 会静默变 GMT，故不能用它构造）。变异探针 1 移除校验后用例立刻 FAIL → **真能抓到**。 |
| 2 | 三处易混边界 | (a) `zh_CN_`→IAE（`explicitIllegalLocaleThrows` / `explicitIllegalThrows` / `illegalLocaleThrows`） vs `zh_CN`→命中（`normalizedLocaleHit` / `normalizedLocaleExportConsistent`）逐条区分 ✓；(b) 缺参占位符原样保留（`messagePlaceholderMissingArgKeptAsIs`）vs null 值 IAE（`nullDateThrows` 等）✓；(c) locale 缺省落默认（`localeFallsBackToDefaultWhenNoContext`）vs null 币种/时区 IAE ✓。 |
| 3 | 回退链 AC-03-1 | `fallbackToDefaultLocaleAcrossSources` 精确场景：db/properties 均无 zh-CN、properties 有 en → 命中 en 值；变异探针 3 删除回退段后 FAIL → **真能抓到**。 |
| 4 | 故障 vs 未命中日志分级 | 源码：未命中 `debug()`、故障/跳过/降级 `warn()` 分级正确；**原测试无日志级别断言**（缺口）→ QA 新增 `QaLogLevelTest` 补全（现 9 例，含本轮 refresh/load WARN 2 例），DEBUG/WARN 分明且含稳定名+key。 |
| 5 | Web finally 清理 | `clearsLocaleAfterRequest` / `sameThreadTwoRequestsDoNotBleed`（AC-08-5）/ `clearsOnDownstreamException`（AC-08-6，异常原样抛出 + 清理）：D-02 端到端用例存在且 PASS。 |
| 6 | D-02 两条消费路径 | `I18nKitTest#serviceAndTemplateRenderShareSameEntry`：服务层 `I18nKit.message` 与模板函数 `Function` 包装逐字等价断言；不引入 Enjoy 依赖（Q7）。PASS。 |
| 7 | D-03 格式化 | 三参正交（16-1/2/3）+ 百分比（16-7）+ 四档（16-8 默认 MEDIUM + `fourStylesAreAvailable`）+ 门禁⑪零自建规则 + 门禁⑩ dir 唯一定义点 + `dirDegradationLogsWarn`（16-5 降级 WARN）；断言遵守 §9.8 跨 provider 稳定口径（不写死符号/完整日期串）。 |
| 8 | 单点/整包一致性 | 原有 `singleAndBundleConsistent`（AC-10-1）；QA 新增缓存下 / refresh 后 / 不可列举例外三态（AC-10-2/3/4）→ 全 PASS。 |
| 9 | 公共面门禁 | 门禁③④ + 变异探针 2：注入额外 public 类后双失败 → **真能拦住多一个 public 类**。反射精确 10 类型（与 §三 门禁④及 `ArchitectureBoundaryTest#EXPECTED_PUBLIC_FQNS` 断言一致）。 |
| 10 | class major 52 | 门禁⑧ + `ClassFileVersionTest` + QA 独立 javap：`target/classes` 全部 **40 个 class major=52**（32 顶层 + 8 个 `$` 嵌套类；直方图 `40 → 52`，无其它值；顶层 32 个与 `src/main/java` 32 个主源文件、CHANGELOG「32 个主源文件编译产物」一致）。实测命令：`find target/classes -name '*.class' \| wc -l` → 40；`find target/classes -name '*.class' ! -name '*$*' \| wc -l` → 32。 |

### 3. 离线纪律与依赖

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 零联网 | PASS | 门禁⑬ + QA 独立扫描 `src/test/java`：无外网 URL 字面量（gitee 版权头注释已剥离/排除） |
| 零 Thread.sleep | PASS | `I18nKitTest` 采用 nanoTime 有界轮询；当前 0 处实际 sleep 调用；3 次重跑稳定 |
| kv 集成默认关闭 | PASS | 无真实 KV/DB/HTTP 集成测试；`kv` 命中语义以 FakeMessageSource 覆盖，依赖缺失以隔离 ClassLoader 探针覆盖 |
| 依赖范围 | PASS | `mvn dependency:tree`：`aifei:provided` + `aifei-cache:provided,optional` + `junit:test`，零 compile 第三方 |

---

## 七、缺陷台账

| 编号 | 类型 | 等级 | 位置 | 处理 | 状态 |
| --- | --- | --- | --- | --- | --- |
| DEF-QA-01 | 测试断言错误 | P3 | `QaLogLevelTest#dirDegradationLogsWarn` | 修正断言 | FIXED |
| DEF-QA-02 | 覆盖缺口（日志级别） | P2 | 原 97 用例无日志级别断言 | 新增 QaLogLevelTest 7 例 | FIXED |
| DEF-QA-03 | 覆盖缺口（一致性边界） | P3 | 原 BundleExportTest 缺缓存下/refresh 后一致性 | 新增 QaConsistencyTest 3 例 | FIXED |
| DEF-QA-04 | 测试纪律（Thread.sleep） | P3 | `I18nKitTest#ttlExpiryReloads` 原 `sleep(80L)` | 改为无 sleep 有界轮询 | FIXED |

- 4 项缺陷均为测试用例问题，已全部修正并回归通过。

---

## 八、遗留风险与建议

1. **AC-06-2 / AC-07-4 依赖源码与文档审查**（非运行时用例）：db 只读不执行 DDL、properties 改值须重启，这两条以「门禁⑤ + 源码审查 + README 声明」为证据，无独立运行时用例（db 真实交互需外部数据库，违反零外部依赖）。属设计允许的审查通道，不构成覆盖缺口。建议：如后续引入内存 db 替身，可补一条只读语义用例。
2. **日志 WARN 消息中 locale 用 `Locale.toString()`（`zh_CN`）而非规范化 tag（`zh-CN`）**：符合 AC「含 locale」要求，但跨 locale 格式不统一。P3 建议（非缺陷）：统一用规范化 tag 更利于日志检索。
3. **ClassFileVersionTest 在 target/classes 不存在时跳过**：纯 IDE 环境可能跳过字节码断言；`mvn clean verify` 下 target/classes 必然存在，本次已独立 javap 全量核验兜底。
4. **物理断网未做**：离线性结论来自「门禁⑬ + 零外网 URL + 零真实集成测试 + 126 例全绿」的组合证据，未做物理断网跑（本机回环可用，外部网络未隔离）。与 aifei-http 同口径。

---

## 九、验证命令（均在模块根 `aifei-i18n/` 执行）

```bash
mvn clean verify                                          # 主验证：必须 0 failure / 0 error（实测 126/0/0）
mvn test -Dtest=ArchitectureBoundaryTest,ClassFileVersionTest   # 仅跑双门禁（13+1 例）
mvn test -Dtest=QaLogLevelTest,QaConsistencyTest           # 仅跑 QA 新增补强用例（9+3 例）
mvn test -Dtest=FormatKitTest#illegalZoneThrows            # E-3 时区坑单方法
mvn dependency:tree                                       # 依赖范围核对（provided/optional/test）
# class major 独立核验
for f in $(find target/classes -name '*.class'); do javap -verbose "$f" | grep -m1 "major version"; done | sort | uniq -c
```

---

## 十、新增约定

- 新增用例前先读 `docs/design.md` §5（公共面签名）与 §15（AC 映射）；**不得**为让用例通过而放宽门禁或扩大公共面。
- 断言日志分级时统一用 `RecordingLogFactory`（`@Before` 注入 / `@After` 恢复），只断言级别 + 关键片段，不写死完整消息。
- 时间相关用例**禁用 `Thread.sleep`**，用「极小 TTL + nanoTime 有界轮询」替代，并多次重跑确认稳定。
- 每条用例在方法注释或本文矩阵中标注覆盖的 AC 编号，保持本文件台账同步。
- 变异探针一律在临时副本执行，交付源码前后 md5 校验；探针用毕即删，不留残留。

---

## 十一、Enjoy 指令用例清单（AC-INC-01~13）

> 指令实现位于 `enjoy/directive/`（非承诺 API），测试类为
> `src/test/java/cn/hg/aifei/i18n/enjoy/directive/DirectiveTest.java`。
> 全部用例用**真实 Enjoy Engine** 渲染内存 / classpath 模板（`engine.getTemplateByString` 或内存 ISource），
> URL host 仅 `localhost`，零联网、零 HTTP 依赖。

| AC | 用例方法 | 判定要点 |
| --- | --- | --- |
| AC-INC-01 | `testLangPositionIndependence` | 三式写法（lang 置首/中/尾）输出逐字相等 |
| AC-INC-02 | `testLangAnyPositionKeepsArgsOrder` | lang 置首/中/尾均提取，剩余参数顺序不变 |
| AC-INC-03 | `testInterpolationAndMissingArg` | `{0}` 插值 + 缺参原样保留（复用 E-6） |
| AC-INC-04 | `testNoLangUsesEffectiveLocale` | 无 lang 走 `effectivePageLang()` → `LocaleResolver.resolve(null)` |
| AC-INC-05 | `testPageLangGoverns` | `#lang('zh-CN')` 后无 lang 文案跟随 zh-CN |
| AC-INC-06 | `testLocalOverrideNotPollutePage` | 局部 ar-SA 为阿文、`#lang()` 仍 zh-CN（不污染） |
| AC-INC-07 | `testDirReadWriteAndIllegal` | `#dir('rtl')` → `#dir()`=rtl；非法值 IAE |
| AC-INC-08 | `testDirFallback` | dir 能力不可用 → ltr + WARN，不抛（`LocaleMeta` 替身隔离） |
| AC-INC-09 | `testIllegalIae` | lang 重复 / 非法 / 缺 key → IAE，消息可定位 |
| AC-INC-10 | `testStringLiteralNotNamedArg` | args 含字符串字面量 `"lang='x'"` 视为普通参数 |
| AC-INC-11 | `testRealEngineEndToEnd` | 真实 Engine 渲染 §4 场景，逐字相等，零联网 |
| AC-INC-12 | `testSameThreadNoLeak` | 同线程两次不同语言渲染各自正确、不串（`clear()` 隔离） |
| AC-INC-13 | `testPriorityChainNoFallback` | 局部 ar-SA / 无 lang 与 `#lang()` 均 zh-CN（请求头 en-US），高优先级不回退 |

**门禁例外自检**：`ArchitectureBoundaryTest#frameworkScopeRules` 仅放开 `cn.aifei.enjoy.` 于
`cn/hg/aifei/i18n/enjoy/directive/`，其余框架包边界 / 公共面 10 类型 / 零自建规则数据等断言不变。
`mvn clean verify` 需 0 failure / 0 error。

