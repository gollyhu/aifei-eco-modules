# aifei-pay 模块需求文档（PRD + 验收标准）

> **文件定位**：本文是 `aifei-pay` 的**需求事实来源**（背景、范围、用户故事、功能需求、验收标准、需求变更点、待明确事项）。公共 API 逐字签名、包结构、配置键名、依赖 scope 的最终定档、公共面类型数量，属设计内容，由 `aifei-pay/docs/design.md` 定稿；本文只声明「必须支持什么、依赖策略的边界、如何验收」。
> **关联**：`docs/计划/aifei-eco-modules 模块规划与排期.md`（§2.3 能力缺口、§3.2 第二档定位、§4.3 `aifei-weixin → aifei-pay` 契约级关联、§5.2 工期）；同构范本 `docs/需求/aifei-http.md`、`docs/需求/aifei-tools.md`、`docs/需求/aifei-audit.md`。
> **坐标与基线**：`io.github.gollyhu:aifei-pay:1.0.0-SNAPSHOT`；包名 `cn.hg.aifei.pay`；Java 8（class major 52）；`cn.aifei:aifei-all` 为 `provided`。
> **事实来源与调研日期**：IJPay 事实于 2026-11-26 经官方仓库 / 官方文档 / 官方 Gitee Pages 调研，逐条标注来源 URL 与验证状态（见 §2）；规划基线口径截至 2026-11-20。
> **文档版本**：**V2.0**（2026-11-26。V1.1 → V2.0 为模块定位由「支付宝单通道门面」纠偏为「IJPay 多渠道聚合 SDK」，逐条留痕见文末「修订记录」）
>
> **【需求变更声明 · V2.0 · 先读】** V1.1 将本模块定档为「支付宝统一接入门面」，并明确不做 IJPay 其余渠道、不做 provider / 多支付网关抽象。用户于 **2026-11-26** 复核后裁决原需求口径有误，要求模块结构对齐 `aifei-sms`：**一套接口聚合 IJPay 提供的所有支付方式**。四项裁决已固定：① 范围 = IJPay 提供的所有支付方式；② 本模块**保留微信支付**，由用户自行选择本模块或 `aifei-weixin`，两者并存且互不依赖；③ **不保留** `AliPay*` 兼容层，直接破坏性重构；④ 回调模型采用 `PayNotifyUtil.verify` 与 `spi.PayNotifyListener` + `PayHandler` **共存**，两者共用同一验签核心。V2.0 是定位与结构变更，不是 V1.1 的细化；§3.2 / §5 / §6 / §7.4 / §9 / §10 已同步重写，V1.1 的依赖事实与字节码判定口径继续有效。

## 〇、交付物性质声明（门禁口径依赖此节）

**本次交付物是库模块（可被应用依赖的 SDK），不是业务应用工程。** 三份常规章节在本模块不适用：

| 章节 | 结论 | 理由 |
| --- | --- | --- |
| 表结构草案 | **不适用** | 本模块是支付通道 SDK，零 DB、零表、零 DAO、运行期不连数据源；门禁反向断言主源码 `src/main/java` 禁止 `import java.sql.`。交易数据落在消费方自己的业务库，不属于本模块交付物。**不得为凑齐模板而编造表。** |
| Service 方法清单 | **不适用** | 模块本体无 `@Path` / Service（那是消费方应用的事）。根包公共面类型清单为**预估**（见 §7.3），须过设计决策、由架构师在 `design.md` 定稿；本文**不代写逐字签名**。 |
| 低保真 UI 原型 | **不适用** | 本模块无自有 UI 页面；回调入口由消费方 Just Service / Handler 承载（见 §10）。是否配 `aifei-pay-demo` 已裁决不配（2026-11-26 用户裁决，详见 §6.3 / §13）。 |

## 一、背景与目标

### 1.1 缺口

规划文档 §2.3 将「支付通道」列为第三方能力缺口，§3.2 把 `aifei-pay` 定位为第二档「通道接入型」。V1.1 把本模块收窄为支付宝单通道门面，而 IJPay 已聚合支付宝、微信、QQ、京东、银联、PayPal、Stripe、富友、XPay 等多种支付方式；业务若逐渠道裸接，仍需自行处理签名/验签、凭据管理、多商户切换、回调报文解析与响应，且各应用各写一套、口径漂移。同期 `aifei-sms` 已验证「一套接口聚合多厂商」的范式，用户据此裁决本模块按同构结构纠偏。

### 1.2 目标

在 `aifei` 生态中提供**基于 IJPay 的多支付通道聚合 SDK**：以一套 `PayClient` 接口聚合 IJPay 提供的所有支付方式，把「配置 → 下单 / 查单 / 退款 → 回调验签与分发」收口为稳定、可验收的公共契约；换通道只改配置、不改业务代码。

- **给谁用**：Java 8 + aifei 的应用后端开发者（需要多渠道收款、对账、退款的业务）。
- **关键定性**：IJPay 是**依赖内核**（引入其 Maven 构件、调用其公开 API），**不是复制代码、不是 fork 移植**（§7.1 硬约束）。
- **与 `aifei-weixin` 并存**：本模块提供 `wxpay` 通道，用户自行选择本模块或 `aifei-weixin`；两者互不 import、互不依赖（§7.4）。

### 1.3 成功标准

1. 消费方不自行实现签名/验签与凭据管理样板，即可通过统一客户端完成多渠道下单、查单、退款与回调处理。
2. 公共 API **零第三方类型、零框架类型**：IJPay 与各渠道 SDK 的响应 / 模型类型不出现在公共签名中（§7.3）。
3. 依赖策略满足仓库纪律：IJPay 各渠道构件及其 SDK 传递依赖为 `optional`，`aifei-all` 为 `provided`；缺失依赖时给可读异常而非 `NoClassDefFoundError`。
4. 多通道注册、默认/命名通道、未知通道、回调验签与分发、凭据轮换、幂等职责边界、沙箱/生产切换均有可机械验证的验收标准（§9）。
5. 与 `aifei-weixin` 的分工由用户自行决策，模块间零代码依赖（§7.4）。

## 二、IJPay 事实调研（来源与验证状态）

> 调研遵循「不臆造」原则：凡官方文档未逐字给出的点，标注为「部分验证」或「未验证」并进入 §11 待明确事项。**验证状态定义**：已验证 = 官方仓库 README / 官方文档逐字可见；部分验证 = 仅第三方技术资料或间接推断可见；未验证 = 未能从可及来源确认。

### 2.1 调研结论汇总

