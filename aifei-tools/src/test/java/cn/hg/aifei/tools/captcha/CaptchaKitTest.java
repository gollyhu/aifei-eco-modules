/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha;

import cn.hg.aifei.tools.captcha.internal.CaptchaStore;
import cn.hg.aifei.tools.support.HiddenClassClassLoader;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 验证码门面功能测试（设计 §3.2 / §7.2 / §9）。
 *
 * <p>覆盖：生成/校验、一次性消费、TTL 到期、错误次数超限失效、token 唯一不可反推、
 * 两类题目、参数校验、集群依赖缺失可读异常。</p>
 */
public class CaptchaKitTest {

    @Before
    public void resetConfig() {
        CaptchaKit.setTtlSeconds(120);
        CaptchaKit.setCapacity(10000);
        CaptchaKit.setMaxFailures(5);
    }

    @Test
    public void generateAndVerify_success_thenSecondTimeFails() throws Exception {
        Captcha c = CaptchaKit.generate();
        assertNotNull(c);
        assertNotNull(c.getToken());
        assertTrue(c.getToken().length() > 0);
        assertNotNull(c.getImageBytes());
        assertTrue(c.getImageBytes().length > 0);
        assertTrue("过期时刻应在将来", c.getExpireAt() > System.currentTimeMillis());

        String answer = peekAnswer(c.getToken());
        assertNotNull(answer);
        assertTrue("正确输入应校验通过", CaptchaKit.verify(c.getToken(), answer));
        assertFalse("同一 token 二次校验须失败（一次性消费）", CaptchaKit.verify(c.getToken(), answer));
    }

    @Test
    public void generate_arithmeticType_verifiesWithAnswer() throws Exception {
        Captcha c = CaptchaKit.generate(CaptchaType.ARITHMETIC);
        String answer = peekAnswer(c.getToken());
        assertNotNull(answer);
        assertTrue("算式题答案应为整数文本", answer.matches("\\d+"));
        assertTrue(CaptchaKit.verify(c.getToken(), answer));
    }

    @Test
    public void verify_wrongInput_failsButDoesNotConsume() throws Exception {
        Captcha c = CaptchaKit.generate();
        String answer = peekAnswer(c.getToken());
        assertFalse(CaptchaKit.verify(c.getToken(), "definitely_wrong"));
        assertTrue("错误输入不应消费 token", CaptchaKit.verify(c.getToken(), answer));
    }

    @Test
    public void verify_ttlExpired_fails() throws Exception {
        CaptchaKit.setTtlSeconds(1);
        Captcha c = CaptchaKit.generate();
        String answer = peekAnswer(c.getToken());
        Thread.sleep(1200L);
        assertFalse("TTL 到期后校验须失败", CaptchaKit.verify(c.getToken(), answer));
    }

    @Test
    public void verify_exceedMaxFailures_failsEvenWithCorrectAnswer() throws Exception {
        CaptchaKit.setMaxFailures(2);
        Captcha c = CaptchaKit.generate();
        String answer = peekAnswer(c.getToken());
        assertFalse(CaptchaKit.verify(c.getToken(), "wrong1"));
        assertFalse(CaptchaKit.verify(c.getToken(), "wrong2"));
        assertFalse("超过错误次数上限后即便答案正确也须失败",
                CaptchaKit.verify(c.getToken(), answer));
    }

    @Test
    public void verify_nullInputs_returnFalse() {
        assertFalse(CaptchaKit.verify(null, "x"));
        assertFalse(CaptchaKit.verify("token", null));
    }

    @Test
    public void token_uniqueAndNotReversibleFromImage() {
        int n = 100;
        Set<String> tokens = new HashSet<String>();
        for (int i = 0; i < n; i++) {
            Captcha c = CaptchaKit.generate();
            String token = c.getToken();
            assertTrue("token 应是 Base64 URL 无填充形式", token.matches("[A-Za-z0-9_-]+"));
            assertEquals("token 长度应稳定（32 字节 Base64 无填充）", 43, token.length());
            assertTrue("token 不得可从图片字节文本反推",
                    new String(c.getImageBytes(), java.nio.charset.StandardCharsets.ISO_8859_1)
                            .indexOf(token) < 0);
            tokens.add(token);
        }
        assertEquals("token 必须唯一", n, tokens.size());
    }

    @Test
    public void generate_nullType_throws() {
        try {
            CaptchaKit.generate(null);
            fail("type 为 null 应抛 CaptchaException");
        } catch (CaptchaException e) {
            assertEquals("captcha: generate argument type must not be null", e.getMessage());
        }
    }

    @Test
    public void config_nonPositive_throws() {
        assertConfigRejected("ttlSeconds", 0, new Runnable() {
            public void run() { CaptchaKit.setTtlSeconds(0); }
        });
        assertConfigRejected("capacity", -1, new Runnable() {
            public void run() { CaptchaKit.setCapacity(-1); }
        });
        assertConfigRejected("maxFailures", 0, new Runnable() {
            public void run() { CaptchaKit.setMaxFailures(0); }
        });
    }

    @Test
    public void clusterDependencyMissing_readableException() throws Exception {
        ClassLoader cl = new HiddenClassClassLoader("cn.hg.aifei.cache.");
        Class<?> deps = Class.forName("cn.hg.aifei.tools.captcha.internal.CaptchaDeps", true, cl);
        Method require = deps.getMethod("requireCache");
        try {
            require.invoke(null);
            fail("缓存内核被隐藏时应抛异常");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertEquals("cn.hg.aifei.tools.captcha.CaptchaException", cause.getClass().getName());
            assertEquals("captcha: io.github.gollyhu:aifei-cache:1.1.0 is required for cluster store",
                    cause.getMessage());
            assertNotNull(cause.getCause());
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 通过反射读取 store 中的标准答案（consume 只读不删），用于驱动正确的校验分支。 */
    private static String peekAnswer(String token) throws Exception {
        Field field = CaptchaKit.class.getDeclaredField("store");
        field.setAccessible(true);
        Object store = field.get(null);
        Method consume = store.getClass().getMethod("consume", String.class);
        return (String) consume.invoke(store, token);
    }

    private static void assertConfigRejected(String name, int value, Runnable r) {
        try {
            r.run();
            fail(name + " 非正数应抛 CaptchaException");
        } catch (CaptchaException e) {
            assertEquals("captcha: " + name + " must be positive, but was: " + value, e.getMessage());
        }
    }
}
