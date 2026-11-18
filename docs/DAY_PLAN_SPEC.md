# Day plan: spec for OP Mock Location

| Field | Value |
|---|---|
| Feature | Day plan: mock a day with any number of office visits and stays |
| App | OP Mock Location (`com.pwc.mockpresence`) |
| Status | Ready to build |
| Size | About 1 Cursor task. 4 new files, 4 changed files |

> **Prompt for Cursor.** Implement the Day plan feature exactly as described in `docs/DAY_PLAN_SPEC.md`. Follow the file list in section 6, reuse the existing engine in `MockService` (do not write a second location loop), keep the UI copy word for word, and check every item in section 8 before finishing. Keep the existing code style: Compose Material 3, no new libraries, no em dashes in UI copy.

---

## 1. Goal

Today the app can mock one office, or two offices with the "Two offices in one day" scenario. Testers need to mock a full day with 3 or more offices, a stay at each, and travel in between, then save it and run it again.

A **day plan** is an ordered list of **stops**. Each stop is an office plus a stay in minutes, and every stop except the last has a travel time to the next one. A plan runs through the same engine as the built-in scenarios: same speeds, same notification, same status card, same log.

## 2. Screens

The app gets a fifth tab, **Day plan**, between Scenarios and Offices. Switch `TabRow` to `ScrollableTabRow` (edge padding 0) so five tabs fit on small phones.

Tab order: Control, Scenarios, **Day plan**, Offices, Log.

### 2.1 Plan list (Day plan tab, default view)

```
┌─────────────────────────────────────┐
│ [status card, unchanged]            │
│ Control  Scenarios  DAY PLAN  ...   │
│                                     │
│ Saved plans                         │
│ ┌─────────────────────────────────┐ │
│ │ Mumbai, BKC, Andheri            │ │
│ │ 3 stops · 5 h 15 min            │ │
│ │ Mumbai WC 90 → BKC Tower 120 →  │ │
│ │ Andheri Hub 60                  │ │
│ │ Expect: 3 visits · 2 points     │ │
│ │ [ Run ]  [ Edit ]   [ Delete ]  │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ Same office twice               │ │
│ │ ...                             │ │
│ └─────────────────────────────────┘ │
│                                     │
│ Speed                               │
│ (Real time) (x4) (x60)              │
│ <speed hint, same as Scenarios>     │
│                                     │
│ [        + New plan          ]      │
└─────────────────────────────────────┘
```

| Element | Behaviour |
|---|---|
| Plan card | Title = plan name. Line 2 = `n stops · <total>`, total at real time, formatted `5 h 15 min` or `45 min`. Line 3 = stops joined with ` → `, each `<office name> <stay>`. Line 4 = expected result (section 4.3). |
| Run | Starts the plan at the selected speed. Runs through `MockService` (section 5). |
| Edit | Opens the editor with this plan. |
| Delete | Asks "Delete this plan?" with Delete and Cancel, then removes it. |
| Speed chips | Same three options and hints as the Scenarios tab. Extract the chips and hint into a shared composable `SpeedPicker`, used by both tabs. |
| New plan | Opens the editor with an empty plan: name "New plan", one stop at the selected office, stay 60. |
| Empty state | Heading "Plan a day across offices". Body "Add stops, set how long to stay and travel, then run it like a scenario." Button "New plan". |
| No offices | Same text as other tabs: "Add an office first in the Offices tab." |
| Missing office | If a stop's office was deleted, the card shows "Office missing, edit this plan" in error colour and Run is hidden. |

### 2.2 Plan editor

Shown in place of the list (not a new Activity). A top row has "← Plans" (back, discards unsaved changes after confirm "Discard changes?") and "Save".

