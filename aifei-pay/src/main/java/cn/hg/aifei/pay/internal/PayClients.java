/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import com.alipay.v3.ApiClient;
import com.alipay.v3.ApiException;
import com.alipay.v3.util.GenericExecuteApi;
import com.alipay.v3.util.model.AlipayConfig;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ApiClient} 与 {@code GenericExecuteApi} 的构建与缓存：每个 appId 独立一套实例。
 *
 * <p>不使用进程级默认客户端，也不使用静态多应用注册表——否则多应用会互相覆盖网关与凭据
 * （`docs/design.md` §2.4.5）。缓存是「装配期一次建成、调用期只读」的形态，因此网关与凭据在同一
 * 进程内不可变。</p>
 */
public final class PayClients {

    private final PayConfig config;

    /**
     * appId → 独立客户端实例。
     */
    private final Map<String, ApiClient> apiClients = new ConcurrentHashMap<>();

    /**
     * appId → 独立页面接口实例（H5 / PC 下单必须走它，才有多应用隔离）。
     */
    private final Map<String, GenericExecuteApi> pageApis = new ConcurrentHashMap<>();

    /**
     * @param config 配置域
     */
    public PayClients(PayConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        this.config = config;
    }

    /**
     * 预建全部应用的实例：装配期把内核类型的构造动作集中完成，避免调用期首次构造失败。
     */
    public void preload() {
        for (String appId : config.apps().keySet()) {
            apiClient(appId);
            pageApi(appId);
        }
    }

    /**
     * 取指定应用的客户端，缺失时构建并缓存。
     *
     * @param appId 应用编号
     * @return 该应用独占的客户端实例
     */
    public ApiClient apiClient(String appId) {
        ApiClient cached = apiClients.get(appId);
        if (cached != null) {
            return cached;
        }
        synchronized (apiClients) {
            cached = apiClients.get(appId);
            if (cached == null) {
                cached = build(config.resolve(appId));
                apiClients.put(appId, cached);
            }
            return cached;
        }
    }

    /**
     * 取指定应用的页面接口，缺失时构建并缓存。
     *
     * @param appId 应用编号
     * @return 该应用独占的页面接口实例
     */
    public GenericExecuteApi pageApi(String appId) {
        GenericExecuteApi cached = pageApis.get(appId);
        if (cached != null) {
            return cached;
        }
        synchronized (pageApis) {
            cached = pageApis.get(appId);
            if (cached == null) {
                cached = new GenericExecuteApi(apiClient(appId));
                pageApis.put(appId, cached);
            }
            return cached;
        }
    }

    /**
     * 构建单个应用的客户端。
     *
     * @param app 应用配置
     * @return 客户端实例
     */
    private ApiClient build(PayAppConfig app) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(app.gatewayUrl());
        apiClient.setConnectTimeout(app.connectTimeout());
        apiClient.setReadTimeout(app.readTimeout());
        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setAppId(app.appId());
        alipayConfig.setPrivateKey(app.privateKey());
        if (app.isCertMode()) {
            alipayConfig.setAppCertContent(app.appCert());
            alipayConfig.setAlipayPublicCertContent(firstCertContent(app));
            alipayConfig.setRootCertContent(app.rootCert());
        } else {
            alipayConfig.setAlipayPublicKey(app.verifyKeys().get(0).publicKeyText());
        }
        try {
            apiClient.setAlipayConfig(alipayConfig);
        } catch (ApiException e) {
            throw PayErrors.kernelFailure("初始化 ApiClient", e);
        }
        return apiClient;
    }

    /**
     * 取第一个证书类型凭据的证书内容，用于证书模式下单时的支付宝公钥证书注入。
     *
     * @param app 应用配置
     * @return 证书内容
     */
    private static String firstCertContent(PayAppConfig app) {
        for (PayVerifyKey key : app.verifyKeys()) {
            if (key.type() == PayVerifyKey.Type.CERT) {
                return key.certContent();
            }
        }
        throw PayErrors.invalidConfig(PayConfig.APP_PREFIX + app.appId() + ".verify.0.type",
                PayVerifyKey.Type.CERT.name().toLowerCase(Locale.ROOT),
                "证书模式下单要求至少配置一组 cert 类型的验签凭据");
    }
}
