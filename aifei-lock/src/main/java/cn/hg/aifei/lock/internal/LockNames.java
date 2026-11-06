/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.internal;

import java.util.UUID;

/**
 * 锁名规则、锁所在缓存名与 token 生成的唯一定义点。
 *
 * <p>非承诺 API。</p>
 */
public final class LockNames {

    /**
     * 锁键所在缓存名：保留分区，业务不得读写；清空它等于清空全部锁。
     */
    public static final String CACHE_NAME = "__LOCK__";

    /**
     * 锁名长度上限。
     */
    public static final int MAX_LENGTH = 128;

    /**
     * 禁止创建工具类实例。
     */
    private LockNames() {
    }

    /**
     * 校验锁名并原样返回。
     *
     * @param name 锁名
     * @return 传入的锁名
     * @throws IllegalArgumentException 为 {@code null}、为空、含空白或长度超过 128
     */
    public static String requireValid(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("lock name can not be blank");
        }
        if (name.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "lock name can not be longer than " + MAX_LENGTH + " chars");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                throw new IllegalArgumentException("lock name must not contain whitespace");
            }
        }
        return name;
    }

    /**
     * 生成一次持有的标识。
     *
     * @return UUID 字符串
     */
    public static String newToken() {
        return UUID.randomUUID().toString();
    }
}
