# aifei-weixin 测试文档

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

## 1. 测试原则

- 用例**不联网、不连数据库、不启持久 HTTP 服务**，可在任意环境离线执行。
- Api 链路通过注入伪 `HttpKit` / `PayHttpKit` 驱动，回调链路通过伪 `WeixinIo` 直接驱动 `WeixinHandler`，不经过真实网络与容器。
- 密码学与协议格式用**固定向量 / 官方报文**钉住：加密结果可与按官方 AES-CBC 布局手工构造的密文互通，签名可被同对公钥验证。
- 架构约束交给门禁测试自动断言，不依赖人工 review。
- 日志系统在测试中替换为空实现，避免依赖真实日志后端。

## 2. 测试分层

共 18 个测试类、109 个用例（另有 7 个测试辅助类，不含用例）。

| 层次 | 测试类 | 用例数 |
| --- | --- | --- |
| 架构门禁 | `KernelBindingTest` | 4 |
| 协议内核（双入口） | `MsgKitTest` | 11 |
| 协议内核（加密 / 签名） | `WxCryptTest` | 7 |
| 协议内核（消息解析） | `InMsgParserTest` | 5 |
| 协议内核（模板分发） | `MsgHandlerDispatchTest` | 2 |
| 协议内核（XML 构建与安全解析） | `OutMsgXmlBuilderTest` / `XmlKitTest` | 4 / 4 |
| 协议内核（账号 SPI） | `WeixinAccountTest` | 4 |
| 接入层 | `WeixinHandlerTest` | 7 |
| Api 客户端 | `ApiCallerTest` | 22 |
| Api 客户端（HTTP 实现） | `JdkHttpKitTest` | 4 |
| 小程序 | `MiniAppApiTest` | 9 |
| 支付（下单 / 查单 / 关单 / 退款） | `PayApiTest` | 6 |
| 支付（商户凭据） | `PayMerchantTest` | 4 |
| 支付（签名） | `PaySignatureTest` | 3 |
| 支付（回调） | `PayNotifyKitTest` | 6 |
| 支付（平台证书） | `PlatformCertManagerTest` | 4 |
| 支付（GCM） | `AesGcmTest` | 3 |

## 3. 测试替身

| 辅助类 | 作用 |
| --- | --- |
| `support.FakeWeixinIo` | 伪 `WeixinIo`：`params` 供 `query`、`body` 供 `body`、`getRequest` 供 `isGet`；`replyXml` 记录结果并累加回复次数 |
| `core.http.FakeHttpKit` | 伪公众号 `HttpKit`：记录 GET / POST / multipart / download 的 URL 与 body，按 `Supplier` 定制响应；对 `/cgi-bin/token` 单独计数并返回 `tokenResponse` |
| `support.FakePayHttpKit` | 伪 `PayHttpKit`：记录 URL、请求头与 body，直接返回预设的 `PayHttpResponse` |
| `support.PayTestKeys` | 支付素材工厂：RSA 密钥对、PKCS#8 / X.509 PEM 生成、用 `AES/GCM/NoPadding` + `GCMParameterSpec(128, nonce)` 构造官方形态密文 |
| `support.NoopLog` / `NoopLogFactory` / `TestLogs` | 空日志实现与工厂；`TestLogs.install()` 幂等地把 `LogFactory` 替换为 `NoopLogFactory` |

## 4. 用例清单

### 4.1 架构门禁 `KernelBindingTest`

对 `src/main/java` 下全部 `.java` 做源码扫描（依赖相对路径，必须在模块根目录执行）：

| 用例 | 断言 |
| --- | --- |
| `noArchOrUndertowReference` | 源码不含 `cn.aifei.vip.` / `io.undertow.` |
| `noSqlReference` | 源码不含 `import java.sql.` |
| `kernelCoreImportOnlyInAifeiPackage` | 含 `import cn.aifei.core.` 的文件路径必位于 `cn/aifei/weixin/aifei/` |
| `aifeiBridgePackageExists` | 接入层目录存在且其中含 `WeixinHandler.java` |

门禁自带前置校验：源码目录必须存在且扫描结果非空。

### 4.2 协议内核

`MsgKitTest`（静态块装配空日志）：

