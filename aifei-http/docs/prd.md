# PRD：aifei-http（出站 HTTP 客户端 SDK）

> 坐标与基线：`io.github.gollyhu:aifei-http:1.0.0-SNAPSHOT`；包名 `cn.hg.aifei.http`；Java 8（class major 52）。
> 公共 API 与契约细节由 `aifei-http/docs/design.md` 定稿。

## 一、缺口与目标

`aifei-all` 只有**入站** HTTP（`aifei-undertow` + HIO），**出站** HTTP 无任何构件（排期表 §二 L19）。业务侧的替代都不成立：裸 `HttpURLConnection` 各写各的超时与读流、`okhttp` 违反本仓库「零第三方公共面」口径、`aifei-weixin` 的 `core.http.JdkHttpClient` 是**模块私有实现且与微信业务耦合**（抛 `WxApiException`、写死流程），不能复用到通用出站调用。

本模块补齐**出站 HTTP 统一客户端**：业务只构造请求 + 读响应，超时、重试、拦截、编解码、资源名由模块统一负责。

**四个目标（对应四件事）**：

| # | 目标 | 具体含义 |
| --- | --- | --- |
| 1 | **统一超时** | connect / read 两段超时全局有默认、可逐请求覆盖；不在业务代码里散落 `setConnectTimeout` |
| 2 | **统一重试** | **默认 0 次、必须显式开启**；默认仅对幂等方法（GET / HEAD）开放重试入口；重放风险由模块用默认值挡住 |
| 3 | **统一拦截** | 拦截器链统一埋点与横切（鉴权头、日志、指标、`aifei-sentinel` 流控），业务代码零感知 |
| 4 | **统一编解码** | 请求体 / 响应体的 charset 与 Content-Type 解析、`String` 与 `byte[]` 双向、响应头与耗时一次性可见 |

**与 `aifei-sentinel` 的协同诉求**：出站调用的流控 / 熔断需要稳定的**资源名**作为埋点维度。模块必须以统一口径产出资源名（见 §5.1），并在发送前、重试各次、成功与失败路径上取到同一个值，供 sentinel 侧 `HttpInterceptor` 读取后自行埋点。**模块零 `aifei-sentinel` 依赖**，只提供值，不提供埋点。

## 二、非目标（能力边界）

| # | 边界 | 后果（README 与 `design.md` 必须声明） | 模块责任 |
| --- | --- | --- | --- |
| 1 | **依赖口径 = 零第三方纯 JDK** | 不引入 okhttp、不设 provider 后端扩展点；传输实现只用 `HttpURLConnection`（Java 8 无 `java.net.http.HttpClient`，不存在第二条纯 JDK 路径） | 传输层保留**仅供单测注入的内部接口**，**不作对外扩展点**，不进入根包公共面 |
| 2 | **不支持 HTTP/2** | 只能与 HTTP/1.1 端点互通；对仅 HTTP/2 的服务不可用 | 不尝试升级、不做协议协商 |
| 3 | **连接池受 JVM 全局属性约束、不可按实例调参** | `http.maxConnections` / `http.keepAlive` 是 JVM 级全局值，无法按 `HttpClient` 实例调整；**模块不得改写这些全局属性**（会波及同进程的 `aifei-weixin` 与其余 JDK HTTP 使用方） | 只读不写；门禁断言源码无 `System.setProperty` / `System.getProperties().put` |
| 4 | **超时仅 connect / read 两段** | 无整体 deadline、**不可取消在途请求**；慢响应只能等 read 超时逐段触发 | 不伪造 deadline、不引入看门狗线程 |
| 5 | **gzip / Cookie / 重定向需模块自行实现** | 不依赖三方库；默认行为见 §5.5 | 自行实现或明确不做 |
| 6 | **不做 provider 扩展点、不做后端切换** | 无 SPI、无「换 okhttp」开关 | 唯一实现；内部注入点只服务单测 |

## 三、需求约束（设计不得推翻）

