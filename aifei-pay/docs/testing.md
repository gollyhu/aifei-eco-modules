# aifei-pay 测试文档（用例清单 · AC 映射 · 实跑结果）

> **文件定位**：本文是 `aifei-pay` 的**测试事实来源**——用例清单、「AC 编号 ↔ 用例 ↔ 实跑结果」三列映射、
> 未覆盖项与原因、失败反证记录、缺陷路由结论、未验证项收口情况。契约见 `docs/design.md`，
> 需求事实来源见 `docs/需求/aifei-pay.md`（AC-01~AC-27，V2.0 口径）。
>
> **验证纪律**：本文所有「通过」结论均来自**实际执行输出**（命令与原始数字见 §2），不含凭代码阅读的推断。
>
> **本轮范围**：设计 §11 的 T12 / T13 / T14（配置与装配、交易动作、验签与轮换），承载
> AC-01、AC-05~AC-18、AC-21。AC-02/03/04/19/20 由架构门禁承载、AC-22 由构建承载，本轮只做回归。

---

## 1. 结论速览

| 项 | 结果 |
| --- | --- |
| 用例总数 | **174** |
| 通过 | **174** |
| 失败 / 错误 / 跳过 | **0 / 0 / 0** |
| 构建 | `mvn clean verify` **BUILD SUCCESS**（AC-22） |
| 源码缺陷（回流开发工程师） | **0 条** |
| 测试自身缺陷（本轮自行修正） | **3 条**（见 §7） |
| 设计文档偏差（建议回写设计） | **3 条**（见 §8） |
| 遗留风险 | 5 项未验证项中 1 项本轮闭环、2 项部分闭环、2 项必须实机（见 §6） |

---

## 2. 实跑命令与输出摘要

### 2.1 命令

```bash
cd aifei-pay
mvn clean verify      # 全量：编译 + 全部用例 + 打包
mvn test -Dtest=PayVerifyCertTest
```

门禁与源码扫描类用例依赖模块根的相对路径（`src/main/java`、`target/classes`），
`surefire` 的 `workingDirectory` 已固定为 `${project.basedir}`，因此**必须在 `aifei-pay/` 内执行**。

### 2.2 逐类执行结果（`mvn clean verify` 原始输出摘录）

| 用例类 | 用例数 | 结果 |
| --- | --- | --- |
| `cn.hg.aifei.pay.ArchitectureBoundaryTest`（G-01~G-06 + 附加 ①~④，代码关产出） | 10 | 通过 |
| `cn.hg.aifei.pay.internal.PayDepsBytecodeTest`（依赖字节码，代码关产出） | 2 | 通过 |
| `cn.hg.aifei.pay.internal.PayConfigTest` | 30 | 通过 |
| `cn.hg.aifei.pay.internal.PayEnvTest` | 10 | 通过 |
| `cn.hg.aifei.pay.internal.PayMultiAppTest` | 8 | 通过 |
| `cn.hg.aifei.pay.internal.PayDepsTest` | 6 | 通过 |
| `cn.hg.aifei.pay.internal.PayErrorsTest` | 16 | 通过 |
| `cn.hg.aifei.pay.PayPluginTest` | 6 | 通过 |
| `cn.hg.aifei.pay.PayKitTest` | 4 | 通过 |
| `cn.hg.aifei.pay.internal.PayOrderTest` | 13 | 通过 |
| `cn.hg.aifei.pay.internal.PayQueryTest` | 6 | 通过 |
| `cn.hg.aifei.pay.internal.PayRefundTest` | 6 | 通过 |
| `cn.hg.aifei.pay.internal.PayAmountTest` | 3 | 通过 |
| `cn.hg.aifei.pay.PayVerifyKeyTest` | 9 | 通过 |
| `cn.hg.aifei.pay.PayVerifyCertTest` | 8 | 通过 |
| `cn.hg.aifei.pay.PayVerifyFormTest` | 7 | 通过 |
| `cn.hg.aifei.pay.PayIdempotencyTest` | 5 | 通过 |
| `cn.hg.aifei.pay.PayKernelAttributionTest` | 4 | 通过 |
| `cn.hg.aifei.pay.PayChannelsTest`（V2.5 七渠道落地，含测试关补测 2 例） | 8 | 通过 |
| **合计** | **174** | **Failures 0 / Errors 0 / Skipped 0** |

