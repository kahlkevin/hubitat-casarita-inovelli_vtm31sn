# Power Reporting Manual Test Matrix

This matrix covers the Hubitat integration behavior that the in-driver automated tests cannot fully exercise: preference handling, actual scheduling, refresh/read mechanics, subscription establishment, hub startup, and physical-device timing.

## Test setup

Use a device that can be placed in these configurations:

- A **stable-load configuration** that produces changing power and energy reports.
- A **threshold-crossing configuration** where inactive power is below 23 mW and active power is reliably at or above 23 mW. Disconnecting the load and disabling the device LED may provide this configuration.

For most timing tests, use the **5-minute throttle**. Enable:

- Power-reporting cache debug output.
- Device event history with timestamps.
- Lifecycle/subscription logging for refresh and reboot tests.

For every test, record:

- Pass/fail.
- Starting mode.
- Power and energy values before and after.
- Event timestamps.
- Relevant cache/debug lines.
- Any unexpected duplicate, missing, or late events.

Hubitat timer execution need not be millisecond-exact. Allow a modest scheduling tolerance around interval boundaries.

## Core reporting behavior

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| C1 | Mode: **Report every change** | Operate the load repeatedly and allow energy reports to arrive. | Every received power and energy report appears promptly as a device event. No quiet-time cache debug messages appear. | |
| C2 | Mode: **5-minute throttle**, bootstrap already expired | Observe an ordinary report that opens a quiet interval, then operate the device normally during that interval. Note which attributes are cached and which produce multiple cached values. Stop activity and wait for expiry. | The opening report appears immediately. Later reports are cached, with no more than the latest observed value retained for each attribute. At expiry, the latest cached power and energy values that were observed are emitted close together, neither attribute displaces the other, and no intermediate value appears later. The next quiet interval begins at the flush time. Claim manual confirmation of per-attribute replacement only for an attribute that produced multiple cached values during the run. | |
| C3 | Mode: **5-minute throttle** | Leave the device operating through at least three full quiet intervals while values continue changing. | Each interval emits the latest cached values. No interval becomes permanently stuck, no value from an earlier interval reappears, and cache debug output remains internally consistent. | |
| C4 | Throttled modes: **5, 15, 30, and 60 minutes** | In five-minute mode, observe an immediate report, a cached ordinary report, and its eventual flush. For each longer mode, select and save the mode, then use debug output to verify the newly established quiet-period deadline after an immediate and a subsequent ordinary report. | The five-minute timer flushes normally. Each longer mode establishes the configured deadline, and changing modes never leaves the prior mode's deadline in effect. Waiting through every longer interval is not required. | |

## Active/inactive power transitions

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| A1 | Device in the threshold-crossing configuration; mode: **5-minute throttle** | Begin a quiet interval while the device is inactive, then toggle between inactive and active several times faster than the throttle interval. End in a known state and wait beyond the final quiet interval. | Every inactive-to-active and active-to-inactive power transition appears immediately and starts a new quiet interval. The final Hubitat power value agrees with the physical state, and no cached value or later callback overwrites it. | |

## In-place upgrade from v1.0

These tests verify the behavior of devices that predate the `powerMonitoring` preference. Use an existing device running the released v1.0 driver, or reproduce that state by installing v1.0 and operating the device before upgrading. Do not open or save the Preferences page after installing v1.1 until the test step explicitly says to do so; saving may materialize the new preference's UI default and conceal a missing-preference migration defect.

Before upgrading, confirm that:

- The device has no stored `powerMonitoring` preference.
- `power` and `energy` are present in Current States.
- Power and energy reports are being published normally.
- The device has a working Matter subscription.

