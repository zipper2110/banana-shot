# Privacy notice text: usage analytics

This is the section about the usage analytics (T5 of B-9) for the privacy page
of the landing site (B-10). Put it on the site with the feedback notice
(`docs/feedback/privacy-notice.md`). The site must show it before the first
release with analytics. The build gets the URL of the page through
`-AnalyticsPrivacyUrl`.

The app has a short version of this text: the consent dialog
(`AnalyticsConsentDialog.INTRO`) and the Privacy page in More
(`PrivacyPage.ANALYTICS_COLLECTED` and `ANALYTICS_EXCLUDED`). Keep the texts in
agreement.

When a counter key is added or changed, update the list below. Increase the
notice version if the new key collects a new type of data. The app then asks
the user again.

- Notice version: 1
- Effective date: the date of the first release with analytics. Write it here
  and on the site at the release.

---

## Usage analytics

**Notice version 1. Effective from: (date of the release).**

BananaShot can send anonymous usage counts. The counts tell us which parts of
the app people use, how long sessions are, and how well exports work. The
analytics are off until you select "Enable analytics". If you select "No
thanks" or close the question, the app sends nothing.

**What the app sends.** While analytics are on, the app sends one short
summary of the current session: at the start, every 5 minutes if something
changed, and at exit. A summary contains only these items:

- A random session ID. The app makes a new ID for each session and keeps it
  only in memory. The ID does not identify you or your computer, and it does
  not link two sessions.
- The app version and the OS family (Windows, macOS, Linux, or other).
- The number of the summary in the session, and if it is the last summary.
- The session length in seconds, and the seconds while the app window was
  active.
- The version of this notice that you accepted.
- These counts:
  - The tabs that you open with a click, and the active seconds on each tab
    (Projects, Points, Colors, Crop/Rotate, Scoring, Stats, Export).
  - Projects created and opened, and videos that could not open.
  - Points added, deleted, and marked as favorites. Comments added. Point
    outcomes recorded in Scoring.
  - Changes of the colors, and changes of the crop or rotation.
  - Help windows opened.
  - Exports for each encoder type (software, NVIDIA, AMD, Intel): started,
    completed, failed, cancelled, and interrupted at exit. The total export
    time and the total video length of the completed exports, in seconds. The
    failures by cause (source missing, output not writable, process start,
    encoder error, other). The export options in use (scoreboard, comments,
    stats card, favorites only, idle trim). The output size (720p, 1080p,
    1440p, 2160p, or other).
  - Errors that the app did not expect. The app sends only the number, not
    the error.
  - If the previous session did not close normally.
  - A range for the number of sessions on this computer (1, 2 to 5, 6 to 20,
    or more than 20).

**What the app does not send.** Your videos, audio, or frames. File names,
folder names, or paths. Project names, player names, scores, or comments.
Text that you type. A user ID, a device ID, or an install ID. Error messages
or log files. The clock time, the time zone, or the language of your
computer.

**Where the data goes.** The app sends the summary over HTTPS to our server
on Cloudflare (a Cloudflare Worker). Cloudflare processes the data for us. The
server stores the summary in a Cloudflare D1 database in the EU jurisdiction.
The server keeps the receive times only to the hour.

**Your IP address.** Cloudflare receives your IP address to deliver the
request. The server does not store it. To limit the number of requests, the
server keeps a keyed hash of the address for one hour.

**How long the data stays.** A daily job deletes each session 90 days after
its last summary.

**How to turn off analytics.** Open More → Privacy and turn off "Send
optional usage analytics". The app then stops at once and sends nothing more.
It deletes the counts of the current session. The summaries that the server
already has stay until the 90 days end. We cannot delete them on request,
because no value links a summary to you.

**Version check.** This section is separate from the analytics. The app
reads a small file from GitHub when it starts and from time to time while it
runs. The file tells if this version still works and if a new version is
available. The request sends no user ID and no analytics data. GitHub
receives your IP address and the standard data of a web request. "Update and
restart" downloads the setup file of the new version from GitHub. You cannot
turn off the version check, because each version stops working after a time.

**Contact.** The address is in More → Contact of the app.
