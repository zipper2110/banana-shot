# Competitor software

An internal list of the software that competes with BananaShot. Each group
has its own table. `docs/marketing/strategy.md` has a short summary and the
weak points that BananaShot fixes.

Written on 2026-10-19. Most facts come from the vendor pages and store
listings, not from a test. "?" means that the source does not tell. Check
the facts again before you use them on the site.

Rules:

- Add each new competitor that you find to the correct group.
- Write the date of the check when you change a fact.
- An app that trims automatically and also lets you fix the cuts goes to
  group 1. An app that trims by hand first and has an optional automatic
  mode goes to group 2.

## 1. Automatic-first apps

The app finds the rallies and removes the dead time automatically (with AI
or with other methods). The user only fixes the result.

| App | Platforms | Sports | Scoreboard | Processing | Price | Notes |
|---|---|---|---|---|---|---|
| [RallyFlow](https://www.rallyflowapp.com/en/) | Android, iOS, Windows, Mac | Tennis | ? (not on the page) | ? (the page says "Have Internet? Good to Go") | ? | Beta. Manual fine-tuning of the cuts. Exports the full match with rallies only, a 3-minute highlights video, or both. Needs a camera behind the baseline that shows the full court. [Intro thread on Tennis Warehouse](https://tt.tennis-warehouse.com/index.php?threads/imagine-theres-an-app-that-auto-trims-your-match-videos-how-should-it-work.794873/): the author (psed) asked users what they want on 2026-01-06 and showed the prototype on 2026-02-19. The processing is on a server, so there is a usage fee. Users asked for the full match as one MP4, a score after each point, and support for a side camera |
| [SwingVision](https://swing.vision/) | iPhone, iPad, Mac | Tennis, pickleball | Yes | Device | Pro about $180 per year | Also stats and line calls. The user can exclude rallies from the highlights. Reviews report missed points |
| [Rallytics](https://rallytics.app/) | Windows, Mac | Tennis | No | A low-resolution copy goes to the analysis | 2 free hours, packs from $4.99, $89.99 per year | The closest automatic-first competitor: a desktop app for full match videos, like BananaShot, but with automatic cuts. Rally editor to fix the cuts. The author's test (October 2026): it often froze and the interface is hard to use |
| [Aero AI](https://apps.apple.com/us/app/aero-ai/id6744615101) | iPhone | Tennis | ? | ? | 3 free edits, then $29 per month or $179 per year (archived listing) | |
| [PerfectSwing: Rally Highlights](https://apps.apple.com/us/app/perfectswing-rally-highlights/id6751231905) | iPhone, iPad, Mac (M1 and later) | Tennis, pickleball, padel | Yes, entered during the review. Export with the score needs Pro | Device, optional cloud storage | Pro from $3.99 to $59.99 | The user can add and merge rallies |
| [TennisCut](https://www.tenniscut.com/) | iPhone, Android, Apple Watch | Tennis | Yes, free. The watch keeps the score | Device only | Free app, Pro subscription for AI trimming (price ?) | Finds the rallies during the recording. Rally editor to keep or delete each rally. Same app as "Tennis Cut" in the stores |
| [CourtCut](https://courtcut.app/) | Web, iPhone | Padel, tennis, pickleball, badminton | No | Web: their server. iPhone: device | Free 1 video per month with watermark, Pro $99.99 per year | Picks the best points for a highlights video. Trim editor on all plans |
| [RallyCut](https://app.rallycut.com/) | Android | Tennis | ? | Device (search result) | Free 1 video per month | Not verified: the page did not load. A different "RallyCut: AI Badminton Editor" exists on iOS |
| [OverRally](https://overrally.com/app) | iOS, Android, web | Tennis | ? | ? (upload) | Free options | Cuts the match to rallies in about 10 minutes. Zoomed clips that follow one player |
| [40Love.ai](https://40love.ai/) | Phone video, service | Tennis | ? | ? | ? | Says that a 2-hour match becomes 20 minutes. Also analysis |
| [TennisVideoEditor](https://tennisvideoeditor.com/) | Web (upload MP4, MOV, WebM) | Tennis | ? (not on the page) | Their server. The page says that it deletes the data after processing | Subscription (price ?) | "Like SwingVision, but just video trimming". Finds the rallies from the movement. A 2-3 hour match becomes 15-25 minutes. No manual fix on the page. [Intro post on r/10s](https://www.reddit.com/r/10s/comments/1343390/a_web_app_for_easy_tennis_video_editing/) |
| [Padel Vision Highlight Cut](https://padelvision.padelradar.io/en/highlights) | Web (upload or YouTube link) | Padel | No | ? (upload) | 1 credit cuts up to 60 minutes. The first credit is free | Removes breaks, ball pickups, and side changes. Optional 9:16 version |
| [Covelant](https://www.covelant.com/) | ? | Padel, tennis, pickleball | ? | ? | ? | Automatic highlights, stats, and clips |
| [RallyClip](https://iroblesrazzaq.github.io/RallyClip/) | Python on a PC (GPU recommended) | Tennis | No | Local | Free, open source (GPL-3.0) | For technical users only. Browser interface and command line |
| [TT Clips](https://tt-clips.com/) | ? | Table tennis | ? | ? | ? | One developer. Status not verified |
| [SportArc](https://sportarc.ai/features/tennis-video-editor/) | iPhone, iPad | Tennis, badminton, table tennis | No (not on the page) | ? | Free, in-app purchases ("Plus") | Auto Cut finds the rallies in a long recording. The user selects the clips to export. No manual fix of the cuts on the page. AI coach and match review for table tennis only. Site in several languages |
| [RallyCut: AI Badminton Editor](https://appshunter.io/ios/app/id6752290110) | iOS | Badminton | ? | ? | ? | Splits the rallies automatically |
| [Spherely: AI Swing Coach](https://apps.apple.com/app/id6743446008) | iOS | Table tennis, badminton, squash | ? | ? | ? | Mainly a coach app. Finds the rallies and the best shots |

## 2. Manual-first editors

The user marks the points. The app makes the video with a score. BananaShot
is in this group.

| App | Platforms | Sports | Scoreboard | Processing | Price | Notes |
|---|---|---|---|---|---|---|
| [MatchCut Pro](https://matchcutpro.com/) (was ScoreboardMaker) | Windows, Mac | Tennis (more sports planned) | Yes. Pro has all styles | Local, also the AI | Free with watermark, Pro €9 per month | Closest to BananaShot. AI rally detection (beta) in Pro. Trim, rotate, zoom, social media editor. A YouTube channel in the outreach list uses it. [Intro post on Reddit](https://www.reddit.com/user/MatchCutPro/comments/1vdewuo/i_built_a_tennis_video_editor_for_my_brother/) |
| [Tennis Studio](https://apps.microsoft.com/detail/9mwj0rtzbmqm?hl=en-US) | Windows (Microsoft Store) | Tennis | Yes, one style. Score by hand or from the Garmin watch app | Local | About $8 | Cut, crop, rotate. No color correction. The author's test (October 2026): basic |
| [Easy Scoreboard - Video Editor](https://apps.apple.com/app/easy-scoreboard-video-editor/id6737460216) (Jaewoo Choi) | iPhone, Mac (M1 and later). No Android version found | Tennis, table tennis, badminton (store screenshots). The score is a generic counter with +/− buttons; the text names only "AD" in tennis | Yes, custom designs, templates, logo, font, server indicator | Device | Free, "Lifetime Access" $7.99 | Same idea as BananaShot on a phone. Trims by seconds. Since November 2024. 3 ratings, 3.0. One review reports export errors. Probably the app of the [intro post on r/tabletennis](https://www.reddit.com/r/tabletennis/comments/1glk804/easily_add_a_scoreboard_to_your_table_tennis/) (Reddit blocks the read, so not verified). Video example: ? |

## 3. Record-with-score apps

The phone records the match and shows the score on the video. The user
keeps the score during the match. These apps do not remove the dead time.

| App | Platforms | Sports | Notes |
|---|---|---|---|
| [Scorify](https://apps.apple.com/app/6749523905) | iPhone, Apple Watch | Tennis, pickleball | Free: 3 recordings per week with a watermark. A flag on a point makes a clip |
| [BT Tennis Camera](https://apps.apple.com/app/id6477843371) | iPad | Tennis | Remote score control, timers for breaks and changeovers. Also BT Table Tennis Camera |
| [Scoreboard Pro: Score & Record](https://apps.apple.com/app/id496758984) | iPhone | Many sports | The recording needs Pro |
| [3DTennis](https://mwm.ai/apps/3dtennis/6754299144) | iPhone, Apple Watch | Tennis, beach tennis, padel | Clips after the key moments |
| [Racket Reel](https://www.racketreel.com/) | Phone, "any supported device" for the score | Tennis | Prototype: the page has only a waiting list ("Be the first to know when it's ready") and shows © 2021. Record, score during the match, export the full match or clips |
| [CameraFi Studio](https://blog.camerafi.com/2024/06/tennis-scoreboard-user-guide.html) | Android | Many sports | For live streams |

## 4. Club camera systems

Cameras at the club record the match. Only at partner clubs. The video is
on the server of the vendor.

| System | Sports | Notes |
|---|---|---|
| [Wingfield](https://www.wingfield.io/en) | Tennis | The app can skip the dead time |
| [PlaySight](https://playsight.com/) | Tennis, pickleball, other sports | |
| Rackety | Padel | [Example club](https://www.cnmontjuic.cat/record-your-matches-with-rackety-great-news-at-lets-padel-montjuic/) |
| TiePlayer | Padel | |
| [Paddeo Sports](https://www.paddeo.com/index_en.html) | Padel, pickleball, table tennis | Software for clubs |

## 5. General video editors

DaVinci Resolve, Premiere, CapCut, Shotcut, Clipchamp, OpenShot. They cut
anything, but have no tennis score. The scoreboard is manual work for each
point.

## 6. Scoreboard-only apps

The app keeps the score only. It does not record or edit video. These apps
compete with the scoreboard part of BananaShot and are a source of score
styles and rule options.

| App | Platforms | Sports | Notes |
|---|---|---|---|
| [ScoreCounter](https://scorecounter.com/tennis) | Web | Tennis, badminton, table tennis, many others | Free. Best of 3 or 5, final-set rule, no-ad scoring, match tiebreak to 10. Shares the live score with a link. Also tournament brackets |

## 7. Team film platforms

Cloud services for coaches and teams. The team uploads the match videos,
tags the points, and shares the clips and the stats. They are not editors
for one player.

| Platform | Platforms | Sports | Price | Notes |
|---|---|---|---|---|
| [QwikCut](https://www.qwikcut.com/tennis/) | Web (desktop), iOS, Android | Tennis and many team sports | $349 per year for high school and college teams (125 hours of storage), $329 per year for youth and club teams (75 hours) | Cloud. The user makes the highlights by hand from tags and playlists. Stats by game, season, and set; export to Excel and MaxPreps. No scoreboard on the page. Also sells AI cameras |

## 8. Not found

- Tennis Film: the name was in the rename search (2026-09-28). A web search
  on 2026-10-19 found no app with this name.
- [Court Rally](https://www.stork.ai/fr/court-rally): a coach platform with
  AI analysis ($1.99 per clip). Not a match editor.
