# aifei-pay

支付宝支付的 aifei 统一接入门面：以 IJPay 支付宝 V3 内核为依赖底座，把「配置装配 → 下单 / 查单 / 退款 → 回调验签」
收口为稳定的公共契约，与 `aifei-weixin` 支付侧的动作语义对齐。

- 坐标：`io.github.gollyhu:aifei-pay:1.0.0-SNAPSHOT`（`packaging=jar`）
- 包名：`cn.hg.aifei.pay`
- 基线：Java 8（语法与 API 双约束，主 JAR class major = 52）

本模块是**库模块（SDK）**，不是应用工程：没有 `AppConfig` / `Launcher` / `service/` 包 / 路由 / HIO / 表结构 /
`Db+Row` / UI 页面。交易数据落在消费方业务库，不属于本模块交付物。

## 1. 公共 API 一览

| 类型 | 角色 |
| --- | --- |
| `AliPayClient` | 交易动作接口：`precreate`（扫码）/ `wapPay`（H5）/ `pagePay`（PC）/ `query` / `refund` |
| `AliPayKit` | 静态门面：`client()` + 两个网关常量；**有状态**，依赖装配完成 |
| `AliPayNotifyUtil` | 回调验签工具：`verify`（布尔语义）/ `verifyAndParse`（失败抛异常）；**无状态** |
| `AliPayException` | 唯一外泄的业务失败类型（`extends RuntimeException`），`code()` 透出内核错误码 |
| `model.PayOrderResult` | 下单结果（含 `Channel` / `PayloadType`） |
| `model.PayQueryResult` | 查单结果 |
| `model.PayRefundResult` | 退款结果（`outRequestNo()` 为幂等键回显） |
| `model.PayNotifyResult` | 验签结果 + 交易 / 退款幂等键（含 `NotifyType`） |

根包精确 4 个公共类型、`model` 包精确 4 个值对象，由架构门禁强制，不可增删。

**金额单位是非对称的**：入参统一用 `long ...Fen`（分，必须 > 0）；出参是内核原样透出的元字符串（如 `"0.01"`）。
换算只用 `BigDecimal`，模块内不出现 `double` / `float`。

## 2. 接入步骤

### 2.1 声明依赖

`aifei-all` 由应用自带（`provided`）；支付宝内核相关构件在本模块是 `optional`，**不会向下游传递**，
消费方必须自己声明。

```xml
<dependencies>
    <!-- 生态统一底座（应用自带） -->
    <dependency>
        <groupId>cn.aifei</groupId>
        <artifactId>aifei-all</artifactId>
        <version>1.1.2</version>
        <scope>provided</scope>
    </dependency>

    <dependency>
        <groupId>io.github.gollyhu</groupId>
        <artifactId>aifei-pay</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- 必带：支付宝 V3 内核 -->
    <dependency>
        <groupId>com.github.javen205</groupId>
        <artifactId>IJPay-AliPay-V3</artifactId>
        <version>3.0.0</version>
    </dependency>

    <!-- 必带：开放平台 SDK，版本需与本模块锁定值一致 -->
    <dependency>
        <groupId>com.alipay.sdk</groupId>
        <artifactId>alipay-sdk-java-v3</artifactId>
        <version>3.1.89.ALL</version>
        <exclusions>
            <exclusion><groupId>dom4j</groupId><artifactId>dom4j</artifactId></exclusion>
            <exclusion><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk15on</artifactId></exclusion>
            <exclusion><groupId>org.openapitools</groupId><artifactId>jackson-databind-nullable</artifactId></exclusion>
        </exclusions>
    </dependency>

    <!-- 必带：证书解析（sign-mode=cert 时运行期必需） -->
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcprov-jdk18on</artifactId>
        <version>1.86</version>
    </dependency>

    <!-- 与 SDK 的替换声明对齐，锁定到 Java 8 基线内 -->
    <dependency>
        <groupId>org.dom4j</groupId>
        <artifactId>dom4j</artifactId>
        <version>2.1.5</version>
    </dependency>
</dependencies>
```

要点：

1. **不要**再显式引入 `bcprov-jdk15on`（会与 `bcprov-jdk18on` 同名类冲突）。
2. `dom4j` 必须锁 `2.1.5`（2.2.0 起字节码为 55，超出 Java 8 基线）。
3. 上述三个 exclusion 不要移除；其中 `jackson-databind-nullable` 的排除基于「该 SDK 版本被引用 0 次」的实测结论，
   **升级 `alipay-sdk-java-v3` 时必须重新验证**。
4. 缺失内核依赖时，模块会在装配期或调用入口抛 `AliPayException`，消息含缺失坐标与补齐提示，
   不会让 `NoClassDefFoundError` 冒到业务代码。

### 2.2 装配

