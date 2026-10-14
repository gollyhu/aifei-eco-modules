/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken.undertow;

import cn.dev33.satoken.context.model.SaStorage;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link SaStorage} 的 Undertow 桥接实现。
 */
final class SaStorageForUndertow implements SaStorage {

    /** 挂载在 exchange 上的存储 Map；AttachmentKey 为进程级共享，值随 exchange 销毁 */
    @SuppressWarnings("unchecked")
    private static final AttachmentKey<Map<String, Object>> STORAGE_KEY =
            (AttachmentKey<Map<String, Object>>) (AttachmentKey<?>) AttachmentKey.create(Map.class);

    private final HttpServerExchange exchange;

    SaStorageForUndertow(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Object getSource() {
        return exchange;
    }

    @Override
    public Object get(String key) {
        return storageMap().get(key);
    }

    @Override
    public SaStorage set(String key, Object value) {
        storageMap().put(key, value);
        return this;
    }

    @Override
    public SaStorage delete(String key) {
        storageMap().remove(key);
        return this;
    }

    /** 读取当前请求的存储 Map，不存在时按需创建 */
    private Map<String, Object> storageMap() {
        Map<String, Object> storage = exchange.getAttachment(STORAGE_KEY);
        if (storage == null) {
            storage = new LinkedHashMap<>();
            exchange.putAttachment(STORAGE_KEY, storage);
        }
        return storage;
    }
}
