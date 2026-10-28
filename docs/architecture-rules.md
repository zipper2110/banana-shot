# Architecture Rules

These rules define packaging and dependency conventions to keep the codebase consistent and maintainable. All new work must follow them; existing code should be migrated opportunistically.

## Package topology (feature‑centric)
Top‑level feature and shared packages:
- `org.litvin.app` — application entry/wiring/bootstrap only (no business logic).
- `org.litvin.media` — media adapters and low‑level media services.
  - `org.litvin.media.mpv` — the libmpv preview engine (JNA bindings, core, shader/overlay plumbing).
- `org.litvin.projects` — project/session metadata and manifest handling.
- `org.litvin.adjustments` — color/geometry adjustments domain and persistence.
- `org.litvin.points` — points/EDL domain and services.
- `org.litvin.scoring` — scoring rules/engine, score timelines, score IO.
- `org.litvin.export` — export/pipeline/FFmpeg/queue/overlay writing.
- `org.litvin.analytics` — opt‑in usage analytics (B-9): the events, the session counters, the summary transport, consent state.
- `org.litvin.feedback` — feedback reports (B-8): the report and its JSON, the log data, the sender, the endpoint setting.
- `org.litvin.shared.util` — cross‑cutting utilities and primitives (timecode, debouncers, pagination, OS tweaks, etc.).
- `org.litvin.i18n` — the texts of the app languages and the formats (B-11): `I18n` and `tr`, the message catalog, `DisplayFormats`, and `MachineFormats`.
- `org.litvin.ui` — UI layer only.
  - `org.litvin.ui.tabs.{projects,points,stats,adjustments,export}` — tab UIs per feature (`adjustments` is the Colors & Transform tab with the Colors and Transform sub-tabs) (plus `test`, a diagnostics tab wired in only when the test flag is enabled).
  - `org.litvin.ui.commons` — shared UI widgets/components/styles with no tab dependency.
  - `org.litvin.ui.help` — help catalog and dialog.
  - `org.litvin.ui.privacy` — analytics consent dialog and the Privacy page of the More window.
  - `org.litvin.ui.more` — the More window and its Settings and About pages.
  - `org.litvin.ui.feedback` — the feedback form (MVP: the presenter and its contracts are in `.presenter`).

Subpackage conventions inside a tab (`org.litvin.ui.tabs.<feature>`):
- `.presenter` — the presenter and its UI contracts (see MVP below).
- `.ui` / `.components` — widgets that belong to that tab only.

Directory layout must mirror the declared package. A file whose `package` line does not match its directory is migration debt, not a pattern to copy.

## Dependency rules
Allowed directions:
- `app` → `ui`, `projects`, feature packages, `analytics`, `shared.util`, `media`.
- `ui.tabs.*` → the corresponding feature package(s), `projects`, `export`, `media`, `shared.util`, and `ui.commons`.
- `ui.commons` → `shared.util` only (must not depend on any specific tab or feature).
- `ui.privacy` → `analytics` (the consent/privacy dialogs are the only UI that uses more than the analytics facade); `ui.help` → `shared.util` and `ui.commons`; `ui.more` → `ui.commons` only (`app` gives it the pages of other packages as `MoreSection` items).
- Feature packages may depend on `shared.util` and other leaf services (`media`) but not on `ui` or `app`.
- `media` → `adjustments` is allowed and deliberate: the libmpv preview must apply the same crop/rotate/color transforms the FFmpeg export does, so it consumes the adjustments domain (`GeometryPlan`, `CropRect`, `AdjustmentsV1`) instead of duplicating the math.
- `analytics` must not depend on any other `org.litvin` package — it is a leaf service. `app` gives it the app version and the HTTP client.
- Feature and UI packages can use only `Analytics`, `AnalyticsEvent` (with its nested types), and `DisabledAnalytics`. They get `Analytics` from `app` through the constructor; the default is `DisabledAnalytics`. Only `app` and `ui.privacy` use the rest of `analytics`. A feature that has no constructor from `app` (for example `RenderQueueManager`) gives plain facts to a listener, and `app` changes them into events. `AnalyticsDependencyTest` checks these rules.
- `ui.feedback` → `feedback` and `ui.commons`. `feedback` must not depend on any other `org.litvin` package: `app` gives it the HTTP client with the trust of `license.online`. The other UI packages open the form only through callbacks from `app`.
- `shared.util` must not depend on any feature/ui/app.
- `i18n` is a leaf package: it must not depend on any other `org.litvin` package. All packages can depend on it, also `shared.util`, `ui.commons`, and the leaf services. `ArchitectureDependencyHygieneTest` checks this rule.
- Cycles are forbidden.

