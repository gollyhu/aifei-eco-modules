/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 架构边界门禁：扫描 {@code src/main/java} 源码文本断言分层边界与公共面收敛。
 *
 * <p>依赖相对路径，必须在模块根目录执行。
 */
public class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final String ROOT_PACKAGE_PATH = "cn/hg/aifei/oss";
    private static final String ADAPTER_PACKAGE_PATH = "cn/hg/aifei/oss/internal/";
    private static final String BRIDGE_PACKAGE_PATH = "cn/hg/aifei/oss/aifei/";

    @Test
    public void xfsOnlyInAdapterPackage() throws IOException {
        for (Path file : javaFiles()) {
            if (relative(file).startsWith(ADAPTER_PACKAGE_PATH)) {
                continue;
            }
            if (containsText(file, "org.dromara.x.file.storage")) {
                fail("x-file-storage 类型只能出现在 internal 适配层: " + relative(file));
            }
        }
    }

    @Test
    public void frameworkOnlyInBridgePackage() throws IOException {
        for (Path file : javaFiles()) {
            if (relative(file).startsWith(BRIDGE_PACKAGE_PATH)) {
                continue;
            }
            if (containsText(file, "cn.aifei.")) {
                fail("框架类型只能出现在 aifei 接入层: " + relative(file));
            }
        }
    }

    @Test
    public void noSqlOrUndertowReference() throws IOException {
        for (Path file : javaFiles()) {
            if (containsText(file, "java.sql.") || containsText(file, "io.undertow.")) {
                fail("禁止引用 java.sql / io.undertow: " + relative(file));
            }
        }
    }

    @Test
    public void publicSurfaceExact() throws IOException {
        List<Path> rootFiles = rootPackageFiles();
        assertEquals("根包公共类型必须精确为 6 个", 6, rootFiles.size());

        List<String> names = new ArrayList<String>();
        for (Path file : rootFiles) {
            names.add(file.getFileName().toString());
            String content = read(file);
            assertTrue(relative(file) + " 未声明公共类型", content.contains("public interface ")
                    || content.contains("public class ")
                    || content.contains("public final class "));
        }
        Collections.sort(names);
        assertEquals("根包公共类型文件名集合必须精确相等",
                Arrays.asList("OssClient.java", "OssException.java", "OssFileInfo.java",
                        "OssKit.java", "OssObject.java", "OssPage.java"),
                names);
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            return stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static List<Path> rootPackageFiles() throws IOException {
        Path rootDir = SOURCE_ROOT.resolve(ROOT_PACKAGE_PATH);
        try (Stream<Path> stream = Files.list(rootDir)) {
            List<Path> result = new ArrayList<Path>();
            for (Path p : stream.collect(Collectors.toList())) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".java")) {
                    result.add(p);
                }
            }
            return result;
        }
    }

    private static boolean containsText(Path file, String needle) throws IOException {
        return read(file).contains(needle);
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String relative(Path file) {
        return SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
    }
}
