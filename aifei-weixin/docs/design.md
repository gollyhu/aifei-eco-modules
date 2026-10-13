# aifei-weixin 设计文档

> 版本：1.0.0 ｜ 关联：`README.md`（接入用法）、`docs/开发规范/微信接入规范.md`（接入规范）、`docs/testing.md`（测试与用例清单）、`AGENTS.md`（开发约束）

## 1. 背景与目标

微信公众平台服务端开发需要自行落地一批协议细节：SHA-1 验签、AES-256-CBC 消息加解密、安全 XML 解析、`access_token` 生命周期、被动回复 XML 结构；微信支付 V3 还需要 RSA 签名与验签、AES-256-GCM 解密、平台证书轮换。这些细节分散在官方文档中，直接实现容易在编码、填充、签名串格式上出错。

`aifei-weixin` 把上述能力收敛为一个分层 SDK，供任意自带 `aifei-all` 的 aifei 应用引入。

设计目标：

| 目标 | 落地方式 |
| --- | --- |
| 依赖隔离 | 唯一编译依赖 `cn.aifei:aifei-all`（`provided`，由消费方自带）；不引入第三方 HTTP / JSON / 密码学库 |
| 传输无关 | 协议内核不感知 HTTP：回调链路经 `WeixinIo` SPI 适配，Api 链路经 `HttpKit` / `PayHttpKit` SPI 适配 |
| 业务零协议细节 | 业务只实现 `MsgHandler` 钩子（返回 `OutMsg`）与账号 / 缓存 SPI，不接触 XML、签名与加解密 |
| 纯 JDK 实现 | 加解密、签名、GCM、mTLS 全部使用 JDK 自带 API，且限定 Java 8 可用范围 |

## 2. 关键决策

| 决策 | 结论 | 事实依据 |
| --- | --- | --- |
| 协议内核 | 自研极简内核（XML / 签名 / 加解密 / 消息模型），不引入外部微信 SDK | 微信报文结构极简，自研可将依赖收敛到零；`XmlBuilder` 注释明确「不引入 XML 库」 |
| 业务扩展形态 | 钩子返回 `OutMsg` 而非直接 render | `MsgHandler` 注释：业务代码感知不到 HTTP，返回 `null` 表示不回复 |
| XXE 防护 | `XmlKit` 禁 DOCTYPE、禁外部实体、禁外部 DTD、禁 XInclude、禁实体展开 | 该类注释记录了 XXE 是同类实现的已知历史问题 |
| HTTP 实现 | `JdkHttpKit` 基于 `HttpURLConnection`，并抽 `HttpKit` 接口 | 接口注释：便于单测注入伪实现；线上零第三方依赖 |
| 支付 HTTP 与公众号 HTTP 分离 | 支付单独定义 `PayHttpKit` + `PayHttpResponse`，不复用 `HttpKit` | 支付需按状态码与响应体判别业务错误（非 2xx 不在传输层抛异常），且需注入 `SSLContext` 做 mTLS |
| 小程序 token | 复用公众号 `ApiCaller` 的 token 链路与缓存实例 | `MiniAppApi` 直接委托 `ApiCaller` 的 `appId`/`secret` 重载 |
| Api 层装配 | 静态门面 `WxApiConfig` / `WxPayConfig`，不使用 aifei `Prop` | `core.*` 与 `pay.*` 不依赖 aifei 内核，无法使用 `PropKit`；这两层需能在非 aifei 环境独立使用 |
| 回调接入方式 | 实现 aifei `Handler` 旁路（`WeixinHandler`），不注册拦截器 | 首验为 GET、消息为 POST，且需自行读 body、写 `text/xml`、结束交换 |

## 3. 总体架构

```
消费方应用（自带 aifei-all）
        │  settings.addHandler(new WeixinHandler<>(...))   ← 必须在 FrontHandler 之前
        ▼
接入层  cn.aifei.weixin.aifei   WeixinHandler            ← 唯一 import cn.aifei.core.* 的类
        │  ▲ WeixinIo（传输 SPI）  ▲ WeixinAccountProvider（账号 SPI）
        ▼
协议内核  cn.aifei.weixin   MsgKit / MsgHandler / WxConsts
        ├── core.encrypt   WxCrypt / Sha1Kit / RandomKit
        ├── core.kit       XmlKit / XmlBuilder
        └── core.msg       InMsg* / OutMsg* / InMsgParser / OutMsgXmlBuilder

客户端层（与接入层无依赖关系，可单独使用）
        ├── core.api / core.http / core.cache    ApiCaller + 各 XxxApi + HttpKit + AccessTokenCache
        ├── miniapp                              MiniAppApi（复用 core.api）
        └── pay / pay.core                       PayApi / PayNotifyKit / PaySignature / PlatformCertManager
```

