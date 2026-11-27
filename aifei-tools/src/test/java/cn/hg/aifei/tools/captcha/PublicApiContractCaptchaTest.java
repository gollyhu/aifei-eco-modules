/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha;

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
 * captcha 公共面形态测试：精确 4 类型、逐字签名、公共方法无 throws、
 * 签名零第三方类型、class major 52。基于运行时反射与 class 字节实测。
 */
public class PublicApiContractCaptchaTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("Captcha 须 public final", isPublicFinal(Captcha.class));
        assertTrue("CaptchaKit 须 public final", isPublicFinal(CaptchaKit.class));
        assertTrue("CaptchaType 须 public enum",
                CaptchaType.class.isEnum() && Modifier.isPublic(CaptchaType.class.getModifiers()));
        assertTrue("CaptchaException 须 public final", isPublicFinal(CaptchaException.class));
        assertTrue("CaptchaException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(CaptchaException.class));
    }

    @Test
    public void captcha_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String getToken()",
                "byte[] getImageBytes()",
                "long getExpireAt()")), publicDeclared(Captcha.class));
        Constructor<?>[] ctors = Captcha.class.getDeclaredConstructors();
        assertEquals("Captcha 仅 1 个构造", 1, ctors.length);
        assertFalse("Captcha 构造须非 public（包级）", Modifier.isPublic(ctors[0].getModifiers()));
    }

    @Test
    public void captchaKit_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static cn.hg.aifei.tools.captcha.Captcha generate()",
                "static cn.hg.aifei.tools.captcha.Captcha generate(cn.hg.aifei.tools.captcha.CaptchaType)",
                "static boolean verify(java.lang.String, java.lang.String)",
                "static void setTtlSeconds(int)",
                "static void setCapacity(int)",
                "static void setMaxFailures(int)",
                "static void enableClusterStore()")), publicDeclared(CaptchaKit.class));
        Constructor<?>[] ctors = CaptchaKit.class.getDeclaredConstructors();
        assertEquals("CaptchaKit 仅 1 个私有构造", 1, ctors.length);
        assertTrue("CaptchaKit 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void captchaType_constantsExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList("CHAR", "ARITHMETIC")),
                new LinkedHashSet<String>(Arrays.asList(names(CaptchaType.values()))));
    }

    @Test
    public void captchaException_twoPublicConstructorsOnly() {
        assertEquals("CaptchaException 仅 2 个公共构造", 2, CaptchaException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : CaptchaException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("CaptchaException 无自有公共方法", 0, publicDeclared(CaptchaException.class).size());
    }

    @Test
    public void publicMethods_noThrowsClause() {
        Class<?>[] types = {Captcha.class, CaptchaKit.class, CaptchaType.class, CaptchaException.class};
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
        Class<?>[] types = {Captcha.class, CaptchaKit.class, CaptchaType.class, CaptchaException.class};
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
                "cn/hg/aifei/tools/captcha/Captcha.class",
                "cn/hg/aifei/tools/captcha/CaptchaKit.class",
                "cn/hg/aifei/tools/captcha/CaptchaType.class",
                "cn/hg/aifei/tools/captcha/CaptchaException.class"};
        for (String name : classes) {
            assertEquals(name + " 的 class major 须为 52", 52, classMajor(name));
        }
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static String[] names(CaptchaType[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i].name();
        }
        return out;
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractCaptchaTest.class.getClassLoader().getResourceAsStream(resource);
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
