# PROGRESS

## Current phase
Phase 0. Task 1 built; at STOP POINT 1 waiting for Michel to enable Pages and check Palm Flame on the S23. Next: Task 2 (page) in parallel with Tasks 3–4 (watch).

## Prerequisites (Michel)
- [x] Firebase RTDB created (Singapore region, test mode). URL: `https://imagine-cast-default-rtdb.asia-southeast1.firebasedatabase.app`
- [x] GitHub repo `imagine-cast` created (`michel-j-hodge/imagine-cast`)
- [x] Watch paired for Wi-Fi debugging
- [x] PowerBand project located and opened in Android Studio (toolchain check)

## Tasks
- [x] 1 Repo scaffold + Pages (built; hardware validation pending)
- [ ] 2 Camera page: state machine + relay
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
(none yet)

## Session log

### 2026-11-23 — Task 1
- Created `/docs/index.html` as a byte-identical copy of `/reference/palm-flame.html` (checked with `cmp`). No page logic changed.
- Added empty `/docs/.nojekyll`.
- Created `/watch/` with a `.gitkeep` placeholder so the folder exists in git; Task 3 replaces it with the Android Studio project.
- Added `.gitignore` with the Android Studio entries from the task file.
- Michel to do: Settings → Pages → Deploy from branch → `main` → `/docs`. Then open `https://michel-j-hodge.github.io/imagine-cast/` on the S23 and confirm Palm Flame works as before.
