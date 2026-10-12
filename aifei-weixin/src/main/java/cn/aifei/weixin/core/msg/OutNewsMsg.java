/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import cn.aifei.weixin.WxConsts;
import java.util.ArrayList;
import java.util.List;

/**
 * 图文回复
 */
public class OutNewsMsg extends OutMsg {

    private final List<News> articles = new ArrayList<>();

    public OutNewsMsg() {
        super(WxConsts.ReplyType.NEWS);
    }

    public List<News> getArticles() {
        return articles;
    }

    public OutNewsMsg addArticle(News article) {
        articles.add(article);
        return this;
    }

    public OutNewsMsg addArticle(String title, String description, String picUrl, String url) {
        return addArticle(new News(title, description, picUrl, url));
    }
}
