# aifei-pay 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（选型实证、契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **改代码前必读 `docs/design.md`**（尤其 §5 包结构与文件清单、§6 公共 API 逐字签名、§7 配置键名、§10 门禁）。
- 需求事实来源是 `docs/需求/aifei-pay.md`。**需求与设计冲突时，先改设计决策再改代码**；设计决策变更须回写 `docs/design.md` 对应章节。
- 未经设计决策，不得自行确定契约类行为（异常类型与消息、值对象字段、配置键名、验签凭据选取规则、金额单位等），也不得新增或扩大公共 API 面。

## 交付性质

`aifei-pay` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**：没有 `AppConfig` / `Launcher` / `service/` 包 / `webapp/` / 路由 / HIO / 表结构 / `Db+Row` / `Generator` / UI 页面。坐标 `io.github.gollyhu:aifei-pay:1.0.0-SNAPSHOT`（`packaging=jar`），公共 API + `model` 值对象 + `spi` + `internal` 实现 + `aifei.PayPlugin` / `PayHandler` 同处一个 JAR。V2.0 起模块定位为 IJPay 多渠道聚合，公共面不再出现 `AliPay*` 类型。

**不适用范围（不得据此模块约束要求）**：表结构 / `Db+Row` / `Generator`（零 DB）；`@Path` Service 与 HIO（模块无入站 HTTP 面）；页面 / 原型基线（无 UI，且**已裁决不配 demo 工程**）。

## 硬性约束

### 技术基线

- **Java 8 双约束（语法 + API）**：`source`/`target` = `1.8`；主 JAR 内全部 class major = **52**（含嵌套类型，无豁免）。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 后 API。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；原创文件写统一版权头，既有版权块不得改写。
- **零 DB**：全模块禁 `import java.sql.`，不依赖 `aifei-db`。
- **零日志绑定**：pom 直接依赖禁 `slf4j*` / `logback*` / `log4j*` / `commons-logging` / `tinylog`。模块自身记录走 `cn.aifei.log.Log`，**方法内** `Log.get(本类.class)`（禁 `static` 字段——`LogFactory` 装配晚于类初始化）。
- **禁进程级可变全局状态**：不得调用 `Configuration.setDefaultApiClient` / `Configuration.getDefaultApiClient`，不得使用 `AliPayApiConfigKit`，不得 `Security.addProvider`，不得 `System.setProperty` / `System.getProperties().put`。
- **不产出任何容器化产物**：不得新增容器构建文件、编排清单或 `helm/` / `charts/` 目录；`src/main/java` 不得出现 `public static void main(String[]`。
- **零联网纪律**：`src/test/java` 内不得出现任何 `http://` / `https://` 字面量；单测只允许伪内核与本地密码学运算。`src/main/java` 的 `https://` 字面量**只允许**出现在 `PayKit.java` 的 `GATEWAY_SANDBOX` / `GATEWAY_PRODUCTION` 两个常量（支付宝网关）。

### V2.0 多渠道纪律

- 公共交易面只有 `PayClient` / `PayKit` / `PayNotifyUtil` / `PayException`；`spi` 只有 `PayNotify` / `PayNotifyListener`。不得新增 `AliPay*` 兼容类型。
- 通道由 `pay.<name>.type` 与 `PayTypes` 白名单定义；未登记 type 启动失败；已登记未实装通道必须报「适配器未落地」，不得静默返回。
- `PayNotifyUtil` 与 `PayHandler` 必须共用同一验签核心；不得出现两套验签实现。
- 本模块 `wxpay` 与 `aifei-weixin` 并存，用户二选一；源码零 `cn.aifei.weixin` import。

### 依赖纪律

| 构件 | scope | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei-all` | `provided` | 消费方自带，不向下游传递 |
| `com.github.javen205:IJPay-AliPay-V3` | `optional` | 支付宝 V3 内核 |
| `com.github.javen205:IJPay-WxPay` | `optional` | 微信支付内核（按适配器落地进度引入） |
| `com.alipay.sdk:alipay-sdk-java-v3` | `optional` | 显式声明以**锁定版本** `3.1.89.ALL` |
| `org.dom4j:dom4j` | `optional` | 锁定 `2.1.5`（2.2.0 起字节码 55） |
| `org.bouncycastle:bcprov-jdk18on` | `optional` | 锁定 `1.86`；证书模式运行期必需 |
| `junit:junit` | `test` | — |

- **不得新增任何其它直接依赖**（含测试期 mock 框架）。需要替身时用 `internal` 的包级可见接口手工桩（`PayKernel` + `internal/PayKernelStub` 是先例）。**包级可见接口只能被同包类型实现**，故桩必须放在 `internal/` 而非 `support/`。
- **不得移除 `alipay-sdk-java-v3` 上的三个 exclusion**（`dom4j:dom4j` / `bcprov-jdk15on` / `jackson-databind-nullable`）。其中 `jackson-databind-nullable` 的排除基于「该 SDK 版本被引用 0 次」的实测结论（`docs/design.md` §2.3）；**升级 SDK 版本时必须重跑该验证**。
- **不得再显式引入 `bcprov-jdk15on`**（会与 `bcprov-jdk18on` 同名类冲突）。

### 公共面收敛（不得放宽）

- 根包 `cn.hg.aifei.pay` **精确 4 个公共类型**：`PayClient` / `PayKit` / `PayNotifyUtil` / `PayException`。
- `cn.hg.aifei.pay.model` **精确 4 个公共类型**：`PayOrderResult` / `PayQueryResult` / `PayRefundResult` / `PayNotify`（嵌套枚举不单独计数）。
- 上述两个包**零第三方类型、零框架类型**：不出现 `com.ijpay.*` / `com.alipay.*` / `com.alibaba.fastjson*` / `cn.aifei.*`。
- 公共签名只用 `String` / `long` / `boolean` / `Map<String,String>` / `Throwable` / 本模块类型；**不出现** `JSONObject`、内核 `Response`/`Model` 类型、`java.util.function.*`。
- 公共面**不得出现 `internal` 包行话**（内核边界、持有器、唯一解析点等）；`internal` / `aifei` 均属**非承诺 API**，消费方不得引用。
- **命名语义**：`PayKit` 是**有状态门面**（依赖插件 `start()` 装配，未装配 / 已停止即失败）；`PayNotifyUtil` 是**无状态工具**（无字段、无缓存、不改入参），故用 `Util` 后缀。**不提供** `AliPayUtil`。
- **组合优先，禁止继承内核类型**：不得 `extends com.ijpay.*` / `extends com.alipay.*`。

## 包边界与门禁

`ArchitectureBoundaryTest`（**10 例**）扫描 `src/main/java`（`a04OfflineOnly` 另扫 `src/test/java`）全部 `.java`，扫描前剥离注释：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| **G-01** | `g01PublicSurfaceExact` | 根包直接子文件集合**精确等于** 4 个公共类型；`model/` 直接子文件集合精确等于 4 个值对象；4+4 个类型均 `public`；`PayOrderResult$Channel` / `PayOrderResult$PayloadType` / `PayNotify$NotifyType` 为 public 嵌套枚举 |
| **G-02** | `g02NoThirdPartyInPublicSurface` | 根包与 `model/` 包不得 import / 继承 `com.ijpay.*` / `com.alipay.*` / `com.alibaba.fastjson*` / `cn.aifei.*` |
| **G-03** | `g03NoSqlAndNoOtherChannels` | 全模块无 `import java.sql.`；无 `com.ijpay.wxpay` / `qq` / `jdpay` / `unionpay` / `paypal` / `stripe` / `fuiou` / `xpay` 前缀；无 `IJPay-WxPay` / `IJPay-Servlet-` 代码引用 |
| **G-04** | `g04LayerBoundary` | `cn.aifei.*` 只允许在 `aifei/`；例外登记表**精确等于** `{internal/PayErrors.java -> cn.aifei.log., internal/PayVerifier.java -> cn.aifei.log.}`；`com.ijpay.*` 只允许在 `internal/IjpayPayKernel.java` 与 `internal/PayClients.java`；`com.alipay.*` 只允许在上述两文件 + `internal/PayVerifyKey.java` + `internal/PayVerifier.java` |
| **G-05** | `g05Java8AndBytecode` | `target/classes` 全部 class major == 52；源码无 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` / `"""` / `sealed ` / 模式匹配；源码无 `extends com.ijpay.` / `extends com.alipay.` |
| **G-06** | `g06DependencyDiscipline` | pom 直接依赖集合精确等于 6 项（scope 亦精确）；无日志门面/绑定直接依赖；三个 exclusion 精确存在 |
| 附加 ① | `a01NoContainerArtifacts` | 无容器化构建/编排产物；`src/main/java` 无 `public static void main(String[]` |
| 附加 ② | `a02NoForbiddenDocWord` | `README.md` / `AGENTS.md` / `docs/**/*.md` 不含项目禁用词根（`work` + `buddy` 连写，大小写不敏感） |
| 附加 ③ | `a03NoGlobalMutableState` | 无 `Configuration.setDefaultApiClient` / `Configuration.getDefaultApiClient` / `AliPayApiConfigKit` / `Security.addProvider` / `System.setProperty` / `System.getProperties().put` |
| 附加 ④ | `a04OfflineOnly` | `src/test/java` 零 `http://` / `https://` 字面量；`src/main/java` 的 `https://` 只允许在 `PayKit.java` 的两个网关常量 |

