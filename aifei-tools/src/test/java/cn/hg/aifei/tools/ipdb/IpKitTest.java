/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

import cn.hg.aifei.tools.support.IpSampleData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * IP 便捷门面测试（设计 §3.1 / §7.3）。
 *
 * <p>覆盖：三参便捷重载默认走 VECTOR_INDEX（与显式 VECTOR_INDEX 结果一致）、即开即关可重复调用。</p>
 */
public class IpKitTest {

    private static final String V4_PATH = IpSampleData.v4Path();
    private static final String V6_PATH = IpSampleData.v6Path();

    @Test
    public void search_defaultMode_equalsExplicitVectorIndex() {
        IpRegion def = IpKit.search(V4_PATH, V6_PATH, IpSampleData.V4_CN_IP);
        IpRegion explicit = IpKit.search(V4_PATH, V6_PATH, IpSampleData.V4_CN_IP, IpQueryMode.VECTOR_INDEX);
        assertEquals(explicit.getCountry(), def.getCountry());
        assertEquals(explicit.getProvince(), def.getProvince());
        assertEquals(explicit.getCity(), def.getCity());
        assertEquals(explicit.getIsp(), def.getIsp());
        assertEquals(explicit.getIsoCode(), def.getIsoCode());
        assertEquals("中国", def.getCountry());
    }

    @Test
    public void search_repeatable_facadeOpensAndClosesEachTime() {
        for (int i = 0; i < 5; i++) {
            IpRegion r = IpKit.search(V4_PATH, V6_PATH, IpSampleData.V4_FOREIGN_IP);
            assertEquals("United States", r.getCountry());
            assertEquals("US", r.getIsoCode());
        }
    }

    @Test
    public void search_unknown_allEmpty() {
        IpRegion r = IpKit.search(V4_PATH, V6_PATH, IpSampleData.UNKNOWN_IP);
        assertEquals("", r.getCountry());
        assertEquals("", r.getProvince());
        assertEquals("", r.getCity());
        assertEquals("", r.getIsp());
        assertEquals("", r.getIsoCode());
    }
}
