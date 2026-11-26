/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消息映射结果载体（非承诺实现层构件）：承载本次调用产出的内核消息对象与临时文件列表。
 *
 * <p>由映射方法每次新建返回，发送实现持有并在 {@code finally} 中按 {@link #getTempFiles()} 清理，
 * 全程不使用静态字段与 {@code ThreadLocal} 跟踪临时文件，杜绝并发串扰与泄漏。</p>
 */
public final class MappedMessage {

    private final org.dromara.email.comm.entity.MailMessage message;
    private final List<Path> tempFiles;

    /**
     * @param message   内核消息对象
     * @param tempFiles 本次调用产生的临时文件（附件落盘结果）
     */
    public MappedMessage(org.dromara.email.comm.entity.MailMessage message, List<Path> tempFiles) {
        this.message = message;
        this.tempFiles = Collections.unmodifiableList(
                new ArrayList<Path>(tempFiles == null ? Collections.<Path>emptyList() : tempFiles));
    }

    /**
     * @return 内核消息对象
     */
    public org.dromara.email.comm.entity.MailMessage getMessage() {
        return message;
    }

    /**
     * @return 本次调用产生的临时文件（不可变，按写入顺序）
     */
    public List<Path> getTempFiles() {
        return tempFiles;
    }
}
