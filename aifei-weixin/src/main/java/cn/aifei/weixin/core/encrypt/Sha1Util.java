/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.encrypt;

import cn.hg.aifei.tools.crypto.CryptoException;
import cn.hg.aifei.tools.crypto.CryptoUtil;
import java.util.Arrays;

/**
 * SHA-1 摘要工具（委托 aifei-tools crypto，字典序签名协议留在来源模块）。
 */
public class Sha1Util {

    private static final String MISSING_TOOLS =
            "缺少可选依赖 io.github.gollyhu:aifei-tools:1.1.0-SNAPSHOT；使用本能力时消费方须显式声明";

    /**
     * 微信签名：参数按字典序排序后直接拼接，取 40 位小写十六进制。
     *
     * <p>明文模式：{@code sortedSign(token, timestamp, nonce)}；
     * 安全模式：{@code sortedSign(token, timestamp, nonce, encrypt)}。
     */
    public static String sortedSign(String... parts) {
        String[] sorted = Arrays.copyOf(parts, parts.length);
        Arrays.sort(sorted);

        StringBuilder sb = new StringBuilder(64);
        for (String part : sorted) {
            sb.append(part);
        }
        return sha1(sb.toString());
    }

    /**
     * 直接对文本取 SHA-1，40 位小写十六进制（不做排序，用于 JS-SDK 前端签名等定序拼接场景）
     */
    public static String sha1(String text) {
        return sha1ViaTools(text);
    }

    private static String sha1ViaTools(String text) {
        if (text == null) {
            throw new NullPointerException();
        }
        try {
            return Sha1Holder.sha1Hex(text);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException(MISSING_TOOLS, e);
        }
    }

    /**
     * 隔离 tools 异常类型引用：可选依赖缺失时本类仍可加载，缺失错误由上层助手收敛。
     */
    private static final class Sha1Holder {
        private static String sha1Hex(String text) {
            try {
                return CryptoUtil.sha1Hex(text);
            } catch (CryptoException e) {
                throw new IllegalStateException("JDK 不支持 SHA-1",
                        e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    private Sha1Util() {
    }
}
