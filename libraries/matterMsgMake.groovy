// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** MATTER MESSAGE MAKER LIBRARY (symbol prefix: mm)
****************************************************************************************************************************/
// #else
/**
 *  Matter Message Maker Library (symbol prefix: mm)
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
    name: "matterMsgMake",
    description: "Produce Matter message structures from incoming description strings from protocol driver",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(blackBox)
// @AT(INCLIB)(debugAndLogging)
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// MM PUBLIC API
// --

@Field static final Map mm_matterMsgType = [
    UNKNOWN: 0,
    ATTR_REPORT: 1,
    EVENT: 2,
    SUBSCRIPTION_RESULT: 3,
]

Map mm_matterMsgFrom(String description) {
    def resultType = mm_matterMsgType.UNKNOWN
    Map result
    try {
        if (description?.startsWith("read attr -")) {
            resultType = mm_matterMsgType.ATTR_REPORT
        } else if (description?.startsWith("event -")) {
            resultType = mm_matterMsgType.EVENT
        } else if (description?.startsWith("subscriptionResult -")) {
            resultType = mm_matterMsgType.SUBSCRIPTION_RESULT
        } else {
            warn "Unknown description prefix: '${description}'"
        }

        result =
            [type: resultType] + description
                .substring(description.indexOf("-") + 1)
                .split(",")
                .collectEntries {
                    List<String> kvp = it.split(":").collect { it.trim() }
                    String key = kvp.first()
                    String val = kvp.last()
                    __mm_valueParsers[key]?.call(val) ?: [(key): val]
                }

        return result
    } finally {
// #ifdef ENABLE_BLACK_BOX
//  #ifdef INCLUDE_HUBITAT_PARSE_OUTPUT
        bb_recordEntry { [channel: "parse", description: description, custom: result, he: { try { matter.parseDescriptionAsMap(description) } catch(e) { e.toString() } }()] }
//  #else
        bb_recordEntry { [channel: "parse", description: description, custom: result] }
//  #endif
// #endif
    }
}

// -------------------------------------------------------------------------------------------------------------------------
// MM PRIVATE API
// --

@Field static final Map<String, Closure<Map>> __mm_valueParsers = [
    attrId: { [attrInt: Integer.parseInt(it, 16)] },
    cluster: { [clusterInt: Integer.parseInt(it, 16)] },
    endpoint: { [endpointInt: Integer.parseInt(it, 16)] },
    evtId: { [evtInt: Integer.parseInt(it, 16)] },
    eventSerial: { [eventSerialInt: new BigInteger(it, 16)] },
// #ifdef ENABLE_PARSER_TYPE_RETURN
    value: { v = parse_tlv(it); [decodedType: v.type, decodedValue: v.value] },
// #else
    value: { [decodedValue: parse_tlv(it)] },
// #endif
].asImmutable()
