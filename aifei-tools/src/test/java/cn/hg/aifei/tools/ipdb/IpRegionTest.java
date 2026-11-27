/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

import cn.hg.aifei.tools.ipdb.internal.RegionParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * IP 五段值对象与 region 解析测试。
 *
 * <p>覆盖：五段解析、"0"/"Reserved" 归空串、未命中全空、of 工厂 null 归一与空白串原样保留。</p>
 */
public class IpRegionTest {

    @Test
    public void parse_fiveSegments_allFieldsMapped() {
        IpRegion r = RegionParser.parse("中国|广东省|深圳市|电信|CN");
        assertEquals("中国", r.getCountry());
        assertEquals("广东省", r.getProvince());
        assertEquals("深圳市", r.getCity());
        assertEquals("电信", r.getIsp());
        assertEquals("CN", r.getIsoCode());
    }

    @Test
    public void parse_zeroAndReserved_normalizedToEmpty() {
        IpRegion r = RegionParser.parse("Reserved|Reserved|Reserved|0|0");
        assertEquals("", r.getCountry());
        assertEquals("", r.getProvince());
        assertEquals("", r.getCity());
        assertEquals("", r.getIsp());
        assertEquals("", r.getIsoCode());
    }

    @Test
    public void parse_partialZero_mixedFields() {
        IpRegion r = RegionParser.parse("中国|0|0|电信|CN");
        assertEquals("中国", r.getCountry());
        assertEquals("", r.getProvince());
        assertEquals("", r.getCity());
        assertEquals("电信", r.getIsp());
        assertEquals("CN", r.getIsoCode());
    }

    @Test
    public void parse_nullAndEmpty_allFieldsEmpty() {
        for (String raw : new String[]{null, ""}) {
            IpRegion r = RegionParser.parse(raw);
            assertNotNull("未命中须返回非 null 对象", r);
            assertEquals("", r.getCountry());
            assertEquals("", r.getProvince());
            assertEquals("", r.getCity());
            assertEquals("", r.getIsp());
            assertEquals("", r.getIsoCode());
        }
    }

    @Test
    public void parse_shortSegments_missingFieldsEmpty() {
        IpRegion r = RegionParser.parse("中国|广东省");
        assertEquals("中国", r.getCountry());
        assertEquals("广东省", r.getProvince());
        assertEquals("", r.getCity());
        assertEquals("", r.getIsp());
        assertEquals("", r.getIsoCode());
    }

    @Test
    public void of_allNull_allFieldsEmpty() {
        IpRegion r = IpRegion.of(null, null, null, null, null);
        assertEquals("", r.getCountry());
        assertEquals("", r.getProvince());
        assertEquals("", r.getCity());
        assertEquals("", r.getIsp());
        assertEquals("", r.getIsoCode());
    }

    @Test
    public void of_blankStrings_preservedAsIs() {
        IpRegion r = IpRegion.of(" ", "", "\t", "  ", " ");
        assertEquals(" ", r.getCountry());
        assertEquals("", r.getProvince());
        assertEquals("\t", r.getCity());
        assertEquals("  ", r.getIsp());
        assertEquals(" ", r.getIsoCode());
    }

    @Test
    public void of_normalValues_allFieldsPreserved() {
        IpRegion r = IpRegion.of("中国", "北京市", "海淀区", "联通", "CN");
        assertEquals("中国", r.getCountry());
        assertEquals("北京市", r.getProvince());
        assertEquals("海淀区", r.getCity());
        assertEquals("联通", r.getIsp());
        assertEquals("CN", r.getIsoCode());
    }
}
