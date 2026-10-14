# aifei-websocket 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档的分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者。

## 开发前提

- **改代码前必读 `docs/design.md`**。需求与现有设计冲突时，先明确并更新设计决策，再改代码。
- 未经设计决策，不得自行确定契约类行为（空值语义、异常类型与消息、连接状态迁移、退避算法、配置键名、回调语义等），也不得新增或扩大公共 API 面。
- 本模块同时遵循仓库根 `AGENTS.md` 的共同底线，冲突时以更严格者为准；仓库根的协作记忆目录下维护当日日志（`YYYY-MM-DD.md`，追加式），其属项目数据，不得删除。

## 模块定位与两个子能力

`aifei-websocket` 为 aifei 应用补齐框架未覆盖的 WebSocket 基础能力，含两个**独立装配、独立生命周期**的子能力：

- **client（客户端能力）**：应用主动出站连接第三方 WS 服务，含心跳保活与自动重连。
- **server（服务端能力）**：Aifei 应用接收 WS 连接，管理会话与广播。

两者**不共用一个接口**：客户端是「主动发起并维持出站连接」，服务端是「被动接收并管理会话」，语义不同。

## 硬性约束

### 技术基线

- 代码、依赖与 API 必须兼容 **Java 8**：**语法**与 **API 双约束**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching 等 Java 8 之后语法；禁 `List.of` / `Map.of` 等 Java 9+ 集合工厂与其它 Java 8 之后 API。
- 主 JAR 内全部 class 的 major version 必须为 **52**。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名、协议字面量与异常消息）；文件头版权块属归属信息，不得改写。
- **`cn.aifei:aifei-all` 为 `provided`**：消费方自带，不向下游传递。
- **`org.java-websocket:Java-WebSocket` 为 `optional`**：不向下游传递。模块实现包使用它，消费方必须自带该依赖（及 `slf4j-api` + 一个日志绑定）。

### 公共面收敛（不得放宽）

- **公共 API 零第三方类型、零框架类型**。
- 根包公共类型固定为 14 个：`WebSocketClient` / `ClientListener` / `WebSocketServer` / `ServerListener` / `WebSocketSession` / `ConnectionState` / `Message` / `WebSocketConfig` / `ClientConfig` / `ServerConfig` / `WebSocketException` / `WebSocketConnectionException` / `WebSocketCodecException` / `WebSocketKit`；`spi` 包 3 个：`MessageCodec` / `WebSocketProvider` / `AbstractWebSocketProvider`。新增公共类型必须先过设计决策。
- 异常消息中**不得**包含任何第三方类型名；底层库异常一律包装为本模块异常类型后再上浮或交给回调。

## 包边界与门禁

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| `org.java_websocket.*` | 仅 `cn.hg.aifei.websocket.javawebsocket` | `ArchitectureBoundaryTest#thirdPartyOnlyInImplPackage` |
| `cn.aifei.*`（除 `cn.hg.aifei.websocket.*`，框架类型） | 仅 `cn.hg.aifei.websocket.aifei` | `ArchitectureBoundaryTest#frameworkOnlyInBridgePackage` |
| `io.undertow.*` / `java.sql.*` | **全模块禁止** | `ArchitectureBoundaryTest#noUndertowOrSqlReference` |

另有门禁 `ArchitectureBoundaryTest#aifeiBridgePackageExists`：断言接入包非空，且 `WebSocketClientPlugin.java` / `WebSocketServerPlugin.java` 存在。

`ArchitectureBoundaryTest` 扫描 `src/main/java` 下全部 `.java` 断言上述边界，因此**必须在模块根目录执行**。**不得为兼容错误用法而放宽或绕过门禁断言。**

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 状态迁移 | `internal.AbstractWebSocketClient` / `internal.AbstractWebSocketServer` 骨架 |
| 指数退避 + 抖动 | `internal.Backoff`（纯函数，可独立单测） |
| 参数与配置校验 | `internal.WebSocketValidator` |
| 回调保护（异常转投 `onError`） | `internal.ListenerGuard` |
| 会话标识生成 | `internal.SessionIds` |
| 业务对象 → `Message` 编码入口 | `internal.MessageCodecs` |
| `ReadyState` ↔ `ConnectionState` 映射、`ByteBuffer` → `byte[]` 拷贝、地址格式化、关闭码取值（`NORMAL_CLOSURE`） | `javawebsocket.JavaWebSocketSupport` |
| 配置键名与解析（`Prop` → 配置对象） | `aifei.WebSocketConfigLoader`（包级私有） |
| 后端注册表（`type` → 无状态 provider） | `internal.WebSocketProviderRegistry` |

