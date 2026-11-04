# Closed backlog items

This file lists the items of `backlog.md` that closed without the work.
Each item tells the reason. To open an item again, move it back to
`backlog.md`.

### B-31 License dialog at the first start

- The Velopack setup EXE shows no license page (B-24). Show the ELv2 text,
  `LICENSE-NOTICE`, and the third-party notices in a dialog at the first
  start of the app.
- Keep a flag in the preferences, so that the dialog shows only one time.
- Done when: the first start with empty preferences shows the dialog. The
  next start does not show it.
- Closed on 2026-11-04 (decision of the author). Reasons:
  - The app already gives the three texts. More > About opens `LICENSE`
    (ELv2), `LICENSE-NOTICE`, and `THIRD-PARTY-NOTICES`. The files are also
    in the `legal/` folder of the installed app.
  - The ELv2 does not need a click to accept. Its text says that a person
    who uses the software agrees to the terms.
  - The third-party licenses (for example LGPL libmpv, FFmpeg, and the Java
    runtime) need the license texts with the distribution. They do not need
    a dialog at the start.
  - A dialog at the first start adds one more step before the user sees the
    app. The app already shows the overview help at the first start.
- Open the item again only if the author wants a click-through agreement
  (EULA) as a record that the user accepted the terms. Ask a lawyer first.
- In place of the dialog: in a released build on Windows and on macOS,
  check that More > About opens all three files.
