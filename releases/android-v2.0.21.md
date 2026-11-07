# Arcane Vault Android v2.0.21 — Safe Physical Printing Recovery

## Added
- **Restore verified printing IDs only (keep quantities)** when importing a physical collection CSV.
- The same printing-recovery mode for Windows QR, USB, Wi-Fi and collection snapshot imports.
- Printed inventory summary shows the number of rows with printing identifiers.
- A dedicated **Undo last printing ID recovery** action with a single, intentional rollback snapshot.
- Regression tests covering matching quantities, mismatched quantities, multiple printings, ambiguous records and unchanged owned totals.

## Changed
- Only card names that currently have one unidentified inventory row, an exact matching quantity in the verified source, and complete printing identifiers are eligible for recovery.
- Ambiguous, quantity-mismatched or already-identified cards are left untouched.
- Android now distinguishes different Scryfall printing IDs when normalizing owned printings, while retaining compatibility with existing price-cache keys.
- Set Progress explains how to restore identifiers without doubling the physical inventory.

## Fixed
- Physical Set Progress could remain at zero even after a successful Scryfall printing-catalog sync because the stored physical inventory had card names and quantities but no printing IDs or set + collector numbers.
- The old CSV options offered only Replace or Merge (which adds quantities); there was no safe metadata-only recovery path.

## Removed
- No existing features removed.

## Data safety
- **No automatic set guesses based on card names.** Source data must explicitly identify printings. The user must confirm the source represents the same physical collection.
- The repair does not change physical card quantities or Arena ownership. If a source record is ambiguous or quantities mismatch, it is skipped.
- Native storage must confirm the new physical inventory write; a local rollback copy is saved before any change.
- Keep the Android app installed and retain app data. Export a printing-aware collection CSV or Windows sync snapshot as the recovery source.
