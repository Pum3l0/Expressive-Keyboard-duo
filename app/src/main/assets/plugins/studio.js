/* @plugin
{
  "id": "builtin.studio",
  "name": "Студия",
  "version": "1.0",
  "author": "Expressive Keyboard",
  "description": "Пример мода на API 3: пианино из синтезатора, рисование пальцем по клавиатуре, быстрые фразы с вводом прямо в моде, озвучка текста и свои настройки. Откройте его код, чтобы сделать свой мод.",
  "icon": "@music",
  "enabledByDefault": false,
  "config": [
    { "id": "wave", "title": "Звук пианино", "type": "choice", "default": "triangle",
      "choices": [{ "id": "sine", "title": "Мягкий" }, { "id": "triangle", "title": "Флейта" }, { "id": "square", "title": "8 бит" }, { "id": "saw", "title": "Синт" }] },
    { "id": "volume", "title": "Громкость", "type": "slider", "min": 0.1, "max": 1, "step": 0.1, "default": 0.5 },
    { "id": "brush", "title": "Толщина кисти", "type": "slider", "min": 2, "max": 16, "step": 1, "default": 6 },
    { "id": "vibrate", "title": "Вибрация при нажатии", "type": "toggle", "default": true }
  ]
}
*/

var tab = "piano";
var phrases = kb.storage.get("phrases", ["Скоро буду!", "Перезвоню позже", "Спасибо 🙏"]);
var strokes = [];
var current = null;
var drawing = false;
var COLORS = ["#FF5F6D", "#FFC371", "#47E891", "#4FACFE", "#B388FF", "#FFFFFF"];
var color = COLORS[0];

// --- пианино: две октавы, частоты по равномерной темперации
var NOTES = ["До", "Ре", "Ми", "Фа", "Соль", "Ля", "Си"];
var STEPS = [0, 2, 4, 5, 7, 9, 11];

function play(i) {
  var octave = Math.floor(i / 7);
  var semis = STEPS[i % 7] + 12 * octave;
  var hz = 261.63 * Math.pow(2, semis / 12);
  kb.tone(hz, 260, { wave: kb.config("wave"), volume: kb.config("volume") });
  if (kb.config("vibrate")) kb.haptic("tick");
}

// --- рисование пальцем прямо по клавишам
function redraw() {
  var items = [];
  var all = current ? strokes.concat([current]) : strokes;
  for (var i = 0; i < all.length; i++) {
    items.push({ type: "path", points: all[i].points, color: all[i].color, width: all[i].width });
  }
  if (drawing) items.push({ type: "text", x: 12, y: 8, text: "Рисуйте пальцем. Закончить — ✕ справа вверху", size: 11, color: "#CCFFFFFF" });
  kb.overlay(items);
}

kb.on("touch", function (e) {
  // ✕ в углу клавиатуры снимает перехват касаний — рисование заканчивается
  if (e.type === "cancel") { drawing = false; current = null; redraw(); return; }
  if (!drawing) return;
  if (e.type === "down") current = { points: [[e.x, e.y]], color: color, width: kb.config("brush") };
  else if (e.type === "move" && current) current.points.push([e.x, e.y]);
  else if (e.type === "up" && current) { strokes.push(current); current = null; }
  redraw();
});

function startDrawing() {
  drawing = true;
  kb.captureTouches(true);
  kb.closePanel();
  redraw();
}

function stopDrawing() {
  drawing = false;
  kb.captureTouches(false);
  redraw();
}

// --- быстрые фразы: добавляются через поле ввода мода
function addPhrase() {
  kb.input({ title: "Новая фраза", hint: "Например: Уже выхожу" }).then(function (t) {
    if (!t) return;
    phrases.unshift(t);
    kb.storage.set("phrases", phrases);
    kb.refresh();
  });
}

kb.on("option", function () { kb.refresh(); });

kb.addPanel({
  id: "studio",
  title: "Студия",
  icon: "@music",
  render: function () {
    var ui = [{
      type: "tabs", selected: tab,
      items: [{ text: "Пианино", icon: "🎹", value: "piano" }, { text: "Рисунок", icon: "🎨", value: "draw" },
        { text: "Фразы", icon: "💬", value: "phrases" }, { text: "Голос", icon: "🔊", value: "voice" }],
      onClick: function (v) { tab = v; }
    }];
    if (tab === "piano") {
      var keys = [];
      for (var i = 0; i < 14; i++) keys.push({ text: NOTES[i % 7], value: i });
      ui.push({ type: "grid", items: keys, minWidth: 40, height: 64, onClick: play });
      ui.push({ type: "text", text: "Звук и громкость — в настройках мода", size: 12, align: "center", color: "#99888888" });
    } else if (tab === "draw") {
      ui.push({
        type: "tabs", selected: color,
        items: COLORS.map(function (c) { return { text: "●", value: c }; }),
        onClick: function (c) { color = c; }
      });
      ui.push({ type: "row", children: [
        drawing
          ? { type: "button", text: "Готово", icon: "@check", accent: true, onClick: stopDrawing }
          : { type: "button", text: "Рисовать по клавиатуре", icon: "@edit", accent: true, onClick: startDrawing },
        { type: "button", text: "Стереть", icon: "@delete", onClick: function () { strokes = []; redraw(); } }
      ] });
      ui.push({ type: "canvas", height: 90, items: strokes.map(function (s) {
        return { type: "path", points: s.points.map(function (p) { return [p[0] / 4, p[1] / 4]; }), color: s.color, width: Math.max(1, s.width / 3) };
      }) });
    } else if (tab === "phrases") {
      ui.push({ type: "button", text: "Добавить фразу", icon: "@add", wide: true, accent: true, onClick: addPhrase });
      for (var j = 0; j < phrases.length; j++) {
        (function (idx) {
          ui.push({ type: "box", padding: 12, onClick: function () { kb.commit(phrases[idx]); }, children: [
            { type: "text", text: phrases[idx], bold: true, maxLines: 2 }
          ] });
        })(j);
      }
      if (phrases.length) ui.push({ type: "text", text: "Нажмите — вставить. Удалить: удержите «Очистить»", size: 11, color: "#99888888" });
      ui.push({ type: "button", text: "Очистить", icon: "@delete", onLongClick: function () {
        phrases = []; kb.storage.set("phrases", phrases);
      } });
    } else {
      ui.push({ type: "input", title: "Что прочитать вслух", hint: "Нажмите и наберите текст", value: kb.storage.get("say", ""),
        onChange: function (t) { kb.storage.set("say", t); },
        onSubmit: function (t) { kb.speak(t); } });
      ui.push({ type: "row", children: [
        { type: "button", text: "Прочитать", icon: "▶", accent: true, onClick: function () { kb.speak(kb.storage.get("say", "") || "Привет!"); } },
        { type: "button", text: "Стоп", icon: "■", onClick: function () { kb.stopSpeaking(); } }
      ] });
    }
    return ui;
  }
});
