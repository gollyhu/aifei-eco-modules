# aifei-sms 设计文档

> 本文是 `aifei-sms` 的设计事实来源：公共 API 签名、包边界与门禁断言、配置键全集、行为契约与验收映射以本文为准。面向使用者的用法见 `README.md`；面向开发代理的约束见模块 `AGENTS.md`；测试策略与用例清单见 `docs/testing.md`。
>
> `aifei-sms` 是 aifei 生态的库模块（SDK，可被应用依赖），只有「根包公共 API + `internal` 适配层 + `aifei` 接入层 + `spi` 回执 SPI」四层，零存储（禁 `java.sql.*`），无 `AppConfig` / `Launcher` / `@Path` / 业务库表 / `Db+Row` / `Generator`。
>
> **坐标**：`io.github.gollyhu:aifei-sms:1.0.0-SNAPSHOT`（`packaging=jar`）；根包 `cn.hg.aifei.sms`；接入层 `cn.hg.aifei.sms.aifei`；适配层 `cn.hg.aifei.sms.internal`；回执 SPI `cn.hg.aifei.sms.spi`；**Java 8**（语法与 API 双约束，主 JAR class major = 52）；sms4j 内核 `org.dromara.sms4j:sms4j-core:3.3.6` 为 `optional`，`aifei:1.1.2` 为 `provided`。
>
> **技术路线**：基于 **sms4j v3.3.6**（dromara 社区，Apache-2.0，JDK 8+）二次封装，以「引入核心 + `internal` 适配层隔离」落地，不自行实现短信发送协议；禁 `sms4j-spring-boot-starter` / `sms4j-solon-plugin`。发送内核走 sms4j 的 `SmsFactory` / `SmsBlend`；本模块 `internal` 只做配置解析、类型映射、依赖探测、异常翻译、回执验签与结果适配。

---

## 1. 模块定位与设计原则

### 1.1 定位

aifei 生态有 `aifei-weixin` 覆盖微信通道，但**短信通道缺位**：业务普遍各自裸用阿里云 / 腾讯云 / 华为云 SDK，模板 ID、签名、变量拼装、回执接收各写各的，换厂商即重写发送代码。本模块基于 sms4j 补齐**统一短信通道**：以一套接口聚合多厂商（首期落地阿里云），换厂商只改 `sms.*` 配置不改业务代码。

| # | 目标 | 落点 |
| --- | --- | --- |
| 1 | **统一发送门面** | `SmsClient`（接口）/ `SmsKit`（静态门面）模板化发送；底层经 sms4j `SmsBlend`（§5） |
| 2 | **多厂商聚合** | 基于 sms4j 聚合 23 家厂商；首期落地 `alibaba`，其余按通道追加 |
| 3 | **类型隔离** | `org.dromara.sms4j.*` 只在 `internal.sms4j`；公共面零第三方 / 零框架类型（§4.2） |
| 4 | **回执接收** | `aifei.SmsHandler` + `spi.SmsReceiptListener` + 阿里云 HMAC-SHA1 验签（§8） |

### 1.2 基于 sms4j 二次封装（非自实现，铁律）

本模块**不自行实现任何短信发送协议、厂商 HTTP 签名与模板拼装逻辑**，而是基于 sms4j 3.3.6 二次封装。本模块只做六件事：

1. **装配**：实现 aifei `Plugin`，在 `start()` 读 `sms.*` 配置，经 `SmsFactory.createSmsBlend(supplierConfig)`（返回 `void`、内部注册）后按 `configId` 用 `SmsFactory.getSmsBlend(configId)` 取回 sms4j `SmsBlend`（§5.4 / §7.2）。
2. **薄门面**：对外暴露 aifei 风格极简 API（`SmsClient` / `SmsKit`）。
3. **类型收敛**：sms4j 类型**不得**出现在公共 API 面。
4. **框架接线**：经 `@Inject` 注入客户端、经 `app-config.txt` 的 `Prop` 承载配置。
5. **回执验签补充**：sms4j 是**出站发送**聚合框架，**不提供入站回执验签**，故阿里云回调验签由 `internal` 用纯 JDK `javax.crypto.Mac` 实现——这是 sms4j 未覆盖能力的补充，**不是**对发送内核的「自研顶替」。
6. **统一异常与依赖探测**：失败收敛 `SmsException`；sms4j 缺失时可读失败（§7.4 / §7.5）。

### 1.3 设计原则（硬约束）

| # | 纪律 |
| --- | --- |
| 1 | **根包 `cn.hg.aifei.sms` 精确 5 个公共类型**：`SmsClient` / `SmsKit` / `SmsMessage` / `SmsResult` / `SmsException`。不得增删、不得改名；变更须先改本文 §5 与公共面名单（§5.6） |
| 2 | **公共签名零第三方类型、零框架类型**：只用 JDK 类型 + 本模块类型；不出现 `org.dromara.sms4j.*` / `cn.aifei.*`（`spi` 包同约束） |
| 3 | **sms4j 唯一内核**：`org.dromara.sms4j:*` 为 `optional`；仅 `sms4j-core`（传递 provider/api/comm）引入；禁 starter / solon plugin；由 `internal.sms4j` 单点引用 |
| 4 | **唯一定义点**：配置键名解析、通道类型映射、异常消息措辞、依赖探测、验签算法各只有一处实现（§7.1 标 ★） |
| 5 | **零存储 / 零日志绑定**：`src/main/java` 禁 `import java.sql.` / `io.undertow.*`；不声明任何日志绑定实现 |
| 6 | **同步发送**：公共面只提供同步发送，不暴露异步 API、不自带线程池 |
| 7 | **Java 8 双约束**：语法与 API 均限 Java 8，主 JAR class major = 52 |

> 第 1、2、3、4、5、7 条由架构门禁 `ArchitectureBoundaryTest`（§9）强制，**不得放宽或绕过**。

### 1.4 本期不做清单（能力边界）

| 不做项 | 边界说明 |
| --- | --- |
| 邮件 / APP 推送 | 属其它通道范畴，规划明确不建或未排期 |
| 短信模板 CRUD / 审核 | 属消费方业务系统；本模块只「用已有模板发送」 |
| 内容审核 / 敏感词过滤 | 由消费方在调用前自行处理 |
| 频率限制 / 防刷 | 属风控范畴，消费方自行实现（可配 `aifei-sentinel` / `aifei-cache`） |
| 多厂商负载均衡 / 故障切换 | 只「按配置指定厂商发送」，不做 failover / 权重路由 |
| 异步发送 API / 内置线程池 | 公共面只同步；异步由消费方包装 |
| 发送记录持久化 / 回执去重 | 零存储；落库与幂等由消费方承担 |
| 批量群发 / 国际短信特殊处理 | P1，本期不做 |

---

## 4. 架构分层与包边界

### 4.1 包结构

```text
cn.hg.aifei.sms                    根包：5 公共类型（发送门面 / 消息 / 结果 / 异常），零第三方、零框架类型
cn.hg.aifei.sms.spi                回执 SPI（2 公共类型）：SmsReceiptListener（消费方实现）+ SmsReceipt（回执值对象），零第三方、零框架类型
cn.hg.aifei.sms.aifei              接入层：SmsPlugin / SmsHandler —— 唯一允许 import cn.aifei.* 的包
cn.hg.aifei.sms.internal           适配层：配置解析 / 通道工厂 / 依赖探测 / 异常构造 / 回执验签
   └─ internal.sms4j               唯一允许 import org.dromara.sms4j.* 的子包
```

**依赖方向（单向，门禁 §9 强制）**：

```text
根包 / spi      ──→ 纯 JDK + 本模块类型（spi 可被引擎层回调，但不反向依赖 internal 具体类型）
aifei 接入包     ──→ 根包 + spi + internal + cn.aifei.*（框架 Plugin / Prop / AopKit / Handler）
internal        ──→ 根包 + spi + JDK；sms4j 适配子包引用 org.dromara.sms4j.*
internal.sms4j  ──→ sms4j API + JDK；不得引用 cn.aifei.*（框架能力经 aifei 包注入，见 §5.4）
禁止：根包 / spi 出现 org.dromara.sms4j.* / cn.aifei.*；
      任何库模块 .java import io.undertow.* / java.sql.*；
      internal（除 sms4j 子包）import org.dromara.sms4j.*；
      任何 .java import org.slf4j.*（库模块零日志绑定）
```

### 4.2 架构门禁（`ArchitectureBoundaryTest`）

