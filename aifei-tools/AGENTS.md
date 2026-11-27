# aifei-tools 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与架构门禁、唯一定义点清单、已定档行为契约红线与验证命令。

四份文档分工：`README.md` 面向使用者（安装、用法、可选依赖警告）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **改代码前必读 `docs/design.md`**（尤其 §1.1 模块划分、§2 文件清单、§3 公共类型逐字签名、§6 依赖与 21 例门禁、§7 约定/异常语义/唯一定义点、§8 已定档决策，以及 §11 通用原语增量设计 / §11.7 的 33 例门禁）。需求与设计冲突时，**先改设计决策再改代码**。
- 未经设计决策，不得自行确定契约类行为：空值语义、异常类型与消息、默认值（TTL/capacity/maxFailures/查询模式）、并发策略、哨兵与退化口径等；也不得新增或扩大公共 API 面。
- 发现设计问题只回报主理人，**不在实现期自行扩张或改写 `docs/design.md`**。公共面签名不可行时先回写 `docs/design.md` §3，不得先斩后奏。

## 交付性质（库模块，非业务应用）

`aifei-tools` 是 aifei 生态的**库模块（SDK，可被应用依赖）**，**不是应用工程**。坐标 `io.github.gollyhu:aifei-tools:1.1.0-SNAPSHOT`（`packaging=jar`），包名 `cn.hg.aifei.tools`，Java 8（class major 52）。定位「极简工具集」：**根包零公共类型**，九个工具按子包隔离、各自独立可用、无跨工具抽象。

**不适用章节（不得据此模块约束要求）**：

| 常规章节 | 结论 | 理由 |
| --- | --- | --- |
| 业务服务 / 子系统拓扑 / `@Path` / HIO / 路由 / 拦截器 | 不适用 | 单一 JAR 库、无入站 HTTP 面；无 Plugin 装配期 |
| 表结构 / `Db+Row` / `Generator` / `java.sql` | 不适用 | 零 DB，门禁⑦反向断言全模块无 `import java.sql.` |
| 页面 / 原型 / QA 基线门禁 | 不适用 | 无页面；本轮不配 demo（T12 本轮不做） |
| `AppConfig implements AifeiConfig` / `Launcher` / 配置键 | 不适用 | 无连接资源、无配置可解析；行为全部由方法入参显式传入 |
| provider / SPI / `META-INF/services` | 不适用 | 门禁⑬反向断言禁用 |

## 硬性约束

### 技术基线

- **Java 8 双约束（语法 + API）**：`source`/`target` = `1.8`；主 JAR 内全部 class major = **52**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / `instanceof` 模式匹配；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 后 API（门禁⑨）。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、协议字面量与异常消息）。原创文件写统一版权头，既有版权块不得改写。
- **行尾**：`.md` 输出 CRLF；Java 源码保持仓库现状不做无关行尾改写。批量改写按字节整文件读入 → 逐处断言命中次数 → 全部通过才单次写回，不得对同一文件并发多次编辑。
- 构建/测试命令一律在**模块目录**执行（门禁依赖相对路径 `src/main/java`）；仓库根无聚合 pom，不得在仓库根执行模块测试。

### 依赖（不可推翻）

pom 只允许下列五项，属性 `<ip2region.version>3.3.7</ip2region.version>` 与 `<zxing.version>3.5.3</zxing.version>` 并列管理：

| 依赖 | scope | 约束 |
| --- | --- | --- |
| `cn.aifei:aifei-all:1.1.2` | **provided** | 生态统一底座；本模块源码零引用、不传递 |
| `org.lionsoul:ip2region:3.3.7` | **optional** | ip 内核，经 `ipdb/internal` 引用；阻断传递；缺失→`IpException` |
| `io.github.gollyhu:aifei-cache:1.1.0` | **optional** | captcha 集群存储，经 `captcha/internal` 反射探测；阻断传递；缺失→`CaptchaException` |
| `com.google.zxing:core:3.5.3` | **optional** | qr 内核，经 `qr/internal` 引用；零传递依赖；阻断传递；缺失→`QrException`；**不引 javase** |
| `junit:junit:4.13.2` | **test** | 单测 + 门禁 |