| # | 事实项 | 结论 | 来源 URL | 验证状态 |
| --- | --- | --- | --- | --- |
| F-01 | 项目定位 | Java 聚合支付 SDK，口号「IJPay 让支付触手可及」；不依赖任何第三方 MVC 框架，仅作工具嵌入任意系统 | `https://gitee.com/javen205/IJPay` | **已验证** |
| F-02 | 授权协议 | Apache License 2.0，`Copyright (c) 2017 IJPay Authors` | `https://gitee.com/javen205/IJPay`（README License 段） | **已验证** |
| F-03 | 模块划分 | `IJPay-Core`（公共基础：加解密/签名/证书/HTTP/二维码/IP）、`IJPay-WxPay`（微信支付）、`IJPay-AliPay`（支付宝 V2，基于 alipay-sdk-java 4.x，不依赖 Core）、`IJPay-AliPay-V3`（支付宝 V3，不依赖 Core）、`IJPay-QQ` / `IJPay-JDPay` / `IJPay-UnionPay` / `IJPay-PayPal` / `IJPay-Stripe` / `IJPay-FuiYou` / `IJPay-XPay`、`IJPay-All`（聚合包，不含 Stripe/AliPay-V3/Servlet 适配）；另有 `IJPay-Servlet-Javax` / `IJPay-Servlet-Jakarta` 适配模块。**本期基线已裁决采用 `IJPay-AliPay-V3`（2026-11-26 用户裁决，详见 D-01 / §7.1），不再使用 V2；V3 不依赖 Core、基于 alipay-sdk-java-v3** | `https://gitee.com/javen205/IJPay`、`https://javen205.gitee.io/ijpay/guide/maven.html` | **已验证** |
| F-04 | 微信模块命名 | 微信支付模块名为 `IJPay-WxPay`，**不是** `ijpay-wechat` | `https://javen205.gitee.io/ijpay/guide/maven.html` | **已验证** |
| F-05 | Maven 坐标 | `groupId=com.github.javen205`；支付宝 V2=`IJPay-AliPay`，V3=`IJPay-AliPay-V3`；另有 `IJPay-Core` / `IJPay-WxPay` / `IJPay-All` 等；**版本采用 `3.0.0`（2026-11-26 用户裁决，详见 D-01 / §7.1）** | `https://javen205.gitee.io/ijpay/guide/maven.html` | **已验证** |
| F-06 | 支付宝能力 | 覆盖下单（`alipay.trade.precreate` / `app.pay` / `wap.pay` / `page.pay` / `create` / `pay`）、查单（`alipay.trade.query`）、退款（`alipay.trade.refund` / `fastpay.refund.query` / `page.refund`）、关单/撤销/结算；封装为 `AliPayApi` 静态方法 + 官方 Model | `https://ijpay.dreamlu.net/ijpay/guide/alipay` | **已验证** |
| F-07 | 签名与多商户 | 支持「普通公钥」与「公钥证书」两种签名方式；多商户多应用，经 `AliPayApiConfig` / `AliPayApiConfigKit` 按当前线程 `appId` 切换 | `https://gitee.com/javen205/IJPay`、`https://ijpay.dreamlu.net/ijpay/guide/alipay` | **已验证**（切 appId 思路已验证；`AliPayApiConfigKit` 注册/切换的完整代码在单独的「支付宝初始化」章节，本次未逐字读取 → 部分验证） |
| F-08 | 回调验签 | 支付宝同步 `return_url` / 异步 `notify_url` 均需验签；第三方资料显示用 `AliPayApi.toMap(request)` + 官方 `AlipaySignature.rsaCheckV1` 验签 | 官方文档 `https://ijpay.dreamlu.net/ijpay/guide/alipay`（**未展开**验签代码）；第三方 `https://blog.gitcode.com/14fbe08e2836545ab244ae505a5adfe1.html` | **部分验证**（需在设计期以 IJPay 源码 / 支付宝 SDK JavaDoc 逐字核对具体方法与证书模式验签差异） |
| F-09 | Java 8 基线 | IJPay 3.x 运行时基线 Java 8+、全部发布模块 class 主版本 52；JDK 9+ 用 `--release 8`，JDK 8 用 `source/target 1.8`；不使用 Java 9+ 语法/API；所有直接与传递依赖基础字节码 ≤ 52。`IJPay-AliPay` / `IJPay-AliPay-V3` 均 Java 8+、不需 Servlet | `https://javen205.gitee.io/ijpay/guide/jdk/support-matrix.html` | **已验证** |
| F-10 | 为保 Java 8 的依赖锁定 | guava 停 `33.6.0-jre`（33.7.0 起字节码 53）、dom4j 停 `2.1.5`（2.2.0 起字节码 55）、okhttp 停 `4.12.0`（5.x 改 KMP 拆分且与支付宝 SDK 4.x 有运行时风险）、jakarta.servlet-api 停 `5.0.0`（6.x 字节码 55）。**口径限定（2026-11-26 设计关实测补正）**：上述锁定值写在 IJPay **父 pom 的 `dependencyManagement`**，按 Maven 规则**不向消费者传播**；V3 路线实际解析为 `okhttp 4.9.3` / `kotlin-stdlib 1.4.10` / `guava 28.1-jre`（见 F-11 与 `design.md` §2.1.2） | `https://javen205.gitee.io/ijpay/guide/jdk/support-matrix.html` | **已验证**（父 pom 锁定值已验证；「不向消费者传播」于 2026-11-26 实测补正） |
| F-11 | 支付宝模块传递依赖 | `IJPay-AliPay`（V2，本期不纳入，§6.4）：基于 alipay-sdk-java 4.x，排除 okhttp 3.12.13 → 统一 okhttp 4.12.0 + kotlin-stdlib，用 fastjson 2.0.66。`IJPay-AliPay-V3`（**本期基线**，实测依赖树 32 个 JAR 见 `design.md` §2.2）：`alipay-sdk-java-v3:3.1.89.ALL`，实际解析 `okhttp 4.9.3`（含 `okio 2.8.0`）/ `kotlin-stdlib 1.4.10` / `guava 28.1-jre`；**V3 路线无 fastjson**，改用 `gson 2.9.0` + `gson-fire 1.8.5`；额外传递 `hutool-crypto` / `hutool-http` / `hutool-json` / `hutool-core` 四件 `5.8.48` 与 `commons-logging:1.1.1`（仅被 `AlipayLogger` 使用）。`dom4j:dom4j`（旧坐标）与 `org.bouncycastle:bcprov-jdk15on` **已由上游 IJPay pom 排除，本模块无需重复排除**；实际生效 `org.dom4j:dom4j:2.1.5` / `org.bouncycastle:bcprov-jdk18on:1.86`（**勿再显式引入 bcprov-jdk15on，避免同名类冲突**） | `https://javen205.gitee.io/ijpay/guide/maven.html`、`design.md` §2.1 / §2.2（2026-11-26 实测） | **已验证**（精确版本与排除生效状态已由设计关 2026-11-26 实测闭环） |
| F-12 | 精确构件版本 | `alipay-sdk-java-v3` 精确版本 = **`3.1.89.ALL`**（由 `IJPay-AliPay-V3:3.0.0` 的 pom 属性 `alipay.version` 硬写，非 `latest-version` 占位）；IJPay 对外构件版本 `3.0.0`（2026-11-26 用户裁决，D-01） | `https://repo1.maven.org/maven2/com/github/javen205/IJPay-AliPay-V3/3.0.0/IJPay-AliPay-V3-3.0.0.pom`（逐字取证见 `design.md` §2.1.1） | **已验证**（2026-11-26 设计关实测闭环，见 `design.md` §2.1.1 / §2.2） |
| F-13 | 扩展点 / SPI | 官方 README 未见明确 SPI / 插件机制说明；形态为「静态工具类 + 配置 Kit + 包装官方 SDK」；未封装接口通过 `AliPayApiConfigKit.getAliPayApiConfig().getAliPayClient()` 取底层 `DefaultAlipayClient` 自行 `execute` | `https://gitee.com/javen205/IJPay`、`https://ijpay.dreamlu.net/ijpay/guide/alipay` | **部分验证**（未见 ≠ 不存在；能否继承/重写具体类须读源码确认，已保留为设计期核对项，见 §11.2） |

### 2.2 已明确排除的调研歧义

- **IJPay 自身含微信支付（IJPay-WxPay），本模块 V2.0 起纳入为 `wxpay` 通道**：用户自行选择本模块或 `aifei-weixin`，两者并存且互不依赖（§7.4.1，2026-11-26 用户裁决）。
- **IJPay 十余个渠道模块纳入聚合范围**：公共面 / 配置 / 注册表 / 门禁面向全渠道；支付宝与微信本期完整迁移，其余渠道按适配器逐项补齐（§3.2-8）。
- 用户原文链接「IJPayj」按笔误处理，指向 `https://gitee.com/javen205/IJPay`（**2026-11-26 用户裁决确认**）。

## 三、范围

### 3.1 本期做（P0 全部，详见 §6）

- **一套聚合接口**：`PayClient` 绑定某一支付通道，提供下单 / 查单 / 退款 / 通道标识；`PayKit` 提供 `defaultClient()` / `use(name)` / `channels()`；`PayPlugin` 完成多通道装配。
- **IJPay 全支付方式覆盖目标**：公共面、配置、注册表、门禁与测试覆盖 IJPay 提供的所有支付方式；支付宝与微信支付本期完整迁移，其余渠道（QQ / 京东 / 银联 / PayPal / Stripe / 富友 / XPay 等）按适配器逐项补齐。
- **支付宝能力迁移**：下单（扫码 / H5 / PC）、查单、退款、回调验签（普通公钥 / 公钥证书 / 同步 GET / 异步 POST）全部迁入 `alipay` 通道适配器。
- **微信支付能力**：以 `wxpay` 通道纳入本模块，由 `IJPay-WxPay` 内核支撑；用户自行选择本模块或 `aifei-weixin`，两者并存、互不依赖。
- **回调双模型**：`PayNotifyUtil.verify` / `verifyAndParse` 为无状态验签入口；`PayHandler` + `spi.PayNotifyListener` 为可选入站闭环；两者共用同一验签核心，不出现两套验签实现。
- **依赖与隔离**：IJPay 各渠道构件及其 SDK 传递依赖为 `optional`；公共签名零第三方 / 零框架类型；缺失依赖给可读异常，配置非法启动即失败。
- **契约验收**：多通道注册 / 默认通道 / 未知通道 / 凭据轮换 / 幂等边界 / 环境切换均有可机械验证的验收标准（§9）。

