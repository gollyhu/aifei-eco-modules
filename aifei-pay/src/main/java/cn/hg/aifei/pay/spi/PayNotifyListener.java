/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.spi;

/**
 * 回调监听器：只接收验签通过的事件。
 */
public interface PayNotifyListener {

    /**
     * @param notify 验签通过的回调事件
     */
    void onNotify(PayNotify notify);
}
