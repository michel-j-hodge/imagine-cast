# Phase 0 Architecture — Sign arms the watch, punch fires

## 1. Gate question

Can the camera page arm the watch over a cloud relay fast enough to feel immediate, with one player standing 1–2 m from a propped-up phone?

Everything in this phase serves that measurement. Punch → fireball is built only after the delay test passes.

## 2. System

```
PHONE (S23 Ultra, Chrome, GitHub Pages HTTPS)
  MediaPipe hand → readPose() → stable pose
  fist → two  ──►  PUT  /bands/band-01/state.json  {"power":"fire","seq":<Date.now()>,"sentAt":<ms>}
  open (held ≥ 1 s while armed) ──► PUT same path   {"power":"none", ...}
  EventSource /bands/band-01/ack.json  ◄── shows round-trip delay

FIREBASE RTDB (asia-southeast1)
  /bands/band-01/state   written by page, streamed to watch
  /bands/band-01/ack     written by watch, streamed to page

WATCH (Galaxy Watch 8)
  RelayClient: SSE GET /bands/band-01/state.json ─► RelayParser ─► new seq?
       ├─ immediately PUT /bands/band-01/ack.json {"seq":<seq>,"rxAt":<ms>}
       └─ power fire → ARM_FIRE (red, buzz, arm.wav)   power none → DISARM (dark)
  PunchDetector (accelerometer, local) ─► if ARMED → FIREBALL (fireball.wav, strong buzz, orange flash)
```

## 3. Relay contract

Base URL: `DB_URL`, e.g. `https://<project>-default-rtdb.asia-southeast1.firebasedatabase.app`

**Write the state (page only).** Always PUT the whole object, never PATCH a single field:
```
PUT {DB_URL}/bands/{BAND_ID}/state.json
{"power":"fire"|"none","seq":1764000000000,"sentAt":1764000000000}
```
- `seq` is `Date.now()` at send time. If two sends land in the same millisecond, use `max(Date.now(), lastSeq + 1)`.
- `sentAt` is informational only. Never compare clocks across devices.

**Write the ack (watch only):**
```
PUT {DB_URL}/bands/{BAND_ID}/ack.json
{"seq":1764000000000,"rxAt":<watch ms>}
```

**Stream format (Firebase REST streaming, Server-Sent Events):**
```
event: put
data: {"path":"/","data":{"power":"fire","seq":...,"sentAt":...}}

event: keep-alive
data: null
```
- Act on `put` events where `path == "/"` and `data` is an object with `power` and `seq`.
- Ignore `keep-alive`.
- Log `patch` events, and `put` events on any other path, then ignore them.
- On `cancel` or `auth_revoked`, close the stream and reconnect with backoff.
- Firebase may answer the stream request with a redirect (HTTP 307). OkHttp and EventSource follow it by default. Do not turn redirects off.
- `data` can be `null` if nothing has been written yet. Treat null as no state.

**seq rules on the watch:**
- `lastSeq` starts at 0 when the app process starts.
- **First snapshot after the app starts:** set `lastSeq = data.seq`. Do not change state, do not ack. The watch starts in `IDLE`. This keeps an old "fire" from earlier in the day from arming the watch.
- **Later `put` events:** only `seq > lastSeq` counts. Update `lastSeq`, ack first, then apply the state. A missed `ARM_FIRE` gets no buzz; buzz only for events received live.
- **Snapshot after a reconnect:** same rule. If `seq > lastSeq`, it was missed while disconnected. Apply the state silently, with no buzz and no sound, and still ack.

## 4. Camera page (`/docs/index.html`)

Start from `/reference/palm-flame.html`.

**Keep unchanged:**
- `readPose()` and its threshold constants
- `OneEuro`
- `toScreen`
- the camera and tracker startup code
- the frame loop structure
- the WebGL shader source (do not edit the GLSL)
- `?debug`
- the wake lock

**Remove:**
- orb physics, the gyro/devicemotion handling and `camOff`
- the bolt, ring and orb spell entries and their guide text
- the start-screen copy about four spells

Leave unused shader uniforms at 0.

**State machine.** This replaces `updateState`:
```
idle      --stable fist-->                         charging (chargeAt = now)
charging  --stable two within CHARGE_WINDOW-->     send fire; armed = true; state = idle
charging  --stable fist-->                         chargeAt = now
charging  --CHARGE_WINDOW elapsed-->               idle
any       --stable open held ≥ DISARM_HOLD_MS while armed--> send none; armed = false
```
- The page tracks `armed` only for display and for the disarm rule. The watch is the source of truth for what the player feels.
- Arming again while already armed is allowed. It sends a new seq and the watch buzzes again, which confirms the cast landed.
- Losing the hand from frame does not disarm.
- **Visuals:** show the charge sparks while in `charging`. While `armed`, show a small steady flame on the palm (`uFlame` around 0.6). This is so Michel can see the page's state while testing; the kid will not be looking at the screen.