| 用例 | 断言 |
| --- | --- |
| `verifyPlainModeReturnsRawEchostr` | 明文 GET 签名正确 → 返回 `echostr` 原文 |
| `verifyRejectsWrongSignature` | 签名错误 → `fail` |
| `verifyFailsWhenParamsMissing` | 缺 `timestamp` / `nonce` → `fail` |
| `verifySafeModeReturnsDecryptedEchostr` | 安全模式下 `signature` 与含密文的 `msg_signature` 均返回解密后的 `echostr` |
| `handlePlainTextReversesUserNamesAndFillsCreateTime` | 回包 `ToUserName` / `FromUserName` 反转，`Content` 为钩子返回值，`CreateTime` 为当前秒级时间戳 |
| `handleReturnsSuccessWhenNoReplyOrUnknownType` | 钩子返回 `null` 或消息类型未知 → `success` |
| `handleEscapesHandlerContent` | 含 `& < > ]]>` 的回复内容被安全转义 |
| `handleRejectsInvalidSignatureAndMalformedXml` | 签名错误 → `fail`；畸形 XML → `success` 空回 |
| `handleSafeModeDecryptsRequestAndEncryptsReply` | 安全模式回包 `MsgSignature` 可验签，解密后 `Content` 正确 |
| `handleSafeModeRejectsTamperedRequest` | `token` 不匹配 → `fail` |
| `handleSafeModeAccountEncryptsReplyEvenForPlainRequest` | 账号配置了 `aesKey` 时，明文请求的回复仍加密 |

`WxCryptTest`：

| 用例 | 断言 |
| --- | --- |
| `sha1MatchesNistVectorAndSortsParts` | `sha1("abc")` 等于 NIST 向量；`sortedSign` 与手工排序拼接结果一致 |
| `signatureVectorPlainAndEncrypted` | 明文与含密文两种签名向量均匹配 |
| `decryptFrozenVector` | 冻结密文解密为预期明文（保护数据格式不漂移） |
| `encryptDecryptRoundTripWithEdgeChars` | 边界字符、emoji、`]]>`、空串加解密回原文 |
| `interopWithOfficialLayout` | 测试侧按官方 AES-CBC 布局构造的密文可被解密 |
| `appIdMismatchRejected` | `appId` 不一致 → 异常 |
| `aesKeyLengthValidated` | 合法 key 解码为 32 字节；非法长度 → 异常 |

| 类 | 用例 | 断言 |
| --- | --- | --- |
| `InMsgParserTest` | `parsesTextMessageWithCdataAndEntity` | CDATA 与实体解码为原文，字段完整 |
| | `parsesImageVoiceVideoShortVideo` | 图片 / 语音（含 `MediaId16K`）/ 视频 / 小视频字段正确 |
| | `parsesLocationLinkAndSpeechRecognition` | 位置 / 链接 / 语音识别字段正确 |
| | `parsesAllEventTypes` | 关注、取关、扫码、LOCATION、菜单 CLICK 与 VIEW、模板、群发统计、客服、资质认证成功与失败全部命中 |
| | `returnsNullForUnknownMsgTypeOrEvent` | 未知类型 / 未知事件 / 缺 `MsgType` → `null` |
| `MsgHandlerDispatchTest` | `dispatchesAllSeventeenHooks` | 17 个钩子逐一命中且分发顺序固定 |
| | `unhandledTypeReturnsNull` | 裸 `InMsg` → `null` |
| `OutMsgXmlBuilderTest` | `rendersTextReplyInExactOrder` | 文本回复字段顺序精确匹配 |
| | `rendersNestedMediaAndNewsReplies` | 图片与多篇图文（`ArticleCount`）嵌套渲染正确 |
| | `rendersMusicAndVideoReplies` | 音乐 / 视频 / 语音渲染正确 |
| | `escapesInjectionPayload` | 注入载荷被转义且可被 `XmlKit` 还原 |
| `XmlKitTest` | `parsesCdataEntityAndEmptyNode` | CDATA / 实体 / 空节点 / 嵌套取值解析正确 |
| | `rejectsDoctypeToBlockXxe` | DOCTYPE 与外部实体被拒（异常含「XML 解析失败」） |
| | `rejectsBlankAndMalformedInput` | 空串与畸形 XML 抛异常 |
| | `xmlBuilderEscapesEverything` | 转义齐全、CDATA 中 `]]>` 正确拆分、`text` 支持数值拼接 |
| `WeixinAccountTest` | `failFastOnInvalidArguments` | `appId` / `token` 空白、`aesKey` 非 43 字符 → 异常 |
| | `safeModeDependsOnAesKey` | 无 `aesKey` → 明文模式，有 → 安全模式 |
| | `trimsAppIdAndToken` | `appId` / `token` 自动 trim |
| | `providerReturnsNullForUnknownAppId` | 已知返回同一实例，未知与 `null` → `null` |

