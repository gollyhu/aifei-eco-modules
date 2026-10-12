/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.miniapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.weixin.WxApiConfig;
import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.core.api.ApiCaller;
import cn.aifei.weixin.core.cache.MemoryAccessTokenCache;
import cn.aifei.weixin.core.http.FakeHttpKit;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * 小程序 A1 / A2 验证标准：code2Session 解析与错误语义、订阅消息 / 手机号 / 小程序码
 * 的路径与报文、postDownload 的 token 失效重试恰一次。
 */
public class MiniAppApiTest {

    private static final String APP_ID = "wxminiapp123456";

    private MiniAppAccount account;
    private FakeHttpKit http;
    private MemoryAccessTokenCache cache;

    @Before
    public void setUp() {
        account = MiniAppAccount.of(APP_ID, "mini-secret");
        http = new FakeHttpKit();
        cache = new MemoryAccessTokenCache();
        WxApiConfig.set(http, cache);
    }

    @Test
    public void code2SessionParsesOpenidUnionidSessionKey() {
        http.getSupplier = () -> "{\"openid\":\"oABC\",\"unionid\":\"uABC\",\"session_key\":\"sk==\"}";

        Code2Session session = MiniAppApi.code2Session(account, "js-code-1");

        assertEquals("oABC", session.getOpenid());
        assertEquals("uABC", session.getUnionid());
        assertEquals("sk==", session.getSessionKey());
        assertEquals(1, http.getUrls.size());
        String url = http.getUrls.get(0);
        assertTrue(url.contains("sns/jscode2session"));
        assertTrue(url.contains("appid=" + APP_ID));
        assertTrue(url.contains("js_code=js-code-1"));
        assertTrue("sns 接口不注入 access_token", !url.contains("access_token"));
    }

    @Test
    public void code2SessionWithoutUnionidReturnsNull() {
        http.getSupplier = () -> "{\"openid\":\"oABC\",\"session_key\":\"sk==\"}";

        assertNull(MiniAppApi.code2Session(account, "js-code-1").getUnionid());
    }

    @Test
    public void code2SessionBusinessErrorThrows() {
        http.getSupplier = () -> "{\"errcode\":40029,\"errmsg\":\"invalid js_code\"}";

        try {
            MiniAppApi.code2Session(account, "bad-code");
            fail("业务错误应抛 WxApiException");
        } catch (WxApiException e) {
            assertTrue(e.getMessage().contains("40029"));
        }
    }

    @Test
    public void code2SessionMissingSessionKeyThrows() {
        http.getSupplier = () -> "{\"openid\":\"oABC\"}";

        try {
            MiniAppApi.code2Session(account, "js-code-1");
            fail("缺少 session_key 应抛 WxApiException");
        } catch (WxApiException e) {
            assertTrue(e.getMessage().contains("session_key"));
        }
    }

    @Test
    public void subscribeSendPostsWithToken() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("touser", "oABC");

        MiniAppApi.subscribeSend(account, payload);

        assertEquals("取 token 走 GET，POST 列表仅一条", 1, http.postUrls.size());
        assertEquals(ApiCaller.API_BASE + "cgi-bin/message/subscribe/send?access_token=TOKEN-1",
                http.postUrls.get(0));
        assertEquals("{\"touser\":\"oABC\"}", http.postBodies.get(0));
    }

    @Test
    public void getUserPhoneNumberPostsWithCode() {
        MiniAppApi.getUserPhoneNumber(account, "phone-code-1");

        assertEquals(ApiCaller.API_BASE + "wxa/business/getuserphonenumber?access_token=TOKEN-1",
                http.postUrls.get(0));
        assertEquals("{\"code\":\"phone-code-1\"}", http.postBodies.get(0));
    }

    @Test
    public void getWxacodeUnlimitReturnsBinary() {
        byte[] image = {(byte) 0x89, 'P', 'N', 'G'};
        http.postDownloadSupplier = () -> image;

        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", "a=1");
        byte[] result = MiniAppApi.getWxacodeUnlimit(account, payload);

        assertArrayEquals(image, result);
        assertEquals(ApiCaller.API_BASE + "wxa/getwxacodeunlimit?access_token=TOKEN-1",
                http.postDownloadUrls.get(0));
        assertEquals("{\"scene\":\"a=1\"}", http.postDownloadBodies.get(0));
    }

    @Test
    public void postDownloadRetriesExactlyOnceAfterTokenInvalid() {
        AtomicInteger calls = new AtomicInteger();
        http.postDownloadSupplier = () -> calls.incrementAndGet() == 1
                ? "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}".getBytes(StandardCharsets.UTF_8)
                : new byte[]{1, 2, 3};

        byte[] result = MiniAppApi.getWxacodeUnlimit(account, new HashMap<String, Object>());

        assertArrayEquals(new byte[]{1, 2, 3}, result);
        assertEquals("清缓存后应重新取 token", 2, http.tokenRequestCount);
        assertEquals(2, http.postDownloadUrls.size());
    }

    @Test
    public void accountValueObjectGuards() {
        try {
            MiniAppAccount.of(null, "s");
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        try {
            MiniAppAccount.of(APP_ID, " ");
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        assertFalse("toString 不得泄漏 secret",
                MiniAppAccount.of(APP_ID, "mini-secret").toString().contains("mini-secret"));
        assertEquals(MiniAppAccount.of(APP_ID, "s"), MiniAppAccount.of(APP_ID, "s "));
    }
}
