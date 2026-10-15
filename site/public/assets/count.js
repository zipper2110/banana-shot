// Counts one page view (privacy page, "This site"). It sends the page path and the address of the page that sent the
// visitor. It sets no cookies. The 404 page gives its path in data-path.
(function () {
  var script = document.currentScript;
  if (!navigator.sendBeacon || !window.JSON) return;
  // The author opens a page with ?nocount once in each browser, so that the own visits do not count. ?nocount=off
  // stops this. Only then does the script keep a flag on the device (site/README.md, "Page view counter").
  // A mark in the corner shows that this browser does not count.
  function showMark() {
    var mark = document.createElement("div");
    mark.className = "nocount-mark";
    mark.textContent = "Not counted";
    mark.title = "This browser does not count page views. Open ?nocount=off to count again.";
    document.body.appendChild(mark);
  }
  try {
    var flag = "bananashot-nocount";
    var nocount = new URLSearchParams(location.search).get("nocount");
    if (nocount === "off") localStorage.removeItem(flag);
    else if (nocount !== null) localStorage.setItem(flag, "1");
    if (localStorage.getItem(flag)) return showMark();
  } catch (e) {
    // Blocked storage: count the view.
  }
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
