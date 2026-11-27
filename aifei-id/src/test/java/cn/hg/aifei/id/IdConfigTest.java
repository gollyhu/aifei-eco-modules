/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.id.internal.IdConfig;
import java.util.Properties;
import org.junit.Test;

/**
 * 配置域单元测试：8 个配置键的默认值、生效范围、边界值与全部非法取值的消息逐字断言。
 *
 * <p>覆盖配置缺失 / 越界 / 非整数（parse 层）以及与生效策略无关的键
 * 不读、不校验。装配层的「启动期拒绝且不留半装配」属装配入口职责。</p>
 *
 * <p>消息断言一律按固定文案逐字比对，避免用常量拼期望值——拼装式期望值会跟着实现的措辞漂移，
 * 失去逐字对齐的意义。</p>
 */
public class IdConfigTest {

    // ─────────── 默认值与生效范围 ───────────

    /**
     * 雪花策略：只配必填的 workerId，其余取默认值，号段键即使存在也不生效。
     */
    @Test
    public void snowflakeDefaults() {
        IdConfig config = IdConfig.parse(props("id.workerId", "10"));
        assertEquals("snowflake", config.strategy());
        assertEquals("config", config.workerSource());
        assertEquals(10, config.workerId());
        assertEquals(30000L, config.workerLeaseMillis());
        assertEquals(1000, config.segmentStep());
        assertEquals("default", config.segmentKey());
        assertEquals("cache", config.segmentStore());
        assertEquals(31536000000L, config.stateTtlMillis());
    }

    /**
     * 号段策略：workerId 键不再必填，号段键取默认值。
     */
    @Test
    public void segmentDefaults() {
        IdConfig config = IdConfig.parse(props("id.strategy", "segment"));
        assertEquals("segment", config.strategy());
        assertEquals(1000, config.segmentStep());
        assertEquals("default", config.segmentKey());
        assertEquals("cache", config.segmentStore());
        assertEquals(31536000000L, config.stateTtlMillis());
        assertEquals("config", config.workerSource());
        assertEquals(0, config.workerId());
        assertEquals(30000L, config.workerLeaseMillis());
    }

    /**
     * 场景 16：配置整体缺失时，默认策略的必填键先于其它校验失败。
     */
    @Test
    public void nullPropertiesRejected() {
        assertEquals("id.workerId is required when id.worker.source=config", messageOf(null));
    }

    /**
     * 场景 16：必填键配成空白等同缺失（去空白后为空串）。
     */
    @Test
    public void blankValuesTreatedAsMissing() {
        assertEquals("id.workerId is required when id.worker.source=config",
                messageOf(props("id.workerId", "   ")));
    }

    /**
     * 取值两端空白被剥离，剥离后正常解析。
     */
    @Test
    public void valuesAreTrimmed() {
        assertEquals(42, IdConfig.parse(props("id.workerId", " 42 ")).workerId());
    }

    // ─────────── 枚举键 ───────────

    /**
     * 场景 16：策略取值不在允许集合内。
     */
    @Test
    public void invalidStrategyRejected() {
        assertEquals("id.strategy must be one of [snowflake, segment], but was: snow",
                messageOf(props("id.workerId", "1", "id.strategy", "snow")));
    }

    /**
     * 场景 16：workerId 来源取值不在允许集合内。
     */
    @Test
    public void invalidWorkerSourceRejected() {
        assertEquals("id.worker.source must be one of [config, cache, lock], but was: redis",
                messageOf(props("id.workerId", "1", "id.worker.source", "redis")));
    }

    /**
     * 场景 16：号段存储取值不在允许集合内。
     */
    @Test
    public void invalidSegmentStoreRejected() {
        assertEquals("id.segment.store must be one of [cache, local], but was: redis",
                messageOf(props("id.strategy", "segment", "id.segment.store", "redis")));
    }

    // ─────────── workerId ───────────

    /**
     * 场景 16：{@code source=config} 时 workerId 必填。
     */
    @Test
    public void missingWorkerIdRejected() {
        assertEquals("id.workerId is required when id.worker.source=config",
                messageOf(props("id.worker.source", "config")));
    }

    /**
     * 场景 16：workerId 越界（上界 1023 的相邻值）。
     */
    @Test
    public void workerIdOutOfRangeRejected() {
        assertEquals("id.workerId must be in [0, 1023], but was: 1024",
                messageOf(props("id.workerId", "1024")));
        assertEquals("id.workerId must be in [0, 1023], but was: -1",
                messageOf(props("id.workerId", "-1")));
    }

