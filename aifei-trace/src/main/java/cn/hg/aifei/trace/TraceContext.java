/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

/**
 * 链路上下文持有器，以普通 ThreadLocal 承载当前线程的 TraceId 与 SpanId。
 *
 * <p>只做 set（null = 移除）/ get / clear，零业务语义；trim、回退、生成等语义由 TraceKit 承担，
 * 业务日常使用 TraceKit 即可。</p>
 *
 * <p>设计：</p>
 * <ol>
 *   <li>使用普通 ThreadLocal 而非 InheritableThreadLocal，避免线程池复用线程导致的 TraceId 串号。
 *       请求结束必须调用 clear() 清理，通常由 web.TraceHandler 在 finally 中保证；
 *       手动 init 的场景（如异步任务）由业务自行 try/finally 清理。</li>
 *   <li>ThreadLocal 不跨线程自动传播，线程池 / 异步场景需业务手动传递 TraceId，
 *       在子线程通过 TraceKit.init(traceId) 恢复。</li>
 * </ol>
 *
 * <p>本类型是全模块 ThreadLocal 的唯一持有点，不得在别处复制 ThreadLocal 状态。</p>
 */
public final class TraceContext {

    private static final ThreadLocal<String> traceIdTL = new ThreadLocal<String>();
    private static final ThreadLocal<String> spanIdTL = new ThreadLocal<String>();

    private TraceContext() {
    }

    /**
     * 获取当前线程绑定的 TraceId。
     *
     * @return 当前线程绑定的 TraceId，未绑定时返回 null
     */
    public static String getTraceId() {
        return traceIdTL.get();
    }

    /**
     * 获取当前线程绑定的 SpanId。
     *
     * @return 当前线程绑定的 SpanId，未绑定时返回 null
     */
    public static String getSpanId() {
        return spanIdTL.get();
    }

    /**
     * 绑定 TraceId。
     *
     * @param traceId 待绑定的 TraceId；传 null 等价于移除（ThreadLocal.remove），重复调用安全
     */
    public static void setTraceId(String traceId) {
        if (traceId == null) {
            traceIdTL.remove();
        } else {
            traceIdTL.set(traceId);
        }
    }

    /**
     * 绑定 SpanId。
     *
     * @param spanId 待绑定的 SpanId；传 null 等价于移除（ThreadLocal.remove），重复调用安全
     */
    public static void setSpanId(String spanId) {
        if (spanId == null) {
            spanIdTL.remove();
        } else {
            spanIdTL.set(spanId);
        }
    }

    /**
     * 清理当前线程的全部链路上下文（TraceId 与 SpanId），防止线程池复用串号；对未绑定线程调用幂等安全。
     */
    public static void clear() {
        traceIdTL.remove();
        spanIdTL.remove();
    }
}
