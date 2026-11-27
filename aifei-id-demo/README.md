# aifei-id-demo

`io.github.gollyhu:aifei-id` 的**示例工程**（**示例工程，不发布**）：全程离线演示雪花与号段两种发号策略，覆盖单号生成、并发生重、号段换段与装配状态四个场景。

- HTTP 端口 **18140**（回环访问 `localhost`），零外部服务、零 CDN
- 单页入口 `http://localhost:18140/`（根路径经跳转页进入 `/id/demo/page`；页面模板 `src/main/webapp/view/id/index.html`，本地 Vue）
- 依赖 `io.github.gollyhu:aifei-id:1.0.0-SNAPSHOT` + `cn.aifei:aifei:1.1.2`（均以 `compile` 自带）+ `io.undertow:undertow-core:2.2.39.Final`；零日志绑定（demo 自带 `ConsoleLogFactory`）

## 前置：先把 aifei-id 装入本地仓库

`aifei-id` 是库模块，需先安装到本地仓库（或按 [根 README 构建小节](../README.md#构建) 安装全部模块）：

```bat
cd ../aifei-id
mvn -q -DskipTests clean install
cd ../aifei-id-demo
```

## 两个启动脚本（互斥，勿同时运行）

本 demo 提供两份启动脚本，**共用 18140 端口，同一时刻只能运行一个**；切换前先用 `stop-demo.bat` 停掉正在运行的那个。

| 脚本 | 策略 | 配置文件 | 关键配置 |
| --- | --- | --- | --- |
| `start-demo.bat` | 雪花（`1+41+10+12`） | `app-config.txt` | `id.strategy=snowflake`、`id.worker.source=config`、`id.workerId=1` |
| `start-demo-segment.bat` | 号段 + 本地存储 | `app-config-segment.txt` | `id.strategy=segment`、`id.segment.step=200`、`id.segment.store=local` |

```bat
start-demo.bat           :: 雪花模式启动（HTTP 18140），按 Ctrl+C 或运行 stop-demo.bat 结束
start-demo-segment.bat   :: 号段模式启动（HTTP 18140），与上一脚本互斥
stop-demo.bat            :: 按端口 18140 找到监听进程并结束
```

两个脚本都做三步：`mvn compile dependency:build-classpath` → 读 classpath → 以 `-Ddemo.config=<配置文件> -Dundertow.port=18140 -Dundertow.host=127.0.0.1` 启动 `cn.hg.aifei.id.demo.DemoApp`。

启动后访问 `http://localhost:18140/` 即为正常（根路径经跳转页进入 `/id/demo/page`）。

> 号段模式用 `local` 存储，进程重启会从 `[1, step]` 重新取段、**与重启前的号重叠**，仅限单实例 / 开发测试；生产多实例场景请改用 `cache` 号段存储与 `cache` / `lock` workerId 来源（见 [aifei-id README](../aifei-id/README.md)）。

## 演示端点（全部 GET，HTTP 恒 200，语义由信封 `code` 承载，`0` 成功）

端点逐字取自 `src/main/java/cn/hg/aifei/id/demo/service/IdDemoService.java`（类级 `@Path("/id/demo")`）。

| 端点 | 作用 | 参数 / 返回 |
| --- | --- | --- |
| `/id/demo/page` | 演示页视图，由 Enjoy 渲染 `webapp/view/id/index.html` | 返回模板名 `/id/index.html` |
| `/id/demo/state` | 装配状态快照 | `data = { installed, strategy, workerSource, workerId, segmentStore, step }` |
| `/id/demo/next` | 场景①：单号生成 | `data = { id, firstSeen }`；`id` 为**字符串**（19 位 long 在 JS 中会丢精度） |
| `/id/demo/batch?size=2000` | 场景②：8 线程并发生成 + 去重计数 | `data = { size, unique, duplicated, first, last, costMillis }`；`duplicated` 恒为 0 |
| `/id/demo/segment?size=450` | 场景③：号段耗尽与换段过程 | `data = { step, generated, segments:[{index,start,end,count}], currentSegment }` |

页面要展示的 strategy / workerId / 段边界均由 **demo 自身配置或 ID 值自算**，不是 aifei-id 提供的状态查询 API（模块无状态查询能力）。

## 配置出处

- `id.*` 键（`id.strategy` / `id.worker.source` / `id.workerId` / `id.segment.step` / `id.segment.store`）的含义与合法取值见 [aifei-id README 配置节](../aifei-id/README.md)；解析与校验在 `IdPlugin.start()` 完成，配置有误即应用启动失败。
- 非 `id.*` 的演示开关 `enjoyDevMode=true` 仅控制模板热加载。
- HTTP 端口不在配置文件里，由启动脚本的 `-Dundertow.port=18140` 传入。

## 停止

```bat
stop-demo.bat    :: 按端口 18140 找到监听进程并结束
```
