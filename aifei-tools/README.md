# aifei-tools

## 1. 定位与场景

把“几十行就能写完、却每个应用都要抄一遍”的极简能力收拢到一个 JAR：**图形验证码、离线 IP 归属地、二维码、通用脱敏**，四个工具互不相关、各用一个子包，取用哪个就只用哪个，互不牵连。

- 坐标：`io.github.gollyhu:aifei-tools:1.0.0`（`packaging=jar`）
- 包名：`cn.hg.aifei.tools`；Java 8（class major 52）；根包零公共类型
- 首批四个工具：`captcha` 验证码 / `ipdb` IP 归属地 / `qr` 二维码 / `mask` 脱敏

**适合**：单体或小应用里需要上述某一项能力、又不愿为它单独引一个框架或起一个服务；**不适合**：滑块 / 点选行为验证、短信邮件验证码发送、联网 IP 库、经纬度与风控画像——这些都不在本模块范围内。

## 2. 5 分钟快速开始

加依赖：

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-tools</artifactId>
    <version>1.0.0</version>
</dependency>
```

`aifei-all:1.1.2` 由消费方自带（本模块对它 `provided`、不传递）。四个工具各给一个最小闭环，按需取用即可。

**脱敏（纯 JDK，零额外依赖）**

```java
import cn.hg.aifei.tools.mask.MaskUtil;

String safePhone = MaskUtil.maskPhone("13800138000");   // 138****8000
String safeIp = MaskUtil.maskIp("192.168.2.1");          // 192.*.*.*
```

**验证码（默认进程内存储，单机开箱即用）**

```java
import cn.hg.aifei.tools.captcha.Captcha;
import cn.hg.aifei.tools.captcha.CaptchaKit;

// 登录页生成验证码，把 token 下发前端、把图片字节写给浏览器
Captcha captcha = CaptchaKit.generate();
String token = captcha.getToken();
byte[] png = captcha.getImageBytes();

// 用户提交后校验：同一个 token 校验成功一次即失效
boolean passed = CaptchaKit.verify(token, userInput);
```

**二维码（需显式引入 `com.google.zxing:core:3.5.3`，只引 `core`、不引 `javase`——后者会额外拖入命令行与图像 IO 库）**

```xml
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
```

```java
import cn.hg.aifei.tools.qr.QrKit;

byte[] png = QrKit.encode("https://example.com/order/12345");
String text = QrKit.decode(png);   // 还原为原文
```

**IP 归属地（需显式引入 `org.lionsoul:ip2region:3.3.7`，xdb 数据文件需自备、显式传路径）**

```xml
<dependency>
    <groupId>org.lionsoul</groupId>
    <artifactId>ip2region</artifactId>
    <version>3.3.7</version>
</dependency>
```

```java
import cn.hg.aifei.tools.ipdb.IpKit;
import cn.hg.aifei.tools.ipdb.IpRegion;

// v4 / v6 数据文件由你自备、显式传路径；本模块不打包、不联网下载
IpRegion region = IpKit.search("/data/ip2region_v4.xdb", "/data/ip2region_v6.xdb", "1.2.3.4");
String country = region.getCountry();   // 中国
```

> `ip` 与 `qr` 的内核是**可选依赖**，不会随本模块传递。要用这两个工具，须在你的工程里显式加上对应依赖（见 §5、§7 依赖表）。未引入时首次调用抛可读异常，提示补齐坐标，而不是裸 `NoClassDefFoundError`。`captcha` 单机用无需额外依赖，`mask` 全程零依赖。

## 3. 常用用法

四个工具各是一个静态门面 / 无状态工具，随取随用，没有装配期、没有配置键。

### 验证码：`CaptchaKit`

```java
import cn.hg.aifei.tools.captcha.*;

Captcha chart = CaptchaKit.generate();                 // 默认字符型
Captcha math = CaptchaKit.generate(CaptchaType.ARITHMETIC);  // 算式型
byte[] png = chart.getImageBytes();
long expireAt = chart.getExpireAt();                   // 过期时刻（毫秒时间戳）

boolean ok = CaptchaKit.verify(chart.getToken(), userInput);  // 一次性消费
```

### IP 归属地：`IpSearcher` / `IpKit`

多次查询用一个会话，用完关闭（可放进 try-with-resources，它实现了 `AutoCloseable`）：

```java
import cn.hg.aifei.tools.ipdb.*;

