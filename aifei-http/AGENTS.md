# aifei-http 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **改代码前必读 `docs/design.md`**（尤其 §5 的 6 类型逐字签名与 §2 的 16 条设计决策）。需求与设计冲突时，**先改设计决策再改代码**。
- 未经设计决策，不得自行确定契约类行为（异常类型与消息、资源名规则、重试触发条件、配置键名、拦截器链语义等），也不得新增或扩大公共 API 面。

## 交付性质

`aifei-http` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**：没有 `AppConfig` / `Launcher` / `service/` 包 / `webapp/` / HTTP 路由 / HIO / 表结构 / Db+Row / Generator / UI 页面。坐标 `io.github.gollyhu:aifei-http:1.0.0`（`packaging=jar`），公共 API + `internal` + `aifei.HttpPlugin` 同处一个 JAR。

**不适用范围（不得据此模块约束要求）**：表结构 / `Db+Row` / `Generator`（零 DB）；`@Path` Service 与 HIO（模块无入站 HTTP 面）；页面 / 原型基线（无 UI，`aifei-http-demo` 单页由前端直接实装，不经原型与 QA 基线门禁）。

## 硬性约束

### 技术基线

- **Java 8 双约束（语法 + API）**：`source`/`target` = `1.8`；主 JAR 内全部 class major = **52**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 后 API。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；原创文件写统一版权头，既有版权块不得改写。
- **零第三方**：pom 只允许 `cn.aifei:aifei-all`（**`provided`**，不向下游传递）+ `junit`（`test`）。**不得**新增 `optional` / `compile` 第三方依赖；**不得**引入任何日志绑定（`slf4j` / `logback` / `log4j` 一律禁止）。
- **零 DB**：全模块禁 `import java.sql.`，不依赖 `aifei-db`。
- **传输实现只用 `HttpURLConnection`**：`java.net.HttpURLConnection` / `java.net.URL` 只允许出现在 `internal` 包（根包禁止）；不支持 HTTP/2、不做协议协商。
- **禁改写 JVM 全局属性**：不得出现 `System.setProperty` / `System.getProperties().put`，不得出现 `http.maxConnections` / `http.keepAlive` 字面量——这些是 JVM 级全局值，改写会波及同进程其它 JDK HTTP 使用方，**只读不写**。
- **无 provider / 无 SPI / 无 `META-INF/services`**：`internal.HttpTransport` 为**包级可见**接口，**仅服务单测注入**，不得升为 `public`、不得提供任何公开注册方法。

### 公共面收敛（不得放宽）

- 根包 `cn.hg.aifei.http` **精确 6 个公共类型**：`HttpClient` / `HttpRequest` / `HttpResponse` / `HttpKit` / `HttpException` / `HttpInterceptor`。**不得增删、不得改名**；新增/变更须先改 `docs/design.md` §5 并过设计决策。
- 公共面**零第三方类型、零框架类型、零 JDK 传输类型**：不出现 `cn.aifei.*`、`java.net.URL`、`HttpURLConnection`、`java.util.function.*`；参数与返回以 `String` / `byte[]` / `Map` / `List` / 基本类型为主。
- 公共面**不得出现 `internal` 包行话**（唯一定义点、持有器、传输接口、编排器等）；`internal` / `aifei` 均属**非承诺 API**，消费方不得引用。
- **命名语义**：`HttpKit` 是**有状态门面**（依赖 `HttpPlugin.start()` 装配，未装配 / 已停止即失败）；纯静态无状态工具类才可用 `Util` 后缀（本模块**不提供** `HttpUtil`）。

## 包边界与门禁