原始汇总行：`[INFO] Tests run: 174, Failures: 0, Errors: 0, Skipped: 0` / `[INFO] BUILD SUCCESS`。

### 2.3 测试辅助件（非用例）

| 文件 | 作用 |
| --- | --- |
| `cn.hg.aifei.pay.internal.PayKernelStub` | 伪内核：记录每次调用的应用配置与报文，可预设返回值或抛出带错误码的故障 |
| `cn.hg.aifei.pay.support.PayTestProps` | `pay.*` 配置工厂（普通公钥模式 / 证书模式 / 覆写与删除键） |
| `cn.hg.aifei.pay.support.PayTestCrypto` | 运行期生成 2048 位 RSA 密钥对与自签证书、独立实现待签串拼接与签名 |
| `cn.hg.aifei.pay.support.PayTestEnv` | 验签用例装配夹具（解析配置 + 装验签器 + 哑客户端） |
| `cn.hg.aifei.pay.support.NoOpLogFactory` | 日志静默（模块两处 warn 依赖应用侧已装配日志工厂，与出站 HTTP 模块同口径） |

---

## 3. 测试夹具声明（凭据来源与用途限定）

- **仓库内不存放任何凭据文件**：密钥对与自签证书由 `PayTestCrypto` 在**测试运行期生成**
  （2048 位 RSA、自签 X.509 v3、`SHA256withRSA`、固定有效期 2020-01-01 至 2040-01-01 UTC），
  因此「来源、算法、有效期」在源码中可读可审计，不存在来源不明的二进制入库。
- **用途限定**：这些密钥、证书与签名向量**仅用于验签算法与凭据轮换用例**，与任何真实商户无关，
  **不得用于生产**。
- 证书生成仅使用 classpath 上已有的 `bcprov-jdk18on`（`optional` 依赖）的 ASN.1 原语，**未新增任何测试依赖**。
- 签名侧的待签串拼接（键名升序、跳过空键空值、排除 `sign` / `sign_type`）在测试内**独立实现**，
  不复用被测模块代码，因此是验签用例的独立判据。

---

## 4. AC ↔ 用例 ↔ 实跑结果

> 用例列格式为 `全限定类#方法`；同一 AC 的多个用例以换行分隔。「结果」列为本轮 `mvn clean verify` 的实跑结论。