## Formats and the locale
The Windows regional format must not change a text that a program reads. A text that the user reads uses the regional format.
- A text for a program (FFmpeg arguments, ASS files, JSON, hex colors, hashes, time codes in a field) uses `MachineFormats` (`Locale.ROOT`) or an explicit `Locale.ROOT` or `Locale.US`. Never use a format call without a locale.
- A number, date, or time that the user reads uses `DisplayFormats`. It uses the Windows regional format with the digits 0–9 and the Gregorian calendar.
- A number that the user types can have "." or ",". Read it with `DisplayFormats.parseDecimal` or `Timecode.parse`. Do not compare or parse a display text: compare the number.
- Do not change the default locale of the JVM (`Locale.setDefault`) in the app. Only tests can change it, and they must set the old value again.
- `DefaultLocaleScanTest` finds format calls without a locale. `DefaultLocaleOutputTest` compares the machine texts under other default locales.

## Texts for the user

All texts that the user can see come from the message catalog (B-11). The
plan is in `docs/localization/b-11-localization.md`. How to add a text is in
`CONTRIBUTING.md`, "Add a text".
- Read a text with `tr("key")`. The key is a string literal. `MessageCatalogTest` finds the keys that the code uses, and fails for a key without an English text or a text without a translation.
- The domain packages (`stats`, `scoring`, `export`, `projects`, `license`) do not make sentences for the user. They return enums, `Text` values, or numbers, or read a single text with `tr` in a getter. The UI or the overlay writer makes the sentence.
- Use one key for a full sentence, with named placeholders. Do not make a sentence from parts: the word order is different in other languages.
- Do not put HTML markup in the catalog if possible. When a text needs bold parts, use named placeholders for these parts.
- Do not save a default text in a file. Save "not set" (an empty or null value), and show the text of the app language. The scoreboard title and the side names do this (`ScoreboardSettingsV1.title`, `ScoreV1.player1Name`). `APP_CREDIT` stays English as the brand text.
- Component names (`name = "more-settings-theme"`) stay in English. Tests find components by name, not by text. Do not make a component name from a display text.
- Code logic never depends on a display text. Do not compare, parse, store, or search a display text. Use an enum, an ID, or a number. Preferences and project files keep codes, not texts.
- The user never sees only the message of an exception. Use `ErrorTexts.of`: it gives a catalog text first, and the English message of the exception as the detail under it.
- Show a caption in capital letters with `DisplayFormats.upper`. It uses the locale of the app language.
- Put a no-break space (U+00A0) between a number and its unit in the catalog ("5\u00A0s", "2,3\u00A0GB"). `MessageCatalogTest` checks it.
- A text from a server or a file (release notes, expiry messages) shows as plain text, never as HTML.
- `HardCodedTextScanTest` finds a text in the code that is not in the catalog. Its allowlist has only brand names, short codes, a file name, and the technical texts of the feedback report.
- Run the app with the test language `qps` (`-Dbananashot.language=qps`) to find a text that is not in the catalog and a text that does not fit.

## Third-party license boundaries
The app uses the Elastic License 2.0. A GPL component must not be part of the app process.
- Start the bundled GPL FFmpeg build only as a separate process (`ffmpeg.exe`, `ffprobe.exe`). Communicate only through command-line arguments, pipes and files.
- Do not load a GPL FFmpeg build into the app process (for example through JNA, JavaCV or bytedeco). Only an LGPL FFmpeg build, as a separate and replaceable library, can load in process.
- Load only an LGPL build of libmpv (`-Dgpl=false`). Keep `libmpv-2.dll` a separate, replaceable file.
- Before you add a dependency, check its license. Apache-2.0, MIT, BSD, EPL and LGPL (as a replaceable library) are compatible. GPL and AGPL are not compatible in process.

## UI architecture: MVP (Passive View)

This project uses MVP (Model–View–Presenter), Passive View flavor, for all Swing UI under `org.litvin.ui.*`.

