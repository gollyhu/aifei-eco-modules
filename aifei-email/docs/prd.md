# PRD：aifei-email（邮件发送 SDK）

> 公共 API 逐字签名、包结构与门禁细则、行为契约细节由 `aifei-email/docs/design.md` 定稿，本文只给能力诉求与可验收边界。
> `aifei-sms` 的「不做」清单中的邮件通道由本模块承接（APP 推送仍不在本期范围）。
> 坐标与基线：`io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT`；根包 `cn.hg.aifei.email`；aifei 接入层 `cn.hg.aifei.email.aifei`（对齐其它模块）；适配层 `cn.hg.aifei.email.internal`；Java 8（class major 52）；`cn.aifei:aifei-all` 为 `provided`。
> **技术路线**：基于 **`org.dromara.sms4j:sms4j-Email-core:3.3.6`**（E 大写）二次封装，对齐 aifei-sms 基于 sms4j-core 的范式。sms4j 邮件插件包名为 `org.dromara.email`（非 `org.dromara.sms4j`）。

## 一、背景

### 1.1 缺口与目标

aifei 生态已覆盖短信通道（`aifei-sms`），但**出站邮件通道缺位**——业务普遍各自裸用 JavaMail / Spring Mail / 厂商 SDK 发邮件，SMTP 配置、附件拼装、HTML 模板渲染、多账号切换各写各的。

本模块以统一接口封装 SMTP 邮件发送，换 SMTP 服务器只改配置不改代码。基于 **sms4j 邮件插件**（`sms4j-Email-core`）二次封装，对齐 `aifei-oss` / `aifei-sms` 的「聚合门面 + 适配层隔离第三方」范式。

> **sms4j 邮件插件定位**：sms4j 邮件插件是 **SMTP 统一封装 + 多配置池**（可做多租户 / 多账号），**不是**多厂商 HTTP API 聚合（没有 SendGrid / SES / Mailgun / 阿里云邮件适配器）。`"qq"` 这类 key 只是**配置池标识**，不是厂商名。这一点必须在下游理解中写清，避免与 aifei-sms 的「多厂商聚合」定位混淆。若未来要接 SendGrid / SES / Mailgun / 阿里云邮件等厂商 HTTP API，走 `MailProvider` SPI 扩展点，见 §三-3.7。

**四个目标**：

| # | 目标 | 具体含义 |
| --- | --- | --- |
| 1 | **统一发送门面** | 一套 API 发邮件（纯文本 / HTML、多收件人、抄送密送、附件），换 SMTP 服务器只改 `email.*` 配置，不改业务代码 |
| 2 | **多 SMTP 配置池切换** | 基于 sms4j 邮件插件多配置池能力（`MailBuild.build(config)`），支持多账号 / 多租户场景，按标识取用 |
| 3 | **类型隔离** | sms4j 邮件插件的 `MailFactory` / `MailSmtpConfig` / 底层邮件客户端等第三方类型不得出现在公共 API 面，经 `internal.smtp` 适配层隔离。第三方类型为 `org.dromara.email.*`（非 `org.dromara.sms4j`）/ `javax.mail.*` |
| 4 | **依赖安全网** | sms4j-Email-core 为 `optional`，缺失时 `EmailPlugin.start()` 即抛可读 `MailException`（启动即失败，含依赖坐标与引入提示），不裸抛 `NoClassDefFoundError` |

### 1.2 使用角色与用户故事

- 作为**业务开发者**，我希望用一套 API 发邮件（纯文本 / HTML + 附件），以便换 SMTP 服务器只改配置不改代码。
- 作为**平台维护者**，我希望多个 SMTP 账号有统一配置入口，以便按业务场景切换或并存不同发件账号（如通知邮箱 vs 营销邮箱）。
- 作为**应用集成者**，我希望邮件附件以字节（`byte[]`）方式传入（而非文件路径），以便在无文件系统的环境中（如云函数、容器内临时目录受限）也能发送带附件邮件。
- 作为**线上排障者**，我希望发送失败时得到可读的异常信息（而非裸 `NoClassDefFoundError`），以便快速定位是依赖缺失还是 SMTP 返回错误。

### 1.3 范式归属

| 候选范式 | 对标模块 | 是否适用 | 理由 |
| --- | --- | --- | --- |
| **聚合门面 + 适配层隔离** | `aifei-oss`（X File Storage）/ `aifei-sms`（sms4j-core） | **✓ 定位** | sms4j 邮件插件与 sms4j 短信核心同构：一套接口封装 SMTP 发送，换 SMTP 配置只改配置；公共面需隔离第三方类型；有 Plugin + Prop 装配期 |
| 通道接入 Handler + SPI 隔离传输 | `aifei-weixin` | **不适用** | 邮件发送是出站调用（主动连 SMTP），不是入站接收（回调 / 回执），不需要 Handler 注册模式 |
| 无 Plugin 门面型 | `aifei-excel` / `aifei-trace` | **不适用** | sms4j 邮件插件需要从 `email.*` Prop 读配置、经 `MailBuild.build(config)` 构建实例，有装配期，不是随取随用 |

> 范式归属为**聚合门面 + 适配层隔离**（对齐 `aifei-oss` / `aifei-sms`）。底层经 sms4j `MailBuild.build(config)` 取回 `MailClient` 实例（非 `MailFactory.put` / `createMailClient`，避免静态注册表副作用）。SMTP 通道用 sms4j 邮件插件做内核；`MailProvider` SPI 扩展点已预留（对齐 `aifei-mq` 的 `MqProvider` + META-INF/services），各厂商 SDK 为 `optional`；首期只落地 SMTP 通道 + 预留 `MailProvider` SPI。

