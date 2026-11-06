# 变更日志

记录 aifei-cache 的版本变更，版本号遵循语义化版本。行为细节以 `README.md`（用法）与 `docs/design.md`（契约）为准，本文件只记版本差异与升级动作。

## [1.1.0] - 2026-11-05

相对 1.0.0 只增加条件操作（CAS）一项能力。

### 新增

- `Cache.removeIfEquals(cacheName, key, expectedValue)`：值匹配才删除。
- `Cache.expireIfEquals(cacheName, key, expectedValue, ttl)`：值匹配才重设剩余有效期。
- `Cache.supportsCas()`：纯能力声明，不发任何存储命令。
- `CacheKit.removeIfEquals` / `CacheKit.expireIfEquals`：主缓存的静态镜像；`supportsCas()` 与 `namespace()` 一样只有实例方法。
- `internal.CacheValidator.requireExpectedValue`：期望值非空校验（`internal` 为非承诺 API）。

### 实现

- jedis 后端：单条 Lua 脚本原子完成「读到当前值 → 字节比较 → `DEL` / `PEXPIRE`」；脚本、key、ARGV 全按 `byte[]` 传递，期望值只经本实例 codec 序列化、不经 `String` 中转。
- caffeine 后端：不支持条件操作，`supportsCas()` 恒 `false`，两个条件方法抛 `UnsupportedOperationException`。

### 兼容性

- **调用面 minor 兼容**：只新增方法，既有调用代码无需改动。
- **`Cache` 实现面（SPI）不兼容**：自定义实现（含继承 `internal.AbstractCache` 的实现）必须补齐这 3 个方法，否则编译失败；基类刻意不提供默认实现。存在自定义实现时，先补齐再升级。
- **存储格式不变**：空值哨兵、物理 key、Fury 编码均未改动，1.0.0 写入的数据可直接读取，无迁移步骤。

### 升级动作

1. 项目内有自定义 `Cache` 实现（含测试桩）时补齐 `removeIfEquals` / `expireIfEquals` / `supportsCas`。
2. 需要跨实例互斥（如租约式锁）时用 `jedis` 后端；`caffeine` 后端不支持条件操作。
3. `expectedValue` 只用能稳定序列化出相同字节的标量值；带共享子对象或环的对象图不要用于条件操作，字节不保证等价，结果是「安全地永远不匹配」。
4. `cacheNull = true` 写入的哨兵条目没有条件操作入口，只能用 `remove` 清理。

### 验证

- `mvn clean verify` 必须 0 failure / 0 error。
- 新增 `RedisCacheCasTest`（离线，校验两条 Lua 常量与期望值字节装配）与 `RedisCacheIntegrationTest` 的 CAS 端到端用例（默认跳过）；`CacheContractTest`、`FuryRedisValueCodecTest`、`FacadeMirrorTest` 增补对应断言。
- 发布前必须在真实 Redis 上跑一次 `mvn verify -Dredis.integration=true -Dredis.uri=...`，连不上必须失败。

## [1.0.0]

基线版本（提交 `cf8df94`）：主缓存 `__MAIN__` 与扩展实例、Caffeine / Jedis 双后端、带 `loader` 的回源单飞、键分页与筛选、`cacheNull` 否定结果缓存。