模块提供 `aifei.AliPayPlugin implements cn.aifei.plugin.Plugin`，加入插件链即可：

```java
public class AppConfig implements AifeiConfig {
    @Override
    public void configPlugin(Plugins plugins) {
        Prop prop = new Prop("alipay.properties");   // 或复用应用的配置源
        plugins.add(new AliPayPlugin(prop));
    }
}
```

装配顺序（`start()`）：解析 `pay.*` → 探测内核依赖 →（证书模式）探测 BouncyCastle → 为每个 appId 预建客户端 →
注册 `@Inject AliPayClient` 单例 → 写入门面实例。

- 重复 `start()` / `stop()` 无副作用。
- 配置非法时 `start()` 抛 `AliPayException`，且**不留半装配态**（门面保持未装配）。
- 停止只清门面：容器没有注销 API，**已注入的字段仍持旧实例**属已知边界，请在应用生命周期内避免反复装配。

### 2.3 配置样例（`Properties`）

```properties
# 全局
pay.default-app-id = 2021000000000001
pay.connect-timeout = 5000
pay.read-timeout = 15000
pay.page-http-method = POST
pay.return-url = https://example.com/pay/return
pay.notify-url = https://example.com/pay/notify

# 应用 1：证书模式，两组验签凭据（轮换期新旧并存）
pay.apps.2021000000000001.env = production
pay.apps.2021000000000001.sign-mode = cert
pay.apps.2021000000000001.private-key = @file:/etc/alipay/app-private-key.pem
pay.apps.2021000000000001.app-cert = @file:/etc/alipay/app-cert.crt
pay.apps.2021000000000001.root-cert = @file:/etc/alipay/alipay-root.crt
pay.apps.2021000000000001.verify.0.type = cert
pay.apps.2021000000000001.verify.0.value = @file:/etc/alipay/alipay-public-cert-2026.crt
pay.apps.2021000000000001.verify.1.type = cert
pay.apps.2021000000000001.verify.1.value = @file:/etc/alipay/alipay-public-cert-2025.crt

# 应用 2：普通公钥模式，沙箱
pay.apps.2021000000000002.env = sandbox
pay.apps.2021000000000002.sign-mode = key
pay.apps.2021000000000002.private-key = @file:/etc/alipay/sandbox-private-key.pem
pay.apps.2021000000000002.verify.0.type = key
pay.apps.2021000000000002.verify.0.value = @file:/etc/alipay/sandbox-alipay-public-key.pem
```

凭据取值支持两种形态：以 `@file:` 开头的文件引用（按 UTF-8 读取）与内联 PEM 文本（可含 `-----BEGIN/END-----`）。
**不支持** classpath 引用。已支持：私钥允许裸 Base64。

## 3. 配置键全表

### 3.1 全局键

| 键名 | 必填 | 默认值 | 合法值 | 说明 |
| --- | --- | --- | --- | --- |
| `pay.default-app-id` | 否 | 无 | 非空白字符串 | `appId` 参数为 `null` / 空白时取此值 |
| `pay.connect-timeout` | 否 | `5000` | 正整数（毫秒） | 连接超时 |
| `pay.read-timeout` | 否 | `15000` | 正整数（毫秒） | 读取超时（支付网关往返较长） |
| `pay.page-http-method` | 否 | `POST` | `GET` / `POST` | `POST` → `payloadType=FORM`；`GET` → `payloadType=URL` |
| `pay.notify-url` | 否 | 无 | 非空白字符串 | 全局默认异步回调地址 |
| `pay.return-url` | 否 | 无 | 非空白字符串 | 全局默认同步回调地址 |

### 3.2 单应用键（`<appId>` 为支付宝应用编号，`<n>` 为从 0 起的凭据序号）

| 键名 | 必填 | 默认值 | 合法值 | 说明 |
| --- | --- | --- | --- | --- |
| `pay.apps.<appId>.env` | **是** | 无 | `sandbox` / `production` | 环境标签；决定默认网关并校验网关一致性 |
| `pay.apps.<appId>.gateway-url` | 否 | 环境对应官方网关 | URL | 显式配置时 scheme 必须为 `https`，host 必须等于环境对应官方网关 host |
| `pay.apps.<appId>.sign-mode` | 否 | `cert` | `cert` / `key` | 签名方式 |
| `pay.apps.<appId>.private-key` | **是** | 无 | PEM 或 `@file:` | 应用私钥（RSA PKCS#8） |
| `pay.apps.<appId>.app-cert` | `cert` 模式必填 | 无 | PEM 或 `@file:` | 应用公钥证书（下单签名用） |
| `pay.apps.<appId>.root-cert` | `cert` 模式必填 | 无 | PEM 或 `@file:` | 支付宝根证书 |
| `pay.apps.<appId>.charset` | 否 | `UTF-8` | `UTF-8` | 内核仅支持 UTF-8，其它值启动失败 |
| `pay.apps.<appId>.sign-type` | 否 | `RSA2` | `RSA2` | 内核仅支持 RSA2，其它值启动失败 |
| `pay.apps.<appId>.verify.<n>.type` | **是**（至少 `n=0`） | 无 | `cert` / `key` | 验签凭据类型 |
| `pay.apps.<appId>.verify.<n>.value` | **是** | 无 | PEM 或 `@file:` | `cert` → 支付宝公钥证书；`key` → 支付宝公钥 |
| `pay.apps.<appId>.verify.<n>.cert-sn` | 否 | 自动计算 | 32 位小写 hex | 显式配置时必须与证书实际序列号一致 |

