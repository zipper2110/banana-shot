# B-70 Shot-by-shot scoring mode

Status: concept. Out of scope for now. Do this after B-68. No code yet.

## Goal

A third scoring mode for tennis. The user marks each shot of a rally with
its stroke. The app then knows the strokes, the rally length in shots, and
the stroke that ended the point.

## Relation to B-68

- B-68 (`docs/point-details/b-68-point-details.md`) makes the basic mode
  and the advanced mode. This mode is the third mode of the same switch.
- The modes are nested. Shot-by-shot data gives the advanced data, and the
  advanced data gives the winner. Thus the statistics read one model.
- This mode uses these parts of B-68 with no change: the server rules, the
  packs, the serve numbers from the packs, X, L, the corrections, and the
  export option for faults and lets.
- Decided on 2026-11-13: advanced mode records no strokes. Strokes come
  only from this mode.

## Concept

- The app knows who hit each shot. The server hits first, and then the
  sides alternate. This is also true in doubles and padel.
- The user presses a stroke key at each ball contact: 1 forehand,
  2 backhand, 3 volley, 4 smash, 5 drop shot, 6 lob. The first shot is the
  serve, and the app adds it.
- The return is the first shot of the receiver. The app knows it from the
  order of the shots, so the stroke list has no "return".
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

1. The final stroke list. For example: is a slice a separate stroke, or a
   forehand or a backhand? Is "lob" a stroke, or a kind of forehand or
   backhand?
2. The digit keys: advanced mode uses the digits for the ending buttons.
   This mode uses them for the strokes. Each mode has its own key map. How
   does the point bar show the keys of the active mode?
3. The data model: a list of shots for each point clip, with the video time
   and the stroke of each shot.
4. The statistics: errors and winners by stroke, the rally length in shots,
   and the stroke of the return.
5. The filter of B-69: can it filter by stroke, for example "all my
   backhand unforced errors"?
6. Padel has other strokes, for example the bandeja and the víbora. Does
   padel get this mode, and with which list?
