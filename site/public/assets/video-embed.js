// Loads the YouTube player of a video link with data-youtube when the visitor clicks it (privacy page, "This site").
// Until the click, the page loads nothing from YouTube. Without this script, the link opens the video on YouTube.
(function () {
  document.addEventListener("click", function (event) {
    var link = event.target.closest && event.target.closest("a.video-play[data-youtube]");
    if (!link) return;
    // A middle click or a click with a modifier key opens YouTube in a new tab, as usual.
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    var frame = document.createElement("iframe");
    frame.src = "https://www.youtube-nocookie.com/embed/" + encodeURIComponent(link.dataset.youtube) +
      "?autoplay=1&rel=0&hl=en";
    frame.title = "Tutorial video";
    frame.allow = "autoplay; encrypted-media; picture-in-picture; fullscreen";
    frame.allowFullscreen = true;
    frame.referrerPolicy = "strict-origin-when-cross-origin";
    var box = document.createElement("div");
    box.className = "video-play";
    box.appendChild(frame);
    link.replaceWith(box);
  });
})();
