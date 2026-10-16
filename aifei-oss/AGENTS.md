# aifei-oss 开发约束

本文件面向开发代理与贡献者：改本模块代码前必须遵守的硬约束、包边界与门禁、唯一定义点清单、行为契约要点与验证命令。

四份文档的分工：`README.md` 面向使用者（安装、用法、配置）；`docs/design.md` 面向设计者与实现者（契约、决策、验收，是**设计事实来源**）；本文件面向开发代理与贡献者；`docs/testing.md` 面向测试维护者（实现阶段补）。

## 开发前提

- **改代码前必读 `docs/design.md`**。需求与现有设计冲突时，先明确并更新设计决策，再改代码。
- 未经设计决策，不得自行确定契约类行为（空值语义、异常类型与消息、配置键名、默认平台选择、覆盖语义等），也不得新增或扩大公共 API 面。
- 本模块同时遵循仓库根 `AGENTS.md` 的共同底线，冲突时以更严格者为准。

## 模块定位

`aifei-oss` 基于 **X File Storage**（`org.dromara.x-file-storage`）二次封装，为 aifei 应用补齐多平台对象 / 文件存储能力。本模块**不自实现存储协议**，只做装配、薄门面、类型收敛与框架接线。

## 硬性约束

### 技术基线

- 代码、依赖与 API 必须兼容 **Java 8**：**语法**与 **API 双约束**。禁 `var` / `record` / `switch` 表达式 / text block（`"""`）/ `sealed` / pattern matching 等 Java 8 之后语法；禁 `List.of` / `Map.of` 等 Java 9+ 集合工厂与其它 Java 8 之后 API。
- 主 JAR 内全部 class 的 major version 必须为 **52**。
- 源码 **UTF-8**；缩进 4 空格；注释一律**简体中文**（保留英文原文的只有 javadoc 标签名、代码标识符、配置键名与异常消息）；文件头版权块属归属信息，不得改写。
- **`cn.aifei:aifei-all` 为 `provided`**：消费方自带，不向下游传递。

### 依赖策略（**例外，见 `docs/design.md` §3**）

- **`x-file-storage-core` 为 `compile` 依赖**：这是仓库根「第三方依赖一律 `optional` 或 `provided`」的**显式例外**（理由见 `docs/design.md` §3.2），**不得**擅自改为 `optional` / `provided`。
- **仅依赖 `core`**：禁止引入 `x-file-storage-spring` / `x-file-storage-solon`；`FileStorageService` 一律用 `FileStorageServiceBuilder` 编程式构建。
- **各云厂商 SDK 不代为引入**：由消费方按需自带；本模块 `pom.xml` 不得声明 `aliyun-sdk-oss` / `aws-java-sdk-s3` / `minio` 等平台 SDK（测试用内存 / 本地实现除外）。
- **加 / 升 x-file-storage 版本前必须重跑 P0 门禁**（`major version = 52`，见 `docs/design.md` §3.1），并先更新设计决策。

### 公共面收敛（不得放宽）

- **公共 API 零第三方类型、零框架类型**。
- 公共类型固定为 **6 个，全部在根包 `cn.hg.aifei.oss`**：`OssClient` / `OssKit` / `OssConfig` / `OssFileInfo` / `OssObject` / `OssException`。新增公共类型必须先过设计决策。
- `internal` 与 `aifei` 均属**非承诺 API**：实现类因装配需要可声明为 `public`，但**明确不是公共 API**，消费方不得引用。
- 异常消息中**不得**包含任何第三方类型名（x-file-storage / 云 SDK 类型）；底层异常一律在适配层翻译为 `OssException` 后再上浮。

## 包边界与门禁

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| `org.dromara.x.file.storage.*`（x-file-storage） | **仅** `cn.hg.aifei.oss.internal` | `ArchitectureBoundaryTest#xfsOnlyInAdapterPackage` |
| `cn.aifei.*`（框架类型，除 `cn.hg.aifei.oss.*`） | **仅** `cn.hg.aifei.oss.aifei` | `ArchitectureBoundaryTest#frameworkOnlyInBridgePackage` |
| `java.sql.*` / `io.undertow.*` | **全模块禁止** | `ArchitectureBoundaryTest#noSqlOrUndertowReference` |

另有 `ArchitectureBoundaryTest#publicSurfaceExact`：断言根包公共类型**精确 6 个**。

`ArchitectureBoundaryTest` 扫描 `src/main/java` 下全部 `.java` 断言上述边界，因此**必须在模块根目录执行**。**不得为兼容错误用法而放宽或绕过门禁断言。**

## 唯一定义点清单（实现类不得复制）

| 规则 | 唯一定义点 |
| --- | --- |
| 配置键名与解析（`Prop` → 平台配置） | `aifei.OssConfigLoader` |
| 字段级配置校验 | `OssConfig` |
| `FileStorageService` 构建与平台装配 | `internal`（适配层构建类） |
| `FileInfo` → `OssFileInfo` / `OssObject` 映射 | `internal`（适配层映射类） |
| 异常翻译（x-file-storage / 云 SDK → `OssException`） | `internal`（适配层翻译类） |

> 适配层具体类名在实现阶段定档后回填本表；无论类名如何，规则实现**只有一处**。

## 行为契约要点速查

- **上传**：成功返回 `OssFileInfo`；失败抛 `OssException`；同名路径**跟随 x-file-storage 默认覆盖语义**。
- **下载**：不存在抛 `OssException`；返回的 `OssObject` 由调用方关闭（try-with-resources）。
- **删除**：不存在返回 `false`（不抛异常）；成功返回 `true`。
- **存在性**：不存在返回 `false`。
- **签名 URL**：平台不支持时抛 `OssException`。
- **复制**：仅限**同一平台内**；跨平台不在本期范围。
- **平台选择**：`@Inject OssClient` = 默认平台；多平台经 `OssKit.use(name)`。
- **装配失败即启动失败**：必填配置缺失 / 平台 SDK 未引入，应用启动即失败，不留半装配状态。
- **脱敏**：`OssConfig` 的 `toString` 不得输出 `secretKey` 等敏感字段。

## 验证

**命令一律在模块目录内执行**（门禁测试依赖相对路径 `src/main/java`）：

```bash
cd aifei-oss && mvn clean verify                        # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest                # 单跑门禁
mvn verify -Doss.integration=true                       # 开启 MinIO 集成测试（默认关闭）
```

当前基线：以 `mvn clean verify` 的实测结果为准，要求 0 failure / 0 error。已修复的缺陷必须固化为**回归测试**，测试须独立且幂等。

## 文档同步要求

- 涉及**公共行为**的改动，必须同步 `docs/design.md`（契约）与 `README.md`（用法 / 配置）。
- 术语必须与 `docs/design.md` 统一（存储平台、默认平台、门面、适配层、接入层、对象路径等），不得另起一套叫法。
- 每类规则仍只有唯一定义点；实现类不得复制规则。
- 注释规范：公共类型与公共成员必须书写 javadoc 并写全 `@param` / `@return` / `@throws`；只在代码无法表达「为什么」时书写注释，不复述下一行代码，不写行尾注释，不留被注释掉的死代码。
