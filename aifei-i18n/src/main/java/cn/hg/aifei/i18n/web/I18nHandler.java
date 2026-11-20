/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.web;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.hg.aifei.i18n.internal.LocaleContext;
import cn.hg.aifei.i18n.internal.LocaleResolver;

/**
 * Web 入口 Locale 绑定处理器（设计 D-12 / D-13 / §5.7，形态对齐 aifei-trace 的 TraceHandler）。
 *
 * <p>执行序：extractor 取原始头（异常吞掉，不绑定）→ {@code LocaleResolver.parseAcceptLanguage} 解析
 * 首个受支持语种 → 成功则 {@code LocaleContext.set} → next 透传 → <b>finally 无条件清理</b>。</p>
 *
 * <p>下游异常原样向上传播（不捕获、不包装），finally 照常清理，杜绝线程池复用串语言；
 * 本类必须第一个 addHandler，静态资源请求不进 Handler 链、天然无绑定。</p>
 *
 * @param <I> 请求输入类型
 * @param <O> 请求输出类型
 */
public class I18nHandler<I extends Input, O extends Output> extends Handler<I, O> {

    private volatile LocaleExtractor<I> extractor;

    /**
     * 无参构造：不提取请求头，落默认 locale。
     */
    public I18nHandler() {
    }

    /**
     * 带提取器构造。
     *
     * @param extractor 头提取器；{@code null} 等价不提取
     */
    public I18nHandler(LocaleExtractor<I> extractor) {
        this.extractor = extractor;
    }

    /**
     * 设置提取器，支持链式调用。
     *
     * @param extractor 提取器；{@code null} 等价不提取
     * @return 自身
     */
    public I18nHandler<I, O> setExtractor(LocaleExtractor<I> extractor) {
        this.extractor = extractor;
        return this;
    }

    @Override
    public void handle(String path, I input, O output) throws Exception {
        try {
            String raw = null;
            if (extractor != null) {
                try {
                    raw = extractor.acceptLanguage(input);
                } catch (Exception e) {
                    // 提取失败不影响业务请求：raw 保持 null，不绑定、落默认 locale
                }
            }
            String locale = LocaleResolver.parseAcceptLanguage(raw);
            if (locale != null) {
                LocaleContext.set(locale);
            }
            if (next != null) {
                next.handle(path, input, output);
            }
        } finally {
            LocaleContext.clear();
        }
    }
}
