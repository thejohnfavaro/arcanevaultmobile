# Arcane Vault Android v2.1.4 — Safe inventory peer write-back

## Added
- Native **Review & apply peer inventory** action in Data & Sync. Select the latest Windows peer snapshot and a previously shared common baseline, review non-conflicting changes, and explicitly confirm.
- Three-way candidate builder distinguishes incoming-only updates, Android-only edits and simultaneous modifications.
- Crash-recovery journal stages an entire original physical/Arena inventory snapshot before any commit. Native durable inventories and SQLite ownership mirrors are updated together, with restore-on-error and automatic recovery after interruption.
- Tests for record identity, change classification, conflict/deletion rejection, field order and recovery integration.

## Changed
- Physical printing and Arena quantities can be applied to Android after review, while existing Android-only changes are preserved.
- Provenance/schema metadata is excluded from user conflict classification; real unknown workspace-field conflicts still block the update.

## Fixed
- Missing peer records are never inferred to be deletions without explicit tombstones.
- Updated inventory quantities are guarded against changes made after the initial preview.

## Removed
- No existing import, QR, or preview capabilities removed.

## Safety
- **Android write-back currently applies physical/Arena inventory only.** Incoming saved-deck or preferred-artwork edits are refused, because their legacy WebView storage requires coordinated crash recovery. Those can be applied on Windows instead.
- This is an explicit user-reviewed, manually baselined stage, not automatic full synchronization or authenticated pairing. Android records are protected by recovery snapshots; never clear application data during recovery.
- Peer transfer over local Wi-Fi currently uses unencrypted HTTP. Use only trusted home networks and keep QR token private.
- Play Protect investigation #36 remains open; successful APK signing is not malware clearance.
