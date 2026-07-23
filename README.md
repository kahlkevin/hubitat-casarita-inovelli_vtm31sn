# Inovelli White Series Dimmer VTM31-SN Driver

This is a Hubitat driver bundle for Inovelli White Series Matter devices, centered on the VTM31-SN dimmer and notification bar support.

The project keeps source drivers, libraries, includes, and install/update assets separate, then uses [GPP](https://files.nothingisreal.com/software/gpp/gpp.html) and [Ninja](https://ninja-build.org/manual.html#_introduction) to generate Hubitat-ready Groovy files. Development builds are staged as modular driver/library files; pre-release and production builds are staged as monolithic driver files for easier installation and a minimal on-hub footprint.

See [the Hubitat Community Thread](https://community.hubitat.com/t/release-inovelli-white-series-vtm31-sn-matter-community-driver/165102) for additional information.

See [CHANGELOG.md](CHANGELOG.md) for release notes and upgrade details.

## Supported Devices

- Inovelli Dimmer White Series VTM31-SN
- Inovelli notification bar child device created by the VTM31-SN driver (automatically)

## Highlighted Features

 - Full switch and dimmer control for the primary load
 - Independent control of the built-in RGB notification bar through an automatically managed child device
 - On/off, level, color, and color-temperature control for the notification bar
 - Access to the device’s built-in notification and LED effects
 - Button and multi-tap event support
 - Configurable power and energy monitoring
 - Comprehensive access to the VTM31-SN’s Matter configuration settings
 - Device preferences organized into functional groups so related settings are easy to find and configure together
 - Automatic synchronization with the device’s existing settings and current state
 - Fast, efficient initialization and recovery with automatically optimized Matter subscriptions
 - Automatic driver selection and setup when commissioning a supported device to Hubitat

## Repository Layout

- `drivers/` - Hubitat driver source files.
- `libraries/` - Hubitat library source files shared by the drivers.
- `includes/` - GPP include fragments and macros used during preprocessing.
- `assets/` - Build-time templates for BOM, install, and update metadata.
- `build/ninja/` - Ninja build definitions for `dev`, `pre`, and `prod` flavors.
- `build/genstages/` - Python helpers that generate staged Ninja input files and version metadata.
- `build/out/` - Ignored generated build output.
- `dist/` - Ignored ZIP packages produced by `make.cmd`.

## Build Requirements

The current build workflow is Windows-first and calls into WSL for Ninja/GPP execution.

- Windows command prompt or PowerShell
- WSL with `ninja` and `gpp` available on `PATH` (ex: install using `sudo apt-get install -y gpp ninja-build` in Ubuntu/Debian)
- `python3` available from Windows for build metadata generation
- 7-Zip installed at `C:\Program Files\7-Zip\7z.exe` for package creation

## Building

From the repository root:

```cmd
make.cmd
```

By default this builds the `dev` flavor. You can choose a flavor explicitly:

```cmd
make.cmd dev
make.cmd pre
make.cmd prod
```

To clean a flavor:

```cmd
make.cmd clean dev
make.cmd clean pre
make.cmd clean prod
```

Successful package builds create ZIP files under `dist/`, using the generated `bom.txt` for each flavor.

## Build Flavors

- `dev` uses namespace `casarita-dev` and stages modular driver/library files.
- `pre` uses namespace `casarita-pre` and stages monolithic driver files.
- `prod` uses namespace `casarita` and stages monolithic driver files.

The staged files are written under `build/out/<flavor>/`.

Only the `prod` flavor provides fingerprints that will automatically associate the driver with a newly-commissioned device.
This permits safe side-by-side installation of all flavors on a single hub without risk of confusion/cross-contamination during automatic driver installation.

## VS Code Helpers

The workspace includes tasks for common local workflows:

- `Stage This File and Copy Contents to Clipboard` preprocesses the active `.groovy` source into the `dev` staging directory and copies the staged output.
- `Build Bundle Package` runs `make.cmd`.
- `Stage Files` runs the development Ninja build from `build/ninja/`.

## Installation

There are two recommended installation paths:

 1. Install via Hubitat Package Manager
    - Package name: `Inovelli Dimmer White Series VTM31-SN by Kevin Kahl`
    - Search by keyword or find by tag under `Matter`, `Lights & Switches`, or `Energy Monitoring`
 2. Use the generated bundle package for the flavor you want from `dist/`:
    - Open the Bundles tab (enable Show advanced/developer options in the Settings page if that tab isn't visible).
    - Click `Import .ZIP`, then `+ Choose` to select your preferred bundle package.
    - Finalize installation using the red `Import` button.

For production use, prefer the `prod` package. Development and pre-release flavors intentionally use distinct namespaces and/or name prefixes so they can coexist with production installs while testing.
Those flavors will not be auto-detected when adding a new VTM31-SN dimmer to your Hubitat.

## Known Issues

- Button events (including double-tap, multi-tap) may not be delivered promptly. This is due to a Hubitat platform limitation that we hope is addressed soon. See [this discussion](https://community.hubitat.com/t/matter-event-subscriptions-expose-eventpathib-isurgent-for-low-latency-generic-switch-button-events/164389) in the Hubitat Community forums for up-to-date information.

## Licensing

Except where noted in-file, this repository is licensed under the Apache License, Version 2.0. See `LICENSE`.

Source files generally carry SPDX and Apache-2.0 notices.
