// Feature 3, "Match statistics": a copy of the Statistics tab.
// The full tab. The user adds a row and the momentum chart to the statistics card in the video.
(function (D) {
  "use strict";

  var PLAYERS = D.PLAYERS;
  var ROWS = [
    { group: "SERVE", label: "Points won on serve", match: [38, 29], set1: [20, 15], inVideo: true },
    { label: "Service games won", match: [9, 7], set1: [5, 4], inVideo: true },
    { label: "Aces", match: [4, 2], set1: [2, 1], inVideo: false },
    { group: "RETURN", label: "Break points won", match: [3, 1], set1: [2, 1], inVideo: true },
    { group: "POINT LENGTH", label: "Short points won", match: [21, 17], set1: [11, 9], inVideo: true },
    { label: "Long points won", match: [12, 14], set1: [6, 8], inVideo: false }
  ];
  // The winner of each point of the match, for the momentum chart: 1 is J. Park, 2 is M. Silva.
  // M. Silva leads first, then J. Park comes back.
  var WINNERS = "2212221211222122" + "1121112111211121121112211121" + "2211121";
  var CHECK = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9.5 16.6 5 12.1l1.4-1.4 3.1 3.1 8.1-8.1L19 7.1z"/></svg>';

  // The momentum chart: the lead of J. Park in points, from the first point to the last point.
  // Above the middle line, J. Park leads. Below it, M. Silva leads.
  function chartSvg(width, height, clipId) {
    var lead = 0, max = 1, values = [0];
    for (var i = 0; i < WINNERS.length; i++) {
      lead += WINNERS[i] === "1" ? 1 : -1;
      values.push(lead);
      max = Math.max(max, Math.abs(lead));
    }
    var mid = height / 2, step = width / (values.length - 1);
    var line = values.map(function (v, i) { return (i ? "L" : "M") + (i * step).toFixed(1) + " " + (mid - v / max * (mid - 4)).toFixed(1); }).join(" ");
    var area = line + " L" + width + " " + mid + " L0 " + mid + " Z";
    return '<svg viewBox="0 0 ' + width + " " + height + '" preserveAspectRatio="none">' +
      '<defs><clipPath id="' + clipId + '"><rect class="reveal" x="0" y="0" width="' + width + '" height="' + height + '"/></clipPath>' +
      '<clipPath id="' + clipId + 't"><rect x="0" y="0" width="' + width + '" height="' + mid + '"/></clipPath>' +
      '<clipPath id="' + clipId + 'b"><rect x="0" y="' + mid + '" width="' + width + '" height="' + mid + '"/></clipPath></defs>' +
      '<line x1="0" x2="' + width + '" y1="' + mid + '" y2="' + mid + '" stroke="#333" stroke-width="1"/>' +
      '<g clip-path="url(#' + clipId + ')">' +
        '<path d="' + area + '" fill="' + PLAYERS[0].color + '" fill-opacity="0.35" clip-path="url(#' + clipId + 't)"/>' +
        '<path d="' + area + '" fill="' + PLAYERS[1].color + '" fill-opacity="0.35" clip-path="url(#' + clipId + 'b)"/>' +
        '<path d="' + line + '" fill="none" stroke="#e8e8e8" stroke-width="1.5" vector-effect="non-scaling-stroke"/>' +
      "</g></svg>";
  }

  var chartCount = 0;

  // Builds the Statistics tab. Returns the stage and the functions that change the copy.
  function build(demo, views) {
    var id = "stc" + (chartCount++);
    var table = "";
    ROWS.forEach(function (row, i) {
      if (row.group) table += '<div class="st-gt"><div class="t">' + row.group + '</div><div class="st-lane" data-group="' + i + '"></div></div>';
      table += '<div class="st-row" data-row="' + i + '"><span class="v p1"></span>' +
        '<div class="mid">' + row.label + '<div class="st-bar"><i style="background:' + PLAYERS[0].color + '"></i><i style="background:' + PLAYERS[1].color + '"></i></div></div>' +
        '<span class="v p2"></span><div class="st-lane"><span class="ui-check">' + CHECK + "</span></div></div>";
    });

    var ui = D.stage(demo,
      D.nav(3) +
      '<div class="st-left">' +
        '<div class="st-thead">' +
          '<div class="p1"><b style="background:' + PLAYERS[0].color + '"></b>' + PLAYERS[0].name + '</div>' +
          '<div class="mid">Statistic</div>' +
          '<div class="p2">' + PLAYERS[1].name + '<b style="background:' + PLAYERS[1].color + '"></b></div>' +
          '<div class="st-lane">In video</div>' +
        '</div>' +
        '<div class="st-scope"><span class="sel">Match</span><span>Set 1 · 6-4</span><span>Set 2 · 4-2</span></div>' +
        '<div class="st-mo"><div class="st-mo-main"><b>Momentum</b>' + chartSvg(360, 78, id) + '</div>' +
          '<div class="st-lane"><span class="ui-check st-mo-check">' + CHECK + "</span></div></div>" +
        table +
      '</div>' +
      '<div class="st-col">' +
        '<h4>In the video</h4><div class="st-sub"></div>' +
        '<div class="st-prev"><img class="ui-photo" src="/assets/frames/stats.webp" loading="lazy" alt=""><div class="st-pcard"></div></div>' +
        '<div class="st-pager"><span class="ui-btn">‹ Previous page</span><span class="st-page"></span><span class="ui-btn">Next page ›</span></div>' +
        '<div class="st-field"><div><span>Card transparency</span><b>20 %</b></div><div class="ui-slider" style="--v:0.25"><i></i><b></b></div></div>' +
        '<div class="st-help">The card shows at the end of the exported video. A summary card can show after each set.</div>' +
      '</div>',
      views);

    var scope = ui.qa(".st-scope span");
    var rowEls = ui.qa(".st-row");
    var reveal = ui.q("#" + id + " .reveal");
    var moCheck = ui.q(".st-mo-check");
    var card = ui.q(".st-pcard"), sub = ui.q(".st-sub"), pageText = ui.q(".st-page");
    var state = { values: [], inVideo: [], momentum: false, page: 0 };

    // Shows the values in the table. "values" has a pair of numbers for each row.
    function setValues(values) {
      state.values = values;
      rowEls.forEach(function (el, i) {
        var v = values[i], total = v[0] + v[1];
        var p1 = el.querySelector(".p1"), p2 = el.querySelector(".p2");
        p1.textContent = v[0];
        p2.textContent = v[1];
        p1.classList.toggle("lead", v[0] > v[1]);
        p2.classList.toggle("lead", v[1] > v[0]);
        var bars = el.querySelectorAll(".st-bar i");
        bars[0].style.flexGrow = total ? v[0] / total : 1;
        bars[1].style.flexGrow = total ? v[1] / total : 1;
      });
    }

    // Shows the part of the momentum chart from the start to k (0 to 1).
    function setChart(k) { reveal.setAttribute("width", 360 * k); }

    function selectScope(index) {
      scope.forEach(function (el, i) { el.classList.toggle("sel", i === index); });
    }

    function setInVideo(i, on) {
      state.inVideo[i] = on;
      rowEls[i].classList.toggle("inv", on);
      rowEls[i].querySelector(".ui-check").classList.toggle("on", on);
      // The group titles show how many rows of the group are in the video.
      ui.qa("[data-group]").forEach(function (el) {
        var first = Number(el.dataset.group), last = first + 1;
        while (last < ROWS.length && !ROWS[last].group) last++;
        var count = 0;
        for (var r = first; r < last; r++) if (state.inVideo[r]) count++;
        el.textContent = count + " of " + (last - first);
      });
    }

    function setMomentum(on) {
      state.momentum = on;
      moCheck.classList.toggle("on", on);
    }

    function pageCount() { return state.momentum ? 2 : 1; }

    // Shows a page of the statistics card in the preview: "stats", "momentum", or "set".
    function renderCard(kind, newRow) {
      var rows = "", count = 0;
      var values = kind === "set" ? ROWS.map(function (r) { return r.set1; }) : ROWS.map(function (r) { return r.match; });
      ROWS.forEach(function (row, i) {
        if (!state.inVideo[i]) return;
        count++;
        var v = values[i];
        rows += '<div class="st-prow' + (i === newRow ? " new" : "") + '"><b class="' + (v[0] > v[1] ? "lead" : "") + '">' + v[0] + "</b><span>" + row.label +
          '</span><b class="' + (v[1] > v[0] ? "lead" : "") + '">' + v[1] + "</b></div>";
      });
      var title = kind === "set" ? "SET 1 · 6-4" : kind === "momentum" ? "MOMENTUM" : "MATCH STATISTICS";
      card.innerHTML = '<div class="st-ptitle">' + title + "</div>" +
        '<div class="st-phead"><span>' + PLAYERS[0].name.toUpperCase() + "</span><span>" + PLAYERS[1].name.toUpperCase() + "</span></div>" +
        (kind === "momentum" ? '<div class="st-pchart">' + chartSvg(226, 90, id + "p") + "</div>" : rows);
      sub.textContent = "Statistics card · " + count + " rows" + (state.momentum ? " and the momentum chart" : "");
      pageText.textContent = "Page " + (kind === "momentum" ? 2 : 1) + " of " + pageCount();
    }

    // Changes the page of the card with a short fade.
    async function flipCard(kind) {
      card.classList.add("out");
      await ui.sleep(220);
      renderCard(kind);
      card.classList.remove("out");
    }

    function reset() {
      rowEls.forEach(function (el) { el.classList.remove("new"); });
      card.parentNode.classList.remove("ui-glow");
      ROWS.forEach(function (row, i) { setInVideo(i, row.inVideo); });
      setMomentum(false);
      selectScope(0);
      setValues(ROWS.map(function (r) { return r.match; }));
      setChart(1);
      card.classList.remove("out");
      renderCard("stats");
    }

    return {
      ui: ui, scope: scope, rowEls: rowEls, moCheck: moCheck, card: card, state: state, reset: reset,
      setValues: setValues, setChart: setChart, selectScope: selectScope, setInVideo: setInVideo,
      setMomentum: setMomentum, renderCard: renderCard, flipCard: flipCard
    };
  }

  // The user adds a row and the momentum chart to the card, then the card pages show.
  D.register("stats", function (demo) {
    var st = build(demo, {
      // 30% closer, on the top right corner: the "In video" column and the card.
      lane: { x: D.STAGE_W - D.STAGE_W / 1.3, y: 0, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 },
      preview: { x: 504, y: 26, w: 456, h: 290 }
    });
    var ui = st.ui;
    var ACES = 2;

    async function play() {
      await ui.sleep(700);
      ui.camera("lane");
      await ui.sleep(700);
      // A new row for the card.
      var check = st.rowEls[ACES].querySelector(".ui-check");
      await ui.press(check);
      st.setInVideo(ACES, true);
      st.rowEls[ACES].classList.add("new");
      st.renderCard("stats", ACES);
      ui.label("check", "Add to the video");
      await ui.sleep(1400);
      // The momentum chart is a second page of the card.
      await ui.press(st.moCheck);
      st.setMomentum(true);
      st.renderCard("stats");
      ui.label("chart", "Add the momentum chart");
      await ui.sleep(1300);
      ui.hideLabel();
      // Closer on the card: its pages.
      ui.camera("preview");
      await ui.sleep(700);
      ui.label("film", "Statistics card at the end");
      await ui.sleep(1500);
      await st.flipCard("momentum");
      ui.label("chart", "Momentum chart");
      await ui.sleep(1600);
      await st.flipCard("set");
      ui.label("flag", "Summary after each set");
      await ui.sleep(1900);
      ui.hideLabel();
      ui.camera("wide");
      await ui.sleep(900);
      st.rowEls[ACES].classList.remove("new");
    }

    ui.run(st.reset, play, function () {
      st.setInVideo(ACES, true);
      st.setMomentum(true);
      st.renderCard("stats");
    });
  });
})(window.Demos);
