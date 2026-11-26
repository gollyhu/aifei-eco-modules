# aifei-pay 支付渠道配置

本文是 `aifei-pay` V2.0 的**渠道级配置事实来源**：列出各支付通道的键、示例与适配器状态。
模块级键（`pay.connect-timeout`、`pay.notify-url` 等）见 [`../README.md`](../README.md) §3。

## 1. 通道命名与装配规则

- 通道实例名写作 `pay.<name>.*`，`<name>` 不允许包含点号。
- `pay.<name>.type` 决定通道类型，合法值由 `PayTypes` 白名单定义。
- 所有通道都必须配置 `pay.<name>.default-merchant-id`。
- `pay.default-channel` 指向某个已声明的 `<name>`；只声明一个通道时可省略。
- 启动期只校验类型白名单、必填键与默认通道；配置错误抛 `PayException`，不留半装配态。
- 只有 `wxpay` 保持「已登记未落地」：可完成装配，交易动作抛 `PayException("支付通道适配器未落地：...")`；其余七渠道均为真实适配器。

## 2. 通道通用键

| 键名 | 必填 | 默认值 | 合法值 | 说明 |
| --- | --- | --- | --- | --- |
| `pay.<name>.type` | **是** | 无 | `alipay2` / `alipay3` / `wxpay` / `qq` / `jdpay` / `unionpay` / `paypal` / `stripe` / `fuiou` / `xpay` | 通道类型 |
| `pay.<name>.default-merchant-id` | **是** | 无 | 非空白字符串 | 未显式传商户 / 应用编号时取此值 |

> `type` 与 `default-merchant-id` 之外的键按通道类型解释；当前九个渠道（`alipay3` / `alipay2` 与 §3.3 的七个 IJPay 渠道）均已完整落地并具备读取与校验链，仅 `wxpay` 为已登记未落地。

## 3. 各支付渠道配置表

### 3.1 `alipay3` / `alipay2`（支付宝 V3 / V2，均已完整落地）

支付宝通道键会被映射为内部 `pay.apps.<default-merchant-id>.*` 配置域；以下键均可直接写在 `pay.<name>.` 下。

| 内核版本 | 本模块状态 | 依赖构件 | 通道类型 | 说明 |
| --- | --- | --- | --- | --- |
| V3 | **已落地** | `IJPay-AliPay-V3` + `alipay-sdk-java-v3` | `alipay3` | 下表键按 V3 SDK 解析 |
| V2 | **已落地** | `IJPay-AliPay` + `alipay-sdk-java` 4.x | `alipay2` | 同一套通道键映射到 V2 的 `serviceUrl` / `aliPayPublicKey` / 证书内容 |

两版本共用上表配置键与公共 API，但内核、验签核心、依赖构件与客户端缓存按版本隔离。
无版本含义的 `alipay` 不是合法类型；配置 `alipay` 会在启动时报非法类型。

| 键名 | 必填 | 默认值 | 合法值 | 说明 |
| --- | --- | --- | --- | --- |
| `pay.<name>.env` | **是** | 无 | `sandbox` / `production` | 环境标签，决定默认网关 |
| `pay.<name>.gateway-url` | 否 | 环境对应官方网关 | URL | 显式配置时 scheme 必须为 `https`，host 必须与环境对应官方网关一致 |
| `pay.<name>.sign-mode` | 否 | `cert` | `cert` / `key` | 签名方式 |
| `pay.<name>.private-key` | **是** | 无 | PEM / `@file:` / 裸 Base64 | 应用私钥（RSA PKCS#8） |
| `pay.<name>.app-cert` | `cert` 模式必填 | 无 | PEM / `@file:` | 应用公钥证书，下单签名用 |
| `pay.<name>.root-cert` | `cert` 模式必填 | 无 | PEM / `@file:` | 支付宝根证书 |
| `pay.<name>.charset` | 否 | `UTF-8` | `UTF-8` | 内核仅支持 UTF-8 |
| `pay.<name>.sign-type` | 否 | `RSA2` | `RSA2` | 内核仅支持 RSA2 |
| `pay.<name>.verify.<n>.type` | **是**（至少 `n=0`） | 无 | `cert` / `key` | 验签凭据类型 |
| `pay.<name>.verify.<n>.value` | **是** | 无 | PEM / `@file:` | `cert` → 支付宝公钥证书；`key` → 支付宝公钥 |
| `pay.<name>.verify.<n>.cert-sn` | 否 | 自动计算 | 32 位小写 hex | 显式配置时须与证书实际序列号一致 |

