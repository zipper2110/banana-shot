# Backlog of the paid version

This file lists the work for the paid version ("Pro"). The main backlog is
`backlog.md`. Added on 2026-11-12.

An item here has the prefix `P-`. The milestone B-66 is in this file too.

The paid features that the code has now are in `paid-features.md`. When a
paid feature is implemented, add it to that list.

## The paid switch

- Decided on 2026-11-12: until the paid version has a subscription check
  (P-2), a simple switch turns on the paid features.
- Give the program argument `--paid` or the VM option
  `-Dbananashot.paid=true`. Without them, the app is the free version.
- The code is `org.litvin.shared.util.Edition`. `Edition.isPaid` tells if
  the paid features are on. A restart after a language change keeps the
  switch.
- Each paid feature has a mark in the code (for example
  `CommentStyle.paid`). Before a free release, the free version must not
  show a paid feature.
- Any person who knows the argument can turn on the paid features. This is
  acceptable only while the paid version is not for sale. P-2 replaces the
  switch before the paid release.

### B-66 Milestone: paid version

- Added on 2026-11-11. Moved from `backlog.md` on 2026-11-12. This is a
  milestone. It collects other items.
- The site calls this version "Pro" (Pricing page, decision 32 of
  `docs/site/b-10-tasks.md`). `docs/marketing/strategy.md` says: decide on
  the Pro version only after the free version has steady users.
- Price: a monthly subscription. The default price is $9 per month. The user
  can change the price to any value. The minimum price is $3 or $5 (not
  decided).
- Option: a one-time payment for a lifetime license to the current version
  and its features.
- Features of the paid version:
  - Short vertical videos with camera movement (see B-16 in `backlog.md`).
  - An intro and outro constructor (see B-15 in `backlog.md`).
  - Audio tracks: the user can add an audio track (for example music) to the
    intro, the outro, and the main video.
  - More styles for scoreboards and thumbnails, or a constructor for a
    personal style.
  - More comment styles (P-1).
  - Comment animations (P-3).
  - Filter and export by point detail (B-69).
  - Advanced scoring for tennis (B-68 in `backlog.md`). Decided on
    2026-11-15. It is implemented. See `paid-features.md`.
  - Maybe other features.
  - Maybe more than 5 exported videos per month. Probably not.
- Open questions: the minimum price; whether to offer the lifetime license;
  which other features go in the paid version (for example the announcements
  of B-71); whether the free version gets an export limit. The Pricing page
  now says that the free version has all features, so an export limit
  changes that promise.
- Done when: the user can buy the paid version, and the paid features work
  only with a valid subscription or license.

### P-1 Paid comment styles

- Decided on 2026-11-12: the comment styles Glow, TV label (lower third),
  and Bubble are for the paid version. They have `paid = true` in
  `CommentStyle`.
- The free version does not show them in the comment dialog. It draws a
  comment with a paid style as Outline in the preview and in the export.
  When the user edits that comment, the dialog shows Outline.
- The texts of the three styles are in all languages already.
- Done when: the paid version shows and draws the three styles, and the
  free version does not.

### P-2 Subscription check

- Added on 2026-11-12. Replace the paid switch with a check of an online
  subscription or a license.
- Open questions: the payment provider; how often the app checks the
  subscription; how long the paid features work offline; what the app shows
  when the subscription ends.
- Use the licensing work in `docs/licensing/` (the online check and the
  threat model) as a base.
- Done when: only a user with a valid subscription or license gets the paid
  features, and `--paid` has no effect in a released build.

### P-3 Paid comment animations

- Decided on 2026-11-13: the comment animations Fade, Pop, and Slide up
  are for the paid version. They have `paid = true` in `CommentAnimation`.
  Only None is free.
- The free version does not show the Animation switch in the comment
  dialog. It draws a comment with a paid animation without animation in
  the preview and in the export. When the user edits that comment, the
  dialog saves None.
- The texts of the animations are in all languages already.
- Done when: the paid version shows and draws the animations, and the
  free version does not.

### B-69 Filter and export by point detail

- Added on 2026-11-13. Moved from `backlog.md` on 2026-11-17.
- Let the user filter the points by their details and export only these
  points. For example: "all my unforced errors", "all my winners", or "all
  my forced and unforced errors". This is good for training and for a
  coach.
- Decided on 2026-11-17:
  - The filter is for the paid version. It uses only the details of
    advanced scoring (B-68), and advanced scoring is paid. Basic scoring
    has only the winner, so it gets no filter.
  - The filter is in the Export tab. It is a multi-select: the user can
    select more than one value, for example "Forced errors" and "Unforced
    errors".
- The rules of the filter (2026-11-17):
  - The filter is in step 1, under the content cards. It shows only for a
    project in advanced mode in the paid version, and not for "Full video".
  - It has three groups: player, ending (aces, winners, forced errors,
    unforced errors, double faults), and error kind (net, long, wide).
    Strokes need B-70, because advanced mode records no strokes.
  - "Player" is the player who made the ending: the winner of an ace or a
    winner, and the other player for an error or a double fault. Thus "all
    my unforced errors" is "player 1" and "Unforced errors".
  - In one group, a point matches when it has one of the selected values.
    A point must match each group that has a selection. A group with no
    selection keeps all points. A filter with no selection keeps all
    points.
  - With a selection, a point with no ending does not match: a point with
    only the winner (Q, E, or basic mode), a W clip, and a pack with no
    point clip.
  - The fault clips and the let clips of a point that matches go in with
    the option "Include serve faults and lets".
  - The filter works with "Only points" and "Only favorites". The content
    cards show the length and the count of the points that match.
  - The filter is not saved. It is empty when the user opens a project.
  - The file name gets "filtered" in place of "points" or "favs".
- Done when: in the paid version, the user selects a filter in the Export
  tab, and the export has only the points that match it. The free version
  does not show the filter.