| # | 前提 | 需求含义 |
| --- | --- | --- |
| 1 | 纯 JDK 传输 | 见 §二 第 1 条；技术底稿复用 `aifei-weixin` 的 `core.http.JdkHttpClient` / `pay.JdkPayHttpClient`（同路线已验证：multipart、文件下载、mTLS） |
| 2 | **根包公共 API 精确 6 个类型**（门禁断言） | `HttpClient` / `HttpRequest` / `HttpResponse` / `HttpKit` / `HttpException` / `HttpInterceptor`。**不得增删、不得改名**；嵌套类型不单独计数；新增一律先过设计决策并回写本文 |
| 3 | **资源名规则** | `HttpRequest` 可显式设 `resource`；未设时默认 `METHOD + " " + host + path`（**去 query**）；**唯一拼装点** `internal.HttpResource`；模块零 `aifei-sentinel` 依赖 |
| 4 | **装配形态与 `MqPlugin` / `JobPlugin` 同构** | 提供 `cn.hg.aifei.http.aifei.HttpPlugin`；配置前缀 `http.*`；`@Inject HttpClient` 取用；`HttpKit` 为门面 |
| 5 | **`Kit` / `Util` 命名口径** | `HttpKit` = **有状态门面**（依赖 `HttpPlugin.start()` 完成装配，未装配 / 已停止时调用即失败）；纯静态工具类才可用 `Util` 后缀（根 `AGENTS.md`「命名约定」） |
| 6 | **重试默认 0 次、必须显式开启**，默认仅对幂等方法（GET / HEAD）开放重试入口 | 出站 HTTP 隐式重放可能造成重复下单 / 重复扣款，属**高危默认值**，不得给「开箱重试」 |
| 7 | **`aifei-http-demo` 出 1 页演示页面** | 前端直接出功能完整单页（不走 PM 原型与 QA 基线门禁），对接 demo 自身真实端点（见第八节） |

## 四、功能需求

| 需求编号 | 能力 | 说明 |
| --- | --- | --- |
| `R-01` | 请求构造 | 方法（至少 GET / POST / PUT / DELETE / HEAD）、URL、query 参数（自动 URL 编码，中文正确）、请求头（含多值与覆盖）、请求体（`String` / `byte[]`）、可选显式 `resource` |
| `R-02` | 发送与响应 | 返回 `HttpResponse`：状态码、响应头（大小写不敏感、多值可枚举）、响应体（`String` 按 charset 解码 / `byte[]` 原样）、耗时（毫秒）、本次尝试次数 |
| `R-03` | 超时 | connect / read 两段，全局默认 + 逐请求覆盖；超时抛 `HttpException`（cause 保留底层异常） |
| `R-04` | 重试 | 默认 **0** 次；显式开启后按次数重试；触发条件与退避见 §5.3；重试对业务代码透明（业务只看到最终结果与尝试次数） |
| `R-05` | 拦截器链 | 应用侧注册的 `HttpInterceptor` 链，顺序确定、异常不被吞、可中断（见 §5.2）；每次尝试完整穿链 |
| `R-06` | 资源名 | 默认推导与显式覆盖，唯一拼装点 `internal.HttpResource`，重试各次恒定（见 §5.1） |
| `R-07` | 编解码 | 请求 / 响应 charset 与 Content-Type 解析；文本与二进制双向；中文 / emoji 往返一致（见 §5.4） |
| `R-08` | 装配与门面 | `HttpPlugin`（配置前缀 `http.*`）完成 `@Inject HttpClient` 注册与 `HttpKit` 静态引用写入；`stop()` 清除；重复 start/stop 幂等；配置非法启动即失败 |
| `R-09` | 响应体保护 | 单次响应体读取上限（防大响应 OOM），超限抛 `HttpException`（默认值 `8 MiB`） |

**配置能力诉求**（键名与默认值由 `design.md` 定稿）：

| 能力 | 建议默认 | 约束 |
| --- | --- | --- |
| connect 超时（毫秒） | `5000`（对齐 `aifei-weixin` 底稿） | ≥ 1；`0` / 负数启动失败 |
| read 超时（毫秒） | `10000` | 同上 |
| 重试次数 | `0` | ≥ 0；非法值启动失败 |
| 重试间隔（毫秒） | 可配固定间隔 | ≥ 0 |
| 响应体上限（字节） | `8 * 1024 * 1024` | ≥ 1 |
| 是否接受 gzip 响应 / 是否自动跟随重定向 / 是否保存 Cookie | 全部**默认关闭** | 见 §5.5 |
| 默认请求头（如 User-Agent） | 可配 | 逐请求可覆盖 |

