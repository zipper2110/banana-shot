// Feature 6, "Fast and flexible export": a copy of the Export tab.
// The camera goes through the three steps of the setup: the content, the parts of the video, and the quality.
// Then the full tab: the first export runs, and two more exports wait in the queue.
(function (D) {
  "use strict";

  // "mb" is the file size and "seconds" is the export time, both in the original quality.
  var CHOICES = [
    { title: "Full video", length: "1:52:40", file: "full video", mb: 3100, seconds: 690 },
    { title: "Only points", length: "28:15", file: "only points", mb: 780, seconds: 173 },
    { title: "Only favorites", length: "6:12", file: "only favorites", mb: 180, seconds: 41 }
  ];
  // The quality tiles of the Simple mode (ExportSimplePreset in the app).
  var QUALITIES = [
    { title: "Original quality", summary: "Largest file, slowest export", size: 1, time: 1 },
    { title: "Balanced", summary: "Good quality, smaller file", size: 0.75, time: 0.8 },
    { title: "Fast export", summary: "Lower quality, smallest file", size: 0.5, time: 0.6 }
  ];
  var TILES = ["Scoreboard", "Comments", "Statistics card", "Set summaries"];
  var SCOREBOARD = 0, STATS_CARD = 2;
  var CHECK = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9.5 16.6 5 12.1l1.4-1.4 3.1 3.1 8.1-8.1L19 7.1z"/></svg>';

  function minutes(seconds) {
    seconds = Math.max(0, Math.round(seconds));
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
  }

  function fileSize(mb) {
    return mb >= 1000 ? "about " + (mb / 1000).toFixed(1) + " GB" : "about " + Math.round(mb / 10) * 10 + " MB";
  }

  function build(demo, views) {
    var ui = D.stage(demo,
      D.nav(6) +
      '<div class="ex-config">' +
        '<div class="ex-scroll">' +
          '<div class="ex-step"><i>1</i>Content</div>' +
          '<div class="ex-choices">' + CHOICES.map(function (c) {
            return '<div class="ex-choice"><span>' + c.title + '</span><small>' + c.length + "</small></div>";
          }).join("") + "</div>" +
          '<div class="ex-step ex-gap"><i>2</i>Include in the video</div>' +
          '<div class="ex-tiles">' + TILES.map(function (t) {
            return '<div class="ex-tile"><span class="ui-check">' + CHECK + "</span>" + t + "</div>";
          }).join("") + "</div>" +
          // The Simple / Advanced switch of the app. The animation stays in the Simple mode.
          '<div class="ex-step ex-gap"><i>3</i>Quality and file size<span class="ex-mode"><b class="on">Simple</b><b>Advanced</b></span></div>' +
          '<div class="ex-quality">' + QUALITIES.map(function (q) {
            return '<div class="ex-q"><b>' + q.title + "</b><span>" + q.summary + "</span><small></small></div>";
          }).join("") + "</div>" +
        "</div>" +
        '<div class="ex-foot"><small class="ex-summary"></small><span class="ui-btn lime ex-go">Export</span></div>' +
      "</div>" +
      '<div class="ex-side"><h4>Exports</h4><div class="ex-rows"></div></div>',
      views);

    var choices = ui.qa(".ex-choice"), tiles = ui.qa(".ex-tile"), qualities = ui.qa(".ex-q");
    var summary = ui.q(".ex-summary"), go = ui.q(".ex-go"), rows = ui.q(".ex-rows");
    var choice = 0, quality = 0;
    var queue = []; // The rows that wait, in the queue order.

    // Shows the selected content and quality, the file size of each quality tile, and the summary.
    function show() {
      var c = CHOICES[choice];
      choices.forEach(function (el, i) { el.classList.toggle("sel", i === choice); });
      qualities.forEach(function (el, i) {
        el.classList.toggle("sel", i === quality);
        el.querySelector("small").textContent = fileSize(c.mb * QUALITIES[i].size);
      });
      summary.textContent = c.title + " · " + c.length + " · " + fileSize(c.mb * QUALITIES[quality].size);
    }

    function selectChoice(index) { choice = index; show(); }
    function selectQuality(index) { quality = index; show(); }
    function setTile(index, on) { tiles[index].querySelector(".ui-check").classList.toggle("on", on); }

    // A new row in the export list. Returns the functions that change the row.
    function addRow(waiting) {
      var c = CHOICES[choice], q = QUALITIES[quality];
      var seconds = c.seconds * q.time;
      var el = document.createElement("div");
      el.className = "ex-row" + (waiting ? " wait" : "");
      el.innerHTML = '<span class="ic">' + D.icon("film") + "</span>" +
        "<div><b>Club final 2026-10-04 – " + c.file + ".mp4</b><small>" + c.title + " · " + q.title + '</small><div class="ex-prog"><i></i></div></div>' +
        '<div class="st"></div>';
      rows.appendChild(el);
      var bar = el.querySelector(".ex-prog i"), status = el.querySelector(".st");
      var row = { progress: 0 };

      row.show = function () {
        bar.style.width = row.progress * 100 + "%";
        if (row.progress >= 1) {
          el.classList.add("done");
          status.innerHTML = "Done<em>in " + minutes(seconds) + "</em>";
        } else if (el.classList.contains("wait")) {
          status.innerHTML = "Queued · " + (queue.indexOf(row) + 1) + "<em>Waiting</em>";
        } else {
          status.innerHTML = Math.floor(row.progress * 100) + "%<em>" + minutes((1 - row.progress) * seconds) + " left</em>";
        }
      };

      // Moves the progress to "to" (0 to 1) in "ms" milliseconds. A waiting row leaves the queue.
      row.run = function (to, ms) {
        if (el.classList.contains("wait")) {
          el.classList.remove("wait");
          queue.splice(queue.indexOf(row), 1);
          queue.forEach(function (r) { r.show(); });
        }
        var from = row.progress;
        return ui.tween(ms, function (k) { row.progress = from + (to - from) * k; row.show(); });
      };

      if (waiting) queue.push(row);
      row.show();
      return row;
    }

    function reset() {
      rows.innerHTML = "";
      queue = [];
      tiles.forEach(function (el, i) { setTile(i, false); });
      choice = 0;
      quality = 0;
      show();
    }

    return {
      ui: ui, choices: choices, tiles: tiles, qualities: qualities, go: go,
      reset: reset, selectChoice: selectChoice, selectQuality: selectQuality, setTile: setTile, addRow: addRow
    };
  }

  // The setup of an export, step by step, and then a queue of three exports.
  D.register("export", function (demo) {
    var W = 380, H = W * 9 / 16; // The views of the setup steps have the width of the setup column.
    var ex = build(demo, {
      content: { x: 48, y: 0, w: W, h: H },
      include: { x: 48, y: 156, w: W, h: H },
      quality: { x: 48, y: 262, w: W, h: H }
    });
    var ui = ex.ui;

    async function selectChoice(index) {
      await ui.press(ex.choices[index]);
      ex.selectChoice(index);
    }

    async function checkTile(index) {
      await ui.press(ex.tiles[index]);
      ex.setTile(index, true);
    }

    async function exportNow(waiting) {
      await ui.press(ex.go);
      return ex.addRow(waiting);
    }

    function reset() {
      demo.classList.remove("hud-top", "hud-side");
      ex.reset();
    }

    async function play() {
      await ui.sleep(500);
      // 1. The content: the full video, only the points, or only the favorites.
      ui.camera("content");
      await ui.sleep(600);
      ui.label("film", "Export the full match or only a part");
      await ui.sleep(1000);
      await selectChoice(1);
      await ui.sleep(1000);
      await selectChoice(2);
      await ui.sleep(1100);
      // 2. The parts of the video.
      ui.camera("include");
      ui.label("chart", "Add the scoreboard and the stats");
      await ui.sleep(900);
      await checkTile(SCOREBOARD);
      await ui.sleep(450);
      await checkTile(STATS_CARD);
      await ui.sleep(1000);
      // 3. The quality: from the original quality to a fast export.
      ui.camera("quality");
      ui.label("bolt", "Keep the quality or export fast");
      await ui.sleep(1300);
      await ui.press(ex.qualities[2]);
      ex.selectQuality(2);
      await ui.sleep(1200);
      // 4. The full tab: one export runs, and two more wait in the queue.
      ui.hideLabel();
      ui.camera("wide");
      demo.classList.add("hud-top", "hud-side"); // The Export button is at the bottom left.
      await ui.sleep(800);
      var first = await exportNow(false);
      var running = first.run(0.7, 3400);
      await ui.sleep(700);
      await selectChoice(1);
      await ui.sleep(250);
      var second = await exportNow(true);
      ui.label("queue", "More exports wait in a queue");
      await ui.sleep(600);
      await selectChoice(0);
      await ui.sleep(250);
      await exportNow(true);
      await running;
      await first.run(1, 800);
      ui.label("check", "Ready to share");
      await second.run(0.12, 1800);
      await ui.sleep(600);
      ui.hideLabel();
    }

    ui.run(reset, play, function () {
      demo.classList.add("hud-top", "hud-side");
      ex.setTile(SCOREBOARD, true);
      ex.setTile(STATS_CARD, true);
      ex.selectQuality(2);
      ex.selectChoice(2);
      ex.addRow(false).run(0.7, 1);
      ex.selectChoice(1);
      ex.addRow(true);
      ex.selectChoice(0);
      ex.addRow(true);
      ex.selectChoice(2);
    });
  });
})(window.Demos);
