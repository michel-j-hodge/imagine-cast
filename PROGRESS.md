# PROGRESS

## Current phase
Phase 0. Task 1 verified (Pages works on the S23). Task 2 built; at STOP POINT 2 (Michel can verify it now, or together with STOP POINT 3). Next: Task 3 (watch scaffold).

## Prerequisites (Michel)
- [x] Firebase RTDB created (Singapore region, test mode). URL: `https://imagine-cast-default-rtdb.asia-southeast1.firebasedatabase.app`
- [x] GitHub repo `imagine-cast` created (`michel-j-hodge/imagine-cast`)
- [x] Watch paired for Wi-Fi debugging
- [x] PowerBand project located and opened in Android Studio (toolchain check)

## Tasks
- [x] 1 Repo scaffold + Pages (verified on S23)
- [x] 2 Camera page: state machine + relay (built; phone validation pending)
- [ ] 3 Watch scaffold
- [ ] 4 Relay client, arming, acks
- [ ] 5 Delay test → Gate 0
- [ ] 6 Punch → fireball
- [ ] 7 Range and lighting test
- [ ] 8 Kid test

## Decisions
- 2026-11-23: The camera picks the power and the watch fires it. Network delay only on arming.
- 2026-11-23: Relay is Firebase RTDB over REST and SSE, Singapore region. No SDKs.
- 2026-11-23: The watch stays armed until an open hand is held for 1 s. Every punch while armed fires.
- 2026-11-23: Fireball feedback is on the watch only for Phase 0.

## Open Questions
- **CORS preflight (to measure):** a browser PUT to Firebase always triggers an OPTIONS preflight. It can't be seen from the phone. To check it, open the page on a laptop with DevTools → Network, cast a few times, and record whether an OPTIONS request appears before each PUT, and how long it takes. Chrome caches preflights for a short time, so the first cast after a pause may be the slow one. No change made; recorded per architecture §4.

## Session log

### 2026-11-23 — Task 1
- Created `/docs/index.html` as a byte-identical copy of `/reference/palm-flame.html` (checked with `cmp`). No page logic changed.
- Added empty `/docs/.nojekyll`.
- Created `/watch/` with a `.gitkeep` placeholder so the folder exists in git; Task 3 replaces it with the Android Studio project.
- Added `.gitignore` with the Android Studio entries from the task file.
- Michel to do: Settings → Pages → Deploy from branch → `main` → `/docs`. Then open `https://michel-j-hodge.github.io/imagine-cast/` on the S23 and confirm Palm Flame works as before.

### 2026-11-23 — Task 2
- `docs/index.html` is now "Imagine Cast". Stripped: orb physics, gyro/devicemotion and `camOff`, the bolt/ring/orb spells and their guide text, the four-spell start screen, the fingertip glow and the spell-circle tilt. Unused shader uniforms are set to 0 every frame.
- Kept byte-identical to Palm Flame (checked with a script): the VERT/FRAG shader source, `OneEuro`, `readPose()` and its threshold constants, `toScreen`, camera and tracker startup, the flip button, `?debug` and the wake lock.
- Tuning block adds `DISARM_HOLD_MS` = 1000, `BAND_ID` = "band-01", `DB_URL` (with a `?db=` override), `ARMED_FLAME` = 0.6 (the palm flame shown while armed) and `ACK_TIMEOUT_MS` = 3000 (the 3 s ack window from the spec, now a named constant). `POSE_FRAMES` and `CHARGE_WINDOW` are unchanged. `LOST_HOLD` (500 ms) is kept, but now it only cancels a charge in progress. Losing the hand never disarms.
- State machine follows architecture §4. `sendState(power)`: whole-object PUT `{power, seq, sentAt}`, `seq = max(Date.now(), lastSeq+1)`, timed write leg, one CSV row per send.
- Ack EventSource on `/bands/band-01/ack.json`. The first `put` after every (re)connect is ignored (it's a snapshot). Acks are matched to sends by seq. A round trip only counts if the ack arrives within 3 s. To support the console check, it also accepts a `patch` on `/` and a `put` on `/seq`.
- Relay panel in the top right, below the flip button: status, armed, last write and round trip, and n/median/max (warm-up excluded). Copy CSV columns: `seq,power,writeMs,rttMs,note`, with the warm-up row and any write errors marked in `note`. The warm-up PUT of `none` and the EventSource both start when "Start camera" is tapped.
- Small deviations, flagged here: (1) removed Palm Flame's `navigator.vibrate` buzzes, because a vibrating propped-up phone can shift the camera, and the watch gives the feedback now; (2) moved the `?debug` panel down to 200 px so it doesn't overlap the relay panel in portrait.
- Tested off-device: an automated simulation with fake fetch/EventSource passed 13/13 checks. It covered the warm-up, first snapshot ignored, fist→two sends fire, two after the window sends nothing, open under 1 s sends nothing, open of 1 s or more sends none once, re-arm sends a new seq, the 3 s ack cutoff, and the console-style ack edits. Script syntax checked with `node --check`. Not yet run against the real Firebase or on the phone.
