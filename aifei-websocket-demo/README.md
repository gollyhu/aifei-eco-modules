# aifei-websocket-demo

`aifei-websocket` 的本地回环演示工程（**示例工程，不发布**）：在**同一个进程内**装配一个 WebSocket 服务端与
一个 WebSocket 客户端，客户端连接服务端，用来现场演示 **建连 → 收发 → 心跳 → 断线自动重连** 的全过程。

- 结构对齐 `aifei-sentinel-demo`：`DemoApp` → `Aifei.start(DemoAppConfig)`，自写 HIO（`common/arch` 包，
  应用侧代码）+ 一个 Just Service（`service` 包）。
- 两个 WebSocket 实例都由装配入口注册，Service 里用 `@Inject WebSocketClient` / `@Inject WebSocketServer` 取用，
  即 `aifei-websocket` 推荐的标准用法。

## 端口

| 用途 | 端口 | 来源 |
| --- | --- | --- |
| HTTP（演示端点） | **18090** | `start-demo.bat` 的 `-Dundertow.port=18090` |
| WebSocket 服务端（本地回环） | **18091** | `app-config.txt` 的 `websocket.server.port` |

两个端口都与 `aifei-sentinel-demo` 的 18080 错开。客户端地址为 `ws://127.0.0.1:18091/ws`。

## 运行

```bat
start-demo.bat    :: 启动 demo（HTTP 18090 / WS 18091），按 Ctrl+C 或运行 stop-demo.bat 结束
stop-demo.bat     :: 按端口 18090 找到监听进程并结束
```

启动后访问 <http://localhost:18090/ws/demo/state> 看到客户端为 `OPEN` 即为正常。

## 演示端点

所有端点都是 GET，返回 `{"code":0,"msg":...,"data":...}`（HTTP 恒 200，语义由 `code` 承载；`0` 为成功）。

| 端点 | 作用 |
| --- | --- |
| `GET /ws/demo/state` | 现场快照：客户端状态 / 远端地址 / 后端类型、服务端是否启动 / 端口 / 会话清单、事件日志 |
| `GET /ws/demo/log` | 只返回事件日志（最新在前，容量上限 200 条） |
| `GET /ws/demo/log/clear` | 清空事件日志 |
| `GET /ws/demo/echo/send?text=hello` | 通过**客户端** `send(text)` 发出；服务端监听器**回声**回同一会话；客户端监听器记录收到的回声 |
| `GET /ws/demo/broadcast?text=notice` | 通过**服务端** `broadcast(text)` 广播给全部会话；客户端监听器记录收到的广播 |
| `GET /ws/demo/server/stop` | 停掉服务端（`server.stop()`），客户端触发 `onClose` 并按指数退避自动重连 |
| `GET /ws/demo/server/start` | 重新启动服务端（`server.start()`），客户端重连成功回到 `OPEN` |
| `GET /ws/demo/client/connect` | 手动建连（仅 `CLOSED` 时可用） |
| `GET /ws/demo/client/disconnect` | 手动关闭客户端（`close()` 之后不再自动重连，直到再次显式 `connect()`） |

事件日志里的类型包括：`SERVER_ON_START` / `SERVER_ON_OPEN` / `SERVER_ON_MESSAGE` / `SERVER_ECHO` /
`SERVER_ON_CLOSE` / `SERVER_ON_STOP`、`CLIENT_ON_OPEN` / `CLIENT_ON_MESSAGE` / `CLIENT_ON_CLOSE` /
`CLIENT_ON_ERROR` / `CLIENT_ON_RECONNECT` / `CLIENT_ON_RECONNECT_EXHAUSTED`，以及演示端点自身打点的
`DEMO_CLIENT_SEND` / `DEMO_SERVER_BROADCAST`。

## 现场演示脚本（约 30 秒）

1. `GET /ws/demo/state` → `clientState = OPEN`，`serverSessionCount = 1`。
2. `GET /ws/demo/echo/send?text=hello` → 日志出现 `DEMO_CLIENT_SEND` → `SERVER_ON_MESSAGE` → `SERVER_ECHO`
   → `CLIENT_ON_MESSAGE 收到文本: hello`（客户端 → 服务端 → 客户端 闭环）。
3. `GET /ws/demo/broadcast?text=notice` → 日志出现 `DEMO_SERVER_BROADCAST` 与 `CLIENT_ON_MESSAGE 收到文本: notice`。
4. `GET /ws/demo/server/stop` → 稍等 1~2 秒查 `/ws/demo/state`：`clientState = CLOSED`、`serverStarted = false`，
   日志出现 `SERVER_ON_CLOSE (code=1001)`、`CLIENT_ON_CLOSE (code=1001, remote=true)`、`CLIENT_ON_RECONNECT`；
   随后每次失败重连都会打 `CLIENT_ON_ERROR` + `CLIENT_ON_CLOSE (code=-1)`。
5. `GET /ws/demo/server/start` → 稍等 1~2 秒查 `/ws/demo/state`：客户端**自动**回到 `OPEN`，日志出现
   `SERVER_ON_START`、`SERVER_ON_OPEN`、`CLIENT_ON_OPEN`（此时 `serverSessionCount` 又是 1）。

## 配置要点（`src/main/resources/app-config.txt`）

| 键 | 值 | 说明 |
| --- | --- | --- |
| `websocket.server.port` | `18091` | 接入层**必填**键，缺失即应用启动失败 |
| `websocket.server.heartbeat-seconds` | `10` | 服务端按 10 秒发 ping 并探测死连接 |
| `websocket.server.session-idle-timeout-seconds` | `0` | 空闲回收关闭，便于反复观察同一会话 |
| `websocket.client.uri` | `ws://127.0.0.1:18091/ws` | 接入层**必填**键 |
| `websocket.client.heartbeat-seconds` | `10` | 客户端同样发 ping（控制帧不上浮为消息） |
| `websocket.client.auto-reconnect` | `true` | **必须显式开启**（默认 `false`），演示断线重连的前提 |
| `websocket.client.max-retries` | `30` | 重连窗口约 30 秒，足够演示期间停掉再拉起服务端 |
| `websocket.client.initial-retry-delay-millis` | `300` | 首次退避 300 毫秒，现场 1 秒内即可看到重连 |
| `websocket.client.max-retry-delay-millis` | `1000` | 单次退避上限 1 秒（退避约 `300/600/1000/1000...` 毫秒） |
| `websocket.client.retry-jitter-ratio` | 未配置 | 沿用默认 `0.2`（抖动），因此每次退避在上下界之间浮动 |
| `websocket.client.header.X-Demo-Client` | `aifei-websocket-demo` | 演示握手附加请求头的前缀键写法 |

## 依赖说明

本工程自带 `org.java-websocket:Java-WebSocket:1.6.0`（`aifei-websocket` 内该依赖为 `optional`，不向下游传递）。
本 demo **未引入 SLF4J 绑定**，因此 Java-WebSocket 的日志走 SLF4J 的 no-op 实现并在启动时打印一次
`SLF4J(W): No SLF4J providers were found.` 警告——这是 `docs/design.md` §7.2 规定的依赖清单所致，不影响功能；
需要 Java-WebSocket 的日志时按 `aifei-websocket/README.md` 自带 `slf4j-api` + 一个绑定即可。