**P1（本次不做）**：连接池按实例调参、HTTP/2、provider 与后端切换、整体 deadline 与在途取消、异步 / 回调 / Future 式调用、multipart 与文件上传下载的高层封装（需要时业务自行拼 `byte[]` 请求体）、Cookie 持久化容器、重定向的完整语义矩阵（303 / 307 / 308）、代理认证、mTLS 高层配置、熔断与限流（属 `aifei-sentinel`）、消息级压缩之外的编码（如 zstd / br）。

## 五、关键契约

### 5.1 资源名（`aifei-sentinel` 埋点维度）

| 项 | 约定 |
| --- | --- |
| 默认值 | `METHOD + " " + host + path`，**去 query**、去 fragment；例：`GET api.example.com/v1/orders` |
| 显式覆盖 | `HttpRequest` 可设 `resource`；已设则**完全替代**默认推导，不再拼接 |
| 可读时机 | 发送前、每次尝试、成功、失败（异常）路径上取值**恒等**；重试不改变资源名（同一逻辑请求 = 同一资源） |
| 唯一拼装点 | `internal.HttpResource`；其余类不得出现同时使用 `getHost()` 与 `getPath()` 的资源名推导 |
| 跨模块口径 | 出站维度 `METHOD host path` 与 `aifei-sentinel` 入站维度「全限定类名#方法名」是**两个不同维度**，同表可并存；需按业务方法维度埋点时，由 sentinel 侧在 `HttpInterceptor` 内自行二次映射，**模块不做**。此差异须在 `design.md` 与 sentinel 文档交叉声明（根 `AGENTS.md`：跨模块术语一致） |

### 5.2 拦截器链语义

| 项 | 约定 |
| --- | --- |
| 顺序 | 按注册顺序进入（正序）；收尾按**逆序**（保证资源释放对称，如 sentinel `entry.exit()` 配对） |
| 可见信息 | 进入时已可读最终资源名、方法、URL（不含 query 的资源名即可用）、尝试序号 |
| 可中断 | 任一拦截器抛 `HttpException`（或运行期异常）→ **后续拦截器不再进入**、**真实发送不发生**；异常原样向调用方传播，不被吞、不被替换成别的类型 |
| 异常安全 | 拦截器内部异常与传输层异常走同一异常口径（`HttpException`）；模块不捕获后静默继续 |
| 线程 | 同步执行在发起调用的线程上，不引入额外线程 |
| 短路 | v1 **不支持**「拦截器直接合成响应并跳过发送」（最小面；sentinel 降级用抛异常已够） |

### 5.3 超时与重试语义

| 场景 | 约定 |
| --- | --- |
| connect 超时 | 建连阶段超过 connect 超时 → `HttpException`，`cause` 为底层 `IOException`（如 `ConnectException` / `SocketTimeoutException`） |
| read 超时 | 读响应阶段无数据超过 read 超时 → `HttpException` |
| 无整体 deadline | 一次请求总耗时上限 = 建连 + 若干段 read，**不做**总时长兜底、**不可**取消在途请求（README 显式声明） |
| 默认重试 | `0`：一次尝试，失败即抛 |
| 重试入口 | 仅幂等方法（GET / HEAD）可开启；未显式声明幂等的 POST / PUT / DELETE / PATCH **不提供**重试入口（配置或 API 层拒绝，不得静默忽略） |
| 重试触发条件 | 仅**网络层失败**（连接拒绝 / 超时 / 读流中断）与 **5xx** 重试；**4xx 不重试**（确定性客户端错误，重放无意义） |
| 退避（默认假设） | v1 为可配**固定间隔**，不做指数退避与抖动 |
| 观测 | 最终 `HttpResponse` / `HttpException` 必须能给出「本次共尝试几次」 |

### 5.4 编解码

| 项 | 约定 |
| --- | --- |
| 请求文本体 | 按请求头 `Content-Type` 的 charset 编码，缺省 UTF-8 |
| 响应文本体 | 按响应头 `Content-Type` 的 charset 解码，缺省 UTF-8；无 `Content-Type` 时按 UTF-8 |
| 二进制 | `byte[]` 原样进出，不做任何转换（字节级相等） |
| 响应头 | 大小写不敏感查找；同名多值可枚举（`Set-Cookie` 等） |
| 失败 | 解码不可恢复 → `HttpException`，不得静默返回半截字符串 |

### 5.5 默认行为（gzip / Cookie / 重定向）