    /**
     * workerId 的闭区间两端取值合法。
     */
    @Test
    public void workerIdBoundariesAccepted() {
        assertEquals(0, IdConfig.parse(props("id.workerId", "0")).workerId());
        assertEquals(1023, IdConfig.parse(props("id.workerId", "1023")).workerId());
    }

    /**
     * 场景 16：workerId 不是整数。
     */
    @Test
    public void workerIdNotIntegerRejected() {
        assertEquals("id.workerId must be an integer, but was: abc",
                messageOf(props("id.workerId", "abc")));
    }

    // ─────────── 租约时长 ───────────

    /**
     * 场景 16：租约时长低于下界 1000ms。
     */
    @Test
    public void leaseBelowMinimumRejected() {
        assertEquals("id.worker.leaseMillis must be at least 1000, but was: 999",
                messageOf(props("id.worker.source", "lock", "id.worker.leaseMillis", "999")));
    }

    /**
     * 租约下界合法，缺省取默认值；{@code source=lock} 时 workerId 不再是必填键。
     */
    @Test
    public void leaseBoundaryAccepted() {
        assertEquals(1000L, IdConfig.parse(props("id.worker.source", "lock",
                "id.worker.leaseMillis", "1000")).workerLeaseMillis());
        assertEquals(30000L, IdConfig.parse(props("id.worker.source", "lock")).workerLeaseMillis());
    }

    /**
     * 场景 16：租约时长不是整数。
     */
    @Test
    public void leaseNotIntegerRejected() {
        assertEquals("id.worker.leaseMillis must be an integer, but was: 1.5s",
                messageOf(props("id.worker.source", "lock", "id.worker.leaseMillis", "1.5s")));
    }

    // ─────────── 号段长度 ───────────

    /**
     * 场景 16：号段长度越界（闭区间外）。
     */
    @Test
    public void segmentStepOutOfRangeRejected() {
        assertEquals("id.segment.step must be in [1, 1000000], but was: 0",
                messageOf(props("id.strategy", "segment", "id.segment.step", "0")));
        assertEquals("id.segment.step must be in [1, 1000000], but was: 1000001",
                messageOf(props("id.strategy", "segment", "id.segment.step", "1000001")));
    }

    /**
     * 号段长度的闭区间两端取值合法。
     */
    @Test
    public void segmentStepBoundariesAccepted() {
        assertEquals(1, IdConfig.parse(props("id.strategy", "segment",
                "id.segment.step", "1")).segmentStep());
        assertEquals(1000000, IdConfig.parse(props("id.strategy", "segment",
                "id.segment.step", "1000000")).segmentStep());
    }

    /**
     * 场景 16：号段长度不是整数。
     */
    @Test
    public void segmentStepNotIntegerRejected() {
        assertEquals("id.segment.step must be an integer, but was: 1k",
                messageOf(props("id.strategy", "segment", "id.segment.step", "1k")));
    }

    // ─────────── 号段键名 ───────────

    /**
     * 场景 16：号段键名超过 64 字符上限。
     */
    @Test
    public void segmentKeyTooLongRejected() {
        String longKey = repeat('k', 65);
        assertEquals("id.segment.key must be non-blank, without whitespace and no longer than "
                + "64 chars, but was: " + longKey,
                messageOf(props("id.strategy", "segment", "id.segment.key", longKey)));
    }

    /**
     * 场景 16：号段键名含空白字符（会成为计数器键的一部分，必须拒绝）。
     */
    @Test
    public void segmentKeyWithWhitespaceRejected() {
        assertEquals("id.segment.key must be non-blank, without whitespace and no longer than "
                + "64 chars, but was: order id",
                messageOf(props("id.strategy", "segment", "id.segment.key", "order id")));
    }

    /**
     * 号段键名配成空白等同未配，回退默认值。
     */
    @Test
    public void segmentKeyBlankFallsBackToDefault() {
        assertEquals("default", IdConfig.parse(props("id.strategy", "segment",
                "id.segment.key", "   ")).segmentKey());
    }

    /**
     * 号段键名的长度上限本身合法。
     */
    @Test
    public void segmentKeyMaxLengthAccepted() {
        String maxKey = repeat('k', 64);
        assertEquals(maxKey, IdConfig.parse(props("id.strategy", "segment",
                "id.segment.key", maxKey)).segmentKey());
    }

