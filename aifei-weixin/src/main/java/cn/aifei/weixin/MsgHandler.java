/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin;

import cn.aifei.weixin.core.msg.InCustomEvent;
import cn.aifei.weixin.core.msg.InFollowEvent;
import cn.aifei.weixin.core.msg.InImageMsg;
import cn.aifei.weixin.core.msg.InLinkMsg;
import cn.aifei.weixin.core.msg.InLocationEvent;
import cn.aifei.weixin.core.msg.InLocationMsg;
import cn.aifei.weixin.core.msg.InMassEvent;
import cn.aifei.weixin.core.msg.InMenuEvent;
import cn.aifei.weixin.core.msg.InMsg;
import cn.aifei.weixin.core.msg.InQrCodeEvent;
import cn.aifei.weixin.core.msg.InShortVideoMsg;
import cn.aifei.weixin.core.msg.InSpeechRecognitionMsg;
import cn.aifei.weixin.core.msg.InTemplateEvent;
import cn.aifei.weixin.core.msg.InTextMsg;
import cn.aifei.weixin.core.msg.InUnFollowEvent;
import cn.aifei.weixin.core.msg.InVerifyEvent;
import cn.aifei.weixin.core.msg.InVideoMsg;
import cn.aifei.weixin.core.msg.InVoiceMsg;
import cn.aifei.weixin.core.msg.OutMsg;

/**
 * 业务扩展点（模板方法，返回值驱动）。
 *
 * <p>与 jfinal-weixin 的关键差异：钩子返回 {@link OutMsg} 而非直接 render，
 * 业务代码感知不到 HTTP。返回 null 表示不回复（响应 success）。
 */
public abstract class MsgHandler {

    /**
     * 模板方法：按 InMsg 类型分发到具体钩子，返回 null 表示不回复
     */
    public OutMsg process(InMsg msg) {
        if (msg instanceof InTextMsg) {
            return processInTextMsg((InTextMsg) msg);
        } else if (msg instanceof InImageMsg) {
            return processInImageMsg((InImageMsg) msg);
        } else if (msg instanceof InVoiceMsg) {
            return processInVoiceMsg((InVoiceMsg) msg);
        } else if (msg instanceof InVideoMsg) {
            return processInVideoMsg((InVideoMsg) msg);
        } else if (msg instanceof InShortVideoMsg) {
            return processInShortVideoMsg((InShortVideoMsg) msg);
        } else if (msg instanceof InLocationMsg) {
            return processInLocationMsg((InLocationMsg) msg);
        } else if (msg instanceof InLinkMsg) {
            return processInLinkMsg((InLinkMsg) msg);
        } else if (msg instanceof InSpeechRecognitionMsg) {
            return processInSpeechRecognitionMsg((InSpeechRecognitionMsg) msg);
        } else if (msg instanceof InFollowEvent) {
            return processInFollowEvent((InFollowEvent) msg);
        } else if (msg instanceof InUnFollowEvent) {
            return processInUnFollowEvent((InUnFollowEvent) msg);
        } else if (msg instanceof InQrCodeEvent) {
            return processInQrCodeEvent((InQrCodeEvent) msg);
        } else if (msg instanceof InLocationEvent) {
            return processInLocationEvent((InLocationEvent) msg);
        } else if (msg instanceof InMenuEvent) {
            return processInMenuEvent((InMenuEvent) msg);
        } else if (msg instanceof InTemplateEvent) {
            return processInTemplateEvent((InTemplateEvent) msg);
        } else if (msg instanceof InMassEvent) {
            return processInMassEvent((InMassEvent) msg);
        } else if (msg instanceof InCustomEvent) {
            return processInCustomEvent((InCustomEvent) msg);
        } else if (msg instanceof InVerifyEvent) {
            return processInVerifyEvent((InVerifyEvent) msg);
        }
        return null;
    }

    // ---------------- 消息钩子：8 个 ----------------

    protected OutMsg processInTextMsg(InTextMsg msg) {
        return null;
    }

    protected OutMsg processInImageMsg(InImageMsg msg) {
        return null;
    }

    protected OutMsg processInVoiceMsg(InVoiceMsg msg) {
        return null;
    }

    protected OutMsg processInVideoMsg(InVideoMsg msg) {
        return null;
    }

    protected OutMsg processInShortVideoMsg(InShortVideoMsg msg) {
        return null;
    }

    protected OutMsg processInLocationMsg(InLocationMsg msg) {
        return null;
    }

    protected OutMsg processInLinkMsg(InLinkMsg msg) {
        return null;
    }

    protected OutMsg processInSpeechRecognitionMsg(InSpeechRecognitionMsg msg) {
        return null;
    }

    // ---------------- 事件钩子：9 个（CLICK 与 VIEW 共用 processInMenuEvent） ----------------

    protected OutMsg processInFollowEvent(InFollowEvent event) {
        return null;
    }

    protected OutMsg processInUnFollowEvent(InUnFollowEvent event) {
        return null;
    }

    protected OutMsg processInQrCodeEvent(InQrCodeEvent event) {
        return null;
    }

    protected OutMsg processInLocationEvent(InLocationEvent event) {
        return null;
    }

    protected OutMsg processInMenuEvent(InMenuEvent event) {
        return null;
    }

    protected OutMsg processInTemplateEvent(InTemplateEvent event) {
        return null;
    }

    protected OutMsg processInMassEvent(InMassEvent event) {
        return null;
    }

    protected OutMsg processInCustomEvent(InCustomEvent event) {
        return null;
    }

    protected OutMsg processInVerifyEvent(InVerifyEvent event) {
        return null;
    }
}
