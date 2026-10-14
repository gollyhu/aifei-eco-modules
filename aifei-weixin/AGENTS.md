# aifei-weixin 开发约束

本文件适用于 aifei-weixin 模块，供开发代理与项目贡献者使用。

## 文档分工

| 文件 | 面向 | 内容 |
| --- | --- | --- |
| `README.md` | 使用者 | 定位、依赖约束、接入示例、测试与门禁概览 |
| `docs/开发规范/微信接入规范.md` | 接入方 | 接入步骤、后台配置、上线检查清单（**接入的唯一规范来源**） |
| `docs/design.md` | 设计者与实现者 | 分层、契约、关键决策（**事实来源**） |
| `docs/testing.md` | 实现者 | 测试原则、用例清单、未覆盖项 |
| 本文件 | 开发代理与贡献者 | 改代码前必须遵守什么、怎么验证 |

## 开发前提

- 改代码前先读 `docs/design.md`；需求与现有设计冲突时，先更新设计决策，再改代码。
- 行为变化必须同步四处文档中的相关者：`README.md`（用法）、`docs/开发规范/微信接入规范.md`（接入要求）、`docs/design.md`（契约）、`docs/testing.md`（用例清单）。

## 硬性约束

### 技术栈

- 代码、依赖与 API 必须兼容 Java 8（`source` / `target` 均为 1.8），不得使用 Java 8 之后才有的 API。
- 唯一编译依赖是 `cn.aifei:aifei-all`，且必须保持 `provided`（不传递，消费方自带）。
- 不得引入 `aifei-vip-arch`、`undertow`、`jedis`、`okhttp`、`jackson`、`fastjson1`、`mysql`、`druid`。
- 加解密、签名、验签、GCM、mTLS 一律使用 JDK 自带 API，不引入第三方密码学库。

### 结构（分层不可打破）

- `cn.aifei.weixin.aifei.WeixinHandler` 是**唯一** `import cn.aifei.core.*` 的类（`KernelBindingTest` 强制）；`cn.aifei.log.Log` 仅允许出现在 `MsgKit` 与 `WeixinHandler`。
- 协议内核与接入层通过 `spi` 包通信：`spi` 包不得引用 `cn.aifei.*`，`WeixinIo` 的泛型保持无上界（验签参数一律走 `query`，不得改用内核 `Input.getStr(...)`）。
- `core.*`、`pay.*`、`miniapp`、`common` 不得引用 aifei 内核类型（含 HIO 与日志）；这些层需能在非 aifei 环境独立使用。日志门面只在内核入口与接入层使用。
- 协议内核（`core.encrypt` / `core.kit` / `core.msg`）不得感知 HTTP 与 aifei：不引用 `java.net.*`、不引用内核类型。
- 公共类型的新增改动须先在 `docs/design.md` 中登记其职责与契约。

### 契约（不得擅自变更）

- **响应体**：无被动回复、消息解析失败、未知消息类型、业务异常一律返回 `success`；验签失败与解密失败返回 `fail`。回调链路 HTTP 状态码恒 200。
- **5 秒限制**：回调链路禁止同步调用 Api。
- **幂等**：SDK 不做去重，`MsgHandler` 与支付回调的幂等由消费方承担——不得在 SDK 内引入隐式去重（会影响多实例语义）。
- **注册顺序**：`WeixinHandler` 必须注册在 `FrontHandler` 之前，该约束由消费方测试守护，SDK 侧不得通过在 `handle` 内做兜底渲染来"兼容"错误顺序。
- **敏感脱敏**：`WeixinAccount` / `MiniAppAccount` / `Code2Session` / `PayMerchant` 的 `toString` 不得输出 `secret` / `aesKey` / `apiV3Key` / 私钥 / `sessionKey`；新增值对象沿用该规则。
- **超时与重试**：`JdkHttpKit` 与 `JdkPayHttpKit` 默认连接 5000 ms、读取 10000 ms，并关闭重定向；token 失效（`40001` / `42001`）只允许重试**一次**；支付链路不自动重试。
- **token 语义**：缓存过期时间 = 现在 + `expires_in`（默认 7200 秒）− 5 分钟；缓存键为 `appId`；并发取 token 必须按 appId 分段锁单飞。
- **异步/同步边界**：`MsgKit` 不得捕获 `Error`，不得吞掉 `WxApiException` 之外的类型语义；异常降级只允许发生在 `MsgKit` 的既定分支上。

### 数据格式（发布后不得更改）

