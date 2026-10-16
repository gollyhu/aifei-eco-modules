/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssClient;
import cn.hg.aifei.oss.OssException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 平台标识到 {@link OssClient} 的运行期注册表，供静态门面与装配入口共用。
 *
 * <p>登记与清除发生在应用装配的启动 / 停止阶段，{@code clear()} 亦用于测试隔离。取用期一律<b>先生命周期、
 * 后参数</b>，且「尚未装配」与「平台标识未知」统一抛 {@link OssException}（有意差异，见模块公共门面
 * javadoc），不抛 {@link IllegalStateException} / {@link IllegalArgumentException}。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssClients {

    /**
     * 登记、取用与清除共用的持有锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 平台标识到客户端的映射；保持登记顺序以便异常消息稳定。
     */
    private static final Map<String, OssClient> REGISTRY = new LinkedHashMap<>();

    /**
     * 默认平台客户端，对应 {@code @Inject OssClient}。
     */
    private static OssClient defaultClient;

    /**
     * 禁止创建工具类实例。
     */
    private OssClients() {
    }

    /**
     * 登记一个平台的客户端，覆盖同名的既有登记。
     *
     * @param platform 平台标识
     * @param client 该平台的客户端
     * @throws OssException 平台标识为空白，或客户端为 {@code null}
     */
    public static void register(String platform, OssClient client) {
        OssConfig.requirePlatform(platform);
        if (client == null) {
            throw new OssException("oss client can not be null, platform: " + platform);
        }
        synchronized (LOCK) {
            REGISTRY.put(platform, client);
        }
    }

    /**
     * 登记默认平台的客户端。
     *
     * @param client 默认平台的客户端
     * @throws OssException 客户端为 {@code null}
     */
    public static void registerDefault(OssClient client) {
        if (client == null) {
            throw new OssException("default oss client can not be null");
        }
        synchronized (LOCK) {
            defaultClient = client;
        }
    }

    /**
     * 取默认平台的客户端。
     *
     * @return 默认平台的客户端
     * @throws OssException 尚未装配，或默认平台客户端尚未登记
     */
    public static OssClient defaultClient() {
        synchronized (LOCK) {
            if (REGISTRY.isEmpty()) {
                throw new OssException("oss has not been initialized, add the oss plugin first");
            }
            if (defaultClient == null) {
                throw new OssException("default oss client has not been registered");
            }
            return defaultClient;
        }
    }

    /**
     * 取指定平台的客户端。
     *
     * @param platform 平台标识
     * @return 该平台的客户端
     * @throws OssException 尚未装配，平台标识为空白，或平台标识未知
     */
    public static OssClient get(String platform) {
        synchronized (LOCK) {
            if (REGISTRY.isEmpty()) {
                throw new OssException("oss has not been initialized, add the oss plugin first");
            }
            OssConfig.requirePlatform(platform);
            OssClient client = REGISTRY.get(platform);
            if (client == null) {
                throw new OssException(
                        "unknown oss platform: " + platform + ", registered platforms: " + REGISTRY.keySet());
            }
            return client;
        }
    }

    /**
     * 返回已登记平台标识的只读快照，不随后续登记 / 清除改变。
     *
     * @return 已登记平台标识集合；尚未装配时为空集合
     */
    public static Set<String> platforms() {
        synchronized (LOCK) {
            if (REGISTRY.isEmpty()) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTRY.keySet()));
        }
    }

    /**
     * 清除全部登记与默认客户端；清除后取用立即失败。
     */
    public static void clear() {
        synchronized (LOCK) {
            REGISTRY.clear();
            defaultClient = null;
        }
    }
}
