# aifei-sms 模块需求（任务输入）

> 本文件是**任务输入**（需求 + 验收标准），不是 `design.md`、不是 PRD 补充、不是任务分解。公共 API 逐字签名、包结构与门禁细则、行为契约细节，由 `aifei-sms/docs/design.md` 定稿，本文只给能力诉求与可验收边界。
> 关联：`docs/计划/aifei-eco-modules 模块规划与排期.md` §3.2（`aifei-sms` 立项判词：短信通道——模板化发送 + 回执；工期 2 周；排期 W3–W4 2026-12-07 ~ 12-18 全量交付，原文「先落地 1 家通道，其余按 provider 追加」）；同构范本 `docs/需求/aifei-tools.md`（任务输入型文档结构）、`docs/需求/aifei-audit.md`（单模块完整范式）。
> 坐标与基线：`io.github.gollyhu:aifei-sms:1.0.0-SNAPSHOT`；包名 `cn.hg.aifei.sms`（根包），aifei 接入层落 `cn.hg.aifei.sms.aifei`（对齐其它模块）；Java 8（class major 52）；`cn.aifei:aifei-all` 为 `provided`。
> **技术路线锁定**：基于 **sms4j v3.3.6**（https://gitee.com/dromara/sms4j，dromara 社区出品，Apache-2.0，JDK 8+）进行二次封装。sms4j 是用户点名要求的技术关键词，PRD 中明确出现并作为技术路线锁定，不得被「自研等价」顶替。

## 〇、交付物性质声明（先读，门禁口径依赖此节）

**本次交付物是库模块（可被应用依赖的 SDK），不是业务应用工程。** 三份 PRD 常规章节在本模块 **不适用**：

| 章节 | 结论 | 理由 |
| --- | --- | --- |
| 表结构草案 | **不适用** | 本模块**零存储**：无表、无 DDL、无 `Db+Row`、无 `Generator`、运行期不连任何数据源。短信模板的创建与维护是**消费方**的事，不属于本模块交付物。门禁反向断言主源码（`src/main/java`）禁止 `import java.sql.`。**不得为凑齐模板而编造表。** |
| Service 方法清单 | **不适用** | 模块本体无 `@Path` / Service（那是应用工程的事）。根包 / `internal` / 接入包的公共面**类型清单为预估**（见 §五），须过设计决策、由架构师在 `design.md` 定稿；本文**不代写公共面签名**。 |
| 低保真 UI 原型 | **不适用** | 本模块跳过 PM 原型与 QA 基线门禁；`aifei-sms-demo` 的演示页面由前端直接实装（见第七节），不经原型评审。 |

## 一、背景

### 1.1 缺口与目标

aifei 生态已覆盖微信通道（`aifei-weixin`：公众号 / 小程序 / 微信支付），但**短信通道缺位**——业务普遍各自裸用阿里云 / 腾讯云 / 华为云等厂商 SDK，模板 ID、签名、变量拼装、回执接收各写各的，换厂商即重写发送代码。

本模块补齐**短信通道能力**：以统一接口聚合多厂商短信发送，换厂商只改配置不改代码。基于 **sms4j**（dromara 社区出品的短信聚合框架，定位「短信界的 JDBC」，一套接口聚合 21 家厂商）二次封装，对齐 `aifei-oss` 的「聚合门面 + 适配层隔离第三方」范式。

**四个目标**：

| # | 目标 | 具体含义 |
| --- | --- | --- |
| 1 | **统一发送门面** | 一套 API 发短信（模板化发送），换厂商只改 `sms.*` 配置，不改业务代码 |
| 2 | **多厂商聚合** | 基于 sms4j 聚合 21 家厂商，首期落地阿里云，其余按通道追加（规划原文「先落地 1 家通道」，首期定档阿里云） |
| 3 | **类型隔离** | sms4j 的 `SmsBlend` / `SmsFactory` 等第三方类型不得出现在公共 API 面，经 `internal` 适配层隔离 |
| 4 | **回执接收** | 提供厂商 HTTP 回调的接收入口与验签能力，供消费方注册到 Handler 链 |

### 1.2 使用角色与用户故事

- 作为**业务开发者**，我希望用一套 API 发短信（指定模板 + 变量），以便换厂商只改配置不改代码。
- 作为**平台维护者**，我希望多厂商短信通道有统一配置入口，以便按业务场景切换或并存不同厂商。
- 作为**应用集成者**，我希望短信回执能经统一入口接收与验签，以便不用为每家厂商各写一套回调处理。
- 作为**线上排障者**，我希望发送失败时得到可读的异常信息（而非裸 `NoClassDefFoundError`），以便快速定位是依赖缺失还是厂商返回错误。

### 1.3 范式归属

| 候选范式 | 对标模块 | 是否适用 | 理由 |
| --- | --- | --- | --- |
| **聚合门面 + 适配层隔离** | `aifei-oss`（X File Storage） | **✓ 定位** | sms4j 与 X File Storage 同构：一套接口聚合多后端，换后端只改配置；公共面需隔离第三方类型；有 Plugin + Prop 装配期 |
| 通道接入 Handler + SPI 隔离传输 | `aifei-weixin` | 部分参考 | 回执/回调接收可参考 `WeixinHandler` 的 Handler 注册模式与验签先例，但发送侧不是通道接入型 |
| 无 Plugin 门面型 | `aifei-excel` / `aifei-trace` | **不适用** | sms4j 需要从 `sms.*` Prop 读配置、构建 `SmsBlend` 实例，有装配期，不是随取随用 |

