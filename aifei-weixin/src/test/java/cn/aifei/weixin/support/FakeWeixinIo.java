/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.support;

import cn.aifei.core.Input;
import cn.aifei.weixin.spi.WeixinIo;
import java.util.HashMap;
import java.util.Map;

/**
 * 单测用传输层伪实现（泛型取内核 Input，便于直接驱动 WeixinHandler）
 */
public class FakeWeixinIo implements WeixinIo<Input> {

    public final Map<String, String> params = new HashMap<>();
    public String body;
    public boolean getRequest;
    public String replied;
    public int replyCount;

    @Override
    public String query(Input in, String name) {
        return params.get(name);
    }

    @Override
    public String body(Input in) {
        return body;
    }

    @Override
    public boolean isGet(Input in) {
        return getRequest;
    }

    @Override
    public void replyXml(Input in, String xml) {
        this.replied = xml;
        this.replyCount++;
    }
}
