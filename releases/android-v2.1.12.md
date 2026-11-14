# Arcane Vault Android v2.1.12 — automatic first USB sync and shared baseline

## Added
- First USB pairing mode launched from Windows without needing a previously exported common baseline.
- Automatic two-phase peer handshake: compare records, apply compatible additions or selected authority, verify equality and save a matching checkpoint on Android and Windows.
- Conflict review showing the Windows-selected source, with explicit Android approval before incompatible records are replaced.
- Automatic Android backup before first-pair write-back, plus built-in verification of inventory, deck and preferred artwork changes.

## Changed
- Nonconflicting first-sync additions are combined without a separate device-authority decision.
- The first USB sync now creates matching baselines automatically after both sides verify the synchronized records; later syncs use ordinary differential merging.

## Fixed
- Newly paired USB devices no longer need a manually located or previously shared baseline JSON file before syncing.
- Windows-authoritative pairing and Android-authoritative pairing both have a verified acknowledgment stage.

## Removed
- No prior snapshots, recovery paths, or manual sync modes removed.

## Safety
- Conflicting edits and quantities are never silently resolved; Windows and Android both require explicit approval of which source to retain.
- Native inventory commits retain the inventory journal and a complete pre-sync snapshot; Windows native replacement retains rollback files.
- Checkpoints are only committed after the two devices compare equal. Physical device validation is still needed.
