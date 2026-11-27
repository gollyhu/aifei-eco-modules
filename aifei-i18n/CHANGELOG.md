# 变更日志 — aifei-i18n

## 1.0.0（2026-11-19）

首个正式版本。坐标 `io.github.gollyhu:aifei-i18n:1.0.0-SNAPSHOT`，Java 8（class major 52），零第三方 compile 依赖。

### 新特性

- **开放来源扩展**：实现 `MessageSource` 并经 `I18nPlugin#addSource` 显式注册即可接入新来源，模块源码与 pom 零改动。
- **三个默认来源**：`properties`（零第三方、启动期全量快照）、`db`（只读、参数化查询、支持运行期刷新）、`kv`（基于 `aifei-cache`，不可列举时整包导出跳过并告警）。
- **唯一回退链**：`按 locale 逐来源 → 默认 locale 逐来源 → 返回 key 原文`，缺译永不抛异常；未命中记 DEBUG、来源故障记 WARN，两级严格区分。
- **单条取文案与整包导出同源同值**：`I18nKit.message(...)` 取单条，`I18nKit.bundle(...)` 导整包；语言包四字段固定、`messages` key 稳定排序、`{0}` 占位符原样保留。
- **Locale 上下文贯通**：显式 > 上下文 > Accept-Language > 默认 locale 四级优先级；Web 入口 `I18nHandler` 绑定上下文并在 `finally` 无条件清理，线程复用不串语言。
- **JDK 格式化薄封装**：数值（含百分比）/ 货币 / 日期 / 日期时间四门面，SHORT / MEDIUM / LONG / FULL 四档默认 MEDIUM；币种与时区为独立参数，不自建任何规则数据。
- **架构门禁随构建自动执行**：源码文本 + 反射双重门禁 13 例与 class major 52 门禁 1 例，禁止越层引用与 Java 8+ 字节码。

### 变更

无（首个版本）。

### 修复

无（首个版本）。

### 已知问题 / 限制

- `properties` 来源为启动期全量快照，改值须重启应用才生效；需热更新请放 `db` / `kv` 来源并调用 `I18nKit.refresh()`。
- 模块不提供 HTTP 端点，前端语言包下发由消费方 Just Service 一行写成 JSON（契约见 README §二.5）。
- 跨 JDK 格式化结果由 JDK 自带 locale 数据决定，不承诺字节级一致；建议统一指定 `-Djava.locale.providers=CLDR`。
- `dir` 判定依赖 `java.awt.ComponentOrientation`（`java.desktop`）；jlink 裁剪运行时镜像须保留该模块，否则降级为 `ltr` + WARN，不阻断导出（Java 8 无模块系统，不受影响）。
- `i18n.locales` 白名单**仅用于请求头过滤**，不阻断显式 locale 的整包导出；导出合法性只要求显式 locale 为合法 BCP 47。

## 构建与交付产物

- **构建命令**：`cd aifei-i18n && mvn clean verify`。实测输出关键行：`Tests run: 126, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`。
- **产物**：`target/aifei-i18n-1.0.0.jar`。内含 32 个主源文件编译产物及 Maven 元数据（`pom.xml` / `pom.properties`）。
- **版本核对**：`javap -verbose` 抽验 `cn.hg.aifei.i18n.I18nKit` → `major version: 52`；`pom.xml` 为 `1.0.0`，与坐标 `io.github.gollyhu:aifei-i18n:1.0.0-SNAPSHOT` 一致。
- **环境要求**：JDK 8+（class major 52）、Maven 3.6+；`cn.aifei:aifei:1.1.2` 由消费方提供（`provided`）；仅用 `kv` 来源时才需 `io.github.gollyhu:aifei-cache:1.2.0-SNAPSHOT`。

## 交付物清单

