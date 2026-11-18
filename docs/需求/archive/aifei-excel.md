# aifei-excel 模块需求（任务输入）

> **归档状态：历史任务输入，只读归档**（2026-11-18 归档）。`aifei-excel` 已按第一档计划交付 1.0.0；本文件是其需求快照的**交付后补录**——原始任务输入未单独落盘，内容依 `aifei-excel/docs/design.md`、`AGENTS.md`、`README.md`、`docs/testing.md` 与规划文档回溯整理，**不作为现行设计依据**。契约现行事实见 `aifei-excel/docs/design.md`，用法见 `README.md`，开发约束见 `AGENTS.md`。
>
> 本文件是**任务输入**，不是 PRD / 设计。第四节「已定档决策」为交付时已生效的口径，不得重复询问或改写。
>
> 全文以**已交付视角**陈述：需求条目均为 1.0.0 已实现并已验收的能力；凡依交付事实回溯推断的口径，标注「假设（回溯）」（见第十二节）。
>
> 关联：`../计划/aifei-eco-modules 模块规划与排期.md` §2.3（能力域「数据文件」）、§3.1（第一档已交付清单）。

## 一、背景

生态已有 `aifei-oss` 负责「文件往哪存」，但**缺 Excel 读写能力**：业务普遍各自裸用 POI / EasyExcel，注解映射、流式大文件、异常口径各写各的，每个应用抄一遍。

本模块补齐**统一 Excel 读写门面**。规划文档 §2.3 将其归入「数据文件」能力域；与 `aifei-oss` 是组合关系而非依赖关系——「Excel 怎么读写」归本模块，「文件往哪存」归 oss，两者经 `InputStream` / `OutputStream` 对接。

## 二、产品目标与成功标准

### 2.1 产品目标

为 aifei 应用提供**一套 Excel 读写门面**，使业务开发者不再各自裸用 POI / EasyExcel：注解约定表头名与列序、小文件一次性读写、大文件流式分批读写、失败一律收敛为一种可读异常。使用者是**做导入导出的业务开发者**与**管理依赖口径的应用维护者**；成功标准是「掌握 5 个公共类型即可完成全部读写」「导入导出口径在模块内唯一、换项目不必重写」「十万行不 OOM」「未引内核时得到可读提示而非 `NoClassDefFoundError`」，且每条都能被单测、门禁断言、`dependency:tree` 或 `javap` 机械判定（见 2.2）。

- 坐标：`io.github.gollyhu:aifei-excel:1.0.0`（`packaging=jar`）
- 包名：`cn.hg.aifei.excel`
- 技术基线：**Java 8**（语法与 API 双约束，主 JAR class major = 52）

四件事，一件一个落点：

| # | 目标 | 落点 |
| --- | --- | --- |
| 1 | **注解表头映射**：表头名 / 列序 / 排除字段，统一字段参与语义 | 注解 `ExcelColumn` / `ExcelIgnore` + `internal.easyexcel.RowMapper`（唯一映射定义点） |
| 2 | **一次性便捷读写**（小文件）：全量读成 `List`、列表一次写 | `ExcelKit` |
| 3 | **流式读写**（大文件）：分批读回调、同流多次追加写，内存 `O(batchSize)` | `Excel` 会话 + `internal.easyexcel.BatchReadListener` / `ExcelWriterAdapter` |
| 4 | **统一异常**：参数 / IO / 解析转换三段收敛，保留 cause | `ExcelException` + `internal.easyexcel.ExcelErrors`（唯一构造点） |

### 2.2 成功标准（均已达成，逐条可机械判定）

