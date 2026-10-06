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
| 26 | Each page has a "Home" link as the first item of the header menu and of the footer menu, so that the way back to the home page is clear. The current page has a light pill in the header menu. On a phone, the five header items fit in one line down to 360 px. |

## Open questions

Write each new decision in "Decisions" at once.

- Q1: the local part of the contact address, for example `hello@` or
  `support@`.
- Q2: screenshots of the app for the home page. Now the top of the home
  page has a still copy of the Points tab (decision 19).
- Q3: the author must read two new sections of the privacy page: "This site"
  (Cloudflare Web Analytics) and "Contact and your rights". They are not in
  the texts of B-8 and B-9.
- Q4: check the facts on the download page: Windows 10 support, and the disk
  space (now "about 700 MB"). Measure the installed size in B-26.
- Q5: closed by decision 22.
- Q6: closed. The author approved the text of the examples section.
- Q7: closed. The still images of the videos are in `site/public/assets/frames/`: `video-doubles.webp` (the YouTube thumbnail, 1280 x 720) and `video-singles.webp` (the YouTube thumbnail without its black bars, 480 x 270; YouTube has no larger one). The site loads nothing from YouTube.
- Q8: closed by decision 24. The page has no YouTube player; the videos are plain links. The still images are on the site (Q7).
- Q9: the app icon (`src/main/resources/icons/app-icon.svg`, the `.ico`
  file) is still the old lime `#A1FE00`. Thus `favicon-32.png` and
  `icon-256.png` of the site stay lime too, but `logo.svg` now has the app
  accent. Change the app icon to the accent, or keep the lime icon?

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
