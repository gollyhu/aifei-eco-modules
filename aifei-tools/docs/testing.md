# aifei-tools 测试策略与用例清单

> 面向实现者与质量关卡。契约事实来源为 `docs/design.md`（§6.2 门禁、§7 异常语义、§8 定档决策、§9 P0 映射）；本文只落地测试策略、用例清单与运行结果，不重复决策理由。
> 本模块为库模块，无 Plugin / 门面装配 / Web 面，测试全部在模块内完成，0 failure / 0 error 为准入线（根 `AGENTS.md`）。

## 1. 测试策略

四条策略贯穿全部用例，目的是「完全离线、可重复、零外链生产数据」：

| 策略 | 做法 | 理由 |
| --- | --- | --- |
| **全离线** | 测试源无任何外部 URL（门禁⑭断言 host 仅 `127.0.0.1` / `localhost`）；无联网 IP 查询、无外部服务 | CI 与开发机无网也能跑，结果稳定 |
| **xdb 样本内嵌** | 最小 v4/v6 xdb 以 gzip + base64 常量固化在 `support/IpSampleData.java`（源码零二进制）；首次取用解码到 `target/`，不入库 | 使用真实 ip2region 3.3.7 内核端到端校验，又不提交二进制数据文件（§7.4 定档） |
| **依赖缺失隔离实测** | 用 `support/HiddenClassClassLoader` 隐藏内核包前缀，反射调用 `*Deps` 探测方法，断言抛可读模块异常 | 真实模拟「消费方未引 optional 内核」，验证不裸抛 `NoClassDefFoundError` |
| **集群存储内存 Mock** | `CaptchaStoreTest` 用动态代理实现 `Cache` / `CounterFactory`，值存内存 `ConcurrentHashMap`，注册进 `CacheManager` | 验证集群存储三态与 TTL，不连真实 Redis，保持离线 |

样本内容（真实 ip2region 3.3.7 的 xdb 结构 3.0 一次性生成后固化）：境内 IPv4 中文五段、境外 IPv4 英文五段、Reserved 保留段、含 `"0"` 哨兵段、IPv6 中文段、IPv6 保留段、未收录 IP。三模式一致性用同一份样本、同一 IP 逐字比对。

## 2. 用例构成与结果

实测口径来自 `mvn clean verify` 的 surefire 报告（`EXIT=0`、`BUILD SUCCESS`）：

| 分组 | 测试类 | 用例数 |
| --- | --- | --- |
| 架构门禁 | `ArchitectureBoundaryTest` | 17 |
| ipdb | `IpSearcherTest` 15 / `IpRegionTest` 8 / `IpKitTest` 3 / `PublicApiContractIpTest` 9 | 35 |
| captcha | `CaptchaKitTest` 10 / `CaptchaStoreTest` 7 / `PublicApiContractCaptchaTest` 8 | 25 |
| qr | `QrKitTest` 11 / `PublicApiContractQrTest` 8 | 19 |
| mask | `MaskUtilTest` 12 / `MaskKitTest` 8 / `PublicApiContractMaskTest` 7 | 27 |
| **合计** | 门禁 17 + 功能 106 | **123** |

**结果：`Tests run: 123, Failures: 0, Errors: 0, Skipped: 0`。** `target/aifei-tools-1.0.0.jar` 产出，主 JAR class major 52（门禁⑨ + 各 `PublicApiContract*Test.mainClasses_majorVersionIsJava8` 双重断言）。

> **数字口径（最终确认）**：用例总数 **123（门禁 17 + 功能 106）**，已由主理人独立复跑确认（`Tests run: 123, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`）。功能用例含测试关卡最后补强新增的 1 例 §8-D14 权威向量逐字断言（`MaskUtilTest`）。本文所有数字以磁盘源码 `@Test`（123 个）与 surefire 报告为准，`mvn clean verify` 无任何跳过；请勿再引用补强前的旧总数。

## 3. 用例清单（按子包）

### 3.1 架构门禁 `ArchitectureBoundaryTest`（17 例）

对应 §6.2 定档的 17 条，纯追加、原 14 例断言不删减：