| 项 | 默认 | 显式开启后 |
| --- | --- | --- |
| gzip | **不发送** `Accept-Encoding: gzip`，不自行解压 | 可发送并在模块内解压（`GZIPInputStream`），解压失败抛 `HttpException` |
| Cookie | 不保存、不回传（无 Cookie 容器） | 业务经请求头自行管理 |
| 重定向 | **不自动跟随**（`setInstanceFollowRedirects(false)`），3xx 原样作为 `HttpResponse` 返回 | v1 不做完整跟随矩阵（见 §四 P1） |

## 六、非功能与硬约束

| 项 | 要求 |
| --- | --- |
| Java 8 | `source` / `target` 均 `1.8`；主 JAR 内全部 class major **52**；无 Java 8 后语法 / API |
| 公共 API 实现无关 | 根包 6 类型零 `cn.aifei.*`、零第三方类型、零 `HttpURLConnection` / `URL` 等 JDK 传输类型出现在签名中（`String` / `byte[]` / `Map` / 基本类型为主） |
| 依赖 | `cn.aifei:aifei-all` **provided**（不传递 → 消费方必须自带，README 声明）；**零第三方** compile / runtime 依赖；不引入任何日志绑定；模块自身记录经 `cn.aifei.log.Log`（惰性获取，晚于 `LogFactory` 装配） |
| 零 DB | 全模块禁 `import java.sql.`；不依赖 `aifei-db` |
| 唯一定义点 | 资源名推导（`internal.HttpResource`）、默认值常量（配置域）、超时应用、重试判定（幂等白名单 + 触发条件）、拦截器链装配顺序、异常构造与消息格式；实现类不得复制 |
| **架构门禁 `ArchitectureBoundaryTest`**（源码文本扫描，在模块根目录执行，不得放宽） | ① 根包**顶层**公共类型精确 6，名单与 §三-2 完全一致；② 全模块禁 `java.sql.`；③ `cn.aifei.*` 只允许出现在 `aifei` 接入包（`cn.aifei.log.*` 同理按模块口径限制在接入包与 `internal` 的记录点，具体白名单由 `design.md` 定）；④ 禁 `System.setProperty` / `System.getProperties().put` 且不得出现 `http.maxConnections` / `http.keepAlive` 写入；⑤ 资源名推导只出现在 `internal.HttpResource`；⑥ 接入包存在 `HttpPlugin`；⑦ 根包源码不得 import `java.net.HttpURLConnection` / `URL`（传输细节不外溢） |
| **零联网单测**（硬约束） | 「零联网」= **不访问外网、不依赖任何外部服务**；允许 JDK 自带 `com.sun.net.httpserver.HttpServer` 绑定 `127.0.0.1` 随机端口作回环端点，允许注入内部传输接口的伪实现。用例须在断网环境全绿 |
| 测试不得靠长等待 | 超时用例的宽容余量写入 `docs/testing.md`；禁秒级 `Thread.sleep` 堆叠 |
| 文档四件套 | `README.md`（用法与能力边界声明）、`docs/design.md`（契约事实来源，含公共面 6 类型定稿）、`docs/testing.md`（测试策略与用例清单）、模块 `AGENTS.md`（开发约束） |
| 构建 | 模块目录内 `mvn clean verify` 0 failure / 0 error |

## 七、验收标准（AC，门禁输入）

每条均**可机械验证**：能被单测、门禁断言或 `mvn dependency:tree` / `javap` 类命令固定判定的。