## 二、范围

- 交付 `io.github.gollyhu:aifei-email:1.0.0-SNAPSHOT`，包名 `cn.hg.aifei.email`，Java 8（class major 52）
- 基于 **`org.dromara.sms4j:sms4j-Email-core:3.3.6`**（E 大写）进行二次封装（**引入 sms4j 邮件插件核心，不引入 sms4j-spring-boot-starter / sms4j-solon-plugin**——aifei 非 Spring 非 Solon，须在 aifei 接入层用 `EmailPlugin(Prop)` 装配，配置走 `email.*` Prop，对齐 `aifei-oss` 的 `oss.*` / `aifei-sms` 的 `sms.*` 范式）
- 公共面：根包精确 5（`MailClient`（发送门面接口）/ `MailKit`（有状态静态门面）/ `MailMessage`（收件人 / 主题 / 正文 / 附件值对象）/ `MailResult`（发送结果值对象）/ `MailException`（统一异常））+ `spi` 包精确 1（`MailProvider`，通道扩展 SPI）
- 首期只落地 SMTP 通道（基于 sms4j 邮件插件）；`MailProvider` SPI 扩展点已预留，落 `cn.hg.aifei.email.spi` 包，首期唯一实现 = SMTP provider（包装 sms4j 邮件插件）
- 零**强制**第三方运行依赖（sms4j-Email-core 为 `optional`，缺失时 `EmailPlugin.start()` 即抛可读 `MailException`，启动即失败）；`aifei-all` 为 `provided`
- 配 `aifei-email-demo`（不发布）+ 文档四件套（`README.md` / `docs/design.md` / `AGENTS.md` / `docs/testing.md`）

**不做**（边界，写死）：

1. **邮件模板 CRUD**（模板的创建、审核、管理流程）：属消费方业务系统的事，本模块只负责「用已有模板 / 直接内容发送」，只做 `#{变量名}` 变量替换，不引模板引擎（如 FreeMarker / Thymeleaf）。
2. **IMAP / POP3 收信**：属入站邮件范畴，本模块只做出站发送，不做收信监听。
3. **退信 / 打开点击追踪**（首期）：退信需 IMAP 收信或厂商 webhook，打开 / 点击追踪需嵌入追踪像素与链接重写，首期不做；首期不设 `spi` 包（不做回执 / 退信监听）。
4. **异步发送与内置线程池**：公共面只提供同步发送，不暴露异步 API、不自带线程池（见 §三-3.4）。
5. **发送记录持久化**：模块零存储，发送记录的落库由消费方自行处理。
6. **内容审核 / 敏感词过滤**：由消费方在调用发送前自行处理。
7. **附件病毒扫描**：由消费方在调用发送前自行处理。
8. **群发性能优化**：不做批量群发的性能优化（如连接复用、流水线发送）。
9. **多 SMTP 负载均衡 / 故障切换**：本期只做「按配置指定 SMTP 通道发送」，不做自动 failover 与权重路由。
10. **APP 推送**：属其它通道范畴，不在本模块范围（aifei-sms §二不做清单第 1 项中的 APP 推送部分仍不建）。

## 三、关键设计约束

> 以下为 sms4j 邮件插件与 aifei 仓库纪律 / 装配范式之间的关键约束，结论已并入功能需求与验收要点。

### 3.1 装配范式

| 项 | 内容 |
| --- | --- |
| **约束** | sms4j 邮件插件原生无 Spring / Solon starter（不同于 sms4j 短信有 starter），提供编程式 API `MailBuild.build(config)`（直接接受配置返回客户端，无需先 `put` 再 `createMailClient`），aifei 用 `Plugin` + `Prop`（`app-config.txt`）装配，非 Spring 非 Solon |
| **结论** | **在 aifei 接入层用 `EmailPlugin(Prop)` 装配**：构造期持 `Prop` 引用，`start()` 内解析 `email.*` 配置、经 `MailSmtpConfig.builder()...build()` 构建配置对象、经 `MailBuild.build(config)` 取回 sms4j `MailClient` 实例并包装为模块 `MailClient` 注册到 `@Inject` 单例；**禁止引入 sms4j-spring-boot-starter / sms4j-solon-plugin**（本就不存在邮件 starter，但须禁任何形式的 starter 引入）。**选用 `MailBuild.build(config)` 而非 `MailFactory.put` + `createMailClient`**：前者无静态注册表副作用，`stop()` 时无需清理 sms4j 全局状态（对齐 aifei-sms `SmsFactory` 有静态注册表需注意的差异） |
| **理由** | 对齐 `aifei-oss` 先例：`aifei-oss` 同样不引入 x-file-storage-spring，只引入 core 并用 `FileStorageServiceBuilder` 编程式构建；对齐 `aifei-sms` 先例：同样不引入 sms4j starter，只用 sms4j 核心编程式构建；aifei 装配范式统一为 Plugin + Prop，不能为邮件单独引入 starter 而破坏一致性 |
| **影响** | 若引入 starter（即使将来出现），会拖入 Spring / Solon 自动装配依赖，违反「公共 API 零框架类型」与「第三方一律 optional / provided」纪律 |

### 3.2 公共面零第三方类型