- 包依赖方向单向：`aifei` 接入层 → 协议内核 / SPI；客户端层不反向依赖接入层。
- 根包 4 个类型：`MsgKit`（双入口）、`MsgHandler`（业务钩子）、`WxApiConfig`（Api 装配）、`WxConsts`（协议常量）。
- 公共支撑类型在 `common`：`ApiResult`（统一返回）、`WxApiException`（传输失败与未装配）。

### 3.1 架构约束（由门禁测试强制）

`KernelBindingTest` 对 `src/main/java` 做源码扫描，断言：

| 约束 | 说明 |
| --- | --- |
| 零 `cn.aifei.vip.*` / `io.undertow.*` 引用 | 不绑定具体 Web 容器与商业模块 |
| 零 `import java.sql.` | SDK 不持有任何数据源能力，持久化由消费方 SPI 承担 |
| `import cn.aifei.core.` 只出现在 `cn.aifei.weixin.aifei` 包 | 内核绑定收敛到单一接入层类 |
| 接入层包与 `WeixinHandler` 必须存在 | 防止「门禁假通过」（目录被清空时仍然绿灯） |

### 3.2 依赖设计

| 依赖 | scope | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei-all` | `provided` | 提供 `Handler` / `Input` / `Output` / `Log` 与 fastjson2；不传递，消费方自带 |
| `junit:junit` | `test` | 单元测试 |

明确不依赖：`aifei-vip-arch`、`undertow`、`jedis`、`okhttp`、`jackson`、`fastjson1`、`mysql`、`druid`。项目、依赖与 API 兼容 Java 8。

## 4. 协议内核

### 4.1 加解密 `WxCrypt`（AES-256-CBC）

| 项 | 事实 |
| --- | --- |
| 密钥 | `EncodingAESKey` 为 43 字符，补 `=` 后 Base64 解码得 32 字节；长度或 Base64 非法抛 `IllegalArgumentException` |
| IV | 取密钥前 16 字节 |
| 明文布局 | `16 字节随机串` + `4 字节大端消息长度` + `消息体` + `appId` |
| 填充 | 使用 `AES/CBC/PKCS5Padding` 由 JDK 自动补位 / 去填充；**禁止手工补位或去填充** |
| 解密校验 | 明文长度不足 20 字节、消息长度越界、`appId` 不一致均抛 `IllegalArgumentException` |
| 输出 | Base64 字符串 |

该布局由官方的微信服务端实现细节决定，属**冻结数据格式**：任何改动都会导致与微信报文不互通，由 `WxCryptTest` 中的密文向量与官方布局互通用例钉住。

### 4.2 签名 `Sha1Kit`

- `sortedSign(String... parts)`：参数按**字典序排序后直接拼接**取 SHA-1，输出 40 位小写十六进制。
- `sha1(String)`：不排序的原始摘要，用于 JS-SDK 等定序拼接场景。
- 明文模式签名参数为 `token, timestamp, nonce`；安全模式为 `token, timestamp, nonce, encrypt`。

### 4.3 安全 XML

`XmlKit.parse(String)`：返回根节点下「直接子节点名 → 文本」的**有序**映射（`LinkedHashMap`，同名节点保留首个），XML 为空或非法时抛 `IllegalArgumentException`。解析器固定启用：

- `disallow-doctype-decl=true`（命中 DOCTYPE 即失败）
- 外部普通实体 / 外部参数实体 / 外部 DTD 全部禁用
- `setXIncludeAware(false)`、`setExpandEntityReferences(false)`
- `ACCESS_EXTERNAL_DTD` / `ACCESS_EXTERNAL_SCHEMA` 置空（老版本 JDK 不支持该属性时忽略）

`XmlBuilder`：极简构建器。<br>
`text(name, value)` 做实体转义（`& < > " '`），`cdata(name, value)` 将内容中的 `]]>` 拆分为 `]]]]><![CDATA[>`；`value` 为 `null` 时不输出节点。业务内容因此无法破坏报文结构。

