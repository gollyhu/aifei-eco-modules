/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 脱敏工具测试。
 *
 * <p>覆盖：通用 mask、9 条默认规则全部示例、null/空串统一口径、负数 keep 抛异常、
 * maskIp 四类场景（IPv4 / IPv6 压缩与展开等价 / 非法文本退化）。</p>
 */
public class MaskUtilTest {

    @Test
    public void genericMask_keepLeftAndRight() {
        assertEquals("138****8000", MaskUtil.mask("13800138000", 3, 4));
        assertEquals("ab****gh", MaskUtil.mask("abcdefgh", 2, 2));
        assertEquals("abcd", MaskUtil.mask("abcd", 2, 2)); // 保留位合计超过长度
    }

    @Test
    public void genericMask_customMaskChar() {
        assertEquals("138####8000", MaskUtil.mask("13800138000", 3, 4, '#'));
    }

    @Test
    public void allRules_nullToNull_emptyToEmpty() {
        assertNull(MaskUtil.mask(null, 3, 4));
        assertEquals("", MaskUtil.mask("", 3, 4));
        assertNull(MaskUtil.maskPhone(null));
        assertEquals("", MaskUtil.maskPhone(""));
        assertNull(MaskUtil.maskIdCard(null));
        assertEquals("", MaskUtil.maskIdCard(""));
        assertNull(MaskUtil.maskBankCard(null));
        assertEquals("", MaskUtil.maskBankCard(""));
        assertNull(MaskUtil.maskEmail(null));
        assertEquals("", MaskUtil.maskEmail(""));
        assertNull(MaskUtil.maskName(null));
        assertEquals("", MaskUtil.maskName(""));
        assertNull(MaskUtil.maskFixedPhone(null));
        assertEquals("", MaskUtil.maskFixedPhone(""));
        assertNull(MaskUtil.maskPassword(null));
        assertEquals("", MaskUtil.maskPassword(""));
        assertNull(MaskUtil.maskAddress(null));
        assertEquals("", MaskUtil.maskAddress(""));
        assertNull(MaskUtil.maskIp(null));
        assertEquals("", MaskUtil.maskIp(""));
    }

    @Test
    public void negativeKeep_throwsMaskException() {
        assertNegativeKeep(-1, 0);
        assertNegativeKeep(0, -1);
        assertNegativeKeep(-2, -3);
    }

    @Test
    public void defaultRules_allExamples() {
        assertEquals("138****8000", MaskUtil.maskPhone("13800138000"));
        assertEquals("110101********1234", MaskUtil.maskIdCard("110101199001011234"));
        assertEquals("622202*********0123", MaskUtil.maskBankCard("6222021234567890123"));
        assertEquals("z******n@example.com", MaskUtil.maskEmail("zhangsan@example.com"));
        assertEquals("张**", MaskUtil.maskName("张三丰"));
        assertEquals("0571-****6666", MaskUtil.maskFixedPhone("0571-88886666"));
        assertEquals("******", MaskUtil.maskPassword("abc123"));
        assertEquals("浙江省杭州市******", MaskUtil.maskAddress("浙江省杭州市金城坊街2号"));
        assertEquals("192.*.*.*", MaskUtil.maskIp("192.168.2.1"));
    }

    @Test
    public void maskFixedPhone_noDash_degradesToFirst4Last4() {
        assertEquals("0571****6666", MaskUtil.maskFixedPhone("057188886666"));
    }

    @Test
    public void maskPassword_constantLength() {
        assertEquals("******", MaskUtil.maskPassword("a"));
        assertEquals("******", MaskUtil.maskPassword("aVeryLongPasswordValue"));
    }

    @Test
    public void maskAddress_shortValue_allMasked() {
        assertEquals("***", MaskUtil.maskAddress("北京市"));
        assertEquals("******", MaskUtil.maskAddress("123456"));
        assertEquals("123456*", MaskUtil.maskAddress("1234567"));
    }

    @Test
    public void maskIp_ipv4AndIpv6Compressed() {
        assertEquals("192.*.*.*", MaskUtil.maskIp("192.168.2.1"));
        assertEquals("2001:*:*:*:*:*:*:*", MaskUtil.maskIp("2001:db8:85a3::8a2e:370:7334"));
    }

    @Test
    public void maskIp_ipv6CompressedAndExpanded_equivalent() {
        // 权威向量：压缩式与两种展开写法结果须逐字相同（首段 + 7 个 *）
        String compressed = MaskUtil.maskIp("2001:db8:85a3::8a2e:370:7334");
        String expandedShort = MaskUtil.maskIp("2001:db8:85a3:0:0:8a2e:370:7334");
        String expandedPadded = MaskUtil.maskIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals(expandedShort, compressed);
        assertEquals("逐段补全式须与压缩式结果逐字相同", compressed, expandedPadded);
        assertEquals("2001:*:*:*:*:*:*:*", compressed);
        assertEquals("2001:*:*:*:*:*:*:*", expandedPadded);
        assertEquals("掩码数应为 7", 7, countChar(compressed, '*'));
        assertEquals("掩码数应为 7", 7, countChar(expandedPadded, '*'));
    }

    @Test
    public void maskIp_malformedText_degradesWithoutException() {
        assertEquals("a**", MaskUtil.maskIp("abc"));
        // 多 :: / 超段 / 非法字符等畸形 IPv6 不得抛异常
        String malformed = MaskUtil.maskIp("2001::db8::zzzz::1");
        assertTrue("畸形输入须返回字符串而非抛异常", malformed != null && malformed.length() > 0);
        String tooMany = MaskUtil.maskIp("1:2:3:4:5:6:7:8:9:10");
        assertTrue("超段输入须返回字符串", tooMany != null && tooMany.startsWith("1:"));
    }

    @Test
    public void maskIp_authoritativeMalformedVectors_zeroException() {
        // 畸形输入向量：以下畸形输入均须正常返回脱敏串、全程零异常
        String[] vectors = {"::", ":::", ":a::b", "1:2:3:4:5:6:7:8:9::a", "garbage", "a.b.c"};
        for (String v : vectors) {
            String out = MaskUtil.maskIp(v);
            assertTrue("畸形输入[" + v + "]须返回非空串", out != null && out.length() > 0);
        }
        // 无分隔符特征者按宽松退化：保留首字符 + 其余等长 *
        assertEquals("g******", MaskUtil.maskIp("garbage"));
    }

    // ---------------------------------------------------------------- 工具

    private static void assertNegativeKeep(int left, int right) {
        try {
            MaskUtil.mask("abcdef", left, right);
            fail("keepLeft/keepRight 为负应抛 MaskException");
        } catch (MaskException e) {
            assertEquals("mask: keepLeft and keepRight must not be negative", e.getMessage());
        }
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
