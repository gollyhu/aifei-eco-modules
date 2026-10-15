/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Socket 创建与关闭的唯一定义点：连接超时、{@code wss} 的 {@code SSLSocket} 与主机名校验、远端地址格式化与
 * 安全关闭都只在本类出现一次。
 *
 * <p>{@code wss} 一侧在完成 TLS 握手前必须设置 {@code SSLParameters#setEndpointIdentificationAlgorithm("HTTPS")}，
 * 即<b>必须做主机名校验</b>，不得只完成 TLS 握手而跳过身份校验。本类属内部实现，不是公共 API。</p>
 *
 * <p>线程安全：本类无状态，方法可被多线程并发调用。</p>
 */
public final class JdkSocketSupport {

    /**
     * 禁止创建工具类实例。
     */
    private JdkSocketSupport() {
    }

    /**
     * 建立一条到目标地址 TCP 连接；{@code secure} 为真时建立 TLS 连接并完成主机名校验。
     *
     * @param host 目标主机
     * @param port 目标端口
     * @param connectTimeoutMillis 连接超时（毫秒）
     * @param secure 是否建立 TLS 连接（{@code wss}）
     * @return 已连接的套接字；不为 {@code null}
     * @throws IOException 连接失败、超时、TLS 握手失败或主机名校验失败
     */
    public static Socket connect(String host, int port, int connectTimeoutMillis, boolean secure) throws IOException {
        Socket socket;
        if (secure) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            Socket created = factory.createSocket();
            created.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            SSLSocket sslSocket = (SSLSocket) created;
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
        }
        socket.setTcpNoDelay(true);
        return socket;
    }

    /**
     * 格式化远端地址为 {@code host:port} 形式；地址不可用时返回 {@code null}。
     *
     * @param address 远端地址；可为 {@code null}
     * @return 格式化后的地址；不可用时 {@code null}
     */
    public static String formatAddress(SocketAddress address) {
        if (address == null) {
            return null;
        }
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) address;
            return inet.getHostString() + ":" + inet.getPort();
        }
        String text = address.toString();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        return text;
    }

    /**
     * 安全关闭套接字：关闭失败不上浮，供连接收敛路径调用。
     *
     * @param socket 待关闭的套接字；可为 {@code null}
     */
    public static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关闭失败不影响连接收敛结论
        }
    }

    /**
     * 安全关闭监听套接字：关闭失败不上浮，供服务端停止路径调用。
     *
     * @param serverSocket 待关闭的监听套接字；可为 {@code null}
     */
    public static void closeQuietly(ServerSocket serverSocket) {
        if (serverSocket == null) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // 关闭失败不影响服务端停止结论
        }
    }

    /**
     * 安全关闭任意可关闭对象（握手期间的流等）；关闭失败不上浮。
     *
     * @param closeable 待关闭对象；可为 {@code null}
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 关闭失败不影响连接收敛结论
        }
    }
}
