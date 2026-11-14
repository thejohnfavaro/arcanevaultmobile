# Arcane Vault Android v2.1.7 — reviewed incremental inventory sync

## Added
- A per-card and per-printing difference review for applying Windows inventory changes to Android, including before/after quantities, copy totals and distinct physical printings.
- Matching remembered Windows baseline offer before requiring a manual baseline file selection.
- Regression tests covering a single extra copy, repeat sync no-op and distinct printings.

## Changed
- The Android Data & Sync action is now called **Sync Windows inventory differences** to reflect reviewed inventory write-back rather than read-only preview.

## Fixed
- Inventory apply review now describes exact quantity changes relative to Android's current collection rather than only counting modified record objects.

## Removed
- No existing features removed.

## Safety
- Requires an actual common baseline and explicit approval before writing; a local snapshot alone is not proof of matching state.
- Existing crash-recovery journal, optimistic inventory checks, and conflict/deletion refusal remain.
- Android deck and artwork write-back is not yet supported; this release improves only physical and Arena inventory write-back.
