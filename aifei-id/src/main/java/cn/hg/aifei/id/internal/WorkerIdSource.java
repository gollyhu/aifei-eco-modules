/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

/**
 * workerId 来源契约：把「取一个 10 位节点编号」与「该编号是否仍然有效」两件事独立出来，雪花算法只依赖
 * 本接口。
 *
 * <p>三种实现的取得方式与代价不同（静态配置零依赖、共享计数器取模、锁租约抢占），但对外语义一致：
 * {@link #acquire()} 返回 {@code 0..1023} 的编号；{@link #healthy()} 声明该编号是否仍然可用；
 * {@link #close()} 释放资源。切换来源只改 {@code id.worker.source} 配置，雪花算法无需改动。</p>
 *
 * <p>实现必须线程安全：{@link #healthy()} 会被发号热路径频繁读取，{@link #acquire()} 可能被日志等
 * 诊断路径再次调用，两次调用必须返回同一个编号（不得每次取号都消耗新的编号）。</p>
 *
 * <p>非承诺 API。</p>
 */
public interface WorkerIdSource {

    /**
     * 返回本来源取得的 workerId。
     *
     * <p>幂等：同一实例多次调用返回同一编号，不重复占用编号资源。</p>
     *
     * @return workerId，取值 {@code 0..1023}
     */
    int acquire();

    /**
     * 声明当前 workerId 是否仍然可安全使用。
     *
     * @return 可用时 {@code true}；租约已丢失时 {@code false}，此时继续发号必然重号
     */
    boolean healthy();

    /**
     * 释放该来源持有的资源（如 workerId 租约与续期线程）；重复调用无副作用。
     *
     * <p>实现不得向外抛异常：停机路径不应掩盖主流程的异常。</p>
     */
    void close();
}