| 项 | 内容 |
| --- | --- |
| **约束** | sms4j 邮件插件的 `MailFactory` / `MailSmtpConfig` / 底层邮件客户端类型（`MailClient` 等，类名为 `org.dromara.email.api.MailClient`）是第三方类型，不得出现在根包公共面。注意：sms4j 邮件插件自身存在名为 `MailClient` 的类型（`org.dromara.email.api.MailClient`），与本模块根包 `MailClient` 同名——靠包路径隔离（`org.dromara.email.*` vs `cn.hg.aifei.email.*`），sms4j 的类型只在 `internal.smtp` 出现 |
| **结论** | **在 `internal.smtp` 适配层隔离 sms4j**（见 §四基线 2）：`internal.smtp` 是唯一允许引用 `org.dromara.email.*`（非 `org.dromara.sms4j`）/ `javax.mail.*` / `com.sun.mail.*` 的层；公共面只暴露定稿的 `MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`（根包 5）+ `MailProvider`（spi 1），公共签名零第三方类型、零框架类型 |
| **理由** | 对齐 `aifei-oss` 隔离 X File Storage（`org.dromara.x.file.storage.*` 只在 `internal`）、`aifei-sms` 隔离 sms4j 短信核心（`org.dromara.sms4j.*` 只在 `internal`）、`aifei-excel` 隔离 EasyExcel（`com.alibaba.*` 只在 `internal.easyexcel`） |
| **影响** | 公共签名暴露 sms4j 类型会使消费方被迫依赖 sms4j，换内核即破坏二进制兼容，违反「公共 API 实现无关」 |

### 3.3 依赖策略

| 项 | 内容 |
| --- | --- |
| **约束** | sms4j-Email-core 为 `optional`（缺失抛可读 `MailException`，对齐 `aifei-excel` / `aifei-sms`）还是 `compile` 例外（对齐 `aifei-oss` 的 x-file-storage-core 已登记例外）？ |
| **结论** | **`optional`**（见 §四基线 4）：sms4j-Email-core 为 `optional`，阻断传递；缺失时 `EmailPlugin.start()` 即抛可读 `MailException`（启动即失败，消息含依赖坐标与引入提示，不得裸抛 `NoClassDefFoundError`），对齐 `aifei-excel` 的 EasyExcel 先例与 `aifei-sms` 的 sms4j 先例。理由：仓库纪律要求「库模块零强制第三方运行依赖」；sms4j 邮件插件缺失时模块门面类仍可加载（只是发送不可用），不构成「脱离即无任何功能」的 compile 例外 |
| **理由** | `aifei-excel` 的 EasyExcel 为 `optional` 先例已验证可行；`aifei-sms` 的 sms4j 为 `optional` 先例；sms4j 邮件插件缺失时模块门面类仍可加载（只是发送不可用），不构成 compile 例外 |
| **影响** | 若改为 `compile`，须在根 `AGENTS.md` 登记新的 compile 例外（新增例外一律须先在根登记，模块文档不得单方面声明）；`optional` ，缺失探测与可读异常是 P0 验收项。**javax.mail 许可证**：sms4j-Email-core 传递依赖 `com.sun.mail:javax.mail:1.6.2`（JavaMail，CDDL 1.1 + GPL 2.0 with Classpath Exception），CPE 豁免可接受，许可证不再是阻塞项；第三方声明动作（LICENSE / NOTICE / README + 根 AGENTS.md 登记）实现期落地 |

### 3.4 重试 / 线程模型

| 项 | 内容 |
| --- | --- |
| **约束** | sms4j 邮件插件内置失败自动重试（`maxRetries` 默认 1，`retryInterval` 默认 5 秒，`maxRetries=0` 可关闭），但仓库纪律倾向不自带线程模型 / 不隐藏失败（`aifei-mq` 定档「同步投递、无线程池」，`aifei-audit` 定档「同步直写、零线程池」，`aifei-sms` 定档「不提供异步」） |
| **结论** | **`maxRetries=0` 关闭**（见 §三-3.4）：避免隐藏失败（重试可能掩盖 SMTP 配置问题或网络故障）；`email.*` 配置默认 `max-retries=0`，不在公共面暴露重试参数。sms4j 邮件插件的重试是在同步调用线程内进行的（非异步线程池），但仍可能隐藏首次失败 |
| **理由** | 仓库纪律倾向「库模块不自带线程模型」与「失败应显式暴露」；隐藏重试会让消费方误以为发送成功而实际经历了重试，排障困难；对齐 `aifei-sms` 的「不提供异步、同步发送」口径 |
| **影响** | 若保留默认重试且不在公共面暴露，需在 README 显式声明「默认重试 N 次」并在 `MailResult` 中暴露重试次数；若提供异步重试 API，需引入线程池生命周期管理，工期显著增加 |

### 3.5 回执 / 退信

| 项 | 内容 |
| --- | --- |
| **约束** | 邮件回执（已读回执）与退信（bounce）处理需 IMAP / POP3 收信或厂商 webhook，形态不同于发送（发送是出站调用，回执 / 退信是入站接收），首期不做 |
| **结论** | **首期不设回执 / 退信 `spi`**：`spi` 包现已存在但用于 `MailProvider` 通道扩展 SPI（通道扩展 SPI），不设回执 / 退信监听 SPI。不提供任何收信能力、不提供退信通知 SPI、不提供打开 / 点击追踪。首期聚焦出站发送。未来若需收信 / 退信监听，走独立 `spi` 扩展（对齐 `aifei-sms` 的 `SmsReceiptListener` 落 `spi` 包的先例），但不在本期 |
| **理由** | 收信属入站，形态与发送完全不同（需 IMAP / POP3 连接管理、轮询 / IDLE 监听、退信解析），首期聚焦出站发送可快速交付；对齐仓库「一次只做一件事」的模块拆分原则 |
| **影响** | 若首期做回执 / 退信，需引入 IMAP / POP3 依赖与监听生命周期管理，工期显著增加，且引入 javax.mail 收信 API 的 CDDL / GPL 许可证更复杂（javax.mail 许可证 CPE 豁免可接受，见 §三-3.3） |