| AC 编号 | 对应需求 | 判定条件（可度量） | 验证方式 |
| --- | --- | --- | --- |
| `AC-R-01` | §三-2 | 根包 `cn.hg.aifei.http` 顶层 public 类型集合**恰好等于** `{HttpClient, HttpRequest, HttpResponse, HttpKit, HttpException, HttpInterceptor}`（多一个或少一个即失败；嵌套类型不计数） | 门禁单测 `ArchitectureBoundaryTest#rootTypesExactlySix` + 编译产物反射扫描 |
| `AC-R-02` | §六 依赖 | `mvn dependency:tree` 中除 `cn.aifei:aifei-all`（provided）外**无任何第三方** compile / runtime 依赖；源码 import 仅出现 `java.*`、根包 / `internal`、`cn.aifei.*`（限 `aifei` 接入包） | `dependency:tree` 断言 + 门禁源码扫描 |
| `AC-R-03` | §六 Java 8 | 主 JAR 内全部 class major == 52；源码无 `var` / `record` / `switch` 表达式 / text block / `List.of(` / `Map.of(` | 构建后 `javap -verbose` 批量断言 + 门禁正则扫描 |
| `AC-R-04` | `R-04` 默认不重试 | 回环端点固定返回 5xx 并计数；**未显式开启重试**时一次调用后端点计数 == **1**，调用方收到失败 | 集成测试（回环端点计数器） |
| `AC-R-05` | `R-04` 幂等重试入口 | GET + 重试 2 次 + 端点前 2 次 503、第 3 次 200 → 端点计数 == **3**，最终返回 200，且调用方读到的尝试次数 == **3** | 集成测试 |
| `AC-R-06` | `R-04` 非幂等限制 | 未显式声明幂等的 POST 在端点持续 5xx 时：**无法开启重试**（配置 / API 层拒绝并抛出，不得静默忽略），端点计数 == **1** | 单元测试（拒绝路径）+ 集成测试（计数） |
| `AC-R-07` | `R-03` connect 超时 | 连接本机未监听端口：抛 `HttpException`，`getCause()` 为 `java.net.ConnectException`（或等价 `IOException`），总耗时 ≤ connectTimeout + 宽容余量 | 集成测试（耗时断言 + 异常链） |
| `AC-R-08` | `R-03` read 超时 | 回环端点延迟 > readTimeout：抛 `HttpException`，耗时 ≥ readTimeout 且 ≤ readTimeout + 宽容余量 | 集成测试 |
| `AC-R-09` | `R-03` 超时可覆盖 | 全局 read 超时 5000、单请求设 100 → 端点延迟 150 时按 100 触发超时（证明逐请求覆盖生效，不是全局值） | 集成测试 |
| `AC-R-10` | `R-05` 链顺序 | 注册 A / B / C 三个记录型拦截器：进入顺序 `A→B→C`，收尾顺序 `C→B→A`，各恰好一次 | 单元测试（记录列表断言） |
| `AC-R-11` | `R-05` 可中断 | B 抛 `HttpException`：C 未进入、回环端点计数 == **0**（真实发送未发生）、调用方收到的异常类型与 B 抛出的一致（未被吞、未被替换） | 单元测试 + 集成测试（计数为 0） |
| `AC-R-12` | `R-06` 默认资源名 | `GET http://api.example.com/v1/orders?x=1&y=2` 发送（伪传输层）→ 拦截器读到资源名 == **`GET api.example.com/v1/orders`**（含 method 前缀、去 query） | 单元测试（固定字面量期望值，不用实现推导） |
| `AC-R-13` | `R-06` 显式覆盖 | 请求设 `resource = "order.query"` → 拦截器读到 `order.query`；默认推导不参与拼接 | 单元测试 |
| `AC-R-14` | `R-06` 恒定性与单点 | 重试 3 次尝试中拦截器读到的资源名**完全相同**；异常路径与成功路径取值相同；门禁断言资源名推导只在 `internal.HttpResource`（其余源码不得同时使用 `getHost()` 与 `getPath()`） | 集成测试 + 门禁源码扫描 |
| `AC-R-15` | §5.3 异常口径 | 连接拒绝 / connect 超时 / read 超时 / 非法 URL / 响应解码失败 → 抛出的均为 `HttpException`（或其子类，`extends RuntimeException`），无受检异常外泄，底层异常在 `getCause()` | 单元测试 + 集成测试 |
| `AC-R-16` | §5.3 非 2xx | 端点返回 500 + 响应体：调用**正常返回** `HttpResponse`，`statusCode == 500`、响应体可读；返回 404 时即便已开启重试也**不重试**（端点计数 == 1） | 集成测试 |
| `AC-R-17` | `R-08` 装配 | `HttpPlugin.start()` 后 `@Inject HttpClient` 非 null、可发请求；`stop()` 后 `HttpKit` 调用抛 `HttpException`（不静默返回）；重复 `start()` / `stop()` 幂等、无异常 | 集成测试（容器装配 + 静态引用） |
| `AC-R-18` | `R-08` 配置非法 | 超时为 `0` / 负数、重试次数为负、响应体上限为 `0` → 启动失败抛 `HttpException`，消息含**键名与原值** | 单元测试 |
| `AC-R-19` | §三-3 边界 / §二-3 | 用例执行前后 `System.getProperty("http.maxConnections")` 与 `System.getProperty("http.keepAlive")` 取值**不变**；门禁断言源码无对这两个键的写入、无 `System.setProperty` / `System.getProperties().put` | 单元测试（快照比对）+ 门禁源码扫描 |
| `AC-R-20` | §六 零联网 | 全模块单测在**断网环境**（或仅回环可见）通过；测试源码中出现的 URL 字面量 host 只能是 `127.0.0.1` / `localhost`（门禁扫描） | 门禁源码扫描 + CI 断网跑 `mvn clean verify` |
| `AC-R-21` | `R-07` 编解码 | 中文 + emoji 的文本体往返一致；`Content-Type: text/plain; charset=GBK` 响应按 GBK 解码（与 UTF-8 结果不同，证明读到了 charset）；二进制 `byte[]`（含 0x00 / 0xFF）往返**字节级相等** | 单元测试 + 集成测试 |
| `AC-R-22` | `R-02` 响应可观 | `HttpResponse` 可读：`statusCode`、响应头（大小写不敏感查找、`Set-Cookie` 多值可枚举）、响应体（`String` 与 `byte[]`）、耗时毫秒（≥ 0 且与端点延迟同数量级）、尝试次数 | 集成测试 |
| `AC-R-23` | `R-01` 请求构造 | query 参数含中文 / 空格 / `&` 时编码正确（回环端点回显 query 与期望串相等）；自定义请求头在端点侧可见；`byte[]` 请求体字节级到达 | 集成测试（端点回显） |
| `AC-R-24` | §5.5 默认行为 | 默认不发送 `Accept-Encoding: gzip`；端点返回 302 时不自动跟随（`statusCode == 302` 原样返回）；显式开启 gzip 后收到 gzip 响应体可正确解压为原文本 | 集成测试 |
| `AC-R-25` | `R-09` 响应体上限 | 端点返回超过上限的响应体 → 抛 `HttpException`，消息含实际上限值；未超限正常返回 | 集成测试 |
| `AC-R-26` | §六 零 DB | 全模块源码无 `java.sql.`、无 `aifei-db` 依赖；接入包存在 `HttpPlugin` | 门禁源码扫描 + `dependency:tree` |

