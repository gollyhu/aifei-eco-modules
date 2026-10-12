/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.cache;

/**
 * access_token / jsapi_ticket 缓存抽象。
 *
 * <p>key 为 appId，jsapi_ticket 复用同一实例并以 {@code jsapi_ticket:{appId}} 为前缀。
 * 生产多实例部署须替换为共享实现（如接入 aifei-cache），否则会互相挤掉 token 并撞微信配额。
 */
public interface AccessTokenCache {

    String get(String key);

    /**
     * @param expireAtMillis 绝对过期时间戳（由 SDK 计算 now + expiresIn*1000 - 5min）
     */
    void set(String key, String token, long expireAtMillis);

    void remove(String key);
}
