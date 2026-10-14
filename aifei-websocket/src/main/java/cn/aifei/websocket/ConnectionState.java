/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

/**
 * 连接状态：客户端连接与服务端会话共用同一枚举，语义与 RFC6455 / W3C 一致。
 */
public enum ConnectionState {

    /**
     * 握手进行中。
     */
    CONNECTING,

    /**
     * 已建立，可收发。
     */
    OPEN,

    /**
     * 关闭握手进行中。
     */
    CLOSING,

    /**
     * 已关闭或从未连接。
     */
    CLOSED
}
