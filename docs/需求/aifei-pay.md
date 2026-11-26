# aifei-pay 模块需求文档（PRD + 验收标准）

> **文件定位**：本文是 `aifei-pay` 的**需求事实来源**（背景、范围、用户故事、功能需求、验收标准、需求变更点、待明确事项）。公共 API 逐字签名、包结构、配置键名、依赖 scope 的最终定档、公共面类型数量，属设计内容，由 `aifei-pay/docs/design.md` 定稿；本文只声明「必须支持什么、依赖策略的边界、如何验收」。
> **关联**：`docs/计划/aifei-eco-modules 模块规划与排期.md`（§2.3 能力缺口、§3.2 第二档定位、§4.3 `aifei-weixin → aifei-pay` 契约级关联、§5.2 工期）；同构范本 `docs/需求/aifei-http.md`、`docs/需求/aifei-tools.md`、`docs/需求/aifei-audit.md`。
> **坐标与基线**：`io.github.gollyhu:aifei-pay:1.0.0-SNAPSHOT`；包名 `cn.hg.aifei.pay`；Java 8（class major 52）；`cn.aifei:aifei-all` 为 `provided`。
> **事实来源与调研日期**：IJPay 事实于 2026-11-26 经官方仓库 / 官方文档 / 官方 Gitee Pages 调研，逐条标注来源 URL 与验证状态（见 §2）；规划基线口径截至 2026-11-20。
> **文档版本**：**V1.1**（2026-11-26。V1.0 → V1.1 为设计关裁决结论的需求侧口径回写与依赖事实订正，逐条留痕见文末「修订记录」）
>
> **【需求变更声明 · 先读】** 本模块在规划文档 §3.2 中的原依赖策略为「**纯 JDK 签名，零第三方**」。用户本次明确要求「**基于 IJPay（https://gitee.com/javen205/IJPay）扩展**」，这是一项**依赖策略变更**，不是对原规划的简单细化。变更及其连带影响已在 §5 差异表显式列出；原 §11 待裁决事项（Q1~Q8 + IJPay-WxPay 衍生裁决）已由用户于 **2026-11-26** 全部裁决，结论已固定至对应章节（裁决要点见下），§11 不再保留裁决留痕表。裁决要点：① 用户原文链接「IJPayj」按笔误处理，指向 `https://gitee.com/javen205/IJPay`（§2.2）；② 路线采用 **IJPay-AliPay-V3（支付宝 V3 SDK）**，不再是 V2（D-01 / §6.4 / §7.1）；③ 依赖 `optional`（D-03 / §7.2）；④ 包装（组合）非继承（§7.1）；⑤「复用 PayApi 契约」仅对齐动作语义与错误契约、公共返回用自有值对象（D-02 / D-05 / §7.3 / §7.4）；⑥ 默认签名方式公钥证书（§6.2）；⑦ 以配置显式管理网关与凭据（§6.2 / §8.4 / AC-15）；⑧ 不配 demo（§6.3 / §13）。

## 〇、交付物性质声明（门禁口径依赖此节）

**本次交付物是库模块（可被应用依赖的 SDK），不是业务应用工程。** 三份常规章节在本模块不适用：

| 章节 | 结论 | 理由 |
| --- | --- | --- |
| 表结构草案 | **不适用** | 本模块是支付通道 SDK，零 DB、零表、零 DAO、运行期不连数据源；门禁反向断言主源码 `src/main/java` 禁止 `import java.sql.`。交易数据落在消费方自己的业务库，不属于本模块交付物。**不得为凑齐模板而编造表。** |
| Service 方法清单 | **不适用** | 模块本体无 `@Path` / Service（那是消费方应用的事）。根包公共面类型清单为**预估**（见 §7.3），须过设计决策、由架构师在 `design.md` 定稿；本文**不代写逐字签名**。 |
| 低保真 UI 原型 | **不适用** | 本模块无自有 UI 页面；回调入口由消费方 Just Service / Handler 承载（见 §10）。是否配 `aifei-pay-demo` 已裁决不配（2026-11-26 用户裁决，详见 §6.3 / §13）。 |

## 一、背景与目标

### 1.1 缺口

规划文档 §2.3 将「支付宝支付」列为第三方通道缺口，§3.2 把 `aifei-pay` 定位为第二档「通道接入型」，范式已由 `aifei-weixin` 验证。本仓库现有微信支付 V3 能力在 `aifei-weixin` 的 `cn.aifei.weixin.pay` 包（`PayApi` 下单/查单/关单/退款、`PayNotifyUtil` 验签与解密，纯 JDK 实现），**不含支付宝**。业务若接支付宝，需自行处理签名/验签、公钥与证书管理、多商户多应用切换、回调报文解析等大量样板，且各应用各写一套、口径漂移。

### 1.2 目标

在 `aifei` 生态中提供**支付宝支付的统一接入门面**：以 IJPay 的支付宝内核为依赖底座，把「配置 → 下单 / 查单 / 退款 → 回调验签」收口为稳定、可验收的公共契约，与 `aifei-weixin` 支付侧的动作语义对齐，便于业务在两侧切换。

- **给谁用**：Java 8 + aifei 的应用后端开发者（需要支付宝收款、对账、退款的业务）。
- **关键定性**：IJPay 是**依赖内核**（引入其 Maven 构件、调用其公开 API），**不是复制代码、不是 fork 移植**（§7.1 硬约束）。

### 1.3 成功标准

1. 消费方不自行实现签名/验签与证书管理样板，即可完成支付宝下单、查单、退款与回调验签。
2. 公共 API **零第三方类型、零框架类型**：IJPay 与支付宝 SDK 的响应 / 模型类型不出现在公共签名中（§7.3）。
3. 依赖策略满足仓库纪律：IJPay 及支付宝 SDK 为 `optional`，`aifei-all` 为 `provided`；缺失依赖时给可读异常而非 `NoClassDefFoundError`。
4. 回调验签、证书/公钥轮换、幂等职责边界、沙箱/生产切换均有可机械验证的验收标准（§9）。
5. 与 `aifei-weixin` 支付能力分工清晰，不重复建设微信支付（§7.4）。

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

- **IJPay 自身含微信支付（IJPay-WxPay），本模块默认不引入、由消费方自行选择其一**：避免与 `aifei-weixin` 已有微信支付 V3 重复；本模块公共面 / 门禁不覆盖 IJPay-WxPay（详见 §7.4.1，2026-11-26 用户裁决）。
- **IJPay 含十余个渠道模块，本模块只取支付宝侧**：不做「全渠道支付 SDK」扩张（§3.2）。
- 用户原文链接「IJPayj」按笔误处理，指向 `https://gitee.com/javen205/IJPay`（**2026-11-26 用户裁决确认**）。

## 三、范围

### 3.1 本期做（P0 全部，详见 §6）