| 类型族 | 允许出现的包 | 门禁断言方法 |
| --- | --- | --- |
| `org.dromara.sms4j.*` | **仅** `cn/hg/aifei/sms/internal/sms4j/` | `sms4jOnlyInAdapter` |
| `cn.aifei.*` | **仅** `cn/hg/aifei/sms/aifei/` | `frameworkOnlyInBridgePackage` |
| `java.sql.*` / `io.undertow.*` | **全模块禁止** | `noSqlOrUndertowInLibrary` |
| `org.slf4j.*`（含 test） | **全模块禁止** | `noSlf4jBinding` |
| 根包公共类型数量 | **精确 5 个**且文件名集合精确 | `publicSurfaceExact` |
| 公共签名零第三方 / 零框架类型 | 根包 + `spi` 包 | `publicSignaturesNoThirdParty` |
| `Class.forName` 唯一 | 仅 `internal/sms4j/SmsDeps.java` | `classForNameOnlyInDeps` |
| 异常消息前缀唯一 | 仅 `internal/SmsErrors.java` | `errorMessagesOnlyInErrors` |
| Java 8 语法 / API | 全模块 | `java8Compatibility` |

完整断言清单（14 例）见 §9。

---

## 5. 公共接口契约（根包精确 5 类型）

根包 `cn.hg.aifei.sms` **精确 5 个公共类型**，为本节与门禁 `EXPECTED_ROOT_TYPES_SET`（§9）的**唯一事实来源**。逐字签名如下（`static` 与否、返回类型、参数顺序均不得改动）。所有公共方法均**不声明 `throws` 子句**（`SmsException` 为 `RuntimeException`），但 javadoc **必须**写全 `@param` / `@return` / `@throws SmsException`。

### 5.1 `SmsClient`（发送门面接口）

```java
public interface SmsClient {
    SmsResult send(String phoneNumber, String templateId, LinkedHashMap<String, String> variables);
    SmsResult send(String phoneNumber, String message);
    String channelName();
}
```

契约要点：

| 方法 | 契约 | 失败 |
| --- | --- | --- |
| `send(phone, templateId, variables)` | **模板化发送**（R-01 主路径）：同步阻塞至厂商返回；`variables` 为模板变量 key-value（可为 `null` 或空 `Map`，视为无变量）；成功返回 `SmsResult` | 参数非法 / 依赖缺失 / 插件未启动 / 厂商错误 / 网络异常 → `SmsException` |
| `send(phone, message)` | **内容直发**（对齐 sms4j `sendMessage(phone, content)`；部分厂商按模板内容发送）：同步发送 | 同上 |
| `channelName()` | 返回该实例绑定的通道标识（`sms.<name>` 的 `<name>`），便于日志与排障 | 无 |

- 由 `SmsPlugin` 注册为单例，经 `@Inject SmsClient` 取**默认通道**；命名通道经 `SmsKit.use(name)`。
- **线程安全**：实现无可变共享状态，`SmsBlend` 线程安全由 sms4j 保证（Q-08 / SmsBlend 经代理缓存）；模块不额外加锁。

### 5.2 `SmsKit`（静态门面，`final` + 私有构造 + 全 `static`）

```java
public final class SmsKit {
    private SmsKit() {
    }

    public static SmsClient defaultClient();
    public static SmsClient use(String channelName);
    public static Set<String> channels();
    public static SmsResult send(String phoneNumber, String templateId, LinkedHashMap<String, String> variables);
}
```

契约要点：

| 方法 | 契约 | 失败 |
| --- | --- | --- |
| `defaultClient()` | 取默认通道客户端（由 `sms.default-channel` 指定，或单通道时取该通道） | 未装配 / 已停止 → `SmsException("sms: plugin not started or stopped ...")`（§7.5 场景 5） |
| `use(channelName)` | 按通道标识取命名通道客户端 | 通道名不存在 → `SmsException`（场景 6） |
| `channels()` | 返回已装配通道标识集合（不可变 `Set`） | 未装配 / 已停止 → `SmsException` |
| `send(phone, templateId, variables)` | 便捷方法，等价 `defaultClient().send(...)` | 同 `defaultClient()` 与 `SmsClient.send` |

- **属 Kit**（有状态门面）：须先 `SmsPlugin.start()` 装配；未装配 / 已停止调用即失败（对齐 `AuditKit` javadoc `@throws AuditException 插件未启动或已停止`）。
- 命名依据见 §1.3 / D9，不得改名 `SmsUtil`。

### 5.3 `SmsMessage`（发送消息值对象，不可变）

```java
public final class SmsMessage {
    public SmsMessage(String phoneNumber, String templateId, LinkedHashMap<String, String> variables);

    public String getPhoneNumber();
    public String getTemplateId();
    public LinkedHashMap<String, String> getVariables();   // 返回防御性副本
}
```

- 承载「手机号 + 模板 ID + 模板变量」，构造期校验必填项（手机号 / 模板 ID 不得为空白，见 §7.5 场景 1/2），不可变、线程安全。
- 保留此类是为了「消息与结果职责分离」与未来扩展（如批量收件人）；发送 API 主路径用 `SmsClient.send(phone, templateId, variables)` 三参形态，消费方也可先构造 `SmsMessage` 再取字段传入（本类不引入额外的 `send(SmsMessage)` 重载以控制公共面）。

> **公共面计数口径**：`SmsMessage` 属根包 5 类型之一但发送主路径不强制使用它——这是设计取舍：既保留消息值对象的语义完整性与门禁计数，又让主发送 API 保持三参直传的简洁（对齐 sms4j `sendMessage(phone, templateId, map)`）。

### 5.4 `SmsResult`（发送结果值对象，不可变）

```java
public final class SmsResult {
    public String getChannelName();
    public boolean isSuccess();
    public String getMessage();        // 厂商描述 / 失败原因，可空
    public String getRequestId();      // 厂商流水号 / 回执 ID，可空
}

// 位于 aifei 包，非根包计数；实际源码以 import 引入简名 Plugin / Prop（非全限定名书写）
public final class SmsPlugin implements Plugin {
    public SmsPlugin(Prop prop);                 // prop == null → SmsErrors.propNull()
    @Override public synchronized void start();
    @Override public synchronized void stop();
}
```

- `SmsResult` 字段语义：
  - `channelName`：通道标识；
  - `isSuccess`：发送是否成功（映射 `SmsResponse.isSuccess()`）；
  - `message`：厂商返回描述 / 失败原因（来自 `SmsResponse.getData()` 转字符串，可空）；
  - `requestId`：厂商流水号 / 回执 ID（从 `SmsResponse.getData()` 解析，可空）。
- **发送失败不返回 `SmsResult(success=false)`，而是抛 `SmsException`**（R-01 / AC-01）：`isSuccess=false` 仅用于「厂商明确返回失败应答但非异常」的场景边界；默认所有失败路径走异常（§7.5）。

> 注：上代码块中的 `SmsPlugin` 仅用于对比展示签名形态，**其实际归属包为 `cn.hg.aifei.sms.aifei`**（见 §5.5），不计入根包 5 类型。公共面名单见 §5.6。

### 5.5 接入构件与 SPI（非根包计数）

```java
// cn.hg.aifei.sms.aifei —— 接入层，唯一允许 import cn.aifei.*；均经 import 引入简名
public final class SmsPlugin implements Plugin { /* 签名见 §5.4 */ }

// Handler 是抽象类（非接口）：须 extends；框架 Output 为空接口、无写响应能力，
// 故读写交换经 SmsIo<I> 桥接（消费方 / demo 提供实现，见下）
public final class SmsHandler<I extends Input, O extends Output> extends Handler<I, O> {
    public static final String DEFAULT_PREFIX = "/sms/callback";
    public SmsHandler(SmsIo<I> io, List<SmsReceiptListener> listeners);
    public SmsHandler(String prefix, SmsIo<I> io, List<SmsReceiptListener> listeners);
    @Override public void handle(String path, I in, O out) throws Exception;   // 见 §8.2
    public Throwable lastListenerError();
}

// 回执传输桥接（接入层非承诺构件、非计数）：泛型无上界、零第三方 / 零框架类型
public interface SmsIo<I> {
    Map<String, String> params(I in);
    String body(I in);
    void reply(I in, int status, String json);
}

// cn.hg.aifei.sms.spi —— 回执 SPI（2 个公共类型），零第三方 / 零框架类型
public interface SmsReceiptListener {
    void onReceipt(SmsReceipt receipt);
}

// cn.hg.aifei.sms.spi.SmsReceipt —— 回执内容值对象（不可变），供监听器读取
public final class SmsReceipt {
    public String getChannelName();      // 通道标识
    public String getPhoneNumber();      // 手机号（脱敏由消费方处理）
    public String getStatus();           // 厂商状态码（如阿里云 SUCCESS / FAIL）
    public String getRequestId();        // 厂商流水号 / 回执 ID
    public long getReportTime();         // 回执时间（毫秒时间戳；厂商报文无时间时用接收时刻）
    public String getRawBody();          // 原始回调报文（便于消费方二次解析）
}
```

