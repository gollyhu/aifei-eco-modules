/* ==========================================================================
 * aifei-mq-demo 实例演示页交互脚本（view/mq/demo.html）
 * 档案 A（Aifei 静态层）：纯原生 JS + 本地全局构建版 Vue（/assets/lib/vue.global.prod.js），
 * 无构建工具、无外部依赖。
 *
 * 端点（全部 GET，HTTP 恒 200，业务看信封 code）：state / messages / messages/clear /
 * subscribe / unsubscribe / send / send/message / event/send / event/dispatch /
 * event/dispatch/clear。每个请求都显式携带非空 instance。
 * ========================================================================== */

(function () {
    "use strict";

    var Vue = window.Vue;

    // ---- 常量 ----
    var MAIN_INSTANCE = "__MAIN__";
    var DEFAULT_TOPIC = "aifei.mq.demo.topic";      // 与 DemoAppConfig.DEMO_TOPIC 一致
    var E2_DEFAULT_BIZ_ID = "A1001";                // 与 E2Topics.DEFAULT_BIZ_ID 一致
    var POLL_NORMAL = 2000;
    var POLL_SLOW = 5000;
    var SLOW_AFTER_FAILS = 3;
    var STARTUP_CHIP_TITLE = "装配期订阅由 DemoAppConfig.onStart() 注册，页面无法取消；如需取消请改代码后重启";

    // ---- 时间格式化 ----
    function pad(n, len) {
        var s = String(n);
        while (s.length < len) {
            s = "0" + s;
        }
        return s;
    }

    function nowClock() {
        var d = new Date();
        return pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2) + ":" + pad(d.getSeconds(), 2)
            + "." + pad(d.getMilliseconds(), 3);
    }

    function nowDateTime() {
        var d = new Date();
        return d.getFullYear() + "-" + pad(d.getMonth() + 1, 2) + "-" + pad(d.getDate(), 2)
            + " " + pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2);
    }

    // 实例名一律从 URL 读；缺失 / 空串回落主实例。
    // 关键：后端把「空串」当作未提供并回落主实例，故前端必须始终显式传非空实例名。
    function readInstanceName() {
        var name = "";
        try {
            name = new URLSearchParams(window.location.search).get("instance") || "";
        } catch (e) {
            name = "";
        }
        name = name.trim();
        return name === "" ? MAIN_INSTANCE : name;
    }

    var INSTANCE = readInstanceName();

    // 拼请求地址：instance 恒为第一参数且非空；其余参数按需追加
    function apiUrl(path, params) {
        var parts = ["instance=" + encodeURIComponent(INSTANCE)];
        if (params) {
            Object.keys(params).forEach(function (key) {
                var v = params[key];
                parts.push(key + "=" + encodeURIComponent(v === null || v === undefined ? "" : String(v)));
            });
        }
        return path + "?" + parts.join("&");
    }

    // 取信封：网络失败 / 非 JSON 一律 reject；HTTP 200 + code != 0 视为业务失败，由调用方呈现 msg
    function requestEnvelope(url) {
        return fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("HTTP " + response.status);
                }
                return response.text();
            })
            .then(function (text) {
                if (!text) {
                    throw new Error("响应为空");
                }
                var env;
                try {
                    env = JSON.parse(text);
                } catch (e) {
                    throw new Error("响应不是合法 JSON");
                }
                if (!env || typeof env !== "object") {
                    throw new Error("响应格式异常");
                }
                return env;
            });
    }

    function num(v) {
        return v === null || v === undefined || v === "" ? "—" : String(v);
    }

    var app = Vue.createApp({
        setup: function () {
            // ---- 顶部信息条 ----
            var view = Vue.reactive({
                instance: INSTANCE,
                mqType: "—",
                prefixText: "—",
                subscribedTopicCount: "0",
                sentCount: "—",
                receivedCount: "—",
                messageLogSize: "—",
                messageLogCapacity: "—"
            });
            var phase = Vue.ref("loading");            // loading | ready | failed
            var stateErrorDetail = Vue.ref("");        // 后端 msg
            var fatal = Vue.ref(false);                // 未知实例：整页降级（不自动跳转）
            var lastError = Vue.reactive({ text: "", time: "" });

            // ---- 订阅区 ----
            var subscribedTopics = Vue.ref([]);
            var subscribers = Vue.ref([]);
            var subTopic = Vue.ref("");
            var subBusy = Vue.ref(false);
            var unsubBusy = Vue.ref(false);
            var subMsg = Vue.ref("");
            var subMsgOk = Vue.ref(true);

            // ---- 发布区 ----
            var pubTopic = Vue.ref(DEFAULT_TOPIC);
            var pubText = Vue.ref("hello");
            var sendMode = Vue.ref("text");            // text | message
            var sendBusy = Vue.ref(false);
            var receipt = Vue.reactive({ show: false, cls: "", head: "", detail: "", extra: "", note: "" });

            // ---- 消息流 ----
            var msgList = Vue.ref([]);                 // 后端顺序（最新在前），不再排序
            var sentRows = Vue.ref([]);                // 本页发送行（最新在前，不参与轮询）
            var firstLoading = Vue.ref(true);
            var msgError = Vue.ref("");
            var pollSlow = Vue.ref(false);
            var paused = Vue.ref(false);
            var autoScroll = Vue.ref(true);
            var copyLabel = Vue.ref("复制");
            var logNotice = Vue.ref("");
            var clearBusy = Vue.ref(false);

            // ---- E2 事件分发（固定 topic + payload 内 type；不进消息流） ----
            var e2 = Vue.reactive({ domain: "", type: "", bizId: E2_DEFAULT_BIZ_ID, sentPayload: "" });
            var dispatch = Vue.reactive({
                domainTopics: [], typeOptions: {}, defaultDomain: "", defaultType: "",
                totalReceived: 0, unknownCount: 0, rows: []
            });
            var e2Loading = Vue.ref(true);              // 首屏骨架；成功/失败后置 false
            var e2Error = Vue.ref("");
            var e2SendBusy = Vue.ref(false);
            var e2ClearBusy = Vue.ref(false);
            var e2Receipt = Vue.reactive({ show: false, cls: "", head: "", detail: "", extra: "", note: "" });

            // ---- 抽屉 / 模板引用 ----
            var drawerOpen = Vue.ref(false);
            var logBody = Vue.ref(null);
            var subCard = Vue.ref(null);
            var drawerRef = Vue.ref(null);

            // ---- 非响应式内部句柄 ----
            var pollTimer = null;
            var pollInterval = POLL_NORMAL;
            var pollFailCount = 0;
            var copyTimer = null;
            var noticeTimer = null;

            // ================= 顶部状态 =================
            function setLastError(text) {
                lastError.text = text || "";
                lastError.time = nowDateTime();
            }

            function applyState(d) {
                d = d || {};
                view.instance = d.instance ? String(d.instance) : INSTANCE;
                view.mqType = d.mqType ? String(d.mqType) : "—";
                view.prefixText = d.configPrefix ? String(d.configPrefix) + ".*" : "—";
                applySubscriptions(d);
                view.sentCount = num(d.sentCount);
                view.receivedCount = num(d.receivedCount);
                view.messageLogSize = num(d.messageLogSize);
                view.messageLogCapacity = num(d.messageLogCapacity);
                phase.value = "ready";
                fatal.value = false;
                stateErrorDetail.value = "";
            }

            function markStateFailed(msg) {
                var text = msg ? String(msg) : "请求失败";
                phase.value = "failed";
                fatal.value = text.indexOf("未知实例") >= 0;
                stateErrorDetail.value = text;
                setLastError("读取状态失败：" + text);
            }

            function applySubscriptions(d) {
                if (!d) {
                    return;
                }
                if (Object.prototype.hasOwnProperty.call(d, "subscribedTopics")) {
                    subscribedTopics.value = Array.isArray(d.subscribedTopics) ? d.subscribedTopics : [];
                }
                if (Object.prototype.hasOwnProperty.call(d, "subscribers")) {
                    subscribers.value = Array.isArray(d.subscribers) ? d.subscribers : [];
                }
                view.subscribedTopicCount = num(
                    d.subscribedTopicCount !== undefined && d.subscribedTopicCount !== null
                        ? d.subscribedTopicCount
                        : subscribedTopics.value.length
                );
            }

            // ================= 消息流 =================
            function applyMessages(d) {
                d = d || {};
                msgList.value = Array.isArray(d.messages) ? d.messages : [];   // 保持后端顺序
                if (d.maxEntries !== undefined && d.maxEntries !== null) {
                    view.messageLogCapacity = String(d.maxEntries);
                }
                msgError.value = "";
                firstLoading.value = false;
                scrollAfterRender();
            }

            function markMessagesFailed(msg) {
                msgError.value = msg ? String(msg) : "请求失败";
                firstLoading.value = false;
                setLastError("读取消息记录失败：" + msgError.value);
            }

            function serverEntry(m) {
                var metaParts = [];
                if (m.payloadSize !== undefined && m.payloadSize !== null) {
                    metaParts.push(m.payloadSize + "B");
                }
                if (m.subscriber) {
                    metaParts.push(String(m.subscriber));
                }
                return {
                    key: "r-" + num(m.sequence) + "-" + String(m.receivedAt || "") + "-" + String(m.topic || ""),
                    receivedAt: m.receivedAt ? String(m.receivedAt) : "—",
                    dir: "recv",
                    topic: m.topic ? String(m.topic) : "—",
                    text: m.text === null || m.text === undefined ? "" : String(m.text),
                    meta: metaParts.join(" · ")
                };
            }

            // 两个「各自已是最新在前」的列表做一次归并（不改动各自内部顺序）
            var entries = Vue.computed(function () {
                var server = msgList.value || [];
                var local = sentRows.value || [];
                var out = [];
                var i = 0;
                var j = 0;
                while (i < server.length && j < local.length) {
                    var serverTime = String(server[i].receivedAt || "");
                    if (serverTime >= local[j].receivedAt) {
                        out.push(serverEntry(server[i]));
                        i++;
                    } else {
                        out.push(local[j]);
                        j++;
                    }
                }
                while (i < server.length) {
                    out.push(serverEntry(server[i]));
                    i++;
                }
                while (j < local.length) {
                    out.push(local[j]);
                    j++;
                }
                return out;
            });

            var total = Vue.computed(function () {
                return entries.value.length;
            });

            function capacityNumber() {
                var cap = parseInt(view.messageLogCapacity, 10);
                return isNaN(cap) || cap <= 0 ? 0 : cap;   // 0 = 未知上限（不裁剪）
            }

            function prependSentRow(d) {
                var rows = sentRows.value.slice();
                var metaParts = [];
                if (d.messageId !== undefined && d.messageId !== null) {
                    metaParts.push("messageId=" + d.messageId);
                }
                if (d.delivered !== undefined && d.delivered !== null) {
                    metaParts.push("delivered=" + d.delivered);
                }
                rows.unshift({
                    key: "s-" + num(d.messageId) + "-" + String(Date.now()),
                    receivedAt: nowClock(),
                    dir: "send",
                    topic: d.topic ? String(d.topic) : pubTopic.value.trim(),
                    text: d.text === null || d.text === undefined ? "" : String(d.text),
                    meta: metaParts.join(" · ")
                });
                var cap = capacityNumber();
                if (cap > 0 && rows.length > cap) {
                    rows = rows.slice(0, cap);
                }
                sentRows.value = rows;
                scrollAfterRender();
            }

            function scrollAfterRender() {
                Vue.nextTick(function () {
                    var el = logBody.value;
                    if (!el || !autoScroll.value) {
                        return;
                    }
                    // 最新在上：用户未向下滚动时保持顶部
                    if (el.scrollTop <= 24) {
                        el.scrollTop = 0;
                    }
                });
            }

            function flashLog(text) {
                logNotice.value = text;
                if (noticeTimer) {
                    clearTimeout(noticeTimer);
                }
                noticeTimer = setTimeout(function () {
                    noticeTimer = null;
                    logNotice.value = "";
                }, 4000);
            }

            // ================= 取数 =================
            function fetchState() {
                return requestEnvelope(apiUrl("/mq/demo/state"))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            markStateFailed(env.msg);
                            return false;
                        }
                        applyState(env.data);
                        return true;
                    })
                    .catch(function (err) {
                        markStateFailed(err && err.message);
                        return false;
                    });
            }

            function fetchMessages() {
                return requestEnvelope(apiUrl("/mq/demo/messages"))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            markMessagesFailed(env.msg);
                            return false;
                        }
                        applyMessages(env.data);
                        return true;
                    })
                    .catch(function (err) {
                        markMessagesFailed(err && err.message);
                        return false;
                    });
            }

            // ================= E2 分发统计（并入既有轮询，不新开定时器） =================
            function countOf(v) {
                var n = Number(v);
                return isNaN(n) ? 0 : n;
            }

            function textOf(v) {
                return v === null || v === undefined ? "" : String(v);
            }

            function typeListOf(domain) {
                var list = (dispatch.typeOptions || {})[textOf(domain)];
                return Array.isArray(list) ? list.map(String) : [];
            }

            function applyDispatch(d) {
                d = d || {};
                dispatch.domainTopics = Array.isArray(d.domainTopics) ? d.domainTopics.map(String) : [];
                dispatch.typeOptions = d.typeOptions && typeof d.typeOptions === "object" ? d.typeOptions : {};
                dispatch.defaultDomain = textOf(d.defaultDomain);
                dispatch.defaultType = textOf(d.defaultType);
                dispatch.totalReceived = countOf(d.totalReceived);
                dispatch.unknownCount = countOf(d.unknownCount);
                dispatch.rows = Array.isArray(d.rows) ? d.rows : [];
                e2Error.value = "";
                e2Loading.value = false;
                syncE2Selection();
            }

            // 选项就绪后把下拉对齐后端候选：域越界 → 缺省域；类型越界 → 该域首个（= 该域缺省类型）
            function syncE2Selection() {
                var domains = dispatch.domainTopics || [];
                if (!domains.length) {
                    return;
                }
                if (domains.indexOf(e2.domain) < 0) {
                    e2.domain = domains.indexOf(dispatch.defaultDomain) >= 0 ? dispatch.defaultDomain : domains[0];
                }
                var types = typeListOf(e2.domain);
                if (types.indexOf(e2.type) < 0) {
                    // 首屏取后端 defaultType；该域没有它（或已换域）则取该域首个类型
                    e2.type = e2.domain === dispatch.defaultDomain && types.indexOf(dispatch.defaultType) >= 0
                        ? dispatch.defaultType : (types.length ? types[0] : "");
                }
            }

            function markDispatchFailed(msg) {
                e2Error.value = msg ? String(msg) : "请求失败";
                e2Loading.value = false;
                setLastError("读取分发统计失败：" + e2Error.value);
            }

            // 失败只标错误、不改 dispatch（保留上次数据）
            function fetchDispatch() {
                return requestEnvelope(apiUrl("/mq/demo/event/dispatch"))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            markDispatchFailed(env.msg);
                            return false;
                        }
                        applyDispatch(env.data);
                        return true;
                    })
                    .catch(function (err) {
                        markDispatchFailed(err && err.message);
                        return false;
                    });
            }

            // 命中表的展示映射：字段名沿用后端 rows[]，只做「unknown → 文案」与「null → —」的显示转换
            function e2TypeText(type) {
                var t = textOf(type);
                return t === "unknown" ? "未知 type（兜底桶）" : (t || "—");
            }

            function e2LastText(row) {
                var bizId = textOf(row && row.lastBizId);
                var at = textOf(row && row.lastAt);
                return bizId || at ? (bizId || "—") + " · " + (at || "—") : "—";
            }

            function e2Fail(head) {
                e2Receipt.show = true;
                e2Receipt.cls = "is-fail";
                e2Receipt.head = head;
                e2Receipt.detail = "";
                e2Receipt.extra = "";
                e2Receipt.note = "";
            }

            // 发送 E2 事件：成功后只刷新分发统计（不动 sentCount / receivedCount）
            function sendEvent() {
                if (e2Locked.value || e2SendBusy.value || e2ClearBusy.value) {
                    return;
                }
                var domain = textOf(e2.domain).trim();
                var type = textOf(e2.type).trim();
                var bizId = textOf(e2.bizId).trim();
                // 前置校验：空白参数后端判 10001，这里先拦下，避免把「空串 → 缺省」误当合法输入
                var invalid = domain === "" ? "domain 不能为空"
                    : (type === "" ? "type 不能为空" : (bizId === "" ? "bizId 不能为空" : ""));
                if (invalid) {
                    e2Fail("code=10001 " + invalid);
                    setLastError("发送事件失败：" + invalid);
                    return;
                }
                e2SendBusy.value = true;
                requestEnvelope(apiUrl("/mq/demo/event/send", { domain: domain, type: type, bizId: bizId }))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            e2Fail("code=" + env.code + " " + (textOf(env.msg) || "发送事件失败"));
                            setLastError("发送事件失败：" + (textOf(env.msg) || "code=" + env.code));
                            return;
                        }
                        var d = env.data || {};
                        e2Receipt.show = true;
                        e2Receipt.cls = "is-ok";
                        e2Receipt.head = "code=0 " + (textOf(env.msg) || ("已发送事件 " + type + " 到 " + domain));
                        e2Receipt.detail = "delivered=" + num(d.delivered) + "  hitCount=" + num(d.hitCount)
                            + "  totalReceived=" + num(d.totalReceived) + "  messageId=" + num(d.messageId);
                        e2Receipt.extra = "payload=" + textOf(d.payload);
                        e2Receipt.note = Number(d.delivered) === 0
                            ? "该实例没有 E2 监听器接到本次事件（装配期订阅只覆盖就绪实例），命中表不会增加" : "";
                        e2.sentPayload = textOf(d.payload);
                        return fetchDispatch();
                    })
                    .catch(function (err) {
                        e2Fail("发送事件失败：" + (err && err.message ? err.message : "请求失败"));
                        setLastError(e2Receipt.head);
                    })
                    .then(function () {
                        e2SendBusy.value = false;
                    });
            }

            // 清空命中统计：二次确认文案含实例名
            function clearDispatch() {
                if (e2Locked.value || e2SendBusy.value || e2ClearBusy.value) {
                    return;
                }
                if (!window.confirm("将清空 " + view.instance
                        + " 的 E2 事件分发命中统计（所有浏览器会话都受影响），装配期订阅与消息记录不受影响。确定？")) {
                    return;
                }
                e2ClearBusy.value = true;
                requestEnvelope(apiUrl("/mq/demo/event/dispatch/clear"))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            e2Fail("code=" + env.code + " " + (textOf(env.msg) || "清空分发统计失败"));
                            setLastError("清空分发统计失败：" + (textOf(env.msg) || "code=" + env.code));
                            return;
                        }
                        applyDispatch(env.data);   // clear 端点回传全 0 统计，等价于一次刷新
                        e2Receipt.show = true;
                        e2Receipt.cls = "is-ok";
                        e2Receipt.head = "code=0 " + (textOf(env.msg) || "命中统计已清空");
                        e2Receipt.detail = "totalReceived=0  unknownCount=0";
                        e2Receipt.extra = "";
                        e2Receipt.note = "装配期订阅（不可取消）与消息记录均未受影响";
                    })
                    .catch(function (err) {
                        e2Fail("清空分发统计失败：" + (err && err.message ? err.message : "请求失败"));
                        setLastError(e2Receipt.head);
                    })
                    .then(function () {
                        e2ClearBusy.value = false;
                    });
            }

            // 用户改动下拉 / 输入即丢掉「上次发送的 payload」，预览回到本地实时形状
            function onE2Edit() {
                e2.sentPayload = "";
            }

            // 换域联动：类型不在新域清单内则重置为新域首个。
            // 域值取自事件目标：v-model（select 指令）的 change 监听在本处理器之后执行，读 e2.domain 会拿到旧值。
            function onE2DomainChange(e) {
                onE2Edit();
                var domain = e && e.target ? textOf(e.target.value) : textOf(e2.domain);
                var types = typeListOf(domain);
                if (types.indexOf(e2.type) < 0) {
                    e2.type = types.length ? types[0] : "";
                }
            }

            function clearPoll() {
                if (pollTimer) {
                    clearTimeout(pollTimer);
                    pollTimer = null;
                }
            }

            function scheduleNext() {
                clearPoll();
                if (paused.value || document.hidden) {
                    return;
                }
                pollTimer = setTimeout(function () {
                    pollTimer = null;
                    pollOnce();
                }, pollInterval);
            }

            function pollOnce() {
                return Promise.all([fetchState(), fetchMessages(), fetchDispatch()]).then(function (results) {
                    var failed = results.some(function (r) {
                        return r === false;
                    });
                    if (failed) {
                        pollFailCount++;
                        if (pollFailCount >= SLOW_AFTER_FAILS) {
                            pollInterval = POLL_SLOW;
                            pollSlow.value = true;
                        }
                    } else {
                        pollFailCount = 0;
                        pollInterval = POLL_NORMAL;
                        pollSlow.value = false;
                    }
                    firstLoading.value = false;
                    scheduleNext();
                });
            }

            function togglePause() {
                paused.value = !paused.value;
                if (paused.value) {
                    clearPoll();
                    return;
                }
                pollOnce();   // 继续时立即补拉一次
            }

            // ================= 订阅 / 取消订阅 =================
            function subscribe() {
                if (locked.value || subBusy.value || unsubBusy.value) {
                    return;
                }
                var topic = subTopic.value.trim();
                subBusy.value = true;
                subMsg.value = "";
                requestEnvelope(apiUrl("/mq/demo/subscribe", { topic: topic }))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            subMsgOk.value = false;
                            subMsg.value = "订阅失败：" + (env.msg ? String(env.msg) : "code=" + env.code);
                            setLastError(subMsg.value);
                            return;
                        }
                        var d = env.data || {};
                        applySubscriptions(d);
                        subMsgOk.value = true;
                        subMsg.value = d.alreadySubscribed === true
                            ? "该主题已订阅（本页），未重复登记"
                            : "已订阅 " + topic + "（影响该实例的所有浏览器会话）";
                        // 订阅后立刻刷一次状态与消息（新主题的后续消息才会出现在记录里）
                        fetchState();
                    })
                    .catch(function (err) {
                        subMsgOk.value = false;
                        subMsg.value = "订阅失败：" + (err && err.message ? err.message : "请求失败");
                        setLastError(subMsg.value);
                    })
                    .then(function () {
                        subBusy.value = false;
                    });
            }

            function unsubscribe(topicName) {
                var topic = String(topicName === null || topicName === undefined ? "" : topicName).trim();
                if (!topic || locked.value || unsubBusy.value || subBusy.value) {
                    return;
                }
                unsubBusy.value = true;
                subMsg.value = "";
                requestEnvelope(apiUrl("/mq/demo/unsubscribe", { topic: topic }))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            subMsgOk.value = false;
                            subMsg.value = "取消订阅失败：" + (env.msg ? String(env.msg) : "code=" + env.code);
                            setLastError(subMsg.value);
                            return;
                        }
                        applySubscriptions(env.data || {});
                        subMsgOk.value = true;
                        subMsg.value = env.msg ? String(env.msg) : ("已取消订阅 " + topic + "（影响该实例的所有浏览器会话）");
                        fetchState();
                    })
                    .catch(function (err) {
                        subMsgOk.value = false;
                        subMsg.value = "取消订阅失败：" + (err && err.message ? err.message : "请求失败");
                        setLastError(subMsg.value);
                    })
                    .then(function () {
                        unsubBusy.value = false;
                    });
            }

            function unsubscribeByInput() {
                unsubscribe(subTopic.value);
            }

            // ================= 发送 =================
            function send() {
                if (locked.value || sendBusy.value) {
                    return;
                }
                var topic = pubTopic.value.trim();
                var text = pubText.value;
                var path = sendMode.value === "message" ? "/mq/demo/send/message" : "/mq/demo/send";
                // 前置校验（未发请求即报错）：后端 InParser 把空串折叠为 null，@Para(defaultValue=...)
                // 随即生效，空 topic / 空 text 会被静默改成默认主题 / 默认消息体并返回 code=0。
                var invalidMsg = "";
                if (topic === "") {
                    invalidMsg = "topic 不能为空";
                } else if (String(text === null || text === undefined ? "" : text).trim() === "") {
                    invalidMsg = "text 不能为空";
                }
                if (invalidMsg) {
                    receipt.show = true;
                    receipt.cls = "is-fail";
                    receipt.head = "code=10001 发送失败：" + invalidMsg;
                    receipt.detail = "";
                    receipt.extra = "";
                    receipt.note = "";
                    setLastError("发送失败：" + invalidMsg);
                    return;
                }
                sendBusy.value = true;
                requestEnvelope(apiUrl(path, { topic: topic, text: text }))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            receipt.show = true;
                            receipt.cls = "is-fail";
                            receipt.head = "code=" + env.code + " " + (env.msg ? String(env.msg) : "发送失败");
                            receipt.detail = "";
                            receipt.extra = "";
                            receipt.note = "";
                            setLastError("发送失败：" + (env.msg ? String(env.msg) : "code=" + env.code));
                            return;
                        }
                        var d = env.data || {};
                        receipt.show = true;
                        receipt.cls = "is-ok";
                        receipt.head = "code=0 " + (env.msg ? String(env.msg) : ("已发送到主题 " + topic));
                        var parts = [];
                        if (d.messageId !== undefined && d.messageId !== null) {
                            parts.push("messageId=" + d.messageId);
                        }
                        if (d.delivered !== undefined && d.delivered !== null) {
                            parts.push("delivered=" + d.delivered);
                        }
                        if (d.receivedCount !== undefined && d.receivedCount !== null) {
                            parts.push("receivedCount=" + d.receivedCount);
                        }
                        receipt.detail = parts.join("  ");
                        receipt.extra = d.payloadSize !== undefined && d.payloadSize !== null
                            ? "payloadSize=" + d.payloadSize : "";
                        receipt.note = Number(d.delivered) === 0
                            ? "该主题当前无订阅者，消息已被丢弃（发送仍成功）" : "";
                        // 本页发送行：由本页记录，不参与后端轮询
                        prependSentRow(d);
                        // 同步投递：立刻拉一次记录，把接收行补上
                        return fetchMessages().then(function () {
                            return fetchState();
                        });
                    })
                    .catch(function (err) {
                        receipt.show = true;
                        receipt.cls = "is-fail";
                        receipt.head = "发送失败：" + (err && err.message ? err.message : "请求失败");
                        receipt.detail = "";
                        receipt.extra = "";
                        receipt.note = "";
                        setLastError(receipt.head);
                    })
                    .then(function () {
                        sendBusy.value = false;
                    });
            }

            function onTextKeydown(e) {
                if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    send();
                }
            }

            function useDefaultTopic() {
                pubTopic.value = DEFAULT_TOPIC;
            }

            function clearPubInput() {
                pubText.value = "";
            }

            // ================= 清空记录 =================
            function clearMessages() {
                if (locked.value || clearBusy.value) {
                    return;
                }
                var ok = window.confirm("将清空 " + view.instance
                    + " 的消息记录与发送/接收计数（所有浏览器会话都受影响），订阅不受影响。确定？");
                if (!ok) {
                    return;
                }
                clearBusy.value = true;
                requestEnvelope(apiUrl("/mq/demo/messages/clear"))
                    .then(function (env) {
                        if (Number(env.code) !== 0) {
                            var failMsg = "清空失败：" + (env.msg ? String(env.msg) : "code=" + env.code);
                            flashLog(failMsg);
                            setLastError(failMsg);
                            return;
                        }
                        var d = env.data || {};
                        msgList.value = [];
                        sentRows.value = [];
                        msgError.value = "";
                        view.messageLogSize = num(d.messageLogSize);
                        view.sentCount = num(d.sentCount);
                        view.receivedCount = num(d.receivedCount);
                        flashLog("已清空 " + view.instance + " 的记录与计数（所有浏览器会话都受影响），订阅保留");
                        return fetchState();
                    })
                    .catch(function (err) {
                        var failMsg = "清空失败：" + (err && err.message ? err.message : "请求失败");
                        flashLog(failMsg);
                        setLastError(failMsg);
                    })
                    .then(function () {
                        clearBusy.value = false;
                    });
            }

            // ================= 复制 =================
            function entriesToText() {
                return entries.value.map(function (e) {
                    var meta = e.meta ? "  " + e.meta : "";
                    return "[" + e.receivedAt + "] " + (e.dir === "send" ? "发" : "收") + " " + e.topic + "  " + e.text + meta;
                }).join("\n");
            }

            function flashCopy(label) {
                copyLabel.value = label;
                if (copyTimer) {
                    clearTimeout(copyTimer);
                }
                copyTimer = setTimeout(function () {
                    copyTimer = null;
                    copyLabel.value = "复制";
                }, 1500);
            }

            function copyLogs() {
                if (entries.value.length === 0) {
                    flashCopy("无记录");
                    return;
                }
                writeClipboard(entriesToText());
            }

            function writeClipboard(text) {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(function () {
                        flashCopy("已复制");
                    }, function () {
                        fallbackCopy(text);
                    });
                    return;
                }
                fallbackCopy(text);
            }

            function fallbackCopy(text) {
                var ta = document.createElement("textarea");
                ta.value = text;
                ta.setAttribute("readonly", "");
                ta.style.position = "fixed";
                ta.style.top = "-1000px";
                document.body.appendChild(ta);
                ta.select();
                var ok = false;
                try {
                    ok = document.execCommand("copy");
                } catch (e) {
                    ok = false;
                }
                document.body.removeChild(ta);
                flashCopy(ok ? "已复制" : "复制失败");
            }

            // ================= 跳转 / 抽屉 =================
            function back() {
                window.location.href = "/";
            }

            function scrollToSubs() {
                var el = subCard.value;
                if (el && el.scrollIntoView) {
                    el.scrollIntoView({ behavior: "smooth", block: "start" });
                }
                var input = document.getElementById("sub-topic");
                if (input && !locked.value) {
                    try {
                        input.focus({ preventScroll: true });
                    } catch (e) {
                        input.focus();
                    }
                }
            }

            function openDrawer() {
                drawerOpen.value = true;
            }

            function closeDrawer() {
                drawerOpen.value = false;
            }

            function onGlobalKeydown(e) {
                if (e.key === "Escape" && drawerOpen.value) {
                    closeDrawer();
                }
            }

            Vue.watch(drawerOpen, function (open) {
                document.body.style.overflow = open ? "hidden" : "";
                Vue.nextTick(function () {
                    if (open && drawerRef.value) {
                        drawerRef.value.focus();
                    }
                });
            });

            // ================= 计算属性 =================
            var locked = Vue.computed(function () {
                return fatal.value;
            });

            var statusInfo = Vue.computed(function () {
                if (phase.value === "ready") {
                    return { text: "● 就绪", cls: "st-open" };
                }
                if (phase.value === "failed") {
                    return { text: "● 未装配", cls: "st-fail" };
                }
                return { text: "○ 读取中", cls: "st-pending" };
            });

            var stateErrorText = Vue.computed(function () {
                if (!stateErrorDetail.value) {
                    return "";
                }
                if (fatal.value) {
                    var detail = stateErrorDetail.value;
                    var idx = detail.indexOf("可用实例");
                    var avail = idx >= 0 ? detail.substring(idx).replace(/^[，,\s]+/, "") : detail;
                    return "未知实例：" + view.instance + "（" + avail + "）";
                }
                return "读取状态失败：" + stateErrorDetail.value;
            });

            var msgErrorText = Vue.computed(function () {
                if (!msgError.value) {
                    return "";
                }
                return "读取消息记录失败：" + msgError.value
                    + (pollSlow.value ? "（已降频至 5 秒重试）" : "（每 2 秒自动重试）");
            });

            var subMsgCls = Vue.computed(function () {
                return subMsgOk.value ? "is-ok" : "is-fail";
            });

            var canSubscribe = Vue.computed(function () {
                return !locked.value && !subBusy.value && !unsubBusy.value;
            });

            var canUnsubscribe = Vue.computed(function () {
                return !locked.value && !subBusy.value && !unsubBusy.value
                    && String(subTopic.value).trim() !== "";
            });

            var canSend = Vue.computed(function () {
                return !locked.value && !sendBusy.value;
            });

            // E2：加载中（首屏骨架）与整页降级（10001 未知实例）时控件一并 disabled
            var e2Locked = Vue.computed(function () {
                return locked.value || e2Loading.value;
            });

            var e2TypeOptions = Vue.computed(function () {
                return typeListOf(e2.domain);
            });

            // 预览：发送成功后展示后端回传的 payload；用户一改动就回到本地实时形状（同形，不参与请求）
            var e2Preview = Vue.computed(function () {
                if (e2.sentPayload) {
                    return e2.sentPayload;
                }
                return "{\"type\":" + JSON.stringify(textOf(e2.type))
                    + ",\"version\":1,\"bizId\":" + JSON.stringify(textOf(e2.bizId)) + "}";
            });

            var e2Empty = Vue.computed(function () {
                return !e2Loading.value && !e2Error.value && countOf(dispatch.totalReceived) === 0;
            });

            var chips = Vue.computed(function () {
                var topics = subscribedTopics.value || [];
                var sourcesByTopic = {};
                (subscribers.value || []).forEach(function (s) {
                    if (!s || !s.topic) {
                        return;
                    }
                    var key = String(s.topic);
                    var src = s.source === "startup" ? "startup" : (s.source === "page" ? "page" : String(s.source || ""));
                    if (!sourcesByTopic[key]) {
                        sourcesByTopic[key] = [];
                    }
                    if (src && sourcesByTopic[key].indexOf(src) < 0) {
                        sourcesByTopic[key].push(src);
                    }
                });
                function orderSrc(list) {
                    return list.slice().sort(function (a, b) {
                        if (a === "startup") {
                            return -1;
                        }
                        if (b === "startup") {
                            return 1;
                        }
                        return 0;
                    });
                }
                var out = [];
                topics.forEach(function (t) {
                    var key = String(t);
                    var sources = orderSrc(sourcesByTopic[key] || []);
                    out.push({ topic: key, sources: sources, canRemove: sources.indexOf("page") >= 0 });
                });
                // 兜底：订阅者里出现但 subscribedTopics 未包含的主题也不丢
                Object.keys(sourcesByTopic).forEach(function (key) {
                    var exists = false;
                    for (var k = 0; k < out.length; k++) {
                        if (out[k].topic === key) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        var srcs = orderSrc(sourcesByTopic[key]);
                        out.push({ topic: key, sources: srcs, canRemove: srcs.indexOf("page") >= 0 });
                    }
                });
                return out;
            });

            // 重复记录提示：当前输入的主题已由装配期监听器订阅
            var dupWarning = Vue.computed(function () {
                var topic = String(subTopic.value).trim();
                if (!topic) {
                    return false;
                }
                for (var i = 0; i < chips.value.length; i++) {
                    if (chips.value[i].topic === topic
                        && chips.value[i].sources.indexOf("startup") >= 0
                        && chips.value[i].sources.indexOf("page") < 0) {
                        return true;
                    }
                }
                return false;
            });

            function sourceLabel(source) {
                if (source === "startup") {
                    return "装配期";
                }
                if (source === "page") {
                    return "本页";
                }
                return String(source);
            }

            // ================= 可见性 =================
            function onVisibilityChange() {
                if (document.hidden) {
                    clearPoll();
                    return;
                }
                if (!paused.value) {
                    pollOnce();
                }
            }

            // ================= 生命周期 =================
            Vue.onMounted(function () {
                document.title = "MQ 演示 · " + INSTANCE;
                document.addEventListener("keydown", onGlobalKeydown);
                document.addEventListener("visibilitychange", onVisibilityChange);
                pollOnce();
            });

            Vue.onUnmounted(function () {
                document.removeEventListener("keydown", onGlobalKeydown);
                document.removeEventListener("visibilitychange", onVisibilityChange);
                clearPoll();
                if (copyTimer) {
                    clearTimeout(copyTimer);
                    copyTimer = null;
                }
                if (noticeTimer) {
                    clearTimeout(noticeTimer);
                    noticeTimer = null;
                }
                document.body.style.overflow = "";
            });

            return {
                // 状态
                view: view,
                statusInfo: statusInfo,
                stateErrorText: stateErrorText,
                lastError: lastError,
                locked: locked,
                // 订阅
                subTopic: subTopic,
                subBusy: subBusy,
                unsubBusy: unsubBusy,
                subMsg: subMsg,
                subMsgCls: subMsgCls,
                canSubscribe: canSubscribe,
                canUnsubscribe: canUnsubscribe,
                chips: chips,
                dupWarning: dupWarning,
                startupChipTitle: STARTUP_CHIP_TITLE,
                sourceLabel: sourceLabel,
                subscribe: subscribe,
                unsubscribe: unsubscribe,
                unsubscribeByInput: unsubscribeByInput,
                // 发布
                pubTopic: pubTopic,
                pubText: pubText,
                sendMode: sendMode,
                sendBusy: sendBusy,
                canSend: canSend,
                receipt: receipt,
                send: send,
                onTextKeydown: onTextKeydown,
                useDefaultTopic: useDefaultTopic,
                clearPubInput: clearPubInput,
                // E2 事件分发
                e2: e2,
                dispatch: dispatch,
                e2Loading: e2Loading,
                e2Error: e2Error,
                e2Receipt: e2Receipt,
                e2SendBusy: e2SendBusy,
                e2ClearBusy: e2ClearBusy,
                e2Locked: e2Locked,
                e2TypeOptions: e2TypeOptions,
                e2Preview: e2Preview,
                e2Empty: e2Empty,
                e2TypeText: e2TypeText,
                e2LastText: e2LastText,
                sendEvent: sendEvent,
                clearDispatch: clearDispatch,
                onE2Edit: onE2Edit,
                onE2DomainChange: onE2DomainChange,
                // 消息流
                entries: entries,
                total: total,
                firstLoading: firstLoading,
                msgErrorText: msgErrorText,
                paused: paused,
                autoScroll: autoScroll,
                copyLabel: copyLabel,
                logNotice: logNotice,
                clearBusy: clearBusy,
                logBody: logBody,
                togglePause: togglePause,
                copyLogs: copyLogs,
                clearMessages: clearMessages,
                // 其它
                subCard: subCard,
                drawerOpen: drawerOpen,
                drawerRef: drawerRef,
                openDrawer: openDrawer,
                closeDrawer: closeDrawer,
                scrollToSubs: scrollToSubs,
                back: back
            };
        }
    });

    app.mount("#mq-demo-app");
})();
