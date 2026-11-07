# Arcane Vault Android v2.0.13 — Set Progress Visibility Fix

## Added
- Actionable Set Progress empty-state guidance distinguishing missing printing identifiers from missing catalog data.
- Regression guards for physical printing defaults and empty states.

## Changed
- New Set Progress sessions default to Physical Collection, which supports confirmed printing progress. Existing saved collection-source preferences are retained.

## Fixed
- The Arena name-only printing limitation no longer presents as an unexplained blank grid.
- When Hide sets with no progress is enabled, the interface explains why no sets are shown and how to recover the view.

## Removed
- No features removed.

## Data safety
- No collection, deck, or ownership records are migrated, deleted, or reset.
- Arena collection quantities remain available. Set-specific Arena progress requires printing-identifiable import data and is not inferred from names alone.