> **结论**：aifei-sms 的发送侧范式归属为**聚合门面 + 适配层隔离**（对齐 `aifei-oss`）；回执侧参考 `aifei-weixin` 的 Handler 接入模式。两种先例在本文中均有引用，不混用。

## 二、范围

- 交付 `io.github.gollyhu:aifei-sms:1.0.0-SNAPSHOT`，包名 `cn.hg.aifei.sms`，Java 8（class major 52）
- 基于 **sms4j** 核心进行二次封装（**引入 sms4j 核心，不引入 sms4j-spring-boot-starter / sms4j-solon-plugin**——aifei 非 Spring 非 Solon，须在 aifei 接入层用 `SmsPlugin(Prop)` 装配，配置走 `sms.*` Prop（已定档，对齐 `aifei-oss` 的 `oss.*` 范式））；sms4j 版本已定档 **v3.3.6**，精确 artifactId（`org.dromara:sms4j-core` 还是其它）仍由架构师实证——本文不臆造精确 artifact 与 API 签名
- 公共面预估（须过设计决策）：`SmsClient` / `SmsKit` / `SmsMessage` / `SmsException`（规划 §3.2 基线）；实际可能需更多（如 `SmsResult` 发送结果、`SmsTemplate` 模板抽象、回执监听器等），见 §五预估 + §八待确认
- 首期落地阿里云通道，其余按通道追加（规划原文「先落地 1 家通道，其余按 provider 追加」，首期定档阿里云）
- 零**强制**第三方运行依赖（sms4j 为 `optional`（已定档），缺失时首次调用抛可读 `SmsException`，对齐 `aifei-excel` 先例）；`aifei-all` 为 `provided`
- 配 `aifei-sms-demo`（不发布）+ 文档四件套（`README.md` / `docs/design.md` / `AGENTS.md` / `docs/testing.md`）

**不做**（边界）：

1. **邮件 / APP 推送**：属其它通道范畴，规划 §6 已明确不建或未排期。
2. **短信模板的创建与管理**（模板 CRUD、审核流程）：属消费方业务系统的事，本模块只负责「用已有模板发送」。
3. **短信内容审核 / 敏感词过滤**：由消费方在调用发送前自行处理。
4. **频率限制 / 防刷策略**：属风控范畴，消费方自行实现（可配合 `aifei-sentinel` / `aifei-cache` 计数限流）。
5. **多厂商负载均衡 / 故障切换**：本期只做「按配置指定厂商发送」，不做自动 failover 与权重路由。
6. **异步发送的内置线程模型**：公共面已定档只提供同步发送、不提供异步 API，异步由消费方自行用线程池 / `aifei-mq` 包装（见 §三前置决策第 4 点）。
7. **短信发送记录的持久化**：模块零存储，发送记录的落库由消费方自行处理。

## 三、必须先定档的前置决策（6 项关键技术张力）

> 以下 6 项是 sms4j 与 aifei 仓库纪律 / 装配范式之间的关键技术张力。每项给**倾向**与**理由**，未获架构师否决即按倾向执行；若被否决，须同步回写本文对应小节与验收要点。

### 3.1 sms4j 与 aifei 装配范式冲突

| 项 | 内容 |
| --- | --- |
| **张力** | sms4j 原生提供 SpringBoot / Solon starter（自动装配、yaml 配置），aifei 用 `Plugin` + `Prop`（`app-config.txt`）装配，非 Spring 非 Solon |
| **倾向** | **引入 sms4j 核心（非 starter）**，在 aifei 接入层用 `SmsPlugin(Prop)` 装配：构造期持 `Prop` 引用，`start()` 内解析 `sms.*` 配置（配置映射**已定档**走 `app-config.txt` Prop，对齐 `aifei-oss` 的 `oss.*` 范式）、构建 sms4j 的 `SmsBlend` 实例并注册；**禁止引入 sms4j-spring-boot-starter / sms4j-solon-plugin**。sms4j 核心编程式构建 `SmsBlend` 的具体调用方式仍待架构师实证（见 §八 Q3） |
| **理由** | 对齐 `aifei-oss` 先例：`aifei-oss` 同样不引入 x-file-storage-spring，只引入 core 并用 `FileStorageServiceBuilder` 编程式构建；aifei 装配范式统一为 Plugin + Prop，不能为 sms4j 单独引入 starter 而破坏一致性 |
| **否决影响** | 若引入 starter，会拖入 Spring / Solon 自动装配依赖，违反「公共 API 零框架类型」与「第三方一律 optional/provided」纪律，工期显著增加 |

### 3.2 公共面零第三方类型

