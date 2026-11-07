# Arcane Vault Android v2.0.7

## Added
- Last-resort recovery of Arena and physical card names and quantities from the existing local catalog's ownership index when earlier WebView data cannot be recovered.
- Verification of migrated preference values before removing redundant older browser storage.

## Changed
- Native app-private collection storage remains the one authoritative inventory copy.
- Older WebView origins are cleaned only when data was recovered and verified, preserving any conflicting snapshots rather than risking irreversible data loss.
- Deliberately emptied inventories are respected and never repopulated from stale backups.

## Fixed
- Strengthened the v2.0.6 recovery and cleanup workflow to avoid discarding unmatched older preferences.
- Improved collection recovery when earlier Android versions stored inventory under different randomly chosen localhost ports.

## Removed
- Unverified automatic deletion of older browser data; cleanup only removes confirmed duplicates.