### 3.2 本期不做（非目标，防范围蔓延）

1. **业务幂等去重与订单状态机**：模块不做「同一通知去重 / 已处理缓存」；`out_trade_no` / 退款单号等幂等键透出给业务，由业务侧前置校验。
2. **业务订单落库与对账**：交易记录、账单核对、财务报表由消费方实现；模块零 DB。
3. **支付页面 UI / 收银台**：模块只产出支付参数与跳转载荷；页面渲染由消费方负责。
4. **复制 / fork IJPay 源码**：IJPay 是依赖内核，禁止把其源码拷进本仓库（§7.1）。
5. **Servlet 适配层**：不引入 `IJPay-Servlet-Javax` / `IJPay-Servlet-Jakarta`；aifei 侧由 `PayHandler` + `PayIo` 承担桥接。
6. **回调送达可靠性**：不保证渠道通知必达、不做主动补单轮询（属业务职责）。
7. **多租户感知**：notify_url 的租户段解析由消费方负责。
8. **IJPay 全渠道一次性实装**：本期目标是结构与公共面覆盖全渠道，支付宝与微信完整迁移，其余渠道按适配器逐项补齐；不得以「结构已支持」宣称未落地渠道可用。

## 四、用户故事

- 作为**业务后端开发者**，我希望用一组稳定的 `PayClient` 方法完成多渠道下单 / 查单 / 退款，以便不直接接触 IJPay 与各渠道 SDK 的类型和样板。
- 作为**业务后端开发者**，我希望通过 `PayKit.use(name)` 切换支付通道，以便新增渠道不改业务代码。
- 作为**业务后端开发者**，我希望已有 Handler 只调 `PayNotifyUtil.verify` 即可验签，也可选注册 `PayHandler` + `PayNotifyListener` 走完整回调闭环。
- 作为**接入方架构师**，我希望 IJPay 各渠道构件以 `optional` 方式依赖、公共 API 不含第三方类型，以便保留仓库的依赖纪律与升级自由。
- 作为**运维 / 安全负责人**，我希望各通道凭据可配置化并在轮换期支持新旧并存，以便凭据更新不需改代码、不停机。
- 作为**业务开发者**，我希望本模块的 `wxpay` 通道与 `aifei-weixin` 二选一使用，两者不互相干扰。
- 作为**测试工程师**，我希望多通道注册、默认通道与回调路由可机械验证，且沙箱与生产配置互不串用。
- 作为**模块维护者**，我希望 IJPay 类型被隔离在渠道适配层，以便升级 IJPay 版本或新增渠道不波及公共契约。

## 五、与原规划的差异表（需求变更点）

> 本节是本次需求相对于 `docs/计划/aifei-eco-modules 模块规划与排期.md` §3.2 / §4.3 / §5.2 的**显式变更清单**。凡标「待裁决」者，未获用户确认前不得在设计中默默采用。

| # | 维度 | 原规划（截至 2026-11-20） | 本次要求 / 变更 | 影响 | 状态 |
| --- | --- | --- | --- | --- | --- |
| D-01 | 依赖策略 | 「纯 JDK 签名，零第三方」（§3.2） | 「基于 IJPay 扩展」→ 依赖 IJPay 各通道构件；支付宝基线仍为 **IJPay-AliPay-V3**（V2 暂不纳入，§6.4） | 根本性变更；scope 与隔离口径见 §7.2 | **已裁决（2026-11-26 用户裁决）** |
| D-02 | 公共面预估 | `AliPayClient` / `AliPayKit` / `AliPayNotifyUtil` / `AliPayException`（§3.2） | V1.1 曾定档「根包 4 类 + `model` 4 值对象」；**V2.0 由 D-10 取代**：删除全部 `AliPay*`，改为 `PayClient` 等公共面并新增 `spi` | 公共面重做，见 D-10 / §7.3 | **已被 D-10 取代（2026-11-26 用户裁决）** |
| D-03 | 依赖 scope | 零第三方（无此议题） | IJPay 各通道构件与渠道 SDK **`optional`**；`aifei-all` `provided`；缺失时可读异常 | 参照 `aifei-excel` / `aifei-tools` 的 optional 内核先例 | **已裁决（2026-11-26 用户裁决，详见 §7.2）** |
| D-04 | 能力范围 | 「支付宝支付：下单/查单/退款/回调验签」（§3.2） | 扩展为 IJPay 全支付方式的统一聚合；支付宝与微信本期完整迁移，其余渠道按适配器逐项补齐 | §3.1 / §3.2-8 / §6.1 新增 R-13~R-15 | **已裁决（2026-11-26 用户裁决）** |
| D-05 | 与微信分工 | 「`aifei-weixin` → `aifei-pay` 复用 `PayApi` 契约」（§4.3，未立项） | 本模块提供 `wxpay` 通道；用户自行选择本模块或 `aifei-weixin`；两者并存、零 import 依赖 | §7.4 / §7.4.1 重写 | **已裁决（2026-11-26 用户裁决）** |
| D-06 | 工期 | 3 周（12/21–01/08，跨元旦，§5.2） | 多渠道聚合扩大改造面；工期由交付负责人重估 | 需求侧不承诺工期 | **不在本文定档** |
| D-07 | IJPay 授权 | 原规划未涉及 | IJPay 为 Apache-2.0（F-02），本模块引入各渠道构件需保留其许可与署名说明 | 合规留痕；README / design 须声明来源与许可 | **需求侧约束已定**（§7.1） |
| D-08 | IJPay-WxPay 口径 | 原 PRD「硬禁 IJPay-WxPay」→ V1.1「默认不引入、消费方自选」 | **V2.0 起本模块纳入 `wxpay` 通道**，由 `IJPay-WxPay` 支撑；用户自行选择本模块或 `aifei-weixin` | §3.2 / §7.4.1 / §10.3 G-03 / AC-20 同步调整 | **口径变更（2026-11-26 用户裁决）** |
| D-09 | 模块定位 | V1.1「支付宝统一接入门面」 | **V2.0「基于 IJPay 的多支付通道聚合 SDK」**，结构对齐 `aifei-sms` | 全文定位、公共面、配置、门禁重写 | **已裁决（2026-11-26 用户裁决）** |
| D-10 | 公共面 | V1.1 根包 4 类 `AliPay*`，`model` 4 值对象 | **删除全部 `AliPay*` 兼容层**；公共面改为 `PayClient` / `PayKit` / `PayNotifyUtil` / `PayException` 等（设计关定稿），新增 `spi.PayNotifyListener` 与 `PayNotify` | §7.3 / design.md 定稿 | **已裁决（2026-11-26 用户裁决）** |
| D-11 | 配置模型 | V1.1 `pay.apps.<appId>.*` | 改为 `pay.default-channel` + `pay.<name>.type` + `pay.<name>.*`；支付宝原 `appId` 参数作为通道内商户标识保留 | §6.2 / design.md 定稿 | **已裁决（2026-11-26 用户裁决）** |
| D-12 | 回调模型 | V1.1 仅 `AliPayNotifyUtil.verify` / `verifyAndParse` | **`PayNotifyUtil.verify` 与 `spi.PayNotifyListener` + `PayHandler` 共存**；两者共用同一验签核心 | §6.1 R-15 / §8.1 / §9 AC-23~AC-25 | **已裁决（2026-11-26 用户裁决）** |

## 六、功能需求

需求池分级：**P0（本期必须，缺了不算交付）** 全部映射到 §9 AC；P1 / P2 见 §6.4。编号固定，只允许在其后追加，不得重排或改号。

### 6.1 P0 需求清单