The backward-compatible upgrade behavior is **Report every change**, matching v1.0. An absent `powerMonitoring` preference must never be interpreted as **Disable reporting**.

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| U1 | Untouched v1.0 device with no stored `powerMonitoring` preference | Record the pre-upgrade Current States and recent event history. Install the v1.1 production update over v1.0 without opening or saving Preferences. Operate the load and observe reports. | Existing `power` and `energy` states are preserved. New reports continue to appear as every-change events. Neither attribute is deleted, and the effective reporting mode is not disabled. | |
| U2 | U1 completed; Preferences still not saved | Press **Refresh**, then trigger **Initialize** or otherwise force subscription replacement. Operate the load after each action. | Refresh and subscription construction include both power-reporting paths. Both attributes update promptly and every subsequent report is emitted. No lifecycle action interprets the absent preference as disabled or removes either attribute. | |
| U3 | U2 completed; Preferences still not saved | Reboot the Hubitat hub and wait for initialization and subscription establishment. Then operate the load repeatedly. | Boot-time initialization restores a subscription containing both power-reporting paths. `power` and `energy` remain present or are promptly restored, and reporting continues in every-change mode. | |
| U4 | Upgraded device is operating in the backward-compatible every-change mode | Save an unrelated preference without deliberately changing **Power and energy reports**. Then Refresh and reboot once more. | The effective mode remains **Report every change** across preference processing, Refresh, and reboot. The new preference is either explicitly stored as `1` or an absent value continues to resolve to `1`; it never resolves to disabled. | |
| U5 | U1-U4 completed | Explicitly select **Disable reporting** and save, then explicitly select **Report every change** and save. | The explicit disable removes both attributes and reporting paths as in P1/P2. Re-enabling restores both paths and attributes as in P3. Upgrade compatibility does not override an explicit user choice. | |

## Preference-transition behavior

These tests are particularly valuable because preference application and its resulting refresh are intentionally not deeply automated.

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| P1 | Mode: **5-minute throttle**, with cached values pending | Change to **Disable reporting** and save. | Pending values are discarded, any scheduled flush is cancelled, and both `power` and `energy` disappear from Current States. The replacement subscription omits both reporting paths, and the attributes remain absent. If any already-in-flight report is observed, it does not recreate them. | |
| P2 | Mode: **Disable reporting** | Change to **Report every change** and save. | The preference update establishes a replacement subscription containing both reporting paths. Its initial reports promptly restore power and energy; subsequent reports are all emitted immediately. | |
| P3 | Mode: **Disable reporting** | Change to **5-minute throttle** and save. | The replacement subscription contains both reporting paths, and its initial reports promptly restore both attributes. Reports received during the first ten seconds are immediate. The configured quiet interval begins when bootstrap ends. | |
| P4 | Mode: **5-minute throttle**, with cached values pending | Change to **Report every change** and save. | Pending values are emitted during preference processing. No power-reporting refresh occurs. Afterward, all reports are immediate and no old scheduled callback produces another stale event. | |
| P5 | Mode: **5-minute throttle**, with cached values pending | Change to **15-minute throttle** and save. | Pending values are emitted during preference processing, with no power-reporting refresh. The next report opens a 15-minute quiet interval, and no former five-minute deadline remains effective. | |
| P6 | Mode: **Report every change** | Change to a throttled mode and save. | No power-reporting refresh occurs. The next report is emitted immediately and opens the newly configured quiet interval; subsequent ordinary reports within it are cached. | |
| P7 | Mode: **5-minute throttle**, with cached values pending | Change an unrelated driver preference and save without changing the power-reporting mode. | Power-reporting state is untouched: pending values remain cached, the existing timer and quiet-period deadline remain in effect, and no refresh attributable to power reporting occurs. The values flush normally at the original deadline. | |

Pending values can be emitted while processing an enabled-to-enabled preference transition. This is expected and does not indicate an extra device refresh.

