# aifei-email 设计文档

> 本文是 `aifei-email` 的**设计事实来源（契约）**：公共 API 逐字签名、包边界与门禁断言、配置键全集、sms4j 邮件插件选型实证、行为契约、任务分解与验收映射以本文为准。面向使用者的用法见 `README.md`；面向开发代理的约束见模块 `AGENTS.md`；测试策略与用例清单见 `docs/testing.md`（本文只定契约与端点清单）。
>
> **交付性质**：`aifei-email` 是 aifei 生态的**库模块（SDK，可被应用依赖）**，**不是业务应用工程**。任务输入 §〇 已定档：表结构草案（零存储、禁 `java.sql.*`）、Service 方法清单（无 `@Path` / 服务）、低保真 UI 原型（跳过 PM 原型与 QA 基线门禁）三章节**不适用**。本模块只有「根包公共 API + `spi` 通道扩展 SPI + `internal` 适配层 + `aifei` 接入层」四层，无 `AppConfig` / `Launcher` / `@Path` / 业务库表 / `Db+Row` / `Generator`。
>
> **坐标**：`io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT`（`packaging=jar`）；根包 `cn.hg.aifei.email`；接入层 `cn.hg.aifei.email.aifei`；适配层 `cn.hg.aifei.email.internal`（含 `internal.smtp` 子包）；通道扩展 SPI `cn.hg.aifei.email.spi`；**Java 8**（语法与 API 双约束，主 JAR class major = 52）；sms4j 邮件插件内核 `org.dromara.sms4j:sms4j-Email-core:3.3.6` 为 `optional`，`aifei:1.1.2` 为 `provided`。
>
> **技术路线（用户点名锁定，不得推翻）**：基于 **sms4j-Email-core v3.3.6**（dromara 社区，https://sms4j.com/doc3/plugin/mail.html，Apache-2.0，JDK 8+）二次封装。sms4j 邮件插件必须以「**引入核心 + `internal.smtp` 适配层隔离**」的技术路线落地，**不得被「自研 SMTP 等价」顶替**；**禁** `sms4j-spring-boot-starter` / `sms4j-solon-plugin`（本就不存在邮件 starter，但须禁任何形式的 starter 引入）。发送内核一律走 sms4j 的 `MailBuild` / `MailClient`（实证见 §2）；本模块 `internal.smtp` 只做配置解析、类型映射、依赖探测、异常翻译、附件转换与 HTML 模板渲染映射，**不自行实现 SMTP 协议**。
>
> **许可证闭环（用户已定档，法务已确认）**：sms4j-Email-core 传递依赖 `com.sun.mail:javax.mail:1.6.2`（JavaMail，**CDDL 1.1 + GPL 2.0 with Classpath Exception**），法务已确认 CPE 豁免可接受——许可证不再是阻塞项。第三方声明动作（LICENSE/NOTICE/README 声明 + 根 `AGENTS.md` 登记）在实现期落地（§13 事项 2）。

---

## 0. 不适用章节声明（模板裁剪）

本次是**库模块（SDK）**，不是业务应用工程。系统架构师模板中以下章节**不适用**，逐条给出理由：

| 模板章节 | 结论 | 理由 |
| --- | --- | --- |
| 模块划分（业务服务/子系统拓扑） | **不适用** | 单一 JAR 库，无业务服务切分；仅按「根包公共 API / `spi` 扩展 SPI / `internal` 适配层 / `aifei` 接入层」四个技术层划分（§4） |
| 业务库表结构 / `Db+Row` / `Generator` / `Model` | **不适用** | 本模块**零存储**：无表、无连接、无 DDL；门禁反向断言 `src/main/java` 无 `import java.sql.`、无 `aifei-db` 依赖（§4.2 / §9） |
| 服务拓扑 / `@Path` 方法清单 / HIO / 路由 / 拦截器（入站） | **不适用** | 库模块本体无业务入站 HTTP 面；邮件发送是出站调用（主动连 SMTP），不是入站接收；真实端点只在 `aifei-email-demo`（§12） |
| 部署架构 / 实例规格 / 容量规划 | **不适用** | 库模块随消费方应用部署，无独立部署单元 |
| 数据层角色产出清单 / 前端页面设计交付 | **不适用** | 无数据层实现；无设计交付页面——demo 单页由前端直接实装，不经原型与 QA 基线门禁 |
| `AppConfig implements AifeiConfig` / `Launcher` | **不适用** | 库模块无启动入口；装配经消费方 `config(Plugins)` 内 `plugins.add(new EmailPlugin(prop))`（§5.5） |
| 缓存 / Redis 设计 | **不适用** | 模块零缓存、零 Redis：不做发送记录持久化、不做幂等去重；不引入 `aifei-cache` 及任何 Redis 客户端 |

`aifei-email/` 内 `mvn clean verify` 必须 **0 failure / 0 error**（含架构门禁测试）。

---

## 1. 模块定位与设计原则

### 1.1 定位

aifei 生态有 `aifei-sms` 覆盖短信通道，但**出站邮件通道缺位**：业务普遍各自裸用 JavaMail / Spring Mail / 厂商 SDK 发邮件，SMTP 配置、附件拼装、HTML 模板渲染、多账号切换各写各的，换 SMTP 服务器即重写发送代码。本模块基于 sms4j 邮件插件补齐**统一出站邮件通道**：以一套接口封装 SMTP 邮件发送，换 SMTP 服务器只改 `email.*` 配置不改业务代码。

| # | 目标 | 落点 |
| --- | --- | --- |
| 1 | **统一发送门面** | `MailClient`（接口）/ `MailKit`（静态门面）发送邮件（纯文本 / HTML、多收件人、抄送密送、附件）；底层经 sms4j `MailBuild` / `MailClient`（§5） |
| 2 | **多 SMTP 配置池切换** | 基于 sms4j 邮件插件多配置池能力（`MailBuild.build(config)`），支持多账号 / 多租户场景，按标识取用 |
| 3 | **类型隔离** | `org.dromara.email.*`（sms4j 邮件插件包名）/ `javax.mail.*` 只在 `internal.smtp`；公共面零第三方 / 零框架类型（§4.2） |
| 4 | **依赖安全网** | sms4j-Email-core 为 `optional`，缺失时首次调用抛可读 `MailException`（含依赖坐标与引入提示），不裸抛 `NoClassDefFoundError` |

> **sms4j 邮件插件定位（实证级，§2.4）**：sms4j 邮件插件是 **SMTP 统一封装 + 多配置池**（可做多租户 / 多账号），**不是**多厂商 HTTP API 聚合（没有 SendGrid / SES / Mailgun / 阿里云邮件适配器）。`"qq"` 这类 key 只是**配置池标识**，不是厂商名。若未来要接厂商 HTTP API，走 `MailProvider` SPI 扩展点（D13）。

### 1.2 基于 sms4j 邮件插件二次封装（非自实现，铁律）

本模块**不自行实现任何 SMTP 协议、邮件消息构造与传输逻辑**，而是基于 sms4j-Email-core 3.3.6 二次封装。本模块只做六件事：

1. **装配**：实现 aifei `Plugin`，在 `start()` 读 `email.*` 配置，经 `MailSmtpConfig.builder()...build()` 构建 sms4j 配置对象、经 `MailBuild.build(config)` 取回 sms4j `MailClient` 实例并包装为模块 `MailClient` 注册到 `@Inject` 单例（§5.5 / §7.2）。
2. **薄门面**：对外暴露 aifei 风格极简 API（`MailClient` / `MailKit`）。
3. **类型收敛**：sms4j 邮件插件类型（`org.dromara.email.*`）**不得**出现在公共 API 面。
4. **框架接线**：经 `@Inject` 注入客户端、经 `app-config.txt` 的 `Prop` 承载配置。
5. **附件转换**：sms4j 原生附件用 `Map<显示名, 文件路径>`（实证见 §2.4），本模块在适配层将消费方传入的 `byte[]` 写入临时文件后转换为 sms4j 格式，以适配无文件系统环境。
6. **统一异常与依赖探测**：失败收敛 `MailException`；sms4j 缺失时可读失败（§7.4 / §7.5）。

### 1.3 设计原则（硬约束）

| # | 纪律 |
| --- | --- |
| 1 | **根包 `cn.hg.aifei.email` 精确 5 个公共类型**：`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`。不得增删、不得改名；变更须先改本文 §5 并过设计决策（Q7 定稿，§5.6） |
| 2 | **公共签名零第三方类型、零框架类型**：只用 JDK 类型 + 本模块类型；不出现 `org.dromara.email.*` / `javax.mail.*` / `com.sun.mail.*` / `cn.aifei.*`（`spi` 包同约束） |
| 3 | **sms4j 邮件插件唯一内核**：`org.dromara.sms4j:sms4j-Email-core` 为 `optional`；仅引入 `sms4j-Email-core`（传递 api/comm）；禁 starter / solon plugin；由 `internal.smtp` 单点引用 |
| 4 | **唯一定义点**：配置键名解析、通道配置项校验、异常消息措辞、依赖探测各只有一处实现（§7.1 标 ★） |
| 5 | **零存储 / 零日志绑定**：`src/main/java` 禁 `import java.sql.` / `io.undertow.*`；不声明任何日志绑定实现 |
| 6 | **同步发送**：公共面只提供同步发送，不暴露异步 API、不自带线程池（§3 D6） |
| 7 | **Java 8 双约束**：语法与 API 均限 Java 8，主 JAR class major = 52 |

> 第 1、2、3、4、5、7 条由架构门禁 `ArchitectureBoundaryTest`（§9）强制，**不得放宽或绕过**。

### 1.4 本期不做清单（边界，防范围蔓延）

| 不做项 | 边界说明 |
| --- | --- |
| 邮件模板 CRUD / 审核 | 属消费方业务系统；本模块只做 `#{变量名}` 替换，不引模板引擎（§3 D10） |
| IMAP / POP3 收信 | 属入站邮件范畴，本模块只做出站发送 |
| 退信 / 打开点击追踪 | 需 IMAP 收信或厂商 webhook，首期不做；首期不设回执 `spi` |
| 异步发送 API / 内置线程池 | 公共面只同步；异步由消费方包装 |
| 发送记录持久化 | 零存储；落库由消费方承担 |
| 内容审核 / 敏感词过滤 | 由消费方在调用前自行处理 |
| 附件病毒扫描 | 由消费方在调用前自行处理 |
| 群发性能优化 | 不做批量群发的连接复用 / 流水线发送 |
| 多 SMTP 负载均衡 / 故障切换 | 只「按配置指定 SMTP 通道发送」，不做 failover / 权重路由 |
| APP 推送 | 属其它通道范畴，不在本模块范围 |
| 模板引擎引入 | 只用 `#{变量名}` 替换，不引 FreeMarker / Thymeleaf（D10） |
| sms4j 内置重试在公共面暴露 | 倾向 `maxRetries=0` 关闭（D7） |
| 黑名单过滤公共面暴露 | sms4j 有此能力（`Blacklist` 接口），首期不在公共面暴露 |
| 附件压缩包（zipName）公共面暴露 | sms4j 有此能力，首期不在公共面暴露 |

---

## 2. 选型实证记录（含依赖树结论）

> **门禁要求**：对框架 / 依赖做「不可用 / 有缺陷」断言前必须当场实测；本次对定档依赖做精确 artifact、Java 8 兼容、传递依赖、编程式装配可行性实测。**全部结论均为当场实测，非凭记忆**。

### 2.1 实测环境与命令清单

| 项 | 值 |
| --- | --- |
| Maven | `Apache Maven 3.8.3`（PowerShell 下 `-D` 参数整体加引号） |
| 本地仓库 | `D:/Apache/MavenRepository`（`settings.xml` 的 `localRepository`） |
| 探测 JDK | JDK 19（仅用于 `javap` 读 class major；编译 `source/target=1.8`） |
| 临时探针 | `%TEMP%/sms4j-email-probe`（用毕即删，不留仓库） |

执行过的实证命令（枚举，便于复核）：
1. `mvn -q dependency:get "-Dartifact=org.dromara.sms4j:sms4j-Email-core:3.3.6"`（在 `%TEMP%/sms4j-email-probe` 目录执行以避开根 pom 无 version 的问题）→ **EXIT=0**。
2. `Get-ChildItem D:/Apache/MavenRepository/org/dromara/sms4j/sms4j-Email-core/3.3.6/` → 落盘 `sms4j-Email-core-3.3.6.jar`（18541 B）+ `.pom`（1309 B）+ `.sha1`。
3. `jar tf <jar>` → 列出 core/api/comm jar 真实类名。
4. `javap -verbose -classpath <jar> <class> | Select-String "major version"` → 逐构件 / 逐传递项验证字节码版本。
5. `javap -classpath <cp> ...` → 反编译 `MailFactory` / `MailBuild` / `MailService` / `MailClient` / `MailSmtpConfig` / `MailSmtpConfigBuilder` / `MailMessage` / `MailsBuilder` / `MailException` / `Parameter` 方法签名与字节码。
6. 临时 pom（`sms4j-Email-core:3.3.6` 唯一依赖）`mvn dependency:tree` → EXIT=0，抓完整传递树。
7. 从 `javax.mail-1.6.2.jar` 内提取 `META-INF/LICENSE.txt`；读取 `javax.mail-1.6.2.pom` 头部许可证声明。

### 2.2 Q1 结论：精确 artifactId 与 Java 8 / 依赖树（**已实证通过**）

| # | 结论 | 证据 |
| --- | --- | --- |
| 1 | **精确 artifactId = `org.dromara.sms4j:sms4j-Email-core:3.3.6`**（大写 E，用户定档一致） | `dependency:get` EXIT=0；落盘 `org/dromara/sms4j/sms4j-Email-core/3.3.6/sms4j-Email-core-3.3.6.jar` |
| 2 | **groupId 为 `org.dromara.sms4j`**，父 pom 为 `org.dromara.sms4j:sms4j-Email-plugin:3.3.6` | core pom `<parent>` 与 `<groupId>` 实际内容 |
| 3 | **core 传递链**：`sms4j-Email-core → sms4j-Email-api → sms4j-Email-comm` | `dependency:tree` 输出 |
| 4 | **sms4j-Email 各构件 class major = 52** | `javap -verbose`：core（MailFactory/MailBuild/MailService）、api（MailClient）、comm（MailSmtpConfig）全部 `major version: 52` |
| 5 | **传递项全部 Java 8 兼容**（major ≤ 52） | javax.mail 1.6.2 = 51（Java 7）；javax.activation 1.1 = 48；com.sun.activation:javax.activation 1.2.0 = 49；slf4j-api 1.7.36 = 49；hutool-core/http/cron/setting/log 5.8.28 = 52 |
| 6 | **无 Spring / 无日志绑定实现 / 无 starter** | `dependency:tree` 无 `org.springframework.*`；日志仅 `slf4j-api:1.7.36`（API，无 `slf4j-simple` / `logback` / `log4j` 绑定）；无 starter / solon |
| 7 | **sms4j-Email-core 自身许可证为 Apache 2.0** | core pom `<licenses>` 声明 `Apache 2` |
| 8 | **javax.mail 许可证为 CDDL 1.1 + GPL 2.0 with Classpath Exception** | `javax.mail-1.6.2.pom` 头部声明「subject to either GPL Version 2 only or CDDL」+「GPL Classpath Exception」；jar 内 `META-INF/LICENSE.txt` 首行「COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.1」；法务已确认 CPE 豁免可接受 |
| 9 | **包名为 `org.dromara.email`**（非 `org.dromara.sms4j.email`） | `jar tf` 输出：`org/dromara/email/core/factory/MailFactory.class` 等；门禁断言以此包名前缀为准 |
| 10 | 全树传递项 | 见 §2.3 |

### 2.3 Q1 依赖树（实测回填，实现期以本表核对）

