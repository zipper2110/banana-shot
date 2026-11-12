# B-15 Intro and outro

Status: postponed (decided on 2026-10-30). This file keeps the concept until
the work starts.

## Goal

The user can put an intro before the match video and an outro after it.
Each one is a list of blocks. The user can add, remove, repeat and move the
blocks, and each block has its own settings.

Users asked for two things:

- A separate video clip, for example a channel intro.
- A part of the match video, for example as a teaser.

The outro also solves an open question of a user report (2026-10-30): a
highlights export ends at the end of the last point, so the final score
never shows. A final score block in the outro shows it.

## Reference

- Intro reference video (added on 2026-11-11):
  https://www.youtube.com/watch?v=h68cFc_meyU
- The intro has an audio track, multiple text frames and images.
- The block types below have no image block yet. Audio is open question 2.

## Concept

### Blocks in a list, not a free timeline

- The intro and the outro are each an ordered list of blocks. The user
  drags a block to move it, and can duplicate or delete it.
- Each block has a length. A strip above the list shows the total length
  and the part of each block.
- Blocks do not overlap and have no tracks. A full timeline editor costs
  much and gives little for an intro.

### Block types

| Block | Intro | Outro | Notes |
|---|---|---|---|
| Match card | yes | | Uses the thumbnail styles. The background is a still frame, a video fragment (dim or blurred), or a slow zoom over a frame. |
| Text card | yes | yes | Background: a color, a still frame, or a video fragment, with dim and blur. |
| Fragment of this match | yes | yes | For example a teaser of the favorite points. It needs no other file. |
| Coming up | yes | | A fragment of this match with a text over it, for example "Coming up...". See below. |
| Separate video clip | yes | yes | A video file of the user, for example a channel intro or a sponsor clip. |
| Final score card | | yes | Uses the final scoreboard display, larger. |
| Statistics card | | yes | Uses the existing statistics card. |

### Coming up block

A short fragment of the match that shows a good moment before the match
starts. A text shows over it, by default "Coming up...". The user can
repeat the block to show two or three moments.

- Fragment: the user selects a point in the points list, or sets a start
  and an end in the video. The user can trim the start and the end. The
  default length is 3 to 6 s.
- Suggested points: the app shows the points that are good teasers first.
  For example the match point, the longest point, the set points and break
  points that the player won (`PointStakes`), and the start of the longest
  point run (`KeyPoints`).
- Text: the user can change the text and its position. The text can have
  placeholders. Examples: "Coming up...", "Watch this", "{player1} saves a
  match point".
- Second line (optional): the stake and the score at that moment, for
  example "Match point · 6-4 5-4". The app takes it from the score of the
  point.
- No spoiler: by default the fragment stops before the end of the point,
  and the scoreboard of the video does not show. Thus the teaser does not
  tell the result. The user can turn this off.
- Effects (optional): slow motion, and a freeze on the last frame while
  the text stays. Dim and blur as on the other video backgrounds.

### Blocks use the project data

- The cards take the names, the title, the date, the place and the score
  from the project. A text can have placeholders, for example `{player1}`
  and `{date}`. A changed name then changes the intro too.
- The thumbnail has the same fields. One "match info" model can serve the
  thumbnail, the match card and the scoreboard title.
- A block without its data does not show. For example, a project without a
  score has no final score card. Thus a default or a preset never makes a
  broken video.

### Defaults and presets

- A new project gets a default intro and outro:
  - Intro: the match card for 4 s over a dim fragment of the video.
  - Outro: the final score for 4 s, then the statistics for 6 s, when the
    project has a score.
- Presets are start points that the user can then change: None, Simple,
  Full (coming up, match card, final score and statistics), Short (for
  social media clips).
- "Use as my default" keeps the user's own intro and outro for new
  projects, as the scoreboard style does (`ScoreboardStyleDefaults`).

## Open questions

1. The place in the app. Proposal: an "Intro & outro" section on the Export
   tab, and a check box on each export ("Include intro and outro"). A
   highlights export, a full export and later a vertical clip can need
   different settings.
2. Audio: the sound of the fragment, silence, or a music file of the user.
   The app gives no music library.
3. Transitions: a short fade between the blocks and into the match is
   enough at first.
4. Coming up: how long before the end of the point the fragment must stop
   to keep the result secret. A fixed time (for example 1 s) is simple. The
   time of the last shot is better, but the app does not mark it now.

## Technical notes

- A separate video clip is the hard part. Phone clips can be HEVC, have a
  variable frame rate, a rotation, HDR, and other sizes and audio formats.
  The export must convert each clip to the size, frame rate and audio of
  the export before it joins them. HDR to SDR needs a tone map.
- The cards can use the scene model of the scoreboard (`ScoreboardScene`
  and its ASS output). Then the preview and the export show the same card.
- The preview must show the intro before a long export. The mpv preview
  already shows ASS over a video, so a preview of one block is possible
  without a full render.
- Each block with a video background makes the export a little longer.
- The fade of the full-video scoreboard (2026-10-30) and B-56 also need
  in and out animations. Use the same approach.

## Epics

### E1 Blocks and cards

- The intro and outro lists, with the match card, the text card (color and
  still frame backgrounds), the final score card and the statistics card.
- The project default, the Export tab section and the export check box.
- Done when: an export can have an intro and an outro from these blocks,
  and the preview shows each block.

### E2 Video fragments and presets

- Video fragment backgrounds, the teaser block, the coming up block, the
  fades between blocks.
- The presets and "Use as my default".
- Done when: the user can select a preset, change it, and keep it as the
  default for new projects.

### E3 Separate clips and music

- Separate video clips with the conversion to the export format (codec,
  frame rate, rotation, HDR, audio).
- A music file for the intro and the outro.
- Done when: a clip from another phone or camera joins the export with no
  visible or audible break.
