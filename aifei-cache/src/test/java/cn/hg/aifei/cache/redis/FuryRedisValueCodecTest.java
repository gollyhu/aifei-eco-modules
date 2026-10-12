/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.redis;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.internal.AbstractCache;
import cn.hg.aifei.cache.testvalue.CodecValue;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fury.exception.InsecureException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/**
 * {@link FuryRedisValueCodec} 的编解码与反序列化白名单用例，不依赖 Redis。
 *
 * <p>Redis 集成用例在默认构建下整类跳过，本类补上默认构建的告警：白名单漏掉哨兵枚举时
 * {@link #shouldRoundTripSentinel()} 会立即失败，而不是等到发布后写 Redis 才暴露。</p>
 */
public class FuryRedisValueCodecTest {

    private final FuryRedisValueCodec codec = new FuryRedisValueCodec();

    /**
     * JDK 内置值类型无需声明即可往返。
     */
    @Test
    public void shouldRoundTripJdkValues() {
        assertEquals("tom", codec.deserialize(codec.serialize("tom")));
        assertEquals(42, codec.deserialize(codec.serialize(42)));

        Map<String, String> map = new HashMap<>();
        map.put("a", "1");
        assertEquals(map, codec.deserialize(codec.serialize(map)));

        List<String> list = new ArrayList<>(Arrays.asList("x", "y"));
        assertEquals(list, codec.deserialize(codec.serialize(list)));
    }

    /**
     * 哨兵值必须能往返；白名单条目写成 {@code …NullValue$INSTANCE} 时本条失败。
     */
    @Test
    public void shouldRoundTripSentinel() {
        Object sentinel = AbstractCache.toStoredValue(null, true);
        Object restored = codec.deserialize(codec.serialize(sentinel));
        assertSame("哨兵往返后仍应是同一个枚举实例", sentinel, restored);
        assertNull("哨兵换算成业务值必须是 null", AbstractCache.fromStoredValue(restored));
    }

    /**
     * 未声明的业务值类型被拒绝，且拦截发生在写入侧。
     *
     * <p>本用例使用测试类自身的嵌套类型，其包名不会被任何用例放进白名单，因此与
     * {@link #shouldAllowDeclaredPackage()} 无执行顺序依赖。</p>
     */
    @Test
    public void shouldRejectUndeclaredBusinessValue() {
        assertThrows(InsecureException.class, () -> codec.serialize(new UndeclaredValue("n")));
    }

    /**
     * 经 {@code cache.jedis.allowedPackages} 声明的包被放行后可正常往返。
     *
     * <p>放行是进程级且只增不减的，故本条只放行专门准备的 {@code cn.hg.aifei.cache.testvalue} 包。</p>
     */
    @Test
    public void shouldAllowDeclaredPackage() {
        CodecValue value = new CodecValue("n", 7);
        assertThrows(InsecureException.class, () -> codec.serialize(value));

        FuryRedisValueCodec.allowPackages(Arrays.asList("cn.hg.aifei.cache.testvalue"));

        CodecValue restored = (CodecValue) codec.deserialize(codec.serialize(value));
        assertEquals("n", restored.name);
        assertEquals(7, restored.amount);
    }

    /**
     * 默认不被放行的业务值类型。
     */
    public static class UndeclaredValue implements Serializable {

        private static final long serialVersionUID = 1L;

        public String name;

        public UndeclaredValue(String name) {
            this.name = name;
        }
    }
}
