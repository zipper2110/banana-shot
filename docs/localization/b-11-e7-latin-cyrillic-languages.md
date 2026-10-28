# B-11 E7: More Languages with Latin and Cyrillic Letters

This file holds the tasks of epic E7 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: in-progress
- Plan sections: 2 (plural forms), 6 ("Fonts", "Fit"), 8, 12, 13
- Depends on: E4 (the process for a new language), E2 (the data)
- Size: about 1 week for each language. No native review (plan,
  "Decisions", "Translation", 2026-10-28).
- Value: a large part of the users can use the app in their language. The
  author selected Russian, Portuguese, French, and German on 2026-10-20
  (plan, "Decisions", "Next languages"). The data of E2 selects the later
  languages.

B-11 is done after E6. This epic continues with each new language.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E7-T1 | Select the languages | E2 | in-progress |
| E7-T2 | Plural forms with the CLDR rules | — | in-progress |
| E7-T3 | Shortcuts with Cyrillic and Greek keyboard layouts | — | in-progress |
| E7-T4 | Font check for each language | — | done |
| E7-L-ru | Russian | E7-T1–E7-T4 | in-progress |
| E7-L-pt | Portuguese (Brazil and Portugal) | E7-T1–E7-T4 | in-progress |
| E7-L-fr | French | E7-T1–E7-T4, E7-L-pt | in-progress |
| E7-L-de | German | E7-T1–E7-T4, E7-L-fr | in-progress |
| E7-L-`<code>` | One task for each language (see the template) | E7-T1–E7-T4 | — |

When E7-T1 selects a language, add a row `E7-L-<code>` to this table (for
example `E7-L-pt`), and copy the template below.

### E7-T1 Select the languages

- Status: in-progress
- Plan: "Decisions" ("Next languages"), 10
- Value: the translation work goes to the languages with the most users.
- Requirements:
  - Use at least 4 weeks of `os_language` data from the cockpit (E2).
  - Compare the data with the marketing plan.
  - Select the languages and their order. A reviewer is not necessary
    (plan, "Decisions", "Translation").
- Acceptance:
  - The plan, "Decisions", has the list, the order, and the data.
- Tests: — (decision)
- Notes: the author selected Russian on 2026-10-20, before the 4 weeks of
  data (plan, "Decisions", "Russian"). On the same day the author selected
  Portuguese, French, and German in this order, also before the data (plan,
  "Decisions", "Next languages"). On 2026-10-28 the author decided that
  the languages ship without reviewers. Later languages wait for the
  data.

### E7-T2 Plural forms with the CLDR rules

- Status: in-progress
- Plan: 2 ("Message syntax"), 12
- Value: texts with a number are correct in languages with more than two
  plural forms ("1 punto", but Russian "1 очко", "2 очка", "5 очков").
- Requirements:
  - Each plural message of each language has all plural categories of the
    language (CLDR rules). Example: Russian needs `one`, `few`, `many`, and
    `other`.
  - If E3-T1 selected own plural rules, the rules of each new language come
    from the CLDR data.
  - A plural message with a case that the language does not have (for
    example `few` in Spanish, often a copy from another language) is a
    test failure.
  - A number with decimals uses the CLDR category for decimals. Example:
    Russian "1,5 секунды" uses `other`, not `one`.
  - Zero uses the category of the language. Example: French "0 point"
    uses `one`. Use an explicit `=0` case only when the English text has
    one too.
- Acceptance:
  - A catalog test fails for a Russian message with only `one` and
    `other`.
  - A test formats a Russian plural message with 1, 2, 5, and 21, and gets
    the correct form for each number.
  - The test also uses 0, 11, 12, 22, 25, 101, 111, and 1.5 for Russian
    and Polish, and 0 and 1.5 for French.
  - A catalog test fails for a Spanish message with a `few` case.