| 项 | 内容 |
| --- | --- |
| **张力** | sms4j 的 `SmsBlend` / `SmsFactory` 是第三方类型，不得出现在根包公共面 |
| **倾向** | **在 `internal` 适配层隔离 sms4j**：`internal.sms4j`（或等价子包）是唯一允许引用 `org.dromara.sms4j.*` 的层；公共面只暴露规划预估的 `SmsClient` / `SmsKit` / `SmsMessage` / `SmsException`，公共签名零第三方类型、零框架类型 |
| **理由** | 对齐 `aifei-oss` 隔离 X File Storage（`org.dromara.x.file.storage.*` 只在 `internal`）、`aifei-excel` 隔离 EasyExcel（`com.alibaba.*` 只在 `internal.easyexcel`） |
| **否决影响** | 公共签名暴露 sms4j 类型会使消费方被迫依赖 sms4j，换内核即破坏二进制兼容，违反「公共 API 实现无关」 |

### 3.3 依赖策略与规划文档差异

| 项 | 内容 |
| --- | --- |
| **张力** | 规划 §3.2 原写「阿里云 / 腾讯云 / 华为云 SDK 均 `optional`」，现改为引入 sms4j 作聚合门面。sms4j 本身 `optional`（缺失抛可读 `SmsException`，对齐 `aifei-excel`）还是 `compile` 例外（对齐 `aifei-oss` 的 x-file-storage-core 已登记例外）？ |
| **倾向** | **已定档 `optional`**（用户裁决）：sms4j 为 `optional`，缺失时首次调用抛可读 `SmsException`（消息含依赖坐标与引入提示，不得裸抛 `NoClassDefFoundError`），对齐 `aifei-excel` 的 EasyExcel 先例。理由：仓库纪律要求「库模块零强制第三方运行依赖」；sms4j 缺失时模块门面类仍可加载（只是发送不可用），不构成「脱离即无任何功能」的 compile 例外 |
| **理由** | `aifei-excel` 的 EasyExcel 为 `optional` 先例已验证可行；sms4j 缺失时模块门面类仍可加载（只是发送不可用），不构成 compile 例外 |
| **否决影响** | 若改为 `compile`，须在根 `AGENTS.md` 登记新的 compile 例外（新增例外一律须先在根登记，模块文档不得单方面声明）；`optional` 已定档，缺失探测与可读异常是 P0 验收项 |

### 3.4 异步线程池

| 项 | 内容 |
| --- | --- |
| **张力** | sms4j 内置线程池统一执行异步发送（核心 / 最大线程数、队列容量可配），但仓库纪律倾向不自带线程模型（`aifei-mq` 定档「同步投递、无线程池」，`aifei-audit` 定档「同步直写、零线程池」） |
| **倾向** | **已定档不提供异步**（用户裁决）：公共面只提供同步发送，不暴露异步发送 API；sms4j 内置线程池不启用或不在公共面暴露；异步需求由消费方自行用线程池 / `aifei-mq` 包装 |
| **理由** | 仓库纪律倾向「库模块不自带线程模型」，避免同一进程多套线程模型冲突；异步发送的线程池调优（核心数、队列、拒绝策略）应由消费方按自身场景决定，模块不代劳 |
| **否决影响** | 若提供异步发送 API，需引入线程池生命周期管理（启停、优雅关闭、拒绝策略），工期显著增加，且需在门禁中处理线程创建断言（`aifei-audit` 的 `G-07` 门禁断言主源码无线程创建） |

### 3.5 回执 / 回调

| 项 | 内容 |
| --- | --- |
| **张力** | 规划要求「回执」。短信回执常为厂商 HTTP 回调（厂商主动 POST 状态报告到业务方指定 URL），形态不同于发送（发送是出站调用，回执是入站接收） |
| **倾向** | **已定档提供回执接收**（用户裁决）：提供接入层 Handler（类似 `aifei-weixin` 的 `WeixinHandler`），供消费方注册到 Handler 链；**提供验签能力**（含阿里云验签实现，首期一并落地——阿里云支持回调）；回执内容解析后交给消费方实现的回执监听器（SPI），模块本身不落库、不做幂等去重 |
| **理由** | 对齐 `aifei-weixin` 的回调先例（`WeixinHandler` 接收回调 + `PayNotifyUtil` 验签解密）：回执接收属接入层职责，验签是安全底线，落库与幂等由消费方承担（同微信 SDK 不做去重的契约） |
| **否决影响** | 若不做回执接收，则规划要求的「回执」能力缺位，消费方需为每家厂商各写一套回调处理，违背「统一通道」定位 |

### 3.6 公共面精确数量

| 项 | 内容 |
| --- | --- |
| **张力** | 规划 §3.2 预估 4 类（`SmsClient` / `SmsKit` / `SmsMessage` / `SmsException`），实际可能需更多（`SmsResult` 发送结果、`SmsTemplate` 模板抽象、回执监听器 SPI 等） |
| **倾向** | **给预估不冻结**：根包预估 4 类（`SmsClient` / `SmsKit` / `SmsMessage` / `SmsException`），可能扩展至 `SmsResult`（发送结果值对象）；回执监听器（`SmsReceiptListener`）**已定档落 `cn.hg.aifei.sms.spi` 包**（用户裁决），根包精确数量（`SmsResult` 是否独立等）仍由架构师在 `design.md` 定稿 |
| **理由** | 未经设计决策不得冻结公共面数量（根 `AGENTS.md`：「未经设计决策，不得新增或扩大公共 API 面」）；预估用于对齐规划基线，不代替架构决策 |
| **否决影响** | 若实际需 >4 类，须在 `design.md` 定稿后回写本文 §五预估表与验收要点中的门禁断言数量 |

