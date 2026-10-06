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
- Since 2026-10-06, `ANALYTICS_PRIVACY_URL` is
  `https://banana-shot-editor.app/privacy/` (decision 5). Before, it was the
  repository URL, a temporary value. Builds made before 2026-10-06 have the
  old URL. Do not use them for the release or for B-26.
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

Decided on 2026-10-06:

| # | Decision |
|---|---|
| 11 | The home page shows each feature in its own row, with an animation that shows the idea of the feature. The animation does not have to look exactly like the app. The animations are in `site/public/assets/features.js`. Each one runs only while it is on the screen, and shows only its end state when "reduce motion" is on. Feature 1 ("No dead time") is done first, for review. |
| 12 | Feature 1 has two options for review. Option A: an abstract timeline, scissors cut the dead time. Option B: a copy of the Points tab, a camera zooms on the parts (C, the timeline, V, the list, then the timeline without the dead time). The author keeps option A and compares. |
| 13 | Option B, changed by the author: no hotkeys and little text; the focus is the action and the result. The order: the full app; a 130% zoom on the bottom right corner; flags at the start and the end of the point, and the new mark on the timeline; back to the full app; the new point pops up in the list with a strong highlight; the timeline moves the marks together. The action labels ("Start of the point", "End of the point") show at the bottom of the box with the flags. |
| 14 | Feature 2 ("Scoreboard in the video") also has two options for review. Option A: a drawn court; after each rally, the scoreboard shows the new score, then the scoreboard changes its colors and its position. Option B: a copy of the Scoring tab; the full app, a 130% zoom on the top right corner, two clicks on the winner ("Point to J. Park", "Game to J. Park"), the score changes in the score panel and in the video, the points go into the list, then back to the full app and the scoreboard changes its style and position ("Your style"). The players and the titles are made up. |
| 15 | The text of feature 2 does not list the scoring formats. It says "the usual match formats". The scoreboard card is no longer in the feature cards, because feature 2 has its own rows. |
| 16 | The action labels of the B options are lime with dark text and a glow, so that the eye catches them. When the scoreboard style changes, the shape changes too, not only the color: first a white scoreboard with a blue title band, then a navy scoreboard with an orange bar and no title. This applies to options A and B of feature 2. |
| 17 | All feature animations use approach B: parts of the app, the action labels, and the focus on the action and the result. The old option A of features 1 and 2 (the abstract drawings) is removed. The action labels have a white background. Each of the 6 features gets two options for review. Option A: a copy of the full app screen, with a camera that zooms on the parts (as the first versions of features 1 and 2). Option B: minimal and clean: only the video and the one control that matters, large and with space, without the app frame. The feature cards under the rows are removed, because each feature now has its own rows. The animations are now in `site/public/assets/demos/` (`core.js` has the shared parts, and each feature has one file) and in `site/public/assets/demos.css`. `features.js` is removed. |
| 18 | Option A (the full app copy) is the animation of each feature. Option B (the minimal copies) is removed. The animations stay HTML for now; render them to video only if a slow phone lags. When an animation goes off the screen, it stops and its CSS animations pause. When it comes back on the screen, it starts again from the start. |
| 19 | The image at the top of the home page is a still copy of the Points tab, in the style of the feature animations (`demos/hero.js`). The video frame is from the author (see decision 22). The video has the scoreboard, and the timeline shows the marked points. No labels on the image: they overload it. The image does not move. The old SVG drawing is removed. |
| 20 | The title of the home page is "Your match has a story to share". It states the motivation behind the app: a match video that is organized, easy to watch, enjoyable, and easy to share. It talks to the player about their own match. The first choice, "Every match deserves a good video", was about the result, not the motivation. Slogans state a fact; they do not tell the reader to do something. The old title ("Your full match. Only the points. With the score.") sounded artificial. The `<title>` of the page stays descriptive for search engines. |
| 21 | The text of the site is casual, conversational, and simple. The description under the title is "BananaShot turns a raw tennis recording into a video that's easy to watch and share. Mark the points, keep score, and export it with a scoreboard." The scoring part tells the meaning ("keep score"), not the action (the click). It does not say "short video", because the video with only the points is often 20 to 40 minutes long. |
| 22 | Each animation has its own video frame from the author, in `site/public/assets/frames/`: `hero`, `points`, `scoring`, `stats`, `comments`, and `picture`. The frames are WebP, 1280 px wide, quality 78 (made with ffmpeg; 110 to 185 KB each). The frames of the feature animations load only when they come near the screen (`loading="lazy"`). `demo-frame.jpg` (a frame with real people) is removed. This closes Q5. |
| 23 | The site gets a new look. The mockups are in `design/site-restyle/` (3 options: A Studio, B Console, C Sport). Option A (Studio) is the main direction. Changes to A: the page is not near-black, because a background effect shows through matte glass parts (three effects for review: glass, court, and aurora); the hero has the text and the download buttons on the left and a smaller screenshot on the right; the "Local" and "No AI" sections each have a soft band of their own color, and each item is its own tile (the idea of option C, but softer). The accent of the site is the app accent `#C8EC46` (`Palette.DEFAULT_ACCENT`), not the old lime `#A1FE00`. The page does not use the near-black `#0E0E0E` as its background, and the eyebrow labels are not faint green. The demos keep the app colors. The text of the page does not change. |
| 24 | The home page shows two example videos that the author made with the app, after the note of the author and before the download section (moved there by the author; first it was after "How it works"): a doubles full match (`MIBRymxQM0A`) and singles highlights (`uQiJRa9Juh4`), both on the YouTube channel of the author. Each video is a link with a still image. The link opens the video on YouTube in a new tab. The page has no YouTube player: the embedded player showed error 153 (no referrer) in the local test, and a plain link loads nothing from YouTube on the site. The sections of the home page have class names (`section-how`, `section-examples`, `section-features`, `section-local`, `section-noai`, `section-author`, `section-cta`), so that the styles do not depend on the order of the sections. |
| 25 | The author accepts option A (Studio) with the aurora background as the new look of the site (2026-10-06). It goes into `site/public/assets/site.css` and applies to all pages. The glass and court backgrounds, the background switch, and options B and C stay only in `design/site-restyle/`. The site logo (`logo.svg`), the demos, and `theme-color` (`#151a21`) use the app accent. The demos keep the other app colors (`.ui-demo` in `demos.css`). Checked with `wrangler dev`: all pages load, and no page scrolls sideways at 375 px. |