| # | 方法 | 判定口径 |
| --- | --- | --- |
| ① | `rootPackageEmpty` | 根包直接 `.java` 精确 0 |
| ② | `ipPublicSurfaceExact` | `ipdb/` 公共类型精确 5 且集合相等 |
| ③ | `captchaPublicSurfaceExact` | `captcha/` 精确 4 且集合相等 |
| ④ | `ip2regionOnlyInIpInternal` | `import org.lionsoul.` 仅 `ipdb/internal/` |
| ⑤ | `cacheOnlyInCaptchaInternal` | `import cn.hg.aifei.cache.` 仅 `captcha/internal/` |
| ⑥ | `noFrameworkReference` | 全模块禁 `import cn.aifei.` |
| ⑦ | `noWebSqlOrLogBinding` | 禁 undertow / `java.sql` / slf4j，pom 无日志绑定 |
| ⑧ | `publicSignaturesNoThirdParty` | 公共层源码无第三方 / 框架类型字面量 |
| ⑨ | `java8Compatibility` | 禁 `var` / `record` / `List.of` / text block 等 Java 8+ 语法 API |
| ⑩ | `classForNameOnlyInDeps` | `Class.forName` 仅 `*Deps` 两个文件 |
| ⑪ | `errorMessagesOnlyInErrors` | 四类消息前缀仅 `*Errors` 定义（本轮扩充覆盖 qr/mask） |
| ⑫ | `regionParsingOnlyInParser` | region 分隔符与 `"0"` / `"Reserved"` 归空串唯一实现在 `RegionParser` |
| ⑬ | `noServiceProvider` | 禁 `ServiceLoader` / `META-INF/services` |
| ⑭ | `testSourcesOfflineOnly` | 测试源 URL host 仅回环地址，base64 常量允许 |
| ⑮ | `qrPublicSurfaceExact` | `qr/` 精确 4 且集合相等 |
| ⑯ | `zxingOnlyInQrInternal` | `import com.google.zxing.` 仅 `qr/internal/` |
| ⑰ | `maskPublicSurfaceExact` | `mask/` 精确 3 且集合相等 |

### 3.2 ipdb（35 例）

对应 §9「三模式一致 / 双库命中 / 未知段语义 / 异常 / 并发 / 生命周期」验收要点：

- **`IpSearcherTest`（15）**：三模式 FILE_ONLY / VECTOR_INDEX / CONTENT 对同 IP 同 xdb 结果逐字一致；两参 `open` 默认等于 VECTOR_INDEX；境内 IPv4 中文五段 + 境外 IPv4 英文五段命中；保留段与 `"0"` 哨兵归一；IPv6 中文段与保留段；未收录 IP 全空；`close()` 连续三次幂等；关闭后 `search` 抛 `ip: session is closed`；双路径 null / mode null / ip null / 非法 IP（保留 cause）/ xdb 不可读（含路径）均抛可读 `IpException`；隐藏 `org.lionsoul.` 后首次调用抛带坐标的 `IpException`；8 线程 80 次并发查询无串扰。
- **`IpRegionTest`（8）**：五段逐字段映射；`"0"` / `"Reserved"` → 空串；部分 `"0"` 混合字段；null / 空 raw 返回非 null 全空对象；短段缺字段补空；`IpRegion.of` 工厂全 null → 五字段全空、正常值保留。
- **`IpKitTest`（3）**：便捷门面两参 / 四参重载默认走 VECTOR_INDEX，与会话直查一致。
- **`PublicApiContractIpTest`（9）**：`IpSearcher` / `IpKit` / `IpRegion` / `IpException` / `IpQueryMode` 类型 final 且形态正确、逐字签名（含 `IpRegion.of` 工厂与 5 getter）、枚举常量精确、异常仅两个公共构造、公共方法无 `throws`、公共签名零第三方类型、class major 52。

**权威口径（§8-E1 / §7.2，QA 固定断言）**：`IpRegion.of` 五参 null → 五字段均为 `""`；**空白串属合法内容、原样保留，不 trim、不归一**（`" "` / `"\t"` 逐字保留）。region raw 的 `"0"` / `"Reserved"` → 空串；查不到返回五字段全空的非 null 对象。

### 3.3 captcha（25 例）

对应 §9「一次性消费 / TTL / 错误次数 / token 安全 / 单机与集群」验收要点：

