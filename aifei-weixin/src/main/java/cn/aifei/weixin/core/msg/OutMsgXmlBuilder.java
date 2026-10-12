/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import cn.aifei.weixin.core.kit.XmlBuilder;

/**
 * 被动回复渲染为明文 XML。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Passive_user_reply_message.html
 */
public class OutMsgXmlBuilder {

    public static String build(OutMsg msg) {
        XmlBuilder xml = new XmlBuilder();
        xml.text("ToUserName", msg.getToUserName());
        xml.text("FromUserName", msg.getFromUserName());
        xml.text("CreateTime", msg.getCreateTime());
        xml.text("MsgType", msg.getMsgType());

        if (msg instanceof OutTextMsg) {
            xml.text("Content", ((OutTextMsg) msg).getContent());
        } else if (msg instanceof OutImageMsg) {
            xml.open("Image").text("MediaId", ((OutImageMsg) msg).getMediaId()).close("Image");
        } else if (msg instanceof OutVoiceMsg) {
            xml.open("Voice").text("MediaId", ((OutVoiceMsg) msg).getMediaId()).close("Voice");
        } else if (msg instanceof OutVideoMsg) {
            OutVideoMsg video = (OutVideoMsg) msg;
            xml.open("Video")
                    .text("MediaId", video.getMediaId())
                    .text("Title", video.getTitle())
                    .text("Description", video.getDescription())
                    .close("Video");
        } else if (msg instanceof OutMusicMsg) {
            OutMusicMsg music = (OutMusicMsg) msg;
            xml.open("Music")
                    .text("Title", music.getTitle())
                    .text("Description", music.getDescription())
                    .text("MusicUrl", music.getMusicUrl())
                    .text("HQMusicUrl", music.getHqMusicUrl())
                    .text("ThumbMediaId", music.getThumbMediaId())
                    .close("Music");
        } else if (msg instanceof OutNewsMsg) {
            OutNewsMsg news = (OutNewsMsg) msg;
            xml.text("ArticleCount", news.getArticles().size());
            xml.open("Articles");
            for (News article : news.getArticles()) {
                xml.open("item")
                        .text("Title", article.getTitle())
                        .text("Description", article.getDescription())
                        .text("PicUrl", article.getPicUrl())
                        .text("Url", article.getUrl())
                        .close("item");
            }
            xml.close("Articles");
        }

        return xml.build();
    }

    private OutMsgXmlBuilder() {
    }
}