### 4.4 消息模型与解析

| 类别 | 数量 | 类型 |
| --- | --- | --- |
| 接收消息 | 8 | `text` / `image` / `voice` / `video` / `shortvideo` / `location` / `link` / `speech_recognition` |
| 接收事件 | 10 | `subscribe` / `unsubscribe` / `SCAN` / `LOCATION` / `CLICK` / `VIEW` / `TEMPLATESENDJOBFINISH` / `MASSSENDJOBFINISH` / 客服会话（3 种） / 资质认证（4 种） |
| 被动回复 | 6 | 文本 / 图片 / 语音 / 视频 / 音乐 / 图文（多篇） |

- 基类：`InMsg`（`ToUserName` / `FromUserName` / `CreateTime` / `MsgType` / `MsgId`）与 `InEvent`（追加 `Event` / `EventKey`）；被动回复基类 `OutMsg`（`ToUserName` / `FromUserName` / `CreateTime` / `MsgType`），图文条目为 `News`。
- `InMsgParser.parse`：`MsgType` 缺失或未知类型返回 `null`（由调用方响应 `success`）；CLICK 与 VIEW 归并到 `InMenuEvent`；客服会话与资质认证由 `WxConsts.isCustomEvent` / `isVerifyEvent` 判定；`CreateTime` / `MsgId` / 计数字段解析失败按 `0` 处理。
- `OutMsgXmlBuilder.build`：按类型渲染明文 XML；图文的 `ArticleCount` 由 `articles.size()` 决定。

### 4.5 双入口 `MsgKit`（传输无关）

| 入口 | 语义 |
| --- | --- |
| `verify(io, in, account)` | GET URL 首验。参数缺失、签名失败、`echostr` 解密失败均返回 `fail`；明文模式返回 `echostr` 原文，安全模式返回解密结果 |
| `handle(io, in, account, handler)` | POST 消息链路：验签 →（解密）→ 解析 → 分发 → 渲染 →（加密）→ 返回响应体字符串 |

关键契约：

- **安全模式判定**：请求携带 `encrypt_type=aes` 或账号配置了 `aesKey` 即为安全模式。
- **首验签名兼容**：安全模式依次尝试 `msg_signature(含 echostr)`、`signature(不含 echostr)`、`signature(含 echostr)` 三种组合，兼容微信不同版本的探测行为。
- **响应体**：验签失败与解密失败返回 `fail`；消息解析失败、未知消息类型、业务处理异常、业务返回 `null` 一律返回 `success`。空响应会让微信判定超时并重试 3 次，因此「不回复」必须显式回 `success`。
- **被动回复反转**：`ToUserName` / `FromUserName` 取反、`CreateTime` 填充当前秒级时间戳，均由 `MsgKit` 统一承担，业务不感知。
- **安全模式回复**：输出 `Encrypt` + `MsgSignature` + `TimeStamp` + `Nonce`、`Nonce` 出自 `RandomKit`；账号配置了 `aesKey` 时，即使请求为明文，回复同样加密。
- HTTP 状态码恒 200，语义由响应体（`success` / `fail`）承载。

### 4.6 业务扩展点 `MsgHandler`

模板方法 `process(InMsg)` 按类型分发到 17 个 `protected` 钩子（8 个消息钩子 + 9 个事件钩子，CLICK 与 VIEW 共用 `processInMenuEvent`）。全部钩子默认返回 `null`（不回复），业务按需覆写。

## 5. 接入层（`cn.aifei.weixin.aifei` 与 `spi`）

### 5.1 传输 SPI `WeixinIo<I>`

| 方法 | 契约 |
| --- | --- |
| `query(I in, String name)` | 取 URL 查询参数，无则返回 `null` |
| `body(I in)` | 读取原始请求体（UTF-8） |
| `isGet(I in)` | `true` = URL 首验，`false` = 消息回调 |
| `replyXml(I in, String xml)` | 以 `text/xml` 写回并**结束交换**；`xml` 为 `success` 表示无被动回复 |

该接口泛型无上界、包内不引用 aifei 内核类型，因此验签参数统一走 `query` 而非内核的 `Input.getStr(...)`。

### 5.2 账号 SPI

