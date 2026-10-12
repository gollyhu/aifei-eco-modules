/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.redis;

import cn.hg.aifei.cache.elementvalue.ElementValue;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fury.exception.InsecureException;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 数组值形态的往返用例：业务类型数组与多维数组，不依赖 Redis。
 *
 * <p>把两个口径翻译成可执行的对照，每组只改一个变量：</p>
 * <ul>
 *   <li><b>元素类型</b>：{@code String[]} 与 {@code ElementValue[]} 都是一维对象数组，
 *       元素是 JDK 标量还是业务类型是唯一差别 —— 后者即「业务类型数组」。</li>
 *   <li><b>维度</b>：{@code int[]} 与 {@code int[][]} 元素完全相同，维度是唯一差别；
 *       {@code String[]} 与 {@code String[][]} 同理 —— 后者即「多维数组」，与元素类型无关。</li>
 * </ul>
 *
 * <p>其中 {@code java.time} 数组、{@code BigDecimal[]} 与 {@code Object[]{LocalDate[]}} 是
 * <b>判别性样本</b> —— 它们正是「按元素类归一化」那条路线修不好、只有放行数组维度才能一次覆盖的
 * 形态，故必须钉住。</p>
 *
 * <p>本类补上默认构建的告警：Redis 集成用例在默认构建下整类跳过，而数组值的缺陷正是被这道
 * 门控放过的。</p>
 */
public class ArrayValueShapeTest {

    private final FuryRedisValueCodec codec = new FuryRedisValueCodec();

    @BeforeClass
    public static void setUp() {
        // 等价于配置 cache.jedis.allowedPackages = cn.hg.aifei.cache.elementvalue
        FuryRedisValueCodec.allowPackages(Arrays.asList("cn.hg.aifei.cache.elementvalue"));
    }

    /**
     * 一维数组的既有能力不得回退：基本类型、JDK 标量、装箱基本类型都要继续往返。
     */
    @Test
    public void shouldKeepRoundTrippingSingleDimensionalArrays() {
        assertArrayEquals(new int[] {1, 2},
                (int[]) codec.deserialize(codec.serialize(new int[] {1, 2})));
        assertArrayEquals(new byte[] {1, 2},
                (byte[]) codec.deserialize(codec.serialize(new byte[] {1, 2})));
        assertArrayEquals(new String[] {"a", "b"},
                (String[]) codec.deserialize(codec.serialize(new String[] {"a", "b"})));
        assertArrayEquals(new Integer[] {1, 2},
                (Integer[]) codec.deserialize(codec.serialize(new Integer[] {1, 2})));
        assertArrayEquals(new Long[] {1L, 2L},
                (Long[]) codec.deserialize(codec.serialize(new Long[] {1L, 2L})));
        assertArrayEquals(new Boolean[] {true, false},
                (Boolean[]) codec.deserialize(codec.serialize(new Boolean[] {true, false})));
    }

    /**
     * 业务类型数组：一维对象数组、元素是已声明的业务类型。
     */
    @Test
    public void shouldRoundTripBusinessElementArray() {
        ElementValue[] restored = (ElementValue[]) codec
                .deserialize(codec.serialize(new ElementValue[] {new ElementValue("n", 7)}));

        assertEquals(1, restored.length);
        assertEquals("n", restored[0].name);
        assertEquals(7, restored[0].amount);
    }

    /**
     * 多维数组：与元素类型无关，基本类型、JDK 标量、装箱类型、业务类型都要往返。
     */
    @Test
    public void shouldRoundTripMultiDimensionalArrays() {
        int[][] matrix = (int[][]) codec.deserialize(codec.serialize(new int[][] {{1, 2}, {3, 4}}));
        assertEquals(2, matrix.length);
        assertArrayEquals(new int[] {1, 2}, matrix[0]);
        assertArrayEquals(new int[] {3, 4}, matrix[1]);

        byte[][] bytes = (byte[][]) codec.deserialize(codec.serialize(new byte[][] {{1}, {2}}));
        assertEquals(2, bytes.length);
        assertArrayEquals(new byte[] {1}, bytes[0]);
        assertArrayEquals(new byte[] {2}, bytes[1]);

        String[][] table = (String[][]) codec
                .deserialize(codec.serialize(new String[][] {{"a"}, {"b"}}));
        assertEquals(2, table.length);
        assertArrayEquals(new String[] {"a"}, table[0]);
        assertArrayEquals(new String[] {"b"}, table[1]);

        Integer[][] boxed = (Integer[][]) codec
                .deserialize(codec.serialize(new Integer[][] {{1}, {2}}));
        assertEquals(2, boxed.length);
        assertArrayEquals(new Integer[] {1}, boxed[0]);
        assertArrayEquals(new Integer[] {2}, boxed[1]);

        ElementValue[][] nested = (ElementValue[][]) codec
                .deserialize(codec.serialize(new ElementValue[][] {{new ElementValue("n", 7)}}));
        assertEquals(1, nested.length);
        assertEquals("n", nested[0][0].name);
        assertEquals(7, nested[0][0].amount);
    }

