/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.miniapp;

import java.util.HashMap;
import java.util.Map;

/**
 * 小程序账号数据源抽象。模式对齐公众号 {@code WeixinAccountProvider}；
 * 多租户等生产数据源由使用方自行实现（SDK 不接 DB）。
 */
public interface MiniAppAccountProvider {

    /**
     * 按 appId 取账号，返回 null 表示未知 appId
     */
    MiniAppAccount get(String appId);

    /**
     * 内存实现，测试与单账号场景直接使用
     */
    static MiniAppAccountProvider of(MiniAppAccount... accounts) {
        Map<String, MiniAppAccount> map = new HashMap<>();
        if (accounts != null) {
            for (MiniAppAccount account : accounts) {
                if (account != null) {
                    map.put(account.getAppId(), account);
                }
            }
        }
        return map::get;
    }
}