### 3.6 模板渲染

| 项 | 内容 |
| --- | --- |
| **约束** | sms4j 邮件插件支持 HTML 模板邮件（模板放 `resources/template`，变量 `#{变量名}`，支持 `Map` 或实现 `Parameter` 接口的实体传参），是否引入独立模板引擎（FreeMarker / Thymeleaf）增强渲染能力？ |
| **结论** | **不引模板引擎**（见 §三-3.6）：只用 sms4j 内置的 `#{变量名}` 替换（`htmlValues(Map<String,String>)`），或消费方自行渲染 HTML 后以纯 HTML 内容（`htmlContent`）传入 `MailMessage`。模块本身不依赖任何模板引擎 |
| **理由** | 模板引擎引入额外依赖与学习成本，sms4j 内置 `#{变量名}` 替换已能满足简单变量场景；复杂模板渲染由消费方在调用前自行用 FreeMarker / Thymeleaf 等渲染为 HTML 字符串后传入，模块不代劳 |
| **影响** | 若引模板引擎，须在根 `AGENTS.md` 登记新的 compile 依赖，违反「零强制第三方运行依赖」纪律；且模板引擎选型属消费方偏好，不应由模块绑定 |

### 3.7 厂商 API 通道扩展

| 项 | 内容 |
| --- | --- |
| **约束** | sms4j 邮件插件是 SMTP 统一封装 + 多配置池，**不是**多厂商 HTTP API 聚合（无 SendGrid / SES / Mailgun / 阿里云邮件适配器）。若未来要接厂商 HTTP API，需自研 provider 扩展点。首期是否预留 `MailProvider` SPI 接口？ |
| **结论** | **首期预留 `MailProvider` SPI 扩展点**（见 §三-3.7）：`spi.MailProvider` 接口（`type()` + `createClient(String, Map<String,String>) → MailClient`）+ `internal.MailProviderRegistry`（`ServiceLoader` 发现）+ `internal.smtp.SmtpMailProvider`（SMTP 默认实现，包装 sms4j 邮件插件）+ `META-INF/services/cn.hg.aifei.email.spi.MailProvider`；各厂商 SDK 为 `optional`、走 META-INF/services 发现。首期唯一实现 = SMTP provider（包装 sms4j 邮件插件）。落 `cn.hg.aifei.email.spi` 包（落 `cn.hg.aifei.email.spi` 包） |
| **理由** | 对齐 `aifei-mq` 的 `MqProvider` + META-INF/services 扩展范式（已验证可行）：首期预留接口不影响实现量（SMTP provider 是唯一实现），但为未来接 SendGrid / SES / Mailgun / 阿里云邮件 HTTP API 预留路径，避免届时改公共面。**SPI 签名落 `spi` 包**（非 `internal`），使公共契约自洽、实现无关，消费方可不依赖 `internal` 实现自定义厂商 provider |
| **影响** | 若不预留，未来接厂商 HTTP API 需改公共面（新增 `MailProvider` 接口）或硬编码 if-else 分支，违反开闭原则；预留的代价仅是多定义一个接口 + 一个默认实现 |

### 3.8 公共面精确数量

| 项 | 内容 |
| --- | --- |
| **约束** | 公共面预估 5 类（`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`），实际可能需更多（如 `MailProvider` SPI 接口、附件值对象等） |
| **结论** | 根包与 `spi` 包公共类型如下（见 §五 5.4）：根包精确 **5** 类（`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`，`MailResult` 独立成类）+ `spi` 包精确 **1** 类（`MailProvider`）。门禁 `ArchitectureBoundaryTest.publicSurfaceExact` 按文件名集合精确相等断言，多一个或少一个即失败 |
| **理由** | 发送结果需承载「状态 + 通道标识」，`MailResult` 独立值对象职责清晰；集合相等可防同名替换（`aifei-excel` / `aifei-sms` 先例）。`MailProvider` SPI 落 `spi` 包，根包数量不变 |
| **影响** | 若实际需 >5 类（根包），本表为门禁断言名单 |

## 四、基线（仓库纪律硬约束）