- `WxCrypt` 的明文布局（16 字节随机串 + 4 字节大端长度 + 消息体 + `appId`）、IV 取密钥前 16 字节、`AES/CBC/PKCS5Padding` 由 JDK 处理填充（**禁止手工补位或去填充**）。
- `WxConsts` 中全部协议字面量（响应体、消息/回复/事件类型、`EncodingAESKey` 长度 43）。
- 被动回复 XML 的节点顺序与嵌套结构（`OutMsgXmlBuilder` 中的渲染顺序）。
- 支付 V3 签名串格式（末尾带 `\n`、`canonicalUrl` 带前导 `/`）、`Authorization` 字段顺序、应答与回调验签串 `timestamp\nnonce\nbody\n`、`AUTH_SCHEME`。
- 平台证书路径 `v3/certificates`、`apiV3Key` 必须 32 字符、`AES/GCM/NoPadding` 且 tag 128 位。
- 缓存键约定：token / ticket 的键为 `appId`，`jsapi_ticket` 前缀 `jsapi_ticket:`。

上述数据格式改动会导致与微信报文或其他实例不互通，且均有测试向量钉住（见 `docs/testing.md`）。

### 唯一定义点（每类规则只有一处）

- **AES-256-CBC 加解密**：`core.encrypt.WxCrypt`（`MsgKit.encrypt/decrypt` 只做转发）。
- **SHA-1 签名与摘要**：`core.encrypt.Sha1Kit`（`MsgKit.checkSignature` 只做参数选择与比较）。
- **随机串**：`core.encrypt.RandomKit`。
- **XXE 安全解析特征**：`core.kit.XmlKit`（安全特性集中一处，不得在别处另建解析器）。
- **XML 文本转义与 CDATA 拆分**：`core.kit.XmlBuilder`。
- **接收消息 / 事件类型判定与解析**：`core.msg.InMsgParser` + `WxConsts.isCustomEvent` / `isVerifyEvent`。
- **被动回复渲染**：`core.msg.OutMsgXmlBuilder`。
- **业务分发**：`MsgHandler.process`（钩子顺序即类型判定顺序）。
- **协议字面量**：`WxConsts`（不得在业务类内散落 `"text"`、`"subscribe"` 等字符串）。
- **token 获取、注入、失效重试、并发单飞**：`core.api.ApiCaller`；各 `XxxApi` 只拼路径与报文。
- **HTTP 传输**：公众号 `core.http.JdkHttpKit`、支付 `pay.JdkPayHttpKit`；非 2xx 的处理策略分别固定在这两个类。
- **支付签名与 `Authorization`**：`pay.core.PaySignature`；**支付验签**：`pay.core.PayVerifier`；**GCM 解密**：`pay.core.AesGcm`；**平台证书**：`pay.core.PlatformCertManager`；**回调验签 + 解密编排**：`pay.PayNotifyKit`。
- **回调路由与 GET/POST 分流**：`WeixinHandler`（不得把分流下沉到 `MsgKit`）。

### 注释规范

- 语言一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、协议字面量与异常消息。
- 公共类型与公共成员必须写 javadoc 并写全 `@param` / `@return` / `@throws`；非自明处不得省略。
- 注释只在代码无法表达「为什么」时书写，不复述下一行代码，不写行尾注释，不留被注释掉的死代码。
- 类头部保留官方文档链接（`developers.weixin.qq.com` / `pay.weixin.qq.com`）的做法沿用，便于溯源。
- 文件头版权块：原创文件按「包名与版权」约束使用统一版权头，导入/复制文件保留原版权块；既有历史版权块不回溯改写。

## 工作流程

1. **读设计**：确认需求在 `docs/design.md` 中有依据；没有依据先补决策。
2. **改实现**：遵守上述定义点与冻结契约，不越出分层边界。
3. **补测试**：新增或改变公共行为时同步更新测试；触及协议格式时补固定向量用例。
4. **跑验证**：在模块根目录执行 `mvn clean test`，必须 0 failure / 0 error（`KernelBindingTest` 依赖相对路径 `src/main/java`，不得在仓库根执行本模块的测试）。
5. **同步文档**：按「开发前提」中的四处文档同步。

## 提交前检查清单

- [ ] `mvn clean test` 通过（0 failure / 0 error）。
- [ ] 未新增 `cn.aifei.core.*` 引用（除 `WeixinHandler`）、未新增 arch / undertow / sql 引用；`spi` 与 `core.*` / `pay.*` 仍未引用 aifei 内核。
- [ ] 未引入 Java 8 之后才有的 API 与新的第三方依赖。
- [ ] 协议数据格式（加解密布局、`WxConsts` 常量、签名串、XML 结构、缓存键前缀）未被改动；如确需改动，已先在 `docs/design.md` 记录决策并补齐固定向量用例。
- [ ] 响应体语义（`success` / `fail`）、重试策略、超时值未变；token 失效仍只重试一次。
- [ ] 新增值对象的 `toString` 未泄漏密钥类字段。
- [ ] 行为变化已按「开发前提」同步上述四处文档。
- [ ] 注释符合「注释规范」，文件头版权块未被改写。
