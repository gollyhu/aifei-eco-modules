# aifei-http-demo

`io.github.gollyhu:aifei-http` 的**示例工程**（应用工程，非库），用真实装配的 `@Inject HttpClient` 请求
**本机自己的回环端点**，现场演示出站 HTTP 的四类能力：统一超时、统一重试、统一拦截、统一编解码（含资源名）。

- 端口 **18120**（`aifei-job-demo` 占 18110）
- 依赖 `io.github.gollyhu:aifei-http:1.0.0-SNAPSHOT` + `cn.aifei:aifei-all:1.1.2`（`aifei-http` 以 `provided` 声明，消费方自带）
- 页面入口 `http://127.0.0.1:18120/http/demo/page`（静态层：`view/http/index.html` + `assets/{js,css}` + 本地全局 Vue，零 CDN）

## ★ 全程零外网声明

**本 demo 的任何请求目标都只能是 `127.0.0.1:18120` 自身**：

1. 演示端点请求的 URL 一律由 `demo.self-base-url`（默认 `http://127.0.0.1:18120`）拼出；
2. 「连接被拒」演示打的是 `http://127.0.0.1:1/...`（本机未监听端口），仍属回环；
3. `/http/demo/send` 只接收**相对路径** `path`，传入含 `://` 的外部地址会被当场拒绝（`code=1`）；
4. 源码与页面中不存在任何外部域名，断网环境下功能完整可用。

## 前置：先把 aifei-http 装入本地仓库

