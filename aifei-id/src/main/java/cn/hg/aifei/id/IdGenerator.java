/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

/**
 * ID 生成策略契约：业务代码只依赖本接口，不依赖雪花或号段的具体实现，切换策略只改配置。
 *
 * <p><b>返回值</b>：恒正（{@code > 0}）——雪花恒为正（符号位固定 0），号段从 1 起。</p>
 *
 * <p><b>唯一性</b>：只在配置的域内唯一。雪花 = 「同 EPOCH + 同 workerId」空间内的全局唯一；
 * 号段 = 同 {@code id.segment.key} 域内唯一，不同 key 的号段序列互不相干。</p>
 *
 * <p><b>趋势</b>：同一实例严格递增；但不保证连续——雪花在毫秒边界跳跃、号段跨实例有空洞、
 * 进程崩溃会丢弃未用完的号段。</p>
 *
 * <p><b>线程安全</b>：实现必须线程安全，可被任意多线程并发调用。</p>
 *
 * <p><b>失败</b>：运行期不可用（时钟回拨超阈、号段存储失败、workerId 租约丢失等）时抛
 * {@link IdException}；永不返回 {@code 0}、永不降级。方法不声明 {@code throws}（{@link IdException}
 * 是非受检异常），以免污染使用方签名。</p>
 *
 * <p><b>冻结</b>：雪花返回值的位语义属已发布数据格式（1 符号位 + 41 毫秒时间戳 + 10 workerId +
 * 12 序列号），不得在后续版本变更。</p>
 *
 * <p>取用方式：{@link IdKit}（主通道），或 {@code @Inject IdGenerator}（{@code IdPlugin} 启动时注册的
 * 单例）。业务可在自身单测中自行提供实现——本接口无装配依赖。</p>
 */
public interface IdGenerator {

    /**
     * 取下一个 ID。
     *
     * @return 下一个 ID，恒正且在同一实例上严格递增
     * @throws IdException 模块运行期不可用：时钟回拨超阈、时钟早于 EPOCH、时间戳溢出、等待时钟被中断、
     *                     号段取段失败、号段回退、workerId 租约丢失
     */
    long nextId();
}
