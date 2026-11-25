(function () {
    'use strict';

    var BASE = '/oss/demo';
    var logs = [];
    var nextMarker = '';
    var hasMore = false;

    function $(id) { return document.getElementById(id); }

    function ok(res) { return !!res && res.code === 0; }

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function fmtSize(value) {
        if (typeof value !== 'number' || !isFinite(value)) return '-';
        if (value < 1024) return value + ' B';
        if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
        return (value / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function now() {
        var d = new Date();
        function pad(n) { return n < 10 ? '0' + n : String(n); }
        return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    }

    function textValue(data) {
        if (data == null) return '';
        return typeof data === 'string' ? data : JSON.stringify(data, null, 2);
    }

    function showToast(res) {
        var el = $('toast');
        el.textContent = (ok(res) ? '成功' : '失败') + ' · ' + (res.msg || (ok(res) ? 'OK' : '未知错误'));
        el.className = 'toast ' + (ok(res) ? 'ok' : 'err');
    }

    function addLog(endpoint, params, res, cost, url) {
        logs.unshift({
            time: now(),
            endpoint: endpoint,
            params: params || {},
            code: res.code,
            msg: res.msg || '',
            cost: Math.round(cost),
            raw: JSON.stringify({ url: url, response: res }),
            url: url
        });
        if (logs.length > 50) logs.pop();
        renderLogs();
    }

    function renderLogs() {
        var box = $('logList');
        if (!logs.length) {
            box.innerHTML = '<div class="log-empty">暂无调用记录</div>';
            return;
        }
        box.innerHTML = logs.map(function (item) {
            return '<div class="log-item">' +
                '<div class="log-head">' +
                    '<span class="log-time">' + esc(item.time) + '</span>' +
                    '<span class="log-endpoint">/' + esc(item.endpoint) + '</span>' +
                    '<span class="log-msg">' + esc(JSON.stringify(item.params)) + '</span>' +
                    '<span class="log-cost">' + esc(item.cost) + ' ms</span>' +
                    '<span class="log-code' + (item.code === 0 ? '' : ' err') + '">code ' + esc(item.code) + '</span>' +
                '</div>' +
                (item.msg ? '<div class="log-msg">' + esc(item.msg) + '</div>' : '') +
                '<details><summary>原始响应</summary><pre>' + esc(item.raw) + '</pre></details>' +
            '</div>';
        }).join('');
    }

    function call(endpoint, params) {
        var query = new URLSearchParams();
        Object.keys(params || {}).forEach(function (key) {
            var value = params[key];
            if (value !== undefined && value !== null && value !== '') query.append(key, String(value));
        });
        var qs = query.toString();
        var url = BASE + '/' + endpoint + (qs ? '?' + qs : '');
        var start = performance.now();

        return fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' } })
            .then(function (response) { return response.json(); })
            .then(function (res) {
                addLog(endpoint, params, res, performance.now() - start, url);
                showToast(res);
                return res;
            })
            .catch(function (err) {
                var res = { code: -1, msg: '请求失败：' + err.message, data: null };
                addLog(endpoint, params, res, performance.now() - start, url);
                showToast(res);
                return res;
            });
    }

    function updatePageState(marker, truncated, directories) {
        nextMarker = marker || '';
        hasMore = !!truncated;
        $('nextBtn').disabled = !hasMore;
        var dirs = directories && directories.length ? directories.join('、') : '无';
        $('pageHint').textContent = '游标：' + (nextMarker || '空') + ' · ' +
            (hasMore ? '仍有下一页' : '已到末页') + ' · 子目录：' + dirs;
    }

    function renderFiles(files) {
        var tbody = $('fileRows');
        if (!files || !files.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="empty">当前目录下没有对象</td></tr>';
            return;
        }
        tbody.innerHTML = files.map(function (file) {
            var path = esc(file.path || '');
            var url = file.url
                ? '<a href="' + esc(file.url) + '" target="_blank" rel="noopener">打开</a>'
                : '-';
            return '<tr>' +
                '<td class="path-cell">' + path + '</td>' +
                '<td>' + esc(fmtSize(file.size)) + '</td>' +
                '<td class="url-cell">' + url + '</td>' +
                '<td>' +
                    '<button type="button" class="btn btn-small" data-act="view" data-path="' + path + '">查看</button> ' +
                    '<button type="button" class="btn btn-small" data-act="copy" data-path="' + path + '">复制</button> ' +
                    '<button type="button" class="btn btn-small" data-act="url" data-path="' + path + '">URL</button> ' +
                    '<button type="button" class="btn btn-small btn-danger" data-act="delete" data-path="' + path + '">删除</button>' +
                '</td>' +
            '</tr>';
        }).join('');
    }

    function loadState() {
        return call('state', {}).then(function (res) {
            if (!ok(res)) {
                $('stateBadge').textContent = '状态读取失败';
                $('stateBadge').className = 'badge badge-err';
                return;
            }
            var platforms = (res.data && res.data.platforms) || [];
            $('platformText').textContent = platforms.length ? platforms.join('、') : '无';
            $('stateBadge').textContent = '就绪 · ' + platforms.length + ' 个平台';
            $('stateBadge').className = 'badge badge-ok';
        });
    }

    function loadList() {
        return call('list', { path: $('listPath').value }).then(function (res) {
            if (!ok(res)) return;
            renderFiles(res.data);
            updatePageState('', false, []);
        });
    }

    function loadPage() {
        var maxFiles = parseInt($('maxFiles').value, 10);
        if (!maxFiles || maxFiles < 1) maxFiles = 2;
        return call('page', {
            path: $('listPath').value,
            maxFiles: maxFiles,
            marker: nextMarker
        }).then(function (res) {
            if (!ok(res)) return;
            var data = res.data || {};
            renderFiles(data.files);
            updatePageState(data.nextMarker, data.truncated, data.directories);
        });
    }

    function upload(endpoint) {
        return call(endpoint, {
            path: $('uploadPath').value,
            text: $('uploadText').value
        }).then(function (res) {
            if (ok(res)) loadList();
        });
    }

    function download() {
        return call('download', { path: $('viewPath').value }).then(function (res) {
            if (ok(res)) $('contentResult').value = textValue(res.data);
        });
    }

    function checkExists() {
        return call('exists', { path: $('viewPath').value }).then(function (res) {
            if (!ok(res)) return;
            $('existsResult').textContent = res.data === true ? '状态：存在' : '状态：不存在';
        });
    }

    function copyObject() {
        return call('copy', { src: $('copySrc').value, dest: $('copyDest').value })
            .then(function (res) { if (ok(res)) loadList(); });
    }

    function deleteObject() {
        var path = $('deletePath').value;
        if (!window.confirm('确认删除对象：' + path + '？')) return Promise.resolve(null);
        return call('delete', { path: path })
            .then(function (res) { if (ok(res)) loadList(); });
    }

    function generateUrl() {
        return call('url', { path: $('urlPath').value, ttl: $('urlTtl').value })
            .then(function (res) {
                var box = $('urlResult');
                if (!ok(res)) {
                    box.textContent = '生成失败：' + (res.msg || '未知错误');
                    return;
                }
                var link = String(res.data || '');
                box.innerHTML = '<a href="' + esc(link) + '" target="_blank" rel="noopener">' + esc(link) + '</a>';
            });
    }

    $('refreshStateBtn').addEventListener('click', loadState);
    $('listBtn').addEventListener('click', loadList);
    $('pageBtn').addEventListener('click', function () {
        updatePageState('', false, []);
        loadPage();
    });
    $('nextBtn').addEventListener('click', loadPage);
    $('resetPageBtn').addEventListener('click', function () {
        updatePageState('', false, []);
    });
    $('uploadBtn').addEventListener('click', function () { upload('upload'); });
    $('uploadIfAbsentBtn').addEventListener('click', function () { upload('upload-if-absent'); });
    $('downloadBtn').addEventListener('click', download);
    $('existsBtn').addEventListener('click', checkExists);
    $('copyContentBtn').addEventListener('click', function () {
        var text = $('contentResult').value;
        if (!text) return;
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                showToast({ code: 0, msg: '内容已复制' });
            });
            return;
        }
        $('contentResult').select();
        document.execCommand('copy');
    });
    $('copyBtn').addEventListener('click', copyObject);
    $('deleteBtn').addEventListener('click', deleteObject);
    $('urlBtn').addEventListener('click', generateUrl);
    $('clearLogsBtn').addEventListener('click', function () {
        logs = [];
        renderLogs();
    });

    $('fileRows').addEventListener('click', function (event) {
        var button = event.target.closest('button[data-act]');
        if (!button) return;
        var path = button.getAttribute('data-path');
        var action = button.getAttribute('data-act');
        if (action === 'view') {
            $('viewPath').value = path;
            download();
        } else if (action === 'copy') {
            $('copySrc').value = path;
        } else if (action === 'url') {
            $('urlPath').value = path;
            generateUrl();
        } else if (action === 'delete') {
            $('deletePath').value = path;
            deleteObject();
        }
    });

    loadState();
    loadList();
}());
