/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask;

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
 * mask 公共面形态测试：精确 3 类型、逐字签名、公共方法无 throws、
 * 签名零第三方类型、class major 52。
 */
public class PublicApiContractMaskTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("MaskUtil 须 public final", isPublicFinal(MaskUtil.class));
        assertTrue("MaskKit 须 public final", isPublicFinal(MaskKit.class));
        assertTrue("MaskException 须 public final", isPublicFinal(MaskException.class));
        assertTrue("MaskException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(MaskException.class));
    }

    @Test
    public void maskUtil_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static java.lang.String mask(java.lang.String, int, int)",
                "static java.lang.String mask(java.lang.String, int, int, char)",
                "static java.lang.String maskPhone(java.lang.String)",
                "static java.lang.String maskIdCard(java.lang.String)",
                "static java.lang.String maskBankCard(java.lang.String)",
                "static java.lang.String maskEmail(java.lang.String)",
                "static java.lang.String maskName(java.lang.String)",
                "static java.lang.String maskFixedPhone(java.lang.String)",
                "static java.lang.String maskPassword(java.lang.String)",
                "static java.lang.String maskAddress(java.lang.String)",
                "static java.lang.String maskIp(java.lang.String)")), publicDeclared(MaskUtil.class));
        Constructor<?>[] ctors = MaskUtil.class.getDeclaredConstructors();
        assertEquals("MaskUtil 仅 1 个私有构造", 1, ctors.length);
        assertTrue("MaskUtil 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void maskKit_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static void register(java.lang.String, java.util.function.UnaryOperator)",
                "static void replace(java.lang.String, java.util.function.UnaryOperator)",
                "static java.util.function.UnaryOperator use(java.lang.String)")),
                publicDeclared(MaskKit.class));
        Constructor<?>[] ctors = MaskKit.class.getDeclaredConstructors();
        assertEquals("MaskKit 仅 1 个私有构造", 1, ctors.length);
        assertTrue("MaskKit 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void maskException_twoPublicConstructorsOnly() {
        assertEquals("MaskException 仅 2 个公共构造", 2, MaskException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : MaskException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("MaskException 无自有公共方法", 0, publicDeclared(MaskException.class).size());
    }

    @Test
    public void publicMethods_noThrowsClause() {
        Class<?>[] types = {MaskUtil.class, MaskKit.class, MaskException.class};
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
        Class<?>[] types = {MaskUtil.class, MaskKit.class, MaskException.class};
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
                "cn/hg/aifei/tools/mask/MaskUtil.class",
                "cn/hg/aifei/tools/mask/MaskKit.class",
                "cn/hg/aifei/tools/mask/MaskException.class"};
        for (String name : classes) {
            assertEquals(name + " 的 class major 须为 52", 52, classMajor(name));
        }
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractMaskTest.class.getClassLoader().getResourceAsStream(resource);
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
                        + m.getReturnType().getName() + " " + m.getName()
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
            sb.append(types[i].getName());
        }
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
