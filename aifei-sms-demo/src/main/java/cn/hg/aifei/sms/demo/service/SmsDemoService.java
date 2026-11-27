/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.service;

import cn.aifei.core.Path;
import cn.aifei.util.Prop;
import cn.aifei.util.PropKit;
import cn.hg.aifei.sms.SmsClient;
import cn.hg.aifei.sms.SmsException;
import cn.hg.aifei.sms.SmsKit;
import cn.hg.aifei.sms.SmsResult;
import cn.hg.aifei.sms.aifei.SmsConfigLoader;
import cn.hg.aifei.sms.demo.arch.In;
import cn.hg.aifei.sms.demo.arch.Out;
import cn.hg.aifei.sms.demo.mock.ReceiptRecorder;
import cn.hg.aifei.sms.internal.SmsErrors;
import cn.hg.aifei.sms.internal.sms4j.SmsDeps;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * demo 演示服务端点：发送全链路走 SmsKit → SmsSender → mock SmsBlend；
 * 错误消息由 SmsErrors / 真实探测构造；回执签名仅用于辅助模拟，验签由真实 SmsHandler 完成。
 */
@Path("/sms/demo")
public class SmsDemoService {

    private static final String SIGNATURE_KEY = "signature";
    private static final String VERIFY_SECRET_KEY = "sms.aliyun.access-key-secret";

    /** 演示页视图。 */
    @Path("page")
    public String page() {
        return "/sms/index.html";
    }

    /** 模板化发送：真实走门面全链路，底层 mock 返回预设结果。 */
    @Path("send")
    public Out send(In in) {
        Map<String, Object> body = in.getMap("");
        String channel = text(body.get("channel"));
        String phone = text(body.get("phone"));
        String templateId = text(body.get("templateId"));
        LinkedHashMap<String, String> variables = variablesOf(body.get("variables"));
        try {
            SmsClient client = channel == null || channel.isEmpty() ? SmsKit.defaultClient() : SmsKit.use(channel);
            SmsResult result = client.send(phone, templateId, variables);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("channel", result.getChannelName());
            data.put("success", result.isSuccess());
            data.put("requestId", result.getRequestId());
            data.put("message", result.getMessage());
            return Out.ok().data(data);
        } catch (SmsException e) {
            return Out.fail(e.getMessage());
        }
    }

    /** 多通道切换：返回已装配通道集合与默认通道。 */
    @Path("channels")
    public Out channels() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        try {
            Set<String> channels = SmsKit.channels();
            List<String> names = new ArrayList<String>(channels);
            Collections.sort(names);
            data.put("channels", names);
            data.put("default", SmsKit.defaultClient().channelName());
        } catch (SmsException e) {
            data.put("channels", Collections.<String>emptyList());
            data.put("default", "");
        }
        return Out.ok().data(data);
    }

    /** 配置错误态：以非法配置真实触发必填项校验，返回 SmsErrors 构造的消息。 */
    @Path("config-error")
    public Out configError() {
        Prop invalid = new Prop();
        invalid.getProperties().setProperty("sms.aliyun.type", "alibaba");
        invalid.getProperties().setProperty("sms.aliyun.secret-key", "sk");
        invalid.getProperties().setProperty("sms.aliyun.signature", "sig");
        try {
            SmsConfigLoader.load(invalid);
            return Out.fail("unexpected: invalid config accepted");
        } catch (SmsException e) {
            return Out.of("error", e.getMessage());
        }
    }

    /** 依赖缺失态：以隔离类加载器真实触发探测失败，返回 SmsErrors 构造的消息。 */
    @Path("dependency-missing")
    public Out dependencyMissing() {
        try {
            Method method = SmsDeps.class.getDeclaredMethod("requireSms4j", ClassLoader.class);
            method.setAccessible(true);
            ClassLoader isolated = new ClassLoader(null) {
            };
            try {
                method.invoke(null, new Object[]{isolated});
                return Out.fail("unexpected: sms4j probe succeeded");
            } catch (InvocationTargetException e) {
                return Out.of("error", ((SmsException) e.getCause()).getMessage());
            }
        } catch (Exception e) {
            return Out.fail(e.getMessage());
        }
    }

    /** 回执展示：监听器真实记录内容。 */
    @Path("receipts")
    public Out receipts() {
        return Out.of("receipts", ReceiptRecorder.snapshot());
    }

    /** 空态复位：清空回执记录。 */
    @Path("reset")
    public Out reset() {
        ReceiptRecorder.clear();
        return Out.ok();
    }

    /** 依赖状态展示。 */
    @Path("state")
    public Out state() {
        boolean available;
        try {
            SmsDeps.requireSms4j();
            available = true;
        } catch (SmsException e) {
            available = false;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("sms4jAvailable", available);
        data.put("version", "3.3.6");
        data.put("channels", channels().getData());
        return Out.ok().data(data);
    }

    /**
     * 模拟回执签名辅助：按阿里云规范计算 signature，页面据此 POST 到真实回调端点验签。
     * 验签本身由 SmsHandler 完成，本端点不代替验签。
     */
    @Path("receipt-sign")
    public Out receiptSign(In in) {
        Map<String, Object> body = in.getMap("");
        String secret = PropKit.get(VERIFY_SECRET_KEY);
        try {
            List<String> keys = new ArrayList<String>(body.keySet());
            keys.remove(SIGNATURE_KEY);
            Collections.sort(keys);
            StringBuilder canonical = new StringBuilder();
            for (String key : keys) {
                Object value = body.get(key);
                canonical.append(key).append(value == null ? "" : String.valueOf(value));
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            return Out.of("signature", signature);
        } catch (Exception e) {
            return Out.fail("sign failed: " + e.getMessage());
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static LinkedHashMap<String, String> variablesOf(Object value) {
        LinkedHashMap<String, String> variables = new LinkedHashMap<String, String>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                variables.put(String.valueOf(entry.getKey()),
                        entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        return variables;
    }
}
