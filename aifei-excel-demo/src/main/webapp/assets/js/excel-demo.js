/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
(function () {
    'use strict';

    var BASE = '/excel/demo';

    function envelope(resp) {
        return resp.json();
    }

    function checkStatus(resp) {
        if (!resp.ok) {
            throw new Error('HTTP ' + resp.status);
        }
        return resp;
    }

    var app = Vue.createApp({
        data: function () {
            return {
                state: null,
                stateLoading: false,
                stateError: '',
                selectedFile: null,
                batchSize: 1000,
                parseLoading: false,
                parseError: '',
                parseResult: null,
                streamRows: 50000
            };
        },
        computed: {
            stateChipCls: function () {
                if (!this.state) {
                    return 'chip chip-ghost';
                }
                return this.state.easyExcelAvailable ? 'chip chip-ok' : 'chip chip-err';
            },
            stateChipText: function () {
                if (!this.state) {
                    return '依赖状态未知';
                }
                return this.state.easyExcelAvailable ? '内核可用' : '内核缺失';
            },
            sampleHeaders: function () {
                if (!this.parseResult || !this.parseResult.sample || !this.parseResult.sample.length) {
                    return [];
                }
                var width = this.parseResult.sample[0].length;
                var heads = [];
                for (var i = 0; i < width; i++) {
                    heads.push(this.parseResult.headers && this.parseResult.headers[i]
                        ? this.parseResult.headers[i] : ('列' + (i + 1)));
                }
                return heads;
            }
        },
        mounted: function () {
            this.loadState();
        },
        methods: {
            loadState: function () {
                var self = this;
                self.stateLoading = true;
                self.stateError = '';
                fetch(BASE + '/state', { method: 'GET' })
                    .then(checkStatus)
                    .then(envelope)
                    .then(function (env) {
                        if (env.code !== 0) {
                            throw new Error(env.msg || '状态读取失败');
                        }
                        self.state = env.data;
                    })
                    .catch(function (e) {
                        self.stateError = '依赖状态读取失败：' + e.message
                            + ' —— 请确认 demo 已在 18150 端口启动。';
                    })
                    .finally(function () {
                        self.stateLoading = false;
                    });
            },
            onFileChange: function (e) {
                this.selectedFile = e.target.files && e.target.files[0] ? e.target.files[0] : null;
                this.parseError = '';
            },
            parseFile: function () {
                var self = this;
                if (!self.selectedFile) {
                    return;
                }
                self.parseLoading = true;
                self.parseError = '';
                self.parseResult = null;
                var form = new FormData();
                form.append('file', self.selectedFile);
                form.append('batchSize', String(self.batchSize || 1000));
                fetch(BASE + '/parse', { method: 'POST', body: form })
                    .then(checkStatus)
                    .then(envelope)
                    .then(function (env) {
                        if (env.code !== 0) {
                            throw new Error(env.msg || '解析失败');
                        }
                        self.parseResult = env.data;
                    })
                    .catch(function (e) {
                        self.parseError = '解析失败：' + e.message;
                    })
                    .finally(function () {
                        self.parseLoading = false;
                    });
            },
            exportOnce: function () {
                window.location.href = BASE + '/export-once';
            },
            exportStream: function () {
                var rows = this.streamRows > 0 ? this.streamRows : 50000;
                window.location.href = BASE + '/export-stream?rows=' + rows;
            }
        }
    });

    app.mount('#excel-demo-app');
})();
