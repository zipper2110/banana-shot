# B-68 Point details

Status: concept. The review on 2026-11-13 left open questions, but none blocks the work (see "Open questions"). No code yet.

## Goal

The user can record more than the winner of a point: serve faults, double
faults, aces, winners, and the kinds of errors. The Stats tab then shows
the statistics that tennis players and coaches usually use, for example
the double faults and the unforced errors of each player.

The marking must stay fast. The user marks the points while the video
plays, with one key press at each event.

## Scope: tennis only

- This concept is for tennis projects only. Padel and beach tennis
  projects keep the basic mode for now.
- Later, other sports can get the same or an adapted version. Known
  differences:
  - Beach tennis has one serve and no service let
    (`docs/beach-tennis.md`). The start button label is "Serve", X is a
    fault that loses the point, and L is not available.
  - Padel has two serves and lets, as tennis. It has other strokes (for
    example the bandeja and the víbora) and points that end at the walls.

## Usual tennis statistics

| Level | Data | Used by |
|---|---|---|
| Broadcast | Aces, double faults, 1st serve in %, 1st serve points won %, 2nd serve points won %, winners, unforced errors, forced errors, net points won, break points | ATP and WTA match statistics, most tennis apps |
| Coach | The stroke that ended the point (forehand, backhand, volley, smash, drop shot, return), the error kind (net, long, wide), rally length in shots | Tagging apps, coaches |
| Shot by shot | Each shot with its stroke, and often its direction and depth | The Match Charting Project |

The app already has the break points and the point duration. The point
duration is a good stand-in for the rally length.

## Scoring modes

The project has a scoring mode. The mode sets which controls the point bar
shows.

| Mode | The user records | Controls |
|---|---|---|
| Basic | The winner | As now: S, C (Point start), Q, W, E |
| Advanced | Serve faults, lets, and how the point ended | S, C (1st serve / 2nd serve), X, L, W, and the ending buttons. No winner buttons. |
| Shot by shot | Each shot, and how the rally ended | Not in B-68. B-70 adds it later. See "Shot by shot (B-70)". |

- B-68 makes the basic mode and the advanced mode. The switch shows only
  these two modes until B-70.
- The modes are nested. Each mode gives all the data of the modes below it.
  Shot-by-shot data gives the advanced data, and the advanced data gives
  the winner. Thus the statistics read one model.
- The mode is a project setting, because it sets which data the project
  has. Settings has the default mode for new projects.
- The switch is a segmented control above the point bar in the Points tab.
  The user can change the mode during the match, for example to record
  only the important games in detail.
- A mode change deletes no data. A lower mode hides the extra data. The
  data shows again in the higher mode.

## Advanced mode

### Ending buttons

The ending buttons replace the winner buttons. One key press gives the
winner and how the point ended. An ending key ends the pending point, as a
winner key does now.

The buttons are in two groups. The group is the player who wins the point,
not the player who hit the last shot. Thus the left group gives the point
to player 1, as Q does now, and the right group gives it to player 2, as E
does now. This prevents wrong scores.

| Point to player 1 | Key | Point to player 2 | Key |
|---|---|---|---|
| Player 1 winner | 1 | Player 2 winner | 0 |
| Player 2 forced error | 2 | Player 1 forced error | 9 |
| Player 2 unforced error | 3 | Player 1 unforced error | 8 |
| Ace (only when player 1 serves) | 4 | Ace (only when player 2 serves) | 7 |

- The buttons show the player names, for example "Ben unforced error".
- The outer keys (1 and 0) are the winners. The keys mirror the two sides.
- W ("No point") stays. It is for clips that are not points.
- Decided on 2026-11-13: the user records the details of both players.
  The errors of the opponent are points of the user, so the statistics
  need them. One key records both facts, so this costs no extra time. A
  "player 1 only" option can come later if users ask for it.
- A double fault is not an ending button. The user presses X (see below).
- Only the server's group shows the ace button. Thus 7 buttons are
  active at a time.

### Decision rules for the ending buttons

The help page of advanced mode and the button tooltips use these rules.
They are the rules of the ATP and WTA statisticians and of the Match
Charting Project.

The user looks at the last shot of the rally and asks two questions:

1. Who hit the last shot?
2. Did the opponent touch the ball?

| The last shot | Button | The point goes to |
|---|---|---|
| The serve lands in, and the receiver does not touch it | Ace of the server | The server |
| Any other shot lands in, and the opponent does not touch it | Winner of the player who hit it | The player who hit it |
| The ball goes into the net or out, and the player was under pressure | Forced error of the player who hit it | The opponent |
| The ball goes into the net or out, and the player was not under pressure | Unforced error of the player who hit it | The opponent |