- 支付宝**下单**（至少覆盖当面付扫码 `alipay.trade.precreate`、手机网站/H5 `alipay.trade.wap.pay`、电脑网站/PC `alipay.trade.page.pay`；其余 app/gateway 接口见 P1）。
- 支付宝**查单**（`alipay.trade.query`）。
- 支付宝**退款**（`alipay.trade.refund`；退款查询 `fastpay.refund.query` 见 P1）。
- 支付宝**回调验签**（同步 `return_url` 与异步 `notify_url` 两类，普通公钥与公钥证书两种签名方式）。
- 依赖 IJPay 支付宝内核（作为 `optional` 依赖），本模块提供 aifei 侧的门面、配置装配、回调入口适配、公共值对象隔离。
- 公共面精确类型集合由设计关定档（预估见 §7.3），公共签名零第三方 / 零框架类型。
- 缺失 IJPay 依赖时可读异常；配置非法启动即失败；回调验签 / 证书轮换 / 幂等边界 / 环境切换的可验收语义。

### 3.2 本期不做（非目标，防范围蔓延）

1. **微信支付能力**：本模块默认**不引入** `IJPay-WxPay`、不为其提供门面；由 `aifei-weixin` 负责微信支付（§7.4）。消费方若已基于 IJPay 整体接入多渠道，可自行引入 IJPay-WxPay，但本模块公共面 / 门禁不覆盖它（**2026-11-26 用户裁决**，见 §7.4.1）。
2. **IJPay 其余渠道**（QQ / 京东 / 银联 / PayPal / Stripe / 富友 / XPay）：本模块只做支付宝。
3. **业务幂等去重与订单状态机**：模块不做「同一通知去重 / 已处理缓存」；`out_trade_no` / 退款单号等幂等键透出给业务，由业务侧前置校验（对齐 `aifei-weixin` 的 `PayNotifyUtil` 契约，§8.3）。
4. **业务订单落库与对账**：交易记录、账单核对、财务报表由消费方实现；模块零 DB。
5. **支付页面 UI / 收银台**：模块只产出支付参数与跳转 URL；页面渲染由消费方负责。
6. **复制 / fork IJPay 源码**：IJPay 是依赖内核，禁止把其源码拷进本仓库（§7.1）。
7. **Servlet 适配层**：不引入 `IJPay-Servlet-Javax` / `IJPay-Servlet-Jakarta`；回调报文以字符串 / Map 入参交给模块，容器适配由消费方处理（aifei 侧走 Handler）。
8. **provider 扩展点 / 多支付网关抽象**：本版单内核（IJPay 支付宝），不设 SPI 与后端切换开关。
9. **回调送达可靠性**：不保证支付宝通知必达、不做主动补单轮询（属业务职责）。
10. **多租户感知**：notify_url 的租户段解析由消费方负责（对齐 `PayNotifyUtil` 契约）。

## 四、用户故事

- 作为**业务后端开发者**，我希望用一组稳定的 `AliPayClient` 方法完成支付宝下单 / 查单 / 退款，以便不直接接触 IJPay 与支付宝 SDK 的类型和样板。
- 作为**业务后端开发者**，我希望回调入口一行完成验签并返回可供业务判断的结果对象，以便不必自己处理公钥 / 证书与报文解析。
- 作为**接入方架构师**，我希望 IJPay 与支付宝 SDK 以 `optional` 方式依赖、公共 API 不含第三方类型，以便保留仓库的依赖纪律与升级自由。
- 作为**运维 / 安全负责人**，我希望公私钥与证书可配置化并在轮换期支持新旧并存，以便证书更新不需改代码、不停机。
- 作为**业务开发者**，我希望同一套动作语义在微信支付（`aifei-weixin`）与支付宝（`aifei-pay`）之间一致，以便业务在两侧切换时改动最小。
- 作为**测试工程师**，我希望沙箱与生产配置可切换且互不串用，以便自动化测试在沙箱完成、线上用生产凭据。
- 作为**模块维护者**，我希望IJPay 类型被隔离在内部实现层，以便升级 IJPay 版本不波及公共契约。

## 五、与原规划的差异表（需求变更点）

> 本节是本次需求相对于 `docs/计划/aifei-eco-modules 模块规划与排期.md` §3.2 / §4.3 / §5.2 的**显式变更清单**。凡标「待裁决」者，未获用户确认前不得在设计中默默采用。

| # | 维度 | 原规划（截至 2026-11-20） | 本次要求 / 变更 | 影响 | 状态 |
| --- | --- | --- | --- | --- | --- |
| D-01 | 依赖策略 | 「纯 JDK 签名，零第三方」（§3.2） | 「基于 IJPay 扩展」→ 依赖 **IJPay-AliPay-V3**（支付宝 V3 SDK）内核（含 alipay-sdk-java-v3 等传递依赖） | 根本性变更；V2 路线本期不纳入（§6.4）；scope 与隔离口径见 §7.2 | **已裁决（2026-11-26 用户裁决）** |
| D-02 | 公共面预估 | `AliPayClient` / `AliPayKit` / `AliPayNotifyUtil` / `AliPayException`（§3.2） | 保留名称预估，但须叠加「公共签名零第三方类型」约束：IJPay / 支付宝 SDK 的 Response / Model 不得进公共签名；公共返回用**自有值对象**（不引入 `JSONObject`）。`model` 子包为该授权范围内的**第三种归属形态**（根包直接子类型 / 嵌套只读结构 / `model` 子包），**精确 4 个值对象**，于 2026-11-26 确认 | 公共面需增设本模块自有值对象；类型数量由设计关定档 | **已裁决**：公共返回用自有值对象（2026-11-26 用户裁决，详见 §7.3）；数量已定档（4 个值对象，归属 `model` 子包，2026-11-26） |
| D-03 | 依赖 scope | 零第三方（无此议题） | IJPay 及支付宝 SDK **`optional`**；`aifei-all` `provided`；缺失时可读异常 | 参照 `aifei-excel` / `aifei-tools` 的 optional 内核先例 | **已裁决（2026-11-26 用户裁决，详见 §7.2）；scope 具体落点待设计定档** |
| D-04 | 能力范围 | 「支付宝支付：下单/查单/退款/回调验签」（§3.2） | 不变；但 IJPay 支付宝还有转账/红包/花呗等能力，本期不纳 | 范围守护，见 §3.2 | **无实质变更** |
| D-05 | 与微信分工 | 「`aifei-weixin` → `aifei-pay` 复用 `PayApi` 契约」（§4.3，未立项） | IJPay 自带微信能力但本模块默认不引入；「复用 PayApi 契约」解读为**仅对齐动作语义与错误契约**，公共返回用自有值对象（§7.4） | 避免与 `aifei-weixin` 重复建设；不产生模块间代码依赖 | **已裁决（2026-11-26 用户裁决，详见 §7.4）** |
| D-06 | 工期 | 3 周（12/21–01/08，跨元旦，§5.2） | 依赖 IJPay 后可能缩短（内核能力复用），但新增公共面隔离与依赖链验证工作；工期重估属交付负责人职责 | 需求侧不承诺工期，仅提示变量 | **不在本文定档** |
| D-07 | IJPay 授权 | 原规划未涉及 | IJPay 为 Apache-2.0（F-02），本模块引入依赖需保留其许可与署名说明 | 合规留痕；README / design 须声明来源与许可 | **需求侧约束已定**（§7.1） |
| D-08 | IJPay-WxPay 口径 | 原 PRD 视作「硬禁 IJPay-WxPay」（§3.2-1 / G-03 / AC-20） | **放宽为「默认不引入、由消费方自行选择其一」**；本模块不为 IJPay-WxPay 提供门面、不纳入公共面与门禁守护；门禁断言调整为「本模块源码不引入 IJPay-WxPay」（消费方自带不在门禁范围） | §3.2-1 / §7.4.1 / §10.3 G-03 / AC-20 同步调整 | **口径变更（2026-11-26 用户裁决，详见 §7.4.1）** |