- Tests: `MessageCatalogTest` ("each plural has the categories of its
  language", "the plural check finds a missing and an extra category"),
  `MessagePatternTest` ("a Russian plural uses the four Russian
  categories").
- Notes: Russian, French, and German are done. Polish has no rule in `PluralRules`
  yet. Add its rule and test numbers with the language.
  The CLDR rules of the selected languages are different for 0 and for
  decimals:
  - `pt` (Brazil) and `fr`: `one` for 0, 1, and 1.5 (i = 0 or 1).
  - `pt-PT` and `de`: `one` only for 1 without decimals (i = 1 and v = 0).
    Thus `pt-PT` needs its own rule, also as a child of `pt`.
  - `ja`, `zh-Hans`, `zh-Hant`: only `other` (E8).
  - CLDR also gives `fr`, `pt`, and `es` a `many` case for millions in
    compact notation. The app has no compact numbers, so the Spanish rule
    has no `many`. Do the same for `fr` and `pt`.

### E7-T3 Shortcuts with Cyrillic and Greek keyboard layouts

- Status: in-progress
- Plan: 8
- Value: a user with a Russian, Ukrainian, or Greek keyboard layout can use
  all shortcuts without a change of the layout.
- Requirements:
  - Each shortcut works with the Russian and the Greek keyboard layouts on
    Windows. The shortcut binds the physical key, not the typed character.
  - No shortcut uses Ctrl+Alt with a letter or a punctuation key. Windows
    sends AltGr as Ctrl+Alt. Polish, German, Czech, and other layouts type
    letters with AltGr (for example "ą", "ł", "@", "€"). Such a shortcut
    acts when the user types the letter.
  - No shortcut uses a punctuation key ("[", "]", "+", "/", ";"). On many
    layouts these keys have other characters or need AltGr.
  - While a text field has the focus, the user can type all letters of
    the layout, also with AltGr.
- Acceptance:
  - A source scan test fails for a shortcut that binds a typed character.
  - The scan test also fails for a shortcut with Ctrl+Alt and for a
    shortcut with a punctuation key.
  - The manual check types "ą", "ł", and "€" with AltGr (Polish layout)
    in a player name and a comment. The letters show, and no shortcut
    acts.
  - The manual check with the Russian and the Greek layouts is in
    `qa/windows/ui-smoke.md`, and it passes.
- Tests: `KeyboardShortcutScanTest` (the main sources, and a negative test
  with each bad shortcut).
- Notes:
  - On 2026-10-20 all shortcuts bind key codes (`AppShortcuts`,
    `KeyStroke.getKeyStroke("Q")`). No code reads the typed character, and
    no shortcut uses Ctrl+Alt or a punctuation key.
  - On 2026-10-28 the scan test is done. It reads the first text argument
    of `getKeyStroke`, `AppShortcut`, `bind`, and `bindStepKey`, and the
    `KeyEvent.VK_` codes and the modifier masks. A text that is not a key
    stroke also fails, so a wrong key name does not hide a shortcut.
  - Step 7 of `qa/windows/ui-smoke.md` is the manual check (the Russian,
    the Greek, and the Polish layouts). The report of `Run-UiSmoke.ps1` has
    its row.
  - Open: the manual check.

### E7-T4 Font check for each language

- Status: done
- Plan: 6 ("Fonts")
- Value: no text of a shipped language shows empty boxes or a different
  font in the UI or in the video.
- Requirements:
  - Each scoreboard font can show all letters of the video texts of each
    shipped language.
  - The UI font can show all letters of the UI texts of each shipped
    language.
  - If B-13 bundled open fonts, the check uses these fonts.
  - The check uses all letters of the language (the CLDR exemplar
    characters), not only the letters of the current texts. Thus player
    names in the language also show. Examples of gaps in old fonts: the
    Romanian "ș" and "ț" with a comma below (U+0219, U+021B), and the
    Vietnamese letters with two accents.
  - The check uses the letters in the composed form (NFC) and in the
    decomposed form (NFD), because a user can type both.
- Acceptance:
  - A font test passes for each shipped language. A font that cannot show
    a language is not in the list of styles for this language.
  - On Windows, the "Ink Free" style shows Cyrillic and Greek letters, or
    the style is not available for these languages.
- Tests: `ScoreboardFontCoverageTest` (NFC, and NFD after the
  composition), `DecomposedTextTest`, `DrawnTextTest`.
- Notes:
  - On 2026-10-20 all scoreboard fonts show all Russian and Spanish
    letters in NFC, also "Ink Free". Arial Black, Georgia, Trebuchet MS,
    and Ink Free have no combining accents (U+0301, U+0303, U+0306,
    U+0308), so a typed "е" + U+0308 showed a box in these styles.
  - Fixed on 2026-10-28: the video draws each text in the composed form
    (`DrawnText`). The ASS texts of the scoreboard, the comments, the
    replay badge, and the preview overlay use `DrawnText.ass`. The font
    choice and the text width (`ScoreboardFonts`), the player names
    (`ScoreboardComponent.display`), the scoreboard images of the UI, and
    the thumbnail texts also use the composed form. The length limit of a
    name now counts the letters, not the combining marks.
  - A letter with a mark that has no composed form (for example a
    Cyrillic vowel with a stress mark, U+0301) still needs the combining
    mark. The four fonts above show a box for it. No CLDR letter of a
    shipped language has this problem.
  - No shipped language uses Greek, so the Greek check is not necessary
    now. Do it with the first Greek language.

### E7-L-ru Russian

- Status: in-progress
- Plan: 13, and the process of E4 (`CONTRIBUTING.md`)
- Value: the users of Russian can use the app and share videos in Russian.
- Requirements: the template below.
- Acceptance: the template below.
- Tests: `MessageCatalogTest` ("the Russian catalog has texts in Russian"
  and the checks of all languages), `LanguageSettingsTest` ("Russian of all
  countries gives Russian"), `EffectiveExpiryTest` ("the moment shows in
  Russian with the genitive month and the year mark").
- Notes:
  - Done on 2026-10-20: the machine draft of `messages_ru.properties` and
    `help_ru.properties`, the glossary, the detection, the plural rule, and
    the expiry date format ("29 марта 2027 г., 16:00"). The language list of
    E2-T1 already has `ru`.
  - The sidebar is wider when a label does not fit (`UiStyles.sidebarWidth`).
    "Розыгрыши" needs 91 px. English and Spanish keep 81 px.
  - The JDK and FlatLaf have no Russian texts. The app uses no Swing
    dialog with a JDK text (no `JOptionPane`, no `JFileChooser`), and the
    file dialog is the Windows dialog. Thus no English Swing text shows.
  - `ScoreboardPreviewMain --language=ru` shows the video texts of all
    styles. They fit.
  - Open: the Windows smoke test in Russian, and the open parts of E7-T3 and E7-T4.

### E7-L-pt Portuguese (Brazil and Portugal)

- Status: in-progress
- Plan: "Decisions" ("Portuguese variants", "Form of address"), 13, and
  the process of E4 (`CONTRIBUTING.md`)
- Value: the users in Brazil and Portugal can use the app and share videos
  in Portuguese. Padel is very popular in Portugal.
- Requirements:
  - The template below, for each variant.
  - Two app languages, as Spanish (E4): `pt` (Brazil) has all texts.
    `pt-PT` has only the texts that are different. All other texts come
    from `pt`.
  - The detection gives Brazil for `pt-BR` and `pt`, and Portugal for
    `pt-PT` and all other Portuguese locales.
  - A native review is optional. If reviewers come forward later, one
    from Brazil reviews `pt`, and one from Portugal reviews `pt-PT` and
    the `pt` texts that `pt-PT` uses.
  - The glossary has one Portuguese section with a column for each
    variant, as the Spanish section.
  - The `pt-PT` plural rule (E7-T2).
- Acceptance:
  - The template below, for each variant.
  - A test gives Brazil for `pt-BR` and Portugal for `pt-PT` and `pt-AO`.
  - A plural test formats 0 and 1.5 in `pt` (`one`) and in `pt-PT`
    (`other`).
- Tests: `MessageCatalogTest` ("the Portuguese catalogs have texts of
  Brazil and of Portugal", "Portuguese of Portugal has no words of Brazil",
  and the checks of all languages), `LanguageSettingsTest` ("Portuguese of
  Brazil gives Brazil and all other Portuguese gives Portugal"),
  `MessagePatternTest` ("the Portuguese plural of Brazil and of Portugal
  differ for 0 and decimals"), `EffectiveExpiryTest` ("the moment shows in
  Portuguese with the month in lower case"), `ScoreboardFontCoverageTest`.
- Notes:
  - Done on 2026-10-20: the machine draft of `messages_pt.properties` and
    `help_pt.properties`, the `pt-PT` files, the glossary, the detection,
    the plural rules, and the expiry date format ("29 de março de 2027,
    16:00"). The language list of E2-T1 already has `pt`.
  - `PluralRules` has a rule for `pt` (Brazil) and uses the English rule
    for `pt-PT`. It is the first rule that depends on the variant.
  - The `pt-PT` files come from a word list (the glossary, "Words of each
    variant") and a manual check of each changed text. They have 318
    message texts and 140 help texts.
  - The sidebar uses "Cor e enquadr." on two lines. The full word
    "enquadramento" needs 98 px and makes the sidebar 114 px wide.
  - `ScoreboardPreviewMain --language=pt` shows the video texts of all
    styles. They fit.
  - Open: the Windows smoke test in both variants, and the open parts of E7-T3
    and E7-T4.

### E7-L-fr French

- Status: in-progress
- Plan: "Decisions" ("Form of address"), 13, and the process of E4
  (`CONTRIBUTING.md`)
- Value: the users in France, Belgium, Switzerland, and Canada can use the
  app and share videos in French.
- Requirements:
  - The template below.
  - One variant for all countries. The author checks that no text uses a
    word that only France uses, if a common word exists.
  - French typography: a no-break space before ":", ";", "?", and "!", and
    inside « … ». A normal space can break the line before the mark.
  - The French plural rule (E7-T2).
  - The AZERTY layout: the shortcuts use key codes (plan section 8). The
    help and the hints name the key letters of the shortcuts, so check that
    the names are correct on AZERTY.
- Acceptance:
  - The template below.
  - A catalog test fails for a French text with a normal space before
    ":", "?", or "!", or inside « … ».
  - A test gives French for `fr-FR`, `fr-BE`, `fr-CH`, and `fr-CA`.
- Tests: `MessageCatalogTest` ("the French catalog has texts in French",
  "French texts use the French spacing and apostrophe", "the French spacing
  check finds a normal space, a missing space, and a straight apostrophe",
  and the checks of all languages), `LanguageSettingsTest` ("French of all
  countries gives French"), `MessagePatternTest` ("a French plural uses one
  for 0 and for decimals below 2"), `EffectiveExpiryTest` ("the moment
  shows in French with the month in lower case"),
  `ScoreboardFontCoverageTest`.
- Notes:
  - Done on 2026-10-20: the machine draft of `messages_fr.properties` and
    `help_fr.properties`, the glossary, the detection, the plural rule, and
    the expiry date format ("29 mars 2027 à 16:00"). The language list of
    E2-T1 already has `fr`.
  - The catalog uses U+00A0 for all French no-break spaces, also before
    ";", "?", and "!" (not the narrow U+202F). All scoreboard fonts show
    U+00A0.
  - The texts use the typographic apostrophe (’). A straight apostrophe
    before a placeholder starts an ICU quote.
  - AZERTY: the shortcuts bind the Windows virtual key codes. On a Latin
    layout, the code of a letter key is the letter on the key. Thus the
    names Q, W, E, R, S, C, V, and F in the help are correct on AZERTY.
    On AZERTY, the keys Q, W, and E are not side by side (Q and W are at
    the left of the middle row and of the bottom row). The feedback of
    the users tells if this is a problem.
  - `ScoreboardPreviewMain --language=fr` shows the video texts of all
    styles. They fit.
  - The screenshots of 2026-10-28 (`AllWindowsUiFlowIT`): the sidebar has
    its normal width. The cut texts are fixed (see E3-T22): the Stats
    column header shows "DANS LA / VIDÉO" on 2 lines, the change-marker
    hint shows all 3 lines, and the Export footer keeps "fps" on one line.
  - Open: the Windows smoke test in French, and the open parts of E7-T3
    and E7-T4.

### E7-L-de German

- Status: in-progress
- Plan: "Decisions" ("Form of address"), 7, 13, and the process of E4
  (`CONTRIBUTING.md`)
- Value: the users in Germany, Austria, and Switzerland can use the app and
  share videos in German.
- Requirements:
  - The template below.
  - One variant for all countries, with "ß". Swiss German writes "ss", but
    the Swiss users read "ß".
  - The "du" form (plan, "Decisions", "Form of address").
  - German has the longest texts and long compound nouns. `WrapText` and
    the help break a word that is wider than the line (plan section 7).
  - The video texts fit in each scoreboard style
    (`ScoreboardPreviewMain --language=de`).
- Acceptance:
  - The template below.
  - A test wraps a German compound noun that is wider than the line. No
    line is wider than the width.
  - A test gives German for `de-DE`, `de-AT`, and `de-CH`.
- Tests: `MessageCatalogTest` ("the German catalog has texts in German",
  "German texts use the German quotes", and the checks of all languages),
  `LanguageSettingsTest` ("German of all countries gives German"),
  `MessagePatternTest` ("a German plural uses one only for 1"),
  `EffectiveExpiryTest` ("the moment shows in German with the day mark"),
  `WrapTextTest`, `ScoreboardFontCoverageTest`.
- Notes:
  - Done on 2026-10-20: the machine draft of `messages_de.properties` and
    `help_de.properties`, the glossary, the detection, the plural rule
    (as English), and the expiry date format ("29. März 2027, 16:00"). The
    language list of E2-T1 already has `de`.
  - `WrapText` in `ui.commons` (the help and most texts) and `WrapText` of
    the Stats tab break a word that is wider than the line. `WrapText` of
    the Export tab did this already.
  - The video badge "ENTSCHEIDUNGSPUNKT" has 18 letters. The catalog
    comment allows about 16.
  - The screenshots of 2026-10-28 (`AllWindowsUiFlowIT`): the sidebar has
    its normal width. The cut texts are fixed:
    - The Export quality card says "Original" (also in the help). The
      Projects step says "Thumbnail machen". The change-marker hint is
      shorter.
    - The timeline label column grows for "KOMMENTARE" (see E3-T22).
  - Open: the Windows smoke test in German, and the open parts of E7-T3
    and E7-T4.

### Template: E7-L-`<code>` `<Language>`

- Status: open
- Plan: 13, and the process of E4 (`CONTRIBUTING.md`)
- Value: the users of `<Language>` can use the app and share videos in
  their language.
- Requirements:
  - The glossary of the language is in `docs/localization/glossary.md`.
  - The catalog and the help of the language come from a machine draft
    with the glossary and the key comments.
  - A native review is optional (plan, "Decisions", "Translation"). If a
    native speaker who plays tennis or padel comes forward, they review
    the texts from the screenshots (E4-T4).
  - The language code is in the language list of E2-T1. Thus the
    analytics and the feedback accept it without a deployment. If the code
    is not in the list, change the list first, and deploy the workers
    before the app release.
  - The Swing and FlatLaf texts show in the language. If the JDK and
    FlatLaf have no texts for the language, the catalog gives them. No
    Swing text shows in English or in the Windows language.
  - `README.md` and the macOS bundle (B-13) list the language.
  - If the author answers feedback in the language, the feedback form
    tells it (E2-T6).
  - German has the longest texts. For German, check the screenshots with
    care.
- Acceptance:
  - The check of E4-T3 shows no missing key for the language.
  - The fit tests (E3-T19), the font test (E7-T4), and the plural test
    (E7-T2) pass for the language.
  - The Windows smoke test passes in the language.
  - The language shows in the list of More, Settings, and in the video
    language list.
  - The catalog tests find no plural case that the language does not
    have, and no case that it needs and does not have.
  - The detection maps the regional variants of the language to it (for
    example `pt-BR` and `pt-PT`). If the variants need different texts,
    the plan, "Decisions", tells which variant ships and why.
- Tests: —

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks E7-T1 to E7-T4 are done before the first
language of this epic.