门禁**不得为兼容错误用法而放宽或绕过**；扫描依赖相对路径 `src/main/java`，**必须在 `aifei-pay/` 根目录执行**（`surefire` 的 `workingDirectory` 已固定为 `${project.basedir}`）。

`PayDepsBytecodeTest`（独立于门禁）承载 AC-02 的**依赖侧**断言：遍历 classpath 中每个 JAR 的全部 `.class`，读取 `major_version`，**跳过 `module-info.class` 与 `META-INF/versions/**`**（这两类对 Java 8 运行期不可达，依据见 `docs/design.md` §2.3），断言 `<= 52`；并断言 classpath 中不存在 `dom4j:dom4j`（旧坐标）与 `bcprov-jdk15on`，存在 `org.dom4j:dom4j:2.1.5` 与 `bcprov-jdk18on:1.86`。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 配置键名 / 默认值 / 解析 / 校验 / 非法消息 | `internal.PayConfig` |
| 敏感值脱敏规则 | `internal.PayMask` |
| 异常构造与消息格式 | `internal.PayErrors` |
| 可选依赖探测（`Class.forName`） | `internal.PayDeps` |
| 运行期实例持有与「未装配」消息 | `internal.PayHolder` |
| 分 ↔ 元字符串换算、金额正数校验 | `internal.PayAmounts` |
| 凭据文本读取（`@file:` / 内联 PEM） | `internal.PayResource` |
| 证书序列号计算与公钥提取 | `internal.PayVerifyKey` |
| `ApiClient` / `GenericExecuteApi` 构建与缓存 | `internal.PayClients` |
| 内核调用（IJPay / SDK 交易 API 出现点） | `internal.IjpayPayKernel` |
| 内核响应 → 公共值对象转换 | `internal.PayResults` |
| urlencoded 解析与 `Map` 防御性拷贝 | `internal.PayForms` |
| 验签（`AlipaySignature` 出现点） | `internal.PayVerifier` |
| `cn.aifei.log.Log` 记录点（仅此两处） | `internal.PayErrors`（内核异常收敛前 warn 一次）、`internal.PayVerifier`（验签失败 warn 一次） |
| 装配顺序与容器注册 | `aifei.PayPlugin` |

## 行为契约要点速查

- **金额单位非对称**：**入参** `long ...Fen`（分，必须 `> 0`）；**出参** 内核原样透出的元字符串（如 `"0.01"`）。换算唯一点 `PayAmounts.fenToYuan`，**禁 `double` / `float`**。
- **多应用**：`appId` 为每个交易方法的**第一参数**；`null` / 空白回退 `pay.default-app-id`；未配置的 appId 抛 `PayException`。**不用 `ThreadLocal`**、不用 `AliPayApiConfigKit`。
- **H5 / PC 下单必须走 `new GenericExecuteApi(apiClient).pageExecute(...)`**，**不得**用 `AliPayApi.tradeWapPay` / `tradePagePay` 静态方法——后者共用进程级 `Configuration.getDefaultApiClient()`，多应用会互相覆盖。
- **载荷类型**：`pay.page-http-method=POST`（默认）→ `payloadType=FORM`（自提交表单 HTML）；`GET` → `payloadType=URL`（跳转 URL）。扫码恒为 `payloadType=QRCODE`。
- **验签是纯函数**：`PayNotifyUtil` 不改入参 `Map`（内置 `AlipaySignature.getSignCheckContentV1` 会原地 `remove("sign")` / `remove("sign_type")`，必须先 `PayForms.copy`）；不写缓存；同一入参 N 次结果恒等。
- **验签能力上限（内核限制，非本模块选择）**：`sign_type` 仅 `RSA2`；`charset` 仅 `UTF-8`；值为空的字段不参与签名串。
- **证书验签两条禁令**：① **不得**把证书 PEM **文本**传给 `AlipaySignature.getCertSN(path)` / `getAlipayPublicKey(path)`（它们只接受**路径**）；内容形态必须直接调 `AntCertificationUtil.getCertSN(content)` / `getCertPublicKey(content)`，再走 `AlipaySignature.rsaCheckV1(params, publicKey, "UTF-8", "RSA2")`。② **不得**使用已 `@Deprecated` 的 `rsaCertCheckV1` / `rsaCertCheckV2`。
- **证书模式必须有 bcprov**：`AntCertificationUtil` 静态块注册 BC provider，`CertificateFactory.getInstance("X.509","BC")` 强依赖它；缺失必须由 `PayDeps.requireBouncyCastle()` 在**装配期**转成可读 `PayException`，不得让它变成 `NoClassDefFoundError`。
- **异常分类边界**：调用方参数写错（空白单号、`amountFen <= 0`、超长）→ `IllegalArgumentException`；配置非法 / 未装配 / 依赖缺失 / 内核失败 / 验签失败 → `PayException`（`extends RuntimeException`，受检异常**不得外泄**）。`code()` 透出内核错误码（如 `ACQ.TRADE_NOT_EXIST`），`cause` 保留但**不进入消息**。
- **脱敏**：凭据类键（`private-key` / `app-cert` / `root-cert` / `verify.<n>.value`）的值在**任何**消息与日志中一律 `***`（附长度）；未知键默认 `***`。拼接配置值到消息只允许经 `PayMask` + `PayErrors`。
- **查单不存在**：抛 `PayException` 且 `code() = ACQ.TRADE_NOT_EXIST`（口径唯一，不返回「空结果」形态）。
- **退款幂等**：`outRequestNo` 原样透传给 `setOutRequestNo`，模块**不去重**；`PayRefundResult.outRequestNo()` 回显传入值。
- **装配幂等**：重复 `start()` / `stop()` 无副作用；配置非法时**不留半装配态**（`PayHolder` 保持未装配）；`stop()` 后已注入字段仍持旧实例属已知边界（框架无注销 API），须在 README 声明。
- **`PayKit.client()` 与 `@Inject PayClient` 返回同一对象**。