```text
io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT
├─ cn.aifei:aifei:1.1.2 ........................ provided（消费方自带，不传递）
├─ org.dromara.sms4j:sms4j-Email-core:3.3.6 ........ optional（阻断传递，唯一实现内核）
│  ├─ org.dromara.sms4j:sms4j-Email-api:3.3.6 ...... optional（传递）
│  │  └─ org.dromara.sms4j:sms4j-Email-comm:3.3.6 .. optional（传递）
│  │     ├─ cn.hutool:hutool-cron:5.8.28 ........... optional（传递）
│  │     │  ├─ cn.hutool:hutool-core:5.8.28 ........ optional（传递）
│  │     │  └─ cn.hutool:hutool-setting:5.8.28 ..... optional（传递）
│  │     │     └─ cn.hutool:hutool-log:5.8.28 ...... optional（传递）
│  │     ├─ cn.hutool:hutool-http:5.8.28 ........... optional（传递）
│  │     └─ com.sun.mail:javax.mail:1.6.2 .......... optional（传递，JavaMail，CDDL+GPL+CPE）
│  │        └─ javax.activation:activation:1.1 ..... optional（传递）
│  ├─ com.sun.activation:javax.activation:1.2.0 .... optional（传递）
│  └─ org.slf4j:slf4j-api:1.7.36 ................... optional（传递，仅 API 无绑定）
└─ junit:junit:4.13.2 .............................. test
   └─ hamcrest-core ................................ test（由 junit 传递；仅测试期，不进生产 classpath）
```

**断言口径**：① `sms4j-Email-core` 及全部传递项均为 `optional`（`optional` 阻断向下游传递，消费方要用发送能力必须显式引入 sms4j-Email-core，从而连带获得其传递项）；② 无日志绑定实现（仅 `slf4j-api`，消费方自行选择绑定）；③ 无 Spring / 无 JDBC / 连接池 / 无 starter；④ 除上表外出现任何条目即视为依赖失控（`dependency:tree` 人工核对项）；⑤ 表中 `hamcrest-core`（junit `test` 传递）为实测受限传递项，非本模块 pom 直接声明、不进消费方运行 classpath，**不违背**「零强制第三方运行依赖 / 无日志绑定 / sms4j optional」三条纪律——scope 为 `test`（仅测试期），不属 sms4j 传递链且非任何日志绑定实现；精确坐标与版本以 `mvn dependency:tree` 实测为准。

**`optional` 语义必须写入 README**：不引 `org.dromara.sms4j:sms4j-Email-core:3.3.6` 则本模块发送不可用（首次调用抛可读 `MailException`，消息由 `MailErrors` 给出，不得出现裸 `NoClassDefFoundError`）——这是「能力按需引入」。sms4j 缺失的可读失败由适配层唯一探测点捕获 `ClassNotFoundException` / `LinkageError` 后包装（§7.4）。

**javax.mail 许可证声明必须写入 README + LICENSE/NOTICE**：sms4j-Email-core 传递依赖 `com.sun.mail:javax.mail:1.6.2`（JavaMail）许可证为 **CDDL 1.1 + GPL 2.0 with Classpath Exception**。法务已确认 CPE 豁免可接受（用户已定档），许可证不再是阻塞项。实现期须落地第三方声明动作：① 模块 `LICENSE` / `NOTICE` 文件声明 javax.mail 许可证；② `README.md` 注意事项声明；③ 根 `AGENTS.md` 登记 javax.mail 为 optional 传递依赖的许可证留痕（§13 事项 2）。

### 2.4 Q3 结论：编程式装配可行性（**已实证通过**）

> 实证问题：sms4j 邮件插件在**不引入 starter** 的前提下能否编程式构建邮件客户端？结论：**能，完整可行**。

| # | 结论 | 证据（javap 方法签名 / 字节码） |
| --- | --- | --- |
| 1 | `MailFactory` 提供编程式装配 API | `public static void put(Object, MailSmtpConfig)` / `public static MailClient createMailClient(Object)` / `public static MailClient createMailClient(Object, Blacklist)` / `public static void get(Object)` / `public static void remove(Object)`。**`get(Object)` 返回 `void` 已 javap 复核，非笔误**：其语义是「按 key 触发注册表加载客户端」（加载动作无返回值），与「get 通常返回值」的直觉不同；本模块不使用 `MailFactory`（选用 `MailBuild.build`，§2.4 落地结论） |
| 2 | **`MailBuild.build(config)` 可直接构建客户端，不经 Factory 静态注册表** | `public static MailClient build(MailSmtpConfig) throws MessagingException` / `build(MailSmtpConfig, Blacklist) throws MessagingException`——直接接受配置返回客户端，无需先 `put` 再 `createMailClient` |
| 3 | 构建链路**纯编程式**，不读 yaml、不需 starter | `MailBuild.build(MailSmtpConfig)` 内部创建 `javax.mail.Session` + 构造 `MailBuild` 实例 + `MailService.instance(build)` 返回 `MailClient`；全程无配置文件读取 |
| 4 | **配置对象用 builder 模式**（构造器包私有，不可直接 `new`） | `MailSmtpConfig.builder() → MailSmtpConfigBuilder`（public static）；Builder 方法：`port(String)` / `fromAddress(String)` / `nickName(String)` / `smtpServer(String)` / `username(String)` / `password(String)` / `isSSL(String)` / `isAuth(String)` / `retryInterval(int)` / `maxRetries(int)` / `build()`；MailSmtpConfig 构造器为包私有 `MailSmtpConfig(String×8, int, int)` |
| 5 | **字段类型：port/isSSL/isAuth 均为 String**（非 int/boolean） | `getPort() → String` / `getIsSSL() → String` / `getIsAuth() → String`；`getRetryInterval() → int` / `getMaxRetries() → int` |
| 6 | **发送 API 返回 void（无结果对象）** | `MailClient.send(MailMessage) → void`（`MailService implements MailClient`）；成功无异常即发送成功。**对外只抛 `org.dromara.email.comm.errors.MailException`（RuntimeException）**，`MailClient.send` / `MailService.send` 均**不声明 `throws`**（javap 复核：两方法签名均无 throws 子句）。**受检异常在 sms4j 内部被包装**：javap -c 反编译 `MailService` 私有 `send(...)` 异常表为 `catch (javax.mail.MessagingException \| java.io.IOException)`，无重试（maxRetries≤0）时 `new org.dromara.email.comm.errors.MailException(Throwable cause)` 后 `athrow`。故本模块适配层 `delegate.send(...)` 只需处理 RuntimeException，**无需 catch `MessagingException`**；cause 链中保留原始 `MessagingException` / `IOException`，供 §7.5 场景 10/11 分类 |
| 7 | **sms4j MailMessage 用 builder 构建** | `MailMessage.Builder() → MailsBuilder`；方法：`mailAddress(List/String)` / `title(String)` / `body(String)` / `cc(List/String)` / `bcc(List/String)` / `files(Map<String,String>/String,String)` / `html(String path)` / `html(InputStream)` / `html(File)` / `htmlContent(String)` / `htmlValues(Map/String,String/Parameter)` / `zipName(String)` / `build()` |
| 8 | **附件格式为 `Map<显示名, 文件路径>`**（非 byte[]/InputStream） | `MailMessage.getFiles() → Map<String,String>`；MailsBuilder `files(String name, String path)` / `files(Map<String,String>)`——适配层需将消费方 `byte[]` 写入临时文件后转为 `Map<显示名, 路径>` |
| 9 | **sms4j 自带 MailException**（`org.dromara.email.comm.errors.MailException extends RuntimeException`） | 与本模块 `cn.hg.aifei.email.MailException` 同名不同包，靠包路径隔离；适配层 catch sms4j MailException 后包装为模块 MailException |
| 10 | **HTML 模板变量替换** | `MailMessage.getHtmlValues() → Map<String,String>`；`htmlValues(Parameter)` 接受实现 `Parameter` 空标记接口的实体；模板含 `#{变量名}` 占位符，由 sms4j 内部替换 |
| 11 | **Blacklist 接口**（首期不在公共面暴露） | `org.dromara.email.api.Blacklist`：`List<String> getBlacklist()`；`MailFactory.createMailClient(Object, Blacklist)` 可传入黑名单 |
| 12 | **`html(String)` 路径语义实证（javap -c）** | `MailService.send` 对 `getHtmlPath()` 非空时调 `HtmlUtil.readHtml(String, Class<MailService>)`；反编译 `HtmlUtil.readHtml(String,Class)`：**`Class.getResourceAsStream("/template/" + path)`**——即以 `MailService.class`（sms4j 自身类）的 **classloader** 从 **classpath 根 `/template/`** 读资源（非文件系统路径）。结论：① 是 classpath 资源读取，不是文件路径；② 用 sms4j jar 的加载器而非消费方 TCCL，**库模块无法可靠读到消费方 classpath 的 `template/`**（评审 P1 #7 风险成立）。故本模块**不采用 `html(String)`**，改用 `html(InputStream)` + TCCL（§8.2 写死） |

**落地结论（与实现一致）**：`internal.smtp.SmtpClientFactory.create(channelName, options)` 负责解析 `email.<name>.*` 配置为 `Map<String, String>` → 经 `MailSmtpConfig.builder()...build()` 构建 sms4j 配置对象 → 调 `MailBuild.build(config)`（返回 sms4j `MailClient`）→ 包成 `SmtpMailSender`（本模块 `MailClient` 实现），本模块按**通道标识**经 `MailClients` 维护自己的客户端注册表。**选用 `MailBuild.build(config)` 而非 `MailFactory.put + createMailClient`**：前者无静态注册表副作用，`stop()` 时无需清理 sms4j 全局状态（对齐 aifei-sms `SmsFactory` 有静态注册表需注意的差异）。**不评估「internal 自行解析并手动构建 smtp Session」的兜底路径——该路径已被实证排除，编程式 API 完备**。

> **关键差异（与 aifei-sms 对比）**：① sms4j 邮件插件包名为 `org.dromara.email`（非 `org.dromara.sms4j`），门禁断言以此为准；② `MailClient.send()` 返回 void（sms4j 短信 `SmsBlend.sendMessage()` 返回 `SmsResponse`），故 `MailResult` 由适配层在无异常时构造成功结果（无 SMTP 返回信息可提取）；③ 附件格式为文件路径 Map（sms4j 短信无附件概念），适配层需做 `byte[] → 临时文件` 转换；④ `MailSmtpConfig` 用 builder 模式（sms4j 短信 `AlibabaConfig` 用无参构造 + setter）。

### 2.5 选型决策留痕（偏离 / 遵循默认栈记录）

| 决策 | 结论 | 证据 / 理由 |
| --- | --- | --- |
| 发送内核 | 引入 sms4j-Email-core:3.3.6（用户点名，不弃用） | §2.2 / §2.3 实测可用、Java 8 兼容、无 Spring / 无日志绑定 |
| 依赖范围 | sms4j-Email-core 为 `optional`（非 compile 例外） | 对齐 `aifei-excel` / `aifei-sms` 先例；门面类可独立加载，缺依赖仅发送不可用，不构成「脱离即无功能」的 compile 例外；根 `AGENTS.md` 无需新增 compile 例外登记（§13 事项 1） |
| 装配形态 | `EmailPlugin(Prop)` + 编程式 `MailBuild.build`，禁 starter / solon | §2.4 实证编程式可行；对齐 `aifei-oss` 只引 core 的范式；选用 `MailBuild.build` 而非 `MailFactory.put+createMailClient` 以避免静态注册表副作用 |
| javax.mail 许可证 | CDDL 1.1 + GPL 2.0 w/ CPE，法务已确认可接受 | §2.2 实证 pom 头部声明 + jar 内 LICENSE.txt；用户已定档法务闭环；实现期落地第三方声明（§13 事项 2） |
| 邮件模板引擎（aifei-enjoy） | **不在库模块内嵌，维持 D10**；消费方可用 aifei-enjoy 自行渲染后传 `htmlContent` | §2.6 当场实证：aifei-enjoy 内含于 aifei-all:1.1.2、Java 8 兼容、零新增依赖；但嵌 `cn.aifei.enjoy.*` 于 internal 会违反 D12 / 门禁 ③，且与 sms4j `#{变量名}` 语法双轨 |

### 2.6 aifei-enjoy 作为邮件模板方案的实证评估（用户新增方向，重点）

> 用户建议「邮件模板可考虑 aifei-enjoy 处理」。按门禁要求**当场实证、不凭记忆**，结论为**部分采纳**：承认其能力可用，但**决定不在 aifei-email 库模块内引入它做模板内核**（维持 D10 / D12），改为在消费方侧提供已实证可用的路径。

| 实证问题 | 命令 | 结果（证据） |
| --- | --- | --- |
| ① 仓库内是否有 aifei-enjoy 独立模块？ | `list_dir` 仓库根 + 根 `README.md` 模块表检索 | **无独立 `aifei-enjoy/` 模块目录**；它是 aifei **框架自身模块**，由 `cn.aifei:aifei-enjoy:1.1.2` 独立发布，并以 shade 方式内含于 `aifei-all:1.1.2` |
| ② aifei-all:1.1.2 是否提供 Enjoy 能力？ | `jar tf aifei-all-1.1.2.jar \| Select-String 'cn/aifei/enjoy/'` | **提供**：内含 180 个 `cn/aifei/enjoy/*.class`（`Engine` / `Env` / `Directive` / `EngineConfig` / `expr.ast.*` 等）；`jar tf aifei-enjoy-1.1.2.jar` 顶层仅 `cn/aifei/enjoy` 一个包 |
| ③ Java 8 兼容？ | `javap -verbose -classpath aifei-1.1.2.jar cn.aifei.enjoy.Engine \| Select-String "major version"` | **major version: 52**（Java 8 字节码，兼容） |
| ④ 新增依赖 / 公共面影响？ | 读 `aifei-enjoy-1.1.2.pom` + `javap cn.aifei.enjoy.Engine` | pom 仅 test-scope junit，**零第三方运行依赖**；`Engine.use()` / `getTemplate(String)` / `getTemplateByString(String)` 可用。但类型均在 `cn.aifei.enjoy.*`，属框架类型 |

**决策（D15）与理由**：

1. **能力可用（不简单拒绝）**：aifei-enjoy 确实内含于本模块已有的 `provided` 依赖 `aifei-all:1.1.2`，Java 8 兼容、零新增依赖坐标，技术上完全可作渲染内核；仓库 `aifei-i18n` 已有「复用 aifei-all 内含 Enjoy、零 pom 改动」的先例（其 `docs/需求/aifei-i18n.md` §11 实证）。
2. **不在库模块内嵌（不盲目采纳）**：① 本模块 `internal.smtp` 若 `import cn.aifei.enjoy.*`，将**直接违反 D12 与门禁 ③ `frameworkOnlyInBridgePackage`**（`cn.aifei.*` 仅允许 `aifei` 接入包），须放宽门禁——违背「门禁不得为兼容错误用法放宽」纪律；② 与 D10「不引模板引擎」冲突；③ Enjoy 指令语法（`#(x)` / `#directive`）与 sms4j `#{变量名}` 双轨并存，增加消费方学习成本与契约歧义；④ `aifei` 为 `provided`，库模块自身不应在适配层绑定框架实现类型。
3. **推荐路径（消费方侧，零本模块改动）**：复杂模板场景由消费方在调用前用 aifei-enjoy（`aifei` provided 已含）渲染为 HTML 字符串，再经 `MailMessage.htmlContent(...)` 传入——该路径 D10 已预留、公共面零变化、门禁零放宽，且已被 aifei-i18n 先例证实可行。**本模块公共面继续只承诺 `#{变量名}` 替换语义**（AC-01），不承诺 Enjoy 语法。
4. **若未来确需库模块内嵌**：须先改 §3 D10/D12 + 门禁 ③ 并提请主会话登记根 `AGENTS.md`，属独立增量，不在本期范围。

---

## 3. 设计决策表