Goals:
- Separate UI definition from business logic and IO.
- Keep Swing components thin and deterministic to render from immutable state.
- Make behavior testable without Swing.

### Roles and contracts
- View (in `org.litvin.ui…`):
  - Swing components (`JPanel`, dialogs, widgets) implement a small `…View` interface.
  - No business logic or domain computations; only forwards user events and renders state.
  - Required methods:
    - `fun render(state: <Feature>ViewState)` — idempotent; updates the UI from immutable state only. Enable/disable and visibility decisions belong in `ViewState`, not in the View.
    - `fun renderEffect(effect: <Feature>ViewEffect)` — optional, for one‑off UI commands (dialogs, focus, notifications). Keep these one‑shot effects out of the persistent `ViewState`.
- Presenter (in `org.litvin.ui.tabs.<feature>.presenter`):
  - Orchestrates user intents, domain use‑cases/services, and produces `ViewState`.
  - Lifecycle aware.
  - Required methods:
    - `fun attach(view: <Feature>View)` / `fun detach()`
    - `fun onIntent(intent: <Feature>Intent)`
    - `fun onActivated()` / `fun onDeactivated()` (hooked to tab lifecycle)
- Model/Domain (in feature packages e.g., `org.litvin.scoring`, `org.litvin.points`):
  - Entities, pure functions, repositories, and services. No Swing/UI dependencies.

### Data flow (unidirectional)
`User/Event` → `View` forwards `Intent` → `Presenter` runs use‑cases → computes new immutable `ViewState` → `View.render(state)`.

### Threading/EDT rules
- Never block the EDT. Long‑running work is initiated by the Presenter off‑EDT.
- View updates happen on the EDT (`EventQueue.invokeLater { … }`). Presenters ensure this when calling `view.render(…)`.
- Domain/services remain UI‑agnostic; they do not touch Swing.

### Package placement and dependencies
- Views: `org.litvin.ui.tabs.<feature>` and subpackages.
- Presenters and UI contracts: `org.litvin.ui.tabs.<feature>.presenter`.
- Domain/services: the corresponding feature packages (`org.litvin.scoring`, `org.litvin.points`, etc.).
- Dependency direction: View → Presenter(contract) → Domain/Services. Domain must not depend on UI.

### Naming conventions
For a feature `Foo` (e.g., Scoring, Points):
- Interfaces/classes:
  - `FooView`, `FooPresenter`, `FooViewState`, `FooIntent`, `FooViewEffect` (optional).
- Presenter methods:
  - `attach`, `detach`, `onIntent`, `onActivated`, `onDeactivated`.
- State is immutable (`data class FooViewState(…)`). Avoid mutable UI‑driven state outside this object.

### Reference implementation
`projects` is the worked example; `adjustments` is the same shape in miniature. Read those rather than a sketch:
- Contracts — [`ProjectsContracts.kt`](../src/main/kotlin/org/litvin/ui/tabs/projects/presenter/ProjectsContracts.kt), [`AdjustVideoContracts.kt`](../src/main/kotlin/org/litvin/ui/tabs/adjustments/presenter/AdjustVideoContracts.kt)
- Presenter — [`DefaultProjectsPresenter.kt`](../src/main/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenter.kt)
- View wiring (`attach`/`detach` from `addNotify`/`removeNotify`) — [`SwingProjectsPanel.kt`](../src/main/kotlin/org/litvin/ui/tabs/projects/SwingProjectsPanel.kt)
- Presenter test against a fake view — [`DefaultProjectsPresenterTest.kt`](../src/test/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenterTest.kt)

## Project names and folders
- Decision (B-36, 2026-10-06): the project name and the project folder name are different. The manifest keeps the name that the user typed. The name can have any characters. It must not be empty, and it must have a maximum of 80 characters (`NewProjectRules.nameError`).
- `NewProjectRules.folderName` makes a correct Windows file name from the name (`SafeFileName`). It replaces `< > : " / \ | ? *` and control characters with `_`, removes the periods and spaces at the end, and adds `_` to a reserved name such as `CON`. For example, "Who won?" gives the folder `Who won_`. The manifest file in the folder has the folder name and the extension `.trproj`.
- The app does not get the project name from the folder name. It reads the name from the manifest. The folder name is only a fallback when the manifest has no name.
- A new project gets a suffix such as " (2)" when a project with the same name exists (the comparison ignores case). The folder gets a suffix when the folder exists. Thus, "Who won?" and "Who won*" get the folders `Who won_` and `Who won_ (2)`.
- A rename changes only the manifest. The folder keeps its name. Projects from before B-36 have a folder name that is the same as the name. They open with no change.
- The export file name also uses `SafeFileName` on the project name.

