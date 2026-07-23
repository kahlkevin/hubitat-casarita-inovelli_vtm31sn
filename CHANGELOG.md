# Changelog

All notable changes to this project are documented in this file.

## v1.1 (22 July 2026)

A focused follow-up to the initial release, shaped by early community feedback.

- **Power and energy reporting.** Disable reporting, report every change, or coalesce routine reports over a selected interval. Coalescing reduces event traffic during steady operation while keeping on/off changes and Refresh responsive.
- **Quieter logs.** Unchanged attribute values are no longer repeated in informational logs.

Coalescing applies to events published to Hubitat; it does not alter device reporting.

## v1.0 (12 July 2026)

- Initial stable release.
