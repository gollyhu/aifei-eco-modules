/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.mock;

import cn.hg.aifei.sms.spi.SmsReceipt;
import cn.hg.aifei.sms.spi.SmsReceiptListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** demo 回执监听器：记录真实 SmsHandler 交付的回执，供页面展示。 */
public final class ReceiptRecorder implements SmsReceiptListener {

    private static final List<SmsReceipt> RECEIPTS =
            java.util.Collections.synchronizedList(new ArrayList<SmsReceipt>());

    @Override
    public void onReceipt(SmsReceipt receipt) {
        RECEIPTS.add(receipt);
    }

    /**
     * @return 回执快照（每个元素为页面可直接展示的 map）
     */
    public static List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        synchronized (RECEIPTS) {
            for (SmsReceipt receipt : RECEIPTS) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("status", receipt.getStatus());
                item.put("requestId", receipt.getRequestId());
                item.put("reportTime", receipt.getReportTime());
                item.put("phoneNumber", receipt.getPhoneNumber());
                item.put("channel", receipt.getChannelName());
                result.add(item);
            }
        }
        return result;
    }

    /** 清空记录（页面空态演示用）。 */
    public static void clear() {
        synchronized (RECEIPTS) {
            RECEIPTS.clear();
        }
    }
}
