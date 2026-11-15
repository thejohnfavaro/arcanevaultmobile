# Arcane Vault Android v2.1.35 — Incremental Set Progress Cache

## Added
- Persistent Physical and Arena Set Progress overview snapshots, loaded instantly on subsequent visits with local sorting and filtering.
- Set-level dirty tracking that identifies which set's owned printings or Arena names changed and refreshes only the affected set progress.
- An "Updating [set] progress" message beside the affected set tile, preserving the last known completion while the scoped calculation runs.
- Offline cached official set names while the first complete local snapshot is prepared.

## Changed
- Identical inventory sync snapshots no longer rewrite ownership tables or recalculate set coverage.
- New and removed verified printings or changed quantities invalidate only matching set codes.
- Arena card-name changes update only sets containing the affected name.
- Set Progress widget deep links skip the all-sets overview; Back returns to cached results without waiting for global reconciliation.
- Local set detail counts use set-scoped index-friendly exact printing lookups.

## Fixed
- Set Progress could remain on Reading physical set progress while performing global ownership joins.
- Opening TRK from the launcher widget could stall behind the set overview indexer.
- Repeated unchanged sync messages unnecessarily forced expensive SQLite ownership rewrites.

## Removed
- No inventories, decks, printings, widget selections, or ownership data were deleted.

## Safety
- Set coverage is never inferred from unresolved card names; only verified printing identities count for Physical progress.
- Broad cache invalidation is retained for an official catalog sync because the cards or set definitions within existing sets can change.
- A large import can trigger a full snapshot refresh; the lightweight normal path updates only changed sets.
- The last valid cached value remains visible while a changed set is updating. An invalidated calculation cannot overwrite newer data.
