/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

/**
 * TraceId 生成策略接口，可通过 TraceKit.setTraceIdGenerator(...) 替换默认实现。
 *
 * <p>generate() 的格式契约（javadoc 强约束，模块运行期不校验产出，替换方自担）：</p>
 * <ol>
 *   <li>返回值非空，不得返回 null；</li>
 *   <li>全局趋势唯一；</li>
 *   <li>仅含 [0-9a-f]，固定 32 位小写十六进制——保证日志检索、grep 与跨服务对齐有确定格式。</li>
 * </ol>
 *
 * <p>实现应无状态，或自行保证线程安全（默认实现 DefaultTraceIdGenerator 无状态）。</p>
 */
public interface TraceIdGenerator {

    /**
     * 生成一个新 TraceId。
     *
     * <p>契约：返回值非空、全局趋势唯一、仅含 [0-9a-f]、固定 32 位小写十六进制；
     * 违反格式契约的行为不被模块拦截，属实现方自身缺陷。</p>
     *
     * @return 新生成的 TraceId，永不为 null
     */
    String generate();
}