**Relay panel.** Always visible in the top-right, separate from the `?debug` panel:
```
relay   ● live / ○ connecting / ✕ error
armed   yes/no
last    write 48 ms · round trip 162 ms
n=14    median 155 ms · max 290 ms
[Copy CSV]
```
- **write** is the time from `fetch` start to the response resolving (page → Firebase only).
- **round trip** is the time from send until the ack with the same seq arrives on the page's EventSource.
- Keep one row per send: `seq, power, writeMs, rttMs (blank if no ack within 3 s)`. The Copy CSV button copies the rows to the clipboard.
- Open the EventSource on start. On start, also send one warm-up PUT of `{"power":"none","seq":...}`. This opens the TLS connection early so the first real cast isn't the slow one. The watch acks it like any other event; mark the warm-up row in the CSV.
- **Config:** `DB_URL` constant, overridden by the `?db=` query parameter if present.

**Known cost to watch for:** a PUT from a browser triggers a CORS preflight request. If DevTools on a laptop shows an OPTIONS request before every PUT, record the extra time under Open Questions. Do not switch to the Firebase SDK unless Michel decides to.

## 5. Watch app

**Classes:**
- `Config` — `DB_URL`, `BAND_ID`, backoff list.
- `RelayParser` — a pure function `parse(eventType, dataJson): RelayEvent?`. No Android dependencies, so it can be unit tested.
- `RelayClient(okHttp, parser, onState: (power, live) -> Unit, onStatus: (String) -> Unit)`:
  - `start()` and `stop()`.
  - Owns the seq rules from section 3 and the ack PUT.
  - Reconnects with backoff on failure; resets the backoff after a successful open.
  - Callbacks are delivered on the main thread.
- `PunchDetector(threshold, cooldownMs, clock)` — `onSample(x, y, z, tMs): Boolean`. True means a punch. Pure Kotlin, unit tested.
- `FeedbackManager(context)` — SoundPool with `arm.wav` and `fireball.wav` loaded once; `armed()`, `fireball()`, `release()`. Buzzes: arm 60 ms; fireball 150 ms at maximum amplitude.
- `MainActivity` — wires everything together, owns the Compose UI state (`IDLE`/`ARMED`, flash, status text), and handles the lifecycle.

**Lifecycle:**
- `onResume`: `relayClient.start()`, register the accelerometer, keep the screen on.
- `onPause`: `relayClient.stop()`, unregister the accelerometer, clear keep-screen-on.
- `onDestroy`: `feedback.release()`.

**UI (round screen):**
- `IDLE`: near-black background, "Make a sign" small and centred.
- `ARMED`: full red background, "FIRE" large.
- `FIREBALL`: 300 ms orange-white flash over whichever state is showing.
- Bottom status line, small grey: relay status and network type ("BT proxy" or "Wi-Fi"), read from `ConnectivityManager.getNetworkCapabilities(activeNetwork)`. The delay test needs to know which network was used.

**Permissions:** `INTERNET`, `VIBRATE`, `ACCESS_NETWORK_STATE`.

**Punch rule (Task 6 only):**
- magnitude = √(x² + y² + z²), gravity included. Gravity is not subtracted; Phase 0 only needs a crude spike.
- Fire when magnitude > `PUNCH_THRESHOLD` and at least `PUNCH_COOLDOWN_MS` have passed since the last punch.
- Only acts when the state is `ARMED`. In `IDLE` a punch does nothing (no sound): the sign is what unlocks the power.

## 6. Delay budget (estimates, not measured)

| Step | Estimate |
|---|---|
| Hand detected + 3 frames held | 100–200 ms |
| Page → Firebase (write) | 40–120 ms |
| Firebase → watch (via phone over Bluetooth, or Wi-Fi) | 40–150 ms, unknown |
| Watch reacts | < 20 ms |

The round trip on the page adds the ack leg back. It is a diagnostic, not the number the player feels. The player feels detection plus the one-way trip, and the gate judges that by feel. The round-trip number tells us which leg to fix if the feel test fails.

## 7. Risks

- **Bluetooth proxy delay.** The watch's internet goes through the phone over Bluetooth by default. Test both: Bluetooth on, and Bluetooth off on the watch (which forces Wi-Fi).
- **Accidental disarm.** Kids open their hands all the time while playing. That is why `DISARM_HOLD_MS` is 1 s. If the watch drops out of red when nobody meant it to, raise this value before changing anything else.
- **Range and lighting.** Palm Flame was only tested handheld. A child's hand at 2 m may not track, which is tested in Task 7.
- **Public database.** In test mode, anyone with the URL can read and write, and the GitHub repo is public. Acceptable for a prototype; keep personal data out of it. The rules expire after about 30 days, and Firebase will then reject requests until the rules are extended.
- **Battery.** Screen on, an open stream and the accelerometer together. Fine for 15-minute sessions; check the drop after the playtest.
- **Weak watch speaker.** Haptics and the red screen carry the feedback; sound is a bonus.

## 8. Gate 0 (after Task 5)

- **ADVANCE:**
  - median round trip ≤ 300 ms and max ≤ 600 ms over 20 casts, and
  - Michel judges arming as feeling immediate, and
  - fist→two is recognised in at least 9 of 10 casts at 1–1.5 m.
- **STAY & REWORK:** it feels laggy, but the CSV shows one leg is clearly the cause (for example, the Bluetooth proxy, or the preflight). Fix that leg and run again.
- **KILL:** the round trip is above 600 ms on both networks, or recognition at play distance is unreliable even in good light.
