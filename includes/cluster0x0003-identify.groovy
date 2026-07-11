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
// #ifndef __CLUSTER03_COMMON
// #define __CLUSTER03_COMMON
/***************************************************************************************************************************
** IDENTIFY CAPABILITY IMPLEMENTATION (USING CLUSTER 0x03)
****************************************************************************************************************************/

@Field static final String EVT_IDTIME = @NAMEOF(identifyTime)
@Field static final String EVT_IDTYPE = @NAMEOF(identifyType)

// #endif
// #ifdef API
//  #if 0
//// Dependencies:
//// @INCLIB(debugAndLogging)
//// @INCLIB(matterHelpers)
//  #endif
void identify(Integer ep, Integer timeInSeconds) {
    try {
        // Identify Command (Matter Application Cluster Specification R1.4 § 1.2.6.1)
        String cmd = matterInvoke(ep, 0x0003, 0x00, mdv.uint16(timeInSeconds))
        sendMatterCommand(cmd)
    } catch (AssertionError | Exception e) {
        error e
    }
}

// #endif
// #ifdef CAPABILITY
//  #if !@defined(ENDPOINT)
//      #error ENDPOINT must be defined at build time
//  #endif
//  #ifdef CHILD_DRIVER
//      #define PARENT parent.#1
//  #else
//      #define PARENT #1
//  #endif
void identify(timeInSeconds = null) { @PARENT(identify(@ENDPOINT, (timeInSeconds ?: 10) as Integer)) }

//  #undef PARENT
// #endif
