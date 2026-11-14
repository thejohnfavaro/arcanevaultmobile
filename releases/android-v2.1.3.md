# Arcane Vault Android v2.1.3 — Set Progress filters and Wi-Fi QR scanning

## Added
- **Physical printings / Arena card names** scope selection in native Set Progress, matching the Windows distinction between verified physical editions and Arena name-level ownership.
- **Hide sets with no progress**, enabled by default, with a persistent toggle.
- Sorting by **newest release, oldest release, set name A–Z, or most complete**. Sets without official release dates stay last in release-date sorts.
- **Hide unowned printings / Hide unowned Arena cards** in the per-set checklist.
- **Scan Windows Wi-Fi QR code** in native Data & Sync, using the existing in-app Google Code Scanner. After scanning, choose the matching send/receive direction and confirm the link.

## Changed
- Set scope, hide-empty preference, set sorting and owned-only checklist setting persist across visits.
- Search and pagination respect current filters; stale set search results cannot overwrite a newer request.
- QR-scanned links undergo the same strict private-network, one-use token validation as manually entered links.

## Fixed
- Set Progress no longer defaults to a long alphabetical list of mostly empty sets without sorting controls.
- Arena card-name coverage is no longer mislabeled as physical printings in set detail.
- Copying or typing long Wi-Fi peer links is no longer necessary when Windows displays a QR code.

## Removed
- No existing tools or filters removed. Manual wireless URL entry remains an option.

## Safety
- Set Progress remains read-only. Peer Wi-Fi exchange remains **snapshot transfer and read-only comparison**, not automatic merge.
- QR links expire after five minutes and are secrets; use trusted home Wi-Fi only, as the current LAN transport uses unencrypted HTTP.
- Play Protect report #36 remains unresolved and is not cleared by passing release signing checks.
