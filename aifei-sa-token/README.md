# aifei-sa-token

面向 [aifei](https://aifei.cn) + Undertow 的 [Sa-Token](https://sa-token.com) 认证鉴权接入 SDK。

它的目标很明确：

- **不改造既有 HIO**：`Input` / `Output` / `IoHandler` 保持原样；
- **不写上下文适配类**：请求头、Cookie、请求方法、请求路径、响应写回由 SDK 自己接线；
- **只改一行装配**：用 `SaTokenUndertowDispatcher` 包一层你现有的 `Dispatcher`。

## 适用场景

- 使用 aifei（`cn.aifei:aifei-all`）；
- 使用 Undertow 作为底层 Server；
- 希望用 Sa-Token 做登录认证、角色权限、注解鉴权；
- 不想为了鉴权去重写控制层与出入参模型。

## 依赖

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-sa-token</artifactId>
    <version>1.0.0</version>
</dependency>
```

SDK 对 `cn.aifei:aifei-all` 与 `io.undertow:undertow-core` 均为 `provided`，由你的应用自行提供。

环境要求：JDK 8+，Sa-Token 1.46.0。

## 快速开始

### 1. 注册插件

```java
@Override
public void config(Plugins plugins) {
    plugins.add(new SaTokenAifeiPlugin(PropKit.use("app-config.txt")));
}
```

### 2. 包装 Dispatcher

保留你现有的 HIO 与 `Dispatcher` 实现，只在装配处包一层：

```java
@Override
public void config(Settings<Input, Output> settings) {
    settings.setServer(
            new UndertowServer(),
            new SaTokenUndertowDispatcher<>(new IoDispatcher()));
    settings.addHandler(new IoHandler());
}
```

> 注意：`SaTokenAifeiPlugin` 与 `SaTokenUndertowDispatcher` 必须同时存在。少了插件，Sa-Token 上下文实现仍是默认的空实现，请求进入桥接层会直接报错。

### 3. 挂鉴权拦截器

二选一：

```java
// 方式一：按路径鉴权（默认同时校验方法注解）
@Override
public void config(Routes routes) {
    routes.scan("cn.demo.service", new SaTokenInterceptor(SaTokenUndertowError.defaultHandler())
            .addInclude("/admin/**")
            .addExclude("/auth/login", "/auth/doLogin")
            .setAuth(() -> StpUtil.checkLogin()));
}
```

```java
// 方式二：只用方法注解鉴权
@Override
public void config(Routes routes) {
    routes.scan("cn.demo.service", new SaAnnotationInterceptor(SaTokenUndertowError.defaultHandler()));
}
```

> 两个拦截器同时注册会让同一方法的注解鉴权执行两次。确需并用时，把 `SaTokenInterceptor` 的 `setAnnotationCheck(false)`。

### 4. 写业务

```java
@Path("/auth")
public class AuthService {

    public Out login(@Para(name = "username") String username) {
        // 校验账号密码后写入登录态，Sa-Token 会自动把 token 写到响应头 / Cookie
        StpUtil.login(username);
        return Out.ok().set("token", StpUtil.getTokenValue());
    }

    public Out logout() {
        StpUtil.logout();
        return Out.ok("已退出");
    }
}

@Path("/admin")
public class AdminService {

    @SaCheckRole("admin")
    public Out index() {
        return Out.ok("admin 可访问");
    }
}
```

## 配置项

配置写在 `app-config.txt`，键前缀 `sa-token.`，未配置的键保持 Sa-Token 默认值：

```properties
sa-token.token-name = demo-token
sa-token.timeout = 86400
sa-token.is-read-header = true
sa-token.is-write-header = true
sa-token.token-prefix = Bearer
```

常用键：`token-name`、`timeout`、`active-timeout`、`is-concurrent`、`is-share`、`max-login-count`、`is-read-body` / `is-read-header` / `is-read-cookie`、`is-write-header`、`token-style`、`token-prefix`、`data-refresh-period`、`auto-renew`、`is-print`、`is-log`、`log-level`、`curr-domain`。

取值非法会**在应用启动时**直接报错并指明出错键名，不会静默降级。

## 鉴权失败响应

默认策略 `SaTokenUndertowError.defaultHandler()`：未登录置 HTTP 401，其余鉴权失败置 403，响应体仍由你的 IoHandler 输出。

若希望沿用「HTTP 恒 200 + 业务码」的语义，换成返回自己 `Out` 的策略即可：

```java
new SaTokenInterceptor(e -> Out.fail(OutCode.NOT_LOGIN))
```

## 角色与权限

角色与权限由 Sa-Token 的 `StpInterface` 提供，在 `onStart()` 中注册（此时所有插件已启动）：

```java
@Override
public void onStart() {
    SaManager.setStpInterface(Aop.get(AppStpInterface.class));
}
```

```java
public class AppStpInterface implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionService.listByUserId(loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleService.listByUserId(loginId);
    }
}
```

## 会话持久层

默认登录态存于 Sa-Token 内置内存实现，单机可用、零依赖。两种情况需要换成基于 [aifei-cache](https://github.com/gollyhu/aifei-cache) 的持久层：

- **多实例部署**：各实例的登录态互不相通；
- **登录态需要落库**：例如审计日志、在线用户列表、按账号反查踢人等功能要直接查库。

### 1. 显式引入 aifei-cache

`aifei-cache` 在本 SDK 中是 `optional` 依赖，不会自动带入：

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

使用 Redis 后端时，还需按 aifei-cache 的要求引入其可选依赖（jedis、序列化实现）。

### 2. 配置并注册 CachePlugin

按 aifei-cache 的方式配置 `cache.*` 并注册 `CachePlugin`，后端可选 `caffeine` 或 `jedis`。Sa-Token 的全部数据都落在固定缓存名 `satoken` 下。

### 3. 换上持久层实现

在 `onStart()` 中设置（此时各插件均已启动）。按是否需要「登录态落库」二选一。

**形态一：纯缓存**（登录态只存在于缓存）

```java
@Override
public void onStart() {
    SaManager.setSaTokenDao(new SaTokenDaoForCache(CacheUtil.use()));
}
```

适用于「登录态无需落库」的部署；多实例共享登录态的前提是缓存后端为 Redis。

**形态二：缓存加速 + 权威持久层**（登录态落库，Redis 只做加速）

```java
@Override
public void onStart() {
    SaManager.setSaTokenDao(new SaTokenDaoForCache(CacheUtil.use(), new MySaTokenDao()));
}
```

`MySaTokenDao` 是你基于自己业务表实现的 `SaTokenDao`（本 SDK 不可能内置你的表结构）。此时：

- 委托层是**权威**，缓存只做加速：读未命中回源并回填、写先落委托层再刷新缓存、超时以委托层为准、`searchData` 直接委托；
- 委托层只以 `SaTokenDao` 接口形态传入，本 SDK 不引入 JDBC 依赖，你的实现可以用任意持久化方式。

两种形态都只改这一处装配，业务代码与鉴权规则不受影响。

### 使用须知

- **`update` 非原子**（纯缓存形态）：aifei-cache 未提供「只改值、保留 TTL」的原子操作，更新值为「读剩余有效期 → 写回」，并发下存在窗口；
- **`getTimeout` 不走缓存**（有委托层时）：Sa-Token 默认 `autoRenew=true`，每次 `getLoginId()` 都要查超时以决定续签，这部分请求会落到委托层上，请确保该查询足够轻量；
- **无空值缓存**：token 无效或不存在时会回源委托层，高频非法请求可能给委托层带来压力；
- **`searchData` 开销**（纯缓存形态）：Redis 后端为全量 SCAN，键量大时开销显著；该能力不在登录鉴权热路径上；
- **`init` / `destroy` 不转发**：委托层的初始化与释放需你在装配处自行处理；
- **超时换算**：Sa-Token 的 `-1`（永不过期）换算为 aifei-cache 允许的最长有效期（约 68 年）。

## 路径规则语法

`addInclude` / `addExclude` 支持 Ant 风格表达式：

| 表达式 | 语义 |
|---|---|
| `/admin/*` | 匹配 `/admin` 下一层 |
| `/admin/**` | 匹配 `/admin` 及其任意深层（也匹配 `/admin` 本身） |
| `/u/{id}` | 匹配单层路径变量 |
| `/f/{path_}` | 匹配跨层路径变量 |

规则按 **HTTP 请求路径**匹配，`include` 为空表示对所有路径鉴权。

## 已知限制

- **仅支持 Undertow**：桥接层基于 `HttpServerExchange`，换其他 Server 需新增桥接实现。
- **登录态默认单机内存存储**：多实例部署或需要落库时换成 `SaTokenDaoForCache`，见「会话持久层」一节。
- **上下文基于 ThreadLocal**：在业务中新开线程后调用 Sa-Token API 拿不到当前请求上下文。
- **`SaRequest.getParamMap()` / `getParamNames()` 只覆盖查询串**：aifei 的 `Input` 接口不支持枚举参数名；Sa-Token 读取 token 走的是单值取值与请求头 / Cookie，不受影响。
- **不支持服务端 forward**：Undertow 桥接层调用 `forward` 会抛 `UnsupportedOperationException`。

## 构建

```bat
cd aifei-sa-token
mvn clean test
```

## 致谢

- [jfinal/aifei](https://github.com/jfinal/aifei)：框架内核与扩展点
- [dromara/sa-token](https://github.com/dromara/sa-token)：认证鉴权能力

本项目的「装饰 Dispatcher 接线上下文」思路，受到社区实现 `io.github.ysjsgzq:aifei-sa-token` 的启发，特此致谢。

## License

Apache License 2.0