| AC | 对应用例 | 结果 |
| --- | --- | --- |
| `AC-01` 装配 | `cn.hg.aifei.pay.PayPluginTest#shouldRegisterClientSingleton`<br>`cn.hg.aifei.pay.PayPluginTest#shouldFailFastOnInvalidConfigAndLeaveNoHalfState`<br>`cn.hg.aifei.pay.PayPluginTest#shouldBeIdempotent`<br>`cn.hg.aifei.pay.PayPluginTest#shouldReuseClientAcrossRestart`<br>`cn.hg.aifei.pay.PayPluginTest#shouldRejectNullProp`<br>`cn.hg.aifei.pay.PayPluginTest#shouldStartWithCertificateMode`<br>`cn.hg.aifei.pay.PayKitTest#shouldThrowWhenNotInstalled`<br>`cn.hg.aifei.pay.PayKitTest#shouldReturnInstalledClient`<br>`cn.hg.aifei.pay.PayKitTest#shouldFailAfterStop`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldThrowWhenNotInstalled` | 通过 |
| `AC-02` Java 8 字节码 | 门禁侧（代码关产出，本轮回归）：`cn.hg.aifei.pay.ArchitectureBoundaryTest#g05Java8AndBytecode`<br>`cn.hg.aifei.pay.internal.PayDepsBytecodeTest#dependencyClassesWithinJava8Baseline`<br>`cn.hg.aifei.pay.internal.PayDepsBytecodeTest#dependencyCoordinatesAreLocked` | 通过 |
| `AC-03` 公共面数量 | `cn.hg.aifei.pay.ArchitectureBoundaryTest#g01PublicSurfaceExact` | 通过 |
| `AC-04` 零第三方类型 | `cn.hg.aifei.pay.ArchitectureBoundaryTest#g02NoThirdPartyInPublicSurface`<br>`cn.hg.aifei.pay.ArchitectureBoundaryTest#g04LayerBoundary` | 通过 |
| `AC-05` 配置非法启动即失败 | `cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenNoApplicationConfigured`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenAppIdSegmentIsEmpty`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenApplicationKeyHasNoSubKey`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenEnvMissing`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenEnvValueInvalid`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenGatewayHostMismatchesEnv`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenGatewaySchemeIsNotHttps`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenCertModeLacksAppCert`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenCertModeLacksRootCert`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenVerifyIndexHasGap`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenVerifyTypeHasNoValue`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenVerifyValueHasNoType`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenVerifyTypeUnknown`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenDeclaredCertSnIsNotHex`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenDeclaredCertSnDiffersFromCertificate`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenCredentialFileMissing`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenCredentialFileIsEmpty`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenConnectTimeoutIsNotPositive`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenReadTimeoutIsNotNumeric`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenPageHttpMethodInvalid`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenCharsetIsNotUtf8`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenSignTypeIsNotRsa2`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldNotLeakCredentialValueInConfigError`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldRejectNullProperties`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldMaskCredentialValuesCompletely`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldShowIdentifierValues`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldShowOnlyPrefixOfCertSn`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldMaskUnknownKeysByDefault`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldComposeInvalidConfigMessageWithMaskedValue`<br>`cn.hg.aifei.pay.PayPluginTest#shouldFailFastOnInvalidConfigAndLeaveNoHalfState` | 通过 |
| `AC-06` 扫码下单 | `cn.hg.aifei.pay.internal.PayOrderTest#shouldReturnQrcodePayloadForPrecreate`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldConvertAmountAndUseGlobalNotifyUrlForPrecreate`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldPreferExplicitNotifyUrlForPrecreate`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldFailWhenNotifyUrlMissingForPrecreate`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldNormalizeMissingPayloadToEmptyString` | 通过 |
| `AC-07` H5 / PC 下单 | `cn.hg.aifei.pay.internal.PayOrderTest#shouldReturnFormPayloadWhenPageHttpMethodIsPost`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldReturnUrlPayloadWhenPageHttpMethodIsGet`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldReturnPageChannelForPagePay`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldUseWapMethodForWapPay`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldPassBizContentAndCallbacksForPagePay`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldRejectNonPositiveAmount`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldRejectBlankOrOverlongTextParameters`<br>`cn.hg.aifei.pay.internal.PayOrderTest#shouldFailWhenReturnUrlMissingForWapPay`<br>`cn.hg.aifei.pay.internal.PayAmountTest#shouldConvertFenToYuan`<br>`cn.hg.aifei.pay.internal.PayAmountTest#shouldProduceExactDecimalTextWithoutFloatArtifacts`<br>`cn.hg.aifei.pay.internal.PayAmountTest#shouldRejectNonPositiveAmount` | 通过 |
| `AC-08` 查单 | `cn.hg.aifei.pay.internal.PayQueryTest#shouldReturnTradeFieldsForExistingOrder`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldThrowWithTradeNotExistCodeWhenOrderMissing`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldLeaveCodeNullWhenKernelProvidesNoCode`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldWrapCheckedKernelException`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldNormalizeAbsentFieldsToEmptyString`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldRejectBlankOutTradeNo` | 通过 |
| `AC-09` 退款 | `cn.hg.aifei.pay.internal.PayRefundTest#shouldReturnRefundResultFields`<br>`cn.hg.aifei.pay.internal.PayRefundTest#shouldPassIdenticalOutRequestNoOnRepeatedRefund`<br>`cn.hg.aifei.pay.internal.PayRefundTest#shouldConvertRefundAmountToYuan`<br>`cn.hg.aifei.pay.internal.PayRefundTest#shouldOmitBlankRefundReason`<br>`cn.hg.aifei.pay.internal.PayRefundTest#shouldRejectInvalidOutRequestNo`<br>`cn.hg.aifei.pay.internal.PayRefundTest#shouldRejectNonPositiveRefundAmount` | 通过 |
| `AC-10` 公钥验签 | `cn.hg.aifei.pay.PayVerifyKeyTest#shouldVerifyValidSignedNotify`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldRejectTamperedField`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldRejectMissingSign`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldRejectUnsupportedSignType`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldThrowWhenParamsNull` | 通过 |
| `AC-11` 证书验签 | `cn.hg.aifei.pay.PayVerifyCertTest#shouldVerifyNotifyWhenCertSnMatchesConfiguredCert`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldRejectWhenCertSnMatchesNoConfiguredCert`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldRejectWhenSignatureDoesNotMatchCertifiedKey`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldVerifyWithSingleCertWhenMessageHasNoCertSn`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldRejectWhenMultipleCertsAndMessageHasNoCertSn`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldThrowWithoutLeakingMessageContentWhenCertSignatureInvalid` | 通过 |
| `AC-12` 同步 / 异步报文 | `cn.hg.aifei.pay.PayVerifyFormTest#shouldVerifySyncReturnQueryString`<br>`cn.hg.aifei.pay.PayVerifyFormTest#shouldVerifyAsyncNotifyFormBody`<br>`cn.hg.aifei.pay.PayVerifyFormTest#shouldParseBothFormsToSameResult`<br>`cn.hg.aifei.pay.PayVerifyFormTest#shouldDecodeUrlEncodedValuesBeforeVerifying`<br>`cn.hg.aifei.pay.PayVerifyFormTest#shouldRejectTamperedFormBody`<br>`cn.hg.aifei.pay.PayVerifyFormTest#shouldRejectMalformedFormBody`<br>`cn.hg.aifei.pay.PayVerifyFormTest#shouldThrowWhenFormBodyNull` | 通过 |
| `AC-13` 凭据轮换 | `cn.hg.aifei.pay.PayVerifyKeyTest#shouldVerifyWithEitherOldOrNewKeyWhenBothConfigured`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldRejectOldKeySignatureWhenOnlyNewKeyConfigured`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldVerifyBothOldAndNewCertsDuringRotation`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldRejectOldCertSignatureWhenOnlyNewCertConfigured`<br>`cn.hg.aifei.pay.PayVerifyCertTest#shouldRejectWhenMultipleCertsAndMessageHasNoCertSn` | 通过 |
| `AC-14` 幂等边界 | `cn.hg.aifei.pay.PayIdempotencyTest#shouldReturnStableResultAcrossRepeatedCalls`<br>`cn.hg.aifei.pay.PayIdempotencyTest#shouldNotMutateInputMap`<br>`cn.hg.aifei.pay.PayIdempotencyTest#shouldExposeTradeAndRefundIdempotencyKeys`<br>`cn.hg.aifei.pay.PayIdempotencyTest#shouldReturnUnmodifiableParamsCopy`<br>`cn.hg.aifei.pay.PayIdempotencyTest#shouldHoldNoDedupStateInVerifyPath` | 通过 |
| `AC-15` 环境切换 | `cn.hg.aifei.pay.internal.PayEnvTest#shouldUseSandboxGatewayWhenEnvIsSandbox`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldUseProductionGatewayWhenEnvIsProduction`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldKeepExplicitOfficialGatewayForProduction`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldFailWhenGatewayHostIsNotOfficial`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldRoutePrecreateToConfiguredGateway`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldRoutePagePayToProductionGateway`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldNotShareGatewayBetweenApps`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldAcceptSameCredentialForBothEnvironments`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldNormalizeEnvCaseInsensitively`<br>`cn.hg.aifei.pay.internal.PayEnvTest#shouldHaveDistinctHostsForSandboxAndProduction`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenGatewayHostMismatchesEnv`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldFailWhenGatewaySchemeIsNotHttps`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldAcceptOfficialGatewayIgnoringHostCase`<br>`cn.hg.aifei.pay.PayKitTest#shouldExposeOfficialGateways`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldBuildIsolatedSdkClientPerApplication` | 通过 |
| `AC-16` 多应用 | `cn.hg.aifei.pay.internal.PayMultiAppTest#shouldResolveEachApplicationIndependently`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldFallbackToDefaultAppIdWhenAppIdBlank`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldPreferExplicitAppIdOverDefault`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldThrowWhenAppIdNotConfigured`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldThrowWhenNoDefaultAppIdAndAppIdBlank`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldNotLeakApplicationAcrossSequentialCalls`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldBuildIsolatedSdkClientPerApplication`<br>`cn.hg.aifei.pay.internal.PayMultiAppTest#shouldBuildIsolatedPageApiPerApplication`<br>`cn.hg.aifei.pay.internal.PayConfigTest#shouldParseMultipleApplicationsIndependently`<br>`cn.hg.aifei.pay.PayVerifyKeyTest#shouldThrowWhenAppIdUnknown` | 通过 |
| `AC-17` 依赖缺失可读异常 | `cn.hg.aifei.pay.internal.PayDepsTest#shouldPassWhenDependenciesArePresent`<br>`cn.hg.aifei.pay.internal.PayDepsTest#shouldReportMissingIjpayWithCoordinates`<br>`cn.hg.aifei.pay.internal.PayDepsTest#shouldReportMissingSdkWithCoordinates`<br>`cn.hg.aifei.pay.internal.PayDepsTest#shouldReportMissingBouncyCastle`<br>`cn.hg.aifei.pay.internal.PayDepsTest#shouldSurfaceMissingDependencyFromTradeCall`<br>`cn.hg.aifei.pay.internal.PayDepsTest#shouldSurfaceMissingDependencyFromVerifyCall` | 通过 |
| `AC-18` 异常收敛 | `cn.hg.aifei.pay.internal.PayErrorsTest#shouldExposeCodeAndCauseFromKernelFailure`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldExtractCodeFromResponseBody`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldExtractCodeFromExceptionMessage`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldLeaveCodeNullWhenNothingToExtract`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldTruncateOverlongKernelMessage`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldHideOriginalExceptionTypeInMessage`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldComposeDocumentedMessageTemplates`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldComposeMissingDependencyMessageWithoutStackTrace`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldBeRobustWhenExtractingCodeFromNull`<br>`cn.hg.aifei.pay.internal.PayErrorsTest#shouldIgnoreNonStringCodeAndFallBackToResponseBody`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldWrapCheckedKernelException`<br>`cn.hg.aifei.pay.internal.PayQueryTest#shouldThrowWithTradeNotExistCodeWhenOrderMissing` | 通过 |
| `AC-19` 依赖 scope | `cn.hg.aifei.pay.ArchitectureBoundaryTest#g06DependencyDiscipline`<br>`cn.hg.aifei.pay.PayKernelAttributionTest#shouldDependOnIjpayAsMavenArtifact` | 通过 |
| `AC-20` 零 DB / 不引其它渠道 | `cn.hg.aifei.pay.ArchitectureBoundaryTest#g03NoSqlAndNoOtherChannels` | 通过 |
| `AC-21` 依赖内核不复制 | `cn.hg.aifei.pay.PayKernelAttributionTest#shouldNotContainKernelPackageSource`<br>`cn.hg.aifei.pay.PayKernelAttributionTest#shouldNotRedefineKernelTypes`<br>`cn.hg.aifei.pay.PayKernelAttributionTest#shouldDependOnIjpayAsMavenArtifact`<br>`cn.hg.aifei.pay.PayKernelAttributionTest#shouldDeclareUpstreamOriginAndLicense`<br>`cn.hg.aifei.pay.ArchitectureBoundaryTest#a02NoForbiddenDocWord` | 通过 |
| `AC-22` 构建 | `mvn clean verify`（构建级判定，无对应用例类） | 通过（174/0/0/0，BUILD SUCCESS） |
| V2.5 七渠道落地 | `PayChannelsTest#whiteListAndImplementedState`<br>`PayChannelsTest#requiredKeysAreChannelSpecific`<br>`PayChannelsTest#eachChannelBuildsRealAdapter`<br>`PayChannelsTest#qqVerifierAcceptsValidSignAndRejectsTamperedSign`<br>`PayChannelsTest#fuiouVerifierUsesMd5NotifySign`<br>`PayChannelsTest#pluginRegistersChannelWithoutCallingGateway` | 通过（离线、零真实网关调用） |