| 编号 | 需求 | 说明 |
| --- | --- | --- |
| `R-01` | **多通道装配与门面** | 提供 `PayPlugin`（配置前缀 `pay.*`），在 `start()` 装载并校验全部通道配置；`@Inject PayClient` 取默认通道；`PayKit` 提供 `defaultClient()` / `use(name)` / `channels()`；配置非法**启动即失败**（消息含键名与敏感值脱敏提示），失败不留半装配态。 |
| `R-02` | **下单** | 通过 `PayClient` 的统一下单动作完成各通道支持的下单形态（支付宝扫码 / H5 / PC、微信 JSAPI / Native 等，具体方法由 `design.md` 定稿）；返回公共值对象（不含第三方类型），含拉起或跳转载荷。 |
| `R-03` | **查单** | 按商户订单号查询交易状态；返回本模块公共值对象，含交易状态、金额、渠道交易号等必要字段；不存在语义由通道适配器收敛为唯一口径。 |
| `R-04` | **退款** | 按商户订单号 + 商户退款单号 + 金额发起退款；退款单号作为幂等键透传；返回退款结果公共值对象。 |
| `R-05` | **回调验签** | `PayNotifyUtil.verify` / `verifyAndParse` 对已提供报文做验签，结果明确（通过 / 失败），失败不静默；验签是**纯函数**，同一入参多次调用结果一致，不修改入参。 |
| `R-06` | **凭据轮换** | 各通道配置支持多组验签凭据并存（支付宝公钥 / 证书、微信平台证书等），按通道规则选取；过期 / 未配置凭据验签失败。 |
| `R-07` | **幂等职责边界** | 模块**不做**通知去重与已处理缓存；须从回调结果透出 `out_trade_no` 等幂等键供业务前置校验；同一报文重复调用验签结果稳定、模块内部不产生副作用。 |
| `R-08` | **环境切换** | 通过配置切换沙箱与生产（网关 / 凭据）；切换不改业务代码；两套环境互不串用。 |
| `R-09` | **多商户 / 多应用** | 通道内支持显式指定商户 / 应用标识（如支付宝 appId、微信 mchId），未指定时回退通道默认值；不同标识的配置互不串扰。 |
| `R-10` | **依赖缺失可读异常** | 未引入所需 IJPay 通道构件时，首次调用抛本模块 `PayException`（含缺失依赖提示），不裸抛 `NoClassDefFoundError` / `ClassNotFoundException`。 |
| `R-11` | **公共面收敛与依赖纪律** | 根包公共类型精确且由架构门禁断言；公共签名零第三方 / 零框架类型；IJPay 与各渠道 SDK 类型仅在 `internal` 适配层出现。 |
| `R-12` | **异常语义统一** | 业务调用失败（签名失败 / 接口返回错误 / 验签失败）统一收敛为 `PayException`（含可读消息与 cause）；不向调用方抛 IJPay / SDK 原始异常类型。 |
| `R-13` | **多通道聚合** | 通道由 `pay.<name>.type` 声明，`PayClients` 维护通道注册表；默认通道由 `pay.default-channel` 指定；未知通道 / 未装配 / 已停止均抛 `PayException`；新增通道不改公共面。 |
| `R-14` | **微信支付通道** | 本模块以 `wxpay` 通道纳入 IJPay-WxPay；用户自行选择本模块或 `aifei-weixin`；两者可并存但互不 import、互不共享配置与回调链路。 |
| `R-15` | **回调闭环与 SPI** | `spi.PayNotifyListener` + `aifei.PayHandler` 为可选入站闭环：按通道读报文 → 调同一验签核心 → 解析为 `PayNotify` → 同步分发监听器 → 按通道写响应；验签失败不分发；模块不做幂等与状态机。 |

### 6.2 配置能力诉求（键名与默认值由 `design.md` 定稿）

> 本表只约束「必须可配」的能力。所有凭据类配置**不得出现在日志 / 异常消息明文**中。

| 能力 | 约束 | 建议默认 |
| --- | --- | --- |
| 通道标识 `<name>` | 必填，作为 `pay.<name>.*` 的前缀与 `PayKit.use(name)` 的键 | 无 |
| 通道类型 `pay.<name>.type` | 必填，取 IJPay 渠道白名单（`alipay` / `wxpay` / `qq` / `jdpay` / `unionpay` / `paypal` / `stripe` / `fuiou` / `xpay` 等） | 无 |
| 默认通道 `pay.default-channel` | 多通道必填，单通道可缺省取唯一通道 | 无 |
| 商户 / 应用标识 | 通道内标识（支付宝 appId、微信 mchId 等），按通道必填规则校验 | 无 |
| 通道凭据 | 私钥、公钥、证书、API 密钥等，按通道必填规则校验；**支持多组并存**以轮换 | 无 |
| 签名方式 | 按通道支持方式配置（支付宝公钥 / 公钥证书等） | 通道默认值 |
| 网关环境 | 沙箱 / 生产切换；网关 URL 与凭据组由配置显式管理 | 沙箱（仅开发期） |
| 回调地址 notifyUrl / returnUrl | 下单时逐笔传入或通道级默认 | 逐笔传入 |
| 超时 | 连接 / 读取两段（若 IJPay 内核暴露） | 默认（对齐 `aifei-http` 建议值） |

### 6.3 P1（应做，资源允许则做）

- 支付宝退款查询（`alipay.trade.fastpay.refund.query`）、关单（`alipay.trade.close`）、撤销（`alipay.trade.cancel`）。
- 支付宝 APP 支付（`alipay.trade.app.pay`）、交易创建（`alipay.trade.create`）、条码支付（`alipay.trade.pay`）等其余下单形态。
- 微信支付其余下单形态与各渠道扩展动作（按 IJPay 通道能力逐项补齐）。
- ~~`aifei-pay-demo` 单页演示~~（**已裁决不配**，2026-11-26 用户裁决，详见 §13）。

### 6.4 P2（明确延后）

- 转账 / 红包 / 花呗分期 / 结算等非收款主链路接口。
- 主动补单轮询、通知送达补偿。
- `IJPay-AliPay` V2 路线：本期支付宝以 `IJPay-AliPay-V3` 为基线；V2 仅在用户另行裁决后纳入。
- IJPay 全渠道一次性实装：按 §3.2-8 的逐项补齐口径执行。

## 七、IJPay 扩展方案的需求侧约束

### 7.1 IJPay 是依赖内核，不是复制代码（硬约束）

- 本模块通过 **Maven 依赖**引入 IJPay 各支付通道构件（支付宝基线 `com.github.javen205:IJPay-AliPay-V3:3.0.0`；微信 `IJPay-WxPay`；其余渠道按白名单逐项引入），调用其公开 API 完成签名 / 报文 / 传输；**禁止**把 IJPay 源码复制、fork、改写后放入本仓库。
- IJPay 为 Apache-2.0（F-02）：`README.md` 与 `docs/design.md` 须声明依赖来源、版本与许可，保留其署名；不修改其版权信息。
- 抽象方向：本模块是 **IJPay 之上的 aifei 聚合适配层**（配置装配 + 通道注册表 + 公共值对象隔离 + 回调入口适配 + 错误收敛），不是 IJPay 的替代品，也不重复实现其已有能力。
- **扩展形态：采用包装（组合），非继承**（**2026-11-26 用户裁决**）。各渠道 adapter 只组合 IJPay / 渠道 SDK，不继承其类型。

### 7.2 依赖策略（Java 8 兼容 + `optional`）

- `cn.aifei:aifei-all` 为 `provided`（不传递，消费方自带）。
- IJPay 各通道构件及其渠道 SDK 一律 `optional` / 阻断传递，由消费方按实际使用的通道显式声明；若某通道必须改为 `compile`，须先在根 `AGENTS.md` 登记例外——需求侧不预设例外。
- **Java 8 兼容性风险（需求侧必须盯住）**：验收标准 `AC-02` 要求对**最终依赖树**逐个断言「对 Java 8 可加载的 class」字节码 ≤ 52（`module-info.class` 与 `META-INF/versions/**` 不纳入判定，理由与实证见 AC-02 / `design.md` §2.3）。V3 支付宝路线已于 2026-11-26 实测闭环；**新增任何 IJPay 通道构件前必须重复同一验证**，未通过不得纳入白名单。
- **V3 依赖锁定继续有效**：`dom4j:dom4j`（旧坐标）与 `org.bouncycastle:bcprov-jdk15on` 已由上游排除；实际生效 `org.dom4j:dom4j:2.1.5` 与 `org.bouncycastle:bcprov-jdk18on:1.86`；**严禁再显式引入 `bcprov-jdk15on`**。
- 依赖缺失时给可读 `PayException`（R-10），对齐 `aifei-excel` / `aifei-tools` 的 optional 先例。
- 不引入任何日志绑定；模块自身记录走 `cn.aifei.log.Log`（惰性获取）。