Decided on 2026-10-06 (contact address):

| # | Decision |
|---|---|
| 26 | The first release uses the personal Gmail of the author (`leetvin@gmail.com`) as the contact address, in the app and on the site. The address on the domain (decision 6, Cloudflare Email Routing) needs more setup. It is B-40, the first post-release item. This changes decision 6 for the first release. |
| 27 | The download page gives an upper limit for the disk space, not an exact number: "Under 1 GB for the app, and more for your videos". The exact size changes between releases, and the user needs only an approximate number. The measurement is in Q4. |
| 28 | The download page keeps "Windows 10 or Windows 11, 64-bit". The app is not tested on Windows 10, but its parts support it. If users report problems that occur only on Windows 10, add tests or other measures then. This closes Q4. |
| 29 | The effective date of the privacy notice is 6 October 2026, the planned day of the site deploy, not the release date. The site goes live before the release, so a placeholder date must not be on the live page. The section "This site" applies from the deploy. No released app sends analytics before the first release, so the earlier date causes no problem. |
| 30 | The site texts tell that the app is signed (B-22). The "Run anyway" steps stay, because an OV signature does not give SmartScreen reputation at once. The Smart App Control notes now say that the signed app works with it. The changelog shows "Release date: coming soon" for 1.0.0 until the release; write the date (YYYY-MM-DD) on the day of the release. T2, T3, and T6 are done (2026-10-06). |
| 26 | Each page has a "Home" link as the first item of the header menu and of the footer menu, so that the way back to the home page is clear. The current page has a light pill in the header menu. On a phone, the five header items fit in one line down to 360 px. |
| 31 | The site has a Contact page (`/contact/`, 2026-10-06). It is the last item of the header menu and comes after Privacy in the footer menu. It has the same information as More &rarr; Contact in the app (`ContactPage.kt`): feedback from the app first, then the email address, then what to put in a problem report by email and where the log files are. The FAQ answer "How do I report a problem" links to it. The privacy page keeps its own contact section. On a phone, the six header items fit in one line down to 360 px (smaller items below 420 px). This changes the "five header items" of the second decision 26. |

## Open questions

Write each new decision in "Decisions" at once.

- Q1: closed by decision 26. The local part of the domain address is a
  question of B-40 (post-release).
- Q2: closed by decision 19 (2026-10-06). The top of the home page has a
  still copy of the Points tab in place of a screenshot.
- Q3: closed (2026-10-06). The author read and approved the sections "This
  site" (Cloudflare Web Analytics) and "Contact and your rights" of the
  privacy page.
- Q4: closed by decisions 27 (disk space) and 28 (Windows 10).
  Measured on 2026-10-06 (signed dry run, version 1.0.0, author's
  computer): `%LocalAppData%\BananaShot` is 539 MB. `current` (the app) is
  375 MB, and `packages` (the full `.nupkg`, kept for updates) is 159 MB.
  An update also needs space for the downloaded setup and a second app
  folder for a short time (estimate: about 1 GB free in total). Windows 10
  is not tested.
