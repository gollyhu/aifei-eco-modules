# aifei-trace 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

三份文档分工：`README.md` 面向使用者（安装、用法、手动传递指引）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者。

## 开发前提

- **改代码前必读 `docs/design.md`**（尤其 §5 的 8 类型逐字签名、§7 的行为契约与异常语义表、§10 的门禁 10 例）。需求与设计冲突时，**先改设计决策再改代码**。
- 未经设计决策，不得自行确定契约类行为（空值语义、异常类型与消息、生成格式、提取失败处置、前缀形态等），也不得新增或扩大公共 API 面。

## 交付性质

`aifei-trace` 是 aifei 生态的**库模块（可被应用依赖的 SDK）**，**不是应用工程**：没有 `AppConfig` / `Launcher` / `service/` 包 / `webapp/` / HTTP 路由 / HIO / 表结构 / Db+Row / Generator / UI 页面；也没有 Plugin、配置键、internal 包、SPI。坐标 `io.github.gollyhu:aifei-trace:1.0.0-SNAPSHOT`（`packaging=jar`），全模块 8 公共类型即全部实现。

**不适用范围（不得据此模块约束要求）**：表结构 / `Db+Row` / `Generator`（零 DB）；`@Path` Service 与 HIO（`TraceHandler` 是挂在使用方 Handler 链上的接入构件，不是路由）；页面 / 原型基线（`aifei-trace-demo` 单页由前端直接实装，不经原型与 QA 基线门禁）；Plugin 装配（trace 无连接资源、无配置可解析，装配动作全部发生在使用方 AppConfig 内）。

## 硬性约束

### 技术基线