启动期校验（违反即 `AliPayException`，消息含键名，凭据类值一律显示为 `***（长度 n）`）：
至少一个应用；`env` 必填且合法；`gateway-url` 的 scheme 与 host 必须与环境一致；`cert` 模式下 `app-cert` /
`root-cert` 必填；`verify.<n>` 序号必须从 0 起连续；`type` 与 `value` 必须成对；`cert-sn` 必须与证书一致；
凭据文件必须可读且非空。

## 4. 五个交易动作

```java
@Inject
private AliPayClient pay;

// 1) 扫码预下单：载荷类型恒为 QRCODE
PayOrderResult qr = pay.precreate(appId, "ORDER-20260101-0001", 1L, "会员年费", null);
qr.payloadType();   // PayOrderResult.PayloadType.QRCODE
qr.payload();       // 二维码内容串，交给二维码渲染

// 2) H5 支付：载荷类型由 pay.page-http-method 决定（默认 POST → FORM）
PayOrderResult wap = pay.wapPay(appId, "ORDER-20260101-0002", 9900L, "会员年费",
        "https://example.com/pay/return", null);
wap.payloadType();  // FORM（自提交表单 HTML）或 URL（跳转地址）

// 3) PC 支付：与 H5 同构，渠道不同
PayOrderResult page = pay.pagePay(appId, "ORDER-20260101-0003", 19900L, "企业版年费",
        "https://example.com/pay/return", null);

// 4) 查单：不存在交易会抛 AliPayException 且 code() = ACQ.TRADE_NOT_EXIST
try {
    PayQueryResult query = pay.query(appId, "ORDER-20260101-0001");
    query.tradeStatus();    // 如 TRADE_SUCCESS
    query.totalAmount();    // 元字符串，如 "0.01"
} catch (AliPayException e) {
    if ("ACQ.TRADE_NOT_EXIST".equals(e.code())) {
        // 业务口径唯一：交易不存在
    }
}

// 5) 退款：outRequestNo 是幂等键，原样透传给支付宝
PayRefundResult refund = pay.refund(appId, "ORDER-20260101-0001", "REFUND-0001", 1L, "用户取消");
refund.outRequestNo();  // 回显 "REFUND-0001"
```

静态上下文（工具类、脚本）用门面取同一实例：

```java
AliPayClient pay = AliPayKit.client();   // 与 @Inject 注入的是同一个对象
```

多应用：`appId` 是每个交易方法的**第一参数**，传 `null` / 空白回退 `pay.default-app-id`，未配置的 appId 抛
`AliPayException`。每个 appId 使用各自独立的客户端实例，互不串扰；模块不使用线程绑定，也不使用静态多应用注册表。

## 5. 回调验签

异步通知（POST 表单）与同步跳转（GET query string）走同一条解析与验签路径：

```java
// 异步通知：Spring MVC 风格拿 form body 字符串，或自行构造成 Map
public String handleNotify(String appId, String formBody) {
    PayNotifyResult notify = AliPayNotifyUtil.verifyAndParse(appId, formBody);  // 失败抛 AliPayException
    String outTradeNo = notify.outTradeNo();      // 交易幂等键
    String tradeStatus = notify.tradeStatus();    // 如 TRADE_SUCCESS
    String totalAmount = notify.totalAmount();    // 元字符串
    boolean refundNotify = notify.type() == PayNotifyResult.NotifyType.REFUND;
    return "success";                             // 业务处理成功后回 "success"
}

// 需要布尔语义时
boolean ok = AliPayNotifyUtil.verify(appId, paramsMap);

// 同步跳转：把 query string 原样传入
PayNotifyResult sync = AliPayNotifyUtil.verifyAndParse(appId, request.getQueryString());
```

契约要点：

1. **纯函数**：不改入参 `Map`（内部先做防御性拷贝）、不写缓存，同一入参重复调用结果恒等。
2. 验签失败：`verify` 返回 `false`；`verifyAndParse` 抛 `AliPayException`（消息含 `verify failed` 语义与 appId，
   **不含报文内容与凭据**）。