## 六、功能需求

需求池分级：**P0（本期必须，缺了不算交付）** 全部映射到 §9 AC；P1 / P2 见 §6.4。编号固定，只允许在其后追加，不得重排或改号。

### 6.1 P0 需求清单

| 编号 | 需求 | 说明 |
| --- | --- | --- |
| `R-01` | **配置装配与门面** | 提供装配入口（形态与 `HttpPlugin` / `AuditPlugin` 同构，配置前缀 `pay.*`，实际键名由 `design.md` 定稿）；完成支付宝应用配置（appId、私钥、支付宝公钥 / 证书、网关环境等）的装载与校验；`@Inject AliPayClient` 取用；配置非法**启动即失败**（消息含键名与敏感值脱敏后的提示）；`AliPayKit` 为有状态门面；未装配 / 已停止时调用即失败。 |
| `R-02` | **下单** | 至少支持当面付扫码（`alipay.trade.precreate`）、H5（`alipay.trade.wap.pay`）、PC（`alipay.trade.page.pay`）三类；入参含商户订单号、金额、标题、回调地址；返回可供消费方拉起支付 / 跳转的公共值对象（不含第三方类型）。 |
| `R-03` | **查单** | 按商户订单号（及支付宝交易号，若 SDK 支持）查询交易状态；返回本模块公共值对象，含交易状态、金额、交易号等必要字段。 |
| `R-04` | **退款** | 按商户订单号 + 商户退款单号 + 金额发起退款；退款单号作为幂等键透传；返回退款结果公共值对象。 |
| `R-05` | **回调验签** | 同步 `return_url` 与异步 `notify_url` 两类报文均可验签；同时支持普通公钥与公钥证书两种签名方式；验签结果明确（通过 / 失败），失败不静默；验签是**纯函数**，同一报文多次调用结果一致。 |
| `R-06` | **证书 / 公钥轮换** | 配置支持多组支付宝公钥 / 证书并存（按签名方式 / 证书序列号选取），使轮换期新旧凭据可同时验签通过；过期 / 未配置的凭据验签失败。 |
| `R-07` | **幂等职责边界** | 模块**不做**通知去重与已处理缓存；须从回调报文中透出 `out_trade_no` 等幂等键供业务前置校验；同一报文重复调用验签结果稳定、模块内部不产生副作用。 |
| `R-08` | **沙箱 / 生产环境切换** | 通过配置切换沙箱与生产网关（及各自凭据）；切换不改业务代码；两套环境的配置不串用。 |
| `R-09` | **多应用 / 多商户**（P0 边界最小化） | 至少支持「当前调用显式指定 appId」或「按当前线程绑定 appId」二者其一（具体形态待设计定档）；不同 appId 的配置与调用互不串扰。 |
| `R-10` | **依赖缺失可读异常** | 未引入 IJPay / 支付宝 SDK 依赖时，首次调用抛本模块 `AliPayException`（含缺失依赖提示），不裸抛 `NoClassDefFoundError` / `ClassNotFoundException`。 |
| `R-11` | **公共面收敛与依赖纪律** | 根包公共类型精确且由架构门禁断言（名单 §7.3，数量设计关定档）；公共签名零第三方 / 零框架类型；IJPay 与支付宝 SDK 类型仅在内部实现层出现。 |
| `R-12` | **异常语义统一** | 业务调用失败（签名失败 / 接口返回错误 / 验签失败）统一收敛为 `AliPayException`（含可读消息与 cause）；不向调用方抛 IJPay / SDK 的原始异常类型。 |

### 6.2 配置能力诉求（键名与默认值由 `design.md` 定稿）

> 本表只约束「必须可配」的能力。所有凭据类配置**不得出现在日志 / 异常消息明文**中。

| 能力 | 约束 | 建议默认 |
| --- | --- | --- |
| 应用标识 appId | 必填（多应用时按 appId 取配置） | 无 |
| 应用私钥 | 必填（支持文本 / 文件路径两种形态，具体由设计定档） | 无 |
| 支付宝公钥 / 公钥证书 | 与签名方式对应的验签凭据，**支持多组并存**以支持轮换 | 无 |
| 签名方式 | 普通公钥 / 公钥证书二选一 | 公钥证书（**已裁决**，2026-11-26 用户裁决） |
| 网关环境 | 沙箱 / 生产切换；本模块以**配置显式管理网关 URL + 凭据组**（不依赖 IJPay 内建开关） | 沙箱（仅开发期；网关与凭据组经配置显式指定，**已裁决** 2026-11-26，详见 §8.4） |
| 回调地址 notifyUrl / returnUrl | 下单时逐笔传入或全局默认 | 逐笔传入 |
| 超时 | 连接 / 读取两段（若 IJPay 内核暴露） | 默认（对齐 `aifei-http` 建议值，待设计定档） |

### 6.3 P1（应做，资源允许则做）

- 退款查询（`alipay.trade.fastpay.refund.query`）、关单（`alipay.trade.close`）、撤销（`alipay.trade.cancel`）。
- APP 支付（`alipay.trade.app.pay`）、交易创建（`alipay.trade.create`）、条码支付（`alipay.trade.pay`）等其余下单形态。
- 统一配置类 `AliPayApiConfig` 风格的.builder 装配与多应用注册表。
- ~~`aifei-pay-demo` 单页演示~~（**已裁决不配**，2026-11-26 用户裁决，详见 §13）。

### 6.4 P2（明确延后）

- 转账 / 红包 / 花呗分期 / 结算等 IJPay 已封装但非收款主链路的接口。
- 主动补单轮询、通知送达补偿。
- ~~支付宝 V3 SDK（`IJPay-AliPay-V3`）路线~~——**已采纳为本期基线（2026-11-26 用户裁决，详见 D-01 / §7.1）**；V2（`IJPay-AliPay`）路线**不纳入本期**。
- 跨渠道（微信 + 支付宝）统一门面抽象层。

## 七、IJPay 扩展方案的需求侧约束

### 7.1 IJPay 是依赖内核，不是复制代码（硬约束）

- 本模块通过 **Maven 依赖**引入 IJPay 支付宝 V3 构件（基线坐标 `com.github.javen205:IJPay-AliPay-V3:3.0.0`，**2026-11-26 用户裁决**，详见 D-01 / §7.2），调用其公开 API 完成签名 / 报文 / 传输；**禁止**把 IJPay 源码复制、fork、改写后放入本仓库。
- IJPay 为 Apache-2.0（F-02）：`README.md` 与 `docs/design.md` 须声明依赖来源、版本与许可，保留其署名；不修改其版权信息。
- 抽象方向：本模块是 **IJPay 之上的 aifei 适配门面**（配置装配 + 公共值对象隔离 + 回调入口适配 + 错误收敛），不是 IJPay 的替代品，也不重复实现其已有能力。
- **扩展形态：采用包装（组合），非继承**（**2026-11-26 用户裁决**）。IJPay 是否提供可继承 / 可重写扩展点不再阻塞设计；若设计期核对发现 IJPay 有可用扩展点，仍以「组合优先」为原则（核对项见 §11.2）。