## 四、已定档基线（仓库纪律硬约束，设计不得推翻）

| # | 基线 | 需求含义 |
| --- | --- | --- |
| 1 | **Java 8 双约束** | `source` / `target` 均 `1.8`；主 JAR 内全部 class major **52**；不使用 Java 8 之后的语法（`var` / `record` / `switch` 表达式 / text block / `sealed` / pattern matching）与 API（`List.of` / `Map.of` 等） |
| 2 | **公共 API 实现无关** | 根包与 `spi` 包零第三方类型（`org.dromara.sms4j.*`）、零框架类型（`cn.aifei.*`）；公共签名只出现 JDK 类型 + 本模块类型 |
| 3 | **`aifei-all` 为 `provided`** | 不传递，消费方必须自带（README 声明） |
| 4 | **第三方依赖 optional（已定档）** | sms4j 为 `optional`（已定档，见 §三-3.3），阻断传递；缺失时首次调用抛可读 `SmsException`，不得裸抛 `NoClassDefFoundError` / `ClassNotFoundException` |
| 5 | **根包公共类型数量精确** | 由架构门禁 `ArchitectureBoundaryTest` 断言（数量预估 4，实际由 `design.md` 定档后冻结） |
| 6 | **命名约定** | `SmsKit` 属 **Kit**（有状态门面：需要先装配 `SmsPlugin.start()` 才能干活），不得改名为 `SmsUtil`；`SmsClient` 是接口（名词性后缀，对齐 `HttpClient` / `OssClient`） |
| 7 | **包名** | 根包 `cn.hg.aifei.sms`；aifei 接入层 `cn.hg.aifei.sms.aifei`（对齐其它模块）；适配层 `cn.hg.aifei.sms.internal`（或其子包，由 `design.md` 定） |
| 8 | **零存储** | `src/main/java` 禁 `import java.sql.`；不依赖 `aifei-db`；不出现任何 SQL / 表名 / 数据源概念 |
| 9 | **零日志绑定** | 不引入 `org.slf4j.*` / `logback` / `log4j` 实现绑定（test scope 同样不得引入日志绑定实现）；模块自身记录走 `cn.aifei.log.Log`（惰性获取）或如 `aifei-excel` 先例零 `cn.aifei.*` 引用（由 `design.md` 定档） |
| 10 | **文档四件套 + demo** | `README.md`（用法与能力边界声明）、`docs/design.md`（契约事实来源，含公共面定稿）、`docs/testing.md`（测试策略与用例清单）、模块 `AGENTS.md`（开发约束）+ `aifei-sms-demo`（不发布） |
| 11 | **唯一定义点** | 配置键名解析、通道类型映射、异常消息措辞、依赖探测，各自只有一处定义点，实现类不得复制 |
| 12 | **门禁不得放宽** | 架构门禁 `ArchitectureBoundaryTest` 以源码文本扫描断言边界，不得为「兼容错误用法」放宽或绕过 |

## 五、功能需求

> 需求池分级：**R-01 至 R-06 全部为 P0**（缺一不算交付），每条在第六节有对应可验证的验收要点。编号固定，后续只允许在其后追加，不得重排或改号。

| 需求编号 | 能力 | 说明 |
| --- | --- | --- |
| `R-01` | **模板化发送** | 经 `SmsClient`（或 `SmsKit` 静态门面）发送短信：指定通道标识 + 手机号 + 模板 ID + 模板变量（`SmsMessage` 承载），底层经 sms4j 的 `SmsBlend` 发送。换厂商只改 `sms.*` 配置、不改业务代码 |
| `R-02` | **多厂商切换** | 支持 `sms.*` 配置声明多通道（通道标识 + 厂商类型 + 厂商配置项），按标识取用（`SmsKit.use(channelName)` 或 `@Inject SmsClient` 取默认通道）；首期落地阿里云，其余按通道追加 |
| `R-03` | **装配与配置** | `SmsPlugin(Prop)` 完成装配：构造期持 `Prop` 引用，`start()` 内解析 `sms.*` 配置、构建 sms4j `SmsBlend` 实例并注册；配置非法（必填项缺失、通道类型未知等）**启动即失败**（消息含键名与原值）；重复 `start()` / `stop()` 幂等；配置走 `sms.*` Prop（对齐 `oss.*` / `mq.*` 范式） |
| `R-04` | **依赖缺失探测** | sms4j 为 `optional`（已定档），缺失时首次调用发送抛可读 `SmsException`（消息含依赖坐标与引入提示），不得裸抛 `NoClassDefFoundError`；对齐 `aifei-excel` 的 `ExcelDeps` 先例 |
| `R-05` | **回执接收** | 提供接入层 Handler（类似 `aifei-weixin` 的 `WeixinHandler`），供消费方注册到 Handler 链接收厂商 HTTP 回调；提供验签能力（含阿里云验签实现，首期一并落地）；回执内容解析后交给消费方实现的回执监听器（SPI），模块本身不落库、不做幂等去重 |
| `R-06` | **统一异常** | 统一 `SmsException`（`RuntimeException`）收敛：参数错误 / 依赖缺失 / 厂商返回错误 / 网络异常 / 验签失败，保留 cause；消息可读，不含第三方类型名；对齐 `aifei-oss` 的 `OssException` / `aifei-excel` 的 `ExcelException` 先例 |