try (IpSearcher searcher = IpSearcher.open("/data/ip2region_v4.xdb", "/data/ip2region_v6.xdb")) {
    IpRegion a = searcher.search("1.2.3.4");       // 境内，中文五段
    IpRegion b = searcher.search("8.8.8.8");       // 境外，英文五段
    // 五段：getCountry / getProvince / getCity / getIsp / getIsoCode
}
```

只查一次可用便捷门面 `IpKit.search(v4Xdb, v6Xdb, ip)`（内部即“开会话 → 查 → 关”）。

### 二维码：`QrKit`

```java
import cn.hg.aifei.tools.qr.*;

byte[] png = QrKit.encode("订单 12345 已发货");             // 默认 300×300、边距 4、纠错 M
String text = QrKit.decode(png);                            // byte[] 解码
String fromStream = QrKit.decode(inputStream);              // 流解码，不关闭你的流
```

### 脱敏：`MaskUtil` 与 `MaskKit`

```java
import cn.hg.aifei.tools.mask.*;

String a = MaskUtil.mask("13800138000", 3, 4);   // 通用：保留左 3 右 4，中间 ****
String b = MaskUtil.mask("13800138000", 3, 4, '#');  // 自定义掩码字符
String c = MaskUtil.maskPhone("13800138000");
String d = MaskUtil.maskIdCard("110101199001011234");
String e = MaskUtil.maskEmail("zhangsan@example.com");
String f = MaskUtil.maskPassword("abc123");             // 恒为 ******
```

九个默认规则：`phone` / `idCard` / `bankCard` / `email` / `name` / `fixedPhone` / `password` / `address` / `ip`，均可经 `MaskKit.use("规则名")` 取用；规则可自定义注册与替换（见 §4 场景四）。

## 4. 深入使用

### 场景一：多实例部署的登录验证码共享

业务问题：登录页由多个实例提供服务，验证码在一台实例生成、请求落到另一台实例校验，默认进程内存储会校验失败。

给工程加上 `aifei-cache:1.1.0`，启动时切一次共享存储，之后业务代码不变：

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.1.0</version>
</dependency>
```

```java
public class LoginConfig {
    public void init() {
        CaptchaKit.enableClusterStore();   // 全局切到共享存储；无 aifei-cache 时抛 CaptchaException
    }
}

public class LoginService {
    public boolean checkCode(String token, String input) {
        return CaptchaKit.verify(token, input);   // 与单机写法逐字一致
    }
}
```

切换前后 `generate` / `verify` 的业务代码完全相同，只是答案的存放位置从本进程换成了共享缓存。

### 场景二：日志审计标注请求来源地域

业务问题：访问日志要标注来源国家 / 省份，数据量大、查询频繁，希望查找够快又不把整份数据文件塞进内存。

同一份数据文件，用查询模式在“省内存”与“更快”之间切换，业务代码不变：

```java
public class AccessLogService {
    private final IpSearcher searcher = IpSearcher.open(
            "/data/ip2region_v4.xdb", "/data/ip2region_v6.xdb", IpQueryMode.VECTOR_INDEX);

    public String regionOf(String clientIp) {
        IpRegion r = searcher.search(clientIp);
        return r.getCountry() + r.getProvince();
    }
    // 应用退出时调用 searcher.close()
}
```

三种模式对**同一 IP、同一数据文件返回完全相同的五段结果**，差异只在性能与内存：

| 模式 | 内核策略 | 特点 |
| --- | --- | --- |
| `FILE_ONLY` | NoCache | 每次读磁盘，内存最低 |
| `VECTOR_INDEX`（默认） | VIndexCache | 固定约 512 KiB 索引缓存，约 100µs，折中最优 |
| `CONTENT` | FullCache | 整份文件驻留内存，约 10µs，内存占用最高 |

IPv4 与 IPv6 分别传各自的数据文件（`ip2region_v4.xdb` / `ip2region_v6.xdb`），查询时按 IP 版本自动路由，业务无需判断版本。查不到的地址返回五段全为空串的结果对象（非 null）。

### 场景三：给不同内容生成合适容量的二维码

业务问题：短链接用小图即可，含大量信息的票据码则需要更高纠错与更大尺寸。

四档纠错 `L` / `M` / `Q` / `H`（容错由低到高）与 `size` / `margin` 通过 `QrOptions` 指定：

```java
public class QrCodeService {
    public byte[] shortLinkQr(String url) {
        return QrKit.encode(url);   // 默认 300 / 4 / M
    }

    public byte[] ticketQr(String payload) {
        QrOptions opt = QrOptions.of(500, 4, QrErrorCorrection.H);  // 大尺寸 + 高纠错
        return QrKit.encode(payload, opt);
    }
}
```

