/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.log.Log;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.id.IdGenerator;
import cn.hg.aifei.id.internal.CacheAccess;
import cn.hg.aifei.id.internal.CacheSegmentStore;
import cn.hg.aifei.id.internal.CacheWorkerIdSource;
import cn.hg.aifei.id.internal.ConfigWorkerIdSource;
import cn.hg.aifei.id.internal.IdConfig;
import cn.hg.aifei.id.internal.IdDeps;
import cn.hg.aifei.id.internal.IdHolder;
import cn.hg.aifei.id.internal.LocalSegmentStore;
import cn.hg.aifei.id.internal.LockWorkerIdSource;
import cn.hg.aifei.id.internal.SegmentIdGenerator;
import cn.hg.aifei.id.internal.SegmentStore;
import cn.hg.aifei.id.internal.SnowflakeIdGenerator;
import cn.hg.aifei.id.internal.WorkerIdSource;
import java.time.Duration;

/**
 * 把分布式 ID 生成能力装配进 aifei：启动期一次性解析校验配置、装配生成器并注册单例，停机期释放
 * workerId 租约。
 *
 * <p>使用方式：在 {@code config(Plugins plugins)} 里 {@code plugins.add(new IdPlugin(prop))}；装配后
 * 业务用 {@code IdKit.nextId()} 取号，或用 {@code @Inject IdGenerator} 注入（本插件把生成器注册为
 * {@link IdGenerator} 的单例对象）。</p>
 *
 * <p>构造期只持有配置引用，不解析、不校验、不取 workerId、不建线程；全部动作发生在 {@link #start()}。
 * 任一环节失败即回滚（关闭已创建的 workerId 来源、清空持有器），不留半装配。</p>
 *
 * <p>装配顺序：{@code id.worker.source=cache} 必须晚于 {@code CachePlugin}，{@code =lock} 必须晚于
 * {@code CachePlugin} 与 {@code LockPlugin}，否则依赖模块尚未就绪，本插件抛出携带原因的
 * {@code IdException}。不支持命名实例（一个进程只应有一个 workerId）。</p>
 *
 * <p>停机后不从 AOP 容器注销（框架无注销 API，与 {@code CachePlugin} 一致）：已注入的
 * {@link IdGenerator} 引用仍留在容器中，但 {@code IdKit} 会抛 {@code IllegalStateException}。</p>
 *
 * <p>非承诺 API：公共面收敛在根包三个类型，本类位于装配包。</p>
 */
public class IdPlugin implements Plugin {

    /**
     * 构造期保留的配置引用，解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 本次装配创建的 workerId 来源，{@link #stop()} 与失败回滚时据此释放租约；号段策略下为 {@code null}。
     */
    private WorkerIdSource workerSource;

    /**
     * 是否已完成装配；{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private volatile boolean started = false;

    /**
     * 创建插件；只保存配置，不解析、不校验、不取 workerId、不建线程。
     *
     * @param prop 应用配置
     * @throws IllegalArgumentException {@code prop} 为 {@code null}
     */
    public IdPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        this.prop = prop;
    }

    /**
     * 解析配置、按策略装配生成器并注册单例；重复启动不会重复装配，失败不留半装配状态。
     *
     * @throws cn.hg.aifei.id.IdException 配置非法、可选依赖缺失、缓存后端非共享、workerId 全被占用
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        WorkerIdSource source = null;
        try {
            IdConfig config = IdConfig.parse(prop.getProperties());
            IdGenerator generator;
            if (IdConfig.STRATEGY_SEGMENT.equals(config.strategy())) {
                generator = buildSegmentGenerator(config);
            } else {
                source = buildWorkerIdSource(config);
                generator = new SnowflakeIdGenerator(source);
            }
            IdHolder.install(generator);
            AopKit.get().addSingletonObject(IdGenerator.class, generator);
            logStarted(config, source);
            workerSource = source;
            started = true;
        } catch (RuntimeException e) {
            rollback(source);
            throw e;
        } catch (Error e) {
            rollback(source);
            throw e;
        }
    }

    /**
     * 停机收敛：清空持有器、关闭 workerId 来源（中断续期线程并释放租约）；重复或未启动调用无副作用。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        IdHolder.clear();
        WorkerIdSource source = workerSource;
        workerSource = null;
        try {
            if (source != null) {
                source.close();
            }
        } finally {
            started = false;
        }
    }

    // ─────────── 装配分支 ───────────

    /**
     * 装配号段生成器：{@code local} 为显式本地退化，{@code cache} 要求共享后端。
     *
     * @param config 已校验的配置
     * @return 号段生成器
     */
    private static IdGenerator buildSegmentGenerator(IdConfig config) {
        SegmentStore store;
        if (IdConfig.SEGMENT_STORE_LOCAL.equals(config.segmentStore())) {
            store = new LocalSegmentStore();
        } else {
            IdDeps.requireCache();
            store = new CacheSegmentStore(CacheAccess.requireSharedCounter());
        }
        return new SegmentIdGenerator(store, config.segmentKey(), config.segmentStep(),
                config.stateTtlMillis());
    }

    /**
     * 按 {@code id.worker.source} 装配 workerId 来源，缺失依赖立即失败、不降级。
     *
     * @param config 已校验的配置
     * @return workerId 来源
     */
    private static WorkerIdSource buildWorkerIdSource(IdConfig config) {
        String workerSource = config.workerSource();
        if (IdConfig.WORKER_SOURCE_CACHE.equals(workerSource)) {
            IdDeps.requireCache();
            return new CacheWorkerIdSource(CacheAccess.requireSharedCounter(), config.stateTtlMillis());
        }
        if (IdConfig.WORKER_SOURCE_LOCK.equals(workerSource)) {
            IdDeps.requireLock();
            IdDeps.requireCache();
            return LockWorkerIdSource.open(Duration.ofMillis(config.workerLeaseMillis()));
        }
        return new ConfigWorkerIdSource(config.workerId());
    }

    /**
     * 记录一条装配结果日志（策略 + workerId）：运维定位生效策略的唯一出口。
     *
     * <p>日志门面未装配时跳过记录——可观测性让位于可用性，记录失败不应导致装配失败。</p>
     *
     * @param config 已校验的配置
     * @param source workerId 来源；号段策略下为 {@code null}
     */
    private static void logStarted(IdConfig config, WorkerIdSource source) {
        String message;
        if (source == null) {
            message = "aifei-id started, strategy: " + config.strategy()
                    + ", segmentStore: " + config.segmentStore()
                    + ", segmentKey: " + config.segmentKey()
                    + ", step: " + config.segmentStep();
        } else {
            message = "aifei-id started, strategy: " + config.strategy()
                    + ", workerSource: " + config.workerSource()
                    + ", workerId: " + source.acquire();
        }
        try {
            Log.get(IdPlugin.class).info(message);
        } catch (RuntimeException e) {
            // 日志门面未装配，跳过记录
        }
    }

    /**
     * 失败回滚：关闭已创建的 workerId 来源并清空持有器，确保不留半装配。
     *
     * @param source 已创建的 workerId 来源；未创建时为 {@code null}
     */
    private static void rollback(WorkerIdSource source) {
        if (source != null) {
            source.close();
        }
        IdHolder.clear();
    }
}
