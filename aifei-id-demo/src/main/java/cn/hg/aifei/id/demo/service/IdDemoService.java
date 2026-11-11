/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo.service;

import cn.aifei.core.Path;
import cn.hg.aifei.id.IdKit;
import cn.hg.aifei.id.demo.DemoAppConfig;
import cn.hg.aifei.id.demo.arch.Out;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示 Service：覆盖 aifei-id 的 4 个演示场景。
 *
 * <p>所有端点均为 GET，HTTP 状态恒 200，业务语义由 {@code code} 承载（{@code 0} 为成功），
 * 信封为 {@code { code, message, data }}。</p>
 *
 * <p>场景与端点对照（设计文档 §13）：</p>
 * <ul>
 *   <li>场景① {@code /id/demo/next}：单号生成（{@code IdKit.nextIdStr()}，<b>字符串</b>，
 *       19 位 long 在 JS 中会丢精度）</li>
 *   <li>场景② {@code /id/demo/batch}：多线程并发生成 + 去重计数（{@code duplicated} 必须为 0）</li>
 *   <li>场景③ {@code /id/demo/segment}：号段耗尽与换段过程（段边界由 demo 按 ID 值与自身 step 自算）</li>
 *   <li>场景④ {@code /id/demo/state}：装配状态（{@code installed} 由 {@code IdKit} 探测，其余取 demo 自身配置）</li>
 * </ul>
 *
 * <p><b>边界</b>：workerId / 段边界 / 策略等展示值一律由 demo <b>自算或取自自身配置</b>，
 * 不是模块能力（aifei-id 无状态查询 API，设计决策 D1）。</p>
 */
@Path("/id/demo")
public class IdDemoService {

    /**
     * 批量端点缺省生成数量（设计文档 §13 示例值）。
     */
    private static final int DEFAULT_BATCH_SIZE = 2000;

    /**
     * 号段端点缺省生成数量（设计文档 §13 示例值）。
     */
    private static final int DEFAULT_SEGMENT_SIZE = 450;

    /**
     * 单次请求允许生成的上限，防止演示参数写错把服务打满。
     */
    private static final int MAX_SIZE = 200000;

    /**
     * 并发批量生成的线程数上限（demo 固定值；不随 CPU 核数变化，便于说明并发下的唯一性）。
     */
    private static final int BATCH_THREADS = 8;

    /**
     * 已生成过的 ID 集合：供 {@code next} 端点判定 {@code firstSeen}（同一个 ID 不会被生成两次，
     * 故首次必为 true；重复出现即说明模块有重号 bug）。
     */
    private static final ConcurrentHashMap<String, Boolean> SEEN = new ConcurrentHashMap<String, Boolean>();

    /**
     * 演示页视图：由 Enjoy 从 {@code webapp/view/id/index.html} 渲染（模板根为 {@code webapp/view}）。
     *
     * <p>静态资源目录由前端负责落盘；页面未就位时本端点渲染失败，属预期。</p>
     *
     * @return 模板名，必须以 {@code /} 开头
     */
    @Path("page")
    public String page() {
        return "/id/index.html";
    }

    /**
     * 场景①：单号生成。
     *
     * @return {@code data} 为 {@code { id: 字符串形态, firstSeen: 本次是否首次出现 }}
     */
    @Path("next")
    public Out next() {
        // 必须用字符串形态：19 位 long 超出 JS Number.MAX_SAFE_INTEGER（2^53），前端解析会静默丢精度
        String id = IdKit.nextIdStr();
        boolean firstSeen = SEEN.putIfAbsent(id, Boolean.TRUE) == null;

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", id);
        data.put("firstSeen", firstSeen);
        return Out.ok().data(data);
    }

    /**
     * 场景②：多线程并发生成 + 去重计数。
     *
     * <p>把 {@code size} 个 ID 切成至多 {@link #BATCH_THREADS} 份并发生成，共享一个
     * {@code ConcurrentHashMap} 去重：{@code duplicated} 恒应为 0（雪花在同一 workerId 下不重号；
     * 号段在单实例内不重号）。</p>
     *
     * @param size 生成数量，缺省 {@value #DEFAULT_BATCH_SIZE}，上限 {@value #MAX_SIZE}
     * @return {@code data} 为 {@code { size, unique, duplicated, first, last, costMillis }}
     */
    @Path("batch")
    public Out batch(Integer size) {
        final int total = normalize(size, DEFAULT_BATCH_SIZE);
        final int threads = Math.min(BATCH_THREADS, total);
        final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<String, Boolean>();
        final AtomicInteger duplicated = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "id-demo-batch-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        List<String> ids = new ArrayList<String>(total);
        long begin = System.currentTimeMillis();
        try {
            List<Future<List<String>>> futures = new ArrayList<Future<List<String>>>(threads);
            int base = total / threads;
            int remainder = total % threads;
            for (int i = 0; i < threads; i++) {
                final int count = base + (i < remainder ? 1 : 0);
                futures.add(pool.submit(new Callable<List<String>>() {
                    @Override
                    public List<String> call() {
                        List<String> part = new ArrayList<String>(count);
                        for (int j = 0; j < count; j++) {
                            String id = IdKit.nextIdStr();
                            if (seen.putIfAbsent(id, Boolean.TRUE) != null) {
                                duplicated.incrementAndGet();
                            }
                            part.add(id);
                        }
                        return part;
                    }
                }));
            }
            for (Future<List<String>> future : futures) {
                ids.addAll(future.get());
            }
        } catch (Exception e) {
            return Out.fail("并发生成失败: " + messageOf(e));
        } finally {
            pool.shutdown();
        }

        long costMillis = System.currentTimeMillis() - begin;

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("size", ids.size());
        data.put("unique", seen.size());
        data.put("duplicated", duplicated.get());
        data.put("first", ids.isEmpty() ? null : ids.get(0));
        data.put("last", ids.isEmpty() ? null : ids.get(ids.size() - 1));
        data.put("costMillis", costMillis);
        return Out.ok().data(data);
    }

