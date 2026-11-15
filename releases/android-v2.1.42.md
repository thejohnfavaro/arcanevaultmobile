# Arcane Vault Android v2.1.42

## Added
- Owned-card batch substitution planning in the native Decks editor for Commander and Brawl.
- Read-only per-swap explanations, unresolved shortages and an explicit Apply to unsaved draft action.
- Unit coverage for quantity-preserving changes and stale source rejection.

## Changed
- Plans use only the selected physical or Arena ownership pool and locally recorded rules metadata.
- Candidate cards must preserve all recognized card roles, remain in commander color identity, be legal in the chosen format, obey recorded copy limits, and avoid substantially increasing mana value.
- Plans are recalculated before application; saving remains a separate Review & Save action.

## Fixed
- The same recorded inventory copy cannot be assigned to multiple substitution slots.

## Removed
- None. Existing deck text editor, manual card search, and backup-protected save remain available.
