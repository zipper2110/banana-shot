# Privacy notice: usage analytics (maintainer notes)

The full text of the notice is on the landing site (B-10):
`site/public/privacy/index.html`, section "Usage statistics" (`#analytics`).
The section tells about the two levels (`#essential`, `#extended`), the
legal basis of each level, the right to object, and how to turn off all
statistics (`#turn-off`).
The URL of the page is `https://banana-shot-editor.app/privacy/`. Since
2026-10-03 (B-10 decision 4), the site page is the only full text. Change the
text there, not in this file.

The build gets the URL of the page through `-AnalyticsPrivacyUrl` (the GitHub
variable `ANALYTICS_PRIVACY_URL`). The site must show the notice before the
first release with analytics.

The app has a short version of this text: the consent dialog
(`AnalyticsConsentDialog.ESSENTIAL` and `EXTENDED`) and the Privacy page in
More (`PrivacyPage.ANALYTICS_ESSENTIAL`, `ANALYTICS_EXTENDED`, and
`ANALYTICS_EXCLUDED`). Keep the texts in agreement.

When a counter key or a session attribute is added or changed, update the list in the "Usage
statistics" section of the site page. A new essential key needs a strong
reason: the essential level is on by default, and it rests on legitimate
interest. Update `docs/analytics/legitimate-interest.md` first. Increase the notice version if the new
key collects a new type of data. The app then asks the user again.

- Notice version: 2 (B-44, 2026-10-09). Version 2 adds the session
  attributes: the theme, the accent (default or custom), the app language,
  and the sports of the projects. Version 1 was effective from 6 October 2026
  (B-10 decision 29).
- Effective date: 9 October 2026. It is on the site page ("Effective from").
  When the notice version changes, write the new date.

The "Version check and updates" section of the same page tells about the
request to GitHub (`docs/licensing/build-expiry-spec.md`). That request is
separate from the analytics consent.
