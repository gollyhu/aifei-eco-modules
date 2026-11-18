# aifei-audit

注解式操作审计 SDK：把「谁在何时对什么做了什么、结果如何」采集为一条不可变事件，交给消费方自己实现的落点。

- 坐标：`io.github.gollyhu:aifei-audit:1.0.0`｜包名：`cn.hg.aifei.audit`｜Java 8
- **零存储、零线程池、零生态依赖**：只有 `cn.aifei:aifei-all`（`provided`），消费方必须自带

## 依赖声明

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-audit</artifactId>
    <version>1.0.0</version>
</dependency>
```

`cn.aifei:aifei-all:1.1.2` 为 `provided`，由消费方自带（本模块不向下游传递）；模块自身零第三方 compile/runtime 依赖。

## 1. 接入三步

```java
// ① 容器里注册落点（同一时刻只能注册一个实现）
AopKit.get().addSingletonObject(AuditSink.class, new MyAuditSink());
// 可选：操作人 / 结果判定
AopKit.get().addSingletonObject(AuditActorProvider.class, new MyActorProvider());
AopKit.get().addSingletonObject(AuditResultJudge.class, new MyJudge());

// ② 装配插件（构造期只持配置源，校验与建快照在 start()）
plugins.add(new AuditPlugin(prop)
        .addScanPackages("com.example.biz")          // 扫描范围必填（包名或类清单）
        .setTraceIdSupplier(TraceKit::getTraceId));  // 可选，模块不依赖 aifei-trace

// ③ 采集拦截器必须第一个传入（下标 0 即最外层）
routes.scan("com.example.biz", new AuditInterceptor());
```

> **拦截器顺序是硬约束**：审计拦截器必须首个注册。顺序颠倒时审计完全失效（不是少记字段，而是一条都不记）。

配置（`audit.*`，全部可选）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `audit.enabled` | `true` | 关闭时不装运行期快照，手动埋点等价「未启动」 |
| `audit.fail-fast` | `false` | 仅影响落点异常是否上抛（业务已抛异常时业务异常优先） |
| `audit.args.enabled` | `false` | 全局入参记录开关（与注解 `args` 取 OR） |
| `audit.args.max-length` | `512` | 摘要长度上限，`>= 0`，超限截断不抛错 |
| `audit.client.enabled` | `false` | 是否调用客户端标识取值器 |
| `audit.module` | 空 | 事件上的业务模块标识 |
| `audit.scan.packages` | 无 | 逗号分隔包名，供启动期注解校验 |
| `audit.scan.classes` | 无 | 逗号分隔全限定类名，jar / fat-jar 场景兜底 |

非法取值**启动即失败**，消息含键名与原值。

## 2. 三态语义（实现者不得混淆）

| 状态 | 注解路径 | `AuditKit.record*` | 模块日志 |
| --- | --- | --- | --- |
| ① 未启动 / 启动失败 / `enabled=false` | 不采集、不抛、返回值不变 | 抛 `AuditException`（含「插件未启动或已停止」） | 不记 |
| ② 已启动、容器无 `AuditSink` | 不构造事件、直接返回 | 静默返回 | `start()` 恰一条告警，运行期不重复 |
| ③ 已停止 | 同 ① | 同 ① | 同 ① |

## 3. 事件

`AuditEvent` 15 个只读字段（事件号、发生时刻、动作码、业务模块、描述、操作人 id / 名、目标对象、入参摘要、结果、错误码、错误消息、耗时、`traceId`、客户端）。`Result` 为嵌套枚举，全字段 `final`、无 setter。

- 事件号：模块内唯一（不承诺跨实例全局唯一）
- 发生时刻：动作**开始**时刻；耗时：单调时钟差，采集与落点自身耗时不计入
- 结果：业务异常即失败；正常返回时可由 `AuditResultJudge` 判为失败（错误码固定 `BUSINESS_CODE_FAILURE`）

## 4. 落点契约（必读）

- 落点在**业务方法所在线程同步调用**，无线程池、无内部队列、无批量、无重试、无死信
- **落点慢即业务慢**——这是已定档的取舍，不要在 sink 里做阻塞 IO 或等待外部系统
- `AuditSink` 同一时刻只能注册一个实现；需要按动作分发时，在自己的 sink 内分发（运行期换实现请用「可切换委托 sink」）
- 三个 SPI 实现须线程安全，不得回调业务方法（避免递归采集）

失败隔离：落点 / 操作人 / 判定任一处异常都被就地吞掉并记一条模块日志，业务返回值与异常语义不变；`fail-fast=true` 时仅在业务**正常返回**的情况下上抛落点异常。

## 5. 手动埋点与注解路径的差异

```java
AuditKit.record("order.batch", "批量任务项", "order-1");
AuditKit.recordFailure("order.batch", "批量任务项", "order-1", error);
```

共同点：同一事件类型、同一落点、同一隔离策略。差异：① 不带入参摘要；② 耗时恒 `0`；③ 目标对象只能由调用方给出；④ 业务异常须由调用方自己捕获后再埋。

## 6. 入参脱敏

默认不记录入参。开启后按参数位（0-based 下标）剔除，摘要形如 `#0=VISIBLE-OK;#1=<masked>`，超长截断为 `...` 后缀；越界下标忽略、不抛错。不做嵌套对象遍历、正则规则库、字段注解（非目标）。

## 7. 能力边界（本期不做）

审计存储 / 建表、查询与展示 API、归档报表、采样、异步队列与背压、批量入库、失败重试、与 sa-token / mq / trace 的硬依赖、入参深度脱敏、注解取值表达式、多实例协调与全局事件号。模块**不提供任何查询接口**，展示页属示例工程。

## 8. 非承诺 API

`cn.hg.aifei.audit.internal` 下的类型全部为 `public`——这是 Java 跨包可见性的硬约束（根包门面与 `aifei` 接入包必须调用它们），**不是兼容性承诺**：消费方不得引用 `cn.hg.aifei.audit.internal.*`，模块不保证其类型、方法或语义在后续版本中保持兼容。