- **不得新增其它第三方依赖**；不得引入任何日志绑定（`slf4j-simple` / `logback` / `log4j` 一律禁止，门禁⑦）。
- 版本实证（见 design §0）：ip2region 锁 **3.3.7**（3.3.8 已切 Java 11 基线，有实证故弃用）；zxing core 3.5.3 已 `javap` 实测 class major 52；javase 因拖入 `jcommander`/`jai-imageio-core` 不引。
- **根 `AGENTS.md` 登记清单为空**（公共面零框架/第三方类型、无 compile 例外）。新增 compile 例外或公共面框架类型须先在根 `AGENTS.md` 登记。

### 公共面收敛（不得放宽）

- **根包 `cn.hg.aifei.tools` 精确 0 公共类型**（门禁①）；不创建 `package-info.java`。
- 九子包共**精确 28 个公共类型**（既有 20 + 新增 8），签名逐字见 `docs/design.md` §3 与 §11.3：

| 子包 | 公共类型（N） |
| --- | --- |
| `ipdb` | `IpSearcher` / `IpKit` / `IpRegion` / `IpException` / `IpQueryMode`（5） |
| `captcha` | `Captcha` / `CaptchaKit` / `CaptchaType` / `CaptchaException`（4） |
| `qr` | `QrKit` / `QrOptions` / `QrErrorCorrection` / `QrException`（4） |
| `mask` | `MaskUtil` / `MaskKit` / `MaskException`（3） |
| `kdf` | `KdfKit` / `KdfSpec` / `KdfHash` / `KdfException`（4） |
| `crypto` | `CryptoUtil` / `CryptoException`（2） |
| `random` | `RandomUtil` / `RandomException`（2） |
| `xml` | `XmlUtil` / `XmlException`（2） |
| `codec` | `CodecUtil` / `CodecException`（2） |

- **不得增删、不得改名**；新增公共类型或改签名须先回写 `docs/design.md` §3 过设计决策再改门禁期望集合。**唯一已定档方法级例外**：`IpRegion.of(String,String,String,String,String)` 静态工厂（design §8-E1），供 `ipdb.internal` 构造公共值对象，五个 getter 与「字段永不为 null」契约冻结不变。
- 公共签名**零第三方类型、零框架类型**：只含 JDK 类型（`InputStream` / `String` / `UnaryOperator` / 基本类型等）+ 本模块类型；不出现 `org.lionsoul.*` / `com.google.zxing.*` / `cn.aifei.*` / `cn.hg.aifei.cache.*`（门禁⑧）。
- **无 Plugin、无配置键、无 SPI、无 `aifei` 接入包、零 `cn.aifei.*` 引用、零日志绑定、零 DB、零 Web 面**（门禁⑥⑦⑬）。

## 包结构与依赖方向

```text
cn.hg.aifei.tools              根包：精确 0 公共类型（不创建任何 .java）
cn.hg.aifei.tools.ipdb         ip：5 公共类型 + internal（非承诺）
cn.hg.aifei.tools.captcha      captcha：4 公共类型 + internal（非承诺）
cn.hg.aifei.tools.qr           qr：4 公共类型 + internal（非承诺）
cn.hg.aifei.tools.mask         mask：3 公共类型 + internal（非承诺）
cn.hg.aifei.tools.kdf          kdf：4 公共类型 + internal（非承诺）
cn.hg.aifei.tools.crypto      crypto：2 公共类型 + internal（非承诺）
cn.hg.aifei.tools.random      random：2 公共类型 + internal（非承诺）
cn.hg.aifei.tools.xml         xml：2 公共类型 + internal（非承诺）
cn.hg.aifei.tools.codec       codec：2 公共类型 + internal（非承诺）
```

