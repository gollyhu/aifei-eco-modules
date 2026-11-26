# PRD：aifei-sms

> **交付物性质**：本模块是**库模块 SDK**（可被应用依赖），不是业务应用工程。任务输入 §〇 已定档：表结构草案（零存储、禁 `java.sql.*`）、Service 方法清单（无 `@Path` / 服务）、低保真 UI 原型（跳过 PM 原型与 QA 基线门禁）三章节**不适用**，本文不为凑齐模板而编造表、服务签名或原型。公共 API 逐字签名、包内分布、配置键名、sms4j 精确 artifactId 由架构师在 `aifei-sms/docs/design.md` 定稿，本文只给能力诉求与可验收边界。
>
> **技术路线（锁定，不得推翻）**：基于 **sms4j v3.3.6**（dromara 社区出品，https://gitee.com/dromara/sms4j，Apache-2.0，JDK 8+）二次封装。sms4j 是用户点名的技术关键词，必须以「引入核心 + internal 适配层隔离」的技术路线落地，**不得被「自研等价」顶替**；不引入 sms4j-spring-boot-starter / sms4j-solon-plugin。

## 1. 产品目标

aifei-sms 基于 sms4j v3.3.6 二次封装，为 aifei 生态补齐缺位的短信通道：面向**业务开发者、平台维护者、应用集成者与线上排障者**，以统一接口聚合多厂商短信的**模板化发送**与**回执接收**，换厂商只改 `sms.*` 配置、不改业务代码；公共面隔离 sms4j 第三方类型，模块自身零存储、零强制第三方运行依赖、零日志绑定。**成功标准**：P0 需求 R-01~R-06 全部通过 AC-01~AC-16 的机械验证，架构门禁 `ArchitectureBoundaryTest` 通过，`mvn clean verify` 达 0 failure / 0 error，文档四件套与 `aifei-sms-demo` 齐备。

## 2. 用户故事

- 作为**业务开发者**，我希望用一套 API 发短信（指定模板 + 变量），以便换厂商只改配置不改代码。
- 作为**平台维护者**，我希望多厂商短信通道有统一配置入口，以便按业务场景切换或并存不同厂商。
- 作为**应用集成者**，我希望短信回执能经统一入口接收与验签，以便不用为每家厂商各写一套回调处理。
- 作为**线上排障者**，我希望发送失败时得到可读的异常信息（而非裸 `NoClassDefFoundError`），以便快速定位是依赖缺失还是厂商返回错误。

## 3. 需求池

> 分级口径：R-01~R-06 与横切质量约束 Q-01~Q-08 **全部为 P0**（缺一不算交付）；P1 / P2 见第 4 节「本期不做」。编号沿用任务输入 §五（R-01~R-06 固定、不得重排改号，仅允许在其后追加），验收标准逐条对应任务输入 §六 AC-01~AC-16，全部可机械验证。

### 3.1 需求池总表

| 编号 | 需求 | 优先级 | 对应 AC | 验收标准（摘要） |
|---|---|---|---|---|
| R-01 | 模板化发送 | P0 | AC-01 / AC-03 | 指定手机号+模板ID+变量同步发送返回 `SmsResult`；失败抛可读 `SmsException`；换厂商业务代码零改动 |
| R-02 | 多厂商切换 | P0 | AC-02 | 多通道各取独立实例分别发送成功；换默认通道仅改配置 |
| R-03 | 装配与配置 | P0 | AC-04 / AC-05 | `start()`/`stop()` 幂等；配置非法启动即失败且消息含键名与原值、无残留态 |
| R-04 | 依赖缺失探测 | P0 | AC-06 | 缺失 sms4j 时首次调用抛可读 `SmsException`，无裸 `NoClassDefFoundError` |
| R-05 | 回执接收 | P0 | AC-07 | 验签通过后监听器收到解析回执；验签失败不交付并返回约定错误响应 |
| R-06 | 统一异常 | P0 | AC-08 | 各类失败收敛 `SmsException`、保留 cause、消息可读且不含第三方类型名 |
| Q-01 | 公共面边界 | P0 | AC-09 / AC-10 / AC-11 | 零第三方/框架类型、sms4j import 仅在 internal、根包类型数量与 design.md 名单精确一致 |
| Q-02 | Java 8 基线 | P0 | AC-12 | class major==52，源码无 Java 8 之后语法/API |
| Q-03 | 依赖策略 | P0 | AC-13 | aifei-all provided、sms4j optional、无其它第三方编译/运行依赖、无日志绑定实现 |
| Q-04 | 构建 | P0 | AC-14 | `mvn clean verify` 0 failure / 0 error |
| Q-05 | 文档 | P0 | AC-15 | 文档四件套齐备 + 根 README 补条目 |
| Q-06 | demo | P0 | AC-16 | 18180 端口可启动、发送与回执场景可见、零外网 host |
| Q-07 | 零存储与门禁 | P0 | AC-11 | 全模块禁 `java.sql.*` / `io.undertow.*`，门禁不得放宽 |
| Q-08 | 线程安全 | P0 | AC-01 | `SmsClient` 实现线程安全，并发调用无状态污染 |

