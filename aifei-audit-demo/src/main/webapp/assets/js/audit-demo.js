(function () {
  "use strict";

  var API = "/audit/demo";
  var MAX_CARDS = 80;

  var DOM = {
    events: document.getElementById("events"),
    empty: document.querySelector('[data-role="empty"]'),
    seq: document.querySelector('[data-role="seq"]'),
    poll: document.querySelector('[data-role="poll"]')
  };

  var view = {
    seq: 0,
    traceId: null,
    sink: null,
    failMode: false,
    lastEvent: null,
    busy: false,
    token: null,
    tokenName: "audit-demo-token"
  };

  var FIELDS = [
    { key: "eventId", label: "事件号", index: "1" },
    { key: "occurredAt", label: "发生时刻", index: "2", time: true },
    { key: "action", label: "动作码", index: "3" },
    { key: "module", label: "业务模块", index: "4" },
    { key: "desc", label: "描述", index: "5" },
    { key: "actorId", label: "操作人 id", index: "6" },
    { key: "actorName", label: "操作人名", index: "7" },
    { key: "target", label: "目标对象", index: "8" },
    { key: "argsSummary", label: "入参摘要", index: "9" },
    { key: "result", label: "结果", index: "10" },
    { key: "errorCode", label: "错误码", index: "11" },
    { key: "errorMessage", label: "错误消息", index: "11" },
    { key: "elapsedMillis", label: "耗时(ms)", index: "12" },
    { key: "traceId", label: "traceId", index: "13" },
    { key: "client", label: "客户端标识", index: "14" }
  ];

  /* ---------- 基础请求 ---------- */

  function query(params) {
    if (!params) {
      return "";
    }
    var pairs = [];
    Object.keys(params).forEach(function (key) {
      var value = params[key];
      if (value === null || value === undefined) {
        return;
      }
      pairs.push(encodeURIComponent(key) + "=" + encodeURIComponent(String(value)));
    });
    return pairs.length ? "?" + pairs.join("&") : "";
  }

  function api(path, params) {
    var headers = { Accept: "application/json" };
    if (view.token) {
      headers[view.tokenName] = view.token;
    }
    return fetch(API + path + query(params), {
      headers: headers,
      cache: "no-store"
    }).then(function (response) {
      return response.json();
    });
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = text;
    }
    return node;
  }

  function formatTime(value) {
    if (!value) {
      return "-";
    }
    var date = new Date(Number(value));
    var hh = String(date.getHours()).padStart(2, "0");
    var mm = String(date.getMinutes()).padStart(2, "0");
    var ss = String(date.getSeconds()).padStart(2, "0");
    var ms = String(date.getMilliseconds()).padStart(3, "0");
    return hh + ":" + mm + ":" + ss + "." + ms;
  }

  function show(value) {
    if (value === null || value === undefined || value === "") {
      return "-";
    }
    return String(value);
  }

  /* ---------- 顶部状态 ---------- */

  function chip(name) {
    return document.querySelector('[data-chip="' + name + '"]');
  }

  function renderState(data) {
    view.sink = data.sink;
    view.failMode = data.sinkFailMode === true;
    view.traceId = data.traceId;
    var config = data.config || {};

    chip("sink").textContent = "落点：" + show(data.sink);
    chip("args").textContent = "入参记录：" + (config.argsEnabled ? "on" : "off");
    chip("max").textContent = "摘要上限：" + show(config.maxLength);
    chip("failfast").textContent = "fail-fast：" + (config.failFast ? "true" : "false");
    chip("client").textContent = "客户端标识：" + (config.clientEnabled ? "on" : "off");
    chip("module").textContent = "模块：" + show(config.module);
    chip("trace").textContent = "traceId：" + show(data.traceId);
    chip("actor").textContent = data.actor
      ? "操作人：" + data.actor.name + " (" + data.actor.id + ")"
      : "操作人：未登录";

    var counters = data.counters || {};
    Object.keys(counters).forEach(function (key) {
      var node = document.querySelector('[data-counter="' + key + '"]');
      if (node) {
        node.textContent = String(counters[key]);
      }
    });

    if (view.failMode) {
      chip("sink").classList.add("chip-danger");
    } else {
      chip("sink").classList.remove("chip-danger");
    }

    var select = document.querySelector('[data-role="sink"]');
    if (select && data.sink) {
      select.value = data.sink;
    }
  }

  function refreshState() {
    return api("/state").then(function (result) {
      return renderState(result.data || {});
    });
  }

  /* ---------- 事件流 ---------- */

  function renderEvent(item) {
    var failure = item.result === "FAILURE";
    var card = el("article", "event" + (failure ? " is-failure" : ""));

    var head = el("div", "event-head");
    head.appendChild(el("span", "event-seq", "#" + item.seq));
    head.appendChild(el("span", "event-action", show(item.action)));
    head.appendChild(el("span", "badge" + (failure ? " badge-failure" : ""), failure ? "FAILURE" : "SUCCESS"));
    card.appendChild(head);

    var meta = el("div", "event-meta");
    meta.textContent = formatTime(item.occurredAt) + " · " + show(item.eventId);
    card.appendChild(meta);

    var grid = el("div", "event-grid");
    FIELDS.forEach(function (field) {
      var row = el("div", "field-item");
      var key = el("span", "field-key");
      key.appendChild(el("i", null, "要素" + field.index));
      key.appendChild(document.createTextNode(field.label));
      row.appendChild(key);

      var value = item[field.key];
      var text = field.time ? formatTime(value) + " (" + show(value) + ")" : show(value);
      var valueNode = el("span", "field-value", text);
      if (value === null || value === undefined || value === "") {
        valueNode.classList.add("is-null");
      }
      if (field.key === "errorCode" || field.key === "errorMessage") {
        if (value) {
          valueNode.classList.add("is-error");
        }
      }
      row.appendChild(valueNode);
      grid.appendChild(row);
    });
    card.appendChild(grid);

    var foot = el("div", "event-foot");
    foot.appendChild(el("span", null, "要素 13/13 · 字段 15/15"));
    foot.appendChild(el("span", null, "traceId " + (item.traceId ? "已携带" : "为空")));
    card.appendChild(foot);

    return card;
  }

  function appendEvents(items) {
    if (!items || !items.length) {
      return;
    }
    DOM.empty.hidden = true;
    DOM.empty.style.display = "none";
    items.forEach(function (item) {
      DOM.events.insertBefore(renderEvent(item), DOM.events.firstChild);
      view.lastEvent = item;
    });
    while (DOM.events.querySelectorAll(".event").length > MAX_CARDS) {
      var cards = DOM.events.querySelectorAll(".event");
      DOM.events.removeChild(cards[cards.length - 1]);
    }
  }

  function pollEvents() {
    return api("/events", { since: view.seq }).then(function (result) {
      var data = result.data || {};
      if (typeof data.seq === "number") {
        view.seq = data.seq;
        DOM.seq.textContent = String(data.seq);
      }
      appendEvents(data.events || []);
      DOM.poll.textContent = "增量轮询：since=" + view.seq + " · " + new Date().toLocaleTimeString("zh-CN", { hour12: false });
    });
  }

  function clearEvents() {
    Array.prototype.slice.call(DOM.events.querySelectorAll(".event")).forEach(function (card) {
      DOM.events.removeChild(card);
    });
    DOM.empty.hidden = false;
    DOM.empty.style.display = "";
    view.lastEvent = null;
    view.seq = 0;
    DOM.seq.textContent = "0";
  }

  /* ---------- 场景反馈 ---------- */

  function feedbackOf(button) {
    var scenario = button.closest(".scenario");
    return scenario ? scenario.querySelector('[data-role="feedback"]') : null;
  }

  function say(node, text, kind) {
    if (!node) {
      return;
    }
    node.textContent = text;
    node.classList.remove("is-ok", "is-warn", "is-busy");
    if (kind) {
      node.classList.add(kind);
    }
  }

  function deliveredCount() {
    var node = document.querySelector('[data-counter="delivered"]');
    return node ? Number(node.textContent) : 0;
  }

  function run(button, task) {
    if (view.busy) {
      return;
    }
    view.busy = true;
    var node = feedbackOf(button);
    say(node, "请求中…", "is-busy");
    button.disabled = true;
    Promise.resolve()
      .then(task)
      .catch(function (error) {
        say(node, "请求失败：" + error.message, "is-warn");
      })
      .then(function () {
        button.disabled = false;
        view.busy = false;
        return refreshState().catch(function () {
          return null;
        });
      });
  }

  /* ---------- 各场景动作 ---------- */

  var actions = {
    annotated: function (button) {
      var node = feedbackOf(button);
      var before = deliveredCount();
      run(button, function () {
        return api("/annotated").then(function (result) {
          var value = result.data ? result.data.returnValue : null;
          return pollEvents().then(refreshState).then(function () {
            var delta = deliveredCount() - before;
            say(node, "业务返回 " + value + " · 事件 +" + delta + "（已标注 → 恰 1 条）", "is-ok");
          });
        });
      });
    },

    unannotated: function (button) {
      var node = feedbackOf(button);
      var before = deliveredCount();
      run(button, function () {
        return api("/unannotated").then(function (result) {
          var value = result.data ? result.data.returnValue : null;
          return pollEvents().then(refreshState).then(function () {
            var delta = deliveredCount() - before;
            say(node, "业务返回 " + value + " · 事件 +" + delta + "（未标注 → 零采集，对照组）", "is-ok");
          });
        });
      });
    },

    manual: function (button) {
      var node = feedbackOf(button);
      var scenario = button.closest(".scenario");
      var times = scenario.querySelector('[data-role="times"]').value;
      run(button, function () {
        return api("/manual", { times: times }).then(function (result) {
          var recorded = result.data ? result.data.recorded : 0;
          return pollEvents().then(refreshState).then(function () {
            say(node, "AuditKit.record 调用 " + recorded + " 次 → 事件 +" + recorded + "（耗时恒 0，无入参摘要）", "is-ok");
          });
        });
      });
    },

    masked: function (button) {
      var node = feedbackOf(button);
      run(button, function () {
        return api("/masked", { keep: "VISIBLE-OK", secret: "SECRET-XYZ" }).then(function (result) {
          return pollEvents().then(refreshState).then(function () {
            var recordArgs = result.data && result.data.recordArgs;
            say(node, "recordArgs=" + recordArgs + " → 摘要应含 VISIBLE-OK、只把第 2 个参数换成 <masked>", "is-ok");
          });
        });
      });
    },

    "masked-off": function (button) {
      var node = feedbackOf(button);
      run(button, function () {
        return api("/masked/off", { keep: "VISIBLE-OK", secret: "SECRET-XYZ" }).then(function (result) {
          return pollEvents().then(refreshState).then(function () {
            var recordArgs = result.data && result.data.recordArgs;
            say(node, "recordArgs=" + recordArgs + " → 摘要为 -（不记录入参，与上一条对照）", "is-ok");
          });
        });
      });
    },

    "switch-sink": function (button) {
      var node = feedbackOf(button);
      var scenario = button.closest(".scenario");
      var name = scenario.querySelector('[data-role="sink"]').value;
      run(button, function () {
        return api("/sink/switch", { name: name }).then(function (result) {
          if (result.code !== 0) {
            say(node, result.msg || "切换失败", "is-warn");
            return null;
          }
          return refreshState().then(function () {
            say(node, "已切到 " + name + " · 业务代码未改动，下次交付落在该落点（计数见右侧）", "is-ok");
          });
        });
      });
    },

    failure: function (button) {
      var node = feedbackOf(button);
      var scenario = button.closest(".scenario");
      var evidence = scenario.querySelector('[data-role="evidence"]');
      run(button, function () {
        return api("/failure")
          .catch(function () {
            return null;
          })
          .then(function () {
            return api("/failure/evidence");
          })
          .then(function (result) {
            var data = result.data || {};
            var thrown = data.thrown || {};
            var failureEvent = data.lastFailure;
            evidence.hidden = false;
            evidence.textContent = "";
            evidence.appendChild(el("span", null, "异常类型：" + show(thrown.type)));
            evidence.appendChild(el("span", null, "异常消息：" + show(thrown.message)));
            evidence.appendChild(el("span", null, "业务实例与冒泡实例同一：" + (thrown.sameInstance ? "true" : "false")));
            evidence.appendChild(el("span", null, "失败事件：" + (failureEvent
              ? "errorCode=" + show(failureEvent.errorCode) + " · errorMessage=" + show(failureEvent.errorMessage)
              : "尚未采集到")));
            return pollEvents().then(refreshState).then(function () {
              say(node, "框架把异常收敛为服务端错误响应（HTTP 200 / code≠0），事件侧已记 1 条 FAILURE", "is-warn");
            });
          });
      });
    },

    "trace-check": function (button) {
      var node = feedbackOf(button);
      run(button, function () {
        return api("/annotated").then(function (result) {
          var requestTrace = result.data ? result.data.traceId : null;
          return pollEvents().then(refreshState).then(function () {
            var eventTrace = view.lastEvent ? view.lastEvent.traceId : null;
            var same = requestTrace && eventTrace && requestTrace === eventTrace;
            say(node, "本次请求 traceId=" + show(requestTrace) + " · 该请求的事件 traceId=" + show(eventTrace)
              + (same ? " → 逐字符一致（supplier 注入生效）" : " → 不一致，请检查 supplier 注入"),
            same ? "is-ok" : "is-warn");
          });
        });
      });
    },

    reset: function (button) {
      var node = feedbackOf(button);
      run(button, function () {
        return api("/reset").then(function () {
          clearEvents();
          return refreshState().then(function () {
            say(node, "展示数据与计数已归零，事件列表回到空态", "is-ok");
          });
        });
      });
    },

    "sink-fail-on": function (button) {
      var node = feedbackOf(button);
      run(button, function () {
        return api("/sink/fail", { on: "true" }).then(function () {
          return refreshState().then(function () {
            say(node, "已打开：落点每次交付先抛异常（fail-fast=false → 吞掉并记一条模块日志）", "is-warn");
          });
        });
      });
    },

    "sink-fail-off": function (button) {
      var node = feedbackOf(button);
      run(button, function () {
        return api("/sink/fail", { on: "false" }).then(function () {
          return refreshState().then(function () {
            say(node, "已关闭落点异常，恢复正常交付", "is-ok");
          });
        });
      });
    },

    "annotated-error": function (button) {
      var node = feedbackOf(button);
      if (!view.failMode) {
        say(node, "请先点「打开 sink 异常」，再触发本动作才能看到隔离效果", "is-warn");
        return;
      }
      var before = deliveredCount();
      run(button, function () {
        return api("/annotated").then(function (result) {
          var value = result.data ? result.data.returnValue : null;
          return pollEvents().then(refreshState).then(function () {
            var delta = deliveredCount() - before;
            say(node, "业务返回 " + value + "（未变）· 交付尝试 +" + delta
              + " · 落点异常已被吞掉并记一条模块日志，事件未进入列表", "is-warn");
          });
        });
      });
    },

    login: function (button) {
      var node = feedbackOf(button);
      var user = button.getAttribute("data-user");
      say(node, "登录中…", "is-busy");
      api("/login", { userId: user }).then(function (result) {
        var data = result.data || {};
        view.token = data.token || null;
        view.tokenName = data.tokenName || view.tokenName;
        return refreshState().then(function () {
          var actor = data.actor;
          say(node, actor ? "已登录为 " + actor.name + "，后续事件携带该操作人" : "登录失败", actor ? "is-ok" : "is-warn");
        });
      });
    },

    logout: function (button) {
      var node = feedbackOf(button);
      say(node, "登出中…", "is-busy");
      api("/logout").then(function () {
        view.token = null;
        return refreshState().then(function () {
          say(node, "已登出，后续事件操作人为空", "is-ok");
        });
      });
    }
  };

  /* ---------- 绑定 ---------- */

  document.addEventListener("click", function (event) {
    var button = event.target.closest("[data-act]");
    if (!button) {
      return;
    }
    var handler = actions[button.getAttribute("data-act")];
    if (handler) {
      handler(button);
    }
  });

  /* ---------- 启动 ---------- */

  refreshState()
    .then(pollEvents)
    .catch(function (error) {
      DOM.poll.textContent = "初始化失败：" + error.message;
    });

  setInterval(function () {
    pollEvents().catch(function () {
      DOM.poll.textContent = "轮询失败，1 秒后重试";
    });
  }, 1000);

  setInterval(function () {
    refreshState().catch(function () {
      return null;
    });
  }, 3000);
})();
