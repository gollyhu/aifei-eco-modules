# 第三方运行内核版权与许可声明

本模块（`aifei-tools`）自身为原创实现；四个工具中仅 **IP 归属地** 与 **二维码** 依赖可选第三方运行内核。本文件汇总这两个可选内核的版权与许可信息，**仅作署名留痕，不复制许可全文**——许可全文随各依赖 jar 自带（或见上游项目发行包）。

## 1. ip2region（IP 归属地内核）

- 坐标：`org.lionsoul:ip2region:3.3.7`
- 许可：**Apache-2.0 OR MIT**（双许可，可任选其一；上游 `LICENSE.md` 声明 SPDX `Apache-2.0 OR MIT`）
- 来源项目：Maven Central `https://repo1.maven.org/maven2/org/lionsoul/ip2region/3.3.7/`；GitHub 仓库 `https://github.com/lionsoul2014/ip2region`
- 版权：`Copyright (c) 2015 Lionsoul`（上游 `LICENSE.md` 版权行；发行包 POM 开发者为 `chenxin`，id `lionsoul`）
- xdb 数据文件：由消费方自备，其版权与许可归**数据提供方**所有；本模块不打包、不分发、不对其作任何权利声明。

## 2. ZXing core（二维码内核）

- 坐标：`com.google.zxing:core:3.5.3`（只引 `core`，不引 `javase`）
- 许可：**Apache License 2.0**
- 来源项目：Maven Central `https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/`；GitHub 仓库 `https://github.com/zxing/zxing`
- 版权：`Copyright (C) 2010 ZXing authors`（发行包 POM 文件头版权声明；jar Manifest 中 `Bundle-Developers: ZXing Authors`）

## 不在署名范围

- `io.github.gollyhu:aifei-cache` 是本生态自家模块、非第三方，`junit:junit` 为 test scope、不随发布分发，二者均不在本声明范围。

## 分发提示

消费方正式分发含本模块的产品时，请按上述许可要求保留相应署名与许可声明。
