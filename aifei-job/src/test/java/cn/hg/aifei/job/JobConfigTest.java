/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobConfig;
import cn.hg.aifei.job.support.TestLogs;
import java.util.Arrays;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 配置域用例（4 个键）：缺失必填、空值、含空项、越界与默认值。
 *
 * <p>启动期配置失败一律是 {@link JobException}（<b>不是</b> {@code IllegalArgumentException} /
 * {@code IllegalStateException}），且消息必须同时含<b>键名</b>与<b>原值</b>，便于定位写错的配置项。</p>
 */
public class JobConfigTest {

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ 键名常量

    /**
     * 4 个配置键名字面量（对外契约，不得改动）。
     */
    @Test
    public void configKeysAreContractual() {
        assertEquals("job.scan.packages", JobConfig.SCAN_PACKAGES_KEY);
        assertEquals("job.pool.size", JobConfig.POOL_SIZE_KEY);
        assertEquals("job.pool.queue", JobConfig.POOL_QUEUE_KEY);
        assertEquals("job.shutdown.timeoutMillis", JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY);
    }

    // ------------------------------------------------------------------ 默认值

    /**
     * 只给必填键时，其余三项取默认值：线程数 = 处理器核数、队列 = 256、停机等待 = 10000ms。
     */
    @Test
    public void optionalKeysFallBackToDefaults() {
        JobConfig config = JobConfig.parse(props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job"));
        assertEquals("pool.size 默认值应为处理器核数",
                Runtime.getRuntime().availableProcessors(), config.poolSize());
        assertEquals("pool.queue 默认值应为 256", 256, config.poolQueue());
        assertEquals("shutdown.timeoutMillis 默认值应为 10000", 10000L, config.shutdownTimeoutMillis());
    }

    /**
     * 可选键写空白视为缺省，同样取默认值。
     */
    @Test
    public void blankOptionalValuesFallBackToDefaults() {
        Properties props = props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job");
        props.setProperty(JobConfig.POOL_SIZE_KEY, "   ");
        props.setProperty(JobConfig.POOL_QUEUE_KEY, "");
        props.setProperty(JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, " ");
        JobConfig config = JobConfig.parse(props);
        assertEquals("空白值应按缺省处理", Runtime.getRuntime().availableProcessors(), config.poolSize());
        assertEquals("空白值应按缺省处理", 256, config.poolQueue());
        assertEquals("空白值应按缺省处理", 10000L, config.shutdownTimeoutMillis());
    }

    /**
     * 扫描包按声明顺序保留且逐项去空白。
     */
    @Test
    public void scanPackagesAreTrimmedAndOrdered() {
        JobConfig config = JobConfig.parse(props(JobConfig.SCAN_PACKAGES_KEY, " cn.acme.a ,cn.acme.b "));
        assertEquals("扫描包应逐项去空白并保持声明顺序",
                Arrays.asList("cn.acme.a", "cn.acme.b"), config.scanPackages());
    }

    /**
     * 显式配置生效。
     */
    @Test
    public void explicitValuesWin() {
        Properties props = props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job");
        props.setProperty(JobConfig.POOL_SIZE_KEY, "4");
        props.setProperty(JobConfig.POOL_QUEUE_KEY, "64");
        props.setProperty(JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, "0");
        JobConfig config = JobConfig.parse(props);
        assertEquals(4, config.poolSize());
        assertEquals(64, config.poolQueue());
        assertEquals("shutdown.timeoutMillis 允许为 0（不等待）", 0L, config.shutdownTimeoutMillis());
    }

    // ------------------------------------------------------------------ 缺失 / 空值 / 含空项

    /**
     * 缺失必填键：配置集合为 {@code null}（等价空集合）。
     */
    @Test
    public void missingScanPackagesFails() {
        assertConfigInvalid(null, JobConfig.SCAN_PACKAGES_KEY, null);
    }

    /**
     * 缺失必填键：配置集合非 {@code null} 但没有该键。
     */
    @Test
    public void absentScanPackagesKeyFails() {
        assertConfigInvalid(new Properties(), JobConfig.SCAN_PACKAGES_KEY, null);
    }

    /**
     * 必填键为空串或纯空白。
     */
    @Test
    public void blankScanPackagesFails() {
        assertConfigInvalid(props(JobConfig.SCAN_PACKAGES_KEY, ""), JobConfig.SCAN_PACKAGES_KEY, null);
        assertConfigInvalid(props(JobConfig.SCAN_PACKAGES_KEY, "   "), JobConfig.SCAN_PACKAGES_KEY, null);
    }

    /**
     * 必填键含空项（连续逗号 / 逗号空格 / 尾随逗号），消息须带原值。
     */
    @Test
    public void scanPackagesWithBlankItemFails() {
        assertConfigInvalid(props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a,,cn.acme.b"),
                JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a,,cn.acme.b");
        assertConfigInvalid(props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a, ,cn.acme.b"),
                JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a, ,cn.acme.b");
        assertConfigInvalid(props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a,"),
                JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a,");
    }

    // ------------------------------------------------------------------ 越界与非整数

    /**
     * {@code job.pool.size} 越界（{@code >= 1}）。
     */
    @Test
    public void poolSizeOutOfRangeFails() {
        assertConfigInvalid(withPoolSize("0"), JobConfig.POOL_SIZE_KEY, "0");
        assertConfigInvalid(withPoolSize("-3"), JobConfig.POOL_SIZE_KEY, "-3");
    }

    /**
     * {@code job.pool.queue} 越界（{@code >= 1}）。
     */
    @Test
    public void poolQueueOutOfRangeFails() {
        assertConfigInvalid(withPoolQueue("0"), JobConfig.POOL_QUEUE_KEY, "0");
        assertConfigInvalid(withPoolQueue("-1"), JobConfig.POOL_QUEUE_KEY, "-1");
    }

    /**
     * {@code job.shutdown.timeoutMillis} 越界（{@code >= 0}）。
     */
    @Test
    public void shutdownTimeoutOutOfRangeFails() {
        assertConfigInvalid(withShutdownTimeout("-1"), JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, "-1");
    }

    /**
     * 数值键非整数：同样是 {@code JobException}，消息含键名与原值，底层解析异常保留为 cause。
     */
    @Test
    public void nonNumericValuesFailWithCause() {
        assertNonNumeric(withPoolSize("abc"), JobConfig.POOL_SIZE_KEY, "abc");
        assertNonNumeric(withPoolQueue("1.5"), JobConfig.POOL_QUEUE_KEY, "1.5");
        assertNonNumeric(withShutdownTimeout("10s"), JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, "10s");
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * 构造只含一个键值对的可变配置集合。
     *
     * @param key 键名
     * @param value 值
     * @return 配置集合
     */
    private static Properties props(String key, String value) {
        Properties props = new Properties();
        props.setProperty(key, value);
        return props;
    }

    /**
     * @param poolSize 线程数取值
     * @return 只配了必填键与线程数的配置集合
     */
    private static Properties withPoolSize(String poolSize) {
        Properties props = props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job");
        props.setProperty(JobConfig.POOL_SIZE_KEY, poolSize);
        return props;
    }

    /**
     * @param poolQueue 队列容量取值
     * @return 只配了必填键与队列容量的配置集合
     */
    private static Properties withPoolQueue(String poolQueue) {
        Properties props = props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job");
        props.setProperty(JobConfig.POOL_QUEUE_KEY, poolQueue);
        return props;
    }

    /**
     * @param timeout 停机等待上限取值
     * @return 只配了必填键与停机等待上限的配置集合
     */
    private static Properties withShutdownTimeout(String timeout) {
        Properties props = props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job");
        props.setProperty(JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, timeout);
        return props;
    }

    /**
     * 断言配置解析失败为 {@code JobException}，消息含键名（与原值，如有）。
     *
     * @param props 配置集合，{@code null} 表示空配置
     * @param key 期望出现在消息中的键名
     * @param rawValue 期望出现在消息中的原值片段；{@code null} 表示不断言原值
     */
    private static void assertConfigInvalid(Properties props, String key, String rawValue) {
        try {
            JobConfig.parse(props);
            fail("配置非法时应抛 JobException，键：" + key);
        } catch (JobException e) {
            assertTrue("消息应含键名 " + key + "，实际：" + e.getMessage(), e.getMessage().contains(key));
            if (rawValue != null) {
                assertTrue("消息应含原值 '" + rawValue + "'，实际：" + e.getMessage(),
                        e.getMessage().contains(rawValue));
            }
        }
    }

    /**
     * 断言「数值无法解析」的失败：{@code JobException} + 键名 + 原值 + 非空 cause。
     *
     * @param props 配置集合
     * @param key 期望出现在消息中的键名
     * @param rawValue 期望出现在消息中的原值
     */
    private static void assertNonNumeric(Properties props, String key, String rawValue) {
        try {
            JobConfig.parse(props);
            fail("非整数取值应抛 JobException，键：" + key);
        } catch (JobException e) {
            assertTrue("消息应含键名 " + key + "，实际：" + e.getMessage(), e.getMessage().contains(key));
            assertTrue("消息应含原值 " + rawValue + "，实际：" + e.getMessage(),
                    e.getMessage().contains(rawValue));
            assertNotNull("数值解析失败应保留底层异常为 cause", e.getCause());
        }
    }
}
