# B-68 Point details

Status: concept (decided with the author on 2026-11-13). No code yet.

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
| Shot by shot | Each shot, and how the rally ended | Later. See "Shot by shot". |

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

The questions are optional. C (the next serve start) or R (next point)
skips them. The keys of the questions are open question 1.

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
  - On a 2nd serve, X ends the clip as a double fault. The receiver wins.
- X with no open point records a 1st serve fault without a clip. The next
  serve is a 2nd serve. This is for users who do not want the faults in
  the video and do not press C at a 1st serve. It is also for a missed C.
  A user can mix the two ways in one match.
- X with no open point on a 2nd serve does nothing. A double fault needs a
  clip, because the score needs a point. A hint tells the user to press C
  at the 2nd serve start.
- L ends the open clip as a let clip. The serve number does not change.
- L with no open point does nothing. A hint shows (see "Hints").
- Fault clips and let clips have the outcome "no point". The score does not
  change. The time between the clips is cut, for example the time when the
  players take the ball off the court.
- Esc cancels the pending point, as now. With no pending point, Esc
  cancels a fault that X recorded without a clip.

### Labels that show the serve number

- The start button shows "1st serve" or "2nd serve". It keeps the C key
  chip. Basic mode keeps "Point start".
- The X button shows "Serve fault" on a 1st serve and "Double fault" on a
  2nd serve.
- After a fault, the label changes to "2nd serve". After a let, it does not
  change. After an ending or a double fault, it goes back to "1st serve".
  L and W do not change the serve number.
- The serve number comes from the clip before the playhead, not from one
  global state. Thus the label is correct after the user goes back to an
  earlier part of the match.
- The short labels must fit in all app languages and in the narrow form of
  the point bar.

### Hints

- The hints are toasts, not dialogs. A toast does not stop the video and
  does not take the keyboard focus.
- Each hint has "Don't show again". The hints use the existing hint system,
  so "Show all hints again" in More → Settings shows them again.
- L with no open point: "A let without a point start has no effect. Press C
  at the serve start, then L, to keep the let in the video."
- X with no open point on a 2nd serve: "Press C at the 2nd serve start,
  then X, to record the double fault."
- The same pattern can cover other keys that do nothing, with one hint for
  each key.

## Export

- The Export tab has a new option: "Include serve faults and lets".
- On: the "Only points" and "Only favorites" exports include the fault
  clips and the let clips.
- Off: the exports skip them. The video has only the serve that counts, as
  the author marks it now.
- Each project has the option. Settings has the default for new projects.
- The fault clips are always in the data. Thus the user can change the
  decision after the marking, and can make two exports from one marking.
- A fault that X recorded without a clip has no clip to include.
- "Only favorites" uses the favorite flag of the point that the fault clip
  belongs to.

## Points list and navigator

- Fault clips and let clips do not count as points. The navigator does not
  number them. For example, "Point 12" has a "1st serve" sub-row.
- The timeline shows the fault clips and the let clips dimmed.
- The points table gets a short code column, for example
  "2nd · UE BH net".
- The scoreboard can show a small "2nd serve" mark during a 2nd-serve
  clip (optional).

## Statistics

New rows in the Stats tab, and on the statistics card of the export:

- Serve: aces, double faults, 1st serve in %, 1st serve points won,
  2nd serve points won.
- Endings: winners, forced errors, unforced errors, and the ratio of
  winners to unforced errors.
- Errors by stroke and by error kind, when the follow-up questions have
  answers.

Rules:

- The count of 1st serve faults comes from the points on a 2nd serve, not
  from the fault clips. Thus a fault with a clip and a fault without a clip
  count the same, and no fault counts two times.
- A point that the user scored in basic mode has no details. The advanced
  rows show their coverage, for example "Unforced errors: 12 (in 40
  detailed points)".

## Data model

The score file (`ScoreV1`) gets two fields. Old projects open with no
change: a missing field means "basic" and "no details".

```kotlin
val scoringMode: ScoringMode = ScoringMode.BASIC, // BASIC, ADVANCED, SHOT_BY_SHOT
val details: Map<String, PointDetailV1> = emptyMap(), // key: the point id, as serverMarks

data class PointDetailV1(
    val serve: ServeNumber? = null,  // FIRST, SECOND
    val ending: Ending? = null,      // SERVE_FAULT, LET, DOUBLE_FAULT, ACE, WINNER, FORCED_ERROR, UNFORCED_ERROR
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
- The app sets `serve` when a point starts. It does not compute it later
  from the order of the clips. Thus a moved or deleted clip does not move a
  2nd serve to the wrong point.
- A fault clip has the outcome NONE and the ending SERVE_FAULT. A let clip
  has the outcome NONE and the ending LET.
- If the server of a point is not known, an ace and a double fault cannot
  give the winner. The app asks for the server first.

## Shot by shot

This mode comes after advanced mode. The concept:

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

1. The keys of the follow-up questions. The ending keys use the digits. One
   option: while a question is open, the digits answer the question.
2. Details for both players, or an option "record details only for player
   1" for faster marking. The proposal is both players.
3. The default of "Include serve faults and lets" for new projects. The
   proposal is off, so the video stays as it is now.
4. Filter and export by detail, for example "all my backhand unforced
   errors". It is good for training. It is not in the epics yet.

## Epics

| Epic | Content |
|---|---|
| E1 | The scoring mode switch and the data model |
| E2 | The ending buttons, the automatic winner, the code column in the points table |
| E3 | Serve faults and lets: X, L, the serve labels, fault clips, X without a clip, the hints, the export option |
| E4 | The new rows in the Stats tab and on the statistics card |
| E5 | The follow-up questions (after open question 1) |
| E6 | Shot-by-shot mode |
