// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** EVENT SEQUENCING LIBRARY (symbol prefix: es)
****************************************************************************************************************************/
// #else
/**
 *  Event Sequencing Library (symbol prefix: es)
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
    name: "eventSequencer",
    description: "Event sequencing",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// #endif

@Field static final int EVENT_REORDER_WINDOW_MS = 50
@Field static final int EVENT_REORDER_BUFFER_MAX = 32
@Field static final ConcurrentHashMap<String, Object> __eventSequencerLocks = new ConcurrentHashMap(16, 0.75, 1)
@Field static final ConcurrentHashMap<String, Map> __eventSequencerState = new ConcurrentHashMap(16, 0.75, 1)

Object __es_lock() {
    __eventSequencerLocks.computeIfAbsent(device.getDeviceNetworkId(), { new Object() })
}

Map __es_state() {
    __eventSequencerState.computeIfAbsent(device.getDeviceNetworkId(), {
        [
            pending: new ConcurrentHashMap<BigInteger, Map>(EVENT_REORDER_BUFFER_MAX, 0.75, 1),
            generation: 0L,
            activeToken: null,
            windowStartedMs: 0L,
            highBufferedSerial: null,
        ]
    })
}

BigInteger __es_highWater() {
    def hw = state.eventSerialHighWater ?: 0
    (hw instanceof BigInteger) ? hw : new BigInteger("${hw}")
}

void __es_advanceHighWater(BigInteger eventSerial) {
    if (eventSerial > __es_highWater()) state.eventSerialHighWater = eventSerial
}

void __es_scheduleWindowLocked(Map erState) {
    try {
        Long token = ((erState.generation ?: 0L) as Long) + 1L
        erState.generation = token
        erState.activeToken = token
        erState.windowStartedMs = now()
        runInMillis(EVENT_REORDER_WINDOW_MS, @NAMEOF(__es_flushReorderTimer), [data: [token: token]])
    } catch (AssertionError | Exception e) {
        error e
    }
}

void __es_clearReorderStateLocked(String reason) {
    Map erState = __es_state()
// #ifdef ENABLE_DEBUG_EVENTS
    if (erState.pending) debug "Clearing event reorder buffer (${reason}, count = ${erState.pending.size()})"
// #endif
    erState.pending.clear()
    erState.activeToken = null
    erState.windowStartedMs = 0L
    erState.highBufferedSerial = null
    unschedule(@NAMEOF(__es_flushReorderTimer))
}

void __es_flushReorderBufferLocked(String reason, Long token = null) {
    Map erState = __es_state()

    if (token != null && erState.activeToken != token) {
        warn "Ignoring stale event reorder flush (${reason}, token = ${token}, active = ${erState.activeToken})"
        return
    }

    if (reason != "timer") unschedule(@NAMEOF(__es_flushReorderTimer))

    Map pending = erState.pending
    if (!pending) {
        erState.activeToken = null
        erState.windowStartedMs = 0L
        erState.highBufferedSerial = null
// #ifdef ENABLE_DEBUG_EVENTS
        debug "Ignoring empty event reorder flush (${reason})"
// #endif
        return
    }

    Long residenceMs = erState.windowStartedMs ? now() - (erState.windowStartedMs as Long) : null
    List<Map> sorted = pending.values().sort { Map a, Map b -> a.eventSerialInt <=> b.eventSerialInt }
    BigInteger lowSerial = sorted.first().eventSerialInt
    BigInteger highSerial = sorted.last().eventSerialInt
// #ifdef ENABLE_DEBUG_EVENTS
    debug "Flushing event reorder buffer (${reason}, count = ${sorted.size()}, low = ${lowSerial}, high = ${highSerial}, ageMs = ${residenceMs})"
// #endif

    pending.clear()
    erState.activeToken = null
    erState.windowStartedMs = 0L
    erState.highBufferedSerial = null

    sorted.each { Map eventMap ->
        BigInteger hw = __es_highWater()
        if (eventMap.eventSerialInt > hw) {
            try {
                getApi().driver.dispatchMsg.call(eventMap)
                __es_advanceHighWater(eventMap.eventSerialInt)
            } catch (AssertionError | Exception e) {
                error e
            }
// #ifdef ENABLE_DEBUG_EVENTS
        } else {
            debug "Rejecting (below highwater [hw = ${hw}, evtSerial = ${eventMap.eventSerialInt}]): ${eventMap}"
// #endif
        }
    }
}

void __es_flushReorderBuffer(String reason, Long token = null) {
    synchronized(__es_lock()) {
        __es_flushReorderBufferLocked(reason, token)
    }
}

void __es_flushReorderTimer(Map data = null) {
    Long token = data?.token as Long
    __es_flushReorderBuffer("timer", token)
}

void es_subWaitStarted() {
    synchronized(__es_lock()) {
        getApi().driver.setSubCompleteLocked.call(false)
        __es_flushReorderBufferLocked("resubscribe")
        __es_clearReorderStateLocked("resubscribe")
    }
}

void es_subWaitFinished() {
    synchronized(__es_lock()) {
        __es_clearReorderStateLocked("subscriptionResult")
        getApi().driver.setSubCompleteLocked.call(true)
    }
}

void es_sequenceEvent(Map matterMsg, String description) {
    synchronized(__es_lock()) {
        BigInteger eventSerial = matterMsg.eventSerialInt
        BigInteger hw = __es_highWater()

        if (!getApi().driver.isSubComplete.call()) {
            __es_advanceHighWater(eventSerial)
// #ifdef ENABLE_DEBUG_EVENTS
            debug "Rejecting (sub not complete, consumed playback [hw = ${state.eventSerialHighWater}, evtSerial = ${eventSerial}]): ${description}"
// #endif
            return
        }

        if (eventSerial <= hw) {
// #ifdef ENABLE_DEBUG_EVENTS
            debug "Rejecting (below highwater [hw = ${hw}, evtSerial = ${eventSerial}]): ${description}"
// #endif
            return
        }

        Map erState = __es_state()
        Map pending = erState.pending
        if (pending.containsKey(eventSerial)) {
// #ifdef ENABLE_DEBUG_EVENTS
            debug "Rejecting duplicate pending event (evtSerial = ${eventSerial}): ${description}"
// #endif
            return
        }

        boolean wasEmpty = !pending
        BigInteger highBuffered = erState.highBufferedSerial
// #ifdef ENABLE_DEBUG_EVENTS
        if (highBuffered != null && eventSerial < highBuffered) {
            debug "Observed out-of-order event arrival (highestBuffered = ${highBuffered}, evtSerial = ${eventSerial}, depth = ${highBuffered - eventSerial})"
        }
// #endif

        pending[eventSerial] = matterMsg
        if (highBuffered == null || eventSerial > highBuffered) erState.highBufferedSerial = eventSerial

        if (wasEmpty) __es_scheduleWindowLocked(erState)
        if (pending.size() >= EVENT_REORDER_BUFFER_MAX) __es_flushReorderBufferLocked("cap")
    }
}
