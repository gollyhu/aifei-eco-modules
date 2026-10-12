/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

import cn.aifei.util.Prop;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;

/**
 * 测试共用工具：可控时钟与 {@link Prop} 构造。
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * 由键值对构造 {@link Prop}；空值也会被写入，用于验证「空值视为未配置」。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置对象
     */
    public static Prop prop(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be key/value pairs");
        }
        Prop prop = new Prop();
        for (int i = 0; i < keyValues.length; i += 2) {
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }

    /**
     * 手动推进的 Caffeine 时钟，用于确定性过期用例。
     */
    public static final class FakeTicker implements Ticker {

        private long nanos;

        /**
         * 推进时钟。
         *
         * @param amount 推进量
         */
        public void advance(Duration amount) {
            nanos += amount.toNanos();
        }

        @Override
        public long read() {
            return nanos;
        }
    }
}