| # | 成功标准 | 机械判定 | 交付实测 |
| --- | --- | --- | --- |
| S1 | 掌握 5 个公共类型即可完成全部读写 | 根包精确 5 类型；公共签名零第三方 / 零框架类型 | 门禁⑤⑥ + `PublicApiContractTest`（9 例）通过 |
| S2 | 大文件内存不随总行数增长 | 回调期间驻留对象数 ≤ `batchSize` | `ExcelReadTest` 内存边界用例（计数器）通过 |
| S3 | 映射 / 消息 / 批大小 / 流所有权口径在模块内唯一 | 每类规则仅一处定义点，实现类不得复制 | 门禁⑪⑫⑬ + `AnnotationMappingTest`（12 例） |
| S4 | 失败可解释、可统一 `catch` | 全部失败 = `ExcelException`，消息含定位信息并保留 cause | `ExcelExceptionTest`（6 例）通过 |
| S5 | 未引内核时报错可读 | 首次调用抛可读 `ExcelException`，非裸 `NoClassDefFoundError` | `ExcelExceptionTest` 场景 12 |
| S6 | 依赖与字节码基线可控 | 依赖三项（`provided` / `optional` / `test`）；class major 52 | `dependency:tree` + `javap` |
| S7 | 能力可被人看见、可离线真跑 | demo 18150 真实完成「上传解析 + 两种导出」 | demo 实测：`/page` 200、xlsx `PK` 头、`code=0 rows=12` |
| S8 | 可交接、可回归 | 61 例 `0 failure / 0 error`（门禁 14 + 功能 47） | `mvn clean verify` |

## 三、用户故事

角色限定为真实消费方：**业务开发者**（在应用里写导入导出）与**应用维护者**（管依赖与升级口径）。不涉及 C 端用户。

| # | 角色 | 故事 | 对应需求 |
| --- | --- | --- | --- |
| US-01 | 业务开发者 | 作为业务开发者，我希望在行模型上用注解声明表头名与列序，以便不必为每张表手写列下标与取值代码 | P0-04 |
| US-02 | 业务开发者 | 作为业务开发者，我希望一行代码把小 Excel 读成 `List`、把列表写回文件，以便在管理后台做简单导入导出 | P0-03 |
| US-03 | 业务开发者 | 作为业务开发者，我希望大文件按批回调处理且内存不随行数增长，以便导入十万行不 OOM | P0-01 |
| US-04 | 业务开发者 | 作为业务开发者，我希望同一个 writer 会话分批多次追加写，以便边算边写、不必先在内存拼全量列表 | P0-02 |
| US-05 | 业务开发者 | 作为业务开发者，我希望模块不关闭我传入的流，以便我在同一存储流 / 响应流上继续操作 | P0-06 |
| US-06 | 业务开发者 | 作为业务开发者，我希望所有失败只有一种异常且消息带行号、列名，以便一个 `catch` 收口并直接定位坏数据 | P0-07 |
| US-07 | 应用维护者 | 作为应用维护者，我希望未引 easyexcel 时得到可读提示而不是类缺失堆栈，以便分辨「依赖没加」与「代码写错」 | P0-07 |
| US-08 | 应用维护者 | 作为应用维护者，我希望依赖只有三项、三方内核可选且不向下传递，以便按需引入、不被强加整棵 POI 树 | P0-08 |
| US-09 | 应用维护者 | 作为应用维护者，我希望公共面与边界被门禁和单测锁死、字节码版本可查，以便升级换代不出现静默的契约漂移 | P0-08 |
| US-10 | 业务开发者 | 作为业务开发者，我希望有示例工程能离线真跑一遍上传解析与两种导出，以便先看效果再接自己的业务 | P0-09 |

## 四、已定档决策（不可推翻）

1. **交付性质 = 库模块（SDK），不是业务应用工程**：无 `AppConfig` / `Launcher` / `@Path` Service / `service/` 业务包 / 业务库表 / `Db+Row` / `Generator` / 服务拓扑 / 部署架构 / 数据层实现。
2. **无 Plugin、无配置键、无 `aifei` 接入包**：无连接资源、无配置可解析，全部行为由方法入参显式传入（对齐 `aifei-trace` 先例）。
3. **根包公共 API 精确 5 个类型**：`Excel` / `ExcelKit` / `ExcelException` / `ExcelColumn` / `ExcelIgnore`；不得增删改名，变更须先改 `design.md` §5 并过设计决策。
4. **公共签名零第三方类型、零框架类型**：只出现 JDK 类型 + 本模块类型；`com.alibaba.*` / `org.apache.poi.*` / `cn.aifei.*` 不得出现在公共签名。
5. **依赖三项封顶**：`cn.aifei:aifei-all:1.1.2`（`provided`）、`com.alibaba:easyexcel:3.3.2`（`optional`，唯一第三方内核）、`junit:4.13.2`（`test`）。
6. **无 provider / 无 SPI / 无 `META-INF/services`**；不设自定义转换器 SPI，只用 EasyExcel 内置常用类型转换器。
7. **`internal.easyexcel` 为非承诺实现层**：可引用 EasyExcel / POI，但不得被公共面签名暴露。
8. **每类规则唯一定义点**：异常构造与消息、可选依赖探测、字段映射、writer 会话、读 builder 装配、批回调、默认批大小、流所有权，各只有一个定义点，实现类不得复制。
9. **命名例外**：`Excel` 是流式会话对象（final + 私有构造 + 静态工厂），`ExcelKit` 是一次性便捷门面——均非典型 `Util` / `Kit`，名字由定档冻结。

