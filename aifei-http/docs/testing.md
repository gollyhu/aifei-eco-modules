# aifei-http 测试策略

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

本文面向测试维护者：说明测试结构、AC 覆盖台账、运行方式与新增约定。契约口径见 `docs/design.md`，开发约束见模块 `AGENTS.md`。

## 运行方式

命令一律在模块根 `aifei-http/` 执行（架构门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                    # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest            # 仅跑架构门禁（15 例）
mvn test -Dtest=HttpConfigTest                      # 单类
mvn test -Dtest=HttpConfigTest#shouldUseDefaultsWhenAbsent   # 单方法
mvn dependency:tree                                 # 断言：aifei(provided) + junit(test)
```

**零外部依赖**：全部用例离线可跑。真实传输用例只访问 `com.sun.net.httpserver.HttpServer` 绑定的 `127.0.0.1` 随机端口；编排用例注入伪传输实现，不产生任何网络调用。

## 实测计数

`mvn clean verify` → **`Tests run: 122 / Failures: 0 / Errors: 0 / Skipped: 0`**（JDK 21 编译 target 1.8，主 JAR class major = 52）。较复验时的 117 例多出 5 例，全部来自门禁⑩ 的自证用例（`DEF-01` 修复）。

| 测试类 | 例数 | 类型 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 15 | 架构门禁（源码文本扫描） |
| `HttpKitTest` | 4 | 单元（静态门面） |
| `HttpPluginTest` | 8 | 单元（装配生命周期） |
| `HttpRequestTest` | 17 | 单元（请求构建器） |
| `HttpResponseTest` | 8 | 单元（响应值对象） |
| `internal.DefaultHttpClientTest` | 12 | 单元（编排，伪传输） |
| `internal.HttpCodecTest` | 7 | 单元（编解码） |
| `internal.HttpConfigTest` | 11 | 单元（配置域） |
| `internal.HttpGzipTest` | 5 | 单元（gzip） |
| `internal.HttpResourceTest` | 6 | 单元（资源名） |
| `internal.HttpRetryTest` | 9 | 单元（重试决策） |
| `internal.HttpTimeoutsTest` | 4 | 单元（超时装配） |
| `internal.JdkHttpTransportTest` | 16 | 集成（回环端点） |

支撑类（不含独立用例）：`support.LoopbackServer`（回环端点：回显 query / 头 / 体、状态码、延迟、前 N 次 5xx、gzip、302、大响应体、GBK），`support.NoOpLogFactory`（空日志工厂，见「测试环境约定」），`internal.TransportStub`（伪传输：录制调用 + 脚本化响应 / 异常）。

## 架构门禁（15 例）

`ArchitectureBoundaryTest` 在模块根执行，按源码文本断言，**不得放宽或绕过**：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| ① | `noThirdPartyReference` | 每条 `import` 必须以 `java.` / `javax.` / `cn.aifei.` / `cn.hg.aifei.http.` 开头 |
| ② | `frameworkOnlyInBridgePackage` | `cn.aifei.*` 只允许出现在接入包；`cn.aifei.log.*` 另允许出现在 `internal` 的记录点 |
| ③ | `noSqlReference` | 全模块无 `import java.sql.` |
| ④ | `noJvmGlobalPropertyWrite` | 无 `System.setProperty` / `System.getProperties().put`，且不出现 `http.maxConnections` / `http.keepAlive` 字面量 |
| ⑤ | `publicSurfaceExact` | 根包直接子文件精确 6 个、文件名集合精确相等，且 6 个类型均为 `public`（反射口径） |
| ⑥ | `resourceDerivationOnlyInHttpResource` | 除 `internal/HttpResource.java` 外不得同时出现 `getHost()` 与 `getPath()` |
| ⑦ | `jdkTransportOnlyInInternal` | 根包不 import `java.net.URL` / `java.net.HttpURLConnection`；后者只在 `internal` |
| ⑧ | `java8Compatibility` | 无 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` / `"""` / `sealed ` |
| ⑨ | `bridgePackageExists` | 接入包存在且含 `HttpPlugin.java` |
| ⑩ | `testSourcesOfflineOnly` | 测试源码经**五态状态机剥离注释**后，URL 字面量 host 只能是 `127.0.0.1` / `localhost`；协议名大小写不敏感（`HTTPS://` / `Http://` 一并拦） |

