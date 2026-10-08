# B-14 Padel: Changes for Padel Support

This file tells what we must change in the app to support padel at the same
level as tennis. The backlog item is B-14 in `docs/backlog.md`.

## Goal

A padel player can do all the steps that a tennis player can do:

- Create a project from a padel video.
- Mark the points.
- Score the points with the padel rules. The app computes the games, the
  sets, and the tiebreaks.
- Export a video with a scoreboard that shows two teams of two players.
- See the statistics of the match.

## Summary

Padel uses almost the same score system as tennis: 0, 15, 30, 40, games, sets
to 6 games, a tiebreak at 6–6, and a 10-point super tiebreak. Thus the current
scoring engine already scores most padel matches correctly.

The main differences are:

1. Padel is a doubles game. Each side is a team of two players. The app now
   has one name for each side, and the user interface says "Player 1" and
   "Player 2".
2. Padel uses a different deuce rule. Since 2026, the FIP rules use the
   "star point" (see "Padel rules that affect the app"). Before 2026, padel
   used the "golden point". The app has the golden point (it is "No-ad"), but
   it does not have the star point.
3. Some texts, icons, and drawings show tennis only. Examples are the app
   tagline, the help, the tennis court in the stats card preview, and the
   landing site.

The data model, the export, the scoreboard styles, and the statistics need
only small changes. The largest work is the team names in the 16 scoreboard
layouts.

## Padel rules that affect the app

| Rule | Tennis (now) | Padel | Change in the app |
|---|---|---|---|
| Points in a game | 0, 15, 30, 40 | The same | None |
| Deuce | Advantage or No-ad | Star point (FIP, from 2026). Golden point in many clubs and older leagues. Advantage in some clubs. | Add the star point rule |
| Sets | To 6 games, two-game lead | The same | None |
| Set tiebreak | 7 points at 6–6 | The same | None |
| Deciding set | Full set or a 10-point match tiebreak | Full set, or a 10-point "super tiebreak" (common in amateur play) | Only the name "super tiebreak" |
| Players | Singles (doubles is possible) | Always doubles: two teams of two players | Team names and player names |
| Serve order | Changes after each game. In a tiebreak: 1 point, then 2 points each. | The same for the teams. In each team, the two players serve in turns. | Optional: show which player serves |
| Court | Tennis court | 20 m × 10 m with glass walls | New drawing in the stats card preview |
| Social formats | Rare | "Americano" and "Mexicano": a fixed total of points, for example 24 points | Optional: a new match structure |

### The star point

Source: the FIP decision of 28 November 2025, and the 2026 guide of USA
Padel. Check the final text of the FIP Rules of Padel (rule 1, option 2)
before you write the code.

- At the first deuce (40–40), the players play for an advantage, as in tennis.
- If the score goes back to deuce, the players play for an advantage again.
- If the score goes back to deuce a second time (the third deuce), the next
  point wins the game. This point is the "star point". The receiving team
  selects the side of the serve.

In the raw point counts of `ScoringEngine`, deuce is 3–3, 4–4, and 5–5. At
5–5, the next point wins the game. Thus the game ends when one of these
conditions is true:

- A team has 4 or more points and leads by 2 points.
- The sum of the points is 11 or more (the point after 5–5).

## Changes in the code

The changes are in the order of the work. Each change tells the files to
change and the tests to add.

### C1 Sport of a project (data model)

Add a sport to the score data. All other changes read it.

- Add `enum class Sport { TENNIS, PADEL }` in `org.litvin.scoring`. Put
  `@JsonEnumDefaultValue` on `TENNIS`.
- Add `val sport: Sport = Sport.TENNIS` to `ScoreV1`
  (`src/main/kotlin/org/litvin/scoring/ScoreIO.kt`).
- An old `score.json` has no `sport` field. Jackson reads it as `TENNIS`.
  Thus the old projects do not change.
- An old app version ignores the unknown field (`FAIL_ON_UNKNOWN_PROPERTIES`
  is false). Thus an old app can open a padel project. It shows "Player 1"
  and "Player 2" labels, but the score stays correct (see C2 for the star
  point).
- Tests: `ScoreIOTest`. Read an old file without `sport`. Write and read a
  padel file.

Why `score.json` and not `project.trproj`: the rules, the names, and the
scoreboard settings are in `score.json`. The sport changes all of them. A
user can also change the sport of an existing project in the score settings.

### C2 Star point rule (scoring engine)

- Add `STAR_POINT` to `DeuceRule` in
  `src/main/kotlin/org/litvin/scoring/MatchRules.kt`. Write a KDoc comment
  that explains the rule.