## Refresh bootstrap

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| R1 | Mode: **15 minutes** or longer, with cached values pending | Note the cached values, press **Refresh**, and observe reports returned during the next ten seconds. Continue observing beyond the discarded timer's former deadline. | Cached values are silently discarded, the pending timer is cancelled, and power and energy refresh promptly rather than remaining stale for the long throttle interval. Recognized reports during bootstrap appear immediately without cache debug output, and no discarded value appears later. | |
| R2 | Mode: **5-minute throttle** | Press Refresh. After ten seconds, cause an ordinary non-transition report and then another shortly afterward. | Both reports are cached within the quiet interval already anchored at bootstrap expiry, then the latest values flush at its deadline. | |
| R3 | Mode: **Disable reporting** | Press Refresh. | `power` and `energy` are deleted immediately. The subscription-derived read omits both reporting paths, and the attributes remain absent. | |

## Startup and subscription establishment

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| B1 | Mode: **15 minutes** or longer; note current values | Reboot the Hubitat hub and wait for driver initialization and subscription establishment. | Both power and energy are restored promptly after startup. Neither remains stale for the configured throttle interval. | |
| B2 | Mode: **Disable reporting** | Reboot the hub. | The boot-time subscription omits both reporting paths. Power and energy remain absent after initialization and subscription establishment. | |
| B3 | Mode: **5-minute throttle**, with one or both values cached | Before the pending timer expires, trigger **Initialize** or another normal driver action that replaces the subscription. Observe the initial reports, then allow ordinary reports to continue and wait beyond the old timer deadline. | Old cached values and their timer are discarded. Refresh/bootstrap runs as part of subscription setup, initial subscription reports update both attributes promptly, and subsequent reports return to normal throttling. The old callback never emits stale data later. | |

## Timer and stale-callback integration stress

Exact callback races are covered deterministically by the automated tests. These scenarios instead look for practical integration symptoms while exercising Hubitat's real scheduler.

| ID | Condition | Sequence | Expected result and observations | Result/evidence |
| --- | --- | --- | --- | --- |
| S1 | Mode: **5-minute throttle** | Repeatedly alternate physical activity, Refresh—including while the load is changing—and idle periods over 30-60 minutes. | Refresh responses converge on recent device values even with concurrent traffic. No cache becomes stuck, no old value appears after a newer one, and reports continue to flush over later intervals. Repeat Refresh if necessary to confirm reliable recovery from an inconclusive concurrent ordering. | |
| S2 | Mode: **5-minute throttle** | Run the real-load device for several hours or overnight. | Latest-value coalescing continues across many intervals. No timer stops firing, no unbounded logging occurs, and displayed values remain plausible. | |

## Completion gate

Consider the feature ready when:

- [ ] All in-place upgrade tests U1-U5 pass on a device whose v1.0 `powerMonitoring` preference is genuinely absent.
- [ ] Run the core reporting tests C1-C4 after establishing the upgrade or fresh-install baseline and before relying on the throttle behavior in later stress tests; all pass, with the C2 evidence identifying which attributes actually repeated and C3 covering at least three consecutive ordinary quiet intervals on a real load.
- [ ] All preference transitions P1-P7 pass.
- [ ] Refresh tests R1-R3 pass, especially long-interval refresh.
- [ ] Boot and subscription-establishment tests B1-B3 pass.
- [ ] Aggressive switching A1 produces no stale final state.
- [ ] Run integration stress test S1 after the Refresh and preference-transition behavior has passed; no stale callback or stuck cache is observed.
- [ ] Run S2 as the final overnight soak; it produces no stuck cache or stale callback.
- [ ] Disabling reporting consistently removes both attributes.

Throttle admission and final event dispatch are separate operations. A physical report can pass the throttle check immediately before Refresh acquires the power-reporting lock, then finish dispatching after Refresh has begun resetting state. Refresh therefore does not guarantee perfectly atomic event ordering when reports arrive concurrently. This is acceptable provided that the refresh responses promptly converge on current device values, a repeated Refresh offers reliable recovery if necessary, and no cached value or scheduled callback from before the Refresh later overwrites the recovered state.

The development-only full-subscription attribute scan intentionally bypasses power-reporting path filtering. When reporting is disabled, `pr_consumeReport` still suppresses its power and energy reports so they do not recreate Current States. Treat the scan as an expert operation and avoid preference, Refresh, Initialize, or Configure actions until it completes.
