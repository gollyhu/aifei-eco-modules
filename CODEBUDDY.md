# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## 仓库概况

aifei（https://aifei.cn）框架的生态模块仓库，各模块坐标均为 `io.github.gollyhu:<模块>:1.0.0`，编译目标 Java 8。**没有根聚合 pom**：7 个模块各自独立构建、独立发布，仓库根不执行 `mvn`。其中 2 个 `*-demo` 为示例工程，不发布。所有模块依赖仓库外的 aifei 框架（`cn.aifei:aifei` / `cn.aifei:aifei-all:1.1.1`），本仓库不含该框架源码。

| 模块 | 定位 |
| --- | --- |
| `aifei-cache` | 多实例缓存与计数组件，Caffeine / Jedis 双后端 |
| `aifei-sentinel` | Sentinel 流控/熔断/系统保护的 aifei 接入 SDK |
| `aifei-weixin` | 微信开发包：公众号协议内核 + 小程序 + 微信支付 V3 + aifei 接入层 |
| `aifei-sentinel-demo` | 自写 HIO 的最小接入示例（验证 sentinel 对接，不发布） |
| `aifei-sa-token` | Sa-Token 认证鉴权接入 SDK：登录态、角色权限、注解鉴权 |
| `aifei-websocket` | WebSocket 双向能力：客户端出站连接 + 服务端接收连接，Java-WebSocket 后端可替换 |
| `aifei-websocket-demo` | 自写 HIO 的回环演示示例（不发布） |

## 常用命令

以下命令均在各模块目录执行（仓库根无聚合 pom，不能一次构建全部）：

```bat
cd aifei-cache && mvn clean verify    # aifei-cache 的强制验证：必须 0 failure / 0 error
mvn test -Dtest=CaffeineCacheTest#putThenGet   # 跑单个测试，格式 类名#方法名
cd aifei-weixin && mvn clean test     # 109 个用例，不联网、不连数据库
cd aifei-sentinel && mvn test         # 含架构门禁测试（KernelBindingTest）
cd aifei-sa-token && mvn clean test   # 含架构门禁测试（KernelBindingTest）
cd aifei-websocket && mvn clean verify   # 含架构门禁测试（ArchitectureBoundaryTest），须在模块根目录内执行
mvn clean install                     # 装入本地仓库，供依赖它的应用模块使用
```

Redis 集成测试（仅 aifei-cache，默认关闭；开启后连不上 Redis 测试必须失败）：

```bat
mvn verify -Dredis.integration=true -Dredis.uri=redis://127.0.0.1:6379/0
```

发布（仅 aifei-cache 配置了 Maven Central 发布）：

```bat
mvn -Pcentral-release clean deploy    # GPG 签名 + Central Portal bundle 提交（autoPublish=false，需 settings.xml 有 server id: aifei）
```

demo 运行（`aifei-sentinel-demo` 目录，Windows 批处理）：

```bat
start-demo.bat            # 本地模式启动 demo，端口 18080，验证地址 http://localhost:18080/demo
start-dashboard.bat       # 启动本地 Sentinel 控制台，端口 8858（账号 sentinel/sentinel），需自备 sentinel-dashboard jar
start-demo-dashboard.bat  # Dashboard 模式启动 demo（控制台未起时会自动拉起并等待就绪）
stop-demo.bat             # 按端口 18080 找到监听进程并结束
```

demo 运行（`aifei-websocket-demo` 目录，Windows 批处理）：

```bat
start-demo.bat            # 启动回环 demo（HTTP 18090 / WS 18091），验证地址 http://localhost:18090/ws/demo/state
stop-demo.bat             # 按端口 18090 找到监听进程并结束
```

## 架构

### aifei 框架集成方式（跨模块共性）

- 装配入口：实现 `cn.aifei.plugin.Plugin`，在 `config(Plugins plugins)` 中注册；配置解析与建连发生在 `start()`，构造期只持有 `Prop` 引用、不解析不校验。配置错误表现为应用启动失败，不留半装配状态。
- 横切拦截：实现 `cn.aifei.aop.Interceptor`，经 `routes.scan("包名", interceptor...)` 注册；降级返回值经 `inv.setReturnValue` 走消费方 IoHandler 正常渲染，因此 HTTP 状态码恒 200，限流等语义由业务码承载。
- 消息/请求入口：实现 `Handler`，注册到 `settings.addHandler(...)`；注册顺序敏感（如微信首验 GET 必须早于 FrontHandler）。
- 配置：`PropKit.use("app-config.txt")` 载入 `app-config-{env}.txt`；单例注入用 `@Inject`（底层 `AopKit` 按类型注册，无按名通道）。

### aifei-cache：多实例 + 可插拔后端