- "Touched or not touched" separates a winner from an error. The user can
  see this in the video, so this decision needs no judgment.
- If the opponent touches the ball and the ball does not go back in, it is
  an error of the opponent, not a winner. This is also true for a serve:
  if the receiver touches the serve and the return does not go back in, it
  is a forced or unforced error of the receiver, not an ace.
- Decided on 2026-11-13: there is no "service winner" ending. A forced
  return error records the same point.
- Forced or unforced is the only judgment. The question is: "Could this
  player make this shot most of the time?"
  - Forced error: the player had to run far, or the ball was very fast,
    very deep, or at the body. The player hit the ball late or with a
    stretched arm.
  - Unforced error: the player was in position, had time, and the ball was
    normal. The player missed.
  - Professional statisticians also do not always agree on this decision.
    The help tells the user that a consistent rule is more important than
    a perfect decision.
- Return errors use the same rule. A return that goes out after a strong
  serve is usually a forced error. A return that goes out after a slow
  2nd serve is usually an unforced error.

Unusual cases:

| Case | Button |
|---|---|
| The ball touches the net and lands in, and the opponent does not touch it | Winner of the player who hit it |
| The ball hits the opponent's body | Winner of the player who hit it |
| A player touches the net, or hits the ball after two bounces | Unforced error of that player |

Tooltips: each ending button has a short form of its rule, for example
"Ana unforced error: Ana's shot went into the net or out, and Ana was not
under pressure."

### Follow-up questions

After the ending, a strip under the point bar can ask more questions:

1. Shot: forehand, backhand, volley, smash, drop shot, or return.
2. Error kind: net, long, or wide. Only for errors.

Decided on 2026-11-13:

- The questions are off by default. The user turns them on with an option
  of the advanced mode. The project has the option, and Settings has the
  default for new projects.
- While a question is open, the digit keys answer the question. They do
  not select an ending. The strip shows the label of each digit.
- C (the next serve start), R (next point), or Esc closes the question
  without an answer. The answers are optional.
- The risk: a user presses a digit for the next ending while a question is
  open, and gives an answer by mistake. This is why the questions are off
  by default.

### Serve faults and lets

The user presses C at the start of every serve, because the user does not
know yet if the serve is a fault. The user presses X at every fault.

| On court | Keys | Clips |
|---|---|---|
| 1st serve in, rally | C, ending key | One point clip |
| 1st serve fault, 2nd serve in | C, X, C, ending key | A fault clip, then a point clip on the 2nd serve |
| Double fault | C, X, C, X | A fault clip, then a double fault clip. The receiver wins. |
| Let, then 1st serve in | C, L, C, ending key | A let clip, then a point clip on the 1st serve |

- X with an open point:
  - On a 1st serve, X ends the clip as a fault clip. The next serve is a
    2nd serve.
  - On a 2nd serve, X ends the clip as a fault clip. It is the second
    fault of the pack, so it is a double fault. The receiver wins.
- X with no open point and no active clip (see "Corrections") adds a fault
  clip of zero length at the playhead. Decided on 2026-11-13. The clip is
  in the points list. Thus the user can go to it, select it, edit it, and
  delete it, as other clips.
  - This is for users who do not want the faults in the video and do not
    press C at a 1st serve. It is also for a missed C. A user can mix the
    two ways in one match.
  - It is only for the first serve of a pack. When the pack at the playhead
    already has a fault clip or a let clip, X with no open point adds no
    clip. A 2nd serve fault needs a real clip. A hint tells the user to
    press C at the 2nd serve start, then X (see "Hints").
- X with no open point and an active clip changes that clip (see
  "Corrections").
- A clip of zero length has no video. The exports skip it. The timeline
  shows it as a thin mark.
- L ends the open clip as a let clip. The serve number does not change.
- L with no open point changes the active clip (see "Corrections"). With no
  active clip, L does nothing, and a hint shows (see "Hints").
- Fault clips and let clips have the outcome "no point". The score does not
  change. The time between the clips is cut, for example the time when the
  players take the ball off the court.
- Esc cancels the pending point, as now. Esc does not remove a clip of
  zero length. Delete removes it, as other clips.

### Labels that show the serve number

- The start button shows "1st serve" or "2nd serve". It keeps the C key
  chip. Basic mode keeps "Point start".