### 7.2 依赖策略（Java 8 兼容 + `optional`）

- `cn.aifei:aifei-all` 为 `provided`（不传递，消费方自带）。
- IJPay 支付宝构件及支付宝 SDK 传递依赖按仓库纪律应为 `optional` / 阻断传递，由消费方按需显式声明；**具体 scope 须过设计决策并在根 `AGENTS.md` 登记例外（若需 `compile`）后方可落地**——需求侧不预设例外。
- **Java 8 兼容性风险（需求侧必须盯住）**：IJPay 3.0.0 自身保证 class 52（F-09 / F-10），但其传递依赖链复杂（okhttp 4.9.3 + kotlin-stdlib 1.4.10、gson 2.9.0、guava 28.1-jre、hutool 5.8.48、dom4j 2.1.5、bcprov-jdk18on 1.86 等，F-11；**V3 路线无 fastjson**）。验收标准 `AC-02` 要求对**最终依赖树**逐个断言「对 Java 8 可加载的 class」字节码 ≤ 52（`module-info.class` 与 `META-INF/versions/**` 不纳入判定，理由与实证见 AC-02 / `design.md` §2.3）；若某传递依赖破坏 Java 8 基线，须通过版本锁定 / 排除项处理，否则不得立项落地。
- **V3 内核传递依赖处理（基线已裁决 V3，2026-11-26）**：`IJPay-AliPay-V3` 所依赖的 `dom4j:dom4j`（旧坐标）与 `org.bouncycastle:bcprov-jdk15on` **已由上游 IJPay pom 排除，本模块无需重复排除**（2026-11-26 实测，见 `design.md` §2.1.1）；实际生效 `org.dom4j:dom4j:2.1.5` 与 `org.bouncycastle:bcprov-jdk18on:1.86`（F-11），本模块仍应显式声明使版本锁定成为本模块 pom 的可见事实（声明形态由 `design.md` 定档）；**严禁再显式引入 `bcprov-jdk15on`，避免与 `bcprov-jdk18on` 同名类冲突**。精确版本已定档（F-12）。
- 依赖缺失时给可读异常（R-10），对齐 `aifei-excel` 的 EasyExcel 与 `aifei-tools` 的 ip2region / ZXing 先例。
- 不引入任何日志绑定；模块自身记录走 `cn.aifei.log.Log`（惰性获取）。

### 7.3 公共面隔离（零第三方 / 零框架类型）

- 根包公共面**预估** 4 类（沿用规划命名，须过设计决策，`design.md` 定稿）：`AliPayClient`（接口，`XxxClient` 命名）/ `AliPayKit`（有状态门面）/ `AliPayNotifyUtil`（无状态验签工具，`XxxUtil` 命名）/ `AliPayException`（统一异常）。**嵌套类型不单独计数**；新增公共类型须先过设计决策并回写本文 §5 D-02。
- **IJPay / 支付宝 SDK 的类型（如 `AlipayTradeQueryResponse`、各类 `AlipayTrade*Model`、`DefaultAlipayClient`、`JSONObject` 等）不得出现在任何公共签名中**；由 `internal` 层转换为本模块自有值对象（值对象数量与归属由设计关定档，可为根包公共类型或嵌套只读结构）。
- 与 `aifei-weixin` 的差异须在 `README.md` / `design.md` 交叉声明：`aifei-weixin` 的 `PayApi` 返回 `com.alibaba.fastjson2.JSONObject`（其既有既定事实），本模块公共面**不沿用**该第三方返回类型，改用自有值对象。此项是 D-02 约束的直接结果，**已由用户裁决确认（2026-11-26）：仅对齐动作语义与错误契约，公共返回用自有值对象，不引入 `JSONObject`**（详见 D-02 / §7.4）。

### 7.4 与 `aifei-weixin` 的分工边界

| 维度 | `aifei-weixin`（既有） | `aifei-pay`（本模块） |
| --- | --- | --- |
| 渠道 | 微信支付 V3（`cn.aifei.weixin.pay`） | 支付宝（依赖 IJPay-AliPay-V3 内核） |
| 实现形态 | 纯 JDK 实现（签名 / 加解密 / HTTP 自研） | 依赖 IJPay 内核（不开 second implementation） |
| 动作语义 | `PayApi`：`jsapi` / `query` / `close` / `refund`；`PayNotifyUtil`：`verify` / `decryptResource` / `verifyAndDecrypt` | 对齐同类动作语义：下单 / 查单 / 退款 / 回调验签（命名与错误契约向 `PayApi` 看齐，见下） |
| 回调幂等 | 明确**不做**幂等，业务按 `out_trade_no` + 状态前置校验 | 同左（R-07） |
| 公共返回类型 | `JSONObject`（fastjson2，既有事实） | 自有值对象（§7.3，**已裁决 2026-11-26**） |

**「复用 PayApi 契约」的需求侧解读（已裁决 2026-11-26）**：指动作集合、方法语义与错误契约的对齐（非 2xx / 验签失败抛统一异常、回调不做幂等），**不是**代码级 import 复用、不是引入 `IJPay-WxPay`、不产生模块间代码依赖。公共返回用本模块自有值对象，**不引入 `JSONObject`**。模块间零 `import` 依赖由门禁断言（§10）。

### 7.4.1 IJPay-WxPay 与 `aifei-weixin` 的取舍说明（2026-11-26 用户裁决）

IJPay 自带微信支付能力（`IJPay-WxPay`），与本仓库 `aifei-weixin` 的 `cn.aifei.weixin.pay` 在能力上重叠。**用户裁决（2026-11-26）：不强禁 IJPay-WxPay，交由引入者（消费方）自行选择其一**；本模块默认不引入 IJPay-WxPay，也不为其提供门面。具体取舍口径：

1. **本模块默认不引入 IJPay-WxPay**，避免与 `aifei-weixin` 既有微信支付 V3 重复建设。本模块公共面 / 门禁（§7.3 / §10.3 G-02 / G-03）**不覆盖 IJPay-WxPay**——即本模块源码不 import `com.ijpay.wxpay.*`（门禁 `G-03` 断言此点），但消费方自带 IJPay-WxPay 不在门禁守护范围内。
2. **两者差异**（供消费方决策）：
   - `aifei-weixin`：本仓库**纯 JDK 自研**（签名 / 加解密 / mTLS / HTTP 全自实现，零第三方运行依赖），与微信支付 V3 官方协议直接对齐，已落地并验证。
   - `IJPay-WxPay`：IJPay 封装的微信支付，依赖其内核与传递依赖（okhttp / kotlin-stdlib / fastjson 等，见 F-11），形态为「静态工具类 + 包装官方 SDK」。
