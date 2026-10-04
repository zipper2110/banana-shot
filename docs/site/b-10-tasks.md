# B-10 Tasks: Landing Site

This file tracks the work of B-10 (the landing site of the app). The backlog
item is in `docs/backlog.md`.

## Goal

A public site tells what BananaShot does and lets users download it. The site
has the privacy notice page that the app opens (B-8 and B-9). The privacy
page must be live before the first release.

## Facts

- Each release has the privacy URL built in (`ANALYTICS_PRIVACY_URL`). A build
  works for 6 months. Thus, the privacy URL of the first release must stay
  valid for at least 6 months after the last build that uses it. Do not
  change the path of the privacy page after the first release.
- On 2026-10-03, `ANALYTICS_PRIVACY_URL` is
  `https://github.com/zipper2110/banana-shot`. This is a temporary value. It
  must change to the URL of the privacy page before the first release.
- The full privacy text is in `site/public/privacy/index.html` (decision 4).
- `site/README.md` tells how to preview and deploy the site.

## Decisions

Decided on 2026-10-03:

| # | Decision |
|---|---|
| 1 | The domain is `banana-shot-editor.app`. The author has it on Cloudflare. |
| 2 | The first version is the full site: home, download, FAQ, changelog, and privacy. |
| 3 | Plain HTML and CSS in `site/`. No build step and no site generator. Each page has its own copy of the header and the footer. |
| 4 | `site/privacy/index.html` is the only full text of the privacy notice. `docs/analytics/privacy-notice.md` and `docs/feedback/privacy-notice.md` keep only the maintainer notes and point to it. The app has the short text (`PrivacyPage.kt`, `AnalyticsConsentDialog`). |
| 5 | The privacy URL is `https://banana-shot-editor.app/privacy/`. Do not change this path after the first release (see "Facts"). |
| 6 | Contact address: Cloudflare Email Routing forwards an address on the domain to the Gmail of the author. `CONTACT_EMAIL` in the app changes to this address (B-8 decision 7). The author replies from Gmail. |
| 7 | No help pages on the site. The app has the help (F1). The site has an FAQ page. |
| 8 | The site uses Cloudflare Web Analytics. It sets no cookies, so the site has no cookie banner. The privacy page has a section about it. |
| 9 | Hosting: Cloudflare Workers static assets, deployed with `wrangler deploy` from `site/`. The author already uses wrangler for the two Workers. |
| 10 | The download button links to the stable URL `https://github.com/zipper2110/banana-shot/releases/latest/download/BananaShot-win-Setup.exe` (the same name in each release, B-30). |

## Open questions

Write each new decision in "Decisions" at once.

- Q1: the local part of the contact address, for example `hello@` or
  `support@`.
- Q2: screenshots of the app for the home page. Now the home page has a
  drawing of a video frame and a timeline.
- Q3: the author must read two new sections of the privacy page: "This site"
  (Cloudflare Web Analytics) and "Contact and your rights". They are not in
  the texts of B-8 and B-9.
- Q4: check the facts on the download page: Windows 10 support, and the disk
  space (now "about 700 MB"). Measure the installed size in B-26.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| T1 | Skeleton: `site/`, shared CSS, header, footer, logo, favicon | — | done |
| T2 | Home page | T1, Q2 | in-progress |
| T3 | Download page: system needs, the SmartScreen steps, the download button | T1, Q4 | in-progress |
| T4 | FAQ page | T1 | done |
| T5 | Changelog page | T1 | done |
| T6 | Privacy page: analytics, feedback, version check, site analytics. The docs notice files point to it | T1, Q3 | in-progress |
| T7 | Contact address: Email Routing (the author), `CONTACT_EMAIL` in the app, the address in the notices | Q1 | open |
| T8 | Deployment: wrangler config, custom domain, Web Analytics, `ANALYTICS_PRIVACY_URL` | T1–T6 | open |
| T9 | Release checks: links, phone width, steps in `release-checklist.md` | T8 | open |

Status values: `open`, `in-progress`, `done`.

- T6: write the effective date on the page at the first release with
  analytics. B-9 T7 stays open until the page is live.
- T1 to T6 (2026-10-03): all pages are in `site/public/`. Checked with
  `wrangler dev`: each page returns 200, `/privacy` redirects to
  `/privacy/`, an unknown path returns the 404 page, and no page scrolls
  sideways at 375 px.
- T6: the analytics and feedback sentences are the same as in the texts of
  B-9 and B-8. The version check is now a separate section. The "In short" box and the sections "This site" and "Contact and
  your rights" are new (Q3).
- T7: when the address exists, put it in "Contact and your rights" of the
  privacy page and in "Help and contact" of the FAQ.
- T8: change `ANALYTICS_PRIVACY_URL` only when the page is live. Add the
  Web Analytics script to each page, or turn on the automatic setup in the
  Cloudflare dashboard. Redirect `www.banana-shot-editor.app` to the domain
  without `www`.