### 7.3 公共面隔离（零第三方 / 零框架类型）

- 根包公共面由 `design.md` 定稿，至少包含：`PayClient`（通道客户端接口）/ `PayKit`（有状态门面：`defaultClient()` / `use(name)` / `channels()`）/ `PayNotifyUtil`（无状态验签工具）/ `PayException`（统一异常）。**删除全部 `AliPay*` 类型，不设兼容层**；新增公共类型须先过设计决策并回写本文 §5 D-10。
- `spi` 包提供 `PayNotifyListener` 与 `PayNotify`；`aifei` 包提供 `PayHandler` / `PayIo`（非承诺 API，不计入根包公共面）。
- **IJPay 与各渠道 SDK 的类型不得出现在任何公共签名中**；由 `internal` 适配层转换为本模块自有值对象。与 `aifei-weixin` 的 `JSONObject` 返回口径不互相沿用，本模块公共返回始终为自有值对象。

### 7.4 与 `aifei-weixin` 的分工边界

| 维度 | `aifei-weixin`（既有） | `aifei-pay`（本模块） |
| --- | --- | --- |
| 渠道 | 微信支付 V3（`cn.aifei.weixin.pay`） | IJPay 全支付通道，含 `wxpay` 与 `alipay` 等 |
| 实现形态 | 纯 JDK 实现（签名 / 加解密 / HTTP 自研） | 依赖 IJPay 内核与渠道 SDK（不开 second implementation） |
| 动作语义 | `PayApi`：`jsapi` / `query` / `close` / `refund` | `PayClient`：下单 / 查单 / 退款 / 通道标识；动作语义同构但类型独立 |
| 回调模型 | `PayNotifyUtil`：`verify` / `decryptResource` / `verifyAndDecrypt` | `PayNotifyUtil.verify` / `verifyAndParse` 与 `PayHandler` + `PayNotifyListener` 共存 |
| 回调幂等 | 明确**不做**幂等，业务按 `out_trade_no` + 状态前置校验 | 同左（R-07） |
| 公共返回类型 | `JSONObject`（fastjson2，既有事实） | 自有值对象（§7.3） |

**选择权归用户（2026-11-26 裁决）**：两套微信支付能力并存，同一业务链路应二选一，不得混用凭据与回调链路；模块之间零 `import` 依赖由门禁断言（§10）。本模块不沿用 `aifei-weixin` 的 `JSONObject` 返回口径。

### 7.4.1 IJPay-WxPay 与 `aifei-weixin` 的取舍说明（2026-11-26 用户裁决）

IJPay 自带微信支付能力（`IJPay-WxPay`），与本仓库 `aifei-weixin` 的 `cn.aifei.weixin.pay` 在能力上重叠。**用户裁决（2026-11-26）：本模块保留微信支付，以 `wxpay` 通道纳入；用户自行选择本模块或 `aifei-weixin`，两者并存且互不依赖。**

1. **本模块提供 `wxpay` 通道**：`IJPay-WxPay` 作为 optional 内核由消费方按需引入；本模块公共面与门禁覆盖该通道，IJPay 类型仅出现在 `internal` 适配层。
2. **两者差异**（供消费方决策）：
   - `aifei-weixin`：本仓库**纯 JDK 自研**（签名 / 加解密 / mTLS / HTTP 全自实现，零第三方运行依赖），与微信支付 V3 官方协议直接对齐，已落地并验证。
   - 本模块 `wxpay`：IJPay 封装的微信支付，依赖其内核与传递依赖，形态为「静态工具类 + 包装官方 SDK」，与支付宝等其它通道共用一套聚合 API。
3. **使用边界**：同一业务链路应二选一，不得混用凭据与回调链路；若已选本模块的多渠道聚合，则微信侧走 `PayKit.use("wxpay")`，不要绕过门面直用 `IJPay-WxPay`。
4. **门禁口径**：G-03 由「禁其它渠道」改为「仅允许 `PayTypes` 白名单内的 IJPay 渠道包」；`com.ijpay.wxpay.*` 只允许出现在 `internal` 适配层。§10.3 与 AC-20 已同步调整。

## 八、关键契约

### 8.1 回调验签与分发（R-05 / R-15）

| 项 | 约定 |
| --- | --- |
| 报文来源 | 各渠道回调报文以 `PayNotifyRequest` 统一表达（参数 Map、原始 body、header）；不依赖 Servlet 类型 |
| 签名方式 | 按渠道支持方式验签（支付宝普通公钥 / 公钥证书、微信平台证书等）；算法差异由渠道 adapter 收敛 |
| `PayNotifyUtil` | 无状态入口：`verify(...)` 返回布尔；`verifyAndParse(...)` 返回 `PayNotify`，失败抛 `PayException`；不读 HTTP、不写响应、不调监听器 |
| `PayHandler` + `PayNotifyListener` | 可选入站闭环：读报文 → 调同一验签核心 → 解析 → 同步分发监听器 → 按渠道写响应；验签失败不分发 |
| 单一实现点 | 两种模型共用同一 `internal` 验签核心，严禁两套验签实现漂移 |
| 结果语义 | 验签通过 / 失败明确；失败不静默；同一报文重复验签结果稳定、模块内部无副作用（R-07） |
| 官方依据 | 各渠道以 IJPay 对应构件与渠道 SDK 的源码 / JavaDoc 逐字核对，并在固定向量用例中固化；支付宝 V3 既有核对结论继续有效 |

### 8.2 证书 / 公钥轮换（R-06）

| 项 | 约定 |
| --- | --- |
| 并存 | 配置允许同一 appId 下多组验签凭据（公钥 / 证书 + 序列号）同时生效 |
| 选取 | 按签名方式 / 报文携带的证书序列号匹配凭据；匹配不到即验签失败 |
| 轮换动作 | 新增新凭据 → 新旧并存 → 移除旧凭据，全程不改代码、不需停机（配置刷新机制由设计定档） |
| 安全 | 私钥与凭据不明文出现在日志 / 异常消息 |

### 8.3 幂等职责边界（R-07）

| 项 | 约定 |
| --- | --- |
| 模块职责 | **不做**通知去重、不做「已处理通知」缓存、不做订单状态机 |
| 透出能力 | 从回调报文解析并透出 `out_trade_no`、交易号、退款单号等供业务幂等键使用（公共值对象字段） |
| 重复语义 | 同一报文重复调用验签结果一致、模块内部无副作用；业务侧按幂等键前置校验（对齐 `PayNotifyUtil` 既有契约） |
| 告知义务 | `README.md` 显式声明「模块不做幂等、业务必须自行按幂等键去重」 |

### 8.4 沙箱 / 生产切换（R-08）

| 项 | 约定 |
| --- | --- |
| 切换方式 | 通过配置指定环境（网关 URL + 凭据组显式管理，**已裁决 2026-11-26**，不依赖 IJPay 内建开关）；不改业务代码 |
| 隔离 | 沙箱与生产的 appId / 凭据 / 网关互不串用；以沙箱凭据误调生产网关应被配置校验拦截或明确失败（**可机械验证的边界见 AC-15**：仅校验 `env` 必填、`gateway-url` 的 host 与 `env` 一致、不同 appId 网关互不串扰；凭据本身归属哪个环境无法机械判定，由配置评审与凭据来源管理兜底、README 声明） |
| 用途 | 自动化测试在沙箱完成、线上用生产凭据（具体键名由设计定档） |

## 九、验收标准（AC，门禁输入）

> 每条均可机械验证：能被单测、门禁断言、`mvn dependency:tree` / `javap` 固定判定。禁止「体验良好」等不可测措辞。AC 与 P0 需求一一映射；`G-xx` 为架构门禁断言（§10）。

