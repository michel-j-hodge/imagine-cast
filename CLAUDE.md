# Imagine Cast — CLAUDE.md

Read this first, then PROGRESS.md, then PHASE_0_ARCHITECTURE.md and PHASE_0_TASKS.md.

## What this is

A side experiment for Imagine Bands. A phone propped up facing the play area runs a web page that tracks the player's hand with MediaPipe. When the player makes a fist and then two fingers up, the page sends an event through a cloud relay to a Galaxy Watch 8. The watch turns red ("fire armed"). While the watch is armed, a punch detected by the watch's own accelerometer fires a fireball: sound, strong haptic and a screen flash.

**Division of work:** the camera chooses the power, and the watch fires it. Network delay only ever sits on the arming step. The punch is detected and answered on the watch, so it has no network delay. Do not move punch detection to the camera.

One player and one watch. This is a family prototype, not a product.

**The question Phase 0 answers:** does sign-to-watch arming feel immediate enough over a cloud relay to build on?

## Repo layout

```
/docs/index.html          Camera page, served by GitHub Pages (main branch, /docs folder)
/docs/.nojekyll           Stops GitHub Pages from running Jekyll on the folder
/watch/                   Android Studio project (Wear OS app)
/reference/palm-flame.html  Original Palm Flame prototype. Read-only. Never edit.
CLAUDE.md, PROGRESS.md, PHASE_0_*.md, GATE0_LOG.md
```

## Stack

**Web page**
- One self-contained HTML file. No build step, no framework, no npm.
- MediaPipe `@mediapipe/tasks-vision@0.10.3` from jsDelivr, pinned exactly as in Palm Flame.
- Relay writes use `fetch` PUT to the Firebase REST API. Relay reads use the browser's built-in `EventSource`.
- No Firebase JS SDK.

**Watch app**
- Kotlin, Android Studio latest stable, Jetpack Compose for Wear OS. Use whatever Compose for Wear artifacts the "Empty Wear App" template generates. Do not swap material libraries.
- Single module, single Activity. Dependencies are passed in by hand. No Hilt, Dagger or Koin.
- Networking: `com.squareup.okhttp3:okhttp` and `com.squareup.okhttp3:okhttp-sse`, same version (latest 4.x).
- JSON: `org.json`, which ships with Android. Unit tests must add `testImplementation("org.json:json:20240303")`, because Android's org.json is stubbed in local JVM tests and fails with "Method ... not mocked".
- Audio: SoundPool only.
- Haptics: VibratorManager.
- Sensors: SensorManager, TYPE_ACCELEROMETER, SENSOR_DELAY_GAME.
- Tests: JUnit for `RelayParser` and `PunchDetector` only.

**Relay**
- Firebase Realtime Database, Singapore region (asia-southeast1), REST API only, test-mode rules.

Do not add any library, framework or layer not listed here. If you think you need one, stop and add it to PROGRESS.md under "Open Questions".

## Fixed names (do not rename)

- Watch states: `IDLE`, `ARMED`
- Relay power values: `"fire"`, `"none"`
- Watch-side events: `ARM_FIRE`, `DISARM`, `FIREBALL`
- Watch classes: `MainActivity`, `RelayClient`, `RelayParser`, `PunchDetector`, `FeedbackManager`, `Config`
- Sound files in `watch/app/src/main/res/raw/`: `arm.wav`, `fireball.wav`. Placeholders are fine.

## Constants

These are starting values. Each one lives as a named constant: web constants in the page's Tuning block, watch constants in `Config.kt` or `PunchDetector`. Do not silently change a value. If one looks wrong, flag it under Open Questions.

| Constant | Value | Where |
|---|---|---|
| `BAND_ID` | `"band-01"` | page + Config.kt |
| `DB_URL` | Michel's database URL | page + Config.kt (page also accepts `?db=`) |
| `POSE_FRAMES` | 3 | page (unchanged from Palm Flame) |
| `CHARGE_WINDOW` | 1500 ms | page (unchanged from Palm Flame) |
| `DISARM_HOLD_MS` | 1000 ms | page |
| `PUNCH_THRESHOLD` | 18 m/s², magnitude including gravity | PunchDetector |
| `PUNCH_COOLDOWN_MS` | 500 ms | PunchDetector |
| `RECONNECT_BACKOFF_MS` | 500, 1000, 2000, 4000, then 5000 (cap) | RelayClient |

## Rules

- Register sensor listeners and open the relay stream in `onResume`. Unregister and close them in `onPause`. Battery is a named risk.
- Keep the watch screen on only while the Activity is resumed (`FLAG_KEEP_SCREEN_ON`).
- The watch never reacts to the first snapshot it receives after the app starts. It always starts in `IDLE`. See the architecture doc.
- Use `seq = Date.now()` on the page. Never use a counter: a counter resets when the page reloads, and the watch would then ignore every new event.
- In code comments, state that acceleration magnitude includes gravity and why.
- If a spec is wrong, ambiguous or blocks you, stop and write it under Open Questions in PROGRESS.md. Do not quietly deviate.
- Stop at every **STOP POINT** in the task file. Michel has to verify on the hardware before you continue.
- At the end of each session, update PROGRESS.md and commit.

## Out of scope (do not build unless Michel asks)

- Multiple players or multiple watches, or telling hands apart
- Spells other than fire, gesture combos
- Fireball visuals on the camera screen driven by the watch
- Local WebSocket server, Bluetooth LE transport, Firebase SDKs, authentication or security rules
- Scores, missions, accounts, persistence, analytics
- Changes to `readPose()` thresholds without measured evidence

## Deploying

- **Page:** commit to `main`. GitHub Pages serves `/docs` over HTTPS, which the camera requires.
- **Watch:** Android Studio Run, or `adb install -r`, over Wi-Fi debugging. If the watch shows up twice in `adb devices`, keep the mDNS TLS entry and disconnect the plain TCP entry.

## Session prompts

**Start:** Read CLAUDE.md first, then PROGRESS.md, then PHASE_0_ARCHITECTURE.md and PHASE_0_TASKS.md. We're on Phase 0. Begin with Task N.

**End:** Before ending, update PROGRESS.md with what you built, anything you observed, and Open Questions. Commit all changes.
