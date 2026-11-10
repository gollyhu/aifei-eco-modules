/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * 配置域与默认值的唯一定义点：键名常量、默认值常量、{@link #parse(Properties)} 的解析与校验都在本类，
 * 其它类不得硬编码 {@code "job.pool.size"} 之类的字面量。
 *
 * <p>本类是 final 值对象：解析成功即固定，解析全部失败路径都以 {@link cn.hg.aifei.job.JobException}
 * 抛出并携带键名与原值。</p>
 */
public final class JobConfig {

    /**
     * 声明扫描包的配置键，必填，逗号分隔。
     */
    public static final String SCAN_PACKAGES_KEY = "job.scan.packages";

    /**
     * 执行层线程数配置键，可选，{@code >= 1}。
     */
    public static final String POOL_SIZE_KEY = "job.pool.size";

    /**
     * 执行层有界队列容量配置键，可选，{@code >= 1}。
     */
    public static final String POOL_QUEUE_KEY = "job.pool.queue";

    /**
     * 停机等待上限配置键，可选，{@code >= 0}（{@code 0} 表示不等待，直接中断）。
     */
    public static final String SHUTDOWN_TIMEOUT_MILLIS_KEY = "job.shutdown.timeoutMillis";

    /**
     * 队列容量默认值；只适合开发 / 低负载环境，正式环境须按「任务数 × 单批堆积量」评估后取更大值。
     */
    public static final int DEFAULT_POOL_QUEUE = 256;

    /**
     * 停机等待上限默认值，单位毫秒。
     */
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 10000L;

    /**
     * 扫描包，保持声明顺序且每项已去空白。
     */
    private final List<String> scanPackages;

    /**
     * 执行层线程数。
     */
    private final int poolSize;

    /**
     * 执行层有界队列容量。
     */
    private final int poolQueue;

    /**
     * 停机等待上限，单位毫秒。
     */
    private final long shutdownTimeoutMillis;

    /**
     * 禁止外部构造，只能经 {@link #parse(Properties)} 创建。
     *
     * @param scanPackages 扫描包
     * @param poolSize 执行层线程数
     * @param poolQueue 执行层有界队列容量
     * @param shutdownTimeoutMillis 停机等待上限，单位毫秒
     */
    private JobConfig(List<String> scanPackages, int poolSize, int poolQueue, long shutdownTimeoutMillis) {
        this.scanPackages = Collections.unmodifiableList(new ArrayList<>(scanPackages));
        this.poolSize = poolSize;
        this.poolQueue = poolQueue;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    }

    /**
     * 解析并校验全部配置项。
     *
     * @param properties 全部配置项；传 {@code null} 视为空配置集合（随后因缺少必填键而失败）
     * @return 校验通过的配置值对象
     * @throws cn.hg.aifei.job.JobException 必填键缺失、扫描包含空项、取值越界或数值无法解析
     */
    public static JobConfig parse(Properties properties) {
        Properties props = properties == null ? new Properties() : properties;
        List<String> scanPackages = parseScanPackages(props);
        int poolSize = parseInt(props, POOL_SIZE_KEY, defaultPoolSize());
        if (poolSize < 1) {
            throw JobErrors.configInvalid(POOL_SIZE_KEY, poolSize, ">= 1");
        }
        int poolQueue = parseInt(props, POOL_QUEUE_KEY, DEFAULT_POOL_QUEUE);
        if (poolQueue < 1) {
            throw JobErrors.configInvalid(POOL_QUEUE_KEY, poolQueue, ">= 1");
        }
        long shutdownTimeoutMillis = parseLong(props, SHUTDOWN_TIMEOUT_MILLIS_KEY,
                DEFAULT_SHUTDOWN_TIMEOUT_MILLIS);
        if (shutdownTimeoutMillis < 0L) {
            throw JobErrors.configInvalid(SHUTDOWN_TIMEOUT_MILLIS_KEY, shutdownTimeoutMillis, ">= 0");
        }
        return new JobConfig(scanPackages, poolSize, poolQueue, shutdownTimeoutMillis);
    }

    /**
     * @return 不可变的扫描包列表（按声明顺序，每项已去空白）
     */
    public List<String> scanPackages() {
        return scanPackages;
    }

    /**
     * @return 执行层线程数
     */
    public int poolSize() {
        return poolSize;
    }

    /**
     * @return 执行层有界队列容量
     */
    public int poolQueue() {
        return poolQueue;
    }

    /**
     * @return 停机等待上限，单位毫秒
     */
    public long shutdownTimeoutMillis() {
        return shutdownTimeoutMillis;
    }

    /**
     * 解析扫描包：逗号分隔、逐项去空白，缺键、空值或含空项即失败。
     *
     * @param props 全部配置项
     * @return 扫描包列表
     */
    private static List<String> parseScanPackages(Properties props) {
        String raw = props.getProperty(SCAN_PACKAGES_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            throw JobErrors.scanPackagesEmpty(SCAN_PACKAGES_KEY, raw);
        }
        List<String> scanPackages = new ArrayList<>();
        for (String item : raw.split(",", -1)) {
            String packageName = item.trim();
            if (packageName.isEmpty()) {
                throw JobErrors.scanPackagesEmpty(SCAN_PACKAGES_KEY, raw);
            }
            scanPackages.add(packageName);
        }
        return scanPackages;
    }

    /**
     * 解析整型配置项；缺键或空白取默认值。
     *
     * @param props 全部配置项
     * @param key 配置键名
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static int parseInt(Properties props, String key, int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        String value = raw.trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw JobErrors.configInvalid(key, value, "an integer", e);
        }
    }

    /**
     * 解析长整型配置项；缺键或空白取默认值。
     *
     * @param props 全部配置项
     * @param key 配置键名
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static long parseLong(Properties props, String key, long defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        String value = raw.trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw JobErrors.configInvalid(key, value, "a long integer", e);
        }
    }

    /**
     * 执行层线程数的默认值：处理器核数。
     *
     * @return 处理器核数，恒 {@code >= 1}
     */
    private static int defaultPoolSize() {
        return Runtime.getRuntime().availableProcessors();
    }
}
