/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.hg.aifei.email.spi.MailProvider;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 公共面契约（AC-08 / AC-09）：根包精确 5 类型、spi 精确 1 类型、门面形态、签名零第三方 / 零框架类型、
 * 值对象不可变与附件字节防御性复制。
 */
public class PublicApiContractTest {

    private static final String MODULE_PACKAGE_PREFIX = "cn.hg.aifei.email.";
    private static final Set<String> ROOT_TYPES = new LinkedHashSet<String>(Arrays.asList(
            "MailClient.java", "MailKit.java", "MailMessage.java", "MailResult.java", "MailException.java"));
    private static final Set<String> SPI_TYPES = new LinkedHashSet<String>(Arrays.asList("MailProvider.java"));

    /** AC-09：根包顶层类型精确 5 个（文件名集合精确相等）。 */
    @Test
    public void rootPackageHasExactlyFiveTypes() throws IOException {
        Set<String> names = typeNames("src/main/java/cn/hg/aifei/email");
        assertEquals("根包公共类型数量必须为 5", 5, names.size());
        assertEquals("根包公共类型集合必须精确相等", ROOT_TYPES, names);
    }

    /** AC-09：spi 包顶层类型精确 1 个。 */
    @Test
    public void spiPackageHasExactlyOneType() throws IOException {
        Set<String> names = typeNames("src/main/java/cn/hg/aifei/email/spi");
        assertEquals("spi 公共类型数量必须为 1", 1, names.size());
        assertEquals("spi 公共类型集合必须精确相等", SPI_TYPES, names);
    }

    /** AC-08：发送门面为接口，仅两个方法且不声明受检异常。 */
    @Test
    public void mailClientIsMinimalInterface() {
        assertTrue(MailClient.class.isInterface());
        Set<String> methods = methodNames(MailClient.class);
        assertEquals(new LinkedHashSet<String>(Arrays.asList("send", "channelName")), methods);
        for (Method method : MailClient.class.getDeclaredMethods()) {
            assertEquals("公共方法不得声明受检异常: " + method.getName(), 0, method.getExceptionTypes().length);
        }
        assertSameType(MailResult.class, declaredMethod(MailClient.class, "send", MailMessage.class).getReturnType());
    }