文本按 UTF-8 编码，中文不乱码；空串也是合法内容，可正常出图并还原为空串。

### 场景四：客户资料导出的统一脱敏口径

业务问题：不同团队导出的客户资料，脱敏格式不一致；公司新规要求订单号按统一规则打码，且手机号规则要临时调整为只留后 4 位。

新增规则用 `register`，覆盖已有规则用 `replace`；九个默认方法会跟随替换后的规则：

```java
public class ExportMaskConfig {
    public void init() {
        MaskKit.register("orderNo", v -> v.length() <= 4 ? v : "****" + v.substring(v.length() - 4));
        MaskKit.replace("phone", v -> v == null || v.length() < 4 ? v : "****" + v.substring(v.length() - 4));
    }
}

public class CustomerExportService {
    public String maskOrderNo(String orderNo) {
        return MaskKit.use("orderNo").apply(orderNo);
    }
    public String maskPhone(String phone) {
        return MaskUtil.maskPhone(phone);   // 自动跟随上面 replace 后的 phone 规则
    }
}
```

`register` 同名已存在会报错、`replace` 不存在的规则会报错；九个默认规则初始即在册、不可删除（但可 `replace`）。

## 5. 高级使用

### 可选依赖策略

本模块坚持“零强制第三方运行依赖”：`aifei-all` 为 `provided`，三个能力内核为 `optional`，都不向下游传递。

| 能力 | 内核依赖 | 缺失时 |
| --- | --- | --- |
| IP 归属地 | `org.lionsoul:ip2region:3.3.7` | 首次调用 `IpSearcher` / `IpKit` 抛可读 `IpException` |
| 二维码 | `com.google.zxing:core:3.5.3` | 首次调用 `QrKit` 抛可读 `QrException` |
| 验证码集群存储 | `io.github.gollyhu:aifei-cache:1.1.0` | 调用 `enableClusterStore()` 抛可读 `CaptchaException` |

- 验证码单机模式、脱敏不依赖任何内核，引了本模块即可用。
- `zxing` 只引 `core`，**不引 `javase`**（后者会额外拖入命令行与图像 IO 库）；二维码 PNG 由模块内部绘制。
- 缺失内核时抛的是本模块异常、消息里写明需要补齐的坐标，且保留底层 cause——这是“能力按需引入”，不是“功能降级”。
- 版本口径：`ip2region` / `zxing` 版本由本模块 pom 的属性（`ip2region.version` / `zxing.version`）管理；`aifei-cache` 版本在 pom 中直接写死为 `1.1.0`（无对应属性）。消费方需与上表坐标一致。

### IP 数据文件

`ip2region` 的 xdb 是独立二进制数据文件，不打包进 JAR、不设配置键、不联网下载，由消费方自备并在调用时显式传路径。IPv4 / IPv6 分别对应 v4 / v6 两份文件；本模块只按 ip2region 3.3.7 的 xdb 格式读取，数据文件的生成与更新请用 ip2region 官方 maker。

## 6. 注意事项与技术难点

- **可选内核缺失时首次调用抛可读异常**：对齐 `aifei-excel` 的 EasyExcel 先例——不引内核时首次调用抛带坐标提示的模块异常，不是裸 `NoClassDefFoundError`。引了哪个内核才解锁哪个能力。
- **验证码 token 一次性消费**：`verify` 成功即删除答案，同一 token 二次提交必定失败；答错只累计失败次数、不消费 token，失败次数超过上限（默认 5）后即使答对也失效；过期（默认 120 秒）同样失效。这三类业务失败都返回 `false`，不抛异常。
- **验证码容量与 TTL 默认**：TTL 默认 120 秒、进程内存储容量默认 10000、失败上限默认 5，均可经 `CaptchaKit.setTtlSeconds` / `setCapacity` / `setMaxFailures` 调整（须为正数）。容量满时按最近最少使用淘汰。
- **IP归属地五段语义**：字段永不为 null；内核的保留值 `0` / `Reserved` 归一为空串 `""`，查不到的地址返回五段全空串的对象（非 null）。调用方无需识别内核哨兵。
- **IP 会话生命周期**：`close()` 幂等，可重复调用；关闭后再 `search` 抛可读 `IpException`。一个 `IpSearcher` 会话可被多线程并发查询、结果互不串扰，但请用一个会话对应一个内核实例，用完关闭。
- **xdb 由消费方自备**：显式传路径、不打包、不联网下载；路径不可读会以可读异常报出具体路径。
- **二维码解码的私有区哨兵边缘**：为让空串能正常往返，内部用一个 Unicode 私有区字符（U+E000）作占位；若待编码文本**恰好是单个 U+E000 字符**，解码会被还原成空串。该字符属私有区、不作为常规文本使用，此边缘行为已知且接受，详见 `docs/design.md` §8-C2。
- **二维码解码失败不静默**：无法识别的图片抛 `QrException` 并保留 cause，不返回 null；`decode(InputStream)` 不关闭调用方传入的流，流的开关由调用方负责。
- **脱敏是宽松文本处理**：`maskIp` 对非法文本不抛异常——按分隔符特征打码，`::` 压缩形式会先展开为完整 8 段再打码（如 `2001:db8:85a3::8a2e:370:7334` → `2001:*:*:*:*:*:*:*`），等价写法结果一致，畸形输入健壮退化。`value` 为 null 返回 null、空串返回空串；`MaskException` 只用于规则注册类错误与负数保留位。
- **双许可署名**：`ip2region` 采用 **Apache-2.0 OR MIT** 双许可。消费方引入该内核时，须按其许可要求随产品保留相应署名与许可声明；许可文本随依赖自带，本模块不复制。完整署名与许可信息见 `THIRD_PARTY_NOTICES.md`。

