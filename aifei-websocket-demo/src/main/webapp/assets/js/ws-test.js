/* ==========================================================================
 * WebSocket 测试页交互脚本
 * 档案 A（Aifei 静态层）：无构建工具，使用本地全局构建版 Vue（/assets/lib/vue.global.prod.js）。
 * 页面只读后端一个端点 GET /ws/demo/state（轮询）；本页自身的收发走浏览器原生 WebSocket。
 * ========================================================================== */

(function () {
    "use strict";

    var Vue = window.Vue;

    // ---- 常量 ----
    var DEFAULT_URL = "ws://127.0.0.1:18091/ws";
    var MAX_LOGS = 200;
    var BACKOFF = [500, 1000, 2000, 5000];   // 退避序列，5000 为上限
    var MAX_RETRY = 10;                       // 单轮非主动断开的自动重连上限
    var POLL_NORMAL = 2000;
    var POLL_SLOW = 5000;                     // 连续 3 次失败后降频
    var DEMO_STATE_URL = "/ws/demo/state";
    var MAX_HISTORY = 50;                      // ↑↓ 历史条数上限
    var DEFAULT_HEARTBEAT_SECONDS = 10;
    var DEFAULT_HEARTBEAT_TEXT = "ping";

    // ---- 时间格式化 ----
    function pad(n, len) {
        var s = String(n);
        while (s.length < len) {
            s = "0" + s;
        }
        return s;
    }

    function nowTime() {
        var d = new Date();
        return pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2) + ":" + pad(d.getSeconds(), 2)
            + "." + pad(d.getMilliseconds(), 3);
    }

    function nowDateTime() {
        var d = new Date();
        return d.getFullYear() + "-" + pad(d.getMonth() + 1, 2) + "-" + pad(d.getDate(), 2)
            + " " + pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2);
    }

    function dirLabel(kind) {
        if (kind === "send") {
            return "▲ 发送";
        }
        if (kind === "recv") {
            return "▼ 接收";
        }
        return "● 系统";
    }

    function bytesToHex(bytes) {
        var parts = [];
        for (var i = 0; i < bytes.length; i++) {
            parts.push(pad(bytes[i].toString(16).toUpperCase(), 2));
        }
        return parts.join(" ");
    }

    function modeLabel(m) {
        switch (m) {
            case "json":
                return "JSON";
            case "hex":
                return "HEX";
            case "base64":
                return "Base64";
            default:
                return "文本";
        }
    }

    var app = Vue.createApp({
        setup: function () {
            // ---- 本页连接状态 ----
            var url = Vue.ref(DEFAULT_URL);
            var phase = Vue.ref("UNCONNECTED");   // UNCONNECTED / CONNECTING / OPEN / CLOSED / RECONNECTING
            var closedReason = Vue.ref("");        // "manual" | "other"
            var autoReconnect = Vue.ref(true);
            var sendCount = Vue.ref(0);
            var recvCount = Vue.ref(0);
            var reconnectCount = Vue.ref(0);
            var lastError = Vue.reactive({ text: "", time: "" });
            var logs = Vue.ref([]);
            var autoScroll = Vue.ref(true);
            var draft = Vue.ref("");
            var drawerOpen = Vue.ref(false);
            var reconnectInfo = Vue.reactive({ attempt: 0, delay: 0 });
            var mode = Vue.ref("text");            // text | json | hex | base64
            var beautifyJson = Vue.ref(false);
            var copyLabel = Vue.ref("复制日志");
            var heartbeatRunning = Vue.ref(false);
            var heartbeatSeconds = Vue.ref(DEFAULT_HEARTBEAT_SECONDS);
            var heartbeatText = Vue.ref(DEFAULT_HEARTBEAT_TEXT);

            // ---- demo 侧（只轮询 /ws/demo/state） ----
            var demo = Vue.reactive({
                clientState: "—",
                serverStarted: "—",
                serverPort: "—",
                serverSessionCount: "—",
                reachable: true,
                errorTitle: ""
            });

            // ---- 模板引用 ----
            var logBody = Vue.ref(null);
            var drawerRef = Vue.ref(null);
            var drawerTrigger = Vue.ref(null);

            // ---- 非响应式内部句柄 ----
            var ws = null;                 // 当前浏览器 WebSocket
            var reconnectTimer = null;
            var pollTimer = null;
            var pollInterval = POLL_NORMAL;
            var pollFailCount = 0;
            var manualClose = false;       // 主动关闭标志：置真后 onclose 不重连
            var isReconnectAttempt = false;
            var retryAttempt = 0;          // 本轮（非主动断开）已尝试的重连次数
            var history = [];              // 已成功发送的历史（最新在前，上限 MAX_HISTORY）
            var historyIndex = -1;         // 当前浏览位置，-1 = 未进入历史（草稿态）
            var draftBeforeHistory = "";   // 进入历史前的草稿，回到最新时恢复
            var heartbeatTimer = null;
            var copyTimer = null;

            // ================= 日志 =================
            function appendLog(kind, text) {
                var arr = logs.value;
                arr.push({ time: nowTime(), kind: kind, text: text });
                if (arr.length > MAX_LOGS) {
                    arr.splice(0, arr.length - MAX_LOGS);
                }
                if (autoScroll.value) {
                    scrollToBottom();
                }
            }

            function scrollToBottom() {
                Vue.nextTick(function () {
                    var el = logBody.value;
                    if (el) {
                        el.scrollTop = el.scrollHeight;
                    }
                });
            }

            function clearLogs() {
                logs.value = [];
            }

            // 日志展示文本：JSON 美化开启且可解析时缩进输出，否则原样
            function displayText(l) {
                if (!beautifyJson.value) {
                    return l.text;
                }
                try {
                    return JSON.stringify(JSON.parse(l.text), null, 2);
                } catch (e) {
                    return l.text;
                }
            }

            function logToText() {
                return logs.value.map(function (l) {
                    return "[" + l.time + "] " + dirLabel(l.kind) + " " + displayText(l);
                }).join("\n");
            }

            function copyLogs() {
                if (logs.value.length === 0) {
                    flashCopy("无日志");
                    return;
                }
                writeClipboard(logToText());
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

            // 复制结果通过按钮文案短暂反馈，不写日志（避免污染被测日志）
            function flashCopy(label) {
                copyLabel.value = label;
                if (copyTimer) {
                    clearTimeout(copyTimer);
                }
                copyTimer = setTimeout(function () {
                    copyTimer = null;
                    copyLabel.value = "复制日志";
                }, 1500);
            }

            function setError(text) {
                lastError.text = text;
                lastError.time = nowDateTime();
            }

            // ================= 本页 WebSocket =================
            function clearReconnectTimer() {
                if (reconnectTimer) {
                    clearTimeout(reconnectTimer);
                    reconnectTimer = null;
                }
            }

            function teardownSocket() {
                if (ws) {
                    // 先摘回调，避免旧连接事件污染新状态
                    ws.onopen = null;
                    ws.onmessage = null;
                    ws.onerror = null;
                    ws.onclose = null;
                    ws = null;
                }
            }

            function createSocket(isReconnect) {
                isReconnectAttempt = !!isReconnect;
                teardownSocket();
                appendLog("sys", (isReconnect ? "正在重连 " : "正在连接 ") + url.value);

                var socket;
                try {
                    socket = new WebSocket(url.value);
                } catch (e) {
                    handleError();
                    return;
                }
                ws = socket;
                socket.binaryType = "arraybuffer";   // 二进制帧以 ArrayBuffer 交付，便于统一转十六进制
                phase.value = "CONNECTING";
                socket.onopen = handleOpen;
                socket.onmessage = handleMessage;
                socket.onerror = handleError;
                socket.onclose = handleClose;
            }

            function handleOpen() {
                phase.value = "OPEN";
                closedReason.value = "";
                if (isReconnectAttempt) {
                    appendLog("sys", "重连成功，已恢复 OPEN（第 " + retryAttempt + " 次）");
                } else {
                    appendLog("sys", "已连接 " + url.value);
                }
                isReconnectAttempt = false;
                retryAttempt = 0;
                reconnectInfo.attempt = 0;
                reconnectInfo.delay = 0;
            }

            function handleMessage(ev) {
                recvCount.value++;
                if (typeof ev.data === "string") {
                    appendLog("recv", ev.data);
                } else if (ev.data instanceof ArrayBuffer) {
                    appendLog("recv", bytesToHex(new Uint8Array(ev.data)));   // 二进制统一以十六进制展示
                } else {
                    appendLog("recv", String(ev.data));
                }
            }

            function handleError() {
                if (manualClose) {
                    return;
                }
                var msg = "连接失败：无法连接到 " + url.value + "，请确认 demo 已启动且 WS 服务端监听 18091";
                setError(msg);
                appendLog("sys", msg);
            }

            function handleClose(ev) {
                ws = null;

                // 任何断开（含自动重连中）都自动停止心跳并复位按钮
                if (heartbeatRunning.value) {
                    stopHeartbeat("连接已断开，心跳已自动停止");
                }

                // 用户主动 close()：永不自动重连（硬契约①）；关闭码与日志文案严格对应
                if (manualClose) {
                    manualClose = false;
                    phase.value = "CLOSED";
                    closedReason.value = "manual";
                    appendLog("sys", "已断开：code=1000 reason=主动关闭 —— 不会自动重连");
                    return;
                }

                var code = ev && ev.code;
                var reason = (ev && ev.reason) || "（无）";

                // 非主动断开：勾选自动重连时按退避重试
                if (autoReconnect.value) {
                    if (retryAttempt < MAX_RETRY) {
                        scheduleReconnect(code, reason);
                        return;
                    }
                    phase.value = "CLOSED";
                    closedReason.value = "other";
                    appendLog("sys", "重连次数已用尽（" + MAX_RETRY + " 次），已停止自动重连；可手动点「连接」重试");
                    return;
                }

                phase.value = "CLOSED";
                closedReason.value = "other";
                appendLog("sys", "已断开：code=" + code + " reason=" + reason);
            }

            function scheduleReconnect(code, reason) {
                retryAttempt++;
                reconnectCount.value++;
                var delay = BACKOFF[Math.min(retryAttempt - 1, BACKOFF.length - 1)];

                phase.value = "RECONNECTING";
                reconnectInfo.attempt = retryAttempt;
                reconnectInfo.delay = delay;

                appendLog("sys", "已断开：code=" + code + " reason=" + reason
                    + " —— 非主动断开，将在 " + delay + "ms 后重连（第 " + retryAttempt + " 次）");

                clearReconnectTimer();
                reconnectTimer = setTimeout(function () {
                    reconnectTimer = null;
                    if (manualClose) {
                        return;
                    }
                    createSocket(true);
                }, delay);
            }

            function connect() {
                if (phase.value === "CONNECTING" || phase.value === "OPEN" || phase.value === "RECONNECTING") {
                    return;
                }

                var target = url.value ? url.value.trim() : "";
                if (!target) {
                    setError("请输入 WebSocket 地址");
                    appendLog("sys", "请输入 WebSocket 地址");
                    return;
                }
                // 仅支持 ws://，不支持 wss://（硬契约③）
                if (target.indexOf("wss://") === 0) {
                    setError("demo 服务端仅支持 ws://，不支持 wss://");
                    appendLog("sys", "地址无效：仅支持 ws://，不支持 wss://");
                    return;
                }
                if (target.indexOf("ws://") !== 0) {
                    setError("地址格式无效，需以 ws:// 开头");
                    appendLog("sys", "地址格式无效，需以 ws:// 开头");
                    return;
                }

                url.value = target;
                manualClose = false;
                retryAttempt = 0;      // 手动「连接」不计入重连次数
                closedReason.value = "";
                clearReconnectTimer();
                createSocket(false);
            }

            function disconnect() {
                // 主动 close 置 manualClose，禁止重连；同时取消待执行的重连定时器
                manualClose = true;
                clearReconnectTimer();

                var socket = ws;
                if (socket && (socket.readyState === WebSocket.CONNECTING || socket.readyState === WebSocket.OPEN)) {
                    socket.close(1000, "主动关闭");
                    // 立即切态，避免 CLOSING 期间按钮态抖动（CLOSING 归入「已断开（主动）」）
                    phase.value = "CLOSED";
                    closedReason.value = "manual";
                    return;
                }

                // 无活动连接（如重连等待中）：仅取消重连并置为已断开
                teardownSocket();
                phase.value = "CLOSED";
                closedReason.value = "manual";
                appendLog("sys", "已断开：code=1000 reason=主动关闭 —— 不会自动重连");
            }

            function send() {
                var text = draft.value;
                if (!text) {
                    setError("请输入要发送的内容");
                    return;
                }

                var socket = ws;
                // 发送不排队：非 OPEN 直接丢弃，不缓存、不补发（硬契约②）
                if (!socket || socket.readyState !== WebSocket.OPEN || phase.value !== "OPEN") {
                    var msg = "发送失败：连接已断开，消息已丢弃（不排队）";
                    setError(msg);
                    appendLog("sys", msg);
                    return;
                }

                // 按模式编码；编码失败则不发、记错误与系统日志，且不记入历史
                var payload;
                try {
                    payload = encodePayload(text);
                } catch (e) {
                    var em = "发送失败（" + modeLabel(mode.value) + "）：" + e.message;
                    setError(em);
                    appendLog("sys", em);
                    return;
                }

                try {
                    socket.send(payload);
                } catch (e2) {
                    var failMsg = "发送失败：连接已断开，消息已丢弃（不排队）";
                    setError(failMsg);
                    appendLog("sys", failMsg);
                    return;
                }

                sendCount.value++;
                recordHistory(text);
                appendLog("send", text);
                draft.value = "";
                historyIndex = -1;
                draftBeforeHistory = "";
            }

            // 模式编码：text/json 返回字符串；hex/base64 返回 Uint8Array（二进制帧）
            function encodePayload(text) {
                if (mode.value === "json") {
                    try {
                        JSON.parse(text);
                    } catch (e) {
                        throw new Error("JSON 解析失败：" + e.message);
                    }
                    return text;   // 合法则原样发送，不压缩、不改写
                }
                if (mode.value === "hex") {
                    return parseHex(text);
                }
                if (mode.value === "base64") {
                    return parseBase64(text);
                }
                return text;
            }

            function parseHex(text) {
                // 允许 0x/0X 前缀，允许空格、逗号、分号、换行作分隔符
                var cleaned = text.replace(/0[xX]/g, "").replace(/[\s,;]+/g, "");
                if (cleaned === "") {
                    throw new Error("HEX 内容为空");
                }
                if (!/^[0-9a-fA-F]+$/.test(cleaned)) {
                    throw new Error("含非十六进制字符");
                }
                if (cleaned.length % 2 !== 0) {
                    throw new Error("十六进制长度必须为偶数（每 2 个字符为 1 字节）");
                }
                var bytes = new Uint8Array(cleaned.length / 2);
                for (var i = 0; i < bytes.length; i++) {
                    bytes[i] = parseInt(cleaned.substr(i * 2, 2), 16);
                }
                return bytes;
            }

            function parseBase64(text) {
                var bin;
                try {
                    bin = atob(text.trim());
                } catch (e) {
                    throw new Error("Base64 解码失败");
                }
                var bytes = new Uint8Array(bin.length);
                for (var i = 0; i < bin.length; i++) {
                    bytes[i] = bin.charCodeAt(i);
                }
                return bytes;
            }

            function recordHistory(text) {
                // 相邻重复不重复记录
                if (history.length > 0 && history[0] === text) {
                    return;
                }
                history.unshift(text);
                if (history.length > MAX_HISTORY) {
                    history.pop();
                }
            }

            // ↑ 往更早（索引增大）
            function historyPrev() {
                if (history.length === 0) {
                    return;
                }
                if (historyIndex === -1) {
                    draftBeforeHistory = draft.value;   // 首次进入历史，记住草稿
                }
                if (historyIndex < history.length - 1) {
                    historyIndex++;
                }
                draft.value = history[historyIndex];
            }

            // ↓ 往更晚；走到最新之后再 ↓ 恢复草稿
            function historyNext() {
                if (history.length === 0 || historyIndex === -1) {
                    return;
                }
                historyIndex--;
                draft.value = historyIndex === -1 ? draftBeforeHistory : history[historyIndex];
            }

            function onSendKeydown(e) {
                if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    send();
                    return;
                }
                if (e.key === "ArrowUp" && history.length > 0) {
                    e.preventDefault();   // 阻止光标默认移动
                    historyPrev();
                    return;
                }
                if (e.key === "ArrowDown" && history.length > 0) {
                    e.preventDefault();
                    historyNext();
                }
            }

            function setMode(m) {
                mode.value = m;
            }

            function formatJson() {
                var text = draft.value;
                if (!text) {
                    setError("请输入要格式化的 JSON");
                    return;
                }
                try {
                    draft.value = JSON.stringify(JSON.parse(text), null, 2);
                } catch (e) {
                    var msg = "JSON 格式化失败：" + e.message;
                    setError(msg);
                    appendLog("sys", msg);
                }
            }

            function clearInput() {
                draft.value = "";
                historyIndex = -1;
                draftBeforeHistory = "";
            }

            // ---- 应用级文本心跳（后端与浏览器均无协议级 ping 能力）----
            function clearHeartbeatTimer() {
                if (heartbeatTimer) {
                    clearTimeout(heartbeatTimer);
                    heartbeatTimer = null;
                }
            }

            function toggleHeartbeat() {
                if (heartbeatRunning.value) {
                    stopHeartbeat("已手动停止心跳");
                    return;
                }
                if (phase.value !== "OPEN") {
                    var msg = "开始心跳失败：连接未就绪（需已连接）";
                    setError(msg);
                    appendLog("sys", msg);
                    return;
                }
                heartbeatRunning.value = true;
                appendLog("sys", "已开始心跳（间隔 " + heartbeatSeconds.value + "s，内容：" + heartbeatText.value + "）");
                scheduleHeartbeat();
            }

            function scheduleHeartbeat() {
                clearHeartbeatTimer();
                var sec = Number(heartbeatSeconds.value);
                if (!(sec >= 1)) {
                    sec = 1;
                }
                // 递归 setTimeout：运行中改间隔或 ping 内容，下一拍即生效
                heartbeatTimer = setTimeout(function () {
                    heartbeatTimer = null;
                    sendHeartbeatTick();
                    if (heartbeatRunning.value) {
                        scheduleHeartbeat();
                    }
                }, sec * 1000);
            }

            function sendHeartbeatTick() {
                if (phase.value !== "OPEN" || !ws || ws.readyState !== WebSocket.OPEN) {
                    stopHeartbeat("连接已断开，心跳已自动停止");
                    return;
                }
                try {
                    ws.send(heartbeatText.value);   // 文本帧，不经模式编码
                } catch (e) {
                    stopHeartbeat("心跳发送失败，已自动停止");
                    return;
                }
                sendCount.value++;
                appendLog("send", heartbeatText.value);
            }

            function stopHeartbeat(reason) {
                heartbeatRunning.value = false;
                clearHeartbeatTimer();
                if (reason) {
                    appendLog("sys", reason);
                }
            }

            // ================= demo 状态轮询 =================
            function applyDemo(data) {
                data = data || {};
                demo.clientState = data.clientState != null ? String(data.clientState) : "—";
                demo.serverStarted = data.serverStarted != null ? String(data.serverStarted) : "—";
                demo.serverPort = data.serverPort != null ? String(data.serverPort) : "—";
                demo.serverSessionCount = data.serverSessionCount != null ? String(data.serverSessionCount) : "—";
                demo.reachable = true;
                demo.errorTitle = "";
                if (pollFailCount > 0) {
                    appendLog("sys", "demo 状态已恢复");
                }
                pollFailCount = 0;
                pollInterval = POLL_NORMAL;
            }

            function markDemoUnreachable(err) {
                demo.clientState = "—";
                demo.serverStarted = "—";
                demo.serverPort = "—";
                demo.serverSessionCount = "—";
                demo.reachable = false;
                demo.errorTitle = "demo 状态不可达（" + DEMO_STATE_URL + "）："
                    + (err && err.message ? err.message : "请求失败");
                // 首次失败记 1 条系统日志，连续失败不重复记录
                if (pollFailCount === 0) {
                    appendLog("sys", "demo 状态不可达（" + DEMO_STATE_URL + "），将每 2s 重试");
                }
                pollFailCount++;
                if (pollFailCount >= 3) {
                    pollInterval = POLL_SLOW;
                }
            }

            function pollState() {
                return fetch(DEMO_STATE_URL, { headers: { "Accept": "application/json" } })
                    .then(function (response) {
                        if (!response.ok) {
                            throw new Error("HTTP " + response.status);
                        }
                        return response.text();
                    })
                    .then(function (text) {
                        return text ? JSON.parse(text) : null;
                    })
                    .then(function (json) {
                        // 统一信封：code 非 0 视为失败
                        if (!json || Number(json.code) !== 0) {
                            throw new Error(json && json.msg ? json.msg : "demo 状态不可达");
                        }
                        applyDemo(json.data);
                    })
                    .catch(function (err) {
                        markDemoUnreachable(err);
                    });
            }

            function pollTick() {
                pollTimer = null;
                if (document.hidden) {
                    return;
                }
                pollState().then(function () {
                    if (document.hidden) {
                        return;
                    }
                    pollTimer = setTimeout(pollTick, pollInterval);
                });
            }

            function stopPolling() {
                if (pollTimer) {
                    clearTimeout(pollTimer);
                    pollTimer = null;
                }
            }

            function onVisibilityChange() {
                if (document.hidden) {
                    stopPolling();
                } else {
                    // 恢复可见立即补拉一次
                    stopPolling();
                    pollTick();
                }
            }

            // ================= 抽屉 =================
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
                if (open) {
                    document.body.style.overflow = "hidden";
                    Vue.nextTick(function () {
                        if (drawerRef.value) {
                            drawerRef.value.focus();
                        }
                    });
                } else {
                    document.body.style.overflow = "";
                    Vue.nextTick(function () {
                        if (drawerTrigger.value) {
                            drawerTrigger.value.focus();
                        }
                    });
                }
            });

            // ================= 计算属性 =================
            var stateInfo = Vue.computed(function () {
                switch (phase.value) {
                    case "CONNECTING":
                        return { text: "● 连接中…", cls: "st-pending" };
                    case "OPEN":
                        return { text: "● 已连接", cls: "st-open" };
                    case "RECONNECTING":
                        return {
                            text: "● 重连中（第 " + reconnectInfo.attempt + " 次，" + reconnectInfo.delay + " ms 后重试）",
                            cls: "st-pending"
                        };
                    case "CLOSED":
                        return {
                            text: closedReason.value === "manual" ? "● 已断开（主动关闭，不会自动重连）" : "● 已断开",
                            cls: "st-closed"
                        };
                    default:
                        return { text: "● 未连接", cls: "st-idle" };
                }
            });

            var urlDisabled = Vue.computed(function () {
                return phase.value !== "UNCONNECTED" && phase.value !== "CLOSED";
            });

            var canConnect = Vue.computed(function () {
                return phase.value === "UNCONNECTED" || phase.value === "CLOSED";
            });

            // 连接中 / 重连中也允许「断开」（= 放弃本次尝试并主动关闭，不再重连）
            var canDisconnect = Vue.computed(function () {
                return phase.value === "CONNECTING" || phase.value === "OPEN" || phase.value === "RECONNECTING";
            });

            var canSend = Vue.computed(function () {
                return phase.value === "OPEN";
            });

            var sendHint = Vue.computed(function () {
                switch (phase.value) {
                    case "CONNECTING":
                        return "正在连接，发送已禁用（消息不排队）";
                    case "OPEN":
                        return "已连接（Ctrl / ⌘ + Enter 发送）";
                    case "CLOSED":
                        return "已断开，发送已禁用（消息不排队，不会补发）";
                    case "RECONNECTING":
                        return "重连中，发送已禁用（消息不排队）";
                    default:
                        return "未连接，发送已禁用（消息不排队，不会补发）";
                }
            });

            // ================= 生命周期 =================
            Vue.onMounted(function () {
                document.addEventListener("visibilitychange", onVisibilityChange);
                document.addEventListener("keydown", onGlobalKeydown);
                pollTick();
            });

            Vue.onUnmounted(function () {
                document.removeEventListener("visibilitychange", onVisibilityChange);
                document.removeEventListener("keydown", onGlobalKeydown);
                stopPolling();
                clearReconnectTimer();
                clearHeartbeatTimer();
                if (copyTimer) {
                    clearTimeout(copyTimer);
                    copyTimer = null;
                }
                teardownSocket();
                document.body.style.overflow = "";
            });

            return {
                url: url,
                phase: phase,
                autoReconnect: autoReconnect,
                sendCount: sendCount,
                recvCount: recvCount,
                reconnectCount: reconnectCount,
                lastError: lastError,
                logs: logs,
                autoScroll: autoScroll,
                draft: draft,
                drawerOpen: drawerOpen,
                demo: demo,
                logBody: logBody,
                drawerRef: drawerRef,
                drawerTrigger: drawerTrigger,
                stateInfo: stateInfo,
                urlDisabled: urlDisabled,
                canConnect: canConnect,
                canDisconnect: canDisconnect,
                canSend: canSend,
                sendHint: sendHint,
                dirLabel: dirLabel,
                connect: connect,
                disconnect: disconnect,
                send: send,
                onSendKeydown: onSendKeydown,
                clearLogs: clearLogs,
                openDrawer: openDrawer,
                closeDrawer: closeDrawer,
                mode: mode,
                setMode: setMode,
                beautifyJson: beautifyJson,
                copyLabel: copyLabel,
                copyLogs: copyLogs,
                displayText: displayText,
                heartbeatRunning: heartbeatRunning,
                heartbeatSeconds: heartbeatSeconds,
                heartbeatText: heartbeatText,
                toggleHeartbeat: toggleHeartbeat,
                formatJson: formatJson,
                clearInput: clearInput
            };
        }
    });

    app.mount("#ws-app");
})();
