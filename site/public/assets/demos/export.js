// Feature 6, "Fast export": a copy of the Export tab.
// The full tab. The app tests the video encoders of the PC, the export runs on the graphics card,
// and a second export waits in the queue.
(function (D) {
  "use strict";

  var CHOICES = [
    { title: "Full video", length: "1:52:40", file: "full video", size: "about 3.1 GB", seconds: 690 },
    { title: "Only points", length: "49:35", file: "only points", size: "about 1.4 GB", seconds: 316 },
    { title: "Only favorites", length: "6:12", file: "only favorites", size: "about 180 MB", seconds: 41 }
  ];
  // The encoders, and the result of the test on this PC.
  var ENCODERS = [
    { name: "NVIDIA NVENC", works: true },
    { name: "AMD AMF", works: false },
    { name: "Intel Quick Sync", works: true },
    { name: "Software (x264)", works: true }
  ];
  var TILES = [["Scoreboard", true], ["Comments", true], ["Statistics card", true], ["Set summaries", false]];
  var CHECK = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9.5 16.6 5 12.1l1.4-1.4 3.1 3.1 8.1-8.1L19 7.1z"/></svg>';

  function minutes(seconds) {
    seconds = Math.max(0, Math.round(seconds));
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
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
            return '<div class="ex-tile"><span class="ui-check' + (t[1] ? " on" : "") + '">' + CHECK + "</span>" + t[0] + "</div>";
          }).join("") + "</div>" +
          '<div class="ex-step ex-gap"><i>3</i>Quality and file size</div>' +
          '<div class="ui-cap" style="margin-bottom:6px">Encoder</div>' +
          '<div class="ex-enc">' + ENCODERS.map(function (e) {
            return "<div><b>" + e.name + "</b><span></span></div>";
          }).join("") + "</div>" +
        "</div>" +
        '<div class="ex-foot"><small class="ex-summary"></small><span class="ui-btn lime ex-go">Export</span></div>' +
      "</div>" +
      '<div class="ex-side"><h4>Exports</h4><div class="ui-cap">Export queue</div><div class="ex-rows"></div></div>',
      views);

    var choices = ui.qa(".ex-choice"), encoders = ui.qa(".ex-enc > div");
    var summary = ui.q(".ex-summary"), go = ui.q(".ex-go"), rows = ui.q(".ex-rows");
    var choice = 1, encoder = 0;

    function selectChoice(index) {
      choice = index;
      choices.forEach(function (el, i) { el.classList.toggle("sel", i === index); });
      summary.textContent = CHOICES[index].title + " · " + CHOICES[index].length + " · " + CHOICES[index].size;
    }

    // The test state of an encoder: "", "test", "ok", or "no".
    function setEncoder(index, state) {
      var el = encoders[index];
      el.className = state;
      el.querySelector("span").innerHTML = state === "test" ? "Testing" : state === "ok" ? CHECK + "Works on this PC" : state === "no" ? "Not found" : "";
    }

    function selectEncoder(index) {
      encoder = index;
      encoders.forEach(function (el, i) { el.classList.toggle("sel", i === index); });
    }

    // A new row in the export queue. Returns the functions that change the row.
    function addRow(waiting) {
      var c = CHOICES[choice];
      var el = document.createElement("div");
      el.className = "ex-row" + (waiting ? " wait" : "");
      el.innerHTML = '<span class="ic">' + D.icon("film") + "</span>" +
        "<div><b>Club final 2026-10-04 – " + c.file + ".mp4</b><small>" + c.title + " · " + ENCODERS[encoder].name + '</small><div class="ex-prog"><i></i></div></div>' +
        '<div class="st"></div>';
      rows.appendChild(el);
      var bar = el.querySelector(".ex-prog i"), status = el.querySelector(".st"), sub = el.querySelector("small");
      var row = { progress: 0 };

      function show() {
        bar.style.width = row.progress * 100 + "%";
        if (row.progress >= 1) {
          el.classList.add("done");
          status.innerHTML = "Done<em>in " + minutes(c.seconds) + "</em>";
        } else if (el.classList.contains("wait")) {
          status.innerHTML = "Waiting<em>in the queue</em>";
        } else {
          status.innerHTML = Math.floor(row.progress * 100) + "%<em>" + minutes((1 - row.progress) * c.seconds) + " left</em>";
        }
      }

      // Moves the progress to "to" (0 to 1) in "ms" milliseconds.
      row.run = function (to, ms) {
        el.classList.remove("wait");
        sub.innerHTML = c.title + " · " + ENCODERS[encoder].name + ' · <span class="ex-speed">' + (encoder === 3 ? "1.6" : "9.4") + "× real time</span>";
        var from = row.progress;
        return ui.tween(ms, function (k) { row.progress = from + (to - from) * k; show(); });
      };
      show();
      return row;
    }

    function reset() {
      rows.innerHTML = "";
      selectChoice(1);
    }

    return {
      ui: ui, choices: choices, go: go, reset: reset, selectChoice: selectChoice,
      setEncoder: setEncoder, selectEncoder: selectEncoder, addRow: addRow
    };
  }

  // The encoder test, a fast export, and a second export in the queue.
  D.register("export", function (demo) {
    demo.classList.add("hud-top"); // The Export button is at the bottom.
    var ex = build(demo, {
      // 30% closer, on the bottom left corner: the encoders and the Export button.
      enc: { x: 0, y: D.STAGE_H - D.STAGE_H / 1.3, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 }
    });
    var ui = ex.ui;

    function reset() {
      ex.reset();
      ENCODERS.forEach(function (e, i) { ex.setEncoder(i, ""); });
      ex.selectEncoder(-1);
    }

    async function play() {
      await ui.sleep(600);
      ui.camera("enc");
      await ui.sleep(650);
      // The app tests each encoder on this PC.
      ui.label("bolt", "Finds the encoders of your PC");
      for (var i = 0; i < ENCODERS.length; i++) {
        ex.setEncoder(i, "test");
        await ui.sleep(380);
        ex.setEncoder(i, ENCODERS[i].works ? "ok" : "no");
      }
      ex.selectEncoder(0);
      await ui.sleep(1000);
      await ui.press(ex.go);
      ui.hideLabel();
      // The full app: the export runs on the graphics card.
      ui.camera("wide");
      var first = ex.addRow(false);
      await ui.sleep(300);
      ui.label("bolt", "Fast export on the graphics card");
      var running = first.run(0.55, 1700);
      await ui.sleep(900);
      // A second export waits in the queue.
      await ui.press(ex.choices[2]);
      ex.selectChoice(2);
      await ui.sleep(250);
      await ui.press(ex.go);
      var second = ex.addRow(true);
      ui.label("queue", "More exports wait in a queue");
      await running;
      await first.run(1, 1000);
      await second.run(1, 1200);
      ui.label("check", "Ready to share");
      await ui.sleep(1900);
      ui.hideLabel();
    }

    ui.run(reset, play, function () {
      ENCODERS.forEach(function (e, i) { ex.setEncoder(i, e.works ? "ok" : "no"); });
      ex.selectEncoder(0);
      ex.addRow(false).run(1, 1);
      ex.selectChoice(2);
      ex.addRow(false).run(1, 1);
    });
  });
})(window.Demos);
