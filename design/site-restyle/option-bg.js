// Mockup only: a switch for the background effect of options A and B.
// The effect is html[data-bg]: glass (default), court, or aurora. "?bg=court" in the URL selects one too.
(function () {
  "use strict";
  var EFFECTS = ["glass", "court", "aurora"];
  var root = document.documentElement;
  function stored() { try { return localStorage.getItem("option-bg"); } catch (e) { return null; } }
  function store(v) { try { localStorage.setItem("option-bg", v); } catch (e) { /* no storage */ } }
  var query = new URLSearchParams(location.search).get("bg");
  var start = EFFECTS.indexOf(query) >= 0 ? query : EFFECTS.indexOf(stored()) >= 0 ? stored() : "glass";
  root.setAttribute("data-bg", start);

  document.addEventListener("DOMContentLoaded", function () {
    var bar = document.createElement("div");
    bar.className = "bg-switch";
    bar.innerHTML = "<span>Background</span>";
    EFFECTS.forEach(function (name) {
      var b = document.createElement("button");
      b.type = "button";
      b.textContent = name.charAt(0).toUpperCase() + name.slice(1);
      b.setAttribute("aria-pressed", String(name === start));
      b.addEventListener("click", function () {
        root.setAttribute("data-bg", name);
        store(name);
        bar.querySelectorAll("button").forEach(function (o) { o.setAttribute("aria-pressed", String(o === b)); });
      });
      bar.appendChild(b);
    });
    document.body.appendChild(bar);
    // "?from=4" hides the sections before section 4, for screenshots of the lower part of the mockup.
    var from = parseInt(new URLSearchParams(location.search).get("from"), 10);
    var sections = document.querySelectorAll("main > section");
    for (var i = 0; i < from - 1 && i < sections.length; i++) sections[i].hidden = true;
  });
})();
