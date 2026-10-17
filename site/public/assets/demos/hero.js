// The image at the top of the home page: a still copy of the Points tab.
// The video has the scoreboard, and the timeline shows the marked points.
// It does not move, so it does not use ui.run().
(function (D) {
  "use strict";

  // The timeline shows 10 minutes of the match: 0:36:00 to 0:46:00. 824 px for 600 s.
  var T0 = 2160, T1 = 2760, TRACK_W = 824;
  // [start, end, favorite]. WINNERS has the index of the player who won each point.
  var POINTS = [[2172, 2190], [2215, 2241, true], [2268, 2280], [2310, 2338], [2371, 2389], [2405, 2430], [2444, 2461], [2490, 2516]];
  var WINNERS = [0, 1, 0, 0, 1, 0, 1, 0];
  var FIRST_NUMBER = 22; // The number of the first point in the window.
  var NOW = 2516;

  function x(t) { return (t - T0) / (T1 - T0) * TRACK_W; }
  function pad(n) { return String(n).padStart(2, "0"); }
  function short(t) { return [Math.floor(t / 3600), Math.floor((t % 3600) / 60), t % 60].map(pad).join(":"); }

  D.register("hero", function (demo) {
    var ruler = "";
    for (var r = T0 + 60; r < T1; r += 120) ruler += '<span style="left:' + x(r) + 'px">' + Math.floor(r / 60) + ":" + pad(r % 60) + "</span>";
    var marks = POINTS.map(function (p) {
      return '<div class="ui-mk' + (p[2] ? " fav" : "") + '" style="left:' + x(p[0]) + "px;width:" + (x(p[1]) - x(p[0])) + 'px"></div>';
    }).join("");
    var rows = POINTS.map(function (p, i) {
      var last = i === POINTS.length - 1;
      return '<div class="ui-row won ui-num' + (last ? " sel" : "") + '" style="--w:' + D.PLAYERS[WINNERS[i]].color + '"><span class="n">' + (FIRST_NUMBER + i) + "</span><span>" + short(p[0]) +
        '</span><span class="len">' + (p[1] - p[0]) + ' s</span><span class="star">' + (p[2] ? "★" : "") + "</span></div>";
    }).join("");
    var players = [["J. Park", "6", "3", "40"], ["M. Silva", "4", "2", "15"]];

    var ui = D.stage(demo,
      D.nav(1) +
      '<div class="ui-video"><img src="/assets/frames/hero.webp" alt="">' +
        '<div class="sb pos-tl"><div class="sb-title"><i></i><span>CLUB LEAGUE</span></div>' +
        players.map(function (p, i) {
          return '<div class="sb-row' + (i === 0 ? " lead" : "") + '"><span class="sb-name"><b style="background:' + D.PLAYERS[i].color + '"></b>' + p[0].toUpperCase() +
            '</span><span class="sb-set">' + p[1] + '</span><span class="sb-games">' + p[2] + '</span><span class="sb-pts"><span>' + p[3] + "</span></span></div>";
        }).join("") +
        "</div>" +
      "</div>" +
      D.pointBar() +
      D.playBar(short(NOW) + ".0") +
      '<div class="ui-side">' +
        D.pointHead(FIRST_NUMBER + POINTS.length - 1, 74, false) +
        D.listHead(74, 9, 6) +
        '<div class="ui-thead"><span>#</span><span>Start</span><span style="text-align:right">Length</span><span></span></div>' +
        '<div class="ui-rows">' + rows + "</div>" +
        D.pointsFoot() +
      "</div>" +
      '<div class="ui-tl">' +
        '<div class="ui-ruler">' + ruler + "</div>" +
        '<span class="ui-gut" style="top:47px">MARKS</span><span class="ui-gut" style="top:84px">VIDEO</span>' +
        '<div class="ui-marks">' + marks + '</div><div class="ui-vtrack"></div>' +
        '<div class="ui-ph" style="left:' + (76 + x(NOW)) + 'px"></div>' +
      "</div>",
      { hero: { x: 48, y: 0, w: 912, h: 540 } });

    ui.camera("hero");
  });
})(window.Demos);