### 3.2 功能需求逐条验收标准（Given / When / Then）

**R-01 · 模板化发送（AC-01 / AC-03）**

- Given：已按 `sms.*` 配置阿里云通道并完成 `SmsPlugin.start()` 装配；已通过 `SmsKit.use(channel)` 或 `@Inject SmsClient` 取得实例；底层 `SmsBlend` 以 mock 注入（**不真实发短信**）。
- When：调用发送，传入手机号、模板 ID 与模板变量（由 `SmsMessage` 承载）。
- Then：同步阻塞至厂商返回，成功返回 `SmsResult`（含发送状态与厂商流水号 / 回执 ID）；厂商返回错误时抛可读 `SmsException`。
- AC-03 附加：Given 已配置通道 A 且业务发送代码不变；When 仅改 `sms.*` 配置（厂商类型 + 厂商配置项）切换到通道 B；Then 仍能成功发送——验证「换厂商只改配置、业务代码零改动」这一核心价值主张。

**R-02 · 多厂商切换（AC-02）**

- Given：`sms.*` 中声明两家不同厂商类型的通道，各带独立厂商配置项。
- When：分别用 `SmsKit.use(name)` 取实例并各发一条；随后仅修改 `sms.default-channel` 配置。
- Then：各取到独立实例、分别发送成功；默认通道随配置切换、业务代码不变。未配置默认通道且未指定通道名 → 启动失败或调用时抛 `SmsException`。

**R-03 · 装配与配置（AC-04 / AC-05）**

- AC-04：Given `SmsPlugin(Prop)` 持有合法 `sms.*` 配置；When 执行 `start()` 后发送、再 `stop()`，并将 `start()` / `stop()` 各连续调用两次；Then `start()` 后发送可用，`stop()` 后发送抛模块异常（消息含「插件未启动或已停止」），重复调用幂等无异常、状态不叠加。
- AC-05：Given `sms.*` 配置存在必填项缺失或通道类型未知；When 调用 `start()`；Then 抛模块异常且消息含**键名与原值**，失败后无半装配残留态，再次以合法配置 `start()` 可正常工作。配置解析、类型映射、校验的唯一定义点在 `internal`。

**R-04 · 依赖缺失探测（AC-06）**

- Given：运行时未引入 sms4j 依赖（或构建产物中排除后）。
- When：首次调用发送。
- Then：抛可读 `SmsException`（消息含依赖坐标与引入提示，如 `org.dromara:sms4j-core:3.3.6 is required, add it to your dependencies`），**不得裸抛** `NoClassDefFoundError` / `ClassNotFoundException`；探测在 `internal` 唯一 `Class.forName` 点完成，不初始化类。探测时机（start() 或首次发送）由 `design.md` 定档，**倾向 start() 探测以启动即失败（假设）**。

**R-05 · 回执接收（AC-07）**

