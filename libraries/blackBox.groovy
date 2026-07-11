// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** BLACK BOX LIBRARY (symbol prefix: bb)
****************************************************************************************************************************/
// #else
/**
 *  Black Box Library (symbol prefix: bb)
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
    name: "blackBox",
    description: "Gather data along specific telemetry channels during operation and then save to a file on demand (debug tool)",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// BB PUBLIC API
// --

List<Map> bb_genPrefs() {
    [[name: __bb_blackBoxPref, type: "bool", title:"<b>Enable black box</b>", description: "Record selected telemetry signals during operation. Afterward, save to hub-local file using the associated debug trigger command. Then find the filename in the log output.", defaultValue:false, group: [name: "10debug", pri: 40]]]
}

void bb_updated() {
    if (settings.enableBlackBox != (state.blackBoxRecording ?: false)) {
        if (settings.enableBlackBox) __bb_clearStore()
        state.blackBoxRecording = settings.enableBlackBox
    }
}

void bb_recordEntry(String value, String channel) {
    if (!settings.enableBlackBox) return
    __bb_recordEntry([now: now(), channel: channel, value: value])
}

void bb_recordEntry(Map map) {
    if (!settings.enableBlackBox) return
    __bb_recordEntry([now: now(), channel: null] << map)
}

void bb_recordEntry(Closure block) {
    if (!settings.enableBlackBox) return
    __bb_recordEntry([now: now(), channel: null] << block?.call())
}

int bb_recordCount() {
    return __bb_blackBoxStorage?.get(device.getDeviceNetworkId())?.count?.get() ?: 0
}

void bb_saveEntries() {
    def old = __bb_clearStore()
    if (old == null) return

    try {
        String fileName = dl_saveToFile("black_box", old.list as List)
        infoAlways "Black box saved to hub File Manager as '${fileName}'"
    } catch (AssertionError | Exception e) {
        error e
    }
}

// -------------------------------------------------------------------------------------------------------------------------
// BB PRIVATE API
// --

@Field static ConcurrentHashMap __bb_blackBoxStorage = new ConcurrentHashMap(16, 0.75, 1)
@Field static final String __bb_blackBoxPref = "enableBlackBox"
// #define L_BB_RECORDLIMIT 1000

static Map __bb_newStore() {
    return [count: new AtomicInteger(0), list: new ConcurrentLinkedQueue<Map>()]
}

Map __bb_clearStore() {
    def old
    __bb_blackBoxStorage?.computeIfPresent(device.getDeviceNetworkId()) { k, v ->
        old = v
        return __bb_newStore()
    }
    return old
}

void __bb_recordEntry(Map map) {
    Map store = __bb_blackBoxStorage.computeIfAbsent(device.getDeviceNetworkId(), { __bb_newStore() })
    assert store
    store.list.add(map)
    // It's ok for the list addition and the increment to not be synchronized with each other
    if (store.count.incrementAndGet() >= @L_BB_RECORDLIMIT) device.updateSetting(__bb_blackBoxPref, false)
}
