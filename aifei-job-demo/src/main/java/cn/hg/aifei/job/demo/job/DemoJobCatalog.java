/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 演示任务目录：<b>demo 侧唯一的一份任务元数据</b>，含触发方式描述与「下次触发时间」的自算规则。
 *
 * <p>模块公共面只有 {@code JobKit.names()} 与 {@code JobKit.state()}，{@code JobEvent} 里也没有
 * {@code period} / {@code cron} / {@code nextFireTime}。因此「触发方式」与「下次触发时间」这两项由本类
 * 按 demo 自己的注解常量自算——<b>不是模块能力</b>，也不读模块内部类、不依赖 Quartz：</p>
 *
 * <ul>
 *   <li>cron：表达式 {@code 0/5 * * * * ?} 的触发点是「秒数被 5 整除」的时刻，故按 5 秒对齐 epoch 求下一个点；</li>
 *   <li>fixedRate：以上次<b>开始</b>为基准；已越过计划点则按「当前时刻 + 节拍」重排（对应一次 SKIPPED）；</li>
 *   <li>fixedDelay：以上次<b>结束</b>为基准；从未执行过时取首次延迟。</li>
 * </ul>
 */
public final class DemoJobCatalog {

    /**
     * cron 表达式原文（与 {@link DemoCronJob#tick} 上的注解保持一致）。
     */
    public static final String CRON_EXPRESSION = "0/5 * * * * ?";

    /**
     * cron 节拍（毫秒）：{@code 0/5 * * * * ?} 的触发点间隔。
     */
    public static final long CRON_STEP_MILLIS = 5000L;

    /**
     * {@link DemoFixedRateJob#poll} 的 period（毫秒）；注解与本目录共用同一常量，避免两处节拍漂移。
     */
    public static final long FIXED_RATE_PERIOD_MILLIS = 3000L;

    /**
     * {@link DemoFixedRateJob#poll} 的 initialDelay（毫秒）。
     */
    public static final long FIXED_RATE_INITIAL_DELAY_MILLIS = 2000L;

    /**
     * {@link DemoFixedDelayJob#flush} 的 delay（毫秒）。
     */
    public static final long FIXED_DELAY_BEAT_MILLIS = 5000L;

    /**
     * {@link DemoFailingJob#boom} 的 period（毫秒）。
     */
    public static final long FAILING_PERIOD_MILLIS = 4000L;

    /**
     * {@link DemoSlowJob#slow} 的 period（毫秒）；任务体耗时 4000 ms 大于它，故每轮记 SKIPPED。
     */
    public static final long SLOW_PERIOD_MILLIS = 2000L;

    /**
     * {@link DemoShutdownJob#waiting} 的 delay（毫秒）。
     */
    public static final long SHUTDOWN_DELAY_MILLIS = 3000L;

    /**
     * 全部演示任务（顺序即页面表格顺序）。
     */
    private static final List<Task> TASKS = Collections.unmodifiableList(Arrays.asList(
            Task.cron(DemoCronJob.class, "tick", "每 5 秒到点触发（cron 表达式自身定义触发点）"),
            Task.fixedRate(DemoFixedRateJob.class, "poll", "固定频率：以上次开始为基准（首次延迟 2 秒）",
                    FIXED_RATE_PERIOD_MILLIS, FIXED_RATE_INITIAL_DELAY_MILLIS),
            Task.fixedDelay(DemoFixedDelayJob.class, "flush", "固定延迟：以上次结束为基准",
                    FIXED_DELAY_BEAT_MILLIS, 0L),
            Task.fixedRate(DemoFailingJob.class, "boom", "必抛异常：演示异常就地隔离，不影响其他任务",
                    FAILING_PERIOD_MILLIS, 0L),
            Task.fixedRate(DemoSlowJob.class, "slow", "任务体耗时 4 秒 > 节拍 2 秒：演示 fixedRate 超时记 SKIPPED",
                    SLOW_PERIOD_MILLIS, 0L),
            Task.fixedDelay(DemoShutdownJob.class, "waiting", "长阻塞任务：演示停机「等待在途 → 超时中断」",
                    SHUTDOWN_DELAY_MILLIS, 0L)));

