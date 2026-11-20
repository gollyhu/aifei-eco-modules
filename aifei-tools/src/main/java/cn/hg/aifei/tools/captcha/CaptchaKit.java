/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha;

import cn.hg.aifei.tools.captcha.internal.CaptchaDeps;
import cn.hg.aifei.tools.captcha.internal.CaptchaErrors;
import cn.hg.aifei.tools.captcha.internal.CaptchaStore;
import cn.hg.aifei.tools.captcha.internal.ClusterCaptchaStore;
import cn.hg.aifei.tools.captcha.internal.ImageGenerator;
import cn.hg.aifei.tools.captcha.internal.LruCaptchaStore;
import cn.hg.aifei.tools.captcha.internal.TokenGenerator;

/**
 * 验证码门面：生成答案图片并按 token 一次性校验。
 *
 * <p>默认使用进程内 LRU 存储（TTL 120 秒、容量 10000、最多 5 次失败）；
 * 调用 {@link #enableClusterStore()} 切换为共享缓存以支持多实例校验。
 * 配置方法在生成/校验之前调用生效。</p>
 */
public final class CaptchaKit {

    private static final int DEFAULT_TTL_SECONDS = 120;
    private static final int DEFAULT_CAPACITY = 10000;
    private static final int DEFAULT_MAX_FAILURES = 5;

    private static volatile int ttlSeconds = DEFAULT_TTL_SECONDS;
    private static volatile int capacity = DEFAULT_CAPACITY;
    private static volatile int maxFailures = DEFAULT_MAX_FAILURES;
    private static volatile boolean cluster;
    private static volatile CaptchaStore store = new LruCaptchaStore(capacity, maxFailures);

    private CaptchaKit() {
    }

    /**
     * 生成默认字符题验证码。
     *
     * @return 含 token、图片字节与过期时刻的结果
     * @throws CaptchaException 生成或存储失败
     */
    public static Captcha generate() {
        return generate(CaptchaType.CHAR);
    }

    /**
     * 生成指定类型验证码。
     *
     * @param type 题目类型
     * @return 含 token、图片字节与过期时刻的结果
     * @throws CaptchaException 参数为空、生成或存储失败
     */
    public static Captcha generate(CaptchaType type) {
        if (type == null) {
            throw CaptchaErrors.nullArgument("generate", "type");
        }
        String token = TokenGenerator.newToken();
        TokenGenerator.Question question = TokenGenerator.newQuestion(type);
        byte[] image = ImageGenerator.draw(question.getText());
        long expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        store.save(token, question.getAnswer(), expireAt);
        return new Captcha(token, image, expireAt);
    }

    /**
     * 校验用户输入；成功即删除答案，同一 token 二次校验必定失败。
     *
     * @param token 生成时返回的令牌
     * @param input 用户输入答案
     * @return 校验成功返回 true；答案错误、过期、已消费或超限返回 false
     * @throws CaptchaException 存储系统级错误
     */
    public static boolean verify(String token, String input) {
        if (token == null || input == null) {
            return false;
        }
        String answer = store.consume(token);
        if (answer == null) {
            return false;
        }
        if (!answer.equals(input)) {
            store.fail(token);
            return false;
        }
        store.remove(token);
        return true;
    }

    /**
     * 设置答案有效期。
     *
     * @param ttl 有效期秒数，须为正数
     * @throws CaptchaException 非正数
     */
    public static void setTtlSeconds(int ttl) {
        if (ttl <= 0) {
            throw CaptchaErrors.notPositive("ttlSeconds", ttl);
        }
        ttlSeconds = ttl;
    }

    /**
     * 设置进程内 LRU 容量。
     *
     * @param newCapacity 容量，须为正数
     * @throws CaptchaException 非正数
     */
    public static void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw CaptchaErrors.notPositive("capacity", newCapacity);
        }
        capacity = newCapacity;
        rebuildStore();
    }

    /**
     * 设置单个 token 的失败次数上限。
     *
     * @param newMaxFailures 上限，须为正数
     * @throws CaptchaException 非正数
     */
    public static void setMaxFailures(int newMaxFailures) {
        if (newMaxFailures <= 0) {
            throw CaptchaErrors.notPositive("maxFailures", newMaxFailures);
        }
        maxFailures = newMaxFailures;
        rebuildStore();
    }

    /**
     * 切换到共享缓存存储；依赖缺失或未装配时抛可读异常。
     *
     * @throws CaptchaException 共享缓存依赖缺失或未装配
     */
    public static void enableClusterStore() {
        CaptchaDeps.requireCache();
        store = new ClusterCaptchaStore(maxFailures);
        cluster = true;
    }

    private static void rebuildStore() {
        if (cluster) {
            store = new ClusterCaptchaStore(maxFailures);
        } else {
            store = new LruCaptchaStore(capacity, maxFailures);
        }
    }
}