> 格式：**决策** —— 理由 —— 被否决的替代方案。逐条对应任务输入 §三前置决策 / PRD 定档，并补充契约落地所需的衍生决策。

| # | 决策 | 理由 | 被否决的替代方案 |
| --- | --- | --- | --- |
| **D1**（技术路线） | **基于 sms4j-Email-core:3.3.6 二次封装，发送全走 `MailBuild` / sms4j `MailClient`**；`internal.smtp` 单点引用 `org.dromara.email.*` / `javax.mail.*` | 用户点名锁定；§2 实测可用；对齐 `aifei-oss` / `aifei-sms` 聚合门面 + 适配层隔离 | ① 自研 SMTP 发送「等价替代」——违反铁律、重复造轮子；② 裸用 javax.mail——换 SMTP 配置即重写发送代码，违背统一门面目标 |
| **D2**（§三-3.1） | **引入 sms4j-Email-core（非 starter）**，在 `EmailPlugin(Prop).start()` 编程式装配；禁任何形式的 starter | §2.4 实证编程式 API 完备（`MailBuild.build(config)` 直接返回 MailClient）；aifei 非 Spring 非 Solon，装配统一 Plugin + Prop | 引入 starter → 拖入 Spring / Solon 自动装配依赖，破坏「公共面零框架类型」与依赖纪律 |
| **D3**（§三-3.2） | **根包 + `spi` 公共面零第三方 / 零框架类型**；sms4j 邮件插件类型只在 `internal.smtp` | 公共 API 实现无关，换内核不破坏二进制兼容 | 公共方法暴露 sms4j `MailClient` / `MailSmtpConfig` → 消费方被迫依赖 sms4j |
| **D4**（§三-3.3） | **sms4j-Email-core 为 `optional`**；缺失首次调用抛可读 `MailException`；javax.mail 许可证 CDDL+GPL+CPE 法务已确认可接受 | §2.2 实证 Java 8 兼容；`aifei-excel` / `aifei-sms` optional 先例；门面可独立加载 | ① `compile`（像 aifei-oss）——需根登记新例外，且 sms4j 不构成「脱离即无功能」；② 裸抛 `NoClassDefFoundError`——违反 R-04 |
| **D5**（Q7 / §三-3.8） | **根包精确 5 个公共类型**：`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`；`MailResult` **独立成类**；门禁按文件名集合精确相等断言 | 发送结果需承载「状态 + 通道标识」，独立值对象职责清晰；集合相等可防同名替换（`aifei-excel` 决策 D3 / `aifei-sms` D5 先例） | ① 用 `boolean` 返回值代替 `MailResult`——无法承载通道标识与消息；② 把结果塞进 `MailMessage`——消息与结果语义混淆 |
| **D6**（§三-3.4 / §四基线 13） | **只提供同步发送**，不暴露异步 API、不自带线程池 | 仓库纪律「库模块不自带线程模型」；sms4j 邮件插件的重试功能倾向关闭（D7）；异步由消费方包装 | 提供异步 API → 引入线程池生命周期管理，工期增加 |
| **D7**（§三-3.4 / Q4） | **`maxRetries=0` 关闭重试**：`email.*` 配置默认 `max-retries=0`（配置层可配，§6.1）；**Java 公共 API 不暴露重试参数**（措辞修订：原「不在公共面暴露重试参数」易与配置层含 `max-retries` 冲突，明确为「公共 API 不暴露、配置层可配」） | 仓库纪律「库模块不隐藏失败」；重试可能掩盖 SMTP 配置问题或网络故障；对齐 `aifei-sms` / `aifei-mq` / `aifei-audit` 同步口径 | 保留默认重试且在公共 API 暴露 → 隐藏失败，排障困难；若保留须在 `MailResult` 暴露重试次数，增加公共面复杂度 |
| **D8**（§五-5.3 探测时机） | **依赖探测在 `EmailPlugin.start()` 执行**（启动即失败），`internal.smtp.MailDeps` 是唯一 `Class.forName` 点 | 有 Plugin 装配期，启动即失败优于延迟到首次发送；与 R-03「配置非法 / 依赖缺失启动即失败」一致；`MailKit` 未装配调用另有「插件未启动」保护 | 首次发送探测 → 问题延迟暴露；与 R-03 启动期校验口径不一致 |
| **D9**（§四基线 6 命名） | `MailKit` 属 **Kit**（有状态门面，需先 `EmailPlugin.start()`），不得改名 `MailUtil`；`MailClient` 为接口 | 根 `AGENTS.md` 命名约定 §「XxxKit 有状态门面」；对齐 `AuditKit` / `OssKit` / `SmsKit` | 改名 `MailUtil` → 违反定档与命名口径 |
| **D10**（§三-3.6） | **库模块不引模板引擎**：只用 sms4j 内置 `#{变量名}` 替换（`htmlValues(Map<String,String>)`）；复杂模板由消费方在调用前自行渲染 HTML 后以 `htmlContent` 传入。**经实证评估后决定不在库模块内嵌 aifei-enjoy**（决策 D15 / §2.6），但明确推荐消费方用 `aifei-all`（provided，已内含 aifei-enjoy）自行渲染后传 `htmlContent` | 模板引擎引入额外依赖与学习成本；sms4j 内置 `#{变量名}` 已满足简单变量场景；在 `internal.smtp` 嵌 `cn.aifei.enjoy.*` 会违反 D12 / 门禁 ③（cn.aifei.* 仅限 aifei 包）；消费方侧渲染路径天然可用且不动公共面（D15 实证） | ① 引 FreeMarker / Thymeleaf → 须在根 `AGENTS.md` 登记 compile 依赖，违反「零强制第三方运行依赖」纪律；② 在库模块内嵌 aifei-enjoy → 放宽门禁 ③ + 与 D12 冲突 + 模板语法与 sms4j `#{变量名}` 双轨（D15） |
| **D11**（mock 注入点） | 在 `internal.smtp.SmtpClientFactory` 设 **`setClientFactory(Function<MailSmtpConfig, org.dromara.email.api.MailClient>)`** 包级注入点，供测试 / demo 注入 mock sms4j `MailClient`；默认走 `MailBuild.build` | 任务输入定档「适配层可注入点 mock」；不新增公共 API、不污染门禁；对齐 `aifei-sms` D11 范式 | 要求消费方真实发邮件测试——不可重复、有外网依赖 |
| **D12**（§四基线 9 日志） | **除 `aifei` 接入包外全模块零 `cn.aifei.*` 引用；尤其不引 `cn.aifei.log.*`**，失败即抛 `MailException`。`aifei` 接入包（`EmailPlugin` / `EmailConfigLoader`）例外：必须 `import cn.aifei.*`（`Plugin` / `Prop` / `AopKit`，门禁 ⑫） | 对齐 `aifei-excel` D2 / `aifei-sms` D12：避免装配期 `NoLogFactory` 陷阱；异常即契约；`aifei` 包引用框架属 Plugin 接入构件（§13 事项 1，非公共面框架类型例外），根 `AGENTS.md` 无需登记 | 用 `cn.aifei.log.Log` 记日志 → 引入装配期风险且需登记例外，无收益 |
| **D13**（§三-3.7 / Q2） | **首期预留 `MailProvider` SPI 扩展点**：`spi.MailProvider` 接口（`type()` + `createClient(String, Map<String,String>) → MailClient`）+ `internal.MailProviderRegistry`（`ServiceLoader` 发现）+ `internal.smtp.SmtpMailProvider`（SMTP 默认实现，包装 sms4j 邮件插件）+ `META-INF/services/cn.hg.aifei.email.spi.MailProvider`；各厂商 SDK 为 `optional` | 对齐 `aifei-mq` 的 `MqProvider` + META-INF/services 扩展范式（已验证可行）；预留接口不影响实现量（SMTP provider 是唯一实现），但为未来接 SendGrid / SES / Mailgun / 阿里云邮件 HTTP API 预留路径，避免届时改公共面 | 若不预留，未来接厂商 HTTP API 需改公共面或硬编码 if-else 分支，违反开闭原则 |
| **D14**（附件转换） | **消费方以 `byte[]` 传入附件**（`MailMessage` 字段 `Map<String, byte[]>`），适配层 `SmtpMappers` 写入临时文件后转为 sms4j `Map<显示名, 路径>`，发送后清理临时文件 | sms4j 原生附件用文件路径（§2.4 实证）；`byte[]` 适配无文件系统环境（云函数 / 容器临时目录受限）；不入公共面 | ① 公共面暴露 `Map<String, String>`（文件路径）→ 消费方须提供文件路径，不适配无文件系统环境；② 公共面暴露 `InputStream` → 适配层需在读完后关闭流，消费方可能复用流，语义复杂 |

---

## 4. 架构分层与包边界

### 4.1 包结构

```text
cn.hg.aifei.email                    根包：5 公共类型（发送门面 / 消息 / 结果 / 异常），零第三方、零框架类型
cn.hg.aifei.email.spi                通道扩展 SPI（1 公共类型）：MailProvider（消费方实现 / 厂商扩展），零第三方、零框架类型
cn.hg.aifei.email.aifei              接入层：EmailPlugin / EmailConfigLoader —— 唯一允许 import cn.aifei.* 的包
cn.hg.aifei.email.internal           适配层：通道注册表 / 依赖探测包装 / 异常构造 / Provider 注册表 / 通道配置值对象
   └─ internal.smtp                  唯一允许 import org.dromara.email.* / javax.mail.* / com.sun.mail.* 的子包
```

**依赖方向（单向，门禁 §9 强制）**：

```text
根包 / spi      ──→ 纯 JDK + 本模块类型（spi 可被 internal 引用，但不反向依赖 internal 具体类型）
aifei 接入包     ──→ 根包 + spi + internal + cn.aifei.*（框架 Plugin / Prop / AopKit）
internal        ──→ 根包 + spi + JDK；不引用 org.dromara.email.* / javax.mail.*（引用经 internal.smtp）
internal.smtp   ──→ sms4j 邮件插件 API（org.dromara.email.*）+ javax.mail.* + JDK；不得引用 cn.aifei.*
禁止：根包 / spi 出现 org.dromara.email.* / javax.mail.* / com.sun.mail.* / cn.aifei.*；
      任何库模块 .java import io.undertow.* / java.sql.*；
      internal（除 smtp 子包）import org.dromara.email.* / javax.mail.* / com.sun.mail.*；
      任何 .java import org.slf4j.*（库模块零日志绑定）
```

### 4.2 架构门禁（`ArchitectureBoundaryTest`）

| 类型族 | 允许出现的包 | 门禁断言方法 |
| --- | --- | --- |
| `org.dromara.email.*` | **仅** `cn/hg/aifei/email/internal/smtp/` | `sms4jEmailOnlyInAdapter` |
| `javax.mail.*` / `com.sun.mail.*` | **仅** `cn/hg/aifei/email/internal/smtp/` | `javaxMailOnlyInAdapter` |
| `cn.aifei.*` | **仅** `cn/hg/aifei/email/aifei/` | `frameworkOnlyInBridgePackage` |
| `java.sql.*` / `io.undertow.*` | **全模块禁止** | `noSqlOrUndertowInLibrary` |
| `org.slf4j.*`（含 test） | **全模块禁止** | `noSlf4jBinding` |
| 根包公共类型数量 | **精确 5 个**且文件名集合精确 | `publicSurfaceExact` |
| spi 包类型数量 | **精确 1 个**且文件名集合精确 | `spiSurfaceExact` |
| 公共签名零第三方 / 零框架类型 | 根包 + `spi` 包 | `publicSignaturesNoThirdParty` |
| `Class.forName` 唯一 | 仅 `internal/smtp/MailDeps.java` | `classForNameOnlyInDeps` |
| 异常消息前缀唯一 | 仅 `internal/MailErrors.java` | `errorMessagesOnlyInErrors` |
| Java 8 语法 / API | 全模块 | `java8Compatibility` |

完整断言清单（14 例）见 §9。

---

## 5. 公共接口契约（Q7 定稿：根包精确 5 类型 + spi 精确 1 类型）

根包 `cn.hg.aifei.email` **精确 5 个公共类型**，为本节与门禁 `EXPECTED_ROOT_TYPES_SET`（§9）的**唯一事实来源**。逐字签名如下（**编码角色照抄**；`static` 与否、返回类型、参数顺序均不得改动）。所有公共方法均**不声明 `throws` 子句**（`MailException` 为 `RuntimeException`），但 javadoc **必须**写全 `@param` / `@return` / `@throws MailException`。

### 5.1 `MailClient`（发送门面接口）

```java
public interface MailClient {
    MailResult send(MailMessage message);
    String channelName();
}
```

契约要点：

| 方法 | 契约 | 失败 |
| --- | --- | --- |
| `send(message)` | **同步发送**（R-01 主路径）：同步阻塞至 SMTP 返回结果；成功返回 `MailResult`（`isSuccess=true`）；`message` 为 `null` → 参数错误 | 参数非法 / 依赖缺失 / 插件未启动 / SMTP 错误 / 网络异常 / 认证失败 → `MailException` |
| `channelName()` | 返回该实例绑定的通道标识（`email.<name>` 的 `<name>`），便于日志与排障 | 无 |

- 由 `EmailPlugin` 注册为单例，经 `@Inject MailClient` 取**默认通道**；命名通道经 `MailKit.use(name)`。
- **线程安全**：实现无可变共享状态，底层 sms4j `MailClient` 线程安全由 sms4j 保证；模块不额外加锁。
- **发送失败不返回 `MailResult(isSuccess=false)`，而是抛 `MailException`**（R-01 / AC-01）：底层 sms4j `MailClient.send()` 返回 void（无结果对象），成功无异常即发送成功，失败抛异常。

### 5.2 `MailKit`（静态门面，`final` + 私有构造 + 全 `static`）

```java
public final class MailKit {
    private MailKit() {
    }

    public static MailClient defaultClient();
    public static MailClient use(String channelName);
    public static Set<String> channels();
    public static MailResult send(MailMessage message);
}
```

契约要点：

| 方法 | 契约 | 失败 |
| --- | --- | --- |
| `defaultClient()` | 取默认通道客户端（由 `email.default-channel` 指定，或单通道时取该通道） | 未装配 / 已停止 → `MailException("email: plugin not started or stopped ...")`（§7.5 场景 5） |
| `use(channelName)` | 按通道标识取命名通道客户端 | 通道名不存在 → `MailException`（场景 6） |
| `channels()` | 返回已装配通道标识集合（不可变 `Set`） | 未装配 / 已停止 → `MailException` |
| `send(message)` | 便捷方法，等价 `defaultClient().send(message)` | 同 `defaultClient()` 与 `MailClient.send` |

- **属 Kit**（有状态门面）：须先 `EmailPlugin.start()` 装配；未装配 / 已停止调用即失败（对齐 `AuditKit` / `SmsKit` javadoc `@throws MailException 插件未启动或已停止`）。
- 命名依据见 §1.3 / D9，不得改名 `MailUtil`。

### 5.3 `MailMessage`（发送消息值对象，不可变，Builder 模式）

```java
public final class MailMessage {

    MailMessage();

    public static MailMessage.Builder builder();

    public List<String> getTo();                    // 收件人（多），不可变 List
    public List<String> getCc();                    // 抄送（多），不可变 List，可为空
    public List<String> getBcc();                   // 密送（多），不可变 List，可为空
    public String getSubject();                     // 主题
    public String getBody();                        // 正文（纯文本），可为空（HTML 邮件时可空）
    public String getHtmlContent();                 // HTML 内容（可选，非空时邮件为 HTML 格式）
    public String getHtmlTemplate();                // HTML 模板名（可选，适配层经 TCCL 从 classpath template/ 加载，§8.2）
    public Map<String, String> getTemplateVariables();  // 模板变量（#{变量名} 替换），不可变 Map，可为空
    public Map<String, byte[]> getAttachments();    // 附件（显示名 → 字节），不可变 Map，可为空

    public static final class Builder {
        public Builder to(String... addresses);
        public Builder to(List<String> addresses);
        public Builder cc(String... addresses);
        public Builder cc(List<String> addresses);
        public Builder bcc(String... addresses);
        public Builder bcc(List<String> addresses);
        public Builder subject(String subject);
        public Builder body(String body);
        public Builder htmlContent(String htmlContent);
        public Builder htmlTemplate(String templateName);
        public Builder templateVariables(Map<String, String> variables);
        public Builder templateVariable(String key, String value);
        public Builder attachment(String name, byte[] data);
        public Builder attachments(Map<String, byte[]> attachments);
        public MailMessage build();
    }
}
```

