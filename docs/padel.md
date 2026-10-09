# Padel (B-14)

This file tells what the app does for padel, and it lists the open gaps and
questions. For the order of the work, see `backlog.md`.

## Goal

A padel player can do all the steps that a tennis player can do:

- Create a project from a padel video.
- Mark the points.
- Score the points with the padel rules. The app computes the games, the
  sets, and the tiebreaks.
- Export a video with a scoreboard that shows two teams.
- See the statistics of the match.

## Padel rules that affect the app

| Rule | Tennis | Padel | In the app |
|---|---|---|---|
| Points in a game | 0, 15, 30, 40 | The same | The same engine |
| Deuce | Advantage or no-ad | Star point (FIP, from 2026). Golden point in many clubs and older leagues. Advantage in some clubs. | Advantage, golden point, star point |
| Sets | To 6 games, two-game lead | The same | The same engine |
| Set tiebreak | 7 points at 6–6 | The same | The same engine |
| Deciding set | Full set or a 10-point match tiebreak | Full set, or a 10-point "super tiebreak" | The padel name is "super tiebreak" |
| Players | Singles (doubles is possible) | Always doubles: two teams of two players | One name field for each team (Q-4, Q-7) |
| Serve order | Changes after each game. In a tiebreak: 1 point, then 2 points each. | The same for the teams. In each team, the two players serve in turns. | The serving team only (G-2) |
| Court | Tennis court | 20 m × 10 m with glass walls | A padel court drawing in the Stats tab |
| Social formats | Rare | "Americano" and "Mexicano": a fixed total of points, for example 24 points | Americano (`TOTAL_POINTS`). Events are B-43. |

### The star point

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

## What the app does

- New project: the "New project" dialog has a Tennis / Padel switch. For
  padel, the dialog also shows the padel rules: the format, the deuce rule,
  the total points of an Americano match, and the serve turn.
- The next new project starts with the sport of the last new project. A
  padel project also starts with the rules of the last padel project
  (preferences `lastSport` and `lastPadelRules` of the Projects node).
- A padel project gets `score.json` when the project is created. The file
  has `sport: PADEL`, the rules, and the names "Team 1" and "Team 2". The
  flag `useDefaultScoreboard` makes the Scoring tab apply the default
  scoreboard style, as for a project without `score.json`. The Scoring tab
  still opens the scoring settings on the first visit, so the user can type
  the team names.
- Scoring settings: a Sport row. A change of the sport resets the rules to
  the defaults of the new sport and changes a default side name ("Player 1"
  to "Team 1"). Padel shows its own formats and words: "super tiebreak" for
  "match tiebreak", "golden point" for "no-ad".
- Deuce rules: advantage, golden point (`NO_AD`), and star point
  (`STAR_POINT`, FIP rules since 2026: two advantages, then one deciding
  point at the third deuce, 5–5 in points). Only padel offers the star
  point (`Sport.deuceRules`).
- Americano: the structure `TOTAL_POINTS`. Points count 1, 2, 3 until both
  teams together played the total (16, 21, 24, or 32). The team with more
  points wins. Equal points are a draw. Each team serves 2 or 4 points in a
  row. The points after the end of the match do not count.
- Statistics: a new row "Deciding points won". Its label is "Golden points
  won" or "Star points won". An Americano match shows the points as its
  score (for example "13-11") and has match points, but no sets or games.
- The Stats tab and the statistics card use "Team 1" and "Team 2" when a
  padel team has no name.
- Stats tab: until the video frame loads, the card preview shows a drawn
  padel court (blue, with glass walls) for a padel project
  (`StatsCardPreview`).
- Scoreboard badge: during a deciding point, all 16 scoreboard styles show
  a tab in the accent color: "GOLDEN POINT", "STAR POINT", or "DECIDING
  POINT" (tennis no-ad). The tab is under a board at the top of the video
  and above a board at the bottom, so the board does not move. The
  scoreboard style has the switch "Golden / star point badge"
  (`showDecidingPoint`, on by default).
- Scoring tab: when the selected point is a deciding point, the score panel
  shows the same badge at the right of "Who won the point?".
- Team names: the scoring settings and the scoreboard accept 24 characters
  (`ScoreboardComponent.PLAYER_NAME_MAX_CHARS`). All 16 styles show a
  24-character team name in full.
- Tests: a padel golden project (`src/test/resources/golden/padel/`) and a
  UI-flow test that creates a padel project
  (`ApplicationShellUiFlowIT`).
- Projects list: a Sport column. A project without `score.json` is tennis.
- The help pages tell about padel.
- The README tells about padel.

## Code that does not know the sport

- Points tab, Colors tab, Crop and Rotate tab.
- Export pipeline (`FFmpegCommandBuilder`, `RenderService`, the chunk and
  pass planners). It uses the scoreboard images.