- In `ScoringEngine.Match.checkGame()`
  (`src/main/kotlin/org/litvin/scoring/ScoringEngine.kt`), add:

  ```kotlin
  DeuceRule.STAR_POINT -> (top >= 4 && lead >= 2) || p1Pts + p2Pts >= STAR_POINT_TOTAL
  ```

  `STAR_POINT_TOTAL` is 11. The rule needs no new field in `Match`. Thus
  `Match.copy()` and `stakeIfWonBy()` work without changes.
- `stakesOfPoint` then gives a `GAME` stake to both teams at 5–5. Thus the
  break point statistics count the star point correctly.
- `MatchStats.isDeucePoint()` counts 3–3, 4–4, and 5–5 as deuce points. This
  is correct for the star point too.
- An old app version reads `STAR_POINT` as `ADVANTAGE`
  (`READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE`). The score of a long deuce
  game is then different in the old app. Write this in the release notes.
- Tests: `ScoringEngineTest`. Add cases for 40–40 → Ad → 40–40 → Ad → 40–40 →
  game, for a game that ends at the first advantage, and for the stakes and
  the server change at the star point. Add a case to `MatchStatsTest` for
  break points at the star point.

### C3 Deciding point on the scoreboard

The viewer must see when the next point wins the game. This is true for the
golden point (No-ad at 40–40) and the star point.

- A deciding point is a point that is not in a tiebreak, where both teams
  have a stake of `GAME` or more. `ScoringEngine.Timeline.stakesOfPoint`
  already has this data.
- Add `val decidingPoint: Boolean = false` to `OverlaySpan`
  (`src/main/kotlin/org/litvin/ScoreboardTimeline.kt`) and to
  `ScoreboardDisplay` (`src/main/kotlin/org/litvin/ScoreboardDisplay.kt`).
  Set it in `ScoreboardTimelineBuilder`.
- In the scoreboard layouts, show a small mark next to "40", for example a
  star "★" for the star point and "GP" for the golden point. Use the same
  place as the serve mark. Do it in `ScoreboardLayoutParts.kt` so that all
  16 styles get it.
- Show the same mark in the Scoring tab (`ScorePanel.kt`,
  `ScoringPointsList.kt`).
- Tests: `ScoreboardTimelineBuilderTest`, `ScoreboardAssTest`,
  `ScoreboardLayoutsTest`.

This change helps tennis too (No-ad).

### C4 Teams and player names

Padel has four players. A padel scoreboard shows the two names of each team,
for example "LEBRÓN / GALÁN".

Data:

- Add `player1PartnerName: String = ""` and `player2PartnerName: String = ""`
  to `ScoreV1`. The existing `player1Name` and `player2Name` stay. They are
  the first player of each team. Thus old files and old app versions still
  work.
- Add one function that makes the side name, for example
  `ScoreV1.sideName(side: Int): String`. For tennis it returns the name. For
  padel it returns "NAME / PARTNER", or one name when the partner name is
  empty. Use this function in all places that read the names now:
  - `ScoreboardTimeline.kt` (the spans)
  - `ExportPlanner.kt` (export)
  - `StatsCard.kt` (the stats card in the video)
  - `SwingStatsPanel.kt` (the Stats tab)
  - `SwingScoringPanel.kt` (the Scoring tab)

Scoreboard:

- `ScoreboardComponent.PLAYER_NAME_MAX_CHARS` is 20. Two surnames and " / "
  need more. Shorten each name separately, for example to 12 characters, and
  join them. Do not cut the second name only.
- The layouts measure the name width (`nameWidth()` in
  `ScoreboardLayoutParts.kt`). A longer name makes the board wider. Check all
  16 styles with long team names. Make a decision about each style: one line
  ("LEBRÓN / GALÁN"), or two lines (one name on each line, as on TV). Two
  lines need more height in each row. Start with one line. Add two lines
  later if users ask for it.
- Tests: `ScoreboardLayoutsTest` and `ScoreboardComponentTest` with long
  team names. Look at the export images of all styles.

Score settings dialog (`ScoreSettingsDialog.kt`):

- For padel, show two name fields for each team: "Team 1" with "Player A"
  and "Player B", and the same for "Team 2". Keep one color for each team.
- Add new component names for the new fields. Update
  `ComponentNameContractTest` and the screen objects in
  `src/test/kotlin/org/litvin/ui/flow/screens/ScoringScreen.kt`.

### C5 Words in the user interface

The user interface says "Player 1", "Player 2", and "player" in about 25
places. For padel, it must say "Team 1", "Team 2", and "team".

- Add one source of these words, for example
  `enum class SideTerm(val one: String, val many: String)` or a function
  `Sport.sideLabel(side: Int)`. Do not copy `if (padel)` checks into each
  panel.
