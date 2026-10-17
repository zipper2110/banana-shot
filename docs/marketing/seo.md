# SEO and AI search: outline

Written on 2026-10-17. This file adds detail to the channel "Site SEO and
how-to articles" in `strategy.md`, section 5. It also covers AI search:
ChatGPT search, Perplexity, Google AI Overviews, Copilot, and Claude.

The search phrases in this file are guesses. Google Search Console and Bing
Webmaster Tools show the real phrases after some weeks. Replace the guesses
with real data then.

## 1. Summary

- The site has a good technical base. Do not rebuild it. Add to it.
- The site has only one page that talks about the product. A search engine
  can rank it for only a few phrases. More pages that answer real questions
  give more ways to find the app.
- AI search engines repeat what other sites say about a product. Mentions on
  Reddit, forums, YouTube, catalogs, and comparison articles are more
  important for AI search than the site itself.
- ChatGPT search and Copilot use the Bing index. Register the site with Bing,
  not only with Google.
- Each page must state facts in plain sentences: what the app is, the price,
  the system, the file formats. AI answers copy such sentences.

## 2. What the site does well now

- Each page has its own `<title>`, description, and canonical URL.
- `robots.txt` lets all crawlers in. `sitemap.xml` lists all pages.
- The pages are static HTML. They load fast and need no JavaScript for the
  text. Crawlers that do not run JavaScript (most AI crawlers) see all text.
- The demos have an `aria-label` that describes each screen in words.
- The FAQ answers 16 questions, each with its own anchor.
- The author section, the real match videos, and the GitHub link show a real
  person and a real product. Search engines and AI engines value this.
- The `<title>` of the home page uses the search words "tennis match video
  editor for Windows" (decision 20 in `docs/site/b-10-tasks.md`).
- The bot counter shows Googlebot, Bingbot, OpenAI, Anthropic, and other
  crawlers. `count.js` keeps the referrer host. Thus the cockpit can show
  visits from `chatgpt.com` or `perplexity.ai`.

## 3. Gaps

| Gap | Effect |
|---|---|
| No structured data (JSON-LD) | Search engines and AI engines must guess the price, the system, and the type of the product |
| No Google Search Console and no Bing Webmaster Tools | No data about search phrases. ChatGPT and Copilot can miss the site |
| Cloudflare can block AI crawlers by default on new domains | AI search can have no access to the site, although `robots.txt` allows it |
| Only one product page | The site can rank only for "tennis video editor" phrases |
| No pages for "how to" questions | People who search for a solution do not find the app |
| No comparison pages | AI answers to "SwingVision alternative" do not include the app |
| `sitemap.xml` has no `<lastmod>` | Crawlers do not know which pages changed |
| Few words like "highlights", "score overlay", "dead time" | Searches with these words do not find the home page |
| Few mentions on other sites | AI engines do not know the app exists |

## 4. Search phrases (guesses)

Put each group on one page. Do not put the same group on two pages.

| Group | Example phrases | Page |
|---|---|---|
| Product | tennis match video editor, tennis video editing software, tennis video editor for PC | Home |
| Scoreboard | add scoreboard to tennis video, tennis score overlay, tennis scoreboard for video | How-to page |
| Dead time | cut dead time from tennis video, tennis match only points, shorten tennis match video | How-to page |
| Highlights | make tennis highlights, tennis highlight video from full match | How-to page |
| Recording | how to record a tennis match with a phone, best camera position for tennis | How-to page |
| Alternatives | SwingVision alternative, free SwingVision for Windows, Tennis Studio alternative | Comparison page |
| Padel | padel match video editor, padel scoreboard video | Padel page |
| Coaches and parents | tennis video analysis for coaches, junior tennis match video | Later pages |

## 5. Phase A: the base (this week, 2 to 4 hours)

- [ ] Register the site in Google Search Console. Send `sitemap.xml`.
- [ ] Register the site in Bing Webmaster Tools. Import the data from
      Google Search Console. Send `sitemap.xml`.
- [ ] In the Cloudflare dashboard, open "AI Crawl Control" (or "Bots") for
      the domain. Make sure that it does not block the AI crawlers of
      search: `OAI-SearchBot`, `ChatGPT-User`, `PerplexityBot`,
      `Claude-SearchBot`, `Claude-User`, and Bingbot. Make sure that the
      "managed robots.txt" does not add rules. Then open
      `https://banana-shot-editor.app/robots.txt` and compare it with the
      file in the repository.
- [ ] Turn on "Crawler Hints" in Cloudflare. It tells Bing and other engines
      about changed pages (IndexNow).
- [x] Add JSON-LD to the home page. Done on 2026-10-17: `WebSite`,
      `Person` (the author, `sameAs` GitHub), and `SoftwareApplication`
      (price 0, Windows 10 and 11, version, download URL, features,
      license). Decisions:
      - No `screenshot`: the site has no image file of the app screen. The
        demos are HTML. Add it when a real screenshot file exists.
      - No `VideoObject` for the two match videos: the page shows only a
        link with a still image, not a player. Google uses `VideoObject`
        only for a video that plays on the page. Add it with the tutorial
        video, if the page embeds the player.
      - Add the YouTube channel to `sameAs` after YouTube verifies it.
