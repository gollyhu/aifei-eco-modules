/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * 账号值对象 fail-fast 校验与数据源（S1 验证标准 ⑥、S7）
 */
public class WeixinAccountTest {

    static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopq";

    @Test
    public void failFastOnInvalidArguments() {
        expectInvalid(() -> WeixinAccount.of(null, "secret", "token", null), "appId 为空");
        expectInvalid(() -> WeixinAccount.of("  ", "secret", "token", null), "appId 为空白");
        expectInvalid(() -> WeixinAccount.of("appId", "secret", null, null), "token 为空");
        expectInvalid(() -> WeixinAccount.of("appId", "secret", "token", "short-aes-key"), "aesKey 非 43 字符");
    }

    @Test
    public void safeModeDependsOnAesKey() {
        assertFalse(WeixinAccount.of("appId", "secret", "token", null).isSafeMode());
        assertFalse(WeixinAccount.of("appId", "secret", "token", "").isSafeMode());
        assertTrue(WeixinAccount.of("appId", "secret", "token", AES_KEY).isSafeMode());
        assertNull(WeixinAccount.of("appId", "secret", "token", null).getAesKey());
    }

    @Test
    public void trimsAppIdAndToken() {
        WeixinAccount account = WeixinAccount.of(" appId ", "secret", " token ", AES_KEY);
        assertEquals("appId", account.getAppId());
        assertEquals("token", account.getToken());
        assertEquals("secret", account.getSecret());
    }

    @Test
    public void providerReturnsNullForUnknownAppId() {
        WeixinAccount account = WeixinAccount.of("appId", "secret", "token", null);
        WeixinAccountProvider provider = WeixinAccountProvider.of(account);

        assertSame(account, provider.get("appId"));
        assertNull(provider.get("unknown"));
        assertNull(provider.get(null));
    }

    private static void expectInvalid(Runnable runnable, String message) {
        try {
            runnable.run();
            fail("应抛 IllegalArgumentException：" + message);
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }
}
