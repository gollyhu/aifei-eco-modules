# aifei-sms

基于 **sms4j-core 3.3.6** 二次封装的短信通道 SDK：一套接口聚合多厂商，模板化发送 + 回执接收，换厂商只改 `sms.*` 配置、不改业务代码。库模块（可被应用依赖），非业务应用工程。

坐标：`io.github.gollyhu:aifei-sms:1.0.0-SNAPSHOT`（Java 8，class major 52）。

## 能力边界

- 根包 `cn.hg.aifei.sms` 精确 5 个公共类型：`SmsClient` / `SmsKit` / `SmsMessage` / `SmsResult` / `SmsException`；`spi` 包 2 个：`SmsReceiptListener` / `SmsReceipt`。公共签名零第三方 / 零框架类型。
- 底层发送一律走 sms4j `SmsFactory` / `SmsBlend`；`org.dromara.sms4j.*` 只在 `internal.sms4j` 单点引用；**禁** starter / solon，**不自行实现发送协议**。
- **只提供同步发送**：调用线程阻塞至厂商返回，不暴露异步 API、不自带线程池；异步由消费方自行包装。
- 零存储：不落库、不做回执去重（由消费方按流水号处理）；零日志绑定。
- 首期落地阿里云（`alibaba`），其余厂商按通道追加。

## 依赖（必读）

| 依赖 | 范围 | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei:1.1.2` | `provided` | 消费方自带，不传递 |
| `org.dromara.sms4j:sms4j-core:3.3.6` | **`optional`** | 唯一实现内核，**不传递** |
| `junit:junit:4.13.2` | `test` | 单元测试 + 架构门禁 |

> **`optional` 警告**：消费方必须显式引入 `org.dromara.sms4j:sms4j-core:3.3.6`（连带其传递项）才有发送能力。缺失时 `SmsPlugin.start()` 启动即失败，抛可读 `SmsException`（`sms: org.dromara.sms4j:sms4j-core:3.3.6 is required, add it to your dependencies`），**不会**裸抛 `NoClassDefFoundError`。仅使用门面类不受影响——这是「能力按需引入」。

不声明任何日志绑定实现；sms4j 传递的 `slf4j-api` 仅 API，消费方自选绑定。

## 装配与发送

在应用 `AppConfig#config(Plugins)` 注册插件（构造期只持配置引用，解析与校验在 `start()`）：

```java
plugins.add(new SmsPlugin(prop));   // prop 来自 app-config.txt
```

发送（默认通道经 `@Inject SmsClient` 或 `SmsKit.defaultClient()`；命名通道经 `SmsKit.use(name)`）：

```java
SmsResult result = SmsKit.use("aliyun").send("13800138000", "SMS_001", variables);
// variables 为 LinkedHashMap<String,String>，可为 null / 空
SmsResult direct = SmsKit.send("13800138000", "短信内容");   // 内容直发
```

`SmsKit` 是有状态门面：须先 `SmsPlugin.start()`；未装配或 `stop()` 后调用抛 `SmsException`。`start()` / `stop()` 幂等；配置非法启动即失败且失败回滚（不留半装配）。

## 配置（`sms.*`，走 app-config.txt）

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `sms.<name>.type` | 是 | 厂商类型，首期白名单仅 `alibaba` |
| `sms.<name>.access-key` | 阿里云必填 | AccessKeyId |
| `sms.<name>.secret-key` | 阿里云必填 | AccessKeySecret |
| `sms.<name>.signature` | 阿里云必填 | 短信签名 |
| `sms.<name>.template-id` | 否 | 默认模板 ID |
| `sms.<name>.region-id` | 否 | 阿里云地域（默认 `cn-hangzhou`） |
| `sms.<name>.endpoint` | 否 | 厂商请求地址 |
| `sms.<name>.access-key-id` / `access-key-secret` | 否（验签用） | 回执验签密钥，可与发送凭据同值 |
| `sms.<name>.weight` / `max-retries` / `retry-interval` / `timeout` | 否 | sms4j 参数透传 |
| `sms.default-channel` | 多通道必填 | 默认通道标识；单通道可缺省 |

凭据一律走配置中心，禁止硬编码。

## 回执接收

消费方实现 `SmsReceiptListener` 并随 `SmsHandler` 注册（**须注册在渲染首页的 Handler 之前**）：

```java
SmsHandler<In, Out> handler = new SmsHandler<>(smsIo, listeners);   // smsIo 提供回调参数/body 读取与响应写回
settings.setServer(new UndertowServer(), new IoDispatcher())
        .addHandler(handler)          // 先注册
        .addHandler(new IoHandler("view"));
```

回调路径 `/sms/callback/{channel}`：按通道验签（阿里云 HMAC-SHA1，纯 JDK）→ 解析 → 同步交付监听器。响应契约：验签通过 `200 {"Status":"SUCCESS"}`；验签失败 `403 {"Status":"FAIL"}`；解析/监听器异常 `500 {"Status":"FAIL"}`；未知通道 `404`。`SmsIo` 是接入层桥接接口（`Output` 为空接口，写响应须由传输层提供实现），零第三方 / 零框架类型。

## 本期不做

邮件 / APP 推送、模板 CRUD 与审核、内容审核 / 敏感词、频率限制 / 防刷、多厂商负载均衡 / 故障切换、异步发送 API 与内置线程池、发送记录持久化、批量群发、国际短信特殊处理。

## 文档与示例

- 契约事实来源：[docs/design.md](docs/design.md)；测试策略：[docs/testing.md](docs/testing.md)；开发约束：[AGENTS.md](AGENTS.md)
- 零外网演示工程：[aifei-sms-demo](../aifei-sms-demo/README.md)（端口 18180）
