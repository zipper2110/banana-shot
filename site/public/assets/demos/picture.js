// Feature 5, "Color and framing": a copy of the Colors & Transform tab.
// The full tab. The user moves the sliders one by one, and the video changes with each slider.
// The Colors sub-tab comes first, then the Transform sub-tab. At the end, the user adds a change point.
(function (D) {
  "use strict";

  // The sliders: the range, the start value, and the value at the end of the animation.
  // "mid" sliders have the zero in the middle.
  var ADJUST = [
    { group: "Light", key: "brightness", label: "Brightness", min: -100, max: 100, end: 30, mid: true },
    { key: "contrast", label: "Contrast", min: -100, max: 100, end: 22, mid: true },
    { key: "highlights", label: "Highlights", min: -100, max: 100, end: -15, mid: true },
    { key: "shadows", label: "Shadows", min: -100, max: 100, end: 45, mid: true },
    { group: "Color", key: "saturation", label: "Saturation", min: -100, max: 100, end: 35, mid: true },
    { key: "temperature", label: "Temperature", min: -100, max: 100, end: 12, mid: true }
  ];
  var CROP = [
    { group: "Crop", key: "zoom", label: "Zoom", min: 100, max: 200, start: 100, end: 116, unit: "%" },
    { key: "panX", label: "Pan X", min: -100, max: 100, end: -10, mid: true },
    { key: "panY", label: "Pan Y", min: -100, max: 100, end: 8, mid: true },
    { group: "Rotation", key: "rotation", label: "Rotation", min: -45, max: 45, end: 3, mid: true, unit: "°" },
    { key: "fine", label: "Fine rotation", min: -5, max: 5, end: 0.6, mid: true, unit: "°", digits: 1 }
  ];
  var TILT = -3.6; // The camera of the recording is not level.
  var CHANGE_AT = "00:41:30"; // The change point goes at the playhead.

  // The look of the video for the slider values. The start values give a dark, flat, and tilted image.
  function filterFor(v) {
    var brightness = 0.6 + v.brightness / 30 * 0.36 + v.shadows / 45 * 0.08;
    var contrast = 0.78 + v.contrast / 22 * 0.3 - v.highlights / 15 * 0.02;
    var saturate = 0.5 + v.saturation / 35 * 0.65;
    return "brightness(" + brightness + ") contrast(" + contrast + ") saturate(" + saturate + ") sepia(" + Math.max(0, v.temperature) / 100 + ")";
  }

  function transformFor(v) {
    var angle = TILT + v.rotation + v.fine;
    return "translate(" + v.panX * 0.3 + "%," + v.panY * 0.3 + "%) rotate(" + angle + "deg) scale(" + (1.12 * v.zoom / 100) + ")";
  }

  function sliders(list) {
    return list.map(function (s) {
      return (s.group ? (s.key === list[0].key ? "" : "</div>") + '<div class="pc-group"><div class="ui-cap">' + s.group + "</div>" : "") +
        '<div class="pc-line" data-key="' + s.key + '"><span>' + s.label + '</span><div class="ui-slider' + (s.mid ? " mid" : "") +
        '"><i></i><b></b></div><span class="val"></span></div>';
    }).join("") + "</div>";
  }

  function build(demo, views) {
    var ui = D.stage(demo,
      D.nav(3) +
      '<div class="pc-video"><div class="pc-frame"><img class="pc-after" src="/assets/frames/picture.webp" loading="lazy" alt=""></div><div class="pc-grid"></div></div>' +
      '<div class="pc-bar">' +
        '<div class="sc-scrub ui-num"><span>00:41:30</span><div class="sc-track"><i></i></div><span>01:52:40</span></div>' +
        '<div class="ui-transport"><span class="ui-seek">−5s</span><span class="ui-seek">−1s</span>' +
          '<span class="ui-play"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></span>' +
          '<span class="ui-seek">+1s</span><span class="ui-seek">+5s</span></div>' +
      '</div>' +
      '<div class="pc-panel">' +
        '<div class="pc-tabs"><span class="pc-tab-adjust">Colors</span><span class="pc-tab-crop">Transform</span></div>' +
        '<div class="pc-page pc-adjust"><div class="pc-head"><h4>Color grade<span class="pc-status"></span></h4><span class="ui-btn">Reset all</span></div>' + sliders(ADJUST) + '</div>' +
        '<div class="pc-page pc-crop"><div class="pc-head"><h4>Transform<span class="pc-status"></span></h4><span class="ui-btn">Reset all</span></div>' + sliders(CROP) + '</div>' +
        '<div class="pc-change"><div class="ui-cap">Change points</div><b class="pc-change-status"></b>' +
          '<div class="pc-change-btns"><span class="ui-btn pc-add">+ Add here</span><span class="ui-btn off">Remove</span><span class="ui-btn off">‹</span><span class="ui-btn off">›</span></div>' +
          '<p>Did the light change or did the phone move? Add a change point there. It blends over 1 second.</p></div>' +
      '</div>',
      views);

    var all = ADJUST.concat(CROP);
    var values = {};
    var frame = ui.q(".pc-frame"), after = ui.q(".pc-after"), grid = ui.q(".pc-grid");
    var tabs = { adjust: ui.q(".pc-tab-adjust"), crop: ui.q(".pc-tab-crop") };
    var changeStatus = ui.q(".pc-change-status"), addButton = ui.q(".pc-add");

    function spec(key) { return all.filter(function (s) { return s.key === key; })[0]; }
    function line(key) { return ui.q('.pc-line[data-key="' + key + '"]'); }
    function startOf(s) { return s.start || 0; }

    function render() {
      after.style.filter = filterFor(values);
      frame.style.transform = transformFor(values);
    }

    function setValue(key, value) {
      var s = spec(key);
      values[key] = value;
      var el = line(key);
      el.querySelector(".ui-slider").style.setProperty("--v", (value - s.min) / (s.max - s.min));
      var rounded = Number(value.toFixed(s.digits || 0)) || 0; // "|| 0" changes -0 to 0.
      var shown = rounded.toFixed(s.digits || 0);
      el.querySelector(".val").textContent = (rounded > 0 && s.mid ? "+" : "") + shown + (s.unit || "");
      el.classList.toggle("changed", value !== startOf(s));
      showStatus(key);
    }

    // "All at default" or "2 of 6 changed" in the head of the sub-tab of the slider.
    function showStatus(key) {
      var list = ADJUST.indexOf(spec(key)) >= 0 ? ADJUST : CROP;
      var changed = list.filter(function (s) { return values[s.key] !== undefined && values[s.key] !== startOf(s); }).length;
      ui.q(list === ADJUST ? ".pc-adjust .pc-status" : ".pc-crop .pc-status").textContent =
        changed === 0 ? "All at default" : changed + " of " + list.length + " changed";
    }

    // Moves the sliders to their end values together in "ms" milliseconds.
    async function slide(keys, ms) {
      var sliderEls = keys.map(function (k) { return line(k).querySelector(".ui-slider"); });
      sliderEls.forEach(function (el) { el.classList.add("drag"); });
      await ui.tween(ms, function (k) {
        keys.forEach(function (key) {
          var s = spec(key);
          setValue(key, startOf(s) + (s.end - startOf(s)) * k);
        });
        render();
      });
      sliderEls.forEach(function (el) { el.classList.remove("drag"); });
    }

    function showPage(name) {
      ["adjust", "crop"].forEach(function (page) {
        ui.q(".pc-" + page).classList.toggle("on", name === page);
        tabs[page].classList.toggle("on", name === page);
      });
    }

    // Opens the Transform sub-tab with a click.
    async function openCrop() {
      await ui.press(tabs.crop);
      showPage("crop");
    }

    function setChangePoint(added) {
      changeStatus.textContent = added ? "Change point 1 of 1, at " + CHANGE_AT : "No change points";
      changeStatus.classList.toggle("on", added);
    }

    // Adds a change point at the playhead.
    async function addChangePoint() {
      await ui.press(addButton);
      setChangePoint(true);
    }

    function reset() {
      all.forEach(function (s) { setValue(s.key, startOf(s)); });
      render();
      showPage("adjust");
      setChangePoint(false);
      grid.classList.remove("show");
    }

    function showEnd() {
      all.forEach(function (s) { setValue(s.key, s.end); });
      render();
    }

    return {
      ui: ui, slide: slide, openCrop: openCrop, addChangePoint: addChangePoint, grid: grid,
      reset: reset, showEnd: showEnd
    };
  }

  // The sliders move one by one, and the video changes with each slider.
  D.register("picture", function (demo) {
    var pc = build(demo, {
      // 30% closer, on the top right corner: the right part of the video and the sliders.
      panel: { x: D.STAGE_W - D.STAGE_W / 1.3, y: 0, w: D.STAGE_W / 1.3, h: D.STAGE_H / 1.3 }
    });
    var ui = pc.ui;

    async function play() {
      await ui.sleep(700);
      ui.camera("panel");
      await ui.sleep(650);
      ui.label("sun", "Light");
      await pc.slide(["brightness"], 900);
      await pc.slide(["shadows"], 700);
      await pc.slide(["contrast", "highlights"], 600);
      ui.label("palette", "Color");
      await pc.slide(["saturation"], 800);
      await pc.slide(["temperature"], 450);
      await ui.sleep(400);
      ui.hideLabel();
      // The full app: a clear video.
      ui.camera("wide");
      await ui.sleep(1300);
      // The Transform sub-tab: rotate, then zoom and move.
      // The camera stays wide, so that the full video shows the rotation and the crop.
      await pc.openCrop();
      await ui.sleep(650);
      pc.grid.classList.add("show");
      ui.label("crop", "Rotate to level the court");
      await pc.slide(["rotation"], 900);
      await pc.slide(["fine"], 500);
      pc.grid.classList.remove("show");
      ui.label("crop", "Zoom and move");
      await pc.slide(["zoom"], 900);
      await pc.slide(["panX", "panY"], 600);
      await ui.sleep(400);
      // A change point for a later part of the match, when the phone moved.
      await pc.addChangePoint();
      ui.label("crop", "The phone moved? Add a change point");
      await ui.sleep(1900);
      ui.hideLabel();
      await ui.sleep(600);
    }

    ui.run(pc.reset, play, pc.showEnd);
  });
})(window.Demos);
