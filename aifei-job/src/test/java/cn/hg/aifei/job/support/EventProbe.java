/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.support;

import cn.hg.aifei.job.JobEvent;
import cn.hg.aifei.job.JobListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link JobListener} 的测试替身：线程安全地记录<b>全部事件</b>与<b>回调线程</b>，并提供「等到第 N 个事件」的入口。
 *
 * <p>等待一律用监视器 + 超时（{@code Object#wait(long)}），<b>不用</b> {@code Thread.sleep}：事件一到就被唤醒，
 * 超时立即返回 {@code false}，由用例断言失败。回调线程记录成 {@link Thread} 引用，用例比对引用而<b>不依赖线程名</b>。
 * 事件与线程各存两个平行列表，索引一一对应。</p>
 */
public final class EventProbe implements JobListener {

    /**
     * 已收到的事件，按到达顺序；由 {@link #monitor} 保护。
     */
    private final List<JobEvent> events = new ArrayList<>();

    /**
     * 各事件的回调线程，与 {@link #events} 索引对应；由 {@link #monitor} 保护。
     */
    private final List<Thread> callbackThreads = new ArrayList<>();

    /**
     * 保护两个列表并用于等待通知的监视器。
     */
    private final Object monitor = new Object();

    @Override
    public void onEvent(JobEvent event) {
        synchronized (monitor) {
            events.add(event);
            callbackThreads.add(Thread.currentThread());
            monitor.notifyAll();
        }
    }

    /**
     * @return 已收到事件的不可变快照，按到达顺序
     */
    public List<JobEvent> events() {
        synchronized (monitor) {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    /**
     * @return 各事件回调线程的不可变快照，与 {@link #events()} 索引对应
     */
    public List<Thread> callbackThreads() {
        synchronized (monitor) {
            return Collections.unmodifiableList(new ArrayList<>(callbackThreads));
        }
    }

    /**
     * @param index 事件下标，从 {@code 0} 开始
     * @return 第 {@code index} 个事件的回调线程
     */
    public Thread callbackThread(int index) {
        synchronized (monitor) {
            return callbackThreads.get(index);
        }
    }

    /**
     * @return 已收到的事件总数
     */
    public int size() {
        synchronized (monitor) {
            return events.size();
        }
    }

    /**
     * @param phase 阶段
     * @return 该阶段的全部任务的事件数
     */
    public int count(JobEvent.Phase phase) {
        synchronized (monitor) {
            return countOf(null, phase);
        }
    }

    /**
     * @param taskName 任务名
     * @param phase 阶段
     * @return 该任务该阶段的事件数
     */
    public int count(String taskName, JobEvent.Phase phase) {
        synchronized (monitor) {
            return countOf(taskName, phase);
        }
    }

    /**
     * @param taskName 任务名
     * @return 该任务的事件，按到达顺序
     */
    public List<JobEvent> eventsOf(String taskName) {
        synchronized (monitor) {
            List<JobEvent> matched = new ArrayList<>();
            for (JobEvent event : events) {
                if (event.taskName().equals(taskName)) {
                    matched.add(event);
                }
            }
            return Collections.unmodifiableList(matched);
        }
    }

    /**
     * @param taskName 任务名
     * @return 该任务的阶段序列，按到达顺序
     */
    public List<JobEvent.Phase> phasesOf(String taskName) {
        synchronized (monitor) {
            List<JobEvent.Phase> phases = new ArrayList<>();
            for (JobEvent event : events) {
                if (event.taskName().equals(taskName)) {
                    phases.add(event.phase());
                }
            }
            return Collections.unmodifiableList(phases);
        }
    }

    /**
     * 清空全部记录。
     */
    public void reset() {
        synchronized (monitor) {
            events.clear();
            callbackThreads.clear();
        }
    }

    /**
     * 等待事件总数达到期望值。
     *
     * @param expected 期望的事件总数
     * @param timeoutMillis 超时上限，单位毫秒
     * @return 到达期望值返回 {@code true}；超时返回 {@code false}
     */
    public boolean awaitTotal(int expected, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (monitor) {
            while (events.size() < expected) {
                if (!awaitMore(deadline)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 等待「某任务的某阶段」事件数达到期望值。
     *
     * @param taskName 任务名
     * @param phase 阶段
     * @param expected 期望的事件数
     * @param timeoutMillis 超时上限，单位毫秒
     * @return 到达期望值返回 {@code true}；超时返回 {@code false}
     */
    public boolean awaitPhase(String taskName, JobEvent.Phase phase, int expected, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (monitor) {
            while (countOf(taskName, phase) < expected) {
                if (!awaitMore(deadline)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 在监视器上等待一次，直到被通知或到期。
     *
     * @param deadline 绝对的到期时刻，单位毫秒
     * @return 仍需继续等待返回 {@code true}；已到期或被中断返回 {@code false}
     */
    private boolean awaitMore(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0L) {
            return false;
        }
        try {
            monitor.wait(remaining);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 统计事件数；调用方必须持有 {@link #monitor}。
     *
     * @param taskName 任务名；{@code null} 表示不限任务
     * @param phase 阶段
     * @return 匹配的事件数
     */
    private int countOf(String taskName, JobEvent.Phase phase) {
        int count = 0;
        for (JobEvent event : events) {
            if (taskName != null && !event.taskName().equals(taskName)) {
                continue;
            }
            if (event.phase() == phase) {
                count++;
            }
        }
        return count;
    }
}
