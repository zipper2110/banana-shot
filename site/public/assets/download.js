// The script of /download/. The page is the Windows page, and it is the page for all systems that the app does
// not support. For a visitor with a Mac with Apple Silicon, the script opens /download/mac/.
// The page loads this script in <head>, so that a Mac visitor moves on before the page shows.
(function () {
  var MAC_PAGE = "/download/mac/";

  // The system of the visitor: "windows", "mac", "mac-intel" (not supported), "mobile", or "other".
  // info: userAgent, platform (navigator.platform), uaPlatform and mobile (navigator.userAgentData),
  // maxTouchPoints, and architecture (from getHighEntropyValues, only in some browsers).
  function detectSystem(info) {
    var userAgent = info.userAgent || "";
    var text = info.uaPlatform || info.platform || userAgent;
    if (info.mobile === true || /Android|iPhone|iPad|iPod/i.test(userAgent)) return "mobile";
    if (/^mac|Macintosh|Mac OS X/i.test(text)) {
      // iPadOS asks for desktop pages with the user agent of a Mac. An iPad has a touch screen, a Mac has none.
      if (info.maxTouchPoints > 1) return "mobile";
      if (info.architecture === "x86") return "mac-intel";
      return "mac";
    }
    if (/^win|Windows/i.test(text)) return "windows";
    return "other";
  }

  // The page to open, or null when the visitor stays on the Windows page.
  function pageFor(system) {
    return system === "mac" ? MAC_PAGE : null;
  }

  if (typeof module === "object" && module.exports) {
    module.exports = { detectSystem: detectSystem, pageFor: pageFor };
    return;
  }

  function decide(system) {
    var page = pageFor(system);
    if (page) location.replace(page);
  }

  var data = navigator.userAgentData;
  var info = {
    userAgent: navigator.userAgent,
    platform: navigator.platform,
    uaPlatform: data && data.platform,
    mobile: data && data.mobile,
    maxTouchPoints: navigator.maxTouchPoints || 0
  };
  var system = detectSystem(info);

  // Chrome and Edge can tell the processor. Safari and Firefox cannot, so a Mac there counts as Apple Silicon.
  // Wait at most 500 ms for the answer.
  if (system === "mac" && data && data.getHighEntropyValues) {
    var done = false;
    var finish = function (next) {
      if (done) return;
      done = true;
      decide(next);
    };
    setTimeout(function () { finish(system); }, 500);
    data.getHighEntropyValues(["architecture"]).then(function (values) {
      info.architecture = values.architecture;
      finish(detectSystem(info));
    }, function () { finish(system); });
  } else {
    decide(system);
  }
})();
