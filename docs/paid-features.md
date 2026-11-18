# Paid features

This file lists the paid features that are implemented in the code. Use it
to see which features are paid and which are free. Added on 2026-11-15.

- The open work for the paid version is in `backlog-paid.md`. This file
  lists only the features that the code has now.
- The paid version is not released. Thus each feature here has the status
  "Pending". A pending feature is in the code, but the free version does
  not show it. The decision that it is paid can still change.
- The free version is the only released version. Do not name a pending
  feature in the release notes, the changelog, the "What's new" page, or
  the `notes` of `release/version-policy.json`.
- The paid switch (`--paid` or `-Dbananashot.paid=true`) turns on the
  pending features for a test. See "The paid switch" in `backlog-paid.md`.

## Implemented paid features

| Feature | Item | In the code | What the free version does | Implemented | Status |
|---|---|---|---|---|---|
| Comment style Glow | P-1 | `CommentStyle.GLOW` (`paid = true`) | Does not show it. Draws the comment as Outline. | 2026-11-13 (7e47662) | Pending |
| Comment style TV label | P-1 | `CommentStyle.LOWER_THIRD` (`paid = true`) | Does not show it. Draws the comment as Outline. | 2026-11-13 (7e47662) | Pending |
| Comment style Bubble | P-1 | `CommentStyle.BUBBLE` (`paid = true`) | Does not show it. Draws the comment as Outline. | 2026-11-13 (7e47662) | Pending |
| Comment animation Fade | P-3 | `CommentAnimation.FADE` (`paid = true`) | Does not show the Animation switch. Draws the comment with no animation. | 2026-11-12 (754991a) | Pending |
| Comment animation Pop | P-3 | `CommentAnimation.POP` (`paid = true`) | Does not show the Animation switch. Draws the comment with no animation. | 2026-11-12 (4025faa) | Pending |
| Comment animation Slide up | P-3 | `CommentAnimation.SLIDE_UP` (`paid = true`) | Does not show the Animation switch. Draws the comment with no animation. | 2026-11-12 (4025faa) | Pending |
| Advanced scoring for tennis (serve faults, lets, endings, error kind, and their statistics) | B-68, B-72, B-73 | `ScoringMode.ADVANCED` (`paid = true`), `HelpPage.ADVANCED_SCORING` (`paid = true`) | Uses basic mode for each project. Does not show the Scoring mode group in Scoring rules, the Scoring card in More → Settings, or the Advanced scoring help page. A project in advanced mode keeps its mode and its details. A new project gets basic mode. | 2026-11-13 (fe2f36b to 778ae43). Paid since 2026-11-15. | Pending |
| Point filter of the export (player, ending, error kind) | B-69 | `PointFilter`, `ExportClips.of(filter = ...)`, `PointFilterDropdown` | Does not show the filter buttons. It needs advanced mode, so `ExportClips` ignores it without advanced mode. | 2026-11-17 | Pending |
| Attack and Consistency ratings, and the advanced inputs of the Serve and Return ratings (experimental) | B-77 | `MatchStat.RATING_ATTACK` and `MatchStat.RATING_CONSISTENCY` (`advanced = true`), `PerformanceRatings` | Does not show the Attack and Consistency rows. Serve, Return, Pressure, and Overall use only the basic data. | 2026-11-18 | Pending |

The decision that advanced scoring is paid was made on 2026-11-15. The
statistics insights (B-74) and the filter by point detail (B-69) use the
data of advanced mode. Thus the free version cannot have them in their
current form.

## Related free features

These features are near a paid feature, but they are free. The free version
has them.

- The comment styles Plain, Shadow, Outline, Bold, Card, and Pill.
- The comment animation None.
- Basic scoring: the winner of each point, in all sports.

## How to keep this list

- When a paid feature is implemented, add a row. Give the item of
  `backlog-paid.md`, the mark in the code, and the behavior of the free
  version.
- When a feature moves from paid to free, remove its row, remove the
  `paid` mark in the code, and write the decision in `backlog-paid.md`.
- When the paid version is released, change the status of each feature in
  the release to "Released in <version>".