    // ─────────── 状态有效期 ───────────

    /**
     * 场景 16：状态有效期越界（下界 60000、上界 2147483647000 的相邻值）。
     */
    @Test
    public void stateTtlOutOfRangeRejected() {
        assertEquals("id.state.ttlMillis must be in [60000, 2147483647000], but was: 59999",
                messageOf(props("id.strategy", "segment", "id.state.ttlMillis", "59999")));
        assertEquals("id.state.ttlMillis must be in [60000, 2147483647000], but was: 2147483647001",
                messageOf(props("id.strategy", "segment", "id.state.ttlMillis", "2147483647001")));
    }

    /**
     * 状态有效期的闭区间两端取值合法。
     */
    @Test
    public void stateTtlBoundariesAccepted() {
        assertEquals(60000L, IdConfig.parse(props("id.strategy", "segment",
                "id.state.ttlMillis", "60000")).stateTtlMillis());
        assertEquals(2147483647000L, IdConfig.parse(props("id.strategy", "segment",
                "id.state.ttlMillis", "2147483647000")).stateTtlMillis());
    }

    /**
     * 场景 16：状态有效期不是整数。
     */
    @Test
    public void stateTtlNotIntegerRejected() {
        assertEquals("id.state.ttlMillis must be an integer, but was: 1d",
                messageOf(props("id.strategy", "segment", "id.state.ttlMillis", "1d")));
    }

    /**
     * {@code source=cache} 时状态有效期生效并被校验（它同时是 workerId 计数的 TTL）。
     */
    @Test
    public void cacheWorkerSourceReadsStateTtl() {
        IdConfig config = IdConfig.parse(props("id.worker.source", "cache",
                "id.state.ttlMillis", "120000"));
        assertEquals(120000L, config.stateTtlMillis());
        assertEquals(0, config.workerId());
        assertEquals("id.state.ttlMillis must be in [60000, 2147483647000], but was: 1",
                messageOf(props("id.worker.source", "cache", "id.state.ttlMillis", "1")));
    }

    /**
     * 场景 22 的等价情形：状态有效期在「雪花 + config」下不生效，非法取值也不校验。
     */
    @Test
    public void stateTtlIgnoredWhenNotEffective() {
        IdConfig config = IdConfig.parse(props("id.workerId", "5", "id.state.ttlMillis", "1"));
        assertEquals(31536000000L, config.stateTtlMillis());
    }

    // ─────────── 场景 21 / 22：无关键忽略 ───────────

    /**
     * 场景 21：号段策略下 {@code id.worker.*} 与 {@code id.workerId} 不读、不校验。
     */
    @Test
    public void workerKeysIgnoredUnderSegment() {
        IdConfig config = IdConfig.parse(props("id.strategy", "segment",
                "id.worker.source", "bogus", "id.workerId", "99999",
                "id.worker.leaseMillis", "1"));
        assertEquals("segment", config.strategy());
        assertEquals("config", config.workerSource());
        assertEquals(0, config.workerId());
        assertEquals(30000L, config.workerLeaseMillis());
    }

    /**
     * 场景 22：雪花策略下 {@code id.segment.*} 不读、不校验。
     */
    @Test
    public void segmentKeysIgnoredUnderSnowflake() {
        IdConfig config = IdConfig.parse(props("id.workerId", "3",
                "id.segment.step", "0", "id.segment.key", "order id",
                "id.segment.store", "bogus"));
        assertEquals("snowflake", config.strategy());
        assertEquals(1000, config.segmentStep());
        assertEquals("default", config.segmentKey());
        assertEquals("cache", config.segmentStore());
    }

    // ─────────── 工具 ───────────

    /**
     * 以键值对构造配置。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置对象
     */
    private static Properties props(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    /**
     * 解析配置并返回异常消息。
     *
     * @param properties 配置；允许为 {@code null}
     * @return 抛出的异常消息
     */
    private static String messageOf(Properties properties) {
        try {
            IdConfig config = IdConfig.parse(properties);
            assertNotNull(config);
            fail("该配置应被拒绝，实际解析成功：" + properties);
            return null;
        } catch (IdException e) {
            assertTrue("异常消息不能为空", e.getMessage() != null && !e.getMessage().isEmpty());
            return e.getMessage();
        }
    }

    /**
     * 生成重复字符组成的字符串。
     *
     * @param c 字符
     * @param length 重复次数
     * @return 重复串
     */
    private static String repeat(char c, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
