/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.aifei.PayHandler;
import cn.hg.aifei.pay.aifei.PayIo;
import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.UnimplementedPayClient;
import cn.hg.aifei.pay.model.PayNotifyRequest;
import cn.hg.aifei.pay.spi.PayNotify;
import cn.hg.aifei.pay.spi.PayNotifyListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 回调闭环：PayHandler 与 PayNotifyUtil 共用验签核心，按渠道写响应并分发监听器。
 */
public class PayHandlerTest {

    private static final String ALIPAY = "alipay3";
    private static final String WXPAY = "wxpay";

    private final List<PayNotify> received = new ArrayList<PayNotify>();

    @Before
    public void setUp() {
        PayChannelRegistry.clear();
        PayChannelRegistry.register(ALIPAY, new UnimplementedPayClient(ALIPAY, ALIPAY),
                (merchantId, params) -> true);
        PayChannelRegistry.register(WXPAY, new UnimplementedPayClient(WXPAY, WXPAY),
                (merchantId, params) -> true);
        PayChannelRegistry.registerDefault(ALIPAY);
        PayChannelRegistry.setStarted(true);
        received.clear();
    }

    @After
    public void tearDown() {
        PayChannelRegistry.clear();
    }

    @Test
    public void shouldDispatchAndWriteSuccessForAlipay() {
        FakeIo io = new FakeIo("/pay/notify/" + ALIPAY, true);
        PayHandler handler = new PayHandler(io, Collections.<PayNotifyListener>singletonList(received::add));

        handler.handle();

        assertEquals("成功状态码", 200, io.status);
        assertEquals("支付宝成功应答", "success", io.response);
        assertEquals("监听器调用一次", 1, received.size());
        assertEquals("幂等键应透出", "T2026010100001", received.get(0).outTradeNo());
    }

    @Test
    public void shouldNotDispatchWhenVerifyFails() {
        PayChannelRegistry.register(ALIPAY, new UnimplementedPayClient(ALIPAY, ALIPAY),
                (merchantId, params) -> false);
        FakeIo io = new FakeIo("/pay/notify/" + ALIPAY, true);
        PayHandler handler = new PayHandler(io, Collections.<PayNotifyListener>singletonList(received::add));

        handler.handle();

        assertEquals("失败状态码", 400, io.status);
        assertEquals("支付宝失败应答", "failure", io.response);
        assertTrue("验签失败不得分发", received.isEmpty());
    }

    @Test
    public void shouldWriteWxPayJsonResponse() {
        FakeIo io = new FakeIo("/pay/notify/" + WXPAY, true);
        PayHandler handler = new PayHandler(io, Collections.<PayNotifyListener>singletonList(received::add));

        handler.handle();

        assertEquals("微信成功应答", "{\"code\":\"SUCCESS\",\"message\":\"成功\"}", io.response);
        assertEquals("微信通道", WXPAY, received.get(0).channelName());
    }

    @Test
    public void shouldShareVerifyCoreWithPayNotifyUtil() {
        Map<String, String> params = signedParams();
        PayNotify direct = PayNotifyUtil.verifyAndParse(ALIPAY, null, PayNotifyRequest.of(params));

        FakeIo io = new FakeIo("/pay/notify/" + ALIPAY, true);
        PayHandler handler = new PayHandler(io, Collections.<PayNotifyListener>singletonList(received::add));
        handler.handle();

        assertEquals("两种模型应得到同一结果", direct.outTradeNo(), received.get(0).outTradeNo());
    }

    private static Map<String, String> signedParams() {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("out_trade_no", "T2026010100001");
        params.put("trade_no", "202601010000000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "0.01");
        params.put("sign", "test-sign");
        params.put("sign_type", "RSA2");
        return params;
    }

    private static final class FakeIo implements PayIo {

        private final String path;
        private final Map<String, String> params;
        private int status;
        private String response;

        FakeIo(String path, boolean withParams) {
            this.path = path;
            this.params = withParams ? signedParams() : Collections.<String, String>emptyMap();
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public String param(String name) {
            return params.get(name);
        }

        @Override
        public Map<String, String> params() {
            return params;
        }

        @Override
        public String header(String name) {
            return null;
        }

        @Override
        public Map<String, String> headers() {
            return Collections.emptyMap();
        }

        @Override
        public String body() {
            return null;
        }

        @Override
        public void write(int status, String response) {
            this.status = status;
            this.response = response;
        }
    }
}
