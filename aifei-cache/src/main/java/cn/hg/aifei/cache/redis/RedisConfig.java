/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.redis;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import cn.hg.aifei.cache.internal.Config;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultRedisCredentials;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Redis 连接、连接池与 value 编解码配置。
 *
 * <p>取值校验在本类的 setter 内完成：入参为 {@code null} 视为未配置，不校验也不赋值（保持字段默认
 * 值）；非 {@code null} 时先校验后赋值，失败抛统一格式的配置错误。配置项读取只做语法解析，范围校验
 * 只此一处。</p>
 */
public class RedisConfig extends Config {

    /**
     * 连接池等待上限的默认值，刻意不同于 commons-pool 的无限等待默认值。
     */
    private static final long DEFAULT_MAX_WAIT_MILLIS = 1_500L;

    /**
     * jedis 专有配置键在实例前缀之后的分段：主缓存得到 {@code cache.jedis.*}，扩展实例得到
     * {@code cache.<name>.jedis.*}，与 caffeine 后端的 {@code cache.caffeine.maxSize} 对称。
     */
    public static final String JEDIS_KEY_SEGMENT = "jedis";

    private URI redisUri;
    private String host = Protocol.DEFAULT_HOST;
    private int port = Protocol.DEFAULT_PORT;
    private String user;
    private String password;
    private Integer database;
    private String clientName;
    private Boolean ssl;
    private Boolean resp3;
    private Integer timeoutMillis;
    private Integer connectionTimeoutMillis;
    private Integer socketTimeoutMillis;
    private Integer blockingSocketTimeoutMillis;
    private Integer maxTotal;
    private Integer maxIdle;
    private Integer minIdle;
    private Long maxWaitMillis = DEFAULT_MAX_WAIT_MILLIS;
    private Boolean blockWhenExhausted;
    private Boolean lifo;
    private Boolean fairness;
    private Boolean testOnCreate;
    private Boolean testOnBorrow;
    private Boolean testOnReturn;
    private Boolean testWhileIdle;
    private Long timeBetweenEvictionRunsMillis;
    private Long minEvictableIdleTimeMillis;
    private Long softMinEvictableIdleTimeMillis;
    private Integer numTestsPerEvictionRun;
    private Boolean jmxEnabled;
    private String jmxNamePrefix;
    private String jmxNameBase;

    /**
     * 使用指定 Redis URI；配置了 URI 时 {@code host} / {@code port} 被忽略。
     */
    public RedisConfig uri(String redisUri) {
        if (redisUri != null) {
            this.redisUri = parseRedisUri(".uri", redisUri);
        }
        return this;
    }

    /**
     * 使用指定 Redis URI；配置了 URI 时 {@code host} / {@code port} 被忽略。
     */
    public RedisConfig uri(URI redisUri) {
        if (redisUri != null) {
            this.redisUri = redisUri;
        }
        return this;
    }

    /**
     * 设置 Redis 主机。
     */
    public RedisConfig host(String host) {
        if (host != null) {
            this.host = Config.requireConfigText(configKey(".host"), host);
        }
        return this;
    }

    /**
     * 设置 Redis 端口，取值 1 ~ 65535。
     */
    public RedisConfig port(Integer port) {
        if (port != null) {
            requireBetween(".port", port, 1, 65535);
            this.port = port;
        }
        return this;
    }

    /**
     * 设置 Redis ACL 用户名。
     */
    public RedisConfig user(String user) {
        if (user != null) {
            this.user = user;
        }
        return this;
    }

    /**
     * 设置 Redis 密码。
     */
    public RedisConfig password(String password) {
        if (password != null) {
            this.password = password;
        }
        return this;
    }

    /**
     * 设置 Redis database，取值不小于 0。
     */
    public RedisConfig database(Integer database) {
        if (database != null) {
            requireBetween(".database", database, 0, Integer.MAX_VALUE);
            this.database = database;
        }
        return this;
    }

    /**
     * 设置 Redis 客户端名称。
     */
    public RedisConfig clientName(String clientName) {
        if (clientName != null) {
            this.clientName = clientName;
        }
        return this;
    }

    /**
     * 是否使用 SSL。
     */
    public RedisConfig ssl(Boolean ssl) {
        if (ssl != null) {
            this.ssl = ssl;
        }
        return this;
    }

    /**
     * 使用 RESP3 协议。
     */
    public RedisConfig resp3() {
        return resp3(Boolean.TRUE);
    }

    /**
     * 是否使用 RESP3 协议。
     */
    public RedisConfig resp3(Boolean resp3) {
        if (resp3 != null) {
            this.resp3 = resp3;
        }
        return this;
    }

    /**
     * 同时设置连接超时和 socket 读写超时时间，单位毫秒，取值不小于 1。
     */
    public RedisConfig timeoutMillis(Integer timeoutMillis) {
        if (timeoutMillis != null) {
            requireBetween(".timeoutMillis", timeoutMillis, 1, Integer.MAX_VALUE);
            this.timeoutMillis = timeoutMillis;
        }
        return this;
    }

    /**
     * 设置连接超时时间，单位毫秒，取值不小于 1。
     */
    public RedisConfig connectionTimeoutMillis(Integer connectionTimeoutMillis) {
        if (connectionTimeoutMillis != null) {
            requireBetween(".connectionTimeoutMillis", connectionTimeoutMillis, 1, Integer.MAX_VALUE);
            this.connectionTimeoutMillis = connectionTimeoutMillis;
        }
        return this;
    }

    /**
     * 设置 socket 读写超时时间，单位毫秒，取值不小于 1。
     */
    public RedisConfig socketTimeoutMillis(Integer socketTimeoutMillis) {
        if (socketTimeoutMillis != null) {
            requireBetween(".socketTimeoutMillis", socketTimeoutMillis, 1, Integer.MAX_VALUE);
            this.socketTimeoutMillis = socketTimeoutMillis;
        }
        return this;
    }

    /**
     * 设置阻塞命令 socket 超时时间，单位毫秒，0 表示不超时。
     */
    public RedisConfig blockingSocketTimeoutMillis(Integer blockingSocketTimeoutMillis) {
        if (blockingSocketTimeoutMillis != null) {
            requireBetween(".blockingSocketTimeoutMillis",
                    blockingSocketTimeoutMillis, 0, Integer.MAX_VALUE);
            this.blockingSocketTimeoutMillis = blockingSocketTimeoutMillis;
        }
        return this;
    }

    /**
     * 设置连接池最大连接数，取值 {@code -1}（不限制）或大于 0。
     */
    public RedisConfig maxTotal(Integer maxTotal) {
        if (maxTotal != null) {
            requireMinusOneOrPositive(".maxTotal", maxTotal);
            this.maxTotal = maxTotal;
        }
        return this;
    }

    /**
     * 设置连接池最大空闲连接数，取值不小于 0。
     */
    public RedisConfig maxIdle(Integer maxIdle) {
        if (maxIdle != null) {
            requireBetween(".maxIdle", maxIdle, 0, Integer.MAX_VALUE);
            this.maxIdle = maxIdle;
        }
        return this;
    }

    /**
     * 设置连接池最小空闲连接数，取值不小于 0。
     */
    public RedisConfig minIdle(Integer minIdle) {
        if (minIdle != null) {
            requireBetween(".minIdle", minIdle, 0, Integer.MAX_VALUE);
            this.minIdle = minIdle;
        }
        return this;
    }

    /**
     * 设置连接池等待可用连接的最长时间，单位毫秒，取值 {@code -1}（一直等待）或不小于 0。
     */
    public RedisConfig maxWaitMillis(Long maxWaitMillis) {
        if (maxWaitMillis != null) {
            requireMinusOneOrMore(".maxWaitMillis", maxWaitMillis);
            this.maxWaitMillis = maxWaitMillis;
        }
        return this;
    }

    /**
     * 连接池耗尽时是否阻塞等待。
     */
    public RedisConfig blockWhenExhausted(Boolean blockWhenExhausted) {
        if (blockWhenExhausted != null) {
            this.blockWhenExhausted = blockWhenExhausted;
        }
        return this;
    }

    /**
     * 借出连接时是否优先使用最近归还的空闲连接。
     */
    public RedisConfig lifo(Boolean lifo) {
        if (lifo != null) {
            this.lifo = lifo;
        }
        return this;
    }

    /**
     * 连接池耗尽时是否使用公平等待队列。
     */
    public RedisConfig fairness(Boolean fairness) {
        if (fairness != null) {
            this.fairness = fairness;
        }
        return this;
    }

    /**
     * 创建连接时是否执行校验。
     */
    public RedisConfig testOnCreate(Boolean testOnCreate) {
        if (testOnCreate != null) {
            this.testOnCreate = testOnCreate;
        }
        return this;
    }

    /**
     * 借出连接时是否执行校验。
     */
    public RedisConfig testOnBorrow(Boolean testOnBorrow) {
        if (testOnBorrow != null) {
            this.testOnBorrow = testOnBorrow;
        }
        return this;
    }

    /**
     * 归还连接时是否执行校验。
     */
    public RedisConfig testOnReturn(Boolean testOnReturn) {
        if (testOnReturn != null) {
            this.testOnReturn = testOnReturn;
        }
        return this;
    }

    /**
     * 空闲连接扫描时是否执行校验。
     */
    public RedisConfig testWhileIdle(Boolean testWhileIdle) {
        if (testWhileIdle != null) {
            this.testWhileIdle = testWhileIdle;
        }
        return this;
    }

    /**
     * 设置空闲连接扫描间隔，单位毫秒，取值 {@code -1}（不启用）或不小于 0。
     */
    public RedisConfig timeBetweenEvictionRunsMillis(Long timeBetweenEvictionRunsMillis) {
        if (timeBetweenEvictionRunsMillis != null) {
            requireMinusOneOrMore(".timeBetweenEvictionRunsMillis", timeBetweenEvictionRunsMillis);
            this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        }
        return this;
    }

    /**
     * 设置空闲连接最小可淘汰时间，单位毫秒，取值 {@code -1}（不按该条件淘汰）或不小于 0。
     */
    public RedisConfig minEvictableIdleTimeMillis(Long minEvictableIdleTimeMillis) {
        if (minEvictableIdleTimeMillis != null) {
            requireMinusOneOrMore(".minEvictableIdleTimeMillis", minEvictableIdleTimeMillis);
            this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        }
        return this;
    }

    /**
     * 设置保留 {@code minIdle} 时的空闲连接最小可淘汰时间，单位毫秒，取值 {@code -1} 或不小于 0。
     */
    public RedisConfig softMinEvictableIdleTimeMillis(Long softMinEvictableIdleTimeMillis) {
        if (softMinEvictableIdleTimeMillis != null) {
            requireMinusOneOrMore(".softMinEvictableIdleTimeMillis", softMinEvictableIdleTimeMillis);
            this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        }
        return this;
    }

    /**
     * 设置每次空闲连接扫描检查的连接数量，取值 {@code -1}（检查全部）或大于 0。
     */
    public RedisConfig numTestsPerEvictionRun(Integer numTestsPerEvictionRun) {
        if (numTestsPerEvictionRun != null) {
            requireMinusOneOrPositive(".numTestsPerEvictionRun", numTestsPerEvictionRun);
            this.numTestsPerEvictionRun = numTestsPerEvictionRun;
        }
        return this;
    }

    /**
     * 是否启用连接池 JMX。
     */
    public RedisConfig jmxEnabled(Boolean jmxEnabled) {
        if (jmxEnabled != null) {
            this.jmxEnabled = jmxEnabled;
        }
        return this;
    }

    /**
     * 设置连接池 JMX 名称前缀。
     */
    public RedisConfig jmxNamePrefix(String jmxNamePrefix) {
        if (jmxNamePrefix != null) {
            this.jmxNamePrefix = jmxNamePrefix;
        }
        return this;
    }

    /**
     * 设置连接池 JMX 名称基础部分。
     */
    public RedisConfig jmxNameBase(String jmxNameBase) {
        if (jmxNameBase != null) {
            this.jmxNameBase = jmxNameBase;
        }
        return this;
    }

    /**
     * 协变重写 {@link Config#allowedPackages(List)}，使其在 {@code RedisConfig} 的链式调用中不打断类型。
     */
    @Override
    public RedisConfig allowedPackages(List<String> allowedPackages) {
        super.allowedPackages(allowedPackages);
        return this;
    }

    // ─────────── 配置键拼装 ───────────

    /**
     * 拼装 jedis 配置项的完整键：{@code prefix + ".jedis" + relativeKey}。主缓存下 {@code prefix}
     * 为 {@code cache}，得 {@code cache.jedis.host}；扩展实例下为 {@code cache.mem}，得
     * {@code cache.mem.jedis.host}。
     *
     * <p>读取侧（{@code RedisCacheProvider}）与本类 setter 的报错侧都经此方法，故「jedis 键叫什么」
     * 只有一处定义，两侧不会各自漂移。</p>
     */
    public static String jedisKey(String prefix, String relativeKey) {
        return prefix + "." + JEDIS_KEY_SEGMENT + relativeKey;
    }

