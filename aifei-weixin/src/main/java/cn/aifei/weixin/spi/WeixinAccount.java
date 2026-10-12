/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.spi;

import java.util.Objects;

/**
 * 公众号账号凭据（不可变值对象）。
 *
 * <p>明文模式只使用 appId/secret/token；安全模式额外使用 43 字符 EncodingAESKey。
 */
public class WeixinAccount {

    private final String appId;
    private final String secret;
    private final String token;
    private final String aesKey;

    private WeixinAccount(String appId, String secret, String token, String aesKey) {
        this.appId = appId;
        this.secret = secret;
        this.token = token;
        this.aesKey = aesKey;
    }

    /**
     * fail-fast 构造：appId/token 非空；aesKey 为 null/空（明文模式）或 43 字符（安全模式）
     */
    public static WeixinAccount of(String appId, String secret, String token, String aesKey) {
        requireNotBlank(appId, "appId");
        requireNotBlank(token, "token");

        String normalizedAesKey = isBlank(aesKey) ? null : aesKey.trim();
        if (normalizedAesKey != null && normalizedAesKey.length() != 43) {
            throw new IllegalArgumentException("aesKey 必须为 43 字符的 EncodingAESKey（明文模式请传 null）");
        }

        return new WeixinAccount(appId.trim(), secret, token.trim(), normalizedAesKey);
    }

    private static void requireNotBlank(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * aesKey 非空即为安全模式（消息加密）
     */
    public boolean isSafeMode() {
        return aesKey != null;
    }

    public String getAppId() {
        return appId;
    }

    public String getSecret() {
        return secret;
    }

    public String getToken() {
        return token;
    }

    public String getAesKey() {
        return aesKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WeixinAccount)) {
            return false;
        }
        WeixinAccount that = (WeixinAccount) o;
        return appId.equals(that.appId)
                && Objects.equals(secret, that.secret)
                && token.equals(that.token)
                && Objects.equals(aesKey, that.aesKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, secret, token, aesKey);
    }

    @Override
    public String toString() {
        return "WeixinAccount{appId=" + appId + ", safeMode=" + isSafeMode() + "}";
    }
}
