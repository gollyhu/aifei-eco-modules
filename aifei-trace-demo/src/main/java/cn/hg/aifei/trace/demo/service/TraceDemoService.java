/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.demo.service;

import cn.aifei.core.Path;
import cn.aifei.log.Log;
import cn.hg.aifei.trace.DefaultTraceIdGenerator;
import cn.hg.aifei.trace.TraceKit;
import cn.hg.aifei.trace.demo.DemoAppConfig;
import cn.hg.aifei.trace.demo.arch.Out;
import cn.hg.aifei.trace.demo.console.ConsoleLog;
import cn.hg.aifei.trace.web.TraceHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示 Service：覆盖 aifei-trace 的 4 个演示场景。
 *
 * <p>所有端点均为 GET，返回 {@link Out}（HTTP 恒 200，业务语义由 code 承载，0 为成功）。</p>
 *
 * <p>场景与端点对照（设计文档 §13）：</p>
 * <ul>
 *   <li>场景① {@code /trace/demo/current}：当前请求 traceId / spanId 实时展示（TraceHandler 绑定 + finally 清理）</li>
 *   <li>场景② {@code /trace/demo/log}：页面触发业务日志，验证 {@code [traceId] } 前缀贯通（TraceLogFactory 装配）</li>
 *   <li>场景③ {@code /trace/demo/async}：线程池手动传递（TraceKit.init(parentTraceId) + finally clear）</li>
 *   <li>场景④ {@code /trace/demo/extract}：TraceIdExtractor 从请求头 X-Trace-Id 提取 / 缺失回退生成</li>
 * </ul>
 */
@Path("/trace/demo")
public class TraceDemoService {

    /**
     * 异步任务等待上限（毫秒）；超出视为演示失败，防止页面挂死。
     */
    private static final long ASYNC_TIMEOUT_MILLIS = 5000L;

    /**
     * 经全局 TraceLogFactory 装饰后的 Log：实际实例为 TraceLog 包装 ConsoleLog，
     * 打印时自动携带 {@code [traceId] } 前缀。routes 扫描发生在 setLogFactory 之后，取到的必是装饰器。
     */
    private static final Log log = Log.get(TraceDemoService.class);

    /**
     * 演示用单线程池：刻意使用普通 ThreadLocal 的语义（TraceContext 不继承），跨线程必须手动传递。
     */
    private static final ExecutorService ASYNC_POOL = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "trace-demo-worker-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * 演示页视图：由 Enjoy 从 {@code webapp/view/demo/index.html} 渲染（模板根为 {@code webapp/view}）。
     *
     * @return 模板名，必须以 {@code /} 开头
     */
    @Path("page")
    public String page() {
        return "/demo/index.html";
    }

