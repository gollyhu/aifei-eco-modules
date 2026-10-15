# aifei-mq 测试策略

本文面向测试维护者：说明测试结构、覆盖口径、运行方式与新增约定。

## 运行方式

命令一律在模块根目录（`aifei-mq`）内执行（架构门禁依赖相对路径 `src/main/java`）：

```bash
cd aifei-mq && mvn clean verify     # 必须 0 failure / 0 error
mvn test -Dtest=MqConfigTest        # 单类
mvn test -Dtest=MqConfigTest#shouldDefaultToMemoryWhenTypeAbsent   # 单方法
```

测试零第三方依赖、零外部中间件：内存后端全程无 IO。

## 测试结构

| 测试类 | 类型 | 覆盖 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 架构门禁 | 包边界与公共面大小（源码文本扫描，设计 §12） |
| `MessageBusContractTest` | 契约基类（abstract） | `MessageBus` 行为契约，由各后端继承 |
| `MemoryMessageBusContractTest` | 契约实现 | 内存后端继承运行契约，另加多总线独立计数 |
| `MqConfigTest` | 单元 | 配置默认值、类型白名单、前缀与后端键读取 |
| `MessageTest` | 单元 | 值对象校验、双向防御性拷贝、值语义 |
| `SendResultTest` | 单元 | 值对象校验与值语义 |
| `MqValidatorTest` | 单元 | 参数校验规则与异常类型 |
| `MqHolderTest` | 单元 | 单例登记 / 取用 / 清除与状态异常 |
| `MqKitTest` | 单元 | 门面未启动状态异常与转发 |
| `MqPluginTest` | 单元 | 装配生命周期、幂等、未注册后端失败不留半装配 |

## 架构门禁（`ArchitectureBoundaryTest`）

扫描 `src/main/java` 下全部 `.java`，按源码文本断言 5 条（设计 §12.1），**不得放宽或绕过**：

1. `thirdPartyOnlyInImplementationPackage`：第三方 / 其它模块 import 只允许出现在实现包（根包 / `internal` / `aifei` 包均禁止）。
2. `frameworkOnlyInBridgePackage`：`import cn.aifei.*`（除 `cn.hg.aifei.mq.*`）只允许出现在 `cn.hg.aifei.mq.aifei`。
3. `noSqlReference`：全模块禁止 `import java.sql.`。
4. `aifeiBridgePackageExists`：接入包存在且含 `MqPlugin.java`。
5. `publicSurfaceExact`：根包公共类型精确 7 个，且文件名集合精确等于那 7 个。

> 门禁扫描源码文本，因此**必须在模块根目录内执行**；在仓库根执行会因找不到 `src/main/java` 而失败。

## 契约测试约定

`MessageBusContractTest` 以抽象工厂 `protected abstract MessageBus newBus();` 承载契约，新增后端只需新增一个
子类实现 `newBus()` 即可复用全部用例。当前契约用例：

- `shouldDeliverToSubscriber`：生产 → 订阅 → 消费闭环，订阅者收到同主题同载荷，返回带消息标识的结果。
- `shouldPreserveSingleTopicOrder`：单主题 FIFO。
- `shouldInvokeSubscribersInRegistrationOrder`：多订阅者按登记顺序回调。
- `shouldSucceedWithoutSubscribers`：无订阅者视为成功。
- `shouldAssignIncreasingMessageId`：消息标识单调递增。
- `shouldDeliverSameMessageInstance`：投递发送时构造的同一消息对象。
- `shouldCloseIdempotently`：关闭幂等。
- `shouldRejectSendAfterClose`：关闭后发送抛 `IllegalStateException`。
- `shouldRejectSubscribeAfterClose`：关闭后订阅抛 `IllegalStateException`。
- `shouldIsolateListenerException`：监听器异常就地隔离，不中断其余订阅者。

## 新增约定

- 新增或改变**公共行为**时，先在 `docs/design.md` 明确契约，再补测试；已修复的缺陷固化为回归测试。
- 公共行为的契约用例优先加入 `MessageBusContractTest`（所有后端共用），后端专有行为放各自子类。
- 测试须独立、幂等：每个用例自建被测对象并自行关闭；`MqHolder` / `AopKit` 等静态状态在 `@Before` / `@After` 内清理。
