# Arcane Vault Android v2.1.43

## Added
- Multi-card owned-substitution planning inside the existing native Commander/Brawl deck editor.
- Reviewable one-for-one swap previews with unresolved shortages and explicit Apply to unsaved draft.
- Regression tests for preserved deck quantities and stale source rejection.

## Changed
- Only physical or Arena owned copies from the corresponding format pool are considered; explicitly flagged proxies are excluded.
- Candidates must pass synced local format legality, commander color identity and copy-limit checks while preserving all recognized source roles.
- Plans are recomputed before applying to the unsaved draft; final Review & Save remains separate.
- Original card-name spelling is retained for unchanged entries.

## Fixed
- Batch proposals reserve inventory copies instead of assigning one copy to multiple missing slots.
- Android card-name casing no longer gets normalized when applying suggested substitutions.

## Removed
- None. Manual deck editing remains available.
