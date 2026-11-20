/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

/**
 * 验证码答案存储接口：进程内 LRU 与可选集群存储共用同一契约。
 *
 * <p>读答案（{@link #consume}）与失败计数（{@link #fail}）分离，保证错误答案不消费 token、
 * 正确答案才一次性删除；TTL 到期与失败超限由实现自行判定并返回未命中。</p>
 */
public interface CaptchaStore {

    /**
     * 保存答案及绝对过期时刻。
     *
     * @param token 令牌
     * @param answer 答案
     * @param expireAt 绝对过期毫秒时间戳
     */
    void save(String token, String answer, long expireAt);

    /**
     * 读取仍有效（存在、未过期、失败次数未超上限）的答案，不删除条目。
     *
     * @param token 令牌
     * @return 答案；无效或未命中时返回 null
     */
    String consume(String token);

    /**
     * 记录一次校验失败；达到上限时实现应使该 token 失效。
     *
     * @param token 令牌
     */
    void fail(String token);

    /**
     * 校验成功后一次性删除答案。默认空实现仅便于最小桩实现编译；
     * 进程内与集群两种真实存储均覆盖此方法。
     *
     * @param token 令牌
     */
    default void remove(String token) {
    }
}
