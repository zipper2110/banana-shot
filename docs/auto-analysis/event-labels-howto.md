# How to mark the events of the case library fragments

Gap program, stream S4, item 10. Page: `label_events.html`. Fragments: the case library of S1 (item 8).

## Start

1. Open `D:\bananashot-lab\ws\s4\events\label_events.html` in Chrome or Edge (double-click the file).
2. Press **O** and select the folder `D:\bananashot-lab\ws\s1\cases\labels`. Allow the page to edit files.
   You do this one time. The page then writes one file for each fragment: `<fragment>.events.json`.
   If the header shows a red banner "NO LABEL FOLDER" (or "needs permission"), the marks are not in files:
   click the banner or press **O**. The page then writes all the fragments that you saved before.
   If the page says "the fragment list changed", reload the page (F5). It does not save until then.
   If the list shows "old marks, not used" for a fragment, its clip was cut again. Mark it again from zero.
   A medium fragment can start with your marks of the small fragments ("mapped marks", tag "from s0xx"). Check them,
   delete a wrong one (×) and add the missing events. Enter saves them as your marks of this fragment.
3. The first fragment plays at ½× speed. Press **Space** to stop it.

The page measures your time on each fragment. Please do the first 5 fragments, then stop. The manager uses
your time on them to estimate the time for the rest.

## What you mark

Mark all the events in the clip. Each mark is at the frame on the screen when you press the key. Stop the
video, and use **← →** to find the correct frame (**Shift+← →**: 10 frames).

| Event | Key | Where |
|---|---|---|
| Serve, near or far player | **Shift+N** or **Shift+F** | the frame where the racket touches the ball |
| The serve is a first-serve fault (optional: only if the scripts cannot see it) | **X** after the serve mark | see "The fault flag" below |
| Each other shot, near or far player | **N** or **F** | the frame where the racket touches the ball |
| Bounce, in | **B** | the first frame where the ball touches the ground. Put the mouse on the ball, then press the key |
| Bounce, out | **Shift+B** | as for B |
| Hidden bounce, in or out | **I** or **Shift+I** | you cannot see the bounce, but the flight before and after it shows where it is. Put the mouse at that place, at the frame where it is |
| Net touch: the ball stops at the net | **T** | the frame of the touch |
| Net cord: the ball touches the net and goes over | **Shift+T** | the frame of the touch |

Rules:

- A ball toss with no hit is not a serve. A let serve: do not mark it. Press **M** and write "let".
- Mark each bounce of the ball in the clip, also the second bounce at the end of the rally, if you can see it.
- Out: the ball lands outside the lines for that shot. For a serve, the correct service box is the line.
- A double fault: two serves. Mark the out bounce or the net stop of each serve.
- The page starts at the label window, not at the start of the clip. The label window starts 3 s after the end of the
  previous point, but not earlier than 25 s before the marked point. Mark all events in the label window, also
  the events after the marked point, if you see them.
- The part of the clip before the label window shows the previous point. The time line shows it dark, and the page
  shows "before the label window: no marks here". Do not mark events there. A key press there adds no mark.
  Marks from an earlier save that are there stay in the file. The page shows them as "not used".
- Lab matches: the marked point starts at the second serve. A first-serve fault before it is inside the label window.
  Mark it.
- If you are not sure of a mark, press **Q**. If the fragment cannot be used, press **K** (skip).

The fault flag: you do not have to press **X**. The scripts (`label_faults.py`, E159) find a first-serve fault from
your marks: an out bounce or a net stop after the serve, then the second serve from the same end (the same
service box, or a short time gap). Press **X** only if the scripts cannot see it, for example when you cannot
mark the out bounce or the net stop. The scripts keep each **X**. If they are not sure, they count that serve apart
and do not use it as truth. Nobody asks you about it.

## Help in the page