> ⑩ 只判定**代码字面量**：注释被剥离后再扫描，故文件头的官方仓库地址不参与判定（注释不发起任何请求）；代码里出现外部地址会被判失败。
>
> **剥离规则（`stripComments`，五态状态机）**：状态为「代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释」；只有处于**代码态**时 `//`、`/*` 才是注释起点，因此字符串字面量里的 `"http://host/x"`、`"//"`、`"/*"` 原样保留；字面量内处理 `\"`、`\\` 等转义；**注释内的引号不改变状态**（不会因注释里的 `"` / `'` 进入字符串态）；行注释与块注释被删除、其中的换行保留。本项目为 Java 8，无文本块，不处理 `"""`。故「URL 里的 `//` 被当成行注释截断导致门禁恒真」的漏判不成立（`DEF-01` 已修）。
>
> **自证用例（防再次恒真，样本一律内联拼接构造，不依赖真实文件）**：
>
> | 用例 | 断言 |
> | --- | --- |
> | `shouldFlagExternalUrlInCodeLiteral` | 代码字面量里的外部地址**必被命中**（门禁会失败） |
> | `shouldIgnoreUrlsInsideComments` | 行 / 块注释里的地址不命中；注释里的不成对 `"` 不改变状态（同行代码地址仍被命中，命中数恰为 1） |
> | `shouldKeepSlashesInsideStringAndCharLiteral` | 字符串里的 `//` 不是注释起点（同行后续地址仍命中）；`'"'` 字符字面量不进入字符串态（其后注释内地址不命中） |
> | `shouldFlagSchemeRegardlessOfCase` | `HTTPS://` / `Http://` 同样命中 |
> | `shouldNotFlagPlainSlash` | `10 / 2`、`"a / b"`、端口数字不误判 |
>
> **变异探针实测**：临时插入 `String probe = "http://evil.example.com/steal";` 到测试源码 → ⑩ 报红并列出该文件 `-> http://evil.example.com`（同一文件版权头的 `gitee` 地址未被误报）；删除探针后恢复全绿。当前 `src/test/java` 代码内 URL 字面量全部为 `127.0.0.1` / `localhost`。

## AC ↔ 用例 ↔ 结果（开发期用例映射，最终结论见 §4）

> 本表承载「AC → 用例」的映射关系，「结果」列为**开发期占位快照（待 QA 复验）**，非最终判定；
> QA 独立复验后的最终结果以 §4「AC ↔ 用例 ↔ 结果（独立复验后填写）」为准（26 条均已回填，含 AC-R-20 的 `PASS(降级)` 口径）。两表用例映射一致，勿以本表占位值作验收依据。

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| `AC-R-01` | `ArchitectureBoundaryTest#publicSurfaceExact` | 待 QA 复验 |
| `AC-R-02` | `ArchitectureBoundaryTest#noThirdPartyReference` / `#frameworkOnlyInBridgePackage` / `#jdkTransportOnlyInInternal` + `dependency:tree` | 待 QA 复验 |
| `AC-R-03` | `ArchitectureBoundaryTest#java8Compatibility` + `javap -verbose`（major 52） | 待 QA 复验 |
| `AC-R-04` | `JdkHttpTransportTest#shouldNotRetryByDefault`、`DefaultHttpClientTest#shouldAttemptOnceWithoutRetry`、`HttpRetryTest#shouldAttemptOnceByDefault` | 待 QA 复验 |
| `AC-R-05` | `JdkHttpTransportTest#shouldRetryServerErrorForIdempotentMethod`、`DefaultHttpClientTest#shouldKeepResourceStableAcrossRetries` | 待 QA 复验 |
| `AC-R-06` | `HttpRequestTest#shouldRejectRetryForNonIdempotentMethod`、`HttpRetryTest#shouldRejectNonIdempotentMethod` / `#shouldIgnoreRetryTimesForNonIdempotentMethod`、`JdkHttpTransportTest#shouldRejectRetryForPost` | 待 QA 复验 |
| `AC-R-07` | `JdkHttpTransportTest#shouldFailWhenConnectRefused` | 待 QA 复验 |
| `AC-R-08` | `JdkHttpTransportTest#shouldFailOnReadTimeout` | 待 QA 复验 |
| `AC-R-09` | `JdkHttpTransportTest#shouldPreferRequestReadTimeout`、`HttpTimeoutsTest#shouldPreferRequestValues` / `#shouldResolveSegmentsIndependently` | 待 QA 复验 |
| `AC-R-10` | `DefaultHttpClientTest#shouldEnterInOrderAndExitInReverse` | 待 QA 复验 |
| `AC-R-11` | `DefaultHttpClientTest#shouldAbortWhenBeforeThrows`、`JdkHttpTransportTest#shouldNotSendWhenInterceptorThrows` | 待 QA 复验 |
| `AC-R-12` | `HttpResourceTest#shouldDeriveDefaultResource`、`DefaultHttpClientTest#shouldDeriveDefaultResource` | 待 QA 复验 |
| `AC-R-13` | `HttpResourceTest#shouldUseExplicitResource`、`DefaultHttpClientTest#shouldUseExplicitResource`、`HttpRequestTest#shouldRecordExplicitResource` | 待 QA 复验 |
| `AC-R-14` | `JdkHttpTransportTest#shouldKeepResourceStableOnRetry`、`DefaultHttpClientTest#shouldKeepResourceStableAcrossRetries`、`ArchitectureBoundaryTest#resourceDerivationOnlyInHttpResource` | 待 QA 复验 |
| `AC-R-15` | `JdkHttpTransportTest#shouldFailWhenConnectRefused` / `#shouldFailOnIllegalUrl`、`HttpResourceTest#shouldFailOnIllegalUrl`、`HttpCodecTest#shouldFailOnUnknownCharset`、`HttpResponseTest#shouldFailOnUnknownCharset` | 待 QA 复验 |
| `AC-R-16` | `JdkHttpTransportTest#shouldReturnNonSuccessResponses`、`DefaultHttpClientTest#shouldNotRetryClientError`、`HttpRetryTest#shouldRetryOnlyOnServerErrorStatus` | 待 QA 复验 |
| `AC-R-17` | `HttpPluginTest#shouldRegisterClientSingleton` / `#shouldBeIdempotent` / `#shouldRestartAfterStop`、`HttpKitTest#shouldFailBeforeAssembly` / `#shouldFailAfterClear` | 待 QA 复验 |
| `AC-R-18` | `HttpConfigTest#shouldRejectZeroConnectTimeout` / `#shouldRejectNegativeReadTimeout` / `#shouldRejectNegativeRetryTimes` / `#shouldRejectZeroMaxResponseBytes`、`HttpPluginTest#shouldFailFastOnInvalidConfig` | 待 QA 复验 |
| `AC-R-19` | `HttpPluginTest#shouldNotWriteJvmGlobalProperties`、`ArchitectureBoundaryTest#noJvmGlobalPropertyWrite` | 待 QA 复验 |
| `AC-R-20` | `ArchitectureBoundaryTest#testSourcesOfflineOnly`（+ 全部用例仅用回环端点 / 伪传输） | 待 QA 复验 |
| `AC-R-21` | `JdkHttpTransportTest#shouldRoundTripTextAndBinary`、`HttpCodecTest#shouldRoundTripUtf8Text` / `#shouldDecodeGbk` / `#shouldKeepBinaryBytes`、`HttpResponseTest#shouldDecodeBodyByResponseCharset` | 待 QA 复验 |
| `AC-R-22` | `JdkHttpTransportTest#shouldExposeResponseMetrics`、`HttpResponseTest#shouldLookUpHeadersCaseInsensitively` / `#shouldExposeMetrics` | 待 QA 复验 |
| `AC-R-23` | `JdkHttpTransportTest#shouldEncodeQueryAndDeliverHeadersAndBody`、`HttpRequestTest#shouldAppendEncodedQuery` / `#shouldJoinQueryWithExistingQuery` / `#shouldOverrideAndAppendHeaders` | 待 QA 复验 |
| `AC-R-24` | `JdkHttpTransportTest#shouldNotAcceptGzipByDefaultNorFollowRedirect` / `#shouldDecompressGzipWhenEnabled`、`HttpGzipTest#shouldBeDisabledByDefault` / `#shouldAcceptEncodingWhenEnabled` | 待 QA 复验 |
| `AC-R-25` | `JdkHttpTransportTest#shouldFailWhenResponseExceedsLimit` | 待 QA 复验 |
| `AC-R-26` | `ArchitectureBoundaryTest#noSqlReference` / `#bridgePackageExists` + `dependency:tree` | 待 QA 复验 |

