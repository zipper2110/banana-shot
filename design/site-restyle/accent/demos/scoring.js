// Feature 2, "Scoreboard in the video".
// A copy of the Scoring tab. The user clicks the winner of two points. The score changes
// in the score panel and in the scoreboard on the video. Then the scoreboard gets a different style and position.
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
  // The list shows the points from LIST_FIRST to LIST_LAST. The points before FIRST are scored:
  // [number, winner, score after the point].
  // The zoomed view must show the rows of the two new points.
  var LIST_FIRST = 30, LIST_LAST = 35;
  var EARLIER = [[30, 0, "30-15"]];
  // The two points that the animation scores: the winner, the label, and the score after the point.
  // "game" means that the player wins the game.
  var NEW_POINTS = [
    { winner: 0, label: "Point to J. Park", list: "40-15", rally: [0, ["40", "15"]] },
    { winner: 0, label: "Game to J. Park", list: "Game", rally: [0, "game"] }
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

  function bar(color) { return '<b style="background:' + color + '"></b>'; }

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

  D.register("scoring", function (demo) {
    var ui = D.stage(demo,
      D.nav(2) +
      '<div class="sc-video"><img src="/assets/frames/scoring.webp" loading="lazy" alt=""><div class="sb"></div></div>' +
      '<div class="sc-bar">' +
        '<div class="sc-scrub ui-num"><span>00:41:30</span><div class="sc-track"><i></i></div><span>00:41:56</span></div>' +
        '<div class="ui-transport"><span class="ui-seek">−5s</span><span class="ui-seek">−1s</span>' +
          '<span class="ui-play"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></span>' +
          '<span class="ui-seek">+1s</span><span class="ui-seek">+5s</span></div>' +
      '</div>' +
      '<div class="sc-side">' +
        '<div class="sc-panel">' +
          '<div class="sc-nav"><span>‹ Previous</span><b class="ui-num">Point <span class="sc-num"></span> / ' + TOTAL + '</b><span>Next ›</span></div>' +
          '<div class="sc-table ui-num">' +
            '<div class="sc-thead"><span>After the point</span><span>Sets</span><span>Games</span><span>Points</span></div>' +
            PLAYERS.map(function (p) {
              return '<div class="sc-trow"><span class="who">' + bar(p.color) + p.name + '</span><span class="set"></span><span class="games"></span><span class="pts"></span></div>';
            }).join("") +
          '</div>' +
          '<div class="sc-ask">Who won the point?</div>' +
          '<div class="sc-btns">' +
            '<span class="sc-btn">' + bar(PLAYERS[0].color) + PLAYERS[0].name + '</span>' +
            '<span class="sc-btn none">No point</span>' +
            '<span class="sc-btn">' + bar(PLAYERS[1].color) + PLAYERS[1].name + '</span>' +
          '</div>' +
        '</div>' +
        '<div class="sc-head"><div><b>Points</b><span class="ui-num"><em class="sc-scored"></em>/' + TOTAL + ' scored</span></div><div class="sc-progress"><i></i></div></div>' +
        '<div class="sc-rows ui-num"></div>' +
        '<div class="sc-foot"><span>Scoring settings</span><span class="sc-style">Scoreboard style</span></div>' +
      '</div>',
      {
        // 30% closer, on the top right corner: the scoreboard in the video and the score panel.
        corner: { x: D.STAGE_W - D.STAGE_W / 1.3, y: 0, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 }
      });

    var video = ui.q(".sc-video");
    var overlay = ui.q(".sb");
    var board = scoreboard(overlay);
    var numberText = ui.q(".sc-num");
    var tableRows = ui.qa(".sc-trow");
    var buttons = ui.qa(".sc-btn");
    var scored = ui.q(".sc-scored"), progress = ui.q(".sc-progress i");
    var rows = ui.q(".sc-rows");
    var styleButton = ui.q(".sc-style");
    var score, number;

    // Shows the score in the score panel and in the video.
    function showScore(animate) {
      var lead = leader(score.points);
      for (var i = 0; i < 2; i++) {
        var row = tableRows[i];
        var games = row.querySelector(".games"), pts = row.querySelector(".pts");
        row.querySelector(".set").textContent = score.sets[i];
        if (animate && games.textContent !== String(score.games[i])) flash(games);
        var ptsText = score.points[i] === "GAME" ? "Game" : score.points[i];
        if (animate && pts.textContent !== ptsText) flash(pts);
        games.textContent = score.games[i];
        pts.textContent = ptsText;
        row.classList.toggle("lead", i === lead);
      }
      board.show(score, animate);
    }

    function rowFor(n) { return rows.querySelector('[data-n="' + n + '"]'); }

    function setNumber(n) {
      number = n;
      numberText.textContent = n;
      rows.querySelectorAll(".cur").forEach(function (row) { row.classList.remove("cur"); });
      if (rowFor(n)) rowFor(n).classList.add("cur");
      scored.textContent = n - 1;
      progress.style.width = ((n - 1) / TOTAL * 100) + "%";
    }

    // Builds the list rows. A point that is not scored has no winner.
    function buildRows() {
      var html = "";
      for (var n = LIST_FIRST; n <= LIST_LAST; n++) {
        html += '<div class="sc-row" data-n="' + n + '"><span class="n">' + n + '</span><span class="who">—</span><span class="score"></span></div>';
      }
      rows.innerHTML = html;
    }

    function fillRow(n, winner, text, isNew) {
      rows.querySelectorAll(".new").forEach(function (row) { row.classList.remove("new"); });
      var row = rowFor(n);
      row.querySelector(".who").innerHTML = bar(PLAYERS[winner].color) + PLAYERS[winner].name;
      row.querySelector(".score").textContent = text;
      row.classList.add("won");
      if (isNew) row.classList.add("new");
    }

    // The user clicks the winner of the point. The score changes, and the point goes into the list.
    async function scorePoint(point) {
      await ui.press(buttons[point.winner === 0 ? 0 : 2]);
      ui.label("ball", point.label);
      score = scoreAfter(score, point.rally);
      showScore(true);
      fillRow(number, point.winner, point.list, true);
      scored.textContent = number;
      progress.style.width = (number / TOTAL * 100) + "%";
    }

    // The app goes to the next point.
    async function nextPoint() {
      video.classList.add("seek");
      await ui.sleep(200);
      if (score.points.indexOf("GAME") >= 0) {
        score = nextGame(score);
        showScore(true);
      }
      setNumber(number + 1);
      video.classList.remove("seek");
    }

    function reset() {
      buildRows();
      EARLIER.forEach(function (p) { fillRow(p[0], p[1], p[2], false); });
      score = copyScore(START);
      showScore(false);
      setNumber(FIRST);
      board.style(STYLES[0]);
      setPosition(overlay, "tr");
      video.classList.remove("seek");
    }

    function showEnd() {
      NEW_POINTS.forEach(function (p) {
        score = scoreAfter(score, p.rally);
        fillRow(number, p.winner, p.list, false);
        setNumber(number + 1);
      });
      score = nextGame(score);
      showScore(false);
      board.style(STYLES[1]);
    }

    async function play() {
      // The full app: the match plays.
      await ui.sleep(900);
      // 130% zoom on the top right corner.
      ui.camera("corner");
      await ui.sleep(700);
      // Two clicks: a point, then the game.
      await scorePoint(NEW_POINTS[0]);
      await ui.sleep(1100);
      ui.hideLabel();
      await nextPoint();
      await ui.sleep(350);
      await scorePoint(NEW_POINTS[1]);
      await ui.sleep(1300);
      ui.hideLabel();
      await nextPoint();
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