## 五、需求池与分级

### 5.1 分级口径

| 级别 | 含义（本文件口径） | 依据 |
| --- | --- | --- |
| **P0** | 1.0.0 已交付并已通过验收的能力 | `aifei-excel/docs/design.md` §14 AC-01…AC-14 全「通过」；`docs/testing.md`「AC ↔ 用例 ↔ 结果」台账 |
| **P1** | 设计文档明示「1.1 演进」、或原文即标注 P1 的项；本期未交付、无承诺工期 | `design.md` §3 D8、§2.5、§13 事项 3 / 5 / 7 |
| **P2** | `design.md` §1.3「本期不做」中的其余项；明确不做、无优先级与排期表述 | `design.md` §1.3；模块 `AGENTS.md`「本期不做」 |

> **假设（回溯）**：P1 / P2 的划分规则来自上述文档的显式表述，不是原始需求的业务优先级（原始需求未落盘、无优先级记载）。详见 A4、Q2。

### 5.2 P0（已交付，本期范围）

| 编号 | 能力 | 要求 | 验收 |
| --- | --- | --- | --- |
| P0-01 | 分批读 | `Excel.read` 按行 SAX 解析，满 `batchSize` 回调一次，末批不足兜底一次；0 数据行（仅表头 / 空 Sheet）不回调空批；回调后不再持有该批引用，内存 `O(batchSize)`；两参重载默认 `batchSize=1000` | GWT-01…05；`ExcelReadTest`（7 例） |
| P0-02 | 会话写 | `Excel.writer` 创建会话时不写 `out`；首次 `write`（含空列表）写一次表头；同实例多次 `write` 追加同一 Sheet；`finish()` / `close()` 幂等；已 `finish` 后再 `write` 抛 `ExcelException` | GWT-06…10；`ExcelWriteTest`（7 例） |
| P0-03 | 便捷门面 | `ExcelKit.readAll` 一次性全量读（内存 `O(行数)`，仅小文件，无数据行返回空 `List`）；`ExcelKit.write` 为「会话 + 一批写 + `finish()`」语法糖，`rows` 为 null / 空时写表头后结束 | GWT-11…13；`ExcelKitTest`（6 例） |
| P0-04 | 注解映射 | 默认全部当前类声明字段参与（不含父类，跳过 `static` / `transient`）；`@ExcelIgnore` 优先排除；`@ExcelColumn.value` 指定表头名（缺省用字段名原样匹配，严格相等）；`index>=0` 按列位置、优先于表头名；重复 index / 重复表头报错 | GWT-14…15；`AnnotationMappingTest`（12 例） |
| P0-05 | 内置类型 | `String`、八种基本类型及包装、`BigDecimal` / `BigInteger`、`java.util.Date`、`LocalDate` / `LocalDateTime` / `LocalTime`、`char` / `Character`；其它类型读时转换错误、写时参数错误 | GWT-16…17 |
| P0-06 | 流所有权 | 一律不关闭调用方传入的流：读不关 `InputStream`，`finish()` / `close()` 只 flush 并关闭内核 writer，不关 `OutputStream` | GWT-18；`PublicApiContractTest`（9 例） |
| P0-07 | 异常 | 三段错误统一收敛 `ExcelException`（`extends RuntimeException`，无 `throws` 子句），消息带定位信息并保留 cause；业务方只 `catch`、不 `new`；内核缺失抛可读异常 | GWT-19…21；`ExcelExceptionTest`（6 例） |
| P0-08 | 公共面 / 门禁 / 构建 | 根包精确 5 类型、公共签名零第三方与框架类型、`internal.easyexcel` 为唯一适配层、架构门禁 14 例、依赖三项、class major 52 | GWT-22…25 |
| P0-09 | 交付配套 | 文档四件套（`README.md` / `docs/design.md` / `docs/testing.md` / `AGENTS.md`）+ demo（18150，离线不发布）+ 根 `README.md` 模块总览条目 | GWT-26…27 |

