# License Threat Model

Status: agreed on 2026-10-01.

This document tells how to assess a license measure: a check, a limit, or a
stored value that enforces the license. Use it for each license spec, for
example `build-expiry-spec.md`, and for license keys (L-6.3 in
`elv2-migration-plan.md`).

## User groups

### Honest users

Honest users install the app and use it by the rules. They do not try to
bypass a measure.

- They need no protection from the measures.
- A measure must not make their work worse. This applies also when the
  measure fails, gives a false result, or cannot get to the network.

### Cheaters

Cheaters use the app for its value, as honest users do. When a limit stops
them, they try a "clever trick": an action that does not feel like a
violation of the license. Examples: they move the clock back, or they change
one line in a file.

- A measure must stop the tricks that use normal user actions (see "The
  line").
- After the line, a cheater knows that the action is a violation of the
  license. Some cheaters continue. We accept this. No measure can stop all of
  them.

### Hackers

Hackers want to break the app. They do not need its value. They distribute a
changed build or the instructions to break it.

- We do not protect the app against hackers. A strong protection needs large
  measures and hurts honest users: the app must always be online, the source
  code must be closed, and so on.
- ELv2 and legal action are the only answers to hackers.

## Goal

Find the point where:

- a cheater cannot bypass a measure with normal user actions,
- an honest user does not see an effect of the measures on their work,
- a hacker can break the app, and we do not add measures against it.

## The line

An action is a normal user action when all of these are true:

- People do the action for other reasons too.
- The action uses only the OS or the app user interface.
- The action needs no knowledge of the internals of the app.

A measure must stop a bypass that uses only normal user actions. A bypass
that needs one action after the line is acceptable. Do not add a measure
against it.

| Action | Side of the line |
|---|---|
| Move the system clock | Normal |
| Work offline, turn off Wi-Fi | Normal |
| Keep the app open and put the computer to sleep | Normal |
| Uninstall and install the app again | Normal |
| Install an old installer | Normal |
| Start the app many times each day | Normal |
| Queue many exports | Normal |
| Use a "trial reset" tool that changes the time for one program | Normal (a common tool with a user interface) |
| Edit or delete files in the data folder of the app | After the line |
| Edit or delete values in the registry | After the line |
| Set an environment variable or edit the `.cfg` file | After the line |
| Add a firewall rule or a hosts entry for the app | After the line |
| Run a script that changes the clock before each start | After the line |
| Add a certificate, use a proxy that changes responses | After the line |
| Run the JAR with a different Java runtime | After the line |
| Build from source, change a build property | After the line |
| Edit the bytecode, use a Java agent | After the line |

## Rules for measures

1. **Fail open on infrastructure failures.** No network, a file that is not
   valid, a server error, or a check that cannot write a file must not block
   the app.
2. **Fail closed only on evidence of a user action.** Example: a clock that
   is earlier than the saved time. If an honest user can cause the same
   evidence by mistake (a flat clock battery), make the effect small, and tell
   the user the cause and the fix.
3. **Do not punish honest users for a hacker case.** If a measure is only
   useful against hackers, do not add it.
4. **Prefer measures where the only bypass is a patch for one version.** A
   patched JAR becomes old with each release. Instructions that work for all
   versions ("set the clock to X, add this hosts line") can spread to
   cheaters. Such instructions are acceptable only when at least one step is
   after the line.
5. **Give the author a way to recover.** A measure must not lock all honest
   users if the author or the infrastructure fails. Examples: no release for
   a long time, a wrong server time.

## How to assess a measure or a hole

For each hole, answer these questions:

1. **Group.** Which group can use the hole: cheaters (only normal user
   actions) or hackers (at least one action after the line)? If only
   hackers, stop. Do not add a measure.
2. **Gain.** How much use does the hole give: days, months, or no limit?
3. **Cost of the counter.** How much work is the counter: code, tests, and
   release steps?
4. **Harm of the counter.** What can the counter do to honest users when it
   works, when it fails, and when the network is not available?

Add a counter when the hole is at the cheater level, the gain is real, and
the harm to honest users is small or zero. For an honest-user risk (rule 5),
add a counter when the harm is large, also if the risk is rare.
