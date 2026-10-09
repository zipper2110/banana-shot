// The contact form. It sends the topic, the message, and the email address to the feedback Worker
// (POST /v1/site-feedback). The rules are the same as in the feedback form of the app
// (DefaultFeedbackPresenter.kt): "Send" needs a topic and a message, and the report ID stays until
// the Worker has the report, so a new try cannot make a second report.
(function () {
  var form = document.getElementById("feedback-form");
  if (!form || !window.fetch || !window.crypto || !window.JSON) return;

  var MAX_MESSAGE = 10000;
  var TIMEOUT_MS = 30000;
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  var message = form.elements.message;
  var email = form.elements.email;
  var send = document.getElementById("feedback-send");
  var sendLabel = send.querySelector("span");
  var status = document.getElementById("feedback-status");
  var messageError = document.getElementById("feedback-message-error");
  var emailError = document.getElementById("feedback-email-error");
  var sent = document.getElementById("feedback-sent");
  var sentText = document.getElementById("feedback-sent-text");

  var reportId = null;
  var sending = false;
  var failure = "";
  var canRetry = false;
  var emailTouched = false;

  function topic() {
    var checked = form.querySelector("input[name=topic]:checked");
    return checked ? checked.value : null;
  }

  function newId() {
    if (crypto.randomUUID) return crypto.randomUUID();
    var b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    var h = Array.prototype.map.call(b, function (x) { return (x + 0x100).toString(16).slice(1); }).join("");
    return h.slice(0, 8) + "-" + h.slice(8, 12) + "-" + h.slice(12, 16) + "-" + h.slice(16, 20) + "-" + h.slice(20);
  }

  function errors() {
    var address = email.value.trim();
    return {
      message: message.value.length > MAX_MESSAGE ? "The message is too long: " + message.value.length + " of " + MAX_MESSAGE + " characters." : "",
      email: address && !EMAIL.test(address) ? "Write the full address, for example name@example.com, or leave the field empty." : "",
    };
  }

  function complete() {
    var e = errors();
    return topic() !== null && message.value.trim() !== "" && !e.message && !e.email;
  }

  function render() {
    var e = errors();
    var shownEmailError = emailTouched ? e.email : "";
    messageError.textContent = e.message;
    emailError.textContent = shownEmailError;
    message.setAttribute("aria-invalid", e.message ? "true" : "false");
    email.setAttribute("aria-invalid", shownEmailError ? "true" : "false");
    var ready = complete();
    send.disabled = sending || !ready;
    sendLabel.textContent = sending ? "Sending…" : failure && canRetry ? "Try again" : "Send";
    status.classList.toggle("is-error", !!failure);
    if (sending) status.textContent = "Sending the message…";
    else if (failure) status.textContent = failure;
    else status.textContent = topic() === null || message.value.trim() === "" ? "Select a topic and write a message." : "";
  }

  function failureText(code) {
    if (code === 410) return "The server does not take messages now. Write an email to the address below.";
    if (code === 429) return "Too many messages came from your network in this hour. Try again later or write an email.";
    if (code >= 500) return "The server cannot take the message now. Try again later.";
    if (code === 0) return "The form cannot connect to the server. Check the internet connection and try again.";
    return "The server refused the message. Write an email to the address below.";
  }

  function showSent(address) {
    sentText.textContent = "Thank you. The author has your message. Report ID: " + reportId + "." +
      (address ? " The author can reply to " + address + "." : " You gave no email address, so the author cannot reply.");
    reportId = null;
    form.hidden = true;
    sent.hidden = false;
    sent.focus();
  }

  form.addEventListener("input", function (event) {
    if (event.target.name === "trap") return;
    failure = "";
    render();
  });
  email.addEventListener("blur", function () {
    emailTouched = true;
    render();
  });

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    emailTouched = true;
    if (sending || !complete()) { render(); return; }
    if (!reportId) reportId = newId();
    var address = email.value.trim();
    var body = { report_id: reportId, topic: topic(), message: message.value, trap: form.elements.trap.value };
    if (address) body.email = address;

    sending = true;
    failure = "";
    render();
    var controller = window.AbortController ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, TIMEOUT_MS);
    fetch(form.getAttribute("data-endpoint"), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
      redirect: "error",
      signal: controller ? controller.signal : undefined,
    }).then(function (response) {
      return response.status;
    }, function () {
      return 0;
    }).then(function (code) {
      clearTimeout(timer);
      sending = false;
      if (code === 200 || code === 201) showSent(address);
      else {
        failure = failureText(code);
        // As in the app: after 410, 429, a 5xx, or no connection, the same report can succeed later.
        canRetry = code === 0 || code === 410 || code === 429 || code >= 500;
      }
      render();
    });
  });

  document.getElementById("feedback-new").addEventListener("click", function () {
    // As in the app, the email address stays for the next message.
    var address = email.value;
    form.reset();
    email.value = address;
    emailTouched = false;
    failure = "";
    sent.hidden = true;
    form.hidden = false;
    render();
    form.querySelector("input[name=topic]").focus();
  });

  form.hidden = false;
  render();
})();