**P1（本次不做）**：异步发送 API、多厂商负载均衡 / 故障切换、短信模板 CRUD、频率限制 / 防刷、发送记录持久化、批量群发、国际短信特殊处理。

### 5.1 发送语义（`R-01` / `R-02`）

| 场景 | 约定 |
| --- | --- |
| 发送调用 | 同步发送，调用线程阻塞至厂商返回结果（已定档，见 §三-3.4）；发送成功返回 `SmsResult`（预估，承载发送状态与厂商返回的流水号 / 回执 ID），失败抛 `SmsException` |
| 通道选择 | `@Inject SmsClient` 取默认通道；`SmsKit.use(channelName)` 取命名通道（对齐 `aifei-oss` 的 `OssKit.use(platformName)`）；未配置默认通道且未指定通道名 → 启动失败或调用时抛 `SmsException` |
| 模板化发送 | `SmsMessage` 承载手机号、模板 ID、模板变量（key-value）；底层经 sms4j `SmsBlend` 按通道类型映射到厂商模板化发送 API；具体 API 签名由架构师实证 sms4j 核心后定档 |
| 换厂商 | 换通道只改 `sms.*` 配置（厂商类型 + 厂商配置项），业务代码不变（核心价值主张，同 `aifei-oss` 换存储平台只改 `oss.*` 配置） |
| 并发 | `SmsClient` 实现须线程安全（同一实例被并发调用）；底层 `SmsBlend` 的线程安全性由 sms4j 保证，模块不额外加锁 |

### 5.2 装配与配置（`R-03`）

| 项 | 约定 |
| --- | --- |
| 装配形态 | `SmsPlugin`（与 `OssPlugin` / `MqPlugin` 同构：构造期只持 `Prop` 引用，解析与校验全部在 `start()`） |
| 配置前缀 | `sms.*` |
| 通道声明 | `sms.<name>.type`（厂商类型，合法值由 sms4j 支持的厂商集合确定）+ `sms.<name>.<key>`（厂商配置项，如 `access-key` / `secret-key` / `signature` / `endpoint` 等）；配置映射已定档走 `app-config.txt` Prop（对齐 `aifei-oss` 的 `oss.*` 范式），具体配置项集合由架构师实证 sms4j 核心后在 `design.md` 定档 |
| 默认通道 | `sms.default-channel`（多通道时必填，对齐 `aifei-oss` 的 `oss.default-platform`） |
| 配置解析 | 配置键名解析、通道类型映射、厂商配置项校验的唯一定义点在 `internal`（对齐 `aifei-oss` 的 `OssConfigLoader`） |
| 启动期校验 | 配置非法（必填项缺失、通道类型未知、sms4j 依赖缺失等）→ **启动即失败**，抛模块异常，消息含**键名与原值**；不留半装配状态 |
| 幂等 | 重复 `start()` / `stop()` 幂等、不抛异常 |
| 停止后行为 | `stop()` 后发送调用抛模块异常（消息含「插件未启动或已停止」），对齐 `aifei-audit` 的 `AuditKit` 停止后语义 |

### 5.3 依赖缺失探测（`R-04`）

| 项 | 约定 |
| --- | --- |
| 探测方式 | `internal` 适配层唯一 `Class.forName` 点（对齐 `aifei-excel` 的 `ExcelDeps.requireEasyExcel()`），用 `Class.forName("org.dromara.sms4j.xxx", false, loader)` 不初始化 |
| 缺失行为 | 首次调用发送时抛可读 `SmsException`（消息含依赖坐标与引入提示，如 `sms: org.dromara:sms4j-core:3.3.6 is required, add it to your dependencies`），不得裸抛 `NoClassDefFoundError` / `ClassNotFoundException` |
| 探测时机 | **首次调用时探测**（非启动期），对齐 `aifei-excel` 先例（无 Plugin 的 excel 在首次调用探测；有 Plugin 的 sms4j 可在 `start()` 探测或在首次发送时探测，由 `design.md` 定档——倾向 `start()` 探测以启动即失败） |

### 5.4 回执接收（`R-05`）

| 项 | 约定 |
| --- | --- |
| 接收形态 | 接入层 Handler（`cn.hg.aifei.sms.aifei.SmsHandler` 预估名），供消费方注册到 `settings.addHandler(...)`；注册顺序敏感（对齐 `aifei-weixin` 的 `WeixinHandler` 必须注册在 `FrontHandler` 之前） |
| 回调路由 | 厂商 HTTP 回调 POST 到业务方指定 URL（如 `/sms/callback/{channel}`），`SmsHandler` 按 URL 路由到对应通道的回执处理 |
| 验签 | 厂商回调通常带签名（各厂商验签方式不同），适配层按通道类型分发到对应验签实现；**阿里云验签实现首期一并落地**（用户裁决阿里云支持回调）；验签失败返回 HTTP 错误状态或特定响应体（具体由 `design.md` 定档，对齐 `aifei-weixin` 验签失败返回 `fail`） |
| 回执交付 | 验签通过后解析回执内容（发送状态、流水号、时间等），交给消费方实现的回执监听器（SPI，`SmsReceiptListener`，**已定档落 `cn.hg.aifei.sms.spi` 包**）；模块本身不落库、不做幂等去重（对齐 `aifei-weixin` SDK 不做去重的契约） |
| 响应契约 | 回调处理完成后返回厂商要求的响应（部分厂商要求返回特定 JSON 或 `success` 字面量）；具体响应格式由 `design.md` 按通道定档 |