- `org.lionsoul.*` 只允许在 `ipdb/internal/`（门禁④）；`cn.hg.aifei.cache.*` 只允许在 `captcha/internal/`（门禁⑤）；`com.google.zxing.*` 只允许在 `qr/internal/`（门禁⑯）；`javax.crypto.*` / `java.security.*` 只允许在 `kdf/internal/`、`captcha/internal`（既有）、`crypto/internal/`、`random/internal/`，其余位置一律禁止（门禁⑲，设计 §11.5 / §11.7 定档放宽）；`javax.xml.parsers.*` / `org.w3c.dom.*` 只在 `xml/internal/`，`java.util.zip.*` 只在 `codec/internal/`。
- 公共层（非 internal）→ 纯 JDK + 本模块类型；internal → 各自 optional 内核 + JDK。工具之间互不引用、无跨工具抽象层。

## 包边界与门禁

`ArchitectureBoundaryTest`（**33 例**：原 21 + 通用原语追加 12，扫描 `src/main/java`；⑭ 扫 `src/test/java`）判定口径与 `docs/design.md` §6.2 与 §11.7 **逐字一致**：

| # | 测试方法 | 判定口径 |
| --- | --- | --- |
| ① | `rootPackageEmpty` | 根包 `cn/hg/aifei/tools/` 直接子级 `.java` **精确 0**（不创建 package-info.java；若存在则显式排除并说明） |
| ② | `ipPublicSurfaceExact` | `ipdb/` 直接子 `.java` 精确 5 且集合 = {IpSearcher,IpKit,IpRegion,IpException,IpQueryMode}（集合相等，防同名替换） |
| ③ | `captchaPublicSurfaceExact` | `captcha/` 直接子 `.java` 精确 4 且集合 = {Captcha,CaptchaKit,CaptchaType,CaptchaException} |
| ④ | `ip2regionOnlyInIpInternal` | `import org.lionsoul.` 仅允许 `ipdb/internal/` 下文件（ip2region 依赖边界） |
| ⑤ | `cacheOnlyInCaptchaInternal` | `import cn.hg.aifei.cache.` 仅允许 `captcha/internal/` 下文件 |
| ⑥ | `noFrameworkReference` | 全模块禁止 `import cn.aifei.`（无接入包、无 log 例外） |
| ⑦ | `noWebSqlOrLogBinding` | 禁 `io.undertow.`/`java.sql.`/`org.slf4j.`；pom 不声明 logback/log4j/slf4j-simple |
| ⑧ | `publicSignaturesNoThirdParty` | 五子包公共层（非 internal）源码不得出现 `org.lionsoul.`/`com.google.zxing.`/`cn.aifei.`/`cn.hg.aifei.cache.` 字面量（kdf 扩充覆盖扫描，原四 forbidden 字面量不变） |
| ⑨ | `java8Compatibility` | 禁 `List.of(`/`Map.of(`/`Set.of(`/`record `/`var `/`"""`/`sealed `/`instanceof` 模式匹配 |
| ⑩ | `classForNameOnlyInDeps` | `Class.forName` 仅允许 `IpDeps.java`/`CaptchaDeps.java` |
| ⑪ | `errorMessagesOnlyInErrors` | `ip: `/`captcha: `/`qr: `/`mask: `/`kdf: ` 消息前缀仅允许对应 `*Errors.java`（测试断言与公共 javadoc 除外）——kdf 扩充覆盖 `kdf: ` 前缀，原四组断言逐字保留 |
| ⑫ | `regionParsingOnlyInParser` | region 分隔符 `\|` 及 `"0"`/`"Reserved"` 归空串的唯一实现只在 `RegionParser.java` |
| ⑬ | `noServiceProvider` | 禁 `ServiceLoader`/`META-INF/services`（无 SPI） |
| ⑭ | `testSourcesOfflineOnly` | 测试源 URL host 仅 `127.0.0.1`/`localhost`；`IpSampleData` 的 base64 常量允许 |
| ⑮ | `qrPublicSurfaceExact` | `qr/` 直接子 `.java` 精确 4 且集合 = {QrKit,QrOptions,QrErrorCorrection,QrException} |
| ⑯ | `zxingOnlyInQrInternal` | `import com.google.zxing.` 仅允许 `qr/internal/` 下文件 |
| ⑰ | `maskPublicSurfaceExact` | `mask/` 直接子 `.java` 精确 3 且集合 = {MaskUtil,MaskKit,MaskException} |
| ⑱ | `kdfPublicSurfaceExact` | `kdf/` 直接子 `.java` 精确 4 且集合 = {KdfKit,KdfSpec,KdfHash,KdfException}（口径对齐门禁②③⑮⑰） |
| ⑲ | `cryptoOnlyInKdfInternal` | `import javax.crypto.` / `import java.security.` 允许 `kdf/internal/`、`captcha/internal/`（既有）、`crypto/internal/`、`random/internal/`，其余位置一律禁止（设计 §11.7 定档放宽；方法名与断言文本不变） |
| ⑳ | `kdfPrefixOnlyInErrors` | `kdf: ` 消息前缀仅允许 `KdfErrors.java`（测试断言与公共 javadoc 除外）——扩充门禁⑪覆盖 `kdf: ` 前缀 |
| ⑳′ | `publicSignaturesNoThirdPartyKdf` | `kdf/` 公共层（非 internal）源码不得出现 `javax.crypto.`/`java.security.`/`org.lionsoul.`/`com.google.zxing.`/`cn.aifei.`/`cn.hg.aifei.cache.` 字面量——kdf 公共签名零 JDK 加密类型、零第三方 |
| ㉑ | `cryptoPublicSurfaceExact` | `crypto/` 直接子 `.java` 精确 2 且集合 = {CryptoUtil,CryptoException} |
| ㉒ | `randomPublicSurfaceExact` | `random/` 直接子 `.java` 精确 2 且集合 = {RandomUtil,RandomException} |
| ㉓ | `xmlPublicSurfaceExact` | `xml/` 直接子 `.java` 精确 2 且集合 = {XmlUtil,XmlException} |
| ㉔ | `codecPublicSurfaceExact` | `codec/` 直接子 `.java` 精确 2 且集合 = {CodecUtil,CodecException} |
| ㉕ | `cryptoPrefixOnlyInErrors` | `crypto: ` 前缀仅允许 `CryptoErrors.java`（口径同⑳） |
| ㉖ | `randomPrefixOnlyInErrors` | `random: ` 前缀仅允许 `RandomErrors.java` |
| ㉗ | `xmlPrefixOnlyInErrors` | `xml: ` 前缀仅允许 `XmlErrors.java` |
| ㉘ | `codecPrefixOnlyInErrors` | `codec: ` 前缀仅允许 `CodecErrors.java` |
| ㉙ | `publicSignaturesNoThirdPartyCrypto` | `crypto/` 公共层零 JDK 加密 / 第三方 / 框架字面量，并禁握手常量串 / `sortedSign` / `canonical` / 签名字段名 |
| ㉚ | `publicSignaturesNoThirdPartyRandom` | `random/` 公共层零 JDK 加密 / 第三方 / 框架字面量，并禁来源类型名（Captcha/TokenGenerator/ImageGenerator） |
| ㉛ | `publicSignaturesNoThirdPartyXml` | `xml/` 公共层零 JDK 加密 / 第三方 / 框架字面量，并禁协议根节点与来源字段名 |
| ㉜ | `publicSignaturesNoThirdPartyCodec` | `codec/` 公共层零 JDK 加密 / 第三方 / 框架字面量，并禁来源配置键与传输头 |

