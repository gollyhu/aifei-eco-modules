# AGENTS.md — aifei-i18n 开发约束

本文件面向在本模块内工作的开发 / 测试代理。事实来源：`docs/design.md`（公共 API 逐字签名、唯一定义点、
异常逐字表、门禁断言、任务分解）、`docs/prd.md`（需求，含 §6.1 的 16 条 P0 / 75 条 AC 矩阵与 §十一 Enjoy 模板指令增量）。
本文件只列硬约束与操作纪律，不重复契约细节。

## 一、模块性质

- 这是**库模块（SDK）**，不是应用工程。无 `AppConfig` / `Launcher` / `service/` / `webapp/` / 路由 / HIO / 表实体 / Generator。
- 本模块不提供 HTTP 端点；语言包下发由消费方 Just Service 一行完成。
- 坐标 `io.github.gollyhu:aifei-i18n:1.0.0-SNAPSHOT`，包名 `cn.hg.aifei.i18n`，Java 8（class major 52）。

## 二、公共面（精确 10 类型，不得增删）

- 根包 7：`I18nKit` / `Localizer` / `FormatKit` / `MessageSource` / `LanguageBundle` / `DateStyle` / `I18nException`。
- `web` 2：`I18nHandler` / `LocaleExtractor`；`aifei` 1：`I18nPlugin`。
- `internal` / `defaultSource` / `enjoy/directive` 为非承诺实现，不得升为公共类型。
- 公共签名零第三方类型、零框架类型；`cn.aifei.*` 只允许在 `web`（core）与 `aifei`（Prop/Plugin）包。
- `Localizer` 仅 import `internal.I18nHolder` 与 JDK；构造为包级（仅同包 `I18nKit.local` 可 new），不可变可复用，无 getter。

## 三、唯一定义点（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| `key` 非空校验（空/空白 → IAE，消息 §8.3 逐字） | `internal.I18nEngine#message` |
| 配置键名 / 默认值 / 解析 / 校验 / 非法消息 | `internal.I18nConfig` |
| locale 规范化 + 严格 BCP 47 校验 | `internal.LocaleNormalizer` |
| 生效 locale 四级优先级 + Accept-Language 解析 | `internal.LocaleResolver` |
| ThreadLocal 持有 | `internal.LocaleContext` |
| 回退链两轮 + DEBUG/WARN 分级 | `internal.FallbackChain` |
| 缓存键 / TTL / 全清 / 空串与未命中区分 | `internal.CacheLayer` |
| `{0}` 位置插值 | `internal.MessageInterpolator` |
| 整包 key 排序（TreeMap 自然序）+ 合并 + 跳过 WARN | `internal.BundleExporter` |
| `localeName` / `dir`（JDK API + 降级） | `internal.LocaleMeta` |
| 档位映射 / 币种小数位 / 币种与时区校验 / 格式化 locale 解析 | `internal.FormatSupport`（locale 再委派 LocaleResolver） |
| 来源稳定名注册 / 重名 / 依赖探测 | `internal.SourceRegistry` |
| 运行期引擎持有与「未装配」消息 | `internal.I18nHolder` |
| 日志获取（`Log.get(本类.class)`，禁 static / System.err） | `FallbackChain` / `BundleExporter` / `LocaleMeta` / `I18nEngine` / `PropertiesSource` |

异常消息逐字表见 `docs/design.md` §8.3，各抛出点按表实现，不各写措辞。

## 四、硬性编码纪律

- 全程简体中文注释与文档；类名 / 包名严格 `cn.hg.aifei.i18n`。
- Java 8 语法与 API 双约束：禁 `List.of` / `Map.of` / `var` / 文本块 / record 等 Java 9+ 特性。
- 无 SPI / ServiceLoader / `META-INF/services`；来源扩展走 `I18nPlugin#addSource` 显式注册。
- 不 import `java.sql.*`；db 来源走框架 `cn.aifei.db.core.Db/Row`。
- 框架 `cn.hg.aifei.cache.CacheKit`、`cn.aifei.db.core.*` 只在对应默认来源的**方法体内**用全限定名引用，
  字段与签名只出现 JDK / 本模块类型（保证 optional 依赖缺失时类可加载）。
- 日志惰性获取并做异常隔离；DEBUG 仅「未命中」，WARN 仅「故障 / 降级」，分级不得混用。
- `dir` 只走 JDK `ComponentOrientation`，禁自建语言→方向表；不可用时降级 `ltr` + WARN。
- 格式化零自建规则数据：无日期 / 数字 pattern 常量、无货币符号表、无方向表、无货币小数位表。

## 五、门禁与验证

- `ArchitectureBoundaryTest`：13 例源码文本 + 反射门禁；`ClassFileVersionTest`：全部 class major = 52。
  门禁位于 `src/test/java/cn/hg/aifei/i18n/`，须在模块根执行（surefire `workingDirectory=${project.basedir}` 保证）。
- 门禁**不得为兼容错误用法而放宽**；新增例外必须先改 `docs/design.md` §11 再改门禁。
- 唯一门禁例外：`cn.aifei.enjoy.*` 仅允许出现在 `enjoy/directive/`（design.md §16.8.1）；`cn.aifei.log.*` 另允许 design.md §6.3 登记的 WARN 记录类所在包。
- 测试零联网：测试源码中的 URL host 只能是 `127.0.0.1` / `localhost`；无真实 DB / KV 外部服务依赖。
- 验证命令：`cd aifei-i18n && mvn clean verify`，必须 0 failure / 0 error。

## 六、变更纪律

- 修改公共面签名 / 配置键 / 异常消息 / 门禁口径前，先改 `docs/design.md` 并走设计决策。
- 每批改动后保持公共面签名不变；若签名不可行先回写设计再改代码。
- 缺陷被退回时修源码；测试本身明显写错时可与测试角色确认后修测试。
