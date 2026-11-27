/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.defaultSource;

import cn.hg.aifei.i18n.MessageSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认来源·db：只读、参数化查询，不执行任何 DDL（建表 SQL 随 README 交付，由消费方执行）。
 *
 * <p>表契约（固定，变更属破坏性）：{@code i18n_message(locale, msg_key, content)}。
 * 单点命中 {@code SELECT content FROM i18n_message WHERE locale=? AND msg_key=?}；
 * 整包列举 {@code SELECT msg_key, content FROM i18n_message WHERE locale=?}（可列举）。</p>
 *
 * <p>框架 {@code cn.aifei.db.core.Db/Row} 由 {@code cn.aifei:aifei-db}(provided) 提供，引用只在方法体内使用全限定名，
 * 字段与签名只出现 JDK / 本模块类型。</p>
 */
public final class DbSource implements MessageSource {

    private static final String TABLE = "i18n_message";
    private static final String COL_LOCALE = "locale";
    private static final String COL_KEY = "msg_key";
    private static final String COL_CONTENT = "content";

    @Override
    public String name() {
        return "db";
    }

    @Override
    public String get(String key, String localeTag) {
        String sql = "SELECT " + COL_CONTENT + " FROM " + TABLE + " WHERE " + COL_LOCALE + "=? AND " + COL_KEY + "=?";
        cn.aifei.db.core.Row row = cn.aifei.db.core.Db.sql(sql, localeTag, key).findFirst();
        return row == null ? null : row.getStr(COL_CONTENT);
    }

    @Override
    public Map<String, String> list(String localeTag) {
        String sql = "SELECT " + COL_KEY + ", " + COL_CONTENT + " FROM " + TABLE + " WHERE " + COL_LOCALE + "=?";
        List<cn.aifei.db.core.Row> rows = cn.aifei.db.core.Db.sql(sql, localeTag).find();
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (rows != null) {
            for (cn.aifei.db.core.Row row : rows) {
                result.put(row.getStr(COL_KEY), row.getStr(COL_CONTENT));
            }
        }
        return result;
    }
}
