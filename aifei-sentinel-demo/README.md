# aifei-sentinel-demo

`aifei-sentinel` 的**最小接入示例**（**示例工程，不发布**）：官方同步 HIO（undertow）+ 一个 Just Service（`service` 包），现场演示 Sentinel 的**流控 / 熔断 / 热点参数**三种治理能力。

- 与 `aifei-websocket-demo` 结构对齐：`DemoApp` → `Aifei.start(DemoAppConfig)`，官方同步 HIO（`cn.aifei.demo.common.arch` 包）+ 一个 Just Service（`service` 包）。
- 业务代码零 Sentinel 类型：只注册 `SentinelInterceptor`、实现降级策略 `Out.fail(OutCode.RATE_LIMIT)`，规则与接入由 `aifei-sentinel` SDK 承担。
- 默认端口 80，主页面见下方「主页面与模式识别」。

## 端口

| 用途 | 端口 | 来源 |
| --- | --- | --- |
| HTTP（主页面 + 演示端点） | **80** | `start-demo.bat` / `start-demo-dashboard.bat` 的 `-Dundertow.port` |
| Sentinel 控制台（仅 Dashboard 模式） | 8858 | `start-dashboard.bat`（需自备 sentinel-dashboard jar） |

## 前置：先把 aifei-sentinel 装入本地仓库

