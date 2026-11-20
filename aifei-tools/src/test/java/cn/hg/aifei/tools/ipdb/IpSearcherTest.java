/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

import cn.hg.aifei.tools.support.HiddenClassClassLoader;
import cn.hg.aifei.tools.support.IpSampleData;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IP 查询会话功能测试（设计 §3.1 / §7.2 / §7.3 / §9）。
 *
 * <p>覆盖：三模式一致、v4/v6 命中、境内中文/境外英文/保留段/未知段语义、close 幂等、
 * 关闭后查询、非法参数、xdb 不可读、依赖缺失可读异常、并发无串扰。样本全离线内嵌。</p>
 */
public class IpSearcherTest {

    private static final String V4 = IpSampleData.V4_CN_IP;
    private static final String V4_PATH = IpSampleDataHolder.v4();
    private static final String V6_PATH = IpSampleDataHolder.v6();

    /** 延迟取路径，避免在静态初始化阶段写 target/。 */
    private static final class IpSampleDataHolder {
        private static String v4() {
            return IpSampleData.v4Path();
        }
        private static String v6() {
            return IpSampleData.v6Path();
        }
    }

    @Test
    public void threeModes_sameRegion() {
        IpRegion fileOnly = searchInMode(IpQueryMode.FILE_ONLY, V4);
        IpRegion vector = searchInMode(IpQueryMode.VECTOR_INDEX, V4);
        IpRegion content = searchInMode(IpQueryMode.CONTENT, V4);
        assertRegionEquals(vector, fileOnly);
        assertRegionEquals(vector, content);
    }

    @Test
    public void open_defaultMode_equalsVectorIndex() {
        IpSearcher def = IpSearcher.open(V4_PATH, V6_PATH);
        try {
            assertRegionEquals(searchInMode(IpQueryMode.VECTOR_INDEX, V4), def.search(V4));
        } finally {
            def.close();
        }
    }

