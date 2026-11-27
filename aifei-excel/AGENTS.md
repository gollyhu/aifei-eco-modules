# aifei-excel 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档分工：`README.md` 面向使用者（安装、用法、可选依赖警告）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 开发前提

- **改代码前必读 `docs/design.md`**（尤其 §5 的 5 个公共类型逐字签名、§7.5 的 13 条异常语义表、§9 的 14 条门禁断言）。需求与设计冲突时，**先改设计决策再改代码**。
- 未经设计决策，不得自行确定契约类行为（空值语义、异常类型与消息、批大小默认值、表头匹配规则、流所有权等），也不得新增或扩大公共 API 面。
- 发现设计问题只回报主会话，不在实现期自行扩张或改写 `docs/design.md`。

## 交付性质（库模块，非业务应用）

`aifei-excel` 是 aifei 生态的**库模块（SDK，可被应用依赖）**，**不是业务应用工程**：没有 `AppConfig` / `Launcher` / `@Path` Service / `service/` 业务包 / `webapp/` / HTTP 路由 / 表结构 / `Db+Row` / `Generator` / Plugin / 配置键。坐标 `io.github.gollyhu:aifei-excel:1.0.0-SNAPSHOT`（`packaging=jar`），根包**精确 5 公共类型** + `internal.easyexcel` 非承诺适配层同处一个 JAR。

**不适用章节（不得据此模块约束要求）**：

| 模板章节 | 结论 | 理由 |
| --- | --- | --- |
| 业务服务 / 子系统拓扑 | 不适用 | 单一 JAR 库，只分「根包公共 API / internal 适配层」两个技术层 |
| 业务库表 / `Db+Row` / `Generator` / `Model` | 不适用 | 零 DB，与数据仅经 `InputStream` / `OutputStream` 交互；门禁③反向断言 |
| 服务拓扑 / `@Path` / HIO / 路由 / 拦截器 | 不适用 | 库模块本体无入站 HTTP 面；真实端点只在 `aifei-excel-demo` |
| 部署架构 / 容量规划 | 不适用 | 随使用方应用部署；内存边界以 API 契约表达 |
| 数据层 / 前端页面设计交付 | 不适用 | 无数据层；demo 单页由前端直接实装，不经原型与 QA 基线门禁 |
| `AppConfig` / `Launcher` / Plugin / 配置键 | 不适用 | 无连接资源、无配置可解析；全部行为由方法入参显式传入 |

## 硬性约束

### 技术基线

- **Java 8 双约束（语法 + API）**：`source`/`target` = `1.8`；主 JAR 内全部 class major = **52**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / `instanceof` 模式匹配；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 后 API。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、协议字面量与异常消息）；原创文件写统一版权头，既有版权块不得改写。
- **行尾**：`.md` 输出 CRLF；Java 源码保持仓库现状不做无关行尾改写。批量改写按字节整文件读入 → 逐处断言 → 单次写回。
- 模块单独构建/测试时在**模块目录**执行（门禁依赖相对路径 `src/main/java`）；仓库根 pom 兼作聚合器与 BOM，可在根执行全量 reactor（先 `mvn -N install`）。

### 依赖（不可推翻）

- pom 只允许三项：`cn.aifei:aifei:1.1.2`（**`provided`**，源码零引用、不传递）、`com.alibaba:easyexcel:3.3.2`（**`optional`**，唯一第三方内核）、`junit:junit:4.13.2`（`test`）。
- **不得**新增其它第三方依赖；**不得**引入任何日志绑定（`slf4j-simple` / `logback` / `log4j` 一律禁止；EasyExcel 传递的 `slf4j-api` 仅 API，消费方自行选择绑定）。
- **零 DB**：全模块禁 `import java.sql.`，不声明 JDBC 驱动 / 连接池坐标。
- **零 Web 面**：库模块本体禁 `import io.undertow.`；Web 端点只在 demo。

### 公共面收敛（不得放宽）