**AC 与门禁映射**：`AC-R-01` / `AC-R-02` / `AC-R-03` / `AC-R-14`(后半) / `AC-R-19`(后半) / `AC-R-20` / `AC-R-26` 由 `ArchitectureBoundaryTest` 承载；其余由契约 / 集成用例承载。

## 八、`aifei-http-demo` 场景清单

单页演示（前端直接实装，不走原型门禁），**必须离线可跑**：demo 自带**本地回环 echo 端点**（HIO 路由，如 `POST /demo/echo`），支持「回显 query / 请求头 / 请求体」「指定状态码」「延迟 N 毫秒」「前 N 次返回 5xx」「gzip 压缩响应」「重定向到自身」六种可控行为；页面**不得**请求任何外网地址。

| # | 场景 | 必须可见的演示点 |
| --- | --- | --- |
| 1 | 请求构造 | 方法下拉、URL、query 键值表、请求头键值表、请求体文本、显式 `resource` 输入框；发送前展示「即将发出的请求」（方法 / 完整 URL / 头 / 体） |
| 2 | 发送与响应 | 状态码、总耗时（ms）、尝试次数、响应头表格（多值展开）、响应体（原文 / JSON 格式化切换）、收到的 `resource` |
| 3 | 重试 | 重试次数开关；echo 侧设为「前 2 次 500」→ 展示尝试次数 3、每次尝试的状态与耗时；**默认关闭时的对照**（尝试 1 次即失败） |
| 4 | 超时 | connect / read 超时输入；echo 侧设延迟 → 展示 `HttpException` 类型、消息、耗时，与「未超时」形成对照 |
| 5 | 拦截器与资源名 | 页面展示已注册拦截器顺序与本次请求的穿链记录（进入 / 收尾、读到的资源名）；默认推导与显式覆盖两列对照 |
| 6 | 空态 | 首次进入、未发送时结果区显示占位文案，**不残留**上一次结果 |
| 7 | 错误态 | 错误面板：异常类型、消息、`getCause()`、耗时、尝试次数；与成功态视觉可区分 |
| 8 | 连接池边界（只读展示） | 显示当前 JVM 的 `http.maxConnections` / `http.keepAlive` 取值供观察，页面**只读不改** |

