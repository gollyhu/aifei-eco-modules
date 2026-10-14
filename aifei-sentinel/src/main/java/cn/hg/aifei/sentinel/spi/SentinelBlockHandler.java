/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.spi;

import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * 被限流/熔断命中时的降级策略：返回 action 的返回值，消费方返回自己的 Out.fail 或抛异常。
 * 位于 spi 层，不依赖 aifei 内核任何类型。
 */
@FunctionalInterface
public interface SentinelBlockHandler {

    Object handle(BlockException e);
}
