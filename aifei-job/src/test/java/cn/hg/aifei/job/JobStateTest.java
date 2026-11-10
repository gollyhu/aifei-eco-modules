/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobState;
import cn.hg.aifei.job.support.TestLogs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 状态记账用例（{@code internal.JobState}）：只收终态、后写覆盖先写、快照每次新实例且不可修改、按任务名排序。
 *
 * <p>「只收终态」是 {@code state()} 观测口径的实现依据：{@link JobEvent.Phase#STARTED} 既不覆盖也不清除上一次
 * 终态，因此<b>执行中的任务不会从观测面消失</b>；从未产生终态的任务不会出现。</p>
 */
public class JobStateTest {

    /**
     * 任务 A 的任务名。
     */
    private static final String TASK_A = "com.acme.AJob#alpha";

    /**
     * 任务 B 的任务名。
     */
    private static final String TASK_B = "com.acme.BJob#beta";

    /**
     * 用例统一使用的触发时间，epoch 毫秒。
     */
    private static final long TIME = 1609430400000L;

    /**
     * 被测状态记录。
     */
    private JobState state;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    /**
     * 每例自建状态记录，保证用例独立。
     */
    @Before
    public void setUp() {
        state = new JobState();
    }

    // ------------------------------------------------------------------ 只收终态

    /**
     * {@code STARTED} 被忽略：从未有终态的任务不出现在快照里。
     */
    @Test
    public void startedIsIgnored() {
        state.record(event(TASK_A, JobEvent.Phase.STARTED, null));
        assertEquals("STARTED 不应进入状态记录", 0, state.snapshot().size());
    }

    /**
     * 三种终态都能记账。
     */
    @Test
    public void allTerminalPhasesAreRecorded() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        state.record(event(TASK_B, JobEvent.Phase.SKIPPED, null));
        state.record(event("com.acme.CJob#gamma", JobEvent.Phase.FAILED, new IllegalStateException("boom")));
        Map<String, JobEvent> snapshot = state.snapshot();
        assertEquals("应记录 3 个任务的终态", 3, snapshot.size());
        assertSame("SUCCESS 应被记录", JobEvent.Phase.SUCCESS, snapshot.get(TASK_A).phase());
        assertSame("SKIPPED 应被记录", JobEvent.Phase.SKIPPED, snapshot.get(TASK_B).phase());
        assertSame("FAILED 应被记录", JobEvent.Phase.FAILED,
                snapshot.get("com.acme.CJob#gamma").phase());
    }

    /**
     * 同一任务后写的终态覆盖先写的：状态是「最近一次终态」，不是历史。
     */
    @Test
    public void laterTerminalOverwritesEarlier() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        state.record(event(TASK_A, JobEvent.Phase.FAILED, new IllegalStateException("boom"), TIME + 50L));
        Map<String, JobEvent> snapshot = state.snapshot();
        assertEquals("同一任务只保留一条", 1, snapshot.size());
        assertSame("应保留后写的阶段", JobEvent.Phase.FAILED, snapshot.get(TASK_A).phase());
        assertEquals("应保留后写的事件时间", TIME + 50L, snapshot.get(TASK_A).triggerTime());
    }

    /**
     * {@code STARTED} 不覆盖也不清除上一次终态：执行中的任务保留上一次结果的可见性。
     */
    @Test
    public void startedDoesNotClearPreviousTerminal() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        state.record(event(TASK_A, JobEvent.Phase.STARTED, null, TIME + 10L));
        Map<String, JobEvent> snapshot = state.snapshot();
        assertEquals("执行中的任务不应从观测面消失", 1, snapshot.size());
        assertSame("应保留上一次终态", JobEvent.Phase.SUCCESS, snapshot.get(TASK_A).phase());
        assertEquals("应保留上一次终态的时间", TIME, snapshot.get(TASK_A).triggerTime());
    }

    /**
     * {@code null} 事件被忽略且不抛异常。
     */
    @Test
    public void nullEventIsIgnored() {
        state.record(null);
        assertEquals("null 事件不应记账", 0, state.snapshot().size());
    }

    // ------------------------------------------------------------------ 快照实例与不可修改

    /**
     * 每次 {@code snapshot()} 都返回<b>新的 {@code Map}</b>。
     */
    @Test
    public void snapshotReturnsNewMapEachCall() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        Map<String, JobEvent> first = state.snapshot();
        Map<String, JobEvent> second = state.snapshot();
        assertNotSame("两次快照应是不同 Map 实例", first, second);
        assertEquals("两次快照内容应一致", first.keySet(), second.keySet());
    }

    /**
     * 每次 {@code snapshot()} 的 value 也是<b>新的 {@link JobEvent} 实例</b>，不暴露内部记录。
     */
    @Test
    public void snapshotReturnsNewEventInstances() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        JobEvent first = state.snapshot().get(TASK_A);
        JobEvent second = state.snapshot().get(TASK_A);
        assertNotSame("快照的 value 应是新实例", first, second);
        assertEquals("两个实例的任务名一致", first.taskName(), second.taskName());
        assertEquals("两个实例的事件时间一致", first.triggerTime(), second.triggerTime());
        assertSame("两个实例的阶段一致", first.phase(), second.phase());
    }

    /**
     * 快照不可修改：写入即 {@link UnsupportedOperationException}。
     */
    @Test
    public void snapshotIsUnmodifiable() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        Map<String, JobEvent> snapshot = state.snapshot();
        try {
            snapshot.put(TASK_B, event(TASK_B, JobEvent.Phase.SUCCESS, null));
            fail("快照应为不可修改视图");
        } catch (UnsupportedOperationException expected) {
            // 预期：不可修改视图拒绝写入
        }
    }

    /**
     * 快照与后续记账解耦：取过快照之后的新终态不改变旧快照。
     */
    @Test
    public void snapshotIsDetachedFromLaterRecords() {
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        Map<String, JobEvent> before = state.snapshot();
        state.record(event(TASK_A, JobEvent.Phase.FAILED, new IllegalStateException("boom"), TIME + 50L));
        assertSame("旧快照不应被后续记账改动", JobEvent.Phase.SUCCESS, before.get(TASK_A).phase());
        assertEquals("旧快照不应被后续记账改动", TIME, before.get(TASK_A).triggerTime());
        assertSame("新快照应反映最新终态", JobEvent.Phase.FAILED, state.snapshot().get(TASK_A).phase());
    }

    // ------------------------------------------------------------------ 排序

    /**
     * 快照按任务名排序（与插入顺序无关），使观测面与日志顺序稳定。
     */
    @Test
    public void snapshotIsOrderedByTaskName() {
        state.record(event("com.acme.CJob#gamma", JobEvent.Phase.SUCCESS, null));
        state.record(event(TASK_B, JobEvent.Phase.SUCCESS, null));
        state.record(event(TASK_A, JobEvent.Phase.SUCCESS, null));
        List<String> keys = new ArrayList<>(state.snapshot().keySet());
        assertEquals("应按任务名升序排列", 3, keys.size());
        assertEquals("第 1 个任务名", TASK_A, keys.get(0));
        assertEquals("第 2 个任务名", TASK_B, keys.get(1));
        assertEquals("第 3 个任务名", "com.acme.CJob#gamma", keys.get(2));
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * @param taskName 任务名
     * @param phase 阶段
     * @param error 故障原因
     * @return 事件快照
     */
    private static JobEvent event(String taskName, JobEvent.Phase phase, Throwable error) {
        return event(taskName, phase, error, TIME);
    }

    /**
     * @param taskName 任务名
     * @param phase 阶段
     * @param error 故障原因
     * @param triggerTime 触发时间
     * @return 事件快照
     */
    private static JobEvent event(String taskName, JobEvent.Phase phase, Throwable error, long triggerTime) {
        return JobEvent.of(taskName, triggerTime, phase, error);
    }
}