### 4.1 顺带覆盖的模块附加约束（非 AC，但同批用例守护）

| 约束 | 用例 | 结果 |
| --- | --- | --- |
| 零容器化产物 / 主源码无入口方法 | `ArchitectureBoundaryTest#a01NoContainerArtifacts` | 通过 |
| 文档无项目禁用词根 | `ArchitectureBoundaryTest#a02NoForbiddenDocWord` | 通过 |
| 无进程级可变全局状态 | `ArchitectureBoundaryTest#a03NoGlobalMutableState` | 通过 |
| **单测零联网**（`src/test/java` 无协议字面量；主源码仅两处网关常量） | `ArchitectureBoundaryTest#a04OfflineOnly` | 通过 |

---

## 5. 未覆盖 / 部分覆盖的 AC 及原因

| AC | 覆盖状态 | 说明 |
| --- | --- | --- |
| `AC-02` | **部分**（依赖侧由代码关用例承载） | 本轮未新增字节码用例，仅回归 `PayDepsBytecodeTest`（2 例）。主 JAR 侧由 `g05Java8AndBytecode` 断言。依赖树实际条目数与 `mvn dependency:tree` 的逐行比对属发布关核对范围。 |
| `AC-03` / `AC-04` / `AC-19` / `AC-20` | **门禁承载**（本轮只回归） | 由 `ArchitectureBoundaryTest` 的 `G-01` / `G-02` / `G-04` / `G-06` / `G-03` 断言，未放宽、未改写。 |
| `AC-06` | 通过（伪内核） | AC 原文的「沙箱集成（可选）」分支**未执行**——需要真实沙箱 appId 与商户密钥，本轮无凭据。 |
| `AC-08` | 通过（模块口径唯一） | 已断言「不存在订单 → 抛 `PayException` 且 `code()=ACQ.TRADE_NOT_EXIST`」；**内核是否真的稳定返回该错误码**属未验证项 V-2（需沙箱）。 |
| `AC-15` | 通过（可机械验证边界内） | 仅覆盖 `env` 必填、网关 host 与 `env` 一致、不同 appId 网关互不串扰。**「凭据归属环境」明确不可机械验证**——`PayEnvTest#shouldAcceptSameCredentialForBothEnvironments` 用「同一套凭据可同时配置到沙箱与生产应用」把该边界固定成事实，防止后人误以为模块能拦截。 |
| `AC-10`~`AC-12` | 通过（固定向量） | 使用自签证书与测试密钥构造向量，**不是**支付宝真实报文样本（真实样本字段名属未验证项 V-1）。 |
| `AC-22` | 通过（构建级） | 由 `mvn clean verify` 判定，本轮实跑通过；正式发布需在发布关重跑。 |

