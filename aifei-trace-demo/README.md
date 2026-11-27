# aifei-trace-demo

`io.github.gollyhu:aifei-trace` 的**示例工程**（**示例工程，不发布**）：全程离线演示链路贯通能力——TraceId 绑定与 `finally` 清理、日志 `[traceId] ` 前缀、线程池手动传递、请求头提取与回退生成。

- HTTP 端口 **18130**（回环访问 `localhost`），零外部服务、零外网、零 CDN
- 单页入口 `http://localhost:18130/`（根路径经跳转页进入 `/trace/demo/page`；页面模板 `src/main/webapp/view/demo/index.html`，本地 Vue）
- 依赖 `cn.aifei:aifei:1.1.2` + `io.github.gollyhu:aifei-trace:1.0.0-SNAPSHOT`（均以 `compile` 自带）+ `io.undertow:undertow-core:2.2.39.Final`；零日志绑定（demo 自带 `ConsoleLogFactory`）

## 前置：先把 aifei-trace 装入本地仓库

`aifei-trace` 是库模块，需先安装到本地仓库（或按 [根 README 构建小节](../README.md#构建) 安装全部模块）：

```bat
cd ../aifei-trace
mvn -q -DskipTests clean install
cd ../aifei-trace-demo
```

## 启动 / 停止

```bat
start-demo.bat    :: 编译 + 组 classpath + 启动（HTTP 18130），按 Ctrl+C 或运行 stop-demo.bat 结束
stop-demo.bat     :: 按端口 18130 找到监听进程并结束
```

脚本做三步：`mvn compile dependency:build-classpath` → 读 classpath → 以 `-Ddemo.config=app-config.txt -Dundertow.port=18130 -Dundertow.host=0.0.0.0` 启动 `cn.hg.aifei.trace.demo.DemoApp`。

启动后访问 `http://localhost:18130/`（自动进入 `/trace/demo/page`）即为正常。

## 演示端点（全部 GET，HTTP 恒 200，语义由信封 `code` 承载，`0` 成功）

端点逐字取自 `src/main/java/cn/hg/aifei/trace/demo/service/TraceDemoService.java`（类级 `@Path("/trace/demo")`）。

| 端点 | 作用 |
| --- | --- |
| `/trace/demo/page` | 演示页视图，由 Enjoy 渲染 `webapp/view/demo/index.html` |
| `/trace/demo/state` | 装配快照：提取头、生成器类、日志工厂链、Handler 链、线程池 |
| `/trace/demo/current` | 场景①：当前请求的 traceId / spanId（每次新请求 traceId 都变） |
| `/trace/demo/span` | 场景①补充：`startSpan()` 覆盖 spanId、不影响 traceId（单层 span） |
| `/trace/demo/log` | 场景②：触发 4 条业务日志，验证 `[traceId] ` 前缀贯通 + 控制台回显 |
| `/trace/demo/log/clear` | 清空日志回显缓冲，便于下一次演示从零观察 |
| `/trace/demo/async` | 场景③：线程池手动传递（`TraceKit.init(parentTraceId)` + `finally clear`）三断言 |
| `/trace/demo/extract` | 场景④：从请求头 `X-Trace-Id` 提取，空白 / 缺失回退生成 32 位小写十六进制 |

演示要点：`current` / `extract` 的 traceId 每次请求都不同；`async` 断言子线程 traceId 与父线程相同、spanId 为 `null`（ThreadLocal 刻意不继承）、`clear()` 后回到 `null`；`log` 的控制台行同步出现在 System.out。

## 配置出处

- 本 demo 只有一个自定义键 `enjoyDevMode=true`（模板热加载），见 `src/main/resources/app-config.txt`。
- aifei-trace 为**零配置键**模块：链路装配全部在 `DemoAppConfig` 中显式完成（`settings.setLogFactory(new TraceLogFactory(...))` 与 `TraceHandler` 第一个 `addHandler`），详见 [aifei-trace README](../aifei-trace/README.md)。
- HTTP 端口不在配置文件里，由启动脚本的 `-Dundertow.port=18130` 传入。