`ArchitectureBoundaryTest`（**10 例**）扫描 `src/main/java`（`testSourcesOfflineOnly` 扫 `src/test/java`）全部 `.java`：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| ① | `noThirdPartyReference` | 每条 `import` 必须以 `java.` / `javax.` / `cn.aifei.` / `cn.hg.aifei.http.` 开头（本模块**零第三方**，无放行包） |
| ② | `frameworkOnlyInBridgePackage` | `cn.aifei.*`（除 `cn.aifei.log.*`）只允许出现在 `cn/hg/aifei/http/aifei/`；`cn.aifei.log.*` 允许出现在 `aifei` 与 `internal` |
| ③ | `noSqlReference` | 全模块不得出现 `import java.sql.` |
| ④ | `noJvmGlobalPropertyWrite` | 不得出现 `System.setProperty` / `System.getProperties().put`，也不得出现 `http.maxConnections` / `http.keepAlive` 字面量 |
| ⑤ | `publicSurfaceExact` | 根包**直接子文件**精确 **6** 个，且文件名集合**精确等于** `EXPECTED_ROOT_TYPES`（`HttpInterceptor.HttpAttempt` 为嵌套类型，不计数） |
| ⑥ | `resourceDerivationOnlyInHttpResource` | 除 `internal/HttpResource.java` 外，任何文件不得**同时**出现 `getHost()` 与 `getPath()` |
| ⑦ | `jdkTransportOnlyInInternal` | 根包不得 `import java.net.URL` / `java.net.HttpURLConnection`；`HttpURLConnection` 只在 `internal` 包出现 |
| ⑧ | `java8Compatibility` | 不得出现 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` / `"""` / `sealed ` 等 Java 8 后语法与 API |
| ⑨ | `bridgePackageExists` | 接入包存在且含 `HttpPlugin.java` |
| ⑩ | `testSourcesOfflineOnly` | 测试源码中的 `http://` / `https://` 字面量，host 只能是 `127.0.0.1` 或 `localhost` |

门禁**不得为兼容错误用法而放宽或绕过**；扫描依赖相对路径 `src/main/java`，**必须在 `aifei-http/` 根目录执行**。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 配置键名 / 默认值 / 解析 / 校验 / 非法值消息（含键名与原值） | `internal.HttpConfig` |
| 资源名拼装（默认推导 + 显式覆盖优先级；method 大写、端口归一、空 path 归一） | `internal.HttpResource#of` |
| 幂等白名单、重试次数换算、重试触发条件（网络失败 / 5xx） | `internal.HttpRetry` |
| 「网络层失败」标记（可重试判定依据） | `internal.HttpIoException`（唯一构造点 `JdkHttpTransport`） |
| connect / read 超时的逐请求与全局合并 | `internal.HttpTimeouts#resolve` |
| charset 解析、`String` ↔ `byte[]` | `internal.HttpCodec` |
| `Accept-Encoding` 决策、gzip 解压 | `internal.HttpGzip` |
| 响应体上限判定（读流与解压后共用） | `JdkHttpTransport#requireWithinLimit` |
| `HttpURLConnection` / `URL` 出现点、`setInstanceFollowRedirects(false)`、keep-alive 与 `disconnect` 策略 | `internal.JdkHttpTransport` |
| 拦截器链顺序（正序 `before` / 逆序 `after`）、收尾范围、异常原样传播、重试循环、度量写回、重试日志 | `internal.DefaultHttpClient` |
| 运行期实例持有与「未装配即失败」消息 | `internal.HttpHolder` |
| `HttpResponse` 装配（防御性拷贝 + 度量） | `HttpResponse#of` |
| `HttpException` 构造与消息格式 | `docs/design.md` §5.5 表格（各抛出点按表实现，不各写措辞） |
| 拦截器注册与顺序 | `HttpPlugin#addInterceptor` |

## 行为契约要点速查

- **资源名**：默认 `METHOD + " " + host + path`（去 query、method 大写、host 含非默认端口、空 path 归一 `/`）；显式 `resource` 完全替代推导；一次 `exec()` 内**冻结一次**，重试各次与异常路径恒等。
- **重试**：默认 **0** 次；仅 `GET` / `HEAD` 可开启（`HttpRequest#retry` 对非幂等方法**当场抛** `IllegalArgumentException`，不静默忽略）；触发条件 = 网络层失败 **或** 5xx；4xx / 3xx / 解码失败 / 解压失败 / 超限 / 非法 URL **不重试**；退避为固定 `http.retry-interval`（默认 200ms），无指数、无抖动。
- **拦截器**：`before` 正序、`after` 逆序，正常路径各恰好一次；任 `before` 抛异常 → 后续不进入、**不发送**、**不重试**、异常**原样**抛出；只有 `before` 已成功返回者（含抛出者自身）才收到 `after`；无短路、无返回值；同步在调用线程执行。
- **非 2xx 不抛**：3xx / 4xx / 5xx 一律返回 `HttpResponse`，由业务读 `statusCode()`。
- **超时**：仅 connect / read 两段；无整体 deadline、**不可取消在途请求**（README 必须声明）。
- **默认行为**：gzip 默认关（`http.gzip=true` 才发 `Accept-Encoding` 并解压）；**无 Cookie 容器**（业务经请求头管理）；**固定不跟随重定向**（3xx 原样返回）。
- **响应体上限**：默认 8 MiB，作用于**解压后**字节数；超限抛 `HttpException`（消息含生效上限值），**不截断**。
- **异常分类**：传输失败 / 配置非法 / 未装配 / 超限 / 解码失败 / 重试拒绝 → `HttpException`（`extends RuntimeException`，无受检异常外泄，底层异常在 `getCause()`）；调用方参数写错（null、负数、空白名）→ `IllegalArgumentException`。
- **`HttpRequest` 可变且非线程安全**：鉴权类拦截器必须用 `header()`（覆盖）而非 `addHeader()`（追加），否则头随重试次数累积。
- **keep-alive**：读完响应体后关闭流、**不** `disconnect()`；仅异常/超限路径 `disconnect()`（决策 13，对 `aifei-weixin` 底稿的有意偏离）。
- **日志**：一律 `cn.aifei.log.Log`，**方法内** `Log.get(本类.class)`（禁 `static` 字段，`LogFactory` 装配晚于类初始化）；唯一记录点为「将重试」时 `warn` 一次。

## 注释规范

- **语言**：一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、配置键名、异常消息。
- **javadoc**：公共类型与公共成员**必须**书写并写全 `@param` / `@return` / `@throws`；`internal` / `aifei` 成员在非自明处书写。公共面不得出现「唯一定义点 / 持有器 / 传输接口」等内部行话。
- **精简**：只在代码无法表达「为什么」时写注释；不复述下一行代码、不写行尾注释、不留被注释掉的死代码 / TODO / 占位符。

## 验证

**命令在 `aifei-http/` 根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                     # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest             # 仅跑架构门禁（10 例）
mvn test -Dtest=HttpConfigTest                       # 单用例：类名或 类名#方法名
mvn dependency:tree                                  # 断言：仅 aifei-all(provided) + junit(test)
```

demo（平级目录 `aifei-http-demo/`，端口 18120，离线可跑）：

```bash
cd ../aifei-http-demo
start-demo.bat        # 启动 demo（自带本地回环 /demo/echo）
stop-demo.bat         # 停止 demo
```

> **零联网纪律**：全部单测必须在不访问外网的环境下全绿——只允许 `com.sun.net.httpserver.HttpServer` 绑定 `127.0.0.1` 随机端口作回环端点，或注入 `internal.HttpTransport` 的伪实现。**禁秒级 `Thread.sleep` 堆叠**；超时用例的宽容余量写入 `docs/testing.md`。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置），并更新 `docs/testing.md`。
- 术语必须与 `docs/design.md` 统一：请求 HttpRequest / 响应 HttpResponse / 客户端 HttpClient / 拦截器 HttpInterceptor / 一次尝试 attempt / 资源名 resource / 幂等方法 / 传输层 transport / 装配插件 HttpPlugin。
- **跨模块术语**：出站资源名维度（`METHOD host path`）与 `aifei-sentinel` 入站维度（`全限定类名#方法名`）是**两个不同维度**，同表可并存；需按业务方法维度埋点时由 sentinel 侧在 `HttpInterceptor` 内自行二次映射，**本模块不做**。