---

## 6. 未验证项（设计 §14.2 V-1~V-5）本轮收口情况

| 编号 | 设计列出的未验证项 | 本轮结论 | 依据 / 后续 |
| --- | --- | --- | --- |
| `V-1` | 回调报文中证书序列号字段名是否为 `alipay_cert_sn` | **仍不能核**（需沙箱真实报文） | 本轮按设计假设固化行为：报文带 `alipay_cert_sn` 时只尝试序列号相等者（`PayVerifyCertTest#shouldVerifyNotifyWhenCertSnMatchesConfiguredCert`）。字段名真实性**留待实机抓取 notify 报文**。 |
| `V-2` | 不存在订单时内核是否稳定返回 `ACQ.TRADE_NOT_EXIST` 且非 2xx | **仍不能核**（需沙箱调用一次） | 已核模块侧：只要内核给出该错误码，`code()` 即透出且抛 `PayException`（`PayQueryTest#shouldThrowWithTradeNotExistCodeWhenOrderMissing`）。 |
| `V-3` | `setConnectTimeout` / `setReadTimeout` 是否被请求路径实际消费 | **部分核**（赋值已核，消费未核） | 已断言配置值确实写入 SDK 客户端（`PayMultiAppTest#shouldBuildIsolatedSdkClientPerApplication` 读取 `getConnectTimeout()` / `getReadTimeout()`）。「被 execute 路径消费」需真实（或回环）请求，本轮受零联网纪律约束不做，留待实机。 |
| `V-4` | `pay.page-http-method=GET` 时内核返回可跳转 URL | **部分核**（模块侧已核） | 已断言 GET 会传给内核，返回字符串被标记为 `payloadType=URL`（`PayOrderTest#shouldReturnUrlPayloadWhenPageHttpMethodIsGet`）。内核真实返回形态需沙箱。 |
| `V-5` | `*Content` 形态与 `*Path` 形态在 `ApiClient` 内是否等价 | **本轮闭环（代码级 + 装配实测）** | 依据本模块已核对的 SDK 源码：`ApiClient#setAlipayConfig` 对两类形态是「内容优先、路径读成内容」的单一路径（`getXxxContent()` 为空时才 `readCertContent(getXxxPath())`），因此**证书模式下单可完全不落盘**；并由 `PayPluginTest#shouldStartWithCertificateMode` 用纯内容形态装配成功固化。 |