契约要点：

| 关注点 | 契约 |
| --- | --- |
| 收件人 | `to` 至少一个地址（`build()` 校验，§7.5 场景 1）；地址格式校验由 SMTP 层兜底，模块不做正则 |
| 主题 | `subject` 不得为空白（场景 2） |
| 正文 | `body`（纯文本）、`htmlContent`（HTML）、`htmlTemplate`（模板名）三者关系：**`htmlContent` 与 `htmlTemplate` 互斥，同时非空 → 参数异常**（§7.5 场景 12）；`templateVariables` **仅对 `htmlTemplate` 生效**，对 `htmlContent` 不做事后替换（避免二义性）。若 `htmlContent` 非空 → 直接用该 HTML 串（消费方若用 aifei-enjoy 渲染，走此入口，D15）；若 `htmlTemplate` 非空 → 适配层经 TCCL 从 classpath `template/` 加载 + `#{变量名}` 替换（§8.2）；若仅 `body` → 纯文本邮件 |
| 附件 | `attachments` 的 `byte[]` 由适配层 `SmtpMappers` 写入临时文件后转为 sms4j `Map<显示名, 路径>`，发送后清理（D14）；`byte[]` 为空或 null → 忽略该附件 |
| 不可变性 | `build()` 后所有 getter 返回不可变副本（`Collections.unmodifiableList` / `Map`）；`getAttachments()` 的 `byte[]` 在 `build()` 时与每次 `get` 时均逐项 `clone()`（`unmodifiableMap` 只保护 Map 结构、不保护 byte[] 内容，故必须 clone 防篡改） |
| Builder | 内部类 `Builder` 不增加根包类型计数（同一文件） |

> **公共面计数口径**：`MailMessage` + 内部 `Builder` 属根包 5 类型之一——`Builder` 是 `MailMessage` 的内部类，不增加顶层 `.java` 文件计数。

### 5.4 `MailResult`（发送结果值对象，不可变）

```java
public final class MailResult {
    public MailResult(String channelName, boolean success, String message);  // ★ public：唯一构造点在异包 internal.smtp.SmtpMailSender，与 MailException 同策略
    public String getChannelName();      // 通道标识
    public boolean isSuccess();          // 发送是否成功
    public String getMessage();          // "sent"（成功时），可空
}

// 位于 aifei 包，非根包计数；实际源码以 import 引入简名 Plugin / Prop（非全限定名书写）
public final class EmailPlugin implements Plugin {
    public EmailPlugin(Prop prop);                 // prop == null → MailErrors.propNull()
    @Override public synchronized void start();
    @Override public synchronized void stop();
}
```

- `MailResult` 字段语义：
  - `channelName`：通道标识；
  - `isSuccess`：发送是否成功（底层 sms4j `send()` 返回 void，成功无异常即 `true`）；**当前恒为 `true`**——因发送失败一律抛 `MailException`，不存在 `isSuccess=false` 的结果对象；保留该字段是为结果对象将来扩展（如异步回执）预留，维护 API 完整性与语义直观性，不构成冗余缺陷；
  - `message`：成功时为 `"sent"`（sms4j 不提供 SMTP 返回信息，无流水号可提取）。
- **构造器可见性**：`public MailResult(String, boolean, String)`——`MailResult` 在根包，唯一构造点 `SmtpMailSender` 在异包 `internal.smtp`，跨包必须 `public`（与 `MailException` 的 `public` 构造器同策略，§5.7）。门禁不因 public 构造器增加顶层类型计数。
- **发送失败不返回 `MailResult(isSuccess=false)`，而是抛 `MailException`**（R-01 / AC-01）。

> 注：上代码块中的 `EmailPlugin` 仅用于对比展示签名形态，**其实际归属包为 `cn.hg.aifei.email.aifei`**（见 §5.5），不计入根包 5 类型。Q7 冻结名单见 §5.6。

### 5.5 接入构件与 SPI（非根包计数）

```java
// cn.hg.aifei.email.aifei —— 接入层，唯一允许 import cn.aifei.*；均经 import 引入简名
public final class EmailPlugin implements Plugin { /* 签名见 §5.4 */ }

// cn.hg.aifei.email.aifei.EmailConfigLoader —— ★ email.* 配置键名解析唯一定义点
public final class EmailConfigLoader {
    // ★ 包私有：返回 internal 类型 MailChannelConfig，不作为公共 API 暴露；同包 EmailPlugin 直接调用，
    //   测试经 EmailPlugin.start() 间接验证（避免 public 方法泄漏 internal 类型）
    static List<MailChannelConfig> load(Prop prop);                   // 全量解析 + 校验
    static String defaultChannel(Prop prop, List<MailChannelConfig> configs);
}

// cn.hg.aifei.email.spi —— 通道扩展 SPI（1 个公共类型），零第三方 / 零框架类型
public interface MailProvider {
    String type();                                                    // 通道类型标识，如 "smtp"
    MailClient createClient(String channelName, Map<String, String> options);  // 按配置创建客户端
}
```

> **SPI 签名决策（D13）**：`MailProvider` 作为**通道扩展 SPI 落在 `spi` 包**——类型为接口、纯 JDK 字段（`String` / `Map` / 返回本模块 `MailClient`），零第三方 / 零框架类型（AC-08 / AC-09 合规）。**理由**：公共 SPI 的签名不得引用 `internal`（非承诺实现层）类型，否则消费方被迫依赖可随时重构的内部包；把接口提升到 `spi` 使公共契约自洽、实现无关。**首期唯一实现**：`internal.smtp.SmtpMailProvider`（包装 sms4j 邮件插件），经 `META-INF/services/cn.hg.aifei.email.spi.MailProvider` 发现。**被否决方案**：`MailProvider` 落 `internal`（像 aifei-mq 的 `MqProvider`）——虽可工作，但消费方无法在不依赖 internal 的前提下实现自定义厂商 provider，与「为未来接厂商 HTTP API 预留路径」的设计意图不符。

### 5.6 Q7 定稿：公共面精确名单（冻结）

| 包 | 精确类型 | 数量 | 门禁常量 |
| --- | --- | --- | --- |
| 根包 `cn.hg.aifei.email` | `MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException` | **5** | `EXPECTED_ROOT_TYPES_SET`（§9） |
| `cn.hg.aifei.email.spi` | `MailProvider` | **1** | `EXPECTED_SPI_TYPES_SET`（§9） |
| `cn.hg.aifei.email.aifei` | `EmailPlugin` / `EmailConfigLoader` | 2（接入构件，非承诺 API，不计数） | — |

**根包公共类型精确 5 个**（文件名集合精确相等，多一个或少一个即失败）；**`spi` 包精确 1 个**（`MailProvider.java`，集合精确相等）。`MailException` 的 `public` 构造器不增加顶层类型计数（同一文件）。`MailMessage.Builder` 为内部类，不增加顶层 `.java` 文件计数。`EmailPlugin`/`EmailConfigLoader`（aifei）不计入根包 5 个。此表即 AC-09 门禁断言的冻结名单，回写任务输入 §五 5.4 与 AC-09。

### 5.7 命名依据汇总

| 类型 | 形态 | 命名依据 |
| --- | --- | --- |
| `MailClient` | 接口，名词性后缀 | 对齐 `HttpClient` / `OssClient` / `SmsClient`（根 `AGENTS.md` §命名约定） |
| `MailKit` | `public final` + 私有构造 + 全 `static` | 有状态门面（需先 `EmailPlugin.start()`），对齐 `OssKit` / `AuditKit` / `SmsKit`（D9） |
| `MailMessage` | `public final` 不可变值对象 + Builder | 名词，消息值对象（D5）；Builder 为内部类 |
| `MailResult` | `public final` 不可变值对象 | 名词，发送结果语义分离（D5） |
| `MailException` | `public final extends RuntimeException` | 模块唯一异常；`public` 构造器因唯一构造点在异包 `internal`（§7.5） |
| `MailProvider` | 接口，SPI | 通道扩展 SPI，消费方 / 厂商实现（D13） |
| `EmailPlugin` / `EmailConfigLoader` | 接入构件 | 对齐 `OssPlugin` / `OssConfigLoader` / `SmsPlugin` / `SmsConfigLoader` |

---

## 6. 配置模型（`email.*`）

### 6.1 配置键全集

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `email.default-channel` | 多通道时必填 | 默认通道标识；`@Inject MailClient` 对应此通道；单通道时可缺省（取唯一通道） |
| `email.<name>.smtp-server` | 是 | SMTP 服务器地址（映射 `MailSmtpConfig.smtpServer`） |
| `email.<name>.port` | 是 | SMTP 端口（映射 `MailSmtpConfig.port`，String 类型） |
| `email.<name>.from-address` | 是 | 发件人地址（映射 `MailSmtpConfig.fromAddress`） |
| `email.<name>.username` | 是 | SMTP 认证用户名（映射 `MailSmtpConfig.username`） |
| `email.<name>.password` | 是 | SMTP 认证密码（映射 `MailSmtpConfig.password`） |
| `email.<name>.is-ssl` | 否（默认 `"true"`） | 是否启用 SSL（映射 `MailSmtpConfig.isSSL`，String `"true"`/`"false"`） |
| `email.<name>.is-auth` | 否（默认 `"true"`） | 是否启用认证（映射 `MailSmtpConfig.isAuth`，String `"true"`/`"false"`） |
| `email.<name>.nick-name` | 否 | 发件人显示名称（映射 `MailSmtpConfig.nickName`） |
| `email.<name>.retry-interval` | 否（默认 `5`） | 重试间隔秒数（映射 `MailSmtpConfig.retryInterval`，int） |
| `email.<name>.max-retries` | 否（默认 `0`） | 最大重试次数（映射 `MailSmtpConfig.maxRetries`，int；**默认 0 = 不重试**，D7） |

> **凭据安全**：`password` 等敏感值一律走 `app-config.txt`，禁止硬编码（工程规范 §3）；门禁读取配置只核键名不复制值。
> **字段类型映射（实证）**：`port` / `isSSL` / `isAuth` 在 sms4j `MailSmtpConfig` 中均为 **String 类型**（非 int / boolean，§2.4 实证）；`retryInterval` / `maxRetries` 为 int。配置值从 `Prop` 读取为 String，直接映射到 String 字段；int 字段经 `Integer.parseInt` 转换，格式非法 → 启动失败（消息含键名与原值）。

### 6.2 通道发现与校验规则（唯一定义点 `aifei.EmailConfigLoader` + `internal.smtp.SmtpClientFactory`）

> 对齐 `aifei-oss` / `aifei-sms`：`EmailConfigLoader` 在 `aifei` 包（可读 `Prop`），配置规则委托 `internal`。本模块同理：`aifei.EmailConfigLoader` 读 `Prop`，通道配置项 → `MailSmtpConfig` builder 的填充在 `internal.smtp.SmtpClientFactory`。

1. **通道发现**：扫描 `prop.getProperties()` 中所有匹配 `email.<name>.smtp-server` 的键，`<name>` 即通道标识；`<name>` 不得为空白、不得含 `.`（否则键解析歧义）、**不得为保留字 `default-channel`**（否则 `email.default-channel.smtp-server` 会被误识别为名为 `default-channel` 的通道；该保留字专用于 `email.default-channel` 配置项）。
2. **至少一条通道**：无任何 `email.<name>.smtp-server` → 启动失败（场景 3）。
3. **必填项**：按通道逐一校验 `email.<name>.<key>` 非空，必填键 = `{smtp-server, port, from-address, username, password}`；缺失 → 启动失败，消息含**完整键名与原值**（原值为空显示 `<empty>`，场景 3）。
4. **int 字段校验**：`retry-interval` / `max-retries` 若提供则须为合法整数，否则 → 启动失败（消息含键名与原值）。
5. **默认通道**：多通道时 `email.default-channel` 必填且须指向已声明通道；单通道时可缺省取唯一通道；值指向不存在通道 → 启动失败（场景 7）。
6. **键名 → builder**：`kebab-case` 键名（如 `smtp-server`）转 `camelCase`（`smtpServer`）后按字段映射填 `MailSmtpConfig.builder()...build()`；未知配置项忽略（不报错，便于透传 sms4j 新参数）。**拼写错误的排障口径（P2）**：`email.<name>.*` 下未识别键（如 `smtp-servr`）会被忽略，可能导致「必填项缺失 → 场景 4」而非明确的「未知键」错误；因本模块零日志绑定（D12）不运行时告警，改由 `README.md` 配置章节 + `docs/testing.md` 明确列出「合法键全集（§6.1）以便核对拼写」，并将「严格模式（未知键即启动失败）」登记为未来增强（§13 事项 10，本期不做）——本期维持透传优先，不引入会破坏零日志纪律的运行时告警。
7. **默认值填充**：`is-ssl` / `is-auth` 缺失时填 `"true"`；`retry-interval` 缺失时填 `5`；`max-retries` 缺失时填 `0`（D7 关闭重试）。
8. **失败不留半装配**：`EmailPlugin.start()` 内先全量解析校验成 `List<MailChannelConfig>`，全部合法后才构建客户端并注册；任一步失败 → 清空本模块注册表后再上抛（§7.2）。

---

## 7. 内部结构、依赖探测与行为契约

### 7.1 各类型职责（★ 后为该规则的唯一定义点）

