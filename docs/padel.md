# Padel

This file has two parts:

- "Baseline": what the app does for padel in the first release with padel
  support. B-14 made this baseline.
- "Improvements": the open tasks of B-45 (`backlog.md`). Each task starts
  from the baseline.

## Baseline

### Goal

A padel player can do all the steps that a tennis player can do:

- Create a project from a padel video.
- Mark the points.
- Score the points with the padel rules. The app computes the games, the
  sets, and the tiebreaks.
- Export a video with a scoreboard that shows two teams.
- See the statistics of the match.

The baseline does all these steps.

### Padel rules that affect the app

| Rule | Tennis | Padel | In the app |
|---|---|---|---|
| Points in a game | 0, 15, 30, 40 | The same | The same engine |
| Deuce | Advantage or no-ad | Star point (FIP, from 2026). Golden point in many clubs and older leagues. Advantage in some clubs. | Advantage, golden point, star point |
| Sets | To 6 games, two-game lead | The same. Some leagues play one pro set to 9 games with a tiebreak at 8–8 (FFT formats D1 and D2). | 4, 6, 8, or 9 games. For 9 games, the tiebreak at 8–8 (`earlyTiebreak`). |
| Set tiebreak | 7 points at 6–6 | The same | The same engine |
| Deciding set | Full set or a 10-point match tiebreak | Full set, or a 10-point "super tiebreak" | The padel name is "super tiebreak" |
| Players | Singles (doubles is possible) | Always doubles: two teams of two players | One name field for each team |
| Serve order | Changes after each game. In a tiebreak: 1 point, then 2 points each. | The same for the teams. In each team, the two players serve in turns. | The serving team only (P-1) |
| Court | Tennis court | 20 m × 10 m with glass walls | A padel court drawing in the Stats tab |
| Social formats | Rare | "Americano" and "Mexicano": a fixed total of points, for example 24 points | Americano (`TOTAL_POINTS`). Events are B-43. |

#### The star point

Source: the FIP decision of 28 November 2025, and the 2026 guide of USA
Padel (see "Sources").

- At the first deuce (40–40), the players play for an advantage, as in tennis.
- If the score goes back to deuce, the players play for an advantage again.
- If the score goes back to deuce a second time (the third deuce), the next
  point wins the game. This point is the "star point". The receiving team
  selects the side of the serve.

In the raw point counts of `ScoringEngine`, deuce is 3–3, 4–4, and 5–5. A
star point game ends when one of these conditions is true:

- A team has 4 or more points and leads by 2 points.
- The sum of the points is 11 or more (the point after 5–5).

### What the app does

Projects:

- The "New project" dialog has a Tennis / Padel switch. For padel, the
  dialog also shows the padel rules: the format, the deuce rule, the total
  points of an Americano match, and the serve turn.
- The next new project starts with the sport of the last new project. A
  padel project also starts with the rules of the last padel project
  (preferences `lastSport` and `lastPadelRules` of the Projects node).
- A padel project gets `score.json` when the project is created. The file
  has `sport: PADEL`, the rules, and the names "Team 1" and "Team 2". The
  flag `useDefaultScoreboard` makes the Scoring tab apply the default
  scoreboard style, as for a project without `score.json`. The Scoring tab
  still opens the scoring settings on the first visit, so the user can type
  the team names.
- The Projects list has a Sport column. A project without `score.json` is
  tennis.

Scoring:

- The scoring settings have a Sport row. A change of the sport resets the
  rules to the defaults of the new sport and changes a default side name
  ("Player 1" to "Team 1"). Padel shows its own formats and words: "super
  tiebreak" for "match tiebreak", "golden point" for "no-ad".
- Deuce rules: advantage, golden point (`NO_AD`), and star point
  (`STAR_POINT`). Only padel offers the star point (`Sport.deuceRules`).
- Americano: the structure `TOTAL_POINTS`. Points count 1, 2, 3 until both
  teams together played the total (16, 21, 24, or 32). The team with more
  points wins. Equal points are a draw. Each team serves 2 or 4 points in a
  row. The points after the end of the match do not count.
