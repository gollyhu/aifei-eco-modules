/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.cache.TestSupport.FakeTicker;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link CaffeineCounter} 的固定窗口、删除后重启、超限 TTL 不改动状态等回归用例，
 * 以及计数名称集合、{@code clear} 与省略 TTL 重载的覆盖。
 */
public class CaffeineCounterTest {

    private static final String COUNTER = "visits";

    private static CaffeineCounter newCounter(FakeTicker ticker) {
        return new CaffeineCounter(1_000L, ticker, Config.DEFAULT_NAMESPACE);
    }

    /**
     * 首次创建使用传入 TTL，未命中读取返回 null。
     */
    @Test
    public void shouldCreateOnFirstIncrease() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);
        assertNull(counter.get(COUNTER, "k"));

        assertEquals(2L, counter.increase(COUNTER, "k", 2L, Duration.ofMillis(1_000)));
        assertEquals(Long.valueOf(2L), counter.get(COUNTER, "k"));

        ticker.advance(Duration.ofMillis(1_000));
        assertNull(counter.get(COUNTER, "k"));
    }

    /**
     * 回归：固定窗口命中不得延长原截止点。
     */
    @Test
    public void shouldKeepFixedWindowOnRepeatedIncrease() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);

        assertEquals(1L, counter.increase(COUNTER, "k", 1L, Duration.ofMillis(1_000)));
        ticker.advance(Duration.ofMillis(400));

        assertEquals(2L, counter.increase(COUNTER, "k", 1L, Duration.ofMillis(1_000)));

        // 截止点仍是首次创建的 1000ms，而不是「命中时再续 1000ms」
        ticker.advance(Duration.ofMillis(600));
        assertNull("固定窗口到期后必须清零，而不是被命中续期", counter.get(COUNTER, "k"));
    }

    /**
     * 回归：读取与替换之间发生过期时，从 0 重新开始。
     */
    @Test
    public void shouldRestartFromZeroWhenExpiredBetweenReadAndReplace() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);

        assertEquals(5L, counter.increase(COUNTER, "k", 5L, Duration.ofMillis(1_000)));
        ticker.advance(Duration.ofMillis(1_000));

        assertEquals("过期后必须从 0 重新计数", 3L,
                counter.increase(COUNTER, "k", 3L, Duration.ofMillis(1_000)));
        assertEquals(Long.valueOf(3L), counter.get(COUNTER, "k"));
    }

    /**
     * 回归：删除后递增必须从 0 重新开始，不得返回旧值 + step。
     */
    @Test
    public void shouldRestartFromZeroAfterRemove() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);

        assertEquals(5L, counter.increase(COUNTER, "k", 5L, Duration.ofMillis(1_000)));
        counter.remove(COUNTER, "k");
        assertNull(counter.get(COUNTER, "k"));

        assertEquals("删除后递增必须从 0 开始", 3L,
                counter.increase(COUNTER, "k", 3L, Duration.ofMillis(1_000)));
    }

    /**
     * 回归：超限 TTL 被拒绝时，不得改变已有计数的数值与截止点。
     */
    @Test
    public void shouldNotChangeCounterWhenTtlRejected() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);

        assertEquals(5L, counter.increase(COUNTER, "k", 5L, Duration.ofMillis(1_000)));
        ticker.advance(Duration.ofMillis(400));

        Duration tooLarge = Duration.ofSeconds(Integer.MAX_VALUE).plusNanos(1L);
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase(COUNTER, "k", 7L, tooLarge));

        assertEquals("被拒绝的调用不得改变计数值", Long.valueOf(5L), counter.get(COUNTER, "k"));
        ticker.advance(Duration.ofMillis(600));
        assertNull("被拒绝的调用不得延长或缩短原截止点", counter.get(COUNTER, "k"));
    }

    /**
     * {@code increaseAndRefreshTtl} 每次命中都重置剩余 TTL。
     */
    @Test
    public void shouldRefreshTtlOnDemand() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);

        assertEquals(1L, counter.increaseAndRefreshTtl(COUNTER, "k", 1L, Duration.ofMillis(1_000)));
        ticker.advance(Duration.ofMillis(900));
        assertEquals(2L, counter.increaseAndRefreshTtl(COUNTER, "k", 1L, Duration.ofMillis(1_000)));

        ticker.advance(Duration.ofMillis(900));
        assertEquals("刷新 TTL 后不应过期", Long.valueOf(2L), counter.get(COUNTER, "k"));
    }

    /**
     * 递减与递减刷新 TTL。
     */
    @Test
    public void shouldDecrease() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = newCounter(ticker);

        assertEquals(-2L, counter.decrease(COUNTER, "k", 2L, Duration.ofMillis(1_000)));
        assertEquals(-5L, counter.decrease(COUNTER, "k", 3L, Duration.ofMillis(1_000)));
        assertEquals(1L, counter.increase(COUNTER, "k", 6L, Duration.ofMillis(1_000)));
    }

    /**
     * 计数值溢出抛 {@code ArithmeticException}。
     */
    @Test
    public void shouldThrowOnOverflow() {
        CaffeineCounter counter = newCounter(new FakeTicker());
        assertEquals(Long.MAX_VALUE, counter.increase(COUNTER, "k", Long.MAX_VALUE, Duration.ofMillis(1_000)));
        assertThrows(ArithmeticException.class,
                () -> counter.increase(COUNTER, "k", 1L, Duration.ofMillis(1_000)));
    }

    /**
     * 参数校验：step 必须大于 0；空名/空键按未命中处理。
     */
    @Test
    public void shouldValidateArguments() {
        CaffeineCounter counter = newCounter(new FakeTicker());

        assertThrows(IllegalArgumentException.class,
                () -> counter.increase(COUNTER, "k", 0L, Duration.ofMillis(1_000)));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase(" ", "k", 1L, Duration.ofMillis(1_000)));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase(COUNTER, "k", 1L, Duration.ZERO));

        assertNull(counter.get(null, "k"));
        assertNull(counter.get(COUNTER, " "));
        counter.remove(null, "k");
    }

    /**
     * 命名空间与默认 TTL 由构造期写入且只读。
     */
    @Test
    public void shouldExposeNamespaceAndDefaultTtl() {
        CaffeineCounter counter = new CaffeineCounter(1_000L, new FakeTicker(), "ns", 12_345L);
        assertEquals("ns", counter.namespace());
        assertEquals(Duration.ofMillis(12_345L), counter.ttl());
    }

    /**
     * 未显式指定时，实例默认 TTL 取 {@code Config.DEFAULT_TTL}。
     */
    @Test
    public void shouldFallBackToConfigDefaultTtl() {
        assertEquals(Duration.ofMillis(Config.DEFAULT_TTL), new CaffeineCounter().ttl());
        assertEquals(Duration.ofMillis(Config.DEFAULT_TTL), new CaffeineCounter(1_000L).ttl());
        assertEquals(Duration.ofMillis(Config.DEFAULT_TTL),
                new CaffeineCounter(1_000L, new FakeTicker(), "ns").ttl());
    }

    /**
     * 省略 TTL 的重载一律使用实例默认 TTL。
     */
    @Test
    public void shouldUseInstanceDefaultTtlForTtlOmittingOverloads() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = new CaffeineCounter(1_000L, ticker, Config.DEFAULT_NAMESPACE, 1_000L);

        assertEquals(1L, counter.increase(COUNTER, "k", 1L));
        assertEquals(2L, counter.increaseAndRefreshTtl(COUNTER, "k", 1L));
        assertEquals(1L, counter.decrease(COUNTER, "k", 1L));
        assertEquals(0L, counter.decreaseAndRefreshTtl(COUNTER, "k", 1L));

        ticker.advance(Duration.ofMillis(999));
        assertEquals("距末次写入不足默认 TTL，不得过期", Long.valueOf(0L), counter.get(COUNTER, "k"));
        ticker.advance(Duration.ofMillis(1));
        assertNull("省略 TTL 的写路径必须按实例默认 TTL 过期", counter.get(COUNTER, "k"));
    }

    /**
     * 回归：省略 TTL 的固定窗口重载不得逐次延长窗口。
     */
    @Test
    public void shouldKeepFixedWindowForTtlOmittingOverloads() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCounter counter = new CaffeineCounter(1_000L, ticker, Config.DEFAULT_NAMESPACE, 1_000L);

        assertEquals(1L, counter.increase(COUNTER, "k", 1L));
        ticker.advance(Duration.ofMillis(400));
        assertEquals(2L, counter.increase(COUNTER, "k", 1L));

        ticker.advance(Duration.ofMillis(600));
        assertNull("固定窗口到期后必须清零，而不是被命中续期", counter.get(COUNTER, "k"));
    }

    /**
     * {@code counterNames()} 按名称登记：读取不登记，同名不同 key 不产生重复。
     */
    @Test
    public void shouldRecordCounterNamesPerName() {
        CaffeineCounter counter = new CaffeineCounter(1_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE, 1_000L);
        assertTrue("初始集合必须为空", counter.counterNames().isEmpty());

        counter.get("absent", "k");
        assertFalse("读取不得登记计数名称", counter.counterNames().contains("absent"));

        counter.increase("visits", "u1", 1L);
        assertEquals(Collections.singleton("visits"), counter.counterNames());

        // 同一名称的其它 key、以及新名称，分别按「不新增」与「新增」处理
        counter.increase("visits", "u2", 1L);
        counter.decrease("hits", "u1", 1L);
        assertEquals(new LinkedHashSet<>(Arrays.asList("visits", "hits")), counter.counterNames());
    }

    /**
     * {@code remove} 不缩减名称集合；{@code clear} 清空该名称下的全部计数项并精确移除该名称，前缀相近的其它名称不受影响。
     */
    @Test
    public void shouldRemoveCounterNamesOnClearOnly() {
        CaffeineCounter counter = new CaffeineCounter(1_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE, 1_000L);
        counter.increase("visits", "k", 1L);
        counter.increase("visits", "other", 1L);
        counter.increase("visitor", "k", 1L);

        counter.remove("visits", "k");
        assertNull(counter.get("visits", "k"));
        assertEquals("remove 不得改动名称集合",
                new LinkedHashSet<>(Arrays.asList("visits", "visitor")), counter.counterNames());

        counter.clear("visits");
        assertNull(counter.get("visits", "k"));
        assertNull("clear 必须清空该名称下的全部计数项", counter.get("visits", "other"));
        assertEquals("前缀相近的其它计数名称不得被清零", Long.valueOf(1L), counter.get("visitor", "k"));
        assertEquals("clear 精确移除该名称，其它名称保留",
                new LinkedHashSet<>(Collections.singletonList("visitor")), counter.counterNames());
    }

    /**
     * 被 {@code clear} 移除的名称在再次创建时重新登记。
     */
    @Test
    public void shouldReregisterCounterNameAfterClear() {
        CaffeineCounter counter = new CaffeineCounter(1_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE, 1_000L);
        counter.increase("visits", "k", 1L);
        counter.clear("visits");
        assertTrue(counter.counterNames().isEmpty());

        counter.increase("visits", "k", 1L);
        assertEquals("清空后再次创建必须重新登记", Collections.singleton("visits"), counter.counterNames());
    }

    /**
     * {@code counterNames()} 返回副本，外部修改不影响内部集合。
     */
    @Test
    public void shouldReturnCounterNamesCopy() {
        CaffeineCounter counter = new CaffeineCounter(1_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE, 1_000L);
        counter.increase("visits", "k", 1L);

        Set<String> names = counter.counterNames();
        names.add("injected");
        assertFalse("返回的集合必须是副本", counter.counterNames().contains("injected"));
    }

    /**
     * {@code clear} 对空白名称抛异常；清空未知名称是空操作。
     */
    @Test
    public void shouldValidateClearArguments() {
        CaffeineCounter counter = new CaffeineCounter(1_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE, 1_000L);
        assertThrows(IllegalArgumentException.class, () -> counter.clear(" "));
        assertThrows(IllegalArgumentException.class, () -> counter.clear(null));

        counter.clear("unknown");
        assertTrue(counter.counterNames().isEmpty());
    }
}