| 类型 | 包 | 职责 |
| --- | --- | --- |
| `MailClient` / `MailKit` | 根 | 发送门面；委托 `internal.MailClients` 取实例 |
| `MailMessage` / `MailResult` / `MailException` | 根 | 值对象 / 统一异常；三者 `public` 构造器因唯一构造点在异包 `internal.smtp`：`MailMessage` 经 Builder、`MailResult` 由 `SmtpMailSender`、`MailException` 由 `MailErrors` 构造（§5.3/§5.4/§7.5） |
| `MailProvider` | spi | 通道扩展 SPI（消费方 / 厂商实现）；`type()` + `createClient(channelName, options)` |
| `EmailPlugin` | aifei | ★ 装配生命周期：`implements Plugin`、`start/stop` 幂等、失败回滚、注册 `@Inject` 单例（带 `singletonRegistered` 守卫）、调用 `EmailConfigLoader` + `MailDeps` + `MailProviderRegistry` |
| `EmailConfigLoader` | aifei | ★ `email.*` 配置键名解析唯一定义点（读 `Prop`，返回 `List<MailChannelConfig>`） |
| `MailChannelConfig` | internal | **单通道配置值对象（非承诺实现层，非计数）**：不可变，承载 `name` / 原始 kebab-case `options` Map |
| `MailClients` | internal | ★ 通道注册表：按通道标识存 `MailClient` 实例 + 默认通道 + `started` 状态；`register` / `registerDefault` / `defaultClient` / `use` / `channels` / `isStarted` / `setStarted` / `clear`（进程内 `ConcurrentHashMap`） |
| `MailErrors` | internal | ★ `MailException` 唯一构造点 + ★ 全部异常消息措辞唯一定义点（§7.5） |
| `MailProviderRegistry` | internal | ★ Provider 发现：`ServiceLoader.load(MailProvider.class)` 按 `type()` 建索引；`getProvider(type)`（找不到返回 `null`，由 `EmailPlugin.start()` 判空抛场景 13）/ `types()`；同 type 多实现 → 装配期错误 |
| `MailDeps` | internal.smtp | ★ 唯一 `Class.forName` 点（`org.dromara.email.core.factory.MailFactory`，不初始化，第二参 `false`）；`requireSms4j()` + 包级 `requireSms4j(ClassLoader)`（测试注入）；缺失抛可读异常 |
| `SmtpMailProvider` | internal.smtp | ★ SMTP 通道 `MailProvider` 实现：`type()` 返回 `"smtp"`；`createClient(channelName, options)` 委托 `SmtpClientFactory.create`；**类签名无 sms4j 类型**（sms4j 引用仅在方法体，确保 ServiceLoader 可在 sms4j 缺失时加载本类） |
| `SmtpClientFactory` | internal.smtp | ★ 客户端构建唯一装配点：`create(channelName, options) → MailClient`，内部经 `MailSmtpConfig.builder()...build()` → `MailBuild.build(config)` → `new SmtpMailSender(channelName, sms4jMailClient)`；★ mock 注入点 `setClientFactory(Function)`（D11） |
| `SmtpMailSender` | internal.smtp | 发送实现：`implements MailClient`，构造 `SmtpMailSender(String channelName, org.dromara.email.api.MailClient delegate)`，调 `delegate.send(mapped.getMessage())`，**在 `finally` 中按 `mapped.getTempFiles()` 清理临时文件**（P1 #6），结果 / 异常经 `SmtpMappers` / `MailErrors` 翻译 |
| `SmtpMappers` | internal.smtp | `toSms4jMessage(MailMessage) → MappedMessage`：本模块 `MailMessage` → sms4j `MailMessage`（含附件 `byte[]` → 临时文件 → `Map<显示名, 路径>` 转换，D14）；`cleanupTempFiles(List<Path>)`：按传入列表清理，**无静态字段 / ThreadLocal 跟踪**（P1 #6） |
| `MappedMessage` | internal.smtp | ★ 映射结果载体（非承诺构件）：不可变，含 `getMessage() → org.dromara.email.comm.entity.MailMessage` + `getTempFiles() → List<Path>`（本次调用产生的临时文件）。由 `toSms4jMessage` 每次新建返回，`SmtpMailSender.send` 持有并在 finally 按 `getTempFiles()` 清理，杜绝并发下静态字段 / ThreadLocal 泄漏与串扰（P1 #6） |
| `DefaultMailClientProxy` | internal | ★ `@Inject` 单例的动态委托代理（非承诺构件）：`implements MailClient`，**不缓存具体 client**；`send()` / `channelName()` 每次调用都经 `MailClients.defaultClient()` 现取当前注册表的默认通道客户端再委托。注册一次即可，stop→start 后配置变更自动指向新 `SmtpMailSender`（P1 #10 方案②） |

> **非承诺构件计数口径**：`MailChannelConfig`（internal）、`MailClients`（internal）、`MailProviderRegistry`（internal）、`DefaultMailClientProxy`（internal）、`SmtpMailProvider` / `SmtpClientFactory` / `SmtpMailSender` / `SmtpMappers` / `MappedMessage` / `MailDeps`（internal.smtp）均为非承诺实现层构件，**不计入根包 5 与 spi 1 的冻结公共面计数**（门禁 ⑤ 的计数口径不变，仍按 §5.6 冻结名单断言）。

### 7.2 装配生命周期（`EmailPlugin`）

对齐 `OssPlugin` / `SmsPlugin` / `MqPlugin` / `AuditPlugin` 范式：

```text
EmailPlugin(Prop prop)               // 构造期只持引用，null → MailErrors.propNull()，不解析不校验
  - if (prop == null) throw MailErrors.propNull()
  - this.prop = prop

start()  [synchronized, 幂等]
  if started → return
  try:
    MailDeps.requireSms4j()                                   // ① 依赖探测（D8：启动即失败）
    List<MailChannelConfig> configs = EmailConfigLoader.load(prop)   // ② 全量解析+校验（失败含键名原值）
    String defaultChannel = EmailConfigLoader.defaultChannel(prop, configs)
    MailProvider provider = MailProviderRegistry.getProvider("smtp")  // ③ 取 SMTP provider（ServiceLoader 发现）
    if provider == null: throw MailErrors.providerMissing("smtp")    // ★ 明确判空，避免 NPE（场景 13，P0 #5）
    for each config:
        MailClient client = provider.createClient(config.getName(), config.getOptions())  // ④ SmtpMailProvider → SmtpClientFactory → MailBuild.build → SmtpMailSender
        MailClients.register(config.getName(), client)
    MailClients.registerDefault(defaultChannel)
    MailClients.setStarted(true)                              // ⑤ 共享装配状态置位
    if (!singletonRegistered):                                // ⑥ @Inject 单例，addSingletonObject 不幂等，仅注册一次
        AopKit.get().addSingletonObject(MailClient.class, new DefaultMailClientProxy())  // ★ 注册动态委托代理，非固定 SmtpMailSender（P1 #10）
        singletonRegistered = true
    started = true
  catch (RuntimeException | Error e):
    MailClients.clear()                                       // 失败回滚，不留半装配（含 started 复位）
    throw e

stop()   [synchronized, 幂等]
  if !started → return
  MailClients.clear()                                         // 清空注册表 + 默认通道 + started 复位
  started = false
  // singletonRegistered 不复位（框架无注销 @Inject 单例的公开 API，对齐 OssPlugin/MqPlugin/SmsPlugin）
```

**stop() 后调用语义（AC-04）**：`stop()` 后 `MailKit.*` 与已注入的 `MailClient` 实例调用 `send(...)` 时，`SmtpMailSender.send` 首先检查 `MailClients.isStarted()`，为 `false` → 抛 `MailException("email: plugin not started or stopped, call EmailPlugin.start() first")`（§7.5 场景 5）。注入的 `DefaultMailClientProxy` 每次 `send()` 经 `MailClients.defaultClient()` 现取——stop 后该调用同样触发场景 5，不会出现「代理持有旧 client 且发送成功」的假象。

**stop→start 语义（写死，P1 #10 方案②）**：`singletonRegistered` 保持不复位（框架无注销 `@Inject` 单例的公开 API），但因注册的是 `DefaultMailClientProxy`（动态委托代理），二次 `start()` 重建注册表后，注入单例的 `send()` / `channelName()` 自动委托到新装配的 `SmtpMailSender`，**两次 start 之间配置变更（换 SMTP / 换账号）即时生效，无需重启应用**。这兼容 AC-05 的「重装配」语义并消除了「注入单例仍指向旧 delegate」的陈旧缺陷。若改用「注册固定 SmtpMailSender」的方案①，则必须声明「stop→start 后注入单例不刷新、配置变更需重启应用」——本设计**不采用**方案①。

**幂等与失败无残留**：`start()` / `stop()` 均 `synchronized` + `started` 守卫；连续两次无副作用、状态不叠加；`start()` 任一步失败先 `MailClients.clear()` 再抛，再次以合法配置 `start()` 可正常工作（AC-05）。

### 7.3 发送链路（R-01 / R-02 / R-03）

```text
MailKit.use(name).send(message)
  或 @Inject MailClient （默认通道）
  → SmtpMailSender.send(message)
      → 参数校验（message 非空→场景 1；htmlContent 与 htmlTemplate 同时非空→场景 12）
      → MailClients.isStarted() 校验（场景 5）
      → MappedMessage mapped = SmtpMappers.toSms4jMessage(message)  // 本模块 MailMessage → sms4j MailMessage
          · 附件 byte[] → 临时文件 → Map<显示名, 路径>（写入失败→场景 14）
          · htmlTemplate → TCCL 读 classpath template/<name>（读不到→场景 15）；htmlContent 直接用；templateVariables → htmlValues
      → try:
            delegate.send(mapped.getMessage())   // sms4j 内核（同步阻塞）
              · sms4j 内部：MailService.messageBuild → javax.mail.Message 构造 → Transport.send
              · 对外只抛 sms4j MailException（RuntimeException，§2.4 #6），受检异常已在内部包装
            → 成功：MailResult(channelName, true, "sent")
         catch sms4j MailException / RuntimeException e:
            → 按 cause 链分类（§7.5 判据）：网络类→MailErrors.networkError（场景 11）；否则→MailErrors.smtpError（场景 10）
            → throw MailException（保留 cause，消息不含第三方类型名）
         finally:
            SmtpMappers.cleanupTempFiles(mapped.getTempFiles())  // 按本次映射列表清理，成功/失败都执行（P1 #6）
```

- **换 SMTP 只改配置**（AC-03）：业务代码只认 `MailClient.send`；切换 `email.<name>.*` 配置后，`SmtpClientFactory` 构建对应 SMTP 客户端，发送代码零改动。
- **多通道独立实例**（AC-02）：每个 `email.<name>` 独立构建 sms4j `MailClient`（独立 `MailSmtpConfig`），`MailKit.use(name)` 取各自 `SmtpMailSender`；默认通道由 `email.default-channel` 切换。
- **并发**：`MailClients` 用 `ConcurrentHashMap`；`SmtpMailSender` 无可变字段；底层 sms4j `MailClient` 线程安全；模块不额外加锁。

### 7.4 依赖缺失探测（R-04 / AC-06）

- **唯一探测点**：`internal.smtp.MailDeps.requireSms4j()`。
- **方式**：`Class.forName("org.dromara.email.core.factory.MailFactory", false, loader)`（第二参 `false` = 不初始化类）；捕获 `ClassNotFoundException` / `LinkageError`（含 `NoClassDefFoundError`）。
- **时机**：`EmailPlugin.start()`（D8，启动即失败）。
- **缺失行为**：抛 `MailException("email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required, add it to your dependencies")`（场景 8），**不裸抛** `NoClassDefFoundError`。
- **门禁**：`classForNameOnlyInDeps` 断言全模块仅此一处 `Class.forName`。
- **ServiceLoader 安全性**：`SmtpMailProvider` 类签名无 sms4j 类型（sms4j 引用仅在方法体），故 `ServiceLoader.load(MailProvider.class)` 可在 sms4j 缺失时正常加载 `SmtpMailProvider`（不会触发 `ServiceConfigurationError`）；`MailDeps.requireSms4j()` 在 `createClient` 调用前执行，确保 sms4j 缺失时启动即失败，不会延迟到 `createClient` 触发 `NoClassDefFoundError`。

### 7.5 异常语义表（全部失败路径，无遗漏）

消息**逐字**由 `MailErrors` 构造；`{...}` 为占位拼接值。分类：**参**=参数错误 / **依赖**=依赖缺失 / **装配**=装配期 / **SMTP**=SMTP 返回错误 / **网络**=网络异常 / **状态**=插件状态错误 / **IO**=附件或模板文件 IO 失败。

| # | 类 | 场景 | 行为 | 消息（逐字，唯一措辞来源） |
| --- | --- | --- | --- | --- |
| 1 | 参 | `send` message 为 null | 拒绝 | `email: message must not be null` |
| 2 | 参 | `send` message 收件人为空（`to` 为空列表） | 拒绝 | `email: recipient list must not be empty` |
| 3 | 参 | `send` message 主题为空白 | 拒绝 | `email: subject must not be blank` |
| 4 | 装配 | 未声明任何通道 / 必填项缺失 | 启动失败 | `email: config {fullKey} is required but was: {rawValue}`（空值显示 `<empty>`） |
| 5 | 状态 | 插件未启动 / 已停止时调用门面 | 拒绝 | `email: plugin not started or stopped, call EmailPlugin.start() first` |
| 6 | 状态 | `MailKit.use(name)` 通道不存在 | 拒绝 | `email: channel not found: {name}` |
| 7 | 装配 | `email.default-channel` 指向不存在通道 / 多通道缺失 | 启动失败 | `email: default-channel {rawValue} is invalid or missing` |
| 8 | 依赖 | sms4j 缺失（探测失败） | 包装可读异常 | `email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required, add it to your dependencies` |
| 9 | 装配 | int 配置项格式非法（retry-interval / max-retries 非数字） | 启动失败 | `email: config {fullKey} must be an integer but was: {rawValue}` |
| 10 | SMTP | SMTP 返回错误（认证失败 / 收件人拒绝等） | 包装 | `email: smtp error on channel {name}, cause: {causeMsg}`（cause=原异常） |
| 11 | 网络 | 发送底层网络异常 | 包装 | `email: network error on channel {name}, cause: {causeMsg}`（cause=原异常） |
| 12 | 参 | `htmlContent` 与 `htmlTemplate` 同时非空（互斥校验，P1 #8） | 拒绝（发送期，参数校验） | `email: htmlContent and htmlTemplate are mutually exclusive` |
| 13 | 装配 | `ServiceLoader` 找不到 `type()=="smtp"` 的 Provider（资源文件缺失 / 无实现），`getProvider` 返回 null | 启动失败（明确判空，避免 NPE） | `email: no MailProvider found for type smtp, check META-INF/services/cn.hg.aifei.email.spi.MailProvider` |
| 14 | IO | 附件临时文件写入失败（`SmtpMappers` 写 `byte[]` 抛 `IOException`） | 包装（发送期） | `email: failed to write attachment {name} to temp file, cause: {causeMsg}`（cause=原异常） |
| 15 | IO | `htmlTemplate` 指定模板经 TCCL 加载不到（classpath `template/<name>` 不存在） | 拒绝（发送期） | `email: html template not found: {name} on classpath template/` |
| 16 | 装配 | 装配期 `MailBuild.build(config)` 抛 `MessagingException`（SMTP 配置无法构建客户端）——属 **start 期构建失败**，非发送期场景 10/11 | 启动失败（包装 sms4j MailException 的 cause） | `email: failed to build smtp client for channel {name}, cause: {causeMsg}`（cause=原异常） |

- **不降级**：任何失败都抛 `MailException`，不返回半截数据、不静默吞错、不吞 cause。
- **cause 保留**：场景 10/11/14/16 用 `new MailException(msg, cause)`，其余 `new MailException(msg)`。cause 均为被包装原因；消息正文只取 `cause.getMessage()`，不含第三方类型名。
- **消息不含第三方类型名**：sms4j 异常的类名（如 `org.dromara.email.comm.errors.MailException`）不得进入消息，只取错误描述。
- **场景 10 vs 11 分类判据（逐字可断言）**：`delegate.send` 对外只抛 sms4j `MailException`（RuntimeException，§2.4 #6），其 cause 链为 `javax.mail.MessagingException` / `java.io.IOException`。适配层沿 cause 链判定：**若 cause 链中任一层为 `java.net.SocketException` / `java.net.ConnectException` / `java.net.SocketTimeoutException` / `java.io.IOException`（含 `javax.mail.MessagingException` 的 `cause` 是上述网络类）→ 网络错误（场景 11）**；**否则（认证失败 / 收件人拒绝，cause 为 `javax.mail.AuthenticationFailedException` / `javax.mail.SendFailedException` 且无网络类根因）→ SMTP 错误（场景 10）**。判据只依赖 cause 类及其嵌套 cause，不依赖消息文本，保证测试逐字可断言；无法归类时默认按 SMTP 错误（场景 10）。
- **场景 16 vs 10/11 时相区分**：16 发生在 `EmailPlugin.start()` 装配期（`SmtpClientFactory.create` → 默认工厂 `MailBuild.build`），10/11 发生在发送期（`SmtpMailSender.send` → `delegate.send`）；二者消息措辞不同，测试按时相分别断言（§7.2 / §7.6）。
- **枚举值**：以上措辞为唯一定义点，测试按表逐字断言；门禁 `errorMessagesOnlyInErrors` 锁定 `email: ` 前缀仅出现在 `internal/MailErrors.java`（公共类型与测试断言除外）。场景 13 消息中的 `META-INF/services/cn.hg.aifei.email.spi.MailProvider` 为资源路径字面量、非配置键，不触发门禁 ⑬（门禁 ⑬ 只查 `email.default-channel` 与配置前缀解析）。

