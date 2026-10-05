# B-38 Tasks: Minor Findings of the Feature Audit

This file tracks the work of B-38. B-38 fixes the minor and super-minor
findings (F-04 to F-17) of the feature audit of 2026-10-05. The backlog item
is in `docs/backlog.md`.

## Goal

Each minor and super-minor finding of the audit is fixed, or it has a
decision to keep the current behavior.

## Decisions

| # | Decision |
|---|---|
| 1 | Decided on 2026-10-05: do this work after the first release. One backlog item (B-38) holds all the findings. |

## Open questions

These questions need a decision before the related task starts. Write each
decision in "Decisions" at once.

| # | Task | Question |
|---|---|---|
| Q1 | T1 | Delete a point: ask for a confirmation, or delete at once and give an undo (Ctrl+Z, or an "Undo" link in a status message)? |
| Q2 | T3 | Scoreboard checkbox: keep the choice of the user for each project, as the other checkboxes do? Or set it from the score data only for a project that has no saved choice? |
| Q3 | T4 | Points tab precision: add only a frame step (the same key as in the Scoring tab), or also a timeline zoom? |
| Q4 | T7 | Scoreboard sets: show all completed sets, or let the user select the number in the scoreboard style? |
| Q5 | T8 | Final result: show the final score after the match point until the end of the last point, or for a fixed time (for example 5 s)? |
| Q6 | T9 | Deciding set: add a rule "10-point tiebreak at 6–6 in the deciding set" and use it in the "Best of 5 sets" preset? Or only change the preset text? |
| Q7 | T13 | Comments between points in "Only points": warn the user, move the comment to the start of the next point, or both? Also cut a comment at the end of its point? |

## Tasks

| Task | Finding | Severity | Scope | Depends on | Status |
|---|---|---|---|---|---|
| T1 | F-04 | Minor | Safe delete of a point | Q1 | open |
| T2 | F-05 | Minor | Tell the user about a skipped overlay or card | — | open |
| T3 | F-06 | Minor | Keep the Scoreboard checkbox choice | Q2 | open |
| T4 | F-07 | Minor | Precise marking in the Points tab | Q3 | open |
| T5 | F-08 | Minor | Refuse an export to the source video path | — | open |
| T6 | F-09 | Minor | Show a failed save of the adjustments | — | open |
| T7 | F-10 | Super-minor | Show more sets on the scoreboard | Q4 | open |
| T8 | F-11 | Super-minor | Show the final result on the scoreboard | Q5 | open |
| T9 | F-12 | Super-minor | Grand Slam deciding-set tiebreak | Q6 | open |
| T10 | F-13 | Super-minor | Clear the pending Start | — | open |
| T11 | F-14, F-15 | Super-minor | Reload the projects list; show damaged projects | — | open |
| T12 | F-16 | Super-minor | Set a point back to "not scored" | — | open |
| T13 | F-17 | Super-minor | Comments between points in "Only points" | Q7 | open |

Status values: `open`, `in-progress`, `done`. When a task is done, write
the test classes in its "Tests" line. Do not remove the task.

## T1 Safe delete of a point (F-04)

- The Delete key and the row Delete button remove a point at once. During
  playback, the selection follows the playhead. The next visit of the
  Scoring tab also removes the outcome and the marks of the point.
- Delete in the Edit dialog asks for a confirmation. The workaround now:
  mark the point again and give it a winner again.
- Make the three delete paths (key, row button, Edit dialog) behave the
  same. The behavior depends on Q1.
- Code: `ui/tabs/points/ui/Keybindings.kt:50`,
  `ui/tabs/points/SwingPointsPanel.kt:658-669`, `:698-719`.
- Tests:

## T2 Tell the user about a skipped overlay or card (F-05)

- When the app cannot write the overlay file, or a statistics card fails,
  the export continues without it. The export ends as "completed".
- This is one case of B-39 (silent errors). Fix it here, and mark it in the
  B-39 list.
- Keep the export. Mark the job as "completed with warnings" and show the
  reason in the Completed list. Write the error to the log.
- Code: `RenderQueue.kt:301-306`, `RenderQueue.kt:455-460`.
- Tests:

## T3 Keep the Scoreboard checkbox choice (F-06)

- Each visit of the Export tab sets the Scoreboard checkbox again from the
  score data. The behavior depends on Q2.
