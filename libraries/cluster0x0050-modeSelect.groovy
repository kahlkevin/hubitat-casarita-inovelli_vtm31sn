// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** MODE SELECT LIBRARY (symbol prefix: ms)
****************************************************************************************************************************/
// #else
/**
 *  Mode Select Library (symbol prefix: ms)
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
    name: "cluster0x0050-modeSelect",
    description: "Enable modeling modeSelect options as Preferences and Capability Attributes",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// @AT(INCLIB)(matterHelpers)
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// MS PUBLIC API
// --

@Field static final String EVT_CURRENTMODE = "currentMode"
@Field static final String EVT_SWVER_S = @NAMEOF(softwareVersion)
@Field static final String EVT_SWVER_L = @NAMEOF(softwareVersionId)

@Field final Map<String, Closure> ms_api = [
    genPrefs: this.&__ms_genPrefs,
    updated: this.&__ms_updated,
    prefClientInstalled: this.&__ms_prefClientInstalled,
    attrClientInstalled: this.&__ms_attrClientInstalled,
    getModes: this.&__ms_getModes,
    listeners: [
        (EVT_SWVER_S): this.&__ms_softwareVersion_updated,
        (EVT_SWVER_L): this.&__ms_softwareVersionId_updated,
        (EVT_CURRENTMODE): this.&__ms_currentMode_updated,
    ].asImmutable(),
// #if @defined(ENABLE_DEBUG_MODESELECT) || @defined(ENABLE_DISTCLEAN_SUPPORT)
    debug: [
        deactivateSpec: { __ms_activateSpec(null, null) },
//      #ifdef ENABLE_DEBUG_MODESELECT
        getDeviceState: { __ms_getDeviceState() },
        getVerId: { __ms_getVerId(null, null)?.findAll({ !(it.value instanceof Closure) })?.asImmutable() },
//      #endif
    ].asImmutable(),
// #endif
].asImmutable()
// #ifdef ENABLE_TESTS_FOR_MODESELECT

void ms_runAllTests() {
    infoAlways "${__ms_testGetVerId() ? "pass" : "fail"}!"
    infoAlways "${__ms_testPickLatestSpec() ? "pass" : "fail"}!"
    infoAlways "${__ms_testSpecCache() ? "pass" : "fail"}!"
}
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// MS PRIVATE API
// --

@Field static final String __ms_dv_ac_endPoints = "attrClientEndpoints"
@Field static final String __ms_dv_pc_endPoints = "prefClientEndpoints"

// Unified storage for all device modeSelect state
// Key is deviceNetworkId string, Value is deviceState
@Field static ConcurrentHashMap<String, Map> __ms_global_deviceState = new ConcurrentHashMap(8, 0.75, 1)

// #ifdef FUTURE
//   (a) Once we have more experience with how fw version updates evolve, consider moving
//       this data set (and the version/switching/update logic) out to a separate module
//   (b) Consider housing additional invariant value represented in cluster attributes
//       (such as cluster 8's min level, max level)
//   (c) Implement chaining and patching so maps don't have to be (mostly) duplicated
//       when version updates (see parent field below)
// #endif
@Field static final Map __ms_optionSpecs = [
    "1.1.5": [
        softwareVersionId: 1150,
// #ifdef FUTURE
        parent: null,
// #endif
        options: [
            "switchType": [
                ep: 20,
                partOf: 1,
                label: "Switch Type",
                // description: "",
                group: [name: "20load", pri: 20],
                defaultMode: 0,
                modes: [
                    [ label: "Standalone (Single-Pole)",                mode: 0,    semanticTag: 0x00000000],
                    [ label: "Multi-Way with Traditional Dumb Switch",  mode: 1,    semanticTag: 0x00000000],
                    [ label: "Multi-Way with Smart Aux Switch",         mode: 2,    semanticTag: 0x00000000],
                ],
            ],
            "smartBulbMode": [
                ep: 21,
                partOf: 1,
                label: "Smart Bulb Mode",
                description: "The lighting load outputs remain powered at all times. Control over the smart lighting is via hub integrations, Rule Machine rules, etc.",
                group: [name: "20load", pri: 30],
                defaultMode: 0,
                modes: [
                    [ label: "Disable",                                 mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "Smart Bulb Enable",                       mode: 1,    semanticTag: 0x00000000 ],
                ],
            ],
            "controlOfSwitchLoad": [
                ep: 22,
                partOf: 1,
                label: "Disable paddle switch (local protection mode)",
                group: [name: "30user", pri: 20],
                description: "Decide whether paddle switch controls the lighting load or is disabled",
                defaultMode: 0,
                modes: [
                    [ label: "Remote & paddle control",                 mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "Remote control only",                     mode: 1,    semanticTag: 0x00000000 ],
                ],
            ],
            "dimmingSpeed": [
                ep: 23,
                partOf: 1,
                label: "Default Dimming Speed",
                description: "The time taken to transition on &lt;=&gt; off when operating as a dimmer (see <i>Switch Mode</i>)</br>(when not overridden by <i>On/Off Transition Time</i> settings)",
                group: [name: "30user50speed", pri: 20],
                defaultMode: 25,
                modes: [
                    [ label: "Instant",                                 mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "500ms",                                   mode: 5,    semanticTag: 0x00000000 ],
                    [ label: "800ms",                                   mode: 8,    semanticTag: 0x00000000 ],
                    [ label: "1s",                                      mode: 10,   semanticTag: 0x00000000 ],
                    [ label: "1.5s",                                    mode: 15,   semanticTag: 0x00000000 ],
                    [ label: "2s",                                      mode: 20,   semanticTag: 0x00000000 ],
                    [ label: "2.5s",                                    mode: 25,   semanticTag: 0x00000000 ],
                    [ label: "3s",                                      mode: 30,   semanticTag: 0x00000000 ],
                    [ label: "3.5s",                                    mode: 35,   semanticTag: 0x00000000 ],
                    [ label: "4s",                                      mode: 40,   semanticTag: 0x00000000 ],
                    [ label: "5s",                                      mode: 50,   semanticTag: 0x00000000 ],
                    [ label: "6s",                                      mode: 60,   semanticTag: 0x00000000 ],
                    [ label: "7s",                                      mode: 70,   semanticTag: 0x00000000 ],
                    [ label: "8s",                                      mode: 80,   semanticTag: 0x00000000 ],
                    [ label: "10s",                                     mode: 100,  semanticTag: 0x00000000 ],
                ],
            ],
            "buttonDelay": [
                ep: 24,
                partOf: 1,
                label: "Button Delay",
                description: "Longer delay makes it easier to trigger multi-tap sequences. Disabling may prevent them.",
                group: [name: "30user50speed", pri: 10],
                defaultMode: 5,
                modes: [
                    [ label: "No Delay",                                mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "300ms",                                   mode: 3,    semanticTag: 0x00000000 ],
                    [ label: "400ms",                                   mode: 4,    semanticTag: 0x00000000 ],
                    [ label: "500ms",                                   mode: 5,    semanticTag: 0x00000000 ],
                    [ label: "600ms",                                   mode: 6,    semanticTag: 0x00000000 ],
                    [ label: "700ms",                                   mode: 7,    semanticTag: 0x00000000 ],
                    [ label: "800ms",                                   mode: 8,    semanticTag: 0x00000000 ],
                    [ label: "900ms",                                   mode: 9,    semanticTag: 0x00000000 ],
                ],
            ],
            "ledColor": [
                ep: 25,
                partOf: 1,
                label: "LED Color",
                description: "Choose the color used to indicate on/off/dimmer level",
                group: [name: "30user20led", pri: 10],
                defaultMode: 170,
                modes: [
                    [ label: "Red",                                     mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "Orange",                                  mode: 14,   semanticTag: 0x00000000 ],
                    [ label: "Lemon",                                   mode: 35,   semanticTag: 0x00000000 ],
                    [ label: "Lime",                                    mode: 64,   semanticTag: 0x00000000 ],
                    [ label: "Green",                                   mode: 85,   semanticTag: 0x00000000 ],
                    [ label: "Teal",                                    mode: 106,  semanticTag: 0x00000000 ],
                    [ label: "Cyan",                                    mode: 127,  semanticTag: 0x00000000 ],
                    [ label: "Aqua",                                    mode: 149,  semanticTag: 0x00000000 ],
                    [ label: "Blue",                                    mode: 170,  semanticTag: 0x00000000 ],
                    [ label: "Violet",                                  mode: 191,  semanticTag: 0x00000000 ],
                    [ label: "Magenta",                                 mode: 212,  semanticTag: 0x00000000 ],
                    [ label: "Pink",                                    mode: 234,  semanticTag: 0x00000000 ],
                    [ label: "White",                                   mode: 255,  semanticTag: 0x00000000 ],
                ],
            ],
            "ledEffect": [
                ep: 26,
                partOf: 6,
                isAttribute: true,
                label: "LED Effect",
                // description: "",
                defaultMode: 1,
                modes: [
                    [ label: "Off",                                     mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "Solid",                                   mode: 1,    semanticTag: 0x00000000 ],
                    [ label: "Fast Blink",                              mode: 2,    semanticTag: 0x00000000 ],
                    [ label: "Slow Blink",                              mode: 3,    semanticTag: 0x00000000 ],
                    [ label: "Middle Chase",                            mode: 5,    semanticTag: 0x00000000 ],
                    [ label: "Open Close",                              mode: 6,    semanticTag: 0x00000000 ],
                    [ label: "Small To Big",                            mode: 7,    semanticTag: 0x00000000 ],
                    [ label: "Slow Falling",                            mode: 9,    semanticTag: 0x00000000 ],
                    [ label: "Middle Falling",                          mode: 10,   semanticTag: 0x00000000 ],
                    [ label: "Fast Falling",                            mode: 11,   semanticTag: 0x00000000 ],
                    [ label: "Slow Rising",                             mode: 12,   semanticTag: 0x00000000 ],
                    [ label: "Middle Rising",                           mode: 13,   semanticTag: 0x00000000 ],
                    [ label: "Fast Rising",                             mode: 14,   semanticTag: 0x00000000 ],
                    [ label: "Middle Blink",                            mode: 15,   semanticTag: 0x00000000 ],
                    [ label: "Slow Chase",                              mode: 16,   semanticTag: 0x00000000 ],
                    [ label: "Fast Chase",                              mode: 17,   semanticTag: 0x00000000 ],
                    [ label: "Slow Siren",                              mode: 18,   semanticTag: 0x00000000 ],
                    [ label: "Fast Siren",                              mode: 19,   semanticTag: 0x00000000 ],
                ],
            ],
            "switchMode": [
                ep: 27,
                partOf: 1,
                label: "Switch Mode",
                description: "Click to set. (<b>NOTE</b>: <i>Won't reflect config changes made manually via quick taps.</i>)",
                group: [name: "20load", pri: 10],
                defaultMode: 2,
                modes: [
                    [ label: "On/Off",                                  mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "Leading Dimmer",                          mode: 1,    semanticTag: 0x00000000 ],
                    [ label: "Trailing Dimmer",                         mode: 2,    semanticTag: 0x00000000 ],
                ],
            ],
            "disableAudibleClick": [
                ep: 28,
                partOf: 1,
                label: "Use physical relay (audible click)",
                description: "Switching via the physical relay will remove all power from the lighting load when the switch is off. (<b>NOTE</b>: <i>Cannot be enabled when using Smart Bulb Mode or disabled when Switch Type is Multi-Way with Traditional Dumb Switch.</i>)",
                group: [name: "20load", pri: 40],
                defaultMode: 0,
                modes: [
                    [ label: "Enable",                                  mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "Disable",                                 mode: 1,    semanticTag: 0x00000000 ],
                ],
            ],
            "ledOnIntensity": [
                ep: 29,
                partOf: 1,
                label: "LED Intensity when switch is on",
                // description: "",
                group: [name: "30user20led", pri: 20],
                defaultMode: 100,
                modes: [
                    [ label: "off",                                      mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "1%",                                       mode: 1,    semanticTag: 0x00000000 ],
                    [ label: "3%",                                       mode: 3,    semanticTag: 0x00000000 ],
                    [ label: "5%",                                       mode: 5,    semanticTag: 0x00000000 ],
                    [ label: "8%",                                       mode: 8,    semanticTag: 0x00000000 ],
                    [ label: "10%",                                      mode: 10,   semanticTag: 0x00000000 ],
                    [ label: "13%",                                      mode: 13,   semanticTag: 0x00000000 ],
                    [ label: "16%",                                      mode: 16,   semanticTag: 0x00000000 ],
                    [ label: "20%",                                      mode: 20,   semanticTag: 0x00000000 ],
                    [ label: "23%",                                      mode: 23,   semanticTag: 0x00000000 ],
                    [ label: "26%",                                      mode: 26,   semanticTag: 0x00000000 ],
                    [ label: "30%",                                      mode: 30,   semanticTag: 0x00000000 ],
                    [ label: "33%",                                      mode: 33,   semanticTag: 0x00000000 ],
                    [ label: "36%",                                      mode: 36,   semanticTag: 0x00000000 ],
                    [ label: "40%",                                      mode: 40,   semanticTag: 0x00000000 ],
                    [ label: "45%",                                      mode: 45,   semanticTag: 0x00000000 ],
                    [ label: "50%",                                      mode: 50,   semanticTag: 0x00000000 ],
                    [ label: "60%",                                      mode: 60,   semanticTag: 0x00000000 ],
                    [ label: "70%",                                      mode: 70,   semanticTag: 0x00000000 ],
                    [ label: "80%",                                      mode: 80,   semanticTag: 0x00000000 ],
                    [ label: "90%",                                      mode: 90,   semanticTag: 0x00000000 ],
                    [ label: "100%",                                     mode: 100,  semanticTag: 0x00000000 ],
                ],
            ],
            "auxTest": [
                ep: 30,
                partOf: 1,
                label: "Aux Switch Sensitivity Adjustment",
                description: "Click to set. (<i>This is an advanced setting. Consult Inovelli forum/support materials.</i>)",
                defaultMode: 125,
                modes: [
                    [ label: "105",                                     mode: 105,  semanticTag: 0x00000000 ],
                    [ label: "115",                                     mode: 115,  semanticTag: 0x00000000 ],
                    [ label: "125",                                     mode: 125,  semanticTag: 0x00000000 ],
                    [ label: "135",                                     mode: 135,  semanticTag: 0x00000000 ],
                    [ label: "145",                                     mode: 145,  semanticTag: 0x00000000 ],
                ],
            ],
            "ledOffIntensity": [
                ep: 31,
                partOf: 1,
                label: "LED Intensity when switch is off",
                // description: "",
                group: [name: "30user20led", pri: 30],
                defaultMode: 33,
                modes: [
                    [ label: "off",                                      mode: 0,    semanticTag: 0x00000000 ],
                    [ label: "1%",                                       mode: 1,    semanticTag: 0x00000000 ],
                    [ label: "3%",                                       mode: 3,    semanticTag: 0x00000000 ],
                    [ label: "5%",                                       mode: 5,    semanticTag: 0x00000000 ],
                    [ label: "8%",                                       mode: 8,    semanticTag: 0x00000000 ],
                    [ label: "10%",                                      mode: 10,   semanticTag: 0x00000000 ],
                    [ label: "13%",                                      mode: 13,   semanticTag: 0x00000000 ],
                    [ label: "16%",                                      mode: 16,   semanticTag: 0x00000000 ],
                    [ label: "20%",                                      mode: 20,   semanticTag: 0x00000000 ],
                    [ label: "23%",                                      mode: 23,   semanticTag: 0x00000000 ],
                    [ label: "26%",                                      mode: 26,   semanticTag: 0x00000000 ],
                    [ label: "30%",                                      mode: 30,   semanticTag: 0x00000000 ],
                    [ label: "33%",                                      mode: 33,   semanticTag: 0x00000000 ],
                    [ label: "36%",                                      mode: 36,   semanticTag: 0x00000000 ],
                    [ label: "40%",                                      mode: 40,   semanticTag: 0x00000000 ],
                    [ label: "45%",                                      mode: 45,   semanticTag: 0x00000000 ],
                    [ label: "50%",                                      mode: 50,   semanticTag: 0x00000000 ],
                    [ label: "60%",                                      mode: 60,   semanticTag: 0x00000000 ],
                    [ label: "70%",                                      mode: 70,   semanticTag: 0x00000000 ],
                    [ label: "80%",                                      mode: 80,   semanticTag: 0x00000000 ],
                    [ label: "90%",                                      mode: 90,   semanticTag: 0x00000000 ],
                    [ label: "100%",                                     mode: 100,  semanticTag: 0x00000000 ],
                ],
            ],
        ],
    ],
].asImmutable()

Map __ms_make_modeValueCache() { new ConcurrentHashMap<Integer, Object>(16, 0.75, 1) }
Map __ms_make_deviceState(String activeSpecKey = null) { [activeSpecKey: activeSpecKey, modeValueCache: (activeSpecKey != null ? __ms_make_modeValueCache() : null)].asImmutable() }

Map __ms_getDeviceState(String idHint = null) {
    String id = idHint ?: (parent ?: device).getDeviceNetworkId()
    __ms_global_deviceState.computeIfAbsent(id, { __ms_make_deviceState() })
}

boolean __ms_setDeviceState(Map newState, Map oldState = null) {
    assert newState != null
    String id = (parent ?: device).getDeviceNetworkId()
    oldState = oldState ?: __ms_getDeviceState(id)
    __ms_global_deviceState.replace(id, oldState, newState)
}

String __ms_getActiveSpec() { __ms_getDeviceState()?.activeSpecKey }
Map<Integer, Object> __ms_getModeValueCache() { __ms_getDeviceState().modeValueCache }
void __ms_upgradePrefs() { warnAlways "${dl_currentMethod()} is NYI" }

Map __ms_getVerId(String sHint, Object iHint) {
    String sVal = sHint ?: (parent ?: device)?.getDataValue(EVT_SWVER_S)
    Long iVal = mh_safeParseLong(iHint) ?: mh_safeParseLong((parent ?: device)?.getDataValue(EVT_SWVER_L))
    return (sVal == null || iVal == null) ? null : [s: sVal, i: iVal, getString: { sVal }, getLong: { iVal }].asImmutable()
}

String __ms_pickLatestSpec(Map swVerId, Map specDb = __ms_optionSpecs) {
    assert swVerId != null
    assert specDb != null
    return specDb
        .findAll { it.value.softwareVersionId <= swVerId.i }
        ?.max { it.value.softwareVersionId }
        ?.key
}

boolean __ms_activateSpec(String specKey, Map oldState) {
// #if @defined(ENABLE_DEBUG_MODESELECT) || @defined(ENABLE_DEBUG_LIFECYCLE)
    debug "activateModeSelectSpec(${specKey}, ...)"
// #endif
    if ((specKey == null || __ms_optionSpecs.containsKey(specKey)) && __ms_setDeviceState(__ms_make_deviceState(specKey), oldState)) {
        List<Object> devices = getChildDevices().plus(device).findAll { it.getDataValue(__ms_dv_ac_endPoints) != null }
        devices?.each { try { it.modeSpecActivated() } catch (ignored) {} }
        return true
    }
    return false
}

void __ms_ensureSpec(String sHint = null, Object iHint = null) {
    def swVerId = __ms_getVerId(sHint, iHint)
    if (swVerId == null) return

    def originalState = __ms_getDeviceState()
    def originalSpecKey = originalState.activeSpecKey
    def desiredSpecKey = swVerId.getString()
    if (originalSpecKey == desiredSpecKey) return

    if (originalSpecKey == null) desiredSpecKey = __ms_pickLatestSpec(swVerId)
    if (__ms_activateSpec(desiredSpecKey, originalState)) {
        if (originalSpecKey != null) __ms_upgradePrefs()
        String cmd = matterReadAttribute(-1, 0x0050, 0x0003)
        (parent ?: this).sendMatterCommand(cmd)
    }
}

void __ms_softwareVersion_updated(Map hubEvent) { __ms_ensureSpec(hubEvent.value) }
void __ms_softwareVersionId_updated(Map hubEvent) { __ms_ensureSpec(null, hubEvent.value) }

void __ms_currentMode_updated(Map hubEvent) {
    __ms_ensureSpec()

    // Nothing to do unless there's an active optionSpec set
    def deviceState = __ms_getDeviceState()
    if (deviceState.activeSpecKey == null) return

    assert deviceState.modeValueCache != null

    Integer ep = hubEvent.endpointInt
    Integer newMode = mh_safeParseInteger(hubEvent.value)

    // Find optionSpec for the modeSelect endpoint whose mode value has changed
    Map optionSpec = __ms_optionSpecs[deviceState.activeSpecKey].options.findResult { (it.value.ep == ep) ? ([setting: it.key] << it.value) : null }
    if (optionSpec == null) return

    List<Object> devices = getChildDevices().plus(device).findAll { mh_intListLoad(it, __ms_dv_pc_endPoints)?.contains(optionSpec.partOf) || mh_intListLoad(it, __ms_dv_ac_endPoints)?.contains(optionSpec.partOf) }

    if (devices == null) {
        deviceState.modeValueCache.put(ep, newMode)
    } else {
        Closure update = (optionSpec.isAttribute) ? {
            // Invoke device driver's modeChanged method
            try { it.modeChanged(optionSpec.setting, newMode) } catch (ignored) {}
        } : {
            // Directly update the device's modeSelect option-associated setting
            // Since these settings are configured as enum inputs (see genPrefs),
            // we must use this specific argument form with updateSetting().
            it.updateSetting(optionSpec.setting, [type: "enum", value: "${newMode}"])
        }

        // Atomically update both the value cache and the attribute or setting
        deviceState.modeValueCache.compute(ep, { key, oldVal -> devices.each(update); newMode })
    }
}

List<Map> __ms_getModes(String setting) {
    def activeSpecKey = __ms_getActiveSpec()
    if (activeSpecKey == null) return null

    __ms_optionSpecs.getAt(activeSpecKey).options.getAt(setting)?.modes.asImmutable()
}

List<Map> __ms_genPrefs(Object prefDriver) {
    // Sometimes called this way during initial bundle import
    if (prefDriver?.device == null || (parent == null && device == null)) return

    // For which endpoints does the device support and maintain preferences?
    List<Integer> epsOfInterest = mh_intListLoad(prefDriver.device, __ms_dv_pc_endPoints)
    if (epsOfInterest?.isEmpty() != false) return

    __ms_ensureSpec()

    // Nothing to do unless there's an active optionSpec set
    def activeSpecKey = __ms_getActiveSpec()
    if (activeSpecKey == null) return

    assert prefDriver.metaClass.respondsTo(prefDriver, @NAMEOF(input), Map)

    // For each non-attribute option associated with an endpoint of interest, build a preference input UI item
    List<Map> prefs = __ms_optionSpecs[activeSpecKey].options
        ?.findAll { it.value.partOf in epsOfInterest && !it.value.isAttribute }
        ?.collect {
            def d = (it.value.description) ? [description: it.value.description] : [:]
            def g = (it.value.group) ? [group: it.value.group] : [:]
            [name: it.key, type: "enum", options: it.value.modes.collectEntries { [(it.mode): it.label] }, title: it.value.label] << d << g
        }
    if (prefs?.any { it.group?.name?.startsWith("20load") }) {
        prefs << [type: "paragraph", title: 'Electrical Configuration', description: "These settings depend on how your switch is physically wired and affect how the mains load output is controlled. Be sure to carefully review the installation and wiring instructions.", group: [name: "20load", pri: 0]]
    }
    return prefs
}

void __ms_updatedWorker(Map deviceState, String settingKey, Map optionSpec, int newMode) {
    assert deviceState?.modeValueCache != null

    // Update the option mode (using Matter) if we have a new value
    Integer cachedMode = mh_safeParseInteger(deviceState.modeValueCache[optionSpec.ep])
    if (cachedMode != newMode) {
// #ifdef ENABLE_DEBUG_MODESELECT
        debug "${dl_currentMethod()}: option = ${settingKey}, cachedMode = ${cachedMode}, newMode = ${newMode}"
        debug "${dl_currentMethod()}: Setting option ${settingKey} to value ${optionSpec.modes.findResult { newMode == it.mode ? it.label : null }}"
// #endif
        // ChangeToMode Command (Matter Application Cluster Specification R1.4 § 1.9.7.1)
        String cmd = matterInvoke(optionSpec.ep, 0x0050, 0x00, mdv.uint8(newMode))
        sendMatterCommand(cmd)
    }
}

void __ms_updatedWorker(Map deviceState, String settingKey, int newMode) {
    assert deviceState?.activeSpecKey != null

    def optionSpec = __ms_optionSpecs.getAt(deviceState.activeSpecKey).options.getAt(settingKey)
    if (optionSpec == null) return

    __ms_updatedWorker(deviceState, settingKey, optionSpec, newMode)
}

void __ms_updated(String settingKey, int newMode) {
    assert settingKey != null

    // Nothing to do unless there's an active optionSpec set
    def deviceState = __ms_getDeviceState()
    if (deviceState.activeSpecKey == null) return

    // Update the specified option mode if changed
    __ms_updatedWorker(deviceState, settingKey, newMode)
}

void __ms_updated(DeviceWrapper prefDevice) {
    assert prefDevice

    // Nothing to do unless there's an active optionSpec set
    def deviceState = __ms_getDeviceState()
    if (deviceState.activeSpecKey == null) return

    // For which endpoints does the device support and maintain preferences?
    List<Integer> epsOfInterest = mh_intListLoad(prefDevice, __ms_dv_pc_endPoints)
    if (epsOfInterest?.isEmpty() != false) return

    // For each option mode backed by a preference of interest, update if changed
    __ms_optionSpecs[deviceState.activeSpecKey].options
        ?.findAll { it.value.partOf in epsOfInterest && !it.value.isAttribute }
        ?.each {
            Integer prefMode = mh_safeParseInteger(prefDevice.getSetting(it.key))
            if (prefMode == null) return

            __ms_updatedWorker(deviceState, it.key, it.value, prefMode)
        }
}

void __ms_prefClientInstalled(DeviceWrapper prefDevice, int endPoint) { mh_intListSave(prefDevice, __ms_dv_pc_endPoints, [endPoint]) }
void __ms_prefClientInstalled(DeviceWrapper prefDevice, List<Integer> endPoints) { mh_intListSave(prefDevice, __ms_dv_pc_endPoints, endPoints) }

void __ms_attrClientInstalled(DeviceWrapper attrDevice, int endPoint) { mh_intListSave(attrDevice, __ms_dv_ac_endPoints, [endPoint]) }
void __ms_attrClientInstalled(DeviceWrapper attrDevice, List<Integer> endPoints) { mh_intListSave(attrDevice, __ms_dv_ac_endPoints, endPoints) }
// #ifdef ENABLE_TESTS_FOR_MODESELECT

// -------------------------------------------------------------------------------------------------------------------------
// MS TESTS
// --

boolean __ms_testGetVerId() {
    infoAlways dl_currentMethod()
    try {
        String testS = "Beep-boop"
        Long testI = 1945
        String curS = (parent ?: device).getDataValue(EVT_SWVER_S)
        Long curI = mh_safeParseLong((parent ?: device).getDataValue(EVT_SWVER_L))
        def v

        assert null != curS
        assert null != curI

        v = __ms_getVerId(null, null)
        assert v.getString() == curS
        assert v.getLong() == curI

        // Test immutability
        try { v.s = "fubar"; assert false } catch (UnsupportedOperationException) { /* expected */ }

        v = __ms_getVerId(testS, null)
        assert v.getString() == testS
        assert v.getLong() == curI

        v = __ms_getVerId(null, "${testI}")
        assert v.getString() == curS
        assert v.getLong() == testI

        v = __ms_getVerId(testS, "fubar")
        assert v.getString() == testS
        assert v.getLong() == curI

        return true
    } catch (AssertionError | Exception e) {
        error e
    }
    return false
}