> **SPI 签名**：`SmsReceipt` 作为**回执内容值对象落在 `spi` 包**，与 `SmsReceiptListener` 共同构成回执 SPI 的 2 个公共类型——二者均为本模块类型、纯 JDK 字段，零第三方 / 零框架类型（AC-09 / AC-11 合规）。**理由**：公共 SPI 的签名不得引用 `internal`（非承诺实现层）类型，否则消费方被迫依赖可随时重构的内部包，与「internal 非承诺」自相矛盾；把值对象提升到 `spi` 使公共契约自洽、实现无关。**被否决方案**：`SmsReceipt` 落 `internal` 并由 SPI 引用——虽不触发第三方/框架门禁，但破坏包边界语义。

### 5.6 公共面精确名单

| 包 | 精确类型 | 数量 | 门禁常量 |
| --- | --- | --- | --- |
| 根包 `cn.hg.aifei.sms` | `SmsClient` / `SmsKit` / `SmsMessage` / `SmsResult` / `SmsException` | **5** | `EXPECTED_ROOT_TYPES_SET`（§9） |
| `cn.hg.aifei.sms.spi` | `SmsReceiptListener` / `SmsReceipt` | **2** | `EXPECTED_SPI_TYPES = 2`（§9） |
| `cn.hg.aifei.sms.aifei` | `SmsPlugin` / `SmsHandler` | 2（接入构件，非承诺 API，不计数） | — |

**根包公共类型精确 5 个**（文件名集合精确相等，多一个或少一个即失败）；**`spi` 包精确 2 个**（`SmsReceiptListener.java` / `SmsReceipt.java`，集合精确相等）。`SmsException` 的 `public` 构造器不增加顶层类型计数（同一文件）。`SmsPlugin`/`SmsHandler`（aifei）不计入根包 5 个。此表即 AC-10 门禁断言的冻结名单，回写任务输入 §五 5.5 与 AC-10。

### 5.7 命名依据汇总

| 类型 | 形态 | 命名依据 |
| --- | --- | --- |
| `SmsClient` | 接口，名词性后缀 | 对齐 `HttpClient` / `OssClient`（根 `AGENTS.md` §命名约定） |
| `SmsKit` | `public final` + 私有构造 + 全 `static` | 有状态门面（需先 `SmsPlugin.start()`），对齐 `OssKit` / `AuditKit` |
| `SmsMessage` / `SmsResult` | `public final` 不可变值对象 | 名词，消息 / 结果语义分离 |
| `SmsException` | `public final extends RuntimeException` | 模块唯一异常；`public` 构造器因唯一构造点在异包 `internal`（§7.5） |
| `SmsReceiptListener` | 接口，SPI | 回执监听器扩展点，消费方实现 |
| `SmsReceipt` | `public final` 不可变值对象 | 回执内容数据契约，名词（spi 包） |
| `SmsPlugin` / `SmsHandler` | 接入构件 | 对齐 `OssPlugin` / `WeixinHandler` |

---

## 6. 配置模型（`sms.*`）

### 6.1 配置键全集

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `sms.default-channel` | 多通道时必填 | 默认通道标识；`@Inject SmsClient` 对应此通道；单通道时可缺省（取唯一通道） |
| `sms.<name>.type` | 是 | 厂商类型（supplier 字面量，合法值见 §6.2；首期白名单 = `alibaba`） |
| `sms.<name>.access-key` | 视厂商（阿里云必填） | 阿里云 `AccessKeyId`（映射 `BaseConfig.setAccessKeyId`） |
| `sms.<name>.secret-key` | 视厂商（阿里云必填） | 阿里云 `AccessKeySecret`（映射 `setAccessKeySecret`） |
| `sms.<name>.signature` | 视厂商（阿里云必填） | 短信签名（映射 `setSignature`） |
| `sms.<name>.template-id` | 否 | 默认模板 ID（映射 `setTemplateId`）；调用时也可在 `send` 入参指定 |
| `sms.<name>.region-id` | 否（阿里云默认 `cn-hangzhou`） | 阿里云地域（映射 `AlibabaConfig.setRegionId`） |
| `sms.<name>.endpoint` | 否 | 厂商请求地址（映射 `AlibabaConfig.setRequestUrl`） |
| `sms.<name>.access-key-id` | 否（阿里云验签用） | **阿里云回执验签 AccessKey ID**——可与 `access-key` 同值；验签专用配置键，供 `AliyunReceiptVerifier` 使用（§8.3） |
| `sms.<name>.access-key-secret` | 否（阿里云验签用） | **阿里云回执验签 AccessKey Secret**——可与 `secret-key` 同值；HMAC-SHA1 密钥（§8.3） |
| `sms.<name>.weight` | 否 | 权重（映射 `setWeight`）；本期不做负载均衡，仅透传，默认 1 |
| `sms.<name>.max-retries` / `retry-interval` / `timeout` | 否 | sms4j 重试 / 超时参数（透传 `BaseConfig`），有 sms4j 默认值 |

> **凭据安全**：`access-key` / `secret-key` / `access-key-secret` 等敏感值一律走 `app-config.txt`，禁止硬编码；门禁读取配置只核键名不复制值。

### 6.2 通道类型映射（唯一定义点 `internal.sms4j.SmsSupplierTypes`）

| 配置 `type` 值 | sms4j supplier 字面量 | sms4j 配置类 | 本期状态 |
| --- | --- | --- | --- |
| `alibaba` | `alibaba` | `org.dromara.sms4j.aliyun.config.AlibabaConfig` | **首期落地** |
| 其余 24 项（supplier 全集去掉 alibaba，注意全集实际 25 项见下） | 同名 | 各 `*Config` | P1，按通道追加；首期不在白名单 → 配置即报「类型未知」 |

> supplier 字面量共 **25** 项。首期 `SmsSupplierTypes.supported()` 只返回 `{alibaba}`；其余类型在后续通道追加任务中逐个登记（每追加一家：加映射 + 必填项校验规则 + 单测），**不属于本期 P0**。

**类型映射规则**：`sms.<name>.type` 的值直接作为 sms4j supplier 字面量传给 `ProviderFactoryHolder.requireForSupplier(type)`；映射表 additionally 记录「supplier → 配置类构造器 + 必填键集合」，供 `SmsBlendFactory` 构建 `BaseConfig` 与 `SmsConfigLoader` 校验必填项。

### 6.3 配置解析与校验规则（唯一定义点 `aifei.SmsConfigLoader` + `internal.sms4j.SmsSupplierTypes`）

> 对齐 `aifei-oss`：`OssConfigLoader` 在 `aifei` 包（可读 `Prop`），配置规则委托 `internal.OssConfig`。本模块同理：`aifei.SmsConfigLoader` 读 `Prop`，类型映射与必填项规则在 `internal.sms4j.SmsSupplierTypes`，厂商配置项 → `BaseConfig` setter 的填充在 `internal.sms4j.SmsBlendFactory`。

1. **通道发现**：扫描 `prop.getProperties()` 中所有匹配 `sms.<name>.type` 的键，`<name>` 即通道标识；`<name>` 不得为空白、不得含 `.`（否则键解析歧义）。
2. **至少一条通道**：无任何 `sms.<name>.type` → 启动失败（场景 3）。
3. **类型合法**：`type` 不在 `SmsSupplierTypes.supported()` → 启动失败，消息含键名与原值（场景 4）。
4. **必填项**：按类型从 `SmsSupplierTypes.requiredKeys(type)` 取必填键集合，逐一校验 `sms.<name>.<key>` 非空；缺失 → 启动失败，消息含**完整键名与原值**（原值为空显示 `<empty>`，场景 3）。阿里云必填 = `{access-key, secret-key, signature}`。
5. **默认通道**：多通道时 `sms.default-channel` 必填且须指向已声明通道；单通道时可缺省取唯一通道；值指向不存在通道 → 启动失败（场景 7）。
6. **键名 → setter**：`kebab-case` 键名（如 `access-key`）转 `camelCase`（`accessKey`）后按 `SmsSupplierTypes` 的字段映射填 `BaseConfig` setter；未知配置项忽略（不报错，便于透传 sms4j 新参数）。
7. **失败不留半装配**：`SmsPlugin.start()` 内先全量解析校验成 `List<SmsChannelConfig>`，全部合法后才构建 `SmsBlend` 并注册；任一步失败 → 清空本模块注册表后再上抛（§7.2）。