    /**
     * 场景③：号段耗尽与换段过程。
     *
     * <p>连续生成 {@code size} 个 ID，按 demo 自身已知的 {@code step} 把每个 ID 归到
     * {@code index = (id - 1) / step} 号段，统计每段实际用掉的个数 —— 于是「耗尽 → 取段 → 继续」
     * 在 {@code segments} 里表现为按 {@code index} 升序的多行。</p>
     *
     * @param size 生成数量，缺省 {@value #DEFAULT_SEGMENT_SIZE}，上限 {@value #MAX_SIZE}
     * @return {@code data} 为 {@code { step, generated, segments: [{index, start, end, count}], currentSegment }}
     */
    @Path("segment")
    public Out segment(Integer size) {
        int total = normalize(size, DEFAULT_SEGMENT_SIZE);
        int step = DemoAppConfig.segmentStep();
        if (step < 1) {
            step = 1;
        }

        // 按 index 升序聚合：key = 段序号，value = 本次在该段内生成的个数
        Map<Long, Integer> counts = new TreeMap<Long, Integer>();
        long lastId = 0L;
        for (int i = 0; i < total; i++) {
            long id = IdKit.nextId();
            lastId = id;
            Long index = Long.valueOf((id - 1L) / step);
            Integer count = counts.get(index);
            counts.put(index, count == null ? Integer.valueOf(1) : Integer.valueOf(count.intValue() + 1));
        }

        // 段序号与段边界都是 ID 值：一律以字符串下发，19 位 long 在 JS 中会丢精度（设计 §13「ID 全程以字符串展示」）
        List<Map<String, Object>> segments = new ArrayList<Map<String, Object>>(counts.size());
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            long index = entry.getKey().longValue();
            Map<String, Object> segment = new LinkedHashMap<String, Object>();
            segment.put("index", Long.toString(index));
            segment.put("start", Long.toString(index * step + 1L));
            segment.put("end", Long.toString(index * step + step));
            segment.put("count", entry.getValue());
            segments.add(segment);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("step", step);
        data.put("generated", total);
        data.put("segments", segments);
        data.put("currentSegment", total == 0 ? null : Long.toString((lastId - 1L) / step));
        return Out.ok().data(data);
    }

    /**
     * 场景④：装配状态。
     *
     * <p>{@code installed} 用「调用 {@code IdKit.nextId()} 是否抛 {@code IllegalStateException}」探测
     * （未装配或已停机即 false）；策略、workerId 等取 demo 自身配置 —— aifei-id 不提供状态查询 API，
     * 这些值不是模块能力。</p>
     *
     * @return {@code data} 为 {@code { installed, strategy, workerSource, workerId, segmentStore, step }}
     */
    @Path("state")
    public Out state() {
        boolean installed = true;
        try {
            IdKit.nextId();
        } catch (IllegalStateException e) {
            installed = false;
        }

        // workerId / workerSource 只对雪花策略有意义；号段策略下模块不读这两个键，故置 null（JSON 中省略该键）
        boolean snowflake = "snowflake".equals(DemoAppConfig.strategy());

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("installed", installed);
        data.put("strategy", DemoAppConfig.strategy());
        data.put("workerSource", snowflake ? DemoAppConfig.workerSource() : null);
        data.put("workerId", snowflake ? DemoAppConfig.workerId() : null);
        data.put("segmentStore", DemoAppConfig.segmentStore());
        data.put("step", DemoAppConfig.segmentStep());
        return Out.ok().data(data);
    }

    // ===== 内部工具 =====================================================================

    /**
     * 归一化演示数量：缺省 / 非正数回退默认值，超出上限则截断。
     *
     * @param size         请求参数
     * @param defaultValue 默认值
     * @return 合法数量（{@code 1..MAX_SIZE}）
     */
    private static int normalize(Integer size, int defaultValue) {
        if (size == null || size.intValue() < 1) {
            return defaultValue;
        }
        return Math.min(size.intValue(), MAX_SIZE);
    }

    /**
     * 取异常的可读消息；{@code ExecutionException} 时下钻到被包装的真实原因。
     *
     * @param e 异常
     * @return 消息文本，不会为 null
     */
    private static String messageOf(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getName() : message;
    }
}
