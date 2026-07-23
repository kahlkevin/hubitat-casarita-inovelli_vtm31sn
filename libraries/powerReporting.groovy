// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** POWER REPORTING LIBRARY (symbol prefix: pr)
****************************************************************************************************************************/
// #else
/**
 *  Power Reporting Library (symbol prefix: pr)
 *
 *  SPDX-FileCopyrightText: 2026 Kevin Kahl
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright 2026 Kevin Kahl
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

library (
    base: "driver",
    category: "matter",
    name: "powerReporting",
    description: "Power monitoring and reporting control",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// @AT(INCLIB)(matterHelpers)
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// PR PUBLIC API
// --

@Field static final String EVT_POWER = "power"
@Field static final String EVT_ENERGY = "energy"

List<Map> pr_genPrefs() {
    [
        [name: @NAMEOF(powerMonitoring), type: "enum", title: "<b>Power and energy reports</b>", description: "Coalescing reduces event traffic during steady operation by holding repeated changes and reporting only the latest. On/off changes and Refresh still report promptly.", defaultValue: __pr_defaultMode, options: __pr_modeOptions],
    ]
}

void pr_updated() {
    int reportingMode = __pr_configuredReportingMode()
    int previousReportingMode
    synchronized(__pr_lock()) {
        previousReportingMode = __pr_appliedReportingMode()
        if (previousReportingMode != reportingMode) __pr_lockedApplyReportingMode(reportingMode)
    }
    if ((previousReportingMode == 0) != (reportingMode == 0)) __pr_resubscribe()
}

boolean pr_includeSubscriptionEntity(Map entity) {
    // Subscription entities use the hubEventMake ep/cluster/attr-or-evt schema.
    __pr_appliedReportingMode() != 0 || __pr_reportType(entity.cluster as Integer, entity.attr as Integer) == null
}

void pr_refreshStarting() {
    try {
        synchronized(__pr_lock()) {
            __pr_lockedBeginRefresh()
        }
    } catch (AssertionError | Exception e) {
        error e
    }
}

// Returns true when this library consumes matterMsg by suppressing it or queuing it for later dispatch.
boolean pr_consumeReport(Map matterMsg) {
    try {
        String reportType = __pr_reportType(matterMsg)
        if (reportType == null) return false
        int reportingMode = __pr_appliedReportingMode()
        if (reportingMode == 0) return true
        if (reportingMode == 1) return false
        synchronized(__pr_lock()) {
            Map prState = __pr_deviceState()

            // Re-check conditions now that we've acquired critical section
            reportingMode = prState.appliedReportingMode as int
            if (reportingMode == 0) return true
            if (reportingMode == 1) return false

            long now = __pr_now()
            if (now < prState.refreshBootstrapUntil) return false
            if (now >= prState.coalescingUntil || (reportType == EVT_POWER && (__pr_isLoadActive(matterMsg.decodedValue) != __pr_isLoadActive(__pr_currentReportedPowerMw())))) {
                prState.coalescingUntil = now + reportingMode * 1000
                __pr_lockedCancelFlushIfScheduled()
                __pr_lockedDispatchPendingExcept(reportType)
                return false
            } else {
                prState.pendingByType[reportType] = matterMsg
                if (!prState.flushScheduled) {
                    __pr_scheduleFlushCallback(prState.coalescingUntil - now)
                    prState.flushScheduled = true
                }
// #ifdef ENABLE_DEBUG_POWER_REPORTING
//  #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
                if (__pr_testContext() == null)
//  #endif
                debug { "prState.pendingByType[${reportType}] updated. prState = ${pr_lockedStateString()}" }
// #endif
                return true
            }
        }
    } catch (AssertionError | Exception e) {
        error e
    }
    return true
}
// #ifdef ENABLE_DEBUG_POWER_REPORTING

String pr_stateString() { synchronized(__pr_lock()) { pr_lockedStateString() } }
String pr_lockedStateString() {
    Map prState = __pr_deviceState()
    Map s = prState.clone()
    s.pendingByType = prState.pendingByType.collect { [(it.key): [decodedValue: it.value.decodedValue]] }
    "prState (@${now()}) = ${s}"
}

void pr_clearState() {
    synchronized (__pr_lock()) {
        if (__pr_lockedCancelFlushIfScheduled()) __pr_lockedDispatchPendingExcept(null)
        __pr_stateByDni.remove(device.getDeviceNetworkId())
    }
}
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// PR PRIVATE API
// --

@Field static final long __pr_refreshBootstrapMs = 10_000L
@Field static final int __pr_activeMw = 23  // Observed using f/w 1.1.5 and no load wired to physical device
@Field static final int __pr_defaultMode = 1
@Field static final Map __pr_modeOptions = [
    0:      "Disable reporting",
    1:      "Report every change",
    300:    "Coalesce reports over a 5-minute interval",
    900:    "Coalesce reports over a 15-minute interval",
    1800:   "Coalesce reports over a 30-minute interval",
    3600:   "Coalesce reports over a 60-minute interval",
].asImmutable()

@Field static final ConcurrentHashMap<String, Object> __pr_locks = new ConcurrentHashMap(16, 0.75, 1)
@Field static final ConcurrentHashMap<String, Map> __pr_stateByDni = new ConcurrentHashMap(16, 0.75, 1)
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
@Field static final ConcurrentHashMap<String, Map> __pr_testContexts = new ConcurrentHashMap(4, 0.75, 1)
// #endif

Object __pr_lock() { __pr_locks.computeIfAbsent(device.getDeviceNetworkId(), { new Object() }) }

Map __pr_deviceState() {
    __pr_stateByDni.computeIfAbsent(device.getDeviceNetworkId(), {
        __pr_createState(null)
    })
}

Map __pr_createState(Integer reportingMode) {
    [
        appliedReportingMode: reportingMode,
        refreshBootstrapUntil: 0L,
        coalescingUntil: 0L,
        flushScheduled: false,
        pendingByType: [:],
    ]
}

Map __pr_replaceState(Integer reportingMode = __pr_deviceState().appliedReportingMode as Integer) {
    __pr_stateByDni.put(device.getDeviceNetworkId(), __pr_createState(reportingMode))
}

void __pr_lockedApplyReportingMode(int reportingMode) {
    if (__pr_lockedCancelFlushIfScheduled() && reportingMode != 0) __pr_lockedDispatchPendingExcept(null)
    __pr_replaceState(reportingMode)
    if (reportingMode == 0) __pr_deletePowerAttributes()
}

void __pr_lockedBeginRefresh() {
    int reportingMode = __pr_appliedReportingMode()
    __pr_lockedCancelFlushIfScheduled()
    __pr_replaceState()
    if (reportingMode == 0) __pr_deletePowerAttributes()
    else {
        Map prState = __pr_deviceState()
        long refreshBootstrapUntil = __pr_now() + __pr_refreshBootstrapMs
        prState.refreshBootstrapUntil = refreshBootstrapUntil
        prState.coalescingUntil = refreshBootstrapUntil + reportingMode * 1000L
    }
}

void __pr_deletePowerAttributes() {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) {
        testContext.deleteCalls++
        return
    }
// #endif
    device.deleteCurrentState(EVT_POWER)
    device.deleteCurrentState(EVT_ENERGY)
}

String __pr_reportType(Integer clusterId, Integer attrId) {
    if (clusterId == 0x90 && attrId == 0x0008) return EVT_POWER
    if (clusterId == 0x91 && attrId == 0x0001) return EVT_ENERGY
    return null
}

String __pr_reportType(Map matterMsg) { __pr_reportType(matterMsg.clusterInt as Integer, matterMsg.attrInt as Integer) }

long __pr_now() {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) return testContext.nowMs as long
// #endif
    return now()
}

int __pr_configuredReportingMode() {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) return testContext.intervalSec as int
// #endif
    if (powerMonitoring == null) powerMonitoring = __pr_defaultMode
    return mh_prefInt(powerMonitoring)
}

void __pr_resubscribe() {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) {
        testContext.resubscribeCalls++
        return
    }
// #endif
    getApi().driver.resubscribe()
}

int __pr_appliedReportingMode() {
    Integer reportingMode = __pr_deviceState().appliedReportingMode as Integer
    if (reportingMode != null) return reportingMode

    synchronized(__pr_lock()) {
        // Another execution may have initialized or replaced the state before this lock was acquired.
        Map prState = __pr_deviceState()
        reportingMode = prState.appliedReportingMode as Integer
        if (reportingMode == null) {
            reportingMode = __pr_configuredReportingMode()
            // Republish rather than mutate so lock-free readers see the initialized value safely.
            Map replacementState = prState.clone()
            replacementState.appliedReportingMode = reportingMode
            __pr_stateByDni.put(device.getDeviceNetworkId(), replacementState)
        }
    }
    return reportingMode
}

boolean __pr_isLoadActive(valueMw) { (valueMw ?: 0) >= __pr_activeMw }

def __pr_currentReportedPowerMw() {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) return testContext.currentPowerMw
// #endif
    def currentPower = device.currentValue(EVT_POWER)
    currentPower == null ? null : currentPower * 1000
}

void __pr_scheduleFlushCallback(long delayMs) {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) {
        testContext.scheduledAt = testContext.nowMs + delayMs
        testContext.scheduleCalls++
        return
    }
// #endif
    runInMillis(delayMs, @NAMEOF(__pr_runScheduledFlush))
}

void __pr_cancelFlushCallback() {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) {
        testContext.scheduledAt = null
        testContext.cancelCalls++
        return
    }
// #endif
    unschedule(__pr_runScheduledFlush)
}

void __pr_dispatchMsg(Map matterMsg) {
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING
    Map testContext = __pr_testContext()
    if (testContext != null) {
        testContext.dispatched << matterMsg.testId
        if (__pr_reportType(matterMsg) == EVT_POWER) testContext.currentPowerMw = matterMsg.decodedValue
        return
    }
// #endif
    getApi().driver.dispatchMsg.call(matterMsg)
}

void __pr_runScheduledFlush() {
    synchronized(__pr_lock()) {
        Map prState = __pr_deviceState()
        // A cancelled callback may still arrive after reset, or may observe the flag for a replacement timer.
        // Ignore the former; defer the latter until the current quiet period actually ends.
        if (!prState.flushScheduled) return
        long now = __pr_now()
        long remainingMs = prState.coalescingUntil - now
        if (remainingMs > 0) {
            __pr_scheduleFlushCallback(remainingMs)
            return
        }
        __pr_lockedDispatchPendingExcept(null)
        prState.coalescingUntil = now + __pr_appliedReportingMode() * 1000
        prState.flushScheduled = false
    }
}

void __pr_lockedDispatchPendingExcept(String reportType) {
    Map prState = __pr_deviceState()
    Map pendingByType = prState.pendingByType
    pendingByType.each { if (it.key != reportType) __pr_dispatchMsg(it.value) }
    pendingByType.clear()
}

boolean __pr_lockedCancelFlushIfScheduled() {
    Map prState = __pr_deviceState()
    if (prState.flushScheduled) {
        __pr_cancelFlushCallback()
        prState.flushScheduled = false
        return true
    }
    return false
}
// #ifdef ENABLE_TESTS_FOR_POWER_REPORTING

// -------------------------------------------------------------------------------------------------------------------------
// PR TESTS
// --

Map __pr_testContext() { __pr_testContexts.get(device.getDeviceNetworkId()) }

void pr_runAllTests() {
    infoAlways "${__pr_testReportClassification() ? "pass" : "FAIL!"}: power report classification"
    infoAlways "${__pr_testSubscriptionEntityFilter() ? "pass" : "FAIL!"}: subscription entity filter"
    infoAlways "${__pr_testPreferenceResubscription() ? "pass" : "FAIL!"}: preference resubscription"
    infoAlways "${__pr_testAppliedPreferenceCache() ? "pass" : "FAIL!"}: applied preference cache"
    infoAlways "${__pr_testCoalescingAndBoundary() ? "pass" : "FAIL!"}: coalescing and quiet-period boundary"
    infoAlways "${__pr_testTimerFlush() ? "pass" : "FAIL!"}: scheduled flush"
    infoAlways "${__pr_testRefreshBootstrap() ? "pass" : "FAIL!"}: refresh bootstrap"
    infoAlways "${__pr_testStaleCallbackAfterReset() ? "pass" : "FAIL!"}: stale scheduled callback after state reset"
    infoAlways "${__pr_testEarlyStaleCallback() ? "pass" : "FAIL!"}: stale callback before current quiet-period deadline"
    infoAlways "${__pr_testActiveTransitions() ? "pass" : "FAIL!"}: active/inactive power transitions"
    infoAlways "${__pr_testEnergyBeforePower() ? "pass" : "FAIL!"}: energy report before first power report"
}

boolean __pr_runTest(Closure testBody) {
    String dni = device.getDeviceNetworkId()
    Map savedState
    Map testContext = [
        nowMs: 0L,
        intervalSec: 300,
        scheduledAt: null,
        scheduleCalls: 0,
        cancelCalls: 0,
        deleteCalls: 0,
        resubscribeCalls: 0,
        currentPowerMw: null,
        dispatched: [],
    ]

    synchronized(__pr_lock()) {
        savedState = __pr_stateByDni.remove(dni)
        __pr_testContexts.put(dni, testContext)
        try {
            __pr_appliedReportingMode()
            testBody.call(testContext)
            return true
        } catch (AssertionError | Exception e) {
            error e
            return false
        } finally {
            __pr_stateByDni.remove(dni)
            __pr_testContexts.remove(dni)
            if (savedState != null) __pr_stateByDni.put(dni, savedState)
        }
    }
}

boolean __pr_testAppliedPreferenceCache() {
    __pr_runTest { Map testContext ->
        String dni = device.getDeviceNetworkId()
        Map initialState = __pr_deviceState()
        assert initialState.appliedReportingMode == 300

        testContext.intervalSec = 900
        assert __pr_appliedReportingMode() == 300

        initialState.coalescingUntil = 123L
        assert __pr_replaceState().is(initialState)
        Map resetState = __pr_deviceState()
        assert resetState.appliedReportingMode == 300
        assert resetState.coalescingUntil == 0L

        __pr_lockedApplyReportingMode(__pr_configuredReportingMode())
        assert __pr_deviceState().appliedReportingMode == 900

        __pr_stateByDni.remove(dni)
        testContext.intervalSec = 1800
        assert __pr_deviceState().appliedReportingMode == null
        assert __pr_appliedReportingMode() == 1800
        assert __pr_deviceState().appliedReportingMode == 1800
    }
}

Map __pr_testMsg(String testId, int cluster, int attr, def decodedValue) {
    [testId: testId, clusterInt: cluster, attrInt: attr, decodedValue: decodedValue]
}

boolean __pr_testFeed(Map matterMsg) {
    boolean consumed = pr_consumeReport(matterMsg)
    if (!consumed) __pr_dispatchMsg(matterMsg)
    !consumed
}

boolean __pr_testReportClassification() {
    __pr_runTest { Map testContext ->
        Map powerMsg = __pr_testMsg("power", 0x90, 0x0008, 1)
        Map energyMsg = __pr_testMsg("energy", 0x91, 0x0001, 1)
        assert __pr_reportType(powerMsg) == EVT_POWER
        assert __pr_reportType(energyMsg) == EVT_ENERGY
        assert __pr_reportType(__pr_testMsg("other power attr", 0x90, 0x0000, 1)) == null
        assert __pr_reportType(__pr_testMsg("other energy attr", 0x91, 0x0000, 1)) == null
        assert __pr_reportType(__pr_testMsg("other cluster", 0x08, 0x0008, 1)) == null
        assert !pr_consumeReport(__pr_testMsg("other report", 0x08, 0x0008, 1))
    }
}

boolean __pr_testSubscriptionEntityFilter() {
    __pr_runTest { Map testContext ->
        List<Map> entities = [
            [ep: -1, cluster: 0x90, attr: 0x0008],
            [ep: -1, cluster: 0x91, attr: 0x0001],
            [ep: -1, cluster: 0x90, attr: 0x0000],
            [ep: -1, cluster: 0x08, attr: 0x0008],
            [ep: -1, cluster: 0x90, evt: 0x0008],
        ]

        [1, 300, 900, 1800, 3600].each { int reportingMode ->
            __pr_replaceState(reportingMode)
            assert entities.findAll(this.&pr_includeSubscriptionEntity) == entities
        }

        __pr_replaceState(0)
        assert entities.findAll(this.&pr_includeSubscriptionEntity) == entities.drop(2)
    }
}

boolean __pr_testPreferenceResubscription() {
    __pr_runTest { Map testContext ->
        assert __pr_appliedReportingMode() == 300

        pr_updated()
        assert testContext.resubscribeCalls == 0

        testContext.intervalSec = 900
        pr_updated()
        assert testContext.resubscribeCalls == 0

        testContext.intervalSec = 0
        pr_updated()
        assert testContext.resubscribeCalls == 1
        assert testContext.deleteCalls == 1

        pr_updated()
        assert testContext.resubscribeCalls == 1
        assert testContext.deleteCalls == 1

        testContext.intervalSec = 1
        pr_updated()
        assert testContext.resubscribeCalls == 2

        testContext.intervalSec = 300
        pr_updated()
        assert testContext.resubscribeCalls == 2
    }
}

boolean __pr_testCoalescingAndBoundary() {
    __pr_runTest { Map testContext ->
        Map prState = __pr_deviceState()
        assert __pr_testFeed(__pr_testMsg("p0", 0x90, 0x0008, 0))
        assert testContext.dispatched == ["p0"]
        assert prState.coalescingUntil == 300_000L

        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("p10", 0x90, 0x0008, 10))
        assert testContext.scheduledAt == 300_000L
        assert testContext.scheduleCalls == 1

        testContext.nowMs = 20_000L
        assert !__pr_testFeed(__pr_testMsg("e1", 0x91, 0x0001, 1))
        testContext.nowMs = 25_000L
        assert !__pr_testFeed(__pr_testMsg("p15", 0x90, 0x0008, 15))
        assert prState.pendingByType.power.testId == "p15"
        assert prState.pendingByType.energy.testId == "e1"
        assert testContext.scheduleCalls == 1

        testContext.nowMs = 300_000L
        assert __pr_testFeed(__pr_testMsg("e2", 0x91, 0x0001, 2))
        assert testContext.dispatched == ["p0", "p15", "e2"]
        assert prState.pendingByType.isEmpty()
        assert prState.coalescingUntil == 600_000L
        assert !prState.flushScheduled
        assert testContext.scheduledAt == null
        assert testContext.cancelCalls == 1
    }
}

boolean __pr_testTimerFlush() {
    __pr_runTest { Map testContext ->
        Map prState = __pr_deviceState()
        assert __pr_testFeed(__pr_testMsg("p50", 0x90, 0x0008, 50))
        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("p60", 0x90, 0x0008, 60))
        testContext.nowMs = 20_000L
        assert !__pr_testFeed(__pr_testMsg("e1", 0x91, 0x0001, 1))

        assert testContext.scheduledAt == 300_000L
        testContext.nowMs = testContext.scheduledAt
        testContext.scheduledAt = null
        __pr_runScheduledFlush()

        assert testContext.dispatched == ["p50", "p60", "e1"]
        assert prState.pendingByType.isEmpty()
        assert prState.coalescingUntil == 600_000L
        assert !prState.flushScheduled
        assert testContext.scheduleCalls == 1
        assert testContext.cancelCalls == 0
    }
}

boolean __pr_testRefreshBootstrap() {
    __pr_runTest { Map testContext ->
        Map staleState = __pr_deviceState()
        assert __pr_testFeed(__pr_testMsg("p0", 0x90, 0x0008, 0))
        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("p10", 0x90, 0x0008, 10))
        assert staleState.pendingByType.power.testId == "p10"
        assert testContext.scheduledAt == 300_000L

        testContext.nowMs = 20_000L
        pr_refreshStarting()
        Map refreshState = __pr_deviceState()
        assert refreshState.refreshBootstrapUntil == 30_000L
        assert refreshState.coalescingUntil == 330_000L
        assert refreshState.pendingByType.isEmpty()
        assert !refreshState.flushScheduled
        assert testContext.scheduledAt == null
        assert testContext.cancelCalls == 1

        testContext.nowMs = 21_000L
        assert __pr_testFeed(__pr_testMsg("e1", 0x91, 0x0001, 1))
        testContext.nowMs = 22_000L
        assert __pr_testFeed(__pr_testMsg("p50", 0x90, 0x0008, 50))
        assert testContext.dispatched == ["p0", "e1", "p50"]
        assert refreshState.coalescingUntil == 330_000L
        assert refreshState.pendingByType.isEmpty()
        assert !refreshState.flushScheduled
        assert testContext.scheduleCalls == 1

        testContext.nowMs = 24_000L
        pr_refreshStarting()
        Map extendedState = __pr_deviceState()
        assert extendedState.refreshBootstrapUntil == 34_000L
        assert extendedState.coalescingUntil == 334_000L
        testContext.nowMs = 33_000L
        assert __pr_testFeed(__pr_testMsg("p60", 0x90, 0x0008, 60))
        assert extendedState.coalescingUntil == 334_000L

        testContext.nowMs = 34_000L
        assert !__pr_testFeed(__pr_testMsg("e2", 0x91, 0x0001, 2))
        assert extendedState.pendingByType.energy.testId == "e2"
        assert testContext.scheduledAt == 334_000L
        testContext.nowMs = 35_000L
        assert !__pr_testFeed(__pr_testMsg("e3", 0x91, 0x0001, 3))
        assert extendedState.pendingByType.energy.testId == "e3"
        assert testContext.scheduledAt == 334_000L
        assert testContext.dispatched == ["p0", "e1", "p50", "p60"]
        assert testContext.scheduleCalls == 2
        assert testContext.cancelCalls == 1

        testContext.nowMs = 334_000L
        testContext.scheduledAt = null
        __pr_runScheduledFlush()
        assert testContext.dispatched == ["p0", "e1", "p50", "p60", "e3"]
        assert extendedState.pendingByType.isEmpty()
        assert extendedState.coalescingUntil == 634_000L
        assert !extendedState.flushScheduled
    }
}

boolean __pr_testStaleCallbackAfterReset() {
    __pr_runTest { Map testContext ->
        Map staleState = __pr_deviceState()
        assert __pr_testFeed(__pr_testMsg("p50", 0x90, 0x0008, 50))

        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("p60", 0x90, 0x0008, 60))
        assert staleState.pendingByType.power.testId == "p60"
        assert staleState.flushScheduled
        assert testContext.scheduledAt == 300_000L

        assert __pr_lockedCancelFlushIfScheduled()
        assert __pr_replaceState().is(staleState)
        assert testContext.scheduledAt == null
        assert testContext.cancelCalls == 1

        testContext.nowMs = 300_000L
        __pr_runScheduledFlush() // Simulate a cancelled callback that was already queued by Hubitat.

        Map replacementState = __pr_deviceState()
        assert testContext.dispatched == ["p50"]
        assert replacementState.pendingByType.isEmpty()
        assert replacementState.refreshBootstrapUntil == 0L
        assert replacementState.coalescingUntil == 0L
        assert !replacementState.flushScheduled
        assert testContext.scheduleCalls == 1
        assert testContext.cancelCalls == 1
    }
}

boolean __pr_testEarlyStaleCallback() {
    __pr_runTest { Map testContext ->
        assert __pr_testFeed(__pr_testMsg("e0", 0x91, 0x0001, 0))
        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("e1", 0x91, 0x0001, 1))
        assert testContext.scheduledAt == 300_000L

        testContext.nowMs = 20_000L
        pr_refreshStarting()
        testContext.nowMs = 26_000L
        assert __pr_testFeed(__pr_testMsg("e2", 0x91, 0x0001, 2))
        Map currentState = __pr_deviceState()
        assert currentState.coalescingUntil == 330_000L

        testContext.nowMs = 31_000L
        assert !__pr_testFeed(__pr_testMsg("e3", 0x91, 0x0001, 3))
        assert testContext.scheduledAt == 330_000L

        testContext.nowMs = 300_000L
        __pr_runScheduledFlush() // Simulate the cancelled callback observing the replacement timer's flag.
        assert testContext.dispatched == ["e0", "e2"]
        assert currentState.pendingByType.energy.testId == "e3"
        assert currentState.coalescingUntil == 330_000L
        assert currentState.flushScheduled
        assert testContext.scheduledAt == 330_000L
        assert testContext.scheduleCalls == 3

        testContext.nowMs = 330_000L
        testContext.scheduledAt = null
        __pr_runScheduledFlush()
        assert testContext.dispatched == ["e0", "e2", "e3"]
        assert currentState.pendingByType.isEmpty()
        assert currentState.coalescingUntil == 630_000L
        assert !currentState.flushScheduled
        assert testContext.cancelCalls == 1
    }
}

boolean __pr_testActiveTransitions() {
    __pr_runTest { Map testContext ->
        Map prState = __pr_deviceState()
        assert __pr_testFeed(__pr_testMsg("p0", 0x90, 0x0008, 0))
        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("p10", 0x90, 0x0008, 10))
        testContext.nowMs = 20_000L
        assert !__pr_testFeed(__pr_testMsg("e1", 0x91, 0x0001, 1))

        testContext.nowMs = 30_000L
        assert __pr_testFeed(__pr_testMsg("p50", 0x90, 0x0008, 50))
        assert testContext.dispatched == ["p0", "e1", "p50"]
        assert prState.pendingByType.isEmpty()
        assert prState.coalescingUntil == 330_000L

        testContext.nowMs = 40_000L
        assert !__pr_testFeed(__pr_testMsg("p60", 0x90, 0x0008, 60))
        testContext.nowMs = 50_000L
        assert __pr_testFeed(__pr_testMsg("p0-again", 0x90, 0x0008, 0))
        assert testContext.dispatched == ["p0", "e1", "p50", "p0-again"]
        assert prState.pendingByType.isEmpty()
        assert prState.coalescingUntil == 350_000L
        assert testContext.cancelCalls == 2
    }
}

boolean __pr_testEnergyBeforePower() {
    __pr_runTest { Map testContext ->
        Map prState = __pr_deviceState()
        assert __pr_testFeed(__pr_testMsg("e1", 0x91, 0x0001, 1))
        assert testContext.currentPowerMw == null

        testContext.nowMs = 10_000L
        assert !__pr_testFeed(__pr_testMsg("p0", 0x90, 0x0008, 0))
        testContext.nowMs = 20_000L
        assert __pr_testFeed(__pr_testMsg("p50", 0x90, 0x0008, 50))

        assert testContext.dispatched == ["e1", "p50"]
        assert testContext.currentPowerMw == 50
        assert prState.pendingByType.isEmpty()
        assert prState.coalescingUntil == 320_000L
    }
}
// #endif
