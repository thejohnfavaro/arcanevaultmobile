# Arcane Vault Android v2.1.111

## Added
- **Custom Deck Art** in the native saved-deck editor: Auto/Custom mode, choose any current saved-deck mainboard card (or commander when applicable), and pick an official Scryfall printing for presentation.
- Deck Library covers use the saved custom card/printing choice and fall back to commander artwork or a mainboard card when Auto is selected or the chosen card is unavailable.
- Commander input and its printing picker now hide automatically for Arena Standard, 60-card and other non-Commander formats. Commander and Arena Brawl remain supported.

## Changed
- Cosmetic cover choices are kept in `deckCoverMode`, `deckCoverCardName` and `deckCoverPrintingId` inside the same revision-protected saved deck object that syncs with Windows.
- Existing Android deck recovery revisions preserve the previous cover choice and Commander printing preference.
- If a custom cover card was removed, the save confirmation explains that the deck cover will safely revert to Auto.

## Fixed
- 60-card decks no longer display a misleading Commander editing field.
- Chosen custom deck artwork now appears in the Android Deck Library rather than always defaulting to Commander or first-card art.
- Preview art changes cannot modify physical/Arena card ownership or silently add a card to the mainboard.

## Removed
- Nothing. Existing decks, inventories, matches, snapshots and revision history remain intact.

## Verification
- Set Custom Deck Art on a 60-card Arena Standard deck and select an alternate printing; save/reopen to verify consistent artwork.
- Confirm the Commander field is hidden for Standard and generic 60-card formats, but returns when switching to Commander/Arena Brawl.
- Remove the chosen custom cover card, save and verify the fallback warning; confirm no ownership or deck quantities change.
- Sync with Windows and verify the Custom Deck Art selection persists on both devices.