- **`CaptchaKitTest`（10）**：生成 → 校验成功 → 同 token 二次失败（一次性消费）；算式型题目答案正确；答错只计数不消费；TTL 到期失效；超错误次数后即使用正确答案也失败；null 输入返回 false；token 唯一且不可从图片字节反推；`generate(null)` 抛可读异常；ttl / capacity / maxFailures 非正数抛 `captcha: ... must be positive`；缺 `aifei-cache` 时 `enableClusterStore()` 抛带坐标的 `CaptchaException`。
- **`CaptchaStoreTest`（7）**：LRU 容量淘汰（访问刷新最近使用）、TTL 过期视为未命中并删除、三态（成功删 / 失败只计数 / 超限删）；`CaptchaStore` 默认 `remove` 空实现使最小桩可编译；Cluster 存储经内存 Mock 覆盖 save/consume/remove 往返、TTL 过期、三态。
- **`PublicApiContractCaptchaTest`（8）**：`Captcha` / `CaptchaKit` / `CaptchaType` / `CaptchaException` 类型 final、逐字签名、枚举常量 CHAR/ARITHMETIC 精确、异常仅两公共构造、公共方法无 `throws`、零第三方类型、class major 52。

### 3.4 qr（19 例）

对应 §9「闭环 / 四档 EC 与 options / 中文 UTF-8 / 流不关闭 / 缺失异常 / 公共面」验收要点：

- **`QrKitTest`（11）**：ASCII 与中文 UTF-8 encode→decode 往返；空串 `""` 出图并还原为空串；四档纠错 L/M/Q/H 全部往返；`size` / `margin` 生效（150 vs 400 像素，字节不同且均可还原）；`decode(InputStream)` 不关闭调用方流；`encode(null)`、`decode(null)`、`decode(空 byte[])`、`decode((InputStream)null)` 抛措辞精确的可读 `QrException`；`QrOptions.of(...,null)` 与 `encode(...,null)` 抛可读异常；非法图片 decode 抛 `QrException` 且保留 cause；隐藏 `com.google.zxing.` 后首次调用抛带坐标的 `QrException`。
- **`PublicApiContractQrTest`（8）**：`QrKit` / `QrOptions` / `QrErrorCorrection` / `QrException` 类型 final、逐字签名、`defaults()` = 300/4/M、枚举常量 L/M/Q/H 精确、异常仅两公共构造、公共方法无 `throws`、零第三方类型、class major 52。

**权威口径（§8-C2）**：空串 `""` 是合法内容、encode→decode 正常往返；实现用私有区哨兵 U+E000 达成——**「文本恰好是单个 U+E000 字符的往返」属已知边界，设计未覆盖、不列入验收，QA 不对此落断言**（测试清单无该用例，符合定档）。null / 空输入抛可读 `QrException`；decode 失败抛异常保留 cause、不返回 null（§8-C1/C3）。

### 3.5 mask（27 例）

对应 §9「通用 mask / 9 默认规则 / 三态 / replace 跟随 / 公共面」验收要点：

- **`MaskUtilTest`（12）**：通用 mask 保留左/右位与自定义掩码字符（保留合计超长度时原样）；9 规则统一口径（value null→null、空串→空串）；负数保留位抛 `mask: keepLeft and keepRight must not be negative`；9 默认规则全部示例值（phone/idCard/bankCard/email/name/fixedPhone/password/address/ip）；`maskFixedPhone` 无 `-` 退化为前 4 后 4；`maskPassword` 恒定 6 星不随长度变化；`maskAddress` 短值全打码；`maskIp` IPv4 与 IPv6 压缩式；IPv6 压缩式与展开式等价且 7 个 `*`；畸形文本健壮退化不抛异常；§8-D14 权威向量逐字断言（本轮补强新增的 1 例：三写法逐字断言为 `2001:*:*:*:*:*:*:*`）。
- **`MaskKitTest`（8）**：`register` 已存在抛、`replace` 不存在抛、`use` 未注册抛、name/fn 为 null 抛；默认规则初始在册、不可删；`replace` 后 `MaskUtil.maskXxx` 跟随新规则（`maskXxx(x) ≡ use("xxx").apply(x)` 冻结契约）。
- **`PublicApiContractMaskTest`（7）**：`MaskUtil` / `MaskKit` / `MaskException` 类型 final、逐字签名（含 9 个默认方法与通用 mask 重载）、异常仅两公共构造、公共方法无 `throws`、零第三方类型、class major 52。