三层名字各管一件事：**实例名**（连哪个存储）/ **缓存名**（业务分区）/ **命名空间**（同一存储上区分应用）。主缓存实例名恒为 `__MAIN__`（配置前缀 `cache.*`），是唯一可 `@Inject` 的实例；扩展实例由 `cache.names` 声明（前缀 `cache.<name>.*`），只能用 `CacheUtil.use("name").xxx()` 操作。换后端只改 `cache.type`，不改代码。

包结构：

- 根包 6 个公共类型：`Cache` / `Counter` / `CacheUtil` / `CounterUtil` / `CachePlugin` / `CacheKeyMatchType`。新增公共类型必须先过设计决策。
- `internal`（非承诺 API）：`Config`（配置域与框架常量、实例名解析）；`CacheValidator`（接口参数与命名空间校验的唯一定义点）；`AbstractCache` / `AbstractCounter`（物理 key 拼装与空值哨兵编解码）；`SingleFlight`（回源单飞的登记与「回源 + 回写」）；`KeyRanges`（`keys` 区间换算，语义对齐 Redis `LRANGE`）；`CacheProvider` + `CacheProviderRegistry` + `AbstractCacheProvider`（后端扩展点，新增后端 = 新增 provider 实现 + 注册表静态初始化里注册一行）；`NullValue`（空值哨兵，其包名与枚举名属冻结数据格式）。
- `caffeine` / `redis`：两个后端实现，必须遵循相同接口契约。Redis 后端值默认 Fury 编解码（`FuryRedisValueCodec`，反序列化白名单唯一定义点；`requireClassRegistration(false)` 不得改为 true，会改变写入字节格式、破坏存量数据）。

### aifei-sentinel：业务代码零 Sentinel 类型

- 公共面三个类型：`SentinelKit`（唯一装配入口，静态 `loadRules(Prop)` / `shutdown()`）；`SentinelInterceptor`（唯一 import `cn.aifei.aop.*` 的类，`entry`/`entry.exit` 成对、`exit` 在 `finally`）；`SentinelBlockHandler`（`spi` 包函数式接口，降级接线）。业务只注册拦截器、实现降级策略。
- 两种互斥运行模式，由是否配置 `sentinel.dashboard.server` 决定：**本地模式**规则文件是唯一来源，物理文件走 `FileRefreshableDataSource` 秒级热读，classpath 资源一次性加载；**Dashboard 模式**先注入系统属性 `csp.sentinel.dashboard.server` / `project.name`，一次性加载初始规则，控制台推送规则并**合并回写**本地文件，且不启热读（否则推送/回写/热读互相触发形成回环）。transport 依赖（`sentinel-transport-simple-http`）仅 Dashboard 模式需要、由消费方提供，SDK 只用 `Class.forName` 反射探测。
- 默认资源名为「全限定类名#方法名」，与规则 JSON 中 `resource` 须严格一致；规则 JSON 单文件分 key：`flowRules` / `degradeRules` / `systemRules` / `paramFlowRules` / `authorityRules`。

### aifei-weixin：分层内核 + 接入隔离

- 协议内核（根包 `cn.aifei.weixin`）：AES-256-CBC 加解密、SHA-1 签名、安全 XML 解析（禁 DOCTYPE / 外部实体）、消息模型与解析/渲染；`MsgKit` 是传输无关双入口（`verify` 首验 / `handle` 消息），`MsgHandler` 模板方法提供 17 个钩子供业务扩展。
- 客户端层：`pay`（微信支付 V3，RSA / GCM / mTLS 纯 JDK 实现，`PayApi` + `PayNotifyKit` 回调验签解密 + `PlatformCertManager` 平台证书惰性刷新）、`miniapp`（小程序，复用公众号 token 链路）、`common`（Api 结果与异常）。
- 接入层 `cn.aifei.weixin.aifei`：`WeixinHandler` 是唯一 import aifei 内核的类，传输经 `WeixinIo` SPI 隔离。回调地址 `/weixin/{appId}/msg`。
- 关键契约：无被动回复必须回 `success`；回调链路禁止同步调用 Api（微信 5 秒超时）；SDK 不做幂等去重，业务自行用 `MsgId` 幂等。

### aifei-sentinel-demo

自写 HIO（undertow）最小示例，`DemoApp` → `Aifei.start(DemoAppConfig)`，用于验证 sentinel SDK 对接。规则文件工作副本 `sentinel-rules.json`（首次启动从 `src/main/resources` 复制，本地模式改它秒级热生效）；`app-config.txt` / `app-config-dashboard.txt` 分别对应本地与 Dashboard 两种模式。

### aifei-websocket

`aifei-websocket`（坐标 `io.github.gollyhu:aifei-websocket:1.0.0`）补齐 aifei 未覆盖的 WebSocket 能力，含两个**独立装配、独立生命周期**的子能力：**client**（主动出站连第三方服务，可选心跳保活与自动重连）与 **server**（Aifei 应用接收 WS 连接、管理会话与广播），两者不共用接口。

