# Padel (B-14)

This file tells what the app does for padel, and it lists the open gaps and
questions. For the order of the work, see `backlog.md`.

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
  point at the third deuce, 5–5 in points).
- Americano: the structure `TOTAL_POINTS`. Points count 1, 2, 3 until both
  teams together played the total (16, 21, 24, or 32). The team with more
  points wins. Equal points are a draw. Each team serves 2 or 4 points in a
  row. The points after the end of the match do not count.
- Statistics: a new row "Deciding points won". Its label is "Golden points
  won" or "Star points won". An Americano match shows the points as its
  score (for example "13-11") and has match points, but no sets or games.
- The Stats tab and the statistics card use "Team 1" and "Team 2" when a
  padel team has no name.
- Scoreboard badge: during a deciding point, all 16 scoreboard styles show
  a tab in the accent color: "GOLDEN POINT", "STAR POINT", or "DECIDING
  POINT" (tennis no-ad). The tab is under a board at the top of the video
  and above a board at the bottom, so the board does not move. The
  scoreboard style has the switch "Golden / star point badge"
  (`showDecidingPoint`, on by default).
- Projects list: a Sport column. A project without `score.json` is tennis.
- The help pages tell about padel.

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

## Open questions

- Q-4 Team names. A team is one name field, for example "Lebrón / Galán"
  (maximum 24 characters). Do users need two name fields for each team?
  The scoreboard styles then need space for two names on each side.
- Q-6 Branding. The tagline is "Tennis Video Editor" (`AppInfo.TAGLINE`),
  the app mark is a tennis ball, and the landing site tells about tennis
  only. Do we change the name, the tagline, or the site for padel?
- Q-7 Tennis doubles. Tennis doubles also has teams. Do we add a
  singles / doubles choice for tennis, so that the labels say "Team"?

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
- G-8 Court drawing. The Stats tab draws a tennis court until the video
  frame loads (`StatsCardPreview`). It does not draw a padel court.
- G-9 Icons. The serve button and other icons use the tennis racket icon
  (`Material2MZ.SPORTS_TENNIS`). Material2 has no padel icon.
- G-10 Older versions. Version 1.0.0 reads `STAR_POINT` as advantage and
  `TOTAL_POINTS` as sets, because of the enum defaults. If a user opens a
  padel project in 1.0.0 and the app saves the score, the rule is lost.
  This is a problem only after a downgrade.
- G-11 Localization. Padel terms in Spanish ("punto de oro") are part of
  B-11.
- G-12 UI-flow tests. The Robot UI-flow tests (`-Pui-flow`) do not cover
  the Tennis / Padel switch yet.
