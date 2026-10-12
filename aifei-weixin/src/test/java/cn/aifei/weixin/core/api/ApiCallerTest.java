/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.weixin.WxApiConfig;
import cn.aifei.weixin.common.ApiResult;
import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.core.cache.MemoryAccessTokenCache;
import cn.aifei.weixin.core.http.FakeHttpKit;
import cn.aifei.weixin.spi.WeixinAccount;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * Api 客户端（S3 验证标准 ①②③④⑤⑥⑦⑧⑨）
 */
public class ApiCallerTest {

    private static final String APP_ID = "wx1234567890abcdef";

    private final WeixinAccount account = WeixinAccount.of(APP_ID, "secret", "token", null);

    private FakeHttpKit http;
    private MemoryAccessTokenCache cache;

    @Before
    public void setUp() {
        http = new FakeHttpKit();
        cache = new MemoryAccessTokenCache();
        WxApiConfig.set(http, cache);
    }

    @Test
    public void injectsAccessTokenIntoQuery() {
        ApiResult result = MenuApi.get(account);

        assertTrue(result.isSucceed());
        assertEquals(2, http.getUrls.size());
        assertTrue(http.getUrls.get(0).contains("cgi-bin/token?grant_type=client_credential&appid=" + APP_ID));
        assertEquals(ApiCaller.API_BASE + "cgi-bin/menu/get?access_token=TOKEN-1", http.getUrls.get(1));
    }

    @Test
    public void businessErrorReturnsResultInsteadOfThrowing() {
        http.getSupplier = () -> "{\"errcode\":40003,\"errmsg\":\"invalid openid\"}";

        ApiResult result = UserApi.getUserInfo(account, "openid", "zh_CN");

        assertFalse(result.isSucceed());
        assertEquals(40003, result.getErrorCode());
        assertEquals("invalid openid", result.getErrorMsg());
        assertTrue(http.getUrls.get(1).contains("openid=openid"));
        assertTrue(http.getUrls.get(1).contains("lang=zh_CN"));
    }

    @Test
    public void retriesExactlyOnceAfterTokenInvalid() {
        AtomicInteger apiCalls = new AtomicInteger();
        http.getSupplier = () -> apiCalls.incrementAndGet() == 1
                ? "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}"
                : "{\"errcode\":0,\"errmsg\":\"ok\"}";

        ApiResult result = MenuApi.get(account);

        assertTrue(result.isSucceed());
        assertEquals(2, apiCalls.get());
        assertEquals("清缓存后应重新取 token", 2, http.tokenRequestCount);
    }

    @Test
    public void keepsResultWhenRetryStillFails() {
        AtomicInteger apiCalls = new AtomicInteger();
        http.getSupplier = () -> {
            apiCalls.incrementAndGet();
            return "{\"errcode\":42001,\"errmsg\":\"expired\"}";
        };

        ApiResult result = MenuApi.get(account);

        assertFalse(result.isSucceed());
        assertEquals(42001, result.getErrorCode());
        assertEquals("只允许重试一次", 2, apiCalls.get());
    }

    @Test
    public void cacheHitAvoidsSecondTokenRequest() {
        MenuApi.get(account);
        MenuApi.get(account);

        assertEquals(1, http.tokenRequestCount);
    }

    @Test
    public void expiredTokenIsRefetched() {
        cache.set(APP_ID, "OLD-TOKEN", System.currentTimeMillis() - 1);

        ApiResult result = MenuApi.delete(account);

        assertTrue(result.isSucceed());
        assertEquals(1, http.tokenRequestCount);
        assertTrue(http.getUrls.get(1).contains("access_token=TOKEN-1"));
    }

