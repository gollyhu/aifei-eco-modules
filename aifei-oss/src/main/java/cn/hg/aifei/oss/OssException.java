/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

/**
 * 对象存储能力的唯一未受检异常。
 *
 * <p>底层存储实现（各平台 SDK、X File Storage）抛出的异常一律在适配层翻译为本类型后再上浮，因此调用方只需
 * 捕获本异常即可覆盖「未装配 / 未知平台、参数非法、对象不存在、平台不支持、平台故障」等全部失败场景。异常
 * 消息中不会出现任何第三方类型名。</p>
 *
 * <p>本异常不细分连接、超时、签名等子类。</p>
 */
public class OssException extends RuntimeException {

    /**
     * 由故障描述创建异常。
     *
     * @param message 故障描述
     */
    public OssException(String message) {
        super(message);
    }

    /**
     * 由故障描述与根因创建异常。
     *
     * @param message 故障描述
     * @param cause 根因
     */
    public OssException(String message, Throwable cause) {
        super(message, cause);
    }
}
