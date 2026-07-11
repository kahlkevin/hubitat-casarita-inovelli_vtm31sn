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

@Field static final String EVT_SUBCOMPLETE = "subscriptionComplete"
@Field static final List<String> SUBCOMPLETE_VAL = ["false", "true"].asImmutable()

@Field static final String USERCONFIRMS = "Yes, I know what I'm doing."
@Field static final String NOTMATTER = "Driver instance not associated with a physical Matter-controlled device."
// #ifdef ENABLE_DEBUG_MODESELECT
@Field static final String DBG_DELSWVER_I = "kill: softwareVersionId (device data)"
@Field static final String DBG_DELSWVER_S = "kill: softwareVersion (device data)"
// #endif
// #ifdef ENABLE_DISTCLEAN_SUPPORT
@Field static final String DBG_DISTCLEAN = "kill: DIST CLEAN, kill child devices and wipe data (CAREFUL!)"
// #endif
// #ifdef ENABLE_ATTRIBUTE_PASSIVE_CACHE
@Field static final String DBG_SAVEATTRS = "save: CURRENT matter endpoints/clusters/attributes (value cache)"
// #endif
// #ifdef ENABLE_TESTS_FOR_MODESELECT
@Field static final String DBG_TESTMODESELECT = "test: run modeSelect tests"
// #endif
// #ifdef ENABLE_TESTS_FOR_PARSER
@Field static final String DBG_TESTPARSER = "test: run tlv parser tests"
// #endif
// #ifdef ENABLE_DEBUG_SUBS
@Field static final String DBG_DUMPSUBS = "dump: subscription and refresh details"
@Field static final String DBG_SUBSCRIBE2 = "call: subscribe2()"
@Field static final String DBG_SUBSCRIBE3 = "call: subscribe3()"
// #endif
@Field static final String DBG_UNSUBSCRIBE = "call: unsubscribe()"
// #if @defined(L_NAIVE_HIGH_WATER)
// #error Don't define L_xxxx macros outside of individual source files
// #endif
// #if @EVENT_SEQUENCING != none && @EVENT_SEQUENCING != legacy && @EVENT_SEQUENCING != reorder && @EVENT_SEQUENCING != urgent
// #error EVENT_SEQUENCING must be set to none|legacy|reorder|urgent
// #endif
// #ifeq @EVENT_SEQUENCING urgent
// #error 'EVENT_SEQUENCING=urgent' mode is not yet supported
// #endif
// #if @EVENT_SEQUENCING == legacy
// #define L_NAIVE_HIGH_WATER
// #endif
// #ifdef ENABLE_ATTRIBUTE_SCAN_AND_SAVE

@Field static final String DBG_SAVEMATTER = "save: ALL matter device endpoints/clusters/attributes"
@Field static final String AWAITFULLDUMP = "awaitingFullMatterDump"
// #endif