    /** AC-08：静态门面为 final + 私有构造 + 全静态方法。 */
    @Test
    public void mailKitIsStatefulStaticFacade() {
        assertTrue("MailKit 必须为 final", Modifier.isFinal(MailKit.class.getModifiers()));
        Constructor<?>[] constructors = MailKit.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue("MailKit 构造器必须私有", Modifier.isPrivate(constructors[0].getModifiers()));

        Set<String> names = new LinkedHashSet<String>();
        for (Method method : MailKit.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertTrue("MailKit 公共方法必须全为 static: " + method.getName(),
                    Modifier.isStatic(method.getModifiers()));
            names.add(method.getName());
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "defaultClient", "use", "channels", "send")), names);
    }

    /** AC-09：消息值对象为 final，仅可经 Builder 构造（无公共构造器），Builder 为内部类。 */
    @Test
    public void mailMessageIsImmutableWithInternalBuilder() {
        assertTrue(Modifier.isFinal(MailMessage.class.getModifiers()));
        for (Constructor<?> constructor : MailMessage.class.getDeclaredConstructors()) {
            assertFalse("MailMessage 不得暴露公共构造器", Modifier.isPublic(constructor.getModifiers()));
        }
        Class<?>[] nested = MailMessage.class.getDeclaredClasses();
        assertEquals("Builder 必须是内部类（不新增顶层类型）", 1, nested.length);
        assertEquals("cn.hg.aifei.email.MailMessage$Builder", nested[0].getName());
        assertSameType(MailMessage.class, nested[0].getDeclaringClass());
        assertTrue(Modifier.isPublic(nested[0].getModifiers()));
        assertTrue(Modifier.isStatic(nested[0].getModifiers()));
        assertSameType(nested[0], builderMethod().getReturnType());
    }

    /** AC-09：结果值对象为 final 且字段不可变。 */
    @Test
    public void mailResultIsImmutableValueObject() {
        assertTrue(Modifier.isFinal(MailResult.class.getModifiers()));
        MailResult result = new MailResult("qq", true, "sent");
        assertEquals("qq", result.getChannelName());
        assertTrue(result.isSuccess());
        assertEquals("sent", result.getMessage());
        for (java.lang.reflect.Field field : MailResult.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertTrue("字段必须私有 final: " + field.getName(),
                    Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
        }
    }

    /** AC-07：统一异常为 final 且非受检。 */
    @Test
    public void mailExceptionIsUnchecked() {
        assertTrue(Modifier.isFinal(MailException.class.getModifiers()));
        assertTrue(RuntimeException.class.isAssignableFrom(MailException.class));
        assertFalse("不得为受检异常", Exception.class.equals(MailException.class.getSuperclass()));
    }

    /** AC-08 / AC-09：spi 为接口，两个方法且签名只用 JDK 与本模块类型。 */
    @Test
    public void mailProviderSpiShape() {
        assertTrue(MailProvider.class.isInterface());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("type", "createClient")),
                methodNames(MailProvider.class));
        for (Method method : MailProvider.class.getDeclaredMethods()) {
            assertNoCheckedExceptions(MailProvider.class.getName(), method);
            assertAllowedType(method.getGenericReturnType());
            for (Type parameterType : method.getGenericParameterTypes()) {
                assertAllowedType(parameterType);
            }
        }
    }

    /** AC-08：根包 + spi 全部公共方法（含 Builder）签名零第三方 / 零框架类型、零受检异常。 */
    @Test
    public void publicSignaturesUseOnlyJdkAndModuleTypes() {
        List<Class<?>> types = new ArrayList<Class<?>>(Arrays.asList(
                MailClient.class, MailKit.class, MailMessage.class, MailResult.class, MailException.class,
                MailProvider.class, MailMessage.Builder.class));
        for (Class<?> type : types) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertNoCheckedExceptions(type.getSimpleName(), method);
                assertAllowedType(method.getGenericReturnType());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertAllowedType(parameterType);
                }
            }
        }
    }

    /** 不可变性：集合视图不可修改，附件字节每次读取均为副本。 */
    @Test
    public void builderEnforcesImmutability() {
        byte[] payload = new byte[] {1, 2, 3};
        MailMessage message = MailMessage.builder()
                .to("a@qq.test")
                .subject("immutable")
                .attachment("a.txt", payload)
                .templateVariable("k", "v")
                .build();

        try {
            message.getTo().add("b@qq.test");
            fail("收件人列表必须不可变");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
        try {
            message.getCc().add("b@qq.test");
            fail("抄送列表必须不可变");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
        try {
            message.getTemplateVariables().put("k2", "v2");
            fail("模板变量必须不可变");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
        try {
            message.getAttachments().put("b.txt", new byte[] {9});
            fail("附件 Map 结构必须不可变");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }

        message.getAttachments().get("a.txt")[0] = 99;
        assertArrayEquals("每次读取附件都应返回字节副本", payload,
                message.getAttachments().get("a.txt"));
        payload[1] = 88;
        assertArrayEquals("构建期也必须复制字节，外部修改不得影响消息", new byte[] {1, 2, 3},
                message.getAttachments().get("a.txt"));
    }

    /** Builder 拒绝非法消息：收件人与主题在构建期校验。 */
    @Test
    public void builderRejectsInvalidMessages() {
        try {
            MailMessage.builder().subject("no recipient").build();
            fail("收件人为空时必须拒绝");
        } catch (MailException e) {
            assertEquals("email: recipient list must not be empty", e.getMessage());
        }
        try {
            MailMessage.builder().to("a@qq.test").build();
            fail("主题为空白时必须拒绝");
        } catch (MailException e) {
            assertEquals("email: subject must not be blank", e.getMessage());
        }
    }

    private static Set<String> typeNames(String directory) throws IOException {
        Path root = Paths.get(directory);
        assertTrue("源码目录必须存在: " + directory, Files.isDirectory(root));
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static Set<String> methodNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<String>();
        for (Method method : type.getDeclaredMethods()) {
            names.add(method.getName());
        }
        return names;
    }

    private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("未找到方法: " + type.getName() + "#" + name, e);
        }
    }

    private static Method builderMethod() {
        try {
            return MailMessage.class.getDeclaredMethod("builder");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("MailMessage 必须提供 builder()", e);
        }
    }

    private static void assertSameType(Class<?> expected, Class<?> actual) {
        assertEquals(expected.getName(), actual.getName());
    }

    private static void assertNoCheckedExceptions(String where, Method method) {
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            assertFalse("公共方法不得声明受检异常: " + where + "#" + method.getName() + " -> " + exceptionType,
                    Exception.class.isAssignableFrom(exceptionType)
                            && !RuntimeException.class.isAssignableFrom(exceptionType));
        }
    }

    private static void assertAllowedType(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isPrimitive() || void.class.equals(clazz)) {
                return;
            }
            if (clazz.isArray()) {
                assertAllowedType(clazz.getComponentType());
                return;
            }
            String name = clazz.getName();
            assertTrue("公共签名只允许 JDK 与本模块类型: " + name,
                    name.startsWith("java.") || name.startsWith(MODULE_PACKAGE_PREFIX));
            return;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            assertAllowedType(parameterized.getRawType());
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertAllowedType(argument);
            }
            return;
        }
        fail("公共签名出现未预期的泛型形态: " + type);
    }

    /** 值对象字段断言辅助：{@link MailResult} 的字段集合不应包含可变容器。 */
    @Test
    public void mailResultFieldsAreSimpleValues() {
        for (java.lang.reflect.Field field : MailResult.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertFalse("结果对象不应持有集合字段: " + field.getName(),
                    Map.class.isAssignableFrom(field.getType()));
        }
    }
}
