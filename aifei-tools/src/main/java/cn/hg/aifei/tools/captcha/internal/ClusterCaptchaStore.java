/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKit;

/**
 * 基于可选共享缓存的集群存储：同一 token 的答案可被多实例校验。
 *
 * <p>值格式为「答案#失败次数#绝对过期毫秒」，TTL 由绝对过期时刻换算为秒传给缓存；
 * 失败计数为读改写，不追求跨实例原子性（防枚举场景足够）。缓存不可用时收敛为
 * {@link cn.hg.aifei.tools.captcha.CaptchaException} 并保留根因。</p>
 */
public final class ClusterCaptchaStore implements CaptchaStore {

    private static final String CACHE_NAME = "aifei-tools-captcha";
    private static final String SEPARATOR = "#";

    private final Cache cache;
    private final int maxFailures;

    /**
     * 构造集群存储并取出主缓存实例。
     *
     * @param maxFailures 失败次数上限，须为正数
     * @throws cn.hg.aifei.tools.captcha.CaptchaException 共享缓存依赖缺失或未装配
     */
    public ClusterCaptchaStore(int maxFailures) {
        CaptchaDeps.requireCache();
        try {
            this.cache = CacheKit.use();
        } catch (RuntimeException e) {
            throw CaptchaErrors.storageFailed(e);
        }
        this.maxFailures = maxFailures;
    }

    @Override
    public void save(String token, String answer, long expireAt) {
        try {
            cache.put(CACHE_NAME, token, answer + SEPARATOR + 0 + SEPARATOR + expireAt, ttlSeconds(expireAt));
        } catch (RuntimeException e) {
            throw CaptchaErrors.storageFailed(e);
        }
    }

    @Override
    public String consume(String token) {
        String[] parts = read(token);
        if (parts == null) {
            return null;
        }
        int failures = parseFailures(parts);
        if (failures < 0 || failures >= maxFailures) {
            remove(token);
            return null;
        }
        return parts[0];
    }

    @Override
    public void fail(String token) {
        String[] parts = read(token);
        if (parts == null) {
            return;
        }
        int failures = parseFailures(parts);
        if (failures < 0) {
            remove(token);
            return;
        }
        failures++;
        long expireAt = parseExpireAt(parts);
        if (failures >= maxFailures || expireAt <= System.currentTimeMillis()) {
            remove(token);
            return;
        }
        try {
            cache.put(CACHE_NAME, token,
                    parts[0] + SEPARATOR + failures + SEPARATOR + expireAt, ttlSeconds(expireAt));
        } catch (RuntimeException e) {
            throw CaptchaErrors.storageFailed(e);
        }
    }

    @Override
    public void remove(String token) {
        try {
            cache.remove(CACHE_NAME, token);
        } catch (RuntimeException e) {
            throw CaptchaErrors.storageFailed(e);
        }
    }

    private String[] read(String token) {
        String value;
        try {
            value = cache.<String>get(CACHE_NAME, token);
        } catch (RuntimeException e) {
            throw CaptchaErrors.storageFailed(e);
        }
        if (value == null) {
            return null;
        }
        String[] parts = value.split(SEPARATOR, -1);
        if (parts.length < 3) {
            return null;
        }
        long expireAt = parseExpireAt(parts);
        if (expireAt <= System.currentTimeMillis()) {
            remove(token);
            return null;
        }
        return parts;
    }

    private static int parseFailures(String[] parts) {
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseExpireAt(String[] parts) {
        try {
            return Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int ttlSeconds(long expireAt) {
        long remaining = (expireAt - System.currentTimeMillis()) / 1000L;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, remaining));
    }
}
