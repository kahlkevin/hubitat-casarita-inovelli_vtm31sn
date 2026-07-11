// #if 0
/**
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
// #endif
// #ifndef __CLUSTER08_COMMON
// #define __CLUSTER08_COMMON
/***************************************************************************************************************************
** SWITCHLEVEL CAPABILITY IMPLEMENTATION (AND CLUSTER 0x08 EVENT CONSTANTS)
****************************************************************************************************************************/

// #ifdef DEFMOVERATE_SUPPORT
@Field static final String EVT_MOVERATE = @NAMEOF(defaultMoveRate)
// #endif
@Field static final String EVT_OFFTIME = @NAMEOF(offTransitionTime)
@Field static final String EVT_ONTIME = @NAMEOF(onTransitionTime)
@Field static final String EVT_REMTIME = @NAMEOF(remainingTime)
@Field static final String NUM_UNSPEC = 'unspecified'

// #endif
// #ifdef API
//  #if 0
//// Dependencies:
//// @INCLIB(debugAndLogging)
//// @INCLIB(matterHelpers)
//// #define API
//// #include "cluster0x0006-onOff.groovy" // in API mode
//  #endif
void setLevel(Integer ep, BigDecimal levelPct, BigDecimal durationSeconds) {
    try {
        int level = userPctToUint8(levelPct)

        // Level == 0 triggers "off" (and the normally configured transition time)
        // We do this instead of calling the level cluster function to avoid observed behavior where level is left at 1%.
        // Tradeoff is loss of control over the transition duration (device will instead use configured off transition time).
        if (level == 0) {
            off(ep)
        } else {
            // MoveToLevelWithOnOff Command (Matter Application Cluster Specification R1.4 § 1.6.7.1 + 1.6.7.6)
            String cmd = matterInvoke(ep, 0x0008, 0x04, mdv.uint8(level), mdv.uint16x(userOptSecsToTenths(durationSeconds)), *mdv.NoOptionsOverride)
            sendMatterCommand(cmd)
        }
    } catch (AssertionError | Exception e) {
        error e
    }
}

void c08_setOffTransitionTime(Integer ep, durationSeconds) {
    // Write Attribute (Matter Application Cluster Specification R1.4 § 1.6.6.13)
    String cmd = matterWriteAttribute(ep, 0x0008, 0x0013, mdv.uint16x(userOptSecsToTenths(durationSeconds)))
    sendMatterCommand(cmd)
}

void c08_setOnTransitionTime(Integer ep, durationSeconds) {
    // Write Attribute (Matter Application Cluster Specification R1.4 § 1.6.6.12)
    String cmd = matterWriteAttribute(ep, 0x0008, 0x0012, mdv.uint16x(userOptSecsToTenths(durationSeconds)))
    sendMatterCommand(cmd)
}

// #ifdef DEFMOVERATE_SUPPORT
void c08_setDefaultMoveRate(Integer ep, moveRate) {
    // Write Attribute (Matter Application Cluster Specification R1.4 § 1.6.6.14)
    String cmd = matterWriteAttribute(ep, 0x0008, 0x0014, mdv.uint8x(moveRate == null ? null : clamp(userNumberToPosInt(new BigDecimal(moveRate)), 1, 254)))
    sendMatterCommand(cmd)
}
// #endif

List<Map> c08_genPrefs() {
    [
// #ifdef DEFMOVERATE_SUPPORT
        [name: EVT_MOVERATE, type: "number", title: "Default Move Rate", description: "Set level-change rate for use when Set Level duration is unspecified.<br/>Given in units-per-second (or leave blank for '${NUM_UNSPEC}').", group: [name: "30user50speed", pri: 40]],
// #endif
        [name: EVT_OFFTIME, type: "number", title: "Off Transition Time", description: "Set on-to-off transition time in seconds (or blank for '${NUM_UNSPEC}')<br/>(overrides <i>Default Dimming Speed</i>)", group: [name: "30user50speed", pri: 35]],
        [name: EVT_ONTIME, type: "number", title: "On Transition Time", description: "Set off-to-on transition time in seconds (or blank for '${NUM_UNSPEC}')<br/>(overrides <i>Default Dimming Speed</i>)", group: [name: "30user50speed", pri: 30]],
    ]
}

void c08_updated(DeviceWrapper prefDevice, Integer ep) {
    [
// #ifdef DEFMOVERATE_SUPPORT
        (EVT_MOVERATE): this.&c08_setDefaultMoveRate,
// #endif
        (EVT_OFFTIME): this.&c08_setOffTransitionTime,
        (EVT_ONTIME): this.&c08_setOnTransitionTime,
    ]
    .each { Map<String, Closure>.Entry it ->
        String curAttrValue = prefDevice.currentValue(it.key)
        String newPrefValue = prefDevice.getSetting(it.key)
        if (curAttrValue == NUM_UNSPEC) curAttrValue = null
// #ifdef ENABLE_DEBUG_PREFS
        debug "${dl_currentMethod(1)}(${prefDevice}, ${ep}): [${it.key}]: curAttrValue = ${curAttrValue}, newPrefValue = ${newPrefValue}"
// #endif
        if (curAttrValue != newPrefValue) it.value(ep, newPrefValue)
    }
}

// #endif
// #ifdef CAPABILITY
//  #if !@defined(ENDPOINT)
//      #error ENDPOINT must be defined at build time
//  #endif
//  #ifdef CHILD_DRIVER
//      #define IFPARENT parent?.#1
//      #define PARENT parent.#1
//  #else
//      #define IFPARENT #1
//      #define PARENT #1
//  #endif
void setLevel(BigDecimal level, BigDecimal durationSeconds = null) { @PARENT(setLevel(@ENDPOINT, level, durationSeconds)) }
// #ifdef CHILD_DRIVER
List<Map> c08_genPrefs() { parent?.c08_genPrefs() }
// #endif
void c08_updated() { @PARENT(c08_updated(device, @ENDPOINT)) }
void c08_definition() {
    capability "SwitchLevel"
// #ifdef DEFMOVERATE_SUPPORT
    attribute EVT_MOVERATE, "number"
// #endif
    attribute EVT_OFFTIME, "number"
    attribute EVT_ONTIME, "number"
    attribute EVT_REMTIME, "number"
}

//  #undef IFPARENT
//  #undef PARENT
// #endif