- Code: `ui/tabs/export/SwingExportPanel.kt:101`, `:530-532`.
- Tests:

## T4 Precise marking in the Points tab (F-07)

- The Points tab moves the playhead only by 1 s or 5 s. The Scoring tab has
  a frame step (F). The scope depends on Q3.
- Use the frame step code of the Scoring tab. Use the same keys in both
  tabs. Update the help text in `HelpCatalog`.
- Code: `ui/tabs/points/ui/Keybindings.kt:44-58`,
  `ui/tabs/points/ui/SwingTimelineComponent.kt:195`.
- Tests:

## T5 Refuse an export to the source video path (F-08)

- The final move of an export can replace the source video.
- In the save dialog, refuse the path of the source video of the project.
  Compare the canonical paths, not case-sensitive on Windows. Show a
  message and keep the dialog open.
- Also check the path in `RenderQueue` before the final move. A saved queue
  can have an old job with this path.
- Code: `ui/tabs/export/SwingExportPanel.kt:402-416`, `RenderQueue.kt:543`.
- Tests:

## T6 Show a failed save of the adjustments (F-09)

- `AdjustmentsStore` ignores a save error. The Points and Scoring tabs show
  "Autosave failed" for the same problem.
- This is one case of B-39 (silent errors). Fix it here, and mark it in the
  B-39 list.
- Give the error to the Colors and the Transform tabs. Show the same
  "Autosave failed" status there.
- Code: `AdjustmentsStore.kt:96-101`.
- Tests:

## T7 Show more sets on the scoreboard (F-10)

- The scoreboard shows only the last 2 completed sets. The scope depends on
  Q4. Check that the wider scoreboard fits in each style and at each export
  resolution.
- Code: `ScoreboardDisplay.kt:33`.
- Tests:

## T8 Show the final result on the scoreboard (F-11)

- The scoreboard shows the score before each point. The video never shows
  the final result. The behavior depends on Q5.
- Code: `ScoreboardTimeline.kt` (`computeSnapshotsBefore`).
- Tests:

## T9 Grand Slam deciding-set tiebreak (F-12)

- The "Best of 5 sets" preset says "Grand Slam format", but its deciding
  set has a 7-point tiebreak. Grand Slam tournaments use a 10-point
  tiebreak at 6–6. The scope depends on Q6.
- A new rule changes the score of saved projects. Keep the old rule for
  projects that have a saved match format.
- Code: `scoring/MatchRules.kt:128-132`.
- Tests:

## T10 Clear the pending Start (F-13)

- A pending Start (C without V) stays when a different project opens.
- Clear the pending Start in `setPoints`. Let Esc clear the pending Start
  (`clearPending()` has no caller now). Update the help text.
- Code: `PointsDispatcher.kt:136-140`,
  `ui/tabs/points/SwingPointsPanel.kt:459-489`.
- Tests:

## T11 Reload the projects list; show damaged projects (F-14, F-15)

- F-14: a click on the Projects tab does not scan the projects folder
  again. `RecentsProvider.current()` returns the cache. Scan the folder
  again on the click, as the comment in `SwingApplicationFactory.kt:328-331`
  says. Read the memory note about the UI-flow tests first: the click must
  activate the current card again.
- F-15: a project with a damaged manifest is not in the list, and the user
  gets no message. Show a row "Damaged project" with the folder path and an
  "Open folder" action.
- Code: `projects/RecentsProvider.kt:39`, `:52`,
  `projects/ProjectsRepository.kt` (`getRecents`).
- Tests:

## T12 Set a point back to "not scored" (F-16)

- After a point has an outcome, the user cannot remove it. "No point"
  counts as scored in the "Scored x/y" figures.
- Add an action that clears the outcome (for example Backspace, and a
  button in the score panel). Update the help text.
- Code: `ui/tabs/scoring/SwingScoringPanel.kt:777-788`,
  `ui/tabs/scoring/ui/ScorePanel.kt:105-113`.
- Tests:

## T13 Comments between points in "Only points" (F-17)

- A comment that starts between two points is not in the video, and the
  user gets no warning. A comment that starts in a point can continue over
  the next points. The behavior depends on Q7.
- Code: `export/ExportPlanner.kt:299-340`.
- Tests:
