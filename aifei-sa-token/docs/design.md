# aifei-sa-token 设计文档

> 版本：1.0.0 ｜ 关联：`README.md`（定位与用法）、`docs/testing.md`（测试）

## 1. 背景与目标

Sa-Token 是一站式 Java 认证鉴权框架（认证、权限、会话、注解鉴权等）。它通过 `SaTokenContext` 抽象屏蔽 Web 框架差异，但官方 starter 全部面向 Servlet 或 Spring 生态（`sa-token-servlet`、`sa-token-spring-boot-starter`、`sa-token-solon-plugin` 等），**没有 aifei 的现成实现**。

aifei 的两个特征使常见接法都不成立：

- **抛弃 Servlet**：命令式服务是 Undertow，无法复用 `SaRequestForServlet` 等实现；
- **HIO 属应用侧代码**：`Input` / `Output` / `IoHandler` / `Dispatcher` 由应用自行实现，SDK 不能依赖具体类型，且公开源码中无企业级 HIO（Header / Cookie / 会话能力缺失）。

`aifei-sa-token` 的目标是把 Sa-Token 接进 aifei：

- 消费方**不必改造自有 HIO**，也不必为 Sa-Token 编写模型适配类；
- 消费方只写鉴权规则与失败响应，不接触上下文桥接细节；
- 与 Sa-Token 官方配置、注解、`StpUtil` 用法保持一致，降低迁移成本。

## 2. 选型与技术决策

| 决策 | 结论 | 理由 |
|---|---|---|
| 引入方式 | 引入 `sa-token-core`（compile），**不引任何 starter / servlet 插件** | 只用核心的认证鉴权与会话能力；starter 会把 Servlet 依赖与 Spring 生态带入 |
| 上下文挂载点 | **装饰 `cn.aifei.server.Dispatcher`** | 见 §2.1，这是本设计最关键的决策 |
| 装配入口 | 实现 `cn.aifei.plugin.Plugin`，构造期只持 `Prop` | 对齐仓库根 `CODEBUDDY.md` 的框架集成共性：配置解析发生在 `start()`，不留半装配状态 |
| 路由匹配 | 自研 `PathPatternMatcher`（Ant 风格），**不覆盖** `SaStrategy.instance.routeMatcher` | aifei 的 `cn.aifei.router.Router` 只做精确匹配、`cn.aifei.util.PathUtil` 是文件系统工具，无 URL 通配匹配可复用；自研后无需污染 Sa-Token 全局策略 |
| 鉴权失败响应 | 默认置 HTTP 401（未登录）/ 403（其余），可经 SPI 换成业务码 | 挂载点在 Dispatcher 层，拿到 `HttpServerExchange` 后状态码可直接写；同时保留"HTTP 恒 200 + 业务码"这一本仓库既有语义的替换通道 |
| 会话持久层 | 默认用 Sa-Token 内置内存实现；多实例或需落库时换用 `SaTokenDaoForCache`（纯缓存 / 缓存加速 + 权威委托两种形态） | 单机零依赖即可跑通；换实现只改装配一处，业务代码不变 |
| Java 版本 | 1.8 | 仓库硬约束；Sa-Token 1.46.0 本身即以 `maven.compiler.release=8` 发布 |

### 2.1 为什么挂载在 Dispatcher 层

最初的方案是把上下文装配放在 aifei 拦截器（`cn.aifei.aop.Interceptor`）里，但拦截器是**方法级 AOP**，其 `Invocation` 只能拿到 `Input` / `Output`，而：

- `cn.aifei.core.Input` 只提供参数取值，**没有 Header / Cookie / 请求方法 / 请求路径**；
- aifei 的 `Output` 实现（应用自写）通常**不持有** `HttpServerExchange`，无法写响应头与 Cookie。

要让 Sa-Token 工作，必须补齐「请求元信息读取」与「响应写回」两件事。这两个能力都只有 `HttpServerExchange` 提供，而 `Dispatcher` 的入参恰好就是它：