```
┌─────────────────────────────────────┐
│ ← Plans                      Save   │
│                                     │
│ Plan name                           │
│ [ Mumbai, BKC, Andheri          ]   │
│                                     │
│ Timeline                            │
│ |██ Mumbai ██|░tr░|███ BKC ███|░|█| │
│ 09:00 start (real time)    14:15    │
│                                     │
│ ┌ 1 ──────────────────────── ↑ ↓ ✕ ┐│
│ │ Office  [ Mumbai WC (100 m) ▾ ]  ││
│ │ Stay    [ − ]  90 min  [ + ]     ││
│ └──────────────────────────────────┘│
│      ⋮ Travel  [ − ] 20 min [ + ]   │
│ ┌ 2 ──────────────────────── ↑ ↓ ✕ ┐│
│ │ Office  [ BKC Tower (150 m) ▾ ]  ││
│ │ Stay    [ − ] 120 min  [ + ]     ││
│ └──────────────────────────────────┘│
│      ⋮ Travel  [ − ] 25 min [ + ]   │
│ ┌ 3 ──────────────────────── ↑ ↓ ✕ ┐│
│ │ Office  [ Andheri Hub (100 m) ▾ ]││
│ │ Stay    [ − ]  60 min  [ + ]     ││
│ └──────────────────────────────────┘│
│ [        + Add stop          ]      │
│                                     │
│ Before and after                    │
│ Start outside   [ − ]  3 min [ + ]  │
│ End outside     [ − ] 20 min [ + ]  │
│                                     │
│ GPS accuracy at stops               │
│ (±5 m) (±12 m) (±40 m) (±150 m)     │
│                                     │
│ Expected in Office Presence         │
│ 3 visits · Day points 2             │
│ Highest office counts, not the sum. │
│ Travel time is not counted.         │
│ <warnings, section 4.4>             │
│                                     │
│ [        Save and run          ]    │
└─────────────────────────────────────┘
```

| Element | Behaviour |
|---|---|
| Plan name | Required, 1 to 40 characters. Error "Enter a plan name." |
| Stop card | Number badge, office dropdown (reuse `OfficePicker`), stay stepper. Icons: up, down, remove (`Icons` are not in the project, so use `TextButton` labels "Up", "Down", "Remove"). Up is hidden on the first stop, Down on the last. Remove is hidden when there is only one stop. |
| Stay stepper | Minus and plus change by 5. Tapping the number opens a small number field. Range 1 to 600 min. |
| Travel row | Between two stops only. Stepper by 5, range 0 to 240 min. 0 means "jump" and the row shows "Jump (no travel)". |
| Add stop | Appends a stop. Office defaults to the first office not already used in the plan, else the selected office. Stay 60. Travel before it 20. Max 12 stops; at 12 the button reads "12 stops is the limit". |
| Timeline | One horizontal bar, full width, 16 dp high. Segments are proportional to minutes: stays in accent colour with the office name if the segment is wide enough, travel and before/after in a neutral colour. Under it: start and end clock time if the plan started now at real time. Pure Compose `Canvas` or `Row` with `weight`. |
| Start outside / End outside | Steppers by 1, range 0 to 60. Defaults 3 and 20. |
| GPS accuracy | Chips, default ±6 m stored as 6 (the "±5 m" chip maps to 5; keep 6 as default if no chip is touched, and show no chip selected). Applies to stays. Travel always uses 10 m. |
| Expected | Computed live (section 4.3). |
| Save | Validates, saves, returns to the list with a snackbar-style line "Plan saved". |
| Save and run | Validates, saves, starts the plan at the speed chosen on the list, returns to the list. |

### 2.3 While a plan runs

No new screen. The existing status card already shows "Scenario", the plan name as label, and "Step x of y". Change two things:

| Change | Detail |
|---|---|
| Mode text | `Day plan` instead of `Scenario` when a plan is running. |
| Step label | Use friendly labels from the builder, for example `At BKC Tower (stop 2 of 3)`, `Travel to Andheri Hub`. |

The notification text uses the same step label.

## 3. Data model

New file `model/DayPlan.kt`:

```kotlin
data class PlanStop(
    val officeId: String,
    val stayMinutes: Int,          // 1..600
    val travelToNextMinutes: Int,  // 0..240, ignored on the last stop
)

data class DayPlan(
    val id: String,
    val name: String,
    val stops: List<PlanStop>,     // 1..12
    val startOutsideMinutes: Int = 3,   // 0..60
    val endOutsideMinutes: Int = 20,    // 0..60
    val accuracyM: Float = 6f,
    val updatedAt: Long = System.currentTimeMillis(),
)
```

New file `data/PlanStore.kt`, same pattern as `OfficeStore`: SharedPreferences `mock_presence`, key `plans`, JSON array, newest `updatedAt` first. Methods: `load()`, `save(plan)` (insert or replace by id), `delete(id)`, `get(id)`, `findByName(name)` (case-insensitive).

## 4. Logic

### 4.1 Plan to steps (new file `model/PlanBuilder.kt`)

`fun build(plan: DayPlan, all: List<Office>): Pair<Scenario, List<Office>>?`

Returns null if any office is missing. The office list passed to the engine is the plan's offices in stop order, **one entry per stop** (so the same office can appear twice), and `Spot.office` is the stop index.

Steps, in order:

1. If `startOutsideMinutes > 0`: `Hold("Outside <stop1 office>", start, outside(0))`.
2. For stop `i`:
   - If `i == 0`: `Move("Walk in to <office>", 2.0, outside(0), desk(0))`. If start outside is 0, skip this move.
   - `Hold("At <office> (stop i+1 of n)", stay, desk(i), accuracyM = plan.accuracyM)`.
   - If not the last stop and travel > 0:
     - Different office from the next stop: `Move("Travel to <next office>", travel, desk(i), desk(i+1), accuracyM = 10f)`.
     - Same office as the next stop: `Move("Step out", travel/2, desk(i), outside(i, 300.0))`, then `Move("Back in to <office>", travel - travel/2, outside(i, 300.0), desk(i+1))`.
   - If travel is 0: no step (the point jumps).
3. After the last stop: `Move("Leave <office>", 2.0, desk(last), outside(last))`, then if `endOutsideMinutes > 0`: `Hold("Gone", end, outside(last))`.

`desk(i)` and `outside(i, m)` are the same helpers as in `Scenarios.kt`. Move them to a small `Spots` object in `model/` so both files use them.

`Scenario.id` = `"plan:" + plan.id`, title = plan name, `officesNeeded` = 1, `expected` = text from 4.3.

### 4.2 Total time

`totalMinutes = start + (2 if start > 0) + sum(stays) + sum(travel except last) + 2 + end`. Show it on the card and the editor. Use the built `Scenario.totalMinutes` so it always matches what runs.

### 4.3 Expected result

Mirror the Office Presence rules (gap 15 min by default, points = highest office of the day):

- **Visits:** start at the number of stops. For each pair of neighbouring stops at the **same** office whose travel is **under 15 min**, subtract 1 (the server keeps one visit open across a short break).
- **Day points:** max `points` of the plan's offices.
- Text: `<v> visits · Day points <p>`, then "Highest office counts, not the sum." if there are 2 or more different offices, then "Travel time is not counted."

### 4.4 Warnings (shown under Expected, warning colour, do not block saving)

| Condition | Text |
|---|---|
| Travel under 15 min between two **different** offices | "Travel to <office> is under the 15 min gap. Office Presence should still split the visits because the office changes." |
| Accuracy over 100 m | "Accuracy worse than 100 m. Office Presence ignores these fixes, so no visits should appear." |
| Total at real time ends after 23:59 today | "At real time this plan crosses midnight. Visits will be split into two days." |
| Two stops overlap fences (centres closer than the sum of radii) | "<A> and <B> fences overlap. Travel between them may never look like Out." |

## 5. Service and adb changes

### 5.1 `MockService`

- Add `ACTION_PLAN` and `EXTRA_PLAN_ID`.
- Refactor `runScenario(i: Intent)` into two parts:
  - `runScenario(i)` resolves the built-in scenario and offices A and B as today, then calls `runSteps(sc, offices, secPerMin, mode = "Scenario")`.
  - New `runPlan(i)` loads the plan from `PlanStore`, calls `PlanBuilder.build`, logs "Plan '<name>' with n stops" or "Plan has a missing office" and returns, then calls `runSteps(sc, offices, secPerMin, mode = "Day plan")`.