    /**
     * 禁止创建工具类实例。
     */
    private DemoJobCatalog() {
    }

    /**
     * @return 全部演示任务（不可变、顺序固定）
     */
    public static List<Task> all() {
        return TASKS;
    }

    /**
     * 按任务名查任务元数据。
     *
     * @param taskName 任务名（类全名#方法名）
     * @return 任务元数据；未登记的任务返回 {@code null}
     */
    public static Task of(String taskName) {
        if (taskName == null) {
            return null;
        }
        for (Task task : TASKS) {
            if (taskName.equals(task.taskName())) {
                return task;
            }
        }
        return null;
    }

    /**
     * @return 全部任务名，顺序与 {@link #all()} 一致
     */
    public static List<String> taskNames() {
        List<String> names = new ArrayList<String>(TASKS.size());
        for (Task task : TASKS) {
            names.add(task.taskName());
        }
        return names;
    }

    /**
     * 触发方式。
     */
    public enum Mode {

        /**
         * cron 表达式触发。
         */
        CRON,

        /**
         * 固定频率：以上次开始为基准。
         */
        FIXED_RATE,

        /**
         * 固定延迟：以上次结束为基准。
         */
        FIXED_DELAY
    }

    /**
     * 一个演示任务的元数据（不可变）。
     */
    public static final class Task {

        /**
         * 任务名：类全名#方法名。
         */
        private final String name;

        /**
         * 业务说明。
         */
        private final String biz;

        /**
         * 触发方式。
         */
        private final Mode mode;

        /**
         * 节拍（毫秒）：fixedRate 的 period、fixedDelay 的 delay；cron 时为 {@link #CRON_STEP_MILLIS}。
         */
        private final long beat;

        /**
         * 首次延迟（毫秒）：cron 时无意义，固定为 {@code 0}。
         */
        private final long initialDelay;

        /**
         * cron 表达式原文；非 cron 任务为 {@code null}。
         */
        private final String expression;

        /**
         * 创建任务元数据。
         *
         * @param name 任务名
         * @param biz 业务说明
         * @param mode 触发方式
         * @param beat 节拍（毫秒）
         * @param initialDelay 首次延迟（毫秒）
         * @param expression cron 表达式原文，非 cron 任务传 {@code null}
         */
        private Task(String name, String biz, Mode mode, long beat, long initialDelay, String expression) {
            this.name = name;
            this.biz = biz;
            this.mode = mode;
            this.beat = beat;
            this.initialDelay = initialDelay;
            this.expression = expression;
        }

        /**
         * 构造 cron 任务元数据。
         *
         * @param jobClass 任务类
         * @param method 方法名
         * @param biz 业务说明
         * @return 任务元数据
         */
        static Task cron(Class<?> jobClass, String method, String biz) {
            return new Task(jobClass.getName() + "#" + method, biz, Mode.CRON,
                    CRON_STEP_MILLIS, 0L, CRON_EXPRESSION);
        }

        /**
         * 构造固定频率任务元数据。
         *
         * @param jobClass 任务类
         * @param method 方法名
         * @param biz 业务说明
         * @param period 节拍（毫秒）
         * @param initialDelay 首次延迟（毫秒）
         * @return 任务元数据
         */
        static Task fixedRate(Class<?> jobClass, String method, String biz, long period, long initialDelay) {
            return new Task(jobClass.getName() + "#" + method, biz, Mode.FIXED_RATE, period, initialDelay, null);
        }

