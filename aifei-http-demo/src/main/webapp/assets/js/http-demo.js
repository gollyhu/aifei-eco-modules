/* ==========================================================================
 * aifei-http-demo 演示页交互脚本（view/http/index.html）
 * 档案 A（Aifei 静态层）：纯原生 JS + 本地全局构建版 Vue（/assets/lib/vue.global.prod.js），
 * 无构建工具、无 npm、无 CDN、无外网依赖。
 *
 * 约定：
 * 1. 后端统一返回 HTTP 200 + 业务信封 { code, msg, data }，code === 0 才算成功；
 *    本文件的 requestJson() 是唯一请求入口，非 0 一律 reject 成带 code/msg 的 Error。
 * 2. 每个按钮都对应一次真实端点调用，没有任何本地伪造数据。
 * 3. 预期内的「演示失败」（读超时 / 连接被拒）由后端放在 data.error 里，端点本身 code=0，
 *    页面据 ok 字段渲染「响应」或「异常」面板。
 * ========================================================================== */

(function () {
    "use strict";

    var Vue = window.Vue;

    // ---- 端点常量（与 HttpDemoService 的 @Path 一一对应，不猜、不改） ----
    var API = {
        state: "/http/demo/state",
        send: "/http/demo/send",
        get: "/http/demo/get",
        retry: "/http/demo/retry",
        timeout: "/http/demo/timeout",
        interceptor: "/http/demo/interceptor",
        gzip: "/http/demo/gzip",
        redirect: "/http/demo/redirect",
        resetEcho: "/http/demo/echo/state/reset"
    };

    var METHODS = ["GET", "POST", "PUT", "DELETE", "HEAD"];

    var DEFAULT_FORM = {
        method: "GET",
        path: "/demo/echo",
        body: "",
        contentType: "",
        resource: "",
        connectTimeout: "",
        readTimeout: "",
        retryTimes: "",
        intervalMillis: ""
    };

    // ---- 工具 ----

    function pad2(n) {
        var s = String(n);
        return s.length < 2 ? "0" + s : s;
    }

    function clock() {
        var d = new Date();
        return pad2(d.getHours()) + ":" + pad2(d.getMinutes()) + ":" + pad2(d.getSeconds());
    }

    function errText(err) {
        if (err && err.message) {
            return String(err.message);
        }
        return "请求失败（无错误消息）";
    }

    /**
     * 统一请求入口：HTTP 恒 200，成败看信封的 code（0 成功）。
     * 网络错误 / 非 JSON / code != 0 三种情况统一 reject，错误消息直接给用户看。
     */
    function requestJson(url) {
        return fetch(url, {
            method: "GET",
            cache: "no-store",
            headers: { "Accept": "application/json" }
        }).then(function (response) {
            if (!response.ok) {
                throw new Error("HTTP " + response.status + " " + response.statusText);
            }
            return response.text();
        }).then(function (text) {
            var json = null;
            if (text) {
                try {
                    json = JSON.parse(text);
                } catch (e) {
                    throw new Error("响应不是合法 JSON：" + text.slice(0, 120));
                }
            }
            if (!json) {
                throw new Error("响应为空");
            }
            var code = Number(json.code);
            if (code !== 0) {
                var bizError = new Error(json.msg ? String(json.msg) : ("业务失败 code=" + code));
                bizError.code = code;
                throw bizError;
            }
            return {
                code: code,
                msg: json.msg === null || json.msg === undefined ? "" : String(json.msg),
                data: json.data || {}
            };
        });
    }

    /**
     * 拼查询串：跳过空值，键与值都做百分号编码（后端把 query 原样接在 URL 后）。
     */
    function buildUrl(base, params) {
        var parts = [];
        Object.keys(params).forEach(function (key) {
            var value = params[key];
            if (value === undefined || value === null || value === "") {
                return;
            }
            parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(String(value)));
        });
        return parts.length ? base + "?" + parts.join("&") : base;
    }

    /**
     * 尽量把 JSON 文本美化成缩进格式；不是 JSON 就原样返回。
     */
    function prettyText(text) {
        if (text === null || text === undefined) {
            return "";
        }
        var raw = String(text);
        var trimmed = raw.trim();
        if (!trimmed) {
            return "";
        }
        var head = trimmed.charAt(0);
        if (head === "{" || head === "[") {
            try {
                return JSON.stringify(JSON.parse(trimmed), null, 2);
            } catch (e) {
                return raw;
            }
        }
        return raw;
    }

    function byteLength(text) {
        var value = text === null || text === undefined ? "" : String(text);
        if (!value) {
            return 0;
        }
        if (window.TextEncoder) {
            return new TextEncoder().encode(value).length;
        }
        return value.length;
    }

    /**
     * 后端 headers 可能是 Map<String, List<String>>，也可能是 Map<String, String>。
     */
    function headerValues(raw) {
        if (raw === null || raw === undefined) {
            return [""];
        }
        if (Object.prototype.toString.call(raw) === "[object Array]") {
            return raw.map(function (item) {
                return item === null || item === undefined ? "" : String(item);
            });
        }
        return [String(raw)];
    }

    function headerText(headers) {
        if (!headers) {
            return "（无）";
        }
        var names = Object.keys(headers);
        if (!names.length) {
            return "（无）";
        }
        return names.map(function (name) {
            return name + ": " + headerValues(headers[name]).join(", ");
        }).join(" | ");
    }

    function statusMetricCls(status) {
        var code = Number(status);
        if (code >= 200 && code < 300) {
            return "metric-ok";
        }
        if (code >= 300 && code < 400) {
            return "metric-warn";
        }
        return "metric-err";
    }

    // ---- 复用组件：一个 run 结果面板（后端所有演示端点都返回同构的 ok/response/error 三段） ----

    var RUN_PANEL_TEMPLATE = [
        '<div class="run-panel">',
        '    <span class="run-title" v-if="title">{{ title }}</span>',
        '    <p class="hint" v-if="!run">尚未运行</p>',
        '    <template v-else>',
        '        <div class="metrics">',
        '            <span class="metric" :class="statusCls">',
        '                <span class="metric-k">{{ run.ok ? "状态码" : "异常类型" }}</span>',
        '                <span class="metric-v">{{ run.ok ? run.response.status : (run.error.simpleType || "失败") }}</span>',
        '            </span>',
        '            <span class="metric metric-tight">',
        '                <span class="metric-k">attempts</span>',
        '                <span class="metric-v">{{ attempts }}</span>',
        '            </span>',
        '            <span class="metric metric-tight">',
        '                <span class="metric-k">elapsedMillis</span>',
        '                <span class="metric-v">{{ elapsed }}</span>',
        '            </span>',
        '            <span class="metric metric-tight" v-if="run.ok">',
        '                <span class="metric-k">bodySize</span>',
        '                <span class="metric-v">{{ run.response.bodySize }}</span>',
        '            </span>',
        '        </div>',
        '        <div class="error-box" v-if="!run.ok" role="alert" style="margin-top: 8px;">',
        '            <p><span class="kv-k">type</span><code>{{ run.error.type }}</code></p>',
        '            <p><span class="kv-k">message</span><span>{{ run.error.message || "（null）" }}</span></p>',
        '            <p><span class="kv-k">cause</span><code>{{ run.error.causeType || "（无 cause）" }}</code></p>',
        '            <p><span class="kv-k">causeMessage</span><span>{{ run.error.causeMessage || "（无）" }}</span></p>',
        '        </div>',
        '        <template v-if="run.ok">',
        '            <h4 class="sub-title" v-if="showHeaders">响应头（{{ headerRows.length }} 个，多值可折叠）</h4>',
        '            <div class="table-wrap" v-if="showHeaders">',
        '                <table class="data-table">',
        '                    <thead><tr><th>名称</th><th>值</th></tr></thead>',
        '                    <tbody>',
        '                    <tr v-for="row in headerRows" :key="row.name">',
        '                        <td class="cell-key">{{ row.name }}</td>',
        '                        <td class="cell-val">',
        '                            <span v-if="!row.multi || !expanded[row.name]">{{ row.values[0] }}</span>',
        '                            <span v-else><span v-for="(value, idx) in row.values" :key="idx">{{ idx + 1 }}. {{ value }}<br></span></span>',
        '                            <button class="btn btn-mini" type="button" v-if="row.multi" @click="toggle(row.name)">',
        '                                {{ expanded[row.name] ? "折叠（共 " + row.values.length + " 个值）" : "展开其余（共 " + row.values.length + " 个值）" }}',
        '                            </button>',
        '                        </td>',
        '                    </tr>',
        '                    </tbody>',
        '                </table>',
        '            </div>',
        '            <h4 class="sub-title" v-if="showBody">响应体（bodySize {{ run.response.bodySize }} 字节 / bodyLength {{ run.response.bodyLength }} 字符）</h4>',
        '            <pre class="body-box" :class="{ \'body-box-empty\': !prettyBody }" v-if="showBody">{{ prettyBody || "（空响应体：例如 HEAD 请求或 302 重定向）" }}</pre>',
        '            <p class="hint" v-if="run.response.decodeError">解码失败：{{ run.response.decodeError.simpleType }}: {{ run.response.decodeError.message }}</p>',
        '        </template>',
        '    </template>',
        '</div>'
    ].join("\n");

    var RunPanel = {
        name: "run-panel",
        props: {
            run: { type: Object, default: null },
            title: { type: String, default: "" },
            showHeaders: { type: Boolean, default: false },
            showBody: { type: Boolean, default: true }
        },
        data: function () {
            return { expanded: {} };
        },
        computed: {
            attempts: function () {
                if (!this.run) {
                    return "—";
                }
                if (this.run.ok) {
                    return this.run.response.attempts;
                }
                return this.run.error.attempts === undefined || this.run.error.attempts === null
                    ? "—" : this.run.error.attempts;
            },
            elapsed: function () {
                if (!this.run) {
                    return "—";
                }
                var value = this.run.ok ? this.run.response.elapsedMillis : this.run.error.elapsedMillis;
                return value === undefined || value === null ? "—" : value;
            },
            statusCls: function () {
                if (!this.run || !this.run.ok) {
                    return "metric-err";
                }
                return statusMetricCls(this.run.response.status);
            },
            headerRows: function () {
                if (!this.run || !this.run.ok) {
                    return [];
                }
                var headers = this.run.response.headers || {};
                return Object.keys(headers).map(function (name) {
                    var values = headerValues(headers[name]);
                    return {
                        name: name,
                        values: values,
                        multi: values.length > 1
                    };
                });
            },
            prettyBody: function () {
                if (!this.run || !this.run.ok) {
                    return "";
                }
                return prettyText(this.run.response.body);
            }
        },
        methods: {
            toggle: function (name) {
                this.expanded[name] = !this.expanded[name];
            }
        },
        template: RUN_PANEL_TEMPLATE
    };

    // ---- 主应用 ----

    var app = Vue.createApp({
        data: function () {
            return {
                methods: METHODS,
                selfBaseUrl: "",
                state: null,
                stateLoading: false,
                stateError: "",
                stateAt: "",
                resetBusy: false,
                resetMsg: "",
                resetError: "",

                form: {
                    method: DEFAULT_FORM.method,
                    path: DEFAULT_FORM.path,
                    query: [{ k: "status", v: "200" }],
                    headers: [],
                    body: DEFAULT_FORM.body,
                    contentType: DEFAULT_FORM.contentType,
                    resource: DEFAULT_FORM.resource,
                    connectTimeout: DEFAULT_FORM.connectTimeout,
                    readTimeout: DEFAULT_FORM.readTimeout,
                    retryTimes: DEFAULT_FORM.retryTimes,
                    intervalMillis: DEFAULT_FORM.intervalMillis
                },

                sending: false,
                sendError: "",
                sendAt: "",
                sendUrl: "",
                sendCode: null,
                sendMsg: "",
                requestEcho: null,
                sendRun: null,
                sendRejected: [],
                sendTrace: [],
                sendServiceElapsed: null,
                hasSent: false,

                basicScene: {
                    busy: false, error: "", data: null, url: "", at: "",
                    status: "200", delay: "0", failFirst: "0", key: "demo-get"
                },
                retryScene: {
                    busy: false, error: "", data: null, url: "", at: "",
                    failFirst: "2", retryTimes: "2", intervalMillis: ""
                },
                timeoutScene: {
                    busy: false, error: "", data: null, url: "", at: "",
                    delay: "1500", readTimeout: "300", connectTimeout: "500"
                },
                interceptorScene: {
                    busy: false, error: "", data: null, url: "", at: "",
                    status: "200"
                },
                resourceScene: {
                    busy: false, error: "", data: null, url: "", at: "",
                    status: "200", resource: "demo.resource.explicit"
                },
                gzipScene: { busy: false, error: "", data: null, url: "", at: "" },
                redirectScene: { busy: false, error: "", data: null, url: "", at: "" },

                // 后端 HttpTrace / EchoState 是进程级静态计数器：并发的两次演示会互相污染 beforeOrder。
                // 因此页面层做互斥——一次只允许一个「发送 / 场景」在跑，其余按钮禁用。
                activeScene: ""
            };
        },

        computed: {
            busyAny: function () {
                return this.sending || this.activeScene !== "";
            },
            counters: function () {
                var snapshot = this.state || {};
                return {
                    requestCount: snapshot.requestCount === undefined ? "—" : snapshot.requestCount,
                    attemptCount: snapshot.attemptCount === undefined ? "—" : snapshot.attemptCount,
                    retryCount: snapshot.retryCount === undefined ? "—" : snapshot.retryCount,
                    lastResource: snapshot.lastResource || ""
                };
            },
            backendText: function () {
                if (this.stateError) {
                    return "状态读取失败";
                }
                if (this.stateLoading && !this.state) {
                    return "读取状态中…";
                }
                if (this.state && this.state.clientInjected) {
                    return "HttpClient 已装配";
                }
                if (this.state) {
                    return "HttpClient 未装配";
                }
                return "尚未读取状态";
            },
            backendCls: function () {
                if (this.stateError) {
                    return "chip-err";
                }
                if (this.state && this.state.clientInjected) {
                    return "chip-ok";
                }
                if (this.stateLoading && !this.state) {
                    return "chip-idle";
                }
                return "chip-warn";
            },

            // ---- 发送前预览（与后端 resolveSelfUrl / 默认资源名规则逐条对齐） ----
            baseUrl: function () {
                return this.selfBaseUrl || window.location.origin;
            },
            hostPort: function () {
                return this.baseUrl.replace(/^[a-zA-Z]+:\/\//, "");
            },
            previewMethod: function () {
                return (this.form.method || "GET").toUpperCase();
            },
            previewPath: function () {
                var path = (this.form.path || "").trim();
                if (!path) {
                    return "/demo/echo";
                }
                return path.charAt(0) === "/" ? path : "/" + path;
            },
            previewQuery: function () {
                var pairs = [];
                this.form.query.forEach(function (row) {
                    var key = (row.k || "").trim();
                    if (!key) {
                        return;
                    }
                    pairs.push(encodeURIComponent(key) + "=" + encodeURIComponent(row.v === undefined || row.v === null ? "" : String(row.v)));
                });
                return pairs.join("&");
            },
            previewUrl: function () {
                return this.baseUrl + this.previewPath + (this.previewQuery ? "?" + this.previewQuery : "");
            },
            previewHeaderText: function () {
                var lines = [];
                this.form.headers.forEach(function (row) {
                    var key = (row.k || "").trim();
                    if (!key) {
                        return;
                    }
                    lines.push(key + ": " + (row.v === undefined || row.v === null ? "" : String(row.v)));
                });
                return lines.join(" ; ");
            },
            previewBodyText: function () {
                var body = this.form.body || "";
                if (!body) {
                    return "（空：不设置请求体）";
                }
                return byteLength(body) + " 字节 / " + body.length + " 字符";
            },
            previewPolicyText: function () {
                var cfg = (this.state && this.state.httpConfig) || {};
                var connectText = this.form.connectTimeout === "" || this.form.connectTimeout === null
                    ? "connectTimeout 继承配置 " + (cfg["http.connect-timeout"] || "?") + " ms"
                    : "connectTimeout = " + this.form.connectTimeout + " ms（逐请求覆盖）";
                var readText = this.form.readTimeout === "" || this.form.readTimeout === null
                    ? "readTimeout 继承配置 " + (cfg["http.read-timeout"] || "?") + " ms"
                    : "readTimeout = " + this.form.readTimeout + " ms（逐请求覆盖）";
                var retryText;
                if (this.form.retryTimes === "" || this.form.retryTimes === null) {
                    retryText = "不调用 retry()，继承配置 http.retry-times=" + (cfg["http.retry-times"] || "?");
                } else {
                    retryText = "显式 retry(" + this.form.retryTimes + ")"
                        + (this.form.intervalMillis === "" || this.form.intervalMillis === null
                            ? "，间隔继承配置 " + (cfg["http.retry-interval"] || "?") + " ms"
                            : "，间隔 " + this.form.intervalMillis + " ms");
                }
                return connectText + " · " + readText + " · " + retryText;
            },
            derivedResource: function () {
                return this.previewMethod + " " + this.hostPort + this.previewPath;
            },
            resourceOverridden: function () {
                return !!(this.form.resource || "").trim();
            },
            effectiveResource: function () {
                return this.resourceOverridden ? this.form.resource.trim() : this.derivedResource;
            },

            // ---- 状态快照派生 ----
            httpConfigRows: function () {
                var config = (this.state && this.state.httpConfig) || {};
                return Object.keys(config).map(function (key) {
                    return { key: key, value: config[key] };
                });
            },
            echoCounterRows: function () {
                var counters = (this.state && this.state.echoCounters) || {};
                return Object.keys(counters).map(function (key) {
                    return { key: key, value: counters[key] };
                });
            }
        },

        methods: {
            // ---- 展示型方法 ----
            clock: clock,
            headerText: headerText,
            configValue: function (key) {
                var config = (this.state && this.state.httpConfig) || {};
                return config[key] === undefined ? "—" : config[key];
            },
            jvmValue: function (name) {
                var jvm = (this.state && this.state.jvmProperties) || {};
                return jvm[name] === undefined ? "—" : jvm[name];
            },
            jvmNote: function () {
                var jvm = (this.state && this.state.jvmProperties) || {};
                return jvm.note || "";
            },
            statusLabel: function (run) {
                if (!run) {
                    return "—";
                }
                return run.ok ? String(run.response.status) : "—（异常）";
            },

            // ---- 状态快照 ----
            loadState: function (silent) {
                var self = this;
                this.stateLoading = true;
                if (!silent) {
                    this.stateError = "";
                }
                return requestJson(API.state).then(function (res) {
                    self.state = res.data;
                    self.selfBaseUrl = res.data.selfBaseUrl || "";
                    self.stateAt = clock();
                    self.stateError = "";
                }).catch(function (err) {
                    self.stateError = errText(err);
                }).then(function () {
                    self.stateLoading = false;
                });
            },
            resetEchoState: function () {
                var self = this;
                if (this.resetBusy) {
                    return;
                }
                this.resetBusy = true;
                this.resetMsg = "";
                this.resetError = "";
                requestJson(API.resetEcho).then(function (res) {
                    var cleared = res.data.clearedCounters || {};
                    self.resetMsg = "已重置 echo 计数：清理前有 " + Object.keys(cleared).length
                        + " 个计数分组，当前 " + Object.keys(res.data.counters || {}).length + " 个。";
                }).catch(function (err) {
                    self.resetError = errText(err);
                }).then(function () {
                    self.resetBusy = false;
                    self.loadState(true);
                });
            },

            // ---- 表单编辑 ----
            addQuery: function () {
                this.form.query.push({ k: "", v: "" });
            },
            removeQuery: function (idx) {
                this.form.query.splice(idx, 1);
            },
            addHeader: function () {
                this.form.headers.push({ k: "", v: "" });
            },
            removeHeader: function (idx) {
                this.form.headers.splice(idx, 1);
            },
            resetForm: function () {
                if (this.sending) {
                    return;
                }
                var self = this;
                this.form.method = DEFAULT_FORM.method;
                this.form.path = DEFAULT_FORM.path;
                this.form.query = [{ k: "status", v: "200" }];
                this.form.headers = [];
                this.form.body = DEFAULT_FORM.body;
                this.form.contentType = DEFAULT_FORM.contentType;
                this.form.resource = DEFAULT_FORM.resource;
                this.form.connectTimeout = DEFAULT_FORM.connectTimeout;
                this.form.readTimeout = DEFAULT_FORM.readTimeout;
                this.form.retryTimes = DEFAULT_FORM.retryTimes;
                this.form.intervalMillis = DEFAULT_FORM.intervalMillis;
                // 空态：结果一起清掉，回到首次进入的引导文案
                self.sendError = "";
                self.sendAt = "";
                self.sendUrl = "";
                self.sendCode = null;
                self.sendMsg = "";
                self.requestEcho = null;
                self.sendRun = null;
                self.sendRejected = [];
                self.sendTrace = [];
                self.sendServiceElapsed = null;
                self.hasSent = false;
            },

            // ---- 通用发送 ----
            buildSendUrl: function () {
                var form = this.form;
                var headerLines = [];
                form.headers.forEach(function (row) {
                    var key = (row.k || "").trim();
                    if (!key) {
                        return;
                    }
                    headerLines.push(key + ": " + (row.v === undefined || row.v === null ? "" : String(row.v)));
                });
                return buildUrl(API.send, {
                    method: this.previewMethod,
                    path: this.previewPath,
                    query: this.previewQuery,
                    headers: headerLines.join("\n"),
                    body: form.body,
                    contentType: form.contentType,
                    resource: form.resource,
                    connectTimeout: form.connectTimeout,
                    readTimeout: form.readTimeout,
                    retryTimes: form.retryTimes,
                    intervalMillis: form.intervalMillis
                });
            },
            sendRequest: function () {
                var self = this;
                if (this.sending || this.activeScene) {
                    return;
                }
                this.sending = true;
                this.sendError = "";
                this.sendAt = "";
                this.sendUrl = this.buildSendUrl();

                requestJson(this.sendUrl).then(function (res) {
                    var data = res.data || {};
                    self.sendCode = res.code;
                    self.sendMsg = res.msg;
                    self.requestEcho = data.request || null;
                    self.sendRun = {
                        ok: !!data.response,
                        response: data.response || null,
                        error: data.error || null
                    };
                    self.sendRejected = data.rejected || [];
                    self.sendTrace = data.trace || [];
                    self.sendServiceElapsed = data.serviceElapsedMillis === undefined || data.serviceElapsedMillis === null
                        ? null : data.serviceElapsedMillis;
                    self.sendAt = clock();
                    self.hasSent = true;
                }).catch(function (err) {
                    self.sendError = errText(err);
                    self.sendRun = null;
                    self.requestEcho = null;
                    self.sendRejected = [];
                    self.sendTrace = [];
                    self.sendServiceElapsed = null;
                    self.sendAt = clock();
                }).then(function () {
                    self.sending = false;
                    self.loadState(true);
                });
            },

            // ---- 场景运行通用流程 ----
            runScene: function (scene, url) {
                var self = this;
                if (scene.busy || this.activeScene) {
                    return;
                }
                scene.busy = true;
                scene.error = "";
                scene.url = url;
                scene.at = "";
                this.activeScene = url;
                requestJson(url).then(function (res) {
                    scene.data = res.data || {};
                    scene.at = clock();
                }).catch(function (err) {
                    scene.data = null;
                    scene.error = errText(err);
                }).then(function () {
                    scene.busy = false;
                    self.activeScene = "";
                    self.loadState(true);
                });
            },
            runBasicGet: function () {
                var scene = this.basicScene;
                this.runScene(scene, buildUrl(API.get, {
                    status: scene.status,
                    delay: scene.delay,
                    failFirst: scene.failFirst,
                    key: scene.key
                }));
            },
            runRetry: function () {
                var scene = this.retryScene;
                this.runScene(scene, buildUrl(API.retry, {
                    failFirst: scene.failFirst,
                    retryTimes: scene.retryTimes,
                    intervalMillis: scene.intervalMillis
                }));
            },
            runTimeout: function () {
                var scene = this.timeoutScene;
                this.runScene(scene, buildUrl(API.timeout, {
                    delay: scene.delay,
                    readTimeout: scene.readTimeout,
                    connectTimeout: scene.connectTimeout
                }));
            },
            runInterceptor: function () {
                var scene = this.interceptorScene;
                this.runScene(scene, buildUrl(API.interceptor, {
                    status: scene.status
                }));
            },
            runResource: function () {
                var scene = this.resourceScene;
                this.runScene(scene, buildUrl(API.interceptor, {
                    resource: scene.resource,
                    status: scene.status
                }));
            },
            runGzip: function () {
                this.runScene(this.gzipScene, API.gzip);
            },
            runRedirect: function () {
                this.runScene(this.redirectScene, API.redirect);
            }
        },

        mounted: function () {
            this.loadState(false);
        }
    });

    app.component("run-panel", RunPanel);
    app.mount("#http-demo-app");
})();
