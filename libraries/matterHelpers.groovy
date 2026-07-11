// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** MATTER HELPERS LIBRARY (symbol prefix: mh)
****************************************************************************************************************************/
// #else
/**
 *  Matter Helpers Library (symbol prefix: mh)
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
    name: "matterHelpers",
    description: "Matter Command Helpers",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(blackBox)
// @AT(INCLIB)(debugAndLogging)
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// Matter Data Value (mdv)
// --

@Field static final Closure<Map> __mh_asUint8 = { [dataType: DataType.UINT8, hexValue: HexUtils.integerToHexString((it as Integer) & 0xFF, 1)] }
@Field static final Closure<Map> __mh_asUint16 = { [dataType: DataType.UINT16, hexValue: HexUtils.integerToHexString((it as Integer) & 0xFFFF, 2), isBigEndian: true] }
@Field static final Closure<Map> __mh_asUint8x = { (it == null) ? [dataType: DataType.NULL] : __mh_asUint8(it) }
@Field static final Closure<Map> __mh_asUint16x = { (it == null) ? [dataType: DataType.NULL] : __mh_asUint16(it) }

// Matter Data Value
@Field static final Map mdv = [
    enum8: this.&__mh_asUint8,
    map8: this.&__mh_asUint8,
    uint8: this.&__mh_asUint8,
    uint8x: this.&__mh_asUint8x,
    uint16: this.&__mh_asUint16,
    uint16x: this.&__mh_asUint16x,
    optionsOverride: { it, ovr = null -> def om = __mh_asUint8(it); def oo = (ovr == null) ? om : __mh_asUint8(ovr); [om, oo].asImmutable() },
    ExecuteIfOff: [[dataType: DataType.UINT8, hexValue: "01"], [dataType: DataType.UINT8, hexValue: "01"]].asImmutable(),   // OptionsBitmap.ExecuteIfOff
    NoOptionsOverride: [[dataType: DataType.UINT8, hexValue: "00"], [dataType: DataType.UINT8, hexValue: "00"]].asImmutable(),
    Null: [dataType: DataType.NULL].asImmutable(),
    False: [dataType: DataType.BOOLEAN_FALSE].asImmutable(),
    True: [dataType: DataType.BOOLEAN_TRUE].asImmutable(),
    ZeroU8: [dataType: DataType.UINT8, hexValue: "00"].asImmutable(),
    ZeroU16: [dataType: DataType.UINT16, hexValue: "0000"].asImmutable(),
]

// -------------------------------------------------------------------------------------------------------------------------
// Matter command construction helpers
// --

String matterInvoke(Integer ep, Integer cluster, Integer command, Map... args) {
    List<String> fields = (args as List<Map>)?.withIndex().collect { Map it, int index ->
        List va = [it.dataType as Integer, index as Integer]
        if (it.hexValue instanceof String) va += (it.isBigEndian ? zigbee.swapOctets(it.hexValue) : it.hexValue)
        matter.cmdFieldStr(*va)
    }
    return matter.invoke(ep, cluster, command).replace("{1518}", "{15${fields.join("")}18}")
}

String matterWriteAttribute(Integer ep, Integer cluster, Integer attr, Map dataValue) {
    matter.writeAttributes([matter.attributeWriteRequest(ep, cluster, attr, dataValue.dataType, dataValue.hexValue ?: "")])
}

String matterReadAttribute(Integer ep, Integer cluster, Integer attr) {
    matter.readAttributes([matter.attributePath(HexUtils.integerToHexString(ep & 0xFFFF, 2), cluster, attr)])
}

boolean isMatterDevice() {
    try {
        return device?.getControllerType() == "MAT"
    } catch (ignored) {
        return false
    }
}

def sendMatterCommand(String cmd) {
// #ifeq @ENABLE_DEBUG_MATTER_SEND log
    debug cmd
// #elif @ENABLE_DEBUG_MATTER_SEND == blackbox
//  #ifndef ENABLE_BLACK_BOX
//  #error ENABLE_DEBUG_MATTER_SEND == blackbox, but ENABLE_BLACK_BOX is not set!
//  #endif
    bb_recordEntry([channel: "send", cmd: cmd])
// #endif
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.MATTER))
}

// -------------------------------------------------------------------------------------------------------------------------
// UI, data, preference, and Matter parameter conversion and interaction helpers
// --

static List<Integer> mh_intListLoad(DeviceWrapper clientDevice, String dataValue) {
    assert clientDevice != null
    assert dataValue

    clientDevice.getDataValue(dataValue)?.split(",")?.collect({ mh_safeParseInteger(it.trim()) })?.findAll({ it != null })
}

static void mh_intListSave(DeviceWrapper clientDevice, String dataValue, List<Integer> endPoints) {
    assert clientDevice != null
    assert dataValue
    assert endPoints

    clientDevice.removeDataValue(dataValue)
    String value = endPoints.findAll({ it != null })?.join(',')
    if (value?.isEmpty() == false) clientDevice.updateDataValue(dataValue, value)
}

static Long mh_safeParseLong(Object value) { try { value as Long } catch (NumberFormatException ignored) { null } }
static Integer mh_safeParseInteger(Object value) { try { value as Integer } catch (NumberFormatException ignored) { null } }

static int clamp(int val, int min, int max) { Math.max(Math.min(val, max), min) }
static BigDecimal clamp(BigDecimal val, BigDecimal min, BigDecimal max) { val.min(max).max(min) }

static Integer userOptSecsToTenths(Object val) { (val == null) ? val : new BigDecimal(val).abs().setScale(1, RoundingMode.HALF_UP).movePointRight(1).intValue() }
static int userPctToUint8(BigDecimal val) { clamp(val.abs(), BigDecimal.ZERO, 100).multiply(2.54).setScale(0, RoundingMode.HALF_UP).intValue() }
static int userNumberToPct(BigDecimal val) { clamp(val.abs(), BigDecimal.ZERO, 100).intValue() }
static int userNumberToPosInt(BigDecimal val) { val.abs().setScale(0, RoundingMode.HALF_UP).intValue() }
static int userKelvinToMireds(BigDecimal degreesK) { clamp(1_000_000.0G.divide(degreesK.abs(), 0, RoundingMode.HALF_UP).intValue(), 1, 65279) }
static int miredsToKelvin(int mireds) { mireds = (Math.abs(mireds) & 0xFFFF); mireds ? 1_000_000.0G.divide(mireds, 0, RoundingMode.HALF_UP).intValue() : 0 }