```java
public interface Dispatcher<P1, P2, I extends Input, O extends Output> {
    void init(Handler<I, O> handler);
    void dispatch(P1 p1, P2 p2);
}
```

装饰 `Dispatcher` 后同时获得三项收益：

1. 请求元信息与响应写能力全部可得，**无需任何 In/Out 适配代码**；
2. 上下文生命周期与请求完全对齐，比方法级拦截器覆盖更完整；
3. 消费方改动仅一行（替换 `setServer` 的第二个参数）。

代价是**绑定 Undertow**：本 SDK 的定位即为「aifei + Undertow 的 Sa-Token 接入」，该绑定是显式设计决策，由门禁测试约束其边界（见 §3.2）。

## 3. 总体架构

单模块，自包含 `pom.xml`（无聚合父 pom），坐标 `io.github.gollyhu:aifei-sa-token:1.0.0`。

### 3.1 内部分层

```
aifei-sa-token/src/main/java/cn/aifei/satoken/
├── SaTokenAifeiPlugin.java          装配入口
├── spi/    SaTokenErrorHandler      鉴权失败策略（零 aifei 依赖）
│           SaAuthStrategy           鉴权动作（零 aifei 依赖）
├── internal/  SaTokenConfigLoader   Prop→SaTokenConfig 的唯一定义点
│              PathPatternMatcher    Ant 风格路径匹配（非承诺 API）
├── aifei/  SaTokenInterceptor       路径鉴权
│           SaAnnotationInterceptor  注解鉴权
│           SaTokenErrorSupport      异常分类转换（唯一定义点）
├── dao/    SaTokenDaoForCache       持久层：唯一 import cn.hg.aifei.cache.* 的包
└── undertow/                        桥接层：唯一 import io.undertow.* 的包
            SaTokenUndertowDispatcher
            SaTokenUndertowContext
            SaRequestForUndertow
            SaResponseForUndertow
            SaStorageForUndertow
            SaTokenUndertowError
```

依赖方向单向：`aifei` / `dao` / `undertow` → `internal` / `spi`；`internal` 与 `spi` 不反向依赖上层。

`aifei`、`dao`、`undertow` 三个包**互不依赖**：接入层（拦截器）不绑定具体 Web Server，持久层也不引用 Web Server 与拦截器。

### 3.2 架构约束

由 `KernelBindingTest` 门禁强制（见 `docs/testing.md`）：

- `cn.aifei.aop.*` 的 import 只允许出现在 `cn.aifei.satoken.aifei` 包；
- `io.undertow.*` 的 import 只允许出现在 `cn.aifei.satoken.undertow` 包；
- `cn.hg.aifei.cache.*` 的 import 只允许出现在 `cn.aifei.satoken.dao` 包；
- `cn.aifei.vip.*`、`java.sql.*` 零引用。

第 2、3 条遵循同一思路：把对具体实现的依赖**收拢到单一包**，使边界可见、可测、可演进。

- 第 2 条是本模块区别于 `aifei-sentinel` 的地方：sentinel 对 undertow 零依赖，而 Sa-Token 的接入必须接触 Server 原生对象；未来接其他 Server 只需新增一个同构的桥接包。
- 第 3 条使持久层可替换：不使用 `dao` 包时，`aifei-cache` 不会进入消费方运行时。

### 3.3 依赖设计

| 依赖 | scope | 说明 |
|---|---|---|
| `cn.aifei:aifei-all` | `provided` | 内核 + `aifei-undertow`；消费方自带 |
| `io.undertow:undertow-core` | `provided` | `aifei-all` 已排除其传递依赖，桥接层需要其类型，故显式声明 |
| `cn.dev33:sa-token-core` | `compile` | Sa-Token 核心：认证、会话、注解策略 |
| `io.github.gollyhu:aifei-cache` | `optional` | 仅 `dao` 包使用；不向下游传递，消费方按需自带 |
| `junit:junit` | `test` | 单元测试 |

## 4. 核心组件

### 4.1 桥接层：`SaTokenUndertowDispatcher`

