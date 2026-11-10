/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.support;

import cn.hg.aifei.job.Cron;
import cn.hg.aifei.job.FixedDelay;
import cn.hg.aifei.job.FixedRate;
import cn.hg.aifei.job.internal.JobConfig;
import cn.hg.aifei.job.internal.JobNames;
import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.internal.JobTrigger;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语义用例的任务类集合与装配辅助：一个{@link Task}类提供<b>计数、可阻塞、可定时阻塞、可抛异常、可响应中断</b>
 * 五类行为注入，并以 {@code @FixedRate} / {@code @FixedDelay} / {@code @Cron} 三种注解各暴露无参 {@code public void} 方法。
 *
 * <p><b>为什么一个类多个方法</b>：任务名 = {@code 类全名#方法名}（{@link JobNames#of(Method)}），同一类的不同方法天然
 * 得到不同任务名，且<b>注册表按任务名排序</b>——于是「谁先占住执行层唯一工作线程、谁占满队列、谁被拒」可以由方法名
 * （{@code alpha} → {@code beta} → {@code gamma} → {@code delta}）<b>确定性</b>地表达，不必依赖装配顺序这种易碎前提。
 * 反过来，若拆成多个子类共享同一父类方法，{@link JobNames#of(Method)} 取的是<b>声明类</b>，任务名会全部相同并被判重，
 * 反而造不出多任务场景。</p>
 *
 * <p><b>阻塞一律用 {@link Semaphore}</b>：{@link #block()} 的无限阻塞用于「占住唯一工作线程」「停机中断」；
 * {@link #slow(long)} 的定时阻塞用于「任务耗时超过 period」。两者都是<b>任务体自身的耗时</b>，不是用例的等待，
 * 因此用例侧无需任何 {@code Thread.sleep}，也不需要断言「第 N 次触发在 X 毫秒内」。</p>
 *
 * <p>装配辅助（{@link #config(int, int, long)} / {@link #registry(JobTask...)} / 三个任务工厂）放在本类，是为了让 7 个
 * 语义用例共用同一份配置解析与任务名口径，避免各写一遍 {@code Properties} 拼装与反射取方法。</p>
 */
public final class TestJobs {

    /**
     * 语义用例统一的宽松超时，单位毫秒（等待一律用它，不用秒级 sleep）。
     */
    public static final long TIMEOUT_MILLIS = 5000L;

    /**
     * 稳定等待时长，单位毫秒：只用于「确认某指标在短时间内不再变化」，<b>不得</b>用于时序断言。
     */
    public static final long STABLE_WAIT_MILLIS = 200L;

    /**
     * 装配用的扫描包：只要求非空，语义用例不经过扫描器。
     */
    private static final String SCAN_PACKAGES = "cn.hg.aifei.job.support";

    /**
     * 禁止创建工具类实例。
     */
    private TestJobs() {
    }

    // ------------------------------------------------------------------ 装配辅助

    /**
     * 构造配置值对象：绕过 {@code Properties} 拼装的样板代码。
     *
     * @param poolSize 执行层线程数
     * @param poolQueue 执行层有界队列容量
     * @param shutdownTimeoutMillis 停机等待上限，单位毫秒
     * @return 配置值对象
     */
    public static JobConfig config(int poolSize, int poolQueue, long shutdownTimeoutMillis) {
        Properties props = new Properties();
        props.setProperty(JobConfig.SCAN_PACKAGES_KEY, SCAN_PACKAGES);
        props.setProperty(JobConfig.POOL_SIZE_KEY, Integer.toString(poolSize));
        props.setProperty(JobConfig.POOL_QUEUE_KEY, Integer.toString(poolQueue));
        props.setProperty(JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, Long.toString(shutdownTimeoutMillis));
        return JobConfig.parse(props);
    }

    /**
     * @param tasks 任务定义
     * @return 注册表
     */
    public static JobRegistry registry(JobTask... tasks) {
        return new JobRegistry(Arrays.asList(tasks));
    }

    /**
     * 稳定等待：短等待后返回。仅用于「确认不再有新变化」，<b>不用</b>于「某事件应在 X 毫秒内发生」。
     *
     * @param millis 等待时长，单位毫秒
     */
    public static void stableWait(long millis) {
        try {
            new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ 任务工厂

    /**
     * 构造固定频率任务定义。
     *
     * @param instance {@link Task} 实例
     * @param methodName 方法名（{@code alpha} / {@code beta} / {@code gamma} / {@code delta} / {@code epsilon}）
     * @param period 间隔，单位毫秒
     * @param initialDelay 首次延迟，单位毫秒
     * @return 任务定义
     */
    public static JobTask fixedRate(Object instance, String methodName, long period, long initialDelay) {
        Method method = method(instance, methodName);
        String taskName = JobNames.of(method);
        return new JobTask(taskName, instance, method, JobTrigger.fixedRate(period, initialDelay, taskName));
    }

    /**
     * 构造固定延迟任务定义。
     *
     * @param instance {@link Task} 实例
     * @param methodName 方法名（{@code epsilon}）
     * @param delay 延迟，单位毫秒
     * @param initialDelay 首次延迟，单位毫秒
     * @return 任务定义
     */
    public static JobTask fixedDelay(Object instance, String methodName, long delay, long initialDelay) {
        Method method = method(instance, methodName);
        String taskName = JobNames.of(method);
        return new JobTask(taskName, instance, method, JobTrigger.fixedDelay(delay, initialDelay, taskName));
    }

    /**
     * 构造 cron 任务定义。
     *
     * @param instance {@link Task} 实例
     * @param methodName 方法名（{@code zeta}）
     * @param expression cron 表达式
     * @return 任务定义
     */
    public static JobTask cron(Object instance, String methodName, String expression) {
        Method method = method(instance, methodName);
        String taskName = JobNames.of(method);
        return new JobTask(taskName, instance, method, JobTrigger.cron(expression, taskName));
    }

    /**
     * @param instance {@link Task} 实例
     * @param methodName 方法名
     * @return 该方法的任务名（与生产口径同为 {@code 类全名#方法名}）
     */
    public static String nameOf(Object instance, String methodName) {
        return JobNames.of(method(instance, methodName));
    }

    /**
     * 取 {@code public} 无参方法。
     *
     * @param instance 实例
     * @param methodName 方法名
     * @return 方法对象
     */
    private static Method method(Object instance, String methodName) {
        try {
            return instance.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "no public method " + methodName + " on " + instance.getClass().getName(), e);
        }
    }

    // ------------------------------------------------------------------ 任务体

    /**
     * 可注入行为的任务体：计数、并发峰值、开始时刻序列、可选阻塞 / 定时阻塞、可选抛异常、中断响应。
     *
     * <p>六个无参 {@code public void} 方法分别挂三种触发注解：{@code alpha} / {@code beta} / {@code gamma} /
     * {@code delta} 为固定频率，{@code epsilon} 为固定延迟，{@code zeta} 为 cron。注解属性只是「可扫描的默认形态」，
     * 用例真正生效的 {@code period} / {@code initialDelay} 由 {@link JobTrigger} 显式给出——这样同一任务体可以按用例
     * 取 20ms 级数值，而不必为每个时长新建一个类。</p>
     */
    public static final class Task {

        /**
         * 任务体被调用的次数。
         */
        private final AtomicInteger runs = new AtomicInteger();

        /**
         * 当前正在执行的次数（不重叠断言的观测量）。
         */
        private final AtomicInteger inFlight = new AtomicInteger();

        /**
         * 历史并发峰值。
         */
        private final AtomicInteger peakInFlight = new AtomicInteger();

        /**
         * 每次进入任务体时的时刻，按到达顺序，epoch 毫秒。
         */
        private final CopyOnWriteArrayList<Long> starts = new CopyOnWriteArrayList<>();

        /**
         * 阻塞许可：{@link #block()} 时无限等待，{@link #slow(long)} 时定时等待。
         */
        private final Semaphore permits = new Semaphore(0);

        /**
         * 「任务体已真正进入」信号：{@code STARTED} 事件发生在任务体之前，因此用例不能拿 {@link #runs()} 当作
         * 「已在执行」的依据，需等这个信号。
         */
        private final CountDownLatch firstEntry = new CountDownLatch(1);

        /**
         * 保护 {@link #starts} 并用于「已进入任务体 N 次」等待的监视器。
         */
        private final Object entryMonitor = new Object();

        /**
         * 是否无限阻塞直到被释放或中断。
         */
        private volatile boolean blocking;

        /**
         * 定时阻塞时长，单位毫秒；{@code 0} 表示不阻塞。
         */
        private volatile long holdMillis;

        /**
         * 待抛出的异常；{@code null} 表示不抛。
         */
        private volatile RuntimeException failure;

        /**
         * 从第几次调用开始抛异常（{@code 1} 起）。
         */
        private volatile int failFromRun = 1;

        /**
         * 是否每次调用都抛异常。
         */
        private volatile boolean failEveryRun;

        /**
         * 是否收到过中断。
         */
        private volatile boolean interrupted;

        /**
         * 最近一次任务体所在线程。
         */
        private volatile Thread bodyThread;

        /**
         * 固定频率任务体（排序最前，用于「占住唯一工作线程」）。
         */
        @FixedRate(period = 20)
        public void alpha() {
            body();
        }

        /**
         * 固定频率任务体（排序第二，用于「占满队列」）。
         */
        @FixedRate(period = 20)
        public void beta() {
            body();
        }

        /**
         * 固定频率任务体（排序第三，用于「提交被拒」）。
         */
        @FixedRate(period = 20)
        public void gamma() {
            body();
        }

        /**
         * 固定频率任务体（排序第四，第二个「提交被拒」任务）。
         */
        @FixedRate(period = 20)
        public void delta() {
            body();
        }

        /**
         * 固定延迟任务体。
         */
        @FixedDelay(delay = 20)
        public void epsilon() {
            body();
        }

        /**
         * cron 任务体。
         */
        @Cron("0 0 0 1 1 ? 2099")
        public void zeta() {
            body();
        }

        /**
         * 任务体共用实现：计数与采样 → 可选阻塞 → 可选抛异常。
         */
        private void body() {
            bodyThread = Thread.currentThread();
            int run = runs.incrementAndGet();
            synchronized (entryMonitor) {
                starts.add(Long.valueOf(System.currentTimeMillis()));
                entryMonitor.notifyAll();
            }
            firstEntry.countDown();
            int flight = inFlight.incrementAndGet();
            int peak = peakInFlight.get();
            while (flight > peak && !peakInFlight.compareAndSet(peak, flight)) {
                peak = peakInFlight.get();
            }
            try {
                if (blocking) {
                    permits.acquire();
                } else if (holdMillis > 0L) {
                    permits.tryAcquire(holdMillis, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                // 停机中断：记录后恢复中断位并正常返回，让终态记账与续排（被拒）都照常走一遍。
                interrupted = true;
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            RuntimeException error = this.failure;
            if (error != null && (failEveryRun || run == failFromRun)) {
                throw error;
            }
        }

        /**
         * @return 本对象，便于链式装配
         */
        public Task block() {
            this.blocking = true;
            return this;
        }

        /**
         * @param millis 每次执行最多阻塞的毫秒数
         * @return 本对象，便于链式装配
         */
        public Task slow(long millis) {
            this.holdMillis = millis;
            return this;
        }

        /**
         * @param error 每次执行都抛出的异常
         * @return 本对象，便于链式装配
         */
        public Task failAlways(RuntimeException error) {
            this.failure = error;
            this.failEveryRun = true;
            this.failFromRun = 1;
            return this;
        }

        /**
         * @param runNumber 第几次调用开始抛异常（{@code 1} 起）
         * @param error 抛出的异常
         * @return 本对象，便于链式装配
         */
        public Task failOnRun(int runNumber, RuntimeException error) {
            this.failure = error;
            this.failEveryRun = false;
            this.failFromRun = runNumber;
            return this;
        }

        /**
         * @param count 释放的许可数
         */
        public void release(int count) {
            permits.release(count);
        }

        /**
         * 解除阻塞：后续执行立即返回（{@code @After} 与「队列腾空」用例释放挂起任务用）。
         */
        public void unblock() {
            this.blocking = false;
            this.holdMillis = 0L;
            permits.release(64);
        }

        /**
         * 等待任务体<b>真正进入</b>（{@code STARTED} 事件早于任务体，故不能用 {@link #runs()} 代替本信号）。
         *
         * @param timeoutMillis 超时上限
         * @return 是否已进入任务体
         */
        public boolean awaitFirstEntry(long timeoutMillis) {
            try {
                return firstEntry.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        /**
         * 等待任务体<b>被进入 N 次</b>：读取 {@link #startTime(int)} 前必须先等到对应次数，否则 {@code STARTED}
         * 早于任务体这一事实会让下标越界（这是本批最容易漏的竞态）。
         *
         * @param expected 期望的进入次数
         * @param timeoutMillis 超时上限
         * @return 是否到达期望次数
         */
        public boolean awaitEntries(int expected, long timeoutMillis) {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            synchronized (entryMonitor) {
                while (starts.size() < expected) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    try {
                        entryMonitor.wait(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return true;
            }
        }

        /**
         * @return 任务体被调用的次数
         */
        public int runs() {
            return runs.get();
        }

        /**
         * @return 每次进入任务体的时刻，按到达顺序
         */
        public List<Long> starts() {
            return Collections.unmodifiableList(new ArrayList<>(starts));
        }

        /**
         * @param index 下标，从 {@code 0} 开始
         * @return 第 {@code index} 次进入任务体的时刻，epoch 毫秒
         */
        public long startTime(int index) {
            return starts.get(index).longValue();
        }

        /**
         * @return 历史并发峰值
         */
        public int peakInFlight() {
            return peakInFlight.get();
        }

        /**
         * @return 是否收到过中断
         */
        public boolean interrupted() {
            return interrupted;
        }

        /**
         * @return 最近一次任务体所在线程；尚未执行时为 {@code null}
         */
        public Thread bodyThread() {
            return bodyThread;
        }
    }
}
