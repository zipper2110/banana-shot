// Features page, "Export preview": a copy of the preview window of the Export tab (B-75).
// The window plays the export before the encode: the points one after the other, a replay with a zoom,
// and the statistics cards. Then the user jumps to a part with Next and with a click on the strip.
(function (D) {
  "use strict";

  var PLAYERS = D.PLAYERS;
  // The parts of the export in output order: the label, the length in seconds, and the kind.
  // The score is the score after the part: [points, games] of each player.
  var PARTS = [
    { label: "Point 1", seconds: 14, kind: "point", score: [["15", "0"], [4, 2]] },
    { label: "Point 2", seconds: 21, kind: "point", score: [["30", "0"], [4, 2]] },
    { label: "Replay", seconds: 12, kind: "replay", score: [["30", "0"], [4, 2]] },
    { label: "Point 3", seconds: 9, kind: "point", score: [["30", "15"], [4, 2]] },
    { label: "Point 4", seconds: 17, kind: "point", score: [["40", "15"], [4, 2]] },
    { label: "Point 5", seconds: 11, kind: "point", score: [["0", "0"], [5, 2]] },
    { label: "Set 2 stats", seconds: 10, kind: "card", card: "/assets/features/card-classic.webp", score: [["0", "0"], [5, 2]] },
    { label: "Match stats", seconds: 12, kind: "card", card: "/assets/features/card-momentum.webp", score: [["0", "0"], [5, 2]] }
  ];
  var TOTAL = PARTS.reduce(function (sum, p) { return sum + p.seconds; }, 0);
  var STRIP_W = 816; // The width of the strip in stage px.
  var START_SCORE = [["0", "0"], [4, 2]];
  var NOTE = "The preview can stop for a moment between points. The exported video has no stops.";

  function clock(seconds) {
    seconds = Math.max(0, Math.round(seconds));
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
  }

  // The start of each part, in seconds from the start of the export.
  var STARTS = [];
  PARTS.reduce(function (t, p) { STARTS.push(t); return t + p.seconds; }, 0);

  function x(seconds) { return seconds / TOTAL * STRIP_W; }

  function build(demo) {
    var strip = PARTS.map(function (p, i) {
      return '<span class="pv-part pv-k-' + p.kind + '" style="left:' + x(STARTS[i]) + "px;width:" + (x(p.seconds) - 3) + 'px">' + p.label + "</span>";
    }).join("");
    var board = '<div class="sb pos-tl"><div class="sb-title"><i></i><span>CLUB FINAL</span></div>' + PLAYERS.map(function (p) {
      return '<div class="sb-row"><span class="sb-name"><b style="background:' + p.color + '"></b>' + p.name.toUpperCase() +
        '</span><span class="sb-set">6</span><span class="sb-games"></span><span class="sb-pts"><span></span></span></div>';
    }).join("") + "</div>";

    var ui = D.stage(demo,
      // The Export tab behind the window. Only its shapes show, because the window covers it.
      D.nav(5) +
      '<div class="pv-behind"><i style="height:120px"></i><i style="height:90px"></i><i style="height:150px"></i><i class="pv-go"></i></div>' +
      '<div class="pv-dim"></div>' +
      '<div class="pv-win">' +
        '<div class="pv-title"><span>Preview – Only points, 1080p</span><b>✕</b></div>' +
        '<div class="pv-video"><div class="pv-frame"><img class="pv-img" src="/assets/frames/points.webp" loading="lazy" alt="">' +
          '<img class="pv-card" src="/assets/features/card-classic.webp" alt="" loading="lazy">' + board + '<span class="pv-badge">REPLAY</span></div></div>' +
        '<div class="pv-note"><i>i</i>' + NOTE + "</div>" +
        '<div class="pv-bar"><span class="ui-now ui-num pv-time"></span>' +
          '<div class="ui-transport"><span class="ui-seek">−5s</span><span class="ui-seek">−1s</span>' +
            '<span class="ui-play"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 5h4v14H7zm6 0h4v14h-4z"/></svg></span>' +
            '<span class="ui-seek">+1s</span><span class="ui-seek">+5s</span></div>' +
          '<div class="pv-btns"><span class="pv-prev">Previous</span><span class="pv-next">Next</span><span class="pv-close">Close</span></div>' +
        "</div>" +
        '<div class="pv-strip">' + strip + '<div class="pv-ph"></div></div>' +
      "</div>",
      { strip: { x: 60, y: 300, w: 840, h: 472 } });

    var parts = ui.qa(".pv-part"), ph = ui.q(".pv-ph"), time = ui.q(".pv-time");
    var img = ui.q(".pv-img"), card = ui.q(".pv-card"), badge = ui.q(".pv-badge"), frame = ui.q(".pv-frame");
    var rows = ui.qa(".sb-row");
    var now = 0, current = -1;

    function partAt(seconds) {
      for (var i = PARTS.length - 1; i >= 0; i--) if (seconds >= STARTS[i]) return i;
      return 0;
    }

    function showScore(score, animate) {
      rows.forEach(function (row, i) {
        var games = row.querySelector(".sb-games"), pts = row.querySelector(".sb-pts span");
        if (animate && pts.textContent !== score[0][i]) { pts.classList.remove("flash"); void pts.offsetWidth; pts.classList.add("flash"); }
        games.textContent = score[1][i];
        pts.textContent = score[0][i];
        var me = parseInt(score[0][i], 10) || 0, other = parseInt(score[0][1 - i], 10) || 0;
        row.classList.toggle("lead", me > other);
      });
    }

    // Shows the state at "seconds": the playhead, the time, the part, the scoreboard, the replay, and the card.
    function show(seconds, animate) {
      now = Math.min(TOTAL, seconds);
      ph.style.left = x(now) + "px";
      time.textContent = clock(now) + " / " + clock(TOTAL);
      var i = partAt(now);
      if (i === current) return;
      var part = PARTS[i];
      parts.forEach(function (el, k) { el.classList.toggle("on", k === i); });
      frame.classList.toggle("pv-replay", part.kind === "replay");
      frame.classList.toggle("pv-showcard", part.kind === "card");
      if (part.card) card.src = part.card;
      // The score of a point shows after the point. During a point, the board shows the score before it.
      showScore(i === 0 ? START_SCORE : PARTS[i - 1].score, animate);
      current = i;
    }

    // Plays from now to "to" seconds at the given speed (seconds of video in one real second).
    function play(to, speed) {
      var from = now;
      return ui.tween(Math.abs(to - from) / speed * 1000, function (k) { show(from + (to - from) * k, true); }, true);
    }

    function reset() {
      current = -1;
      demo.classList.add("hud-top");
      ui.q(".pv-win").classList.remove("open");
      ui.q(".pv-dim").classList.remove("open");
      show(0, false);
    }

    function open() {
      ui.q(".pv-win").classList.add("open");
      ui.q(".pv-dim").classList.add("open");
    }

    function closeWindow() {
      ui.q(".pv-win").classList.remove("open");
      ui.q(".pv-dim").classList.remove("open");
    }

    return { ui: ui, parts: parts, show: show, play: play, reset: reset, open: open, closeWindow: closeWindow, next: ui.q(".pv-next"), close: ui.q(".pv-close") };
  }

  D.register("preview", function (demo) {
    var pv = build(demo), ui = pv.ui;

    async function play() {
      await ui.sleep(400);
      pv.open();
      await ui.sleep(700);
      ui.label("film", "Watch the export before it starts");
      // Points 1 and 2 at a fast speed, so that the demo stays short.
      await pv.play(STARTS[2], 12);
      // The replay: the zoom and the slow motion of the export.
      ui.label("flag", "Replays, cards, and the score, as in the export");
      await pv.play(STARTS[3], 4);
      await pv.play(STARTS[3] + 4, 12);
      // Next goes to the next part.
      ui.label("queue", "Jump to any part");
      await ui.sleep(300);
      for (var i = 0; i < 2; i++) {
        await ui.press(pv.next);
        pv.show(STARTS[4 + i], true);
        await ui.sleep(650);
      }
      await pv.play(STARTS[5] + 5, 10);
      // A click on the strip opens the statistics card.
      ui.camera("strip");
      await ui.sleep(600);
      await ui.press(pv.parts[6]);
      pv.show(STARTS[6], true);
      await ui.sleep(1500);
      await ui.press(pv.parts[7]);
      pv.show(STARTS[7], true);
      await ui.sleep(1200);
      ui.camera("wide");
      ui.label("check", "Then close it and export");
      await ui.sleep(900);
      await ui.press(pv.close);
      pv.closeWindow();
      await ui.sleep(1400);
    }

    ui.run(pv.reset, play, function () {
      pv.open();
      pv.show(STARTS[2] + 4, false);
    });
  });
})(window.Demos);