### 5.3 P1（本期未交付，有显式演进或风险表述）

| 编号 | 项 | 边界 | 依据 |
| --- | --- | --- | --- |
| P1-01 | 自定义转换器 SPI | `@ExcelColumn` 增 `converterClass`、注册自定义 `Converter`；本版注解只有 `value` / `index` | `design.md` §3 D8「未来需要时作为 1.1 演进」；§1.3 第 4 项 |
| P1-02 | 父类字段映射 | 本版只扫当前类声明字段；继承字段映射留待演进 | `design.md` §6.5 + §13 事项 7「作为 1.1 演进」（**不在 §1.3 表内**，见 A7 / Q3；已裁定保留在 P1） |
| P1-03 | POI 4.1.2 安全公告处置（风险项，非能力） | 本版锁 `easyexcel:3.3.2` / POI 4.1.2 不动；是否整体升级由主理人决定 | `design.md` §2.5 + §13 事项 3（原文标注「P1 非阻塞」） |
| P1-04 | 发布元数据（Central） | 本轮 pom 不含 Central 发布 profile 与 licenses / developers / scm | `design.md` §10.1 + §13 事项 5（待拍板，非阻塞） |

### 5.4 P2（明确不做，无承诺、未排期）

| 编号 | 项 | 边界 | 依据 |
| --- | --- | --- | --- |
| P2-01 | CSV 读写 | 只支持 xls / xlsx；不暴露 CSV API | `design.md` §1.3 第 1 项 |
| P2-02 | 模板导出 | 不做 `withTemplate` 填充，只做注解模型平铺导出 | §1.3 第 2 项 |
| P2-03 | 公式 / 图片 / 富文本 / 批注 / 合并单元格 / 超链接 | 只读写单元格标量值 | §1.3 第 3 项 |
| P2-04 | JSR-303 / Bean Validation | 不做字段校验注解，值合法性由使用方保证 | §1.3 第 5 项 |
| P2-05 | provider / SPI / 多后端 | 只有 EasyExcel 一条实现路径，无后端切换 | §1.3 第 6 项 |
| P2-06 | 独立配置键 / Plugin / 命名实例 | 无 `excel.*` 配置键、无 Plugin、无多实例注册表 | §1.3 第 7 项 |
| P2-07 | 加密 / 密码保护 / 数字签名文档 | 不处理受保护工作簿 | §1.3 第 8 项 |
| P2-08 | 多 Sheet 高级编排 | 读第 0 个 Sheet、写单 Sheet | §1.3 第 9 项 |
| P2-09 | 流式读的随机访问 / 回退 | 单向逐批读，回调后不可回退、不缓存历史批 | §1.3 第 10 项 |

## 六、P0 逐条验收标准（Given / When / Then）

判定条件均为可机械验证项：能被单测断言、架构门禁断言、`dependency:tree`、`javap` 或文件检查固定判定。消息逐字以 `design.md` §7.5 为唯一措辞来源。