metadata {
    definition (
        name: "@MDNAME(Inovelli Dimmer White VTM31-SN)",
        author: "Kevin Kahl",
        namespace: "@NAMESPACE",
        version: "@VERSION"
    ) {
        capability "Configuration"
        capability "EnergyMeter"
        capability "Initialize"
        capability "PowerMeter"
        capability "Refresh"

        attribute EVT_IDTIME, "number"
        attribute EVT_IDTYPE, "string"
        attribute EVT_SUBCOMPLETE, "enum", SUBCOMPLETE_VAL

        c06_definition()
        c08_definition()
        c3b_definition()

        fingerprint endpointId:"01", inClusters:"0003,0004,0006,0008,001D,0040,0041,122FFC31", outClusters:"", model:"@MDNAME(VTM31-SN)", manufacturer:"@MDNAME(Inovelli)", controllerType:"MAT"

        command "configure",    [[name: "Configure*",   type: "ENUM",   description: "<ol><li>Confirm child devices</li><li>Restore necessary persistent settings</li><li>Re-initialize Matter subscriptions</li></ol><i>Intended for rare use when you've just switched from some other driver type to this one (in the Device Info tab).</i><br/><br/><i><b>WARNING</b>: Device events may be ignored while this command is running.</i>", constraints: [USERCONFIRMS]]]
        command "identify",     [[name: "Identification Period (seconds)",
                                                        type: "NUMBER", description: "Pulse the switch LED indicator for a period of time to make it easy to identify and confirm communication with the correct switch (default: 10 seconds)."]]
        command "initialize",   [[name: "Initialize*",  type: "ENUM",   description: "Automatically triggered to renew Matter device subscriptions whenever Hubitat reboots or subscriptions are lost (due to power failure, network issues, etc.).<br/><br/>There is <u>no need</u> to ever run this manually.<br/><br/><i><b>WARNING</b>: Device events may be ignored while this command is running.</i>", constraints: [USERCONFIRMS]]]
        command "refresh",      [[name: "Refresh",                      description: "Re-reads various settings from the switch device. Safe to use anytime, although usually not necessary. Use whenever you want to double-check everything is in sync.<br/><br/><i><b>NOTE</b>:  This may cause values displayed here and on the Preferences and Device Info tabs to change.</i>"]]
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

        dl_definition(
            [
//  #ifdef ENABLE_DEBUG_MODESELECT
                DBG_DELSWVER_I,
                DBG_DELSWVER_S,
//  #endif
//  #ifdef ENABLE_DISTCLEAN_SUPPORT
                DBG_DISTCLEAN,
//  #endif
//  #ifdef ENABLE_ATTRIBUTE_PASSIVE_CACHE
                DBG_SAVEATTRS,
//  #endif
//  #ifdef ENABLE_ATTRIBUTE_SCAN_AND_SAVE
                DBG_SAVEMATTER,
//  #endif
//  #ifdef ENABLE_TESTS_FOR_MODESELECT
                DBG_TESTMODESELECT,
//  #endif
//  #ifdef ENABLE_TESTS_FOR_PARSER
                DBG_TESTPARSER,
//  #endif
//  #ifdef ENABLE_DEBUG_SUBS
                DBG_DUMPSUBS,
                DBG_SUBSCRIBE2,
                DBG_SUBSCRIBE3,
//  #endif
                DBG_UNSUBSCRIBE,
            ]
        )
// #endif
    }

    preferences {
// #ifdef ENABLE_DEBUG_LIFECYCLE
        debug "metadata.preferences - (re)generate preferences inputs"
// #endif
        __prefs_api.apply(this, [
// #ifdef ENABLE_BLACK_BOX
            bb_genPrefs(),
// #endif
            dl_genPrefs(),
            ms_api.genPrefs(this) ?: [],
            c06_genPrefs(),
            c08_genPrefs(),
        ], true)
    }
}

@Field final Map __driver_api = [
    isSubComplete: this.&__isSubComplete,
    setSubCompleteLocked: this.&__setSubComplete,
    dispatchMsg: this.&__dispatchMsg,
].asImmutable()

@Field final Map __prefs_api = [
    apply: this.&__applyPrefs,
].asImmutable()

Map getApi() {
    return [
        driver: __driver_api,
        modeSelect: ms_api,
        prefs: __prefs_api,
    ].asImmutable()
}

@Field final Closure __fire = { Map hubEvent ->
    Map<String, Closure> listeners = ms_api.listeners
// #ifdef ENABLE_DEBUG_DISPATCH
    ifDebug {
        boolean hasListener = listeners[hubEvent.name] != null
        String msg = "__fire(\"${hubEvent.name}\"), hasListener = ${hasListener}"
        if (hasListener) debug msg; else warn msg
    }
// #endif
    listeners[hubEvent.name]?.call(hubEvent)
}

void retireHubEvent(Map hubEvent) {
    hr_retireHubEvent(hubEvent, [
        onCustom: __fire,
        onUpdate: __fire,
    ])
}

