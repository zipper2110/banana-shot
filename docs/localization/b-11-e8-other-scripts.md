# B-11 E8: Languages with Other Scripts

This file holds the tasks of epic E8 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: in-progress
- Plan sections: 6 ("Fonts"), 7, 9, 13
- Depends on: E7 (the shared tasks E7-T1 to E7-T4), E2 (the data)
- Size: 1 to 2 weeks for the first language of a new script. Less for the
  next languages of the same script.
- Value: users of Chinese, Japanese, Korean, or other languages with large
  character sets can use the app in their language. The author selected
  Japanese, Simplified Chinese, and Traditional Chinese on 2026-10-20 (plan,
  "Decisions", "Next languages", "Chinese scripts").

Not in this epic: right-to-left languages (plan section 9).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E8-T1 | Select the language | E2 | in-progress |
| E8-T2 | Video fonts for the script | E8-T1 | in-progress |
| E8-T3 | UI font for the script | E8-T1 | in-progress |
| E8-T4 | Line breaks without spaces | E8-T1 | in-progress |
| E8-T5 | Input method tests | E8-T1 | open |
| E8-L-ja | Japanese | E8-T2–E8-T5 | in-progress |
| E8-L-zh-Hans | Simplified Chinese | E8-T2–E8-T5 | in-progress |
| E8-L-zh-Hant | Traditional Chinese | E8-L-zh-Hans | in-progress |
| E8-L-`<code>` | One task for each language (template of E7) | E8-T2–E8-T5 | — |

When E8-T1 selects a language, add a row `E8-L-<code>` to this table, and
copy the template of E7 ("Template: E7-L-`<code>` `<Language>`").

### E8-T1 Select the language

- Status: in-progress
- Plan: "Decisions" ("Next languages"), 10
- Value: the high cost of a new script goes to a language with enough
  users.
- Requirements:
  - Compare the number of users of each language (`os_language` data) with
    the cost of the fonts and of the review.
  - A reviewer must be available.
  - Chinese has two scripts. The analytics value `zh` does not tell
    Simplified from Traditional. Decide the script from other data (for
    example the feedback, or the marketing plan). Do not guess from `zh`.
  - The detection of E3-T21 maps `zh-TW`, `zh-HK`, `zh-MO`, and `zh-Hant`
    to Traditional Chinese, and `zh-CN`, `zh-SG`, and `zh-Hans` to
    Simplified Chinese. If the app has only one of the two, the other
    gives English, not the wrong script.
- Acceptance:
  - The plan, "Decisions", has the language, the data, and the reviewer.
  - If the language is Chinese, a test proves that `zh-TW` does not give
    Simplified Chinese.
