/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.http.HttpException;
import java.util.Arrays;
import org.junit.Test;

/**
 * 重试决策（AC-R-04 / AC-R-06 / AC-R-16 的判定部分）：幂等白名单、尝试次数换算、触发条件。
 */
public class HttpRetryTest {

    /**
     * 幂等白名单只含 GET 与 HEAD。
     */
    @Test
    public void shouldExposeIdempotentMethods() {
        assertEquals(Arrays.asList("GET", "HEAD"), Arrays.asList(
                HttpRetry.IDEMPOTENT_METHODS.toArray(new String[0])));
    }

    /**
     * AC-R-06：非幂等方法不提供重试入口，当场抛出而非静默忽略。
     */
    @Test
    public void shouldRejectNonIdempotentMethod() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HttpRetry.requireIdempotent("POST"));

        assertTrue("消息应含方法名：" + e.getMessage(), e.getMessage().contains("POST"));
    }

    /**
     * 幂等方法（大小写不敏感）通过校验。
     */
    @Test
    public void shouldAcceptIdempotentMethodInAnyCase() {
        HttpRetry.requireIdempotent("GET");
        HttpRetry.requireIdempotent("head");
        HttpRetry.requireIdempotent("Head");
    }

    /**
     * {@code null} 方法名视为非幂等。
     */
    @Test
    public void shouldRejectNullMethod() {
        assertThrows(IllegalArgumentException.class, () -> HttpRetry.requireIdempotent(null));
    }

    /**
     * AC-R-04：未开启重试时只尝试 1 次。
     */
    @Test
    public void shouldAttemptOnceByDefault() {
        assertEquals(1, HttpRetry.maxAttempts("GET", -1, 0));
        assertEquals(2, HttpRetry.maxAttempts("GET", -1, 1));
    }

    /**
     * 逐请求值优先于全局默认。
     */
    @Test
    public void shouldPreferRequestRetryTimes() {
        assertEquals(3, HttpRetry.maxAttempts("GET", 2, 5));
        assertEquals(6, HttpRetry.maxAttempts("HEAD", -1, 5));
    }

    /**
     * AC-R-06：非幂等方法的尝试次数恒为 1，与配置无关。
     */
    @Test
    public void shouldIgnoreRetryTimesForNonIdempotentMethod() {
        assertEquals(1, HttpRetry.maxAttempts("POST", 5, 5));
        assertEquals(1, HttpRetry.maxAttempts("PUT", -1, 3));
        assertEquals(1, HttpRetry.maxAttempts("DELETE", 2, 2));
    }

    /**
     * 只有网络层失败可重试；配置 / 超限 / 解码等确定性失败不重试。
     */
    @Test
    public void shouldRetryOnlyOnIoFailure() {
        assertTrue(HttpRetry.shouldRetryOnFailure(new HttpIoException("请求失败", new java.io.IOException("x"))));
        assertFalse(HttpRetry.shouldRetryOnFailure(new HttpException("响应体超过上限 8 字节（已读 9）")));
        assertFalse(HttpRetry.shouldRetryOnFailure(new HttpException("URL 非法: not-a-url")));
    }

    /**
     * AC-R-16：只有 5xx 状态码可重试。
     */
    @Test
    public void shouldRetryOnlyOnServerErrorStatus() {
        assertFalse(HttpRetry.shouldRetryOnStatus(200));
        assertFalse(HttpRetry.shouldRetryOnStatus(302));
        assertFalse(HttpRetry.shouldRetryOnStatus(404));
        assertFalse(HttpRetry.shouldRetryOnStatus(499));
        assertTrue(HttpRetry.shouldRetryOnStatus(500));
        assertTrue(HttpRetry.shouldRetryOnStatus(503));
        assertTrue(HttpRetry.shouldRetryOnStatus(599));
        assertFalse(HttpRetry.shouldRetryOnStatus(600));
    }
}