> 上表「结果」列为开发期占位（待 QA 复验），仅承载 AC↔用例映射；QA 独立复验结果以 §4 同名表为准（已全部回填 PASS / PASS(降级)）。实现者自评不构成验收依据。

## 测试环境约定

- **超时宽容余量**：connect 侧 `+500ms`，read 侧 `+800ms`（`JdkHttpTransportTest.CONNECT_TOLERANCE_MILLIS` / `READ_TOLERANCE_MILLIS`）；超时用例只做单次等待，**不堆叠 `Thread.sleep`**。
- **重试间隔**：用例内一律把 `http.retry-interval` 置 `0`，避免秒级等待。
- **连接拒绝**：用未监听的回环端口 `http://127.0.0.1:1/x` 制造 `ConnectException`，不依赖外部网络。
- **日志**：模块唯一的记录点是「将重试」时的 `warn`；单测不引入任何日志绑定，故 `DefaultHttpClientTest` / `JdkHttpTransportTest` 在类级换入 `support.NoOpLogFactory`（`LogKit` 可插拔），用例结束还原，避免影响其它测试类。生产环境的日志工厂由应用启动流程装配。
- **AC-R-12 的演示域名**：门禁⑩禁止测试源码出现非回环 URL 字面量，故 `api.example.com` 按片段拼接（`"http" + "://" + "api.example.com"`）后交给 `internal.HttpResource` 做纯函数断言，断言值仍是固定字面量 `GET api.example.com/v1/orders`，**不发任何请求**。
- **AC-R-19 的只读展示**：`HttpPluginTest#shouldNotWriteJvmGlobalProperties` 只读 `System.getProperty(...)` 做前后快照比对，不写任何全局属性。

## 新增约定

- 新增用例前先读 `docs/design.md` §5（公共面签名）与 §10（AC 映射）；**不得**为让用例通过而放宽门禁或扩大公共面。
- 真实传输行为放 `internal.JdkHttpTransportTest`（回环端点）；编排行为放 `internal.DefaultHttpClientTest`（伪传输）；纯规则类各自独立成类，不引入 IO。
- 断言一律用固定期望值（不引用实现推导结果），异常用例同时断言类型、消息要点与 `getCause()`。
- 新增端点行为时扩展 `support.LoopbackServer` 的路径分派，不要在用例里内联 `HttpServer`。
- 每条用例写清覆盖的 AC 编号（类注释或方法注释），保持本文件台账同步。

## 执行结果（QA 独立复验，日期 2026-11-10）

> 复验者：QA（独立上下文执行，未引用实现者自评）。环境：JDK 21.0.11 / Maven 3.9.16，`source`/`target` = `1.8`。
> 除注明外命令均在 `aifei-http/` 根目录执行；demo 命令在 `aifei-http-demo/`。

### 1. 构建与产物

