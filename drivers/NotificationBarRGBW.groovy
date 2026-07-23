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
// #ifdef MONOLITH_BUILD

/**
 *  This file is generated. Do not edit directly.
 *  Source modules are combined from the project source tree.
 */
// #endif

// #include "imports.groovy"

@Field static final String ATTR_EFFECTNAME = "effectName"
@Field static final String ATTR_LIGHTEFFECTS = "lightEffects"
@Field static final String OPT_LEDEFFECT = "ledEffect"
@Field static final String PREF_CCTLEVEL = @NAMEOF(coupleColorTempToLevel)

@Field static final byte C08_OPTBIT_CCTTOLEVEL = 0b10

metadata {
    definition (
        name: "@MDNAME(Inovelli RGBW Notification Bar White)",
        author: "Kevin Kahl",
        namespace: "@NAMESPACE",
        version: "@VERSION"
    ) {
        capability "ColorControl"
        capability "ColorMode"
        capability "ColorTemperature"
        capability "LightEffects"
        capability "SwitchLevel"

        c06_definition()

        command "setLevel"  , [[name: "level*", type: "NUMBER", description:"Level to set (1 to 100)"]]

        // Override the default UI since we already have level as a separate setting and the endpoint doesn't support transition duration
        command "setColorTemperature", [[name: "colorTemperature*", type: "NUMBER", description: "Color temperature in degrees Kelvin"]]
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

        dl_definition([DBG_DEFAULT])
// #endif
    }

    preferences {
// #ifdef ENABLE_DEBUG_LIFECYCLE
        debug "metadata.preferences - (re)generate preferences inputs"
// #endif
        Map api = parent?.getApi()
        Map prefsApi = api?.prefs
        prefsApi?.apply(this, [
// #ifdef ENABLE_BLACK_BOX
            bb_genPrefs(),
// #endif
            dl_genPrefs(),
            api?.modeSelect?.genPrefs(this) ?: [],
            c06_genPrefs(),
            [
                [name: PREF_CCTLEVEL, type: "bool", title: "Couple Color Temp to Level", description: "Setting Level also changes color temperature when Color Mode is CT"],
            ],
        ], true)
    }
}

// Called when driver is first installed and also whenever the parent driver is explicitly configured (via configure())
void installed(parentInitiated = null) {
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}(${parentInitiated ? "parent" : "hub"}Initiated)"

// #endif
    dl_installed()
    try {
        parent.getApi().modeSelect.attrClientInstalled(device, @ENDPOINT_LED)
        hr_hubEventClientInstalled(device, @ENDPOINT_LED)
    } catch (NullPointerException) {
        warnAlways "Driver is not properly configured and will not function. (Did you install it manually?)"
    } catch (AssertionError | Exception e) {
        error e
    }
// #ifdef ENABLE_DEBUG_LIFECYCLE

    debug "${dl_currentMethod()}() - EXIT"
// #endif
}

void retireHubEvent(Map hubEvent) {
    hr_retireHubEvent(hubEvent, [
        onCustom: {
            if (it.name == EVT_C08OPTIONS) {
                def oldValue = state[EVT_C08OPTIONS]
                state[EVT_C08OPTIONS] = it.value
                device.updateSetting(PREF_CCTLEVEL, [type: "bool", value: "${!!(it.value & C08_OPTBIT_CCTTOLEVEL)}"])
                return oldValue
            }
            return hr_unhandled
        },
        onFilter: { if (it.colorControl) runInMillis(100, @NAMEOF(updateColorControlAttributes)) },
    ])
}

void updated() {
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}()"

// #endif
    dl_updated()
// #ifdef ENABLE_BLACK_BOX
    bb_updated()
// #endif
    parent?.getApi()?.modeSelect.updated(device)
    c06_updated()
    Byte c08Options = state[EVT_C08OPTIONS]
    if (c08Options != null && (coupleColorTempToLevel != (!!(c08Options & C08_OPTBIT_CCTTOLEVEL)))) {
        byte val = coupleColorTempToLevel ? c08Options | C08_OPTBIT_CCTTOLEVEL : c08Options & ~C08_OPTBIT_CCTTOLEVEL
        // Write Attribute (Matter Application Cluster Specification R1.4 § 1.6.6.9)
        String cmd = matterWriteAttribute(@ENDPOINT_LED, 0x0008, 0x000F, mdv.uint8(val))
        parent?.sendMatterCommand(cmd)
    }
// #ifdef ENABLE_DEBUG_LIFECYCLE

    debug "${dl_currentMethod()}() - EXIT"
