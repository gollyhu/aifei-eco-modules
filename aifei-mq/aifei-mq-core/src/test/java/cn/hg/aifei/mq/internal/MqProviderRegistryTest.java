/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.MqException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

/**
 * {@link MqProviderRegistry} 的按类型建索引、重复类型冲突、缺失类型报错与消息不泄露配置取值。
 *
 * <p>本类位于 {@code internal} 包，以便直接使用包内可见的 {@link MqProviderRegistry#index(Iterable)} 构造
 * 无法经 classpath 复现的场景（如同一 type 多实现）。</p>
 */
public class MqProviderRegistryTest {

    /**
     * 假配置中的凭据取值：异常消息里不得出现。
     */
    private static final String SECRET = "s3cr3t-p@ssw0rd";

    /**
     * 按 {@code type()} 建索引，命中正确且保持顺序；真实发现结果的类型也能取到同类型 provider。
     */
    @Test
    public void shouldIndexProvidersByType() {
        Map<String, MqProvider> indexed = MqProviderRegistry.index(Arrays.asList(
                new FixedTypeProvider("alpha"),
                new FixedTypeProvider("beta")
        ));

        assertEquals(Arrays.asList("alpha", "beta"), new ArrayList<>(indexed.keySet()));
        assertEquals("alpha", indexed.get("alpha").type());
        assertEquals("beta", indexed.get("beta").type());
        assertFalse(indexed.containsKey("gamma"));

        MqProviderRegistry registry = new MqProviderRegistry();
        for (String type : registry.types()) {
            assertNotNull("类型不得为空", type);
            assertEquals("types() 中的每个类型都应能取到同类型 provider", type, registry.getProvider(type).type());
        }
        assertThrows("types() 不得被外部修改", UnsupportedOperationException.class,
                () -> registry.types().add("gamma"));
    }

    /**
     * 同一 {@code type} 出现多个实现时必须失败，并在消息中列出冲突的 provider 类名，不静默择一。
     */
    @Test
    public void shouldRejectDuplicateType() {
        List<MqProvider> providers = Arrays.asList(new DuplicateTypeProviderA(), new DuplicateTypeProviderB());

        MqException failure = assertThrows(MqException.class, () -> MqProviderRegistry.index(providers));

        String message = failure.getMessage();
        assertTrue("消息应含后端类型，实际：" + message, message.contains("dup"));
        assertTrue("消息应列出冲突实现 A，实际：" + message,
                message.contains(DuplicateTypeProviderA.class.getName()));
        assertTrue("消息应列出冲突实现 B，实际：" + message,
                message.contains(DuplicateTypeProviderB.class.getName()));
    }

    /**
     * 缺失类型时失败：消息含 {@code mq.type=<type>} 与补齐依赖的提示，且不泄露任何配置取值。
     */
    @Test
    public void shouldFailWithoutLeakingCredentialsWhenTypeMissing() {
        MqProviderRegistry registry = new MqProviderRegistry();

        MqException fromRegistry = assertThrows(MqException.class, () -> registry.getProvider("nosuch"));
        assertTrue("消息应含 mq.type=nosuch，实际：" + fromRegistry.getMessage(),
                fromRegistry.getMessage().contains("mq.type=nosuch"));
        assertTrue("消息应提示补齐后端子模块，实际：" + fromRegistry.getMessage(),
                fromRegistry.getMessage().contains("aifei-mq-memory"));

        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, "nosuch");
        properties.setProperty("mq.nosuch.password", SECRET);
        properties.setProperty("mq.nosuch.username", SECRET);

        MqException fromConfig = assertThrows(MqException.class, () -> MqConfig.parse(
                properties, Collections.singleton(MqConfig.DEFAULT_TYPE), MqConfig.MAIN_INSTANCE_NAME));

        String message = fromConfig.getMessage();
        assertTrue("消息应含 mq.type=nosuch，实际：" + message, message.contains("mq.type=nosuch"));
        assertTrue("消息应提示补齐后端子模块，实际：" + message, message.contains("aifei-mq-memory"));
        assertFalse("消息不得泄露配置取值，实际：" + message, message.contains(SECRET));
    }

    /**
     * 固定类型的桩 provider；只用于建索引，不创建总线。
     */
    private static class FixedTypeProvider implements MqProvider {

        /**
         * 本桩声明的后端类型。
         */
        private final String type;

        /**
         * 创建桩。
         *
         * @param type 后端类型
         */
        private FixedTypeProvider(String type) {
            this.type = type;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String type() {
            return type;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MessageBus createBus(MqConfig config) {
            throw new UnsupportedOperationException("桩 provider 不创建总线");
        }
    }

    /**
     * 与 {@link DuplicateTypeProviderB} 声明同一类型 {@code dup} 的桩 provider。
     */
    private static final class DuplicateTypeProviderA extends FixedTypeProvider {

        /**
         * 创建桩。
         */
        private DuplicateTypeProviderA() {
            super("dup");
        }
    }

    /**
     * 与 {@link DuplicateTypeProviderA} 声明同一类型 {@code dup} 的桩 provider。
     */
    private static final class DuplicateTypeProviderB extends FixedTypeProvider {

        /**
         * 创建桩。
         */
        private DuplicateTypeProviderB() {
            super("dup");
        }
    }
}
