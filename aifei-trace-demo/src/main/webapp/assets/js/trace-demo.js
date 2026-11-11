/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

/**
 * aifei-trace-demo 页面脚本：4 个演示场景的交互（档案 A · Aifei 静态层，全局 Vue，无构建工具）。
 *
 * 统一响应信封：后端 Out 为 { code, msg, data }，HTTP 恒 200；非 0 视为业务失败并把 msg 展示给用户。
 */
(function () {
    'use strict';

    var API_BASE = '/trace/demo';

    /** 统一请求封装：解析响应信封，非 0 抛业务错误 */
    function request(path) {
        return fetch(API_BASE + path, { credentials: 'same-origin' }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP 状态 ' + response.status + '（预期恒 200）');
            }
            return response.json();
        }).then(function (envelope) {
            if (!envelope || typeof envelope.code !== 'number') {
                throw new Error('响应缺少 code 字段，信封不符合 { code, msg, data } 契约');
            }
            if (envelope.code !== 0) {
                throw new Error(envelope.msg || '业务失败（code=' + envelope.code + '）');
            }
            return envelope.data;
        });
    }

    /** 带自定义请求头的请求封装（场景④使用） */
    function requestWithHeader(path, headers) {
        return fetch(API_BASE + path, {
            method: 'GET',
            headers: headers,
            credentials: 'same-origin'
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP 状态 ' + response.status + '（预期恒 200）');
            }
            return response.json();
        }).then(function (envelope) {
            if (!envelope || typeof envelope.code !== 'number') {
                throw new Error('响应缺少 code 字段，信封不符合 { code, msg, data } 契约');
            }
            if (envelope.code !== 0) {
                throw new Error(envelope.msg || '业务失败（code=' + envelope.code + '）');
            }
            return envelope.data;
        });
    }

    /** 布尔字段兼容判断（后端可能传 0/1 或 true/false） */
    function isTruthy(value) {
        return value === true || value === 1 || value === '1' || value === 'true';
    }

    /** 当前时间标签 */
    function nowLabel() {
        var d = new Date();
        function pad(n) { return n < 10 ? '0' + n : '' + n; }
        return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    }

    var app = Vue.createApp({
        data: function () {
            return {
                // 装配状态
                state: null,
                stateLoading: false,
                stateError: '',

                // 场景①
                currentBusy: false,
                currentError: '',
                currentData: null,
                currentHistory: [],

                // 场景②
                logBusy: false,
                logError: '',
                logData: null,

                // 场景③
                asyncBusy: false,
                asyncError: '',
                asyncData: null,

                // 场景④
                extractBusy: false,
                extractError: '',
                extractValue: '',
                extractData: null,
                extractHistory: []
            };
        },

        computed: {
            backendText: function () {
                return this.state ? '装配已确认' : (this.stateError ? '后端不可达' : '等待读取装配状态');
            },
            backendCls: function () {
                return this.state ? 'chip-ok' : (this.stateError ? 'chip-err' : 'chip-ghost');
            },
            /** 场景②控制台样本文本 */
            consoleText: function () {
                if (!this.logData || !this.logData.consoleLines || !this.logData.consoleLines.length) {
                    return '';
                }
                return this.logData.consoleLines.join('\n');
            },
            /** 场景③子线程日志行（过滤出含 trace-demo-worker 的行） */
            childLogText: function () {
                if (!this.asyncData || !this.asyncData.consoleLines) {
                    return '';
                }
                var lines = this.asyncData.consoleLines.filter(function (line) {
                    return line.indexOf('trace-demo-worker') !== -1;
                });
                return lines.join('\n');
            }
        },

        methods: {
            // ===== 装配状态 =====
            loadState: function (silent) {
                var self = this;
                if (!silent) {
                    self.stateLoading = true;
                }
                self.stateError = '';
                return request('/state').then(function (data) {
                    self.state = data;
                }).catch(function (err) {
                    self.stateError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.stateLoading = false;
                });
            },

            // ===== 场景① =====
            loadCurrent: function () {
                var self = this;
                self.currentBusy = true;
                self.currentError = '';
                request('/current').then(function (data) {
                    self.currentData = data;
                    self.currentHistory.push({
                        traceId: data.traceId,
                        spanId: data.spanId,
                        at: nowLabel()
                    });
                }).catch(function (err) {
                    self.currentError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.currentBusy = false;
                });
            },

            loadSpan: function () {
                var self = this;
                self.currentBusy = true;
                self.currentError = '';
                request('/span').then(function (data) {
                    self.currentData = data;
                    self.currentHistory.push({
                        traceId: data.traceId,
                        spanId: data.spanId,
                        at: nowLabel()
                    });
                }).catch(function (err) {
                    self.currentError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.currentBusy = false;
                });
            },

            // ===== 场景② =====
            writeLog: function () {
                var self = this;
                self.logBusy = true;
                self.logError = '';
                request('/log').then(function (data) {
                    self.logData = data;
                }).catch(function (err) {
                    self.logError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.logBusy = false;
                });
            },

            clearLog: function () {
                var self = this;
                self.logBusy = true;
                self.logError = '';
                request('/log/clear').then(function (data) {
                    self.logData = data;
                }).catch(function (err) {
                    self.logError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.logBusy = false;
                });
            },

            // ===== 场景③ =====
            runAsync: function () {
                var self = this;
                self.asyncBusy = true;
                self.asyncError = '';
                request('/async').then(function (data) {
                    // 布尔字段兼容判断
                    data.sameTraceId = isTruthy(data.sameTraceId);
                    data.childSpanIdNull = isTruthy(data.childSpanIdNull);
                    data.clearedProperly = isTruthy(data.clearedProperly);
                    self.asyncData = data;
                }).catch(function (err) {
                    self.asyncError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.asyncBusy = false;
                });
            },

            // ===== 场景④ =====
            runExtract: function (mode) {
                var self = this;
                self.extractBusy = true;
                self.extractError = '';

                var sent = '';
                var headers = {};
                if (mode === 'custom') {
                    sent = self.extractValue.trim();
                    headers['X-Trace-Id'] = sent;
                } else if (mode === 'blank') {
                    sent = '   ';
                    headers['X-Trace-Id'] = sent;
                }
                // mode === 'missing'：不携带该头

                var modeText = mode === 'custom' ? '携带自定义值'
                        : (mode === 'blank' ? '携带空白值' : '不携带该头');

                requestWithHeader('/extract', headers).then(function (data) {
                    // 判定：发送了非空白值且服务端 traceId 与发送值一致 → 提取贯通；否则回退生成
                    var sentTrim = sent.trim();
                    data.extracted = sentTrim !== '' && data.traceId === sentTrim;
                    data.modeText = modeText;
                    data.sent = sent;
                    self.extractData = data;
                    self.extractHistory.push({
                        modeText: modeText,
                        sent: sent,
                        traceId: data.traceId,
                        extracted: data.extracted,
                        at: nowLabel()
                    });
                }).catch(function (err) {
                    self.extractError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.extractBusy = false;
                });
            }
        },

        mounted: function () {
            this.loadState(false);
        }
    });

    app.mount('#trace-demo-app');
})();
