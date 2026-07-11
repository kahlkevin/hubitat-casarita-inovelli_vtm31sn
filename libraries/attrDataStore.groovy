// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** ATTRIBUTE DATA STORE LIBRARY (symbol prefix: ds)
****************************************************************************************************************************/
// #else
/**
 *  Attribute Data Store Library (symbol prefix: ds)
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
    name: "attrDataStore",
    description: "Cache endpoint/cluster/attribute values and save to file (debug tool)",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// DS PUBLIC API
// --

void ds_clear() { __ds_getMap()?.clear() }

def ds_put(int endpointInt, int clusterInt, int attrInt, def valueToStore) {
    Map clMap = __ds_ensureMap(endpointInt, clusterInt)
    if (valueToStore == null) return clMap.remove(attrInt)
    else return clMap.put(attrInt, valueToStore)
}

// #ifdef ATTRDATASTORE_GETTER
def ds_get(int endpointInt, int clusterInt, int attrInt) { return __ds_getMap()?.get(endpointInt)?.get(clusterInt)?.get(attrInt); }
// #endif

void ds_save(String fileNamePrefix = "ep_cluster_attr_cache", String msgPrefix = "Attribute cache") {
    try {
        String fileName = dl_saveToFile(fileNamePrefix, __ds_getMap())
        infoAlways "${msgPrefix} saved to '${fileName}'"
    } catch (AssertionError | Exception e) {
        error e
    }
}

// -------------------------------------------------------------------------------------------------------------------------
// DS PRIVATE API
// --

@Field static ConcurrentHashMap __ds_data = new ConcurrentHashMap(16, 0.75, 1)

Map __ds_getMap() { return __ds_data.get(device?.getDeviceNetworkId()) }
Map __ds_ensureMap(int endpointInt, int clusterInt) {
    Map evMap = __ds_data.computeIfAbsent(device.getDeviceNetworkId(), { new ConcurrentHashMap<String,ConcurrentHashMap>(8, 0.75, 1) })
    Map epMap = evMap.computeIfAbsent(endpointInt, { new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1) })
    return epMap.computeIfAbsent(clusterInt, { new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1) })
}