- Change the labels in these files:
  - `ui/tabs/scoring/SwingScoringPanel.kt`
  - `ui/tabs/scoring/ui/ScorePanel.kt`, `ScoringPointsList.kt`,
    `ScoringPlaybackBar.kt`, `ScoreSettingsDialog.kt`,
    `ScoreboardSettingsDialog.kt`
  - `ui/tabs/stats/SwingStatsPanel.kt`, `StatsTable.kt`, `MomentumChart.kt`,
    `StatsUi.kt`
  - `stats/StatsCard.kt`, `ScoreboardDisplay.kt` (the default names)
- Statistic names stay the same. "Break points", "Service games", and
  "Deuce points" are also padel terms.
- The deuce choice in the score settings has three options:
  - "Advantage": win by 2 points.
  - "Golden point" for padel, "No-ad" for tennis: the next point at 40–40
    wins.
  - "Star point": two advantages, then the next point wins.
- Rename "Match tiebreak" to "Super tiebreak" for padel. The rule is the
  same.

### C6 Padel match formats

`MatchFormatPreset` (`MatchRules.kt`) has tennis presets only, for example
"Best of 5 sets (Grand Slam)". Padel needs its own list.

- Give each preset a sport, or make a separate list for padel. The score
  settings show only the presets of the project sport, and "Custom".
- Padel presets:

  | Preset | Rules |
  |---|---|
  | Best of 3 sets | Sets to 6 games, tiebreak at 6–6 |
  | Best of 3 sets, super tiebreak | Sets to 6 games. At one set all, a 10-point super tiebreak |
  | One set | One set to 6 games, tiebreak at 6–6 |
  | Games only | Count games without sets |
  | Super tiebreak (10 points) | One tiebreak to 10 points |
  | Plain points | Count points without a limit |

- `MatchFormatPreset.applyTo()` keeps the deuce rule of the user. For a new
  padel project, the default deuce rule is `STAR_POINT`. For a new tennis
  project, it stays `ADVANTAGE`.
- `MatchFormatPreset.of()` must find the preset in the list of the project
  sport.
- Tests: `MatchRulesTest`. Each padel preset maps back to itself.

### C7 Select the sport

- New project dialog (`ui/tabs/projects/components/NewProjectDialog.kt`):
  add a choice "Tennis" or "Padel". Store the choice as a user preference.
  Most users play one sport, so the next project uses the same sport.
- Score settings dialog: show the sport at the top. A change of the sport
  changes the presets, the default deuce rule, and the name fields. It does
  not change the scored points.
- Projects list (`ProjectRow.kt`): optional. Show a small sport icon.
- The Scoring tab shows the score settings one time for each project
  (`scoreSettingsReviewed`). Thus the user sees the sport before the first
  score.

### C8 Statistics

- The statistics work for padel without changes in `MatchStats.kt`.
- Point length: the defaults are "short: 10 s or less" and "long: 13 s or
  more" (`StatsSettingsV1`). Padel points are usually longer than tennis
  points. Use different defaults for padel. Measure them with real padel
  videos before the release.
- Optional: add "Star points won" (a new `MatchStat` key in
  `stats/StatRows.kt`). Do not change the existing keys. They are stored in
  `stats.json`.
- `StatsCardPreview.kt` draws a tennis court in place of the video frame.
  Draw a padel court for padel: no doubles lines, the service lines near the
  net, and the glass walls at the ends.

### C9 Scoreboard look (optional)

- The 16 styles work for padel. Some names are tennis names ("Grass Court",
  "Clay Court", "Hard Court"). Keep them.
- Optional: add one padel style, for example "Padel Glass" (blue and white).
- The serve mark is a ball. A padel ball looks like a tennis ball. No change
  is necessary.

### C10 Server inside a team (optional)

- Now the app knows which team serves. It does not know which player of the
  team serves.
- In padel, the four players serve in a fixed order: A1, B1, A2, B2. The
  order can change only at the start of a set.
- To show the name of the server, the app must know the first server of each
  team in each set. Add a "server player" mark, as the existing server mark.
  The engine then computes the rotation.
- This is optional for the first padel version. The team serve mark is
  enough for the score and the statistics.

### C11 Americano and Mexicano (optional)

- Many social padel games use a fixed total of points, for example 24 or 32
  points. The match ends when the sum of the points of the two teams gets to
  the total.
- The app can count these games now with "Plain points", but the match has
  no end.
- Optional: add a `MatchStructure.POINTS_TOTAL` with a total, for example
  16, 21, 24, or 32. The match ends at the total. Add it to `canonical()`,
  the presets, and the score settings.

### C12 App texts, help, and icons

- `AppInfo.TAGLINE` is "Tennis Video Editor". Change it to "Tennis and Padel
  Video Editor", or a shorter text.
