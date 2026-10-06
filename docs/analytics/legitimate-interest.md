# Legitimate interest assessment: essential statistics

Status: reviewed by the author, 2026-10-06. Claude wrote the first draft. It is not legal advice.

This assessment covers only the essential level of the usage statistics
(`docs/analytics/design.md`, "Levels"). The extended level uses consent
(GDPR Article 6(1)(a)), so it needs no assessment. Review this file when an
essential key, the retention, or the processor changes.

## Processing

- Controller: the author of BananaShot (contact on the privacy notice).
- Processor: Cloudflare (Worker and D1 database in the EU jurisdiction).
- Data that the app sends: a random session ID that lives only in memory,
  the level, the app version, the OS family, the summary number, the final
  flag, the session length, the active time, the notice version, and the
  counters `unclean_exit`, `uncaught_error`, and `session_n_<bucket>`.
- Data that Cloudflare receives for the delivery: the IP address. The Worker
  does not store it. The rate limit keeps a keyed hash of it for one hour.
- Data on the device: the session count and the open-session flag in the
  Java preferences. They give `session_n_<bucket>` and `unclean_exit`.
- Retention: 90 days after the last summary of a session.

## 1. Purpose test

The interest: know if people use the app, how many come back, which
versions and OS families are in use, and if a new version crashes. The
author is one person with a small project. Without these numbers, the
author cannot decide where to put the work, or see a bad release.

- The interest is real and present: it is the reason for the change of
  2026-10-06.
- The interest is lawful. Product statistics and stability monitoring are
  usual interests of a software provider.

## 2. Necessity test

- The opt-in extended level alone does not meet the purpose. Few users
  select an opt-in, and the users who do are not typical. Thus opt-in
  numbers cannot tell how many people use the app.
- A less intrusive way with the same result is not known. Download counts
  from GitHub show downloads, not use or return. Feedback reports are rare.
- Each item is necessary for the purpose. The level has no feature use, no
  export data, and no tab data. The session number is a range, not a count.
  There is no install ID, so the server cannot link two sessions.
- The times on the server are rounded to the hour. The server keeps no IP
  address.

## 3. Balancing test

Factors for the user:

- The data does not identify the user. Only the IP address in transit is
  personal data, and the server does not keep it.
- No special category data, no data about children, and no data from the
  videos or the projects.
- No profiling, no advertising, no transfer to third parties other than the
  processor.
- A user of desktop software can expect anonymous usage statistics. Many
  products send them by default (for example VS Code and Firefox).

Safeguards:

- The first-start dialog tells about the essential level, before the user
  works with the app. It tells where to turn off all statistics.
- More → Privacy has the switch "Send essential statistics". When the user
  turns it off, the app stops at once and sends nothing after a restart.
  This is the way to object (GDPR Article 21).
- The privacy notice lists each item, the legal basis, the retention, and
  the way to object.
- The 90-day retention and the EU storage.

Result: the interest of the author is not overridden by the interests or
rights of the users, with the safeguards above.

## Open risk

The ePrivacy Directive, Article 5(3), is separate from the GDPR. It can
require consent when software reads or stores information on the device,
also for anonymous data. The EDPB Guidelines 2/2023 read it broadly. Some
national authorities (for example CNIL in France) exempt strictly limited
audience measurement. The app reads its own session count and version, and
it has an off switch. The author accepts this risk for the first release
(B-9 decision 26). If an authority or a user raises it, change the default
of the essential level to off for EU users, or ask in the first-start
dialog.