---

## 7. 内部结构、依赖探测与行为契约

### 7.1 各类型职责（★ 后为该规则的唯一定义点）

| 类型 | 包 | 职责 |
| --- | --- | --- |
| `SmsClient` / `SmsKit` | 根 | 发送门面；委托 `internal.SmsClients` 取实例 |
| `SmsMessage` / `SmsResult` / `SmsException` | 根 | 值对象 / 统一异常（构造器 public，唯一构造点在 `SmsErrors`） |
| `SmsReceiptListener` / `SmsReceipt` | spi | 回执监听器 SPI + 回执内容值对象（公共承诺） |
| `SmsPlugin` | aifei | ★ 装配生命周期：`implements Plugin`、`start/stop` 幂等、失败回滚、注册 `@Inject` 单例（带 `singletonRegistered` 守卫）、调用 `SmsConfigLoader` |
| `SmsHandler` | aifei | ★ 回执路由：`extends Handler`（抽象类，非 implements）+ `handle(String path, I in, O out)`；URL 前缀解析、验签分发、回执解析、监听器交付、响应闭环（§8） |
| `SmsConfigLoader` | aifei | ★ `sms.*` 配置键名解析唯一定义点（读 `Prop`，返回 `List<SmsChannelConfig>`）；类型判定经 `SmsSupplierTypes.isSupported` |
| `SmsIo<I>` | aifei | **回执传输桥接接口（接入层非承诺构件，非计数）**：`params/body/reply`，因框架 `Output` 为空接口而引入，由消费方 / demo 实现；泛型无上界、零第三方 / 零框架类型 |
| `SmsChannelConfig` | internal | **单通道配置值对象（非承诺实现层，非计数）**：不可变，承载 `name`/`type`/原始 kebab-case `options`，验签密钥等经 `getOption` 按原键名读取 |
| `SmsClients` | internal | ★ 通道注册表：按通道标识存 `SmsClient` 实例 + `SmsChannelConfig` + 默认通道 + `started` 状态；`register(name, client, config)` / `registerDefault` / `defaultClient` / `use` / `channels` / `config` / `isStarted` / `setStarted` / `clear`（进程内 `ConcurrentHashMap`，非缓存出口） |
| `SmsSupplierTypes` | internal.sms4j | ★ 通道类型映射 + 必填键集合 + 合法值集合唯一定义点（§6.2 / §6.3）；`supported` / `isSupported` / `requiredKeys` / `newSupplierConfig`（构建并填充 sms4j 配置对象） |
| `SmsBlendFactory` | internal.sms4j | ★ 客户端批量构建唯一装配点：`buildClients(List<SmsChannelConfig>) → Map<String, SmsClient>`，内部经 `SmsSupplierTypes.newSupplierConfig` 建配置 → `SmsFactory.createSmsBlend(config)`（返回 **void**）→ `SmsFactory.getSmsBlend(configId)` 取回 → `new SmsSender(name, blend)`；★ mock 注入点 `setBlendFactory(Function)` |
| `SmsSender` | internal.sms4j | 发送实现：`implements SmsClient`，构造 `SmsSender(String channelName, SmsBlend blend)`，调 `SmsBlend.sendMessage`，结果/异常经 `SmsMappers` / `SmsErrors` 翻译 |
| `SmsMappers` | internal.sms4j | `toResult(channel, response)`：`SmsResponse` → `SmsResult`；`toReceipt(channel, params, rawBody)`：`SmsReceipt` 字段解析（状态/流水号/时间） |
| `SmsDeps` | internal.sms4j | ★ 唯一 `Class.forName` 点（`org.dromara.sms4j.core.factory.SmsFactory`，不初始化，第二参 `false`）；`requireSms4j()` + 包级 `requireSms4j(ClassLoader)`（测试注入）；缺失抛可读异常 |
| `SmsErrors` | internal | ★ `SmsException` 唯一构造点 + ★ 全部异常消息措辞唯一定义点（§7.5） |
| `AliyunReceiptVerifier` | internal.sms4j | ★ 阿里云回执验签唯一实现（HMAC-SHA1，§8.3）；`verify(Map<String,String> params, String accessKeySecret)` |

> **非承诺构件计数口径**：`SmsIo`（aifei）与 `SmsChannelConfig`（internal）是为适配真实框架 / sms4j API 而新增的**接入层 / 非承诺实现层构件**，与 `SmsPlugin` / `SmsHandler` / `SmsConfigLoader` / `SmsClients` / `SmsDeps` 等同类——**不计入根包 5 个公共类型与 spi 2 个公共类型**（门禁 ⑤ 的计数口径不变，仍按 §5.6 冻结名单断言）。公共签名零第三方 / 零框架类型约束对二者同样适用。

### 7.2 装配生命周期（`SmsPlugin`）

对齐 `OssPlugin` / `MqPlugin` / `AuditPlugin` 范式：

> 下列流程与实际 `SmsPlugin` 源码一致（签名以 `aifei/SmsPlugin.java` 为准）：

```text
SmsPlugin(Prop prop)               // 构造期只持引用，null → SmsErrors.propNull()，不解析不校验
  - if (prop == null) throw SmsErrors.propNull()
  - this.prop = prop

start()  [synchronized, 幂等]
  if started → return
  try:
    SmsDeps.requireSms4j()                         // ① 依赖探测（启动即失败）
    List<SmsChannelConfig> configs = SmsConfigLoader.load(prop)   // ② 全量解析+校验（失败含键名原值）
    String defaultChannel = SmsConfigLoader.defaultChannel(prop, configs)
    Map<String, SmsClient> clients = SmsBlendFactory.buildClients(configs)  // ③ 构建全部客户端（内部 newSupplierConfig→createSmsBlend(void)→getSmsBlend→new SmsSender）
    for each config: SmsClients.register(config.getName(), clients.get(config.getName()), config)  // ④ 三参：名/客户端/配置
    SmsClients.registerDefault(defaultChannel)
    SmsClients.setStarted(true)                    // ⑤ 共享装配状态置位（供门面 stop 后校验）
    if (!singletonRegistered):                     // ⑥ @Inject 单例，addSingletonObject 不幂等，仅注册一次
        AopKit.get().addSingletonObject(SmsClient.class, SmsClients.defaultClient())
        singletonRegistered = true
    started = true
  catch (RuntimeException | Error e):
    SmsClients.clear()                             // 失败回滚，不留半装配（含 started 复位）
    throw e

stop()   [synchronized, 幂等]
  if !started → return
  SmsClients.clear()                               // 清空注册表 + 默认通道 + started 复位
  started = false
  // 说明：singletonRegistered 不复位（框架无注销 @Inject 单例的公开 API，对齐 OssPlugin/MqPlugin），
  //       已注册单例对象生命周期内保持有效，但 SmsClients 的 started 复位后门面调用抛「插件未启动或已停止」（见下）。
```

**stop() 后调用语义（AC-04）**：`stop()` 后 `SmsKit.*` 与已注入的 `SmsClient` 实例调用 `send(...)` 时，`SmsSender.send` 首先检查 `SmsClients.isStarted()`，为 `false` → 抛 `SmsException("sms: plugin not started or stopped, call SmsPlugin.start() first")`（§7.5 场景 5）。这样即使 `@Inject` 单例对象仍被持有，也会因共享的 started 标志而失败，满足「stop() 后抛模块异常」。

**幂等与失败无残留**：`start()` / `stop()` 均 `synchronized` + `started` 守卫；连续两次无副作用、状态不叠加；`start()` 任一步失败先 `SmsClients.clear()` 再抛，再次以合法配置 `start()` 可正常工作（AC-05）。

### 7.3 发送链路

> 下列为调用链**示意**，实际签名见 `internal/sms4j/SmsSender.java` 与 `internal/sms4j/SmsMappers.java`。

```text
SmsKit.use(name).send(phone, templateId, variables)
  或 @Inject SmsClient （默认通道）
  → SmsSender.send(...)
      → 参数校验（phone/templateId 非空，SmsErrors 场景 1/2）
      → SmsClients.isStarted() 校验（场景 5）
      → SmsBlend.sendMessage(phone, templateId, variables)   // sms4j 内核（同步阻塞）
          · sms4j 内部：ProviderFactory → AlibabaSmsImpl → AliyunUtils 出站签名 → hutool-http POST
      → 成功：SmsMappers.toResult(channelName, response) → SmsResult
      → 厂商错误 / 网络异常 / sms4j 异常：catch → SmsErrors.supplierError / networkError → SmsException（保留 cause，消息不含第三方类型名）
          · 关键校验：SmsSender.requireSuccess(response) 对 response == null 或 !isSuccess() 抛 supplierError（默认失败路径走异常，见 §5.4）
```

