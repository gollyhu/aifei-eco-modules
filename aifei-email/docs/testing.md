# aifei-email 测试策略与用例清单

面向实现者与验收者。契约事实来源是 [design.md](design.md)（§7.5 异常语义、§9 门禁、§14 AC 映射），本文只回答「怎么测、测了什么、哪些没测到、怎么复现」。

## 一、测试策略

| 层 | 测试类 | 判据 |
| --- | --- | --- |
| 架构门禁 | `ArchitectureBoundaryTest`（14 例） | 全绿；按源码文本断言分层边界与唯一定义点，**不得删减或放宽** |
| 发送契约 | `SendContractTest`（10 例） | 纯文本 / HTML / 模板 / 附件 / 多通道 / 换配置；结果字段与内核收到内容逐项断言 |
| 装配生命周期 | `PluginLifecycleTest`（7 例） | 启动可用、停止拒绝、双幂等、失败无残留、重装配刷新 |
| 配置校验 | `ConfigValidationTest`（13 例） | 通道发现 / 必填 / 整数 / 默认通道 / Provider 缺失，消息含键名与原值 |
| 依赖缺失 | `DependencyMissingTest`（4 例） | 可读异常、无 `NoClassDefFoundError` 逸出、隔离手段有效 |
| 异常契约 | `ExceptionContractTest`（23 例） | §7.5 场景 1–16 逐字消息 + cause 链 + 分类判据 + 不泄漏第三方类名 |
| 公共面契约 | `PublicApiContractTest`（12 例） | 根包 5 + spi 1、门面形态、反射零第三方/框架类型、不可变与字节克隆 |
| 字节码 | `javap -verbose`（构建期人工抽查） | `MailClient` / `MailKit` / `EmailPlugin` / `MailDeps` 均 major 52 |
| 依赖树 | `mvn dependency:tree`（构建期人工核对） | 仅 `aifei-all`(provided) / `sms4j-Email-core`(optional) / `junit`(test) |

**实测结果**（`aifei-email/` 内 `mvn clean verify`）：