`aifei-sentinel` 是库模块，需先安装到本地仓库（或按 [根 README 构建小节](../README.md#构建) 安装全部模块）：

```bat
cd ../aifei-sentinel
mvn -q -DskipTests clean install
cd ../aifei-sentinel-demo
```

## 运行

```bat
start-demo.bat              :: 本地模式启动 demo（默认端口 80），主页面 http://localhost:80/
start-dashboard.bat         :: 启动本地 Sentinel 控制台（端口 8858，账号 sentinel/sentinel），需自备 sentinel-dashboard jar
start-demo-dashboard.bat    :: Dashboard 模式启动 demo（控制台未起时会自动拉起并等待就绪）
stop-demo.bat               :: 按端口 80 找到监听进程并结束（与启动脚本共用 DEMO_PORT）
```

## 主页面与模式识别

启动后访问 <http://localhost:80/> 即进入 Web 演示页（根路径由 `SentinelPageService.index()` 提供）。

页面加载时请求 `GET /demo/state`，按配置文件里的 `sentinel.dashboard.server` **自动识别运行模式**，无需随启动脚本手动切换：

- **本地模式**：顶栏显示「本地模式」，不显示控制台链接；规则来自 `sentinel-rules.json`，改文件秒级生效。
- **Dashboard 模式**：顶栏显示「Dashboard 模式」，并出现 `http://127.0.0.1:8858` 控制台链接；规则由控制台推送。

三张卡分别调用 `/demo/index`（流控）、`/demo/slow`（熔断）、`/demo/detail?id=1`（热点参数），支持单次与连发 N 次，结果列表显示 `code` / `msg` / 耗时；`10006` 在流控与热点卡标为「限流」、在熔断卡标为「熔断降级」（两者共用业务码）。

## 端口被占用怎么办

启动脚本默认绑定 80。若 80 已被其它进程（IIS / Apache / Nginx 等）占用，脚本会**明确失败**：打印监听进程的 PID 与 `taskkill /f /pid <PID>` 命令，并以退出码 1 结束；**不会静默换端口、也不会谎报启动成功**。

两种处理方式：

1. 释放 80：按脚本提示执行 `taskkill /f /pid <PID>`，再重新运行启动脚本。
2. 指定备用端口（推荐，无需动系统服务）：

   ```bat
   set DEMO_PORT=18080
   start-demo.bat
   ```

   也可直接传参：`start-demo.bat 18080`（命令行参数优先于环境变量）。此时主页面为 `http://localhost:18080/`；`stop-demo.bat` 读取同一个 `DEMO_PORT`，按端口精确匹配 PID，不会误杀 8080 / 18080 等相近端口的进程。

## 演示端点

所有端点都是 GET，返回 `{"code":0,"msg":...,"data":...}`（HTTP 恒 200，语义由 `code` 承载；`0` 为成功，限流/熔断返回对应业务码）。

| 端点 | 治理类型 | 规则（`sentinel-rules.json`） |
| --- | --- | --- |
| `GET /demo/index` | 流控（QPS） | `demo.index`，`count=2.0`（每秒放行 2 次，超出限流） |
| `GET /demo/slow` | 熔断（RT） | `demo.slow`，`count=500.0`（RT>500ms 触发，接口实际 sleep 800ms） |
| `GET /demo/detail?id=N` | 热点参数限流 | `demo.detail`，`paramIdx=0`（即 `id`），`count=5.0`（同一 id 每秒 5 次，超出限流） |
| `GET /demo/state` | 模式来源 | 无对应规则，故不会被限流；页面靠它自动识别本地 / Dashboard 模式 |

## 现场演示脚本（约 1 分钟）

1. 打开主页面 <http://localhost:80/>，确认顶栏模式徽标（本地或 Dashboard）。
2. 流控卡点「单次请求」→ `code=0`（正常）；点「连发测试」超过 2 次/秒：前 2 次 `code=0`，之后标为「限流」。
3. 熔断卡连发 6 次（每次 ~800ms）：RT 超阈值后标为「熔断降级」，不再执行 `slow` 方法。
4. 热点卡连发 8 次（`id=1`）：前 5 次 `code=0`，之后标为「限流」。

> 压测可用 `ab -n 20 -c 5 http://localhost/demo/index` 之类工具快速触发限流。

## 两种运行模式（互斥）

由 `app-config*.txt` 是否配置 `sentinel.dashboard.server` 决定，规则来源不同：

| 模式 | 配置 | 规则来源 | 控制台 |
| --- | --- | --- | --- |
| 本地（默认） | `app-config.txt` | `sentinel-rules.json` 物理文件，`FileRefreshableDataSource` 秒级热读；改文件即时生效，无需重启 | 不需要 |
| Dashboard | `app-config-dashboard.txt` | 初始规则一次性加载，控制台推送并**合并回写** `sentinel-rules.json`（重启不丢） | 需先 `start-dashboard.bat`（端口 8858） |

切换：用 `start-demo-dashboard.bat`（自动拉起控制台）；若手动切配置，在启动命令上加 `-Ddemo.config=app-config-dashboard.txt`。注意 `start-demo.bat` 的首个参数是端口而非模式名。页面无需切换，打开时自动识别。

## 规则文件（`sentinel-rules.json`）

首次启动由 `src/main/resources/sentinel-rules.json` 复制到工作目录（根 `sentinel-rules.json`），本地模式改它秒级热生效。三类规则：

```json
{
  "flowRules":      [{"resource": "demo.index",  "grade": 1, "count": 2.0}],
  "degradeRules":   [{"resource": "demo.slow",  "grade": 0, "count": 500.0, "timeWindow": 10}],
  "paramFlowRules": [{"resource": "demo.detail", "grade": 1, "count": 5.0, "paramIdx": 0}]
}
```

`index` / `slow` / `detail` 方法标注了 `cn.hg.aifei.sentinel.annotation.SentinelResource`，资源名取注解值（如 `demo.index`），须与规则 JSON 的 `resource` 严格一致；未标注的方法（如 `state`）仍用默认「全限定类名#方法名」（见 `aifei-sentinel` 模块约定）。

## 依赖说明

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei-all` | `1.1.2` | aifei 框架（demo 自带） |
| `io.github.gollyhu:aifei-sentinel` | `1.0.0` | 本 SDK |
| `com.alibaba.csp:sentinel-transport-simple-http` | `1.8.8` | **仅 Dashboard 模式**需要（连接官方控制台） |
| `io.undertow:undertow-core` | `2.2.39.Final` | HIO 的 Web 容器 |
| `org.apache.logging.log4j:log4j-core` | `2.26.0` | 日志实现 |

注：`aifei-sentinel` 内 `sentinel-transport-simple-http` 为 `provided`（仅反射探测），Dashboard 模式由本 demo 显式引入。
