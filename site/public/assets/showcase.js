// The feature groups of the features page. Each ".showcase" has items ".sc-item": a tab ".sc-tab" (a title, a
// short line, and more details) and a media box ".sc-media" (an animation or a gallery).
// Without this script, the items show one under the other, each with its media.
// With it, the tabs are a list on the left, and the media of the open item shows on the right.
// On a narrow screen, each media shows under its tab (an accordion). One item of each group is open.
(function () {
  "use strict";

  function setUp(showcase) {
    var items = Array.prototype.slice.call(showcase.querySelectorAll(".sc-item"));
    if (!items.length) return null;
    var buttons = items.map(function (item, i) {
      var title = item.querySelector(".sc-tab h3");
      var media = item.querySelector(".sc-media");
      media.id = media.id || item.id + "-media";
      var button = document.createElement("button");
      button.type = "button";
      button.className = "sc-btn";
      button.setAttribute("aria-controls", media.id);
      while (title.firstChild) button.appendChild(title.firstChild);
      title.appendChild(button);
      button.addEventListener("click", function () { open(i); });
      return button;
    });

    function open(index) {
      items.forEach(function (item, i) {
        item.classList.toggle("on", i === index);
        buttons[i].setAttribute("aria-expanded", String(i === index));
      });
      // The animations measure their box when they start. A box that was hidden had no size, so tell them again.
      window.dispatchEvent(new Event("resize"));
    }

    showcase.style.setProperty("--n", items.length);
    showcase.classList.add("ready");
    open(0);
    return { items: items, open: open };
  }

  // A link to a feature (for example #preview) opens its item and shows its group.
  function openFromHash(showcases) {
    var id = decodeURIComponent(location.hash.slice(1));
    if (!id) return;
    showcases.forEach(function (sc) {
      sc.items.forEach(function (item, i) {
        if (item.id !== id) return;
        sc.open(i);
        // The item has no box of its own (display: contents), so scroll to its tab.
        item.querySelector(".sc-tab").scrollIntoView({ block: "start" });
      });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    var showcases = Array.prototype.slice.call(document.querySelectorAll(".showcase")).map(setUp).filter(Boolean);
    openFromHash(showcases);
    window.addEventListener("hashchange", function () { openFromHash(showcases); });
  });
})();
