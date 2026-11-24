# Changelog

本文件记录 `aifei-tools` 模块的版本变更，按时间倒序。版本号遵循语义化版本（capability 扩展走 minor 递增、缺陷修复走 patch、破坏性变更走 major）。

## [1.0.0] - 2026-11-24

### 概述

aifei 生态的极简工具集库模块（SDK，可被应用依赖），定位「几十行就能写完、却每个应用都要抄一遍」的能力收拢到一个 JAR。五个工具按子包隔离、各自独立可用、互不牵连：`captcha` 验证码 / `ipdb` IP 归属地 / `qr` 二维码 / `mask` 脱敏 / `kdf` 密钥派生。

- 坐标：`io.github.gollyhu:aifei-tools:1.0.0-SNAPSHOT`（`packaging=jar`）；包名 `cn.hg.aifei.tools`；Java 8（class major 52）；根包零公共类型

### 五子包与公共类型（共 20 个，根包零公共类型）

| 子包 | 公共类型（N） | 内核 | 缺失时 |
| --- | --- | --- | --- |
| `ipdb` | `IpSearcher` / `IpKit` / `IpRegion` / `IpException` / `IpQueryMode`（5） | `org.lionsoul:ip2region:3.3.7`（optional） | 首次调用抛可读 `IpException` |
| `captcha` | `Captcha` / `CaptchaKit` / `CaptchaType` / `CaptchaException`（4） | `io.github.gollyhu:aifei-cache:1.1.0`（optional，仅集群存储） | 单机零依赖；切集群缺 aifei-cache 抛可读 `CaptchaException` |
| `qr` | `QrKit` / `QrOptions` / `QrErrorCorrection` / `QrException`（4） | `com.google.zxing:core:3.5.3`（optional，不引 javase） | 首次调用抛可读 `QrException` |
| `mask` | `MaskUtil` / `MaskKit` / `MaskException`（3） | 无（纯 JDK） | 始终可用 |
| `kdf` | `KdfKit` / `KdfSpec` / `KdfHash` / `KdfException`（4） | 无（纯 JDK `javax.crypto` / `java.security`） | 始终可用 |

### kdf 子包（纯 JDK 零第三方实现）

- 三算法：HKDF（RFC 5869 extract+expand）、PBKDF2（RFC 8018 §5.2）、HMAC 派生原语（RFC 4231）
- 两档哈希：SHA-256 / SHA-512（`KdfHash` 枚举）
- internal 4 类型：`KdfErrors`（`kdf: ` 消息唯一定义点）/ `HkdfEngine` / `Pbkdf2Engine` / `HmacEngine`
- `javax.crypto.*` / `java.security.*` import 仅 `kdf/internal/`，公共签名零 JDK 加密类型
- HMAC 方案①（`KdfKit.derive` 两重载 + data 第三入参）：`derive(spec, ikm)` 与 `derive(spec, ikm, data)`；HMAC 派生原语为 `HMAC(ikm, ikm)`（自 HMAC，输出 HashLen 字节，spec.keyLen 仅校验合法性、不影响输出长度）

### 架构门禁（21 例）

`ArchitectureBoundaryTest` 21 例（原 17 + kdf 追加 4），扫描 `src/main/java`：

- 原 17 例（①⑪）逐字保留，门禁⑪⑧扩充覆盖 `kdf: ` 消息前缀与 kdf 公共层第三方字面量扫描
- kdf 追加 4 例：⑱`kdfPublicSurfaceExact` / ⑲`cryptoOnlyInKdfInternal` / ⑳`kdfPrefixOnlyInErrors` / ⑳′`publicSignaturesNoThirdPartyKdf`

### 测试用例（178 例）

- 用例总数 **178（门禁 21 + 功能 157）**，0 failure / 0 error
- kdf 子包 51 例：`HkdfTest` 6 / `Pbkdf2Test` 6 / `HmacTest` 10 / `KdfKitTest` 20 / `PublicApiContractKdfTest` 9
- 全部离线可重复（xdb 样本内嵌 base64、captcha 集群用内存 Mock、无联网）

### 实现期偏离（2 项，已记录于 design §8-F）

1. **`Pbkdf2Engine` 实现手法偏离**：设计 §1.3 / §3.5 原定 `SecretKeyFactory.getInstance("PBKDF2WithHmac"+hash)` + `PBEKeySpec` + `generateSecret().getEncoded()`。实测（JDK 19 SunJCE）发现 `getEncoded()` 返回的 key 长度由 PBE cipher 约束决定（默认 8 字节），不等于 `PBEKeySpec` 请求的 keyLen，无法满足"任意 keyLen 输出"契约。改用手动 HMAC PRF 循环实现 RFC 8018 §5.2，纯 JDK、零第三方，符合门禁⑲边界。输出与 RFC 7914 §B.1 官方向量逐字一致。
2. **门禁⑲口径放宽**：设计 §6.2 原定 `javax.crypto.` / `java.security.` import 仅 `kdf/internal/`。实测发现 `captcha/internal` 既有 `java.security.SecureRandom`（用于 token 随机数），门禁⑲放宽 `java.security.` 边界至 `kdf/internal/` 与 `captcha/internal/`；`javax.crypto.` 边界不变（仅 `kdf/internal/`）。

### F9-a 实测结论

- 当前 JDK（实测 JDK 19 SunJCE）`PBKDF2WithHmacSHA256` / `PBKDF2WithHmacSHA512` / `HmacSHA256` / `HmacSHA512` **四算法名全部可用**，无 `NoSuchAlgorithmException`。

### 依赖清单

`aifei-all`(provided) + `ip2region`/`aifei-cache`/`zxing`(optional) + `junit`(test)；kdf 纯 JDK，不新增任何第三方依赖。零日志绑定、零 DB、零 Web 面、无 Plugin、无配置键、无 SPI。