## 注释规范

- **语言**：一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、配置键名、异常消息。
- **javadoc**：公共类型与公共成员**必须**书写并写全 `@param` / `@return` / `@throws`；`internal` / `aifei` 成员在非自明处书写。公共面不得出现「内核边界 / 持有器 / 唯一解析点」等内部行话。
- **精简**：只在代码无法表达「为什么」时写注释；不复述下一行代码、不写行尾注释、不留被注释掉的死代码 / TODO / 占位符。
- 涉及第三方的「易踩坑」结论（如证书内容 vs 路径、入参 `Map` 被原地修改）**必须**在代码处留一行注释指向 `docs/design.md` 对应小节。

## 测试要求

- 单测**零联网、零第三方 mock 框架**。内核调用替身用 `internal/PayKernelStub.java`（实现包级可见 `internal.PayKernel`；**必须同包**）；验签用例用 JDK `Signature.getInstance("SHA256withRSA")` 现场签名，不使用任何网络或真实商户凭据。
- **测试凭据运行期生成，禁止入库**：仓库内**不得**存在 `src/test/resources/` 下的任何私钥、证书或二进制夹具。密钥对与自签证书由 `support/PayTestCrypto`（2048 位 RSA，自签）在测试进程内现场生成，仅用于验签算法与轮换用例，与任何真实商户无关。该策略声明见 `docs/testing.md` §3。
- 用例与 AC 的对应关系写入 `docs/testing.md`（「AC 编号 ↔ 用例 ↔ 结果」三列）。
- 日志在测试中静默：`support/NoOpLogFactory.java`（仿 `aifei-http` 先例）。

## 验证

**命令在 `aifei-pay/` 根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                  # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest          # 仅跑架构门禁（10 例）
mvn test -Dtest=PayDepsBytecodeTest               # 仅跑依赖字节码断言（AC-02 依赖侧）
mvn test -Dtest=PayConfigTest                     # 单用例：类名或 类名#方法名
mvn dependency:tree                               # 断言：与 docs/design.md §2.2 定稿树逐行一致
javap -verbose target/classes/cn/hg/aifei/pay/PayClient.class | grep major   # 断言 52
```

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置），并更新 `docs/testing.md`。
- 术语必须与 `docs/design.md` §4 统一：客户端 PayClient / 门面 PayKit / 验签工具 PayNotifyUtil / 应用配置 PayAppConfig / 配置域 PayConfig / 验签凭据 PayVerifyKey / 内核边界 PayKernel / 网关环境 env / 同步与异步报文 / 幂等键。
- **跨模块术语**：`aifei-weixin` 的 `PayApi` / `PayNotifyUtil` 与本模块是**同构但不同实现**——动作语义与错误契约对齐，**不产生模块间代码依赖**（不 import 对方任何类型）。本模块公共返回为**自有值对象**，不引入 `JSONObject`。
