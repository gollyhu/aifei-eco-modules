# Changelog

本文件记录 `aifei-tools` 模块的版本变更，按时间倒序。版本号遵循语义化版本（capability 扩展走 minor 递增、缺陷修复走 patch、破坏性变更走 major）；SNAPSHOT 未定版阶段不适用升位，变更写入当前版本条目。

## [1.0.0-SNAPSHOT] - 2026-11-27

### 概述

新增四个纯 JDK 通用原语子包 `crypto` / `random` / `xml` / `codec`，模块公共类型由 20 增至 **28**（最终九子包），架构门禁由 21 增至 **33**；pom 依赖清单不变，**零新增第三方依赖、零配置键、零 Plugin / SPI、零日志绑定**。既有五子包（ipdb / captcha / qr / mask / kdf）公共面、签名、行为与依赖冻结不变，根包仍精确 0 公共类型。

### 新增四子包（每子包公共类型 N=2）

| 子包 | 公共类型 | 公共原语 | internal |
| --- | --- | --- | --- |
| `crypto` | `CryptoUtil` / `CryptoException` | `sha1Hex` / `sha1Base64` / `hmacSha1` / `hmacSha256` / `constantTimeEquals`（5） | `CryptoErrors` / `Sha1Engine` / `HmacEngine` / `ConstantTimeEngine` |
| `random` | `RandomUtil` / `RandomException` | `randomString(int)` / `token()`（2） | `RandomErrors` / `RandomEngine` |
| `xml` | `XmlUtil` / `XmlException` | `parse(String)` / `escape(String)`（2） | `XmlErrors` / `XmlParser` |
| `codec` | `CodecUtil` / `CodecException` | `gunzip(byte[])`（1） | `CodecErrors` / `GzipEngine` |

- `crypto` 文本编码固定 UTF-8，摘要输出 40 位小写十六进制或标准 Base64，HMAC 按 RFC 2104 `HMAC(K, text)` 顺序传参并输出标准 Base64；`constantTimeEquals` 遇 null 返回 false 不抛异常
- `random` 使用 62 个 ASCII 字母数字字符，`length >= 0`、0 返回空串；`token()` 为 32 字节安全随机源转无填充 Base64URL（43 字符）
- `xml` 在 `XmlParser` 单点完成六项 XXE 硬化，文档类型声明命中即抛异常不展开；`parse` 返回 JDK 保序映射、同名取首；`escape` 只做五实体单次替换；不硬编码协议根节点与字段名
- `codec` 的 `gunzip` 对 null / 空数组原样返回，失败抛 `CodecException` 并保留 cause；不读取配置键、不干涉传输层协商
- 四个新子包互不引用，也不被既有五子包引用；公共层零 JDK 加密类型、零第三方 / 框架字面量；`javax.crypto.*` / `java.security.*` 仅 `crypto/internal` / `random/internal`（及既有 `kdf/internal` / `captcha/internal`）使用
- 全部新增 `.java` 使用仓库统一原创版权头，按公开算法与规范自行重写，不复制来源模块的源码、注释、协议串或字段名

### 依赖口径

- 模块坐标：`io.github.gollyhu:aifei-tools:1.0.0-SNAPSHOT`
- 依赖清单不变：`aifei`(provided) + `ip2region` / `zxing` / `aifei-cache`(optional) + `junit`(test)；四个新子包纯 JDK

## [1.0.0] - 2026-11-24

### 概述

aifei 生态的极简工具集库模块（SDK，可被应用依赖），定位「几十行就能写完、却每个应用都要抄一遍」的能力收拢到一个 JAR。五个工具按子包隔离、各自独立可用、互不牵连：`captcha` 验证码 / `ipdb` IP 归属地 / `qr` 二维码 / `mask` 脱敏 / `kdf` 密钥派生。

- 坐标：`io.github.gollyhu:aifei-tools:1.0.0-SNAPSHOT`（`packaging=jar`）；包名 `cn.hg.aifei.tools`；Java 8（class major 52）；根包零公共类型

### 五子包与公共类型（共 20 个，根包零公共类型）

| 子包 | 公共类型（N） | 内核 | 缺失时 |
| --- | --- | --- | --- |
| `ipdb` | `IpSearcher` / `IpKit` / `IpRegion` / `IpException` / `IpQueryMode`（5） | `org.lionsoul:ip2region:3.3.7`（optional） | 首次调用抛可读 `IpException` |
| `captcha` | `Captcha` / `CaptchaKit` / `CaptchaType` / `CaptchaException`（4） | `io.github.gollyhu:aifei-cache:1.2.0-SNAPSHOT`（optional，仅集群存储） | 单机零依赖；切集群缺 aifei-cache 抛可读 `CaptchaException` |
| `qr` | `QrKit` / `QrOptions` / `QrErrorCorrection` / `QrException`（4） | `com.google.zxing:core:3.5.3`（optional，不引 javase） | 首次调用抛可读 `QrException` |
| `mask` | `MaskUtil` / `MaskKit` / `MaskException`（3） | 无（纯 JDK） | 始终可用 |
| `kdf` | `KdfKit` / `KdfSpec` / `KdfHash` / `KdfException`（4） | 无（纯 JDK `javax.crypto` / `java.security`） | 始终可用 |

### kdf 子包（纯 JDK 零第三方实现）

- 三算法：HKDF（RFC 5869 extract+expand）、PBKDF2（RFC 8018 §5.2）、HMAC 派生原语（RFC 4231）
- 两档哈希：SHA-256 / SHA-512（`KdfHash` 枚举）
- internal 4 类型：`KdfErrors`（`kdf: ` 消息唯一定义点）/ `HkdfEngine` / `Pbkdf2Engine` / `HmacEngine`
- `javax.crypto.*` / `java.security.*` import 仅 `kdf/internal/`，公共签名零 JDK 加密类型
- HMAC 方案①（`KdfKit.derive` 两重载 + data 第三入参）：`derive(spec, ikm)` 与 `derive(spec, ikm, data)`；HMAC 派生原语为 `HMAC(ikm, ikm)`（自 HMAC，输出 HashLen 字节，spec.keyLen 仅校验合法性、不影响输出长度）

### 依赖清单

`aifei`(provided) + `ip2region`/`aifei-cache`/`zxing`(optional) + `junit`(test)；kdf 纯 JDK，不新增任何第三方依赖。零日志绑定、零 DB、零 Web 面、无 Plugin、无配置键、无 SPI。