| AC 编号 | 对应需求 | 判定条件（Given / When / Then，可度量） | 验证方式 |
| --- | --- | --- | --- |
| `AC-01` | R-01 装配 | Given 合法多通道配置，When `start()` 后 `@Inject PayClient`，Then 非 null 且为默认通道；`PayKit.defaultClient()` 与之同一实例；Given 已停止，When 调门面，Then 抛 `PayException`；重复 `start()` / `stop()` 幂等 | 集成测试（容器装配 + 静态引用） |
| `AC-02` | R-11 / §7.2 Java 8 | Given 构建产物，When `javap -verbose` 逐类检查，Then 主 JAR 全部 class major == 52；Given `mvn dependency:tree` 后的依赖 classpath，When 逐 JAR 检查，Then 依赖 jar 中「对 Java 8 可加载的」全部 `.class` major ≤ 52；`module-info.class` 与 `META-INF/versions/**` 不纳入判定 | `javap` 批量断言 + `dependency:tree` + 门禁 |
| `AC-03` | R-11 公共面 | Given 公共类型扫描，When 枚举根包 / `spi`，Then 集合精确等于 `design.md` 定稿名单，且**不存在任何 `AliPay*` 公共类型** | 门禁 `G-01` + 反射 |
| `AC-04` | R-11 / §7.3 零第三方类型 | Given 公共 API 全量签名，When 审查，Then 零 IJPay / 渠道 SDK / `JSONObject` / `cn.aifei.*` 类型；源码仅 `internal` 适配层可 import 第三方 | 门禁 `G-02` + 源码扫描 |
| `AC-05` | R-01 配置非法 | Given 必填项缺失 / 非法 `type`，When `start()`，Then 抛 `PayException`，消息含完整键名且不含凭据明文；失败无半装配态 | 单测 |
| `AC-06` | R-02 下单 | Given 合法下单参数，When 调 `PayClient` 下单动作，Then 返回公共值对象含拉起 / 跳转载荷；支付宝扫码 / H5 / PC 与微信 JSAPI / Native 按适配器逐个断言 | 单测（伪内核）+ 沙箱集成（可选） |
| `AC-07` | R-02 参数校验 | Given 空单号 / 金额 ≤ 0 / 缺回调地址，When 调用，Then 抛 `IllegalArgumentException` 或 `PayException`（口径设计定档，唯一） | 单测 |
| `AC-08` | R-03 查单 | Given 存在订单，When 查单，Then 返回公共值对象含状态 / 金额 / 渠道交易号；不存在口径由适配器收敛并固定 | 单测 |
| `AC-09` | R-04 退款 | Given 订单号 + 退款单号 + 合法金额，When 退款，Then 返回退款值对象；同一退款单号重复提交，断言幂等键原样透传、模块不去重 | 单测 |
| `AC-10` | R-05 验签固定向量 | Given 支付宝普通公钥签名报文，When `PayNotifyUtil.verify`，Then true；篡改后 false 且不静默 | 单测（固定向量） |
| `AC-11` | R-05 凭据轮换 | Given 同一通道新旧两组凭据，When 分别验签，Then 两者均通过；仅配置新凭据时旧报文失败 | 单测 |
| `AC-12` | R-05 报文入参 | Given 参数 Map / urlencoded body / header+body 三类 `PayNotifyRequest`，When 验签，Then 各渠道按唯一解析路径通过 | 单测（固定向量） |
| `AC-13` | R-05 证书模式 | Given 支付宝公钥证书与匹配序列号，When 验签，Then true；序列号不匹配则 false | 单测 |
| `AC-14` | R-07 幂等边界 | Given 同一报文连续验签 N 次，When 比较结果，Then N 次一致、模块内无去重缓存；`PayNotify` 可读 `outTradeNo` 等幂等键 | 单测 + 源码扫描 |
| `AC-15` | R-08 环境切换 | Given 沙箱 / 生产配置，When 调用，Then 指向各自网关；env 必填、gateway host 与 env 一致、不同商户网关互不串扰；凭据归属环境不可机械判定，由 README 声明 | 单测（伪内核断言） |
| `AC-16` | R-09 多商户 / 应用 | Given 两个商户标识各自配置，When 显式指定调用，Then 配置互不串扰；未配置标识抛 `PayException` | 单测 |
| `AC-17` | R-10 依赖缺失 | Given classpath 无所需 IJPay 通道构件，When 首次调用该通道，Then 抛 `PayException`，消息含缺失坐标，无裸错误 | 单测（隔离 classpath） |
| `AC-18` | R-12 异常收敛 | Given 内核原始异常，When 经门面调用，Then 调用方收到 `PayException`（含 cause），无 IJPay / SDK 原始异常外泄 | 单测 |
| `AC-19` | §7.2 依赖 scope | Given `mvn dependency:tree`，Then `aifei-all` 为 `provided`，各 IJPay / 渠道 SDK 为 `optional`（或已登记例外），无未声明传递 | `dependency:tree` + 根例外核对 |
| `AC-20` | §3.2 零 DB / 渠道白名单 | Given 源码扫描，Then 无 `import java.sql.`；`com.ijpay.*` 仅出现在 `internal` 适配层；未在 `PayTypes` 白名单登记的渠道包零命中 | 门禁 `G-03` + `dependency:tree` |
| `AC-21` | §7.1 依赖内核 | Given 依赖树与源码，Then IJPay 以 Maven 构件引入、无源码复制 / fork；README / design 含 Apache-2.0 署名 | 人工审查 + 门禁 |
| `AC-22` | 构建 | Given 模块目录，When `mvn clean verify`，Then 0 failure / 0 error | 构建 |
| `AC-23` | R-13 多通道聚合 | Given 配置 `alipay` 与 `wxpay` 两个通道，When `PayKit.use("alipay")` / `use("wxpay")` / `defaultClient()`，Then 返回对应且互不相同的实例；未知通道抛 `PayException` | 单测 + 装配测试 |
| `AC-24` | R-15 双模型共用核心 | Given 同一合法回调向量，When 分别经 `PayNotifyUtil.verifyAndParse` 与 `PayHandler` 处理，Then 验签结果一致且源码仅有一个验签核心实现点 | 单测 + 源码扫描 |
| `AC-25` | R-15 Handler 闭环 | Given 合法报文与已注册 `PayNotifyListener`，When 请求进入 `PayHandler`，Then 监听器收到一次 `PayNotify` 且响应体为渠道成功应答；Given 篡改报文，Then 监听器零调用且响应为渠道失败应答 | Handler 单测（伪 `PayIo`） |
| `AC-26` | R-14 微信通道 | Given 配置 `wxpay` 通道，When 装配与调用，Then 使用 `IJPay-WxPay` 适配器；模块源码零 `cn.aifei.weixin` import；与 `aifei-weixin` 可并存 | 门禁 `G-03` + 单测 |
| `AC-27` | R-13 白名单 | Given `pay.<name>.type` 为未登记值，When `start()`，Then 启动失败并列出合法类型；已登记但未实装的渠道明确提示「结构支持、适配器未落地」 | 单测 |

**AC 与门禁映射**：`AC-02`（部分）/ `AC-03` / `AC-04` / `AC-20` / `AC-26` 由架构门禁 `ArchitectureBoundaryTest` 承载；`AC-19` 由 `dependency:tree` 与根例外登记核对承载；其余由单测 / 集成用例承载。用例清单写入 `docs/testing.md`，交付门禁按「AC 编号 ↔ 用例 ↔ 结果」三列映射。

## 十、非功能要求与硬约束

### 10.1 非功能要求

- **性能**：默认（支付调用延迟由渠道网关决定，模块不做吞吐 KPI；签名 / 验签为本地计算）。
- **安全**：私钥与凭据不落日志 / 异常明文；凭据不明文提交（仓库安全纪律）；回调验签失败必须能被消费方拒绝处理。
- **兼容**：Java 8（语法 + API 双约束，class major 52）；IJPay 3.x 自身基线为 Java 8 / class 52；各渠道及传递依赖「对 Java 8 可加载的 class」字节码 ≤ 52（`AC-02`；`module-info.class` 与 `META-INF/versions/**` 不纳入）；`aifei-all` `provided`。

### 10.2 硬约束（设计不得推翻）