| GWT | 对应 P0 | Given（前置） | When（动作） | Then（可机械判定） | 验证通道 |
| --- | --- | --- | --- | --- | --- |
| GWT-01 | P0-01 | 现场生成的 xlsx（2500 数据行），`batchSize=1000` | `Excel.read(in, OrderRow.class, 1000, c)` | 回调 3 次，批大小序列 `[1000,1000,500]`，末批不足仍回调一次 | `ExcelReadTest` |
| GWT-02 | P0-01 | 同一 xlsx | 两参重载 `Excel.read(in, OrderRow.class, c)` | 批大小序列与 GWT-01 一致（等价 `batchSize=1000`） | `ExcelReadTest` + 门禁⑫ |
| GWT-03 | P0-01 | 10 万行样本 | 回调内以计数器统计当前驻留对象数 | 峰值 ≤ `batchSize`，不随文件总行数线性增长 | 内存边界用例（计数器，非全量 `List`） |
| GWT-04 | P0-01 | `batchSize=0` | 四参 `read` | 抛 `ExcelException`，消息逐字 `excel: batchSize must be positive, but was: 0` | `ExcelReadTest` + 门禁⑬ |
| GWT-05 | P0-01 | 3 批样本，回调内抛 `IllegalStateException` | `Excel.read` | 抛 `ExcelException` 且 `getCause()` 为该异常；后续批次不再回调 | `ExcelReadTest` |
| GWT-06 | P0-02 | 空 `ByteArrayOutputStream` | `Excel.writer(out, OrderRow.class, "订单")` 后立即查 `out.size()` | `out.size()==0`（建会话不写 `out`） | `ExcelWriteTest` |
| GWT-07 | P0-02 | 同一 writer 会话 | `write(b1=1000 行)` → `write(b2=500 行)` → `finish()` → 读回 | 表头仅 1 次；数据行 1500 且顺序与写入一致（同一 Sheet） | `ExcelWriteTest` |
| GWT-08 | P0-02 | 会话已 `finish()` | 再次 `finish()` / `close()` | 均不抛异常、不产生额外字节（幂等） | `ExcelWriteTest` |
| GWT-09 | P0-02 | 会话已 `finish()` | 再 `write(batch)` | 抛 `ExcelException`，消息逐字 `excel: writer session already finished, create a new writer` | `ExcelWriteTest` + 门禁⑬ |
| GWT-10 | P0-02 | 新会话，未 `write` | 直接 `finish()` 后读回 | 文件含表头行、数据行 0（finish 补写表头） | `ExcelWriteTest` |
| GWT-11 | P0-03 | 0 数据行样本 | `ExcelKit.readAll(in, T.class)` | 返回非 null 且 `size()==0` 的 `List` | `ExcelKitTest` |
| GWT-12 | P0-03 | 12 行 POJO 与输出流 | `ExcelKit.write(out, T.class, rows)` 后 `ExcelKit.readAll` 读回 | 行数与逐字段值相等（读写闭环） | `ExcelKitTest` |
| GWT-13 | P0-03 | `rows` 为 `null` 或空列表 | `ExcelKit.write` | 不抛异常；产出仅含表头的文件 | `ExcelKitTest` / `ExcelWriteTest` |
| GWT-14 | P0-04 | 行模型含无注解字段 / `@ExcelColumn(value="订单号")` / `@ExcelColumn(index=0)` / `@ExcelIgnore`，配对应样本 | 读入全部行 | 逐字段按「`index>=0` 优先 → `value` 或字段名严格相等 → `@ExcelIgnore` 排除」取值；被排除字段恒为默认值；不做大小写折叠与驼峰 / 下划线互转 | `AnnotationMappingTest` |
| GWT-15 | P0-04 | 两字段 `index` 相同；或表头出现同名两列 | 映射构建 / 解析该文件 | 抛 `ExcelException`，消息逐字 `excel: duplicated column index {i} on fields {f1} and {f2}` / `excel: duplicated header "{h}" at column {c1} and {c2}` | `AnnotationMappingTest` + 门禁⑬ |
| GWT-16 | P0-05 | 字段覆盖 `String`、八种基本类型及包装、`BigDecimal` / `BigInteger`、`java.util.Date`、`LocalDate` / `LocalDateTime` / `LocalTime`、`char` | 写出后读回 | 逐字段值往返一致 | `AnnotationMappingTest` |
| GWT-17 | P0-05 | 字段类型为自定义 POJO 或枚举（内置清单之外） | 读该列 / 写出该行 | 读：抛 `ExcelException`，消息逐字 `excel: convert failed at row {r}, column {c}, field {f}, type {t}, cause: {m}`；写：抛 `ExcelException`，消息逐字 `excel: unsupported field type {t} on field {f}` | `AnnotationMappingTest` + 门禁⑬ |
| GWT-18 | P0-06 | 关闭状态可观测的 `InputStream` / `OutputStream` 包装（`close()` 置标记） | 完成一次读；完成 `write` + `finish()` | 两个包装的关闭标记均为 false（未关闭调用方流） | `PublicApiContractTest` / `ExcelReadTest` / `ExcelWriteTest` |
| GWT-19 | P0-07 | 入参为 null；流抛 `IOException`；字符串填入 `int` 字段 | 触发三条路径 | 三类均抛 `ExcelException`：null 参数无 cause；IO 与转换错误 `getCause()!=null`；消息逐字对齐 `design.md` §7.5 对应行 | `ExcelExceptionTest` + 门禁⑬ |
| GWT-20 | P0-07 | 运行期无法加载 `com.alibaba.excel.EasyExcel`（以隔离加载模拟依赖缺失，手法见 `docs/testing.md`） | 首次调用任一读写 API | 抛 `ExcelException`，消息逐字 `excel: com.alibaba:easyexcel:3.3.2 is required, add it to your dependencies`；异常类型不是 `NoClassDefFoundError` | `ExcelExceptionTest` 场景 12 + 门禁⑩ |
| GWT-21 | P0-07 | 5 个公共类型的源码 | 检索方法声明 | 公共方法全部无 `throws` 子句（`ExcelException` 为 `RuntimeException`） | `PublicApiContractTest` / 源码断言 |
| GWT-22 | P0-08 | 模块源码 | 枚举根包直接子 `.java`；检索三方与框架字面量 | 文件名集合精确等于 5 个定档类型；5 个公共类型零 `com.alibaba.` / `org.apache.poi.` / `cn.aifei.` | 门禁⑤⑥ |
| GWT-23 | P0-08 | `target/classes` 与 `pom.xml` | `javap -verbose` 抽查 `Excel` / `ExcelKit` / `BatchReadListener`；执行 `dependency:tree` | 全部 class major = 52；依赖树仅 `aifei-all`（`provided`）+ `easyexcel`（`optional`）及其传递项 + `junit`（`test`），无日志绑定、无 JDBC / 连接池、无 Spring | `javap` + `dependency:tree` |
| GWT-24 | P0-08 | 模块根目录 | `mvn clean verify` | `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`；其中 `ArchitectureBoundaryTest` 14 例全绿 | 构建日志 |
| GWT-25 | P0-08 | 根包注入 `import com.alibaba.excel.EasyExcel`（变异探针） | 执行门禁 | 断言① 失败并输出违规文件名（证明门禁非空转） | 变异探针实测（`docs/testing.md` AC-11） |
| GWT-26 | P0-09 | `aifei-excel-demo` 启动于 18150 | GET `/excel/demo/page`；POST `/parse`（上传 xlsx）；GET `/export-once`、`/export-stream` | 均 HTTP 200；`/parse` 返回真实累计 `rows` / `batches`（实测记录 `code=0 rows=12`）；导出体以 `PK` 字节头起始（真实 xlsx），页面资源零外网请求 | demo 实测（`docs/testing.md` AC-12） |
| GWT-27 | P0-09 | 模块根与仓库根 | 检查文件存在与内容 | `README.md` / `docs/design.md` / `docs/testing.md` / `AGENTS.md` 齐备；`README.md` 含「本期不做」与 optional 依赖警告；根 `README.md` 模块总览含 `aifei-excel` 条目 | 文件检查 + 内容检查 |

