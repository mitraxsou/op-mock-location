# OP Mock Location

A small Android test app that feeds fake GPS positions to the phone, so you can test Office Presence (`com.pwc.officepresence`) at your desk. It runs as a foreground service, so it keeps working while Office Presence is open, in the background, or the screen is off.

| Field | Value |
|---|---|
| Package | `com.pwc.mockpresence` |
| Stack | Kotlin, Jetpack Compose (BOM 2024.06.00), Play Services Location 21.3. Same as Office Presence |
| SDK | min 26, target and compile 34 |

## How it works

Office Presence reads location from the fused provider (geofences, `PresenceService` samples, `ArrivalDetect`, Check now). This app turns on **fused mock mode** and also replaces the **GPS and network providers** with test providers. It then pushes one fix every 1 to 5 seconds. So whenever Office Presence asks for a fix, it gets the fake one. Geofence enter and exit fire as the fake point crosses the fence.

When you press Stop, mock mode is turned off and the real GPS comes back.

## Setup (once per phone)

1. Open the folder in Android Studio (Hedgehog or newer). It reads `gradle/wrapper/gradle-wrapper.properties` and downloads Gradle 8.7. From the command line, run `./gradlew assembleDebug` (or `gradlew.bat` on Windows), and `./gradlew testDebugUnitTest` for the unit tests. To sign a release build, put `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in a `keystore.properties` file next to `settings.gradle.kts` (it is gitignored), or set `OPMOCK_STORE_FILE`, `OPMOCK_STORE_PASSWORD`, `OPMOCK_KEY_ALIAS` and `OPMOCK_KEY_PASSWORD`, then run `./gradlew assembleRelease`.
2. Run the `app` configuration on the phone (a debug build is fine for a test tool).
3. Turn on Developer options on the phone (About phone > tap Build number 7 times).
4. Developer options > **Select mock location app** > **OP Mock Location**.
5. Open the app, tap **Grant permissions** (location and notifications).
6. **Offices** tab: add the offices you will test with. Copy centre, radius and points from Office Presence Admin > Offices, or paste the same CSV you would use for the admin bulk import.

## Using it

| Tab | What it does |
|---|---|
| Control | Pick an office and a position: Centre, Edge -10 m, Edge +20 m, Outside +400 m, Far 5 km, or a custom lat, lng. Set accuracy (5, 12, 40, 150 m), jitter, and how often to send a fix. **Mock this position** keeps it there until you stop or change it. |
| Scenarios | Scripted days that move the fake phone through the rules (see the test plan below). Choose office A, office B and the speed. |
| Day plan | Saved days with any number of office stops, a stay at each, and travel in between. See [Day plan](#day-plan). |
| Offices | Add, delete, select, import CSV. |
| Log | Every step and error, newest first. Also in Logcat with tag `OPMock`. |

The status card shows the fake point, the nearest office, the distance from its centre, and what Office Presence should see right now (In, Out, or ignored because of accuracy).

The notification has **Inside**, **Outside** and **Stop** buttons. They use the office selected in the app, so you can switch without leaving Office Presence.

### Speed

| Speed | 1 scenario minute | Use with |
|---|---|---|
| Real time | 60 s | Normal rules (gap 15 min, sample 5 min). Most accurate. |
| x4 | 15 s | Quick look at notifications and Today. Gap results will not match. |
| x60 | 1 s | Office Presence **Developer mode** (test gap 15 s, sampling 15 s). The gap rule then scales the same way: a 10 minute break becomes 10 s, under the 15 s test gap. |

Speed does not change the phone clock, so visit times and hours in Office Presence will be short. Check the shape of the result (how many visits, In or Out, points), not the hours.

### Day plan

1. Open the **Day plan** tab and tap **New plan**.
2. Add stops, pick an office for each, and set the stay, the travel between stops, and the minutes outside before and after.
3. Tap **Save and run**, or pick a speed on the list and tap **Run** on a saved plan. The card shows the expected visits and day points.

A plan runs through the same engine as the scenarios, so speed, the notification, the status card and the log all work the same. From adb, run a saved plan by name (no extra quotes inside the name):

```bash
adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd plan --es name "Mumbai, BKC, Andheri" --es speed x60
```

`tools/day-plan.sh` still works without the app UI. It drives the same day with `inside` and `outside` commands.

## Test plan

Defaults assumed: `gapMinutes` 15, `sampleIntervalMinutes` 5, `fenceRadiusMeters` 100. Rule references are to `OFFICE_PRESENCE_LIVING_DOC.md`.

| Scenario | Rule tested | Pass when |
|---|---|---|
| Arrive, stay, leave | A3 steps 1 to 3, A4 | Arrived notification, In with "Since", one visit of about 60 min, day counts with the office points, Left notification, Out. |
| Short break (under the gap) | A4 gap | One visit, not two. |
| Long break (over the gap) | A4 gap | Two visits on one day. Points counted once. |
| Two offices in one day | A4 points, travel | Two visits. Travel not counted. Day points = higher office, not the sum. Give the two offices different points to see this. |
| Inside with weak GPS | A3 step 2, fence radius as max accuracy | "Looking for the office", no visit while accuracy is 150 m. Visit starts once accuracy is good. |
| Leave with coarse GPS | A3 step 3 | No immediate Left (exit worse than 15 m ignored). Visit still closes after the gap. |
| Standing at the fence edge | Geofence flapping | One continuous visit. |
| GPS goes quiet inside | A4 gap, exit handling | Visit ends about one gap after the last inside fix. |
| Long stay (midnight test) | A3 step 6 | Start around 23:00. Month shows two days, each counted. Real time only. |

Also worth checking by hand with the Control tab:

| Check | How |
|---|---|
| ArrivalDetect on app open | Mock Centre, force stop Office Presence, open it. Today should show In without walking in. |
| Boot | Mock Centre, reboot, start the mock again, wait. Office Presence should register geofences and show In. |
| Hourly presence ping | Mock Centre, leave Office Presence in the background for over 20 min on a weekday between 09:00 and 19:00. Server log should show a `presence_check` and a new sample. |
| Check now | Mock Outside, tap Check now in Office Presence, then mock Centre and tap again. |
| Correction rules | Use a mocked day, then raise a correction for it in Office Presence. With `correctionRequireSync` on, a day with phone data can auto approve; a day without should go to pending. |

## adb control (scripted tests)

Start any mock once from the app first. Android blocks starting a location service from the background, and after that the commands work while the service is running.

```bash
A="adb shell am broadcast -a com.pwc.mockpresence.CMD"
$A --es cmd inside --es office "Mumbai WC"      # centre of that office (also selects it)
$A --es cmd outside                             # 400 m beyond the fence
$A --es cmd edge --es meters -10                # 10 m inside the fence line
$A --es cmd at --es lat 19.0760 --es lng 72.8777 --es accuracy 150
$A --es cmd scenario --es id long_break --es speed x60
$A --es cmd scenario --es id two_offices --es office "Office A" --es officeB "Office B"
$A --es cmd plan --es name "Mumbai, BKC, Andheri" --es speed x60
$A --es cmd stop
```

Scenario ids: `arrive_stay`, `short_break`, `long_break`, `two_offices`, `poor_accuracy`, `coarse_exit`, `edge_flap`, `gps_silent`, `long_stay`. Optional on any command: `accuracy`, `jitter`, `interval` (ms).

The receiver requires the `DUMP` permission, which the adb shell has and normal apps do not, so other apps on the phone cannot drive it.

## Limits

- Wi-Fi name (`wifiSsid`) is not faked. Events will carry the real network.
- Geofence transitions can take from a few seconds to about 2 minutes after the point crosses the fence. Status updates from `PresenceService` samples follow the sample interval.
- The phone clock is not changed. For the midnight test, run near midnight.
- On Android 12+ every fake fix has `Location.isMock = true`.

## Before you use it against production

Office Presence does not send `isMock` today (gap P0-4), and debug builds talk to production (gap P1-10). Fake fixes from this app are therefore stored as real presence and earn real points. Until those gaps are fixed:

- Test only with **test accounts** (Users > Add user with the test flag, or View as > Start testing). They are hidden from the roster and reports.
- Do not leave Office Presence signed in as a real person while a mock is running.
- Fixing P0-4 is a small change: send `loc.isMock` with each event from `PresenceService` and `ArrivalDetect`, store it on `PresenceEvent`, and ignore or flag mock events for accounts that are not test accounts. This app is then a good way to verify that fix.
