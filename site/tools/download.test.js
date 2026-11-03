// Tests the system check of /download/ (site/public/assets/download.js) and the copy of the Windows page.
// Run: node --test site/tools/download.test.js
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { detectSystem, pageFor } = require("../public/assets/download.js");

const UA = {
  windowsChrome: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
  macSafari: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
  macChrome: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
  macFirefox: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:144.0) Gecko/20100101 Firefox/144.0",
  linuxFirefox: "Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0",
  android: "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
  iphone: "Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1",
};

test("Windows browsers get the Windows app", () => {
  assert.equal(detectSystem({ userAgent: UA.windowsChrome, platform: "Win32", uaPlatform: "Windows", mobile: false }), "windows");
  assert.equal(detectSystem({ userAgent: UA.windowsChrome }), "windows");
});

test("Mac browsers get the Mac app", () => {
  assert.equal(detectSystem({ userAgent: UA.macSafari, platform: "MacIntel", maxTouchPoints: 0 }), "mac");
  assert.equal(detectSystem({ userAgent: UA.macFirefox, platform: "MacIntel", maxTouchPoints: 0 }), "mac");
  assert.equal(detectSystem({ userAgent: UA.macChrome, platform: "MacIntel", uaPlatform: "macOS", mobile: false }), "mac");
  assert.equal(detectSystem({ userAgent: UA.macChrome, uaPlatform: "macOS", architecture: "arm" }), "mac");
});

test("a Mac with an Intel processor is not supported", () => {
  assert.equal(detectSystem({ userAgent: UA.macChrome, uaPlatform: "macOS", architecture: "x86" }), "mac-intel");
});

test("an iPad with the user agent of a Mac is a mobile device", () => {
  assert.equal(detectSystem({ userAgent: UA.macSafari, platform: "MacIntel", maxTouchPoints: 5 }), "mobile");
});

test("phones are mobile devices", () => {
  assert.equal(detectSystem({ userAgent: UA.android, platform: "Linux armv8l", uaPlatform: "Android", mobile: true }), "mobile");
  assert.equal(detectSystem({ userAgent: UA.iphone, platform: "iPhone", maxTouchPoints: 5 }), "mobile");
});

test("other and unknown systems are other", () => {
  assert.equal(detectSystem({ userAgent: UA.linuxFirefox, platform: "Linux x86_64" }), "other");
  assert.equal(detectSystem({ userAgent: "", platform: "" }), "other");
  assert.equal(detectSystem({}), "other");
});

test("only a Mac with Apple Silicon goes to /download/mac/", () => {
  assert.equal(pageFor("mac"), "/download/mac/");
});

test("all other visitors stay on the Windows page", () => {
  for (const system of ["windows", "mac-intel", "mobile", "other", undefined]) {
    assert.equal(pageFor(system), null, String(system));
  }
});

test("/download/ and /download/windows/ have the same main part", () => {
  const main = (file) => {
    const html = fs.readFileSync(path.join(__dirname, "..", "public", file), "utf8");
    return html.slice(html.indexOf("<main"), html.indexOf("</main>"));
  };
  assert.equal(main("download/index.html"), main("download/windows/index.html"));
});