    @Test
    public void concurrentFirstCallFetchesTokenExactlyOnce() throws Exception {
        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    assertEquals("TOKEN-1", ApiCaller.accessToken(account));
                    return null;
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            assertEquals("并发未命中必须单飞（只发一次 token 请求）", 1, http.tokenRequestCount);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void multipartSendsFieldsAndFile() throws Exception {
        File file = File.createTempFile("aifei-weixin-upload", ".png");
        file.deleteOnExit();
        Files.write(file.toPath(), "binary".getBytes(StandardCharsets.UTF_8));

        ApiResult result = MediaApi.uploadTemp(account, "image", file);

        assertTrue(result.isSucceed());
        assertEquals(1, http.multipartUrls.size());
        assertEquals(ApiCaller.API_BASE + "cgi-bin/media/upload?access_token=TOKEN-1", http.multipartUrls.get(0));
        assertEquals("image", http.lastFields.get("type"));
        assertSame(file, http.lastFiles.get("media"));
    }

    @Test
    public void postSendsJsonBody() {
        JSONObject menu = new JSONObject();
        menu.put("button", JSON.parseArray("[{\"type\":\"click\",\"name\":\"菜单\"}]"));

        assertTrue(MenuApi.create(account, menu).isSucceed());
        assertTrue(http.postUrls.get(0).startsWith(ApiCaller.API_BASE + "cgi-bin/menu/create?access_token=TOKEN-1"));
        assertTrue(http.postBodies.get(0).contains("\"name\":\"菜单\""));
    }

    @Test
    public void downloadReturnsBytes() {
        http.downloadResponse = new byte[]{1, 2, 3};

        assertEquals(3, MediaApi.getTemp(account, "media-id").length);
        assertEquals(ApiCaller.API_BASE + "cgi-bin/media/get?access_token=TOKEN-1&media_id=media-id",
                http.downloadUrls.get(0));
    }

    @Test
    public void downloadRetriesExactlyOnceAfterTokenInvalid() {
        AtomicInteger downloads = new AtomicInteger();
        http.downloadSupplier = () -> downloads.incrementAndGet() == 1
                ? "{\"errcode\":42001,\"errmsg\":\"expired\"}".getBytes(StandardCharsets.UTF_8)
                : new byte[]{7, 8, 9};

        byte[] bytes = MediaApi.getTemp(account, "media-id");

        assertArrayEquals(new byte[]{7, 8, 9}, bytes);
        assertEquals("只允许重试一次", 2, downloads.get());
        assertEquals("清缓存后应重新取 token", 2, http.tokenRequestCount);
        assertTrue(http.downloadUrls.get(1).contains("access_token=TOKEN-1"));
    }

    @Test
    public void downloadBinaryContentDoesNotTriggerRetry() {
        AtomicInteger downloads = new AtomicInteger();
        http.downloadSupplier = () -> {
            downloads.incrementAndGet();
            return new byte[]{1, 2, 3};
        };

        byte[] bytes = MediaApi.getTemp(account, "media-id");

        assertArrayEquals(new byte[]{1, 2, 3}, bytes);
        assertEquals("二进制素材内容不得误判为错误 JSON 触发重试", 1, downloads.get());
    }

    @Test
    public void jsapiTicketReusesSameCacheInstance() {
        Map<String, Object> data = new HashMap<>();
        data.put("errcode", 0);
        data.put("ticket", "TICKET-1");
        data.put("expires_in", 7200);
        http.getSupplier = () -> JSON.toJSONString(data);

        assertTrue(JsTicketApi.getTicket(account).isSucceed());
        assertEquals("TICKET-1", JsTicketApi.getTicket(account).getData().getString("ticket"));

        assertEquals(2, http.getUrls.size());
        assertEquals(1, http.tokenRequestCount);
        assertEquals("TICKET-1", cache.get(JsTicketApi.TICKET_CACHE_PREFIX + APP_ID));
    }

    @Test
    public void oauth2DoesNotInjectAccessToken() {
        http.getSupplier = () -> "{\"access_token\":\"OAUTH-TOKEN\",\"openid\":\"o1\"}";

        ApiResult result = OAuth2Api.getAccessToken(account, "CODE");

        assertTrue(result.isSucceed());
        String url = http.getUrls.get(0);
        assertTrue(url.startsWith(ApiCaller.API_BASE + "sns/oauth2/access_token?"));
        assertTrue(url.contains("appid=" + APP_ID));
        assertTrue(url.contains("code=CODE"));
        assertFalse(url.contains("access_token=token"));
        assertEquals(0, http.tokenRequestCount);
        assertNull(cache.get(APP_ID));
    }

    @Test
    public void nonJsonResponseThrowsWxApiException() {
        http.getSupplier = () -> "<html>502 Bad Gateway</html>";

        try {
            MenuApi.get(account);
            fail("非 JSON 响应必须抛 WxApiException");
        } catch (WxApiException expected) {
            assertTrue(expected.getMessage().contains("非 JSON"));
        }
    }

    @Test
    public void transportFailurePropagates() {
        http.getSupplier = () -> {
            throw new WxApiException("微信接口请求失败");
        };

        try {
            MenuApi.get(account);
            fail("传输层失败必须抛 WxApiException");
        } catch (WxApiException expected) {
            assertEquals("微信接口请求失败", expected.getMessage());
        }
    }

    @Test
    public void uninitializedConfigFailsFast() {
        WxApiConfig.set(null, null);

        try {
            MenuApi.get(account);
            fail("WxApiConfig 未装配必须抛 WxApiException");
        } catch (WxApiException expected) {
            assertTrue(expected.getMessage().contains("WxApiConfig 未初始化"));
        }
    }

    @Test
    public void memoryCacheHonorsExpiryAndRemove() {
        cache.set("k", "v", System.currentTimeMillis() + 10000);
        assertEquals("v", cache.get("k"));

        cache.set("k2", "v2", System.currentTimeMillis() - 1);
        assertNull(cache.get("k2"));

        cache.remove("k");
        assertNull(cache.get("k"));
    }

    @Test
    public void buildUrlEncodesParams() {
        String url = ApiCaller.buildUrl("cgi-bin/x", "T", Collections.singletonMap("k", "a b&c"));
        assertEquals(ApiCaller.API_BASE + "cgi-bin/x?access_token=T&k=a+b%26c", url);
    }

    @Test
    public void qrcodeAndJsTicketSignatureHelpers() {
        assertEquals("https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=gQH%2B",
                QrcodeApi.showQrcodeUrl("gQH+"));
        assertEquals(cn.aifei.weixin.core.encrypt.Sha1Kit.sha1("jsapi_ticket=TT&noncestr=NN&timestamp=1&url=http://a"),
                JsTicketApi.sign("TT", "NN", 1L, "http://a"));
    }

    @Test
    public void showQrcodeAndShortUrlDoNotNeedAccountToken() {
        assertTrue(QrcodeApi.showQrcodeUrl("t").contains("showqrcode?ticket=t"));
        assertEquals(0, http.tokenRequestCount);
    }

    @Test
    public void appIdSecretOverloadSharesTokenCacheWithAccount() {
        assertTrue(MenuApi.get(account).isSucceed());
        ApiResult byAppIdSecret = ApiCaller.get(APP_ID, "secret", "cgi-bin/menu/get", null);

        assertTrue(byAppIdSecret.isSucceed());
        assertEquals("appId/secret 重载与 WeixinAccount 委托共用 appId 缓存键，只取一次 token",
                1, http.tokenRequestCount);
        assertEquals(ApiCaller.API_BASE + "cgi-bin/menu/get?access_token=TOKEN-1", http.getUrls.get(2));
    }
}