门禁**不得删除、放宽或绕过**，也不得为兼容错误用法而调整；新增例外必须先改 `docs/design.md` 再改断言并过主理人登记。构建期断言（门禁外）：主 JAR 全 class major=52（`javap` 抽查 `IpSearcher`/`IpKit`/`IpSession`/`CaptchaKit`/`QrKit`/`QrDeps`/`MaskKit`/`KdfKit`/`CryptoUtil`/`RandomUtil`/`XmlUtil`/`CodecUtil`）；`mvn dependency:tree` 仅出现上述依赖清单（含 zxing 且无 javese/jcommander/jai-imageio，四个新子包零新增第三方）。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 依赖探测（ip2region） | `ipdb.internal.IpDeps`（唯一 `Class.forName` 点之一） |
| `IpException` 构造与全部 `ip: ` 消息 | `ipdb.internal.IpErrors` |
| region 五段解析与 `"0"`/`"Reserved"`→空串 | `ipdb.internal.RegionParser` |
| Config 构建 / `Ip2Region.create` / `close()` 幂等 | `ipdb.internal.IpSession` |
| 依赖探测（aifei-cache） | `captcha.internal.CaptchaDeps`（唯一 `Class.forName` 点之一） |
| `CaptchaException` 构造与全部 `captcha: ` 消息 | `captcha.internal.CaptchaErrors` |
| token 随机与题目/答案生成 | `captcha.internal.TokenGenerator` |
| captcha 答案存储三态（save/consume/fail/remove） | `captcha.internal.CaptchaStore` + 两个实现 |
| ZXing 依赖探测（禁用 `Class.forName`） | `qr.internal.QrDeps`（方法内延迟符号解析 + catch `NoClassDefFoundError`/`LinkageError`） |
| `QrException` 构造与全部 `qr: ` 消息 | `qr.internal.QrErrors` |
| `BitMatrix`↔PNG 编解码与空串哨兵 | `qr.internal.ImageCodec` |
| mask 注册表（唯一 `ConcurrentHashMap`） | `mask.internal.MaskRegistry` |
| `MaskException` 构造与全部 `mask: ` 消息 | `mask.internal.MaskErrors` |
| 9 默认规则初始注册（不可删） | `mask.internal.DefaultMasks` |
| `KdfException` 构造与全部 `kdf: ` 消息 | `kdf.internal.KdfErrors` |
| HKDF extract+expand（RFC 5869） | `kdf.internal.HkdfEngine` |
| PBKDF2（RFC 8018 §5.2，手动 HMAC PRF 循环） | `kdf.internal.Pbkdf2Engine` |
| HMAC 派生原语（RFC 4231，`HMAC(ikm, ikm)`） | `kdf.internal.HmacEngine` |
| `CryptoException` 构造与全部 `crypto: ` 消息 | `crypto.internal.CryptoErrors` |
| SHA-1 摘要（十六进制 / 标准 Base64） | `crypto.internal.Sha1Engine` |
| HMAC-SHA1 / HMAC-SHA256 | `crypto.internal.HmacEngine` |
| 常量时间比较 | `crypto.internal.ConstantTimeEngine` |
| `RandomException` 构造与全部 `random: ` 消息 | `random.internal.RandomErrors` |
| 安全随机取样与 Base64URL 令牌 | `random.internal.RandomEngine` |
| `XmlException` 构造与全部 `xml: ` 消息 | `xml.internal.XmlErrors` |
| DOM 解析与 XXE 硬化（六项特性） | `xml.internal.XmlParser` |
| `CodecException` 构造与全部 `codec: ` 消息 | `codec.internal.CodecErrors` |
| gzip 解压 | `codec.internal.GzipEngine` |

