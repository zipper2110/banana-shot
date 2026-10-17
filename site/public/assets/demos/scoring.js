// Feature 2, "Scoreboard in the video".
// A copy of the Points tab. The user clicks the winner at the end of two points. The score changes
// in the scoreboard on the video. Then the scoreboard gets a different style and position.
(function (D) {
  "use strict";

  var PLAYERS = D.PLAYERS;
  var POINT_ORDER = ["0", "15", "30", "40", "AD"];
  // The scoreboard styles that the animation shows, one after the other.
  var STYLES = [
    { accent: "#c8ec46", title: "CLUB LEAGUE", theme: "" },
    { accent: "#1d4ed8", title: "SUMMER CUP", theme: "light" },
    { accent: "#ffb547", title: "CLUB FINAL", theme: "band" }
  ];
  var TOTAL = 74;
  var FIRST = 31; // The number of the point that the animation scores first.
  // The list shows the points before FIRST: [number, start in seconds, length in seconds, winner].
  var EARLIER = [[25, 2412, 21, 1], [26, 2451, 14, 0], [27, 2480, 32, 0], [28, 2530, 9, 1], [29, 2561, 18, 0], [30, 2597, 24, 0]];
  // The two points that the animation scores: the start, the length, the label, and the score after the point.
  // "game" means that the player wins the game.
  var NEW_POINTS = [
    { start: 2638, length: 17, winner: 0, label: "Point to J. Park", rally: [0, ["40", "15"]] },
    { start: 2671, length: 22, winner: 0, label: "Game to J. Park", rally: [0, "game"] }
  ];
  var START = { sets: [6, 4], games: [4, 2], points: ["30", "15"] };

  function copyScore(score) {
    return { sets: score.sets.slice(), games: score.games.slice(), points: score.points.slice() };
  }

  // The score after a rally. "game" gives the game to the winner and keeps "GAME" in the points.
  function scoreAfter(score, rally) {
    var next = copyScore(score);
    if (rally[1] === "game") {
      next.points = ["", ""];
      next.points[rally[0]] = "GAME";
    } else {
      next.points = rally[1].slice();
    }
    return next;
  }

  // The score of the next game, after "GAME".
  function nextGame(score) {
    var next = copyScore(score);
    next.games[score.points.indexOf("GAME")]++;
    next.points = ["0", "0"];
    return next;
  }

  // The index of the player who leads the game, or -1.
  function leader(points) {
    if (points[0] === "GAME") return 0;
    if (points[1] === "GAME") return 1;
    var a = POINT_ORDER.indexOf(points[0]), b = POINT_ORDER.indexOf(points[1]);
    return a > b ? 0 : b > a ? 1 : -1;
  }

  // Restarts the "flash" animation on the element.
  function flash(element) {
    element.classList.remove("flash");
    void element.offsetWidth;
    element.classList.add("flash");
  }

  // Builds the scoreboard in the empty element "el".
  function scoreboard(el) {
    el.innerHTML = '<div class="sb-title"><i></i><span></span></div>' + PLAYERS.map(function (p) {
      return '<div class="sb-row"><span class="sb-name"><b style="background:' + p.color + '"></b>' + p.name.toUpperCase() +
        '</span><span class="sb-set"></span><span class="sb-games"></span><span class="sb-pts"><span></span></span></div>';
    }).join("");
    var rows = el.querySelectorAll(".sb-row");
    var title = el.querySelector(".sb-title span");
    return {
      show: function (score, animate) {
        var lead = leader(score.points);
        for (var i = 0; i < 2; i++) {
          var row = rows[i];
          var games = row.querySelector(".sb-games"), pts = row.querySelector(".sb-pts");
          var ptsText = pts.querySelector("span");
          row.querySelector(".sb-set").textContent = score.sets[i];
          if (animate && games.textContent !== String(score.games[i])) flash(games);
          if (animate && ptsText.textContent !== score.points[i]) flash(ptsText);
          games.textContent = score.games[i];
          ptsText.textContent = score.points[i];
          pts.classList.toggle("word", score.points[i].length > 2);
          row.classList.toggle("lead", i === lead);
        }
      },
      style: function (style) {
        el.style.setProperty("--acc", style.accent);
        title.textContent = style.title;
        el.classList.remove("light", "band");
        if (style.theme) el.classList.add(style.theme);
      }
    };
  }

  function setPosition(el, position) {
    el.classList.remove("pos-tl", "pos-tr", "pos-bl", "pos-br");
    el.classList.add("pos-" + position);
  }

  function pad(n) { return String(n).padStart(2, "0"); }
  function short(t) { return [Math.floor(t / 3600), Math.floor((t % 3600) / 60), t % 60].map(pad).join(":"); }

  // The timeline shows 10 minutes of the match: 0:39:00 to 0:49:00. 824 px for 600 s.
  // The marks of the two new points are there from the start, because the timeline is not in the zoomed view.
  var T0 = 2340, T1 = 2940, TRACK_W = 824;
  function x(t) { return (t - T0) / (T1 - T0) * TRACK_W; }

  function timeline() {
    var html = '<div class="ui-tl"><div class="ui-ruler">';
    for (var r = T0 + 60; r < T1; r += 120) html += '<span style="left:' + x(r) + 'px">' + Math.floor(r / 60) + ":" + pad(r % 60) + "</span>";
    html += '</div><span class="ui-gut" style="top:47px">MARKS</span><span class="ui-gut" style="top:84px">VIDEO</span><div class="ui-marks">';
    EARLIER.map(function (p) { return [p[1], p[2]]; }).concat(NEW_POINTS.map(function (p) { return [p.start, p.length]; })).forEach(function (m) {
      html += '<div class="ui-mk" style="left:' + x(m[0]) + "px;width:" + (x(m[0] + m[1]) - x(m[0])) + 'px"></div>';
    });
    return html + '</div><div class="ui-vtrack"></div><div class="ui-ph" style="left:' + (76 + x(NEW_POINTS[0].start + NEW_POINTS[0].length)) + 'px"></div></div>';
  }

  D.register("scoring", function (demo) {
    var ui = D.stage(demo,
      D.nav(1) +
      '<div class="ui-video sc-board"><img src="/assets/frames/scoring.webp" loading="lazy" alt=""><div class="sb"></div></div>' +
      D.pointBar() +
      D.playBar() +
      '<div class="ui-side">' +
        D.pointHead(FIRST, TOTAL, false) +
        D.listHead(TOTAL, 9, 6) +
        '<div class="ui-thead"><span>#</span><span>Start</span><span style="text-align:right">Length</span><span></span></div>' +
        '<div class="ui-rows"></div>' +
        D.pointsFoot() +
      '</div>' +
      timeline(),
      {
        // 50% closer: the video with the scoreboard and the point bar.
        board: { x: 48, y: 0, w: 632, h: 360 }
      });

    var video = ui.q(".ui-video");
    var overlay = ui.q(".sb");
    var board = scoreboard(overlay);
    var now = ui.q(".ui-now");
    var startButton = ui.q(".pt-start"), startText = ui.q(".pt-start em");
    var winnerButtons = [ui.q(".pt-p1"), ui.q(".pt-p2")];
    var numberText = ui.q(".pt-num");
    var rows = ui.q(".ui-rows");
    var styleButton = ui.q(".pt-style");
    var score, number;

    function addRow(n, start, length, winner, isNew) {
      rows.querySelectorAll(".sel").forEach(function (row) { row.classList.remove("sel"); });
      rows.querySelectorAll(".new").forEach(function (row) { row.classList.remove("new"); });
      var row = document.createElement("div");
      row.className = "ui-row won ui-num sel" + (isNew ? " new" : "");
      row.style.setProperty("--w", PLAYERS[winner].color);
      row.innerHTML = '<span class="n">' + n + "</span><span>" + short(start) + '</span><span class="len">' + length + ' s</span><span class="star"></span>';
      rows.appendChild(row);
    }

    // The point waits for its end: the Point start button shows its start time.
    function startPoint(point) {
      startButton.classList.toggle("pending", point !== null);
      startText.textContent = point === null ? "" : short(point.start);
      if (point !== null) now.textContent = short(point.start + point.length) + ".0";
    }

    function setNumber(n) {
      number = n;
      numberText.textContent = n;
    }

    // The user clicks the winner at the end of the point. The score changes, and the point goes into the list.
    async function scorePoint(point) {
      var button = winnerButtons[point.winner];
      await ui.press(button);
      button.classList.add("hit");
      ui.label("ball", point.label);
      score = scoreAfter(score, point.rally);
      board.show(score, true);
      addRow(number, point.start, point.length, point.winner, true);
      startPoint(null);
    }

    // The video goes to the next point, and the user marks its start.
    async function nextPoint(point) {
      winnerButtons.forEach(function (b) { b.classList.remove("hit"); });
      video.classList.add("seek");
      await ui.sleep(250);
      if (score.points.indexOf("GAME") >= 0) {
        score = nextGame(score);
        board.show(score, true);
      }
      setNumber(number + 1);
      video.classList.remove("seek");
      if (point) {
        await ui.press(startButton);
        startPoint(point);
      }
    }

    function reset() {
      rows.innerHTML = "";
      EARLIER.forEach(function (p) { addRow(p[0], p[1], p[2], p[3], false); });
      winnerButtons.forEach(function (b) { b.classList.remove("hit"); });
      score = copyScore(START);
      board.show(score, false);
      setNumber(FIRST);
      startPoint(NEW_POINTS[0]);
      board.style(STYLES[0]);
      setPosition(overlay, "tr");
      video.classList.remove("seek");
    }

    function showEnd() {
      NEW_POINTS.forEach(function (p) {
        score = scoreAfter(score, p.rally);
        addRow(number, p.start, p.length, p.winner, false);
        setNumber(number + 1);
      });
      score = nextGame(score);
      board.show(score, false);
      startPoint(null);
      board.style(STYLES[1]);
    }

    async function play() {
      // The full app: the point plays.
      await ui.sleep(900);
      // 150% zoom on the video and the point bar.
      ui.camera("board");
      await ui.sleep(700);
      // Two clicks on the winner: a point, then the game.
      await scorePoint(NEW_POINTS[0]);
      await ui.sleep(1100);
      ui.hideLabel();
      await nextPoint(NEW_POINTS[1]);
      await ui.sleep(700);
      await scorePoint(NEW_POINTS[1]);
      await ui.sleep(1300);
      ui.hideLabel();
      await nextPoint(null);
      await ui.sleep(500);
      // Back to the full app: the scoreboard gets a different style and position.
      ui.camera("wide");
      await ui.sleep(700);
      await ui.press(styleButton);
      ui.label("palette", "Your style");
      board.style(STYLES[1]);
      setPosition(overlay, "tl");
      await ui.sleep(1300);
      board.style(STYLES[2]);
      setPosition(overlay, "bl");
      await ui.sleep(1700);
      ui.hideLabel();
    }

    ui.run(reset, play, showEnd);
  });
})(window.Demos);