校验规则：

- `verify.<n>` 的序号必须从 `0` 起连续，`type` 与 `value` 必须成对出现。
- 凭据支持轮换：同一通道配置多组 `verify.<n>`，任一组验签通过即通过。
- 证书模式且报文携带 `alipay_cert_sn` 时，优先选用序列号匹配的凭据。
- 凭据值支持 `@file:绝对路径`，不支持 classpath 引用；私钥允许裸 Base64。

### 3.2 `wxpay`（微信支付，配置已登记，交易适配器待落地）

| 键名 | 必填 | 默认值 | 合法值 | 说明 |
| --- | --- | --- | --- | --- |
| `pay.<name>.type` | **是** | 无 | `wxpay` | 通道类型 |
| `pay.<name>.default-merchant-id` | **是** | 无 | 非空白字符串 | 商户号 / 应用标识 |
| 其它键 | 否 | 无 | — | 预留给 `IJPay-WxPay` 适配器，当前不读取、不校验、不生效 |

`wxpay` 与 `aifei-weixin` 的微信支付能力并存：用户自行选择其一；本模块源码零 `cn.aifei.weixin` import。
当前调用 `createOrder` / `query` / `refund` 会明确报「支付通道适配器未落地」。

### 3.3 其它 IJPay 渠道（均已落地）

七渠道均通过显式配置 API 构造请求，装配期不联网；调用期才发起真实网关请求。金额入参统一为分，
签名 / 金额格式由渠道适配器内部换算。`currency` 为七渠道通用键（默认 `CNY`）；`notify-url` /
`return-url` 可全局配置或按通道配置（显式入参优先），三者均由公共客户端 `ChannelPayClient` 读取。

| `type` | 依赖构件 | 必填键（除 `type` / `default-merchant-id`） | 可选键 | 说明 |
| --- | --- | --- | --- | --- |
| `qq` | `IJPay-QQ` | `app-id`、`partner-key` | `cert-path`（退款必填）、`cert-password`（退款用，默认空） | QQ 钱包，MD5 签名 `appid` / `mch_id` / `nonce_str` / `sign` |
| `jdpay` | `IJPay-JDPay` | `version`、`rsa-private-key`、`rsa-public-key`、`des-key` | `order-type` | 京东支付，`genReqXml` 生成加密报文，`rsa-public-key` 用于回调验签 |
| `unionpay` | `IJPay-UnionPay` | `gateway-url`、`api-key` | `version`、`service`、`h5-service`、`query-service`、`refund-service` | 银联，`UnionPayApi.execution` 显式网关，MD5 签名 |
| `paypal` | `IJPay-PayPal` | `client-id`、`secret` | `env`、`webhook-id`、`webhook-cert` | PayPal Orders V2；回调验签需预置平台证书内容（`webhook-cert`）与 `webhook-id` |
| `stripe` | `IJPay-Stripe` + `stripe-java` | `api-key` | `webhook-secret`（验签必填） | 显式 `StripeApiConfig` 构造客户端；回调用 `constructEvent` 验签 |
| `fuiou` | `IJPay-FuiYou` | `mchnt-key` | `order-type`、`term-id`、`term-ip` | 富友，`FuiYouPayCore.createSign` 按文档固定字段顺序签名 |
| `xpay` | `IJPay-XPay` | `app-id`、`api-key`、`server-url` | `api-type`（`wx` / `alipay`） | XPay，`PayApi.exePost` + `WxPayUrlEnum` / `AliPayUrlEnum` |

渠道特定键在 `pay.<name>.` 下书写，凭据支持 `@file:` 或内联文本。回调验签所需字段以 Map 传入
`PayNotifyUtil.verify`：PayPal 需要 `body` / `paypal-transmission-sig` / `paypal-transmission-id` /
`paypal-transmission-time`；Stripe 需要 `body` / `stripe-signature`；富友需要 `sign` / `mchnt_cd` /
`mchnt_order_no` / `settle_order_amt` / `order_amt` / `txn_fin_ts` / `reserved_fy_settle_dt` /
`random_str`；京东需要回调全字段（按字典序拼接、排除 `sign`）与 `sign`；QQ / 银联 / XPay 由
`WxPayKit.verifyNotify` 校验回调全字段（含 `sign`）。统一约定：`body` 键承载原始报文（Stripe / PayPal 验签必需）。

