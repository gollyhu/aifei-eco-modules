/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.aifei;

import cn.aifei.plugin.Plugin;
import cn.aifei.scanner.Scanner;
import cn.aifei.util.Prop;
import cn.hg.aifei.job.JobListener;
import cn.hg.aifei.job.internal.CronCalculator;
import cn.hg.aifei.job.internal.JobConfig;
import cn.hg.aifei.job.internal.JobContainer;
import cn.hg.aifei.job.internal.JobEvents;
import cn.hg.aifei.job.internal.JobHolder;
import cn.hg.aifei.job.internal.JobInstances;
import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobSchedule;
import cn.hg.aifei.job.internal.JobScheduler;
import cn.hg.aifei.job.internal.JobScanner;
import cn.hg.aifei.job.internal.JobState;
import cn.hg.aifei.job.local.QuartzCronSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 定时任务能力的装配入口，也是本模块<b>唯一</b> import aifei 插件 / 容器 / 扫描器类型的类。
 *
 * <p>应用侧在 {@code AifeiConfig#config(Plugins)} 内 {@code plugins.add(new JobPlugin(prop))} 完成装配。
 * 构造函数只持有 {@link Prop} 引用：<b>不解析、不校验、不建线程池</b>；{@code job.*} 的 4 个键、扫描包、任务方法与
 * 注解属性全部在 {@link #start()} 内解析与校验，失败即抛 {@code JobException} 使应用启动失败。</p>
 *
 * <p>装配链路：配置 → 扫包取候选类 → 注解识别与签名校验 → 任务名判重建表 → 监听器按类型取一次 →
 * 装观测面 → 排首批待触发实例。任一环节失败都<b>先停掉已启动的调度、再清空观测面、然后抛原异常</b>，
 * 不留半装配状态。</p>
 *
 * <p>实例解析语义：任务实例一律从容器按类型取，取不到或多例都属配置错误（启动失败）；
 * 监听器取不到属<b>正常</b>（观测为可选设施，只是没有事件送达方），多例仍属配置错误。</p>
 */
public class JobPlugin implements Plugin {

    /**
     * 配置源；解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 已装配的调度内核；{@code null} 表示尚未装配，{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private JobScheduler scheduler;

    /**
     * 是否已装配。
     */
    private boolean started;

    /**
     * 创建插件；只保存配置源引用，不做任何解析、校验或建线程。
     *
     * @param prop 配置源
     * @throws IllegalArgumentException {@code prop} 为 {@code null}
     */
    public JobPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        this.prop = prop;
    }

    /**
     * 解析配置、扫描任务、装配调度内核并排首批待触发实例；重复启动不会重复装配。
     *
     * @throws cn.hg.aifei.job.JobException 配置缺失或越界、扫描包为空或扫不到任务、注解与注解方法签名非法、
     *         同一方法多个触发注解、cron 表达式非法、任务实例取不到或多例、同一任务名重复、监听器多例；
     *         抛出即应用启动失败，且不留半装配状态
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        JobScheduler created = null;
        try {
            JobConfig config = JobConfig.parse(prop.getProperties());
            CronCalculator cron = new QuartzCronSupport();
            JobContainer container = new AifeiJobContainer();
            JobScanner scanner = new JobScanner(container, cron, config.scanPackages());
            JobRegistry registry = new JobRegistry(scanner.scan(scanCandidates(config.scanPackages())));
            JobListener listener = JobInstances.optionalSingle(container, JobListener.class);
            JobState state = new JobState();
            created = new JobScheduler(config, registry, state, new JobEvents(listener), new JobSchedule(cron));
            JobHolder.install(registry, state);
            created.start();
            this.scheduler = created;
            this.started = true;
        } catch (RuntimeException | Error e) {
            rollback(created, e);
            throw e;
        }
    }

    /**
     * 停止调度内核并清空观测面；此后 {@code JobKit} 取用返回空集合。重复停止直接返回。
     *
     * <p>监听器引用随调度内核一并释放（持链为 {@code JobScheduler} → {@code JobEvents} → {@code JobListener}），
     * 因此再次 {@link #start()} 会重新从容器取一次。</p>
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            if (scheduler != null) {
                scheduler.stop();
            }
        } finally {
            scheduler = null;
            started = false;
            JobHolder.clear();
        }
    }

    /**
     * 回滚半装配状态：先停已启动的调度（其停机流程可能抛异常，挂到原始故障的抑制异常上，不掩盖根因），
     * 再清空观测面。
     *
     * @param created 已创建的调度内核；尚未创建时传 {@code null}
     * @param failure 原始故障
     */
    private static void rollback(JobScheduler created, Throwable failure) {
        if (created != null) {
            try {
                created.stop();
            } catch (RuntimeException e) {
                failure.addSuppressed(e);
            }
        }
        JobHolder.clear();
    }

    /**
     * 用框架扫描器取出各扫描包下的候选类。
     *
     * <p>候选类发现只能用框架 {@link Scanner}。每个包各建一个扫描器实例：包名与结果集都是该对象的实例字段，
     * 各包独立扫描可避免共享状态被后一次扫描覆盖。注解识别与签名校验仍在 {@code internal.JobScanner}，
     * 本方法只负责「取类」。</p>
     *
     * @param scanPackages 已校验的扫描包清单
     * @return 候选类清单，保持各包的扫描顺序；包内无类时该包贡献零项
     */
    private static List<Class<?>> scanCandidates(List<String> scanPackages) {
        List<Class<?>> candidates = new ArrayList<>();
        for (String packageName : scanPackages) {
            Set<Class<?>> scanned = new Scanner().scan(packageName);
            if (scanned != null) {
                candidates.addAll(scanned);
            }
        }
        return candidates;
    }
}