- **命名后缀**：`XxxUtil` 无状态工具（全 static、结果只由入参决定）；`XxxKit` 有状态门面（操作有生命周期的会话/注册表，全 static）；会话对象/值对象用名词（`IpSearcher`/`Captcha`/`IpRegion`/`QrOptions` 等命名例外，已登记 design §7.1）；`internal` 用 `Deps/Errors/Session/Parser/Store/Generator/Registry/Codec/Masks`。
- **消息前缀唯一定义点**：`ip: ` 仅在 `IpErrors`、`captcha: ` 仅在 `CaptchaErrors`、`qr: ` 仅在 `QrErrors`、`mask: ` 仅在 `MaskErrors`、`kdf: ` 仅在 `KdfErrors`、`crypto: ` 仅在 `CryptoErrors`、`random: ` 仅在 `RandomErrors`、`xml: ` 仅在 `XmlErrors`、`codec: ` 仅在 `CodecErrors`（门禁⑪及新增四例 + 测试断言与公共 javadoc 除外）。

## 已定档行为契约红线（QA 与后续改动的依据）

以下均为 `docs/design.md` §7/§8 已定档口径，**改代码即改契约，须先回写设计**：

- **ip**：默认模式 `VECTOR_INDEX`（两参重载同）；三模式 FILE_ONLY/VECTOR_INDEX/CONTENT 映射内核 NoCache/VIndexCache/FullCache，对同 IP 同 xdb 结果须一致；并发 = 实例独占 + 内核 `SearcherPool`（searchers = CPU 核数、fairLock=false，不自建 ThreadLocal、不裸用 `xdb.Searcher`）；`IpRegion` 五段字段永不为 null，`"0"`/`"Reserved"`→空串、查不到返回五字段全空串对象；`close()` 幂等、关闭后查询抛可读 `IpException`；xdb 路径显式入参、不打包不联网。
- **`IpRegion.of(...)`**：只把 null 归一为空串（`x == null ? "" : x` 五段同构），**空白串属合法内容、原样保留**（不 trim、不归一）。
- **captcha**：`verify` 一次性消费（成功即删、失败计数、超限失效，业务失败返回 `false` 不抛异常）；默认 TTL=120s / capacity=10000 / maxFailures=5；默认进程内 LRU，切集群走 optional aifei-cache；token = `SecureRandom` 32 字节 Base64 URL 无填充，不可从图片反推。
- **qr**：`encode`/`decode` 均保留；`encode(null)`/`decode(null)`/`decode(空 byte[])` 抛 `QrException`；空串 `""` 合法且可往返（实现用私有区单字符 `\uE000` 哨兵）；**单个 U+E000 字符往返被误归一为空串属已知限制、不列入验收、QA 不落断言**；decode 失败抛 `QrException` 保留 cause、不返回 null；`decode(InputStream)` 不关闭调用方流；`QrOptions` 冻结为 size/margin/EC 三项 + `defaults()`/`of()`（默认 300/4/M、UTF-8 内部固定）；不加流式输出。
- **mask**：通用 `mask` + 9 默认规则（phone/idCard/bankCard/email/name/fixedPhone/password/address/ip）初始在册不可删，`maskXxx(x)≡use("xxx").apply(x)`、`replace` 后跟随；value 为 null 返回 null、空串返回空串；`keepLeft`/`keepRight` 负数抛 `MaskException`；`maskIp` 按分隔符特征打码——含 `.` 按 `.` 分段，含 `:` 视为 IPv6 且 `::` 先展开为完整 8 段再打码（首段 + 其余每段一个 `*`，示例 IPv6 为 7 个 `*`），等价写法结果一致、畸形输入健壮不抛异常，无分隔符退化为保留首字符 + 等长 `*`；内容文本非法一律不抛 `MaskException`，`MaskException` 仅用于规则注册与负数 keep。
- **kdf**：`KdfKit.derive(spec, ikm)` 按 `spec.getAlgorithm()` 分派到 `HkdfEngine` / `Pbkdf2Engine` / `HmacEngine`；algorithm 合法值集合 {HKDF, PBKDF2, HMAC} 在 `KdfSpec` 工厂内校验、非法抛 `KdfException`；`KdfSpec` 三静态工厂是构造唯一入口、值对象不可变（私有构造器、salt/info 防御性拷贝）；HKDF salt 可空（默认全 0 HashLen 字节）、info 可空；PBKDF2 salt 不可空、iterations > 0；HMAC 无 salt/iterations（getSalt 返回 null、getIterations 返回 0）；HMAC 派生原语为 `HMAC(ikm, ikm)`（自 HMAC，输出 HashLen 字节，spec.keyLen 仅校验合法性、不影响输出长度）；`javax.crypto.*` / `java.security.*` import 仅 `kdf/internal/`（captcha/internal 既有 `SecureRandom` 保留，门禁⑲偏离已记录）；公共签名零 JDK 加密类型、零第三方（门禁⑳′）；PBKDF2 实现偏离原设计 `SecretKeyFactory` 手法，改用手动 HMAC PRF 循环（见 design §8-F 实现期偏离记录）。
- **crypto**：文本编码固定 UTF-8；SHA-1 输出 40 位小写十六进制或标准 Base64；HMAC 遵循 RFC 2104 参数顺序（key 在前、data 在后）并输出标准 Base64；`expected` / `actual` 任一为 null 时常量时间比较返回 false 不抛异常；算法不可用保留底层 cause。
- **random**：`randomString(length)` 字符集固定 62 个 ASCII 字母数字，`length >= 0`、0 返回空串、负数抛 `RandomException`；`token()` 为 32 字节安全随机源转无填充 Base64URL（43 字符）；唯一共享状态为 JDK 线程安全的 `SecureRandom`；不与 captcha 互引。
- **xml**：`parse` 返回 JDK 保序映射、只取根节点直接子元素、同名取首、非元素节点跳过；六项 XXE 硬化在 `XmlParser` 单点定义，文档类型声明命中即抛异常不展开；`escape` 只做五实体单次替换；不硬编码协议根节点与字段名。
- **codec**：`gunzip(null)` 返回 null、`gunzip(空数组)` 返回该数组；解压失败抛 `CodecException` 保留 cause、不静默返回 null；不读取配置键、不干涉传输层协商。

