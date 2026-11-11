/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.hg.aifei.id.IdGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单测用生成器：从指定值开始逐个递增，产出完全可预测。
 *
 * <p>{@link cn.hg.aifei.id.IdKit} 只是一层转发门面，它的用例需要「知道下一个值是什么」，因此不能用真实
 * 雪花（值依赖时钟）。本桩也用于验证 {@code long → 十进制字符串} 转换不丢精度：起始值取
 * {@code 2^53 + 1} 这类超出 JS 安全整数范围的 19 位值。</p>
 *
 * <p>线程安全：内部用 {@link AtomicLong}。</p>
 */
public final class FakeIdGenerator implements IdGenerator {

    /**
     * 下一个待发值。
     */
    private final AtomicLong next;

    /**
     * 以起始值构造生成器。
     *
     * @param firstValue 首次 {@link #nextId()} 返回的值
     */
    public FakeIdGenerator(long firstValue) {
        this.next = new AtomicLong(firstValue);
    }

    /**
     * 返回当前值并把游标推进一格。
     *
     * @return 下一个值
     */
    @Override
    public long nextId() {
        return next.getAndIncrement();
    }
}
