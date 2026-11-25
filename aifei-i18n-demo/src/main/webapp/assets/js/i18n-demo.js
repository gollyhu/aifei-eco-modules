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

// SDK 不提供前端格式化运行时，此处用浏览器 Intl；隐藏 input 仍保留 ISO 原值供 dpReadIso 读取。
function formatDate(iso, locale){
  return new Intl.DateTimeFormat(locale).format(new Date(iso + 'T00:00:00'));
}

// 日历控件：隐藏 input 保留 ISO 值，显示框按 locale 呈现；面板文案全部经 tr 取自语言包。
var dpState = {year:0, month:0};
var dpOpened = false;

function dpEl(id){ return document.getElementById(id); }
function dpPad(n){ return n < 10 ? '0' + n : '' + n; }
function dpIso(y, m, d){ return y + '-' + dpPad(m + 1) + '-' + dpPad(d); }
function dpReadIso(){ return dpEl('f-date').value; }

function dpRefreshDisplay(){
  var iso = dpReadIso();
  var text = dpEl('f-date-text');
  text.textContent = iso ? formatDate(iso, currentLocale()) : tr('form.date.ph');
  text.className = iso ? 'dp-value' : 'dp-value dp-placeholder';
}

function dpSyncLabels(){
  var prev = dpEl('dp-prev'), next = dpEl('dp-next');
  prev.title = tr('form.calendar.prev');
  prev.setAttribute('aria-label', prev.title);
  next.title = tr('form.calendar.next');
  next.setAttribute('aria-label', next.title);
  dpEl('dp-clear').textContent = tr('form.calendar.clear');
  dpEl('dp-today').textContent = tr('form.calendar.today');
  dpRefreshDisplay();
}

function dpRender(){
  var y = dpState.year, m = dpState.month;
  dpEl('dp-title').textContent = tr('form.calendar.title', [tr('form.calendar.month.' + (m + 1)), String(y)]);
  var week = dpEl('dp-week');
  week.textContent = '';
  for(var w = 0; w < 7; w++){
    var wc = document.createElement('span');
    wc.className = 'dp-week-cell';
    wc.textContent = tr('form.calendar.week.' + w);
    week.appendChild(wc);
  }
  var grid = dpEl('dp-grid');
  grid.textContent = '';
  var firstDay = new Date(y, m, 1).getDay();
  var days = new Date(y, m + 1, 0).getDate();
  var iso = dpReadIso(), now = new Date();
  var i, cell, btn, day;
  for(i = 0; i < firstDay; i++){
    cell = document.createElement('span');
    cell.className = 'dp-cell dp-blank';
    grid.appendChild(cell);
  }
  for(i = 1; i <= days; i++){
    day = i;
    btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'dp-cell';
    btn.textContent = String(day);
    if(dpIso(y, m, day) === iso){ btn.className += ' dp-selected'; }
    if(y === now.getFullYear() && m === now.getMonth() && day === now.getDate()){ btn.className += ' dp-today'; }
    btn.addEventListener('click', (function(d){ return function(){ dpSelect(dpIso(y, m, d)); }; })(day));
    grid.appendChild(btn);
  }
}

function dpSelect(iso){
  dpEl('f-date').value = iso;
  dpRefreshDisplay();
  dpClosePanel();
}

function dpOpenPanel(){
  var iso = dpReadIso(), base = iso ? iso.split('-') : null, now = new Date();
  dpState.year = base ? Number(base[0]) : now.getFullYear();
  dpState.month = base ? Number(base[1]) - 1 : now.getMonth();
  dpRender();
  dpEl('dp-panel').classList.add('open');
  dpEl('f-date-display').setAttribute('aria-expanded', 'true');
  dpOpened = true;
}

function dpClosePanel(){
  dpEl('dp-panel').classList.remove('open');
  dpEl('f-date-display').setAttribute('aria-expanded', 'false');
  dpOpened = false;
}

function dpShift(delta){
  dpState.month += delta;
  if(dpState.month < 0){ dpState.month = 11; dpState.year--; }
  else if(dpState.month > 11){ dpState.month = 0; dpState.year++; }
  dpRender();
}

function dpClear(){
  dpEl('f-date').value = '';
  dpRefreshDisplay();
  dpClosePanel();
}

function dpSetToday(){
  var now = new Date();
  dpSelect(dpIso(now.getFullYear(), now.getMonth(), now.getDate()));
}

function initDatePicker(){
  dpEl('f-date-display').addEventListener('click', function(){
    if(dpOpened){ dpClosePanel(); } else { dpOpenPanel(); }
  });
  dpEl('dp-prev').addEventListener('click', function(){ dpShift(-1); });
  dpEl('dp-next').addEventListener('click', function(){ dpShift(1); });
  dpEl('dp-clear').addEventListener('click', dpClear);
  dpEl('dp-today').addEventListener('click', dpSetToday);
  document.addEventListener('click', function(e){
    if(!dpOpened){ return; }
    var node = e.target;
    while(node && node !== document){
      if(node.id === 'datepicker'){ return; }
      node = node.parentNode;
    }
    dpClosePanel();
  });
  document.addEventListener('keydown', function(e){
    if(dpOpened && e.keyCode === 27){ dpClosePanel(); }
  });
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
  dpSyncLabels();
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
function toast(key, args){
  var box = document.getElementById('toast');
  document.getElementById('toast-text').textContent = tr(key, args);
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
  var d = dpReadIso();
  if(d){ toast('toast.search.date', [d]); } else { toast('toast.search'); }
});
document.getElementById('btnReset').addEventListener('click', function(){
  document.getElementById('demoForm').reset();
  dpClear();
  listShown = false;
  clearTable();
  toast('toast.reset');
});

// 选中当前 locale（首屏取服务端渲染的 <html lang> = i18n.defaultLocale，P-01）
var sel = document.getElementById('langSelect');
if(sel){ sel.value = currentLocale(); }

initDatePicker();
var locale = currentLocale();
loadBundle(locale).then(function(){ return loadMoney(locale); });