- Pro set to 9 games: the padel format "Pro set (9 games)". The set ends at
  9 games, for example 9–7, or 9–8 after a tiebreak at 8–8. The rule
  `earlyTiebreak` moves the set tiebreak one game earlier. "Games in a set"
  has 9 games for tennis and padel. With 9 games, "Set tiebreak" offers
  "Tiebreak at 8–8", "Tiebreak at 9–9", and "No tiebreak". "No tiebreak" is
  the other league form: a two-game lead after 8–8.
- When the selected point is a deciding point, the score panel shows a badge
  at the right of "Who won the point?".
- Team names: the scoring settings and the scoreboard accept 24 characters
  (`ScoreboardComponent.PLAYER_NAME_MAX_CHARS`).

Scoreboard and export:

- All 17 styles show a 24-character team name in full. A blank team name
  shows "Team 1" or "Team 2" in the preview and in the export.
- During a deciding point, all styles show a tab in the accent color:
  "GOLDEN POINT", "STAR POINT", or "DECIDING POINT" (tennis no-ad). The tab
  is under a board at the top of the video and above a board at the bottom,
  so the board does not move. The scoreboard style has the switch
  "Golden / star point badge" (`showDecidingPoint`, on by default).
- The style "Padel Glass": a dark padel-blue board in a light glass frame, a
  frosted glass band at the top, white court lines, and a padel-ball yellow
  tile for the leading points. The board is darker than the court blue, so
  it stands out on a padel video. All sports can use it.

Statistics:

- The row "Deciding points won". Its label is "Golden points won" or "Star
  points won". An Americano match shows the points as its score (for
  example "13-11") and has match points, but no sets or games.
- The Stats tab and the statistics card use "Team 1" and "Team 2" when a
  padel team has no name.
- Until the video frame loads, the card preview shows a drawn padel court
  (blue, with glass walls) for a padel project (`StatsCardPreview`).
- A padel project without `stats.json` uses the point length limits 12 s
  (short) and 18 s (long) (`StatsSettingsV1.defaults`). These values are an
  estimate (P-5).

Other:

- The help pages and the README tell about padel. The tagline
  (`AppInfo.TAGLINE`) is "Tennis & Padel Video Editor".
- The usage statistics have the session attribute `sport` (B-44,
  `docs/analytics/design.md`).
- Tests: a padel golden project (`src/test/resources/golden/padel/`) and a
  UI-flow test that creates a padel project (`ApplicationShellUiFlowIT`).

### Code that does not know the sport

- Points tab, Colors tab, Crop and Rotate tab.
- Export pipeline (`FFmpegCommandBuilder`, `RenderService`, the chunk and
  pass planners). It uses the scoreboard images.
- `MatchStats`: the statistics are correct for teams. "Break points",
  "Service games", and "Deuce points" are also padel terms.
- `RulesEngine.kt`: an old tennis engine that only tests use.

### Known limits

- Older versions. Version 1.0.0 reads `STAR_POINT` as advantage and
  `TOTAL_POINTS` as sets, because of the enum defaults. It ignores
  `earlyTiebreak`, so it plays the tiebreak of a 9-game pro set at 9–9. It
  shows the "Padel Glass" style as "Broadcast". If a user opens a padel
  project in 1.0.0 and the app saves the score, the rule is lost. This is a
  problem only after a downgrade. A check with the installed 1.0.0
  (2026-10-09): it opens a padel `score.json` and an Americano `score.json`
  without an error.
- An Americano draw shows the equal points. Nothing on the scoreboard or in
  Stats says "Draw" (B-43).

## Decisions

- 2026-10-08: the sport is in `score.json` (`ScoreV1.sport`), not in the
  project manifest. The scoring rules, the side names, and the statistics
  all read `score.json`. A file without the field is a tennis project.
- 2026-10-08: the default padel deuce rule is the star point, because the
  FIP rules use it since 2026. Users who play the golden point or advantage
  change it in the "New project" dialog or in the scoring settings.
