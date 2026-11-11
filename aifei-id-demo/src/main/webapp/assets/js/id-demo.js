/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

/**
 * aifei-id-demo 页面脚本：4 个功能块的交互（档案 A · Aifei 静态层，全局 Vue，无构建工具）。
 *
 * 统一响应信封：后端 Out 为 { code, msg, data }，HTTP 恒 200；非 0 视为业务失败并把 msg 展示给用户。
 *
 * ID 纪律：ID 一律以字符串形态流转，绝不 parseInt / Number / 隐式转数字（19 位 long 超出 2^53 会静默丢精度）。
 * 需要位运算时用 BigInt，且只用于「demo 自算展示」。
 */
(function () {
    'use strict';

    var API_BASE = '/id/demo';

    /** 连续生成次数 */
    var MANY_COUNT = 20;

    /** 历史表最大保留行数（防止 DOM 无限增长） */
    var HISTORY_LIMIT = 100;

    /** 雪花 EPOCH：2024-01-01T00:00:00Z，与模块 internal.SnowflakeIdGenerator 一致（仅用于 demo 展示换算） */
    var EPOCH_MILLIS = 1704067200000;

    /** 统一请求封装：解析响应信封，非 0 抛业务错误（消息承载失败原因） */
    function request(path) {
        return fetch(API_BASE + path, { credentials: 'same-origin' }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP 状态 ' + response.status + '（契约要求恒 200）');
            }
            return response.json();
        }).then(function (envelope) {
            if (!envelope || typeof envelope.code !== 'number') {
                throw new Error('响应缺少 code 字段，信封不符合 { code, msg, data } 契约');
            }
            if (envelope.code !== 0) {
                throw new Error(envelope.msg || envelope.message || ('业务失败（code=' + envelope.code + '）'));
            }
            return envelope.data;
        });
    }

    /** 布尔字段兼容判断（后端可能传 0/1 或 true/false） */
    function isTruthy(value) {
        return value === true || value === 1 || value === '1' || value === 'true';
    }

    /** 是否为纯十进制数字串（ID 合法性判定） */
    function isDigits(value) {
        return typeof value === 'string' && /^[0-9]+$/.test(value);
    }

    /**
     * ID 值转展示字符串。字符串原样返回；若后端误以数字下发，仅做展示转换、绝不参与任何算术。
     */
    function idText(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return typeof value === 'string' ? value : String(value);
    }

    /** 非负整数参数归一化（用于 size / step 这类「数量」，不用于 ID） */
    function normalizeCount(value, fallback, max) {
        var text = (value === null || value === undefined) ? '' : String(value).trim();
        if (!/^[0-9]+$/.test(text)) {
            return null;
        }
        var num = Number(text);
        if (!isFinite(num) || num < 1) {
            return null;
        }
        return num > max ? max : num;
    }

    function pad(value, width) {
        var text = String(value);
        while (text.length < width) {
            text = '0' + text;
        }
        return text;
    }

    /** 毫秒时间戳 → YYYY-MM-DD HH:mm:ss.SSS */
    function formatDateTime(millis) {
        var d = new Date(millis);
        return d.getFullYear() + '-' + pad(d.getMonth() + 1, 2) + '-' + pad(d.getDate(), 2) + ' '
            + pad(d.getHours(), 2) + ':' + pad(d.getMinutes(), 2) + ':' + pad(d.getSeconds(), 2)
            + '.' + pad(d.getMilliseconds(), 3);
    }

    /** 客户端时刻标签 HH:mm:ss.SSS */
    function nowLabel() {
        var d = new Date();
        return pad(d.getHours(), 2) + ':' + pad(d.getMinutes(), 2) + ':' + pad(d.getSeconds(), 2)
            + '.' + pad(d.getMilliseconds(), 3);
    }

    /** 高精度计时（毫秒，保留 1 位小数） */
    function elapsedSince(start) {
        var end = (window.performance && window.performance.now) ? window.performance.now() : Date.now();
        return Math.round((end - start) * 10) / 10;
    }

    function timeMark() {
        return (window.performance && window.performance.now) ? window.performance.now() : Date.now();
    }

    /**
     * 雪花位拆解（demo 自算展示，非模块 API）：[1 符号][41 时间戳][10 workerId][12 序列号]。
     * 必须用 BigInt —— 19 位十进制超出 Number.MAX_SAFE_INTEGER。
     */
    function decomposeId(idStr) {
        if (!isDigits(idStr) || typeof BigInt !== 'function') {
            return null;
        }
        var v = BigInt(idStr);
        var binary = v.toString(2);
        while (binary.length < 64) {
            binary = '0' + binary;
        }
        var tsOffset = (v >> 22n) & ((1n << 41n) - 1n);
        return {
            binary: binary,
            signBits: binary.slice(0, 1),
            tsBits: binary.slice(1, 42),
            workerBits: binary.slice(42, 52),
            seqBits: binary.slice(52, 64),
            sign: ((v >> 63n) & 1n).toString(),
            tsOffset: tsOffset.toString(),
            // 时间戳相对值 < 2^41，转 Number 无精度风险；这里转的是「时间值」而不是 ID
            absTime: formatDateTime(EPOCH_MILLIS + Number(tsOffset)),
            workerId: ((v >> 12n) & 1023n).toString(),
            sequence: (v & 4095n).toString()
        };
    }

    var app = Vue.createApp({
        data: function () {
            return {
                // 装配状态（功能块④ + 顶部）
                state: null,
                stateLoading: false,
                stateError: '',

                // 功能块①
                nextBusy: false,
                busyKind: '',
                nextError: '',
                nextData: null,
                nextHistory: [],
                progress: 0,
                manyCount: MANY_COUNT,
                bigIntOk: typeof BigInt === 'function',

                // 功能块②
                batchBusy: false,
                batchError: '',
                batchData: null,
                batchSize: 2000,

                // 功能块③
                segBusy: false,
                segError: '',
                segData: null,
                segSize: 450
            };
        },

        computed: {
            stateLoaded: function () {
                return this.state !== null;
            },
            installed: function () {
                return this.stateLoaded && isTruthy(this.state.installed);
            },
            notInstalled: function () {
                return this.stateLoaded && !this.installed;
            },
            installedChipText: function () {
                if (this.stateLoaded) {
                    return this.installed ? '模块已装配' : '模块未装配';
                }
                return this.stateError ? '状态读取失败' : '等待装配状态';
            },
            installedChipCls: function () {
                if (this.stateLoaded) {
                    return this.installed ? 'chip-ok' : 'chip-err';
                }
                return this.stateError ? 'chip-err' : 'chip-ghost';
            },
            isSegment: function () {
                return this.stateLoaded && this.state.strategy === 'segment';
            },
            isSnowflakeStrategy: function () {
                return this.stateLoaded && this.state.strategy === 'snowflake';
            },
            /** 位拆解仅在「已确认为雪花策略」且浏览器支持 BigInt 时进行，避免把号段 ID 误拆 */
            decompositionEnabled: function () {
                return this.isSnowflakeStrategy && this.bigIntOk;
            },
            nextLoadingText: function () {
                return (this.nextBusy && this.busyKind === 'one') ? '生成中…' : '生成一个 ID';
            },
            manyLoadingText: function () {
                return (this.nextBusy && this.busyKind === 'many')
                    ? '连续生成中…'
                    : ('连续生成 ' + this.manyCount + ' 次');
            },
            uniqueOk: function () {
                return !!this.batchData && this.batchData.unique === this.batchData.size;
            },
            dupOk: function () {
                return !!this.batchData && this.batchData.duplicated === 0;
            },
            segments: function () {
                return (this.segData && this.segData.segments) ? this.segData.segments : [];
            },
            workerHintText: function () {
                if (!this.stateLoaded) {
                    return '';
                }
                if (this.isSegment) {
                    return '当前为号段策略（segment）：不涉及 workerId，唯一性靠 id.segment.key 域内的共享计数器保证；'
                        + '跨不同 key 的号段序列互不相干，跨域不保证唯一。';
                }
                var source = this.state.workerSource;
                if (source === 'config') {
                    return 'workerId 来源 = config（默认、零外部依赖）：取自 id.workerId 静态配置。'
                        + '多实例部署必须配不同 workerId —— 模块在 config 模式无共享状态，重复 workerId 必然重号且启动期检测不到。';
                }
                if (source === 'cache') {
                    return 'workerId 来源 = cache：经 Counter 原子自增后取模 1024 取得。无续期、无后台线程；'
                        + '累计冷启动超过 1024 次会与存活实例撞号（TTL 365 天窗口内的取模回绕风险），需运维关注。';
                }
                if (source === 'lock') {
                    return 'workerId 来源 = lock：按 0..1023 升序 tryLock 抢占租约并长期持有，'
                        + '后台线程 aifei-id-worker-lease 周期续期；续期丢失即置 healthy=false 并停止发号（不可自愈，避免重号）。'
                        + '该模式依赖 aifei-lock + aifei-cache 两个模块。';
                }
                return '未识别的 workerSource：'
                    + (source === null || source === undefined ? '（后端未返回）' : String(source));
            },
            workerHintCls: function () {
                if (this.isSegment) {
                    return 'alert-info';
                }
                var source = this.stateLoaded ? this.state.workerSource : '';
                return (source === 'config' || source === 'cache') ? 'alert-warn' : 'alert-info';
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
                    // 失败即清空，避免展示过期状态
                    self.state = null;
                    self.stateError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.stateLoading = false;
                });
            },

            /** 状态字段展示（缺失显示 —） */
            stateText: function (key) {
                if (!this.stateLoaded) {
                    return '—';
                }
                var value = this.state[key];
                return (value === null || value === undefined || value === '') ? '—' : String(value);
            },

            // ===== 功能块①：单号生成 =====
            /** 追加一行历史，返回该行（delta / 递增判定为 demo 自算） */
            appendNext: function (data, elapsed) {
                var idStr = idText(data && data.id);
                var dec = this.decompositionEnabled ? decomposeId(idStr) : null;
                var prev = this.nextHistory.length ? this.nextHistory[this.nextHistory.length - 1] : null;

                var delta = null;
                var increasing = null;
                if (prev && isDigits(prev.id) && isDigits(idStr)) {
                    var a = BigInt(prev.id);
                    var b = BigInt(idStr);
                    delta = (b - a).toString();
                    increasing = b > a;
                }

                var row = {
                    id: idStr,
                    length: idStr.length,
                    dec: dec,
                    delta: delta,
                    increasing: increasing,
                    firstSeen: isTruthy(data && data.firstSeen),
                    elapsed: elapsed,
                    at: nowLabel()
                };

                var list = this.nextHistory.concat([row]);
                if (list.length > HISTORY_LIMIT) {
                    list = list.slice(list.length - HISTORY_LIMIT);
                }
                this.nextHistory = list;
                return row;
            },

            generateOne: function () {
                var self = this;
                if (self.nextBusy) {
                    return;
                }
                self.nextBusy = true;
                self.busyKind = 'one';
                self.nextError = '';

                var mark = timeMark();
                request('/next').then(function (data) {
                    var row = self.appendNext(data, elapsedSince(mark));
                    self.nextData = {
                        id: row.id,
                        length: row.length,
                        dec: row.dec,
                        firstSeen: row.firstSeen,
                        elapsed: row.elapsed
                    };
                }).catch(function (err) {
                    self.nextError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.nextBusy = false;
                    self.busyKind = '';
                });
            },

            /** 连续生成 N 次：串行请求，便于观察严格递增与跨毫秒 */
            generateMany: function () {
                var self = this;
                if (self.nextBusy) {
                    return;
                }
                self.nextBusy = true;
                self.busyKind = 'many';
                self.nextError = '';
                self.progress = 0;

                var chain = Promise.resolve();
                for (var i = 0; i < MANY_COUNT; i++) {
                    chain = chain.then(function () {
                        var mark = timeMark();
                        return request('/next').then(function (data) {
                            var row = self.appendNext(data, elapsedSince(mark));
                            self.nextData = {
                                id: row.id,
                                length: row.length,
                                dec: row.dec,
                                firstSeen: row.firstSeen,
                                elapsed: row.elapsed
                            };
                            self.progress = self.progress + 1;
                        });
                    });
                }

                chain.catch(function (err) {
                    self.nextError = (err && err.message ? err.message : String(err)) + '（连续生成中途失败，已中止）';
                }).finally(function () {
                    self.nextBusy = false;
                    self.busyKind = '';
                    self.progress = 0;
                });
            },

            clearNextHistory: function () {
                this.nextHistory = [];
            },

            // ===== 功能块②：并发批量生成去重校验 =====
            runBatch: function () {
                var self = this;
                if (self.batchBusy) {
                    return;
                }
                // size 是「数量」而非 ID，可安全按数值处理
                var size = normalizeCount(self.batchSize, 2000, 200000);
                self.batchError = '';
                if (size === null) {
                    self.batchError = 'size 必须是 1..200000 的整数，当前值：' + String(self.batchSize);
                    return;
                }
                self.batchSize = size;
                self.batchBusy = true;

                request('/batch?size=' + size).then(function (data) {
                    self.batchData = {
                        size: data.size,
                        unique: data.unique,
                        duplicated: data.duplicated,
                        costMillis: data.costMillis,
                        first: idText(data.first),
                        last: idText(data.last)
                    };
                }).catch(function (err) {
                    self.batchError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.batchBusy = false;
                });
            },

            // ===== 功能块③：号段耗尽与新段获取 =====
            runSegment: function () {
                var self = this;
                if (self.segBusy) {
                    return;
                }
                var size = normalizeCount(self.segSize, 450, 200000);
                self.segError = '';
                if (size === null) {
                    self.segError = 'size 必须是 1..200000 的整数，当前值：' + String(self.segSize);
                    return;
                }
                self.segSize = size;
                self.segBusy = true;

                request('/segment?size=' + size).then(function (data) {
                    var segs = (data.segments || []).map(function (seg) {
                        return {
                            index: idText(seg.index),
                            // 段边界同样是 ID 值：契约上按字符串处理（雪花策略下数值可能超出 2^53）
                            start: idText(seg.start),
                            end: idText(seg.end),
                            count: (seg.count === null || seg.count === undefined) ? 0 : seg.count
                        };
                    });
                    self.segData = {
                        step: data.step,
                        stepNum: Number(data.step),   // step 是「段长」数量，非 ID
                        generated: data.generated,
                        currentSegment: idText(data.currentSegment),
                        segments: segs
                    };
                }).catch(function (err) {
                    self.segError = err && err.message ? err.message : String(err);
                }).finally(function () {
                    self.segBusy = false;
                });
            },

            isCurrentSegmentRow: function (seg) {
                return !!this.segData && idText(seg.index) === idText(this.segData.currentSegment);
            },

            segRowText: function (seg) {
                if (this.isCurrentSegmentRow(seg)) {
                    return '正在使用中（当前段）';
                }
                var stepNum = this.segData ? this.segData.stepNum : 0;
                if (stepNum && Number(seg.count) === stepNum) {
                    return '段已填满（已耗尽 → 已取新段）';
                }
                return '历史段（本批未填满）';
            },

            segRowCls: function (seg) {
                if (this.isCurrentSegmentRow(seg)) {
                    return '';
                }
                var stepNum = this.segData ? this.segData.stepNum : 0;
                return (stepNum && Number(seg.count) === stepNum) ? 'txt-ok' : 'txt-warn';
            }
        },

        mounted: function () {
            this.loadState(false);
        }
    });

    app.mount('#id-demo-app');
})();
