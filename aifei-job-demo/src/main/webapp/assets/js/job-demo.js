/* ==========================================================================
 * aifei-job-demo 演示页交互脚本（view/job/demo.html）
 * 档案 A（Aifei 静态层）：纯原生 JS + 本地全局构建版 Vue（/assets/lib/vue.global.prod.js），
 * 无构建工具、无外部依赖、零 CDN。
 *
 * 数据全部来自本进程运行中的 aifei-job 实例（全 GET，HTTP 恒 200，信封 { code, msg, data }，code=0 为成功）：
 * - /job/demo/names          JobKit.names()：已注册任务名
 * - /job/demo/state          JobKit.state()：任务名 → { phase, triggerTime, triggerAt, error }（只有最近终态）
 * - /job/demo/events         监听器环形缓冲（最新在前）：{ seq, taskName, phase, triggerTime, triggerAt, threadName, error }
 *                            —— 执行中 / 到点触发 / 跳过三类视图的唯一来源
 * - /job/demo/events/clear   清空事件流：{ cleared, remaining, capacity }
 * - /job/demo/tasks          任务清单：{ name, biz, mode, trigger, rhythm, registered, lastPhase, lastTriggerAt,
 *                            running, nextFireTime, nextFireAt }（触发方式与下次触发时间由 demo 自算，非模块能力）
 * - /job/demo/shutdown       停机三步 + 在途 / 跳过统计（只读，applying 恒 false，不真停应用）
 * ========================================================================== */