| # | 基线 | 需求含义 |
| --- | --- | --- |
| 1 | **Java 8 双约束** | `source` / `target` 均 `1.8`；主 JAR 内全部 class major **52**；不使用 Java 8 之后的语法（`var` / `record` / `switch` 表达式 / text block / `sealed` / pattern matching）与 API（`List.of` / `Map.of` 等） |
| 2 | **公共 API 实现无关** | 根包与 `spi` 包（若设）零第三方类型（`org.dromara.email.*` / `javax.mail.*` / `com.sun.mail.*`）、零框架类型（`cn.aifei.*`）；公共签名只出现 JDK 类型 + 本模块类型 |
| 3 | **`aifei-all` 为 `provided`** | 不传递，消费方必须自带（README 声明） |
| 4 | **第三方依赖 optional** | sms4j-Email-core 为 `optional`（见 §三-3.3），阻断传递；缺失时 `EmailPlugin.start()` 即抛可读 `MailException`（启动即失败），不得裸抛 `NoClassDefFoundError` / `ClassNotFoundException`。javax.mail 许可证（CDDL 1.1 + GPL 2.0 with CPE）CPE 豁免可接受 |
| 5 | **根包公共类型数量精确** | 由架构门禁 `ArchitectureBoundaryTest` 断言（根包精确 **5** 个——`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`；`spi` 包精确 **1** 个——`MailProvider`） |
| 6 | **命名约定** | `MailKit` 属 **Kit**（有状态门面：需要先装配 `EmailPlugin.start()` 才能干活），不得改名为 `MailUtil`；`MailClient` 是接口（名词性后缀，对齐 `SmsClient` / `OssClient` / `HttpClient`） |
| 7 | **包名** | 根包 `cn.hg.aifei.email`；aifei 接入层 `cn.hg.aifei.email.aifei`（对齐其它模块）；适配层 `cn.hg.aifei.email.internal`（或其子包，见 §四基线 7） |
| 8 | **零存储** | `src/main/java` 禁 `import java.sql.`；不依赖 `aifei-db`；不出现任何 SQL / 表名 / 数据源概念 |
| 9 | **零日志绑定** | 不引入 `org.slf4j.*` / `logback` / `log4j` 实现绑定（test scope 同样不得引入日志绑定实现）；模块自身记录走 `cn.aifei.log.Log`（惰性获取）或如 `aifei-excel` 先例零 `cn.aifei.*` 引用 |
| 10 | **文档四件套 + demo** | `README.md`（用法与能力边界声明）、`docs/design.md`（契约事实来源，含公共面定稿）、`docs/testing.md`（测试策略与用例清单）、模块 `AGENTS.md`（开发约束）+ `aifei-email-demo`（不发布） |
| 11 | **唯一定义点** | 配置键名解析、通道类型映射、异常消息措辞、依赖探测，各自只有一处定义点，实现类不得复制 |
| 12 | **门禁不得放宽** | 架构门禁 `ArchitectureBoundaryTest` 以源码文本扫描断言边界，不得为「兼容错误用法」放宽或绕过 |
| 13 | **同步发送** | 公共面只提供同步发送，不暴露异步 API、不自带线程池（sms4j 邮件插件重试以 `maxRetries=0` 关闭，不在公共面暴露，避免隐藏失败，见 §三-3.4） |

## 五、功能需求

> 需求池分级：**R-01 至 R-05 全部为 P0**（缺一不算交付），每条在第六节有对应可验证的验收要点。编号固定，后续只允许在其后追加，不得重排或改号。

| 需求编号 | 能力 | 说明 |
| --- | --- | --- |
| `R-01` | **统一发送** | 经 `MailClient`（或 `MailKit` 静态门面）发送邮件：`MailMessage` 承载收件人（多）、抄送、密送、主题、正文（纯文本 / HTML）、附件（字节 `byte[]`），底层经 sms4j 邮件插件 SMTP 发送。支持 HTML 模板邮件（`#{变量名}` 变量替换，传 `Map` 或实体参数）。换 SMTP 服务器只改 `email.*` 配置、不改业务代码 |
| `R-02` | **多通道切换** | 支持 `email.*` 配置声明多 SMTP 通道（通道标识 + SMTP 配置项），按标识取用（`MailKit.use(name)` 或 `@Inject MailClient` 取默认通道）；基于 sms4j 邮件插件的多配置池能力（`MailBuild.build(config)`）。注意：此处的「通道」指 SMTP 配置池（不同 SMTP 服务器 / 账号），不是厂商 |
| `R-03` | **装配与配置** | `EmailPlugin(Prop)` 完成装配：构造期持 `Prop` 引用，`start()` 内解析 `email.*` 配置、经 `MailSmtpConfig.builder()...build()` 构建配置对象、经 `MailBuild.build(config)` 取回 sms4j `MailClient` 实例并包装注册为 `@Inject` 单例；配置非法（必填项缺失等）**启动即失败**（消息含键名与原值）；重复 `start()` / `stop()` 幂等；配置走 `email.*` Prop（对齐 `oss.*` / `sms.*` 范式） |
| `R-04` | **依赖缺失探测** | sms4j-Email-core 为 `optional`，缺失时 `EmailPlugin.start()` 即抛可读 `MailException`（启动即失败，消息含依赖坐标与引入提示），不得裸抛 `NoClassDefFoundError`；对齐 `aifei-excel` 的 `ExcelDeps` / `aifei-sms` 的依赖探测先例 |
| `R-05` | **统一异常** | 统一 `MailException`（`RuntimeException`）收敛：参数错误 / 依赖缺失 / SMTP 返回错误 / 网络异常 / 认证失败，保留 cause；消息可读，不含第三方类型名；对齐 `aifei-oss` 的 `OssException` / `aifei-sms` 的 `SmsException` 先例 |

**P1（本期不做）**：异步发送 API + 内置线程池、多 SMTP 负载均衡 / 故障切换、邮件模板 CRUD、IMAP / POP3 收信、退信 / 打开点击追踪、发送记录持久化、内容审核 / 敏感词过滤、附件病毒扫描、群发性能优化、模板引擎引入（只用 `#{变量名}` 替换）、sms4j 内置重试在公共面暴露（以 `maxRetries=0` 关闭）、黑名单过滤公共面暴露、附件压缩包（zipName）公共面暴露。

### 5.1 发送语义（`R-01` / `R-02`）

