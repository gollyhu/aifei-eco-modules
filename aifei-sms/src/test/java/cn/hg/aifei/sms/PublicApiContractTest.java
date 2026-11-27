/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.hg.aifei.sms.spi.SmsReceipt;
import cn.hg.aifei.sms.spi.SmsReceiptListener;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 公共面契约测试：根包精确 5、spi 精确 2、形态与签名零第三方 / 零框架类型。
 */
public class PublicApiContractTest {

    @Test
    public void rootPackageHasExactlyFivePublicTypes() throws IOException {
        Set<String> names = fileNames(Paths.get("src/main/java/cn/hg/aifei/sms"));
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "SmsClient.java", "SmsKit.java", "SmsMessage.java", "SmsResult.java", "SmsException.java")), names);
    }

    @Test
    public void spiPackageHasExactlyTwoPublicTypes() throws IOException {
        Set<String> names = fileNames(Paths.get("src/main/java/cn/hg/aifei/sms/spi"));
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "SmsReceiptListener.java", "SmsReceipt.java")), names);
    }

    @Test
    public void kitShapeIsFinalPrivateCtorAndStaticOnly() {
        assertTrue(Modifier.isFinal(SmsKit.class.getModifiers()));
        Constructor<?>[] constructors = SmsKit.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        for (Method method : SmsKit.class.getDeclaredMethods()) {
            assertTrue("SmsKit 方法必须为 static: " + method.getName(), Modifier.isStatic(method.getModifiers()));
        }
    }

    @Test
    public void clientIsInterfaceAndExceptionIsRuntime() {
        assertTrue(SmsClient.class.isInterface());
        assertTrue(RuntimeException.class.isAssignableFrom(SmsException.class));
        assertTrue(SmsReceiptListener.class.isInterface());
        assertTrue(Modifier.isFinal(SmsReceipt.class.getModifiers()));
    }

    @Test
    public void publicSignaturesUseOnlyJdkAndModuleTypes() {
        List<Class<?>> types = Arrays.<Class<?>>asList(
                SmsClient.class, SmsKit.class, SmsMessage.class, SmsResult.class, SmsException.class,
                SmsReceiptListener.class, SmsReceipt.class);
        for (Class<?> type : types) {
            for (Method method : type.getDeclaredMethods()) {
                assertJdkOrModule(type, method.getReturnType(), method.getName());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertJdkOrModule(type, parameter, method.getName());
                }
            }
        }
    }

    private static void assertJdkOrModule(Class<?> owner, Class<?> type, String method) {
        String name = type.getName();
        assertTrue("公共签名不得含第三方/框架类型: " + owner.getSimpleName() + "." + method + " -> " + name,
                !name.startsWith("org.dromara.") && !name.startsWith("cn.aifei."));
    }

    private static Set<String> fileNames(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
