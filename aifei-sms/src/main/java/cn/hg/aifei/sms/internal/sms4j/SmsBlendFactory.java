/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import cn.hg.aifei.sms.SmsClient;
import cn.hg.aifei.sms.internal.SmsChannelConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.universal.SupplierConfig;
import org.dromara.sms4j.core.factory.SmsFactory;

/**
 * sms4j {@link SmsBlend} 构建唯一装配点。
 *
 * <p>默认经 {@code SmsFactory.createSmsBlend(config)} 编程式构建并按 configId 取回实例；
 * 包级注入点 {@link #setBlendFactory(Function)} 供测试与 demo 注入返回预设响应的 mock，
 * 不进入公共面。本类返回 {@link SmsClient} 映射，使 {@code SmsBlend} 类型不出适配层。</p>
 */
public final class SmsBlendFactory {

    private static final Function<SupplierConfig, SmsBlend> DEFAULT_FACTORY = new Function<SupplierConfig, SmsBlend>() {
        @Override
        public SmsBlend apply(SupplierConfig config) {
            SmsFactory.createSmsBlend(config);
            return SmsFactory.getSmsBlend(config.getConfigId());
        }
    };

    private static Function<SupplierConfig, SmsBlend> blendFactory = DEFAULT_FACTORY;

    private SmsBlendFactory() {
    }

    /**
     * 替换构建函数；传 {@code null} 恢复默认真实工厂。
     *
     * @param factory 构建函数（包级注入，仅同包测试 / 反射桥接调用）
     */
    static void setBlendFactory(Function<SupplierConfig, SmsBlend> factory) {
        blendFactory = factory == null ? DEFAULT_FACTORY : factory;
    }

    /**
     * 按通道配置构建全部客户端实例（每个通道独立 {@code SmsBlend}）。
     *
     * @param configs 已校验的通道配置
     * @return 通道标识到客户端的映射（保持配置顺序）
     */
    public static Map<String, SmsClient> buildClients(List<SmsChannelConfig> configs) {
        Map<String, SmsClient> clients = new LinkedHashMap<String, SmsClient>();
        for (SmsChannelConfig config : configs) {
            SupplierConfig supplierConfig = SmsSupplierTypes.newSupplierConfig(
                    config.getType(), config.getName(), config.getOptions());
            SmsBlend blend = blendFactory.apply(supplierConfig);
            clients.put(config.getName(), new SmsSender(config.getName(), blend));
        }
        return clients;
    }
}
