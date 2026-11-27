# PRD：aifei-tools（极简通用工具集）

> 关联：`aifei-cache`（验证码答案共享存储，`optional`）；`org.lionsoul:ip2region`（IP 归属地数据内核，`optional`）；`com.google.zxing:core`（二维码编解码内核，`optional`）。

## 一、定位

本模块是**极简工具集**：一个 JAR 收纳多个互不相关的工具，按子包隔离、各自独立可用，不做跨工具抽象。首批工具为验证码（captcha）、IP 归属地查询（ipdb）、二维码（qr）、脱敏（mask）。

## 二、范围

- 交付 `io.github.gollyhu:aifei-tools:1.0.0-SNAPSHOT`，包名 `cn.hg.aifei.tools`，Java 8（class major 52）
- 首批工具（4 个，各自独立可用）：验证码落 `cn.hg.aifei.tools.captcha` 子包；IP 归属地查询落 `cn.hg.aifei.tools.ipdb` 子包；二维码落 `cn.hg.aifei.tools.qr` 子包；脱敏落 `cn.hg.aifei.tools.mask` 子包
- 后续工具按需追加（候选：其它极简工具），本版不预置空壳、不留占位类
- 零**强制**第三方运行依赖；`aifei-all` 为 `provided`；第三方能力内核为 `optional`（captcha 的 `aifei-cache`、ipdb 的 `org.lionsoul:ip2region:3.3.7`、qr 的 `com.google.zxing:core:3.5.3`），缺失时首次调用抛可读异常，对齐 `aifei-excel` 的 EasyExcel 先例

**不做**（边界）：

1. 滑块 / 点选行为验证：需前端配合，超出后端极简定位。
2. 短信 / 邮件验证码发送：属 `aifei-sms` 通道范畴。
3. 风控画像、设备指纹、频率策略编排。
4. 工具之间的公共抽象层（如统一 `Tool` 接口）——各工具互不相关，抽象只增加认知成本。

## 四、captcha 工具要求

- **生成**：字符型与算式型两类；纯 JDK `Graphics2D` 绘制 PNG（不引第三方图形库）；接口返回 token、图片字节、过期时刻
- **校验**：`verify(token, input)` 一次性消费——校验成功即删除答案，重复提交同一 token 必定失败
- **答案存储**：默认进程内 LRU（容量与 TTL 可配，单机可用）；引入 `aifei-cache` 时改走共享存储以支持多实例与多语言环境下的集群校验
- **约束**：TTL 默认 2 分钟；错误次数上限可配，超限即失效（防枚举）；token 必须不可猜（安全随机）
- **不做**：音频验证码、无状态签名式验证码（答案随 token 下发会被直接解码，仅可作前端免交互场景，不在本版）

## 五、ip 工具要求

- **定位与场景**：离线 IP 归属地查询（城市级），输入 IPv4 / IPv6 文本，返回「国家|省份|城市|ISP|ISO 国家码」五段结果（境内中文、境外英文）。典型场景：日志/审计标注来源地域、请求地域统计、内容地域化。无 Plugin、无配置键、随取随用（对齐 `aifei-excel` / `aifei-trace` 的无装配门面型）。
- **依赖策略**：唯一第三方内核 `org.lionsoul:ip2region:3.3.7`（与 `aifei-tools/pom.xml` 的 `<ip2region.version>3.3.7</ip2region.version>` 及根 `README.md` 依赖说明一致）为 `optional`、阻断传递；`aifei-all` 为 `provided`；库模块零 `cn.aifei.*` 引用、零日志绑定。依赖缺失时首次调用抛可读 `IpException`（收敛 `NoClassDefFoundError` / `ClassNotFoundException`），不得裸抛。双许可 Apache-2.0 OR MIT，署名与许可说明见第三方声明文件。
- **公共面**：落 `cn.hg.aifei.tools.ipdb` 子包，精确 5 个类型——`IpKit`（便捷门面，`search(v4Xdb, v6Xdb, ip[, mode])`）、`IpRegion`（结果值对象）、`IpQueryMode`（查询模式枚举）、`IpException`（统一异常）、`IpSearcher`（`AutoCloseable` 会话）。公共签名零第三方、零框架类型，不得暴露 ip2region 的 `Searcher`。region 五段解析（含「0」/未知值语义）唯一定义点放 `internal`，公共层与实现层不得复制。
- **数据文件策略**：xdb 为独立二进制数据文件、不打包进 jar，由消费方自带并经入参显式传入路径（不设配置键、不内置默认文件、不联网下载）。IPv4 / IPv6 分别对应 `ip2region_v4.xdb` / `ip2region_v6.xdb`。
- **查询模式**：`IpQueryMode` 枚举 `FILE_ONLY`（内存最低、每次磁盘 IO）、`VECTOR_INDEX`（固定 512 KiB 索引缓存，约 100µs，默认）、`CONTENT`（全文件内存，约 10µs）三选一，由入参指定；三种模式对同一 IP 与同一 xdb 的归属地结果须一致，差异只在性能/内存占用。
- **并发与生命周期契约**：`Searcher` 非线程安全，并发策略按模式定档（每次查询新建 / ThreadLocal / 实例独占等），不得在无保护下单实例共享；三种模式均持有需关闭的资源，`close()` 须幂等，关闭后查询抛可读异常（统一为 `IpException`）。
- **异常**：统一收敛为 `IpException`——参数非法（null / 非法 IP 文本）、xdb 文件缺失或不可读、依赖缺失、解析失败均给可读消息与 cause；不向调用方抛底层原始异常。
- **不做**（边界）：
  1. xdb 文件的生成、更新与联网下载（数据文件由消费方自备）。
  2. 联网 API 查询、在线 IP 库回退（本工具纯离线）。
  3. 经纬度 / 运营商精细画像、风控、代理识别等归属地之外的语义。
  4. 配置键与自动加载；db 路径必须显式入参。
  5. 跨工具公共抽象层（同「二、范围」不做第 4 条）。

