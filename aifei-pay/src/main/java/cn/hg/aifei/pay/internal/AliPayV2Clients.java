/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayException;
import com.alipay.api.AlipayClient;
import com.ijpay.alipay.AliPayApiConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝 V2 客户端缓存：按应用配置构建并复用 {@link AlipayClient}。
 */
public final class AliPayV2Clients {

    private final PayConfig config;

    private final Map<String, AlipayClient> clients = new LinkedHashMap<String, AlipayClient>();

    public AliPayV2Clients(PayConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        this.config = config;
    }

    public void preload() {
        for (String appId : config.apps().keySet()) {
            client(appId);
        }
    }

    public AlipayClient client(String appId) {
        PayAppConfig app = config.resolve(appId);
        AlipayClient cached = clients.get(app.appId());
        if (cached != null) {
            return cached;
        }
        PayDeps.requireV2Kernel();
        AliPayApiConfig apiConfig = AliPayApiConfig.builder()
                .setAppId(app.appId())
                .setPrivateKey(app.privateKey())
                .setServiceUrl(app.gatewayUrl())
                .setCharset(app.charset())
                .setSignType(app.signType());
        try {
            if (app.isCertMode()) {
                apiConfig.setCertModel(true)
                        .setAppCertContent(app.appCert())
                        .setAliPayCertContent(verifyContent(app, true))
                        .setAliPayRootCertContent(app.rootCert())
                        .buildByCertContent();
            } else {
                apiConfig.setAliPayPublicKey(verifyContent(app, false)).build();
            }
        } catch (Exception e) {
            throw new PayException("支付宝 V2 客户端构建失败：" + app.appId(), e);
        }
        AlipayClient created = apiConfig.getAliPayClient();
        if (created == null) {
            throw new PayException("支付宝 V2 客户端构建失败：" + app.appId());
        }
        clients.put(app.appId(), created);
        return created;
    }

    private static String verifyContent(PayAppConfig app, boolean cert) {
        for (PayVerifyKey key : app.verifyKeys()) {
            if ((key.type() == PayVerifyKey.Type.CERT) == cert) {
                return key.certContent();
            }
        }
        throw new PayException("支付宝 V2 验签凭据缺失：" + app.appId());
    }
}
