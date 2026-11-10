/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * demo 自己的事件环形缓冲：监听器写入、端点读取，是「执行中」这类过程视图的<b>唯一来源</b>。
 *
 * <p>模块的 {@code JobKit.state()} 只承载最近一次终态，不含运行态，因此「正在执行 / 在途任务」只能由
 * {@link cn.hg.aifei.job.JobListener} 的事件流推导——本类就是这个流的容器（容量固定，最新在前，满了丢最旧）。</p>
 *
 * <p>线程安全：监听器回调并发到达（不同任务的事件来自不同线程），故所有读写都在同一个监视器内完成；
 * 读出的列表是快照副本，调用方修改它不影响缓冲区。</p>
 */
public final class DemoEventLog {

    /**
     * 环形缓冲容量：与前端原型的事件上限一致。
     */
    public static final int CAPACITY = 240;

    /**
     * 阶段名：即将开始执行（与模块 {@code JobEvent.Phase#STARTED} 同名，便于页面直接比对）。
     */
    public static final String PHASE_STARTED = "STARTED";

    /**
     * 阶段名：本次计划触发未执行（提交被拒 / fixedRate 超时 / 停机中断）。
     */
    public static final String PHASE_SKIPPED = "SKIPPED";

    /**
     * 阶段名：任务异常。
     */
    public static final String PHASE_FAILED = "FAILED";

    /**
     * 事件缓冲区，最新元素在队首。
     */
    private static final Deque<Record> RECORDS = new ArrayDeque<Record>(CAPACITY);

    /**
     * 自增序号，跨 {@link #clear()} 不回退（页面按序号判断新旧）。
     */
    private static long sequence;

    /**
     * 首次写入事件的时刻，用于「启动于」展示；无事件时为 {@code 0}。
     */
    private static long firstRecordedAt;

    /**
     * 禁止创建工具类实例。
     */
    private DemoEventLog() {
    }

    /**
     * 追加一条事件；容量已满时丢弃最旧的一条。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param phase 阶段名，取值见 {@link #PHASE_STARTED} 等
     * @param triggerTime 触发时间（epoch 毫秒）
     * @param errorText 异常摘要，非 {@code FAILED} 阶段传 {@code null}
     * @param threadName 产生该事件的线程名（监听器同步回调的所在线程）
     */
    public static void record(String taskName, String phase, long triggerTime, String errorText, String threadName) {
        Record record = new Record(0L, taskName, phase, triggerTime, errorText, threadName);
        synchronized (RECORDS) {
            record.sequence = ++sequence;
            if (RECORDS.size() >= CAPACITY) {
                RECORDS.removeLast();
            }
            RECORDS.addFirst(record);
            if (firstRecordedAt <= 0L) {
                firstRecordedAt = System.currentTimeMillis();
            }
        }
    }

    /**
     * 取事件快照（最新在前）。
     *
     * @return 新的可变副本，修改它不影响缓冲区
     */
    public static List<Record> snapshot() {
        synchronized (RECORDS) {
            return new ArrayList<Record>(RECORDS);
        }
    }

    /**
     * 清空事件流；序号继续自增，不清零。
     *
     * @return 被清掉的条数
     */
    public static int clear() {
        synchronized (RECORDS) {
            int cleared = RECORDS.size();
            RECORDS.clear();
            return cleared;
        }
    }

    /**
     * @return 当前事件条数
     */
    public static int size() {
        synchronized (RECORDS) {
            return RECORDS.size();
        }
    }

    /**
     * @return 首次写入事件的时刻（epoch 毫秒）；无事件时返回 {@code 0}
     */
    public static long firstRecordedAt() {
        synchronized (RECORDS) {
            return firstRecordedAt;
        }
    }

    /**
     * 取某任务最近一条事件。
     *
     * @param taskName 任务名
     * @return 最近一条事件；该任务无事件时返回 {@code null}
     */
    public static Record lastOf(String taskName) {
        if (taskName == null) {
            return null;
        }
        synchronized (RECORDS) {
            for (Record record : RECORDS) {
                if (taskName.equals(record.taskName)) {
                    return record;
                }
            }
            return null;
        }
    }

    /**
     * 取某任务最近一条<b>终态</b>事件（{@code SUCCESS} / {@code FAILED} / {@code SKIPPED}）。
     *
     * @param taskName 任务名
     * @return 最近一条终态事件；无终态时返回 {@code null}
     */
    public static Record lastTerminalOf(String taskName) {
        if (taskName == null) {
            return null;
        }
        synchronized (RECORDS) {
            for (Record record : RECORDS) {
                if (taskName.equals(record.taskName) && !PHASE_STARTED.equals(record.phase)) {
                    return record;
                }
            }
            return null;
        }
    }

    /**
     * 取在途任务：每个任务只看最近一条事件，阶段为 {@code STARTED} 即表示正在执行。
     *
     * @return 在途事件快照（最新在前）
     */
    public static List<Record> inFlight() {
        List<Record> result = new ArrayList<Record>();
        synchronized (RECORDS) {
            List<String> seen = new ArrayList<String>();
            for (Record record : RECORDS) {
                if (record.taskName == null || seen.contains(record.taskName)) {
                    continue;
                }
                seen.add(record.taskName);
                if (PHASE_STARTED.equals(record.phase)) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    /**
     * 统计某阶段的事件条数。
     *
     * @param phase 阶段名
     * @return 条数
     */
    public static int countPhase(String phase) {
        if (phase == null) {
            return 0;
        }
        int count = 0;
        synchronized (RECORDS) {
            for (Record record : RECORDS) {
                if (phase.equals(record.phase)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 一条事件的不可变快照。
     */
    public static final class Record {

        /**
         * 自增序号（越大越新）。
         */
        private long sequence;

        /**
         * 任务名（类全名#方法名）。
         */
        private final String taskName;

        /**
         * 阶段名。
         */
        private final String phase;

        /**
         * 触发时间（epoch 毫秒）；{@code SKIPPED} 时为原计划触发时刻。
         */
        private final long triggerTime;

        /**
         * 异常摘要，非 {@code FAILED} 阶段为 {@code null}。
         */
        private final String errorText;

        /**
         * 产生该事件的线程名。
         */
        private final String threadName;

        /**
         * 创建事件记录；序号由 {@link DemoEventLog#record} 在同步块内回填。
         *
         * @param sequence 序号，占位传 {@code 0}
         * @param taskName 任务名
         * @param phase 阶段名
         * @param triggerTime 触发时间（epoch 毫秒）
         * @param errorText 异常摘要，可为 {@code null}
         * @param threadName 线程名
         */
        private Record(long sequence, String taskName, String phase, long triggerTime,
                       String errorText, String threadName) {
            this.sequence = sequence;
            this.taskName = taskName;
            this.phase = phase;
            this.triggerTime = triggerTime;
            this.errorText = errorText;
            this.threadName = threadName;
        }

        /**
         * @return 自增序号
         */
        public long sequence() {
            return sequence;
        }

        /**
         * @return 任务名
         */
        public String taskName() {
            return taskName;
        }

        /**
         * @return 阶段名
         */
        public String phase() {
            return phase;
        }

        /**
         * @return 触发时间（epoch 毫秒）
         */
        public long triggerTime() {
            return triggerTime;
        }

        /**
         * @return 异常摘要，非 {@code FAILED} 阶段为 {@code null}
         */
        public String errorText() {
            return errorText;
        }

        /**
         * @return 产生该事件的线程名
         */
        public String threadName() {
            return threadName;
        }
    }
}