## 六、qr 工具要求

- **定位与场景**：二维码生成与解码，落 `cn.hg.aifei.tools.qr` 子包，子包隔离、各自独立可用（同「二、范围」定位）。
- **依赖策略**：唯一第三方内核 `com.google.zxing:core:3.5.3` 为 `optional`、零传递依赖已实证，不引 `javase`；库模块公共签名零第三方、零框架类型。ZXing 缺失探测放 `internal`，缺失时首次调用抛可读 `QrException`（不得裸抛），对齐 `aifei-excel` 的 EasyExcel 先例。
- **公共面（精确 4 个，冻结）**：落 `cn.hg.aifei.tools.qr` 子包，类型与签名如下：

```java
public final class QrKit {
    private QrKit();
    public static byte[] encode(String text);                     // 等价于 encode(text, QrOptions.defaults())
    public static byte[] encode(String text, QrOptions options);
    public static String decode(byte[] pngBytes);
    public static String decode(InputStream in);                  // 不关闭调用方传入的流
}
public final class QrOptions {
    public static QrOptions defaults();                           // size=300, margin=4, EC=M, UTF-8 内部固定
    public static QrOptions of(int size, int margin, QrErrorCorrection ec);
    public int getSize(); public int getMargin(); public QrErrorCorrection getErrorCorrection();
}
public enum QrErrorCorrection { L, M, Q, H }
public final class QrException extends RuntimeException {
    public QrException(String message); public QrException(String message, Throwable cause);
}
```

## 七、mask 工具要求

- **定位与场景**：通用脱敏 + 9 个默认规则，落 `cn.hg.aifei.tools.mask` 子包，子包隔离、各自独立可用；纯 JDK 零依赖，无第三方内核、无配置键、随取随用。
- **公共面（精确 3 个，冻结）**：落 `cn.hg.aifei.tools.mask` 子包，类型与签名如下：

```java
public final class MaskUtil {
    private MaskUtil();
    public static String mask(String value, int keepLeft, int keepRight);
    public static String mask(String value, int keepLeft, int keepRight, char maskChar);
    // 默认规则：转发注册表，replace 后跟随
    public static String maskPhone(String value);
    public static String maskIdCard(String value);
    public static String maskBankCard(String value);
    public static String maskEmail(String value);
    public static String maskName(String value);
    public static String maskFixedPhone(String value);
    public static String maskPassword(String value);
    public static String maskAddress(String value);
    public static String maskIp(String value);
}
public final class MaskKit {
    private MaskKit();
    public static void register(String name, UnaryOperator<String> masker);      // 已存在→MaskException
    public static void replace(String name, UnaryOperator<String> masker);       // 不存在→MaskException
    public static UnaryOperator<String> use(String name);                        // 未注册→MaskException
}
public final class MaskException extends RuntimeException {
    public MaskException(String message); public MaskException(String message, Throwable cause);
}
```