```java
public class SaTokenUndertowDispatcher<I extends Input, O extends Output>
        implements Dispatcher<HttpServerExchange, Void, I, O> {

    @Override
    public void dispatch(HttpServerExchange exchange, Void unused) {
        SaTokenUndertowContext.setExchange(exchange);
        try {
            SaManager.getSaTokenContext().setContext(
                    new SaRequestForUndertow(),
                    new SaResponseForUndertow(),
                    new SaStorageForUndertow(exchange));
            delegate.dispatch(exchange, unused);
        } finally {
            SaManager.getSaTokenContext().clearContext();
            SaTokenUndertowContext.clearInput();
            SaTokenUndertowContext.clearExchange();
        }
    }
}
```

三个模型对象均为**无状态**，每次请求新建，数据实际存放在线程变量（`SaTokenUndertowContext`）与 `exchange` 上。

`init()` 中另包一层 `Handler`，用于在业务执行期间绑定 aifei 已创建的 `Input`：

```java
@Override
public void init(Handler<I, O> handler) {
    delegate.init(new Handler<I, O>() {
        @Override
        public void handle(String path, I input, O output) throws Exception {
            SaTokenUndertowContext.setInput(input);
            try {
                handler.handle(path, input, output);
            } finally {
                SaTokenUndertowContext.clearInput();
            }
        }
    });
}
```

必要性：`Input` 由被装饰的 Dispatcher 在 `dispatch` 内部创建，`dispatch` 入口处尚不存在，只能在 `Handler` 边界捕获。绑定后 `SaRequest.getParam` 才能读到 JSON body 中的 token。

### 4.2 请求模型：`SaRequestForUndertow`

| 方法 | 数据来源 |
|---|---|
| `getParam(String)` | 优先 aifei `Input`，退回 `exchange.getQueryParameters()` |
| `getParamNames()` / `getParamMap()` | `exchange.getQueryParameters()` |
| `getHeader` / `getCookie*` | `exchange.getRequestHeaders()` / `getRequestCookie()` |
| `getRequestPath` / `getUrl` / `getMethod` / `getHost` | `exchange` 同名能力 |
| `forward(String)` | 抛 `UnsupportedOperationException` |

**已知限制**：`cn.aifei.core.Input` 不支持枚举参数名，故 `getParamNames()` / `getParamMap()` 只能覆盖查询串，与 `getParam(String)` 的数据源不完全一致。Sa-Token 读取 token 走的是 `getParam` / `getHeader` / `getCookie*`，不受影响。

### 4.3 响应模型：`SaResponseForUndertow`

把状态码、响应头、Cookie、重定向映射到 `exchange`。

`redirect(String)` **只写状态码与 `Location` 头，不调用 `endExchange()`**：本 SDK 不接管响应输出，若提前结束 exchange，消费方 IoHandler 随后的 `getOutputStream()` 写响应体会失败。重定向后是否继续执行 action 由消费方决定。

### 4.4 存储模型：`SaStorageForUndertow`

复用 Undertow 的 `AttachmentKey` 把请求级 `Map` 挂在 `exchange` 上，生命周期随 exchange 销毁，SDK 无需额外清理。

### 4.5 接入层：`SaTokenInterceptor` / `SaAnnotationInterceptor`

判断顺序：路径命中 → 注解校验 → 自定义 `SaAuthStrategy` → 进入 action。

```java
@Override
public void intercept(Invocation inv) throws Exception {
    try {
        if (matched() && checkAnnotation(inv)) {
            auth.check();
        }
        inv.invoke();
    } catch (StopMatchException ignored) {
        inv.invoke();
    } catch (Exception e) {
        if (!SaTokenErrorSupport.handle(inv, e, errorHandler)) {
            throw e;
        }
    }
}
```

- 规则语义：`includeList` 为空表示所有路径纳入鉴权；命中 `excludeList` 则不鉴权。
- 路径取自 `SaHolder.getRequest().getRequestPath()`，即 **HTTP 路径维度**（含 path 参数的真实 URL），与消费方按 URL 写规则的直觉一致。
- 两个拦截器**二选一**：`SaTokenInterceptor` 默认会执行注解校验，与 `SaAnnotationInterceptor` 同时注册会导致同一方法的注解鉴权执行两次；如需并用，须 `setAnnotationCheck(false)`。