    /**
     * 判别性样本：{@code java.time} 数组与 {@code BigDecimal[]}。
     *
     * <p>这三项是判别性样本。元素类是 JDK 类型而非业务类型，且 Fury 对它们有内置注册序列化器、
     * 根本不咨询白名单（这就是「标量 {@code LocalDate} 正常、{@code LocalDate[]} 失败」的原因），
     * 因此「把数组类名归一化到元素类再判定」的修法对它们无效 —— 只有放行数组维度才能覆盖。</p>
     */
    @Test
    public void shouldRoundTripJavaTypedArrays() {
        LocalDate[] dates = (LocalDate[]) codec
                .deserialize(codec.serialize(new LocalDate[] {LocalDate.of(2026, 9, 22)}));
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 9, 22)}, dates);

        LocalDateTime[] dateTimes = (LocalDateTime[]) codec.deserialize(
                codec.serialize(new LocalDateTime[] {LocalDateTime.of(2026, 9, 22, 10, 0)}));
        assertArrayEquals(new LocalDateTime[] {LocalDateTime.of(2026, 9, 22, 10, 0)}, dateTimes);

        BigDecimal[] decimals = (BigDecimal[]) codec
                .deserialize(codec.serialize(new BigDecimal[] {new BigDecimal("1.50")}));
        assertEquals(1, decimals.length);
        assertEquals(0, new BigDecimal("1.50").compareTo(decimals[0]));
    }

    /**
     * 「数组套数组」：外层是 {@code Object[]}，元素本身又是数组。
     */
    @Test
    public void shouldRoundTripArrayNestedInObjectArray() {
        Object[] restored = (Object[]) codec.deserialize(
                codec.serialize(new Object[] {new LocalDate[] {LocalDate.of(2026, 9, 22)}}));

        assertEquals(1, restored.length);
        assertTrue(restored[0] instanceof LocalDate[]);
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 9, 22)}, (LocalDate[]) restored[0]);
    }

    /**
     * 混装对象数组：既不是业务类型数组、也不是多维数组，属既有能力，数组维度放行后不得回退。
     */
    @Test
    public void shouldRoundTripMixedObjectArray() {
        Object[] restored = (Object[]) codec.deserialize(codec.serialize(new Object[] {"a", 1}));
        assertArrayEquals(new Object[] {"a", 1}, restored);
    }

    /**
     * 标量与集合：修复前就正常，数组维度放行后不得回退。
     *
     * <p>放行是并集累加（{@code allowClass} 只增不减），故这一侧在机制上不可能回退；本用例把它
     * 钉成可执行断言。</p>
     */
    @Test
    public void shouldKeepRoundTrippingScalarsAndCollections() {
        assertEquals(LocalDate.of(2026, 9, 22),
                codec.deserialize(codec.serialize(LocalDate.of(2026, 9, 22))));
        assertEquals(0, new BigDecimal("1.50").compareTo(
                (BigDecimal) codec.deserialize(codec.serialize(new BigDecimal("1.50")))));

        ElementValue element = new ElementValue("n", 7);
        ElementValue restoredElement = (ElementValue) codec.deserialize(codec.serialize(element));
        assertEquals("n", restoredElement.name);
        assertEquals(7, restoredElement.amount);

        List<Object> list = new ArrayList<>(
                Arrays.asList(LocalDate.of(2026, 9, 22), new BigDecimal("1.50"), element));
        List<?> restoredList = (List<?>) codec.deserialize(codec.serialize(list));
        assertEquals(3, restoredList.size());
        assertEquals(LocalDate.of(2026, 9, 22), restoredList.get(0));
        assertEquals(0, new BigDecimal("1.50").compareTo((BigDecimal) restoredList.get(1)));
        assertEquals("n", ((ElementValue) restoredList.get(2)).name);

        Map<String, Object> map = new HashMap<>();
        map.put("date", LocalDate.of(2026, 9, 22));
        map.put("value", new BigDecimal("1.50"));
        @SuppressWarnings("unchecked")
        Map<String, Object> restoredMap = (Map<String, Object>) codec.deserialize(codec.serialize(map));
        assertEquals(LocalDate.of(2026, 9, 22), restoredMap.get("date"));
        assertEquals(0, new BigDecimal("1.50").compareTo((BigDecimal) restoredMap.get("value")));
    }

    /**
     * 放行数组维度不得架空拒绝清单：数组形态的危险类型仍须在写入侧被拒。
     */
    @Test
    public void shouldStillRejectForbiddenClassInArrayForm() {
        assertThrows(InsecureException.class,
                () -> codec.serialize(new Runtime[] {Runtime.getRuntime()}));
    }

    /**
     * 放行数组维度不得架空业务白名单：元素类未声明的数组仍须在写入侧被拒。
     *
     * <p>用本测试类的嵌套类型作元素，其包名不会被任何用例放进白名单，故不依赖执行顺序。</p>
     */
    @Test
    public void shouldStillRejectUndeclaredElementArray() {
        assertThrows(InsecureException.class,
                () -> codec.serialize(new UndeclaredElement[] {new UndeclaredElement("n")}));
        assertThrows(InsecureException.class,
                () -> codec.serialize(new UndeclaredElement[][] {{new UndeclaredElement("n")}}));
    }

    /**
     * 未声明的业务值类型样本。
     */
    public static class UndeclaredElement implements Serializable {

        private static final long serialVersionUID = 1L;

        public String name;

        public UndeclaredElement(String name) {
            this.name = name;
        }
    }
}
