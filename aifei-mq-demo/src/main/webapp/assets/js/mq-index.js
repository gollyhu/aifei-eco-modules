/* ==========================================================================
 * aifei-mq-demo 实例入口页交互脚本（view/mq/index.html）
 * 档案 A（Aifei 静态层）：纯原生 JS + 本地全局构建版 Vue（/assets/lib/vue.global.prod.js），
 * 无构建工具、无外部依赖。本页只调用一个端点：GET /mq/demo/instances。
 * ========================================================================== */

(function () {
    "use strict";

    var Vue = window.Vue;

    // ---- 常量 ----
    var INSTANCES_URL = "/mq/demo/instances";
    var DEMO_PAGE = "/mq/demo/page";

    // ---- 工具 ----
    function pad(n, len) {
        var s = String(n);
        while (s.length < len) {
            s = "0" + s;
        }
        return s;
    }

    function nowClock() {
        var d = new Date();
        return pad(d.getHours(), 2) + ":" + pad(d.getMinutes(), 2) + ":" + pad(d.getSeconds(), 2);
    }

    /**
     * 统一请求：HTTP 恒 200，业务结果看信封的 code（0 为成功）。
     * 失败（网络错误 / code != 0）一律 reject 成 Error，由调用方决定如何呈现。
     */
    function requestJson(url) {
        return fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("HTTP " + response.status);
                }
                return response.text();
            })
            .then(function (text) {
                var json = null;
                if (text) {
                    try {
                        json = JSON.parse(text);
                    } catch (e) {
                        throw new Error("响应不是合法 JSON");
                    }
                }
                if (!json) {
                    throw new Error("响应为空");
                }
                if (Number(json.code) !== 0) {
                    throw new Error(json.msg ? String(json.msg) : "code=" + json.code);
                }
                return json.data || {};
            });
    }

    var app = Vue.createApp({
        setup: function () {
            var instances = Vue.ref([]);        // 上一次成功的实例清单（失败时保留）
            var loading = Vue.ref(true);        // 首次加载中
            var busy = Vue.ref(false);          // 请求进行中（刷新）
            var error = Vue.ref("");            // 错误态文案（不含固定前缀）
            var stale = Vue.ref(false);         // 降级态：刷新失败但保留旧数据
            var dataTime = Vue.ref("");         // 数据时间 HH:mm:ss
            var configError = Vue.ref("");      // 后端可选返回的配置问题说明
            var drawerOpen = Vue.ref(false);
            var drawerRef = Vue.ref(null);
            var hasLoaded = false;

            // ---- 取数 ----
            function load() {
                busy.value = true;
                return requestJson(INSTANCES_URL)
                    .then(function (data) {
                        var list = data.instances || [];
                        instances.value = list;
                        configError.value = data.configError ? String(data.configError) : "";
                        error.value = "";
                        stale.value = false;
                        dataTime.value = nowClock();
                        hasLoaded = true;
                    })
                    .catch(function (err) {
                        error.value = err && err.message ? err.message : "请求失败";
                        // 保留上一次成功数据，并标记降级（灰化 + 数据时间）
                        stale.value = hasLoaded;
                    })
                    .then(function () {
                        busy.value = false;
                        loading.value = false;
                    });
            }

            function reload() {
                return load();
            }

            // ---- 跳转（绝对路由） ----
            // 卡片是原生 <a target>：target 固定到实例，重复点击复用同名标签；rel=opener 供演示页"返回即关闭"
            function demoUrl(inst) {
                var name = inst && inst.instanceName ? String(inst.instanceName) : "";
                return name ? DEMO_PAGE + "?instance=" + encodeURIComponent(name) : DEMO_PAGE;
            }

            function tabName(inst) {
                return "mqDemo:" + (inst && inst.instanceName ? String(inst.instanceName) : "");
            }

            // ---- 展示helper ----
            function text(v) {
                return v === null || v === undefined || v === "" ? "—" : String(v);
            }

            function num(v) {
                return v === null || v === undefined || v === "" ? "—" : String(v);
            }

            // 配置前缀展示时补 .*（mq → mq.*；mq.demoA → mq.demoA.*）
            function prefixText(inst) {
                if (!inst || !inst.configPrefix) {
                    return "—";
                }
                return String(inst.configPrefix) + ".*";
            }

            function statusText(inst) {
                return inst && inst.ready === true ? "● 就绪" : "● 未装配";
            }

            function statusCls(inst) {
                return inst && inst.ready === true ? "st-open" : "st-fail";
            }

            function statusTitle(inst) {
                return inst && inst.ready === true
                    ? "该实例的生产者 / 消费者可取用"
                    : "该实例未装配完成，生产者 / 消费者不可取用，请确认 MqPlugin 与 app-config.txt";
            }

            // 顶栏右侧提示：首次加载「加载实例清单…」，手动刷新「刷新中…」
            var busyHint = Vue.computed(function () {
                return loading.value ? "加载实例清单…" : "刷新中…";
            });

            // 顶栏摘要：主实例（mq.type）+ 命名实例（mq.names）逐项列出 name = type
            var instancesSummary = Vue.computed(function () {
                var list = instances.value || [];
                var head = "配置来源：app-config.txt ｜ 实例清单由 mq.type + mq.names 决定";
                if (!list.length) {
                    return head;
                }
                var parts = [];
                list.forEach(function (inst) {
                    parts.push(inst.instanceName + " = " + text(inst.type));
                });
                return head + " ｜ " + parts.join(" ｜ ");
            });

            // 警示条：主实例是否内存后端决定「进程内、重启即清空」这句话是否成立
            var backendNote = Vue.computed(function () {
                var list = instances.value || [];
                var main = null;
                var named = [];
                list.forEach(function (inst) {
                    if (inst.main === true) {
                        main = inst;
                    } else {
                        named.push(inst);
                    }
                });
                if (!main) {
                    return "";
                }
                var note = main.type === "memory"
                    ? "主实例 " + main.instanceName + " 为内存后端（进程内、重启即清空）"
                    : "主实例 " + main.instanceName + " 走 " + text(main.type);
                if (named.length) {
                    var namedParts = [];
                    named.forEach(function (inst) {
                        namedParts.push(inst.instanceName + "（" + text(inst.type) + "）");
                    });
                    note += " · 命名实例：" + namedParts.join("、");
                }
                return "⚠ 多后端演示 · " + note;
            });

            // ---- 抽屉 ----
            function openDrawer() {
                drawerOpen.value = true;
            }

            function closeDrawer() {
                drawerOpen.value = false;
            }

            function onKeydown(e) {
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

            // ---- 生命周期 ----
            Vue.onMounted(function () {
                document.addEventListener("keydown", onKeydown);
                load();
            });

            Vue.onUnmounted(function () {
                document.removeEventListener("keydown", onKeydown);
                document.body.style.overflow = "";
            });

            return {
                instances: instances,
                loading: loading,
                busy: busy,
                error: error,
                stale: stale,
                dataTime: dataTime,
                configError: configError,
                busyHint: busyHint,
                instancesSummary: instancesSummary,
                backendNote: backendNote,
                drawerOpen: drawerOpen,
                drawerRef: drawerRef,
                reload: reload,
                demoUrl: demoUrl,
                tabName: tabName,
                text: text,
                num: num,
                prefixText: prefixText,
                statusText: statusText,
                statusCls: statusCls,
                statusTitle: statusTitle,
                openDrawer: openDrawer,
                closeDrawer: closeDrawer
            };
        }
    });

    app.mount("#mq-index-app");
})();
