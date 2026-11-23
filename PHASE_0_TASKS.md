# Phase 0 Tasks

Do the tasks in order unless marked parallel. At a **STOP POINT**, commit, update PROGRESS.md, and end the session. Michel tests on the hardware before the next task starts.

**Prerequisites (Michel, before Task 1):**
- Firebase Realtime Database created in the Singapore region, test mode, URL copied
- GitHub repo `imagine-cast` created (public, so GitHub Pages is free)
- Watch paired for Wi-Fi debugging in Android Studio
- The old PowerBand project builds and runs on the watch, as a toolchain check

---

## Task 1 — Repo scaffold and Pages smoke test

**Steps**
1. Create the layout from CLAUDE.md.
2. Copy `/reference/palm-flame.html` to `/docs/index.html` unchanged. Add an empty `/docs/.nojekyll`.
3. Add `.gitignore` entries for Android Studio (`/watch/.gradle`, `/watch/build`, `/watch/app/build`, `local.properties`, `.idea`, `*.iml`).
4. Commit and push. Tell Michel to enable Pages: Settings → Pages → Deploy from branch → main → /docs.

**Validation:** `https://<user>.github.io/imagine-cast/` loads on the S23. Palm Flame works exactly as before.

**Do not:** edit any page logic yet.

**STOP POINT 1**

---

## Task 2 — Camera page: fire-only state machine and relay  *(parallel with Tasks 3–4)*

**Steps**
1. Strip `/docs/index.html` as described in architecture section 4 (keep / remove lists).
2. Add `BAND_ID`, `DB_URL`, `DISARM_HOLD_MS` to the Tuning block. Add the `?db=` override.
3. Replace `updateState` with the section 4 state machine.
4. Add `sendState(power)`: PUT, timed write leg, seq rule, record a CSV row.
5. Add the ack EventSource on `/bands/{BAND_ID}/ack.json`. Match acks to pending sends by seq to compute the round trip. Ignore the first snapshot.
6. Add the relay panel and the Copy CSV button. Send the warm-up PUT on start.
7. Update the start screen copy: "Make a fist, then two fingers up to arm fire. Hold an open hand for a second to disarm."

**Validation (Michel, on the phone)**
- Fist→two creates a new `state` value with a new seq in the Firebase console within a second.
- Holding an open hand for about 1 s writes `"none"`.
- An open hand for less than 1 s writes nothing.
- The relay panel shows a write time. The round trip shows blank until the watch exists. To check the ack path early, edit `/bands/band-01/ack` in the Firebase console to `{"seq": <the last seq>}`; the panel should show a round trip.

**Do not:**
- touch `readPose()` or the GLSL
- add the Firebase SDK
- add other spells

**STOP POINT 2** (can be verified together with STOP POINT 3)

---

## Task 3 — Watch app scaffold  *(parallel with Task 2)*

**Steps**
1. In `/watch`, create the project from Android Studio → New Project → Wear OS → Empty Wear App. Package `com.imagine.cast`, app name "Imagine Cast".
2. Add OkHttp and okhttp-sse (same 4.x version), plus `testImplementation("org.json:json:20240303")`.
3. Add the `INTERNET`, `VIBRATE` and `ACCESS_NETWORK_STATE` permissions.
4. Create `Config.kt` with the constants. Add placeholder `arm.wav` and `fireball.wav` (any short clips) to `res/raw/`.
5. Build an `IDLE` screen showing "Make a sign" and a status line with the network type.

**Validation:** `./gradlew assembleDebug` passes. The app installs on the watch and shows the IDLE screen with "BT proxy" or "Wi-Fi".

**STOP POINT 3**

---

## Task 4 — Relay client, arming and acks

**Steps**
1. `RelayParser` plus JUnit tests:
   - put on "/" with a valid object
   - put with null data
   - put on a child path (ignored)
   - patch (ignored)
   - keep-alive (ignored)
   - malformed JSON (ignored, no crash)
2. `RelayClient`:
   - SSE stream, implementing the seq rules in architecture section 3 exactly, including first-snapshot and reconnect handling
   - ack PUT before the state callback
   - backoff reconnect
   - callbacks on the main thread
3. `FeedbackManager` with SoundPool and vibration.
4. Wire into `MainActivity`:
   - live `fire` → ARMED: red, "FIRE", 60 ms buzz, `arm.wav`
   - `none` → IDLE
   - silent apply when not live
   - lifecycle rules from CLAUDE.md

**Validation (Michel)**
1. Edit `/bands/band-01/state` in the Firebase console to `{"power":"fire","seq":<a larger number>}`. The watch turns red and buzzes, and an ack appears in the console.
2. Kill and reopen the app. It starts in IDLE even though the state in Firebase is "fire".
3. End to end: fist→two on the phone turns the watch red. The relay panel shows a round trip.
4. Turn the phone's Wi-Fi off for 10 s, then back on. The watch status shows reconnecting and then live again.

**Do not:**
- add punch detection yet
- add the Firebase SDK
- add Bluetooth or a local server

**STOP POINT 4**

---

## Task 5 — Delay test (Michel, no code)

Use GATE0_LOG.md.
- Prop the phone up at chest height in a well-lit room. Stand 1–1.5 m away.
- **Run A:** watch connected to the phone over Bluetooth (default). 20 casts with about 3 s between them. Copy the CSV into the log.
- **Run B:** Bluetooth off on the watch, so it uses Wi-Fi. 20 casts. Copy the CSV into the log.
- For each run, write one line on how it felt.
- Make the gate call (architecture section 8) and record it.

**STOP POINT 5 — Gate 0 decision.** Task 6 only runs on ADVANCE.

---

## Task 6 — Punch → fireball

**Steps**
1. `PunchDetector` plus JUnit tests on fake sample streams:
   - below threshold → no punch
   - a single spike → one punch
   - two spikes 300 ms apart → one punch (cooldown)
   - two spikes 600 ms apart → two punches
2. Register the accelerometer in `onResume`/`onPause`. On a punch while ARMED: `FIREBALL`. Play `fireball.wav`, do the strong buzz and the orange flash. While IDLE, do nothing.

**Validation (Michel):**
- A punch while armed always fires.
- A punch while idle never fires.
- Waving the arm normally while armed does not fire. If it does, note it in Open Questions; do not change the threshold in this task.

**STOP POINT 6**

---

## Task 7 — Range and lighting test (Michel with Titan, no code)

Stand at 1, 1.5 and 2 m, 10 casts at each, first in normal room light and then in dim light. Log the hit rate in GATE0_LOG.md. This tells us how big the play zone can be.

## Task 8 — Kid test (Michel)

Follow the usual protocol: no prompting, observe, write up after. Log it in GATE0_LOG.md.

---

## Subagents

- Task 2 (web) and Tasks 3–4 (watch) touch separate folders and share only the relay contract in architecture section 3. Run them as two subagents. Give each one: architecture sections 3 and 4 (web) or 3 and 5 (watch), CLAUDE.md, and the folders it may touch (`/docs` or `/watch`). Neither subagent edits root markdown files.
- The `RelayParser` tests and the `PunchDetector` tests can be written by separate subagents.
- Do not use subagents for wiring `MainActivity`, editing Gradle files, or any final integration pass.
- **After the subagents finish, the orchestrator:**
  - checks each task's validation items that can be checked off-device
  - runs `./gradlew assembleDebug` and the unit tests
  - checks that both sides use identical paths and field names
  - writes one consolidated PROGRESS.md update