3. **消费方取舍建议**：
   - 若消费方已在 `aifei-weixin` 用微信支付，**继续用 `aifei-weixin`，不要混入 IJPay-WxPay**（避免两套微信支付实现并存、凭据与回调链路混乱）。
   - 若消费方已基于 IJPay 整体接入多渠道（微信 + 支付宝 + 其它），可自行引入 IJPay-WxPay 处理微信支付；此时支付宝侧仍建议走本模块 `aifei-pay` 门面（公共面隔离 + aifei 配置装配 + 回调入口适配），不要绕过门面直用 IJPay-AliPay-V3。
4. **门禁口径调整**：原 PRD 把「不引入 IJPay-WxPay」作为硬约束与门禁断言，本次按用户裁决放宽为「**本模块源码不引入 IJPay-WxPay**」（消费方自带不在门禁范围）。§10.3 `G-03` 与 AC-20 已同步调整（见对应章节）。

## 八、关键契约

### 8.1 回调验签（R-05）

| 项 | 约定 |
| --- | --- |
| 报文来源 | 同步 `return_url`（GET 参数）与异步 `notify_url`（POST 表单）两类，均以字符串 / Map 形式交给模块，不依赖 Servlet 类型 |
| 签名方式 | 普通公钥（RSA / RSA2）与公钥证书两种；证书模式须按报文中的证书序列号选取对应证书验签 |
| 结果语义 | 验签通过 / 失败明确返回；**失败不静默**（便捷入口可抛 `AliPayException`，由消费方回失败响应）；对齐 `PayNotifyUtil.verify` 的布尔语义 + `verifyAndDecrypt` 的抛异常语义 |
| 纯函数性 | 验签不依赖可变内部状态、不对同一报文产生副作用（R-07） |
| 官方依据 | 支付宝开放平台文档与 IJPay 支付宝 V3 SDK（F-08 部分验证，设计期逐字核对；**V3 路线验签方法与证书模式差异须以 IJPay-AliPay-V3 源码 / alipay-sdk-java-v3 JavaDoc 逐字核对，并在 AC-10~AC-12 用例向量中固化**，2026-11-26 裁决后新增核对项） |

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
| `AC-01` | R-01 装配 | Given 合法配置，When `start()` 后 `@Inject AliPayClient`，Then 非 null 可执行；Given 已停止，When 调门面，Then 抛 `AliPayException`；重复 `start()` / `stop()` 幂等无异常 | 集成测试（容器装配 + 静态引用） |
| `AC-02` | R-11 / §7.2 Java 8 | Given 构建产物，When `javap -verbose` 逐类检查，Then 主 JAR 全部 class major == 52；Given `mvn dependency:tree` 后的依赖 classpath，When 逐 JAR 检查，Then **依赖 jar 中对 Java 8 可加载的全部 `.class` major ≤ 52**；`module-info.class` 与 `META-INF/versions/**`（Multi-Release 变体）**不纳入判定**，理由：Java 8 不解析 JPMS 描述符、不识别 Multi-Release 目录，运行期不可达（超基线者须已锁定 / 排除，否则失败）。**本口径于 2026-11-26 由设计关实测后精化，实证见 `aifei-pay/docs/design.md` §2.3**。**设计期待核对项（2026-11-26 裁决后新增）**：V3 路线须核对 `dom4j:dom4j` 与 `bcprov-jdk15on` 已被上游排除、`dom4j 2.1.5` 与 `bcprov-jdk18on 1.86` 生效且无 `bcprov-jdk15on` 残留（§7.2） | `javap` 批量断言 + `dependency:tree` + 门禁 |
| `AC-03` | R-11 公共面 | Given 根包 `cn.hg.aifei.pay` 顶层 public 类型，When 反射扫描，Then 集合精确等于 `design.md` 定稿名单（预估见 §7.3，超集/子集均失败） | 门禁 `G-01` + 反射 |
| `AC-04` | R-11 / §7.3 零第三方类型 | Given 公共 API 全量签名，When 审查，Then 零 IJPay / 支付宝 SDK / `JSONObject` / `cn.aifei.*` 类型；源码仅 `internal` 层可 import 第三方 | 门禁 `G-02` + 源码扫描 |
| `AC-05` | R-01 配置非法 | Given 必填项缺失 / 非法值，When `start()`，Then 启动失败抛 `AliPayException`，消息含键名且不含私钥明文；失败无半装配态，修正后重启正常 | 单测 |
| `AC-06` | R-02 扫码下单 | Given 合法预下单参数（商户单号 / 金额 / 标题 / 回调地址），When 调扫码下单（伪内核 / 沙箱），Then 返回公共值对象含可用于拉起的二维码串 / 链接，且不含第三方类型 | 单测（内核伪实现）+ 沙箱集成（可选） |
| `AC-07` | R-02 H5 / PC 下单 | Given H5 / PC 下单参数，When 调用，Then 返回公共值对象含跳转 URL / 表单参数；两类入参校验（缺回调地址 / 金额 ≤ 0）抛 `AliPayException` | 单测 |
| `AC-08` | R-03 查单 | Given 存在的商户订单号，When 查单，Then 返回公共值对象含交易状态 / 金额 / 交易号；Given 不存在订单号，Then 按内核语义返回明确「未找到」或抛 `AliPayException`（口径设计定档，须唯一） | 单测 |
| `AC-09` | R-04 退款 | Given 订单号 + 退款单号 + 合法金额，When 退款，Then 返回退款结果公共值对象；Given 同一退款单号重复提交，Then 透传同一幂等键给内核（模块不去重，断言透传值一致） | 单测 |
| `AC-10` | R-05 公钥验签 | Given 普通公钥方式、用支付宝测试私钥签名的合法回调报文，When 调验签，Then 通过；Given 篡改报文字段，Then 验签失败且不静默 | 单测（固定向量） |
| `AC-11` | R-05 证书验签 | Given 公钥证书方式、按证书序列号匹配的合法回调报文，When 调验签，Then 通过；Given 序列号不匹配，Then 失败 | 单测（固定向量） |
| `AC-12` | R-05 同步/异步 | Given 同步 `return_url` GET 参数与异步 `notify_url` POST 表单两类报文样本，When 分别验签，Then 均按各自方式正确通过；两类报文解析路径各自唯一 | 单测（固定向量） |
| `AC-13` | R-06 证书轮换 | Given 同一 appId 配置新旧两组凭据，When 分别用旧凭据与新凭据签名的报文验签，Then 两者均通过；Given 仅配置新凭据，When 用旧凭据签名报文验签，Then 失败 | 单测 |
| `AC-14` | R-07 幂等边界 | Given 同一回调报文连续调用验签 N 次，When 比较结果与内部状态，Then N 次结果一致、模块内无「已处理」缓存 / 去重痕迹（源码扫描 + 结果比对）；Given 报文含 `out_trade_no`，Then 公共值对象可读取该幂等键 | 单测 + 源码扫描 |
| `AC-15` | R-08 环境切换 | Given 沙箱配置，When 下单，Then 请求指向沙箱网关；Given 生产配置，When 下单，Then 指向生产网关；Given 沙箱凭据 + 生产环境配置，Then 配置校验失败或调用明确失败，不静默串用（网关 URL + 凭据组经配置显式管理，**已裁决 2026-11-26**，详见 §8.4）。**可机械验证的边界（2026-11-26 裁决口径）**：模块只能校验 ① `env` 必填；② `gateway-url` 的 host 与 `env` 一致（沙箱 / 生产两个官方网关）；③ 不同 appId 的网关互不串扰。**「凭据本身归属哪个环境」明确排除在可验证范围外**——凭据与环境无内在可判定联系（同一对 RSA 密钥可同时用于两套环境的自签测试），模块无法机械判定；该项**由配置评审与凭据来源管理兜底，README 显式声明** | 单测（伪内核断言网关参数）+ 配置校验用例 |
| `AC-16` | R-09 多应用 | Given 两个 appId 各自配置，When 分别指定 appId 调用，Then 各自使用对应配置，结果互不串扰；Given 未配置的 appId，Then 抛 `AliPayException` | 单测 |
| `AC-17` | R-10 依赖缺失 | Given classpath 无 IJPay / 支付宝 SDK，When 首次调用门面，Then 抛 `AliPayException`，消息含缺失依赖提示，无裸 `NoClassDefFoundError` | 单测（隔离 classpath / 伪装载） |
| `AC-18` | R-12 异常收敛 | Given 内核抛出原始异常（签名失败 / 接口错误 / 验签失败），When 经门面调用，Then 调用方收到 `AliPayException`（含 cause），无 IJPay / SDK 原始异常类型外泄 | 单测 |
| `AC-19` | §7.2 依赖 scope | Given `mvn dependency:tree`，Then IJPay 与支付宝 SDK 依赖为 `optional`（或已登记例外），`aifei-all` 为 `provided`，不向消费方传递未声明依赖 | `dependency:tree` + 根 `AGENTS.md` 例外核对 |
| `AC-20` | §3.2 零 DB / 本模块不引 IJPay-WxPay | Given 源码扫描，Then `src/main/java` 无 `import java.sql.`、无 `IJPay-WxPay` / 其余渠道模块 import（门禁 `G-03`）。**口径已放宽（2026-11-26 用户裁决）**：本模块源码不引入 IJPay-WxPay，但消费方自带 IJPay-WxPay 不在本门禁范围（§7.4.1） | 门禁 `G-03` + `dependency:tree`（仅断言本模块源码与主 JAR 直接依赖） |
| `AC-21` | §3.1 / §7.1 依赖内核 | Given 依赖树与源码，Then IJPay 以 Maven 构件引入（非源码复制）；源码中无 IJPay 类的整段复制痕迹；`README` / `design` 含来源与 Apache-2.0 署名声明 | 人工审查 + 门禁（包来源检查） |
| `AC-22` | 构建 | Given 模块目录，When `mvn clean verify`，Then 0 failure / 0 error | 构建 |