### 4.3 接入层 `WeixinHandlerTest`

以 `FakeWeixinIo` + 内存账号表直接驱动 `handle(...)`：

| 用例 | 断言 |
| --- | --- |
| `getFirstVerificationReturnsEchostr` | GET 首验回 `echostr` |
| `getFirstVerificationFailsOnBadSignature` | 签名错误 → `fail` |
| `unknownAppIdIsRejectedWithFail` | 未知 appId → `fail` |
| `postTextMessageRepliesParsableXml` | POST 文本回包可解析，`Content` / `ToUserName` / `FromUserName` 正确 |
| `nonWeixinPathDelegatesToNextHandler` | 非微信路径交给 `next`，自身不回复 |
| `prefixBoundaryFallsThrough` | `/weixin`、`/weixinx/...` 均不匹配（前缀边界判定） |
| `customPrefixAndAccountIdOverrideAreSupported` | 自定义前缀 + 覆写 `accountId` 生效 |

### 4.4 Api 客户端

`ApiCallerTest`（22 个用例）覆盖：

- token 注入、缓存命中不重复请求、过期重新获取、`40001` / `42001` 重试**恰好一次**、重试仍失败时保留结果。
- **并发单飞**：10 线程并发首调只发出一次 token 请求。
- multipart 字段与文件、POST JSON body、download 字节；二进制内容不被误判为失效 JSON。
- OAuth2 路径不注入 `access_token`、不触碰 token 缓存。
- 非 JSON 响应抛 `WxApiException`、传输异常透传、`WxApiConfig` 未装配 fail-fast。
- `MemoryAccessTokenCache` 过期与 `remove`、URL 拼接与参数编码、`JsTicketApi` 复用同一缓存实例。
- `appId` / `secret` 重载与账号重载共用同一缓存键。

`JdkHttpKitTest`：网络失败（`127.0.0.1:1`）包装为 `WxApiException`；`download` 读超时生效；multipart body 的边界 / 字段名 / 文件名 / 收尾边界正确，空 fields 与 files 时 body 为 `--boundary--`。该测试是唯一发起真实连接的用例，只访问本机回环与临时 `ServerSocket`。

### 4.5 小程序 `MiniAppApiTest`

| 用例 | 断言 |
| --- | --- |
| `code2SessionParsesOpenidUnionidSessionKey` | 三字段解析；URL 含 `jscode2session` / `appid` / `js_code` 且无 `access_token` |
| `code2SessionWithoutUnionidReturnsNull` | 无 `unionid` → `null` |
| `code2SessionBusinessErrorThrows` | `errcode` 非 0 → 异常含错误码 |
| `code2SessionMissingSessionKeyThrows` | 缺 `session_key` → 异常 |
| `subscribeSendPostsWithToken` | 订阅消息 POST 带 token |
| `getUserPhoneNumberPostsWithCode` | URL 与 body `{"code":...}` 正确 |
| `getWxacodeUnlimitReturnsBinary` | 小程序码返回二进制 |
| `postDownloadRetriesExactlyOnceAfterTokenInvalid` | `40001` → 清缓存重取 token，重试一次 |
| `accountValueObjectGuards` | 参数 fail-fast；`toString` 不泄漏 `secret`；`equals` 忽略首尾空格 |

### 4.6 支付