## 7. 参考

### 公共类型一览（四子包共 16 个，根包零公共类型）

| 子包 | 公共类型 |
| --- | --- |
| `cn.hg.aifei.tools.captcha` | `Captcha` / `CaptchaKit` / `CaptchaType` / `CaptchaException` |
| `cn.hg.aifei.tools.ipdb` | `IpSearcher` / `IpKit` / `IpRegion` / `IpException` / `IpQueryMode` |
| `cn.hg.aifei.tools.qr` | `QrKit` / `QrOptions` / `QrErrorCorrection` / `QrException` |
| `cn.hg.aifei.tools.mask` | `MaskUtil` / `MaskKit` / `MaskException` |

### 依赖坐标

| 依赖 | scope | 传递 | 用途 |
| --- | --- | --- | --- |
| `cn.aifei:aifei-all:1.1.2` | provided | 否 | 生态底座，本模块源码零引用，消费方自带 |
| `org.lionsoul:ip2region:3.3.7` | optional | 否 | IP 归属地内核 |
| `com.google.zxing:core:3.5.3` | optional | 否 | 二维码内核，零传递依赖、不引 javase |
| `io.github.gollyhu:aifei-cache:1.1.0` | optional | 否 | 验证码集群共享存储 |
| `junit:junit:4.13.2` | test | 否 | 单测与架构门禁 |

零日志绑定、零 DB、零 Web 面、无 Plugin、无配置键。

### 异常对照

四个工具各有统一异常，均为 `RuntimeException`，公共方法不声明 `throws`；业务只 `catch`、不 `new`。

| 异常 | 典型场景 | 备注 |
| --- | --- | --- |
| `IpException` | 参数非法 / IP 文本非法 / xdb 不可读 / 内核缺失 / 会话已关闭 / 查询失败 | 消息前缀 `ip: `，保留 cause |
| `CaptchaException` | 配置项非正 / 集群存储缺 `aifei-cache` / 存储不可用 | 消息前缀 `captcha: `；token 业务失败返回 `false` 不抛异常 |
| `QrException` | 内核缺失 / null 或空输入 / 编解码失败 | 消息前缀 `qr: `，失败保留 cause、不返回 null |
| `MaskException` | 规则重复注册 / 替换或取用未注册规则 / 名称或函数 null / 保留位为负 | 消息前缀 `mask: `；文本内容非法不抛异常 |

精确消息措辞见 `docs/design.md` §7.2。

### 文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/design.md` | 契约事实来源：公共面逐字签名、异常语义、已定档决策、架构门禁 |
| `docs/testing.md` | 测试策略与用例清单（123 例构成、样本策略、覆盖映射） |
| `THIRD_PARTY_NOTICES.md` | 第三方运行内核的版权与许可署名声明 |

### 构建与验证

```bash
cd aifei-tools
mvn clean verify                 # 0 failure / 0 error（123 例：门禁 17 + 功能 106）
mvn test -Dtest=ArchitectureBoundaryTest   # 仅跑架构门禁（17 例）
mvn dependency:tree              # 核对：仅 aifei-all(provided) + 三个 optional 内核 + junit(test)
javap -verbose target/classes/cn/hg/aifei/tools/ipdb/IpSearcher.class | findstr "major version"   # 期望 52
```
