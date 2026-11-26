(function () {
    'use strict';

    function el(id) { return document.getElementById(id); }
    function val(id) { return el(id).value.trim(); }
    function show(id, text) { el(id).textContent = text; }

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

    function postForm(path, params) {
        var form = new URLSearchParams();
        Object.keys(params).forEach(function (key) { form.append(key, params[key] || ''); });
        return fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: form.toString()
        }).then(function (r) { return r.text(); });
    }

    function refreshChannels() {
        return getJSON('/sms/demo/channels').then(function (res) {
            var data = res.data || {};
            var select = el('channel');
            var names = data.channels || [];
            select.innerHTML = '';
            names.forEach(function (name) {
                var opt = document.createElement('option');
                opt.value = name;
                opt.textContent = name + (name === data.default ? '（默认）' : '');
                select.appendChild(opt);
            });
            show('channelInfo', JSON.stringify(data, null, 2));
        });
    }

    function refreshReceipts() {
        return getJSON('/sms/demo/receipts').then(function (res) {
            var receipts = (res.data && res.data.receipts) || [];
            var node = el('receiptList');
            if (receipts.length === 0) {
                node.className = 'out empty';
                node.textContent = '暂无回执记录';
            } else {
                node.className = 'out';
                node.textContent = JSON.stringify(receipts, null, 2);
            }
        });
    }

    function refreshState() {
        return getJSON('/sms/demo/state').then(function (res) {
            show('state', JSON.stringify(res.data || res, null, 2));
        });
    }

    function bind() {
        el('sendBtn').onclick = function () {
            var variables = {};
            try { variables = JSON.parse(val('variables') || '{}'); } catch (e) { variables = {}; }
            postJSON('/sms/demo/send', {
                channel: val('channel'),
                phone: val('phone'),
                templateId: val('templateId'),
                variables: variables
            }).then(function (res) { show('sendResult', JSON.stringify(res, null, 2)); });
        };
        el('refreshChannels').onclick = refreshChannels;
        el('configErrorBtn').onclick = function () {
            getJSON('/sms/demo/config-error').then(function (res) {
                show('configError', (res.data && res.data.error) || res.msg || JSON.stringify(res));
            });
        };
        el('depMissingBtn').onclick = function () {
            getJSON('/sms/demo/dependency-missing').then(function (res) {
                show('depMissing', (res.data && res.data.error) || res.msg || JSON.stringify(res));
            });
        };
        el('callbackBtn').onclick = function () {
            var params = {
                ResultCode: val('receiptStatus'),
                MessageId: val('receiptId'),
                PhoneNumber: val('phone'),
                ReportTime: '2026-01-01 10:00:00'
            };
            postJSON('/sms/demo/receipt-sign', params).then(function (signed) {
                var signature = (signed.data && signed.data.signature) || 'invalid-signature';
                if (el('badSignature').value === 'true') { signature = 'invalid-signature'; }
                params.signature = signature;
                var channel = val('channel') || 'aliyun';
                return postForm('/sms/callback/' + channel, params);
            }).then(function (text) {
                show('callbackResult', text);
                return refreshReceipts();
            });
        };
        el('refreshReceipts').onclick = refreshReceipts;
        el('resetBtn').onclick = function () {
            postJSON('/sms/demo/reset', {}).then(function () { return refreshReceipts(); });
        };
    }

    bind();
    postJSON('/sms/demo/reset', {}).then(function () {
        return Promise.all([refreshChannels(), refreshReceipts(), refreshState()]);
    });
})();
