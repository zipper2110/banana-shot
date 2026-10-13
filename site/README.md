# Landing site

The public site of BananaShot: `https://banana-shot-editor.app/`. B-10 in
`docs/backlog.md`. `docs/site/b-10-tasks.md` has the decisions and the tasks.

## Files

- `public/` has the files of the site. Plain HTML and CSS: no build step.
- Each page is `public/<name>/index.html`. The URL is `/<name>/`.
- `public/assets/site.css` has all styles. The colors come from
  `Palette.kt` of the app.
- Each page has the Open Graph tags (`og:*`, `twitter:card`) for the link
  previews in chats and social networks. All pages use the image
  `public/assets/og-image.jpg`. Its source is in `design/social-preview/`.
  When you add a page, copy the tags and change the title, the description,
  and the URL.
- Each page has its own copy of the header and the footer. When you add a
  page or change a link, change all pages, `404.html`, and `sitemap.xml`.

## Rules

- `public/privacy/index.html` is the only full text of the privacy notice.
  The app opens `https://banana-shot-editor.app/privacy/`. Do not change this
  path. Old builds open it for 6 months after their build date.
- When the privacy text changes, also check the short texts in the app
  (`PrivacyPage.kt`, `AnalyticsConsentDialog`). The maintainer notes are in
  `docs/analytics/privacy-notice.md` and `docs/feedback/privacy-notice.md`.
- At each release, add the version to `public/changelog/index.html`.

## Preview

Run the site on your computer (it uses the wrangler of `analytics-worker`):

```bash
analytics-worker/node_modules/.bin/wrangler dev --config site/wrangler.toml --port 8790 --local
```

Then open `http://localhost:8790/`. The IDE can also start the
configuration `site` in `.claude/launch.json`.

## Deploy

GitHub Actions can do the deploy: run the workflow "Cloudflare deploy" from `master` and select "Site and www-redirect".
Setup of the token: see `.github/workflows/cloudflare-deploy.yml`.

To deploy from your computer, use these commands.

From the repository root:

```bash
analytics-worker/node_modules/.bin/wrangler deploy --config site/wrangler.toml
```

`wrangler.toml` connects the domain `banana-shot-editor.app` to the site.
The site has a small Worker (`worker.js`, see "Bot counter") and no secrets.

`www-redirect/` is a separate small Worker. It redirects
`www.banana-shot-editor.app` to the domain without www (301, same path and
query). Deploy it again only when you change it:

```bash
analytics-worker/node_modules/.bin/wrangler deploy --config site/www-redirect/wrangler.toml
```

## Page view counter

The cockpit shows the exact page views of the site. Cloudflare Web Analytics
samples its data, so its numbers are only estimates. Thus the site counts
page views with its own script:

- `public/assets/count.js` sends one beacon for each page view to the
  analytics Worker (`POST /v1/site`). The body is the page path and
  `document.referrer`. Each page loads the script at the end of `<body>`.
  `404.html` gives the path `/404` in `data-path`.
- The analytics Worker accepts only the origin `https://banana-shot-editor.app`.
  It adds 1 to a daily total in the table `site_daily` of its D1 database
  (migration `0004_site_views.sql`). It keeps only the referrer host, the
  country code of Cloudflare, and the device type. It does not count bots.
- When you add a page, add the script tag to it.

## Bot counter

Most bots do not run scripts, so the beacon does not see them. The beacon
also ignores the bots that run scripts. Thus bots are counted separately:

- `worker.js` is the Worker of the site. It serves the files with
  `env.ASSETS`. For each HTML answer, it calls the analytics Worker over the
  service binding `ANALYTICS` (RPC entrypoint `SiteCounter`) with the path,
  the status, and the user agent.
- The analytics Worker finds the bot name from the user agent (`botName` in
  `analytics-worker/src/site.ts`). It adds 1 to a daily total in
  `site_bot_daily` (migration `0005_site_bots.sql`). It does not store a
  request of a person.
- `run_worker_first` sends only the requests outside `/assets/` to the
  Worker. A failure of the counter does not change the answer.
- Deploy the analytics Worker before the site: the site needs the
  entrypoint `SiteCounter`.

Web Analytics uses the automatic setup of Cloudflare. Cloudflare adds the
beacon script to the pages, so the files have no Web Analytics script. The
cockpit does not read Web Analytics. When you turn it off in the Cloudflare
dashboard, remove its paragraph from the "This site" section of the privacy
page.
