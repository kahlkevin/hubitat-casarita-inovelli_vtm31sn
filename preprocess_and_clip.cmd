::
:: SPDX-FileCopyrightText: 2026 Kevin Kahl
:: SPDX-License-Identifier: Apache-2.0
::
:: Copyright 2026 Kevin Kahl
::
:: Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
:: in compliance with the License. You may obtain a copy of the License at:
::
::     http://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
:: on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
:: for the specific language governing permissions and limitations under the License.
::

@echo off
setlocal

set FLAVOR=dev

if /i "%1" == "prod" (set FLAVOR=prod&& shift)

if /i not "%~x1"==".groovy" (
    echo Expected a .groovy file name, got "%~1" 1>&2
    echo Aborted
    exit /b 1
)

set BASE=%~dp0
set BUILDROOT=%BASE%build\
set BUILDDIR=%BUILDROOT%out\
set STAGEDIR=%BUILDDIR%%FLAVOR%\
cd /d %BUILDROOT%ninja

python3 %BUILDROOT%genstages\genversion.py --quiet %BASE%sources.toml %BUILDDIR%version.ninja
if ERRORLEVEL 1 (
    echo Failed to generate version.ninja
    exit /b %ERRORLEVEL%
)

wsl ninja %FLAVOR%

if ERRORLEVEL 1 (
    echo Aborted
    exit /b %ERRORLEVEL%
)

if NOT EXIST %STAGEDIR% (
    echo Expected staging dir to exist at %STAGEDIR%
    echo Aborted
    exit /b 1
)

set DRVPFX=final-driver-
set LIBPFX=final-lib-

set IN_NAME=%1

set OUT_LIB_NAME=%LIBPFX%%IN_NAME%
set OUT_DRV_NAME=%DRVPFX%%IN_NAME%
set OUT_NAME=

set OUT_LIB_PATH=%STAGEDIR%%OUT_LIB_NAME%
set OUT_DRV_PATH=%STAGEDIR%%OUT_DRV_NAME%
set OUT_PATH=

if EXIST "%OUT_LIB_PATH%" (
    set OUT_PATH=%OUT_LIB_PATH%
    set OUT_NAME=%OUT_LIB_NAME%
) else (
    if EXIST "%OUT_DRV_PATH%" (
        set OUT_PATH=%OUT_DRV_PATH%
        set OUT_NAME=%OUT_DRV_NAME%
    ) else (
        echo Unable to locate staged file. Tried:
        echo     %OUT_LIB_PATH%
        echo     %OUT_DRV_PATH%
        echo Aborted
        exit /b 1
    )
)

clip < %OUT_PATH%

echo.
echo --- Copied to the clipboard:
echo ---
echo ---   %OUT_NAME%
echo ---
echo.
echo Done!