// #ifdef FUTURE
//  (a) Consider migration to parse(Map) (newParse)
//  (b) Potentially simplified logic in mm_matterMsgFrom(), but...
//  (c) Unclear value-add just now, with possible tradeoff limitations
//  (d) See https://community.hubitat.com/search?q=newParse
// #endif
// This parser handles the Matter messages originating from Hubitat's Matter controller
void parse(String description) {
    try {
        Map matterMsg = mm_matterMsgFrom(description)

        switch (matterMsg.type) {
            case mm_matterMsgType.SUBSCRIPTION_RESULT:
                debug "Subscription ID: ${matterMsg.subscriptionId}"
// #ifeq @EVENT_SEQUENCING reorder
                es_subWaitFinished()    // Manages subComplete status via driver api calls
// #else
                __setSubComplete(true)
// #endif
// #ifdef ENABLE_ATTRIBUTE_SCAN_AND_SAVE
                if (state[AWAITFULLDUMP]) {
                    state.remove(AWAITFULLDUMP)
                    ds_save("full_matter_dump", "Full Matter attribute dump")
                    __subscribe()
                }
// #endif
                break

            case mm_matterMsgType.ATTR_REPORT:
// #ifdef ENABLE_ATTRIBUTE_PASSIVE_CACHE
                ds_put(matterMsg.endpointInt, matterMsg.clusterInt, matterMsg.attrInt, matterMsg.decodedValue)
// #endif
                __dispatchMsg(matterMsg)
                break

            case mm_matterMsgType.EVENT:
// #ifeq @EVENT_SEQUENCING reorder
                es_sequenceEvent(matterMsg, description)
// #else
                __legacy_handleEvent(matterMsg, description)
// #endif
                break

            default:
                debug "Unknown Matter message type: '${description}'"
                break
        }
    } catch (AssertionError | Exception e) {
        error e
    }
}

void __dispatchMsg(Map matterMsg) {
    Map hubEvent = hm_hubEventFrom(matterMsg)
    if (hubEvent == null) return
// #if @defined(ENABLE_DEBUG_EVENTS) || @defined(ENABLE_DEBUG_DISPATCH)
    ifDebug { if (matterMsg.containsKey(@NAMEOF(eventSerialInt))) debug "Dispatching ${hubEvent}" }
// #endif
    List<DeviceWrapper> targets = childDevices.plus(device).findAll { hr_isDeviceInterestedInHubEvent(it, matterMsg.endpointInt) }
    if (targets)
        targets.each { (it == device ? this : it).retireHubEvent(hubEvent) }
    else {
// #ifdef ENABLE_ATTRIBUTE_SCAN_AND_SAVE
        if (!state[AWAITFULLDUMP]) {
// #endif
        ifDebug { if (!(matterMsg.clusterInt in [0x0050])) warn "No drivers have registered for Matter attribute/event reports for endpoint ${hubEvent}" }
// #ifdef ENABLE_ATTRIBUTE_SCAN_AND_SAVE
        }
// #endif
        // Parent device to serve as default fallback handler
        retireHubEvent(hubEvent)
    }
}
// #ifneq @EVENT_SEQUENCING reorder

void __legacy_handleEvent(Map matterMsg, String description) {
// #ifdef L_NAIVE_HIGH_WATER
    // parse() does not always get called in event serial order. Out-of-sequence is possible.
    def hw = state.eventSerialHighWater ?: 0
    if (matterMsg.eventSerialInt > hw) {
// #endif
        state.eventSerialHighWater = matterMsg.eventSerialInt
        if (__isSubComplete()) {
            __dispatchMsg(matterMsg)
// #ifdef ENABLE_DEBUG_EVENTS
        } else {
            debug "Rejecting (sub not complete): ${description}"
// #endif
        }
// #ifdef L_NAIVE_HIGH_WATER
//  #ifdef ENABLE_DEBUG_EVENTS
    } else {
        debug "Rejecting (below highwater [hw = ${hw}, evtSerial = ${matterMsg.eventSerialInt}]): ${description}"
//  #endif
    }
// #endif
}
// #endif

void installed() {
    dl_installed()
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}(hubInitiated) - normally called only the first time this driver is installed on a device"
// #endif
}

