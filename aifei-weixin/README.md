# aifei-weixin

微信开发包：公众号协议内核 + 开放接口客户端 + 小程序 + 微信支付 V3 + aifei 接入层。任意自带 `aifei-all` 的 aifei 项目引入即可用。

坐标：`io.github.gollyhu:aifei-weixin:1.0.0`

规范：`docs/开发规范/微信接入规范.md`（唯一规范来源，接入前必读）

## 能力

| 层 | 内容 |
| --- | --- |
| 协议内核 | AES-256-CBC 加解密、SHA-1 签名、安全 XML 解析（禁 DOCTYPE / 外部实体）、XML 构建与转义 |
| 消息模型 | 接收消息 8 类、事件 10 类（9 个事件钩子）、被动回复 6 类，含解析器与渲染器 |
| 分发 | `MsgKit`（传输无关双入口：`verify` 首验 / `handle` 消息）、`MsgHandler`（模板方法，17 个钩子） |
| Api 客户端 | `ApiCaller` + `MenuApi` / `UserApi` / `MediaApi` / `TemplateMsgApi` / `OAuth2Api` / `QrcodeApi` / `JsTicketApi` |
| 小程序 | `MiniAppApi`：code2Session 登录 / 订阅消息 / 手机号 / 小程序码（复用公众号 token 链路） |
| 微信支付 V3 | `PayApi`（JSAPI 下单 / 查单 / 关单 / 退款）+ `PayNotifyKit`（回调验签 + AES-256-GCM 解密）+ `PlatformCertManager`（平台证书惰性刷新）；RSA / GCM / mTLS 纯 JDK 实现 |
| 接入层 | `WeixinIo`（传输 SPI）、`WeixinHandler`（aifei `Handler` 实现，唯一 import 内核的类） |

## 依赖约束（重要）

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-weixin</artifactId>
    <version>1.0.0</version>
</dependency>
```

- `aifei-all` 为 **`provided`**，不传递：**消费方必须自带 `aifei-all`**（`Handler`/`Input`/`Output`/`Log` 与 fastjson2 由它提供）。
- 本模块**不依赖** `aifei-vip-arch`、`undertow`、`jedis`、`okhttp`、`jackson`、`fastjson1`、`mysql`、`druid`。
- 构建：`cd aifei-weixin && mvn clean install`（需先于依赖它的应用模块）。

## 接入（5 行）

```java
// 1. 注册在 FrontHandler 之前（否则微信首验 GET 会被渲染首页）
settings.addHandler(new WeixinHandler<>(
        new MyWeixinIo(),                                              // 传输适配（4 个方法）
        WeixinAccountProvider.of(WeixinAccount.of(appId, secret, token, aesKey)),
        new MsgHandler() {                                             // 业务扩展点
            protected OutMsg processInTextMsg(InTextMsg msg) { return new OutTextMsg("ok"); }
        }));

// 2. 使用 Api 时装配一次，并复用同一 cache 实例
WxApiConfig.set(new JdkHttpKit(), new MemoryAccessTokenCache());
```

回调地址：`https://域名/weixin/{appId}/msg`。

## 关键契约

- 无被动回复时必须回 `success`（空串会被微信判定超时并重试 3 次）；验签失败回 `fail`；HTTP 恒 200。
- 微信要求 5 秒内响应：**回调链路禁止同步调用 Api**。
- 业务处理异常 → 记日志并回 `success`（避免微信重试）；消息解析/分发异常同理。
- 被动回复的 `ToUserName` / `FromUserName` 反转与 `CreateTime` 生成由 `MsgKit` 承担，业务不关心。
- 微信会重试导致重复投递，**SDK 不做幂等去重**，业务需自行用 `MsgId` 幂等。
- 多实例部署必须把 `AccessTokenCache` 换为共享实现（微信刷新 token 会挤掉旧 token 并有每日配额）。

## 测试

```bash
cd aifei-weixin && mvn clean test      # 109 个用例，不联网、不连数据库
```

架构门禁（自动化断言）：

- `KernelBindingTest`：无 `cn.aifei.vip.*` / `io.undertow.*` / `java.sql.*` 引用；`cn.aifei.core.*` 只出现在 `cn.aifei.weixin.aifei` 包。
- `WeixinHandlerOrderTest`（应用侧）：`WeixinHandler` 注册位于 `FrontHandler` 之前。

## 官方文档

- 接入概述：https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/Access_Overview.html
- 接收普通消息：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Receiving_standard_messages.html
- 接收事件推送：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Receiving_event_pushes.html
- 被动回复：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Passive_user_reply_message.html
- 消息加解密：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Message_Encryption_and_Decryption.html
- 小程序 code2Session：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
- 支付 V3 签名 / 验签：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/signature-verification.html
- 支付 V3 回调通知：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/payment-notice.html
- 获取 access_token：https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/Get_access_token.html