**AC 与门禁映射**：`AC-02`（部分）/ `AC-03` / `AC-04` / `AC-20` 由架构门禁 `ArchitectureBoundaryTest` 承载；`AC-19` 由 `dependency:tree` 与根例外登记核对承载；其余由单测 / 集成用例承载。用例清单写入 `docs/testing.md`，交付门禁按「AC 编号 ↔ 用例 ↔ 结果」三列映射。

## 十、非功能要求与硬约束

### 10.1 非功能要求

- **性能**：默认（支付调用延迟由支付宝网关决定，模块不做吞吐 KPI；签名 / 验签为本地计算）。
- **安全**：私钥与凭据不落日志 / 异常明文；凭据不明文提交（仓库安全纪律）；回调验签失败必须能被消费方拒绝处理。
- **兼容**：Java 8（语法 + API 双约束，class major 52）；IJPay 3.0.0 自身基线已验证为 Java 8 / class 52（F-09 已验证，IJPay 3.x 全部发布模块 class 主版本 52、不使用 Java 9+ 语法/API）；IJPay 及传递依赖「**对 Java 8 可加载的 class**」字节码 ≤ 52（`AC-02`；判定口径已于 2026-11-26 精化——`module-info.class` 与 `META-INF/versions/**` 不纳入，实测 0 例外，见 `design.md` §2.3）；`aifei-all` `provided`。

### 10.2 硬约束（设计不得推翻）

| 项 | 要求 |
| --- | --- |
| Java 8 | `source` / `target` = 1.8；无 `var` / `record` / `switch` 表达式 / text block / `List.of` / `Map.of`；主 JAR 全部 class major 52 |
| 公共 API 实现无关 | 根包公共签名零 IJPay / 支付宝 SDK / `JSONObject` / `cn.aifei.*` 类型（§7.3） |
| 依赖 | IJPay 与支付宝 SDK `optional`；`aifei-all` `provided`；不引任何日志绑定；scope 例外须先在根 `AGENTS.md` 登记 |
| 零 DB | `src/main/java` 禁 `import java.sql.`；不依赖 `aifei-db` |
| 依赖内核不复制 | Maven 构件引入 IJPay，禁止源码复制 / fork（§7.1） |
| 渠道边界 | **本模块源码**禁 `IJPay-WxPay` 及支付宝以外渠道模块 import（§3.2 / `AC-20`）；消费方自带 IJPay-WxPay 不在门禁范围（§7.4.1，2026-11-26 用户裁决） |
| 唯一定义点 | 配置域常量与默认值、凭据选取逻辑、验签入口、异常构造与消息格式、公共值对象转换，各只有一个定义点，实现类不得复制 |
| 文档四件套 | `README.md`（用法 + 边界 + 依赖来源声明）、`docs/design.md`（契约事实来源 + 公共面定稿 + 差异决策）、`docs/testing.md`（用例清单 + AC 映射）、模块 `AGENTS.md`（开发约束） |
| 构建 | 模块目录内 `mvn clean verify` 0 failure / 0 error |

### 10.3 架构门禁断言清单（`ArchitectureBoundaryTest`，源码文本扫描，模块根目录执行，不得放宽）

| 编号 | 断言 |
| --- | --- |
| `G-01` | 根包 `cn.hg.aifei.pay` 顶层 public 类型集合精确等于 `design.md` 定稿名单（预估见 §7.3；超集 / 子集均失败） |
| `G-02` | 公共 API 签名零第三方 / 零框架类型：根包源码不 import IJPay（`com.ijpay.*`）、支付宝 SDK（`com.alipay.*`）、`com.alibaba.fastjson*`、`cn.aifei.*` |
| `G-03` | `src/main/java` 无 `java.sql.`；**本模块源码**无微信支付 / 其余渠道模块引用（禁 `com.ijpay.wxpay` / IJPay-QQ / JDPay / UnionPay / PayPal / Stripe / FuiYou / XPay 相关包）。**口径放宽（2026-11-26 用户裁决）**：消费方自带 IJPay-WxPay 不在门禁断言范围 |
| `G-04` | `cn.aifei.*`（若需要）仅出现在 `aifei` 接入包；IJPay / 支付宝 SDK 类型仅出现在 `internal` 实现层 |
| `G-05` | 主 JAR 全部 class major == 52；源码无 Java 8+ 语法 / API（正则兜底，`javap` 为主防线） |
| `G-06` | 依赖树中 IJPay 与支付宝 SDK 为 `optional` 或已登记例外；无日志绑定 |

## 十一、待明确事项