## 测试纪律

- 测试全部**零联网**，URL host 仅 `127.0.0.1`/`localhost`（门禁⑭）；不提交 xdb 二进制——内嵌 base64 最小样本（`support/IpSampleData.java`）测试期解码到 `target/`。
- 断言消息照抄 `docs/design.md` §7.2 与 `*Errors` 的逐字措辞，不得用常量拼期望值（否则措辞漂移无法被发现）。
- maskIp 固定向量直接用 design §8-D14 已实测样本；新增失败路径必须同时落三处：`*Errors` 消息构造、design §7.2 消息表、测试逐字断言。
- 已修复缺陷必须固化为回归测试；测试须独立、幂等。

## 注释规范

- 语言一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、协议字面量与异常消息。
- 公共类型与公共成员**必须**写 javadoc 并写全 `@param` / `@return` / `@throws`；行为契约（空值、默认值、并发、异常）写在 javadoc 里。
- 只在代码无法表达「为什么」时写注释；不复述下一行代码、不写行尾注释、不留死代码与 TODO；文件头版权块属归属信息，不得改写。

## 验证

**命令在 `aifei-tools/` 根目录执行**（门禁依赖相对路径 `src/main/java`；仓库根无聚合 pom）：

```bash
mvn clean verify                                  # 必须 235 例 0 failure / 0 error（含架构门禁 33 例）
mvn test -Dtest=ArchitectureBoundaryTest           # 仅跑架构门禁（33 例）
mvn dependency:tree                                # 断言：aifei-all(provided) + ip2region/zxing/aifei-cache(optional) + junit(test)，无 javese/jcommander/jai-imageio，四个新子包零新增第三方
javap -verbose target/classes/cn/hg/aifei/tools/ipdb/IpSearcher.class | findstr "major version"   # 期望 52
javap -verbose target/classes/cn/hg/aifei/tools/kdf/KdfKit.class | findstr "major version"        # 期望 52
javap -verbose target/classes/cn/hg/aifei/tools/crypto/CryptoUtil.class | findstr "major version" # 期望 52
```