| 类别 | 文件 |
| --- | --- |
| 文档 | `README.md`（面向使用者）、`AGENTS.md`（模块开发约束）、`docs/design.md`（设计事实来源）、`docs/需求/aifei-i18n.md`（需求与 16 条 P0 / 75 条 AC 矩阵）、`docs/testing.md`（QA 独立复验报告）、`CHANGELOG.md`（本文件） |
| 构建 | `pom.xml`（坐标 1.0.0，Java 8 编译目标，门禁 surefire 配置） |
| 主源（32） | 根包 7：`I18nKit` / `Localizer` / `FormatKit` / `MessageSource` / `LanguageBundle` / `DateStyle` / `I18nException`；`web` 2：`I18nHandler` / `LocaleExtractor`；`aifei` 1：`I18nPlugin`；`internal` 13：`I18nConfig` / `LocaleNormalizer` / `LocaleResolver` / `LocaleContext` / `FallbackChain` / `CacheLayer` / `MessageInterpolator` / `BundleExporter` / `LocaleMeta` / `FormatSupport` / `SourceRegistry` / `I18nHolder` / `I18nEngine`；`defaultSource` 3：`PropertiesSource` / `DbSource` / `KvSource`；`enjoy/directive` 6（非承诺）：`I18nDirective` / `LangDirective` / `DirDirective` / `DirectivePageState` / `DirectiveSupport` / `DirectiveRegistrar` |
| 测试（14：11 个测试类 + 3 个测试替身） | `I18nKitTest` / `FormatKitTest` / `LocaleResolverTest` / `BundleExportTest` / `I18nPluginTest` / `I18nHandlerTest` / `ArchitectureBoundaryTest` / `ClassFileVersionTest` / `QaConsistencyTest` / `QaLogLevelTest` / `enjoy/directive/DirectiveTest`；`support`：`FakeMessageSource` / `FakeDbAccessor` / `RecordingLogFactory` |
| 产物 | `target/aifei-i18n-1.0.0.jar` |
| 仓库根同步 | `README.md`（模块总览表 / 构建清单 / 依赖说明各 1 行）、`AGENTS.md`（适用范围模块清单 1 处 + 公共面框架类型例外 1 处） |

## 集成与启动说明

### 1. 依赖引入

```xml
<dependency>
  <groupId>io.github.gollyhu</groupId>
  <artifactId>aifei-i18n</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

消费方须自带 `aifei:1.1.2`（`provided`，不传递）。仅用 `kv` 来源时再显式引入 `aifei-cache:1.2.0-SNAPSHOT`（`optional`，不传递）；未声明 `kv` 时无需该依赖。

### 2. 最小装配

在 `config(Plugins plugins)` 中注册插件，并在 `settings` 上把 `I18nHandler` 作为**第一个** `addHandler`（顺序颠倒会导致请求头 Locale 解析失效）：

```java
settings.addHandler(new I18nHandler<InputImpl, OutputImpl>(
        input -> input.getHeader("Accept-Language")));   // 必须第一个 addHandler
plugins.add(new I18nPlugin(prop));
```

四个配置键（恰好 4 个，无启用开关）：

| 配置键 | 含义 | 必填 / 默认值 |
| --- | --- | --- |
| `i18n.defaultLocale` | 回退链终点语种 | **必填**，缺失即启动失败 |
| `i18n.locales` | 请求头匹配白名单 | 可选，默认不设白名单 |
| `i18n.sources` | 来源稳定名有序清单（顺序即叠加/回退顺序） | 可选，默认仅 `properties` |
| `i18n.cache.ttlMillis` | db / kv 懒缓存 TTL（毫秒） | 可选，默认 `60000`；`≤ 0` 不缓存 |

### 3. 来源数据与资源

- **db 来源**：模块只读、不执行 DDL，建表 SQL 见 `README.md` §三「db 来源建表 SQL」（`i18n_message` 表，`PRIMARY KEY (locale, msg_key)`），由消费方自行执行。
- **properties 来源**：classpath 根下 `i18n.properties`（UTF-8），每行 `<locale>|<key>=<原文>`；**改值须重启应用**才生效。
- **kv 来源**：不可全量列举，整包导出跳过该来源并记 WARN。

### 4. JVM 与裁剪

- 推荐统一指定 `-Djava.locale.providers=CLDR`，消除 Java 8（JRE）与 Java 9+（CLDR）默认 provider 差异。
- Java 9+ 用 jlink 裁剪运行时镜像时须保留 `java.desktop`（`dir` 判定依赖 `java.awt.ComponentOrientation`）。

### 5. 启动验证方法

本模块是库（SDK），无独立进程。按以下顺序验证「起来了」：

1. 在模块根执行 `mvn clean verify`，输出 `BUILD SUCCESS` 且 `Tests run: 126, Failures: 0, Errors: 0`。
2. 消费方应用装配 `I18nPlugin` 后，调用 `I18nKit.local("zh-CN").message("login.title")` 返回译文而非 key 原文；`I18nKit.bundle("zh-CN")` 得到四字段齐全的语言包。
3. Web 场景确认 `I18nHandler` 为链上第一个 Handler，且 `Accept-Language` 能改变文案语种。

### 6. 回滚路径

移除 pom 中的 `aifei-i18n` 依赖与 `I18nPlugin` / `I18nHandler` 装配即可，**不涉及数据库 DDL**（db 来源只读，模块从不执行 DDL，无需删表）。如此前引入过 `aifei-cache` 仅为 `kv` 来源服务，可一并移除。回滚不产生残留数据或迁移步骤。
