# Privacy notice text: feedback reports

This is the section about the feedback reports (T4 of B-8) for the privacy page
of the landing site (B-10). Put it on the site with the analytics notice
(`docs/analytics/design.md`, "Privacy notice"). The Privacy page in More of the
app has a short version of this text (`PrivacyPage.kt`). Keep the two texts in
agreement.

---

## Feedback reports

You can send a problem, an idea, or a question to the author from the app: the
Feedback button in the sidebar, the "Report this problem" button of an error
message, the "Tell us" link at the end of each help page, or More → Contact.

**When the app sends a report.** The app sends a report only when you click
Send in the feedback form. The usage analytics choice does not change this. If
you do not send a report, the app sends no feedback data.

**What a report contains.**

- The topic (problem, idea, question, or other) and your message.
- A random report ID that the app makes for each report.
- The app version, the name and version of Windows, and the Java version.
- Your email address, only if you give it. The author can reply only to this
  address. The app keeps it on your computer for the next report. You can
  delete it in the form.
- The text of the error message, only if you open the form from an error
  message. You can remove it before you send.
- The last 2 MB of the log files of the app, only if you select "Attach the
  log files". The log files contain the names and folders of your videos and
  projects. They do not contain your videos. Before you send, "Show the data"
  shows the exact data that the app sends.

**Where a report goes.** The app sends the report over HTTPS to our server on
Cloudflare (a Cloudflare Worker with a Cloudflare D1 database). The server
sends the report to the author with a Telegram bot. Thus, Telegram also
processes the report.

**How long the data stays.**

- The server keeps the report text, the topic, the email address, the app data,
  and the error text for 90 days. Then a daily job deletes them.
- The server does not keep the log files. It sends them to Telegram and drops
  them.
- Cloudflare receives your IP address to deliver the request. The server does
  not keep it. To limit the number of reports, the server keeps a keyed hash of
  the address for one hour.
- The copy in the Telegram chat of the author stays until the author deletes
  it.

**How to ask for deletion.** Write to the contact address. Give the report ID,
or the date and the text of your message. The app shows the ID after the send.
The author then deletes the report on the server and in the Telegram chat.

**Contact.** The address is in More → Contact of the app.
