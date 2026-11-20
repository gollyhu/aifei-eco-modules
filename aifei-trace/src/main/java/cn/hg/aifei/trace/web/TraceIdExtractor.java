/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.web;

import cn.aifei.core.Input;

/**
 * TraceId 提取策略，供 web.TraceHandler 在请求进入时提取上游透传的 TraceId。
 *
 * <p>注意：cn.aifei.core.Input 公开接口只提供参数读取能力，没有 header 访问能力，
 * 跨服务透传（如 X-Trace-Id 请求头）需业务侧自定义 Input 实现暴露 header 后，
 * 在此返回提取结果。本模块不做任何「万能提取器」。</p>
 *
 * <p>语义：</p>
 * <ol>
 *   <li>无法提取时返回 null（返回 null 或空白时，TraceHandler 回退为生成新 TraceId）。</li>
 *   <li>本接口抛出的任何异常会被 web.TraceHandler 吞掉并回退为生成新 TraceId，
 *       trace 失败不影响业务请求。</li>
 * </ol>
 *
 * @param <I> 请求输入类型，须为框架 Input 的子类型（提取只读 Input，与 Output 无关）
 */
@FunctionalInterface
public interface TraceIdExtractor<I extends Input> {

    /**
     * 从请求中提取上游透传的 TraceId。
     *
     * @param input 当前请求的输入对象
     * @return 提取到的 TraceId；无法提取时返回 null（null / 空白由 TraceHandler 回退生成）
     */
    String extract(I input);
}