// #endif
}

void setHue(BigDecimal hue) {
    try {
        // Notes
        //  - We set ExecuteIfOff to ensure hue value changes regardless of off/on state
        //    (See the first and third examples in the table at Matter Application Cluster Specification R1.4 § 1.6.8.1)

        // MoveToHue Command (Matter Application Cluster Specification R1.4 § 3.2.8.4)
        String cmd = matterInvoke(@ENDPOINT_LED, 0x0300, 0x00,
            mdv.uint8(userPctToUint8(hue)),
            mdv.enum8(0),                       // DirectionEnum.Shortest
            mdv.ZeroU16,                        // Transition time
            *mdv.ExecuteIfOff
        )
        parent?.sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

void setSaturation(BigDecimal saturation) {
    try {
        // Notes
        //  - We set ExecuteIfOff to ensure saturation value changes regardless of off/on state
        //    (See the first and third examples in the table at Matter Application Cluster Specification R1.4 § 1.6.8.1)

        // MoveToSaturation Command (Matter Application Cluster Specification R1.4 § 3.2.8.7)
        String cmd = matterInvoke(@ENDPOINT_LED, 0x0300, 0x03,
            mdv.uint8(userPctToUint8(saturation)),
            mdv.ZeroU16,                        // Transition time
            *mdv.ExecuteIfOff
        )
        parent?.sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

void setLevel(BigDecimal level) {
    try {
        // Notes
        //  - We set ExecuteIfOff to ensure level value changes regardless of off/on state
        //    (See the first and third examples in the table at Matter Application Cluster Specification R1.4 § 1.6.8.1)
        //  - Bug: this endpoint seems to ignore/fail the entire command if the TransitionTime field is non-null/non-zero (tested with firmware 1.1.5)
        //    (Matter Application Cluster Specification R1.4 § 1.6.7.1.1 says to disregard the _field_ if not capable of supporting)

        // MoveToLevel Command (Matter Application Cluster Specification R1.4 § 1.6.7.1)
        String cmd = matterInvoke(@ENDPOINT_LED, 0x0008, 0x00,
            mdv.uint8(Math.max(1, userPctToUint8(level))),  // Minimum value is 1
            mdv.ZeroU16,                        // Transition time
// #ifdef EXPERIMENT_IGNORE_CT_COUPLING
            *mdv.optionsOverride(0b11, 0b01)    // Execute if off, don't couple color temperature
// #else
            *mdv.ExecuteIfOff
// #endif
        )
        parent?.sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

// #ifdef FUTURE
//  (a) Batch the two outgoing Matter commands?
//  (b) Device appears perfectly responsive today, so not a requirement just now.
//  (c) Might be worth doing if more compound-command scenarios are implemented in future.
// #endif
void setColor(colormap) {
    try {
        // Notes
        //  - We set ExecuteIfOff to ensure color values change regardless of off/on state
        //    (See the first and third examples in the table at Matter Application Cluster Specification R1.4 § 1.6.8.1)

        // MoveToHueAndSaturation Command (Matter Application Cluster Specification R1.4 § 3.2.8.10)
        String cmd = matterInvoke(@ENDPOINT_LED, 0x0300, 0x06,
            mdv.uint8(userPctToUint8(colormap.hue)),
            mdv.uint8(userPctToUint8(colormap.saturation)),
            mdv.ZeroU16,                        // Transition time
            *mdv.ExecuteIfOff
        )
        parent?.sendMatterCommand(cmd)

        setLevel(colormap.level)
    } catch (AssertionError | Exception e) {
        error e
    }
}

void setColorTemperature(BigDecimal degreesK, ignoredLevel = null, unsupportedDuration = null) {
    try {
        // Notes
        //  - We set ExecuteIfOff to ensure color temperature changes regardless of off/on state
        //    (See the first and third examples in the table at Matter Application Cluster Specification R1.4 § 1.6.8.1)

        // MoveToColorTemperature Command (Matter Application Cluster Specification R1.4 § 3.2.8.14)
        String cmd = matterInvoke(@ENDPOINT_LED, 0x0300, 0x0A,
            mdv.uint16(userKelvinToMireds(degreesK)),
            mdv.ZeroU16,                        // Transition time
            *mdv.ExecuteIfOff
        )
        parent?.sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

void updateColorControlAttributes() {
    String colorMode = device.currentValue("colorMode")
    String colorName
    Map hsv = [
        hue: mh_safeParseInteger(device.currentValue("hue")),
        saturation: mh_safeParseInteger(device.currentValue("saturation")),
        level: mh_safeParseInteger(device.currentValue("level")),
    ]

    if (hsv.hue != null && hsv.saturation != null && hsv.level != null) {
        sendEvent(name: "color", value: "${hsv}")
        sendEvent(name: "RGB", value: "${ColorUtils.rgbToHEX(ColorUtils.hsvToRGB([hsv.hue, hsv.saturation, hsv.level]))}")
        if (colorMode == "RGB") colorName = convertHueToGenericColorName(hsv.hue, hsv.saturation)
    }
    if (colorMode == "CT") {
        Integer ct = mh_safeParseInteger(device.currentValue("colorTemperature"))
        if (ct != null) colorName = convertTemperatureToGenericColorName(ct)
    }
    if (colorName) sendEvent(name: "colorName", value: colorName)
}


void setEffect(BigDecimal userEffectNumber) {
    try {
        int effectNumber = userNumberToPosInt(userEffectNumber)
        Set<Integer> lightEffectIndices = __getModeConstraints(OPT_LEDEFFECT)?.keySet()
        if (effectNumber in lightEffectIndices)
            parent.getApi().modeSelect.updated(OPT_LEDEFFECT, effectNumber)
        else
            error "${userEffectNumber} is not a valid Light Effects index number. Valid values are: ${lightEffectIndices ?: '&lt;undefined&gt;, try refreshing.'}"
    } catch (AssertionError | Exception e) {
        error e
    }
}

int __getNextEffect(boolean forwardDirection) {
    String effectName = device.currentValue(ATTR_EFFECTNAME)
    List<Map> modes = parent?.getApi()?.modeSelect?.getModes(OPT_LEDEFFECT)
    if (modes == null) throw new IllegalStateException("Valid light effects are not yet loaded. Try refreshing.")
    int lastIndex = modes.size() - 1
    int nextIndex = modes.findIndexOf({ it.label == effectName }) + (forwardDirection ? 1 : -1)
// #ifdef SKIP_OFF_LIGHT_EFFECT
    if (nextIndex < 1) { nextIndex = lastIndex }
    else if (nextIndex > lastIndex) { nextIndex = 1 }
// #else
    if (nextIndex < 0) { nextIndex = lastIndex }
    else if (nextIndex > lastIndex) { nextIndex = 0 }
// #endif
    return modes[nextIndex].mode
}

void setNextEffect() {
    try {
        setEffect(__getNextEffect(true))
    } catch (AssertionError | Exception e) {
        error e
    }
}

void setPreviousEffect() {
    try {
        setEffect(__getNextEffect(false))
    } catch (AssertionError | Exception e) {
        error e
    }
}

Map __getModeConstraints(String option) { parent?.getApi()?.modeSelect?.getModes(option)?.collectEntries({ [(it.mode): it.label] })?.asImmutable() }

void modeSpecActivated() {
    try {
        Map lightEffects = __getModeConstraints(OPT_LEDEFFECT)
        if (lightEffects) retireHubEvent([name: ATTR_LIGHTEFFECTS, value: new JsonBuilder(lightEffects).toString()])
    } catch (AssertionError | Exception e) {
        error e
    }
}

void modeChanged(String setting, int newMode) {
    switch (setting) {
        case OPT_LEDEFFECT:
            String effectName = parent.getApi().modeSelect.getModes(setting)?.find({ it.mode == newMode })?.label
            retireHubEvent([name: ATTR_EFFECTNAME, value: effectName])
            break

        default:
            break
    }
}
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

//  #ifndef ENABLE_DEBUG_COMMON_TRIGGERS
void debugTrigger(arg) {
    dl_debugTrigger(arg) {
//  #else
void debugTrigger(arg, markerText = null) {
    dl_debugTrigger(arg, markerText) {
//  #endif
        switch (it) {
            default:
                debug __getModeConstraints(OPT_LEDEFFECT)
                break
        }
    }
}
// #endif

// #define CAPABILITY
// #define CHILD_DRIVER
// #define ENDPOINT @ENDPOINT_LED
// #include "cluster0x0006-onOff.groovy"
// #undef ENDPOINT
// #undef CHILD_DRIVER
// #undef CAPABILITY
// #include "notificationCommon.groovy"
// #include "macros/begin_library_section"
//// #define CHILD_DRIVER
@FIRST_INCLIB(debugAndLogging)
@INCLIB(matterHelpers)
@INCLIB(hubEventRetire)
// #ifdef ENABLE_BLACK_BOX
@INCLIB(blackBox)
// #endif
