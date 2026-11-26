/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.support;

import cn.hg.aifei.sms.spi.SmsReceipt;
import cn.hg.aifei.sms.spi.SmsReceiptListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用回执监听器：记录收到的回执，可配置为抛异常以验证监听器失败隔离。
 */
public final class RecordingReceiptListener implements SmsReceiptListener {

    private final List<SmsReceipt> receipts = new ArrayList<SmsReceipt>();
    private boolean throwOnReceipt;

    @Override
    public void onReceipt(SmsReceipt receipt) {
        if (throwOnReceipt) {
            throw new IllegalStateException("listener failed");
        }
        receipts.add(receipt);
    }

    /**
     * @return 已收到回执的不可变快照
     */
    public List<SmsReceipt> receipts() {
        return new ArrayList<SmsReceipt>(receipts);
    }

    /**
     * @param value 是否在收到回执时抛异常
     */
    public void setThrowOnReceipt(boolean value) {
        this.throwOnReceipt = value;
    }
}
