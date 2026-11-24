# aifei-excel-demo

`io.github.gollyhu:aifei-excel` 的**示例工程**（不发布）：用真实读写演示 Excel 三类能力——上传解析、一次性导出、流式导出。所有数据来自真实读写：`/parse` 真实解析上传文件，`/export-*` 真实生成可打开的 xlsx。

- HTTP 端口 **18150**（回环访问 `localhost`）
- 单页入口 `http://localhost:18150/excel/demo/page`（页面模板 `src/main/webapp/view/excel/index.html`，零 CDN）
- 依赖 `io.github.gollyhu:aifei-excel:1.0.0-SNAPSHOT` + `com.alibaba:easyexcel:3.3.2`（见文末「依赖引入」）

## 前置：先把 aifei-excel 装入本地仓库

`aifei-excel` 是库模块，需先在本仓库根目录执行（等价一行 `cd aifei-excel && mvn -q -DskipTests clean install`）：

```bat
cd aifei-excel
mvn -q -DskipTests clean install
```

## 启动 / 停止

```bat
start-demo.bat        :: 编译 + 打包 classpath + 启动（-Dundertow.port=18150）
stop-demo.bat         :: 按 18150 找监听进程并结束
```

启动后浏览器打开 `http://localhost:18150/excel/demo/page`。

## 端点全表（HTTP 恒 200，语义由信封 `code` 承载，`0` 成功）

下列路径逐字取自 `src/main/java/cn/hg/aifei/excel/demo/service/ExcelDemoService.java`（类级 `@Path("/excel/demo")` + 方法级 `@Path`）：

| 端点 | 用途 | 参数 / 返回 |
| --- | --- | --- |
| `/excel/demo/page` | 演示页视图，由 Enjoy 渲染 `webapp/view/excel/index.html` | 返回模板名 `/excel/index.html` |
| `/excel/demo/state` | 依赖状态快照 | `data = { easyExcelAvailable, version(3.3.2) }` |
| `/excel/demo/parse` | 上传解析：`Excel.read` 按行 SAX 解析、满 `batchSize` 回调 | POST multipart 字段名 `file`；`batchSize`（缺省 1000）；`data = { fileName, rows, batches, headers, sample(前 10 行) }` |
| `/excel/demo/export-once` | 一次性导出：`ExcelKit.write` 写 12 行示例 | 下载二进制 `aifei-excel-once.xlsx` |
| `/excel/demo/export-stream` | 流式导出：`Excel.writer` 会话分批 `write`（批 5000）后 `finish` | `rows`（缺省 50000，上限 500000）；下载二进制 `aifei-excel-stream.xlsx` |

快速验证（页面未就绪时可直接 curl；先用第二条命令把导出结果保存为当前目录的 `once.xlsx`，再作为上传样例）：

```bash
curl "http://localhost:18150/excel/demo/state"
curl "http://localhost:18150/excel/demo/export-once" -o once.xlsx
curl "http://localhost:18150/excel/demo/export-stream?rows=1000" -o stream.xlsx
curl -F "file=@once.xlsx" "http://localhost:18150/excel/demo/parse?batchSize=500"
```

## 依赖引入（pom.xml 节选）

```xml
<!-- 被演示的库本体：需先在 aifei-excel/ 执行 mvn -q -DskipTests clean install -->
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-excel</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 库模块以 optional 声明唯一内核，不向下游传递；消费方（本 demo）必须显式引入 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.2</version>
</dependency>
```

本 demo 是应用工程（非库）：框架内核 `cn.aifei:aifei-all:1.1.2` 以 `compile` 自带；无 Plugin、无配置键，全程离线。
