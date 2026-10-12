/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.miniapp;

import java.util.Objects;

/**
 * 小程序账号凭据（不可变值对象）。小程序无消息回调 token/aesKey，仅 appId + secret。
 */
public class MiniAppAccount {

    private final String appId;
    private final String secret;

    private MiniAppAccount(String appId, String secret) {
        this.appId = appId;
        this.secret = secret;
    }

    /**
     * fail-fast 构造：appId / secret 均非空
     */
    public static MiniAppAccount of(String appId, String secret) {
        requireNotBlank(appId, "appId");
        requireNotBlank(secret, "secret");
        return new MiniAppAccount(appId.trim(), secret.trim());
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    public String getAppId() {
        return appId;
    }

    public String getSecret() {
        return secret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MiniAppAccount)) {
            return false;
        }
        MiniAppAccount that = (MiniAppAccount) o;
        return appId.equals(that.appId) && secret.equals(that.secret);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, secret);
    }

    /** secret 不进 toString，防日志泄漏 */
    @Override
    public String toString() {
        return "MiniAppAccount{appId=" + appId + "}";
    }
}
