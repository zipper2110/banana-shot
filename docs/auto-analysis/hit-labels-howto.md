# How to mark hits, shot counts and first-serve faults

Gap program, stream S4 (G10). Journal: E70. Time: about 1 minute for each rally (measure it first, step 2).

## What you mark

For each rally clip:

- **Each hit** (racket touches the ball): the time and the player, **near** or **far** end of the court.
  The serve is hit 1. A ball toss with no hit is not a hit. A hit into the net is a hit.
- The shot count is the count of hits. The page counts them.
- **Lab matches only**: a first-serve fault before the point. The marks of these matches start at the
  second serve, so the clip has a pre-roll of up to 12 s. Igor has its faults as clips in the project, so
  for Igor you do not mark faults.

Mark only what you see or hear. If you are not sure of a mark, press Q. If the clip is not a rally
(no play, camera problem), press S.

## Start

1. Open `D:\bananashot-lab\ws\s4\label\label_hits.html` in Chrome or Edge (double-click, or drag it into
   the browser).
2. Press **O** and choose the folder `C:\w\banana-shot\tools\auto-analysis`. The page writes one file for
   each match there: `hit_labels_<match>.json`. Allow the browser to save changes. After a reload, the
   page asks for the permission again at the first Enter.
3. The page opens the first rally that is not done.

## Keys

| Key | Action |
|---|---|
| N | hit of the near player, at the frame on the screen |
| F | hit of the far player |
| Z (or Backspace) | undo the last mark |
| Enter | save, go to the next rally |
| Space | play or pause |
| ← → | one frame back or forward (Shift: 10 frames) |
| 1 2 3 4 | speed 1×, ½×, ¼×, ⅒× |
| R | play again from the rally start |
| P | play the pre-roll at 2× (fault check, lab matches) |
| X | first-serve fault before this point (at the frame of the fault serve); X again removes it |
| C | checked: no first-serve fault |
| Q | not sure (toggle) |
| S | skip: not a usable rally (toggle) |
| M | note |
| V | zoom 2× at the mouse position (toggle) |
| [ ] | previous or next rally, no save |

Click the timeline to go to a time. Click a mark in the list to go to it; click × to delete it.

## Fast method

1. Speed ½× or ¼×. Press N or F at each contact while the clip plays.
2. A key press during playback is late by your reaction time (about 0.2 s × speed). For exact times,
   pause near the contact, step with ← →, and press N or F at the contact frame. You can also press Z
   and mark the hit again.
3. Lab match: press P to see the pre-roll, then X at the fault serve, or C if there is no fault.
4. Enter.

## Work list

180 rallies on 5 matches. The page shows the list (right side) and the progress, the median time for each
rally and the time that is left.

| Order | Match | Rallies | Note |
|---|---|---|---|
| 1 to 10 | Igor (m1116) | 10 | **Timing batch: do these first, then tell the manager the median time** |
| 11 to 60 | Igor (m1116) | 50 | Held-out match (test only) |
| 61 to 180 | m1015, m1020, m1021, m1030 | 30 each | In turns of 6 rallies of each match |

Each match has 1/3 short, 1/3 middle and 1/3 long rallies (thirds of the marked lengths of that match).
The rallies come from your marked points, not from the detection. Target: 150 rallies or more.
You can stop at any time; the work is saved after each Enter.

## Safety of the data

- Each Enter writes the file of the match and a copy in the browser storage.
- If the folder does not work, the **Download** button saves the files to the download folder. Send them
  to the manager.
- Do not edit the JSON files by hand.

## For the agents

- Clips and the work list: `python tools/auto-analysis/label_clips.py list | cut | page`
  (`hit_worklist.json`, clips in `D:\bananashot-lab\ws\s4\label\clips`). Video time = clip `t0` + clip time.
- Output: `hit_labels_<match>.json`, `version` 1, `kind` `bananashot-hit-labels`, `source` (tool, version,
  work list, method), one row for each rally: `hits` (`t` in source video seconds, `player`, `rate`,
  `paused`), `shots`, `fault`, `unsure`, `skip`, `note`, `labelSeconds`.
- m1116 is `testOnly`: use its labels to measure, never to train or fit.
