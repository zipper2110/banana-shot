# Review UI audit (A57, S2, 2026-11-28)

The question (gap-program.md section 1, "Business value", value box 1): can the user make a detected match fully
correct in the app, and much faster than marking it by hand? This audit reads the app code only. It changes no code.

Code base: branch `auto-detect` at da2954ba. The paths start at `src/main/kotlin/org/litvin/`.

The review happens in the window "Review the detected points" (`ui/tabs/points/detect/ReviewWindow.kt:372`). The
Points tab opens it (`ui/tabs/points/SwingPointsPanel.kt:1454`; the button in `ui/tabs/points/detect/DetectUi.kt:246`).
The window has the video, a bounds bar under the video, the evidence text of the detection, and a list of the points
at the side. Each change goes into the project at once (`ReviewHost`, `ReviewWindow.kt:67`; the project side is
`SwingPointsPanel.kt:239` to `305`).

## The review steps

| Step | Supported now | How (screen, action, file and line) | Gap |
|---|---|---|---|
| Check and correct the rally bounds | Yes | Review window, bounds bar: drag the start edge or the end edge (`ReviewBoundsBar`, `ReviewWindow.kt:128`; the commit `ReviewWindow.kt:1077`, then `SwingPointsPanel.kt:248`). Keys I and O and the buttons "Start here" and "End here" put an edge at the playhead (`ReviewWindow.kt:842`, `843`, `683`, `685`, `1067`, `1072`). Left and Right move the playhead by 1 s, with Shift by 5 s (`ReviewWindow.kt:836` to `839`, `1428`). The shot tracks under the bar show the hits and the shot ends (`setShots`, `ReviewWindow.kt:185`). Space plays the point and stops at its end (`ReviewWindow.kt:1012`). A bounds change also marks the point as reviewed (`ReviewWindow.kt:1083`). | Small. The bar shows only 8 s before and after the point (`WINDOW_PAD_MS`, `ReviewWindow.kt:1432`). A start that is more than 8 s earlier needs the playhead and the I key. |
| Check and correct the outcome | Yes for the winner; no for the ending kind | Review window: the winner choice (player 1, "No winner", player 2; `ReviewWindow.kt:526`), keys Q, W and E (`ReviewWindow.kt:845` to `847`; `setWinner`, `ReviewWindow.kt:1120`, then `SwingPointsPanel.kt:242`). The evidence text gives the reason for the winner (`ui/tabs/points/detect/EvidenceTexts.kt`). The list shows the winner of each point (`ReviewWindow.kt:1380`). T accepts the point and goes to the next one (`ReviewWindow.kt:1129`). | The review sets the winner with no ending kind (`ReviewWindow.kt:79`). The ending kind (ace, double fault, winner, error) is set only in the Points tab. Box 1 needs the winner, so this is not a box 1 gap. |
| Check and correct the server player | Partly | The review shows the server of the detection only as text in the evidence ("Server: <name> (near end)", `EvidenceTexts.kt:39`). The "Near end" line and "Swap players" correct a wrong player identity: the other player and the other winner for one point, the next points or all points (`ReviewWindow.kt:519`, `537`, `1090`; `SwingPointsPanel.kt:302`). The server of the score can be changed only in the Points tab: the S key or the serve button sets a server mark (`switchServer`, `ui/tabs/points/PointsScoring.kt:702`). | (1) The review has no server control. (2) The import does not write server marks (`autodetect/DetectImport.kt` has no `serverMarks`), and without a mark no point has a server in the score (`scoring/ScoringEngine.kt:88`). So after the import, the score has no server, the serve statistics are empty, and the game check (below) has no server to compare. |
| Decline a false rally | Yes | Review window: the "Delete" button or the Delete key (`ReviewWindow.kt:535`, `849`; `deleteSelected`, `ReviewWindow.kt:1138`), then a confirmation dialog (`ReviewWindow.kt:1141`). The project deletes the clip (`SwingPointsPanel.kt:255`). | Small. Each false rally needs the dialog (two actions). The deleted point leaves no record, and its detection note goes with it, so the lab cannot count the false rallies that the user declined. |
| Add a missing point | Partly | Review window: the "Add point" button or the N key adds a point of 5 s at the playhead (`ReviewWindow.kt:536`, `848`; `addAtPlayhead`, `ReviewWindow.kt:1159`; `NEW_POINT_MS`, `ReviewWindow.kt:1433`), then the user drags its edges. To get to the place, the user plays on after a point (Space outside a point does not stop, `ReviewWindow.kt:1024`) or moves the playhead with the arrow keys or a click on the bar. | The user gets no clue where a point is missing. The bar shows only 8 s around the selected point, and the review has no timeline of the full match and no "next gap" step. So the user must go through the gaps between the points by hand. |
| Clue: the game check (the server change at each game end) | No | Nothing in the app compares servers with games. The review list shows the number, the start, the length and the winner of each point (`ReviewWindow.kt:1325`, `1373` to `1386`): no score, no game, no server. The Points tab knows the game ends (the GAME badge, `PointsScoring.kt:395`) and the server only from user marks (`ScoringEngine.kt:88` to `95`). The detection has a server for each rally (`autodetect/DetectModel.kt:119`, `133`: `server`, `serverNear`). | All of it: the compare (the server of the score against the server of the detection, game by game), the list of the games that do not agree, and the UX to go to such a game and to close it. |
| Clue: the time gaps between the points of a game | No | The review list shows the start and the length of each point, but not the gap to the point before it (`ReviewWindow.kt:1373` to `1379`). | All of it: the gap of each point, a mark on a gap that is much longer than the other gaps of its game, and a step to go to the next such gap. |

