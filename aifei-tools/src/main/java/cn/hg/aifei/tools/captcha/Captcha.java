/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha;

/**
 * 验证码生成结果值对象：令牌、PNG 图片字节与绝对过期时刻。
 */
public final class Captcha {

    private final String token;
    private final byte[] imageBytes;
    private final long expireAt;

    Captcha(String token, byte[] imageBytes, long expireAt) {
        this.token = token;
        this.imageBytes = imageBytes;
        this.expireAt = expireAt;
    }

    /**
     * 校验时回传的令牌。
     *
     * @return 安全随机令牌
     */
    public String getToken() {
        return token;
    }

    /**
     * 验证码图片。
     *
     * @return PNG 图片字节
     */
    public byte[] getImageBytes() {
        return imageBytes;
    }

    /**
     * 绝对过期时刻。
     *
     * @return 毫秒时间戳
     */
    public long getExpireAt() {
        return expireAt;
    }
}
