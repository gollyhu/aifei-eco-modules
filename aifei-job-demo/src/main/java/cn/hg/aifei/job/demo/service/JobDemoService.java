/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.service;

import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.hg.aifei.job.JobEvent;
import cn.hg.aifei.job.JobKit;
import cn.hg.aifei.job.demo.common.DemoRuntimeConfig;
import cn.hg.aifei.job.demo.job.DemoEventLog;
import cn.hg.aifei.job.demo.job.DemoFormats;
import cn.hg.aifei.job.demo.job.DemoJobCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调度演示端点：把 {@code JobKit} 的只读观测面、监听器事件流与 demo 自算的触发信息拼成页面可直接消费的 JSON。
 *
 * <p>全部端点均为 GET，HTTP 状态恒 200，业务语义由 {@code code} 承载（{@code 0} 为成功）。
 * 三条边界必须守住：</p>
 *
 * <ul>
 *   <li><b>现场快照</b>（{@link #names()} / {@link #state()}）只转发模块公共面，不加工、不缓存；</li>
 *   <li><b>执行中视图</b>只能来自事件流（{@link #events()}）——{@code state()} 不含运行态；</li>
 *   <li><b>触发方式与下次触发时间</b>由 {@link DemoJobCatalog} 按 demo 自己的注解常量自算，
 *       <b>不是模块能力</b>，也不读模块内部类。</li>
 * </ul>
 */
@Path("/job/demo")
public class JobDemoService {

    /**
     * 演示页视图：由 Enjoy 从 {@code webapp/view/job/demo.html} 渲染（模板根为 {@code webapp/view}）。
     *
     * @return 模板名，必须以 {@code /} 开头
     */
    @Path("page")
    public String page() {
        return "/job/demo.html";
    }

    /**
     * 已注册任务名（{@code JobKit.names()} 原样转发，按任务名排序）。
     *
     * @return {@code data} 为任务名数组；未启动或已停机时为空数组
     */
    @Path("names")
    public Out names() {
        List<String> names = new ArrayList<String>(JobKit.names());
        return Out.ok().data(names);
    }

    /**
     * 各任务最近一次终态（{@code JobKit.state()} 原样转发）。
     *
     * @return {@code data} 为「任务名 → { phase, triggerTime, triggerAt, error }」；无终态时为空对象
     */
    @Path("state")
    public Out state() {
        Map<String, JobEvent> snapshot = JobKit.state();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, JobEvent> entry : snapshot.entrySet()) {
            JobEvent event = entry.getValue();
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("phase", event == null || event.phase() == null ? null : event.phase().name());
            view.put("triggerTime", event == null ? 0L : event.triggerTime());
            view.put("triggerAt", event == null ? "—" : DemoFormats.clockText(event.triggerTime()));
            view.put("error", event == null ? null : DemoFormats.errorText(event.error()));
            data.put(entry.getKey(), view);
        }
        return Out.ok().data(data);
    }

    /**
     * 监听器事件流（最新在前），是「执行中 / 到点触发 / 跳过」三类视图的唯一来源。
     *
     * @return {@code data} 为事件数组，元素含 { seq, taskName, phase, triggerTime, triggerAt, threadName, error }
     */
    @Path("events")
    public Out events() {
        List<DemoEventLog.Record> records = DemoEventLog.snapshot();
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>(records.size());
        for (DemoEventLog.Record record : records) {
            data.add(recordView(record));
        }
        return Out.ok().data(data);
    }

    /**
     * 清空事件流（不影响调度、不影响 {@code JobKit.state()}）。
     *
     * @return {@code data} 为 { cleared, remaining, capacity }
     */
    @Path("events/clear")
    public Out clearEvents() {
        int cleared = DemoEventLog.clear();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("cleared", cleared);
        data.put("remaining", DemoEventLog.size());
        data.put("capacity", DemoEventLog.CAPACITY);
        return Out.ok("已清空事件流 " + cleared + " 条").data(data);
    }

    /**
     * 任务清单：触发方式、节拍与 demo 自算的下次触发时间（观测点 1 / 2）。
     *
     * @return {@code data} 为任务数组，顺序与 {@link DemoJobCatalog#all()} 一致
     */
    @Path("tasks")
    public Out tasks() {
        long now = System.currentTimeMillis();
        Set<String> registered = JobKit.names();
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (DemoJobCatalog.Task task : DemoJobCatalog.all()) {
            data.add(taskView(task, registered, now));
        }
        return Out.ok().data(data);
    }

    /**
     * 停机演示信息：只读展示 {@code JobPlugin#stop()} 的收敛顺序与当前在途 / 跳过情况，
     * <b>不会真的停掉应用</b>。
     *
     * @return {@code data} 为 { applying, timeoutMillis, poolSize, queueCapacity, steps, inFlight,
     *         skippedTotal, runningTotal, note }
     */
    @Path("shutdown")
    public Out shutdown() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("applying", Boolean.FALSE);
        data.put("timeoutMillis", DemoRuntimeConfig.shutdownTimeoutMillis());
        data.put("poolSize", DemoRuntimeConfig.poolSize());
        data.put("queueCapacity", DemoRuntimeConfig.queueCapacity());

        List<String> steps = new ArrayList<String>();
        steps.add("① 停止新触发：唤醒层不再排新的待触发实例");
        steps.add("② 等待在途：已提交任务继续执行，最多等待 timeoutMillis");
        steps.add("③ 超时中断：shutdownNow() 发中断信号，未执行任务记 SKIPPED");
        data.put("steps", steps);

        List<Map<String, Object>> inFlight = new ArrayList<Map<String, Object>>();
        long now = System.currentTimeMillis();
        for (DemoEventLog.Record record : DemoEventLog.inFlight()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("taskName", record.taskName());
            item.put("threadName", record.threadName());
            item.put("startedAt", record.triggerTime());
            item.put("startedAtText", DemoFormats.clockText(record.triggerTime()));
            item.put("runningMillis", Math.max(0L, now - record.triggerTime()));
            inFlight.add(item);
        }
        data.put("inFlight", inFlight);
        data.put("runningTotal", inFlight.size());
        data.put("skippedTotal", DemoEventLog.countPhase(DemoEventLog.PHASE_SKIPPED));
        data.put("note", "本端点只读：demo 不调用 JobPlugin.stop()。要观察真实停机，"
                + "请停掉进程（stop-demo.bat）或自行在代码里调用 Plugin#stop()。");
        return Out.ok().data(data);
    }

    /**
     * 组装单个任务视图：模块观测面只提供阶段与时间，触发方式与下次触发时间由 demo 自算。
     *
     * @param task 任务元数据
     * @param registered 已注册任务名集合
     * @param now 当前时刻（epoch 毫秒）
     * @return 任务视图
     */
    private Map<String, Object> taskView(DemoJobCatalog.Task task, Set<String> registered, long now) {
        DemoEventLog.Record last = DemoEventLog.lastOf(task.taskName());
        boolean running = last != null && DemoEventLog.PHASE_STARTED.equals(last.phase());
        long lastStartTime = 0L;
        long lastEndTime = 0L;
        if (last != null) {
            if (running) {
                lastStartTime = last.triggerTime();
            } else {
                // 终态事件的触发时间≈本次开始时刻；SKIPPED 时为原计划触发时刻（demo 侧近似）
                lastStartTime = last.triggerTime();
                lastEndTime = last.triggerTime();
            }
        }
        long next = task.nextFireTime(now, lastStartTime, lastEndTime);

        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("name", task.taskName());
        view.put("biz", task.biz());
        view.put("mode", task.mode().name());
        view.put("trigger", task.triggerText());
        view.put("rhythm", task.rhythmText());
        view.put("expression", task.expression());
        view.put("period", task.periodOrNull());
        view.put("delay", task.delayOrNull());
        view.put("initialDelay", Long.valueOf(task.initialDelay()));
        view.put("registered", Boolean.valueOf(registered.contains(task.taskName())));
        view.put("lastPhase", last == null ? null : last.phase());
        view.put("lastTriggerTime", last == null ? Long.valueOf(0L) : Long.valueOf(last.triggerTime()));
        view.put("lastTriggerAt", last == null ? "—" : DemoFormats.clockText(last.triggerTime()));
        view.put("running", Boolean.valueOf(running));
        view.put("nextFireTime", Long.valueOf(next));
        view.put("nextFireAt", DemoFormats.clockText(next));
        return view;
    }

    /**
     * 组装单条事件视图。
     *
     * @param record 事件记录
     * @return 事件视图
     */
    private Map<String, Object> recordView(DemoEventLog.Record record) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("seq", Long.valueOf(record.sequence()));
        view.put("taskName", record.taskName());
        view.put("phase", record.phase());
        view.put("triggerTime", Long.valueOf(record.triggerTime()));
        view.put("triggerAt", DemoFormats.clockText(record.triggerTime()));
        view.put("threadName", record.threadName());
        view.put("error", record.errorText());
        return view;
    }
}
