/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.web;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.hg.aifei.trace.TraceKit;

/**
 * TraceHandler.
 *
 * <pre>
 * Web 接入点：请求进入时提取（或生成）TraceId 并绑定，请求结束在 finally 中清理，
 * 随后传递给链上 next Handler。本类是「提取异常吞并回退 + finally 清理」的唯一定义点。
 *
 * 语义：
 *  1: extractor 可为 null（纯生成模式），此时直接生成新 TraceId；
 *     setExtractor 传 null 同样等价纯生成模式。
 *  2: extractor.extract(input) 返回 null / 空白时，回退为生成新 TraceId（经 TraceKit.init 语义）。
 *  3: extractor.extract(input) 抛出任何异常时，吞掉并回退为生成新 TraceId，
 *     任何路径下都不让 trace 失败影响业务请求。
 *  4: 无论下游 Handler 成功返回还是抛出异常，均在 finally 中执行 TraceKit.clear()，
 *     杜绝线程池复用线程导致的 TraceId 污染；下游异常原样向上传播（不捕获、不包装）。
 *  5: next == null 时安全结束（框架 Handler 的 next 字段本类可直接访问）。
 *
 * 用法（Handler 链顺序即语义，TraceHandler 必须第一个 addHandler，才能覆盖全部下游 Handler）：
 *   settings.addHandler(new TraceHandler<InputImpl, OutputImpl>(input -> ...提取...));
 *
 * 静态资源请求在 undertow IO 线程即被资源处理拦截，不进 Dispatcher / Handler 链，
 * 天然不产生 TraceId，无需在 trace 侧排除。
 * </pre>
 *
 * @param <I> 请求输入类型，须为框架 Input 的子类型
 * @param <O> 请求输出类型，须为框架 Output 的子类型
 */
public class TraceHandler<I extends Input, O extends Output> extends Handler<I, O> {

    /**
     * TraceId 提取策略；null 表示纯生成模式
     */
    private TraceIdExtractor<I> extractor;

    /**
     * 构造纯生成模式的处理器：不提取，直接生成新 TraceId。
     */
    public TraceHandler() {
    }

    /**
     * 构造提取模式的处理器。
     *
     * @param extractor TraceId 提取策略；传 null 等价纯生成模式
     */
    public TraceHandler(TraceIdExtractor<I> extractor) {
        this.extractor = extractor;
    }

    /**
     * 设置 TraceId 提取策略，支持链式调用。
     *
     * @param extractor TraceId 提取策略；传 null 等价纯生成模式
     * @return 自身（this），支持链式调用
     */
    public TraceHandler<I, O> setExtractor(TraceIdExtractor<I> extractor) {
        this.extractor = extractor;
        return this;
    }

    /**
     * 提取（或生成）并绑定 TraceId，透传链上后续 Handler，结束后在 finally 中清理链路上下文。
     *
     * <p>执行序：extractor 非 null 时提取（异常吞掉回退）→ TraceKit.init 绑定（含空白回退语义）
     * → next 非 null 时透传下游 → finally 中 TraceKit.clear()。</p>
     *
     * <p>异常语义：下游 Handler 抛出的异常原样向上传播（finally 照常清理）；
     * extractor 抛出的异常被吞掉并回退为生成新 TraceId。</p>
     *
     * @param path  请求路径
     * @param input 请求输入对象
     * @param output 请求输出对象
     * @throws Exception 下游 Handler 抛出的异常原样透传（extractor 的异常不会从此方法抛出）
     */
    @Override
    public void handle(String path, I input, O output) throws Exception {
        try {
            String traceId = null;
            if (extractor != null) {
                try {
                    traceId = extractor.extract(input);
                } catch (Exception e) {
                    // 提取失败不影响业务请求，traceId 保持 null，走回退生成分支
                }
            }
            TraceKit.init(traceId);

            if (next != null) {
                next.handle(path, input, output);
            }
        } finally {
            TraceKit.clear();
        }
    }
}
