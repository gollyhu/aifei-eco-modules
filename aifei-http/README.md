# aifei-http

aifei 生态的**出站 HTTP 统一客户端**（库模块 / SDK）：业务只构造请求、读响应，超时、重试、拦截、编解码、资源名由模块统一负责。

- 坐标：`io.github.gollyhu:aifei-http:1.0.0-SNAPSHOT`
- 包名：`cn.hg.aifei.http`；Java 8（class major 52）
- **零第三方**：传输实现只用 JDK `HttpURLConnection`；不引 okhttp / jackson / slf4j / commons-*

## 依赖声明

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-http</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- 消费方必须自带框架内核：本模块对 aifei 是 provided，不向下游传递 -->
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei</artifactId>
    <version>1.1.2</version>
</dependency>
```

## 装配

```java
// AppConfig 内：配置源就绪后加入插件
plugins.add(new HttpPlugin(prop)
        .addInterceptor(new AuthInterceptor())     // 注册顺序 = 进入顺序，收尾为逆序
        .addInterceptor(new MetricsInterceptor()));
```

装配完成后：`@Inject HttpClient` 取到单例；静态上下文（工具方法 / 脚本）用 `HttpKit`。

```java
public class OrderService {

    @Inject HttpClient http;                       // 与 HttpKit.client() 是同一个对象

    public String detail(String id) {
        HttpResponse response = http.exec(HttpRequest.get("https://api.example.com/v1/orders")
                .query("id", id)
                .header("Accept", "application/json")
                .connectTimeout(1000)
                .readTimeout(3000));

        if (response.statusCode() != 200) {        // 非 2xx 不抛异常，由业务判断
            return null;
        }
        return response.bodyAsString();
    }
}
```

## 主要能力

| 能力 | 用法 |
| --- | --- |
| 请求构造 | `HttpRequest.get/post/put/delete/head(url)` 或 `of(method, url)`；`query` / `header`（覆盖）/ `addHeader`（多值）/ `contentType` / `body(String)` / `body(byte[])` / `resource` |
| 逐请求覆盖 | `connectTimeout(ms)` / `readTimeout(ms)` / `retry(times[, intervalMillis])` |
| 发送 | `HttpClient#exec(HttpRequest)` / `HttpKit.exec(...)` 与 5 个便捷方法 |
| 响应 | `statusCode()` / `header(name)`（大小写不敏感）/ `headers(name)`（多值）/ `bodyAsString()` / `bodyAsBytes()` / `elapsedMillis()` / `attempts()` |
| 拦截器 | 实现 `HttpInterceptor`：`before(HttpAttempt)` 必写、`after(HttpAttempt)` 选写（默认空） |
| 资源名 | 默认 `METHOD host path`（去 query、含非默认端口）；`resource(...)` 可完全覆盖 |

`HttpAttempt` 在进入阶段即可读到最终资源名、方法、完整 URL 与尝试序号；收尾阶段另可读到该次响应 / 异常与耗时。

## 配置（`http.*`，全部可选）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `http.connect-timeout` | `5000` | 建连超时（毫秒），须 ≥ 1 |
| `http.read-timeout` | `10000` | 读超时（毫秒），须 ≥ 1 |
| `http.retry-times` | `0` | 幂等（GET / HEAD）请求的默认重试次数，须 ≥ 0 |
| `http.retry-interval` | `200` | 每次重试前的固定等待（毫秒），`0` 合法 |
| `http.max-response-bytes` | `8388608`（8 MiB） | 单次响应体上限，作用于**解压后**字节数，须 ≥ 1 |
| `http.gzip` | `false` | `true` 时发送 `Accept-Encoding: gzip` 并在模块内解压 |
| `http.user-agent` | `aifei-http/1.0.0` | 默认 User-Agent；空白串表示模块不主动设置该头 |

取值非法（非整数、越界、非布尔）→ 启动失败，异常消息含**键名与原值**。

**不提供的键**：`http.follow-redirects`（行为固定为不跟随）、`http.cookie*`（无 Cookie 容器）、`http.headers.*`（通用默认头请用拦截器注入）。

## 语义要点

- **重试**：默认 0 次，仅 `GET` / `HEAD` 可开启（`retry(...)` 对非幂等方法**当场抛** `IllegalArgumentException`，不静默忽略）；触发条件 = 网络层失败 **或** 5xx，4xx / 3xx / 解码失败 / 解压失败 / 超限 / 非法 URL **不重试**；退避为固定间隔，无指数、无抖动。
- **拦截器**：`before` 正序、`after` 逆序，正常路径各恰好一次；任一 `before` 抛异常 → 后续不进入、**不发送**、**不重试**，异常原样传播；**不支持短路**（无返回值，无法合成响应）。
- **非 2xx 不抛**：3xx / 4xx / 5xx 一律返回 `HttpResponse`。
- **异常口径**：传输失败 / 配置非法 / 未装配 / 超限 / 解码失败 / 重试拒绝 → `HttpException`（`extends RuntimeException`），底层原因在 `getCause()`；调用方参数当场写错（`null`、负数、空白名）→ `IllegalArgumentException`。
- **连接复用**：读完响应体关闭流、归还 JDK 连接缓存；仅异常与超限路径断开连接。

## 能力边界（v1 不做）

不支持 HTTP/2；连接池受 JVM 全局属性约束且**模块只读不写**；超时只有 connect / read 两段，**无整体 deadline、不可取消在途请求**；无 provider / SPI / 后端切换；无异步 / 回调 / Future；无 multipart 与文件上传下载高层封装；无 Cookie 容器与持久化；不提供重定向完整语义矩阵（303 / 307 / 308，行为固定为不跟随）；无代理认证 / mTLS 高层配置；无熔断限流（属 `aifei-sentinel`）。

模块**零 `aifei-sentinel` 依赖**，只以统一口径产出资源名（`METHOD host path`）供其 `HttpInterceptor` 自行埋点；该维度与 sentinel 入站维度「全限定类名#方法名」是两个不同维度，可同表并存。

## 注意事项

1. `HttpRequest` 是**可变构建器**且**非线程安全**：不要跨线程共享；一次 `exec()` 返回后可安全复用。
2. 鉴权类拦截器必须用 `header(...)`（覆盖）而非 `addHeader(...)`（追加），否则请求头会随重试次数累积。
3. `stop()` 只清静态门面：`HttpKit` 调用将抛 `HttpException`，但**已注入的 `HttpClient` 字段仍持旧实例**（框架无注销单例的 API，属已知边界）；`stop()` 后再次 `start()` 复用首次装配的实例，不重复注册。
4. `http.user-agent` 配空白串后，JDK 仍可能补默认 `User-Agent`，属不可控。
5. 响应体超限直接失败，**不截断返回**（截断会产出不可解析的残体）。
6. 模块自身日志经 `cn.aifei.log.Log` 惰性取用，唯一记录点是「将重试」时的一条 `warn`。

## 文档

| 文档 | 内容 |
| --- | --- |
| `docs/design.md` | 契约事实来源：公共面逐字签名、设计决策、门禁、AC 映射 |
| `docs/testing.md` | 测试策略、AC ↔ 用例 ↔ 结果台账 |
| `AGENTS.md` | 开发约束：硬约束、包边界、唯一定义点清单、验证命令 |
