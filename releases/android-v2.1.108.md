# Arcane Vault Android v2.1.108

## Added
- Commander printing chooser in the native Deck Library editor.
- Locally indexed official Scryfall printings, with exact verified owned copies listed first.
- Dedicated commanderDisplayPrintId cosmetic preference saved independently from the mainboard and inventory.
- Deck covers render their selected commander printing, using printing-specific artwork cache keys.
- Front-face support for transforming commanders including Etali, Primal Conqueror.

## Changed
- Changing the commander clears an obsolete cosmetic choice until a new printing is selected.
- Printing selection remains an unsaved draft until Review & Save; Android's original recovery-backed deck save is unchanged.
- Deck revision snapshots retain the previous commander printing preference.
- Updated the deck-art regression checks for printing-scoped caches and draft artwork retries.

## Fixed
- Different saved decks using the same commander can now have different official card art.
- Missing printing artwork falls back to regular commander art without modifying saved ownership or printing metadata.

## Removed
- Nothing. No duplicate commander entry or collection change is created.

## Verification
- Open a saved Commander deck, choose a printing in the commander artwork control, and Review & Save.
- Reopen the deck and check the cover, preference persistence, and unchanged card quantities.
- Verify double-faced Etali printings, owned-first ordering, and restoration of default artwork.
