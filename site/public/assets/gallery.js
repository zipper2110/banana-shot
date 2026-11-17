// The image galleries of the features page. Each ".gallery" has a list ".gallery-track" of figures.
// Without this script, the list is a row that the user can scroll or swipe (CSS scroll snap).
// The script adds the Previous and Next buttons, the count, and a row of small images to select an image.
(function () {
  "use strict";

  var ARROW = { prev: "M15 5l-7 7 7 7", next: "M9 5l7 7-7 7" };

  function button(kind, label) {
    var b = document.createElement("button");
    b.type = "button";
    b.className = "gallery-btn gallery-" + kind;
    b.setAttribute("aria-label", label);
    b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + ARROW[kind] + '"/></svg>';
    return b;
  }

  function setUp(gallery) {
    var track = gallery.querySelector(".gallery-track");
    var slides = Array.prototype.slice.call(track.children);
    if (slides.length < 2) return;
    var index = 0;

    var prev = button("prev", "Previous image"), next = button("next", "Next image");
    var count = document.createElement("span");
    count.className = "gallery-count";
    count.setAttribute("aria-live", "polite");
    var thumbs = document.createElement("div");
    thumbs.className = "gallery-thumbs";
    var thumbButtons = slides.map(function (slide, i) {
      var img = slide.querySelector("img"), caption = slide.querySelector("figcaption");
      var b = document.createElement("button");
      b.type = "button";
      b.setAttribute("aria-label", "Show image " + (i + 1) + (caption ? ": " + caption.textContent : ""));
      b.innerHTML = '<img src="' + img.getAttribute("src") + '" alt="" loading="lazy">';
      b.addEventListener("click", function () { go(i); });
      thumbs.appendChild(b);
      return b;
    });
    var bar = document.createElement("div");
    bar.className = "gallery-bar";
    bar.appendChild(prev);
    bar.appendChild(count);
    bar.appendChild(next);
    gallery.appendChild(bar);
    gallery.appendChild(thumbs);
    gallery.classList.add("ready");

    function show(i) {
      index = i;
      count.textContent = (i + 1) + " / " + slides.length;
      prev.disabled = i === 0;
      next.disabled = i === slides.length - 1;
      thumbButtons.forEach(function (b, k) {
        b.classList.toggle("on", k === i);
        if (k === i) b.setAttribute("aria-current", "true"); else b.removeAttribute("aria-current");
      });
    }

    function go(i) {
      i = Math.max(0, Math.min(slides.length - 1, i));
      track.scrollTo({ left: slides[i].offsetLeft - track.offsetLeft, behavior: "smooth" });
      show(i);
    }

    prev.addEventListener("click", function () { go(index - 1); });
    next.addEventListener("click", function () { go(index + 1); });
    track.addEventListener("keydown", function (e) {
      if (e.key === "ArrowLeft") { e.preventDefault(); go(index - 1); }
      if (e.key === "ArrowRight") { e.preventDefault(); go(index + 1); }
    });

    // A swipe or a scroll of the track changes the image too. The count follows the image in the middle.
    var timer;
    track.addEventListener("scroll", function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        var i = Math.round(track.scrollLeft / track.clientWidth);
        if (i !== index) show(Math.max(0, Math.min(slides.length - 1, i)));
      }, 80);
    }, { passive: true });

    show(0);
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll(".gallery").forEach(setUp);
  });
})();