## 4. 配置示例

### 4.1 支付宝 V3 证书模式（含验签凭据轮换）

```properties
pay.default-channel = alipay-main
pay.connect-timeout = 5000
pay.read-timeout = 15000
pay.page-http-method = POST

pay.alipay-main.type = alipay3
pay.alipay-main.default-merchant-id = 2021000000000001
pay.alipay-main.env = production
pay.alipay-main.sign-mode = cert
pay.alipay-main.private-key = @file:/etc/pay/alipay-app-private.pem
pay.alipay-main.app-cert = @file:/etc/pay/alipay-app-cert.crt
pay.alipay-main.root-cert = @file:/etc/pay/alipay-root.crt
pay.alipay-main.verify.0.type = cert
pay.alipay-main.verify.0.value = @file:/etc/pay/alipay-public-cert-2026.crt
pay.alipay-main.verify.1.type = cert
pay.alipay-main.verify.1.value = @file:/etc/pay/alipay-public-cert-2025.crt
```

### 4.2 支付宝公钥模式（沙箱）

```properties
pay.alipay-sandbox.type = alipay2
pay.alipay-sandbox.default-merchant-id = 2021000000000002
pay.alipay-sandbox.env = sandbox
pay.alipay-sandbox.sign-mode = key
pay.alipay-sandbox.private-key = @file:/etc/pay/alipay-sandbox-private.pem
pay.alipay-sandbox.verify.0.type = key
pay.alipay-sandbox.verify.0.value = @file:/etc/pay/alipay-sandbox-public.pem
```

### 4.3 微信支付（最小可装配配置）

```properties
pay.wxpay-main.type = wxpay
pay.wxpay-main.default-merchant-id = 1900000001
```

### 4.4 多通道并存

```properties
pay.default-channel = alipay-main

pay.alipay-main.type = alipay3
pay.alipay-main.default-merchant-id = 2021000000000001
pay.alipay-main.env = production
pay.alipay-main.private-key = @file:/etc/pay/alipay-app-private.pem
pay.alipay-main.verify.0.type = key
pay.alipay-main.verify.0.value = @file:/etc/pay/alipay-public.pem

pay.alipay2-main.type = alipay2
pay.alipay2-main.default-merchant-id = 2021000000000003
pay.alipay2-main.env = production
pay.alipay2-main.private-key = @file:/etc/pay/alipay-v2-private.pem
pay.alipay2-main.verify.0.type = key
pay.alipay2-main.verify.0.value = @file:/etc/pay/alipay-v2-public.pem

pay.wxpay-main.type = wxpay
pay.wxpay-main.default-merchant-id = 1900000001

pay.stripe-main.type = stripe
pay.stripe-main.default-merchant-id = acct_000000000000
pay.stripe-main.api-key = @file:/etc/pay/stripe-secret.key
pay.stripe-main.webhook-secret = @file:/etc/pay/stripe-webhook.secret
```

## 5. 常见配置错误

| 报错消息 | 原因 | 处理 |
| --- | --- | --- |
| `支付配置缺失：pay.<name>.type` | 没有声明任何通道 | 至少配置一个 `pay.<name>.type` |
| `支付配置非法：pay.<name>.type=xxx` | type 不在白名单 | 改用 `PayTypes.supported()` 中的合法值 |
| `支付配置缺失：pay.<name>.default-merchant-id` | 缺少商户 / 应用编号 | 补齐该键 |
| `支付默认通道未登记：xxx` | `pay.default-channel` 指向未声明通道 | 改为已声明的 `<name>` |
| `支付配置缺失：pay.default-channel` | 声明了多个通道但未指定默认通道 | 补齐 `pay.default-channel` |
| `支付通道适配器未落地：wxpay（type=wxpay）` | 通道已装配但无交易适配器 | 使用已落地通道，或等待对应适配器补齐 |
