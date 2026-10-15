# aifei-websocket 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

四份文档的分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者（测试结构、运行与新增约定）。

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
- **零第三方运行时依赖**：传输层为纯 JDK 自实现（`java.net.Socket` / `javax.net.ssl.SSLSocket` 自实现 RFC6455）。**不得引入任何第三方传输库**（含已删除的旧版 `org.java-websocket:Java-WebSocket`），消费方无需自带传输库与日志绑定。

### 公共面收敛（不得放宽）

- **公共 API 零第三方类型、零框架类型**。
- 公共类型固定为 **15 个，全部在根包**：`WebSocketClient` / `ClientListener` / `WebSocketServer` / `ServerListener` / `WebSocketSession` / `ConnectionState` / `Message` / `WebSocketConfig` / `ClientConfig` / `ServerConfig` / `MessageCodec` / `WebSocketException` / `WebSocketConnectionException` / `WebSocketCodecException` / `WebSocketKit`。**不再有 `spi` 包**（`MessageCodec` 已由 `spi` 包移入根包），**不再有传输后端 SPI**（`WebSocketProvider` / `AbstractWebSocketProvider` 已删除）。新增公共类型必须先过设计决策。
- `internal` 与 `internal.jdk` 均属**非承诺 API**：其中实现类因装配需要可声明为 `public`，但**明确不是公共 API**，消费方不得引用。
- 异常消息中**不得**包含任何第三方类型名；底层异常（`IOException` / `SSLException` / 协议错误）一律包装为本模块异常类型后再上浮或交给回调。

## 包边界与门禁

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| `org.java_websocket.*` | **全模块禁止**（旧版 Java-WebSocket 后端已删除） | `ArchitectureBoundaryTest#noJavaWebsocketReference` |
| `cn.aifei.*`（除 `cn.hg.aifei.websocket.*`，框架类型） | 仅 `cn.hg.aifei.websocket.aifei` | `ArchitectureBoundaryTest#frameworkOnlyInBridgePackage` |
| `java.sql.*` | **全模块禁止** | `ArchitectureBoundaryTest#noSqlReference` |

另有门禁 `ArchitectureBoundaryTest#aifeiBridgePackageExists`：断言接入包非空，且 `WebSocketClientPlugin.java` / `WebSocketServerPlugin.java` 存在；同组另有公共面断言 `publicSurfaceExact`：根包公共类型**精确 15 个**、**不存在 `spi` 包**。

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
| 后端标识（`"jdk"`） | `internal.WebSocketBackend` |
| HTTP Upgrade 握手（`Sec-WebSocket-Accept` 计算） | `internal.jdk.Rfc6455Handshake` |
| RFC6455 帧编解码（掩码 / 长度 / opcode / 控制帧 / 分片聚合，上限 16 MiB → 关闭码 `1009`） | `internal.jdk.Rfc6455FrameCodec` |
| Socket 创建与连接超时、`wss` 主机名校验、地址格式化、安全关闭 | `internal.jdk.JdkSocketSupport` |
| 配置键名与解析（`Prop` → 配置对象） | `aifei.WebSocketConfigLoader`（包级私有） |

> 传输实现**唯一且内建**（`internal.jdk`，纯 JDK 自实现 RFC6455）：**无 provider 扩展点、无注册表、无 `type` 配置**。`WebSocketKit` 与两个 `Plugin` 直接 `new` JDK 实现。旧版 `javawebsocket.JavaWebSocketSupport`（状态映射 / `ByteBuffer` 拷贝 / 地址格式化 / 关闭码取值）已随该包删除，其职责由 `internal.jdk` 各类型承接。

## 行为契约要点速查

