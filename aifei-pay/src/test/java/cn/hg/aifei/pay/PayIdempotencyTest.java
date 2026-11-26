/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.log.LogFactory;
import cn.hg.aifei.pay.spi.PayNotify;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestEnv;
import cn.hg.aifei.pay.support.PayTestProps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 幂等职责边界（AC-14）：验签是纯函数——N 次结果一致、不修改入参、不保存「已处理」状态；
 * 幂等键只透出，去重由业务负责。
 */
public class PayIdempotencyTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 验签路径的三个文件（唯一解析点 / 唯一验签入口 / 公共验签工具）。
     */
    private static final String[] VERIFY_PATH_FILES = {
            "src/main/java/cn/hg/aifei/pay/internal/PayVerifier.java",
            "src/main/java/cn/hg/aifei/pay/internal/PayForms.java",
            "src/main/java/cn/hg/aifei/pay/PayNotifyUtil.java"
    };

    /**
     * 去重 / 缓存容器痕迹。
     */
    private static final String[] DEDUP_TOKENS = {
            "Cache",
            "cache",
            "dedup",
            "HashSet",
            "ConcurrentHashMap",
            "processed",
            "已处理"
    };

    /**
     * 可变的静态字段声明片段（字段行不含括号，方法签名行含括号，据此区分）。
     */
    private static final String STATIC_FIELD_MARK = "private static";

    /**
     * 重复调用次数。
     */
    private static final int REPEAT = 5;

    /**
     * 换入前的日志工厂。
     */
    private LogFactory previousLogFactory;

    /**
     * 装配默认（普通公钥模式）配置并静默日志。
     */
    @Before
    public void setUp() {
        previousLogFactory = NoOpLogFactory.install();
        PayTestEnv.install(PayTestProps.keyMode(APP));
    }

    /**
     * 清理门面持有器与日志工厂。
     */
    @After
    public void cleanup() {
        PayTestEnv.clear();
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * AC-14：同一报文连续验签 N 次，结果恒为通过，解析出的幂等键完全一致。
     */
    @Test
    public void shouldReturnStableResultAcrossRepeatedCalls() {
        Map<String, String> params = signedNotify(PayTestCrypto.KEY_PAIR_OLD);

        for (int i = 0; i < REPEAT; i++) {
            assertTrue("第 " + (i + 1) + " 次验签应仍为通过", PayNotifyUtil.verify(APP, params));
            PayNotify result = PayNotifyUtil.verifyAndParse(APP, params);
            assertEquals("第 " + (i + 1) + " 次解析的幂等键应一致", "T2026010100001", result.outTradeNo());
        }
    }

    /**
     * AC-14：验签不修改入参 {@code Map}（内核的待签串拼接会原地删除 {@code sign} / {@code sign_type}，
     * 模块必须先做防御性拷贝）。
     */
    @Test
    public void shouldNotMutateInputMap() {
        Map<String, String> params = signedNotify(PayTestCrypto.KEY_PAIR_OLD);
        Map<String, String> snapshot = new LinkedHashMap<>(params);

        assertTrue(PayNotifyUtil.verify(APP, params));
        PayNotifyUtil.verifyAndParse(APP, params);

        assertEquals("入参规模不应变化", snapshot.size(), params.size());
        assertEquals("sign 不应被移除", snapshot.get("sign"), params.get("sign"));
        assertEquals("sign_type 不应被移除", snapshot.get("sign_type"), params.get("sign_type"));
        assertEquals("入参内容应完全一致", snapshot, params);
    }

    /**
     * AC-14：交易类通知透出 {@code out_trade_no}，退款类通知透出 {@code out_request_no}。
     */
    @Test
    public void shouldExposeTradeAndRefundIdempotencyKeys() {
        Map<String, String> trade = signedNotify(PayTestCrypto.KEY_PAIR_OLD);
        PayNotify tradeResult = PayNotifyUtil.verifyAndParse(APP, trade);
        assertEquals("交易类通知", PayNotify.Type.TRADE, tradeResult.type());
        assertEquals("交易幂等键", "T2026010100001", tradeResult.outTradeNo());
        assertEquals("无退款单号时为空串", "", tradeResult.outRequestNo());

        Map<String, String> refund = refundNotify();
        PayTestCrypto.signNotify(refund, PayTestCrypto.KEY_PAIR_OLD);
        PayNotify refundResult = PayNotifyUtil.verifyAndParse(APP, refund);
        assertEquals("退款类通知", PayNotify.Type.REFUND, refundResult.type());
        assertEquals("退款幂等键", "R2026010100001", refundResult.outRequestNo());
        assertEquals("退款金额", "0.01", refundResult.refundFee());
    }

    /**
     * AC-14：{@code params()} 是只读拷贝，业务不能就地改动验签结果。
     */
    @Test
    public void shouldReturnUnmodifiableParamsCopy() {
        PayNotify result = PayNotifyUtil.verifyAndParse(APP, signedNotify(PayTestCrypto.KEY_PAIR_OLD));

        assertThrows(UnsupportedOperationException.class, () -> result.params().put("trade_status", "TRADE_CLOSED"));
    }

    /**
     * AC-14：验签路径的源码不含去重缓存 / 已处理标记 / 进程级可变静态状态（模块不做幂等）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void shouldHoldNoDedupStateInVerifyPath() throws IOException {
        for (String file : VERIFY_PATH_FILES) {
            Path path = Paths.get(file);
            assertTrue("源码文件应存在（测试需在模块根目录执行）：" + file, Files.isRegularFile(path));
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            for (String token : DEDUP_TOKENS) {
                assertFalse("验签路径不得出现去重 / 缓存痕迹 " + token + "：" + file,
                        source.contains(token));
            }
            for (String line : source.split("\n")) {
                String trimmed = line.trim();
                boolean staticDeclaration = trimmed.contains(STATIC_FIELD_MARK);
                boolean mutableField = staticDeclaration && !trimmed.contains(" final ")
                        && !trimmed.contains("final ") && !trimmed.contains("(");
                assertFalse("验签路径不得持有可变的静态状态：" + file + " -> " + trimmed, mutableField);
            }
        }
    }

    /**
     * 构造带签名的交易类通知。
     *
     * @param signer 签名密钥对
     * @return 报文参数
     */
    private static Map<String, String> signedNotify(java.security.KeyPair signer) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("notify_time", "2026-11-26 10:00:00");
        params.put("notify_type", "trade_status_sync");
        params.put("app_id", APP);
        params.put("charset", "utf-8");
        params.put("out_trade_no", "T2026010100001");
        params.put("trade_no", "2026010122001445101000000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "0.01");
        PayTestCrypto.signNotify(params, signer);
        return params;
    }

    /**
     * 构造一笔未签名的退款类通知。
     *
     * @return 报文参数
     */
    private static Map<String, String> refundNotify() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("notify_time", "2026-11-26 10:05:00");
        params.put("notify_type", "trade_refund");
        params.put("app_id", APP);
        params.put("charset", "utf-8");
        params.put("out_trade_no", "T2026010100001");
        params.put("trade_no", "2026010122001445101000000001");
        params.put("out_request_no", "R2026010100001");
        params.put("refund_fee", "0.01");
        return params;
    }
}
