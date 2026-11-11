/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * DefaultTraceIdGenerator 单元测试：产出格式固定断言（非空、32 位、仅 [0-9a-f]、互不相等）。
 */
public class DefaultTraceIdGeneratorTest {

    /**
     * 产出应满足格式契约：非空、固定 32 位、仅 [0-9a-f] 小写十六进制。
     */
    @Test
    public void generateShouldMeetFormatContract() {
        for (int i = 0; i < 100; i++) {
            String id = DefaultTraceIdGenerator.INSTANCE.generate();
            assertNotNull("TraceId 不得为 null", id);
            assertEquals("TraceId 应固定 32 位，实际为 " + id.length(), 32, id.length());
            assertTrue("TraceId 应仅含 [0-9a-f]，实际为 " + id, id.matches("[0-9a-f]{32}"));
        }
    }

    /**
     * 多次生成互不相等（趋势唯一）。
     */
    @Test
    public void generatedIdsShouldBeUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String id = DefaultTraceIdGenerator.INSTANCE.generate();
            assertTrue("第 " + i + " 次生成出现重复 TraceId：" + id, ids.add(id));
        }
        assertEquals(1000, ids.size());
    }

    /**
     * 单例形态：INSTANCE 为唯一入口；两次调用同一实例行为一致。
     */
    @Test
    public void instanceShouldBeSingleton() {
        assertSame(DefaultTraceIdGenerator.INSTANCE, DefaultTraceIdGenerator.INSTANCE);
        String id1 = DefaultTraceIdGenerator.INSTANCE.generate();
        String id2 = DefaultTraceIdGenerator.INSTANCE.generate();
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }
}