## 七、非功能需求 / 硬约束

- **Java 8 双约束**：`source` / `target` = `1.8`；主 JAR 全部 class major = 52；禁 `var` / `record` / `switch` 表达式 / text block / `sealed` / `instanceof` 模式匹配；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂。
- **零 DB**：全模块禁 `import java.sql.`，不声明 JDBC 驱动 / 连接池坐标。
- **零 Web 面**：库模块本体禁 `import io.undertow.`；真实端点只在 demo。
- **零框架引用**：全模块零 `import cn.aifei.`（含日志门面），不记录日志。
- **零日志绑定**：禁 `slf4j-simple` / `logback` / `log4j`；EasyExcel 传递的 `slf4j-api` 仅 API，消费方自行选择绑定。
- **可选依赖语义**：不引 easyexcel 时读写**完全不可用**，首次调用抛 `ExcelException`（消息含坐标与版本），禁止裸 `NoClassDefFoundError`；这是「能力按需引入」而非「功能降级」。
- **架构门禁 `ArchitectureBoundaryTest`**：源码文本扫描，须在模块根目录执行，不得为兼容错误用法放宽或绕过；新增例外须先改 `design.md` §3 再改断言。
- **构建**：模块目录内 `mvn clean verify` 必须 **0 failure / 0 error**。
- **文档四件套**：`README.md`（用法与可选依赖警告）、`docs/design.md`（契约事实来源）、`docs/testing.md`（测试策略）、`AGENTS.md`（开发约束）；公共行为变更须同步。
- **注释**：简体中文；5 个公共类型的每个公共方法写全 `@param` / `@return` / `@throws ExcelException`；只在代码无法表达「为什么」时写注释。