## UI component conventions
- Encapsulate business logic or visual style in focused components with clear APIs.
- Prefer extracting repeated or cohesive UI widgets into separate component classes instead of keeping them as builder methods inside a tab panel.
- Get all UI colors from the tokens of `Palette` (`ui/commons/Palette.kt`). `ArchitectureDependencyHygieneTest` finds color values in other UI files.
- A theme change applies at once (B-12). The Palette tokens are live colors: a component can keep a token, and it paints with the new value after a theme change. Do not copy the value of a token into a new `Color`, a cached image or a string.
- Make a FlatLaf style string with `Theme.themedStyle`, so that a theme change makes the string again.
- `LIME` is for lime marks on a surface (icons, text, lines). `LIME_FILL` is for a lime fill with `ON_LIME` content (primary buttons, the play button, a checked box). On the Light theme, `LIME` is darker than `LIME_FILL`.
- Favor composition over inheritance; keep components testable.
- Prefer immutable value objects for inputs/outputs where practical.

## Naming
- Names must reflect intent and feature (e.g., `FFmpegCommandBuilder`, `RenderQueue`, `ScoringEngine`).
- Avoid ambiguous names like `Utils`/`Helper`; prefer precise nouns.

## Testing
- Pure logic (e.g., rules engine, timecode, geometry math) belongs in feature or `shared.util` and should have unit tests.
- Presenter tests: send `Intent`s, assert emitted `ViewState`/`Effect`s. No Swing involved.
- Domain tests: verify pure logic in `org.litvin.<feature>` packages.
- End‑to‑end Swing behavior belongs in the UI‑flow ITs under `org.litvin.ui.flow`, which drive the real shell through a headless harness. Use them for cross‑tab wiring, not for logic a presenter test can cover.

## File size and structure
- Kotlin source files longer than 500 lines are discouraged.
- If a file exceeds 500 lines:
  - Prefer splitting it into smaller, focused components (classes, files, or top‑level functions) that align with the feature/package boundaries above.
  - If keeping it as a single file is deliberate, add a clear file‑level KDoc at the top that explains why this size is preferred and what trade‑offs were considered.
- This is a guideline, not a hard limit; exceptions must be justified as above.

## Migration guidance
- When touching legacy code in `org.litvin` that mixes concerns, rehome it into the appropriate feature package or `shared.util`/`ui.commons`.

Known debt, to be paid down opportunistically rather than in a single sweep:
- The root `org.litvin` package still holds ~20 classes that belong in feature packages — FFmpeg/render/overlay pieces (`export`), scoreboard styling and timeline (`scoring`), `Pagination`/`JsonFileIO`/`SessionSettings` (`shared.util`), `GeometryViewportPanel` (`ui`).
- Several files already declare a feature package while still sitting in the `org/litvin/` directory (e.g. `Timecode.kt`, `EdlIO.kt`, `ScoreIO.kt`, `PointsDispatcher.kt`). Move the file when you next touch it.
- Only `projects` and `adjustments` are on MVP. `points` uses an older container pattern — action interfaces (`PointsActions`) plus a `ViewState` snapshot pushed to leaf components, with no presenter — and its contracts sit in the tab package rather than `.presenter`. `export` has no contracts at all. New tabs follow the `projects`/`adjustments` shape.
- `RulesEngine` is superseded by `scoring.ScoringEngine` and is now referenced only from tests; fold the remaining coverage into `ScoringEngineTest` and delete it.

### Migration checklist (per tab)
1) Introduce `…View`, `…Presenter`, `…ViewState`, `…Intent` contracts.
2) Move logic from Swing components into the Presenter, step by step.
3) Extract reusable computations to the corresponding feature packages.
4) Ensure `render(state)` is the only way the View changes persistent UI state.
5) Preserve threading rules: heavy work off‑EDT; rendering on EDT.
