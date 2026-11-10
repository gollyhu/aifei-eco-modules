/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.common;

import cn.aifei.util.Prop;

/**
 * demo 侧运行参数字面量：把 {@code job.pool.size} / {@code job.pool.queue} / {@code job.shutdown.timeoutMillis}
 * 三个键的值缓存下来，供只读端点展示「执行层规模」与「停机等待上限」。
 *
 * <p><b>只是展示用的镜像</b>：这些键的解析、校验与生效点都在 {@code JobPlugin.start()}，本类不参与调度，
 * 也不读取模块内部实现。装配早期（{@link DemoAppConfig#config})完成一次初始化，Service 取用时直接读静态字段。</p>
 */
public final class DemoRuntimeConfig {

    /**
     * 配置键：执行层线程数。
     */
    public static final String POOL_SIZE_KEY = "job.pool.size";

    /**
     * 配置键：执行层有界队列容量。
     */
    public static final String QUEUE_KEY = "job.pool.queue";

    /**
     * 配置键：停机等待上限（毫秒）。
     */
    public static final String SHUTDOWN_TIMEOUT_KEY = "job.shutdown.timeoutMillis";

    /**
     * 未配置时的线程数缺省值，与模块 README 的语义对齐（此处固定为 demo 自己的展示值）。
     */
    private static final int DEFAULT_POOL_SIZE = 4;

    /**
     * 未配置时的队列容量缺省值。
     */
    private static final int DEFAULT_QUEUE = 256;

    /**
     * 未配置时的停机等待缺省值。
     */
    private static final long DEFAULT_SHUTDOWN_TIMEOUT = 10000L;

    /**
     * 执行层线程数。
     */
    private static int poolSize = DEFAULT_POOL_SIZE;

    /**
     * 执行层队列容量。
     */
    private static int queueCapacity = DEFAULT_QUEUE;

    /**
     * 停机等待上限（毫秒）。
     */
    private static long shutdownTimeoutMillis = DEFAULT_SHUTDOWN_TIMEOUT;

    /**
     * 禁止创建工具类实例。
     */
    private DemoRuntimeConfig() {
    }

    /**
     * 从配置源读取三个展示用参数；缺失或非法值直接回落缺省值，不影响应用启动。
     *
     * @param prop 配置源，可为 {@code null}（此时全部取缺省值）
     */
    public static void init(Prop prop) {
        if (prop == null) {
            poolSize = DEFAULT_POOL_SIZE;
            queueCapacity = DEFAULT_QUEUE;
            shutdownTimeoutMillis = DEFAULT_SHUTDOWN_TIMEOUT;
            return;
        }
        poolSize = positive(prop.get(POOL_SIZE_KEY), DEFAULT_POOL_SIZE);
        queueCapacity = positive(prop.get(QUEUE_KEY), DEFAULT_QUEUE);
        shutdownTimeoutMillis = nonNegative(prop.get(SHUTDOWN_TIMEOUT_KEY), DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * @return 执行层线程数
     */
    public static int poolSize() {
        return poolSize;
    }

    /**
     * @return 执行层队列容量
     */
    public static int queueCapacity() {
        return queueCapacity;
    }

    /**
     * @return 停机等待上限（毫秒）
     */
    public static long shutdownTimeoutMillis() {
        return shutdownTimeoutMillis;
    }

    /**
     * 解析正整数，非法即回落缺省值。
     *
     * @param raw 配置原文
     * @param defaultValue 缺省值
     * @return 正整数
     */
    private static int positive(String raw, int defaultValue) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            return value >= 1 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析非负整数，非法即回落缺省值。
     *
     * @param raw 配置原文
     * @param defaultValue 缺省值
     * @return 非负整数
     */
    private static long nonNegative(String raw, long defaultValue) {
        try {
            long value = Long.parseLong(raw == null ? "" : raw.trim());
            return value >= 0L ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
