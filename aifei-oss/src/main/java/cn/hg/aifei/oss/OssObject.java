/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import java.io.OutputStream;

/**
 * 下载返回的对象内容与元数据。
 *
 * <p><b>无需关闭</b>：本接口<b>不</b>声明 {@code AutoCloseable}。底层下载器是回调式的，并不直接暴露
 * 输入流，内容只经 {@link #bytes()} 或 {@link #writeTo(OutputStream)} 按次读取，输入流每次由实现内部开启
 * 并在读取结束后关闭；调用方无需（也无法）管理流生命周期。因此 {@link #bytes()} 与
 * {@link #writeTo(OutputStream)} 每次调用都会重新向存储平台发起一次读取，可重复调用。</p>
 *
 * <p>全部失败场景统一抛 {@link OssException}。</p>
 */
public interface OssObject {

    /**
     * 返回对象路径。
     *
     * @return 平台 {@code base-path} 之下的完整逻辑对象 key
     */
    String getPath();

    /**
     * 返回对象内容长度。
     *
     * @return 字节数；平台未提供时为 {@code -1}
     */
    long getContentLength();

    /**
     * 返回对象 MIME 类型。
     *
     * @return MIME 类型；平台未提供时为 {@code null}
     */
    String getContentType();

    /**
     * 一次性把对象内容读入字节数组。
     *
     * @return 对象内容
     * @throws OssException 读取失败
     */
    byte[] bytes();

    /**
     * 把对象内容流式写出到指定输出流。
     *
     * <p>只写内容、不关闭 {@code out}，交由调用方管理。</p>
     *
     * @param out 目标输出流
     * @throws OssException 读取或写出失败
     */
    void writeTo(OutputStream out);
}