- **1 2 3 4**: speed 1×, ½×, ¼×, ⅒×. **R**: play again from the start of the label window. **U**: sound on or off.
- **V**: zoom 2× at the mouse. **G**: zoom 3× at the far baseline. Press the same key again to go back.
- **Z**: undo the last mark. The list "Marks" shows all marks. Click a mark to go to its frame, click × to delete it.
- The small court shows each bounce from above (far end up). Use it to check a bounce place.
- A circle on the video shows each mark near the current frame.
- **Enter**: save and go to the next fragment. The page shows a warning if two contacts of the same player
  come one after the other, or if the first contact is not a serve. Check it, then press Enter again to save.
- **[ ]**: previous or next fragment, with no save.
- If the folder does not work, the button **Download** gives the file of the current fragment.

## Output (for the agents)

One file for each fragment, `<id>.events.json`. Events in source time `t` (s). Bounces have `px` (the ball pixel
in a 1920-wide frame) and `court` (m, x across, y along, far end > 0, from the court of the fragment at height 0),
`out`, and `seen` (false = inferred). The file also has the tool version, the labeler, the fragment id, the hash of
its manifest row, and `labelSeconds` (the active time).

## Sides step: who is near (item 11)

The app finds "who is near" from the end-change rule. Players do not always follow the rule (for example after a
long break). In this step you say who plays at the near end (the bottom of the picture) at some points of each match.

1. Open the same page, then click the button **Sides** (top right). The page shows one still frame of a point.
2. Press **1** or **2** for the player who is near. The buttons show the player names of the project.
   If you cannot see it, press **Q** (not sure). The page then shows the next point.
3. If the frame does not show it clearly, press **W** for a second frame (1 s after the point start).
   **V**: zoom 2× at the mouse.
4. At the end of a match, the page goes to the next match. **[ ]**: previous / next match. **← →**: previous /
   next point. Click a point in the list to change its mark.

There are 12 to 23 points in each match (5 matches). It takes about 2 to 3 min for each match. The page measures
your time. The page writes `sides_<match>.json` in the same label folder (the folder of the **O** key).

## Faults step: rows before a marked point (E158)

The lab marks start at the second serve. Thus the bench does not know the truth of some detected rows: a row that
has no mark and ends 25 s or less before the next marked point. The row can be a first-serve fault, a real point
that has no mark, or a false rally. In this step you say which.

1. Open the same page, then click the button **Faults** (top right). The page plays a short clip: from 1 s before
   the row to 1 s after the start of the next marked point.
2. The time line under the video shows the detected row (yellow, top) and the next marked point (green, bottom).
3. Press one key. The page then shows the next row.
   - **F**: a first-serve fault. The next marked point starts with the second serve.
   - **P**: a real point (a rally) that has no mark.
   - **N**: not a fault and not a point (for example no serve, a warm-up, or a ball back to the server).
   - **Q**: not sure.
4. **Space**: play / stop. **R**: play again from the start. **← →**: 1 s back / forward (Shift: 5 s).
   **1 2 3 4**: speed. **V**: zoom 2× at the mouse. **U**: sound on / off.
5. **[ ]**: previous / next row. **PgUp PgDn**: previous / next match. Click a row in the list to change its answer.

There are 27 rows in 4 matches. It takes about 5 to 10 min. The page measures your time. The page writes
`faults_<match>.json` in the same label folder (the folder of the **O** key).

Output (for the agents): `faults_<match>.json` has `answers` (for each row: `row`, `rowHash`, `start`, `end` in
source s, `next` (the id of the next marked point), `nextStartMs`, `answer` F, P, N or Q, `at`), `counts`,
`rowCount`, `complete`, `labelSeconds` and `stale`. An answer counts only for the same `rowHash` as in `faults.js`. The row hash is `faults_cases.row_hash`: sha256 of the sorted JSON of match, start and end of the bench row (rounded to 3 decimals) and the id of the next marked point; the first 16 hex characters. A new cut of a clip does not change it.
When the row list changes, the page moves the answers of changed or removed rows to `stale`; they are not used.
The input is `faults.js` of `faults_cases.py page`.