| 场景 | 约定 |
| --- | --- |
| 发送调用 | 同步发送，调用线程阻塞至 SMTP 返回结果；发送成功返回 `MailResult`（承载发送状态与通道标识），失败抛 `MailException`。**补充说明**：sms4j `MailClient.send()` 返回 **void**（无结果对象），`MailResult` 由适配层在无异常时构造（成功无异常即发送成功，`message` 为 `"sent"`，无 SMTP 返回信息可提取） |
| 通道选择 | `@Inject MailClient` 取默认通道；`MailKit.use(channelName)` 取命名通道（对齐 `aifei-oss` 的 `OssKit.use(platformName)` / `aifei-sms` 的 `SmsKit.use(channelName)`）；未配置默认通道且未指定通道名 → 启动失败或调用时抛 `MailException` |
| 邮件内容 | `MailMessage` 承载：收件人（`List`，多收件人）、抄送（`List`，可选）、密送（`List`，可选）、主题（`String`）、正文（`String`，纯文本或 HTML）、附件（`Map<String, byte[]>`）。`MailMessage` 字段与附件 API 形态已由 `design.md` §5.3 定稿，本文不代写 |
| HTML 模板邮件 | 支持 sms4j 邮件插件的 HTML 模板邮件能力（模板放 `resources/template`，变量 `#{变量名}`，支持 `Map` 或实现 `Parameter` 接口的实体传参）；具体 API 暴露方式已由 `design.md` 定档——`MailMessage` 中承载 `htmlContent`（HTML 内容字符串）/ `htmlTemplate`（模板名）+ `templateVariables`（模板变量），由适配层经 TCCL 从 classpath `template/` 加载并以 `htmlValues` 传变量映射到 sms4j。**验收口径**：库侧只断言传给 sms4j 的 `htmlValues` 与模板路径正确，`#{变量名}` 最终渲染在 sms4j `send()` 内部完成、mock 不可断言渲染后字符串，渲染预览放 demo |
| 附件 | 消费方以字节（`byte[]`）方式传入附件，适配层 `SmtpMappers` 将 `byte[]` 写入临时文件后转为 sms4j `Map<显示名, 路径>`（sms4j 原生附件格式），发送后清理临时文件。模块不要求消费方提供文件路径，以适配无文件系统环境（云函数 / 容器临时目录受限）。附件转换的具体实现），业务代码不变（核心价值主张，同 `aifei-oss` 换存储平台只改 `oss.*` 配置、`aifei-sms` 换厂商只改 `sms.*` 配置） |
| 并发 | `MailClient` 实现须线程安全（同一实例被并发调用）；底层 sms4j 邮件客户端的线程安全性由 sms4j 保证，模块不额外加锁 |
| 重试 | sms4j 邮件插件内置失败自动重试（`maxRetries` 默认 1），`maxRetries=0` 关闭、不在公共面暴露（见 §三-3.4）；`email.*` 配置默认 `max-retries=0` |

### 5.2 装配与配置（`R-03`）

| 项 | 约定 |
| --- | --- |
| 装配形态 | `EmailPlugin`（与 `OssPlugin` / `SmsPlugin` / `MqPlugin` 同构：构造期只持 `Prop` 引用，解析与校验全部在 `start()`） |
| 配置前缀 | `email.*` |
| 通道声明 | `email.<name>.smtp-server` / `email.<name>.port` / `email.<name>.from-address` / `email.<name>.username` / `email.<name>.password` / `email.<name>.is-ssl` / `email.<name>.is-auth` / `email.<name>.nick-name` / `email.<name>.retry-interval` / `email.<name>.max-retries`（映射 sms4j `MailSmtpConfig` 字段）；`MailSmtpConfig` 用 **builder 模式**（构造器包私有，用 `MailSmtpConfig.builder()...build()`）；字段类型：`port` / `isSSL` / `isAuth` 均为 **String**（非 int / boolean），`retryInterval` / `maxRetries` 为 **int**；完整配置键全集，对齐 `aifei-oss` 的 `oss.default-platform` / `aifei-sms` 的 `sms.default-channel`） |
| 配置解析 | 配置键名解析、SMTP 配置项校验的唯一定义点在 `aifei.EmailConfigLoader`（读 `Prop`）+ `internal.smtp.SmtpClientFactory`（配置 → `MailSmtpConfig.builder()` 填充） |
| 启动期校验 | 配置非法（必填项缺失、端口非法、int 格式非法等）→ **启动即失败**，抛模块异常，消息含**键名与原值**；不留半装配状态 |
| 幂等 | 重复 `start()` / `stop()` 幂等、不抛异常 |
| 停止后行为 | `stop()` 后发送调用抛模块异常（消息含「插件未启动或已停止」），对齐 `aifei-sms` 的 `SmsKit` 停止后语义 |

### 5.3 依赖缺失探测（`R-04`）

| 项 | 约定 |
| --- | --- |
| 探测方式 | `internal.smtp.MailDeps` 唯一 `Class.forName` 点（对齐 `aifei-excel` 的 `ExcelDeps.requireEasyExcel()` / `aifei-sms` 的依赖探测），用 `Class.forName("org.dromara.email.core.factory.MailFactory", false, loader)` 不初始化 |
| 缺失行为 | 启动期（`EmailPlugin.start()`）抛可读 `MailException`（消息含依赖坐标与引入提示，如 `email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required, add it to your dependencies`），不得裸抛 `NoClassDefFoundError` / `ClassNotFoundException` |
| 探测时机 | **`start()` 探测（启动即失败）**：`MailDeps.requireSms4j()` 在 `EmailPlugin.start()` 执行，启动即失败优于延迟到首次发送；对齐 R-03「配置非法 / 依赖缺失启动即失败」 |
| javax.mail 传递依赖 | sms4j-Email-core 传递依赖 `com.sun.mail:javax.mail:1.6.2`（JavaMail，**CDDL 1.1 + GPL 2.0 with Classpath Exception**），**CPE 豁免可接受**，许可证不再是阻塞项。第三方声明动作（模块 `LICENSE` / `NOTICE` 声明 + `README.md` 注意事项 + 根 `AGENTS.md` 登记 javax.mail 许可证留痕）实现期落地 |