- Given：`SmsHandler` 已注册到 Handler 链并绑定回调 URL；消费方已实现 `SmsReceiptListener`（落 `cn.hg.aifei.sms.spi`）；阿里云验签配置就绪。
- When：厂商回调 POST 到注册 URL（携带签名与回执报文）。
- Then：验签通过 → 解析回执内容（发送状态、流水号、时间等）并交给监听器；验签失败 → **不交付回执**、返回约定错误响应。模块本身不落库、不做幂等去重。验签失败响应格式与回调成功响应契约由 `design.md` 按通道定档。

**R-06 · 统一异常（AC-08）**

- Given：发送调用环境就绪。
- When：分别触发参数错误（手机号为空 / 模板 ID 为空等）、厂商返回错误、网络异常、验签失败。
- Then：全部收敛为 `SmsException`（`RuntimeException`）；参数错误消息可读；厂商错误含厂商错误码与描述并保留 cause；网络异常保留 cause；消息可读且**不含第三方类型名**。

### 3.3 横切质量约束逐条验收标准（Given / When / Then）

**Q-01 · 公共面边界（AC-09 / AC-10 / AC-11）**

- Given：模块源码编译完成、`design.md` 已冻结根包公共面名单。
- When：门禁 `ArchitectureBoundaryTest` 做源码文本扫描 + 反射检查。
- Then：根包 / `spi` 包公共签名零第三方类型（`org.dromara.sms4j.*`）、零框架类型（`cn.aifei.*`）；`org.dromara.sms4j.*` import 只出现在 `internal` 适配层；根包公共类型数量精确等于 `design.md` 名单（预估 4–5，多一个或少一个即失败）；`cn.aifei.*` 只允许出现在 `aifei` 接入包（或 `design.md` 白名单登记的 `internal` 日志点）。门禁不得为兼容错误用法放宽或绕过。

**Q-02 · Java 8 基线（AC-12）**

- Given：模块编译产物与源码就绪。
- When：`javap -verbose` 批量断言 + 门禁正则扫描。
- Then：主 JAR 内全部 class major == **52**；`source` / `target` 均 `1.8`；源码无 `var` / `record` / switch 表达式 / text block / `List.of(` / `Map.of(` 等 Java 8 之后语法与 API。

**Q-03 · 依赖策略（AC-13）**

- Given：`mvn dependency:tree` 输出就绪。
- When：核对依赖树与 pom scope。
- Then：除 `cn.aifei:aifei-all`（`provided`，不传递、消费方自带）外，sms4j 为 `optional`（已定档、阻断传递）、无其它第三方 compile / runtime 依赖、无日志绑定实现（test scope 同样不得引入日志绑定实现）。模块自身日志走 `cn.aifei.log.Log`（惰性获取）或如 `aifei-excel` 先例零 `cn.aifei.*` 引用（由 `design.md` 定档）。

**Q-04 · 构建（AC-14）**

- Given：模块目录。
- When：执行 `mvn clean verify`。
- Then：0 failure / 0 error。

**Q-05 · 文档（AC-15）**

- Given：仓库文档目录。
- When：文件检查。
- Then：`README.md`（用法与能力边界声明，含消费方自带 aifei-all、sms4j optional、同步发送声明）/ `docs/design.md`（契约事实来源、公共面定稿）/ `AGENTS.md`（开发约束）/ `docs/testing.md`（测试策略与用例清单，与 AC 编号一一对应）四件套齐备，根 `README.md` 模块总览补条目。

**Q-06 · demo（AC-16）**

- Given：`aifei-sms-demo`（不发布、离线零外网）启动。
- When：实测 + 门禁扫描。
- Then：端口 **18180** 可启动（18100 mq / 18110 job / 18120 http / 18130 trace / 18140 id / 18150 excel / 18160 audit / 18170 i18n 已占用）；模板化发送、多通道切换、配置错误态、依赖缺失态、回执接收、空态 6 个场景可见；页面与源码零外网 host。demo 以 `compile` 自带 sms4j 验证 optional 口径，不改变模块零强制依赖口径。

**Q-07 · 零存储与门禁（AC-11）**

- Given：模块源码就绪。
- When：门禁扫描 `src/main/java`。
- Then：禁 `import java.sql.`、不依赖 `aifei-db`、不出现任何 SQL / 表名 / 数据源概念；`java.sql.*` / `io.undertow.*` 全模块禁止；门禁断言不得放宽。短信模板的创建与维护属消费方，不在本模块。

