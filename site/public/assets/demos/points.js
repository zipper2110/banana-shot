// Feature 1, "No dead time".
// A copy of the Points tab. The user marks the start of a point, and a click on the winner ends it.
// The point goes into the list, then the timeline moves the marks together.
(function (D) {
  "use strict";

  // The timeline shows 10 minutes of the match: 0:36:00 to 0:46:00. 824 px for 600 s.
  var T0 = 2160, T1 = 2760, TRACK_W = 824;
  // [start, end, favorite, winner].
  var EARLIER = [[2172, 2190, false, 0], [2215, 2241, true, 1], [2268, 2280, false, 0], [2310, 2338, false, 0],
    [2371, 2389, false, 1], [2405, 2430, false, 0], [2444, 2461, false, 1]];
  var NEW_POINT = [2490, 2516];
  var NEW_WINNER = 0;
  var TOTAL = 74;
  var FIRST_NUMBER = 22; // The number of the first point in the window.

  function x(t) { return (t - T0) / (T1 - T0) * TRACK_W; }

  function pad(n) { return String(n).padStart(2, "0"); }
  // "00:41:30", as in the app.
  function short(t) {
    t = Math.floor(t);
    return [Math.floor(t / 3600), Math.floor((t % 3600) / 60), t % 60].map(pad).join(":");
  }
  // "00:41:30.2", as the playback bar of the app.
  function clock(t) { return short(t) + "." + Math.floor((t % 1) * 10); }
  // "41:00" for the ruler.
  function rulerText(t) { return Math.floor(t / 60) + ":" + pad(t % 60); }

  D.register("points", function (demo) {
    var ruler = "";
    for (var r = T0 + 60; r < T1; r += 120) ruler += '<span style="left:' + x(r) + 'px">' + rulerText(r) + "</span>";

    var ui = D.stage(demo,
      D.nav(1) +
      '<div class="ui-video"><img src="/assets/frames/points.webp" loading="lazy" alt=""></div>' +
      D.pointBar() +
      D.playBar() +
      '<div class="ui-side">' +
        D.pointHead(FIRST_NUMBER, TOTAL, false) +
        D.listHead("", 9, 6) +
        '<div class="ui-thead"><span>#</span><span>Start</span><span style="text-align:right">Length</span><span></span></div>' +
        '<div class="ui-rows"></div>' +
        D.pointsFoot() +
      '</div>' +
      '<div class="ui-tl">' +
        '<div class="ui-ruler">' + ruler + '</div>' +
        '<span class="ui-gut" style="top:47px">MARKS</span><span class="ui-gut" style="top:84px">VIDEO</span>' +
        '<div class="ui-marks"></div><div class="ui-vtrack"></div>' +
        '<div class="ui-ph"></div>' +
        '<div class="ui-result"><s class="ui-num">1:52:40</s><b class="ui-num">Only points: 28:15</b></div>' +
      '</div>',
      {
        // 30% closer, on the bottom left corner: the point bar and the timeline.
        corner: { x: 0, y: D.STAGE_H - D.STAGE_H / 1.3, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 },
        timeline: { x: 48, y: 408, w: 912, h: 132 }
      });

    var now = ui.q(".ui-now");
    var startButton = ui.q(".pt-start"), startText = ui.q(".pt-start em");
    var winnerButton = ui.q(NEW_WINNER === 0 ? ".pt-p1" : ".pt-p2");
    var pointNumber = ui.q(".pt-num");
    var rows = ui.q(".ui-rows"), count = ui.q(".pt-marked");
    var timeline = ui.q(".ui-tl"), marks = ui.q(".ui-marks");
    var vtrack = ui.q(".ui-vtrack"), playhead = ui.q(".ui-ph");
    var time = 0, number = 0, pending = null;

    function setTime(t) {
      time = t;
      now.textContent = clock(t);
      playhead.style.left = (76 + x(t)) + "px";
      if (pending) pending.el.style.width = Math.max(2, x(t) - x(pending.start)) + "px";
    }

    // Moves the playhead to the time "to" in "ms" milliseconds, at a constant speed.
    function play(to, ms) {
      var from = time;
      return ui.tween(ms, function (k) { setTime(from + (to - from) * k); }, true);
    }

    function addMark(start, end, fav) {
      var el = document.createElement("div");
      el.className = "ui-mk" + (fav ? " fav" : "");
      el.style.left = x(start) + "px";
      el.style.width = (x(end) - x(start)) + "px";
      marks.appendChild(el);
      return el;
    }

    function addRow(start, end, fav, winner, isNew) {
      number++;
      count.textContent = TOTAL - FIRST_NUMBER - EARLIER.length + number;
      pointNumber.textContent = number;
      rows.querySelectorAll(".sel").forEach(function (row) { row.classList.remove("sel"); });
      var row = document.createElement("div");
      row.className = "ui-row won ui-num sel" + (isNew ? " new" : "");
      row.style.setProperty("--w", D.PLAYERS[winner].color);
      row.innerHTML = '<span class="n">' + number + "</span><span>" + short(start) +
        '</span><span class="len">' + Math.round(end - start) + ' s</span><span class="star">' + (fav ? "★" : "") + "</span>";
      rows.appendChild(row);
    }

    // The Point start button: lime tint and the start time while the point waits for its end.
    function setPending(start) {
      startButton.classList.toggle("pending", start !== null);
      startText.textContent = start === null ? "" : short(start);
    }

    // A flag on the timeline at the playhead: the start or the end of the point.
    function flag(kind) {
      var el = document.createElement("div");
      el.className = "ui-flag " + kind;
      el.style.left = (76 + x(time)) + "px";
      el.innerHTML = D.icon("flag");
      timeline.appendChild(el);
    }

    function removeFlags() {
      timeline.querySelectorAll(".ui-flag").forEach(function (el) { el.classList.add("gone"); });
    }

    async function markStart() {
      await ui.press(startButton);
      flag("start");
      ui.label("flag", "Start of the point");
      pending = { start: time, el: addMark(time, time + 1) };
      pending.el.classList.add("pend");
      setPending(time);
    }

    // A click on the winner ends the point.
    async function markEnd() {
      await ui.press(winnerButton);
      flag("end");
      ui.label("ball", "End of the point: " + D.PLAYERS[NEW_WINNER].name + " scored");
      setPending(null);
      pending.el.classList.remove("pend");
      pending.el.classList.add("pop");
      var point = pending;
      point.end = time;
      pending = null;
      return point;
    }

    // Moves the marks together and removes the time between them.
    function collapse() {
      var left = 0;
      marks.querySelectorAll(".ui-mk").forEach(function (el) {
        el.style.left = left + "px";
        left += parseFloat(el.style.width) + 2;
      });
      vtrack.style.width = left + "px";
      timeline.classList.add("collapsed");
    }

    function reset() {
      timeline.classList.remove("collapsed");
      marks.innerHTML = "";
      rows.innerHTML = "";
      timeline.querySelectorAll(".ui-flag").forEach(function (el) { el.remove(); });
      number = FIRST_NUMBER - 1;
      pending = null;
      EARLIER.forEach(function (p) { addMark(p[0], p[1], p[2]); addRow(p[0], p[1], p[2], p[3], false); });
      vtrack.style.width = TRACK_W + "px";
      setPending(null);
      setTime(2470);
    }

    async function loop() {
      // The full app: the match plays.
      await play(NEW_POINT[0] - 8, 700);
      // 130% zoom on the bottom left corner.
      ui.camera("corner");
      await play(NEW_POINT[0], 550);
      // Point start: a flag at the start, the point grows. The winner click: a flag at the end.
      await markStart();
      await ui.sleep(250);
      await play(NEW_POINT[1], 900);
      var point = await markEnd();
      await ui.sleep(600);
      // Back to the full app: the point is in the list.
      ui.camera("wide");
      removeFlags();
      ui.hideLabel();
      await ui.sleep(450);
      addRow(point.start, point.end, false, NEW_WINNER, true);
      await ui.sleep(1500);
      // The export keeps only the points.
      ui.camera("timeline");
      await ui.sleep(650);
      collapse();
      await ui.sleep(2600);
    }

    ui.run(reset, loop, function () { ui.camera("timeline"); collapse(); });
  });
})(window.Demos);