> 原 Q1~Q8 与「IJPay-WxPay 交引入者」额外裁决已于 **2026-11-26** 全部裁决，结论已固定至 §5 差异表（D-01~D-08）、§2 调研结论、§6.2/§6.3/§6.4、§7.1~§7.4.1、§8.1/§8.4、§9 AC-02/AC-15/AC-19/AC-20、§10.2/§10.3、§13 等对应章节（各落地处均标注「2026-11-26 用户裁决」字样），本节不再保留裁决留痕表。原 §11.2 中 A-2/A-3/A-5/A-6 已由 Q7/Q5 裁决确认，亦一并删除；本节只保留**真正未定档项**：① 假设（未获否决即执行，被否决须回写本文与对应 AC）；② 设计期待核对项（设计期闭环）。

### 11.1 假设（未获否决即执行）

| # | 假设 | 影响面 |
| --- | --- | --- |
| A-1 | IJPay 3.0.0 的传递依赖经锁定后，「对 Java 8 可加载的 class」可全部 ≤ 52（F-10 显示官方已为此锁定 guava/dom4j/okhttp/jakarta.servlet-api 等版本上限，但该锁定写在父 pom 的 `dependencyManagement`、不向消费者传播；V3 实际依赖树为 okhttp 4.9.3 + kotlin-stdlib 1.4.10、gson 2.9.0、guava 28.1-jre、hutool 5.8.48、dom4j 2.1.5、bcprov-jdk18on 1.86。**本假设已于 2026-11-26 由设计关实测闭合**：在 AC-02 精化口径下 0 例外，实证见 `design.md` §2.3） | AC-02、§7.2（IJPay 3.0.0 自身基线已验证，见 §10.1） |
| A-4 | 多应用切换至少支持「显式指定 appId」（线程绑定形态作为可选增强，具体形态待设计定档） | R-09、AC-16 |

### 11.2 设计期待核对项（设计期闭环）

> 以下项均非「已明确无异议」，不得删；须在 `docs/design.md` 定稿前以 IJPay-AliPay-V3 源码 / alipay-sdk-java-v3 JavaDoc / Maven Central 实际 pom 逐字核对，并在对应 AC 用例向量中固化。

| # | 核对项 | 归属章节 | 闭环时机 |
| --- | --- | --- | --- |
| F-12 | ~~`alipay-sdk-java-v3` 的精确小版本号~~——**已闭环**：精确版本 = `3.1.89.ALL`，由 `IJPay-AliPay-V3:3.0.0` 的 pom 属性 `alipay.version` 硬写（2026-11-26 实测逐字取证，见 `design.md` §2.1.1） | §2.1 F-12、§7.2 | **已闭环（2026-11-26）** |
| AC-02 | V3 路线须核对 `dom4j:dom4j` 与 `bcprov-jdk15on` 已被上游排除、`dom4j 2.1.5` 与 `bcprov-jdk18on 1.86` 生效且无 `bcprov-jdk15on` 残留（依赖树逐项断言「对 Java 8 可加载的 class」字节码 ≤ 52；`module-info.class` 与 `META-INF/versions/**` 不纳入判定，见 AC-02 精化口径） | §7.2、AC-02 | 设计期（判定口径已于 2026-11-26 精化，见 `design.md` §2.3） |
| AC-10~12 | V3 路线回调验签方法向量（普通公钥 / 公钥证书 / 同步 GET / 异步 POST 四象限）须以 IJPay-AliPay-V3 源码 / alipay-sdk-java-v3 JavaDoc 逐字核对，并在 AC-10~AC-12 固定向量用例中固化 | §8.1、AC-10~AC-12 | 设计期 |
| Q4 | IJPay 是否存在可继承 / 可重写扩展点（已裁决「包装（组合）非继承」，扩展点不再阻塞设计；设计期核对后仍以「组合优先」为原则） | §7.1 | 设计期 |
| AC-19 | IJPay 与支付宝 SDK 依赖 `optional` 的具体 scope 落点（须过设计决策；若需 `compile` 例外须先在根 `AGENTS.md` 登记） | §7.2、AC-19 | 设计期 |

## 十二、边界与风险

| # | 风险 | 说明 | 缓解 / 需求侧动作 |
| --- | --- | --- | --- |
| K-1 | **依赖链与 Java 8 基线** | IJPay 传递依赖多（okhttp 4.9.3 + kotlin-stdlib 1.4.10、gson 2.9.0、guava 28.1-jre、hutool 5.8.48、dom4j、bcprov-jdk18on；V3 路线无 fastjson）；任一**对 Java 8 可加载的** class 字节码 > 52 即破坏仓库基线 | AC-02 对最终依赖树逐类断言（`module-info.class` 与 `META-INF/versions/**` 不纳入，见 AC-02 口径）；必要时版本锁定 / 排除（F-10 提供先例）；不通过则不落地 |
| K-2 | **公共面被第三方类型污染** | IJPay / 支付宝 SDK / `JSONObject` 类型若进入公共签名，违反「零第三方类型」门禁 | §7.3 隔离约束 + AC-04 + `G-02`；**已裁决（2026-11-26）：公共返回用自有值对象**，风险已闭环至设计期定档 |
| K-3 | **依赖 scope 与传递** | 若内核须随 SDK 传递，`optional` 会让消费方被迫重复声明；`compile` 又须登记例外 | **已裁决（2026-11-26）：依赖 `optional`**；例外须先在根 `AGENTS.md` 登记（D-03） |
| K-4 | **IJPay 版本 / 能力变动** | IJPay 活跃迭代（F-05 版本从 2.9.x 到 3.0.0），API 可能对内实现变动 | 锁定版本 + 公共面隔离（§7.3），使升级不波及契约 |
| K-5 | **回调验签细节未逐字验证** | 证书模式验签、同步/异步差异在官方文档页未展开（F-08 部分验证）；V3 路线验签方法差异为 2026-11-26 裁决后新增核对项 | 设计期读 IJPay-AliPay-V3 源码 / alipay-sdk-java-v3 JavaDoc + 固定向量用例（AC-10~12）闭环 |
| K-6 | **与微信支付重复建设** | IJPay 自带微信能力，若误引入 IJPay-WxPay 会与 `aifei-weixin` 重复 | §3.2-1 口径已放宽 + 已裁决分工（详见 §7.4）+ **2026-11-26 用户裁决：交由引入者自行选择其一**（§7.4.1 / D-08）；本模块源码不引 IJPay-WxPay 由门禁 `G-03` 断言 |
| K-7 | **证书 / 私钥泄露** | 凭据配置若被打入日志 / 异常即安全问题 | §6.2 安全约束 + AC-05「消息不含私钥明文」 |
| K-8 | **工期变量** | 依赖 IJPay 后工期可能变化（D-06） | 需求侧不承诺工期，交交付负责人重估 |

## 十三、入口与交互要点

本模块**无自有 UI 页面**，需交付的「入口」清单如下：