## 新增传输后端的步骤

1. 在 `cn.hg.aifei.websocket.<newbackend>` 包内新增 `XxxProvider extends AbstractWebSocketProvider`，实现 `type()` / `buildClient` / `buildServer`，并在此包内实现客户端 / 服务端 / 会话（第三方类型**只允许**出现在本实现包）。
2. 在 `WebSocketProviderRegistry` 的 `static` 初始化块里**增加一行** `PROVIDERS.put("<newbackend>", new XxxProvider());`。
3. 消费方把配置 `websocket.{client|server}.type` 改为 `<newbackend>`，并自带该后端所需的第三方依赖。

**不得**回头修改 `WebSocketKit`、两个 `Plugin` 或注册表之外的任何装配逻辑；`type` 的允许取值由 `WebSocketProviderRegistry#types()` 派生，不得硬编码到别处。provider 必须**无状态**（配置在 `buildXxx` 内经构造参数现读现用，不得存进实例字段）。

## 行为契约要点速查

- **状态机**：`CLOSED →（connect）CONNECTING →（握手成功）OPEN`；`OPEN →（close）CLOSING → CLOSED`；`OPEN →（对端关闭 / 传输错误 / 心跳超时）CLOSED`。状态变更必须原子。`CONNECTING` / `OPEN` / `CLOSING` 期间 `connect()` 抛 `IllegalStateException`；`CLOSED` 期间 `close()` 为无操作。
- **心跳**：RFC6455 ping/pong 控制帧，由传输后端按 `heartbeatSeconds` 周期收发；`0` = 禁用（默认）。ping/pong **不上浮**为 `Message`、不产生回调。`1.5 × heartbeatSeconds` 内无 pong 即判连接丢失并关闭。
- **自动重连**（仅客户端）：指数退避 + 抖动；重连成功复位计数；连续失败达 `maxRetries` 进入终态并触发一次 `onReconnectExhausted`，此后须再次显式 `connect()`。**「用户主动 `close()` 永不触发重连」是硬约束**：`close()` 置标记并取消待定重连任务，标记在下次显式 `connect()` 时清除。重连执行不经传输回调线程，一律经客户端自有调度线程发起。
- **发送不排队**：`send*` / `broadcast*` 仅在 `OPEN` 时成功，否则抛 `IllegalStateException` 且消息被丢弃；本模块**不提供**自有发送队列与背压，缓冲由调用方自行处理。
- **`onClose` 恰好一次**：无论用户主动还是异常、无论是否重连（含初始握手失败），所有连接尝试的终结都收敛到同一条关闭路径，`onClose` 恰好触发一次；无可用关闭帧时 `code = -1`。
- **回调线程与重入禁忌**：客户端回调在底层库读 / 连接线程上执行，服务端回调在底层库工作线程上执行，重连调度在客户端自有调度线程上执行。回调内可安全 `send` / `close`；**禁止**在客户端回调内 `connect()`、**禁止**在服务端回调内 `server.stop()`；回调内不得做耗时阻塞操作。监听器异常由 `ListenerGuard` 捕获并转投 `onError`，不中断传输线程。
- **服务端会话**：`sessions()` 返回不可变快照；`broadcast*` 对已关闭 / 正在关闭会话静默跳过；`onClose` 先触发、随后注销会话；空闲回收按 `sessionIdleTimeoutSeconds`（`0` = 禁用）扫描，**pong 计入活跃**，最坏回收延迟约 `2 × 阈值`。

## 验证

**命令一律在模块目录内执行**（门禁测试依赖相对路径 `src/main/java`）：

```bash
cd aifei-websocket && mvn clean verify   # 必须 0 failure / 0 error
mvn test -Dtest=BackoffTest#bounds       # 单用例：类名#方法名
```

当前基线：**72 个用例，0 failure / 0 error**。公共接口 + 多实现的契约测试以 `ClientContractTest` / `ServerContractTest` 基类承载，各后端继承运行；已修复的缺陷必须固化为**回归测试**，测试须独立且幂等。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置）。
- 术语必须与 `docs/design.md` 统一（客户端 / 服务端、子能力名、传输后端名、会话、心跳、探活超时、自动重连、空闲会话等），不得另起一套叫法。
- 每类规则仍只有唯一定义点；实现类不得复制规则；多实现契约一致。
- 注释规范：公共类型与公共成员必须书写 javadoc 并写全 `@param` / `@return` / `@throws`；只在代码无法表达「为什么」时书写注释，不复述下一行代码，不写行尾注释，不留被注释掉的死代码。