3. 缺失 `sign` 视为失败；`sign_type` 仅支持 `RSA2`，其它值一律失败（不静默放过）。
4. 凭据轮换：同一 appId 下可配置多组 `verify.<n>`，**任一**通过即通过；证书模式且报文带 `alipay_cert_sn` 时优先
   选取序列号相等者，无匹配即失败。
5. **模块不做去重、不做状态机**：业务必须自行按 `outTradeNo` / `outRequestNo` + `tradeStatus` 做前置校验。

## 6. 证书模式说明

- 证书模式下单需要应用私钥、应用公钥证书、支付宝根证书；三者都以**内容**注入 SDK，无需落盘。
- 证书解析强依赖 BouncyCastle：`sign-mode=cert` 或验签凭据 `type=cert` 时，装配期即探测 `bcprov-jdk18on`，
  缺失会抛可读的 `AliPayException`（而不是 `NoClassDefFoundError`）。
- 证书序列号由模块用证书**内容**计算（`AntCertificationUtil.getCertSN(content)`）；`verify.<n>.cert-sn`
  若显式配置，必须与计算值一致，否则启动失败（防止配错证书）。
- 证书轮换：把新证书配置成 `verify.0`、旧证书保留为 `verify.1`，两者并存期间都能验签；确认无旧报文后再删除旧项。

## 7. 已知边界与限制

| 限制 | 说明 |
| --- | --- |
| **不做幂等** | 模块只透出 `out_trade_no` / `out_request_no`，**业务必须自行按幂等键去重**；模块不记录已处理通知 |
| **不做状态机** | 交易状态流转由业务维护，模块只透出 `tradeStatus` |
| **不做补单 / 轮询 / 送达补偿** | 主动查询与重试由业务调度系统负责 |
| **不做订单落库与对账** | 模块零 DB 依赖 |
| **`sign_type` 仅 RSA2** | 内核限制；`RSA`（SHA1）会被内核拒绝 |
| **`charset` 仅 UTF-8** | 内核限制；值为空的字段不参与签名串 |
| **证书模式缺 BouncyCastle 即失败** | 装配期探测并给可读异常 |
| **凭据与环境无法机械绑定** | 模块只能校验 `gateway-url` 的 host 与 `env` 一致；「这把私钥属于沙箱还是生产」需靠配置评审与凭据来源管理兜底 |
| **`stop()` 后已注入字段仍持旧实例** | 框架无注销 API；`AliPayKit.client()` 会立即失败，但已注入的字段不会自动置空 |
| **不做收银台页面 / Servlet 适配** | 模块只产出支付参数与跳转载荷，回调报文以 `String` / `Map` 入参 |
| **不做其它渠道** | 微信支付见 `aifei-weixin`；QQ / 京东 / 银联 / PayPal / Stripe 等不在范围内 |
| **同步跳转报文也可能为空** | 用户中途放弃支付时不会带回参数，业务需容错 |

异常分类边界：调用方参数写错（空白单号、`amountFen <= 0`、超长）抛 `IllegalArgumentException`；
配置非法 / 未装配 / 依赖缺失 / 内核失败 / 验签失败抛 `AliPayException`，`cause` 保留但不进入消息文本。

## 8. 依赖来源与许可声明

本模块以 Maven 构件方式依赖第三方内核，**不复制、不 fork、不改写**其源码，并保留其署名与许可：

| 构件 | 版本 | 许可 | 版权 |
| --- | --- | --- | --- |
| `com.github.javen205:IJPay-AliPay-V3` | `3.0.0` | Apache License 2.0 | Copyright (c) 2017 IJPay Authors |
| `com.alipay.sdk:alipay-sdk-java-v3` | `3.1.89.ALL` | Apache License 2.0 | Ant Open (alipay.com) |
| `org.bouncycastle:bcprov-jdk18on` | `1.86` | Bouncy Castle Licence（MIT 式） | The Legion of the Bouncy Castle |
| `org.dom4j:dom4j` | `2.1.5` | BSD 式 | MetaStuff / dom4j 贡献者 |

上游项目地址：`gitee.com/javen205/IJPay`。本模块自身的许可为 Apache License 2.0（见仓库根 `LICENSE`）。

## 9. 构建与验证

```bash
cd aifei-pay
mvn clean verify                                  # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest          # 架构门禁（10 例）
mvn test -Dtest=PayDepsBytecodeTest               # 依赖字节码断言
javap -verbose target/classes/cn/hg/aifei/pay/AliPayClient.class | grep major   # 断言 52
```

门禁依赖模块根的相对路径 `src/main/java`，因此**必须在 `aifei-pay/` 目录内执行**。

设计与实现契约详见 [`docs/design.md`](docs/design.md)；开发约束详见 [`AGENTS.md`](AGENTS.md)。