### 5.5 公共面预估（**须过设计决策，由架构师在 `design.md` 定稿**）

本表只是**能力分区与命名口径**，不是签名清单；类型个数为预估，逐字签名、方法形态、构造器、包内分布一律由 `aifei-sms/docs/design.md` 定稿，本文不代写。

| 包 | 预估类型 | 性质 |
| --- | --- | --- |
| 根包 `cn.hg.aifei.sms`（约 4–5 类） | `SmsClient`（发送门面接口）/ `SmsKit`（静态门面）/ `SmsMessage`（发送消息值对象）/ `SmsException`（统一异常）/ `SmsResult`（发送结果值对象，预估） | 公共承诺 API |
| `cn.hg.aifei.sms.spi`（约 1 类） | `SmsReceiptListener`（回执监听器，**已定档落此包**） | 公共承诺 API（消费方实现） |
| `cn.hg.aifei.sms.aifei`（约 2 类，预估） | `SmsPlugin`（装配入口）/ `SmsHandler`（回执接收 Handler） | 接入构件，**非承诺 API**（与 `aifei-oss` 的 `OssPlugin` 同口径） |
| `cn.hg.aifei.sms.internal`（预估） | sms4j 适配层：配置解析、通道工厂、`SmsBlend` 包装、依赖探测、异常构造 | 非承诺实现层，唯一允许引用 `org.dromara.sms4j.*` |

**命名口径**：`SmsKit` 属 **Kit**（需先装配才能干活），不得改叫 `SmsUtil`；`SmsClient` 是接口（名词性后缀，对齐 `OssClient` / `HttpClient`）。

## 六、验收要点

> 每条均可**机械验证**：能被单测、门禁断言或 `mvn dependency:tree` / `javap` 类命令固定判定。禁止「体验良好」「合理」一类不可测措辞。

| 编号 | 对应需求 | 验收条件（可度量） | 验证方式 |
| --- | --- | --- | --- |
| `AC-01` | `R-01` | 配置阿里云通道 → `SmsKit.use(channel)` 或 `@Inject SmsClient` 取到实例 → 发送短信（指定手机号 + 模板 ID + 变量）→ 返回 `SmsResult`（含发送状态与厂商流水号）；发送失败 → 抛 `SmsException` 且消息可读 | 单测（自行实现 `SmsBlend` mock 或在适配层设计可注入点 mock，**不真实发短信**） |
| `AC-02` | `R-02` | 配置 2 家通道（不同厂商类型）→ 各自 `SmsKit.use(name)` 取到独立实例 → 分别发送成功；换默认通道只改 `sms.default-channel` 配置、业务代码不变 | 单测 |
| `AC-03` | `R-01` | 换厂商只改 `sms.*` 配置（厂商类型 + 厂商配置项），**业务发送代码零改动**仍能发送（核心价值主张验证） | 单测 |
| `AC-04` | `R-03` | `SmsPlugin.start()` 后发送可用；`stop()` 后发送抛模块异常（消息含「插件未启动或已停止」）；连续 `start()` 两次 / `stop()` 两次均无异常、状态不叠加 | 单测 |
| `AC-05` | `R-03` | 配置非法（必填项缺失、通道类型未知）→ `start()` 抛模块异常、消息含**键名与原值**；失败后无残留态（再次以合法配置 `start()` 可正常工作） | 单测 |
| `AC-06` | `R-04` | 未引入 sms4j 依赖时首次调用发送 → 抛 `SmsException`、消息含依赖坐标与引入提示、**无裸 `NoClassDefFoundError` / `ClassNotFoundException`** | 单测（模拟依赖缺失，不引 sms4j 或排除后验证） |
| `AC-07` | `R-05` | 厂商回调 POST 到 `SmsHandler` 注册的 URL → 验签通过后回执监听器收到解析后的回执内容（含发送状态、流水号）；验签失败 → 不交付回执、返回约定错误响应 | 单测（mock 厂商回调报文与签名） |
| `AC-08` | `R-06` | 参数错误（手机号为空 / 模板 ID 为空等）→ `SmsException` 且消息可读；厂商返回错误 → `SmsException` 且消息含厂商错误码与描述、保留 cause；网络异常 → `SmsException` 且保留 cause | 单测 |
| `AC-09` | 公共面 | 根包公共签名零第三方类型（`org.dromara.sms4j.*`）、零框架类型（`cn.aifei.*`）；`org.dromara.sms4j.*` import 只出现在 `internal` 适配层 | 门禁 `ArchitectureBoundaryTest` |
| `AC-10` | 公共面 | 根包公共类型数量精确等于 `design.md` 定稿名单（预估 4–5，实际以 `design.md` 冻结为准）；多一个或少一个即失败 | 门禁 `ArchitectureBoundaryTest` + 反射 |
| `AC-11` | 公共面 | `cn.aifei.*` 只允许出现在 `aifei` 接入包（或 `design.md` 白名单登记的 `internal` 日志记录点）；`java.sql.*` / `io.undertow.*` 全模块禁止（库模块本体） | 门禁 `ArchitectureBoundaryTest` |
| `AC-12` | Java 8 | 主 JAR 内全部 class major == **52**；源码无 `var` / `record` / `switch` 表达式 / text block / `List.of(` / `Map.of(` | `javap -verbose` 批量断言 + 门禁正则 |
| `AC-13` | 依赖 | `mvn dependency:tree` 除 `cn.aifei:aifei-all`（provided）外，sms4j 为 `optional`（已定档）、无其它第三方 compile / runtime 依赖、无日志绑定实现 | `dependency:tree` |
| `AC-14` | 构建 | 模块目录内 `mvn clean verify` **0 failure / 0 error** | 构建 |
| `AC-15` | 文档 | 文档四件套齐备（`README.md` / `docs/design.md` / `AGENTS.md` / `docs/testing.md`）+ 根 `README.md` 模块总览补条目 | 文件检查 |
| `AC-16` | demo | `aifei-sms-demo` 可启动（端口定档 **18180**——已占用：18100 mq / 18110 job / 18120 http / 18130 trace / 18140 id / 18150 excel / 18160 audit / 18170 i18n）；发送与回执接收场景可见；页面与源码零外网 host（门禁） | 实测 + 门禁 |

