/* The texts come from HelpCatalog.kt (generated for the mockup). */
window.HELP = [
 {
  "id": "OVERVIEW",
  "title": "Overview",
  "summary": "Turn a full tennis recording into a compact, scored video without changing the source file.",
  "workflow": [
   "Import a source video or open a recent project.",
   "Mark the start and end of each point, and add comments where necessary.",
   "Assign each marked point to a player or mark it as no point.",
   "Review the match statistics, and select the statistics for the video.",
   "Optionally tune the color and frame the video with Transform. You can do this at any time.",
   "Choose export settings and export the finished video."
  ],
  "actions": [
   "Press F1 on a tab to open the help for that tab.",
   "Select a page in the list on the left to read the help for a different tab.",
   "In the sidebar, the Match tabs go in order. Each Match tab uses the data of the tab above it. The Video tabs change the image, and you can use them at any time."
  ],
  "goodToKnow": [
   "Projects, point marks, comments, adjustments, and scoring are saved in project files.",
   "Edits are non-destructive: the original video is only read, never rewritten.",
   "Most editing changes autosave as you work."
  ],
  "shortcuts": [
   [
    "F1",
    "Open Help for the current tab"
   ]
  ]
 },
 {
  "id": "PROJECTS",
  "title": "Projects",
  "summary": "Create a project from a video or reopen a match you worked on earlier.",
  "workflow": [
   "Choose Import new match in the New project panel and select a supported video file.",
   "In the New project dialog, change the project name or the video if necessary, then choose Create project.",
   "Choose Open on a recent project to continue working.",
   "The opened project becomes the current project, unlocks the editing tabs, and opens the Points tab."
  ],
  "actions": [
   "Click a row of the recent projects as an alternative to Open. The open project has no Open button.",
   "Use the pencil button on a project row, or Rename on the current project, to rename the project.",
   "Read the video path, the video duration, the file size, the scored points of all points, and the favorite points in the columns of each recent project. The bar in the Scored column shows the progress of the scoring.",
   "Use the arrow buttons next to the page number when the list of recent projects has multiple pages.",
   "Use the trash button on a project row to delete the project."
  ],
  "goodToKnow": [
   "When you delete a project, the video file stays on the disk.",
   "You cannot delete the open project.",
   "\"Video not found\" shows in red when the video of a project is not on the disk. Put the video back at the same location to open the project."
  ],
  "shortcuts": [
   [
    "F1",
    "Open Projects help"
   ]
  ]
 },
 {
  "id": "POINTS",
  "title": "Points",
  "summary": "Mark the video interval for every point that should be kept, and add comments to the video.",
  "workflow": [
   "Move the playhead to the beginning of a point and set Point start in the Mark a point panel.",
   "Move to the end and set Point end to create a marked point.",
   "Click a row in the Points & events table to seek, then edit, delete, or mark favorites as needed.",
   "Move the playhead to the moment you want to explain and click Add comment.",
   "Give the comment its text, start time, duration, and color, then save it."
  ],
  "actions": [
   "Click the timeline to seek. The MARKS track shows the marked points; favorites are yellow.",
   "Point at a point row to show its Favorite, Edit, and Delete actions.",
   "Point at a marker on the COMMENTS track to read the comment, or click the marker to seek.",
   "Point at a comment row to show its Edit and Delete actions. Edit also changes the color."
  ],
  "goodToKnow": [
   "While a point is pending, the Mark a point panel shows its start, and the timeline shows it as a hatched range.",
   "The export shows each comment on the video for its duration.",
   "Changes autosave to the current project.",
   "To delete a comment, use the Delete button on its row. The Delete key deletes points only.",
   "Up and Down change speed when focus is in the video area."
  ],
  "shortcuts": [
   [
    "Space",
    "Play or pause"
   ],
   [
    "C",
    "Set point start at the playhead"
   ],
   [
    "V",
    "Set point end at the playhead"
   ],
   [
    "A",
    "Toggle favorite on the selected point"
   ],
   [
    "Delete",
    "Delete the selected point"
   ],
   [
    "Left",
    "Seek back 1 second"
   ],
   [
    "Right",
    "Seek forward 1 second"
   ],
   [
    "Shift+Left",
    "Seek back 5 seconds"
   ],
   [
    "Shift+Right",
    "Seek forward 5 seconds"
   ],
   [
    "Up",
    "Increase playback speed"
   ],
   [
    "Down",
    "Decrease playback speed"
   ],
   [
    "F1",
    "Open Points help"
   ]
  ]
 },
 {
  "id": "SCORING",
  "title": "Scoring",
  "summary": "Assign an outcome to each marked point and preview the resulting scoreboard.",
  "workflow": [
   "The first time you open Scoring for a project, the scoring settings open. Enter the player names and colors, and select the match format.",
   "Select a point in the Points list. Play the point, and then click the player who won the point, or No point.",
   "Click Next to go to the next point. Next is lime when the point has an outcome.",
   "On the Export tab, select Scoreboard in Include in the video to burn the score into the exported video."
  ],
  "actions": [
   "Click the star next to the point number to mark the point as a favorite.",
   "Read the score after the point in the score panel: the sets, the games, and the points of each player.",
   "Put the pointer on a row of the Points list and click Go to point to open that point in the Points tab.",
   "Click Scoring settings to change the player names and colors, the match format (for example best of 3 sets, match tiebreak, pro set, games only, plain points), the deuce rule, or to turn on fully manual scoring.",
   "In fully manual scoring, click the + buttons in the Sets, Games, and Points columns to give a set, a game, or the point to a player. Click a + button again to clear it.",
   "To show the serve, click the racket button next to the player who serves (or press S). You can mark the server on any point. Click a marked racket again to clear the mark.",
   "Click Scoreboard style to choose the scoreboard style, title, player colors, serve ball, bottom app line, position, size, background, and accent color.",
   "Use the bar below the video to move in the selected point, to change the playback speed, and to turn on frame-by-frame stepping with the arrow keys while the video is paused."
  ],
  "goodToKnow": [
   "In fully manual scoring, the app counts points only.",
   "The server changes after each game, and in a tiebreak after the first point and then after every two points. A new mark sets the server until the end of that game.",
   "If you do not mark a server, the app does not track the serve.",
   "The exported video uses the same scoreboard as the preview. New projects start with the last saved scoreboard style.",
   "Scoring, the scoring settings, and the scoreboard style save to the project automatically.",
   "Up and Down change speed when focus is in the video area.",
   "Left and Right seek only inside the selected point. The times on the bar below the video count from the start of the point."
  ],
  "shortcuts": [
   [
    "Q",
    "Point for Player 1"
   ],
   [
    "W",
    "No point"
   ],
   [
    "E",
    "Point for Player 2"
   ],
   [
    "R",
    "Advance to the next point and start playback"
   ],
   [
    "Shift+R",
    "Go back to the previous point"
   ],
   [
    "S",
    "Switch the server of the selected point"
   ],
   [
    "A",
    "Toggle favorite on the selected point"
   ],
   [
    "F",
    "Toggle frame-by-frame stepping"
   ],
   [
    "Space",
    "Play or pause"
   ],
   [
    "Left",
    "Seek back 1 second, or one frame while paused in frame mode"
   ],
   [
    "Right",
    "Seek forward 1 second, or one frame while paused in frame mode"
   ],
   [
    "Shift+Left",
    "Seek back 5 seconds"
   ],
   [
    "Shift+Right",
    "Seek forward 5 seconds"
   ],
   [
    "Up",
    "Increase playback speed"
   ],
   [
    "Down",
    "Decrease playback speed"
   ],
   [
    "F1",
    "Open Scoring help"
   ]
  ]
 },
 {
  "id": "STATISTICS",
  "title": "Statistics",
  "summary": "See the match statistics, and select the statistics that the exported video shows.",
  "workflow": [
   "Score the points in the Scoring tab. To get the serve and break point statistics, mark the server of one point.",
   "Open Stats to see the statistics of the full match. If the match has two sets or more, select a set to see the statistics of that set.",
   "Select In video for each row that the exported video must show."
  ],
  "actions": [
   "Put the pointer on the info icon of a gray row to see why the row has no value.",
   "Set the limits of Short points won and Long points won at the top of the Point length group.",
   "Put the pointer on the Momentum chart to see the point and the lead. Click the chart to open that point in the Scoring tab.",
   "Select In video next to Momentum to add the chart to the statistics card.",
   "Click a value with a play icon, for example the longest point, to open that point in the Scoring tab.",
   "Use Card transparency to set how much of the video shows through the card. The In the video preview shows the card over a frame of your video."
  ],
  "goodToKnow": [
   "The statistics use only the points that have a winner. When some points have no winner, the tab shows how many points it used.",
   "A gray row has no value. For example, the serve statistics need a server mark.",
   "Break points and set points need automatic scoring. Manual scoring does not know which points can win a game or a set.",
   "The time statistics use the start and end of each marked point. The average time between points is correct only when the video has no cuts.",
   "Short points won and Long points won compare the points by their length. They are in the Point length group, with Average point won. The default limits, 7 s and 15 s, are about 0-4 shots and 9 or more shots: the ATP rally length ranges. If your marks start well before the serve, make the limits larger.",
   "Return games won shows the service games of the opponent that the player won (breaks). Compare it with Service games won.",
   "The Momentum chart shows the point difference. The line goes up when the first player wins a point, and down when the second player wins a point. A dashed line marks the start of a set.",
   "The Momentum chart is the last page of the statistics card. A set summary shows the chart of its set.",
   "A value with a play icon comes from one point.",
   "The exported video uses the same card transparency as the preview.",
   "On the card, a dark player color becomes lighter, so that it shows on the dark card. The table and the scoreboard keep the player color.",
   "The In video selection autosaves to the project."
  ],
  "shortcuts": [
   [
    "F1",
    "Open Statistics help"
   ]
  ]
 },
 {
  "id": "COLORS",
  "title": "Colors",
  "summary": "Preview and save color corrections that will be applied during export.",
  "workflow": [
   "Scrub to a representative frame and play or pause the preview.",
   "Adjust the Light controls (brightness, contrast, shadows, highlights) and the Color controls (saturation, temperature). Each slider starts at 0 in the center."
  ],
  "actions": [
   "Use Reset all to restore all color controls to their defaults."
  ],
  "goodToKnow": [
   "Slider changes are saved to the project and shared with other previews.",
   "Shadows changes only the darkest third of the tones. Highlights changes only the brightest third. The mid tones do not change.",
   "Temperature adds a warm (orange) or a cool (blue) cast to the full image, also to white and gray.",
   "A system without live color-preview support still saves values for export."
  ],
  "shortcuts": [
   [
    "Space",
    "Play or pause"
   ],
   [
    "F1",
    "Open Colors help"
   ]
  ]
 },
 {
  "id": "CROP",
  "title": "Transform",
  "summary": "Reframe the output by zooming, panning, resizing, and rotating the source.",
  "workflow": [
   "Play the video or use the seek bar to choose a moment for alignment.",
   "Drag inside the crop rectangle to move it, drag handles to resize it, and drag the round handle to rotate.",
   "Click the video before you use the arrow keys to move the crop rectangle.",
   "Use the Zoom, Pan X, Pan Y, Rotation, and Fine rotation sliders or type exact values."
  ],
  "actions": [
   "Use Reset all to restore all transform controls to their defaults.",
   "Hold Shift while rotating to snap the angle to the nearest multiple of 90 degrees."
  ],
  "goodToKnow": [
   "Resizing keeps the aspect ratio and the center of the crop rectangle.",
   "Fine rotation adds up to 5 degrees to the Rotation value in steps of 0.1 degree. The Rotation group shows the total angle.",
   "The rotation handle snaps to 0, 90, and 180 degrees when the angle is within 2 degrees of them.",
   "Arrow-key nudging works while the video has the focus. The chip at the top-left corner of the video shows it."
  ],
  "shortcuts": [
   [
    "Space",
    "Play or pause"
   ],
   [
    "Left",
    "Move the focused crop rectangle left by 1 pixel"
   ],
   [
    "Right",
    "Move the focused crop rectangle right by 1 pixel"
   ],
   [
    "Up",
    "Move the focused crop rectangle up by 1 pixel"
   ],
   [
    "Down",
    "Move the focused crop rectangle down by 1 pixel"
   ],
   [
    "Shift+Left",
    "Move left by 10 pixels"
   ],
   [
    "Shift+Right",
    "Move right by 10 pixels"
   ],
   [
    "Shift+Up",
    "Move up by 10 pixels"
   ],
   [
    "Shift+Down",
    "Move down by 10 pixels"
   ],
   [
    "F1",
    "Open Transform help"
   ]
  ]
 },
 {
  "id": "EXPORT",
  "title": "Export",
  "summary": "Configure and export the final video, then monitor it through completion.",
  "workflow": [
   "In step 1, Content, select Full video, Only points or Only favorites. Each card shows the length of the exported video and the number of points in it.",
   "In step 2, Include in the video, select Scoreboard and Comments if necessary. The text under Scoreboard shows how many points are scored.",
   "Select Statistics card to add the match statistics at the end of the video. The Stats tab sets which statistics the card shows. The text under the name shows how long the card is.",
   "Select Set summaries to add the statistics of each set after the last point of the set. Set summaries are available only for Only points and Only favorites. A set without an exported point has no summary.",
   "In step 3, Quality and file size, select Simple, and then select Original quality, Balanced, or Fast export. Each tile shows the file size. The table under the tiles shows the resolution, FPS, bitrate, file size, and export time of the selected quality. The \"original\" tag marks a value that is the same as in the original video.",
   "Select Advanced to set the resolution, the FPS, the bitrate, and the encoder yourself.",
   "The bottom of the left column shows the estimated file size and the settings of the next export. Select Start export, choose the output file, and monitor progress in the Exports table. When an export runs, the button changes to Enqueue export, and the new export starts after the current exports."
  ],
  "actions": [
   "Cancel the active export or a queued export with the red Cancel export button in its row.",
   "Open the output folder with the Open folder button in the row of an active, queued, or completed export.",
   "Use Clear list in the Completed group to remove the completed entries from the table."
  ],
  "goodToKnow": [
   "Original quality keeps the resolution, FPS, and bitrate of the source. Balanced uses 75% of the source bitrate. Fast export makes the video 1080p at most and uses 50% of the source bitrate.",
   "In the Advanced mode, you can select only a resolution and an FPS that are not higher than the source. The values of the original video have the \"original\" tag and are selected by default.",
   "The bitrate slider goes from one fifth of the source bitrate (worst quality, smallest file) to the source bitrate (best quality).",
   "The app detects the GPU and tests each encoder. It shows only the encoders that work on this PC, and the Simple mode uses the best one.",
   "Only favorites requires at least one favorite point. Scoreboard and Statistics card require at least one scored point.",
   "A statistics card shows each page for 6 seconds over a frozen frame. The cards add this time to the video length and to the file size.",
   "The file size is an estimate (~). The real size can be different, because fast motion needs more data.",
   "Each row of the Exports table shows the status, the file name with its folder and project, the content and video settings, the size, and the time. The size of the active export is the written size against the expected size. Point to the file name to see the full path.",
   "Clear list does not delete the exported video files.",
   "Export has no other tab-specific keyboard shortcuts.",
   "Troubleshooting: if an export fails for an unclear reason, select Advanced and then Software (x264). The software encoder is slower, but it works on all PCs. A hardware encoder can fail after a graphics driver update.",
   "Troubleshooting: if the export still fails, select a lower resolution or FPS, or select Full video to find out if the points cause the problem."
  ],
  "shortcuts": [
   [
    "F1",
    "Open Export help"
   ]
  ]
 }
];