### 7.6 mock 可注入点（D11 / AC-01 / 场景 1）

```java
// internal.smtp.SmtpClientFactory —— 默认装配函数：MailBuild.build 抛 MessagingException，需包装
private static final Function<org.dromara.email.comm.config.MailSmtpConfig,
                               org.dromara.email.api.MailClient> DEFAULT_FACTORY =
        new Function<org.dromara.email.comm.config.MailSmtpConfig,
                     org.dromara.email.api.MailClient>() {
            public org.dromara.email.api.MailClient apply(
                    org.dromara.email.comm.config.MailSmtpConfig config) {
                try {
                    return org.dromara.email.core.service.MailBuild.build(config);
                } catch (javax.mail.MessagingException e) {
                    throw new org.dromara.email.comm.errors.MailException(e);
                }
            }
        };

private static Function<org.dromara.email.comm.config.MailSmtpConfig,
                        org.dromara.email.api.MailClient> clientFactory = DEFAULT_FACTORY;

// 包级可见（同包测试 / demo 经反射或同包辅助类调用）；factory == null 时恢复 DEFAULT_FACTORY：
static void setClientFactory(Function<org.dromara.email.comm.config.MailSmtpConfig,
                             org.dromara.email.api.MailClient> factory) { ... }
```

- **注入对象**是 sms4j 的 `MailClient` 接口（测试自行实现 mock，void send 不抛异常即模拟成功），**不涉及真实邮件发送**。
- 注入点位于 `internal.smtp`，**不进入公共面**（门禁不计数）；注入时机在 `EmailPlugin.start()` 之前。
- demo 通过「同包桥接辅助类」或反射设置该注入点，实现零外网 mock 发送（§12）。

---

## 8. 附件转换与 HTML 模板渲染（内部契约）

### 8.1 附件转换（D14）

sms4j 邮件插件原生附件格式为 `Map<显示名, 文件路径>`（`MailMessage.getFiles() → Map<String,String>`，§2.4 实证）。本模块消费方以 `byte[]` 传入附件（`MailMessage.getAttachments() → Map<String, byte[]>`），适配层 `SmtpMappers.toSms4jMessage()` 负责：

1. `toSms4jMessage(MailMessage)` **每次调用新建 `MappedMessage`**（含 sms4j `MailMessage` + 本次产生的 `List<Path>`），全程**不使用静态字段 / ThreadLocal 跟踪临时文件**（并发正确性原则，P1 #6）。
2. 遍历 `MailMessage.getAttachments()`，每个 `byte[]` 写入 `java.nio.file.Files.createTempFile()` 创建的临时文件，路径加入 `MappedMessage.tempFiles`；写入抛 `IOException` → 场景 14（保留 cause，且在finally 清理已写入的部分）。
3. 构建 `Map<String, String>`（显示名 → 临时文件路径），设置到 sms4j `MailMessage.Builder().files(map)`。
4. `SmtpMailSender.send` 持有该 `MappedMessage`，在 **`finally` 中调 `SmtpMappers.cleanupTempFiles(mapped.getTempFiles())`** 按列表删除——成功 / 失败都执行，且只清理本次调用的临时文件，杜绝并发串扰与泄漏（P1 #6）。
5. 临时文件目录使用 `java.io.tmpdir`（系统临时目录），适配云函数 / 容器环境。

### 8.2 HTML 模板渲染映射（D10）

sms4j 邮件插件支持 HTML 模板邮件（`#{变量名}` 替换，§2.4 实证）。本模块 `MailMessage` 的 HTML 相关字段映射：

| 本模块 `MailMessage` 字段 | sms4j `MailMessage` 映射 | 说明 |
| --- | --- | --- |
| `htmlContent`（非空） | `htmlContent(String)` | 直接设置 HTML 内容字符串，邮件为 HTML 格式；与 `htmlTemplate` 互斥（同时非空 → 场景 12） |
| `htmlTemplate`（非空） | **`html(InputStream)`** | **不写死 `html(String)`**：由适配层用 TCCL 读取 classpath 资源后以流传入（下方写死），`templateVariables` 由 sms4j 对该流内容做 `#{变量名}` 替换 |
| `templateVariables` | `htmlValues(Map<String,String>)` | 模板变量 `#{变量名}` → 值，由 sms4j 内部替换；**仅对 `htmlTemplate` 生效**，`htmlContent` 不做事后替换 |
| `body`（仅 body 非空，HTML 字段为空） | `body(String)` | 纯文本邮件 |

- **模板加载 classloader 策略（写死，P1 #7）**：适配层对 `htmlTemplate=<name>` 执行
  `InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("template/" + name)`，
  再将 `in` 传入 sms4j `MailsBuilder.html(InputStream)`；`in == null` → 场景 15（`email: html template not found: {name} on classpath template/`）。
  **理由（实证 §2.4 #12）**：sms4j `html(String)` 内部走 `MailService.class.getResourceAsStream("/template/"+path)`，用的是 **sms4j 自身 classloader**，库模块无法可靠读到消费方 classpath 的 `template/`；而 `html(InputStream)` 在 `MailService.send` 中被优先处理（javap：先读 `getHtmlInputStream()`），且 TCCL 是消费方应用上下文加载器，能正确解析消费方资源。故**契约锁定 `html(InputStream)` + TCCL**，禁止实现改用 `html(String)`。
- **模板文件位置**：消费方 classpath 的 `template/<name>`（如 `src/main/resources/template/notice.html`），由消费方 / demo 提供（demo 亦经 TCCL 可读）。
- **库模块不引模板引擎**（D10 / D15）：库模块只用 sms4j 内置 `#{变量名}` 替换；复杂模板由消费方在调用前自行渲染 HTML 字符串后以 `htmlContent` 传入——消费方可用 `aifei-all`（provided，已内含 aifei-enjoy，§2.6 实证）渲染，但这是消费方职责，不进入本模块公共面或依赖。

---

## 9. 架构门禁设计（`ArchitectureBoundaryTest`）

- **位置 / 机制**：`src/test/java/cn/hg/aifei/email/ArchitectureBoundaryTest.java`；`Files.walk(Paths.get("src/main/java"))` 收集全部 `.java`，按**源码文本**断言（路径分隔符归一化为 `/`，注释剥离后匹配字面量）。
- **执行位置**：必须在 `aifei-email/` 根目录（依赖相对路径 `src/main/java`），不得放宽断言。
- **关键常量**：

```java
private static final Path   SOURCE_ROOT = Paths.get("src/main/java");
private static final String ROOT_PACKAGE     = "cn/hg/aifei/email/";
private static final String SPI_PACKAGE      = "cn/hg/aifei/email/spi/";
private static final String BRIDGE_PACKAGE   = "cn/hg/aifei/email/aifei/";
private static final String ADAPTER_PACKAGE  = "cn/hg/aifei/email/internal/";
private static final String SMTP_PACKAGE     = "cn/hg/aifei/email/internal/smtp/";
private static final int    EXPECTED_ROOT_TYPES = 5;
private static final int    EXPECTED_SPI_TYPES = 1;
private static final Set<String> EXPECTED_ROOT_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
        "MailClient.java", "MailKit.java", "MailMessage.java", "MailResult.java", "MailException.java"));
private static final Set<String> EXPECTED_SPI_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
        "MailProvider.java"));
```

### 9.1 断言清单（14 例，方法名可直接照写为测试方法）

| # | 测试方法 | 判定口径 | 风险针对 |
| --- | --- | --- | --- |
| ① | `sms4jEmailOnlyInAdapter` | 每条 `import org.dromara.email.` 只允许出现在 `SMTP_PACKAGE` 下文件；出现在根包 / spi / aifei / internal 其它子包即失败 | 第三方实现类型泄漏（D3） |
| ② | `javaxMailOnlyInAdapter` | 每条 `import javax.mail.` / `import com.sun.mail.` 只允许出现在 `SMTP_PACKAGE` 下文件 | JavaMail 类型泄漏进公共面（D3） |
| ③ | `frameworkOnlyInBridgePackage` | `import cn.aifei.` 只允许出现在 `BRIDGE_PACKAGE` 下文件 | D12「零框架引用」被绕过 |
| ④ | `noSqlOrUndertowInLibrary` | 全模块 `.java` 不得 `import io.undertow.` / `import java.sql.`；pom 不得有 JDBC / `aifei-db` | 库模块零 Web / 零 DB（§0） |
| ⑤ | `noSlf4jBinding` | 全模块 `.java` 不得 `import org.slf4j.`；pom 不声明 `slf4j-simple` / `logback` / `log4j`（人工核对） | 日志绑定被悄悄引入（D12） |
| ⑥ | `publicSurfaceExact` | 根包直接子 `.java` 数量精确 = 5 且集合等于 `EXPECTED_ROOT_TYPES_SET`；`spi` 包精确 = 1 且集合等于 `EXPECTED_SPI_TYPES_SET` | 同名替换 / 增删公共类型（D5 / Q7 / D13） |
| ⑦ | `publicSignaturesNoThirdParty` | 根包 + `spi` 包源码中不得出现 `org.dromara.email.` / `javax.mail.` / `com.sun.mail.` / `cn.aifei.` 字面量（import 与全限定名均查） | 公共签名零第三方 / 框架类型（D3） |
| ⑧ | `classForNameOnlyInDeps` | `Class.forName` 只允许出现在 `internal/smtp/MailDeps.java` | 依赖探测散落（§7.4） |
| ⑨ | `errorMessagesOnlyInErrors` | 消息前缀 `email: ` 只允许出现在 `internal/MailErrors.java`（公共类型与测试断言除外） | 异常消息措辞唯一定义点（§7.5） |
| ⑩ | `java8Compatibility` | 不得出现 `List.of(` / `Map.of(` / `Set.of(` / `\brecord\s` / **`\bvar\s`** 局部声明（正则需词边界 `\bvar\s`，避免误伤 `variable` / `varName` / `invariant`）/ `"""` / `\bsealed\s` / `instanceof` 模式匹配 | Java 8 兼容底线 |
| ⑪ | `noStarterDependency` | pom 不得出现 `sms4j-spring-boot-starter` / `sms4j-solon-plugin` / `org.springframework` / `org.noear` | 禁 starter / solon（D2） |
| ⑫ | `pluginBridgeContract` | `aifei` 包必须存在 `EmailPlugin.java` 与 `EmailConfigLoader.java`（防门禁假通过）；`EmailPlugin` 须 `import cn.aifei.` | 接入构件存在性 + 边界 |
| ⑬ | `configKeysOnlyInLoader` | 字面量 `email.default-channel` 与 `email.` 配置前缀解析只允许出现在 `aifei/EmailConfigLoader.java`。**按源码「字符串字面量」判定，不按运行时字符串**：`MailErrors` 中场景 4/9 的 `email: config {fullKey} ...` 是消息模板、`{fullKey}` 运行时值可能含 `email.` 前缀但非字面量，不误伤；场景 13 的资源路径字面量同理 | 配置键名唯一定义点（§6.2） |
| ⑭ | `testSourcesOfflineOnly` | 扫描 `src/test/java`：`http://` / `https://` 字面量 host 只能是 `127.0.0.1` / `localhost`（注释先剥离） | 测试联网 |

> **实现要点**：私有工具 `javaFiles(Path)` / `imports(String)` / `stripComments(String)` / `normalize(Path)` / `isDirectChild(String, String)`；异常消息须列出**违规文件 + 违规内容**。
> 门禁**不得为兼容错误用法而放宽或绕过**；新增例外必须先改本文 §3 再改断言，并提请主会话登记（根 `AGENTS.md`）。
> 门禁外的构建期断言：主 JAR 全部 class major = 52（`javap -verbose` 抽查 `MailClient.class` / `MailKit.class` / `EmailPlugin.class` / `MailDeps.class`）；`mvn dependency:tree` 只出现 §2.3 允许清单。

---

## 10. 依赖与构建

### 10.1 依赖表

| 依赖 | 范围 | 用途 |
| --- | --- | --- |
| `cn.aifei:aifei:1.1.2` | **`provided`** | 生态底座（`Plugin` / `Prop` / `AopKit`）；本模块源码仅 `aifei` 包引用；消费方自带、不传递 |
| `org.dromara.sms4j:sms4j-Email-core:3.3.6` | **`optional`** | 唯一实现内核（§2），经 `internal.smtp` 引用；不传递，缺失时启动期可读失败（场景 8） |
| `junit:junit:4.13.2` | `test` | 单元测试 + 架构门禁 |
| （测试辅助）mock sms4j `MailClient` | test 源码 | 测试自行实现 sms4j `MailClient` 接口（`send` 不抛异常即模拟成功），不真实发邮件、不提交二进制 |

- **零日志绑定**：不声明 `slf4j-simple` / `logback` / `log4j`（sms4j 传递的 `slf4j-api` 仅 API，无绑定；消费方自选）。
- **零 DB / 零 Web 容器**：无 JDBC / 连接池；不引入 `io.undertow.*`（库模块本体）。
- pom 对齐 `aifei-http/pom.xml` / `aifei-excel/pom.xml` / `aifei-sms/pom.xml` 模板：`maven-compiler-plugin`（`source`/`target` 1.8、UTF-8）+ `maven-surefire-plugin`（`workingDirectory = ${project.basedir}`，门禁依赖相对路径）+ forceCreation source plugin + javadoc plugin（`excludePackageNames` 排除 `internal`）。**不引入** Central 发布元数据与 profile（§13 事项 3）。

### 10.2 `dependency:tree` 允许清单

以 §2.3 实测树为准。断言口径重申：① 除 `aifei:provided` / `sms4j-Email-core:optional` 及其传递项 / `junit:test` 外，出现任何条目即依赖失控；② sms4j 全传递项均 `optional`；③ 无日志绑定实现；④ 无 Spring / JDBC / 连接池；⑤ 无 starter / solon；⑥ 允许清单额外登记一项**受限传递依赖**——`hamcrest-core`（scope `test`，由 `junit` 传递），非本模块 pom 直接声明、不进消费方运行 classpath，**不违背**「零强制第三方运行依赖 / 无日志绑定 / sms4j optional」纪律：`hamcrest-core` 仅测试期、不属 sms4j 传递链且非任何日志绑定实现；精确坐标与版本以 `mvn dependency:tree` 实测为准。

### 10.3 验证命令（模块根目录）

```bash
cd aifei-email
mvn clean verify                                                        # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest                                # 仅跑架构门禁（14 例）
mvn dependency:tree                                                     # 断言：仅 §2.3 允许清单
javap -verbose target/classes/cn/hg/aifei/email/MailClient.class | findstr "major version"   # 期望 52
```

---

## 11. 文件清单与有序任务分解

### 11.1 模块源码文件清单

