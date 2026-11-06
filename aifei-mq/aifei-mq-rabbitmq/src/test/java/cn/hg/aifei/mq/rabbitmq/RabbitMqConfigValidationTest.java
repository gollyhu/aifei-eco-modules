/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rabbitmq;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

/**
 * rabbitmq 后端的<b>配置校验</b>用例：必填键缺失与 SSL 参数非法时，总线构造必须在<b>任何建连动作之前</b>
 * 以 {@link MqException} / {@link IllegalArgumentException} 快速失败。
 *
 * <p>本类<b>默认执行</b>、<b>不依赖 broker</b>：全部校验与 SSL 上下文构建都位于 {@code newConnection()}
 * 之前，唯一触碰网络的用例把端口指向必然不可达的 {@code 1}，故失败点仍落在建连阶段。</p>
 */
public class RabbitMqConfigValidationTest {

    /**
     * 已注册类型集合，取真实注册表，保证断言与实现同步。
     */
    private static final Set<String> REGISTERED = new MqProviderRegistry().types();

    /**
     * 本后端的配置键前缀（模拟使用者的真实写法）。
     */
    private static final String PREFIX = "mq." + RabbitMqProvider.TYPE + ".";

    /**
     * 临时密钥库口令。
     */
    private static final String PASSWORD = "changeit";

    /**
     * RabbitMQ：缺 {@code host} 时构造立即抛 {@link MqException}（在建连之前）。
     */
    @Test
    public void shouldFailFastWhenHostMissing() {
        MqException failure = assertThrows(MqException.class,
                () -> new RabbitMqMessageBus(configOf(baseProperties())));
        assertTrue("异常消息应指明缺失的必填键 host，实际为：" + failure.getMessage(),
                failure.getMessage() != null && failure.getMessage().contains("host"));
    }

    /**
     * SSL 开关只接受布尔字面量。
     */
    @Test
    public void shouldRejectNonBooleanSslSwitch() {
        Properties properties = connectableProperties();
        properties.setProperty(PREFIX + "ssl.enabled", "yes");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new RabbitMqMessageBus(configOf(properties)));
        assertTrue("异常消息应指明非法键 ssl.enabled，实际为：" + failure.getMessage(),
                failure.getMessage() != null && failure.getMessage().contains("ssl.enabled"));
    }

    /**
     * SSL 开启且信任库路径不可读时，在建连之前抛 {@link MqException}，消息含路径键名。
     */
    @Test
    public void shouldFailFastWhenSslTrustStoreMissing() {
        Properties properties = connectableProperties();
        properties.setProperty(PREFIX + "ssl.enabled", "true");
        properties.setProperty(PREFIX + "ssl.trust-store", "target/rabbitmq-ssl/does-not-exist.p12");

        MqException failure = assertThrows(MqException.class, () -> new RabbitMqMessageBus(configOf(properties)));
        assertTrue("异常消息应指明不可读的键 ssl.trust-store，实际为：" + failure.getMessage(),
                failure.getMessage() != null && failure.getMessage().contains("ssl.trust-store"));
    }

    /**
     * SSL 算法非法时，在建连之前抛 {@link MqException}。
     */
    @Test
    public void shouldFailFastWhenSslAlgorithmInvalid() {
        Properties properties = connectableProperties();
        properties.setProperty(PREFIX + "ssl.enabled", "true");
        properties.setProperty(PREFIX + "ssl.algorithm", "NoSuchAlgorithm");

        MqException failure = assertThrows(MqException.class, () -> new RabbitMqMessageBus(configOf(properties)));
        assertTrue("异常消息应指明 SSL 上下文初始化失败，实际为：" + failure.getMessage(),
                failure.getMessage() != null && failure.getMessage().contains("ssl context"));
    }

    /**
     * 可读的信任库能构建出 SSL 上下文：失败点顺延到建连阶段，消息不含 {@code ssl}。
     *
     * @throws IOException 临时密钥库读写失败
     * @throws GeneralSecurityException 密钥库生成失败
     */
    @Test
    public void shouldBuildSslContextBeforeConnecting() throws IOException, GeneralSecurityException {
        Path trustStore = Files.createTempFile("rabbitmq-trust-store", ".jks");
        trustStore.toFile().deleteOnExit();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] password = PASSWORD.toCharArray();
        keyStore.load(null, password);
        try (OutputStream out = Files.newOutputStream(trustStore)) {
            keyStore.store(out, password);
        }

        Properties properties = connectableProperties();
        properties.setProperty(PREFIX + "ssl.enabled", "true");
        properties.setProperty(PREFIX + "ssl.trust-store", trustStore.toString());
        properties.setProperty(PREFIX + "ssl.trust-store-type", "JKS");
        properties.setProperty(PREFIX + "ssl.trust-store-password", PASSWORD);

        MqException failure = assertThrows(MqException.class, () -> new RabbitMqMessageBus(configOf(properties)));
        assertFalse("SSL 上下文应构建成功、失败应落在建连阶段，实际为：" + failure.getMessage(),
                failure.getMessage() != null && failure.getMessage().contains("ssl"));
    }

    /**
     * 只声明后端类型、<b>不含该后端任何配置键</b>的配置项。
     *
     * @return 仅含 {@code mq.type} 的配置项
     */
    private static Properties baseProperties() {
        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, RabbitMqProvider.TYPE);
        return properties;
    }

    /**
     * 含必填键 {@code host} 与不可达端口的配置项：使构造越过必填校验后仍必然在建连处失败。
     *
     * @return 配置项
     */
    private static Properties connectableProperties() {
        Properties properties = baseProperties();
        properties.setProperty(PREFIX + "host", "127.0.0.1");
        properties.setProperty(PREFIX + "port", "1");
        properties.setProperty(PREFIX + "connection-timeout-millis", "1000");
        return properties;
    }

    /**
     * 解析为配置对象。
     *
     * @param properties 配置项
     * @return 主实例的配置对象
     */
    private static MqConfig configOf(Properties properties) {
        return MqConfig.parse(properties, REGISTERED, MqConfig.MAIN_INSTANCE_NAME);
    }
}
