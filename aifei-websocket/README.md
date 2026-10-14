# aifei-websocket

`aifei-websocket` 是面向 [aifei](https://aifei.cn) 应用的 WebSocket 双向基础能力模块，补齐框架未覆盖的 WebSocket 能力。它含两个**分别装配、各自独立**的子能力：

- **客户端能力（client）**：应用主动**出站连接**第三方 WS 服务，含心跳保活与自动重连。
- **服务端能力（server）**：Aifei 应用**接收 WS 连接**，管理会话与广播。

```text
应用 ──┬─ WebSocketClient  → 出站连接第三方 WS 服务（心跳保活 / 自动重连）
       └─ WebSocketServer  → 接收 WS 连接（会话管理 / 广播）
                │
                └─ 传输后端 javawebsocket（可替换，改 websocket.{client|server}.type）
```

**WebSocket 不在 Aifei 框架白名单内**（框架自身零第三方依赖、服务端为 Undertow）。因此本模块是**自研能力模块**：基于 **Java-WebSocket** 后端实现，第三方类型一律 `optional` 且**不暴露在公共 API**上——公共面**零第三方类型、零框架类型**。换后端（未来的 Netty / Undertow / 自研）只需改配置 `websocket.{client|server}.type`，不改业务代码。

## 坐标与安装

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-websocket</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 消费方必须自带的依赖

模块内 `aifei-all` 为 `provided`、`Java-WebSocket` 为 `optional`，**都不向下游传递**；又因 Java-WebSocket 经 **SLF4J 门面**记日志、不自带绑定，消费方还需自带 `slf4j-api` 与一个日志绑定。缺一套就是两种典型故障：缺传输后端 → 类加载失败，缺日志绑定 → SLF4J 打印一次警告后静默。

```xml
<!-- 传输后端本体：模块内 optional，不传递，消费方必须自带 -->
<dependency>
    <groupId>org.java-websocket</groupId>
    <artifactId>Java-WebSocket</artifactId>
    <version>1.6.0</version>
</dependency>

<!-- Java-WebSocket 经 SLF4J 门面记日志，需自带 api + 一个绑定 -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
</dependency>
```

绑定版本需与 Java-WebSocket 传递的 `slf4j-api` 对齐（示例用 `2.0.13`）；也可换成 `log4j-slf4j2-impl` 等你项目已在用的绑定。

## 接入方式一：aifei 装配（推荐）

两个子能力各自一个 `Plugin`，**分别装配、各自独立**：只出站连第三方服务的应用仅装配客户端插件，只做服务端的应用仅装配服务端插件。

### 第 1 步：写配置 `app-config.txt`

```properties
# 客户端：出站连接第三方 WS 服务（uri 必填，缺失即应用启动失败）
websocket.client.uri = ws://127.0.0.1:9000/ws
websocket.client.heartbeat-seconds = 30
websocket.client.auto-reconnect = true
websocket.client.header.Authorization = Bearer my-token

# 服务端：接收 WS 连接（port 必填，缺失即应用启动失败）
websocket.server.host = 0.0.0.0
websocket.server.port = 9000
websocket.server.session-idle-timeout-seconds = 300
```

配置写错的表现是**应用启动失败**（异常从 `Plugin.start()` 抛出），不会带着半套配置跑起来。逐键说明见「配置项表」。

### 第 2 步：实现两个监听器

```java
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.Message;

public class MyClientListener implements ClientListener {

    @Override
    public void onOpen() {
        // 每次成功建连（含重连）都会触发；重连场景在此重放缓冲的业务消息
    }

    @Override
    public void onMessage(Message message) {
        if (message.isText()) {
            System.out.println("recv: " + message.asText());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // 无论用户主动还是异常关闭，都恰好触发一次；remote=true 表示由对端发起
    }

    @Override
    public void onError(Throwable error) {
        // 已包装为 WebSocketConnectionException，不外泄第三方类型
    }

    @Override
    public void onReconnect(int attempt, long delayMillis) {
        // 即将进行第 attempt 次重连（从 1 起），已算出退避时长 delayMillis 毫秒
    }
}
```

```java
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketSession;

public class MyServerListener implements ServerListener {

    @Override
    public void onOpen(WebSocketSession session) {
        System.out.println("session open: " + session.id());
    }

    @Override
    public void onMessage(WebSocketSession session, Message message) {
        if (message.isText()) {
            session.send(message.asText());   // 回声：回调内 send 安全，是推荐写法
        }
    }

    @Override
    public void onClose(WebSocketSession session, int code, String reason, boolean remote) {
        // session 仍可读 id() / remoteAddress()，但此时 send 会失败
    }
}
```

### 第 3 步：注册两个 Plugin

```java
import cn.hg.aifei.websocket.aifei.WebSocketClientPlugin;
import cn.hg.aifei.websocket.aifei.WebSocketServerPlugin;

@Override
public void config(Plugins plugins) {
    // Prop / Plugins / PropKit 均为 aifei 内核类型
    Prop prop = PropKit.use("app-config.txt");
    plugins.add(new WebSocketClientPlugin(prop, new MyClientListener()));
    plugins.add(new WebSocketServerPlugin(prop, new MyServerListener()));
}
```

`Plugin` 的 `start()` 内完成「解析配置 → 构建实例 → 建连（客户端）/ 启动（服务端）→ 注册 `@Inject` 单例」，`stop()` 关闭对应实例，二者幂等。

### 第 4 步：业务侧注入使用

```java
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketServer;
import cn.hg.aifei.websocket.WebSocketSession;

@Inject
WebSocketClient client;

@Inject
WebSocketServer server;

client.send("hello");                               // 仅 OPEN 时成功，否则抛 IllegalStateException
server.broadcast("notice");                         // 向全部会话广播，跳过已关闭会话

for (WebSocketSession session : server.sessions()) {  // 快照，可能含正在关闭中的会话
    System.out.println(session.id() + " -> " + session.remoteAddress());
}
```

## 接入方式二：程序化（非 aifei 环境 / 多连接场景）

`WebSocketKit` 免 `Prop`、免框架；返回的实例**不会自动 connect / start**，由调用方决定。多连接场景反复调用 `createClient(...)` 即可，每个实例独立。

### 客户端

模块**不提供阻塞等待 API**（无 `connectBlocking`）；需要同步等待时用 `ClientListener` + 同步器自行实现：

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketKit;

ClientConfig config = new ClientConfig();
config.setUri("ws://127.0.0.1:9000/ws");
config.setHeartbeatSeconds(30);
config.setAutoReconnect(true);

final CountDownLatch opened = new CountDownLatch(1);

ClientListener listener = new ClientListener() {

    @Override
    public void onOpen() {
        opened.countDown();
    }

    @Override
    public void onMessage(Message message) {
        System.out.println("recv size=" + message.size());
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Throwable error) {
    }
};

WebSocketClient client = WebSocketKit.createClient(config, listener);
client.connect();                                   // 异步，立即返回
opened.await(5, TimeUnit.SECONDS);                  // 等待建连成功
client.send("hello");                               // 仅 OPEN 时成功
```

### 服务端

```java
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.WebSocketKit;
import cn.hg.aifei.websocket.WebSocketServer;

ServerConfig config = new ServerConfig();
config.setHost("0.0.0.0");
config.setPort(0);                                  // 0 = 随机端口，实际端口用 server.port() 读取
config.setSessionIdleTimeoutSeconds(300);

WebSocketServer server = WebSocketKit.createServer(config, new MyServerListener());
server.start();
System.out.println("listening on " + server.port());
server.broadcast("notice");
server.stop();
```

## 配置项表

键名采用 kebab-case，与配置对象字段一一对应；`websocket.client.*` 与 `websocket.server.*` 互不依赖。**未配置的键一律沿用配置对象自身的默认值**——默认值只在 `ClientConfig` / `ServerConfig` / `WebSocketConfig` 内定义，`WebSocketConfigLoader` 不硬编码任何默认值，只在键存在时覆写。

### `websocket.client.*`

| 键 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `websocket.client.type` | String | 否 | `javawebsocket` | 传输后端名；未注册即启动失败 |
| `websocket.client.uri` | String | **必填** | — | 目标地址，须以 `ws://` 或 `wss://` 开头；**缺失即应用启动失败** |
| `websocket.client.heartbeat-seconds` | int | 否 | `0`（禁用） | 心跳间隔（秒），范围 `[0, 2147483]` |
| `websocket.client.connect-timeout-millis` | int | 否 | `5000` | 连接超时（毫秒），≥ `1` |
| `websocket.client.auto-reconnect` | boolean | 否 | `false` | 是否自动重连 |
| `websocket.client.max-retries` | int | 否 | `5` | 连续失败重试上限，范围 `[1, 2147483]` |
| `websocket.client.initial-retry-delay-millis` | long | 否 | `500` | 首次重试延迟（毫秒），≥ `1` |
| `websocket.client.max-retry-delay-millis` | long | 否 | `30000` | 单次重试延迟上限（毫秒），须 ≥ `initial-retry-delay-millis` |
| `websocket.client.retry-jitter-ratio` | double | 否 | `0.2` | 抖动比例，范围 `[0.0, 1.0]` |
| `websocket.client.header.<name>` | String | 否 | 空 | 握手附加请求头：`<name>` 为请求头名、键值为请求头值 |

### `websocket.server.*`

| 键 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `websocket.server.type` | String | 否 | `javawebsocket` | 传输后端名；未注册即启动失败 |
| `websocket.server.host` | String | 否 | `0.0.0.0` | 监听地址；不得为空白 |
| `websocket.server.port` | int | **必填** | — | 监听端口，范围 `[0, 65535]`，`0` = 随机端口；**缺失即应用启动失败**（`0` 也必须显式写出） |
| `websocket.server.heartbeat-seconds` | int | 否 | `0`（禁用） | 心跳间隔（秒），范围 `[0, 2147483]` |
| `websocket.server.reuse-address` | boolean | 否 | `true` | 是否允许地址复用 |
| `websocket.server.session-idle-timeout-seconds` | int | 否 | `0`（禁用） | 空闲会话回收阈值（秒），≥ `0` |
| `websocket.server.max-pending-connections` | int | 否 | `-1`（不设置） | 挂起连接队列上限，范围 `[-1, 65535]` |

### 握手附加请求头

请求头是 map，无法用扁平键表达，改用**前缀方案**：键形如 `websocket.client.header.<name>`，其中 `<name>` 为请求头名、键值为请求头值。例如配置 token：

```properties
websocket.client.header.Authorization = Bearer my-token
websocket.client.header.X-Tenant = acme
```

### 取值语法与校验

- 取值范围越界（如 `port = 70000`）在配置对象 setter 内即抛 `IllegalArgumentException`，表现为**应用启动失败**；跨字段校验（`uri` 协议、`max-retry-delay-millis ≥ initial-retry-delay-millis`）在构建实例时完成。
- 取值语法非法（如把 `port` 写成非整数）按「配置错误」上报，异常消息含键名与实参，格式为 `websocket config invalid: <键>=<值>, reason=<原因>`。

## 心跳保活与自动重连

**两项默认都关闭**：`heartbeat-seconds = 0`（禁用）、`auto-reconnect = false`。需要时显式开启。

- **心跳（RFC6455 控制帧）**：由传输后端按 `heartbeat-seconds` 为周期发送 **ping** 控制帧并等待 **pong**，用于保活并探测对端存活。ping / pong **不**作为消息上浮到 `onMessage`，也不产生任何监听器回调。
- **探活超时**：在 `1.5 × heartbeat-seconds` 内未收到对端 pong 即判定连接丢失，由传输层关闭连接（触发 `onClose`）。客户端与服务端**都**按此机制发 ping。
- **与自动重连的联动**：客户端心跳超时走的是**正常关闭路径**，因此开启 `auto-reconnect` 时**会触发重连**（若无心跳，只能依赖 TCP 层最终报错，发现更慢）。
- **用户主动 `close()` 永不触发重连**（硬约束）：`close()` 置「用户主动关闭」标记并取消待定重连任务；该标记在下次显式 `connect()` 时清除。
- **退避算法 = 指数退避 + 抖动**：默认参数（`initial-retry-delay-millis = 500`、`max-retry-delay-millis = 30000`、`retry-jitter-ratio = 0.2`、`max-retries = 5`）下，单次重试延迟序列约为 **`500 / 1000 / 2000 / 4000 / 8000` 毫秒**（抖动使每次落在上下界之间）。
- **终态**：重连成功（`onOpen`）→ 重试计数复位；连续失败达到 `max-retries` → 进入终态（`state() == CLOSED`）并触发一次 `onReconnectExhausted(attempts, lastError)`，**停止重连**；直到再次**显式** `connect()` 才重新开始。
- **一次连接丢失的回调次序**：`onError`（若有）→ `onClose` → `onReconnect`（若重连）→（成功）`onOpen` /（耗尽）`onReconnectExhausted`。

## 发送语义（重要）

**本模块不提供发送队列 / 背压**：`send*` 仅在连接 `OPEN` 时成功，否则抛 `IllegalStateException` 且消息**不进任何队列、被直接丢弃**。

「重连期间缓冲并在 `onOpen` 后重放」需要**调用方自行实现**：

```java
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketClient;

public class BufferingClientListener implements ClientListener {

    private final Queue<String> pending = new ConcurrentLinkedQueue<String>();

    private volatile WebSocketClient client;

    public void bind(WebSocketClient client) {
        this.client = client;
    }

    public void sendOrBuffer(String text) {
        if (client != null && client.isOpen()) {
            client.send(text);
        } else {
            pending.add(text);          // 未就绪（含重连期间）先缓冲
        }
    }

    @Override
    public void onOpen() {
        String text;
        while ((text = pending.poll()) != null) {
            client.send(text);          // 重连成功后按序重放
        }
    }

    @Override
    public void onMessage(Message message) {
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Throwable error) {
    }
}
```

## 服务端会话管理

- **`sessions()`** 返回当前**已登记**会话的**不可变快照**；其中可能包含正在关闭中的会话。
- **`broadcast*`** 向快照内全部会话发送，对**已关闭 / 正在关闭的会话静默跳过**（不抛异常、不中断广播）；无会话时为无操作。
- **空闲会话回收**（`session-idle-timeout-seconds`，默认 `0` = 禁用）：定义「空闲」= 会话在阈值内**没有任何入站帧**（数据帧与 **pong** 控制帧都算活跃）。**pong 计入活跃**，因此「只回 pong、不发数据帧」的存活会话**不会被误判回收**。回收以关闭码 `1000`（正常关闭）关闭并注销，**最坏回收延迟约为 `2 × session-idle-timeout-seconds`**（扫描周期 + 阈值）。它与心跳**相互独立**、可同时启用：心跳检测「死连接」（不回 pong），空闲回收检测「活着但不说话」的连接。
- **回调内使用禁忌**：客户端与服务端回调内**可以**安全地 `send` / `close`（服务端「回声」是推荐写法）；但**禁止**在客户端回调内 `connect()`（可能重入底层 I/O 线程），也**禁止**在服务端回调内 `server.stop()`（可能死锁）；如需，请投递到外部线程执行。回调内不得做耗时阻塞操作。

## 编解码（JSON）

本模块**不内置任何 JSON 实现**，也不暴露任何 JSON 库类型。需要 `send(Object)` / `broadcast(Object)` 时，消费方自行实现 `spi.MessageCodec` 并注入。实现**必须线程安全**（同一编解码器会被多个连接与多个线程并发调用）：

```java
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketCodecException;
import cn.hg.aifei.websocket.spi.MessageCodec;

public class JsonCodec implements MessageCodec {

    @Override
    public Message encode(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload can not be null");
        }
        return Message.text(toJson(payload));     // 文本或二进制由实现决定
    }

    @Override
    public <T> T decode(Message message, Class<T> type) {
        if (message == null || type == null) {
            throw new IllegalArgumentException("message and type can not be null");
        }
        if (!message.isText()) {
            throw new WebSocketCodecException("expect a text message");
        }
        return fromJson(message.asText(), type);
    }

    private static String toJson(Object payload) {
        // 换成你选定的 JSON 库（如 aifei-json / fastjson2 / jackson）；本模块不内置任何 JSON 实现
        throw new UnsupportedOperationException("implement with your JSON library");
    }

    private static <T> T fromJson(String json, Class<T> type) {
        throw new UnsupportedOperationException("implement with your JSON library");
    }
}
```

编解码器是**对象引用**，配置键表达不了它（与监听器同属「配置里表达不了的对象引用」）。因此**两条接入路径都经构造参数注入**：

**aifei 装配路径**：用 3 参构造器把 `MessageCodec` 与监听器一并传给 `Plugin`；`Plugin.start()` 会把它设入配置对象，`@Inject` 到的实例即可直接 `send(Object)` / `broadcast(Object)`：

```java
import cn.hg.aifei.websocket.aifei.WebSocketClientPlugin;
import cn.hg.aifei.websocket.aifei.WebSocketServerPlugin;

@Override
public void config(Plugins plugins) {
    // Prop / Plugins / PropKit 均为 aifei 内核类型
    Prop prop = PropKit.use("app-config.txt");
    // 第 3 个参数即 MessageCodec：与监听器一样是对象引用，故经构造参数传入
    plugins.add(new WebSocketClientPlugin(prop, new MyClientListener(), new JsonCodec()));
    plugins.add(new WebSocketServerPlugin(prop, new MyServerListener(), new JsonCodec()));
}
```

```java
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketServer;

@Inject
WebSocketClient client;

@Inject
WebSocketServer server;

client.send(order);                             // 经 JsonCodec 编码后发送；编码失败抛 WebSocketCodecException
server.broadcast(notice);                       // 向全部会话广播对象
```

不需要对象编解码时用 2 参构造器 `new WebSocketClientPlugin(prop, listener)`（等价 `codec = null`），此时 `send(String)` / `send(byte[])` / `broadcast(String)` / `broadcast(byte[])` 不受影响。

**程序化路径**：在配置对象上 `setCodec` 注入：

```java
ClientConfig config = new ClientConfig();
config.setUri("ws://127.0.0.1:9000/ws");
config.setCodec(new JsonCodec());               // 未注入编解码器时 send(Object) 抛 IllegalStateException
WebSocketClient client = WebSocketKit.createClient(config, listener);
client.connect();
// ...
client.send(order);                             // 经 JsonCodec 编码后发送；编码失败抛 WebSocketCodecException
```

## 常见问题 / 注意事项

| 问题 | 解答 |
| --- | --- |
| 启动报类加载失败 / 连不上第三方服务？ | 消费方必须**自带** `org.java-websocket:Java-WebSocket`（模块内 `optional` 不传递） |
| 日志没有任何输出？ | Java-WebSocket 经 SLF4J 门面记日志，需自带 `slf4j-api` + 一个绑定 |
| 想用随机端口？ | `websocket.server.port = 0`，实际端口用 `server.port()` 读取；但 `port` 键**必填**，`0` 也得显式写出 |
| `close()` 之后还能再连吗？ | 能。显式 `connect()` 允许，重试计数复位；「用户主动关闭不重连」只对**自动**重连生效 |
| `send` 传 `null` 会怎样？ | 抛 `IllegalArgumentException`；`send` 时连接非 `OPEN` 抛 `IllegalStateException` 且消息被丢弃 |
| 为什么在 `onMessage` 里收不到 ping / pong？ | 它们是 RFC6455 控制帧，由传输层内部处理，**不上浮**为 `Message` |
| 服务端为什么要回收空闲会话？ | 客户端可能异常掉线留下死连接；按 `session-idle-timeout-seconds` 回收（`pong` 计入活跃，不会误杀存活会话） |
| 换传输后端要改代码吗？ | 不用。改 `websocket.{client|server}.type` 并自带该后端的依赖即可 |
| 对象编解码在 aifei 装配下能用吗？ | 能。用 3 参构造器把 `MessageCodec` 与监听器一并传入 `Plugin`（如 `new WebSocketClientPlugin(prop, listener, codec)`）；不需要对象编解码时用 2 参构造器（等价 `codec = null`） |

## 构建与验证

```bash
cd aifei-websocket && mvn clean verify
```

## 相关文档

- 设计事实来源（定位、契约、行为语义、验收）：[docs/design.md](docs/design.md)
- 开发约定（硬约束、包边界与门禁、唯一定义点、验证命令）：[AGENTS.md](AGENTS.md)
