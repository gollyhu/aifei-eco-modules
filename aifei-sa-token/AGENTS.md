# aifei-sa-token 开发约束

本文件适用于整个模块，供开发代理和项目贡献者使用。

## 开发前提

- 开始设计或修改代码前，必须先阅读 `docs/design.md` 与 `docs/testing.md`。
- `docs/design.md` 是设计目标、约束与决策的事实来源。
- 需求与现有设计文档冲突时，应先明确并更新设计决策，再修改代码。

## 硬性约束

- 保持分层边界：`cn.aifei.aop.*` 的 import 只允许出现在 `cn.aifei.satoken.aifei` 包。
- `io.undertow.*` 的 import 只允许出现在 `cn.aifei.satoken.undertow` 包；不得放宽该门禁来「兼容」错误用法。
- `cn.hg.aifei.cache.*` 的 import 只允许出现在 `cn.aifei.satoken.dao` 包；持久层不得反向引用 Web Server 与拦截器。
- `cn.aifei.vip.*`、`java.sql.*` 零引用。
- **不得擅自更换上下文挂载点**（当前为装饰 `Dispatcher`）。挂载点的选择直接决定请求/响应能力边界，属设计决策，见 `docs/design.md` §2.1。
- 上下文清理必须在 `finally` 中完成（`clearContext` + `clearInput` + `clearExchange`），否则线程复用会串号。
- 桥接对象（`SaRequest` / `SaResponse` / `SaStorage` 实现）必须无状态，请求相关数据放线程变量或 `exchange`。
- 不得在桥接层调用 `exchange.endExchange()`：响应输出由消费方 IoHandler 统一负责。
- 每类规则只允许一个唯一定义点：配置映射（`SaTokenConfigLoader`）、异常分类（`SaTokenErrorSupport`）、路径匹配（`PathPatternMatcher`）、鉴权失败语义（`spi.SaTokenErrorHandler`）、超时换算与搜索分页（`SaTokenDaoForCache`）。实现类不得复制这些规则。
- 未经设计决策，不得自行确定：失败响应的状态码语义、上下文清理顺序、路径匹配语义、插件启动/停止的还原范围、Sa-Token 超时值与缓存 TTL 的换算语义。
- 公共 API 保持极简：不暴露 Undertow 与 Sa-Token 的内部数据结构，不新增转发型工具类。
- 项目代码、依赖和 API 必须兼容 Java 8。
- `cn.aifei:aifei-all` 与 `io.undertow:undertow-core` 必须保持 `provided`；不得为省事改成 `compile` 向下游传递框架 JAR。
- `io.github.gollyhu:aifei-cache` 必须保持 `optional`；不得改为 `compile` 把所有使用者的运行时都绑上缓存组件。

## 文档维护

- 面向使用者的定位、装配步骤与已知限制维护在 `README.md`。
- 设计目标、行为契约与关键决策维护在 `docs/design.md`；测试策略与用例清单维护在 `docs/testing.md`。
- 新增或改变公共行为时，应同步更新设计文档、测试文档与测试。

## 验证

```bat
cd aifei-sa-token
mvn clean test
```

提交前必须 0 failure / 0 error。架构门禁用例依赖相对路径 `src/main/java`，因此**必须在模块目录内执行**。
