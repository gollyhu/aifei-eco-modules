/* aifei-i18n demo 前端交互：控件文案走语言包 JSON，货币符号走 FormatKit（money 端点），零外网。 */

// 表格样例数据：数据本身不翻译，日期/金额按下述函数按 locale 呈现；点击查询不请求后端。
var SAMPLE_ROWS = [
  {name:'ORION-1001', region:'APAC', status:'Active',   date:'2026-11-19', amount:12800},
  {name:'ORION-1002', region:'EMEA', status:'Active',   date:'2026-11-18', amount:9600},
  {name:'ORION-1003', region:'NA',   status:'Pending',  date:'2026-11-17', amount:24500},
  {name:'ORION-1004', region:'APAC', status:'Archived', date:'2026-11-16', amount:7300},
  {name:'ORION-1005', region:'EMEA', status:'Active',   date:'2026-11-15', amount:15800},
  {name:'ORION-1006', region:'NA',   status:'Active',   date:'2026-11-14', amount:4200}
];
var listShown = false;
var currentMessages = {};
var currentSymbol = '';

// 当前 locale 由服务端经 <html lang> 输出；P-03 切换走整页重渲染，故此处只读。
function currentLocale(){
  return document.documentElement.lang || 'zh-CN';
}

// {0}/{1} 占位插值（与后端 MessageInterpolator 同语义：缺参原样保留）
function fmt(pattern, args){
  return String(pattern).replace(/\{(\d+)\}/g, function(all, i){
    return (args && args[i] != null) ? args[i] : all;
  });
}

function tr(key, args){
  var s = currentMessages[key];
  if(s == null){ s = key; }
  return fmt(s, args);
}

function formatDate(iso, locale){
  var p = iso.split('-');
  if(locale === 'en-US'){
    var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return months[Number(p[1]) - 1] + ' ' + Number(p[2]) + ', ' + p[0];
  }
  if(locale === 'ja-JP'){ return p[0] + '/' + p[1] + '/' + p[2]; }
  return iso;
}

function clearTable(){
  var tbody = document.getElementById('listBody');
  tbody.textContent = '';
  var rowEl = document.createElement('tr');
  rowEl.className = 'empty-row';
  var td = document.createElement('td');
  td.colSpan = 5;
  td.textContent = tr('table.empty');
  rowEl.appendChild(td);
  tbody.appendChild(rowEl);
}

function renderTable(locale){
  var tbody = document.getElementById('listBody');
  tbody.textContent = '';
  SAMPLE_ROWS.forEach(function(r){
    var row = document.createElement('tr');
    var amountText = (currentSymbol || '') + r.amount.toLocaleString('en-US');
    var cells = [r.name, r.region, r.status, formatDate(r.date, locale), amountText];
    cells.forEach(function(v){
      var td = document.createElement('td');
      td.textContent = v;
      row.appendChild(td);
    });
    tbody.appendChild(row);
  });
}

function applyBundle(bundle){
  currentMessages = bundle.messages || {};
  document.documentElement.dir = bundle.dir || 'ltr';
  document.querySelectorAll('[data-i18n]').forEach(function(el){
    var args = [el.getAttribute('data-args-0'), el.getAttribute('data-args-1')]
      .filter(function(v){ return v != null; });
    el.textContent = tr(el.getAttribute('data-i18n'), args);
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(function(el){
    el.setAttribute('placeholder', tr(el.getAttribute('data-i18n-placeholder')));
  });
  if(listShown){ renderTable(currentLocale()); } else { clearTable(); }
}

function loadBundle(locale){
  return fetch('/i18n/demo/bundle?locale=' + encodeURIComponent(locale))
    .then(function(r){ return r.json(); })
    .then(function(resp){ applyBundle((resp && resp.data) || {}); });
}

function loadMoney(locale){
  return fetch('/i18n/demo/money?locale=' + encodeURIComponent(locale))
    .then(function(r){ return r.json(); })
    .then(function(resp){
      currentSymbol = (resp && resp.data && resp.data.symbol) || '';
      document.getElementById('currency-symbol').textContent = currentSymbol;
      if(listShown){ renderTable(locale); }
    });
}

var toastTimer;
function toast(key){
  var box = document.getElementById('toast');
  document.getElementById('toast-text').textContent = tr(key);
  box.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function(){ box.classList.remove('show'); }, 2600);
}

document.getElementById('langSelect').addEventListener('change', function(e){
  // P-03：整页真实重渲染，服务端模板顶部 #lang(lang) 设页面级默认语言
  location.href = '?lang=' + encodeURIComponent(e.target.value);
});
document.getElementById('btnSearch').addEventListener('click', function(){
  listShown = true;
  renderTable(currentLocale());
  toast('toast.search');
});
document.getElementById('btnReset').addEventListener('click', function(){
  document.getElementById('demoForm').reset();
  listShown = false;
  clearTable();
  toast('toast.reset');
});

// 选中当前 locale（首屏取服务端渲染的 <html lang> = i18n.defaultLocale，P-01）
var sel = document.getElementById('langSelect');
if(sel){ sel.value = currentLocale(); }

var locale = currentLocale();
loadBundle(locale).then(function(){ return loadMoney(locale); });
