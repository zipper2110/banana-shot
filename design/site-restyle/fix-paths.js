// The demo scripts use site paths such as "/assets/frames/hero.webp".
// Over file:// these paths point to the disk root. This script changes them to the site folder.
// On an option page (html data-option), the logo comes from accent/, with the app accent.
(function () {
  "use strict";
  var SITE = "../../site/public";
  var option = document.documentElement.hasAttribute("data-option");
  function target(src) {
    if (option && src === "/assets/logo.svg") return "accent/logo.svg";
    return SITE + src;
  }
  function fix(node) {
    if (node.nodeType !== 1) return;
    var list = node.matches("img[src^='/assets/']") ? [node] : [];
    list = list.concat(Array.prototype.slice.call(node.querySelectorAll("img[src^='/assets/']")));
    list.forEach(function (img) { img.setAttribute("src", target(img.getAttribute("src"))); });
  }
  new MutationObserver(function (records) {
    records.forEach(function (r) { r.addedNodes.forEach(fix); });
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
