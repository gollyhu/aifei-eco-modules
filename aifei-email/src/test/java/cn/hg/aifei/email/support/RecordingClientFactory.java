/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 测试用客户端构建函数：按 SMTP 服务器地址（通道间互不相同）登记一个模拟客户端，并留存每次收到的内核配置。
 *
 * <p>内核构建函数的入参只有配置对象（不含通道标识），故本类以服务器地址为键，测试据此断言「配置项 → 内核配置」
 * 的映射与「每通道独立实例」。</p>
 */
public final class RecordingClientFactory implements Function<org.dromara.email.comm.config.MailSmtpConfig,
        org.dromara.email.api.MailClient> {

    private final Map<String, MockSms4jMailClient> byServer = new ConcurrentHashMap<String, MockSms4jMailClient>();
    private final List<org.dromara.email.comm.config.MailSmtpConfig> received =
            new CopyOnWriteArrayList<org.dromara.email.comm.config.MailSmtpConfig>();

    @Override
    public org.dromara.email.api.MailClient apply(org.dromara.email.comm.config.MailSmtpConfig config) {
        received.add(config);
        MockSms4jMailClient client = new MockSms4jMailClient();
        byServer.put(config.getSmtpServer(), client);
        return client;
    }

    /**
     * @param server SMTP 服务器地址
     * @return 该服务器对应的模拟客户端，未构建过返回 {@code null}
     */
    public MockSms4jMailClient clientFor(String server) {
        return byServer.get(server);
    }

    /**
     * @param server SMTP 服务器地址
     * @return 该服务器对应的模拟客户端（必然已构建，未构建则抛断言失败语义的异常）
     */
    public MockSms4jMailClient requireClientFor(String server) {
        MockSms4jMailClient client = byServer.get(server);
        if (client == null) {
            throw new IllegalStateException("未构建该 SMTP 服务器的客户端: " + server);
        }
        return client;
    }

    /**
     * @param server SMTP 服务器地址
     * @return 该服务器对应的内核配置，未构建过返回 {@code null}
     */
    public org.dromara.email.comm.config.MailSmtpConfig configFor(String server) {
        for (org.dromara.email.comm.config.MailSmtpConfig config : received) {
            if (server.equals(config.getSmtpServer())) {
                return config;
            }
        }
        return null;
    }

    /**
     * @return 全部构建次数的快照
     */
    public List<org.dromara.email.comm.config.MailSmtpConfig> receivedConfigs() {
        return Collections.unmodifiableList(new ArrayList<org.dromara.email.comm.config.MailSmtpConfig>(received));
    }

    /**
     * @return 已构建的服务器地址集合
     */
    public Map<String, MockSms4jMailClient> clients() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, MockSms4jMailClient>(byServer));
    }

    /**
     * 清空登记内容（保留构建函数本身）。
     */
    public void clear() {
        byServer.clear();
        received.clear();
    }
}
