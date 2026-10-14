/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 架构门禁：内核绑定收敛。
 * ① 不存在 cn.aifei.vip.* / io.undertow.* / java.sql.* 引用（对 arch / undertow / DB 零依赖）
 * ② cn.aifei.aop.* 与 cn.aifei.core.* 的 import 只允许出现在接入层包 cn.hg.aifei.sentinel.aifei
 * ③ 不硬引用 com.alibaba.csp.sentinel.transport.*（SDK 零硬引用，仅运行时反射探测）
 */
public class KernelBindingTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    private static final String AIFEI_BRIDGE_PACKAGE = "cn/hg/aifei/sentinel/aifei/";

    @Test
    public void noArchOrUndertowOrSqlReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = read(file);
            if (content.contains("cn.aifei.vip.") || content.contains("io.undertow.") || content.contains("java.sql.")) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("SDK 不得依赖 arch / undertow / sql，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void aopImportOnlyInAifeiPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import cn.aifei.aop.") && !normalize(file).contains(AIFEI_BRIDGE_PACKAGE)) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("cn.aifei.aop.* 只允许出现在接入层包，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void coreImportOnlyInAifeiPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import cn.aifei.core.") && !normalize(file).contains(AIFEI_BRIDGE_PACKAGE)) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("cn.aifei.core.* 只允许出现在接入层包，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void noHardReferenceToTransportClasses() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import com.alibaba.csp.sentinel.transport.")) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("SDK 不得硬引用 sentinel-transport-* 类（须反射探测，否则消费方未引 transport 时 NoClassDefFoundError），违规文件："
                + offenders, offenders.isEmpty());
    }

    private static List<Path> javaFiles() throws IOException {
        Assert.assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(), Files.exists(SOURCE_ROOT));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        Assert.assertFalse("未扫描到任何 Java 源码", files.isEmpty());
        return files;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
