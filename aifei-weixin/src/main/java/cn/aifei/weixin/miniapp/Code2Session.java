/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.miniapp;

import cn.aifei.weixin.common.ApiResult;
import cn.aifei.weixin.common.WxApiException;

/**
 * code2Session 结果（不可变值对象）。
 *
 * <p>sessionKey 仅应在登录响应中交给应用层一次，应用不得落日志（会话密钥泄漏风险）。
 */
public class Code2Session {

    private final String openid;
    private final String unionid;
    private final String sessionKey;

    private Code2Session(String openid, String unionid, String sessionKey) {
        this.openid = openid;
        this.unionid = unionid;
        this.sessionKey = sessionKey;
    }

    /**
     * 业务失败（errcode != 0）或缺少 openid / session_key 时抛 {@link WxApiException}
     */
    static Code2Session parse(ApiResult result) {
        if (!result.isSucceed()) {
            throw new WxApiException("code2session 失败：" + result.getErrorCode() + " " + result.getErrorMsg());
        }
        String openid = result.getData().getString("openid");
        String sessionKey = result.getData().getString("session_key");
        if (openid == null || openid.isEmpty()) {
            throw new WxApiException("code2session 失败：响应缺少 openid");
        }
        if (sessionKey == null || sessionKey.isEmpty()) {
            throw new WxApiException("code2session 失败：响应缺少 session_key");
        }
        return new Code2Session(openid, result.getData().getString("unionid"), sessionKey);
    }

    public String getOpenid() {
        return openid;
    }

    /** 未绑定开放平台账号时为 null */
    public String getUnionid() {
        return unionid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    @Override
    public String toString() {
        // sessionKey 不进 toString
        return "Code2Session{openid=" + openid + ", unionid=" + unionid + "}";
    }
}