### 4.6 异常转换：`SaTokenErrorSupport`

三类异常分派，两个拦截器共用：

| 异常 | 处理 |
|---|---|
| `StopMatchException` | 不处理（返回 `false`），由调用方放行 |
| `BackResultException` | 取其 `result` 作为 action 返回值 |
| 其余 `SaTokenException` | 交给 `SaTokenErrorHandler`，返回值经 `inv.setReturnValue` 渲染 |

**关键点**：策略的返回值直接作为 action 返回值，**策略本身抛出的异常则照常向上传播**。这样默认策略可以"置状态码并返回 null"，自定义策略也可以返回消费方自己的 `Out.fail(...)`。

### 4.7 装配入口：`SaTokenAifeiPlugin`

```java
@Override
public void start() {
    if (started) {
        return;
    }
    SaTokenConfig config = SaTokenConfigLoader.load(prop);   // 配置错误在此 fail-fast

    previousConfig = SaManager.getConfig();
    previousContext = SaManager.getSaTokenContext();

    SaManager.setConfig(config);
    SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
    SaTokenPluginHolder.instance.init();

    started = true;
}
```

- Sa-Token 的 `SaManager` 是**全局单例**，故启动前先保存原值，`stop()` 时还原，避免污染同进程的其他运行环境。
- `SaTokenPluginHolder.instance.init()` 幂等（内部以 `isLoader` 守卫），用于安装 classpath 中 `META-INF/satoken/` 声明的第三方 Sa-Token 插件。
- 配置解析失败时 `started` 仍为 `false`，后续 `stop()` 不会误还原。

### 4.8 路径匹配：`PathPatternMatcher`

单遍扫描生成锚定正则，编译结果按表达式缓存（`ConcurrentHashMap`）。

| 语法 | 语义 |
|---|---|
| `*` | 匹配单层片段，不跨 `/` |
| `**` | 匹配任意深度，且允许该段整体缺省（`/a/**` 同时匹配 `/a` 与 `/a/b/c`） |
| `?` | 匹配单个字符，不跨 `/` |
| `{name}` | 匹配单层路径变量 |
| `{name_}` | 匹配跨层路径变量 |

不使用占位符替换（如先替 `**` 再替 `*` 再还原）的原因：那种写法依赖替换顺序，可读性与可测性都差；单遍扫描对每个字符只判断一次。

### 4.9 持久层：`SaTokenDaoForCache`

登录态默认存于 Sa-Token 内置内存实现（单机可用、零依赖）。需要多实例共享、或需要「登录态落库 + 缓存加速」时换上本类。它支持两种装配形态：

| 形态 | 装配方式 | 适用场景 |
|---|---|---|
| 纯缓存 | `new SaTokenDaoForCache(CacheUtil.use())` | 登录态无需落库；多实例需缓存后端为 Redis |
| 缓存加速 + 权威委托 | `new SaTokenDaoForCache(CacheUtil.use(), delegate)` | 登录态需要落库（审计、在线用户列表、按账号反查踢人等），Redis 只做加速层 |

```java
public void onStart() {
    SaManager.setSaTokenDao(new SaTokenDaoForCache(CacheUtil.use(), new MySaTokenDao()));
}
```

**委托层语义（delegate 非空时）**：委托层为**权威**，缓存只做加速。

| 操作 | 行为 |
|---|---|
| 读（`get` / `getObject` / `getSession`） | 先查缓存；未命中则回源委托层，命中则按委托层的剩余超时回填缓存 |
| 写（`set` / `setObject` / `setSession`） | 先落委托层，再刷新缓存 |
| 更新（`update` / `updateObject` / `updateSession`） | 先更新委托层，再按委托层的剩余超时刷新缓存 |
| 删除（`delete` / `deleteObject` / `deleteSession`） | 先删委托层，再删缓存 |
| 超时读写（三个 timeout 方法） | 一律以委托层为准 |
| 搜索（`searchData`） | 直接委托（缓存可能只覆盖部分键，缓存侧结果会不完整） |
| `init` / `destroy` | **不转发**，见下文「生命周期」 |

