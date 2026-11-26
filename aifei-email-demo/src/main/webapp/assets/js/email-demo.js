(function () {
    'use strict';

    var EMPTY_TEXT = {
        sendResult: '尚未发送',
        channelInfo: '加载中…',
        configError: '—',
        depMissing: '—',
        templateResult: '—',
        state: '加载中…'
    };

    function el(id) { return document.getElementById(id); }
    function val(id) { return el(id).value.trim(); }
    function show(id, text) { el(id).textContent = text; }
    function showEmpty(id, text) { el(id).className = 'out empty'; el(id).textContent = text; }

    function getJSON(path) {
        return fetch(path, { method: 'GET' }).then(function (r) { return r.json(); });
    }

    function postJSON(path, body) {
        return fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (r) { return r.json(); });
    }

    function parseJSON(text, fallback) {
        try { return JSON.parse(text || '{}'); } catch (e) { return fallback; }
    }

    function refreshChannels() {
        return getJSON('/email/demo/channels').then(function (res) {
            var data = res.data || {};
            var names = data.channels || [];
            var select = el('channel');
            var current = select.value;
            select.innerHTML = '';
            names.forEach(function (name) {
                var opt = document.createElement('option');
                opt.value = name;
                opt.textContent = name + (name === data.default ? '（默认）' : '');
                select.appendChild(opt);
            });
            if (current) { select.value = current; }
            el('channelInfo').className = 'out';
            show('channelInfo', JSON.stringify(data, null, 2));
        });
    }

    function refreshState() {
        return getJSON('/email/demo/state').then(function (res) {
            el('state').className = 'out';
            show('state', JSON.stringify(res.data || res, null, 2));
        });
    }

    function sendBody(templateMode) {
        var bodyType = templateMode ? 'template' : val('bodyType');
        var body = {
            channel: val('channel'),
            to: templateMode ? val('templateTo') : val('to'),
            subject: templateMode ? ('模板邮件：' + val('templateName')) : val('subject'),
            bodyType: bodyType,
            content: val('content'),
            templateName: templateMode ? val('templateName') : val('sendTemplateName'),
            variables: parseJSON(templateMode ? val('templateVariables') : val('sendVariables'), {})
        };
        if (!templateMode) {
            body.attachmentName = val('attachmentName');
            body.attachmentContent = val('attachmentContent');
        }
        return body;
    }

    function resetAll() {
        Object.keys(EMPTY_TEXT).forEach(function (id) {
            if (id === 'state' || id === 'channelInfo') { return; }
            showEmpty(id, EMPTY_TEXT[id]);
        });
        el('templatePreview').className = 'preview empty';
        el('templatePreview').textContent = '渲染预览将显示在这里';
    }

    function bind() {
        el('sendBtn').onclick = function () {
            postJSON('/email/demo/send', sendBody(false)).then(function (res) {
                el('sendResult').className = 'out';
                show('sendResult', JSON.stringify(res, null, 2));
            });
        };
        el('refreshChannels').onclick = refreshChannels;
        el('configErrorBtn').onclick = function () {
            getJSON('/email/demo/config-error').then(function (res) {
                el('configError').className = 'out';
                show('configError', (res.data && res.data.error) || res.msg || JSON.stringify(res));
            });
        };
        el('depMissingBtn').onclick = function () {
            getJSON('/email/demo/dependency-missing').then(function (res) {
                el('depMissing').className = 'out';
                show('depMissing', (res.data && res.data.error) || res.msg || JSON.stringify(res));
            });
        };
        el('templateBtn').onclick = function () {
            postJSON('/email/demo/html-template', sendBody(true)).then(function (res) {
                var data = res.data || {};
                el('templateResult').className = 'out';
                show('templateResult', JSON.stringify({
                    channel: data.channel,
                    success: data.success,
                    message: data.message,
                    templateName: data.templateName
                }, null, 2));
                var preview = el('templatePreview');
                preview.className = data.renderedHtml ? 'preview' : 'preview empty';
                preview.innerHTML = data.renderedHtml || '模板不存在或为空';
                return refreshChannels();
            });
        };
        el('resetBtn').onclick = function () {
            resetAll();
            return Promise.all([refreshChannels(), refreshState()]);
        };
    }

    bind();
    resetAll();
    Promise.all([refreshChannels(), refreshState()]);
})();
