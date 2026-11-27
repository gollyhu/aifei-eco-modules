/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * qr 公共面形态测试：精确 4 类型、逐字签名、QrOptions defaults=300/4/M、
 * 公共方法无 throws、签名零第三方类型、class major 52。
 */
public class PublicApiContractQrTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("QrKit 须 public final", isPublicFinal(QrKit.class));
        assertTrue("QrOptions 须 public final", isPublicFinal(QrOptions.class));
        assertTrue("QrErrorCorrection 须 public enum",
                QrErrorCorrection.class.isEnum() && Modifier.isPublic(QrErrorCorrection.class.getModifiers()));
        assertTrue("QrException 须 public final", isPublicFinal(QrException.class));
        assertTrue("QrException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(QrException.class));
    }

    @Test
    public void qrKit_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static byte[] encode(java.lang.String)",
                "static byte[] encode(java.lang.String, cn.hg.aifei.tools.qr.QrOptions)",
                "static java.lang.String decode(byte[])",
                "static java.lang.String decode(java.io.InputStream)")), publicDeclared(QrKit.class));
        Constructor<?>[] ctors = QrKit.class.getDeclaredConstructors();
        assertEquals("QrKit 仅 1 个私有构造", 1, ctors.length);
        assertTrue("QrKit 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void qrOptions_signaturesAndDefaultsExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static cn.hg.aifei.tools.qr.QrOptions defaults()",
                "static cn.hg.aifei.tools.qr.QrOptions of(int, int, cn.hg.aifei.tools.qr.QrErrorCorrection)",
                "int getSize()",
                "int getMargin()",
                "cn.hg.aifei.tools.qr.QrErrorCorrection getErrorCorrection()")),
                publicDeclared(QrOptions.class));
        Constructor<?>[] ctors = QrOptions.class.getDeclaredConstructors();
        assertEquals("QrOptions 仅 1 个私有构造", 1, ctors.length);
        assertTrue("QrOptions 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));

        QrOptions def = QrOptions.defaults();
        assertEquals("默认 size 须为 300", 300, def.getSize());
        assertEquals("默认 margin 须为 4", 4, def.getMargin());
        assertEquals("默认纠错等级须为 M", QrErrorCorrection.M, def.getErrorCorrection());
    }

    @Test
    public void qrErrorCorrection_constantsExactly() {
        assertArrayEqualsConstant(new QrErrorCorrection[]{
                QrErrorCorrection.L, QrErrorCorrection.M, QrErrorCorrection.Q, QrErrorCorrection.H},
                QrErrorCorrection.values());
    }

    @Test
    public void qrException_twoPublicConstructorsOnly() {
        assertEquals("QrException 仅 2 个公共构造", 2, QrException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : QrException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("QrException 无自有公共方法", 0, publicDeclared(QrException.class).size());
    }

    @Test
    public void publicMethods_noThrowsClause() {
        Class<?>[] types = {QrKit.class, QrOptions.class, QrErrorCorrection.class, QrException.class};
        for (Class<?> t : types) {
            for (Method m : t.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers())) {
                    assertEquals(t.getSimpleName() + "#" + m.getName() + " 不得声明 throws",
                            0, m.getExceptionTypes().length);
                }
            }
            for (Constructor<?> c : t.getDeclaredConstructors()) {
                if (Modifier.isPublic(c.getModifiers())) {
                    assertEquals(t.getSimpleName() + " 公共构造不得声明 throws",
                            0, c.getExceptionTypes().length);
                }
            }
        }
    }

    @Test
    public void publicSignatures_noThirdPartyTypes() {
        Class<?>[] types = {QrKit.class, QrOptions.class, QrErrorCorrection.class, QrException.class};
        for (Class<?> t : types) {
            for (Method m : t.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                assertPureJdkOrModule(m.getReturnType(), t.getSimpleName() + "#" + m.getName());
                for (Class<?> p : m.getParameterTypes()) {
                    assertPureJdkOrModule(p, t.getSimpleName() + "#" + m.getName());
                }
            }
        }
    }

    @Test
    public void mainClasses_majorVersionIsJava8() throws IOException {
        String[] classes = {
                "cn/hg/aifei/tools/qr/QrKit.class",
                "cn/hg/aifei/tools/qr/QrOptions.class",
                "cn/hg/aifei/tools/qr/QrErrorCorrection.class",
                "cn/hg/aifei/tools/qr/QrException.class"};
        for (String name : classes) {
            assertEquals(name + " 的 class major 须为 52", 52, classMajor(name));
        }
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static void assertArrayEqualsConstant(QrErrorCorrection[] expected, QrErrorCorrection[] actual) {
        assertEquals(Arrays.asList(expected), Arrays.asList(actual));
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractQrTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull("找不到 class 资源: " + resource, raw);
        try {
            DataInputStream in = new DataInputStream(raw);
            assertEquals("非 class 文件", 0xCAFEBABE, in.readInt());
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } finally {
            raw.close();
        }
    }

    private static Set<String> publicDeclared(Class<?> type) {
        Set<String> sigs = new LinkedHashSet<String>();
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                sigs.add((Modifier.isStatic(m.getModifiers()) ? "static " : "")
                        + typeName(m.getReturnType()) + " " + m.getName()
                        + "(" + join(m.getParameterTypes()) + ")");
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
            sb.append(typeName(types[i]));
        }
        return sb.toString();
    }

    private static String typeName(Class<?> type) {
        if (!type.isArray()) {
            return type.getName();
        }
        StringBuilder sb = new StringBuilder(typeName(type.getComponentType()));
        sb.append("[]");
        return sb.toString();
    }

    private static void assertPureJdkOrModule(Class<?> type, String where) {
        if (type.isPrimitive()) {
            return;
        }
        if (type.isArray()) {
            assertPureJdkOrModule(type.getComponentType(), where);
            return;
        }
        String name = type.getName();
        assertFalse(where + " 泄漏第三方/框架类型: " + name,
                name.startsWith("org.lionsoul.") || name.startsWith("com.google.zxing.")
                        || name.startsWith("cn.aifei.") || name.startsWith("cn.hg.aifei.cache."));
        assertTrue(where + " 出现非 JDK/本模块类型: " + name,
                name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("cn.hg.aifei.tools"));
    }
}