void initialize(userInitiated = null) {
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}(${userInitiated ? "user" : "hub"}Initiated) - typically called after children during hub boot (or when Hubitat wants us to re-request our Matter subscriptions)"
    try {
// #endif
    if (!isMatterDevice()) {
        warnAlways NOTMATTER + " Skipping device subscription."
        return
    }

    __subscribe()
// #ifdef ENABLE_DEBUG_LIFECYCLE
    } finally { debug "${dl_currentMethod()}() - EXIT" }
// #endif
}

void configure(userInitiated = null) {
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}(${userInitiated ? "user" : "hub"}Initiated) - ensure device children and attribute/event subscriptions"
    try {
// #endif
    if (!isMatterDevice()) {
        warnAlways NOTMATTER + " Skipping device configuration."
        return
    }

    // Create child devices if necessary
    __ensureChildDevices()

    // Give each child device a chance to update/fix its configuration
    if (userInitiated) childDevices?.each { try { it.installed(true) } catch (AssertionError | Exception e) { error e } }

    // Ensure proper client installations into persistent data
    getApi().modeSelect.prefClientInstalled(device, 1)
    hr_hubEventClientInstalled(device, [@ENDPOINT_ROOT, @ENDPOINT_LOAD, @ENDPOINT_ELECINFO].plus(@BUTTON_EP_MAP.keySet()).sort())

    // Cluster implmentation-related installations
    c3b_installed()

    // Launch Matter device subscription
    initialize(true)
// #ifdef ENABLE_DEBUG_LIFECYCLE
    } finally { debug "${dl_currentMethod()}() - EXIT" }
// #endif
}

void updated() {
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}() - process preference changes"

// #endif
    try {
        dl_updated()
// #ifdef ENABLE_BLACK_BOX
        bb_updated()
// #endif
        getApi().modeSelect.updated(device)
        c06_updated()
        c08_updated()
    } catch (AssertionError | Exception e) {
        error e
    }
// #ifdef ENABLE_DEBUG_LIFECYCLE

    debug "${dl_currentMethod()}() - EXIT"
// #endif
}

void refresh(boolean subAlreadyPending = false) {
// #ifdef ENABLE_DEBUG_LIFECYCLE
    debug "${dl_currentMethod()}(${subAlreadyPending}) - re-sync state from physical device"
    try {
// #endif
    List<Map> attrs = hm_getRefreshPaths()
    String cmd = matter.readAttributes(attrs)

    sendMatterCommand(cmd)

    if (subAlreadyPending) return

    attrs = hm_getSubscriptionPaths().findAll { it.attr }   // Only attributes, no events
    cmd = matter.readAttributes(attrs)

    sendMatterCommand(cmd)
// #ifdef ENABLE_DEBUG_LIFECYCLE
    } finally { debug "${dl_currentMethod()}() - EXIT" }
// #endif
}

boolean __isSubComplete() { device.currentValue(EVT_SUBCOMPLETE) == SUBCOMPLETE_VAL[1] }
void __setSubComplete(boolean complete) { sendEvent(name: EVT_SUBCOMPLETE, value: SUBCOMPLETE_VAL[complete ? 1 : 0]) }

