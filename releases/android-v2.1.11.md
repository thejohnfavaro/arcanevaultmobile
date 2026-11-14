# Arcane Vault Android v2.1.11 — automatic USB peer baseline discovery

## Added
- Android now locates its own saved sync snapshots automatically when a Windows peer snapshot is received over USB or Wi-Fi.
- If a previously confirmed common peer checkpoint exists, Android still offers it first. Otherwise it suggests the latest local sync snapshot and lets you choose an earlier one from an in-app list.
- Manual JSON file browsing remains available for baselines saved outside Arcane Vault's private storage.

## Changed
- The baseline selection step no longer requires navigating deeply into Android internal storage for Arcane Vault-created snapshots.
- Saved snapshots are presented in date order with their exact filenames. Users confirm which snapshot represents the last genuinely shared Windows/Android state before applying a three-way merge.

## Fixed
- Arcane Vault internal sync snapshots are now accessible in the USB peer merge workflow even when the Android document picker cannot browse app-private directories.

## Removed
- No sync modes or saved snapshots removed.

## Safety
- A saved local snapshot is not automatically considered an authenticated common baseline. The user explicitly confirms the correct shared state.
- All existing three-way conflict checks, printing-aware inventory deltas, saved-deck/artwork writeback approval, and verified recovery snapshots remain intact.
- No data is changed during snapshot discovery or selection. Physical USB round-trip validation is still required.
