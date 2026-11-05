/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.redisstream;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XAutoClaimParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntryBinary;

/**
 * Redis Streams 后端总线（实验性，未真机验证）：构造内创建 Jedis 连接池（<b>池懒建连</b>）；消费侧延迟到<b>首次
 * 订阅</b>才创建消费组并启动<b>拉取循环线程</b>（以 {@code block-ms} 阻塞读，{@code claim-enabled} 时空闲回收）。
 *
 * <p><b>懒连接</b>：池不校验连通性，故「启动成功」≠「Redis 可达」，故障延迟到首次收发才以 {@link MqException}
 * 暴露（与 RocketMQ / Kafka 同构）。<b>Jedis 直连</b>（不依赖 {@code aifei-cache}）：{@code topic} 即<b>流键</b>；
 * 发布 = 追加到流（{@code maxlen} 近似裁剪），{@code messageId} 取追加返回的<b>流条目 ID</b>（broker 生成）；
 * 消费 = 按 {@code count} 批量读取、逐条按本地登记顺序回调，确认后移出 pending。</p>
 *
 * <p>投递保证 at-least-once：确认失败 / 断连使消息留在 pending，{@code start-mode=pending} 与空闲回收（{@code claim}）
 * 都会<b>扩大重复面</b>，故监听器必须幂等。{@code unsubscribe} 只解除本地登记（不删消费者、不删流 / 消费组）。
 * 共同底线与允许差异见 {@code docs/design.md} §5.6。</p>
 */
public class RedisStreamMessageBus implements MessageBus {

    /** Redis 主机配置键。 */
    private static final String KEY_HOST = "host";

    /** Redis 端口配置键。 */
    private static final String KEY_PORT = "port";

    /** 库号配置键。 */
    private static final String KEY_DATABASE = "database";

    /** 认证用户名配置键；只进配置域。 */
    private static final String KEY_USERNAME = "username";

    /** 认证口令配置键；只进配置域。 */
    private static final String KEY_PASSWORD = "password";

    /** 消费组名配置键。 */
    private static final String KEY_GROUP = "group";

    /** 消费者名配置键。 */
    private static final String KEY_CONSUMER_NAME = "consumer-name";

    /** 读取起始策略配置键（{@code new} / {@code pending}）。 */
    private static final String KEY_START_MODE = "start-mode";

    /** 是否回收空闲 pending 的配置键。 */
    private static final String KEY_CLAIM_ENABLED = "claim-enabled";

    /** pending 空闲回收阈值（毫秒）配置键。 */
    private static final String KEY_CLAIM_IDLE_MILLIS = "claim-idle-millis";

    /** 单次读取条数上限配置键。 */
    private static final String KEY_COUNT = "count";

    /** 单次阻塞读取上限（毫秒）配置键。 */
    private static final String KEY_BLOCK_MS = "block-ms";

    /** 流长度上限配置键；为空表示不裁剪。 */
    private static final String KEY_MAXLEN = "maxlen";

    /** 连接 / 读写超时（毫秒）配置键。 */
    private static final String KEY_TIMEOUT_MILLIS = "timeout-millis";

    /** 是否启用连接池自愈的配置键。 */
    private static final String KEY_AUTO_RECONNECT = "auto-reconnect";

    // 默认值与上方键名逐一对应（port / database / group / start-mode / claim-enabled / claim-idle-millis /
    // count / block-ms / timeout-millis / auto-reconnect）
    private static final int DEFAULT_PORT = 6379;

    // 端口合法区间
    private static final int MIN_PORT = 1;

    private static final int MAX_PORT = 65535;

    private static final int DEFAULT_DATABASE = 0;

    private static final String DEFAULT_GROUP = "aifei-mq";

    private static final String DEFAULT_START_MODE = "new";

    // 起始策略取值：new 只读新消息；pending 先重读本消费者 pending 再转新消息
    private static final String START_MODE_NEW = "new";

    private static final String START_MODE_PENDING = "pending";

    private static final boolean DEFAULT_CLAIM_ENABLED = true;

    private static final long DEFAULT_CLAIM_IDLE_MILLIS = 60000L;

    private static final int DEFAULT_COUNT = 100;

    private static final int DEFAULT_BLOCK_MS = 2000;

    private static final int DEFAULT_TIMEOUT_MILLIS = 2000;

    private static final boolean DEFAULT_AUTO_RECONNECT = true;

    // 无流可读 / 读取出错后的循环停顿（毫秒），避免空转打爆 CPU
    private static final long IDLE_SLEEP_MILLIS = 200L;

    private static final long RETRY_SLEEP_MILLIS = 500L;

    /** 载荷字段名：流条目为字段映射，载荷以原始字节落在该单一字段。 */
    private static final byte[] PAYLOAD_FIELD = utf8("payload");

    // pending 读取与空闲回收的起始 ID 令牌（0-0）
    private static final StreamEntryID PENDING_START_ID = new StreamEntryID(0L, 0L);

    private static final byte[] CLAIM_START_TOKEN = utf8("0-0");

    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /** 已登记的流键；由 {@link #consumerLock} 保护。 */
    private final Set<String> subscribedStreams = new LinkedHashSet<>();

    /** 已登记流的快照，供拉取循环无锁读取；由 {@link #consumerLock} 保护写入。 */
    private volatile List<String> streamSnapshot = Collections.emptyList();

    /** 连接池（池懒建连，构造期不校验连通性）。 */
    private final JedisPool pool;

    private final String group;

    private final String consumerName;

    private final String startMode;

    private final boolean claimEnabled;

    private final long claimIdleMillis;

    private final int count;

    /** {@code 0} 表示不阻塞（不加 {@code BLOCK}）。 */
    private final int blockMs;

    /** {@code null} 表示不裁剪。 */
    private final Long maxlen;

    /** 关闭时等待拉取线程退出的超时（毫秒）：须覆盖一次最长阻塞读。 */
    private final long closeJoinMillis;

    /** 组名编码后的字节，供命令复用。 */
    private final byte[] groupBytes;

    /** 消费者名编码后的字节，供命令复用。 */
    private final byte[] consumerBytes;

    /** 保护消费组创建、订阅登记与拉取循环启动。 */
    private final Object consumerLock = new Object();

    /** 首次订阅时启动。 */
    private Thread pollThread;

    private boolean consumerStarted;

    /** 本消费者 pending 是否已读完（{@code start-mode=pending} 时用于切换到新消息）。 */
    private volatile boolean pendingDrained;

    private volatile boolean closed;

    /**
     * 读取配置并创建 Jedis 连接池：只对<b>配置</b>错误立即失败，<b>不</b>校验 Redis 连通性。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}，或数值 / 枚举键取值非法
     * @throws MqException 未配置 Redis 主机
     */
    public RedisStreamMessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String host = textValue(config, KEY_HOST);
        if (host == null) {
            throw new MqException("redisstream host is required: "
                    + config.prefix() + "." + config.type() + "." + KEY_HOST);
        }
        int port = intValue(config, KEY_PORT, DEFAULT_PORT);
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("redisstream config value must be within " + MIN_PORT + "-" + MAX_PORT
                    + ": " + KEY_PORT + "=" + port);
        }
        int database = intValue(config, KEY_DATABASE, DEFAULT_DATABASE);
        requireNonNegative(KEY_DATABASE, database);
        String username = textValue(config, KEY_USERNAME);
        String password = config.get(KEY_PASSWORD);
        String groupValue = stringValue(config, KEY_GROUP, DEFAULT_GROUP);
        String consumerNameValue = stringValue(config, KEY_CONSUMER_NAME, generatedConsumerName());
        String startModeValue = normalizeStartMode(stringValue(config, KEY_START_MODE, DEFAULT_START_MODE));
        boolean claimEnabledValue = booleanValue(config, KEY_CLAIM_ENABLED, DEFAULT_CLAIM_ENABLED);
        long claimIdleMillisValue = longValue(config, KEY_CLAIM_IDLE_MILLIS, DEFAULT_CLAIM_IDLE_MILLIS);
        requireNonNegativeLong(KEY_CLAIM_IDLE_MILLIS, claimIdleMillisValue);
        int countValue = intValue(config, KEY_COUNT, DEFAULT_COUNT);
        requirePositive(KEY_COUNT, countValue);
        int blockMsValue = intValue(config, KEY_BLOCK_MS, DEFAULT_BLOCK_MS);
        requireNonNegative(KEY_BLOCK_MS, blockMsValue);
        Long maxlenValue = null;
        String maxlenRaw = textValue(config, KEY_MAXLEN);
        if (maxlenRaw != null) {
            long parsed = parseLong(KEY_MAXLEN, maxlenRaw);
            requirePositiveLong(KEY_MAXLEN, parsed);
            maxlenValue = Long.valueOf(parsed);
        }
        int timeoutMillis = intValue(config, KEY_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS);
        requireNonNegative(KEY_TIMEOUT_MILLIS, timeoutMillis);
        boolean autoReconnect = booleanValue(config, KEY_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT);

        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .database(database)
                .connectionTimeoutMillis(timeoutMillis)
                .socketTimeoutMillis(timeoutMillis)
                .blockingSocketTimeoutMillis(timeoutMillis + blockMsValue);
        if (username != null) {
            clientConfigBuilder.user(username);
        }
        if (password != null && !password.isEmpty()) {
            clientConfigBuilder.password(password);
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setTestOnBorrow(autoReconnect);
        poolConfig.setTestWhileIdle(autoReconnect);
        this.pool = new JedisPool(poolConfig, new HostAndPort(host, port), clientConfigBuilder.build());
        this.group = groupValue;
        this.consumerName = consumerNameValue;
        this.startMode = startModeValue;
        this.claimEnabled = claimEnabledValue;
        this.claimIdleMillis = claimIdleMillisValue;
        this.count = countValue;
        this.blockMs = blockMsValue;
        this.maxlen = maxlenValue;
        this.groupBytes = utf8(groupValue);
        this.consumerBytes = utf8(consumerNameValue);
        this.closeJoinMillis = (long) blockMsValue + (long) timeoutMillis + 1000L;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Thread thread = pollThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(closeJoinMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            pool.close();
        } catch (RuntimeException e) {
            // 关闭阶段不因故障上抛：连接随池释放
        }
        subscriptions.clear();
    }

    /**
     * 追加一条消息到目标流；{@code maxlen} 生效（近似裁剪），故障统一映射为 {@link MqException}。
     *
     * @param message 消息
     * @return 发送结果，messageId 为流条目 ID（{@code 毫秒-序号}）
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 追加失败或未返回条目 ID
     */
    @Override
    public SendResult publish(Message message) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        try (Jedis jedis = pool.getResource()) {
            Map<byte[], byte[]> hash = new LinkedHashMap<>();
            hash.put(PAYLOAD_FIELD, message.payload());
            XAddParams params = XAddParams.xAddParams();
            if (maxlen != null) {
                params.maxLen(maxlen.longValue()).approximateTrimming();
            }
            byte[] entryId = jedis.xadd(utf8(message.topic()), params, hash);
            if (entryId == null) {
                // broker 未返回条目 ID 属运行时故障，而非参数非法
                throw new MqException("redisstream send returned no entry id, topic: " + message.topic());
            }
            return SendResult.of(message.topic(), new String(entryId, StandardCharsets.UTF_8));
        } catch (MqException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MqException("redisstream send failed, topic: " + message.topic(), e);
        }
    }

    /**
     * 登记监听器：首次调用为当前全部流创建消费组并启动拉取循环线程，此后新增流只补建其消费组。
     *
     * @param topic 主题，即流键
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 消费组创建失败
     */
    @Override
    public void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        synchronized (consumerLock) {
            if (closed) {
                throw new IllegalStateException("message bus has been closed");
            }
            subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
            boolean newStream = subscribedStreams.add(topic);
            if (newStream) {
                streamSnapshot = new ArrayList<>(subscribedStreams);
            }
            if (!consumerStarted) {
                for (String stream : subscribedStreams) {
                    ensureGroup(stream);
                }
                Thread thread = new Thread(this::pollLoop, "aifei-mq-redisstream-consumer");
                thread.setDaemon(true);
                this.pollThread = thread;
                this.consumerStarted = true;
                thread.start();
            } else if (newStream) {
                ensureGroup(topic);
            }
        }
    }

    /**
     * 按<b>实例身份</b>移除该监听器，只解除本地登记（尽力语义：不删消费者、不删流 / 消费组）；目标不存在时
     * 静默返回。
     *
     * @param topic 主题，即流键
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     */
    @Override
    public void unsubscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        List<MessageListener> listeners = subscriptions.get(topic);
        if (listeners == null) {
            return;
        }
        listeners.removeIf(e -> e == listener);
    }

    /**
     * 创建消费组（幂等）：组已存在时静默成功；起始 ID 视 {@code start-mode} 取 {@code $}（新消息）或
     * {@code 0}（先重读 pending）。
     *
     * @param stream 流键
     * @throws MqException 组创建失败（客户端 / 网络故障）
     */
    private void ensureGroup(String stream) {
        String idToken = START_MODE_PENDING.equals(startMode)
                ? "0" : StreamEntryID.XGROUP_LAST_ENTRY.toString();
        try (Jedis jedis = pool.getResource()) {
            jedis.xgroupCreate(utf8(stream), groupBytes, utf8(idToken), true);
        } catch (JedisDataException e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                // 组已存在即幂等成功
                return;
            }
            throw new MqException("failed to create redisstream consumer group, stream: " + stream, e);
        } catch (RuntimeException e) {
            throw new MqException("failed to create redisstream consumer group, stream: " + stream, e);
        }
    }

    /**
     * 拉取循环：以 {@code block-ms} 阻塞批量读取，逐条按本地登记顺序派发并确认；{@code claim-enabled} 时空闲回收。
     */
    private void pollLoop() {
        while (!closed) {
            List<String> streams = streamSnapshot;
            if (streams.isEmpty()) {
                sleepQuietly(IDLE_SLEEP_MILLIS);
                continue;
            }
            try (Jedis jedis = pool.getResource()) {
                performRead(jedis, streams);
            } catch (RuntimeException e) {
                // 拉取循环故障就地隔离：短暂停顿后重试，避免空转打爆 CPU（监听器异常已单独隔离）
                sleepQuietly(RETRY_SLEEP_MILLIS);
            }
        }
    }

    /**
     * 执行一轮读取与派发：按起始策略读取本消费者 pending 或新消息，逐条派发并确认；随后按配置回收空闲 pending。
     *
     * @param jedis 连接
     * @param streams 当前已登记的流键
     */
    private void performRead(Jedis jedis, List<String> streams) {
        boolean readingPending = START_MODE_PENDING.equals(startMode) && !pendingDrained;
        StreamEntryID readId = readingPending ? PENDING_START_ID : StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY;
        Map<byte[], StreamEntryID> readMap = new LinkedHashMap<>();
        for (String stream : streams) {
            readMap.put(utf8(stream), readId);
        }
        XReadGroupParams params = XReadGroupParams.xReadGroupParams().count(count);
        if (blockMs > 0) {
            params.block(blockMs);
        }
        List<Map.Entry<byte[], List<StreamEntryBinary>>> results =
                jedis.xreadGroupBinary(groupBytes, consumerBytes, params, readMap);
        boolean anyDelivered = false;
        if (results != null) {
            for (Map.Entry<byte[], List<StreamEntryBinary>> result : results) {
                String stream = new String(result.getKey(), StandardCharsets.UTF_8);
                List<StreamEntryBinary> entries = result.getValue();
                if (entries == null) {
                    continue;
                }
                for (StreamEntryBinary entry : entries) {
                    anyDelivered = true;
                    dispatch(stream, entry.getFields());
                    acknowledge(jedis, stream, entry.getID());
                }
            }
        }
        if (readingPending && !anyDelivered) {
            pendingDrained = true;
        }
        if (claimEnabled) {
            claimPending(jedis, streams);
        }
    }

    /**
     * 回收本组空闲超时的 pending 消息（跨消费者）：派发后以本消费者名义确认。
     *
     * @param jedis 连接
     * @param streams 当前已登记的流键
     */
    private void claimPending(Jedis jedis, List<String> streams) {
        XAutoClaimParams params = XAutoClaimParams.xAutoClaimParams().count(count);
        for (String stream : streams) {
            List<Object> raw = jedis.xautoclaim(utf8(stream), groupBytes, consumerBytes,
                    claimIdleMillis, CLAIM_START_TOKEN, params);
            if (raw == null || raw.size() < 2) {
                continue;
            }
            Object entriesObject = raw.get(1);
            if (!(entriesObject instanceof List)) {
                continue;
            }
            for (Object entryObject : (List<?>) entriesObject) {
                if (!(entryObject instanceof List)) {
                    continue;
                }
                List<?> entry = (List<?>) entryObject;
                if (entry.size() < 2 || !(entry.get(0) instanceof byte[])) {
                    continue;
                }
                String entryId = new String((byte[]) entry.get(0), StandardCharsets.UTF_8);
                dispatch(stream, flatFields(entry.get(1)));
                acknowledge(jedis, stream, entryId);
            }
        }
    }

    /**
     * 按本地登记顺序把一条消息回调给该流的监听器，监听器异常就地隔离。
     *
     * @param stream 流键
     * @param fields 流条目的字段映射
     */
    private void dispatch(String stream, Map<byte[], byte[]> fields) {
        byte[] payload = payloadOf(fields);
        if (payload == null) {
            return;
        }
        List<MessageListener> listeners = subscriptions.get(stream);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        Message message = Message.of(stream, payload);
        for (MessageListener listener : listeners) {
            try {
                listener.onMessage(message);
            } catch (RuntimeException e) {
                // 监听器异常就地隔离：不中断其余订阅者、不上抛给生产者
            }
        }
    }

    /**
     * 确认一条已派发的消息；确认失败时消息留在 pending，属 at-least-once 的正常表现。
     *
     * @param jedis 连接
     * @param stream 流键
     * @param entryId 流条目 ID
     */
    private void acknowledge(Jedis jedis, String stream, StreamEntryID entryId) {
        if (entryId == null) {
            return;
        }
        acknowledge(jedis, stream, entryId.toString());
    }

    /**
     * 以字符串形式确认一条已派发的消息；确认失败时静默忽略。
     *
     * @param jedis 连接
     * @param stream 流键
     * @param entryId 流条目 ID 字符串
     */
    private void acknowledge(Jedis jedis, String stream, String entryId) {
        try {
            jedis.xack(utf8(stream), groupBytes, utf8(entryId));
        } catch (RuntimeException e) {
            // 确认失败：消息留在 pending，属 at-least-once 的正常表现
        }
    }

    /**
     * 把 Redis 侧文本（流键、字段名、消费组 / 消费者名、条目 ID）编码为 UTF-8 字节。
     *
     * <p>仅用于 Redis 协议的文本参数；消息文本载荷的编码只在 {@code BusProducer} 定义。</p>
     *
     * @param text 文本
     * @return UTF-8 字节
     */
    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从流条目字段映射中取出载荷字段的原始字节。
     *
     * @param fields 字段映射，可为 {@code null}
     * @return 载荷字节，缺失时为 {@code null}
     */
    private static byte[] payloadOf(Map<byte[], byte[]> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        for (Map.Entry<byte[], byte[]> entry : fields.entrySet()) {
            if (Arrays.equals(PAYLOAD_FIELD, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 把扁平字段列表（{@code [k1, v1, k2, v2, ...]}）还原为字段映射。
     *
     * @param value 扁平字段列表，可为非列表
     * @return 字段映射；入参非列表时为空映射
     */
    private static Map<byte[], byte[]> flatFields(Object value) {
        Map<byte[], byte[]> fields = new LinkedHashMap<>();
        if (!(value instanceof List)) {
            return fields;
        }
        List<?> list = (List<?>) value;
        for (int i = 0; i + 1 < list.size(); i += 2) {
            Object key = list.get(i);
            Object item = list.get(i + 1);
            if (key instanceof byte[] && item instanceof byte[]) {
                fields.put((byte[]) key, (byte[]) item);
            }
        }
        return fields;
    }

    /**
     * 生成消费者名：未配置 {@code consumer-name} 时使用，保证非空。
     *
     * @return 消费者名
     */
    private static String generatedConsumerName() {
        return RedisStreamProvider.TYPE + "-" + UUID.randomUUID();
    }

    /**
     * 归一化起始策略取值并校验。
     *
     * @param value 配置取值
     * @return {@code new} 或 {@code pending}
     * @throws IllegalArgumentException 取值不是 {@code new} / {@code pending}
     */
    private static String normalizeStartMode(String value) {
        if (START_MODE_NEW.equalsIgnoreCase(value)) {
            return START_MODE_NEW;
        }
        if (START_MODE_PENDING.equalsIgnoreCase(value)) {
            return START_MODE_PENDING;
        }
        throw new IllegalArgumentException("redisstream config value must be new or pending: "
                + KEY_START_MODE + "=" + value);
    }

    /**
     * 线程休眠；被中断时回填中断标志。
     *
     * @param millis 休眠时长（毫秒）
     */
    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 读取配置值并去除首尾空白；未配置或只含空白时返回 {@code null}。 */
    private static String textValue(MqConfig config, String key) {
        String value = config.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 读取字符串配置：未配置或只含空白时取默认值。 */
    private static String stringValue(MqConfig config, String key, String defaultValue) {
        String value = textValue(config, key);
        return (value == null) ? defaultValue : value;
    }

    /** 读取布尔配置：未配置取默认值，取值必须是 {@code true} / {@code false}（忽略大小写），否则抛
     * {@link IllegalArgumentException}。 */
    private static boolean booleanValue(MqConfig config, String key, boolean defaultValue) {
        String value = textValue(config, key);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("redisstream config value must be true or false: " + key);
    }

    /** 读取整型配置：未配置取默认值，取值非整型字面量时抛 {@link IllegalArgumentException}。 */
    private static int intValue(MqConfig config, String key, int defaultValue) {
        String value = textValue(config, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("redisstream config value must be an integer: " + key + "=" + value, e);
        }
    }

    /** 读取长整型配置：未配置取默认值，取值非法时抛 {@link IllegalArgumentException}。 */
    private static long longValue(MqConfig config, String key, long defaultValue) {
        String value = textValue(config, key);
        if (value == null) {
            return defaultValue;
        }
        return parseLong(key, value);
    }

    /** 解析长整型字面量；异常消息只含键名与取值。 */
    private static long parseLong(String key, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("redisstream config value must be a long: " + key + "=" + value, e);
        }
    }

    /** 校验整型配置非负；异常消息只含键名与取值，不拼接凭据。 */
    private static void requireNonNegative(String key, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("redisstream config value must not be negative: " + key + "=" + value);
        }
    }

    /** 校验整型配置为正；异常消息只含键名与取值。 */
    private static void requirePositive(String key, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("redisstream config value must be positive: " + key + "=" + value);
        }
    }

    /** 校验长整型配置非负；异常消息只含键名与取值。 */
    private static void requireNonNegativeLong(String key, long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("redisstream config value must not be negative: " + key + "=" + value);
        }
    }

    /** 校验长整型配置为正；异常消息只含键名与取值。 */
    private static void requirePositiveLong(String key, long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException("redisstream config value must be positive: " + key + "=" + value);
        }
    }
}
