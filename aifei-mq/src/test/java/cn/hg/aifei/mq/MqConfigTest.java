/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

/**
 * {@link MqConfig} 的默认值、类型白名单校验、键前缀与后端专有键读取。
 */
public class MqConfigTest {

    /**
     * 已注册类型集合，取真实注册表，保证与实现同步。
     */
    private static final Set<String> REGISTERED = new MqProviderRegistry().types();

    /**
     * 未配置 {@code mq.type} 时取默认内存后端。
     */
    @Test
    public void shouldDefaultToMemoryWhenTypeAbsent() {
        MqConfig config = MqConfig.parse(new Properties(), REGISTERED);

        assertEquals(MqConfig.DEFAULT_TYPE, config.type());
        assertEquals("mq." + MqConfig.DEFAULT_TYPE, config.prefix());
    }

    /**
     * 显式配置后端类型时按配置取值。
     */
    @Test
    public void shouldUseConfiguredType() {
        MqConfig config = MqConfig.parse(props(MqConfig.TYPE_KEY, "memory"), REGISTERED);

        assertEquals("memory", config.type());
        assertEquals("mq.memory", config.prefix());
    }

    /**
     * {@code mq.type} 只含空白时视为未配置，回落到默认类型。
     */
    @Test
    public void shouldDefaultWhenTypeBlank() {
        MqConfig config = MqConfig.parse(props(MqConfig.TYPE_KEY, "   "), REGISTERED);

        assertEquals(MqConfig.DEFAULT_TYPE, config.type());
    }

    /**
     * 未注册的后端类型必须立即失败。
     */
    @Test
    public void shouldRejectUnregisteredType() {
        Properties properties = props(MqConfig.TYPE_KEY, "kafka");

        assertThrows(IllegalArgumentException.class, () -> MqConfig.parse(properties, REGISTERED));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parse(properties, Collections.singleton("memory")));
    }

    /**
     * 必填入参为 {@code null} 时失败。
     */
    @Test
    public void shouldRejectNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> MqConfig.parse(null, REGISTERED));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parse(new Properties(), null));
    }

    /**
     * 后端专有键经 {@code get} 读取，键名由前缀拼装，未配置时返回 {@code null}。
     */
    @Test
    public void shouldReadBackendRelativeKeys() {
        MqConfig config = MqConfig.parse(props("mq.memory.endpoint", "local"), REGISTERED);

        assertEquals("local", config.get("endpoint"));
        assertNull(config.get("missing"));
    }

    /**
     * 常量取值与设计契约一致。
     */
    @Test
    public void shouldExposeContractConstants() {
        assertEquals("mq", MqConfig.ROOT_PREFIX);
        assertEquals("mq.type", MqConfig.TYPE_KEY);
        assertEquals("memory", MqConfig.DEFAULT_TYPE);
    }

    /**
     * 构造键值对配置；交替出现的键与值。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置
     */
    private static Properties props(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }
}
