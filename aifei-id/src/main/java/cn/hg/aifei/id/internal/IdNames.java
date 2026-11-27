/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

/**
 * 命名规则的唯一定义点：缓存 / 计数分区名、计数键与锁名的拼装、workerId 取值范围。
 *
 * <p>物理 key 前缀由 {@code aifei-cache} 实例的 {@code namespace} 负责，本类只给逻辑名；分区名
 * {@link #CACHE_NAME} 与段键格式属模块的对外可见命名，改动会波及跨版本与跨实例的一致性，故集中在
 * 一处，其它类不得自拼。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class IdNames {

    /**
     * 计数键所在分区名：保留分区，业务不得读写。
     */
    public static final String CACHE_NAME = "__ID__";

    /**
     * cache 模式下 workerId 计数器的键名。
     */
    public static final String WORKER_COUNTER_KEY = "worker";

    /**
     * 号段计数器键的公共前缀，完整键为 {@code segment.<id.segment.key>}。
     */
    public static final String SEGMENT_KEY_PREFIX = "segment.";

    /**
     * workerId 取值下界（含）。
     */
    public static final int MIN_WORKER_ID = 0;

    /**
     * workerId 取值上界（含），即雪花 10 位字段的最大值。
     */
    public static final int MAX_WORKER_ID = 1023;

    /**
     * workerId 的取值个数，也是 cache 模式取模的模基数。
     */
    public static final int WORKER_ID_MODULUS = MAX_WORKER_ID - MIN_WORKER_ID + 1;

    /**
     * workerId 锁名前缀，完整锁名为 {@code id.worker.<n>}。
     *
     * <p>模块前缀 {@code id.} 的字面量唯一定义点在 {@link IdConfig}，此处引用该常量拼接，
     * 不在本类复制字面量；两者都是编译期常量，被直接内联，不产生类初始化顺序问题。</p>
     */
    private static final String LOCK_NAME_PREFIX = IdConfig.KEY_PREFIX + "worker.";

    /**
     * 禁止创建工具类实例。
     */
    private IdNames() {
    }

    /**
     * 拼装号段计数器的逻辑键。
     *
     * @param name {@code id.segment.key} 的取值，非空
     * @return 形如 {@code segment.<name>} 的逻辑键
     */
    public static String segmentKey(String name) {
        return SEGMENT_KEY_PREFIX + name;
    }

    /**
     * 拼装 workerId 租约的锁名。
     *
     * @param workerId workerId，取值 {@code 0..1023}
     * @return 形如 {@code id.worker.<n>} 的锁名
     */
    public static String workerLockName(int workerId) {
        return LOCK_NAME_PREFIX + workerId;
    }
}