| 项 | 要求 |
| --- | --- |
| Java 8 | `source` / `target` = 1.8；无 `var` / `record` / `switch` 表达式 / text block / `List.of` / `Map.of`；主 JAR 全部 class major 52 |
| 公共 API 实现无关 | 根包与 `spi` 公共签名零 IJPay / 渠道 SDK / `JSONObject` / `cn.aifei.*` 类型（§7.3） |
| 无兼容层 | **删除全部 `AliPay*` 公共类型**；不得保留过渡别名或双份契约（D-10） |
| 依赖 | IJPay 各通道构件与渠道 SDK `optional`；`aifei-all` `provided`；不引任何日志绑定；scope 例外须先在根 `AGENTS.md` 登记 |
| 零 DB | `src/main/java` 禁 `import java.sql.`；不依赖 `aifei-db` |
| 依赖内核不复制 | Maven 构件引入 IJPay，禁止源码复制 / fork（§7.1） |
| 渠道边界 | `com.ijpay.*` 仅允许出现在 `internal` 适配层；仅 `PayTypes` 白名单渠道可装配；零 `cn.aifei.weixin` import |
| 唯一定义点 | 配置键 / 凭据选取 / 验签核心 / 异常构造 / 值对象转换 / 通道注册表各只有一个定义点，实现类不得复制 |
| 文档四件套 | `README.md`（用法 + 边界 + 依赖来源声明）、`docs/design.md`（契约事实来源 + 公共面定稿 + 差异决策）、`docs/testing.md`（用例清单 + AC 映射）、模块 `AGENTS.md`（开发约束） |
| 构建 | 模块目录内 `mvn clean verify` 0 failure / 0 error |

### 10.3 架构门禁断言清单（`ArchitectureBoundaryTest`，源码文本扫描，模块根目录执行，不得放宽）

| 编号 | 断言 |
| --- | --- |
| `G-01` | 公共类型集合精确等于 `design.md` 定稿名单：根包含 `PayClient` / `PayKit` / `PayNotifyUtil` / `PayException` 等，且**不存在任何 `AliPay*` 公共类型**；`spi` 含 `PayNotify` / `PayNotifyListener` |
| `G-02` | 公共 API 签名零第三方 / 零框架类型：根包与 `spi` 不 import `com.ijpay.*` / 渠道 SDK / `com.alibaba.fastjson*` / `cn.aifei.*` |
| `G-03` | `src/main/java` 无 `import java.sql.`；`com.ijpay.*` 只允许在 `internal` 适配层；未在 `PayTypes` 白名单登记的渠道包零命中；允许 `com.ijpay.wxpay.*` 出现在 `internal` 适配层，零 `cn.aifei.weixin` import |
| `G-04` | `cn.aifei.*` 仅出现在 `aifei` 接入包；IJPay / 渠道 SDK 类型仅出现在 `internal` 适配层 |
| `G-05` | 主 JAR 全部 class major == 52；源码无 Java 8+ 语法 / API（正则兜底，`javap` 为主防线） |
| `G-06` | 依赖树中各 IJPay / 渠道 SDK 为 `optional` 或已登记例外；无日志绑定 |

## 十一、待明确事项

> 原 Q1~Q8、V1.1 的「IJPay-WxPay 交引入者」以及 V2.0 的四项裁决已于 **2026-11-26** 全部裁决，结论固定至 §5 差异表（D-01~D-12）、§2 调研结论、§3、§6、§7、§8、§9（AC-01~AC-27）、§10、§13 等对应章节。本节只保留**真正未定档项**：① 假设（未获否决即执行）；② 设计期待核对项（设计期闭环）。

### 11.1 假设（未获否决即执行）

| # | 假设 | 影响面 |
| --- | --- | --- |
| A-1 | IJPay 3.0.0 的传递依赖经锁定后，「对 Java 8 可加载的 class」可全部 ≤ 52（F-10 显示官方已为此锁定 guava/dom4j/okhttp/jakarta.servlet-api 等版本上限，但该锁定写在父 pom 的 `dependencyManagement`、不向消费者传播；V3 实际依赖树为 okhttp 4.9.3 + kotlin-stdlib 1.4.10、gson 2.9.0、guava 28.1-jre、hutool 5.8.48、dom4j 2.1.5、bcprov-jdk18on 1.86。**本假设已于 2026-11-26 由设计关实测闭合**：在 AC-02 精化口径下 0 例外，实证见 `design.md` §2.3） | AC-02、§7.2（IJPay 3.0.0 自身基线已验证，见 §10.1） |
| A-4 | 多应用切换至少支持「显式指定 appId」（线程绑定形态作为可选增强，具体形态待设计定档） | R-09、AC-16 |

### 11.2 设计期待核对项（设计期闭环）

> 以下项须在 `docs/design.md` 定稿前以 IJPay 对应构件源码 / 渠道 SDK JavaDoc / Maven Central 实际 pom 逐字核对，并在对应 AC 用例向量中固化。

| # | 核对项 | 归属章节 | 闭环时机 |
| --- | --- | --- | --- |
| F-12 | `alipay-sdk-java-v3` 精确版本 `3.1.89.ALL`——**已闭环**（2026-11-26 实测，见 `design.md` §2.1.1） | §2.1 F-12、§7.2 | **已闭环** |
| AC-02 | V3 路线 `dom4j:dom4j` / `bcprov-jdk15on` 上游排除已闭环；**新增 IJPay 通道构件必须重复同一字节码验证** | §7.2、AC-02 | 设计期（每新增通道） |
| AC-10~12 | 支付宝 V3 验签四象限固定向量已闭环；**其余渠道验签向量须逐项补齐** | §8.1、AC-10~AC-12 | 设计期（每新增通道） |
| Q4 | 扩展点仅作知识核对，组合非继承已裁决 | §7.1 | 设计期 |
| AC-19 | 各 IJPay 通道 `optional` 落点（如需 `compile` 例外须先在根 `AGENTS.md` 登记） | §7.2、AC-19 | 设计期 |
| V2-1 | `IJPay-WxPay` 3.0.0 的实际构件坐标、传递依赖与 Java 8 基线须实测；未闭环前 `wxpay` 适配器不得宣称可用 | §7.2、AC-26 | 设计期 |
| V2-2 | `PayTypes` 白名单与 IJPay 官方模块清单逐项对齐，区分「结构支持」与「适配器已落地」 | §3.2-8、AC-27 | 设计期 |
| V2-3 | `PayNotifyRequest` 的 header / body / params 表达能否覆盖各渠道回调，须逐通道核对 | §8.1、AC-12 | 设计期 |

## 十二、边界与风险

| # | 风险 | 说明 | 缓解 / 需求侧动作 |
| --- | --- | --- | --- |
| K-1 | **依赖链与 Java 8 基线** | IJPay 传递依赖多（okhttp 4.9.3 + kotlin-stdlib 1.4.10、gson 2.9.0、guava 28.1-jre、hutool 5.8.48、dom4j、bcprov-jdk18on；V3 路线无 fastjson）；任一**对 Java 8 可加载的** class 字节码 > 52 即破坏仓库基线 | AC-02 对最终依赖树逐类断言（`module-info.class` 与 `META-INF/versions/**` 不纳入，见 AC-02 口径）；必要时版本锁定 / 排除（F-10 提供先例）；不通过则不落地 |
| K-2 | **公共面被第三方类型污染** | IJPay / 支付宝 SDK / `JSONObject` 类型若进入公共签名，违反「零第三方类型」门禁 | §7.3 隔离约束 + AC-04 + `G-02`；**已裁决（2026-11-26）：公共返回用自有值对象**，风险已闭环至设计期定档 |
| K-3 | **依赖 scope 与传递** | 若内核须随 SDK 传递，`optional` 会让消费方被迫重复声明；`compile` 又须登记例外 | **已裁决（2026-11-26）：依赖 `optional`**；例外须先在根 `AGENTS.md` 登记（D-03） |
| K-4 | **IJPay 版本 / 能力变动** | IJPay 活跃迭代（F-05 版本从 2.9.x 到 3.0.0），API 可能对内实现变动 | 锁定版本 + 公共面隔离（§7.3），使升级不波及契约 |
| K-5 | **回调验签细节未逐字验证** | 证书模式验签、同步/异步差异在官方文档页未展开（F-08 部分验证）；V3 路线验签方法差异为 2026-11-26 裁决后新增核对项 | 设计期读 IJPay-AliPay-V3 源码 / alipay-sdk-java-v3 JavaDoc + 固定向量用例（AC-10~12）闭环 |
| K-6 | **与微信支付重复建设** | IJPay 自带微信能力，本模块 `wxpay` 与 `aifei-weixin` 能力重叠 | 用户裁决：两者并存、二选一，模块间零 import（§7.4 / §7.4.1 / D-08）；门禁 `G-03` 断言零 `cn.aifei.weixin` import，IJPay 类型仅在适配层 |
| K-7 | **证书 / 私钥泄露** | 凭据配置若被打入日志 / 异常即安全问题 | §6.2 安全约束 + AC-05「消息不含私钥明文」 |
| K-8 | **工期变量** | 依赖 IJPay 后工期可能变化（D-06） | 需求侧不承诺工期，交交付负责人重估 |

