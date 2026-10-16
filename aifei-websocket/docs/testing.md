# aifei-websocket 测试文档

本文件说明 `aifei-websocket` 的测试分层、运行方式、契约测试机制、当前基线与新增测试约定。

四份文档的分工：`README.md` 面向使用者；`docs/design.md` 是契约与设计决策的事实来源；`AGENTS.md` 面向开发代理；**本文件面向测试维护者**（测试结构、运行与新增约定）。

## 1. 运行方式

**所有命令必须在模块根目录内执行**——架构门禁 `ArchitectureBoundaryTest` 以相对路径 `src/main/java` 扫描源码，在其上层目录执行会因找不到源码目录而失败。

```cmd
cd aifei-websocket
mvn clean verify                      :: 全量测试，必须 0 failure / 0 error
mvn test -Dtest=BackoffTest#shouldRespectJitterBounds   :: 单个用例，格式 类名#方法名
mvn test -Dtest=Rfc6455FrameCodecTest :: 单个测试类
```

测试**不联网、不连数据库、不依赖外部服务**：回环与契约用例在进程内自建服务端与客户端，协议用例为纯函数断言。

## 2. 测试分层与文件清单

| 层次 | 测试类 | 用例数 | 职责 |
| --- | --- | --- | --- |
| 契约（客户端） | `ClientContractTest`（抽象基类）→ `internal/jdk/JdkWebSocketClientTest` | 13 | `WebSocketClient` / `ClientListener` 公共行为只断言一次，实现类继承运行 |
| 契约（服务端） | `ServerContractTest`（抽象基类）→ `internal/jdk/JdkWebSocketServerTest` | 9 | `WebSocketServer` / `ServerListener` 公共行为只断言一次，实现类继承运行 |
| 端到端 | `internal/jdk/LoopbackTest` | 9 | 进程内回环：建连、双向文本 / 二进制、广播、关闭、断线自动重连、服务端停止下 1001 |
| 协议 | `internal/jdk/Rfc6455HandshakeTest` | 14 | HTTP Upgrade 握手：`Sec-WebSocket-Accept` 计算、非法版本 / Key / 响应、附加请求头 |
| 协议 | `internal/jdk/Rfc6455FrameCodecTest` | 20 | 帧编解码：掩码、125 / 126 / 127 长度边界、部分读取、控制帧、非法操作码、关闭载荷 |
| 传输 | `internal/jdk/JdkWebSocketConnectionTest` | 7 | 分片聚合、控制帧穿插、自动 pong、`1.5 ×` 心跳超时、关闭竞态与资源释放 |
| 公共类型 | `MessageTest` | 9 | `Message` 文本 / 二进制语义与大小 |
| 公共类型 | `ConnectionStateTest` | 10 | 连接状态迁移与非法迁移断言 |
| 内部 | `BackoffTest` | 7 | 指数退避 + 抖动（纯函数） |
| 接入层 | `aifei/WebSocketConfigLoaderTest` | 13 | `Prop` → `ClientConfig` / `ServerConfig`，含必填键缺失与语法非法 |
| 架构门禁 | `ArchitectureBoundaryTest` | 5 | 包边界与公共面大小（见 §5） |

**合计：11 个可运行测试类 + 2 个抽象契约基类，115 个用例。**

## 3. 契约测试机制

公共接口的行为**只在契约基类断言一次**，具体实现通过继承复用：

- `ClientContractTest` / `ServerContractTest` 为 `abstract class`，声明 3 个由子类实现的工厂：`newClient(...)`、`newServer(...)`、`backendType()`。
- 契约用例需要**真实对端**（覆盖「OPEN 后行为 / 断线重连 / `onClose` 恰好一次」等），而同一后端天然同时提供 client 与 server，故两端工厂都交给子类。
- 契约基类**只使用公共 API 与 JDK 类型**，不引用任何内部类型或第三方类型（保证契约不绑定实现）。
- 当前仅 JDK 内建实现继承运行；若将来新增传输实现，只需为该实现新建继承同一基类的测试类，契约用例自动覆盖。

## 4. 协议与传输单测的覆盖重点

- **握手**：`Sec-WebSocket-Accept` 按 RFC6455 示例向量校验；请求 / 响应的大小写与 Token 处理、非法版本与非法 Key、非 101 响应。
- **帧编解码**：客户端发帧必须掩码、服务端发帧不得掩码；7 / 16 / 64 位长度字段与网络字节序；控制帧必须 `FIN=1` 且载荷 ≤ 125 字节；非法操作码、保留位非零、分片控制帧等协议错误。
- **连接行为**：continuation 分片聚合与控制帧穿插；收到 ping 自动回 pong 且 ping / pong **不上浮**为 `Message`、不产生用户回调；`1.5 × heartbeatSeconds` 未收到 pong 判连接丢失；并发读、关闭与异常竞态下 `onClose` 恰好一次、资源正确释放。

## 5. 架构门禁

`ArchitectureBoundaryTest` 以源码文本扫描 `src/main/java` 下全部 `.java`，断言 5 条，**不得为兼容错误用法而放宽**：

| # | 断言 | 内容 |
| --- | --- | --- |
| ① | `noJavaWebsocketReference` | 全模块禁止 `org.java_websocket`（旧版 Java-WebSocket 后端已删除，无任何包可豁免） |
| ② | `frameworkOnlyInBridgePackage` | `import cn.aifei.*`（除 `cn.hg.aifei.websocket.*`）只允许出现在 `cn.hg.aifei.websocket.aifei` 接入包 |
| ③ | `noSqlReference` | 全模块禁止 `import java.sql.` |
| ④ | `publicSurfaceExact` | 根包直接子文件**精确 15 个**，且不存在 `spi` 包 |
| ⑤ | `aifeiBridgePackageExists` | 接入包非空，且 `WebSocketClientPlugin.java` / `WebSocketServerPlugin.java` 存在 |

## 6. 当前基线

| 项 | 值 |
| --- | --- |
| 可运行测试类 | 11（另 2 个抽象契约基类） |
| 用例总数 | **116** |
| 结果 | `Tests run: 115, Failures: 0, Errors: 0, Skipped: 0` |
| 编译目标 | Java 8（class major version = 52） |

> 基线随功能演进更新；提交前 `mvn clean verify` 必须 0 failure / 0 error，且用例数不得减少。

## 7. 新增测试约定

1. **修复固化**：已修复的缺陷必须固化为**回归测试**，防止再次回归。
2. **独立且幂等**：用例之间不得有顺序依赖，可重复运行、结果稳定；不依赖外部服务与网络。
3. **契约一致**：涉及公共接口行为的用例写入契约基类（而非某个实现类），保证公共接口行为只被断言一次；实现特有的协议细节写入该实现的测试类。
4. **规则唯一定义点**：测试不得复制被测的校验规则（如关闭码取值、长度边界），应引用被测类的常量或直接断言其契约。
5. **门禁不放宽**：新增公共类型须同步更新 `publicSurfaceExact` 的期望值并说明设计依据，**不得**为绕过门禁而改写断言。
