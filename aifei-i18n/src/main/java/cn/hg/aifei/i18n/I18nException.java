/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

/**
 * i18n 模块唯一的未受检异常：用于装配期失败（配置非法、来源重名/未注册、已声明来源依赖缺失、
 * {@code defaultLocale} 缺失）与未装配 / 已停止时调用门面。
 *
 * <p>运行期取文案的故障（来源抛异常）不用本异常——按裁决 WARN 后继续回退，不得让业务接口 500；
 * 调用方当场写错的入参（显式非法 locale、null 值/币种/时区/档位）用 {@link IllegalArgumentException}。</p>
 *
 * <p>消息措辞见设计 §8.3 逐字表，各抛出点按表实现，不在本类之外另写口径。</p>
 */
public class I18nException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 错误消息（见设计 §8.3）
     */
    public I18nException(String message) {
        super(message);
    }

    /**
     * @param message 错误消息（见设计 §8.3）
     * @param cause   原始成因
     */
    public I18nException(String message, Throwable cause) {
        super(message, cause);
    }
}