- 根包 `cn.hg.aifei.excel` **精确 5 个公共类型**：`Excel` / `ExcelKit` / `ExcelException` / `ExcelColumn` / `ExcelIgnore`。**不得增删、不得改名**；变更须先改 `docs/design.md` §5 并过设计决策。
- 公共签名**零第三方类型、零框架类型**：只出现 JDK 类型（`InputStream` / `OutputStream` / `Class` / `List` / `Consumer` / 基本类型 / `String`）+ 本模块类型；不出现 `com.alibaba.*` / `org.apache.poi.*` / `cn.aifei.*`。`internal.easyexcel` 为非承诺实现层，其类型不得出现在任何公共签名。
- **无 provider / 无 SPI / 无 `META-INF/services`**；`@ExcelColumn` 只有 `value` / `index` 两属性，**不声明 `converterClass`**。
- **命名例外（已登记 §3 D15）**：`Excel` 是流式会话对象（名词，final + 私有构造 + 静态工厂）；`ExcelKit` 是一次性便捷门面（随取随用、无 Plugin 装配期）——二者均非典型 `Util`/`Kit`，名字由定档冻结，不得改名。

## 包边界与门禁

`ArchitectureBoundaryTest`（**14 例**）扫描 `src/main/java`（`testSourcesOfflineOnly` 扫 `src/test/java`）全部 `.java`：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| ① | `easyExcelOnlyInAdapter` | `import com.alibaba.` / `org.apache.poi.` 只允许在 `internal/easyexcel/` 下 |
| ② | `noFrameworkReference` | 全模块零 `import cn.aifei.`（无接入包、无 log 例外） |
| ③ | `noWebOrSqlInLibrary` | 全模块零 `import io.undertow.` / `import java.sql.` |
| ④ | `noSlf4jBinding` | 全模块零 `import org.slf4j.`；pom 不声明日志绑定 |
| ⑤ | `publicSurfaceExact` | 根包直接子 `.java` 精确 **5** 且文件名集合精确等于 `EXPECTED_ROOT_TYPES_SET` |
| ⑥ | `publicSignaturesNoThirdParty` | 5 个公共类型源码零 `com.alibaba.` / `org.apache.poi.` / `cn.aifei.` 字面量 |
| ⑦ | `noSqlOrJdbcInPom` | pom 零 `java.sql` / JDBC 驱动 / 连接池坐标 |
| ⑧ | `java8Compatibility` | 零 Java 8 后语法 / API（含 `instanceof` 模式匹配） |
| ⑨ | `noServiceProvider` | 零 `ServiceLoader` / `META-INF/services` / `converterClass` |
| ⑩ | `classForNameOnlyInDeps` | `Class.forName` 只允许在 `internal/easyexcel/ExcelDeps.java` |
| ⑪ | `annotationShapeExact` | `ExcelColumn` 仅 `value`/`index` 两属性；`ExcelIgnore` 无属性 |
| ⑫ | `batchSizeDefaultOnlyInExcel` | 默认批大小 `1000` 字面量只允许在根包 `Excel.java` |
| ⑬ | `errorMessagesOnlyInErrors` | 消息前缀 `excel: ` 只允许在 `internal/easyexcel/ExcelErrors.java` |
| ⑭ | `testSourcesOfflineOnly` | 测试源码 `http(s)://` host 只能是 `127.0.0.1` / `localhost` |

门禁**不得为兼容错误用法而放宽或绕过**；新增例外必须先改 `docs/design.md` §3 再改断言并提请主会话登记。门禁外构建期断言：主 JAR 全部 class major = 52（`javap` 抽查 `Excel` / `ExcelKit` / `BatchReadListener`）；`mvn dependency:tree` 只出现 §10.2 允许清单。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 根包公共类型集合（精确 5） | `docs/design.md` §5 + 门禁⑤ |
| `ExcelException` 构造与全部异常消息措辞（§7.5 逐字 13 条） | `internal.easyexcel.ExcelErrors` |
| EasyExcel 可选依赖探测（唯一 `Class.forName`） | `internal.easyexcel.ExcelDeps#requireEasyExcel` |
| 字段参与 / 表头名 / 列序 / 类型校验（§6 全部规则） | `internal.easyexcel.RowMapper` |
| writer 会话：默认 Sheet 名、表头写入时机、多次追加、finish 收尾 | `internal.easyexcel.ExcelWriterAdapter` |
| 读 builder 装配（唯一 `EasyExcelFactory.read(...)`） | `internal.easyexcel.ExcelReaderAdapter` |
| 分批回调：`invoke` / 末批兜底 / 回调异常包装 | `internal.easyexcel.BatchReadListener` |
| 默认批大小 `1000`（仅两参 `read` 重载） | 根包 `Excel#read(InputStream, Class, Consumer)` |
| 流所有权（一律不关闭调用方流） | 根包公共 API javadoc 契约 |

## 行为契约要点速查

