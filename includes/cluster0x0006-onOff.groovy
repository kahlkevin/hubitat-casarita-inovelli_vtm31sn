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
// #ifndef __CLUSTER06_COMMON
// #define __CLUSTER06_COMMON
/***************************************************************************************************************************
** SWITCH CAPABILITY IMPLEMENTATION (AND CLUSTER 0x06 EVENT CONSTANTS)
****************************************************************************************************************************/

@Field static final String EVT_STARTUPMODE = @NAMEOF(startupOnOff)

// #endif
// #ifdef API
//  #if 0
//// Dependencies:
//// @INCLIB(debugAndLogging)
//// @INCLIB(matterHelpers)
//  #endif
void off(Integer ep) {
    try {
        // Off Command (Matter Application Cluster Specification R1.4 § 1.5.7.1)
        String cmd = matterInvoke(ep, 0x0006, 0x00)
        sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

void on(Integer ep) {
    try {
        // On Command (Matter Application Cluster Specification R1.4 § 1.5.7.2)
        String cmd = matterInvoke(ep, 0x0006, 0x01)
        sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

@Field static final Map __c06_startupModes = ["previous setting": -1, off: 0, on: 1, toggle: 2].asImmutable()

static String c06_startupModeToString(Integer value) {
    if (value == null) value = -1
    __c06_startupModes.findResult { (it.value == value) ? "${it.key}" : null }
}

void c06_setOnStartup(Integer ep, String value) {
    if (__c06_startupModes.containsKey(value)) {
        c06_setOnStartup(ep, __c06_startupModes[value])
    } else {
        error "${dl_currentMethod()}: Unrecognized mode!"
    }
}

void c06_setOnStartup(Integer ep, Integer value) {
    // Write Attribute (Matter Application Cluster Specification R1.4 § 1.5.6)
    String cmd = matterWriteAttribute(ep, 0x0006, 0x4003, mdv.uint8x(value < 0 ? null : value))
    sendMatterCommand(cmd)
}

List<Map> c06_genPrefs() {
    [[name: EVT_STARTUPMODE, type: "enum", title: "Power Restoration Mode", description: "Choose what happens when power is restored", options: __c06_startupModes.keySet(), group: [name: "30user", pri: 10]]]
}

void c06_updated(DeviceWrapper prefDevice, Integer ep) {
    String curAttrValue = prefDevice.currentValue(EVT_STARTUPMODE)
    String newPrefValue = prefDevice.getSetting(EVT_STARTUPMODE)
// #ifdef ENABLE_DEBUG_PREFS
    debug "${dl_currentMethod()}(${prefDevice}, ${ep}): [${EVT_STARTUPMODE}]: curAttrValue = ${curAttrValue}, newPrefValue = ${newPrefValue}"
// #endif
    if (curAttrValue != newPrefValue) c06_setOnStartup(ep, newPrefValue)
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
void on() { @PARENT(on(@ENDPOINT)) }
void off() { @PARENT(off(@ENDPOINT)) }
// #ifdef CHILD_DRIVER
List<Map> c06_genPrefs() { parent?.c06_genPrefs() }
// #endif
void c06_updated() { @PARENT(c06_updated(device, @ENDPOINT)) }
void c06_definition() {
    capability "Switch"
    attribute EVT_STARTUPMODE, "string"
}

//  #undef PARENT
//  #undef IFPARENT
// #endif
