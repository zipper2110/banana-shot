# Beach tennis

Beach tennis is the third sport of the app, after tennis and padel
(`padel.md`). It uses the same point, game, and set counting as tennis.

## Rules that affect the app

| Rule | Tennis | Beach tennis (ITF) | In the app |
|---|---|---|---|
| Points in a game | 0, 15, 30, 40 | The same | The same engine |
| Deuce | Advantage or no-ad | Always no-ad: the point at 40–40 wins the game | Default `NO_AD`. Advantage stays a choice for club games. No star point. |
| Sets | To 6 games, two-game lead | Tiebreak sets to 6 games | The same engine |
| Set tiebreak | 7 points at 6–6 | The same | The same engine |
| Deciding set | Full set or a 10-point match tiebreak | A 10-point match tiebreak at one set all | Default `MATCH_TIEBREAK` |
| Players | Singles (doubles is possible) | Usually doubles. Singles is possible. | "Team 1" and "Team 2" |
| Serve | Two serves | One serve, no service let | The serve mark does not change |
| Court | Tennis court | 16 m × 8 m of sand, net at 1.7 m, no service lines | A sand court drawing in the Stats tab |

## Default rules

Decided on 2026-10-26: a new beach tennis project, and a change to beach
tennis in the scoring settings, use the rules of the ITF Beach Tennis World
Tour:

- Best of 3 sets. Each set is to 6 games with a 7-point tiebreak at 6–6.
- A 10-point match tiebreak in place of the third set.
- No-ad: the point at 40–40 wins the game.

In the app, this is the format "Best of 3 sets, match tiebreak" with the
deuce rule "No-ad" (`Sport.defaultRules`).

## Decisions

Decided on 2026-10-26:

- The sport value in `score.json` is `BEACH_TENNIS`. The analytics value is
  `beach_tennis`.
- The formats are the tennis formats without "Best of 5 sets". The padel
  formats "Pro set (9 games)" and "Americano" do not show.
- The words are the tennis words: "match tiebreak", "No-ad", and the badge
  "DECIDING POINT". The ITF also uses these words.
- The default side names are the padel texts ("Team 1", "Pareja 1",
  "Dupla 1"). The catalog has no separate key.
- The New project dialog shows no rules group for beach tennis. The user
  changes the rules in the scoring settings.
- The point length limits of the statistics: a short point lasts 7 s or
  less, and a long point lasts 10 s or more (`StatsSettingsV1.defaults`).
  Beach tennis points are usually shorter than tennis points (10 s and
  13 s). A project with `stats.json` keeps its own limits.
- The Share tab shows the sport name ("BEACH TENNIS") as the match kind, as
  for padel.

## Known limits

- Older versions read `BEACH_TENNIS` as tennis, because of the enum
  default. The rules stay correct, because tennis has the same engine.
- The analytics Worker refuses a summary with an unknown sport. Deploy the
  Worker with `beach_tennis` before the first app build that sends it.

## Open items

- The site, the tagline ("Tennis & Padel Video Editor"), and the help
  summary still name only tennis and padel.
- A native speaker must review the new texts, as for all B-11 texts.

## Sources

- ITF, "2023 ITF Beach Tennis World Tour Regulations, Summary of Key
  Changes", rule G.13 "Match Format":
  https://www.itftennis.com/media/8996/2023-itf-bt-world-tour-regulations-summary-of-key-changes.pdf
- ITF, "A guide to beach tennis" (no-ad, one serve):
  https://www.itftennis.com/en/news-and-media/articles/a-guide-to-beach-tennis/
