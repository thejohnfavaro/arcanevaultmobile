# Arcane Vault Android v2.0.32 — Native printing inspection

## Added
- Browse official Scryfall printings directly from the native Card Catalog inspector.
- View confirmed physical quantities per exact printing, original set and collector number, and official printing artwork.
- Show unresolved physical card copies separately rather than guessing an edition from a matching card name.
- Regression tests for foil/nonfoil totals, contradictory Scryfall IDs, unknown IDs, ambiguous collector matches, and read-only input preservation.

## Changed
- Native card details offer a new printing selector with results loaded in bounded 40-printing batches.
- Quantity reconciliation reads the existing authoritative native physical inventory, rather than inferred Arena counts or stale catalog mirrors.

## Fixed
- Name-only ownership can no longer be mistaken for ownership of a particular printed edition within the native picker.

## Removed
- Nothing. Legacy tools remain available for editing printings and other features not yet migrated.

## Safety
- Read-only feature. No updates to quantities, preferred artwork, backups, saved decks or official Scryfall data.
- A missing identity is shown as unresolved. Importing or correcting a physical collection is still performed through existing guarded Legacy tools.