```text
Tests run: 14, Failures: 0, Errors: 0  -- ArchitectureBoundaryTest
Tests run: 13, Failures: 0, Errors: 0  -- ConfigValidationTest
Tests run:  4, Failures: 0, Errors: 0  -- DependencyMissingTest
Tests run: 23, Failures: 0, Errors: 0  -- ExceptionContractTest
Tests run:  7, Failures: 0, Errors: 0  -- PluginLifecycleTest
Tests run: 12, Failures: 0, Errors: 0  -- PublicApiContractTest
Tests run: 10, Failures: 0, Errors: 0  -- SendContractTest
Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**离线纪律**：全部用例零外网。内核发送客户端由 `support/MockSms4jMailClient` 替代（`send` 不抛异常即模拟成功），不真实发邮件；门禁 ⑭ 扫描测试源码的 host，只允许 `127.0.0.1` / `localhost`。

**隔离纪律**：`MailClients`（通道注册表）、`MailProviderRegistry`（Provider 发现缓存）、`SmtpClientFactory`（构建函数）均为进程内静态状态，每个测试类 `@After` 三项一并复位，故**测试类必须串行执行**（不得开启 surefire 并行）。

## 二、用例清单与 AC 映射

### `SendContractTest`（10 例）— AC-01 / AC-02 / AC-03

| 用例 | 断言要点 | AC |
| --- | --- | --- |
| `sendsPlainTextWithRecipientsAndCopies` | 多收件人 / 抄送 / 密送 / 正文进入内核消息；`MailResult{channel=qq, success=true, message="sent"}`；纯文本不带 HTML | AC-01 |
| `sendsHtmlContentDirectly` | `htmlContent` 直传内核；不产生模板流、不走按路径读取；`templateVariables` 对 HTML 内容不生效 | AC-01 |
| `sendsHtmlTemplateWithVariables` | 模板以**流**交给内核（`getHtmlInputStream()` 非空、`getHtmlPath()` 为空）；`getHtmlValues()` 等于传入变量；流内容由上下文类加载器读到 `template/notice.html` | AC-01 |
| `convertsAttachmentsAndCleansTempFiles` | 发送时刻临时文件真实存在且字节一致；`send` 返回后同路径已删除 | AC-01 |
| `ignoresEmptyAttachments` | 空数组 / `null` 附件被忽略，不产生临时文件 | AC-01 |
| `namedChannelsUseIndependentClients` | 命名通道只落到自己的客户端；两通道各自独立构建 | AC-02 |
| `switchingDefaultChannelOnlyChangesConfig` | 只改 `email.default-channel` + 重装配，业务发送代码不变即落新通道 | AC-02 |
| `switchingSmtpServerOnlyChangesConfig` | 只改 `email.qq.smtp-server` 即用新服务器构建客户端并发送成功 | AC-03 |
| `mapsConfigToCoreConfigWithDefaults` | 必填项直传；`is-ssl`/`is-auth`=`true`、`retry-interval`=5、`max-retries`=0；通道覆盖值生效 | AC-03 |
| `sendsConcurrentlyWithoutCrossTalk` | 8 线程并发：每次调用只带自己的附件、无串扰、临时文件各自清理 | AC-01（§7.3 并发） |

### `PluginLifecycleTest`（7 例）— AC-04 / AC-05

| 用例 | 断言要点 | AC |
| --- | --- | --- |
| `startExposesClients` | 启动后默认通道可用、命名通道可用、通道集合 2 个、每通道各构建一次 | AC-04 |
| `stopRejectsFacadeAndInjectedClient` | 停止后 6 个入口（`defaultClient` / `send` / `use` / `channels` / 注入客户端 `send` / `channelName`）全抛场景 5，且未触发发送 | AC-04 |
| `startIsIdempotent` | 连续两次 `start()` 不重建客户端（构建次数仍为 2） | AC-04 |
| `stopIsIdempotent` | 连续两次 `stop()` 无副作用，注册表为空 | AC-04 |
| `failedStartLeavesNoResidueAndCanRecover` | 配置非法启动失败后：状态复位、注册表空、默认通道空、门面抛场景 5；补全配置后同一入口可重新装配并发送成功 | AC-05 |
| `stopThenStartRefreshesDynamicProxy` | 停止 → 改默认通道 → 重启后 `DefaultMailClientProxy` 指向新实例；旧实例不再被使用 | AC-05（P1 #10） |
| `injectedSingletonFollowsCurrentDefaultChannel` | 框架注入的单例（`AopKit.get().getAopFactory().get(MailClient.class)`）为动态代理，重装配后 `channelName()` / `send()` 自动落到新默认通道 | AC-04（P1 #10） |

### `ConfigValidationTest`（13 例）— AC-05 + 场景 3/4/7/9/13

| 用例 | 断言要点 | AC / 场景 |
| --- | --- | --- |
| `rejectsMissingChannel` | 无 `email.<name>.smtp-server` → 占位键消息 | AC-05 / 场景 4 |
| `rejectsMissingRequiredKey` | 删 `from-address` → 含完整键名与 `<empty>` | AC-05 / 场景 4 |
| `rejectsBlankRequiredKey` | 值置空白 → `<empty>` | AC-05 / 场景 4 |
| `rejectsNonNumericRetryInterval` | `retry-interval=five` | AC-05 / 场景 9 |
| `rejectsNonNumericMaxRetries` | `max-retries=3.5` | AC-05 / 场景 9 |
| `rejectsMultipleChannelsWithoutDefault` | 两通道无默认 → `<empty>` | AC-05 / 场景 7 |
| `rejectsUnknownDefaultChannel` | 默认通道指向未声明通道 | AC-05 / 场景 7 |
| `acceptsSingleChannelWithoutDefault` | 单通道缺省默认 → 取唯一通道 | AC-05 |
| `rejectsMissingProvider` | 屏蔽服务资源后启动失败（场景 13 逐字消息）；`reset()` 后正常类路径可发现 `smtp` | 场景 13 |
| `ignoresChannelNameWithDot` | `email.a.b.smtp-server` 被忽略 → 表现为无通道 | §6.2 规则 1 |
| `ignoresReservedChannelName` | `default-channel` 保留字被忽略 | §6.2 规则 1 |
| `ignoresUnknownOptionKeys` | 拼写错误 / 未来参数静默忽略，不影响合法装配 | §6.2 规则 6 |
| `appliesConfigDefaults` | 默认值填充（SSL / 认证 / 重试） | §6.2 规则 7 |

### `DependencyMissingTest`（4 例）— AC-06

| 用例 | 断言要点 |
| --- | --- |
| `reportsMissingMailCoreAsReadableException` | 抛 `MailException`（非链接错误），消息逐字含坐标与引入提示，不含 `NoClassDefFoundError` / `ClassNotFoundException` 类名 |
| `passesWhenMailCoreIsPresent` | 正常类路径探测通过（防「恒失败」假通过） |
| `isolatedLoaderReallyHidesMailCore` | 隔离加载器确实屏蔽内核类（验证隔离手段有效） |
| `missingDependencyIsNeverSilent` | 缺失不被静默吞掉，且不留下装配状态 |

### `ExceptionContractTest`（23 例）— AC-07（§7.5 场景 1–16 全覆盖）

| 用例 | 场景 | 断言要点 |
| --- | --- | --- |
| `scenario1MessageMustNotBeNull` | 1 | 逐字消息 |
| `scenario2RecipientListMustNotBeEmpty` | 2 | 构建期拒绝 |
| `scenario3SubjectMustNotBeBlank` | 3 | 空白主题被拒绝 |
| `scenario4RequiredConfigMissing` | 4 | 通道已声明但 `smtp-server` 值为空 → `email.qq.smtp-server` + `<empty>` |
| `scenario4RequiredKeyMissing` | 4 | 必填键缺失（`username`） |
| `scenario4NoChannelDeclared` | 4 | 完全无通道 → 占位键 |
| `scenario5PluginStopped` | 5 | 停止后发送被拒 |
| `scenario6ChannelNotFound` | 6 | 逐字消息含通道名 |
| `scenario7InvalidDefaultChannel` | 7 | 默认通道指向未知 |
| `scenario8DependencyMissing` | 8 | 内核缺失逐字消息 |
| `scenario9InvalidInteger` | 9 | 整数项非法 |
| `scenario10SmtpError` | 10 | `cause: 535 auth failed`；`assertSame` 原异常为 cause 且原异常 cause 未截断；消息不含第三方类名 |
| `scenario11NetworkError` | 11 | `cause: connection reset`；原因链可遍历到 `SocketException` |
| `scenario10Or11ClassificationWithoutNetworkCause` | 10/11 边界 | 无网络类根因时按 SMTP 错误归类 |
| `scenario11NetworkErrorInNestedException` | 11 | **next-only 形态**：`new MessagingException("send failed")` + `setNextException(new SocketTimeoutException("read timed out"))` → 判网络错误，`cause: read timed out`；cause 保留、`getNextException()` 链被显式断言遍历。**定位是覆盖该真实链路形态，不声称证明实现的 next 分支必要**（`javax.mail 1.6.2` 的 `getCause()` 已返回 next） |
| `scenario10SmtpErrorInNestedException` | 10 | **next-only 形态**：同结构但 next 为 `AuthenticationFailedException("535 auth failed")` → 仍判 SMTP 错误，`cause: 535 auth failed`；定位同上 |
| `scenario12MutuallyExclusiveHtml` | 12 | 发送前拦截，不调用内核 |
| `scenario13ProviderMissing` | 13 | 屏蔽服务资源真实触发 |
| `scenario14AttachmentWriteFailureMessage` | 14 | 消息逐字 + `assertSame` cause（**覆盖层级见第四节**） |
| `scenario15HtmlTemplateNotFound` | 15 | 模板缺失时拦截且不调用内核 |
| `scenario16BuildClientFailed` | 16 | 装配期构建失败，`cause: boom`，原因类型为邮件协议构建异常 |
| `propNullMessage` | 表外（已批准） | `email: prop can not be null` |
| `allMessagesSharePrefixAndLeakNoTypeNames` | 全量 | 16 条消息均以 `email: ` 开头且不含 `org.dromara.email` / `javax.mail` / `com.sun.mail` |

### `PublicApiContractTest`（12 例）— AC-08 / AC-09

| 用例 | 断言要点 |
| --- | --- |
| `rootPackageHasExactlyFiveTypes` | 根包 `.java` 集合精确等于 5 个文件 |
| `spiPackageHasExactlyOneType` | `spi` 精确等于 `MailProvider.java` |
| `mailClientIsMinimalInterface` | 接口；仅 `send` / `channelName`；无受检异常；`send` 返回 `MailResult` |
| `mailKitIsStatefulStaticFacade` | `final` + 私有构造 + 公共方法全 `static`；方法名集合精确 |
| `mailMessageIsImmutableWithInternalBuilder` | `final`；无公共构造器；唯一内部类 `cn.hg.aifei.email.MailMessage$Builder` 且 `public static` |
| `mailResultIsImmutableValueObject` | `final`；字段私有 final；三个取值方法 |
| `mailExceptionIsUnchecked` | `final` + `RuntimeException` 子类 |
| `mailProviderSpiShape` | 接口；仅 `type` / `createClient`；签名只用 JDK 与本模块类型 |
| `publicSignaturesUseOnlyJdkAndModuleTypes` | 反射遍历根包 + spi + Builder 的公共方法：返回 / 参数类型只允许 `java.*` 与 `cn.hg.aifei.email.*`，且无受检异常 |
| `builderEnforcesImmutability` | 列表 / 变量 / 附件 Map 结构不可变；附件字节在构建期与每次读取均复制 |
| `builderRejectsInvalidMessages` | 构建期拒绝空收件人与空白主题 |
| `mailResultFieldsAreSimpleValues` | 结果对象不持有集合字段 |

### `ArchitectureBoundaryTest`（14 例）— AC-08 / AC-10 / AC-11 / AC-12 / AC-13

断言清单见 [AGENTS.md](../AGENTS.md) 第八节（①–⑭），本文不重复；要点：`org.dromara.email.*` 与 `javax.mail.*` 仅限 `internal/smtp/`，`cn.aifei.*` 仅限 `aifei/`，全模块禁 `java.sql.` / `io.undertow.` / `org.slf4j.`，根包 5 + spi 1 精确相等，`Class.forName` 与异常前缀唯一，Java 8 底线，pom 无 starter，配置键唯一，测试零外网。

## 三、AC 覆盖总表

| AC | 判定条件（摘要） | 验证通道 |
| --- | --- | --- |
| AC-01 | 发送能力（纯文本 / HTML / 多收件人 / 抄送密送 / 附件字节 / 模板变量 / 失败抛可读异常） | `SendContractTest` + `ExceptionContractTest` |
| AC-02 | 多通道独立实例；换默认通道只改配置 | `SendContractTest` |
| AC-03 | 换 SMTP 服务器只改配置，业务代码零改动 | `SendContractTest` |
| AC-04 | 启动可用 / 停止后拒绝 / 双幂等 / 注入单例刷新 | `PluginLifecycleTest` |
| AC-05 | 配置非法启动即失败（消息含键名原值）；失败无残留、可重装配 | `ConfigValidationTest` + `PluginLifecycleTest` |
| AC-06 | 缺内核时可读异常，无裸 `NoClassDefFoundError` | `DependencyMissingTest` + 门禁 ⑧ |
| AC-07 | 参数 / SMTP / 网络 / IO / 装配异常逐字消息 + cause 保留 + 不泄漏类名 | `ExceptionContractTest` + 门禁 ⑨ |
| AC-08 | 公共签名零第三方 / 零框架类型 | `PublicApiContractTest` + 门禁 ① ② ⑦ |
| AC-09 | 根包精确 5、spi 精确 1 | `PublicApiContractTest` + 门禁 ⑥ |
| AC-10 | `cn.aifei.*` 仅在接入包 | 门禁 ③ |
| AC-11 | `java.sql.*` / `io.undertow.*` 全模块禁止 | 门禁 ④ |
| AC-12 | class major 52；无 Java 8 之后的语法 / API | 门禁 ⑩ + `javap` 抽查 |
| AC-13 | 依赖树仅允许清单（optional / provided / test） | 门禁 ⑤ ⑪ + `dependency:check`（`mvn dependency:tree` 人工核对） |
| AC-14 | 模块内 `mvn clean verify` 0 failure / 0 error | 构建 |
| AC-15 | 文档四件套 + 根导航 | 文件检查（README / design / AGENTS / testing + 根 README 条目） |
| AC-16 | `aifei-email-demo` 可启动（18190）且六场景可见、零外网 | demo 实测 + 门禁（demo 侧） |

> AC-12 / AC-13 由「门禁 + 构建期人工核对」双通道验证，属工具链断言而非 JUnit 用例。

## 四、场景 14（附件临时文件写入失败）的实际覆盖方式

**现状：降级覆盖，不粉饰。**

| 维度 | 现状 |
| --- | --- |
| 覆盖层级 | **消息工厂级**（逐字消息 + cause 保留）；**未**做真实 IO 失败路径重放 |
| 断言内容 | `MailErrors.attachmentWriteFailed("a.txt", new IOException("No space left on device"))` → `email: failed to write attachment a.txt to temp file, cause: No space left on device`，并 `assertSame(cause, e.getCause())` |
| 未重放的技术原因 | 需让 `Files.createTempFile` 抛异常，可行手段是改写 `java.io.tmpdir`，但 JDK `TempFileHelper` 在类初始化时**静态缓存**临时目录，共享 surefire 进程内已被其它用例初始化，重放不可靠；`SecurityManager` 方案需额外 JVM 启动参数（JDK 21 默认拒绝安装），未引入 |
| 补偿覆盖（已实测） | 附件正路径由 `SendContractTest.convertsAttachmentsAndCleansTempFiles` 覆盖（发送时刻临时文件真实存在 + 字节与消费方一致 + `send` 返回后同路径已删除）；并发清理由 `sendsConcurrentlyWithoutCrossTalk` 覆盖（8 线程逐次断言清理与无串扰） |
| 后续建议 | 若要求 100% 路径覆盖，用独立 surefire 执行（独立 fork + `-Djava.io.tmpdir=<不存在路径>`）承载该用例，属增量改造 |

## 五、其他已知局限

| 局限 | 说明 |
| --- | --- |
| 场景 13 的触发方式 | 通过 `MailProviderRegistry.setDiscoveryClassLoader(...)` 传入屏蔽服务资源的类加载器（覆盖模式下不做事后回退），属真实 `ServiceLoader` 调用，但依赖内部非承诺钩子；`reset()` 恢复默认策略后另断言正常可发现，避免「services 文件缺失」假通过 |
| 场景 2 / 3 的触发时机 | 收件人为空与主题空白在 `MailMessage.build()` 校验（设计 §5.3），故用例在构建期断言；发送期不再重复校验 |
| 框架单例用例 | `injectedSingletonFollowsCurrentDefaultChannel` 依赖 aifei `AopKit` 单例缓存语义（`addSingletonObject` 存对象、`get` 原样返回）；框架变更需同步调整 |
| 真实 SMTP 未验证 | 全部用例走模拟内核，未对真实 SMTP 服务器做过端到端联调（受零外网纪律约束）；demo 亦为 mock 发送 |
| 静态状态隔离 | 三个静态注册表按类复位，**不支持测试类并行** |

## 六、依赖树核对表（`mvn dependency:tree` 实测）

```text
io.github.gollyhu:aifei-email:jar:1.0.0-SNAPSHOT
+- cn.aifei:aifei-all:jar:1.1.2:provided
|  \- com.alibaba.fastjson2:fastjson2:jar:2.0.65:provided   <- 受限传递依赖（provided，不向下游传递）
+- org.dromara.sms4j:sms4j-Email-core:jar:3.3.6:compile (optional)
\- junit:junit:jar:4.13.2:test
   \- org.hamcrest:hamcrest-core:jar:1.3:test                 <- 受限传递依赖（test，仅测试期）
