/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.aifei.websocket.internal.Backoff;
import org.junit.Test;

/**
 * {@link Backoff} 纯函数的上下界、抖动、默认参数序列与溢出保护（设计 §6.3）。
 *
 * <p>随机数由调用方以 {@code r} 注入，因此全部断言可确定性复现，不依赖时钟与随机源。</p>
 */
public class BackoffTest {

    /**
     * 抖动为 {@code 0} 时结果精确等于未加抖动的封顶基数，便于钉死指数基数与封顶规则。
     */
    @Test
    public void shouldBeExactWithoutJitter() {
        assertEquals(500L, Backoff.delayMillis(1, 500L, 30000L, 0.0, 0.5));
        assertEquals(1000L, Backoff.delayMillis(2, 500L, 30000L, 0.0, 0.5));
        assertEquals(2000L, Backoff.delayMillis(3, 500L, 30000L, 0.0, 0.5));
        assertEquals(4000L, Backoff.delayMillis(4, 500L, 30000L, 0.0, 0.5));
        assertEquals(8000L, Backoff.delayMillis(5, 500L, 30000L, 0.0, 0.5));
    }

    /**
     * 默认参数下 {@code r} 取中值时，延迟序列约为设计给出的 {@code 500 / 1000 / 2000 / 4000 / 8000}。
     */
    @Test
    public void shouldMatchDefaultSequenceAtMidRandom() {
        long initial = 500L;
        long max = 30000L;
        double jitter = 0.2;
        double r = 0.5;

        assertEquals(500L, Backoff.delayMillis(1, initial, max, jitter, r));
        assertEquals(1000L, Backoff.delayMillis(2, initial, max, jitter, r));
        assertEquals(2000L, Backoff.delayMillis(3, initial, max, jitter, r));
        assertEquals(4000L, Backoff.delayMillis(4, initial, max, jitter, r));
        assertEquals(8000L, Backoff.delayMillis(5, initial, max, jitter, r));
    }

    /**
     * 结果始终落在 {@code [capped × (1 - jitter), capped × (1 + jitter)]} 内，且 {@code r} 取边界时命中边界。
     */
    @Test
    public void shouldRespectJitterBounds() {
        long initial = 1000L;
        long max = 16000L;
        double jitter = 0.25;

        // r = 0 时因子取下界，结果等于下界四舍五入
        assertEquals(Math.round(1000L * (1.0 - jitter)), Backoff.delayMillis(1, initial, max, jitter, 0.0));

        // r 趋近 1 时因子趋近上界；逐点断言不越界
        for (int attempt = 1; attempt <= 6; attempt++) {
            long capped = Math.min(initial * (1L << (attempt - 1)), max);
            long lower = Math.round(capped * (1.0 - jitter));
            long upper = Math.round(capped * (1.0 + jitter));

            assertEquals(lower, Backoff.delayMillis(attempt, initial, max, jitter, 0.0));
            long atZero = Backoff.delayMillis(attempt, initial, max, jitter, 0.0);
            long nearOne = Backoff.delayMillis(attempt, initial, max, jitter, 0.999999);
            assertTrue("延迟不得低于下界", atZero >= lower);
            assertTrue("延迟不得高于上界", nearOne <= upper);
            assertTrue("延迟必须为正", nearOne > 0L);
        }
    }

    /**
     * 抖动为零时基数被 {@code max} 封顶：指数放大到不小于上限后一律返回上限。
     *
     * <p>{@code initial = 500}、{@code max = 30000} 时，第 6 次基数为 {@code 16000}（未封顶），
     * 第 7 次基数为 {@code 32000}（封顶到 {@code 30000}）。</p>
     */
    @Test
    public void shouldCapAtMax() {
        assertEquals(16000L, Backoff.delayMillis(6, 500L, 30000L, 0.0, 0.5));
        assertEquals(30000L, Backoff.delayMillis(7, 500L, 30000L, 0.0, 0.5));
        assertEquals(30000L, Backoff.delayMillis(64, 500L, 30000L, 0.0, 0.5));
    }

    /**
     * 首次延迟已不小于上限时直接取上限，避免无谓放大。
     */
    @Test
    public void shouldCapWhenInitialAlreadyReachesMax() {
        assertEquals(1000L, Backoff.delayMillis(1, 1000L, 1000L, 0.0, 0.5));
        assertEquals(1000L, Backoff.delayMillis(3, 1000L, 1000L, 0.0, 0.5));
        assertEquals(1000L, Backoff.delayMillis(1000, 2000L, 1000L, 0.0, 0.5));
    }

    /**
     * 溢出保护：{@code attempt} 取极大值时 {@code 2^(attempt-1)} 会溢出，实现必须逐次封顶并直接返回上限。
     *
     * <p>若实现直接做 {@code long} 位移后比较，此处会得到负数或非上限值；本用例同时保证该路径不会长时间循环。</p>
     */
    @Test
    public void shouldNotOverflowOnHugeAttempt() {
        long max = 30000L;

        assertEquals(max, Backoff.delayMillis(Integer.MAX_VALUE, 500L, max, 0.0, 0.5));
        assertEquals(max, Backoff.delayMillis(1000000, 500L, max, 0.0, 0.5));
        assertTrue(Backoff.delayMillis(Integer.MAX_VALUE, 500L, max, 0.2, 0.5) > 0L);
    }

    /**
     * {@code attempt = 1} 时基数即首次延迟，不进行任何放大。
     */
    @Test
    public void shouldUseInitialDelayOnFirstAttempt() {
        assertEquals(137L, Backoff.delayMillis(1, 137L, 30000L, 0.0, 0.0));
    }
}