**权威口径（§8-D14，实测向量，QA 固定断言）**：`maskIp` 保留首段、其余每段一个 `*`；IPv4 `192.168.2.1` → `192.*.*.*`；IPv6 压缩式 `2001:db8:85a3::8a2e:370:7334` 与逐段补全式 `2001:0db8:85a3:0000:0000:8a2e:0370:7334` 与展开式 `2001:db8:85a3:0:0:8a2e:370:7334` **三者结果一致**，均 → `2001:*:*:*:*:*:*:*`（**7 个 `*`**，`::` 先展开为完整 8 段再打码）；畸形输入（`::` / `:::` / `:a::b` / 超 9 段 / 非法字符 / `garbage` / `a.b.c`）健壮不抛异常，无分隔符特征者退化为保留首字符 + 其余等长 `*`。文本内容非法一律不抛异常，`MaskException` 仅用于规则注册与负数保留位（§8-D13）。

## 4. 运行方法

构建、测试**必须在模块目录执行**（门禁测试依赖相对路径 `src/main/java`，仓库根无聚合 pom，禁止在根执行）：

```bash
cd aifei-tools
mvn clean verify                                  # 全部 123 例，0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest           # 仅门禁 17 例
mvn test -Dtest=IpSearcherTest                     # 仅 ip 会话功能 15 例
mvn test -Dtest="cn.hg.aifei.tools.qr.*"           # 仅 qr 子包
mvn dependency:tree                               # 依赖口径核对
```

- **无需任何开关**：ip 测试走内嵌 xdb 样本（解码到 `target/`），不依赖外部数据文件与网络；captcha 集群用内存 Mock，不连 Redis。设计 §8-A1 提及的「默认关闭的 xdb 集成测试退化方案」**未启用**（因 maker 一次性生成样本已成功固化，无需外部 xdb），故不存在 `-Dip.integration` 之类的开关；开启外部集成测试不是本模块交付内容。
- surefire `workingDirectory=${project.basedir}`，保证门禁相对路径稳定。
- 构建期断言（门禁外）：主 JAR 全 class major 52；`dependency:tree` 仅 §6.1 清单（含 zxing core、无 javase / jcommander / jai-imageio）。

## 5. 结果台账（AC ↔ 用例 ↔ 结果）

| 验收主题（§9） | 落点用例 | 结果 |
| --- | --- | --- |
| 生成→校验→二次失败、TTL、超限、token 不可反推、单机/集群 | `CaptchaKitTest` / `CaptchaStoreTest` | PASS |
| 三模式一致、双库命中、境内中文境外英文、`"0"`/保留段语义、未命中全空 | `IpSearcherTest` / `IpRegionTest` / `IpKitTest` | PASS |
| 非法参数 / xdb 不可读 / 依赖缺失 / 关闭后查询 → 可读 `IpException` | `IpSearcherTest.dependencyMissing_readableException` 等 | PASS |
| 并发无串扰、`close()` 幂等 | `IpSearcherTest.concurrent_noCrosstalk` / `close_idempotent` | PASS |
| 二维码闭环、四档 EC、中文 UTF-8、流不关闭、空串往返、依赖缺失 | `QrKitTest` | PASS |
| 通用 mask、9 默认规则、座机/密码/地址/IP 新规则、IPv6 7 星与畸形健壮 | `MaskUtilTest` | PASS |
| 三态异常、默认规则在册不可删、replace 跟随 | `MaskKitTest` | PASS |
| 公共面形态 / 签名逐字一致 / 零第三方 / class major 52 | 四 `PublicApiContract*Test` | PASS |
| 根包零类型 + 各子包精确 N、依赖边界、Java8、离线 | `ArchitectureBoundaryTest`（17） | PASS |

> 全部 123 例 0 failure / 0 error；无已知遗留问题。任何门禁断言不得放宽；冻结契约的判定规则与异常消息变更须先更新断言（根 `AGENTS.md`）。
