/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.spi;

/**
 * 传输层 SPI：把微信公众号回调的 HTTP 细节抽象出来。
 *
 * <pre>
 * 设计要点：
 *  1: 泛型无上界，本接口（以及整个 spi 包）不依赖 aifei 内核任何类型，
 *     由自动化测试 KernelBindingTest 断言（见 docs/开发规范/微信接入规范.md 架构门禁）。
 *  2: query 必须保留：验签所需的 signature/msg_signature/timestamp/nonce/echostr/encrypt_type
 *     全部来自 query；无上界泛型下 MsgKit 无法调用内核 Input.getStr(...)。
 * </pre>
 *
 * @param <I> 具体传输层的请求对象类型
 */
public interface WeixinIo<I> {

    /**
     * 取请求参数（微信回调的验签参数全部来自 query），无则返回 null
     */
    String query(I in, String name);

    /**
     * 读原始 body（UTF-8），一次性读完
     */
    String body(I in);

    /**
     * 是否为 GET 请求（GET 为 URL 首验，POST 为消息回调）
     */
    boolean isGet(I in);

    /**
     * 以 text/xml 写回响应并结束本次交换。
     * 参数 xml 为 "success" 表示无被动回复。
     */
    void replyXml(I in, String xml);
}
