/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.sentinel.core;

import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/**
 * Dashboard 模式回写：用自定义 {@link SentinelProperty} 替换五类规则的 property，
 * 任何来源的规则变更（Dashboard 推送 / loadRules）都会先更新内存、再合并写回规则 JSON 文件。
 * 仅依赖 sentinel-core，不引用任何 transport 类型（回写能力本身与 transport 无关）。
 * 合并写入的原因：规则文件是「单文件 + 分 key」结构，须逐 key 覆盖、其余 key 保留。
 */
final class SentinelRuleWriter implements Closeable {

    private static final String KEY_FLOW = "flowRules";
    private static final String KEY_DEGRADE = "degradeRules";
    private static final String KEY_SYSTEM = "systemRules";
    private static final String KEY_PARAM = "paramFlowRules";
    private static final String KEY_AUTHORITY = "authorityRules";

    private final Path file;
    private final Object lock = new Object();
    private volatile boolean closed;

    private SentinelRuleWriter(Path file) {
        this.file = file;
    }

    /** 用可回写的 property 替换五类规则的 property（替换即捕获所有后续变更），返回写入器供 shutdown 关闭 */
    static Closeable register(Path file) {
        SentinelRuleWriter writer = new SentinelRuleWriter(file);
        FlowRuleManager.register2Property(writer.property(KEY_FLOW));
        DegradeRuleManager.register2Property(writer.property(KEY_DEGRADE));
        SystemRuleManager.register2Property(writer.property(KEY_SYSTEM));
        ParamFlowRuleManager.register2Property(writer.property(KEY_PARAM));
        AuthorityRuleManager.register2Property(writer.property(KEY_AUTHORITY));
        return writer;
    }

    /** 变更时先走默认通知（更新内存），再合并写回文件 */
    private <T> SentinelProperty<List<T>> property(String key) {
        return new DynamicSentinelProperty<List<T>>() {
            @Override
            public boolean updateValue(List<T> newValue) {
                boolean changed = super.updateValue(newValue);
                if (changed) {
                    write(key, newValue);
                }
                return changed;
            }
        };
    }

    private void write(String key, Object rules) {
        if (closed) {
            return;
        }
        synchronized (lock) {
            try {
                JSONObject root = readRoot();
                root.put(key, rules == null ? Collections.emptyList() : rules);
                atomicWrite(JSON.toJSONString(root));
            } catch (IOException e) {
                throw new IllegalStateException("回写 Sentinel 规则文件失败: " + file, e);
            }
        }
    }

    private JSONObject readRoot() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new JSONObject();
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        return text.isEmpty() ? new JSONObject() : JSON.parseObject(text);
    }

    private void atomicWrite(String json) throws IOException {
        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Files.createTempFile(parent, "sentinel-rules", ".tmp");
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
