# Arcane Vault Android v2.1.109

## Added
- **Arena Standard** as a selectable saved-deck format in the native Android deck editor, alongside Commander, Arena Brawl and generic 60-card.
- Arena Standard requires at least 60 mainboard cards when saving and has no separate commander zone.
- Android saved-deck cards show their format rather than "Commander not specified", and Standard decks use a representative mainboard card for cover artwork.

## Changed
- The selected format is saved within the same revision-protected deck object; previous revisions, ownership and inventory are not rewritten.
- Existing Android collection coverage uses Arena inventory for Arena Standard, consistent with Windows peer sync.
- Deck revision history preserves the previous format for recovery.

## Fixed
- Standard noncommander decks previously displayed empty commander placeholders in the native Deck Library.
- The mobile saved deck editor previously had no control to select an Arena Standard deck type.

## Removed
- Nothing. Saved deck IDs, collection quantities, printing preferences and peer-sync identity remain intact.

## Verification
- Sync a saved Arena Standard deck from Windows and verify the format, deck cover, collection coverage and mainboard.
- Edit the deck in Android and Review & Save; the format should round-trip through the revision-protected save without adding a commander.
- Verify fewer than 60 mainboard cards cannot be saved as Arena Standard.
- Match analytics remains driven by verified imported match history; no device infers wins, losses, or opponent identities.
