// Counts one page view (privacy page, "This site"). It sends the page path and the address of the page that sent the
// visitor. It sets no cookies and keeps nothing on the device. The 404 page gives its path in data-path.
(function () {
  var script = document.currentScript;
  if (!navigator.sendBeacon || !window.JSON) return;
  var body = JSON.stringify({
    path: (script && script.getAttribute("data-path")) || location.pathname,
    referrer: document.referrer || ""
  });
  function send() {
    navigator.sendBeacon("https://bananashot-analytics.banana-shot-feedback.workers.dev/v1/site", body);
  }
  // A page that the browser prepares in advance counts only when the visitor opens it.
  if (document.prerendering) document.addEventListener("prerenderingchange", send, { once: true });
  else send();
})();
