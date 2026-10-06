// Feature 4, "Comments on the video": a copy of the Points tab with the comments.
// The full tab. The user pauses the video and adds a comment at that moment.
(function (D) {
  "use strict";

  // The timeline shows 10 minutes of the match: 0:36:00 to 0:46:00. 824 px for 600 s.
  var T0 = 2160, T1 = 2760, TRACK_W = 824;
  var MARKS = [[2172, 2190], [2215, 2241], [2268, 2280], [2310, 2338], [2371, 2389], [2405, 2430], [2444, 2461], [2490, 2516]];
  var COMMENTS = [
    { t: 2218, text: "Watch the ball, not the net", style: "dark" },
    { t: 2322, text: "Good: early split step", style: "box" },
    { t: 2412, text: "Racket back sooner", style: "blue" }
  ];
  var NEW_COMMENT = { t: 2470, text: "Step in and take it early", style: "box" };

  function x(t) { return (t - T0) / (T1 - T0) * TRACK_W; }
  function pad(n) { return String(n).padStart(2, "0"); }
  function short(t) {
    t = Math.floor(t);
    return [Math.floor(t / 3600), Math.floor((t % 3600) / 60), t % 60].map(pad).join(":");
  }
  function rulerText(t) { return Math.floor(t / 60) + ":" + pad(t % 60); }

  function build(demo, views) {
    var ruler = "";
    for (var r = T0 + 60; r < T1; r += 120) ruler += '<span style="left:' + x(r) + 'px">' + rulerText(r) + "</span>";
    var marks = MARKS.map(function (m) {
      return '<div class="ui-mk" style="left:' + x(m[0]) + "px;width:" + (x(m[1]) - x(m[0])) + 'px"></div>';
    }).join("");

    var ui = D.stage(demo,
      D.nav(1) +
      '<div class="ui-video"><img src="/assets/frames/comments.webp" loading="lazy" alt=""><div class="cm-cap"></div>' +
        '<div class="cm-dialog"><h5>Add comment<span class="cm-at ui-num"></span></h5><div class="cm-field"></div>' +
        '<div class="cm-btns"><span class="ui-btn">Cancel</span><span class="ui-btn lime cm-add">Add</span></div></div>' +
      '</div>' +
      '<div class="ui-playbar">' +
        '<span class="ui-now ui-num"></span>' +
        '<div class="ui-transport"><span class="ui-seek">−5s</span><span class="ui-seek">−1s</span>' +
          '<span class="ui-play"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></span>' +
          '<span class="ui-seek">+1s</span><span class="ui-seek">+5s</span></div>' +
        '<span class="ui-addc">Comment</span>' +
      '</div>' +
      '<div class="ui-side">' +
        '<div class="ui-list-head" style="padding-top:12px"><b>Comments</b><div class="ui-counts"><span><em class="cm-count"></em> comments</span></div></div>' +
        '<div class="cm-list"></div>' +
      '</div>' +
      '<div class="ui-tl">' +
        '<div class="ui-ruler">' + ruler + '</div>' +
        '<span class="ui-gut" style="top:47px">MARKS</span><span class="ui-gut" style="top:85px">COMMENTS</span>' +
        '<div class="ui-marks">' + marks + '</div><div class="cm-lane"></div>' +
        '<div class="ui-ph"></div>' +
      '</div>',
      views);

    var now = ui.q(".ui-now"), playhead = ui.q(".ui-ph"), playButton = ui.q(".ui-play");
    var caption = ui.q(".cm-cap"), list = ui.q(".cm-list"), lane = ui.q(".cm-lane"), count = ui.q(".cm-count");
    var time = T0;

    function setTime(t) {
      time = t;
      now.textContent = short(t) + "." + Math.floor((t % 1) * 10);
      playhead.style.left = (76 + x(t)) + "px";
    }

    // Moves the playhead to the time "to" in "ms" milliseconds, at a constant speed.
    // onTime(t) runs on each frame.
    function play(to, ms, onTime) {
      var from = time;
      return ui.tween(ms, function (k) {
        setTime(from + (to - from) * k);
        if (onTime) onTime(time);
      }, true);
    }

    function setPlaying(on) { playButton.classList.toggle("playing", on); }

    function showCaption(comment) {
      caption.className = "cm-cap show " + comment.style;
      caption.textContent = comment.text;
    }

    function hideCaption() { caption.classList.remove("show"); }

    function addComment(comment, isNew) {
      var mk = document.createElement("div");
      mk.className = "cm-mk" + (isNew ? " new" : "");
      mk.style.left = x(comment.t) + "px";
      mk.innerHTML = D.icon("comment");
      lane.appendChild(mk);
      var row = document.createElement("div");
      row.className = "cm-row" + (isNew ? " new" : "");
      row.innerHTML = '<span class="t">' + short(comment.t) + "</span><span>" + comment.text + "</span>";
      list.appendChild(row);
      count.textContent = list.children.length;
      return { mk: mk, row: row };
    }

    // Marks the comment as the current one in the list and on the timeline.
    function highlight(index) {
      Array.prototype.forEach.call(list.children, function (row, i) { row.classList.toggle("on", i === index); });
      Array.prototype.forEach.call(lane.children, function (mk, i) { mk.classList.toggle("hit", i === index); });
    }

    function resetComments() {
      list.innerHTML = "";
      lane.innerHTML = "";
      COMMENTS.forEach(function (c) { addComment(c, false); });
      hideCaption();
    }

    return {
      ui: ui, setTime: setTime, play: play, setPlaying: setPlaying, showCaption: showCaption, hideCaption: hideCaption,
      addComment: addComment, highlight: highlight, resetComments: resetComments,
      list: list, playButton: playButton, time: function () { return time; }
    };
  }

  // Pause, click "Comment", type, and the comment shows on the video.
  D.register("comments", function (demo) {
    var cm = build(demo, {
      // 30% closer, on the bottom left corner: the video, the "Comment" button, and the timeline.
      bottom: { x: 0, y: D.STAGE_H - D.STAGE_H / 1.3, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 }
    });
    var ui = cm.ui;
    var dialog = ui.q(".cm-dialog"), field = ui.q(".cm-field"), addButton = ui.q(".cm-add");
    var commentButton = ui.q(".ui-addc");

    function reset() {
      cm.resetComments();
      cm.highlight(-1);
      cm.setTime(NEW_COMMENT.t - 14);
      cm.setPlaying(true);
      dialog.classList.remove("show");
      field.textContent = "";
      ui.q(".cm-at").textContent = "at " + short(NEW_COMMENT.t);
    }

    async function play() {
      // The video plays, then the user pauses it.
      await cm.play(NEW_COMMENT.t, 1100);
      await ui.press(cm.playButton);
      cm.setPlaying(false);
      ui.camera("bottom");
      await ui.sleep(650);
      // "Comment", then the text.
      await ui.press(commentButton);
      dialog.classList.add("show");
      ui.label("comment", "Add a comment");
      await ui.sleep(450);
      await ui.type(field, NEW_COMMENT.text, 42);
      await ui.sleep(450);
      await ui.press(addButton);
      dialog.classList.remove("show");
      cm.addComment(NEW_COMMENT, true);
      cm.highlight(COMMENTS.length);
      cm.showCaption(NEW_COMMENT);
      ui.label("comment", "At this moment of the match");
      await ui.sleep(1500);
      ui.hideLabel();
      // Back to the full app: the video plays with the comment.
      ui.camera("wide");
      cm.setPlaying(true);
      await cm.play(NEW_COMMENT.t + 5, 1500);
      ui.label("film", "The export shows it on the video");
      await cm.play(NEW_COMMENT.t + 8, 1400);
      cm.hideCaption();
      ui.hideLabel();
      await cm.play(NEW_COMMENT.t + 10, 600);
    }

    ui.run(reset, play, function () {
      cm.addComment(NEW_COMMENT, false);
      cm.highlight(COMMENTS.length);
      cm.showCaption(NEW_COMMENT);
      cm.setTime(NEW_COMMENT.t);
    });
  });
})(window.Demos);
