# Arcane Vault Android v2.0.14 — Set Progress Printing Match Repair

## Added
- Ownership matching diagnostics directly in Set Progress, distinguishing no physical cards, missing identifiers, and unmatched printings.

## Changed
- Match an owned physical printing by its Scryfall printing ID, without also requiring an exact card-name string.
- If no matching Scryfall ID exists in the catalog, match by exact set code or full set name plus collector number.

## Fixed
- Printings with variant card names or imported set titles are no longer incorrectly excluded from Set Progress solely due to display-name differences.
- Zero-progress diagnosis now identifies whether printing identifiers are missing or failing to match.

## Removed
- No working features removed.

## Data safety
- Existing physical and Arena inventory is preserved. No database schema change or destructive migration.
- Name-only ownership still cannot be assigned to a printing; no unrelated sets receive false progress.
