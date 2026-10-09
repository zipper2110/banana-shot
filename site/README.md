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

GitHub Actions can do the deploy: run the workflow "Deploy" from `master` and select "Site and www-redirect".
Setup of the token: see `.github/workflows/cloudflare-deploy.yml`.

To deploy from your computer, use these commands.

From the repository root:

```bash
analytics-worker/node_modules/.bin/wrangler deploy --config site/wrangler.toml
```

`wrangler.toml` connects the domain `banana-shot-editor.app` to the site.
The site has no Worker code and no secrets.

`www-redirect/` is a separate small Worker. It redirects
`www.banana-shot-editor.app` to the domain without www (301, same path and
query). Deploy it again only when you change it:

```bash
analytics-worker/node_modules/.bin/wrangler deploy --config site/www-redirect/wrangler.toml
```

Web Analytics uses the automatic setup of Cloudflare. Cloudflare adds the
beacon script to the pages, so the files have no analytics script.