| 命令 | 真实输出摘要 | 结论 |
| --- | --- | --- |
| `mvn -B clean verify` | `Tests run: 117, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（3.273 s） | PASS |
| `cd ../aifei-http-demo && mvn -B clean verify` | `BUILD SUCCESS`（2.331 s；demo 无测试用例） | PASS |
| `mvn dependency:tree` | `cn.aifei:aifei:jar:1.1.2:provided` + `junit:junit:jar:4.13.2:test`（另含传递 `fastjson2:provided`、`hamcrest-core:test`）；**零 compile / optional 第三方** | PASS |
| 主 JAR class major（解 zip 逐条目读字节偏移 6–7） | 21 个 class **全部 major 52**，直方图仅 `52 => 21` | PASS |
| JAR 内 `META-INF/services` | 条目数 **0** | PASS |

逐类例数实测与本文件「实测计数」表 **13/13 全部一致**（10/4/8/17/8/12/7/11/5/6/9/4/16 = 117）。

### 2. demo 运行（库模块无可启动进程，运行面由 demo 承载）

`start-demo.bat` → `Server running at http://localhost:18120`（0.55 s）；`curl.exe` 逐端点实测：

| 端点 | 关键数值 |
| --- | --- |
| `/http/demo/state` | `clientInjected=true`；拦截器 `demo-a > demo-b > demo-c`；`http.*` 七键全量可见（5000 / 10000 / 0 / 200 / 8388608 / true / aifei-http-demo/1.0.0）；JVM `http.maxConnections` = `未设置（JDK 默认 5）`、`http.keepAlive` = `未设置（JDK 默认 true）` |
| `/http/demo/get` | status 200 / attempts 1 / elapsed 2 ms |
| `/http/demo/retry` | withoutRetry **503 / attempts 1**；withRetry **200 / attempts 3**（failFirst=2）；trace 每次尝试 `before a→b→c`、`after c→b→a`，resource 恒为 `GET 127.0.0.1:18120/demo/echo` |
| `/http/demo/timeout` | normal 200 / 1 次 / 106 ms；readTimeout 抛 `HttpException`（cause `java.net.SocketTimeoutException`）/ 307 ms；connectRefused cause `java.net.ConnectException` / 2 ms |
| `/http/demo/interceptor` | 默认推导 `GET 127.0.0.1:18120/demo/echo`；显式 `demo.resource.explicit`；两者 before 正序 / after 逆序 |
| `/http/demo/gzip` | echo 收到 `Accept-Encoding: gzip`；gzip 响应 `Content-Length: 87` → 模块内解压后 `bodySize 64`，`decompressed=true` 且与原文逐字相等 |
| `/http/demo/redirect` | `status=302`、`followedRedirect=false`、`attempts=1` |
| `/http/demo/send` | 显式 `resource` 生效（trace 全为 `demo.explicit`）；`POST` + `retryTimes=2` → 后端拒收「非幂等方法不支持重试: POST」；`path=http://example.com/x` → `code=90000` 拒绝 |
| `/http/demo/echo/state/reset` | `code=0`，返回清理前计数分组 |
| `/http/demo/page` | 200 / 56260 B；`src`/`href` 仅 3 个本地资源（`/assets/css/…`、`/assets/js/…`、`/assets/lib/vue.global.prod.js`），**零外网引用** |

`stop-demo.bat` → `SUCCESS: The process with PID 3704 has been terminated.`；复探 18120 已不可访问、无残留 `DemoApp` 进程。

### 3. 浏览器级验证（browser MCP / puppeteer，真实浏览器）

| 场景 | 实测值 | 与 curl 是否一致 |
| --- | --- | --- |
| 打开 `/http/demo/page` | 标题「aifei-http 出站请求演示 · 真实端点」，14 个区块渲染，`externalRefs=[]` | — |
| 点「运行基本 GET」 | 状态码 200 / attempts 1 / elapsed 2 ms / 响应头 12 个 | 一致 |
| 点「运行重试对照」 | withoutRetry 503 / 1 次；withRetry 200 / 3 次 / 416 ms | 一致 |
| 点「运行超时对照」 | 读超时符合预期=是、连接被拒符合预期=是；normal 200 / 1 次 / 102 ms | 一致 |

- **假开关检验（通过）**：把 `b-status` 改 503、`b-delay` 改 120、`b-key` 改 `qa-probe` 后重跑，端点 URL 变为 `?status=503&delay=120&failFirst=0&key=qa-probe`，页面状态码 503、elapsed 131 ms —— 控件真实驱动请求。
- **空态（通过）**：刷新后响应区显示「还没有发出任何请求…」占位，无上次结果残留。
- **错误态（通过）**：`.error-box` / `.metric-err` 独立样式，展示类型、message、cause、耗时、attempts，与成功态视觉可区分。
- 截图：`http-demo-timeout-error-2026-11-10`（1280×1000）。

