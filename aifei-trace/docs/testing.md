# aifei-trace 测试策略与用例清单

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

本文面向**测试维护者**：说明测试分类、用例与设计契约场景的对照关系、验证命令与离线纪律。行为契约与异常语义的完整定义见 `docs/design.md`（§5 公共 API 契约 / §7.2 异常语义表 / §7.3 消息措辞 / §10 门禁清单 / §15 验收标准），本文不复制契约细节，仅用指针引用。

---

## 1. 测试分类

| 分类 | 文件 | 用例数 | 职责 |
| --- | --- | --- | --- |
| **架构门禁** | `ArchitectureBoundaryTest` | 10 例门禁 + 5 例自证 = 15 | 以源码文本扫描断言包边界 / 公共面大小 / Java 8 / 无 SPI / 测试离线（design.md §10 十例清单）；另 5 例自证用例验证门禁⑩剥注释状态机自身的判定正确性（外部 URL 命中 / 注释豁免 / 字面量内 `//` 不误判 / 协议大小写 / 普通斜杠不误报） |
| **单测（门面与生成）** | `TraceKitTest` | 9 | 覆盖 design.md §7.2 场景 1–3 / 7 / 11–13 + startSpan 覆盖语义 + 生成器替换生效 |
| **单测（上下文）** | `TraceContextTest` | 4 | null = 移除、clear 幂等、两个 ThreadLocal 独立存取 |
| **单测（生成器）** | `DefaultTraceIdGeneratorTest` | 3 | 格式契约固定断言（非空 / 32 位 / `[0-9a-f]`）、千次唯一、单例形态 |
| **单测（日志装饰）** | `TraceLogTest` | 7 | 覆盖 §7.2 场景 8–9 与 AC-09：30 打印方法 + 5 开关全覆盖、前缀拼装、TraceId 缺失原样透出、Supplier 打印时刻求值（正反两个时序 + 求值计数器） |
| **单测（工厂装饰）** | `TraceLogFactoryTest` | 4 | 三个 getLog 包装、端到端前缀贯通、null delegate 拒绝、delegate 构造后不变（反射） |
| **单测（web 接入）** | `web.TraceHandlerTest` | 7 | 覆盖 §7.2 场景 4–6 / 12 与 AC-11：纯生成、提取绑定、空白回退、提取异常吞掉回退、下游异常原样传播 + finally 清理、`next == null` 安全结束、setExtractor 链式 / null |
| **测试桩** | `support.RecordingLog` | — | 伪 `cn.aifei.log.Log`：String 变体调用即记录；**Supplier 变体仅暂存不求值**，须显式 `flushPendingSupplier()` 才求值——这是验证「打印时刻读上下文」懒求值契约的关键机制 |

合计 **49 例**（surefire 口径），全部为 JUnit 4。

### 测试隔离纪律

- `TraceKitTest` 替换生成器后于 `@After` 恢复 `DefaultTraceIdGenerator.INSTANCE`（`restoreDefaults()`），并 `TraceKit.clear()` 清理线程上下文。
- 绑定类用例（`TraceLogTest` / `TraceLogFactoryTest` / `TraceHandlerTest`）均以 `@After` 或 try/finally 保证 `clear()`，测试间无状态残留。
- 全部用例零联网、零 `Thread.sleep`、零文件 I/O 依赖（门禁测试读自身源码属固定输入）。

---

## 2. 场景 ↔ 用例对照表（design.md §7.2）

