# AGENTS.md — aifei-audit 开发约束

> 面向开发代理。契约事实来源：`docs/design.md`；需求：`docs/prd.md` 与 `docs/需求/aifei-audit.md`。

## 硬约束

1. **Java 8**：`source/target 1.8`；禁 `var` / `record` / `switch` 表达式 / text block / `List.of` / `Map.of`。
2. **依赖纪律**：模块 pom 只有 `cn.aifei:aifei:1.1.2`（`provided`）+ junit（test）。零第三方 compile / runtime；test scope 也不得出现任何日志绑定。
3. **公共面**：根包顶层 public 类型精确 4 个（`Audit` / `AuditEvent` / `AuditKit` / `AuditException`），`spi` 精确 3 个，`aifei` 2 个。新增公共类型须先过设计决策；门面必须是 Kit，不得改名。
4. **唯一定义点**：事件构造（`internal/EventFactory`）、脱敏截断（`internal/Truncator`）、成败判定兜底（`internal/ResultPolicy`）、失败隔离 `catch`（`internal/SafeInvoker` / `internal/AuditSpi` / `internal/AuditCollector#intercept`）、事件号（`internal/EventIds`）、时钟（`internal/Clock`）、配置键与默认值（`internal/AuditConfig`）、注解属性读取（`internal/AuditAnnotations`）。实现类不得复制这些规则。
5. **`catch` 白名单**：`src/main/java` 中只有三个文件可出现 `catch (`，且 `AuditCollector` 的每个 `catch` 块必须在 200 字符内 `throw`。新增隔离点须同步门禁与设计文档。
6. **框架类型边界**：`cn.aifei.*` 只在 `aifei` 包；`internal` 内**唯一**登记点是 `SafeInvoker` → `cn.aifei.log.*`（白名单恒 1 条，多一条即门禁红）。需要框架能力的另外两处一律经端口由接入包注入：`AuditInvocation`（调用现场）、`AuditEnvironment`（容器查找 + 包扫描）。不许为其它 `internal` 类型开口子；改结构必须同步 `ArchitectureBoundaryTest`。
7. **日志**：只用 `SafeInvoker#warn`（惰性获取、获取失败吞掉）。禁 `System.out` / `System.err` / `printStackTrace`、禁任何日志绑定。
8. **零线程 / 零存储**：不得创建线程、线程池、定时器；不得出现 `java.sql.`。
9. **失败隔离口径**：一次失败一条 `warn`；`fail-fast` 只覆盖落点契约违约；业务异常永远原样上抛（`assertSame`）。
10. **采集时机**：入参只能在 `inv.invoke()` 之后读取，采集全部在 `finally`；`deliver` 先判断 `businessFailed` 再决定是否上抛落点异常。
11. **`internal` 可见性与定位**：全部类型 `public` 是 Java 跨包硬约束（根包与 `aifei` 包必须调用），属**非承诺 API**；消费方不得引用 `cn.hg.aifei.audit.internal.*`，模块不保证其兼容性。

## 测试纪律

- 每个测试类 `@BeforeClass` 装 `RecordingLogFactory`，`@AfterClass` 还原（框架默认 `NoLogFactory` 取日志直接抛异常）。
- 断言「恰一条告警」用 `RecordingLogFactory.count("warn")`，不要抓标准输出。
- 注解路径单测不启动 HTTP：`AuditTestSupport.invocation(...)` 按 `Action + Invocation` 真实形态驱动。
- 框架拒绝同类型二次注册：`AuditSink` 只在 `ConfigAndLifecycleTest` 里注册一次，其余用例直接装运行期快照（`AuditHolder`）。

## 门禁

`mvn clean verify` 会跑 `ArchitectureBoundaryTest`（G-01~G-15），扫描根为模块根下相对路径，**必须在模块根目录执行**。