void __subscribe(boolean subscribeToEverything = false) {
    String cmd

    if (!isMatterDevice()) {
        error "This is not a Matter device. Unable to create a Matter subscription."
        return
    }

    // Refresh just the refresh-only attributes.
    // This ensures basic version information so we can
    // activate the correct data set.
    refresh(true)

    if (subscribeToEverything) {
        cmd = matter.cleanSubscribe(0, 600, [matter.attributePath("FFFF", -1, -1)])
    } else {
        List subIntervals = [@SUBSCRIPTION_MIN_INTERVAL, @SUBSCRIPTION_MAX_INTERVAL]
// #ifdef JSON_SUB
        String subEntities = hm_getSubscriptionEntitiesJson()
        if (subEntities) cmd = String.format('he cleanSubscribe 0x%1$02X 0x%2$04X ', *subIntervals) + subEntities
// #else
        List subPaths = hm_getSubscriptionPaths()
        if (subPaths) cmd = matter.cleanSubscribe(*subIntervals, subPaths)
// #endif
    }

    if (cmd) {
// #ifeq @EVENT_SEQUENCING reorder
        es_subWaitStarted() // Manages subComplete status via driver api calls
// #else
        __setSubComplete(false)
// #endif
// #ifdef ENABLE_ATTRIBUTE_PASSIVE_CACHE
        ds_clear()
// #endif
// #ifdef ENABLE_DEBUG_SUBS
        debug "Matter Subscription: ${cmd}"
// #endif
        sendMatterCommand(cmd)
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
            case DBG_UNSUBSCRIBE:
                __setSubComplete(false)
                sendMatterCommand(matter.unsubscribe())
                break
//  #ifdef ENABLE_DEBUG_MODESELECT

            case DBG_DELSWVER_S:
                device.removeDataValue(EVT_SWVER_S)
                break

            case DBG_DELSWVER_I:
                device.removeDataValue(EVT_SWVER_L)
                break
//  #endif
//  #ifdef ENABLE_DISTCLEAN_SUPPORT

            case DBG_DISTCLEAN:
                warnAlways "distclean: unsubscribe"
                __setSubComplete(false)
                sendMatterCommand(matter.unsubscribe())
                warnAlways "distclean: deactive modeSelect spec"
                ms_api.debug.deactivateSpec()
                warnAlways "distclean: remove child devices"
                childDevices.each { deleteChildDevice(it.deviceNetworkId) }
                warnAlways "distclean: remove prefs, state, and device data"
                dl_distClean()
                warnAlways "distclean: done! (hint: consider reactivating logging)"
                break
//  #endif
//  #ifdef ENABLE_ATTRIBUTE_PASSIVE_CACHE

            case DBG_SAVEATTRS:
                ds_save()
                break
//  #endif
//  #ifdef ENABLE_ATTRIBUTE_SCAN_AND_SAVE

            case DBG_SAVEMATTER:
                try {
                    state[AWAITFULLDUMP] = true
                    __subscribe(true)
                } catch (AssertionError | Exception e) {
                    error e
                }
                break
//  #endif
//  #ifdef ENABLE_TESTS_FOR_MODESELECT

            case DBG_TESTMODESELECT:
                try {
                    ms_runAllTests()
                } catch (AssertionError | Exception e) {
                    error e
                }
                break
//  #endif
//  #ifdef ENABLE_TESTS_FOR_PARSER

            case DBG_TESTPARSER:
                try {
                    tp_runAllTests()
                } catch (AssertionError | Exception e) {
                    error e
                }
                break
//  #endif
//  #ifdef ENABLE_DEBUG_SUBS

            case DBG_DUMPSUBS:
//  #ifdef TEMPERATURE_SUPPORT
                Map dm = [endpointInt: 0x0008, clusterInt: 0x0402, attrInt: 0x0000, decodedValue: 2250]
                debug hm_hubEventFrom(dm)
//  #endif
                debug "__hm_data_field == null (${__hm_data_field == null})"
//  #ifdef JSON_SUB
                debug hm_getSubscriptionEntitiesJson()
//  #endif
                debug hm_getSubscriptionPaths()
                debug hm_getRefreshPaths()
                break

            case DBG_SUBSCRIBE3:
            case DBG_SUBSCRIBE2:
                L1:{
                    List subPaths = [
                        matter.attributePath("FFFF", 0x06, -1), // On/Off
                        matter.attributePath("FFFF", 0x08, -1), // Level
                        matter.attributePath("FFFF", 0x3B, 1),  // Switch state attribute
                        matter.eventPath("FFFF", 0x3B, -1),     // Switch events
                    ]
                    __setSubComplete(false)
                    String cmd = matter.cleanSubscribe((arg == DBG_SUBSCRIBE2) ? 0 : 1, 60, subPaths)
                    debug "Matter Subscription: ${cmd}"
                    sendMatterCommand(cmd)
                }
                break
//  #endif
        }
    }
}
// #endif

