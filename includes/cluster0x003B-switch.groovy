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
// #ifndef __CLUSTER3B_COMMON
// #define __CLUSTER3B_COMMON
/***************************************************************************************************************************
** BUTTON CAPABILITY IMPLEMENTATION (AND CLUSTER 0x3B EVENT CONSTANTS)
****************************************************************************************************************************/

@Field static final String EVT_BTNDTAP = @NAMEOF(doubleTapped)
@Field static final String EVT_BTNHELD = @NAMEOF(held)
@Field static final String EVT_BTNNUM  = @NAMEOF(numberOfButtons)
@Field static final String EVT_BTNPUSH = @NAMEOF(pushed)
@Field static final String EVT_BTNREL  = @NAMEOF(released)

// #endif
// #ifdef CAPABILITY
void doubleTap(button) { sendEvent(name: EVT_BTNDTAP, value: button, isStateChange: true, type: "digital") }
void hold(button) { sendEvent(name: EVT_BTNHELD, value: button, isStateChange: true, type: "digital") }
void push(button) { sendEvent(name: EVT_BTNPUSH, value: button, isStateChange: true, type: "digital") }
void release(button) { sendEvent(name: EVT_BTNREL, value: button, isStateChange: true, type: "digital") }
void c3b_installed() { sendEvent(name: EVT_BTNNUM, value: @BUTTON_EP_MAP.size()) }
void c3b_definition() {
    capability "DoubleTapableButton"
    capability "HoldableButton"
    capability "PushableButton"
    capability "ReleasableButton"
}

// #endif