| 类型 | 契约 |
| --- | --- |
| `WeixinAccount` | 不可变值对象；`of(appId, secret, token, aesKey)`，`aesKey` 为空即明文模式，非空必须为 43 字符否则抛 `IllegalArgumentException`；`secret` 允许为 `null`；`toString` 只输出 `appId` 与是否安全模式 |
| `WeixinAccountProvider` | `get(appId)` 返回 `null` 表示未知 appId（回调被拒）；`of(accounts...)` 提供内存实现 |
| `MiniAppAccount` / `MiniAppAccountProvider` | 小程序账号（仅 `appId` + `secret`），形态与公众号账号对齐 |
| `PlatformKeySource` | `bySerial(serialNo)` 返回验签公钥，未知序列号返回 `null` |

### 5.3 `WeixinHandler<I extends Input, O extends Output>`

| 关注点 | 行为 |
| --- | --- |
| 前缀 | 默认 `/weixin`，可自定义；构造时归一化（trim、去尾斜杠、无前导 `/` 补上），空值抛异常 |
| 路由 | 路径不满足 `prefix + "/"` 前缀时交给 `next.handle(...)` 后返回 |
| appId 解析 | 取前缀后的第一段（`/{prefix}/{appId}/...`）；`accountId` 为 `protected`，可按子域 / 租户覆写 |
| 未知 appId | 记 warn 日志并回 `fail`，不进入 `MsgKit` |
| 分流 | `io.isGet(in)` 决定走 `MsgKit.verify` 还是 `MsgKit.handle` |
| 闭环 | 匹配到微信路径即自行读 body、写 `text/xml`、结束交换，**不再调用 `next`** |
| 构造校验 | `io` / `accountProvider` / `msgHandler` 均要求非 `null` |

回调地址：`https://域名/weixin/{appId}/msg`。

**注册顺序为冻结契约**：必须注册在 `FrontHandler` 之前，否则首验 GET 会被渲染成首页 HTML 而导致对接失败；由消费方的 `WeixinHandlerOrderTest` 守护。

## 6. 公众号 Api 客户端

### 6.1 `ApiCaller`（内部执行器）

| 能力 | 事实 |
| --- | --- |
| token 获取 | `GET cgi-bin/token?grant_type=client_credential&appid=..&secret=..` |
| 提前过期 | 缓存过期时间 = 现在 + `expires_in`（默认 7200）秒 − 5 分钟 |
| 并发单飞 | 按 appId 分段锁（`ConcurrentHashMap` + `synchronized`），锁内二次检查缓存，保证并发首调只请求一次 token |
| token 注入 | 业务请求统一追加 `access_token` 查询参数 |
| 失效重试 | 响应为 `40001`（无效 token）或 `42001`（token 超时）时清缓存并重试**恰好一次** |
| 二进制响应 | 下载类响应仅在「可解析为 token 失效 JSON」时重试，真二进制不误判 |
| 免 token 通道 | `requestWithoutToken` 供 OAuth2 等自带凭据的接口使用 |

### 6.2 token 缓存 `AccessTokenCache`

| 方法 | 契约 |
| --- | --- |
| `get(key)` | 未命中或已过期返回 `null` |
| `set(key, token, expireAtMillis)` | 写入并设定绝对过期时间 |
| `remove(key)` | 清除（token 失效重试时调用） |

`MemoryAccessTokenCache` 基于 `ConcurrentHashMap`，读取时判定过期即清除。**生产多实例必须替换为共享实现**：微信在获取新 token 时会使旧 token 失效，且取 token 有每日配额。

`JsTicketApi` 复用同一缓存实例，键为 `jsapi_ticket:{appId}`（不新增第二套缓存接口），命中时构造合成 `ApiResult` 返回。

### 6.3 HTTP SPI

| 类型 | 契约 |
| --- | --- |
| `HttpKit` | `get` / `post` / `postMultipart` / `download` / `postDownload`（默认抛 `UnsupportedOperationException`） |
| `JdkHttpKit` | 默认连接 / 读取超时 5000 / 10000 ms；关闭重定向；POST 使用 `application/json; charset=utf-8`；multipart 手拼边界；非 2xx 读取 `errorStream` 并抛 `WxApiException`；所有传输异常统一包装为 `WxApiException` |

### 6.4 Api 类清单

