/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.redis;

/**
 * 定义 Redis 缓存值的编解码方式。
 */
public interface RedisValueCodec {

    /**
     * 将非空对象序列化为非空字节数组。
     */
    byte[] serialize(Object value);

    /**
     * 将字节数组反序列化为非空对象。
     */
    Object deserialize(byte[] bytes);
}