### 4. AC ↔ 用例 ↔ 结果（独立复验后填写）

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| `AC-R-01` | `ArchitectureBoundaryTest#publicSurfaceExact` | PASS |
| `AC-R-02` | `#noThirdPartyReference` / `#frameworkOnlyInBridgePackage` / `#jdkTransportOnlyInInternal` + `dependency:tree` | PASS |
| `AC-R-03` | `#java8Compatibility` + JAR 21 class 全 major 52 | PASS |
| `AC-R-04` | `JdkHttpTransportTest#shouldNotRetryByDefault` / `DefaultHttpClientTest#shouldAttemptOnceWithoutRetry` / `HttpRetryTest#shouldAttemptOnceByDefault` + demo retry（attempts=1） | PASS |
| `AC-R-05` | `JdkHttpTransportTest#shouldRetryServerErrorForIdempotentMethod` / `DefaultHttpClientTest#shouldKeepResourceStableAcrossRetries` + demo retry（attempts=3） | PASS |
| `AC-R-06` | `HttpRequestTest#shouldRejectRetryForNonIdempotentMethod` / `HttpRetryTest#shouldRejectNonIdempotentMethod`、`#shouldIgnoreRetryTimesForNonIdempotentMethod` / `JdkHttpTransportTest#shouldRejectRetryForPost` | PASS |
| `AC-R-07` | `JdkHttpTransportTest#shouldFailWhenConnectRefused` | PASS |
| `AC-R-08` | `JdkHttpTransportTest#shouldFailOnReadTimeout` | PASS |
| `AC-R-09` | `JdkHttpTransportTest#shouldPreferRequestReadTimeout` / `HttpTimeoutsTest#shouldPreferRequestValues`、`#shouldResolveSegmentsIndependently` | PASS |
| `AC-R-10` | `DefaultHttpClientTest#shouldEnterInOrderAndExitInReverse` + demo interceptor trace | PASS |
| `AC-R-11` | `DefaultHttpClientTest#shouldAbortWhenBeforeThrows` / `JdkHttpTransportTest#shouldNotSendWhenInterceptorThrows` | PASS |
| `AC-R-12` | `HttpResourceTest#shouldDeriveDefaultResource` / `DefaultHttpClientTest#shouldDeriveDefaultResource` | PASS |
| `AC-R-13` | `HttpResourceTest#shouldUseExplicitResource` / `DefaultHttpClientTest#shouldUseExplicitResource` / `HttpRequestTest#shouldRecordExplicitResource` | PASS |
| `AC-R-14` | `JdkHttpTransportTest#shouldKeepResourceStableOnRetry` / `DefaultHttpClientTest#shouldKeepResourceStableAcrossRetries` / `ArchitectureBoundaryTest#resourceDerivationOnlyInHttpResource` | PASS |
| `AC-R-15` | `JdkHttpTransportTest#shouldFailWhenConnectRefused`、`#shouldFailOnIllegalUrl` / `HttpResourceTest#shouldFailOnIllegalUrl` / `HttpCodecTest#shouldFailOnUnknownCharset` / `HttpResponseTest#shouldFailOnUnknownCharset` | PASS |
| `AC-R-16` | `JdkHttpTransportTest#shouldReturnNonSuccessResponses` / `DefaultHttpClientTest#shouldNotRetryClientError` / `HttpRetryTest#shouldRetryOnlyOnServerErrorStatus` | PASS |
| `AC-R-17` | `HttpPluginTest#shouldRegisterClientSingleton`、`#shouldBeIdempotent`、`#shouldRestartAfterStop` / `HttpKitTest#shouldFailBeforeAssembly`、`#shouldFailAfterClear` | PASS |
| `AC-R-18` | `HttpConfigTest#shouldRejectZeroConnectTimeout`、`#shouldRejectNegativeReadTimeout`、`#shouldRejectNegativeRetryTimes`、`#shouldRejectZeroMaxResponseBytes` / `HttpPluginTest#shouldFailFastOnInvalidConfig` | PASS |
| `AC-R-19` | `HttpPluginTest#shouldNotWriteJvmGlobalProperties` / `ArchitectureBoundaryTest#noJvmGlobalPropertyWrite` + demo state 只读展示 | PASS |
| `AC-R-20` | `ArchitectureBoundaryTest#testSourcesOfflineOnly` + 全部用例仅回环端点 / 伪传输 | **PASS(降级)**：门禁⑩ 实现恒真（见 `DEF-01`），改由 QA 人工全量扫描替代——测试源码代码内 URL 字面量 18 处**全部** `127.0.0.1`，注释内 16 处 `gitee.com` 属版权头（不发起请求） |
| `AC-R-21` | `JdkHttpTransportTest#shouldRoundTripTextAndBinary` / `HttpCodecTest#shouldRoundTripUtf8Text`、`#shouldDecodeGbk`、`#shouldKeepBinaryBytes` / `HttpResponseTest#shouldDecodeBodyByResponseCharset` | PASS |
| `AC-R-22` | `JdkHttpTransportTest#shouldExposeResponseMetrics` / `HttpResponseTest#shouldLookUpHeadersCaseInsensitively`、`#shouldExposeMetrics` | PASS |
| `AC-R-23` | `JdkHttpTransportTest#shouldEncodeQueryAndDeliverHeadersAndBody` / `HttpRequestTest#shouldAppendEncodedQuery`、`#shouldJoinQueryWithExistingQuery`、`#shouldOverrideAndAppendHeaders` | PASS |
| `AC-R-24` | `JdkHttpTransportTest#shouldNotAcceptGzipByDefaultNorFollowRedirect`、`#shouldDecompressGzipWhenEnabled` / `HttpGzipTest#shouldBeDisabledByDefault`、`#shouldAcceptEncodingWhenEnabled` | PASS |
| `AC-R-25` | `JdkHttpTransportTest#shouldFailWhenResponseExceedsLimit` | PASS |
| `AC-R-26` | `ArchitectureBoundaryTest#noSqlReference`、`#bridgePackageExists` + `dependency:tree` | PASS |

