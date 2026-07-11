// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** HUB EVENT MAKER LiBRARY (symbol prefix: hm)
****************************************************************************************************************************/
// #else
/**
 *  Hub Event Maker Library (symbol prefix: hm)
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
    name: "hubEventMake",
    description: "Produce Hubitat Event structures from incoming Matter messages",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// @AT(INCLIB)(hubEventRetire)
// @AT(INCLIB)(matterHelpers)
// @POUND(include) "cluster0x0003-identify.groovy"
// @POUND(include) "cluster0x0008-level.groovy"
// @POUND(include) "cluster0x003B-switch.groovy"
// @POUND(include) "cluster0x0050-modeSelect.groovy"
// @POUND(include) "notificationCommon.groovy"
// @POUND(define) API
// @POUND(include) "cluster0x0006-onOff.groovy" // in API mode
// #endif

@Field static final Closure div1e1 = { it / 10 }
@Field static final Closure div1e2 = { it / 100 }
@Field static final Closure div1e3 = { it / 1000 }
@Field static final Closure div1e6 = { it / 1000000 }
@Field static final Closure u8toPct = { it ? Math.max(Math.round(it / 2.54), 1) : 0 }  // Never round all the way down to zero

// #ifdef TEMPERATURE_SUPPORT
// Temperature transform
@Field static final Closure tempDisplay = { it.driver.convertTemperatureIfNeeded(div1e2(it.dm.decodedValue), "C", 1) }
// #endif

// Button event transforms
@Field static final Closure epToButton = { @BUTTON_EP_MAP[it.dm.endpointInt] }
@Field static final Closure multipressName = { [2: EVT_BTNDTAP, 1: EVT_BTNPUSH].get(it.dm.decodedValue.get(1), "multiTapped") }

// #define L_HINT_REFRESH   "refresh"
// #define L_HINT_THESE     "these"
// #define L_HINT_NONE      "none"
// #define L_HINT_ANY       "any"
@Field static final Map hintRefresh = [ mode: @L_HINT_REFRESH ].asImmutable()
@Field static final Map hintThese   = [ mode: @L_HINT_THESE   ].asImmutable()
@Field static final Map hintNone    = [ mode: @L_HINT_NONE    ].asImmutable()
@Field static final Map hintAny     = [ mode: @L_HINT_ANY     ].asImmutable()

@Field static Map __hm_data_field
@Field static final Closure __hm_data = { __hm_data_field ?: (__hm_data_field = [
    0x0003: [   // Identify Cluster (Matter Application Cluster Specification R1.4 § 1.2), endpoint 1 (ignore this cluster on endpoints 2-6)
        [       //  Matter Attributes
        0x0000: [name: EVT_IDTIME,              unit: "seconds"],
        0x0001: [name: EVT_IDTYPE,              conversion: { [0:"None", 1:"LightOutput", 2:"VisibleIndicator", 3:"AudibleBeep", 4:"Display", 5:"Actuator"].get(it) }],
        ],
        hintThese.plus([endpoints: [1]]),
    ],
    0x0006: [   // On/Off Cluster (Matter Application Cluster Specification R1.4 § 1.5), endpoints 1, 6
        [       //  Matter Attributes
        0x0000: [name: "switch",                conversion: { it ? "on" : "off" }],
        0x4003: [name: EVT_STARTUPMODE,         conversion: this.&c06_startupModeToString, nullValue: c06_startupModeToString(null), extra: [storageType: hr_storageType.ATTRBACKEDPREF, prefType: "enum"]],
        ],
        hintThese,
    ],
    0x0008: [   // Level Cluster (Matter Application Cluster Specification R1.4 § 1.6), endpoints 1, 6
        [       //  Matter Attributes
        0x0000: [name: "level",                 conversion: u8toPct,  unit: "%", extra: [colorControl: true]],
        0x0001: [name: EVT_REMTIME,             conversion: div1e1,   unit: "seconds"],
        0x000F: [name: EVT_C08OPTIONS,          extra: [storageType: hr_storageType.CUSTOM]],
        0x0012: [name: EVT_ONTIME,              conversion: div1e1,   unit: "seconds", nullValue: NUM_UNSPEC, extra: [storageType: hr_storageType.ATTRBACKEDPREF, prefType: "number"]],
        0x0013: [name: EVT_OFFTIME,             conversion: div1e1,   unit: "seconds", nullValue: NUM_UNSPEC, extra: [storageType: hr_storageType.ATTRBACKEDPREF, prefType: "number"]],
// #ifdef DEFMOVERATE_SUPPORT
        0x0014: [name: EVT_MOVERATE,            unit: "units per second", nullValue: NUM_UNSPEC, extra: [storageType: hr_storageType.ATTRBACKEDPREF, prefType: "number"]],
// #endif
        ],
        hintThese,
    ],
    0x0028: [   // Basic Information Cluster (Matter Core Specification R1.4 § 11.1)
        [       //  Matter Attributes
        0x0009: [name: EVT_SWVER_L,             extra: [storageType: hr_storageType.DATAVALUE]],
        0x000A: [name: EVT_SWVER_S,             extra: [storageType: hr_storageType.DATAVALUE]],
        ],
        hintRefresh.plus([endpoints: [0]]),
    ],
    0x003B: [   // Switch Cluster (Matter Application Cluster Specification R1.4 § 1.13), endpoints 3, 4, 5
// #ifneq @EVENT_SEQUENCING urgent
        [       //  Matter Events
        0x0001: [noop: true]    // In the absence if IsUrgent event sub support, a sub to this attribute triggers most events to be delivered immediately
                                // See Matter Core Specification R1.4 § 8.5, pp. 514 for explanation of event subs and IsUrgent.
        ],
        hintThese,
// #else
        [:],    //  Matter Attributes
        hintNone,
// #endif
        [       //  Matter Events
        0x02: [name: EVT_BTNHELD,               value: epToButton, extra: [isStateChange: true]],
        0x04: [name: EVT_BTNREL,                value: epToButton, extra: [isStateChange: true]],
        0x06: [name: multipressName,            value: epToButton, extra: [isStateChange: true]],
// #ifdef ENABLE_DEBUG_EVENTS
        0x01: [name: "initialPressed",          value: epToButton, extra: [isStateChange: true]],   // Informational only
        0x03: [name: "shortReleased",           value: epToButton, extra: [isStateChange: true]],   // Informational only
        0x05: [name: "multiPressOngoing",       value: { it.dm },  extra: [isStateChange: true]],   // Informational only
        ],
        hintAny,
// #else
        ],
        hintThese,
// #endif
    ],
    0x0050: [   // Mode Select Cluster (Matter Application Cluster Specification R1.4 § 1.9)
        [       //  Matter Attributes
        0x0003: [name: EVT_CURRENTMODE,         extra: [storageType: hr_storageType.CUSTOM], descriptionText: { "ModeSelect [ep: ${it.endpointInt}] Current Mode set to ${it.value}" }],
        ],
        hintThese,
    ],
    0x0090: [   // Electrical Power Measurement Cluster (Matter Application Cluster Specification R1.4 § 2.13), endpoint 7
        [       //  Matter Attributes
        0x0004: [name: "voltage",               conversion: div1e3, unit: "V"],
        0x0005: [name: "amperage",              conversion: div1e3, unit: "A"],
        0x0008: [name: "power",                 conversion: div1e3, unit: "W", descriptionText: { "Load power is ${it.value} ${it.unit}" }],
        ],
        hintThese,
    ],
    0x0091: [   // Electrical Energy Measurement Cluster (Matter Application Cluster Specification R1.4 § 2.12), endpoint 7
        [       //  Matter Attributes
        0x0001: [name: "energy",                conversion: { div1e6(it.get(0, 0)) }, unit: "kWh", descriptionText: { "Cumulative energy used: ${it.value} ${it.unit}" }],
        ],
        hintThese,
    ],
// #ifdef FAN_SUPPORT
    0x0202: [   // Fan Control Cluster (Matter Application Cluster Specification R1.4 § 4.4)
        [       //  Matter Attributes
        0x0000: [ name: "speed",                conversion: { ["off", "low", "medium", "high", "on", "auto", "auto" ].get(it) }],
        0x0003: [ name: "level",                unit: "%"],
        ],
        hintThese,
    ],
// #endif
    0x0300: [   // Color Control Cluster (Matter Application Cluster Specification R1.4 § 3.2), endpoint 6
        [       //  Matter Attributes
        0x0000: [name: "hue",                   conversion: u8toPct, unit: "%", extra: [colorControl: true]],
        0x0001: [name: "saturation",            conversion: u8toPct, unit: "%", extra: [colorControl: true]],
        0x0007: [name: "colorTemperature",      conversion: this.&miredsToKelvin, unit: "°K", extra: [colorControl: true]],
        0x0008: [name: "colorMode",             conversion: { ["RGB", "RGB", "CT"].get(it) }, extra: [colorControl: true]],
        ],
        hintThese,
    ],
// #ifdef TEMPERATURE_SUPPORT
    0x0402: [   // Temperature Measurement Cluster (Matter Application Cluster Specification R1.4 § 2.3)
        [       //  Matter Attributes
        0x0000: [name: "temperature",           value: tempDisplay, unit: { "°${it.driver.location.temperatureScale}" }],
        ],
        hintThese,
    ],
// #endif
// #ifdef HUMIDITY_SUPPORT
    0x0405: [   // Water Content Measurement Cluster (Matter Application Cluster Specification R1.4 § 2.6)
        [       //  Matter Attributes
        0x0000: [name: "humidity",              conversion: div1e2, nullValue: "n/a", unit: "%"],
        ],
        hintThese,
    ],
// #endif
].asImmutable()) }
// #ifdef JSON_SUB

// #ifdef FUTURE
//  (a) Remove JSON_SUB once satisfied matter.subscribe() and friends can do everything we need (including IsUrgent = true event subs)
//  (b) See https://community.hubitat.com/t/matter-event-subscriptions-expose-eventpathib-isurgent-for-low-latency-generic-switch-button-events/164389
// #endif
String hm_getSubscriptionEntitiesJson() {
    List seStringConv = hm_getSubscriptionEntities()
        ?.collect {
            it.collectEntries { entry -> [(entry.key): (entry.value instanceof String) ? entry.value : String.format('0x%1$X', entry.value)] }
        }

    seStringConv ? new JsonBuilder(seStringConv)?.toString() : null
}
// #endif

List hm_getSubscriptionPaths() {
    hm_getSubscriptionEntities()
        ?.collect {
            String ep = HexUtils.integerToHexString(it.ep & 0xFFFF, 2)
            if (it.evt) matter.eventPath(ep, it.cluster, it.evt)
            else matter.attributePath(ep, it.cluster, it.attr)
        }
}

List hm_getSubscriptionEntities() {
    Closure processHint = { Integer clusterId, List entryList, int hintIndex, String entityType ->
        Map subscriptionHint = entryList[hintIndex]
        Map base = [ep: -1 as Short, cluster: clusterId]
        List<Short> endpointList = subscriptionHint?.endpoints

        switch (subscriptionHint?.mode) {
            case @L_HINT_THESE:
                Set entitySet = entryList[hintIndex - 1].keySet()
                endpointList
                    ? endpointList.collectMany { endpoint -> entitySet.collect { Integer entityId -> base.plus([ep: endpoint as Short, (entityType): entityId]) } }
                    : entitySet.collect { Integer entityId -> base.plus([(entityType): entityId]) }
                break

            case @L_HINT_ANY:
                base << [(entityType): -1]
                endpointList?.collect({ endpoint -> base.plus([ep: endpoint as Short]) }) ?: [base]
                break

            default:
                []
                break
        }
    }

    __hm_data().collectMany { Map.Entry cluster ->
        Integer clusterId = cluster.key
        List entryList = cluster.value
        processHint(clusterId, entryList, 1, 'attr') + processHint(clusterId, entryList, 3, 'evt')
    }
// #ifdef SORT_SUBSCRIPTION_PATHS
    ?.sort { Map a, Map b ->
        boolean aIsEvt = a.containsKey("evt")
        boolean bIsEvt = b.containsKey("evt")
        int compareResult
        if (aIsEvt == bIsEvt) {
            compareResult = a.cluster <=> b.cluster
            if (compareResult != 0) return compareResult
            compareResult = a.ep <=> b.ep
            if (compareResult != 0) return compareResult
            compareResult = (aIsEvt) ? (a.evt <=> b.evt) : (a.attr <=> b.attr)
        } else if (aIsEvt) {
            compareResult = 1
        } else {
            compareResult = -1
        }
        return compareResult
    }
// #endif
}

List hm_getRefreshPaths() {
    __hm_data().collectMany { Map.Entry cluster ->
        Integer clusterId = cluster.key
        List entryList = cluster.value
        Map subscriptionHint = entryList[1]
        if (subscriptionHint?.mode == @L_HINT_REFRESH) {
            Set attrSet = entryList[0]?.keySet()
            List<Short> endpointList = subscriptionHint?.endpoints
            if (attrSet && endpointList) {
                return endpointList.collectMany { Integer endpoint ->
                    String ep = HexUtils.integerToHexString(endpoint & 0xFFFF, 2)
                    return attrSet.collect { Integer attrId -> matter.attributePath(ep, clusterId, attrId) }
                }
            }
        }
        return []
    }
}

static def __hm_valCall(value, arg, staticValue) { (value instanceof Closure) ? value.call(arg) : staticValue }
static String __hm_defaultDescription(event) { "${StringUtils.splitByCharacterTypeCamelCase(StringUtils.capitalize(event.name)).join(" ")} set to ${event.value}${event.unit ? (" " + event.unit) : ""}" }

Map hm_hubEventFrom(Map matterMsg) {
    try {
        def rec = __hm_data().get(matterMsg.clusterInt)
        if (rec == null) return null

        Map event = [clusterInt: matterMsg.clusterInt, endpointInt: matterMsg.endpointInt]
        Map closureArgs = [dm: matterMsg, driver: this]

        if (matterMsg.attrInt instanceof Integer) {
            rec = rec.get(0)?.get(matterMsg.attrInt)
            event << [attrInt: matterMsg.attrInt]
        } else if (matterMsg.evtInt instanceof Integer) {
            rec = rec.get(2)?.get(matterMsg.evtInt)
            event << [evtInt: matterMsg.evtInt, type: "physical", eventSerialInt: matterMsg.eventSerialInt]
        }

        if (rec == null || rec.noop) return null

        String name = __hm_valCall(rec.name, closureArgs, rec.name)

        def value = matterMsg.decodedValue
        if (value != null) value = __hm_valCall(rec.value, closureArgs, value)
        if (value != null) value = __hm_valCall(rec.conversion, value, value)
        if (value == null) value = rec.get(@NAMEOF(nullValue), @NAMEOF(null))

        event << [name: name, value: value]

        if (rec.unit) { event << [unit: __hm_valCall(rec.unit, closureArgs, rec.unit)] }

        event << [descriptionText: __hm_valCall(rec.descriptionText, event, null) ?: __hm_defaultDescription(event)]

        if (rec.extra instanceof Map) event << rec.extra

        return event
    } catch (AssertionError | Exception e) {
        error e
    }
}