- Q5: closed by decision 22.
- Q6: closed. The author approved the text of the examples section.
- Q7: closed. The still images of the videos are in `site/public/assets/frames/`: `video-doubles.webp` (the YouTube thumbnail, 1280 x 720) and `video-singles.webp` (the YouTube thumbnail without its black bars, 480 x 270; YouTube has no larger one). The site loads nothing from YouTube.
- Q8: closed by decision 24. The page has no YouTube player; the videos are plain links. The still images are on the site (Q7).
- Q9: closed. The app icon has the app accent `#C8EC46` now (`app-icon.svg`, `bananashot.ico`, the PNG files in `design/app-icon/`, and the site icons `favicon-32.png` and `icon-256.png`).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| T1 | Skeleton: `site/`, shared CSS, header, footer, logo, favicon | — | done |
| T2 | Home page | T1 | done |
| T3 | Download page: system needs, the SmartScreen steps, the download button | T1 | done |
| T4 | FAQ page | T1 | done |
| T5 | Changelog page | T1 | done |
| T6 | Privacy page: analytics, feedback, version check, site analytics. The docs notice files point to it | T1 | done |
| T7 | Contact address: the personal Gmail in the app and on the site (decision 26). The domain address is B-40 | — | done |
| T8 | Deployment: wrangler config, custom domain, Web Analytics, `ANALYTICS_PRIVACY_URL` | T1–T6 | done |
| T9 | Release checks: links, phone width, steps in `release-checklist.md` | T8 | done |
| T10 | Contact page, in the menus of all pages, `404.html`, and `sitemap.xml` (decision 31) | T1 | done |

Status values: `open`, `in-progress`, `done`.

- T6: the effective date is on the page (decision 29). B-9 T7 stays open
  until the page is live.
- T1 to T6 (2026-10-03): all pages are in `site/public/`. Checked with
  `wrangler dev`: each page returns 200, `/privacy` redirects to
  `/privacy/`, an unknown path returns the 404 page, and no page scrolls
  sideways at 375 px.
- T6: the analytics and feedback sentences are the same as in the texts of
  B-9 and B-8. The version check is now a separate section. The "In short" box and the sections "This site" and "Contact and
  your rights" are new (Q3).
- T7 (2026-10-06): the privacy page ("Contact and your rights") and the
  FAQ ("Help and contact") show `leetvin@gmail.com` with a `mailto:` link.
  The page source has no plain address: `assets/email.js` makes the link
  from the reversed parts in `data-email`. Without JavaScript, the page
  shows "leetvin [at] gmail.com".
  The app already uses this address (`CONTACT_EMAIL`). B-40 changes all
  three places to the domain address.
- T8 (2026-10-06): the site is deployed with `wrangler deploy` (wrangler
  4.129.0, version ID `f66989a1-4ceb-41e7-8829-4af985ab46f3`) on the
  custom domain `banana-shot-editor.app`. Checked: each page returns 200,
  `/privacy` redirects (307) to `/privacy/`, an unknown path returns 404,
  the privacy page shows "Effective from: 2026-10-06", and the browser
  console has no errors.
- T8 (2026-10-06): Web Analytics is on with the automatic setup. Cloudflare
  adds the beacon (`static.cloudflareinsights.com/beacon.min.js`) at the
  edge, and a browser sends `/cdn-cgi/rum`. `curl` does not get the beacon,
  because it does not ask for HTML like a browser. Check in a browser.
  The download button returns 404 until the first release is published.
- T8 (2026-10-06): the author set `ANALYTICS_PRIVACY_URL` to
  `https://banana-shot-editor.app/privacy/`. The www address is a separate
  Worker (`site/www-redirect/`, version ID
  `8125b2bb-4332-4358-b444-d2c97560295c`) on the custom domain
  `www.banana-shot-editor.app`. It returns 301 to the same path and query on
  the domain without www. The main site stays static files only
  (decision 3). T8 is done.
- T8: change `ANALYTICS_PRIVACY_URL` only when the page is live. Add the
  Web Analytics script to each page, or turn on the automatic setup in the
  Cloudflare dashboard. Redirect `www.banana-shot-editor.app` to the domain
  without `www`. (All done on 2026-10-06, see above.)
- T9 (2026-10-06), on the live site:
  - Links: a script followed the 43 links and images of the 6 pages and
    checked each anchor (`#...`). All return 200. The only exception is the
    download file (`releases/latest/download/BananaShot-win-Setup.exe`). It
    returns 404 until the first release is published. `robots.txt` names
    the sitemap, and each sitemap URL returns 200.
  - Phone width: at 375 px and at 320 px, no page scrolls sideways, and no
    element goes past the right edge (the demo stages are clipped by their
    frame, as intended). Note: headless Chrome cannot make a window
    narrower than about 500 px, so its screenshots cut the text. Use the
    browser device emulation for phone checks.
  - Fixed: on a phone, the menu goes under the logo, and the row gap was 0.
    The pill of the current page touched the logo. The row gap is now 10 px
    (`site.css`, max-width 640 px). Deployed (version ID
    `75ab4414-ba02-4022-9b20-39020567dfba`).
  - `release-checklist.md` step 9 has the site steps: the changelog date and
    the download link of the new release. Step 3a has the privacy page.
  - Minor, not fixed: the menu links on a phone are 23 px high. WCAG 2.2
    asks for 24 px or enough space around the target; the space between the
    links is enough.
- B-40 (2026-10-06): the contact address is now
  `contact@banana-shot-editor.app`. Cloudflare Email Routing forwards it to
  the Gmail of the author, and a test email arrived. The author replies from
  the Gmail. The app (`CONTACT_EMAIL`), the privacy page, and the FAQ show
  the new address. The site is deployed (version ID
  `65b89769-cc82-47ca-aafc-1d6e4b10cd63`). Released apps show the Gmail
  until the users update, so the Gmail stays in use.