- Tests: `LanguageSettingsTest` ("Chinese of Taiwan, Hong Kong, and Macau
  gives Traditional Chinese").
- Notes: on 2026-10-20 the author selected Japanese, Simplified Chinese,
  and Traditional Chinese, before the data of E2. Simplified Chinese comes
  before Traditional Chinese. The reviewers are not known yet. On
  2026-10-21 the app has both Chinese languages, so the case "only one of
  the two" does not apply.

### E8-T2 Video fonts for the script

- Status: in-progress
- Plan: 6 ("Fonts"), "Costs"
- Value: the video shows the letters of the script, and the preview shows
  the same letters as the export.
- Requirements:
  - A font for the script with a license that allows distribution (for
    example Noto Sans CJK). The third-party notices name it.
  - A decision: bundle the font (more than 15 MB for each CJK language), or
    download it when the user selects the language. Write the decision and
    the reason in the plan.
  - The export, the preview, and the thumbnails use the same font file.
  - If the font is a download, the bad cases have a defined result:
    - Without network, or with a server error, the language is not
      selectable as the video language, and the list tells the reason.
      The app tries again later. The app language can still be the
      language, because the UI font is a different font (E8-T3).
    - A file with a wrong size or a wrong checksum is deleted, and not
      used.
    - A part of a file after a stopped download is not used.
    - A full disk gives a clear message.
    - A queued job whose video font is missing does not start. It stays
      in the queue with a clear message. It never makes a video with
      empty boxes.
- Acceptance:
  - The font test of E7-T4 and the fit tests of E3-T19 pass for the
    language.
  - The preview and the export show the same letters.
  - If the font is a download: tests for no network, a wrong checksum,
    and a stopped download. In each case, the app does not use the file,
    and no export makes empty boxes.
- Tests: —
- Notes: on 2026-10-20 the app uses the Windows font Yu Gothic for the
  Japanese video texts (plan, "Decisions", "CJK fonts"). No font is bundled
  or downloaded, so the bad cases of a download do not apply.
  `ScoreboardFonts.family` gives the font of the script when the style font
  cannot show a text: in a Japanese video, or for a text with kana. The
  scoreboards, the comments, the statistics card, the thumbnails, and the
  focus chip of the Transform tab use it. `ScoreboardFontCoverageTest`
  checks the kana and all characters of the Japanese catalog.
  `ScoreboardPreviewMain --language=ja` shows the video texts of all
  styles. They fit. On 2026-10-21 Chinese uses the Windows fonts in the
  same way: Microsoft YaHei for `zh-Hans` and Microsoft JhengHei for
  `zh-Hant`. A text with kana uses Yu Gothic also in a Chinese video.
  `ScoreboardFontCoverageTest` checks all characters of each Chinese
  catalog. `ScoreboardPreviewMain --language=zh-Hans` and
  `--language=zh-Hant` show the video texts of all styles. They fit.
  Japanese and Chinese use the same Unicode code points for many
  characters (Han unification), but the correct shapes are different. Thus
  the video font follows the video language: a Japanese font for `ja`, a
  Simplified Chinese font for `zh-Hans`, and a Traditional Chinese font for
  `zh-Hant`. A single CJK font shows the wrong shapes for two of the three.
  The scoreboard styles now use Windows fonts (Arial Black, Georgia,
  Trebuchet MS, Ink Free). The Windows CJK fonts (Yu Gothic, Microsoft
  YaHei, Microsoft JhengHei) are an option to examine before a bundled or
  downloaded font.

### E8-T3 UI font for the script

- Status: in-progress
- Plan: 7
- Value: the UI shows the letters of the script, not empty boxes.
- Requirements:
  - The UI font now is "Segoe UI". It has no Chinese, Japanese, or Korean
    letters, and Swing does not use a fallback font for it.
  - With the language, the UI uses a font that has the letters (for example
    the Windows fonts Microsoft YaHei, Yu Gothic, Malgun Gothic), or a font
    with a fallback.
  - The custom-painted components use the same font.
  - The user texts in Latin letters (player names) still show correctly.
  - If the selected UI font is not installed (for example a Windows
    edition without the font), the app uses the Java logical font
    "Dialog", which has a fallback, and logs one warning.
- Acceptance:
  - The screenshots of E4-T4 in the language show no empty boxes.
  - A test with a missing UI font gives the "Dialog" font and no empty
    boxes.
- Tests: `ScriptFontsTest` ("a missing Japanese UI font gives the Dialog
  font", "a missing Chinese UI font gives the Dialog font", "the UI font of
  each script shows all characters of its catalog"), `UiFontsTest` ("each
  language name in the list shows without empty boxes").
- Notes: as in E8-T2, the UI font follows the app language: for example
  Yu Gothic UI for `ja`, Microsoft YaHei UI for `zh-Hans`, and Microsoft
  JhengHei UI for `zh-Hant`. On 2026-10-20 `ScriptFonts.uiFamily` and
  `UiFonts` give Yu Gothic UI (and Yu Gothic UI Semibold) for `ja`. The
  language lists (Settings, Export "Video language") show each name in its
  own language, so `UiFonts.showLanguageNames` gives an entry the UI font of
  its language when the font of the app language cannot show it (for
  example "日本語" in an English app). On 2026-10-21 `zh-Hans` uses
  Microsoft YaHei UI and `zh-Hant` uses Microsoft JhengHei UI. These fonts
  have no semibold form, so the semibold texts use the bold form. Microsoft
  JhengHei UI has no "−" (U+2212), so the Traditional texts use "－".
  Open: the screenshots of E4-T4 in Japanese and in Chinese.

### E8-T4 Line breaks without spaces

- Status: in-progress
- Plan: 7
- Value: long texts in Chinese and Japanese wrap correctly in the UI and in
  the help.
- Requirements:
  - Chinese and Japanese have no spaces between words. The text wrap of the
    UI and of the help breaks a line between characters, by the line break
    rules of the language (for example `java.text.BreakIterator`).
  - No line starts with a closing punctuation mark, and no line ends with
    an opening punctuation mark.
  - A Latin word or a number inside a CJK text (for example "BananaShot"
    or "1080p") does not break, unless it is wider than the line.
  - Korean breaks at the spaces between words, as the Korean users
    expect.
  - The "maximum length" in the key comments is in characters. A CJK
    character is about two times wider than a Latin letter. The fit test
    of E3-T19 measures the width, and it decides.
- Acceptance:
  - A unit test wraps a Chinese and a Japanese text to a given width. No
    line is wider than the width, and no line starts with a closing
    punctuation mark.
  - The test also checks: no line ends with an opening punctuation mark,
    "BananaShot" inside a Chinese text stays on one line, and a Korean
    text breaks only at spaces.
- Tests: `LineBreaksTest` (also "a Chinese text does not break before a
  closing mark or after an opening mark"), `WrapTextTest` ("a Japanese text
  wraps between characters", "a Chinese text wraps between characters"),
  `CommentAssTest` ("japaneseCommentWrapsBetweenCharacters").
- Notes: on 2026-10-20 `LineBreaks` splits a word with Chinese or Japanese
  characters by `BreakIterator.getLineInstance`. A word without them stays
  one piece, so the other languages do not change. The text wraps of
  `ui.commons` (also the help), of the Stats, Export, Projects, and
  Adjustments tabs, and of the video comments use it. The Swing HTML texts
  and `JTextArea` use the line breaks of Swing. On 2026-10-21 the wrap test
  with a Chinese text in a component passes.

### E8-T5 Input method tests

- Status: open
- Plan: 9
- Value: a user can type in their script in all text fields when the app
  is in their language.
- Requirements:
  - The input method of the language works on Windows for player names,
    comments, project names, and the search fields.
  - E3-T23 did a first check. Repeat it with the app in the language.
  - During the composition of a text, the shortcuts do not act, and Enter
    selects the candidate. Enter does not close the dialog.
  - Full-width digits and a full-width ":" in a time code field give the
    result of E1-T2 (the same value or a clear error).
- Acceptance:
  - The manual check is in `qa/windows/ui-smoke.md`, and it passes.
  - The manual check includes Enter during the composition in a dialog,
    and a time code typed in full-width mode.
- Tests: — (manual)

### E8-L-ja Japanese

- Status: in-progress
- Plan: "Decisions" ("Form of address"), 7, 13, and the process of E4
  (`CONTRIBUTING.md`)
- Value: the users in Japan can use the app and share videos in Japanese.
- Requirements:
  - The template of E7.
  - The polite です/ます form in sentences. Buttons and menu items use
    short noun forms ("エクスポート", "保存").
  - Japanese quotes are 「…」. Japanese has no plural forms, so each plural
    message has only `other`.
  - The Japanese video font and UI font (E8-T2, E8-T3).
- Acceptance:
  - The template of E7.
  - The screenshots show no Chinese character shapes in the Japanese UI
    and video.
  - A test gives Japanese for `ja-JP` and `ja`.
- Tests: `MessageCatalogTest` ("the Japanese catalog has texts in
  Japanese", "Japanese texts use the Japanese quotes", and the checks of all
  languages), `LanguageSettingsTest` ("Japanese gives Japanese"),
  `MessagePatternTest` ("a Japanese plural uses only other"),
  `EffectiveExpiryTest` ("the moment shows in Japanese with the year, month,
  and day marks"), `ScoreboardFontCoverageTest`, `ScriptFontsTest`.
- Notes:
  - Done on 2026-10-20: the machine draft of `messages_ja.properties` and
    `help_ja.properties`, the glossary, the detection, the plural rule
    (only `other`), and the expiry date format ("2027年3月29日 16:00").
    The language list of E2-T1 already has `ja`.
  - The UI font, the video font, and the line breaks come from E8-T2,
    E8-T3, and E8-T4.
  - The key names use arrows ("←", "→", "↑", "↓"). The key chips use the
    font "Dialog" for a character that the UI font does not show.
  - The help and the texts put the names of tabs and buttons in 「…」.
  - Open: the review by a native speaker, the screenshots and the sidebar
    width in the app, the Windows smoke test in Japanese, the input method
    check (E8-T5), and the open parts of E7-T3 and E7-T4.

### E8-L-zh-Hans Simplified Chinese

- Status: open
- Plan: "Decisions" ("Chinese scripts", "Form of address"), 7, 13, and
  the process of E4 (`CONTRIBUTING.md`)
- Value: the users in mainland China and Singapore can use the app in
  Chinese. YouTube is blocked in mainland China, so these users share the
  videos on other platforms (for example Bilibili, Douyin, or WeChat).
- Requirements:
  - The template of E7.
  - The "你" form. Simplified Chinese quotes are “…”. Chinese has no
    plural forms, so each plural message has only `other`.
  - The help and the texts do not say that YouTube is the only place to
    share a video. Check the thumbnail texts ("YouTube thumbnail").
  - A reviewer from mainland China.
- Acceptance:
  - The template of E7.
  - A test gives Simplified Chinese for `zh-CN`, `zh-SG`, `zh-Hans`, and
    `zh`.
- Tests: `MessageCatalogTest` ("the Chinese catalogs have texts in
  Simplified and in Traditional Chinese", "Chinese texts use the quotes of
  their script", "each Chinese catalog uses only the characters of its
  script", and the checks of all languages), `LanguageSettingsTest`
  ("Chinese of mainland China, Singapore, and without a region gives
  Simplified Chinese"), `MessagePatternTest` ("a Chinese plural uses only
  other"), `EffectiveExpiryTest` ("the moment shows in Chinese with the
  year, month, and day marks"), `ScoreboardFontCoverageTest`,
  `ScriptFontsTest`.
- Notes:
  - Done on 2026-10-21: the machine draft of `messages_zh_Hans.properties`
    and `help_zh_Hans.properties`, the glossary, the detection, the plural
    rule (only `other`), and the expiry date format ("2027年3月29日 16:00").
    The language list of E2-T1 already has `zh`.
  - The UI font, the video font, and the line breaks come from E8-T2,
    E8-T3, and E8-T4.
  - "YouTube thumbnail" is "视频封面", and "YouTube search" is
    "视频平台的搜索结果". The texts keep "YouTube" only for a fact about
    YouTube (the 2 MB limit).
  - Open: the review by a native speaker from mainland China, the
    screenshots and the sidebar width in the app, the Windows smoke test in
    Simplified Chinese, the input method check (E8-T5), and the open parts
    of E7-T3 and E7-T4.

### E8-L-zh-Hant Traditional Chinese

- Status: open
- Plan: "Decisions" ("Chinese scripts", "Form of address"), 7, 13, and
  the process of E4 (`CONTRIBUTING.md`)
- Value: the users in Taiwan, Hong Kong, and Macau can use the app in
  Chinese.
- Requirements:
  - The template of E7.
  - A full catalog, not a child of `zh-Hans`. The draft is a conversion of
    the reviewed Simplified texts with the Taiwan terms (for example
    "影片", "專案", "檔案"). A conversion of only the characters is not
    sufficient.
  - Traditional Chinese quotes are 「…」.
  - The glossary has its own section for `zh-Hant`.
  - A reviewer from Taiwan. If possible, a second reviewer from Hong Kong
    checks the terms.
- Acceptance:
  - The template of E7.
  - A test gives Traditional Chinese for `zh-TW`, `zh-HK`, `zh-MO`, and
    `zh-Hant`, and never Simplified Chinese.
- Tests: `MessageCatalogTest` ("the Chinese catalogs have texts in
  Simplified and in Traditional Chinese", "Chinese texts use the quotes of
  their script", "Traditional Chinese uses the Taiwan terms", "each Chinese
  catalog uses only the characters of its script", and the checks of all
  languages), `LanguageSettingsTest` ("Chinese of Taiwan, Hong Kong, and
  Macau gives Traditional Chinese"), `MessagePatternTest`,
  `EffectiveExpiryTest`, `ScoreboardFontCoverageTest`, `ScriptFontsTest`.
- Notes:
  - Done on 2026-10-21: the machine draft of `messages_zh_Hant.properties`
    and `help_zh_Hant.properties` with the Taiwan terms, the glossary
    section, the detection, the plural rule, and the expiry date format.
  - The draft is a conversion of the Simplified draft, not of reviewed
    texts. The plan expects the reviewed Simplified texts first. Thus the
    Traditional texts get the corrections of the Simplified review too.
  - The texts keep "YouTube" as in English ("製作 YouTube 縮圖").
  - Open: the review by a native speaker from Taiwan (and, if possible, one
    from Hong Kong), the screenshots and the sidebar width in the app, the
    Windows smoke test in Traditional Chinese, the input method check
    (E8-T5), and the open parts of E7-T3 and E7-T4.

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks of the script are done before its first
language.