## 八、必须交付的固定配套

| 类别 | 交付物 |
| --- | --- |
| 构建 | `pom.xml`（Java 8、三项依赖封顶、无日志绑定） |
| 公共面 | 根包精确 5 类型 |
| 内部 | `internal.easyexcel`：依赖探测、错误消息、行映射、读 / 写适配、批回调、字段元数据 |
| 门禁 | `ArchitectureBoundaryTest`（14 例源码文本扫描） |
| 测试 | 门禁 + 契约测试 + 读写功能测试；全部离线，样本现场生成、不提交二进制 |
| 文档 | `docs/design.md`、`docs/testing.md`、`README.md`、`AGENTS.md` |
| demo | `aifei-excel-demo`（端口 18150，离线可跑，不发布） |
| 导航 | 根 `README.md` 模块总览补条目 |

## 九、交付结果（回溯）

| 项 | 结果 |
| --- | --- |
| 版本 | 1.0.0（2026-11-18） |
| 公共面 | 根包 5 类型，与 §四-3 完全一致 |
| 门禁 | `ArchitectureBoundaryTest` 14 例全绿 |
| 测试 | 61 例（门禁 14 + 功能 47） |
| 依赖 | `dependency:tree` 仅 `aifei-all`（provided）+ `easyexcel`（optional）+ `junit`（test） |
| class major | 52 |
| 文档 | 四件套齐备 + demo `README.md` |
| demo | `aifei-excel-demo`，HTTP 18150，零外网 |

## 十、边界（本期不做）

CSV、模板导出、公式 / 图片 / 富文本 / 批注 / 合并单元格 / 超链接、自定义转换器 SPI、JSR-303 校验、provider 与多后端切换、加密文档、多 Sheet 高级编排、流式读回退。清单以 `aifei-excel/docs/design.md` §1.3 为准，实现时不得自行扩张；分级归属见 5.3 / 5.4。

## 十一、交付核验记录（与第六节 GWT 并存）

以下为交付关的自检清单（原始记录，逐条保留）。可测判定条件见第六节 GWT；本节记录「当时核验通过」的事实。

- [x] `aifei-excel` 目录内 `mvn clean verify` 通过（0 failure / 0 error）。
- [x] `ArchitectureBoundaryTest` 14 条断言全部通过。
- [x] 根包公共类型集合精确等于 5 个定档类型。
- [x] 一次性读 → 写 → 读回闭环；流式分批回调批大小与末批兜底正确。
- [x] 不关闭调用方流；`finish()` / `close()` 幂等；已 `finish` 后再 `write` 抛 `ExcelException`。
- [x] 未引入 easyexcel 时首次调用抛可读 `ExcelException`，非裸 `NoClassDefFoundError`。
- [x] 主 JAR 全部 class major = 52；`dependency:tree` 仅三项。
- [x] 文档四件套齐备，根 `README.md` 模块总览有条目。

## 十二、假设与待确认问题

### 12.1 假设（回溯）

| # | 假设 | 说明 |
| --- | --- | --- |
| A1 | 文档性质 | 原始任务输入未单独落盘：本文件由交付事实（`design.md` / `AGENTS.md` / `README.md` / `docs/testing.md` / 规划文档）反向补录；原始诉求措辞、提出人、评审过程无证据，故不记载、不虚构 |
| A2 | 成功标准来源 | 2.2 的口径（5 类型、61 例 0/0、依赖三项、class 52、demo 真实可跑）取自交付实测记录与规划文档 §3.1，非原始需求中明示的验收承诺 |
| A3 | 角色画像 | 用户故事角色（业务开发者 / 应用维护者）由 `README.md` 的消费方定位与「消费方必须自带 easyexcel」语义回溯推定；原始需求未记载角色 |
| A4 | P1 / P2 划分规则 | P1 = `design.md` 有显式「1.1 演进」或原文标注 P1 的项；P2 = §1.3 其余不做项。这是文档口径归类，不是业务优先级排序 |
| A5 | P0 编号映射 | 本文件 P0-01…P0-09 与 `design.md` §14 尾注的 P0 ①…⑨ 为多对一映射，按 §14 AC 行逐条对齐推定 |
| A6 | GWT 数值口径 | GWT 中的样本规模（2500 行 → `[1000,1000,500]`、演示 12 行）取自 `docs/testing.md` 记录与 demo 实测，是判定示例而非需求原文约定；判定结构不变，数值可替换 |
| A7 | P1-02 的归类 | 父类字段映射不在 §1.3 表内，仅 §6.5 与 §13 事项 7 有边界与「1.1 演进」表述；归入 P1 属回溯归类，2026-11-18 已裁定保留（见 Q3） |

