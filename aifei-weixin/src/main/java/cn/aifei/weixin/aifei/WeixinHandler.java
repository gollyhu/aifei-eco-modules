/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.aifei;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.log.Log;
import cn.aifei.weixin.MsgHandler;
import cn.aifei.weixin.MsgKit;
import cn.aifei.weixin.WxConsts;
import cn.aifei.weixin.spi.WeixinAccount;
import cn.aifei.weixin.spi.WeixinAccountProvider;
import cn.aifei.weixin.spi.WeixinIo;
import java.util.Objects;

/**
 * aifei 接入层：微信回调 handler 旁路。
 *
 * <pre>
 * 注意：
 *  1: 必须注册在 FrontHandler 之前。微信首验 GET 不带 Content-Type，否则会被判定为
 *     浏览器刷新并渲染首页 HTML，导致首验静默失败（由 G4 顺序断言测试守护）。
 *  2: 匹配到微信路径即闭环（自行读 body、写 text/xml 并结束交换），不再调用 next。
 *  3: 本类是 SDK 中唯一 import aifei 内核（cn.aifei.core.*）的包。
 * </pre>
 *
 * @param <I> 内核 Input 实现
 * @param <O> 内核 Output 实现
 */
public class WeixinHandler<I extends Input, O extends Output> extends Handler<I, O> {

    static final Log log = Log.get(WeixinHandler.class);

    public static final String DEFAULT_PREFIX = "/weixin";

    private final String prefix;
    private final WeixinIo<I> io;
    private final WeixinAccountProvider accountProvider;
    private final MsgHandler msgHandler;

    /**
     * 完整构造：显式前缀（默认 "/weixin"），路径约定 {@code /{prefix}/{appId}/msg}
     */
    public WeixinHandler(String prefix, WeixinIo<I> io,
                         WeixinAccountProvider accountProvider, MsgHandler msgHandler) {
        this.prefix = normalizePrefix(prefix);
        this.io = Objects.requireNonNull(io, "io can not be null.");
        this.accountProvider = Objects.requireNonNull(accountProvider, "accountProvider can not be null.");
        this.msgHandler = Objects.requireNonNull(msgHandler, "msgHandler can not be null.");
    }

    public WeixinHandler(WeixinIo<I> io, WeixinAccountProvider accountProvider, MsgHandler msgHandler) {
        this(DEFAULT_PREFIX, io, accountProvider, msgHandler);
    }

    @Override
    public void handle(String path, I in, O out) throws Exception {
        if (path == null || !path.startsWith(prefix + "/")) {
            next.handle(path, in, out);
            return;
        }

        String appId = accountId(in, path);
        WeixinAccount account = appId == null ? null : accountProvider.get(appId);
        if (account == null) {
            // 未知 appId 直接回 fail，不进 MsgKit
            log.warn("微信回调未知 appId={} path={}", appId, path);
            io.replyXml(in, WxConsts.RESP_FAIL);
            return;
        }

        String response = io.isGet(in)
                ? MsgKit.verify(io, in, account)
                : MsgKit.handle(io, in, account, msgHandler);
        io.replyXml(in, response);
    }

    /**
     * 解析 appId：默认取 prefix 后第一段；子类可覆盖以支持二级子域名 / 租户解析
     */
    protected String accountId(I in, String path) {
        String rest = path.substring(prefix.length());
        int start = rest.indexOf('/');
        if (start < 0) {
            return null;
        }
        int end = rest.indexOf('/', start + 1);
        String appId = end < 0 ? rest.substring(start + 1) : rest.substring(start + 1, end);
        return appId.isEmpty() ? null : appId;
    }

    private static String normalizePrefix(String prefix) {
        String ret = Objects.requireNonNull(prefix, "prefix can not be null.").trim();
        while (ret.endsWith("/")) {
            ret = ret.substring(0, ret.length() - 1);
        }
        if (ret.isEmpty()) {
            throw new IllegalArgumentException("prefix can not be blank.");
        }
        return ret.startsWith("/") ? ret : "/" + ret;
    }
}