- **流式读**：`Excel.read` 默认 `batchSize=1000`；逐行 SAX 解析、满批回调、末批不足兜底一次；0 数据行（仅表头 / 空 Sheet）**不回调空批**；回调后不再持有该批引用，内存 `O(batchSize)`；回调内抛异常 → 包装 `ExcelException`（cause=原异常）并中止；读过程不关闭 `in`。
- **流式写**：`Excel.writer` 创建会话时不写 `out`；首次 `write`（含空列表）写一次表头，同实例多次 `write` 按序追加同一 Sheet；`finish()`/`close()` 幂等（flush + 关闭底层 writer，不关闭 `out`）；已 `finish` 后再 `write` → `ExcelException`；writer 会话非线程安全。
- **便捷门面**：`ExcelKit.readAll` 一次性全量读，**内存 `O(行数)`，仅小文件**，大文件用 `Excel.read`；无数据行返回空 `List`（非 null）；`ExcelKit.write` 是 writer 会话语法糖，rows 为 null/空时写表头后结束。两者随取随用、无 Plugin 装配期。
- **注解映射**：默认全部当前类声明字段参与（不含父类，跳过 `static`/`transient`）；`@ExcelIgnore` 优先排除；`@ExcelColumn.value` 指定表头名（缺省用字段名原样匹配，严格相等、不折叠大小写、不转下划线）；`index>=0` 按列位置定位、优先于表头名；重复 index / 重复表头 → `ExcelException`。
- **内置类型**：`String`、八种基本类型及包装、`BigDecimal`/`BigInteger`、`java.util.Date`、`LocalDate`/`LocalDateTime`/`LocalTime`、`char`/`Character`；其它类型读时转换错误、写时参数错误；无自定义转换器 SPI。
- **异常**：三段（参数 / IO / 解析转换）统一收敛 `ExcelException`（`extends RuntimeException`，无 `throws` 子句），消息带定位信息并保留 cause；业务方只 `catch`、不 `new`。EasyExcel 缺失首次调用抛可读 `ExcelException`，禁止裸 `NoClassDefFoundError`。
- **本期不做**：CSV / 模板导出 / 公式图片富文本批注合并单元格超链接 / 自定义转换器 SPI / JSR-303 / provider / 加密文档 / 多 Sheet 高级编排 / 流式读回退（详见 `docs/design.md` §1.3）。

## 注释规范

- **语言**：一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、协议字面量与异常消息。
- **javadoc**：5 个公共类型的每个公共方法**必须**写全 `@param` / `@return` / `@throws ExcelException`（`ExcelException` 两个构造器写 `@param`）；注解属性写用途与缺省语义；`internal` 成员在非自明处书写。
- **精简**：只在代码无法表达「为什么」时写注释；不复述下一行代码、不写行尾注释、不留被注释掉的死代码 / TODO / 占位符。

## 验证

**命令在 `aifei-excel/` 根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                  # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest           # 仅跑架构门禁（14 例）
mvn dependency:tree                                # 断言：仅 aifei(provided) + easyexcel(optional) + junit(test)
javap -verbose target/classes/cn/hg/aifei/excel/Excel.class | findstr "major version"   # 期望 52
```

demo（仓库根平级目录 `aifei-excel-demo/`，端口 18150，离线可跑，不发布）：

```bash
cd ../aifei-excel-demo
start-demo.bat        # 启动 demo
stop-demo.bat         # 停止 demo
```

demo 依赖 `aifei-excel:1.0.0-SNAPSHOT`，需先在 `aifei-excel/` 执行 `mvn -q -DskipTests clean install` 安装到本地仓库（默认 `~/.m2/repository`；如改过配置，以 `mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout` 的查询结果为准）后才能构建。

> **零联网纪律**：全部单测离线；xlsx / xls 样本由测试在 `target/` 现场写出再读回，或固定极小样本，**不提交二进制到仓库**。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 可选依赖警告），并更新 `docs/testing.md`。
- 术语必须与 `docs/design.md` 统一：流式门面 `Excel` / 便捷门面 `ExcelKit` / 异常 `ExcelException` / 注解 `ExcelColumn` `ExcelIgnore` / 适配层 `internal.easyexcel` / 批大小 `batchSize` / 流所有权 / 三段错误。
- **跨模块**：与 `aifei-oss` 仅经 `InputStream` / `OutputStream` 组合（导入源 / 导出落盘），**不产生代码依赖**；「文件往哪存」归 oss，「Excel 怎么读写」归本模块。
