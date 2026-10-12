/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.spi;

import java.util.HashMap;
import java.util.Map;

/**
 * 账号数据源抽象。本期只提供内存实现，生产多公众号场景可由使用方自行接 DB / 配置中心。
 */
public interface WeixinAccountProvider {

    /**
     * 按 appId 取账号，返回 null 表示未知 appId（回调请求将被拒绝）
     */
    WeixinAccount get(String appId);

    /**
     * 内存实现，测试与单账号场景直接使用
     */
    static WeixinAccountProvider of(WeixinAccount... accounts) {
        Map<String, WeixinAccount> map = new HashMap<>();
        if (accounts != null) {
            for (WeixinAccount account : accounts) {
                if (account != null) {
                    map.put(account.getAppId(), account);
                }
            }
        }
        return map::get;
    }
}
