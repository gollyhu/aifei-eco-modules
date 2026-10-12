/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.aifei;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.weixin.MsgHandler;
import cn.aifei.weixin.WxConsts;
import cn.aifei.weixin.core.encrypt.Sha1Kit;
import cn.aifei.weixin.core.kit.XmlKit;
import cn.aifei.weixin.core.msg.InTextMsg;
import cn.aifei.weixin.core.msg.OutMsg;
import cn.aifei.weixin.core.msg.OutTextMsg;
import cn.aifei.weixin.spi.WeixinAccount;
import cn.aifei.weixin.spi.WeixinAccountProvider;
import cn.aifei.weixin.support.FakeWeixinIo;
import cn.aifei.weixin.support.TestLogs;
import java.util.Map;
import org.junit.Test;

/**
 * 接入层（S4 验证标准 ①②）：路径匹配、未知 appId、GET 首验、POST 回调、不匹配路径交给 next
 */
public class WeixinHandlerTest {

    static {
        TestLogs.install();
    }

    static final String TOKEN = "tokentokentokentoken";
    static final String APP_ID = "wx1234567890abcdef";

    private final WeixinAccount account = WeixinAccount.of(APP_ID, "secret", TOKEN, null);
    private final WeixinAccountProvider provider = WeixinAccountProvider.of(account);

    private final FakeWeixinIo io = new FakeWeixinIo();

    private final MsgHandler msgHandler = new MsgHandler() {
        @Override
        protected OutMsg processInTextMsg(InTextMsg msg) {
            return new OutTextMsg("echo:" + msg.getContent());
        }
    };

    @Test
    public void getFirstVerificationReturnsEchostr() throws Exception {
        io.getRequest = true;
        signWithEchostr("echostr-value");

        newHandler().handle("/weixin/" + APP_ID + "/msg", null, null);

        assertEquals("echostr-value", io.replied);
    }

    @Test
    public void getFirstVerificationFailsOnBadSignature() throws Exception {
        io.getRequest = true;
        io.params.put("timestamp", "1600000000");
        io.params.put("nonce", "1234567890");
        io.params.put("echostr", "echostr-value");
        io.params.put("signature", "bad-signature");

        newHandler().handle("/weixin/" + APP_ID + "/msg", null, null);

        assertEquals(WxConsts.RESP_FAIL, io.replied);
    }

    @Test
    public void unknownAppIdIsRejectedWithFail() throws Exception {
        signWithEchostr("echostr-value");

        newHandler().handle("/weixin/unknown-appid/msg", null, null);

        assertEquals(WxConsts.RESP_FAIL, io.replied);
    }

    @Test
    public void postTextMessageRepliesParsableXml() throws Exception {
        io.body = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName>"
                + "<FromUserName><![CDATA[openid_user]]></FromUserName>"
                + "<CreateTime>1600000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[hi]]></Content><MsgId>1</MsgId></xml>";
        signWithEchostr(null);

        newHandler().handle("/weixin/" + APP_ID + "/msg", null, null);

        Map<String, String> reply = XmlKit.parse(io.replied);
        assertEquals("echo:hi", reply.get("Content"));
        assertEquals("openid_user", reply.get("ToUserName"));
        assertEquals("gh_official", reply.get("FromUserName"));
    }

    @Test
    public void nonWeixinPathDelegatesToNextHandler() throws Exception {
        RecordingHandler next = new RecordingHandler();

        newHandlerWithNext(next).handle("/vip/account/index", null, null);

        assertTrue(next.called);
        assertNull(io.replied);
        assertEquals(0, io.replyCount);
    }

    @Test
    public void prefixBoundaryFallsThrough() throws Exception {
        RecordingHandler next = new RecordingHandler();
        WeixinHandler<Input, Output> handler = newHandlerWithNext(next);

        handler.handle("/weixin", null, null);
        handler.handle("/weixinx/" + APP_ID + "/msg", null, null);

        assertTrue(next.called);
        assertEquals(0, io.replyCount);
    }

    @Test
    public void customPrefixAndAccountIdOverrideAreSupported() throws Exception {
        io.getRequest = true;
        signWithEchostr("echostr-value");

        WeixinHandler<Input, Output> handler = new WeixinHandler<Input, Output>("/wx", io, provider, msgHandler) {
            @Override
            protected String accountId(Input in, String path) {
                return APP_ID;
            }
        };
        handler.handle("/wx/anything", null, null);

        assertEquals("echostr-value", io.replied);
    }

    private void signWithEchostr(String echostr) {
        io.params.put("timestamp", "1600000000");
        io.params.put("nonce", "1234567890");
        io.params.put("signature", Sha1Kit.sortedSign(TOKEN, "1600000000", "1234567890"));
        if (echostr != null) {
            io.params.put("echostr", echostr);
        }
    }

    private WeixinHandler<Input, Output> newHandler() {
        return new WeixinHandler<>(io, provider, msgHandler);
    }

    private WeixinHandler<Input, Output> newHandlerWithNext(Handler<Input, Output> nextHandler) {
        return new WeixinHandler<Input, Output>(io, provider, msgHandler) {
            {
                // 匿名子类可直接访问继承自 Handler 的 protected 字段 next
                this.next = nextHandler;
            }
        };
    }

    private static class RecordingHandler extends Handler<Input, Output> {
        boolean called;

        @Override
        public void handle(String path, Input input, Output output) {
            called = true;
        }
    }
}
