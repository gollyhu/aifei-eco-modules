# aifei-excel 测试文档

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

本文面向测试维护者：说明测试结构、覆盖清单、AC 覆盖台账、运行方式与新增约定。契约口径见 `docs/design.md`，开发约束见模块 `AGENTS.md`，使用者用法见 `README.md`。

## 运行方式

命令一律在模块根 `aifei-excel/` 执行（架构门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                    # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest            # 仅跑架构门禁（14 例）
mvn test -Dtest=ExcelReadTest#shouldCallbackInBatches  # 单方法
mvn dependency:tree                                 # 断言：aifei(provided) + easyexcel(optional) + junit(test)
javap -verbose target/classes/cn/hg/aifei/excel/Excel.class | findstr "major version"   # 期望 52（Windows；Linux/Git Bash 用 grep）
```

**零外部依赖 / 零联网**：全部用例离线可跑。xlsx / xls 样本由测试在 `target/` 现场用 EasyExcel 写出再读回（自洽闭环），不提交二进制样本到仓库；门禁 ⑭ 扫描 `src/test/java` 断言 URL 字面量 host 只能是 `127.0.0.1` / `localhost`。EasyExcel 传递的 `slf4j-api` 无绑定时打印一条 NOP 提示，不影响结果。

## 实测计数

`mvn clean verify` → **`Tests run: 61 / Failures: 0 / Errors: 0 / Skipped: 0`**（`BUILD SUCCESS`，JDK 编译 `target 1.8`，主 JAR class major = 52）。构成为 **门禁 14 例 + 功能 47 例**。

| 测试类 | 例数 | 类型 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 14 | 架构门禁（源码文本扫描） |
| `AnnotationMappingTest` | 12 | 单元（注解映射 / §6 规则） |
| `ExcelExceptionTest` | 6 | 单元（异常三段 / §7.5 消息逐字） |
| `ExcelKitTest` | 6 | 单元（一次性便捷门面 / §5.2） |
| `ExcelReadTest` | 7 | 集成（流式读 / §7.3，现场写出样本再读回） |
| `ExcelWriteTest` | 7 | 集成（流式写 / §7.4，读回校验） |
| `PublicApiContractTest` | 9 | 契约（公共面签名 / 注解形状 / §5） |

支撑类（`src/test/java/cn/hg/aifei/excel/support/`，不含独立用例）：测试用 POJO（业务视角命名，如 `UserRow` / `OrderRow`，禁 `foo` / `bar`）。

## 架构门禁（14 例）

`ArchitectureBoundaryTest` 在模块根执行，扫描 `src/main/java`（`testSourcesOfflineOnly` 扫 `src/test/java`）全部 `.java`，按源码文本断言（路径归一化、注释剥离后匹配），**不得放宽或绕过**：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| ① | `easyExcelOnlyInAdapter` | `import com.alibaba.` / `import org.apache.poi.` 只允许在 `internal/easyexcel/` 下 |
| ② | `noFrameworkReference` | 全模块零 `import cn.aifei.`（无接入包、无 log 例外） |
| ③ | `noWebOrSqlInLibrary` | 全模块零 `import io.undertow.` / `import java.sql.` |
| ④ | `noSlf4jBinding` | 全模块零 `import org.slf4j.`；pom 不声明日志绑定 |
| ⑤ | `publicSurfaceExact` | 根包直接子 `.java` 精确 5 且文件名集合精确等于 `Excel` / `ExcelKit` / `ExcelException` / `ExcelColumn` / `ExcelIgnore` |
| ⑥ | `publicSignaturesNoThirdParty` | 5 个公共类型源码零 `com.alibaba.` / `org.apache.poi.` / `cn.aifei.` 字面量 |
| ⑦ | `noSqlOrJdbcInPom` | pom 零 `java.sql` / JDBC 驱动 / 连接池坐标 |
| ⑧ | `java8Compatibility` | 零 Java 8 后语法 / API（含 `instanceof` 模式匹配） |
| ⑨ | `noServiceProvider` | 零 `ServiceLoader` / `META-INF/services` / `converterClass` |
| ⑩ | `classForNameOnlyInDeps` | `Class.forName` 只允许在 `internal/easyexcel/ExcelDeps.java` |
| ⑪ | `annotationShapeExact` | `ExcelColumn` 仅 `value` / `index` 两属性；`ExcelIgnore` 无属性 |
| ⑫ | `batchSizeDefaultOnlyInExcel` | 默认批大小 `1000` 字面量只允许在根包 `Excel.java` |
| ⑬ | `errorMessagesOnlyInErrors` | 消息前缀 `excel: ` 只允许在 `internal/easyexcel/ExcelErrors.java`（测试断言除外） |
| ⑭ | `testSourcesOfflineOnly` | 测试源码 URL 字面量 host 只能是 `127.0.0.1` / `localhost` |

> **门禁外构建期断言**：主 JAR 全部 class major = 52（`javap` 抽查 `Excel` / `ExcelKit` / `BatchReadListener`）；`mvn dependency:tree` 只出现允许清单（§10.2 / 本文件「运行方式」）。门禁不得为兼容错误用法而放宽。

## 功能覆盖清单（47 例）

| 测试类 | 覆盖点 | 契约依据 |
| --- | --- | --- |
| `AnnotationMappingTest`（12） | 默认字段名表头 / `@ExcelColumn.value` / `index` 优先 / `@ExcelIgnore` 优先 / `index` 冲突（场景 9）/ 重复表头（场景 8）/ 无字段（场景 11）/ 不支持类型（场景 10）/ 表头严格相等 | §6 全部规则 |
| `ExcelExceptionTest`（6） | 三段分类 / §7.5 消息逐字 / cause 保留 / 依赖缺失可读失败（场景 12）/ 构造器语义 | §5.3 / §7.5 |
| `ExcelKitTest`（6） | `readAll` 空结果返回空 `List` / 小文件全量 / `write` 语法糖读回一致 / 参数 null（场景 1）| §5.2 |
| `ExcelReadTest`（7） | 分批回调次数与批大小 / 末批兜底 / 回调异常（场景 5）/ 转换失败（场景 7）/ IO 失败（场景 6）/ 读后 `in` 未关闭 / xls 与 xlsx 各一遍 | §5.1 / §7.3 / §7.5 |
| `ExcelWriteTest`（7） | 同流多次追加（两批以上，读回校验行数与顺序）/ 表头时机（首次 write / finish 补写）/ finish 幂等（场景 3）/ flush 失败（场景 13）/ 写后 `out` 未关闭 / xls 与 xlsx 各一遍 | §5.1 / §7.4 / §7.5 |
| `PublicApiContractTest`（9） | 公共面精确 5 / 公共签名零第三方 / 注解形状 / 门面形态（会话 vs 便捷）/ 流所有权契约 | §5 |

**内存边界**：`ExcelReadTest` 走较大行数 + `batchSize=1000`，用计数器（而非全量 `List`）校验「回调内累计持有的对象数与文件总行数无关」，避免测试自身 OOM（§7.3 / D11）。

## AC ↔ 用例 ↔ 结果

| AC | 承载用例 | 结果 |
| --- | --- | --- |
| AC-01 | `ArchitectureBoundaryTest#publicSurfaceExact` / `#publicSignaturesNoThirdParty` | 通过 |
| AC-02 | `ArchitectureBoundaryTest#publicSignaturesNoThirdParty` | 通过 |
| AC-03 | `ArchitectureBoundaryTest#noServiceProvider` / `#annotationShapeExact` + `PublicApiContractTest` | 通过 |
| AC-04（注解映射） | `AnnotationMappingTest` | 通过 |
| AC-05（流式读） | `ExcelReadTest` + 内存边界用例 | 通过 |
| AC-06（流式写） | `ExcelWriteTest` | 通过 |
| AC-07（便捷门面） | `ExcelKitTest` + 文档检查（README 小文件边界） | 通过 |
| AC-08（异常） | `ExcelExceptionTest` + `ArchitectureBoundaryTest#errorMessagesOnlyInErrors` | 通过 |
| AC-09（依赖口径） | 门禁②④⑦ + `dependency:tree` | 通过 |
| AC-10（Java 8） | 门禁⑧ + `javap`（major 52） | 通过 |
| AC-11（门禁 14 例） | `mvn test -Dtest=ArchitectureBoundaryTest`（变异探针实证有效） | 通过 |
| AC-12（demo） | `aifei-excel-demo` 实测：`/page` 200、真实 xlsx（PK 头）、解析回读 `code=0 rows=12` | 通过 |
| AC-13（构建） | `mvn clean verify` = 61 例 0/0/0/0 | 通过 |
| AC-14（文档） | README / design / testing / AGENTS + 根导航齐全；本期不做与 optional 警告在 README | 通过 |



## 新增用例约定

- 新增公共行为用例须同步 `docs/design.md`（契约）与 `README.md`（用法）；改门禁须先改设计决策。
- 测试 POJO 用业务视角命名；样本一律在 `target/` 现场生成，不提交二进制；URL 字面量只用 `127.0.0.1` / `localhost`。
- 门禁外依赖口径由 `dependency:tree` 人工核对：除 `aifei(provided)` / `easyexcel(optional)` 及其传递项 / `junit(test)` 外出现任何条目即依赖失控。
