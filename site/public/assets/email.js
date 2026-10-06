// Make the contact links from reversed parts, so that the page source has
// no plain email address for spam bots. Without JavaScript, the text
// "name [at] domain" stays.
document.querySelectorAll("[data-email]").forEach(function (el) {
  var parts = el.getAttribute("data-email").split("|");
  var address = parts[1].split("").reverse().join("") + "@" + parts[0].split("").reverse().join("");
  var link = document.createElement("a");
  link.href = "mailto:" + address;
  link.textContent = address;
  el.replaceWith(link);
});