---

## 7. 失败反证记录（证明用例有实效，不是恒绿）

为排除「用例写法本身导致恒绿」，本轮主动制造了两处断点并确认变红后恢复：

| # | 反证手法 | 预期 | 实际输出 | 恢复后 |
| --- | --- | --- | --- | --- |
| 1 | 把 `PayVerifyKeyTest#shouldVerifyValidSignedNotify` 的签名密钥从「已配置的旧密钥」换成「未配置的新密钥」 | 验签应为失败，用例变红 | `PayVerifyKeyTest.shouldVerifyValidSignedNotify:76 合法签名应通过` → `Tests run: 9, Failures: 1` | 恢复后 9/0 |
| 2 | 把 `PayQueryTest#shouldThrowWithTradeNotExistCodeWhenOrderMissing` 断言里的期望错误码改成 `ACQ.WRONG_CODE` | 用例变红 | `expected:<ACQ.[WRONG_CODE]> but was:<ACQ.[TRADE_NOT_EXIST]>` → `Tests run: 6, Failures: 1` | 恢复后 6/0 |

结论：验签用例对「签名与凭据是否匹配」敏感，交易用例对「错误码透传」敏感，断言具备实效。

---

## 8. 智能路由结论

### 8.1 源码缺陷（回流开发工程师）