**用例引用真实性**：解析本文件 AC 表中的 `类名#方法名` 引用共 **70 条**，逐条在测试源码中命中，**0 条虚构**；另有 54 个用例方法未被 AC 表引用（属补充覆盖，非缺口）。
**断言密度**：13 个测试类 122 个 `@Test`（含 `DEF-01` 修复新增 5 例；117 例时为 314 处断言），无空用例类（`support.LoopbackServer` / `support.NoOpLogFactory` / `internal.TransportStub` 为支撑类，不含用例）。

### 5. 缺陷台账

| 编号 | 类型 | 等级 | 归属角色 | 位置 | 状态 |
| --- | --- | --- | --- | --- | --- |
| `DEF-01` | 门禁失效 / 文档与实现不符 | **P1** | 编码角色（测试实现，`aifei-service-engineer`） | `ArchitectureBoundaryTest.java:340`（`stripComments(read(file))`）+ `:468-485`（`stripComments`）+ `:118`（`URL_PATTERN`） | FIXED（实现侧已修，待 QA 独立复验） |

**`DEF-01` 事实与证据**

- `stripComments` 不区分字符串字面量：遇到 URL 里的 `//` 即按行注释截断，故 `"http://host/path"` 被切成 `"http:`，其后的 `//host` 消失。
- 复现（QA 独立脚本，复刻同一算法）：测试源码 `http(s)://` 原始出现 **36** 次 → 经 `stripComments` + `URL_PATTERN` 后命中 **0** 次；其中**代码内字符串字面量 19 处全部被截断**。
- 变异探针：把 `String u = "http://evil.example.com/steal";` 交给同一逻辑 → 命中 0，判定 **PASS**，即外网地址可被放行。
- 次要缺陷：`URL_PATTERN` 大小写敏感，`HTTPS://Bad.Host/x` 亦漏判。
- 后果：门禁⑩ 恒真，`AC-R-20` 的自动通道失效；本文件原第 58 行「代码里出现外部地址仍会失败」的承诺不成立 —— 属「门禁被隐性放宽」，违反模块 `AGENTS.md`「门禁不得为兼容错误用法而放宽或绕过」。
- 修复方向（不代改）：剥离注释时跟踪字符串 / 字符字面量与转义，或在剥离前先整体提取字符串字面量再判 host；`URL_PATTERN` 加 `Pattern.CASE_INSENSITIVE`。修复后须补一条「外网地址必失败」的自证用例（可用拼接方式构造，避免再次触发本门禁）。

**`DEF-01` 修复记录（实现侧，只改 `ArchitectureBoundaryTest.java` 与本文件）**

- `stripComments` 改为五态状态机（代码 / 字符串 / 字符字面量 / 行注释 / 块注释，处理 `\"`、`\\`，注释内换行保留）——字符串里的 `//`、`/*` 不再被截断。
- `URL_PATTERN` 加 `Pattern.CASE_INSENSITIVE`；判定逻辑抽为 `nonLoopbackUrls(String)`，门禁与自证用例共用同一口径。
- 新增 5 条自证用例（见「架构门禁」⑩ 段落），门禁⑩ 由 10 例增至 15 例，全模块 `Tests run: 122`。
- 变异探针复测：插入外网地址 → ⑩ 报红；删除后全绿。未放宽任何判定，方向为「更严格且正确」。

### 6. 三处已知偏离的裁定

| # | 偏离 | 裁定 | 依据 |
| --- | --- | --- | --- |
| 1 | 门禁⑩ 先剥离注释再扫 URL 字面量 | **要求成立、实现有缺陷（判 `DEF-01`，P1）** | 「注释不发起请求，不应参与判定」的理由成立（16 处 `gitee.com` 全在版权头）；但当前实现连带把**代码字面量**一并截断，造成 **100% 漏判**，故「注释剥离未造成漏判」不成立 |
| 2 | `cn.aifei.log.*` 允许出现在 `internal` | **可接受（有意偏离，非漏检）** | 库本体主源 `cn.aifei.*` 仅 4 处：`aifei/HttpPlugin.java` 的 `AopKit` / `Plugin` / `Prop`，与 `internal/DefaultHttpClient.java` 的 `cn.aifei.log.Log`（唯一日志记录点=「将重试」warn）。`AGENTS.md` 门禁②「`cn.aifei.log.*` 允许出现在 `aifei` 与 `internal`」与实际断言 `logOnly && path.contains(INTERNAL_PACKAGE)` **逐字一致** |
| 3 | 非回环 URL 用 `"http" + "://" + "api.example.com` 拼接规避门禁⑩ | **可接受** | 两处常量分别在 `HttpResourceTest:26` 与 `DefaultHttpClientTest:39`；前者只喂 `HttpResource.of(...)` 纯函数，后者只喂 `TransportStub` 伪传输（该测试类 14 处 `TransportStub`、0 处 `JdkHttpTransport`），**从不进入真实请求路径**；断言值仍是固定字面量 `GET api.example.com/v1/orders` |

### 7. Aifei 专属检查清单

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Java 8 兼容 | ✅ | `source`/`target`=1.8；主 JAR 21 class 全 major 52；门禁⑧ 无 Java 8 后片段 |
| 无分层污染 | ✅ | 主源仅根包 6 + `internal` 11 + `aifei` 1，无 Controller / Render / Repository / Mapper / DTO |
| HIO 完整 | 不适用 | 库模块无入站 HTTP 面（HIO 由 demo 承载） |
| SQL 安全 | ✅（不适用） | 零 `java.sql.`、零动态 SQL、零 `.sql` 文件；门禁③ 真实生效（`contains("import java.sql.")`，主源命中 0） |
| 生成器产物未被手改 | 不适用 | 无 `common/db/base/`、无 `ModelSet.java` |
| 事务用法 | 不适用 | 模块无事务调用、无 `commit()` |
| 能力边界 | ✅ | 仅 `HttpURLConnection`（`internal` 内）；无上传下载 / Cookie / Session / Excel / Admin 脚手架；公共面零 `cn.aifei.*` / `java.net.*` |

