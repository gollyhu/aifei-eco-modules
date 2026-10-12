/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import cn.aifei.weixin.MsgHandler;
import org.junit.Test;

/**
 * 模板方法分发（S2 验证标准 ③）：8 消息钩子 + 9 事件钩子逐一命中
 */
public class MsgHandlerDispatchTest {

    private final StringBuilder hits = new StringBuilder();

    private final MsgHandler handler = new MsgHandler() {
        @Override
        protected OutMsg processInTextMsg(InTextMsg msg) {
            return hit("text");
        }

        @Override
        protected OutMsg processInImageMsg(InImageMsg msg) {
            return hit("image");
        }

        @Override
        protected OutMsg processInVoiceMsg(InVoiceMsg msg) {
            return hit("voice");
        }

        @Override
        protected OutMsg processInVideoMsg(InVideoMsg msg) {
            return hit("video");
        }

        @Override
        protected OutMsg processInShortVideoMsg(InShortVideoMsg msg) {
            return hit("shortvideo");
        }

        @Override
        protected OutMsg processInLocationMsg(InLocationMsg msg) {
            return hit("location");
        }

        @Override
        protected OutMsg processInLinkMsg(InLinkMsg msg) {
            return hit("link");
        }

        @Override
        protected OutMsg processInSpeechRecognitionMsg(InSpeechRecognitionMsg msg) {
            return hit("speech");
        }

        @Override
        protected OutMsg processInFollowEvent(InFollowEvent event) {
            return hit("follow");
        }

        @Override
        protected OutMsg processInUnFollowEvent(InUnFollowEvent event) {
            return hit("unfollow");
        }

        @Override
        protected OutMsg processInQrCodeEvent(InQrCodeEvent event) {
            return hit("qrcode");
        }

        @Override
        protected OutMsg processInLocationEvent(InLocationEvent event) {
            return hit("locationEvent");
        }

        @Override
        protected OutMsg processInMenuEvent(InMenuEvent event) {
            return hit("menu");
        }

        @Override
        protected OutMsg processInTemplateEvent(InTemplateEvent event) {
            return hit("template");
        }

        @Override
        protected OutMsg processInMassEvent(InMassEvent event) {
            return hit("mass");
        }

        @Override
        protected OutMsg processInCustomEvent(InCustomEvent event) {
            return hit("custom");
        }

        @Override
        protected OutMsg processInVerifyEvent(InVerifyEvent event) {
            return hit("verify");
        }

        private OutMsg hit(String name) {
            hits.append(name).append(',');
            return new OutTextMsg(name);
        }
    };

    @Test
    public void dispatchesAllSeventeenHooks() {
        assertEquals("text", process(new InTextMsg()));
        assertEquals("image", process(new InImageMsg()));
        assertEquals("voice", process(new InVoiceMsg()));
        assertEquals("video", process(new InVideoMsg()));
        assertEquals("shortvideo", process(new InShortVideoMsg()));
        assertEquals("location", process(new InLocationMsg()));
        assertEquals("link", process(new InLinkMsg()));
        assertEquals("speech", process(new InSpeechRecognitionMsg()));
        assertEquals("follow", process(new InFollowEvent()));
        assertEquals("unfollow", process(new InUnFollowEvent()));
        assertEquals("qrcode", process(new InQrCodeEvent()));
        assertEquals("locationEvent", process(new InLocationEvent()));
        assertEquals("menu", process(new InMenuEvent()));
        assertEquals("template", process(new InTemplateEvent()));
        assertEquals("mass", process(new InMassEvent()));
        assertEquals("custom", process(new InCustomEvent()));
        assertEquals("verify", process(new InVerifyEvent()));

        assertEquals("text,image,voice,video,shortvideo,location,link,speech,"
                        + "follow,unfollow,qrcode,locationEvent,menu,template,mass,custom,verify,",
                hits.toString());
    }

    @Test
    public void unhandledTypeReturnsNull() {
        assertNull(handler.process(new InMsg()));
    }

    private String process(InMsg msg) {
        OutMsg outMsg = handler.process(msg);
        return outMsg == null ? null : ((OutTextMsg) outMsg).getContent();
    }
}