## 十三、入口与交互要点

本模块**无自有 UI 页面**，需交付的「入口」清单如下：

| # | 入口 | 使用者 | 关键交互 | 归属 |
| --- | --- | --- | --- | --- |
| 1 | 下单入口 | 业务 Service | 商户单号 / 金额 / 标题 / 回调地址 → 拉起参数 / 跳转 URL 值对象 | 模块公共 API（`PayClient`） |
| 2 | 查单入口 | 业务 Service | 商户订单号 → 交易状态值对象 | 模块公共 API |
| 3 | 退款入口 | 业务 Service | 订单号 + 退款单号 + 金额 → 退款结果值对象 | 模块公共 API |
| 4 | 回调验签入口 | 消费方 Handler / Just Service | `PayNotifyRequest` → 验签结果与 `PayNotify` 幂等键值对象 | 模块公共 API（`PayNotifyUtil`） |
| 5 | 回调闭环入口 | 应用 `AppConfig` | 注册 `PayHandler` 与 `PayNotifyListener` → 验签通过自动分发 | 模块接入构件 + `spi` |
| 6 | 装配入口 | 应用 `AppConfig` | 装载多通道配置、校验、`@Inject PayClient`、默认通道 | 模块接入构件（`PayPlugin`） |
| 7 | 命名通道入口 | 业务 Service | `PayKit.use(name)` / `PayKit.channels()` | 模块公共 API（`PayKit`） |
| 8 | 配置刷新入口（轮换） | 运维 / 配置中心 | 新增 / 移除验签凭据，新旧并存 | 模块公共 API（机制设计定档） |
| 9 | ~~demo 演示页~~（**已裁决不配，无此项**，2026-11-26 用户裁决，详见 §6.3） | — | — | — |

## 十四、需求关 DoD 自检

| # | 检查项 | 结论 |
| --- | --- | --- |
| 1 | 目标可一句话说清 | **通过**：`aifei-pay` 基于 IJPay 提供一套 `PayClient` 聚合全支付通道，换通道只改配置；支付宝与微信本期完整迁移，其余渠道按适配器逐项补齐。 |
| 2 | 每条 P0 都有可测 AC | **通过**：`R-01`~`R-15` 共 15 条 P0 映射到 §9 共 **27** 条 AC（`AC-01`~`AC-27`），涵盖多通道注册、微信通道、回调双模型与共用验签核心。 |
| 3 | 本期不做清单明确 | **通过**：§3.2 共 8 条非目标，明确零 DB、零 UI、不复制 IJPay、不实装 Servlet 适配、全渠道不一次性实装等边界。 |
| 4 | 假设与待裁决显式标注 | **通过**：V2.0 四项裁决固定于开篇与 §5 D-09~D-12；§11 仅保留 2 条假设与 8 项设计期待核对项（含微信依赖、渠道白名单、统一回调入参）。 |
| 5 | 需求变更显式记录 | **通过**：开篇【需求变更声明 · V2.0】+ §5 差异表（D-01~D-12）显式记录从「支付宝单通道」到「IJPay 多渠道聚合」的定位变更、微信归属、无兼容层与回调双模型裁决。**V2.0 修订逐条留痕见「修订记录」**。 |

## 修订记录

| 版本 | 日期 | 修订项 | 变动内容（原文口径 → 本版口径） | 依据 | 影响面 |
| --- | --- | --- | --- | --- | --- |
| **V2.0** | 2026-11-26 | 模块定位与结构纠偏（用户四项裁决） | V1.1「支付宝单通道门面、不做其它渠道、不做多网关抽象、不引 IJPay-WxPay、仅 `AliPayNotifyUtil` 验签」→ V2.0「基于 IJPay 的多支付通道聚合 SDK，结构对齐 `aifei-sms`；全渠道聚合目标、本模块保留 `wxpay` 由用户二选一、不保留 `AliPay*` 兼容层、`PayNotifyUtil.verify` 与 `PayHandler`+`PayNotifyListener` 共存且共用验签核心」 | 用户 2026-11-26 复核裁决 | 开篇变更声明；§1/§2.2/§3/§4/§5 D-04~D-12/§6 R-01~R-15/§7/§8.1/§9 AC-01~AC-27/§10/§11/§12 K-6/§13/§14 全量同步 |
| **V1.1** | 2026-11-26 | ① AC-02 字节码判定口径精化（原为交付阻塞项） | 原文「IJPay-AliPay-V3 及全部传递依赖无字节码 > 52 者」按字面**永不可达且无修复路径**（`javax.ws.rs-api:2.1.1` 根级 `module-info.class` major 53 不可排除；`bcprov-jdk18on:1.86` 的 `META-INF/versions/11/**` 有 112 个 major 55 的 class，属 BC 官方 Multi-Release 发布形态）→ 改为「依赖 jar 中**对 Java 8 可加载的**全部 `.class` major ≤ 52；`module-info.class` 与 `META-INF/versions/**` 不纳入判定，理由：Java 8 不解析 JPMS 描述符、不识别 Multi-Release 目录，运行期不可达」；主 JAR 断言不放宽（仍全部 == 52） | `aifei-pay/docs/design.md` §2.3（`javap` 逐 JAR 实测，精化口径下 0 例外） | §9 AC-02（仅改判定条件，验证方式沿用 `javap` 批量断言 + `dependency:tree` + 门禁）；连带 §7.2、§10.1、§11.1 A-1、§11.2 AC-02 行、§12 K-1 口径同步 |
| **V1.1** | 2026-11-26 | ② AC-15 可机械验证边界收口 | 原文「沙箱凭据不得误调生产网关」未界定可验证范围 → 补「可机械验证的边界」：模块只能校验 ① `env` 必填；② `gateway-url` 的 host 与 `env` 一致（沙箱/生产两个官方网关）；③ 不同 appId 的网关互不串扰；**「凭据本身归属哪个环境」明确排除在可验证范围外**，由配置评审与凭据来源管理兜底、README 显式声明 | `aifei-pay/docs/design.md` §14.1 A-3 / §14.3 R-1 | §9 AC-15（仅追加边界说明，原 GWT 判定与验证方式不变） |
| **V1.1** | 2026-11-26 | ③ D-02 补记值对象归属形态与数量 | 原文「公共返回用自有值对象、数量待定档」→ 追加「`model` 子包为该授权范围内的第三种归属形态（根包直接子类型 / 嵌套只读结构 / `model` 子包），精确 4 个值对象」，状态列由「数量待定档」更新为「数量已定档」 | `aifei-pay/docs/design.md` §14.1 A-2 / §6.5（`cn.hg.aifei.pay.model` 精确 4 类型） | §5 D-02（仅追加授权界定，D-02 原语义不变） |
| **V1.1** | 2026-11-26 | ④ §2 依赖事实订正（架构师实测与原文表述不一致处） | `okhttp` 4.12.0 → **4.9.3**；`kotlin-stdlib` → **1.4.10**；`guava` 33.6.0-jre → **28.1-jre**（原因：IJPay 父 pom 的 `dependencyManagement` 不向消费者传播）；**V3 路线无 fastjson** → 实际 `gson 2.9.0`；补记额外传递依赖 `hutool 5.8.48` 四件与 `commons-logging:1.1.1`（仅被 `AlipayLogger` 使用）；`alipay-sdk-java-v3` 精确版本定档 **3.1.89.ALL**；`dom4j:dom4j` / `bcprov-jdk15on` 已由**上游 IJPay pom 排除、本模块无需重复排除**，实际生效 `dom4j:2.1.5` / `bcprov-jdk18on:1.86` | `aifei-pay/docs/design.md` §2.1（pom 逐字）/ §2.2（依赖树逐行 32 JAR） | §2.1 F-10 / F-11 / F-12；连带 §7.2、§11.1 A-1、§11.2 F-12 行、§12 K-1 |

> 修订边界声明：V1.1 仅做口径回写与事实订正，未改变业务规则；V2.0 是**定位与结构的破坏性变更**，`R-01`~`R-12` 语义改写为多渠道口径并追加 `R-13`~`R-15`，`AC-01`~`AC-22` 编号保留并改写语义、追加 `AC-23`~`AC-27`；V1.1 的依赖事实与字节码判定口径继续有效。