### 5.4 公共面定稿

> 根包**精确 5 类**、`spi` 包**精确 1 类**（`MailProvider`，已预留）。`ArchitectureBoundaryTest.publicSurfaceExact` 按**文件名集合精确相等**断言，多一个或少一个即失败（AC-09）。

| 包 | 定稿类型 | 数量 | 性质 |
| --- | --- | --- | --- |
| 根包 `cn.hg.aifei.email` | `MailClient`（发送门面接口）/ `MailKit`（静态门面）/ `MailMessage`（发送消息值对象 + Builder）/ `MailResult`（发送结果值对象）/ `MailException`（统一异常） | **精确 5** | 公共承诺 API |
| `cn.hg.aifei.email.spi` | `MailProvider`（通道扩展 SPI，已预留） | **精确 1** | 公共承诺 API（消费方实现 / 厂商扩展） |
| `cn.hg.aifei.email.aifei` | `EmailPlugin`（装配入口）/ `EmailConfigLoader`（配置解析） | 2 | 接入构件，**非承诺 API**（与 `aifei-oss` 的 `OssPlugin` / `aifei-sms` 的 `SmsPlugin` 同口径），不计入根包数量 |
| `cn.hg.aifei.email.internal`（含 `internal.smtp` 子包） | 配置解析、通道注册表、Provider 发现、依赖探测、异常构造、SMTP 客户端构建 / 发送 / 类型映射 | 不计数 | 非承诺实现层，唯一允许引用 `org.dromara.email.*` / `javax.mail.*` / `com.sun.mail.*`（仅 `internal.smtp` 子包） |

**命名口径**：`MailKit` 属 **Kit**（需先装配才能干活），不得改叫 `MailUtil`；`MailClient` 是接口（名词性后缀，对齐 `SmsClient` / `OssClient` / `HttpClient`）。首期不设回执 / 退信 `spi`；`MailProvider` SPI 落 `spi` 包。

## 六、验收要点

> 每条均可**机械验证**：能被单测、门禁断言或 `mvn dependency:tree` / `javap` 类命令固定判定。禁止「体验良好」「合理」一类不可测措辞。

