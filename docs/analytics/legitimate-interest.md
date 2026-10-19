# Legitimate interest assessment: essential statistics

Status: reviewed by the author, 2026-10-06. Claude wrote the first draft. It is not legal advice.

Update for the language attributes (B-11 E2, task E2-T8): draft by Claude,
2026-10-19. **Not reviewed yet.** The author must review and approve it
before the release that sends the language attributes. Then write the date of
the review here. If the result of the balancing test is negative, the two
attributes stay on the extended level. Then change E2 and the B-11 plan.

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
- Since schema 4 (B-11 E2): the attributes `language` (the language of the
  app interface) and `os_language` (the Windows display language). Each is
  one code from a fixed list of about 40 primary language subtags, or
  `other`. Never a region or a full locale.
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

The interest of the language attributes: decide which languages to translate
the app into, and see if the users use the translations. A translation costs
the author about one week and the fee of a native reviewer. The author must
spend this on the languages of the real users.

- The interest is real and present: B-11 plans more languages after Spanish,
  and selects them from this data.
- The interest is lawful. Localization planning is a usual interest of a
  software provider.

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

The language attributes:

- The extended level alone gives biased data. The extended level is opt-in,
  and the opt-in dialog is in English until the app has the language of the
  user. A user who does not read English well is less likely to select an
  opt-in in an English dialog. Thus opt-in data undercounts exactly the
  users who need a translation.
- A less intrusive way with the same result is not known. The countries of
  the site visits do not tell the language (for example Switzerland or the
  United States). The feedback reports are rare.
- Each attribute is the minimum: only the primary language, never the
  region. The app language alone does not show the users that a translation
  can help, because these users now use the app in English. The Windows
  language alone does not show if the users use a translation.

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
- New risk of the language attributes: in a small user base, a rare language
  with a rare app version can make one session easy to see. This does not
  identify a person: the server keeps no IP address, no install ID, and no
  value that links two sessions. The author cannot find the person behind a
  session.

Safeguards:

- The first-start dialog tells about the essential level, before the user
  works with the app. It tells where to turn off all statistics.
- More → Privacy has the switch "Send essential statistics". When the user
  turns it off, the app stops at once and sends nothing after a restart.
  This is the way to object (GDPR Article 21).
- The privacy notice lists each item, the legal basis, the retention, and
  the way to object.
- The 90-day retention and the EU storage.
- The language list stays short (about 40 values). A rare language becomes
  `other` in the app, before the send.
- The cockpit shows a language only when it has at least 5 sessions in the
  period, or in the week of a weekly chart. Smaller languages count as
  `other`, and a group with fewer than 5 sessions does not show. The rule
  applies to each cell, also to a language combined with another value (the
  Windows language of the sessions with the app in English). The cockpit does
  not show a language combined with an app version.
- The notice version increases from 2 to 3. The first-start dialog shows
  again, and it names the two languages under the essential level.

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

Since B-11 E2, the app also reads the Windows display language from the
device for the essential level. This adds to the same open risk. The app
reads only the language part, and the off switch stops it.