- **默认规则与注册表**：默认规则名 `phone`/`idCard`/`bankCard`/`email`/`name`/`fixedPhone`/`password`/`address`/`ip` 初始即在注册表；`maskXxx(x)` ≡ `use("规则名").apply(x)`（如 `maskPhone(x)` ≡ `use("phone").apply(x)`）；默认规则不可删；注册表 `ConcurrentHashMap`；`MaskUtil` 默认方法转发注册表，`replace` 后跟随新规则。
- **自定义规则**：类型为 `UnaryOperator<String>`，调用 `use("xxx").apply(value)`；`register`/`replace`/`use` 均用全拼写。
- **默认规则格式（中国大陆格式假设）**：手机号 11 位前 3 后 4、身份证 18 位前 6 后 4、银行卡前 6 后 4、邮箱 @ 前保留首尾各 1 位、姓名保留姓；座机保留区号（含 `-`）与后 4 位、中间等长 `*`，无 `-` 时退化为保留前 4 后 4（`0571-88886666` → `0571-****6666`）；密码恒定返回 `******`，不保留原长度、不泄露位数（`abc123` → `******`）；地址保留前 6 字符、其余等长 `*`，长度 ≤6 时全打码（`浙江省杭州市金城坊街2号` → `浙江省杭州市******`）；IP 保留首段、其余每段一个 `*`，IPv4 用 `.` 分段、IPv6 用 `:` 分段（`192.168.2.1` → `192.*.*.*`；`2001:db8:85a3::8a2e:370:7334` → `2001:*:*:*:*:*:*:*`）——实现期可演进的约定，不臆造其它格式。
- **异常**：统一收敛为 `MaskException`——重复 `register`、`replace` 不存在的规则、`use` 未注册规则均抛 `MaskException`。

## 八、验收要点

- 生成 → 校验成功 → 同 token 二次校验失败（一次性消费）
- TTL 到期失效；超过错误次数上限失效；token 不可从图片字节反推
- 未引入 `aifei-cache` 时单机可用；引入后集群内跨实例校验成功
- 门禁按定档口径断言：根包零类型、`captcha` 子包公共类型精确 N 个
- 文档四件套齐备 + 根 `README.md` 模块总览补条目
- 同一 IP 与同一 xdb，`FILE_ONLY` / `VECTOR_INDEX` / `CONTENT` 三种模式返回相同的 `IpRegion` 五段结果
- IPv4 用 `ip2region_v4.xdb`、IPv6 用 `ip2region_v6.xdb` 均能命中并返回预期五段；境内样本返回中文、境外样本返回英文，未知段按定档的「0」语义表达
- 非法 IP 文本 / null 入参、xdb 文件不存在或不可读、未引入 ip2region 依赖 → 首次调用均抛 `IpException` 且消息可读，无裸 `NoClassDefFoundError` / `NullPointerException`
- 多线程并发查询（按定档并发策略）无结果串扰、无 `Searcher` 共享异常
- `close()` 连续调用幂等；关闭后查询抛可读异常（符合定档语义）
- 门禁按定档口径断言：根包公共类型为零、`ipdb` 子包公共类型精确 5 个（`IpKit` / `IpRegion` / `IpQueryMode` / `IpException` / `IpSearcher`，N=5 已随实现定档）
- 测试不提交 xdb 二进制
- qr：`encode` 产出可被 `decode` 还原的 PNG。
- qr：四档纠错等级与 `size`/`margin` 选项生效。
- qr：中文文本 UTF-8 不乱码。
- qr：`decode` 不关闭调用方流。
- qr：未引入 ZXing 依赖时首次调用抛可读 `QrException`。
- qr：门禁断言根包公共类型为零、`qr` 子包公共类型精确 4 个。
- mask：通用 `mask` 按保留位数与掩码字符输出。
- mask：9 个默认规则按标注格式脱敏。
- mask：未注册规则名 `use` 抛 `MaskException`。
- mask：重复 `register` 抛 `MaskException`。
- mask：`replace` 不存在的规则抛 `MaskException`。
- mask：`replace` 后 `MaskUtil` 默认方法跟随新规则。
- mask：默认规则不可删除。
- mask：门禁断言 `mask` 子包公共类型精确 3 个。
- mask：`maskFixedPhone` 按座机规则脱敏，`0571-88886666` → `0571-****6666`；且 `maskFixedPhone(x)` ≡ `use("fixedPhone").apply(x)`，`fixedPhone` 初始在册，`replace("fixedPhone", ...)` 后跟随新规则。
- mask：`maskPassword` 恒定返回 `******`，`abc123` → `******`，不保留原长度、不泄露位数；且 `maskPassword(x)` ≡ `use("password").apply(x)`，`password` 初始在册，`replace("password", ...)` 后跟随新规则。
- mask：`maskAddress` 保留前 6 字符、其余等长 `*`，`浙江省杭州市金城坊街2号` → `浙江省杭州市******`，长度 ≤6 时全打码；且 `maskAddress(x)` ≡ `use("address").apply(x)`，`address` 初始在册，`replace("address", ...)` 后跟随新规则。
- mask：`maskIp` 保留首段、其余每段一个 `*`，IPv4/IPv6 分别按 `.`/`:` 分段，`192.168.2.1` → `192.*.*.*`、`2001:db8:85a3::8a2e:370:7334` → `2001:*:*:*:*:*:*:*`；且 `maskIp(x)` ≡ `use("ip").apply(x)`，`ip` 初始在册，`replace("ip", ...)` 后跟随新规则。