| 编号 | 对应需求 | 验收条件（可度量） | 验证方式 |
| --- | --- | --- | --- |
| `AC-01` | `R-01` | 配置 SMTP 通道 → `MailKit.use(channel)` 或 `@Inject MailClient` 取到实例 → 发送邮件（纯文本 + HTML + 多收件人 + 抄送密送 + 附件字节）→ 返回 `MailResult`（含发送状态与通道标识）；HTML 模板邮件：**断言传给 sms4j 的 `MailMessage.getHtmlValues()`（模板变量）与模板路径（TCCL classpath `template/` 资源）正确**——`#{变量名}` 最终渲染在 sms4j `send()` 内部完成、mock 不可断言渲染后字符串，渲染预览放 demo；发送失败 → 抛 `MailException` 且消息可读 | 单测（自行实现 mock 或在适配层设计可注入点 mock SMTP 响应，**不真实发邮件**，对齐 `aifei-sms`  注入点设计，注入点；HTML 模板按 P1 #11 只断言传参、不断言渲染结果，渲染预览见 demo 场景 5） |
| `AC-02` | `R-02` | 配置 2 个 SMTP 通道（不同 SMTP 服务器 / 账号）→ 各自 `MailKit.use(name)` 取到独立实例 → 分别发送成功；换默认通道只改 `email.default-channel` 配置、业务代码不变 | 单测 |
| `AC-03` | `R-01` | 换 SMTP 服务器只改 `email.*` 配置（SMTP 服务器地址 / 端口 / 账号 / 密码等），**业务发送代码零改动**仍能发送（核心价值主张验证） | 单测 |
| `AC-04` | `R-03` | `EmailPlugin.start()` 后发送可用；`stop()` 后发送抛模块异常（消息含「插件未启动或已停止」）；连续 `start()` 两次 / `stop()` 两次均无异常、状态不叠加；**stop→start 配置变更后注入单例自动刷新**（动态委托代理 `DefaultMailClientProxy`，每次 send 现取当前默认通道） | 单测 |
| `AC-05` | `R-03` | 配置非法（必填项缺失、端口非法等）→ `start()` 抛模块异常、消息含**键名与原值**；失败后无残留态（再次以合法配置 `start()` 可正常工作）；**重装配后注入单例指向新配置**（`DefaultMailClientProxy` 自动委托新 `SmtpMailSender`，换 SMTP / 换账号即时生效无需重启） | 单测 |
| `AC-06` | `R-04` | 未引入 sms4j-Email-core 依赖时 `EmailPlugin.start()` 即抛 `MailException`（消息含依赖坐标与引入提示，启动即失败，）、**无裸 `NoClassDefFoundError` / `ClassNotFoundException`** | 单测（模拟依赖缺失，不引 sms4j-Email-core 或排除后验证） |
| `AC-07` | `R-05` | 异常场景**扩至 16 条**（消息逐字断言）：参数错误（message 为 null / 收件人为空 / 主题为空等）、SMTP 错误（场景 10）、网络错误（场景 11）→ `MailException` 且消息可读、保留 cause；**新增场景 12–16**：场景 12 `htmlContent` 与 `htmlTemplate` 同时非空 → 参数异常（互斥）；场景 13 `META-INF/services` 缺失致 `MailProviderRegistry.getProvider("smtp")` 返回 null → Provider 缺失、启动失败；场景 14 附件临时文件写 IO 失败；场景 15 HTML 模板文件不存在（TCCL 加载不到 classpath `template/<name>`）；场景 16 装配期 `MailBuild.build` 抛 `MessagingException`（start 期构建失败，非发送期 10/11）。**场景 10 vs 11 cause 链判据**：cause 链含 `java.net.SocketException` / `java.net.ConnectException` / `java.net.SocketTimeoutException` / `java.io.IOException` → network（场景 11），否则（认证失败 / 收件人拒绝）→ smtp（场景 10），无法归类默认按 smtp；消息不含第三方类型名 | 单测（16 场景逐字断言消息 + cause 保留 + 场景 10/11 分类判据） |
| `AC-08` | 公共面 | 根包 + `spi` 公共签名零第三方类型（`org.dromara.email.*` / `javax.mail.*` / `com.sun.mail.*`）、零框架类型（`cn.aifei.*`）；`org.dromara.email.*` import 只出现在 `internal.smtp` 适配层 | 门禁 `ArchitectureBoundaryTest`（门禁 ①②⑦） |
| `AC-09` | 公共面 | 根包公共类型数量精确等于 `design.md` 定稿名单（**精确 5**：`MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException`）；`spi` 包精确 **1**（`MailProvider`，已预留）；多一个或少一个即失败 | 门禁 `ArchitectureBoundaryTest.publicSurfaceExact`（文件名集合精确相等）+ 反射 |
| `AC-10` | 公共面 | `cn.aifei.*` 只允许出现在 `aifei` 接入包（门禁 ③） | 门禁 `ArchitectureBoundaryTest`（门禁 ③） |
| `AC-11` | 公共面 | `java.sql.*` / `io.undertow.*` 全模块禁止（库模块本体） | 门禁 `ArchitectureBoundaryTest` |
| `AC-12` | Java 8 | 主 JAR 内全部 class major == **52**；源码无 `var` / `record` / `switch` 表达式 / text block / `List.of(` / `Map.of(` | `javap -verbose` 批量断言 + 门禁正则 |
| `AC-13` | 依赖 | `mvn dependency:tree` 除 `cn.aifei:aifei-all`（provided）外，sms4j-Email-core 为 `optional`、无其它第三方 compile / runtime 依赖、无日志绑定实现、无 starter。**依赖树关键项**：javax.mail 1.6.2 / javax.activation 1.1 / com.sun.activation:javax.activation 1.2.0 / slf4j-api 1.7.36（仅 API 无绑定）/ hutool 5.8.28（core/http/cron/setting/log），全 optional，无 Spring / 无日志绑定 / 无 starter | `dependency:tree`（门禁） |
| `AC-14` | 构建 | 模块目录内 `mvn clean verify` **0 failure / 0 error** | 构建 |
| `AC-15` | 文档 | 文档四件套齐备（`README.md` / `docs/design.md` / `AGENTS.md` / `docs/testing.md`）+ 根 `README.md` 模块总览补条目 | 文件检查 |
| `AC-16` | demo | `aifei-email-demo` 可启动（端口 **18190**——已占用：18100 mq / 18110 job / 18120 http / 18130 trace / 18140 id / 18150 excel / 18160 audit / 18170 i18n / 18180 sms）；发送与 HTML 模板渲染场景可见；页面与源码零外网 host（门禁） | 门禁 + 启动验证 |

## 七、`aifei-email-demo` 场景清单

形态与 `aifei-oss-demo` / `aifei-sms-demo` 同款：平级工程、自写 HIO、`start-demo.bat` / `stop-demo.bat`、**离线可跑、零外网**、单页、不经原型基线门禁。

| # | 场景 | 必须可见的演示点 |
| --- | --- | --- |
| 1 | 模板化发送 | 页面填收件人 + 主题 + 选正文类型（纯文本 / HTML）+ 填附件 → 点击发送 → 展示发送结果（状态 + 通道标识）；**不真实发邮件**，经 mock sms4j `MailClient`（`send` 不抛异常即模拟成功）返回 |
| 2 | 多通道切换 | 下拉切换 SMTP 通道（不同配置池标识）→ 同一发送代码不变、结果落点不同（不同通道标识） |
| 3 | 配置错误态 | 演示必填项缺失（如 SMTP 服务器为空 / 端口非法）→ 启动失败提示（页面展示错误消息含键名与原值） |
| 4 | 依赖缺失态 | 演示「未引入 sms4j-Email-core」时 `EmailPlugin.start()` 即抛可读 `MailException`（启动即失败，页面展示异常消息含依赖坐标与引入提示） |
| 5 | HTML 模板渲染 | 演示 HTML 模板邮件：模板含 `#{变量名}` 占位符 → 填变量 → 展示渲染后的 HTML 内容预览 + 附件（字节传入）→ 发送。**说明**：sms4j 的 `#{变量名}` 替换在 `send()` 内部完成、不暴露渲染后字符串，此预览由 demo 展示层自行做同名替换生成（仅用于页面预览，不进入库公共面），与库侧 AC-01「只断言 `htmlValues` 与模板路径正确」口径对齐）；demo 直接依赖 sms4j-Email-core 与 `aifei-all` 属示例工程行为，不影响模块零依赖口径（sms4j-Email-core 在模块 pom 为 `optional`，demo 以 `compile` 自带验证 optional 口径）。demo 端口定档 **18190**（已占用：18100 mq / 18110 job / 18120 http / 18130 trace / 18140 id / 18150 excel / 18160 audit / 18170 i18n / 18180 sms）。

