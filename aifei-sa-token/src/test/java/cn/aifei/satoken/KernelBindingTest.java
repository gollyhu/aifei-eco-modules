/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken;

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
 * 架构门禁：分层边界收敛。
 */
public class KernelBindingTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    private static final String AIFEI_BRIDGE_PACKAGE = "cn/aifei/satoken/aifei/";
    private static final String UNDERTOW_BRIDGE_PACKAGE = "cn/aifei/satoken/undertow/";
    private static final String DAO_PACKAGE = "cn/aifei/satoken/dao/";

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
    public void undertowImportOnlyInUndertowPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import io.undertow.") && !normalize(file).contains(UNDERTOW_BRIDGE_PACKAGE)) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("io.undertow.* 只允许出现在桥接层包，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void cacheImportOnlyInDaoPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import cn.hg.aifei.cache.") && !normalize(file).contains(DAO_PACKAGE)) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("cn.hg.aifei.cache.* 只允许出现在持久层包，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void noArchOrSqlReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = read(file);
            if (content.contains("cn.aifei.vip.") || content.contains("java.sql.")) {
                offenders.add(file.toString());
            }
        }
        Assert.assertTrue("SDK 不得依赖 arch / sql，违规文件：" + offenders, offenders.isEmpty());
    }

    private static List<Path> javaFiles() throws IOException {
        Assert.assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(),
                Files.exists(SOURCE_ROOT));
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
