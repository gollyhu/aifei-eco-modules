/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

/**
 * 本模块唯一异常类型，覆盖参数错误、IO 错误与解析转换错误三类失败，并保留底层 cause。
 *
 * <p><b>本异常由模块内部抛出，业务方只 {@code catch}、不要 {@code new}。</b>
 * 构造器为 {@code public} 是因为唯一构造点 {@code internal.easyexcel.ExcelErrors} 与公共类型不同包
 * （Java 包私有语义所限），并非对外构造入口。消息必然携带定位信息（参数名 / 行号 / 列名 / 文件路径语义），
 * 并保留底层 cause。</p>
 *
 * <p>作为 {@link RuntimeException} 的子类，公共方法均不声明 {@code throws} 子句；使用方按需捕获。
 * 所有失败都不降级：不返回半截数据、不静默跳过错误行、不吞掉 cause。</p>
 */
public final class ExcelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造仅携带消息的自有异常（参数错误、依赖缺失等无底层 cause 的场景）。
     *
     * @param message 异常消息，由模块内部唯一构造点生成，携带定位信息
     */
    public ExcelException(String message) {
        super(message);
    }

    /**
     * 构造携带消息与底层原因的异常（IO 错误、解析转换错误、回调异常等场景）。
     *
     * @param message 异常消息，由模块内部唯一构造点生成，携带定位信息
     * @param cause   底层异常，原样保留便于排障，不得为 null
     */
    public ExcelException(String message, Throwable cause) {
        super(message, cause);
    }
}