- `ui/help/HelpCatalog.kt`: the overview says "a full tennis recording".
  Change the text, and add the padel rules (star point, golden point, team
  names) to the Scoring help page.
- The help icon of the Points page and the Points tab use
  `Material2MZ.SPORTS_TENNIS`. Ikonli has no padel icon. Keep the tennis
  icon, or use a neutral icon, for example a ball.
- `AnimatedAppMark.kt` is a tennis ball. Keep it. It is the app mark.

### C13 Analytics (optional)

- Add counters to see how many users score padel, for example
  `score_recorded_padel`, or `project_created_tennis` and
  `project_created_padel`.
- The counter keys are a closed list in three places: the app
  (`analytics/AnalyticsSchema.kt`), the contract
  (`analytics-contract/v1/counter-keys.json`), and the server
  (`analytics-worker`). Change all three and the cockpit. Follow
  `docs/analytics/design.md`. Update the privacy notice if the text lists
  the counters.

### C14 Site, README, and marketing

- `site/public/index.html`, `download/`, and `faq/`: they say "tennis" in the
  title, the description, and the text. Add padel only after the release
  with padel support. `docs/marketing/strategy.md` tells why: "Do not
  promote to these groups before the app supports their score rules."
- Add padel screenshots and one padel FAQ entry (star point, team names).
- `README.md`: change the first line and the "Scoring" feature.
- Spanish is important for padel (B-11). Do the padel release first, then
  Spanish.

## Code that needs no change

- Points tab, Colors tab, Crop and Rotate tab: they do not use the sport.
- Export pipeline (`FFmpegCommandBuilder`, `RenderService`, the chunk and
  pass planners): they use the scoreboard images and do not know the sport.
- `ScoringEngine`: only `checkGame()` changes (C2). Sets, tiebreaks, the
  super tiebreak, and the serve rotation of the teams are the same as in
  tennis.
- `MatchStats`: the statistics are correct for teams.
- `RulesEngine.kt`: an old tennis engine that only tests use. Do not change
  it.

## Order of the work and size

| Step | Changes | Size | Required for the first padel version |
|---|---|---|---|
| 1 | C1 sport, C2 star point | Small | Yes |
| 2 | C5 words, C6 presets, C7 select the sport | Medium | Yes |
| 3 | C4 team names in the data, the dialog, and the 16 layouts | Medium to large | Yes |
| 4 | C3 deciding point mark | Small to medium | Yes |
| 5 | C8 statistics defaults and the court drawing, C12 texts | Small | Yes |
| 6 | C14 site and README | Small | Yes, at the release |
| 7 | C9 padel style, C10 server player, C11 Americano, C13 analytics | Medium | No |

## Tests

- Unit tests: `ScoreIOTest`, `ScoringEngineTest`, `MatchRulesTest`,
  `MatchStatsTest`, `ScoreboardTimelineBuilderTest`, `ScoreboardLayoutsTest`,
  `ScoreboardAssTest`, `ScoreboardComponentTest`, `StatsCardTest`.
- UI tests: `SwingScoringPanelScoreSettingsTest`, `ScorePanelTest`,
  `ComponentNameContractTest`, and a new UI flow test: create a padel
  project, set four names, score a star point game, and export.
- Golden files: add a padel project to `src/test/resources/golden/`.
- Manual check: export one padel match with all 16 scoreboard styles, with
  long team names. Look at each style in the preview and in the exported
  video.
- Compatibility check: open a padel project in version 1.0.0. The app must
  open it without an error.

## Open questions

These questions need a decision before the related change starts.

| # | Change | Question |
|---|---|---|
| Q1 | C1 | Keep the sport in `score.json` (recommended), or in `project.trproj`? |
| Q2 | C4 | Team names on the scoreboard: one line ("A / B"), or two lines? |
| Q3 | C5 | Show the star point option for tennis too, or only for padel? |
| Q4 | C6 | The default deuce rule for a new padel project: star point (FIP 2026), or golden point (common in clubs)? |
| Q5 | C11 | Is Americano in the first padel version? |
| Q6 | C12 | The new tagline and the app description. |
| Q7 | C8 | The point length defaults for padel. |

## Sources

- FIP: [Premier Padel and the International Padel Federation unveil the 2026 calendar and the new star point system](https://www.padelfip.com/2025/12/premier-padel-and-international-padel-federation-unveil-2026-qatar-airways-premier-padel-tour-calendar-and-innovative-new-star-point-system-launched)
- Padel Addict: [What is the star point](https://www.padeladdict.com/en/what-is-the-star-point-this-is-premier-padels-new-scoring-system/)
- USA Padel: [2026 Competition Structure Guide](https://padelusa.org/wp-content/uploads/2026/06/2026-COMPETITION-STRUCTURE-GUIDE-JUN-30-2026.pdf)
