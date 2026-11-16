# Arcane Vault Android v2.1.50

## Added
- Explicit bulk Arena authority for every differing card record present in both Android and Windows snapshots.
- Unit test for Android-only Arena edits that must be replaced by Windows values when "Use Windows for all" is selected.
- Regression checks for bulk conflict scope, no-op diagnostics, and safe review.

## Changed
- "Use Windows for all Arena conflicts" now consistently applies the Windows values to all differing shared Arena records, even when the three-way baseline classifies an edit as Android-only.
- "Use Android for all Arena conflicts" keeps the Android version of those records.
- The review states how many shared Arena records differ; the usual per-card choice, full backup, and final apply approval remain.
- A merge proposal with no actual differences now explicitly reports that nothing was written rather than implying a completed sync.

## Fixed
- Choosing "Use Windows for all" could preserve Android-only changes, producing a no-op despite the explicit bulk authority choice.

## Removed
- None. No silent deletion of records missing on either peer; unrelated physical inventory, deck and artwork conflicts are not overridden.