- [x] Add JSON-LD `FAQPage` to the FAQ. Google shows FAQ results only for a
      few official sites, but AI engines and Bing read it. Done on
      2026-10-17. The script `site/tools/faq-jsonld.mjs` makes the block
      from the page, so the text is always the same (`site/README.md`,
      "Search data").
- [x] Add `<lastmod>` to each URL in `sitemap.xml`. Done on 2026-10-17. The
      rule is in `site/README.md` and in `docs/release-checklist.md`.
- [ ] Deploy the site. Then test the home page and the FAQ in the Google
      Rich Results Test and the Schema.org validator.
- [ ] Add the missing search words to the home page where they fit
      naturally: "highlights", "score overlay", "dead time". Follow the site
      copy style. Do not fill the page with keywords.
- [ ] Add facts to the download page: the latest version, the date, and the
      file size. AI answers like exact facts.
- [ ] GitHub repository: add a one-sentence description with the site link.
      Add the topics `tennis`, `video-editor`, `scoreboard`, `padel`,
      `windows`. The README must start with one plain sentence about what
      the app does.

## 6. Phase B: pages that answer questions (months 1 to 3)

This phase replaces the item "Write 3 to 5 how-to pages" in `strategy.md`,
Phase 2.

### Rules for each page

- One page answers one question. The URL contains the main words, for
  example `/guides/tennis-video-scoreboard/`.
- The first paragraph gives the answer in two or three sentences. The rest
  gives the steps and the details. AI engines quote the first paragraph.
- Each page shows the steps with any tool first, and then with BananaShot.
  An honest page gets more links and more trust than an advertisement.
- Use real screenshots and frames from your own matches.
- Each page has a date and the author name.
- Add the page to the header or to a "Guides" page, to `sitemap.xml`, and
  to the footer of the other pages (see `site/README.md`).
- Add a `HowTo` or `Article` JSON-LD block.

### Pages in order

1. How to add a scoreboard to a tennis video.
2. How to cut the dead time from a tennis match video.
3. How to record a tennis match with a phone (camera position, mount,
   settings, battery, file size). This page brings people before they need
   an editor.
4. How to make a highlights video of a tennis match.
5. Free alternatives to SwingVision for Windows. Compare honestly: what each
   app does better. Include general editors and Tennis Studio. Check the
   facts and the prices on the date of the page.
6. Padel match video with a score (when the padel scoring is ready to show).

### The tutorial video

- When the tutorial video is ready (`tutorial-video.md`), embed it on the
  home page and on the guide pages that it fits.
- Add a `VideoObject` JSON-LD block for it.
- Give the YouTube video chapters. Google often shows YouTube videos in
  answers to "how to" questions.

## 7. Phase C: mentions on other sites (all the time)

AI engines learn about a product from other sites. This phase uses the
channels of `strategy.md`. It adds these rules for search:

- When you answer a question on Reddit or Talk Tennis, name the app and the
  problem in the same sentence, for example "BananaShot, a free Windows app
  that cuts the dead time and adds a scoreboard". AI engines connect the
  name with the problem.
- Add the app to AlternativeTo as an alternative to SwingVision, Tennis
  Studio, and general editors. Use the same description everywhere.
- Add the app to winget, the Microsoft Store, and 2 or 3 software catalogs.
- Each exported video and each YouTube description links to the site.
- Ask tennis bloggers who write "best tennis apps" lists to look at the app.
  These lists are a main source of AI answers to "which app".
- A "Show HN" or a Product Hunt launch gives links from strong sites.

## 8. Later

- Pages for coaches and for parents of junior players, when real users of
  these groups give quotes.
- A Spanish version of the site with `hreflang`, after the Spanish UI
  (B-11).
- A macOS page only when the macOS version exists.
- `llms.txt`: a short text file for AI tools. No large AI engine confirms
  that it uses the file. Add it only when the other work is done. It takes
  15 minutes.

## 9. What to measure

Look at these numbers once a month. Search results change slowly.

| Number | Source |
|---|---|
| Search phrases, views, clicks, and position | Google Search Console, Bing Webmaster Tools |
| Pages in the index | Google Search Console, Bing Webmaster Tools |
| Visits from search engines and from AI tools (`chatgpt.com`, `perplexity.ai`, `copilot.microsoft.com`, `gemini.google.com`) | Cockpit, referrer host |
| Visits of AI crawlers | Cockpit, bot counter |
| Answers of AI tools | Each month, ask ChatGPT, Perplexity, and Google: "app to edit a tennis match video with a scoreboard", "free SwingVision alternative for Windows". Write down if the answer names the app |
| "How did you find the app?" | The answers of new users (`strategy.md`, Phase 1) |

## 10. What not to do

- Do not buy links or reviews.
- Do not make many thin pages with small changes of the same phrase.
- Do not use AI to write the guide pages without your own facts and
  frames. Such pages do not rank and do not get links.
- Do not change the URLs of existing pages. The app opens `/privacy/`.
- Do not hide text for search engines. All text must show to the visitor.