- `runSteps` is today's loop body unchanged, except the mode string is passed in and used in `emit` and in the Silence status update.
- Wherever the code reads `knownOffices` for the nearest office, keep it as the full saved office list.

### 5.2 `CommandReceiver`

New command:

```bash
adb shell am broadcast -a com.pwc.mockpresence.CMD --es cmd plan --es name "'Mumbai, BKC, Andheri'" --es speed x60
```

Find the plan by name with `PlanStore.findByName`. Unknown name logs "adb: no plan named '<name>'". Speed parsing is the same as `scenario`.

### 5.3 README

Add a "Day plan" section with a 3 line how-to and the adb command, and note that `tools/day-plan.sh` still works without the app UI.

## 6. Files

| File | Change |
|---|---|
| `model/DayPlan.kt` | New. Data classes from section 3. |
| `model/Spots.kt` | New. `desk`, `centre`, `outside`, `edge` helpers moved out of `Scenarios.kt`. |
| `model/PlanBuilder.kt` | New. `build`, `expectedText`, `warnings`. Pure Kotlin, no Android imports, so it can be unit tested. |
| `data/PlanStore.kt` | New. |
| `ui/DayPlanScreen.kt` | New. `DayPlanTab`, `PlanCard`, `PlanEditor`, `StopCard`, `Stepper`, `PlanTimeline`. |
| `model/Scenarios.kt` | Use `Spots`. |
| `mock/MockService.kt` | Section 5.1. |
| `mock/CommandReceiver.kt` | Section 5.2. |
| `ui/MainActivity.kt` | Fifth tab, `ScrollableTabRow`, shared `SpeedPicker`, make `OfficePicker`, `ChipRow`, `Label`, `Hint`, `startService` `internal` so `DayPlanScreen.kt` can use them. |
| `README.md` | Section 5.3. |

Optional, recommended: `app/src/test/java/.../PlanBuilderTest.kt` with JUnit 4 (add `testImplementation("junit:junit:4.13.2")`). Cases in section 8.

## 7. Copy

All copy is in sections 2 and 4. Keep it exactly. Rules: sentence case, short plain sentences, no em or en dashes, no "please", no exclamation marks.

## 8. Acceptance checks

**Builder (unit tests or manual):**

1. Plan A 90, travel 20, B 120, travel 25, C 60, start 3, end 20 gives steps: Outside, Walk in, At A, Travel to B, At B, Travel to C, At C, Leave, Gone. Total 342 min.
2. Same plan: expected "3 visits · Day points <max>".
3. Plan A 30, travel 10, A 30: steps include Step out and Back in; expected visits 1.
4. Plan A 30, travel 25, A 30: expected visits 2.
5. Plan with travel 0 between A and B: no travel step; expected visits 2.
6. Plan with a deleted office: `build` returns null; card shows "Office missing, edit this plan" and no Run button.

**On a phone with Office Presence (test account, Developer mode on, speed x60):**

7. Run the 3 office plan. Office Presence Today shows three visits, one per office, and the day counts once with the highest office points.
8. The status card mode reads "Day plan" and the step label names the current office and stop number. The notification shows the same label.
9. Inside and Outside in the notification interrupt the plan and switch to a fixed position (existing behaviour).
10. Stop returns real GPS. Starting a scenario while a plan runs replaces the plan.
11. Plans survive closing and reopening the app.
12. `adb ... --es cmd plan --es name ...` runs the named plan once any mock has been started from the app.

**UI:**

13. Five tabs fit and scroll on a 360 dp wide phone.
14. Up, Down and Remove reorder and delete stops and the timeline and expected text update at once.
15. Leaving the editor with unsaved changes asks "Discard changes?".
16. Light and dark mode both readable.
