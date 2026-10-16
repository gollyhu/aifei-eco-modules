/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import cn.hg.aifei.oss.internal.OssClients;
import java.util.Set;

/**
 * {@link OssClient} 的静态门面：不注入也能取到客户端，或按平台标识取用指定平台。
 *
 * <p>「默认平台」由配置项 {@code oss.default-platform} 指定，应用启动后即存在，也正是 {@code @Inject
 * OssClient} 注入的对象。多平台共存时用 {@link #use(String)} 按平台标识取；取到的是应用启动时装配好的同一
 * 个对象，可以放心存下来反复使用。</p>
 *
 * <p><b>异常口径（有意差异）</b>：本门面在「尚未装配」与「平台标识未知」两种情形下<b>统一</b>抛
 * {@link OssException}，以便调用方用同一类型兜住全部失败场景；这与部分既有模块分别抛
 * {@code IllegalStateException} / {@code IllegalArgumentException} 的写法不同，属有意为之，不应「对齐」改回。</p>
 */
public final class OssKit {

    /**
     * 禁止创建工具类实例。
     */
    private OssKit() {
    }

    /**
     * 取默认平台的客户端；即 {@code @Inject OssClient} 注入的同一个对象。
     *
     * @return 默认平台的客户端
     * @throws OssException 尚未装配，或默认平台尚未注册
     */
    public static OssClient defaultClient() {
        return OssClients.defaultClient();
    }

    /**
     * 按平台标识取客户端。
     *
     * @param platformName 平台标识，即配置块 {@code oss.<name>.*} 中的 {@code <name>}
     * @return 该平台的客户端
     * @throws OssException 尚未装配，或平台标识未知
     */
    public static OssClient use(String platformName) {
        return OssClients.get(platformName);
    }

    /**
     * 返回已注册平台标识的只读快照。
     *
     * <p>返回的集合不随后续注册 / 清除而改变，可安全遍历。</p>
     *
     * @return 已注册平台标识集合；尚未装配时为空集合
     */
    public static Set<String> platforms() {
        return OssClients.platforms();
    }
}
