// Feature 1, "No dead time".
// A copy of the Points tab. The user marks the start and the end of a point.
// The point goes into the list, then the timeline moves the marks together.
(function (D) {
  "use strict";

  // The timeline shows 10 minutes of the match: 0:36:00 to 0:46:00. 824 px for 600 s.
  var T0 = 2160, T1 = 2760, TRACK_W = 824;
  var EARLIER = [[2172, 2190], [2215, 2241, true], [2268, 2280], [2310, 2338], [2371, 2389], [2405, 2430], [2444, 2461]];
  var NEW_POINT = [2490, 2516];
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
      '<div class="ui-playbar">' +
        '<span class="ui-now ui-num"></span>' +
        '<div class="ui-transport"><span class="ui-seek">−5s</span><span class="ui-seek">−1s</span>' +
          '<span class="ui-play"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></span>' +
          '<span class="ui-seek">+1s</span><span class="ui-seek">+5s</span></div>' +
        '<span class="ui-addc">Comment</span>' +
      '</div>' +
      '<div class="ui-side">' +
        '<div class="ui-mark">' +
          '<div class="ui-mark-head">Mark a point<span class="ui-pending"></span></div>' +
          '<div class="ui-steps">' +
            '<div class="ui-step ui-start"><div class="ui-step-top"><span class="ui-n">1</span>Point start</div><div class="ui-val ui-num"></div></div>' +
            '<div class="ui-step ui-end"><div class="ui-step-top"><span class="ui-n">2</span>Point end</div><div class="ui-val ui-num"></div></div>' +
          '</div>' +
        '</div>' +
        '<div class="ui-list-head"><b>Points</b><div class="ui-counts"><span><em class="ui-count"></em> marked</span></div></div>' +
        '<div class="ui-thead"><span>#</span><span>Start</span><span style="text-align:right">Length</span><span></span></div>' +
        '<div class="ui-rows"></div>' +
      '</div>' +
      '<div class="ui-tl">' +
        '<div class="ui-ruler">' + ruler + '</div>' +
        '<span class="ui-gut" style="top:47px">MARKS</span><span class="ui-gut" style="top:84px">VIDEO</span>' +
        '<div class="ui-marks"></div><div class="ui-vtrack"></div>' +
        '<div class="ui-ph"></div>' +
        '<div class="ui-result"><s class="ui-num">1:52:40</s><b class="ui-num">Only points: 49:35</b></div>' +
      '</div>',
      {
        // 30% closer, on the bottom right corner: the timeline and the list.
        corner: { x: D.STAGE_W - D.STAGE_W / 1.3, y: D.STAGE_H - D.STAGE_H / 1.3, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 },
        timeline: { x: 48, y: 408, w: 912, h: 132 }
      });

    var now = ui.q(".ui-now");
    var startStep = ui.q(".ui-start"), endStep = ui.q(".ui-end");
    var pendingText = ui.q(".ui-pending");
    var rows = ui.q(".ui-rows"), count = ui.q(".ui-count");
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

    function addRow(start, end, fav, isNew) {
      number++;
      count.textContent = number;
      rows.querySelectorAll(".sel").forEach(function (row) { row.classList.remove("sel"); });
      var row = document.createElement("div");
      row.className = "ui-row ui-num" + (isNew ? " new sel" : "");
      row.innerHTML = '<span class="n">' + number + "</span><span>" + short(start) +
        '</span><span class="len">' + Math.round(end - start) + ' s</span><span class="star">' + (fav ? "★" : "") + "</span>";
      rows.appendChild(row);
    }

    function steps(state) {
      startStep.className = "ui-step ui-start" + (state === "start" ? " next" : " done");
      endStep.className = "ui-step ui-end" + (state === "end" ? " next" : "");
      startStep.querySelector(".ui-val").textContent = state === "end" ? short(pending.start) : "—";
      endStep.querySelector(".ui-val").textContent = "—";
      pendingText.textContent = state === "end" ? "Pending point" : "";
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

    function markStart() {
      flag("start");
      ui.label("flag", "Start of the point");
      pending = { start: time, el: addMark(time, time + 1) };
      pending.el.classList.add("pend");
      steps("end");
    }

    function markEnd() {
      flag("end");
      ui.label("flag", "End of the point");
      endStep.classList.remove("next");
      endStep.classList.add("done");
      endStep.querySelector(".ui-val").textContent = short(time);
      pendingText.textContent = "";
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
      EARLIER.forEach(function (p) { addMark(p[0], p[1], p[2]); addRow(p[0], p[1], p[2], false); });
      vtrack.style.width = TRACK_W + "px";
      steps("start");
      setTime(2470);
    }

    async function loop() {
      // The full app: the match plays.
      await play(NEW_POINT[0] - 8, 700);
      // 130% zoom on the bottom right corner.
      ui.camera("corner");
      await play(NEW_POINT[0], 550);
      // A flag at the start, the point grows, a flag at the end.
      markStart();
      await ui.sleep(250);
      await play(NEW_POINT[1], 900);
      var point = markEnd();
      await ui.sleep(600);
      // Back to the full app: the point is in the list.
      ui.camera("wide");
      removeFlags();
      ui.hideLabel();
      await ui.sleep(450);
      addRow(point.start, point.end, false, true);
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