    /**
     * 重写为在实例前缀后插入 {@link #JEDIS_KEY_SEGMENT}，使各 setter 的配置错误消息带全键
     * （如 {@code cache.jedis.port=70000}，而不是 {@code cache.port=70000}）。
     */
    @Override
    public String configKey(String relativeKey) {
        return jedisKey(prefix(), relativeKey);
    }

    // ─────────── 取值校验 ───────────

    /**
     * 校验连接池上下限关系：{@code maxIdle ≤ maxTotal}、{@code minIdle ≤ maxTotal}、
     * {@code minIdle ≤ maxIdle}（{@code maxTotal = -1} 时不参与比较）。作为链式调用的收尾，返回自身。
     */
    public RedisConfig requirePoolBounds() {
        if (maxTotal != null && maxTotal != -1 && maxIdle != null && maxIdle > maxTotal) {
            throw configInvalidAt(".maxIdle", maxIdle,
                    "must be less than or equal to " + configKey(".maxTotal"));
        }
        if (maxTotal != null && maxTotal != -1 && minIdle != null && minIdle > maxTotal) {
            throw configInvalidAt(".minIdle", minIdle,
                    "must be less than or equal to " + configKey(".maxTotal"));
        }
        if (maxIdle != null && minIdle != null && minIdle > maxIdle) {
            throw configInvalidAt(".minIdle", minIdle,
                    "must be less than or equal to " + configKey(".maxIdle"));
        }
        return this;
    }

    /**
     * 校验取值落在闭区间内。
     */
    private void requireBetween(String relativeKey, long value, long min, long max) {
        if (value < min || value > max) {
            throw configInvalidAt(relativeKey, value, "must be between " + min + " and " + max);
        }
    }

    /**
     * 校验取值为 {@code -1} 或大于 0。
     */
    private void requireMinusOneOrPositive(String relativeKey, int value) {
        if (value != -1 && value <= 0) {
            throw configInvalidAt(relativeKey, value, "must be -1 or greater than 0");
        }
    }

    /**
     * 校验取值不小于 {@code -1}。
     */
    private void requireMinusOneOrMore(String relativeKey, long value) {
        if (value < -1L) {
            throw configInvalidAt(relativeKey, value, "must be greater than or equal to -1");
        }
    }

    /**
     * 解析 Redis URI，失败按配置错误上报（消息中的 userInfo 已脱敏）。
     */
    private URI parseRedisUri(String relativeKey, String redisUri) {
        try {
            return URI.create(redisUri);
        } catch (IllegalArgumentException e) {
            throw configInvalidAt(relativeKey, redisUri, "invalid redis uri");
        }
    }

    // ─────────── 客户端与编解码装配 ───────────

    RedisClient createClient() {
        RedisClient.Builder builder = RedisClient.builder();
        DefaultJedisClientConfig.Builder clientConfigBuilder;
        if (redisUri == null) {
            builder.hostAndPort(host, port);
            clientConfigBuilder = DefaultJedisClientConfig.builder();
        } else {
            builder.hostAndPort(JedisURIHelper.getHostAndPort(redisUri));
            clientConfigBuilder = DefaultJedisClientConfig.builder(redisUri);
        }
        applyClientConfig(clientConfigBuilder);
        builder.clientConfig(clientConfigBuilder.build());
        builder.poolConfig(createPoolConfig());
        return builder.build();
    }

    /**
     * 返回该配置所指存储的唯一名，形如 {@code 127.0.0.1:6379/0}；供启动期排他判定使用。
     *
     * <p>按 {@link #createClient()} 的同一套语义计算，不另立规则：{@code redisUri != null} 时
     * host / port 取自 URI（字段被忽略），{@code database} 取「显式 &gt; URI &gt; 0」。</p>
     */
    String uniqueName() {
        String effectiveHost;
        int effectivePort;
        if (redisUri == null) {
            effectiveHost = host;
            effectivePort = port;
        } else {
            HostAndPort hostAndPort = JedisURIHelper.getHostAndPort(redisUri);
            effectiveHost = hostAndPort.getHost();
            effectivePort = hostAndPort.getPort();
        }
        int effectiveDatabase = database != null
                ? database
                : (redisUri != null && JedisURIHelper.hasDbIndex(redisUri)
                        ? JedisURIHelper.getDBIndex(redisUri)
                        : 0);
        return effectiveHost + ":" + effectivePort + "/" + effectiveDatabase;
    }