- **状态机**：`CLOSED →（connect）CONNECTING →（握手成功）OPEN`；`OPEN →（close）CLOSING → CLOSED`；`OPEN →（对端关闭 / 传输错误 / 心跳超时）CLOSED`。状态变更必须原子。`CONNECTING` / `OPEN` / `CLOSING` 期间 `connect()` 抛 `IllegalStateException`；`CLOSED` 期间 `close()` 为无操作。
- **心跳**：RFC6455 ping/pong 控制帧，由传输层（`internal.jdk`）按 `heartbeatSeconds` 周期收发；`0` = 禁用（默认）。ping/pong **不上浮**为 `Message`、不产生回调；收到 ping 自动回 pong。`1.5 × heartbeatSeconds` 内无 pong 即判连接丢失并关闭。
- **自动重连**（仅客户端）：指数退避 + 抖动；重连成功复位计数；连续失败达 `maxRetries` 进入终态并触发一次 `onReconnectExhausted`，此后须再次显式 `connect()`。**「用户主动 `close()` 永不触发重连」是硬约束**：`close()` 置标记并取消待定重连任务，标记在下次显式 `connect()` 时清除。重连执行不经传输回调线程，一律经客户端自有调度线程发起。
- **发送不排队**：`send*` / `broadcast*` 仅在 `OPEN` 时成功，否则抛 `IllegalStateException` 且消息被丢弃；本模块**不提供**自有发送队列与背压，缓冲由调用方自行处理。
- **`onClose` 恰好一次**：无论用户主动还是异常、无论是否重连（含初始握手失败），所有连接尝试的终结都收敛到同一条关闭路径，`onClose` 恰好触发一次；无可用关闭帧时 `code = -1`。
- **回调线程与重入禁忌**：客户端回调在传输实现的读线程上执行，服务端回调在连接各自的读线程 / 接受线程上执行，重连调度在客户端自有调度线程上执行。回调内可安全 `send` / `close`；**禁止**在客户端回调内 `connect()`、**禁止**在服务端回调内 `server.stop()`；回调内不得做耗时阻塞操作。监听器异常由 `ListenerGuard` 捕获并转投 `onError`，不中断传输线程。
- **服务端会话**：`sessions()` 返回不可变快照；`broadcast*` 对已关闭 / 正在关闭会话静默跳过；`onClose` 先触发、随后注销会话；空闲回收按 `sessionIdleTimeoutSeconds`（`0` = 禁用）扫描，**pong 计入活跃**，最坏回收延迟约 `2 × 阈值`。
- **传输层要点**（详见 `docs/design.md` §5）：客户端发帧必须掩码、服务端发帧不掩码；完整支持 7 / 16 / 64 位长度与 `continuation` 分片（含控制帧穿插）；分片聚合上限固定 **16 MiB**，超限以关闭码 **1009** 关闭（**不新增公共配置项**）；错误映射 `1002` / `1007` / `1009`，无可用关闭帧时 `onClose` 的 `code = -1`；客户端支持 `ws` / `wss`（`wss` 必须启用主机名校验），**服务端本轮仅支持 `ws`**；不实现 `permessage-deflate` 与 `Sec-WebSocket-Protocol` 子协议协商。

## 验证

**命令一律在模块目录内执行**（门禁测试依赖相对路径 `src/main/java`）：

```bash
cd aifei-websocket && mvn clean verify   # 必须 0 failure / 0 error
mvn test -Dtest=BackoffTest#shouldRespectJitterBounds   # 单用例：类名#方法名
```

当前基线：**以 `mvn clean verify` 的实测结果为准（实现完成后由 QA 回填用例数）**，要求 0 failure / 0 error。公共接口的契约测试以 `ClientContractTest` / `ServerContractTest` 基类承载，由唯一实现（`internal.jdk`）继承运行；已修复的缺陷必须固化为**回归测试**，测试须独立且幂等。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置）。
- 术语必须与 `docs/design.md` 统一（客户端 / 服务端、子能力名、**后端标识**（固定 `jdk`，不再是配置项）、会话、心跳、探活超时、自动重连、空闲会话等），不得另起一套叫法。
- 每类规则仍只有唯一定义点；实现类不得复制规则；唯一实现（`internal.jdk`）须满足全部契约条目。
- 注释规范：公共类型与公共成员必须书写 javadoc 并写全 `@param` / `@return` / `@throws`；只在代码无法表达「为什么」时书写注释，不复述下一行代码，不写行尾注释，不留被注释掉的死代码。
