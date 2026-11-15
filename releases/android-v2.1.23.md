# Arcane Vault Android v2.1.23 — Unified Experience

## Added
- Five persistent, reachable bottom destinations: Home, Catalog, Collection, Decks, and Sync.
- Contextual Collection tabs for Physical, Arena and Set Progress; Decks tabs for saved decks and EDH Lab.
- Full in-workspace saved-deck review and editing with explicit discard and confirmed save controls.

## Changed
- Align native Android surfaces, borders and accent with the Arcane Noir desktop palette.
- Reclaim catalog viewport height by removing the old nine-item scrolling navigation strip.
- Deck previews show ownership gaps and mainboard cards in their own navigable page, not a text-only modal.
- Keep legacy tools accessible through Sync without crowding the primary navigation.

## Fixed
- Essential navigation destinations previously competed for narrow horizontal space and made card browsing feel cramped.
- Saved-deck inspection and editing were constrained by stacked dialogs instead of using available workspace height.

## Removed
- Only redundant navigation chrome and modal presentation; no collection, printing, deck or settings data is deleted.

## Safety
- Existing Android persistence, transfer protocol, printing reconciliation and updater remain unchanged.
- Native deck edits continue to require compare-and-swap checks and private recovery copies.