    /**
     * 返回本配置使用的 value 编解码器；本项目固定使用自带的 Fury 实现，见 {@link #defaultValueCodec()}。
     */
    RedisValueCodec createValueCodec() {
        return defaultValueCodec();
    }

    static RedisValueCodec defaultValueCodec() {
        return DefaultValueCodecHolder.INSTANCE;
    }

    /**
     * 转换为 Jedis 连接池配置；未显式配置的项不覆盖底层默认值。
     */
    private ConnectionPoolConfig createPoolConfig() {
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        if (maxTotal != null) {
            poolConfig.setMaxTotal(maxTotal);
        }
        if (maxIdle != null) {
            poolConfig.setMaxIdle(maxIdle);
        }
        if (minIdle != null) {
            poolConfig.setMinIdle(minIdle);
        }
        if (maxWaitMillis != null) {
            poolConfig.setMaxWait(Duration.ofMillis(maxWaitMillis));
        }
        if (blockWhenExhausted != null) {
            poolConfig.setBlockWhenExhausted(blockWhenExhausted);
        }
        if (lifo != null) {
            poolConfig.setLifo(lifo);
        }
        if (fairness != null) {
            poolConfig.setFairness(fairness);
        }
        if (testOnCreate != null) {
            poolConfig.setTestOnCreate(testOnCreate);
        }
        if (testOnBorrow != null) {
            poolConfig.setTestOnBorrow(testOnBorrow);
        }
        if (testOnReturn != null) {
            poolConfig.setTestOnReturn(testOnReturn);
        }
        if (testWhileIdle != null) {
            poolConfig.setTestWhileIdle(testWhileIdle);
        }
        if (timeBetweenEvictionRunsMillis != null) {
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
        }
        if (minEvictableIdleTimeMillis != null) {
            poolConfig.setMinEvictableIdleDuration(Duration.ofMillis(minEvictableIdleTimeMillis));
        }
        if (softMinEvictableIdleTimeMillis != null) {
            poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(softMinEvictableIdleTimeMillis));
        }
        if (numTestsPerEvictionRun != null) {
            poolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        }
        if (jmxEnabled != null) {
            poolConfig.setJmxEnabled(jmxEnabled);
        }
        if (jmxNamePrefix != null) {
            poolConfig.setJmxNamePrefix(jmxNamePrefix);
        }
        if (jmxNameBase != null) {
            poolConfig.setJmxNameBase(jmxNameBase);
        }
        return poolConfig;
    }

    /**
     * 应用 Redis 客户端配置；显式项覆盖 URI 中对应项。
     */
    private void applyClientConfig(DefaultJedisClientConfig.Builder builder) {
        if (resp3 != null && resp3) {
            builder.resp3();
        }
        if (timeoutMillis != null) {
            builder.timeoutMillis(timeoutMillis);
        }
        if (user != null || password != null) {
            builder.credentials(new DefaultRedisCredentials(effectiveUser(), effectivePassword()));
        }
        if (database != null) {
            builder.database(database);
        }
        if (clientName != null) {
            builder.clientName(clientName);
        }
        if (ssl != null) {
            builder.ssl(ssl);
        }
        if (connectionTimeoutMillis != null) {
            builder.connectionTimeoutMillis(connectionTimeoutMillis);
        }
        if (socketTimeoutMillis != null) {
            builder.socketTimeoutMillis(socketTimeoutMillis);
        }
        if (blockingSocketTimeoutMillis != null) {
            builder.blockingSocketTimeoutMillis(blockingSocketTimeoutMillis);
        }
    }

    /**
     * 返回显式配置覆盖 URI 后的 Redis 用户名。
     */
    private String effectiveUser() {
        if (user != null || redisUri == null) {
            return user;
        }
        return JedisURIHelper.getUser(redisUri);
    }

    /**
     * 返回显式配置覆盖 URI 后的 Redis 密码。
     */
    private String effectivePassword() {
        if (password != null || redisUri == null) {
            return password;
        }
        return JedisURIHelper.getPassword(redisUri);
    }

    /**
     * 惰性创建默认 codec，避免 caffeine-only 场景强制加载 Fury。
     */
    private static final class DefaultValueCodecHolder {

        private static final RedisValueCodec INSTANCE = new FuryRedisValueCodec();
    }
}
