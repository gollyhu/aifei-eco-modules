/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import java.util.UUID;

/**
 * 默认 TraceId 生成策略：UUID 去横线派生的 32 位小写十六进制字符串，
 * 满足 TraceIdGenerator 接口的格式契约（非空、仅 [0-9a-f]、固定 32 位）。
 *
 * <p>私有构造 + 唯一单例 INSTANCE（外部无法另建实例）：替换生成器后需要恢复默认时，
 * 传入 {@code DefaultTraceIdGenerator.INSTANCE} 即可。</p>
 *
 * <p>本类型是全模块 TraceId 生成算法与 UUID 的唯一定义点，不得在别处复制生成逻辑。</p>
 */
public class DefaultTraceIdGenerator implements TraceIdGenerator {

    /**
     * 全局唯一单例，TraceKit 未替换生成器时即使用本实例
     */
    public static final DefaultTraceIdGenerator INSTANCE = new DefaultTraceIdGenerator();

    private DefaultTraceIdGenerator() {
    }

    /**
     * 生成一个新 TraceId：UUID 去横线，32 位小写十六进制。
     *
     * @return 非空、仅含 [0-9a-f]、固定 32 位的 TraceId
     */
    @Override
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
