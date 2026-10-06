// The shared parts of the feature animations on the home page.
// Each animation is a copy of an app screen on a stage of 960 x 540 px. A "camera" zooms on the parts,
// and an action label at the bottom tells what the user does.
// Each animation runs only while it is on the screen. Use the timing functions of the stage
// (ui.sleep, ui.tween, ui.press, ui.type), so that the animation can stop off the screen.
// With "reduce motion" on, each animation shows only its end state.
// The other files in this folder register the animations with Demos.register(name, function).
// An element with data-demo="name" on the page gets the animation of that name.
window.Demos = (function () {
  "use strict";

  var STAGE_W = 960, STAGE_H = 540;
  var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var PLAYERS = [{ name: "J. Park", color: "#6f8cff" }, { name: "M. Silva", color: "#e0574a" }];
  var registry = {};

  // The icons of the action labels: paths in a 24 x 24 box, filled.
  var ICONS = {
    flag: "M5 21V3h2v1h12l-3 5 3 5H7v7z",
    palette: "M12 3a9 9 0 0 0 0 18c1.1 0 1.8-.8 1.8-1.8 0-.5-.2-.9-.5-1.2-.3-.3-.5-.7-.5-1.2 0-1 .8-1.8 1.8-1.8H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8zm-5.5 9a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z",
    chart: "M4 20h17v-2H6V4H4zm4-4h2v-5H8zm4 0h2V7h-2zm4 0h2v-8h-2z",
    check: "M9.5 16.6 5 12.1l1.4-1.4 3.1 3.1 8.1-8.1L19 7.1z",
    comment: "M4 3h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H8l-5 4V4a1 1 0 0 1 1-1zm3 5v2h10V8zm0 4v2h7v-2z",
    sun: "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM11 1h2v3h-2zm0 19h2v3h-2zM1 11h3v2H1zm19 0h3v2h-3zM4.2 5.6l1.4-1.4 2.1 2.1-1.4 1.4zm12.1 12.1 1.4-1.4 2.1 2.1-1.4 1.4zM16.3 6.3l2.1-2.1 1.4 1.4-2.1 2.1zM4.2 18.4l2.1-2.1 1.4 1.4-2.1 2.1z",
    crop: "M6 2h2v14h14v2h-4v4h-2v-4H6zM2 6h4v2H2zm8 0h6a2 2 0 0 1 2 2v6h-2V8h-6z",
    bolt: "M13 2 4 14h7l-1 8 9-12h-7z",
    queue: "M3 5h18v2H3zm0 6h12v2H3zm0 6h12v2H3zm14-2 5 3-5 3z",
    film: "M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm1 2v2h2V6zm12 0v2h2V6zM5 11v2h2v-2zm12 0v2h2v-2zM5 16v2h2v-2zm12 0v2h2v-2z"
  };

  // The icons that are lines, not filled shapes.
  var LINE_ICONS = {
    ball: '<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6c3.1 3.5 3.1 9.3 0 12.8M18.4 5.6c-3.1 3.5-3.1 9.3 0 12.8"/>'
  };

  function icon(name) {
    if (LINE_ICONS[name]) {
      return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">' + LINE_ICONS[name] + "</svg>";
    }
    return '<svg viewBox="0 0 24 24" fill="currentColor"><path d="' + ICONS[name] + '"/></svg>';
  }

  function ease(k) { return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2; }

  // A promise that never settles. A stopped pass waits on it, so the pass ends without an error.
  var NEVER = new Promise(function () {});

  // The navigation column of the app. "active" is the index of the open tab:
  // 0 Projects, 1 Points, 2 Scoring, 3 Statistics, 4 Adjustments, 5 Crop, 6 Export.
  function nav(active) {
    var html = '<div class="ui-nav"><img src="/assets/logo.svg" alt="">';
    for (var i = 0; i < 7; i++) html += '<i class="' + (i === active ? "on" : "") + '"></i>';
    return html + "</div>";
  }

  function watchVisibility(element, onVisible) {
    if (!("IntersectionObserver" in window)) { onVisible(true); return; }
    new IntersectionObserver(function (entries) {
      onVisible(entries[0].isIntersecting);
    }, { threshold: 0.35 }).observe(element);
  }

  // Puts the stage and the action label into the demo box.
  // "views" are the parts of the stage that the camera can show: {x, y, w, h} in stage pixels.
  // The view "wide" (the full stage) is always there.
  function stage(demo, html, views) {
    demo.innerHTML = '<div class="ui-stage">' + html + '</div><div class="ui-hud"><i></i><span></span></div>';
    var stageEl = demo.querySelector(".ui-stage");
    var hud = demo.querySelector(".ui-hud");
    var view = "wide";
    views = views || {};
    views.wide = { x: 0, y: 0, w: STAGE_W, h: STAGE_H };

    function camera(name) {
      view = name;
      var rect = views[name];
      var W = demo.clientWidth, H = demo.clientHeight;
      var s = Math.min(W / rect.w, H / rect.h);
      var tx = W / 2 - (rect.x + rect.w / 2) * s;
      var ty = H / 2 - (rect.y + rect.h / 2) * s;
      tx = Math.min(0, Math.max(W - STAGE_W * s, tx));
      ty = Math.min(0, Math.max(H - STAGE_H * s, ty));
      stageEl.style.transform = "translate(" + tx + "px," + ty + "px) scale(" + s + ")";
    }
    window.addEventListener("resize", function () { camera(view); });

    // Each pass of the loop has a number. When the demo goes off the screen, the number changes:
    // the timing functions of the old pass then never finish, so the old pass stops where it is.
    var pass = 0;
    function stopped(id) { return id !== pass; }

    function sleep(ms) {
      var id = pass;
      return new Promise(function (resolve) { setTimeout(resolve, ms); }).then(function () { return stopped(id) ? NEVER : undefined; });
    }

    // Calls step(k) on each frame for "ms" milliseconds. k goes from 0 to 1, with an ease in and out
    // or, with "linear", at a constant speed.
    function tween(ms, step, linear) {
      var id = pass, start = performance.now();
      return new Promise(function (resolve) {
        function frame(now) {
          if (stopped(id)) return;
          var k = Math.min(1, (now - start) / ms);
          step(linear ? k : ease(k));
          if (k < 1) requestAnimationFrame(frame); else resolve();
        }
        requestAnimationFrame(frame);
      });
    }

    // A short press on a button of the copy.
    async function press(element) {
      element.classList.add("press");
      await sleep(170);
      element.classList.remove("press");
    }

    // Types the text into the element, one letter after the other.
    async function type(element, text, msPerLetter) {
      for (var i = 1; i <= text.length; i++) {
        element.textContent = text.slice(0, i);
        await sleep(msPerLetter);
      }
    }

    function label(iconName, text) {
      hud.querySelector("i").innerHTML = icon(iconName);
      hud.querySelector("span").textContent = text;
      hud.classList.add("show");
    }

    function hideLabel() { hud.classList.remove("show"); }

    // Plays the animation in a loop while the demo is on the screen. reset() puts the start state,
    // play() is one pass (async), and end() shows the end state when "reduce motion" is on.
    // Off the screen, the pass stops and the CSS animations pause. Back on the screen, a new pass starts.
    function run(reset, play, end) {
      function resetAll() {
        demo.classList.add("no-anim");
        demo.classList.remove("press");
        stageEl.querySelectorAll(".press").forEach(function (el) { el.classList.remove("press"); });
        hideLabel();
        camera("wide");
        reset();
        void demo.offsetWidth; // Applies the reset before the transitions come back.
        demo.classList.remove("no-anim");
      }

      async function loop(id) {
        while (!stopped(id)) {
          resetAll();
          demo.classList.remove("fading");
          await play();
          demo.classList.add("fading");
          await sleep(350);
        }
      }

      resetAll();
      if (reduceMotion) { end(); return; }
      demo.classList.add("paused");
      watchVisibility(demo, function (isVisible) {
        pass++;
        demo.classList.toggle("paused", !isVisible);
        if (isVisible) loop(pass);
      });
    }

    return {
      el: stageEl,
      q: function (selector) { return stageEl.querySelector(selector); },
      qa: function (selector) { return Array.prototype.slice.call(stageEl.querySelectorAll(selector)); },
      camera: camera,
      label: label,
      hideLabel: hideLabel,
      sleep: sleep,
      tween: tween,
      press: press,
      type: type,
      run: run
    };
  }

  function register(name, start) { registry[name] = start; }

  // The deferred scripts run before DOMContentLoaded, so all the animations are registered then.
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-demo]").forEach(function (demo) {
      var start = registry[demo.dataset.demo];
      if (start) start(demo);
    });
  });

  return {
    STAGE_W: STAGE_W, STAGE_H: STAGE_H, PLAYERS: PLAYERS, reduceMotion: reduceMotion,
    icon: icon, nav: nav, stage: stage, register: register
  };
})();