`aifei-http` 是库模块，需先安装到本地仓库（或按 [根 README 构建小节](../README.md#构建) 安装全部模块）：

```bat
cd ../aifei-http
mvn -q -DskipTests clean install
cd ../aifei-http-demo
```

## 启动 / 停止

```bat
start-demo.bat        :: 编译 + 打包 classpath + 启动（-Dundertow.port=18120）
stop-demo.bat         :: 按 18120 找监听进程并结束
```

手工启动（参数同 `start-demo.bat`，PowerShell 可直接执行）：

```powershell
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
$CP = Get-Content target\cp.txt -Raw
java -Ddemo.config=app-config.txt -Dundertow.port=18120 -Dundertow.host=0.0.0.0 -cp ".\target\classes;$CP" cn.hg.aifei.http.demo.DemoApp
```

构建自验：`mvn -B clean verify`（当前无测试源码，编译打包通过即 BUILD SUCCESS）。

## 配置（`src/main/resources/app-config.txt`）

`http.*` 七键写全前缀；非法值会让应用启动失败（消息含键名与原值）。

| 键 | 本 demo 取值 | 说明 |
| --- | --- | --- |
| `http.connect-timeout` | `5000` | 建连超时（毫秒） |
| `http.read-timeout` | `10000` | 读超时（毫秒） |
| `http.retry-times` | `0` | 默认**不重试**（重试必须显式 `retry(N)` 开启） |
| `http.retry-interval` | `200` | 每次重试前的固定等待 |
| `http.max-response-bytes` | `8388608` | 响应体上限（解压后字节） |
| `http.gzip` | `true` | 置 `true` 才会发 `Accept-Encoding: gzip` 并在模块内解压 |
| `http.user-agent` | `aifei-http-demo/1.0.0` | 默认 UA |

另有 demo 自身键 `demo.self-base-url`（回环基址，**改端口时必须同步改它**）与 `enjoyDevMode` / `printAction`。

## 端点全表（全部 GET，HTTP 恒 200，语义由业务 `code` 承载，`0` 为成功）

| 路径 | 参数 | 用途 | 覆盖场景 |
| --- | --- | --- | --- |
| `/demo/echo` | `status`(100–599，默认 200)、`delay`(0–30000ms)、`failFirst`(N)、`key`(计数分组)、`gzip`(1/true)、`redirect`(1/true)、`text`(指定响应体文本) | **可控回环目标端点**（由 demo 自身 HIO `EchoHandler` 实现，不经 Service）：原样回显方法 / 路径 / 查询串 / 全部请求头 / 请求体大小与内容；可按参数改状态码、延迟、前 N 次 503、gzip 压缩、302 重定向 | §八 全部场景的靶子 |
| `/http/demo/state` | 无 | 现场快照：`http.*` 七键、`clientInjected`、已登记拦截器顺序、累计 `requestCount` / `attemptCount` / `retryCount`、最近资源名、echo 计数、JVM `http.maxConnections` / `http.keepAlive`（**只读展示**） | §八-8；装配与计数证据 |
| `/http/demo/get` | `status`、`delay`、`failFirst`、`key` | 基本 GET：返回 `request`（即将发出的请求）+ `response`（状态码 / 总耗时 / 尝试次数 / 响应头 / 响应体）或 `error` | §八-2 |
| `/http/demo/retry` | `failFirst`(默认 2)、`retryTimes`(默认 2)、`intervalMillis`(缺省用 `http.retry-interval`) | 重试对照：`withoutRetry`（默认不重试，`attempts=1` 即返回 503）vs `withRetry`（显式 `retry(N)`，`attempts=N+1` 拿到 200）+ 第二次运行的穿链记录 | §八-3 |
| `/http/demo/timeout` | `delay`(默认 1500)、`readTimeout`(默认 300)、`connectTimeout`(默认 500) | 三分支：`normal`（不超时）/ `readTimeout`（`HttpException`，cause 为 `SocketTimeoutException`）/ `connectRefused`（`HttpException`，cause 为 `ConnectException`），返回异常类型、消息、cause、耗时、尝试次数与「是否符合预期」 | §八-4、§八-7 |
| `/http/demo/interceptor` | `resource`（缺省 `demo.resource.explicit`）、`status` | 资源名对照：`defaultResource`（默认推导 `METHOD host:port path`）vs `explicitResource`；两次运行的 `beforeOrder`（正序）/ `afterOrder`（逆序）与逐条 `events` | §八-5 |
| `/http/demo/gzip` | 无 | 探针返回 echo 实际收到的 `Accept-Encoding`；再取一次 gzip 压缩响应，给出 `decompressed`（模块解压后与原文逐字相等）与解压后字节数 | §八-2；编解码 |
| `/http/demo/redirect` | 无 | echo 返回 302 → 模块**固定不跟随**，返回 `status=302`、`location`、`followedRedirect=false`、`attempts=1` | §八-2 |
| `/http/demo/send` | `method`、`path`、`query`、`headers`(多行 `Name: Value`)、`body`、`contentType`、`resource`、`connectTimeout`、`readTimeout`、`retryTimes`、`intervalMillis` | 通用发送（页面「请求构造器」）：返回 `request`（方法 / 完整 URL / 头 / 体大小与内容 / 显式资源名 / 超时 / 重试设置）+ `response` 或 `error` + `trace`；`rejected` 记录被拒的入参（如 POST 开重试） | §八-1、§八-2、§八-6、§八-7 |
| `/http/demo/echo/state/reset` | 无 | 重置 echo 端点的 `failFirst` 计数分组（返回清理前快照） | 辅助：多次演示互不干扰 |

> §八-6「空态」是纯前端行为（首次进入不残留上次结果），数据结构已由 `send` 的 `request` / `response` / `error` 三段支撑。

## 演示步骤（页面未就绪时可先用 curl 验证）

```bat
curl.exe -s "http://127.0.0.1:18120/http/demo/state"
curl.exe -s "http://127.0.0.1:18120/http/demo/get?status=200&delay=0"
curl.exe -s "http://127.0.0.1:18120/http/demo/retry"
curl.exe -s "http://127.0.0.1:18120/http/demo/timeout"
curl.exe -s "http://127.0.0.1:18120/http/demo/interceptor"
curl.exe -s "http://127.0.0.1:18120/http/demo/gzip"
curl.exe -s "http://127.0.0.1:18120/http/demo/redirect"
curl.exe -s "http://127.0.0.1:18120/http/demo/send?method=GET&path=/demo/echo&resource=demo.explicit"
curl.exe -s "http://127.0.0.1:18120/http/demo/echo/state/reset"
```

实测要点（本机已跑通）：

- `retry`：`withoutRetry.attempts=1`（503）；`withRetry.attempts=3`（两次 503 + 一次 200，总耗时 ≈ 2×200ms 间隔 + 传输）；
- `timeout`：读超时 `elapsedMillis≈306 ≥ 300`，cause `java.net.SocketTimeoutException: Read timed out`；
  连接被拒 cause `java.net.ConnectException: Connection refused`；
- `interceptor`：`beforeOrder = demo-a>demo-b>demo-c`，`afterOrder = demo-c>demo-b>demo-a`，
  默认资源名形如 `GET 127.0.0.1:18120/demo/echo`（含非默认端口、去 query）；
- `gzip`：`http.gzip=true` 时 `echoSawAcceptEncoding=gzip`、`decompressed=true`；
- `redirect`：`status=302`、`followedRedirect=false`；
- `send`：POST 带 JSON 体时 echo 回显 `bodySize=7`；`retryTimes=2` 被当场拒绝（`非幂等方法不支持重试: POST`）。

## 实现说明与已知边界

1. **拦截器登记了 3 个实例**（`demo-a` / `demo-b` / `demo-c`，同一个记录型类 `DemoInterceptor`）：
   单个实例看不出「正进逆出」，三个实例才能把顺序实证出来。它们**只记录**，不短路、不抛异常、不改请求；
   计数只由第一个实例（`primary`）贡献，避免三倍放大。
2. **`HttpException` 的实际类型**：传输层失败时抛出的是模块内部的 `HttpIoException`（`HttpException` 子类，
   库内标记「网络层失败」）。页面按 `HttpException` 家族展示即可，`attempts()` / `getCause()` 均可读。
3. **预期失败不算接口失败**：`timeout` 等端点的异常细节放在 `error` 字段里，整体仍 `code=0`，便于页面渲染错误面板。
4. **gzip 只能由配置切换**：模块无逐请求 gzip 开关，`http.gzip=false` 时既不发送 `Accept-Encoding` 也不解压
   （压缩字节原样返回）。要演示「默认不压缩」需改配置重启。
5. **重定向固定不跟随**：模块不提供 `http.follow-redirects` 键，行为不可配置。
6. **`/demo/echo` 的请求体回显**只在请求声明正的 `Content-Length` 时读取（上限 64 KiB），无体请求不读流、不阻塞。
7. 页面走 Aifei 静态层（纯 HTML/CSS/JS + 本地全局 Vue，无构建工具），每个控件都发真实请求、无假开关。
   静态目录必须在启动前存在，启动后新建的 `webapp` 目录不会被纳入资源管理器（静态请求会 404）。