| 类 | 能力 |
| --- | --- |
| `MenuApi` | 自定义菜单 create / get / delete |
| `UserApi` | 用户信息、粉丝列表（`lang` 默认 `zh_CN`） |
| `MediaApi` | 临时素材上传 / 下载、永久素材与永久图文新增 |
| `TemplateMsgApi` | 模板消息发送 |
| `OAuth2Api` | 网页授权：授权 URL、换取 access_token、用户信息、刷新 token（自带 `appid`/`secret`，不注入 token） |
| `QrcodeApi` | 创建二维码 ticket、展示二维码 URL、长链转短链 |
| `JsTicketApi` | JS-SDK ticket 获取与前端签名 |

### 6.5 装配 `WxApiConfig`

```java
WxApiConfig.set(new JdkHttpKit(), new MemoryAccessTokenCache());   // 使用 Api 前装配一次
```

- 静态 volatile 字段，`set` 可重复调用（以最后一次为准，主要供测试隔离），未装配即取用抛 `WxApiException`（避免隐式 NPE）。
- `AccessTokenCache` 必须复用同一实例，否则缓存失效并额外消耗取 token 配额。

## 7. 小程序

| 方法 | HTTP | 路径 | token |
| --- | --- | --- | --- |
| `code2Session(account, code)` | GET | `sns/jscode2session` | 无（自带 appid / secret / js_code） |
| `subscribeSend(account, payload)` | POST | `cgi-bin/message/subscribe/send` | 有 |
| `getUserPhoneNumber(account, code)` | POST | `wxa/business/getuserphonenumber` | 有 |
| `getWxacodeUnlimit(account, payload)` | POST | `wxa/getwxacodeunlimit` | 有（下载二进制，失败时微信以 JSON `errcode` 返回，原样交调用方判别） |

`Code2Session` 为结果值对象：`isSucceed()` 为假或缺少 `openid` / `session_key` 时抛 `WxApiException`；`unionid` 未绑定开放平台时为 `null`；`toString` 不输出 `sessionKey`（禁止落日志）。

## 8. 微信支付 V3

### 8.1 `PayApi`

| 方法 | HTTP | 路径 |
| --- | --- | --- |
| `jsapi(...)` | POST | `v3/pay/transactions/jsapi` |
| `queryByOutTradeNo(...)` | GET | `v3/pay/transactions/out-trade-no/{no}?mchid=` |
| `close(...)` | POST | `v3/pay/transactions/out-trade-no/{no}/close` |
| `refund(...)` | POST | `v3/refund/domestic/refunds` |
| `buildJsapiPayParams(...)` | — | 委托 `PaySignature` 做 JSAPI 二次签名 |

- 每次请求即时生成时间戳与随机串，方法线程安全。
- 非 2xx 抛 `WxApiException`，错误信息取自响应体的 `code` / `message`；响应体非 JSON 时降级为原文（截断至 200 字符）。

### 8.2 签名与验签

| 项 | 规则 |
| --- | --- |
| 请求签名串 | `method\ncanonicalUrl\ntimestamp\nnonce\nbody\n`（末尾带 `\n`，`canonicalUrl` 必须带前导 `/`） |
| 算法 | `SHA256withRSA`，Base64 输出 |
| `Authorization` | `WECHATPAY2-SHA256-RSA2048 mchid="..",nonce_str="..",timestamp="..",serial_no="..",signature=".."` |
| 应答 / 回调验签串 | `timestamp\nnonce\nbody\n` |
| 验签失败 | `PayVerifier.verify` 返回 `false`（参数缺失、序列号未知、异常均归为 `false`） |
| JSAPI 二次签名串 | `appId\ntimeStamp\nnonceStr\nprepay_id={prepayId}\n` |

上述签名串格式由微信支付 V3 协议决定，属**冻结契约**，由 `PaySignatureTest` / `PayNotifyKitTest` 的官方 message 与互验用例钉住。

### 8.3 回调 `PayNotifyKit`

1. `verify(headers, body)`：从头中取 `Wechatpay-Timestamp` / `Wechatpay-Nonce` / `Wechatpay-Signature` / `Wechatpay-Serial`（头名大小写不敏感），缺任一项返回 `false`。
2. `decryptResource(merchant, body)`：校验报文为 JSON 且含 `resource.ciphertext`，用 `apiV3Key` 做 AES-256-GCM 解密后解析为 `JSONObject`。
3. `verifyAndDecrypt(...)`：先验签，失败抛 `WxApiException`，成功则解密。

SDK **不做幂等去重、不感知租户**：微信会重复投递，业务须自行按 `out_trade_no` 等业务键幂等。

