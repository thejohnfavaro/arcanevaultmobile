# Arcane Vault Android v2.1.25 — Commander Deck Covers

## Added
- Commander artwork previews on saved decks in the Android Deck Library, matching the Windows deck-box layout.
- Compact illustrated deck tiles with deck name, commander, saved format, card count and folder.

## Changed
- Saved deck cover images are looked up by exact commander name in the existing local Scryfall catalog.
- Load only artwork for currently visible deck rows; a two-worker queue and bounded in-memory image cache avoid fetching hundreds of covers on opening.
- Keep a styled Arcane Vault deck-box placeholder when the commander has no indexed art or the device is offline.

## Fixed
- Android Deck Library previously displayed text-only rows, unlike Windows' illustrated deck-box previews.

## Removed
- No cards, deck metadata, printing preferences, or ownership records removed.

## Safety
- Commander art is illustrative and does not assert ownership, a particular physical printing, or deck legality.
- Existing saved deck records are not rewritten and no new artwork state is synced.
- Art requests use the same guarded Scryfall cache and exact-name local database lookup.