委托层只以 `SaTokenDao` 接口形态出现，本类不对其存储形态做假设，因此不引入 JDBC 或消费方类型依赖 —— 消费方基于自有表实现的关系库持久层可直接作为 delegate 传入，而本模块的架构门禁（`java.sql.*` 零引用）仍然成立。

**生命周期**：本类**不覆盖** `init()` 与 `destroy()`，因此不会转发给委托层。Sa-Token 在没有官方 starter 的场景下不会自动调用 `SaTokenDao.init()`，故委托层的初始化与释放须由消费方在装配处（或其自身插件中）自行处理。

**key 映射**：Sa-Token 的 key 是扁平字符串（如 `satoken:login:token:xxx`），而 `aifei-cache` 是「缓存名 + 键」两级结构。本实现把 Sa-Token 的 key 整体作为缓存键，全部数据落在固定缓存名 `satoken` 下；命名空间与后端（Caffeine / Redis）由消费方的 aifei-cache 配置决定，本类不做存储形态假设。

**超时约定换算**：Sa-Token 用秒级 `long` 表达超时，`aifei-cache` 用 `Duration` 且要求不少于 1 毫秒。

| Sa-Token 语义 | 换算结果 |
|---|---|
| `timeout > 0` | `Duration.ofSeconds(timeout)` |
| `timeout == -1`（`NEVER_EXPIRE`） | `Duration.ofSeconds(Integer.MAX_VALUE)`，即 aifei-cache 允许的最长有效期 |
| `timeout == 0` 或 `<= -2`（`NOT_VALUE_EXPIRE`） | 不写入；`updateTimeout` 路径不调整有效期 |
| `timeout > Integer.MAX_VALUE` | 收敛为最长有效期，避免超出 aifei-cache 上限被拒 |

反向换算（`getTimeout`）：无该项返回 `NOT_VALUE_EXPIRE`；有效期为最长值时返回 `NEVER_EXPIRE`；剩余不足 1 秒但尚未过期时向上取整为 1 秒，避免与「0 表示不存储」的约定混淆。

**写入路径复用**：字符串与对象两套接口共用同一实现（`set`/`setObject` → `store`，`update`/`updateObject` → `rewrite`，`updateTimeout`/`updateObjectTimeout` → `resetTtl`）；`SaSession` 读写全部委托对象读写。

**已知行为差异与注意点**：

| 项 | 说明 |
|---|---|
| `update` / `updateObject` 非原子（纯缓存形态） | aifei-cache 未提供「只改值、保留 TTL」的原子操作，实现为「读剩余有效期 → 写回」，并发下存在窗口；该项不存在或已过期时跳过写入 |
| 有委托层时 timeout 每次都打委托层 | 三个 timeout 方法都不走缓存。Sa-Token 默认 `autoRenew=true`，每次 `getLoginId()` 都要查超时以决定续签，这部分压力会落到委托层上，缓存加速在该路径不生效 |
| 无空值缓存 | 读未命中即回源委托层。token 无效或不存在属高频场景，可能把大量无效查询透传到委托层 |
| `delete` 的顺序 | 先删委托层再删缓存；极端情况下（删缓存失败）会读到陈旧的登录态。这是分布式缓存的固有问题 |
| `updateTimeout` 的边界 | `timeout <= 0` 时会转发给委托层，但**不调整缓存 TTL**，两侧可能不一致。Sa-Token 实际只传正数续期，风险低 |
| `searchData` 的开销（纯缓存形态） | 委托 `Cache.keys` 做前缀下推，返回后再按关键字过滤与分页；Redis 后端为全量 SCAN，键量大时开销显著。该能力不在登录鉴权热路径上 |

## 5. 接口契约

### 5.1 鉴权失败策略

```java
@FunctionalInterface
public interface SaTokenErrorHandler {
    Object handle(SaTokenException e);
}
```

