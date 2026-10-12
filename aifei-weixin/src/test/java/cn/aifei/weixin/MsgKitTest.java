/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.aifei.weixin.core.encrypt.Sha1Kit;
import cn.aifei.weixin.core.encrypt.WxCrypt;
import cn.aifei.weixin.core.kit.XmlKit;
import cn.aifei.weixin.core.msg.InTextMsg;
import cn.aifei.weixin.core.msg.OutMsg;
import cn.aifei.weixin.core.msg.OutTextMsg;
import cn.aifei.weixin.spi.WeixinAccount;
import cn.aifei.weixin.support.FakeWeixinIo;
import cn.aifei.weixin.support.TestLogs;
import java.util.Map;
import org.junit.Test;

/**
 * 双入口主流程（S2 验证标准 ④⑥⑧、S4 验证标准 ①）：明文模式与安全模式的 verify / handle
 */
public class MsgKitTest {

    static {
        TestLogs.install();
    }

    static final String TOKEN = "tokentokentokentoken";
    static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopq";
    static final String APP_ID = "wx1234567890abcdef";

    private static final String TIMESTAMP = "1600000000";
    private static final String NONCE = "1234567890";

    private final WeixinAccount plainAccount = WeixinAccount.of(APP_ID, "secret", TOKEN, null);
    private final WeixinAccount safeAccount = WeixinAccount.of(APP_ID, "secret", TOKEN, AES_KEY);

    // ---------------- GET 首验 ----------------

