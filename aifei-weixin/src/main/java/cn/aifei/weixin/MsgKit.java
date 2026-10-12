/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin;

import cn.aifei.log.Log;
import cn.aifei.weixin.core.encrypt.RandomKit;
import cn.aifei.weixin.core.encrypt.Sha1Kit;
import cn.aifei.weixin.core.encrypt.WxCrypt;
import cn.aifei.weixin.core.kit.XmlBuilder;
import cn.aifei.weixin.core.kit.XmlKit;
import cn.aifei.weixin.core.msg.InMsg;
import cn.aifei.weixin.core.msg.InMsgParser;
import cn.aifei.weixin.core.msg.OutMsg;
import cn.aifei.weixin.core.msg.OutMsgXmlBuilder;
import cn.aifei.weixin.spi.WeixinAccount;
import cn.aifei.weixin.spi.WeixinIo;
import java.util.Map;

/**
 * 传输无关主入口（双入口：GET 首验 / POST 消息回调）。
 *
 * <pre>
 * 职责边界：
 *  1: GET 与 POST 分流由 WeixinHandler 负责（依 io.isGet(in)）
 *  2: 被动回复的 ToUserName / FromUserName 反转、CreateTime 生成由本类统一承担
 *  3: 验签失败回 "fail"；消息解析 / 业务处理异常回 "success"（避免微信无谓重试）
 * </pre>
 */
public class MsgKit {

    static final Log log = Log.get(MsgKit.class);

    /**
     * GET：URL 首验。返回 echostr（明文模式原文 / 安全模式解密后明文），失败返回 "fail"
     */
    public static <I> String verify(WeixinIo<I> io, I in, WeixinAccount account) {
        String signature = io.query(in, "signature");
        String msgSignature = io.query(in, "msg_signature");
        String timestamp = io.query(in, "timestamp");
        String nonce = io.query(in, "nonce");
        String echostr = io.query(in, "echostr");
        if (echostr == null || timestamp == null || nonce == null) {
            log.warn("微信 URL 首验参数缺失 appId={}", account.getAppId());
            return WxConsts.RESP_FAIL;
        }

        boolean safeMode = isSafeRequest(io, in, account);
        boolean signatureOk;
        if (safeMode) {
            // 安全模式首验：微信可能只带 signature（不含 echostr 参与签名），也可能带 msg_signature（含 echostr）
            signatureOk = checkSignature(account.getToken(), msgSignature, timestamp, nonce, echostr)
                    || checkSignature(account.getToken(), signature, timestamp, nonce)
                    || checkSignature(account.getToken(), signature, timestamp, nonce, echostr);
        } else {
            signatureOk = checkSignature(account.getToken(), signature, timestamp, nonce);
        }
        if (!signatureOk) {
            log.warn("微信 URL 首验签名校验失败 appId={}", account.getAppId());
            return WxConsts.RESP_FAIL;
        }

        if (!safeMode) {
            return echostr;
        }
        try {
            return decrypt(account.getAesKey(), account.getAppId(), echostr);
        } catch (RuntimeException e) {
            log.error("微信 URL 首验 echostr 解密失败 appId=" + account.getAppId(), e);
            return WxConsts.RESP_FAIL;
        }
    }