### 8.4 平台证书 `PlatformCertManager`

| 维度 | 策略 |
| --- | --- |
| 缓存 | `ConcurrentHashMap<serialNo, {PublicKey, expireAtMillis}>` |
| 惰性刷新 | `bySerial` 未命中时触发一次 `refresh()`，再查一次 |
| 刷新节流 | 距上次刷新不足 60 秒且缓存非空则跳过（防止未知序列号反复触发下载） |
| 并发 | `refresh()` 为 `synchronized`，多实例各自持有、无需共享 |
| 证书下载 | `GET v3/certificates`，带 `Authorization` 头；非 2xx 抛 `WxApiException` |
| 解密 | 每个 `encrypt_certificate` 用 `apiV3Key` 做 AesGcm 解密得到 PEM |
| 过期 | `expire_time` 解析后提前 5 分钟失效；缺省时按 12 小时兜底 |
| 公钥解析 | 支持 X.509 证书 PEM 与公钥 PEM 两种格式 |

`AesGcm` 固定使用 `AES/GCM/NoPadding`、tag 128 位，`associated_data` 非空时 `updateAAD`，失败抛 `WxApiException`。

### 8.5 商户凭据与 HTTP

- `PayMerchant.of(mchId, privateKey, serialNo, apiV3Key)`：`apiV3Key` 必须为 32 字符，`privateKey` 非空；`ofPem` 支持带 / 不带 PEM 头的 PKCS#8 私钥；`toString` 不输出私钥与 `apiV3Key`。
- `JdkPayHttpKit`：默认 5000 / 10000 ms，关闭重定向；可注入 `SSLContext` 实现 mTLS（客户端证书由调用方构造）；非 2xx 不抛异常，交由调用方按 `PayHttpResponse` 的状态码与响应体处理。
- 装配：`WxPayConfig.set(new JdkPayHttpKit(sslContext), new PlatformCertManager(merchant))`，未装配即取用抛 `WxApiException`。

## 9. 协议常量

`WxConsts` 集中定义协议字面量，改动会影响与微信的互通，属**冻结数据格式**：

| 分类 | 值 |
| --- | --- |
| 响应体 | `RESP_SUCCESS = "success"`、`RESP_FAIL = "fail"` |
| AESKey 长度 | `ENCODING_AES_KEY_LENGTH = 43` |
| 消息类型 | `text` / `image` / `voice` / `video` / `shortvideo` / `location` / `link` / `speech_recognition` / `event` |
| 回复类型 | `text` / `image` / `voice` / `video` / `music` / `news` |
| 事件类型 | `subscribe` / `unsubscribe` / `SCAN` / `LOCATION` / `CLICK` / `VIEW` / `TEMPLATESENDJOBFINISH` / `MASSSENDJOBFINISH` / 客服会话 3 种 / 资质认证 4 种 |
| 加密类型 | `aes` |

## 10. 风险与规避

| 风险 | 规避 |
| --- | --- |
| 无被动回复回空串 → 微信判定超时并重试 3 次 | 一律回 `success`；`MsgKit` 对所有异常分支兜底 `success` |
| 回调链路耗时超过微信的 5 秒限制 | 回调链路禁止同步调用 Api；耗时业务转异步处理 |
| 多实例 token 相互挤掉、配额耗尽 | `AccessTokenCache` 可替换为共享实现；SDK 已按 appId 单飞避免同实例内重复取 token |
| 重复投递 | 文档明确 SDK 不做幂等，业务按 `MsgId` / `out_trade_no` 自行幂等 |
| XXE 注入 | `XmlKit` 禁 DOCTYPE、外部实体与外部 DTD |
| 业务内容破坏 XML 结构 | 文本节点统一实体转义；CDATA 内的 `]]>` 自动拆分 |
| 手工处理 PKCS5 填充导致中文消息尾部损坏 | 填充完全交给 JDK，禁止手工补位 / 去填充 |
| 平台证书轮换期间验签失败 | 未知序列号触发惰性刷新 + 60 秒节流；过期时间提前 5 分钟 |
| 敏感信息泄漏到日志 | `MiniAppAccount` / `Code2Session` / `PayMerchant` / `WeixinAccount` 的 `toString` 均不含密钥类字段 |
| 注册顺序错误导致首验被渲染成首页 | 契约 + `WeixinHandlerOrderTest` 双保险 |
