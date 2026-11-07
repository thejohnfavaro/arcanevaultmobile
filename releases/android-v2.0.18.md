# Arcane Vault Android v2.0.18 — Unified Rarity Badges & Owned Card Details

## Added
- Confirmation of metadata storage commits and clearer batch-level fetch progress.

## Fixed
- Normalize card rarity pills to one height, width, type size and alignment throughout the app.
- Allow card metadata to wrap without stretched or clipped rarity pills.
- Fetch missing details now respects the existing Retry previously unmatched names option.
- Cards with legitimate empty Oracle text no longer trigger perpetual unnecessary fetches.
- Refreshed owned card metadata is committed to IndexedDB before a batch is marked saved, with explicit storage errors and per-batch progress.

## Changed
- Collection fetch progress and failure messages clearly distinguish batches saved, names unmatched and storage problems.
- Existing card ownership and catalog filters are untouched.

## Removed
- No existing features removed.

## Data safety
- No collection reset, database migration, or destructive storage operation.
- Metadata updates do not modify quantities or printings.
