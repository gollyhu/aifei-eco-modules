/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

/**
 * 链路追踪语义门面：init 的 trim 与空白回退生成、生成器替换入口、startSpan 覆盖语义均由本类唯一定义。
 *
 * <p>命名例外说明：本类无装配生命周期、任何时刻调用都成立（未做任何配置时 getTraceId() 返回 null、
 * init() 直接生成），永不抛「未装配」类异常；属 Kit 判据的显式例外（有状态门面，对齐 LogKit 形态）。</p>
 *
 * <p>常用用法：</p>
 * <ol>
 *   <li>请求进入时绑定 TraceId（由 web.TraceHandler 自动完成，业务一般无需手动调用）：
 *       {@code TraceKit.init(extractedTraceId);}（提取为 null / 空白时自动生成）</li>
 *   <li>业务代码获取当前 TraceId：{@code String traceId = TraceKit.getTraceId();}</li>
 *   <li>线程池 / 异步场景在子线程恢复链路（处理完务必 try/finally clear）：
 *       {@code TraceKit.init(parentTraceId);}</li>
 *   <li>请求结束清理（由 web.TraceHandler 在 finally 中保证）：{@code TraceKit.clear();}</li>
 * </ol>
 */
public final class TraceKit {

    /**
     * 当前 TraceId 生成器，初始为 DefaultTraceIdGenerator.INSTANCE。
     * internal 细节，唯一写入点是 setTraceIdGenerator，不得升为 public；
     * 运行期替换无锁、不保证原子可见性，建议仅在启动期（AppConfig）替换。
     */
    static TraceIdGenerator idGenerator = DefaultTraceIdGenerator.INSTANCE;

    private TraceKit() {
    }

    /**
     * 获取当前 TraceId 生成器。
     *
     * @return 当前生成器，初始为 DefaultTraceIdGenerator.INSTANCE，经 setTraceIdGenerator 替换后返回替换值
     */
    public static TraceIdGenerator getTraceIdGenerator() {
        return idGenerator;
    }

    /**
     * 替换 TraceId 生成器，替换实现需满足 TraceIdGenerator 的格式契约（模块不校验产出）。
     *
     * <p>建议仅在启动期调用；运行期替换无锁、不保证原子可见性。</p>
     *
     * @param generator 新的生成器实现，不得为 null
     * @throws IllegalArgumentException generator 为 null 时抛出
     */
    public static void setTraceIdGenerator(TraceIdGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator can not be null.");
        }
        TraceKit.idGenerator = generator;
    }

    /**
     * 生成并绑定新 TraceId，等价 init(null)。
     *
     * @return 最终生效的 TraceId
     */
    public static String init() {
        return init(null);
    }

    /**
     * 绑定 TraceId：traceId 先 trim，trim 后非空白则原样绑定；为 null / 空白则回退调用生成器产出新 TraceId 并绑定。
     *
     * @param traceId 待绑定的 TraceId，可为 null 或空白（此时走回退生成分支）
     * @return 最终生效的 TraceId（trim 后的原值，或回退生成的新值）
     */
    public static String init(String traceId) {
        String ret = traceId != null && traceId.trim().length() != 0 ? traceId.trim() : idGenerator.generate();
        TraceContext.setTraceId(ret);
        return ret;
    }

    /**
     * 生成并绑定新 SpanId。同一请求内多次调用覆盖旧值，不做 Span 树；不影响已绑定的 TraceId。
     *
     * @return 最终生效的 SpanId
     */
    public static String startSpan() {
        String spanId = idGenerator.generate();
        TraceContext.setSpanId(spanId);
        return spanId;
    }

    /**
     * 获取当前线程绑定的 TraceId。
     *
     * @return 当前线程绑定的 TraceId，未绑定时返回 null（不抛异常）
     */
    public static String getTraceId() {
        return TraceContext.getTraceId();
    }

    /**
     * 获取当前线程绑定的 SpanId。
     *
     * @return 当前线程绑定的 SpanId，未绑定时返回 null（不抛异常）
     */
    public static String getSpanId() {
        return TraceContext.getSpanId();
    }

    /**
     * 清理当前线程的全部链路上下文（TraceId 与 SpanId）；重复调用安全。
     */
    public static void clear() {
        TraceContext.clear();
    }
}
