# aifei-excel

aifei 生态的 **Excel 读写统一门面**（库模块 / SDK）：注解表头映射、一次性便捷读写、流式分批读写，单一内核 easyexcel 可选引入。

- 坐标：`io.github.gollyhu:aifei-excel:1.0.0`（`packaging=jar`）
- 包名：`cn.hg.aifei.excel`；Java 8（class major 52）
- 根包精确 5 个公共类型：`Excel` / `ExcelKit` / `ExcelException` / `ExcelColumn` / `ExcelIgnore`
- 无 Plugin、无配置键、无 provider / SPI；不记录日志、零 DB、零 Web 面

## 依赖声明

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-excel</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 消费方必须自带框架内核：本模块对 aifei-all 是 provided，不向下游传递 -->
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-all</artifactId>
    <version>1.1.2</version>
</dependency>

<!-- 读写内核：optional，不向下游传递；要用读写能力就必须显式引入 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.2</version>
</dependency>
```

> **可选依赖语义（重要）**：本模块对 `com.alibaba:easyexcel:3.3.2` 声明为 `optional`，**不会**随本模块传递到消费方。若消费方未显式引入它，本模块的读写 API 在**首次调用**时抛 `ExcelException`，消息为 `excel: com.alibaba:easyexcel:3.3.2 is required, add it to your dependencies`，而**不是**裸 `NoClassDefFoundError`。这是「能力按需引入」而非「功能降级」：不引 easyexcel，读写完全不可用。EasyExcel 传递的 POI 4.1.2、slf4j-api 1.7.32 等同样不向下游传递。

> `cn.aifei:aifei-all:1.1.2` 为 `provided`，本模块源码零引用、不向下游传递，消费方须自带。

## 取用方式

本模块**无 Plugin、无装配期**：`Excel` / `ExcelKit` 随取随用，任何时刻可调用（与 `aifei-http` 的 `HttpKit`「未装配即失败」是两种门面形态）。装配入口类不存在，也无需注册任何单例。

一律**不关闭调用方传入的流**：读不关 `InputStream`，写 `finish()` / `close()` 只 flush 并关闭内核 writer，不关 `OutputStream`。流的开关与生命周期由调用方负责。支持 xls / xlsx，由内核按文件签名自动识别。

## 一次性读写（`ExcelKit`，小文件）

`ExcelKit.readAll` 一次性把全部行读进 `List`，**内存 O(行数)，仅定位小文件**；大文件请用 `Excel.read` 分批。`ExcelKit.write` 是「writer 会话 + 一批写 + `finish()`」的语法糖。

```java
// 一次性全量读（无数据行返回空 List，非 null）
List<OrderRow> rows = ExcelKit.readAll(in, OrderRow.class);

// 列表一次写；rows 为 null / 空时写表头后结束，不抛异常
ExcelKit.write(out, OrderRow.class, rows);

// 指定 Sheet 名的重载
ExcelKit.write(out, OrderRow.class, "订单", rows);
```

## 流式读写（`Excel`，大文件）

### 分批读

`Excel.read` 按行 SAX 解析，累积满 `batchSize` 行回调一次，末批不足亦回调一次；回调后本模块不再持有该批引用，内存 `O(batchSize × 单对象大小)`，与文件总行数无关。两参重载默认 `batchSize=1000`。

```java
// 两参重载：默认 batchSize = 1000
Excel.read(in, OrderRow.class, batch -> {
    for (OrderRow row : batch) {
        handle(row);
    }
});

// 指定 batchSize
Excel.read(in, OrderRow.class, 500, batch -> handle(batch));

// 回调内抛异常会包装为 ExcelException（cause = 原异常）并中止读
```

### 多次追加写

`Excel.writer(...)` 创建会话时不写 `out`；首次 `write`（含空列表）写一次表头，同实例多次 `write` 按调用顺序追加到同一 Sheet；`finish()` / `close()` 幂等收尾（flush + 关闭内核 writer，不关 `out`）；**已 `finish()` 后再 `write` 抛 `ExcelException`**。writer 会话非线程安全，须单线程写入；不同会话互不干扰。静态读无共享状态，可并发。

写 API 为**分步调用**，各步返回 `void`，不支持链式写法：

```java
// 分步调用：Excel.writer(...) → write(...) → finish()
Excel writer = Excel.writer(out, OrderRow.class, "订单");
writer.write(batch1);
writer.write(batch2);
writer.finish();

// 便于 try-with-resources：close() 等价 finish()
try (Excel session = Excel.writer(out, OrderRow.class)) {
    session.write(batch1);
    session.write(batch2);
}   // 自动 close() == finish()
```

## 注解用法

行模型 POJO 须有**可访问的无参构造器**；默认扫描当前类声明的全部字段（不含父类，`static` / `transient` 字段跳过），字段可为 `private`。

```java
public class OrderRow {

    @ExcelColumn(index = 0)                 // 第 0 列，按列位置定位（优先于表头名）
    private long id;

    @ExcelColumn(value = "订单号")          // 表头名 = "订单号"
    private String orderNo;

    private BigDecimal amount;              // 无注解：表头名 = 字段名原样 "amount"

    @ExcelIgnore                            // 读写两侧都排除；即使同时标 @ExcelColumn 也排除
    private String internalMemo;