    @Test
    public void verifyPlainModeReturnsRawEchostr() {
        FakeWeixinIo io = new FakeWeixinIo();
        io.getRequest = true;
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("echostr", "hello-echostr");
        io.params.put("signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE));

        assertEquals("hello-echostr", MsgKit.verify(io, null, plainAccount));
    }

    @Test
    public void verifyRejectsWrongSignature() {
        FakeWeixinIo io = new FakeWeixinIo();
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("echostr", "hello");
        io.params.put("signature", "deadbeef");

        assertEquals(WxConsts.RESP_FAIL, MsgKit.verify(io, null, plainAccount));
    }

    @Test
    public void verifyFailsWhenParamsMissing() {
        FakeWeixinIo io = new FakeWeixinIo();
        io.params.put("signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE));

        assertEquals(WxConsts.RESP_FAIL, MsgKit.verify(io, null, plainAccount));
    }

    @Test
    public void verifySafeModeReturnsDecryptedEchostr() {
        String echostrCipher = WxCrypt.encrypt(AES_KEY, APP_ID, "hello-safe");

        FakeWeixinIo io = new FakeWeixinIo();
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("echostr", echostrCipher);
        io.params.put("encrypt_type", "aes");
        io.params.put("signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE));
        assertEquals("hello-safe", MsgKit.verify(io, null, safeAccount));

        // 微信也可能带 msg_signature（含 echostr 密文参与签名）
        FakeWeixinIo io2 = new FakeWeixinIo();
        io2.params.put("timestamp", TIMESTAMP);
        io2.params.put("nonce", NONCE);
        io2.params.put("echostr", echostrCipher);
        io2.params.put("encrypt_type", "aes");
        io2.params.put("msg_signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE, echostrCipher));
        assertEquals("hello-safe", MsgKit.verify(io2, null, safeAccount));
    }

    // ---------------- POST 消息 ----------------

    @Test
    public void handlePlainTextReversesUserNamesAndFillsCreateTime() {
        FakeWeixinIo io = new FakeWeixinIo();
        io.body = textXml("你好");
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE));

        String response = MsgKit.handle(io, null, plainAccount, echoHandler());

        Map<String, String> reply = XmlKit.parse(response);
        assertEquals("openid_user", reply.get("ToUserName"));
        assertEquals("gh_official", reply.get("FromUserName"));
        assertEquals("text", reply.get("MsgType"));
        assertEquals("echo:你好", reply.get("Content"));
        long createTime = Long.parseLong(reply.get("CreateTime"));
        assertTrue("CreateTime 应为当前秒级时间戳", Math.abs(createTime - System.currentTimeMillis() / 1000) <= 5);
    }

    @Test
    public void handleReturnsSuccessWhenNoReplyOrUnknownType() {
        FakeWeixinIo noReply = signedIo(textXml("hi"));
        assertEquals(WxConsts.RESP_SUCCESS, MsgKit.handle(noReply, null, plainAccount, new MsgHandler() {
        }));

        FakeWeixinIo unknown = signedIo("<xml><ToUserName>gh</ToUserName><MsgType><![CDATA[unknown]]></MsgType></xml>");
        assertEquals(WxConsts.RESP_SUCCESS, MsgKit.handle(unknown, null, plainAccount, echoHandler()));
    }

    @Test
    public void handleEscapesHandlerContent() {
        FakeWeixinIo io = signedIo(textXml("payload"));
        MsgHandler handler = new MsgHandler() {
            @Override
            protected OutMsg processInTextMsg(InTextMsg msg) {
                return new OutTextMsg("a & b <c> ]]>");
            }
        };

        String response = MsgKit.handle(io, null, plainAccount, handler);
        assertTrue(response.contains("a &amp; b &lt;c&gt; ]]&gt;"));
    }

    @Test
    public void handleRejectsInvalidSignatureAndMalformedXml() {
        FakeWeixinIo badSignature = new FakeWeixinIo();
        badSignature.body = textXml("hi");
        badSignature.params.put("timestamp", TIMESTAMP);
        badSignature.params.put("nonce", NONCE);
        badSignature.params.put("signature", "bad");
        assertEquals(WxConsts.RESP_FAIL, MsgKit.handle(badSignature, null, plainAccount, echoHandler()));

        FakeWeixinIo malformed = signedIo("<xml><a></xml>");
        assertEquals(WxConsts.RESP_SUCCESS, MsgKit.handle(malformed, null, plainAccount, echoHandler()));
    }

    @Test
    public void handleSafeModeDecryptsRequestAndEncryptsReply() {
        String encrypt = WxCrypt.encrypt(AES_KEY, APP_ID, textXml("安全模式"));

        FakeWeixinIo io = new FakeWeixinIo();
        io.body = "<xml><ToUserName><![CDATA[gh_official]]></ToUserName><Encrypt><![CDATA["
                + encrypt + "]]></Encrypt></xml>";
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("encrypt_type", "aes");
        io.params.put("msg_signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE, encrypt));

        String response = MsgKit.handle(io, null, safeAccount, echoHandler());

        Map<String, String> envelope = XmlKit.parse(response);
        String replyEncrypt = envelope.get("Encrypt");
        String replyNonce = envelope.get("Nonce");
        String replyTimestamp = envelope.get("TimeStamp");
        assertEquals(Sha1Kit.sortedSign(TOKEN, replyTimestamp, replyNonce, replyEncrypt), envelope.get("MsgSignature"));

        Map<String, String> reply = XmlKit.parse(WxCrypt.decrypt(AES_KEY, APP_ID, replyEncrypt));
        assertEquals("openid_user", reply.get("ToUserName"));
        assertEquals("echo:安全模式", reply.get("Content"));
    }

    @Test
    public void handleSafeModeRejectsTamperedRequest() {
        String encrypt = WxCrypt.encrypt(AES_KEY, APP_ID, textXml("安全模式"));

        FakeWeixinIo io = new FakeWeixinIo();
        io.body = "<xml><ToUserName><![CDATA[gh]]></ToUserName><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>";
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("encrypt_type", "aes");
        io.params.put("msg_signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE, encrypt));

        assertEquals(WxConsts.RESP_FAIL, MsgKit.handle(io, null,
                WeixinAccount.of(APP_ID, "secret", "another-token", AES_KEY), echoHandler()));
    }

    @Test
    public void handleSafeModeAccountEncryptsReplyEvenForPlainRequest() {
        // 兼容模式：请求明文但账号配置了 aesKey，回复仍为加密报文（微信两种模式均接受）
        FakeWeixinIo io = signedIo(textXml("hi"));
        String response = MsgKit.handle(io, null, safeAccount, echoHandler());
        assertTrue(response.contains("<Encrypt><![CDATA["));
    }

    private static FakeWeixinIo signedIo(String body) {
        FakeWeixinIo io = new FakeWeixinIo();
        io.body = body;
        io.params.put("timestamp", TIMESTAMP);
        io.params.put("nonce", NONCE);
        io.params.put("signature", Sha1Kit.sortedSign(TOKEN, TIMESTAMP, NONCE));
        return io;
    }

    private static MsgHandler echoHandler() {
        return new MsgHandler() {
            @Override
            protected OutMsg processInTextMsg(InTextMsg msg) {
                return new OutTextMsg("echo:" + msg.getContent());
            }
        };
    }

    private static String textXml(String content) {
        return "<xml>"
                + "<ToUserName><![CDATA[gh_official]]></ToUserName>"
                + "<FromUserName><![CDATA[openid_user]]></FromUserName>"
                + "<CreateTime>1600000000</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + content + "]]></Content>"
                + "<MsgId>1234567890123456</MsgId>"
                + "</xml>";
    }
}