void __ensureChildDevices() {
    try {
        assert(getDataValue("model") == "VTM31-SN")
        List<String> cdNames = childDevices.collect { it.deviceNetworkId }
        Closure ensure = { String name, Closure create -> name = "${device.deviceNetworkId}-${name}"; if (!cdNames.contains(name)) create(name) }
        ensure('notif') { addChildDevice("@NAMESPACE", "@MDNAME(Inovelli RGBW Notification Bar White)", it)?.setDisplayName("${device.displayName} LED Notification Bar") }
    } catch (AssertionError | Exception e) {
        error e
    }
}

/***************************************************************************************************************************
** PREFERENCES ORGANIZATION AND RENDER HELPERS
****************************************************************************************************************************/

// #ifdef ENABLE_PREF_SECTION_TITLE_COLOR
String __prefSectionTitle(String text) { "<span style='color: dodgerblue'>" + text + " --&gt;</span>" }
// #else
String __prefSectionTitle(String text) { "<u>" + text + "</u> --&gt;" }
// #endif

List<Map> __sortPrefs(List<Map> unsorted) {
// #ifdef ENABLE_DEBUG_PREFS
    int count = 0
// #endif
    unsorted.sort { Map a, Map b ->
        Map ag = a?.group
        Map bg = b?.group
        int r
        if (!ag || !bg) {
            if (ag) r = -1
            else if (bg) r = 1
            else r = 0
        } else {
            String aName = ag.name ?: "zzz"
            String bName = bg.name ?: "zzz"
            r = aName <=> bName
            if (r == 0) {
                int aPri = ag.pri ?: 0
                int bPri = bg.pri ?: 0
                r = aPri <=> bPri
            }
        }
// #ifdef ENABLE_DEBUG_PREFS
        log.debug "Round ${++count}: ag = ${ag}, bg = ${bg}, r = ${r}"
// #endif
        return r
    }
}

void __applyPrefs(Object prefDriver, List<List<Map>> unsortedPrefs, boolean includeEndUserPrefsCard) {
    assert prefDriver && prefDriver.metaClass.respondsTo(prefDriver, @NAMEOF(input), Map)
    if (includeEndUserPrefsCard) { unsortedPrefs.add([[type: "paragraph", title: 'End User Preferences', description: "Use these settings to fine tune behavioral aspects of the smart switch to your individual preference and usage requirements.", group: [name: "30user", pri: 0]]]) }
    List<Map> p = __sortPrefs(unsortedPrefs.collectMany { it }).collect { if (it.type == "paragraph") it.title = __prefSectionTitle(it.title); it }
// #ifdef ENABLE_DEBUG_PREFS
    log.debug(p)
// #endif
    p.each { prefDriver.input(it) }
}

// #define API
// #define CAPABILITY
// #define ENDPOINT @ENDPOINT_LOAD
// #include "cluster0x0003-identify.groovy"
// #include "cluster0x0006-onOff.groovy"
// #include "cluster0x0008-level.groovy"
// #undef ENDPOINT
// #include "cluster0x003B-switch.groovy"
// #undef CAPABILITY
// #undef API
// #include "notificationCommon.groovy"
// #include "macros/begin_library_section"
//// #define PARENT_DRIVER
@FIRST_INCLIB(debugAndLogging)
@INCLIB(matterHelpers)
// #if @defined(ENABLE_ATTRIBUTE_PASSIVE_CACHE) || @defined(ENABLE_ATTRIBUTE_SCAN_AND_SAVE)
@INCLIB(attrDataStore)
// #endif
// #ifdef ENABLE_BLACK_BOX
@INCLIB(blackBox)
// #endif
// #ifeq @EVENT_SEQUENCING reorder
@INCLIB(eventSequencer)
// #endif
@INCLIB(hubEventRetire)
@INCLIB(hubEventMake)
@INCLIB(cluster0x0050-modeSelect)
@INCLIB(matterMsgMake)
@INCLIB(tlvParser)