## Proposal: the smallest UI changes, in order

Each change is in the review window, except 2b (the import). No implementation before the user's OK.

1. **Time gaps (small; no scoring change).** The review list gets the gap to the point before it, for example
   "+38 s". A gap gets a yellow mark when it is much longer than the gaps of its neighbors (for example more than
   twice the median of the 10 points around it, and more than 25 s; the numbers are a proposal). A key (G) and a
   button "Next long gap" put the playhead at the start of the next marked gap and play it. N then adds the missed
   point there.
2. **The server in the review and in the score.**
   - a. The review shows "Serves: <name>" (the server of the score) next to "Near end", with the S key, as in the
     Points tab (the same `switchServer` logic through `ReviewHost`).
   - b. The import writes one server mark: on the first point with a known detected server (or on each point where
     the detection is sure). Then the score has servers at once.
3. **The game check (after the winners are reviewed).** For each game, the app compares the server of the score
   with the server of the detection. One missed point (or one false point) moves each later game end by one point,
   so the server of the next game does not agree. The first game that does not agree marks the place: the point
   is most probably missing in the game before it. The longest gap of that game (step 1) is the first place to look.
   UX options:
   - **Option A: a check step at the end.** "Accept all points and close" first runs the check. When games do not
     agree, a panel says, for example, "2 games do not agree with the server". The list then shows only these games.
     Each item goes to the game and plays its longest gap. The user adds the point, or clicks "No point is
     missing here", which closes the item.
   - **Option B: game rows in the list.** The list gets a row at each game end with the score ("Game 4: 3-1 Ana")
     and the server of the next game. A red mark shows a game where the server of the next game does not agree.
     A click on the row plays the longest gap of the game. The check updates after each change.
   Option A is the smaller change and keeps the list as it is now. Option B shows the problem at the time the user
   reviews the game, but it needs the score in the review list.
4. **Decline in one action.** Delete with no dialog for an automatic point, with Undo (Ctrl+Z) in its place. The
   project records the declined automatic points (time and detection data), so the lab can count the false rallies.
5. **A new point at the gap.** "Add point" in a marked gap uses the full gap (from the end of the point before it
   to the start of the next point, minus a margin) in place of 5 s, so that the user only moves the two edges in.

Order: 1 first (the smallest change, and it gives a clue at once), then 2 (the game check needs the server), then 3,
then 4 and 5.