- `MatchStats`: the statistics are correct for teams. "Break points",
  "Service games", and "Deuce points" are also padel terms.
- `RulesEngine.kt`: an old tennis engine that only tests use.

## Decisions (2026-10-08)

- The sport is in `score.json` (`ScoreV1.sport`), not in the project
  manifest. The scoring rules, the side names, and the statistics all read
  `score.json`. A file without the field is a tennis project.
- The default padel deuce rule is the star point, because the FIP rules use
  it since 2026. The author confirmed it. Users who play the golden point or
  advantage change it in the "New project" dialog or in the scoring
  settings.
- Americano events are a separate item: B-43 in `backlog.md`.
- The sport becomes a dimension of the usage statistics: B-44 in
  `backlog.md`.
- A tennis project gets no `score.json` at creation. Its behavior did not
  change.

## Decisions (2026-10-09)

- Q-4 Team names: each team keeps one name field, for example
  "Lebrón / Galán". The app does not get two name fields for each team, and
  the scoreboard styles do not get two name lines. The scoreboard shows the
  full 24 characters of the name field.
- Q-6 Branding: the tagline (`AppInfo.TAGLINE`) is "Tennis & Padel Video
  Editor". Keep the app name and the tennis ball mark. Change
  the site at the padel release (G-18).
- Q-7 Tennis doubles: no singles / doubles choice now. A tennis doubles user
  types both names in one player name, for example "A / B". Add the choice
  only if users ask for it.

## Gaps

- G-2 Server of each player. Padel and tennis doubles have four servers in a
  fixed order (A1, B1, A2, B2). The app tracks only the serving side. There
  are no serve statistics for each player.
- G-4 Padel statistics. Padel players look at winners, errors, smashes out
  of the court ("por 3", "por 4"), and wall play. The app has no shot or
  point-ending tags for tennis or padel. This is a separate feature.
- G-6 Pro set to 9 games. Some padel leagues play a pro set to 9 games. The
  "Games in a set" control has 4, 6, and 8 games only.
- G-7 Star point side. At the star point, the receiving team chooses the
  side of the serve. The app does not record it. It has no effect on the
  score.

- G-9 Icons. The serve button and other icons use the tennis racket icon
  (`Material2MZ.SPORTS_TENNIS`). Material2 has no padel icon.
- G-10 Older versions. Version 1.0.0 reads `STAR_POINT` as advantage and
  `TOTAL_POINTS` as sets, because of the enum defaults. If a user opens a
  padel project in 1.0.0 and the app saves the score, the rule is lost.
  This is a problem only after a downgrade. A check with the installed
  1.0.0 (2026-10-09): it opens a padel `score.json` and an Americano
  `score.json` without an error.
- G-11 Localization. Padel terms in Spanish ("punto de oro") are part of
  B-11.
- G-13 Point length. A padel project without `stats.json` uses the limits
  12 s and 18 s (`StatsSettingsV1.defaults`). The author chose these
  values on 2026-10-09 as an estimate. Measure real padel videos, and change
  the values if necessary.
- G-18 Release notes, site, and FAQ. At the padel release, add the release
  note (see "Release note draft") to the site changelog
  (`site/public/changelog/index.html`) and to the update `notes`. The site
  tells about tennis only. The FAQ says "Padel support is planned". Change
  the site only after the release with padel support
  (`docs/marketing/strategy.md`). Add padel screenshots and one
  padel FAQ entry (star point, team names). Do the padel release first, then
  Spanish (B-11).
- G-19 Padel scoreboard style (optional). The 16 styles work for padel.
  Some style names are tennis names ("Grass Court", "Clay Court"). A padel
  style, for example "Padel Glass" (blue and white), is possible.

## Release note draft

- Padel: a Tennis / Padel switch in the "New project" dialog, team names,
  the golden point, the star point, and Americano matches.
- A "Star point" or "Golden point" badge on the scoreboard, and the
  "Deciding points won" statistic.
- Do not open a padel project in an older version. An older version reads
  the star point as advantage and an Americano match as sets. If it saves
  the score, the padel rule is lost.

## Sources

- FIP: [Premier Padel and the International Padel Federation unveil the 2026 calendar and the new star point system](https://www.padelfip.com/2025/12/premier-padel-and-international-padel-federation-unveil-2026-qatar-airways-premier-padel-tour-calendar-and-innovative-new-star-point-system-launched)
- Padel Addict: [What is the star point](https://www.padeladdict.com/en/what-is-the-star-point-this-is-premier-padels-new-scoring-system/)
- USA Padel: [2026 Competition Structure Guide](https://padelusa.org/wp-content/uploads/2026/06/2026-COMPETITION-STRUCTURE-GUIDE-JUN-30-2026.pdf)