**额外强制检查**：全量 import 分类——库本体 262 行 import 仅 `java.*` / `org.junit`(test) / `com.sun.net.httpserver`(test，PRD §六 允许) / `cn.aifei.*` / 本模块；demo 141 行 import 为 `cn.aifei.*` / `io.undertow.*` / `com.alibaba.fastjson2` / `java.*`（应用侧依赖，非库约束范围）。全模块**零** `System.setProperty` / `System.getProperties().put` / `http.maxConnections` / `http.keepAlive` 字面量。页面与 JS **零外部域名**。demo 源码仅 import 库的 5 个公共类型（`HttpClient` / `HttpRequest` / `HttpResponse` / `HttpException` / `HttpInterceptor`）+ `aifei.HttpPlugin`，**未引用 `internal`**；库本体文件最后修改 20:51:29，demo 开发起点 20:56:20，**无库文件在 demo 阶段被改动**。

### 8. 遗留风险与未落实项

1. `DEF-01` 未修复前，`AC-R-20` 的自动门禁通道无效（当前为人工扫描替代，属降级证据）。
2. demo 的 `pom.xml` 依赖本地仓库的 `aifei-http:1.0.0-SNAPSHOT.jar`（20:51:08 安装），非 workspace 源码直连；已核对两者 class 集合与大小完全一致（21/21、33275 B），本次验证有效，但**后续库改动须先 `mvn install` 再验 demo**。
3. 门禁④「不得出现 `http.maxConnections` / `http.keepAlive` 字面量」只扫 `aifei-http/src/main/java`，不含 demo；demo 的只读展示因此不受拦（与 PRD §八-8 要求一致，属已知边界而非缺陷）。
4. 页面把内部类型全限定名 `cn.hg.aifei.http.internal.HttpIoException` 直接展示（P3 建议，非缺陷：`AC-R-15` 明确允许 `HttpException` 子类；建议页面改用 `simpleType` 展示）。
5. 「断网环境」未做物理断网跑（本机回环可用，外部网络未隔离）；离线性结论来自「代码内 URL 全回环 + 伪传输 + 全量用例 117 例无外网访问」的组合证据。

## DEF-01 复验（QA 独立复验 第 2 轮，日期 2026-11-10）

> 复验者：QA（独立上下文，**不采信实现侧自证**，探针自造且不复用实现侧样本）。环境：JDK 21.0.11 / Maven 3.9.16，`source`/`target` = `1.8`；命令均在 `aifei-http/` 根目录执行。本节只追加，不改动以上任何既有内容。

### 1. 构建

