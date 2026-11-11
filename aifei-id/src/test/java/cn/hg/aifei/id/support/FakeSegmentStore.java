/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.hg.aifei.id.internal.SegmentStore;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 单测用号段存储：按脚本返回段末端、可注入故障，并记录最近一次的调用参数。
 *
 * <p>号段生成器的三段逻辑需要三种可控输入：① 连续给段末端——验证段内连续与耗尽取段；② 给出不大于上次
 * 末端的末端——验证回退检测；③ 直接抛异常——验证取段失败的包装与根因保留。脚本耗尽即抛
 * {@link IllegalStateException}，让「忘了编排」的用例立刻失败，而不是静默返回含糊值。</p>
 *
 * <p>非线程安全（脚本不可重入）；并发用例请用 {@link cn.hg.aifei.id.internal.LocalSegmentStore}。</p>
 */
public final class FakeSegmentStore implements SegmentStore {

    /**
     * 尚未消费的段末端，队首为下一次返回。
     */
    private final Deque<Long> ends = new ArrayDeque<>();

    /**
     * 注入的故障；非 {@code null} 时先抛它，不消费脚本。
     */
    private RuntimeException failure;

    /**
     * 最近一次调用传入的键名。
     */
    private String lastKey;

    /**
     * 最近一次调用传入的段长。
     */
    private int lastStep;

    /**
     * 最近一次调用传入的有效期。
     */
    private Duration lastTtl;

    /**
     * 调用次数。
     */
    private int calls;

    /**
     * 编排下一次取段返回的末端。
     *
     * @param end 段末端（含）
     * @return 本对象，便于链式编排
     */
    public FakeSegmentStore thenReturn(long end) {
        ends.addLast(Long.valueOf(end));
        return this;
    }

    /**
     * 注入一次取段故障。
     *
     * @param failure 抛出的运行时异常
     * @return 本对象，便于链式编排
     */
    public FakeSegmentStore failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    /**
     * 返回脚本中的下一个段末端，并记录调用参数。
     *
     * @param key 号段键
     * @param step 号段长度
     * @param ttl 计数器有效期
     * @return 段末端（含）
     */
    @Override
    public long nextSegmentMax(String key, int step, Duration ttl) {
        calls++;
        lastKey = key;
        lastStep = step;
        lastTtl = ttl;
        if (failure != null) {
            throw failure;
        }
        Long end = ends.pollFirst();
        if (end == null) {
            throw new IllegalStateException("FakeSegmentStore 的段末端脚本已用尽");
        }
        return end.longValue();
    }

    /**
     * 返回最近一次取段传入的键名。
     *
     * @return 键名；尚未调用时为 {@code null}
     */
    public String lastKey() {
        return lastKey;
    }

    /**
     * 返回最近一次取段传入的段长。
     *
     * @return 段长；尚未调用时为 {@code 0}
     */
    public int lastStep() {
        return lastStep;
    }

    /**
     * 返回最近一次取段传入的有效期。
     *
     * @return 有效期；尚未调用时为 {@code null}
     */
    public Duration lastTtl() {
        return lastTtl;
    }

    /**
     * 返回取段次数。
     *
     * @return 调用次数
     */
    public int calls() {
        return calls;
    }
}