- **两个装配入口与构造器**：`WebSocketClientPlugin` / `WebSocketServerPlugin` 各含 2 参 `(Prop, Listener)` 与 3 参 `(Prop, Listener, MessageCodec)` 构造器；构造期只持引用，`start()` 解析 `websocket.{client|server}.*` 配置并注册 `@Inject WebSocketClient` / `WebSocketServer` 单例（`websocket.client.uri` 与 `websocket.server.port` 为接入层必填键，缺失即启动失败）。监听器必传、编解码器可选。
- **包边界与门禁**：`org.java_websocket.*` 只允许出现在 `javawebsocket` 实现包；`cn.aifei.*`（框架类型，除 `cn.aifei.websocket.*`）只允许出现在 `aifei` 接入包；`io.undertow.*` / `java.sql.*` 全模块禁止。由源码文本扫描门禁 `ArchitectureBoundaryTest` 断言 4 条（第三方只在实现包、框架只在接入包、禁 Undertow/SQL、接入包非空且两个 Plugin 存在），故必须在模块根目录执行。
- **公共面收敛纪律**：公共 API 零第三方类型、零框架类型；根包固定 14 个公共类型（`WebSocketClient` / `ClientListener` / `WebSocketServer` / `ServerListener` / `WebSocketSession` / `ConnectionState` / `Message` / `WebSocketConfig` / `ClientConfig` / `ServerConfig` / `WebSocketException` / `WebSocketConnectionException` / `WebSocketCodecException` / `WebSocketKit`），`spi` 包 3 个（`MessageCodec` / `WebSocketProvider` / `AbstractWebSocketProvider`），新增公共类型须先过设计决策。
- **provider 扩展点**：新增后端 = 在 `cn.aifei.websocket.<backend>` 包新增 `XxxProvider extends AbstractWebSocketProvider`，并在 `WebSocketProviderRegistry` 的静态初始化块加一行 `PROVIDERS.put(...)`；provider 必须无状态，`type` 取值由注册表派生。默认后端 `javawebsocket`（`org.java-websocket:Java-WebSocket` 为 `<optional>`，消费方自带）。
- **两个必须知道的契约**：① **发送不排队**——`send*` / `broadcast*` 仅在 `OPEN` 时成功，否则抛 `IllegalStateException` 且消息被丢弃，本模块不提供自有发送队列与背压；② **用户主动 `close()` 永不触发自动重连**——`close()` 置标记并取消待定重连，标记在下次显式 `connect()` 时清除。
- **保活与自动重连默认关闭**：`heartbeatSeconds` 默认 `0`（禁用，ping/pong 不上浮为 `Message`、不产生回调）、`autoReconnect` 默认 `false`，须显式开启。

### aifei-websocket-demo

自写 HIO（`common/arch` 包，应用侧代码）的本地回环演示工程（**示例工程，不发布**）：同一进程内装配一个 WebSocket 服务端与一个客户端，客户端连服务端，演示 **建连 → 收发 → 心跳 → 断线自动重连**。结构对齐 `aifei-sentinel-demo`：`DemoApp` → `Aifei.start(DemoAppConfig)` + 一个 Just Service（`service` 包），两个 WS 实例经装配入口注册、Service 内 `@Inject WebSocketClient` / `@Inject WebSocketServer` 取用。端口：HTTP **18090**（`start-demo.bat` 的 `-Dundertow.port=18090`）/ WebSocket 服务端 **18091**（`app-config.txt` 的 `websocket.server.port`），客户端连 `ws://127.0.0.1:18091/ws`。演示端点（均为 GET，HTTP 恒 200、语义由 `code` 承载）：`/ws/demo/state`、`/ws/demo/log`、`/ws/demo/log/clear`、`/ws/demo/echo/send?text=`（客户端发、服务端回声）、`/ws/demo/broadcast?text=`（服务端广播）、`/ws/demo/server/stop`（触发客户端自动重连）、`/ws/demo/server/start`（重连回 `OPEN`）、`/ws/demo/client/connect`、`/ws/demo/client/disconnect`。运行用 `start-demo.bat` / `stop-demo.bat`。

### aifei-sa-token

`aifei-sa-token`（坐标 `io.github.gollyhu:aifei-sa-token:1.0.0`）是 aifei + Undertow 的 Sa-Token 认证鉴权接入 SDK：

