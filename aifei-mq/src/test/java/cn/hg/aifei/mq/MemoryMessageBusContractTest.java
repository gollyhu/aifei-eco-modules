/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import cn.hg.aifei.mq.memory.MemoryMessageBus;
import java.util.Properties;
import org.junit.Test;

/**
 * 内存后端继承运行 {@link MessageBusContractTest} 的全部契约。
 */
public class MemoryMessageBusContractTest extends MessageBusContractTest {

    /**
     * {@inheritDoc}
     */
    @Override
    protected MessageBus newBus() {
        MqConfig config = MqConfig.parse(new Properties(), new MqProviderRegistry().types());
        return new MemoryMessageBus(config);
    }

    /**
     * 内存后端总线支持重复创建且各自独立计数。
     */
    @Test
    public void shouldKeepIndependentStatePerBus() {
        MessageBus first = newBus();
        MessageBus second = newBus();
        try {
            String firstId = first.producer().send("t", "1").messageId();
            String secondId = second.producer().send("t", "1").messageId();

            assertEquals("1", firstId);
            assertEquals("1", secondId);
        } finally {
            first.close();
            second.close();
        }
    }
}
