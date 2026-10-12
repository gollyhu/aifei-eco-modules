/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

/**
 * 启动期排他判定所需的能力接口：实现类声明自己所在存储的唯一名。非承诺 API，仅供装配使用。
 *
 * <p>唯一名属<b>装配期信息</b>，因此不经 {@code Cache} 接口暴露。两个实例冲突的判据是「唯一名相同
 * 并且 {@code namespace} 相同」，判重发生在 {@code CacheManager#register}。</p>
 *
 * <p>「不参与排他判定」由<b>不实现本接口</b>表达（进程内后端各自独占存储）。实现类应返回非空且
 * 非空白的字符串（jedis 为 {@code host:port/database}）；为防御起见，返回 {@code null} 或空白同样
 * 视为不参与排他判定。</p>
 */
public interface UniqueNameAware {

    /**
     * 返回该实例所在存储的唯一名，用于启动期排他判定。
     *
     * <p>同一存储的两个实例必须返回相等的字符串，不同存储必须返回不相等的字符串。</p>
     *
     * @return 唯一名
     */
    String uniqueName();
}
