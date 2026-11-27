/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.util.Map;

/**
 * 内核边界：把「预下单 / 页面下单 / 查单 / 退款」四个内核调用抽象为包级可见接口。
 *
 * <p><b>非承诺 API，也不是扩展点</b>：唯一的生产实现是 {@link IjpayPayKernel}，接口存在的目的是让单测
 * 能注入伪内核（模块零第三方 mock 依赖），因此本类型刻意不加 {@code public}——包外不可见。</p>
 *
 * <p>返回 {@code Object} 是刻意的：响应类型不外泄给 {@link DefaultPayClient}，转换由
 * {@link PayResults} 承担。</p>
 */
interface PayKernel {

    /**
     * 当面付扫码预下单。
     *
     * @param app       应用配置
     * @param bizParams 报文参数
     * @return 内核响应对象
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    Object precreate(PayAppConfig app, Map<String, Object> bizParams) throws Exception;

    /**
     * 页面类下单（H5 / PC）。
     *
     * @param app        应用配置
     * @param method     支付宝接口方法名
     * @param httpMethod HTTP 方法 {@code GET} / {@code POST}
     * @param bizParams  报文参数
     * @return 载荷：{@code GET} 为跳转 URL，{@code POST} 为自提交表单 HTML
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    String pagePay(PayAppConfig app, String method, String httpMethod, Map<String, Object> bizParams)
            throws Exception;

    /**
     * 查单。
     *
     * @param app       应用配置
     * @param bizParams 报文参数
     * @return 内核响应对象
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    Object query(PayAppConfig app, Map<String, Object> bizParams) throws Exception;

    /**
     * 退款。
     *
     * @param app       应用配置
     * @param bizParams 报文参数
     * @return 内核响应对象
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    Object refund(PayAppConfig app, Map<String, Object> bizParams) throws Exception;
}