```text
aifei-email/                                  io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT（packaging=jar）
├── pom.xml                                   # Java 8；aifei provided；sms4j-Email-core optional；junit test；零日志绑定/零 starter
├── README.md                                 # 用法 / email.* 配置 / 同步发送声明 / sms4j optional 警告 / javax.mail 许可证声明（T08 写）
├── AGENTS.md                                 # 模块硬约束（T08 写，素材 §9 + §3 决策表）
└── src/main/resources/                       # 非 Java 资源（T04 落地，ServiceLoader 发现 SMTP provider）
    └── META-INF/
        └── services/
            └── cn.hg.aifei.email.spi.MailProvider  # 内容为 cn.hg.aifei.email.internal.smtp.SmtpMailProvider 全限定名（D13 / §7.1）；缺此文件则 getProvider("smtp") 返回 null → 场景 13 启动失败
├── docs/
│   ├── design.md                             # ★ 本文（契约事实来源，本轮唯一落盘）
│   └── testing.md                            # 测试策略与用例清单（T08 写，素材 §7.5 + §12）
└── src/main/java/cn/hg/aifei/email/
    ├── MailClient.java                       # 发送门面接口（根包 5 之一）
    ├── MailKit.java                          # 静态门面（根包 5 之一）
    ├── MailMessage.java                      # 发送消息值对象 + Builder（根包 5 之一）
    ├── MailResult.java                       # 发送结果值对象（根包 5 之一）
    ├── MailException.java                    # 统一异常（根包 5 之一）
    ├── spi/
    │   └── MailProvider.java                 # 通道扩展 SPI 接口（spi 1 类型之一）
    ├── aifei/
    │   ├── EmailPlugin.java                  # ★ 装配入口（implements Plugin）
    │   └── EmailConfigLoader.java            # ★ email.* 配置解析唯一定义点
    ├── internal/
    │   ├── MailChannelConfig.java            # 单通道配置值对象（非承诺实现层，非计数）
    │   ├── MailErrors.java                   # ★ 异常唯一构造点 + 消息措辞（门禁 ⑨）
    │   ├── MailClients.java                  # ★ 通道注册表（register/default/use/channels/isStarted/setStarted/clear）
    │   ├── MailProviderRegistry.java         # ★ Provider 发现（ServiceLoader，getProvider/types）
    │   └── DefaultMailClientProxy.java       # ★ @Inject 单例的动态委托代理（send/channelName 每次现取默认通道，P1 #10）
    └── internal/smtp/
        ├── MailDeps.java                     # ★ 唯一 Class.forName 点（门禁 ⑧）
        ├── SmtpMailProvider.java             # MailProvider 实现（type="smtp"，createClient 委托 SmtpClientFactory）
        ├── SmtpClientFactory.java            # ★ 客户端构建装配点（MailSmtpConfig.builder→MailBuild.build→SmtpMailSender）+ mock 注入点（D11）
        ├── SmtpMailSender.java               # MailClient 实现（发送 + 异常翻译）
        └── SmtpMappers.java                  # MailMessage→sms4j MailMessage / 附件 byte[]→临时文件转换 / 清理
```

### 11.2 测试文件清单（`src/test/java/cn/hg/aifei/email/`）

```text
├── ArchitectureBoundaryTest.java             # 门禁 14 例（§9）
├── SendContractTest.java                     # 发送 / 换 SMTP / 多通道 / 结果映射（R-01/R-02）
├── PluginLifecycleTest.java                  # start/stop 幂等 / 失败无残留 / stop 后语义（R-03）
├── ConfigValidationTest.java                 # 必填项 / int 格式 / 默认通道 / 消息含键名原值（R-03）
├── DependencyMissingTest.java                # sms4j 缺失可读异常，无裸 NoClassDefFoundError（R-04）
├── ExceptionContractTest.java                # 参数/SMTP/网络异常逐字消息 + cause（R-05）
├── PublicApiContractTest.java                # 公共面精确 5+1 + 零第三方/框架 + SPI 形态
└── support/
    └── MockSms4jMailClient.java              # 测试用 sms4j MailClient mock（send 不抛异常即模拟成功）
```

### 11.3 有序任务分解（T01…T10，逐步依赖）

> owner 均为 `aifei-core-engineer`；每任务完成后**不得**改动公共面签名，若发现签名不可行须**先回写本文 §5** 再改代码（根 `AGENTS.md`：需求与设计冲突时先更新设计决策）。T01 为项目基础设施（构建文件 + 配置骨架 + 公共架构件）。

| 任务 | 内容（文件） | 依赖 | 优先级 | 验收 |
| --- | --- | --- | --- | --- |
| **T01 基础设施** | `pom.xml` + 目录骨架 + 根包 5 公共类型**签名骨架**（方法体抛 `UnsupportedOperationException`）+ `spi/MailProvider` 签名 + `ArchitectureBoundaryTest` 常量与 ⑥ 公共面计数断言先落地 | — | P0 | `mvn -q -DskipTests compile` 通过；5 签名与 §5 逐字一致；class major 52；`dependency:tree` 仅 §2.3 清单；门禁 ⑥ 绿 |
| **T02 异常与依赖探测** | `internal/MailErrors.java` + `internal/smtp/MailDeps.java` | T01 | P0 | §7.5 消息逐字；场景 8 可读失败（模拟缺依赖不出现裸 `NoClassDefFoundError`）；门禁 ⑧⑨ |
| **T03 配置与 Provider 注册** | `aifei/EmailConfigLoader.java` + `internal/MailChannelConfig.java` + `internal/MailProviderRegistry.java` | T02 | P0 | §6 全部规则（通道发现 / 必填 / int 校验 / 默认通道 / 键名→builder）；`ConfigValidationTest` 绿；门禁 ⑬ |
| **T04 SMTP 适配层** | `internal/smtp/SmtpMailProvider.java` + `SmtpClientFactory.java` + `SmtpMailSender.java` + `SmtpMappers.java` + `MappedMessage.java` + `internal/MailClients.java` + `internal/DefaultMailClientProxy.java` + **`src/main/resources/META-INF/services/cn.hg.aifei.email.spi.MailProvider`** | T03 | P0 | §2.4 编程式装配链路落地；mock 注入点（D11）；附件转换 + HTML 模板映射（TCCL）；**services 资源文件内容为 `SmtpMailProvider` 全限定名，`MailProviderRegistry.getProvider("smtp")` 非 null（场景 13 不误触发）**；`MappedMessage` + finally 清理（P1 #6）；动态委托代理（P1 #10）；`SendContractTest` 绿；门禁 ①②⑦ |
| **T05 接入层装配** | `aifei/EmailPlugin.java` | T04 | P0 | §7.2 start/stop 幂等、失败回滚、`@Inject` 单例、stop 后语义、依赖探测（D8）；`PluginLifecycleTest` 绿；门禁 ③⑫ |
| **T06 公共门面实现 + 门禁** | `MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException` 方法体 + 补齐门禁 ①–⑭ | T04、T05 | P0 | §5 契约全部落地；门禁 14 例全绿；`mvn clean verify` 0/0 |
| **T07 核心单测** | `SendContractTest` / `PluginLifecycleTest` / `ConfigValidationTest` / `DependencyMissingTest` / `ExceptionContractTest` / `PublicApiContractTest` + `support/**` | T06 | P0 | AC-01~AC-08 逐条覆盖；§7.5 场景 1–16 全覆盖（含场景 10/11 分类判据、场景 12–16）；stop→start 注入单例刷新（P1 #10）；全部 mock 零外网 |
| **T08 文档四件套（本模块）** | `README.md` / `AGENTS.md` / `docs/testing.md` + 根导航同步（根 `README.md` 模块表加 `aifei-email` + demo 条目 + 依赖口径）+ `LICENSE` / `NOTICE`（javax.mail 许可证声明）+ 根 `AGENTS.md` 登记 | T07 | P1 | README 含 §1.4 本期不做、§2.3 optional 警告、同步发送声明、javax.mail CDDL+GPL+CPE 许可证声明；AGENTS 对齐 §9 门禁 14 例；testing 用例与 AC 一一对应 |
| **T09 demo 后端** | `aifei-email-demo/pom.xml` + `DemoApp` / `DemoAppConfig` + `demo/arch/**`（自写 HIO 复制改包名）+ `demo/console/**` + `demo/service/EmailDemoService` + mock 注入桥接 | T06 | P1 | 端口 18190 启动；§12.2 六场景端点按契约返回；零外网（mock 发送） |
| **T10 demo 前端与脚本** | `aifei-email-demo/webapp/view/email/index.html` + `assets/{js,css,lib}` + `start-demo.bat` / `stop-demo.bat` + demo `README.md` | T09 | P1 | §12.3 六场景可见；页面零外网请求；真跑一遍通过；demo `dependency:tree` 验证 optional 口径 |

### 11.4 跨文件约定（工程师与 QA 共同遵守）

| 约定 | 内容 |
| --- | --- |
| **命名** | 根包公共类型逐字按 §5；`internal` / `aifei` 类型按 §7.1 表名；测试用 mock 用业务视角命名，禁 `foo`/`bar`/`test` 无义名；文件头版权块按根 `AGENTS.md` 统一声明 |
| **公共方法 javadoc** | 简体中文；根包 5 类型 + `spi` 每个公共方法写全 `@param` / `@return` / `@throws MailException`；`MailKit` 明确「须先 `EmailPlugin.start()`」 |
| **异常消息** | 唯一定义点 `MailErrors`，逐字对齐 §7.5；`email: ` 前缀；禁止在其它实现类自拼（门禁 ⑨） |
| **依赖探测** | 唯一 `Class.forName` 点 `MailDeps`（门禁 ⑧）；不反射实例化、不 `ServiceLoader`（`MailProviderRegistry` 的 `ServiceLoader` 是 Provider 发现，非依赖探测） |
| **配置键解析** | 唯一 `aifei/EmailConfigLoader`（门禁 ⑬）；kebab→camel 转换只此一处 |
| **附件转换** | 唯一 `SmtpMappers.toSms4jMessage(MailMessage) → MappedMessage`（含临时文件列表）；`SmtpMailSender.send` 在 finally 调 `SmtpMappers.cleanupTempFiles(mapped.getTempFiles())` 按列表清理；**禁用静态字段 / ThreadLocal 跟踪临时文件**（P1 #6） |
| **线程安全** | `MailClients` 用 `ConcurrentHashMap`；`SmtpMailSender` / `DefaultMailClientProxy` 无可变字段；`EmailPlugin.start/stop` synchronized；模块不自建线程池 |
| **测试注册表隔离** | `MailClients` 为进程内静态全局；每个测试类 `@After` 必须调 `MailClients.clear()`（含 started 复位），避免测试类间状态串扰；`PluginLifecycleTest` 额外覆盖 stop→start 注入单例刷新（P1 #10） |
| **行尾与编码** | `.md` / `.java` 一律 CRLF、UTF-8（根 `AGENTS.md`，`core.autocrlf=true`）；批量改写按字节读写、整文件读入断言后单次写回 |
| **测试离线** | 全部单测零外网；mock sms4j `MailClient` 的 `send` 不抛异常即模拟成功，不真实发邮件；门禁 ⑭ 扫测试源码 host |
| **git 纪律** | Conventional Commit；不改冻结契约；`git status` 无 `AD` 残留；当日工作日志追加仓库根协作记忆目录 |

---

## 12. 测试策略与 demo 端点清单

### 12.1 测试策略

| 层 | 覆盖 | 判据 |
| --- | --- | --- |
| **架构门禁** | `ArchitectureBoundaryTest` 14 例（§9） | 全绿；不得放宽 |
| **字节码** | 主 JAR 全部 class major = 52（`javap` 抽查 `MailClient` / `MailKit` / `EmailPlugin` / `MailDeps`） | 恒 52 |
| **发送契约** | `SendContractTest`：发送返回 `MailResult` / 换 SMTP 业务代码零改动 / 多通道独立实例 / **HTML 模板：断言 mock 收到的 sms4j `MailMessage.getHtmlValues()` 与模板路径正确（不断言渲染后字符串，P1 #11）** / 附件转换 + finally 清理临时文件 / 结果字段映射 | §5.1 / §7.3 / AC-01~AC-03 |
| **装配生命周期** | `PluginLifecycleTest`：start 后可用 / stop 后抛场景 5 / start×2、stop×2 幂等 / 失败后合法重装配 / **stop→start 配置变更后注入单例（`DefaultMailClientProxy`）委托到新 `SmtpMailSender`（P1 #10）** | §7.2 / AC-04~AC-05 |
| **配置校验** | `ConfigValidationTest`：无通道 / 必填缺失（消息含键名原值）/ int 格式非法 / 默认通道非法 / **Provider 缺失（场景 13）** | §6.2 / §7.5 / AC-05 |
| **依赖缺失** | `DependencyMissingTest`：隔离 ClassLoader 下 start 抛场景 8 可读异常，无裸 `NoClassDefFoundError` | §7.4 / AC-06 |
| **异常契约** | `ExceptionContractTest`：参数 / SMTP / 网络 / **互斥（12）/ 附件 IO（14）/ 模板不存在（15）/ 装配期 build 失败（16）** 逐字消息 + cause 保留 + 消息不含第三方类型名 + **场景 10/11 分类判据** | §7.5 / AC-07 |
| **公共面契约** | `PublicApiContractTest`：根包精确 5 / spi 精确 1（`MailProvider`）/ 公共签名零第三方 / `MailKit` 形态 / `MailClient` 接口 | §5 / AC-08~AC-09 |
| **依赖树** | `mvn dependency:tree` 仅 §2.3 清单；无 Spring / 日志绑定 / starter | §10.2 / AC-13 |

- **离线**：全部单测零外网；mock sms4j `MailClient`（门禁 ⑭）。
- **判据**：`aifei-email/` 内 `mvn clean verify` **0 failure / 0 error**。

### 12.2 demo 端点清单（`aifei-email-demo`，不发布）

- **位置**：仓库根 `aifei-email-demo/`（与 `aifei-email/` 平级）；依赖 `io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT` + `org.dromara.sms4j:sms4j-Email-core:3.3.6`（demo 以 `compile` 自带，验证 optional 口径）+ `cn.aifei:aifei`（demo 以 `compile` 自带，验证 provided 口径）。
- **端口**：**18190**（已定档；18100 mq / 18110 job / 18120 http / 18130 trace / 18140 id / 18150 excel / 18160 audit / 18170 i18n / 18180 sms 已占用）。
- **形态**：自写 HIO（包名 `cn.hg.aifei.email.demo`，跟随 `aifei-sms-demo` 的 `arch` 同步类结构：`ContentType` / `In` / `InParser` / `IoDispatcher` / `IoHandler` / `Out` / `OutCode` / `TargetFactory`）+ `console/**`（自带日志）+ `EmailDemoService` + `DemoAppConfig` + `start-demo.bat` / `stop-demo.bat`；离线可跑、零外网。
- **mock**：demo 经 §7.6 注入点设 `MockSms4jMailClient`（sms4j `MailClient` 的 mock，`send` 不抛异常即模拟成功），**不真实发邮件**。
- **错误演示隔离机制（写死，P1 #9）**：
  - `/config-error` **只调用 `EmailConfigLoader.load(prop)`（包级方法，demo 经同包桥接类或反射访问）捕获异常并取 `getMessage()`，绝不进入 `EmailPlugin.start()`**——避免 `start()` 失败路径的 `MailClients.clear()` 清空已运行的合法注册表，污染全局导致后续 `/send` 全失败。错误消息由 `MailErrors` 真实构造（场景 4）。
  - `/dependency-missing` 用 `MailDeps` 的**包级 `requireSms4j(ClassLoader)` 钩子**（§7.1）传入一个「屏蔽 sms4j 相关类」的隔离 `ClassLoader`（覆盖 `loadClass`，对 `org.dromara.email.*` 前缀抛 `ClassNotFoundException`）触发真实的依赖探测失败，取 `MailErrors` 真实构造的消息（场景 8）——不修改 `MailDeps` 唯一 `Class.forName` 门禁 ⑧，也不影响全局注册表。此钩子仅 demo / 测试使用，不进入库公共面。
- **端点契约**（信封 `code` / `data` / `msg`，HTTP 状态恒 200，语义由 `code` 承载；`/email/demo/...` 命名空间）：