### 12.2 待确认问题（口径缺口与文档不一致）

> Q3 / Q4 / Q5 / Q7 已于 2026-11-18 由架构定档，并已落地到 `aifei-excel/docs/design.md`；本节对这四项只记录结论与出处，不再提请确认。Q1 / Q2 仍为未闭环项；Q6 属实证环境记录、非契约条款，无文档处置动作，保持原状。

| # | 问题 | 现状 / 提请确认 |
| --- | --- | --- |
| Q1 | 原始需求无原文 | 本文件不能作为需求变更依据；若需「当时如何提需求」的原始记录，须另寻上游材料 |
| Q2 | P1 / P2 非业务优先级 | 排序依据是 `design.md` §1.3「不做清单」与少量「1.1 演进」表述；真实业务优先级须由主理人重排 |
| Q3 | P1-02 是否应列入需求池 | **已裁定：保留在 P1**。§1.3 是实现期防范围蔓延红线，**不是需求池的完备来源**；凡 §13 标注「1.1 演进」的条目即需求池演进候选（P1），与是否在 §1.3 列举无关；父类字段映射见 `design.md` §6.5 与 §13 事项 7。裁决出处：`design.md` §1.3 表后「归类口径」 |
| Q4 | `write` 传 `null` / 空 `rows` 的措辞不一致 | **已定档，措辞冲突已消除**。实际行为：`write(rows)` 先写表头（幂等）再追加数据行，`null` / 空列表时只保证表头、不写数据行——首次 `write(null)` 或 `write(空列表)` 写一次表头、不抛异常，表头已写则本次调用无实际写入；已 `finish()` 后再 `write` 仍抛 `ExcelException`（会话已关闭检查在前）。原按「含空列表写一次表头」记录，与此一致，GWT-13 无需改动。裁决出处：`design.md` §5.1 `write(rows)` 行 |
| Q5 | 空文件（0 数据行）的回调次数 | **已定档**：`BatchReadListener.doAfterAllAnalysed` 仅在「表头已解析且当前批非空」时 `deliver()`，故 0 数据行（仅表头 / 空 Sheet）**不回调空批**，消费方无需判空。裁决出处：`design.md` §7.3（§5.1 read 行补注、`AGENTS.md`「流式读」同步） |
| Q6 | `design.md` §2.2 记录的仓库根路径 | 实证记录处写 `c:/Workspace/github/aifei-eco-modules`，与实际工作区路径不一致（仅实证环境记录，非契约条款）；本文件未据此改写任何事实 |
| Q7 | `design.md` §12.1 测试策略表未列 `PublicApiContractTest`（9 例） | **已修复**：§11.1 文件清单与 §12.1 测试策略表均已补 `PublicApiContractTest`（9 例：公共面精确 5 / 公共签名零第三方 / 注解形状 / 门面形态 / 流所有权契约），47 例功能测试的构成（12+6+6+7+7+9）成立。裁决出处：`design.md` §11.1、§12.1 |

## 十三、执行方式

- 本文件为**任务输入**，第四节已定档决策不再重复询问；契约细节以 `aifei-excel/docs/design.md` 为准（其 §5 公共类型逐字签名是编码角色的唯一依据）。
- 需求与设计冲突时，**先改设计决策再改代码**。
- 验证命令一律在 `aifei-excel/` 目录内执行（门禁依赖相对路径 `src/main/java`）；仓库根无聚合 pom。
- 本文件为只读归档：后续增量以新任务输入承载，不在此文件上继续演进。