**Q-08 · 线程安全（AC-01）**

- Given：`SmsClient` 单实例被多线程共享。
- When：并发调用发送。
- Then：实现线程安全、无状态污染；底层 `SmsBlend` 线程安全由 sms4j 保证，模块不额外加锁。

## 4. 本期不做

**§二「不做」清单（7 条，写死）：**

1. 邮件 / APP 推送——属其它通道范畴，规划已明确不建或未排期。
2. 短信模板的创建与管理（模板 CRUD、审核流程）——属消费方业务系统，本模块只负责「用已有模板发送」。
3. 短信内容审核 / 敏感词过滤——由消费方在调用发送前自行处理。
4. 频率限制 / 防刷策略——属风控范畴，消费方自行实现（可配合 aifei-sentinel / aifei-cache）。
5. 多厂商负载均衡 / 故障切换——本期只做「按配置指定厂商发送」，不做自动 failover 与权重路由。
6. 异步发送的内置线程模型——公共面只提供同步发送、不提供异步 API，异步由消费方自行包装。
7. 短信发送记录的持久化——模块零存储，落库由消费方处理。

**P1（应做但本期不做，任务输入 §五末清单写死）：** 异步发送 API、多厂商负载均衡 / 故障切换、短信模板 CRUD、频率限制 / 防刷、发送记录持久化、批量群发、国际短信特殊处理。

**P2（可延后）：** 本期无单独 P2 项；上述 P1 项在本期之后按排期另行评估，不在本模块范围。

## 5. 界面与交互要点

> 本模块为库模块 SDK，无业务页面、无 PM 原型与低保真原型门禁。下列为**编程入口**与**demo 演示页面**清单，不画详细原型。

**编程入口（供消费方集成）：**

- `SmsClient` / `SmsKit`（根包）：发送入口，指定通道 + 手机号 + 模板 ID + 变量，`SmsKit.use(channelName)` 取命名通道、`@Inject SmsClient` 取默认通道；仅同步发送。
- `SmsPlugin(Prop)`（`cn.hg.aifei.sms.aifei`）：装配入口，构造期持 `Prop`，`start()` 解析 `sms.*` 并构建 `SmsBlend`；配置非法启动即失败，需先装配才能使用 `SmsKit`。
- `SmsHandler`（`cn.hg.aifei.sms.aifei`，预估名）：回执接收入口，供消费方注册到 `settings.addHandler(...)`，注册顺序敏感（对齐 `WeixinHandler` 须注册在 `FrontHandler` 之前）。
- `SmsReceiptListener`（`cn.hg.aifei.sms.spi`，已定档）：回执监听器 SPI，消费方实现以接收解析后的回执。
- `SmsMessage` / `SmsResult` / `SmsException`（根包，数量为预估）：消息、结果值对象与统一异常；逐字签名由 `design.md` 定稿。

**`aifei-sms-demo` 演示页面（单页、离线零外网、端口 18180）：**

| 场景 | 关键交互 |
|---|---|
| 模板化发送 | 填手机号 + 选模板 + 填变量 → 发送 → 展示状态与厂商流水号（mock，不真实发短信） |
| 多通道切换 | 下拉切换通道 → 同一发送代码不变、结果落点不同 |
| 配置错误态 | 演示必填项缺失 → 展示含键名与原值的启动失败提示 |
| 依赖缺失态 | 演示未引入 sms4j → 首次调用展示含依赖坐标与引入提示的可读异常 |
| 回执接收 | 模拟厂商回调 POST → 展示回执内容（状态 / 流水号 / 时间）；验签失败展示拒绝原因 |
| 空态 | 首次进入、未触发操作时显示占位文案，不残留上次数据 |

## 6. 非功能要求