| 类 | 用例 | 断言 |
| --- | --- | --- |
| `PayApiTest` | `jsapiPostsOfficialBody` | 下单返回 `prepay_id`，URL / `Authorization` 前缀 / `Content-Type` / 报文字段正确 |
| | `queryUsesGetWithMchidQuery` | 查单为 GET 且带 `mchid` |
| | `closeToleratesEmpty204Body` | 关单 204 空体不抛异常 |
| | `refundPostsAmountAndRefundNo` | 退款报文字段与金额正确 |
| | `non2xxThrowsWithOfficialCodeAndMessage` | 非 2xx 抛异常，含状态码与官方 `code` / `message` |
| | `signatureCoversRequestPathAndBody` | 还原签名串后签名可被公钥验证（覆盖 path 与 body） |
| `PayMerchantTest` | `ofPemParsesPkcs8Key` | PKCS#8 PEM 解析成功，私钥签名可被同对公钥验证 |
| | `pemWithOrWithoutHeadersBothAccepted` | 带 / 不带 PEM 头得到同一私钥 |
| | `failFastOnBadArguments` | 空白 `mchId`、`null` 私钥、长度不为 32 的 `apiV3Key`、非法 Base64 → 异常 |
| | `toStringDoesNotLeakSecrets` | 不含 `apiV3Key` 与私钥内容 |
| `PaySignatureTest` | `signVerifiesWithPublicKey` | 签名可验证，篡改内容验证失败 |
| | `buildAuthorizationHeaderFormat` | 头内 `mchid` / `serial_no` / 10 位时间戳 / 32 位随机串正确，message 互验通过 |
| | `buildJsapiPayParamsSignsOfficialMessage` | JSAPI 二次签名按官方 message 构造且可验证 |
| `PayNotifyKitTest` | `verifyAcceptsValidSignature` | 合法签名 → `true` |
| | `verifyFailsOnTamperedBodyOrUnknownSerial` | 篡改 body / 未知序列号 / 缺头 → `false` |
| | `verifyIsCaseInsensitiveOnHeaderNames` | 头名大小写不敏感 |
| | `decryptResourceReturnsPlaintextTransaction` | `resource` 解密为交易明文 |
| | `verifyAndDecryptThrowsOnBadSignature` | 验签失败抛异常 |
| | `missingResourceThrows` | 缺 `resource` 抛异常 |
| `PlatformCertManagerTest` | `downloadsAndDecryptsCertificate` | 下载 + 解密得到公钥，URL 与 `Authorization` 头正确 |
| | `unknownSerialReturnsNullAfterRefresh` | 未知序列号返回 `null`，刷新窗口内不重复下载 |
| | `downloadFailureThrows` | 下载失败（500）抛异常 |
| | `parsePublicKeySupportsCertificatePem` | 证书与公钥 PEM 均可解析，`expire_time` 可按 `OffsetDateTime` 解析 |
| `AesGcmTest` | `decryptRoundtrip` | 加解密回原文 |
| | `decryptWithoutAssociatedData` | 无 AAD 可解密 |
| | `wrongKeyOrTamperedCiphertextThrows` | 错误 key 与篡改密文抛 `WxApiException` |

## 5. 运行方式

```shell
# 模块根目录（aifei-weixin）执行
mvn test           # 全部 109 个用例
mvn clean test     # 干净构建下重跑
```

- `KernelBindingTest` 依赖相对路径 `src/main/java`，**必须在模块根目录执行**。
- 全部用例离线；`JdkHttpKitTest` 只访问本机回环地址并自建临时 `ServerSocket`。
- 静态装配（`WxApiConfig.set` / `WxPayConfig.set`）为进程级状态，各测试类在 `@Before` 中重新注入伪实现以保证用例间隔离；`LogFactory` 由 `TestLogs.install()` 幂等替换。

## 6. 未覆盖项与人工验证

| 项 | 说明 |
| --- | --- |
| 永久素材与图文素材 | `MediaApi.addMaterial` / `addNewsMaterial` 无自动用例 |
| 模板消息与二维码创建 | `TemplateMsgApi.send`、`QrcodeApi.create`、`QrcodeApi.shortUrl` 无自动用例 |
| OAuth2 其余接口 | `buildAuthorizeUrl` / `getUserInfo` / `refreshToken` 未覆盖（仅 `getAccessToken` 被间接覆盖） |
| 支付门面透传与降级分支 | `PayApi.buildJsapiPayParams` 未直接测；`refund` 的空 `notifyUrl` 分支、错误体非 JSON 的降级分支未覆盖 |
| `JdkHttpKit` 成功路径 | GET / POST 正常响应与 `postDownload` 传输未覆盖（仅失败、超时与 multipart 拼装） |
| 真实微信联调 | 首验、消息回调、支付下单与回调需在真实公众号 / 商户环境人工验证 |
| 多实例 token 共享 | `AccessTokenCache` 共享实现的正确性由消费方环境验证，SDK 只提供接口与内存实现 |

## 7. 变更时的测试要求

- 新增或改变公共行为（消息类型、响应体语义、签名格式、token 策略、支付流程）时，同步更新本文件的用例清单与 `docs/design.md`。
- 触及协议数据格式（加解密布局、签名串、XML 结构、`WxConsts` 常量值）时，必须补齐或更新固定向量用例。
- 触及内核绑定边界（`cn.aifei.core.*` 引用位置、新增 arch / undertow / sql 引用）时，必须先让 `KernelBindingTest` 通过。
- 改动后 `mvn clean test` 必须 0 failure / 0 error。
