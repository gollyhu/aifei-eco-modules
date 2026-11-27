/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.id.IdException;

/**
 * 异常构造与消息措辞的唯一定义点：本类是全模块唯一出现 {@code new IdException} 的地方，其它类一律
 * {@code throw IdErrors.xxx(...)}。
 *
 * <p>每个方法<b>返回</b>异常对象而不是直接抛出，便于调用方决定是否附带 {@code cause}，也让「构造异常」
 * 与「在何处失败」两件事分离。消息文本用英文，与仓库其它模块一致，并逐字对齐统一的异常消息表。</p>
 *
 * <p>消息由「参数（配置键名 / 原值 / 时间戳 / 键名）+ 措辞片段」拼装：配置键名、分区名与 workerId 范围
 * 一律取 {@link IdConfig} / {@link IdNames} 的常量，因此定位信息不会因实现改动而与唯一定义点脱节。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class IdErrors {

    /**
     * 禁止创建工具类实例。
     */
    private IdErrors() {
    }

    // ─────────── 启动期：配置校验 ───────────

    /**
     * 枚举型配置取值不在允许集合内。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @param candidates 允许取值，以英文逗号加空格分隔
     * @return 异常对象，由调用方抛出
     */
    public static IdException configNotInEnum(String key, String raw, String candidates) {
        return new IdException(key + " must be one of [" + candidates + "], but was: " + raw);
    }

    /**
     * 条件必填配置缺失。
     *
     * @param key 配置键名
     * @param condition 触发必填的条件描述，如「某键=某取值」
     * @return 异常对象，由调用方抛出
     */
    public static IdException configRequired(String key, String condition) {
        return new IdException(key + " is required when " + condition);
    }

    /**
     * 数值型配置越界。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @param min 下界（含）
     * @param max 上界（含）
     * @return 异常对象，由调用方抛出
     */
    public static IdException configOutOfRange(String key, String raw, long min, long max) {
        return new IdException(key + " must be in [" + min + ", " + max + "], but was: " + raw);
    }

    /**
     * 数值型配置低于下界。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @param min 下界（含）
     * @return 异常对象，由调用方抛出
     */
    public static IdException configBelowMinimum(String key, String raw, long min) {
        return new IdException(key + " must be at least " + min + ", but was: " + raw);
    }

    /**
     * 配置值不是整数。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @return 异常对象，由调用方抛出
     */
    public static IdException configNotInteger(String key, String raw) {
        return new IdException(key + " must be an integer, but was: " + raw);
    }

    /**
     * 号段键名非法（空白、含空白字符或超长）。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @param maxLength 允许的最大长度
     * @return 异常对象，由调用方抛出
     */
    public static IdException configInvalidSegmentKey(String key, String raw, int maxLength) {
        return new IdException(key + " must be non-blank, without whitespace and no longer than "
                + maxLength + " chars, but was: " + raw);
    }

    // ─────────── 启动期：依赖与装配 ───────────

    /**
     * 当前配置需要 {@code aifei-cache} 模块，但 classpath 上不存在。
     *
     * @return 异常对象，由调用方抛出
     */
    public static IdException cacheMissing() {
        return new IdException("aifei-id requires module aifei-cache for this configuration, "
                + "add io.github.gollyhu:aifei-cache to your dependencies");
    }

    /**
     * 当前配置需要 {@code aifei-lock} 模块，但 classpath 上不存在。
     *
     * @return 异常对象，由调用方抛出
     */
    public static IdException lockMissing() {
        return new IdException("aifei-id requires module aifei-lock when "
                + IdConfig.KEY_WORKER_SOURCE + "=" + IdConfig.WORKER_SOURCE_LOCK
                + ", add io.github.gollyhu:aifei-lock to your dependencies");
    }

    /**
     * 依赖模块在 classpath 上存在，但尚未装配（例如 {@code CachePlugin} 未启动或启动顺序不对）。
     *
     * @param dependency 未就绪的协同模块名
     * @param detail 底层异常的说明，用于定位真实原因
     * @return 异常对象，由调用方抛出
     */
    public static IdException assemblyNotReady(String dependency, String detail) {
        return new IdException("aifei-id assembly failed, " + dependency
                + " is not ready, start it before IdPlugin: " + detail);
    }

    /**
     * 缓存后端不是共享存储，多实例各自取号必然重号。
     *
     * @return 异常对象，由调用方抛出
     */
    public static IdException notSharedBackend() {
        return new IdException("aifei-cache backend is not a shared store (supportsCas=false), "
                + "aifei-id requires a shared backend such as redis");
    }

    /**
     * {@code 0..1023} 全部 workerId 已被其它实例以租约占用。
     *
     * @return 异常对象，由调用方抛出
     */
    public static IdException noWorkerIdAvailable() {
        return new IdException("no worker id available in [" + IdNames.MIN_WORKER_ID + ", "
                + IdNames.MAX_WORKER_ID + "], all leases are held by other instances");
    }

    // ─────────── 运行期：雪花 ───────────

    /**
     * 时钟回拨超出容差，继续发号必然重号。
     *
     * @param lastTimestamp 上一毫秒时间戳
     * @param now 当前时钟读数
     * @param backwards 回拨毫秒数
     * @param tolerance 容忍的回拨毫秒数
     * @return 异常对象，由调用方抛出
     */
    public static IdException clockBackwards(long lastTimestamp, long now, long backwards, long tolerance) {
        return new IdException("clock moved backwards beyond tolerance, lastTimestamp: " + lastTimestamp
                + ", now: " + now + ", backwards: " + backwards + "ms, tolerance: " + tolerance + "ms");
    }

    /**
     * 系统时钟早于模块纪元，41 位时间戳字段无法表达负值。
     *
     * @param now 当前时钟读数
     * @return 异常对象，由调用方抛出
     */
    public static IdException clockBeforeEpoch(long now) {
        return new IdException("clock is before aifei-id epoch 2024-01-01T00:00:00Z, now: " + now);
    }

    /**
     * 41 位时间戳字段耗尽，继续左移会污染 workerId 位。
     *
     * @param now 当前时钟读数
     * @return 异常对象，由调用方抛出
     */
    public static IdException timestampOverflow(long now) {
        return new IdException("timestamp overflow, the 41-bit timestamp field of snowflake is exhausted, "
                + "now: " + now);
    }

    /**
     * 回拨等待或等下一毫秒时被中断，时钟未追上。
     *
     * @return 异常对象，由调用方抛出
     */
    public static IdException waitInterrupted() {
        return new IdException("interrupted while waiting for clock to catch up");
    }

    /**
     * workerId 租约已丢失，继续发号会与他人重号。
     *
     * @param workerId 当前持有的 workerId
     * @return 异常对象，由调用方抛出
     */
    public static IdException workerLeaseLost(long workerId) {
        return new IdException("worker id lease lost, workerId: " + workerId
                + ", stop generating ids to avoid duplicates");
    }

    // ─────────── 运行期：号段 ───────────

    /**
     * 取段时存储抛错，保留根因。
     *
     * @param key 号段键（{@code id.segment.key} 的取值）
     * @param cause 存储抛出的运行时异常
     * @return 异常对象，由调用方抛出
     */
    public static IdException segmentStoreFailed(String key, Throwable cause) {
        return new IdException("segment store failed, cache: " + IdNames.CACHE_NAME + ", key: " + key
                + ", cause: " + cause.getMessage(), cause);
    }

    /**
     * 新领号段起点不大于上次段末端（计数器过期或被清空 / 重置），继续发号必然重号。
     *
     * @param lastMax 上次号段末端
     * @param start 新号段起点
     * @param end 新号段末端
     * @return 异常对象，由调用方抛出
     */
    public static IdException segmentRolledBack(long lastMax, long start, long end) {
        return new IdException("segment rolled back, lastMax: " + lastMax + ", newSegment: [" + start + ", "
                + end + "], the counter may have expired or been reset");
    }
}
