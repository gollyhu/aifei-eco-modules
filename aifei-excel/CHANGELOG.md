# 变更日志

记录 aifei-excel 的版本变更，版本号遵循语义化版本。行为细节以 `README.md`（用法）与 `docs/design.md`（契约）为准，本文件只记版本差异与升级动作。本模块为库模块（SDK），示例工程 `aifei-excel-demo` 不发布。

## [1.0.0] - 2026-11-18

首版：Excel 读写统一门面（注解表头映射 + 一次性便捷读写 + 流式分批读写），坐标 `io.github.gollyhu:aifei-excel:1.0.0-SNAPSHOT`（`packaging=jar`），Java 8（class major 52）。

### 新增

- `Excel`（流式门面）：静态 `read` 分批回调（默认 `batchSize=1000`，内存 `O(batchSize)`）、`writer` 会话同实例多次 `write` 追加、`finish()` / `close()` 幂等收尾；读不关 `in`、写不关 `out`。
- `ExcelKit`（一次性便捷门面）：`readAll` 全量读（仅小文件）、`write` 列表一次写；随取随用、无 Plugin 装配期。
- `ExcelException`（唯一模块异常）：三段失败（参数 / IO / 解析转换）统一收敛，消息带定位信息并保留底层 cause；业务方只 `catch`、不 `new`。
- `@ExcelColumn`（表头名 `value` / 列序 `index` 两属性）与 `@ExcelIgnore`（字段排除）：默认全部当前类字段参与，表头名严格相等匹配；`index >= 0` 优先按列位置定位。
- 内置常用类型转换（`String`、八种基本类型及包装、`BigDecimal` / `BigInteger`、`Date`、`LocalDate` / `LocalDateTime` / `LocalTime`、`char` / `Character`），无自定义转换器 SPI。
- xls / xlsx 读写，由内核按文件签名自动识别。

### 实现

- 唯一实现内核为 `com.alibaba:easyexcel:3.3.2`，经 `internal.easyexcel` 非承诺适配层单点引用；POI 随其传递，库模块源码不直接 import POI。
- `internal.easyexcel` 承载字段映射（`RowMapper`，唯一定义点）、读装配（`ExcelReaderAdapter` + `BatchReadListener`）、写会话（`ExcelWriterAdapter`）、异常构造（`ExcelErrors`，唯一构造点）与可选依赖探测（`ExcelDeps`，唯一 `Class.forName` 点）。
- 架构门禁 `ArchitectureBoundaryTest` 14 例守护公共面精确 5 类型、零第三方 / 零框架类型、Java 8、无 SPI、测试离线等边界。

### 兼容性

- **依赖口径**：**零 `cn.aifei` 依赖**（源码零引用、不向下游传递，消费方自带）；`com.alibaba:easyexcel:3.3.2` 为 `optional`（阻断传递，消费方须显式引入）；`junit:junit:4.13.2` 为 `test`。零日志绑定、零 DB、零 Web 面。
- **内核缺失行为**：未引入 `com.alibaba:easyexcel:3.3.2` 时，首次调用读写 API 抛可读 `ExcelException`（提示补齐依赖），不泄漏裸 `NoClassDefFoundError`；属「能力按需引入」，非功能降级。
- **门面形态**：`Excel` 为有生命周期的流式会话对象，`ExcelKit` 为无装配期的一次性便捷门面；均随取随用（不需要 Plugin）。
- 首版无存量调用方，无迁移动作。

### 验证

- `mvn clean verify` = 61 例 / 0 failure / 0 error / 0 skipped（门禁 14 例 + 功能 47 例），`BUILD SUCCESS`。
- 主 JAR 全部 class major = 52（Java 8）；`dependency:tree` 仅允许清单（aifei provided + easyexcel optional + junit test）。
- QA 第 2 轮 `IS_PASS: YES`；门禁变异探针实证有效；示例工程 `/page` 200、导出真实 xlsx（PK 头）、解析回读 `code=0 rows=12`。

### 已知问题

- **POI 4.1.2 安全公告（P1，非阻塞）**：EasyExcel 3.3.2 固定依赖 POI 4.1.2；换用 5.5.1 需对 easyexcel 做 exclusion + 显式声明，可能破坏 EasyExcel 3.3.2 针对 POI 4.1.2 的二进制兼容。本版按定档锁 `com.alibaba:easyexcel:3.3.2` / POI 4.1.2 不动，是否未来整体升级由主理人决定，实现期不私自变更。
- **`slf4j-api:1.7.32` 随 EasyExcel 传递**（仅 API，无绑定）：库模块源码零 `org.slf4j.*` 引用；消费方若已有其它 slf4j 版本由依赖仲裁决定，本模块不声明日志绑定。
- 本期不支持 CSV / 模板导出 / 公式图片富文本 / 自定义转换器 SPI / 多 Sheet 编排等（边界见 `README.md`「本期不做」与 `docs/design.md` §1.3），属明确范围而非缺陷。
