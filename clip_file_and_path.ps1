#
#  SPDX-FileCopyrightText: 2026 Kevin Kahl
#  SPDX-License-Identifier: Apache-2.0
#
#  Copyright 2026 Kevin Kahl
#
#  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
#  in compliance with the License. You may obtain a copy of the License at:
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
#  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
#  for the specific language governing permissions and limitations under the License.
#

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Path
)

Add-Type -AssemblyName System.Windows.Forms

try {
    $resolvedPath = (Resolve-Path $Path -ErrorAction Stop).Path
}
catch {
    Write-Error "File not found: $Path"
    exit 1
}

$files = New-Object System.Collections.Specialized.StringCollection
[void]$files.Add($resolvedPath)

$data = New-Object System.Windows.Forms.DataObject
$data.SetFileDropList($files)
$data.SetText($resolvedPath, [System.Windows.Forms.TextDataFormat]::UnicodeText)

# Explorer-style copy intent: DROPEFFECT_COPY = 1
$copyEffect = [byte[]]@(1,0,0,0)
$data.SetData("Preferred DropEffect", [System.IO.MemoryStream]::new($copyEffect))

[System.Windows.Forms.Clipboard]::SetDataObject($data, $true)

Write-Host "Copied to clipboard: $resolvedPath"