boolean __ms_testPickLatestSpec() {
    infoAlways dl_currentMethod()
    try {
        def testrec1 = ["1.1.5": [softwareVersionId: 1150]]
        def testrec2 = ["1.0.25": [softwareVersionId: 1025]]
        def testrec3 = ["1.2.0": [softwareVersionId: 1200]]
        def testdb = testrec1 << testrec2 << testrec3
        def swVerIdOld = [s: "older", i: 1101]
        def swVerIdCur = [s: "1.1.5", i: 1150]
        def swVerIdNew = [s: "newer", i: 1300]
        assert "1.1.5" == __ms_pickLatestSpec(swVerIdCur, testdb)
        assert "1.0.25" == __ms_pickLatestSpec(swVerIdOld, testdb)
        assert "1.2.0" == __ms_pickLatestSpec(swVerIdNew, testdb)
        return true
    } catch (AssertionError | Exception e) {
        error e
    }
    return false
}

boolean __ms_testSpecCache() {
    infoAlways dl_currentMethod()
    def savedDeviceState = __ms_getDeviceState()
    def curDeviceState = null
    def __hasModeValueCache = { def c = __ms_getModeValueCache(); c && !c.isEmpty() }

    try {
        assert true == __ms_activateSpec(null, savedDeviceState)
        curDeviceState = __ms_getDeviceState()
        assert false == (__ms_getActiveSpec() != null)
        assert null == __ms_getModeValueCache()
        assert null == __ms_getModeValueCache() // Twice in a row to make sure it doesn't get created
        assert false == __hasModeValueCache()
        assert true == __ms_activateSpec(__ms_optionSpecs.keySet().first(), curDeviceState)
        curDeviceState = __ms_getDeviceState()
        assert true == (__ms_getActiveSpec() != null)
        assert null != __ms_getModeValueCache()
        assert true == __ms_getModeValueCache().isEmpty()
        assert false == __hasModeValueCache()
        assert null != __ms_getModeValueCache().computeIfAbsent(20, { 42 })   // Confirm entry was absent previously
        assert true == __hasModeValueCache()
        assert 42 == __ms_getModeValueCache().get(20)?.value
        return true
    } catch (AssertionError | Exception e) {
        error e
    } finally {
        __ms_setDeviceState(savedDeviceState, curDeviceState)
    }
    return false
}
// #endif
