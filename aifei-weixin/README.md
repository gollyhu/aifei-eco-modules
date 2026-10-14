# aifei-weixin

微信开发包：公众号协议内核 + 开放接口客户端 + 小程序 + 微信支付 V3 + aifei 接入层。任意自带 `aifei-all` 的 aifei 项目引入即可用。

坐标：`io.github.gollyhu:aifei-weixin:1.0.0`

## 文档

各文档的定位与分工见 `AGENTS.md` 的「文档分工」表：

| 文档 | 内容 |
| --- | --- |
| `docs/开发规范/微信接入规范.md` | **接入前必读**：接入步骤、后台配置、上线检查清单 |
| `docs/design.md` | 分层设计、关键决策与数据格式契约 |
| `docs/testing.md` | 测试策略与用例清单 |
| `AGENTS.md` | 开发约束、文档分工与提交前检查清单 |

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

## 快速开始

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-weixin</artifactId>
    <version>1.0.0</version>
</dependency>
```

`aifei-all` 为 **`provided`**、不传递，应用须自行声明；编译依赖与禁用依赖清单见 `docs/design.md` 3.2 与 `AGENTS.md` 硬性约束。

```java
// 必须注册在 FrontHandler 之前，否则微信首验 GET 会被渲染成首页（详见 design.md 5.3）
settings.addHandler(new WeixinHandler<>(
        new MyWeixinIo(),
        WeixinAccountProvider.of(WeixinAccount.of(appId, secret, token, aesKey)),
        new MsgHandler() {
            protected OutMsg processInTextMsg(InTextMsg msg) { return new OutTextMsg("ok"); }
        }));

// 使用 Api 前装配一次，tokenCache 须复用同一实例
WxApiConfig.set(new JdkHttpKit(), new MemoryAccessTokenCache());
```

回调地址：`https://域名/weixin/{appId}/msg`。完整接入步骤（含后台配置与上线检查清单）见 `docs/开发规范/微信接入规范.md`；构建：`cd aifei-weixin && mvn clean install`（需先于依赖它的应用模块）。

## 关键契约

- 无被动回复必须回 `success`；验签 / 解密失败回 `fail`；回调链路 HTTP 恒 200。
- 微信要求 5 秒内响应：**回调链路禁止同步调用 Api**。
- **SDK 不做幂等去重**：消息按 `MsgId`、支付回调按 `out_trade_no` 由业务自行幂等；多实例部署须把 `AccessTokenCache` 换为共享实现。

契约的完整依据见 `docs/design.md` 4.5 与 `docs/开发规范/微信接入规范.md` 第 6 节；`WeixinHandler` 的注册顺序约束见 `docs/design.md` 5.3。

## 测试

```bash
cd aifei-weixin && mvn clean test      # 不联网、不连数据库
```

用例数量与分层见 `docs/testing.md`；架构门禁 `KernelBindingTest` 的断言见 `docs/testing.md` 4.1；应用侧注册顺序由文档约定 + 消费方自行保障（`WeixinHandler` 须注册在 `FrontHandler` 之前）。
