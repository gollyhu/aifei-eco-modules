/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.random;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * random 两个原语的长度、字符集、唯一性与并发测试（设计 §11.3.2，测试策略 §3.8）。
 *
 * <p>随机值本身不可预测，用例只断言长度、字符集、无填充与抽样唯一性，不固定具体值。</p>
 */
public class RandomUtilTest {

    @Test
    public void randomString_lengthAndCharset() {
        assertEquals("length=0 须返回空串", "", RandomUtil.randomString(0));
        for (int length : new int[]{1, 16, 64}) {
            String actual = RandomUtil.randomString(length);
            assertEquals("length=" + length, length, actual.length());
            for (int i = 0; i < actual.length(); i++) {
                assertTrue("字符集只允许 [A-Za-z0-9]，实际: " + actual.charAt(i),
                        isAsciiAlphaDigit(actual.charAt(i)));
            }
        }
    }

    @Test
    public void randomString_multipleCallsNotEqual() {
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 50; i++) {
            assertTrue("抽样出现重复随机串", seen.add(RandomUtil.randomString(32)));
        }
        assertEquals(50, seen.size());
    }

    @Test
    public void token_base64UrlNoPadding() {
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 20; i++) {
            String token = RandomUtil.token();
            assertEquals("token 须为 43 字符", 43, token.length());
            assertTrue("token 须为 Base64URL 字符集", token.matches("[A-Za-z0-9_-]{43}"));
            assertFalse("token 不得含等号填充", token.contains("="));
            assertTrue("多次调用不得重复", seen.add(token));
        }
    }

    @Test
    public void token_concurrentCallsNoExceptionAndNoDuplicate() throws Exception {
        final int threads = 8;
        final int perThread = 50;
        final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<String>();
        final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<Throwable>();
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(new Runnable() {
                    public void run() {
                        try {
                            start.await();
                            for (int i = 0; i < perThread; i++) {
                                results.add(RandomUtil.randomString(16));
                                results.add(RandomUtil.token());
                            }
                        } catch (Throwable e) {
                            errors.add(e);
                        }
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue("并发调用不应抛异常: " + errors.peek(), errors.isEmpty());
        assertEquals(threads * perThread * 2, results.size());
        assertEquals("并发抽样不应出现重复", results.size(), new HashSet<String>(results).size());
    }

    private static boolean isAsciiAlphaDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
