/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Cache;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * 同 JVM 回源单飞：并发未命中同一缓存项时，最多只让一个线程调用 {@code loader}，其余线程复用其装载结果。
 *
 * <p>非承诺 API，仅供实现使用。只覆盖单个 JVM：多实例部署时每个实例各自单飞一次，跨实例不收敛。</p>
 *
 * <p>使用约束：{@code loader} 内不得对<b>同一个缓存实例</b>的其它 key 再走带 {@code loader} 的
 * {@code get}。登记与装载之间没有全局顺序，交叉等待会成环。</p>
 */
public final class SingleFlight {

    /**
     * 等待被中断的标记：仅在内部用于区分「无结果」，不会写入任何存储。
     */
    private static final Object INTERRUPTED = new Object();

    /**
     * 在途装载登记表，key 为物理 key；条目在一次装载结束后移除。
     */
    private static final ConcurrentHashMap<String, Cell> IN_FLIGHT = new ConcurrentHashMap<>();

    private SingleFlight() {
    }

    /**
     * 未命中分支的装载：登记后双检，只有仍未命中才回源；其余线程等待并复用胜者的结果。
     *
     * @param cache 目标缓存实现
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param ttlMillis 装载值的有效期，单位毫秒
     * @param loader 加载器
     * @param <T> 期望的值类型
     * @return 缓存值或加载值
     */
    @SuppressWarnings("unchecked")
    public static <T> T load(Cache cache, String cacheName, String key, long ttlMillis, Supplier<T> loader) {
        String flightKey = AbstractCache.physicalKey(cache.namespace(), cacheName, key);
        Cell cell = new Cell();
        Cell winner = IN_FLIGHT.putIfAbsent(flightKey, cell);

        if (winner != null) {
            Object shared = winner.awaitShared();
            if (shared != INTERRUPTED) {
                // 胜者发布的结果直接复用，null 与真值一视同仁。
                return (T) shared;
            }
            // 等待被中断：放弃复用，退化为自行回源，不登记、不影响其它线程。
            return loadAndWrite(cache, cacheName, key, ttlMillis, loader);
        }

        try {
            // 双检：登记与首次未命中之间存在窗口，先到者可能已装好真实值或否定结果。
            T existing = cache.get(cacheName, key);
            if (existing != null || cache.exists(cacheName, key)) {
                cell.publish(existing);
                return existing;
            }
            T loaded = loadAndWrite(cache, cacheName, key, ttlMillis, loader);
            cell.publish(loaded);
            return loaded;
        } catch (RuntimeException | Error e) {
            cell.fail(e);
            throw e;
        } finally {
            IN_FLIGHT.remove(flightKey, cell);
        }
    }

    /**
     * 【实现专用】「回源 + 回写」的唯一定义点；{@code loader} 抛出时不写缓存，异常原样传播。
     */
    private static <T> T loadAndWrite(Cache cache, String cacheName, String key, long ttlMillis,
            Supplier<T> loader) {
        T loaded = loader.get();
        cache.put(cacheName, key, loaded, Duration.ofMillis(ttlMillis));
        return loaded;
    }

    /**
     * 一次装载的共享结果；跟随者等待其完成并直接取用，不再读缓存、不再回源。
     */
    private static final class Cell {

        private final CountDownLatch done = new CountDownLatch(1);

        private volatile Object result;

        private volatile Throwable error;

        void publish(Object value) {
            this.result = value;
            done.countDown();
        }

        void fail(Throwable cause) {
            this.error = cause;
            done.countDown();
        }

        /**
         * 阻塞等待胜者发布结果；等待被中断时返回 {@link #INTERRUPTED}。
         */
        Object awaitShared() {
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return INTERRUPTED;
            }
            if (error != null) {
                rethrow(error);
            }
            return result;
        }
    }

    /**
     * 让跟随者看到与胜者同型的失败：{@code RuntimeException} / {@code Error} 原样重抛。
     */
    private static void rethrow(Throwable cause) {
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        throw new IllegalStateException(cause);
    }
}
