/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.spi;

/**
 * 回执监听器扩展点：消费方实现本接口并注册到 {@code SmsHandler}，接收验签通过后的解析回执。
 *
 * <p>回调在 Handler 线程内同步交付；耗时逻辑由消费方自行转异步。模块不落库、不做幂等去重，
 * 重复投递由消费方按流水号自行处理。</p>
 */
public interface SmsReceiptListener {

    /**
     * 接收一条回执。
     *
     * @param receipt 解析后的回执内容
     */
    void onReceipt(SmsReceipt receipt);
}
