/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用内核发送客户端模拟（零外网）：记录收到的内核消息与其附件落盘内容，支持「成功」与「抛指定异常」两种模式。
 *
 * <p>线程安全：记录表为同步列表，供并发用例断言各次调用互不串扰；{@code send} 不抛异常即模拟发送成功。</p>
 */
public final class MockSms4jMailClient implements org.dromara.email.api.MailClient {

    private final List<Sent> sent = Collections.synchronizedList(new ArrayList<Sent>());

    private volatile RuntimeException failure;

    /**
     * 每次发送的观测快照：内核消息 + 发送时刻的附件字节 + 附件临时文件路径。
     */
    public static final class Sent {

        private final org.dromara.email.comm.entity.MailMessage message;
        private final Map<String, byte[]> attachedBytes;
        private final List<String> tempFilePaths;
        private final Map<String, Boolean> tempFileExistedAtSend;

        Sent(org.dromara.email.comm.entity.MailMessage message) {
            this.message = message;
            Map<String, byte[]> bytes = new LinkedHashMap<String, byte[]>();
            List<String> paths = new ArrayList<String>();
            Map<String, Boolean> existed = new LinkedHashMap<String, Boolean>();
            if (message.getFiles() != null) {
                for (Map.Entry<String, String> entry : message.getFiles().entrySet()) {
                    paths.add(entry.getValue());
                    File file = new File(entry.getValue());
                    existed.put(entry.getKey(), Boolean.valueOf(file.isFile()));
                    bytes.put(entry.getKey(), readAll(file));
                }
            }
            this.attachedBytes = Collections.unmodifiableMap(bytes);
            this.tempFilePaths = Collections.unmodifiableList(paths);
            this.tempFileExistedAtSend = Collections.unmodifiableMap(existed);
        }

        /**
         * @return 内核消息对象
         */
        public org.dromara.email.comm.entity.MailMessage message() {
            return message;
        }

        /**
         * @return 发送时刻读到的附件字节（显示名 → 字节）
         */
        public Map<String, byte[]> attachedBytes() {
            return attachedBytes;
        }

        /**
         * @return 发送时刻的附件临时文件路径
         */
        public List<String> tempFilePaths() {
            return tempFilePaths;
        }

        /**
         * @return 发送时刻各附件临时文件是否真实存在
         */
        public Map<String, Boolean> tempFileExistedAtSend() {
            return tempFileExistedAtSend;
        }

        private static byte[] readAll(File file) {
            if (!file.isFile()) {
                return new byte[0];
            }
            try {
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                throw new IllegalStateException("测试读取附件临时文件失败", e);
            }
        }
    }

    /**
     * 发送：处于失败模式时抛出预设异常，否则记录本次消息（含附件落盘内容）。
     *
     * @param message 内核消息
     */
    @Override
    public void send(org.dromara.email.comm.entity.MailMessage message) {
        RuntimeException preset = failure;
        if (preset != null) {
            throw preset;
        }
        sent.add(new Sent(message));
    }

    /**
     * 切换为失败模式。
     *
     * @param preset 每次发送要抛出的异常
     */
    public void failWith(RuntimeException preset) {
        this.failure = preset;
    }

    /**
     * 切换为成功模式。
     */
    public void succeed() {
        this.failure = null;
    }

    /**
     * @return 已记录的发送次数
     */
    public int count() {
        return sent.size();
    }

    /**
     * @return 最近一次发送的观测快照，未发送过返回 {@code null}
     */
    public Sent last() {
        synchronized (sent) {
            return sent.isEmpty() ? null : sent.get(sent.size() - 1);
        }
    }

    /**
     * @return 全部发送快照的副本
     */
    public List<Sent> all() {
        synchronized (sent) {
            return new ArrayList<Sent>(sent);
        }
    }

    /**
     * 清空记录（保留失败模式）。
     */
    public void clear() {
        sent.clear();
    }
}