## 七、`aifei-sms-demo` 场景清单

形态与 `aifei-oss-demo` / `aifei-excel-demo` 同款：平级工程、自写 HIO、`start-demo.bat` / `stop-demo.bat`、**离线可跑、零外网**、单页、不经原型基线门禁。

| # | 场景 | 必须可见的演示点 |
| --- | --- | --- |
| 1 | 模板化发送 | 页面填手机号 + 选模板 + 填变量 → 点击发送 → 展示发送结果（状态 + 厂商流水号）；**不真实发短信**，自行实现 `SmsBlend` mock 或在适配层可注入点 mock 厂商响应 |
| 2 | 多通道切换 | 下拉切换通道 → 同一发送代码不变、结果落点不同（不同通道标识） |
| 3 | 配置错误态 | 演示必填项缺失 → 启动失败提示（页面展示错误消息含键名与原值） |
| 4 | 依赖缺失态 | 演示「未引入 sms4j」时首次调用抛可读 `SmsException`（页面展示异常消息含依赖坐标与引入提示） |
| 5 | 回执接收 | 模拟厂商回调 POST → 页面展示回执内容（发送状态、流水号、时间）；验签失败 → 展示拒绝原因 |
| 6 | 空态 | 首次进入、未触发操作时列表显示占位文案，不残留上一次数据 |

约束：demo 不真实发短信（sms4j 不提供内置 mock 能力，自行实现 `SmsBlend` mock 或在适配层设计可注入点 mock 厂商响应）；demo 直接依赖 sms4j 与 `aifei-all` 属示例工程行为，不影响模块零依赖口径（sms4j 在模块 pom 为 `optional`（已定档），demo 以 `compile` 自带验证 optional 口径）。

## 八、待确认问题

> 本节共 **3** 项仍待确认（Q1 artifactId / Q3 装配方式 / Q7 根包数量），其余 7 项（Q2 / Q4 / Q5 / Q6 / Q8 / Q9 / Q10）已定档并固定到对应章节（§三 / §四 / §二 / §五 / §六 / §七 / §九）。保留项不重新编号，§十 / DoD 中 Q1 / Q3 引用不受影响。

| # | 问题 | 倾向 / 默认假设 |
| --- | --- | --- |
| `Q1` | **sms4j 精确 Maven artifactId**：版本已定档 v3.3.6（固定到 §〇 / §二 / §九），精确 artifactId（`sms4j-core`？`sms4j`？`org.dromara:sms4j`？）仍需实证——sms4j 核心 jar 是否存在独立于 starter 的 core artifact？ | **仍待架构师实证**：须用 `mvn dependency:get` + `javap -v` 验证 class major 52 + `dependency:tree` 检查传递依赖（无 Spring / 无日志绑定实现、传递项是否 Java 8 兼容）。实证结论写入 `design.md` §选型实证记录（对齐 `aifei-oss` design.md §2 / `aifei-excel` design.md §2 范式） |
| `Q3` | **sms4j 装配方式**：sms4j 核心是否提供编程式 API（类似 `aifei-oss` 的 `FileStorageServiceBuilder.create(properties).build()`）来构建 `SmsBlend`？还是必须经 yaml / starter 装配？ | **仍待架构师实证**（用户裁决：交架构师证实后给方案）：须验证 sms4j 核心在不引入 starter 的前提下能否编程式构建 `SmsBlend`（传入配置对象 / Prop）。若不能，需评估是否需在 `internal` 适配层自行解析 `sms.*` Prop 并手动构建 sms4j 配置对象。倾向：sms4j 核心提供 `SmsPlugin` / `SmsFactory` 编程式 API（基于 sms4j 文档「`SmsFactory.getSmsBlend(...)` 获取实例」），具体调用方式由架构师实证 |
| `Q7` | **根包公共面精确数量**：预估 4–5 类（根包），`SmsResult` 是否独立成类？（回执监听器已定档落 `cn.hg.aifei.sms.spi` 包，固定到 §五 5.5） | **仍待 `design.md` 定档**：由架构师在 `design.md` 定稿后冻结，回写本文 §五预估表与 `AC-10` 门禁断言数量 |