- The X button shows "Serve fault" on a 1st serve and "Double fault" on a
  2nd serve.
- After a fault, the label changes to "2nd serve". After a let, it does not
  change. After an ending or a double fault, it goes back to "1st serve".
  L and W do not change the serve number.
- The serve number comes from the pack at the playhead (see "Packs"), not
  from one global state. A clip of zero length also counts. Thus the label
  is correct after the user goes back to an earlier part of the match.
- The short labels must fit in all app languages and in the narrow form of
  the point bar.

### Corrections

Decided on 2026-11-13:

- With no open point, X, L, and the ending keys change the active clip.
  The active clip is the clip under the playhead, or the nearest clip in
  2 seconds of the playhead, as the active point now (`NEAR_POINT_MS` in
  `SwingPointsPanel`). A click on a row in the list also makes its clip
  the active clip.
- An ending key gives a new ending and a new winner to the active clip, as
  Q and E do now. On a fault clip or a let clip, it makes a point clip.
- X makes the active clip a fault clip. L makes it a let clip. For example,
  the user goes to a 1st serve fault and presses L to make it a let, or
  the reverse.
- The packs, the serve numbers, the labels, and the winners then change
  automatically (see "Packs").
- To fix a missed X, the user adds the fault: C and X at the 1st serve, or
  X alone in the gap. The point clip after it becomes a 2nd serve.
- Known limit, accepted on 2026-11-13: X in the 2 seconds before or after a
  clip changes that clip. It does not add a fault clip of zero length. In
  live marking, this is rare, because the next serve is usually more than
  2 seconds after the previous clip. To add a fault in this case, the user
  moves the playhead out of the 2 seconds, or uses C and X.

### The server

Decided on 2026-11-13:

- Advanced mode needs the server of each point. An ace gives the point to
  the server. A double fault gives the point to the receiver. The serve
  statistics are for each server.
- The project can have no serve mark. Then the engine gives no server to
  any point. In this case, when the user selects advanced mode or presses C
  in advanced mode, the point bar asks "Who serves?". It shows one button
  for each player name. It is not a dialog, and the video does not stop.
- The answer becomes the serve mark of the selected point, or of the point
  that C starts. The ending buttons and X are not active until the user
  answers.
- The winner of an ace or a double fault comes from the server of the
  point. S or the serve button can change the server of points that have
  an ace or a double fault. Then the winners of these points change, and
  the score can change.
- Before such a change, the app asks the user to confirm. The dialog
  shows:
  - each point that gets a new winner, with its number and its ending, for
    example "Point 14: ace, Ana → Ben",
  - the changes of the score that follow, for example a game or a set that
    now goes to the other player,
  - the buttons "Change server" and "Cancel".
- After "Change server", the app writes the new winners into `outcomes`.
  Thus basic mode and the scoreboard show the same score.
- A server change that changes no winner needs no confirmation, as now.
- Other edits can also move the server: a new winner of an earlier point,
  a deleted point, Override score, or new match rules. Decided on
  2026-11-13: these edits do not ask for confirmation. The app computes the
  winners of the aces and the double faults again, and writes them into
  `outcomes`. This is a trial. If users find it confusing, a confirmation
  can come later.

### Hints

- The hints are toasts, not dialogs. A toast does not stop the video and
  does not take the keyboard focus.
- Each hint has "Don't show again". The hints use the existing hint system,
  so "Show all hints again" in More → Settings shows them again.
- L with no open point: "A let without a point start has no effect. Press C
  at the serve start, then L, to keep the let in the video."
- X with no open point in a pack that already has a fault clip or a let
  clip: "This point already has a serve clip. Press C at the serve start,
  then X, to record the fault."
- The same pattern can cover other keys that do nothing, with one hint for
  each key.

## Export

- The Export tab has a new option: "Include serve faults and lets".
- On: the "Only points" and "Only favorites" exports include the fault
  clips and the let clips.
- Off: the exports skip them. The video has only the serve that counts, as
  the author marks it now.
- Each project has the option. Settings has the default for new projects.
  The default is off (decided on 2026-11-13), so the exported video stays
  as it is now.
- The fault clips are always in the data. Thus the user can change the
  decision after the marking, and can make two exports from one marking.
- A clip of zero length has no video, so the export skips it. The score
  that it gives still shows on the scoreboard.
- "Only favorites" uses the favorite flag of the point clip of the pack
  (see "Packs").

## Points list and navigator

### Packs

Decided on 2026-11-13:

- A pack is all the clips of one real point: the fault clips and the let
  clips of its serves, and then the point clip. For example: a 1st serve
  fault, a let, and then the 2nd serve with the rally.
- A fault clip or a let clip belongs to the next point clip in time order.
  A pack ends with the clip that gives the point: an ending, a double
  fault, or a winner from basic mode. A W clip ("No point") is not in a
  pack.
- The app computes the serve number of each clip from its pack. Decided on
  2026-11-13. A clip after no fault clip of its pack is on the 1st serve.
  A clip after one fault clip is on the 2nd serve. A let clip does not
  change the serve number. The second fault clip of a pack is the double
  fault, and it ends the pack.
- Thus the serve numbers are always the same as the clips. An added or a
  deleted fault clip changes the serve numbers and can change the winner
  of a double fault. The app writes the new winners into `outcomes`, as
  for a server change (see "The server").
- At the end of the list, a pack can have no point clip yet. For example,
  the user pressed X, and did not mark the 2nd serve yet.
- The list shows each pack as a light visual group. For example, a thin
  line at the left of its rows, and less space between its rows than
  between two packs. A pack with one clip looks as a normal row.
- Fault clips and let clips do not count as points. The pack is one point,
  so the navigator and the list number the packs, not the clips. The
  number shows on the first row of the pack.
- R and Shift+R go to the next and the previous clip, as now. Ctrl+R and
  Ctrl+Shift+R go to the first clip of the next and the previous pack. On
  macOS, the keys are Cmd+R and Cmd+Shift+R. Decided on 2026-11-13. Thus
  the user sees the full point with its serves. Now
  `AppShortcuts.displayName` knows only the Shift prefix, so it must also
  show Ctrl and Cmd.

### Labels

Decided on 2026-11-13:

- In advanced mode, each row has a label for its serve: "1st serve",
  "2nd serve", or "Let".
- After the label, the row shows the ending in a short form. For example:
  "1st serve · Fault", "2nd serve · Double fault", or
  "2nd serve · UE BH net".
- Basic mode shows no labels.

### Timeline

- The timeline shows the fault clips and the let clips dimmed.
- A clip of zero length shows as a thin mark.

## Scoreboard

- Decided on 2026-11-13: the scoreboard shows a small "2nd serve" mark
  during a 2nd-serve clip. Broadcasts often show this mark.
- All scoreboard styles show the mark, in the preview and in the export.
- Projects in basic mode have no serve numbers, so their scoreboard does
  not change.

## Statistics

New rows in the Stats tab, and on the statistics card of the export:

- Serve: aces, double faults, 1st serve in %, 1st serve points won,
  2nd serve points won.
- Endings: winners, forced errors, unforced errors, and the ratio of
  winners to unforced errors.
- Errors by stroke and by error kind, when the follow-up questions have
  answers.

Rules:

- The serve statistics use the serve numbers of the packs. A fault clip
  with video and a fault clip of zero length count the same.
- A point that the user scored in basic mode has no details. The advanced
  rows show their coverage, for example "Unforced errors: 12 (in 40
  detailed points)".

## Data model

The score file (`ScoreV1`) gets two fields. Old projects open with no
change: a missing field means "basic" and "no details".

```kotlin
val scoringMode: ScoringMode = ScoringMode.BASIC, // BASIC, ADVANCED. B-70 adds SHOT_BY_SHOT.
val details: Map<String, PointDetailV1> = emptyMap(), // key: the point id, as serverMarks

data class PointDetailV1(
    val ending: Ending? = null,      // SERVE_FAULT, LET, ACE, WINNER, FORCED_ERROR, UNFORCED_ERROR
    val stroke: Stroke? = null,      // FOREHAND, BACKHAND, VOLLEY, SMASH, DROP, RETURN
    val errorKind: ErrorKind? = null, // NET, LONG, WIDE
)
```

- The winner stays in `outcomes`. The ending key sets the winner. An ace
  and a double fault need the server, and the server is known from
  `serverMarks`.
- The player who made a winner or an ace is the point winner. The player
  who made an error is the other player. Thus the details need no player
  field.
- The details keep no serve number and no DOUBLE_FAULT ending. The app
  computes both from the packs (see "Packs"). Thus no stored value can
  disagree with the clips.
- A fault clip has the ending SERVE_FAULT. Its outcome is NONE, or the
  receiver when it is the double fault of its pack. A let clip has the
  outcome NONE and the ending LET.