- **上下文接线在 Dispatcher 层**：装饰 `cn.aifei.server.Dispatcher`（`SaTokenUndertowDispatcher`）以取得请求元信息与响应写回能力；不改自有 HIO、不写模型适配类。上下文清理必须在 `finally`（`clearContext` + `clearInput` + `clearExchange`），否则线程复用会串号。
- **装配只改一行 + 注册插件 + 挂拦截器**：`plugins.add(new SaTokenAifeiPlugin(prop))` + `settings.setServer(server, new SaTokenUndertowDispatcher<>(new IoDispatcher()))` + `routes.scan(...)` 挂鉴权拦截器；插件与桥接 Dispatcher 必须同时存在（缺插件则上下文为空实现，请求进入桥接层即报错）。
- **两种鉴权方式（二选一）**：路径鉴权 `SaTokenInterceptor`（Ant 风格 `addInclude` / `addExclude` + 自定义 `SaAuthStrategy`）与注解鉴权 `SaAnnotationInterceptor`；同时注册会让同一方法的注解鉴权执行两次，并用时须 `setAnnotationCheck(false)`。
- **失败语义可切**：默认 `SaTokenUndertowError.defaultHandler()` 未登录置 HTTP 401、其余置 403；可换成返回业务 `Out`（如 `Out.fail(...)`）以保持本仓库既有的「HTTP 恒 200 + 业务码」语义。
- **持久层可替换**：默认 Sa-Token 内置内存实现（单机零依赖）；多实例或需落库时换 `SaTokenDaoForCache`（纯缓存 / 缓存加速 + 权威委托两种形态），`aifei-cache` 为 `<optional>`。分层边界由 `KernelBindingTest` 门禁（`cn.aifei.aop.*` 只在 `satoken.aifei` 包、`io.undertow.*` 只在 `satoken.undertow` 包、`cn.hg.aifei.cache.*` 只在 `satoken.dao` 包，`cn.aifei.vip.*` / `java.sql.*` 零引用）。

## 开发约定

改代码前必须读对应模块的 `AGENTS.md`（各模块文档分工：`README.md` 面向使用者，`docs/design.md` 是契约与设计决策的事实来源，`AGENTS.md` 面向开发代理）：

- `aifei-cache/AGENTS.md`：约束最细——Java 8（class major 52）、Caffeine 不得高于 2.9.3（最后支持 JDK 8 的版本）、Caffeine/Jedis/Fury 保持 `<optional>`、冻结数据格式（哨兵类型、key 前缀、物理 key 格式）不得变更、每类规则只许一个唯一定义点（实现类不得复制校验规则）、provider 必须无状态、公共成员 javadoc 规范与中文注释要求。改公共行为须同步 `docs/design.md` 与 `README.md` 并补测试。
- `aifei-sentinel/AGENTS.md`：`SentinelKit` 门面极简、业务零 Sentinel 类型；`cn.aifei.aop.*` 与 `cn.aifei.core.*` 只允许出现在接入层包；两种运行模式互斥；资源名规则等未经设计决策不得自定。
- `aifei-weixin`：架构门禁由测试自动化断言（`KernelBindingTest`：无 `cn.aifei.vip.*` / `io.undertow.*` / `java.sql.*` 引用，`cn.aifei.core.*` 只出现在接入包）；`aifei-all` 为 provided，消费方必须自带。
- `aifei-websocket/AGENTS.md`：公共 API 零第三方/框架类型（根包 14 个公共类型 + `spi` 包 3 个，新增须过设计决策）；`cn.aifei:aifei-all` 与 `org.java-websocket:Java-WebSocket` 分别为 `provided` / `optional`；第三方类型只在 `javawebsocket` 实现包、框架类型只在 `aifei` 接入包（门禁 `ArchitectureBoundaryTest`，须在模块根目录执行）；每类规则唯一定义点、provider 必须无状态；「用户主动 `close()` 永不触发自动重连」「发送不排队」为硬契约；改公共行为须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置）。
- `aifei-sa-token/AGENTS.md`：分层边界由 `KernelBindingTest` 门禁（`cn.aifei.aop.*` 只在 `satoken.aifei` 包、`io.undertow.*` 只在 `satoken.undertow` 包、`cn.hg.aifei.cache.*` 只在 `satoken.dao` 包，`cn.aifei.vip.*` / `java.sql.*` 零引用）；不得擅自更换上下文挂载点（当前装饰 `Dispatcher`）、上下文清理必须在 `finally`；桥接对象无状态、不得调用 `exchange.endExchange()`；每类规则唯一定义点；`aifei-all` / `io.undertow:undertow-core` 保持 `provided`、`io.github.gollyhu:aifei-cache` 保持 `optional`；改代码前必读 `docs/design.md`。

通用：

- 需求与现有设计冲突时，先更新 `docs/design.md` 中的设计决策，再改代码。
- 工作区文本文件为 CRLF（`core.autocrlf=true`）；PowerShell 下批处理/脚本正文只用 ASCII，`-D` 形式的 Maven 参数要整体加引号，否则会在冒号处被切断。
- 提交前 `mvn clean verify` 必须 0 failure / 0 error；aifei-cache 涉及 Redis 的改动额外加跑 `-Dredis.integration=true`。