- **换厂商只改配置**（AC-03）：业务代码只认 `SmsClient.send`；切换 `sms.<name>.type` + 厂商配置项后，`SmsBlendFactory` 构建对应厂商 `SmsBlend`，发送代码零改动。
- **多通道独立实例**（AC-02）：每个 `sms.<name>` 独立构建 `SmsBlend`（独立 configId），`SmsKit.use(name)` 取各自 `SmsSender`；默认通道由 `sms.default-channel` 切换。
- **并发**（Q-08）：`SmsClients` 用 `ConcurrentHashMap`；`SmsSender` 无可变字段；`SmsBlend` 经 sms4j 代理缓存且线程安全；模块不额外加锁。

### 7.4 依赖缺失探测

- **唯一探测点**：`internal.sms4j.SmsDeps.requireSms4j()`。
- **方式**：`Class.forName("org.dromara.sms4j.core.factory.SmsFactory", false, loader)`（第二参 `false` = 不初始化类）；捕获 `ClassNotFoundException` / `LinkageError`（含 `NoClassDefFoundError`）。
- **时机**：`SmsPlugin.start()`（启动即失败）。
- **缺失行为**：抛 `SmsException("sms: org.dromara.sms4j:sms4j-core:3.3.6 is required, add it to your dependencies")`（场景 12），**不裸抛** `NoClassDefFoundError`。
- **门禁**：`classForNameOnlyInDeps` 断言全模块仅此一处 `Class.forName`。

### 7.5 异常语义表（全部失败路径，无遗漏）

消息**逐字**由 `SmsErrors` 构造；`{...}` 为占位拼接值。分类：**参**=参数错误 / **依赖**=依赖缺失 / **装配**=装配期 / **厂商**=厂商返回错误 / **网络**=网络异常 / **验签**=回执验签失败 / **状态**=插件状态错误。

| # | 类 | 场景 | 行为 | 消息（逐字，唯一措辞来源） |
| --- | --- | --- | --- | --- |
| 1 | 参 | `send` 手机号为 null / 空白 | 拒绝 | `sms: phoneNumber must not be blank` |
| 2 | 参 | `send` 模板 ID 为 null / 空白（模板化发送时） | 拒绝 | `sms: templateId must not be blank` |
| 3 | 装配 | 未声明任何通道 / 必填项缺失 | 启动失败 | `sms: config {fullKey} is required but was: {rawValue}`（空值显示 `<empty>`） |
| 4 | 装配 | 通道类型未知 | 启动失败 | `sms: unknown channel type for {fullKey}: {rawValue}` |
| 5 | 状态 | 插件未启动 / 已停止时调用门面 | 拒绝 | `sms: plugin not started or stopped, call SmsPlugin.start() first` |
| 6 | 状态 | `SmsKit.use(name)` 通道不存在 | 拒绝 | `sms: channel not found: {name}` |
| 7 | 装配 | `sms.default-channel` 指向不存在通道 / 多通道缺失 | 启动失败 | `sms: default-channel {rawValue} is invalid or missing` |
| 8 | 依赖 | sms4j 缺失（探测失败） | 包装可读异常 | `sms: org.dromara.sms4j:sms4j-core:3.3.6 is required, add it to your dependencies` |
| 9 | 厂商 | 厂商返回错误应答 | 包装 | `sms: supplier error on channel {name}, code: {vendorCode}, message: {vendorMsg}`（cause=原异常或厂商异常数据） |
| 10 | 网络 | 发送底层网络异常 | 包装 | `sms: network error on channel {name}, cause: {causeMsg}`（cause=原异常） |
| 11 | 验签 | 回执验签失败 | 拒绝回执 | `sms: receipt signature verification failed on channel {name}` |
| 12 | 验签 | 回执报文解析失败 | 拒绝回执 | `sms: receipt parse failed on channel {name}, cause: {causeMsg}`（cause=原异常） |

- **不降级**：任何失败都抛 `SmsException`，不返回半截数据、不静默吞错、不吞 cause。
- **cause 保留**：场景 9/10/12 用 `new SmsException(msg, cause)`，其余 `new SmsException(msg)`。
- **消息不含第三方类型名**：厂商异常的类名（如 `SmsBlendException`）不得进入消息，只取错误码与描述。
- **枚举值**：以上措辞为唯一定义点，测试按表逐字断言；门禁 `errorMessagesOnlyInErrors` 锁定 `sms: ` 前缀仅出现在 `internal/SmsErrors.java`（公共类型与测试断言除外）。

### 7.6 mock 可注入点

> 下列签名与实际 `internal/sms4j/SmsBlendFactory.java` 一致（`SmsFactory.createSmsBlend` 返回 `void`，不可写作方法引用 `SmsFactory::createSmsBlend`）：

```java
// internal.sms4j.SmsBlendFactory —— 默认装配函数：createSmsBlend 返回 void，需再按 configId 取回
private static final Function<SupplierConfig, SmsBlend> DEFAULT_FACTORY =
        new Function<SupplierConfig, SmsBlend>() {
            public SmsBlend apply(SupplierConfig config) {
                SmsFactory.createSmsBlend(config);                    // void：内部注册
                return SmsFactory.getSmsBlend(config.getConfigId());  // 取回已注册实例
            }
        };

private static Function<SupplierConfig, SmsBlend> blendFactory = DEFAULT_FACTORY;

// 包级可见（同包测试 / demo 经反射或同包辅助类调用）；factory == null 时恢复 DEFAULT_FACTORY：
static void setBlendFactory(Function<org.dromara.sms4j.api.universal.SupplierConfig,
                            org.dromara.sms4j.api.SmsBlend> factory) { ... }
```

- **注入对象**是 sms4j 的 `SmsBlend` 接口（测试自行实现 mock，返回预设 `SmsResponse`），**不涉及真实短信**。
- 注入点位于 `internal`，**不进入公共面**（门禁不计数）；注入时机在 `SmsPlugin.start()` 之前。
- demo 通过「同包桥接辅助类」或反射设置该注入点，实现零外网 mock 发送（§12）。

---

## 8. 回执接收设计（R-05 / AC-07）

### 8.1 回执链路总览

> 下列链路与实际 `aifei/SmsHandler.java` 一致；读写交换经 `SmsIo<I>` 桥接（`params/body/reply`）：

```text
厂商 HTTP 回调 POST /sms/callback/{channel}
  → SmsHandler.handle(String path, I in, O out)   [extends Handler（抽象类）；aifei 接入层，唯一 import cn.aifei.* 的回执构件]
      → 前缀判断：path 不以 {prefix}/ 开头 → 交 next.handle(...)，非短信路径不处理
      → 路由：channelOf(path) 取 {channel} 段 → SmsClients.config(channel) 取 SmsChannelConfig
          · config == null 或 !SmsClients.isStarted() → io.reply(in, 404, FAIL_JSON)，结束交换
      → 读请求：io.params(in) → Map<String,String>；io.body(in) → 原始 body（异常 → io.reply 500 FAIL）
      → 验签：verified(config, params)：isSupported(type) 且 verifier.verify(params, secret)
          · secret = config.getOption("access-key-secret")（§8.3）
          · AliyunReceiptVerifier.verify(Map<String,String> params, String accessKeySecret)
          · 验签失败 / 不支持类型 → io.reply(in, 403, FAIL_JSON)，不交付监听器，结束交换
      → 解析：SmsMappers.toReceipt(channel, params, body) → SmsReceipt（状态 / 流水号 / 时间 / 手机号 / 原始报文）
      → 交付：遍历已注册的 SmsReceiptListener → onReceipt(receipt)（同步调用；单个监听器异常记入 lastListenerError 并继续其余监听器）
      → 响应：任一监听器异常 → io.reply(in, 500, FAIL_JSON)；全部成功 → io.reply(in, 200, SUCCESS_JSON)，结束交换
```

- **注册顺序敏感**（对齐 `aifei-weixin` 的 `WeixinHandler` 须在 `FrontHandler` 之前）：`SmsHandler` 必须注册在 `FrontHandler` 之前，否则回调 GET/POST 被渲染成首页。
- **模块不落库、不做幂等去重**：重复投递由消费方监听器按流水号自行处理（对齐 `aifei-weixin` 契约）。

### 8.2 `SmsHandler` 契约（`cn.hg.aifei.sms.aifei`）