| §7.2 场景 | 行为 | 覆盖用例 |
| --- | --- | --- |
| 1 | `setTraceIdGenerator(null)` 拒绝（§7.3 措辞） | `TraceKitTest.setNullGeneratorShouldReject` |
| 2 | `init(null/""/"   ")` 回退生成并绑定 | `TraceKitTest.initNullOrBlankShouldFallbackToGenerate` |
| 3 | `init(" abc ")` trim 后绑定 `"abc"` | `TraceKitTest.initShouldTrimTraceId` |
| 4 | extractor 返回 null / 空白回退生成 | `web.TraceHandlerTest.blankExtractionShouldFallbackToGenerate` |
| 5 | extractor 抛异常吞掉并回退生成 | `web.TraceHandlerTest.extractionExceptionShouldBeSwallowedAndFallback` |
| 6 | 下游异常原样传播 + finally 清理 | `web.TraceHandlerTest.downstreamExceptionShouldPropagateAndFinallyClear`（`assertSame` 原异常实例；清后 `getTraceId()==null` 且 `getSpanId()==null`） |
| 7 | `getTraceId()` / `getSpanId()` 未绑定返回 null | `TraceKitTest.gettersShouldReturnNullWhenUnbound`、`TraceContextTest.gettersShouldReturnNullWhenUnbound` |
| 8 | TraceId 缺失时消息原样透出 | `TraceLogTest.missingTraceIdShouldPassThroughRaw`；Supplier 反向时序 `supplierShouldNotPrefixWhenTraceIdClearedBeforeFlush` |
| 9 | `new TraceLog(null)` / `new TraceLogFactory(null)` 拒绝（§7.3 措辞） | `TraceLogTest.nullDelegateShouldReject`、`TraceLogFactoryTest.nullDelegateShouldReject` |
| 10 | 替换生成器产出违例不校验、按原值绑定 | **间接覆盖**：`web.TraceHandlerTest.extractedValueShouldBeBound` 证明非契约格式值（`"upstream-trace-id"`）按原值绑定不拦截；「生成器产出违例」路径无直接用例（见 §4 余量说明） |
| 11 | 未做任何配置直接调用即正常工作 | `TraceKitTest.shouldWorkWithoutAnyConfiguration` |
| 12 | `next == null` 安全结束 | `web.TraceHandlerTest.nextNullShouldEndSafely` |
| 13 | 重复 / 对未绑定线程 clear 幂等 | `TraceKitTest.clearShouldBeIdempotent`、`TraceContextTest.clearShouldBeIdempotent` |

补充契约点（§7.1 要点）由以下用例覆盖：startSpan 覆盖且不影响 traceId（`TraceKitTest.startSpanShouldOverrideSpanIdWithoutTouchingTraceId`）；init 返回值恒为生效值（`TraceKitTest.initShouldReturnEffectiveTraceId`）；Supplier 打印时刻求值（`TraceLogTest.supplierVariantsShouldEvaluateAtPrintTimeForAllLevels` + 正向时序 `supplierShouldReadContextAtFlushNotAtCall`——求值计数器证明调用时刻不求值）；`isXxxEnabled` / `getName` 透传（`TraceLogTest.enabledFlagsAndNameShouldPassThrough`）；生成器替换生效与恢复（`TraceKitTest.setGeneratorShouldTakeEffect`）。

---

## 3. 验证命令（模块根目录 `aifei-trace/` 执行）

```bash
mvn clean verify                       # 全量 49 例，必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest   # 仅架构门禁（15 例 = 10 门禁 + 5 自证）
mvn dependency:tree -Dverbose          # 依赖面：根 + aifei-all(provided) + junit(test)
```

class 版本抽验（AC-03）：

```bash
javap -verbose target/classes/cn/hg/aifei/trace/TraceKit.class | grep "major version"   # Git Bash/Linux；Windows cmd 用 findstr "major version"
# 全部主 class 应输出 major version: 52
```

**surefire 必须在 `aifei-trace/` 模块根目录执行**：`ArchitectureBoundaryTest` 以相对路径 `src/main/java` / `src/test/java` 扫描源码（pom 已设 `workingDirectory = ${project.basedir}`）；在其他目录执行会因「源码目录不存在」而失败——这是设计内的防呆断言，不是环境问题。

---

## 4. 离线纪律与宽容余量

- **离线纪律**：全部测试零网络请求。门禁⑩（`testSourcesOfflineOnly`）剥注释后扫描测试源码，代码字面量里的 URL host 只允许 `127.0.0.1` / `localhost`；版权头 gitee 地址在注释中被剥离，不参与判定。断网环境 `mvn clean verify` 全绿（仅 Maven 自身可能查仓库，可配合 `-o` 离线模式）。
- **无等待**：测试无 `Thread.sleep`、无锁等待；生成器唯一性断言用 1000 次 UUID 采样，毫秒级完成。
- **宽容余量（已知缺口，均有兜底）**：
  1. **§7.2 场景 10 的「生成器路径」**：未单独构造「自定义生成器返回非 32 位格式 → 模块原样绑定」用例；已由 extractor 路径（`extractedValueShouldBeBound` 绑定非契约格式值）间接证明「绑定不校验格式」。若未来需要直接覆盖，可在 `TraceKitTest` 增加一个返回任意字符串的桩生成器用例。
  2. **门禁⑧的「instanceof 模式匹配」片段**：design.md §10 ⑧文本提及，实现断言清单未含该片段（属 Java 16+ 语法，`javac -source 1.8` 本身会拒编，双保险少一层文本断言，不构成放宽）。
  3. 门禁②的「禁止其余 `cn.aifei.*` 框架包」按前缀枚举（core / log 之外的 `cn.aifei.*` 一律失败），与 §10 ② 口径一致。

> 以上余量均**不改变**契约语义，列为维护者知悉项；新增公共类型 / 放宽门禁须先改 `docs/design.md` §5 / §10。
