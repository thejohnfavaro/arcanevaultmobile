# Arcane Vault Android v2.1.51

## Added
- Regression coverage for a shared Arena card conflict (such as Andromedan Automatons) occurring alongside a Windows-only card (such as Artificial Anomaly).
- A test confirming unpaired records remain outside bulk authority selection while safe additions can still merge.

## Changed
- The Use Windows for all / Use Android for all controls choose among differing cards present on both devices.
- Cards that only exist on one device are handled separately by the guarded three-way merge rather than causing the conflict-choice dialog to fail.
- Explain that safe additions remain possible and ambiguous removals still require reviewed tombstone handling.
- Remove the misleading hard-coded 2.1.46 handler label from error dialogs.

## Fixed
- The bulk-choice helper previously threw `Arena removal/identity conflict requires manual review: artificial anomaly` while preparing the dialog for a separate `cards/andromedan automatons` conflict.
- A missing row no longer prevents the user from reviewing unrelated shared-card conflicts.

## Removed
- No data, card copies, deck edits, printing identities, preferred artwork, peer baselines or backups are removed. No silent deletion or automatic apply has been enabled.
