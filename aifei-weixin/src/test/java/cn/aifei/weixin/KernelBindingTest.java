/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 架构门禁 G3：内核绑定收敛。
 *
 * <pre>
 * ① 不存在 cn.aifei.vip.* 与 io.undertow.* 的 import（对 arch / undertow 零依赖）
 * ② cn.aifei.core.* 的 import 只允许出现在 cn.aifei.weixin.aifei 包
 * </pre>
 */
public class KernelBindingTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    private static final String AIFEI_BRIDGE_PACKAGE = "cn/aifei/weixin/aifei/";

    @Test
    public void noArchOrUndertowReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = read(file);
            if (content.contains("cn.aifei.vip.") || content.contains("io.undertow.")) {
                offenders.add(file.toString());
            }
        }
        assertTrue("SDK 不得依赖 arch 或 undertow，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void noSqlReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import java.sql.")) {
                offenders.add(file.toString());
            }
        }
        assertTrue("SDK 禁止直连数据库（多租户反查归应用侧），违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void kernelCoreImportOnlyInAifeiPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = read(file);
            if (content.contains("import cn.aifei.core.") && !normalize(file).contains(AIFEI_BRIDGE_PACKAGE)) {
                offenders.add(file.toString());
            }
        }
        assertTrue("cn.aifei.core.* 只允许出现在 cn.aifei.weixin.aifei 包，违规文件：" + offenders, offenders.isEmpty());
    }

    @Test
    public void aifeiBridgePackageExists() throws IOException {
        assertTrue("接入层包不应为空", Files.exists(SOURCE_ROOT.resolve(AIFEI_BRIDGE_PACKAGE)));
        assertFalse("唯一边界类 WeixinHandler 应存在于接入层包",
                javaFiles().stream().noneMatch(p -> normalize(p).contains(AIFEI_BRIDGE_PACKAGE + "WeixinHandler.java")));
    }

    private static List<Path> javaFiles() throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(), Files.exists(SOURCE_ROOT));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertFalse("未扫描到任何 Java 源码", files.isEmpty());
        return files;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
