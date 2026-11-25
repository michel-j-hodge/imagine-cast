# Gate 0 Log

## Task 5 — Delay test

Setup: room / light: ______  phone height: ______  distance: ______  date: ______

### Run A — watch over Bluetooth proxy
Median round trip: 621 ms (fires)  Max: 885 ms (fires)  Median write: ____ ms  Fired: 20 / 20
Feel (one line): Steady but slower, like the watch loading up.

```csv
seq,power,writeMs,rttMs,note
```

### Run B — watch over Wi-Fi (Bluetooth off)
Median round trip: 389 ms (fires)  Max: 526 ms (fires)  Median write: ____ ms  Casts: 19
Note: the first cast was not acked because the watch was still connecting at startup.
Feel (one line): Instant.

```csv
seq,power,writeMs,rttMs,note
```

### Gate 0 decision
ADVANCE / STAY & REWORK / KILL: **ADVANCE**
Reason: Both networks are inside the §8 bar (median ≤ 800 ms, max ≤ 1500 ms). Wi-Fi is clearly faster (median 389 ms vs 621 ms) and felt instant; Bluetooth proxy felt like a charge-up, not broken.
Decision: play Task 6 and the kid test on Wi-Fi (Bluetooth off on the watch).

## Task 6 — Cast-then-punch
Network: Wi-Fi (per Gate 0 decision)  First punches fired: **20 / 20**  Decision (≥16 continue / <16 local transport): **CONTINUE**, the cloud relay is good enough.
Notes: Throwing a punch right after the sign fires quickly; "that part is solid". Caveat: the detector is too sensitive at 18 m/s² (pointing at a steady pace and bringing the hand down fire; quite a few double punches), so some first-punch hits could have been helped by stray movement. Arming speed is not in question. Open Question in PROGRESS.md; threshold unchanged pending the spike-log capture.

## Task 7 — Range and lighting

| Distance | Normal light hits / 10 | Dim light hits / 10 | Notes |
|---|---|---|---|
| 1.0 m | | | |
| 1.5 m | | | |
| 2.0 m | | | |

## Task 8 — Kid test

Date / players / duration:
Success criteria seen (repeats unprompted, varies strength, invents a move, calls a cousin, uses it in pretend play, asks again, others get it instantly, chaos/laughter):
Observations:
Accidental disarms:
Battery drop:
