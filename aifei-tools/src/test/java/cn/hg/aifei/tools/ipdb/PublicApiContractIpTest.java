/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ipdb 公共面形态测试（设计 §3.1 / §6.2）：精确 5 类型、逐字签名、公共方法无 throws、
 * 签名零第三方类型、class major 52。基于运行时反射与 class 字节实测。
 */
public class PublicApiContractIpTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("IpSearcher 须 public final", isPublicFinal(IpSearcher.class));
        assertTrue("IpKit 须 public final", isPublicFinal(IpKit.class));
        assertTrue("IpRegion 须 public final", isPublicFinal(IpRegion.class));
        assertTrue("IpException 须 public final", isPublicFinal(IpException.class));
        assertTrue("IpQueryMode 须 public enum",
                IpQueryMode.class.isEnum() && Modifier.isPublic(IpQueryMode.class.getModifiers()));
        assertTrue("IpSearcher 须 implements AutoCloseable",
                AutoCloseable.class.isAssignableFrom(IpSearcher.class));
        assertTrue("IpException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(IpException.class));
    }

    @Test
    public void ipSearcher_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static cn.hg.aifei.tools.ipdb.IpSearcher open(java.lang.String, java.lang.String)",
                "static cn.hg.aifei.tools.ipdb.IpSearcher open(java.lang.String, java.lang.String, cn.hg.aifei.tools.ipdb.IpQueryMode)",
                "cn.hg.aifei.tools.ipdb.IpRegion search(java.lang.String)",
                "void close()")), publicDeclared(IpSearcher.class));
        Constructor<?>[] ctors = IpSearcher.class.getDeclaredConstructors();
        assertEquals("IpSearcher 仅 1 个私有构造", 1, ctors.length);
        assertTrue("IpSearcher 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void ipKit_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static cn.hg.aifei.tools.ipdb.IpRegion search(java.lang.String, java.lang.String, java.lang.String)",
                "static cn.hg.aifei.tools.ipdb.IpRegion search(java.lang.String, java.lang.String, java.lang.String, cn.hg.aifei.tools.ipdb.IpQueryMode)")),
                publicDeclared(IpKit.class));
        Constructor<?>[] ctors = IpKit.class.getDeclaredConstructors();
        assertEquals("IpKit 仅 1 个私有构造", 1, ctors.length);
        assertTrue("IpKit 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
        assertEquals("IpKit 构造须无参", 0, ctors[0].getParameterTypes().length);
    }

    @Test
    public void ipRegion_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static cn.hg.aifei.tools.ipdb.IpRegion of(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)",
                "java.lang.String getCountry()",
                "java.lang.String getProvince()",
                "java.lang.String getCity()",
                "java.lang.String getIsp()",
                "java.lang.String getIsoCode()")), publicDeclared(IpRegion.class));
        Constructor<?>[] ctors = IpRegion.class.getDeclaredConstructors();
        assertEquals("IpRegion 仅 1 个私有构造", 1, ctors.length);
        assertTrue("IpRegion 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void ipQueryMode_constantsExactly() {
        assertArrayEquals(new IpQueryMode[]{IpQueryMode.FILE_ONLY, IpQueryMode.VECTOR_INDEX, IpQueryMode.CONTENT},
                IpQueryMode.values());
    }

    @Test
    public void ipException_twoPublicConstructorsOnly() {
        assertEquals("IpException 仅 2 个公共构造", 2, IpException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : IpException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("IpException 无自有公共方法", 0, publicDeclared(IpException.class).size());
    }

    @Test
    public void publicMethods_noThrowsClause() {
        Class<?>[] types = {IpSearcher.class, IpKit.class, IpRegion.class, IpException.class, IpQueryMode.class};
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
        Class<?>[] types = {IpSearcher.class, IpKit.class, IpRegion.class, IpException.class, IpQueryMode.class};
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
            for (Constructor<?> c : t.getDeclaredConstructors()) {
                for (Class<?> p : c.getParameterTypes()) {
                    assertPureJdkOrModule(p, t.getSimpleName() + "#<init>");
                }
            }
        }
    }

    @Test
    public void mainClasses_majorVersionIsJava8() throws IOException {
        String[] classes = {
                "cn/hg/aifei/tools/ipdb/IpSearcher.class",
                "cn/hg/aifei/tools/ipdb/IpKit.class",
                "cn/hg/aifei/tools/ipdb/IpRegion.class",
                "cn/hg/aifei/tools/ipdb/IpException.class",
                "cn/hg/aifei/tools/ipdb/IpQueryMode.class"};
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
        InputStream raw = PublicApiContractIpTest.class.getClassLoader().getResourceAsStream(resource);
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
