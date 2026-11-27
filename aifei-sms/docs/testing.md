# aifei-sms 测试文档

> 判据：模块根目录 `mvn clean verify` **0 failure / 0 error**；全部单测零外网、零真实短信。

## 测试分层

| 层 | 覆盖 | 判据 |
| --- | --- | --- |
| 架构门禁 | `ArchitectureBoundaryTest` 14 例（§9） | 全绿，不得放宽 |
| 字节码 | 抽验 `SmsClient` / `SmsKit` / `SmsPlugin` / `SmsDeps` 的 class major | 恒 52 |
| 发送契约 | `SendContractTest`：模板化发送 / 内容直发 / 多通道 / 默认通道切换 / 并发 | AC-01~AC-03 / Q-08 |
| 装配生命周期 | `PluginLifecycleTest`：start 可用 / stop 后失败 / 幂等 / 失败回滚 | AC-04~AC-05 |
| 配置校验 | `ConfigValidationTest`：无通道 / 必填缺失 / 类型未知 / 默认通道非法 | AC-05 |
| 依赖缺失 | `DependencyMissingTest`：隔离类加载器模拟缺失，可读异常无裸 `NoClassDefFoundError` | AC-06 |
| 回执 | `ReceiptTest`：验签通过/失败 / 解析字段 / 监听器异常 / 404 / 响应契约 | AC-07 |
| 异常契约 | `ExceptionContractTest`：参数 / 厂商 / 网络 / 状态 / 验签 / 解析逐字消息 + cause | AC-08 |
| 公共面 | `PublicApiContractTest`：根包 5 / spi 2 / Kit 形态 / 签名零第三方 | Q-01 / AC-09~AC-10 |
| 依赖树 | `mvn dependency:tree` | 仅允许清单，无 Spring / 日志绑定 / starter |

## 用例与 AC 映射

| 测试类 · 用例 | 对应 AC | 验证点 |
| --- | --- | --- |
| `SendContractTest.templateSendReturnsResultWithRequestId` | AC-01 | `SmsKit.defaultClient().send` 返回 `SmsResult`，含通道、状态、流水号、消息 |
| `SendContractTest.contentSendUsesTwoArgApi` | AC-01 | 内容直发走 `sendMessage(phone, content)` |
| `SendContractTest.namedChannelsAreIndependentInstances` | AC-02 | 两通道各取独立实例，`channels()` 快照含两者 |
| `SendContractTest.defaultChannelSwitchChangesOnlyConfiguration` | AC-02 / AC-03 | 仅改配置切换默认通道，业务发送代码不变 |
| `SendContractTest.concurrentSendHasNoStatePollution` | Q-08 | 8 线程并发发送结果一致、无异常 |
| `PluginLifecycleTest.sendAvailableAfterStart` | AC-04 | start 后 `SmsClients.isStarted()` 为 true 且发送成功 |
| `PluginLifecycleTest.stopThenSendFailsWithReadableMessage` | AC-04 | stop 后抛场景 5 逐字消息 |
| `PluginLifecycleTest.stopThenChannelsAndUseFail` | AC-04 | stop 后 `channels()` / `use()` 同样失败 |
| `PluginLifecycleTest.repeatedStartAndStopAreIdempotent` | AC-04 | start×2 / stop×2 幂等、状态不叠加 |
| `PluginLifecycleTest.failedStartRollsBackAndCanRecover` | AC-05 | 非法配置失败回滚，合法配置可重装配 |
| `ConfigValidationTest.noChannelDeclaredFails` | AC-05 | 无通道消息含 `sms.<name>.type` 与 `<empty>` |
| `ConfigValidationTest.missingRequiredKeyShowsFullKeyAndEmpty` | AC-05 | 必填缺失消息含完整键名与原值 |
| `ConfigValidationTest.unknownTypeShowsKeyAndValue` | AC-05 | 类型未知消息含键名与原值 |
| `ConfigValidationTest.invalidDefaultChannelFails` / `multiChannelWithoutDefaultFails` | AC-05 | 默认通道非法 / 多通道缺失 |
| `ConfigValidationTest.nullPropFails` | R-03 | 构造期 null Prop 可读失败 |
| `DependencyMissingTest.missingSms4jThrowsReadableSmsException` | AC-06 | 可读坐标消息、非 `NoClassDefFoundError` |
| `DependencyMissingTest.availableSms4jPassesProbe` | R-04 | 依赖存在时探测通过 |
| `ReceiptTest.verifiedReceiptIsParsedAndDelivered` | AC-07 | 真实验签 → 解析 → 监听器收到状态 / 流水号 / 手机号 / 时间 / 原始报文 |
| `ReceiptTest.badSignatureIsRejectedWithoutDelivery` | AC-07 | 验签失败 403 FAIL、不交付 |
| `ReceiptTest.missingSecretIsRejected` | AC-07 | 验签密钥缺失按验签失败处理 |
| `ReceiptTest.unknownChannelReturns404` | §8.4 | 未知通道 404 FAIL |
| `ReceiptTest.listenerFailureReturns500` | §8.4 | 监听器异常 500 FAIL 且记录 |
| `ReceiptTest.nonCallbackPathIsDelegatedToNext` | §8.2 | 非短信路径交 next |
| `ExceptionContractTest.blankPhoneNumberUsesExactMessage` / `blankTemplateIdUsesExactMessage` | AC-08 | 参数消息逐字 |
| `ExceptionContractTest.supplierErrorUsesVendorCodeAndMessage` | AC-08 | 厂商错误含码与描述、不含第三方类型名 |
| `ExceptionContractTest.networkErrorKeepsCause` | AC-08 | 网络异常保留 cause |
| `ExceptionContractTest.channelNotFoundUsesExactMessage` | §7.5 场景 6 | 通道不存在逐字消息 |
| `ExceptionContractTest.verificationAndParseMessagesAreExact` | §7.5 场景 11/12 | 验签 / 解析消息逐字 + cause |
| `PublicApiContractTest.*`（5 例） | AC-09 / AC-10 | 精确类型集合、Kit 形态、签名反射检查 |

## 离线与 mock

- 全部发送经 `SmsBlendFactory.setBlendFactory(Function)` 包级注入点注入 `MockSmsBlend`，返回预设 `SmsResponse` 或抛预设异常；**不真实发短信、不访问外网**。
- 测试源码 http(s) 字面量 host 仅允许 `127.0.0.1` / `localhost`（门禁 ⑭）。
- 测试数据自包含、用例独立；`SmsClients.clear()` 与 mock 注入复位保证无用例间状态污染。

## 验证命令（模块根目录）

```bash
mvn clean verify                                        # 49 例全绿（含门禁 14 例）
mvn test -Dtest=ArchitectureBoundaryTest                # 仅门禁 14 例
mvn dependency:tree                                     # 依赖范围核对
javap -verbose target/classes/cn/hg/aifei/sms/SmsClient.class | findstr "major version"   # 52
```