**形态**：`public final class SmsHandler<I extends Input, O extends Output> extends Handler<I, O>`（`Handler` 是**抽象类**，须 `extends` 而非 `implements`）。构造：`SmsHandler(SmsIo<I> io, List<SmsReceiptListener> listeners)` / `SmsHandler(String prefix, SmsIo<I> io, List<SmsReceiptListener> listeners)`；入口：`handle(String path, I in, O out) throws Exception`。因框架 `Output` 为空接口、无写响应能力，读写交换一律经 `SmsIo<I>` 桥接（§5.5）。

| 关注点 | 行为 |
| --- | --- |
| 前缀 | 默认 `/sms/callback`（常量 `DEFAULT_PREFIX`），可自定义；构造期归一化（trim、去尾斜杠、补前导 `/`），空值抛异常 |
| 路由 | URL 形如 `{prefix}/{channel}`；取前缀后第一段为通道标识；`SmsClients.config(channel)` 取不到配置或未装配 → `io.reply(in, 404, FAIL_JSON)` 并结束交换 |
| 验签分流 | 按通道 `type` 选验签器；`SmsSupplierTypes.isSupported(type)` 为假或 `AliyunReceiptVerifier.verify(params, secret)` 失败 → `io.reply(in, 403, FAIL_JSON)`；密钥取自 `config.getOption("access-key-secret")` |
| 闭环 | 匹配到短信路径即经 `SmsIo` 自行读写响应、**结束交换**，不再调用 `next.handle(...)`；非短信路径交 `next` |
| 构造校验 | `io` / `listeners` 均非 null（可空监听器列表 → 验签通过后仅回 SUCCESS，不交付）；前缀非空 |
| 排障 | `lastListenerError()` 返回最近一次监听器异常（无则 `null`）；监听器异常不影响其余监听器，但结果为 500 FAIL（§8.4） |

### 8.3 阿里云验签实现（首期落地）

- **算法**：HMAC-SHA1（阿里云短信状态报告回调签名 `signature` = Base64(HMAC-SHA1(accessKeySecret, 排序后的参数规范串))）。
- **实现**：`AliyunReceiptVerifier.verify(Map<String,String> params, String accessKeySecret)`：
  1. 取回调参数中 `signature`（大小写按阿里云回调字段），缺失 → false；
  2. 剔除 `signature` 键，其余参数按 key 字典序排序，拼 `key+value`（不加分隔符，符合阿里云规范）；
  3. `Mac.getInstance("HmacSHA1")` + `SecretKeySpec(accessKeySecret.getBytes(UTF_8), "HmacSHA1")` → `doFinal` → `Base64.encode`；
  4. `MessageDigest.isEqual(expectedBytes, providedBytes)` 常量时间比较（防时序攻击）→ 布尔结果。
- **密钥来源**：`sms.<channel>.access-key-secret`（§6.1），从 `SmsChannelConfig` 取；缺失 → 验签失败（场景 11）。
- **纯 JDK**：仅用 `javax.crypto.Mac` / `java.security.MessageDigest` / `java.util.Base64`；不引入任何第三方密码学库。

### 8.4 回执内容解析与响应契约（按通道定稿）

**阿里云回执字段解析**（`SmsMappers.toReceipt`）：

| `SmsReceipt` 字段 | 阿里云回调来源 | 缺省处理 |
| --- | --- | --- |
| `status` | `ResultCode`（如 `OK` / 失败码）或 `Status` | 缺失 → 空串 |
| `requestId`（流水号） | `MessageId` / `BizId` | 缺失 → 空串 |
| `reportTime`（时间） | `ReportTime` / `NotifyTime`（解析为毫秒时间戳） | 缺失 → 接收时刻 `System.currentTimeMillis()` |
| `phoneNumber` | `PhoneNumber` / `DestCode` 相关字段 | 缺失 → 空串 |
| `channelName` | URL 路由得到的通道标识 | 必有 |
| `rawBody` | 原始 POST body | 必有 |

**响应契约**：

| 场景 | HTTP 状态 | 响应体 | 是否交付监听器 |
| --- | --- | --- | --- |
| 验签通过 + 解析/交付成功 | 200 | `{"Status":"SUCCESS"}` | 是 |
| 验签失败 | 403 | `{"Status":"FAIL"}` | **否** |
| 解析失败 / 监听器抛异常 | 500 | `{"Status":"FAIL"}` | 否（异常监听器不影响其他监听器，记录后仍回 FAIL） |
| 未知通道 / 不支持验签类型 | 404 / 403 | `{"Status":"FAIL"}` | 否 |

- HTTP 状态码与响应体双表达（便于排障）；对齐阿里云回调应答约定。
- `SmsReceiptListener` 同步调用（在回调线程内），消费方耗时逻辑应自行转异步（对齐 `aifei-weixin`「回调链路禁止同步调用 Api」的教训）。

---

## 9. 架构门禁设计（`ArchitectureBoundaryTest`）

- **位置 / 机制**：`src/test/java/cn/hg/aifei/sms/ArchitectureBoundaryTest.java`；`Files.walk(Paths.get("src/main/java"))` 收集全部 `.java`，按**源码文本**断言（路径分隔符归一化为 `/`，注释剥离后匹配字面量）。
- **执行位置**：必须在 `aifei-sms/` 根目录（依赖相对路径 `src/main/java`），不得放宽断言。
- **关键常量**：

```java
private static final Path   SOURCE_ROOT = Paths.get("src/main/java");
private static final String ROOT_PACKAGE     = "cn/hg/aifei/sms/";
private static final String SPI_PACKAGE      = "cn/hg/aifei/sms/spi/";
private static final String BRIDGE_PACKAGE   = "cn/hg/aifei/sms/aifei/";
private static final String ADAPTER_PACKAGE  = "cn/hg/aifei/sms/internal/";
private static final String SMS4J_PACKAGE    = "cn/hg/aifei/sms/internal/sms4j/";
private static final int    EXPECTED_ROOT_TYPES = 5;
private static final int    EXPECTED_SPI_TYPES = 2;
private static final Set<String> EXPECTED_ROOT_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
        "SmsClient.java", "SmsKit.java", "SmsMessage.java", "SmsResult.java", "SmsException.java"));
private static final Set<String> EXPECTED_SPI_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
        "SmsReceiptListener.java", "SmsReceipt.java"));
```

### 9.1 断言清单（14 例，方法名可直接照写为测试方法）

