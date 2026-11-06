# aifei-lock

面向 aifei 应用的**分布式锁**：基于共享缓存的跨实例互斥。业务代码只依赖三个类型（`Lock` / `LockKit` / `LockException`），锁落在主缓存实例的 `__LOCK__` 保留分区，不新建连接、零配置键。

坐标：`io.github.gollyhu:aifei-lock:1.0.0`，包名 `cn.hg.aifei.lock`，Java 8。

## 它能做什么，不能做什么

**能力声明：本模块提供互斥，不提供一致性。** 不在保证范围：

- 租约到期后仍有并发执行（双持有）
- Redis 主从切换导致锁丢失
- GC / 长时间停顿期间锁过期
- 跨库、跨服务以及 DB 与 MQ 之间的原子性
- 无锁降级：未装配即抛 `IllegalStateException`，不会静默无锁执行

**不做**：分布式事务、单机 / caffeine 后端、可重入、公平排队、读写锁、锁丢失回调、JDBC 后端、多实例、自动续期看门狗。

## 安装

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-lock</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 必须显式引入：本模块对 aifei-cache 的依赖是 optional，不会传递给你 -->
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.1.0</version>
</dependency>
```

- `cn.aifei:aifei-all` 为 `provided`，消费方应用自带（日志门面 `cn.aifei.log` 由它提供）。
- **`aifei-cache` 为 `optional`，不向下游传递**：漏引入时 `cn.hg.aifei.lock.cache.CacheLockStore` 在**类加载期**即 `NoClassDefFoundError`，早于 `LockPlugin` 的启动校验，无法转成可读错误。
- 锁只对**共享存储**后端有意义：主缓存必须是 `jedis`。`caffeine` 后端会在启动期被能力校验拒绝（`lock backend is not a shared store`），因为进程内锁静默无互斥比报错更危险。

## 装配

`LockPlugin` 无参构造、无配置项，**必须晚于 `CachePlugin`**：

```java
@Override
public void config(Plugins plugins) {
    plugins.add(new CachePlugin(prop));   // 先：注册 __MAIN__ 主缓存实例
    plugins.add(new LockPlugin());        // 后：校验主缓存能力并注册锁存储
}
```

启动期校验只有一次能力查询（无 I/O、不探活）：`supportsCas()` 为 `false` 时启动失败。存储故障推迟到首次加锁，由 `LockException` 承载。不想用锁就不要装配——不设 `lock.enabled`。

## 标准用法

锁在**事务外**获取，事务提交后自动释放：

```java
public Out pay(Long orderId) {
    String name = "order:pay:" + orderId;
    // null 句柄不触发 close（JLS 保证）；return 先求值事务（提交完成），再执行 close
    try (Lock lock = LockKit.tryLock(name, Duration.ofSeconds(30))) {
        if (lock == null) {
            return Out.fail("操作过于频繁，请稍后重试");   // 抢锁失败是正常业务结果
        }
        return Db.transaction(tx -> doPay(tx, orderId));
    }
}
```

有限等待：先立即尝试一次，失败后按固定 50ms 间隔轮询。

```java
Lock lock = LockKit.tryLock("order:pay:" + orderId, Duration.ofSeconds(30), Duration.ofSeconds(2));
if (lock == null) {
    // 等待用尽，或等待期间被中断（后者可用 Thread.currentThread().isInterrupted() 区分）
}
```

抢不到锁返回 `null`，等待期间被中断也返回 `null` 并**恢复中断位**、不抛异常；`wait == 0` 时不检查中断位（对齐 `ReentrantLock#tryLock()`）。

## 长任务分段续期

租约过短时用 `renew()` 续期；返回 `false` 表示锁已丢失，**必须立即停手**：

```java
try (Lock lock = LockKit.tryLock("job:report:monthly", Duration.ofSeconds(60))) {
    if (lock == null) return;                  // 已有实例在跑
    for (Batch batch : batches()) {
        process(batch);
        if (!lock.renew()) {                   // false = 锁已丢失
            log.warn("lock lost, abort job");
            return;                            // 互斥已失效
        }
    }
}
```

`renew()` 用句柄创建时的租约重设 TTL，不需要也不能另行传时长。

## 使用顺序

违反即误用：

1. 锁在**事务外**获取：事务内加锁把等待计入事务时长，放大连接占用与锁竞争。
2. 锁在**提交之后**释放：提前释放开出「他人读到未提交或部分可见数据」的窗口。
3. 租约时长 > 事务最长耗时 + 网络余量，按 P99 定。
4. 锁粒度必须覆盖事务内改动的全部数据：漏一条写路径，互斥即失效。

## 反例

```java
Db.transaction(tx -> { LockKit.tryLock(name, lease); ... });   // ✗ 事务内加锁：等待计入事务时长
lock.release();  // 不在 finally 内                            // ✗ 异常即漏释放
if (!lock.renew()) throw new RuntimeException("lock lost");    // ✗ 锁丢失是业务分支，应 return
static Lock current;  current = LockKit.tryLock(...);          // ✗ 句柄只对应一次持有，不能存全局字段
CacheKit.use().clear("__LOCK__");                              // ✗ 清空锁分区 = 清空全部锁
```

## 返回值与异常语义

| 情形 | `release()` | `renew()` | `close()` | 日志 |
| --- | --- | --- | --- | --- |
| 值不匹配（已过期或被他人持有） | `false` | `false` | 吞掉 | DEBUG |
| CAS 成功 | `true` | `true` | 吞掉 | DEBUG |
| 存储不可用 / 超时 / 序列化失败 | **抛 `LockException`** | **抛 `LockException`** | **不抛**（记 ERROR） | ERROR |

- **异常 ≠ 丢失**：`LockException` 只表示「结果未知」，可重试或放弃并由租约自然过期兜底；只有 `renew()` 返回 `false` 才是「确认丢失」。
- **`release()` 也遵守「异常 ≠ 丢失」**：抛异常只表示「是否删除未知」，重试安全（CAS 不会误删他人锁）。
- **竞争失败与故障必须可区分**：抢不到锁返回 `null` / `false`（DEBUG），存储故障抛 `LockException`（ERROR）。
- `LockException` 继承 `RuntimeException`，公共方法不声明 `throws`；参数非法抛 `IllegalArgumentException` 且不被包装（锁名为空、含空白、超过 128 字符，`lease < 1ms`，`wait < 0`）。
- `close()` 永不抛异常：try-with-resources 退出时抛异常会抑制业务真实异常。需感知释放失败（告警）时显式调用 `release()` 并自行 try/catch。

## 日志

统一经 aifei 框架日志门面 `cn.aifei.log.Log`，取本类 logger，不引入任何日志绑定。未装配 `LogFactory` 时按框架既有行为（框架会提示未配置日志工厂）；测试须先装配。

## 句柄语义

- `Lock` 是 final class、全字段 final、不可伪造也不可继承；只暴露 `name()`，不暴露 token 与租约。
- 采用对象身份比较：同名两次持有互不相等；句柄只对应一次持有，不能存全局字段。
- 测试替身请放在存储层，经 `LockHolder.register(FakeLockStore)` 注入，不要 mock 句柄（final class 在 Java 8 下需 inline mock maker）。

## 文档

- 用法与本页：[README.md](README.md)
- 契约与设计决策：[docs/design.md](docs/design.md)
- 开发约束：[AGENTS.md](AGENTS.md)