    @Test
    public void search_v4ChineseAndForeign() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH, IpQueryMode.VECTOR_INDEX);
        try {
            IpRegion cn = s.search(IpSampleData.V4_CN_IP);
            assertEquals("中国", cn.getCountry());
            assertEquals("北京市", cn.getProvince());
            assertEquals("北京市", cn.getCity());
            assertEquals("联通", cn.getIsp());
            assertEquals("CN", cn.getIsoCode());

            IpRegion us = s.search(IpSampleData.V4_FOREIGN_IP);
            assertEquals("United States", us.getCountry());
            assertEquals("California", us.getProvince());
            assertEquals("Los Angeles", us.getCity());
            assertEquals("Comcast", us.getIsp());
            assertEquals("US", us.getIsoCode());
        } finally {
            s.close();
        }
    }

    @Test
    public void search_reservedAndZero_normalized() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH, IpQueryMode.VECTOR_INDEX);
        try {
            assertAllEmpty(s.search(IpSampleData.V4_RESERVED_IP));
            IpRegion zero = s.search(IpSampleData.V4_ZERO_IP);
            assertEquals("中国", zero.getCountry());
            assertEquals("", zero.getProvince());
            assertEquals("", zero.getCity());
            assertEquals("电信", zero.getIsp());
            assertEquals("CN", zero.getIsoCode());
        } finally {
            s.close();
        }
    }

    @Test
    public void search_v6ChineseAndReserved() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH, IpQueryMode.VECTOR_INDEX);
        try {
            IpRegion cn = s.search(IpSampleData.V6_CN_IP);
            assertEquals("中国", cn.getCountry());
            assertEquals("广东省", cn.getProvince());
            assertEquals("深圳市", cn.getCity());
            assertEquals("电信", cn.getIsp());
            assertEquals("CN", cn.getIsoCode());
            assertAllEmpty(s.search(IpSampleData.V6_RESERVED_IP));
        } finally {
            s.close();
        }
    }

    @Test
    public void search_unknown_allEmpty() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH, IpQueryMode.VECTOR_INDEX);
        try {
            assertAllEmpty(s.search(IpSampleData.UNKNOWN_IP));
        } finally {
            s.close();
        }
    }

    @Test
    public void close_idempotent() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH);
        s.close();
        s.close();
        s.close();
    }

    @Test
    public void search_afterClose_throwsReadable() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH);
        s.close();
        try {
            s.search(V4);
            fail("关闭后查询应抛 IpException");
        } catch (IpException e) {
            assertEquals("ip: session is closed, create a new IpSearcher", e.getMessage());
        }
    }

    @Test
    public void open_bothPathsNull_throws() {
        try {
            IpSearcher.open(null, null);
            fail("双路径为 null 应抛 IpException");
        } catch (IpException e) {
            assertEquals("ip: at least one of v4/v6 xdb path must be provided", e.getMessage());
        }
    }

    @Test
    public void open_nullMode_throws() {
        try {
            IpSearcher.open(V4_PATH, V6_PATH, null);
            fail("mode 为 null 应抛 IpException");
        } catch (IpException e) {
            assertEquals("ip: open argument mode must not be null", e.getMessage());
        }
    }

    @Test
    public void search_nullIp_throws() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH);
        try {
            s.search(null);
            fail("ip 为 null 应抛 IpException");
        } catch (IpException e) {
            assertEquals("ip: search argument ip must not be null", e.getMessage());
        } finally {
            s.close();
        }
    }

    @Test
    public void search_invalidIp_throwsWithCause() {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH);
        try {
            s.search("999.999.999.999");
            fail("非法 IP 应抛 IpException");
        } catch (IpException e) {
            assertTrue("消息须可读且含非法 IP", e.getMessage().contains("ip: invalid ip address"));
            assertNotNull("须保留底层根因", e.getCause());
        } finally {
            s.close();
        }
    }

    @Test
    public void open_unreadableXdb_throws() {
        try {
            IpSearcher.open("target/__no_such_xdb__.xdb", null);
            fail("xdb 不可读应抛 IpException");
        } catch (IpException e) {
            assertTrue("消息须含不可读路径", e.getMessage().contains("ip: xdb file not readable"));
            assertNotNull(e.getCause());
        }
    }

    @Test
    public void dependencyMissing_readableException() throws Exception {
        ClassLoader cl = new HiddenClassClassLoader("org.lionsoul.");
        Class<?> deps = Class.forName("cn.hg.aifei.tools.ipdb.internal.IpDeps", true, cl);
        Method require = deps.getMethod("requireIp2Region");
        try {
            require.invoke(null);
            fail("kernel 依赖被隐藏时应抛异常");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertEquals("cn.hg.aifei.tools.ipdb.IpException", cause.getClass().getName());
            assertEquals("ip: org.lionsoul:ip2region:3.3.7 is required, add it to your dependencies",
                    cause.getMessage());
            assertNotNull(cause.getCause());
        }
    }

    @Test
    public void concurrent_noCrosstalk() throws Exception {
        final IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH, IpQueryMode.VECTOR_INDEX);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final String[] ips = {
                    IpSampleData.V4_CN_IP, IpSampleData.V4_FOREIGN_IP,
                    IpSampleData.V6_CN_IP, IpSampleData.V4_ZERO_IP
            };
            List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 80; i++) {
                final int idx = i;
                results.add(pool.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        IpRegion r = s.search(ips[idx % ips.length]);
                        return verifyRegion(ips[idx % ips.length], r);
                    }
                }));
            }
            for (Future<Boolean> f : results) {
                assertTrue("并发查询结果串扰", f.get().booleanValue());
            }
        } finally {
            pool.shutdownNow();
            s.close();
        }
    }

    // ---------------------------------------------------------------- 工具

    private static IpRegion searchInMode(IpQueryMode mode, String ip) {
        IpSearcher s = IpSearcher.open(V4_PATH, V6_PATH, mode);
        try {
            return s.search(ip);
        } finally {
            s.close();
        }
    }

    private static boolean verifyRegion(String ip, IpRegion r) {
        if (IpSampleData.V4_CN_IP.equals(ip)) {
            return "中国".equals(r.getCountry()) && "北京市".equals(r.getProvince());
        }
        if (IpSampleData.V4_FOREIGN_IP.equals(ip)) {
            return "United States".equals(r.getCountry()) && "California".equals(r.getProvince());
        }
        if (IpSampleData.V6_CN_IP.equals(ip)) {
            return "中国".equals(r.getCountry()) && "广东省".equals(r.getProvince());
        }
        return "中国".equals(r.getCountry()) && "".equals(r.getProvince()) && "电信".equals(r.getIsp());
    }

    private static void assertRegionEquals(IpRegion expected, IpRegion actual) {
        assertEquals(expected.getCountry(), actual.getCountry());
        assertEquals(expected.getProvince(), actual.getProvince());
        assertEquals(expected.getCity(), actual.getCity());
        assertEquals(expected.getIsp(), actual.getIsp());
        assertEquals(expected.getIsoCode(), actual.getIsoCode());
    }

    private static void assertAllEmpty(IpRegion r) {
        assertNotNull("结果不得为 null", r);
        assertEquals("", r.getCountry());
        assertEquals("", r.getProvince());
        assertEquals("", r.getCity());
        assertEquals("", r.getIsp());
        assertEquals("", r.getIsoCode());
    }
}
