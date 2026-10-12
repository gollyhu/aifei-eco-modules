/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * 哨兵编解码的唯一定义点：缓存 {@code null} 落成的必须是 {@link NullValue#INSTANCE} 这一个实例。
 *
 * <p>哨兵类型迁入本包后为包级私有，根包的测试无法再引用它 —— 因此「严格同一性」与「全限定名
 * 稳定」这两条断言只能落在本包内，用来补上根包测试改用「非 null + 跨调用同一实例」等价断言后
 * 留下的覆盖缺口。</p>
 *
 * <p>钉死这两点有价值的原因是：哨兵的全限定名会经 Fury 写进存储字节流，属数据格式。一旦有人
 * 另外造一个哨兵对象、或改动该类型的包名，往返用例仍会通过，但存量数据的可读性契约已被破坏。</p>
 */
public class SentinelDefinitionPointTest {

    /**
     * 编解码只认 {@link NullValue#INSTANCE}，且该实例可透明还原为 {@code null}。
     */
    @Test
    public void shouldStoreNullAsTheSingleSentinelInstance() {
        assertSame(NullValue.INSTANCE, AbstractCache.toStoredValue(null, true));
        assertNull(AbstractCache.fromStoredValue(NullValue.INSTANCE));
    }

    /**
     * 哨兵的全限定名属数据格式，迁包一次即定版，此后不得再变。
     */
    @Test
    public void shouldKeepSentinelFullyQualifiedNameStable() {
        assertEquals("cn.hg.aifei.cache.internal.NullValue", NullValue.class.getName());
    }
}
