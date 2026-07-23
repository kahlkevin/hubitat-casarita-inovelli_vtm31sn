// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** HUB EVENT RETIREMENT LIBRARY (symbol prefix: hr)
****************************************************************************************************************************/
// #else
/**
 *  Hub Event Retirement Library (symbol prefix: hr)
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
    name: "hubEventRetire",
    description: "Hubitat event parser/dispatcher",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// @AT(INCLIB)(matterHelpers)
// #endif

@Field static final Map hr_storageType = [
    CAPABILITYATTRIBUTE: 0,
    ATTRBACKEDPREF: 1,
    DATAVALUE: 2,
    CUSTOM: 3,
]

@Field static final Object hr_unhandled = new Object()
@Field static final String DV_HE_ENDPOINTS = "hubEventEndpoints"

static void hr_hubEventClientInstalled(DeviceWrapper hubEventDevice, int endPoint) { mh_intListSave(hubEventDevice, DV_HE_ENDPOINTS, [endPoint]) }
static void hr_hubEventClientInstalled(DeviceWrapper hubEventDevice, List<Integer> endPoints) { mh_intListSave(hubEventDevice, DV_HE_ENDPOINTS, endPoints) }
static boolean hr_isDeviceInterestedInHubEvent(DeviceWrapper hubEventDevice, int endPoint) { mh_intListLoad(hubEventDevice, DV_HE_ENDPOINTS)?.contains(endPoint) ?: false }

// Map handlers = [
//     onFilter: { Map hubEvent -> ... },
//     onCustom: { Map hubEvent -> ... },
//     onUpdate: { Map hubEvent -> ... },
// ]

void hr_retireHubEvent(Map ev, Map<String, Closure<Map>> handlers = null) {
    try {
        boolean unchanged

        handlers?.onFilter?.call(ev)
        switch (ev.storageType ?: hr_storageType.CAPABILITYATTRIBUTE) {
            case hr_storageType.CUSTOM:
                Closure handler = handlers?.onCustom
                def oldValue = handler?.call(ev)
                if (!handler || oldValue.is(hr_unhandled)) return
                unchanged = oldValue == ev.value
                break

            case hr_storageType.DATAVALUE:
                String valueAsString = (ev.value instanceof String) ? ev.value : "${ev.value}"
                ifInfo(ev) { unchanged = device.getDataValue(ev.name) == valueAsString }
                device.updateDataValue(ev.name, valueAsString)
                handlers?.onUpdate?.call(ev)
                break

            case hr_storageType.ATTRBACKEDPREF:
                assert ev.prefType
                device.updateSetting(ev.name, [type: ev.prefType, value: "${ev.value}"])
                // Fall through
            case hr_storageType.CAPABILITYATTRIBUTE:
                if (!device.hasAttribute(ev.name)) return
                ifInfo(ev) { unchanged = device.currentValue(ev.name) == ev.value }
                sendEvent(ev)
                break

            default:
                error "Unknown event storage type"
                return
        }

// #define L_FMT (#1.descriptionText ?: "${#1.name} set to ${#1.value}")
// #ifdef ENABLE_LOG_UNCHANGED
        info(ev) {
            def sb = new StringBuilder()
            sb << @L_FMT(it)
            if (unchanged) sb << " (unchanged)"
            return sb
        }
// #else
        if (unchanged) return
        info @L_FMT(ev)
// #endif
// #undef L_FMT
    } catch (AssertionError | Exception e) {
        error e
    }
}