- 2026-10-08: a tennis project gets no `score.json` at creation.
- 2026-10-08: Americano events are a separate item: B-43 in `backlog.md`.
- 2026-10-09: team names. Each team keeps one name field, for example
  "Lebrón / Galán". The app does not get two name fields for each team, and
  the scoreboard styles do not get two name lines.
- 2026-10-09: branding. The tagline is "Tennis & Padel Video Editor". Keep
  the app name and the tennis ball mark. Change the site after the release
  with padel support (P-7).
- 2026-10-09: tennis doubles. No singles / doubles choice now. A tennis
  doubles user types both names in one player name, for example "A / B".
  Add the choice only if users ask for it.
- 2026-10-09: the point length limits of padel are 12 s and 18 s, as an
  estimate (P-5).
- 2026-10-10: the baseline is complete. The open work is B-45.

## Improvements (B-45)

Each task starts from the baseline. The order is not decided, except for
P-7 and P-6: the site first, then Spanish.

- P-1 Server of each player. Padel and tennis doubles have four servers in a
  fixed order (A1, B1, A2, B2). The app tracks only the serving team. There
  are no serve statistics for each player.
- P-2 Shot and point-ending tags. Padel players look at winners, errors,
  smashes out of the court ("por 3", "por 4"), and wall play. The app has no
  shot or point-ending tags for tennis or padel. This is a separate feature
  for both sports.
- P-3 Star point side. At the star point, the receiving team chooses the
  side of the serve. The app does not record it. It has no effect on the
  score.
- P-4 Padel icon. The serve button and other icons use the tennis racket
  icon (`Material2MZ.SPORTS_TENNIS`). Material2 has no padel icon. Use an
  SVG icon for padel projects.
- P-5 Point length limits. Measure the point lengths of real padel videos,
  and change the 12 s and 18 s limits if necessary
  (`StatsIO.PADEL_SHORT_POINT_MAX_SECONDS` and
  `PADEL_LONG_POINT_MIN_SECONDS`).
- P-6 Spanish. The padel terms in Spanish (for example "punto de oro" for
  the golden point) are part of B-11, epic E4
  (`docs/localization/b-11-e4-spanish.md`, task E4-T2). Do it after P-7.
- P-7 Site and FAQ. After the release with padel support:
  - Add the release note (see "Release note of the baseline") to the site
    changelog (`site/public/changelog/index.html`) and to the update
    `notes` of `release/version-policy.json`.
  - The site tells about tennis only. The FAQ says "Padel support is
    planned". Change these texts (`docs/marketing/strategy.md`).
  - Add padel screenshots and one padel FAQ entry (star point, team names).

## Release note of the baseline

- Padel: a Tennis / Padel switch in the "New project" dialog, team names,
  the golden point, the star point, and Americano matches.
- A "Star point" or "Golden point" badge on the scoreboard, and the
  "Deciding points won" statistic.
- A pro set to 9 games with a tiebreak at 8–8.
- A new scoreboard style "Padel Glass".
- Do not open a padel project in an older version. An older version reads
  the star point as advantage and an Americano match as sets. If it saves
  the score, the padel rule is lost.

## Sources

- FIP: [Premier Padel and the International Padel Federation unveil the 2026 calendar and the new star point system](https://www.padelfip.com/2025/12/premier-padel-and-international-padel-federation-unveil-2026-qatar-airways-premier-padel-tour-calendar-and-innovative-new-star-point-system-launched)
- Padel Addict: [What is the star point](https://www.padeladdict.com/en/what-is-the-star-point-this-is-premier-padels-new-scoring-system/)
- USA Padel: [2026 Competition Structure Guide](https://padelusa.org/wp-content/uploads/2026/06/2026-COMPETITION-STRUCTURE-GUIDE-JUN-30-2026.pdf)
- FFT padel league formats D1 and D2 (pro set to 9 games, tiebreak at 8/8),
  as listed by a padel shop blog. Check the current FFT rules before you
  depend on them.