- **技术路线**：基于 sms4j v3.3.6 二次封装，引入核心并仅在 `internal` 适配层隔离 `org.dromara.sms4j.*`；禁 starter / solon plugin；**不得以自研等价顶替**。（对齐 AC-09）
- **Java 8 双约束**：`source` / `target` = `1.8`，class major 52，禁用 Java 8 之后语法与 API。（AC-12）
- **公共 API 实现无关**：根包与 `spi` 包零第三方 / 零框架类型，公共签名仅 JDK 类型 + 本模块类型；`SmsKit` 属 Kit 不得改名 `SmsUtil`，`SmsClient` 为接口；根包 `cn.hg.aifei.sms`、接入包 `cn.hg.aifei.sms.aifei`、适配层 `cn.hg.aifei.sms.internal`。（AC-09 / AC-10 / AC-11）
- **依赖策略**：`aifei-all` 为 `provided`（不传递）；sms4j `optional`（缺失抛可读 `SmsException`）；零强制第三方运行依赖；零日志绑定。（AC-13 / AC-06）
- **零存储**：`src/main/java` 禁 `import java.sql.`，不依赖 aifei-db，无 SQL / 表 / 数据源概念。（AC-11）
- **安全**：回执回调必须验签（含阿里云验签实现，首期落地）；验签失败不交付回执并返回约定错误响应。凭据走 `sms.*` 配置中心，禁硬编码。（AC-07）
- **并发**：`SmsClient` 实现线程安全；同步发送，不自带线程模型。（AC-01）
- **唯一定义点**：配置键名解析、通道类型映射、异常消息措辞、依赖探测各只有一处定义点，实现类不得复制。（§四基线 11）
- **构建与文档**：`mvn clean verify` 0 failure / 0 error；文档四件套 + 根 README 补条目；demo 18180 端口零外网。（AC-14 / AC-15 / AC-16）
- **门禁**：`ArchitectureBoundaryTest` 以源码文本扫描断言边界，不得放宽或绕过。（AC-09~AC-11）
- **性能 / 兼容性**：默认（无特殊量化指标；Java 8 兼容口径如上）。

## 7. 待确认问题

**架构师实证项（保留，不得臆造结论）：**

- **Q1 · sms4j 精确 artifactId（待实证）**：版本已定档 **v3.3.6**，精确 artifactId（`org.dromara:sms4j-core`？`sms4j`？）仍需实证——须用 `mvn dependency:get` + `javap -v` 验证 class major 52、`dependency:tree` 检查传递依赖（无 Spring / 无日志绑定实现、传递项 Java 8 兼容），结论写入 `design.md` §选型实证记录。本文不臆造精确 artifact。
- **Q3 · sms4j 编程式装配可行性（待实证）**：不引入 starter 的前提下，sms4j 核心能否编程式构建 `SmsBlend`（倾向存在 `SmsFactory.getSmsBlend(...)` 等 API）；若不能，需评估在 `internal` 自行解析 `sms.*` 并手动构建 sms4j 配置对象。具体调用方式由架构师实证后定档。本文不臆造 sms4j API 签名。
- **Q7 · 根包公共面精确数量（待 design.md 定档）**：预估 4–5 类，`SmsResult` 是否独立、`SmsHandler` 最终命名由 `design.md` 冻结后回写 §5 与 AC-10 门禁断言数量。回执监听器已定档落 `cn.hg.aifei.sms.spi`。

**本文显式标注的假设（推断处，非用户明确规则）：**

- 假设 1：依赖探测时机**倾向 `SmsPlugin.start()` 探测以启动即失败**；若架构师权衡后改在首次发送探测，需回写 R-04 与 AC-06 验证通道（R-03 启动期校验已含依赖缺失启动即失败倾向）。
- 假设 2：`SmsHandler` 为回执 Handler 的**预估名**，`SmsResult` 为发送结果值对象的**预估类**；均以 `design.md` 定稿为准，本文不冻结签名。
- 假设 3：demo 的 sms4j mock 采用「自行实现 `SmsBlend` mock 或在 `internal` 适配层设计可注入点」（任务输入已定档此二选一，具体注入点由 `design.md` 定）。

**留待 design.md 按通道定档（非本文臆造）：** 厂商回调验签失败响应格式、回调成功响应契约、通道类型合法值集合与厂商配置项清单。以上由架构师实证 sms4j 后在 `design.md` 定稿，本文不预先编造。
