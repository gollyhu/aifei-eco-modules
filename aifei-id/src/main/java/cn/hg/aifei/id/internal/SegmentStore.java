/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import java.time.Duration;

/**
 * 号段存储契约：把「一次原子领走一个号段」抽象成单个方法，隔离 {@code aifei-cache} 的具体类型。
 *
 * <p>返回值是<b>新号段的末端（含）</b>而不是长度：共享后端的一次原子自增天然给出「累计计数值」，
 * 号段即 {@code [返回值 - step + 1, 返回值]}。号段生成器据此推进游标，不需要再回读。</p>
 *
 * <p>实现允许抛运行时异常表示存储不可用；{@link SegmentIdGenerator} 会把它包装成携带键名与根因的
 * 模块异常。实现自身不负责回退检测——那属于号段生成器的唯一职责。</p>
 *
 * <p>非承诺 API。</p>
 */
public interface SegmentStore {

    /**
     * 领走一个新号段，返回其末端。
     *
     * @param key 号段键（{@code id.segment.key} 的取值），非空
     * @param step 号段长度，{@code 1..1000000}
     * @param ttl 号段计数器的有效期，每次取段顺带续期（闲置过期语义）
     * @return 新号段末端（含），号段即 {@code [返回值 - step + 1, 返回值]}
     */
    long nextSegmentMax(String key, int step, Duration ttl);
}