| # | 测试方法 | 判定口径 | 风险针对 |
| --- | --- | --- | --- |
| ① | `sms4jOnlyInAdapter` | 每条 `import org.dromara.sms4j.` 只允许出现在 `SMS4J_PACKAGE` 下文件；出现在根包 / spi / aifei / internal 其它子包即失败 | 第三方实现类型泄漏 |
| ② | `frameworkOnlyInBridgePackage` | `import cn.aifei.` 只允许出现在 `BRIDGE_PACKAGE` 下文件 | D12「零框架引用」被绕过 |
| ③ | `noSqlOrUndertowInLibrary` | 全模块 `.java` 不得 `import io.undertow.` / `import java.sql.`；pom 不得有 JDBC / `aifei-db` | 库模块零 Web / 零 DB |
| ④ | `noSlf4jBinding` | 全模块 `.java` 不得 `import org.slf4j.`；pom 不声明 `slf4j-simple` / `logback` / `log4j`（人工核对） | 日志绑定被悄悄引入 |
| ⑤ | `publicSurfaceExact` | 根包直接子 `.java` 数量精确 = 5 且集合等于 `EXPECTED_ROOT_TYPES_SET`；`spi` 包精确 = 2 且集合等于 `EXPECTED_SPI_TYPES_SET` | 同名替换 / 增删公共类型 |
| ⑥ | `publicSignaturesNoThirdParty` | 根包 + `spi` 包源码中不得出现 `org.dromara.sms4j.` / `cn.aifei.` 字面量（import 与全限定名均查） | 公共签名零第三方 / 框架类型 |
| ⑦ | `classForNameOnlyInDeps` | `Class.forName` 只允许出现在 `internal/sms4j/SmsDeps.java` | 依赖探测散落（§7.4） |
| ⑧ | `errorMessagesOnlyInErrors` | 消息前缀 `sms: ` 只允许出现在 `internal/SmsErrors.java`（公共类型与测试断言除外） | 异常消息措辞唯一定义点（§7.5） |
| ⑨ | `java8Compatibility` | 不得出现 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` 局部声明 / `"""` / `sealed ` / `instanceof` 模式匹配 | Java 8 兼容底线 |
| ⑩ | `noStarterDependency` | pom 不得出现 `sms4j-spring-boot-starter` / `sms4j-solon-plugin` / `org.springframework` / `org.noear` | 禁 starter / solon |
| ⑪ | `supplierTypesOnlyInFactory` | supplier 字面量集合（如 `"alibaba"`）的映射定义只允许出现在 `internal/sms4j/SmsSupplierTypes.java` | 通道类型映射唯一定义点（§6.2） |
| ⑫ | `pluginBridgeContract` | `aifei` 包必须存在 `SmsPlugin.java` 与 `SmsHandler.java`（防门禁假通过）；二者须 `import cn.aifei.` | 接入构件存在性 + 边界 |
| ⑬ | `configKeysOnlyInLoader` | 字面量 `sms.default-channel` 与 `sms.` 配置前缀解析只允许出现在 `aifei/SmsConfigLoader.java` | 配置键名唯一定义点（§6.3） |
| ⑭ | `testSourcesOfflineOnly` | 扫描 `src/test/java`：`http://` / `https://` 字面量 host 只能是 `127.0.0.1` / `localhost`（注释先剥离） | 测试联网 |

> **实现要点**：私有工具 `javaFiles(Path)` / `imports(String)` / `stripComments(String)` / `normalize(Path)` / `isDirectChild(String, String)`；异常消息须列出**违规文件 + 违规内容**。
> 门禁**不得为兼容错误用法而放宽或绕过**；新增例外必须先改本文契约章节再改断言，并提请主会话登记（根 `AGENTS.md`）。
> 门禁外的构建期断言：主 JAR 全部 class major = 52（`javap -verbose` 抽查 `SmsClient.class` / `SmsKit.class` / `SmsPlugin.class` / `SmsDeps.class`）；`mvn dependency:tree` 只出现依赖允许清单。

---

## 10. 依赖与构建

### 10.1 依赖表

| 依赖 | 范围 | 用途 |
| --- | --- | --- |
| `cn.aifei:aifei:1.1.2` | **`provided`** | 生态底座（`Plugin` / `Prop` / `AopKit` / `Handler` / `Input` / `Output`）；本模块源码仅 `aifei` 包引用；消费方自带、不传递 |
| `org.dromara.sms4j:sms4j-core:3.3.6` | **`optional`** | 唯一实现内核，经 `internal.sms4j` 引用；不传递，缺失时启动期可读失败（场景 8） |
| `io.github.gollyhu:aifei-tools` | `optional` | 阿里云回执 HMAC-SHA1 验签复用 `crypto.CryptoUtil`（`AliyunReceiptVerifier`）；不向下游传递 |
| `junit:junit:4.13.2` | `test` | 单元测试 + 架构门禁 |
| （测试辅助）mock `SmsBlend` | test 源码 | 测试自行实现 `SmsBlend` 接口返回预设 `SmsResponse`，不真实发短信、不提交二进制 |

- **零日志绑定**：不声明 `slf4j-simple` / `logback` / `log4j`（sms4j 传递的 `slf4j-api` 仅 API，无绑定；消费方自选）。
- **零 DB / 零 Web 容器**：无 JDBC / 连接池；不引入 `io.undertow.*`（库模块本体）。
- pom 对齐 `aifei-http/pom.xml` / `aifei-excel/pom.xml` 模板：`maven-compiler-plugin`（`source`/`target` 1.8、UTF-8）+ `maven-surefire-plugin`（`workingDirectory = ${project.basedir}`，门禁依赖相对路径）+ forceCreation source plugin + javadoc plugin（`excludePackageNames` 排除 `internal`）。**不引入** Central 发布元数据与 profile。

### 10.2 `dependency:tree` 允许清单

以依赖树为准。断言口径重申：① 除 `aifei:provided` / `sms4j-core:optional` 及其传递项 / `junit:test` 外，出现任何条目即依赖失控；② sms4j 全传递项均 `optional`；③ 无日志绑定实现；④ 无 Spring / JDBC / 连接池；⑤ 无 starter / solon；⑥ 允许清单额外登记两项**受限传递依赖**——`fastjson2`（scope `provided`，由消费方自带）与 `hamcrest-core`（scope `test`，由 `junit` 传递），二者均非本模块 pom 直接声明、不进消费方运行 classpath，**不违背**「零强制第三方运行依赖 / 无日志绑定 / sms4j optional」纪律：`fastjson2` 为 provided（消费方自带、不传递）、`hamcrest-core` 仅测试期、二者均不属 sms4j 传递链且非任何日志绑定实现；精确坐标与版本以 `mvn dependency:tree` 为准。

### 10.3 验证命令（模块根目录）

```bash
cd aifei-sms
mvn clean verify                                                        # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest                                # 仅跑架构门禁（14 例）
mvn dependency:tree                                                     # 断言：仅依赖允许清单
javap -verbose target/classes/cn/hg/aifei/sms/SmsClient.class | findstr "major version"   # 期望 52
```

---

## 11. 跨文件约定

### 11.1 跨文件约定

| 约定 | 内容 |
| --- | --- |
| **命名** | 根包公共类型逐字按 §5；`internal` / `aifei` 类型按 §7.1 表名；测试用 mock 用业务视角命名，禁 `foo`/`bar`/`test` 无义名；文件头版权块按根 `AGENTS.md` 统一声明 |
| **公共方法 javadoc** | 简体中文；根包 5 类型 + `spi` 每个公共方法写全 `@param` / `@return` / `@throws SmsException`；`SmsKit` 明确「须先 `SmsPlugin.start()`」 |
| **异常消息** | 唯一定义点 `SmsErrors`，逐字对齐 §7.5；`sms: ` 前缀；禁止在其它实现类自拼（门禁 ⑧） |
| **依赖探测** | 唯一 `Class.forName` 点 `SmsDeps`（门禁 ⑦）；不反射实例化厂商、不 `ServiceLoader` |
| **配置键解析** | 唯一 `aifei/SmsConfigLoader`（门禁 ⑬）；类型映射唯一 `internal/sms4j/SmsSupplierTypes`（门禁 ⑪）；kebab→camel 转换只此一处 |
| **回执验签** | 唯一 `internal/sms4j/AliyunReceiptVerifier`；纯 JDK `HmacSHA1`，常量时间比较；密钥只从 `SmsChannelConfig` 取 |
| **线程安全** | `SmsClients` 用 `ConcurrentHashMap`；`SmsSender` 无可变字段；`SmsPlugin.start/stop` synchronized；模块不自建线程池 |
| **行尾与编码** | `.md` / `.java` 一律 CRLF、UTF-8（根 `AGENTS.md`，`core.autocrlf=true`）；批量改写按字节读写、整文件读入断言后单次写回 |
| **测试离线** | 全部单测零外网；mock `SmsBlend` 返回预设 `SmsResponse`，不真实发短信；门禁 ⑭ 扫测试源码 host |
| **git 纪律** | Conventional Commit；不改冻结契约；`git status` 无 `AD` 残留；当日工作日志追加仓库根协作记忆目录 |

---

## 12. 测试策略与 demo 端点清单

### 12.1 测试策略

| 层 | 覆盖 | 判据 |
| --- | --- | --- |
| **架构门禁** | `ArchitectureBoundaryTest` 14 例（§9） | 全绿；不得放宽 |
| **字节码** | 主 JAR 全部 class major = 52（`javap` 抽查 `SmsClient` / `SmsKit` / `SmsPlugin` / `SmsDeps`） | 恒 52 |
| **发送契约** | `SendContractTest`：模板化发送返回 `SmsResult` / 换厂商业务代码零改动 / 多通道独立实例 / 结果字段映射（含 cause） | §5.1 / §7.3 / AC-01~AC-03 |
| **装配生命周期** | `PluginLifecycleTest`：start 后可用 / stop 后抛场景 5 / start×2、stop×2 幂等 / 失败后合法重装配 | §7.2 / AC-04~AC-05 |
| **配置校验** | `ConfigValidationTest`：无通道 / 必填缺失（消息含键名原值）/ 类型未知 / 默认通道非法 | §6.3 / AC-05 |
| **依赖缺失** | `DependencyMissingTest`：排除 sms4j 后 start 抛场景 8 可读异常，无裸 `NoClassDefFoundError` | §7.4 / AC-06 |
| **回执** | `ReceiptTest`：验签通过交付 / 验签失败拒绝+403 / 解析字段 / 监听器异常 / 响应契约 / 未知通道 404 | §8 / AC-07 |
| **异常契约** | `ExceptionContractTest`：参数 / 厂商 / 网络 / 验签逐字消息 + cause 保留 + 消息不含第三方类型名 | §7.5 / AC-08 |
| **公共面契约** | `PublicApiContractTest`：根包精确 5 / spi 精确 2（`SmsReceiptListener` / `SmsReceipt`）/ 公共签名零第三方 / `SmsKit` 形态 / `SmsClient` 接口 | §5 / Q-01 |
| **依赖树** | `mvn dependency:tree` 仅依赖允许清单；无 Spring / 日志绑定 / starter | §10.2 / Q-03 |

- **离线**：全部单测零外网；mock `SmsBlend` 返回预设 `SmsResponse`（门禁 ⑭）。
- **判据**：`aifei-sms/` 内 `mvn clean verify` **0 failure / 0 error**。

### 12.2 demo 端点清单（`aifei-sms-demo`，不发布）

- **位置**：仓库根 `aifei-sms-demo/`（与 `aifei-sms/` 平级）；依赖 `io.github.gollyhu:aifei-sms:1.0.0-SNAPSHOT` + `org.dromara.sms4j:sms4j-core:3.3.6`（demo 以 `compile` 自带，验证 optional 口径）+ `cn.aifei:aifei`（demo 以 `compile` 自带，验证 provided 口径）。
- **端口**：**18180**（18100 mq / 18110 job / 18120 http / 18130 trace / 18140 id / 18150 excel / 18160 audit / 18170 i18n 已占用）。
- **形态**：自写 HIO（包名 `cn.hg.aifei.sms.demo`，跟随 `aifei-excel-demo` 的 `arch` 同步类结构：`ContentType` / `In` / `InParser` / `IoDispatcher` / `IoHandler` / `Out` / `OutCode` / `TargetFactory`）+ `console/**`（自带日志）+ `SmsDemoService` + `DemoAppConfig` + `start-demo.bat` / `stop-demo.bat`；离线可跑、零外网。
- **mock**：demo 经 §7.6 注入点设 `MockSmsBlend`（返回预设 `SmsResponse`），**不真实发短信**。
- **端点契约**（信封 `code` / `data` / `msg`，HTTP 状态恒 200，语义由 `code` 承载；`/sms/demo/...` 命名空间；回执模拟端点除外）：

| 端点 | 方法 | 请求 | `data` 内容 | 对应场景 |
| --- | --- | --- | --- | --- |
| `/sms/demo/page` | GET | — | 页面（静态层 HTML） | 空态 |
| `/sms/demo/send` | POST | 通道名 + 手机号 + 模板 ID + 变量 JSON | `{ channel, success, requestId, message }` —— 经 mock `SmsBlend` 返回 | 场景 1 模板化发送 |
| `/sms/demo/channels` | GET | — | `{ channels: [...], default: "..." }` —— 注册表内容 | 场景 2 多通道切换 |
| `/sms/demo/config-error` | GET | — | `{ error: "sms: config sms.aliyun.access-key is required but was: <empty>" }` —— 以非法配置尝试装配的真实错误消息 | 场景 3 配置错误态 |
| `/sms/demo/dependency-missing` | GET | — | `{ error: "sms: org.dromara.sms4j:sms4j-core:3.3.6 is required ..." }` —— 经模拟探测失败得到的真实错误消息 | 场景 4 依赖缺失态 |
| `/sms/callback/{channel}`（Handler） | POST | 模拟厂商回调报文 + 签名 | 走真实 `SmsHandler`：验签 → 解析 → 监听器 → `{"Status":"SUCCESS"/"FAIL"}` | 场景 5 回执接收 |
| `/sms/demo/receipts` | GET | — | `{ receipts: [ {status, requestId, reportTime, phoneNumber, channel} ] }` —— 监听器记录内容 | 场景 5 展示 |
| `/sms/demo/state` | GET | — | `{ sms4jAvailable: true, version: "3.3.6", channels: [...] }` | 依赖状态展示 |

- **端点真实性**：`/send` 必须真实走 `SmsKit`→`SmsSender`→mock `SmsBlend` 全链路（不得静态假数据）；`/sms/callback/{channel}` 必须经真实 `SmsHandler` 验签与解析；`/config-error` / `/dependency-missing` 的消息由 `SmsErrors` 真实构造（非硬编码字符串）。

### 12.3 demo 页面展示内容（6 场景）

1. **模板化发送**：填手机号 + 选模板 + 填变量 → 发送 → 展示状态 + 厂商流水号（mock，不真实发短信）。
2. **多通道切换**：下拉切换通道 → 同一发送代码不变、结果落点不同（不同通道标识）。
3. **配置错误态**：展示必填项缺失的真实启动失败消息（含键名与原值）。
4. **依赖缺失态**：展示「未引入 sms4j」时首次调用的真实可读异常（含依赖坐标与引入提示）。
5. **回执接收**：模拟厂商回调 POST → 展示回执内容（状态 / 流水号 / 时间）；验签失败展示拒绝原因。
6. **空态**：首次进入、未触发操作时列表显示占位文案，不残留上次数据。

- **离线可跑**：页面零外网请求；`start-demo.bat` 启动后浏览器访问 `http://localhost:18180/sms/demo/page` 可完成一轮「发送 → 切换通道 → 模拟回执」闭环。


---

## 14. 验收标准（AC）映射

| # | 判定条件 | 验证通道 | 对应 P0 |
| --- | --- | --- | --- |
| AC-01 | 配置阿里云通道 → `SmsKit.use` / `@Inject` 取实例 → 模板化发送返回 `SmsResult`（含状态与流水号）；失败抛可读 `SmsException`（mock，不真实发） | `SendContractTest` + §7.6 mock 注入点 | R-01 |
| AC-02 | 两通道各取独立实例分别发送；仅改 `sms.default-channel` 默认通道切换、业务代码不变 | `SendContractTest` | R-02 |
| AC-03 | 换厂商只改 `sms.*` 配置，业务发送代码零改动仍成功 | `SendContractTest`（切换 type + mock 厂商响应） | R-01 |
| AC-04 | start 后可用；stop 后发送抛「插件未启动或已停止」；start×2 / stop×2 幂等 | `PluginLifecycleTest` + §7.2 | R-03 |
| AC-05 | 配置非法启动即失败、消息含键名与原值；失败无残留、合法重装配可用 | `ConfigValidationTest` + `PluginLifecycleTest` | R-03 |
| AC-06 | 缺 sms4j 时首次调用抛可读 `SmsException`（含坐标），无裸 `NoClassDefFoundError` | `DependencyMissingTest` + 门禁 ⑦ | R-04 |
| AC-07 | 回调验签通过 → 监听器收到解析回执（状态/流水号）；验签失败不交付、返回约定错误响应 | `ReceiptTest` + §8 | R-05 |
| AC-08 | 参数/厂商/网络/验签错误收敛 `SmsException`、保留 cause、消息可读且不含第三方类型名 | `ExceptionContractTest` + 门禁 ⑧ | R-06 |
| AC-09 | 根包/spi 零第三方、零框架；`org.dromara.sms4j.*` import 仅 `internal.sms4j` | 门禁 ①②⑥ | Q-01 |
| AC-10 | 根包公共类型精确 5（`SmsClient`/`SmsKit`/`SmsMessage`/`SmsResult`/`SmsException`），spi 精确 2（`SmsReceiptListener` / `SmsReceipt`） | 门禁 ⑤ `publicSurfaceExact` + 反射 | Q-01 |
| AC-11 | `cn.aifei.*` 仅 `aifei` 包；`java.sql.*` / `io.undertow.*` 全模块禁止 | 门禁 ②③ | Q-01/Q-07 |
| AC-12 | 主 JAR 全 class major 52；源码无 Java 8 之后语法/API | 门禁 ⑨ + `javap` 抽查 | Q-02 |
| AC-13 | 除 aifei(provided) 外 sms4j optional、无其它第三方编译/运行依赖、无日志绑定、无 starter | 门禁 ④⑩ + `dependency:tree` | Q-03 |
| AC-14 | 模块目录 `mvn clean verify` 0 failure / 0 error | 构建日志 | Q-04 |
| AC-15 | 文档四件套齐备 + 根 README 补条目 | 文件检查 + T09 | Q-05 |
| AC-16 | demo 端口 18180 可启动、六场景可见、零外网 host | 门禁 ⑭（demo 侧） | Q-06 |

> **P0 覆盖核对**：R-01~R-06 + Q-01~Q-08 全部映射到 AC-01~AC-16 与 §9 门禁，无遗漏。Q-08（线程安全）在 §5.1 / §7.3 / §11.1 以契约 + 实现约定覆盖，并由 `SendContractTest` 并发用例验证。

---