| 端点 | 方法 | 请求 | `data` 内容 | 对应场景 |
| --- | --- | --- | --- | --- |
| `/email/demo/page` | GET | — | 页面（静态层 HTML） | 空态 |
| `/email/demo/send` | POST | 通道名 + 收件人 + 主题 + 正文类型（纯文本/HTML）+ **HTML 模板名（可选，与 HTML 内容互斥，区分场景 5）** + 附件 | `{ channel, success, message }` —— 经 mock `MailClient` 返回 | 场景 1 模板化发送 |
| `/email/demo/channels` | GET | — | `{ channels: [...], default: "..." }` —— 注册表内容 | 场景 2 多通道切换 |
| `/email/demo/config-error` | GET | — | `{ error: "email: config email.qq.smtp-server is required but was: <empty>" }` —— 经 `EmailConfigLoader.load` 捕获的 `MailErrors` 真实消息（**不进 start，不污染注册表**） | 场景 3 配置错误态 |
| `/email/demo/dependency-missing` | GET | — | `{ error: "email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required ..." }` —— 经隔离 ClassLoader 触发 `MailDeps.requireSms4j(loader)` 的 `MailErrors` 真实消息 | 场景 4 依赖缺失态 |
| `/email/demo/html-template` | POST | 模板名 + 变量 JSON | `{ renderedHtml, channel, success }` —— `renderedHtml` 由 **demo 侧自行做同名 `#{变量名}` 替换**生成预览（P2，见下方说明）+ 发送结果 | 场景 5 HTML 模板渲染 |
| `/email/demo/state` | GET | — | `{ sms4jAvailable: true, version: "3.3.6", channels: [...] }` | 依赖状态展示 |

- **端点真实性**：`/send` 必须真实走 `MailKit`→`SmtpMailSender`→mock sms4j `MailClient` 全链路（不得静态假数据）；`/config-error` / `/dependency-missing` 的消息由 `MailErrors` 真实构造（非硬编码字符串）。
- **`renderedHtml` 来源（P2 / P1 #11）**：sms4j 的 `#{变量名}` 替换在 `send()` 内部完成、不暴露渲染后字符串，故 `MockSms4jMailClient` 无法断言最终渲染结果。demo 的 `renderedHtml` 由 **demo 展示层自行对模板串做同名 `#{变量名}` 替换**生成（仅用于页面预览）；该预览替换**与 sms4j 行为对齐、属 demo 展示逻辑，不进入库公共面、不引模板引擎**（与 D10 / D15 不冲突）。库侧 AC-01 只断言「传给 sms4j 的 `htmlValues` 与模板路径正确」（§12.1 / §14 AC-01），不依赖渲染后字符串。

### 12.3 demo 页面展示内容（6 场景）

1. **模板化发送**：页面填收件人 + 主题 + 选正文类型（纯文本 / HTML）+ 填附件 → 点击发送 → 展示发送结果（状态 + 消息）；**不真实发邮件**，mock SMTP 响应。
2. **多通道切换**：下拉切换 SMTP 通道（不同配置池标识）→ 同一发送代码不变、结果落点不同（不同通道标识）。
3. **配置错误态**：演示必填项缺失（如 SMTP 服务器为空 / 端口非法）→ 启动失败提示（页面展示错误消息含键名与原值）。
4. **依赖缺失态**：演示「未引入 sms4j-Email-core」时首次调用抛可读 `MailException`（页面展示异常消息含依赖坐标与引入提示）。
5. **HTML 模板渲染**：演示 HTML 模板邮件：模板含 `#{变量名}` 占位符 → 填变量 → 展示渲染后的 HTML 内容预览 + 附件（字节传入）→ 发送。
6. **空态**：首次进入、未触发操作时列表显示占位文案，不残留上一次数据。

- **离线可跑**：页面零外网请求；`start-demo.bat` 启动后浏览器访问 `http://localhost:18190/email/demo/page` 可完成一轮「发送 → 切换通道 → HTML 模板渲染」闭环。
- **不经过原型与 QA 基线门禁**（任务输入 §〇 定档）。

---

## 13. 待明确事项与假设

| # | 事项 | 处理 / 假设 | 归属 |
| --- | --- | --- | --- |
| 1 | **根 `AGENTS.md` 需登记清单 / D12 口径对齐** | sms4j-Email-core 为 `optional`（非 compile 例外）；根包 / spi 零框架类型、零第三方类型；**D12 已修订为「除 `aifei` 接入包外全模块零 `cn.aifei.*` 引用，尤其不引 `cn.aifei.log.*`」**——`aifei` 包引用 `cn.aifei.*`（Plugin / Prop / AopKit）属 Plugin 接入构件、门禁 ⑫ 强制（与 `aifei-oss` / `aifei-sms` 同类，非「公共面框架类型例外」）；与 §4.1 依赖方向一致 → **需登记清单为空（compile 例外），本设计不修改根 `AGENTS.md` 的 compile 例外清单**。若评审要求改变任一前提，须先提请主会话登记 | 已定档（默认空，D12 口径已对齐） |
| 2 | **javax.mail 许可证声明落地** | CDDL 1.1 + GPL 2.0 w/ CPE，法务已确认可接受（用户已定档）；实现期 T08 落地：① 模块 `LICENSE` / `NOTICE` 声明 javax.mail 许可证；② `README.md` 注意事项声明；③ 根 `AGENTS.md` 登记 javax.mail 为 optional 传递依赖的许可证留痕 | 待 T08 落地 |
| 3 | **发布元数据（Central）** | 本轮 pom 不含 Central 发布 profile；是否加入 licenses/developers/scm + central-publishing 由主理人决定（`aifei-oss` 有、`aifei-excel` 无，仓库不统一） | 待主会话拍板（非阻塞实现） |
| 4 | **Q1 精确 artifactId** | 已实证 `org.dromara.sms4j:sms4j-Email-core:3.3.6`（§2.2），大写 E，网络可达、`dependency:get` EXIT=0；传递项 Java 8 兼容、无 Spring / 无日志绑定。定档无需最小假设兜底 | 已实证定档 |
| 5 | **Q3 装配方式** | 已实证编程式 `MailBuild.build(MailSmtpConfig)` 完备（§2.4），选用 `MailBuild.build` 而非 `MailFactory.put+createMailClient` 以避免静态注册表副作用 | 已实证定档 |
| 6 | **Q7 根包数量** | 定稿 5 类（§5.6），`MailResult` 独立；spi 定稿 1 类（`MailProvider`，D13 预留）；已回写任务输入 §五 5.4 与 AC-09 | 已定档并回写 |
| 7 | **Q2 MailProvider SPI** | 定档首期预留（D13），`spi.MailProvider` 接口 + `internal.MailProviderRegistry`（ServiceLoader）+ `internal.smtp.SmtpMailProvider`（SMTP 默认实现）；META-INF/services 发现；首期唯一实现 = SMTP | 已定档 |
| 8 | **Q4 重试关闭** | 定档 `maxRetries=0` 默认关闭（D7）；**D7 措辞已修订为「Java 公共 API 不暴露重试参数，配置层可配（默认 0）」**（与 §6.1 含 `max-retries` / `retry-interval` 配置键对齐，消除「不在公共面暴露重试参数」与配置层可配的表述冲突） | 已定档（D7 措辞已对齐） |
| 9 | **本批只交本文** | 本批不写任何 Java 源码，也不写 `README.md` / `AGENTS.md` / `docs/testing.md` / demo / 根导航（由 T08–T10 产出）；本文 §5–§12 是其素材 | 已定档 |
| 10 | **未知配置项严格模式（未来增强）** | 本期 `email.<name>.*` 未识别键静默忽略（透传 sms4j 新参数，§6.2 规则 6）；拼写错误排障由 README/testing 列合法键全集缓解。「未知键即启动失败」的严格模式登记为未来增量，本期不做（避免破坏零日志绑定纪律） | 待未来增量（非阻塞） |
| 11 | **aifei-enjoy 模板方案决策留痕** | §2.6 当场实证：内含于 aifei-all:1.1.2、Java 8（major 52）、零新增依赖；决策 D15 **不在库模块内嵌**（D12 / 门禁 ③ / D10 冲突 + 语法双轨），推荐消费方自行渲染后传 `htmlContent`；若未来要库内嵌须先改 D10/D12 + 门禁 ③ 并提请主会话登记 | 已实证定档（D15） |
| 12 | **需芬西施同步回写的需求文档点** | 本轮设计修订影响判定条件：① **AC-01**「HTML 模板变量替换生效」→ 改为「传给 sms4j 的 `htmlValues` 与模板路径正确」（mock 不可断言渲染后字符串，§14）；② 异常场景由 11 扩展到 16（§7.5），AC-07 措辞相应扩展；③ 公共面新增内部构件（`MappedMessage` / `DefaultMailClientProxy`）不计入公共面计数，无需改 AC-09。**本设计不修改需求文档**，需由需求分析师同步 | 待需求分析师回写 |

---

## 14. 验收标准（AC）映射（对应 PRD / 任务输入 AC-01~AC-16）

| # | 判定条件 | 验证通道 | 对应 P0 |
| --- | --- | --- | --- |
| AC-01 | 配置 SMTP 通道 → `MailKit.use` / `@Inject` 取实例 → 发送邮件（纯文本 + HTML + 多收件人 + 抄送密送 + 附件字节）→ 返回 `MailResult`（含发送状态）；HTML 模板邮件：**断言传给 sms4j 的 `MailMessage.getHtmlValues()` 与模板路径（TCCL classpath 资源）正确**（`#{变量名}` 最终渲染在 sms4j `send()` 内部完成、不暴露结果，mock 无法断言渲染后字符串；渲染预览放 demo，§12.2）；发送失败 → 抛 `MailException` 且消息可读 | `SendContractTest` + §7.6 mock 注入点（P1 #11 调整判定口径） | R-01 |
| AC-02 | 两通道各取独立实例分别发送；换默认通道只改 `email.default-channel` 配置、业务代码不变 | `SendContractTest` | R-02 |
| AC-03 | 换 SMTP 服务器只改 `email.*` 配置（SMTP 服务器地址 / 端口 / 账号 / 密码等），业务发送代码零改动仍能发送 | `SendContractTest`（切换配置 + mock SMTP 响应） | R-01 |
| AC-04 | start 后可用；stop 后发送抛「插件未启动或已停止」；start×2 / stop×2 幂等 | `PluginLifecycleTest` + §7.2 | R-03 |
| AC-05 | 配置非法启动即失败、消息含键名与原值；失败无残留、合法重装配可用 | `ConfigValidationTest` + `PluginLifecycleTest` | R-03 |
| AC-06 | 缺 sms4j 时首次调用抛可读 `MailException`（含坐标），无裸 `NoClassDefFoundError` | `DependencyMissingTest` + 门禁 ⑧ | R-04 |
| AC-07 | 参数错误（收件人空 / 主题空 / `htmlContent` 与 `htmlTemplate` 互斥等）→ `MailException` 且消息可读；SMTP 返回错误 → `MailException` 且消息含 SMTP 错误描述、保留 cause；网络异常 → `MailException` 且保留 cause；**附件 IO / 模板不存在 / Provider 缺失 / 装配期 build 失败均有逐字消息（场景 12–16）**；场景 10/11 按 cause 链判据分类（§7.5）；消息不含第三方类型名 | `ExceptionContractTest` + 门禁 ⑨（P0 #5 扩展场景覆盖） | R-05 |
| AC-08 | 根包公共签名零第三方类型（`org.dromara.email.*` / `javax.mail.*`）、零框架类型（`cn.aifei.*`）；`org.dromara.email.*` import 只出现在 `internal.smtp` 适配层 | 门禁 ①②⑦ | 公共面 |
| AC-09 | 根包公共类型数量精确等于 `design.md` 定稿名单（5：`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`）；spi 精确 1（`MailProvider`） | 门禁 ⑥ `publicSurfaceExact` + 反射 | 公共面 |
| AC-10 | `cn.aifei.*` 只允许出现在 `aifei` 接入包 | 门禁 ③ | 公共面 |
| AC-11 | `java.sql.*` / `io.undertow.*` 全模块禁止（库模块本体） | 门禁 ④ | 公共面 |
| AC-12 | 主 JAR 内全部 class major == **52**；源码无 `var` / `record` / `switch` 表达式 / text block / `List.of(` / `Map.of(` | 门禁 ⑩ + `javap` 抽查 | Java 8 |
| AC-13 | `mvn dependency:tree` 除 `cn.aifei:aifei`（provided）外，sms4j-Email-core 为 `optional`、无其它第三方 compile / runtime 依赖、无日志绑定实现、无 starter | 门禁 ⑤⑪ + `dependency:tree`（§2.3） | 依赖 |
| AC-14 | 模块目录内 `mvn clean verify` **0 failure / 0 error** | 构建 | 构建 |
| AC-15 | 文档四件套齐备（`README.md` / `docs/design.md` / `AGENTS.md` / `docs/testing.md`）+ 根 `README.md` 模块总览补条目 | 文件检查 | 文档 |
| AC-16 | `aifei-email-demo` 可启动（端口定档 **18190**）；发送与 HTML 模板渲染场景可见；页面与源码零外网 host（门禁） | 实测 + 门禁 ⑭（demo 侧） | demo |

> **P0 覆盖核对**：R-01~R-05 全部映射到 §14 AC-01~AC-16，无遗漏。R-01（统一发送）→ AC-01/AC-03；R-02（多通道切换）→ AC-02；R-03（装配与配置）→ AC-04/AC-05；R-04（依赖缺失探测）→ AC-06；R-05（统一异常）→ AC-07。公共面 / Java 8 / 依赖 / 构建 / 文档 / demo 分别映射到 AC-08~AC-16。

---

## 15. 设计关 DoD 自检

| # | 检查项（quality-gates §2） | 结论 |
| --- | --- | --- |
| 1 | 选型有实证：偏离/弃用默认栈的每个决策附当场实测证据，无凭记忆断言 | **通过**：§2 全部为当场实测（`dependency:get` EXIT=0、`javap` major 52、完整 `dependency:tree`、编程式装配字节码反编译、javax.mail LICENSE 提取）；**本轮评审新增实证**：`MailClient.send` 无 throws + sms4j 私有 send 异常表（§2.4 #6）、`html(String)` 的 `Class.getResourceAsStream` 语义（§2.4 #12）、`MailFactory.get` 为 void（§2.4 #1）、aifei-enjoy 内含 aifei-all:1.1.2 / major 52 / 零新增依赖（§2.6）；Q1/Q3 结论均有证据；无「凭记忆判不可用」 |
| 2 | 覆盖 PRD 全部 P0，遗漏处显式说明 | **通过**：R-01~R-05 逐条映射到 §14 AC-01~AC-16；§7.5 异常场景由 11 扩到 16（P0 #5）；本期不做清单 §1.4 显式写死 |
| 3 | 文件清单完整，每个文件恰好归属一个任务 | **通过**：§11.1 模块文件（含新增 `META-INF/services/...MailProvider`、`MappedMessage.java`、`DefaultMailClientProxy.java`）+ §11.2 测试文件，均在 §11.3 T01~T10 中恰好出现一次（三新增文件归 T04） |
| 4 | 任务列表有序、标注依赖，T01 为项目基础设施 | **通过**：T01~T10 逐步依赖，T01 = pom + 目录骨架 + 公共签名骨架 + 门禁骨架 |
| 5 | 跨文件约定（命名/错误码/公共工具）已定义 | **通过**：§11.4 定义命名、javadoc、异常消息、依赖探测、配置解析、附件转换（MappedMessage + finally）、线程安全、测试注册表隔离、编码、git 纪律的唯一定义点与约定 |

---

> **一句话总览**：`io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT` 基于 **sms4j-Email-core:3.3.6（已实测 Java 8 兼容、编程式装配完备、无 Spring / 无日志绑定、javax.mail CDDL+GPL+CPE 法务已确认）** 二次封装——根包精确 **5 个公共类型**（`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`）+ `spi` 包 1 类型（`MailProvider` 通道扩展 SPI）+ `aifei.EmailPlugin`/`EmailConfigLoader`；sms4j 邮件插件经 `internal.smtp` 单点隔离且为 `optional`；发送走 `MailBuild.build(config)` → sms4j `MailClient.send()`（同步、void 返回），附件 `byte[]` 经适配层转临时文件，HTML 模板 `#{变量名}` 替换；零存储、零日志绑定、同步发送、重试默认关闭；示例工程 `aifei-email-demo` 端口 18190 以 mock 方式零外网跑通六场景，不发布。