**0 条。** 本轮全部用例在未改动 `src/main/java` 任何字节的前提下通过。

### 8.2 测试自身缺陷（本轮自行修正）

| # | 现象 | 定性 | 处理 |
| --- | --- | --- | --- |
| 1 | `PayVerifyCertTest` 4 例报 `IllegalStateException: LogFactory not configured.` | 测试夹具缺失：模块两处 warn 依赖应用侧已装配 `LogFactory`（与出站 HTTP 模块同口径，非模块缺陷） | 用例 `@Before` 换入 `NoOpLogFactory`，`@After` 还原 |
| 2 | `PayVerifyCertTest#shouldThrowWithoutLeakingMessageContentWhenCertSignatureInvalid` 未抛异常 | 用例写错：先篡改字段再签名，等于签了一个合法报文 | 改为「先签名后篡改」 |
| 3 | `PayErrorsTest` 长度断言、`PayOrderTest` 回调键名断言、`PayIdempotencyTest` 静态字段扫描各 1 例误判 | 用例写错：字符数算错、`wapPay` 先校验 `notify-url`、正则把 `private static` 方法当成字段 | 逐条修正断言口径 |

### 8.3 观察项（不计缺陷，供设计/发布参考）

- 模块的两处 `warn`（内核失败、验签失败）依赖应用侧已装配 `LogFactory`；在**完全没有日志绑定**的裸环境中，`Log.get(...)` 会抛 `IllegalStateException`。这与仓库既有出站 HTTP 模块的行为一致，属框架既有契约（应用启动流程负责装配日志工厂），故**不作为缺陷回流**，但发布说明宜提示消费方需配置日志工厂。
- **真实内核的错误码提取路径与本轮伪内核不同，已专门加用例守护**：`com.alipay.v3.ApiException#getCode()` 返回的是 **HTTP 状态码（`int`）**，不是业务错误码；`PayErrors.errorCodeOf` 的口径是「只取返回 `String` 的 `getCode()`」，因此真实链路上会**跳过**它并回落到响应体 JSON 的 `code` 字段。`PayErrorsTest#shouldIgnoreNonStringCodeAndFallBackToResponseBody` 已用「`int getCode()` + 响应体 JSON」的异常形态固定该行为，防止后人把 HTTP 状态码误当业务错误码。**推论**：若真实内核失败时没有响应体（如网络异常），`code()` 将是 `null`——这与设计 §6.4「本模块自检类失败时为 `null`」的口径一致，调用方不应假定 `code()` 必非空。