默认实现 `SaTokenUndertowError.defaultHandler()`：`NotLoginException` → 401，其余 `SaTokenException` → 403，返回 `null`。

保持"HTTP 恒 200 + 业务码"语义时：

```java
new SaTokenInterceptor(e -> Out.fail(OutCode.NOT_LOGIN))
```

### 5.2 鉴权动作

```java
@FunctionalInterface
public interface SaAuthStrategy {
    void check() throws Exception;
}
```

不直接复用 Sa-Token 的函数类型，避免把其函数接口类型暴露进消费方装配代码。

### 5.3 上下文生命周期

```
dispatch(exchange)
  ├─ setExchange(exchange)
  ├─ context.setContext(req, res, stg)      ← 此后 Sa-Token API 可用
  ├─ delegate.dispatch(...)
  │    └─ Handler 边界：setInput(input) ... clearInput()
  └─ finally: clearContext() + clearInput() + clearExchange()
```

**红线**：`clearContext()` 必须在 `finally` 中。Undertow 复用请求线程，残留绑定会串入下一个请求。

## 6. 配置项

读取 `app-config.txt`，键前缀固定 `sa-token.`，键名与 Sa-Token 官方 kebab-case 保持一致，未出现的键保持官方默认值。

| 键 | 语义 |
|---|---|
| `sa-token.token-name` | token 名称（同时是 Cookie 名与参数名） |
| `sa-token.timeout` | token 有效期（秒） |
| `sa-token.active-timeout` | 最低活跃频率（秒） |
| `sa-token.is-concurrent` | 是否允许同账号多地登录 |
| `sa-token.is-share` | 多人登录同账号是否共用 token |
| `sa-token.max-login-count` | 同账号最大登录数 |
| `sa-token.is-read-body` / `is-read-header` / `is-read-cookie` | token 的读取来源开关 |
| `sa-token.is-write-header` | 登录后是否把 token 写入响应头 |
| `sa-token.token-style` / `token-prefix` | token 风格与提交前缀 |
| `sa-token.data-refresh-period` | 内置 DAO 清理过期数据的间隔（秒） |
| `sa-token.auto-renew` | 是否自动续签活跃时间 |
| `sa-token.is-print` / `is-log` / `log-level` | 日志与版本输出 |
| `sa-token.curr-domain` | 当前项目网络地址 |

**取值非法即启动失败**：数字项、布尔项解析失败会抛 `IllegalArgumentException` 并指明出错键名与期望格式，不留半装配状态。

## 7. 风险与规避

| 风险 | 规避 |
|---|---|
| 消费方忘记注册 `SaTokenAifeiPlugin`，但已用桥接 Dispatcher | 上下文装配时 Sa-Token 会抛 `SaTokenContextException`，属 fail-fast；README 明确装配顺序 |
| 线程池复用导致上下文串号 | `finally` 中三重清理（context / input / exchange） |
| `redirect` 后继续走 IoHandler 写响应体 | 桥接层不 `endExchange()`；重定向语义由消费方决定后续流程 |
| 同一方法注解鉴权执行两次 | 两个拦截器二选一；并用时须 `setAnnotationCheck(false)` |
| `getParamMap` 不含请求体参数 | 文档显式声明该限制；token 读取路径不受影响 |
| Undertow 类型泄漏到其他层 | `KernelBindingTest` 断言 `io.undertow.*` 仅在 `undertow` 包 |
| 登录态在多实例间不共享 | 默认使用 Sa-Token 内置内存实现；多实例部署时换用 `SaTokenDaoForCache`（见 §4.9） |
| 引用了 `dao` 包却未引入 `aifei-cache` | `aifei-cache` 为 `optional` 不向下传递；消费方须自行声明该依赖，否则运行到 `SaTokenDaoForCache` 时报 `NoClassDefFoundError` |
| Sa-Token 全局状态被改动而未还原 | 插件启动前保存、`stop()` 还原 |
| 异步 / 跨线程调用 Sa-Token API | 上下文基于 ThreadLocal，跨线程不可见；文档声明，不做额外支持 |