- **Java 8 双约束（语法 + API）**：`source`/`target` = `1.8`；主 JAR 内全部 class major = **52**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching；禁 `List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂与其它 Java 8 后 API。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、异常消息）；原创文件写统一版权头，既有版权块不得改写。
- **零第三方**：pom 只允许 `cn.aifei:aifei-all`（**`provided`**，不向下游传递，消费方自带）+ `junit`（`test`）。**不得**新增 `optional` / `compile` 第三方依赖；**不得引入任何日志绑定**（`slf4j` / `logback` / `log4j` 一律禁止——模块只面向 `cn.aifei.log` 接口）；**不声明 `cn.aifei:aifei-log`**（Log / LogFactory 接口在内核 `cn.aifei:aifei` 中，随 aifei-all 提供）。
- **零 DB**：全模块禁 `import java.sql.`。
- **禁改写 JVM 全局状态**：不得出现 `System.setProperty` / `System.getProperties().put` / `System.setErr` / `System.setOut`。
- **无 Plugin / 无 SPI / 无 `META-INF/services`**：模块无装配插件、无配置键、无自动装配；`src/main/resources` 下不得出现 `META-INF/services/` 目录。
- **异常与失败语义全面「不失败」**：模块方法**不声明、不抛出**任何模块异常类型（无 `TraceException`，不存在也**不得引入**）；受检异常仅在 `TraceHandler.handle` 签名上透传（override 框架签名）；调用方参数写错用 `IllegalArgumentException`，消息措辞**逐字**对齐 `docs/design.md` §7.3 表（`generator can not be null.` / `delegate can not be null.`），不得各写一套。
- **测试纪律**：全部测试零联网、无秒级 `Thread.sleep` 堆叠；`TraceKitTest` 等替换生成器的用例必须在 `@After` 恢复 `DefaultTraceIdGenerator.INSTANCE`（测试间隔离）。

### 公共面收敛（不得放宽）

- 根包 `cn.hg.aifei.trace` **精确 6 个公共类型**：`TraceKit` / `TraceContext` / `TraceIdGenerator` / `DefaultTraceIdGenerator` / `TraceLog` / `TraceLogFactory`；`cn.hg.aifei.trace.web` **精确 2 个**：`TraceHandler` / `TraceIdExtractor`。**不得增删、不得改名**；新增/变更须先改 `docs/design.md` §5 并过设计决策。
- **公共面框架类型例外（已登记，根 `AGENTS.md`「已登记的公共面框架类型例外」清单）**：`TraceLog` / `TraceLogFactory` 签名暴露 `cn.aifei.log.Log` / `LogFactory`（仅限根包这两个文件）；`web.TraceHandler` / `web.TraceIdExtractor` 暴露 `cn.aifei.core.Handler` / `Input` / `Output`（仅限 web 包这两个文件）。除这两组外，公共面**零第三方类型**。新增例外必须先在根 `AGENTS.md` 登记并改 `docs/design.md` §2，再改门禁 ②。
- **无 internal 包**：8 公共类型即全部实现（对 aifei-http 范式的有意偏离，见 design.md §2 决策 5）；不得引入任何隐藏实现类。
- `TraceKit` 是 Kit 判据的**显式命名例外**：无装配生命周期、任何时刻调用成立、永不抛「未装配」异常（对齐官方 LogKit / TraceKit 形态）；不得据此把其它需要装配的类型照此命名。
- 生成器契约（非空、仅 `[0-9a-f]`、固定 32 位小写十六进制）由 `TraceIdGenerator` javadoc 声明，**模块运行期不校验产出**（决策 7）。

## 包边界与门禁

`ArchitectureBoundaryTest`（**10 例**）扫描 `src/main/java`（`testSourcesOfflineOnly` 扫 `src/test/java`）全部 `.java`：

| # | 测试方法 | 断言 |
| --- | --- | --- |
| ① | `noThirdPartyReference` | 每条 `import` 必须以 `java.` / `javax.` / `cn.aifei.` / `cn.hg.aifei.trace.` 开头（本模块**零第三方**，无放行包） |
| ② | `frameworkScopeRules` | `import cn.aifei.core.*` 只允许出现在 `web` 包；`import cn.aifei.log.*` 只允许出现在根包且不得出现在 `web` 包；其余任何 `cn.aifei.*` 框架包一律失败 |
| ③ | `publicSurfaceExact` | 根包**直接子文件**精确 **6** 个且文件名集合精确相等；`web` 直接子文件精确 **2** 个且集合精确相等 |
| ④ | `noSqlReference` | 全模块不得出现 `import java.sql.` |
| ⑤ | `noJvmGlobalPropertyWrite` | 不得出现 `System.setProperty` / `System.getProperties().put` / `System.setErr` / `System.setOut` |
| ⑥ | `threadLocalOnlyInTraceContext` | `ThreadLocal` 字面量只允许出现在 `TraceContext.java` |
| ⑦ | `uuidOnlyInDefaultGenerator` | `UUID` 字面量只允许出现在 `DefaultTraceIdGenerator.java` |
| ⑧ | `java8Compatibility` | 不得出现 `List.of(` / `Map.of(` / `Set.of(` / `record ` / `var ` / `"""` / `sealed ` 等 Java 8 后语法与 API |
| ⑨ | `noServiceProvider` | 不得出现 `ServiceLoader` / `META-INF/services` 字面量；`src/main/resources` 下不得存在 `META-INF/services/` 目录 |
| ⑩ | `testSourcesOfflineOnly` | 测试源码中的 `http://` / `https://` 字面量，host 只能是 `127.0.0.1` 或 `localhost`（注释先剥离，版权头地址不误判） |

门禁**不得为兼容错误用法而放宽或绕过**；扫描依赖相对路径 `src/main/java`，**必须在 `aifei-trace/` 根目录执行**。

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| ThreadLocal 字段与 set/get/remove（null = 移除） | `TraceContext` |
| `init` 的 trim 与空白回退生成语义、`startSpan` 覆盖语义 | `TraceKit#init` / `TraceKit#startSpan` |
| 生成器字段、替换入口与 null 校验 | `TraceKit#setTraceIdGenerator` |
| TraceId 生成算法（UUID 去横线） | `DefaultTraceIdGenerator#generate` |
| 生成格式契约（非空 / `[0-9a-f]` / 32 位） | `TraceIdGenerator` javadoc |
| `[traceId] ` 前缀拼装与「缺失 → 空串」 | `TraceLog#prefix` |
| Supplier 懒求值包装形态 | `TraceLog` 各 Supplier 变体 |
| LogFactory 装饰与「显式 delegate 防自包装」约定 | `TraceLogFactory` |
| extractor 异常吞并回退、finally 清理、`next == null` 安全结束 | `web.TraceHandler#handle` |
| 提取契约（null 语义、Input 无 header 边界） | `web.TraceIdExtractor` javadoc |
| `IllegalArgumentException` 消息措辞 | `docs/design.md` §7.3 表（各抛出点按表逐字实现，不各写措辞） |

## 行为契约要点速查

- **ThreadLocal 刻意不继承**：禁用可继承 ThreadLocal（线程池复用串号）；跨线程 / 跨模块必须手动传递（README §4 场景一 / 场景三）。
- **请求结束必须 clear**：`TraceHandler` 在 finally 保证；手动 `init` 的场景业务自行 try/finally 清理。
- **traceId 绑定前 trim**：`init("  abc  ")` 绑定 `"abc"`；`init(null/""/"   ")` 回退生成；返回值恒为最终生效值。
- **spanId 单层覆盖**：`startSpan()` 再次调用覆盖旧 spanId，不影响 traceId；不做 Span 树。
- **前缀拼装在打印时刻**：String 变体调用时刻拼接；Supplier 变体懒求值（打印时刻读上下文）；TraceId 缺失时消息原样透出（空前缀、不报错不打警告）。
- **TraceHandler 执行序**：提取（异常吞掉回退）→ `TraceKit.init` → `next` 透传 → finally `clear()`；下游异常**原样传播**（不捕获不包装）；`next == null` 安全结束；**必须第一个 addHandler**。
- **未绑定返回 null 不抛异常**：`getTraceId()` / `getSpanId()` / 未配置直接调用均成立（TraceKit 无装配生命周期）。
- **产出不校验**：替换生成器产出格式违例时按原值绑定、不拦截。
- **无模块异常**：全部失败路径为静默回退 / 吞掉；`IllegalArgumentException` 仅三处 null 入参（§7.3 表）。

## 注释规范

- **语言**：一律简体中文；保留英文原文的只有 javadoc 标签名、代码标识符、异常消息。
- **javadoc**：公共类型与公共成员**必须**书写并写全 `@param` / `@return` / `@throws`；行为契约（空值、回退、异常、线程安全）写在 javadoc 而非行注释。
- **精简**：只在代码无法表达「为什么」时写注释；不复述下一行代码、不写行尾注释、不留被注释掉的死代码 / TODO / 占位符。

## 验证

**命令在 `aifei-trace/` 根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
mvn clean verify                                     # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest             # 仅跑架构门禁（10 例）
mvn test -Dtest=TraceKitTest                         # 单用例：类名或 类名#方法名
mvn dependency:tree                                  # 断言：仅 aifei-all(provided) + junit(test)
```

构建后可用 `javap -verbose` 抽查主 class major = 52。

> **零联网纪律**：全部单测必须断网全绿；禁秒级 `Thread.sleep` 堆叠；测试桩按 `aifei-http` 的 support/ 范式自建（如 `support.RecordingLog`、伪 Handler 链 + 伪 Input/Output），不引入任何服务器或外部服务。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 手动传递指引）；公共 API 签名变更须先改 design.md §5 并过设计决策，**严禁先斩后奏**。
- 术语必须与 `docs/design.md` §3 统一：TraceId / SpanId / 链路上下文 / 生成器 TraceIdGenerator / 提取器 TraceIdExtractor / 接入处理器 TraceHandler / 日志装饰器 TraceLog / 回退生成 fallback。
- 模块为**单实现**，根 `AGENTS.md` 的「多实现契约底线 / 允许差异双清单」条款不适用；若未来引入第二实现（如分布式生成器），须先在 design.md 补共同底线与允许差异双清单。
- 不得因 demo 页面需求回头要求模块新增公共 API（design.md §12 事项 8）。
