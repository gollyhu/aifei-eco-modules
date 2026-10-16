/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

/**
 * {@link MqConfig} 的默认值、类型白名单校验、实例前缀与后端专有键读取，以及 {@code mq.names} 的解析与校验。
 */
public class MqConfigTest {

    /**
     * 已注册类型集合，取真实注册表，保证与实现同步。
     */
    private static final Set<String> REGISTERED = new MqProviderRegistry().types();

    /**
     * 主实例名，本类用例中反复用作实例名入参。
     */
    private static final String MAIN = MqConfig.MAIN_INSTANCE_NAME;

    /**
     * 未配置 {@code mq.type} 时取默认内存后端；主实例前缀为根级 {@code mq}。
     */
    @Test
    public void shouldDefaultToMemoryWhenTypeAbsent() {
        MqConfig config = MqConfig.parse(new Properties(), REGISTERED, MAIN);

        assertEquals(MqConfig.DEFAULT_TYPE, config.type());
        assertEquals(MqConfig.ROOT_PREFIX, config.prefix());
        assertEquals(MAIN, config.instanceName());
    }

    /**
     * 显式配置后端类型时按配置取值。
     */
    @Test
    public void shouldUseConfiguredType() {
        MqConfig config = MqConfig.parse(props(MqConfig.TYPE_KEY, "memory"), REGISTERED, MAIN);

        assertEquals("memory", config.type());
        assertEquals("mq", config.prefix());
    }

    /**
     * {@code mq.type} 只含空白时视为未配置，回落到默认类型。
     */
    @Test
    public void shouldDefaultWhenTypeBlank() {
        MqConfig config = MqConfig.parse(props(MqConfig.TYPE_KEY, "   "), REGISTERED, MAIN);

        assertEquals(MqConfig.DEFAULT_TYPE, config.type());
    }

    /**
     * 命名实例读自己的 {@code mq.<name>.type}；未配置时同样回落到默认类型，前缀为 {@code mq.<name>}。
     */
    @Test
    public void shouldParseNamedInstance() {
        MqConfig defaulted = MqConfig.parse(new Properties(), REGISTERED, "orders");
        assertEquals("orders", defaulted.instanceName());
        assertEquals(MqConfig.DEFAULT_TYPE, defaulted.type());
        assertEquals("mq.orders", defaulted.prefix());

        MqConfig configured = MqConfig.parse(
                props("mq.orders.type", "memory", "mq.orders.memory.endpoint", "orders-host"),
                REGISTERED, "orders");
        assertEquals("memory", configured.type());
        assertEquals("mq.orders", configured.prefix());
        assertEquals("orders-host", configured.get("endpoint"));
    }

    /**
     * 未注册的后端类型必须立即失败，主实例与命名实例同理。
     */
    @Test
    public void shouldRejectUnregisteredType() {
        Properties properties = props(MqConfig.TYPE_KEY, "kafka");
        Properties namedProperties = props("mq.orders.type", "kafka");

        assertThrows(IllegalArgumentException.class, () -> MqConfig.parse(properties, REGISTERED, MAIN));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parse(properties, Collections.singleton("memory"), MAIN));
        assertThrows(IllegalArgumentException.class, () -> MqConfig.parse(namedProperties, REGISTERED, "orders"));
    }

    /**
     * 必填入参为 {@code null} 时失败。
     */
    @Test
    public void shouldRejectNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> MqConfig.parse(null, REGISTERED, MAIN));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parse(new Properties(), null, MAIN));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parse(new Properties(), REGISTERED, null));
    }

    /**
     * 后端专有键经 {@code get} 读取，键名由实例前缀与类型拼装，未配置时返回 {@code null}。
     */
    @Test
    public void shouldReadBackendRelativeKeys() {
        MqConfig config = MqConfig.parse(props("mq.memory.endpoint", "local"), REGISTERED, MAIN);

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
        assertEquals("mq.names", MqConfig.NAMES_KEY);
        assertEquals("memory", MqConfig.DEFAULT_TYPE);
        assertEquals("__MAIN__", MqConfig.MAIN_INSTANCE_NAME);
    }

    /**
     * {@code mq.names} 逐项去空白后按声明顺序解析；未配置时返回空列表。
     */
    @Test
    public void shouldParseInstanceNames() {
        assertTrue(MqConfig.parseInstanceNames(new Properties(), MqConfig.NAMES_KEY).isEmpty());
        assertTrue(MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "   "), MqConfig.NAMES_KEY).isEmpty());

        List<String> names = MqConfig.parseInstanceNames(
                props(MqConfig.NAMES_KEY, " orders , payments "), MqConfig.NAMES_KEY);

        assertEquals(2, names.size());
        assertEquals("orders", names.get(0));
        assertEquals("payments", names.get(1));
    }

    /**
     * {@code mq.names} 出现空白项、非法名、保留名、主实例名或重名时失败。
     */
    @Test
    public void shouldRejectInvalidInstanceNames() {
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "orders,,payments"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "orders,"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "orders,orders"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "type"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "names"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "__MAIN__"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "a.b"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "a:b"), MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(props(MqConfig.NAMES_KEY, "a b"), MqConfig.NAMES_KEY));
    }

    /**
     * {@code mq.names} 解析的入参为 {@code null} 时失败。
     */
    @Test
    public void shouldRejectNullArgumentsForInstanceNames() {
        assertThrows(IllegalArgumentException.class, () -> MqConfig.parseInstanceNames(null, MqConfig.NAMES_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(new Properties(), null));
        assertThrows(IllegalArgumentException.class,
                () -> MqConfig.parseInstanceNames(new Properties(), "  "));
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