## 九、参考资产

| 用途 | 位置 |
| --- | --- |
| **聚合门面 + 适配层隔离第三方范式先例**（aifei-sms 发送侧主范式） | `aifei-oss/docs/design.md`（§1.2 基于 X File Storage 二次封装、§3 依赖策略决策、§4 包边界、§5 公共接口契约） |
| **optional + 缺失首次调用抛可读异常先例** | `aifei-excel/docs/design.md`（§2 选型实证记录、§7.2 `ExcelDeps` 依赖探测、§7.5 场景 12 依赖缺失异常消息） |
| **通道接入 Handler + 回调验签先例**（aifei-sms 回执侧参考） | `aifei-weixin/AGENTS.md`（`WeixinHandler` 唯一 import aifei 内核、回调验签、注册顺序敏感）；`aifei-weixin/docs/design.md` |
| **Plugin + Prop 装配范式**（构造期持引用、start() 解析校验、失败不留半装配） | `aifei-oss` 的 `OssPlugin` / `aifei-mq` 的 `MqPlugin` / `aifei-audit` 的 `AuditPlugin` |
| **任务输入与文档范本**（章节顺序、验收要点编号化、待确认问题带默认假设） | `docs/需求/aifei-tools.md`、`docs/需求/aifei-audit.md` |
| **立项依据与工期** | `docs/计划/aifei-eco-modules 模块规划与排期.md` §3.2（短信通道、2 周）、§5.2（W3–W4 排期） |
| **demo 组织方式** | `aifei-oss-demo` / `aifei-excel-demo`（自写 HIO、`start-demo.bat` / `stop-demo.bat`、离线零外网） |
| **全体底线** | 仓库根 `AGENTS.md`（命名约定、Java 8、公共 API 实现无关、门禁不得放宽、compile 例外登记） |
| **sms4j 项目** | https://gitee.com/dromara/sms4j（dromara 社区，Apache-2.0，JDK 8+，v3.3.6） |

## 十、执行方式

- 本文件是**任务输入**：§三 的 6 项前置决策（Q2 / Q4 / Q5 / Q6 已定档并入对应小节，Q7 部分定档部分待 `design.md`，Q3 仍待架构师实证）、§二 的不做清单、§四 的已定档基线不得推翻；**公共面逐字签名、包内分布、异常类型职责、配置键名、sms4j 精确 artifactId**（版本已定档 v3.3.6）由架构师在 `aifei-sms/docs/design.md` 定稿，定稿后本文验收要点的验证通道按定稿位置固化。
- **前置实证先行**：`Q1`（sms4j 精确 artifactId，版本已定档 v3.3.6）、`Q3`（sms4j 核心编程式装配可行性）必须在模块实现前由架构师完成实证并给出结论；结论影响 §三前置决策与 §五公共面形态时先回写本文，再进入实现。mock / 测试能力已定档自行实现 `SmsBlend` mock 或在适配层设计可注入点（见 §六 AC-01 / §七）。
- 本文不提供任务分解与实施顺序（属架构与排期职责）。工期口径引用规划：**2 周**（模块 + demo + 文档 + 门禁），排期 W3–W4（2026-12-07 ~ 12-18）。
- 回写纪律：`design.md` 定稿后同步 `docs/testing.md`（用例清单与 AC 编号一一对应）、`README.md`（能力边界、消费方自带 `aifei-all`、sms4j optional 策略声明（已定档）、§三-3.4 同步发送声明（已定档不提供异步））。

---

## 需求关 DoD 自检

| # | 检查项 | 结论 |
| --- | --- | --- |
| 1 | 目标可一句话说清 | **通过**：`aifei-sms` 基于 sms4j 二次封装，以统一接口聚合多厂商短信发送（模板化发送 + 回执），换厂商只改配置不改代码——公共面隔离 sms4j 类型，模块自身零存储、零日志绑定。 |
| 2 | 每条 P0 需求都有可测 AC | **通过**：`R-01`–`R-06` 共 6 条 P0 全部映射到第六节 `AC-01`–`AC-16`（共 16 条），每条给出可机械验证的判定条件与验证方式；`Q1`（artifactId）/ `Q3` 作为前置实证项单列，未混入 AC。 |
| 3 | 本期不做清单明确 | **通过**：§二「不做」7 条 + §五末 P1 清单（异步 / 负载均衡 / 模板 CRUD / 频率限制 / 持久化 / 群发 / 国际短信），逐条写死；范围蔓延有明确拦截点。 |
| 4 | 假设与待确认问题显式标注、无臆造业务规则 | **通过**：§八共 **3** 项仍待确认（Q1 artifactId / Q3 装配方式 / Q7 根包数量），其余 7 项（Q2 / Q4 / Q5 / Q6 / Q8 / Q9 / Q10）已定档并固定到对应章节；`Q1`（artifactId）/ `Q3`（编程式装配可行性）标注为**架构师实证项**，不在本文臆造 sms4j API 签名；§三 6 项关键技术张力逐条给倾向与否决影响；本文未引入用户未表达的业务规则，sms4j 版本已定档 v3.3.6，artifactId / API 签名标为「架构师实证项」。 |
