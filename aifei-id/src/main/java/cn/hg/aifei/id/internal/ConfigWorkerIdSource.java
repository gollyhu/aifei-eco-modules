/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

/**
 * 静态配置来源：workerId 直接取自 {@code id.workerId}，零外部依赖。
 *
 * <p>这是默认来源，也是唯一能在「只引 {@code aifei-id} 一个 jar」时工作的来源。它没有任何共享状态，
 * 因此<b>无法检测重号</b>：两个实例配同一个 workerId 必然重号，风险由使用方承担（多实例场景应改用
 * {@code cache} 或 {@code lock}）。</p>
 *
 * <p>无状态、无线程，天然线程安全。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class ConfigWorkerIdSource implements WorkerIdSource {

    /**
     * 配置中取得的 workerId。
     */
    private final int workerId;

    /**
     * 以配置值构造来源。
     *
     * @param workerId workerId，取值 {@code 0..1023}，由 {@link IdConfig} 保证合法
     */
    public ConfigWorkerIdSource(int workerId) {
        this.workerId = workerId;
    }

    /**
     * 返回配置的 workerId。
     *
     * @return 配置的 workerId
     */
    @Override
    public int acquire() {
        return workerId;
    }

    /**
     * 静态配置没有租约，恒返回 {@code true}。
     *
     * @return 恒为 {@code true}
     */
    @Override
    public boolean healthy() {
        return true;
    }

    /**
     * 空操作：静态配置无需释放任何资源。
     */
    @Override
    public void close() {
        // 静态配置没有资源需要释放
    }
}