    /**
     * 装配快照：日志工厂链、Handler 链、生成器与提取头等静态事实，供页面展示装配结果。
     *
     * @return 装配快照
     */
    @Path("state")
    public Out state() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("traceHeader", DemoAppConfig.TRACE_HEADER);
        data.put("generatorClass", TraceKit.getTraceIdGenerator().getClass().getName());
        data.put("logFactoryChain", "TraceLogFactory -> ConsoleLogFactory（System.out，无日志绑定）");
        data.put("handlerChain", "TraceHandler（第一位，提取头 " + DemoAppConfig.TRACE_HEADER
                + "）-> IoHandler");
        data.put("threadPoolThread", ASYNC_POOL.toString());
        return Out.ok().data(data);
    }

    /**
     * 场景①：当前请求的 traceId / spanId。traceId 由第一位 Handler {@link TraceHandler} 绑定，
     * 每次刷新页面（新请求）都会变化；同一请求内多次调用本端点返回同值。
     *
     * @return 当前线程链路上下文
     */
    @Path("current")
    public Out current() {
        Map<String, Object> data = snapshotOfCurrentThread();
        data.put("note", "traceId 由第一个 Handler TraceHandler 绑定；每次刷新页面（新请求）都会生成新值");
        return Out.ok().data(data);
    }

    /**
     * 场景①补充：开启新 Span。startSpan() 覆盖旧 spanId 且不影响 traceId，同一请求内两次调用 spanId 不同。
     *
     * @return 开启新 span 后的链路上下文
     */
    @Path("span")
    public Out span() {
        String spanId = TraceKit.startSpan();
        Map<String, Object> data = snapshotOfCurrentThread();
        data.put("newSpanId", spanId);
        data.put("note", "startSpan() 覆盖旧 spanId、不影响 traceId（单层 span，不做 Span 树）");
        return Out.ok().data(data);
    }

    /**
     * 场景②：触发业务日志。Service 里的 log 已被全局 TraceLogFactory 装饰，
     * 打印时自动拼 {@code [traceId] } 前缀（前缀在打印时刻读取链路上下文）。
     *
     * @return 写入的日志行数 + 控制台输出样本（ConsoleLog 环形缓冲回显）
     */
    @Path("log")
    public Out writeLog() {
        log.info("页面触发了一条业务日志：TraceLog 在打印时刻自动拼装 [traceId] 前缀");
        log.info("带参数的格式化日志：userId={}, amount={}", 42, "￥199.00");
        log.warn("这是一条 WARN 级日志，前缀同样贯通");
        log.info(() -> "Supplier 懒求值日志：前缀在打印时刻读取，异步日志也能取到打印线程的 traceId");

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("writtenCount", 4);
        data.put("traceId", TraceKit.getTraceId());
        data.put("consoleLines", ConsoleLog.recentLines());
        data.put("note", "以上为 ConsoleLog 环形缓冲回显；启动 demo 的控制台（System.out）同步可见这些行");
        return Out.ok().data(data);
    }

    /**
     * 清空控制台输出样本缓冲，便于下一次演示从零观察。
     *
     * @return 确认信息
     */
    @Path("log/clear")
    public Out clearLog() {
        ConsoleLog.clearHistory();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("consoleLines", ConsoleLog.recentLines());
        data.put("note", "控制台输出样本缓冲已清空");
        return Out.ok().data(data);
    }

    /**
     * 场景③：线程池手动传递。提交前在请求线程捕获 {@code parentTraceId}，
     * 任务体内 {@code TraceKit.init(parentTraceId)} 恢复链路、finally {@code TraceKit.clear()} 防池线程复用串号。
     *
     * <p>预期结果：childTraceId == parentTraceId（手动传递贯通）；子线程 spanId 未传递为 null（ThreadLocal
     * 刻意不继承）；clear 后子线程 traceId 回到 null（防串号）。</p>
     *
     * @return 父子线程 traceId 对照与断言结果
     */
    @Path("async")
    public Out async() {
        final String parentTraceId = TraceKit.getTraceId();
        final String parentSpanId = TraceKit.getSpanId();
        final String requestThreadName = Thread.currentThread().getName();

        Future<Map<String, Object>> future = ASYNC_POOL.submit(() -> {
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            String childThreadId;
            try {
                // 手动传递：请求线程捕获的 traceId 以变量捕获方式随 lambda 进入任务（§8.3 指引）
                TraceKit.init(parentTraceId);
                childThreadId = TraceKit.getTraceId();
                log.info("子线程日志：TraceKit.init(parentTraceId) 后前缀贯通，与父线程同链路");
            } finally {
                TraceKit.clear();
            }
            String afterClear = TraceKit.getTraceId();

            r.put("childTraceId", childThreadId);
            r.put("sameTraceId", parentTraceId != null && parentTraceId.equals(childThreadId));
            r.put("childSpanId", TraceKit.getSpanId());
            r.put("childSpanIdNull", TraceKit.getSpanId() == null);
            r.put("childThreadIdAfterClear", afterClear);
            r.put("clearedProperly", afterClear == null);
            r.put("childThreadName", Thread.currentThread().getName());
            return r;
        });

        Map<String, Object> child;
        try {
            child = future.get(ASYNC_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return Out.fail("异步任务执行失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("parentTraceId", parentTraceId);
        data.put("parentSpanId", parentSpanId);
        data.put("requestThreadName", requestThreadName);
        data.putAll(child);
        data.put("consoleLines", ConsoleLog.recentLines());
        data.put("note", "手动传递三断言：childTraceId == parentTraceId；子线程 spanId 为 null（不继承）；clear 后回到 null");
        return Out.ok().data(data);
    }

    /**
     * 场景④：提取演示。页面携带自定义 {@code X-Trace-Id} 请求头（或空白值、或不携带）调用本端点：
     * 第一位 Handler {@code TraceHandler} 经 extractor 提取 —— 非空白则原样绑定（trim 后）；
     * 空白 / 缺失 / 提取异常一律回退生成新 traceId。
     *
     * @return 本请求最终生效的 traceId（页面据此判断「提取贯通」还是「回退生成」）
     */
    @Path("extract")
    public Out extract() {
        Map<String, Object> data = snapshotOfCurrentThread();
        data.put("traceHeader", DemoAppConfig.TRACE_HEADER);
        data.put("generatorSample", DefaultTraceIdGenerator.INSTANCE.generate());
        data.put("note", "携带非空白 X-Trace-Id → 提取贯通（trim 后原样绑定）；空白/缺失 → 回退生成 32 位小写十六进制");
        return Out.ok().data(data);
    }

    // ===== 内部工具 =====================================================================

    /**
     * 当前线程链路上下文快照。
     */
    private Map<String, Object> snapshotOfCurrentThread() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("traceId", TraceKit.getTraceId());
        data.put("spanId", TraceKit.getSpanId());
        data.put("threadName", Thread.currentThread().getName());
        data.put("traceIdLength", TraceKit.getTraceId() == null ? 0 : TraceKit.getTraceId().length());
        return data;
    }
}