(function () {
    "use strict";

    var Vue = window.Vue;

    /**
     * 轮询间隔（毫秒）：页面可见时每 1 秒拉一轮，页面隐藏 / 卸载时暂停。
     */
    var POLL_INTERVAL_MS = 1000;

    /**
     * 单次请求超时（毫秒）：超时即视为本轮失败，走可见降级提示，不阻塞下一轮。
     */
    var REQUEST_TIMEOUT_MS = 4000;

    /**
     * 到点高亮持续时长（毫秒）：短时闪烁，不用 sleep 阻塞。
     */
    var FIRING_HIGHLIGHT_MS = 1200;

    /**
     * 单轮最多高亮的任务数：事件成对（STARTED + 终态）到达时避免整屏闪烁。
     */
    var HIGHLIGHT_BATCH = 8;

    /**
     * 事件缓冲容量：对齐后端 DemoEventLog.CAPACITY（端点未暴露该值，清空接口返回的 capacity 同值；
     * 首次清空后以返回值覆盖）。
     */
    var DEFAULT_EVENT_CAPACITY = 240;

    /**
     * 端点地址：与 JobDemoService 的 @Path 一一对应。
     */
    var ENDPOINTS = {
        names: "/job/demo/names",
        state: "/job/demo/state",
        events: "/job/demo/events",
        eventsClear: "/job/demo/events/clear",
        tasks: "/job/demo/tasks",
        shutdown: "/job/demo/shutdown"
    };

    /**
     * 唤醒层线程名前缀（模块 JobScheduler）：该线程记的 SKIPPED 是「提交被拒」。
     */
    var WAKE_THREAD_PREFIX = "aifei-job-wake";

    /**
     * 执行层线程名前缀（模块 JobScheduler）：该线程记的 SKIPPED 是「fixedRate 超时」。
     */
    var WORKER_THREAD_PREFIX = "aifei-job-worker-";

    /* ===================== 纯工具（无状态） ===================== */

    /**
     * 左补零。
     *
     * @param {number} n 数值
     * @param {number} len 目标长度
     * @returns {string} 补零后的文本
     */
    function pad(n, len) {
        var s = String(n);
        while (s.length < len) {
            s = "0" + s;
        }
        return s;
    }

    /**
     * 把 epoch 毫秒格式化为 HH:mm:ss.SSS（与后端 DemoFormats.clockText 同格式）。
     *
     * @param {number} ms epoch 毫秒
     * @returns {string} 时刻文本
     */
    function clockText(ms) {
        var d = new Date(ms);
        return pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2) + ":" + pad(d.getSeconds(), 2)
            + "." + pad(d.getMilliseconds(), 3);
    }

    /**
     * 取任务短名（去掉包前缀），例如 DemoFixedRateJob#poll。
     *
     * @param {string} name 任务全名
     * @returns {string} 短名
     */
    function shortName(name) {
        var text = String(name || "");
        var dot = text.lastIndexOf(".");
        return dot < 0 ? text : text.slice(dot + 1);
    }

    /**
     * 数值兜底。
     *
     * @param {*} value 待判定值
     * @param {number} fallback 兜底值
     * @returns {number} 数值
     */
    function numberOr(value, fallback) {
        return typeof value === "number" && isFinite(value) ? value : fallback;
    }

    /**
     * 按线程名判定事件来源（模块线程命名见 JobScheduler：唤醒层 aifei-job-wake、执行层 aifei-job-worker-N）。
     *
     * @param {string} threadName 事件线程名
     * @returns {string} pool / wake / other
     */
    function threadRole(threadName) {
        var name = String(threadName || "");
        if (name.indexOf(WORKER_THREAD_PREFIX) === 0) {
            return "pool";
        }
        if (name.indexOf(WAKE_THREAD_PREFIX) === 0) {
            return "wake";
        }
        return "other";
    }

    /**
     * 跳过分类：端点只给 phase 与 threadName，故按线程名区分两类跳过来源（其余归入停机中断一类）。
     *
     * @param {string} role 线程角色
     * @returns {string} reject / overrun / other
     */
    function skipSource(role) {
        if (role === "wake") {
            return "reject";
        }
        if (role === "pool") {
            return "overrun";
        }
        return "other";
    }

    /**
     * 跳过来源说明文本。
     *
     * @param {string} source 跳过来源
     * @returns {string} 说明
     */
    function skipText(source) {
        if (source === "reject") {
            return "提交被拒：执行队列已满，唤醒层就地记账 SKIPPED（事件时间为原计划触发时刻），节拍基准随之重置";
        }
        if (source === "overrun") {
            return "fixedRate 超时：本轮耗时越过下个计划点，执行层记 SKIPPED（事件时间为原计划触发时刻），节拍基准重置为当前时刻";
        }
        return "其他来源（如停机中断）：非唤醒 / 执行线程记账的 SKIPPED";
    }

    /**
     * 事件说明文本：只按 phase 与后端返回的字段解释语义，不补造数据。
     *
     * @param {object} ev 事件视图（含 phase / threadRole / error）
     * @returns {string} 说明
     */
    function eventDesc(ev) {
        if (ev.phase === "STARTED") {
            return "到点触发：已提交执行层执行（STARTED 不记账，state() 里仍保留上一次终态）";
        }
        if (ev.phase === "SUCCESS") {
            return "本轮执行完成：续排在 finally 中完成，节拍不受异常影响";
        }
        if (ev.phase === "FAILED") {
            return "本轮异常（就地隔离，不重试、不影响其他任务与续排）：" + (ev.error || "——");
        }
        if (ev.phase === "SKIPPED") {
            return skipText(skipSource(ev.threadRole));
        }
        return "未识别的阶段：" + ev.phase;
    }

    /**
     * 距离下次触发的倒计时文本（按浏览器时钟与端点时间戳的差值估算）。
     *
     * @param {number} nextFireTime 下次触发时刻（epoch 毫秒）
     * @returns {string} 倒计时文本
     */
    function countdownText(nextFireTime) {
        if (!nextFireTime || nextFireTime <= 0) {
            return "无下次触发";
        }
        var left = nextFireTime - Date.now();
        if (left <= 0) {
            return "已到点，等下一轮轮询刷新";
        }
        if (left < 1000) {
            return "不到 1 秒后";
        }
        return "约 " + (left / 1000).toFixed(1) + " 秒后";
    }

    /**
     * 把异常翻译成中文提示。
     *
     * @param {*} err 异常对象
     * @returns {string} 提示文本
     */
    function errorText(err) {
        if (!err) {
            return "未知错误";
        }
        if (err.name === "AbortError") {
            return "请求超时（" + REQUEST_TIMEOUT_MS + " ms 内无响应）";
        }
        if (err.name === "TypeError") {
            return "无法连接后端（请求未到达，检查 18110 端口进程是否在运行）";
        }
        return err.message || String(err);
    }

    /**
     * 拉取一个端点并解析业务信封：只有 code=0 才算成功，否则把 msg 抛出。
     *
     * @param {string} url 端点地址
     * @returns {Promise<*>} data 部分
     */
    function fetchJson(url) {
        var controller = typeof window.AbortController === "function" ? new window.AbortController() : null;
        var timer = 0;
        var options = {
            method: "GET",
            cache: "no-store",
            headers: {"Accept": "application/json"}
        };
        if (controller) {
            options.signal = controller.signal;
            timer = setTimeout(function () {
                controller.abort();
            }, REQUEST_TIMEOUT_MS);
        }

        function cleanup() {
            if (timer) {
                clearTimeout(timer);
                timer = 0;
            }
        }

        return fetch(url, options).then(function (resp) {
            if (!resp.ok) {
                throw new Error("HTTP " + resp.status + "（" + url + "）");
            }
            return resp.json();
        }).then(function (json) {
            if (!json || typeof json.code !== "number") {
                throw new Error("响应格式异常：缺少 code 字段（" + url + "）");
            }
            if (json.code !== 0) {
                throw new Error(json.msg || ("业务失败 code=" + json.code + "（" + url + "）"));
            }
            return json.data;
        }).then(function (data) {
            cleanup();
            return data;
        }, function (err) {
            cleanup();
            throw new Error(errorText(err));
        });
    }

    /* ===================== 页面 ===================== */

    var app = Vue.createApp({
        setup: function () {
            // ---- 轮询状态 ----
            var backendOk = Vue.ref(false);
            var backendMsg = Vue.ref("");
            var firstLoaded = Vue.ref(false);
            var autoPoll = Vue.ref(true);
            var refreshing = Vue.ref(false);
            var clearing = Vue.ref(false);
            var clearMsg = Vue.ref("");
            var lastRefreshAt = Vue.ref("—");

            // ---- 数据（全部来自端点） ----
            var names = Vue.ref([]);
            var rows = Vue.ref([]);
            var events = Vue.ref([]);
            var eventCapacity = Vue.ref(DEFAULT_EVENT_CAPACITY);
            var stateMap = Vue.reactive({});
            var shutdown = Vue.reactive({
                applying: false,
                timeoutMillis: 0,
                poolSize: 0,
                queueCapacity: 0,
                steps: [],
                inFlight: [],
                runningTotal: 0,
                skippedTotal: 0,
                note: ""
            });

            // ---- 非响应式内部状态 ----
            var pollTimer = 0;
            var lastSeq = null;
            var firingTimers = {};
            var latestByTask = {};
            var disposed = false;

            /* ---------- 事件流 ---------- */

            /**
             * 记录每个任务最近一条事件（事件流最新在前，故首次出现即为最近），供任务行显示执行线程。
             *
             * @param {Array} list 后端事件数组
             */
            function collectLatest(list) {
                var map = {};
                for (var i = 0; i < list.length; i++) {
                    var item = list[i] || {};
                    var taskName = item.taskName || "";
                    if (!taskName || map[taskName]) {
                        continue;
                    }
                    map[taskName] = {
                        phase: item.phase || "",
                        threadName: item.threadName || "—"
                    };
                }
                latestByTask = map;
            }

            /**
             * 把后端事件数组转成视图模型（只做字段映射与语义说明，不产生任何假数据）。
             *
             * @param {Array} list 后端事件数组
             * @returns {Array} 视图数组
             */
            function toEventViews(list) {
                var views = [];
                for (var i = 0; i < list.length; i++) {
                    var item = list[i] || {};
                    var phase = item.phase || "UNKNOWN";
                    var role = threadRole(item.threadName);
                    var view = {
                        key: "ev-" + numberOr(item.seq, i),
                        seq: numberOr(item.seq, 0),
                        time: item.triggerAt || "—",
                        phase: phase,
                        threadName: item.threadName || "—",
                        threadRole: role,
                        taskName: item.taskName || "",
                        error: item.error || ""
                    };
                    view.desc = eventDesc(view);
                    views.push(view);
                }
                return views;
            }

            /**
             * 给任务行短时高亮（到点触发 / 跳过 / 终态到达）；不阻塞、不 sleep。
             *
             * @param {string} taskName 任务全名
             */
            function markFiring(taskName) {
                var row = findRow(taskName);
                if (!row) {
                    return;
                }
                row.firing = true;
                if (firingTimers[taskName]) {
                    clearTimeout(firingTimers[taskName]);
                }
                firingTimers[taskName] = setTimeout(function () {
                    delete firingTimers[taskName];
                    var current = findRow(taskName);
                    if (current) {
                        current.firing = false;
                    }
                }, FIRING_HIGHLIGHT_MS);
            }

            /**
             * 按任务名取当前任务行。
             *
             * @param {string} taskName 任务全名
             * @returns {object|null} 任务行
             */
            function findRow(taskName) {
                var list = rows.value;
                for (var i = 0; i < list.length; i++) {
                    if (list[i].name === taskName) {
                        return list[i];
                    }
                }
                return null;
            }

            /**
             * 落实事件流：渲染 + 对新增事件对应任务触发行高亮（首轮不闪，避免整屏一起闪）。
             *
             * @param {Array} list 后端事件数组
             */
            function applyEvents(list) {
                var isFirstApply = !firstLoaded.value;
                var added = [];
                var maxSeq = lastSeq;
                for (var i = 0; i < list.length; i++) {
                    var seq = numberOr(list[i] && list[i].seq, 0);
                    if (maxSeq === null || seq > maxSeq) {
                        maxSeq = seq;
                    }
                    if (lastSeq !== null && seq > lastSeq) {
                        added.push(list[i]);
                    }
                }
                lastSeq = maxSeq;
                events.value = toEventViews(list);
                if (isFirstApply) {
                    return;
                }
                for (var k = 0; k < added.length && k < HIGHLIGHT_BATCH; k++) {
                    var taskName = added[k] && added[k].taskName;
                    if (taskName) {
                        markFiring(taskName);
                    }
                }
            }

            /* ---------- JobKit.state() ---------- */

            /**
             * 落实 state()：整表替换，键为任务全名。
             *
             * @param {object} data 后端返回的 map
             */
            function applyState(data) {
                var existing = Object.keys(stateMap);
                for (var i = 0; i < existing.length; i++) {
                    delete stateMap[existing[i]];
                }
                if (!data) {
                    return;
                }
                var keys = Object.keys(data);
                for (var k = 0; k < keys.length; k++) {
                    var item = data[keys[k]] || {};
                    stateMap[keys[k]] = {
                        phase: item.phase || "",
                        triggerAt: item.triggerAt || "—",
                        error: item.error || ""
                    };
                }
            }

            /* ---------- 任务清单 ---------- */

            /**
             * 由 /tasks + /state + 事件流重建任务行；保留上一轮的高亮标记，避免闪烁被重置。
             *
             * @param {Array} list 后端任务数组
             */
            function buildRows(list) {
                var previous = {};
                for (var p = 0; p < rows.value.length; p++) {
                    previous[rows.value[p].name] = rows.value[p];
                }
                var built = [];
                for (var i = 0; i < list.length; i++) {
                    var task = list[i] || {};
                    var name = task.name || "";
                    var old = previous[name];
                    var st = stateMap[name] || null;
                    var latest = latestByTask[name] || null;
                    var running = task.running === true;
                    built.push({
                        name: name,
                        biz: task.biz || "",
                        triggerText: task.trigger || "—",
                        rhythmText: task.rhythm || "—",
                        registered: task.registered === true,
                        running: running,
                        runThread: running && latest ? latest.threadName : "—",
                        lastTriggerAt: task.lastTriggerAt || "—",
                        nextFireAt: task.nextFireAt || "—",
                        countdownText: countdownText(numberOr(task.nextFireTime, 0)),
                        statePhase: st && st.phase ? st.phase : "",
                        stateTriggerAt: st && st.triggerAt ? st.triggerAt : "—",
                        phaseNote: st && st.phase ? "state() 最近终态" : "从未产生终态（执行中的任务也不入 state()）",
                        firing: old ? old.firing === true : false
                    });
                }
                rows.value = built;
            }

            /* ---------- 停机（只读） ---------- */

            /**
             * 落实 /shutdown：三步文本、在途任务与统计。
             *
             * @param {object} data 后端返回的停机视图
             */
            function applyShutdown(data) {
                if (!data) {
                    return;
                }
                shutdown.applying = data.applying === true;
                shutdown.timeoutMillis = numberOr(data.timeoutMillis, 0);
                shutdown.poolSize = numberOr(data.poolSize, 0);
                shutdown.queueCapacity = numberOr(data.queueCapacity, 0);
                shutdown.runningTotal = numberOr(data.runningTotal, 0);
                shutdown.skippedTotal = numberOr(data.skippedTotal, 0);
                shutdown.note = data.note || "";

                var steps = data.steps || [];
                var built = [];
                for (var i = 0; i < steps.length; i++) {
                    built.push({
                        key: "step-" + i,
                        text: steps[i],
                        badge: i === 0 ? "停机语义 · 立即"
                            : (i === 1 ? "最多等待 " + shutdown.timeoutMillis + " ms" : "仅超时才发生")
                    });
                }
                shutdown.steps = built;

                var inFlight = data.inFlight || [];
                var items = [];
                for (var k = 0; k < inFlight.length; k++) {
                    var item = inFlight[k] || {};
                    items.push({
                        key: (item.taskName || "task") + "|" + numberOr(item.startedAt, k),
                        taskName: item.taskName || "—",
                        threadName: item.threadName || "—",
                        startedAtText: item.startedAtText || "—",
                        runningMillis: numberOr(item.runningMillis, 0)
                    });
                }
                shutdown.inFlight = items;
            }

            /* ---------- 轮询 ---------- */

            /**
             * 拉取一轮全部端点：任一失败即进入可见降级（保留上一次成功数据），恢复后自动继续。
             *
             * @returns {Promise<void>} 完成信号
             */
            function refreshOnce() {
                if (refreshing.value) {
                    return Promise.resolve();
                }
                refreshing.value = true;
                return Promise.all([
                    fetchJson(ENDPOINTS.names),
                    fetchJson(ENDPOINTS.state),
                    fetchJson(ENDPOINTS.events),
                    fetchJson(ENDPOINTS.tasks),
                    fetchJson(ENDPOINTS.shutdown)
                ]).then(function (result) {
                    if (disposed) {
                        return;
                    }
                    names.value = result[0] || [];
                    applyState(result[1]);
                    var eventList = result[2] || [];
                    collectLatest(eventList);
                    buildRows(result[3] || []);
                    applyShutdown(result[4]);
                    applyEvents(eventList);
                    backendOk.value = true;
                    backendMsg.value = "";
                    firstLoaded.value = true;
                    lastRefreshAt.value = clockText(Date.now());
                }, function (err) {
                    if (disposed) {
                        return;
                    }
                    backendOk.value = false;
                    backendMsg.value = errorText(err);
                }).then(function () {
                    refreshing.value = false;
                });
            }

            /**
             * 启动轮询定时器（自动轮询开关打开且页面可见时才有定时器）。
             */
            function startPoll() {
                stopPoll();
                if (disposed || !autoPoll.value) {
                    return;
                }
                pollTimer = setInterval(function () {
                    if (document.hidden) {
                        return;
                    }
                    refreshOnce();
                }, POLL_INTERVAL_MS);
            }

            /**
             * 停止轮询定时器。
             */
            function stopPoll() {
                if (pollTimer) {
                    clearInterval(pollTimer);
                    pollTimer = 0;
                }
            }

            /* ---------- 交互 ---------- */

            /**
             * 手动刷新一次（与自动轮询共用同一把 refreshing 锁，不会并发）。
             */
            function refreshNow() {
                refreshOnce();
            }

            /**
             * 切换自动轮询：关闭即停表，打开立刻拉一轮再起表。
             *
             * @param {boolean} checked 复选框状态
             */
            function toggleAutoPoll(checked) {
                autoPoll.value = checked === true;
                if (autoPoll.value) {
                    refreshOnce();
                    startPoll();
                } else {
                    stopPoll();
                }
            }

            /**
             * 清空事件流（真实端点 /job/demo/events/clear），随后立刻同步一轮。
             */
            function clearEvents() {
                if (clearing.value) {
                    return;
                }
                clearing.value = true;
                clearMsg.value = "";
                fetchJson(ENDPOINTS.eventsClear).then(function (data) {
                    if (disposed) {
                        return;
                    }
                    var info = data || {};
                    events.value = [];
                    eventCapacity.value = numberOr(info.capacity, eventCapacity.value);
                    clearMsg.value = "已清空事件流 " + numberOr(info.cleared, 0) + " 条（剩余 "
                        + numberOr(info.remaining, 0) + " 条 / 容量 " + eventCapacity.value + "）";
                    return refreshOnce();
                }, function (err) {
                    if (disposed) {
                        return;
                    }
                    clearMsg.value = "清空事件流失败：" + errorText(err) + "（不清空页面已有的历史事件）";
                }).then(function () {
                    clearing.value = false;
                });
            }

            /* ---------- 计算属性 ---------- */

            var backendCls = Vue.computed(function () {
                if (backendOk.value) {
                    return "running";
                }
                return firstLoaded.value ? "err" : "idle";
            });

            var backendText = Vue.computed(function () {
                if (backendOk.value) {
                    return "后端在线";
                }
                return firstLoaded.value ? "后端不可达" : "连接中";
            });

            var pollText = Vue.computed(function () {
                var head = autoPoll.value ? "自动轮询 " + POLL_INTERVAL_MS + " ms" : "轮询已暂停";
                return head + " · 最近刷新 " + lastRefreshAt.value;
            });

            var poolText = Vue.computed(function () {
                if (!shutdown.poolSize && !shutdown.queueCapacity) {
                    return "—";
                }
                return shutdown.poolSize + " 线程 · 队列 " + shutdown.queueCapacity;
            });

            var namesText = Vue.computed(function () {
                if (names.value.length === 0) {
                    return firstLoaded.value ? "（空：未注册任何任务，或已停机）" : "（加载中）";
                }
                return names.value.join(" · ");
            });

            var stateRows = Vue.computed(function () {
                var list = [];
                var keys = Object.keys(stateMap);
                keys.sort();
                for (var i = 0; i < keys.length; i++) {
                    var item = stateMap[keys[i]] || {};
                    list.push({
                        name: keys[i],
                        phase: item.phase || "",
                        triggerAt: item.triggerAt || "—",
                        error: item.error || ""
                    });
                }
                return list;
            });

            var counts = Vue.computed(function () {
                var c = {started: 0, success: 0, failed: 0, skipped: 0, reject: 0, overrun: 0, other: 0};
                var list = events.value;
                for (var i = 0; i < list.length; i++) {
                    var phase = list[i].phase;
                    if (phase === "STARTED") {
                        c.started++;
                    } else if (phase === "SUCCESS") {
                        c.success++;
                    } else if (phase === "FAILED") {
                        c.failed++;
                    } else if (phase === "SKIPPED") {
                        c.skipped++;
                        var source = skipSource(list[i].threadRole);
                        if (source === "reject") {
                            c.reject++;
                        } else if (source === "overrun") {
                            c.overrun++;
                        } else {
                            c.other++;
                        }
                    }
                }
                return c;
            });

            var isolationText = Vue.computed(function () {
                var failedByTask = {};
                var list = events.value;
                var i;
                for (i = 0; i < list.length; i++) {
                    if (list[i].phase === "FAILED") {
                        var name = list[i].taskName;
                        failedByTask[name] = (failedByTask[name] || 0) + 1;
                    }
                }
                var topName = "";
                var topCount = 0;
                var keys = Object.keys(failedByTask);
                for (i = 0; i < keys.length; i++) {
                    if (failedByTask[keys[i]] > topCount) {
                        topCount = failedByTask[keys[i]];
                        topName = keys[i];
                    }
                }
                if (!topCount) {
                    return "事件流内暂无 FAILED";
                }
                var otherSuccess = 0;
                for (i = 0; i < list.length; i++) {
                    if (list[i].phase === "SUCCESS" && list[i].taskName !== topName) {
                        otherSuccess++;
                    }
                }
                return shortName(topName) + " FAILED " + topCount + " 次 · 同期其他任务 SUCCESS " + otherSuccess + " 次";
            });

            var emptyRowsText = Vue.computed(function () {
                if (!firstLoaded.value) {
                    return "尚未取到任务清单：正在连接后端…";
                }
                return "后端返回的任务清单为空（未注册任务，或调度内核已停机）。";
            });

            /* ---------- 生命周期 ---------- */

            Vue.onMounted(function () {
                refreshOnce().then(function () {
                    startPoll();
                });
                document.addEventListener("visibilitychange", onVisibilityChange);
                window.addEventListener("pagehide", stopPoll);
            });

            Vue.onUnmounted(function () {
                disposed = true;
                stopPoll();
                document.removeEventListener("visibilitychange", onVisibilityChange);
                window.removeEventListener("pagehide", stopPoll);
                var keys = Object.keys(firingTimers);
                for (var i = 0; i < keys.length; i++) {
                    clearTimeout(firingTimers[keys[i]]);
                }
                firingTimers = {};
            });

            /**
             * 页面隐藏即停表省资源，回到前台立刻补一轮再起表。
             */
            function onVisibilityChange() {
                if (document.hidden) {
                    stopPoll();
                    return;
                }
                if (autoPoll.value) {
                    refreshOnce();
                    startPoll();
                }
            }

            return {
                // 状态
                backendOk: backendOk,
                backendMsg: backendMsg,
                backendCls: backendCls,
                backendText: backendText,
                firstLoaded: firstLoaded,
                autoPoll: autoPoll,
                pollText: pollText,
                pollIntervalMs: POLL_INTERVAL_MS,
                refreshing: refreshing,
                clearing: clearing,
                clearMsg: clearMsg,
                lastRefreshAt: lastRefreshAt,
                // 数据
                names: names,
                namesText: namesText,
                rows: rows,
                emptyRowsText: emptyRowsText,
                events: events,
                eventCapacity: eventCapacity,
                stateRows: stateRows,
                shutdown: shutdown,
                counts: counts,
                isolationText: isolationText,
                poolText: poolText,
                // 交互
                refreshNow: refreshNow,
                toggleAutoPoll: toggleAutoPoll,
                clearEvents: clearEvents
            };
        }
    });

    app.mount("#job-demo-app");
}());