> **零联网纪律**：全部单测离线可重复；xdb 样本内嵌 base64、不提交二进制、不外链生产数据。

## 冻结契约 vs 可演进

- **冻结契约（改动须先过设计决策并更新测试断言）**：28 个公共类型与 §3 / §11.3 逐字签名；九类异常消息措辞与前缀唯一定义点；region 五段解析与 `"0"`→空串语义；依赖坐标/scope（含 zxing 不引 javase，四个新子包无新增第三方）；默认模式 VECTOR_INDEX 与并发策略；`IpRegion.of` null 归一口径；maskIp IPv6 展开与退化口径；qr 空串哨兵契约；四个新子包的 null / 空串 / Base64 / 保序 / 五实体 / 原样返回语义与零协议语义；33 例门禁判定口径（原 21 例逐字保留，唯一放宽为⑲ import 边界，新增 12 例）。
- **可自主演进（不破坏门禁与冻结契约前提下）**：`internal/*` 实现细节与私有工具类；测试样本的具体字节与桩实现；文档措辞与 javadoc 细化；demo（本轮不配，后续可选立项）。
- 本模块为 Java 8 / class major 52 硬基线；若将来拟放宽 Java 基线（如 ip2region 3.3.8），须重走设计决策并同步 design §0 实证记录。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）、`README.md`（用法/可选依赖警告）与 `docs/testing.md`（用例），并确认门禁断言未放宽。
- 术语与 `docs/design.md` 统一：极简工具集 / 子包隔离 / 根包零公共类型 / 五段 region / 三模式 / `IpRegion` 值对象 / 一次性消费 / 哨兵 / 默认规则注册表 / 宽松退化。