        /**
         * 构造固定延迟任务元数据。
         *
         * @param jobClass 任务类
         * @param method 方法名
         * @param biz 业务说明
         * @param delay 节拍（毫秒）
         * @param initialDelay 首次延迟（毫秒）
         * @return 任务元数据
         */
        static Task fixedDelay(Class<?> jobClass, String method, String biz, long delay, long initialDelay) {
            return new Task(jobClass.getName() + "#" + method, biz, Mode.FIXED_DELAY, delay, initialDelay, null);
        }

        /**
         * @return 任务名（类全名#方法名）
         */
        public String taskName() {
            return name;
        }

        /**
         * @return 业务说明
         */
        public String biz() {
            return biz;
        }

        /**
         * @return 触发方式
         */
        public Mode mode() {
            return mode;
        }

        /**
         * @return 节拍（毫秒）
         */
        public long beat() {
            return beat;
        }

        /**
         * @return 首次延迟（毫秒）
         */
        public long initialDelay() {
            return initialDelay;
        }

        /**
         * @return cron 表达式原文；非 cron 任务返回 {@code null}
         */
        public String expression() {
            return expression;
        }

        /**
         * @return {@code @FixedRate} 的 period；非 fixedRate 任务返回 {@code null}（页面显示用）
         */
        public Long periodOrNull() {
            return mode == Mode.FIXED_RATE ? Long.valueOf(beat) : null;
        }

        /**
         * @return {@code @FixedDelay} 的 delay；非 fixedDelay 任务返回 {@code null}（页面显示用）
         */
        public Long delayOrNull() {
            return mode == Mode.FIXED_DELAY ? Long.valueOf(beat) : null;
        }

        /**
         * 触发方式描述，形如 {@code @FixedRate(period = 3000, initialDelay = 2000)}。
         *
         * @return 注解形式的描述文本
         */
        public String triggerText() {
            if (mode == Mode.CRON) {
                return "@Cron(\"" + expression + "\")";
            }
            if (mode == Mode.FIXED_RATE) {
                return initialDelay > 0L
                        ? "@FixedRate(period = " + beat + ", initialDelay = " + initialDelay + ")"
                        : "@FixedRate(period = " + beat + ")";
            }
            return initialDelay > 0L
                    ? "@FixedDelay(delay = " + beat + ", initialDelay = " + initialDelay + ")"
                    : "@FixedDelay(delay = " + beat + ")";
        }

        /**
         * 节拍说明（含基准，页面「触发方式与节拍」列的第二行）。
         *
         * @return 节拍说明文本
         */
        public String rhythmText() {
            if (mode == Mode.CRON) {
                return "每 " + (beat / 1000L) + " 秒（表达式定义全部触发点）";
            }
            if (mode == Mode.FIXED_RATE) {
                return "每 " + (beat / 1000L) + " 秒（以上次开始为基准）";
            }
            return "上次结束 + " + (beat / 1000L) + " 秒";
        }

        /**
         * 自算下次触发时刻（epoch 毫秒）；这是 demo 侧近似值，不是模块承诺。
         *
         * @param now 当前时刻（epoch 毫秒）
         * @param lastStartTime 该任务最近一次开始执行时刻；无记录传 {@code 0}
         * @param lastEndTime 该任务最近一次终态时刻；无记录传 {@code 0}
         * @return 下次触发时刻（epoch 毫秒），恒大于 {@code now}
         */
        public long nextFireTime(long now, long lastStartTime, long lastEndTime) {
            if (mode == Mode.CRON) {
                // 秒对齐：下一个严格晚于 now 的 5 秒节拍点
                return (now / beat + 1L) * beat;
            }
            if (mode == Mode.FIXED_RATE) {
                long base = lastStartTime > 0L ? lastStartTime : now + initialDelay;
                long next = base + beat;
                // 已越过计划点（任务体超时）：按当前时刻重排，即观测到的那次 SKIPPED
                return next > now ? next : now + beat;
            }
            long base = lastEndTime > 0L ? lastEndTime : (lastStartTime > 0L ? lastStartTime : now + initialDelay);
            long next = base + beat;
            return next > now ? next : now + beat;
        }
    }
}
