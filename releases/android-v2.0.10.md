# Arcane Vault Android v2.0.10

## Added
- **Fetch All Owned Card Details** in Import & Data for Arena and physical cards, including optional refresh of existing metadata, checkpointing and resumable progress.
- Verified first-face fallback for split, adventure and other multi-face Scryfall lookups.

## Changed
- Set Progress uses the actual physical printing identified by Scryfall ID or exact set, collector number and name, rather than treating name matches as ownership across all sets.
- Name-only Arena imports are not assigned to particular printings, avoiding false progress on unreleased sets.
- Physical printing ownership is indexed in a new SQLite table with non-destructive schema migration.

## Fixed
- `Card Name // Other` sometimes failed individual or bulk metadata lookup.
- Set completion incorrectly counted cards owned from other printings.

## Removed
- Set-level ownership inferred from the card name alone.
