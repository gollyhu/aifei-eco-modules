/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;

/**
 * 依赖侧的 Java 8 基线断言（AC-02）：独立于架构门禁，因为它需要解析 classpath 而不是读源码文本。
 *
 * <p><b>判定口径</b>（设计 §2.3，含交付裁决 A-1）：对依赖树中每个 JAR，**排除 {@code module-info.class}
 * 与 {@code META-INF/versions/**} 后的全部 {@code .class}，major ≤ 52**。依据：Java 8 虚拟机不解析 JPMS
 * 模块描述符、也不识别 {@code Multi-Release} 属性，这两类条目对 Java 8 运行期完全不可达。主 JAR 自身的
 * 断言不放宽（由 {@code ArchitectureBoundaryTest#g05Java8AndBytecode} 强制 major == 52）。</p>
 */
public class PayDepsBytecodeTest {

    /**
     * Java 8 的 class major。
     */
    private static final int JAVA8_MAJOR = 52;

    /**
     * JPMS 模块描述符文件名（根级与 Multi-Release 变体同名）。
     */
    private static final String MODULE_INFO = "module-info.class";

    /**
     * Multi-Release JAR 的版本化目录前缀。
     */
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";

    /**
     * class 文件头长度（魔数 4 + minor 2 + major 2）。
     */
    private static final int CLASS_HEADER_LENGTH = 8;

    /**
     * 期望存在的锁定构件（jar 文件名）。
     */
    private static final String[] EXPECTED_JARS = {
            "IJPay-AliPay-V3-3.0.0.jar",
            "alipay-sdk-java-v3-3.1.89.ALL.jar",
            "IJPay-AliPay-3.0.0.jar",
            "alipay-sdk-java-4.40.1067.ALL.jar",
            "fastjson-2.0.66.jar",
            "okhttp-4.12.0.jar",
            "dom4j-2.1.5.jar",
            "bcprov-jdk18on-1.86.jar",
            "IJPay-QQ-3.0.0.jar",
            "IJPay-JDPay-3.0.0.jar",
            "IJPay-UnionPay-3.0.0.jar",
            "IJPay-PayPal-3.0.0.jar",
            "IJPay-Stripe-3.0.0.jar",
            "stripe-java-34.1.0.jar",
            "IJPay-FuiYou-3.0.0.jar",
            "IJPay-XPay-3.0.0.jar",
            "IJPay-Core-3.0.0.jar"
    };

    /**
     * 旧坐标 {@code dom4j:dom4j} 的 jar 文件名式（1.x 系列）。
     */
    private static final Pattern LEGACY_DOM4J = Pattern.compile("dom4j-1\\..*\\.jar");

    /**
     * 必须不存在的构件片段。
     */
    private static final String[] FORBIDDEN_JAR_FRAGMENTS = {
            "bcprov-jdk15on"
    };

    /**
     * 依赖 JAR 数量的下限：定稿依赖树为 32 个依赖 JAR，再加测试期 JUnit 与 Hamcrest。
     */
    private static final int MINIMUM_DEPENDENCY_JARS = 40;

    /**
     * 断言依赖 classpath 上的全部可加载 class 均在 Java 8 基线内。
     *
     * @throws IOException 读取 JAR 失败
     */
    @Test
    public void dependencyClassesWithinJava8Baseline() throws IOException {
        List<Path> jars = classpathJars();
        assertTrue("未从 classpath 解析到依赖 JAR，实际为 " + jars.size() + " 个", jars.size()
                >= MINIMUM_DEPENDENCY_JARS);

        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (Path jar : jars) {
            try (ZipFile zipFile = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }
                    if (isUnreachableOnJava8(entry.getName())) {
                        continue;
                    }
                    int major = majorVersion(zipFile.getInputStream(entry));
                    scanned++;
                    if (major > JAVA8_MAJOR) {
                        offenders.add(jar.getFileName() + "!" + entry.getName() + " -> " + major);
                    }
                }
            }
        }
        assertTrue("未扫描到任何 class 条目", scanned > 0);
        assertTrue("依赖 JAR 的 class major 必须 <= " + JAVA8_MAJOR + "，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 断言依赖坐标锁定：旧 {@code dom4j:dom4j} 与 {@code bcprov-jdk15on} 不存在，锁定版本存在。
     */
    @Test
    public void dependencyCoordinatesAreLocked() {
        List<Path> jars = classpathJars();
        Set<String> names = new LinkedHashSet<>();
        for (Path jar : jars) {
            names.add(jar.getFileName().toString());
        }
        List<String> legacy = new ArrayList<>();
        List<String> forbidden = new ArrayList<>();
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (LEGACY_DOM4J.matcher(lower).matches()) {
                legacy.add(name);
            }
            for (String fragment : FORBIDDEN_JAR_FRAGMENTS) {
                if (lower.contains(fragment)) {
                    forbidden.add(name);
                }
            }
        }
        assertTrue("classpath 不得包含旧坐标 dom4j:dom4j，违规：" + legacy, legacy.isEmpty());
        assertTrue("classpath 不得包含 bcprov-jdk15on，违规：" + forbidden, forbidden.isEmpty());

        List<String> missing = new ArrayList<>();
        for (String expected : EXPECTED_JARS) {
            if (!names.contains(expected)) {
                missing.add(expected);
            }
        }
        assertEquals("依赖坐标必须锁定为 " + Arrays.toString(EXPECTED_JARS) + "，缺失：" + missing,
                new ArrayList<String>(), missing);
        assertFalse("classpath 上的依赖 JAR 不应为空", names.isEmpty());
    }

    /**
     * 判断条目对 Java 8 运行期是否不可达（JPMS 描述符或 Multi-Release 变体）。
     *
     * @param entryName JAR 内条目名
     * @return 不可达时 {@code true}
     */
    private static boolean isUnreachableOnJava8(String entryName) {
        return entryName.startsWith(MULTI_RELEASE_PREFIX) || entryName.endsWith("/" + MODULE_INFO)
                || MODULE_INFO.equals(entryName);
    }

    /**
     * 读取 class 条目的 major version。
     *
     * @param input 条目输入流（本方法负责关闭）
     * @return major version
     * @throws IOException 读取失败
     */
    private static int majorVersion(InputStream input) throws IOException {
        try (InputStream stream = input) {
            byte[] header = new byte[CLASS_HEADER_LENGTH];
            int read = 0;
            while (read < CLASS_HEADER_LENGTH) {
                int count = stream.read(header, read, CLASS_HEADER_LENGTH - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            assertTrue("class 文件头不完整", read == CLASS_HEADER_LENGTH);
            return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        }
    }

    /**
     * 解析当前 classpath 上的全部 JAR。
     *
     * @return JAR 路径列表
     */
    private static List<Path> classpathJars() {
        List<Path> jars = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");
        assertFalse("java.class.path 为空", classpath == null || classpath.isEmpty());
        String[] entries = classpath.split(Pattern.quote(File.pathSeparator));
        for (String entry : entries) {
            Path path = Paths.get(entry);
            if (Files.isRegularFile(path) && entry.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                jars.add(path);
            }
        }
        return jars;
    }
}