- A clip of zero length is a `PointV1` with `startMs == endMs`. Now
  `PointsDispatcher` refuses clips shorter than 200 ms, and the scoring
  code ignores clips with `endMs <= startMs` (for example `setOutcome` and
  `switchServer` in `PointsScoring`). A fault clip of zero length must be
  an exception in each of these places.
- The time statistics skip clips of zero length.
- If the server of a point is not known, an ace and a double fault cannot
  give the winner. The app asks for the server first (see "The server").

## Shot by shot (B-70)

Decided on 2026-11-13: shot-by-shot mode is not in the scope of B-68.
Backlog item B-70 keeps it for later. The concept:

- The app knows who hit each shot. The server hits first, and then the
  sides alternate. This is also true in doubles and padel.
- The user presses a stroke key at each ball contact: 1 forehand,
  2 backhand, 3 volley, 4 smash, 5 drop shot, 6 lob. The first shot is the
  serve, and the app adds it.
- X and L work as in advanced mode. The first stroke key can start the
  point, so C is not necessary.
- Enter ends the rally. The strip then asks about the last shot: winner,
  forced error, or unforced error, and then the error kind. The app knows
  who hit the last shot, so it sets the winner. Q and E are not necessary.
- Each shot keeps its video time. The timeline shows the shots as ticks.
  The statistics get the rally length in shots.
- A rally strip shows the shots as chips in the player colors. A click on a
  chip changes its stroke. Delete removes the selected chip.
- Option: the video plays slower in this mode.
- Not planned now: the direction and the depth of each shot.

## Open questions

A review of the concept against the code (2026-11-13) found these
questions. None of them blocks the start of the work. Decide each one
before the work on the related epic starts.

1. Do Q and E work in advanced mode, as "winner only"? Without them, the
   user must select a wrong ending when the video does not show the end of
   the point.
2. One stroke list for B-68 and B-70. B-68 has "return" and no "lob". B-70
   has "lob" and no "return". A return is also a forehand or a backhand, so
   B-69 does not find "backhand unforced errors" on returns. Proposal: keep
   only strokes in the list. The app knows a return from the order of the
   shots.
3. The time statistics (average point, playing time, longest point, average
   gap) now use all clips. Fault clips and let clips make these values
   lower. Proposal: these statistics skip fault clips and let clips.
4. Basic mode with fault clips: are the fault clips hidden, or do they show
   as numbered "no point" clips? Does the list show the packs? Does the
   export option apply? Are W clips still numbered in advanced mode?
5. The hints: the app has no toasts. The existing hints are balloons that
   stay until the user closes them. Use balloons, or make a new toast?
6. Esc while a follow-up question is open: does Esc close the question
   only, or also cancel the pending point?
7. The row labels, for example "2nd serve · UE BH net", must fit in the
   fixed width of the side column in all app languages.
8. The digit keys: on an AZERTY keyboard (French), the top-row digits need
   Shift. Test the bindings on AZERTY, and also bind the numpad digits.
9. "Done when" of B-68 does not include E5 and E6. Are they part of B-68?

Decided on 2026-11-13. The decisions are in the sections above:

- The keys of the follow-up questions: see "Follow-up questions".
- Details for both players: see "Ending buttons".
- The default of "Include serve faults and lets": see "Export".
- Filter and export by detail, for example "all my backhand unforced
  errors": a separate backlog item, B-69.
- The server question and the confirmation of a server change: see "The
  server".
- No confirmation when other edits move the server: see "The server".
- X with no open point adds a clip of zero length: see "Serve faults and
  lets".
- The link between a fault clip and its point: see "Packs".
- The serve labels in the list: see "Labels".
- The serve number comes from the pack: see "Packs".
- Ctrl+R and Ctrl+Shift+R (Cmd on macOS) go to the next and the previous
  pack: see "Packs".
- The correction of mistakes, and the 2-second window of X: see
  "Corrections".

## Epics

| Epic | Content |
|---|---|
| E1 | The scoring mode switch (basic and advanced) and the data model |
| E2 | The ending buttons, the automatic winner, the "Who serves?" question, the confirmation of a server change |
| E3 | Serve faults and lets: X, L, the serve labels on the buttons, fault clips, clips of zero length, the corrections, the hints, the export option |
| E3b | The points list: packs, serve numbers from the packs, pack numbers, Ctrl+R and Ctrl+Shift+R (Cmd on macOS), the row labels with the endings, the timeline marks |
| E4 | The new rows in the Stats tab and on the statistics card |
| E5 | The follow-up questions |
| E6 | The "2nd serve" mark on the scoreboard |