    public OrderRow() { }                   // 可访问的无参构造器
    // getter / setter 省略
}
```

- `@ExcelColumn` 仅两个属性：`value`（表头名，缺省用字段名原样）与 `index`（列序，0 起；`>=0` 时按列位置定位，优先于表头名）。**不提供 `converterClass` 等自定义转换器入口**。
- 表头名**严格相等**匹配（`String.equals`）：不折叠大小写、不做驼峰与下划线互转。
- 未匹配到字段的表头列忽略；匹配不到表头列的字段保持 Java 默认值。
- 两个字段 `index` 相同，或表头出现重复名称 → `ExcelException`。
- 全部字段被 `@ExcelIgnore`（或无字段）→ 读返回空对象行、写仅表头，均不抛异常。

### 内置支持类型

`String`；`int` / `Integer` / `long` / `Long` / `double` / `Double` / `float` / `Float` / `short` / `Short` / `byte` / `Byte`；`boolean` / `Boolean`；`BigDecimal` / `BigInteger`；`java.util.Date`；`java.time.LocalDate` / `LocalDateTime` / `LocalTime`；`char` / `Character`。

其它类型（自定义 POJO / 枚举 / `Map` / `List` 字段）：读时解析转换错误、写时参数错误。无自定义转换器 SPI。

## 异常

全部失败收敛到唯一异常 `ExcelException`（`extends RuntimeException`，公共方法不声明 `throws` 子句）。三段分类：**参数错误**（入参非法、批大小非正、已结束会话再写、类型不支持、依赖缺失）/ **IO 错误**（流读写 / flush 失败）/ **解析转换错误**（值转换失败、表头重复、批次回调自身抛异常）。消息携带定位信息，IO / 转换 / 回调类异常保留底层 `cause`。

**业务方只 `catch`、不要 `new`**（构造器为 `public` 是因内部唯一构造点与公共类型不同包，并非对外构造入口）。所有失败都不降级：不返回半截数据、不静默跳过错误行、不吞 cause。

## 本期不做（边界）

CSV 读写；模板导出；公式 / 图片 / 富文本 / 批注 / 合并单元格 / 超链接；自定义转换器 SPI；JSR-303 / Bean Validation；provider / SPI / 多后端；独立配置键 / Plugin / 命名实例；加密 / 密码保护 / 数字签名文档；多 Sheet 高级编排（读写默认单 Sheet：读第 0 个 Sheet、写单 Sheet）；流式读的随机访问与回退（回调后不可回退、不缓存历史批）。

与 `aifei-oss` 的关系：本模块只认 `InputStream` / `OutputStream`，「导入源 / 导出落盘」由使用方把 `aifei-oss` 的流传入本模块实现，**两者不产生代码依赖**。

## 构建与验证

```bash
cd aifei-excel
mvn clean verify                                  # 必须 0 failure / 0 error（61 例，含门禁 14 例）
mvn test -Dtest=ArchitectureBoundaryTest           # 仅跑架构门禁（14 例）
mvn dependency:tree                               # 断言：仅 aifei-all(provided) + easyexcel(optional) + junit(test)
javap -verbose target/classes/cn/hg/aifei/excel/Excel.class | findstr "major version"   # 期望 52
```

## FAQ

1. **大文件该用哪个 API？** 读用 `Excel.read` 分批回调（内存 `O(batchSize)`）；写用 `Excel.writer` 会话分批追加。`ExcelKit.readAll` 内存 `O(行数)`，只适合小文件。
2. **没引 easyexcel 会怎样？** 首次调用读写 API 抛可读 `ExcelException`（提示补齐 `com.alibaba:easyexcel:3.3.2`），不是 `NoClassDefFoundError`。这是能力按需引入，不引则读写完全不可用。
3. **为什么不能写 `Excel.writer(...).write(rows).finish()`？** `write` / `finish` 返回 `void`，须按 `Excel.writer(...)` → `write(...)` → `finish()` 分步调用。
4. **支持 xls 还是 xlsx？** 两者都支持，由内核按文件签名自动识别；不做 CSV。
5. **表头大小写 / 驼峰下划线会转换吗？** 不会，严格相等匹配；要改表头名用 `@ExcelColumn.value`。
6. **writer 会话能多线程写吗？** 不能，非线程安全，须单线程；静态读可并发。
7. **字段能继承吗？** 本期只扫当前类声明字段，不含父类；`static` / `transient` 跳过。
8. **流由谁关闭？** 调用方。本模块读不关 `in`、`finish()` / `close()` 不关 `out`。
9. **为什么 `ExcelKit` 不需要装配？** 它是无生命周期的一次性门面，随取随用；与 `aifei-http` 的 `HttpKit`（依赖 `HttpPlugin` 装配、未装配即失败）是两种门面形态。
10. **POI 4.1.2 的安全公告怎么处理？** 本版按定档锁 `com.alibaba:easyexcel:3.3.2` / POI 4.1.2（EasyExcel 3.3.2 固定依赖该版本；换 5.5.1 需 exclusion + 显式声明，可能破坏其二进制兼容）。该风险登记为 P1 非阻塞，是否整体升级由主理人决定，本版不私自变更。

## 示例工程

[aifei-excel-demo](../aifei-excel-demo/)（**示例工程，不发布**）：自写 HIO、单页演示，端口 **18150**，真实演示「上传解析 → 分批统计 / 一次性导出 / 流式导出」。启动 `start-demo.bat`、停止 `stop-demo.bat`，验证地址 `http://localhost:18150/excel/demo/page`。demo 以 `compile` 自带 `com.alibaba:easyexcel:3.3.2` 与 `cn.aifei:aifei-all`，用于验证 `optional` / `provided` 口径。

## 文档

| 文档 | 内容 |
| --- | --- |
| `docs/design.md` | 契约事实来源：公共面逐字签名、注解与内存边界、异常语义表、门禁、AC |
| `docs/testing.md` | 测试策略、门禁 14 例 + 功能 47 例、AC ↔ 用例 ↔ 结果台账 |
| `AGENTS.md` | 开发约束：硬约束、包边界、唯一定义点、验证命令 |
| `CHANGELOG.md` | 版本变更与升级动作 |
