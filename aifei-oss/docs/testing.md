# aifei-oss 测试文档

本文件面向测试维护者：测试分层、用例清单、运行方式与新增约定。契约与设计决策见 `docs/design.md`。

## 1. 测试分层

| 层 | 覆盖对象 | 是否联网 | 所在用例 |
| --- | --- | --- | --- |
| 单元 / 契约 | 公共门面到本地存储平台的完整链路（上传 / 下载 / 存在性 / 元数据 / 删除） | 否 | `LocalStorageTest` |
| 架构门禁 | `src/main/java` 源码文本的分层边界与公共面收敛 | 否 | `ArchitectureBoundaryTest` |
| 字节码约束 | 主 JAR 内全部 class 的 major version = 52 | 否 | `ClassFileVersionTest` |
| 集成（默认关闭） | 真实对象存储（MinIO，S3 兼容） | 是 | 待补（见 §4） |

## 2. 用例清单

### 2.1 `ArchitectureBoundaryTest`（4 项门禁）

| 用例 | 断言 |
| --- | --- |
| `xfsOnlyInAdapterPackage` | `org.dromara.x.file.storage.*` 只出现在 `cn.hg.aifei.oss.internal` |
| `frameworkOnlyInBridgePackage` | `cn.aifei.*` 只出现在 `cn.hg.aifei.oss.aifei` |
| `noSqlOrUndertowReference` | 全模块无 `java.sql.*` / `io.undertow.*` |
| `publicSurfaceExact` | 根包公共类型精确 6 个 |

### 2.2 `ClassFileVersionTest`（1 项）

- `mainClassesAreJava8`：`target/classes` 下全部 `.class` 的 major version 必须为 52。

### 2.3 `LocalStorageTest`（1 项）

- `uploadDownloadDeleteExists`：以本地存储平台（临时目录）验证 `upload → exists → download → getFileInfo → delete → !exists` 完整链路，并断言上传后的对象路径与平台标识。

## 3. 运行

**命令一律在模块根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
cd aifei-oss && mvn clean verify          # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest  # 单跑门禁
mvn test -Dtest=LocalStorageTest#uploadDownloadDeleteExists
```

开启 MinIO 集成测试（默认关闭）：

```bash
mvn verify -Doss.integration=true
```

## 4. 新增约定

- 公共行为变更必须同步更新对应用例；已修复的缺陷必须固化为**回归测试**。
- 测试须**独立且幂等**：`LocalStorageTest` 使用临时目录并在结束时 `OssClients.clear()` 与清理临时目录，不得依赖执行顺序。
- 涉及真实外部服务（MinIO 等）的用例默认关闭；一旦开启，连不上必须使测试失败，不得静默跳过（命名对齐 `aifei-cache` 的 `redis.integration`）。
- 新增平台实现时，应补一组「上传 / 下载 / 删除」的集成用例，并保证不联网的本地存储用例仍可独立运行。
