# PROGRESS

## Current phase
Phase 0. Next: Task 1 (repo scaffold and Pages smoke test).

## Prerequisites (Michel)
- [x] Firebase RTDB created (Singapore region, test mode). URL: `https://imagine-cast-default-rtdb.asia-southeast1.firebasedatabase.app`
- [ ] GitHub repo `imagine-cast` created
- [x] Watch paired for Wi-Fi debugging
- [x] PowerBand project located and opened in Android Studio (toolchain check)

## Tasks
- [ ] 1 Repo scaffold + Pages
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
