/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.hg.aifei.sms.internal.sms4j.SmsDeps;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 依赖缺失测试（AC-06 / R-04）：隔离类加载器模拟 sms4j 缺失，断言可读异常而非裸 NoClassDefFoundError。
 */
public class DependencyMissingTest {

    @Test
    public void missingSms4jThrowsReadableSmsException() throws Exception {
        Method method = SmsDeps.class.getDeclaredMethod("requireSms4j", ClassLoader.class);
        method.setAccessible(true);
        ClassLoader isolated = new ClassLoader(null) {
        };
        try {
            method.invoke(null, isolated);
            fail("sms4j 缺失应抛异常");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue("应抛模块异常", cause instanceof SmsException);
            assertTrue("不得裸抛 NoClassDefFoundError", !(cause instanceof NoClassDefFoundError));
            assertEquals("sms: org.dromara.sms4j:sms4j-core:3.3.6 is required, add it to your dependencies",
                    cause.getMessage());
        }
    }

    @Test
    public void availableSms4jPassesProbe() {
        SmsDeps.requireSms4j();
    }
}