    /**
     * POST：消息链路。验签 →（解密）→ 解析 → 分发 → 渲染 →（加密）→ 返回响应体字符串
     */
    public static <I> String handle(WeixinIo<I> io, I in, WeixinAccount account, MsgHandler handler) {
        String signature = io.query(in, "signature");
        String msgSignature = io.query(in, "msg_signature");
        String timestamp = io.query(in, "timestamp");
        String nonce = io.query(in, "nonce");

        String body = io.body(in);
        String encrypt;
        try {
            Map<String, String> root = XmlKit.parse(body);
            encrypt = root.get("Encrypt");
        } catch (RuntimeException e) {
            log.error("解析微信回调 XML 失败 appId=" + account.getAppId(), e);
            return WxConsts.RESP_SUCCESS;
        }

        String plainXml;
        if (encrypt != null) {
            String signToCheck = msgSignature != null ? msgSignature : signature;
            if (!checkSignature(account.getToken(), signToCheck, timestamp, nonce, encrypt)) {
                log.warn("微信回调 msg_signature 校验失败 appId={}", account.getAppId());
                return WxConsts.RESP_FAIL;
            }
            try {
                plainXml = decrypt(account.getAesKey(), account.getAppId(), encrypt);
            } catch (RuntimeException e) {
                log.error("微信回调消息解密失败 appId=" + account.getAppId(), e);
                return WxConsts.RESP_FAIL;
            }
        } else {
            if (!checkSignature(account.getToken(), signature, timestamp, nonce)) {
                log.warn("微信回调 signature 校验失败 appId={}", account.getAppId());
                return WxConsts.RESP_FAIL;
            }
            plainXml = body;
        }

        try {
            InMsg inMsg = InMsgParser.parse(plainXml);
            if (inMsg == null) {
                log.warn("微信回调消息类型未支持，响应 success appId={}", account.getAppId());
                return WxConsts.RESP_SUCCESS;
            }

            OutMsg outMsg = handler.process(inMsg);
            if (outMsg == null) {
                return WxConsts.RESP_SUCCESS;
            }

            // 被动回复必须反转收发方，并由本类填充当前秒级时间戳
            outMsg.setToUserName(inMsg.getFromUserName());
            outMsg.setFromUserName(inMsg.getToUserName());
            outMsg.setCreateTime(System.currentTimeMillis() / 1000);

            String replyXml = OutMsgXmlBuilder.build(outMsg);
            if (!account.isSafeMode()) {
                return replyXml;
            }
            return encryptReply(account, replyXml);
        } catch (RuntimeException e) {
            log.error("处理微信回调消息异常，响应 success appId=" + account.getAppId(), e);
            return WxConsts.RESP_SUCCESS;
        }
    }

    /**
     * 微信签名校验：参数按字典序拼 token,timestamp,nonce[,encrypt] 后取 SHA-1
     */
    public static boolean checkSignature(String token, String signature, String timestamp, String nonce, String... encrypt) {
        if (token == null || signature == null || timestamp == null || nonce == null) {
            return false;
        }
        String expect;
        if (encrypt == null || encrypt.length == 0 || encrypt[0] == null) {
            expect = Sha1Kit.sortedSign(token, timestamp, nonce);
        } else {
            expect = Sha1Kit.sortedSign(token, timestamp, nonce, encrypt[0]);
        }
        return expect.equals(signature.toLowerCase());
    }

    public static String encrypt(String aesKey, String appId, String plain) {
        return WxCrypt.encrypt(aesKey, appId, plain);
    }

    public static String decrypt(String aesKey, String appId, String encryptBase64) {
        return WxCrypt.decrypt(aesKey, appId, encryptBase64);
    }

    /**
     * 安全模式被动回复：Encrypt + MsgSignature + TimeStamp + Nonce
     */
    private static String encryptReply(WeixinAccount account, String replyXml) {
        String encrypt = WxCrypt.encrypt(account.getAesKey(), account.getAppId(), replyXml);
        String replyTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String replyNonce = RandomKit.randomStr();
        String msgSignature = Sha1Kit.sortedSign(account.getToken(), replyTimestamp, replyNonce, encrypt);

        return new XmlBuilder()
                .cdata("Encrypt", encrypt)
                .cdata("MsgSignature", msgSignature)
                .text("TimeStamp", replyTimestamp)
                .cdata("Nonce", replyNonce)
                .build();
    }

    private static <I> boolean isSafeRequest(WeixinIo<I> io, I in, WeixinAccount account) {
        return WxConsts.EncryptType.AES.equals(io.query(in, "encrypt_type")) || account.isSafeMode();
    }

    private MsgKit() {
    }
}
