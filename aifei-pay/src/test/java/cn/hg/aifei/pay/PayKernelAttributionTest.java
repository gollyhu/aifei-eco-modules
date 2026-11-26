/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 依赖内核不复制（AC-21）：IJPay 只以 Maven 构件引入，本模块源码不含其包声明、不重新定义其核心类型，
 * 且 README / 设计文档保留了上游署名与 Apache-2.0 许可声明。
 *
 * <p>设计 §1.5 与本类共同承担 AC-21 的机械可验证部分；「是否存在整段复制的口语化判断」仍属人工审查，
 * 这里用「内核包声明 + 核心类型定义」两类硬痕迹做客观拦截。</p>
 */
public class PayKernelAttributionTest {

    /**
     * 主源码根目录（相对模块根）。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 内核包声明痕迹。
     */
    private static final String[] KERNEL_PACKAGE_DECLARATIONS = {
            "package com.ijpay",
            "package com.alipay"
    };

    /**
     * 内核核心类型定义痕迹（若被复制进本仓库必然出现）。
     */
    private static final Pattern[] KERNEL_TYPE_DEFINITIONS = {
            Pattern.compile("class\\s+AliPayTradeApi\\b"),
            Pattern.compile("class\\s+AliPayApi\\b"),
            Pattern.compile("class\\s+AliPayApiConfig\\b"),
            Pattern.compile("class\\s+AlipaySignature\\b"),
            Pattern.compile("class\\s+ApiClient\\b"),
            Pattern.compile("interface\\s+ApiClient\\b")
    };

    /**
     * AC-21：主源码不得出现内核包的 {@code package} 声明（排除源码复制）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void shouldNotContainKernelPackageSource() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            for (String declaration : KERNEL_PACKAGE_DECLARATIONS) {
                if (code.contains(declaration)) {
                    offenders.add(file + " -> " + declaration);
                }
            }
        }
        assertTrue("本模块不得包含内核源码（包声明），违规：" + offenders, offenders.isEmpty());
    }

    /**
     * AC-21：主源码不得重新定义内核核心类型（只允许以字符串常量等方式引用其全限定名做依赖探测）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void shouldNotRedefineKernelTypes() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            for (Pattern definition : KERNEL_TYPE_DEFINITIONS) {
                if (definition.matcher(code).find()) {
                    offenders.add(file + " -> " + definition.pattern());
                }
            }
        }
        assertTrue("本模块不得重新定义内核类型，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * AC-21：IJPay 以 Maven 构件引入（而非把源码拷进仓库）。
     *
     * @throws IOException 读取构建文件失败
     */
    @Test
    public void shouldDependOnIjpayAsMavenArtifact() throws IOException {
        String pom = new String(Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);

        assertTrue("pom 应声明 IJPay-AliPay-V3 构件", pom.contains("<artifactId>IJPay-AliPay-V3</artifactId>"));
        assertTrue("pom 应声明构件版本属性", pom.contains("<ijpay.version>"));
        assertTrue("pom 应声明支付宝 SDK 构件",
                pom.contains("<artifactId>alipay-sdk-java-v3</artifactId>"));
    }

    /**
     * AC-21：README 与设计文档保留上游署名与 Apache-2.0 许可声明。
     *
     * @throws IOException 读取文档失败
     */
    @Test
    public void shouldDeclareUpstreamOriginAndLicense() throws IOException {
        String readme = new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8);
        String design = new String(Files.readAllBytes(Paths.get("docs/design.md")), StandardCharsets.UTF_8);

        assertTrue("README 应声明 IJPay 构件坐标", readme.contains("com.github.javen205:IJPay-AliPay-V3"));
        assertTrue("README 应声明 Apache-2.0 许可", readme.contains("Apache License 2.0"));
        assertTrue("README 应保留上游版权署名",
                readme.contains("Copyright (c) 2017 IJPay Authors"));
        assertTrue("README 应给出来源项目地址", readme.contains("gitee.com/javen205/IJPay"));

        assertTrue("设计文档应声明 IJPay 构件坐标", design.contains("IJPay-AliPay-V3"));
        assertTrue("设计文档应声明 Apache-2.0 许可", design.contains("Apache License 2.0"));
        assertFalse("文档不得声称改写了上游源码", readme.contains("改写 IJPay 源码"));
    }

    /**
     * 收集主源码文件。
     *
     * @return Java 源码文件列表
     * @throws IOException 遍历失败
     */
    private static List<Path> mainSources() throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(),
                Files.isDirectory(SOURCE_ROOT));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            stream.filter(path -> path.toString().endsWith(".java")).filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertFalse("未扫描到任何主源码", files.isEmpty());
        return files;
    }
}
