// Selects "Mac" in each Windows / Mac switch (.os-switch) for a visitor on a Mac.
// The CSS shows the steps of the selected system. Without this script, Windows stays selected.
(function () {
  var data = navigator.userAgentData;
  var text = (data && data.platform) || navigator.platform || navigator.userAgent || "";
  if (!/^mac|Macintosh|Mac OS X/i.test(text)) return;
  var inputs = document.querySelectorAll('.os-switch input[value="mac"]');
  for (var i = 0; i < inputs.length; i++) inputs[i].checked = true;
})();
