# Contributing

Thank you for your interest in BananaShot.

## Code contributions

The project does not accept outside code contributions at this time.
Do not open pull requests with code changes. Maintainers will close them.

The reason: the project owner must keep the full copyright of the code, so
that the owner can change the license in the future. Before the project
accepts outside code, it will publish a Contributor License Agreement (CLA).
Each contributor must then sign the CLA before a maintainer merges their code.

## Issues

You can open issues for bug reports and feature requests.

## Translations

The app texts come from a machine translation. No native speaker reviewed
them. If a text is wrong or not natural in your language, open an issue. Give
the English text, the current text, and your correction. If you play tennis
or padel and want to review all texts of a language, write to the contact
address on the Privacy page of the app.

### Add a new language

The maintainer uses these steps for each new language. The plan is in
[docs/localization/b-11-localization.md](docs/localization/b-11-localization.md).
Spanish (epic E4) was the first language that used them.

1. **Language code.** The code must be in the language list of the usage
   statistics (`AnalyticsEvent.Language.CODES`, B-11 E2-T1). The servers
   accept only these codes. If the code is not in the list, change the list
   and deploy the servers before the release.
2. **App language.** Add the language to `AppLanguage`: the code, the name
   of the language in the language itself, and the parent for a regional
   variant. Add it to `AppLanguage.forLocale`, so that "Same as Windows"
   finds it. Add its plural categories to `PluralRules` in `Messages.kt`. A
   language with a new script also needs a font (see `ScriptFonts`).
3. **Glossary.** Add a table for the language to
   [docs/localization/glossary.md](docs/localization/glossary.md): the sport
   terms, the score words, the app terms, and the words of each regional
   variant. The "do not translate" list is the same for all languages.
4. **Draft.** Make a machine translation of `messages.properties` and
   `help.properties` into `messages_<code>.properties` and
   `help_<code>.properties`. Give the translator the glossary and the key
   comments. Keep the keys, the placeholders (`{name}`), the ICU keywords
   (`plural`, `one`, `other`), the apostrophes, and the markup. A regional
   variant has only the texts that are different from its parent.
5. **Records.** Write the record of the English text above each key:
   `TranslationCheckMain` with `--stamp=<code> --all-keys`. The command is in
   `TranslationCheckMain.kt`.
6. **Checks.** Run `MessageCatalogTest`. It finds a missing key, a broken
   placeholder or plural, a lost term of the "do not translate" list, and
   changed markup. A language with a missing text fails the test, so it
   cannot be in a build. Run `TranslationCheckMain`: it must show no missing
   and no stale key for the language. Add a test for the
   quotes and the punctuation of the language, as for French, German,
   Japanese, and Chinese.
7. **Fit check.** Run `VideoTextFitTest`. Make the screenshots of the UI with
   `qa/Make-TranslatorScreenshots.ps1 -Language <code>`, and look for cut
   texts. If a video text is cut, give a shorter text first. Change the
   layout only if no shorter text is possible.
8. **Smoke test.** Do a pass of [qa/windows/ui-smoke.md](qa/windows/ui-smoke.md)
   on Windows in the language. Include the installer, the date and number
   formats, an export with each scoreboard layout, and a change of the
   language with "Restart now".
9. **Review.** A review by a native speaker is optional. If a reviewer comes
   forward, the corrections go into the next release.
10. **Release.** The release checklist runs the translation check. Add the
    language to the list in [README.md](README.md).

The privacy notice on the site stays in English, and the English text is
binding. The app already tells this in each language other than English
(`privacy.notice.english`). A new translation does not change the notice
version.

## Formats in the code

The Windows regional format must not change a text that a program reads.

- A text for a program (FFmpeg arguments, ASS files, JSON, hex colors, time
  codes) uses `MachineFormats` or an explicit `Locale.ROOT`.
- A number, date, or time that the user reads uses `DisplayFormats`.
- Do not change the default locale of the JVM.

`DefaultLocaleScanTest` finds a format call without a locale. The full rules
are in [docs/architecture-rules.md](docs/architecture-rules.md), "Formats and
the locale".

## License

BananaShot uses the Elastic License 2.0. See [LICENSE](LICENSE) and
[LICENSE-NOTICE](LICENSE-NOTICE). Versions up to the `last-gpl` tag were
released under the GNU General Public License version 3 or later.
