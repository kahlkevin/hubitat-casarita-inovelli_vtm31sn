// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** DEBUG AND LOGGING LIBRARY (symbol prefix: dl)
****************************************************************************************************************************/
// #else
/**
 *  Debug And Logging Library (symbol prefix: dl)
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
    name: "debugAndLogging",
    description: "Debugging and logging utility library (debug tool)",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(blackBox)
// #endif

// #ifdef MODULAR_BUILD
//  #define APICALL (parent ?: this).getApi().#1
// #else
//// #if @defined(CHILD_DRIVER)
////  #define APICALL parent.getApi().#1
//// #elif @defined(PARENT_DRIVER)
////  #define APICALL getApi().#1
//// #else
////  #warning Non-library mode, but neither PARENT_DRIVER nor CHILD_DRIVER have been defined. Check function.
//// #endif
// #endif
// #ifeq @FLAVOR dev
//  #define L_LOG_TIMEOUT_DESC no time limit
//  #define L_LOG_TIMEOUT_VAL 1
// #else
//  #define L_LOG_TIMEOUT_DESC 15 minutes
//  #define L_LOG_TIMEOUT_VAL 15
// #endif
// #ifndef ENABLE_DEBUG_TRIGGER_SUPPORT
//  #undef ENABLE_DEBUG_COMMON_TRIGGERS
// #endif
// #if @defined(ENABLE_ATTRIBUTE_PASSIVE_CACHE) || @defined(ENABLE_ATTRIBUTE_SCAN_AND_SAVE) || @defined(ENABLE_BLACK_BOX) || @defined(ENABLE_DEBUG_HEOBJS)
//  #define L_ENABLE_SAVE_TO_FILE
// #else
//  #undef ENABLE_FOLDER_SUPPORT
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// DL PUBLIC API (typical)
// --

def debug(msg) { if (__dl_debugLogEnable()) log.debug msg }
def debug(arg, Closure block){ if (__dl_debugLogEnable()) log.debug block?.call(arg) }
def debug(Closure block) { if (__dl_debugLogEnable()) log.debug block?.call() }
def info(msg) { if (__dl_infoLogEnable()) log.info msg }
def info(arg, Closure block) { if (__dl_infoLogEnable()) log.info block?.call(arg) }
def info(Closure block) { if (__dl_infoLogEnable()) log.info block?.call() }
def warn(msg) { if (__dl_debugLogEnable()) log.warn msg }

def ifDebug(arg, Closure block) { if (__dl_debugLogEnable()) block?.call(arg) }
def ifDebug(Closure block) { if (__dl_debugLogEnable()) block?.call() }
def ifInfo(arg, Closure block) { if (__dl_infoLogEnable()) block?.call(arg) }
def ifInfo(Closure block) { if (__dl_infoLogEnable()) block?.call() }

def infoAlways(msg) { log.info msg }
def warnAlways(msg) { log.warn msg }

def error(msg) {
    if (msg instanceof AssertionError || msg instanceof Exception) {
        def stack = msg.getStackTrace().findResult({ (it.getFileName()?.startsWith("user_driver_")) ? "method <code>${it.getMethodName()}</code> (${it.getFileName()}:${it.getLineNumber()})" : null })
        def message = msg.getMessage()?.replaceAll(/([\r\n]+)|\s{2,}/, "")
        if (msg instanceof Exception) message = "${getObjectClassName(msg)}: " + message
        msg = "<code>${message}</code> in ${stack}"
    }
    log.error msg
}

static String dl_currentMethod() { dl_currentMethod(1) }
static String dl_currentMethod(int additionalSkipLayers) {
    int skipCount = 1 + additionalSkipLayers
    return new Exception().stackTrace.findResult { (it.getFileName()?.startsWith("user_driver_")) ? ((skipCount--) ? null : it.methodName) : null }
}

// -------------------------------------------------------------------------------------------------------------------------
// DL PUBLIC API (integration)
// --
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

@Field static final String DBG_DEFAULT = "*default*"
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS
@Field static final String DBG_MARKERONLY = "*marker only*"
//  #endif

void dl_definition(List<String> extra = null) {
    boolean isChild = !(fingerprints as boolean)
    List<String> constraints = __dl_sharedTriggers(isChild)
    if (extra) constraints = constraints.plus(extra).sort()

    command @NAMEOF(debugTrigger), [
        [
            name: "Debug Trigger*",
            type: "ENUM",
            description: "Trigger diagnostic functions. These typically log to the debug channel.<br/><i>Be sure the \"Enable debug logging\" preference is enabled.</i>",
            constraints: constraints,
        ],
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS
        [
            name: "Marker Text (optional)",
            type: "STRING",
            description: "Emit marker text before triggering.",
        ],
//  #endif
    ]
}
// #endif

void dl_installed() { dl_updated() }

List<Map> dl_genPrefs() {
    [
        [type: "paragraph", title: 'Logging Control', description: "These settings govern log output.", group: [name: "10debug", pri: 0]],
        [name: @NAMEOF(debugLogEnable), type: "enum", title: "<b>Enable debug logging</b>", defaultValue: @L_LOG_TIMEOUT_VAL, options: __dl_loggingOptions, group: [name: "10debug", pri: 10]],
        [name: @NAMEOF(infoLogEnable), type: "enum", title: "<b>Enable informational logging</b>", defaultValue: @L_LOG_TIMEOUT_VAL, options: __dl_loggingOptions, group: [name: "10debug", pri: 15]],
    ]
}

void dl_updated() {
    int dle = __dl_debugLogEnable()
    int ile = __dl_infoLogEnable()
    if (dle > 1) runIn(dle*60, __dl_debugLogsOff); else unschedule(__dl_debugLogsOff)
    if (ile > 1) runIn(ile*60, __dl_infoLogsOff); else unschedule(__dl_infoLogsOff)
}
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

//  #ifndef ENABLE_DEBUG_COMMON_TRIGGERS
void dl_debugTrigger(arg, Closure custom = null) {
//  #else
void dl_debugTrigger(arg, markerText, Closure custom = null) {
    boolean isMarking = (markerText || arg == DBG_MARKERONLY || arg == __dl_tr_TREEMARKER)

    if (!__dl_debugLogEnable()) warnAlways "To view output from debugging triggers, debug logging typically must be enabled"

    if (isMarking) {
        switch (arg) {
            case DBG_MARKERONLY:
            case __dl_tr_TREEMARKER:
                String marker = __dl_formatMarker(null, markerText)
                if (arg == __dl_tr_TREEMARKER) childDevices.each { it.debug marker }
                debug marker
                return

            default:
                debug __dl_formatMarker("BEGIN", markerText)
                break
        }
    }

//  #endif
    if (!__dl_debugTrigger(arg) && custom) custom(arg)
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS

    if (isMarking) debug __dl_formatMarker("END", markerText)
//  #endif
}
// #endif
// #if @defined(ENABLE_DEBUG_HEOBJS) || @defined(L_ENABLE_SAVE_TO_FILE) || @defined(ENABLE_DISTCLEAN_SUPPORT)

// -------------------------------------------------------------------------------------------------------------------------
// DL PUBLIC API (helpers normally called from triggers)
// --
// #endif
// #ifdef ENABLE_DEBUG_HEOBJS

void dl_dumpObject(String desc, Object obj) {
    assert desc != null
    assert obj != null

    StringBuilder sb = new StringBuilder()
    sb << "${desc} class: ${getObjectClassName(obj)}\n\n"
    sb << "${desc} methods: ${obj?.metaClass?.methods*.name?.unique()?.sort()}\n\n"
    sb << "${desc} properties: ${obj?.metaClass?.properties*.name?.unique()?.sort()}\n"

    try {
        String fileName = dl_saveToFile("dump_${desc}_object", sb, false)
        infoAlways "${desc} object dump saved to '${fileName}'"
    } catch (AssertionError | Exception e) {
        error e
    }
}
// #endif
// #ifdef L_ENABLE_SAVE_TO_FILE

String dl_saveToFile(String fileNamePrefix, Object content, boolean serializeToJson = true) {
    assert fileNamePrefix

    String renderedContent
    if (content != null) {
        content = (content instanceof Map) ? __dl_orderMapByKey(content) : content
        renderedContent = (serializeToJson ? new JsonBuilder(content) : content)?.toString()
    }
    if (renderedContent == null) throw new Exception("Content renders to null. Nothing to save.")

    String fileName = "${fileNamePrefix}-${device.deviceNetworkId}.${serializeToJson ? "json" : "txt"}"
//  #ifdef ENABLE_FOLDER_SUPPORT
    fileName = __dl_prependFolder("@NAMESPACE logs", fileName)
//  #endif

    uploadHubFile(fileName, renderedContent.getBytes("UTF-8"))
    return fileName
}
// #endif
// #ifdef ENABLE_DISTCLEAN_SUPPORT

void dl_distClean() {
    __dl_killPrefs()
    __dl_killState()
    __dl_killData()
}
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// DL PRIVATE API
// --
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

//  #ifdef ENABLE_DEBUG_HEOBJS
@Field static final String __dl_tr_DEVICEOBJ = "save: object metadata: device"
@Field static final String __dl_tr_DRIVEROBJ = "save: object metadata: driver"
@Field static final String __dl_tr_MATTEROBJ = "save: object metadata: matter"
@Field static final String __dl_tr_FILELIST = "dump: hub files list"
//  #endif
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS
@Field static final String __dl_tr_DUMPATTRS = "dump: attributes"
@Field static final String __dl_tr_DUMPDDATA = "dump: device data"
@Field static final String __dl_tr_DUMPPREFS = "dump: preferences"
@Field static final String __dl_tr_KILLDDATA = "kill: device data (reset all non-preserved)"
@Field static final String __dl_tr_KILLPREFS = "kill: preferences (reset all)"
@Field static final String __dl_tr_KILLSTATE = "kill: state variables (reset all)"
@Field static final String __dl_tr_LOGGINGON = "call: loggingOn (@L_LOG_TIMEOUT_DESC)"
@Field static final String __dl_tr_TREELOGGING = "call: loggingOn (@L_LOG_TIMEOUT_DESC) + children"
@Field static final String __dl_tr_TREEMARKER = "*tree marker*"
//  #endif
//  #ifdef ENABLE_DEBUG_MODESELECT
@Field static final String __dl_tr_DUMPMSDS = "modeSelect: dump state"
@Field static final String __dl_tr_NULLMSDS = "modeSelect: deactivate state (no active pref/attr set)"
@Field static final String __dl_tr_VERIDMSDS = "modeSelect: dump version id"
//  #endif
//  #ifdef ENABLE_BLACK_BOX
@Field static final String __dl_tr_SAVEBLACKBOX = "save: black box"
//  #endif

static List<String> __dl_sharedTriggers(boolean isChild) {
    [
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS
        DBG_MARKERONLY,
        __dl_tr_DUMPATTRS,
        __dl_tr_DUMPDDATA,
        __dl_tr_DUMPPREFS,
        __dl_tr_KILLDDATA,
        __dl_tr_KILLPREFS,
        __dl_tr_KILLSTATE,
        __dl_tr_LOGGINGON,
//  #endif
//  #ifdef ENABLE_DEBUG_MODESELECT
        __dl_tr_DUMPMSDS,
        __dl_tr_NULLMSDS,
        __dl_tr_VERIDMSDS,
//  #endif
//  #ifdef ENABLE_BLACK_BOX
        __dl_tr_SAVEBLACKBOX,
//  #endif
//  #ifdef ENABLE_DEBUG_HEOBJS
        __dl_tr_DEVICEOBJ,
        __dl_tr_DRIVEROBJ,
        __dl_tr_FILELIST,
        __dl_tr_MATTEROBJ,
//  #endif
    ]
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS
    .plus(isChild ? [] : [__dl_tr_TREELOGGING, __dl_tr_TREEMARKER])
//  #endif
    .sort()
}
// #endif

static int __dl_prefInt(val) {
    if (val instanceof Integer) return val
    if (val == null || val == '' || val == '0') return 0
    if (val == '1') return 1
    if (val instanceof Boolean) return val ? 1 : 0
    if (val instanceof Number) return val.intValue()

    try {
        return Integer.parseInt(val.toString())
    } catch (ignored) {
        return 0
    }
}

int __dl_debugLogEnable() { __dl_prefInt(debugLogEnable) }
int __dl_infoLogEnable() { __dl_prefInt(infoLogEnable) }

@Field static final Map __dl_loggingOptions = [
    0:    "Off",
    1:    "On",
    15:   "On, for 15 minutes",
    30:   "On, for 30 minutes",
    60:   "On, for 1 hour",
    120:  "On, for 2 hours",
    240:  "On, for 4 hours",
    480:  "On, for 8 hours",
    1400: "On, for 24 hours",
].asImmutable()
// #ifdef ENABLE_DEBUG_COMMON_TRIGGERS

void __dl_activateLogging(DeviceWrapper dev, int val) {
    if (dev.unwrappedDevice.is(device.unwrappedDevice)) {
        dev.removeSetting(@NAMEOF(debugLogEnable))
        dev.removeSetting(@NAMEOF(infoLogEnable))
        dev.updateSetting(@NAMEOF(debugLogEnable), [type: "enum", value: "${val}"])
        dev.updateSetting(@NAMEOF(infoLogEnable), [type: "enum", value: "${val}"])
        if (val > 1) {
            runIn(val*60, __dl_debugLogsOff)
            runIn(val*60, __dl_infoLogsOff)
        } else {
            unschedule(__dl_debugLogsOff)
            unschedule(__dl_infoLogsOff)
        }
    } else {
        try {
            dev.__dl_activateLogging(dev, val)
        } catch (e) {
            error e
        }
    }
}
// #endif
// #if @defined(ENABLE_DISTCLEAN_SUPPORT) || @defined(ENABLE_DEBUG_COMMON_TRIGGERS)

void __dl_killPrefs() { settings.keySet().each { device.removeSetting(it) } }
void __dl_killState() { state.clear() }

@Field static final List<String> __dl_preserveDataValue = [
    "hardwareVersion",
    "inClusters",
    "manufacturer",
    "model",
    "outClusters",
    "serialNumber",
    "uniqueId",
].asImmutable()

void __dl_killData() {
    Set<String> keys = device.getData().keySet().minus(__dl_preserveDataValue)
    keys.each { device.removeDataValue(it) }
}
// #endif
// #ifdef ENABLE_DEBUG_TRIGGER_SUPPORT

boolean __dl_debugTrigger(String arg) {
    switch (arg) {
        default:
            return false
//  #ifdef ENABLE_DEBUG_COMMON_TRIGGERS

        case __dl_tr_DUMPATTRS:
            device.getSupportedAttributes().each { a -> debug "Attribute ${a.name} (${a.dataType}) = ${device.currentValue(a.name, true)}" }
            break

        case __dl_tr_DUMPDDATA:
            Map data = device.getData()
            debug "device data keys: ${data.keySet().join(", ")}"
            data.each {
                debug "-> ${it.key} // device.getDataValue('${it.key}') = '${it.value}'${it.key in __dl_preserveDataValue ? ' (preserved)' : ''}"
            }
            break

        case __dl_tr_DUMPPREFS:
            L1:{
                def keys = settings.keySet()
                debug "named prefs: ${keys.join(", ")}"
                keys.each {
                    def v = device.getSetting(it)
                    debug "-> ${it} // device.getSetting('${it}') = ${v} [${getObjectClassName(v)}]"
                }
            }
            break

        case __dl_tr_KILLDDATA:
            __dl_killData()
            debug "${arg} done!"
            break

        case __dl_tr_KILLPREFS:
            __dl_killPrefs()
            debug "${arg} done!"
            break

        case __dl_tr_KILLSTATE:
            __dl_killState()
            debug "${arg} done!"
            break

        case __dl_tr_LOGGINGON:
            __dl_activateLogging(device, @L_LOG_TIMEOUT_VAL)
            log.debug "${arg} done!"
            break

        case __dl_tr_TREELOGGING:
            childDevices.plus(device).each { DeviceWrapper dev -> __dl_activateLogging(dev, @L_LOG_TIMEOUT_VAL) }
            log.debug "${arg} done!"
            break
//  #endif
//  #ifdef ENABLE_DEBUG_MODESELECT

        case __dl_tr_DUMPMSDS:
            debug "${@APICALL(modeSelect.debug.getDeviceState())}"
            break

        case __dl_tr_VERIDMSDS:
            debug "${@APICALL(modeSelect.debug.getVerId())}"
            break

        case __dl_tr_NULLMSDS:
            @APICALL(modeSelect.debug.deactivateSpec())
            debug "${arg} done!"
            break
//  #endif
//  #ifdef ENABLE_DEBUG_HEOBJS

        case __dl_tr_DRIVEROBJ:
            dl_dumpObject("driver", this)
            break

        case __dl_tr_DEVICEOBJ:
            dl_dumpObject("device", device)
            break

        case __dl_tr_FILELIST:
            debug getHubFiles()
            break

        case __dl_tr_MATTEROBJ:
            dl_dumpObject("matter", matter)
            break
//  #endif
//  #ifdef ENABLE_BLACK_BOX

        case __dl_tr_SAVEBLACKBOX:
            if (bb_recordCount()) bb_saveEntries()
            else warn "No black box records to save!"
            break
//  #endif
    }
    return true
}
// #endif

void __dl_debugLogsOff() {
    warnAlways "Disabling debug logging after timeout"
    device.updateSetting(@NAMEOF(debugLogEnable), [type: "enum", value: "0"])
}

void __dl_infoLogsOff() {
    warnAlways "Disabling info logging after timeout"
    device.updateSetting(@NAMEOF(infoLogEnable), [type: "enum", value: "0"])
}
// #ifdef L_ENABLE_SAVE_TO_FILE

static Map __dl_orderMapByKey(Map m) {
    m?.entrySet()
        ?.collect { if (it.value instanceof Map) it.setValue(__dl_orderMapByKey(it.value)); it } // Recursive map key sort
        ?.sort { Map.Entry a, Map.Entry b -> a.key <=> b.key }
        ?.collectEntries()
}
// #endif
// #ifdef ENABLE_FOLDER_SUPPORT

// Support File Manager+ folder creation and path saving
String __dl_prependFolder(String folderName, String fileName) {
    Closure fmplusName = { String fn -> fn?.replace(' ', '.') }
    String prefix = 'folder_' + fmplusName(folderName) + '_'
    String markerFileName = prefix + 'folder.marker'
    if (null == getHubFiles()?.find { it.name == markerFileName }) {
        uploadHubFile(markerFileName, "Folder created: ${new Date()}\nHint: Install File Manager+ via Hubitat Package Manager".getBytes("UTF-8"))
    }
    return (prefix + fileName)
}
// #endif
// #ifdef ENABLE_DEBUG_COMMON_TRIGGERS

String __dl_formatMarker(String markType = null, String markName = null) {
    StringBuilder sb = new StringBuilder()
// #ifndef NO_LOG_STYLING
    sb << "<b>"
// #endif
    sb << "*** "
    if (markType) sb << markType.toUpperCase() + " "
    sb << "MARKER"
    if (markName) {
        sb << ": "
// #ifndef NO_LOG_STYLING
        sb << "<span style='color: dodgerblue'>"
// #endif
        sb << markName
// #ifndef NO_LOG_STYLING
        sb << "</span>"
// #endif
    }
// #ifndef NO_LOG_STYLING
    sb << "</b>"
// #endif
// #ifdef ENABLE_BLACK_BOX
    List<String> bbEntryParts = markType ? [markType.toUpperCase()] : []
    if (markName) bbEntryParts.add(markName)
    bb_recordEntry(bbEntryParts.join(' : ') ?: '***', 'marker')
// #endif
    sb.toString()
}
// #endif
