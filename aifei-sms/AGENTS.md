# aifei-sms 开发约束（模块级）

> 契约事实来源为 `docs/design.md`（公共 API 逐字签名、配置键全集、行为契约、门禁断言）。本文件只列不可违反的开发纪律；与本文冲突时先改 `design.md` 再改代码。

## 包边界（由 `ArchitectureBoundaryTest` 强制，不得放宽）

- 根包 `cn.hg.aifei.sms` **精确 5 个公共类型**：`SmsClient` / `SmsKit` / `SmsMessage` / `SmsResult` / `SmsException`；`spi` 包**精确 2 个**：`SmsReceiptListener` / `SmsReceipt`。文件名集合精确相等，多一个少一个即失败。
- `org.dromara.sms4j.*` **只在** `internal.sms4j`；`cn.aifei.*` **只在** `aifei` 包；根包与 `spi` 的公共签名零第三方 / 零框架类型。
- 全模块禁 `java.sql.*` / `io.undertow.*` / `org.slf4j.*`（含测试）；零日志绑定实现。
- `Class.forName` 仅 `internal/sms4j/SmsDeps.java`；`sms: ` 异常消息措辞仅 `internal/SmsErrors.java`；supplier 字面量映射仅 `internal/sms4j/SmsSupplierTypes.java`；配置键解析仅 `aifei/SmsConfigLoader.java`。

## 技术路线（用户点名锁定）

- 基于 `org.dromara.sms4j:sms4j-core:3.3.6` 二次封装；发送一律经 `SmsFactory` / `SmsBlend`，**不得自研等价顶替**；禁 starter / solon。
- sms4j 为 `optional`（消费方显式引入），`aifei` 为 `provided`；缺失时 `start()` 抛可读 `SmsException`，不裸抛 `NoClassDefFoundError`。
- 同步发送：不暴露异步 API、不建线程池；零存储、不做幂等去重；不引入 `aifei-db`。

## Java 8 双约束

`source` / `target` = `1.8`，主 JAR class major = 52；禁 `var` / `record` / switch 表达式 / text block / `sealed` / instanceof 模式匹配 / `List.of` / `Map.of` / `Set.of`。

## 门禁 14 例（必须全绿）

`sms4jOnlyInAdapter` / `frameworkOnlyInBridgePackage` / `noSqlOrUndertowInLibrary` / `noSlf4jBinding` / `publicSurfaceExact` / `publicSignaturesNoThirdParty` / `classForNameOnlyInDeps` / `errorMessagesOnlyInErrors` / `java8Compatibility` / `noStarterDependency` / `supplierTypesOnlyInFactory` / `pluginBridgeContract` / `configKeysOnlyInLoader` / `testSourcesOfflineOnly`。

模块根目录 `mvn clean verify` 必须 0 failure / 0 error；`mvn dependency:tree` 只含设计 §2.3 允许清单。

## 唯一定义点

| 规则 | 唯一位置 |
| --- | --- |
| 异常消息措辞与构造 | `internal/SmsErrors.java` |
| 依赖探测 | `internal/sms4j/SmsDeps.java` |
| 通道类型 / 必填键 / setter 映射 | `internal/sms4j/SmsSupplierTypes.java` |
| 配置键解析与校验 | `aifei/SmsConfigLoader.java` |
| sms4j 装配与 mock 注入 | `internal/sms4j/SmsBlendFactory.java` |
| 阿里云回执验签 | `internal/sms4j/AliyunReceiptVerifier.java` |
| 通道注册表 | `internal/SmsClients.java` |
| 装配生命周期 | `aifei/SmsPlugin.java` |
| 回执路由与响应闭环 | `aifei/SmsHandler.java` |

## 测试纪律

全部单测零外网：经 `SmsBlendFactory.setBlendFactory(Function)` 包级注入点返回预设 `SmsResponse`，不真实发短信；测试源码 http(s) host 只能是 `127.0.0.1` / `localhost`。凭据不硬编码，走配置中心。