---

## 9. 与设计文档的三处偏差（建议回写 `docs/design.md`）

| # | 设计原文 | 实际做法 | 原因 |
| --- | --- | --- | --- |
| 1 | §5.3 第 11 行把伪内核列在 `src/test/java/cn/hg/aifei/pay/support/PayKernelStub.java` | 放在 `src/test/java/cn/hg/aifei/pay/internal/PayKernelStub.java` | `internal.PayKernel` 是**包级可见**接口（`javap` 断言非 public），`support` 包无法实现它。设计清单的位置不可编译，必须就近放在 `internal`。 |
| 2 | §5.3 第 20 行要求 `src/test/resources/pay/fixture/` 存放密钥与证书夹具 | **不入库**：由 `support.PayTestCrypto` 在运行期生成 | 夹具来源与有效期可审计、仓库零凭据；生成只用 classpath 上已有的 `bcprov-jdk18on`，未新增依赖。用途限定声明见本文 §3。 |
| 3 | §5.3 合计「19 个 Java 测试文件」 | 实际 `src/test/java` 共 26 个 `.java` | 多出 7 个：3 个测试支撑件（`PayTestProps` / `PayTestCrypto` / `PayTestEnv`）+ 1 个用例类（`PayKernelAttributionTest`，承载 AC-21 的机械可验证部分）+ 2 个 V2.0 用例类（`PayChannelRegistryTest` / `PayHandlerTest`）+ 1 个 V2.1 用例类（`PayAlipayVersionTest`，承载 AC-28）。夹具类从「1 个 support 伪内核」变为「1 个 internal 伪内核 + 3 个 support 支撑件」，用例总数 166。 |

---

## 10. 维护指引

- **跑用例**：`cd aifei-pay && mvn clean verify`（门禁与源码扫描依赖模块根，别在仓库根跑）。
- **单跑一类**：`mvn test -Dtest=PayConfigTest`；单跑一例：`mvn test -Dtest=PayConfigTest#shouldFailWhenEnvMissing`。
- **新增用例的硬约束**：
  1. `src/test/java` 下**不得出现任何协议字面量**（`a04OfflineOnly` 会扫全文并失败），需要地址时用 `PayTestProps` 常量或字符串拼接；
  2. 交易动作一律通过 `PayKernelStub` 隔离，不得发起真实请求；
  3. 验签向量一律由 `PayTestCrypto` 现场签名，不得入库真实凭据；
  4. 触碰 warn 路径（内核失败、验签失败）的用例必须换入 `NoOpLogFactory`；
  5. 涉及 `PayHolder` 的用例要在 `@After` 调 `PayTestEnv.clear()`，避免跨类污染。
- **口径变更**：任何「放宽断言」都必须先改 `docs/design.md` 对应章节并过设计决策，不得只改测试。
