/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * 单测用可编排时钟：按脚本顺序返回毫秒读数，脚本耗尽后一直返回最后一个值。
 *
 * <p>雪花算法把时刻采样收在构造参数 {@link LongSupplier} 上，本类就是注入点。三种编排能力对应三类用例：
 * ① 恒定值——同一毫秒内序列号递增与序列耗尽；② 回退值——时钟回拨；③ 定次重复后推进——「等下一毫秒」
 * 与「等时钟追上」两条等待路径无需真实 {@code sleep} 即可覆盖，测试因此零等待、可重复。</p>
 *
 * <p>非线程安全：断言等待路径的用例要求时钟读数完全由脚本决定，只允许单线程使用。</p>
 */
public final class FakeClock implements LongSupplier {

    /**
     * 尚未消费的脚本值，队首为下一次读数。
     */
    private final Deque<Long> script = new ArrayDeque<>();

    /**
     * 当前读数，脚本耗尽后由它继续返回。
     */
    private long current;

    /**
     * 以起始读数构造时钟。
     *
     * @param startValue 起始读数（毫秒）
     */
    public FakeClock(long startValue) {
        this.current = startValue;
    }

    /**
     * 追加一次读数。
     *
     * @param value 下一次读取的毫秒值
     * @return 本对象，便于链式编排
     */
    public FakeClock thenReturn(long value) {
        script.addLast(Long.valueOf(value));
        return this;
    }

    /**
     * 追加 {@code times} 次相同读数，用于「同一毫秒内连续发号」的用例。
     *
     * @param value 重复的毫秒值
     * @param times 重复次数，必须为正
     * @return 本对象，便于链式编排
     */
    public FakeClock thenReturn(long value, int times) {
        for (int i = 0; i < times; i++) {
            script.addLast(Long.valueOf(value));
        }
        return this;
    }

    /**
     * 取一次时钟读数：优先消费脚本，脚本耗尽后返回最后一个值。
     *
     * @return 毫秒读数
     */
    @Override
    public long getAsLong() {
        Long next = script.pollFirst();
        if (next != null) {
            current = next.longValue();
        }
        return current;
    }
}
