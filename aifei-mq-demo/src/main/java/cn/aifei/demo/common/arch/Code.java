/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

/**
 * Code
 *
 * <pre>
 * 设计：
 *  1: 使用 Code 接口而非直接使用枚举，扩展新枚举不必修改 Out。
 *
 *  2: 给定 Code 最小集合实现类 OutCode，满足大部分需求。
 *
 *  3: 用户可创建类似 AppCode 枚举类实现 Code 接口，即可融入 Out。
 * </pre>
 */
public interface Code {
    int code();
    String msg();
}

