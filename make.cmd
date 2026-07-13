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
set CLEAN=
set RELEASE=
:args
if /I "%1" == "distclean" (set CLEAN=dist&& shift && goto :args)
if /I "%1" == "clean" (set CLEAN=1&& shift && goto :args)
if /I "%1" == "dev" (set FLAVOR=%1&& shift && goto :args)
if /I "%1" == "pre" (set FLAVOR=%1&& shift && goto :args)
if /I "%1" == "prod" (set FLAVOR=%1&& shift && goto :args)
if /I "%1" == "release" (set RELEASE=--release&& shift && goto :args)
if /I "%1" NEQ "" (
    echo Unrecognized argument: '%1'
    echo Aborted
    exit /b 1
)

set BASE=%~dp0
set BUILDROOT=%BASE%build\
set BUILDDIR=%BUILDROOT%out\
set STAGEDIR=%BUILDDIR%%FLAVOR%\
set DISTDIR=%BASE%dist\
cd /d %BUILDROOT%ninja

:: FUTURE:
::  (a) Get gpp vendored in for Windows build so we can ditch wsl
::  (b) Ninja already available via winget
if "%CLEAN%" EQU "dist" (
    cd /d %BASE%
    rmdir /s /q %BUILDDIR%
    rmdir /s /q %DISTDIR%
    exit /b 0
) else if "%CLEAN%" NEQ "" (
    wsl ninja -f build-%FLAVOR%.ninja -t clean
    exit /b 0
) else (
    python3 %BUILDROOT%genstages\genversion.py --quiet %RELEASE% %BASE%sources.toml %BUILDDIR%version.ninja
    if ERRORLEVEL 1 (
        echo Failed to generate version.ninja
        exit /b %ERRORLEVEL%
    )
    wsl ninja %FLAVOR%
)

if ERRORLEVEL 1 (
    echo Aborted
    exit /b %ERRORLEVEL%
)

if NOT EXIST %STAGEDIR% (
    echo Expected staging dir to exist at %STAGEDIR%
    echo Aborted
    exit /b 1
)

set ZIP="C:\Program Files\7-Zip\7z.exe"
set ARCHIVE=%DISTDIR%inovelli_vtm31sn-%FLAVOR%.zip

cd /d %STAGEDIR%

%ZIP% a -tzip -up1q0r2x1y2z1w2 -spm %ARCHIVE% -i@bom.txt
powershell -STA -File %BASE%clip_file_and_path.ps1 %ARCHIVE%