| # | 入口 | 使用者 | 关键交互 | 归属 |
| --- | --- | --- | --- | --- |
| 1 | 下单入口 | 业务 Service | 商户单号 / 金额 / 标题 / 回调地址 → 拉起参数 / 跳转 URL 值对象 | 模块公共 API（`AliPayClient`） |
| 2 | 查单入口 | 业务 Service | 商户订单号 → 交易状态值对象 | 模块公共 API |
| 3 | 退款入口 | 业务 Service | 订单号 + 退款单号 + 金额 → 退款结果值对象 | 模块公共 API |
| 4 | 回调验签入口 | 消费方 Handler / Just Service | 报文（Map / 字符串）+ 头信息 → 验签结果与幂等键值对象 | 模块公共 API（`AliPayNotifyUtil`） |
| 5 | 装配入口 | 应用 `AppConfig` | 装载配置、校验、`@Inject AliPayClient`、环境切换 | 模块接入构件（`AliPayPlugin`，形态待设计定档） |
| 6 | 配置刷新入口（轮换） | 运维 / 配置中心 | 新增 / 移除验签凭据，新旧并存 | 模块公共 API（机制设计定档） |
| 7 | ~~demo 演示页~~（**已裁决不配，无此项**，2026-11-26 用户裁决，详见 §6.3） | — | — | — |

## 十四、需求关 DoD 自检

| # | 检查项 | 结论 |
| --- | --- | --- |
| 1 | 目标可一句话说清 | **通过**：`aifei-pay` 以 IJPay 支付宝内核为依赖底座，提供「配置 → 下单/查单/退款 → 回调验签」的 aifei 统一门面，与 `aifei-weixin` 支付侧语义对齐。 |
| 2 | 每条 P0 都有可测 AC | **通过**：`R-01`~`R-12` 共 12 条 P0 全部映射到 §9 共 **22** 条 AC（`AC-01`~`AC-22`），每条给出 GWT 判定条件与验证方式；回调验签（AC-10~12）、证书轮换（AC-13）、幂等边界（AC-14）、沙箱/生产切换（AC-15）均有专门 AC。 |
| 3 | 本期不做清单明确 | **通过**：§3.2 共 10 条非目标 + §6.3/§6.4 的 P1/P2 清单，逐条写死；含「不做微信支付 / 不做其余渠道 / 不复制 IJPay 源码」等范围蔓延拦截点。 |
| 4 | 假设与待裁决显式标注、无臆造业务规则 | **通过**：§2 调研结论逐条标注来源 URL 与验证状态（已验证 / 部分验证 / 未验证；未验证项 F-12 已于 **2026-11-26 实测闭环**）；原 §11 待用户裁决事项（Q1~Q8 + IJPay-WxPay 衍生裁决）已于 **2026-11-26 全部裁决**，结论已固定至对应章节、§11 仅保留真正未定档项（A-1/A-4 假设 + 5 项设计期待核对项，见 §11.1/§11.2；A-1 与 F-12 已实测闭合）；关键推断均标注裁决来源。 |
| 5 | 需求变更显式记录 | **通过**：开篇【需求变更声明】+ §5 差异表（D-01~D-08）显式记录「零第三方 → 基于 IJPay-AliPay-V3」的依赖策略变更与连带影响；8 项待裁决已全部裁决并落地到对应章节；新增 D-08 记录 IJPay-WxPay 口径放宽（2026-11-26 用户裁决）。**V1.1 修订逐条留痕见「修订记录」**。 |

## 修订记录

| 版本 | 日期 | 修订项 | 变动内容（原文口径 → V1.1 口径） | 依据 | 影响面 |
| --- | --- | --- | --- | --- | --- |
| **V1.1** | 2026-11-26 | ① AC-02 字节码判定口径精化（原为交付阻塞项） | 原文「IJPay-AliPay-V3 及全部传递依赖无字节码 > 52 者」按字面**永不可达且无修复路径**（`javax.ws.rs-api:2.1.1` 根级 `module-info.class` major 53 不可排除；`bcprov-jdk18on:1.86` 的 `META-INF/versions/11/**` 有 112 个 major 55 的 class，属 BC 官方 Multi-Release 发布形态）→ 改为「依赖 jar 中**对 Java 8 可加载的**全部 `.class` major ≤ 52；`module-info.class` 与 `META-INF/versions/**` 不纳入判定，理由：Java 8 不解析 JPMS 描述符、不识别 Multi-Release 目录，运行期不可达」；主 JAR 断言不放宽（仍全部 == 52） | `aifei-pay/docs/design.md` §2.3（`javap` 逐 JAR 实测，精化口径下 0 例外） | §9 AC-02（仅改判定条件，验证方式沿用 `javap` 批量断言 + `dependency:tree` + 门禁）；连带 §7.2、§10.1、§11.1 A-1、§11.2 AC-02 行、§12 K-1 口径同步 |
| **V1.1** | 2026-11-26 | ② AC-15 可机械验证边界收口 | 原文「沙箱凭据不得误调生产网关」未界定可验证范围 → 补「可机械验证的边界」：模块只能校验 ① `env` 必填；② `gateway-url` 的 host 与 `env` 一致（沙箱/生产两个官方网关）；③ 不同 appId 的网关互不串扰；**「凭据本身归属哪个环境」明确排除在可验证范围外**，由配置评审与凭据来源管理兜底、README 显式声明 | `aifei-pay/docs/design.md` §14.1 A-3 / §14.3 R-1 | §9 AC-15（仅追加边界说明，原 GWT 判定与验证方式不变） |
| **V1.1** | 2026-11-26 | ③ D-02 补记值对象归属形态与数量 | 原文「公共返回用自有值对象、数量待定档」→ 追加「`model` 子包为该授权范围内的第三种归属形态（根包直接子类型 / 嵌套只读结构 / `model` 子包），精确 4 个值对象」，状态列由「数量待定档」更新为「数量已定档」 | `aifei-pay/docs/design.md` §14.1 A-2 / §6.5（`cn.hg.aifei.pay.model` 精确 4 类型） | §5 D-02（仅追加授权界定，D-02 原语义不变） |
| **V1.1** | 2026-11-26 | ④ §2 依赖事实订正（架构师实测与原文表述不一致处） | `okhttp` 4.12.0 → **4.9.3**；`kotlin-stdlib` → **1.4.10**；`guava` 33.6.0-jre → **28.1-jre**（原因：IJPay 父 pom 的 `dependencyManagement` 不向消费者传播）；**V3 路线无 fastjson** → 实际 `gson 2.9.0`；补记额外传递依赖 `hutool 5.8.48` 四件与 `commons-logging:1.1.1`（仅被 `AlipayLogger` 使用）；`alipay-sdk-java-v3` 精确版本定档 **3.1.89.ALL**；`dom4j:dom4j` / `bcprov-jdk15on` 已由**上游 IJPay pom 排除、本模块无需重复排除**，实际生效 `dom4j:2.1.5` / `bcprov-jdk18on:1.86` | `aifei-pay/docs/design.md` §2.1（pom 逐字）/ §2.2（依赖树逐行 32 JAR） | §2.1 F-10 / F-11 / F-12；连带 §7.2、§11.1 A-1、§11.2 F-12 行、§12 K-1 |

> 修订边界声明：V1.1 **仅做口径回写与事实订正**，未改变任何业务规则；`R-01`~`R-12` 语义未变，`AC-01`~`AC-22` 编号连续、无新增 / 无删减（仅 AC-02 判定条件与 AC-15 边界说明的措辞精化）。
