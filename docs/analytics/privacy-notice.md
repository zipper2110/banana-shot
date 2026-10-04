# Privacy notice: usage analytics (maintainer notes)

The full text of the notice is on the landing site (B-10):
`site/public/privacy/index.html`, section "Usage analytics" (`#analytics`).
The URL of the page is `https://banana-shot-editor.app/privacy/`. Since
2026-10-03 (B-10 decision 4), the site page is the only full text. Change the
text there, not in this file.

The build gets the URL of the page through `-AnalyticsPrivacyUrl` (the GitHub
variable `ANALYTICS_PRIVACY_URL`). The site must show the notice before the
first release with analytics.

The app has a short version of this text: the consent dialog
(`AnalyticsConsentDialog.INTRO`) and the Privacy page in More
(`PrivacyPage.ANALYTICS_COLLECTED` and `ANALYTICS_EXCLUDED`). Keep the texts in
agreement.

When a counter key is added or changed, update the list in the "Usage
analytics" section of the site page. Increase the notice version if the new
key collects a new type of data. The app then asks the user again.

- Notice version: 1
- Effective date: the date of the first release with analytics. Write it on
  the site page ("Effective from") at the release.

The "Version check and updates" section of the same page tells about the
request to GitHub (`docs/licensing/build-expiry-spec.md`). That request is
separate from the analytics consent.
