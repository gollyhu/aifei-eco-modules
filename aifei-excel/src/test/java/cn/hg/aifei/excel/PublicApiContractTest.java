/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 公共 API 契约测试：
 * 精确 5 顶层公共类型、逐字方法签名、公共面零第三方/框架类型、class major 52、
 * 依赖 scope/optional、无 SPI 文件。全部基于运行时反射 / class 字节 / pom 文本实测。
 */
public class PublicApiContractTest {

    private static final Path CLASSES = Paths.get("target/classes/cn/hg/aifei/excel");
    private static final Path POM = Paths.get("pom.xml");
    private static final Path SERVICES = Paths.get("src/main/resources/META-INF/services");

    private static final Set<String> EXPECTED_ROOT_CLASSES = new LinkedHashSet<String>(Arrays.asList(
            "Excel.class", "ExcelKit.class", "ExcelException.class",
            "ExcelColumn.class", "ExcelIgnore.class"));

    // ---------------------------------------------------------------- 组1：公共面精确 5 类型

    @Test
    public void rootPackage_hasExactlyFivePublicTopLevelTypes() throws IOException {
        Set<String> names = new LinkedHashSet<String>();
        try (java.util.stream.Stream<Path> paths = Files.list(CLASSES)) {
            for (Path file : paths.toArray(Path[]::new)) {
                String name = file.getFileName().toString();
                if (name.endsWith(".class") && name.indexOf('$') < 0) {
                    names.add(name);
                }
            }
        }
        assertEquals("根包顶层 class 须精确为 5 个公共类型", EXPECTED_ROOT_CLASSES, names);
        assertTrue("Excel 须为 public final", Modifier.isPublic(Excel.class.getModifiers())
                && Modifier.isFinal(Excel.class.getModifiers()));
        assertTrue("ExcelKit 须为 public final", Modifier.isPublic(ExcelKit.class.getModifiers())
                && Modifier.isFinal(ExcelKit.class.getModifiers()));
        assertTrue("ExcelException 须为 public final",
                Modifier.isPublic(ExcelException.class.getModifiers())
                        && Modifier.isFinal(ExcelException.class.getModifiers()));
        assertTrue("ExcelException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(ExcelException.class));
    }

    @Test
    public void excel_signaturesMatchSectionFiveExactly() {
        assertEquals("Excel 仅 7 个公共方法", 7, publicDeclared(Excel.class).size());
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static void read(java.io.InputStream, java.lang.Class, java.util.function.Consumer)",
                "static void read(java.io.InputStream, java.lang.Class, int, java.util.function.Consumer)",
                "static cn.hg.aifei.excel.Excel writer(java.io.OutputStream, java.lang.Class)",
                "static cn.hg.aifei.excel.Excel writer(java.io.OutputStream, java.lang.Class, java.lang.String)",
                "void write(java.util.List)",
                "void finish()",
                "void close()")), publicDeclared(Excel.class));
        assertTrue("Excel 须 implements AutoCloseable",
                AutoCloseable.class.isAssignableFrom(Excel.class));
        Constructor<?>[] ctors = Excel.class.getDeclaredConstructors();
        assertEquals("Excel 仅 1 个私有构造", 1, ctors.length);
        assertTrue("Excel 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
        assertEquals("Excel 构造签名不符",
                Arrays.<Class<?>>asList(java.io.OutputStream.class, Class.class, String.class),
                Arrays.asList(ctors[0].getParameterTypes()));
    }

    @Test
    public void excelKit_signaturesMatchSectionFiveExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static java.util.List readAll(java.io.InputStream, java.lang.Class)",
                "static void write(java.io.OutputStream, java.lang.Class, java.util.List)",
                "static void write(java.io.OutputStream, java.lang.Class, java.lang.String, java.util.List)")),
                publicDeclared(ExcelKit.class));
        Constructor<?>[] ctors = ExcelKit.class.getDeclaredConstructors();
        assertEquals("ExcelKit 仅 1 个私有构造", 1, ctors.length);
        assertTrue("ExcelKit 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
        assertEquals("ExcelKit 构造须无参", 0, ctors[0].getParameterTypes().length);
    }

    @Test
    public void excelException_twoPublicConstructorsOnly() {
        assertEquals("ExcelException 仅 2 个公共构造", 2, ExcelException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : ExcelException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("ExcelException 无自有公共方法", 0, publicDeclared(ExcelException.class).size());
    }

    @Test
    public void annotations_runtimeFieldTargetAndShapeExact() throws Exception {
        assertEquals(RetentionPolicy.RUNTIME, ExcelColumn.class.getAnnotation(Retention.class).value());
        assertEquals(RetentionPolicy.RUNTIME, ExcelIgnore.class.getAnnotation(Retention.class).value());
        assertArrayEquals(new ElementType[]{ElementType.FIELD},
                ExcelColumn.class.getAnnotation(Target.class).value());
        assertArrayEquals(new ElementType[]{ElementType.FIELD},
                ExcelIgnore.class.getAnnotation(Target.class).value());
        assertEquals("ExcelColumn.value 缺省须为空串", "", ExcelColumn.class.getMethod("value").getDefaultValue());
        assertEquals("ExcelColumn.index 缺省须为 -1", -1, ExcelColumn.class.getMethod("index").getDefaultValue());
        assertEquals("ExcelColumn 仅 value/index 两属性", 2, ExcelColumn.class.getDeclaredMethods().length);
        assertEquals("ExcelIgnore 无属性", 0, ExcelIgnore.class.getDeclaredMethods().length);
    }

    @Test
    public void publicSignatures_noThirdPartyOrFrameworkTypes() {
        Class<?>[] roots = {Excel.class, ExcelKit.class, ExcelException.class, ExcelColumn.class, ExcelIgnore.class};
        for (Class<?> root : roots) {
            for (Method m : root.getDeclaredMethods()) {
                assertPureJdkOrModule(m.getReturnType(), root.getSimpleName() + "#" + m.getName());
                for (Class<?> p : m.getParameterTypes()) {
                    assertPureJdkOrModule(p, root.getSimpleName() + "#" + m.getName());
                }
            }
            for (Constructor<?> c : root.getDeclaredConstructors()) {
                for (Class<?> p : c.getParameterTypes()) {
                    assertPureJdkOrModule(p, root.getSimpleName() + "#<init>");
                }
            }
        }
    }

    // ---------------------------------------------------------------- 组1：class major 52

    @Test
    public void mainClasses_majorVersionIsJava8() throws IOException {
        String[] classes = {
                "cn/hg/aifei/excel/Excel.class",
                "cn/hg/aifei/excel/ExcelKit.class",
                "cn/hg/aifei/excel/ExcelException.class",
                "cn/hg/aifei/excel/ExcelColumn.class",
                "cn/hg/aifei/excel/ExcelIgnore.class",
                "cn/hg/aifei/excel/internal/easyexcel/BatchReadListener.class",
                "cn/hg/aifei/excel/internal/easyexcel/RowMapper.class",
                "cn/hg/aifei/excel/internal/easyexcel/ExcelWriterAdapter.class"};
        for (String name : classes) {
            assertEquals(name + " 的 class major 须为 52（Java 8）", 52, classMajor(name));
        }
    }

    // ---------------------------------------------------------------- 组6：依赖边界文本复核

    @Test
    public void pom_zeroAifeiDependency_easyExcelOptional_junitTest() throws IOException {
        String pom = new String(Files.readAllBytes(POM), StandardCharsets.UTF_8);
        assertFalse("本模块源码零引用，pom 不得声明任何 cn.aifei 依赖",
                pom.contains("<groupId>cn.aifei</groupId>"));
        assertFalse("pom 不得再声明 aifei-all", pom.contains("<artifactId>aifei-all</artifactId>"));
        assertTrue("easyexcel 须 optional", dependencyBlock(pom, "easyexcel").contains("<optional>true</optional>"));
        assertTrue("junit 须 test", dependencyBlock(pom, "junit").contains("<scope>test</scope>"));
        for (String forbidden : Arrays.asList("slf4j-simple", "logback", "log4j",
                "mysql-connector", "postgresql", "ojdbc", "hikari", "druid", "c3p0")) {
            assertFalse("pom 禁止声明: " + forbidden, pom.toLowerCase().contains(forbidden.toLowerCase()));
        }
    }

    @Test
    public void noServiceProviderFiles() {
        assertFalse("库模块不得存在 META-INF/services", Files.exists(SERVICES));
    }

    // ---------------------------------------------------------------- 工具

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull("找不到 class 资源: " + resource, raw);
        try {
            DataInputStream in = new DataInputStream(raw);
            assertEquals("非 class 文件: " + resource, 0xCAFEBABE, in.readInt());
            in.readUnsignedShort(); // minor
            return in.readUnsignedShort(); // major
        } finally {
            raw.close();
        }
    }

    private static Set<String> publicDeclared(Class<?> type) {
        Set<String> sigs = new LinkedHashSet<String>();
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                sigs.add((Modifier.isStatic(m.getModifiers()) ? "static " : "")
                        + m.getReturnType().getName() + " " + m.getName() + "(" + join(m.getParameterTypes()) + ")");
            }
        }
        return sigs;
    }

    private static String join(Class<?>[] types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(types[i].getName());
        }
        return sb.toString();
    }

    private static void assertPureJdkOrModule(Class<?> type, String where) {
        if (type.isPrimitive()) {
            return;
        }
        String name = type.getName();
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        assertFalse(where + " 泄漏第三方类型: " + name,
                name.startsWith("com.alibaba.") || name.startsWith("org.apache.poi.")
                        || name.startsWith("cn.aifei."));
        assertTrue(where + " 出现非 JDK/本模块类型: " + name,
                name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("cn.hg.aifei.excel"));
    }

    private static String dependencyBlock(String pom, String artifactId) {
        int at = pom.indexOf("<artifactId>" + artifactId + "</artifactId>");
        assertTrue("pom 未声明依赖: " + artifactId, at >= 0);
        int start = pom.lastIndexOf("<dependency>", at);
        int end = pom.indexOf("</dependency>", at);
        assertTrue("依赖块不完整: " + artifactId, start >= 0 && end > at);
        return pom.substring(start, end);
    }
}