| 命令 | 真实输出 | 结论 |
| --- | --- | --- |
| `mvn -B clean verify` | `Tests run: 122, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，`Total time: 3.372 s`（复跑 3.285 s，含 clean） | PASS |

增量核对：`122 − 117 = 5`，与 `ArchitectureBoundaryTest` 由 10 例增至 15 例逐一对齐——10 条原始断言 + 5 条自证（`shouldFlagExternalUrlInCodeLiteral`、`shouldIgnoreUrlsInsideComments`、`shouldKeepSlashesInsideStringAndCharLiteral`、`shouldFlagSchemeRegardlessOfCase`、`shouldNotFlagPlainSlash`），用例名实测与你上轮记录的 117 无重复、无删改。

### 2. 变异探针（QA 自造：`src/test/java/cn/hg/aifei/http/__QaProbe.java`，每轮 `mvn -B test -Dtest=ArchitectureBoundaryTest`）

| # | 探针内容 | 期望 | 实测真实输出（摘要） | 结论 |
| --- | --- | --- | --- | --- |
| ① | `String u = "https://attacker.invalid/path";`（真实外网字面量） | FAIL | `Tests run: 15, Failures: 1`；`testSourcesOfflineOnly:400` → 违规 `[src/test/java/cn/hg/aifei/http/__QaProbe.java -> https://attacker.invalid]`；`BUILD FAILURE` | PASS（门禁真实生效，报文精确指向探针文件） |
| ② | 外网地址**只写在注释**（行注释、块注释、注释掉的代码片段，块注释内另含不成对 `"`） | PASS | `Tests run: 15, Failures: 0`，`BUILD SUCCESS` | PASS（注释被正确剥离；注释内引号未把状态机带进字符串态） |
| ③ | `"http://127.0.0.1:8080/a//b"`、`"http://localhost:18080/x//y//z"`、`"http://127.0.0.1:1/refused"` | PASS | `Tests run: 15, Failures: 0`，`BUILD SUCCESS` | PASS（字符串内 `//` 不再截断，回环地址不误报） |
| ④（QA 追加的强度探针） | `"http://127.0.0.1.attacker.invalid/x"`、`"http://localhost.attacker.invalid/x"` | FAIL | `Failures: 1` → 两条均被列入违规 | PASS（host 为**精确等值**比对，无前缀 / 包含式放行） |

**探针清理取证**：删除 `__QaProbe.java` 后复跑 `mvn -B clean verify` → `Tests run: 122, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`；`Get-ChildItem -Recurse -Filter '__QaProbe*'` 计数 = **0**；`src/test/java/cn/hg/aifei/http` 根包 `.java` 仍为 **5** 个（与探针前一致）；`git status --short -- aifei-http` 仅 `?? aifei-http/`（模块整目录本就未纳入版本库），QA 临时日志 `qa-verify.log` 已删除（`Test-Path` = False），无残留。

### 3. 门禁强度核对（对照代码事实，不看自述）

- **白名单未放宽**：`LOOPBACK_HOSTS = {"127.0.0.1", "localhost"}`（`:113`）；判定为 `!LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))`（`:687-699`），无新增域名、无通配、无端口例外（端口被 `:` 切掉后不参与判定，与规范一致）。
- **协议大小写不敏感**：`Pattern.compile("https?://([^/\\s\"'<>]+)", Pattern.CASE_INSENSITIVE)`（`:119-120`），由自证用例 `shouldFlagSchemeRegardlessOfCase` 覆盖。
- **单一判定实现**：门禁 `testSourcesOfflineOnly`（`:393-401`）与 5 条自证用例（`:409-488`）**共用** `nonLoopbackUrls(stripComments(...))`，不存在「门禁一套、自证一套」的双口径。
- **扫描范围未缩**：仍对 `TEST_ROOT = src/test/java` 做全量 `Files.walk`（`:497-507`），并有「扫不到任何 `.java` 即失败」的防空转断言（`:505`）。

### 4. 文档一致性

- ⑩ 描述（`§架构门禁` ⑩ 行 / 剥离规则 / 自证用例表 / 变异探针段）与代码事实一致：五态状态机、`CASE_INSENSITIVE`、共用 `nonLoopbackUrls` 三项均可逐条对照到代码行。
- `Tests run: 122`、`ArchitectureBoundaryTest 15 例`、`较复验时的 117 例多出 5 例` 三项与实测一致。
- 上一轮「执行结果」章节：AC 表 26 行、`§3` 浏览器表、`§8` 遗留风险等判定文本**逐字在位，无删除、无改写**（行数只增未减）。实现侧仅做新增：`§5` 台账 `DEF-01` 状态列由 `OPEN` 置为 `FIXED（实现侧已修，待 QA 独立复验）`，并在 `§5` 内新增「`DEF-01` 修复记录」子块（`:223-228`）；状态停在 `FIXED` 而非自行 `CLOSED`，关闭权留给 QA，**流程正确**。
- 待对齐（P3 记录性，不影响门禁判定）：`§4`「13 个测试类 117 个 `@Test` / 314 处断言」为上一轮快照，现为 122 例（新增 5 例的断言未计入）；`§8-1`「`DEF-01` 未修复前…」为历史陈述，已在 `§实测计数` 处注明增量。

### 5. 回归（G1–G9 未被本次修复破坏）

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| ① `noThirdPartyReference` | PASS | 122 例 0 失败；`:193-205` 与 design §11 ① 逐条同构 |
| ⑤ `publicSurfaceExact`（仍精确 6） | PASS | `:279-297` 数量 = `EXPECTED_PUBLIC_TYPES`(6) + 文件名集合精确相等 + 6 类型 `public`（反射）；根包主源实测 6 个文件 |
| ④ `noJvmGlobalPropertyWrite` | PASS | `:254-272` 四片段（`System.setProperty` / `System.getProperties().put` / `http.maxConnections` / `http.keepAlive`）未变 |
| ⑧ `java8Compatibility` | PASS | 主 JAR 解 zip 读偏移 6–7：直方图 `major 52 => 21`（无其它 major） |
| ②③⑥⑦⑨ | PASS | 均 0 失败，断言文本与 design §11 ②③⑥⑦⑨ 逐行一致 |

- **未放宽他门**：10 条原始断言的判定逻辑与 design §11 表逐行比对，**除 ⑩ 外无任何一处被改为更宽松**（无新增 `skip` / 白名单 / 提前 `return` / 例外路径）。
- **回归面为零的旁证**：主源 mtime 最新为 `HttpCodec.java 20:47:55`，而修复文件为 `ArchitectureBoundaryTest.java 21:27:48` / `testing.md 21:28:39` —— 修复只触及测试与文档，`src/main/java` 未动。

### 6. 结论

- **`DEF-01` = CLOSED**：门禁⑩ 由「恒真」修为真实生效；QA 独立探针 ①（外部 URL 必红）、②（注释必绿）、③（回环含 `//` 必绿）全部符合预期，追加的 ④（回环前缀伪装域名）亦被拦。
- `AC-R-20` 的**自动门禁通道恢复有效**（不再依赖人工扫描替代）；`§8-5` 的物理断网跑仍为已知边界，不构成本条降级理由。
- 新缺陷：**无 P0 / P1**。仅 1 条 P3 记录性偏差（见 `§4` 待对齐两项）；另 design §11 ⑧ 提到的 `instanceof` 模式变量未在 `POST_JAVA8_FRAGMENTS` 中单列——**既有偏差、非本次修复引入**，且 `source/target = 1.8` 在编译期兜底。
- 缺陷台账 `DEF-01` 最终状态：**CLOSED（QA 第 2 轮独立复验，2026-11-10）**。