## 扩展新工具（追加第 5、第 6 … 个）

> 详版约定见 `docs/design.md` §10。追加工具**不是放开既有契约**——下列四条是不可省的硬动作：

1. **新增子包**：落 `cn.hg.aifei.tools.<新工具>`；公共类型放子包直接子级、internal 实现放 `<子包>/internal/`；根包精确 0 公共类型恒定，不创建 `package-info.java`。同构骨架参照 design §10.1。
2. **门禁纯追加**：在 `ArchitectureBoundaryTest.java` 追加「新子包公共面精确 N + 集合相等」断言；引入新内核则追加「该 import 仅允许在新子包 internal」边界断言；新消息前缀纳入门禁⑪、公共签名零第三方扩充门禁⑧覆盖。**原 21 例逐字不删改、不放宽**（已定档例外仅 §11.7 的⑲ import 边界放宽），只做追加（例数 21 → N，验证命令期望数同步改写）。
3. **不改既有签名**：既有九子包 28 个公共类型与 §3 / §11.3 逐字签名、§8 决策全部冻结；新工具公共签名只含 JDK + 本模块类型、零第三方/框架类型、公共方法无 `throws`、javadoc 写全；后缀 `XxxUtil`（无状态）/ `XxxKit`（有状态门面），不另立 `XxxHelper`/`XxxSupport`/`XxxTool`。
4. **文档四同步**：先回写 `docs/design.md`（§1.1 / §2 / §3 / §5 / §6.2 / §9）过设计决策，再同步 `README.md`、本文件（公共面清单/门禁表/唯一定义点）与 `docs/testing.md`；测试按各子包同构（`PublicApiContract` + 功能测试 + internal 单测）。

- **依赖**：新内核一律 `optional` 阻断传递、缺失首次调用抛可读模块异常；`aifei-all` 仍 provided；零日志绑定。
- **不做跨工具抽象**：不引入统一 `Tool` 接口、公共基类或 `common/` 层，工具之间零依赖、零互相引用。
- **版本**：能力扩展走 minor 递增（`1.0.0` → `1.1.0` …）；首次新增工具时建立 `CHANGELOG.md` 并逐次记录。
