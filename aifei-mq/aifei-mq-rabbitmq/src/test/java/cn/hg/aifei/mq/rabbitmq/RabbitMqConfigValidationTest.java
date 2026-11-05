/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rabbitmq;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

/**
 * rabbitmq 后端的<b>配置校验</b>用例：缺少必填键 {@code host} 时，总线构造必须在<b>任何建连动作之前</b>
 * 以 {@link MqException} 快速失败。
 *
 * <p>本类<b>默认执行</b>、<b>不建连</b>、<b>无 broker 依赖</b>：必填键校验位于创建 {@code ConnectionFactory} 与
 * {@code newConnection()} 之前，故缺键时用例立即失败而不阻塞，无须 broker 可达，也无须以极小超时兜底。</p>
 */
public class RabbitMqConfigValidationTest {

    /**
     * 已注册类型集合，取真实注册表，保证断言与实现同步。
     */
    private static final Set<String> REGISTERED = new MqProviderRegistry().types();

    /**
     * RabbitMQ：缺 {@code host} 时构造立即抛 {@link MqException}（在建连之前）。
     */
    @Test
    public void shouldFailFastWhenHostMissing() {
        MqConfig config = configOf(RabbitMqProvider.TYPE);

        MqException failure = assertThrows(MqException.class, () -> new RabbitMqMessageBus(config));
        assertTrue("异常消息应指明缺失的必填键 host，实际为：" + failure.getMessage(),
                failure.getMessage() != null && failure.getMessage().contains("host"));
    }

    /**
     * 构造只声明后端类型、<b>不含该后端任何必填键</b>的配置对象。
     *
     * @param type 后端类型标识
     * @return 仅含 {@code mq.type} 的配置对象
     */
    private static MqConfig configOf(String type) {
        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, type);
        return MqConfig.parse(properties, REGISTERED, MqConfig.MAIN_INSTANCE_NAME);
    }
}