```

| 条目 | 范围 | 判定 |
| --- | --- | --- |
| `cn.aifei:aifei-all:1.1.2` | `provided` | 允许清单（§2.3） |
| `com.alibaba.fastjson2:fastjson2:2.0.65` | `provided`（由 `aifei-all` 传递） | **允许**：`provided` 不向下游传递，不构成第三方 compile / runtime 依赖；`aifei-sms` 已有同款登记先例 |
| `org.dromara.sms4j:sms4j-Email-core:3.3.6` | `optional` | 允许清单（§2.3）；内核传递项同样 optional |
| `junit:junit:4.13.2` | `test` | 允许清单（§10.2） |
| `org.hamcrest:hamcrest-core:1.3` | `test`（由 junit 传递） | 允许清单（§10.2 已登记受限传递项） |

> **AC-13 结论：PASS。** AC-13 口径是「除 `aifei-all` 外无其它第三方 **compile / runtime** 依赖」，`fastjson2` 为 `provided`，故不违反。
> **设计文档补登遗留**：`design.md` §2.3 的依赖树未列 `fastjson2`（它经由 `aifei-all` 传递而非本模块直接声明）；此差异属设计文档补登项，**不影响 AC-13 判定**，已在本表留痕。

## 七、与设计文档的已知差异（实现层等价偏差）

均已由主理人裁定为「实现层等价偏差」，**不改 `design.md` 契约、不改公共面签名、不改 16 条消息措辞**。

| # | 偏差 | 理由 | 影响判定 |
| --- | --- | --- | --- |
| S-4 | `MailMessage` 只有包内 `MailMessage(Builder)` 构造器，没有设计字面列出的 `MailMessage()` 无参构造器 | 无参构造器会允许构造非法消息（空收件人 / 空主题），`build()` 失控；包内构造器仍不暴露公共构造器 | 无契约影响（公共面零变化） |
| S-5 | `MailErrors` 增加 2 条表外消息：`propNull()`（`email: prop can not be null`）、`duplicateProvider()`（`email: multiple MailProvider implementations found for type {type}`） | 前者由 §5.4 / §7.2 明确要求 `MailErrors.propNull()`；后者由 §7.1「同 type 多实现 → 装配期错误」要求，但 §7.5 未冻结措辞 | 无契约影响；16 条场景措辞逐字未变，另测 2 条 |
| S-6 | 场景 6 / 7 / 15 的 `{name}` / `{rawValue}` 走统一 `display()`，空值显示 `<empty>` | 与场景 4 / 7 的占位口径保持一致，避免空串进入消息导致「冒号后什么都没有」 | 无契约影响（仅空值入参的展示细节） |
| S-7 | 场景 10 / 11 / 14 / 16 的 `{causeMsg}` 取 **cause 链上最深一层非空 message**，而非直接取外层 `getMessage()` | 外层 `RuntimeException(cause).getMessage()` 等于内层 `toString()`，会把 `javax.mail.*` 类名带进消息，违反 §7.5「消息不含第三方类型名」 | 无契约影响（消息模板未变，仅取值层级） |
| S-8 | `<htmlTemplate>` 读取过程中的 `IOException` 并入场景 15（`html template not found: ...`） | §7.5 只定义「模板不存在」，未定义「模板读取失败」；两者对消费方语义一致（模板不可用） | 无契约影响（属未定义路径的归并） |
| S-12 | `MailClients` 增加 `defaultChannelName()` / `snapshot()`，`MailProviderRegistry` 增加 `setDiscoveryClassLoader()` / `reset()` | 内部（非承诺）构件的诊断与测试便利；`reset()` 同时清发现缓存与类加载器覆盖 | 无契约影响（不进根包 / spi 公共面，门禁 ⑥ 计数不变） |
| S-9 | `SmtpClientFactory` 整数二次防线报错用**裸选项键**（`max-retries`）而非完整键名（`email.<name>.max-retries`） | 完整键名前缀字面量出现在 `internal.smtp` 会触发门禁 ⑬（配置前缀唯一性） | 无契约影响；该分支为**防御分支、在 `EmailConfigLoader` 校验之后不可达**，主路径由场景 9 覆盖（含完整键名） |
| I-2 | **§7.5 判据事实澄清**：在 `javax.mail 1.6.2` 下 `MessagingException#getCause()` 已被重写为返回内部 `next`，故 **cause 链天然包含 next 链**；实现显式遍历 `getNextException()` 属**防御性等价**（对未重写 `getCause()` 的实现 / 未来版本有意义） | 邮件协议把嵌套异常挂在 `next`（`setNextException` 串链）；显式遍历使判据不依赖 `getCause()` 的重写行为 | **消息与公共面不变**（16 条措辞逐字不变、cause 仍保留原异常）；2 条用例以 `setNextException` 构造 **next-only 链**覆盖该形态，不声称证明 next 分支必要 |
| S-11 | `EmailPlugin.start()` 的单例注册改为**类级双检**（`synchronized (EmailPlugin.class)`） | `singletonRegistered` 是 static 而方法锁是实例锁，多插件实例并发装配可能重复注册 | 无契约影响（语义与幂等性不变） |

## 八、门禁兜底位置与遗留项

| 事项 | 现状 |
| --- | --- |
| `META-INF/services` 资源守护（原 I-3 建议加门禁） | **本轮不加门禁**（加项属设计变更）。已由 `ConfigValidationTest.rejectsMissingProvider` 双向兜住：屏蔽服务资源 → `getProvider("smtp") == null` + 场景 13 逐字消息；`reset()` 后正常类路径 → `getProvider("smtp") != null`（防「services 文件缺失」假通过） |
| 门禁加固（原 S-10：`record` / `sealed` 尾空格匹配、①③ 仅匹配 `import` 行） | **本轮不动**（属设计变更），登记为遗留项 |
| 单机测试类并行 | 不支持（三个静态状态共享），已在第五节列明 |

## 九、复现命令

```bash
cd aifei-email
mvn clean verify                                # 83 例，期望 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest        # 门禁 14 例
mvn test -Dtest=ExceptionContractTest           # 异常契约 23 例
mvn test -Dtest=SendContractTest#convertsAttachmentsAndCleansTempFiles   # 单用例
mvn dependency:tree                             # 依赖口径核对
```
