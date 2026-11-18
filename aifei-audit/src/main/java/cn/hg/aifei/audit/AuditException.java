/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

/**
 * 模块唯一异常类型：启动期配置 / 扫描非法、运行期手动埋点不可用。
 *
 * <p>运行期落点、操作人、判定三处 SPI 的异常不得包装为本类型（要么吞掉，要么原样上抛）。</p>
 */
public class AuditException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 异常消息
     */
    public AuditException(String message) {
        super(message);
    }

    /**
     * @param message 异常消息
     * @param cause   原始异常
     */
    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}
