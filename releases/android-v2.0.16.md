# Arcane Vault Android v2.0.16 — Physical Set Completion Repair

## Added
- Recovery of previously indexed Scryfall printing identities (where still available) when the durable physical inventory must be reconstructed.

## Changed
- Physical Set Progress now opens with **All Magic printings**, not the Arena-only catalog subset.
- Switching between Arena and Physical also changes the catalog scope to the appropriate default.

## Fixed
- Paper-only owned printings are no longer silently excluded by the Arena-listed catalog filter.
- Aggregate-only recovery no longer destroys a matching, previously indexed printing mirror. A changed inventory still replaces stale identities instead of claiming inaccurate progress.

## Removed
- No features removed.

## Data safety
- No destructive migration; native collection storage remains authoritative.
- Previously lost printing attributes cannot be inferred from card names alone. Import a verified backup with IDs or set/collector numbers if the app reports zero identified printings.
- This release does not assign a card to a set merely because the card name appears in that set.
